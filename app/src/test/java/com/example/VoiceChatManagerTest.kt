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

    // Free Fire default: mic starts muted, speaker starts unmuted
    assertTrue(manager.uiState.value.isMicMuted)
    assertFalse(manager.uiState.value.isSpeakerMuted)

    // Grant mic permission & test Free Fire mic toggle
    manager.onMicPermissionChanged(true)
    assertTrue(manager.uiState.value.hasMicPermission)
    assertTrue(executedScripts.any { it.contains("onPermissionGranted(true)") })

    // Toggle mic ON (false) then OFF (true) then ON (false)
    assertFalse(manager.toggleMicMute())
    assertFalse(manager.uiState.value.isMicMuted)
    assertTrue(executedScripts.last().contains("setMicMuted(false)"))

    assertTrue(manager.toggleMicMute())
    assertTrue(manager.uiState.value.isMicMuted)
    assertTrue(executedScripts.last().contains("setMicMuted(true)"))

    assertFalse(manager.toggleMicMute())
    assertFalse(manager.uiState.value.isMicMuted)

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

    // Granting permission automatically triggers connectToPeer (with Free Fire default mic muted)
    manager.onMicPermissionChanged(true)
    assertFalse(manager.uiState.value.pendingConnectAfterPermission)
    assertEquals(VoiceCallStatus.CONNECTING, manager.uiState.value.status)
    assertTrue(executedScripts.any { it.contains("connectToPeer(\"vh-333444\", true)") })
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

  @Test
  fun squadInviteYesAutoLinksVoiceAndNoDeclinesCleanlyWithoutJoining() {
    val executedScripts = mutableListOf<String>()
    val notices = mutableListOf<String>()
    val manager = VoiceChatManager().apply {
      setJsExecutorForTest { executedScripts.add(it) }
      onSquadNotice = { notices.add(it) }
    }
    manager.handlePeerOpen("vh-100001")
    manager.onMicPermissionChanged(true)

    // 1. Inviter sends invite -> peer NOT in squad until YES is clicked
    val sent = manager.invitePeerToLobby("vh-200002", "VICE HERO", 0, com.example.game.state.WeaponType.ASSAULT_RIFLE)
    assertTrue(sent)
    assertTrue(manager.uiState.value.remoteSquadPeers.isEmpty())
    assertTrue(executedScripts.last().contains("SQUAD_JOIN_REQ"))

    // 2. If friend clicks NO -> inviter receives SQUAD_DECLINE, stays solo, gets notice
    manager.handleSquadMessage("vh-200002", """{"type":"SQUAD_DECLINE","peerId":"vh-200002"}""")
    assertTrue(manager.uiState.value.remoteSquadPeers.isEmpty())
    assertTrue(notices.last().contains("DECLINED"))

    // 3. If friend clicks YES -> inviter receives SQUAD_JOIN_ACK, adds friend & auto-connects voice
    manager.handleSquadMessage(
      "vh-200002",
      """{"type":"SQUAD_JOIN_ACK","peerId":"vh-200002","name":"NEON STRIKER","heroIdx":"2","weapon":"SNIPER_RIFLE","ready":"true"}""",
    )
    assertEquals(1, manager.uiState.value.remoteSquadPeers.size)
    assertEquals("vh-200002", manager.uiState.value.connectedRemoteId)
    assertEquals(VoiceCallStatus.CONNECTING, manager.uiState.value.status)
    assertTrue(executedScripts.last().contains("connectToPeer(\"vh-200002\", true)"))
  }

  @Test
  fun receiverDecliningWithNoSendsDeclineWithoutJoiningSquad() {
    val executedScripts = mutableListOf<String>()
    val manager = VoiceChatManager().apply {
      setJsExecutorForTest { executedScripts.add(it) }
    }
    manager.handlePeerOpen("vh-200002")
    manager.handleSquadMessage(
      "vh-100001",
      """{"type":"SQUAD_JOIN_REQ","peerId":"vh-100001","name":"VICE HERO","heroIdx":"0","weapon":"ASSAULT_RIFLE","ready":"true"}""",
    )
    assertEquals("vh-100001", manager.uiState.value.incomingInvite?.peerId)

    manager.declineIncomingInvite()
    assertNull(manager.uiState.value.incomingInvite)
    assertTrue(manager.uiState.value.remoteSquadPeers.isEmpty())
    assertTrue(executedScripts.last().contains("SQUAD_DECLINE"))
  }

  @Test
  fun turningMicOnInSharedLobbyAutomaticallyConnectsVoice() {
    val executedScripts = mutableListOf<String>()
    val manager = VoiceChatManager().apply {
      setJsExecutorForTest { executedScripts.add(it) }
    }
    manager.handlePeerOpen("vh-200002")
    manager.setMicMuted(true)
    manager.handleSquadMessage(
      "vh-100001",
      """{"type":"SQUAD_JOIN_REQ","peerId":"vh-100001","name":"VICE HERO","heroIdx":"0","weapon":"ASSAULT_RIFLE","ready":"true"}""",
    )
    manager.acceptIncomingInvite("NEON STRIKER", 2, com.example.game.state.WeaponType.SMG_MICRO)
    assertEquals(1, manager.uiState.value.remoteSquadPeers.size)

    // Grant mic permission and turn mic ON -> automatically connects WebRTC voice with lobby partner
    manager.onMicPermissionChanged(true)
    executedScripts.clear()
    val nextMuted = manager.toggleMicMute()
    assertFalse(nextMuted)
    assertEquals(VoiceCallStatus.CONNECTING, manager.uiState.value.status)
    assertTrue(executedScripts.any { it.contains("connectToPeer(\"vh-100001\", false)") })
  }

  @Test
  fun speakerMuteSpeakingActivityAndFirebaseRoomSignalingWorkTogether() {
    val executedScripts = mutableListOf<String>()
    val sentSignals = mutableListOf<Triple<String, String, String>>()
    val manager = VoiceChatManager().apply {
      setJsExecutorForTest { executedScripts.add(it) }
      onSendWebRtcSignal = { to, type, payload -> sentSignals.add(Triple(to, type, payload)) }
    }

    // 1. Free Fire default starts muted; unmuting enables speaking indicator ring
    assertTrue(manager.uiState.value.isMicMuted)
    manager.setMicMuted(false)
    manager.handleLocalVoiceActivity(isSpeaking = true, level = 0.72f)
    assertTrue(manager.uiState.value.isLocalSpeaking)
    assertEquals(0.72f, manager.uiState.value.localVoiceLevel, 0.01f)

    // Muting mic immediately clears local speaking indicator
    manager.toggleMicMute()
    assertTrue(manager.uiState.value.isMicMuted)
    assertFalse(manager.uiState.value.isLocalSpeaking)

    // 2. Remote teammate speaking + Speaker toggle mutes all teammates
    manager.syncRoomAudioPeers(
      localUid = "uid_a",
      memberUids = listOf("uid_a", "uid_b"),
      uidPeerMap = mapOf("uid_b" to "vh-222333"),
    )
    assertTrue(executedScripts.any { it.contains("syncRoomPeers(\"uid_a\", \"uid_b\"") })

    manager.handleRemoteVoiceActivity("uid_b", true)
    assertTrue("uid_b" in manager.uiState.value.speakingPeerIds)
    assertTrue("vh-222333" in manager.uiState.value.speakingPeerIds)

    assertTrue(manager.toggleSpeakerMute())
    assertTrue(manager.uiState.value.isSpeakerMuted)
    assertTrue(manager.uiState.value.speakingPeerIds.isEmpty())
    assertTrue(executedScripts.last().contains("setSpeakerMuted(true)"))

    assertFalse(manager.toggleSpeakerMute())
    assertFalse(manager.uiState.value.isSpeakerMuted)
    assertTrue(executedScripts.last().contains("setSpeakerMuted(false)"))

    // 3. Incoming WebRTC signal & room audio connected update UI state
    manager.receiveWebRtcSignal("uid_b", "OFFER", """{"type":"offer","sdp":"v=0"}""")
    assertTrue(executedScripts.last().contains("handleRoomSignal(\"uid_b\", \"OFFER\""))

    manager.handleRoomVoiceConnected("uid_b")
    assertEquals(VoiceCallStatus.CONNECTED, manager.uiState.value.status)
    assertTrue("uid_b" in manager.uiState.value.roomConnectedUids)
    assertTrue(sentSignals.isEmpty())

    // 4. Socket.IO room signaling bridge events
    val html = VoiceChatManager.buildPeerJsHtml()
    assertTrue(html.contains("join-voice-room"))
    assertTrue(html.contains("user-connected"))
    assertTrue(html.contains("user-disconnected"))
    manager.connectSocketSignaling("https://voice.example.com", "squad-room-1")
    assertTrue(executedScripts.last().contains("joinSocketRoom(\"https://voice.example.com\", \"squad-room-1\""))
    manager.disconnectSocketSignaling()
    assertTrue(executedScripts.last().contains("leaveSocketRoom()"))
  }
}
