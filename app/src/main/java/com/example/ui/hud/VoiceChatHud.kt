package com.example.ui.hud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RecordVoiceOver
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.game.state.LobbySquadMember
import com.example.game.voice.IncomingSquadInvite
import com.example.game.voice.VoiceCallStatus
import com.example.game.voice.VoiceChatUiState
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldenHour
import com.example.ui.theme.PalmMint
import com.example.ui.theme.SunsetCoral
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ViceSurface
import com.example.ui.theme.ViceSurfaceSolid
import kotlinx.coroutines.delay

@Composable
fun FreeFireMicToggleButton(
  isMicMuted: Boolean,
  status: VoiceCallStatus,
  hasMicPermission: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  isSpeaking: Boolean = false,
  voiceLevel: Float = 0f,
  compact: Boolean = false,
  compactHeightDp: Float = 28f,
) {
  val accent =
    when {
      isMicMuted -> SunsetCoral
      isSpeaking -> PalmMint
      status == VoiceCallStatus.CONNECTED -> PalmMint
      hasMicPermission -> ElectricCyan
      else -> GoldenHour
    }
  val ringScale by animateFloatAsState(
    targetValue = if (isSpeaking && !isMicMuted) 1.04f + (voiceLevel * 0.08f) else 1f,
    label = "micRingScale",
  )

  val heightModifier =
    if (compact) {
      Modifier.height(compactHeightDp.dp)
    } else {
      Modifier.minimumInteractiveComponentSize().defaultMinSize(minHeight = 48.dp)
    }
  val circleSize = if (compact) (compactHeightDp * 0.72f).dp else 30.dp
  val iconSize = if (compact) (compactHeightDp * 0.44f).dp else 18.dp
  val primaryTextSp = if (compact) (compactHeightDp * 0.27f).coerceIn(7f, 9f).sp else 10.sp
  val secondaryTextSp = if (compact) (compactHeightDp * 0.21f).coerceIn(6f, 7.5f).sp else 8.sp

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp),
    modifier =
      modifier
        .then(heightModifier)
        .clip(RoundedCornerShape(if (compact) 14.dp else 24.dp))
        .background(
          Brush.horizontalGradient(
            listOf(
              ViceSurfaceSolid.copy(alpha = 0.92f),
              accent.copy(alpha = if (isMicMuted) 0.18f else if (isSpeaking) 0.34f else 0.24f),
            )
          )
        )
        .border(
          width = if (isSpeaking && !isMicMuted) (if (compact) 1.8.dp else 2.4.dp) else (if (compact) 1.dp else 1.5.dp),
          color = accent.copy(alpha = if (isSpeaking && !isMicMuted) 1f else 0.85f),
          shape = RoundedCornerShape(if (compact) 14.dp else 24.dp),
        )
        .clickable(onClick = onClick)
        .padding(
          start = if (compact) 4.dp else 6.dp,
          end = if (compact) 7.dp else 11.dp,
          top = if (compact) 2.dp else 5.dp,
          bottom = if (compact) 2.dp else 5.dp,
        )
        .testTag("mic_mute_button"),
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier.size(circleSize)
          .scale(ringScale)
          .clip(CircleShape)
          .background(accent.copy(alpha = if (isSpeaking && !isMicMuted) 0.36f else 0.22f))
          .border(if (isSpeaking && !isMicMuted) 1.8.dp else 1.dp, accent.copy(alpha = 0.95f), CircleShape),
    ) {
      Icon(
        imageVector = if (isMicMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
        contentDescription =
          if (isMicMuted) "Unmute microphone" else "Mute microphone",
        tint = accent,
        modifier = Modifier.size(iconSize),
      )
    }
    Column {
      Text(
        text =
          if (isMicMuted) stringResource(R.string.voice_mic_muted)
          else if (isSpeaking) "TALKING"
          else stringResource(R.string.voice_mic_on),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = primaryTextSp, fontWeight = FontWeight.Bold, lineHeight = primaryTextSp),
        color = accent,
      )
      if (!compact) {
        Text(
          text = if (status == VoiceCallStatus.CONNECTED) "LIVE" else "VOICE",
          style = MaterialTheme.typography.labelSmall.copy(fontSize = secondaryTextSp),
          color = TextSecondary,
        )
      }
    }
  }
}

@Composable
fun FreeFireSpeakerToggleButton(
  isSpeakerMuted: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
  compactHeightDp: Float = 28f,
) {
  val accent = if (isSpeakerMuted) SunsetCoral else PalmMint
  val heightModifier =
    if (compact) {
      Modifier.height(compactHeightDp.dp)
    } else {
      Modifier.minimumInteractiveComponentSize().defaultMinSize(minHeight = 48.dp)
    }
  val circleSize = if (compact) (compactHeightDp * 0.70f).dp else 28.dp
  val iconSize = if (compact) (compactHeightDp * 0.42f).dp else 16.dp
  val primaryTextSp = if (compact) (compactHeightDp * 0.26f).coerceIn(7f, 8.5f).sp else 9.sp

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp),
    modifier =
      modifier
        .then(heightModifier)
        .clip(RoundedCornerShape(if (compact) 14.dp else 24.dp))
        .background(ViceSurfaceSolid.copy(alpha = 0.90f))
        .border(if (compact) 1.dp else 1.4.dp, accent.copy(alpha = 0.85f), RoundedCornerShape(if (compact) 14.dp else 24.dp))
        .clickable(onClick = onClick)
        .padding(
          start = if (compact) 4.dp else 7.dp,
          end = if (compact) 7.dp else 10.dp,
          top = if (compact) 2.dp else 5.dp,
          bottom = if (compact) 2.dp else 5.dp,
        )
        .testTag("speaker_mute_button"),
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier.size(circleSize)
          .clip(CircleShape)
          .background(accent.copy(alpha = 0.22f))
          .border(1.dp, accent.copy(alpha = 0.85f), CircleShape),
    ) {
      Icon(
        imageVector =
          if (isSpeakerMuted) Icons.AutoMirrored.Filled.VolumeOff
          else Icons.AutoMirrored.Filled.VolumeUp,
        contentDescription =
          if (isSpeakerMuted) "Unmute all teammates" else "Mute all teammates",
        tint = accent,
        modifier = Modifier.size(iconSize),
      )
    }
    Column {
      Text(
        text = if (isSpeakerMuted) "MUTED" else "TEAM",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = primaryTextSp, fontWeight = FontWeight.ExtraBold, lineHeight = primaryTextSp),
        color = accent,
      )
      if (!compact) {
        Text(
          text = "SPEAKER",
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
          color = TextSecondary,
        )
      }
    }
  }
}

/**
 * Free Fire-style Top-Left In-Game Voice Chat HUD with floating Mic toggle (Mute/Unmute),
 * Speaker toggle (Mute all teammates), and animated speaking indicator rings around the
 * player's name, mic button, and active teammates.
 */
@Composable
fun FreeFireInGameVoiceHud(
  localPlayerName: String,
  uiState: VoiceChatUiState,
  squadMembers: List<LobbySquadMember>,
  onToggleMic: () -> Unit,
  onToggleSpeaker: () -> Unit,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
  scaleFactor: Float = 1f,
) {
  val pulseTransition = rememberInfiniteTransition(label = "voicePulse")
  val pulseAlpha by pulseTransition.animateFloat(
    initialValue = 0.55f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
    label = "pulseAlpha",
  )

  val localSpeaking = uiState.isLocalSpeaking && !uiState.isMicMuted
  val micAccent =
    when {
      uiState.isMicMuted -> SunsetCoral
      localSpeaking -> PalmMint
      uiState.status == VoiceCallStatus.CONNECTED -> PalmMint
      uiState.hasMicPermission -> ElectricCyan
      else -> GoldenHour
    }
  val speakerAccent = if (uiState.isSpeakerMuted) SunsetCoral else ElectricCyan
  val btnSize = ((if (compact) 32f else 48f) * scaleFactor).dp
  val btnIconSize = ((if (compact) 15f else 21f) * scaleFactor).dp
  val minTouchMod = if (compact || scaleFactor < 0.95f) Modifier else Modifier.minimumInteractiveComponentSize()

  Column(
    verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp),
    modifier = modifier.testTag("top_left_voice_hud"),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp),
      modifier =
        Modifier.clip(RoundedCornerShape(if (compact) 18.dp else 28.dp))
          .background(ViceSurfaceSolid.copy(alpha = 0.78f))
          .border(
            width = if (localSpeaking) 1.6.dp else 0.9.dp,
            color =
              if (localSpeaking) PalmMint.copy(alpha = pulseAlpha)
              else Color.White.copy(alpha = 0.18f),
            shape = RoundedCornerShape(if (compact) 18.dp else 28.dp),
          )
          .padding(horizontal = if (compact) 5.dp else 8.dp, vertical = if (compact) 3.dp else 5.dp),
    ) {
      // 1. Floating Speaker Toggle Button (Mute/Unmute All Teammates)
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier.then(minTouchMod)
            .size(btnSize)
            .clip(CircleShape)
            .background(speakerAccent.copy(alpha = if (uiState.isSpeakerMuted) 0.28f else 0.24f))
            .border(
              width = if (compact) 1.2.dp else 1.6.dp,
              color = speakerAccent.copy(alpha = 0.92f),
              shape = CircleShape,
            )
            .clickable(onClick = onToggleSpeaker)
            .testTag("hud_speaker_mute_button"),
      ) {
        Icon(
          imageVector =
            if (uiState.isSpeakerMuted) Icons.AutoMirrored.Filled.VolumeOff
            else Icons.AutoMirrored.Filled.VolumeUp,
          contentDescription =
            if (uiState.isSpeakerMuted) "Unmute all teammates" else "Mute all teammates",
          tint = speakerAccent,
          modifier = Modifier.size(btnIconSize),
        )
      }

      // 2. Floating Mic Toggle Button (Muted by default, glowing speaking ring when active)
      val micScale by animateFloatAsState(
        targetValue = if (localSpeaking) 1.06f + (uiState.localVoiceLevel * 0.08f) else 1f,
        label = "hudMicSpeakScale",
      )
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier.then(minTouchMod)
            .size(btnSize)
            .scale(micScale)
            .clip(CircleShape)
            .background(
              if (localSpeaking) PalmMint.copy(alpha = 0.34f)
              else micAccent.copy(alpha = if (uiState.isMicMuted) 0.26f else 0.22f)
            )
            .border(
              width = if (localSpeaking) (if (compact) 2.2.dp else 3.dp) else (if (compact) 1.2.dp else 1.6.dp),
              color = if (localSpeaking) PalmMint.copy(alpha = pulseAlpha) else micAccent.copy(alpha = 0.92f),
              shape = CircleShape,
            )
            .clickable(onClick = onToggleMic)
            .testTag("hud_mic_mute_button"),
      ) {
        Icon(
          imageVector = if (uiState.isMicMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
          contentDescription = if (uiState.isMicMuted) "Unmute microphone" else "Mute microphone",
          tint = micAccent,
          modifier = Modifier.size(btnIconSize),
        )
      }

      // 3. Player Name Pill with Live Speaking Indicator Ring
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 5.dp),
        modifier =
          Modifier.clip(RoundedCornerShape(if (compact) 12.dp else 18.dp))
            .background(
              if (localSpeaking) PalmMint.copy(alpha = 0.22f)
              else ViceSurface.copy(alpha = 0.72f)
            )
            .border(
              width = if (localSpeaking) 1.8.dp else 0.9.dp,
              color =
                if (localSpeaking) PalmMint.copy(alpha = pulseAlpha)
                else Color.White.copy(alpha = 0.18f),
              shape = RoundedCornerShape(if (compact) 12.dp else 18.dp),
            )
            .padding(
              horizontal = ((if (compact) 6f else 9f) * scaleFactor).dp,
              vertical = ((if (compact) 3f else 5f) * scaleFactor).dp,
            )
            .testTag("local_speaker_name_ring"),
      ) {
        Box(
          modifier =
            Modifier.size(((if (compact) 6f else 8f) * scaleFactor).dp)
              .clip(CircleShape)
              .background(
                when {
                  localSpeaking -> PalmMint
                  uiState.isMicMuted -> SunsetCoral
                  uiState.roomConnectedUids.isNotEmpty() || uiState.status == VoiceCallStatus.CONNECTED -> ElectricCyan
                  else -> TextSecondary
                }
              )
        )
        Column {
          Text(
            text = localPlayerName.ifBlank { "YOU" },
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontSize = ((if (compact) 8.5f else 10f) * scaleFactor).coerceIn(6f, 16f).sp,
                fontWeight = FontWeight.ExtraBold,
              ),
            color = if (localSpeaking) PalmMint else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text =
              when {
                uiState.isSpeakerMuted -> "TEAM MUTED"
                localSpeaking -> "SPEAKING"
                uiState.roomConnectedUids.isNotEmpty() -> "CH ${uiState.roomConnectedUids.size + 1} LIVE"
                uiState.status == VoiceCallStatus.CONNECTED -> "VOICE LIVE"
                uiState.isMicMuted -> "MIC OFF"
                else -> "MIC ON"
              },
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontSize = ((if (compact) 7f else 8f) * scaleFactor).coerceIn(5.5f, 13f).sp,
                fontWeight = FontWeight.Bold,
              ),
            color = if (localSpeaking) PalmMint else if (uiState.isSpeakerMuted) SunsetCoral else ElectricCyan,
          )
        }
      }
    }

    // Teammates row with individual speaking indicator rings
    val activeTeammates = squadMembers.filter { it.isRemoteP2P }
    if (activeTeammates.isNotEmpty()) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        activeTeammates.forEach { mate ->
          val mateSpeaking = !uiState.isSpeakerMuted && (mate.id in uiState.speakingPeerIds)
          val mateColor =
            when {
              uiState.isSpeakerMuted -> SunsetCoral
              mateSpeaking -> PalmMint
              else -> ElectricCyan
            }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier =
              Modifier.clip(RoundedCornerShape(if (compact) 8.dp else 12.dp))
                .background(
                  if (mateSpeaking) PalmMint.copy(alpha = 0.24f)
                  else ViceSurface.copy(alpha = 0.78f)
                )
                .border(
                  width = if (mateSpeaking) 1.8.dp else 0.9.dp,
                  color =
                    if (mateSpeaking) PalmMint.copy(alpha = pulseAlpha)
                    else mateColor.copy(alpha = 0.45f),
                  shape = RoundedCornerShape(if (compact) 8.dp else 12.dp),
                )
                .padding(horizontal = if (compact) 5.dp else 7.dp, vertical = if (compact) 2.dp else 3.dp)
                .testTag("teammate_voice_badge_${mate.id}"),
          ) {
            Icon(
              imageVector =
                if (uiState.isSpeakerMuted) Icons.AutoMirrored.Filled.VolumeOff
                else Icons.AutoMirrored.Filled.VolumeUp,
              contentDescription = null,
              tint = mateColor,
              modifier = Modifier.size(if (compact) 9.dp else 11.dp),
            )
            Text(
              text = mate.name,
              style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compact) 7.5.sp else 9.sp, fontWeight = FontWeight.Bold),
              color = if (mateSpeaking) PalmMint else TextPrimary,
              maxLines = 1,
            )
          }
        }
      }
    }
  }
}

@Composable
fun VoiceSquadTogglePill(
  uiState: VoiceChatUiState,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
  scaleFactor: Float = 1f,
) {
  val accent = statusAccentColor(uiState.status)
  val touchMod =
    if (compact || scaleFactor < 0.95f) Modifier.height((28f * scaleFactor).dp)
    else Modifier.minimumInteractiveComponentSize().defaultMinSize(minHeight = (48f * scaleFactor).dp)
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(((if (compact) 3f else 5f) * scaleFactor).dp),
    modifier =
      modifier
        .then(touchMod)
        .clip(RoundedCornerShape(((if (compact) 12f else 16f) * scaleFactor).dp))
        .background(ViceSurface.copy(alpha = 0.84f))
        .border(0.9.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(((if (compact) 12f else 16f) * scaleFactor).dp))
        .clickable(onClick = onClick)
        .padding(horizontal = ((if (compact) 7f else 10f) * scaleFactor).dp, vertical = ((if (compact) 3f else 6f) * scaleFactor).dp)
        .testTag("voice_panel_toggle_button"),
  ) {
    Box(
      modifier =
        Modifier.size(((if (compact) 6f else 8f) * scaleFactor).dp)
          .clip(CircleShape)
          .background(accent)
    )
    Icon(
      imageVector = Icons.Filled.RecordVoiceOver,
      contentDescription = "Toggle voice chat panel",
      tint = accent,
      modifier = Modifier.size(((if (compact) 12f else 16f) * scaleFactor).dp),
    )
    Text(
      text =
        when (uiState.status) {
          VoiceCallStatus.CONNECTED -> "SQUAD LIVE"
          VoiceCallStatus.CONNECTING -> "CALLING"
          else -> "VOICE ID"
        },
      style =
        MaterialTheme.typography.labelSmall.copy(
          fontSize = ((if (compact) 8.5f else 10f) * scaleFactor).coerceIn(6.5f, 15f).sp,
          fontWeight = FontWeight.Bold,
        ),
      color = TextPrimary,
    )
    Icon(
      imageVector = if (uiState.isPanelExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
      contentDescription = null,
      tint = TextSecondary,
      modifier = Modifier.size(((if (compact) 12f else 15f) * scaleFactor).dp),
    )
  }
}

@Composable
fun VoiceChatPanel(
  uiState: VoiceChatUiState,
  onRemotePeerIdChange: (String) -> Unit,
  onConnectVoice: () -> Unit,
  onDisconnectVoice: () -> Unit,
  onToggleMicMute: () -> Unit,
  onCollapse: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val clipboardManager = LocalClipboardManager.current
  val haptics = LocalHapticFeedback.current
  var copiedRecently by remember { mutableStateOf(false) }

  LaunchedEffect(copiedRecently) {
    if (copiedRecently) {
      delay(1800)
      copiedRecently = false
    }
  }

  val statusColor = statusAccentColor(uiState.status)
  val isCallActive =
    uiState.status == VoiceCallStatus.CONNECTED || uiState.status == VoiceCallStatus.CONNECTING

  AnimatedVisibility(
    visible = uiState.isPanelExpanded,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically(),
    modifier = modifier,
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(7.dp),
      modifier =
        Modifier.widthIn(min = 260.dp, max = 332.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(ViceSurfaceSolid.copy(alpha = 0.90f))
          .border(1.2.dp, statusColor.copy(alpha = 0.62f), RoundedCornerShape(16.dp))
          .padding(horizontal = 10.dp, vertical = 8.dp)
          .testTag("voice_chat_panel"),
    ) {
      // Header row: Squad Voice + live status + hide toggle
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.weight(1f),
        ) {
          Box(
            modifier =
              Modifier.size(8.dp)
                .clip(CircleShape)
                .background(statusColor)
          )
          Text(
            text = stringResource(R.string.voice_chat_title),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = statusColor,
          )
          Text(
            text = "· ${uiState.statusDetail}",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 32.dp)
              .clip(RoundedCornerShape(10.dp))
              .clickable(onClick = onCollapse)
              .padding(horizontal = 6.dp)
              .testTag("collapse_voice_panel_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.ExpandLess,
            contentDescription = "Minimize voice panel",
            tint = TextSecondary,
            modifier = Modifier.size(18.dp),
          )
        }
      }

      // 1) My Peer ID display + Copy button
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.28f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
            .padding(start = 9.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.voice_my_peer_id),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = TextSecondary,
          )
          Text(
            text = uiState.myPeerId.ifBlank { "Generating ID..." },
            style =
              MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
              ),
            color = if (uiState.myPeerId.isNotBlank()) ElectricCyan else GoldenHour,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("my_peer_id_text"),
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          modifier =
            Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(
                if (copiedRecently) PalmMint.copy(alpha = 0.22f)
                else ElectricCyan.copy(alpha = 0.16f)
              )
              .border(
                1.dp,
                (if (copiedRecently) PalmMint else ElectricCyan).copy(alpha = 0.65f),
                RoundedCornerShape(8.dp),
              )
              .clickable(enabled = uiState.myPeerId.isNotBlank()) {
                clipboardManager.setText(AnnotatedString(uiState.myPeerId))
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                copiedRecently = true
              }
              .padding(horizontal = 9.dp, vertical = 6.dp)
              .testTag("copy_peer_id_button"),
        ) {
          Icon(
            imageVector = if (copiedRecently) Icons.Filled.Check else Icons.Filled.ContentCopy,
            contentDescription = "Copy My Peer ID",
            tint = if (copiedRecently) PalmMint else ElectricCyan,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text =
              if (copiedRecently) stringResource(R.string.voice_copied)
              else stringResource(R.string.voice_copy),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = if (copiedRecently) PalmMint else ElectricCyan,
          )
        }
      }

      // 2) Input box for friend's Peer ID + 'Connect Voice' button + 3) Mic quick toggle
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Box(
          contentAlignment = Alignment.CenterStart,
          modifier =
            Modifier.weight(1f)
              .height(44.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(Color.Black.copy(alpha = 0.34f))
              .border(1.dp, ElectricCyan.copy(alpha = 0.38f), RoundedCornerShape(10.dp))
              .padding(horizontal = 9.dp),
        ) {
          if (uiState.remotePeerIdInput.isEmpty()) {
            Text(
              text = stringResource(R.string.voice_peer_id_hint),
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
              color = TextSecondary.copy(alpha = 0.7f),
              maxLines = 1,
            )
          }
          BasicTextField(
            value = uiState.remotePeerIdInput,
            onValueChange = onRemotePeerIdChange,
            singleLine = true,
            textStyle =
              MaterialTheme.typography.labelMedium.copy(
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
              ),
            cursorBrush = SolidColor(ElectricCyan),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (!isCallActive) onConnectVoice() }),
            modifier = Modifier.fillMaxWidth().testTag("remote_peer_id_input"),
          )
        }

        val connectColor = if (isCallActive) SunsetCoral else PalmMint
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .defaultMinSize(minHeight = 48.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(connectColor.copy(alpha = 0.22f))
              .border(1.2.dp, connectColor.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
              .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                if (isCallActive) onDisconnectVoice() else onConnectVoice()
              }
              .padding(horizontal = 10.dp, vertical = 8.dp)
              .testTag("connect_voice_button"),
        ) {
          Icon(
            imageVector = if (isCallActive) Icons.Filled.CallEnd else Icons.Filled.Call,
            contentDescription = if (isCallActive) "End voice call" else "Connect Voice",
            tint = connectColor,
            modifier = Modifier.size(15.dp),
          )
          Text(
            text =
              if (isCallActive) stringResource(R.string.voice_disconnect_button)
              else stringResource(R.string.voice_connect_button),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = TextPrimary,
          )
        }

        // Synced compact Mic toggle beside Connect for one-handed drawer control
        val micColor = if (uiState.isMicMuted) SunsetCoral else PalmMint
        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier.size(48.dp)
              .clip(CircleShape)
              .background(micColor.copy(alpha = 0.20f))
              .border(1.2.dp, micColor.copy(alpha = 0.85f), CircleShape)
              .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggleMicMute()
              }
              .testTag("panel_mic_mute_button"),
        ) {
          Icon(
            imageVector = if (uiState.isMicMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
            contentDescription = if (uiState.isMicMuted) "Unmute mic" else "Mute mic",
            tint = micColor,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}

@Composable
fun SquadInvitePopup(
  invite: IncomingSquadInvite,
  onAccept: () -> Unit,
  onDecline: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier =
      modifier
        .widthIn(min = 250.dp, max = 340.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(ViceSurfaceSolid.copy(alpha = 0.96f))
        .border(1.5.dp, PalmMint, RoundedCornerShape(14.dp))
        .padding(horizontal = 12.dp, vertical = 10.dp)
        .testTag("incoming_invite_banner"),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier.size(34.dp)
            .clip(CircleShape)
            .background(PalmMint.copy(alpha = 0.20f))
            .border(1.dp, PalmMint, CircleShape),
      ) {
        Icon(
          imageVector = Icons.Filled.Group,
          contentDescription = "Squad Invite",
          tint = PalmMint,
          modifier = Modifier.size(18.dp),
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "SQUAD LOBBY INVITE",
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold),
          color = PalmMint,
        )
        Text(
          text = "${invite.senderName} (${invite.peerId}) wants to play together",
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = TextPrimary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier =
          Modifier.weight(1f)
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(PalmMint)
            .clickable(onClick = onAccept)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("accept_invite_button"),
      ) {
        Icon(
          imageVector = Icons.Filled.Check,
          contentDescription = "Accept lobby invite",
          tint = ViceSurfaceSolid,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
          text = "ACCEPT",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
          color = ViceSurfaceSolid,
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier =
          Modifier.weight(1f)
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SunsetCoral.copy(alpha = 0.24f))
            .border(1.2.dp, SunsetCoral, RoundedCornerShape(10.dp))
            .clickable(onClick = onDecline)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("decline_invite_button"),
      ) {
        Icon(
          imageVector = Icons.Filled.Close,
          contentDescription = "Decline lobby invite",
          tint = SunsetCoral,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
          text = "DECLINE",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
          color = SunsetCoral,
        )
      }
    }
  }
}

private fun statusAccentColor(status: VoiceCallStatus): Color =
  when (status) {
    VoiceCallStatus.CONNECTED -> PalmMint
    VoiceCallStatus.CONNECTING -> GoldenHour
    VoiceCallStatus.READY -> ElectricCyan
    VoiceCallStatus.ERROR -> SunsetCoral
    VoiceCallStatus.INITIALIZING,
    VoiceCallStatus.DISCONNECTED -> TextSecondary
  }
