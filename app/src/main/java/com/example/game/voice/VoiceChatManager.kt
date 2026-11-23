package com.example.game.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
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
  val isMicMuted: Boolean = false,
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

  private var webViewRef: WebView? = null
  private var jsExecutor: ((String) -> Unit)? = null
  private var lastStateBroadcastMs: Long = 0L

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
    _uiState.update { it.copy(isMicMuted = nextMuted) }
    evalJs("window.viceVoice && window.viceVoice.setMicMuted($nextMuted);")
    return nextMuted
  }

  fun setMicMuted(muted: Boolean) {
    _uiState.update { it.copy(isMicMuted = muted) }
    evalJs("window.viceVoice && window.viceVoice.setMicMuted($muted);")
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
    _uiState.update { it.copy(statusDetail = "Invite sent to $targetId...") }
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
        statusDetail = "${invite.senderName} joined squad!",
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
    onSquadNotice?.invoke("${invite.senderName} JOINED LOBBY!")
  }

  fun declineIncomingInvite() {
    val invite = _uiState.value.incomingInvite ?: return
    _uiState.update { it.copy(incomingInvite = null, statusDetail = "Invite declined") }
    val msg = buildJsonMap("type" to "SQUAD_DECLINE", "peerId" to _uiState.value.myPeerId)
    evalJs("window.viceVoice && window.viceVoice.sendToPeer(${quoteJsString(invite.peerId)}, ${quoteJsString(msg)});")
  }

  fun removeRemoteSquadMember(peerId: String) {
    val msg = buildJsonMap("type" to "SQUAD_LEAVE", "peerId" to _uiState.value.myPeerId)
    evalJs("window.viceVoice && window.viceVoice.sendToPeer(${quoteJsString(peerId)}, ${quoteJsString(msg)});")
    val updated = _uiState.value.remoteSquadPeers.filterNot { it.id == peerId }
    _uiState.update { it.copy(remoteSquadPeers = updated) }
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
        _uiState.update { it.copy(statusDetail = "$name joined squad!") }
        onSquadNotice?.invoke("$name JOINED SQUAD!")
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
        _uiState.update {
          it.copy(
            remoteSquadPeers = updated,
            statusDetail = if (type == "SQUAD_DECLINE") "$senderPeer declined" else "$senderPeer left squad",
          )
        }
        onRemoteSquadChanged?.invoke(updated)
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

  // Bridge callbacks invoked from JS (or unit tests)
  internal fun handlePeerOpen(peerId: String) {
    _uiState.update {
      it.copy(
        myPeerId = peerId,
        status = if (it.connectedRemoteId != null) it.status else VoiceCallStatus.READY,
        statusDetail = if (it.connectedRemoteId != null) it.statusDetail else "Ready · Share ID or invite",
      )
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
    _uiState.update {
      it.copy(
        connectedRemoteId = remoteId,
        status = VoiceCallStatus.CONNECTED,
        statusDetail = "Live voice · $remoteId",
      )
    }
  }

  internal fun handleCallClosed(reason: String) {
    _uiState.update {
      it.copy(
        connectedRemoteId = null,
        status = if (it.myPeerId.isNotBlank()) VoiceCallStatus.READY else VoiceCallStatus.DISCONNECTED,
        statusDetail = reason.ifBlank { "Call ended" },
      )
    }
  }

  internal fun handlePeerError(message: String) {
    _uiState.update {
      it.copy(
        connectedRemoteId = null,
        status = VoiceCallStatus.ERROR,
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
  }

  companion object {
    const val BASE_URL = "https://vicehorizon.local/"
    const val PEERJS_CDN_PRIMARY = "https://unpkg.com/peerjs@1.5.4/dist/peerjs.min.js"
    const val PEERJS_CDN_FALLBACK = "https://cdn.jsdelivr.net/npm/peerjs@1.5.4/dist/peerjs.min.js"

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
      </head>
      <body>
        <audio id="remoteAudio" autoplay playsinline></audio>
        <script>
          (function() {
            var peer = null;
            var activeCall = null;
            var localStream = null;
            var currentMuted = false;
            var pendingIncomingCall = null;
            var dataConns = {};
            var pendingPayloads = {};

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
              conn.on('error', function() {});
            }

            function randomPeerCode() {
              var digits = Math.floor(100000 + Math.random() * 900000);
              return 'vh-' + digits;
            }

            function applyMuteState() {
              if (!localStream) return;
              var tracks = localStream.getAudioTracks();
              for (var i = 0; i < tracks.length; i++) {
                tracks[i].enabled = !currentMuted;
              }
            }

            async function ensureLocalStream(mutedFlag) {
              if (typeof mutedFlag === 'boolean') {
                currentMuted = mutedFlag;
              }
              if (localStream) {
                applyMuteState();
                return localStream;
              }
              if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
                throw new Error('Microphone API unavailable');
              }
              localStream = await navigator.mediaDevices.getUserMedia({
                audio: {
                  echoCancellation: true,
                  noiseSuppression: true,
                  autoGainControl: true
                },
                video: false
              });
              applyMuteState();
              return localStream;
            }

            function bindCallEvents(call, remoteId) {
              if (activeCall && activeCall !== call) {
                try { activeCall.close(); } catch (e) {}
              }
              activeCall = call;
              notify('onCallConnecting', remoteId);

              call.on('stream', function(remoteStream) {
                var audioEl = document.getElementById('remoteAudio');
                if (audioEl) {
                  audioEl.srcObject = remoteStream;
                  var playPromise = audioEl.play();
                  if (playPromise && typeof playPromise.catch === 'function') {
                    playPromise.catch(function() {});
                  }
                }
                notify('onCallConnected', remoteId);
              });

              call.on('close', function() {
                if (activeCall === call) {
                  activeCall = null;
                  notify('onCallClosed', 'Call ended');
                }
              });

              call.on('error', function(err) {
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
              peer = preferredId ? new Peer(preferredId, { debug: 1 }) : new Peer({ debug: 1 });

              peer.on('open', function(id) {
                notify('onPeerOpen', id);
              });

              peer.on('connection', function(conn) {
                bindDataConnection(conn);
              });

              peer.on('call', async function(incomingCall) {
                var callerId = incomingCall.peer || 'Player';
                try {
                  var stream = await ensureLocalStream(currentMuted);
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
                  var stream = await ensureLocalStream(mutedFlag);
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
                var existing = dataConns[remoteId];
                if (existing && existing.open) {
                  try { existing.send(payload); } catch (e) {}
                  return;
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
                if (!peer || peer.destroyed || peer.disconnected) {
                  notify('onPeerError', 'Voice server not ready yet');
                  return;
                }
                try {
                  var stream = await ensureLocalStream(mutedFlag);
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
                applyMuteState();
              },
              disconnectCall: function() {
                if (activeCall) {
                  try { activeCall.close(); } catch (e) {}
                  activeCall = null;
                }
                var audioEl = document.getElementById('remoteAudio');
                if (audioEl) {
                  audioEl.srcObject = null;
                }
              },
              destroy: function() {
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
    kotlinx.coroutines.delay(400L)
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
