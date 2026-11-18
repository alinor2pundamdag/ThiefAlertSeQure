package com.thiefalertsequre.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/** One still front-camera capture, initiated only after successful owner authentication in a visible Activity. */
internal object CameraEvidenceCapture {
    private const val SETTINGS = "camera_evidence_settings"
    private const val KEY_ENABLED = "capture_after_owner_authentication"
    private const val KEY_ENCRYPT_NEW_PHOTOS = "encrypt_new_photos"
    private const val CAPTURE_TIMEOUT_MS = 15_000L
    private const val PREFERRED_WIDTH = 1280
    private const val PREFERRED_HEIGHT = 720
    private val inFlight = AtomicBoolean(false)

    fun isEnabled(context: Context): Boolean = runCatching {
        SafePreferences.boolean(
            context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE),
            KEY_ENABLED,
            false
        )
    }.getOrDefault(false)

    fun isEncryptionEnabled(context: Context): Boolean = runCatching {
        when (val read = SafePreferences.readBoolean(
            context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE),
            KEY_ENCRYPT_NEW_PHOTOS,
            false
        )) {
            is PreferenceRead.Value -> read.value
            is PreferenceRead.Corrupt -> true
        }
    }.getOrDefault(true)

    fun setEncryptionEnabled(context: Context, enabled: Boolean): Boolean = runCatching {
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENCRYPT_NEW_PHOTOS, enabled).commit()
    }.getOrDefault(false)

    fun setEnabled(context: Context, enabled: Boolean): Boolean = runCatching {
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).commit()
    }.getOrDefault(false)

    fun captureAfterSuccessfulAuthentication(activity: Activity) {
        val app = activity.applicationContext
        if (!isEnabled(app)) return
        val permissionGranted = ContextCompat.checkSelfPermission(
            activity, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val visible = isActivityVisible(activity)
        // Read window/display state on the Activity thread before camera callbacks
        // move to the dedicated camera HandlerThread.
        val displayRotationDegrees = when (activity.windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        val encryptEvidence = isEncryptionEnabled(app)
        if (!CameraEvidencePolicy.shouldCapture(true, permissionGranted, true, visible)) {
            recordFailure(app, if (!permissionGranted) "camera_evidence_permission_missing"
                else "camera_evidence_activity_not_visible",
                "Front-camera evidence was skipped; owner authentication remains successful")
            return
        }
        val galleryPermissionGranted = encryptEvidence || android.os.Build.VERSION.SDK_INT >= 29 ||
            (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
        if (!galleryPermissionGranted) {
            recordFailure(app, "camera_evidence_gallery_permission_missing", "Read/write Gallery permissions are required on Android 8–9 when unencrypted Gallery storage is selected")
            return
        }
        if (!inFlight.compareAndSet(false, true)) {
            recordFailure(app, "camera_evidence_capture_busy", "A previous front-camera evidence capture is still completing")
            return
        }

        val manager = app.getSystemService(CameraManager::class.java) ?: run {
            inFlight.set(false)
            recordFailure(app, "camera_evidence_camera_service_missing", "Android did not expose the camera system service")
            return
        }
        val cameraId = runCatching {
            manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            }
        }.getOrNull()
        if (cameraId == null) {
            inFlight.set(false)
            recordFailure(app, "camera_evidence_front_camera_unavailable", "No front-facing camera was reported by Android")
            return
        }

        val characteristics = runCatching { manager.getCameraCharacteristics(cameraId) }.getOrNull()
        if (characteristics == null) {
            inFlight.set(false)
            recordFailure(app, "camera_evidence_characteristics_unavailable", "Android did not return front-camera characteristics")
            return
        }
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = runCatching { map?.getOutputSizes(ImageFormat.JPEG) }.getOrNull()
        val size = sizes?.filter { it.width <= PREFERRED_WIDTH && it.height <= PREFERRED_HEIGHT }
            ?.maxByOrNull { it.width.toLong() * it.height.toLong() }
            ?: sizes?.minByOrNull { it.width.toLong() * it.height.toLong() }
        if (size == null) {
            inFlight.set(false)
            recordFailure(app, "camera_evidence_jpeg_unavailable", "Front camera does not expose a JPEG output size")
            return
        }

        val thread = HandlerThread("tas-camera-evidence").apply { start() }
        val handler = Handler(thread.looper)
        val reader = runCatching { ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2) }
            .getOrNull()
        if (reader == null) {
            inFlight.set(false)
            thread.quitSafely()
            recordFailure(app, "camera_evidence_reader_failed", "Android could not create a bounded JPEG image reader")
            return
        }

        val finished = AtomicBoolean(false)
        val activityVisible = AtomicBoolean(visible)
        val lifecycle = (activity as LifecycleOwner).lifecycle
        var lifecycleObserver: LifecycleEventObserver? = null
        var camera: CameraDevice? = null
        var session: CameraCaptureSession? = null

        fun finish(event: String? = null, detail: String = "") {
            if (!finished.compareAndSet(false, true)) return
            handler.removeCallbacksAndMessages(null)
            runCatching { session?.close() }
            runCatching { camera?.close() }
            runCatching { reader.close() }
            lifecycleObserver?.let { observer ->
                Handler(Looper.getMainLooper()).post {
                    runCatching { lifecycle.removeObserver(observer) }
                }
            }
            if (event != null) recordFailure(app, event, detail)
            inFlight.set(false)
            thread.quitSafely()
        }

        lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_DESTROY) {
                activityVisible.set(false)
                handler.post {
                    finish(
                        "camera_evidence_activity_not_visible",
                        "App left the visible state before the front-camera evidence photo was saved"
                    )
                }
            }
        }
        lifecycle.addObserver(lifecycleObserver!!)

        handler.postDelayed({
            finish("camera_evidence_capture_timeout", "Front-camera still capture did not complete within 15 seconds")
        }, CAPTURE_TIMEOUT_MS)

        reader.setOnImageAvailableListener({ source ->
            if (finished.get() || !activityVisible.get()) {
                runCatching { source.acquireLatestImage()?.close() }
                if (!finished.get()) {
                    finish(
                        "camera_evidence_activity_not_visible",
                        "App left the visible state before the front-camera evidence photo could be saved"
                    )
                }
                return@setOnImageAvailableListener
            }
            val image = runCatching { source.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            val jpeg = try {
                val buffer = image.planes.firstOrNull()?.buffer
                    ?: throw IllegalStateException("Camera returned no JPEG plane")
                ByteArray(buffer.remaining()).also { buffer.get(it) }
            } catch (error: Exception) {
                image.close()
                finish("camera_evidence_image_read_failed", error.javaClass.simpleName)
                return@setOnImageAvailableListener
            }
            image.close()
            val boundedJpeg = boundJpeg(jpeg)
            if (boundedJpeg == null) {
                finish("camera_evidence_image_rejected", "JPEG could not be compressed below the 2 MiB evidence size limit")
                return@setOnImageAvailableListener
            }
            val encryptedEntry = if (encryptEvidence) runCatching { CameraEvidenceVault.save(app, boundedJpeg) }.getOrNull() else null
            val galleryUri = if (!encryptEvidence) runCatching { CameraEvidenceMediaStore.save(app, boundedJpeg) }.getOrNull() else null
            val saved = if (encryptEvidence) encryptedEntry != null else galleryUri != null
            if (!saved) {
                finish("camera_evidence_storage_failed", if (encryptEvidence)
                    "Encrypted photo could not be committed to the private vault"
                else "Photo could not be published to the Gallery MediaStore")
            } else {
                val storageDetail = if (encryptEvidence) {
                    val entry = requireNotNull(encryptedEntry)
                    "storage=encrypted_private_vault; id=${entry.id}; sha256=${entry.sha256}; bytes=${entry.sizeBytes}"
                } else {
                    "storage=gallery_unencrypted; uri=$galleryUri; bytes=${boundedJpeg.size}"
                }
                runCatching { LocalEvidenceVault.recordAsync(app, "front_camera_evidence_saved", "$storageDetail; trigger=successful_owner_authentication") }
                runCatching { LocalTamperHistory.record(app, "front_camera_evidence_saved", "Front-camera photo saved after successful owner authentication; $storageDetail") }
                notifyOwner(app, if (encryptEvidence) "Front-camera evidence saved in the encrypted private vault." else "Front-camera evidence saved to Gallery.")
                finish()
            }
        }, handler)

        try {
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    camera = device
                    if (finished.get()) {
                        device.close()
                        return
                    }
                    if (!activityVisible.get()) {
                        finish("camera_evidence_activity_not_visible", "App left the visible state before the front-camera device opened")
                        return
                    }
                    try {
                        device.createCaptureSession(
                            listOf(reader.surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(configured: CameraCaptureSession) {
                                    session = configured
                                    if (finished.get()) {
                                        configured.close()
                                        return
                                    }
                                    if (!activityVisible.get()) {
                                        finish("camera_evidence_activity_not_visible", "App left the visible state before the front-camera photo was captured")
                                        return
                                    }
                                    try {
                                        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                            addTarget(reader.surface)
                                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                                            val afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                                            if (afModes?.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) == true) {
                                                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                            }
                                            val aeModes = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
                                            if (aeModes?.contains(CaptureRequest.CONTROL_AE_MODE_ON) == true) {
                                                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                            }
                                            set(CaptureRequest.JPEG_QUALITY, 85.toByte())
                                            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                                            val frontFacing = characteristics.get(CameraCharacteristics.LENS_FACING) ==
                                                CameraCharacteristics.LENS_FACING_FRONT
                                            set(
                                                CaptureRequest.JPEG_ORIENTATION,
                                                CameraEvidencePolicy.jpegOrientation(
                                                    sensorOrientation, displayRotationDegrees, frontFacing
                                                )
                                            )
                                        }.build()
                                        configured.capture(request, object : CameraCaptureSession.CaptureCallback() {
                                            override fun onCaptureFailed(
                                                session: CameraCaptureSession,
                                                request: CaptureRequest,
                                                failure: android.hardware.camera2.CaptureFailure
                                            ) {
                                                finish("camera_evidence_capture_failed", "Android camera capture failed with reason ${failure.reason}")
                                            }
                                        }, handler)
                                    } catch (error: Exception) {
                                        finish("camera_evidence_capture_failed", error.javaClass.simpleName + ": " + error.message)
                                    }
                                }

                                override fun onConfigureFailed(configured: CameraCaptureSession) {
                                    finish("camera_evidence_session_failed", "Android could not configure the front-camera JPEG capture session")
                                }
                            },
                            handler
                        )
                    } catch (error: Exception) {
                        finish("camera_evidence_session_failed", error.javaClass.simpleName + ": " + error.message)
                    }
                }

                override fun onDisconnected(device: CameraDevice) {
                    device.close()
                    finish("camera_evidence_camera_disconnected", "Front camera disconnected before the evidence photo was saved")
                }

                override fun onError(device: CameraDevice, error: Int) {
                    device.close()
                    finish("camera_evidence_camera_error", "Android camera error code $error")
                }
            }, handler)
        } catch (error: SecurityException) {
            finish("camera_evidence_permission_error", "Android denied front-camera access after permission was granted")
        } catch (error: CameraAccessException) {
            finish("camera_evidence_access_error", "Android camera access failed: ${error.reason}")
        } catch (error: Exception) {
            finish("camera_evidence_start_failed", error.javaClass.simpleName + ": " + error.message)
        }
    }

    private fun boundJpeg(source: ByteArray): ByteArray? {
        // Inspect dimensions without allocating a full-resolution bitmap. A small
        // compressed 8K JPEG can still require hundreds of MiB when decoded.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        val sourceWidth = bounds.outWidth
        val sourceHeight = bounds.outHeight
        if (sourceWidth <= 0 || sourceHeight <= 0) return null
        if (CameraEvidencePolicy.isValidImageSize(source.size) &&
            sourceWidth <= PREFERRED_WIDTH && sourceHeight <= PREFERRED_HEIGHT
        ) return source

        val requiredSampleRatio = maxOf(
            sourceWidth.toFloat() / PREFERRED_WIDTH,
            sourceHeight.toFloat() / PREFERRED_HEIGHT
        )
        var sampleSize = 1
        while (sampleSize.toFloat() * 2f <= requiredSampleRatio) sampleSize *= 2
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = runCatching {
            BitmapFactory.decodeByteArray(source, 0, source.size, decodeOptions)
        }.getOrNull() ?: return null
        return try {
            val scale = minOf(
                1f,
                PREFERRED_WIDTH.toFloat() / bitmap.width.coerceAtLeast(1),
                PREFERRED_HEIGHT.toFloat() / bitmap.height.coerceAtLeast(1)
            )
            val resized = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else bitmap
            try {
                for (quality in listOf(85, 75, 65, 55)) {
                    val output = ByteArrayOutputStream()
                    if (!resized.compress(Bitmap.CompressFormat.JPEG, quality, output)) continue
                    val bytes = output.toByteArray()
                    if (CameraEvidencePolicy.isValidImageSize(bytes.size)) return bytes
                }
                null
            } finally {
                if (resized !== bitmap) resized.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun isActivityVisible(activity: Activity): Boolean =
        activity is androidx.lifecycle.LifecycleOwner &&
            (activity as androidx.lifecycle.LifecycleOwner).lifecycle.currentState
                .isAtLeast(Lifecycle.State.STARTED) &&
            !activity.isFinishing &&
            (android.os.Build.VERSION.SDK_INT < 17 || !activity.isDestroyed)

    private fun recordFailure(context: Context, event: String, detail: String) {
        runCatching { LocalEvidenceVault.recordAsync(context, event, detail) }
        runCatching { LocalTamperHistory.record(context, event, detail) }
        notifyOwner(context, "Front-camera evidence was not saved. Owner authentication and alarm state were not changed.")
    }

    private fun notifyOwner(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            runCatching {
                Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
