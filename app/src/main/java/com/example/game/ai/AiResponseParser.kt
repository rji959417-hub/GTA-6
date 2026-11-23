package com.example.game.ai

/**
 * Parsed metadata + visible reply extracted from a Gemini multilingual response.
 * Supports progressive streaming so partial `[lang:...]` or `[heard:...]` headers
 * are never flashed in the visible chat bubble before closing `]`.
 */
data class ParsedAiReply(
  val languageTag: String,
  val languageLabel: String,
  val heardTranscript: String?,
  val replyText: String,
  val headerPending: Boolean = false,
)

object AiResponseParser {
  private val LANG_TAG_REGEX = Regex("""^\[lang:([^|\]]+)\|([^\]]+)\]""")
  private val HEARD_TAG_REGEX = Regex("""^\[heard:([^\]]+)\]""")

  fun parse(rawText: String): ParsedAiReply {
    var remaining = rawText.trimStart()
    var langTag = "en-US"
    var langLabel = "Auto"
    var heard: String? = null

    if (remaining.startsWith("[") && !remaining.contains("]")) {
      return ParsedAiReply(
        languageTag = langTag,
        languageLabel = langLabel,
        heardTranscript = null,
        replyText = "",
        headerPending = true,
      )
    }

    val langMatch = LANG_TAG_REGEX.find(remaining)
    if (langMatch != null) {
      langTag = langMatch.groupValues[1].trim().ifBlank { "en-US" }
      langLabel = langMatch.groupValues[2].trim().ifBlank { langTag }
      remaining = remaining.removeRange(langMatch.range).trimStart()
    }

    if (remaining.startsWith("[") && !remaining.contains("]")) {
      return ParsedAiReply(
        languageTag = langTag,
        languageLabel = langLabel,
        heardTranscript = null,
        replyText = "",
        headerPending = true,
      )
    }

    val heardMatch = HEARD_TAG_REGEX.find(remaining)
    if (heardMatch != null) {
      val transcript = heardMatch.groupValues[1].trim()
      if (transcript.isNotEmpty()) {
        heard = transcript
      }
      remaining = remaining.removeRange(heardMatch.range).trimStart()
    }

    return ParsedAiReply(
      languageTag = langTag,
      languageLabel = langLabel,
      heardTranscript = heard,
      replyText = remaining.trim(),
      headerPending = false,
    )
  }

  /**
   * Validates inline audio byte payload stays strictly under Gemini AI Logic's 20 MB limit.
   */
  fun validateAudioPayload(bytes: ByteArray): String? =
    when {
      bytes.isEmpty() -> "Recorded audio is empty. Hold or tap Record, speak clearly, then stop."
      bytes.size > MAX_INLINE_AUDIO_BYTES ->
        "Audio clip is too large (${bytes.size / (1024 * 1024)} MB). Keep questions under 30 seconds."
      else -> null
    }

  const val MAX_INLINE_AUDIO_BYTES = 18 * 1024 * 1024
}
