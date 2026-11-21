package com.example.game.state

import com.example.game.math.CircleCollider
import com.example.game.math.CollisionSolver
import com.example.game.math.Vec3
import com.example.game.math.angleBetweenXZ
import com.example.game.math.lerpAngleRad
import com.example.game.math.normalizeAngleRad
import com.example.game.world.HouseSpec
import com.example.game.world.WorldData
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class GameSimulation(val world: WorldData) {
  val input = InputState()

  @Volatile var isPlaying: Boolean = false
  @Volatile var isPaused: Boolean = false
  @Volatile var timePreset: TimeOfDayPreset = TimeOfDayPreset.SUNSET
  @Volatile var cameraMode: CameraDistanceMode = CameraDistanceMode.STANDARD

  // Player kinematic state
  var playerX: Float = world.playerSpawnX
  var playerY: Float = 0f
  var playerZ: Float = world.playerSpawnZ
  var playerVelY: Float = 0f
  var playerYawRad: Float = world.playerSpawnYaw
  var playerWalkCycle: Float = 0f
  var playerSpeedNorm: Float = 0f
  var playerArmReachNorm: Float = 0f // 0..1 when pulling driver out of car!
  var isGrounded: Boolean = true
  var isSwimming: Boolean = false

  // Cars (player + autonomous AI road traffic)
  val vehicles: List<VehicleRuntimeState> =
    world.vehicleSpawns.map {
      VehicleRuntimeState(
        id = it.id,
        displayName = it.displayName,
        x = it.x,
        z = it.z,
        yawRad = it.yawRad,
        bodyColor = it.bodyColor,
        trimColor = it.trimColor,
        maxSpeed = it.maxSpeed,
        accel = it.accel,
        isAiTraffic = it.isAiTraffic,
        waypointIndex = it.loopRouteIndex,
        usesInnerLoop = it.id == "ai_car_3" || it.id == "ai_car_4",
        driverShirtColor = it.driverShirtColor,
      )
    }

  // Boats (pier speedboats + autonomous AI ocean boats)
  val boats: List<BoatRuntimeState> =
    world.boatSpawns.map {
      BoatRuntimeState(
        id = it.id,
        displayName = it.displayName,
        x = it.x,
        z = it.z,
        yawRad = it.yawRad,
        hullColor = it.hullColor,
        accentColor = it.accentColor,
        maxSpeed = it.maxSpeed,
        isAiCruise = it.isAiCruise,
        routeRadiusX = it.routeRadiusX,
        routeRadiusZ = it.routeRadiusZ,
        routeAngle = it.routePhase,
        captainShirtColor = it.captainShirtColor,
      )
    }

  // Strolling Pedestrians
  val pedestrians: List<PedestrianRuntimeState> =
    world.pedestrianSpawns.map {
      val start = it.waypoints.first()
      PedestrianRuntimeState(
        id = it.id,
        waypoints = it.waypoints,
        shirtColor = it.shirtColor,
        pantsColor = it.pantsColor,
        speed = it.speed,
        x = start.first,
        z = start.second,
      )
    }

  // NPCs pulled out of hijacked cars/boats and knocked down onto the ground
  val ejectedNpcs = mutableListOf<EjectedNpcState>()
  var activeCarjack: CarjackSequenceState? = null

  var activeVehicle: VehicleRuntimeState? = null
  var activeBoat: BoatRuntimeState? = null
  var nearbyVehicle: VehicleRuntimeState? = null
  var nearbyBoat: BoatRuntimeState? = null
  var currentInsideHouse: HouseSpec? = null

  // Camera state
  var camYawRad: Float = world.playerSpawnYaw + PI.toFloat()
  var camPitchRad: Float = 0.28f
  var camPos: Vec3 = Vec3(playerX, 3.2f, playerZ + 6f)
  var camLookAt: Vec3 = Vec3(playerX, 1.4f, playerZ)
  var camFovDeg: Float = 62f

  var worldTimeSec: Float = 0f
  private var userLookCooldown: Float = 0f
  private var hijackCounter: Int = 0

  private val dynamicVehicleColliders = ArrayList<CircleCollider>(16)

  @Synchronized
  fun step(rawDt: Float) {
    val dt = rawDt.coerceIn(0.001f, 0.05f)
    worldTimeSec += dt

    if (!isPlaying) {
      stepAiRoadTraffic(dt)
      stepAiOceanBoats(dt)
      stepPedestrians(dt)

      val orbitAngle = worldTimeSec * 0.16f
      val radius = 52f
      camPos =
        Vec3(
          sin(orbitAngle) * radius,
          17f + sin(worldTimeSec * 0.35f) * 2.5f,
          cos(orbitAngle) * radius,
        )
      camLookAt = Vec3(-2f, 2.2f, 2f)
      camFovDeg = 58f
      return
    }

    if (isPaused) return

    val (dYaw, dPitch) = input.consumeOrbitDelta()
    if (abs(dYaw) > 0.0005f || abs(dPitch) > 0.0005f) {
      camYawRad = normalizeAngleRad(camYawRad - dYaw * 0.0065f)
      camPitchRad = (camPitchRad + dPitch * 0.0052f).coerceIn(-0.08f, 1.08f)
      userLookCooldown = 1.6f
    } else if (userLookCooldown > 0f) {
      userLookCooldown = max(0f, userLookCooldown - dt)
    }

    stepAiRoadTraffic(dt)
    stepAiOceanBoats(dt)
    stepPedestrians(dt)
    stepEjectedNpcs(dt)

    val carjack = activeCarjack
    val boat = activeBoat
    val car = activeVehicle
    when {
      carjack != null -> stepCarjackSequence(carjack, dt)
      boat != null -> stepDrivingBoat(boat, dt)
      car != null -> stepDrivingCar(car, dt)
      else -> stepOnFoot(dt)
    }

    updateHouseDetection()
    updateCamera(dt)
  }

  private fun stepCarjackSequence(seq: CarjackSequenceState, dt: Float) {
    seq.elapsedSec += dt
    val t = (seq.elapsedSec / seq.durationSec).coerceIn(0f, 1f)
    nearbyVehicle = null
    nearbyBoat = null

    val targetCar = seq.vehicleId?.let { id -> vehicles.firstOrNull { it.id == id } }
    val targetBoat = seq.boatId?.let { id -> boats.firstOrNull { it.id == id } }
    val ejected = ejectedNpcs.firstOrNull { it.id == seq.npcId }

    if (targetCar != null) {
      // Keep car stationary while pulling driver out
      targetCar.forwardSpeed *= 0.8f
      targetCar.isBraking = true

      // Left-hand driver door position & outward throw direction
      val leftX = -cos(targetCar.yawRad)
      val leftZ = sin(targetCar.yawRad)
      val doorX = targetCar.x + leftX * 1.48f
      val doorZ = targetCar.z + leftZ * 1.48f
      val seatX = targetCar.x + leftX * 0.34f
      val seatZ = targetCar.z + leftZ * 0.34f

      when {
        t < 0.32f -> {
          // Phase 1: Step up to driver door and reach hands into cabin
          val p = t / 0.32f
          playerX = seq.startX + (doorX - seq.startX) * p
          playerZ = seq.startZ + (doorZ - seq.startZ) * p
          playerY = computeGroundHeight(playerX, playerZ)
          playerYawRad = angleBetweenXZ(seatX - doorX, seatZ - doorZ)
          playerArmReachNorm = p
          playerSpeedNorm = 0.6f
          playerWalkCycle += dt * 10f

          if (ejected != null) {
            ejected.x = seatX
            ejected.z = seatZ
            ejected.y = 0.55f
            ejected.fallProgress = 0f
          }
        }
        t < 0.72f -> {
          // Phase 2: Yank NPC out of driver seat & shove them down onto road!
          val p = (t - 0.32f) / 0.40f
          playerX = doorX
          playerZ = doorZ
          playerY = computeGroundHeight(playerX, playerZ)
          playerYawRad = angleBetweenXZ(leftX, leftZ) // Turn outward as you throw them!
          playerArmReachNorm = 1.0f - p * 0.3f
          playerSpeedNorm = 0.3f

          if (ejected != null) {
            // Pull from seat -> past door -> tossed 2.2m outward & falling flat
            ejected.x = seatX + leftX * (0.4f + p * 2.1f)
            ejected.z = seatZ + leftZ * (0.4f + p * 2.1f)
            val arcLift = sin(p * PI.toFloat()) * 0.45f
            val groundY = computeGroundHeight(ejected.x, ejected.z)
            ejected.y = groundY + (1f - p) * 0.45f + arcLift
            ejected.fallProgress = (p * 1.15f).coerceIn(0f, 1f)
            ejected.yawRad = normalizeAngleRad(targetCar.yawRad + PI.toFloat() * 0.5f)
          }
        }
        else -> {
          // Phase 3: Hop smoothly into the vacated driver's seat!
          val p = (t - 0.72f) / 0.28f
          playerX = doorX + (seatX - doorX) * p
          playerZ = doorZ + (seatZ - doorZ) * p
          playerY = computeGroundHeight(playerX, playerZ) + sin(p * PI.toFloat()) * 0.35f
          playerYawRad = lerpAngleRad(playerYawRad, targetCar.yawRad, (dt * 12f).coerceAtMost(1f))
          playerArmReachNorm = (1f - p).coerceAtLeast(0f)
        }
      }

      if (t >= 1f) {
        playerArmReachNorm = 0f
        activeCarjack = null
        activeVehicle = targetCar
        playerX = targetCar.x
        playerY = 0.25f
        playerZ = targetCar.z
        playerYawRad = targetCar.yawRad
        camYawRad = normalizeAngleRad(targetCar.yawRad + PI.toFloat())
        camPitchRad = 0.24f
      }
    } else if (targetBoat != null) {
      targetBoat.forwardSpeed *= 0.8f
      val leftX = -cos(targetBoat.yawRad)
      val leftZ = sin(targetBoat.yawRad)
      val sideX = targetBoat.x + leftX * 1.6f
      val sideZ = targetBoat.z + leftZ * 1.6f

      if (t < 0.65f) {
        val p = t / 0.65f
        playerX = seq.startX + (sideX - seq.startX) * p
        playerZ = seq.startZ + (sideZ - seq.startZ) * p
        playerYawRad = angleBetweenXZ(targetBoat.x - playerX, targetBoat.z - playerZ)
        playerArmReachNorm = 1f
        if (ejected != null) {
          ejected.x = targetBoat.x + leftX * (0.4f + p * 2.6f)
          ejected.z = targetBoat.z + leftZ * (0.4f + p * 2.6f)
          ejected.y = -0.35f + sin(p * PI.toFloat()) * 0.75f - p * 0.45f
          ejected.fallProgress = p
        }
      } else {
        val p = (t - 0.65f) / 0.35f
        playerX = sideX + (targetBoat.x - sideX) * p
        playerZ = sideZ + (targetBoat.z - sideZ) * p
        playerArmReachNorm = (1f - p).coerceAtLeast(0f)
      }

      if (t >= 1f) {
        playerArmReachNorm = 0f
        activeCarjack = null
        activeBoat = targetBoat
        playerX = targetBoat.x
        playerY = targetBoat.bobY
        playerZ = targetBoat.z
        playerYawRad = targetBoat.yawRad
        camYawRad = normalizeAngleRad(targetBoat.yawRad + PI.toFloat())
        camPitchRad = 0.25f
      }
    } else {
      playerArmReachNorm = 0f
      activeCarjack = null
    }
  }

  private fun stepEjectedNpcs(dt: Float) {
    val iter = ejectedNpcs.iterator()
    while (iter.hasNext()) {
      val npc = iter.next()
      npc.ageSec += dt

      // Skip physics during initial 0.72s while carjack throw controls position directly
      if (npc.ageSec < 0.72f) continue

      if (npc.inWater) {
        npc.x += npc.vx * dt
        npc.z += npc.vz * dt
        npc.vx *= 0.92f
        npc.vz *= 0.92f
        npc.y = -0.78f + sin(worldTimeSec * 2.4f + npc.x) * 0.04f
        npc.fallProgress = 0.2f
        npc.walkCycle += dt * 4f
      } else {
        val groundY = computeGroundHeight(npc.x, npc.z)
        if (npc.y > groundY || npc.vy > 0f) {
          npc.vy -= 18f * dt
          npc.x += npc.vx * dt
          npc.z += npc.vz * dt
          npc.y += npc.vy * dt
          if (npc.y <= groundY) {
            npc.y = groundY
            npc.vy = 0f
          }
        }
        npc.vx *= 0.85f
        npc.vz *= 0.85f

        when {
          npc.ageSec < 3.4f -> {
            // Lying knocked down on the ground!
            npc.fallProgress = 1.0f
          }
          npc.ageSec < 4.3f -> {
            // Slowly getting back up off the road
            val standT = (npc.ageSec - 3.4f) / 0.9f
            npc.fallProgress = (1.0f - standT).coerceIn(0f, 1f)
          }
          else -> {
            // Dust off and walk away down the street
            npc.fallProgress = 0f
            val fwdX = sin(npc.yawRad)
            val fwdZ = cos(npc.yawRad)
            npc.x += fwdX * 1.9f * dt
            npc.z += fwdZ * 1.9f * dt
            npc.walkCycle += dt * 7.5f
          }
        }
      }

      if (npc.ageSec > 12f) {
        iter.remove()
      }
    }
  }

  private fun stepAiRoadTraffic(dt: Float) {
    for (v in vehicles) {
      if (!v.isAiTraffic || activeVehicle?.id == v.id) continue
      if (activeCarjack?.vehicleId == v.id) continue

      val loop = if (v.usesInnerLoop) world.roadLoopWaypointsInner else world.roadLoopWaypointsOuter
      val target = loop[v.waypointIndex % loop.size]
      val dx = target.first - v.x
      val dz = target.second - v.z
      val distToWp = hypot(dx, dz)

      if (distToWp < 3.6f) {
        v.waypointIndex = (v.waypointIndex + 1) % loop.size
      }

      val desiredYaw = angleBetweenXZ(dx, dz)
      val yawDiff = normalizeAngleRad(desiredYaw - v.yawRad)
      v.steerAngle = (yawDiff * 0.9f).coerceIn(-0.55f, 0.55f)
      v.yawRad = lerpAngleRad(v.yawRad, desiredYaw, (dt * 4.2f).coerceAtMost(1f))

      val fwdX = sin(v.yawRad)
      val fwdZ = cos(v.yawRad)
      var blockedAhead = false

      if (isPlaying && activeVehicle == null && activeBoat == null) {
        val pdx = playerX - v.x
        val pdz = playerZ - v.z
        val proj = pdx * fwdX + pdz * fwdZ
        val lat = abs(pdx * fwdZ - pdz * fwdX)
        if (proj in 0.5f..8.0f && lat < 2.3f) {
          blockedAhead = true
        }
      }

      if (!blockedAhead) {
        for (other in vehicles) {
          if (other.id == v.id) continue
          val odx = other.x - v.x
          val odz = other.z - v.z
          val proj = odx * fwdX + odz * fwdZ
          val lat = abs(odx * fwdZ - odz * fwdX)
          if (proj in 0.5f..9.5f && lat < 2.6f) {
            blockedAhead = true
            break
          }
        }
      }

      val targetSpeed = if (blockedAhead) 0f else if (abs(yawDiff) > 0.5f) 7.5f else 11.8f
      val accelRate = if (blockedAhead) 14f else 7.5f
      v.forwardSpeed += (targetSpeed - v.forwardSpeed) * (dt * accelRate).coerceAtMost(1f)
      v.isBraking = blockedAhead || v.forwardSpeed > targetSpeed + 1.0f

      v.x += fwdX * v.forwardSpeed * dt
      v.z += fwdZ * v.forwardSpeed * dt
      v.wheelSpinRad += (v.forwardSpeed / 0.36f) * dt
      v.rollRad = (v.steerAngle * (v.forwardSpeed / 22f) * 0.045f).coerceIn(-0.06f, 0.06f)
    }
  }

  private fun stepAiOceanBoats(dt: Float) {
    for (b in boats) {
      if (!b.isAiCruise || activeBoat?.id == b.id) {
        b.bobY = sin(worldTimeSec * 2.2f + b.x * 0.1f) * 0.06f
        continue
      }
      if (activeCarjack?.boatId == b.id) continue

      val angularSpeed = b.maxSpeed / ((b.routeRadiusX + b.routeRadiusZ) * 0.5f)
      b.routeAngle += angularSpeed * dt
      val nextX = cos(b.routeAngle) * b.routeRadiusX
      val nextZ = sin(b.routeAngle) * b.routeRadiusZ
      val vx = -sin(b.routeAngle) * b.routeRadiusX * angularSpeed
      val vz = cos(b.routeAngle) * b.routeRadiusZ * angularSpeed

      b.x = nextX
      b.z = nextZ
      b.yawRad = angleBetweenXZ(vx, vz)
      b.forwardSpeed = hypot(vx, vz)
      b.pitchRad = -0.05f
      b.rollRad = 0.06f
      b.bobY = sin(worldTimeSec * 2.6f + b.routeAngle * 3f) * 0.10f
      b.wakeIntensity = (b.forwardSpeed / 16f).coerceIn(0.2f, 1f)
    }
  }

  private fun stepPedestrians(dt: Float) {
    for (ped in pedestrians) {
      val wp = ped.waypoints[ped.targetIdx]
      val dx = wp.first - ped.x
      val dz = wp.second - ped.z
      val dist = hypot(dx, dz)

      if (dist < 0.8f) {
        if (ped.waypoints.size == 2) {
          ped.targetIdx = 1 - ped.targetIdx
        } else {
          ped.targetIdx = (ped.targetIdx + 1) % ped.waypoints.size
        }
        continue
      }

      if (isPlaying && activeVehicle == null && activeBoat == null) {
        val pd = CollisionSolver.distXZ(ped.x, ped.z, playerX, playerZ)
        if (pd < 1.15f) continue
      }

      val nx = dx / dist
      val nz = dz / dist
      ped.yawRad = lerpAngleRad(ped.yawRad, angleBetweenXZ(nx, nz), (dt * 8f).coerceAtMost(1f))
      ped.x += nx * ped.speed * dt
      ped.z += nz * ped.speed * dt
      ped.walkCycle += dt * ped.speed * 4.0f
    }
  }

  private fun stepOnFoot(dt: Float) {
    playerArmReachNorm = 0f
    val mx = input.moveX.coerceIn(-1f, 1f)
    val my = input.moveY.coerceIn(-1f, 1f)
    val mag = hypot(mx, my).coerceAtMost(1f)

    val inWater = CollisionSolver.isInDeepWater(playerX, playerZ)
    isSwimming = inWater

    val sprinting = input.sprintHeld && mag > 0.2f
    val targetSpeed =
      when {
        inWater -> if (sprinting) 5.2f else 3.4f
        sprinting -> 9.8f
        else -> 4.9f
      }
    val speed = mag * targetSpeed

    if (mag > 0.08f) {
      val fwdX = -sin(camYawRad)
      val fwdZ = -cos(camYawRad)
      val rightX = cos(camYawRad)
      val rightZ = -sin(camYawRad)

      val wishX = rightX * mx + fwdX * (-my)
      val wishZ = rightZ * mx + fwdZ * (-my)
      val wishLen = hypot(wishX, wishZ).coerceAtLeast(1e-5f)
      val dirX = wishX / wishLen
      val dirZ = wishZ / wishLen

      val targetYaw = angleBetweenXZ(dirX, dirZ)
      playerYawRad = lerpAngleRad(playerYawRad, targetYaw, (dt * 12f).coerceAtMost(1f))

      playerX += dirX * speed * dt
      playerZ += dirZ * speed * dt
      playerWalkCycle += dt * (if (sprinting) 13.5f else 8.2f) * mag
      playerSpeedNorm = if (sprinting) 1f else 0.55f * mag
    } else {
      playerSpeedNorm = (playerSpeedNorm - dt * 6f).coerceAtLeast(0f)
    }

    if (input.jumpQueued) {
      input.jumpQueued = false
      if (isGrounded && !inWater) {
        playerVelY = 7.4f
        isGrounded = false
      }
    }

    if (inWater) {
      val swimY = -0.78f + sin(worldTimeSec * 2.4f) * 0.04f
      playerY += (swimY - playerY) * (dt * 8f).coerceAtMost(1f)
      playerVelY = 0f
      isGrounded = true
    } else {
      val floorHeight = computeGroundHeight(playerX, playerZ)
      playerVelY -= 21f * dt
      playerY += playerVelY * dt
      if (playerY <= floorHeight) {
        playerY = floorHeight
        playerVelY = 0f
        isGrounded = true
      }
    }

    dynamicVehicleColliders.clear()
    for (v in vehicles) {
      val fx = sin(v.yawRad) * 1.1f
      val fz = cos(v.yawRad) * 1.1f
      dynamicVehicleColliders += CircleCollider(v.x + fx, v.z + fz, 1.15f, v.displayName)
      dynamicVehicleColliders += CircleCollider(v.x - fx, v.z - fz, 1.15f, v.displayName)
    }
    for (p in pedestrians) {
      dynamicVehicleColliders += CircleCollider(p.x, p.z, 0.35f, "Pedestrian")
    }

    val (rx, rz, _) =
      CollisionSolver.resolveAll(
        playerX,
        playerZ,
        0.44f,
        world.aabbColliders,
        world.circleColliders + dynamicVehicleColliders,
      )
    val clamped = CollisionSolver.clampSwimmerToWorld(rx, rz)
    playerX = clamped.first
    playerZ = clamped.second

    var closestCar: VehicleRuntimeState? = null
    var closestCarDist = 5.4f
    for (v in vehicles) {
      val d = CollisionSolver.distXZ(playerX, playerZ, v.x, v.z)
      if (d < closestCarDist) {
        closestCarDist = d
        closestCar = v
      }
    }
    nearbyVehicle = closestCar

    var closestBoat: BoatRuntimeState? = null
    var closestBoatDist = 6.5f
    for (b in boats) {
      val d = CollisionSolver.distXZ(playerX, playerZ, b.x, b.z)
      if (d < closestBoatDist) {
        closestBoatDist = d
        closestBoat = b
      }
    }
    nearbyBoat = closestBoat
  }

  private fun stepDrivingCar(car: VehicleRuntimeState, dt: Float) {
    nearbyVehicle = null
    nearbyBoat = null
    isSwimming = false

    val throttle = if (input.gasHeld) 1f else 0f
    val brakeInput = if (input.brakeHeld) 1f else 0f
    val handbrake = input.handbrakeHeld

    val steerTarget =
      when {
        input.steerLeft && !input.steerRight -> 0.58f
        input.steerRight && !input.steerLeft -> -0.58f
        else -> 0f
      }
    car.steerAngle += (steerTarget - car.steerAngle) * (dt * 10f).coerceAtMost(1f)

    var longAccel = 0f
    if (throttle > 0f) longAccel += car.accel * throttle
    if (brakeInput > 0f) {
      longAccel -= if (car.forwardSpeed > 1.2f) 28f * brakeInput else 11f * brakeInput
    }
    if (handbrake) longAccel -= car.forwardSpeed * 1.8f

    val drag = car.forwardSpeed * 0.42f + car.forwardSpeed * abs(car.forwardSpeed) * 0.012f
    car.forwardSpeed += (longAccel - drag) * dt
    car.forwardSpeed = car.forwardSpeed.coerceIn(-12f, car.maxSpeed)
    if (abs(car.forwardSpeed) < 0.12f && throttle == 0f && brakeInput == 0f) {
      car.forwardSpeed = 0f
    }

    val speedAbs = abs(car.forwardSpeed)
    val steerEffectiveness = (speedAbs / 5.5f).coerceIn(0f, 1f)
    val dirSign = if (car.forwardSpeed >= -0.2f) 1f else -1f
    val yawRate = car.steerAngle * 2.55f * steerEffectiveness * dirSign * (if (handbrake) 1.38f else 1f)
    car.yawRad = normalizeAngleRad(car.yawRad + yawRate * dt)

    val targetLat =
      if (handbrake && speedAbs > 6f) -car.steerAngle * car.forwardSpeed * 0.46f
      else -car.steerAngle * car.forwardSpeed * 0.09f
    val grip = if (handbrake) 3.2f else 9.5f
    car.lateralSpeed += (targetLat - car.lateralSpeed) * (dt * grip).coerceAtMost(1f)

    car.isBraking = brakeInput > 0f || handbrake
    car.isDrifting = (handbrake && speedAbs > 6.5f) || abs(car.lateralSpeed) > 2.6f

    val fwdX = sin(car.yawRad)
    val fwdZ = cos(car.yawRad)
    val rightX = cos(car.yawRad)
    val rightZ = -sin(car.yawRad)

    car.x += (fwdX * car.forwardSpeed + rightX * car.lateralSpeed) * dt
    car.z += (fwdZ * car.forwardSpeed + rightZ * car.lateralSpeed) * dt

    val targetPitch = (-longAccel * 0.0028f).coerceIn(-0.07f, 0.07f)
    val targetRoll = (yawRate * (speedAbs / 24f) * 0.055f).coerceIn(-0.08f, 0.08f)
    car.pitchRad += (targetPitch - car.pitchRad) * (dt * 8f).coerceAtMost(1f)
    car.rollRad += (targetRoll - car.rollRad) * (dt * 8f).coerceAtMost(1f)
    car.wheelSpinRad += (car.forwardSpeed / 0.36f) * dt

    dynamicVehicleColliders.clear()
    for (other in vehicles) {
      if (other.id != car.id) {
        dynamicVehicleColliders += CircleCollider(other.x, other.z, 1.55f, other.displayName)
      }
    }

    val allCircles = world.circleColliders + dynamicVehicleColliders
    val frontOffset = 1.25f
    val rearOffset = -1.25f
    var hitAny = false

    val (fx, fz, hitF) =
      CollisionSolver.resolveAll(
        car.x + fwdX * frontOffset,
        car.z + fwdZ * frontOffset,
        1.05f,
        world.aabbColliders,
        allCircles,
      )
    if (hitF) {
      car.x = fx - fwdX * frontOffset
      car.z = fz - fwdZ * frontOffset
      hitAny = true
    }

    val (rx, rz, hitR) =
      CollisionSolver.resolveAll(
        car.x + fwdX * rearOffset,
        car.z + fwdZ * rearOffset,
        1.05f,
        world.aabbColliders,
        allCircles,
      )
    if (hitR) {
      car.x = rx - fwdX * rearOffset
      car.z = rz - fwdZ * rearOffset
      hitAny = true
    }

    if (hitAny) {
      car.forwardSpeed *= 0.55f
      car.lateralSpeed *= 0.4f
    }

    val clamped = CollisionSolver.clampCarToIsland(car.x, car.z, margin = 2.0f)
    if (clamped.first != car.x || clamped.second != car.z) {
      car.x = clamped.first
      car.z = clamped.second
      car.forwardSpeed *= 0.6f
    }

    playerX = car.x
    playerY = 0.25f
    playerZ = car.z
    playerYawRad = car.yawRad
  }

  private fun stepDrivingBoat(boat: BoatRuntimeState, dt: Float) {
    nearbyVehicle = null
    nearbyBoat = null
    isSwimming = false

    val throttle = if (input.gasHeld) 1f else 0f
    val revInput = if (input.brakeHeld) 1f else 0f
    val boostTurn = input.handbrakeHeld

    val steerTarget =
      when {
        input.steerLeft && !input.steerRight -> 0.65f
        input.steerRight && !input.steerLeft -> -0.65f
        else -> 0f
      }
    boat.steerAngle += (steerTarget - boat.steerAngle) * (dt * 7f).coerceAtMost(1f)

    var thrust = 0f
    if (throttle > 0f) thrust += 16.5f * throttle
    if (revInput > 0f) thrust -= if (boat.forwardSpeed > 1f) 18f * revInput else 8.5f * revInput

    val waterDrag = boat.forwardSpeed * 0.52f + boat.forwardSpeed * abs(boat.forwardSpeed) * 0.015f
    boat.forwardSpeed += (thrust - waterDrag) * dt
    boat.forwardSpeed = boat.forwardSpeed.coerceIn(-9f, boat.maxSpeed)
    if (abs(boat.forwardSpeed) < 0.1f && throttle == 0f && revInput == 0f) boat.forwardSpeed = 0f

    val speedAbs = abs(boat.forwardSpeed)
    val steerFactor = (0.35f + speedAbs / 8f).coerceIn(0.35f, 1.15f)
    val dirSign = if (boat.forwardSpeed >= -0.2f) 1f else -1f
    val yawRate = boat.steerAngle * 1.95f * steerFactor * dirSign * (if (boostTurn) 1.35f else 1f)
    boat.yawRad = normalizeAngleRad(boat.yawRad + yawRate * dt)

    val targetLat = -boat.steerAngle * boat.forwardSpeed * 0.22f
    boat.lateralSpeed += (targetLat - boat.lateralSpeed) * (dt * 4.5f).coerceAtMost(1f)

    val fwdX = sin(boat.yawRad)
    val fwdZ = cos(boat.yawRad)
    val rightX = cos(boat.yawRad)
    val rightZ = -sin(boat.yawRad)

    boat.x += (fwdX * boat.forwardSpeed + rightX * boat.lateralSpeed) * dt
    boat.z += (fwdZ * boat.forwardSpeed + rightZ * boat.lateralSpeed) * dt

    val speedRatio = (speedAbs / boat.maxSpeed).coerceIn(0f, 1f)
    val targetPitch = (-speedRatio * 0.09f) + (if (revInput > 0f) 0.04f else 0f)
    val targetRoll = (yawRate * (0.04f + speedRatio * 0.12f)).coerceIn(-0.22f, 0.22f)
    boat.pitchRad += (targetPitch - boat.pitchRad) * (dt * 6f).coerceAtMost(1f)
    boat.rollRad += (targetRoll - boat.rollRad) * (dt * 6f).coerceAtMost(1f)
    boat.bobY = sin(worldTimeSec * 2.8f + boat.x * 0.12f) * (0.08f + speedRatio * 0.05f)
    boat.wakeIntensity = speedRatio

    val (bx, bz, hitWaterEdge) = CollisionSolver.resolveBoatWaterBounds(boat.x, boat.z)
    boat.x = bx
    boat.z = bz
    if (hitWaterEdge) {
      boat.forwardSpeed *= 0.45f
    }

    for (other in boats) {
      if (other.id == boat.id) continue
      CollisionSolver.resolveCircleVsCircle(boat.x, boat.z, 2.2f, other.x, other.z, 2.2f)?.let {
        boat.x += it.first
        boat.z += it.second
        boat.forwardSpeed *= 0.6f
      }
    }

    playerX = boat.x
    playerY = boat.bobY
    playerZ = boat.z
    playerYawRad = boat.yawRad
  }

  private fun computeGroundHeight(x: Float, z: Float): Float {
    for (h in world.houses) {
      val hw = h.width * 0.5f + 0.6f
      val hd = h.depth * 0.5f + 0.8f
      if (x in (h.centerX - hw)..(h.centerX + hw) && z in (h.centerZ - hd)..(h.centerZ + hd)) {
        return 0.14f
      }
    }
    if (CollisionSolver.isOnPierOrDock(x, z)) {
      return 0.24f
    }
    if ((abs(z - 16f) < 5f || abs(z + 24f) < 5f) && abs(x) < 47f) return 0.04f
    if ((abs(x + 42f) < 5f || abs(x - 42f) < 5f) && z in -29f..21f) return 0.04f
    if (abs(x) < 4.6f && z in -21f..13f) return 0.04f
    return 0f
  }

  private fun updateHouseDetection() {
    if (activeVehicle != null || activeBoat != null || activeCarjack != null) {
      currentInsideHouse = null
      return
    }
    var inside: HouseSpec? = null
    for (h in world.houses) {
      val hw = h.width * 0.5f - 0.35f
      val hd = h.depth * 0.5f - 0.35f
      if (playerX in (h.centerX - hw)..(h.centerX + hw) &&
        playerZ in (h.centerZ - hd)..(h.centerZ + hd)
      ) {
        inside = h
        break
      }
    }
    currentInsideHouse = inside
  }

  private fun updateCamera(dt: Float) {
    val carjack = activeCarjack
    val boat = activeBoat
    val car = activeVehicle
    when {
      carjack != null -> {
        val targetCar = carjack.vehicleId?.let { id -> vehicles.firstOrNull { it.id == id } }
        val targetBoat = carjack.boatId?.let { id -> boats.firstOrNull { it.id == id } }
        val cx = targetCar?.x ?: targetBoat?.x ?: playerX
        val cz = targetCar?.z ?: targetBoat?.z ?: playerZ
        val targetLook = Vec3((cx + playerX) * 0.5f, 1.1f, (cz + playerZ) * 0.5f)
        val dist = 5.4f
        val camX = targetLook.x + sin(camYawRad) * cos(0.28f) * dist
        val camZ = targetLook.z + cos(camYawRad) * cos(0.28f) * dist
        val camY = targetLook.y + sin(0.28f) * dist + 0.5f
        val smooth = (dt * 12f).coerceAtMost(1f)
        camPos = Vec3.lerp(camPos, Vec3(camX, camY, camZ), smooth)
        camLookAt = Vec3.lerp(camLookAt, targetLook, smooth)
      }
      boat != null -> {
        if (userLookCooldown <= 0f && abs(boat.forwardSpeed) > 1.2f) {
          val desiredCamYaw =
            if (boat.forwardSpeed >= 0f) normalizeAngleRad(boat.yawRad + PI.toFloat())
            else boat.yawRad
          camYawRad = lerpAngleRad(camYawRad, desiredCamYaw, (dt * 3.0f).coerceAtMost(1f))
        }
        val speedRatio = (abs(boat.forwardSpeed) / boat.maxSpeed).coerceIn(0f, 1f)
        val dist = cameraMode.carDist + 1.8f + speedRatio * 2.0f
        val targetLook = Vec3(boat.x, 1.0f, boat.z)
        val camX = targetLook.x + sin(camYawRad) * cos(camPitchRad) * dist
        val camZ = targetLook.z + cos(camYawRad) * cos(camPitchRad) * dist
        val camY = (targetLook.y + sin(camPitchRad) * dist + 1.1f).coerceAtLeast(1.3f)

        val smooth = (dt * 11f).coerceAtMost(1f)
        camPos = Vec3.lerp(camPos, Vec3(camX, camY, camZ), smooth)
        camLookAt = Vec3.lerp(camLookAt, targetLook, smooth)
        camFovDeg += ((64f + speedRatio * 10f) - camFovDeg) * (dt * 6f).coerceAtMost(1f)
      }
      car != null -> {
        if (userLookCooldown <= 0f && abs(car.forwardSpeed) > 1.2f) {
          val desiredCamYaw =
            if (car.forwardSpeed >= 0f) normalizeAngleRad(car.yawRad + PI.toFloat())
            else car.yawRad
          camYawRad = lerpAngleRad(camYawRad, desiredCamYaw, (dt * 3.4f).coerceAtMost(1f))
        }
        val speedRatio = (abs(car.forwardSpeed) / car.maxSpeed).coerceIn(0f, 1f)
        val dist = cameraMode.carDist + speedRatio * 1.4f
        val targetLook = Vec3(car.x, 1.22f, car.z)
        val camX = targetLook.x + sin(camYawRad) * cos(camPitchRad) * dist
        val camZ = targetLook.z + cos(camYawRad) * cos(camPitchRad) * dist
        val camY = (targetLook.y + sin(camPitchRad) * dist + 0.65f).coerceAtLeast(1.0f)

        val smooth = (dt * 12f).coerceAtMost(1f)
        camPos = Vec3.lerp(camPos, Vec3(camX, camY, camZ), smooth)
        camLookAt = Vec3.lerp(camLookAt, targetLook, smooth)
        camFovDeg += ((62f + speedRatio * 11f) - camFovDeg) * (dt * 6f).coerceAtMost(1f)
      }
      else -> {
        val inside = currentInsideHouse != null
        val dist = if (inside) min(cameraMode.footDist, 3.1f) else cameraMode.footDist
        val pitch = if (inside) camPitchRad.coerceIn(-0.05f, 0.36f) else camPitchRad
        val targetLook = Vec3(playerX, playerY + 1.48f, playerZ)
        val camX = targetLook.x + sin(camYawRad) * cos(pitch) * dist
        val camZ = targetLook.z + cos(camYawRad) * cos(pitch) * dist
        val maxH = if (inside) 3.75f else 18f
        val camY = (targetLook.y + sin(pitch) * dist).coerceIn(0.65f, maxH)

        val smooth = (dt * 14f).coerceAtMost(1f)
        camPos = Vec3.lerp(camPos, Vec3(camX, camY, camZ), smooth)
        camLookAt = Vec3.lerp(camLookAt, targetLook, smooth)
        camFovDeg += (60f - camFovDeg) * (dt * 6f).coerceAtMost(1f)
      }
    }
  }

  @Synchronized
  fun toggleEnterExitVehicleOrBoat(): Boolean {
    if (activeCarjack != null) return false

    val currentBoat = activeBoat
    if (currentBoat != null) {
      val dockDist = CollisionSolver.distXZ(currentBoat.x, currentBoat.z, 22f, 82.5f)
      if (dockDist < 12f) {
        playerX = 22f
        playerZ = 81.5f
        playerY = 0.24f
        isSwimming = false
      } else {
        val leftX = -cos(currentBoat.yawRad)
        val leftZ = sin(currentBoat.yawRad)
        playerX = currentBoat.x + leftX * 2.5f
        playerZ = currentBoat.z + leftZ * 2.5f
        playerY = -0.78f
        isSwimming = CollisionSolver.isInDeepWater(playerX, playerZ)
      }
      currentBoat.forwardSpeed *= 0.25f
      currentBoat.wakeIntensity = 0f
      activeBoat = null
      return false
    }

    val currentCar = activeVehicle
    if (currentCar != null) {
      val leftX = -cos(currentCar.yawRad)
      val leftZ = sin(currentCar.yawRad)
      val exitCandidateX = currentCar.x + leftX * 2.15f
      val exitCandidateZ = currentCar.z + leftZ * 2.15f
      val (rx, rz, _) =
        CollisionSolver.resolveAll(
          exitCandidateX,
          exitCandidateZ,
          0.45f,
          world.aabbColliders,
          world.circleColliders,
        )
      val clamped = CollisionSolver.clampCarToIsland(rx, rz, margin = 0.6f)
      playerX = clamped.first
      playerZ = clamped.second
      playerY = computeGroundHeight(playerX, playerZ)
      playerYawRad = currentCar.yawRad
      currentCar.isBraking = false
      currentCar.isDrifting = false
      currentCar.forwardSpeed *= 0.3f
      activeVehicle = null
      return false
    }

    val targetBoat = nearbyBoat
    val targetCar = nearbyVehicle
    if (targetBoat != null && (targetCar == null || isSwimming)) {
      if (targetBoat.isAiCruise) {
        // GTA-style boat hijack: yank AI captain out into the ocean!
        targetBoat.isAiCruise = false
        targetBoat.forwardSpeed = 0f
        val leftX = -cos(targetBoat.yawRad)
        val leftZ = sin(targetBoat.yawRad)
        val npcId = "ejected_${++hijackCounter}"
        ejectedNpcs +=
          EjectedNpcState(
            id = npcId,
            x = targetBoat.x,
            y = 0.1f,
            z = targetBoat.z,
            yawRad = normalizeAngleRad(targetBoat.yawRad + (PI / 2.0).toFloat()),
            shirtColor = targetBoat.captainShirtColor,
            pantsColor = floatArrayOf(0.18f, 0.32f, 0.48f),
            vx = leftX * 2.2f,
            vy = 0f,
            vz = leftZ * 2.2f,
            inWater = true,
          )
        activeCarjack =
          CarjackSequenceState(
            boatId = targetBoat.id,
            targetName = targetBoat.displayName,
            npcId = npcId,
            startX = playerX,
            startZ = playerZ,
          )
        return true
      }
      activeBoat = targetBoat
      camYawRad = normalizeAngleRad(targetBoat.yawRad + PI.toFloat())
      camPitchRad = 0.25f
      return true
    }

    if (targetCar != null) {
      if (targetCar.isAiTraffic) {
        // GTA-style carjacking: pull the AI driver out with hands and knock them down onto the road!
        targetCar.isAiTraffic = false
        targetCar.forwardSpeed = 0f
        targetCar.isBraking = true
        val leftX = -cos(targetCar.yawRad)
        val leftZ = sin(targetCar.yawRad)
        val npcId = "ejected_${++hijackCounter}"
        ejectedNpcs +=
          EjectedNpcState(
            id = npcId,
            x = targetCar.x + leftX * 0.34f,
            y = 0.55f,
            z = targetCar.z + leftZ * 0.34f,
            yawRad = normalizeAngleRad(targetCar.yawRad + (PI / 2.0).toFloat()),
            shirtColor = targetCar.driverShirtColor,
            pantsColor = floatArrayOf(0.16f, 0.18f, 0.24f),
            vx = leftX * 1.4f,
            vy = 0f,
            vz = leftZ * 1.4f,
            inWater = false,
          )
        activeCarjack =
          CarjackSequenceState(
            vehicleId = targetCar.id,
            targetName = targetCar.displayName,
            npcId = npcId,
            startX = playerX,
            startZ = playerZ,
          )
        return true
      }

      activeVehicle = targetCar
      camYawRad = normalizeAngleRad(targetCar.yawRad + PI.toFloat())
      camPitchRad = 0.24f
      return true
    }

    return false
  }

  @Synchronized
  fun summonNearestCar() {
    if (activeVehicle != null || activeBoat != null || activeCarjack != null) return
    val car = vehicles.first()
    car.isAiTraffic = false
    val fwdX = -sin(camYawRad)
    val fwdZ = -cos(camYawRad)
    val spawnX = playerX + fwdX * 4.5f
    val spawnZ = playerZ + fwdZ * 4.5f
    val (rx, rz, _) =
      CollisionSolver.resolveAll(spawnX, spawnZ, 1.5f, world.aabbColliders, world.circleColliders)
    val clamped = CollisionSolver.clampCarToIsland(rx, rz, margin = 2.2f)
    car.x = clamped.first
    car.z = clamped.second
    car.forwardSpeed = 0f
    car.lateralSpeed = 0f
    car.yawRad = normalizeAngleRad(playerYawRad + (PI / 2.0).toFloat())
  }

  @Synchronized
  fun teleportToHouse(houseIndex: Int) {
    val h = world.houses.getOrNull(houseIndex) ?: return
    activeVehicle = null
    activeBoat = null
    activeCarjack = null
    isSwimming = false
    val doorSign = if (h.facingSouth) 1f else -1f
    playerX = h.centerX
    playerZ = h.centerZ + doorSign * (h.depth * 0.5f + 2.4f)
    playerY = 0f
    playerYawRad = if (h.facingSouth) PI.toFloat() else 0f
    camYawRad = normalizeAngleRad(playerYawRad + PI.toFloat())
  }

  @Synchronized
  fun teleportToBoatPier() {
    activeVehicle = null
    activeBoat = null
    activeCarjack = null
    isSwimming = false
    playerX = 22f
    playerZ = 81.2f
    playerY = 0.24f
    playerYawRad = 0f
    camYawRad = PI.toFloat()
    camPitchRad = 0.24f
  }

  @Synchronized
  fun resetToSpawn() {
    activeVehicle = null
    activeBoat = null
    activeCarjack = null
    ejectedNpcs.clear()
    isSwimming = false
    playerX = world.playerSpawnX
    playerY = 0f
    playerZ = world.playerSpawnZ
    playerYawRad = world.playerSpawnYaw
    camYawRad = normalizeAngleRad(playerYawRad + PI.toFloat())
    camPitchRad = 0.28f
    vehicles.forEachIndexed { idx, v ->
      val sp = world.vehicleSpawns[idx]
      v.x = sp.x
      v.z = sp.z
      v.yawRad = sp.yawRad
      v.isAiTraffic = sp.isAiTraffic
      v.waypointIndex = sp.loopRouteIndex
      v.forwardSpeed = 0f
      v.lateralSpeed = 0f
    }
    boats.forEachIndexed { idx, b ->
      val sp = world.boatSpawns[idx]
      b.x = sp.x
      b.z = sp.z
      b.yawRad = sp.yawRad
      b.isAiCruise = sp.isAiCruise
      b.routeAngle = sp.routePhase
      b.forwardSpeed = 0f
      b.lateralSpeed = 0f
      b.wakeIntensity = 0f
    }
  }

  @Synchronized
  fun snapshotHud(isMuted: Boolean): HudSnapshot {
    val boat = activeBoat
    val car = activeVehicle
    val carjack = activeCarjack
    val speedMs =
      when {
        boat != null -> hypot(boat.forwardSpeed, boat.lateralSpeed)
        car != null -> hypot(car.forwardSpeed, car.lateralSpeed)
        playerSpeedNorm > 0.1f -> playerSpeedNorm * (if (isSwimming) 5.2f else 9.8f)
        else -> 0f
      }
    val kmh = (speedMs * 3.6f).toInt()

    val zone =
      when {
        carjack != null -> "CARJACKING ${carjack.targetName.uppercase()}"
        boat != null -> "BAYSIDE OPEN OCEAN"
        isSwimming -> "SWIMMING IN BAYSIDE SEA"
        currentInsideHouse != null -> "INSIDE ${currentInsideHouse!!.name.uppercase()}"
        CollisionSolver.isOnPierOrDock(playerX, playerZ) -> "MARINA BOAT PIER"
        playerZ > 51f -> "BAYSIDE SUNSET BEACH"
        abs(playerX) < 10f && playerZ in -12f..4f -> "BAYSIDE CENTRAL PLAZA"
        abs(playerZ - 16f) < 6f || abs(playerZ + 24f) < 6f -> "SUNSET BOULEVARD"
        else -> "KEY BAYSIDE"
      }

    return HudSnapshot(
      isPlaying = isPlaying,
      isPaused = isPaused,
      isDriving = car != null || boat != null,
      isDrivingBoat = boat != null,
      isSwimming = isSwimming,
      isCarjacking = carjack != null,
      carjackTargetName = carjack?.targetName,
      playerX = playerX,
      playerZ = playerZ,
      playerYawRad = playerYawRad,
      cameraYawRad = camYawRad,
      speedKmh = kmh,
      isSprinting = car == null && boat == null && input.sprintHeld && playerSpeedNorm > 0.6f,
      isDrifting = car?.isDrifting == true,
      activeVehicleName = boat?.displayName ?: car?.displayName,
      nearbyVehicleName = nearbyVehicle?.displayName,
      nearbyVehicleOccupied = nearbyVehicle?.isAiTraffic == true,
      nearbyBoatName = nearbyBoat?.displayName,
      nearbyBoatOccupied = nearbyBoat?.isAiCruise == true,
      insideHouseName = currentInsideHouse?.name,
      zoneName = zone,
      timePreset = timePreset,
      cameraMode = cameraMode,
      isMuted = isMuted,
      vehiclesMapPos = vehicles.map { Triple(it.id, it.x, it.z) },
      boatsMapPos = boats.map { Triple(it.id, it.x, it.z) },
    )
  }
}
