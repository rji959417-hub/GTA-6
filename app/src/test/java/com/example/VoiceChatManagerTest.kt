package com.example

import com.example.game.voice.VoiceCallStatus
import com.example.game.voice.VoiceChatManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceChatManagerTest {
  @Test
  fun generatedHtmlLoadsPeerJsFromCdnWithWebRtcAudioAndMuteControls() {
    val html = VoiceChatManager.buildPeerJsHtml()
    assertTrue(html.contains(VoiceChatManager.PEERJS_CDN_PRIMARY))
    assertTrue(html.contains(VoiceChatManager.PEERJS_CDN_FALLBACK))
    assertTrue(html.contains("navigator.mediaDevices.getUserMedia"))
    assertTrue(html.contains("peer.call(remoteId, stream)"))
    assertTrue(html.contains("peer.on('call'"))
    assertTrue(html.contains("setMicMuted"))
    assertTrue(html.contains("<audio id=\"remoteAudio\" autoplay playsinline>"))
  }

  @Test
  fun peerLifecycleConnectDisconnectAndMicToggleUpdateUiAndBridge() {
    val executedScripts = mutableListOf<String>()
    val manager = VoiceChatManager().apply {
      setJsExecutorForTest { executedScripts.add(it) }
    }

    assertEquals(VoiceCallStatus.INITIALIZING, manager.uiState.value.status)

    // Signaling opens with myPeerId
    manager.handlePeerOpen("vh-482910")
    assertEquals("vh-482910", manager.uiState.value.myPeerId)
    assertEquals(VoiceCallStatus.READY, manager.uiState.value.status)

    // Grant mic permission & test Free Fire mic toggle
    manager.onMicPermissionChanged(true)
    assertTrue(manager.uiState.value.hasMicPermission)
    assertTrue(executedScripts.any { it.contains("onPermissionGranted(false)") })

    assertTrue(manager.toggleMicMute())
    assertTrue(manager.uiState.value.isMicMuted)
    assertTrue(executedScripts.last().contains("setMicMuted(true)"))

    assertFalse(manager.toggleMicMute())
    assertFalse(manager.uiState.value.isMicMuted)
    assertTrue(executedScripts.last().contains("setMicMuted(false)"))

    // Validation: prevent calling own ID or blank ID
    manager.connectVoice()
    assertEquals(VoiceCallStatus.READY, manager.uiState.value.status)
    assertTrue(manager.uiState.value.statusDetail.contains("Enter"))

    manager.updateRemotePeerIdInput("vh-482910")
    manager.connectVoice()
    assertEquals(VoiceCallStatus.ERROR, manager.uiState.value.status)

    // Outgoing call to friend Peer ID
    manager.updateRemotePeerIdInput("  vh-901234 ")
    manager.connectVoice()
    assertEquals(VoiceCallStatus.CONNECTING, manager.uiState.value.status)
    assertEquals("vh-901234", manager.uiState.value.connectedRemoteId)
    assertTrue(executedScripts.last().contains("connectToPeer(\"vh-901234\", false)"))

    // Call connected & ended
    manager.handleCallConnected("vh-901234")
    assertEquals(VoiceCallStatus.CONNECTED, manager.uiState.value.status)

    manager.disconnectVoice()
    assertEquals(VoiceCallStatus.READY, manager.uiState.value.status)
    assertNull(manager.uiState.value.connectedRemoteId)
    assertTrue(executedScripts.last().contains("disconnectCall()"))
  }

  @Test
  fun connectVoiceDeferredUntilMicPermissionGranted() {
    val executedScripts = mutableListOf<String>()
    val manager = VoiceChatManager().apply {
      setJsExecutorForTest { executedScripts.add(it) }
    }
    manager.handlePeerOpen("vh-111222")
    manager.updateRemotePeerIdInput("vh-333444")

    // Attempting connect without mic permission marks pending connect instead of calling JS
    manager.connectVoice()
    assertTrue(manager.uiState.value.pendingConnectAfterPermission)
    assertTrue(executedScripts.isEmpty())

    // Granting permission automatically triggers connectToPeer
    manager.onMicPermissionChanged(true)
    assertFalse(manager.uiState.value.pendingConnectAfterPermission)
    assertEquals(VoiceCallStatus.CONNECTING, manager.uiState.value.status)
    assertTrue(executedScripts.any { it.contains("connectToPeer(\"vh-333444\", false)") })
  }

  @Test
  fun peerJsDataChannelHandlesSquadInvitesMatchStartAndRemotePoses() {
    val executedScripts = mutableListOf<String>()
    var startedZone: String? = null
    val receivedPoses = mutableListOf<com.example.game.state.RemotePlayerPose>()
    val manager = VoiceChatManager().apply {
      setJsExecutorForTest { executedScripts.add(it) }
      onRemoteStartMatch = { zone -> startedZone = zone }
      onRemotePoseReceived = { pose -> receivedPoses.add(pose) }
    }

    manager.handlePeerOpen("vh-100001")

    // Receive incoming P2P squad invite
    manager.handleSquadMessage(
      "vh-200002",
      """{"type":"SQUAD_JOIN_REQ","peerId":"vh-200002","name":"NEON STRIKER","heroIdx":"2","weapon":"ASSAULT_RIFLE","ready":"true"}""",
    )
    val invite = manager.uiState.value.incomingInvite
    assertEquals("vh-200002", invite?.peerId)
    assertEquals("NEON STRIKER", invite?.senderName)

    // Accept invite -> remote player joins squad roster and sends SQUAD_JOIN_ACK
    manager.acceptIncomingInvite("VICE HERO", 0, com.example.game.state.WeaponType.SMG_MICRO)
    assertNull(manager.uiState.value.incomingInvite)
    assertEquals(1, manager.uiState.value.remoteSquadPeers.size)
    assertTrue(executedScripts.last().contains("SQUAD_JOIN_ACK"))

    // Receiving START_MATCH launches remote player at same drop zone
    manager.handleSquadMessage("vh-200002", """{"type":"START_MATCH","dropZone":"GREEN_VALLEY"}""")
    assertEquals("GREEN_VALLEY", startedZone)

    // Receiving PLAYER_STATE updates 3D remote player pose
    manager.handleSquadMessage(
      "vh-200002",
      """{"type":"PLAYER_STATE","peerId":"vh-200002","x":"14.5","y":"1.2","z":"-8.0","yaw":"1.57","spd":"0.9","spr":"true","sit":"false","drop":"GROUND","wpn":"ASSAULT_RIFLE","hero":"2"}""",
    )
    assertEquals(1, receivedPoses.size)
    assertEquals(14.5f, receivedPoses[0].x, 0.01f)
    assertEquals(com.example.game.state.WeaponType.ASSAULT_RIFLE, receivedPoses[0].weapon)
  }
}
