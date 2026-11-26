package com.example

import com.example.game.gl.CharacterMeshBuilder
import com.example.game.math.CollisionSolver
import com.example.game.state.BotTacticalMode
import com.example.game.state.CarSkin
import com.example.game.state.ClothStyle
import com.example.game.state.CombatMoveType
import com.example.game.state.DropPhase
import com.example.game.state.GameSimulation
import com.example.game.state.GunSkin
import com.example.game.state.HairCustomization
import com.example.game.state.HitZone
import com.example.game.state.HudElementId
import com.example.game.state.HudLayoutConfig
import com.example.game.state.LandingReactionType
import com.example.game.state.LobbyDropZone
import com.example.game.state.LootCategory
import com.example.game.state.LootItemKind
import com.example.game.state.NpcReactionState
import com.example.game.state.PantsStyle
import com.example.game.state.ParkourActionType
import com.example.game.state.WeaponType
import com.example.game.state.WeatherMode
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
    assertEquals(10, world.houses.size) // 10 Nextera multi-story & tactical houses across POIs
    assertTrue("Expected mixed conifer, acacia & palm forests across Nextera", world.trees.size >= 55)
    assertEquals(10, world.vehicleSpawns.size) // 10 parked player-drivable vehicles across Nextera POIs
    assertTrue("Nextera must have zero AI traffic cars", world.vehicleSpawns.none { it.isAiTraffic })
    assertEquals(3, world.boatSpawns.size) // 2 marina pier boats + 1 offshore cruise yacht
    assertTrue(world.pedestrianSpawns.size >= 20)
    assertTrue("Expected cylindrical landmarks (citadel, silos, reactors, towers)", world.staticCylinders.isNotEmpty())
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

    // Verify all AI bot cars are removed and BR foot bots never drive cars
    assertTrue("Zero AI traffic cars should exist on Nextera", sim.vehicles.none { it.isAiTraffic })
    sim.isPlaying = true
    repeat(30) { sim.step(0.03f) }
    assertTrue("Foot bots should never enter or drive cars", sim.pedestrians.none { it.drivingVehicleId != null })
  }

  @Test
  fun playerCanWalkSprintJumpAndEnterHouse() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.isPlaying = true

    sim.teleportToHouse(0)
    val startZ = sim.playerZ
    assertNull(sim.currentInsideHouse)

    // Farmtopia Manor faces South (+Z front yard), so walk North (-Z, camYaw = 0f) through the doorway
    sim.camYawRad = 0f
    sim.input.moveY = -1f
    sim.input.sprintHeld = true
    repeat(60) { sim.step(0.03f) }

    assertTrue("Player should advance into Farmtopia Manor", sim.playerZ < startZ - 1.5f)
    assertNotNull("Player should be detected inside Farmtopia Manor", sim.currentInsideHouse)

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
    val boat = sim.boats.first() // boat_nextera_dock_1 at (-205.5f, 95.5f)
    sim.playerX = -202.2f
    sim.playerZ = 91.2f
    sim.playerY = sim.computeGroundHeight(sim.playerX, sim.playerZ, 0.2f)
    sim.step(0.02f)

    assertNotNull("Speedboat should be detected nearby from Marina Pier", sim.nearbyBoat)
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
  fun boatsAndPedestriansMoveAutomaticallyWithoutAiCars() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.isPlaying = true

    assertTrue("Map must have zero AI traffic cars", sim.vehicles.none { it.isAiTraffic })
    val aiBoat = sim.boats.first { it.isAiCruise }
    val ped = sim.pedestrians.first()

    val boatStartX = aiBoat.x
    val boatStartZ = aiBoat.z
    val pedStartX = ped.x
    val pedStartZ = ped.z

    repeat(40) { sim.step(0.03f) }

    assertTrue(
      "AI ocean yacht should cruise across water",
      aiBoat.x != boatStartX || aiBoat.z != boatStartZ,
    )
    assertTrue(
      "On-foot island bots should patrol Nextera",
      ped.x != pedStartX || ped.z != pedStartZ,
    )
    assertTrue(
      "Parked cars stay parked without AI drivers",
      sim.vehicles.all { it.forwardSpeed == 0f && it.driverBotId == null },
    )
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
    val target = sim.pedestrians.first { !it.isBraveDefender && !it.isRivalFighter }
    target.x = -44f
    target.z = 16f
    target.y = 0f
    sim.playerX = target.x - 1.4f
    sim.playerZ = target.z
    sim.playerY = 0f
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

    // Aim directly at an unarmed civilian on Farmtopia road and fire
    val target = sim.pedestrians.first { !it.isBraveDefender && !it.isRivalFighter }
    val bystander = sim.pedestrians.first { it.id != target.id && !it.isBraveDefender && !it.isRivalFighter }
    target.x = -38f
    target.z = 16.5f
    target.y = sim.computeGroundHeight(target.x, target.z, 0f)
    bystander.x = -30f
    bystander.z = 16.5f
    bystander.y = sim.computeGroundHeight(bystander.x, bystander.z, 0f)
    sim.playerX = -43f
    sim.playerZ = 16.5f
    sim.playerY = sim.computeGroundHeight(sim.playerX, sim.playerZ, 0f)
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

    // Place target 48 meters downrange along flat Farmtopia -> Zipway East-West highway (z ~ 18f)
    val target = sim.pedestrians.first { it.id == "ped_chubby_coral" }
    sim.pedestrians.forEach {
      if (it.id != target.id) {
        it.x = -158f
        it.z = 168f
      }
    }
    target.x = 6f
    target.z = 18.0f
    target.y = sim.computeGroundHeight(target.x, target.z, 0f)
    target.hp = 100f
    target.gracePeriodSec = 0f
    sim.playerX = -42f
    sim.playerZ = 18.0f + 0.34f // align shoulder scope crosshair straight down z = 18.0f
    sim.playerY = sim.computeGroundHeight(sim.playerX, sim.playerZ, 0f)
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

    val candidate = sim.pedestrians.first { !it.isBraveDefender && !it.isRivalFighter }
    val enemy = sim.pedestrians.first { it.id != candidate.id && !it.isFriend }

    candidate.x = -46f
    candidate.z = 14f
    candidate.y = 0f
    sim.playerX = candidate.x - 1.5f
    sim.playerZ = candidate.z
    sim.playerY = 0f
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

    // 1. Drive South (+Z) across Turbine Red Suspension Bridge (x = -170, z = -38 -> 46)
    car.x = -170f
    car.z = -34f
    car.yawRad = 0f // Facing South (+Z)
    sim.input.gasHeld = true
    repeat(145) { sim.step(0.03f) }
    assertTrue("Car should drive cleanly across Turbine Red Bridge (z=${car.z})", car.z > 32f)

    // 2. Drive North (-Z) across Intellect South Moat Bridge (x = -16, z = -84 -> -142) into Intellect Center
    car.x = -16f
    car.z = -84f
    car.forwardSpeed = 0f
    car.yawRad = kotlin.math.PI.toFloat() // Facing North (-Z)
    sim.input.gasHeld = true
    repeat(130) { sim.step(0.03f) }
    assertTrue("Car should drive smoothly across Intellect Moat Bridge into Intellect Center (z=${car.z})", car.z < -134f)
    assertTrue(
      "Snapshot zone should update to Intellect Center once across moat bridge",
      sim.snapshotHud(isMuted = false).zoneName.contains("INTELLECT"),
    )
  }

  @Test
  fun twoStoryHouseStairsLetPlayerWalkToSecondFloorAndBackDown() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.isPlaying = true

    // House 3 is Plazaria Courtyard Loft (2-story, centerX = -66f, centerZ = 126f, width = 14f, depth = 11f, facingSouth = false)
    val twoStory = sim.world.houses[3]
    assertEquals(2, twoStory.floors)
    sim.teleportToHouse(3)
    assertTrue("Should start on ground level near driveway", sim.playerY < 0.35f)

    // Walk South (+Z, camYaw=PI) through North open doorway into 1F interior
    sim.camYawRad = kotlin.math.PI.toFloat()
    sim.input.moveY = -1f
    repeat(50) { sim.step(0.03f) }
    assertEquals("Should be inside Plazaria Courtyard Loft on 1F", twoStory.name, sim.currentInsideHouse?.name)

    // Align at foot of interior staircase (x = -61.1f, z = 122.8f) and walk South (+Z) up the 11 steps
    sim.playerX = -61.1f
    sim.playerZ = 122.8f
    sim.playerY = sim.computeGroundHeight(sim.playerX, sim.playerZ, 0.2f)
    sim.camYawRad = kotlin.math.PI.toFloat()
    sim.input.moveY = -1f
    repeat(80) { sim.step(0.03f) }

    assertTrue("Climbing interior stairs should elevate player onto 2nd floor (y=${sim.playerY})", sim.playerY > 3.1f)
    assertTrue("Player should still be inside Plazaria Courtyard Loft on 2F", sim.currentInsideHouse?.name == twoStory.name)

    // Walk West (-X) across the top landing onto the 2nd floor loft
    sim.camYawRad = (kotlin.math.PI / 2.0).toFloat()
    sim.input.moveY = -1f
    repeat(35) { sim.step(0.03f) }
    assertTrue("2nd floor loft slab should hold player above 3.3m (y=${sim.playerY})", sim.playerY > 3.3f)

    // Walk back onto stair top and descend back to ground floor
    sim.playerX = -61.1f
    sim.playerZ = 128.9f
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

    // 1. Rust Town Barracks (houses[6], center (164, 26), facingX = +1)
    val townHouse = sim.world.houses[6]
    assertTrue(townHouse.hasRooftop)
    sim.teleportToHouse(6)
    assertTrue("Should start at yard gate on ground floor", sim.playerY < 0.35f)

    // Climb Exterior Front Concrete Staircase straight West (-X) at z = 29.6f (x = 178.3 -> 173.5) onto 2F Balcony
    sim.playerX = 178.3f
    sim.playerZ = 29.6f
    sim.playerY = sim.computeGroundHeight(sim.playerX, sim.playerZ, 0.2f)
    sim.camYawRad = (kotlin.math.PI / 2.0).toFloat() // -X forward
    sim.input.moveY = -1f
    repeat(65) { sim.step(0.03f) }
    assertTrue("Exterior concrete stairs should elevate player to 2F balcony (y=${sim.playerY})", sim.playerY > 3.2f)

    // Climb Interior 2F->Roof Staircase (z = 29.55f, x = 166.5 -> 161.5) onto Rooftop!
    sim.playerX = 166.5f
    sim.playerZ = 29.55f
    sim.playerY = 3.54f
    sim.camYawRad = (kotlin.math.PI / 2.0).toFloat() // -X forward up to roof landing
    sim.input.moveY = -1f
    repeat(65) { sim.step(0.03f) }
    assertTrue("Interior 2F->Roof stairs should elevate player to Rooftop deck (y=${sim.playerY})", sim.playerY > 6.5f)
    assertTrue(
      "HUD zone should show Rooftop tag when on roof",
      sim.snapshotHud(isMuted = false).zoneName.contains("ROOFTOP"),
    )

    // 2. Plazaria Courtyard Loft (houses[3], center (-66, 126), facingSouth=false) Left-Wing 2F->Roof Staircase
    val plazariaLoft = sim.world.houses[3]
    assertTrue(plazariaLoft.hasRooftop)
    sim.teleportToHouse(3)
    sim.playerX = -71.4f
    sim.playerZ = 128.9f
    sim.playerY = 3.54f
    sim.camYawRad = 0f // Moving North (-Z) up roof stairs to 6.94m
    sim.input.moveY = -1f
    repeat(75) { sim.step(0.03f) }
    assertTrue("Plazaria roof stairs should elevate player onto loft rooftop (y=${sim.playerY})", sim.playerY > 6.5f)
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

    // 1. Vault over Rust Town Barracks Yard Wall (z = 19.58..20.02, x = 173.5..179.3, maxY = 1.3)
    sim.playerX = 176f
    sim.playerZ = 18.8f
    sim.playerY = 0f
    sim.playerYawRad = 0f // facing South (+Z) toward yard compound wall
    sim.camYawRad = kotlin.math.PI.toFloat()
    sim.step(0.02f)

    assertNotNull("Compound wall vault prompt should appear", sim.nearbyParkourLabel)
    assertTrue("Should initiate vault over compound wall", sim.triggerParkour())
    assertEquals(ParkourActionType.VAULT_OVER, sim.activeParkour?.type)
    repeat(25) { sim.step(0.03f) }
    assertNull("Vault should complete", sim.activeParkour)
    assertTrue("Player should land cleanly on South side of wall (z=${sim.playerZ})", sim.playerZ > 20.3f)
    assertTrue("Player should land back near ground (y=${sim.playerY})", sim.playerY < 0.25f)

    // 2. Climb Ledge ("Hath se pakad ker chadna") onto Vice Arms Counter & Parked Sports Car
    sim.teleportToGunShop()
    sim.playerX = -46f
    sim.playerZ = -10.35f
    sim.playerY = 0.12f
    sim.playerYawRad = kotlin.math.PI.toFloat() // facing North (-Z) toward Arms Counter (z = -11.9..-11.1, top = 1.28f)
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

    // 3. Vault off Rooftop Parapet (Rust Town Barracks rooftop at y = 6.94m) -> Freefall + Hard Stagger + Fall Damage
    val rooftopHouse = sim.world.houses[6] // Rust Town Barracks at (164, 26), roofY = 6.94f
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

  @Test
  fun houseLootAutoPickupCollectsGunsMedkitsCoinsAndLv3Lv4Armor() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.isPlaying = true

    // Verify all 10 houses spawned coins, medkits, guns, Lv.3 & Lv.4 vests and helmets
    assertTrue("Houses should contain coins", sim.worldLootItems.any { it.kind == LootItemKind.COIN })
    assertTrue("Houses should contain medkits", sim.worldLootItems.any { it.kind == LootItemKind.MEDKIT })
    assertTrue("Houses should contain guns", sim.worldLootItems.any { it.kind.category == LootCategory.WEAPON })
    assertTrue("Houses should contain Lv.3 Vest", sim.worldLootItems.any { it.kind == LootItemKind.VEST_LV3 })
    assertTrue("Houses should contain Lv.4 Vest", sim.worldLootItems.any { it.kind == LootItemKind.VEST_LV4 })
    assertTrue("Houses should contain Lv.3 Helmet", sim.worldLootItems.any { it.kind == LootItemKind.HELMET_LV3 })
    assertTrue("Houses should contain Lv.4 Helmet", sim.worldLootItems.any { it.kind == LootItemKind.HELMET_LV4 })

    val startCash = sim.playerCash
    val house0Loot = sim.worldLootItems.filter { it.houseId == sim.world.houses.first().id }
    assertTrue("First house should have multiple loot pickups", house0Loot.size >= 5)

    // Walk onto each house loot item -> auto-pickup collects coins, gun, vest, helmet & medkit!
    for (loot in house0Loot) {
      sim.playerX = loot.x
      sim.playerY = loot.y
      sim.playerZ = loot.z
      sim.step(0.02f)
    }

    assertTrue("Coin auto-pickup should increase cash", sim.playerCash > startCash)
    assertTrue("House gun auto-pickup should unlock a firearm", sim.ownedWeapons.size > 1)
    assertTrue("House armor auto-pickup should equip Lv.3 or Lv.4 Vest", sim.playerVestLevel >= 3)
    assertTrue("House armor auto-pickup should equip Lv.3 or Lv.4 Helmet", sim.playerHelmetLevel >= 3)

    val medkitItem = sim.bagItems.firstOrNull { it.kind == LootItemKind.MEDKIT }
    assertNotNull("Medkit should be stored inside Backpack", medkitItem)

    // Injure player and use quick Medkit to restore health
    sim.playerHealth = 35f
    assertTrue("Quick Medkit should heal injured player", sim.useQuickMedkit())
    assertTrue("Health should recover toward 100", sim.playerHealth >= 95f)
  }

  @Test
  fun backpackEnforcesMaxCapacityAndRemovalDropsItemBackToWorld() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.isPlaying = true

    // Collect 8 non-currency bag items (Medkits & Guns)
    val medAndWeaponLoot =
      sim.worldLootItems.filter {
        it.kind.category == LootCategory.MEDICAL || it.kind.category == LootCategory.WEAPON
      }
    assertTrue("World should have more than 8 medkit/weapon pickups", medAndWeaponLoot.size >= 10)

    for (loot in medAndWeaponLoot.take(8)) {
      sim.playerX = loot.x
      sim.playerY = loot.y
      sim.playerZ = loot.z
      sim.step(0.02f)
    }
    assertEquals(GameSimulation.MAX_BAG_SLOTS, sim.bagItems.size)
    assertTrue("HUD should report bag full at 8/8", sim.snapshotHud(isMuted = false).isBagFull)

    // 9th non-currency item must NOT be picked up while bag is 8/8 full
    val ninth = medAndWeaponLoot[8]
    val countBeforeNinth = sim.worldLootItems.size
    sim.playerX = ninth.x
    sim.playerY = ninth.y
    sim.playerZ = ninth.z
    sim.step(0.02f)
    assertEquals("9th item must stay on ground when bag is full", countBeforeNinth, sim.worldLootItems.size)
    assertEquals(GameSimulation.MAX_BAG_SLOTS, sim.bagItems.size)

    // Open bag, remove 1 item -> drops back to 3D world and frees slot so 9th item auto-loots!
    sim.openBag()
    assertTrue(sim.isBagOpen)
    val firstBagUid = sim.bagItems.first().uid
    assertTrue("Removing item from full bag should succeed", sim.removeBagItem(firstBagUid, dropToWorld = true))
    assertEquals(GameSimulation.MAX_BAG_SLOTS - 1, sim.bagItems.size)
    assertEquals("Removed item should reappear in world loot", countBeforeNinth + 1, sim.worldLootItems.size)
    sim.closeBag()

    sim.step(0.02f)
    assertEquals("Free slot now allows 9th item to auto-pickup", GameSimulation.MAX_BAG_SLOTS, sim.bagItems.size)
  }

  @Test
  fun rivalFightersEngageWithGunsAndMeleeWhileArmorMitigatesDamage() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.isPlaying = true

    val rival = sim.pedestrians.first { it.isRivalFighter }
    assertTrue("Rival fighter should spawn armed with a gun", rival.weapon != WeaponType.FISTS)
    assertTrue("Rival should wear Lv.3 or Lv.4 armor", rival.vestLevel >= 3 || rival.helmetLevel >= 3)

    // Place player within open line-of-sight range (10m) in front of rival -> triggers combat & enemy gunfire
    sim.playerX = rival.x + 10f
    sim.playerZ = rival.z
    sim.playerY = sim.computeGroundHeight(sim.playerX, sim.playerZ, 0f)
    rival.y = sim.computeGroundHeight(rival.x, rival.z, 0f)
    sim.playerHealth = 100f

    var sawEnemyBullet = false
    repeat(40) {
      sim.step(0.03f)
      if (sim.bulletProjectiles.any { it.fromEnemy }) sawEnemyBullet = true
    }
    assertEquals("Rival should spot player and enter FIGHTING_BACK", NpcReactionState.FIGHTING_BACK, rival.reactionState)
    assertTrue("Armed rival should fire enemy bullet projectiles at player", sawEnemyBullet)

    // Equip Lv.4 Vest & Lv.4 Helmet -> incoming damage is reduced cleanly
    sim.playerVestLevel = 4
    sim.playerVestDurability = LootItemKind.VEST_LV4.maxDurability
    sim.playerHelmetLevel = 4
    sim.playerHelmetDurability = LootItemKind.HELMET_LV4.maxDurability
    sim.playerHealth = 100f
    // Move right up to melee distance so rival throws punches/kicks too
    sim.playerX = rival.x + 1.4f
    sim.playerZ = rival.z
    repeat(45) { sim.step(0.03f) }
    assertTrue("Player armor durability should absorb part of rival strikes", sim.playerVestDurability < LootItemKind.VEST_LV4.maxDurability || sim.playerHealth < 100f)

    // Player fights back using Hand Punch & Foot Kick to KO armored rival -> rival drops loot!
    val lootBeforeKo = sim.worldLootItems.size
    repeat(8) {
      if (rival.reactionState != NpcReactionState.KNOCKED_DOWN) {
        sim.playerX = rival.x + 1.25f
        sim.playerZ = rival.z
        if (it % 2 == 0) sim.triggerPunch() else sim.triggerKick()
        repeat(16) { sim.step(0.03f) }
      }
    }
    assertEquals("Armored rival should be knocked down after combo punches & kicks", NpcReactionState.KNOCKED_DOWN, rival.reactionState)
    assertTrue("Knocked out rival should drop loot (coin/medkit/gun)", sim.worldLootItems.size > lootBeforeKo)
  }

  @Test
  fun eliminationShowsKillerSpectateReadOnlyModeRewardsCoinsAndReturnsToLobby() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.startAirplaneDrop()
    sim.jumpFromAirplane()
    sim.dropPhase = DropPhase.GROUND
    sim.playerY = sim.computeGroundHeight(sim.playerX, sim.playerZ, 0f)

    val rival = sim.pedestrians.first { it.isRivalFighter }
    rival.y = sim.computeGroundHeight(rival.x, rival.z, 0f)
    sim.playerX = rival.x + 1.5f
    sim.playerZ = rival.z
    sim.playerHealth = 12f
    sim.playerVestLevel = 0
    sim.playerVestDurability = 0f
    sim.playerHelmetLevel = 0
    sim.playerHelmetDurability = 0f
    val cashBeforeElim = sim.playerCash

    // Step until rival eliminates the player
    repeat(80) {
      if (!sim.isPlayerEliminated) sim.step(0.03f)
    }

    assertTrue("Player should enter eliminated state instead of instant respawn", sim.isPlayerEliminated)
    assertFalse("Spectating should wait until player taps SHOW", sim.isSpectatingKiller)
    assertTrue("Match coin reward should be awarded on elimination", sim.playerCash > cashBeforeElim)

    val preLockSnap = sim.snapshotHud(isMuted = false)
    assertTrue(preLockSnap.lastMatchCoinsEarned > 0)
    assertNotNull(preLockSnap.eliminatedByName)

    // Attempt to move, sprint, punch, and fire while eliminated -> must remain locked
    val elimX = sim.playerX
    val elimZ = sim.playerZ
    sim.input.moveX = 1f
    sim.input.moveY = -1f
    sim.input.sprintHeld = true
    sim.triggerPunch()
    sim.input.fireQueued = true
    repeat(15) { sim.step(0.03f) }
    assertEquals("Eliminated player must not move", elimX, sim.playerX, 0.001f)
    assertEquals("Eliminated player must not move", elimZ, sim.playerZ, 0.001f)
    assertNull("Eliminated player must not attack", sim.activeAttackMove)

    // Tap SHOW -> read-only killer spectate mode activates
    sim.startSpectatingKiller()
    assertTrue("Spectating killer mode should be active", sim.isSpectatingKiller)
    val killerStartX = rival.x
    val killerStartZ = rival.z

    // Try moving/controlling killer via player input -> killer moves autonomously, player stays locked
    sim.input.moveX = -1f
    sim.input.moveY = -1f
    sim.input.sprintHeld = true
    repeat(35) { sim.step(0.03f) }
    assertEquals("Player position stays locked while spectating", elimX, sim.playerX, 0.001f)
    assertEquals("Player position stays locked while spectating", elimZ, sim.playerZ, 0.001f)
    assertTrue(
      "Spectated killer should actively roam/move on their own",
      CollisionSolver.distXZ(killerStartX, killerStartZ, rival.x, rival.z) > 0.3f,
    )
    assertTrue(
      "Camera look-at should follow killer location",
      CollisionSolver.distXZ(sim.camLookAt.x, sim.camLookAt.z, rival.x, rival.z) < 0.8f,
    )

    // Tap BACK -> returns cleanly to Lobby with earned coins intact for a new match
    val coinsAfterMatch = sim.playerCash
    sim.returnToLobby()
    assertFalse(sim.isPlaying)
    assertFalse(sim.isPlayerEliminated)
    assertFalse(sim.isSpectatingKiller)
    assertEquals("Earned coins must remain saved after returning to lobby", coinsAfterMatch, sim.playerCash)
  }

  @Test
  fun lobbyStyleShopBuysAndEquipsClothesHairPantsGunPaintAndCarSkin() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.playerCash = 3000

    sim.openStyleShop()
    assertTrue(sim.isStyleShopOpen)

    val startCash = sim.playerCash
    assertTrue(sim.buyOrEquipCloth(ClothStyle.CYBER_NEON))
    assertTrue(sim.buyOrEquipHair(HairCustomization.CYBER_SPIKES))
    assertTrue(sim.buyOrEquipPants(PantsStyle.CYBER_DENIM))
    assertTrue(sim.buyOrEquipGunSkin(GunSkin.GOLD_PLATED))
    assertTrue(sim.buyOrEquipCarSkin(CarSkin.NEON_VICE))

    val expectedCost =
      ClothStyle.CYBER_NEON.price +
        HairCustomization.CYBER_SPIKES.price +
        PantsStyle.CYBER_DENIM.price +
        GunSkin.GOLD_PLATED.price +
        CarSkin.NEON_VICE.price
    assertEquals(startCash - expectedCost, sim.playerCash)

    // Verify 3D Hero appearance reflects equipped Cloth, Hair Style & Pants
    val heroLook = sim.currentHeroAppearance()
    assertEquals(HairStyle.SPIKY_CROWN, heroLook.hairStyle)
    assertEquals(ClothStyle.CYBER_NEON.tunicColor!![0], heroLook.tunicColor[0], 0.001f)
    assertEquals(PantsStyle.CYBER_DENIM.pantsColor!![2], heroLook.pantsColor[2], 0.001f)

    // Verify snapshot exposes all equipped & owned customizations
    val snap = sim.snapshotHud(isMuted = false)
    assertEquals(ClothStyle.CYBER_NEON, snap.equippedCloth)
    assertEquals(HairCustomization.CYBER_SPIKES, snap.equippedHair)
    assertEquals(PantsStyle.CYBER_DENIM, snap.equippedPants)
    assertEquals(GunSkin.GOLD_PLATED, snap.equippedGunSkin)
    assertEquals(CarSkin.NEON_VICE, snap.equippedCarSkin)
    assertTrue(GunSkin.GOLD_PLATED in snap.ownedGunSkins)
    assertTrue(CarSkin.NEON_VICE in snap.ownedCarSkins)

    // Re-equipping an already owned item must be free
    val cashBeforeFreeSwap = sim.playerCash
    assertTrue(sim.buyOrEquipGunSkin(GunSkin.STANDARD_STEEL))
    assertEquals(cashBeforeFreeSwap, sim.playerCash)
    assertEquals(GunSkin.STANDARD_STEEL, sim.equippedGunSkin)

    sim.closeStyleShop()
    assertFalse(sim.isStyleShopOpen)
  }

  @Test
  fun expandedWorldCoversAllFourDirectionsWithWaterfallsMarketAndRailway() {
    val world = WorldBuilder.buildKeyBayside()

    // Core baseline spawns preserved (with all AI traffic cars removed)
    assertEquals(10, world.houses.size)
    assertEquals(10, world.vehicleSpawns.size)
    assertTrue("Zero AI traffic cars on Nextera", world.vehicleSpawns.none { it.isAiTraffic })
    assertEquals(3, world.boatSpawns.size)

    // Nextera landmarks, waterfalls, market & on-foot Battle Royale bots
    assertEquals(2, world.waterfalls.size)
    assertTrue("Expected at least 6 Farmtopia produce market stalls", world.marketStalls.size >= 6)
    assertTrue("Old perimeter railway removed for Nextera", world.railwayLoopWaypoints.isEmpty())
    assertTrue("Decorative market bot routes should be removed", world.marketBotRoutes.isEmpty())
    assertTrue("Expected Battle Royale bot spawns across Nextera POIs", world.pedestrianSpawns.size >= 20)

    // Land checks across all Nextera islands & lobes (North Intellect, West Turbine, East Rust, South Grav/Deca)
    assertTrue("North Intellect Center island must be solid land", CollisionSolver.isOnLand(-18f, -184f))
    assertTrue("West Turbine bluff island must be solid land", CollisionSolver.isOnLand(-218f, 6f))
    assertTrue("East Rust Town & Mortar Ruins must be solid land", CollisionSolver.isOnLand(180f, 16f))
    assertTrue("South Deca Square must be solid land", CollisionSolver.isOnLand(24f, 172f))
  }

  @Test
  fun livingWorldRoutesWeatherRipplesMarketBotsAndAmbientSound() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.startAirplaneDrop()
    sim.dropPhase = DropPhase.GROUND
    sim.playerCash = 500

    val initialHeliPos = sim.livingWorld.helicopters.first().let { it.x to it.z }
    val initialAirlinerPos = sim.livingWorld.ambientAirplanes.first().let { it.x to it.z }
    val initialBirdPos = sim.livingWorld.birds.first().let { it.x to it.z }
    val initialCloudPos = sim.livingWorld.clouds.first().let { it.x to it.z }

    // Advance simulation across several seconds so sky routes & bots progress
    repeat(180) {
      sim.step(0.05f)
    }

    val movedHeli = sim.livingWorld.helicopters.first().let { it.x to it.z }
    val movedAirliner = sim.livingWorld.ambientAirplanes.first().let { it.x to it.z }
    val movedBird = sim.livingWorld.birds.first().let { it.x to it.z }
    val movedCloud = sim.livingWorld.clouds.first().let { it.x to it.z }

    assertNotEquals(initialHeliPos, movedHeli)
    assertNotEquals(initialAirlinerPos, movedAirliner)
    assertNotEquals(initialBirdPos, movedBird)
    assertNotEquals(initialCloudPos, movedCloud)
    assertTrue(
      "Decorative market shopper bots should be removed",
      sim.livingWorld.marketBots.isEmpty(),
    )
    assertTrue(
      "Expected real Battle Royale bots patrolling East Nextera zones (Rust/Mortar)",
      sim.pedestrians.any { it.isBrBot && it.x > 140f },
    )

    // Weather control
    sim.cycleWeatherMode()
    assertNotEquals(WeatherMode.CLEAR_BREEZE, sim.livingWorld.weatherMode)
    sim.setWeatherMode(WeatherMode.THUNDERSTORM)
    repeat(20) { sim.step(0.05f) }
    assertTrue("Thunderstorm should increase rain intensity", sim.livingWorld.rainIntensity > 0.3f)

    // Teleport to Farmtopia Produce Market & buy snack
    sim.teleportToMarket()
    sim.playerHealth = 50f
    val cashBefore = sim.playerCash
    val bought = sim.buyMarketSnack()
    assertTrue("Player near market stall should be able to buy snack", bought)
    assertEquals(cashBefore - 25, sim.playerCash)
    assertEquals(85f, sim.playerHealth, 0.01f)
    val marketAudio = sim.computeAmbientAudioSnapshot()
    assertTrue("Market crowd ambient audio should be elevated at bazaar", marketAudio.marketPeopleLevel > 0.25f)

    // Teleport to Intellect Moat Waterfall & check surf/waterfall sound level
    sim.teleportToWaterfall()
    sim.step(0.05f)
    val waterfallAudio = sim.computeAmbientAudioSnapshot()
    assertTrue("Waterfall/surf ambient audio should be elevated near cascades", waterfallAudio.waterSurfLevel > 0.3f)
  }

  @Test
  fun battleRoyaleBotsLootHousesHealWithMedkitsAndUseGlooWallsAndCover() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.startAirplaneDrop()
    sim.dropPhase = DropPhase.GROUND
    // Park player safely away at Grav Labs so player doesn't interrupt looting test
    sim.playerX = -158f
    sim.playerZ = 168f

    val bot = sim.pedestrians.first { !it.isRivalFighter }
    // Give bot a gun, medkit, gloo wall, and put a loot item right beside it on flat Farmtopia road
    bot.x = -44f
    bot.z = 16f
    bot.y = 0f
    bot.gracePeriodSec = 0f
    bot.weapon = WeaponType.FISTS
    val gunLoot =
      sim.worldLootItems.firstOrNull { it.kind.category == LootCategory.WEAPON }
    assertNotNull("Expected world gun loot item", gunLoot)
    gunLoot!!.x = bot.x + 0.5f
    gunLoot.z = bot.z

    repeat(10) { sim.step(0.05f) }
    assertTrue("BR bot should pick up nearby gun loot into bag/weapon", bot.weapon != WeaponType.FISTS)

    // Now simulate an injured bot under threat > 4.2m away -> deploys Gloo Wall, takes cover, and heals with medkit
    val shooter = sim.pedestrians.first { it.isRivalFighter && it.id != bot.id }
    shooter.x = -32f
    shooter.z = 16f
    shooter.y = 0f
    shooter.gracePeriodSec = 0f

    bot.hp = 45f
    bot.botMedkits = 2
    bot.botGlooWalls = 2
    bot.glooWallCooldownSec = 0f
    bot.reactionState = NpcReactionState.FIGHTING_BACK

    var deployedGlooOrTookCover = false
    var healedWithMedkit = false
    repeat(80) {
      sim.step(0.05f)
      if (sim.glooWalls.isNotEmpty() || bot.botMode == BotTacticalMode.TAKING_COVER || bot.sitProgress > 0.1f) {
        deployedGlooOrTookCover = true
      }
      if (bot.hp > 60f && bot.botMedkits < 2) {
        healedWithMedkit = true
      }
    }

    assertTrue("Injured BR bot under fire should deploy Gloo Wall / take cover", deployedGlooOrTookCover)
    assertTrue("Injured BR bot with medkits should heal behind cover", healedWithMedkit)
  }

  @Test
  fun battleRoyaleBotsFightEachOtherAndDropLootOnKo() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.startAirplaneDrop()
    sim.dropPhase = DropPhase.GROUND
    // Keep player far away at Grav Labs so bots focus on each other
    sim.playerX = -158f
    sim.playerZ = 168f

    val rivals = sim.pedestrians.filter { it.isRivalFighter }.take(2)
    assertEquals(2, rivals.size)
    val botA = rivals[0]
    val botB = rivals[1]

    // Freeze other peds out of the duel arena
    sim.pedestrians.forEach {
      if (it.id != botA.id && it.id != botB.id) {
        it.x = -158f
        it.z = 168f
      }
    }

    botA.x = -44f
    botA.z = 16f
    botA.y = 0f
    botA.gracePeriodSec = 0f
    botA.weapon = WeaponType.ASSAULT_RIFLE
    botA.hp = 100f
    botA.botGlooWalls = 0
    botA.botMedkits = 0

    botB.x = -35f
    botB.z = 16f
    botB.y = 0f
    botB.gracePeriodSec = 0f
    botB.weapon = WeaponType.PISTOL_9MM
    botB.hp = 35f
    botB.vestLevel = 0
    botB.vestDurability = 0f
    botB.helmetLevel = 0
    botB.helmetDurability = 0f
    botB.botGlooWalls = 0
    botB.botMedkits = 0

    val initialLootCount = sim.worldLootItems.size
    repeat(80) {
      sim.step(0.05f)
    }

    val eitherKo =
      botA.reactionState == NpcReactionState.KNOCKED_DOWN ||
        botB.reactionState == NpcReactionState.KNOCKED_DOWN
    assertTrue("Two armed BR bots facing each other should fight until one is knocked down", eitherKo)
    assertTrue("Knocked down BR bot should drop loot into world", sim.worldLootItems.size >= initialLootCount)
  }

  @Test
  fun battleRoyaleBotsDriveCarsAndPlayerGlooWallBlocksBullets() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.startAirplaneDrop()
    sim.dropPhase = DropPhase.GROUND

    // 1. Player deploys a Gloo Wall on flat Farmtopia road and verifies bullets are blocked & damage the Gloo Wall
    sim.pedestrians.forEach {
      it.x = -158f
      it.z = 168f
    }
    sim.teleportOnFootTo(-44f, 16f, kotlin.math.PI.toFloat() * 0.5f) // facing +X
    sim.playerHealth = 100f
    val glooBefore = sim.playerReserveGlooWalls
    assertTrue("Player should start with starter Gloo Walls and deploy one", sim.deployPlayerGlooWall())
    assertEquals(glooBefore - 1, sim.playerReserveGlooWalls)
    assertEquals(1, sim.glooWalls.size)
    val wall = sim.glooWalls.first()
    val wallHpBefore = wall.hp

    // Fire an enemy bullet directly through the Gloo Wall toward player
    sim.bulletProjectiles.add(
      com.example.game.state.BulletProjectileState(
        x = -36f,
        y = 1.2f,
        z = 16f,
        prevX = -36f,
        prevY = 1.2f,
        prevZ = 16f,
        vx = -90f,
        vy = 0f,
        vz = 0f,
        damage = 40f,
        knockback = 2f,
        remainingDist = 25f,
        fromEnemy = true,
        fromPlayer = false,
      )
    )
    repeat(10) { sim.step(0.02f) }
    assertEquals("Gloo Wall must protect player from incoming bullet", 100f, sim.playerHealth, 0.01f)
    assertTrue("Gloo Wall must absorb bullet damage", wall.hp < wallHpBefore)

    // 2. Verify all AI bot cars are removed and BR bots remain on foot without driving cars
    assertTrue("Zero AI traffic cars must be spawned", sim.vehicles.none { it.isAiTraffic })
    val brBot = sim.pedestrians.first { it.isBrBot && !it.isFriend }
    val parkedCar = sim.vehicles.first()
    brBot.x = parkedCar.x + 1.5f
    brBot.z = parkedCar.z
    repeat(20) { sim.step(0.05f) }
    assertNull("BR bots must stay on foot and never claim cars", brBot.drivingVehicleId)
    assertNull("Parked cars must never get an AI bot driver", parkedCar.driverBotId)
  }

  @Test
  fun hudLayoutConfigResizesIndividualAndGlobalIconsAndRoundTrips() {
    var config = HudLayoutConfig.DEFAULT
    assertEquals(1.0f, config.effectiveScale(HudElementId.SHOOT), 0.001f)

    // Resize individual icons smaller and larger
    config = config
      .withElementScale(HudElementId.SHOOT, 1.45f)
      .withElementScale(HudElementId.JOYSTICK, 0.70f)
      .withElementScale(HudElementId.MINIMAP, 0.65f)
      .withElementScale(HudElementId.GLOO_WALL, 1.30f)
      .withMasterScale(0.90f)

    assertEquals(1.45f, config.rawScale(HudElementId.SHOOT), 0.001f)
    assertEquals(1.305f, config.effectiveScale(HudElementId.SHOOT), 0.01f)
    assertEquals(0.63f, config.effectiveScale(HudElementId.JOYSTICK), 0.01f)
    assertEquals(0.90f, config.effectiveScale(HudElementId.JUMP), 0.01f)

    // Serialize & deserialize preserves all per-icon sizes
    val restored = HudLayoutConfig.deserialize(config.serialize())
    assertEquals(config.masterScale, restored.masterScale, 0.001f)
    assertEquals(config.rawScale(HudElementId.SHOOT), restored.rawScale(HudElementId.SHOOT), 0.001f)
    assertEquals(config.rawScale(HudElementId.JOYSTICK), restored.rawScale(HudElementId.JOYSTICK), 0.001f)
    assertEquals(config.rawScale(HudElementId.MINIMAP), restored.rawScale(HudElementId.MINIMAP), 0.001f)
    assertEquals(config.rawScale(HudElementId.GLOO_WALL), restored.rawScale(HudElementId.GLOO_WALL), 0.001f)

    // Resetting single icon vs resetting all
    val afterSingleReset = restored.resetElement(HudElementId.SHOOT)
    assertEquals(1.0f, afterSingleReset.rawScale(HudElementId.SHOOT), 0.001f)
    assertEquals(0.70f, afterSingleReset.rawScale(HudElementId.JOYSTICK), 0.001f)

    val afterResetAll = afterSingleReset.resetAll()
    assertEquals(HudLayoutConfig.DEFAULT, afterResetAll)
  }

  @Test
  fun oneTapAutoSprintMovesForwardWithoutJoystickAndTogglesOff() {
    val sim = GameSimulation(WorldBuilder.buildKeyBayside())
    sim.isPlaying = true
    sim.dropPhase = DropPhase.GROUND
    sim.camYawRad = kotlin.math.PI.toFloat()
    sim.playerYawRad = 0f

    val startX = sim.playerX
    val startZ = sim.playerZ

    // Tap SPRINT once without touching the movement joystick
    assertTrue("Auto-sprint should activate on single tap", sim.toggleAutoSprint())
    sim.input.moveX = 0f
    sim.input.moveY = 0f
    repeat(30) { sim.step(0.03f) }

    val snapRunning = sim.snapshotHud(isMuted = false)
    assertTrue("HudSnapshot should report isAutoSprinting=true", snapRunning.isAutoSprinting)
    assertTrue("HudSnapshot should report isSprinting=true", snapRunning.isSprinting)
    assertTrue(
      "Player should sprint forward without holding the joystick",
      CollisionSolver.distXZ(startX, startZ, sim.playerX, sim.playerZ) > 3.5f,
    )

    // Tap SPRINT again -> stops auto-sprint cleanly
    assertFalse("Second tap should turn auto-sprint off", sim.toggleAutoSprint())
    val stopX = sim.playerX
    val stopZ = sim.playerZ
    repeat(15) { sim.step(0.03f) }

    val snapStopped = sim.snapshotHud(isMuted = false)
    assertFalse("HudSnapshot should report isAutoSprinting=false", snapStopped.isAutoSprinting)
    assertEquals(stopX, sim.playerX, 0.01f)
    assertEquals(stopZ, sim.playerZ, 0.01f)
  }

  @Test
  fun hudLayoutConfigSavesAndResetsDragPositionsAndDrivingPedals() {
    var config = HudLayoutConfig.DEFAULT
      .withElementOffset(HudElementId.MINIMAP, 0.42f, 0.18f)
      .withElementOffset(HudElementId.DRIVING_PEDALS, -0.35f, -0.25f)
      .withElementScale(HudElementId.DRIVING_PEDALS, 1.35f)
      .nudgeElementOffset(HudElementId.SHOOT, -0.20f, -0.10f)

    val restored = HudLayoutConfig.deserialize(config.serialize())
    assertEquals(0.42f, restored.offsetOf(HudElementId.MINIMAP).normX, 0.005f)
    assertEquals(0.18f, restored.offsetOf(HudElementId.MINIMAP).normY, 0.005f)
    assertEquals(-0.35f, restored.offsetOf(HudElementId.DRIVING_PEDALS).normX, 0.005f)
    assertEquals(1.35f, restored.rawScale(HudElementId.DRIVING_PEDALS), 0.005f)
    assertEquals(-0.20f, restored.offsetOf(HudElementId.SHOOT).normX, 0.005f)

    // Extreme values clamp cleanly inside screen bounds
    val clamped = restored.withElementOffset(HudElementId.JOYSTICK, 2.5f, -3.0f)
    assertEquals(0.88f, clamped.offsetOf(HudElementId.JOYSTICK).normX, 0.001f)
    assertEquals(-0.88f, clamped.offsetOf(HudElementId.JOYSTICK).normY, 0.001f)

    // Reset offset only preserves scale, resetElement clears both
    val resetPosOnly = clamped.resetElementOffset(HudElementId.DRIVING_PEDALS)
    assertEquals(0f, resetPosOnly.offsetOf(HudElementId.DRIVING_PEDALS).normX, 0.001f)
    assertEquals(1.35f, resetPosOnly.rawScale(HudElementId.DRIVING_PEDALS), 0.001f)

    val resetFull = resetPosOnly.resetElement(HudElementId.MINIMAP)
    assertEquals(0f, resetFull.offsetOf(HudElementId.MINIMAP).normX, 0.001f)
    assertEquals(1f, resetFull.rawScale(HudElementId.MINIMAP), 0.001f)
  }

  @Test
  fun terrainHeightfieldCreatesUndulatingHillsAndSyncsPhysicsAndCars() {
    val world = WorldBuilder.buildKeyBayside()
    assertEquals(10, world.houses.size)
    assertTrue("Expected Free Fire gabled roofs", world.gabledRoofs.isNotEmpty())
    assertTrue("Expected angular rocky boulders", world.boulders.size >= 10)
    assertTrue("Expected spinning wind turbines", world.windTurbines.size >= 3)
    assertTrue("Expected futuristic geodesic domes", world.domes.size >= 3)
    assertTrue(
      "Expected pine conifers & acacia trees",
      world.trees.any { it.kind == com.example.game.world.TreeKind.PINE_CONIFER } &&
        world.trees.any { it.kind == com.example.game.world.TreeKind.CYPRESS_ACACIA },
    )

    // Flat infrastructure stays smoothly at y ~ 0 while Nextera mountains/hills undulate realistically
    val roadY = com.example.game.world.TerrainHeightfield.elevationAt(-44f, 16f)
    assertTrue("Main Nextera Farmtopia highway should stay flat near 0f (was $roadY)", roadY in -0.15f..0.20f)

    val peakY = com.example.game.world.TerrainHeightfield.elevationAt(156f, -158f)
    assertTrue("Twin Bridge / Mortar mountain peak should rise dramatically (was $peakY)", peakY > 15f)

    val intellectY = com.example.game.world.TerrainHeightfield.elevationAt(-18f, -180f)
    assertTrue("Intellect Center hilltop plateau should be elevated (was $intellectY)", intellectY > 2.0f)

    // Driving car onto elevated Intellect Center plateau updates car.y and playerY cleanly above 0
    val sim = GameSimulation(world)
    sim.isPlaying = true
    sim.dropPhase = DropPhase.GROUND
    val car = sim.vehicles.first { !it.isAiTraffic }
    car.x = -18f
    car.z = -180f
    sim.activeVehicle = car
    repeat(15) { sim.step(0.04f) }
    assertTrue("Car on Intellect Citadel plateau should sit on elevated ground (${car.y})", car.y > 2.0f)
    assertTrue("Player inside elevated car should match car elevation (${sim.playerY})", sim.playerY > 2.2f)
  }
}
