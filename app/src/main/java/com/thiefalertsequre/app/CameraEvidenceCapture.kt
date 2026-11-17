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
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/** One still front-camera capture, initiated only after successful owner authentication in a visible Activity. */
internal object CameraEvidenceCapture {
    private const val SETTINGS = "camera_evidence_settings"
    private const val KEY_ENABLED = "capture_after_owner_authentication"
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
        if (!CameraEvidencePolicy.shouldCapture(true, permissionGranted, true, visible)) {
            recordFailure(app, if (!permissionGranted) "camera_evidence_permission_missing"
                else "camera_evidence_activity_not_visible",
                "Front-camera evidence was skipped; owner authentication remains successful")
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
        val map = characteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(ImageFormat.JPEG)
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
        var camera: CameraDevice? = null
        var session: CameraCaptureSession? = null

        fun finish(event: String? = null, detail: String = "") {
            if (!finished.compareAndSet(false, true)) return
            handler.removeCallbacksAndMessages(null)
            runCatching { session?.close() }
            runCatching { camera?.close() }
            runCatching { reader.close() }
            if (event != null) recordFailure(app, event, detail)
            inFlight.set(false)
            thread.quitSafely()
        }

        handler.postDelayed({
            finish("camera_evidence_capture_timeout", "Front-camera still capture did not complete within 15 seconds")
        }, CAPTURE_TIMEOUT_MS)

        reader.setOnImageAvailableListener({ source ->
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
            val entry = runCatching { CameraEvidenceVault.save(app, boundedJpeg) }.getOrNull()
            if (entry == null) {
                finish("camera_evidence_storage_failed", "Encrypted front-camera evidence could not be committed to local storage")
            } else {
                runCatching {
                    LocalEvidenceVault.recordAsync(
                        app,
                        "front_camera_evidence_saved",
                        "id=${entry.id}; sha256=${entry.sha256}; bytes=${entry.sizeBytes}; trigger=successful_owner_authentication"
                    )
                }
                runCatching {
                    LocalTamperHistory.record(
                        app,
                        "front_camera_evidence_saved",
                        "Encrypted front-camera photo saved after successful owner authentication; id=${entry.id}; sha256=${entry.sha256}"
                    )
                }
                notifyOwner(app, "Front-camera evidence saved securely.")
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
                    if (!isActivityVisible(activity)) {
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
                                    if (!isActivityVisible(activity)) {
                                        finish("camera_evidence_activity_not_visible", "App left the visible state before the front-camera photo was captured")
                                        return
                                    }
                                    try {
                                        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                            addTarget(reader.surface)
                                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                                            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                                            val rotation = when (activity.windowManager.defaultDisplay.rotation) {
                                                Surface.ROTATION_90 -> 90
                                                Surface.ROTATION_180 -> 180
                                                Surface.ROTATION_270 -> 270
                                                else -> 0
                                            }
                                            set(CaptureRequest.JPEG_ORIENTATION, (sensorOrientation - rotation + 360) % 360)
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
        if (CameraEvidencePolicy.isValidImageSize(source.size)) return source
        val bitmap = runCatching { BitmapFactory.decodeByteArray(source, 0, source.size) }.getOrNull() ?: return null
        return try {
            val scale = minOf(
                1f,
                1280f / bitmap.width.coerceAtLeast(1),
                720f / bitmap.height.coerceAtLeast(1)
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
