package com.example

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
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
import com.example.game.state.GameSimulation
import com.example.game.state.ViceGameViewModel
import com.example.ui.hud.ViceHorizonScreen
import com.example.ui.theme.ViceHorizonTheme
import java.util.UUID
import kotlin.math.hypot

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
  private var activeSimulation: GameSimulation? = null
  private val heldMovementKeys = HashSet<Int>()
  private var keyboardDrivingMovement = false
  private var keyboardDrivingVehicle = false

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
        SideEffect {
          activeSimulation = vm.simulation
        }
        val renderer = remember(vm, context) {
          ViceGlRenderer(
            sim = vm.simulation,
            onHudTick = { vm.refreshHudFromGlThread() },
            appContext = context.applicationContext,
          )
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

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    val sim = activeSimulation
    if (sim == null || !sim.isPlaying || sim.isPaused || sim.isGunShopOpen || sim.isBagOpen) {
      if (heldMovementKeys.isNotEmpty() || keyboardDrivingMovement || keyboardDrivingVehicle) {
        clearKeyboardMovement(sim)
      }
      return super.dispatchKeyEvent(event)
    }

    val keyCode = event.keyCode
    val isDirectionalKey =
      keyCode == KeyEvent.KEYCODE_W ||
        keyCode == KeyEvent.KEYCODE_S ||
        keyCode == KeyEvent.KEYCODE_A ||
        keyCode == KeyEvent.KEYCODE_D ||
        keyCode == KeyEvent.KEYCODE_DPAD_UP ||
        keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
        keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
    val isSprintKey = keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT
    val isJumpKey = keyCode == KeyEvent.KEYCODE_SPACE

    if (!isDirectionalKey && !isSprintKey && !isJumpKey) {
      return super.dispatchKeyEvent(event)
    }

    when (event.action) {
      KeyEvent.ACTION_DOWN -> {
        if (isJumpKey && event.repeatCount == 0) {
          sim.input.jumpQueued = true
        }
        if (isSprintKey) {
          heldMovementKeys.add(keyCode)
          sim.input.sprintHeld = true
        }
        if (isDirectionalKey) {
          heldMovementKeys.add(keyCode)
          applyHeldKeysToSimulation(sim)
        }
        return true
      }
      KeyEvent.ACTION_UP -> {
        heldMovementKeys.remove(keyCode)
        if (isSprintKey) {
          sim.input.sprintHeld =
            heldMovementKeys.contains(KeyEvent.KEYCODE_SHIFT_LEFT) ||
              heldMovementKeys.contains(KeyEvent.KEYCODE_SHIFT_RIGHT)
        }
        if (isDirectionalKey) {
          applyHeldKeysToSimulation(sim)
        }
        return true
      }
    }
    return super.dispatchKeyEvent(event)
  }

  private fun applyHeldKeysToSimulation(sim: GameSimulation) {
    val up = heldMovementKeys.contains(KeyEvent.KEYCODE_W) || heldMovementKeys.contains(KeyEvent.KEYCODE_DPAD_UP)
    val down = heldMovementKeys.contains(KeyEvent.KEYCODE_S) || heldMovementKeys.contains(KeyEvent.KEYCODE_DPAD_DOWN)
    val left = heldMovementKeys.contains(KeyEvent.KEYCODE_A) || heldMovementKeys.contains(KeyEvent.KEYCODE_DPAD_LEFT)
    val right = heldMovementKeys.contains(KeyEvent.KEYCODE_D) || heldMovementKeys.contains(KeyEvent.KEYCODE_DPAD_RIGHT)
    val anyDir = up || down || left || right

    if (sim.activeVehicle != null || sim.activeBoat != null) {
      if (anyDir) {
        keyboardDrivingVehicle = true
        sim.input.gasHeld = up
        sim.input.brakeHeld = down
        sim.input.steerLeft = left
        sim.input.steerRight = right
      } else if (keyboardDrivingVehicle) {
        keyboardDrivingVehicle = false
        sim.input.gasHeld = false
        sim.input.brakeHeld = false
        sim.input.steerLeft = false
        sim.input.steerRight = false
      }
      if (keyboardDrivingMovement) {
        keyboardDrivingMovement = false
        sim.input.moveX = 0f
        sim.input.moveY = 0f
      }
      return
    }

    if (anyDir) {
      var mx = (if (right) 1f else 0f) - (if (left) 1f else 0f)
      var my = (if (down) 1f else 0f) - (if (up) 1f else 0f)
      val mag = hypot(mx, my)
      if (mag > 1f) {
        mx /= mag
        my /= mag
      }
      keyboardDrivingMovement = true
      sim.input.moveX = mx
      sim.input.moveY = my
    } else if (keyboardDrivingMovement) {
      keyboardDrivingMovement = false
      sim.input.moveX = 0f
      sim.input.moveY = 0f
    }
  }

  private fun clearKeyboardMovement(sim: GameSimulation?) {
    heldMovementKeys.clear()
    if (sim != null) {
      if (keyboardDrivingMovement) {
        sim.input.moveX = 0f
        sim.input.moveY = 0f
      }
      if (keyboardDrivingVehicle) {
        sim.input.gasHeld = false
        sim.input.brakeHeld = false
        sim.input.steerLeft = false
        sim.input.steerRight = false
      }
      sim.input.sprintHeld = false
    }
    keyboardDrivingMovement = false
    keyboardDrivingVehicle = false
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (!hasFocus) {
      clearKeyboardMovement(activeSimulation)
    } else {
      runCatching {
        WindowCompat.getInsetsController(window, window.decorView).apply {
          systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
          hide(WindowInsetsCompat.Type.systemBars())
        }
      }
    }
  }
}
