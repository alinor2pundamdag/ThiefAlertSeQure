package com.thiefalertsequre.app

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Authentication-gated, searchable viewer for encrypted local event and evidence history. */
class LocalTamperHistoryActivity : FragmentActivity() {
    private lateinit var root: LinearLayout
    private lateinit var search: EditText
    private lateinit var rows: LinearLayout
    private var activeCategory = "all"
    private var authenticatedUntilElapsedMs = 0L
    private var biometricPrompt: BiometricPrompt? = null
    private var biometricChallenge: ByteArray? = null
    private var sessionExpiryRunnable: Runnable? = null
    private var authenticationRequested = false
    private var waitingForSecureLockSetup = false
    private val filterButtons = linkedMapOf<String, MaterialButton>()

    private val exportDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) exportCsv(uri)
    }

    private val credentialResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) grantAccess()
        else {
            authenticatedUntilElapsedMs = 0L
            renderLocked("Android owner verification was cancelled or not completed. History remains locked.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        renderLocked()
        requestAuthentication()
    }

    override fun onDestroy() {
        biometricPrompt?.cancelAuthentication()
        biometricPrompt = null
        clearBiometricChallenge()
        sessionExpiryRunnable?.let { expiry ->
            if (::root.isInitialized) root.removeCallbacks(expiry)
        }
        sessionExpiryRunnable = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (waitingForSecureLockSetup && getSystemService(KeyguardManager::class.java).isDeviceSecure) {
            waitingForSecureLockSetup = false
            authenticationRequested = false
            renderLocked("Secure Android screen lock detected. Verify your identity to open the history.")
        } else if (authenticatedUntilElapsedMs > 0L &&
            !LocalHistorySessionPolicy.isActive(SystemClock.elapsedRealtime(), authenticatedUntilElapsedMs)
        ) {
            authenticatedUntilElapsedMs = 0L
            renderLocked("The two-minute history access session expired. Authenticate again to continue.")
        } else if (::root.isInitialized) {
            ThemeRuntime.apply(this, root)
        }
    }

    private fun requestAuthentication() {
        if (authenticationRequested) return
        authenticationRequested = true
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isDeviceSecure) {
            waitingForSecureLockSetup = true
            renderLocked("Set a secure Android PIN, pattern or password before opening private event history.")
            return
        }
        waitingForSecureLockSetup = false
        val biometricAvailable = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        if (!biometricAvailable) {
            launchDeviceCredential()
            return
        }
        val cryptoCipher = runCatching { LocalHistoryBiometricCrypto.createCipher() }.getOrElse {
            authenticationRequested = false
            renderLocked("Secure biometric verification is unavailable. History remains locked; use device credential verification.")
            launchDeviceCredential()
            return
        }
        val challenge = LocalHistoryBiometricCrypto.newChallenge()
        biometricChallenge = challenge
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    biometricPrompt = null
                    val challenge = biometricChallenge
                    val proofValid = runCatching {
                        LocalHistoryBiometricCrypto.verify(result.cryptoObject?.cipher, challenge ?: byteArrayOf())
                    }.getOrDefault(false)
                    clearBiometricChallenge()
                    if (isFinishing || isDestroyed) return
                    if (proofValid) {
                        grantAccess()
                    } else {
                        authenticationRequested = false
                        renderLocked("Secure biometric verification could not complete its Android Keystore operation. History remains locked; use device credential verification.")
                    }
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    biometricPrompt = null
                    clearBiometricChallenge()
                    if (isFinishing || isDestroyed) return
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) launchDeviceCredential()
                    else {
                        authenticationRequested = false
                        renderLocked("Authentication was not completed. $errString")
                    }
                }
                override fun onAuthenticationFailed() {
                    android.widget.Toast.makeText(this@LocalTamperHistoryActivity,
                        "Biometric not recognized. Try again or use the device credential.",
                        android.widget.Toast.LENGTH_LONG).show()
                }
            })
        biometricPrompt = prompt
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock local protection history")
            .setSubtitle("Verify with your enrolled strong biometric or choose your device credential")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Use device PIN, pattern or password")
            .build()
        runCatching {
            prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cryptoCipher))
        }.onFailure {
            clearBiometricChallenge()
            biometricPrompt = null
            authenticationRequested = false
            renderLocked("Secure biometric verification could not start. History remains locked; use device credential verification.")
        }
    }

    @Suppress("DEPRECATION")
    private fun launchDeviceCredential() {
        val intent = getSystemService(KeyguardManager::class.java).createConfirmDeviceCredentialIntent(
            "Verify device owner", "Enter your existing phone PIN, pattern or password"
        )
        if (intent == null) {
            authenticationRequested = false
            renderLocked("Android could not open device credential verification. History remains locked.")
            return
        }
        credentialResult.launch(intent)
    }

    private fun grantAccess() {
        authenticationRequested = false
        authenticatedUntilElapsedMs = SystemClock.elapsedRealtime() + SESSION_MS
        renderUnlocked()
        sessionExpiryRunnable?.let(root::removeCallbacks)
        val expiry = Runnable {
            sessionExpiryRunnable = null
            if (!isFinishing && !isDestroyed && authenticatedUntilElapsedMs > 0L &&
                SystemClock.elapsedRealtime() >= authenticatedUntilElapsedMs
            ) {
                authenticatedUntilElapsedMs = 0L
                renderLocked("The two-minute history access session expired. Authenticate again to continue.")
            }
        }
        sessionExpiryRunnable = expiry
        root.postDelayed(expiry, SESSION_MS)
    }

    private fun renderLocked(message: String = "Local event history and encrypted evidence are protected by Android owner authentication.") {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(30))
            setBackgroundColor(getColor(R.color.tas_background))
        }
        scroll.addView(root)
        root.addView(button("‹  Back") { finish() })
        root.addView(space(18))
        root.addView(text("LOCAL TAMPER HISTORY", 11f, R.color.tas_primary, true))
        root.addView(space(6))
        root.addView(text("Owner verification required", 26f, R.color.tas_text, true).markAsPageHeading())
        root.addView(space(12))
        root.addView(text(message, 14f, R.color.tas_muted))
        root.addView(space(14))
        if (!getSystemService(KeyguardManager::class.java).isDeviceSecure) {
            root.addView(button("Open Android screen-lock settings") {
                AndroidDeviceCompatibility.open(this, Intent(Settings.ACTION_SECURITY_SETTINGS), Intent(Settings.ACTION_SETTINGS))
            })
        } else {
            root.addView(button("Verify with Android") {
                authenticationRequested = false
                requestAuthentication()
            })
        }
        root.addView(space(10))
        root.addView(text("No event details, location snapshots or evidence metadata are shown before successful owner verification.", 12f, R.color.tas_muted))
        setContentView(scroll)
        AndroidDeviceCompatibility.applySystemBarInsets(scroll)
        ThemeRuntime.apply(this, root)
    }

    private fun renderUnlocked() {
        val historyHealthy = LocalTamperHistory.verifyIntegrity(this)
        val evidenceHealthy = LocalEvidenceVault.verifyIntegrity(this)
        val history = if (historyHealthy) LocalTamperHistory.read(this, 100) else emptyList()
        val evidence = if (evidenceHealthy) LocalEvidenceVault.read(this, 100) else emptyList()
        val scroll = ScrollView(this).apply { isFillViewport = true }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(32))
            setBackgroundColor(getColor(R.color.tas_background))
        }
        scroll.addView(root)
        root.addView(button("‹  Back") { finish() })
        root.addView(space(16))
        root.addView(text("PROTECTION EVENT TIMELINE", 11f, R.color.tas_primary, true))
        root.addView(space(6))
        root.addView(text("Local Tamper History", 27f, R.color.tas_text, true).markAsPageHeading())
        root.addView(space(6))
        root.addView(text("Authenticated session • auto-locks after two minutes • local-only • screenshots blocked", 12f, R.color.tas_muted))
        root.addView(space(12))
        root.addView(text(
            "Event log: ${if (historyHealthy) history.size else "integrity failure"} • Encrypted archive: ${if (evidenceHealthy) evidence.size else "integrity failure"}",
            12f, R.color.tas_muted
        ))
        if (!historyHealthy || !evidenceHealthy) {
            root.addView(space(8))
            root.addView(text("WARNING: Encrypted history integrity/decryption failed. Stored data is preserved; export is disabled. Preserve app data for diagnosis.", 13f, R.color.tas_danger, true))
        }
        if (evidence.any { LocalEvidenceVaultPolicy.isFutureDated(System.currentTimeMillis(), it.timestampEpochMs) }) {
            root.addView(space(8))
            root.addView(text(
                "Some evidence timestamps are ahead of the device clock. Records are retained; review Android automatic date and time settings.",
                12f, R.color.tas_warning, true
            ))
        }
        root.addView(space(12))
        search = EditText(this).apply {
            hint = "Search event code or redacted detail"
            setSingleLine(true)
            textSize = 14f
            setTextColor(getColor(R.color.tas_text))
            setHintTextColor(getColor(R.color.tas_muted))
            contentDescription = "Search local protection events"
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(space(8))
        val filterScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        filterButtons.clear()
        listOf(
            "all" to "All", "power" to "Power", "bluetooth" to "Bluetooth",
            "alarm" to "Alarm", "authentication" to "Authentication", "evidence" to "Evidence", "system" to "System"
        ).forEach { (key, label) ->
            val filterButton = button(if (activeCategory == key) "✓ $label" else label) {
                activeCategory = key
                filterButtons.forEach { (filterKey, item) ->
                    val filterLabel = when (filterKey) {
                        "all" -> "All"
                        "power" -> "Power"
                        "bluetooth" -> "Bluetooth"
                        "alarm" -> "Alarm"
                        "authentication" -> "Authentication"
                        "evidence" -> "Evidence"
                        else -> "System"
                    }
                    item.text = if (filterKey == activeCategory) "✓ $filterLabel" else filterLabel
                    item.isSelected = filterKey == activeCategory
                }
                renderRows(history, evidence, historyHealthy, evidenceHealthy)
            }
            filterButton.isSelected = key == activeCategory
            filterButtons[key] = filterButton
            filterRow.addView(filterButton)
            filterRow.addView(spaceHorizontal(6))
        }
        filterScroll.addView(filterRow)
        root.addView(filterScroll)
        root.addView(space(10))
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(rows, LinearLayout.LayoutParams(-1, -2))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderRows(history, evidence, historyHealthy, evidenceHealthy)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        renderRows(history, evidence, historyHealthy, evidenceHealthy)
        root.addView(space(12))
        root.addView(button("Export event history and evidence metadata (CSV)") {
            if (!requireAuthenticatedSession()) return@button
            if (historyHealthy && evidenceHealthy) exportDocument.launch("thiefalertsequre-events.csv")
            else summaryOrToast("Export disabled because integrity verification failed.")
        })
        root.addView(space(8))
        root.addView(outlinedButton("Clear local event timeline") {
            if (!requireAuthenticatedSession()) return@outlinedButton
            MaterialAlertDialogBuilder(this)
                .setTitle("Clear local event timeline?")
                .setMessage("This deletes only the encrypted event timeline. The separate encrypted evidence archive and front-camera photo vault are retained.")
                .setNegativeButton("Keep history", null)
                .setPositiveButton("Clear timeline") { _, _ ->
                    if (!requireAuthenticatedSession()) return@setPositiveButton
                    val cleared = LocalTamperHistory.clear(this)
                    if (cleared) LocalTamperHistory.record(this, "history_cleared", "Owner cleared the local event timeline", false)
                    summaryOrToast(if (cleared) "Local event timeline cleared." else "History could not be cleared.")
                    renderUnlocked()
                }.showThemed(this)
        })
        root.addView(space(8))
        root.addView(outlinedButton("Delete encrypted evidence metadata archive") {
            if (!requireAuthenticatedSession()) return@outlinedButton
            MaterialAlertDialogBuilder(this)
                .setTitle("Delete encrypted evidence archive?")
                .setMessage("This deletes encrypted event metadata and owner-saved location snapshots. It does not delete front-camera photos or the separate event timeline.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete archive") { _, _ ->
                    if (!requireAuthenticatedSession()) return@setPositiveButton
                    val deleted = LocalEvidenceVault.deleteAll(this)
                    if (deleted) LocalTamperHistory.record(this, "evidence_archive_deleted", "Owner deleted the encrypted evidence metadata archive", false)
                    summaryOrToast(if (deleted) "Encrypted evidence archive deleted." else "Evidence archive could not be deleted.")
                    renderUnlocked()
                }.showThemed(this)
        })
        setContentView(scroll)
        AndroidDeviceCompatibility.applySystemBarInsets(scroll)
        ThemeRuntime.apply(this, root)
    }

    private fun renderRows(history: List<String>, evidence: List<LocalEvidenceVault.Entry>,
        historyHealthy: Boolean, evidenceHealthy: Boolean) {
        if (!::rows.isInitialized) return
        rows.removeAllViews()
        val query = if (::search.isInitialized) search.text?.toString()?.trim().orEmpty() else ""
        if (!historyHealthy) {
            rows.addView(text("Event timeline unavailable: encrypted history integrity check failed.", 13f, R.color.tas_danger))
        } else {
            val filtered = history.filter { line ->
                val code = LocalTamperHistoryPolicy.eventCodeFromLine(line)
                LocalTamperHistoryPolicy.matchesCategory(code, activeCategory) &&
                    (query.isBlank() || line.contains(query, ignoreCase = true))
            }
            rows.addView(text("LOCAL EVENT LOG • ${filtered.size} matching events", 10f, R.color.tas_primary, true))
            if (filtered.isEmpty()) rows.addView(text("No matching events.", 13f, R.color.tas_muted))
            filtered.forEach { line ->
                val parts = line.split(" | ", limit = 3)
                addRecordCard(parts.getOrElse(0) { "Timestamp unavailable" },
                    parts.getOrElse(1) { "unknown_event" }, parts.getOrElse(2) { "No additional detail" })
            }
        }
        rows.addView(space(14))
        rows.addView(text("ENCRYPTED EVIDENCE ARCHIVE • INTEGRITY ${if (evidenceHealthy) "VERIFIED" else "FAILED"}",
            10f, if (evidenceHealthy) R.color.tas_primary else R.color.tas_danger, true))
        if (!evidenceHealthy) {
            rows.addView(text("Archive records are hidden because decryption or integrity verification failed.", 13f, R.color.tas_danger))
        } else {
            val filteredEvidence = evidence.filter { entry ->
                LocalTamperHistoryPolicy.matchesCategory(entry.event, activeCategory) &&
                    (query.isBlank() || "${entry.event} ${entry.detail}".contains(query, ignoreCase = true))
            }
            if (filteredEvidence.isEmpty()) rows.addView(text("No matching encrypted evidence records.", 13f, R.color.tas_muted))
            filteredEvidence.forEach { entry ->
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(entry.timestampEpochMs))
                val offset = formatOffset(entry.timezoneOffsetMinutes)
                addRecordCard("$stamp (UTC$offset)", entry.event, "${entry.detail}\nSHA-256: ${entry.integritySha256}")
            }
        }
    }

    private fun addRecordCard(timestamp: String, event: String, detail: String) {
        val card = MaterialCardView(this).apply {
            radius = dp(12).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = getColor(R.color.tas_surface_variant)
            setCardBackgroundColor(getColor(R.color.tas_surface))
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        body.addView(text(timestamp, 11f, R.color.tas_muted))
        body.addView(space(4))
        body.addView(text(event, 14f, R.color.tas_text, true))
        if (detail.isNotBlank()) {
            body.addView(space(4))
            body.addView(text(detail, 12f, R.color.tas_muted))
        }
        card.addView(body)
        rows.addView(card)
        rows.addView(space(7))
    }

    private fun exportCsv(uri: android.net.Uri) {
        // The document picker can remain open beyond the two-minute owner-authentication window.
        // Re-check here because its result callback can run after the Activity resumes.
        if (!requireAuthenticatedSession()) return
        if (!LocalTamperHistory.verifyIntegrity(this) || !LocalEvidenceVault.verifyIntegrity(this)) {
            summaryOrToast("Export disabled because integrity verification failed.")
            return
        }
        runCatching {
            val writer = contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)
                ?: error("Android could not open the selected export destination.")
            writer.use { output ->
                output.appendLine("record_type,timestamp,event,detail,timezone_offset_minutes,sha256")
                LocalTamperHistory.read(this, 100).asReversed().forEach { line ->
                    val parts = line.split(" | ", limit = 3)
                    output.appendLine(listOf("event_history", parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" },
                        parts.getOrElse(2) { "" }, "", "").joinToString(",") { csv(it) })
                }
                LocalEvidenceVault.read(this, 100).forEach { entry ->
                    output.appendLine(listOf("encrypted_evidence", entry.timestampEpochMs.toString(), entry.event,
                        entry.detail, entry.timezoneOffsetMinutes.toString(), entry.integritySha256)
                        .joinToString(",") { csv(it) })
                }
            }
        }.onSuccess { summaryOrToast("Event history and encrypted evidence metadata exported.") }
            .onFailure { summaryOrToast("Export failed: ${it.message}") }
    }

    private fun clearBiometricChallenge() {
        biometricChallenge?.fill(0)
        biometricChallenge = null
    }

    private fun requireAuthenticatedSession(): Boolean {
        if (LocalHistorySessionPolicy.isActive(SystemClock.elapsedRealtime(), authenticatedUntilElapsedMs)) {
            return true
        }
        authenticatedUntilElapsedMs = 0L
        renderLocked("The two-minute history access session expired. Authenticate again to continue.")
        return false
    }

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""
    private fun formatOffset(minutes: Int): String {
        val sign = if (minutes >= 0) "+" else "-"
        val absolute = kotlin.math.abs(minutes)
        return "$sign${absolute / 60}:${(absolute % 60).toString().padStart(2, '0')}"
    }
    private fun summaryOrToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }
    private fun button(value: String, click: () -> Unit) = MaterialButton(this).apply {
        text = value
        isAllCaps = false
        minHeight = dp(48)
        setOnClickListener { click() }
    }
    private fun outlinedButton(value: String, click: () -> Unit) = MaterialButton(this).apply {
        text = value
        isAllCaps = false
        minHeight = dp(48)
        strokeWidth = dp(1)
        strokeColor = android.content.res.ColorStateList.valueOf(getColor(R.color.tas_primary))
        backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.tas_surface))
        setTextColor(getColor(R.color.tas_text))
        setOnClickListener { click() }
    }
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(getColor(color))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun spaceHorizontal(width: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(width), 1) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object { private const val SESSION_MS = 120_000L }
}
