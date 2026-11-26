package com.example

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.game.ai.configureAppCheck
import com.example.game.gl.ViceGlRenderer
import com.example.game.state.ViceGameViewModel
import com.example.ui.hud.ViceHorizonScreen
import com.example.ui.theme.ViceHorizonTheme
import java.util.UUID

private const val LOCAL_PROFILE_PREFS = "vice_local_profile"
private const val KEY_LOCAL_UID = "local_uid"

private fun getOrCreateLocalPlayerUid(context: Context): String {
  val prefs = context.applicationContext.getSharedPreferences(LOCAL_PROFILE_PREFS, Context.MODE_PRIVATE)
  val existing = prefs.getString(KEY_LOCAL_UID, null)?.trim().orEmpty()
  if (existing.isNotEmpty()) return existing
  val created = "local_${UUID.randomUUID().toString().replace("-", "").take(16)}"
  prefs.edit().putString(KEY_LOCAL_UID, created).apply()
  return created
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configureAppCheck(this, intent)
    if (requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
      requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )
    setContent {
      ViceHorizonTheme {
        val context = LocalContext.current
        val localPrefs = remember(context) {
          context.applicationContext.getSharedPreferences(LOCAL_PROFILE_PREFS, Context.MODE_PRIVATE)
        }
        val localUid = remember(context) { getOrCreateLocalPlayerUid(context) }
        val vm: ViceGameViewModel = viewModel(
          key = localUid,
          factory = viewModelFactory {
            initializer {
              ViceGameViewModel(
                cloudRepository = null,
                currentUserId = localUid,
                initialFirebaseUser = null,
                localPrefs = localPrefs,
              )
            }
          },
        )
        val hud by vm.hudState.collectAsStateWithLifecycle()
        val permanentCode by vm.permanentPlayerCode.collectAsStateWithLifecycle()
        val renderer = remember(vm) {
          ViceGlRenderer(sim = vm.simulation, onHudTick = { vm.refreshHudFromGlThread() })
        }

        ViceHorizonScreen(
          sim = vm.simulation,
          renderer = renderer,
          hud = hud,
          voiceManager = vm.voiceChatManager,
          aiAssistant = vm.aiAssistant,
          permanentPlayerCode = permanentCode,
          onStartMatchFromLobby = { vm.startMatchFromLobby() },
          onInvitePeerById = { peerId -> vm.invitePeerById(peerId) },
          onAcceptPeerInvite = { vm.acceptPeerInvite() },
          onDeclinePeerInvite = { vm.declinePeerInvite() },
          onRemoveRemoteSquadMember = { id -> vm.removeRemoteSquadMember(id) },
          onSyncLobbyState = { vm.syncLobbyStateToPeers() },
          onToggleMute = { vm.toggleMute() },
          onHudForceRefresh = { vm.refreshHudFromGlThread() },
        )
      }
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      runCatching {
        WindowCompat.getInsetsController(window, window.decorView).apply {
          systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
          hide(WindowInsetsCompat.Type.systemBars())
        }
      }
    }
  }
}
