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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Group
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.state.HudSnapshot
import com.example.game.state.LobbySquadMember
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
    LobbyFriendCatalogEntry("ai_menor", "MENOR", 2, ElectricCyan, "AI SQUAD"),
    LobbyFriendCatalogEntry("ai_draki", "DRAKI", 1, GoldenHour, "AI SQUAD"),
    LobbyFriendCatalogEntry("ai_whisky", "WHISKY", 3, PalmMint, "AI SQUAD"),
    LobbyFriendCatalogEntry("ai_corvo", "CORVO", 4, SunsetCoral, "AI SQUAD"),
    LobbyFriendCatalogEntry("ai_kobra", "KOBRA", 5, NeonPink, "AI SQUAD"),
  )

@Composable
fun ViceLobbyOverlay(
  hud: HudSnapshot,
  voiceState: VoiceChatUiState,
  onStartMatch: () -> Unit,
  onRotateHero: (Float) -> Unit,
  onOpenGunShop: () -> Unit,
  onCycleHeroPreset: () -> Unit,
  onCycleWeapon: () -> Unit,
  onToggleSitPose: () -> Unit,
  onCycleTimePreset: () -> Unit,
  onCycleDropZone: () -> Unit,
  onToggleAudioMute: () -> Unit,
  onToggleMicMute: () -> Unit,
  onRemotePeerIdChange: (String) -> Unit,
  onInvitePeerById: (String) -> Unit,
  onConnectVoice: () -> Unit,
  onDisconnectVoice: () -> Unit,
  onInviteAiBuddy: (id: String, name: String, heroIdx: Int) -> Unit,
  onRemoveSquadMember: (id: String, isRemoteP2P: Boolean) -> Unit,
  onAcceptIncomingInvite: () -> Unit,
  onDeclineIncomingInvite: () -> Unit,
  onOpenAiAssistant: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val clipboardManager = LocalClipboardManager.current
  val haptics = LocalHapticFeedback.current
  var showVoiceDrawer by remember { mutableStateOf(false) }
  var peerIdCopied by remember { mutableStateOf(false) }

  LaunchedEffect(peerIdCopied) {
    if (peerIdCopied) {
      delay(1600)
      peerIdCopied = false
    }
  }

  val totalSquadCount = 1 + hud.lobbySquadMembers.size

  Box(modifier = modifier.fillMaxSize()) {
    // 1. Center drag-to-spin 3D Hero stage & subtle side edge contrast gradients
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.horizontalGradient(
              0.0f to ViceVoid.copy(alpha = 0.56f),
              0.24f to Color.Transparent,
              0.74f to Color.Transparent,
              1.0f to ViceVoid.copy(alpha = 0.62f),
            )
          )
          .pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
              change.consume()
              onRotateHero(dragAmount.x)
            }
          }
    )

    // 2. Main Safe-Area Free Fire Lobby Frame
    BoxWithConstraints(
      modifier =
        Modifier.fillMaxSize()
          .windowInsetsPadding(WindowInsets.safeDrawing)
          .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
      val compactHeight = maxHeight < 410.dp
      val rightPanelWidth = if (maxWidth < 740.dp) 174.dp else 198.dp
      val leftRailWidth = if (maxWidth < 740.dp) 114.dp else 128.dp

      // -----------------------------------------------------------
      // TOP-LEFT: Player Profile Header + Currencies + Share ID
      // -----------------------------------------------------------
      Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.align(Alignment.TopStart),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier =
            Modifier.clip(RoundedCornerShape(10.dp))
              .background(
                Brush.horizontalGradient(
                  listOf(ViceSurfaceSolid.copy(alpha = 0.94f), ViceSurface.copy(alpha = 0.72f))
                )
              )
              .border(1.dp, GoldenHour.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
              .padding(horizontal = 8.dp, vertical = 5.dp),
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(listOf(NeonPink, SunsetCoral)))
                .border(1.dp, GoldenHour, RoundedCornerShape(8.dp)),
          ) {
            Icon(
              imageVector = Icons.Filled.Shield,
              contentDescription = "Hero emblem",
              tint = ViceVoid,
              modifier = Modifier.size(20.dp),
            )
          }

          Column {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = hud.heroPresetName,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = TextPrimary,
              )
              Text(
                text = "LV.71",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                color = GoldenHour,
              )
            }
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text(
                text = "$${hud.playerCash}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = GoldenHour,
              )
              Text(
                text = "SQUAD $totalSquadCount/4",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = ElectricCyan,
              )
            }
          }
        }

        // Quick Lobby Peer ID Copy Strip
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier =
            Modifier.clip(RoundedCornerShape(8.dp))
              .background(ViceSurfaceSolid.copy(alpha = 0.86f))
              .border(1.dp, ElectricCyan.copy(alpha = 0.42f), RoundedCornerShape(8.dp))
              .clickable(enabled = voiceState.myPeerId.isNotBlank()) {
                clipboardManager.setText(AnnotatedString(voiceState.myPeerId))
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                peerIdCopied = true
              }
              .padding(horizontal = 8.dp, vertical = 4.dp)
              .testTag("lobby_copy_peer_id_pill"),
        ) {
          Text(
            text =
              if (voiceState.myPeerId.isNotBlank()) "ID: ${voiceState.myPeerId}"
              else "ID: connecting...",
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
              ),
            color = ElectricCyan,
          )
          Icon(
            imageVector = if (peerIdCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
            contentDescription = "Copy Lobby Peer ID",
            tint = if (peerIdCopied) PalmMint else TextPrimary,
            modifier = Modifier.size(13.dp),
          )
          if (peerIdCopied) {
            Text(
              text = "COPIED",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
              color = PalmMint,
            )
          }
        }
      }

      // -----------------------------------------------------------
      // LEFT VERTICAL NAVIGATION RAIL (Shop, Outfit, Weapons, Voice, Sky)
      // -----------------------------------------------------------
      Column(
        verticalArrangement = Arrangement.spacedBy(if (compactHeight) 4.dp else 6.dp),
        modifier =
          Modifier.align(Alignment.CenterStart)
            .padding(top = if (compactHeight) 14.dp else 6.dp, bottom = 30.dp)
            .width(leftRailWidth),
      ) {
        LobbyLeftNavItem(
          icon = Icons.Filled.ShoppingCart,
          label = "SHOP",
          accent = GoldenHour,
          onClick = onOpenGunShop,
          testTagName = "lobby_shop_button",
        )
        LobbyLeftNavItem(
          icon = Icons.Filled.Checkroom,
          label = "OUTFIT",
          accent = ElectricCyan,
          onClick = onCycleHeroPreset,
          testTagName = "lobby_outfit_button",
        )
        LobbyLeftNavItem(
          icon = Icons.Filled.GpsFixed,
          label = hud.equippedWeapon.shortTag,
          accent = NeonPink,
          onClick = onCycleWeapon,
          testTagName = "lobby_weapon_button",
        )
        LobbyLeftNavItem(
          icon = Icons.Filled.RecordVoiceOver,
          label = if (showVoiceDrawer) "HIDE VOICE" else "VOICE CHAT",
          accent = PalmMint,
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
          onClick = onCycleTimePreset,
          testTagName = "lobby_time_button",
        )
      }

      // -----------------------------------------------------------
      // TOP-RIGHT: Brand Crest + Free Fire Mic Toggle + SFX Mute
      // -----------------------------------------------------------
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.align(Alignment.TopEnd),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .defaultMinSize(minHeight = 44.dp)
              .clip(RoundedCornerShape(22.dp))
              .background(
                Brush.horizontalGradient(
                  listOf(ElectricCyan.copy(alpha = 0.24f), NeonPink.copy(alpha = 0.22f))
                )
              )
              .border(1.5.dp, ElectricCyan.copy(alpha = 0.85f), RoundedCornerShape(22.dp))
              .clickable(onClick = onOpenAiAssistant)
              .padding(horizontal = 11.dp, vertical = 6.dp)
              .testTag("lobby_ai_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = "Ask Multilingual AI Assistant",
            tint = ElectricCyan,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = "ASK AI",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold),
            color = TextPrimary,
          )
        }

        Box(
          modifier =
            Modifier.clip(RoundedCornerShape(10.dp))
              .background(ViceSurfaceSolid.copy(alpha = 0.84f))
              .border(1.dp, GoldenHour.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
              .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
          Text(
            text = "VICE HORIZON",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp),
            color = GoldenHour,
          )
        }

        FreeFireMicToggleButton(
          isMicMuted = voiceState.isMicMuted,
          status = voiceState.status,
          hasMicPermission = voiceState.hasMicPermission,
          onClick = onToggleMicMute,
        )

        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .size(44.dp)
              .clip(CircleShape)
              .background(ViceSurfaceSolid.copy(alpha = 0.86f))
              .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
              .clickable(onClick = onToggleAudioMute)
              .testTag("lobby_sfx_mute_button"),
        ) {
          Icon(
            imageVector =
              if (hud.isMuted) Icons.AutoMirrored.Filled.VolumeOff
              else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = if (hud.isMuted) "Unmute game audio" else "Mute game audio",
            tint = Color.White,
            modifier = Modifier.size(18.dp),
          )
        }
      }

      // -----------------------------------------------------------
      // RIGHT-SIDE FRIEND / SQUAD INVITE COLUMN (just like photo!)
      // -----------------------------------------------------------
      Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier =
          Modifier.align(Alignment.TopEnd)
            .padding(top = 52.dp, bottom = 92.dp)
            .width(rightPanelWidth)
            .clip(RoundedCornerShape(12.dp))
            .background(ViceSurfaceSolid.copy(alpha = 0.82f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
            .padding(7.dp)
            .testTag("lobby_invite_list_panel"),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.PersonAdd,
              contentDescription = null,
              tint = GoldenHour,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "INVITE FRIENDS",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold),
              color = TextPrimary,
            )
          }
          Text(
            text = "$totalSquadCount/4",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = PalmMint,
          )
        }

        // PeerJS Online Friend ID Invite Row
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Box(
            contentAlignment = Alignment.CenterStart,
            modifier =
              Modifier.weight(1f)
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.36f))
                .border(1.dp, ElectricCyan.copy(alpha = 0.36f), RoundedCornerShape(8.dp))
                .padding(horizontal = 7.dp),
          ) {
            if (voiceState.remotePeerIdInput.isEmpty()) {
              Text(
                text = "Peer ID...",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
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
                  fontSize = 10.sp,
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
              Modifier.minimumInteractiveComponentSize()
                .defaultMinSize(minWidth = 48.dp, minHeight = 36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ElectricCyan.copy(alpha = 0.22f))
                .border(1.dp, ElectricCyan.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                .clickable {
                  haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                  onInvitePeerById(voiceState.remotePeerIdInput)
                }
                .padding(horizontal = 7.dp)
                .testTag("lobby_peer_invite_button"),
          ) {
            Text(
              text = "INVITE",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
              color = ElectricCyan,
            )
          }
        }

        // Scrollable Friend Invite Strip (Joined P2P Peers + Squad Buddies)
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(4.dp),
          contentPadding = PaddingValues(vertical = 2.dp),
          modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 200.dp),
        ) {
          val p2pMembers = hud.lobbySquadMembers.filter { it.isRemoteP2P }
          items(p2pMembers, key = { "p2p_${it.id}" }) { remote ->
            LobbyFriendInviteRow(
              name = remote.name,
              subtitle = "LIVE P2P · READY",
              accent = PalmMint,
              isJoined = true,
              onActionClick = { onRemoveSquadMember(remote.id, true) },
              testTagName = "invite_row_${remote.id}",
            )
          }

          items(LOBBY_BUDDY_CATALOG, key = { it.id }) { friend ->
            val joined = hud.lobbySquadMembers.any { it.id == friend.id }
            LobbyFriendInviteRow(
              name = friend.name,
              subtitle = if (joined) "IN LOBBY · READY" else "${friend.tag} · ONLINE",
              accent = friend.accentColor,
              isJoined = joined,
              onActionClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onInviteAiBuddy(friend.id, friend.name, friend.heroIndex)
              },
              testTagName = "invite_row_${friend.id}",
            )
          }
        }
      }

      // -----------------------------------------------------------
      // BOTTOM-LEFT DOCK: Pose / Vault / Weapons / Voice (matching FF bar)
      // -----------------------------------------------------------
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
          Modifier.align(Alignment.BottomStart)
            .clip(RoundedCornerShape(12.dp))
            .background(ViceSurfaceSolid.copy(alpha = 0.88f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
      ) {
        LobbyBottomTabButton(
          icon = Icons.Filled.EventSeat,
          label = if (hud.isSitting) "STAND" else "POSE",
          onClick = onToggleSitPose,
          testTagName = "lobby_pose_button",
        )
        LobbyBottomTabButton(
          icon = Icons.Filled.Checkroom,
          label = "VAULT",
          onClick = onCycleHeroPreset,
          testTagName = "lobby_vault_button",
        )
        LobbyBottomTabButton(
          icon = Icons.Filled.GpsFixed,
          label = "ARMAS",
          onClick = onOpenGunShop,
          testTagName = "lobby_armas_button",
        )
        LobbyBottomTabButton(
          icon = Icons.Filled.Group,
          label = "SQUAD ${totalSquadCount}/4",
          onClick = { showVoiceDrawer = !showVoiceDrawer },
          testTagName = "lobby_squad_status_button",
        )
      }

      // -----------------------------------------------------------
      // BOTTOM-CENTER: Live Squad Tags + Hero Drag Hint
      // -----------------------------------------------------------
      if (hud.lobbySquadMembers.isNotEmpty()) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier =
            Modifier.align(Alignment.BottomCenter)
              .padding(bottom = 8.dp)
              .clip(RoundedCornerShape(50))
              .background(ViceSurfaceSolid.copy(alpha = 0.86f))
              .border(1.dp, PalmMint.copy(alpha = 0.65f), RoundedCornerShape(50))
              .padding(horizontal = 12.dp, vertical = 5.dp)
              .testTag("lobby_squad_banner"),
        ) {
          Icon(
            imageVector = Icons.Filled.Group,
            contentDescription = "Squad ready",
            tint = PalmMint,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = "SQUAD READY: YOU + ${hud.lobbySquadMembers.joinToString(", ") { it.name }}",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      // -----------------------------------------------------------
      // BOTTOM-RIGHT LAUNCH DOCK (Mode/Map Card + Golden START like DECOLAR!)
      // -----------------------------------------------------------
      Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.align(Alignment.BottomEnd).width(rightPanelWidth),
      ) {
        // Mode / Map Selector Plate
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier =
            Modifier.fillMaxWidth()
              .defaultMinSize(minHeight = 40.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(ViceSurfaceSolid.copy(alpha = 0.90f))
              .border(1.dp, ElectricCyan.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
              .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCycleDropZone()
              }
              .padding(horizontal = 10.dp, vertical = 5.dp)
              .testTag("cycle_drop_zone_button"),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
          ) {
            Icon(
              imageVector = Icons.Filled.FlightTakeoff,
              contentDescription = "Drop Mode",
              tint = ElectricCyan,
              modifier = Modifier.size(16.dp),
            )
            Column {
              Text(
                text = hud.lobbyDropZone.title,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold),
                color = TextPrimary,
                maxLines = 1,
              )
              Text(
                text = hud.lobbyDropZone.subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = ElectricCyan,
                maxLines = 1,
              )
            }
          }
          Icon(
            imageVector = Icons.Filled.SwapHoriz,
            contentDescription = "Switch map zone",
            tint = TextSecondary,
            modifier = Modifier.size(16.dp),
          )
        }

        // Golden-Yellow START Button (like DECOLAR in screenshot)
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
          shape = RoundedCornerShape(10.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier =
            Modifier.fillMaxWidth()
              .height(52.dp)
              .border(1.5.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
              .testTag("play_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = ViceVoid,
            modifier = Modifier.size(22.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = if (totalSquadCount > 1) "START ($totalSquadCount)" else "START",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp),
            color = ViceVoid,
          )
        }
      }

      // -----------------------------------------------------------
      // TOP-CENTER: Toast Banner, Incoming Peer Invite, & Optional Voice Drawer
      // -----------------------------------------------------------
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
      ) {
        if (hud.lastHitBanner != null) {
          Box(
            modifier =
              Modifier.clip(RoundedCornerShape(50))
                .background(ViceSurfaceSolid.copy(alpha = 0.90f))
                .border(1.2.dp, GoldenHour.copy(alpha = 0.78f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 5.dp)
          ) {
            Text(
              text = hud.lastHitBanner,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = GoldenHour,
            )
          }
        }

        val incoming = voiceState.incomingInvite
        if (incoming != null) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
              Modifier.widthIn(max = 330.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ViceSurfaceSolid.copy(alpha = 0.95f))
                .border(1.5.dp, PalmMint, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp)
                .testTag("incoming_invite_banner"),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "SQUAD INVITE",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                color = PalmMint,
              )
              Text(
                text = "${incoming.senderName} (${incoming.peerId})",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Box(
              contentAlignment = Alignment.Center,
              modifier =
                Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 36.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(PalmMint)
                  .clickable(onClick = onAcceptIncomingInvite)
                  .padding(horizontal = 10.dp)
                  .testTag("accept_invite_button"),
            ) {
              Text(
                text = "JOIN",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = ViceVoid,
              )
            }
            Box(
              contentAlignment = Alignment.Center,
              modifier =
                Modifier.size(36.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(SunsetCoral.copy(alpha = 0.25f))
                  .border(1.dp, SunsetCoral, RoundedCornerShape(8.dp))
                  .clickable(onClick = onDeclineIncomingInvite)
                  .testTag("decline_invite_button"),
            ) {
              Icon(Icons.Filled.Close, contentDescription = "Decline", tint = SunsetCoral, modifier = Modifier.size(16.dp))
            }
          }
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

@Composable
private fun LobbyLeftNavItem(
  icon: ImageVector,
  label: String,
  accent: Color,
  onClick: () -> Unit,
  testTagName: String,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier =
      Modifier.fillMaxWidth()
        .minimumInteractiveComponentSize()
        .defaultMinSize(minHeight = 42.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(
          Brush.horizontalGradient(
            listOf(ViceSurfaceSolid.copy(alpha = 0.90f), ViceSurface.copy(alpha = 0.65f))
          )
        )
        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 9.dp, vertical = 7.dp)
        .testTag(testTagName),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = accent,
      modifier = Modifier.size(18.dp),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
      color = TextPrimary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun LobbyFriendInviteRow(
  name: String,
  subtitle: String,
  accent: Color,
  isJoined: Boolean,
  onActionClick: () -> Unit,
  testTagName: String,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(Color.Black.copy(alpha = 0.34f))
        .border(
          1.dp,
          if (isJoined) PalmMint.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.14f),
          RoundedCornerShape(8.dp),
        )
        .padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier.size(28.dp)
          .clip(RoundedCornerShape(7.dp))
          .background(accent.copy(alpha = 0.25f))
          .border(1.dp, accent, RoundedCornerShape(7.dp)),
    ) {
      Text(
        text = name.take(1),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
        color = accent,
      )
    }

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = name,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
        color = TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
        color = if (isJoined) PalmMint else TextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier.minimumInteractiveComponentSize()
          .defaultMinSize(minWidth = 36.dp, minHeight = 36.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(
            if (isJoined) PalmMint.copy(alpha = 0.24f) else GoldenHour.copy(alpha = 0.22f)
          )
          .border(
            1.dp,
            if (isJoined) PalmMint else GoldenHour,
            RoundedCornerShape(8.dp),
          )
          .clickable(onClick = onActionClick)
          .testTag(testTagName),
    ) {
      Icon(
        imageVector = if (isJoined) Icons.Filled.Check else Icons.Filled.Add,
        contentDescription = if (isJoined) "Remove $name" else "Invite $name",
        tint = if (isJoined) PalmMint else GoldenHour,
        modifier = Modifier.size(16.dp),
      )
    }
  }
}

@Composable
private fun LobbyBottomTabButton(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
  testTagName: String,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    modifier =
      Modifier.minimumInteractiveComponentSize()
        .defaultMinSize(minHeight = 40.dp)
        .clip(RoundedCornerShape(8.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 6.dp)
        .testTag(testTagName),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = TextSecondary,
      modifier = Modifier.size(15.dp),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
      color = TextPrimary,
    )
  }
}
