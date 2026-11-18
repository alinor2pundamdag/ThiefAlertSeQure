package com.thiefalertsequre.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.opengl.Matrix as GlMatrix
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import com.google.android.filament.Box
import com.google.android.filament.Camera
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndexBuffer
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Material
import com.google.android.filament.MathUtils
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.Texture
import com.google.android.filament.VertexBuffer
import com.google.android.filament.View
import com.google.android.filament.View.AmbientOcclusionOptions
import com.google.android.filament.View.BloomOptions
import com.google.android.filament.View.DepthOfFieldOptions
import com.google.android.filament.View.DynamicResolutionOptions
import com.google.android.filament.View.FogOptions
import com.google.android.filament.Viewport
import com.google.android.filament.android.FilamentHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.HDRLoader
import com.google.android.filament.utils.IBLPrefilterContext
import com.google.android.filament.filamat.MaterialBuilder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.pow
import kotlin.math.sqrt
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.ByteArrayOutputStream

/**
 * Actual Filament scene used by the Seismic Genesis intro.
 *
 * The shield and every debris shard are independently extruded triangle meshes.
 * Shards are integrated as rigid bodies (gravity, angular motion, floor response,
 * pairwise sphere contacts and the final directional wind impulse).
 */
class GenesisFilamentRendererView(
    context: Context,
    private val onInitializationFailed: (() -> Unit)? = null
) : TextureView(context) {
    private data class Point2(val x: Float, val y: Float)
    private data class Mesh(val entity: Int, val vertexBuffer: VertexBuffer, val indexBuffer: IndexBuffer)
    private data class LetterBody(
        val body: Body,
        val homeX: Float,
        val homeY: Float,
        val startY: Float,
        val fallVelocity: Float
    )
    private data class Body(
        val mesh: Mesh,
        var x: Float,
        var y: Float,
        var z: Float,
        var vx: Float,
        var vy: Float,
        var vz: Float,
        var rx: Float,
        var ry: Float,
        var rz: Float,
        val radius: Float,
        val mass: Float = (radius * radius * radius * 18f).coerceAtLeast(0.025f),
        var angularX: Float = 0f,
        var angularY: Float = 0f,
        var angularZ: Float = 0f
    )

    companion object {
        // Native Filament initialization must fail closed into the Compose
        // fallback. A static initializer exception would otherwise crash the
        // intro before initializeScene() can catch renderer failures.
        private val filamentInitializationReady by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            runCatching {
                Filament.init()
                true
            }.getOrDefault(false)
        }

        fun isSupported(context: Context): Boolean {
            if (!filamentInitializationReady) return false
            if (android.os.Build.VERSION.SDK_INT < 26) return false
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                ?: return false
            return manager.deviceConfigurationInfo.reqGlEsVersion >= 0x30000
        }
    }

    private var engine: Engine? = null
    private var renderer: com.google.android.filament.Renderer? = null
    private var scene: Scene? = null
    private var environmentSkybox: Skybox? = null
    private var environmentIndirectLight: IndirectLight? = null
    private var environmentSkyboxTexture: com.google.android.filament.Texture? = null
    private var environmentReflectionTexture: com.google.android.filament.Texture? = null
    private var filamentView: View? = null
    private var camera: Camera? = null
    private var uiHelper: UiHelper? = null
    private var swapChain: SwapChain? = null
    private var pbrMaterial: Material? = null
    private var shieldMaterial: com.google.android.filament.MaterialInstance? = null
    private var shieldRimMaterial: com.google.android.filament.MaterialInstance? = null
    private var debrisMaterial: com.google.android.filament.MaterialInstance? = null
    private var titleMaterial: com.google.android.filament.MaterialInstance? = null
    private var coreMaterial: com.google.android.filament.MaterialInstance? = null
    private var shieldMesh: Mesh? = null
    private var shieldRimMesh: Mesh? = null
    private var coreMesh: Mesh? = null
    private val bodies = mutableListOf<Body>()
    private val titleBodies = mutableListOf<LetterBody>()
    private val nameBodies = mutableListOf<LetterBody>()
    private var titleAdded = false
    private var nameAdded = false
    private var coreLightEntity = 0
    private var coreLightRemoved = false
    private var lightEntity = 0
    private val rimLightEntities = mutableListOf<Int>()
    private var currentProgress = 0f
    private var lastPhysicsProgress = 0f
    private var debrisAdded = false
    private var running = false
    private var released = false
    private var surfaceWidth = 1
    private var surfaceHeight = 1
    var isReady: Boolean = false
        private set

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running || released) return
            val activeRenderer = renderer
            val activeChain = swapChain
            val activeView = filamentView
            if (activeRenderer != null && activeChain != null && activeView != null &&
                uiHelper?.isReadyToRender == true
            ) {
                updateScene(currentProgress)
                if (activeRenderer.beginFrame(activeChain, frameTimeNanos)) {
                    activeRenderer.render(activeView)
                    activeRenderer.endFrame()
                }
            }
            if (running && !released) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        isOpaque = true
        initializeScene()
    }

    fun setProgress(progress: Float) {
        currentProgress = progress.coerceIn(0f, 1f)
    }

    fun startRendering() {
        if (released || running) return
        running = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun pauseRendering() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun initializeScene() {
        try {
            val activeEngine = Engine.create()
            engine = activeEngine
            renderer = activeEngine.createRenderer()
            scene = activeEngine.createScene()
            filamentView = activeEngine.createView()
            val cameraEntity = EntityManager.get().create()
            camera = activeEngine.createCamera(cameraEntity)
            filamentView?.camera = camera
            filamentView?.scene = scene
            filamentView?.isPostProcessingEnabled = true
            filamentView?.fogOptions = FogOptions().apply {
                distance = 2.2f
                cutOffDistance = 18f
                maximumOpacity = 0.28f
                height = -0.85f
                heightFalloff = 0.42f
                color = floatArrayOf(0.055f, 0.075f, 0.11f)
                density = 0.035f
                inScatteringStart = 1.8f
                inScatteringSize = 12f
                fogColorFromIbl = true
                enabled = true
            }
            filamentView?.bloomOptions = BloomOptions().apply {
                strength = 0.24f
                resolution = 128
                levels = 5
                threshold = true
                enabled = true
            }
            filamentView?.depthOfFieldOptions = DepthOfFieldOptions().apply {
                cocScale = 0.22f
                maxApertureDiameter = 0.008f
                foregroundRingCount = 3
                backgroundRingCount = 3
                fastGatherRingCount = 3
                maxForegroundCOC = 8
                maxBackgroundCOC = 12
                enabled = true
            }
            filamentView?.ambientOcclusionOptions = AmbientOcclusionOptions().apply {
                radius = 0.18f
                intensity = 0.85f
                enabled = true
            }
            filamentView?.dynamicResolutionOptions = DynamicResolutionOptions().apply {
                minScale = 0.82f
                maxScale = 1f
                sharpness = 0.92f
                homogeneousScaling = true
                quality = View.QualityLevel.HIGH
                enabled = true
            }
            buildStudioHdrEnvironment(activeEngine)

            pbrMaterial = buildPbrMaterial(activeEngine)
            shieldMaterial = pbrMaterial?.createInstance()?.apply {
                setParameter("baseColor", Colors.RgbType.SRGB, 0.34f, 0.43f, 0.52f)
                setParameter("metallic", 0.92f)
                setParameter("roughness", 0.22f)
                setParameter("emissiveColor", 0f, 0f, 0f)
            }
            debrisMaterial = pbrMaterial?.createInstance()?.apply {
                setParameter("baseColor", Colors.RgbType.SRGB, 0.30f, 0.32f, 0.34f)
                setParameter("metallic", 0.18f)
                setParameter("roughness", 0.86f)
                setParameter("emissiveColor", 0f, 0f, 0f)
            }
            titleMaterial = pbrMaterial?.createInstance()?.apply {
                setParameter("baseColor", Colors.RgbType.SRGB, 0.88f, 0.035f, 0.025f)
                setParameter("metallic", 0.48f)
                setParameter("roughness", 0.34f)
                setParameter("emissiveColor", 0.12f, 0.002f, 0.001f)
            }
            coreMaterial = pbrMaterial?.createInstance()?.apply {
                setParameter("baseColor", Colors.RgbType.SRGB, 0.95f, 0.035f, 0.015f)
                setParameter("metallic", 0.35f)
                setParameter("roughness", 0.2f)
                setParameter("emissiveColor", 3.5f, 0.04f, 0.012f)
            }

            val shieldOutline = listOf(
                Point2(-0.78f, 0.82f), Point2(0.78f, 0.82f),
                Point2(0.73f, 0.10f), Point2(0.48f, -0.54f),
                Point2(0f, -0.94f), Point2(-0.48f, -0.54f),
                Point2(-0.73f, 0.10f)
            )
            shieldMesh = createExtrudedMesh(shieldOutline, 0.18f, shieldMaterial!!, bevelDepth = 0.045f)
            scene?.addEntity(shieldMesh!!.entity)
            shieldRimMaterial = pbrMaterial?.createInstance()?.apply {
                setParameter("baseColor", Colors.RgbType.SRGB, 0.68f, 0.76f, 0.86f)
                setParameter("metallic", 0.98f)
                setParameter("roughness", 0.16f)
                setParameter("emissiveColor", 0.025f, 0.035f, 0.05f)
            }
            val insetRimOutline = shieldOutline.map { Point2(it.x * 0.91f, it.y * 0.91f) }
            shieldRimMesh = createExtrudedMesh(insetRimOutline, 0.055f, shieldRimMaterial!!)
            scene?.addEntity(shieldRimMesh!!.entity)
            createFractureBodies(shieldOutline)

            val coreOutline = (0 until 12).map { index ->
                val angle = index * (2.0 * PI / 12.0)
                Point2(cos(angle).toFloat() * 0.19f, sin(angle).toFloat() * 0.19f)
            }
            coreMesh = createExtrudedMesh(coreOutline, 0.13f, coreMaterial!!)
            scene?.addEntity(coreMesh!!.entity)

            titleBodies += createTextBodies("Developed By:", titleMaterial!!, 2.35f, -2.0f, 0.82f)
            nameBodies += createTextBodies("Alinor Pundamdag", debrisMaterial!!, 2.25f, -2.35f, 0.08f)

            lightEntity = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(0.82f, 0.88f, 1.0f)
                .intensity(72_000f)
                .direction(-0.42f, -0.72f, -1.0f)
                .castShadows(true)
                .build(activeEngine, lightEntity)
            scene?.addEntity(lightEntity)

            listOf(
                Triple(floatArrayOf(-1.75f, 0.55f, 1.15f), floatArrayOf(0.18f, 0.34f, 0.82f), 620f),
                Triple(floatArrayOf(1.72f, -0.42f, 0.95f), floatArrayOf(0.92f, 0.20f, 0.08f), 540f)
            ).forEach { (position, color, intensity) ->
                val rimLight = EntityManager.get().create()
                LightManager.Builder(LightManager.Type.POINT)
                    .position(position[0], position[1], position[2])
                    .color(color[0], color[1], color[2])
                    .intensity(intensity)
                    .falloff(4.2f)
                    .build(activeEngine, rimLight)
                rimLightEntities += rimLight
                scene?.addEntity(rimLight)
            }

            coreLightEntity = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.POINT)
                .position(0f, 0f, 0.28f)
                .color(1f, 0.035f, 0.012f)
                .intensity(2_400f)
                .falloff(2.6f)
                .build(activeEngine, coreLightEntity)
            scene?.addEntity(coreLightEntity)

            camera?.setExposure(16.0f, 1.0f / 125.0f, 100.0f)
            camera?.lookAt(0.0, 0.0, 5.4, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)

            uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).also { helper ->
                helper.renderCallback = object : UiHelper.RendererCallback {
                    override fun onNativeWindowChanged(surface: Surface) {
                        swapChain?.let { engine?.destroySwapChain(it) }
                        swapChain = engine?.createSwapChain(surface)
                    }

                    override fun onDetachedFromSurface() {
                        swapChain?.let {
                            engine?.destroySwapChain(it)
                            engine?.flushAndWait()
                            swapChain = null
                        }
                    }

                    override fun onResized(width: Int, height: Int) {
                        surfaceWidth = max(1, width)
                        surfaceHeight = max(1, height)
                        camera?.setProjection(
                            43.0,
                            surfaceWidth.toDouble() / surfaceHeight.toDouble(),
                            0.1,
                            40.0,
                            Camera.Fov.VERTICAL
                        )
                        filamentView?.viewport = Viewport(0, 0, surfaceWidth, surfaceHeight)
                        engine?.let { FilamentHelper.synchronizePendingFrames(it) }
                    }
                }
                helper.attachTo(this)
            }
            isReady = true
        } catch (error: Throwable) {
            android.util.Log.e("Genesis3D", "Filament scene initialization failed", error)
            release()
            post { onInitializationFailed?.invoke() }
        }
    }

    /**
     * Builds a compact procedural HDR studio probe and prefilters it into Filament's
     * cubemap reflection data. This avoids network access and keeps the intro self-contained.
     */
    private fun buildStudioHdrEnvironment(activeEngine: Engine) {
        val width = 128
        val height = 64
        val output = ByteArrayOutputStream(32_768)
        output.write("#?RADIANCE\nFORMAT=32-bit_rle_rgbe\n\n-Y $height +X $width\n".toByteArray(Charsets.US_ASCII))

        fun rgbe(red: Float, green: Float, blue: Float): ByteArray {
            val peak = max(red, max(green, blue))
            if (peak < 1.0e-8f) return byteArrayOf(0, 0, 0, 0)
            val exponent = kotlin.math.floor(kotlin.math.log(peak.toDouble(), 2.0)).toInt() + 1
            val scale = 256.0 / 2.0.pow(exponent.toDouble())
            return byteArrayOf(
                (red * scale).toInt().coerceIn(0, 255).toByte(),
                (green * scale).toInt().coerceIn(0, 255).toByte(),
                (blue * scale).toInt().coerceIn(0, 255).toByte(),
                (exponent + 128).coerceIn(0, 255).toByte()
            )
        }

        for (y in 0 until height) {
            output.write(byteArrayOf(2, 2, (width shr 8).toByte(), (width and 255).toByte()))
            val channels = Array(4) { ByteArray(width) }
            for (x in 0 until width) {
                val u = x.toFloat() / (width - 1)
                val v = y.toFloat() / (height - 1)
                val horizon = kotlin.math.exp(-kotlin.math.abs(v - 0.52f) * 18f)
                val overheadStrip = if (v < 0.30f && (u in 0.12f..0.38f || u in 0.62f..0.88f)) 7.5f else 0f
                val warmPanel = if (u in 0.46f..0.54f && v < 0.58f) 2.4f else 0f
                val red = 0.025f + horizon * 0.12f + overheadStrip + warmPanel
                val green = 0.032f + horizon * 0.15f + overheadStrip * 0.94f + warmPanel * 0.10f
                val blue = 0.05f + horizon * 0.20f + overheadStrip * 0.86f + warmPanel * 0.035f
                val pixel = rgbe(red, green, blue)
                for (channel in 0..3) channels[channel][x] = pixel[channel]
            }
            for (channel in 0..3) {
                output.write(width)
                output.write(channels[channel])
            }
        }

        val hdrBytes = output.toByteArray()
        val hdrBuffer = ByteBuffer.allocateDirect(hdrBytes.size).put(hdrBytes).apply { flip() }
        val hdr = HDRLoader.createTexture(activeEngine, hdrBuffer)
            ?: error("Unable to decode generated Seismic Genesis HDR environment")
        var prefilter: IBLPrefilterContext? = null
        var converter: IBLPrefilterContext.EquirectangularToCubemap? = null
        var specularFilter: IBLPrefilterContext.SpecularFilter? = null
        var skyboxTexture: Texture? = null
        var reflections: Texture? = null
        try {
            val prefilterContext = IBLPrefilterContext(activeEngine)
            prefilter = prefilterContext
            val converterContext = IBLPrefilterContext.EquirectangularToCubemap(prefilterContext)
            converter = converterContext
            val specularFilterContext = IBLPrefilterContext.SpecularFilter(prefilterContext)
            specularFilter = specularFilterContext
            val generatedSkyboxTexture = checkNotNull(converterContext.run(hdr)) { "HDR to cubemap conversion failed" }
            skyboxTexture = generatedSkyboxTexture
            val generatedReflections = checkNotNull(specularFilterContext.run(generatedSkyboxTexture)) {
                "HDR reflection prefilter failed"
            }
            reflections = generatedReflections
            environmentSkyboxTexture = generatedSkyboxTexture
            environmentReflectionTexture = generatedReflections
            environmentIndirectLight = IndirectLight.Builder()
                .reflections(generatedReflections)
                .intensity(24_000f)
                .build(activeEngine)
            environmentSkybox = Skybox.Builder().environment(generatedSkyboxTexture).build(activeEngine)
            scene?.indirectLight = environmentIndirectLight
            scene?.skybox = environmentSkybox
        } catch (error: Throwable) {
            if (environmentSkyboxTexture == null) skyboxTexture?.let {
                runCatching { activeEngine.destroyTexture(it) }
            }
            if (environmentReflectionTexture == null) reflections?.let {
                runCatching { activeEngine.destroyTexture(it) }
            }
            throw error
        } finally {
            runCatching { activeEngine.destroyTexture(hdr) }
            runCatching { specularFilter?.destroy() }
            runCatching { converter?.destroy() }
            runCatching { prefilter?.destroy() }
        }
    }

    private fun buildPbrMaterial(activeEngine: Engine): Material {
        MaterialBuilder.init()
        try {
            val result = MaterialBuilder()
                .platform(MaterialBuilder.Platform.MOBILE)
                .name("Genesis physically based concrete and shield")
                .shading(MaterialBuilder.Shading.LIT)
                .uniformParameter(MaterialBuilder.UniformType.FLOAT3, "baseColor")
                .uniformParameter(MaterialBuilder.UniformType.FLOAT, "metallic")
                .uniformParameter(MaterialBuilder.UniformType.FLOAT, "roughness")
                .uniformParameter(MaterialBuilder.UniformType.FLOAT3, "emissiveColor")
                .material(
                    """
                    void material(inout MaterialInputs material) {
                        prepareMaterial(material);
                        material.baseColor.rgb = materialParams.baseColor;
                        material.metallic = materialParams.metallic;
                        material.roughness = materialParams.roughness;
                        material.emissive = materialParams.emissiveColor;
                        material.clearCoat = 0.28;
                        material.clearCoatRoughness = 0.24;
                    }
                    """.trimIndent()
                )
                .optimization(MaterialBuilder.Optimization.NONE)
                .build(activeEngine)
            check(result.isValid) { "Filament PBR material compilation failed" }
            return Material.Builder().payload(result.buffer, result.buffer.remaining()).build(activeEngine)
        } finally {
            MaterialBuilder.shutdown()
        }
    }


    private fun createTextBodies(
        text: String,
        material: com.google.android.filament.MaterialInstance,
        startY: Float,
        fallVelocity: Float,
        targetY: Float
    ): List<LetterBody> {
        val glyphs = mutableListOf<Pair<Mesh?, Float>>()
        var totalWidth = 0f
        text.forEach { character ->
            if (character.isWhitespace()) {
                glyphs += null to 0.14f
                totalWidth += 0.14f
            } else {
                val (mesh, width) = createVoxelGlyphMesh(character, material)
                glyphs += mesh to width
                totalWidth += width + 0.035f
            }
        }
        totalWidth = (totalWidth - 0.035f).coerceAtLeast(0.1f)
        var cursor = -totalWidth * 0.5f
        val result = mutableListOf<LetterBody>()
        glyphs.forEach { (mesh, width) ->
            if (mesh != null) {
                val homeX = cursor + width * 0.5f
                val body = Body(
                    mesh = mesh,
                    x = homeX,
                    y = startY,
                    z = 0.34f,
                    vx = 0f,
                    vy = fallVelocity,
                    vz = 0f,
                    rx = 0f,
                    ry = 0f,
                    rz = 0f,
                    radius = 0.12f
                )
                result += LetterBody(body, homeX, targetY, startY, fallVelocity)
                cursor += width + 0.035f
            } else {
                cursor += width
            }
        }
        return result
    }

    /**
     * Rasterizes one glyph and extrudes its alpha silhouette into a real voxel-cut 3D
     * concrete letter. Each character is its own Filament renderable and rigid body.
     */
    private fun createVoxelGlyphMesh(
        character: Char,
        material: com.google.android.filament.MaterialInstance
    ): Pair<Mesh, Float> {
        val bitmap = Bitmap.createBitmap(48, 64, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            textSize = 54f
        }
        val glyph = character.toString()
        val bounds = Rect()
        paint.getTextBounds(glyph, 0, glyph.length, bounds)
        val drawX = (bitmap.width - paint.measureText(glyph)) * 0.5f - bounds.left
        val baseline = (bitmap.height - bounds.height()) * 0.5f - bounds.top
        Canvas(bitmap).drawText(glyph, drawX, baseline, paint)

        val gridWidth = 24
        val gridHeight = 32
        val cell = 0.012f
        val depth = 0.14f
        val halfDepth = depth * 0.5f
        val occupied = Array(gridHeight) { BooleanArray(gridWidth) }
        var minX = gridWidth
        var maxX = -1
        var minY = gridHeight
        var maxY = -1
        for (y in 0 until gridHeight) {
            for (x in 0 until gridWidth) {
                val px = ((x + 0.5f) * bitmap.width / gridWidth).toInt().coerceIn(0, bitmap.width - 1)
                val py = ((y + 0.5f) * bitmap.height / gridHeight).toInt().coerceIn(0, bitmap.height - 1)
                if (android.graphics.Color.alpha(bitmap.getPixel(px, py)) > 70) {
                    occupied[y][x] = true
                    minX = min(minX, x)
                    maxX = max(maxX, x)
                    minY = min(minY, y)
                    maxY = max(maxY, y)
                }
            }
        }
        bitmap.recycle()
        check(maxX >= minX && maxY >= minY) { "Unable to rasterize 3D glyph: $character" }

        val width = (maxX - minX + 1) * cell
        val height = (maxY - minY + 1) * cell
        val positions = ArrayList<Float>(8_192)
        val tangents = ArrayList<Float>(10_000)
        val indices = ArrayList<Short>(6_000)

        fun addVertex(point: FloatArray, normal: FloatArray) {
            positions.add(point[0]); positions.add(point[1]); positions.add(point[2])
            val tangent = FloatArray(4)
            MathUtils.packTangentFrame(
                1f, 0f, 0f,
                0f, 1f, 0f,
                normal[0], normal[1], normal[2],
                tangent
            )
            tangents.add(tangent[0]); tangents.add(tangent[1])
            tangents.add(tangent[2]); tangents.add(tangent[3])
        }

        fun addTriangle(a: FloatArray, b: FloatArray, c: FloatArray, normal: FloatArray) {
            val base = (positions.size / 3).toShort()
            addVertex(a, normal); addVertex(b, normal); addVertex(c, normal)
            indices.add(base); indices.add((base + 1).toShort()); indices.add((base + 2).toShort())
        }

        fun addQuad(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray, normal: FloatArray) {
            addTriangle(a, b, c, normal)
            addTriangle(a, c, d, normal)
        }

        for (y in minY..maxY) {
            for (x in minX..maxX) {
                if (!occupied[y][x]) continue
                val x0 = (x - minX) * cell - width * 0.5f
                val x1 = x0 + cell
                val y0 = (maxY - y) * cell - height * 0.5f
                val y1 = y0 + cell
                val front = halfDepth
                val back = -halfDepth

                addQuad(
                    floatArrayOf(x0, y0, front), floatArrayOf(x1, y0, front),
                    floatArrayOf(x1, y1, front), floatArrayOf(x0, y1, front),
                    floatArrayOf(0f, 0f, 1f)
                )
                addQuad(
                    floatArrayOf(x0, y0, back), floatArrayOf(x0, y1, back),
                    floatArrayOf(x1, y1, back), floatArrayOf(x1, y0, back),
                    floatArrayOf(0f, 0f, -1f)
                )

                if (x == minX || !occupied[y][x - 1]) {
                    addQuad(
                        floatArrayOf(x0, y0, back), floatArrayOf(x0, y0, front),
                        floatArrayOf(x0, y1, front), floatArrayOf(x0, y1, back),
                        floatArrayOf(-1f, 0f, 0f)
                    )
                }
                if (x == maxX || !occupied[y][x + 1]) {
                    addQuad(
                        floatArrayOf(x1, y0, front), floatArrayOf(x1, y0, back),
                        floatArrayOf(x1, y1, back), floatArrayOf(x1, y1, front),
                        floatArrayOf(1f, 0f, 0f)
                    )
                }
                if (y == maxY || !occupied[y + 1][x]) {
                    addQuad(
                        floatArrayOf(x0, y0, front), floatArrayOf(x0, y0, back),
                        floatArrayOf(x1, y0, back), floatArrayOf(x1, y0, front),
                        floatArrayOf(0f, -1f, 0f)
                    )
                }
                if (y == minY || !occupied[y - 1][x]) {
                    addQuad(
                        floatArrayOf(x0, y1, back), floatArrayOf(x0, y1, front),
                        floatArrayOf(x1, y1, front), floatArrayOf(x1, y1, back),
                        floatArrayOf(0f, 1f, 0f)
                    )
                }
            }
        }

        val vertexCount = positions.size / 3
        val vertexSize = 28
        val vertexData = ByteBuffer.allocateDirect(vertexCount * vertexSize).order(ByteOrder.nativeOrder())
        for (i in 0 until vertexCount) {
            vertexData.putFloat(positions[i * 3])
            vertexData.putFloat(positions[i * 3 + 1])
            vertexData.putFloat(positions[i * 3 + 2])
            vertexData.putFloat(tangents[i * 4])
            vertexData.putFloat(tangents[i * 4 + 1])
            vertexData.putFloat(tangents[i * 4 + 2])
            vertexData.putFloat(tangents[i * 4 + 3])
        }
        vertexData.flip()
        val indexData = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder())
        indices.forEach { indexData.putShort(it) }
        indexData.flip()

        val activeEngine = checkNotNull(engine)
        val vertexBuffer = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(vertexCount)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, vertexSize)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0, VertexBuffer.AttributeType.FLOAT4, 12, vertexSize)
            .build(activeEngine)
        vertexBuffer.setBufferAt(activeEngine, 0, vertexData)
        val indexBuffer = IndexBuffer.Builder()
            .indexCount(indices.size)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(activeEngine)
        indexBuffer.setBuffer(activeEngine, indexData)

        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(Box(-width * 0.5f, -height * 0.5f, -halfDepth, width * 0.5f, height * 0.5f, halfDepth))
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer, 0, indices.size)
            .material(0, material)
            .culling(false)
            .build(activeEngine, entity)
        return Mesh(entity, vertexBuffer, indexBuffer) to width
    }

    private fun createFractureBodies(outline: List<Point2>) {
        // Deterministic seed placement inside the shield, followed by convex Voronoi clipping.
        // Each resulting cell is a distinct, irregular 3D fragment rather than a radial triangle.
        fun contains(point: Point2): Boolean {
            var inside = false
            var j = outline.lastIndex
            for (i in outline.indices) {
                val a = outline[i]
                val b = outline[j]
                if ((a.y > point.y) != (b.y > point.y)) {
                    val crossingX = (b.x - a.x) * (point.y - a.y) /
                        ((b.y - a.y).takeIf { abs(it) > 0.000001f } ?: 0.000001f) + a.x
                    if (point.x < crossingX) inside = !inside
                }
                j = i
            }
            return inside
        }

        val seeds = mutableListOf<Point2>()
        for (row in 0..10) {
            for (column in 0..10) {
                val index = row * 11 + column
                val jitterX = sin(index * 12.9898f) * 0.035f
                val jitterY = cos(index * 7.233f) * 0.035f
                val point = Point2(
                    -0.72f + column * 0.144f + jitterX,
                    -0.88f + row * 0.176f + jitterY
                )
                if (contains(point) && seeds.size < 48) seeds += point
            }
        }
        check(seeds.size >= 32) { "Insufficient in-shield fracture seeds" }

        fun clipToBisector(polygon: List<Point2>, site: Point2, other: Point2): List<Point2> {
            if (polygon.isEmpty()) return emptyList()
            val nx = 2f * (other.x - site.x)
            val ny = 2f * (other.y - site.y)
            val limit = other.x * other.x + other.y * other.y -
                site.x * site.x - site.y * site.y
            val output = mutableListOf<Point2>()
            var previous = polygon.last()
            var previousDistance = nx * previous.x + ny * previous.y - limit
            for (current in polygon) {
                val currentDistance = nx * current.x + ny * current.y - limit
                val previousInside = previousDistance <= 0.00001f
                val currentInside = currentDistance <= 0.00001f
                if (previousInside != currentInside) {
                    val denominator = previousDistance - currentDistance
                    val t = if (abs(denominator) < 0.000001f) 0f else previousDistance / denominator
                    output += Point2(
                        previous.x + (current.x - previous.x) * t,
                        previous.y + (current.y - previous.y) * t
                    )
                }
                if (currentInside) output += current
                previous = current
                previousDistance = currentDistance
            }
            return output
        }

        val center = Point2(0f, 0.05f)
        seeds.forEachIndexed { index, site ->
            var cell: List<Point2> = outline
            seeds.forEachIndexed { otherIndex, other ->
                if (otherIndex != index && cell.size >= 3) {
                    cell = clipToBisector(cell, site, other)
                }
            }
            if (cell.size < 3) return@forEachIndexed

            var twiceArea = 0f
            for (i in cell.indices) {
                val a = cell[i]
                val b = cell[(i + 1) % cell.size]
                twiceArea += a.x * b.y - b.x * a.y
            }
            val area = abs(twiceArea) * 0.5f
            if (area < 0.0008f) return@forEachIndexed
            val cx = cell.map { it.x }.average().toFloat()
            val cy = cell.map { it.y }.average().toFloat()
            val local = cell.map { Point2(it.x - cx, it.y - cy) }
            val radius = (sqrt(area) * 0.46f).coerceIn(0.065f, 0.24f)
            val depth = 0.09f + radius * 0.38f
            val mesh = createExtrudedMesh(local, depth, debrisMaterial!!)
            val dx = cx - center.x
            val dy = cy - center.y
            val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            val seed = index * 17 + cell.size * 5
            val spin = if (index % 2 == 0) 1f else -1f
            bodies += Body(
                mesh = mesh,
                x = cx, y = cy, z = 0f,
                vx = dx / distance * (1.45f + (seed % 7) * 0.19f),
                vy = dy / distance * (1.85f + (seed % 5) * 0.27f),
                vz = spin * (0.7f + (seed % 4) * 0.22f),
                rx = 0f, ry = 0f, rz = 0f,
                radius = radius,
                mass = (area * depth * 18f).coerceAtLeast(0.025f),
                angularX = spin * (3.0f + (seed % 5) * 0.75f),
                angularY = -spin * (2.1f + (seed % 6) * 0.58f),
                angularZ = spin * (3.8f + (seed % 7) * 0.62f)
            )
        }
        // Add a second, smaller debris scale so the collapse contains chips as well as plates.
        seeds.take(18).forEachIndexed { index, site ->
            val angle = index * 2.3999632f
            val size = 0.035f + (index % 4) * 0.009f
            val points = listOf(
                Point2(0f, size),
                Point2(-size * (0.58f + (index % 3) * 0.12f), -size * 0.52f),
                Point2(size * (0.62f + (index % 2) * 0.16f), -size * 0.43f)
            ).map { point ->
                val rotatedX = point.x * cos(angle) - point.y * sin(angle)
                val rotatedY = point.x * sin(angle) + point.y * cos(angle)
                Point2(rotatedX, rotatedY)
            }
            val mesh = createExtrudedMesh(points, 0.025f + size * 0.25f, debrisMaterial!!)
            val directionX = site.x - center.x
            val directionY = site.y - center.y
            val distance = sqrt(directionX * directionX + directionY * directionY).coerceAtLeast(0.001f)
            val spin = if (index % 2 == 0) 1f else -1f
            bodies += Body(
                mesh = mesh,
                x = site.x, y = site.y, z = (index % 3 - 1) * 0.075f,
                vx = directionX / distance * (2.0f + (index % 5) * 0.38f),
                vy = directionY / distance * (2.3f + (index % 4) * 0.31f),
                vz = spin * (1.15f + (index % 3) * 0.3f),
                rx = 0f, ry = 0f, rz = 0f,
                radius = size * 0.72f,
                mass = 0.012f + size * 0.08f,
                angularX = spin * (7f + index % 4),
                angularY = -spin * (6f + index % 5),
                angularZ = spin * (8f + index % 6)
            )
        }
        check(bodies.isNotEmpty()) { "Voronoi fracture produced no shield fragments" }
    }

    private fun createExtrudedMesh(
        polygon: List<Point2>,
        depth: Float,
        material: com.google.android.filament.MaterialInstance,
        bevelDepth: Float = 0f
    ): Mesh {
        val positions = ArrayList<Float>(512)
        val tangents = ArrayList<Float>(512)
        val indices = ArrayList<Short>(256)
        val halfDepth = depth * 0.5f

        fun addVertex(point: FloatArray, normal: FloatArray) {
            positions.add(point[0]); positions.add(point[1]); positions.add(point[2])
            val tangent = FloatArray(4)
            MathUtils.packTangentFrame(
                1f, 0f, 0f,
                0f, 1f, 0f,
                normal[0], normal[1], normal[2],
                tangent
            )
            tangents.add(tangent[0]); tangents.add(tangent[1])
            tangents.add(tangent[2]); tangents.add(tangent[3])
        }

        fun addTriangle(a: FloatArray, b: FloatArray, c: FloatArray, normal: FloatArray) {
            val base = (positions.size / 3).toShort()
            addVertex(a, normal); addVertex(b, normal); addVertex(c, normal)
            indices.add(base); indices.add((base + 1).toShort()); indices.add((base + 2).toShort())
        }

        val centerX = polygon.map { it.x }.average().toFloat()
        val centerY = polygon.map { it.y }.average().toFloat()
        val bevel = bevelDepth.coerceIn(0f, halfDepth * 0.8f)
        val insetScale = if (bevel > 0f) 0.925f else 1f
        val inset = polygon.map { point ->
            Point2(centerX + (point.x - centerX) * insetScale, centerY + (point.y - centerY) * insetScale)
        }
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            val ia = inset[i]
            val ib = inset[(i + 1) % inset.size]
            val centerFront = floatArrayOf(centerX, centerY, halfDepth)
            val iaFront = floatArrayOf(ia.x, ia.y, halfDepth)
            val ibFront = floatArrayOf(ib.x, ib.y, halfDepth)
            addTriangle(centerFront, iaFront, ibFront, floatArrayOf(0f, 0f, 1f))

            val centerBack = floatArrayOf(centerX, centerY, -halfDepth)
            val iaBack = floatArrayOf(ia.x, ia.y, -halfDepth)
            val ibBack = floatArrayOf(ib.x, ib.y, -halfDepth)
            addTriangle(centerBack, ibBack, iaBack, floatArrayOf(0f, 0f, -1f))

            val edgeX = b.x - a.x
            val edgeY = b.y - a.y
            val edgeLength = sqrt(edgeX * edgeX + edgeY * edgeY).coerceAtLeast(0.0001f)
            val sideNormal = floatArrayOf(-edgeY / edgeLength, edgeX / edgeLength, 0f)
            val frontOuterA = floatArrayOf(a.x, a.y, halfDepth - bevel)
            val frontOuterB = floatArrayOf(b.x, b.y, halfDepth - bevel)
            val frontInnerA = floatArrayOf(ia.x, ia.y, halfDepth)
            val frontInnerB = floatArrayOf(ib.x, ib.y, halfDepth)
            val backOuterA = floatArrayOf(a.x, a.y, -halfDepth + bevel)
            val backOuterB = floatArrayOf(b.x, b.y, -halfDepth + bevel)
            val backInnerA = floatArrayOf(ia.x, ia.y, -halfDepth)
            val backInnerB = floatArrayOf(ib.x, ib.y, -halfDepth)

            if (bevel > 0f) {
                val frontBevelNormal = floatArrayOf(
                    sideNormal[0] * 0.72f, sideNormal[1] * 0.72f, 0.69f
                )
                val backBevelNormal = floatArrayOf(
                    sideNormal[0] * 0.72f, sideNormal[1] * 0.72f, -0.69f
                )
                addTriangle(frontOuterA, frontOuterB, frontInnerB, frontBevelNormal)
                addTriangle(frontOuterA, frontInnerB, frontInnerA, frontBevelNormal)
                addTriangle(backOuterA, backInnerB, backOuterB, backBevelNormal)
                addTriangle(backOuterA, backInnerA, backInnerB, backBevelNormal)
            }

            addTriangle(frontOuterA, backOuterA, backOuterB, sideNormal)
            addTriangle(frontOuterA, backOuterB, frontOuterB, sideNormal)
        }

        val vertexCount = positions.size / 3
        val vertexSize = 28
        val vertexData = ByteBuffer.allocateDirect(vertexCount * vertexSize).order(ByteOrder.nativeOrder())
        for (i in 0 until vertexCount) {
            vertexData.putFloat(positions[i * 3])
            vertexData.putFloat(positions[i * 3 + 1])
            vertexData.putFloat(positions[i * 3 + 2])
            vertexData.putFloat(tangents[i * 4])
            vertexData.putFloat(tangents[i * 4 + 1])
            vertexData.putFloat(tangents[i * 4 + 2])
            vertexData.putFloat(tangents[i * 4 + 3])
        }
        vertexData.flip()

        val indexData = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder())
        indices.forEach { indexData.putShort(it) }
        indexData.flip()

        val activeEngine = checkNotNull(engine)
        val vertexBuffer = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(vertexCount)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, vertexSize)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0, VertexBuffer.AttributeType.FLOAT4, 12, vertexSize)
            .build(activeEngine)
        vertexBuffer.setBufferAt(activeEngine, 0, vertexData)

        val indexBuffer = IndexBuffer.Builder()
            .indexCount(indices.size)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(activeEngine)
        indexBuffer.setBuffer(activeEngine, indexData)

        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(Box(-1.2f, -1.2f, -depth, 1.2f, 1.2f, depth))
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer, 0, indices.size)
            .material(0, material)
            .culling(false)
            .build(activeEngine, entity)
        return Mesh(entity, vertexBuffer, indexBuffer)
    }

    private fun updateScene(progress: Float) {
        val activeEngine = engine ?: return
        val activeScene = scene ?: return
        val shield = shieldMesh ?: return
        val transformManager = activeEngine.transformManager

        updateLetterBodies(activeScene, transformManager, titleBodies, progress, 0.18f, 0.245f, 0.82f, true)
        updateLetterBodies(activeScene, transformManager, nameBodies, progress, 0.39f, 0.455f, 0.82f, false)
        updateCore(activeScene, transformManager, progress)

        val shake = when {
            progress in 0.245f..0.278333f ->
                sin(((progress - 0.245f) / 0.033333f) * 6f * PI).toFloat() * 0.105f
            progress in 0.405f..0.455f ->
                sin(((progress - 0.405f) / 0.05f) * 8f * PI).toFloat() * 0.16f
            progress in 0.82f..0.87f ->
                sin(((progress - 0.82f) / 0.05f) * 6f * PI).toFloat() * 0.08f
            else -> 0f
        }
        camera?.lookAt(shake.toDouble(), (shake * 0.55f).toDouble(), 5.4, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)

        val shieldTransform = transformManager.getInstance(shield.entity)
        if (progress < 0.245f) {
            if (debrisAdded) {
                bodies.forEach { activeScene.removeEntity(it.mesh.entity) }
                activeScene.addEntity(shield.entity)
                shieldRimMesh?.let { activeScene.addEntity(it.entity) }
                debrisAdded = false
            }
            val appear = (progress / 0.13f).coerceIn(0f, 1f)
            val scale = 0.015f + appear * 0.985f
            val matrix = FloatArray(16)
            GlMatrix.setIdentityM(matrix, 0)
            GlMatrix.scaleM(matrix, 0, scale, scale, scale)
            transformManager.setTransform(shieldTransform, matrix)
            shieldRimMesh?.let { rim ->
                val rimMatrix = FloatArray(16)
                GlMatrix.setIdentityM(rimMatrix, 0)
                GlMatrix.translateM(rimMatrix, 0, 0f, 0f, 0.118f)
                GlMatrix.scaleM(rimMatrix, 0, scale, scale, scale)
                transformManager.setTransform(transformManager.getInstance(rim.entity), rimMatrix)
            }
            return
        }

        if (!debrisAdded) {
            activeScene.removeEntity(shield.entity)
            shieldRimMesh?.let { activeScene.removeEntity(it.entity) }
            bodies.forEach { activeScene.addEntity(it.mesh.entity) }
            debrisAdded = true
            lastPhysicsProgress = progress
        }
        val delta = ((progress - lastPhysicsProgress) * 9f).coerceIn(0f, 0.04f)
        lastPhysicsProgress = progress
        if (delta <= 0f) return

        val wind = ((progress - 0.82f) / 0.18f).coerceIn(0f, 1f)
        bodies.forEach { body ->
            body.vx += wind * 3.4f * delta
            body.vy -= 5.2f * delta
            body.x += body.vx * delta
            body.y += body.vy * delta
            body.z += body.vz * delta
            body.rx += body.angularX * delta
            body.ry += body.angularY * delta
            body.rz += body.angularZ * delta

            if (body.y - body.radius < -2.55f) {
                body.y = -2.55f + body.radius
                if (body.vy < 0f) {
                    val impactSpeed = -body.vy
                    body.vy = impactSpeed * 0.28f
                    val floorImpulse = impactSpeed * body.mass
                    body.angularX += body.vz * floorImpulse * 0.9f
                    body.angularZ -= body.vx * floorImpulse * 0.65f
                }
                body.vx *= 0.84f
                body.vz *= 0.84f
                body.angularX *= 0.88f
                body.angularY *= 0.88f
                body.angularZ *= 0.88f
            }
        }

        // Low-cost rigid-body contact approximation for the small, deterministic shield shard set.
        for (i in bodies.indices) {
            for (j in i + 1 until bodies.size) {
                val a = bodies[i]
                val b = bodies[j]
                val dx = b.x - a.x
                val dy = b.y - a.y
                val dz = b.z - a.z
                val distance = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.0001f)
                val minDistance = a.radius + b.radius
                if (distance < minDistance) {
                    val nx = dx / distance
                    val ny = dy / distance
                    val nz = dz / distance
                    val relative = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny + (b.vz - a.vz) * nz
                    val invA = 1f / a.mass
                    val invB = 1f / b.mass
                    val inverseMassSum = invA + invB
                    if (relative < 0f) {
                        val restitution = 0.38f
                        val normalImpulse = -(1f + restitution) * relative / inverseMassSum
                        a.vx -= normalImpulse * invA * nx
                        a.vy -= normalImpulse * invA * ny
                        a.vz -= normalImpulse * invA * nz
                        b.vx += normalImpulse * invB * nx
                        b.vy += normalImpulse * invB * ny
                        b.vz += normalImpulse * invB * nz

                        val rvx = b.vx - a.vx
                        val rvy = b.vy - a.vy
                        val rvz = b.vz - a.vz
                        val tangentDot = rvx * nx + rvy * ny + rvz * nz
                        var tx = rvx - tangentDot * nx
                        var ty = rvy - tangentDot * ny
                        var tz = rvz - tangentDot * nz
                        val tangentLength = sqrt(tx * tx + ty * ty + tz * tz)
                        if (tangentLength > 0.0001f) {
                            tx /= tangentLength; ty /= tangentLength; tz /= tangentLength
                            val frictionImpulse = (-tangentLength / inverseMassSum)
                                .coerceIn(-normalImpulse * 0.42f, normalImpulse * 0.42f)
                            a.vx -= frictionImpulse * invA * tx
                            a.vy -= frictionImpulse * invA * ty
                            a.vz -= frictionImpulse * invA * tz
                            b.vx += frictionImpulse * invB * tx
                            b.vy += frictionImpulse * invB * ty
                            b.vz += frictionImpulse * invB * tz
                            val spinImpulse = frictionImpulse * 2.2f
                            a.angularX += (ny * tz - nz * ty) * spinImpulse * invA
                            a.angularY += (nz * tx - nx * tz) * spinImpulse * invA
                            a.angularZ += (nx * ty - ny * tx) * spinImpulse * invA
                            b.angularX -= (ny * tz - nz * ty) * spinImpulse * invB
                            b.angularY -= (nz * tx - nx * tz) * spinImpulse * invB
                            b.angularZ -= (nx * ty - ny * tx) * spinImpulse * invB
                        }
                    }
                    val correction = ((minDistance - distance - 0.003f).coerceAtLeast(0f) * 0.72f) / inverseMassSum
                    a.x -= nx * correction * invA; a.y -= ny * correction * invA; a.z -= nz * correction * invA
                    b.x += nx * correction * invB; b.y += ny * correction * invB; b.z += nz * correction * invB
                }
            }
        }

        bodies.forEach { setBodyTransform(transformManager, it) }
    }

    private fun updateLetterBodies(
        activeScene: Scene,
        transformManager: com.google.android.filament.TransformManager,
        letters: List<LetterBody>,
        progress: Float,
        startProgress: Float,
        impactProgress: Float,
        windStart: Float,
        isTitle: Boolean
    ) {
        var added = if (isTitle) titleAdded else nameAdded
        if (progress < startProgress) {
            if (added) letters.forEach { activeScene.removeEntity(it.body.mesh.entity) }
            added = false
            if (isTitle) titleAdded = false else nameAdded = false
            return
        }
        if (!added) {
            letters.forEach { activeScene.addEntity(it.body.mesh.entity) }
            added = true
            if (isTitle) titleAdded = true else nameAdded = true
        }

        val fall = ((progress - startProgress) / (impactProgress - startProgress)).coerceIn(0f, 1f)
        val easedFall = fall * fall * (3f - 2f * fall)
        val windTime = ((progress - windStart) * 9f).coerceAtLeast(0f)
        letters.forEachIndexed { index, letter ->
            val body = letter.body
            if (progress < impactProgress) {
                body.x = letter.homeX
                body.y = letter.startY + (letter.homeY - letter.startY) * easedFall +
                    sin(fall * PI).toFloat() * abs(letter.fallVelocity) * 0.025f
                body.z = 0.34f
                body.rx = -5f * fall
                body.ry = (if (index % 2 == 0) 1f else -1f) * 3f * fall
                body.rz = (if (index % 2 == 0) 1f else -1f) * 2.5f * fall
            } else if (progress < windStart) {
                body.x = letter.homeX
                body.y = letter.homeY
                body.z = 0.34f
                body.rx = 0f
                body.ry = 0f
                body.rz = 0f
            } else {
                val side = if (letter.homeX < 0f) -1f else 1f
                body.x = letter.homeX + (2.15f + index * 0.018f) * windTime + 1.25f * windTime * windTime
                body.y = letter.homeY + (0.72f + (index % 3) * 0.12f) * windTime - 2.25f * windTime * windTime
                body.z = 0.34f + sin(windTime * 4f + index) * 0.32f
                body.rx = windTime * (135f + index * 2f)
                body.ry = windTime * (112f + index * 3f)
                body.rz = windTime * (side * 165f + index * 4f)
            }
            setBodyTransform(transformManager, body)
        }
    }

    private fun updateCore(
        activeScene: Scene,
        transformManager: com.google.android.filament.TransformManager,
        progress: Float
    ) {
        val core = coreMesh ?: return
        val scale = when {
            progress < 0.16f -> 0.012f
            progress < 0.245f -> 0.08f + ((progress - 0.16f) / 0.085f).coerceIn(0f, 1f) * 0.92f
            progress < 0.82f -> 1f + sin(progress * 42f).toFloat() * 0.055f
            else -> (1f - (progress - 0.82f) / 0.18f).coerceIn(0f, 1f)
        }
        val matrix = FloatArray(16)
        GlMatrix.setIdentityM(matrix, 0)
        GlMatrix.scaleM(matrix, 0, scale, scale, scale)
        transformManager.setTransform(transformManager.getInstance(core.entity), matrix)
        if (progress >= 0.82f && !coreLightRemoved && coreLightEntity != 0) {
            activeScene.removeEntity(coreLightEntity)
            coreLightRemoved = true
        }
    }

    private fun setBodyTransform(
        transformManager: com.google.android.filament.TransformManager,
        body: Body
    ) {
        val instance = transformManager.getInstance(body.mesh.entity)
        val matrix = FloatArray(16)
        GlMatrix.setIdentityM(matrix, 0)
        GlMatrix.translateM(matrix, 0, body.x, body.y, body.z)
        GlMatrix.rotateM(matrix, 0, body.rx, 1f, 0f, 0f)
        GlMatrix.rotateM(matrix, 0, body.ry, 0f, 1f, 0f)
        GlMatrix.rotateM(matrix, 0, body.rz, 0f, 0f, 1f)
        transformManager.setTransform(instance, matrix)
    }

    fun release() {
        if (released) return
        released = true
        isReady = false
        pauseRendering()
        runCatching { uiHelper?.detach() }.onFailure {
            android.util.Log.w("Genesis3D", "Filament surface detach failed during release", it)
        }
        uiHelper = null
        val activeEngine = engine
        if (activeEngine != null) {
            fun attempt(label: String, action: () -> Unit) {
                runCatching(action).onFailure {
                    android.util.Log.w("Genesis3D", "Filament resource cleanup failed: $label", it)
                }
            }
            attempt("flush") { activeEngine.flushAndWait() }
            swapChain?.let { chain -> attempt("swap chain") { activeEngine.destroySwapChain(chain) } }
            swapChain = null

            fun destroyMesh(mesh: Mesh) {
                attempt("scene mesh entity") { scene?.removeEntity(mesh.entity) }
                attempt("mesh entity") { activeEngine.destroyEntity(mesh.entity) }
                attempt("mesh vertex buffer") { activeEngine.destroyVertexBuffer(mesh.vertexBuffer) }
                attempt("mesh index buffer") { activeEngine.destroyIndexBuffer(mesh.indexBuffer) }
                attempt("entity manager mesh") { EntityManager.get().destroy(mesh.entity) }
            }
            bodies.toList().forEach { destroyMesh(it.mesh) }
            titleBodies.toList().forEach { destroyMesh(it.body.mesh) }
            nameBodies.toList().forEach { destroyMesh(it.body.mesh) }
            shieldMesh?.let(::destroyMesh)
            shieldRimMesh?.let(::destroyMesh)
            coreMesh?.let(::destroyMesh)

            if (lightEntity != 0) {
                attempt("key light scene entity") { scene?.removeEntity(lightEntity) }
                attempt("key light") { activeEngine.destroyEntity(lightEntity) }
                attempt("key light entity manager") { EntityManager.get().destroy(lightEntity) }
            }
            if (coreLightEntity != 0) {
                attempt("core light scene entity") { scene?.removeEntity(coreLightEntity) }
                attempt("core light") { activeEngine.destroyEntity(coreLightEntity) }
                attempt("core light entity manager") { EntityManager.get().destroy(coreLightEntity) }
            }
            rimLightEntities.toList().forEach { entity ->
                attempt("rim light scene entity") { scene?.removeEntity(entity) }
                attempt("rim light") { activeEngine.destroyEntity(entity) }
                attempt("rim light entity manager") { EntityManager.get().destroy(entity) }
            }

            shieldMaterial?.let { attempt("shield material instance") { activeEngine.destroyMaterialInstance(it) } }
            shieldRimMaterial?.let { attempt("shield rim material instance") { activeEngine.destroyMaterialInstance(it) } }
            debrisMaterial?.let { attempt("debris material instance") { activeEngine.destroyMaterialInstance(it) } }
            titleMaterial?.let { attempt("title material instance") { activeEngine.destroyMaterialInstance(it) } }
            coreMaterial?.let { attempt("core material instance") { activeEngine.destroyMaterialInstance(it) } }
            pbrMaterial?.let { attempt("PBR material") { activeEngine.destroyMaterial(it) } }
            attempt("scene indirect light detach") { scene?.indirectLight = null }
            attempt("scene skybox detach") { scene?.skybox = null }
            environmentIndirectLight?.let { attempt("indirect light") { activeEngine.destroyIndirectLight(it) } }
            environmentSkybox?.let { attempt("skybox") { activeEngine.destroySkybox(it) } }
            environmentReflectionTexture?.let { attempt("reflection texture") { activeEngine.destroyTexture(it) } }
            environmentSkyboxTexture?.let { attempt("environment skybox texture") { activeEngine.destroyTexture(it) } }
            filamentView?.let { attempt("Filament view") { activeEngine.destroyView(it) } }
            scene?.let { attempt("Filament scene") { activeEngine.destroyScene(it) } }
            camera?.let { cameraComponent ->
                attempt("camera component") { activeEngine.destroyCameraComponent(cameraComponent.entity) }
                attempt("camera entity") { EntityManager.get().destroy(cameraComponent.entity) }
            }
            renderer?.let { attempt("Filament renderer") { activeEngine.destroyRenderer(it) } }
            attempt("Filament engine") { activeEngine.destroy() }
        }
        bodies.clear()
        rimLightEntities.clear()
        titleBodies.clear()
        nameBodies.clear()
        shieldMesh = null
        shieldRimMesh = null
        coreMesh = null
        shieldMaterial = null
        shieldRimMaterial = null
        debrisMaterial = null
        titleMaterial = null
        coreMaterial = null
        pbrMaterial = null
        environmentIndirectLight = null
        environmentSkybox = null
        environmentReflectionTexture = null
        environmentSkyboxTexture = null
        lightEntity = 0
        coreLightEntity = 0
        engine = null
        renderer = null
        scene = null
        filamentView = null
        camera = null
        swapChain = null
    }

    override fun onDetachedFromWindow() {
        pauseRendering()
        super.onDetachedFromWindow()
    }
}
