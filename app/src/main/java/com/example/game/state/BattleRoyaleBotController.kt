package com.example.game.state

import com.example.game.audio.CombatSfxEvent
import com.example.game.math.CollisionSolver
import com.example.game.math.angleBetweenXZ
import com.example.game.math.lerpAngleRad
import com.example.game.math.normalizeAngleRad
import com.example.game.world.TerrainHeightfield
import com.example.game.world.HouseSpec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class BattleRoyaleBotController(private val sim: GameSimulation) {

  private sealed interface BotTarget {
    data object PlayerTarget : BotTarget
    data class RivalBotTarget(val bot: PedestrianRuntimeState) : BotTarget
  }

  private val districtAnchors =
    listOf(
      Pair(-72f, 6f),     // Farmtopia Central
      Pair(-178f, -80f),  // Museum
      Pair(-218f, 6f),    // Turbine
      Pair(-18f, -180f),  // Intellect Center
      Pair(-158f, 168f),  // Grav Labs
      Pair(-66f, 152f),   // Plazaria
      Pair(24f, 172f),    // Deca Square
      Pair(154f, 148f),   // Mud Site
      Pair(76f, 14f),     // Zipway
      Pair(188f, 22f),    // Rust Town
      Pair(168f, -68f),   // Mortar Ruins
      Pair(116f, -148f),  // Twin Bridge
    )

  fun stepAllBots(dt: Float) {
    for (ped in sim.pedestrians) {
      if (ped.hitReactTimerSec > 0f) {
        ped.hitReactTimerSec = max(0f, ped.hitReactTimerSec - dt)
        if (ped.hitReactTimerSec <= 0f) ped.hitReactZone = HitZone.NONE
      }
      if (ped.attackCooldownSec > 0f) {
        ped.attackCooldownSec = max(0f, ped.attackCooldownSec - dt)
      }
      if (ped.glooWallCooldownSec > 0f) {
        ped.glooWallCooldownSec = max(0f, ped.glooWallCooldownSec - dt)
      }
      if (ped.vehicleCooldownSec > 0f) {
        ped.vehicleCooldownSec = max(0f, ped.vehicleCooldownSec - dt)
      }
      if (ped.gracePeriodSec > 0f) {
        ped.gracePeriodSec = max(0f, ped.gracePeriodSec - dt)
      }

      // 1. Knockback / physics impulse decay
      if (abs(ped.vx) > 0.05f || abs(ped.vz) > 0.05f) {
        val nx = ped.x + ped.vx * dt
        val nz = ped.z + ped.vz * dt
        val resolved = resolveBotMovement(nx, nz, ped.y)
        ped.x = resolved.first
        ped.z = resolved.second
        ped.vx *= 0.84f
        ped.vz *= 0.84f
      } else {
        ped.vx = 0f
        ped.vz = 0f
      }

      // 2. Squad companions recruited by the player use companion AI
      if (ped.isFriend && ped.reactionState != NpcReactionState.KNOCKED_DOWN) {
        sim.stepCompanion(ped, dt)
        ped.y = sim.computeGroundHeight(ped.x, ped.z, ped.y)
        continue
      }

      // 3. Downed / Respawn handling
      if (ped.reactionState == NpcReactionState.KNOCKED_DOWN) {
        if (ped.drivingVehicleId != null) {
          releaseBotVehicle(ped)
        }
        ped.y = sim.computeGroundHeight(ped.x, ped.z, ped.y)
        ped.fallProgress = min(1f, ped.fallProgress + 4.5f * dt)
        ped.armsUpNorm = max(0f, ped.armsUpNorm - 4f * dt)
        ped.sitProgress = 0f
        ped.healTimerSec = 0f
        ped.downedTimerSec += dt
        ped.respawnTimerSec -= dt
        if (ped.respawnTimerSec <= 0f) {
          respawnBot(ped)
        }
        continue
      }

      // 4. Driving a vehicle across roads & bridges
      if (ped.drivingVehicleId != null) {
        stepBotDrivingCar(ped, dt)
        continue
      }

      ped.y = sim.computeGroundHeight(ped.x, ped.z, ped.y)

      // 5. Preserve short hit flinch for unarmed civilians
      if (ped.reactionState == NpcReactionState.HANDS_UP_FLINCH) {
        ped.armsUpNorm = min(1f, ped.armsUpNorm + 7f * dt)
        ped.fallProgress = 0f
        ped.sitProgress = max(0f, ped.sitProgress - 6f * dt)
        ped.downedTimerSec += dt
        val toPlayerX = sim.playerX - ped.x
        val toPlayerZ = sim.playerZ - ped.z
        if (hypot(toPlayerX, toPlayerZ) > 0.1f) {
          ped.yawRad =
            lerpAngleRad(ped.yawRad, angleBetweenXZ(toPlayerX, toPlayerZ), (dt * 12f).coerceAtMost(1f))
        }
        if (ped.downedTimerSec >= 0.36f) {
          ped.downedTimerSec = 0f
          ped.reactionState = NpcReactionState.FLEEING
        }
        continue
      }

      // 6. Preserve short gunfire/punch panic sprint before bot regroups/loots
      if (ped.reactionState == NpcReactionState.FLEEING) {
        stepBotFleeing(ped, dt)
        continue
      }

      // 7. Battle Royale AI decision & execution (Combat, Gloo Walls, Healing, Cover, Looting, Cars, Roaming)
      stepBattleRoyaleBrain(ped, dt)
    }
  }

  private fun respawnBot(ped: PedestrianRuntimeState) {
    ped.x = ped.spawnX
    ped.z = ped.spawnZ
    ped.y = sim.computeGroundHeight(ped.spawnX, ped.spawnZ, 0f)
    ped.hp = ped.maxHp
    ped.fallProgress = 0f
    ped.armsUpNorm = 0f
    ped.sitProgress = 0f
    ped.hitReactZone = HitZone.NONE
    ped.hitReactTimerSec = 0f
    ped.isHostileTarget = false
    ped.healTimerSec = 0f
    ped.coverTimerSec = 0f
    ped.targetEnemyBotId = null
    ped.targetPlayer = false
    ped.targetLootUid = null
    ped.drivingVehicleId = null
    ped.roamTargetX = ped.spawnX
    ped.roamTargetZ = ped.spawnZ
    ped.roamRefreshSec = 0f
    val sp = sim.world.pedestrianSpawns.firstOrNull { it.id == ped.id }
    if (sp != null) {
      ped.weapon = sp.rivalWeapon
      ped.vestLevel = sp.vestLevel
      ped.vestDurability = armorDurabilityFor(sp.vestLevel, isHelmet = false)
      ped.helmetLevel = sp.helmetLevel
      ped.helmetDurability = armorDurabilityFor(sp.helmetLevel, isHelmet = true)
      ped.botMedkits = if (sp.isRivalFighter) 2 else 0
      ped.botGlooWalls = if (sp.isRivalFighter) 2 else 1
      ped.botBagCount = if (sp.isRivalFighter) 3 else 0
    }
    ped.botMode = BotTacticalMode.ROAMING
    ped.reactionState = if (ped.isFriend) NpcReactionState.COMPANION else NpcReactionState.STROLLING
    ped.targetIdx = 1 % ped.waypoints.size
  }

  private fun stepBotFleeing(ped: PedestrianRuntimeState, dt: Float) {
    ped.armsUpNorm = (0.55f + sin(sim.worldTimeSec * 9f + ped.spawnX) * 0.25f).coerceIn(0.2f, 0.9f)
    ped.fallProgress = 0f
    ped.sitProgress = max(0f, ped.sitProgress - 6f * dt)
    ped.panicTimerSec -= dt

    val awayX = ped.x - sim.playerX
    val awayZ = ped.z - sim.playerZ
    val distFromPlayer = hypot(awayX, awayZ).coerceAtLeast(0.05f)
    val dirX = awayX / distFromPlayer
    val dirZ = awayZ / distFromPlayer

    ped.yawRad = lerpAngleRad(ped.yawRad, angleBetweenXZ(dirX, dirZ), (dt * 11f).coerceAtMost(1f))

    val fleeSpeed = 5.6f
    val nextX = ped.x + dirX * fleeSpeed * dt
    val nextZ = ped.z + dirZ * fleeSpeed * dt
    val (rx, rz, hitWall) =
      CollisionSolver.resolveAll(
        nextX,
        nextZ,
        0.40f,
        sim.world.aabbColliders,
        sim.world.circleColliders,
        entityMinY = ped.y,
        entityMaxY = ped.y + 1.75f,
      )

    var finalX = rx
    var finalZ = rz
    if (hitWall) {
      val sideX = ped.x + (-dirZ) * fleeSpeed * dt * 0.7f
      val sideZ = ped.z + dirX * fleeSpeed * dt * 0.7f
      val sideRes =
        CollisionSolver.resolveAll(
          sideX,
          sideZ,
          0.40f,
          sim.world.aabbColliders,
          sim.world.circleColliders,
          entityMinY = ped.y,
          entityMaxY = ped.y + 1.75f,
        )
      if (hypot(sideRes.first - ped.x, sideRes.second - ped.z) > hypot(rx - ped.x, rz - ped.z)) {
        finalX = sideRes.first
        finalZ = sideRes.second
      }
    }
    val glooResolved = sim.resolveEntityVsGlooWalls(finalX, finalZ, 0.40f, ped.y)
    val clamped = CollisionSolver.clampCarToIsland(glooResolved.first, glooResolved.second, margin = 2.2f)
    ped.x = clamped.first
    ped.z = clamped.second
    ped.walkCycle += dt * 13.5f

    if (ped.panicTimerSec <= 0f && distFromPlayer > 18f) {
      ped.reactionState = NpcReactionState.STROLLING
      ped.botMode = BotTacticalMode.LOOTING
      ped.armsUpNorm = 0f
    }
  }

  private fun stepBattleRoyaleBrain(ped: PedestrianRuntimeState, dt: Float) {
    ped.armsUpNorm = max(0f, ped.armsUpNorm - 4f * dt)
    ped.fallProgress = max(0f, ped.fallProgress - 4f * dt)

    val canFight = ped.isRivalFighter || ped.weapon != WeaponType.FISTS || ped.isHostileTarget ||
      ped.reactionState == NpcReactionState.FIGHTING_BACK

    // 1. Acquire nearest combat threat (Player or Enemy AI Bot)
    val threat = if (canFight) findBestThreatForBot(ped) else null
    val threatPos = threat?.let { targetCoords(it) }
    val threatDist =
      if (threatPos != null) CollisionSolver.distXZ(ped.x, ped.z, threatPos.first, threatPos.second)
      else Float.MAX_VALUE

    // 2. Reactive Gloo Wall shield deployment when hurt or under fire at mid-range (> 4.2m)
    if (threatPos != null &&
      threatDist > 4.2f &&
      ped.botGlooWalls > 0 &&
      ped.glooWallCooldownSec <= 0f &&
      (ped.hp < 58f || (ped.hitReactTimerSec > 0.25f && ped.hp < 88f))
    ) {
      val spawned = sim.spawnGlooWallForBot(ped, threatPos.first, threatPos.second)
      if (spawned) {
        ped.botGlooWalls--
        ped.botBagCount = max(0, ped.botBagCount - 1)
        ped.glooWallCooldownSec = 6.5f
        ped.botMode = BotTacticalMode.TAKING_COVER
        ped.coverTimerSec = 2.6f
        val dx = ped.x - threatPos.first
        val dz = ped.z - threatPos.second
        val d = hypot(dx, dz).coerceAtLeast(0.1f)
        ped.coverX = ped.x + (dx / d) * 1.15f
        ped.coverZ = ped.z + (dz / d) * 1.15f
      }
    }

    // 3. Medkit Healing when wounded (< 65 HP) and carrying a Medkit
    if (ped.hp < 65f && ped.botMedkits > 0) {
      stepBotHealingAndCover(ped, threatPos, threatDist, dt)
      return
    } else {
      ped.healTimerSec = 0f
    }

    // 4. Tactical cover / hiding peek behavior when moderately hurt or briefly suppressing
    if (ped.coverTimerSec > 0f && threatPos != null && threatDist > 4.2f) {
      ped.coverTimerSec = max(0f, ped.coverTimerSec - dt)
      stepBotCoverHold(ped, threat!!, threatPos, threatDist, dt)
      return
    }

    // 5. Active Combat vs Player or Enemy Bot
    if (threat != null && threatPos != null) {
      ped.reactionState = NpcReactionState.FIGHTING_BACK
      ped.botMode = BotTacticalMode.FIGHTING
      ped.sitProgress = max(0f, ped.sitProgress - 6f * dt)
      stepBotCombat(ped, threat, threatPos, threatDist, dt)
      return
    }

    // Clear stale combat state when no threat is around
    if (ped.reactionState == NpcReactionState.FIGHTING_BACK) {
      ped.panicTimerSec -= dt
      if (ped.panicTimerSec <= 0f) {
        ped.reactionState = NpcReactionState.STROLLING
        ped.attackMove = null
        ped.targetEnemyBotId = null
        ped.targetPlayer = false
      }
    }
    ped.attackMove = null
    ped.sitProgress = max(0f, ped.sitProgress - 6f * dt)

    // 6. Auto-pickup any nearby loot touched while moving
    collectTouchingWorldLoot(ped)

    // 7. Initial grace patrol for unarmed bots so early-match patrols & sniper tests stay predictable
    if (!ped.isRivalFighter && ped.weapon == WeaponType.FISTS && sim.worldTimeSec < 3.5f) {
      stepWaypointPatrol(ped, dt)
      return
    }

    // 8. Seek & enter houses for Guns, Armor, Medkits, Coins & Gloo Walls
    val desiredLoot = findBestLootForBot(ped)
    if (desiredLoot != null && (ped.weapon == WeaponType.FISTS || ped.botMedkits < 2 || ped.vestLevel < 3 || ped.botGlooWalls < 2)) {
      ped.botMode = BotTacticalMode.LOOTING
      ped.targetLootUid = desiredLoot.uid
      moveBotTowardWorldTarget(ped, desiredLoot.x, desiredLoot.z, speed = 5.6f, dt = dt)
      collectTouchingWorldLoot(ped)
      return
    }

    // 9. Board an unoccupied parked vehicle when roaming long distances (never stealing right next to player)
    if ((ped.isRivalFighter || ped.weapon != WeaponType.FISTS) && ped.vehicleCooldownSec <= 0f && sim.worldTimeSec > 3.5f) {
      val nearbyCar = findBoardableCar(ped)
      if (nearbyCar != null) {
        val distCar = CollisionSolver.distXZ(ped.x, ped.z, nearbyCar.x, nearbyCar.z)
        if (distCar <= 2.45f) {
          boardCarAsBot(ped, nearbyCar)
          return
        } else {
          ped.botMode = BotTacticalMode.ROAMING
          moveBotTowardWorldTarget(ped, nearbyCar.x, nearbyCar.z, speed = 5.8f, dt = dt)
          return
        }
      }
    }

    // 10. Dynamic full-map Battle Royale roaming across islands & bridges
    ped.botMode = BotTacticalMode.ROAMING
    ped.roamRefreshSec -= dt
    val distRoam = CollisionSolver.distXZ(ped.x, ped.z, ped.roamTargetX, ped.roamTargetZ)
    if (ped.roamRefreshSec <= 0f || distRoam < 3.5f) {
      pickNextRoamDestination(ped)
    }
    moveBotTowardWorldTarget(ped, ped.roamTargetX, ped.roamTargetZ, speed = max(3.6f, ped.speed * 1.45f), dt = dt)
    collectTouchingWorldLoot(ped)
  }

  private fun findBestThreatForBot(ped: PedestrianRuntimeState): BotTarget? {
    var bestTarget: BotTarget? = null
    var bestScore = Float.MAX_VALUE

    // Candidate A: Player (when playing, grounded, on foot and alive)
    val playerEligible =
      sim.isPlaying &&
        !sim.isPlayerEliminated &&
        sim.dropPhase == DropPhase.GROUND &&
        sim.activeVehicle == null &&
        sim.activeBoat == null &&
        !sim.isSwimming
    if (playerEligible) {
      val d = CollisionSolver.distXZ(ped.x, ped.z, sim.playerX, sim.playerZ)
      val maxDetect = if (ped.reactionState == NpcReactionState.FIGHTING_BACK || ped.isHostileTarget) 30f else 22f
      if (d <= maxDetect && abs(ped.y - sim.playerY) < 6.0f) {
        val los = sim.hasLineOfSight(ped.x, ped.y + 1.35f, ped.z, sim.playerX, sim.playerY + 1.25f, sim.playerZ)
        if (los || ped.isHostileTarget || ped.reactionState == NpcReactionState.FIGHTING_BACK) {
          val score = d * (if (los) 0.85f else 1.25f)
          bestScore = score
          bestTarget = BotTarget.PlayerTarget
        }
      }
    }

    // Candidate B: Other AI Battle Royale Bots (Bot vs Bot firefights across islands!)
    val botCombatReady = sim.isPlaying || sim.worldTimeSec > 2.5f
    if (botCombatReady) {
      for (other in sim.pedestrians) {
        if (other.id == ped.id || other.isFriend || other.reactionState == NpcReactionState.KNOCKED_DOWN) continue
        if (other.drivingVehicleId != null) continue
        val otherIsArmedOrRival = other.isRivalFighter || other.weapon != WeaponType.FISTS || other.id == ped.targetEnemyBotId
        if (!otherIsArmedOrRival) continue
        val d = CollisionSolver.distXZ(ped.x, ped.z, other.x, other.z)
        if (d > 26f || abs(ped.y - other.y) > 5.0f) continue
        val los = sim.hasLineOfSight(ped.x, ped.y + 1.35f, ped.z, other.x, other.y + 1.25f, other.z)
        if (!los && ped.targetEnemyBotId != other.id) continue
        val score = d * (if (los) 0.95f else 1.35f)
        if (score < bestScore) {
          bestScore = score
          bestTarget = BotTarget.RivalBotTarget(other)
        }
      }
    }

    when (bestTarget) {
      is BotTarget.PlayerTarget -> {
        ped.targetPlayer = true
        ped.targetEnemyBotId = null
        ped.panicTimerSec = 18f
      }
      is BotTarget.RivalBotTarget -> {
        ped.targetPlayer = false
        ped.targetEnemyBotId = bestTarget.bot.id
        ped.panicTimerSec = 18f
      }
      null -> {
        ped.targetPlayer = false
        ped.targetEnemyBotId = null
      }
    }
    return bestTarget
  }

  private fun targetCoords(target: BotTarget): Pair<Float, Float> =
    when (target) {
      is BotTarget.PlayerTarget -> Pair(sim.playerX, sim.playerZ)
      is BotTarget.RivalBotTarget -> Pair(target.bot.x, target.bot.z)
    }

  private fun targetY(target: BotTarget): Float =
    when (target) {
      is BotTarget.PlayerTarget -> sim.playerY
      is BotTarget.RivalBotTarget -> target.bot.y
    }

  private fun stepBotHealingAndCover(
    ped: PedestrianRuntimeState,
    threatPos: Pair<Float, Float>?,
    threatDist: Float,
    dt: Float,
  ) {
    ped.botMode = BotTacticalMode.HEALING
    ped.attackMove = null

    if (threatPos != null && threatDist > 5.2f) {
      val hasExposedLos =
        sim.hasLineOfSight(ped.x, ped.y + 1.35f, ped.z, threatPos.first, ped.y + 1.25f, threatPos.second)
      if (hasExposedLos) {
        val coverSpot = findBestCoverSpot(ped, threatPos.first, threatPos.second)
        val distCover = CollisionSolver.distXZ(ped.x, ped.z, coverSpot.first, coverSpot.second)
        if (distCover > 0.95f) {
          ped.sitProgress = max(0f, ped.sitProgress - 6f * dt)
          moveBotTowardWorldTarget(ped, coverSpot.first, coverSpot.second, speed = 5.6f, dt = dt)
          return
        }
      }
    }

    // Crouch behind cover/Gloo Wall and apply Medkit
    ped.sitProgress = min(0.78f, ped.sitProgress + 5.5f * dt)
    if (threatPos != null) {
      val desiredYaw = angleBetweenXZ(threatPos.first - ped.x, threatPos.second - ped.z)
      ped.yawRad = lerpAngleRad(ped.yawRad, desiredYaw, (dt * 10f).coerceAtMost(1f))
    }
    ped.healTimerSec += dt
    if (ped.healTimerSec >= 1.25f) {
      ped.healTimerSec = 0f
      ped.botMedkits = max(0, ped.botMedkits - 1)
      ped.botBagCount = max(0, ped.botBagCount - 1)
      ped.hp = min(ped.maxHp, ped.hp + 65f)
      sim.hitSparks += HitSparkState(ped.x, ped.y + 1.25f, ped.z, floatArrayOf(0.22f, 0.94f, 0.62f), durationSec = 0.35f)
      ped.botMode = if (threatPos != null) BotTacticalMode.TAKING_COVER else BotTacticalMode.ROAMING
    }
  }

  private fun stepBotCoverHold(
    ped: PedestrianRuntimeState,
    threat: BotTarget,
    threatPos: Pair<Float, Float>,
    threatDist: Float,
    dt: Float,
  ) {
    ped.botMode = BotTacticalMode.TAKING_COVER
    val coverSpot = findBestCoverSpot(ped, threatPos.first, threatPos.second)
    val distCover = CollisionSolver.distXZ(ped.x, ped.z, coverSpot.first, coverSpot.second)
    if (distCover > 0.9f) {
      ped.sitProgress = max(0f, ped.sitProgress - 5f * dt)
      moveBotTowardWorldTarget(ped, coverSpot.first, coverSpot.second, speed = 5.4f, dt = dt)
    } else {
      ped.sitProgress = min(0.62f, ped.sitProgress + 4.5f * dt)
      val nx = (threatPos.first - ped.x) / threatDist.coerceAtLeast(0.05f)
      val nz = (threatPos.second - ped.z) / threatDist.coerceAtLeast(0.05f)
      ped.yawRad = lerpAngleRad(ped.yawRad, angleBetweenXZ(nx, nz), (dt * 10f).coerceAtMost(1f))
      // Peek & fire back if line of sight opens or enemy rushes cover
      val hasGun = ped.weapon != WeaponType.FISTS
      val ty = targetY(threat)
      val los = sim.hasLineOfSight(ped.x, ped.y + 1.35f, ped.z, threatPos.first, ty + 1.25f, threatPos.second)
      if (hasGun && los && ped.attackCooldownSec <= 0f) {
        fireBotGunAtTarget(ped, threatPos.first, ty, threatPos.second)
        ped.attackCooldownSec = max(0.55f, ped.weapon.fireIntervalSec * 2.3f)
      } else if (threatDist < 4.2f) {
        ped.coverTimerSec = 0f
      }
    }
  }

  private fun stepBotCombat(
    ped: PedestrianRuntimeState,
    threat: BotTarget,
    threatPos: Pair<Float, Float>,
    dist: Float,
    dt: Float,
  ) {
    val dx = threatPos.first - ped.x
    val dz = threatPos.second - ped.z
    val safeDist = dist.coerceAtLeast(0.05f)
    val nx = dx / safeDist
    val nz = dz / safeDist
    ped.yawRad = lerpAngleRad(ped.yawRad, angleBetweenXZ(nx, nz), (dt * 12f).coerceAtMost(1f))

    val ty = targetY(threat)
    val hasGun = (ped.isRivalFighter || ped.isBrBot) && ped.weapon != WeaponType.FISTS
    val clearLos = hasGun && dist in 3.2f..26f && sim.hasLineOfSight(ped.x, ped.y + 1.35f, ped.z, threatPos.first, ty + 1.25f, threatPos.second)

    // Continue active melee swing animation & apply damage at strike apex
    if (ped.attackMove != null) {
      ped.attackProgress += dt / ped.attackMove!!.durationSec
      if (ped.attackProgress >= 0.45f && ped.attackCooldownSec <= 0f && dist < 2.30f) {
        applyBotMeleeStrike(ped, threat, ped.attackMove!!, nx, nz)
      }
      if (ped.attackProgress >= 1f) {
        ped.attackMove = null
        ped.attackProgress = 0f
      }
      return
    }

    if (clearLos && dist > 4.2f) {
      // Tactical gunfight with lateral strafe so bots fight dynamically like Free Fire players
      ped.strafeFlipTimerSec -= dt
      if (ped.strafeFlipTimerSec <= 0f) {
        ped.strafeFlipTimerSec = 1.6f + ((ped.id.hashCode() and 3) * 0.35f)
        ped.strafeDirSign = -ped.strafeDirSign
      }
      val strafeX = -nz * ped.strafeDirSign
      val strafeZ = nx * ped.strafeDirSign
      val strafeSpeed = if (dist < 11f) 3.1f else 2.2f
      val nextX = ped.x + (strafeX * 0.85f + nx * (if (dist > 16f) 0.45f else -0.15f)) * strafeSpeed * dt
      val nextZ = ped.z + (strafeZ * 0.85f + nz * (if (dist > 16f) 0.45f else -0.15f)) * strafeSpeed * dt
      val resolved = resolveBotMovement(nextX, nextZ, ped.y)
      ped.x = resolved.first
      ped.z = resolved.second
      ped.walkCycle += dt * 7.8f

      if (ped.attackCooldownSec <= 0f) {
        fireBotGunAtTarget(ped, threatPos.first, ty, threatPos.second)
        ped.attackCooldownSec = max(0.50f, ped.weapon.fireIntervalSec * 2.3f)
      }
      return
    }

    if (dist > 1.65f) {
      val chaseSpeed = if (ped.isRivalFighter) 5.1f else 4.5f
      moveBotTowardWorldTarget(ped, threatPos.first, threatPos.second, speed = chaseSpeed, dt = dt)
    } else if (ped.attackCooldownSec <= 0f) {
      ped.attackMove =
        if (((sim.worldTimeSec * 3f).toInt() + ped.id.hashCode()) % 3 == 0) CombatMoveType.KICK_FRONT
        else if (ped.isRivalFighter) CombatMoveType.PUNCH_HOOK
        else CombatMoveType.PUNCH_JAB
      ped.attackProgress = 0f
    }
  }

  private fun applyBotMeleeStrike(
    ped: PedestrianRuntimeState,
    threat: BotTarget,
    move: CombatMoveType,
    dirX: Float,
    dirZ: Float,
  ) {
    ped.attackCooldownSec = if (ped.isRivalFighter) 0.85f else 1.0f
    when (threat) {
      is BotTarget.PlayerTarget -> {
        val strikeDmg = if (ped.isRivalFighter) 14f else 8f
        sim.applyIncomingDamageToPlayer(
          rawDamage = strikeDmg,
          hitZone = move.targetHitZone,
          attackerId = ped.id,
          attackerName = sim.rivalDisplayName(ped),
          attackerWeapon = ped.weapon,
        )
        sim.hitSparks += HitSparkState(sim.playerX, sim.playerY + 1.15f, sim.playerZ, floatArrayOf(1.0f, 0.35f, 0.2f))
        sim.onCombatSfx?.invoke(CombatSfxEvent.HIT)
      }
      is BotTarget.RivalBotTarget -> {
        val victim = threat.bot
        val strikeDmg = if (ped.isRivalFighter) 22f else 14f
        sim.applyDamageToPedestrian(
          ped = victim,
          damage = strikeDmg,
          knockbackSpeed = 3.8f,
          dirX = dirX,
          dirZ = dirZ,
          hitZone = move.targetHitZone,
          fromGunfire = false,
          fromPlayer = false,
          attackerBot = ped,
        )
        sim.onCombatSfx?.invoke(CombatSfxEvent.HIT)
      }
    }
  }

  private fun fireBotGunAtTarget(ped: PedestrianRuntimeState, tx: Float, ty: Float, tz: Float) {
    val weapon = ped.weapon
    if (weapon == WeaponType.FISTS) return
    val muzzleX = ped.x + sin(ped.yawRad) * 0.65f
    val muzzleY = ped.y + (if (ped.sitProgress > 0.3f) 0.92f else 1.24f)
    val muzzleZ = ped.z + cos(ped.yawRad) * 0.65f
    val dx = tx - muzzleX
    val dy = (ty + 1.15f) - muzzleY
    val dz = tz - muzzleZ
    val dist = hypot(hypot(dx, dz), dy).coerceAtLeast(1e-4f)
    val speed = min(58f, weapon.bulletSpeed * 0.74f)
    val dmg = (weapon.damage * 0.30f).coerceIn(8f, 18f)

    sim.hitSparks += HitSparkState(muzzleX, muzzleY, muzzleZ, floatArrayOf(1.0f, 0.62f, 0.20f), durationSec = 0.08f)
    sim.bulletProjectiles +=
      BulletProjectileState(
        x = muzzleX,
        y = muzzleY,
        z = muzzleZ,
        prevX = muzzleX,
        prevY = muzzleY,
        prevZ = muzzleZ,
        vx = (dx / dist) * speed,
        vy = (dy / dist) * speed,
        vz = (dz / dist) * speed,
        damage = dmg,
        knockback = 1.6f,
        remainingDist = 32f,
        fromEnemy = true,
        fromPlayer = false,
        attackerId = ped.id,
        attackerName = sim.rivalDisplayName(ped),
        attackerWeapon = weapon,
      )
    sim.onCombatSfx?.invoke(CombatSfxEvent.GUN_LIGHT)
  }

  private fun findBestCoverSpot(ped: PedestrianRuntimeState, threatX: Float, threatZ: Float): Pair<Float, Float> {
    val awayX = ped.x - threatX
    val awayZ = ped.z - threatZ
    val distT = hypot(awayX, awayZ).coerceAtLeast(0.1f)
    val ux = awayX / distT
    val uz = awayZ / distT

    var bestSpot: Pair<Float, Float>? = null
    var bestDist = 18f

    // 1. Prefer standing directly behind an active Gloo Wall relative to threat
    for (wall in sim.glooWalls) {
      if (wall.hp <= 0f) continue
      val d = CollisionSolver.distXZ(ped.x, ped.z, wall.x, wall.z)
      if (d < bestDist) {
        val wx = wall.x - threatX
        val wz = wall.z - threatZ
        val wd = hypot(wx, wz).coerceAtLeast(0.1f)
        bestDist = d
        bestSpot = Pair(wall.x + (wx / wd) * 1.15f, wall.z + (wz / wd) * 1.15f)
      }
    }
    if (bestSpot != null) return bestSpot

    // 2. Check nearby trees / pillars / rocks
    for (c in sim.world.circleColliders) {
      val d = CollisionSolver.distXZ(ped.x, ped.z, c.x, c.z)
      if (d < bestDist) {
        val cx = c.x - threatX
        val cz = c.z - threatZ
        val cd = hypot(cx, cz).coerceAtLeast(0.1f)
        val candX = c.x + (cx / cd) * (c.radius + 0.75f)
        val candZ = c.z + (cz / cd) * (c.radius + 0.75f)
        if (!sim.isPointBlockedBySolidAabb(candX, candZ, ped.y + 0.35f, ped.y + 1.65f, 0.35f)) {
          bestDist = d
          bestSpot = Pair(candX, candZ)
        }
      }
    }

    // 3. Check nearby House exterior side opposite the threat
    for (h in sim.world.houses) {
      val d = CollisionSolver.distXZ(ped.x, ped.z, h.centerX, h.centerZ)
      if (d < 22f) {
        val hx = h.centerX - threatX
        val hz = h.centerZ - threatZ
        val candX =
          if (abs(hx) >= abs(hz)) h.centerX + (if (hx >= 0f) h.width * 0.5f + 1.1f else -(h.width * 0.5f + 1.1f))
          else h.centerX
        val candZ =
          if (abs(hz) > abs(hx)) h.centerZ + (if (hz >= 0f) h.depth * 0.5f + 1.1f else -(h.depth * 0.5f + 1.1f))
          else h.centerZ
        if (CollisionSolver.distXZ(ped.x, ped.z, candX, candZ) < bestDist) {
          bestDist = CollisionSolver.distXZ(ped.x, ped.z, candX, candZ)
          bestSpot = Pair(candX, candZ)
        }
      }
    }

    return bestSpot ?: Pair(ped.x + ux * 5.0f, ped.z + uz * 5.0f)
  }

  private fun findBestLootForBot(ped: PedestrianRuntimeState): WorldLootItem? {
    var bestItem: WorldLootItem? = null
    var bestScore = Float.MAX_VALUE

    for (item in sim.worldLootItems) {
      if (item.pickupCooldownSec > 0f) continue
      if (abs(item.y - ped.y) > 1.8f && item.y > 2.0f) continue
      val d = CollisionSolver.distXZ(ped.x, ped.z, item.x, item.z)
      if (d > 42f) continue

      val utility =
        when (item.kind.category) {
          LootCategory.WEAPON -> {
            val w = item.kind.weapon ?: continue
            when {
              ped.weapon == WeaponType.FISTS -> 0.25f
              weaponRank(w) > weaponRank(ped.weapon) -> 0.55f
              else -> continue
            }
          }
          LootCategory.VEST -> {
            if (item.kind.armorLevel > ped.vestLevel || (item.kind.armorLevel == ped.vestLevel && ped.vestDurability < item.kind.maxDurability * 0.65f)) 0.60f
            else continue
          }
          LootCategory.HELMET -> {
            if (item.kind.armorLevel > ped.helmetLevel || (item.kind.armorLevel == ped.helmetLevel && ped.helmetDurability < item.kind.maxDurability * 0.65f)) 0.65f
            else continue
          }
          LootCategory.MEDICAL -> if (ped.botMedkits < 3 && ped.botBagCount < GameSimulation.MAX_BAG_SLOTS) 0.72f else continue
          LootCategory.GLOO_WALL -> if (ped.botGlooWalls < 3 && ped.botBagCount < GameSimulation.MAX_BAG_SLOTS) 0.76f else continue
          LootCategory.CURRENCY -> 1.15f
        }
      val score = d * utility
      if (score < bestScore) {
        bestScore = score
        bestItem = item
      }
    }
    return bestItem
  }

  private fun collectTouchingWorldLoot(ped: PedestrianRuntimeState) {
    val iter = sim.worldLootItems.iterator()
    while (iter.hasNext()) {
      val item = iter.next()
      if (item.pickupCooldownSec > 0f) continue
      if (abs(item.y - ped.y) > 1.75f) continue
      if (CollisionSolver.distXZ(ped.x, ped.z, item.x, item.z) > 1.55f) continue

      var picked = false
      when (item.kind.category) {
        LootCategory.CURRENCY -> picked = true
        LootCategory.WEAPON -> {
          val w = item.kind.weapon
          if (w != null && weaponRank(w) > weaponRank(ped.weapon)) {
            ped.weapon = w
            if (ped.botBagCount < GameSimulation.MAX_BAG_SLOTS) ped.botBagCount++
            picked = true
          }
        }
        LootCategory.VEST -> {
          val lvl = item.kind.armorLevel
          if (lvl > ped.vestLevel || (lvl == ped.vestLevel && ped.vestDurability < item.kind.maxDurability * 0.8f)) {
            ped.vestLevel = lvl
            ped.vestDurability = item.kind.maxDurability
            picked = true
          }
        }
        LootCategory.HELMET -> {
          val lvl = item.kind.armorLevel
          if (lvl > ped.helmetLevel || (lvl == ped.helmetLevel && ped.helmetDurability < item.kind.maxDurability * 0.8f)) {
            ped.helmetLevel = lvl
            ped.helmetDurability = item.kind.maxDurability
            picked = true
          }
        }
        LootCategory.MEDICAL -> {
          if (ped.botMedkits < 3 && ped.botBagCount < GameSimulation.MAX_BAG_SLOTS) {
            ped.botMedkits++
            ped.botBagCount++
            picked = true
          }
        }
        LootCategory.GLOO_WALL -> {
          if (ped.botGlooWalls < 3 && ped.botBagCount < GameSimulation.MAX_BAG_SLOTS) {
            ped.botGlooWalls++
            ped.botBagCount++
            picked = true
          }
        }
      }
      if (picked) {
        if (ped.targetLootUid == item.uid) ped.targetLootUid = null
        sim.hitSparks += HitSparkState(item.x, item.y + 0.45f, item.z, floatArrayOf(0.28f, 0.92f, 0.72f), durationSec = 0.16f)
        iter.remove()
      }
    }
  }

  private fun findBoardableCar(ped: PedestrianRuntimeState): VehicleRuntimeState? = null

  private fun boardCarAsBot(ped: PedestrianRuntimeState, car: VehicleRuntimeState) {
    car.driverBotId = ped.id
    ped.drivingVehicleId = car.id
    ped.botMode = BotTacticalMode.DRIVING_CAR
    ped.driveTimerSec = 10f
    ped.attackMove = null
    ped.sitProgress = 0f
    ped.x = car.x
    ped.z = car.z
    ped.y = car.y + 0.25f
    ped.yawRad = car.yawRad
    pickNextRoamDestination(ped, minDistance = 42f)
  }

  fun releaseBotVehicle(ped: PedestrianRuntimeState) {
    val carId = ped.drivingVehicleId ?: return
    val car = sim.vehicles.firstOrNull { it.id == carId }
    if (car != null && car.driverBotId == ped.id) {
      car.driverBotId = null
      car.forwardSpeed *= 0.25f
      car.isBraking = false
      val leftX = -cos(car.yawRad)
      val leftZ = sin(car.yawRad)
      val exitX = car.x + leftX * 2.1f
      val exitZ = car.z + leftZ * 2.1f
      val resolved = resolveBotMovement(exitX, exitZ, 0f)
      ped.x = resolved.first
      ped.z = resolved.second
    }
    ped.drivingVehicleId = null
    ped.vehicleCooldownSec = 10f
    ped.y = sim.computeGroundHeight(ped.x, ped.z, 0f)
  }

  private fun stepBotDrivingCar(ped: PedestrianRuntimeState, dt: Float) {
    val car = sim.vehicles.firstOrNull { it.id == ped.drivingVehicleId }
    if (car == null || car.driverBotId != ped.id || sim.activeVehicle?.id == car.id || sim.activeCarjack?.vehicleId == car.id) {
      ped.drivingVehicleId = null
      ped.vehicleCooldownSec = 8f
      ped.y = sim.computeGroundHeight(ped.x, ped.z, 0f)
      return
    }

    ped.driveTimerSec -= dt
    val playerThreatDist =
      if (sim.isPlaying && !sim.isPlayerEliminated && sim.dropPhase == DropPhase.GROUND && sim.activeVehicle == null) {
        CollisionSolver.distXZ(car.x, car.z, sim.playerX, sim.playerZ)
      } else Float.MAX_VALUE
    val destDist = CollisionSolver.distXZ(car.x, car.z, ped.roamTargetX, ped.roamTargetZ)

    if (ped.driveTimerSec <= 0f || destDist < 12f || playerThreatDist < 18f) {
      releaseBotVehicle(ped)
      ped.botMode = if (playerThreatDist < 22f) BotTacticalMode.FIGHTING else BotTacticalMode.LOOTING
      return
    }

    val nav = computeBridgeAwareWaypoint(car.x, car.z, ped.roamTargetX, ped.roamTargetZ)
    val dx = nav.first - car.x
    val dz = nav.second - car.z
    val desiredYaw = angleBetweenXZ(dx, dz)
    val yawDiff = normalizeAngleRad(desiredYaw - car.yawRad)
    car.steerAngle = (yawDiff * 0.9f).coerceIn(-0.56f, 0.56f)
    car.yawRad = lerpAngleRad(car.yawRad, desiredYaw, (dt * 4.4f).coerceAtMost(1f))

    val targetSpeed = if (abs(yawDiff) > 0.55f) 8.0f else min(15.5f, car.maxSpeed * 0.65f)
    car.forwardSpeed += (targetSpeed - car.forwardSpeed) * (dt * 6.5f).coerceAtMost(1f)
    car.isBraking = false

    val fwdX = sin(car.yawRad)
    val fwdZ = cos(car.yawRad)
    car.x += fwdX * car.forwardSpeed * dt
    car.z += fwdZ * car.forwardSpeed * dt
    car.wheelSpinRad += (car.forwardSpeed / 0.36f) * dt
    car.rollRad = (car.steerAngle * (car.forwardSpeed / 22f) * 0.045f).coerceIn(-0.06f, 0.06f)

    val (rx, rz, hit) =
      CollisionSolver.resolveAll(car.x, car.z, 1.45f, sim.world.aabbColliders, sim.world.circleColliders)
    val glooResolved = sim.resolveEntityVsGlooWalls(rx, rz, 1.45f, 0.25f)
    val clamped = CollisionSolver.clampCarToIsland(glooResolved.first, glooResolved.second, margin = 2.0f)
    car.x = clamped.first
    car.z = clamped.second
    if (hit) car.forwardSpeed *= 0.45f
    car.y = sim.computeGroundHeight(car.x, car.z, car.y + 0.45f)
    val (slopePitch, _) = TerrainHeightfield.computeSlopeAngles(car.x, car.z, car.yawRad)
    car.pitchRad += (slopePitch - car.pitchRad) * (dt * 9f).coerceAtMost(1f)

    ped.x = car.x
    ped.z = car.z
    ped.y = car.y + 0.25f
    ped.yawRad = car.yawRad
  }

  private fun stepWaypointPatrol(ped: PedestrianRuntimeState, dt: Float) {
    val wp = ped.waypoints[ped.targetIdx]
    val dx = wp.first - ped.x
    val dz = wp.second - ped.z
    val dist = hypot(dx, dz)
    if (dist < 0.8f) {
      if (ped.waypoints.size == 2) ped.targetIdx = 1 - ped.targetIdx
      else ped.targetIdx = (ped.targetIdx + 1) % ped.waypoints.size
      return
    }
    if (sim.isPlaying && !sim.isPlayerEliminated && sim.activeVehicle == null && sim.activeBoat == null) {
      val pd = CollisionSolver.distXZ(ped.x, ped.z, sim.playerX, sim.playerZ)
      if (pd < 1.15f) return
    }
    val nx = dx / dist
    val nz = dz / dist
    ped.yawRad = lerpAngleRad(ped.yawRad, angleBetweenXZ(nx, nz), (dt * 8f).coerceAtMost(1f))
    ped.x += nx * ped.speed * dt
    ped.z += nz * ped.speed * dt
    ped.walkCycle += dt * ped.speed * 4.0f
  }

  private fun pickNextRoamDestination(ped: PedestrianRuntimeState, minDistance: Float = 16f) {
    val seed = abs(ped.id.hashCode() + (sim.worldTimeSec * 10f).toInt())
    // Occasionally gravitate toward player's general area or a house so bots stay engaging
    if (sim.isPlaying && !sim.isPlayerEliminated && seed % 3 == 0 && CollisionSolver.isOnLand(sim.playerX, sim.playerZ)) {
      val spreadAngle = (seed % 360) * (PI.toFloat() / 180f)
      val cand = CollisionSolver.clampCarToIsland(sim.playerX + cos(spreadAngle) * 18f, sim.playerZ + sin(spreadAngle) * 18f, 3.0f)
      ped.roamTargetX = cand.first
      ped.roamTargetZ = cand.second
      ped.roamRefreshSec = 10f
      return
    }
    for (offset in 0 until districtAnchors.size) {
      val anchor = districtAnchors[(seed + offset) % districtAnchors.size]
      if (CollisionSolver.distXZ(ped.x, ped.z, anchor.first, anchor.second) >= minDistance) {
        ped.roamTargetX = anchor.first
        ped.roamTargetZ = anchor.second
        ped.roamRefreshSec = 12f
        return
      }
    }
    val house = sim.world.houses[seed % sim.world.houses.size]
    val entry = doorEntryForHouse(house)
    ped.roamTargetX = entry.first
    ped.roamTargetZ = entry.second
    ped.roamRefreshSec = 10f
  }

  private fun moveBotTowardWorldTarget(
    ped: PedestrianRuntimeState,
    destX: Float,
    destZ: Float,
    speed: Float,
    dt: Float,
  ) {
    // Route through house doorway if target is inside a different HouseSpec
    var targetX = destX
    var targetZ = destZ
    val destHouse = houseContaining(destX, destZ)
    val pedHouse = houseContaining(ped.x, ped.z)
    if (destHouse != null && pedHouse?.id != destHouse.id) {
      val door = doorEntryForHouse(destHouse)
      if (CollisionSolver.distXZ(ped.x, ped.z, door.first, door.second) > 1.7f) {
        targetX = door.first
        targetZ = door.second
      }
    }

    val nav = computeBridgeAwareWaypoint(ped.x, ped.z, targetX, targetZ)
    val dx = nav.first - ped.x
    val dz = nav.second - ped.z
    val dist = hypot(dx, dz)
    if (dist < 0.25f) return

    val nx = dx / dist
    val nz = dz / dist
    ped.yawRad = lerpAngleRad(ped.yawRad, angleBetweenXZ(nx, nz), (dt * 11f).coerceAtMost(1f))

    val stepLen = min(dist, speed * dt)
    val nextX = ped.x + nx * stepLen
    val nextZ = ped.z + nz * stepLen

    val (rx, rz, hit) =
      CollisionSolver.resolveAll(
        nextX,
        nextZ,
        0.38f,
        sim.world.aabbColliders,
        sim.world.circleColliders,
        entityMinY = ped.y,
        entityMaxY = ped.y + 1.75f,
      )
    var finalX = rx
    var finalZ = rz
    // Tangential obstacle slide around buildings/walls
    if (hit && hypot(rx - ped.x, rz - ped.z) < stepLen * 0.35f) {
      val sideSign = if ((ped.id.hashCode() and 1) == 0) 1f else -1f
      val sx = ped.x + (-nz * sideSign) * stepLen
      val sz = ped.z + (nx * sideSign) * stepLen
      val sideRes =
        CollisionSolver.resolveAll(
          sx,
          sz,
          0.38f,
          sim.world.aabbColliders,
          sim.world.circleColliders,
          entityMinY = ped.y,
          entityMaxY = ped.y + 1.75f,
        )
      if (hypot(sideRes.first - ped.x, sideRes.second - ped.z) > hypot(rx - ped.x, rz - ped.z)) {
        finalX = sideRes.first
        finalZ = sideRes.second
      }
    }

    val glooResolved = sim.resolveEntityVsGlooWalls(finalX, finalZ, 0.38f, ped.y)
    val clamped = CollisionSolver.clampCarToIsland(glooResolved.first, glooResolved.second, margin = 1.9f)
    ped.x = clamped.first
    ped.z = clamped.second
    ped.walkCycle += dt * (speed * 2.2f)
  }

  private fun resolveBotMovement(x: Float, z: Float, y: Float): Pair<Float, Float> {
    val (rx, rz, _) =
      CollisionSolver.resolveAll(
        x,
        z,
        0.38f,
        sim.world.aabbColliders,
        sim.world.circleColliders,
        entityMinY = y,
        entityMaxY = y + 1.75f,
      )
    val glooResolved = sim.resolveEntityVsGlooWalls(rx, rz, 0.38f, y)
    return CollisionSolver.clampCarToIsland(glooResolved.first, glooResolved.second, margin = 1.9f)
  }

  private fun computeBridgeAwareWaypoint(curX: Float, curZ: Float, destX: Float, destZ: Float): Pair<Float, Float> {
    // Crossing North Intellect Center moat (r around (-18, -184) < 88)
    val curInIntellect = hypot(curX + 18f, curZ + 184f) < 82f
    val destInIntellect = hypot(destX + 18f, destZ + 184f) < 82f
    if (curInIntellect != destInIntellect) {
      val bridges =
        listOf(
          Pair(-16f, -84f) to Pair(-16f, -142f),   // South Center Bridge
          Pair(-96f, -92f) to Pair(-58f, -142f),   // SW Bridge
          Pair(66f, -148f) to Pair(24f, -162f),    // East Bridge
        )
      val best =
        bridges.minByOrNull { (southEnd, northEnd) ->
          val mx = (southEnd.first + northEnd.first) * 0.5f
          val mz = (southEnd.second + northEnd.second) * 0.5f
          hypot(curX - mx, curZ - mz)
        }!!
      val entry = if (curInIntellect) best.second else best.first
      val exit = if (curInIntellect) best.first else best.second
      return if (hypot(curX - entry.first, curZ - entry.second) > 6.5f) entry else exit
    }

    // Crossing West Turbine Red Suspension Bridge across Turbine Cove (x = -170, z = -38..46)
    val curOnTurbine = curX < -186f && curZ in -48f..52f
    val destOnTurbine = destX < -186f && destZ in -48f..52f
    if (curOnTurbine != destOnTurbine) {
      val bridgeMid = Pair(-170f, 4f)
      val turbineGate = Pair(-188f, 4f)
      return if (curOnTurbine) {
        if (hypot(curX - turbineGate.first, curZ - turbineGate.second) > 6.5f) turbineGate else bridgeMid
      } else {
        if (hypot(curX - bridgeMid.first, curZ - bridgeMid.second) > 6.5f) bridgeMid else turbineGate
      }
    }

    return Pair(destX, destZ)
  }

  private fun houseContaining(x: Float, z: Float): HouseSpec? =
    sim.world.houses.firstOrNull { h ->
      abs(x - h.centerX) <= h.width * 0.5f && abs(z - h.centerZ) <= h.depth * 0.5f
    }

  private fun doorEntryForHouse(h: HouseSpec): Pair<Float, Float> =
    when {
      h.facingX > 0 -> Pair(h.centerX + h.width * 0.5f + 2.0f, h.centerZ)
      h.facingX < 0 -> Pair(h.centerX - h.width * 0.5f - 2.0f, h.centerZ)
      h.facingSouth -> Pair(h.centerX, h.centerZ + h.depth * 0.5f + 2.0f)
      else -> Pair(h.centerX, h.centerZ - h.depth * 0.5f - 2.0f)
    }

  private fun weaponRank(w: WeaponType): Int =
    when (w) {
      WeaponType.FISTS -> 0
      WeaponType.PISTOL_9MM -> 1
      WeaponType.SMG_MICRO -> 2
      WeaponType.SHOTGUN -> 3
      WeaponType.SNIPER_RIFLE -> 4
      WeaponType.ASSAULT_RIFLE -> 5
    }

  private fun armorDurabilityFor(level: Int, isHelmet: Boolean): Float =
    when {
      level >= 4 -> if (isHelmet) LootItemKind.HELMET_LV4.maxDurability else LootItemKind.VEST_LV4.maxDurability
      level == 3 -> if (isHelmet) LootItemKind.HELMET_LV3.maxDurability else LootItemKind.VEST_LV3.maxDurability
      else -> 0f
    }
}
