package com.example.game.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.game.state.DropPhase
import com.example.game.state.LobbySquadMember
import com.example.game.state.RemotePlayerPose
import com.example.game.state.WeaponType
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class VoiceCallStatus {
  INITIALIZING,
  READY,
  CONNECTING,
  CONNECTED,
  DISCONNECTED,
  ERROR,
}

data class IncomingSquadInvite(
  val peerId: String,
  val senderName: String,
  val heroIndex: Int,
  val weapon: WeaponType = WeaponType.FISTS,
)

data class VoiceChatUiState(
  val myPeerId: String = "",
  val remotePeerIdInput: String = "",
  val connectedRemoteId: String? = null,
  val status: VoiceCallStatus = VoiceCallStatus.INITIALIZING,
  val statusDetail: String = "Starting voice node...",
  val isMicMuted: Boolean = true,
  val isSpeakerMuted: Boolean = false,
  val isLocalSpeaking: Boolean = false,
  val localVoiceLevel: Float = 0f,
  val speakingPeerIds: Set<String> = emptySet(),
  val roomConnectedUids: Set<String> = emptySet(),
  val hasMicPermission: Boolean = false,
  val isPanelExpanded: Boolean = true,
  val pendingConnectAfterPermission: Boolean = false,
  val remoteSquadPeers: List<LobbySquadMember> = emptyList(),
  val incomingInvite: IncomingSquadInvite? = null,
)

class VoiceChatManager {
  private val mainHandler: Handler? by lazy {
    runCatching { Looper.getMainLooper()?.let { Handler(it) } }.getOrNull()
  }
  private val _uiState = MutableStateFlow(VoiceChatUiState())
  val uiState: StateFlow<VoiceChatUiState> = _uiState.asStateFlow()

  var onRemoteStartMatch: ((String) -> Unit)? = null
  var onRemotePoseReceived: ((RemotePlayerPose) -> Unit)? = null
  var onRemoteSquadChanged: ((List<LobbySquadMember>) -> Unit)? = null
  var onSquadNotice: ((String) -> Unit)? = null
  var onPeerIdOpened: ((String) -> Unit)? = null
  var onSendWebRtcSignal: ((recipientUid: String, type: String, payload: String) -> Unit)? = null

  private val uidToPeerId = LinkedHashMap<String, String>()
  private var activeRoomLocalUid: String = ""
  private var activeRoomRemoteUids: List<String> = emptyList()

  private var appContext: Context? = null
  private var webViewRef: WebView? = null
  private var jsExecutor: ((String) -> Unit)? = null
  private var lastStateBroadcastMs: Long = 0L

  fun attachContext(context: Context) {
    if (appContext == null) {
      appContext = context.applicationContext
    }
  }

  private fun setVoiceSpeakerRouting(active: Boolean) {
    val ctx = appContext ?: return
    runCatching {
      val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
      if (active) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        val hasExternalHeadset = audioManager.isWiredHeadsetOn || audioManager.isBluetoothScoOn || audioManager.isBluetoothA2dpOn
        if (!hasExternalHeadset) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val speaker =
              audioManager.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
              }
            if (speaker != null) {
              audioManager.setCommunicationDevice(speaker)
            }
          }
          @Suppress("DEPRECATION")
          audioManager.isSpeakerphoneOn = true
        }
      } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          audioManager.clearCommunicationDevice()
        }
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = false
        audioManager.mode = AudioManager.MODE_NORMAL
      }
    }
  }

  internal fun setJsExecutorForTest(executor: ((String) -> Unit)?) {
    jsExecutor = executor
  }

  fun updateRemotePeerIdInput(value: String) {
    val sanitized = value.replace("\n", "").replace("\r", "").take(64)
    _uiState.update { it.copy(remotePeerIdInput = sanitized) }
  }

  fun togglePanelExpanded() {
    _uiState.update { it.copy(isPanelExpanded = !it.isPanelExpanded) }
  }

  fun setPanelExpanded(expanded: Boolean) {
    _uiState.update { it.copy(isPanelExpanded = expanded) }
  }

  fun onMicPermissionChanged(granted: Boolean) {
    val wasWaitingToConnect = _uiState.value.pendingConnectAfterPermission
    _uiState.update {
      it.copy(
        hasMicPermission = granted,
        pendingConnectAfterPermission = false,
        statusDetail =
          if (granted && it.status == VoiceCallStatus.ERROR) "Mic ready · Tap Connect Voice"
          else it.statusDetail,
        status =
          if (granted && it.status == VoiceCallStatus.ERROR && it.myPeerId.isNotBlank()) {
            VoiceCallStatus.READY
          } else {
            it.status
          },
      )
    }
    if (granted) {
      evalJs("window.viceVoice && window.viceVoice.onPermissionGranted(${_uiState.value.isMicMuted});")
      if (wasWaitingToConnect && _uiState.value.remotePeerIdInput.isNotBlank()) {
        connectVoice()
      } else if (!_uiState.value.isMicMuted) {
        ensureSquadVoiceConnectedOnMicUnmute()
      }
    } else if (wasWaitingToConnect) {
      _uiState.update {
        it.copy(
          status = VoiceCallStatus.ERROR,
          statusDetail = "Microphone permission needed for voice chat",
        )
      }
    }
  }

  fun toggleMicMute(): Boolean {
    val nextMuted = !_uiState.value.isMicMuted
    _uiState.update {
      it.copy(
        isMicMuted = nextMuted,
        isLocalSpeaking = if (nextMuted) false else it.isLocalSpeaking,
        localVoiceLevel = if (nextMuted) 0f else it.localVoiceLevel,
      )
    }
    evalJs("window.viceVoice && window.viceVoice.setMicMuted($nextMuted);")
    if (!nextMuted) {
      ensureSquadVoiceConnectedOnMicUnmute()
    }
    return nextMuted
  }

  fun setMicMuted(muted: Boolean) {
    _uiState.update {
      it.copy(
        isMicMuted = muted,
        isLocalSpeaking = if (muted) false else it.isLocalSpeaking,
        localVoiceLevel = if (muted) 0f else it.localVoiceLevel,
      )
    }
    evalJs("window.viceVoice && window.viceVoice.setMicMuted($muted);")
    if (!muted) {
      ensureSquadVoiceConnectedOnMicUnmute()
    }
  }

  fun toggleSpeakerMute(): Boolean {
    val nextMuted = !_uiState.value.isSpeakerMuted
    _uiState.update {
      it.copy(
        isSpeakerMuted = nextMuted,
        speakingPeerIds = if (nextMuted) emptySet() else it.speakingPeerIds,
      )
    }
    evalJs("window.viceVoice && window.viceVoice.setSpeakerMuted($nextMuted);")
    return nextMuted
  }

  fun setSpeakerMuted(muted: Boolean) {
    _uiState.update {
      it.copy(
        isSpeakerMuted = muted,
        speakingPeerIds = if (muted) emptySet() else it.speakingPeerIds,
      )
    }
    evalJs("window.viceVoice && window.viceVoice.setSpeakerMuted($muted);")
  }

  fun syncRoomAudioPeers(
    localUid: String,
    memberUids: List<String>,
    uidPeerMap: Map<String, String> = emptyMap(),
  ) {
    activeRoomLocalUid = localUid
    if (uidPeerMap.isNotEmpty()) {
      uidToPeerId.putAll(uidPeerMap)
    }
    val remoteUids = memberUids.filter { it.isNotBlank() && it != localUid }.distinct()
    activeRoomRemoteUids = remoteUids
    val current = _uiState.value
    evalJs(
      "window.viceVoice && window.viceVoice.syncRoomPeers(${quoteJsString(localUid)}, ${quoteJsString(remoteUids.joinToString(","))}, ${current.isMicMuted}, ${current.isSpeakerMuted});"
    )
    if (remoteUids.isEmpty() && current.roomConnectedUids.isNotEmpty()) {
      _uiState.update { it.copy(roomConnectedUids = emptySet()) }
    }
  }

  fun receiveWebRtcSignal(senderUid: String, type: String, payloadJson: String) {
    if (senderUid.isBlank() || type.isBlank() || payloadJson.isBlank()) return
    evalJs(
      "window.viceVoice && window.viceVoice.handleRoomSignal(${quoteJsString(senderUid)}, ${quoteJsString(type)}, ${quoteJsString(payloadJson)});"
    )
  }

  /**
   * Optional Socket.IO WebRTC room signaling bridge compatible with the companion Express + Socket.IO server
   * (`join-voice-room`, `user-connected`, `signal`, `user-disconnected`), complementing Firebase signaling.
   */
  fun connectSocketSignaling(serverUrl: String, roomId: String) {
    val cleanUrl = serverUrl.trim()
    val cleanRoom = roomId.trim()
    if (cleanUrl.isBlank() || cleanRoom.isBlank()) return
    val current = _uiState.value
    evalJs(
      "window.viceVoice && window.viceVoice.joinSocketRoom(${quoteJsString(cleanUrl)}, ${quoteJsString(cleanRoom)}, ${current.isMicMuted}, ${current.isSpeakerMuted});"
    )
  }

  fun disconnectSocketSignaling() {
    evalJs("window.viceVoice && window.viceVoice.leaveSocketRoom();")
  }

  /**
   * When two players are already in the same Free Fire lobby and either player turns their
   * microphone ON, ensure WebRTC audio is actively linked if no call is running yet.
   */
  fun ensureSquadVoiceConnectedOnMicUnmute() {
    val current = _uiState.value
    if (!current.hasMicPermission) return
    val squadPeer = current.remoteSquadPeers.firstOrNull() ?: return
    if (current.status == VoiceCallStatus.CONNECTED || current.status == VoiceCallStatus.CONNECTING) {
      return
    }
    if (current.myPeerId.isBlank()) return
    autoLinkVoiceWithPeer(squadPeer.id)
  }

  private fun autoLinkVoiceWithPeer(remotePeerId: String) {
    val current = _uiState.value
    if (remotePeerId.isBlank() || remotePeerId == current.myPeerId) return
    if (current.connectedRemoteId == remotePeerId &&
      (current.status == VoiceCallStatus.CONNECTED || current.status == VoiceCallStatus.CONNECTING)
    ) {
      return
    }
    _uiState.update {
      it.copy(
        status = VoiceCallStatus.CONNECTING,
        statusDetail = "Linking squad voice with $remotePeerId...",
        connectedRemoteId = remotePeerId,
      )
    }
    val quotedId = quoteJsString(remotePeerId)
    evalJs("window.viceVoice && window.viceVoice.connectToPeer($quotedId, ${current.isMicMuted});")
  }

  fun setStatusDetail(detail: String) {
    _uiState.update { it.copy(statusDetail = detail) }
  }

  fun setCloudIncomingInvite(invite: IncomingSquadInvite?) {
    _uiState.update {
      it.copy(
        incomingInvite = invite,
        statusDetail =
          if (invite != null) "Invite from ${invite.senderName} (${invite.peerId})"
          else it.statusDetail,
      )
    }
  }

  fun syncCloudSquadPeers(
    peers: List<LobbySquadMember>,
    isHost: Boolean,
    localName: String,
    heroIndex: Int,
    weapon: WeaponType,
  ) {
    val firstLivePeerId = peers.firstOrNull { it.id.startsWith("vh-") }?.id
    _uiState.update {
      it.copy(
        remoteSquadPeers = peers,
        remotePeerIdInput = firstLivePeerId ?: it.remotePeerIdInput,
        statusDetail =
          when {
            it.status == VoiceCallStatus.CONNECTED -> it.statusDetail
            peers.isNotEmpty() -> "${peers.first().name} in Lobby · Turn Mic ON to talk!"
            else -> it.statusDetail
          },
      )
    }
    onRemoteSquadChanged?.invoke(peers)
    if (firstLivePeerId != null && _uiState.value.myPeerId.isNotBlank()) {
      val payload =
        buildJsonMap(
          "type" to "LOBBY_SYNC",
          "peerId" to _uiState.value.myPeerId,
          "name" to localName,
          "heroIdx" to heroIndex.toString(),
          "weapon" to weapon.name,
          "ready" to "true",
        )
      evalJs("window.viceVoice && window.viceVoice.sendToPeer(${quoteJsString(firstLivePeerId)}, ${quoteJsString(payload)});")
      if (isHost && _uiState.value.hasMicPermission &&
        _uiState.value.status != VoiceCallStatus.CONNECTED &&
        _uiState.value.status != VoiceCallStatus.CONNECTING
      ) {
        autoLinkVoiceWithPeer(firstLivePeerId)
      }
    }
  }

  fun markPendingConnectForPermission() {
    _uiState.update {
      it.copy(
        pendingConnectAfterPermission = true,
        statusDetail = "Allow microphone to connect voice",
      )
    }
  }

  fun connectVoice() {
    val current = _uiState.value
    val targetId = current.remotePeerIdInput.trim()
    if (targetId.isEmpty()) {
      _uiState.update {
        it.copy(
          status = if (it.myPeerId.isNotBlank()) VoiceCallStatus.READY else it.status,
          statusDetail = "Enter a friend's Peer ID first",
        )
      }
      return
    }
    if (current.myPeerId.isNotBlank() && targetId == current.myPeerId) {
      _uiState.update {
        it.copy(
          status = VoiceCallStatus.ERROR,
          statusDetail = "Cannot connect to your own Peer ID",
        )
      }
      return
    }
    if (!current.hasMicPermission) {
      markPendingConnectForPermission()
      return
    }

    _uiState.update {
      it.copy(
        status = VoiceCallStatus.CONNECTING,
        statusDetail = "Calling $targetId...",
        connectedRemoteId = targetId,
      )
    }
    val quotedId = quoteJsString(targetId)
    evalJs("window.viceVoice && window.viceVoice.connectToPeer($quotedId, ${current.isMicMuted});")
  }

  fun disconnectVoice() {
    evalJs("window.viceVoice && window.viceVoice.disconnectCall();")
    _uiState.update {
      it.copy(
        connectedRemoteId = null,
        status = if (it.myPeerId.isNotBlank()) VoiceCallStatus.READY else VoiceCallStatus.DISCONNECTED,
        statusDetail = if (it.myPeerId.isNotBlank()) "Voice ready · Call ended" else "Disconnected",
      )
    }
  }

  fun invitePeerToLobby(
    remoteIdInput: String,
    localName: String,
    heroIndex: Int,
    weapon: WeaponType,
  ): Boolean {
    val targetId = remoteIdInput.trim()
    val state = _uiState.value
    if (state.myPeerId.isBlank()) {
      _uiState.update { it.copy(statusDetail = "Connecting server... wait 1s & retry") }
      return false
    }
    if (targetId.isEmpty() || targetId == state.myPeerId) {
      _uiState.update {
        it.copy(
          statusDetail =
            if (targetId.isEmpty()) "Enter friend Peer ID to invite"
            else "Cannot invite your own Peer ID",
        )
      }
      return false
    }
    if (state.remoteSquadPeers.size >= 3 && state.remoteSquadPeers.none { it.id == targetId }) {
      _uiState.update { it.copy(statusDetail = "Squad is full (max 4 players)") }
      return false
    }

    val payload =
      buildJsonMap(
        "type" to "SQUAD_JOIN_REQ",
        "peerId" to state.myPeerId,
        "name" to localName,
        "heroIdx" to heroIndex.toString(),
        "weapon" to weapon.name,
        "ready" to "true",
      )
    evalJs("window.viceVoice && window.viceVoice.sendToPeer(${quoteJsString(targetId)}, ${quoteJsString(payload)});")
    _uiState.update { it.copy(statusDetail = "Invite sent to $targetId · Waiting YES/NO...") }
    onSquadNotice?.invoke("INVITE SENT TO $targetId")
    return true
  }

  fun acceptIncomingInvite(localName: String, heroIndex: Int, weapon: WeaponType) {
    val invite = _uiState.value.incomingInvite ?: return
    val member =
      LobbySquadMember(
        id = invite.peerId,
        name = invite.senderName,
        badge = "P2P",
        heroIndex = invite.heroIndex,
        weapon = invite.weapon,
        isReady = true,
        isRemoteP2P = true,
      )
    addOrUpdateRemoteSquadMember(member)
    _uiState.update {
      it.copy(
        incomingInvite = null,
        remotePeerIdInput = invite.peerId,
        statusDetail = "${invite.senderName} in your Lobby · Turn on Mic to talk!",
      )
    }

    val ack =
      buildJsonMap(
        "type" to "SQUAD_JOIN_ACK",
        "peerId" to _uiState.value.myPeerId,
        "name" to localName,
        "heroIdx" to heroIndex.toString(),
        "weapon" to weapon.name,
        "ready" to "true",
      )
    evalJs("window.viceVoice && window.viceVoice.sendToPeer(${quoteJsString(invite.peerId)}, ${quoteJsString(ack)});")
    onSquadNotice?.invoke("${invite.senderName} JOINED LOBBY · MIC ON TO TALK")
  }

  fun declineIncomingInvite() {
    val invite = _uiState.value.incomingInvite ?: return
    _uiState.update {
      it.copy(
        incomingInvite = null,
        statusDetail = "Invite rejected (NO)",
      )
    }
    val msg = buildJsonMap("type" to "SQUAD_DECLINE", "peerId" to _uiState.value.myPeerId)
    evalJs("window.viceVoice && window.viceVoice.sendToPeer(${quoteJsString(invite.peerId)}, ${quoteJsString(msg)});")
    onSquadNotice?.invoke("INVITE DECLINED")
  }

  fun removeRemoteSquadMember(peerId: String) {
    val msg = buildJsonMap("type" to "SQUAD_LEAVE", "peerId" to _uiState.value.myPeerId)
    evalJs("window.viceVoice && window.viceVoice.sendToPeer(${quoteJsString(peerId)}, ${quoteJsString(msg)});")
    val updated = _uiState.value.remoteSquadPeers.filterNot { it.id == peerId }
    _uiState.update { it.copy(remoteSquadPeers = updated) }
    if (_uiState.value.connectedRemoteId == peerId) {
      disconnectVoice()
    }
    onRemoteSquadChanged?.invoke(updated)
  }

  fun broadcastLobbySync(localName: String, heroIndex: Int, weapon: WeaponType, ready: Boolean) {
    if (_uiState.value.remoteSquadPeers.isEmpty()) return
    val payload =
      buildJsonMap(
        "type" to "LOBBY_SYNC",
        "peerId" to _uiState.value.myPeerId,
        "name" to localName,
        "heroIdx" to heroIndex.toString(),
        "weapon" to weapon.name,
        "ready" to ready.toString(),
      )
    evalJs("window.viceVoice && window.viceVoice.broadcastSquad(${quoteJsString(payload)});")
  }

  fun broadcastStartMatch(dropZoneName: String) {
    if (_uiState.value.remoteSquadPeers.isEmpty()) return
    val payload = buildJsonMap("type" to "START_MATCH", "dropZone" to dropZoneName)
    evalJs("window.viceVoice && window.viceVoice.broadcastSquad(${quoteJsString(payload)});")
  }

  fun sendInGamePoseIfReady(
    nowMs: Long,
    x: Float,
    y: Float,
    z: Float,
    yawRad: Float,
    speedNorm: Float,
    isSprinting: Boolean,
    isSitting: Boolean,
    dropPhase: DropPhase,
    weapon: WeaponType,
    heroIndex: Int,
  ) {
    if (_uiState.value.remoteSquadPeers.isEmpty()) return
    if (abs(nowMs - lastStateBroadcastMs) < 65L) return
    lastStateBroadcastMs = nowMs
    val payload =
      buildJsonMap(
        "type" to "PLAYER_STATE",
        "peerId" to _uiState.value.myPeerId,
        "x" to ((x * 100f).toInt() / 100f).toString(),
        "y" to ((y * 100f).toInt() / 100f).toString(),
        "z" to ((z * 100f).toInt() / 100f).toString(),
        "yaw" to ((yawRad * 100f).toInt() / 100f).toString(),
        "spd" to ((speedNorm * 100f).toInt() / 100f).toString(),
        "spr" to isSprinting.toString(),
        "sit" to isSitting.toString(),
        "drop" to dropPhase.name,
        "wpn" to weapon.name,
        "hero" to heroIndex.toString(),
      )
    evalJs("window.viceVoice && window.viceVoice.broadcastSquad(${quoteJsString(payload)});")
  }

  private fun addOrUpdateRemoteSquadMember(member: LobbySquadMember) {
    val existing = _uiState.value.remoteSquadPeers.toMutableList()
    val idx = existing.indexOfFirst { it.id == member.id }
    if (idx >= 0) {
      existing[idx] = member
    } else if (existing.size < 3) {
      existing.add(member)
    }
    _uiState.update { it.copy(remoteSquadPeers = existing) }
    onRemoteSquadChanged?.invoke(existing)
  }

  internal fun handleSquadMessage(fromPeerId: String, rawJson: String) {
    val map = parseSimpleJsonObject(rawJson) ?: return
    val type = map["type"] ?: return
    val senderPeer = (map["peerId"] ?: fromPeerId).ifBlank { fromPeerId }
    when (type) {
      "SQUAD_JOIN_REQ" -> {
        val name = map["name"]?.take(18)?.ifBlank { null } ?: senderPeer.uppercase()
        val heroIdx = map["heroIdx"]?.toIntOrNull() ?: 2
        val weapon = map["weapon"]?.let { w -> WeaponType.entries.firstOrNull { it.name == w } } ?: WeaponType.ASSAULT_RIFLE
        _uiState.update {
          it.copy(
            incomingInvite = IncomingSquadInvite(senderPeer, name, heroIdx, weapon),
            statusDetail = "Invite from $name ($senderPeer)",
          )
        }
        onSquadNotice?.invoke("SQUAD INVITE FROM $name")
      }
      "SQUAD_JOIN_ACK" -> {
        val name = map["name"]?.take(18)?.ifBlank { null } ?: senderPeer.uppercase()
        val heroIdx = map["heroIdx"]?.toIntOrNull() ?: 2
        val weapon = map["weapon"]?.let { w -> WeaponType.entries.firstOrNull { it.name == w } } ?: WeaponType.ASSAULT_RIFLE
        val member =
          LobbySquadMember(
            id = senderPeer,
            name = name,
            badge = "P2P",
            heroIndex = heroIdx,
            weapon = weapon,
            isReady = map["ready"] != "false",
            isRemoteP2P = true,
          )
        addOrUpdateRemoteSquadMember(member)
        _uiState.update {
          it.copy(
            remotePeerIdInput = senderPeer,
            statusDetail = "$name accepted (YES) · Same Lobby!",
          )
        }
        onSquadNotice?.invoke("$name ACCEPTED (YES) · IN LOBBY!")
        autoLinkVoiceWithPeer(senderPeer)
      }
      "LOBBY_SYNC" -> {
        val name = map["name"]?.take(18)?.ifBlank { null } ?: senderPeer.uppercase()
        val heroIdx = map["heroIdx"]?.toIntOrNull() ?: 2
        val weapon = map["weapon"]?.let { w -> WeaponType.entries.firstOrNull { it.name == w } } ?: WeaponType.ASSAULT_RIFLE
        val member =
          LobbySquadMember(
            id = senderPeer,
            name = name,
            badge = "P2P",
            heroIndex = heroIdx,
            weapon = weapon,
            isReady = map["ready"] != "false",
            isRemoteP2P = true,
          )
        addOrUpdateRemoteSquadMember(member)
      }
      "SQUAD_LEAVE", "SQUAD_DECLINE" -> {
        val updated = _uiState.value.remoteSquadPeers.filterNot { it.id == senderPeer }
        val statusMsg =
          if (type == "SQUAD_DECLINE") "$senderPeer clicked NO (declined)"
          else "$senderPeer left squad"
        _uiState.update {
          it.copy(
            remoteSquadPeers = updated,
            statusDetail = statusMsg,
          )
        }
        onRemoteSquadChanged?.invoke(updated)
        onSquadNotice?.invoke(
          if (type == "SQUAD_DECLINE") "$senderPeer DECLINED INVITE" else "$senderPeer LEFT SQUAD",
        )
      }
      "START_MATCH" -> {
        val dropZone = map["dropZone"] ?: "FULL_WORLD"
        onRemoteStartMatch?.invoke(dropZone)
      }
      "PLAYER_STATE" -> {
        val existingMember = _uiState.value.remoteSquadPeers.firstOrNull { it.id == senderPeer }
        val pose =
          RemotePlayerPose(
            peerId = senderPeer,
            name = existingMember?.name ?: senderPeer.uppercase(),
            x = map["x"]?.toFloatOrNull() ?: return,
            y = map["y"]?.toFloatOrNull() ?: 0f,
            z = map["z"]?.toFloatOrNull() ?: return,
            yawRad = map["yaw"]?.toFloatOrNull() ?: 0f,
            speedNorm = map["spd"]?.toFloatOrNull() ?: 0f,
            isSprinting = map["spr"] == "true",
            isSitting = map["sit"] == "true",
            dropPhase = map["drop"]?.let { d -> DropPhase.entries.firstOrNull { it.name == d } } ?: DropPhase.GROUND,
            weapon = map["wpn"]?.let { w -> WeaponType.entries.firstOrNull { it.name == w } } ?: WeaponType.FISTS,
            heroIndex = map["hero"]?.toIntOrNull() ?: (existingMember?.heroIndex ?: 2),
          )
        onRemotePoseReceived?.invoke(pose)
      }
    }
  }

  fun attachWebView(webView: WebView) {
    webViewRef = webView
  }

  fun detachWebView(webView: WebView) {
    if (webViewRef === webView) {
      webViewRef = null
    }
  }

  fun shutdown() {
    setVoiceSpeakerRouting(false)
    val wv = webViewRef
    webViewRef = null
    jsExecutor = null
    wv?.let {
      runOnMain {
        runCatching {
          it.evaluateJavascript("window.viceVoice && window.viceVoice.destroy();", null)
          it.stopLoading()
          it.destroy()
        }
      }
    }
  }

  internal fun handleLocalVoiceActivity(isSpeaking: Boolean, level: Float) {
    _uiState.update { state ->
      val activeSpeaking = isSpeaking && !state.isMicMuted
      state.copy(
        isLocalSpeaking = activeSpeaking,
        localVoiceLevel = if (state.isMicMuted) 0f else level.coerceIn(0f, 1f),
      )
    }
  }

  internal fun handleRemoteVoiceActivity(remoteKey: String, isSpeaking: Boolean) {
    _uiState.update { state ->
      if (state.isSpeakerMuted) {
        return@update state.copy(speakingPeerIds = emptySet())
      }
      val mappedPeer = uidToPeerId[remoteKey]
      val nextSet = state.speakingPeerIds.toMutableSet()
      if (isSpeaking) {
        nextSet.add(remoteKey)
        if (!mappedPeer.isNullOrBlank()) nextSet.add(mappedPeer)
      } else {
        nextSet.remove(remoteKey)
        if (!mappedPeer.isNullOrBlank()) nextSet.remove(mappedPeer)
      }
      state.copy(speakingPeerIds = nextSet)
    }
  }

  internal fun handleRoomVoiceConnected(remoteUid: String) {
    setVoiceSpeakerRouting(true)
    _uiState.update { state ->
      val nextRoom = state.roomConnectedUids + remoteUid
      state.copy(
        roomConnectedUids = nextRoom,
        status = VoiceCallStatus.CONNECTED,
        statusDetail = "Team Voice Live (${nextRoom.size + 1} in room)",
      )
    }
  }

  internal fun handleRoomVoiceClosed(remoteUid: String) {
    _uiState.update { state ->
      val nextRoom = state.roomConnectedUids - remoteUid
      val mappedPeer = uidToPeerId[remoteUid]
      val nextSpeaking = state.speakingPeerIds - remoteUid - (mappedPeer ?: "")
      val stillConnected = nextRoom.isNotEmpty() || state.connectedRemoteId != null
      if (!stillConnected) {
        setVoiceSpeakerRouting(false)
      }
      state.copy(
        roomConnectedUids = nextRoom,
        speakingPeerIds = nextSpeaking,
        status =
          when {
            stillConnected -> VoiceCallStatus.CONNECTED
            state.myPeerId.isNotBlank() -> VoiceCallStatus.READY
            else -> VoiceCallStatus.DISCONNECTED
          },
      )
    }
  }

  // Bridge callbacks invoked from JS (or unit tests)
  internal fun handlePeerOpen(peerId: String) {
    _uiState.update {
      it.copy(
        myPeerId = peerId,
        status = if (it.connectedRemoteId != null || it.roomConnectedUids.isNotEmpty()) it.status else VoiceCallStatus.READY,
        statusDetail = if (it.connectedRemoteId != null || it.roomConnectedUids.isNotEmpty()) it.statusDetail else "Ready · Share ID or invite",
      )
    }
    onPeerIdOpened?.invoke(peerId)
    if (activeRoomLocalUid.isNotBlank() && activeRoomRemoteUids.isNotEmpty()) {
      syncRoomAudioPeers(activeRoomLocalUid, activeRoomRemoteUids)
    }
  }

  internal fun handleCallConnecting(remoteId: String) {
    _uiState.update {
      it.copy(
        connectedRemoteId = remoteId,
        status = VoiceCallStatus.CONNECTING,
        statusDetail = "Linking audio with $remoteId...",
      )
    }
  }

  internal fun handleCallConnected(remoteId: String) {
    setVoiceSpeakerRouting(true)
    _uiState.update {
      it.copy(
        connectedRemoteId = remoteId,
        status = VoiceCallStatus.CONNECTED,
        statusDetail = "Live voice · $remoteId",
      )
    }
  }

  internal fun handleCallClosed(reason: String) {
    if (_uiState.value.roomConnectedUids.isEmpty()) {
      setVoiceSpeakerRouting(false)
    }
    _uiState.update {
      val stillRoom = it.roomConnectedUids.isNotEmpty()
      it.copy(
        connectedRemoteId = null,
        status =
          when {
            stillRoom -> VoiceCallStatus.CONNECTED
            it.myPeerId.isNotBlank() -> VoiceCallStatus.READY
            else -> VoiceCallStatus.DISCONNECTED
          },
        statusDetail = if (stillRoom) "Team Voice Live (${it.roomConnectedUids.size + 1} in room)" else reason.ifBlank { "Call ended" },
      )
    }
  }

  internal fun handlePeerError(message: String) {
    if (_uiState.value.roomConnectedUids.isEmpty()) {
      setVoiceSpeakerRouting(false)
    }
    _uiState.update {
      it.copy(
        connectedRemoteId = null,
        status = if (it.roomConnectedUids.isNotEmpty()) VoiceCallStatus.CONNECTED else VoiceCallStatus.ERROR,
        statusDetail = message.ifBlank { "Voice connection error" },
      )
    }
  }

  private fun evalJs(script: String) {
    val testExec = jsExecutor
    if (testExec != null) {
      testExec(script)
      return
    }
    val wv = webViewRef ?: return
    runOnMain { wv.evaluateJavascript(script, null) }
  }

  private fun runOnMain(block: () -> Unit) {
    val handler = mainHandler
    val onMain = handler != null && runCatching { Looper.myLooper() == Looper.getMainLooper() }.getOrDefault(false)
    if (handler == null || onMain) {
      block()
    } else {
      handler.post(block)
    }
  }

  inner class JsBridge {
    @JavascriptInterface
    fun onPeerOpen(peerId: String) {
      runOnMain { handlePeerOpen(peerId) }
    }

    @JavascriptInterface
    fun onCallConnecting(remoteId: String) {
      runOnMain { handleCallConnecting(remoteId) }
    }

    @JavascriptInterface
    fun onCallConnected(remoteId: String) {
      runOnMain { handleCallConnected(remoteId) }
    }

    @JavascriptInterface
    fun onCallClosed(reason: String) {
      runOnMain { handleCallClosed(reason) }
    }

    @JavascriptInterface
    fun onPeerError(message: String) {
      runOnMain { handlePeerError(message) }
    }

    @JavascriptInterface
    fun onSquadMessage(remoteId: String, rawJson: String) {
      runOnMain { handleSquadMessage(remoteId, rawJson) }
    }

    @JavascriptInterface
    fun onWebRtcSignal(recipientUid: String, type: String, payload: String) {
      runOnMain { onSendWebRtcSignal?.invoke(recipientUid, type, payload) }
    }

    @JavascriptInterface
    fun onLocalVoiceActivity(isSpeaking: Boolean, level: Float) {
      runOnMain { handleLocalVoiceActivity(isSpeaking, level) }
    }

    @JavascriptInterface
    fun onRemoteVoiceActivity(remoteKey: String, isSpeaking: Boolean) {
      runOnMain { handleRemoteVoiceActivity(remoteKey, isSpeaking) }
    }

    @JavascriptInterface
    fun onRoomVoiceConnected(remoteUid: String) {
      runOnMain { handleRoomVoiceConnected(remoteUid) }
    }

    @JavascriptInterface
    fun onRoomVoiceClosed(remoteUid: String) {
      runOnMain { handleRoomVoiceClosed(remoteUid) }
    }
  }

  companion object {
    const val BASE_URL = "https://vicehorizon.local/"
    const val PEERJS_CDN_PRIMARY = "https://unpkg.com/peerjs@1.5.4/dist/peerjs.min.js"
    const val PEERJS_CDN_FALLBACK = "https://cdn.jsdelivr.net/npm/peerjs@1.5.4/dist/peerjs.min.js"
    const val SOCKET_IO_CDN = "https://cdn.socket.io/4.7.5/socket.io.min.js"

    internal fun quoteJsString(value: String): String {
      val escaped =
        buildString(value.length + 4) {
          append('"')
          for (ch in value) {
            when (ch) {
              '\\' -> append("\\\\")
              '"' -> append("\\\"")
              '\n' -> append("\\n")
              '\r' -> append("\\r")
              '\t' -> append("\\t")
              '<' -> append("\\u003c")
              '>' -> append("\\u003e")
              else -> append(ch)
            }
          }
          append('"')
        }
      return escaped
    }

    internal fun buildJsonMap(vararg entries: Pair<String, String>): String =
      entries.joinToString(prefix = "{", postfix = "}", separator = ",") { (k, v) ->
        "${quoteJsString(k)}:${quoteJsString(v)}"
      }

    internal fun parseSimpleJsonObject(raw: String): Map<String, String>? {
      val trimmed = raw.trim()
      if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
      val result = LinkedHashMap<String, String>()
      var idx = 1
      fun skipWs() {
        while (idx < trimmed.length && trimmed[idx].isWhitespace()) idx++
      }
      fun readToken(): String? {
        skipWs()
        if (idx >= trimmed.length) return null
        if (trimmed[idx] == '"') {
          idx++
          val sb = StringBuilder()
          while (idx < trimmed.length) {
            val ch = trimmed[idx++]
            if (ch == '\\' && idx < trimmed.length) {
              val esc = trimmed[idx++]
              when (esc) {
                'n' -> sb.append('\n')
                'r' -> sb.append('\r')
                't' -> sb.append('\t')
                'u' -> {
                  if (idx + 4 <= trimmed.length) {
                    val code = trimmed.substring(idx, idx + 4).toIntOrNull(16)
                    if (code != null) sb.append(code.toChar())
                    idx += 4
                  }
                }
                else -> sb.append(esc)
              }
            } else if (ch == '"') {
              return sb.toString()
            } else {
              sb.append(ch)
            }
          }
          return sb.toString()
        } else {
          val start = idx
          while (idx < trimmed.length && trimmed[idx] !in charArrayOf(',', '}', ':')) idx++
          return trimmed.substring(start, idx).trim()
        }
      }
      while (idx < trimmed.length - 1) {
        val key = readToken() ?: break
        skipWs()
        if (idx < trimmed.length && trimmed[idx] == ':') idx++
        val value = readToken() ?: break
        result[key] = value
        skipWs()
        if (idx < trimmed.length && trimmed[idx] == ',') idx++
      }
      return result
    }

    fun buildPeerJsHtml(): String =
      """
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
        <script>
          function loadFallbackPeerJs() {
            var s = document.createElement('script');
            s.src = '$PEERJS_CDN_FALLBACK';
            s.onload = function() { window.viceVoice && window.viceVoice.initPeer(); };
            s.onerror = function() {
              if (window.AndroidVoice) {
                window.AndroidVoice.onPeerError('PeerJS CDN unreachable');
              }
            };
            document.head.appendChild(s);
          }
        </script>
        <script src="$PEERJS_CDN_PRIMARY" onerror="loadFallbackPeerJs()"></script>
        <script src="$SOCKET_IO_CDN" onerror="/* Socket.IO optional */"></script>
      </head>
      <body>
        <audio id="remoteAudio" autoplay playsinline></audio>
        <div id="roomAudioContainer"></div>
        <script>
          (function() {
            var peer = null;
            var activeCall = null;
            var localStream = null;
            var currentMuted = true;
            var currentSpeakerMuted = false;
            var pendingIncomingCall = null;
            var dataConns = {};
            var pendingPayloads = {};
            var roomLocalUid = '';
            var roomPeers = {};
            var roomIceQueues = {};
            var roomAudioEls = {};
            var remoteMeters = {};
            var voiceSocket = null;
            var socketPeerFlags = {};
            var meterAudioCtx = null;
            var localMeterInterval = null;
            var localLastSpeaking = false;
            var localHoldUntilMs = 0;

            var RTC_ICE_CONFIG = {
              iceServers: [
                { urls: 'stun:stun.l.google.com:19302' },
                { urls: 'stun:stun1.l.google.com:19302' },
                { urls: 'stun:stun.cloudflare.com:3478' }
              ]
            };

            function notify(method, arg) {
              try {
                if (window.AndroidVoice && typeof window.AndroidVoice[method] === 'function') {
                  window.AndroidVoice[method](arg || '');
                }
              } catch (e) {}
            }

            function notifySquad(remoteId, data) {
              try {
                var raw = (typeof data === 'string') ? data : JSON.stringify(data);
                if (window.AndroidVoice && typeof window.AndroidVoice.onSquadMessage === 'function') {
                  window.AndroidVoice.onSquadMessage(remoteId || '', raw || '');
                }
              } catch (e) {}
            }

            function bindDataConnection(conn) {
              if (!conn || !conn.peer) return;
              var remoteId = conn.peer;
              conn._viceOpenedAt = Date.now();
              dataConns[remoteId] = conn;
              conn.on('open', function() {
                var queue = pendingPayloads[remoteId];
                if (queue && queue.length) {
                  pendingPayloads[remoteId] = [];
                  for (var i = 0; i < queue.length; i++) {
                    try { conn.send(queue[i]); } catch (e) {}
                  }
                }
              });
              conn.on('data', function(data) {
                notifySquad(remoteId, data);
              });
              conn.on('close', function() {
                if (dataConns[remoteId] === conn) {
                  delete dataConns[remoteId];
                  notifySquad(remoteId, JSON.stringify({ type: 'SQUAD_LEAVE', peerId: remoteId }));
                }
              });
              conn.on('error', function() {
                if (dataConns[remoteId] === conn) {
                  delete dataConns[remoteId];
                }
              });
            }

            function randomPeerCode() {
              var digits = Math.floor(100000 + Math.random() * 900000);
              return 'vh-' + digits;
            }

            var isFallbackSilentStream = false;
            var silentCtx = null;

            function createSilentFallbackStream() {
              try {
                var Ctx = window.AudioContext || window.webkitAudioContext;
                if (!Ctx) return null;
                silentCtx = silentCtx || new Ctx();
                if (silentCtx.state === 'suspended') {
                  silentCtx.resume().catch(function() {});
                }
                var osc = silentCtx.createOscillator();
                var gain = silentCtx.createGain();
                gain.gain.value = 0.0;
                var dst = silentCtx.createMediaStreamDestination();
                osc.connect(gain);
                gain.connect(dst);
                osc.start();
                isFallbackSilentStream = true;
                return dst.stream;
              } catch (e) {
                return null;
              }
            }

            function ensureMeterCtx() {
              var Ctx = window.AudioContext || window.webkitAudioContext;
              if (!Ctx) return null;
              if (!meterAudioCtx) {
                meterAudioCtx = new Ctx();
              }
              if (meterAudioCtx.state === 'suspended') {
                meterAudioCtx.resume().catch(function() {});
              }
              return meterAudioCtx;
            }

            function computeStreamRms(analyser, buf) {
              analyser.getByteTimeDomainData(buf);
              var sum = 0;
              for (var i = 0; i < buf.length; i++) {
                var v = (buf[i] - 128) / 128.0;
                sum += v * v;
              }
              return Math.sqrt(sum / buf.length);
            }

            function startLocalMeter(stream) {
              if (localMeterInterval) {
                clearInterval(localMeterInterval);
                localMeterInterval = null;
              }
              if (!stream || isFallbackSilentStream) return;
              try {
                var ctx = ensureMeterCtx();
                if (!ctx) return;
                var src = ctx.createMediaStreamSource(stream);
                var analyser = ctx.createAnalyser();
                analyser.fftSize = 256;
                src.connect(analyser);
                var buf = new Uint8Array(analyser.fftSize);
                localMeterInterval = setInterval(function() {
                  if (currentMuted || isFallbackSilentStream) {
                    if (localLastSpeaking) {
                      localLastSpeaking = false;
                      if (window.AndroidVoice && window.AndroidVoice.onLocalVoiceActivity) {
                        window.AndroidVoice.onLocalVoiceActivity(false, 0.0);
                      }
                    }
                    return;
                  }
                  var rms = computeStreamRms(analyser, buf);
                  var now = Date.now();
                  if (rms > 0.032) {
                    localHoldUntilMs = now + 360;
                  }
                  var speaking = (now < localHoldUntilMs);
                  if (speaking !== localLastSpeaking || speaking) {
                    localLastSpeaking = speaking;
                    if (window.AndroidVoice && window.AndroidVoice.onLocalVoiceActivity) {
                      window.AndroidVoice.onLocalVoiceActivity(speaking, Math.min(1.0, rms * 4.5));
                    }
                  }
                }, 95);
              } catch (e) {}
            }

            function stopRemoteMeter(key) {
              var entry = remoteMeters[key];
              if (entry) {
                clearInterval(entry.timer);
                delete remoteMeters[key];
              }
              if (window.AndroidVoice && window.AndroidVoice.onRemoteVoiceActivity) {
                window.AndroidVoice.onRemoteVoiceActivity(key, false);
              }
            }

            function startRemoteMeter(key, stream) {
              stopRemoteMeter(key);
              if (!stream) return;
              try {
                var ctx = ensureMeterCtx();
                if (!ctx) return;
                var src = ctx.createMediaStreamSource(stream);
                var analyser = ctx.createAnalyser();
                analyser.fftSize = 256;
                src.connect(analyser);
                var buf = new Uint8Array(analyser.fftSize);
                var state = { speaking: false, holdUntil: 0, timer: null };
                state.timer = setInterval(function() {
                  if (currentSpeakerMuted) {
                    if (state.speaking) {
                      state.speaking = false;
                      if (window.AndroidVoice && window.AndroidVoice.onRemoteVoiceActivity) {
                        window.AndroidVoice.onRemoteVoiceActivity(key, false);
                      }
                    }
                    return;
                  }
                  var rms = computeStreamRms(analyser, buf);
                  var now = Date.now();
                  if (rms > 0.028) {
                    state.holdUntil = now + 380;
                  }
                  var speaking = (now < state.holdUntil);
                  if (speaking !== state.speaking) {
                    state.speaking = speaking;
                    if (window.AndroidVoice && window.AndroidVoice.onRemoteVoiceActivity) {
                      window.AndroidVoice.onRemoteVoiceActivity(key, speaking);
                    }
                  }
                }, 110);
                remoteMeters[key] = state;
              } catch (e) {}
            }

            function applySpeakerMuteToAll() {
              var remoteAudio = document.getElementById('remoteAudio');
              if (remoteAudio) {
                remoteAudio.muted = currentSpeakerMuted;
                remoteAudio.volume = currentSpeakerMuted ? 0.0 : 1.0;
              }
              var keys = Object.keys(roomAudioEls);
              for (var i = 0; i < keys.length; i++) {
                var el = roomAudioEls[keys[i]];
                if (el) {
                  el.muted = currentSpeakerMuted;
                  el.volume = currentSpeakerMuted ? 0.0 : 1.0;
                }
              }
            }

            function replaceTrackOnActiveCall(stream) {
              if (!stream) return;
              var tracks = stream.getAudioTracks();
              if (!tracks || !tracks.length) return;
              var audioTrack = tracks[0];
              if (activeCall && activeCall.peerConnection) {
                try {
                  var senders = activeCall.peerConnection.getSenders();
                  for (var i = 0; i < senders.length; i++) {
                    if (senders[i].track && senders[i].track.kind === 'audio') {
                      senders[i].replaceTrack(audioTrack).catch(function() {});
                    }
                  }
                } catch (e) {}
              }
              var rKeys = Object.keys(roomPeers);
              for (var r = 0; r < rKeys.length; r++) {
                var pc = roomPeers[rKeys[r]];
                if (!pc) continue;
                try {
                  var rSenders = pc.getSenders();
                  var replaced = false;
                  for (var j = 0; j < rSenders.length; j++) {
                    if (rSenders[j].track && rSenders[j].track.kind === 'audio') {
                      rSenders[j].replaceTrack(audioTrack).catch(function() {});
                      replaced = true;
                    }
                  }
                  if (!replaced) {
                    pc.addTrack(audioTrack, stream);
                  }
                } catch (e) {}
              }
            }

            function applyMuteState() {
              if (!localStream) return;
              var tracks = localStream.getAudioTracks();
              for (var i = 0; i < tracks.length; i++) {
                tracks[i].enabled = !currentMuted;
              }
            }

            async function ensureLocalStream(mutedFlag, allowSilentFallback) {
              if (typeof mutedFlag === 'boolean') {
                currentMuted = mutedFlag;
              }
              if (localStream && !isFallbackSilentStream) {
                applyMuteState();
                return localStream;
              }
              if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
                try {
                  var micStream = await navigator.mediaDevices.getUserMedia({
                    audio: {
                      echoCancellation: true,
                      noiseSuppression: true,
                      autoGainControl: true
                    },
                    video: false
                  });
                  isFallbackSilentStream = false;
                  localStream = micStream;
                  applyMuteState();
                  replaceTrackOnActiveCall(micStream);
                  startLocalMeter(micStream);
                  return localStream;
                } catch (err) {
                  if (!allowSilentFallback) {
                    throw err;
                  }
                }
              } else if (!allowSilentFallback) {
                throw new Error('Microphone API unavailable');
              }
              if (localStream) {
                return localStream;
              }
              var silent = createSilentFallbackStream();
              if (silent) {
                localStream = silent;
                return localStream;
              }
              throw new Error('Microphone access denied');
            }

            function attachRemoteAudioStream(remoteStream, meterKey) {
              var audioEl = document.getElementById('remoteAudio');
              if (audioEl && remoteStream) {
                audioEl.muted = currentSpeakerMuted;
                audioEl.volume = currentSpeakerMuted ? 0.0 : 1.0;
                audioEl.srcObject = remoteStream;
                var playPromise = audioEl.play();
                if (playPromise && typeof playPromise.catch === 'function') {
                  playPromise.catch(function() {});
                }
                if (meterKey) {
                  startRemoteMeter(meterKey, remoteStream);
                }
              }
            }

            function attachRoomAudioStream(remoteUid, remoteStream) {
              if (!remoteStream) return;
              var el = roomAudioEls[remoteUid];
              if (!el) {
                el = document.createElement('audio');
                el.autoplay = true;
                el.playsInline = true;
                var container = document.getElementById('roomAudioContainer');
                if (container) container.appendChild(el);
                roomAudioEls[remoteUid] = el;
              }
              el.muted = currentSpeakerMuted;
              el.volume = currentSpeakerMuted ? 0.0 : 1.0;
              el.srcObject = remoteStream;
              var p = el.play();
              if (p && typeof p.catch === 'function') p.catch(function() {});
              startRemoteMeter(remoteUid, remoteStream);
            }

            function closeRoomPeer(remoteUid) {
              stopRemoteMeter(remoteUid);
              var pc = roomPeers[remoteUid];
              if (pc) {
                try { pc.close(); } catch (e) {}
                delete roomPeers[remoteUid];
              }
              delete roomIceQueues[remoteUid];
              var el = roomAudioEls[remoteUid];
              if (el) {
                try {
                  el.srcObject = null;
                  if (el.parentNode) el.parentNode.removeChild(el);
                } catch (e) {}
                delete roomAudioEls[remoteUid];
              }
              if (window.AndroidVoice && window.AndroidVoice.onRoomVoiceClosed) {
                window.AndroidVoice.onRoomVoiceClosed(remoteUid);
              }
            }

            async function getOrCreateRoomPeer(remoteUid) {
              if (roomPeers[remoteUid]) return roomPeers[remoteUid];
              var pc = new RTCPeerConnection(RTC_ICE_CONFIG);
              roomPeers[remoteUid] = pc;
              roomIceQueues[remoteUid] = roomIceQueues[remoteUid] || [];

              try {
                var stream = await ensureLocalStream(currentMuted, true);
                var tracks = stream.getAudioTracks();
                if (tracks.length > 0) {
                  for (var i = 0; i < tracks.length; i++) {
                    pc.addTrack(tracks[i], stream);
                  }
                } else if (pc.addTransceiver) {
                  pc.addTransceiver('audio', { direction: 'sendrecv' });
                }
              } catch (e) {
                try {
                  if (pc.addTransceiver) {
                    pc.addTransceiver('audio', { direction: 'sendrecv' });
                  }
                } catch (ignored) {}
              }

              pc.onicecandidate = function(ev) {
                if (!ev.candidate) return;
                if (socketPeerFlags[remoteUid] && voiceSocket && voiceSocket.connected) {
                  voiceSocket.emit('signal', { to: remoteUid, signal: ev.candidate });
                } else if (window.AndroidVoice && window.AndroidVoice.onWebRtcSignal) {
                  window.AndroidVoice.onWebRtcSignal(remoteUid, 'ICE', JSON.stringify(ev.candidate));
                }
              };

              pc.ontrack = function(ev) {
                var rStream = (ev.streams && ev.streams[0]) ? ev.streams[0] : (ev.track ? new MediaStream([ev.track]) : null);
                if (rStream) {
                  attachRoomAudioStream(remoteUid, rStream);
                  if (window.AndroidVoice && window.AndroidVoice.onRoomVoiceConnected) {
                    window.AndroidVoice.onRoomVoiceConnected(remoteUid);
                  }
                }
              };

              pc.onconnectionstatechange = function() {
                if (pc.connectionState === 'connected') {
                  if (window.AndroidVoice && window.AndroidVoice.onRoomVoiceConnected) {
                    window.AndroidVoice.onRoomVoiceConnected(remoteUid);
                  }
                } else if (pc.connectionState === 'closed' || pc.connectionState === 'failed') {
                  if (roomPeers[remoteUid] === pc) {
                    closeRoomPeer(remoteUid);
                  }
                }
              };

              return pc;
            }

            async function flushRoomIce(remoteUid, pc) {
              var q = roomIceQueues[remoteUid];
              if (!q || !q.length || !pc.remoteDescription) return;
              roomIceQueues[remoteUid] = [];
              for (var i = 0; i < q.length; i++) {
                try { await pc.addIceCandidate(new RTCIceCandidate(q[i])); } catch (e) {}
              }
            }

            function bindCallEvents(call, remoteId) {
              var prevCall = activeCall;
              activeCall = call;
              if (prevCall && prevCall !== call) {
                try { prevCall.close(); } catch (e) {}
              }
              notify('onCallConnecting', remoteId);

              call.on('stream', function(remoteStream) {
                attachRemoteAudioStream(remoteStream, remoteId);
                notify('onCallConnected', remoteId);
              });

              if (call.peerConnection) {
                call.peerConnection.addEventListener('track', function(ev) {
                  if (ev.streams && ev.streams[0]) {
                    attachRemoteAudioStream(ev.streams[0], remoteId);
                    notify('onCallConnected', remoteId);
                  } else if (ev.track && typeof MediaStream !== 'undefined') {
                    attachRemoteAudioStream(new MediaStream([ev.track]), remoteId);
                    notify('onCallConnected', remoteId);
                  }
                });
              }

              call.on('close', function() {
                stopRemoteMeter(remoteId);
                if (activeCall === call) {
                  activeCall = null;
                  notify('onCallClosed', 'Call ended');
                }
              });

              call.on('error', function(err) {
                stopRemoteMeter(remoteId);
                if (activeCall === call) {
                  activeCall = null;
                }
                notify('onPeerError', (err && err.message) ? err.message : 'Voice stream error');
              });
            }

            function createPeerInstance(preferredId) {
              if (typeof Peer === 'undefined') {
                return;
              }
              if (peer && !peer.destroyed) {
                return;
              }
              var peerOptions = {
                debug: 1,
                config: {
                  iceServers: [
                    { urls: 'stun:stun.l.google.com:19302' },
                    { urls: 'stun:stun1.l.google.com:19302' },
                    { urls: 'stun:stun.cloudflare.com:3478' }
                  ]
                }
              };
              peer = preferredId ? new Peer(preferredId, peerOptions) : new Peer(peerOptions);

              peer.on('open', function(id) {
                notify('onPeerOpen', id);
              });

              peer.on('connection', function(conn) {
                bindDataConnection(conn);
              });

              peer.on('call', async function(incomingCall) {
                var callerId = incomingCall.peer || 'Player';
                try {
                  var stream = await ensureLocalStream(currentMuted, true);
                  incomingCall.answer(stream);
                  bindCallEvents(incomingCall, callerId);
                } catch (err) {
                  pendingIncomingCall = incomingCall;
                  notify('onPeerError', 'Allow mic to answer ' + callerId);
                }
              });

              peer.on('disconnected', function() {
                if (peer && !peer.destroyed) {
                  try { peer.reconnect(); } catch (e) {}
                }
              });

              peer.on('error', function(err) {
                var type = err && err.type ? err.type : '';
                if (type === 'unavailable-id' && preferredId) {
                  try { peer.destroy(); } catch (e) {}
                  peer = null;
                  createPeerInstance(null);
                  return;
                }
                var msg = (err && err.message) ? err.message : ('Peer error: ' + type);
                notify('onPeerError', msg);
              });
            }

            window.viceVoice = {
              initPeer: function() {
                createPeerInstance(randomPeerCode());
              },
              onPermissionGranted: async function(mutedFlag) {
                try {
                  var stream = await ensureLocalStream(mutedFlag, false);
                  if (pendingIncomingCall) {
                    var callToAnswer = pendingIncomingCall;
                    pendingIncomingCall = null;
                    callToAnswer.answer(stream);
                    bindCallEvents(callToAnswer, callToAnswer.peer || 'Player');
                  }
                } catch (err) {
                  notify('onPeerError', (err && err.message) ? err.message : 'Mic init failed');
                }
              },
              sendToPeer: function(remoteId, payload) {
                if (!peer || peer.destroyed || !remoteId) return;
                if (peer.disconnected) {
                  try { peer.reconnect(); } catch (e) {}
                }
                var existing = dataConns[remoteId];
                if (existing && existing.open) {
                  try { existing.send(payload); return; } catch (e) {}
                }
                var stale = existing && ((Date.now() - (existing._viceOpenedAt || 0) > 4000) || (existing.dataChannel && existing.dataChannel.readyState === 'closed'));
                if (stale) {
                  try { existing.close(); } catch (e) {}
                  delete dataConns[remoteId];
                  existing = null;
                }
                if (!pendingPayloads[remoteId]) pendingPayloads[remoteId] = [];
                pendingPayloads[remoteId].push(payload);
                if (!existing) {
                  var conn = peer.connect(remoteId, { reliable: true });
                  bindDataConnection(conn);
                }
              },
              broadcastSquad: function(payload) {
                var keys = Object.keys(dataConns);
                for (var i = 0; i < keys.length; i++) {
                  var conn = dataConns[keys[i]];
                  if (conn && conn.open) {
                    try { conn.send(payload); } catch (e) {}
                  }
                }
              },
              connectToPeer: async function(remoteId, mutedFlag) {
                if (!peer || peer.destroyed) {
                  notify('onPeerError', 'Voice server not ready yet');
                  return;
                }
                if (peer.disconnected) {
                  try { peer.reconnect(); } catch (e) {}
                }
                try {
                  var stream = await ensureLocalStream(mutedFlag, true);
                  var call = peer.call(remoteId, stream);
                  if (!call) {
                    notify('onPeerError', 'Could not reach ' + remoteId);
                    return;
                  }
                  bindCallEvents(call, remoteId);
                } catch (err) {
                  notify('onPeerError', (err && err.message) ? err.message : 'Mic access denied');
                }
              },
              setMicMuted: function(mutedFlag) {
                currentMuted = !!mutedFlag;
                if (!currentMuted && (!localStream || isFallbackSilentStream)) {
                  ensureLocalStream(false, true).catch(function() {});
                }
                applyMuteState();
              },
              setSpeakerMuted: function(mutedFlag) {
                currentSpeakerMuted = !!mutedFlag;
                applySpeakerMuteToAll();
              },
              syncRoomPeers: async function(localUid, remoteCsv, mutedFlag, speakerMutedFlag) {
                roomLocalUid = localUid || '';
                if (typeof mutedFlag === 'boolean') currentMuted = mutedFlag;
                if (typeof speakerMutedFlag === 'boolean') {
                  currentSpeakerMuted = speakerMutedFlag;
                  applySpeakerMuteToAll();
                }
                var desired = {};
                if (remoteCsv) {
                  var parts = remoteCsv.split(',');
                  for (var i = 0; i < parts.length; i++) {
                    var u = parts[i].trim();
                    if (u && u !== roomLocalUid) desired[u] = true;
                  }
                }
                var existingKeys = Object.keys(roomPeers);
                for (var k = 0; k < existingKeys.length; k++) {
                  if (!desired[existingKeys[k]]) {
                    closeRoomPeer(existingKeys[k]);
                  }
                }
                var targetUids = Object.keys(desired);
                for (var t = 0; t < targetUids.length; t++) {
                  var remoteUid = targetUids[t];
                  var alreadyOpen = !!roomPeers[remoteUid];
                  var pc = await getOrCreateRoomPeer(remoteUid);
                  if (!alreadyOpen && roomLocalUid && roomLocalUid < remoteUid) {
                    try {
                      var offer = await pc.createOffer();
                      await pc.setLocalDescription(offer);
                      if (window.AndroidVoice && window.AndroidVoice.onWebRtcSignal) {
                        window.AndroidVoice.onWebRtcSignal(remoteUid, 'OFFER', JSON.stringify(pc.localDescription));
                      }
                    } catch (e) {}
                  }
                }
              },
              handleRoomSignal: async function(senderUid, type, payloadJson) {
                if (!senderUid || !type || !payloadJson) return;
                try {
                  if (type === 'HANGUP') {
                    closeRoomPeer(senderUid);
                    return;
                  }
                  var data = JSON.parse(payloadJson);
                  var pc = await getOrCreateRoomPeer(senderUid);
                  if (type === 'OFFER') {
                    await pc.setRemoteDescription(new RTCSessionDescription(data));
                    await flushRoomIce(senderUid, pc);
                    var answer = await pc.createAnswer();
                    await pc.setLocalDescription(answer);
                    if (window.AndroidVoice && window.AndroidVoice.onWebRtcSignal) {
                      window.AndroidVoice.onWebRtcSignal(senderUid, 'ANSWER', JSON.stringify(pc.localDescription));
                    }
                  } else if (type === 'ANSWER') {
                    await pc.setRemoteDescription(new RTCSessionDescription(data));
                    await flushRoomIce(senderUid, pc);
                  } else if (type === 'ICE') {
                    if (pc.remoteDescription) {
                      await pc.addIceCandidate(new RTCIceCandidate(data));
                    } else {
                      roomIceQueues[senderUid] = roomIceQueues[senderUid] || [];
                      roomIceQueues[senderUid].push(data);
                    }
                  }
                } catch (e) {}
              },
              joinSocketRoom: function(serverUrl, roomId, mutedFlag, speakerMutedFlag) {
                if (typeof io === 'undefined' || !serverUrl || !roomId) return;
                if (typeof mutedFlag === 'boolean') currentMuted = mutedFlag;
                if (typeof speakerMutedFlag === 'boolean') {
                  currentSpeakerMuted = speakerMutedFlag;
                  applySpeakerMuteToAll();
                }
                this.leaveSocketRoom();
                try {
                  voiceSocket = io(serverUrl, { transports: ['websocket', 'polling'] });
                  voiceSocket.on('connect', async function() {
                    try { await ensureLocalStream(currentMuted, true); } catch (e) {}
                    voiceSocket.emit('join-voice-room', roomId);
                  });
                  voiceSocket.on('user-connected', async function(userId) {
                    if (!userId || userId === voiceSocket.id) return;
                    socketPeerFlags[userId] = true;
                    try {
                      var pc = await getOrCreateRoomPeer(userId);
                      var offer = await pc.createOffer();
                      await pc.setLocalDescription(offer);
                      voiceSocket.emit('signal', { to: userId, signal: pc.localDescription });
                    } catch (e) {}
                  });
                  voiceSocket.on('signal', async function(pkt) {
                    if (!pkt || !pkt.from || !pkt.signal) return;
                    var fromId = pkt.from;
                    var sig = pkt.signal;
                    socketPeerFlags[fromId] = true;
                    try {
                      var pc = await getOrCreateRoomPeer(fromId);
                      if (sig.type === 'offer') {
                        await pc.setRemoteDescription(new RTCSessionDescription(sig));
                        await flushRoomIce(fromId, pc);
                        var answer = await pc.createAnswer();
                        await pc.setLocalDescription(answer);
                        voiceSocket.emit('signal', { to: fromId, signal: pc.localDescription });
                      } else if (sig.type === 'answer') {
                        await pc.setRemoteDescription(new RTCSessionDescription(sig));
                        await flushRoomIce(fromId, pc);
                      } else if (sig.candidate) {
                        if (pc.remoteDescription) {
                          await pc.addIceCandidate(new RTCIceCandidate(sig));
                        } else {
                          roomIceQueues[fromId] = roomIceQueues[fromId] || [];
                          roomIceQueues[fromId].push(sig);
                        }
                      }
                    } catch (e) {}
                  });
                  voiceSocket.on('user-disconnected', function(userId) {
                    if (!userId) return;
                    delete socketPeerFlags[userId];
                    closeRoomPeer(userId);
                  });
                } catch (e) {}
              },
              leaveSocketRoom: function() {
                var sKeys = Object.keys(socketPeerFlags);
                for (var i = 0; i < sKeys.length; i++) {
                  closeRoomPeer(sKeys[i]);
                }
                socketPeerFlags = {};
                if (voiceSocket) {
                  try { voiceSocket.disconnect(); } catch (e) {}
                  voiceSocket = null;
                }
              },
              disconnectCall: function() {
                this.leaveSocketRoom();
                if (activeCall) {
                  try { activeCall.close(); } catch (e) {}
                  activeCall = null;
                }
                var rKeys = Object.keys(roomPeers);
                for (var i = 0; i < rKeys.length; i++) {
                  closeRoomPeer(rKeys[i]);
                }
                var audioEl = document.getElementById('remoteAudio');
                if (audioEl) {
                  audioEl.srcObject = null;
                }
              },
              destroy: function() {
                if (localMeterInterval) {
                  clearInterval(localMeterInterval);
                  localMeterInterval = null;
                }
                var mKeys = Object.keys(remoteMeters);
                for (var m = 0; m < mKeys.length; m++) {
                  stopRemoteMeter(mKeys[m]);
                }
                this.disconnectCall();
                var keys = Object.keys(dataConns);
                for (var i = 0; i < keys.length; i++) {
                  try { dataConns[keys[i]].close(); } catch (e) {}
                }
                dataConns = {};
                if (localStream) {
                  localStream.getTracks().forEach(function(t) { t.stop(); });
                  localStream = null;
                }
                if (peer) {
                  try { peer.destroy(); } catch (e) {}
                  peer = null;
                }
              }
            };

            if (typeof Peer !== 'undefined') {
              window.viceVoice.initPeer();
            } else {
              window.addEventListener('load', function() {
                window.viceVoice.initPeer();
              });
            }
          })();
        </script>
      </body>
      </html>
      """.trimIndent()
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VoiceWebViewHost(
  manager: VoiceChatManager,
  modifier: Modifier = Modifier,
) {
  val readyToMount = remember { androidx.compose.runtime.mutableStateOf(false) }
  androidx.compose.runtime.LaunchedEffect(Unit) {
    kotlinx.coroutines.delay(1800L)
    readyToMount.value = true
  }
  if (!readyToMount.value) return

  val context = LocalContext.current
  val html = remember { VoiceChatManager.buildPeerJsHtml() }
  val webView =
    remember(context, manager) {
      WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.allowFileAccess = false
        settings.allowContentAccess = false

        addJavascriptInterface(manager.JsBridge(), "AndroidVoice")

        webViewClient =
          object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
              view: WebView?,
              request: WebResourceRequest?,
            ): Boolean = true
          }

        webChromeClient =
          object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
              if (request == null) return
              val micGranted =
                ContextCompat.checkSelfPermission(
                  context,
                  Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
              val asksAudio = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
              if (micGranted && asksAudio) {
                request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
              } else {
                request.deny()
              }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean = true
          }

        loadDataWithBaseURL(
          VoiceChatManager.BASE_URL,
          html,
          "text/html",
          "UTF-8",
          null,
        )
      }
    }

  DisposableEffect(manager, webView) {
    manager.attachWebView(webView)
    onDispose {
      manager.detachWebView(webView)
      runCatching {
        webView.evaluateJavascript("window.viceVoice && window.viceVoice.destroy();", null)
        webView.stopLoading()
        webView.destroy()
      }
    }
  }

  AndroidView(
    factory = { webView },
    modifier = modifier.size(1.dp).alpha(0f),
  )
}
