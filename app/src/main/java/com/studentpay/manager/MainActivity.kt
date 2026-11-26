package com.studentpay.manager

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.studentpay.manager.data.ThemeMode
import com.studentpay.manager.ui.AppRoot
import com.studentpay.manager.ui.AppViewModel
import com.studentpay.manager.ui.theme.StudentPayTheme

class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels(factoryProducer = {
        viewModelFactory {
            initializer {
                val c = (application as App).container
                AppViewModel(c.pin, c.settings, c.repository, c.reports)
            }
        }
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by appViewModel.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Keep system-bar icon colours in step with the in-app theme choice.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.argb(0xe6, 0xFF, 0xFF, 0xFF),
                        Color.argb(0x80, 0x1b, 0x1b, 0x1b),
                    ) { dark },
                )
                onDispose { }
            }
            // FLAG_SECURE: hides content in Recents and blocks screenshots (user can turn it off).
            DisposableEffect(settings.hideInRecents) {
                if (settings.hideInRecents) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                onDispose { }
            }

            StudentPayTheme(darkTheme = dark) { AppRoot(appViewModel) }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) appViewModel.onAppStopped(SystemClock.elapsedRealtime())
    }

    override fun onStart() {
        super.onStart()
        appViewModel.onAppStarted(SystemClock.elapsedRealtime())
    }
}
