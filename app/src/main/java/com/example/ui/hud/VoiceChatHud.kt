package com.example.ui.hud

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
) {
  val accent =
    when {
      isMicMuted -> SunsetCoral
      status == VoiceCallStatus.CONNECTED -> PalmMint
      hasMicPermission -> ElectricCyan
      else -> GoldenHour
    }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .defaultMinSize(minHeight = 48.dp)
        .clip(RoundedCornerShape(24.dp))
        .background(
          Brush.horizontalGradient(
            listOf(
              ViceSurfaceSolid.copy(alpha = 0.92f),
              accent.copy(alpha = if (isMicMuted) 0.18f else 0.24f),
            )
          )
        )
        .border(1.5.dp, accent.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
        .clickable(onClick = onClick)
        .padding(start = 6.dp, end = 11.dp, top = 5.dp, bottom = 5.dp)
        .testTag("mic_mute_button"),
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier.size(30.dp)
          .clip(CircleShape)
          .background(accent.copy(alpha = 0.22f))
          .border(1.dp, accent.copy(alpha = 0.9f), CircleShape),
    ) {
      Icon(
        imageVector = if (isMicMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
        contentDescription =
          if (isMicMuted) "Unmute microphone" else "Mute microphone",
        tint = accent,
        modifier = Modifier.size(18.dp),
      )
    }
    Column {
      Text(
        text =
          if (isMicMuted) stringResource(R.string.voice_mic_muted)
          else stringResource(R.string.voice_mic_on),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
        color = accent,
      )
      Text(
        text = if (status == VoiceCallStatus.CONNECTED) "LIVE" else "VOICE",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
        color = TextSecondary,
      )
    }
  }
}

@Composable
fun VoiceSquadTogglePill(
  uiState: VoiceChatUiState,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val accent = statusAccentColor(uiState.status)
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .defaultMinSize(minHeight = 48.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(ViceSurface.copy(alpha = 0.84f))
        .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 6.dp)
        .testTag("voice_panel_toggle_button"),
  ) {
    Box(
      modifier =
        Modifier.size(8.dp)
          .clip(CircleShape)
          .background(accent)
    )
    Icon(
      imageVector = Icons.Filled.RecordVoiceOver,
      contentDescription = "Toggle voice chat panel",
      tint = accent,
      modifier = Modifier.size(16.dp),
    )
    Text(
      text =
        when (uiState.status) {
          VoiceCallStatus.CONNECTED -> "SQUAD LIVE"
          VoiceCallStatus.CONNECTING -> "CALLING"
          else -> "VOICE ID"
        },
      style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
      color = TextPrimary,
    )
    Icon(
      imageVector = if (uiState.isPanelExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
      contentDescription = null,
      tint = TextSecondary,
      modifier = Modifier.size(16.dp),
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

private fun statusAccentColor(status: VoiceCallStatus): Color =
  when (status) {
    VoiceCallStatus.CONNECTED -> PalmMint
    VoiceCallStatus.CONNECTING -> GoldenHour
    VoiceCallStatus.READY -> ElectricCyan
    VoiceCallStatus.ERROR -> SunsetCoral
    VoiceCallStatus.INITIALIZING,
    VoiceCallStatus.DISCONNECTED -> TextSecondary
  }
