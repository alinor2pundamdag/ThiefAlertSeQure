package com.thiefalertsequre.app

import android.content.Intent
import android.graphics.Paint as AndroidPaint
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import androidx.annotation.RequiresApi
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.OnBackPressedCallback
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The original owner-specified Seismic Genesis sequence.
 * It is a cinematic first-launch intro, not a skippable onboarding carousel.
 */
class SeismicGenesisIntroActivity : FragmentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var impactTone: ToneGenerator? = null
    private var completionAttempts = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val replay = intent.getBooleanExtra(EXTRA_REPLAY, false)
        if (ProtectionStore(this).read().mode != ProtectionMode.DISARMED) {
            if (replay) {
                android.widget.Toast.makeText(
                    this,
                    "Intro replay is unavailable while protection is active.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            if (!replay) openMain()
            finish()
            return
        }

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!replay && SafePreferences.boolean(prefs, KEY_COMPLETED, false)) {
            openMain()
            return
        }

        // There is intentionally no Skip button. Back is consumed until the sequence completes.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        val reducedMotion = AppearanceStudioStore.reduceMotion(this)
        val introAudio = AppearanceStudioStore.audio(this)
        val introHaptics = AppearanceStudioStore.haptics(this)

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                SeismicGenesisIntroContent(
                    reducedMotion = reducedMotion,
                    onImpact = { stage -> playOptionalImpact(stage, introAudio, introHaptics) },
                    onFinished = ::complete
                )
            }
        }
        setContentView(composeView)
    }

    private fun playOptionalImpact(stage: Int, audioEnabled: Boolean, hapticsEnabled: Boolean) {
        if (hapticsEnabled) runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(Vibrator::class.java)
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            }
            if (vibrator?.hasVibrator() == true) {
                val duration = if (stage == 2) 85L else 42L
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, if (stage == 2) 180 else 90))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(duration)
                }
            }
        }
        if (audioEnabled) runCatching {
            impactTone?.release()
            impactTone = ToneGenerator(AudioManager.STREAM_MUSIC, 42).also { tone ->
                tone.startTone(
                    if (stage == 2) ToneGenerator.TONE_PROP_NACK else ToneGenerator.TONE_PROP_ACK,
                    if (stage == 2) 240 else 130
                )
                mainHandler.postDelayed({
                    if (impactTone === tone) {
                        runCatching { tone.release() }
                        impactTone = null
                    }
                }, 280L)
            }
        }
    }

    private fun complete() {
        val saved = getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_COMPLETED, true)
            .putLong(KEY_COMPLETED_AT, System.currentTimeMillis())
            .commit()
        if (!saved) {
            completionAttempts += 1
            android.widget.Toast.makeText(
                this,
                "The intro completion could not be saved. Retrying safely.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            if (completionAttempts < 3) {
                mainHandler.postDelayed({ if (!isFinishing) complete() }, 1_000L)
            }
            return
        }
        openMain()
    }

    private fun openMain() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { impactTone?.release() }
        impactTone = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_REPLAY = "replay_intro"
        private const val PREFS = "batch_three_intro_v1"
        private const val KEY_COMPLETED = "completed"
        private const val KEY_COMPLETED_AT = "completed_at"
        internal const val INTRO_DURATION_MS = 9_000
    }
}

@Composable
private fun SeismicGenesisIntroContent(
    reducedMotion: Boolean,
    onImpact: (Int) -> Unit,
    onFinished: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = (context as? LifecycleOwner)?.lifecycle
    val progress = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0f) }
    var completionSent by remember { mutableStateOf(false) }

    val stage = when {
        progress.value < 0.18f -> 0
        progress.value < 0.39f -> 1
        progress.value < 0.82f -> 2
        else -> 3
    }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            progress.snapTo(1f)
            logoScale.snapTo(1f)
            delay(1_200L)
            if (!completionSent) {
                completionSent = true
                onFinished()
            }
        } else {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.42f,
                    stiffness = 310f
                )
            )
        }
    }

    LaunchedEffect(stage, reducedMotion) {
        if (!reducedMotion && (stage == 1 || stage == 2)) onImpact(stage)
    }

    LaunchedEffect(lifecycle, reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        if (lifecycle == null) {
            progress.animateTo(1f, tween(SeismicGenesisIntroActivity.INTRO_DURATION_MS, easing = LinearEasing))
            if (!completionSent) {
                completionSent = true
                onFinished()
            }
        } else {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (progress.value < 1f) {
                    val remaining = (SeismicGenesisIntroActivity.INTRO_DURATION_MS * (1f - progress.value))
                        .toInt().coerceAtLeast(1)
                    progress.animateTo(1f, tween(remaining, easing = LinearEasing))
                }
                if (!completionSent) {
                    completionSent = true
                    onFinished()
                }
            }
        }
    }

    val spectrumTransition = rememberInfiniteTransition(label = "developer-name-spectrum")
    val spectrumPosition by spectrumTransition.animateFloat(
        initialValue = 0f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20_000, easing = LinearEasing)
        ),
        label = "twenty-light-colors"
    )
    val developerName = "Alinor Pundamdag"
    val concreteName = buildAnnotatedString { append(developerName) }
    val coloredName = buildAnnotatedString {
        developerName.forEachIndexed { index, character ->
            if (character.isWhitespace()) {
                append(character)
            } else {
                val phase = (spectrumPosition + index * 0.62f) % 20f
                val colorIndex = floor(phase).toInt().coerceIn(0, 19)
                val first = Color.hsv(colorIndex * 18f, 0.42f, 1f)
                val second = Color.hsv(((colorIndex + 1) % 20) * 18f, 0.42f, 1f)
                withStyle(SpanStyle(color = lerp(first, second, phase - colorIndex))) {
                    append(character)
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Capture BoxWithConstraints dimensions before entering nested layout scopes.
        val containerHeight = maxHeight
        val p = progress.value
        val shakeEnvelope = when {
            p in 0.245f..0.278333f -> sin(((p - 0.245f) / 0.033333f) * 6f * PI).toFloat() * 15f
            p in 0.405f..0.455f -> sin(((p - 0.405f) / 0.05f) * 8f * PI).toFloat() * 22f
            p in 0.82f..0.87f -> sin(((p - 0.82f) / 0.05f) * 6f * PI).toFloat() * 12f
            else -> 0f
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = shakeEnvelope }
        ) {
            GenesisFractureCanvas(
                progress = p,
                modifier = Modifier.fillMaxSize()
            )

            if (p < 0.43f) {
                Image(
                    painter = painterResource(R.drawable.app_logo),
                    contentDescription = "ThiefAlertSeQure shield icon emerging and disintegrating",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(116.dp)
                        .graphicsLayer {
                            val dissolve = ((p - 0.18f) / 0.22f).coerceIn(0f, 1f)
                            scaleX = logoScale.value * (1f - dissolve * 0.22f)
                            scaleY = logoScale.value * (1f - dissolve * 0.22f)
                            alpha = (1f - dissolve).coerceIn(0f, 1f)
                            rotationZ = dissolve * 8f
                        }
                )
            }

            if (p >= 0.18f && p < 0.48f) {
                val drop = ((p - 0.18f) / 0.10f).coerceIn(0f, 1f)
                val shake = if (p in 0.245f..0.278333f) shakeEnvelope else 0f
                Text(
                    text = "Developed By:",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = containerHeight * (-0.55f * (1f - drop)))
                        .graphicsLayer {
                            translationX = shake
                            rotationZ = shake / 16f
                            alpha = (1f - ((p - 0.36f) / 0.12f).coerceIn(0f, 1f))
                        },
                    color = Color(0xFFFF5252),
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 1.2.sp,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        shadow = Shadow(Color(0xFFFF5252).copy(alpha = 0.8f), blurRadius = 22f)
                    )
                )
            }

            if (p >= 0.39f) {
                val nameDrop = ((p - 0.39f) / 0.20f).coerceIn(0f, 1f)
                val finalFall = ((p - 0.82f) / 0.18f).coerceIn(0f, 1f)
                val concreteBrush = Brush.linearGradient(
                    listOf(Color(0xFFE1E1E1), Color(0xFF8B9097), Color(0xFFD1D4D8), Color(0xFF707780))
                )
                val initialConcrete = p < 0.47f
                Text(
                    text = if (initialConcrete) concreteName else coloredName,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(
                            y = if (p >= 0.82f) containerHeight * (1.25f * finalFall * finalFall)
                            else containerHeight * (-0.55f * (1f - nameDrop))
                        )
                        .graphicsLayer {
                            translationX = if (p in 0.405f..0.455f) shakeEnvelope else 0f
                            rotationZ = if (p < 0.48f) -4f * (1f - nameDrop) else 0f
                            alpha = if (p >= 0.82f) 1f - ((finalFall - 0.55f) / 0.45f).coerceIn(0f, 1f) else 1f
                            if (initialConcrete) compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            if (initialConcrete) {
                                // Cement aggregate flecks are SRC_ATOP-clipped to the letter glyphs.
                                repeat(72) { fleck ->
                                    val x = ((fleck * 37) % 997) / 997f * size.width
                                    val y = ((fleck * 71) % 991) / 991f * size.height
                                    drawCircle(
                                        color = if (fleck % 2 == 0) Color(0xFF40464D).copy(alpha = 0.34f)
                                            else Color.White.copy(alpha = 0.26f),
                                        radius = (0.35f + (fleck % 3) * 0.28f).dp.toPx(),
                                        center = Offset(x, y),
                                        blendMode = BlendMode.SrcAtop
                                    )
                                }
                            }
                        },
                    style = TextStyle(
                        brush = if (initialConcrete) concreteBrush else null,
                        shadow = Shadow(
                            color = if (initialConcrete) Color(0xFF000000) else Color(0xFF8FE9FF).copy(alpha = 0.32f),
                            blurRadius = if (initialConcrete) 18f else 10f
                        )
                    ),
                    fontSize = 35.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.4.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Material-first cinematic fracture plate. API 33+ uses AGSL to shade a layered
 * concrete surface and branching fractures on the GPU. Earlier Android versions
 * retain a safe layered Canvas fallback. Debris is animated independently using
 * radial impulse, drag, rotation and gravity.
 */
private const val GENESIS_CONCRETE_AGSL = """
uniform float2 resolution;
uniform float fractureProgress;

float hash21(float2 p) {
    p = fract(p * float2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}
float noise2(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + float2(1.0, 0.0)), f.x),
               mix(hash21(i + float2(0.0, 1.0)), hash21(i + float2(1.0, 1.0)), f.x), f.y);
}
float fbm(float2 p) {
    float value = 0.0;
    float amplitude = 0.52;
    for (int octave = 0; octave < 5; octave++) {
        value += noise2(p) * amplitude;
        p *= 2.03;
        amplitude *= 0.49;
    }
    return value;
}
float segmentDistance(float2 p, float2 a, float2 b) {
    float2 pa = p - a;
    float2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 0.00001), 0.0, 1.0);
    return length(pa - ba * h);
}
half4 main(float2 fragCoord) {
    float2 uv = fragCoord / resolution;
    float2 p = (uv - float2(0.5, 0.46)) * float2(resolution.x / resolution.y, 1.0);
    float broad = fbm(uv * float2(17.0, 25.0));
    float medium = fbm(uv * float2(91.0, 137.0));
    float fine = noise2(uv * resolution * 0.21);
    float aggregate = hash21(floor(uv * resolution * 0.38));
    float radius = length(p);
    float tonal = broad * 0.42 + medium * 0.34 + fine * 0.16 + aggregate * 0.08;
    float3 concrete = mix(float3(0.026, 0.031, 0.037), float3(0.205, 0.213, 0.219), tonal);
    concrete += (medium - 0.48) * float3(0.075, 0.078, 0.081);
    concrete -= smoothstep(0.22, 0.78, uv.y) * 0.025;
    concrete *= 0.72 + 0.20 * (1.0 - uv.x) + 0.08 * (1.0 - uv.y);

    float crackDistance = 2.0;
    float crackReach = 0.08 + fractureProgress * 0.50;
    for (int ray = 0; ray < 14; ray++) {
        float fi = float(ray);
        float angle = fi * 0.44879895 + (hash21(float2(fi, 17.0)) - 0.5) * 0.24;
        float reach = crackReach * (0.55 + hash21(float2(fi, 31.0)) * 0.70);
        float2 endPoint = float2(cos(angle), sin(angle)) * reach;
        crackDistance = min(crackDistance, segmentDistance(p, float2(0.0), endPoint));
        float branchAngle = angle + (hash21(float2(fi, 53.0)) - 0.5) * 1.12;
        float2 branchStart = endPoint * (0.38 + hash21(float2(fi, 71.0)) * 0.28);
        float2 branchEnd = branchStart + float2(cos(branchAngle), sin(branchAngle)) * reach * 0.48;
        crackDistance = min(crackDistance, segmentDistance(p, branchStart, branchEnd));
        float secondaryAngle = branchAngle + (hash21(float2(fi, 97.0)) - 0.5) * 0.92;
        float2 secondaryStart = mix(branchStart, branchEnd, 0.58);
        float2 secondaryEnd = secondaryStart + float2(cos(secondaryAngle), sin(secondaryAngle)) * reach * 0.22;
        crackDistance = min(crackDistance, segmentDistance(p, secondaryStart, secondaryEnd));
    }
    float gate = smoothstep(0.015, 0.20, fractureProgress);
    float width = 0.0011 + (1.0 - fractureProgress) * 0.00065;
    float core = (1.0 - smoothstep(width, width * 2.8, crackDistance)) * gate;
    float edge = (1.0 - smoothstep(width * 2.0, width * 7.0, crackDistance)) * gate;
    float craterRadius = 0.018 + fractureProgress * 0.115;
    float craterRim = exp(-abs(radius - craterRadius) * 82.0) * gate;
    float craterShadow = exp(-abs(radius - craterRadius * 0.82) * 36.0) * gate;
    concrete *= 1.0 - core * 0.94;
    concrete -= edge * float3(0.025, 0.030, 0.034);
    concrete += edge * float3(0.032, 0.040, 0.047) * (1.0 - core);
    concrete += craterRim * float3(0.045, 0.052, 0.057);
    concrete -= craterShadow * float3(0.035, 0.038, 0.041);
    concrete += (aggregate - 0.5) * 0.018;
    float reveal = smoothstep(0.0, 0.12, fractureProgress);
    return half4(half3(mix(float3(0.0), concrete, reveal)), 1.0);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun drawGenesisRuntimeShader(
    canvas: android.graphics.Canvas,
    shader: android.graphics.RuntimeShader,
    width: Float,
    height: Float,
    fracture: Float
) {
    shader.setFloatUniform("resolution", width, height)
    shader.setFloatUniform("fractureProgress", fracture)
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        this.shader = shader
        isDither = true
    }
    canvas.drawRect(0f, 0f, width, height, paint)
}

@Composable
private fun GenesisFractureCanvas(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val materialShader = remember {
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching { android.graphics.RuntimeShader(GENESIS_CONCRETE_AGSL) }.getOrNull()
        } else null
    }

    Canvas(modifier = modifier) {
        drawRect(Color(0xFF121212))
        val center = Offset(size.width * 0.5f, size.height * 0.46f)
        val fracture = ((progress - 0.23f) / 0.36f).coerceIn(0f, 1f)
        val native = drawContext.canvas.nativeCanvas

        if (fracture > 0f && materialShader != null) {
            runCatching {
                drawGenesisRuntimeShader(native, materialShader, size.width, size.height, fracture)
            }.onFailure {
                drawRect(Color(0xFF15191D).copy(alpha = fracture))
            }
        } else if (fracture > 0f) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF343A40), Color(0xFF171B1F), Color(0xFF080A0C)),
                    center = center,
                    radius = size.maxDimension * (0.62f + fracture * 0.22f)
                )
            )
            repeat(22) { index ->
                val angle = index * 2.3999632f
                val reach = size.maxDimension * fracture * (0.16f + ((index * 37) % 83) / 100f)
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(center.x, center.y)
                    for (segment in 1..5) {
                        val jitter = (((index * 19 + segment * 23) % 17) - 8) * 0.004f
                        val distance = reach * (segment / 5f)
                        lineTo(center.x + cos(angle + jitter) * distance, center.y + sin(angle + jitter) * distance)
                    }
                }
                drawPath(path, Color(0xFF060809).copy(alpha = fracture * 0.82f), style = Stroke(width = 2.0.dp.toPx()))
                drawPath(path, Color(0xFF737B82).copy(alpha = fracture * 0.26f), style = Stroke(width = 0.7.dp.toPx()))
            }
        }

        // Subdued impact illumination with a soft volumetric falloff.
        if (progress in 0.235f..0.29f) {
            val flash = (1f - ((progress - 0.235f) / 0.055f)).coerceIn(0f, 1f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFDDE7EC).copy(alpha = flash * 0.20f),
                        Color(0xFF8FA5B2).copy(alpha = flash * 0.075f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.46f
                ),
                radius = size.minDimension * 0.46f,
                center = center
            )
        }

        // Fine dust uses varied particle scales, radial impulse, drag and downward gravity.
        val dustProgress = ((progress - 0.18f) / 0.48f).coerceIn(0f, 1f)
        if (dustProgress > 0f) {
            val dustPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                style = AndroidPaint.Style.FILL
                isDither = true
            }
            repeat(132) { index ->
                val seed = ((index * 73 + 19) % 997) / 997f
                val angle = index * 2.3999632f + seed * 0.55f
                val travel = dustProgress * size.maxDimension * (0.10f + seed * 0.43f)
                val x = center.x + cos(angle) * travel
                val y = center.y + sin(angle) * travel * 0.78f +
                    dustProgress * dustProgress * size.height * (0.06f + seed * 0.16f)
                val life = (1f - dustProgress * (0.78f + seed * 0.18f)).coerceIn(0f, 1f)
                dustPaint.color = if (index % 4 == 0) android.graphics.Color.rgb(190, 198, 202)
                    else android.graphics.Color.rgb(105, 116, 124)
                dustPaint.alpha = (life * (34f + seed * 100f)).toInt().coerceIn(0, 120)
                val radius = (0.45f + seed * 1.65f) * (1f + dustProgress * 0.25f)
                native.drawOval(x - radius, y - radius * 0.58f, x + radius, y + radius * 0.58f, dustPaint)
            }
        }

        // Angular concrete fragments receive independent impulse, rotation and gravity.
        val shardProgress = ((progress - 0.235f) / 0.70f).coerceIn(0f, 1f)
        if (shardProgress > 0f) {
            repeat(30) { index ->
                val seed = ((index * 61 + 7) % 991) / 991f
                val angle = index * 2.3999632f + seed * 0.42f
                val travel = shardProgress * size.maxDimension * (0.12f + seed * 0.46f)
                val x = center.x + cos(angle) * travel
                val y = center.y + sin(angle) * travel * 0.66f +
                    shardProgress * shardProgress * size.height * (0.10f + seed * 0.20f)
                val life = (1f - shardProgress * (0.72f + seed * 0.24f)).coerceIn(0f, 1f)
                val shardSize = (2.0f + seed * 8.0f) * (1f - shardProgress * 0.12f)
                val fragment = android.graphics.Path().apply {
                    moveTo(-shardSize, -shardSize * 0.32f)
                    lineTo(shardSize * 0.78f, -shardSize * 0.62f)
                    lineTo(shardSize * 0.42f, shardSize * 0.72f)
                    lineTo(-shardSize * 0.58f, shardSize * 0.48f)
                    close()
                }
                val shadowPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.BLACK
                    alpha = (life * 100f).toInt().coerceIn(0, 100)
                }
                val facePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    color = if (index % 3 == 0) android.graphics.Color.rgb(154, 160, 163)
                        else android.graphics.Color.rgb(83, 92, 99)
                    alpha = (life * 205f).toInt().coerceIn(0, 205)
                }
                val save = native.save()
                native.translate(x, y)
                native.rotate(index * 41f + shardProgress * (210f + seed * 460f))
                native.drawPath(fragment, shadowPaint)
                native.translate(-shardSize * 0.13f, -shardSize * 0.13f)
                native.drawPath(fragment, facePaint)
                native.restoreToCount(save)
            }
        }

        // Developer-label dust: varied sizes, soft opacity and ballistic downward drift.
        val titleDustProgress = ((progress - 0.39f) / 0.17f).coerceIn(0f, 1f)
        if (titleDustProgress > 0f) {
            val titleCenter = Offset(size.width * 0.5f, size.height * 0.43f)
            val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(184, 154, 139)
                style = AndroidPaint.Style.FILL
            }
            repeat(92) { index ->
                val seed = ((index * 47 + 13) % 983) / 983f
                val angle = index * 2.3999632f
                val distance = 8f + titleDustProgress * size.maxDimension * (0.12f + seed * 0.28f)
                val x = titleCenter.x + cos(angle) * distance
                val y = titleCenter.y + sin(angle) * distance * 0.48f +
                    titleDustProgress * titleDustProgress * size.height * (0.05f + seed * 0.10f)
                paint.alpha = ((1f - titleDustProgress) * (28f + seed * 115f)).toInt().coerceIn(0, 143)
                val radius = 0.45f + seed * 1.2f
                native.drawOval(x - radius, y - radius * 0.65f, x + radius, y + radius * 0.65f, paint)
            }
        }

        // Final wind streaks transition into the Privacy Notice & Terms screen.
        val windProgress = ((progress - 0.82f) / 0.18f).coerceIn(0f, 1f)
        if (windProgress > 0f) {
            val windPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(166, 183, 194)
                alpha = ((1f - windProgress) * 82f).toInt().coerceIn(0, 82)
                strokeWidth = 0.8f
            }
            repeat(56) { index ->
                val y = (index * 37f + windProgress * size.height * 0.72f) % size.height
                val x = (index * 67f + windProgress * size.width * 1.7f) % size.width
                native.drawLine(x, y, x + 10f + windProgress * 28f, y - 0.8f, windPaint)
            }
        }
    }
}
