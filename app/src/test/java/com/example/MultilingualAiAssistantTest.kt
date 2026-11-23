package com.example

import com.example.game.ai.AiResponseParser
import com.example.game.ai.AiUiState
import com.example.game.ai.MultilingualAiAssistant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MultilingualAiAssistantTest {
  @Test
  fun parserExtractsHindiTagTranscriptAndReply() {
    val raw =
      """
      [lang:hi-IN|Hindi]
      [heard:हम छत से कैसे कूद सकते हैं?]
      छत के किनारे जाकर JUMP बटन दबाएं, खिलाड़ी नीचे कूदकर सुरक्षित लैंड करेगा।
      """.trimIndent()

    val parsed = AiResponseParser.parse(raw)
    assertFalse(parsed.headerPending)
    assertEquals("hi-IN", parsed.languageTag)
    assertEquals("Hindi", parsed.languageLabel)
    assertEquals("हम छत से कैसे कूद सकते हैं?", parsed.heardTranscript)
    assertEquals(
      "छत के किनारे जाकर JUMP बटन दबाएं, खिलाड़ी नीचे कूदकर सुरक्षित लैंड करेगा।",
      parsed.replyText,
    )
  }

  @Test
  fun parserHidesIncompleteStreamingHeaderUntilClosed() {
    val streamingPartial = "[lang:hi-IN"
    val parsed = AiResponseParser.parse(streamingPartial)
    assertTrue(parsed.headerPending)
    assertEquals("", parsed.replyText)
  }

  @Test
  fun parserHandlesHinglishAndPlainRepliesCleanly() {
    val hinglish = "[lang:hi-IN|Hinglish] Rooftop pe chadhne ke liye ledge ke paas CLIMB button dabao."
    val parsed = AiResponseParser.parse(hinglish)
    assertEquals("hi-IN", parsed.languageTag)
    assertEquals("Hinglish", parsed.languageLabel)
    assertNull(parsed.heardTranscript)
    assertEquals("Rooftop pe chadhne ke liye ledge ke paas CLIMB button dabao.", parsed.replyText)

    val plain = AiResponseParser.parse("Drive straight toward Emerald Uptown.")
    assertEquals("en-US", plain.languageTag)
    assertEquals("Drive straight toward Emerald Uptown.", plain.replyText)
  }

  @Test
  fun audioPayloadValidatorRejectsEmptyAndOversizedClips() {
    assertNotNull(AiResponseParser.validateAudioPayload(ByteArray(0)))
    assertNull(AiResponseParser.validateAudioPayload(ByteArray(2048)))
    assertNotNull(
      AiResponseParser.validateAudioPayload(
        ByteArray(AiResponseParser.MAX_INLINE_AUDIO_BYTES + 1),
      ),
    )
  }

  @Test
  fun assistantStateManagesOpenCloseDraftTtsAndMicPermissionError() {
    val scope = TestScope(UnconfinedTestDispatcher())
    val assistant = MultilingualAiAssistant(scope)

    assistant.openPanel()
    assertTrue(assistant.state.value.isOpen)

    assistant.updateDraftText("Mujhe gun shop kahan milegi?")
    assertEquals("Mujhe gun shop kahan milegi?", assistant.state.value.draftText)

    assistant.toggleTtsEnabled()
    assertFalse(assistant.state.value.isTtsEnabled)

    assistant.startVoiceRecording()
    assertTrue(assistant.state.value.uiState is AiUiState.Error)

    assistant.closePanel()
    assertFalse(assistant.state.value.isOpen)
    assistant.shutdown()
  }
}
