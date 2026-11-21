package com.example.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

/**
 * Lightweight procedural synthesizer for subtle vehicle engine hum and coastal breeze ambience.
 * Completely self-contained with zero external audio assets and safe lifecycle handling.
 */
class SoundEngine {
  @Volatile var isMuted: Boolean = false
  @Volatile private var running: Boolean = false
  @Volatile private var isDriving: Boolean = false
  @Volatile private var speedRatio: Float = 0f
  @Volatile private var isDrifting: Boolean = false
  @Volatile private var isPlaying: Boolean = false

  private var audioThread: Thread? = null

  fun start() {
    if (running) return
    running = true
    audioThread =
      thread(start = true, isDaemon = true, name = "ViceAudioSynth") {
        val sampleRate = 22050
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
            val twoPi = 2.0 * PI

            for (i in 0 until bufferSamples) {
              noiseSeed = noiseSeed * 1664525 + 1013904223
              val white = ((noiseSeed ushr 16) and 0xFFFF) / 32768.0 - 1.0

              var sample = 0.0
              if (driving) {
                phase1 += twoPi * baseFreq / sampleRate
                phase2 += twoPi * (baseFreq * 1.98) / sampleRate
                if (phase1 > twoPi) phase1 -= twoPi
                if (phase2 > twoPi) phase2 -= twoPi
                val engine = sin(phase1) * 0.65 + sin(phase2) * 0.35
                val rumble = engine * (0.14 + spd * 0.16)
                val tireScreech = if (drifting) white * 0.12 else 0.0
                sample = rumble + tireScreech
              } else {
                // Gentle coastal breeze hush
                sample = white * 0.015
              }

              val clamped = (sample.coerceIn(-0.9, 0.9) * 14000.0).toInt().toShort()
              pcm[i] = clamped
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
