package com.example.ui.hud

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.game.state.HudSnapshot
import com.example.game.state.TimeOfDayPreset
import com.example.game.voice.VoiceChatUiState
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldenHour
import com.example.ui.theme.NeonPink
import com.example.ui.theme.PalmMint
import com.example.ui.theme.SunsetCoral
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ViceSurface
import com.example.ui.theme.ViceSurfaceSolid
import com.example.ui.theme.ViceVoid
import kotlinx.coroutines.delay

private data class LobbyFriendCatalogEntry(
  val id: String,
  val name: String,
  val heroIndex: Int,
  val accentColor: Color,
  val tag: String,
)

private val LOBBY_BUDDY_CATALOG =
  listOf(
    LobbyFriendCatalogEntry("ai_menor", "MENOR", 2, ElectricCyan, "AI"),
    LobbyFriendCatalogEntry("ai_draki", "DRAKI", 1, GoldenHour, "AI"),
    LobbyFriendCatalogEntry("ai_whisky", "WHISKY", 3, PalmMint, "AI"),
    LobbyFriendCatalogEntry("ai_corvo", "CORVO", 4, SunsetCoral, "AI"),
    LobbyFriendCatalogEntry("ai_kobra", "KOBRA", 5, NeonPink, "AI"),
  )

/**
 * Viewport-responsive Free Fire HUD scale tokens using clamp(min, vw/vh, max) math so every
 * button, icon, font, and panel shrinks ~35-40% on mobile landscape without blocking the center hero.
 */
private class LobbyScaleSpec(vw: Float, vh: Float, private val userScale: Float = 1f) {
  private val mobileCompact = vh <= 500f || vw <= 950f
  private val factor = (if (mobileCompact) 0.88f else 1.0f) * userScale

  private fun clampDp(v: Float, min: Float, max: Float): Dp =
    (v * factor).coerceIn(min * userScale.coerceIn(0.6f, 1.5f), max * userScale.coerceIn(0.6f, 1.5f)).dp

  private fun clampSp(v: Float, min: Float, max: Float): TextUnit =
    (v * factor).coerceIn(min * userScale.coerceIn(0.65f, 1.45f), max * userScale.coerceIn(0.65f, 1.45f)).sp

  val padH: Dp = clampDp(vw * 0.008f, 4f, 8f)
  val padV: Dp = clampDp(vh * 0.010f, 3f, 6f)

  val headerHeight: Dp = clampDp(vh * 0.070f, 22f, 28f)
  val headerHeightFloat: Float = (vh * 0.070f * factor).coerceIn(22f, 28f)
  val idStripHeight: Dp = clampDp(vh * 0.050f, 16f, 20f)
  val avatarSize: Dp = clampDp(vh * 0.050f, 16f, 21f)

  val leftRailWidth: Dp = clampDp(vw * 0.105f, 68f, 86f)
  val leftItemHeight: Dp = clampDp(vh * 0.066f, 21f, 26f)
  val leftItemGap: Dp = clampDp(vh * 0.007f, 2f, 3.5f)
  val leftIconSize: Dp = clampDp(vh * 0.031f, 10f, 12.5f)

  val rightPanelWidth: Dp = clampDp(vw * 0.160f, 104f, 128f)
  val rightTopOffset: Dp = headerHeight + clampDp(vh * 0.014f, 3f, 6f)
  val modeCardHeight: Dp = clampDp(vh * 0.072f, 24f, 29f)
  val startBtnHeight: Dp = clampDp(vh * 0.086f, 29f, 35f)
  val rightBottomClearance: Dp = modeCardHeight + startBtnHeight + clampDp(vh * 0.022f, 6f, 10f)

  val inviteInputHeight: Dp = clampDp(vh * 0.056f, 19f, 23f)
  val friendRowHeight: Dp = clampDp(vh * 0.064f, 22f, 26f)
  val friendAvatarSize: Dp = clampDp(vh * 0.046f, 15f, 19f)
  val friendActionSize: Dp = clampDp(vh * 0.050f, 17f, 21f)

  val bottomBarHeight: Dp = clampDp(vh * 0.070f, 23f, 29f)
  val bottomIconSize: Dp = clampDp(vh * 0.030f, 10f, 12.5f)

  val labelSp: TextUnit = clampSp(vh * 0.021f, 7.0f, 8.8f)
  val microSp: TextUnit = clampSp(vh * 0.017f, 6.0f, 7.2f)
  val brandSp: TextUnit = clampSp(vh * 0.022f, 7.5f, 9.2f)
  val startSp: TextUnit = clampSp(vh * 0.030f, 10f, 12.5f)
  val startIconSize: Dp = clampDp(vh * 0.040f, 13f, 16f)
}

@Composable
fun ViceLobbyOverlay(
  hud: HudSnapshot,
  voiceState: VoiceChatUiState,
  permanentPlayerCode: String = "",
  accountDisplayName: String = "",
  onStartMatch: () -> Unit,
  onRotateHero: (Float) -> Unit,
  onOpenGunShop: () -> Unit,
  onOpenStyleShop: () -> Unit = {},
  onCycleHeroPreset: () -> Unit,
  onCycleWeapon: () -> Unit,
  onToggleSitPose: () -> Unit,
  onCycleTimePreset: () -> Unit,
  onCycleDropZone: () -> Unit,
  onToggleAudioMute: () -> Unit,
  onToggleMicMute: () -> Unit,
  onToggleSpeakerMute: () -> Unit = {},
  onRemotePeerIdChange: (String) -> Unit,
  onInvitePeerById: (String) -> Unit,
  onConnectVoice: () -> Unit,
  onDisconnectVoice: () -> Unit,
  onInviteAiBuddy: (id: String, name: String, heroIdx: Int) -> Unit,
  onRemoveSquadMember: (id: String, isRemoteP2P: Boolean) -> Unit,
  onAcceptIncomingInvite: () -> Unit,
  onDeclineIncomingInvite: () -> Unit,
  onOpenHudSettings: () -> Unit = {},
  hudScaleMultiplier: Float = 1f,
  modifier: Modifier = Modifier,
) {
  val clipboardManager = LocalClipboardManager.current
  val haptics = LocalHapticFeedback.current
  var showVoiceDrawer by remember { mutableStateOf(false) }
  var showInviteRail by remember { mutableStateOf(true) }
  var peerIdCopied by remember { mutableStateOf(false) }

  LaunchedEffect(peerIdCopied) {
    if (peerIdCopied) {
      delay(1600)
      peerIdCopied = false
    }
  }

  val totalSquadCount = 1 + hud.lobbySquadMembers.size

  Box(modifier = modifier.fillMaxSize()) {
    // 1. Center drag-to-spin 3D Hero stage with subtle far-edge vignette leaving center clear
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.horizontalGradient(
              0.0f to ViceVoid.copy(alpha = 0.48f),
              0.15f to Color.Transparent,
              0.83f to Color.Transparent,
              1.0f to ViceVoid.copy(alpha = 0.52f),
            )
          )
          .pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
              change.consume()
              onRotateHero(dragAmount.x)
            }
          }
    )

    // 2. Responsive vw/vh Clamped Free Fire Lobby Frame
    BoxWithConstraints(
      modifier =
        Modifier.fillMaxSize()
          .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
      val s = remember(maxWidth, maxHeight, hudScaleMultiplier) {
        LobbyScaleSpec(maxWidth.value, maxHeight.value, hudScaleMultiplier)
      }

      Box(
        modifier =
          Modifier.fillMaxSize()
            .padding(horizontal = s.padH, vertical = s.padV)
      ) {
        // -----------------------------------------------------------
        // TOP-LEFT: Ultra-Compact Free Fire Profile Plate + Copyable ID
        // -----------------------------------------------------------
        Column(
          verticalArrangement = Arrangement.spacedBy(3.dp),
          modifier = Modifier.align(Alignment.TopStart),
        ) {
          val lobbyLocalSpeaking = voiceState.isLocalSpeaking && !voiceState.isMicMuted
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier =
              Modifier.height(s.headerHeight)
                .clip(RoundedCornerShape(6.dp))
                .background(
                  Brush.horizontalGradient(
                    listOf(ViceSurfaceSolid.copy(alpha = 0.90f), ViceSurface.copy(alpha = 0.62f))
                  )
                )
                .border(
                  width = if (lobbyLocalSpeaking) 1.6.dp else 0.9.dp,
                  color = if (lobbyLocalSpeaking) PalmMint else GoldenHour.copy(alpha = 0.45f),
                  shape = RoundedCornerShape(6.dp),
                )
                .padding(horizontal = 5.dp, vertical = 2.dp),
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier =
                Modifier.size(s.avatarSize)
                  .clip(RoundedCornerShape(4.dp))
                  .background(Brush.linearGradient(listOf(NeonPink, SunsetCoral)))
                  .border(0.8.dp, GoldenHour, RoundedCornerShape(4.dp)),
            ) {
              Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = "Hero emblem",
                tint = ViceVoid,
                modifier = Modifier.size(s.leftIconSize),
              )
            }

            Column(verticalArrangement = Arrangement.Center) {
              Text(
                text = accountDisplayName.ifBlank { hud.heroPresetName },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = s.labelSp, fontWeight = FontWeight.ExtraBold, lineHeight = s.labelSp),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text(
                  text = "$${hud.playerCash}",
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = s.microSp, fontWeight = FontWeight.Bold, lineHeight = s.microSp),
                  color = GoldenHour,
                )
                Text(
                  text = "$totalSquadCount/4",
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = s.microSp, fontWeight = FontWeight.Bold, lineHeight = s.microSp),
                  color = ElectricCyan,
                )
              }
            }
          }

          // Compact ID Copy Pill
          val shareableId = permanentPlayerCode.ifBlank { voiceState.myPeerId }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              modifier =
                Modifier.height(s.idStripHeight)
                  .clip(RoundedCornerShape(5.dp))
                  .background(ViceSurfaceSolid.copy(alpha = 0.82f))
                  .border(0.8.dp, ElectricCyan.copy(alpha = 0.42f), RoundedCornerShape(5.dp))
                  .clickable(enabled = shareableId.isNotBlank()) {
                    clipboardManager.setText(AnnotatedString(shareableId))
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    peerIdCopied = true
                  }
                  .padding(horizontal = 6.dp)
                  .testTag("copy_permanent_id_pill"),
            ) {
              Text(
                text =
                  if (permanentPlayerCode.isNotBlank()) "ID:$permanentPlayerCode"
                  else if (voiceState.myPeerId.isNotBlank()) "ID:${voiceState.myPeerId}"
                  else "SYNC...",
                style =
                  MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = s.microSp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = s.microSp,
                  ),
                color = ElectricCyan,
              )
              Icon(
                imageVector = if (peerIdCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                contentDescription = "Copy Peer ID",
                tint = if (peerIdCopied) PalmMint else TextPrimary,
                modifier = Modifier.size(10.dp),
              )
              if (peerIdCopied) {
                Text(
                  text = "OK",
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = s.microSp, fontWeight = FontWeight.Bold),
                  color = PalmMint,
                )
              }
            }

            if (permanentPlayerCode.isNotBlank() && voiceState.myPeerId.isNotBlank()) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                  Modifier.height(s.idStripHeight)
                    .clip(RoundedCornerShape(5.dp))
                    .background(ViceSurfaceSolid.copy(alpha = 0.72f))
                    .border(0.8.dp, PalmMint.copy(alpha = 0.35f), RoundedCornerShape(5.dp))
                    .clickable {
                      clipboardManager.setText(AnnotatedString(voiceState.myPeerId))
                      haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                      peerIdCopied = true
                    }
                    .padding(horizontal = 5.dp)
                    .testTag("lobby_copy_peer_id_pill"),
              ) {
                Text(
                  text = voiceState.myPeerId,
                  style =
                    MaterialTheme.typography.labelSmall.copy(
                      fontFamily = FontFamily.Monospace,
                      fontSize = s.microSp,
                      lineHeight = s.microSp,
                    ),
                  color = PalmMint,
                )
              }
            }
          }
        }

        // -----------------------------------------------------------
        // LEFT VERTICAL NAVIGATION RIBBONS (Gun Shop, Skins, Hero, Melee, Voice, Time)
        // -----------------------------------------------------------
        Column(
          verticalArrangement = Arrangement.spacedBy(s.leftItemGap),
          modifier =
            Modifier.align(Alignment.CenterStart)
              .padding(top = s.headerHeight * 0.45f, bottom = s.bottomBarHeight * 0.55f)
              .width(s.leftRailWidth),
        ) {
          LobbyLeftNavItem(
            icon = Icons.Filled.ShoppingCart,
            label = "GUN SHOP",
            accent = GoldenHour,
            scale = s,
            onClick = onOpenGunShop,
            testTagName = "lobby_shop_button",
          )
          LobbyLeftNavItem(
            icon = Icons.Filled.Palette,
            label = "SKINS",
            accent = NeonPink,
            scale = s,
            onClick = onOpenStyleShop,
            testTagName = "lobby_style_shop_button",
          )
          LobbyLeftNavItem(
            icon = Icons.Filled.Checkroom,
            label = "HERO",
            accent = ElectricCyan,
            scale = s,
            onClick = onCycleHeroPreset,
            testTagName = "lobby_outfit_button",
          )
          LobbyLeftNavItem(
            icon = Icons.Filled.GpsFixed,
            label = hud.equippedWeapon.shortTag,
            accent = NeonPink,
            scale = s,
            onClick = onCycleWeapon,
            testTagName = "lobby_weapon_button",
          )
          LobbyLeftNavItem(
            icon = Icons.Filled.RecordVoiceOver,
            label = if (showVoiceDrawer) "HIDE MIC" else "VOICE CHAT",
            accent = PalmMint,
            scale = s,
            onClick = { showVoiceDrawer = !showVoiceDrawer },
            testTagName = "lobby_voice_drawer_button",
          )
          LobbyLeftNavItem(
            icon = Icons.Filled.WbTwilight,
            label =
              when (hud.timePreset) {
                TimeOfDayPreset.SUNSET -> "SUNSET"
                TimeOfDayPreset.DAYLIGHT -> "DAY"
                TimeOfDayPreset.NEON_NIGHT -> "NIGHT"
              },
            accent = SunsetCoral,
            scale = s,
            onClick = onCycleTimePreset,
            testTagName = "lobby_time_button",
          )
        }

        // -----------------------------------------------------------
        // TOP-RIGHT: Brand Emblem + Mic + Speaker + SFX + HUD Settings
        // -----------------------------------------------------------
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier.align(Alignment.TopEnd),
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.height(s.headerHeight)
                .clip(RoundedCornerShape(6.dp))
                .background(ViceSurfaceSolid.copy(alpha = 0.78f))
                .border(0.9.dp, GoldenHour.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp),
          ) {
            Text(
              text = "VICE HORIZON",
              style =
                MaterialTheme.typography.labelSmall.copy(
                  fontSize = s.brandSp,
                  fontWeight = FontWeight.ExtraBold,
                  letterSpacing = 0.6.sp,
                ),
              color = GoldenHour,
            )
          }

          FreeFireMicToggleButton(
            isMicMuted = voiceState.isMicMuted,
            status = voiceState.status,
            hasMicPermission = voiceState.hasMicPermission,
            isSpeaking = voiceState.isLocalSpeaking,
            voiceLevel = voiceState.localVoiceLevel,
            compact = true,
            compactHeightDp = s.headerHeightFloat,
            onClick = onToggleMicMute,
          )

          FreeFireSpeakerToggleButton(
            isSpeakerMuted = voiceState.isSpeakerMuted,
            compact = true,
            compactHeightDp = s.headerHeightFloat,
            onClick = onToggleSpeakerMute,
          )

          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.size(s.headerHeight)
                .clip(CircleShape)
                .background(ViceSurfaceSolid.copy(alpha = 0.84f))
                .border(0.9.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                .clickable(onClick = onToggleAudioMute)
                .testTag("lobby_sfx_mute_button"),
          ) {
            Icon(
              imageVector =
                if (hud.isMuted) Icons.AutoMirrored.Filled.VolumeOff
                else Icons.AutoMirrored.Filled.VolumeUp,
              contentDescription = if (hud.isMuted) "Unmute game audio" else "Mute game audio",
              tint = Color.White,
              modifier = Modifier.size(s.leftIconSize),
            )
          }

          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.size(s.headerHeight)
                .clip(CircleShape)
                .background(ViceSurfaceSolid.copy(alpha = 0.84f))
                .border(0.9.dp, GoldenHour.copy(alpha = 0.65f), CircleShape)
                .clickable(onClick = onOpenHudSettings)
                .testTag("lobby_hud_settings_button"),
          ) {
            Icon(
              painter = painterResource(R.drawable.ic_hud_settings),
              contentDescription = "Customize HUD Icon Sizes",
              tint = GoldenHour,
              modifier = Modifier.size(s.leftIconSize * 1.15f),
            )
          }
        }

        // -----------------------------------------------------------
        // RIGHT-SIDE SLIM INVITE FRIENDS RAIL (just like Free Fire!)
        // -----------------------------------------------------------
        Row(
          verticalAlignment = Alignment.Top,
          horizontalArrangement = Arrangement.spacedBy(3.dp),
          modifier =
            Modifier.align(Alignment.TopEnd)
              .padding(top = s.rightTopOffset, bottom = s.rightBottomClearance),
        ) {
          // Collapse / Expand tab handle on edge of Invite rail
          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.width(18.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(topStart = 7.dp, bottomStart = 7.dp))
                .background(ViceSurfaceSolid.copy(alpha = 0.82f))
                .border(0.8.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(topStart = 7.dp, bottomStart = 7.dp))
                .clickable { showInviteRail = !showInviteRail }
                .testTag("lobby_invite_collapse_button"),
          ) {
            Icon(
              imageVector = if (showInviteRail) Icons.Filled.ChevronRight else Icons.Filled.ChevronLeft,
              contentDescription = if (showInviteRail) "Hide invite panel" else "Show invite panel",
              tint = GoldenHour,
              modifier = Modifier.size(13.dp),
            )
          }

          if (showInviteRail) {
            Column(
              verticalArrangement = Arrangement.spacedBy(3.dp),
              modifier =
                Modifier.width(s.rightPanelWidth)
                  .clip(RoundedCornerShape(8.dp))
                  .background(ViceSurfaceSolid.copy(alpha = 0.78f))
                  .border(0.8.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                  .padding(5.dp)
                  .testTag("lobby_invite_list_panel"),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.PersonAdd,
                    contentDescription = null,
                    tint = GoldenHour,
                    modifier = Modifier.size(10.dp),
                  )
                  Text(
                    text = "INVITE",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = s.microSp, fontWeight = FontWeight.ExtraBold),
                    color = TextPrimary,
                  )
                }
                Text(
                  text = "$totalSquadCount/4",
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = s.microSp, fontWeight = FontWeight.Bold),
                  color = PalmMint,
                )
              }

              // Compact Peer / Game ID Invite Row
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Box(
                  contentAlignment = Alignment.CenterStart,
                  modifier =
                    Modifier.weight(1f)
                      .height(s.inviteInputHeight)
                      .clip(RoundedCornerShape(5.dp))
                      .background(Color.Black.copy(alpha = 0.36f))
                      .border(0.8.dp, ElectricCyan.copy(alpha = 0.36f), RoundedCornerShape(5.dp))
                      .padding(horizontal = 5.dp),
                ) {
                  if (voiceState.remotePeerIdInput.isEmpty()) {
                    Text(
                      text = "Friend ID",
                      style = MaterialTheme.typography.labelSmall.copy(fontSize = s.microSp),
                      color = TextSecondary.copy(alpha = 0.65f),
                      maxLines = 1,
                    )
                  }
                  BasicTextField(
                    value = voiceState.remotePeerIdInput,
                    onValueChange = onRemotePeerIdChange,
                    singleLine = true,
                    textStyle =
                      MaterialTheme.typography.labelSmall.copy(
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = s.microSp,
                      ),
                    cursorBrush = SolidColor(ElectricCyan),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onInvitePeerById(voiceState.remotePeerIdInput) }),
                    modifier = Modifier.fillMaxWidth().testTag("lobby_peer_id_input"),
                  )
                }

                Box(
                  contentAlignment = Alignment.Center,
                  modifier =
                    Modifier.height(s.inviteInputHeight)
                      .clip(RoundedCornerShape(5.dp))
                      .background(ElectricCyan.copy(alpha = 0.22f))
                      .border(0.8.dp, ElectricCyan.copy(alpha = 0.8f), RoundedCornerShape(5.dp))
                      .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onInvitePeerById(voiceState.remotePeerIdInput)
                      }
                      .padding(horizontal = 5.dp)
                      .testTag("lobby_peer_invite_button"),
                ) {
                  Text(
                    text = "ADD",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = s.microSp, fontWeight = FontWeight.ExtraBold),
                    color = ElectricCyan,
                  )
                }
              }

              if (voiceState.statusDetail.isNotBlank()) {
                Text(
                  text = voiceState.statusDetail,
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = s.microSp, fontWeight = FontWeight.SemiBold),
                  color =
                    when (voiceState.status) {
                      com.example.game.voice.VoiceCallStatus.CONNECTED -> PalmMint
                      com.example.game.voice.VoiceCallStatus.ERROR -> SunsetCoral
                      else -> GoldenHour
                    },
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.fillMaxWidth(),
                )
              }

              LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.5.dp),
                contentPadding = PaddingValues(vertical = 1.dp),
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 148.dp),
              ) {
                val p2pMembers = hud.lobbySquadMembers.filter { it.isRemoteP2P }
                items(p2pMembers, key = { "p2p_${it.id}" }) { remote ->
                  LobbyFriendInviteRow(
                    name = remote.name,
                    subtitle = "P2P",
                    accent = PalmMint,
                    isJoined = true,
                    scale = s,
                    onActionClick = { onRemoveSquadMember(remote.id, true) },
                    testTagName = "invite_row_${remote.id}",
                  )
                }

                items(LOBBY_BUDDY_CATALOG, key = { it.id }) { friend ->
                  val joined = hud.lobbySquadMembers.any { it.id == friend.id }
                  LobbyFriendInviteRow(
                    name = friend.name,
                    subtitle = if (joined) "READY" else "ONLINE",
                    accent = friend.accentColor,
                    isJoined = joined,
                    scale = s,
                    onActionClick = {
                      haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                      onInviteAiBuddy(friend.id, friend.name, friend.heroIndex)
                    },
                    testTagName = "invite_row_${friend.id}",
                  )
                }
              }
            }
          }
        }

        // -----------------------------------------------------------
        // BOTTOM-LEFT DOCK: Sleek Free Fire-Style Divided Bar (POSE | VAULT | ARMAS | SQUAD)
        // -----------------------------------------------------------
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier.align(Alignment.BottomStart)
              .height(s.bottomBarHeight)
              .clip(RoundedCornerShape(6.dp))
              .background(ViceSurfaceSolid.copy(alpha = 0.84f))
              .border(0.8.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
              .padding(horizontal = 4.dp),
        ) {
          LobbyBottomTabButton(
            icon = Icons.Filled.EventSeat,
            label = if (hud.isSitting) "STAND" else "POSE",
            scale = s,
            onClick = onToggleSitPose,
            testTagName = "lobby_pose_button",
          )
          LobbyBarDivider()
          LobbyBottomTabButton(
            icon = Icons.Filled.Checkroom,
            label = "VAULT",
            scale = s,
            onClick = onOpenStyleShop,
            testTagName = "lobby_vault_button",
          )
          LobbyBarDivider()
          LobbyBottomTabButton(
            icon = Icons.Filled.GpsFixed,
            label = "ARMAS",
            scale = s,
            onClick = onOpenGunShop,
            testTagName = "lobby_armas_button",
          )
          LobbyBarDivider()
          LobbyBottomTabButton(
            icon = Icons.Filled.Group,
            label = "SQUAD $totalSquadCount/4",
            scale = s,
            onClick = { showVoiceDrawer = !showVoiceDrawer },
            testTagName = "lobby_squad_status_button",
          )
        }

        // -----------------------------------------------------------
        // BOTTOM-CENTER: Compact Ready Squad Pill (only when squadmates joined)
        // -----------------------------------------------------------
        if (hud.lobbySquadMembers.isNotEmpty()) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier =
              Modifier.align(Alignment.BottomCenter)
                .padding(bottom = 3.dp)
                .clip(RoundedCornerShape(50))
                .background(ViceSurfaceSolid.copy(alpha = 0.82f))
                .border(0.9.dp, PalmMint.copy(alpha = 0.65f), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .testTag("lobby_squad_banner"),
          ) {
            Icon(
              imageVector = Icons.Filled.Group,
              contentDescription = "Squad ready",
              tint = PalmMint,
              modifier = Modifier.size(11.dp),
            )
            Text(
              text = "YOU + ${hud.lobbySquadMembers.joinToString(", ") { it.name }}",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = s.microSp, fontWeight = FontWeight.Bold),
              color = TextPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        // -----------------------------------------------------------
        // BOTTOM-RIGHT LAUNCH DOCK: Compact Mode Plate + Golden START Button
        // -----------------------------------------------------------
        Column(
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier.align(Alignment.BottomEnd).width(s.rightPanelWidth),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier =
              Modifier.fillMaxWidth()
                .height(s.modeCardHeight)
                .clip(RoundedCornerShape(6.dp))
                .background(ViceSurfaceSolid.copy(alpha = 0.88f))
                .border(0.9.dp, ElectricCyan.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                .clickable {
                  haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onCycleDropZone()
                }
                .padding(horizontal = 7.dp)
                .testTag("cycle_drop_zone_button"),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              modifier = Modifier.weight(1f),
            ) {
              Icon(
                imageVector = Icons.Filled.FlightTakeoff,
                contentDescription = "Drop Mode",
                tint = ElectricCyan,
                modifier = Modifier.size(s.leftIconSize),
              )
              Column(verticalArrangement = Arrangement.Center) {
                Text(
                  text = hud.lobbyDropZone.title,
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = s.microSp, fontWeight = FontWeight.ExtraBold, lineHeight = s.microSp),
                  color = TextPrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = hud.lobbyDropZone.subtitle,
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = (s.microSp.value - 0.8f).coerceAtLeast(6f).sp, lineHeight = s.microSp),
                  color = ElectricCyan,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
            Icon(
              imageVector = Icons.Filled.SwapHoriz,
              contentDescription = "Switch map zone",
              tint = TextSecondary,
              modifier = Modifier.size(12.dp),
            )
          }

          Button(
            onClick = {
              haptics.performHapticFeedback(HapticFeedbackType.LongPress)
              onStartMatch()
            },
            colors =
              ButtonDefaults.buttonColors(
                containerColor = GoldenHour,
                contentColor = ViceVoid,
              ),
            shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier =
              Modifier.fillMaxWidth()
                .height(s.startBtnHeight)
                .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                .testTag("play_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.PlayArrow,
              contentDescription = null,
              tint = ViceVoid,
              modifier = Modifier.size(s.startIconSize),
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = if (totalSquadCount > 1) "START ($totalSquadCount)" else "START",
              style = MaterialTheme.typography.labelLarge.copy(fontSize = s.startSp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp),
              color = ViceVoid,
            )
          }
        }

        // -----------------------------------------------------------
        // TOP-CENTER: Compact Toast Banner, Incoming Invite & Voice Drawer
        // -----------------------------------------------------------
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp),
        ) {
          if (hud.lastHitBanner != null) {
            Box(
              modifier =
                Modifier.clip(RoundedCornerShape(50))
                  .background(ViceSurfaceSolid.copy(alpha = 0.88f))
                  .border(1.dp, GoldenHour.copy(alpha = 0.78f), RoundedCornerShape(50))
                  .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
              Text(
                text = hud.lastHitBanner,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = s.microSp, fontWeight = FontWeight.Bold),
                color = GoldenHour,
              )
            }
          }

          val incoming = voiceState.incomingInvite
          if (incoming != null) {
            SquadInvitePopup(
              invite = incoming,
              onAccept = onAcceptIncomingInvite,
              onDecline = onDeclineIncomingInvite,
            )
          }

          AnimatedVisibility(
            visible = showVoiceDrawer,
            enter = fadeIn(),
            exit = fadeOut(),
          ) {
            VoiceChatPanel(
              uiState = voiceState.copy(isPanelExpanded = true),
              onRemotePeerIdChange = onRemotePeerIdChange,
              onConnectVoice = onConnectVoice,
              onDisconnectVoice = onDisconnectVoice,
              onToggleMicMute = onToggleMicMute,
              onCollapse = { showVoiceDrawer = false },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun LobbyLeftNavItem(
  icon: ImageVector,
  label: String,
  accent: Color,
  scale: LobbyScaleSpec,
  onClick: () -> Unit,
  testTagName: String,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
    modifier =
      Modifier.fillMaxWidth()
        .height(scale.leftItemHeight)
        .clip(RoundedCornerShape(4.dp))
        .background(
          Brush.horizontalGradient(
            listOf(ViceSurfaceSolid.copy(alpha = 0.86f), ViceSurface.copy(alpha = 0.42f))
          )
        )
        .border(0.8.dp, accent.copy(alpha = 0.42f), RoundedCornerShape(4.dp))
        .clickable(onClick = onClick)
        .testTag(testTagName),
  ) {
    Box(
      modifier =
        Modifier.width(2.5.dp)
          .fillMaxHeight()
          .background(accent)
    )
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = accent,
      modifier = Modifier.size(scale.leftIconSize),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(fontSize = scale.labelSp, fontWeight = FontWeight.Bold, lineHeight = scale.labelSp),
      color = TextPrimary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(end = 4.dp),
    )
  }
}

@Composable
private fun LobbyFriendInviteRow(
  name: String,
  subtitle: String,
  accent: Color,
  isJoined: Boolean,
  scale: LobbyScaleSpec,
  onActionClick: () -> Unit,
  testTagName: String,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    modifier =
      Modifier.fillMaxWidth()
        .height(scale.friendRowHeight)
        .clip(RoundedCornerShape(5.dp))
        .background(Color.Black.copy(alpha = 0.34f))
        .border(
          0.8.dp,
          if (isJoined) PalmMint.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.12f),
          RoundedCornerShape(5.dp),
        )
        .padding(start = 4.dp, end = 3.dp),
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier.size(scale.friendAvatarSize)
          .clip(RoundedCornerShape(4.dp))
          .background(accent.copy(alpha = 0.25f))
          .border(0.8.dp, accent, RoundedCornerShape(4.dp)),
    ) {
      Text(
        text = name.take(1),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = scale.microSp, fontWeight = FontWeight.ExtraBold),
        color = accent,
      )
    }

    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
      Text(
        text = name,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = scale.microSp, fontWeight = FontWeight.Bold, lineHeight = scale.microSp),
        color = TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = (scale.microSp.value - 0.8f).coerceAtLeast(5.8f).sp, lineHeight = scale.microSp),
        color = if (isJoined) PalmMint else TextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier.size(scale.friendActionSize)
          .clip(RoundedCornerShape(4.dp))
          .background(
            if (isJoined) PalmMint.copy(alpha = 0.24f) else GoldenHour.copy(alpha = 0.22f)
          )
          .border(
            0.8.dp,
            if (isJoined) PalmMint else GoldenHour,
            RoundedCornerShape(4.dp),
          )
          .clickable(onClick = onActionClick)
          .testTag(testTagName),
    ) {
      Icon(
        imageVector = if (isJoined) Icons.Filled.Check else Icons.Filled.Add,
        contentDescription = if (isJoined) "Remove $name" else "Invite $name",
        tint = if (isJoined) PalmMint else GoldenHour,
        modifier = Modifier.size(11.dp),
      )
    }
  }
}

@Composable
private fun LobbyBottomTabButton(
  icon: ImageVector,
  label: String,
  scale: LobbyScaleSpec,
  onClick: () -> Unit,
  testTagName: String,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(3.dp),
    modifier =
      Modifier.fillMaxHeight()
        .clip(RoundedCornerShape(4.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 6.dp)
        .testTag(testTagName),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = TextSecondary,
      modifier = Modifier.size(scale.bottomIconSize),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(fontSize = scale.labelSp, fontWeight = FontWeight.Bold),
      color = TextPrimary,
      maxLines = 1,
    )
  }
}

@Composable
private fun LobbyBarDivider() {
  Box(
    modifier =
      Modifier.width(1.dp)
        .height(12.dp)
        .background(Color.White.copy(alpha = 0.18f))
  )
}
