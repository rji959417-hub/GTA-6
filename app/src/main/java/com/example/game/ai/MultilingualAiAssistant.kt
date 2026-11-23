package com.example.game.ai

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "MultilingualAi"
private const val PRIMARY_MODEL_NAME = "gemini-3.8-flash"
private const val FAST_FALLBACK_MODEL_NAME = "gemini-3.5-flash-lite"
private const val INFERENCE_TIMEOUT_MS = 60_000L
private const val STREAM_FIRST_CHUNK_TIMEOUT_MS = 22_000L
private const val MAX_RECORDING_SECONDS = 30

sealed interface AiUiState {
  data object Idle : AiUiState
  data class Listening(val elapsedSeconds: Int) : AiUiState
  data object Loading : AiUiState
  data class Streaming(val text: String, val languageLabel: String) : AiUiState
  data class Success(val text: String, val languageLabel: String) : AiUiState
  data class Error(val message: String) : AiUiState
}

data class AiChatMessage(
  val id: Long,
  val isUser: Boolean,
  val text: String,
  val languageLabel: String? = null,
  val isVoice: Boolean = false,
)

data class AiAssistantState(
  val isOpen: Boolean = false,
  val uiState: AiUiState = AiUiState.Idle,
  val messages: List<AiChatMessage> = emptyList(),
  val draftText: String = "",
  val isTtsEnabled: Boolean = true,
  val isRecording: Boolean = false,
  val recordingSeconds: Int = 0,
  val isSpeaking: Boolean = false,
  val lastLanguageTag: String = "en-US",
  val lastLanguageLabel: String = "Auto",
  val hasMicPermission: Boolean = false,
)

private val SYSTEM_PROMPT =
  """
  You are NOVA, the built-in smart voice & chat AI companion inside the 3D open-world game Vice Horizon.
  You can answer BOTH real-world general knowledge questions AND Vice Horizon gameplay questions accurately.
  Vice Horizon context if asked: players explore Key Bayside and Emerald Uptown, fly/skydive from the airdrop plane, jump/vault/climb buildings and rooftops, drive sports cars and boats, buy weapons (Fists, 9mm Pistol, Micro SMG, Shotgun, Assault Rifle, Sniper Rifle) from Vice Arms, recruit AI buddies or invite friends by Peer ID in the 3D Squad Lobby, and talk over PeerJS voice chat.

  CRITICAL MULTILINGUAL RULE:
  1. Detect the EXACT language and conversational style spoken or written by the user (e.g., Hindi in Devanagari, Hinglish in Roman script, English, Bengali, Marathi, Tamil, Telugu, Urdu, Spanish, French, Arabic, etc.).
  2. ALWAYS reply accurately, helpfully, and naturally in THAT EXACT SAME language and script. Never switch to another language unless the user explicitly asks you to translate.
  3. Keep answers clear, concise, direct, and factual (typically 1-4 short sentences unless detail is requested).

  REQUIRED OUTPUT FORMAT:
  - Always start line 1 with: [lang:<BCP-47 tag>|<Language Name>]
    Examples: [lang:hi-IN|Hindi], [lang:en-US|English], [lang:hi-IN|Hinglish], [lang:es-ES|Spanish], [lang:mr-IN|Marathi]
  - If the input is an audio recording, include line 2 as: [heard:<short accurate transcript of what the user asked in their language>]
  - After those header tags, write your clean answer directly in the user's language (no markdown code fences or extra boilerplate).
  """.trimIndent()

class MultilingualAiAssistant(
  private val scope: CoroutineScope,
) {
  private val aiBackend by lazy {
    Firebase.ai(backend = GenerativeBackend.googleAI())
  }

  private val fastConfig by lazy {
    generationConfig {
      temperature = 0.4f
      maxOutputTokens = 512
    }
  }

  private val requestOptions by lazy {
    RequestOptions(timeoutInMillis = 35_000L)
  }

  // Initialized lazily on first inference so Firebase.ai never runs eagerly at construction time.
  private val model by lazy {
    aiBackend.generativeModel(
      modelName = PRIMARY_MODEL_NAME,
      generationConfig = fastConfig,
      requestOptions = requestOptions,
      systemInstruction = content { text(SYSTEM_PROMPT) },
    )
  }

  private val fastFallbackModel by lazy {
    aiBackend.generativeModel(
      modelName = FAST_FALLBACK_MODEL_NAME,
      generationConfig = fastConfig,
      requestOptions = requestOptions,
      systemInstruction = content { text(SYSTEM_PROMPT) },
    )
  }

  private val audioRecorder = AiAudioRecorder()
  private val conversationHistory = mutableListOf<Content>()
  private var appContext: Context? = null
  private var speechSpeaker: AiSpeechSpeaker? = null
  private var recordingTimerJob: Job? = null
  private var activeInferenceJob: Job? = null
  private var nextMessageId = 1L

  private val _state = MutableStateFlow(AiAssistantState())
  val state: StateFlow<AiAssistantState> = _state.asStateFlow()

  fun attachContext(context: Context) {
    if (appContext == null) {
      appContext = context.applicationContext
    }
  }

  private fun ensureSpeaker(): AiSpeechSpeaker? {
    val ctx = appContext ?: return null
    if (speechSpeaker == null) {
      speechSpeaker =
        AiSpeechSpeaker(ctx) { speaking ->
          _state.update { it.copy(isSpeaking = speaking) }
        }
    }
    return speechSpeaker
  }

  fun openPanel() {
    ensureSpeaker()
    _state.update { it.copy(isOpen = true) }
  }

  fun closePanel() {
    if (_state.value.isRecording) {
      cancelVoiceRecording()
    }
    speechSpeaker?.stop()
    _state.update { it.copy(isOpen = false) }
  }

  fun updateDraftText(value: String) {
    _state.update { it.copy(draftText = value) }
  }

  fun onMicPermissionChanged(granted: Boolean) {
    _state.update { it.copy(hasMicPermission = granted) }
  }

  fun toggleTtsEnabled() {
    val nextEnabled = !_state.value.isTtsEnabled
    if (!nextEnabled) {
      speechSpeaker?.stop()
    }
    _state.update { it.copy(isTtsEnabled = nextEnabled) }
  }

  fun stopSpeaking() {
    speechSpeaker?.stop()
  }

  fun clearConversation() {
    activeInferenceJob?.cancel()
    speechSpeaker?.stop()
    conversationHistory.clear()
    _state.update {
      it.copy(
        messages = emptyList(),
        uiState = AiUiState.Idle,
        lastLanguageLabel = "Auto",
      )
    }
  }

  fun startVoiceRecording() {
    val context = appContext
    if (context == null) {
      _state.update { it.copy(uiState = AiUiState.Error("Audio subsystem not ready yet.")) }
      return
    }
    if (!_state.value.hasMicPermission) {
      _state.update {
        it.copy(uiState = AiUiState.Error("Microphone permission is required to speak to AI."))
      }
      return
    }
    speechSpeaker?.stop()
    activeInferenceJob?.cancel()

    try {
      audioRecorder.startRecording(context)
      _state.update {
        it.copy(
          isRecording = true,
          recordingSeconds = 0,
          uiState = AiUiState.Listening(elapsedSeconds = 0),
        )
      }
      recordingTimerJob?.cancel()
      recordingTimerJob =
        scope.launch {
          var elapsed = 0
          while (elapsed < MAX_RECORDING_SECONDS && _state.value.isRecording) {
            delay(1_000L)
            if (!_state.value.isRecording) break
            elapsed += 1
            _state.update {
              it.copy(
                recordingSeconds = elapsed,
                uiState = AiUiState.Listening(elapsedSeconds = elapsed),
              )
            }
          }
          if (_state.value.isRecording) {
            stopRecordingAndSendVoiceQuestion()
          }
        }
    } catch (e: Exception) {
      Log.e(TAG, "Unable to start AI microphone recording", e)
      _state.update {
        it.copy(
          isRecording = false,
          recordingSeconds = 0,
          uiState = AiUiState.Error(e.localizedMessage ?: "Failed to access microphone."),
        )
      }
    }
  }

  fun cancelVoiceRecording() {
    recordingTimerJob?.cancel()
    recordingTimerJob = null
    audioRecorder.stopAndDiscard()
    _state.update {
      it.copy(
        isRecording = false,
        recordingSeconds = 0,
        uiState = AiUiState.Idle,
      )
    }
  }

  fun stopRecordingAndSendVoiceQuestion() {
    recordingTimerJob?.cancel()
    recordingTimerJob = null
    if (!_state.value.isRecording) return

    val audioBytes =
      try {
        audioRecorder.stopAndReadBytes()
      } catch (e: Exception) {
        Log.e(TAG, "Failed reading recorded voice clip", e)
        _state.update {
          it.copy(
            isRecording = false,
            recordingSeconds = 0,
            uiState = AiUiState.Error(e.localizedMessage ?: "Failed to read recorded audio."),
          )
        }
        return
      }

    _state.update { it.copy(isRecording = false, recordingSeconds = 0) }

    val payloadError = AiResponseParser.validateAudioPayload(audioBytes)
    if (payloadError != null) {
      _state.update { it.copy(uiState = AiUiState.Error(payloadError)) }
      return
    }

    val userMessageId = nextMessageId++
    _state.update { stateNow ->
      stateNow.copy(
        messages =
          stateNow.messages +
            AiChatMessage(
              id = userMessageId,
              isUser = true,
              text = "Voice question sent (${(audioBytes.size / 1024).coerceAtLeast(1)} KB)",
              isVoice = true,
            ),
      )
    }

    val audioPrompt =
      content("user") {
        inlineData(audioBytes, "audio/mp4")
        text(
          "Listen to the spoken question in this audio clip. Detect the exact spoken language/style and reply accurately in that exact same language using [lang:tag|Name] and [heard:transcript] headers.",
        )
      }

    launchStreamingInference(
      requestContent = audioPrompt,
      historyLabelForUserTurn = null,
      voicePlaceholderMessageId = userMessageId,
    )
  }

  fun sendTextQuestion(explicitPrompt: String? = null) {
    val rawInput = (explicitPrompt ?: _state.value.draftText).trim()
    if (rawInput.isEmpty()) return

    if (explicitPrompt == null) {
      _state.update { it.copy(draftText = "") }
    }
    if (_state.value.isRecording) {
      cancelVoiceRecording()
    }
    speechSpeaker?.stop()

    val userMessageId = nextMessageId++
    _state.update { stateNow ->
      stateNow.copy(
        messages =
          stateNow.messages +
            AiChatMessage(
              id = userMessageId,
              isUser = true,
              text = rawInput,
              isVoice = false,
            ),
      )
    }

    val promptContent = content("user") { text(rawInput) }
    launchStreamingInference(
      requestContent = promptContent,
      historyLabelForUserTurn = rawInput,
      voicePlaceholderMessageId = null,
    )
  }

  private fun launchStreamingInference(
    requestContent: Content,
    historyLabelForUserTurn: String?,
    voicePlaceholderMessageId: Long?,
  ) {
    activeInferenceJob?.cancel()
    val assistantMessageId = nextMessageId++

    activeInferenceJob =
      scope.launch {
        _state.update {
          it.copy(
            uiState = AiUiState.Loading,
            messages =
              it.messages +
                AiChatMessage(
                  id = assistantMessageId,
                  isUser = false,
                  text = "",
                  languageLabel = null,
                ),
          )
        }

        val accumulatedRaw = StringBuilder()

        try {
          val turnInputs: List<Content> = conversationHistory + requestContent

          withTimeout(INFERENCE_TIMEOUT_MS) {
            var streamStalled = false
            try {
              withTimeout(STREAM_FIRST_CHUNK_TIMEOUT_MS) {
                var gotFirstChunk = false
                model.generateContentStream(turnInputs)
                  .flowOn(Dispatchers.IO)
                  .collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                      gotFirstChunk = true
                      accumulatedRaw.append(chunkText)
                      val parsed = AiResponseParser.parse(accumulatedRaw.toString())
                      applyPartialUpdate(
                        parsed = parsed,
                        assistantMessageId = assistantMessageId,
                        voicePlaceholderMessageId = voicePlaceholderMessageId,
                      )
                    }
                  }
                if (!gotFirstChunk && accumulatedRaw.isBlank()) {
                  streamStalled = true
                }
              }
            } catch (firstChunkTimeout: TimeoutCancellationException) {
              if (accumulatedRaw.isBlank()) {
                Log.w(TAG, "Primary stream stalled waiting for first chunk; falling back to unary fast model.")
                streamStalled = true
              } else {
                // Already received partial streamed text; let outer scope finalize what arrived.
              }
            }

            if (streamStalled && accumulatedRaw.isBlank()) {
              val unaryResponse =
                withContext(Dispatchers.IO) {
                  fastFallbackModel.generateContent(turnInputs)
                }
              val unaryText = unaryResponse.text.orEmpty()
              if (unaryText.isNotEmpty()) {
                accumulatedRaw.append(unaryText)
              }
            }
          }

          val finalParsed = AiResponseParser.parse(accumulatedRaw.toString())
          if (finalParsed.replyText.isBlank()) {
            _state.update { cur ->
              cur.copy(
                messages = cur.messages.filterNot { it.id == assistantMessageId },
                uiState = AiUiState.Error("AI returned an empty response. Please try again."),
              )
            }
            return@launch
          }

          val historyUserText =
            historyLabelForUserTurn
              ?: finalParsed.heardTranscript
              ?: "(Voice question in ${finalParsed.languageLabel})"
          conversationHistory.add(content("user") { text(historyUserText) })
          conversationHistory.add(content("model") { text(accumulatedRaw.toString()) })
          while (conversationHistory.size > 12) {
            conversationHistory.removeAt(0)
          }

          _state.update { cur ->
            val updatedMessages =
              cur.messages.map { msg ->
                if (msg.id == voicePlaceholderMessageId && !finalParsed.heardTranscript.isNullOrBlank()) {
                  msg.copy(
                    text = "\"${finalParsed.heardTranscript}\"",
                    languageLabel = finalParsed.languageLabel,
                  )
                } else if (msg.id == assistantMessageId) {
                  msg.copy(
                    text = finalParsed.replyText,
                    languageLabel = finalParsed.languageLabel,
                  )
                } else {
                  msg
                }
              }
            cur.copy(
              uiState = AiUiState.Success(finalParsed.replyText, finalParsed.languageLabel),
              messages = updatedMessages,
              lastLanguageTag = finalParsed.languageTag,
              lastLanguageLabel = finalParsed.languageLabel,
            )
          }

          if (_state.value.isTtsEnabled) {
            ensureSpeaker()?.speak(finalParsed.replyText, finalParsed.languageTag)
          }
        } catch (e: TimeoutCancellationException) {
          Log.e(TAG, "Firebase AI inference timed out after ${INFERENCE_TIMEOUT_MS / 1000}s", e)
          _state.update { cur ->
            cur.copy(
              messages = cur.messages.filterNot { it.id == assistantMessageId && it.text.isBlank() },
              uiState = AiUiState.Error("Request timed out after 60 seconds. Check connection and retry."),
            )
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.e(TAG, "Firebase AI inference failed", e)
          _state.update { cur ->
            cur.copy(
              messages = cur.messages.filterNot { it.id == assistantMessageId && it.text.isBlank() },
              uiState = AiUiState.Error(e.localizedMessage ?: "AI request failed."),
            )
          }
        }
      }
  }

  private fun applyPartialUpdate(
    parsed: ParsedAiReply,
    assistantMessageId: Long,
    voicePlaceholderMessageId: Long?,
  ) {
    _state.update { cur ->
      val updatedMessages =
        cur.messages.map { msg ->
          if (msg.id == voicePlaceholderMessageId && !parsed.heardTranscript.isNullOrBlank()) {
            msg.copy(text = "\"${parsed.heardTranscript}\"")
          } else if (msg.id == assistantMessageId) {
            msg.copy(
              text = parsed.replyText,
              languageLabel = parsed.languageLabel.takeIf { it != "Auto" },
            )
          } else {
            msg
          }
        }
      val nextUi =
        if (!parsed.headerPending && parsed.replyText.isNotEmpty()) {
          AiUiState.Streaming(parsed.replyText, parsed.languageLabel)
        } else {
          AiUiState.Loading
        }
      cur.copy(
        uiState = nextUi,
        messages = updatedMessages,
        lastLanguageTag = parsed.languageTag,
        lastLanguageLabel = parsed.languageLabel,
      )
    }
  }

  fun shutdown() {
    recordingTimerJob?.cancel()
    activeInferenceJob?.cancel()
    audioRecorder.stopAndDiscard()
    speechSpeaker?.shutdown()
    speechSpeaker = null
  }
}
