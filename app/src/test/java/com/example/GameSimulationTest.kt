package com.example

import com.example.game.gl.CharacterMeshBuilder
import com.example.game.math.CollisionSolver
import com.example.game.state.CombatMoveType
import com.example.game.state.DropPhase
import com.example.game.state.GameSimulation
import com.example.game.state.HitZone
import com.example.game.state.LandingReactionType
import com.example.game.state.LobbyDropZone
import com.example.game.state.NpcReactionState
import com.example.game.state.ParkourActionType
import com.example.game.state.WeaponType
import com.example.game.world.BodyBuild
import com.example.game.world.CartoonPresets
import com.example.game.world.HairStyle
import com.example.game.world.WorldBuilder
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSimulationTest {
  @Test
  fun worldHasThreeHousesTreesCarsBoatsAndNpcs() {
    val world = WorldBuilder.buildKeyBayside()
    assertEquals(10, world.houses.size) // 3 Key Bayside + 3 Green Valley + 4 Rooftop Town houses
    assertTrue("Expected palm and shade trees across all 3 maps", world.trees.size >= 55)
    assertEquals(14, world.vehicleSpawns.size) // 6 player + 8 AI traffic cars
    assertEquals(8, world.boatSpawns.size) // 3 pier boats + 5 AI ocean boats
    assertTrue(world.pedestrianSpawns.size >= 20)
    assertTrue("Expected cylindrical landmarks (gazebo, water tanks, tower)", world.staticCylinders.isNotEmpty())
    assertTrue("Expected walkable floors including 2F slabs, rooftops & stairs", world.walkableFloors.size >= 25)
  }

  @Test
  fun cartoonHeroAndDiverseTownsfolkAreConfigured() {
    val hero = CartoonPresets.PLAYER_HERO
    assertEquals(BodyBuild.ROUND_CHUBBY, hero.build)
    assertEquals(HairStyle.BALD_FRINGE, hero.hairStyle)
    assertTrue(hero.hasMustache)
    assertNotNull(hero.vestColor)
    assertTrue(hero.brassButtons)

    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    val builds = sim.pedestrians.map { it.appearance.build }.toSet()
    val hairs = sim.pedestrians.map { it.appearance.hairStyle }.toSet()
    assertTrue("Townsfolk should include chubby, tall lean, and athletic cartoon builds", builds.size == 3)
    assertTrue("Townsfolk should include varied cartoon hairstyles and caps", hairs.size >= 3)

    // Carjacked NPC inherits driver appearance
    val aiCar = sim.vehicles.first { it.isAiTraffic }
    sim.isPlaying = true
    sim.playerX = aiCar.x - 1.8f
    sim.playerZ = aiCar.z
    sim.step(0.02f)
    sim.toggleEnterExitVehicleOrBoat()
    assertEquals(aiCar.appearance, sim.ejectedNpcs.first().appearance)
  }

  @Test
  fun playerCanWalkSprintJumpAndEnterHouse() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.isPlaying = true

    sim.teleportToHouse(0)
    val startZ = sim.playerZ
    assertNull(sim.currentInsideHouse)

    sim.camYawRad = kotlin.math.PI.toFloat()
    sim.input.moveY = -1f
    sim.input.sprintHeld = true
    repeat(60) { sim.step(0.03f) }

    assertTrue("Player should advance into house", sim.playerZ > startZ + 1.5f)
    assertNotNull("Player should be detected inside Oceanview Villa", sim.currentInsideHouse)

    sim.input.moveY = 0f
    sim.input.jumpQueued = true
    sim.step(0.02f)
    assertFalse("Player should leave ground on jump", sim.isGrounded)
    assertTrue(sim.playerY > 0.14f)
  }

  @Test
  fun playerCanEnterDriveAndExitSportsCar() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.isPlaying = true

    val car = sim.vehicles.first()
    sim.playerX = car.x - 1.5f
    sim.playerZ = car.z
    sim.step(0.02f)

    assertNotNull("Car should be detected nearby", sim.nearbyVehicle)
    assertTrue("Should enter car", sim.toggleEnterExitVehicleOrBoat())
    assertNotNull(sim.activeVehicle)

    sim.input.gasHeld = true
    repeat(50) { sim.step(0.03f) }
    assertTrue("Car should reach driving speed", car.forwardSpeed > 10f)

    assertFalse("Should exit car", sim.toggleEnterExitVehicleOrBoat())
    assertNull(sim.activeVehicle)
  }

  @Test
  fun playerCanBoardAndDriveBoatInOcean() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.isPlaying = true

    sim.teleportToBoatPier()
    val boat = sim.boats.first()
    sim.playerX = 16.2f
    sim.playerZ = 82.5f
    sim.step(0.02f)

    assertNotNull("Speedboat should be detected nearby from pier", sim.nearbyBoat)
    assertTrue("Should board boat", sim.toggleEnterExitVehicleOrBoat())
    assertNotNull(sim.activeBoat)

    val startZ = boat.z
    sim.input.gasHeld = true
    repeat(60) { sim.step(0.03f) }
    assertTrue("Boat should accelerate across ocean", boat.forwardSpeed > 8f)
    assertTrue("Boat should sail south into open water", boat.z > startZ)

    assertFalse("Should exit boat", sim.toggleEnterExitVehicleOrBoat())
    assertNull(sim.activeBoat)
  }

  @Test
  fun aiTrafficCarsBoatsAndPedestriansMoveAutomatically() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.isPlaying = true

    val aiCar = sim.vehicles.first { it.isAiTraffic }
    val aiBoat = sim.boats.first { it.isAiCruise }
    val ped = sim.pedestrians.first()

    val carStartX = aiCar.x
    val carStartZ = aiCar.z
    val boatStartX = aiBoat.x
    val boatStartZ = aiBoat.z
    val pedStartX = ped.x

    repeat(40) { sim.step(0.03f) }

    assertTrue(
      "AI traffic car should drive along road",
      aiCar.x != carStartX || aiCar.z != carStartZ,
    )
    assertTrue(
      "AI ocean boat should cruise across water",
      aiBoat.x != boatStartX || aiBoat.z != boatStartZ,
    )
    assertNotEquals("AI pedestrian should stroll", pedStartX, ped.x, 0.05f)
  }

  @Test
  fun playerCarjackingPullsDriverOutAndKnocksThemDown() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.isPlaying = true

    val aiCar = sim.vehicles.first { it.isAiTraffic }
    sim.playerX = aiCar.x - 1.8f
    sim.playerZ = aiCar.z
    sim.step(0.02f)

    assertEquals(aiCar.id, sim.nearbyVehicle?.id)
    assertTrue("Should initiate carjacking", sim.toggleEnterExitVehicleOrBoat())
    assertNotNull("Carjack sequence should be active", sim.activeCarjack)
    assertFalse("AI traffic control stops immediately", aiCar.isAiTraffic)
    assertEquals("Ejected driver spawned", 1, sim.ejectedNpcs.size)

    // Mid-sequence: player reaches arms forward and throws driver out
    repeat(20) { sim.step(0.025f) } // 0.50s
    assertTrue("Player arms should reach out to pull driver", sim.playerArmReachNorm > 0.2f)

    // After 1.0s: carjack finishes, player takes wheel, NPC lies knocked down on ground
    repeat(25) { sim.step(0.025f) } // +0.625s -> 1.125s total
    assertNull("Carjack sequence should finish", sim.activeCarjack)
    assertEquals("Player should now be driving the car", aiCar.id, sim.activeVehicle?.id)
    val npc = sim.ejectedNpcs.first()
    assertTrue("Ejected NPC should be knocked down flat on ground", npc.fallProgress >= 0.9f)
  }

  @Test
  fun sculptedCharacterMeshesHaveValidTopologyAndOutwardNormals() {
    val meshes = CharacterMeshBuilder.allRawMeshes()
    assertEquals(10, meshes.size)
    for ((name, mesh) in meshes) {
      val vertCount = mesh.vertices.size / 8
      assertTrue("$name should have vertices", vertCount > 12)
      assertTrue("$name vertices should fit Short index range", vertCount < 32767)
      assertTrue("$name should have triangles", mesh.indices.isNotEmpty() && mesh.indices.size % 3 == 0)

      for (idx in mesh.indices) {
        val u = idx.toInt() and 0xFFFF
        assertTrue("$name index $u out of range [0, $vertCount)", u in 0 until vertCount)
      }

      for (v in 0 until vertCount) {
        val base = v * 8
        for (k in 0 until 8) {
          assertFalse("$name float NaN/Inf at v=$v", !mesh.vertices[base + k].isFinite())
        }
        val nx = mesh.vertices[base + 3]
        val ny = mesh.vertices[base + 4]
        val nz = mesh.vertices[base + 5]
        val len = sqrt(nx * nx + ny * ny + nz * nz)
        assertTrue("$name normal length near 1.0 (was $len)", len in 0.95f..1.05f)
      }

      // Verify outward orientation on convex/closed shells relative to centroid
      if (name in setOf("capsule", "chubbyHead", "kurtaTorso", "shoe")) {
        var outwardDotSum = 0f
        for (v in 0 until vertCount) {
          val b = v * 8
          outwardDotSum +=
            mesh.vertices[b] * mesh.vertices[b + 3] +
              mesh.vertices[b + 1] * mesh.vertices[b + 4] +
              mesh.vertices[b + 2] * mesh.vertices[b + 5]
        }
        assertTrue("$name normals must point outward (sum=$outwardDotSum)", outwardDotSum > 0f)
      }
    }
  }

  @Test
  fun playerCanSitAnywhereAndStandWhenMoving() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.isPlaying = true
    assertFalse(sim.isSitting)
    assertEquals(0f, sim.sitProgress, 0.001f)

    assertTrue("Should sit down on dry land", sim.toggleSit())
    assertTrue(sim.isSitting)
    repeat(15) { sim.step(0.03f) }
    assertTrue("Sit posture progress should reach 1.0", sim.sitProgress > 0.9f)

    // Moving automatically stands the character smoothly back up
    sim.input.moveY = -1f
    repeat(15) { sim.step(0.03f) }
    assertFalse("Moving stands character back up", sim.isSitting)
    assertTrue("Sit progress eases back toward 0", sim.sitProgress < 0.2f)
  }

  @Test
  fun multiMovePunchAndKickCombosDamageAndKoPedestrians() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.isPlaying = true
    val target = sim.pedestrians.first { !it.isBraveDefender }
    sim.playerX = target.x - 1.4f
    sim.playerZ = target.z
    sim.step(0.02f)

    val startCash = sim.playerCash
    // First punch: JAB
    sim.triggerPunch()
    sim.step(0.02f)
    assertEquals(CombatMoveType.PUNCH_JAB, sim.activeAttackMove)
    repeat(12) { sim.step(0.03f) } // strike connects and finishes
    assertTrue("Punch jab should damage NPC", target.hp < target.maxHp)

    // Second punch inside combo window: HOOK
    sim.triggerPunch()
    sim.step(0.02f)
    assertEquals(CombatMoveType.PUNCH_HOOK, sim.activeAttackMove)
    repeat(14) { sim.step(0.03f) }

    // Switch to Kick: KICK_FRONT or KICK_ROUNDHOUSE finishes KO
    sim.playerX = target.x - 1.4f
    sim.playerZ = target.z
    sim.triggerKick()
    sim.step(0.02f)
    assertTrue("Should execute a kick move", sim.activeAttackMove?.isKick == true)
    repeat(20) { sim.step(0.03f) }

    if (target.hp > 0f) {
      sim.playerX = target.x - 1.4f
      sim.playerZ = target.z
      sim.triggerKick()
      repeat(20) { sim.step(0.03f) }
    }

    assertEquals(NpcReactionState.KNOCKED_DOWN, target.reactionState)
    assertTrue("Defeated count should increase", sim.npcDefeatedCount >= 1)
    assertTrue("Cash should increase from KO reward", sim.playerCash > startCash)
  }

  @Test
  fun gunShopPurchaseAndShootingKillsAndPanicsCrowd() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.isPlaying = true

    // Fast travel to Vice Arms Gun Shop and check proximity
    sim.teleportToGunShop()
    sim.step(0.02f)
    assertTrue("Should detect nearby Gun Shop counter", sim.nearbyGunShop)

    val startCash = sim.playerCash
    assertTrue("Should buy 9MM Pistol", sim.buyOrEquipWeapon(WeaponType.PISTOL_9MM))
    assertEquals(WeaponType.PISTOL_9MM, sim.equippedWeapon)
    assertEquals(startCash - WeaponType.PISTOL_9MM.price, sim.playerCash)

    // Aim directly at a civilian and fire
    val target = sim.pedestrians.first { !it.isBraveDefender }
    val bystander = sim.pedestrians.first { it.id != target.id }
    bystander.x = target.x + 8f
    bystander.z = target.z
    sim.playerX = target.x - 5f
    sim.playerZ = target.z
    sim.playerYawRad = (kotlin.math.PI / 2.0).toFloat() // Facing +X directly toward target

    val bystanderStartDist = CollisionSolver.distXZ(bystander.x, bystander.z, sim.playerX, sim.playerZ)

    // Fire 3 pistol shots (48 damage each -> kills 100 HP target)
    repeat(3) {
      sim.input.fireQueued = true
      repeat(12) { sim.step(0.03f) }
    }

    assertEquals("Target should be killed/knocked down by gunfire", NpcReactionState.KNOCKED_DOWN, target.reactionState)
    assertTrue(
      "Bystander should flinch/flee from gunfire",
      bystander.reactionState == NpcReactionState.FLEEING ||
        bystander.reactionState == NpcReactionState.HANDS_UP_FLINCH,
    )

    // Step simulation so fleeing bystander sprints away to safety
    repeat(30) { sim.step(0.03f) }
    val bystanderEndDist = CollisionSolver.distXZ(bystander.x, bystander.z, sim.playerX, sim.playerZ)
    assertTrue("Fleeing bystander should run further away from shooter", bystanderEndDist > bystanderStartDist + 1.5f)
  }

  @Test
  fun realBulletsFlyOverTimeAndScopeEnablesLongRangeHeadshot() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.isPlaying = true
    assertTrue("Should buy Longshot .50 Sniper", sim.buyOrEquipWeapon(WeaponType.SNIPER_RIFLE))

    // Place East-West patrolling target 48 meters downrange along +X axis
    val target = sim.pedestrians.first { it.id == "ped_chubby_coral" }
    target.x = 24f
    target.z = -18.2f
    target.hp = 100f
    sim.playerX = -24f
    sim.playerZ = -18.2f + 0.34f // align shoulder scope crosshair straight down z = -18.2f
    sim.playerYawRad = (kotlin.math.PI / 2.0).toFloat() // Facing +X

    assertTrue("Should toggle scope zoom on", sim.toggleScope())
    repeat(6) { sim.step(0.04f) } // settle scope camera FOV & aim
    assertTrue("Scope FOV should zoom in tightly for sniper", sim.camFovDeg < 30f)

    // Keep scope crosshair level at head height (~1.54m)
    sim.camPitchRad = 0f
    sim.input.fireQueued = true
    sim.step(0.02f)

    assertTrue("Real 3D bullet projectile should be airborne in flight", sim.bulletProjectiles.isNotEmpty())
    val initialBulletX = sim.bulletProjectiles.first().x

    // Advance physics steps while bullet flies downrange
    repeat(10) { sim.step(0.02f) }
    if (sim.bulletProjectiles.isNotEmpty()) {
      assertTrue("Bullet should physically advance downrange toward target", sim.bulletProjectiles.first().x > initialBulletX + 5f)
    }

    // Finish flight over 48 meters (118 m/s -> ~0.41s)
    repeat(30) { sim.step(0.02f) }
    assertTrue("Sniper shot at 48m should hit and KO/damage target", target.hp < 10f)
    assertEquals("Long-range sniper shot should register HEAD hit zone", HitZone.HEAD, target.hitReactZone)
  }

  @Test
  fun punchAndKickMovesTriggerHeadVsTorsoHitZoneReactions() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.isPlaying = true
    val target = sim.pedestrians.first { !it.isBraveDefender }
    target.hp = 200f
    sim.playerX = target.x - 1.3f
    sim.playerZ = target.z
    sim.step(0.02f)

    // 1st Punch: JAB targets TORSO
    sim.triggerPunch()
    repeat(12) { sim.step(0.03f) }
    assertEquals(HitZone.TORSO, target.hitReactZone)
    assertTrue(target.hitReactTimerSec > 0f)

    // 2nd Punch in combo window: HOOK targets HEAD
    sim.playerX = target.x - 1.3f
    sim.playerZ = target.z
    sim.triggerPunch()
    repeat(12) { sim.step(0.03f) }
    assertEquals(HitZone.HEAD, target.hitReactZone)
    assertTrue(target.hitReactTimerSec > 0f)
  }

  @Test
  fun playerCanBefriendNpcWhoFollowsSitsAndFightsEnemies() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.isPlaying = true

    val candidate = sim.pedestrians.first { !it.isBraveDefender }
    val enemy = sim.pedestrians.first { it.id != candidate.id && !it.isFriend }

    sim.playerX = candidate.x - 1.5f
    sim.playerZ = candidate.z
    sim.step(0.02f)

    assertEquals(candidate.id, sim.nearbyFriendCandidate?.id)
    assertTrue("Should befriend nearby NPC", sim.befriendNearbyNpc())
    assertTrue(candidate.isFriend)
    assertEquals(NpcReactionState.COMPANION, candidate.reactionState)

    // Sit down and verify friend walks into formation and sits beside player
    sim.toggleSit()
    repeat(45) { sim.step(0.04f) }
    assertTrue("Companion should sit alongside player", candidate.sitProgress > 0.35f)

    // Stand up and move across plaza -> companion follows player
    val friendStartX = candidate.x
    sim.toggleSit()
    sim.camYawRad = (kotlin.math.PI / 2.0).toFloat()
    sim.input.moveY = -1f
    repeat(40) { sim.step(0.04f) }
    assertTrue(
      "Companion should move to follow player",
      CollisionSolver.distXZ(candidate.x, candidate.z, sim.playerX, sim.playerZ) < 6.0f &&
        kotlin.math.abs(candidate.x - friendStartX) > 0.5f,
    )
    sim.input.moveY = 0f

    // Enemy starts fighting back nearby -> companion charges in and damages enemy
    enemy.x = sim.playerX + 2.5f
    enemy.z = sim.playerZ
    enemy.reactionState = NpcReactionState.FIGHTING_BACK
    enemy.panicTimerSec = 10f
    val enemyStartHp = enemy.hp

    repeat(50) { sim.step(0.04f) }
    assertTrue("Companion should punch/kick hostile enemy defending player", enemy.hp < enemyStartHp)
  }

  @Test
  fun dualMapBridgesAllowDrivingFromBaysideToEmeraldUptown() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.isPlaying = true

    val car = sim.vehicles.first { it.id == "car_cheetah" }
    sim.playerX = car.x - 1.5f
    sim.playerZ = car.z
    sim.step(0.02f)
    assertTrue("Should board Cheetah GT", sim.toggleEnterExitVehicleOrBoat())

    // Place on East Bayside approach facing East across South Bridge (z = 16, x = 44 -> 108)
    car.x = 44f
    car.z = 16f
    car.yawRad = (kotlin.math.PI / 2.0).toFloat()
    sim.input.gasHeld = true
    repeat(110) { sim.step(0.03f) }

    assertTrue("Car should drive smoothly across East channel bridge onto Green Valley (x=${car.x})", car.x > 96f)
    assertTrue(
      "Snapshot zone should update to Green Valley once across channel",
      sim.snapshotHud(isMuted = false).zoneName.contains("GREEN VALLEY") || car.x > 84f,
    )

    // Now drive West from Bayside across West Town Bridge (x = -44 -> -140) onto Rooftop Town Street
    car.x = -44f
    car.z = 16f
    car.forwardSpeed = 0f
    car.yawRad = (-kotlin.math.PI / 2.0).toFloat() // Facing West (-X)
    sim.input.gasHeld = true
    repeat(120) { sim.step(0.03f) }
    assertTrue("Car should drive smoothly across West channel bridge onto Rooftop Town (x=${car.x})", car.x < -96f)
    assertTrue(
      "Snapshot zone should update to Rooftop Town once across West channel",
      sim.snapshotHud(isMuted = false).zoneName.contains("ROOFTOP TOWN") || car.x < -84f,
    )
  }

  @Test
  fun twoStoryHouseStairsLetPlayerWalkToSecondFloorAndBackDown() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.isPlaying = true

    // House 3 is Emerald Sky Duplex (2-story, centerX = 126f, centerZ = 33f, facingSouth = false)
    val twoStory = sim.world.houses[3]
    assertEquals(2, twoStory.floors)
    sim.teleportToHouse(3)
    assertTrue("Should start on ground level near driveway", sim.playerY < 0.35f)

    // Walk South (+Z, yaw=0 / camYaw=PI) through North open doorway into 1F interior
    sim.camYawRad = kotlin.math.PI.toFloat()
    sim.input.moveY = -1f
    repeat(50) { sim.step(0.03f) }
    assertEquals("Should be inside 2-story house on 1F", twoStory.name, sim.currentInsideHouse?.name)

    // Align at foot of interior staircase (x = 131.4f, z = 29.8f) and walk South (+Z) up the 11 steps
    sim.playerX = 131.4f
    sim.playerZ = 29.5f
    sim.playerY = sim.computeGroundHeight(sim.playerX, sim.playerZ, 0.2f)
    sim.camYawRad = kotlin.math.PI.toFloat()
    sim.input.moveY = -1f
    repeat(80) { sim.step(0.03f) }

    assertTrue("Climbing interior stairs should elevate player onto 2nd floor (y=${sim.playerY})", sim.playerY > 3.1f)
    assertTrue("Player should still be inside Emerald Sky Duplex on 2F", sim.currentInsideHouse?.name == twoStory.name)

    // Walk West (-X) across the top landing onto the 2nd floor loft
    sim.camYawRad = (kotlin.math.PI / 2.0).toFloat()
    sim.input.moveY = -1f
    repeat(35) { sim.step(0.03f) }
    assertTrue("2nd floor loft slab should hold player above 3.3m (y=${sim.playerY})", sim.playerY > 3.3f)

    // Walk back onto stair top and descend back to ground floor
    sim.playerX = 131.4f
    sim.playerZ = 36.2f
    sim.playerY = 3.54f
    sim.camYawRad = 0f // Moving North (-Z) back down the stairs
    sim.input.moveY = -1f
    repeat(85) { sim.step(0.03f) }
    assertTrue("Descending stairs should bring player cleanly back to 1F (y=${sim.playerY})", sim.playerY < 0.45f)
  }

  @Test
  fun airplaneDropCountdownAutoEjectsAndParachuteLandsSafely() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.startAirplaneDrop()

    assertTrue(sim.isPlaying)
    assertEquals(DropPhase.AIRPLANE, sim.dropPhase)
    assertTrue(sim.planeActiveInSky)
    assertTrue(sim.playerY > 60f)

    // Fly in airplane for 4 seconds -> countdown decreases, plane advances East across map
    val startPlaneX = sim.planeX
    repeat(80) { sim.step(0.05f) } // 4.0s
    assertEquals(DropPhase.AIRPLANE, sim.dropPhase)
    assertTrue("Plane should fly eastward", sim.planeX > startPlaneX + 40f)
    assertTrue("Countdown should tick down", sim.snapshotHud(isMuted = false).planeCountdownSec in 5..7)

    // Wait remaining >6s without tapping JUMP -> automatic ejection at 0s!
    repeat(130) { sim.step(0.05f) } // +6.5s -> past 10s total
    assertEquals("Should auto-eject from plane when 10s timer ends", DropPhase.FREEFALL, sim.dropPhase)

    // Test manual parachute deploy
    assertTrue("Should open parachute manually during freefall", sim.deployParachute())
    assertEquals(DropPhase.PARACHUTE, sim.dropPhase)

    // Steer gently and glide until landing safely on ground or ocean
    sim.input.moveX = 0.25f
    sim.input.moveY = -0.6f
    repeat(320) {
      if (sim.dropPhase != DropPhase.GROUND) sim.step(0.05f)
    }
    assertEquals("Parachute should touch down and return to GROUND phase", DropPhase.GROUND, sim.dropPhase)
    assertTrue("Landed player should have valid ground/water elevation", sim.playerY >= -1.0f && sim.playerY < 4.0f)
  }

  @Test
  fun rooftopTownAndGreenValleyHousesSupportGroundTo2FAndRooftopExploration() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.isPlaying = true

    // 1. Rooftop Town House (houses[6] = Azure Bazaar House, center (-173, -1.5), facingX = +1)
    val townHouse = sim.world.houses[6]
    assertTrue(townHouse.hasRooftop)
    sim.teleportToHouse(6)
    assertTrue("Should start at yard gate on ground floor", sim.playerY < 0.35f)

    // Climb Exterior Front Concrete Staircase straight West (-X) at z = 2.10f up onto 2F Terrace Balcony
    sim.playerX = -158.7f
    sim.playerZ = 2.10f
    sim.playerY = sim.computeGroundHeight(sim.playerX, sim.playerZ, 0.2f)
    sim.camYawRad = (kotlin.math.PI / 2.0).toFloat() // -X forward
    sim.input.moveY = -1f
    repeat(65) { sim.step(0.03f) }
    assertTrue("Exterior concrete stairs should elevate player to 2F balcony (y=${sim.playerY})", sim.playerY > 3.2f)

    // Step into 2F interior room, then climb Interior 2F->Roof Staircase (z = 2.05f, x = -170.5 -> -175.5) onto Rooftop!
    sim.playerX = -170.5f
    sim.playerZ = 2.05f
    sim.playerY = 3.54f
    sim.camYawRad = (kotlin.math.PI / 2.0).toFloat() // -X forward up to roof landing
    sim.input.moveY = -1f
    repeat(65) { sim.step(0.03f) }
    assertTrue("Interior 2F->Roof stairs should elevate player to Rooftop deck (y=${sim.playerY})", sim.playerY > 6.5f)
    assertTrue(
      "HUD zone should show Rooftop tag when on roof",
      sim.snapshotHud(isMuted = false).zoneName.contains("ROOFTOP"),
    )

    // 2. Green Valley Villa (houses[3], center (126, 33)) Left-Wing 2F->Roof Staircase
    val gvVilla = sim.world.houses[3]
    assertTrue(gvVilla.hasRooftop)
    sim.teleportToHouse(3)
    sim.playerX = 120.6f
    sim.playerZ = 35.6f
    sim.playerY = 3.54f
    sim.camYawRad = 0f // Moving North (-Z) up roof stairs to 6.94m
    sim.input.moveY = -1f
    repeat(75) { sim.step(0.03f) }
    assertTrue("Green Valley roof stairs should elevate player onto villa rooftop (y=${sim.playerY})", sim.playerY > 6.5f)
  }

  @Test
  fun coldLaunchTitleScreenAndShadersAreValidAndFast() {
    val startNs = System.nanoTime()
    val world = WorldBuilder.buildKeyBayside()
    val sim = GameSimulation(world)
    val initMs = (System.nanoTime() - startNs) / 1_000_000L
    assertTrue("World + simulation cold init should finish well under 2s (was ${initMs}ms)", initMs < 2000L)

    assertFalse(sim.isPlaying)
    repeat(100) {
      sim.step(0.03f)
      val snap = sim.snapshotHud(isMuted = false)
      assertTrue(snap.playerX.isFinite() && snap.playerZ.isFinite())
      assertTrue(sim.camPos.x.isFinite() && sim.camPos.y.isFinite() && sim.camPos.z.isFinite())
    }

    val vertexShaders =
      listOf(
        com.example.game.gl.ViceShaders.SHADOW_VERTEX,
        com.example.game.gl.ViceShaders.SKY_VERTEX,
        com.example.game.gl.ViceShaders.WORLD_VERTEX,
        com.example.game.gl.ViceShaders.WATER_VERTEX,
      )
    val fragmentShaders =
      listOf(
        com.example.game.gl.ViceShaders.SHADOW_FRAGMENT,
        com.example.game.gl.ViceShaders.SKY_FRAGMENT,
        com.example.game.gl.ViceShaders.WORLD_FRAGMENT,
        com.example.game.gl.ViceShaders.WATER_FRAGMENT,
      )
    for (src in vertexShaders + fragmentShaders) {
      assertTrue("Shader must be non-blank", src.isNotBlank())
      assertFalse("Universal ES 2.0 shader must not require #version 300 es", src.contains("#version 300 es"))
      assertTrue("Shader must define main()", src.contains("void main()"))
    }
    for (frag in fragmentShaders) {
      assertTrue("Fragment shader must declare float precision", frag.contains("precision ") && frag.contains("float;"))
      assertTrue("Fragment shader must write gl_FragColor", frag.contains("gl_FragColor"))
    }
  }

  @Test
  fun parkourAllowsVaultingWallsClimbingLedgesAndJumpingOffRooftopsWithPubgReactions() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.isPlaying = true

    // 1. Vault over Enclave West Compound Wall (x = 94.1..94.9, z = -10, maxY = 2.0)
    sim.playerX = 93.4f
    sim.playerZ = -10f
    sim.playerY = 0f
    sim.playerYawRad = (kotlin.math.PI / 2.0).toFloat() // facing East (+X) toward compound wall
    sim.camYawRad = (-kotlin.math.PI / 2.0).toFloat()
    sim.step(0.02f)

    assertNotNull("Compound wall vault prompt should appear", sim.nearbyParkourLabel)
    assertTrue("Should initiate vault over enclave wall", sim.triggerParkour())
    assertEquals(ParkourActionType.VAULT_OVER, sim.activeParkour?.type)
    repeat(25) { sim.step(0.03f) }
    assertNull("Vault should complete", sim.activeParkour)
    assertTrue("Player should land cleanly on East side of wall (x=${sim.playerX})", sim.playerX > 95.1f)
    assertTrue("Player should land back near ground (y=${sim.playerY})", sim.playerY < 0.25f)

    // 2. Climb Ledge ("Hath se pakad ker chadna") onto Vice Arms Counter & Parked Sports Car
    sim.teleportToGunShop()
    sim.playerX = 27.5f
    sim.playerZ = 1.65f
    sim.playerY = 0.12f
    sim.playerYawRad = kotlin.math.PI.toFloat() // facing North (-Z) toward Arms Counter (z = 0.1..0.9, top = 1.28f)
    sim.camYawRad = 0f
    sim.step(0.02f)
    assertNotNull("Counter climb prompt should appear", sim.nearbyParkourLabel)
    assertTrue("Should initiate ledge climb onto Arms Counter", sim.triggerParkour())
    assertEquals(ParkourActionType.CLIMB_LEDGE, sim.activeParkour?.type)
    repeat(25) { sim.step(0.03f) }
    assertNull("Climb should finish", sim.activeParkour)
    assertTrue("Player should stand atop Arms Counter (y=${sim.playerY})", sim.playerY >= 1.18f)

    // Climb onto a parked sports car roof
    val car = sim.vehicles.first { !it.isAiTraffic }
    car.forwardSpeed = 0f
    sim.playerX = car.x - kotlin.math.sin(car.yawRad) * 2.55f
    sim.playerZ = car.z - kotlin.math.cos(car.yawRad) * 2.55f
    sim.playerY = 0f
    sim.playerYawRad = car.yawRad
    sim.step(0.02f)
    assertTrue("Should climb onto parked vehicle", sim.triggerParkour())
    repeat(25) { sim.step(0.03f) }
    assertTrue("Player should stay supported atop car (y=${sim.playerY})", sim.playerY >= 0.85f)

    // 3. Vault off Rooftop Parapet (Rooftop Town house rooftop at y = 6.94m) -> Freefall + Hard Stagger + Fall Damage
    val rooftopHouse = sim.world.houses[6] // Azure Bazaar House at (-173, -1.5), roofY = 6.94f
    val roofY = 6.94f
    sim.playerX = rooftopHouse.centerX - 1.0f
    sim.playerZ = rooftopHouse.centerZ - rooftopHouse.depth * 0.5f + 0.72f
    sim.playerY = roofY
    sim.isGrounded = true
    sim.playerVelY = 0f
    sim.playerHealth = 100f
    sim.playerYawRad = kotlin.math.PI.toFloat() // facing North (-Z) directly at Roof Parapet
    sim.step(0.02f)

    assertEquals("VAULT OFF", sim.nearbyParkourLabel)
    assertTrue("Should vault over rooftop parapet to jump off roof", sim.triggerParkour())
    repeat(20) { sim.step(0.03f) } // finishes vault over parapet and enters freefall
    assertNull("Vault over parapet finishes mid-air", sim.activeParkour)
    assertFalse("Player should be in airborne freefall after leaping off roof", sim.isGrounded)

    // Fall all the way down from ~7m rooftop onto ground -> trigger PUBG Hard Stagger & Fall Damage!
    var sawAirborneFallAnim = false
    var sawHardStagger = false
    repeat(50) {
      sim.step(0.03f)
      if (sim.airborneFallNorm > 0.1f) sawAirborneFallAnim = true
      if (sim.activeLandingReaction == LandingReactionType.HARD_STAGGER) sawHardStagger = true
    }
    assertTrue("Airborne fall animation should activate during rooftop drop", sawAirborneFallAnim)
    assertTrue("Player should touch down on ground (y=${sim.playerY})", sim.isGrounded && sim.playerY < 0.25f)
    assertTrue(
      "Dropping from 6.94m rooftop should trigger PUBG HARD_STAGGER reaction",
      sawHardStagger,
    )
    assertTrue("Dropping from rooftop should deal proportional fall damage (hp=${sim.playerHealth})", sim.playerHealth < 90f)
  }

  @Test
  fun lobbyAllowsInvitingSquadmatesCustomizingHeroSelectingZoneAndStartingMatch() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    assertFalse(sim.isPlaying)

    // Step Lobby -> hero showcased in center 3D frame and drag rotates yaw
    sim.input.addOrbitDelta(80f, 0f)
    val yawBefore = sim.playerYawRad
    sim.step(0.03f)
    assertTrue("Lobby drag should rotate hero yaw", kotlin.math.abs(sim.playerYawRad - yawBefore) > 0.1f)

    // Cycle Hero Outfit & Drop Zone
    assertEquals(1, sim.cycleHeroPreset())
    val nextZone = sim.cycleLobbyDropZone()
    assertEquals(LobbyDropZone.KEY_BAYSIDE, nextZone)

    // Invite buddies up to max 3 teammates (4/4 total squad)
    assertTrue(sim.inviteAiBuddyToLobby("ai_menor", "MENOR", 2))
    assertTrue(sim.inviteAiBuddyToLobby("ai_draki", "DRAKI", 1))
    assertTrue(sim.inviteAiBuddyToLobby("ai_whisky", "WHISKY", 3))
    assertFalse("Should enforce max 3 teammates (4/4 total)", sim.inviteAiBuddyToLobby("ai_corvo", "CORVO", 4))
    assertEquals(3, sim.lobbySquadSnapshot().size)

    // Gun Shop opens right from Lobby
    sim.openGunShop()
    assertTrue(sim.isGunShopOpen)
    assertTrue(sim.buyOrEquipWeapon(WeaponType.PISTOL_9MM))
    sim.closeGunShop()
    assertEquals(WeaponType.PISTOL_9MM, sim.equippedWeapon)

    // Start match from lobby at chosen drop zone -> squad companions active in world
    sim.startAirplaneDrop()
    assertTrue(sim.isPlaying)
    assertEquals(DropPhase.AIRPLANE, sim.dropPhase)
    assertEquals(nextZone.spawnPlaneX, sim.planeX, 0.1f)
    assertEquals(3, sim.pedestrians.count { it.isFriend })

    // Return cleanly back to lobby
    sim.returnToLobby()
    assertFalse(sim.isPlaying)
  }
}
