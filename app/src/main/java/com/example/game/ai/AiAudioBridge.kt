package com.example.game.ai

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.io.File
import java.util.Locale
import java.util.UUID

private const val TAG = "AiAudioBridge"

/**
 * Records short AAC (.m4a) voice questions into app cache so Gemini 3.8 Flash
 * can listen directly in ANY spoken language without offline speech packs.
 */
class AiAudioRecorder {
  private var recorder: MediaRecorder? = null
  private var activeFile: File? = null

  @Suppress("DEPRECATION")
  fun startRecording(context: Context) {
    stopAndDiscard()
    val outputFile = File(context.cacheDir, "ai_voice_${System.currentTimeMillis()}.m4a")
    val mediaRecorder =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context.applicationContext)
      } else {
        MediaRecorder()
      }
    try {
      mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
      mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
      mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
      mediaRecorder.setAudioSamplingRate(16_000)
      mediaRecorder.setAudioEncodingBitRate(64_000)
      mediaRecorder.setOutputFile(outputFile.absolutePath)
      mediaRecorder.prepare()
      mediaRecorder.start()
      recorder = mediaRecorder
      activeFile = outputFile
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start MediaRecorder for AI voice input", e)
      runCatching { mediaRecorder.release() }
      runCatching { outputFile.delete() }
      recorder = null
      activeFile = null
      throw e
    }
  }

  fun stopAndReadBytes(): ByteArray {
    val activeRecorder = recorder ?: throw IllegalStateException("Microphone recorder is not active.")
    val file = activeFile ?: throw IllegalStateException("Audio recording file missing.")
    return try {
      runCatching { activeRecorder.stop() }.onFailure { err ->
        Log.e(TAG, "MediaRecorder.stop failed", err)
      }
      runCatching { activeRecorder.release() }
      if (!file.exists()) {
        ByteArray(0)
      } else {
        file.readBytes()
      }
    } finally {
      recorder = null
      activeFile = null
      runCatching { file.delete() }
    }
  }

  fun stopAndDiscard() {
    val activeRecorder = recorder
    val file = activeFile
    recorder = null
    activeFile = null
    if (activeRecorder != null) {
      runCatching { activeRecorder.stop() }
      runCatching { activeRecorder.release() }
    }
    if (file != null) {
      runCatching { file.delete() }
    }
  }
}

/**
 * Speaks Gemini replies aloud using Android TextToSpeech dynamically matched
 * to the detected BCP-47 language tag (e.g., hi-IN, en-US, es-ES).
 */
class AiSpeechSpeaker(
  context: Context,
  private val onSpeakingStateChanged: (Boolean) -> Unit,
) {
  private val appContext = context.applicationContext
  private var isReady = false
  private var pendingText: String? = null
  private var pendingLangTag: String? = null

  private val tts: TextToSpeech =
    TextToSpeech(appContext) { status ->
      if (status == TextToSpeech.SUCCESS) {
        isReady = true
        val queuedText = pendingText
        val queuedTag = pendingLangTag
        pendingText = null
        pendingLangTag = null
        if (!queuedText.isNullOrBlank()) {
          speak(queuedText, queuedTag ?: "en-US")
        }
      } else {
        Log.e(TAG, "TextToSpeech engine initialization failed with status=$status")
      }
    }.apply {
      setOnUtteranceProgressListener(
        object : UtteranceProgressListener() {
          override fun onStart(utteranceId: String?) {
            onSpeakingStateChanged(true)
          }

          override fun onDone(utteranceId: String?) {
            onSpeakingStateChanged(false)
          }

          @Deprecated("Deprecated in Java")
          override fun onError(utteranceId: String?) {
            onSpeakingStateChanged(false)
          }

          override fun onError(utteranceId: String?, errorCode: Int) {
            Log.e(TAG, "TTS playback error code=$errorCode")
            onSpeakingStateChanged(false)
          }
        }
      )
    }

  fun speak(text: String, languageTag: String) {
    val cleaned = text.trim()
    if (cleaned.isEmpty()) return
    if (!isReady) {
      pendingText = cleaned
      pendingLangTag = languageTag
      return
    }
    try {
      applyBestLocale(languageTag)
      val params = Bundle()
      tts.speak(cleaned, TextToSpeech.QUEUE_FLUSH, params, "ai_${UUID.randomUUID()}")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to speak AI response aloud", e)
      onSpeakingStateChanged(false)
    }
  }

  private fun applyBestLocale(languageTag: String) {
    val primaryLocale = Locale.forLanguageTag(languageTag.ifBlank { "en-US" })
    val primarySupport = tts.isLanguageAvailable(primaryLocale)
    if (primarySupport >= TextToSpeech.LANG_AVAILABLE) {
      tts.language = primaryLocale
      return
    }
    val baseLanguage = primaryLocale.language.ifBlank { "en" }
    val fallbackLocale = Locale(baseLanguage)
    val baseSupport = tts.isLanguageAvailable(fallbackLocale)
    if (baseSupport >= TextToSpeech.LANG_AVAILABLE) {
      tts.language = fallbackLocale
    } else {
      Log.w(TAG, "TTS voice pack unavailable for $languageTag (code=$primarySupport); keeping default.")
    }
  }

  fun stop() {
    pendingText = null
    pendingLangTag = null
    runCatching { tts.stop() }
    onSpeakingStateChanged(false)
  }

  fun shutdown() {
    pendingText = null
    pendingLangTag = null
    runCatching {
      tts.stop()
      tts.shutdown()
    }
    onSpeakingStateChanged(false)
  }
}
