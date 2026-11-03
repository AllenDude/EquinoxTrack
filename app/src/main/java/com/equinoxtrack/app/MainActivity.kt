package com.equinoxtrack.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.equinoxtrack.app.domain.ThemeMode
import com.equinoxtrack.app.ui.AppRoot
import com.equinoxtrack.app.ui.DeepLinks
import kotlinx.coroutines.runBlocking

/**
 * Single activity hosting the Compose UI. It is a FragmentActivity because BiometricPrompt needs one.
 * Deep links from widgets arrive as an extra and are handed to the navigation graph.
 */
class MainActivity : FragmentActivity() {
    private var locked by mutableStateOf(false)
    private var deepLink by mutableStateOf<String?>(null)
    private var backgroundedAt = 0L
    private var prompting = false

    private val container get() = appContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A tiny synchronous read keeps the app from flashing content before the lock screen appears.
        val lockEnabled = runBlocking { container.settings.current().biometricLock }
        locked = lockEnabled && savedInstanceState?.getBoolean(KEY_UNLOCKED) != true
        deepLink = intent?.getStringExtra(DeepLinks.EXTRA)

        setContent {
            val settings by container.settingsState.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings?.themeMode ?: ThemeMode.SYSTEM) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
                onDispose { }
            }
            AppRoot(
                settings = settings,
                locked = locked,
                onUnlock = { authenticate() },
                deepLink = deepLink,
                onDeepLinkHandled = { deepLink = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(DeepLinks.EXTRA)?.let { deepLink = it }
    }

    override fun onResume() {
        super.onResume()
        if (locked) authenticate()
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onRestart() {
        super.onRestart()
        val enabled = container.settingsState.value?.biometricLock == true
        if (enabled && SystemClock.elapsedRealtime() - backgroundedAt > LOCK_AFTER_MS) locked = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_UNLOCKED, !locked)
    }

    private fun authenticate() {
        if (prompting) return
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            // The device lost its screen lock: never trap the user outside their own data.
            locked = false
            return
        }
        prompting = true
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                prompting = false
                locked = false
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                prompting = false
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock EquinoxTrack")
                .setSubtitle("Confirm it's you")
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }

    companion object {
        private const val KEY_UNLOCKED = "unlocked"
        private const val LOCK_AFTER_MS = 30_000L
    }
}
