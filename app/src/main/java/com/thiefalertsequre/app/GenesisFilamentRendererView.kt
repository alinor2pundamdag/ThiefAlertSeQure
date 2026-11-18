package com.thiefalertsequre.app

import android.content.Context
import android.graphics.Matrix
import android.opengl.GLES20
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
import com.google.android.filament.LightManager
import com.google.android.filament.Material
import com.google.android.filament.MathUtils
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.VertexBuffer
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.FilamentHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.filamat.MaterialBuilder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Actual Filament scene used by the Seismic Genesis intro.
 *
 * The shield and every debris shard are independently extruded triangle meshes.
 * Shards are integrated as rigid bodies (gravity, angular motion, floor response,
 * pairwise sphere contacts and the final directional wind impulse).
 */
class GenesisFilamentRendererView(context: Context) : TextureView(context) {
    private data class Point2(val x: Float, val y: Float)
    private data class Mesh(val entity: Int, val vertexBuffer: VertexBuffer, val indexBuffer: IndexBuffer)
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
        val radius: Float
    )

    companion object {
        init { Filament.init() }

        fun isSupported(context: Context): Boolean {
            if (android.os.Build.VERSION.SDK_INT < 26) return false
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                ?: return false
            return manager.deviceConfigurationInfo.reqGlEsVersion >= 0x30000
        }
    }

    private var engine: Engine? = null
    private var renderer: com.google.android.filament.Renderer? = null
    private var scene: Scene? = null
    private var filamentView: View? = null
    private var camera: Camera? = null
    private var uiHelper: UiHelper? = null
    private var swapChain: SwapChain? = null
    private var pbrMaterial: Material? = null
    private var shieldMaterial: com.google.android.filament.MaterialInstance? = null
    private var debrisMaterial: com.google.android.filament.MaterialInstance? = null
    private var shieldMesh: Mesh? = null
    private val bodies = mutableListOf<Body>()
    private var lightEntity = 0
    private var currentProgress = 0f
    private var lastPhysicsProgress = 0f
    private var debrisAdded = false
    private var running = false
    private var released = false
    private var surfaceWidth = 1
    private var surfaceHeight = 1

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
            scene?.skybox = Skybox.Builder().color(0f, 0f, 0f, 1f).build(activeEngine)

            val materialPackage = buildPbrMaterial(activeEngine)
            pbrMaterial = Material.Builder()
                .payload(materialPackage, materialPackage.remaining())
                .build(activeEngine)
            shieldMaterial = pbrMaterial?.createInstance()?.apply {
                setParameter("baseColor", Colors.RgbType.SRGB, 0.34f, 0.43f, 0.52f)
                setParameter("metallic", 0.92f)
                setParameter("roughness", 0.22f)
            }
            debrisMaterial = pbrMaterial?.createInstance()?.apply {
                setParameter("baseColor", Colors.RgbType.SRGB, 0.30f, 0.32f, 0.34f)
                setParameter("metallic", 0.18f)
                setParameter("roughness", 0.86f)
            }

            val shieldOutline = listOf(
                Point2(-0.78f, 0.82f), Point2(0.78f, 0.82f),
                Point2(0.73f, 0.10f), Point2(0.48f, -0.54f),
                Point2(0f, -0.94f), Point2(-0.48f, -0.54f),
                Point2(-0.73f, 0.10f)
            )
            shieldMesh = createExtrudedMesh(shieldOutline, 0.18f, shieldMaterial!!)
            scene?.addEntity(shieldMesh!!.entity)
            createFractureBodies(shieldOutline)

            lightEntity = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(0.82f, 0.88f, 1.0f)
                .intensity(72_000f)
                .direction(-0.42f, -0.72f, -1.0f)
                .castShadows(true)
                .build(activeEngine, lightEntity)
            scene?.addEntity(lightEntity)

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
        } catch (error: Throwable) {
            android.util.Log.e("Genesis3D", "Filament scene initialization failed", error)
            release()
        }
    }

    private fun buildPbrMaterial(activeEngine: Engine): ByteBuffer {
        MaterialBuilder.init()
        try {
            val result = MaterialBuilder()
                .platform(MaterialBuilder.Platform.MOBILE)
                .name("Genesis physically based concrete and shield")
                .shading(MaterialBuilder.Shading.LIT)
                .uniformParameter(MaterialBuilder.UniformType.FLOAT3, "baseColor")
                .uniformParameter(MaterialBuilder.UniformType.FLOAT, "metallic")
                .uniformParameter(MaterialBuilder.UniformType.FLOAT, "roughness")
                .material(
                    """
                    void material(inout MaterialInputs material) {
                        prepareMaterial(material);
                        material.baseColor.rgb = materialParams.baseColor;
                        material.metallic = materialParams.metallic;
                        material.roughness = materialParams.roughness;
                        material.clearCoat = 0.28;
                        material.clearCoatRoughness = 0.24;
                    }
                    """.trimIndent()
                )
                .optimization(MaterialBuilder.Optimization.NONE)
                .build(activeEngine)
            check(result.isValid) { "Filament PBR material compilation failed" }
            return result.buffer
        } finally {
            MaterialBuilder.shutdown()
        }
    }

    private fun createFractureBodies(outline: List<Point2>) {
        val center = Point2(0f, 0.05f)
        val subdivisions = 3
        for (edge in outline.indices) {
            val a = outline[edge]
            val b = outline[(edge + 1) % outline.size]
            for (part in 0 until subdivisions) {
                val t0 = part.toFloat() / subdivisions
                val t1 = (part + 1).toFloat() / subdivisions
                val p0 = Point2(a.x + (b.x - a.x) * t0, a.y + (b.y - a.y) * t0)
                val p1 = Point2(a.x + (b.x - a.x) * t1, a.y + (b.y - a.y) * t1)
                val cx = (center.x + p0.x + p1.x) / 3f
                val cy = (center.y + p0.y + p1.y) / 3f
                val local = listOf(
                    Point2(center.x - cx, center.y - cy),
                    Point2(p0.x - cx, p0.y - cy),
                    Point2(p1.x - cx, p1.y - cy)
                )
                val mesh = createExtrudedMesh(local, 0.15f, debrisMaterial!!)
                val dx = cx - center.x
                val dy = cy - center.y
                val length = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
                val seed = edge * subdivisions + part
                bodies += Body(
                    mesh = mesh,
                    x = cx, y = cy, z = 0f,
                    vx = dx / length * (1.35f + (seed % 4) * 0.18f),
                    vy = dy / length * (1.8f + (seed % 3) * 0.22f),
                    vz = if (seed % 2 == 0) 0.85f else -0.85f,
                    rx = 0f, ry = 0f, rz = 0f,
                    radius = 0.17f
                )
            }
        }
    }

    private fun createExtrudedMesh(
        polygon: List<Point2>,
        depth: Float,
        material: com.google.android.filament.MaterialInstance
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
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            val centerFront = floatArrayOf(centerX, centerY, halfDepth)
            val aFront = floatArrayOf(a.x, a.y, halfDepth)
            val bFront = floatArrayOf(b.x, b.y, halfDepth)
            addTriangle(centerFront, aFront, bFront, floatArrayOf(0f, 0f, 1f))

            val centerBack = floatArrayOf(centerX, centerY, -halfDepth)
            val aBack = floatArrayOf(a.x, a.y, -halfDepth)
            val bBack = floatArrayOf(b.x, b.y, -halfDepth)
            addTriangle(centerBack, bBack, aBack, floatArrayOf(0f, 0f, -1f))

            val edgeX = b.x - a.x
            val edgeY = b.y - a.y
            val edgeLength = sqrt(edgeX * edgeX + edgeY * edgeY).coerceAtLeast(0.0001f)
            val sideNormal = floatArrayOf(edgeY / edgeLength, -edgeX / edgeLength, 0f)
            val aFrontSide = floatArrayOf(a.x, a.y, halfDepth)
            val bFrontSide = floatArrayOf(b.x, b.y, halfDepth)
            val aBackSide = floatArrayOf(a.x, a.y, -halfDepth)
            val bBackSide = floatArrayOf(b.x, b.y, -halfDepth)
            addTriangle(aFrontSide, aBackSide, bBackSide, sideNormal)
            addTriangle(aFrontSide, bBackSide, bFrontSide, sideNormal)
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
        indices.forEach(indexData::putShort)
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
        val shieldTransform = transformManager.getInstance(shield.entity)
        if (progress < 0.245f) {
            if (debrisAdded) {
                bodies.forEach { activeScene.removeEntity(it.mesh.entity) }
                activeScene.addEntity(shield.entity)
                debrisAdded = false
            }
            val appear = (progress / 0.13f).coerceIn(0f, 1f)
            val scale = 0.015f + appear * 0.985f
            val matrix = FloatArray(16)
            GlMatrix.setIdentityM(matrix, 0)
            GlMatrix.scaleM(matrix, 0, scale, scale, scale)
            transformManager.setTransform(shieldTransform, matrix)
            return
        }

        if (!debrisAdded) {
            activeScene.removeEntity(shield.entity)
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
            body.rx += (body.vz * 82f + 38f) * delta
            body.ry += (body.vx * 57f + 24f) * delta
            body.rz += (body.vy * 48f + 35f) * delta

            if (body.y - body.radius < -2.55f) {
                body.y = -2.55f + body.radius
                if (body.vy < 0f) body.vy = -body.vy * 0.31f
                body.vx *= 0.82f
                body.vz *= 0.82f
            }
        }

        // Low-cost rigid-body contact approximation for the small, deterministic shard set.
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
                    if (relative < 0f) {
                        val impulse = -(1.0f + 0.32f) * relative * 0.5f
                        a.vx -= impulse * nx; a.vy -= impulse * ny; a.vz -= impulse * nz
                        b.vx += impulse * nx; b.vy += impulse * ny; b.vz += impulse * nz
                    }
                    val correction = (minDistance - distance) * 0.5f
                    a.x -= nx * correction; a.y -= ny * correction; a.z -= nz * correction
                    b.x += nx * correction; b.y += ny * correction; b.z += nz * correction
                }
            }
        }

        bodies.forEach { body ->
            val instance = transformManager.getInstance(body.mesh.entity)
            val matrix = FloatArray(16)
            GlMatrix.setIdentityM(matrix, 0)
            GlMatrix.translateM(matrix, 0, body.x, body.y, body.z)
            GlMatrix.rotateM(matrix, 0, body.rx, 1f, 0f, 0f)
            GlMatrix.rotateM(matrix, 0, body.ry, 0f, 1f, 0f)
            GlMatrix.rotateM(matrix, 0, body.rz, 0f, 0f, 1f)
            transformManager.setTransform(instance, matrix)
        }
    }

    fun release() {
        if (released) return
        released = true
        pauseRendering()
        runCatching { uiHelper?.detach() }
        uiHelper = null
        val activeEngine = engine
        if (activeEngine != null) {
            runCatching {
                bodies.forEach { body ->
                    activeEngine.destroyEntity(body.mesh.entity)
                    activeEngine.destroyVertexBuffer(body.mesh.vertexBuffer)
                    activeEngine.destroyIndexBuffer(body.mesh.indexBuffer)
                    EntityManager.get().destroy(body.mesh.entity)
                }
                shieldMesh?.let {
                    activeEngine.destroyEntity(it.entity)
                    activeEngine.destroyVertexBuffer(it.vertexBuffer)
                    activeEngine.destroyIndexBuffer(it.indexBuffer)
                    EntityManager.get().destroy(it.entity)
                }
                if (lightEntity != 0) {
                    activeEngine.destroyEntity(lightEntity)
                    EntityManager.get().destroy(lightEntity)
                }
                shieldMaterial?.let(activeEngine::destroyMaterialInstance)
                debrisMaterial?.let(activeEngine::destroyMaterialInstance)
                pbrMaterial?.let(activeEngine::destroyMaterial)
                filamentView?.let(activeEngine::destroyView)
                scene?.let(activeEngine::destroyScene)
                camera?.let { activeEngine.destroyCameraComponent(it.entity); EntityManager.get().destroy(it.entity) }
                renderer?.let(activeEngine::destroyRenderer)
                activeEngine.destroy()
            }
        }
        bodies.clear()
        shieldMesh = null
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
