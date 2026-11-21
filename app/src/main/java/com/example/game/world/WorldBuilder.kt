package com.example.game.world

import com.example.game.math.AabbCollider
import com.example.game.math.CircleCollider
import kotlin.math.PI

object WorldBuilder {
  fun buildKeyBayside(): WorldData {
    val staticBoxes = mutableListOf<PropBoxSpec>()
    val glassBoxes = mutableListOf<PropBoxSpec>()
    val aabbs = mutableListOf<AabbCollider>()
    val circles = mutableListOf<CircleCollider>()

    // ---------------------------------------------------------
    // 1. ISLAND TERRAIN, BEACH, ROADS & CENTRAL PLAZA
    // ---------------------------------------------------------
    staticBoxes +=
      PropBoxSpec(
        cx = 0f,
        cy = -0.30f,
        cz = -4f,
        sx = 128f,
        sy = 0.60f,
        sz = 120f,
        color = floatArrayOf(0.24f, 0.48f, 0.25f),
        surface = MaterialSurface.GRASS,
        roughness = 0.85f,
        castShadow = false,
      )

    staticBoxes +=
      PropBoxSpec(
        cx = 0f,
        cy = -0.34f,
        cz = 59.5f,
        sx = 130f,
        sy = 0.56f,
        sz = 13f,
        color = floatArrayOf(0.88f, 0.77f, 0.56f),
        surface = MaterialSurface.SAND,
        roughness = 0.90f,
        castShadow = false,
      )

    staticBoxes +=
      PropBoxSpec(
        cx = 0f,
        cy = 0.04f,
        cz = 52.2f,
        sx = 114f,
        sy = 0.14f,
        sz = 3.2f,
        color = floatArrayOf(0.56f, 0.39f, 0.25f),
        surface = MaterialSurface.WOOD,
        roughness = 0.72f,
        castShadow = false,
      )

    // Wooden Ocean Pier extending south over the water at x = 22
    staticBoxes +=
      PropBoxSpec(
        cx = 22f,
        cy = 0.12f,
        cz = 69f,
        sx = 5.4f,
        sy = 0.24f,
        sz = 31f,
        color = floatArrayOf(0.52f, 0.35f, 0.22f),
        surface = MaterialSurface.WOOD,
        roughness = 0.75f,
      )
    // T-shaped Boat Boarding Dock Platform at Pier End (z = 82)
    staticBoxes +=
      PropBoxSpec(
        cx = 22f,
        cy = 0.12f,
        cz = 82.5f,
        sx = 12.6f,
        sy = 0.24f,
        sz = 4.6f,
        color = floatArrayOf(0.56f, 0.38f, 0.24f),
        surface = MaterialSurface.WOOD,
        roughness = 0.74f,
      )
    // Neon Marina Beacon Posts on Boat Dock
    for (px in listOf(16.2f, 27.8f)) {
      staticBoxes +=
        PropBoxSpec(
          cx = px,
          cy = 0.85f,
          cz = 83.8f,
          sx = 0.32f,
          sy = 1.4f,
          sz = 0.32f,
          color = floatArrayOf(0.18f, 0.20f, 0.24f),
          surface = MaterialSurface.SLATE,
        )
      staticBoxes +=
        PropBoxSpec(
          cx = px,
          cy = 1.62f,
          cz = 83.8f,
          sx = 0.36f,
          sy = 0.22f,
          sz = 0.36f,
          color = floatArrayOf(0.18f, 0.94f, 0.96f),
          surface = MaterialSurface.EMISSIVE,
          emissive = 0.95f,
          castShadow = false,
        )
    }

    for (pz in listOf(58f, 65f, 72f, 79f, 83.5f)) {
      for (px in listOf(19.6f, 24.4f)) {
        staticBoxes +=
          PropBoxSpec(
            cx = px,
            cy = -0.55f,
            cz = pz,
            sx = 0.42f,
            sy = 1.8f,
            sz = 0.42f,
            color = floatArrayOf(0.32f, 0.22f, 0.14f),
            surface = MaterialSurface.WOOD,
          )
      }
    }

    // Road Network: Sunset Blvd Loop
    val roadColor = floatArrayOf(0.18f, 0.19f, 0.22f)
    staticBoxes +=
      PropBoxSpec(
        cx = 0f,
        cy = 0.015f,
        cz = 16f,
        sx = 94f,
        sy = 0.05f,
        sz = 10f,
        color = roadColor,
        surface = MaterialSurface.ASPHALT,
        roughness = 0.72f,
        castShadow = false,
      )
    staticBoxes +=
      PropBoxSpec(
        cx = 0f,
        cy = 0.015f,
        cz = -24f,
        sx = 94f,
        sy = 0.05f,
        sz = 10f,
        color = roadColor,
        surface = MaterialSurface.ASPHALT,
        roughness = 0.72f,
        castShadow = false,
      )
    staticBoxes +=
      PropBoxSpec(
        cx = -42f,
        cy = 0.015f,
        cz = -4f,
        sx = 10f,
        sy = 0.05f,
        sz = 50f,
        color = roadColor,
        surface = MaterialSurface.ASPHALT,
        roughness = 0.72f,
        castShadow = false,
      )
    staticBoxes +=
      PropBoxSpec(
        cx = 42f,
        cy = 0.015f,
        cz = -4f,
        sx = 10f,
        sy = 0.05f,
        sz = 50f,
        color = roadColor,
        surface = MaterialSurface.ASPHALT,
        roughness = 0.72f,
        castShadow = false,
      )
    staticBoxes +=
      PropBoxSpec(
        cx = 0f,
        cy = 0.015f,
        cz = -4f,
        sx = 9.2f,
        sy = 0.05f,
        sz = 34f,
        color = roadColor,
        surface = MaterialSurface.ASPHALT,
        roughness = 0.72f,
        castShadow = false,
      )

    val stripeColor = floatArrayOf(0.95f, 0.78f, 0.25f)
    for (x in -34..34 step 6) {
      if (x !in -4..4) {
        staticBoxes +=
          PropBoxSpec(
            cx = x.toFloat(),
            cy = 0.045f,
            cz = 16f,
            sx = 2.8f,
            sy = 0.015f,
            sz = 0.24f,
            color = stripeColor,
            surface = MaterialSurface.STUCCO,
            castShadow = false,
          )
        staticBoxes +=
          PropBoxSpec(
            cx = x.toFloat(),
            cy = 0.045f,
            cz = -24f,
            sx = 2.8f,
            sy = 0.015f,
            sz = 0.24f,
            color = stripeColor,
            surface = MaterialSurface.STUCCO,
            castShadow = false,
          )
      }
    }
    for (z in -16..8 step 6) {
      staticBoxes +=
        PropBoxSpec(
          cx = -42f,
          cy = 0.045f,
          cz = z.toFloat(),
          sx = 0.24f,
          sy = 0.015f,
          sz = 2.8f,
          color = stripeColor,
          surface = MaterialSurface.STUCCO,
          castShadow = false,
        )
      staticBoxes +=
        PropBoxSpec(
          cx = 42f,
          cy = 0.045f,
          cz = z.toFloat(),
          sx = 0.24f,
          sy = 0.015f,
          sz = 2.8f,
          color = stripeColor,
          surface = MaterialSurface.STUCCO,
          castShadow = false,
        )
    }

    // Central Bayside Plaza Roundabout & Neon Fountain at (0, -4)
    staticBoxes +=
      PropBoxSpec(
        cx = 0f,
        cy = 0.12f,
        cz = -4f,
        sx = 12f,
        sy = 0.24f,
        sz = 12f,
        color = floatArrayOf(0.76f, 0.72f, 0.68f),
        surface = MaterialSurface.TILE,
      )
    staticBoxes +=
      PropBoxSpec(
        cx = 0f,
        cy = 0.48f,
        cz = -4f,
        sx = 5.4f,
        sy = 0.60f,
        sz = 5.4f,
        color = floatArrayOf(0.85f, 0.82f, 0.78f),
        surface = MaterialSurface.STUCCO,
      )
    staticBoxes +=
      PropBoxSpec(
        cx = 0f,
        cy = 0.76f,
        cz = -4f,
        sx = 4.4f,
        sy = 0.16f,
        sz = 4.4f,
        color = floatArrayOf(0.10f, 0.65f, 0.80f),
        surface = MaterialSurface.WATER,
        roughness = 0.15f,
      )
    staticBoxes +=
      PropBoxSpec(
        cx = 0f,
        cy = 1.65f,
        cz = -4f,
        sx = 1.1f,
        sy = 1.8f,
        sz = 1.1f,
        color = floatArrayOf(0.95f, 0.25f, 0.62f),
        surface = MaterialSurface.EMISSIVE,
        emissive = 0.75f,
      )
    aabbs += AabbCollider(-2.8f, -6.8f, 2.8f, -1.2f, tag = "Bayside Fountain")

    // ---------------------------------------------------------
    // 2. THREE DISTINCT ENTERABLE HOUSES
    // ---------------------------------------------------------
    val houses =
      listOf(
        HouseSpec(
          id = "house_villa",
          name = "Oceanview Modern Villa",
          subtitle = "Beachfront Stucco · Infinity Pool",
          centerX = -21f,
          centerZ = 33f,
          width = 13f,
          depth = 10f,
          wallHeight = 4.2f,
          facingSouth = false,
          primaryColor = floatArrayOf(0.94f, 0.91f, 0.87f),
          accentColor = floatArrayOf(0.96f, 0.45f, 0.36f),
          roofColor = floatArrayOf(0.22f, 0.23f, 0.28f),
          neonColor = floatArrayOf(0.18f, 0.92f, 0.94f),
          hasPool = true,
        ),
        HouseSpec(
          id = "house_artdeco",
          name = "Flamingo Art-Deco House",
          subtitle = "Pastel Coral · Palm Driveway",
          centerX = -22f,
          centerZ = -41f,
          width = 11.5f,
          depth = 9.5f,
          wallHeight = 4.0f,
          facingSouth = true,
          primaryColor = floatArrayOf(0.97f, 0.70f, 0.74f),
          accentColor = floatArrayOf(0.32f, 0.88f, 0.78f),
          roofColor = floatArrayOf(0.88f, 0.84f, 0.78f),
          neonColor = floatArrayOf(1.0f, 0.26f, 0.65f),
        ),
        HouseSpec(
          id = "house_estate",
          name = "Cedar & Glass Estate",
          subtitle = "Timber Lounge · Carport Bay",
          centerX = 24f,
          centerZ = -41f,
          width = 13f,
          depth = 10f,
          wallHeight = 4.5f,
          facingSouth = true,
          primaryColor = floatArrayOf(0.24f, 0.25f, 0.30f),
          accentColor = floatArrayOf(0.68f, 0.42f, 0.24f),
          roofColor = floatArrayOf(0.16f, 0.17f, 0.21f),
          neonColor = floatArrayOf(1.0f, 0.75f, 0.24f),
        ),
      )

    for (h in houses) {
      buildEnterableHouse(h, staticBoxes, glassBoxes, aabbs)
    }

    for (ux in listOf(-32f, -12f, 8f, 34f)) {
      staticBoxes +=
        PropBoxSpec(
          cx = ux,
          cy = 1.25f,
          cz = 56.5f,
          sx = 0.14f,
          sy = 2.5f,
          sz = 0.14f,
          color = floatArrayOf(0.85f, 0.85f, 0.88f),
          surface = MaterialSurface.SLATE,
        )
      staticBoxes +=
        PropBoxSpec(
          cx = ux,
          cy = 2.55f,
          cz = 56.5f,
          sx = 2.8f,
          sy = 0.25f,
          sz = 2.8f,
          color =
            if (ux < 0f) floatArrayOf(0.98f, 0.35f, 0.58f) else floatArrayOf(0.16f, 0.82f, 0.88f),
          surface = MaterialSurface.STUCCO,
        )
      circles += CircleCollider(ux, 56.5f, 0.35f, "Beach Umbrella")
    }

    for (bx in listOf(-14f, 6f, 32f)) {
      staticBoxes +=
        PropBoxSpec(
          cx = bx,
          cy = 0.36f,
          cz = 50.8f,
          sx = 2.4f,
          sy = 0.42f,
          sz = 0.8f,
          color = floatArrayOf(0.62f, 0.40f, 0.22f),
          surface = MaterialSurface.WOOD,
        )
      aabbs += AabbCollider(bx - 1.25f, 50.35f, bx + 1.25f, 51.25f, tag = "Boardwalk Bench")
    }

    // ---------------------------------------------------------
    // 3. TREES (PALM TREES + TROPICAL SHADE TREES)
    // ---------------------------------------------------------
    val trees =
      listOf(
        TreeSpec(-52f, 49f, 7.6f, 15f, TreeKind.PALM),
        TreeSpec(-40f, 49f, 8.2f, 75f, TreeKind.PALM),
        TreeSpec(-2f, 49f, 7.8f, 140f, TreeKind.PALM),
        TreeSpec(12f, 49f, 8.6f, 210f, TreeKind.PALM),
        TreeSpec(32f, 49f, 7.5f, 40f, TreeKind.PALM),
        TreeSpec(48f, 49f, 8.4f, 300f, TreeKind.PALM),
        TreeSpec(-34f, 9f, 7.2f, 20f, TreeKind.PALM),
        TreeSpec(-14f, 9f, 7.8f, 110f, TreeKind.PALM),
        TreeSpec(14f, 9f, 7.4f, 190f, TreeKind.PALM),
        TreeSpec(32f, 9f, 8.1f, 265f, TreeKind.PALM),
        TreeSpec(-34f, 23f, 7.7f, 55f, TreeKind.PALM),
        TreeSpec(-6f, 25.5f, 7.3f, 145f, TreeKind.PALM),
        TreeSpec(12f, 23f, 8.0f, 230f, TreeKind.PALM),
        TreeSpec(32f, 23f, 7.5f, 315f, TreeKind.PALM),
        TreeSpec(-34f, -17f, 7.9f, 35f, TreeKind.PALM),
        TreeSpec(-14f, -17f, 7.2f, 125f, TreeKind.PALM),
        TreeSpec(14f, -17f, 8.3f, 215f, TreeKind.PALM),
        TreeSpec(34f, -17f, 7.6f, 295f, TreeKind.PALM),
        TreeSpec(-54f, -34f, 8.5f, 60f, TreeKind.PALM),
        TreeSpec(-54f, -6f, 8.0f, 175f, TreeKind.PALM),
        TreeSpec(-54f, 24f, 7.8f, 250f, TreeKind.PALM),
        TreeSpec(54f, -34f, 8.2f, 95f, TreeKind.PALM),
        TreeSpec(54f, -6f, 7.9f, 185f, TreeKind.PALM),
        TreeSpec(54f, 24f, 8.6f, 10f, TreeKind.PALM),
        TreeSpec(-34f, 36f, 6.2f, 0f, TreeKind.TROPICAL_SHADE, 0.0f),
        TreeSpec(14f, 34f, 6.8f, 45f, TreeKind.TROPICAL_SHADE, 0.1f),
        TreeSpec(28f, 32f, 6.0f, 90f, TreeKind.TROPICAL_SHADE, -0.05f),
        TreeSpec(-34f, -44f, 6.5f, 30f, TreeKind.TROPICAL_SHADE, 0.08f),
        TreeSpec(4f, -45f, 6.4f, 70f, TreeKind.TROPICAL_SHADE, -0.04f),
        TreeSpec(40f, -44f, 6.7f, 120f, TreeKind.TROPICAL_SHADE, 0.05f),
        TreeSpec(-18f, -4f, 5.8f, 15f, TreeKind.TROPICAL_SHADE, 0.02f),
        TreeSpec(18f, -4f, 6.1f, 80f, TreeKind.TROPICAL_SHADE, -0.06f),
      )

    for (t in trees) {
      circles += CircleCollider(t.x, t.z, 0.55f, "Tree")
    }

    // ---------------------------------------------------------
    // 4. NEON STREETLIGHTS
    // ---------------------------------------------------------
    val streetLights =
      listOf(
        StreetLightSpec(-26f, 10.5f, 0f, floatArrayOf(1.0f, 0.35f, 0.72f)),
        StreetLightSpec(-6f, 10.5f, 0f, floatArrayOf(0.25f, 0.92f, 0.98f)),
        StreetLightSpec(16f, 10.5f, 0f, floatArrayOf(1.0f, 0.35f, 0.72f)),
        StreetLightSpec(36f, 10.5f, 0f, floatArrayOf(0.25f, 0.92f, 0.98f)),
        StreetLightSpec(-26f, -18.5f, 180f, floatArrayOf(0.25f, 0.92f, 0.98f)),
        StreetLightSpec(24f, -18.5f, 180f, floatArrayOf(1.0f, 0.35f, 0.72f)),
        StreetLightSpec(-36.5f, 2f, 90f, floatArrayOf(1.0f, 0.75f, 0.28f)),
        StreetLightSpec(36.5f, 2f, 270f, floatArrayOf(1.0f, 0.75f, 0.28f)),
      )
    for (sl in streetLights) {
      circles += CircleCollider(sl.x, sl.z, 0.28f, "Streetlight")
    }

    // ---------------------------------------------------------
    // 5. DRIVABLE PLAYER CARS + AUTONOMOUS AI ROAD TRAFFIC CARS
    // ---------------------------------------------------------
    // Outer lane (clockwise) & Inner lane (counter-clockwise) waypoints on Sunset Blvd loop
    val outerRoadLoop =
      listOf(
        Pair(-44.0f, 18.0f),
        Pair(44.0f, 18.0f),
        Pair(44.0f, -26.0f),
        Pair(-44.0f, -26.0f),
      )
    val innerRoadLoop =
      listOf(
        Pair(40.0f, 14.0f),
        Pair(-40.0f, 14.0f),
        Pair(-40.0f, -22.0f),
        Pair(40.0f, -22.0f),
      )

    val vehicleSpawns =
      listOf(
        // Player Parked Cars
        VehicleSpawnSpec(
          id = "car_cheetah",
          displayName = "Cheetah Sunset GT",
          x = -13f,
          z = 19.2f, // Parked along Sunset Blvd curb with clear straightaway
          yawRad = (PI / 2.0).toFloat(),
          bodyColor = floatArrayOf(0.95f, 0.22f, 0.42f),
          trimColor = floatArrayOf(0.10f, 0.11f, 0.14f),
          maxSpeed = 34f,
          accel = 17f,
          isAiTraffic = false,
        ),
        VehicleSpawnSpec(
          id = "car_banshee",
          displayName = "Banshee Nightrunner",
          x = 12f,
          z = -31.5f,
          yawRad = PI.toFloat(),
          bodyColor = floatArrayOf(0.14f, 0.78f, 0.88f),
          trimColor = floatArrayOf(0.10f, 0.10f, 0.13f),
          maxSpeed = 37f,
          accel = 19f,
          isAiTraffic = false,
        ),
        // 4 Autonomous AI Traffic Cars Driving Around Sunset Blvd!
        VehicleSpawnSpec(
          id = "ai_car_1",
          displayName = "Comet Retro Cabrio",
          x = 18f,
          z = 18.0f,
          yawRad = (PI / 2.0).toFloat(),
          bodyColor = floatArrayOf(0.98f, 0.72f, 0.20f), // Golden Sunset Taxi/Coupe
          trimColor = floatArrayOf(0.12f, 0.12f, 0.15f),
          maxSpeed = 31f,
          accel = 15f,
          isAiTraffic = true,
          loopRouteIndex = 1,
          driverShirtColor = floatArrayOf(0.90f, 0.30f, 0.55f),
        ),
        VehicleSpawnSpec(
          id = "ai_car_2",
          displayName = "Infernus Vice Roadster",
          x = 44.0f,
          z = -6f,
          yawRad = PI.toFloat(),
          bodyColor = floatArrayOf(0.55f, 0.26f, 0.92f), // Twilight Purple
          trimColor = floatArrayOf(0.10f, 0.10f, 0.14f),
          maxSpeed = 33f,
          accel = 16f,
          isAiTraffic = true,
          loopRouteIndex = 2,
          driverShirtColor = floatArrayOf(0.24f, 0.88f, 0.74f),
        ),
        VehicleSpawnSpec(
          id = "ai_car_3",
          displayName = "Stinger Emerald GT",
          x = -18f,
          z = 14.0f,
          yawRad = (-PI / 2.0).toFloat(),
          bodyColor = floatArrayOf(0.20f, 0.82f, 0.54f), // Palm Emerald
          trimColor = floatArrayOf(0.12f, 0.13f, 0.16f),
          maxSpeed = 32f,
          accel = 16f,
          isAiTraffic = true,
          loopRouteIndex = 1,
          driverShirtColor = floatArrayOf(0.96f, 0.88f, 0.42f),
        ),
        VehicleSpawnSpec(
          id = "ai_car_4",
          displayName = "Coquette Pearl Spyder",
          x = -40.0f,
          z = -4f,
          yawRad = 0f,
          bodyColor = floatArrayOf(0.95f, 0.94f, 0.96f), // Pearl White
          trimColor = floatArrayOf(0.14f, 0.14f, 0.18f),
          maxSpeed = 32f,
          accel = 16f,
          isAiTraffic = true,
          loopRouteIndex = 3,
          driverShirtColor = floatArrayOf(0.22f, 0.56f, 0.96f),
        ),
      )

    // ---------------------------------------------------------
    // 6. DRIVABLE PLAYER BOATS AT PIER + AI OCEAN CRUISER BOATS
    // ---------------------------------------------------------
    val boatSpawns =
      listOf(
        BoatSpawnSpec(
          id = "boat_speeder_1",
          displayName = "Marlin Vice Speedboat",
          x = 13.0f,
          z = 83.5f, // Docked clear on left side of T-Pier!
          yawRad = 0f, // Facing South (+Z) into open ocean
          hullColor = floatArrayOf(0.96f, 0.96f, 0.98f),
          accentColor = floatArrayOf(0.98f, 0.24f, 0.58f),
          maxSpeed = 29f,
          isAiCruise = false,
        ),
        BoatSpawnSpec(
          id = "boat_speeder_2",
          displayName = "Squalo Jet Cruiser",
          x = 31.0f,
          z = 83.5f, // Docked clear on right side of T-Pier!
          yawRad = 0f, // Facing South (+Z) into open ocean
          hullColor = floatArrayOf(0.12f, 0.78f, 0.88f),
          accentColor = floatArrayOf(1.0f, 0.76f, 0.22f),
          maxSpeed = 32f,
          isAiCruise = false,
        ),
        // 3 Autonomous AI Boats Cruising Ocean Around Key Bayside!
        BoatSpawnSpec(
          id = "ai_boat_1",
          displayName = "Bayside Patrol Boat",
          x = 0f,
          z = 94f,
          yawRad = (PI / 2.0).toFloat(),
          hullColor = floatArrayOf(0.98f, 0.42f, 0.30f),
          accentColor = floatArrayOf(0.96f, 0.96f, 0.98f),
          maxSpeed = 16f,
          isAiCruise = true,
          routeRadiusX = 96f,
          routeRadiusZ = 94f,
          routePhase = 0.0f,
          captainShirtColor = floatArrayOf(0.20f, 0.85f, 0.92f),
        ),
        BoatSpawnSpec(
          id = "ai_boat_2",
          displayName = "Sunset Yacht Tender",
          x = 95f,
          z = 0f,
          yawRad = 0f,
          hullColor = floatArrayOf(0.95f, 0.82f, 0.36f),
          accentColor = floatArrayOf(0.16f, 0.18f, 0.26f),
          maxSpeed = 14f,
          isAiCruise = true,
          routeRadiusX = 108f,
          routeRadiusZ = 104f,
          routePhase = 2.1f,
          captainShirtColor = floatArrayOf(0.98f, 0.36f, 0.64f),
        ),
        BoatSpawnSpec(
          id = "ai_boat_3",
          displayName = "Tropic Wave Runner",
          x = -98f,
          z = 12f,
          yawRad = PI.toFloat(),
          hullColor = floatArrayOf(0.45f, 0.90f, 0.72f),
          accentColor = floatArrayOf(0.98f, 0.28f, 0.52f),
          maxSpeed = 18f,
          isAiCruise = true,
          routeRadiusX = 90f,
          routeRadiusZ = 100f,
          routePhase = 4.2f,
          captainShirtColor = floatArrayOf(1.0f, 0.76f, 0.28f),
        ),
      )

    // ---------------------------------------------------------
    // 7. STROLLING AI PEDESTRIANS (SIDEWALKS, PLAZA & BEACH)
    // ---------------------------------------------------------
    val pedestrianSpawns =
      listOf(
        PedestrianSpawnSpec(
          id = "ped_boardwalk_1",
          waypoints = listOf(Pair(-44f, 52.2f), Pair(44f, 52.2f)),
          shirtColor = floatArrayOf(0.98f, 0.38f, 0.62f),
          pantsColor = floatArrayOf(0.94f, 0.92f, 0.88f),
          speed = 2.0f,
        ),
        PedestrianSpawnSpec(
          id = "ped_boardwalk_2",
          waypoints = listOf(Pair(36f, 52.2f), Pair(-28f, 52.2f)),
          shirtColor = floatArrayOf(0.20f, 0.84f, 0.90f),
          pantsColor = floatArrayOf(0.18f, 0.22f, 0.34f),
          speed = 2.2f,
        ),
        PedestrianSpawnSpec(
          id = "ped_beach_1",
          waypoints = listOf(Pair(-38f, 58.0f), Pair(12f, 58.0f)),
          shirtColor = floatArrayOf(1.0f, 0.78f, 0.28f),
          pantsColor = floatArrayOf(0.22f, 0.62f, 0.78f),
          speed = 1.7f,
        ),
        PedestrianSpawnSpec(
          id = "ped_blvd_south",
          waypoints = listOf(Pair(-32f, 10.2f), Pair(32f, 10.2f)),
          shirtColor = floatArrayOf(0.62f, 0.34f, 0.95f),
          pantsColor = floatArrayOf(0.15f, 0.16f, 0.22f),
          speed = 2.3f,
        ),
        PedestrianSpawnSpec(
          id = "ped_blvd_north",
          waypoints = listOf(Pair(30f, -18.2f), Pair(-30f, -18.2f)),
          shirtColor = floatArrayOf(0.28f, 0.90f, 0.64f),
          pantsColor = floatArrayOf(0.22f, 0.24f, 0.30f),
          speed = 2.1f,
        ),
        PedestrianSpawnSpec(
          id = "ped_plaza",
          waypoints = listOf(Pair(-8f, -10f), Pair(8f, -10f), Pair(8f, 2f), Pair(-8f, 2f)),
          shirtColor = floatArrayOf(0.96f, 0.50f, 0.32f),
          pantsColor = floatArrayOf(0.20f, 0.26f, 0.38f),
          speed = 1.9f,
        ),
        PedestrianSpawnSpec(
          id = "ped_pier",
          waypoints = listOf(Pair(22f, 53f), Pair(22f, 81f)),
          shirtColor = floatArrayOf(0.95f, 0.95f, 0.98f),
          pantsColor = floatArrayOf(0.18f, 0.44f, 0.62f),
          speed = 1.8f,
        ),
      )

    return WorldData(
      houses = houses,
      trees = trees,
      streetLights = streetLights,
      staticBoxes = staticBoxes,
      glassBoxes = glassBoxes,
      aabbColliders = aabbs,
      circleColliders = circles,
      vehicleSpawns = vehicleSpawns,
      boatSpawns = boatSpawns,
      roadLoopWaypointsOuter = outerRoadLoop,
      roadLoopWaypointsInner = innerRoadLoop,
      pedestrianSpawns = pedestrianSpawns,
      playerSpawnX = -17.5f,
      playerSpawnZ = 22.0f,
      playerSpawnYaw = 0.72f,
    )
  }

  private fun buildEnterableHouse(
    h: HouseSpec,
    staticBoxes: MutableList<PropBoxSpec>,
    glassBoxes: MutableList<PropBoxSpec>,
    aabbs: MutableList<AabbCollider>,
  ) {
    val cx = h.centerX
    val cz = h.centerZ
    val hw = h.width / 2f
    val hd = h.depth / 2f
    val wallT = 0.42f
    val wh = h.wallHeight
    val floorY = 0.14f

    staticBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = floorY * 0.5f,
        cz = cz,
        sx = h.width + 1.2f,
        sy = floorY,
        sz = h.depth + 1.6f,
        color = floatArrayOf(0.78f, 0.74f, 0.69f),
        surface = MaterialSurface.TILE,
        roughness = 0.45f,
        castShadow = false,
      )

    val walkZ = if (h.facingSouth) cz + hd + 5.2f else cz - hd - 5.2f
    staticBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = 0.03f,
        cz = walkZ,
        sx = 3.6f,
        sy = 0.05f,
        sz = 9.2f,
        color = floatArrayOf(0.72f, 0.69f, 0.65f),
        surface = MaterialSurface.TILE,
        castShadow = false,
      )

    staticBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = floorY + 0.02f,
        cz = cz,
        sx = h.width * 0.52f,
        sy = 0.03f,
        sz = h.depth * 0.46f,
        color = h.accentColor,
        surface = MaterialSurface.STUCCO,
        castShadow = false,
      )

    for (side in listOf(-1f, 1f)) {
      val wx = cx + side * (hw - wallT * 0.5f)
      staticBoxes +=
        PropBoxSpec(
          cx = wx,
          cy = floorY + 0.55f,
          cz = cz,
          sx = wallT,
          sy = 1.1f,
          sz = h.depth,
          color = h.primaryColor,
          surface = MaterialSurface.STUCCO,
        )
      staticBoxes +=
        PropBoxSpec(
          cx = wx,
          cy = floorY + wh - 0.55f,
          cz = cz,
          sx = wallT,
          sy = 1.1f,
          sz = h.depth,
          color = h.primaryColor,
          surface = MaterialSurface.STUCCO,
        )
      for (corner in listOf(-1f, 1f)) {
        staticBoxes +=
          PropBoxSpec(
            cx = wx,
            cy = floorY + wh * 0.5f,
            cz = cz + corner * (hd - 1.1f),
            sx = wallT,
            sy = wh,
            sz = 2.2f,
            color = h.primaryColor,
            surface = MaterialSurface.STUCCO,
          )
      }
      glassBoxes +=
        PropBoxSpec(
          cx = wx,
          cy = floorY + wh * 0.5f,
          cz = cz,
          sx = 0.08f,
          sy = wh - 2.1f,
          sz = h.depth - 4.3f,
          color = floatArrayOf(0.55f, 0.85f, 0.96f),
          surface = MaterialSurface.GLASS,
          roughness = 0.08f,
          castShadow = false,
        )
      aabbs += AabbCollider(wx - 0.30f, cz - hd, wx + 0.30f, cz + hd, tag = h.name)
    }

    val backZ = if (h.facingSouth) cz - hd + wallT * 0.5f else cz + hd - wallT * 0.5f
    staticBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = floorY + wh * 0.5f,
        cz = backZ,
        sx = h.width,
        sy = wh,
        sz = wallT,
        color = h.primaryColor,
        surface = MaterialSurface.STUCCO,
      )
    aabbs += AabbCollider(cx - hw, backZ - 0.30f, cx + hw, backZ + 0.30f, tag = h.name)

    val frontZ = if (h.facingSouth) cz + hd - wallT * 0.5f else cz - hd + wallT * 0.5f
    val doorHalf = 1.45f
    val segWidth = hw - doorHalf
    staticBoxes +=
      PropBoxSpec(
        cx = cx - doorHalf - segWidth * 0.5f,
        cy = floorY + wh * 0.5f,
        cz = frontZ,
        sx = segWidth,
        sy = wh,
        sz = wallT,
        color = h.primaryColor,
        surface = MaterialSurface.STUCCO,
      )
    aabbs += AabbCollider(cx - hw, frontZ - 0.30f, cx - doorHalf, frontZ + 0.30f, tag = h.name)

    staticBoxes +=
      PropBoxSpec(
        cx = cx + doorHalf + segWidth * 0.5f,
        cy = floorY + wh * 0.5f,
        cz = frontZ,
        sx = segWidth,
        sy = wh,
        sz = wallT,
        color = h.primaryColor,
        surface = MaterialSurface.STUCCO,
      )
    aabbs += AabbCollider(cx + doorHalf, frontZ - 0.30f, cx + hw, frontZ + 0.30f, tag = h.name)

    staticBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = floorY + wh - 0.65f,
        cz = frontZ,
        sx = doorHalf * 2f,
        sy = 1.3f,
        sz = wallT,
        color = h.accentColor,
        surface = MaterialSurface.STUCCO,
      )

    val signOffset = if (h.facingSouth) 0.28f else -0.28f
    staticBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = floorY + wh - 0.65f,
        cz = frontZ + signOffset,
        sx = 2.6f,
        sy = 0.24f,
        sz = 0.14f,
        color = h.neonColor,
        surface = MaterialSurface.EMISSIVE,
        emissive = 0.85f,
        castShadow = false,
      )

    staticBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = floorY + wh + 0.24f,
        cz = cz,
        sx = h.width + 1.8f,
        sy = 0.48f,
        sz = h.depth + 2.0f,
        color = h.roofColor,
        surface = MaterialSurface.SLATE,
        roughness = 0.50f,
      )
    staticBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = floorY + wh + 0.08f,
        cz = frontZ + (if (h.facingSouth) 1.0f else -1.0f),
        sx = h.width + 1.82f,
        sy = 0.12f,
        sz = 0.14f,
        color = h.neonColor,
        surface = MaterialSurface.EMISSIVE,
        emissive = 0.7f,
        castShadow = false,
      )

    val porchZ = if (h.facingSouth) cz + hd + 0.7f else cz - hd - 0.7f
    for (px in listOf(cx - hw + 0.6f, cx + hw - 0.6f)) {
      staticBoxes +=
        PropBoxSpec(
          cx = px,
          cy = floorY + wh * 0.5f,
          cz = porchZ,
          sx = 0.36f,
          sy = wh,
          sz = 0.36f,
          color = h.accentColor,
          surface = MaterialSurface.STUCCO,
        )
      aabbs += AabbCollider(px - 0.25f, porchZ - 0.25f, px + 0.25f, porchZ + 0.25f, tag = h.name)
    }

    val sofaZ = if (h.facingSouth) cz - 1.9f else cz + 1.9f
    staticBoxes +=
      PropBoxSpec(
        cx = cx - 1.8f,
        cy = floorY + 0.32f,
        cz = sofaZ,
        sx = 3.2f,
        sy = 0.55f,
        sz = 1.15f,
        color = floatArrayOf(0.92f, 0.89f, 0.84f),
        surface = MaterialSurface.STUCCO,
      )
    staticBoxes +=
      PropBoxSpec(
        cx = cx - 1.8f,
        cy = floorY + 0.72f,
        cz = sofaZ + (if (h.facingSouth) -0.42f else 0.42f),
        sx = 3.2f,
        sy = 0.50f,
        sz = 0.30f,
        color = h.accentColor,
        surface = MaterialSurface.STUCCO,
      )
    aabbs +=
      AabbCollider(cx - 3.4f, sofaZ - 0.60f, cx - 0.2f, sofaZ + 0.60f, tag = "${h.name} Sofa")

    staticBoxes +=
      PropBoxSpec(
        cx = cx + 2.2f,
        cy = floorY + 0.28f,
        cz = cz,
        sx = 1.8f,
        sy = 0.46f,
        sz = 1.1f,
        color = floatArrayOf(0.55f, 0.34f, 0.20f),
        surface = MaterialSurface.WOOD,
      )
    aabbs += AabbCollider(cx + 1.25f, cz - 0.55f, cx + 3.15f, cz + 0.55f, tag = "${h.name} Table")

    val artZ = if (h.facingSouth) backZ + 0.26f else backZ - 0.26f
    staticBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = floorY + 2.2f,
        cz = artZ,
        sx = 2.6f,
        sy = 1.2f,
        sz = 0.10f,
        color = h.neonColor,
        surface = MaterialSurface.EMISSIVE,
        emissive = 0.80f,
        castShadow = false,
      )
    staticBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = floorY + wh - 0.14f,
        cz = cz,
        sx = 1.6f,
        sy = 0.12f,
        sz = 1.6f,
        color = floatArrayOf(1.0f, 0.90f, 0.74f),
        surface = MaterialSurface.EMISSIVE,
        emissive = 0.95f,
        castShadow = false,
      )

    if (h.hasPool) {
      val poolCz = cz + hd + 5.2f
      staticBoxes +=
        PropBoxSpec(
          cx = cx,
          cy = 0.06f,
          cz = poolCz,
          sx = 11.5f,
          sy = 0.14f,
          sz = 7.4f,
          color = floatArrayOf(0.84f, 0.82f, 0.78f),
          surface = MaterialSurface.TILE,
          castShadow = false,
        )
      staticBoxes +=
        PropBoxSpec(
          cx = cx,
          cy = 0.11f,
          cz = poolCz,
          sx = 8.4f,
          sy = 0.08f,
          sz = 4.6f,
          color = floatArrayOf(0.08f, 0.78f, 0.90f),
          surface = MaterialSurface.WATER,
          roughness = 0.10f,
          castShadow = false,
        )
    }
  }
}
