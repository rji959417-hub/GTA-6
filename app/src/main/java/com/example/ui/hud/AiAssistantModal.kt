package com.example.ui.hud

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.game.ai.AiAssistantState
import com.example.game.ai.AiChatMessage
import com.example.game.ai.AiUiState
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

private val QUICK_MULTILINGUAL_PROMPTS =
  listOf(
    "हिंदी में बताओ गेम कैसे खेलें?",
    "Rooftop pe climb aur jump kaise kare?",
    "How do I invite friends in Squad Lobby?",
  )

@Composable
fun AiAssistantModal(
  state: AiAssistantState,
  onVoiceRecordToggle: () -> Unit,
  onCancelRecording: () -> Unit,
  onDraftChange: (String) -> Unit,
  onSendText: () -> Unit,
  onQuickPrompt: (String) -> Unit,
  onToggleTts: () -> Unit,
  onClearChat: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val haptics = LocalHapticFeedback.current
  val listState = rememberLazyListState()

  LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text) {
    if (state.messages.isNotEmpty()) {
      listState.animateScrollToItem(state.messages.lastIndex)
    }
  }

  val isBusy = state.uiState is AiUiState.Loading || state.uiState is AiUiState.Streaming
  val micAccent by animateColorAsState(
    targetValue =
      when {
        state.isRecording -> SunsetCoral
        isBusy -> GoldenHour
        else -> PalmMint
      },
    label = "aiMicAccent",
  )

  BoxWithConstraints(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .fillMaxSize()
        .background(ViceVoid.copy(alpha = 0.78f))
        .clickable(onClick = onClose)
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 14.dp, vertical = 10.dp),
  ) {
    val maxCardWidth = if (maxWidth >= 660.dp) 620.dp else 540.dp

    Column(
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier =
        Modifier.widthIn(max = maxCardWidth)
          .fillMaxWidth()
          .clip(RoundedCornerShape(20.dp))
          .background(ViceSurfaceSolid.copy(alpha = 0.96f))
          .border(1.5.dp, ElectricCyan.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
          .clickable(enabled = false) {}
          .padding(14.dp)
          .testTag("ai_assistant_modal"),
    ) {
      // 1. Header Row: Brand + Language Pill + TTS Toggle + Clear + Close
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f),
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(ElectricCyan, NeonPink))),
          ) {
            Icon(
              imageVector = Icons.Filled.AutoAwesome,
              contentDescription = "Nova AI Assistant",
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
                text = stringResource(R.string.ai_assistant_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = TextPrimary,
              )
              Box(
                modifier =
                  Modifier.clip(RoundedCornerShape(50))
                    .background(GoldenHour.copy(alpha = 0.18f))
                    .border(1.dp, GoldenHour.copy(alpha = 0.65f), RoundedCornerShape(50))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
                    .testTag("ai_language_badge"),
              ) {
                Text(
                  text = state.lastLanguageLabel.uppercase(),
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                  color = GoldenHour,
                )
              }
            }
            Text(
              text = stringResource(R.string.ai_assistant_subtitle),
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
              color = TextSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          // Speak Replies Toggle (TTS)
          val ttsAccent = if (state.isTtsEnabled) ElectricCyan else TextSecondary
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .defaultMinSize(minHeight = 44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ttsAccent.copy(alpha = 0.14f))
                .border(1.dp, ttsAccent.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                .clickable(onClick = onToggleTts)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag("ai_tts_toggle_button"),
          ) {
            Icon(
              imageVector =
                if (state.isTtsEnabled) Icons.AutoMirrored.Filled.VolumeUp
                else Icons.AutoMirrored.Filled.VolumeOff,
              contentDescription = if (state.isTtsEnabled) "Mute AI voice" else "Enable AI voice",
              tint = ttsAccent,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text =
                if (state.isSpeaking) "SPEAKING"
                else if (state.isTtsEnabled) stringResource(R.string.ai_tts_on)
                else stringResource(R.string.ai_tts_off),
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
              color = ttsAccent,
            )
          }

          IconButton(
            onClick = onClearChat,
            modifier = Modifier.size(48.dp).testTag("ai_clear_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.DeleteOutline,
              contentDescription = "Clear AI conversation",
              tint = TextSecondary,
            )
          }

          IconButton(
            onClick = onClose,
            modifier = Modifier.size(48.dp).testTag("ai_close_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = "Close AI Assistant",
              tint = TextPrimary,
            )
          }
        }
      }

      // 2. Chat Feed (Conversation + Quick Multilingual Chips when empty)
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .weight(1f, fill = true)
            .clip(RoundedCornerShape(14.dp))
            .background(ViceVoid.copy(alpha = 0.72f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(8.dp),
      ) {
        if (state.messages.isEmpty()) {
          Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(12.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.GraphicEq,
              contentDescription = null,
              tint = ElectricCyan,
              modifier = Modifier.size(32.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Bolkar ya likhkar poochho — AI usi language mein jawab dega!",
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
              color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Hindi · Hinglish · English · Marathi · Tamil · Spanish · Any Language",
              style = MaterialTheme.typography.labelSmall,
              color = ElectricCyan,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
              QUICK_MULTILINGUAL_PROMPTS.forEachIndexed { idx, prompt ->
                Box(
                  contentAlignment = Alignment.Center,
                  modifier =
                    Modifier.defaultMinSize(minHeight = 40.dp)
                      .clip(RoundedCornerShape(50))
                      .background(ViceSurface.copy(alpha = 0.9f))
                      .border(1.dp, NeonPink.copy(alpha = 0.55f), RoundedCornerShape(50))
                      .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onQuickPrompt(prompt)
                      }
                      .padding(horizontal = 12.dp, vertical = 6.dp)
                      .testTag("ai_quick_prompt_$idx"),
                ) {
                  Text(
                    text = prompt,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                  )
                }
              }
            }
          }
        } else {
          LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            modifier = Modifier.fillMaxSize().testTag("ai_chat_list"),
          ) {
            items(state.messages, key = { it.id }) { msg ->
              AiMessageBubble(
                message = msg,
                isStreamingLast = isBusy && msg.id == state.messages.lastOrNull()?.id && !msg.isUser,
              )
            }
          }
        }
      }

      // 3. Real Error Banner if Firebase AI / Mic fails
      if (state.uiState is AiUiState.Error) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(SunsetCoral.copy(alpha = 0.18f))
              .border(1.dp, SunsetCoral.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
              .padding(horizontal = 10.dp, vertical = 7.dp)
              .testTag("ai_error_banner"),
        ) {
          Text(
            text = state.uiState.message,
            style = MaterialTheme.typography.labelSmall,
            color = SunsetCoral,
            modifier = Modifier.weight(1f),
          )
        }
      }

      // 4. Bottom Voice Record + Text Question Input Dock
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        // Voice Tap Button (Start / Stop & Send)
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .defaultMinSize(minHeight = 48.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(micAccent.copy(alpha = if (state.isRecording) 0.28f else 0.18f))
              .border(1.5.dp, micAccent, RoundedCornerShape(12.dp))
              .clickable(enabled = !isBusy) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onVoiceRecordToggle()
              }
              .padding(horizontal = 12.dp, vertical = 8.dp)
              .testTag("ai_voice_record_button"),
        ) {
          if (isBusy) {
            CircularProgressIndicator(
              color = GoldenHour,
              strokeWidth = 2.dp,
              modifier = Modifier.size(16.dp),
            )
          } else {
            Icon(
              imageVector = if (state.isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
              contentDescription = if (state.isRecording) "Stop and send voice question" else "Speak to AI",
              tint = micAccent,
              modifier = Modifier.size(18.dp),
            )
          }
          Text(
            text =
              when {
                state.isRecording ->
                  "${stringResource(R.string.ai_stop_and_ask)} (${state.recordingSeconds}s)"
                isBusy -> "THINKING..."
                else -> stringResource(R.string.ai_tap_to_speak)
              },
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = micAccent,
          )
        }

        if (state.isRecording) {
          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                .clickable(onClick = onCancelRecording)
                .testTag("ai_voice_cancel_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = "Cancel voice recording",
              tint = TextPrimary,
              modifier = Modifier.size(18.dp),
            )
          }
        }

        // Multilingual Text Box
        Box(
          contentAlignment = Alignment.CenterStart,
          modifier =
            Modifier.weight(1f)
              .defaultMinSize(minHeight = 48.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(Color.Black.copy(alpha = 0.36f))
              .border(1.dp, ElectricCyan.copy(alpha = 0.42f), RoundedCornerShape(12.dp))
              .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
          if (state.draftText.isEmpty()) {
            Text(
              text = stringResource(R.string.ai_input_hint),
              style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
              color = TextSecondary.copy(alpha = 0.65f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          BasicTextField(
            value = state.draftText,
            onValueChange = onDraftChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontSize = 13.sp),
            cursorBrush = SolidColor(ElectricCyan),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (!isBusy) onSendText() }),
            modifier = Modifier.fillMaxWidth().testTag("ai_text_input"),
          )
        }

        // Send Text Question Button
        val sendEnabled = state.draftText.isNotBlank() && !isBusy && !state.isRecording
        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier.size(48.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(
                if (sendEnabled) ElectricCyan else Color.White.copy(alpha = 0.10f),
              )
              .clickable(enabled = sendEnabled) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSendText()
              }
              .testTag("ai_send_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = "Send question to AI",
            tint = if (sendEnabled) ViceVoid else TextSecondary.copy(alpha = 0.45f),
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun AiMessageBubble(
  message: AiChatMessage,
  isStreamingLast: Boolean,
) {
  Row(
    horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
      modifier =
        Modifier.widthIn(max = 440.dp)
          .clip(
            RoundedCornerShape(
              topStart = 14.dp,
              topEnd = 14.dp,
              bottomStart = if (message.isUser) 14.dp else 4.dp,
              bottomEnd = if (message.isUser) 4.dp else 14.dp,
            )
          )
          .background(
            if (message.isUser) ElectricCyan.copy(alpha = 0.16f)
            else ViceSurface.copy(alpha = 0.92f),
          )
          .border(
            1.dp,
            if (message.isUser) ElectricCyan.copy(alpha = 0.55f)
            else GoldenHour.copy(alpha = 0.45f),
            RoundedCornerShape(
              topStart = 14.dp,
              topEnd = 14.dp,
              bottomStart = if (message.isUser) 14.dp else 4.dp,
              bottomEnd = if (message.isUser) 4.dp else 14.dp,
            ),
          )
          .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text =
            when {
              message.isUser && message.isVoice -> "YOU (VOICE)"
              message.isUser -> "YOU"
              else -> "NOVA AI"
            },
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
          color = if (message.isUser) ElectricCyan else GoldenHour,
        )
        if (!message.languageLabel.isNullOrBlank()) {
          Text(
            text = "· ${message.languageLabel.uppercase()}",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = PalmMint,
          )
        }
      }

      Spacer(modifier = Modifier.height(3.dp))

      if (message.text.isEmpty() && isStreamingLast) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          CircularProgressIndicator(
            color = GoldenHour,
            strokeWidth = 2.dp,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = "Listening & replying in your language...",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
            color = TextSecondary,
          )
        }
      } else {
        Text(
          text = message.text,
          style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
          color = TextPrimary,
        )
      }
    }
  }
}
