package com.example.game.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.game.ai.MultilingualAiAssistant
import com.example.game.audio.SoundEngine
import com.example.game.voice.VoiceChatManager
import com.example.game.world.WorldBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ViceGameViewModel : ViewModel() {
  val world = WorldBuilder.buildKeyBayside()
  val simulation = GameSimulation(world)
  private val soundEngine = SoundEngine()
  val voiceChatManager = VoiceChatManager()
  val aiAssistant = MultilingualAiAssistant(viewModelScope)

  private val _hudState = MutableStateFlow(simulation.snapshotHud(isMuted = false))
  val hudState: StateFlow<HudSnapshot> = _hudState.asStateFlow()

  init {
    simulation.onCombatSfx = { event -> soundEngine.playCombatSfx(event) }
    voiceChatManager.onRemoteSquadChanged = { peers ->
      simulation.syncRemoteP2PSquad(peers)
      refreshHudFromGlThread()
    }
    voiceChatManager.onRemotePoseReceived = { pose ->
      simulation.updateRemotePlayerPose(pose)
    }
    voiceChatManager.onRemoteStartMatch = { zoneName ->
      val zone = LobbyDropZone.entries.firstOrNull { it.name == zoneName }
      simulation.startAirplaneDrop(zone)
      refreshHudFromGlThread()
    }
    soundEngine.start()
  }

  fun refreshHudFromGlThread() {
    val snap = simulation.snapshotHud(isMuted = soundEngine.isMuted)
    soundEngine.update(
      playing = snap.isPlaying && !snap.isPaused && !snap.isGunShopOpen,
      driving = snap.isDriving,
      speedKmh = snap.speedKmh,
      drifting = snap.isDrifting,
    )
    if (snap.isPlaying && !snap.isPaused) {
      voiceChatManager.sendInGamePoseIfReady(
        nowMs = System.currentTimeMillis(),
        x = simulation.playerX,
        y = simulation.playerY,
        z = simulation.playerZ,
        yawRad = simulation.playerYawRad,
        speedNorm = simulation.playerSpeedNorm,
        isSprinting = snap.isSprinting,
        isSitting = snap.isSitting,
        dropPhase = snap.dropPhase,
        weapon = snap.equippedWeapon,
        heroIndex = snap.heroPresetIndex,
      )
    }
    _hudState.value = snap
  }

  fun startMatchFromLobby() {
    val zone = simulation.lobbyDropZone
    voiceChatManager.broadcastStartMatch(zone.name)
    simulation.startAirplaneDrop(zone)
    refreshHudFromGlThread()
  }

  fun invitePeerById(peerId: String) {
    val snap = _hudState.value
    voiceChatManager.invitePeerToLobby(
      remoteIdInput = peerId,
      localName = snap.heroPresetName,
      heroIndex = snap.heroPresetIndex,
      weapon = snap.equippedWeapon,
    )
    refreshHudFromGlThread()
  }

  fun acceptPeerInvite() {
    val snap = _hudState.value
    voiceChatManager.acceptIncomingInvite(
      localName = snap.heroPresetName,
      heroIndex = snap.heroPresetIndex,
      weapon = snap.equippedWeapon,
    )
    refreshHudFromGlThread()
  }

  fun syncLobbyStateToPeers() {
    val snap = _hudState.value
    voiceChatManager.broadcastLobbySync(
      localName = snap.heroPresetName,
      heroIndex = snap.heroPresetIndex,
      weapon = snap.equippedWeapon,
      ready = true,
    )
  }

  fun toggleMute() {
    soundEngine.isMuted = !soundEngine.isMuted
    refreshHudFromGlThread()
  }

  override fun onCleared() {
    super.onCleared()
    simulation.onCombatSfx = null
    voiceChatManager.onRemoteSquadChanged = null
    voiceChatManager.onRemotePoseReceived = null
    voiceChatManager.onRemoteStartMatch = null
    soundEngine.stop()
    voiceChatManager.shutdown()
    aiAssistant.shutdown()
  }
}
