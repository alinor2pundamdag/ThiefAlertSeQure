package com.thiefalertsequre.app

import android.content.Intent
import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
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
                val targetAlignment = if (p >= 0.82f) Alignment.BottomCenter else Alignment.Center
                val concreteBrush = Brush.linearGradient(
                    listOf(Color(0xFFE1E1E1), Color(0xFF8B9097), Color(0xFFD1D4D8), Color(0xFF707780))
                )
                val initialConcrete = p < 0.47f
                Text(
                    text = if (initialConcrete) concreteName else coloredName,
                    modifier = Modifier
                        .align(targetAlignment)
                        .offset(y = if (p >= 0.82f) containerHeight * (-0.06f * finalFall) else containerHeight * (-0.55f * (1f - nameDrop)))
                        .graphicsLayer {
                            translationX = if (p in 0.405f..0.455f) shakeEnvelope else 0f
                            rotationZ = if (p < 0.48f) -4f * (1f - nameDrop) else 0f
                            alpha = 1f
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

@Composable
private fun GenesisFractureCanvas(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawRect(Color.Black)
        val center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.46f)
        val fracture = ((progress - 0.23f) / 0.36f).coerceIn(0f, 1f)
        if (fracture > 0f) {
            // A jagged radial fracture opens from the impact point and reveals #121212 underneath.
            val revealPath = androidx.compose.ui.graphics.Path().apply {
                val segments = 48
                for (point in 0..segments) {
                    val angle = point * (2f * PI.toFloat() / segments)
                    val edgeFactor = if (point % 2 == 0) 1f else 0.84f
                    val radius = maxOf(size.width, size.height) * fracture * edgeFactor
                    val x = center.x + cos(angle) * radius
                    val y = center.y + sin(angle) * radius
                    if (point == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(revealPath, Color(0xFF121212))
            val crackColor = Color(0xFF565D66).copy(alpha = fracture * 0.72f)
            val branches = listOf(
                listOf(0f to 0f, -0.08f to -0.06f, -0.13f to -0.02f, -0.20f to -0.12f, -0.27f to -0.16f),
                listOf(0f to 0f, 0.07f to -0.04f, 0.12f to -0.11f, 0.22f to -0.14f, 0.29f to -0.23f),
                listOf(0f to 0f, 0.03f to 0.09f, 0.10f to 0.15f, 0.12f to 0.25f, 0.20f to 0.32f),
                listOf(0f to 0f, -0.06f to 0.08f, -0.10f to 0.17f, -0.20f to 0.23f, -0.26f to 0.34f),
                listOf(0f to 0f, 0.12f to 0.02f, 0.20f to 0.05f, 0.27f to 0.12f),
                listOf(0f to 0f, -0.14f to 0.02f, -0.23f to 0.08f, -0.32f to 0.12f)
            )
            branches.forEachIndexed { index, points ->
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(center.x, center.y)
                    points.drop(1).forEach { (dx, dy) ->
                        lineTo(center.x + size.width * dx * fracture, center.y + size.height * dy * fracture)
                    }
                }
                drawPath(path, crackColor, style = Stroke(width = if (index % 2 == 0) 2.1.dp.toPx() else 1.2.dp.toPx()))
            }
        }

        // Dust fragments are transformed with an Android Matrix (translate + rotate) so the
        // original launcher artwork visibly breaks apart instead of merely fading away.
        val dustProgress = ((progress - 0.18f) / 0.24f).coerceIn(0f, 1f)
        if (dustProgress > 0f) {
            val native = drawContext.canvas.nativeCanvas
            val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(180, 205, 222)
                alpha = ((1f - dustProgress) * 210f).toInt().coerceIn(0, 210)
                style = AndroidPaint.Style.FILL
            }
            repeat(74) { index ->
                val angle = index * 2.3999632f
                val ring = 18f + (index % 9) * 6f
                val distance = ring + dustProgress * (maxOf(size.width, size.height) * (0.22f + (index % 5) * 0.045f))
                val x = center.x + cos(angle) * distance
                val y = center.y + sin(angle) * distance
                val radius = (1.2f + (index % 4) * 0.55f) * (1f - dustProgress * 0.55f)
                val matrix = Matrix().apply {
                    setTranslate(x, y)
                    postRotate((index * 17f) % 360f, x, y)
                }
                val checkpoint = native.save()
                native.concat(matrix)
                native.drawCircle(0f, 0f, radius, paint)
                native.restoreToCount(checkpoint)
            }
        }

        // The "Developed By:" title itself fractures into dust during the strongest impact.
        val titleDustProgress = ((progress - 0.39f) / 0.17f).coerceIn(0f, 1f)
        if (titleDustProgress > 0f) {
            val native = drawContext.canvas.nativeCanvas
            val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(255, 130, 130)
                alpha = ((1f - titleDustProgress) * 190f).toInt().coerceIn(0, 190)
                style = AndroidPaint.Style.FILL
            }
            val titleCenter = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.43f)
            repeat(88) { index ->
                val angle = index * 2.3999632f
                val distance = 12f + titleDustProgress * (size.maxDimension * (0.16f + (index % 6) * 0.035f))
                val x = titleCenter.x + cos(angle) * distance
                val y = titleCenter.y + sin(angle) * distance
                val matrix = Matrix().apply {
                    setTranslate(x, y)
                    postRotate((index * 23f) % 360f, x, y)
                }
                val checkpoint = native.save()
                native.concat(matrix)
                native.drawRect(-1.2f, -1.2f, 1.2f, 1.2f, paint)
                native.restoreToCount(checkpoint)
            }
        }

        // Final digital-wind streaks pull the sequence toward the Privacy Notice & Terms screen.
        val windProgress = ((progress - 0.82f) / 0.18f).coerceIn(0f, 1f)
        if (windProgress > 0f) {
            val native = drawContext.canvas.nativeCanvas
            val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(150, 165, 180)
                alpha = ((1f - windProgress) * 145f).toInt().coerceIn(0, 145)
                strokeWidth = 1.2f
            }
            repeat(42) { index ->
                val y = (index * 43f + windProgress * size.height * 0.72f) % size.height
                val x = (index * 71f + windProgress * size.width * 1.7f) % size.width
                native.drawLine(x, y, x + 16f + windProgress * 34f, y - 1.5f, paint)
            }
        }
    }
}
