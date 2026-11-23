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
  SKIN(12),
  CLOTH(13),
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
  val floors: Int = 1,
  val facingX: Int = 0, // 0 = use facingSouth (+/-Z); +1 = facing East (+X); -1 = facing West (-X)
  val hasRooftop: Boolean = false,
)

enum class RampAxis {
  NONE,
  POS_X,
  NEG_X,
  POS_Z,
  NEG_Z,
}

data class WalkableFloorSpec(
  val minX: Float,
  val minZ: Float,
  val maxX: Float,
  val maxZ: Float,
  val baseElevationY: Float,
  val endElevationY: Float = baseElevationY,
  val rampAxis: RampAxis = RampAxis.NONE,
  val tag: String = "",
) {
  fun elevationAt(x: Float, z: Float): Float {
    val t =
      when (rampAxis) {
        RampAxis.NONE -> 0f
        RampAxis.POS_X -> ((x - minX) / (maxX - minX).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
        RampAxis.NEG_X -> ((maxX - x) / (maxX - minX).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
        RampAxis.POS_Z -> ((z - minZ) / (maxZ - minZ).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
        RampAxis.NEG_Z -> ((maxZ - z) / (maxZ - minZ).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
      }
    return baseElevationY + (endElevationY - baseElevationY) * t
  }
}

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

data class PropCylinderSpec(
  val cx: Float,
  val cy: Float,
  val cz: Float,
  val radius: Float,
  val height: Float,
  val color: FloatArray,
  val surface: MaterialSurface,
  val metallic: Float = 0.15f,
  val roughness: Float = 0.45f,
  val emissive: Float = 0f,
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
  val appearance: CharacterAppearance = CartoonPresets.CHUBBY_EMERALD,
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
  val routeCenterX: Float = 0f,
  val captainShirtColor: FloatArray = floatArrayOf(0.96f, 0.85f, 0.25f),
  val appearance: CharacterAppearance = CartoonPresets.COASTAL_CAPTAIN,
)

data class PedestrianSpawnSpec(
  val id: String,
  val waypoints: List<Pair<Float, Float>>,
  val shirtColor: FloatArray,
  val pantsColor: FloatArray,
  val speed: Float = 2.1f,
  val appearance: CharacterAppearance = CartoonPresets.LEAN_BUDDY_BLUE,
  val isBraveDefender: Boolean = false,
)

data class WorldData(
  val houses: List<HouseSpec>,
  val trees: List<TreeSpec>,
  val streetLights: List<StreetLightSpec>,
  val staticBoxes: List<PropBoxSpec>,
  val staticCylinders: List<PropCylinderSpec> = emptyList(),
  val glassBoxes: List<PropBoxSpec>,
  val aabbColliders: List<AabbCollider>,
  val circleColliders: List<CircleCollider>,
  val walkableFloors: List<WalkableFloorSpec>,
  val vehicleSpawns: List<VehicleSpawnSpec>,
  val boatSpawns: List<BoatSpawnSpec>,
  val roadLoopWaypointsOuter: List<Pair<Float, Float>>,
  val roadLoopWaypointsInner: List<Pair<Float, Float>>,
  val roadLoopWaypointsUptown: List<Pair<Float, Float>>,
  val roadLoopWaypointsTown: List<Pair<Float, Float>>,
  val pedestrianSpawns: List<PedestrianSpawnSpec>,
  val playerSpawnX: Float,
  val playerSpawnZ: Float,
  val playerSpawnYaw: Float,
  val gunShopCenter: Pair<Float, Float> = Pair(27.5f, -2.0f),
  val gunShopBuyPos: Pair<Float, Float> = Pair(27.5f, 3.2f),
)
