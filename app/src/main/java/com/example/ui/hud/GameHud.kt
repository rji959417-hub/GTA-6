package com.example.ui.hud

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ViceSurfaceSolid
import com.example.ui.theme.ViceVoid
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.game.ai.MultilingualAiAssistant
import com.example.game.gl.Vice3DViewport
import com.example.game.gl.ViceGlRenderer
import com.example.game.state.CameraDistanceMode
import com.example.game.state.DropPhase
import com.example.game.state.GameSimulation
import com.example.game.state.HudElementId
import com.example.game.state.HudLayoutConfig
import com.example.game.state.HudLayoutStore
import com.example.game.state.HudSnapshot
import com.example.game.state.LandingReactionType
import com.example.game.state.ParkourActionType
import com.example.game.state.TimeOfDayPreset
import com.example.game.state.WeaponType
import com.example.game.state.WeatherMode
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
  permanentPlayerCode: String = "",
  accountDisplayName: String = "",
  onStartMatchFromLobby: () -> Unit,
  onInvitePeerById: (String) -> Unit,
  onAcceptPeerInvite: () -> Unit,
  onDeclinePeerInvite: () -> Unit = { voiceManager.declineIncomingInvite() },
  onRemoveRemoteSquadMember: (String) -> Unit = { voiceManager.removeRemoteSquadMember(it) },
  onSyncLobbyState: () -> Unit,
  onToggleMute: () -> Unit,
  onHudForceRefresh: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = LocalHapticFeedback.current
  val hudPrefs = remember(context) {
    context.applicationContext.getSharedPreferences("vice_local_profile", Context.MODE_PRIVATE)
  }
  var hudConfig by remember(hudPrefs) { mutableStateOf(HudLayoutStore.load(hudPrefs)) }
  var isHudCustomizeOpen by remember { mutableStateOf(false) }
  val voiceState by voiceManager.uiState.collectAsStateWithLifecycle()
  val aiState by aiAssistant.state.collectAsStateWithLifecycle()
  var pendingAiRecordOnPermission by remember { mutableStateOf(false) }

  LaunchedEffect(voiceManager, aiAssistant, context) {
    val appCtx = context.applicationContext
    voiceManager.attachContext(appCtx)
    aiAssistant.attachContext(appCtx)
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

  val handleMicToggle: () -> Unit = {
    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    val granted =
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    if (!granted) {
      voiceManager.setMicMuted(false)
      micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    } else {
      if (!voiceState.hasMicPermission) {
        voiceManager.onMicPermissionChanged(true)
        aiAssistant.onMicPermissionChanged(true)
      }
      voiceManager.toggleMicMute()
    }
  }

  val handleSpeakerToggle: () -> Unit = {
    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    voiceManager.toggleSpeakerMute()
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

  if (hud.isPlaying || hud.isGunShopOpen || hud.isStyleShopOpen || hud.isBagOpen || aiState.isOpen || isHudCustomizeOpen) {
    BackHandler {
      if (isHudCustomizeOpen) {
        isHudCustomizeOpen = false
      } else if (aiState.isOpen) {
        aiAssistant.closePanel()
      } else if (hud.isStyleShopOpen) {
        sim.closeStyleShop()
        onHudForceRefresh()
      } else if (hud.isBagOpen) {
        sim.closeBag()
        onHudForceRefresh()
      } else if (hud.isGunShopOpen) {
        sim.closeGunShop()
        onHudForceRefresh()
      } else if (hud.isPlayerEliminated) {
        sim.returnToLobby()
        onHudForceRefresh()
        onSyncLobbyState()
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
    // Also allows passive camera orbit while spectating killer, with zero control over killer!
    val canOrbitCamera =
      hud.isPlaying &&
        !hud.isPaused &&
        !hud.isGunShopOpen &&
        !hud.isStyleShopOpen &&
        !hud.isBagOpen &&
        !aiState.isOpen &&
        !isHudCustomizeOpen &&
        (!hud.isPlayerEliminated || hud.isSpectatingKiller)
    if (canOrbitCamera) {
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
    if (hud.isPlaying && !hud.isPlayerEliminated && hud.isScoped && !hud.isPaused && !hud.isGunShopOpen) {
      TacticalScopeOverlay(weapon = hud.equippedWeapon)
    }

    // 2C. PUBG-style Hard Rooftop Fall Screen-Edge Impact Flash
    if (hud.isPlaying && !hud.isPlayerEliminated && hud.activeLandingReaction == LandingReactionType.HARD_STAGGER) {
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
      visible = !hud.isPlaying && !hud.isGunShopOpen && !hud.isStyleShopOpen,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      ViceLobbyOverlay(
        hud = hud,
        voiceState = voiceState,
        permanentPlayerCode = permanentPlayerCode,
        accountDisplayName = accountDisplayName,
        onStartMatch = onStartMatchFromLobby,
        onRotateHero = { deltaX -> sim.input.addOrbitDelta(deltaX, 0f) },
        onOpenGunShop = {
          sim.openGunShop()
          onHudForceRefresh()
        },
        onOpenStyleShop = {
          sim.openStyleShop()
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
          onSyncLobbyState()
        },
        onToggleAudioMute = onToggleMute,
        onToggleMicMute = handleMicToggle,
        onToggleSpeakerMute = handleSpeakerToggle,
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
            onRemoveRemoteSquadMember(id)
          } else {
            sim.removeLobbySquadMember(id)
          }
          onHudForceRefresh()
        },
        onAcceptIncomingInvite = onAcceptPeerInvite,
        onDeclineIncomingInvite = onDeclinePeerInvite,
        onOpenHudSettings = { isHudCustomizeOpen = true },
        hudScaleMultiplier = hudConfig.effectiveScale(HudElementId.LOBBY_UI),
      )
    }

    // 4. Active In-Game HUD vs Eliminated / Read-Only Killer Spectate HUD
    if (hud.isPlaying && hud.isPlayerEliminated) {
      if (!hud.isSpectatingKiller) {
        EliminatedOverlay(
          hud = hud,
          onShowKiller = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            sim.startSpectatingKiller()
            onHudForceRefresh()
          },
          onBackToLobby = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            sim.returnToLobby()
            onHudForceRefresh()
            onSyncLobbyState()
          },
        )
      } else {
        SpectatorHudOverlay(
          hud = hud,
          onBackToLobby = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            sim.returnToLobby()
            onHudForceRefresh()
            onSyncLobbyState()
          },
        )
      }
    } else if (hud.isPlaying) {
      BoxWithConstraints(
        modifier =
          Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 7.dp, vertical = 5.dp)
      ) {
        val compactLandscape = maxHeight <= 500.dp || maxWidth <= 950.dp
        val density = LocalDensity.current
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val radarSize = ((if (compactLandscape) 72f else 102f) * hudConfig.effectiveScale(HudElementId.MINIMAP)).dp
        val joystickSize = ((if (compactLandscape) 86f else 116f) * hudConfig.effectiveScale(HudElementId.JOYSTICK)).dp
        val controlScale = if (compactLandscape) 0.74f else 1f
        val statusScale = hudConfig.effectiveScale(HudElementId.STATUS_BAR)
        val voiceScale = hudConfig.effectiveScale(HudElementId.VOICE_HUD)
        val topUtilScale = hudConfig.effectiveScale(HudElementId.TOP_UTILITIES)
        val actionPillScale = hudConfig.effectiveScale(HudElementId.ACTION_PILLS)

        // Top-Left: Rotating Minimap + Survival Status (HP / Cash / Stars / Panic) + Zone Pill
        Column(
          verticalArrangement = Arrangement.spacedBy(if (compactLandscape) 3.dp else 5.dp),
          modifier = Modifier.align(Alignment.TopStart),
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(if (compactLandscape) 5.dp else 8.dp),
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
              modifier = Modifier.hudElementOffset(HudElementId.MINIMAP, hudConfig, containerWidthPx, containerHeightPx),
            )

            Column(
              verticalArrangement = Arrangement.spacedBy(((if (compactLandscape) 3f else 5f) * statusScale).dp),
              modifier = Modifier.hudElementOffset(HudElementId.STATUS_BAR, hudConfig, containerWidthPx, containerHeightPx),
            ) {
              // Health & Cash Pill
              Column(
                modifier =
                  Modifier.clip(RoundedCornerShape(((if (compactLandscape) 8f else 12f) * statusScale).dp))
                    .background(ViceSurface.copy(alpha = 0.84f))
                    .border(0.9.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(((if (compactLandscape) 8f else 12f) * statusScale).dp))
                    .padding(
                      horizontal = ((if (compactLandscape) 6f else 9f) * statusScale).dp,
                      vertical = ((if (compactLandscape) 4f else 6f) * statusScale).dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(((if (compactLandscape) 2.5f else 4f) * statusScale).dp),
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    Icons.Filled.Favorite,
                    contentDescription = "Health",
                    tint = if (hud.playerHealth < 35) SunsetCoral else NeonPink,
                    modifier = Modifier.size(((if (compactLandscape) 10f else 13f) * statusScale).dp),
                  )
                  Spacer(modifier = Modifier.width((4f * statusScale).dp))
                  Box(
                    modifier =
                      Modifier.width(((if (compactLandscape) 44f else 58f) * statusScale).dp)
                        .height(((if (compactLandscape) 5f else 7f) * statusScale).dp)
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
                  Spacer(modifier = Modifier.width((4f * statusScale).dp))
                  Text(
                    text = "${hud.playerHealth}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = ((if (compactLandscape) 8.5f else 10f) * statusScale).coerceIn(6f, 16f).sp),
                    color = TextPrimary,
                  )
                }

                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy((6f * statusScale).dp),
                ) {
                  Text(
                    text = "$${hud.playerCash}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = ((if (compactLandscape) 9f else 10f) * statusScale).coerceIn(6.5f, 16f).sp, fontWeight = FontWeight.Bold),
                    color = PalmMint,
                    modifier = Modifier.testTag("hud_cash_text"),
                  )
                  if (hud.npcDefeatedCount > 0) {
                    Text(
                      text = "KO ${hud.npcDefeatedCount}",
                      style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compactLandscape) 8.5.sp else 10.sp),
                      color = GoldenHour,
                    )
                  }
                }

                if (hud.vestLevel >= 3 || hud.helmetLevel >= 3) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    if (hud.vestLevel >= 3) {
                      val vColor = if (hud.vestLevel >= 4) GoldenHour else ElectricCyan
                      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("hud_vest_badge")) {
                        Icon(Icons.Filled.Shield, contentDescription = "Vest Level ${hud.vestLevel}", tint = vColor, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                          text = "V${hud.vestLevel}·${hud.vestDurability}",
                          style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                          color = vColor,
                        )
                      }
                    }
                    if (hud.helmetLevel >= 3) {
                      val hColor = if (hud.helmetLevel >= 4) GoldenHour else ElectricCyan
                      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("hud_helmet_badge")) {
                        Icon(Icons.Filled.Security, contentDescription = "Helmet Level ${hud.helmetLevel}", tint = hColor, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                          text = "H${hud.helmetLevel}·${hud.helmetDurability}",
                          style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                          color = hColor,
                        )
                      }
                    }
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
              Modifier
                .hudElementOffset(HudElementId.STATUS_BAR, hudConfig, containerWidthPx, containerHeightPx)
                .clip(RoundedCornerShape((10f * statusScale).dp))
                .background(ViceSurface.copy(alpha = 0.80f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape((10f * statusScale).dp))
                .padding(horizontal = (10f * statusScale).dp, vertical = (4f * statusScale).dp)
          ) {
            Text(
              text = hud.zoneName,
              style = MaterialTheme.typography.labelSmall.copy(fontSize = (10f * statusScale).coerceIn(7f, 16f).sp),
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

          // Top-Left Free Fire-Style Floating Mic, Speaker & Speaking Indicator Ring HUD
          FreeFireInGameVoiceHud(
            localPlayerName = accountDisplayName.ifBlank { hud.heroPresetName },
            uiState = voiceState,
            squadMembers = hud.lobbySquadMembers,
            onToggleMic = handleMicToggle,
            onToggleSpeaker = handleSpeakerToggle,
            modifier = Modifier.hudElementOffset(HudElementId.VOICE_HUD, hudConfig, containerWidthPx, containerHeightPx),
            compact = compactLandscape,
            scaleFactor = voiceScale,
          )
        }

        // Top-Right: Two Compact Rows (Utilities + Anytime Gun Shop / Weapon Switcher)
        Column(
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(if (compactLandscape) 4.dp else 6.dp),
          modifier = Modifier.align(Alignment.TopEnd),
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(((if (compactLandscape) 4f else 6f) * topUtilScale).dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.hudElementOffset(HudElementId.TOP_UTILITIES, hudConfig, containerWidthPx, containerHeightPx),
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
              compact = compactLandscape,
              scaleFactor = topUtilScale,
              onClick = {
                val values = TimeOfDayPreset.entries
                sim.timePreset = values[(hud.timePreset.ordinal + 1) % values.size]
                onHudForceRefresh()
              },
              testTagName = "cycle_time_button",
            )
            HudIconPill(
              icon =
                when (hud.weatherMode) {
                  WeatherMode.THUNDERSTORM -> Icons.Filled.Thunderstorm
                  WeatherMode.LIGHT_RAIN, WeatherMode.MISTY_PETALS -> Icons.Filled.WaterDrop
                  WeatherMode.CLEAR_BREEZE -> Icons.Filled.WbTwilight
                },
              label = hud.weatherMode.shortTag,
              accent = ElectricCyan,
              compact = compactLandscape,
              scaleFactor = topUtilScale,
              onClick = {
                sim.cycleWeatherMode()
                onHudForceRefresh()
              },
              testTagName = "weather_toggle_button",
            )
            HudIconPill(
              icon = Icons.Filled.Videocam,
              label = null,
              accent = ElectricCyan,
              compact = compactLandscape,
              scaleFactor = topUtilScale,
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
              compact = compactLandscape,
              scaleFactor = topUtilScale,
              onClick = onToggleMute,
              testTagName = "mute_button",
            )
            HudIconPill(
              iconPainter = painterResource(R.drawable.ic_hud_settings),
              label = null,
              accent = GoldenHour,
              compact = compactLandscape,
              scaleFactor = topUtilScale,
              onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                isHudCustomizeOpen = true
              },
              testTagName = "hud_settings_button",
            )
            HudIconPill(
              icon = Icons.Filled.Menu,
              label = "MAP",
              accent = NeonPink,
              compact = compactLandscape,
              scaleFactor = topUtilScale,
              onClick = {
                sim.isPaused = true
                onHudForceRefresh()
              },
              testTagName = "pause_menu_button",
            )
          }

          // Second Top-Right Row: Weapon, Bag, Quick Medkit, Gun Shop, Voice Panel Toggle, Free Fire Mic Toggle
          Row(
            horizontalArrangement = Arrangement.spacedBy(((if (compactLandscape) 4f else 6f) * actionPillScale).dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.hudElementOffset(HudElementId.ACTION_PILLS, hudConfig, containerWidthPx, containerHeightPx),
          ) {
            HudIconPill(
              icon =
                if (hud.equippedWeapon == WeaponType.FISTS) Icons.Filled.SportsMma
                else Icons.Filled.GpsFixed,
              label = hud.equippedWeapon.shortTag,
              accent = ElectricCyan,
              trailingIcon = if (hud.ownedWeapons.size > 1) Icons.Filled.SwapHoriz else null,
              compact = compactLandscape,
              scaleFactor = actionPillScale,
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
              icon = Icons.Filled.Backpack,
              label = "BAG ${hud.bagItems.size}/${hud.bagCapacity}",
              accent = if (hud.isBagFull) SunsetCoral else ElectricCyan,
              compact = compactLandscape,
              scaleFactor = actionPillScale,
              onClick = {
                sim.toggleBag()
                onHudForceRefresh()
              },
              testTagName = "bag_button",
            )
            if (hud.medkitCount > 0) {
              HudIconPill(
                icon = Icons.Filled.MedicalServices,
                label = "MED×${hud.medkitCount}",
                accent = PalmMint,
                compact = compactLandscape,
                scaleFactor = actionPillScale,
                onClick = {
                  haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                  sim.useQuickMedkit()
                  onHudForceRefresh()
                },
                testTagName = "quick_medkit_button",
              )
            }
            HudIconPill(
              icon = Icons.Filled.Storefront,
              label = "SHOP",
              accent = GoldenHour,
              compact = compactLandscape,
              scaleFactor = actionPillScale,
              onClick = {
                sim.openGunShop()
                onHudForceRefresh()
              },
              testTagName = "gun_shop_hud_button",
            )
            VoiceSquadTogglePill(
              uiState = voiceState,
              compact = compactLandscape,
              scaleFactor = actionPillScale,
              onClick = { voiceManager.togglePanelExpanded() },
            )
            FreeFireMicToggleButton(
              isMicMuted = voiceState.isMicMuted,
              status = voiceState.status,
              hasMicPermission = voiceState.hasMicPermission,
              isSpeaking = voiceState.isLocalSpeaking,
              voiceLevel = voiceState.localVoiceLevel,
              compact = compactLandscape,
              compactHeightDp = 28f * actionPillScale,
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

          val incomingInGame = voiceState.incomingInvite
          if (incomingInGame != null) {
            SquadInvitePopup(
              invite = incomingInGame,
              onAccept = onAcceptPeerInvite,
              onDecline = onDeclinePeerInvite,
            )
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
            hud.nearbyMarketStallLabel != null -> "${hud.nearbyMarketStallLabel.uppercase()} · TAP BUY $25 (+35 HP)"
            hud.nearbyLootLabel != null -> hud.nearbyLootLabel
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
        if (!hud.isPaused && !hud.isGunShopOpen && !hud.isBagOpen && !hud.isCarjacking && !isHudCustomizeOpen) {
          when {
            hud.dropPhase != DropPhase.GROUND -> {
              if (hud.dropPhase == DropPhase.FREEFALL || hud.dropPhase == DropPhase.PARACHUTE) {
                VirtualJoystick(
                  onMove = { mx, my ->
                    sim.input.moveX = mx
                    sim.input.moveY = my
                  },
                  modifier =
                    Modifier.align(Alignment.BottomStart)
                      .padding(bottom = 6.dp)
                      .hudElementOffset(HudElementId.JOYSTICK, hudConfig, containerWidthPx, containerHeightPx),
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
                compactScale = controlScale,
                hudConfig = hudConfig,
                containerWidthPx = containerWidthPx,
                containerHeightPx = containerHeightPx,
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 4.dp),
              )
            }
            hud.isDriving -> {
              DrivingControlsLeft(
                onSteerLeft = { sim.input.steerLeft = it },
                onSteerRight = { sim.input.steerRight = it },
                compactScale = controlScale,
                hudConfig = hudConfig,
                containerWidthPx = containerWidthPx,
                containerHeightPx = containerHeightPx,
                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 4.dp),
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
                compactScale = controlScale,
                hudConfig = hudConfig,
                containerWidthPx = containerWidthPx,
                containerHeightPx = containerHeightPx,
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 4.dp),
              )
            }
            else -> {
            VirtualJoystick(
              onMove = { mx, my ->
                sim.input.moveX = mx
                sim.input.moveY = my
              },
              modifier =
                Modifier.align(Alignment.BottomStart)
                  .padding(bottom = 4.dp)
                  .hudElementOffset(HudElementId.JOYSTICK, hudConfig, containerWidthPx, containerHeightPx),
              size = joystickSize,
            )
            OnFootActionPad(
              nearbyVehicleName = hud.nearbyVehicleName,
              nearbyVehicleOccupied = hud.nearbyVehicleOccupied,
              nearbyBoatName = hud.nearbyBoatName,
              nearbyBoatOccupied = hud.nearbyBoatOccupied,
              nearbyGunShop = hud.nearbyGunShop,
              nearbyMarketStallLabel = hud.nearbyMarketStallLabel,
              nearbyFriendCandidate = hud.nearbyFriendCandidateId != null,
              nearbyParkourLabel = hud.nearbyParkourLabel,
              isSitting = hud.isSitting,
              isScoped = hud.isScoped,
              equippedWeapon = hud.equippedWeapon,
              glooWallCount = hud.glooWallCount,
              isAutoSprinting = hud.isAutoSprinting,
              onSprintToggleTap = {
                sim.toggleAutoSprint()
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onHudForceRefresh()
              },
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
              onBuyMarketSnackTap = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                sim.buyMarketSnack()
                onHudForceRefresh()
              },
              onDeployGlooWallTap = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                sim.deployPlayerGlooWall()
                onHudForceRefresh()
              },
              compactScale = controlScale,
              hudConfig = hudConfig,
              containerWidthPx = containerWidthPx,
              containerHeightPx = containerHeightPx,
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

    // 5A. Lobby Side Style & Skins Store (Clothes, Hair Style, Pants, Gun Paint, Car Skins)
    AnimatedVisibility(
      visible = !hud.isPlaying && hud.isStyleShopOpen,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      StyleShopModal(
        hud = hud,
        onSelectCloth = { cloth ->
          haptics.performHapticFeedback(HapticFeedbackType.LongPress)
          sim.buyOrEquipCloth(cloth)
          onHudForceRefresh()
          onSyncLobbyState()
        },
        onSelectHair = { hair ->
          haptics.performHapticFeedback(HapticFeedbackType.LongPress)
          sim.buyOrEquipHair(hair)
          onHudForceRefresh()
          onSyncLobbyState()
        },
        onSelectPants = { pants ->
          haptics.performHapticFeedback(HapticFeedbackType.LongPress)
          sim.buyOrEquipPants(pants)
          onHudForceRefresh()
          onSyncLobbyState()
        },
        onSelectGunSkin = { skin ->
          haptics.performHapticFeedback(HapticFeedbackType.LongPress)
          sim.buyOrEquipGunSkin(skin)
          onHudForceRefresh()
          onSyncLobbyState()
        },
        onSelectCarSkin = { skin ->
          haptics.performHapticFeedback(HapticFeedbackType.LongPress)
          sim.buyOrEquipCarSkin(skin)
          onHudForceRefresh()
          onSyncLobbyState()
        },
        onClose = {
          sim.closeStyleShop()
          onHudForceRefresh()
        },
      )
    }

    // 5B. Tactical Backpack Modal (Auto-Pickup Loot, Medkits, Guns, Lv.3/4 Vests & Helmets, Remove when full)
    AnimatedVisibility(
      visible = hud.isPlaying && !hud.isPlayerEliminated && hud.isBagOpen,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      BackpackModal(
        hud = hud,
        onUseOrEquip = { uid ->
          haptics.performHapticFeedback(HapticFeedbackType.LongPress)
          sim.useOrEquipBagItem(uid)
          onHudForceRefresh()
          onSyncLobbyState()
        },
        onRemove = { uid ->
          haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          sim.removeBagItem(uid, dropToWorld = true)
          onHudForceRefresh()
          onSyncLobbyState()
        },
        onClose = {
          sim.closeBag()
          onHudForceRefresh()
        },
      )
    }

    // 6. Pause & Fast-Travel Map Overlay
    AnimatedVisibility(
      visible = hud.isPlaying && !hud.isPlayerEliminated && hud.isPaused && !hud.isGunShopOpen && !hud.isBagOpen,
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
        onSelectWeather = { mode ->
          sim.setWeatherMode(mode)
          onHudForceRefresh()
        },
        onToggleAutoWeather = {
          sim.toggleAutoWeather()
          onHudForceRefresh()
        },
        onTeleportMarket = {
          sim.teleportToMarket()
          sim.isPaused = false
          onHudForceRefresh()
        },
        onTeleportWaterfall = {
          sim.teleportToWaterfall()
          sim.isPaused = false
          onHudForceRefresh()
        },
        onTeleportAtoll = {
          sim.teleportToAtoll()
          sim.isPaused = false
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
          onSyncLobbyState()
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

    // 8. Free Fire-Style Custom HUD Icon Size Settings Overlay
    AnimatedVisibility(
      visible = isHudCustomizeOpen,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      HudCustomizeOverlay(
        initialConfig = hudConfig,
        onSave = { updated ->
          hudConfig = updated
          HudLayoutStore.save(hudPrefs, updated)
          isHudCustomizeOpen = false
        },
        onCancel = { isHudCustomizeOpen = false },
      )
    }
  }
}

@Composable
private fun HudIconPill(
  icon: ImageVector? = null,
  iconPainter: Painter? = null,
  label: String?,
  accent: Color,
  onClick: () -> Unit,
  testTagName: String,
  trailingIcon: ImageVector? = null,
  compact: Boolean = false,
  scaleFactor: Float = 1f,
) {
  val touchMod = if (compact || scaleFactor < 0.95f) Modifier else Modifier.minimumInteractiveComponentSize()
  val cornerDp = ((if (compact) 9f else 14f) * scaleFactor).dp
  val iconDp = ((if (compact) 13f else 18f) * scaleFactor).dp
  val labelSp = ((if (compact) 9f else 11f) * scaleFactor).coerceIn(6.5f, 17f).sp
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      touchMod
        .clip(RoundedCornerShape(cornerDp))
        .background(ViceSurface.copy(alpha = 0.80f))
        .border(0.9.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(cornerDp))
        .clickable(onClick = onClick)
        .padding(
          horizontal = ((if (compact) 7f else 9f) * scaleFactor).dp,
          vertical = ((if (compact) 4f else 9f) * scaleFactor).dp,
        )
        .testTag(testTagName),
  ) {
    if (iconPainter != null) {
      Icon(iconPainter, contentDescription = label ?: testTagName, tint = accent, modifier = Modifier.size(iconDp))
    } else if (icon != null) {
      Icon(icon, contentDescription = label ?: testTagName, tint = accent, modifier = Modifier.size(iconDp))
    }
    if (label != null) {
      Spacer(modifier = Modifier.width(((if (compact) 3f else 5f) * scaleFactor).dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = labelSp),
        color = TextPrimary,
      )
    }
    if (trailingIcon != null) {
      Spacer(modifier = Modifier.width((2f * scaleFactor).dp))
      Icon(
        trailingIcon,
        contentDescription = null,
        tint = accent.copy(alpha = 0.85f),
        modifier = Modifier.size(((if (compact) 11f else 14f) * scaleFactor).dp),
      )
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

@Composable
private fun EliminatedOverlay(
  hud: HudSnapshot,
  onShowKiller: () -> Unit,
  onBackToLobby: () -> Unit,
) {
  BoxWithConstraints(
    modifier =
      Modifier.fillMaxSize()
        .background(
          Brush.radialGradient(
            0.0f to SunsetCoral.copy(alpha = 0.14f),
            1.0f to ViceVoid.copy(alpha = 0.72f),
          )
        )
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 10.dp, vertical = 8.dp)
        .testTag("eliminated_overlay")
  ) {
    val compactMobile = maxHeight <= 500.dp || maxWidth <= 950.dp
    // Center Elimination Card + Coin Reward + Back to Lobby
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(if (compactMobile) 6.dp else 10.dp),
      modifier =
        Modifier.align(Alignment.Center)
          .clip(RoundedCornerShape(if (compactMobile) 16.dp else 22.dp))
          .background(ViceSurfaceSolid.copy(alpha = 0.94f))
          .border(1.3.dp, SunsetCoral.copy(alpha = 0.75f), RoundedCornerShape(if (compactMobile) 16.dp else 22.dp))
          .padding(horizontal = if (compactMobile) 16.dp else 24.dp, vertical = if (compactMobile) 12.dp else 18.dp),
    ) {
      Text(
        text = "YOU WERE ELIMINATED",
        style =
          (if (compactMobile) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall)
            .copy(fontWeight = FontWeight.ExtraBold),
        color = SunsetCoral,
      )
      Text(
        text = "KILLED BY ${(hud.eliminatedByName ?: "RIVAL").uppercase()} · ${hud.eliminatedByWeapon.displayName}",
        style =
          (if (compactMobile) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge)
            .copy(fontWeight = FontWeight.Bold),
        color = TextPrimary,
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
          Modifier.clip(RoundedCornerShape(if (compactMobile) 9.dp else 12.dp))
            .background(GoldenHour.copy(alpha = 0.16f))
            .border(1.dp, GoldenHour.copy(alpha = 0.65f), RoundedCornerShape(if (compactMobile) 9.dp else 12.dp))
            .padding(horizontal = if (compactMobile) 10.dp else 14.dp, vertical = if (compactMobile) 5.dp else 8.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.MonetizationOn,
          contentDescription = "Coins Added to ID",
          tint = GoldenHour,
          modifier = Modifier.size(if (compactMobile) 16.dp else 20.dp),
        )
        Text(
          text = "+${hud.lastMatchCoinsEarned} COINS ADDED TO ID · TOTAL $${hud.playerCash}",
          style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compactMobile) 9.5.sp else 11.sp, fontWeight = FontWeight.ExtraBold),
          color = GoldenHour,
          modifier = Modifier.testTag("eliminated_coins_reward"),
        )
      }

      Button(
        onClick = onBackToLobby,
        colors = ButtonDefaults.buttonColors(containerColor = NeonPink, contentColor = ViceVoid),
        shape = RoundedCornerShape(10.dp),
        contentPadding =
          if (compactMobile) PaddingValues(horizontal = 12.dp, vertical = 4.dp)
          else ButtonDefaults.ContentPadding,
        modifier =
          Modifier.height(if (compactMobile) 34.dp else 48.dp)
            .testTag("eliminated_back_lobby_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = null,
          modifier = Modifier.size(if (compactMobile) 14.dp else 18.dp),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
          text = "BACK TO LOBBY",
          fontWeight = FontWeight.ExtraBold,
          style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compactMobile) 10.sp else 12.sp),
        )
      }
    }

    // Right-side "SHOW" option button to watch the killer live (read-only spectate)
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp),
      modifier =
        Modifier.align(Alignment.CenterEnd)
          .clip(RoundedCornerShape(if (compactMobile) 14.dp else 18.dp))
          .background(
            Brush.verticalGradient(
              listOf(ElectricCyan.copy(alpha = 0.92f), PalmMint.copy(alpha = 0.88f))
            )
          )
          .border(1.5.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(if (compactMobile) 14.dp else 18.dp))
          .clickable(onClick = onShowKiller)
          .padding(horizontal = if (compactMobile) 12.dp else 18.dp, vertical = if (compactMobile) 9.dp else 14.dp)
          .testTag("spectate_killer_button"),
    ) {
      Icon(
        imageVector = Icons.Filled.Visibility,
        contentDescription = "Show Killer",
        tint = ViceVoid,
        modifier = Modifier.size(if (compactMobile) 20.dp else 26.dp),
      )
      Text(
        text = "SHOW",
        style =
          (if (compactMobile) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium)
            .copy(fontWeight = FontWeight.ExtraBold),
        color = ViceVoid,
      )
      Text(
        text = "WATCH KILLER",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compactMobile) 8.sp else 9.sp, fontWeight = FontWeight.Bold),
        color = ViceVoid.copy(alpha = 0.86f),
      )
    }
  }
}

@Composable
private fun SpectatorHudOverlay(
  hud: HudSnapshot,
  onBackToLobby: () -> Unit,
) {
  BoxWithConstraints(
    modifier =
      Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 10.dp, vertical = 6.dp)
        .testTag("spectator_hud_overlay")
  ) {
    val compactMobile = maxHeight <= 500.dp || maxWidth <= 950.dp
    // Top-Center Read-Only Killer Status Pill
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(if (compactMobile) 2.dp else 4.dp),
      modifier =
        Modifier.align(Alignment.TopCenter)
          .clip(RoundedCornerShape(if (compactMobile) 12.dp else 16.dp))
          .background(ViceSurfaceSolid.copy(alpha = 0.90f))
          .border(1.2.dp, ElectricCyan.copy(alpha = 0.72f), RoundedCornerShape(if (compactMobile) 12.dp else 16.dp))
          .padding(horizontal = if (compactMobile) 11.dp else 16.dp, vertical = if (compactMobile) 5.dp else 8.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compactMobile) 6.dp else 8.dp),
      ) {
        Box(
          modifier =
            Modifier.clip(RoundedCornerShape(5.dp))
              .background(SunsetCoral)
              .padding(horizontal = 6.dp, vertical = 1.5.dp)
        ) {
          Text(
            text = "SPECTATING",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compactMobile) 8.5.sp else 10.sp, fontWeight = FontWeight.ExtraBold),
            color = Color.White,
          )
        }
        Text(
          text = (hud.spectatingTargetName ?: hud.eliminatedByName ?: "KILLER").uppercase(),
          style = MaterialTheme.typography.labelMedium.copy(fontSize = if (compactMobile) 10.sp else 13.sp, fontWeight = FontWeight.ExtraBold),
          color = TextPrimary,
          modifier = Modifier.testTag("spectating_killer_name"),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = "Killer HP",
            tint = PalmMint,
            modifier = Modifier.size(if (compactMobile) 11.dp else 13.dp),
          )
          Spacer(modifier = Modifier.width(3.dp))
          Text(
            text = "${hud.spectatingTargetHp} HP",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compactMobile) 9.sp else 11.sp, fontWeight = FontWeight.Bold),
            color = PalmMint,
          )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Filled.GpsFixed,
            contentDescription = "Killer Weapon",
            tint = GoldenHour,
            modifier = Modifier.size(if (compactMobile) 11.dp else 13.dp),
          )
          Spacer(modifier = Modifier.width(3.dp))
          Text(
            text = hud.spectatingTargetWeapon.shortTag,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compactMobile) 9.sp else 11.sp, fontWeight = FontWeight.Bold),
            color = GoldenHour,
          )
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.Lock,
          contentDescription = "Controls Locked",
          tint = ElectricCyan,
          modifier = Modifier.size(10.dp),
        )
        Text(
          text = "VIEW ONLY · CONTROLS LOCKED · DRAG TO ROTATE CAMERA",
          style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compactMobile) 8.sp else 9.sp, fontWeight = FontWeight.SemiBold),
          color = TextSecondary,
        )
      }
    }

    // Side / Top-Right "BACK" Button to return to Lobby and start a new match
    Button(
      onClick = onBackToLobby,
      colors = ButtonDefaults.buttonColors(containerColor = GoldenHour, contentColor = ViceVoid),
      shape = RoundedCornerShape(if (compactMobile) 10.dp else 14.dp),
      contentPadding =
        if (compactMobile) PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        else ButtonDefaults.ContentPadding,
      modifier =
        Modifier.align(Alignment.CenterEnd)
          .height(if (compactMobile) 34.dp else 52.dp)
          .border(1.2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(if (compactMobile) 10.dp else 14.dp))
          .testTag("spectate_back_to_lobby_button"),
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "Back to Lobby",
        modifier = Modifier.size(if (compactMobile) 14.dp else 18.dp),
      )
      Spacer(modifier = Modifier.width(5.dp))
      Text(
        text = "BACK TO LOBBY",
        fontWeight = FontWeight.ExtraBold,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compactMobile) 9.5.sp else 12.sp),
      )
    }
  }
}
