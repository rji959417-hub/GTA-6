package com.example.game.state

import androidx.lifecycle.ViewModel
import com.example.game.audio.SoundEngine
import com.example.game.world.WorldBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ViceGameViewModel : ViewModel() {
  val world = WorldBuilder.buildKeyBayside()
  val simulation = GameSimulation(world)
  private val soundEngine = SoundEngine()

  private val _hudState = MutableStateFlow(simulation.snapshotHud(isMuted = false))
  val hudState: StateFlow<HudSnapshot> = _hudState.asStateFlow()

  init {
    soundEngine.start()
  }

  fun refreshHudFromGlThread() {
    val snap = simulation.snapshotHud(isMuted = soundEngine.isMuted)
    soundEngine.update(
      playing = snap.isPlaying && !snap.isPaused,
      driving = snap.isDriving,
      speedKmh = snap.speedKmh,
      drifting = snap.isDrifting,
    )
    _hudState.value = snap
  }

  fun toggleMute() {
    soundEngine.isMuted = !soundEngine.isMuted
    refreshHudFromGlThread()
  }

  override fun onCleared() {
    super.onCleared()
    soundEngine.stop()
  }
}
