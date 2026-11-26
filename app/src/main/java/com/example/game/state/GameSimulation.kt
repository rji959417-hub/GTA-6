package com.example.game.state

import com.example.game.audio.CombatSfxEvent
import com.example.game.math.AabbCollider
import com.example.game.math.CircleCollider
import com.example.game.math.CollisionSolver
import com.example.game.math.Vec3
import com.example.game.math.angleBetweenXZ
import com.example.game.math.lerpAngleRad
import com.example.game.math.normalizeAngleRad
import com.example.game.world.HouseSpec
import com.example.game.world.TerrainHeightfield
import com.example.game.world.WorldData
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class GameSimulation(val world: WorldData) {
  val input = InputState()

  @Volatile var isPlaying: Boolean = false
  @Volatile var isPaused: Boolean = false
  @Volatile var isGunShopOpen: Boolean = false
  @Volatile var isStyleShopOpen: Boolean = false
  @Volatile var timePreset: TimeOfDayPreset = TimeOfDayPreset.SUNSET
  @Volatile var cameraMode: CameraDistanceMode = CameraDistanceMode.STANDARD

  var onCombatSfx: ((CombatSfxEvent) -> Unit)? = null
  var onEconomyChanged: (() -> Unit)? = null

  // Player kinematic & survival state
  var playerX: Float = world.playerSpawnX
  var playerY: Float = 0f
  var playerZ: Float = world.playerSpawnZ
  var playerVelY: Float = 0f
  var playerYawRad: Float = world.playerSpawnYaw
  var playerWalkCycle: Float = 0f
  var playerSpeedNorm: Float = 0f
  var playerArmReachNorm: Float = 0f
  var isGrounded: Boolean = true
  var isSwimming: Boolean = false

  // Health, economy, sitting, scope, weapons & street combat state
  var playerHealth: Float = 100f
  var playerCash: Int = 1200
  var wantedLevel: Int = 0
  private var wantedDecaySec: Float = 0f
  private var regenCooldownSec: Float = 0f

  var isSitting: Boolean = false
  var sitProgress: Float = 0f
  var isScoped: Boolean = false

  var heroPresetIndex: Int = 0
  var lobbyDropZone: LobbyDropZone = LobbyDropZone.FULL_WORLD
  var lobbyHeroYawRad: Float = 0.16f
  val lobbySquadMembers = mutableListOf<LobbySquadMember>()
  private val remotePlayerPoses = linkedMapOf<String, RemotePlayerPose>()

  var equippedWeapon: WeaponType = WeaponType.FISTS
  val ownedWeapons = linkedSetOf(WeaponType.FISTS)
  private val purchasedWeapons = linkedSetOf(WeaponType.FISTS)
  var nearbyGunShop: Boolean = false
  var nearbyFriendCandidate: PedestrianRuntimeState? = null

  // Free Fire Loot, Backpack (Max 8 slots) & Level 3 / Level 4 Armor state
  @Volatile var isBagOpen: Boolean = false
  val worldLootItems = mutableListOf<WorldLootItem>()
  val bagItems = mutableListOf<BagItem>()
  val glooWalls = mutableListOf<GlooWallState>()
  var playerReserveGlooWalls: Int = 2
  var playerVestLevel: Int = 0
  var playerVestDurability: Float = 0f
  var playerHelmetLevel: Int = 0
  var playerHelmetDurability: Float = 0f
  var nearbyLootLabel: String? = null
  private var bagFullBannerCooldownSec: Float = 0f
  private var lootUidSeq: Int = 0
  private var glooUidSeq: Int = 0
  private var lootRespawnTimerSec: Float = 0f

  var activeAttackMove: CombatMoveType? = null
  var attackTimerSec: Float = 0f
  private var hasAppliedAttackHit: Boolean = false
  private var punchComboIdx: Int = 0
  private var kickComboIdx: Int = 0
  private var comboWindowSec: Float = 0f

  private var weaponCooldownSec: Float = 0f
  private var weaponAimTimerSec: Float = 0f
  private var muzzleFlashTimerSec: Float = 0f
  var npcDefeatedCount: Int = 0

  // Elimination, Killer Spectate & Match Coin Rewards
  @Volatile var isPlayerEliminated: Boolean = false
  @Volatile var isSpectatingKiller: Boolean = false
  var playerFallProgress: Float = 0f
  private var spectatedKillerPedId: String? = null
  private var eliminatedByName: String? = null
  private var eliminatedByWeapon: WeaponType = WeaponType.FISTS
  private var lastAttackerPedId: String? = null
  private var lastAttackerName: String? = null
  private var lastAttackerWeapon: WeaponType = WeaponType.FISTS
  private var lastAttackerTimerSec: Float = 0f
  private var matchElapsedSec: Float = 0f
  private var matchStartKoCount: Int = 0
  private var lastMatchCoinsEarned: Int = 0
  private var matchCoinsAwarded: Boolean = false

  // Lobby Customization Store (Clothes, Hair Styles, Pants, Gun Paint, Car Skins)
  var equippedCloth: ClothStyle = ClothStyle.PRESET_DEFAULT
  val ownedCloths = linkedSetOf(ClothStyle.PRESET_DEFAULT)
  var equippedHair: HairCustomization = HairCustomization.PRESET_DEFAULT
  val ownedHairs = linkedSetOf(HairCustomization.PRESET_DEFAULT)
  var equippedPants: PantsStyle = PantsStyle.PRESET_DEFAULT
  val ownedPants = linkedSetOf(PantsStyle.PRESET_DEFAULT)
  var equippedGunSkin: GunSkin = GunSkin.STANDARD_STEEL
  val ownedGunSkins = linkedSetOf(GunSkin.STANDARD_STEEL)
  var equippedCarSkin: CarSkin = CarSkin.FACTORY_DEFAULT
  val ownedCarSkins = linkedSetOf(CarSkin.FACTORY_DEFAULT)

  private var lastHitBanner: String? = null
  private var hitBannerTimerSec: Float = 0f

  val bulletProjectiles = mutableListOf<BulletProjectileState>()
  val hitSparks = mutableListOf<HitSparkState>()

  val attackProgress: Float
    get() = activeAttackMove?.let { (attackTimerSec / it.durationSec).coerceIn(0f, 1f) } ?: 0f

  val aimNorm: Float
    get() =
      when {
        equippedWeapon == WeaponType.FISTS || isSitting || isSwimming -> 0f
        isScoped -> 1f
        weaponAimTimerSec > 0f -> (weaponAimTimerSec / 0.22f).coerceIn(0f, 1f)
        else -> 0f
      }

  val muzzleFlashNorm: Float
    get() = (muzzleFlashTimerSec / 0.08f).coerceIn(0f, 1f)

  // PUBG-style Parkour (Ledge Grab / Climb / Vault) & Rooftop Drop Reactions
  var activeParkour: ParkourSequenceState? = null
  var nearbyParkourLabel: String? = null
  private var nearbyParkourCandidate: ParkourSequenceState? = null
  var activeLandingReaction: LandingReactionType? = null
  private var landingReactionTimerSec: Float = 0f
  private var landingReactionDurationSec: Float = 0.01f
  var airborneFallNorm: Float = 0f
  private var airPeakY: Float = 0f
  private var airStartY: Float = 0f
  private var airborneLaunchVx: Float = 0f
  private var airborneLaunchVz: Float = 0f
  private var camLandingDipY: Float = 0f
  private var camImpactShake: Float = 0f

  val parkourProgress: Float
    get() = activeParkour?.let { (it.elapsedSec / it.durationSec).coerceIn(0f, 1f) } ?: 0f

  val landingReactionProgress: Float
    get() =
      if (activeLandingReaction != null && landingReactionDurationSec > 0.001f) {
        (1f - landingReactionTimerSec / landingReactionDurationSec).coerceIn(0f, 1f)
      } else {
        0f
      }

  // PUBG-style Airplane Drop & Parachute State
  var dropPhase: DropPhase = DropPhase.GROUND
  var planeTimerSec: Float = 0f
  var planeX: Float = -52f
  var planeY: Float = 66f
  var planeZ: Float = -20f
  var planeYawRad: Float = 1.42f
  var planeActiveInSky: Boolean = false
  private var horizVelX: Float = 0f
  private var horizVelZ: Float = 0f

  val vehicles: List<VehicleRuntimeState> =
    world.vehicleSpawns.map {
      VehicleRuntimeState(
        id = it.id,
        displayName = it.displayName,
        x = it.x,
        z = it.z,
        y = TerrainHeightfield.elevationAt(it.x, it.z).coerceAtLeast(0f),
        yawRad = it.yawRad,
        bodyColor = it.bodyColor,
        trimColor = it.trimColor,
        maxSpeed = it.maxSpeed,
        accel = it.accel,
        isAiTraffic = it.isAiTraffic,
        waypointIndex = it.loopRouteIndex,
        usesInnerLoop = it.id == "ai_car_3" || it.id == "ai_car_4",
        usesUptownLoop = it.id.contains("uptown"),
        usesTownLoop = it.id.contains("town"),
        driverShirtColor = it.driverShirtColor,
        appearance = it.appearance,
      )
    }

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
        routeCenterX = it.routeCenterX,
        captainShirtColor = it.captainShirtColor,
        appearance = it.appearance,
      )
    }

  val pedestrians: List<PedestrianRuntimeState> =
    world.pedestrianSpawns.map {
      val start = it.waypoints.first()
      val vDur =
        when (it.vestLevel) {
          4 -> LootItemKind.VEST_LV4.maxDurability
          3 -> LootItemKind.VEST_LV3.maxDurability
          else -> 0f
        }
      val hDur =
        when (it.helmetLevel) {
          4 -> LootItemKind.HELMET_LV4.maxDurability
          3 -> LootItemKind.HELMET_LV3.maxDurability
          else -> 0f
        }
      PedestrianRuntimeState(
        id = it.id,
        waypoints = it.waypoints,
        shirtColor = it.shirtColor,
        pantsColor = it.pantsColor,
        speed = it.speed,
        x = start.first,
        z = start.second,
        y = TerrainHeightfield.elevationAt(start.first, start.second).coerceAtLeast(0f),
        appearance = it.appearance,
        isBraveDefender = it.isBraveDefender,
        spawnX = start.first,
        spawnZ = start.second,
        isRivalFighter = it.isRivalFighter,
        weapon = it.rivalWeapon,
        vestLevel = it.vestLevel,
        vestDurability = vDur,
        helmetLevel = it.helmetLevel,
        helmetDurability = hDur,
      )
    }

  init {
    populateHouseLoot()
  }

  val ejectedNpcs = mutableListOf<EjectedNpcState>()
  var activeCarjack: CarjackSequenceState? = null

  var activeVehicle: VehicleRuntimeState? = null
  var activeBoat: BoatRuntimeState? = null
  var nearbyVehicle: VehicleRuntimeState? = null
  var nearbyBoat: BoatRuntimeState? = null
  var currentInsideHouse: HouseSpec? = null

  var camYawRad: Float = world.playerSpawnYaw + PI.toFloat()
  var camPitchRad: Float = 0.28f
  var camPos: Vec3 = Vec3(playerX, 3.2f, playerZ + 6f)
  var camLookAt: Vec3 = Vec3(playerX, 1.4f, playerZ)
  var camFovDeg: Float = 62f

  val livingWorld = LivingWorldController(world)
  private val botController = BattleRoyaleBotController(this)
  var nearbyMarketStallLabel: String? = null

  var worldTimeSec: Float = 0f
  private var userLookCooldown: Float = 0f
  private var hijackCounter: Int = 0

  private val dynamicVehicleColliders = ArrayList<CircleCollider>(20)

  @Synchronized
  fun step(rawDt: Float) {
    val dt = rawDt.coerceIn(0.001f, 0.05f)
    worldTimeSec += dt

    if (!isPlaying) {
      stepAiRoadTraffic(dt)
      stepAiOceanBoats(dt)
      stepPedestrians(dt)
      livingWorld.step(dt, playerX, playerZ, false, 0f, boats, camLookAt.x, camLookAt.z)

      val lobbyStageX = -2.2f
      val lobbyStageZ = 8.4f
      val lobbyGroundY = computeGroundHeight(lobbyStageX, lobbyStageZ, 0f)
      playerX = lobbyStageX
      playerZ = lobbyStageZ
      playerY = lobbyGroundY
      playerYawRad = lobbyHeroYawRad
      playerSpeedNorm = 0f
      isSwimming = false
      val targetSit = if (isSitting) 1f else 0f
      sitProgress += (targetSit - sitProgress) * min(1f, dt * 8.5f)
      if (hitBannerTimerSec > 0f) {
        hitBannerTimerSec = max(0f, hitBannerTimerSec - dt)
        if (hitBannerTimerSec <= 0f) lastHitBanner = null
      }

      if (!isGunShopOpen && !isStyleShopOpen) {
        val (dYaw, _) = input.consumeOrbitDelta()
        if (abs(dYaw) > 0.0005f) {
          lobbyHeroYawRad = normalizeAngleRad(lobbyHeroYawRad + dYaw * 0.009f)
          playerYawRad = lobbyHeroYawRad
        }
      } else {
        input.consumeOrbitDelta()
      }

      val camDist = 3.95f
      val camX = lobbyStageX + 0.12f
      val camY = lobbyGroundY + 1.48f
      val camZ = lobbyStageZ + camDist
      camPos = Vec3(camX, camY, camZ)
      camLookAt = Vec3(lobbyStageX + 0.04f, lobbyGroundY + 1.04f, lobbyStageZ - 2.2f)
      camFovDeg = 46f
      return
    }

    if (isPaused || isGunShopOpen || isBagOpen || isStyleShopOpen) return

    if (isPlayerEliminated) {
      // Lock ALL movement, running, shooting, and vehicle controls while eliminated or spectating!
      input.moveX = 0f
      input.moveY = 0f
      input.sprintHeld = false
      input.autoSprint = false
      input.jumpQueued = false
      input.climbQueued = false
      input.punchQueued = false
      input.kickQueued = false
      input.fireHeld = false
      input.fireQueued = false
      input.steerLeft = false
      input.steerRight = false
      input.gasHeld = false
      input.brakeHeld = false
      input.handbrakeHeld = false
      playerSpeedNorm = 0f
      isScoped = false
      nearbyLootLabel = null
      playerFallProgress = min(1f, playerFallProgress + dt * 4.5f)

      val (dYaw, dPitch) = input.consumeOrbitDelta()
      if (isSpectatingKiller && (abs(dYaw) > 0.0005f || abs(dPitch) > 0.0005f)) {
        camYawRad = normalizeAngleRad(camYawRad - dYaw * 0.0065f)
        camPitchRad = (camPitchRad + dPitch * 0.0052f).coerceIn(-0.08f, 1.08f)
      }

      if (planeActiveInSky) {
        val planeSpeed = 36.5f
        planeX += sin(planeYawRad) * planeSpeed * dt
        planeZ += cos(planeYawRad) * planeSpeed * dt
      }

      stepCombatEffects(dt)
      stepGlooWalls(dt)
      stepBullets(dt)
      stepAiRoadTraffic(dt)
      stepAiOceanBoats(dt)
      stepPedestrians(dt)
      stepEjectedNpcs(dt)
      livingWorld.step(dt, playerX, playerZ, false, 0f, boats, camLookAt.x, camLookAt.z)
      ensureLiveSpectatedTarget()
      updateCamera(dt)
      return
    }

    matchElapsedSec += dt
    if (lastAttackerTimerSec > 0f) {
      lastAttackerTimerSec = max(0f, lastAttackerTimerSec - dt)
      if (lastAttackerTimerSec <= 0f) {
        lastAttackerPedId = null
      }
    }

    val (dYaw, dPitch) = input.consumeOrbitDelta()
    if (abs(dYaw) > 0.0005f || abs(dPitch) > 0.0005f) {
      val yawSensitivity = if (isScoped) 0.0028f else 0.0065f
      val pitchSensitivity = if (isScoped) 0.0024f else 0.0052f
      camYawRad = normalizeAngleRad(camYawRad - dYaw * yawSensitivity)
      val maxPitch = if (isScoped) 0.36f else 1.08f
      val minPitch = if (isScoped) -0.32f else -0.08f
      camPitchRad = (camPitchRad + dPitch * pitchSensitivity).coerceIn(minPitch, maxPitch)
      userLookCooldown = 1.6f
      if (isScoped && activeVehicle == null && activeBoat == null) {
        playerYawRad = normalizeAngleRad(camYawRad + PI.toFloat())
      }
    } else if (userLookCooldown > 0f) {
      userLookCooldown = max(0f, userLookCooldown - dt)
    }

    if (wantedLevel > 0) {
      wantedDecaySec -= dt
      if (wantedDecaySec <= 0f) {
        wantedLevel = max(0, wantedLevel - 1)
        wantedDecaySec = if (wantedLevel > 0) 14f else 0f
      }
    }
    if (regenCooldownSec > 0f) {
      regenCooldownSec = max(0f, regenCooldownSec - dt)
    } else if (playerHealth < 100f) {
      val rate = if (isSitting) 5.0f else 2.0f
      playerHealth = min(100f, playerHealth + rate * dt)
    }

    if (planeActiveInSky) {
      val planeSpeed = 36.5f
      planeX += sin(planeYawRad) * planeSpeed * dt
      planeZ += cos(planeYawRad) * planeSpeed * dt
      if (dropPhase != DropPhase.AIRPLANE && planeX > 440f) {
        planeActiveInSky = false
      }
    }

    stepCombatEffects(dt)
    stepGlooWalls(dt)
    stepLootRespawn(dt)
    stepBullets(dt)
    stepAiRoadTraffic(dt)
    stepAiOceanBoats(dt)
    stepPedestrians(dt)
    stepEjectedNpcs(dt)
    livingWorld.step(dt, playerX, playerZ, isSwimming, playerSpeedNorm, boats, camLookAt.x, camLookAt.z)

    val carjack = activeCarjack
    val boat = activeBoat
    val car = activeVehicle
    val parkour = activeParkour
    when {
      dropPhase == DropPhase.AIRPLANE -> stepAirplaneRide(dt)
      dropPhase == DropPhase.FREEFALL -> stepFreefall(dt)
      dropPhase == DropPhase.PARACHUTE -> stepParachuteGlide(dt)
      carjack != null -> stepCarjackSequence(carjack, dt)
      boat != null -> stepDrivingBoat(boat, dt)
      car != null -> stepDrivingCar(car, dt)
      parkour != null -> stepParkourSequence(parkour, dt)
      else -> stepOnFoot(dt)
    }

    if (dropPhase == DropPhase.GROUND && activeVehicle == null && activeBoat == null && activeCarjack == null) {
      stepWorldLootAutoPickup(dt)
    } else {
      nearbyLootLabel = null
    }

    if (playerHealth <= 0f) {
      triggerPlayerElimination()
    }

    updateHouseDetection()
    updateGunShopProximity()
    updateMarketProximity()
    updateFriendCandidate()
    updateCamera(dt)
  }

  @Synchronized
  fun startAirplaneDrop(zoneOverride: LobbyDropZone? = null) {
    if (zoneOverride != null) lobbyDropZone = zoneOverride
    isPlaying = true
    isPaused = false
    isGunShopOpen = false
    isStyleShopOpen = false
    isBagOpen = false
    isPlayerEliminated = false
    isSpectatingKiller = false
    playerFallProgress = 0f
    spectatedKillerPedId = null
    eliminatedByName = null
    eliminatedByWeapon = WeaponType.FISTS
    lastAttackerPedId = null
    lastAttackerName = null
    lastAttackerWeapon = WeaponType.FISTS
    lastAttackerTimerSec = 0f
    matchElapsedSec = 0f
    matchStartKoCount = npcDefeatedCount
    lastMatchCoinsEarned = 0
    matchCoinsAwarded = false
    activeVehicle = null
    activeBoat = null
    activeCarjack = null
    isSitting = false
    isScoped = false
    isSwimming = false
    sitProgress = 0f
    activeAttackMove = null
    attackTimerSec = 0f
    activeParkour = null
    nearbyParkourCandidate = null
    nearbyParkourLabel = null
    activeLandingReaction = null
    landingReactionTimerSec = 0f
    airborneFallNorm = 0f
    airborneLaunchVx = 0f
    airborneLaunchVz = 0f
    camLandingDipY = 0f
    camImpactShake = 0f
    playerHealth = 100f
    playerVestLevel = 0
    playerVestDurability = 0f
    playerHelmetLevel = 0
    playerHelmetDurability = 0f
    bagItems.clear()
    glooWalls.clear()
    playerReserveGlooWalls = 2
    vehicles.forEach { it.driverBotId = null }
    nearbyLootLabel = null
    recomputeOwnedWeaponsFromPurchasedAndBag()
    populateHouseLoot()
    resetRivalFightersForMatch()
    dropPhase = DropPhase.AIRPLANE
    planeTimerSec = 10.0f
    planeX = lobbyDropZone.spawnPlaneX
    planeY = 68f
    planeZ = lobbyDropZone.spawnPlaneZ
    planeYawRad = 1.48f
    planeActiveInSky = true
    activateLobbyAiSquadmatesInWorld()
    playerX = planeX
    playerY = planeY
    playerZ = planeZ
    playerVelY = 0f
    horizVelX = 0f
    horizVelZ = 0f
    playerYawRad = planeYawRad
    isGrounded = false
    airPeakY = playerY
    airStartY = playerY
    camYawRad = normalizeAngleRad(planeYawRad + PI.toFloat())
    camPitchRad = 0.44f
    showBanner("AIRPLANE DROP · TAP JUMP & LOOT HOUSES!")
  }

  @Synchronized
  fun jumpFromAirplane(): Boolean {
    if (!isPlaying || dropPhase != DropPhase.AIRPLANE) return false
    dropPhase = DropPhase.FREEFALL
    playerX = planeX
    playerY = planeY - 2.4f
    playerZ = planeZ
    playerVelY = -4.5f
    horizVelX = sin(planeYawRad) * 9.5f
    horizVelZ = cos(planeYawRad) * 9.5f
    playerYawRad = planeYawRad
    isGrounded = false
    camPitchRad = 0.42f
    onCombatSfx?.invoke(CombatSfxEvent.KICK)
    showBanner("FREEFALL · DEPLOY PARACHUTE!")
    return true
  }

  @Synchronized
  fun deployParachute(): Boolean {
    if (!isPlaying || dropPhase != DropPhase.FREEFALL) return false
    dropPhase = DropPhase.PARACHUTE
    playerVelY = max(-8.0f, playerVelY.coerceAtMost(-5.0f))
    onCombatSfx?.invoke(CombatSfxEvent.FRIEND)
    showBanner("PARACHUTE DEPLOYED · STEER WITH JOYSTICK")
    return true
  }

  private fun stepAirplaneRide(dt: Float) {
    nearbyVehicle = null
    nearbyBoat = null
    nearbyGunShop = false
    nearbyFriendCandidate = null
    isSwimming = false
    isSitting = false
    isScoped = false
    playerX = planeX
    playerY = planeY
    playerZ = planeZ
    playerYawRad = planeYawRad
    planeTimerSec -= dt
    if (planeTimerSec <= 0f) {
      planeTimerSec = 0f
      jumpFromAirplane()
    }
  }

  private fun stepFreefall(dt: Float) {
    val mx = input.moveX.coerceIn(-1f, 1f)
    val my = input.moveY.coerceIn(-1f, 1f)
    val mag = hypot(mx, my).coerceAtMost(1f)
    if (mag > 0.08f) {
      val fwdX = -sin(camYawRad)
      val fwdZ = -cos(camYawRad)
      val rightX = cos(camYawRad)
      val rightZ = -sin(camYawRad)
      val wishX = rightX * mx + fwdX * (-my)
      val wishZ = rightZ * mx + fwdZ * (-my)
      val len = hypot(wishX, wishZ).coerceAtLeast(1e-5f)
      val dirX = wishX / len
      val dirZ = wishZ / len
      horizVelX += (dirX * 13.5f - horizVelX) * (dt * 3.2f).coerceAtMost(1f)
      horizVelZ += (dirZ * 13.5f - horizVelZ) * (dt * 3.2f).coerceAtMost(1f)
      playerYawRad = lerpAngleRad(playerYawRad, angleBetweenXZ(dirX, dirZ), (dt * 8f).coerceAtMost(1f))
    } else {
      horizVelX *= (1f - dt * 0.6f).coerceAtLeast(0f)
      horizVelZ *= (1f - dt * 0.6f).coerceAtLeast(0f)
    }

    playerVelY = max(-22.5f, playerVelY - 12.5f * dt)
    playerX += horizVelX * dt
    playerZ += horizVelZ * dt
    playerY += playerVelY * dt

    val clamped = CollisionSolver.clampSwimmerToWorld(playerX, playerZ)
    playerX = clamped.first
    playerZ = clamped.second

    val groundH = computeGroundHeight(playerX, playerZ, playerY)
    if (playerY - groundH <= 27f) {
      deployParachute()
    }
  }

  private fun stepParachuteGlide(dt: Float) {
    val mx = input.moveX.coerceIn(-1f, 1f)
    val my = input.moveY.coerceIn(-1f, 1f)
    val mag = hypot(mx, my).coerceAtMost(1f)
    if (mag > 0.08f) {
      val fwdX = -sin(camYawRad)
      val fwdZ = -cos(camYawRad)
      val rightX = cos(camYawRad)
      val rightZ = -sin(camYawRad)
      val wishX = rightX * mx + fwdX * (-my)
      val wishZ = rightZ * mx + fwdZ * (-my)
      val len = hypot(wishX, wishZ).coerceAtLeast(1e-5f)
      val dirX = wishX / len
      val dirZ = wishZ / len
      val glideSpeed = 19.5f * mag
      horizVelX += (dirX * glideSpeed - horizVelX) * (dt * 4.5f).coerceAtMost(1f)
      horizVelZ += (dirZ * glideSpeed - horizVelZ) * (dt * 4.5f).coerceAtMost(1f)
      playerYawRad = lerpAngleRad(playerYawRad, angleBetweenXZ(dirX, dirZ), (dt * 7f).coerceAtMost(1f))
    } else {
      // Gentle forward canopy cruise if stick released
      val cruiseX = sin(playerYawRad) * 5.2f
      val cruiseZ = cos(playerYawRad) * 5.2f
      horizVelX += (cruiseX - horizVelX) * (dt * 2.5f).coerceAtMost(1f)
      horizVelZ += (cruiseZ - horizVelZ) * (dt * 2.5f).coerceAtMost(1f)
    }

    playerVelY += (-7.8f - playerVelY) * (dt * 5.0f).coerceAtMost(1f)
    playerX += horizVelX * dt
    playerZ += horizVelZ * dt
    playerY += playerVelY * dt

    val clamped = CollisionSolver.clampSwimmerToWorld(playerX, playerZ)
    playerX = clamped.first
    playerZ = clamped.second

    val groundH = computeGroundHeight(playerX, playerZ, playerY)
    val deepWater = CollisionSolver.isInDeepWater(playerX, playerZ)
    if (!deepWater && playerY <= groundH + 0.10f) {
      val (rx, rz, _) =
        CollisionSolver.resolveAll(
          playerX,
          playerZ,
          0.44f,
          world.aabbColliders,
          world.circleColliders,
          entityMinY = groundH,
          entityMaxY = groundH + 1.8f,
        )
      playerX = rx
      playerZ = rz
      playerY = computeGroundHeight(playerX, playerZ, groundH)
      playerVelY = 0f
      isGrounded = true
      isSwimming = false
      airPeakY = playerY
      airStartY = playerY
      activeLandingReaction = LandingReactionType.COMBAT_ROLL
      landingReactionDurationSec = 0.56f
      landingReactionTimerSec = 0.56f
      camLandingDipY = -0.35f
      dropPhase = DropPhase.GROUND
      camPitchRad = 0.28f
      onCombatSfx?.invoke(CombatSfxEvent.LAND_ROLL)
      showBanner("TOUCHDOWN! WELCOME TO VICE HORIZON")
    } else if (deepWater && playerY <= 0.1f) {
      playerY = -0.78f
      playerVelY = 0f
      isGrounded = true
      isSwimming = true
      airPeakY = playerY
      airStartY = playerY
      dropPhase = DropPhase.GROUND
      camPitchRad = 0.28f
      showBanner("SPLASH LANDING! SWIM TO SHORE OR BOATS")
    }
  }

  private fun stepCombatEffects(dt: Float) {
    val sparkIter = hitSparks.iterator()
    while (sparkIter.hasNext()) {
      val sp = sparkIter.next()
      sp.ageSec += dt
      if (sp.ageSec >= sp.durationSec) sparkIter.remove()
    }

    if (hitBannerTimerSec > 0f) {
      hitBannerTimerSec = max(0f, hitBannerTimerSec - dt)
      if (hitBannerTimerSec <= 0f) lastHitBanner = null
    }

    if (landingReactionTimerSec > 0f) {
      landingReactionTimerSec = max(0f, landingReactionTimerSec - dt)
      if (landingReactionTimerSec <= 0f) {
        activeLandingReaction = null
      }
    }
    camLandingDipY += (0f - camLandingDipY) * (dt * 7.5f).coerceAtMost(1f)
    camImpactShake = max(0f, camImpactShake - dt * 1.4f)

    if (weaponCooldownSec > 0f) weaponCooldownSec = max(0f, weaponCooldownSec - dt)
    if (weaponAimTimerSec > 0f) weaponAimTimerSec = max(0f, weaponAimTimerSec - dt)
    if (muzzleFlashTimerSec > 0f) muzzleFlashTimerSec = max(0f, muzzleFlashTimerSec - dt)
    if (comboWindowSec > 0f) comboWindowSec = max(0f, comboWindowSec - dt)
  }

  // ---------------------------------------------------------
  // REAL FLYING 3D BULLET PROJECTILE PHYSICS & HIT-ZONES
  // ---------------------------------------------------------
  private fun stepBullets(dt: Float) {
    val iter = bulletProjectiles.iterator()
    while (iter.hasNext()) {
      val b = iter.next()
      b.prevX = b.x
      b.prevY = b.y
      b.prevZ = b.z

      // Step flight forward with subtle realistic long-range gravity drop
      b.x += b.vx * dt
      b.y += b.vy * dt - 1.6f * dt * dt
      b.z += b.vz * dt
      b.ageSec += dt

      val stepDist = hypot(hypot(b.x - b.prevX, b.z - b.prevZ), b.y - b.prevY)
      b.remainingDist -= stepDist

      // 1. Check ground impact
      val groundY = computeGroundHeight(b.x, b.z, b.y)
      if (b.y <= groundY + 0.04f) {
        hitSparks += HitSparkState(b.x, groundY + 0.08f, b.z, floatArrayOf(1.0f, 0.80f, 0.35f))
        iter.remove()
        continue
      }

      // 2. Check wall / building AABB occlusion along segment (height-aware!)
      val segDx = b.x - b.prevX
      val segDz = b.z - b.prevZ
      val segLenXZ = hypot(segDx, segDz).coerceAtLeast(1e-4f)
      val rdx = segDx / segLenXZ
      val rdz = segDz / segLenXZ

      var earliestWallT = segLenXZ + 0.01f
      var hitWall = false
      for (box in world.aabbColliders) {
        if (box.tag.contains("Sofa") || box.tag.contains("Table") || box.tag.contains("Bench")) {
          continue
        }
        val tWall = intersectRayAabbXZ(b.prevX, b.prevZ, rdx, rdz, box.minX, box.minZ, box.maxX, box.maxZ)
        if (tWall != null && tWall in 0f..segLenXZ && b.y in box.minY..box.maxY) {
          if (tWall < earliestWallT) {
            earliestWallT = tWall
            hitWall = true
          }
        }
      }

      // 2B. Check Gloo Wall Shield interception along bullet segment
      var earliestGlooT = earliestWallT
      var hitGlooWall: GlooWallState? = null
      val glooIter = glooWalls.iterator()
      while (glooIter.hasNext()) {
        val wall = glooIter.next()
        if (wall.hp <= 0f) continue
        if (b.y !in wall.y..(wall.y + 2.35f)) continue
        val tFrac = intersectSegmentGlooWall(b.prevX, b.prevZ, b.x, b.z, wall)
        if (tFrac != null) {
          val distAlong = tFrac * segLenXZ
          if (distAlong < earliestGlooT) {
            earliestGlooT = distAlong
            hitGlooWall = wall
          }
        }
      }
      if (hitGlooWall != null && earliestGlooT <= earliestWallT) {
        val frac = (earliestGlooT / segLenXZ).coerceIn(0f, 1f)
        val hx = b.prevX + segDx * frac
        val hy = b.prevY + (b.y - b.prevY) * frac
        val hz = b.prevZ + segDz * frac
        hitGlooWall.hp = max(0f, hitGlooWall.hp - b.damage)
        hitSparks += HitSparkState(hx, hy, hz, floatArrayOf(0.24f, 0.90f, 1.0f), durationSec = 0.22f)
        if (hitGlooWall.hp <= 0f) {
          glooWalls.remove(hitGlooWall)
          hitSparks += HitSparkState(hitGlooWall.x, hitGlooWall.y + 1.0f, hitGlooWall.z, floatArrayOf(0.55f, 0.96f, 1.0f), durationSec = 0.36f)
        }
        iter.remove()
        continue
      }

      // 3. Branch enemy vs player projectile collisions
      if (b.fromEnemy) {
        var bestHitDist = earliestWallT
        var playerHitY = b.y
        var hitPlayer = false
        var bestBot: PedestrianRuntimeState? = null
        var bestBotHitY = b.y

        if (isPlaying && dropPhase == DropPhase.GROUND && activeVehicle == null && activeBoat == null && !isSwimming) {
          val tHit = intersectSegmentCircleXZ(b.prevX, b.prevZ, b.x, b.z, playerX, playerZ, 0.52f)
          if (tHit != null) {
            val distAlong = tHit * segLenXZ
            val hitY = b.prevY + (b.y - b.prevY) * tHit
            if (distAlong < bestHitDist && hitY in playerY..(playerY + 1.90f)) {
              bestHitDist = distAlong
              playerHitY = hitY
              hitPlayer = true
            }
          }
        }

        for (ped in pedestrians) {
          if (ped.reactionState == NpcReactionState.KNOCKED_DOWN || ped.id == b.attackerId || ped.isFriend) continue
          val tHit = intersectSegmentCircleXZ(b.prevX, b.prevZ, b.x, b.z, ped.x, ped.z, 0.54f)
          if (tHit != null) {
            val distAlong = tHit * segLenXZ
            val hitY = b.prevY + (b.y - b.prevY) * tHit
            if (distAlong < bestHitDist && hitY in ped.y..(ped.y + 2.0f)) {
              bestHitDist = distAlong
              bestBot = ped
              bestBotHitY = hitY
              hitPlayer = false
            }
          }
        }

        if (hitPlayer && !isPlayerEliminated) {
          val zone = if (playerHitY - playerY >= 1.28f) HitZone.HEAD else HitZone.TORSO
          applyIncomingDamageToPlayer(
            rawDamage = b.damage,
            hitZone = zone,
            attackerId = b.attackerId,
            attackerName = b.attackerName,
            attackerWeapon = b.attackerWeapon,
          )
          hitSparks += HitSparkState(playerX, playerHitY, playerZ, floatArrayOf(1.0f, 0.25f, 0.20f))
          onCombatSfx?.invoke(CombatSfxEvent.HIT)
          iter.remove()
          continue
        }
        if (bestBot != null) {
          val localHitY = bestBotHitY - bestBot.y
          val zone =
            when {
              localHitY >= 1.28f -> HitZone.HEAD
              localHitY >= 0.58f -> HitZone.TORSO
              else -> HitZone.LEGS
            }
          val attackerPed = b.attackerId?.let { id -> pedestrians.firstOrNull { it.id == id } }
          applyDamageToPedestrian(
            ped = bestBot,
            damage = b.damage * (if (zone == HitZone.HEAD) 1.65f else 1.0f),
            knockbackSpeed = b.knockback,
            dirX = rdx,
            dirZ = rdz,
            hitZone = zone,
            fromGunfire = true,
            fromPlayer = false,
            attackerBot = attackerPed,
          )
          onCombatSfx?.invoke(CombatSfxEvent.HIT)
          iter.remove()
          continue
        }
        if (hitWall) {
          val wx = b.prevX + rdx * earliestWallT
          val wz = b.prevZ + rdz * earliestWallT
          hitSparks += HitSparkState(wx, b.y, wz, floatArrayOf(1.0f, 0.78f, 0.28f))
          iter.remove()
          continue
        }
      } else {
        // 3B. Check living Pedestrians & upright Ejected NPCs along segment
        var bestHitDist = earliestWallT
        var bestPed: PedestrianRuntimeState? = null
        var bestEjected: EjectedNpcState? = null
        var bestHitY = b.y

        for (ped in pedestrians) {
          if (ped.reactionState == NpcReactionState.KNOCKED_DOWN || ped.isFriend) continue
          val tHit = intersectSegmentCircleXZ(b.prevX, b.prevZ, b.x, b.z, ped.x, ped.z, 0.56f)
          if (tHit != null) {
            val distAlong = tHit * segLenXZ
            val hitY = b.prevY + (b.y - b.prevY) * tHit
            if (distAlong < bestHitDist && hitY in ped.y..(ped.y + 2.05f)) {
              bestHitDist = distAlong
              bestPed = ped
              bestEjected = null
              bestHitY = hitY
            }
          }
        }

        for (npc in ejectedNpcs) {
          if (npc.isDead || npc.inWater || npc.fallProgress > 0.6f) continue
          val tHit = intersectSegmentCircleXZ(b.prevX, b.prevZ, b.x, b.z, npc.x, npc.z, 0.56f)
          if (tHit != null) {
            val distAlong = tHit * segLenXZ
            val hitY = b.prevY + (b.y - b.prevY) * tHit
            if (distAlong < bestHitDist && hitY in npc.y..(npc.y + 2.0f)) {
              bestHitDist = distAlong
              bestEjected = npc
              bestPed = null
              bestHitY = hitY
            }
          }
        }

        when {
          bestPed != null -> {
            val localHitY = bestHitY - bestPed.y
            val zone =
              when {
                localHitY >= 1.28f -> HitZone.HEAD
                localHitY >= 0.58f -> HitZone.TORSO
                else -> HitZone.LEGS
              }
            val zoneDamage =
              when (zone) {
                HitZone.HEAD -> b.damage * 2.35f
                HitZone.TORSO -> b.damage
                HitZone.LEGS -> b.damage * 0.72f
                HitZone.NONE -> b.damage
              }
            applyDamageToPedestrian(
              ped = bestPed,
              damage = zoneDamage,
              knockbackSpeed = b.knockback * (if (zone == HitZone.HEAD) 1.35f else 1f),
              dirX = rdx,
              dirZ = rdz,
              hitZone = zone,
              fromGunfire = true,
              fromPlayer = true,
            )
            onCombatSfx?.invoke(if (zone == HitZone.HEAD) CombatSfxEvent.HEADSHOT else CombatSfxEvent.HIT)
            iter.remove()
            continue
          }
          bestEjected != null -> {
            val zone =
              when {
                bestHitY - bestEjected.y >= 1.28f -> HitZone.HEAD
                bestHitY - bestEjected.y >= 0.58f -> HitZone.TORSO
                else -> HitZone.LEGS
              }
            val zoneDamage = if (zone == HitZone.HEAD) b.damage * 2.2f else b.damage
            applyDamageToEjectedNpc(bestEjected, zoneDamage, b.knockback, rdx, rdz, zone)
            onCombatSfx?.invoke(if (zone == HitZone.HEAD) CombatSfxEvent.HEADSHOT else CombatSfxEvent.HIT)
            iter.remove()
            continue
          }
          hitWall -> {
            val wx = b.prevX + rdx * earliestWallT
            val wz = b.prevZ + rdz * earliestWallT
            hitSparks += HitSparkState(wx, b.y, wz, floatArrayOf(1.0f, 0.78f, 0.28f))
            iter.remove()
            continue
          }
        }
      }

      if (b.remainingDist <= 0f || b.ageSec > 2.2f) {
        iter.remove()
      }
    }
  }

  // ---------------------------------------------------------
  // PLAYER COMBAT, SITTING, SCOPE & FRIENDSHIP ACTIONS
  // ---------------------------------------------------------

  @Synchronized
  fun toggleSit(): Boolean {
    if (!isPlaying || isPaused || isGunShopOpen || isStyleShopOpen || isPlayerEliminated) return false
    if (activeVehicle != null || activeBoat != null || activeCarjack != null || isSwimming) {
      return false
    }
    isSitting = !isSitting
    if (isSitting) {
      input.autoSprint = false
      isScoped = false
      activeAttackMove = null
      attackTimerSec = 0f
      playerSpeedNorm = 0f
      weaponAimTimerSec = 0f
    }
    return isSitting
  }

  @Synchronized
  fun toggleAutoSprint(): Boolean {
    if (!isPlaying || isPaused || isGunShopOpen || isBagOpen || isStyleShopOpen || isPlayerEliminated) {
      input.autoSprint = false
      return false
    }
    if (activeVehicle != null || activeBoat != null || activeCarjack != null || activeParkour != null) {
      input.autoSprint = false
      return false
    }
    input.autoSprint = !input.autoSprint
    if (input.autoSprint) {
      isSitting = false
      isScoped = false
      activeAttackMove = null
      attackTimerSec = 0f
    }
    return input.autoSprint
  }

  @Synchronized
  fun toggleScope(): Boolean {
    if (!canActOnFootCombat() || equippedWeapon == WeaponType.FISTS) {
      isScoped = false
      return false
    }
    isSitting = false
    isScoped = !isScoped
    if (isScoped) {
      input.autoSprint = false
      // Snap camera smoothly eye-level forward downrange so scope looks right where Motu faces
      camYawRad = normalizeAngleRad(playerYawRad + PI.toFloat())
      camPitchRad = 0.02f
    } else {
      camPitchRad = 0.24f
    }
    return isScoped
  }

  @Synchronized
  fun befriendNearbyNpc(): Boolean {
    if (!canActOnFootCombat()) return false
    val candidate = nearbyFriendCandidate ?: return false
    val currentFriends = pedestrians.count { it.isFriend }
    if (currentFriends >= 3) {
      showBanner("SQUAD FULL (MAX 3 FRIENDS)")
      return false
    }
    candidate.isFriend = true
    candidate.friendSlot = currentFriends
    candidate.reactionState = NpcReactionState.COMPANION
    candidate.hp = candidate.maxHp
    candidate.panicTimerSec = 0f
    candidate.armsUpNorm = 0f
    candidate.hitReactZone = HitZone.NONE
    candidate.hitReactTimerSec = 0f
    candidate.isHostileTarget = false
    candidate.attackMove = null
    hitSparks += HitSparkState(candidate.x, 1.85f, candidate.z, floatArrayOf(0.22f, 0.96f, 0.72f), durationSec = 0.45f)
    onCombatSfx?.invoke(CombatSfxEvent.FRIEND)
    showBanner("FRIEND JOINED YOUR SQUAD!")
    nearbyFriendCandidate = null
    return true
  }

  @Synchronized
  fun dismissFriends() {
    for (ped in pedestrians) {
      if (ped.isFriend) {
        ped.isFriend = false
        ped.sitProgress = 0f
        ped.reactionState = NpcReactionState.STROLLING
      }
    }
    showBanner("FRIENDS DISMISSED")
  }

  @Synchronized
  fun triggerPunch() {
    if (!canActOnFootCombat()) return
    isScoped = false
    input.punchQueued = true
  }

  @Synchronized
  fun triggerKick() {
    if (!canActOnFootCombat()) return
    isScoped = false
    input.kickQueued = true
  }

  @Synchronized
  fun openGunShop() {
    if (isPlayerEliminated) return
    isPaused = false
    isBagOpen = false
    isStyleShopOpen = false
    isGunShopOpen = true
    isSitting = false
    isScoped = false
  }

  @Synchronized
  fun closeGunShop() {
    isGunShopOpen = false
  }

  @Synchronized
  fun openBag() {
    if (!isPlaying || isPlayerEliminated) return
    isPaused = false
    isGunShopOpen = false
    isStyleShopOpen = false
    isBagOpen = true
    isScoped = false
  }

  @Synchronized
  fun closeBag() {
    isBagOpen = false
  }

  @Synchronized
  fun toggleBag(): Boolean {
    if (isPlayerEliminated) return false
    if (isBagOpen) closeBag() else openBag()
    return isBagOpen
  }

  @Synchronized
  fun useQuickMedkit(): Boolean {
    if (!isPlaying || isPlayerEliminated) return false
    val med = bagItems.firstOrNull { it.kind == LootItemKind.MEDKIT }
    if (med == null) {
      showBanner("NO MEDKITS IN BAG")
      return false
    }
    if (playerHealth >= 99.5f) {
      showBanner("HEALTH ALREADY FULL")
      return false
    }
    bagItems.remove(med)
    playerHealth = min(100f, playerHealth + med.kind.healAmount)
    onCombatSfx?.invoke(CombatSfxEvent.FRIEND)
    showBanner("MEDKIT USED · HP ${playerHealth.toInt()}")
    return true
  }

  @Synchronized
  fun deployPlayerGlooWall(): Boolean {
    if (!canActOnFootCombat()) return false
    val bagGlooIdx = bagItems.indexOfFirst { it.kind == LootItemKind.GLOO_WALL }
    when {
      playerReserveGlooWalls > 0 -> playerReserveGlooWalls--
      bagGlooIdx >= 0 -> bagItems.removeAt(bagGlooIdx)
      else -> {
        showBanner("NO GLOO WALLS LEFT")
        return false
      }
    }
    val wallX = playerX + sin(playerYawRad) * 2.15f
    val wallZ = playerZ + cos(playerYawRad) * 2.15f
    val wallY = computeGroundHeight(wallX, wallZ, playerY)
    glooWalls +=
      GlooWallState(
        id = "gloo_${++glooUidSeq}",
        ownerId = "player",
        x = wallX,
        y = wallY,
        z = wallZ,
        yawRad = playerYawRad,
        isPlayerOwned = true,
      )
    hitSparks += HitSparkState(wallX, wallY + 1.0f, wallZ, floatArrayOf(0.24f, 0.90f, 1.0f), durationSec = 0.35f)
    onCombatSfx?.invoke(CombatSfxEvent.CLIMB)
    showBanner("GLOO WALL DEPLOYED!")
    return true
  }

  @Synchronized
  fun useOrEquipBagItem(uid: String): Boolean {
    val idx = bagItems.indexOfFirst { it.uid == uid }
    if (idx < 0) return false
    val item = bagItems[idx]
    return when (item.kind.category) {
      LootCategory.MEDICAL -> {
        if (playerHealth >= 99.5f) {
          showBanner("HEALTH ALREADY FULL")
          false
        } else {
          bagItems.removeAt(idx)
          playerHealth = min(100f, playerHealth + item.kind.healAmount)
          onCombatSfx?.invoke(CombatSfxEvent.FRIEND)
          showBanner("MEDKIT USED · +${item.kind.healAmount.toInt()} HP")
          true
        }
      }
      LootCategory.GLOO_WALL -> {
        if (!canActOnFootCombat()) {
          showBanner("EXIT VEHICLE TO DEPLOY GLOO")
          false
        } else {
          bagItems.removeAt(idx)
          playerReserveGlooWalls++
          deployPlayerGlooWall()
        }
      }
      LootCategory.WEAPON -> {
        val weapon = item.kind.weapon ?: return false
        ownedWeapons += weapon
        equippedWeapon = weapon
        onCombatSfx?.invoke(CombatSfxEvent.CASH)
        showBanner("EQUIPPED ${weapon.displayName}")
        true
      }
      LootCategory.VEST -> {
        val prevLevel = playerVestLevel
        val prevDur = playerVestDurability
        bagItems.removeAt(idx)
        playerVestLevel = item.kind.armorLevel
        playerVestDurability = item.durability.coerceAtLeast(1f)
        if (prevLevel >= 3 && prevDur > 5f && bagItems.size < MAX_BAG_SLOTS) {
          val prevKind = if (prevLevel == 4) LootItemKind.VEST_LV4 else LootItemKind.VEST_LV3
          bagItems.add(BagItem("bag_${++lootUidSeq}", prevKind, prevDur))
        }
        onCombatSfx?.invoke(CombatSfxEvent.FRIEND)
        showBanner("EQUIPPED ${item.kind.displayName}")
        true
      }
      LootCategory.HELMET -> {
        val prevLevel = playerHelmetLevel
        val prevDur = playerHelmetDurability
        bagItems.removeAt(idx)
        playerHelmetLevel = item.kind.armorLevel
        playerHelmetDurability = item.durability.coerceAtLeast(1f)
        if (prevLevel >= 3 && prevDur > 5f && bagItems.size < MAX_BAG_SLOTS) {
          val prevKind = if (prevLevel == 4) LootItemKind.HELMET_LV4 else LootItemKind.HELMET_LV3
          bagItems.add(BagItem("bag_${++lootUidSeq}", prevKind, prevDur))
        }
        onCombatSfx?.invoke(CombatSfxEvent.FRIEND)
        showBanner("EQUIPPED ${item.kind.displayName}")
        true
      }
      LootCategory.CURRENCY -> false
    }
  }

  @Synchronized
  fun removeBagItem(uid: String, dropToWorld: Boolean = true): Boolean {
    val idx = bagItems.indexOfFirst { it.uid == uid }
    if (idx < 0) return false
    val removed = bagItems.removeAt(idx)
    recomputeOwnedWeaponsFromPurchasedAndBag()
    if (dropToWorld) {
      val dropX = playerX + sin(playerYawRad) * 1.45f
      val dropZ = playerZ + cos(playerYawRad) * 1.45f
      val dropY = computeGroundHeight(dropX, dropZ, playerY + 0.4f)
      worldLootItems +=
        WorldLootItem(
          uid = "loot_${++lootUidSeq}",
          kind = removed.kind,
          x = dropX,
          y = dropY,
          z = dropZ,
          pickupCooldownSec = 3.2f,
        )
    }
    onCombatSfx?.invoke(CombatSfxEvent.KICK)
    showBanner("REMOVED ${removed.kind.displayName} (${bagItems.size}/$MAX_BAG_SLOTS)")
    return true
  }

  @Synchronized
  fun returnToLobby() {
    if (isPlaying && !matchCoinsAwarded && (matchElapsedSec >= 4f || npcDefeatedCount > matchStartKoCount)) {
      awardMatchCoinsIfNeeded(isEliminated = false)
      showBanner("MATCH COMPLETE · +$lastMatchCoinsEarned COINS SAVED")
    }
    isPlaying = false
    isPaused = false
    isGunShopOpen = false
    isStyleShopOpen = false
    isBagOpen = false
    isPlayerEliminated = false
    isSpectatingKiller = false
    playerFallProgress = 0f
    spectatedKillerPedId = null
    playerHealth = 100f
    wantedLevel = 0
    dropPhase = DropPhase.GROUND
    activeVehicle = null
    activeBoat = null
    activeCarjack = null
    activeParkour = null
    isSitting = false
    isScoped = false
    input.sprintHeld = false
    input.autoSprint = false
    sitProgress = 0f
    lobbyHeroYawRad = 0.16f
    onEconomyChanged?.invoke()
  }

  @Synchronized
  fun startSpectatingKiller() {
    if (!isPlaying || !isPlayerEliminated) return
    ensureLiveSpectatedTarget()
    isSpectatingKiller = true
    val target = pedestrians.firstOrNull { it.id == spectatedKillerPedId }
    val label = target?.let { rivalDisplayName(it) } ?: eliminatedByName ?: "KILLER"
    showBanner("SPECTATING $label · VIEW ONLY")
  }

  @Synchronized
  fun openStyleShop() {
    isStyleShopOpen = true
    isGunShopOpen = false
    isBagOpen = false
  }

  @Synchronized
  fun closeStyleShop() {
    isStyleShopOpen = false
  }

  @Synchronized
  fun currentHeroAppearance(): com.example.game.world.CharacterAppearance {
    val base = HERO_PRESETS.getOrElse(heroPresetIndex) { "HERO" to com.example.game.world.CartoonPresets.PLAYER_HERO }.second
    val tunic = equippedCloth.tunicColor ?: base.tunicColor
    val vest = if (equippedCloth != ClothStyle.PRESET_DEFAULT) equippedCloth.vestColor else base.vestColor
    val hairStyle = equippedHair.hairStyle ?: base.hairStyle
    val hairColor = equippedHair.hairColor ?: base.hairColor
    val pants = equippedPants.pantsColor ?: base.pantsColor
    return base.copy(
      hairStyle = hairStyle,
      tunicColor = tunic,
      vestColor = vest,
      pantsColor = pants,
      hairColor = hairColor,
    )
  }

  @Synchronized
  fun buyOrEquipCloth(style: ClothStyle): Boolean {
    if (style in ownedCloths) {
      equippedCloth = style
      onCombatSfx?.invoke(CombatSfxEvent.CASH)
      showBanner("EQUIPPED: ${style.displayName}")
      onEconomyChanged?.invoke()
      return true
    }
    if (playerCash >= style.price) {
      playerCash -= style.price
      ownedCloths += style
      equippedCloth = style
      onCombatSfx?.invoke(CombatSfxEvent.CASH)
      showBanner("UNLOCKED: ${style.displayName}!")
      onEconomyChanged?.invoke()
      return true
    }
    showBanner("NEED $${style.price - playerCash} MORE COINS")
    return false
  }

  @Synchronized
  fun buyOrEquipHair(style: HairCustomization): Boolean {
    if (style in ownedHairs) {
      equippedHair = style
      onCombatSfx?.invoke(CombatSfxEvent.CASH)
      showBanner("EQUIPPED: ${style.displayName}")
      onEconomyChanged?.invoke()
      return true
    }
    if (playerCash >= style.price) {
      playerCash -= style.price
      ownedHairs += style
      equippedHair = style
      onCombatSfx?.invoke(CombatSfxEvent.CASH)
      showBanner("UNLOCKED: ${style.displayName}!")
      onEconomyChanged?.invoke()
      return true
    }
    showBanner("NEED $${style.price - playerCash} MORE COINS")
    return false
  }

  @Synchronized
  fun buyOrEquipPants(style: PantsStyle): Boolean {
    if (style in ownedPants) {
      equippedPants = style
      onCombatSfx?.invoke(CombatSfxEvent.CASH)
      showBanner("EQUIPPED: ${style.displayName}")
      onEconomyChanged?.invoke()
      return true
    }
    if (playerCash >= style.price) {
      playerCash -= style.price
      ownedPants += style
      equippedPants = style
      onCombatSfx?.invoke(CombatSfxEvent.CASH)
      showBanner("UNLOCKED: ${style.displayName}!")
      onEconomyChanged?.invoke()
      return true
    }
    showBanner("NEED $${style.price - playerCash} MORE COINS")
    return false
  }

  @Synchronized
  fun buyOrEquipGunSkin(skin: GunSkin): Boolean {
    if (skin in ownedGunSkins) {
      equippedGunSkin = skin
      onCombatSfx?.invoke(CombatSfxEvent.CASH)
      showBanner("GUN PAINT: ${skin.displayName}")
      onEconomyChanged?.invoke()
      return true
    }
    if (playerCash >= skin.price) {
      playerCash -= skin.price
      ownedGunSkins += skin
      equippedGunSkin = skin
      onCombatSfx?.invoke(CombatSfxEvent.CASH)
      showBanner("UNLOCKED PAINT: ${skin.displayName}!")
      onEconomyChanged?.invoke()
      return true
    }
    showBanner("NEED $${skin.price - playerCash} MORE COINS")
    return false
  }

  @Synchronized
  fun buyOrEquipCarSkin(skin: CarSkin): Boolean {
    if (skin in ownedCarSkins) {
      equippedCarSkin = skin
      onCombatSfx?.invoke(CombatSfxEvent.CASH)
      showBanner("CAR SKIN: ${skin.displayName}")
      onEconomyChanged?.invoke()
      return true
    }
    if (playerCash >= skin.price) {
      playerCash -= skin.price
      ownedCarSkins += skin
      equippedCarSkin = skin
      onCombatSfx?.invoke(CombatSfxEvent.CASH)
      showBanner("UNLOCKED CAR: ${skin.displayName}!")
      onEconomyChanged?.invoke()
      return true
    }
    showBanner("NEED $${skin.price - playerCash} MORE COINS")
    return false
  }

  @Synchronized
  fun applyCustomizationState(
    cloth: ClothStyle,
    ownedClothsSet: Set<ClothStyle>,
    hair: HairCustomization,
    ownedHairsSet: Set<HairCustomization>,
    pants: PantsStyle,
    ownedPantsSet: Set<PantsStyle>,
    gunSkin: GunSkin,
    ownedGunSkinsSet: Set<GunSkin>,
    carSkin: CarSkin,
    ownedCarSkinsSet: Set<CarSkin>,
  ) {
    ownedCloths.clear()
    ownedCloths += ClothStyle.PRESET_DEFAULT
    ownedCloths += ownedClothsSet
    equippedCloth = if (cloth in ownedCloths) cloth else ClothStyle.PRESET_DEFAULT

    ownedHairs.clear()
    ownedHairs += HairCustomization.PRESET_DEFAULT
    ownedHairs += ownedHairsSet
    equippedHair = if (hair in ownedHairs) hair else HairCustomization.PRESET_DEFAULT

    ownedPants.clear()
    ownedPants += PantsStyle.PRESET_DEFAULT
    ownedPants += ownedPantsSet
    equippedPants = if (pants in ownedPants) pants else PantsStyle.PRESET_DEFAULT

    ownedGunSkins.clear()
    ownedGunSkins += GunSkin.STANDARD_STEEL
    ownedGunSkins += ownedGunSkinsSet
    equippedGunSkin = if (gunSkin in ownedGunSkins) gunSkin else GunSkin.STANDARD_STEEL

    ownedCarSkins.clear()
    ownedCarSkins += CarSkin.FACTORY_DEFAULT
    ownedCarSkins += ownedCarSkinsSet
    equippedCarSkin = if (carSkin in ownedCarSkins) carSkin else CarSkin.FACTORY_DEFAULT
  }

  @Synchronized
  fun cycleHeroPreset(): Int {
    heroPresetIndex = (heroPresetIndex + 1) % HERO_PRESETS.size
    showBanner("HERO: ${HERO_PRESETS[heroPresetIndex].first}")
    return heroPresetIndex
  }

  @Synchronized
  fun toggleLobbySitPose(): Boolean {
    if (isPlaying) return false
    isSitting = !isSitting
    return isSitting
  }

  @Synchronized
  fun cycleLobbyDropZone(): LobbyDropZone {
    val entries = LobbyDropZone.entries
    lobbyDropZone = entries[(lobbyDropZone.ordinal + 1) % entries.size]
    showBanner("MODE: ${lobbyDropZone.title} · ${lobbyDropZone.subtitle.uppercase()}")
    return lobbyDropZone
  }

  @Synchronized
  fun inviteAiBuddyToLobby(id: String, name: String, heroIdx: Int): Boolean {
    if (lobbySquadMembers.any { it.id == id }) {
      removeLobbySquadMember(id)
      return false
    }
    if (lobbySquadMembers.size >= 3) {
      showBanner("SQUAD FULL (4/4 PLAYERS)")
      return false
    }
    val member =
      LobbySquadMember(
        id = id,
        name = name,
        badge = "AI",
        heroIndex = heroIdx.coerceIn(0, HERO_PRESETS.lastIndex),
        weapon = WeaponType.ASSAULT_RIFLE,
        isReady = true,
        isRemoteP2P = false,
      )
    lobbySquadMembers.add(member)
    onCombatSfx?.invoke(CombatSfxEvent.FRIEND)
    showBanner("$name JOINED LOBBY SQUAD!")
    return true
  }

  @Synchronized
  fun removeLobbySquadMember(id: String) {
    val removed = lobbySquadMembers.removeAll { it.id == id }
    if (removed) {
      showBanner("LEFT SQUAD")
    }
  }

  @Synchronized
  fun syncRemoteP2PSquad(remotePeers: List<LobbySquadMember>) {
    lobbySquadMembers.removeAll { it.isRemoteP2P }
    for (peer in remotePeers) {
      if (lobbySquadMembers.size < 3 && lobbySquadMembers.none { it.id == peer.id }) {
        lobbySquadMembers.add(peer)
      }
    }
  }

  @Synchronized
  fun updateRemotePlayerPose(pose: RemotePlayerPose) {
    val prev = remotePlayerPoses[pose.peerId]
    val nextCycle =
      if (prev != null && pose.speedNorm > 0.08f) {
        prev.walkCycle + pose.speedNorm * 0.42f
      } else {
        prev?.walkCycle ?: 0f
      }
    remotePlayerPoses[pose.peerId] = pose.copy(walkCycle = nextCycle)
  }

  @Synchronized
  fun remotePosesSnapshot(): List<RemotePlayerPose> = remotePlayerPoses.values.toList()

  @Synchronized
  fun lobbySquadSnapshot(): List<LobbySquadMember> = lobbySquadMembers.toList()

  @Synchronized
  fun applyCloudAccountDefaults(
    heroIndex: Int,
    equipped: WeaponType,
    owned: Set<WeaponType>,
    cash: Int,
    koCount: Int,
  ) {
    heroPresetIndex = heroIndex.coerceIn(0, HERO_PRESETS.lastIndex)
    purchasedWeapons.clear()
    purchasedWeapons += WeaponType.FISTS
    purchasedWeapons += owned
    recomputeOwnedWeaponsFromPurchasedAndBag()
    equippedWeapon = if (equipped in ownedWeapons) equipped else WeaponType.FISTS
    playerCash = cash.coerceAtLeast(0)
    npcDefeatedCount = koCount.coerceAtLeast(0)
  }

  private fun activateLobbyAiSquadmatesInWorld() {
    val aiLobbyMembers = lobbySquadMembers.filter { !it.isRemoteP2P }
    if (aiLobbyMembers.isEmpty()) return
    for (ped in pedestrians) {
      ped.isFriend = false
    }
    val candidates = pedestrians.filter { !it.isBraveDefender && !it.isRivalFighter }
    for ((slotIdx, member) in aiLobbyMembers.withIndex()) {
      val ped = candidates.getOrNull(slotIdx) ?: break
      ped.isFriend = true
      ped.friendSlot = slotIdx
      ped.reactionState = NpcReactionState.COMPANION
      ped.hp = ped.maxHp
      ped.fallProgress = 0f
      ped.panicTimerSec = 0f
      ped.x = world.playerSpawnX + (slotIdx - 1) * 1.8f
      ped.z = world.playerSpawnZ + 2.2f
    }
  }

  @Synchronized
  fun buyOrEquipWeapon(weapon: WeaponType): Boolean {
    if (weapon in ownedWeapons) {
      equippedWeapon = weapon
      if (weapon == WeaponType.FISTS) isScoped = false
      onCombatSfx?.invoke(CombatSfxEvent.CASH)
      onEconomyChanged?.invoke()
      return true
    }
    if (playerCash >= weapon.price) {
      playerCash -= weapon.price
      purchasedWeapons += weapon
      ownedWeapons += weapon
      equippedWeapon = weapon
      onCombatSfx?.invoke(CombatSfxEvent.CASH)
      onEconomyChanged?.invoke()
      return true
    }
    return false
  }

  private fun recomputeOwnedWeaponsFromPurchasedAndBag() {
    ownedWeapons.clear()
    ownedWeapons += purchasedWeapons
    for (item in bagItems) {
      item.kind.weapon?.let { ownedWeapons += it }
    }
    if (equippedWeapon !in ownedWeapons) {
      equippedWeapon = ownedWeapons.lastOrNull() ?: WeaponType.FISTS
      if (equippedWeapon == WeaponType.FISTS) {
        isScoped = false
        weaponAimTimerSec = 0f
      }
    }
  }

  @Synchronized
  fun cycleWeapon(): WeaponType {
    val ownedList = WeaponType.entries.filter { it in ownedWeapons }
    if (ownedList.isEmpty()) return WeaponType.FISTS
    val curIdx = ownedList.indexOf(equippedWeapon).coerceAtLeast(0)
    equippedWeapon = ownedList[(curIdx + 1) % ownedList.size]
    if (equippedWeapon == WeaponType.FISTS) {
      isScoped = false
      weaponAimTimerSec = 0f
    } else {
      weaponAimTimerSec = 0.9f
    }
    return equippedWeapon
  }

  @Synchronized
  fun teleportToGunShop() {
    dropPhase = DropPhase.GROUND
    activeVehicle = null
    activeBoat = null
    activeCarjack = null
    isSwimming = false
    isSitting = false
    isScoped = false
    sitProgress = 0f
    activeParkour = null
    activeLandingReaction = null
    landingReactionTimerSec = 0f
    airborneFallNorm = 0f
    playerX = world.gunShopBuyPos.first
    playerZ = world.gunShopBuyPos.second + 1.2f
    playerY = computeGroundHeight(playerX, playerZ, 0f)
    airPeakY = playerY
    airStartY = playerY
    playerYawRad = PI.toFloat()
    camYawRad = normalizeAngleRad(playerYawRad + PI.toFloat())
    camPitchRad = 0.25f
    updateGunShopProximity()
  }

  @Synchronized
  internal fun showBanner(text: String) {
    lastHitBanner = text
    hitBannerTimerSec = 1.6f
  }

  private fun canActOnFootCombat(): Boolean =
    isPlaying &&
      !isPaused &&
      !isGunShopOpen &&
      !isStyleShopOpen &&
      !isBagOpen &&
      !isPlayerEliminated &&
      dropPhase == DropPhase.GROUND &&
      activeVehicle == null &&
      activeBoat == null &&
      activeCarjack == null &&
      !isSwimming

  private fun startMeleeMove(isKick: Boolean) {
    isSitting = false
    val current = activeAttackMove
    if (current != null && attackProgress < 0.65f) return

    val move =
      if (isKick) {
        val next = if (comboWindowSec > 0f) (kickComboIdx + 1) % 2 else 0
        kickComboIdx = next
        if (next == 0) CombatMoveType.KICK_FRONT else CombatMoveType.KICK_ROUNDHOUSE
      } else {
        val next = if (comboWindowSec > 0f) (punchComboIdx + 1) % 3 else 0
        punchComboIdx = next
        when (next) {
          0 -> CombatMoveType.PUNCH_JAB
          1 -> CombatMoveType.PUNCH_HOOK
          else -> CombatMoveType.PUNCH_UPPERCUT
        }
      }

    activeAttackMove = move
    attackTimerSec = 0f
    hasAppliedAttackHit = false
    comboWindowSec = move.durationSec + 0.55f
    onCombatSfx?.invoke(if (isKick) CombatSfxEvent.KICK else CombatSfxEvent.PUNCH)

    val target = findClosestNonFriendNpc(maxDist = 3.6f, requireInFront = false)
    if (target != null) {
      val dx = target.first - playerX
      val dz = target.second - playerZ
      val dist = hypot(dx, dz)
      if (dist > 0.05f) {
        playerYawRad = angleBetweenXZ(dx, dz)
        if (dist > 1.25f) {
          val lunge = min(0.40f, dist - 1.05f)
          playerX += (dx / dist) * lunge
          playerZ += (dz / dist) * lunge
        }
      }
    }
  }

  private fun resolveMeleeStrikeHit(move: CombatMoveType) {
    val reach = if (move.isKick) 2.55f else 2.25f
    val fwdX = sin(playerYawRad)
    val fwdZ = cos(playerYawRad)
    var hitCount = 0

    for (ped in pedestrians) {
      if (ped.reactionState == NpcReactionState.KNOCKED_DOWN || ped.isFriend) continue
      val dx = ped.x - playerX
      val dz = ped.z - playerZ
      val dist = hypot(dx, dz)
      if (dist > reach) continue
      val dot = if (dist > 0.01f) (dx / dist) * fwdX + (dz / dist) * fwdZ else 1f
      if (dist > 1.1f && dot < -0.15f) continue

      val pushX = if (dist > 0.01f) dx / dist else fwdX
      val pushZ = if (dist > 0.01f) dz / dist else fwdZ
      applyDamageToPedestrian(
        ped = ped,
        damage = move.damage,
        knockbackSpeed = move.knockback,
        dirX = pushX,
        dirZ = pushZ,
        hitZone = move.targetHitZone,
        fromGunfire = false,
        fromPlayer = true,
      )
      hitCount++
    }

    for (npc in ejectedNpcs) {
      if (npc.isDead || npc.inWater || npc.fallProgress > 0.4f) continue
      val dx = npc.x - playerX
      val dz = npc.z - playerZ
      val dist = hypot(dx, dz)
      if (dist > reach) continue
      val pushX = if (dist > 0.01f) dx / dist else fwdX
      val pushZ = if (dist > 0.01f) dz / dist else fwdZ
      applyDamageToEjectedNpc(npc, move.damage, move.knockback, pushX, pushZ, move.targetHitZone)
      hitCount++
    }

    if (hitCount > 0) {
      onCombatSfx?.invoke(if (move.targetHitZone == HitZone.HEAD) CombatSfxEvent.HEADSHOT else CombatSfxEvent.HIT)
    }
    alertNearbyPeople(playerX, playerZ, radius = 18f, isGunfire = false)
  }

  private fun fireEquippedGun() {
    val weapon = equippedWeapon
    if (weapon == WeaponType.FISTS || weaponCooldownSec > 0f) return

    isSitting = false
    weaponCooldownSec = weapon.fireIntervalSec
    weaponAimTimerSec = 1.65f
    muzzleFlashTimerSec = 0.08f
    onCombatSfx?.invoke(
      if (weapon == WeaponType.SHOTGUN || weapon == WeaponType.SNIPER_RIFLE || weapon == WeaponType.ASSAULT_RIFLE) {
        CombatSfxEvent.GUN_HEAVY
      } else {
        CombatSfxEvent.GUN_LIGHT
      }
    )

    if (isScoped) {
      playerYawRad = normalizeAngleRad(camYawRad + PI.toFloat())
    } else {
      val lockTarget = findClosestNonFriendNpc(maxDist = weapon.range, requireInFront = true)
      if (lockTarget != null) {
        val dx = lockTarget.first - playerX
        val dz = lockTarget.second - playerZ
        if (hypot(dx, dz) > 0.1f) {
          playerYawRad = angleBetweenXZ(dx, dz)
        }
      }
    }

    val fwdX = sin(playerYawRad)
    val fwdZ = cos(playerYawRad)
    val rightX = cos(playerYawRad)
    val rightZ = -sin(playerYawRad)
    val muzzleX = playerX + fwdX * 0.65f + rightX * 0.24f
    val muzzleY = playerY + (if (isScoped) 1.38f else 1.12f)
    val muzzleZ = playerZ + fwdZ * 0.65f + rightZ * 0.24f

    // Muzzle flash spark
    hitSparks += HitSparkState(muzzleX, muzzleY, muzzleZ, floatArrayOf(1.0f, 0.85f, 0.30f), durationSec = 0.09f)

    // Determine exact bullet elevation angle toward crosshair when scoped, or level-chest height when hip-firing!
    val scopeElevationRad = if (isScoped) (-camPitchRad).coerceIn(-0.42f, 0.38f) else 0.0f
    val spreadMult = if (isScoped) 0.15f else 1.0f

    val count = max(1, weapon.pelletCount)
    for (i in 0 until count) {
      val offsetFactor = if (count == 1) 0f else (i.toFloat() / (count - 1).toFloat()) - 0.5f
      val jitter = sin(worldTimeSec * 43f + i * 2.1f) * 0.25f
      val spreadYaw = (offsetFactor + jitter * 0.35f) * weapon.spreadRad * spreadMult
      val rayYaw = playerYawRad + spreadYaw

      val cosElev = cos(scopeElevationRad)
      val rdx = sin(rayYaw) * cosElev
      val rdy = sin(scopeElevationRad)
      val rdz = cos(rayYaw) * cosElev

      // If firing from shoulder camera while scoped, steer ray from barrel directly through crosshair world point at 40m!
      val targetDist = min(weapon.range, 48f)
      val crossAimX = camPos.x + (sin(playerYawRad) * cosElev) * targetDist
      val crossAimY = camPos.y + sin(scopeElevationRad) * targetDist
      val crossAimZ = camPos.z + (cos(playerYawRad) * cosElev) * targetDist

      val dirX: Float
      val dirY: Float
      val dirZ: Float
      if (isScoped && count == 1) {
        val toCrossX = crossAimX - muzzleX
        val toCrossY = crossAimY - muzzleY
        val toCrossZ = crossAimZ - muzzleZ
        val len = hypot(hypot(toCrossX, toCrossZ), toCrossY).coerceAtLeast(1e-4f)
        dirX = toCrossX / len
        dirY = toCrossY / len
        dirZ = toCrossZ / len
      } else {
        dirX = rdx
        dirY = rdy
        dirZ = rdz
      }

      bulletProjectiles +=
        BulletProjectileState(
          x = muzzleX,
          y = muzzleY,
          z = muzzleZ,
          prevX = muzzleX,
          prevY = muzzleY,
          prevZ = muzzleZ,
          vx = dirX * weapon.bulletSpeed,
          vy = dirY * weapon.bulletSpeed,
          vz = dirZ * weapon.bulletSpeed,
          damage = weapon.damage,
          knockback = if (weapon == WeaponType.SHOTGUN || weapon == WeaponType.SNIPER_RIFLE) 5.8f else 2.8f,
          remainingDist = weapon.range,
        )
    }

    alertNearbyPeople(playerX, playerZ, radius = 38f, isGunfire = true)
  }

  internal fun applyDamageToPedestrian(
    ped: PedestrianRuntimeState,
    damage: Float,
    knockbackSpeed: Float,
    dirX: Float,
    dirZ: Float,
    hitZone: HitZone,
    fromGunfire: Boolean,
    fromPlayer: Boolean,
    attackerBot: PedestrianRuntimeState? = null,
  ) {
    var effectiveDamage = damage
    var armorAbsorbed = false
    if (hitZone == HitZone.HEAD && ped.helmetLevel >= 3 && ped.helmetDurability > 0f) {
      val ratio = if (ped.helmetLevel >= 4) 0.55f else 0.40f
      effectiveDamage = damage * (1f - ratio)
      ped.helmetDurability = max(0f, ped.helmetDurability - damage * 0.55f)
      if (ped.helmetDurability <= 0f) ped.helmetLevel = 0
      armorAbsorbed = true
    } else if (hitZone != HitZone.HEAD && ped.vestLevel >= 3 && ped.vestDurability > 0f) {
      val ratio = if (ped.vestLevel >= 4) 0.58f else 0.42f
      effectiveDamage = damage * (1f - ratio)
      ped.vestDurability = max(0f, ped.vestDurability - damage * 0.60f)
      if (ped.vestDurability <= 0f) ped.vestLevel = 0
      armorAbsorbed = true
    }

    ped.hp = max(0f, ped.hp - effectiveDamage)
    ped.vx = dirX * knockbackSpeed
    ped.vz = dirZ * knockbackSpeed
    ped.hitReactZone = hitZone
    ped.hitReactTimerSec = 0.72f
    ped.hitReactDirSign = if (sin(worldTimeSec * 19f + ped.x) >= 0f) 1f else -1f

    if (fromPlayer && !ped.isFriend) {
      ped.isHostileTarget = true
      ped.targetPlayer = true
    } else if (attackerBot != null && !ped.isFriend && attackerBot.id != ped.id) {
      ped.isHostileTarget = true
      ped.targetEnemyBotId = attackerBot.id
    }

    val sparkY =
      when (hitZone) {
        HitZone.HEAD -> 1.56f
        HitZone.TORSO -> 1.06f
        HitZone.LEGS -> 0.45f
        HitZone.NONE -> 1.15f
      }
    hitSparks +=
      HitSparkState(
        x = ped.x,
        y = sparkY,
        z = ped.z,
        color =
          when {
            armorAbsorbed -> floatArrayOf(0.28f, 0.88f, 1.0f)
            hitZone == HitZone.HEAD -> floatArrayOf(1.0f, 0.16f, 0.22f)
            fromGunfire -> floatArrayOf(1.0f, 0.36f, 0.24f)
            else -> floatArrayOf(1.0f, 0.76f, 0.20f)
          },
      )

    if (fromPlayer) {
      showBanner(
        when {
          ped.hp <= 0f && hitZone == HitZone.HEAD -> "HEADSHOT KO! +$45"
          ped.hp <= 0f -> "KNOCKOUT! +$45"
          armorAbsorbed && hitZone == HitZone.HEAD -> "ARMORED HELMET HIT!"
          armorAbsorbed -> "VEST ARMOR HIT!"
          hitZone == HitZone.HEAD && fromGunfire -> "HEADSHOT CRIT!"
          hitZone == HitZone.HEAD -> "HEAD STRIKE!"
          hitZone == HitZone.LEGS -> "LOW LEG HIT!"
          else -> "BODY HIT!"
        }
      )
    }

    if (ped.hp <= 0f) {
      if (ped.drivingVehicleId != null) {
        botController.releaseBotVehicle(ped)
      }
      ped.reactionState = NpcReactionState.KNOCKED_DOWN
      ped.downedTimerSec = 0f
      ped.respawnTimerSec = if (ped.isRivalFighter) 18.0f else 10.0f
      ped.attackMove = null
      ped.isHostileTarget = false
      ped.targetEnemyBotId = null
      ped.targetPlayer = false
      if (ped.isRivalFighter || ped.weapon != WeaponType.FISTS || ped.botMedkits > 0) {
        dropRivalLoot(ped)
      }
      if (fromPlayer) {
        npcDefeatedCount++
        playerCash += 65
        onEconomyChanged?.invoke()
        wantedLevel = min(5, max(1, wantedLevel + 1))
        wantedDecaySec = 18f
        onCombatSfx?.invoke(CombatSfxEvent.CASH)
      } else if (attackerBot != null) {
        showBanner("${rivalDisplayName(attackerBot)} KO'D ${rivalDisplayName(ped)}")
      }
    } else if (ped.isFriend) {
      ped.reactionState = NpcReactionState.COMPANION
    } else if (ped.isRivalFighter || ped.weapon != WeaponType.FISTS || attackerBot != null) {
      ped.reactionState = NpcReactionState.FIGHTING_BACK
      ped.panicTimerSec = 18f
    } else if (fromGunfire || !ped.isBraveDefender || ped.hp < 35f) {
      ped.reactionState = NpcReactionState.HANDS_UP_FLINCH
      ped.panicTimerSec = 9.5f
      ped.attackMove = null
    } else {
      ped.reactionState = NpcReactionState.FIGHTING_BACK
      ped.panicTimerSec = 10f
    }
  }

  private fun applyDamageToEjectedNpc(
    npc: EjectedNpcState,
    damage: Float,
    knockbackSpeed: Float,
    dirX: Float,
    dirZ: Float,
    hitZone: HitZone,
  ) {
    npc.hp = max(0f, npc.hp - damage)
    npc.vx = dirX * knockbackSpeed
    npc.vz = dirZ * knockbackSpeed
    npc.hitReactZone = hitZone
    npc.hitReactTimerSec = 0.70f
    npc.hitReactDirSign = if (sin(worldTimeSec * 19f + npc.x) >= 0f) 1f else -1f
    val sparkY =
      when (hitZone) {
        HitZone.HEAD -> npc.y + 1.54f
        HitZone.TORSO -> npc.y + 1.05f
        HitZone.LEGS -> npc.y + 0.42f
        HitZone.NONE -> npc.y + 1.0f
      }
    hitSparks += HitSparkState(npc.x, sparkY, npc.z, floatArrayOf(1.0f, 0.24f, 0.28f))
    if (npc.hp <= 0f) {
      npc.isDead = true
      npc.isFleeing = false
      npcDefeatedCount++
      playerCash += 45
      wantedLevel = min(5, max(1, wantedLevel + 1))
      wantedDecaySec = 18f
      onCombatSfx?.invoke(CombatSfxEvent.CASH)
      showBanner(if (hitZone == HitZone.HEAD) "HEADSHOT KO! +$45" else "KNOCKOUT! +$45")
    } else {
      npc.isFleeing = true
      showBanner(if (hitZone == HitZone.HEAD) "HEAD HIT!" else "BODY HIT!")
    }
  }

  fun alertNearbyPeople(originX: Float, originZ: Float, radius: Float, isGunfire: Boolean) {
    for (ped in pedestrians) {
      if (ped.reactionState == NpcReactionState.KNOCKED_DOWN || ped.isFriend) continue
      val d = CollisionSolver.distXZ(ped.x, ped.z, originX, originZ)
      if (d > radius) continue

      if (ped.isRivalFighter) {
        ped.reactionState = NpcReactionState.FIGHTING_BACK
        ped.panicTimerSec = 18f
        ped.isHostileTarget = true
        continue
      }

      if (isGunfire) {
        if (ped.reactionState == NpcReactionState.STROLLING || ped.reactionState == NpcReactionState.FIGHTING_BACK) {
          ped.reactionState = NpcReactionState.HANDS_UP_FLINCH
        }
        ped.panicTimerSec = 10.5f
        ped.attackMove = null
      } else {
        if (ped.isBraveDefender && d < 10f && ped.hp >= 35f) {
          ped.reactionState = NpcReactionState.FIGHTING_BACK
          ped.panicTimerSec = 10f
        } else if (ped.reactionState == NpcReactionState.STROLLING) {
          ped.reactionState = NpcReactionState.HANDS_UP_FLINCH
          ped.panicTimerSec = 8.0f
        } else if (ped.reactionState == NpcReactionState.FLEEING) {
          ped.panicTimerSec = max(ped.panicTimerSec, 8.0f)
        }
      }
    }

    for (npc in ejectedNpcs) {
      if (npc.isDead || npc.inWater) continue
      val d = CollisionSolver.distXZ(npc.x, npc.z, originX, originZ)
      if (d <= radius) {
        npc.isFleeing = true
      }
    }
  }

  private fun findClosestNonFriendNpc(maxDist: Float, requireInFront: Boolean): Pair<Float, Float>? {
    val fwdX = sin(playerYawRad)
    val fwdZ = cos(playerYawRad)
    var bestScore = maxDist
    var bestPos: Pair<Float, Float>? = null

    for (ped in pedestrians) {
      if (ped.reactionState == NpcReactionState.KNOCKED_DOWN || ped.isFriend) continue
      val dx = ped.x - playerX
      val dz = ped.z - playerZ
      val d = hypot(dx, dz)
      if (d > maxDist) continue
      if (requireInFront && d > 0.2f) {
        val dot = (dx / d) * fwdX + (dz / d) * fwdZ
        if (dot < 0.72f) continue
      }
      if (d < bestScore) {
        bestScore = d
        bestPos = Pair(ped.x, ped.z)
      }
    }

    for (npc in ejectedNpcs) {
      if (npc.isDead || npc.inWater || npc.fallProgress > 0.5f) continue
      val dx = npc.x - playerX
      val dz = npc.z - playerZ
      val d = hypot(dx, dz)
      if (d > maxDist) continue
      if (requireInFront && d > 0.2f) {
        val dot = (dx / d) * fwdX + (dz / d) * fwdZ
        if (dot < 0.72f) continue
      }
      if (d < bestScore) {
        bestScore = d
        bestPos = Pair(npc.x, npc.z)
      }
    }

    return bestPos
  }

  private fun stepCarjackSequence(seq: CarjackSequenceState, dt: Float) {
    seq.elapsedSec += dt
    val t = (seq.elapsedSec / seq.durationSec).coerceIn(0f, 1f)
    nearbyVehicle = null
    nearbyBoat = null
    isSitting = false
    isScoped = false
    sitProgress = 0f

    val targetCar = seq.vehicleId?.let { id -> vehicles.firstOrNull { it.id == id } }
    val targetBoat = seq.boatId?.let { id -> boats.firstOrNull { it.id == id } }
    val ejected = ejectedNpcs.firstOrNull { it.id == seq.npcId }

    if (targetCar != null) {
      targetCar.forwardSpeed *= 0.8f
      targetCar.isBraking = true

      val leftX = -cos(targetCar.yawRad)
      val leftZ = sin(targetCar.yawRad)
      val doorX = targetCar.x + leftX * 1.48f
      val doorZ = targetCar.z + leftZ * 1.48f
      val seatX = targetCar.x + leftX * 0.34f
      val seatZ = targetCar.z + leftZ * 0.34f

      when {
        t < 0.32f -> {
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
          val p = (t - 0.32f) / 0.40f
          playerX = doorX
          playerZ = doorZ
          playerY = computeGroundHeight(playerX, playerZ)
          playerYawRad = angleBetweenXZ(leftX, leftZ)
          playerArmReachNorm = 1.0f - p * 0.3f
          playerSpeedNorm = 0.3f

          if (ejected != null) {
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
        alertNearbyPeople(targetCar.x, targetCar.z, radius = 14f, isGunfire = false)
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
      if (npc.hitReactTimerSec > 0f) {
        npc.hitReactTimerSec = max(0f, npc.hitReactTimerSec - dt)
        if (npc.hitReactTimerSec <= 0f) npc.hitReactZone = HitZone.NONE
      }

      if (npc.ageSec < 0.72f) continue

      if (npc.inWater) {
        npc.x += npc.vx * dt
        npc.z += npc.vz * dt
        npc.vx *= 0.92f
        npc.vz *= 0.92f
        npc.y = -0.78f + sin(worldTimeSec * 2.4f + npc.x) * 0.04f
        npc.fallProgress = 0.2f
        npc.walkCycle += dt * 4f
        if (npc.ageSec > 12f) iter.remove()
        continue
      }

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
      } else {
        npc.x += npc.vx * dt
        npc.z += npc.vz * dt
      }
      npc.vx *= 0.85f
      npc.vz *= 0.85f

      if (npc.isDead) {
        npc.fallProgress = min(1f, npc.fallProgress + 4.5f * dt)
        npc.downedAgeSec += dt
        if (npc.downedAgeSec > 4.5f) iter.remove()
        continue
      }

      when {
        npc.ageSec < 3.4f -> npc.fallProgress = 1.0f
        npc.ageSec < 4.3f -> {
          val standT = (npc.ageSec - 3.4f) / 0.9f
          npc.fallProgress = (1.0f - standT).coerceIn(0f, 1f)
        }
        else -> {
          npc.fallProgress = 0f
          if (npc.isFleeing) {
            val awayX = npc.x - playerX
            val awayZ = npc.z - playerZ
            val awayDist = hypot(awayX, awayZ).coerceAtLeast(0.01f)
            val dx = awayX / awayDist
            val dz = awayZ / awayDist
            npc.yawRad = lerpAngleRad(npc.yawRad, angleBetweenXZ(dx, dz), (dt * 12f).coerceAtMost(1f))
            val nx = npc.x + dx * 5.6f * dt
            val nz = npc.z + dz * 5.6f * dt
            val (rx, rz, _) = CollisionSolver.resolveAll(nx, nz, 0.42f, world.aabbColliders, world.circleColliders)
            val clamped = CollisionSolver.clampCarToIsland(rx, rz, margin = 2.0f)
            npc.x = clamped.first
            npc.z = clamped.second
            npc.walkCycle += dt * 13.5f
          } else {
            val fwdX = sin(npc.yawRad)
            val fwdZ = cos(npc.yawRad)
            npc.x += fwdX * 1.9f * dt
            npc.z += fwdZ * 1.9f * dt
            npc.walkCycle += dt * 7.5f
          }
        }
      }

      if (npc.ageSec > 14f) iter.remove()
    }
  }

  private fun stepAiRoadTraffic(dt: Float) {
    for (v in vehicles) {
      if (!v.isAiTraffic || activeVehicle?.id == v.id) continue
      if (activeCarjack?.vehicleId == v.id) continue

      val loop =
        when {
          v.usesTownLoop -> world.roadLoopWaypointsTown
          v.usesUptownLoop -> world.roadLoopWaypointsUptown
          v.usesInnerLoop -> world.roadLoopWaypointsInner
          else -> world.roadLoopWaypointsOuter
        }
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

      if (isPlaying && dropPhase == DropPhase.GROUND && activeVehicle == null && activeBoat == null) {
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
      v.y = computeGroundHeight(v.x, v.z, v.y + 0.45f)
      val (slopePitch, _) = TerrainHeightfield.computeSlopeAngles(v.x, v.z, v.yawRad)
      v.pitchRad += (slopePitch - v.pitchRad) * (dt * 8f).coerceAtMost(1f)
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
      val nextX = b.routeCenterX + cos(b.routeAngle) * b.routeRadiusX
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
    botController.stepAllBots(dt)
  }

  internal fun stepCompanion(friend: PedestrianRuntimeState, dt: Float) {
    friend.reactionState = NpcReactionState.COMPANION
    friend.armsUpNorm = 0f
    friend.fallProgress = 0f
    friend.hp = min(friend.maxHp, friend.hp + 4.0f * dt)

    // While player drives/sails, companion rides smoothly alongside in passenger seat
    if (activeVehicle != null || activeBoat != null) {
      val rightX = cos(playerYawRad)
      val rightZ = -sin(playerYawRad)
      friend.x = playerX + rightX * (1.8f + friend.friendSlot * 0.4f)
      friend.z = playerZ + rightZ * (1.8f + friend.friendSlot * 0.4f)
      friend.yawRad = playerYawRad
      friend.attackMove = null
      friend.sitProgress = 0f
      return
    }

    // 1. Check for nearby hostile NPCs attacking player or engaged by player!
    var targetEnemy: PedestrianRuntimeState? = null
    var bestEnemyDist = 20f
    for (other in pedestrians) {
      if (other.id == friend.id || other.isFriend || other.reactionState == NpcReactionState.KNOCKED_DOWN) {
        continue
      }
      if (other.reactionState == NpcReactionState.FIGHTING_BACK || other.isHostileTarget) {
        val d = CollisionSolver.distXZ(friend.x, friend.z, other.x, other.z)
        if (d < bestEnemyDist) {
          bestEnemyDist = d
          targetEnemy = other
        }
      }
    }

    if (targetEnemy != null) {
      friend.sitProgress = max(0f, friend.sitProgress - 6f * dt)
      val dx = targetEnemy.x - friend.x
      val dz = targetEnemy.z - friend.z
      val dist = hypot(dx, dz).coerceAtLeast(0.05f)
      val nx = dx / dist
      val nz = dz / dist
      friend.yawRad = lerpAngleRad(friend.yawRad, angleBetweenXZ(nx, nz), (dt * 12f).coerceAtMost(1f))

      if (friend.attackMove != null) {
        friend.attackProgress += dt / friend.attackMove!!.durationSec
        if (friend.attackProgress >= 0.42f && friend.attackCooldownSec <= 0f && dist < 2.2f) {
          val move = friend.attackMove!!
          applyDamageToPedestrian(
            ped = targetEnemy,
            damage = 28f,
            knockbackSpeed = 4.6f,
            dirX = nx,
            dirZ = nz,
            hitZone = move.targetHitZone,
            fromGunfire = false,
            fromPlayer = false,
          )
          friend.attackCooldownSec = 0.65f
          onCombatSfx?.invoke(CombatSfxEvent.HIT)
        }
        if (friend.attackProgress >= 1f) {
          friend.attackMove = null
          friend.attackProgress = 0f
        }
      } else if (dist > 1.45f) {
        val chaseSpeed = 6.2f
        val nextX = friend.x + nx * chaseSpeed * dt
        val nextZ = friend.z + nz * chaseSpeed * dt
        val (rx, rz, _) =
          CollisionSolver.resolveAll(nextX, nextZ, 0.40f, world.aabbColliders, world.circleColliders)
        val clamped = CollisionSolver.clampCarToIsland(rx, rz, margin = 2.0f)
        friend.x = clamped.first
        friend.z = clamped.second
        friend.walkCycle += dt * 13.0f
      } else if (friend.attackCooldownSec <= 0f) {
        friend.attackMove =
          if (friend.friendSlot % 2 == 0) CombatMoveType.PUNCH_HOOK else CombatMoveType.KICK_ROUNDHOUSE
        friend.attackProgress = 0f
      }
      return
    }

    friend.attackMove = null

    // 2. Follow player closely in formation & sit together when chilling!
    val angleOffset =
      when (friend.friendSlot) {
        0 -> -2.2f // back-left
        1 -> 2.2f // back-right
        else -> 3.14f // right behind
      }
    val followAngle = playerYawRad + angleOffset
    val goalX = playerX + sin(followAngle) * 2.0f
    val goalZ = playerZ + cos(followAngle) * 2.0f

    val dx = goalX - friend.x
    val dz = goalZ - friend.z
    val distToGoal = hypot(dx, dz)

    if (distToGoal > 0.45f) {
      friend.sitProgress = max(0f, friend.sitProgress - 6f * dt)
      val followSpeed =
        when {
          distToGoal > 14f -> 10.5f // teleport/sprint catchup
          distToGoal > 4.0f -> 7.6f
          else -> 4.8f
        }
      if (distToGoal > 28f) {
        friend.x = goalX
        friend.z = goalZ
      } else {
        val nx = dx / distToGoal
        val nz = dz / distToGoal
        friend.yawRad = lerpAngleRad(friend.yawRad, angleBetweenXZ(nx, nz), (dt * 11f).coerceAtMost(1f))
        val stepMove = min(distToGoal, followSpeed * dt)
        val nextX = friend.x + nx * stepMove
        val nextZ = friend.z + nz * stepMove
        val (rx, rz, _) =
          CollisionSolver.resolveAll(
            nextX,
            nextZ,
            0.38f,
            world.aabbColliders,
            world.circleColliders,
            entityMinY = friend.y,
            entityMaxY = friend.y + 1.75f,
          )
        val clamped = CollisionSolver.clampSwimmerToWorld(rx, rz)
        friend.x = clamped.first
        friend.z = clamped.second
        friend.walkCycle += dt * (followSpeed * 1.9f)
      }
    } else {
      // Arrived beside player — face same way and mirror sitting pose!
      friend.yawRad = lerpAngleRad(friend.yawRad, playerYawRad, (dt * 6f).coerceAtMost(1f))
      friend.sitProgress =
        if (isSitting) min(1f, friend.sitProgress + 5.0f * dt)
        else max(0f, friend.sitProgress - 6.0f * dt)
    }
  }

  @Synchronized
  fun triggerParkour(): Boolean {
    if (!isPlaying || isPaused || isGunShopOpen || isStyleShopOpen || isPlayerEliminated || dropPhase != DropPhase.GROUND) {
      return false
    }
    if (activeVehicle != null || activeBoat != null || activeCarjack != null || activeParkour != null || isSwimming) {
      return false
    }
    val candidate = nearbyParkourCandidate ?: scanParkourCandidate(preferWideArc = true) ?: return false
    startParkour(candidate)
    return true
  }

  private fun startParkour(seq: ParkourSequenceState) {
    isSitting = false
    sitProgress = 0f
    isScoped = false
    activeAttackMove = null
    attackTimerSec = 0f
    activeLandingReaction = null
    landingReactionTimerSec = 0f
    airborneFallNorm = 0f
    airborneLaunchVx = 0f
    airborneLaunchVz = 0f
    activeParkour = seq
    nearbyParkourCandidate = null
    nearbyParkourLabel = null
    playerYawRad = seq.targetYawRad
    playerVelY = 0f
    onCombatSfx?.invoke(CombatSfxEvent.CLIMB)
  }

  private fun stepParkourSequence(seq: ParkourSequenceState, dt: Float) {
    input.jumpQueued = false
    input.climbQueued = false
    seq.elapsedSec += dt
    val p = (seq.elapsedSec / seq.durationSec).coerceIn(0f, 1f)
    playerYawRad = lerpAngleRad(playerYawRad, seq.targetYawRad, (dt * 16f).coerceAtMost(1f))
    playerSpeedNorm = 0.4f

    if (seq.type == ParkourActionType.CLIMB_LEDGE) {
      // Phase 1 (0..0.42): Pull vertically to grip/hang with chest near rim; Phase 2 (0.42..1.0): Pull & step up onto top
      if (p <= 0.42f) {
        val t = (p / 0.42f).coerceIn(0f, 1f)
        val ease = t * (2f - t)
        playerX = seq.startX + (seq.midX - seq.startX) * ease
        playerY = seq.startY + (seq.midY - seq.startY) * ease
        playerZ = seq.startZ + (seq.midZ - seq.startZ) * ease
      } else {
        val t = ((p - 0.42f) / 0.58f).coerceIn(0f, 1f)
        val ease = t * t * (3f - 2f * t)
        playerX = seq.midX + (seq.targetX - seq.midX) * ease
        playerY = seq.midY + (seq.targetY - seq.midY) * ease
        playerZ = seq.midZ + (seq.targetZ - seq.midZ) * ease
      }
    } else {
      // VAULT_OVER: clean quadratic Bezier over mid apex onto far side
      val oneMinus = 1f - p
      playerX = oneMinus * oneMinus * seq.startX + 2f * oneMinus * p * seq.midX + p * p * seq.targetX
      playerY = oneMinus * oneMinus * seq.startY + 2f * oneMinus * p * seq.midY + p * p * seq.targetY
      playerZ = oneMinus * oneMinus * seq.startZ + 2f * oneMinus * p * seq.midZ + p * p * seq.targetZ
    }

    if (p >= 1f) {
      playerX = seq.targetX
      playerY = seq.targetY
      playerZ = seq.targetZ
      activeParkour = null
      if (seq.dropOffAfterVault) {
        // Vaulted off a rooftop parapet / 2F balcony railing -> carry forward momentum into freefall!
        isGrounded = false
        playerVelY = -1.6f
        airborneLaunchVx = seq.launchVelX
        airborneLaunchVz = seq.launchVelZ
        airPeakY = max(seq.startY, seq.midY - 0.1f)
        airStartY = seq.startY
      } else {
        isGrounded = true
        playerVelY = 0f
        airPeakY = playerY
        airStartY = playerY
      }
    }
  }

  internal fun isPointBlockedBySolidAabb(
    px: Float,
    pz: Float,
    minCheckY: Float,
    maxCheckY: Float,
    radius: Float = 0.34f,
    ignoreBox: AabbCollider? = null,
  ): Boolean {
    for (box in world.aabbColliders) {
      if (box === ignoreBox) continue
      if (box.maxY <= minCheckY || box.minY >= maxCheckY) continue
      if (CollisionSolver.resolveCircleVsAabb(px, pz, radius, box) != null) return true
    }
    return false
  }

  private fun scanParkourCandidate(preferWideArc: Boolean = false): ParkourSequenceState? {
    val mx = input.moveX.coerceIn(-1f, 1f)
    val my = input.moveY.coerceIn(-1f, 1f)
    val mag = hypot(mx, my)
    val dirX: Float
    val dirZ: Float
    if (mag > 0.14f) {
      val fwdX = -sin(camYawRad)
      val fwdZ = -cos(camYawRad)
      val rightX = cos(camYawRad)
      val rightZ = -sin(camYawRad)
      val wx = rightX * mx + fwdX * (-my)
      val wz = rightZ * mx + fwdZ * (-my)
      val len = hypot(wx, wz).coerceAtLeast(1e-5f)
      dirX = wx / len
      dirZ = wz / len
    } else {
      dirX = sin(playerYawRad)
      dirZ = cos(playerYawRad)
    }
    val targetYaw = angleBetweenXZ(dirX, dirZ)

    var bestCandidate: ParkourSequenceState? = null
    var bestScore = Float.MAX_VALUE

    // 1. Scan Thin Barriers / Railings / Rooftop Parapets / Yard & Enclave Walls for VAULT_OVER
    for (box in world.aabbColliders) {
      val topDelta = box.maxY - playerY
      if (topDelta < 0.38f || topDelta > 2.35f) continue
      if (box.minY > playerY + 1.32f) continue

      val cx = playerX.coerceIn(box.minX, box.maxX)
      val cz = playerZ.coerceIn(box.minZ, box.maxZ)
      val dx = cx - playerX
      val dz = cz - playerZ
      val dist = hypot(dx, dz)
      val maxDetectDist = if (preferWideArc) 1.65f else 1.45f
      if (dist > maxDetectDist) continue

      val forwardDot = if (dist > 0.03f) (dx * dirX + dz * dirZ) / dist else 1f
      val minDot = if (dist < 0.65f) 0.12f else 0.42f
      if (forwardDot < minDot) continue

      // Ray-march across box along probe direction to check thickness & exit face
      var exitStep = -1f
      for (stepIdx in 1..10) {
        val s = stepIdx * 0.18f
        val sx = cx + dirX * s
        val sz = cz + dirZ * s
        if (sx !in box.minX..box.maxX || sz !in box.minZ..box.maxZ) {
          exitStep = s
          break
        }
      }
      if (exitStep < 0f || exitStep > 1.45f) continue

      val farX = cx + dirX * (exitStep + 0.78f)
      val farZ = cz + dirZ * (exitStep + 0.78f)
      // Must not vault straight into a wall behind the railing/fence
      if (isPointBlockedBySolidAabb(farX, farZ, box.maxY - 0.15f, box.maxY + 1.45f, 0.32f, ignoreBox = box)) {
        continue
      }

      val farFloorY = computeGroundHeight(farX, farZ, box.maxY + 0.35f)
      val dropOff = farFloorY < playerY - 1.55f
      if (!dropOff && isPointBlockedBySolidAabb(farX, farZ, farFloorY + 0.40f, farFloorY + 1.68f, 0.32f, ignoreBox = box)) {
        continue
      }

      val midX = cx + dirX * (exitStep * 0.5f)
      val midZ = cz + dirZ * (exitStep * 0.5f)
      val midY = box.maxY + 0.22f
      val endY = if (dropOff) max(farFloorY, playerY - 0.35f) else farFloorY
      val tagLower = box.tag.lowercase()
      val label =
        when {
          dropOff -> "VAULT OFF"
          tagLower.contains("wall") || tagLower.contains("gate") -> "VAULT WALL"
          tagLower.contains("net") -> "VAULT NET"
          else -> "VAULT"
        }
      val score = dist + abs(topDelta - 0.9f) * 0.25f
      if (score < bestScore) {
        bestScore = score
        bestCandidate =
          ParkourSequenceState(
            type = ParkourActionType.VAULT_OVER,
            label = label,
            startX = playerX,
            startY = playerY,
            startZ = playerZ,
            midX = midX,
            midY = midY,
            midZ = midZ,
            targetX = farX,
            targetY = endY,
            targetZ = farZ,
            targetYawRad = targetYaw,
            dropOffAfterVault = dropOff,
            launchVelX = if (dropOff) dirX * 4.8f else 0f,
            launchVelZ = if (dropOff) dirZ * 4.8f else 0f,
            durationSec = if (dropOff) 0.44f else 0.48f,
          )
      }
    }

    // 2. Scan Elevated Walkable Surfaces, Wide Solid Props & Parked Cars for CLIMB_LEDGE
    val maxClimbReach = if (!isGrounded) 3.10f else 2.95f
    val probeDists = listOf(0.78f, 1.18f, 1.58f)
    for (pd in probeDists) {
      val px = playerX + dirX * pd
      val pz = playerZ + dirZ * pd

      // 2A. WalkableFloors (2F balconies, porches, crates, gun shop roof, clubhouse, gatehouse, rooftops)
      for (surface in world.walkableFloors) {
        if (px !in surface.minX..surface.maxX || pz !in surface.minZ..surface.maxZ) continue
        val elev = surface.elevationAt(px, pz)
        val h = elev - playerY
        if (h < 0.44f || h > maxClimbReach) continue
        if (surface.tag.contains("Stairs", ignoreCase = true) && h < 0.72f) continue

        // Step slightly past any outer edge parapet/railing if one sits right along rim
        var tx = px
        var tz = pz
        if (isPointBlockedBySolidAabb(tx, tz, elev + 0.40f, elev + 1.65f, 0.34f)) {
          val nudgeX = playerX + dirX * (pd + 0.62f)
          val nudgeZ = playerZ + dirZ * (pd + 0.62f)
          if (nudgeX in surface.minX..surface.maxX &&
            nudgeZ in surface.minZ..surface.maxZ &&
            !isPointBlockedBySolidAabb(nudgeX, nudgeZ, elev + 0.40f, elev + 1.65f, 0.32f)
          ) {
            tx = nudgeX
            tz = nudgeZ
          } else {
            continue
          }
        }
        // Make sure there's no floor slab blocking right above player's start before grabbing rim
        val hangX = tx - dirX * 0.42f
        val hangZ = tz - dirZ * 0.42f
        val label =
          when {
            surface.tag.contains("Crate", true) -> "CLIMB CRATE"
            surface.tag.contains("Counter", true) -> "CLIMB COUNTER"
            surface.tag.contains("Roof", true) -> "CLIMB ROOF"
            surface.tag.contains("Balcony", true) -> "CLIMB BALCONY"
            else -> "CLIMB LEDGE"
          }
        val score = pd * 0.85f + h * 0.25f + (if (bestCandidate?.type == ParkourActionType.VAULT_OVER) 0.45f else 0f)
        if (score < bestScore) {
          bestScore = score
          bestCandidate =
            ParkourSequenceState(
              type = ParkourActionType.CLIMB_LEDGE,
              label = label,
              startX = playerX,
              startY = playerY,
              startZ = playerZ,
              midX = (playerX + hangX) * 0.5f + dirX * 0.18f,
              midY = max(playerY + 0.3f, elev - 0.68f),
              midZ = (playerZ + hangZ) * 0.5f + dirZ * 0.18f,
              targetX = tx,
              targetY = elev,
              targetZ = tz,
              targetYawRad = targetYaw,
              durationSec = (0.44f + h * 0.10f).coerceIn(0.46f, 0.72f),
            )
        }
      }

      // 2B. Wide AabbColliders (Tables, Sofas, Enclave Gate Piers, Water Tanks, etc.)
      for (box in world.aabbColliders) {
        val bw = box.maxX - box.minX
        val bd = box.maxZ - box.minZ
        if (bw < 0.65f || bd < 0.65f) continue
        if (px !in box.minX..box.maxX || pz !in box.minZ..box.maxZ) continue
        val h = box.maxY - playerY
        if (h < 0.44f || h > maxClimbReach) continue
        // Avoid full house exterior walls (use balconies/roof floors instead)
        if (box.maxY > 4.8f && world.houses.any { it.name == box.tag }) continue

        val tx = px.coerceIn(box.minX + 0.24f, box.maxX - 0.24f)
        val tz = pz.coerceIn(box.minZ + 0.24f, box.maxZ - 0.24f)
        if (isPointBlockedBySolidAabb(tx, tz, box.maxY + 0.40f, box.maxY + 1.65f, 0.32f, ignoreBox = box)) {
          continue
        }
        val tagLower = box.tag.lowercase()
        val label =
          when {
            tagLower.contains("crate") -> "CLIMB CRATE"
            tagLower.contains("tank") -> "CLIMB TANK"
            tagLower.contains("table") || tagLower.contains("sofa") -> "CLIMB UP"
            else -> "CLIMB LEDGE"
          }
        val score = pd * 0.90f + h * 0.25f + (if (bestCandidate?.type == ParkourActionType.VAULT_OVER) 0.45f else 0f)
        if (score < bestScore) {
          bestScore = score
          bestCandidate =
            ParkourSequenceState(
              type = ParkourActionType.CLIMB_LEDGE,
              label = label,
              startX = playerX,
              startY = playerY,
              startZ = playerZ,
              midX = (playerX + tx) * 0.5f,
              midY = max(playerY + 0.28f, box.maxY - 0.62f),
              midZ = (playerZ + tz) * 0.5f,
              targetX = tx,
              targetY = box.maxY,
              targetZ = tz,
              targetYawRad = targetYaw,
              durationSec = (0.44f + h * 0.10f).coerceIn(0.46f, 0.72f),
            )
        }
      }

      // 2C. Parked / Slow Sports Cars -> Climb onto hood or cabin roof!
      if (playerY < 1.8f) {
        for (car in vehicles) {
          if (abs(car.forwardSpeed) > 1.8f) continue
          val dx = px - car.x
          val dz = pz - car.z
          val c = cos(car.yawRad)
          val s = sin(car.yawRad)
          val lx = dx * c - dz * s
          val lz = dx * s + dz * c
          if (abs(lx) <= 0.95f && abs(lz) <= 2.10f) {
            val carElev = if (abs(lz) <= 0.85f) 1.42f else 0.96f
            val h = carElev - playerY
            if (h in 0.42f..2.15f) {
              val score = pd * 0.95f + h * 0.20f
              if (score < bestScore) {
                bestScore = score
                bestCandidate =
                  ParkourSequenceState(
                    type = ParkourActionType.CLIMB_LEDGE,
                    label = "CLIMB CAR",
                    startX = playerX,
                    startY = playerY,
                    startZ = playerZ,
                    midX = (playerX + px) * 0.5f,
                    midY = max(playerY + 0.25f, carElev - 0.35f),
                    midZ = (playerZ + pz) * 0.5f,
                    targetX = px,
                    targetY = carElev,
                    targetZ = pz,
                    targetYawRad = targetYaw,
                    durationSec = 0.50f,
                  )
              }
            }
          }
        }
      }
    }

    return bestCandidate
  }

  private fun applyLandingImpact(dropDist: Float, touchY: Float) {
    airborneFallNorm = 0f
    airborneLaunchVx = 0f
    airborneLaunchVz = 0f
    airPeakY = touchY
    airStartY = touchY

    when {
      dropDist >= 4.6f -> {
        val dmg = ((dropDist - 4.3f) * 9.0f).coerceIn(14f, 72f)
        applyIncomingDamageToPlayer(
          rawDamage = dmg,
          hitZone = HitZone.LEGS,
          attackerId = null,
          attackerName = "ROOFTOP FALL",
          attackerWeapon = WeaponType.FISTS,
        )
        activeLandingReaction = LandingReactionType.HARD_STAGGER
        landingReactionDurationSec = 0.86f
        landingReactionTimerSec = 0.86f
        camLandingDipY = -0.68f
        camImpactShake = 0.42f
        onCombatSfx?.invoke(CombatSfxEvent.LAND_HEAVY)
        showBanner("HARD ROOFTOP FALL · -${dmg.toInt()} HP!")

        // Nearby pedestrians react with shock to heavy roof crash
        for (ped in pedestrians) {
          if (ped.isFriend || ped.reactionState == NpcReactionState.KNOCKED_DOWN) continue
          if (CollisionSolver.distXZ(playerX, playerZ, ped.x, ped.z) < 9.5f && abs(ped.y - playerY) < 2.5f) {
            ped.reactionState = NpcReactionState.HANDS_UP_FLINCH
            ped.panicTimerSec = max(ped.panicTimerSec, 2.0f)
          }
        }
      }
      dropDist >= 2.4f -> {
        activeLandingReaction = LandingReactionType.COMBAT_ROLL
        landingReactionDurationSec = 0.56f
        landingReactionTimerSec = 0.56f
        camLandingDipY = -0.38f
        camImpactShake = 0.14f
        onCombatSfx?.invoke(CombatSfxEvent.LAND_ROLL)
        showBanner("COMBAT ROLL LANDING!")
      }
      dropDist >= 1.15f -> {
        activeLandingReaction = LandingReactionType.SOFT_LAND
        landingReactionDurationSec = 0.22f
        landingReactionTimerSec = 0.22f
        camLandingDipY = -0.14f
      }
    }
  }

  private fun stepOnFoot(dt: Float) {
    playerArmReachNorm = 0f
    val rawMx = input.moveX.coerceIn(-1f, 1f)
    val rawMy = input.moveY.coerceIn(-1f, 1f)
    val rawMag = hypot(rawMx, rawMy).coerceAtMost(1f)
    val autoSprintActive = input.autoSprint && !isSitting && !isScoped && activeAttackMove == null
    val mx: Float
    val my: Float
    val mag: Float
    if (autoSprintActive && rawMag < 0.14f) {
      // One-tap Auto-Sprint runs straight ahead along camera heading without holding the joystick
      mx = 0f
      my = -1f
      mag = 1f
    } else {
      mx = rawMx
      my = rawMy
      mag = rawMag
    }

    // Deep water only applies near ocean level (not while skydiving/on upper balconies)
    val inWater = playerY < 1.0f && CollisionSolver.isInDeepWater(playerX, playerZ)
    isSwimming = inWater

    if (inWater || mag > 0.14f || input.jumpQueued || input.climbQueued || input.punchQueued || input.kickQueued) {
      isSitting = false
      if (inWater) isScoped = false
    }
    if (input.fireHeld || input.fireQueued) {
      isSitting = false
    }

    sitProgress =
      if (isSitting) min(1f, sitProgress + 5.5f * dt)
      else max(0f, sitProgress - 6.5f * dt)

    if (input.punchQueued) {
      input.punchQueued = false
      if (!inWater) startMeleeMove(isKick = false)
    }
    if (input.kickQueued) {
      input.kickQueued = false
      if (!inWater) startMeleeMove(isKick = true)
    }

    val currentMove = activeAttackMove
    if (currentMove != null) {
      attackTimerSec += dt
      if (attackProgress >= 0.40f && !hasAppliedAttackHit) {
        hasAppliedAttackHit = true
        resolveMeleeStrikeHit(currentMove)
      }
      if (attackTimerSec >= currentMove.durationSec) {
        activeAttackMove = null
        attackTimerSec = 0f
      }
    }

    if (input.fireQueued || (input.fireHeld && equippedWeapon != WeaponType.FISTS)) {
      input.fireQueued = false
      if (!inWater) fireEquippedGun()
    }

    // Update nearby parkour prompt & check climb trigger
    val parkourCand = if (!inWater) scanParkourCandidate(preferWideArc = false) else null
    nearbyParkourCandidate = parkourCand
    nearbyParkourLabel = parkourCand?.label

    if (input.climbQueued) {
      input.climbQueued = false
      if (!inWater && parkourCand != null) {
        startParkour(parkourCand)
        return
      }
    }

    val isRolling = activeLandingReaction == LandingReactionType.COMBAT_ROLL && landingReactionTimerSec > 0.08f
    val isStaggered = activeLandingReaction == LandingReactionType.HARD_STAGGER && landingReactionTimerSec > 0.30f
    val sprinting = (input.sprintHeld || input.autoSprint) && mag > 0.2f && !isSitting && !isScoped && activeAttackMove == null && !isStaggered
    val targetSpeed =
      when {
        isSitting || sitProgress > 0.4f -> 0f
        isStaggered -> 1.1f
        inWater -> if (sprinting) 5.2f else 3.4f
        isScoped -> 2.8f
        sprinting -> 9.8f
        else -> 4.9f
      }
    val speed = mag * targetSpeed

    if (mag > 0.08f && !isSitting) {
      val fwdX = -sin(camYawRad)
      val fwdZ = -cos(camYawRad)
      val rightX = cos(camYawRad)
      val rightZ = -sin(camYawRad)

      val wishX = rightX * mx + fwdX * (-my)
      val wishZ = rightZ * mx + fwdZ * (-my)
      val wishLen = hypot(wishX, wishZ).coerceAtLeast(1e-5f)
      val dirX = wishX / wishLen
      val dirZ = wishZ / wishLen

      if (!isScoped) {
        val targetYaw = angleBetweenXZ(dirX, dirZ)
        playerYawRad = lerpAngleRad(playerYawRad, targetYaw, (dt * 12f).coerceAtMost(1f))
      }

      playerX += dirX * speed * dt
      playerZ += dirZ * speed * dt
      playerWalkCycle += dt * (if (sprinting) 13.5f else 8.2f) * mag
      playerSpeedNorm = if (sprinting) 1f else 0.55f * mag
    } else {
      playerSpeedNorm = (playerSpeedNorm - dt * 6f).coerceAtLeast(0f)
    }

    if (isRolling) {
      playerX += sin(playerYawRad) * 3.8f * dt
      playerZ += cos(playerYawRad) * 3.8f * dt
      playerSpeedNorm = max(playerSpeedNorm, 0.55f)
    }

    if (input.jumpQueued) {
      input.jumpQueued = false
      val jumpParkour =
        if (!inWater && (mag > 0.14f || !isGrounded)) {
          parkourCand?.takeIf {
            it.type == ParkourActionType.VAULT_OVER ||
              it.targetY - playerY in 0.45f..2.98f
          }
        } else null

      if (jumpParkour != null) {
        startParkour(jumpParkour)
        return
      } else if (isGrounded && !inWater && !isStaggered) {
        playerVelY = 7.7f
        isGrounded = false
        airPeakY = playerY
        airStartY = playerY
        activeLandingReaction = null
        landingReactionTimerSec = 0f
        if (mag > 0.14f) {
          airborneLaunchVx = sin(playerYawRad) * 2.4f
          airborneLaunchVz = cos(playerYawRad) * 2.4f
        }
      }
    }

    // Mid-air automatic ledge grab ("hath se pakad ker chadna") when jumping forward toward an elevated edge
    if (!isGrounded && !inWater && mag > 0.22f && playerVelY > -4.5f && parkourCand != null) {
      val climbingHigher = parkourCand.type == ParkourActionType.CLIMB_LEDGE &&
        parkourCand.targetY > airStartY + 0.65f &&
        (parkourCand.targetY - playerY) in 0.35f..2.70f
      val catchingBarrier = parkourCand.type == ParkourActionType.VAULT_OVER && !parkourCand.dropOffAfterVault
      if (climbingHigher || catchingBarrier) {
        startParkour(parkourCand)
        return
      }
    }

    if (inWater) {
      val swimY = -0.78f + sin(worldTimeSec * 2.4f) * 0.04f
      playerY += (swimY - playerY) * (dt * 8f).coerceAtMost(1f)
      playerVelY = 0f
      isGrounded = true
      airborneFallNorm = 0f
      airPeakY = playerY
      airborneLaunchVx = 0f
      airborneLaunchVz = 0f
    } else {
      val prevGrounded = isGrounded
      if (!prevGrounded && (abs(airborneLaunchVx) > 0.05f || abs(airborneLaunchVz) > 0.05f)) {
        playerX += airborneLaunchVx * dt
        playerZ += airborneLaunchVz * dt
        val damp = max(0f, 1f - dt * 1.4f)
        airborneLaunchVx *= damp
        airborneLaunchVz *= damp
      }

      val floorHeight = computeGroundHeight(playerX, playerZ, playerY)
      if (prevGrounded && playerVelY <= 0f && playerY - floorHeight in 0f..0.48f) {
        // Keep character planted smoothly onto descending stairs and slight floor transitions
        playerY = floorHeight
        playerVelY = 0f
        isGrounded = true
        airPeakY = playerY
        airborneFallNorm = 0f
      } else {
        if (prevGrounded && playerY - floorHeight > 0.48f) {
          // Stepped off a roof / cliff / balcony! Begin airborne fall tracking
          isGrounded = false
          airPeakY = playerY
          airStartY = playerY
        }
        playerVelY -= 21f * dt
        playerY += playerVelY * dt
        airPeakY = max(airPeakY, playerY)

        if (playerY <= floorHeight) {
          val dropDist = airPeakY - floorHeight
          playerY = floorHeight
          playerVelY = 0f
          isGrounded = true
          applyLandingImpact(dropDist, floorHeight)
        } else {
          val heightAboveFloor = playerY - floorHeight
          airborneFallNorm =
            if (playerVelY < -3.0f && heightAboveFloor > 1.25f) {
              ((heightAboveFloor - 1.25f) / 2.6f).coerceIn(0f, 1f)
            } else {
              0f
            }
        }
      }
    }

    dynamicVehicleColliders.clear()
    for (v in vehicles) {
      if (playerY > 0.85f) continue // Standing on vehicle hood/roof or higher floors shouldn't be pushed out horizontally
      val fx = sin(v.yawRad) * 1.1f
      val fz = cos(v.yawRad) * 1.1f
      dynamicVehicleColliders += CircleCollider(v.x + fx, v.z + fz, 1.15f, v.displayName)
      dynamicVehicleColliders += CircleCollider(v.x - fx, v.z - fz, 1.15f, v.displayName)
    }
    for (p in pedestrians) {
      if (p.reactionState != NpcReactionState.KNOCKED_DOWN && abs(p.y - playerY) < 1.5f) {
        dynamicVehicleColliders += CircleCollider(p.x, p.z, 0.35f, "Pedestrian")
      }
    }

    val (rx, rz, _) =
      CollisionSolver.resolveAll(
        playerX,
        playerZ,
        0.44f,
        world.aabbColliders,
        world.circleColliders + dynamicVehicleColliders,
        entityMinY = playerY,
        entityMaxY = playerY + 1.75f,
      )
    val glooResolved = resolveEntityVsGlooWalls(rx, rz, 0.44f, playerY)
    val clamped = CollisionSolver.clampSwimmerToWorld(glooResolved.first, glooResolved.second)
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
    isSitting = false
    isScoped = false
    sitProgress = 0f

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

    val targetTerrainY = computeGroundHeight(car.x, car.z, car.y + 0.55f)
    car.y += (targetTerrainY - car.y) * (dt * 14f).coerceAtMost(1f)
    val (slopePitch, slopeRoll) = TerrainHeightfield.computeSlopeAngles(car.x, car.z, car.yawRad)
    val targetPitch = (slopePitch - longAccel * 0.0028f).coerceIn(-0.44f, 0.44f)
    val targetRoll = (slopeRoll + yawRate * (speedAbs / 24f) * 0.055f).coerceIn(-0.34f, 0.34f)
    car.pitchRad += (targetPitch - car.pitchRad) * (dt * 9f).coerceAtMost(1f)
    car.rollRad += (targetRoll - car.rollRad) * (dt * 9f).coerceAtMost(1f)
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

    val glooResolved = resolveEntityVsGlooWalls(car.x, car.z, 1.35f, 0.25f)
    car.x = glooResolved.first
    car.z = glooResolved.second
    val clamped = CollisionSolver.clampCarToIsland(car.x, car.z, margin = 2.0f)
    if (clamped.first != car.x || clamped.second != car.z) {
      car.x = clamped.first
      car.z = clamped.second
      car.forwardSpeed *= 0.6f
    }
    car.y = computeGroundHeight(car.x, car.z, car.y + 0.55f)

    playerX = car.x
    playerY = car.y + 0.25f
    playerZ = car.z
    playerYawRad = car.yawRad
  }

  private fun stepDrivingBoat(boat: BoatRuntimeState, dt: Float) {
    nearbyVehicle = null
    nearbyBoat = null
    isSwimming = false
    isSitting = false
    isScoped = false
    sitProgress = 0f

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
    if (hitWaterEdge) boat.forwardSpeed *= 0.45f

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

  fun computeGroundHeight(x: Float, z: Float, curY: Float = 0f): Float {
    val roadBaseHeight =
      when {
        x in 22.8f..32.2f && z in -3.5f..6.2f -> 0.12f
        CollisionSolver.isOnPierOrDock(x, z) -> 0.24f
        CollisionSolver.isOnBridge(x, z) -> 0.12f
        // Highland Drive & North Parkway Connectors
        abs(z + 96f) < 5.2f && x in -266f..266f -> 0.08f
        x in 236f..296f && z in -22f..14f -> 0.08f
        (abs(z - 16f) < 5f || abs(z + 24f) < 5f) &&
          (abs(x) < 47f || x in 101f..299f || x in -276f..-101f) -> 0.04f
        (abs(x + 42f) < 5f || abs(x - 42f) < 5f || abs(x - 106f) < 5f || abs(x - 148f) < 5f || abs(x - 190f) < 5f ||
          abs(x - 233f) < 5f || abs(x - 299f) < 5f || abs(x + 104f) < 5f || abs(x + 194f) < 5f ||
          abs(x + 233f) < 5f || abs(x + 273f) < 5f) &&
          z in -29f..21f -> 0.04f
        abs(x + 148f) < 5.3f && z in -62f..50f -> 0.05f
        (abs(x) < 5f || abs(x - 148f) < 5f) && z in -96f..-20f -> 0.05f
        abs(x) < 4.6f && z in -21f..13f -> 0.04f
        else -> 0f
      }
    var bestHeight = max(roadBaseHeight, TerrainHeightfield.elevationAt(x, z).coerceAtLeast(0f))

    // Check multi-story floors, ramps, staircases, rooftops, crates, and 2nd-floor balconies below/at entity reach
    for (surface in world.walkableFloors) {
      if (x in surface.minX..surface.maxX && z in surface.minZ..surface.maxZ) {
        val elev = surface.elevationAt(x, z)
        if (elev <= curY + 0.72f && elev > bestHeight) {
          bestHeight = elev
        }
      }
    }

    // Check climbable solid obstacle tops (tables, sofas, wide walls, piers, water tanks)
    for (box in world.aabbColliders) {
      if ((box.maxX - box.minX) < 0.65f || (box.maxZ - box.minZ) < 0.65f) continue
      if (x in box.minX..box.maxX && z in box.minZ..box.maxZ) {
        if (box.maxY <= curY + 0.45f && box.maxY > bestHeight) {
          bestHeight = box.maxY
        }
      }
    }

    // Check parked/slow vehicle hoods and roofs
    if (curY >= 0.45f) {
      for (car in vehicles) {
        if (abs(car.forwardSpeed) > 1.8f) continue
        val dx = x - car.x
        val dz = z - car.z
        val c = cos(car.yawRad)
        val s = sin(car.yawRad)
        val lx = dx * c - dz * s
        val lz = dx * s + dz * c
        if (abs(lx) <= 0.96f && abs(lz) <= 2.12f) {
          val carElev = car.y + (if (abs(lz) <= 0.85f) 1.42f else 0.96f)
          if (carElev <= curY + 0.48f && carElev > bestHeight) {
            bestHeight = carElev
          }
        }
      }
    }
    return bestHeight
  }

  private fun updateHouseDetection() {
    if (dropPhase != DropPhase.GROUND || activeVehicle != null || activeBoat != null || activeCarjack != null) {
      currentInsideHouse = null
      return
    }
    var inside: HouseSpec? = null
    for (h in world.houses) {
      val hw = h.width * 0.5f - 0.30f
      val hd = h.depth * 0.5f - 0.30f
      val inMainBox =
        playerX in (h.centerX - hw)..(h.centerX + hw) &&
          playerZ in (h.centerZ - hd)..(h.centerZ + hd)
      val inBalcony =
        when {
          h.facingX != 0 && h.floors >= 2 && playerY in 2.6f..6.4f -> {
            val frontX = h.centerX + h.facingX * h.width * 0.5f
            val minBX = if (h.facingX > 0) frontX - 0.4f else frontX - 3.6f
            val maxBX = if (h.facingX > 0) frontX + 3.6f else frontX + 0.4f
            playerX in minBX..maxBX && playerZ in (h.centerZ - hd)..(h.centerZ + hd)
          }
          h.floors >= 2 && playerY in 2.6f..6.4f -> {
            val frontZ = if (h.facingSouth) h.centerZ + h.depth * 0.5f else h.centerZ - h.depth * 0.5f
            val minBZ = if (h.facingSouth) frontZ - 0.4f else frontZ - 3.0f
            val maxBZ = if (h.facingSouth) frontZ + 3.0f else frontZ + 0.4f
            playerX in (h.centerX - hw)..(h.centerX + hw) && playerZ in minBZ..maxBZ
          }
          else -> false
        }

      if (inMainBox || inBalcony) {
        inside = h
        break
      }
    }
    currentInsideHouse = inside
  }

  private fun updateGunShopProximity() {
    if (dropPhase != DropPhase.GROUND || activeVehicle != null || activeBoat != null || activeCarjack != null || isSwimming) {
      nearbyGunShop = false
      return
    }
    val d = CollisionSolver.distXZ(playerX, playerZ, world.gunShopBuyPos.first, world.gunShopBuyPos.second)
    nearbyGunShop = d < 5.6f
  }

  private fun updateMarketProximity() {
    if (dropPhase != DropPhase.GROUND || activeVehicle != null || activeBoat != null || activeCarjack != null || isSwimming) {
      nearbyMarketStallLabel = null
      return
    }
    nearbyMarketStallLabel = livingWorld.nearestMarketStall(playerX, playerZ)?.name
  }

  private fun updateFriendCandidate() {
    if (!canActOnFootCombat() || pedestrians.count { it.isFriend } >= 3) {
      nearbyFriendCandidate = null
      return
    }
    var closest: PedestrianRuntimeState? = null
    var closestDist = 3.8f
    for (ped in pedestrians) {
      if (ped.isFriend || ped.reactionState == NpcReactionState.KNOCKED_DOWN || ped.reactionState == NpcReactionState.FIGHTING_BACK) {
        continue
      }
      if (abs(ped.y - playerY) > 1.5f) continue
      val d = CollisionSolver.distXZ(playerX, playerZ, ped.x, ped.z)
      if (d < closestDist) {
        closestDist = d
        closest = ped
      }
    }
    nearbyFriendCandidate = closest
  }

  private fun updateCamera(dt: Float) {
    val carjack = activeCarjack
    val boat = activeBoat
    val car = activeVehicle
    val spectateTarget =
      if (isPlayerEliminated && isSpectatingKiller) {
        pedestrians.firstOrNull { it.id == spectatedKillerPedId }
      } else {
        null
      }
    when {
      isPlayerEliminated && isSpectatingKiller && spectateTarget != null -> {
        val dist = 5.6f
        val pitch = camPitchRad.coerceIn(0.06f, 0.82f)
        val targetLook = Vec3(spectateTarget.x, spectateTarget.y + 1.48f, spectateTarget.z)
        val camX = targetLook.x + sin(camYawRad) * cos(pitch) * dist
        val camZ = targetLook.z + cos(camYawRad) * cos(pitch) * dist
        val camY = (targetLook.y + sin(pitch) * dist).coerceAtLeast(spectateTarget.y + 0.9f)
        val smooth = (dt * 10f).coerceAtMost(1f)
        camPos = Vec3.lerp(camPos, Vec3(camX, camY, camZ), smooth)
        camLookAt = Vec3.lerp(camLookAt, targetLook, smooth)
        camFovDeg += (62f - camFovDeg) * (dt * 8f).coerceAtMost(1f)
      }
      isPlayerEliminated -> {
        val dist = 6.2f
        val targetLook = Vec3(playerX, playerY + 0.85f, playerZ)
        val orbitYaw = camYawRad + dt * 0.35f
        camYawRad = normalizeAngleRad(orbitYaw)
        val camX = targetLook.x + sin(camYawRad) * dist
        val camZ = targetLook.z + cos(camYawRad) * dist
        val camY = (targetLook.y + 2.6f).coerceAtLeast(playerY + 1.4f)
        val smooth = (dt * 8f).coerceAtMost(1f)
        camPos = Vec3.lerp(camPos, Vec3(camX, camY, camZ), smooth)
        camLookAt = Vec3.lerp(camLookAt, targetLook, smooth)
        camFovDeg += (64f - camFovDeg) * (dt * 8f).coerceAtMost(1f)
      }
      dropPhase == DropPhase.AIRPLANE -> {
        val dist = 19.5f
        val targetLook = Vec3(planeX + sin(planeYawRad) * 6f, planeY - 2.0f, planeZ + cos(planeYawRad) * 6f)
        val pitch = camPitchRad.coerceIn(0.10f, 1.05f)
        val camX = planeX + sin(camYawRad) * cos(pitch) * dist
        val camZ = planeZ + cos(camYawRad) * cos(pitch) * dist
        val camY = planeY + sin(pitch) * dist + 1.5f
        val smooth = (dt * 10f).coerceAtMost(1f)
        camPos = Vec3.lerp(camPos, Vec3(camX, camY, camZ), smooth)
        camLookAt = Vec3.lerp(camLookAt, targetLook, smooth)
        camFovDeg += (72f - camFovDeg) * (dt * 8f).coerceAtMost(1f)
      }
      dropPhase == DropPhase.FREEFALL || dropPhase == DropPhase.PARACHUTE -> {
        val dist = if (dropPhase == DropPhase.PARACHUTE) 9.6f else 8.2f
        val liftY = if (dropPhase == DropPhase.PARACHUTE) 1.8f else 0.6f
        val targetLook = Vec3(playerX, playerY + liftY, playerZ)
        val pitch = camPitchRad.coerceIn(-0.08f, 1.05f)
        val camX = targetLook.x + sin(camYawRad) * cos(pitch) * dist
        val camZ = targetLook.z + cos(camYawRad) * cos(pitch) * dist
        val camY = (targetLook.y + sin(pitch) * dist + 1.4f).coerceAtLeast(1.8f)
        val smooth = (dt * 12f).coerceAtMost(1f)
        camPos = Vec3.lerp(camPos, Vec3(camX, camY, camZ), smooth)
        camLookAt = Vec3.lerp(camLookAt, targetLook, smooth)
        val goalFov = if (dropPhase == DropPhase.FREEFALL) 74f else 68f
        camFovDeg += (goalFov - camFovDeg) * (dt * 8f).coerceAtMost(1f)
      }
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
        val targetLook = Vec3(car.x, car.y + 1.22f, car.z)
        val camX = targetLook.x + sin(camYawRad) * cos(camPitchRad) * dist
        val camZ = targetLook.z + cos(camYawRad) * cos(camPitchRad) * dist
        val terrainClearY = TerrainHeightfield.elevationAt(camX, camZ) + 0.85f
        val camY = (targetLook.y + sin(camPitchRad) * dist + 0.65f).coerceAtLeast(max(car.y + 1.0f, terrainClearY))

        val smooth = (dt * 12f).coerceAtMost(1f)
        camPos = Vec3.lerp(camPos, Vec3(camX, camY, camZ), smooth)
        camLookAt = Vec3.lerp(camLookAt, targetLook, smooth)
        camFovDeg += ((62f + speedRatio * 11f) - camFovDeg) * (dt * 6f).coerceAtMost(1f)
      }
      isScoped && equippedWeapon != WeaponType.FISTS -> {
        // Precision Long-Range Scope / Over-the-Shoulder ADS view
        val aimYaw = playerYawRad
        val fwdX = sin(aimYaw)
        val fwdZ = cos(aimYaw)
        val rightX = cos(aimYaw)
        val rightZ = -sin(aimYaw)
        val shoulderRight = if (equippedWeapon == WeaponType.SNIPER_RIFLE) 0.34f else 0.44f
        val backDist = if (equippedWeapon == WeaponType.SNIPER_RIFLE) 1.05f else 1.55f
        val eyeY = playerY + 1.54f

        val goalPos = Vec3(playerX - fwdX * backDist + rightX * shoulderRight, eyeY + sin(camPitchRad) * 0.45f, playerZ - fwdZ * backDist + rightZ * shoulderRight)
        val elev = (-camPitchRad).coerceIn(-0.42f, 0.38f)
        val goalLook = Vec3(goalPos.x + fwdX * cos(elev) * 28f, goalPos.y + sin(elev) * 28f, goalPos.z + fwdZ * cos(elev) * 28f)

        val smooth = (dt * 18f).coerceAtMost(1f)
        camPos = Vec3.lerp(camPos, goalPos, smooth)
        camLookAt = Vec3.lerp(camLookAt, goalLook, smooth)
        camFovDeg += (equippedWeapon.scopeFovDeg - camFovDeg) * (dt * 14f).coerceAtMost(1f)
      }
      else -> {
        val insideEnclosed = currentInsideHouse != null && playerY < 6.4f && activeParkour == null
        val dist = if (insideEnclosed) min(cameraMode.footDist, 3.1f) else cameraMode.footDist
        val pitch = if (insideEnclosed) camPitchRad.coerceIn(-0.05f, 0.36f) else camPitchRad
        val sitLookDrop = sitProgress * 0.34f
        val shakeX = if (camImpactShake > 0.01f) sin(worldTimeSec * 46f) * camImpactShake * 0.22f else 0f
        val shakeY = if (camImpactShake > 0.01f) cos(worldTimeSec * 53f) * camImpactShake * 0.16f else 0f
        val targetLook = Vec3(playerX + shakeX, playerY + 1.48f - sitLookDrop + camLandingDipY + shakeY, playerZ)
        val camX = targetLook.x + sin(camYawRad) * cos(pitch) * dist
        val camZ = targetLook.z + cos(camYawRad) * cos(pitch) * dist
        val terrainClearY = TerrainHeightfield.elevationAt(camX, camZ) + 0.65f
        val minH = max(playerY + 0.65f + camLandingDipY * 0.5f, terrainClearY)
        val maxH = if (insideEnclosed) playerY + 3.25f else playerY + 26f
        val camY = (targetLook.y + sin(pitch) * dist).coerceIn(minH, max(minH, maxH))

        val smooth = (dt * 14f).coerceAtMost(1f)
        camPos = Vec3.lerp(camPos, Vec3(camX, camY, camZ), smooth)
        camLookAt = Vec3.lerp(camLookAt, targetLook, smooth)
        val targetFov = if (airborneFallNorm > 0.15f) 60f + airborneFallNorm * 8f else 60f
        camFovDeg += (targetFov - camFovDeg) * (dt * 10f).coerceAtMost(1f)
      }
    }
  }

  @Synchronized
  fun toggleEnterExitVehicleOrBoat(): Boolean {
    if (!isPlaying || isPlayerEliminated || activeCarjack != null) return false

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
      playerY = computeGroundHeight(playerX, playerZ, currentCar.y + 0.65f)
      playerYawRad = currentCar.yawRad
      currentCar.isBraking = false
      currentCar.isDrifting = false
      currentCar.forwardSpeed *= 0.3f
      activeVehicle = null
      return false
    }

    isSitting = false
    isScoped = false
    input.autoSprint = false
    sitProgress = 0f

    val targetBoat = nearbyBoat
    val targetCar = nearbyVehicle
    if (targetBoat != null && (targetCar == null || isSwimming)) {
      if (targetBoat.isAiCruise) {
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
            pantsColor = targetBoat.appearance.pantsColor,
            appearance = targetBoat.appearance,
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
      val botDriver = targetCar.driverBotId?.let { id -> pedestrians.firstOrNull { it.id == id } }
      if (botDriver != null) {
        botController.releaseBotVehicle(botDriver)
        botDriver.reactionState = NpcReactionState.FIGHTING_BACK
        botDriver.isHostileTarget = true
        botDriver.targetPlayer = true
        botDriver.panicTimerSec = 18f
        showBanner("EJECTED ${rivalDisplayName(botDriver)}!")
      }
      if (targetCar.isAiTraffic) {
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
            pantsColor = targetCar.appearance.pantsColor,
            appearance = targetCar.appearance,
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
    dropPhase = DropPhase.GROUND
    activeVehicle = null
    activeBoat = null
    activeCarjack = null
    isSwimming = false
    isSitting = false
    isScoped = false
    sitProgress = 0f
    activeParkour = null
    activeLandingReaction = null
    landingReactionTimerSec = 0f
    airborneFallNorm = 0f
    if (h.facingX != 0) {
      playerX = h.centerX + h.facingX * (h.width * 0.5f + 4.4f)
      playerZ = h.centerZ - 1.4f
      playerY = computeGroundHeight(playerX, playerZ, 0f)
      playerYawRad = if (h.facingX > 0) (-PI / 2.0).toFloat() else (PI / 2.0).toFloat()
    } else {
      val doorSign = if (h.facingSouth) 1f else -1f
      playerX = h.centerX
      playerZ = h.centerZ + doorSign * (h.depth * 0.5f + 2.4f)
      playerY = computeGroundHeight(playerX, playerZ, 0f)
      playerYawRad = if (h.facingSouth) PI.toFloat() else 0f
    }
    airPeakY = playerY
    airStartY = playerY
    camYawRad = normalizeAngleRad(playerYawRad + PI.toFloat())
  }

  @Synchronized
  fun teleportToBoatPier() {
    dropPhase = DropPhase.GROUND
    activeVehicle = null
    activeBoat = null
    activeCarjack = null
    isSwimming = false
    isSitting = false
    isScoped = false
    sitProgress = 0f
    playerX = -198f
    playerZ = 90.0f
    playerY = 0.24f
    playerYawRad = 0f
    camYawRad = PI.toFloat()
    camPitchRad = 0.24f
  }

  @Synchronized
  fun teleportToMarket() {
    teleportOnFootTo(world.marketCenter.first, world.marketCenter.second - 2.6f, (-PI / 4.0).toFloat())
  }

  @Synchronized
  fun teleportToWaterfall() {
    teleportOnFootTo(world.waterfallCenter.first, world.waterfallCenter.second, PI.toFloat())
  }

  @Synchronized
  fun teleportToAtoll() {
    teleportOnFootTo(world.atollCenter.first, world.atollCenter.second + 8f, 0f)
  }

  private fun teleportOnFootTo(tx: Float, tz: Float, yaw: Float) {
    dropPhase = DropPhase.GROUND
    activeVehicle = null
    activeBoat = null
    activeCarjack = null
    isSwimming = false
    isSitting = false
    isScoped = false
    sitProgress = 0f
    activeParkour = null
    activeLandingReaction = null
    landingReactionTimerSec = 0f
    playerX = tx
    playerZ = tz
    playerY = computeGroundHeight(tx, tz, 0f)
    playerYawRad = yaw
    airPeakY = playerY
    airStartY = playerY
    camYawRad = normalizeAngleRad(playerYawRad + PI.toFloat())
    camPitchRad = 0.24f
  }

  @Synchronized
  fun cycleWeatherMode() {
    val modes = WeatherMode.entries
    livingWorld.weatherMode = modes[(livingWorld.weatherMode.ordinal + 1) % modes.size]
    showBanner("WEATHER · ${livingWorld.weatherMode.label}")
  }

  @Synchronized
  fun setWeatherMode(mode: WeatherMode) {
    livingWorld.weatherMode = mode
    showBanner("WEATHER · ${mode.label}")
  }

  @Synchronized
  fun toggleAutoWeather() {
    livingWorld.autoWeatherCycle = !livingWorld.autoWeatherCycle
    showBanner(if (livingWorld.autoWeatherCycle) "LIVE AUTO WEATHER ON" else "MANUAL WEATHER")
  }

  @Synchronized
  fun buyMarketSnack(): Boolean {
    val stall = livingWorld.nearestMarketStall(playerX, playerZ) ?: return false
    if (playerCash < 25) {
      showBanner("NEED $25 FOR ${stall.kind.label}")
      return false
    }
    playerCash -= 25
    playerHealth = min(100f, playerHealth + 35f)
    val item = stall.kind.itemNames.firstOrNull() ?: "Produce"
    onCombatSfx?.invoke(CombatSfxEvent.CASH)
    showBanner("BOUGHT $item · +35 HP")
    return true
  }

  @Synchronized
  fun computeAmbientAudioSnapshot(): AmbientAudioSnapshot =
    livingWorld.computeAmbientAudioSnapshot(
      playerX = playerX,
      playerZ = playerZ,
      vehicles = vehicles,
      isPlaying = isPlaying && !isPaused && !isPlayerEliminated,
    )

  @Synchronized
  fun resetToSpawn() {
    dropPhase = DropPhase.GROUND
    planeActiveInSky = false
    activeVehicle = null
    activeBoat = null
    activeCarjack = null
    ejectedNpcs.clear()
    bulletProjectiles.clear()
    hitSparks.clear()
    glooWalls.clear()
    playerReserveGlooWalls = 2
    isSwimming = false
    isSitting = false
    isScoped = false
    input.sprintHeld = false
    input.autoSprint = false
    sitProgress = 0f
    activeAttackMove = null
    attackTimerSec = 0f
    activeParkour = null
    nearbyParkourCandidate = null
    nearbyParkourLabel = null
    activeLandingReaction = null
    landingReactionTimerSec = 0f
    airborneFallNorm = 0f
    airborneLaunchVx = 0f
    airborneLaunchVz = 0f
    camLandingDipY = 0f
    camImpactShake = 0f
    playerX = world.playerSpawnX
    playerY = computeGroundHeight(world.playerSpawnX, world.playerSpawnZ, 0f)
    playerZ = world.playerSpawnZ
    airPeakY = playerY
    airStartY = playerY
    playerYawRad = world.playerSpawnYaw
    camYawRad = normalizeAngleRad(playerYawRad + PI.toFloat())
    camPitchRad = 0.28f
    vehicles.forEachIndexed { idx, v ->
      val sp = world.vehicleSpawns[idx]
      v.x = sp.x
      v.z = sp.z
      v.y = TerrainHeightfield.elevationAt(sp.x, sp.z).coerceAtLeast(0f)
      v.yawRad = sp.yawRad
      v.isAiTraffic = sp.isAiTraffic
      v.driverBotId = null
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
    isBagOpen = false
    bagItems.clear()
    playerVestLevel = 0
    playerVestDurability = 0f
    playerHelmetLevel = 0
    playerHelmetDurability = 0f
    nearbyLootLabel = null
    recomputeOwnedWeaponsFromPurchasedAndBag()
    populateHouseLoot()
    pedestrians.forEachIndexed { idx, p ->
      val sp = world.pedestrianSpawns[idx]
      p.x = sp.waypoints.first().first
      p.z = sp.waypoints.first().second
      p.y = computeGroundHeight(p.x, p.z, 0f)
      p.hp = p.maxHp
      p.isFriend = false
      p.sitProgress = 0f
      p.isHostileTarget = false
      p.hitReactZone = HitZone.NONE
      p.hitReactTimerSec = 0f
      p.reactionState = NpcReactionState.STROLLING
      p.fallProgress = 0f
      p.armsUpNorm = 0f
      p.attackMove = null
      p.vx = 0f
      p.vz = 0f
      p.drivingVehicleId = null
      p.healTimerSec = 0f
      p.glooWallCooldownSec = 0f
      p.coverTimerSec = 0f
      p.targetEnemyBotId = null
      p.targetPlayer = false
      p.targetLootUid = null
      p.botMedkits = if (sp.isRivalFighter) 2 else 0
      p.botGlooWalls = if (sp.isRivalFighter) 2 else 1
      p.botBagCount = if (sp.isRivalFighter) 3 else 0
      p.botMode = BotTacticalMode.ROAMING
      p.weapon = sp.rivalWeapon
      p.vestLevel = sp.vestLevel
      p.vestDurability =
        when (sp.vestLevel) {
          4 -> LootItemKind.VEST_LV4.maxDurability
          3 -> LootItemKind.VEST_LV3.maxDurability
          else -> 0f
        }
      p.helmetLevel = sp.helmetLevel
      p.helmetDurability =
        when (sp.helmetLevel) {
          4 -> LootItemKind.HELMET_LV4.maxDurability
          3 -> LootItemKind.HELMET_LV3.maxDurability
          else -> 0f
        }
    }
  }

  private fun resetRivalFightersForMatch() {
    pedestrians.forEachIndexed { idx, p ->
      val sp = world.pedestrianSpawns[idx]
      if (p.isRivalFighter) {
        p.x = sp.waypoints.first().first
        p.z = sp.waypoints.first().second
        p.y = 0f
        p.hp = p.maxHp
        p.reactionState = NpcReactionState.STROLLING
        p.fallProgress = 0f
        p.armsUpNorm = 0f
        p.sitProgress = 0f
        p.attackMove = null
        p.isHostileTarget = false
        p.drivingVehicleId = null
        p.healTimerSec = 0f
        p.glooWallCooldownSec = 0f
        p.coverTimerSec = 0f
        p.targetEnemyBotId = null
        p.targetPlayer = false
        p.targetLootUid = null
        p.botMedkits = 2
        p.botGlooWalls = 2
        p.botBagCount = 3
        p.botMode = BotTacticalMode.ROAMING
        p.weapon = sp.rivalWeapon
        p.vestLevel = sp.vestLevel
        p.vestDurability =
          when (sp.vestLevel) {
            4 -> LootItemKind.VEST_LV4.maxDurability
            3 -> LootItemKind.VEST_LV3.maxDurability
            else -> 0f
          }
        p.helmetLevel = sp.helmetLevel
        p.helmetDurability =
          when (sp.helmetLevel) {
            4 -> LootItemKind.HELMET_LV4.maxDurability
            3 -> LootItemKind.HELMET_LV3.maxDurability
            else -> 0f
          }
      }
    }
  }

  private fun populateHouseLoot() {
    worldLootItems.clear()
    val gunCycle =
      listOf(
        LootItemKind.PISTOL_9MM,
        LootItemKind.SMG_MICRO,
        LootItemKind.SHOTGUN,
        LootItemKind.ASSAULT_RIFLE,
        LootItemKind.SNIPER_RIFLE,
      )
    val vestCycle = listOf(LootItemKind.VEST_LV3, LootItemKind.VEST_LV4)
    val helmCycle = listOf(LootItemKind.HELMET_LV3, LootItemKind.HELMET_LV4)

    world.houses.forEachIndexed { idx, h ->
      val cx = h.centerX
      val cz = h.centerZ
      // 1F interior open spots clear of central sofa/table/stairs
      spawnLootSafely(gunCycle[idx % gunCycle.size], cx + 0.1f, cz + 1.8f, 0.4f, h.id)
      spawnLootSafely(LootItemKind.MEDKIT, cx + 0.2f, cz - 2.1f, 0.4f, h.id)
      spawnLootSafely(LootItemKind.COIN, cx - 0.1f, cz + 0.2f, 0.4f, h.id)
      spawnLootSafely(LootItemKind.GLOO_WALL, cx - 2.2f, cz - 1.9f, 0.4f, h.id)
      spawnLootSafely(vestCycle[idx % vestCycle.size], cx + 2.4f, cz - 2.2f, 0.4f, h.id)
      spawnLootSafely(helmCycle[(idx + 1) % helmCycle.size], cx + 2.4f, cz + 2.1f, 0.4f, h.id)

      // 2F & Rooftop premium loot for multi-story houses
      if (h.floors >= 2) {
        val rareGun = if (idx % 2 == 0) LootItemKind.ASSAULT_RIFLE else LootItemKind.SNIPER_RIFLE
        spawnLootSafely(rareGun, cx - 0.8f, cz - 2.4f, 3.8f, h.id)
        spawnLootSafely(LootItemKind.VEST_LV4, cx - 1.2f, cz + 2.2f, 3.8f, h.id)
        spawnLootSafely(LootItemKind.COIN, cx - 2.8f, cz + 1.8f, 3.8f, h.id)
        if (h.hasRooftop) {
          spawnLootSafely(LootItemKind.HELMET_LV4, cx + 1.4f, cz + 0.4f, 7.2f, h.id)
          spawnLootSafely(LootItemKind.MEDKIT, cx + 1.4f, cz - 1.4f, 7.2f, h.id)
        }
      }
    }
  }

  private fun spawnLootSafely(
    kind: LootItemKind,
    desiredX: Float,
    desiredZ: Float,
    targetCurY: Float,
    houseId: String?,
  ) {
    val candidates =
      listOf(
        Pair(desiredX, desiredZ),
        Pair(desiredX + 0.9f, desiredZ),
        Pair(desiredX - 0.9f, desiredZ),
        Pair(desiredX, desiredZ + 0.9f),
        Pair(desiredX, desiredZ - 0.9f),
      )
    var finalX = desiredX
    var finalZ = desiredZ
    for ((px, pz) in candidates) {
      val elev = computeGroundHeight(px, pz, targetCurY)
      if (!isPointBlockedBySolidAabb(px, pz, elev + 0.25f, elev + 1.45f, 0.36f)) {
        finalX = px
        finalZ = pz
        break
      }
    }
    val elev = computeGroundHeight(finalX, finalZ, targetCurY)
    worldLootItems +=
      WorldLootItem(
        uid = "loot_${++lootUidSeq}",
        kind = kind,
        x = finalX,
        y = elev,
        z = finalZ,
        houseId = houseId,
      )
  }

  private fun dropRivalLoot(ped: PedestrianRuntimeState) {
    spawnLootSafely(LootItemKind.COIN, ped.x - 0.6f, ped.z + 0.4f, ped.y + 0.5f, null)
    spawnLootSafely(LootItemKind.MEDKIT, ped.x + 0.6f, ped.z - 0.4f, ped.y + 0.5f, null)
    if (ped.botGlooWalls > 0) {
      spawnLootSafely(LootItemKind.GLOO_WALL, ped.x - 0.5f, ped.z - 0.5f, ped.y + 0.5f, null)
    }
    val weaponLoot = LootItemKind.entries.firstOrNull { it.weapon == ped.weapon }
    if (weaponLoot != null) {
      spawnLootSafely(weaponLoot, ped.x, ped.z + 0.8f, ped.y + 0.5f, null)
    }
  }

  private fun stepWorldLootAutoPickup(dt: Float) {
    if (bagFullBannerCooldownSec > 0f) {
      bagFullBannerCooldownSec = max(0f, bagFullBannerCooldownSec - dt)
    }
    var nearLabel: String? = null
    val iter = worldLootItems.iterator()
    while (iter.hasNext()) {
      val item = iter.next()
      if (item.pickupCooldownSec > 0f) {
        item.pickupCooldownSec = max(0f, item.pickupCooldownSec - dt)
      }
      val dist = CollisionSolver.distXZ(playerX, playerZ, item.x, item.z)
      if (abs(item.y - playerY) > 1.55f) continue

      if (dist <= 1.35f && item.pickupCooldownSec <= 0f) {
        when (item.kind.category) {
          LootCategory.CURRENCY -> {
            playerCash += item.kind.coinValue
            hitSparks += HitSparkState(item.x, item.y + 0.45f, item.z, floatArrayOf(1.0f, 0.85f, 0.22f))
            onCombatSfx?.invoke(CombatSfxEvent.CASH)
            showBanner("+$${item.kind.coinValue} COIN PICKED UP")
            iter.remove()
            continue
          }
          LootCategory.VEST -> {
            val lvl = item.kind.armorLevel
            val maxDur = item.kind.maxDurability
            if (lvl > playerVestLevel || (lvl == playerVestLevel && playerVestDurability < maxDur * 0.85f)) {
              playerVestLevel = lvl
              playerVestDurability = maxDur
              hitSparks += HitSparkState(item.x, item.y + 0.55f, item.z, floatArrayOf(0.24f, 0.90f, 1.0f))
              onCombatSfx?.invoke(CombatSfxEvent.FRIEND)
              showBanner("AUTO-EQUIPPED ${item.kind.displayName}")
              iter.remove()
              continue
            } else if (bagItems.size < MAX_BAG_SLOTS) {
              bagItems.add(BagItem("bag_${++lootUidSeq}", item.kind, maxDur))
              onCombatSfx?.invoke(CombatSfxEvent.CASH)
              showBanner("LOOTED ${item.kind.displayName} (${bagItems.size}/$MAX_BAG_SLOTS)")
              iter.remove()
              continue
            }
          }
          LootCategory.HELMET -> {
            val lvl = item.kind.armorLevel
            val maxDur = item.kind.maxDurability
            if (lvl > playerHelmetLevel || (lvl == playerHelmetLevel && playerHelmetDurability < maxDur * 0.85f)) {
              playerHelmetLevel = lvl
              playerHelmetDurability = maxDur
              hitSparks += HitSparkState(item.x, item.y + 0.55f, item.z, floatArrayOf(1.0f, 0.80f, 0.24f))
              onCombatSfx?.invoke(CombatSfxEvent.FRIEND)
              showBanner("AUTO-EQUIPPED ${item.kind.displayName}")
              iter.remove()
              continue
            } else if (bagItems.size < MAX_BAG_SLOTS) {
              bagItems.add(BagItem("bag_${++lootUidSeq}", item.kind, maxDur))
              onCombatSfx?.invoke(CombatSfxEvent.CASH)
              showBanner("LOOTED ${item.kind.displayName} (${bagItems.size}/$MAX_BAG_SLOTS)")
              iter.remove()
              continue
            }
          }
          LootCategory.MEDICAL -> {
            if (bagItems.size < MAX_BAG_SLOTS) {
              bagItems.add(BagItem("bag_${++lootUidSeq}", item.kind))
              onCombatSfx?.invoke(CombatSfxEvent.FRIEND)
              showBanner("AUTO-LOOT: MEDKIT (${bagItems.size}/$MAX_BAG_SLOTS)")
              iter.remove()
              continue
            }
          }
          LootCategory.GLOO_WALL -> {
            if (bagItems.size < MAX_BAG_SLOTS) {
              bagItems.add(BagItem("bag_${++lootUidSeq}", item.kind))
              onCombatSfx?.invoke(CombatSfxEvent.CLIMB)
              showBanner("AUTO-LOOT: GLOO WALL (${bagItems.size}/$MAX_BAG_SLOTS)")
              iter.remove()
              continue
            }
          }
          LootCategory.WEAPON -> {
            val weapon = item.kind.weapon
            if (bagItems.size < MAX_BAG_SLOTS && weapon != null) {
              bagItems.add(BagItem("bag_${++lootUidSeq}", item.kind))
              ownedWeapons += weapon
              if (equippedWeapon == WeaponType.FISTS) {
                equippedWeapon = weapon
              }
              hitSparks += HitSparkState(item.x, item.y + 0.45f, item.z, floatArrayOf(0.28f, 0.92f, 0.72f))
              onCombatSfx?.invoke(CombatSfxEvent.CASH)
              showBanner("AUTO-LOOT: ${weapon.displayName} (${bagItems.size}/$MAX_BAG_SLOTS)")
              iter.remove()
              continue
            }
          }
        }
        // Bag is full -> alert player cleanly without spamming
        nearLabel = "BAG FULL · OPEN BAG TO REMOVE (${item.kind.shortLabel})"
        if (bagFullBannerCooldownSec <= 0f) {
          bagFullBannerCooldownSec = 2.4f
          showBanner("BAG FULL ($MAX_BAG_SLOTS/$MAX_BAG_SLOTS) · REMOVE ITEM")
        }
      } else if (dist <= 2.2f && item.pickupCooldownSec <= 0f) {
        nearLabel =
          if (bagItems.size >= MAX_BAG_SLOTS && item.kind.category != LootCategory.CURRENCY) {
            "BAG FULL · REMOVE ITEM FOR ${item.kind.shortLabel}"
          } else {
            "AUTO-PICKUP: ${item.kind.displayName}"
          }
      }
    }
    nearbyLootLabel = nearLabel
  }

  internal fun rivalDisplayName(ped: PedestrianRuntimeState): String {
    if (!ped.isRivalFighter) {
      return if (ped.isBraveDefender) "BAYSIDE GUARD" else "BOT ${ped.id.takeLast(4).uppercase()}"
    }
    return when {
      ped.id.contains("1") -> "RIVAL VIPER"
      ped.id.contains("2") -> "RIVAL KOBRA"
      ped.id.contains("3") -> "RIVAL STRIKER"
      ped.id.contains("4") -> "RIVAL DRAKE"
      ped.id.contains("5") -> "RIVAL PHANTOM"
      ped.id.contains("market") -> "BAZAAR RAIDER"
      ped.id.contains("bazaar") -> "BAZAAR GHOST"
      ped.id.contains("sierra") -> "SIERRA HAWK"
      ped.id.contains("emerald") -> "EMERALD TITAN"
      ped.id.contains("canyon") -> "CANYON RANGER"
      ped.id.contains("atoll") -> "ATOLL CORSAIR"
      else -> "RIVAL ${ped.id.takeLast(2).uppercase()}"
    }
  }

  private fun ensureLiveSpectatedTarget() {
    val current = pedestrians.firstOrNull { it.id == spectatedKillerPedId && it.reactionState != NpcReactionState.KNOCKED_DOWN }
    if (current != null) return
    val fallback =
      pedestrians
        .filter { it.isRivalFighter && it.reactionState != NpcReactionState.KNOCKED_DOWN && !it.isFriend }
        .minByOrNull { CollisionSolver.distXZ(playerX, playerZ, it.x, it.z) }
        ?: pedestrians
          .filter { it.reactionState != NpcReactionState.KNOCKED_DOWN && !it.isFriend }
          .minByOrNull { CollisionSolver.distXZ(playerX, playerZ, it.x, it.z) }
    spectatedKillerPedId = fallback?.id
  }

  private fun awardMatchCoinsIfNeeded(isEliminated: Boolean) {
    if (matchCoinsAwarded) return
    matchCoinsAwarded = true
    val kos = (npcDefeatedCount - matchStartKoCount).coerceAtLeast(0)
    val survivalBonus = (matchElapsedSec * 3.0f).toInt().coerceIn(if (isEliminated) 60 else 40, 260)
    val baseFinish = if (isEliminated) 110 else 75
    val totalReward = baseFinish + survivalBonus + (kos * 85)
    playerCash += totalReward
    lastMatchCoinsEarned = totalReward
    onEconomyChanged?.invoke()
  }

  private fun triggerPlayerElimination() {
    if (isPlayerEliminated) return
    playerHealth = 0f
    isPlayerEliminated = true
    isSpectatingKiller = false
    isScoped = false
    input.autoSprint = false
    isPaused = false
    isBagOpen = false
    isGunShopOpen = false
    isStyleShopOpen = false
    activeAttackMove = null
    activeCarjack = null
    activeParkour = null
    playerFallProgress = 0.08f
    wantedLevel = 0

    val directKiller =
      lastAttackerPedId?.let { id ->
        pedestrians.firstOrNull { it.id == id && it.reactionState != NpcReactionState.KNOCKED_DOWN }
      }
    val resolvedKiller =
      directKiller
        ?: pedestrians
          .filter { it.isRivalFighter && it.reactionState != NpcReactionState.KNOCKED_DOWN && !it.isFriend }
          .minByOrNull { CollisionSolver.distXZ(playerX, playerZ, it.x, it.z) }
        ?: pedestrians
          .filter { it.reactionState != NpcReactionState.KNOCKED_DOWN && !it.isFriend }
          .minByOrNull { CollisionSolver.distXZ(playerX, playerZ, it.x, it.z) }

    spectatedKillerPedId = resolvedKiller?.id
    eliminatedByName = lastAttackerName ?: resolvedKiller?.let { rivalDisplayName(it) } ?: "RIVAL FIGHTER"
    eliminatedByWeapon = if (lastAttackerName != null) lastAttackerWeapon else (resolvedKiller?.weapon ?: WeaponType.FISTS)

    awardMatchCoinsIfNeeded(isEliminated = true)
    onCombatSfx?.invoke(CombatSfxEvent.KICK)
    showBanner("ELIMINATED BY $eliminatedByName · +$lastMatchCoinsEarned COINS")
  }

  internal fun applyIncomingDamageToPlayer(
    rawDamage: Float,
    hitZone: HitZone,
    attackerId: String? = null,
    attackerName: String? = null,
    attackerWeapon: WeaponType = WeaponType.FISTS,
  ) {
    if (isPlayerEliminated) return
    if (attackerName != null) {
      lastAttackerPedId = attackerId
      lastAttackerName = attackerName
      lastAttackerWeapon = attackerWeapon
      lastAttackerTimerSec = 12f
    }
    var finalDmg = rawDamage
    if (hitZone == HitZone.HEAD && playerHelmetLevel >= 3 && playerHelmetDurability > 0f) {
      val absorb = if (playerHelmetLevel >= 4) 0.55f else 0.40f
      finalDmg = rawDamage * (1f - absorb)
      playerHelmetDurability = max(0f, playerHelmetDurability - rawDamage * 0.55f)
      if (playerHelmetDurability <= 0f) {
        playerHelmetLevel = 0
        showBanner("HELMET DESTROYED!")
      }
    } else if (playerVestLevel >= 3 && playerVestDurability > 0f) {
      val absorb = if (playerVestLevel >= 4) 0.58f else 0.42f
      finalDmg = rawDamage * (1f - absorb)
      playerVestDurability = max(0f, playerVestDurability - rawDamage * 0.60f)
      if (playerVestDurability <= 0f) {
        playerVestLevel = 0
        showBanner("VEST DESTROYED!")
      }
    }
    playerHealth = max(0f, playerHealth - finalDmg)
    regenCooldownSec = 5.0f
  }

  internal fun hasLineOfSight(
    x1: Float,
    y1: Float,
    z1: Float,
    x2: Float,
    y2: Float,
    z2: Float,
  ): Boolean {
    val dx = x2 - x1
    val dz = z2 - z1
    val distXZ = hypot(dx, dz).coerceAtLeast(1e-4f)
    val rdx = dx / distXZ
    val rdz = dz / distXZ
    val midY = (y1 + y2) * 0.5f
    for (box in world.aabbColliders) {
      if (box.tag.contains("Sofa") || box.tag.contains("Table") || box.tag.contains("Bench")) continue
      val t = intersectRayAabbXZ(x1, z1, rdx, rdz, box.minX, box.minZ, box.maxX, box.maxZ)
      if (t != null && t in 0.1f..distXZ && midY in box.minY..box.maxY) {
        return false
      }
    }
    for (wall in glooWalls) {
      if (wall.hp <= 0f) continue
      if (midY !in wall.y..(wall.y + 2.3f)) continue
      if (intersectSegmentGlooWall(x1, z1, x2, z2, wall) != null) {
        return false
      }
    }
    // Check terrain ridges / hills blocking direct line of sight
    if (distXZ > 6f) {
      for (i in 1..4) {
        val t = i / 5f
        val sx = x1 + dx * t
        val sz = z1 + dz * t
        val sy = y1 + (y2 - y1) * t
        if (TerrainHeightfield.elevationAt(sx, sz) > sy + 0.30f) {
          return false
        }
      }
    }
    return true
  }

  @Synchronized
  fun snapshotHud(isMuted: Boolean): HudSnapshot {
    val boat = activeBoat
    val car = activeVehicle
    val carjack = activeCarjack
    val speedMs =
      when {
        dropPhase == DropPhase.AIRPLANE -> 88f
        dropPhase == DropPhase.FREEFALL -> abs(playerVelY)
        dropPhase == DropPhase.PARACHUTE -> hypot(horizVelX, horizVelZ)
        boat != null -> hypot(boat.forwardSpeed, boat.lateralSpeed)
        car != null -> hypot(car.forwardSpeed, car.lateralSpeed)
        playerSpeedNorm > 0.1f -> playerSpeedNorm * (if (isSwimming) 5.2f else 9.8f)
        else -> 0f
      }
    val kmh = (speedMs * 3.6f).toInt()
    val nexteraDistrict =
      when {
        hypot(playerX + 18f, playerZ + 180f) < 76f -> "INTELLECT CENTER"
        hypot(playerX + 218f, playerZ - 6f) < 44f -> "TURBINE"
        hypot(playerX + 178f, playerZ + 80f) < 52f -> "MUSEUM"
        hypot(playerX + 158f, playerZ - 168f) < 58f -> "GRAV LABS"
        hypot(playerX + 66f, playerZ - 152f) < 42f -> "PLAZARIA"
        hypot(playerX - 24f, playerZ - 172f) < 48f -> "DECA SQUARE"
        hypot(playerX - 154f, playerZ - 148f) < 48f -> "MUD SITE"
        hypot(playerX - 76f, playerZ - 14f) < 44f -> "ZIPWAY"
        hypot(playerX - 188f, playerZ - 22f) < 56f -> "RUST TOWN"
        hypot(playerX - 172f, playerZ + 68f) < 48f -> "MORTAR RUINS"
        hypot(playerX - 116f, playerZ + 148f) < 44f -> "TWIN BRIDGE"
        hypot(playerX + 72f, playerZ - 6f) < 62f -> "FARMTOPIA"
        else -> "NEXTERA ISLAND"
      }

    val zone =
      when {
        dropPhase == DropPhase.AIRPLANE -> "FLIGHT DROP · ${planeTimerSec.toInt().coerceAtLeast(0)}s"
        dropPhase == DropPhase.FREEFALL -> "SKYDIVING · FREEFALL"
        dropPhase == DropPhase.PARACHUTE -> "PARACHUTE GLIDE"
        carjack != null -> "CARJACKING ${carjack.targetName.uppercase()}"
        isScoped -> "SCOPE ZOOM · ${equippedWeapon.displayName}"
        boat != null -> "$nexteraDistrict WATERS"
        isSwimming -> "SWIMMING · $nexteraDistrict"
        isSitting -> "SITTING · CHILL MODE"
        nearbyGunShop -> "VICE ARMS · GUN SHOP"
        nearbyMarketStallLabel != null -> "FARMTOPIA · ${nearbyMarketStallLabel!!.uppercase()}"
        currentInsideHouse != null -> {
          val floorTag =
            when {
              currentInsideHouse!!.hasRooftop && playerY >= 6.4f -> " · ROOFTOP"
              currentInsideHouse!!.floors >= 2 && playerY >= 2.6f -> " · 2F UPSTAIRS"
              else -> ""
            }
          "INSIDE ${currentInsideHouse!!.name.uppercase()}$floorTag"
        }
        CollisionSolver.isOnBridge(playerX, playerZ) ->
          if (playerX < -140f && playerZ in -42f..50f) "TURBINE RED BRIDGE" else "INTELLECT MOAT BRIDGE"
        CollisionSolver.isOnPierOrDock(playerX, playerZ) -> "GRAV LABS MARINA PIER"
        CollisionSolver.distXZ(playerX, playerZ, -48f, -118f) < 22f -> "INTELLECT MOAT FALLS"
        CollisionSolver.distXZ(playerX, playerZ, -196f, -12f) < 20f -> "TURBINE COVE FALLS"
        else -> nexteraDistrict
      }

    val panicCount =
      pedestrians.count {
        it.reactionState == NpcReactionState.FLEEING || it.reactionState == NpcReactionState.HANDS_UP_FLINCH
      }
    val friends = pedestrians.filter { it.isFriend }
    val groundElev = computeGroundHeight(playerX, playerZ, playerY)

    val vestMax =
      when (playerVestLevel) {
        4 -> LootItemKind.VEST_LV4.maxDurability.toInt()
        3 -> LootItemKind.VEST_LV3.maxDurability.toInt()
        else -> 0
      }
    val helmMax =
      when (playerHelmetLevel) {
        4 -> LootItemKind.HELMET_LV4.maxDurability.toInt()
        3 -> LootItemKind.HELMET_LV3.maxDurability.toInt()
        else -> 0
      }

    val spectatedPed =
      if (isPlayerEliminated && isSpectatingKiller) {
        pedestrians.firstOrNull { it.id == spectatedKillerPedId }
      } else {
        null
      }

    return HudSnapshot(
      isPlaying = isPlaying,
      isPaused = isPaused,
      isGunShopOpen = isGunShopOpen,
      heroPresetIndex = heroPresetIndex,
      heroPresetName = HERO_PRESETS.getOrNull(heroPresetIndex)?.first ?: "VICE HERO",
      lobbyDropZone = lobbyDropZone,
      lobbySquadMembers = lobbySquadMembers.toList(),
      isDriving = car != null || boat != null,
      isDrivingBoat = boat != null,
      isSwimming = isSwimming,
      isSitting = isSitting || sitProgress > 0.35f,
      isScoped = isScoped,
      isCarjacking = carjack != null,
      carjackTargetName = carjack?.targetName,
      dropPhase = dropPhase,
      planeCountdownSec = kotlin.math.ceil(planeTimerSec.toDouble()).toInt().coerceAtLeast(0),
      altitudeMeters = max(0f, playerY - groundElev).toInt(),
      planeX = planeX,
      planeZ = planeZ,
      planeYawRad = planeYawRad,
      playerHealth = playerHealth.toInt(),
      playerCash = playerCash,
      wantedLevel = wantedLevel,
      equippedWeapon = equippedWeapon,
      ownedWeapons = ownedWeapons.toSet(),
      activeAttackMove = activeAttackMove,
      activeParkourType = activeParkour?.type,
      nearbyParkourLabel = nearbyParkourLabel,
      activeLandingReaction = activeLandingReaction,
      lastHitBanner = lastHitBanner,
      nearbyGunShop = nearbyGunShop,
      nearbyFriendCandidateId = nearbyFriendCandidate?.id,
      friendCount = friends.size,
      panickingNpcCount = panicCount,
      npcDefeatedCount = npcDefeatedCount,
      playerX = playerX,
      playerZ = playerZ,
      playerYawRad = playerYawRad,
      cameraYawRad = camYawRad,
      speedKmh = kmh,
      isSprinting = car == null && boat == null && (input.sprintHeld || input.autoSprint) && playerSpeedNorm > 0.6f,
      isAutoSprinting = car == null && boat == null && input.autoSprint,
      isDrifting = car?.isDrifting == true,
      activeVehicleName = boat?.displayName ?: car?.displayName,
      nearbyVehicleName = nearbyVehicle?.displayName,
      nearbyVehicleOccupied = nearbyVehicle?.isAiTraffic == true || nearbyVehicle?.driverBotId != null,
      nearbyBoatName = nearbyBoat?.displayName,
      nearbyBoatOccupied = nearbyBoat?.isAiCruise == true,
      insideHouseName = currentInsideHouse?.name,
      zoneName = zone,
      timePreset = timePreset,
      cameraMode = cameraMode,
      isMuted = isMuted,
      vehiclesMapPos = vehicles.map { Triple(it.id, it.x, it.z) },
      boatsMapPos = boats.map { Triple(it.id, it.x, it.z) },
      pedestriansMapPos =
        pedestrians
          .filter { it.reactionState != NpcReactionState.KNOCKED_DOWN && !it.isFriend && !it.isRivalFighter }
          .map {
            Triple(
              it.x,
              it.z,
              it.reactionState == NpcReactionState.FLEEING || it.reactionState == NpcReactionState.HANDS_UP_FLINCH,
            )
          },
      friendsMapPos = friends.map { Pair(it.x, it.z) },
      gunShopMapPos = world.gunShopCenter,
      isBagOpen = isBagOpen,
      bagItems = bagItems.toList(),
      bagCapacity = MAX_BAG_SLOTS,
      isBagFull = bagItems.size >= MAX_BAG_SLOTS,
      medkitCount = bagItems.count { it.kind == LootItemKind.MEDKIT },
      vestLevel = playerVestLevel,
      vestDurability = playerVestDurability.toInt(),
      vestMaxDurability = vestMax,
      helmetLevel = playerHelmetLevel,
      helmetDurability = playerHelmetDurability.toInt(),
      helmetMaxDurability = helmMax,
      nearbyLootLabel = nearbyLootLabel,
      lootMapPos = worldLootItems.map { Triple(it.x, it.z, it.kind.category) },
      rivalsMapPos =
        pedestrians
          .filter { (it.isRivalFighter || it.weapon != WeaponType.FISTS) && it.reactionState != NpcReactionState.KNOCKED_DOWN && !it.isFriend }
          .map { Pair(it.x, it.z) },
      isPlayerEliminated = isPlayerEliminated,
      isSpectatingKiller = isSpectatingKiller,
      eliminatedByName = eliminatedByName,
      eliminatedByWeapon = eliminatedByWeapon,
      spectatingTargetName = spectatedPed?.let { rivalDisplayName(it) } ?: eliminatedByName,
      spectatingTargetHp = spectatedPed?.hp?.toInt() ?: 0,
      spectatingTargetWeapon = spectatedPed?.weapon ?: eliminatedByWeapon,
      lastMatchCoinsEarned = lastMatchCoinsEarned,
      isStyleShopOpen = isStyleShopOpen,
      equippedCloth = equippedCloth,
      ownedCloths = ownedCloths.toSet(),
      equippedHair = equippedHair,
      ownedHairs = ownedHairs.toSet(),
      equippedPants = equippedPants,
      ownedPants = ownedPants.toSet(),
      equippedGunSkin = equippedGunSkin,
      ownedGunSkins = ownedGunSkins.toSet(),
      equippedCarSkin = equippedCarSkin,
      ownedCarSkins = ownedCarSkins.toSet(),
      weatherMode = livingWorld.weatherMode,
      autoWeatherCycle = livingWorld.autoWeatherCycle,
      rainIntensity = livingWorld.rainIntensity,
      lightningFlash = livingWorld.lightningFlash,
      nearbyMarketStallLabel = nearbyMarketStallLabel,
      marketBotSummary = null,
      trainMapPos = if (world.railwayLoopWaypoints.size >= 2) Pair(livingWorld.trainState.x, livingWorld.trainState.z) else null,
      helicoptersMapPos = livingWorld.helicopters.map { Pair(it.x, it.z) },
      marketMapPos = world.marketCenter,
      waterfallMapPos = world.waterfallCenter,
      atollMapPos = world.atollCenter,
      glooWallCount = playerReserveGlooWalls + bagItems.count { it.kind == LootItemKind.GLOO_WALL },
      glooWallsMapPos = glooWalls.map { Pair(it.x, it.z) },
      aliveBotsCount = pedestrians.count { !it.isFriend && it.reactionState != NpcReactionState.KNOCKED_DOWN },
    )
  }

  internal fun spawnGlooWallForBot(ped: PedestrianRuntimeState, threatX: Float, threatZ: Float): Boolean {
    val dx = threatX - ped.x
    val dz = threatZ - ped.z
    val dist = hypot(dx, dz).coerceAtLeast(0.1f)
    val yaw = angleBetweenXZ(dx, dz)
    val wallX = ped.x + (dx / dist) * 2.05f
    val wallZ = ped.z + (dz / dist) * 2.05f
    val wallY = computeGroundHeight(wallX, wallZ, ped.y)
    glooWalls +=
      GlooWallState(
        id = "gloo_${++glooUidSeq}",
        ownerId = ped.id,
        x = wallX,
        y = wallY,
        z = wallZ,
        yawRad = yaw,
        isPlayerOwned = false,
      )
    hitSparks += HitSparkState(wallX, wallY + 1.0f, wallZ, floatArrayOf(0.24f, 0.90f, 1.0f), durationSec = 0.35f)
    return true
  }

  private fun stepGlooWalls(dt: Float) {
    val iter = glooWalls.iterator()
    while (iter.hasNext()) {
      val wall = iter.next()
      wall.ageSec += dt
      if (wall.ageSec >= wall.durationSec || wall.hp <= 0f) {
        iter.remove()
      }
    }
  }

  private fun stepLootRespawn(dt: Float) {
    lootRespawnTimerSec += dt
    if (lootRespawnTimerSec < 22f) return
    lootRespawnTimerSec = 0f
    if (worldLootItems.size < 22) {
      val h = world.houses[((worldTimeSec * 3f).toInt()) % world.houses.size]
      spawnLootSafely(LootItemKind.MEDKIT, h.centerX + 0.2f, h.centerZ - 2.1f, 0.4f, h.id)
      spawnLootSafely(LootItemKind.GLOO_WALL, h.centerX - 2.0f, h.centerZ - 1.8f, 0.4f, h.id)
      spawnLootSafely(LootItemKind.ASSAULT_RIFLE, h.centerX + 0.1f, h.centerZ + 1.8f, 0.4f, h.id)
    }
  }

  internal fun resolveEntityVsGlooWalls(x: Float, z: Float, radius: Float, entityY: Float): Pair<Float, Float> {
    var rx = x
    var rz = z
    for (wall in glooWalls) {
      if (wall.hp <= 0f) continue
      if (entityY > wall.y + 2.15f) continue
      val dx = rx - wall.x
      val dz = rz - wall.z
      val c = cos(wall.yawRad)
      val s = sin(wall.yawRad)
      val lx = dx * c - dz * s
      val lz = dx * s + dz * c
      val halfW = 1.65f
      val halfT = 0.28f
      val closestX = lx.coerceIn(-halfW, halfW)
      val closestZ = lz.coerceIn(-halfT, halfT)
      val px = lx - closestX
      val pz = lz - closestZ
      val dSq = px * px + pz * pz
      if (dSq >= radius * radius) continue

      var pushLx = 0f
      var pushLz = 0f
      if (dSq > 1e-6f) {
        val d = sqrt(dSq)
        val push = radius - d
        pushLx = (px / d) * push
        pushLz = (pz / d) * push
      } else {
        pushLz = if (lz >= 0f) (halfT - lz + radius) else -(halfT + lz + radius)
      }
      rx += pushLx * c + pushLz * s
      rz += -pushLx * s + pushLz * c
    }
    return Pair(rx, rz)
  }

  private fun intersectSegmentGlooWall(
    x1: Float,
    z1: Float,
    x2: Float,
    z2: Float,
    wall: GlooWallState,
  ): Float? {
    val c = cos(wall.yawRad)
    val s = sin(wall.yawRad)
    val dx1 = x1 - wall.x
    val dz1 = z1 - wall.z
    val lx1 = dx1 * c - dz1 * s
    val lz1 = dx1 * s + dz1 * c

    val dx2 = x2 - wall.x
    val dz2 = z2 - wall.z
    val lx2 = dx2 * c - dz2 * s
    val lz2 = dx2 * s + dz2 * c

    val vx = lx2 - lx1
    val vz = lz2 - lz1
    val segLen = hypot(vx, vz).coerceAtLeast(1e-5f)
    val tHit = intersectRayAabbXZ(lx1, lz1, vx / segLen, vz / segLen, -1.68f, -0.32f, 1.68f, 0.32f) ?: return null
    val frac = tHit / segLen
    return if (frac in 0f..1f) frac else null
  }

  private fun intersectSegmentCircleXZ(
    x1: Float,
    z1: Float,
    x2: Float,
    z2: Float,
    cx: Float,
    cz: Float,
    radius: Float,
  ): Float? {
    val dx = x2 - x1
    val dz = z2 - z1
    val a = dx * dx + dz * dz
    if (a < 1e-6f) {
      return if (hypot(x1 - cx, z1 - cz) <= radius) 0f else null
    }
    val fx = x1 - cx
    val fz = z1 - cz
    val b = 2f * (fx * dx + fz * dz)
    val c = fx * fx + fz * fz - radius * radius
    val disc = b * b - 4f * a * c
    if (disc < 0f) return null
    val sqrtD = sqrt(disc)
    val t1 = (-b - sqrtD) / (2f * a)
    val t2 = (-b + sqrtD) / (2f * a)
    return when {
      t1 in 0f..1f -> t1
      t2 in 0f..1f -> t2
      else -> null
    }
  }

  private fun intersectRayAabbXZ(
    ox: Float,
    oz: Float,
    dx: Float,
    dz: Float,
    minX: Float,
    minZ: Float,
    maxX: Float,
    maxZ: Float,
  ): Float? {
    var tmin = 0f
    var tmax = 1000f

    if (abs(dx) < 1e-6f) {
      if (ox < minX || ox > maxX) return null
    } else {
      val invX = 1f / dx
      var t1 = (minX - ox) * invX
      var t2 = (maxX - ox) * invX
      if (t1 > t2) {
        val tmp = t1
        t1 = t2
        t2 = tmp
      }
      tmin = max(tmin, t1)
      tmax = min(tmax, t2)
      if (tmin > tmax) return null
    }

    if (abs(dz) < 1e-6f) {
      if (oz < minZ || oz > maxZ) return null
    } else {
      val invZ = 1f / dz
      var t1 = (minZ - oz) * invZ
      var t2 = (maxZ - oz) * invZ
      if (t1 > t2) {
        val tmp = t1
        t1 = t2
        t2 = tmp
      }
      tmin = max(tmin, t1)
      tmax = min(tmax, t2)
      if (tmin > tmax) return null
    }

    return tmin
  }

  companion object {
    const val MAX_BAG_SLOTS = 8

    val HERO_PRESETS: List<Pair<String, com.example.game.world.CharacterAppearance>> =
      listOf(
        "VICE HERO" to com.example.game.world.CartoonPresets.PLAYER_HERO,
        "SAFFRON DON" to com.example.game.world.CartoonPresets.CHUBBY_SAFFRON,
        "NEON STRIKER" to com.example.game.world.CartoonPresets.LEAN_BUDDY_BLUE,
        "SKY CAPTAIN" to com.example.game.world.CartoonPresets.COASTAL_CAPTAIN,
        "SHERIFF KHAKI" to com.example.game.world.CartoonPresets.INSPECTOR_KHAKI,
        "ROYAL VIPER" to com.example.game.world.CartoonPresets.CHUBBY_PURPLE,
      )
  }
}
