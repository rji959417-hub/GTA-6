package com.example.game.state

import com.example.game.world.CartoonPresets
import com.example.game.world.CharacterAppearance

enum class TimeOfDayPreset(val label: String, val sunProgress: Float) {
  SUNSET("VICE SUNSET", 0.72f),
  DAYLIGHT("TROPICAL DAY", 0.42f),
  NEON_NIGHT("NEON NIGHT", 0.05f),
}

enum class CameraDistanceMode(val label: String, val footDist: Float, val carDist: Float) {
  STANDARD("STD CAM", 2.75f, 6.8f),
  CLOSE("CLOSE CAM", 2.15f, 5.2f),
  WIDE("WIDE CAM", 4.4f, 9.2f),
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

enum class LootCategory {
  CURRENCY,
  MEDICAL,
  WEAPON,
  VEST,
  HELMET,
  GLOO_WALL,
}

enum class LootItemKind(
  val displayName: String,
  val shortLabel: String,
  val category: LootCategory,
  val weapon: WeaponType? = null,
  val armorLevel: Int = 0,
  val maxDurability: Float = 0f,
  val healAmount: Float = 0f,
  val coinValue: Int = 0,
) {
  COIN("GOLD COIN", "$50", LootCategory.CURRENCY, coinValue = 50),
  MEDKIT("MEDKIT", "+65 HP", LootCategory.MEDICAL, healAmount = 65f),
  GLOO_WALL("GLOO WALL", "GLOO", LootCategory.GLOO_WALL),
  PISTOL_9MM("9MM PISTOL", "PISTOL", LootCategory.WEAPON, weapon = WeaponType.PISTOL_9MM),
  SMG_MICRO("MICRO SMG", "SMG", LootCategory.WEAPON, weapon = WeaponType.SMG_MICRO),
  SHOTGUN("COMBAT SHOTGUN", "SHOTGUN", LootCategory.WEAPON, weapon = WeaponType.SHOTGUN),
  SNIPER_RIFLE("LONGSHOT .50", "SNIPER", LootCategory.WEAPON, weapon = WeaponType.SNIPER_RIFLE),
  ASSAULT_RIFLE("V-47 RIFLE", "RIFLE", LootCategory.WEAPON, weapon = WeaponType.ASSAULT_RIFLE),
  VEST_LV3("LV.3 KEVLAR VEST", "VEST LV3", LootCategory.VEST, armorLevel = 3, maxDurability = 100f),
  VEST_LV4("LV.4 TITAN VEST", "VEST LV4", LootCategory.VEST, armorLevel = 4, maxDurability = 150f),
  HELMET_LV3("LV.3 SPEC HELMET", "HELM LV3", LootCategory.HELMET, armorLevel = 3, maxDurability = 85f),
  HELMET_LV4("LV.4 GOLD HELMET", "HELM LV4", LootCategory.HELMET, armorLevel = 4, maxDurability = 125f),
}

enum class BotTacticalMode {
  LOOTING,
  ROAMING,
  TAKING_COVER,
  HEALING,
  FIGHTING,
  DRIVING_CAR,
}

data class GlooWallState(
  val id: String,
  val ownerId: String,
  var x: Float,
  var y: Float,
  var z: Float,
  val yawRad: Float,
  var hp: Float = 240f,
  val maxHp: Float = 240f,
  var ageSec: Float = 0f,
  val durationSec: Float = 18f,
  val isPlayerOwned: Boolean = false,
)

data class WorldLootItem(
  val uid: String,
  val kind: LootItemKind,
  var x: Float,
  var y: Float,
  var z: Float,
  val houseId: String? = null,
  var pickupCooldownSec: Float = 0f,
)

data class BagItem(
  val uid: String,
  val kind: LootItemKind,
  val durability: Float = kind.maxDurability,
)

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
  val fromEnemy: Boolean = false,
  val fromPlayer: Boolean = !fromEnemy,
  val attackerId: String? = null,
  val attackerName: String? = null,
  val attackerWeapon: WeaponType = WeaponType.FISTS,
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
  var y: Float = 0f,
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
  var driverBotId: String? = null,
  var isPoliceCar: Boolean = false,
  var isPoliceDispatched: Boolean = false,
  var officerDisembarked: Boolean = false,
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
  val isRivalFighter: Boolean = false,
  var weapon: WeaponType = WeaponType.FISTS,
  var vestLevel: Int = 0,
  var vestDurability: Float = 0f,
  var helmetLevel: Int = 0,
  var helmetDurability: Float = 0f,
  val isBrBot: Boolean = true,
  var botMode: BotTacticalMode = BotTacticalMode.ROAMING,
  var botMedkits: Int = if (isRivalFighter) 2 else 0,
  var botGlooWalls: Int = if (isRivalFighter) 2 else 1,
  var botBagCount: Int = if (isRivalFighter) 3 else 0,
  var healTimerSec: Float = 0f,
  var glooWallCooldownSec: Float = 0f,
  var coverX: Float = spawnX,
  var coverZ: Float = spawnZ,
  var coverTimerSec: Float = 0f,
  var strafeDirSign: Float = 1f,
  var strafeFlipTimerSec: Float = 0f,
  var targetEnemyBotId: String? = null,
  var targetPlayer: Boolean = false,
  var targetLootUid: String? = null,
  var drivingVehicleId: String? = null,
  var driveTimerSec: Float = 0f,
  var vehicleCooldownSec: Float = 0f,
  var roamTargetX: Float = spawnX,
  var roamTargetZ: Float = spawnZ,
  var roamRefreshSec: Float = 0f,
  var gracePeriodSec: Float = 0f,
  val isPoliceOfficer: Boolean = false,
  var assignedPoliceCarId: String? = null,
  var policeHasGunDrawn: Boolean = false,
  var armReachNorm: Float = 0f,
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

data class PoliceCaptureSequenceState(
  val officerId: String,
  var elapsedSec: Float = 0f,
  val durationSec: Float = 1.25f,
  var shotFired: Boolean = false,
)

enum class LobbyDropZone(
  val title: String,
  val subtitle: String,
  val spawnPlaneX: Float,
  val spawnPlaneZ: Float,
) {
  FULL_WORLD("NEXTERA DROP", "Full Island", -210f, -18f),
  KEY_BAYSIDE("FARMTOPIA DROP", "Farmtopia", -148f, 6f),
  GREEN_VALLEY("INTELLECT DROP", "Intellect Ctr", -130f, -150f),
  ROOFTOP_TOWN("RUST TOWN DROP", "Rust & Mortar", 40f, -20f),
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
  val isProne: Boolean = false,
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
  val isAutoSprinting: Boolean = false,
  val isDrifting: Boolean = false,
  val activeVehicleName: String? = null,
  val nearbyVehicleName: String? = null,
  val nearbyVehicleOccupied: Boolean = false,
  val nearbyBoatName: String? = null,
  val nearbyBoatOccupied: Boolean = false,
  val insideHouseName: String? = null,
  val zoneName: String = "FARMTOPIA",
  val timePreset: TimeOfDayPreset = TimeOfDayPreset.SUNSET,
  val cameraMode: CameraDistanceMode = CameraDistanceMode.STANDARD,
  val isMuted: Boolean = false,
  val vehiclesMapPos: List<Triple<String, Float, Float>> = emptyList(),
  val boatsMapPos: List<Triple<String, Float, Float>> = emptyList(),
  val pedestriansMapPos: List<Triple<Float, Float, Boolean>> = emptyList(),
  val friendsMapPos: List<Pair<Float, Float>> = emptyList(),
  val gunShopMapPos: Pair<Float, Float> = Pair(-46f, -14f),
  val isBagOpen: Boolean = false,
  val bagItems: List<BagItem> = emptyList(),
  val bagCapacity: Int = 8,
  val isBagFull: Boolean = false,
  val medkitCount: Int = 0,
  val vestLevel: Int = 0,
  val vestDurability: Int = 0,
  val vestMaxDurability: Int = 0,
  val helmetLevel: Int = 0,
  val helmetDurability: Int = 0,
  val helmetMaxDurability: Int = 0,
  val nearbyLootLabel: String? = null,
  val lootMapPos: List<Triple<Float, Float, LootCategory>> = emptyList(),
  val rivalsMapPos: List<Pair<Float, Float>> = emptyList(),
  val isPlayerEliminated: Boolean = false,
  val isSpectatingKiller: Boolean = false,
  val eliminatedByName: String? = null,
  val eliminatedByWeapon: WeaponType = WeaponType.FISTS,
  val spectatingTargetName: String? = null,
  val spectatingTargetHp: Int = 0,
  val spectatingTargetWeapon: WeaponType = WeaponType.FISTS,
  val lastMatchCoinsEarned: Int = 0,
  val isStyleShopOpen: Boolean = false,
  val equippedCloth: ClothStyle = ClothStyle.PRESET_DEFAULT,
  val ownedCloths: Set<ClothStyle> = setOf(ClothStyle.PRESET_DEFAULT),
  val equippedHair: HairCustomization = HairCustomization.PRESET_DEFAULT,
  val ownedHairs: Set<HairCustomization> = setOf(HairCustomization.PRESET_DEFAULT),
  val equippedPants: PantsStyle = PantsStyle.PRESET_DEFAULT,
  val ownedPants: Set<PantsStyle> = setOf(PantsStyle.PRESET_DEFAULT),
  val equippedGunSkin: GunSkin = GunSkin.STANDARD_STEEL,
  val ownedGunSkins: Set<GunSkin> = setOf(GunSkin.STANDARD_STEEL),
  val equippedCarSkin: CarSkin = CarSkin.FACTORY_DEFAULT,
  val ownedCarSkins: Set<CarSkin> = setOf(CarSkin.FACTORY_DEFAULT),
  val weatherMode: WeatherMode = WeatherMode.CLEAR_BREEZE,
  val autoWeatherCycle: Boolean = false,
  val rainIntensity: Float = 0f,
  val lightningFlash: Float = 0f,
  val nearbyMarketStallLabel: String? = null,
  val marketBotSummary: String? = null,
  val trainMapPos: Pair<Float, Float>? = null,
  val helicoptersMapPos: List<Pair<Float, Float>> = emptyList(),
  val marketMapPos: Pair<Float, Float> = Pair(-50f, 24f),
  val waterfallMapPos: Pair<Float, Float> = Pair(-48f, -104f),
  val atollMapPos: Pair<Float, Float> = Pair(-218f, 6f),
  val glooWallCount: Int = 0,
  val glooWallsMapPos: List<Pair<Float, Float>> = emptyList(),
  val aliveBotsCount: Int = 0,
  val policeStationMapPos: Pair<Float, Float> = Pair(-12f, 44f),
  val policeChaseActive: Boolean = false,
  val policeNearestDistMeters: Int = 0,
  val policeCarsMapPos: List<Pair<Float, Float>> = emptyList(),
  val policeOfficersMapPos: List<Pair<Float, Float>> = emptyList(),
  val isBeingArrestedByPolice: Boolean = false,
)

class InputState {
  @Volatile var moveX: Float = 0f
  @Volatile var moveY: Float = 0f
  @Volatile var sprintHeld: Boolean = false
  @Volatile var autoSprint: Boolean = false
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
