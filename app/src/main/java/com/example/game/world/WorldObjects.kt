package com.example.game.world

import com.example.game.math.AabbCollider
import com.example.game.math.CircleCollider

enum class MaterialSurface(val id: Int) {
  GRASS(0),
  ASPHALT(1),
  SAND(2),
  STUCCO(3),
  WOOD(4),
  TILE(5),
  CAR_PAINT(6),
  FOLIAGE(7),
  WATER(8),
  EMISSIVE(9),
  GLASS(10),
  SLATE(11),
}

enum class TreeKind {
  PALM,
  TROPICAL_SHADE,
}

data class HouseSpec(
  val id: String,
  val name: String,
  val subtitle: String,
  val centerX: Float,
  val centerZ: Float,
  val width: Float,
  val depth: Float,
  val wallHeight: Float,
  val facingSouth: Boolean,
  val primaryColor: FloatArray,
  val accentColor: FloatArray,
  val roofColor: FloatArray,
  val neonColor: FloatArray,
  val hasPool: Boolean = false,
)

data class TreeSpec(
  val x: Float,
  val z: Float,
  val height: Float,
  val yawDeg: Float,
  val kind: TreeKind,
  val tintShift: Float = 0f,
)

data class StreetLightSpec(
  val x: Float,
  val z: Float,
  val yawDeg: Float,
  val neonColor: FloatArray,
)

data class PropBoxSpec(
  val cx: Float,
  val cy: Float,
  val cz: Float,
  val sx: Float,
  val sy: Float,
  val sz: Float,
  val color: FloatArray,
  val surface: MaterialSurface,
  val yawDeg: Float = 0f,
  val emissive: Float = 0f,
  val metallic: Float = 0f,
  val roughness: Float = 0.65f,
  val castShadow: Boolean = true,
)

data class VehicleSpawnSpec(
  val id: String,
  val displayName: String,
  val x: Float,
  val z: Float,
  val yawRad: Float,
  val bodyColor: FloatArray,
  val trimColor: FloatArray,
  val maxSpeed: Float,
  val accel: Float,
  val isAiTraffic: Boolean = false,
  val loopRouteIndex: Int = 0,
  val driverShirtColor: FloatArray = floatArrayOf(0.95f, 0.45f, 0.35f),
)

data class BoatSpawnSpec(
  val id: String,
  val displayName: String,
  val x: Float,
  val z: Float,
  val yawRad: Float,
  val hullColor: FloatArray,
  val accentColor: FloatArray,
  val maxSpeed: Float = 28f,
  val isAiCruise: Boolean = false,
  val routeRadiusX: Float = 96f,
  val routeRadiusZ: Float = 96f,
  val routePhase: Float = 0f,
  val captainShirtColor: FloatArray = floatArrayOf(0.96f, 0.85f, 0.25f),
)

data class PedestrianSpawnSpec(
  val id: String,
  val waypoints: List<Pair<Float, Float>>,
  val shirtColor: FloatArray,
  val pantsColor: FloatArray,
  val speed: Float = 2.1f,
)

data class WorldData(
  val houses: List<HouseSpec>,
  val trees: List<TreeSpec>,
  val streetLights: List<StreetLightSpec>,
  val staticBoxes: List<PropBoxSpec>,
  val glassBoxes: List<PropBoxSpec>,
  val aabbColliders: List<AabbCollider>,
  val circleColliders: List<CircleCollider>,
  val vehicleSpawns: List<VehicleSpawnSpec>,
  val boatSpawns: List<BoatSpawnSpec>,
  val roadLoopWaypointsOuter: List<Pair<Float, Float>>,
  val roadLoopWaypointsInner: List<Pair<Float, Float>>,
  val pedestrianSpawns: List<PedestrianSpawnSpec>,
  val playerSpawnX: Float,
  val playerSpawnZ: Float,
  val playerSpawnYaw: Float,
)
