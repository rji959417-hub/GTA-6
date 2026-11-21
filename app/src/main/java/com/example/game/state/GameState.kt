package com.example.game.state

enum class TimeOfDayPreset(val label: String, val sunProgress: Float) {
  SUNSET("VICE SUNSET", 0.72f),
  DAYLIGHT("TROPICAL DAY", 0.42f),
  NEON_NIGHT("NEON NIGHT", 0.05f),
}

enum class CameraDistanceMode(val label: String, val footDist: Float, val carDist: Float) {
  STANDARD("STD CAM", 5.8f, 7.6f),
  CLOSE("CLOSE CAM", 3.9f, 5.6f),
  WIDE("WIDE CAM", 8.2f, 10.2f),
}

data class VehicleRuntimeState(
  val id: String,
  val displayName: String,
  var x: Float,
  var z: Float,
  var yawRad: Float,
  val bodyColor: FloatArray,
  val trimColor: FloatArray,
  val maxSpeed: Float,
  val accel: Float,
  var isAiTraffic: Boolean = false,
  var waypointIndex: Int = 0,
  val usesInnerLoop: Boolean = false,
  val driverShirtColor: FloatArray = floatArrayOf(0.95f, 0.45f, 0.35f),
  var forwardSpeed: Float = 0f,
  var lateralSpeed: Float = 0f,
  var steerAngle: Float = 0f,
  var wheelSpinRad: Float = 0f,
  var pitchRad: Float = 0f,
  var rollRad: Float = 0f,
  var isBraking: Boolean = false,
  var isDrifting: Boolean = false,
)

data class BoatRuntimeState(
  val id: String,
  val displayName: String,
  var x: Float,
  var z: Float,
  var yawRad: Float,
  val hullColor: FloatArray,
  val accentColor: FloatArray,
  val maxSpeed: Float,
  var isAiCruise: Boolean = false,
  val routeRadiusX: Float = 96f,
  val routeRadiusZ: Float = 96f,
  var routeAngle: Float = 0f,
  val captainShirtColor: FloatArray = floatArrayOf(0.96f, 0.85f, 0.25f),
  var forwardSpeed: Float = 0f,
  var lateralSpeed: Float = 0f,
  var steerAngle: Float = 0f,
  var pitchRad: Float = 0f,
  var rollRad: Float = 0f,
  var bobY: Float = 0f,
  var wakeIntensity: Float = 0f,
)

data class PedestrianRuntimeState(
  val id: String,
  val waypoints: List<Pair<Float, Float>>,
  val shirtColor: FloatArray,
  val pantsColor: FloatArray,
  val speed: Float,
  var x: Float,
  var z: Float,
  var yawRad: Float = 0f,
  var targetIdx: Int = 1,
  var forwardDir: Int = 1,
  var walkCycle: Float = 0f,
)

data class EjectedNpcState(
  val id: String,
  var x: Float,
  var y: Float,
  var z: Float,
  var yawRad: Float,
  val shirtColor: FloatArray,
  val pantsColor: FloatArray,
  var vx: Float,
  var vy: Float,
  var vz: Float,
  var fallProgress: Float = 0f, // 0 = upright, 1 = knocked down flat on ground
  var ageSec: Float = 0f,
  val inWater: Boolean = false,
  var walkCycle: Float = 0f,
)

data class CarjackSequenceState(
  val vehicleId: String? = null,
  val boatId: String? = null,
  val targetName: String,
  val npcId: String,
  val startX: Float,
  val startZ: Float,
  var elapsedSec: Float = 0f,
  val durationSec: Float = 0.95f,
)

data class HudSnapshot(
  val isPlaying: Boolean = false,
  val isPaused: Boolean = false,
  val isDriving: Boolean = false,
  val isDrivingBoat: Boolean = false,
  val isSwimming: Boolean = false,
  val isCarjacking: Boolean = false,
  val carjackTargetName: String? = null,
  val playerX: Float = 0f,
  val playerZ: Float = 0f,
  val playerYawRad: Float = 0f,
  val cameraYawRad: Float = 0f,
  val speedKmh: Int = 0,
  val isSprinting: Boolean = false,
  val isDrifting: Boolean = false,
  val activeVehicleName: String? = null,
  val nearbyVehicleName: String? = null,
  val nearbyVehicleOccupied: Boolean = false,
  val nearbyBoatName: String? = null,
  val nearbyBoatOccupied: Boolean = false,
  val insideHouseName: String? = null,
  val zoneName: String = "KEY BAYSIDE",
  val timePreset: TimeOfDayPreset = TimeOfDayPreset.SUNSET,
  val cameraMode: CameraDistanceMode = CameraDistanceMode.STANDARD,
  val isMuted: Boolean = false,
  val vehiclesMapPos: List<Triple<String, Float, Float>> = emptyList(),
  val boatsMapPos: List<Triple<String, Float, Float>> = emptyList(),
)

class InputState {
  @Volatile var moveX: Float = 0f
  @Volatile var moveY: Float = 0f
  @Volatile var sprintHeld: Boolean = false
  @Volatile var jumpQueued: Boolean = false

  @Volatile var steerLeft: Boolean = false
  @Volatile var steerRight: Boolean = false
  @Volatile var gasHeld: Boolean = false
  @Volatile var brakeHeld: Boolean = false
  @Volatile var handbrakeHeld: Boolean = false

  private var pendingOrbitYaw: Float = 0f
  private var pendingOrbitPitch: Float = 0f

  @Synchronized
  fun addOrbitDelta(dYaw: Float, dPitch: Float) {
    pendingOrbitYaw += dYaw
    pendingOrbitPitch += dPitch
  }

  @Synchronized
  fun consumeOrbitDelta(): Pair<Float, Float> {
    val out = Pair(pendingOrbitYaw, pendingOrbitPitch)
    pendingOrbitYaw = 0f
    pendingOrbitPitch = 0f
    return out
  }
}
