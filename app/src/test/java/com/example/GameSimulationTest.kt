package com.example

import com.example.game.state.GameSimulation
import com.example.game.world.WorldBuilder
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
    assertEquals(3, world.houses.size)
    assertTrue("Expected palm and tropical trees", world.trees.size >= 24)
    assertEquals(6, world.vehicleSpawns.size) // 2 player + 4 AI traffic cars
    assertEquals(5, world.boatSpawns.size) // 2 pier boats + 3 AI ocean boats
    assertTrue(world.pedestrianSpawns.size >= 6)
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
}
