package com.example.game.state

import com.example.game.world.CartoonPresets
import com.example.game.world.CharacterAppearance

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

enum class HitZone {
  NONE,
  HEAD,
  TORSO,
  LEGS,
}

enum class DropPhase {
  GROUND,
  AIRPLANE,
  FREEFALL,
  PARACHUTE,
}

enum class ParkourActionType {
  VAULT_OVER,
  CLIMB_LEDGE,
}

enum class LandingReactionType {
  SOFT_LAND,
  COMBAT_ROLL,
  HARD_STAGGER,
}

data class ParkourSequenceState(
  val type: ParkourActionType,
  val label: String,
  val startX: Float,
  val startY: Float,
  val startZ: Float,
  val midX: Float,
  val midY: Float,
  val midZ: Float,
  val targetX: Float,
  val targetY: Float,
  val targetZ: Float,
  val targetYawRad: Float,
  val dropOffAfterVault: Boolean = false,
  val launchVelX: Float = 0f,
  val launchVelZ: Float = 0f,
  var elapsedSec: Float = 0f,
  val durationSec: Float = 0.56f,
)

enum class WeaponType(
  val displayName: String,
  val shortTag: String,
  val price: Int,
  val damage: Float,
  val range: Float,
  val bulletSpeed: Float,
  val fireIntervalSec: Float,
  val pelletCount: Int,
  val spreadRad: Float,
  val twoHanded: Boolean,
  val scopeFovDeg: Float,
) {
  FISTS("FISTS", "MELEE", 0, 30f, 2.35f, 0f, 0.34f, 0, 0f, false, 60f),
  PISTOL_9MM("9MM PISTOL", "PISTOL", 150, 46f, 38f, 72f, 0.28f, 1, 0.024f, false, 40f),
  SMG_MICRO("MICRO SMG", "SMG", 450, 26f, 34f, 68f, 0.10f, 1, 0.070f, false, 42f),
  SHOTGUN("COMBAT SHOTGUN", "SHOTGUN", 800, 28f, 24f, 62f, 0.65f, 5, 0.16f, true, 44f),
  SNIPER_RIFLE("LONGSHOT .50", "SNIPER", 950, 95f, 105f, 118f, 0.78f, 1, 0.004f, true, 20f),
  ASSAULT_RIFLE("V-47 RIFLE", "RIFLE", 1400, 56f, 58f, 88f, 0.14f, 1, 0.032f, true, 30f),
}

enum class CombatMoveType(
  val durationSec: Float,
  val damage: Float,
  val knockback: Float,
  val isKick: Boolean,
  val isHeavy: Boolean,
  val targetHitZone: HitZone,
) {
  PUNCH_JAB(0.32f, 28f, 2.8f, isKick = false, isHeavy = false, targetHitZone = HitZone.TORSO),
  PUNCH_HOOK(0.42f, 44f, 5.2f, isKick = false, isHeavy = true, targetHitZone = HitZone.HEAD),
  PUNCH_UPPERCUT(0.44f, 54f, 5.8f, isKick = false, isHeavy = true, targetHitZone = HitZone.HEAD),
  KICK_FRONT(0.40f, 38f, 5.0f, isKick = true, isHeavy = false, targetHitZone = HitZone.TORSO),
  KICK_ROUNDHOUSE(0.48f, 58f, 7.6f, isKick = true, isHeavy = true, targetHitZone = HitZone.HEAD),
}

enum class NpcReactionState {
  STROLLING,
  HANDS_UP_FLINCH,
  FLEEING,
  FIGHTING_BACK,
  COMPANION,
  KNOCKED_DOWN,
}

data class BulletProjectileState(
  var x: Float,
  var y: Float,
  var z: Float,
  var prevX: Float,
  var prevY: Float,
  var prevZ: Float,
  val vx: Float,
  val vy: Float,
  val vz: Float,
  val damage: Float,
  val knockback: Float,
  var remainingDist: Float,
  var ageSec: Float = 0f,
)

data class HitSparkState(
  val x: Float,
  val y: Float,
  val z: Float,
  val color: FloatArray,
  var ageSec: Float = 0f,
  val durationSec: Float = 0.24f,
)

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
  val usesUptownLoop: Boolean = false,
  val usesTownLoop: Boolean = false,
  val driverShirtColor: FloatArray = floatArrayOf(0.95f, 0.45f, 0.35f),
  val appearance: CharacterAppearance = CartoonPresets.CHUBBY_EMERALD,
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
  val routeCenterX: Float = 0f,
  val captainShirtColor: FloatArray = floatArrayOf(0.96f, 0.85f, 0.25f),
  val appearance: CharacterAppearance = CartoonPresets.COASTAL_CAPTAIN,
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
  var y: Float = 0f,
  val appearance: CharacterAppearance = CartoonPresets.LEAN_BUDDY_BLUE,
  val isBraveDefender: Boolean = false,
  val spawnX: Float = x,
  val spawnZ: Float = z,
  var yawRad: Float = 0f,
  var targetIdx: Int = 1,
  var forwardDir: Int = 1,
  var walkCycle: Float = 0f,
  var hp: Float = 100f,
  val maxHp: Float = 100f,
  var reactionState: NpcReactionState = NpcReactionState.STROLLING,
  var panicTimerSec: Float = 0f,
  var downedTimerSec: Float = 0f,
  var respawnTimerSec: Float = 0f,
  var fallProgress: Float = 0f,
  var vx: Float = 0f,
  var vz: Float = 0f,
  var armsUpNorm: Float = 0f,
  var attackMove: CombatMoveType? = null,
  var attackProgress: Float = 0f,
  var attackCooldownSec: Float = 0f,
  var hitReactZone: HitZone = HitZone.NONE,
  var hitReactTimerSec: Float = 0f,
  var hitReactDirSign: Float = 1f,
  var isFriend: Boolean = false,
  var friendSlot: Int = 0,
  var sitProgress: Float = 0f,
  var isHostileTarget: Boolean = false,
)

data class EjectedNpcState(
  val id: String,
  var x: Float,
  var y: Float,
  var z: Float,
  var yawRad: Float,
  val shirtColor: FloatArray,
  val pantsColor: FloatArray,
  val appearance: CharacterAppearance = CartoonPresets.CHUBBY_EMERALD,
  var vx: Float,
  var vy: Float,
  var vz: Float,
  var fallProgress: Float = 0f,
  var ageSec: Float = 0f,
  val inWater: Boolean = false,
  var walkCycle: Float = 0f,
  var hp: Float = 100f,
  var isFleeing: Boolean = false,
  var isDead: Boolean = false,
  var downedAgeSec: Float = 0f,
  var hitReactZone: HitZone = HitZone.NONE,
  var hitReactTimerSec: Float = 0f,
  var hitReactDirSign: Float = 1f,
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

enum class LobbyDropZone(
  val title: String,
  val subtitle: String,
  val spawnPlaneX: Float,
  val spawnPlaneZ: Float,
) {
  FULL_WORLD("BR BATTLE DROP", "All 3 Islands", -196f, -18f),
  KEY_BAYSIDE("BAYSIDE CLASH", "Key Bayside", -110f, -14f),
  GREEN_VALLEY("ENCLAVE ROYALE", "Green Valley", 42f, -14f),
  ROOFTOP_TOWN("PARKOUR DROP", "Rooftop Town", -240f, -14f),
}

data class LobbySquadMember(
  val id: String,
  val name: String,
  val badge: String,
  val heroIndex: Int,
  val weapon: WeaponType = WeaponType.ASSAULT_RIFLE,
  val isReady: Boolean = true,
  val isRemoteP2P: Boolean = false,
)

data class RemotePlayerPose(
  val peerId: String,
  val name: String,
  val x: Float,
  val y: Float,
  val z: Float,
  val yawRad: Float,
  val speedNorm: Float = 0f,
  val isSprinting: Boolean = false,
  val isSitting: Boolean = false,
  val dropPhase: DropPhase = DropPhase.GROUND,
  val weapon: WeaponType = WeaponType.FISTS,
  val heroIndex: Int = 2,
  val walkCycle: Float = 0f,
)

data class HudSnapshot(
  val isPlaying: Boolean = false,
  val isPaused: Boolean = false,
  val isGunShopOpen: Boolean = false,
  val heroPresetIndex: Int = 0,
  val heroPresetName: String = "VICE HERO",
  val lobbyDropZone: LobbyDropZone = LobbyDropZone.FULL_WORLD,
  val lobbySquadMembers: List<LobbySquadMember> = emptyList(),
  val isDriving: Boolean = false,
  val isDrivingBoat: Boolean = false,
  val isSwimming: Boolean = false,
  val isSitting: Boolean = false,
  val isScoped: Boolean = false,
  val isCarjacking: Boolean = false,
  val carjackTargetName: String? = null,
  val dropPhase: DropPhase = DropPhase.GROUND,
  val planeCountdownSec: Int = 0,
  val altitudeMeters: Int = 0,
  val planeX: Float = 0f,
  val planeZ: Float = 0f,
  val planeYawRad: Float = 0f,
  val playerHealth: Int = 100,
  val playerCash: Int = 1200,
  val wantedLevel: Int = 0,
  val equippedWeapon: WeaponType = WeaponType.FISTS,
  val ownedWeapons: Set<WeaponType> = setOf(WeaponType.FISTS),
  val activeAttackMove: CombatMoveType? = null,
  val activeParkourType: ParkourActionType? = null,
  val nearbyParkourLabel: String? = null,
  val activeLandingReaction: LandingReactionType? = null,
  val lastHitBanner: String? = null,
  val nearbyGunShop: Boolean = false,
  val nearbyFriendCandidateId: String? = null,
  val friendCount: Int = 0,
  val panickingNpcCount: Int = 0,
  val npcDefeatedCount: Int = 0,
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
  val pedestriansMapPos: List<Triple<Float, Float, Boolean>> = emptyList(),
  val friendsMapPos: List<Pair<Float, Float>> = emptyList(),
  val gunShopMapPos: Pair<Float, Float> = Pair(27.5f, -2.0f),
)

class InputState {
  @Volatile var moveX: Float = 0f
  @Volatile var moveY: Float = 0f
  @Volatile var sprintHeld: Boolean = false
  @Volatile var jumpQueued: Boolean = false
  @Volatile var climbQueued: Boolean = false
  @Volatile var punchQueued: Boolean = false
  @Volatile var kickQueued: Boolean = false
  @Volatile var fireHeld: Boolean = false
  @Volatile var fireQueued: Boolean = false

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
