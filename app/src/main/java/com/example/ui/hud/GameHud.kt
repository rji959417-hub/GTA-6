package com.example.ui.hud

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.game.ai.MultilingualAiAssistant
import com.example.game.gl.Vice3DViewport
import com.example.game.gl.ViceGlRenderer
import com.example.game.state.CameraDistanceMode
import com.example.game.state.DropPhase
import com.example.game.state.GameSimulation
import com.example.game.state.HudSnapshot
import com.example.game.state.LandingReactionType
import com.example.game.state.ParkourActionType
import com.example.game.state.TimeOfDayPreset
import com.example.game.state.WeaponType
import com.example.game.voice.VoiceChatManager
import com.example.game.voice.VoiceWebViewHost
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldenHour
import com.example.ui.theme.NeonPink
import com.example.ui.theme.PalmMint
import com.example.ui.theme.SunsetCoral
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.ViceSurface

@Composable
fun ViceHorizonScreen(
  sim: GameSimulation,
  renderer: ViceGlRenderer,
  hud: HudSnapshot,
  voiceManager: VoiceChatManager,
  aiAssistant: MultilingualAiAssistant,
  onStartMatchFromLobby: () -> Unit,
  onInvitePeerById: (String) -> Unit,
  onAcceptPeerInvite: () -> Unit,
  onSyncLobbyState: () -> Unit,
  onToggleMute: () -> Unit,
  onHudForceRefresh: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = LocalHapticFeedback.current
  val voiceState by voiceManager.uiState.collectAsStateWithLifecycle()
  val aiState by aiAssistant.state.collectAsStateWithLifecycle()
  var pendingAiRecordOnPermission by remember { mutableStateOf(false) }

  LaunchedEffect(voiceManager, aiAssistant, context) {
    aiAssistant.attachContext(context.applicationContext)
    val granted =
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    voiceManager.onMicPermissionChanged(granted)
    aiAssistant.onMicPermissionChanged(granted)
  }

  val micPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      voiceManager.onMicPermissionChanged(granted)
      aiAssistant.onMicPermissionChanged(granted)
      if (granted && pendingAiRecordOnPermission) {
        pendingAiRecordOnPermission = false
        aiAssistant.startVoiceRecording()
      } else {
        pendingAiRecordOnPermission = false
      }
    }

  val handleMicToggle = {
    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    voiceManager.toggleMicMute()
    val granted =
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    if (!granted) {
      micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    } else if (!voiceState.hasMicPermission) {
      voiceManager.onMicPermissionChanged(true)
      aiAssistant.onMicPermissionChanged(true)
    }
  }

  val handleConnectVoice = {
    val targetId = voiceState.remotePeerIdInput.trim()
    val granted =
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    if (targetId.isNotEmpty() && !granted) {
      voiceManager.markPendingConnectForPermission()
      micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    } else {
      if (granted && !voiceState.hasMicPermission) {
        voiceManager.onMicPermissionChanged(true)
        aiAssistant.onMicPermissionChanged(true)
      }
      voiceManager.connectVoice()
    }
  }

  val handleAiVoiceToggle = {
    if (aiState.isRecording) {
      aiAssistant.stopRecordingAndSendVoiceQuestion()
    } else {
      val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
          PackageManager.PERMISSION_GRANTED
      if (!granted) {
        pendingAiRecordOnPermission = true
        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
      } else {
        aiAssistant.onMicPermissionChanged(true)
        aiAssistant.startVoiceRecording()
      }
    }
  }

  if (hud.isPlaying || hud.isGunShopOpen || aiState.isOpen) {
    BackHandler {
      if (aiState.isOpen) {
        aiAssistant.closePanel()
      } else if (hud.isGunShopOpen) {
        sim.closeGunShop()
        onHudForceRefresh()
      } else if (hud.isPlaying) {
        sim.isPaused = !sim.isPaused
        onHudForceRefresh()
      }
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    // 0. Headless PeerJS WebRTC Voice WebView Bridge
    VoiceWebViewHost(manager = voiceManager, modifier = Modifier.align(Alignment.TopStart))

    // 1. Fullscreen OpenGL ES 3.0 3D World Viewport
    Vice3DViewport(renderer = renderer, modifier = Modifier.fillMaxSize())

    // 2. Camera Orbit / Scope Aim Touch Drag Layer (underneath HUD buttons)
    if (hud.isPlaying && !hud.isPaused && !hud.isGunShopOpen && !aiState.isOpen) {
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

    // 2B. Non-blocking Tactical Scope Reticle Overlay when scoped in
    if (hud.isPlaying && hud.isScoped && !hud.isPaused && !hud.isGunShopOpen) {
      TacticalScopeOverlay(weapon = hud.equippedWeapon)
    }

    // 2C. PUBG-style Hard Rooftop Fall Screen-Edge Impact Flash
    if (hud.isPlaying && hud.activeLandingReaction == LandingReactionType.HARD_STAGGER) {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(
              Brush.radialGradient(
                0.0f to Color.Transparent,
                0.62f to Color.Transparent,
                1.0f to SunsetCoral.copy(alpha = 0.42f),
              )
            )
            .testTag("hard_fall_vignette")
      )
    }

    // 3. Free Fire-Style 3D Squad Lobby Overlay
    AnimatedVisibility(
      visible = !hud.isPlaying && !hud.isGunShopOpen,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      ViceLobbyOverlay(
        hud = hud,
        voiceState = voiceState,
        onStartMatch = onStartMatchFromLobby,
        onRotateHero = { deltaX -> sim.input.addOrbitDelta(deltaX, 0f) },
        onOpenGunShop = {
          sim.openGunShop()
          onHudForceRefresh()
        },
        onCycleHeroPreset = {
          sim.cycleHeroPreset()
          onHudForceRefresh()
          onSyncLobbyState()
        },
        onCycleWeapon = {
          if (hud.ownedWeapons.size > 1) {
            sim.cycleWeapon()
          } else {
            sim.openGunShop()
          }
          onHudForceRefresh()
          onSyncLobbyState()
        },
        onToggleSitPose = {
          sim.toggleLobbySitPose()
          onHudForceRefresh()
        },
        onCycleTimePreset = {
          val values = TimeOfDayPreset.entries
          sim.timePreset = values[(hud.timePreset.ordinal + 1) % values.size]
          onHudForceRefresh()
        },
        onCycleDropZone = {
          sim.cycleLobbyDropZone()
          onHudForceRefresh()
        },
        onToggleAudioMute = onToggleMute,
        onToggleMicMute = handleMicToggle,
        onRemotePeerIdChange = { voiceManager.updateRemotePeerIdInput(it) },
        onInvitePeerById = onInvitePeerById,
        onConnectVoice = handleConnectVoice,
        onDisconnectVoice = { voiceManager.disconnectVoice() },
        onInviteAiBuddy = { id, name, heroIdx ->
          sim.inviteAiBuddyToLobby(id, name, heroIdx)
          onHudForceRefresh()
        },
        onRemoveSquadMember = { id, isRemoteP2P ->
          if (isRemoteP2P) {
            voiceManager.removeRemoteSquadMember(id)
          } else {
            sim.removeLobbySquadMember(id)
          }
          onHudForceRefresh()
        },
        onAcceptIncomingInvite = onAcceptPeerInvite,
        onDeclineIncomingInvite = { voiceManager.declineIncomingInvite() },
        onOpenAiAssistant = { aiAssistant.openPanel() },
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
        val radarSize = if (compactLandscape) 86.dp else 110.dp
        val joystickSize = if (compactLandscape) 104.dp else 124.dp

        // Top-Left: Rotating Minimap + Survival Status (HP / Cash / Stars / Panic) + Zone Pill
        Column(
          verticalArrangement = Arrangement.spacedBy(5.dp),
          modifier = Modifier.align(Alignment.TopStart),
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
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

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
              // Health & Cash Pill
              Column(
                modifier =
                  Modifier.clip(RoundedCornerShape(12.dp))
                    .background(ViceSurface.copy(alpha = 0.84f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    Icons.Filled.Favorite,
                    contentDescription = "Health",
                    tint = if (hud.playerHealth < 35) SunsetCoral else NeonPink,
                    modifier = Modifier.size(13.dp),
                  )
                  Spacer(modifier = Modifier.width(5.dp))
                  Box(
                    modifier =
                      Modifier.width(58.dp)
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                  ) {
                    Box(
                      modifier =
                        Modifier.fillMaxWidth((hud.playerHealth / 100f).coerceIn(0f, 1f))
                          .fillMaxHeight()
                          .clip(RoundedCornerShape(4.dp))
                          .background(if (hud.playerHealth < 35) SunsetCoral else PalmMint)
                    )
                  }
                  Spacer(modifier = Modifier.width(5.dp))
                  Text(
                    text = "${hud.playerHealth}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TextPrimary,
                  )
                }

                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Text(
                    text = "$${hud.playerCash}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = PalmMint,
                    modifier = Modifier.testTag("hud_cash_text"),
                  )
                  if (hud.npcDefeatedCount > 0) {
                    Text(
                      text = "KO ${hud.npcDefeatedCount}",
                      style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                      color = GoldenHour,
                    )
                  }
                }
              }

              if (hud.wantedLevel > 0) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier =
                    Modifier.clip(RoundedCornerShape(10.dp))
                      .background(ViceSurface.copy(alpha = 0.84f))
                      .border(1.dp, GoldenHour.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                      .padding(horizontal = 7.dp, vertical = 3.dp)
                      .testTag("wanted_stars_row"),
                ) {
                  repeat(5) { starIdx ->
                    Icon(
                      Icons.Filled.Star,
                      contentDescription = null,
                      tint =
                        if (starIdx < hud.wantedLevel) GoldenHour else Color.White.copy(alpha = 0.18f),
                      modifier = Modifier.size(13.dp),
                    )
                  }
                }
              }

              if (hud.friendCount > 0) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier =
                    Modifier.clip(RoundedCornerShape(10.dp))
                      .background(PalmMint.copy(alpha = 0.20f))
                      .border(1.dp, PalmMint.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
                      .clickable {
                        sim.dismissFriends()
                        onHudForceRefresh()
                      }
                      .padding(horizontal = 8.dp, vertical = 4.dp)
                      .testTag("friend_squad_badge"),
                ) {
                  Icon(
                    Icons.Filled.Group,
                    contentDescription = "Squad Friends",
                    tint = PalmMint,
                    modifier = Modifier.size(13.dp),
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "BUDDY ×${hud.friendCount} · TAP LEAVE",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = TextPrimary,
                  )
                }
              }

              if (hud.panickingNpcCount > 0) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier =
                    Modifier.clip(RoundedCornerShape(10.dp))
                      .background(SunsetCoral.copy(alpha = 0.22f))
                      .border(1.dp, SunsetCoral.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                      .padding(horizontal = 7.dp, vertical = 3.dp)
                      .testTag("panic_badge"),
                ) {
                  Icon(
                    Icons.AutoMirrored.Filled.DirectionsRun,
                    contentDescription = "Fleeing Crowd",
                    tint = SunsetCoral,
                    modifier = Modifier.size(12.dp),
                  )
                  Spacer(modifier = Modifier.width(3.dp))
                  Text(
                    text = "${hud.panickingNpcCount} FLEEING",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = TextPrimary,
                  )
                }
              }
            }
          }

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
                  hud.isSitting -> PalmMint
                  hud.nearbyGunShop -> GoldenHour
                  hud.isDrivingBoat || hud.isSwimming -> PalmMint
                  hud.insideHouseName != null -> GoldenHour
                  else -> ElectricCyan
                },
            )
          }
        }

        // Top-Right: Two Compact Rows (Utilities + Anytime Gun Shop / Weapon Switcher)
        Column(
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.align(Alignment.TopEnd),
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
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
              icon = Icons.Filled.AutoAwesome,
              label = "AI",
              accent = ElectricCyan,
              onClick = { aiAssistant.openPanel() },
              testTagName = "hud_ai_button",
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

          // Second Top-Right Row: Weapon, Gun Shop, Voice Panel Toggle, Free Fire Mic Toggle
          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            HudIconPill(
              icon =
                if (hud.equippedWeapon == WeaponType.FISTS) Icons.Filled.SportsMma
                else Icons.Filled.GpsFixed,
              label = hud.equippedWeapon.shortTag,
              accent = ElectricCyan,
              trailingIcon = if (hud.ownedWeapons.size > 1) Icons.Filled.SwapHoriz else null,
              onClick = {
                if (hud.ownedWeapons.size > 1) {
                  sim.cycleWeapon()
                } else {
                  sim.openGunShop()
                }
                onHudForceRefresh()
              },
              testTagName = "cycle_weapon_button",
            )
            HudIconPill(
              icon = Icons.Filled.Storefront,
              label = "GUN SHOP",
              accent = GoldenHour,
              onClick = {
                sim.openGunShop()
                onHudForceRefresh()
              },
              testTagName = "gun_shop_hud_button",
            )
            VoiceSquadTogglePill(
              uiState = voiceState,
              onClick = { voiceManager.togglePanelExpanded() },
            )
            FreeFireMicToggleButton(
              isMicMuted = voiceState.isMicMuted,
              status = voiceState.status,
              hasMicPermission = voiceState.hasMicPermission,
              onClick = handleMicToggle,
            )
          }
        }

        // Top-Center: On-Screen PeerJS Voice Squad Card & Hit Banner
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier =
            Modifier.align(Alignment.TopCenter)
              .padding(top = if (maxWidth < 900.dp) 82.dp else 4.dp),
        ) {
          if (hud.lastHitBanner != null) {
            Box(
              modifier =
                Modifier.clip(RoundedCornerShape(50))
                  .background(ViceSurface.copy(alpha = 0.88f))
                  .border(1.2.dp, GoldenHour.copy(alpha = 0.78f), RoundedCornerShape(50))
                  .padding(horizontal = 14.dp, vertical = 5.dp)
                  .testTag("combat_hit_banner")
            ) {
              Text(
                text = hud.lastHitBanner,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = GoldenHour,
              )
            }
          }

          if (!hud.isPaused && !hud.isGunShopOpen) {
            VoiceChatPanel(
              uiState = voiceState,
              onRemotePeerIdChange = { voiceManager.updateRemotePeerIdInput(it) },
              onConnectVoice = handleConnectVoice,
              onDisconnectVoice = { voiceManager.disconnectVoice() },
              onToggleMicMute = handleMicToggle,
              onCollapse = { voiceManager.setPanelExpanded(false) },
            )
          }
        }

        // Bottom-Center Context Prompt
        val promptText =
          when {
            hud.dropPhase == DropPhase.AIRPLANE ->
              "VICE AIRDROP · ${hud.planeCountdownSec}s · TAP JUMP OR DRAG CAMERA"
            hud.dropPhase == DropPhase.FREEFALL ->
              "SKYDIVING · ALT ${hud.altitudeMeters}m · STEER & OPEN PARACHUTE"
            hud.dropPhase == DropPhase.PARACHUTE ->
              "PARACHUTE GLIDE · STEER TO KEY BAYSIDE OR EMERALD UPTOWN"
            hud.isCarjacking -> "PULLING DRIVER OUT · ${hud.carjackTargetName?.uppercase() ?: ""}"
            hud.activeParkourType == ParkourActionType.CLIMB_LEDGE -> "PULLING UP OVER LEDGE..."
            hud.activeParkourType == ParkourActionType.VAULT_OVER -> "VAULTING BARRIER..."
            hud.activeAttackMove != null -> hud.activeAttackMove.name.replace('_', ' ')
            hud.nearbyParkourLabel != null -> "${hud.nearbyParkourLabel} · TAP CLIMB OR JUMP"
            hud.isSitting -> "SITTING COMFORTABLY · TAP STAND OR MOVE TO GET UP"
            hud.isDriving -> "${hud.activeVehicleName ?: "VEHICLE"} · DRAG TO LOOK"
            hud.nearbyBoatName != null ->
              if (hud.nearbyBoatOccupied) "HIJACK ${hud.nearbyBoatName.uppercase()}"
              else "NEAR ${hud.nearbyBoatName.uppercase()}"
            hud.nearbyVehicleName != null ->
              if (hud.nearbyVehicleOccupied) "CARJACK ${hud.nearbyVehicleName.uppercase()}"
              else "NEAR ${hud.nearbyVehicleName.uppercase()}"
            hud.nearbyGunShop -> "VICE ARMS COUNTER · TAP BUY GUNS"
            hud.insideHouseName != null -> "EXPLORING ${hud.insideHouseName.uppercase()}"
            hud.isSwimming -> "SWIM TOWARD BOATS OR BEACH"
            else -> null
          }
        if (promptText != null) {
          Box(
            modifier =
              Modifier.align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
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

        // Bottom Controls: Airborne Plane/Parachute Drop vs On Foot vs Driving Car / Boat
        if (!hud.isPaused && !hud.isGunShopOpen && !hud.isCarjacking) {
          when {
            hud.dropPhase != DropPhase.GROUND -> {
              if (hud.dropPhase == DropPhase.FREEFALL || hud.dropPhase == DropPhase.PARACHUTE) {
                VirtualJoystick(
                  onMove = { mx, my ->
                    sim.input.moveX = mx
                    sim.input.moveY = my
                  },
                  modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 6.dp),
                  size = joystickSize,
                )
              }
              AirborneDropActionPad(
                dropPhase = hud.dropPhase,
                planeCountdownSec = hud.planeCountdownSec,
                altitudeMeters = hud.altitudeMeters,
                onJumpFromPlaneTap = {
                  haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                  sim.jumpFromAirplane()
                  onHudForceRefresh()
                },
                onDeployChuteTap = {
                  haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                  sim.deployParachute()
                  onHudForceRefresh()
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 6.dp),
              )
            }
            hud.isDriving -> {
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
            }
            else -> {
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
              nearbyGunShop = hud.nearbyGunShop,
              nearbyFriendCandidate = hud.nearbyFriendCandidateId != null,
              nearbyParkourLabel = hud.nearbyParkourLabel,
              isSitting = hud.isSitting,
              isScoped = hud.isScoped,
              equippedWeapon = hud.equippedWeapon,
              onSprintChange = { sim.input.sprintHeld = it },
              onJumpTap = {
                sim.input.jumpQueued = true
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              },
              onClimbTap = {
                sim.input.climbQueued = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
              },
              onEnterCraftTap = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                sim.toggleEnterExitVehicleOrBoat()
                onHudForceRefresh()
              },
              onSitToggleTap = {
                sim.toggleSit()
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onHudForceRefresh()
              },
              onBefriendTap = {
                sim.befriendNearbyNpc()
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onHudForceRefresh()
              },
              onScopeToggleTap = {
                sim.toggleScope()
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onHudForceRefresh()
              },
              onPunchTap = {
                sim.triggerPunch()
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
              },
              onKickTap = {
                sim.triggerKick()
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
              },
              onFireChange = { held ->
                sim.input.fireHeld = held
                if (held) sim.input.fireQueued = true
              },
              onOpenGunShopTap = {
                sim.openGunShop()
                onHudForceRefresh()
              },
              modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 4.dp),
            )
            }
          }
        }
      }
    }

    // 5. Anytime Vice Arms Gun Shop Modal (available in both Lobby and Gameplay)
    AnimatedVisibility(
      visible = hud.isGunShopOpen,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      GunShopModal(
        hud = hud,
        onBuyOrEquip = { weapon ->
          haptics.performHapticFeedback(HapticFeedbackType.LongPress)
          sim.buyOrEquipWeapon(weapon)
          onHudForceRefresh()
          onSyncLobbyState()
        },
        onClose = {
          sim.closeGunShop()
          onHudForceRefresh()
        },
      )
    }

    // 6. Pause & Fast-Travel Map Overlay
    AnimatedVisibility(
      visible = hud.isPlaying && hud.isPaused && !hud.isGunShopOpen,
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
        onTeleportGunShop = {
          sim.teleportToGunShop()
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
        onAirplaneDrop = {
          haptics.performHapticFeedback(HapticFeedbackType.LongPress)
          sim.startAirplaneDrop()
          onHudForceRefresh()
        },
        onReturnToLobby = {
          sim.returnToLobby()
          onHudForceRefresh()
        },
      )
    }

    // 7. Multilingual AI Voice & Chat Assistant Overlay
    AnimatedVisibility(
      visible = aiState.isOpen,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      AiAssistantModal(
        state = aiState,
        onVoiceRecordToggle = handleAiVoiceToggle,
        onCancelRecording = { aiAssistant.cancelVoiceRecording() },
        onDraftChange = { aiAssistant.updateDraftText(it) },
        onSendText = { aiAssistant.sendTextQuestion() },
        onQuickPrompt = { prompt -> aiAssistant.sendTextQuestion(prompt) },
        onToggleTts = { aiAssistant.toggleTtsEnabled() },
        onClearChat = { aiAssistant.clearConversation() },
        onClose = { aiAssistant.closePanel() },
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
  trailingIcon: ImageVector? = null,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.clip(RoundedCornerShape(14.dp))
        .background(ViceSurface.copy(alpha = 0.80f))
        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 9.dp, vertical = 9.dp)
        .testTag(testTagName),
  ) {
    Icon(icon, contentDescription = label ?: testTagName, tint = accent, modifier = Modifier.size(18.dp))
    if (label != null) {
      Spacer(modifier = Modifier.width(5.dp))
      Text(label, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
    }
    if (trailingIcon != null) {
      Spacer(modifier = Modifier.width(3.dp))
      Icon(trailingIcon, contentDescription = null, tint = accent.copy(alpha = 0.85f), modifier = Modifier.size(14.dp))
    }
  }
}

@Composable
private fun TacticalScopeOverlay(weapon: WeaponType) {
  Box(modifier = Modifier.fillMaxSize().testTag("scope_reticle_overlay")) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val cx = size.width * 0.5f
      val cy = size.height * 0.5f
      val ringRadius = (size.minDimension * 0.36f).coerceAtLeast(80f)
      val isSniper = weapon == WeaponType.SNIPER_RIFLE

      // Subtle optical lens vignette outside central scope ring
      drawRect(
        brush =
          Brush.radialGradient(
            0.0f to Color.Transparent,
            0.52f to Color.Transparent,
            0.68f to Color(0xAA04060C),
            1.0f to Color(0xCC020308),
            center = Offset(cx, cy),
            radius = ringRadius * 1.45f,
          )
      )

      // Outer tactical reticle ring
      drawCircle(
        color = ElectricCyan.copy(alpha = 0.72f),
        radius = ringRadius,
        center = Offset(cx, cy),
        style = Stroke(width = 2.2.dp.toPx()),
      )

      // Crisp horizontal & vertical crosshair hairs with central clear gap
      val gap = 14.dp.toPx()
      val hairColor = Color.White.copy(alpha = 0.82f)
      val hairStroke = if (isSniper) 1.4.dp.toPx() else 1.8.dp.toPx()
      drawLine(hairColor, Offset(cx - ringRadius, cy), Offset(cx - gap, cy), strokeWidth = hairStroke)
      drawLine(hairColor, Offset(cx + gap, cy), Offset(cx + ringRadius, cy), strokeWidth = hairStroke)
      drawLine(hairColor, Offset(cx, cy - ringRadius), Offset(cx, cy - gap), strokeWidth = hairStroke)
      drawLine(hairColor, Offset(cx, cy + gap), Offset(cx, cy + ringRadius), strokeWidth = hairStroke)

      // Stadia range ticks for sniper long-range aiming
      if (isSniper) {
        val step = ringRadius * 0.24f
        for (i in 1..3) {
          val offset = step * i
          val tick = if (i == 1) 7.dp.toPx() else 5.dp.toPx()
          drawLine(hairColor, Offset(cx - tick, cy + offset), Offset(cx + tick, cy + offset), strokeWidth = 1.2.dp.toPx())
          drawLine(hairColor, Offset(cx + offset, cy - tick), Offset(cx + offset, cy + tick), strokeWidth = 1.2.dp.toPx())
          drawLine(hairColor, Offset(cx - offset, cy - tick), Offset(cx - offset, cy + tick), strokeWidth = 1.2.dp.toPx())
        }
      }

      // Precision red/gold center aim dot
      drawCircle(color = SunsetCoral, radius = 4.2.dp.toPx(), center = Offset(cx, cy))
      drawCircle(color = GoldenHour, radius = 2.0.dp.toPx(), center = Offset(cx, cy))
    }

    Text(
      text =
        if (weapon == WeaponType.SNIPER_RIFLE) "5X LONGSHOT OPTIC · DRAG TO AIM"
        else "ADS SCOPE · DRAG TO AIM",
      style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
      color = ElectricCyan,
      modifier =
        Modifier.align(Alignment.Center)
          .padding(top = 128.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(ViceSurface.copy(alpha = 0.72f))
          .padding(horizontal = 8.dp, vertical = 3.dp),
    )
  }
}
