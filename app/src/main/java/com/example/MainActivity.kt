package com.example

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.game.ai.configureAppCheck
import com.example.game.gl.ViceGlRenderer
import com.example.game.state.ViceGameViewModel
import com.example.ui.hud.ViceHorizonScreen
import com.example.ui.theme.ViceHorizonTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configureAppCheck(this, intent)
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )
    setContent {
      ViceHorizonTheme {
        val vm: ViceGameViewModel = viewModel()
        val hud by vm.hudState.collectAsStateWithLifecycle()
        val renderer = remember(vm) {
          ViceGlRenderer(sim = vm.simulation, onHudTick = { vm.refreshHudFromGlThread() })
        }

        ViceHorizonScreen(
          sim = vm.simulation,
          renderer = renderer,
          hud = hud,
          voiceManager = vm.voiceChatManager,
          aiAssistant = vm.aiAssistant,
          onStartMatchFromLobby = { vm.startMatchFromLobby() },
          onInvitePeerById = { peerId -> vm.invitePeerById(peerId) },
          onAcceptPeerInvite = { vm.acceptPeerInvite() },
          onSyncLobbyState = { vm.syncLobbyStateToPeers() },
          onToggleMute = { vm.toggleMute() },
          onHudForceRefresh = { vm.refreshHudFromGlThread() },
        )
      }
    }
    window.decorView.post {
      runCatching {
        WindowCompat.getInsetsController(window, window.decorView).apply {
          systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
          hide(WindowInsetsCompat.Type.systemBars())
        }
      }
    }
  }
}
