package com.example.game.state

import com.example.game.math.CollisionSolver
import com.example.game.world.CartoonPresets
import com.example.game.world.WorldData
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class LivingWorldController(private val world: WorldData) {
  var weatherMode: WeatherMode = WeatherMode.CLEAR_BREEZE
  var autoWeatherCycle: Boolean = false
  var rainIntensity: Float = 0f
  var lightningFlash: Float = 0f
  var thunderPulse: Float = 0f

  private var weatherCycleSec: Float = 0f
  private var lightningCooldownSec: Float = 4.5f
  private var rippleSpawnTimerSec: Float = 0f
  private var worldElapsedSec: Float = 0f

  val trainState = TrainRuntimeState()

  val helicopters: List<HelicopterRuntimeState> =
    listOf(
      HelicopterRuntimeState(
        id = "heli_intellect_patrol",
        waypoints =
          listOf(
            Triple(-18f, 26f, -180f),   // Intellect Center
            Triple(116f, 26f, -148f),   // Twin Bridge
            Triple(172f, 26f, -68f),    // Mortar Ruins
            Triple(188f, 26f, 22f),     // Rust Town
            Triple(76f, 24f, 14f),      // Zipway
            Triple(-72f, 24f, 6f),      // Farmtopia
            Triple(-178f, 24f, -80f),   // Museum
          ),
        bodyColor = floatArrayOf(0.14f, 0.72f, 0.88f),
        stripeColor = floatArrayOf(1.0f, 0.80f, 0.22f),
        x = -18f,
        y = 26f,
        z = -180f,
      ),
      HelicopterRuntimeState(
        id = "heli_grav_rescue",
        waypoints =
          listOf(
            Triple(-158f, 24f, 168f),   // Grav Labs
            Triple(-66f, 24f, 152f),    // Plazaria
            Triple(24f, 24f, 172f),     // Deca Square
            Triple(154f, 24f, 148f),    // Mud Site
            Triple(188f, 26f, 22f),     // Rust Town
            Triple(-218f, 26f, 6f),     // Turbine
          ),
        bodyColor = floatArrayOf(0.94f, 0.34f, 0.28f),
        stripeColor = floatArrayOf(0.96f, 0.96f, 0.98f),
        x = -158f,
        y = 24f,
        z = 168f,
      ),
    )

  val ambientAirplanes: List<AmbientAirplaneState> =
    listOf(
      AmbientAirplaneState(
        id = "sky_cruiser_1",
        centerX = 0f,
        centerZ = 0f,
        radiusX = 225f,
        radiusZ = 210f,
        altitudeY = 82f,
        angleRad = 0.4f,
        angularSpeed = 0.11f,
        bodyColor = floatArrayOf(0.96f, 0.97f, 0.99f),
        accentColor = floatArrayOf(0.16f, 0.82f, 0.96f),
      ),
      AmbientAirplaneState(
        id = "sky_cruiser_2",
        centerX = 12f,
        centerZ = -8f,
        radiusX = 250f,
        radiusZ = 235f,
        altitudeY = 92f,
        angleRad = 3.5f,
        angularSpeed = -0.09f,
        bodyColor = floatArrayOf(0.96f, 0.78f, 0.24f),
        accentColor = floatArrayOf(0.96f, 0.24f, 0.56f),
      ),
    )

  val birds: List<BirdRuntimeState> =
    buildList {
      val flockZones =
        listOf(
          Triple(-198f, 76f, BirdKind.SEAGULL),    // Grav Labs Marina Pier
          Triple(-170f, 4f, BirdKind.SEAGULL),     // Turbine Cove Bridge
          Triple(24f, 212f, BirdKind.SEAGULL),     // South Coast Deca Square
          Triple(196f, 96f, BirdKind.SEAGULL),     // East Coast Mud Site
          Triple(-48f, -118f, BirdKind.SEAGULL),   // Intellect Moat Falls
          Triple(-18f, -180f, BirdKind.EAGLE),     // Intellect Center Citadel
          Triple(156f, -158f, BirdKind.EAGLE),     // NE Twin Bridge Ridge
          Triple(-218f, 6f, BirdKind.EAGLE),       // Turbine Bluff
        )
      for ((idx, zone) in flockZones.withIndex()) {
        for (sub in 0..1) {
          val n = idx * 2 + sub
          add(
            BirdRuntimeState(
              id = "bird_$n",
              kind = zone.third,
              centerX = zone.first + (sub - 0.5f) * 14f,
              centerZ = zone.second + (if (sub == 0) -6f else 6f),
              radiusX = if (zone.third == BirdKind.EAGLE) 24f + sub * 7f else 18f + sub * 6f,
              radiusZ = if (zone.third == BirdKind.EAGLE) 18f + sub * 5f else 14f + sub * 4f,
              baseY = if (zone.third == BirdKind.EAGLE) 22f + (n % 3) * 4f else 11f + (n % 3) * 2.5f,
              angleRad = n * 0.85f,
              angularSpeed = (if (n % 2 == 0) 0.44f else -0.39f),
              flapPhase = n * 1.1f,
              wingSpan = if (zone.third == BirdKind.EAGLE) 1.55f else 1.08f,
            )
          )
        }
      }
    }

  val clouds: List<CloudPuffState> =
    List(22) { idx ->
      val angle = idx * (2f * PI.toFloat() / 22f)
      val ring = 95f + (idx % 4) * 58f
      CloudPuffState(
        x = cos(angle) * ring,
        y = 58f + (idx % 5) * 7.5f,
        z = sin(angle) * ring * 0.72f,
        scaleX = 16f + (idx % 4) * 5.5f,
        scaleY = 5.4f + (idx % 3) * 1.8f,
        scaleZ = 12f + (idx % 3) * 4.5f,
        driftSpeedX = 1.6f + (idx % 3) * 0.55f,
        driftSpeedZ = 0.35f * (if (idx % 2 == 0) 1f else -1f),
        seed = idx * 0.71f,
      )
    }

  val marketBots: List<MarketBotState> = emptyList()

  val weatherDrops =
    MutableList(90) { idx ->
      WeatherParticleState(
        x = ((idx * 37) % 52 - 26).toFloat(),
        y = ((idx * 13) % 24 + 1).toFloat(),
        z = ((idx * 53) % 52 - 26).toFloat(),
        speedY = 24f + (idx % 5) * 3.5f,
        driftX = 3.2f + (idx % 3) * 0.8f,
        length = 0.85f + (idx % 3) * 0.25f,
      )
    }

  val waterRipples = mutableListOf<WaterRippleState>()

  private val railwayMetrics: RailwayMetrics by lazy { computeRailwayMetrics(world.railwayLoopWaypoints) }

  private class RailwayMetrics(
    val waypoints: List<Pair<Float, Float>>,
    val segLengths: FloatArray,
    val totalLength: Float,
  )

  private fun computeRailwayMetrics(pts: List<Pair<Float, Float>>): RailwayMetrics {
    if (pts.size < 2) return RailwayMetrics(pts, FloatArray(0), 0f)
    val lens = FloatArray(pts.size)
    var total = 0f
    for (i in pts.indices) {
      val a = pts[i]
      val b = pts[(i + 1) % pts.size]
      val len = hypot(b.first - a.first, b.second - a.second).coerceAtLeast(0.01f)
      lens[i] = len
      total += len
    }
    return RailwayMetrics(pts, lens, total)
  }

  fun sampleRailwayAtDist(distRaw: Float): Triple<Float, Float, Float> {
    val m = railwayMetrics
    if (m.totalLength <= 0.1f || m.waypoints.size < 2) {
      return Triple(0f, -81f, 1.57f)
    }
    var d = distRaw % m.totalLength
    if (d < 0f) d += m.totalLength
    for (i in m.waypoints.indices) {
      val segLen = m.segLengths[i]
      if (d <= segLen || i == m.waypoints.lastIndex) {
        val t = (d / segLen).coerceIn(0f, 1f)
        val a = m.waypoints[i]
        val b = m.waypoints[(i + 1) % m.waypoints.size]
        val x = a.first + (b.first - a.first) * t
        val z = a.second + (b.second - a.second) * t
        val yaw = atan2((b.first - a.first), (b.second - a.second))
        return Triple(x, z, yaw)
      }
      d -= segLen
    }
    return Triple(0f, -81f, 1.57f)
  }

  fun trainCarPose(carIndex: Int): Triple<Float, Float, Float> =
    sampleRailwayAtDist(trainState.progressDist - carIndex * trainState.carSpacing)

  fun step(
    dt: Float,
    playerX: Float,
    playerZ: Float,
    isSwimming: Boolean,
    playerSpeedNorm: Float,
    boats: List<BoatRuntimeState>,
    camLookX: Float,
    camLookZ: Float,
  ) {
    worldElapsedSec += dt
    stepTrain(dt)
    stepHelicopters(dt)
    stepAmbientAirplanes(dt)
    stepBirds(dt)
    stepClouds(dt)
    stepWeather(dt, camLookX, camLookZ)
    stepWaterRipples(dt, playerX, playerZ, isSwimming, playerSpeedNorm, boats)
  }

  private fun stepTrain(dt: Float) {
    if (world.railwayLoopWaypoints.size < 2) return
    trainState.progressDist += trainState.speed * dt
    val pose = sampleRailwayAtDist(trainState.progressDist)
    trainState.x = pose.first
    trainState.z = pose.second
    trainState.yawRad = lerpAngleRad(trainState.yawRad, pose.third, min(1f, dt * 10f))
    trainState.y = 0.28f
  }

  private fun stepHelicopters(dt: Float) {
    for (heli in helicopters) {
      val target = heli.waypoints.getOrElse(heli.targetIdx) { return }
      val dx = target.first - heli.x
      val dy = target.second - heli.y
      val dz = target.third - heli.z
      val distXZ = hypot(dx, dz)

      heli.rotorAngleDeg = (heli.rotorAngleDeg + 1140f * dt) % 360f

      if (distXZ < 4.0f) {
        heli.hoverTimerSec += dt
        heli.pitchRad += (0f - heli.pitchRad) * min(1f, dt * 4f)
        heli.rollRad += (0f - heli.rollRad) * min(1f, dt * 4f)
        if (heli.hoverTimerSec >= 1.2f) {
          heli.hoverTimerSec = 0f
          heli.targetIdx = (heli.targetIdx + 1) % heli.waypoints.size
        }
      } else {
        val dirX = dx / distXZ
        val dirZ = dz / distXZ
        val moveStep = min(distXZ, heli.speed * dt)
        heli.x += dirX * moveStep
        heli.z += dirZ * moveStep
        heli.y += dy * min(1f, dt * 2.2f)

        val targetYaw = atan2(dirX, dirZ)
        val yawDelta = normalizeAngleRad(targetYaw - heli.yawRad)
        heli.yawRad = lerpAngleRad(heli.yawRad, targetYaw, min(1f, dt * 3.4f))
        heli.pitchRad += (0.14f - heli.pitchRad) * min(1f, dt * 4f)
        heli.rollRad += ((-yawDelta * 0.42f).coerceIn(-0.34f, 0.34f) - heli.rollRad) * min(1f, dt * 4f)
      }
    }
  }

  private fun stepAmbientAirplanes(dt: Float) {
    for (plane in ambientAirplanes) {
      plane.angleRad += plane.angularSpeed * dt
      val nx = plane.centerX + cos(plane.angleRad) * plane.radiusX
      val nz = plane.centerZ + sin(plane.angleRad) * plane.radiusZ
      val vx = -sin(plane.angleRad) * plane.radiusX * plane.angularSpeed
      val vz = cos(plane.angleRad) * plane.radiusZ * plane.angularSpeed
      plane.x = nx
      plane.z = nz
      plane.y = plane.altitudeY + sin(worldElapsedSec * 0.6f + plane.angleRad) * 1.4f
      val targetYaw = atan2(vx, vz)
      plane.yawRad = lerpAngleRad(plane.yawRad, targetYaw, min(1f, dt * 6f))
      plane.rollRad = if (plane.angularSpeed > 0f) -0.20f else 0.20f
    }
  }

  private fun stepBirds(dt: Float) {
    for (bird in birds) {
      bird.angleRad += bird.angularSpeed * dt
      bird.flapPhase += dt * (if (bird.kind == BirdKind.EAGLE) 6.4f else 9.8f)
      val nx = bird.centerX + cos(bird.angleRad) * bird.radiusX
      val nz = bird.centerZ + sin(bird.angleRad) * bird.radiusZ
      val vx = -sin(bird.angleRad) * bird.radiusX * bird.angularSpeed
      val vz = cos(bird.angleRad) * bird.radiusZ * bird.angularSpeed
      bird.x = nx
      bird.z = nz
      bird.y = bird.baseY + sin(bird.angleRad * 2f + bird.flapPhase * 0.25f) * 1.6f
      val targetYaw = atan2(vx, vz)
      bird.yawRad = lerpAngleRad(bird.yawRad, targetYaw, min(1f, dt * 8f))
      bird.bankRad = if (bird.angularSpeed > 0f) -0.28f else 0.28f
    }
  }

  private fun stepClouds(dt: Float) {
    for (cloud in clouds) {
      cloud.x += cloud.driftSpeedX * dt
      cloud.z += cloud.driftSpeedZ * dt
      if (cloud.x > 360f) cloud.x = -360f
      if (cloud.x < -360f) cloud.x = 360f
      if (cloud.z > 280f) cloud.z = -280f
      if (cloud.z < -280f) cloud.z = 280f
    }
  }

  private fun stepWeather(dt: Float, camLookX: Float, camLookZ: Float) {
    if (autoWeatherCycle) {
      weatherCycleSec += dt
      if (weatherCycleSec >= 32f) {
        weatherCycleSec = 0f
        val modes = WeatherMode.entries
        weatherMode = modes[(weatherMode.ordinal + 1) % modes.size]
      }
    }

    val targetRain = weatherMode.rainAmount
    rainIntensity += (targetRain - rainIntensity) * min(1f, dt * 2.4f)

    if (weatherMode == WeatherMode.THUNDERSTORM) {
      lightningCooldownSec -= dt
      if (lightningCooldownSec <= 0f) {
        lightningFlash = 1.0f
        thunderPulse = 1.0f
        lightningCooldownSec = 3.8f + Random.nextFloat() * 4.5f
      }
    }
    lightningFlash = max(0f, lightningFlash - dt * 4.2f)
    thunderPulse = max(0f, thunderPulse - dt * 0.75f)

    if (rainIntensity > 0.04f) {
      val fallMul = if (weatherMode == WeatherMode.MISTY_PETALS) 0.16f else 1.0f
      for (drop in weatherDrops) {
        drop.x += drop.driftX * dt * fallMul
        drop.y -= drop.speedY * dt * fallMul
        if (drop.y < 0.1f) {
          drop.y = 22f + Random.nextFloat() * 6f
          drop.x = camLookX + (Random.nextFloat() - 0.5f) * 54f
          drop.z = camLookZ + (Random.nextFloat() - 0.5f) * 54f
        }
      }
    }
  }

  private fun stepWaterRipples(
    dt: Float,
    playerX: Float,
    playerZ: Float,
    isSwimming: Boolean,
    playerSpeedNorm: Float,
    boats: List<BoatRuntimeState>,
  ) {
    val iter = waterRipples.iterator()
    while (iter.hasNext()) {
      val r = iter.next()
      r.ageSec += dt
      if (r.ageSec >= r.maxAgeSec) {
        iter.remove()
      }
    }

    rippleSpawnTimerSec += dt
    if (rippleSpawnTimerSec >= 0.34f) {
      rippleSpawnTimerSec = 0f
      if (waterRipples.size < 20) {
        if (isSwimming && playerSpeedNorm > 0.15f) {
          waterRipples += WaterRippleState(playerX, playerZ, maxAgeSec = 1.4f, maxRadius = 3.4f, intensity = 0.85f)
        }
        for (boat in boats) {
          if (abs(boat.forwardSpeed) > 1.8f && waterRipples.size < 20) {
            waterRipples += WaterRippleState(boat.x, boat.z, maxAgeSec = 1.7f, maxRadius = 5.4f, intensity = 0.92f)
          }
        }
        for (wf in world.waterfalls) {
          if (waterRipples.size < 20) {
            waterRipples += WaterRippleState(wf.x, wf.z + 6.5f, maxAgeSec = 1.8f, maxRadius = wf.poolRadius * 0.9f, intensity = 1.0f)
          }
        }
      }
    }
  }

  fun nearestMarketStall(x: Float, z: Float, maxDist: Float = 5.2f): MarketStallSpec? {
    var best: MarketStallSpec? = null
    var bestDist = maxDist
    for (s in world.marketStalls) {
      val d = CollisionSolver.distXZ(x, z, s.x, s.z)
      if (d < bestDist) {
        bestDist = d
        best = s
      }
    }
    return best
  }

  fun activeMarketBotSummary(): String? = null

  fun computeAmbientAudioSnapshot(
    playerX: Float,
    playerZ: Float,
    vehicles: List<VehicleRuntimeState>,
    isPlaying: Boolean,
  ): AmbientAudioSnapshot {
    if (!isPlaying) return AmbientAudioSnapshot()

    var nearestCarDist = 999f
    for (v in vehicles) {
      val speed = abs(v.forwardSpeed)
      if (speed > 1.2f) {
        nearestCarDist = min(nearestCarDist, CollisionSolver.distXZ(playerX, playerZ, v.x, v.z))
      }
    }
    val trafficLevel = (1f - nearestCarDist / 52f).coerceIn(0f, 1f) * 0.85f

    val trainLevel =
      if (world.railwayLoopWaypoints.size >= 2) {
        val trainDist = CollisionSolver.distXZ(playerX, playerZ, trainState.x, trainState.z)
        (1f - trainDist / 72f).coerceIn(0f, 1f)
      } else {
        0f
      }

    var nearestHeliDist = 999f
    for (h in helicopters) {
      nearestHeliDist = min(nearestHeliDist, CollisionSolver.distXZ(playerX, playerZ, h.x, h.z))
    }
    val helicopterLevel = (1f - nearestHeliDist / 85f).coerceIn(0f, 1f)

    var nearestBirdDist = 999f
    for (b in birds) {
      nearestBirdDist = min(nearestBirdDist, CollisionSolver.distXZ(playerX, playerZ, b.x, b.z))
    }
    val birdLevel = (1f - nearestBirdDist / 58f).coerceIn(0f, 1f)

    val marketDist = CollisionSolver.distXZ(playerX, playerZ, world.marketCenter.first, world.marketCenter.second)
    val marketPeopleLevel = (1f - marketDist / 65f).coerceIn(0f, 1f)

    var nearestWaterfallDist = 999f
    for (wf in world.waterfalls) {
      nearestWaterfallDist = min(nearestWaterfallDist, CollisionSolver.distXZ(playerX, playerZ, wf.x, wf.z + 6f))
    }
    val waterfallLevel = (1f - nearestWaterfallDist / 58f).coerceIn(0f, 1f)
    val coastDist = com.example.game.world.TerrainHeightfield.signedCoastDistance(playerX, playerZ)
    val nearCoast = coastDist > -18f
    val surfBase = if (nearCoast) 0.42f else 0.14f
    val waterSurfLevel = max(surfBase, waterfallLevel)

    val rainLevel = if (weatherMode == WeatherMode.MISTY_PETALS) 0.12f else rainIntensity

    return AmbientAudioSnapshot(
      trafficLevel = trafficLevel,
      trainLevel = trainLevel,
      helicopterLevel = helicopterLevel,
      birdLevel = birdLevel,
      marketPeopleLevel = marketPeopleLevel,
      waterSurfLevel = waterSurfLevel,
      rainLevel = rainLevel,
      thunderPulse = thunderPulse,
    )
  }

  private fun normalizeAngleRad(angle: Float): Float {
    var a = angle
    val twoPi = (2.0 * PI).toFloat()
    while (a > PI.toFloat()) a -= twoPi
    while (a < -PI.toFloat()) a += twoPi
    return a
  }

  private fun lerpAngleRad(from: Float, to: Float, alpha: Float): Float {
    val diff = normalizeAngleRad(to - from)
    return normalizeAngleRad(from + diff * alpha)
  }
}
