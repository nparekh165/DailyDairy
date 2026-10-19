package com.khodiyar.dairybilling

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.CompositionLocalProvider
import com.khodiyar.dairybilling.data.prefs.Features
import com.khodiyar.dairybilling.data.prefs.UiPrefs
import com.khodiyar.dairybilling.ui.common.LocalFeatures
import com.khodiyar.dairybilling.domain.UiText
import com.khodiyar.dairybilling.ui.AppRoot
import com.khodiyar.dairybilling.ui.theme.AppTheme
import com.khodiyar.dairybilling.ui.theme.DairyTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val container get() = (application as DairyApp).container

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            if (container.session.expireIfIdle()) {
                ProcessLifecycleOwner.get().lifecycleScope.launch { container.prefs.clearSession() }
                container.notify(UiText.res(R.string.msg_auto_locked))
            }
        }

        override fun onStop(owner: LifecycleOwner) {
            val user = container.session.current
            ProcessLifecycleOwner.get().lifecycleScope.launch {
                if (user != null) container.prefs.saveSession(user.id, container.time.nowMillis()) else container.prefs.clearSession()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        setContent {
            val prefs by container.prefs.prefs.collectAsState(initial = UiPrefs())
            val features by container.prefs.features.collectAsState(initial = Features())
            CompositionLocalProvider(LocalFeatures provides features) {
                DairyTheme(prefs.theme) {
                    Box(Modifier.fillMaxSize().background(AppTheme.colors.background)) {
                        AppRoot()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        super.onDestroy()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        container.session.touch()
    }
}
