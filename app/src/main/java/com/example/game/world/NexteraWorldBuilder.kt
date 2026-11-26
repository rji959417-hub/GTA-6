package com.example.game.world

import com.example.game.math.AabbCollider
import com.example.game.math.CircleCollider
import com.example.game.state.MarketStallKind
import com.example.game.state.MarketStallSpec
import com.example.game.state.WaterfallSpec
import com.example.game.state.WeaponType
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Builds the complete Nextera Battle Royale map matching the reference overhead map,
 * completely replacing the legacy archipelago. Contains zero AI traffic cars.
 */
object NexteraWorldBuilder {
  fun build(): WorldData {
    val staticBoxes = mutableListOf<PropBoxSpec>()
    val staticCylinders = mutableListOf<PropCylinderSpec>()
    val glassBoxes = mutableListOf<PropBoxSpec>()
    val aabbs = mutableListOf<AabbCollider>()
    val circles = mutableListOf<CircleCollider>()
    val walkableFloors = mutableListOf<WalkableFloorSpec>()
    val trees = mutableListOf<TreeSpec>()
    val streetLights = mutableListOf<StreetLightSpec>()
    val gabledRoofs = mutableListOf<GabledRoofSpec>()
    val boulders = mutableListOf<BoulderSpec>()
    val windTurbines = mutableListOf<WindTurbineSpec>()
    val domes = mutableListOf<DomeSpec>()

    // 1. ROAD NETWORK (matching Nextera winding highways & cross-roads)
    val roadColor = floatArrayOf(0.24f, 0.25f, 0.28f)
    val stripeColor = floatArrayOf(0.92f, 0.88f, 0.72f)
    for (seg in TerrainHeightfield.roadSegments) {
      val dx = seg.x2 - seg.x1
      val dz = seg.z2 - seg.z1
      val len = hypot(dx, dz)
      val yawDeg = (atan2(dx, dz) * 180.0 / PI).toFloat()
      val mx = (seg.x1 + seg.x2) * 0.5f
      val mz = (seg.z1 + seg.z2) * 0.5f
      staticBoxes +=
        PropBoxSpec(
          cx = mx,
          cy = 0.025f,
          cz = mz,
          sx = seg.halfWidth * 2.0f,
          sy = 0.05f,
          sz = len + 2.4f,
          color = roadColor,
          surface = MaterialSurface.ASPHALT,
          yawDeg = yawDeg,
          roughness = 0.74f,
          castShadow = false,
        )
      // Dashed center lane markers
      val dashStep = 7.5f
      var d = 3.5f
      while (d < len - 3.0f) {
        val t = d / len
        val sx = seg.x1 + dx * t
        val sz = seg.z1 + dz * t
        staticBoxes +=
          PropBoxSpec(
            cx = sx,
            cy = 0.055f,
            cz = sz,
            sx = 0.24f,
            sy = 0.015f,
            sz = 2.6f,
            color = stripeColor,
            surface = MaterialSurface.STUCCO,
            yawDeg = yawDeg,
            castShadow = false,
          )
        d += dashStep
      }
    }

    // 2. ICONIC BRIDGES (Turbine Red Suspension Bridge + 3 Intellect Center Moat Bridges)
    buildTurbineRedSuspensionBridge(staticBoxes, staticCylinders, aabbs, walkableFloors)
    buildIntellectMoatBridges(staticBoxes, aabbs, walkableFloors)

    // 3. MARINA PIER & BOAT DOCK (SW Bay near Grav Labs / Turbine Cove)
    buildMarinaBoatPier(staticBoxes, walkableFloors)

    // 4. GUN SHOP (-46, -14) & FARMTOPIA PRODUCE MARKET STALLS (-50, 24)
    WorldBuilder.buildGunShopKiosk(
      cx = -46f,
      cz = -14f,
      staticBoxes = staticBoxes,
      glassBoxes = glassBoxes,
      aabbs = aabbs,
      walkableFloors = walkableFloors,
    )
    val marketStalls = buildFarmtopiaMarket(staticBoxes, aabbs, walkableFloors)

    // 5. WATERFALLS (Turbine Cove Cascade & Intellect Moat Cliff Jharna)
    val waterfalls =
      listOf(
        WaterfallSpec("wf_turbine_cove", x = -196f, topY = 8.4f, baseY = -0.35f, z = -12f, width = 6.5f, poolRadius = 8.5f),
        WaterfallSpec("wf_intellect_moat", x = -48f, topY = 9.2f, baseY = -0.35f, z = -118f, width = 7.0f, poolRadius = 9.0f),
      )

    // 6. 10 ENTERABLE MULTI-STORY HOUSES ACROSS NEXTERA POIs
    val houses =
      listOf(
        HouseSpec(
          id = "house_farmtopia_manor",
          name = "Farmtopia Manor",
          subtitle = "Central Farm · 2F + Rooftop",
          centerX = -72f,
          centerZ = -18f,
          width = 14f,
          depth = 10f,
          wallHeight = 3.4f,
          facingSouth = true,
          primaryColor = floatArrayOf(0.94f, 0.88f, 0.76f),
          accentColor = floatArrayOf(0.84f, 0.44f, 0.24f),
          roofColor = floatArrayOf(0.68f, 0.24f, 0.18f),
          neonColor = floatArrayOf(0.96f, 0.78f, 0.28f),
          floors = 2,
          hasRooftop = true,
        ),
        HouseSpec(
          id = "house_farmtopia_cottage",
          name = "Farmtopia Barn Villa",
          subtitle = "Harvest Fields · Ground Floor",
          centerX = -72f,
          centerZ = 34f,
          width = 13f,
          depth = 10f,
          wallHeight = 3.4f,
          facingSouth = false,
          primaryColor = floatArrayOf(0.90f, 0.85f, 0.72f),
          accentColor = floatArrayOf(0.28f, 0.56f, 0.34f),
          roofColor = floatArrayOf(0.84f, 0.62f, 0.22f),
          neonColor = floatArrayOf(0.28f, 0.92f, 0.68f),
          floors = 1,
        ),
        HouseSpec(
          id = "house_museum_gallery",
          name = "Museum Gallery House",
          subtitle = "Northwest Coast · 2F + Rooftop",
          centerX = -154f,
          centerZ = -66f,
          width = 13f,
          depth = 10f,
          wallHeight = 3.4f,
          facingSouth = true,
          primaryColor = floatArrayOf(0.95f, 0.94f, 0.90f),
          accentColor = floatArrayOf(0.20f, 0.76f, 0.92f),
          roofColor = floatArrayOf(0.90f, 0.74f, 0.24f),
          neonColor = floatArrayOf(0.24f, 0.90f, 0.98f),
          floors = 2,
          hasRooftop = true,
        ),
        HouseSpec(
          id = "house_plazaria_loft",
          name = "Plazaria Courtyard Loft",
          subtitle = "South Boulevard · 2F + Rooftop",
          centerX = -66f,
          centerZ = 126f,
          width = 14f,
          depth = 11f,
          wallHeight = 3.4f,
          facingSouth = false,
          primaryColor = floatArrayOf(0.96f, 0.91f, 0.78f),
          accentColor = floatArrayOf(0.94f, 0.64f, 0.22f),
          roofColor = floatArrayOf(0.86f, 0.68f, 0.24f),
          neonColor = floatArrayOf(1.0f, 0.78f, 0.24f),
          floors = 2,
          hasRooftop = true,
        ),
        HouseSpec(
          id = "house_deca_penthouse",
          name = "Deca Square Penthouse",
          subtitle = "Pink Plaza · 2F + Rooftop",
          centerX = 24f,
          centerZ = 144f,
          width = 13f,
          depth = 10f,
          wallHeight = 3.4f,
          facingSouth = true,
          primaryColor = floatArrayOf(0.95f, 0.86f, 0.92f),
          accentColor = floatArrayOf(0.94f, 0.36f, 0.68f),
          roofColor = floatArrayOf(0.88f, 0.48f, 0.72f),
          neonColor = floatArrayOf(1.0f, 0.32f, 0.68f),
          floors = 2,
          hasRooftop = true,
        ),
        HouseSpec(
          id = "house_grav_dorm",
          name = "Grav Labs Dormitory",
          subtitle = "Southwest Reactor · Ground Floor",
          centerX = -126f,
          centerZ = 146f,
          width = 12f,
          depth = 10f,
          wallHeight = 3.4f,
          facingSouth = true,
          primaryColor = floatArrayOf(0.88f, 0.88f, 0.86f),
          accentColor = floatArrayOf(0.94f, 0.74f, 0.20f),
          roofColor = floatArrayOf(0.24f, 0.28f, 0.36f),
          neonColor = floatArrayOf(0.22f, 0.88f, 0.98f),
          floors = 1,
        ),
        HouseSpec(
          id = "house_rust_barracks",
          name = "Rust Town Barracks",
          subtitle = "East Scrapyard · Ext Stairs + Roof",
          centerX = 164f,
          centerZ = 26f,
          width = 13f,
          depth = 12f,
          wallHeight = 3.4f,
          facingSouth = true,
          primaryColor = floatArrayOf(0.84f, 0.74f, 0.58f),
          accentColor = floatArrayOf(0.90f, 0.44f, 0.22f),
          roofColor = floatArrayOf(0.46f, 0.36f, 0.28f),
          neonColor = floatArrayOf(0.98f, 0.68f, 0.22f),
          floors = 2,
          facingX = 1,
          hasRooftop = true,
        ),
        HouseSpec(
          id = "house_mortar_bunker",
          name = "Mortar Bunker House",
          subtitle = "Desert Ruins · Ground Floor",
          centerX = 148f,
          centerZ = -72f,
          width = 12f,
          depth = 10f,
          wallHeight = 3.4f,
          facingSouth = true,
          primaryColor = floatArrayOf(0.82f, 0.74f, 0.58f),
          accentColor = floatArrayOf(0.56f, 0.48f, 0.36f),
          roofColor = floatArrayOf(0.44f, 0.42f, 0.38f),
          neonColor = floatArrayOf(0.94f, 0.72f, 0.26f),
          floors = 1,
        ),
        HouseSpec(
          id = "house_mud_quarters",
          name = "Mud Site Quarters",
          subtitle = "Southeast Quarry · 2F Overlook",
          centerX = 154f,
          centerZ = 122f,
          width = 13f,
          depth = 10f,
          wallHeight = 3.4f,
          facingSouth = true,
          primaryColor = floatArrayOf(0.86f, 0.80f, 0.68f),
          accentColor = floatArrayOf(0.24f, 0.74f, 0.56f),
          roofColor = floatArrayOf(0.32f, 0.38f, 0.34f),
          neonColor = floatArrayOf(0.28f, 0.92f, 0.72f),
          floors = 2,
        ),
        HouseSpec(
          id = "house_twin_bridge_villa",
          name = "Twin Bridge Villa",
          subtitle = "Northeast Ridge · 2F + Rooftop",
          centerX = 106f,
          centerZ = -124f,
          width = 13f,
          depth = 10f,
          wallHeight = 3.4f,
          facingSouth = true,
          primaryColor = floatArrayOf(0.92f, 0.92f, 0.88f),
          accentColor = floatArrayOf(0.24f, 0.66f, 0.92f),
          roofColor = floatArrayOf(0.24f, 0.42f, 0.58f),
          neonColor = floatArrayOf(0.22f, 0.88f, 0.96f),
          floors = 2,
          hasRooftop = true,
        ),
      )

    for (h in houses) {
      when {
        h.facingX != 0 ->
          TownAndValleyBuilder.buildRooftopTownHouse(
            h = h,
            staticBoxes = staticBoxes,
            staticCylinders = staticCylinders,
            glassBoxes = glassBoxes,
            aabbs = aabbs,
            walkableFloors = walkableFloors,
          )
        h.floors >= 2 ->
          WorldBuilder.buildTwoStoryHouse(
            h = h,
            staticBoxes = staticBoxes,
            glassBoxes = glassBoxes,
            aabbs = aabbs,
            walkableFloors = walkableFloors,
          )
        else ->
          WorldBuilder.buildEnterableHouse(
            h = h,
            staticBoxes = staticBoxes,
            glassBoxes = glassBoxes,
            aabbs = aabbs,
            walkableFloors = walkableFloors,
          )
      }
    }

    // 7. SIGNATURE NEXTERA POIs, SNIPER TOWERS, FORESTS & BOULDERS
    NexteraLandmarksBuilder.buildAll(
      houses = houses,
      trees = trees,
      staticBoxes = staticBoxes,
      staticCylinders = staticCylinders,
      aabbs = aabbs,
      circles = circles,
      walkableFloors = walkableFloors,
      gabledRoofs = gabledRoofs,
      boulders = boulders,
      windTurbines = windTurbines,
      domes = domes,
    )

    // 8. STREETLIGHTS ALONG NEXTERA HIGHWAYS
    val lightSpots =
      listOf(
        Pair(-44f, 16f),
        Pair(-16f, -84f),
        Pair(-170f, -20f),
        Pair(-170f, 20f),
        Pair(-158f, 140f),
        Pair(-66f, 142f),
        Pair(24f, 172f),
        Pair(112f, 128f),
        Pair(164f, 152f),
        Pair(178f, 20f),
        Pair(152f, -66f),
        Pair(118f, -148f),
        Pair(58f, 22f),
        Pair(-178f, -80f),
      )
    for ((idx, pos) in lightSpots.withIndex()) {
      val neon =
        when (idx % 3) {
          0 -> floatArrayOf(0.22f, 0.90f, 0.98f)
          1 -> floatArrayOf(0.98f, 0.78f, 0.24f)
          else -> floatArrayOf(1.0f, 0.32f, 0.68f)
        }
      streetLights += StreetLightSpec(pos.first + 5.8f, pos.second, 0f, neon)
      circles += CircleCollider(pos.first + 5.8f, pos.second, 0.42f, "Streetlight")
    }

    // 9. PARKED PLAYER-DRIVABLE VEHICLES ONLY (ZERO AI TRAFFIC / BOT CARS!)
    val vehicleSpawns =
      listOf(
        VehicleSpawnSpec(
          id = "car_cheetah",
          displayName = "Cheetah GT",
          x = -56f,
          z = -4f,
          yawRad = 0.35f,
          bodyColor = floatArrayOf(0.95f, 0.22f, 0.38f),
          trimColor = floatArrayOf(0.14f, 0.15f, 0.18f),
          maxSpeed = 38f,
          accel = 17f,
          isAiTraffic = false,
        ),
        VehicleSpawnSpec(
          id = "car_stinger",
          displayName = "Museum Spider",
          x = -168f,
          z = -94f,
          yawRad = 1.2f,
          bodyColor = floatArrayOf(0.14f, 0.84f, 0.94f),
          trimColor = floatArrayOf(0.14f, 0.15f, 0.18f),
          maxSpeed = 37f,
          accel = 16.5f,
          isAiTraffic = false,
        ),
        VehicleSpawnSpec(
          id = "car_infernus",
          displayName = "Intellect Coupe",
          x = -10f,
          z = -152f,
          yawRad = 0f,
          bodyColor = floatArrayOf(0.96f, 0.76f, 0.20f),
          trimColor = floatArrayOf(0.14f, 0.15f, 0.18f),
          maxSpeed = 39f,
          accel = 17.5f,
          isAiTraffic = false,
        ),
        VehicleSpawnSpec(
          id = "car_comet",
          displayName = "Plazaria Turbo",
          x = -66f,
          z = 110f,
          yawRad = 1.57f,
          bodyColor = floatArrayOf(0.28f, 0.92f, 0.62f),
          trimColor = floatArrayOf(0.14f, 0.15f, 0.18f),
          maxSpeed = 36f,
          accel = 16f,
          isAiTraffic = false,
        ),
        VehicleSpawnSpec(
          id = "car_banshee",
          displayName = "Deca Neon V8",
          x = 24f,
          z = 128f,
          yawRad = 3.14f,
          bodyColor = floatArrayOf(0.96f, 0.30f, 0.72f),
          trimColor = floatArrayOf(0.95f, 0.96f, 0.98f),
          maxSpeed = 38f,
          accel = 17f,
          isAiTraffic = false,
        ),
        VehicleSpawnSpec(
          id = "car_phoenix",
          displayName = "Grav Prototype",
          x = -144f,
          z = 134f,
          yawRad = 0.8f,
          bodyColor = floatArrayOf(0.98f, 0.62f, 0.18f),
          trimColor = floatArrayOf(0.16f, 0.18f, 0.22f),
          maxSpeed = 38f,
          accel = 17f,
          isAiTraffic = false,
        ),
        VehicleSpawnSpec(
          id = "car_rust_buggy",
          displayName = "Rust Town Buggy",
          x = 174f,
          z = 4f,
          yawRad = -1.2f,
          bodyColor = floatArrayOf(0.68f, 0.42f, 0.24f),
          trimColor = floatArrayOf(0.24f, 0.26f, 0.28f),
          maxSpeed = 36f,
          accel = 16.5f,
          isAiTraffic = false,
        ),
        VehicleSpawnSpec(
          id = "car_mortar_jeep",
          displayName = "Mortar 4x4",
          x = 152f,
          z = -54f,
          yawRad = -0.4f,
          bodyColor = floatArrayOf(0.46f, 0.54f, 0.36f),
          trimColor = floatArrayOf(0.16f, 0.18f, 0.20f),
          maxSpeed = 35f,
          accel = 16f,
          isAiTraffic = false,
        ),
        VehicleSpawnSpec(
          id = "car_mud_pickup",
          displayName = "Mud Site Truck",
          x = 136f,
          z = 148f,
          yawRad = 2.2f,
          bodyColor = floatArrayOf(0.24f, 0.56f, 0.84f),
          trimColor = floatArrayOf(0.16f, 0.18f, 0.20f),
          maxSpeed = 35f,
          accel = 15.5f,
          isAiTraffic = false,
        ),
        VehicleSpawnSpec(
          id = "car_twin_coupe",
          displayName = "Twin Bridge GT",
          x = 96f,
          z = -138f,
          yawRad = -2.1f,
          bodyColor = floatArrayOf(0.94f, 0.94f, 0.96f),
          trimColor = floatArrayOf(0.22f, 0.24f, 0.28f),
          maxSpeed = 39f,
          accel = 17.5f,
          isAiTraffic = false,
        ),
      )

    // 10. MARINA DOCKED SPEEDBOATS + OFFSHORE PATROL BOAT
    val boatSpawns =
      listOf(
        BoatSpawnSpec(
          id = "boat_nextera_dock_1",
          displayName = "Cove Jet Boat",
          x = -205.5f,
          z = 95.5f,
          yawRad = -0.45f,
          hullColor = floatArrayOf(0.96f, 0.96f, 0.98f),
          accentColor = floatArrayOf(0.16f, 0.88f, 0.96f),
          maxSpeed = 31f,
          isAiCruise = false,
        ),
        BoatSpawnSpec(
          id = "boat_nextera_dock_2",
          displayName = "Coral Cruiser",
          x = -191.5f,
          z = 95.5f,
          yawRad = -0.35f,
          hullColor = floatArrayOf(0.96f, 0.30f, 0.58f),
          accentColor = floatArrayOf(0.98f, 0.82f, 0.24f),
          maxSpeed = 32f,
          isAiCruise = false,
        ),
        BoatSpawnSpec(
          id = "boat_offshore_patrol",
          displayName = "South Horizon Yacht",
          x = 0f,
          z = 262f,
          yawRad = 1.57f,
          hullColor = floatArrayOf(0.96f, 0.96f, 0.98f),
          accentColor = floatArrayOf(0.18f, 0.84f, 0.96f),
          maxSpeed = 24f,
          isAiCruise = true,
          routeRadiusX = 145f,
          routeRadiusZ = 24f,
          routePhase = 0f,
          routeCenterX = 0f,
        ),
      )

    // 11. FREE FIRE BR RIVAL BOTS & DIVERSE ISLAND FIGHTERS (ON FOOT, NO CAR DRIVING!)
    val pedestrianSpawns =
      listOf(
        PedestrianSpawnSpec(
          id = "ped_farmtopia_west",
          waypoints = listOf(Pair(-88f, -4f), Pair(-62f, -4f), Pair(-62f, 22f), Pair(-88f, 22f)),
          shirtColor = floatArrayOf(0.95f, 0.42f, 0.24f),
          pantsColor = floatArrayOf(0.18f, 0.22f, 0.32f),
          appearance = CartoonPresets.CHUBBY_CORAL,
          isRivalFighter = true,
          rivalWeapon = WeaponType.SMG_MICRO,
          vestLevel = 3,
          helmetLevel = 3,
        ),
        PedestrianSpawnSpec(
          id = "ped_museum_guard",
          waypoints = listOf(Pair(-172f, -66f), Pair(-154f, -84f), Pair(-140f, -68f), Pair(-162f, -54f)),
          shirtColor = floatArrayOf(0.24f, 0.78f, 0.96f),
          pantsColor = floatArrayOf(0.18f, 0.20f, 0.26f),
          appearance = CartoonPresets.LEAN_BUDDY_BLUE,
          isRivalFighter = true,
          rivalWeapon = WeaponType.ASSAULT_RIFLE,
          vestLevel = 3,
          helmetLevel = 3,
        ),
        PedestrianSpawnSpec(
          id = "ped_intellect_sniper",
          waypoints = listOf(Pair(-34f, -158f), Pair(-2f, -158f), Pair(-2f, -198f), Pair(-34f, -198f)),
          shirtColor = floatArrayOf(0.94f, 0.78f, 0.24f),
          pantsColor = floatArrayOf(0.20f, 0.22f, 0.28f),
          appearance = CartoonPresets.INSPECTOR_KHAKI,
          isRivalFighter = true,
          rivalWeapon = WeaponType.SNIPER_RIFLE,
          vestLevel = 4,
          helmetLevel = 4,
        ),
        PedestrianSpawnSpec(
          id = "ped_deca_rusher",
          waypoints = listOf(Pair(10f, 152f), Pair(38f, 152f), Pair(38f, 184f), Pair(10f, 184f)),
          shirtColor = floatArrayOf(0.96f, 0.28f, 0.64f),
          pantsColor = floatArrayOf(0.18f, 0.18f, 0.24f),
          appearance = CartoonPresets.ATHLETIC_MINT,
          isRivalFighter = true,
          rivalWeapon = WeaponType.SHOTGUN,
          vestLevel = 3,
          helmetLevel = 3,
        ),
        PedestrianSpawnSpec(
          id = "ped_plazaria_patrol",
          waypoints = listOf(Pair(-82f, 138f), Pair(-50f, 138f), Pair(-50f, 166f), Pair(-82f, 166f)),
          shirtColor = floatArrayOf(0.28f, 0.88f, 0.56f),
          pantsColor = floatArrayOf(0.22f, 0.24f, 0.30f),
          appearance = CartoonPresets.CHUBBY_EMERALD,
          isRivalFighter = true,
          rivalWeapon = WeaponType.SMG_MICRO,
          vestLevel = 3,
        ),
        PedestrianSpawnSpec(
          id = "ped_grav_scientist",
          waypoints = listOf(Pair(-140f, 152f), Pair(-120f, 152f), Pair(-120f, 174f), Pair(-140f, 174f)),
          shirtColor = floatArrayOf(0.96f, 0.64f, 0.20f),
          pantsColor = floatArrayOf(0.24f, 0.26f, 0.32f),
          appearance = CartoonPresets.CHUBBY_PURPLE,
          isRivalFighter = true,
          rivalWeapon = WeaponType.ASSAULT_RIFLE,
          vestLevel = 4,
          helmetLevel = 3,
        ),
        PedestrianSpawnSpec(
          id = "ped_rust_scrapper",
          waypoints = listOf(Pair(170f, 8f), Pair(196f, 8f), Pair(196f, 38f), Pair(170f, 38f)),
          shirtColor = floatArrayOf(0.84f, 0.34f, 0.22f),
          pantsColor = floatArrayOf(0.24f, 0.22f, 0.20f),
          appearance = CartoonPresets.CHUBBY_SAFFRON,
          isRivalFighter = true,
          rivalWeapon = WeaponType.SHOTGUN,
          vestLevel = 3,
          helmetLevel = 4,
        ),
        PedestrianSpawnSpec(
          id = "ped_mortar_ranger",
          waypoints = listOf(Pair(144f, -54f), Pair(176f, -54f), Pair(176f, -84f), Pair(144f, -84f)),
          shirtColor = floatArrayOf(0.46f, 0.68f, 0.38f),
          pantsColor = floatArrayOf(0.28f, 0.26f, 0.22f),
          appearance = CartoonPresets.LEAN_BUDDY_GOLD,
          isRivalFighter = true,
          rivalWeapon = WeaponType.ASSAULT_RIFLE,
          vestLevel = 3,
          helmetLevel = 3,
        ),
        PedestrianSpawnSpec(
          id = "ped_mud_excavator",
          waypoints = listOf(Pair(140f, 134f), Pair(168f, 134f), Pair(168f, 160f), Pair(140f, 160f)),
          shirtColor = floatArrayOf(0.24f, 0.80f, 0.72f),
          pantsColor = floatArrayOf(0.20f, 0.24f, 0.28f),
          appearance = CartoonPresets.COASTAL_CAPTAIN,
          isRivalFighter = true,
          rivalWeapon = WeaponType.PISTOL_9MM,
          vestLevel = 3,
        ),
        PedestrianSpawnSpec(
          id = "ped_twin_scout",
          waypoints = listOf(Pair(98f, -138f), Pair(126f, -138f), Pair(126f, -158f), Pair(98f, -158f)),
          shirtColor = floatArrayOf(0.86f, 0.32f, 0.44f),
          pantsColor = floatArrayOf(0.18f, 0.20f, 0.26f),
          appearance = CartoonPresets.LEAN_BUDDY_BLUE,
          isRivalFighter = true,
          rivalWeapon = WeaponType.SMG_MICRO,
          vestLevel = 3,
          helmetLevel = 3,
        ),
        // Regular on-foot Nextera Battle Royale looters & townsfolk (recruitable / house-looting bots)
        PedestrianSpawnSpec(
          id = "ped_chubby_coral",
          waypoints = listOf(Pair(-42f, 18f), Pair(12f, 18f)),
          shirtColor = floatArrayOf(0.95f, 0.42f, 0.24f),
          pantsColor = floatArrayOf(0.18f, 0.22f, 0.32f),
          appearance = CartoonPresets.CHUBBY_CORAL,
        ),
        PedestrianSpawnSpec(
          id = "ped_farm_harvester",
          waypoints = listOf(Pair(-68f, 4f), Pair(-52f, 4f), Pair(-52f, 34f), Pair(-68f, 34f)),
          shirtColor = floatArrayOf(0.30f, 0.78f, 0.42f),
          pantsColor = floatArrayOf(0.22f, 0.24f, 0.28f),
          appearance = CartoonPresets.CHUBBY_EMERALD,
        ),
        PedestrianSpawnSpec(
          id = "ped_turbine_tech",
          waypoints = listOf(Pair(-220f, -14f), Pair(-206f, -14f), Pair(-206f, 22f), Pair(-220f, 22f)),
          shirtColor = floatArrayOf(0.24f, 0.76f, 0.94f),
          pantsColor = floatArrayOf(0.18f, 0.22f, 0.28f),
          appearance = CartoonPresets.LEAN_BUDDY_BLUE,
        ),
        PedestrianSpawnSpec(
          id = "ped_zipway_runner",
          waypoints = listOf(Pair(56f, 8f), Pair(88f, 8f), Pair(88f, 36f), Pair(56f, 36f)),
          shirtColor = floatArrayOf(0.96f, 0.76f, 0.22f),
          pantsColor = floatArrayOf(0.20f, 0.20f, 0.26f),
          appearance = CartoonPresets.INSPECTOR_KHAKI,
        ),
        PedestrianSpawnSpec(
          id = "ped_deca_dancer",
          waypoints = listOf(Pair(14f, 160f), Pair(36f, 160f), Pair(36f, 184f), Pair(14f, 184f)),
          shirtColor = floatArrayOf(0.98f, 0.34f, 0.70f),
          pantsColor = floatArrayOf(0.22f, 0.20f, 0.28f),
          appearance = CartoonPresets.ATHLETIC_MINT,
        ),
        PedestrianSpawnSpec(
          id = "ped_museum_visitor",
          waypoints = listOf(Pair(-172f, -92f), Pair(-152f, -92f), Pair(-152f, -72f), Pair(-172f, -72f)),
          shirtColor = floatArrayOf(0.70f, 0.44f, 0.96f),
          pantsColor = floatArrayOf(0.18f, 0.18f, 0.24f),
          appearance = CartoonPresets.CHUBBY_PURPLE,
        ),
        PedestrianSpawnSpec(
          id = "ped_plazaria_shopper",
          waypoints = listOf(Pair(-78f, 146f), Pair(-54f, 146f), Pair(-54f, 166f), Pair(-78f, 166f)),
          shirtColor = floatArrayOf(0.96f, 0.62f, 0.20f),
          pantsColor = floatArrayOf(0.20f, 0.22f, 0.28f),
          appearance = CartoonPresets.COASTAL_CAPTAIN,
        ),
        PedestrianSpawnSpec(
          id = "ped_intellect_cadet",
          waypoints = listOf(Pair(-28f, -166f), Pair(-8f, -166f), Pair(-8f, -190f), Pair(-28f, -190f)),
          shirtColor = floatArrayOf(0.94f, 0.78f, 0.28f),
          pantsColor = floatArrayOf(0.22f, 0.22f, 0.26f),
          appearance = CartoonPresets.LEAN_BUDDY_GOLD,
        ),
        PedestrianSpawnSpec(
          id = "ped_rust_scavenger",
          waypoints = listOf(Pair(158f, 14f), Pair(184f, 14f), Pair(184f, 38f), Pair(158f, 38f)),
          shirtColor = floatArrayOf(0.86f, 0.38f, 0.24f),
          pantsColor = floatArrayOf(0.24f, 0.22f, 0.20f),
          appearance = CartoonPresets.CHUBBY_SAFFRON,
        ),
        PedestrianSpawnSpec(
          id = "ped_mud_surveyor",
          waypoints = listOf(Pair(144f, 138f), Pair(166f, 138f), Pair(166f, 158f), Pair(144f, 158f)),
          shirtColor = floatArrayOf(0.88f, 0.28f, 0.36f),
          pantsColor = floatArrayOf(0.18f, 0.20f, 0.26f),
          appearance = CartoonPresets.CHUBBY_CORAL,
        ),
      )

    val roadLoop =
      listOf(
        Pair(-178f, -80f),
        Pair(-170f, 46f),
        Pair(-156f, 166f),
        Pair(-66f, 198f),
        Pair(24f, 204f),
        Pair(164f, 152f),
        Pair(178f, 20f),
        Pair(152f, -66f),
        Pair(118f, -148f),
        Pair(14f, -122f),
        Pair(-96f, -92f),
      )

    return WorldData(
      houses = houses,
      trees = trees,
      streetLights = streetLights,
      staticBoxes = staticBoxes,
      staticCylinders = staticCylinders,
      glassBoxes = glassBoxes,
      aabbColliders = aabbs,
      circleColliders = circles,
      walkableFloors = walkableFloors,
      vehicleSpawns = vehicleSpawns,
      boatSpawns = boatSpawns,
      roadLoopWaypointsOuter = roadLoop,
      roadLoopWaypointsInner = roadLoop,
      roadLoopWaypointsUptown = roadLoop,
      roadLoopWaypointsTown = roadLoop,
      pedestrianSpawns = pedestrianSpawns,
      playerSpawnX = -56f,
      playerSpawnZ = 8f,
      playerSpawnYaw = 0.25f,
      gunShopCenter = Pair(-46f, -14f),
      gunShopBuyPos = Pair(-46f, -8.8f),
      waterfalls = waterfalls,
      marketStalls = marketStalls,
      railwayLoopWaypoints = emptyList(),
      marketBotRoutes = emptyList(),
      marketCenter = Pair(-50f, 24f),
      waterfallCenter = Pair(-48f, -104f),
      atollCenter = Pair(-218f, 6f),
      gabledRoofs = gabledRoofs,
      boulders = boulders,
      windTurbines = windTurbines,
      domes = domes,
    )
  }

  private fun buildTurbineRedSuspensionBridge(
    staticBoxes: MutableList<PropBoxSpec>,
    staticCylinders: MutableList<PropCylinderSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
  ) {
    val bx = -170f
    val deckRed = floatArrayOf(0.76f, 0.20f, 0.18f)
    val darkRoad = floatArrayOf(0.24f, 0.25f, 0.28f)
    val glowCyan = floatArrayOf(0.22f, 0.90f, 0.96f)

    // Span deck across Turbine Cove (z = -38..46)
    staticBoxes += PropBoxSpec(bx, 0.10f, 4f, 9.6f, 0.36f, 84f, darkRoad, MaterialSurface.ASPHALT, castShadow = false)
    walkableFloors += WalkableFloorSpec(bx - 4.8f, -38f, bx + 4.8f, 46f, 0.28f, tag = "Turbine Red Bridge")

    // Red Suspension Side Trusses & Bridge Towers
    for (sx in listOf(bx - 4.8f, bx + 4.8f)) {
      staticBoxes += PropBoxSpec(sx, 0.72f, 4f, 0.45f, 1.0f, 84f, deckRed, MaterialSurface.SLATE, metallic = 0.4f)
      aabbs += AabbCollider(sx - 0.3f, -38f, sx + 0.3f, 46f, 0f, 1.4f, "Red Bridge Rail")
      for (tz in listOf(-22f, 28f)) {
        staticBoxes += PropBoxSpec(sx, 4.6f, tz, 0.75f, 9.2f, 0.75f, deckRed, MaterialSurface.SLATE)
        staticBoxes += PropBoxSpec(sx, 9.3f, tz, 0.55f, 0.4f, 0.55f, glowCyan, MaterialSurface.EMISSIVE, emissive = 0.9f, castShadow = false)
      }
    }
    // Overhead Red Crossbars on Towers
    for (tz in listOf(-22f, 28f)) {
      staticBoxes += PropBoxSpec(bx, 8.2f, tz, 10.2f, 0.65f, 0.65f, deckRed, MaterialSurface.SLATE)
    }
    // Support Piers standing in water
    for (pz in listOf(-22f, 4f, 28f)) {
      staticCylinders += PropCylinderSpec(bx - 3.8f, -0.7f, pz, 0.8f, 1.6f, floatArrayOf(0.42f, 0.44f, 0.46f), MaterialSurface.SLATE)
      staticCylinders += PropCylinderSpec(bx + 3.8f, -0.7f, pz, 0.8f, 1.6f, floatArrayOf(0.42f, 0.44f, 0.46f), MaterialSurface.SLATE)
    }
  }

  private fun buildIntellectMoatBridges(
    staticBoxes: MutableList<PropBoxSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
  ) {
    val bridgeDeck = floatArrayOf(0.32f, 0.34f, 0.38f)
    val railLight = floatArrayOf(0.84f, 0.82f, 0.76f)

    // 1. South Center Bridge (-16, -132..-84)
    staticBoxes += PropBoxSpec(-16f, 0.12f, -108f, 9.8f, 0.36f, 50f, bridgeDeck, MaterialSurface.ASPHALT, castShadow = false)
    walkableFloors += WalkableFloorSpec(-20.9f, -133f, -11.1f, -83f, 0.30f, tag = "Intellect South Bridge")
    for (sx in listOf(-20.9f, -11.1f)) {
      staticBoxes += PropBoxSpec(sx, 0.68f, -108f, 0.42f, 0.9f, 50f, railLight, MaterialSurface.STUCCO)
      aabbs += AabbCollider(sx - 0.25f, -133f, sx + 0.25f, -83f, 0f, 1.3f, "Intellect Bridge Rail")
    }

    // 2. SW & East Moat Bridges (angled spans)
    val angledSpans =
      listOf(
        floatArrayOf(-96f, -92f, -58f, -142f),
        floatArrayOf(66f, -148f, 24f, -162f),
      )
    for (sp in angledSpans) {
      val mx = (sp[0] + sp[2]) * 0.5f
      val mz = (sp[1] + sp[3]) * 0.5f
      val len = hypot(sp[2] - sp[0], sp[3] - sp[1])
      val yawDeg = (atan2(sp[2] - sp[0], sp[3] - sp[1]) * 180.0 / PI).toFloat()
      staticBoxes += PropBoxSpec(mx, 0.12f, mz, 9.2f, 0.36f, len + 2f, bridgeDeck, MaterialSurface.ASPHALT, yawDeg = yawDeg, castShadow = false)
      walkableFloors += WalkableFloorSpec(minOf(sp[0], sp[2]) - 4.5f, minOf(sp[1], sp[3]) - 4.5f, maxOf(sp[0], sp[2]) + 4.5f, maxOf(sp[1], sp[3]) + 4.5f, 0.30f, tag = "Intellect Side Bridge")
    }
  }

  private fun buildMarinaBoatPier(
    staticBoxes: MutableList<PropBoxSpec>,
    walkableFloors: MutableList<WalkableFloorSpec>,
  ) {
    val wood = floatArrayOf(0.56f, 0.38f, 0.24f)
    staticBoxes += PropBoxSpec(-198f, 0.12f, 76f, 5.6f, 0.24f, 30f, wood, MaterialSurface.WOOD)
    staticBoxes += PropBoxSpec(-198f, 0.12f, 90.2f, 16.5f, 0.24f, 5.2f, wood, MaterialSurface.WOOD)
    walkableFloors += WalkableFloorSpec(-201f, 61f, -195f, 92.8f, 0.24f, tag = "Nextera Marina Pier")
    walkableFloors += WalkableFloorSpec(-206.5f, 87.5f, -189.5f, 92.9f, 0.24f, tag = "Nextera Marina Dock")
    for (px in listOf(-205.5f, -190.5f)) {
      staticBoxes += PropBoxSpec(px, 1.0f, 92.0f, 0.32f, 1.6f, 0.32f, floatArrayOf(0.20f, 0.22f, 0.26f), MaterialSurface.SLATE)
      staticBoxes += PropBoxSpec(px, 1.9f, 92.0f, 0.38f, 0.26f, 0.38f, floatArrayOf(0.18f, 0.94f, 0.96f), MaterialSurface.EMISSIVE, emissive = 0.95f, castShadow = false)
    }
  }

  private fun buildFarmtopiaMarket(
    staticBoxes: MutableList<PropBoxSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
  ): List<MarketStallSpec> {
    val cx = -50f
    val cz = 24f
    staticBoxes += PropBoxSpec(cx, 0.06f, cz, 18f, 0.12f, 14f, floatArrayOf(0.76f, 0.70f, 0.60f), MaterialSurface.TILE, castShadow = false)
    walkableFloors += WalkableFloorSpec(cx - 9f, cz - 7f, cx + 9f, cz + 7f, 0.12f, tag = "Farmtopia Produce Market")

    val specs =
      listOf(
        MarketStallSpec("stall_veg", "Farmtopia Fresh Veg", MarketStallKind.VEGETABLES, cx - 5.2f, cz - 3.6f, cx - 5.2f, cz - 1.2f, 0f),
        MarketStallSpec("stall_fruit", "Harvest Fruit Stand", MarketStallKind.FRUITS, cx, cz - 3.6f, cx, cz - 1.2f, 0f),
        MarketStallSpec("stall_spice", "Village Spice Booth", MarketStallKind.SPICES, cx + 5.2f, cz - 3.6f, cx + 5.2f, cz - 1.2f, 0f),
        MarketStallSpec("stall_bakery", "Farmhouse Bakery", MarketStallKind.BAKERY, cx - 5.2f, cz + 3.6f, cx - 5.2f, cz + 1.2f, 180f),
        MarketStallSpec("stall_pottery", "Clay Craft Stall", MarketStallKind.POTTERY, cx, cz + 3.6f, cx, cz + 1.2f, 180f),
        MarketStallSpec("stall_cloth", "Custom Gear Tent", MarketStallKind.TEXTILES, cx + 5.2f, cz + 3.6f, cx + 5.2f, cz + 1.2f, 180f),
      )

    for (st in specs) {
      val wood = floatArrayOf(0.52f, 0.34f, 0.20f)
      staticBoxes += PropBoxSpec(st.x, 0.55f, st.z, 3.4f, 0.95f, 1.6f, wood, MaterialSurface.WOOD)
      staticBoxes += PropBoxSpec(st.x, 2.35f, st.z, 3.8f, 0.18f, 2.3f, st.kind.canopyColor, MaterialSurface.CLOTH)
      staticBoxes += PropBoxSpec(st.x, 1.15f, st.z, 2.2f, 0.32f, 1.0f, st.kind.crateColor, MaterialSurface.WOOD)
      aabbs += AabbCollider(st.x - 1.8f, st.z - 0.9f, st.x + 1.8f, st.z + 0.9f, 0f, 1.3f, st.name)
    }
    return specs
  }
}
