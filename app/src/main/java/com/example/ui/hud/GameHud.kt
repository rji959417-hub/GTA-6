package com.example.ui.hud

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.game.gl.Vice3DViewport
import com.example.game.gl.ViceGlRenderer
import com.example.game.state.CameraDistanceMode
import com.example.game.state.GameSimulation
import com.example.game.state.HudSnapshot
import com.example.game.state.TimeOfDayPreset
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldenHour
import com.example.ui.theme.NeonPink
import com.example.ui.theme.PalmMint
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.ViceSurface

@Composable
fun ViceHorizonScreen(
  sim: GameSimulation,
  renderer: ViceGlRenderer,
  hud: HudSnapshot,
  onToggleMute: () -> Unit,
  onHudForceRefresh: () -> Unit,
) {
  val haptics = LocalHapticFeedback.current

  if (hud.isPlaying) {
    BackHandler {
      sim.isPaused = !sim.isPaused
      onHudForceRefresh()
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    // 1. Fullscreen OpenGL ES 3.0 3D World Viewport
    Vice3DViewport(renderer = renderer, modifier = Modifier.fillMaxSize())

    // 2. Camera Orbit Touch Drag Layer (underneath HUD buttons)
    if (hud.isPlaying && !hud.isPaused) {
      Box(
        modifier =
          Modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
              change.consume()
              sim.input.addOrbitDelta(dragAmount.x, dragAmount.y)
            }
          }
      )
    }

    // 3. Title Launch Overlay
    AnimatedVisibility(
      visible = !hud.isPlaying,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      TitleScreenOverlay(
        onPlayClick = {
          haptics.performHapticFeedback(HapticFeedbackType.LongPress)
          sim.isPlaying = true
          sim.isPaused = false
          onHudForceRefresh()
        }
      )
    }

    // 4. Active In-Game HUD
    if (hud.isPlaying) {
      BoxWithConstraints(
        modifier =
          Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 14.dp, vertical = 10.dp)
      ) {
        val compactLandscape = maxHeight < 400.dp
        val radarSize = if (compactLandscape) 94.dp else 116.dp
        val joystickSize = if (compactLandscape) 108.dp else 128.dp

        // Top-Left: Rotating Minimap + Zone Pill
        Column(
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.align(Alignment.TopStart),
        ) {
          MinimapRadar(
            hud = hud,
            houses = sim.world.houses,
            onTapMap = {
              sim.isPaused = true
              onHudForceRefresh()
            },
            radarSize = radarSize,
          )
          Box(
            modifier =
              Modifier.clip(RoundedCornerShape(10.dp))
                .background(ViceSurface.copy(alpha = 0.80f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
          ) {
            Text(
              text = hud.zoneName,
              style = MaterialTheme.typography.labelSmall,
              color =
                when {
                  hud.isDrivingBoat || hud.isSwimming -> PalmMint
                  hud.insideHouseName != null -> GoldenHour
                  else -> ElectricCyan
                },
            )
          }
        }

        // Top-Right: Time-of-Day, Camera, Audio, Map/Menu Pills
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.align(Alignment.TopEnd),
        ) {
          HudIconPill(
            icon = Icons.Filled.WbTwilight,
            label =
              when (hud.timePreset) {
                TimeOfDayPreset.SUNSET -> "SUNSET"
                TimeOfDayPreset.DAYLIGHT -> "DAY"
                TimeOfDayPreset.NEON_NIGHT -> "NIGHT"
              },
            accent = GoldenHour,
            onClick = {
              val values = TimeOfDayPreset.entries
              sim.timePreset = values[(hud.timePreset.ordinal + 1) % values.size]
              onHudForceRefresh()
            },
            testTagName = "cycle_time_button",
          )
          HudIconPill(
            icon = Icons.Filled.Videocam,
            label = null,
            accent = ElectricCyan,
            onClick = {
              val modes = CameraDistanceMode.entries
              sim.cameraMode = modes[(hud.cameraMode.ordinal + 1) % modes.size]
              onHudForceRefresh()
            },
            testTagName = "cycle_cam_button",
          )
          HudIconPill(
            icon =
              if (hud.isMuted) Icons.AutoMirrored.Filled.VolumeOff
              else Icons.AutoMirrored.Filled.VolumeUp,
            label = null,
            accent = Color.White,
            onClick = onToggleMute,
            testTagName = "mute_button",
          )
          HudIconPill(
            icon = Icons.Filled.Menu,
            label = "MAP",
            accent = NeonPink,
            onClick = {
              sim.isPaused = true
              onHudForceRefresh()
            },
            testTagName = "pause_menu_button",
          )
        }

        // Bottom-Center Context Prompt
        val promptText =
          when {
            hud.isCarjacking -> "PULLING DRIVER OUT · ${hud.carjackTargetName?.uppercase() ?: ""}"
            hud.isDriving -> "${hud.activeVehicleName ?: "VEHICLE"} · DRAG TO LOOK"
            hud.nearbyBoatName != null ->
              if (hud.nearbyBoatOccupied) "HIJACK ${hud.nearbyBoatName.uppercase()}"
              else "NEAR ${hud.nearbyBoatName.uppercase()}"
            hud.nearbyVehicleName != null ->
              if (hud.nearbyVehicleOccupied) "CARJACK ${hud.nearbyVehicleName.uppercase()}"
              else "NEAR ${hud.nearbyVehicleName.uppercase()}"
            hud.insideHouseName != null -> "EXPLORING ${hud.insideHouseName.uppercase()}"
            hud.isSwimming -> "SWIM TOWARD BOATS OR BEACH"
            else -> null
          }
        if (promptText != null) {
          Box(
            modifier =
              Modifier.align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
                .clip(RoundedCornerShape(50))
                .background(ViceSurface.copy(alpha = 0.84f))
                .border(1.dp, GoldenHour.copy(alpha = 0.45f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp)
          ) {
            Text(
              text = promptText,
              style = MaterialTheme.typography.labelSmall,
              color = TextPrimary,
            )
          }
        }

        // Bottom Controls: On Foot vs Driving Car / Boat
        if (!hud.isPaused && !hud.isCarjacking) {
          if (hud.isDriving) {
            DrivingControlsLeft(
              onSteerLeft = { sim.input.steerLeft = it },
              onSteerRight = { sim.input.steerRight = it },
              modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 6.dp),
            )
            DrivingControlsRight(
              speedKmh = hud.speedKmh,
              isDrifting = hud.isDrifting,
              isBoat = hud.isDrivingBoat,
              onGasChange = { sim.input.gasHeld = it },
              onBrakeChange = { sim.input.brakeHeld = it },
              onDriftChange = { sim.input.handbrakeHeld = it },
              onExitCarTap = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                sim.toggleEnterExitVehicleOrBoat()
                onHudForceRefresh()
              },
              modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 6.dp),
            )
          } else {
            VirtualJoystick(
              onMove = { mx, my ->
                sim.input.moveX = mx
                sim.input.moveY = my
              },
              modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 6.dp),
              size = joystickSize,
            )
            OnFootActionPad(
              nearbyVehicleName = hud.nearbyVehicleName,
              nearbyVehicleOccupied = hud.nearbyVehicleOccupied,
              nearbyBoatName = hud.nearbyBoatName,
              nearbyBoatOccupied = hud.nearbyBoatOccupied,
              onSprintChange = { sim.input.sprintHeld = it },
              onJumpTap = {
                sim.input.jumpQueued = true
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              },
              onEnterCraftTap = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                sim.toggleEnterExitVehicleOrBoat()
                onHudForceRefresh()
              },
              modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 6.dp),
            )
          }
        }
      }
    }

    // 5. Pause & Fast-Travel Map Overlay
    AnimatedVisibility(
      visible = hud.isPlaying && hud.isPaused,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      PauseMapModal(
        hud = hud,
        houses = sim.world.houses,
        onResume = {
          sim.isPaused = false
          onHudForceRefresh()
        },
        onTeleportHouse = { idx ->
          sim.teleportToHouse(idx)
          sim.isPaused = false
          onHudForceRefresh()
        },
        onTeleportPier = {
          sim.teleportToBoatPier()
          sim.isPaused = false
          onHudForceRefresh()
        },
        onSummonCar = {
          sim.summonNearestCar()
          sim.isPaused = false
          onHudForceRefresh()
        },
        onResetWorld = {
          sim.resetToSpawn()
          sim.isPaused = false
          onHudForceRefresh()
        },
        onSelectTime = { preset ->
          sim.timePreset = preset
          onHudForceRefresh()
        },
        onCycleCamera = {
          val modes = CameraDistanceMode.entries
          sim.cameraMode = modes[(hud.cameraMode.ordinal + 1) % modes.size]
          onHudForceRefresh()
        },
      )
    }
  }
}

@Composable
private fun HudIconPill(
  icon: ImageVector,
  label: String?,
  accent: Color,
  onClick: () -> Unit,
  testTagName: String,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.clip(RoundedCornerShape(14.dp))
        .background(ViceSurface.copy(alpha = 0.80f))
        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 10.dp)
        .testTag(testTagName),
  ) {
    Icon(icon, contentDescription = label ?: testTagName, tint = accent, modifier = Modifier.size(20.dp))
    if (label != null) {
      Spacer(modifier = Modifier.width(5.dp))
      Text(label, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
    }
  }
}
