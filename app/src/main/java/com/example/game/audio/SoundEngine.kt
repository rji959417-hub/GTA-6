package com.example.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class CombatSfxEvent {
  PUNCH,
  KICK,
  GUN_LIGHT,
  GUN_HEAVY,
  HIT,
  HEADSHOT,
  CASH,
  FRIEND,
  CLIMB,
  LAND_ROLL,
  LAND_HEAVY,
}

/**
 * Lightweight procedural synthesizer for vehicle engine hum, coastal breeze ambience,
 * and low-latency combat / gun / cash / companion SFX bursts with zero external audio assets.
 */
class SoundEngine {
  @Volatile var isMuted: Boolean = false
  @Volatile private var running: Boolean = false
  @Volatile private var isDriving: Boolean = false
  @Volatile private var speedRatio: Float = 0f
  @Volatile private var isDrifting: Boolean = false
  @Volatile private var isPlaying: Boolean = false

  private val sfxLock = Any()
  private var punchTime = -1.0
  private var kickTime = -1.0
  private var gunTime = -1.0
  private var gunHeavy = false
  private var hitTime = -1.0
  private var cashTime = -1.0
  private var climbTime = -1.0
  private var landTime = -1.0
  private var landHeavy = false
  private var punchPhase = 0.0
  private var cashPhase = 0.0

  private var audioThread: Thread? = null

  fun playCombatSfx(event: CombatSfxEvent) {
    if (isMuted) return
    synchronized(sfxLock) {
      when (event) {
        CombatSfxEvent.PUNCH -> {
          punchTime = 0.0
          punchPhase = 0.0
        }
        CombatSfxEvent.KICK -> {
          kickTime = 0.0
          punchPhase = 0.0
        }
        CombatSfxEvent.GUN_LIGHT -> {
          gunTime = 0.0
          gunHeavy = false
        }
        CombatSfxEvent.GUN_HEAVY -> {
          gunTime = 0.0
          gunHeavy = true
        }
        CombatSfxEvent.HIT -> hitTime = 0.0
        CombatSfxEvent.HEADSHOT -> {
          hitTime = 0.0
          punchTime = 0.0
        }
        CombatSfxEvent.CASH, CombatSfxEvent.FRIEND -> {
          cashTime = 0.0
          cashPhase = 0.0
        }
        CombatSfxEvent.CLIMB -> {
          climbTime = 0.0
        }
        CombatSfxEvent.LAND_ROLL -> {
          landTime = 0.0
          landHeavy = false
        }
        CombatSfxEvent.LAND_HEAVY -> {
          landTime = 0.0
          landHeavy = true
        }
      }
    }
  }

  fun start() {
    if (running) return
    running = true
    audioThread =
      thread(start = true, isDaemon = true, name = "ViceAudioSynth") {
        val sampleRate = 22050
        val dt = 1.0 / sampleRate
        val bufferSamples = 512
        var track: AudioTrack? = null
        try {
          val minBuf =
            AudioTrack.getMinBufferSize(
              sampleRate,
              AudioFormat.CHANNEL_OUT_MONO,
              AudioFormat.ENCODING_PCM_16BIT,
            )
          track =
            AudioTrack.Builder()
              .setAudioAttributes(
                AudioAttributes.Builder()
                  .setUsage(AudioAttributes.USAGE_GAME)
                  .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                  .build()
              )
              .setAudioFormat(
                AudioFormat.Builder()
                  .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                  .setSampleRate(sampleRate)
                  .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                  .build()
              )
              .setBufferSizeInBytes(maxOf(minBuf, bufferSamples * 2))
              .setTransferMode(AudioTrack.MODE_STREAM)
              .build()
          track.play()

          val pcm = ShortArray(bufferSamples)
          var phase1 = 0.0
          var phase2 = 0.0
          var noiseSeed = 1234567
          val twoPi = 2.0 * PI

          while (running) {
            if (isMuted || !isPlaying) {
              pcm.fill(0)
              track.write(pcm, 0, pcm.size)
              Thread.sleep(20)
              continue
            }

            val spd = speedRatio.coerceIn(0f, 1f)
            val driving = isDriving
            val drifting = isDrifting
            val baseFreq = if (driving) 48.0 + spd * 115.0 else 0.0

            var localPunchT: Double
            var localKickT: Double
            var localGunT: Double
            var localGunHeavy: Boolean
            var localHitT: Double
            var localCashT: Double
            var localClimbT: Double
            var localLandT: Double
            var localLandHeavy: Boolean
            synchronized(sfxLock) {
              localPunchT = punchTime
              localKickT = kickTime
              localGunT = gunTime
              localGunHeavy = gunHeavy
              localHitT = hitTime
              localCashT = cashTime
              localClimbT = climbTime
              localLandT = landTime
              localLandHeavy = landHeavy
            }

            for (i in 0 until bufferSamples) {
              noiseSeed = noiseSeed * 1664525 + 1013904223
              val white = ((noiseSeed ushr 16) and 0xFFFF) / 32768.0 - 1.0

              var sample = 0.0
              if (driving) {
                phase1 += twoPi * baseFreq * dt
                phase2 += twoPi * (baseFreq * 1.98) * dt
                if (phase1 > twoPi) phase1 -= twoPi
                if (phase2 > twoPi) phase2 -= twoPi
                val engine = sin(phase1) * 0.65 + sin(phase2) * 0.35
                val rumble = engine * (0.14 + spd * 0.16)
                val tireScreech = if (drifting) white * 0.12 else 0.0
                sample = rumble + tireScreech
              } else {
                sample = white * 0.014
              }

              // Procedural combat & gun bursts
              if (localPunchT in 0.0..0.14) {
                val env = exp(-localPunchT * 26.0)
                val freq = 175.0 * (1.0 - localPunchT * 3.8).coerceAtLeast(0.35)
                punchPhase += twoPi * freq * dt
                sample += (sin(punchPhase) * 0.45 + white * 0.22) * env
                localPunchT += dt
              }
              if (localKickT in 0.0..0.18) {
                val env = exp(-localKickT * 20.0)
                val freq = 130.0 * (1.0 - localKickT * 3.2).coerceAtLeast(0.32)
                punchPhase += twoPi * freq * dt
                sample += (sin(punchPhase) * 0.55 + white * 0.28) * env
                localKickT += dt
              }
              if (localGunT in 0.0..(if (localGunHeavy) 0.24 else 0.15)) {
                val decay = if (localGunHeavy) 16.0 else 28.0
                val env = exp(-localGunT * decay)
                val boom = sin(twoPi * (if (localGunHeavy) 78.0 else 115.0) * localGunT) * 0.42
                sample += (white * (if (localGunHeavy) 0.85 else 0.68) + boom) * env
                localGunT += dt
              }
              if (localHitT in 0.0..0.11) {
                val env = exp(-localHitT * 32.0)
                sample += (sin(twoPi * 105.0 * localHitT) * 0.48 + white * 0.30) * env
                localHitT += dt
              }
              if (localCashT in 0.0..0.24) {
                val tone = if (localCashT < 0.09) 880.0 else 1318.5
                cashPhase += twoPi * tone * dt
                val env = exp(-localCashT * 12.0) * 0.26
                sample += sin(cashPhase) * env
                localCashT += dt
              }
              if (localClimbT in 0.0..0.20) {
                val env = exp(-localClimbT * 16.0)
                val pullTone = sin(twoPi * (140.0 + localClimbT * 320.0) * localClimbT) * 0.24
                sample += (white * 0.28 + pullTone) * env
                localClimbT += dt
              }
              if (localLandT in 0.0..(if (localLandHeavy) 0.30 else 0.22)) {
                val decay = if (localLandHeavy) 11.0 else 16.0
                val thudFreq = if (localLandHeavy) 58.0 else 86.0
                val env = exp(-localLandT * decay)
                val bodyThud = sin(twoPi * thudFreq * localLandT) * (if (localLandHeavy) 0.72 else 0.48)
                sample += (bodyThud + white * (if (localLandHeavy) 0.45 else 0.26)) * env
                localLandT += dt
              }

              val clamped = (sample.coerceIn(-0.95, 0.95) * 15500.0).toInt().toShort()
              pcm[i] = clamped
            }

            synchronized(sfxLock) {
              punchTime = if (localPunchT in 0.0..0.14) localPunchT else -1.0
              kickTime = if (localKickT in 0.0..0.18) localKickT else -1.0
              val maxGun = if (localGunHeavy) 0.24 else 0.15
              gunTime = if (localGunT in 0.0..maxGun) localGunT else -1.0
              hitTime = if (localHitT in 0.0..0.11) localHitT else -1.0
              cashTime = if (localCashT in 0.0..0.24) localCashT else -1.0
              climbTime = if (localClimbT in 0.0..0.20) localClimbT else -1.0
              val maxLand = if (localLandHeavy) 0.30 else 0.22
              landTime = if (localLandT in 0.0..maxLand) localLandT else -1.0
            }

            track.write(pcm, 0, pcm.size)
          }
        } catch (_: Throwable) {
          // Gracefully silent if device/emulator audio sink is unavailable
        } finally {
          try {
            track?.stop()
            track?.release()
          } catch (_: Throwable) {}
        }
      }
  }

  fun update(playing: Boolean, driving: Boolean, speedKmh: Int, drifting: Boolean) {
    isPlaying = playing
    isDriving = driving
    speedRatio = (speedKmh / 120f).coerceIn(0f, 1f)
    isDrifting = drifting
  }

  fun stop() {
    running = false
    audioThread = null
  }
}
