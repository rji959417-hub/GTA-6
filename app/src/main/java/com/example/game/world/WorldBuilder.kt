package com.example.game.world

import com.example.game.math.AabbCollider
import com.example.game.math.CircleCollider
import kotlin.math.PI

object WorldBuilder {
  fun buildKeyBayside(): WorldData {
    val staticBoxes = mutableListOf<PropBoxSpec>()
    val staticCylinders = mutableListOf<PropCylinderSpec>()
    val glassBoxes = mutableListOf<PropBoxSpec>()
    val aabbs = mutableListOf<AabbCollider>()
    val circles = mutableListOf<CircleCollider>()
    val walkableFloors = mutableListOf<WalkableFloorSpec>()

    // ---------------------------------------------------------
    // 1. ISLAND 1 (KEY BAYSIDE) & ISLAND 2 (EMERALD UPTOWN) TERRAIN
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
    // 1B. DUAL CONNECTING CHANNEL BRIDGES (x = 47..101 at z = 16 & z = -24)
    // ---------------------------------------------------------
    buildChannelBridge(
      zCenter = 16f,
      accentNeon = floatArrayOf(0.18f, 0.92f, 0.96f),
      staticBoxes = staticBoxes,
      aabbs = aabbs,
      walkableFloors = walkableFloors,
    )
    buildChannelBridge(
      zCenter = -24f,
      accentNeon = floatArrayOf(1.0f, 0.28f, 0.65f),
      staticBoxes = staticBoxes,
      aabbs = aabbs,
      walkableFloors = walkableFloors,
    )

    // ---------------------------------------------------------
    // 1C. ISLAND 2: EMERALD UPTOWN DISTRICT (x = 84..212, centered x = 148)
    // ---------------------------------------------------------
    staticBoxes +=
      PropBoxSpec(
        cx = 148f,
        cy = -0.30f,
        cz = -4f,
        sx = 128f,
        sy = 0.60f,
        sz = 120f,
        color = floatArrayOf(0.22f, 0.50f, 0.29f),
        surface = MaterialSurface.GRASS,
        roughness = 0.85f,
        castShadow = false,
      )
    // Emerald Uptown Southern Sand Beach
    staticBoxes +=
      PropBoxSpec(
        cx = 148f,
        cy = -0.34f,
        cz = 59.5f,
        sx = 130f,
        sy = 0.56f,
        sz = 13f,
        color = floatArrayOf(0.90f, 0.79f, 0.58f),
        surface = MaterialSurface.SAND,
        roughness = 0.90f,
        castShadow = false,
      )
    // Uptown Waterfront Boardwalk
    staticBoxes +=
      PropBoxSpec(
        cx = 148f,
        cy = 0.04f,
        cz = 52.2f,
        sx = 114f,
        sy = 0.14f,
        sz = 3.2f,
        color = floatArrayOf(0.54f, 0.37f, 0.23f),
        surface = MaterialSurface.WOOD,
        roughness = 0.72f,
        castShadow = false,
      )
    // Uptown Ocean Pier at x = 168
    staticBoxes +=
      PropBoxSpec(
        cx = 168f,
        cy = 0.12f,
        cz = 69f,
        sx = 5.4f,
        sy = 0.24f,
        sz = 31f,
        color = floatArrayOf(0.52f, 0.35f, 0.22f),
        surface = MaterialSurface.WOOD,
        roughness = 0.75f,
      )
    staticBoxes +=
      PropBoxSpec(
        cx = 168f,
        cy = 0.12f,
        cz = 82.5f,
        sx = 12.6f,
        sy = 0.24f,
        sz = 4.6f,
        color = floatArrayOf(0.56f, 0.38f, 0.24f),
        surface = MaterialSurface.WOOD,
        roughness = 0.74f,
      )
    for (px in listOf(162.2f, 173.8f)) {
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
          color = floatArrayOf(0.24f, 0.96f, 0.68f),
          surface = MaterialSurface.EMISSIVE,
          emissive = 0.95f,
          castShadow = false,
        )
    }

    // Uptown Asphalt Road Grid connecting smoothly to both Channel Bridges
    staticBoxes +=
      PropBoxSpec(
        cx = 148f,
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
        cx = 148f,
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
    for (ax in listOf(106f, 148f, 190f)) {
      staticBoxes +=
        PropBoxSpec(
          cx = ax,
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
    }
    for (x in 114..182 step 6) {
      if (x !in 144..152) {
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
    // Green Valley Central Park, Gazebo, Double-Arch Gate, Tennis Court, Compound Walls & Clubhouse
    TownAndValleyBuilder.buildGreenValleyFeatures(
      staticBoxes = staticBoxes,
      staticCylinders = staticCylinders,
      glassBoxes = glassBoxes,
      aabbs = aabbs,
      walkableFloors = walkableFloors,
    )

    // ---------------------------------------------------------
    // 2. ENTERABLE HOUSES (KEY BAYSIDE 1F + GREEN VALLEY ENCLAVE 2-STORY WALKABLE VILLAS)
    // ---------------------------------------------------------
    val houses =
      mutableListOf(
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
        // Map 2: Green Valley Enclave 2-Story + Walkable Rooftop Modern Villas!
        HouseSpec(
          id = "house_uptown_duplex",
          name = "Green Valley Sky Villa",
          subtitle = "Enclave 2F · Balcony, Roof & Pool",
          centerX = 126f,
          centerZ = 33f,
          width = 15.0f,
          depth = 11.0f,
          wallHeight = 3.4f,
          facingSouth = false,
          primaryColor = floatArrayOf(0.92f, 0.90f, 0.83f),
          accentColor = floatArrayOf(0.56f, 0.64f, 0.44f), // Sage olive modern villa trim
          roofColor = floatArrayOf(0.80f, 0.78f, 0.73f),
          neonColor = floatArrayOf(0.22f, 0.90f, 0.56f),
          hasPool = true,
          floors = 2,
          hasRooftop = true,
        ),
        HouseSpec(
          id = "house_uptown_manor",
          name = "Green Valley Manor",
          subtitle = "Enclave 2F · Lounge & Rooftop",
          centerX = 126f,
          centerZ = -41f,
          width = 15.0f,
          depth = 11.0f,
          wallHeight = 3.4f,
          facingSouth = true,
          primaryColor = floatArrayOf(0.94f, 0.89f, 0.78f),
          accentColor = floatArrayOf(0.74f, 0.56f, 0.38f),
          roofColor = floatArrayOf(0.76f, 0.73f, 0.68f),
          neonColor = floatArrayOf(1.0f, 0.72f, 0.28f),
          floors = 2,
          hasRooftop = true,
        ),
        HouseSpec(
          id = "house_uptown_penthouse",
          name = "Valley Crest Residence",
          subtitle = "Enclave 2F · Sky Deck Lounge",
          centerX = 170f,
          centerZ = -41f,
          width = 15.4f,
          depth = 11.0f,
          wallHeight = 3.5f,
          facingSouth = true,
          primaryColor = floatArrayOf(0.90f, 0.88f, 0.84f),
          accentColor = floatArrayOf(0.36f, 0.34f, 0.32f),
          roofColor = floatArrayOf(0.72f, 0.70f, 0.66f),
          neonColor = floatArrayOf(0.24f, 0.92f, 0.82f),
          floors = 2,
          hasRooftop = true,
        ),
      )

    for (h in houses) {
      if (h.floors >= 2) {
        buildTwoStoryHouse(h, staticBoxes, glassBoxes, aabbs, walkableFloors)
      } else {
        buildEnterableHouse(h, staticBoxes, glassBoxes, aabbs, walkableFloors)
      }
    }

    // ---------------------------------------------------------
    // 2B. VICE ARMS GUN SHOP AT SUNSET PLAZA EAST (27.5, -2.0)
    // ---------------------------------------------------------
    buildGunShopKiosk(27.5f, -2.0f, staticBoxes, glassBoxes, aabbs, walkableFloors)

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
      mutableListOf(
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
        // Green Valley Enclave Palms & Park Shade Trees
        TreeSpec(96f, 49f, 8.2f, 25f, TreeKind.PALM),
        TreeSpec(116f, 49f, 7.6f, 95f, TreeKind.PALM),
        TreeSpec(138f, 49f, 8.5f, 165f, TreeKind.PALM),
        TreeSpec(158f, 49f, 7.9f, 240f, TreeKind.PALM),
        TreeSpec(180f, 49f, 8.3f, 310f, TreeKind.PALM),
        TreeSpec(198f, 49f, 7.7f, 50f, TreeKind.PALM),
        TreeSpec(114f, 9f, 7.4f, 35f, TreeKind.PALM),
        TreeSpec(134f, 9f, 8.0f, 130f, TreeKind.PALM),
        TreeSpec(162f, 9f, 7.5f, 220f, TreeKind.PALM),
        TreeSpec(182f, 9f, 8.1f, 290f, TreeKind.PALM),
        TreeSpec(114f, -17f, 7.6f, 75f, TreeKind.PALM),
        TreeSpec(134f, -17f, 7.3f, 170f, TreeKind.PALM),
        TreeSpec(162f, -17f, 8.2f, 250f, TreeKind.PALM),
        TreeSpec(182f, -17f, 7.8f, 330f, TreeKind.PALM),
        TreeSpec(202f, -34f, 8.4f, 45f, TreeKind.PALM),
        TreeSpec(202f, 24f, 8.1f, 195f, TreeKind.PALM),
        TreeSpec(112f, 35f, 6.3f, 20f, TreeKind.TROPICAL_SHADE, 0.04f),
        TreeSpec(166f, 32f, 6.6f, 85f, TreeKind.TROPICAL_SHADE, -0.05f),
        TreeSpec(148f, -45f, 6.4f, 60f, TreeKind.TROPICAL_SHADE, 0.06f),
      )

    for (t in trees) {
      circles += CircleCollider(t.x, t.z, 0.55f, "Tree")
    }

    // ---------------------------------------------------------
    // 4. NEON STREETLIGHTS
    // ---------------------------------------------------------
    val streetLights =
      mutableListOf(
        StreetLightSpec(-26f, 10.5f, 0f, floatArrayOf(1.0f, 0.35f, 0.72f)),
        StreetLightSpec(-6f, 10.5f, 0f, floatArrayOf(0.25f, 0.92f, 0.98f)),
        StreetLightSpec(16f, 10.5f, 0f, floatArrayOf(1.0f, 0.35f, 0.72f)),
        StreetLightSpec(36f, 10.5f, 0f, floatArrayOf(0.25f, 0.92f, 0.98f)),
        StreetLightSpec(-26f, -18.5f, 180f, floatArrayOf(0.25f, 0.92f, 0.98f)),
        StreetLightSpec(24f, -18.5f, 180f, floatArrayOf(1.0f, 0.35f, 0.72f)),
        StreetLightSpec(-36.5f, 2f, 90f, floatArrayOf(1.0f, 0.75f, 0.28f)),
        StreetLightSpec(36.5f, 2f, 270f, floatArrayOf(1.0f, 0.75f, 0.28f)),
        // Green Valley Streetlights
        StreetLightSpec(120f, 10.5f, 0f, floatArrayOf(0.20f, 0.96f, 0.74f)),
        StreetLightSpec(140f, 10.5f, 0f, floatArrayOf(1.0f, 0.35f, 0.72f)),
        StreetLightSpec(176f, 10.5f, 0f, floatArrayOf(0.25f, 0.92f, 0.98f)),
        StreetLightSpec(120f, -18.5f, 180f, floatArrayOf(0.25f, 0.92f, 0.98f)),
        StreetLightSpec(176f, -18.5f, 180f, floatArrayOf(0.20f, 0.96f, 0.74f)),
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
    val uptownRoadLoop =
      listOf(
        Pair(106.0f, 18.0f),
        Pair(190.0f, 18.0f),
        Pair(190.0f, -26.0f),
        Pair(106.0f, -26.0f),
      )
    val townRoadLoop =
      listOf(
        Pair(-194.0f, 18.0f),
        Pair(-104.0f, 18.0f),
        Pair(-104.0f, -26.0f),
        Pair(-194.0f, -26.0f),
      )

    val vehicleSpawns =
      mutableListOf(
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
          driverShirtColor = CartoonPresets.CHUBBY_SAFFRON.tunicColor,
          appearance = CartoonPresets.CHUBBY_SAFFRON,
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
          driverShirtColor = CartoonPresets.LEAN_BUDDY_BLUE.tunicColor,
          appearance = CartoonPresets.LEAN_BUDDY_BLUE,
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
          driverShirtColor = CartoonPresets.INSPECTOR_KHAKI.tunicColor,
          appearance = CartoonPresets.INSPECTOR_KHAKI,
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
          driverShirtColor = CartoonPresets.CHUBBY_PURPLE.tunicColor,
          appearance = CartoonPresets.CHUBBY_PURPLE,
        ),
        // Emerald Uptown Player Sports Cars + AI Loop Cars
        VehicleSpawnSpec(
          id = "car_uptown_turismo",
          displayName = "Turismo Emerald R",
          x = 122f,
          z = 19.2f,
          yawRad = (PI / 2.0).toFloat(),
          bodyColor = floatArrayOf(0.18f, 0.90f, 0.62f),
          trimColor = floatArrayOf(0.10f, 0.11f, 0.14f),
          maxSpeed = 38f,
          accel = 20f,
          isAiTraffic = false,
        ),
        VehicleSpawnSpec(
          id = "car_uptown_comet",
          displayName = "Vortex Sunset Coupe",
          x = 166f,
          z = -31.0f,
          yawRad = PI.toFloat(),
          bodyColor = floatArrayOf(1.0f, 0.66f, 0.18f),
          trimColor = floatArrayOf(0.12f, 0.12f, 0.16f),
          maxSpeed = 35f,
          accel = 18f,
          isAiTraffic = false,
        ),
        VehicleSpawnSpec(
          id = "ai_car_uptown_1",
          displayName = "Sentinel Uptown GT",
          x = 152f,
          z = 18.0f,
          yawRad = (PI / 2.0).toFloat(),
          bodyColor = floatArrayOf(0.96f, 0.28f, 0.48f),
          trimColor = floatArrayOf(0.12f, 0.12f, 0.15f),
          maxSpeed = 32f,
          accel = 16f,
          isAiTraffic = true,
          loopRouteIndex = 1,
          driverShirtColor = CartoonPresets.ATHLETIC_MINT.tunicColor,
          appearance = CartoonPresets.ATHLETIC_MINT,
        ),
        VehicleSpawnSpec(
          id = "ai_car_uptown_2",
          displayName = "Banshee Palm Cruiser",
          x = 190f,
          z = -6.0f,
          yawRad = PI.toFloat(),
          bodyColor = floatArrayOf(0.24f, 0.78f, 0.96f),
          trimColor = floatArrayOf(0.12f, 0.12f, 0.15f),
          maxSpeed = 32f,
          accel = 16f,
          isAiTraffic = true,
          loopRouteIndex = 2,
          driverShirtColor = CartoonPresets.CHUBBY_CORAL.tunicColor,
          appearance = CartoonPresets.CHUBBY_CORAL,
        ),
      )

    // ---------------------------------------------------------
    // 6. DRIVABLE PLAYER BOATS AT PIER + AI OCEAN CRUISER BOATS
    // ---------------------------------------------------------
    val boatSpawns =
      mutableListOf(
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
          captainShirtColor = CartoonPresets.COASTAL_CAPTAIN.tunicColor,
          appearance = CartoonPresets.COASTAL_CAPTAIN,
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
          captainShirtColor = CartoonPresets.CHUBBY_EMERALD.tunicColor,
          appearance = CartoonPresets.CHUBBY_EMERALD,
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
          captainShirtColor = CartoonPresets.LEAN_BUDDY_GOLD.tunicColor,
          appearance = CartoonPresets.LEAN_BUDDY_GOLD,
        ),
        BoatSpawnSpec(
          id = "boat_uptown_1",
          displayName = "Emerald Bay Jetboat",
          x = 159.0f,
          z = 83.5f,
          yawRad = 0f,
          hullColor = floatArrayOf(0.22f, 0.92f, 0.68f),
          accentColor = floatArrayOf(0.98f, 0.30f, 0.60f),
          maxSpeed = 33f,
          isAiCruise = false,
        ),
        BoatSpawnSpec(
          id = "ai_boat_uptown",
          displayName = "Emerald Horizon Patrol",
          x = 240f,
          z = 0f,
          yawRad = 0f,
          hullColor = floatArrayOf(0.95f, 0.95f, 0.98f),
          accentColor = floatArrayOf(0.20f, 0.90f, 0.74f),
          maxSpeed = 16f,
          isAiCruise = true,
          routeCenterX = 148f,
          routeRadiusX = 96f,
          routeRadiusZ = 96f,
          routePhase = 1.2f,
          captainShirtColor = CartoonPresets.INSPECTOR_KHAKI.tunicColor,
          appearance = CartoonPresets.INSPECTOR_KHAKI,
        ),
      )

    // ---------------------------------------------------------
    // 7. STROLLING CARTOON TOWNSFOLK (SIDEWALKS, PLAZA, HOUSES & BEACH)
    // ---------------------------------------------------------
    val pedestrianSpawns =
      mutableListOf(
        PedestrianSpawnSpec(
          id = "ped_buddy_lean",
          waypoints = listOf(Pair(-44f, 52.2f), Pair(44f, 52.2f)),
          shirtColor = CartoonPresets.LEAN_BUDDY_BLUE.tunicColor,
          pantsColor = CartoonPresets.LEAN_BUDDY_BLUE.pantsColor,
          speed = 2.0f,
          appearance = CartoonPresets.LEAN_BUDDY_BLUE,
        ),
        PedestrianSpawnSpec(
          id = "ped_chubby_emerald",
          waypoints = listOf(Pair(36f, 52.2f), Pair(-28f, 52.2f)),
          shirtColor = CartoonPresets.CHUBBY_EMERALD.tunicColor,
          pantsColor = CartoonPresets.CHUBBY_EMERALD.pantsColor,
          speed = 1.9f,
          appearance = CartoonPresets.CHUBBY_EMERALD,
        ),
        PedestrianSpawnSpec(
          id = "ped_chubby_saffron",
          waypoints = listOf(Pair(-38f, 58.0f), Pair(12f, 58.0f)),
          shirtColor = CartoonPresets.CHUBBY_SAFFRON.tunicColor,
          pantsColor = CartoonPresets.CHUBBY_SAFFRON.pantsColor,
          speed = 1.7f,
          appearance = CartoonPresets.CHUBBY_SAFFRON,
        ),
        PedestrianSpawnSpec(
          id = "ped_inspector_blvd",
          waypoints = listOf(Pair(-32f, 10.2f), Pair(32f, 10.2f)),
          shirtColor = CartoonPresets.INSPECTOR_KHAKI.tunicColor,
          pantsColor = CartoonPresets.INSPECTOR_KHAKI.pantsColor,
          speed = 2.2f,
          appearance = CartoonPresets.INSPECTOR_KHAKI,
          isBraveDefender = true,
        ),
        PedestrianSpawnSpec(
          id = "ped_chubby_coral",
          waypoints = listOf(Pair(30f, -18.2f), Pair(-30f, -18.2f)),
          shirtColor = CartoonPresets.CHUBBY_CORAL.tunicColor,
          pantsColor = CartoonPresets.CHUBBY_CORAL.pantsColor,
          speed = 2.0f,
          appearance = CartoonPresets.CHUBBY_CORAL,
        ),
        PedestrianSpawnSpec(
          id = "ped_plaza_gold",
          waypoints = listOf(Pair(-8f, -10f), Pair(8f, -10f), Pair(8f, 2f), Pair(-8f, 2f)),
          shirtColor = CartoonPresets.LEAN_BUDDY_GOLD.tunicColor,
          pantsColor = CartoonPresets.LEAN_BUDDY_GOLD.pantsColor,
          speed = 1.9f,
          appearance = CartoonPresets.LEAN_BUDDY_GOLD,
        ),
        PedestrianSpawnSpec(
          id = "ped_pier_captain",
          waypoints = listOf(Pair(22f, 53f), Pair(22f, 81f)),
          shirtColor = CartoonPresets.COASTAL_CAPTAIN.tunicColor,
          pantsColor = CartoonPresets.COASTAL_CAPTAIN.pantsColor,
          speed = 1.8f,
          appearance = CartoonPresets.COASTAL_CAPTAIN,
          isBraveDefender = true,
        ),
        PedestrianSpawnSpec(
          id = "ped_villa_purple",
          waypoints = listOf(Pair(-16f, 23.5f), Pair(-28f, 23.5f)),
          shirtColor = CartoonPresets.CHUBBY_PURPLE.tunicColor,
          pantsColor = CartoonPresets.CHUBBY_PURPLE.pantsColor,
          speed = 1.75f,
          appearance = CartoonPresets.CHUBBY_PURPLE,
        ),
        PedestrianSpawnSpec(
          id = "ped_artdeco_mint",
          waypoints = listOf(Pair(-29f, -33.0f), Pair(-14f, -33.0f)),
          shirtColor = CartoonPresets.ATHLETIC_MINT.tunicColor,
          pantsColor = CartoonPresets.ATHLETIC_MINT.pantsColor,
          speed = 2.0f,
          appearance = CartoonPresets.ATHLETIC_MINT,
        ),
        PedestrianSpawnSpec(
          id = "ped_estate_emerald",
          waypoints = listOf(Pair(15f, -33.5f), Pair(32f, -33.5f)),
          shirtColor = CartoonPresets.CHUBBY_EMERALD.tunicColor,
          pantsColor = CartoonPresets.CHUBBY_EMERALD.pantsColor,
          speed = 1.85f,
          appearance = CartoonPresets.CHUBBY_EMERALD,
        ),
        PedestrianSpawnSpec(
          id = "ped_east_boardwalk",
          waypoints = listOf(Pair(46f, -18f), Pair(46f, 24f)),
          shirtColor = CartoonPresets.LEAN_BUDDY_BLUE.tunicColor,
          pantsColor = CartoonPresets.LEAN_BUDDY_BLUE.pantsColor,
          speed = 2.1f,
          appearance = CartoonPresets.LEAN_BUDDY_BLUE,
        ),
        PedestrianSpawnSpec(
          id = "ped_west_walk",
          waypoints = listOf(Pair(-47.5f, 16f), Pair(-47.5f, -22f)),
          shirtColor = CartoonPresets.CHUBBY_SAFFRON.tunicColor,
          pantsColor = CartoonPresets.CHUBBY_SAFFRON.pantsColor,
          speed = 1.8f,
          appearance = CartoonPresets.CHUBBY_SAFFRON,
        ),
        // Emerald Uptown Citizens & Bridge Walkers
        PedestrianSpawnSpec(
          id = "ped_bridge_walker",
          waypoints = listOf(Pair(48f, 12.2f), Pair(100f, 12.2f)),
          shirtColor = CartoonPresets.ATHLETIC_MINT.tunicColor,
          pantsColor = CartoonPresets.ATHLETIC_MINT.pantsColor,
          speed = 2.0f,
          appearance = CartoonPresets.ATHLETIC_MINT,
        ),
        PedestrianSpawnSpec(
          id = "ped_uptown_boardwalk",
          waypoints = listOf(Pair(108f, 52.2f), Pair(184f, 52.2f)),
          shirtColor = CartoonPresets.CHUBBY_CORAL.tunicColor,
          pantsColor = CartoonPresets.CHUBBY_CORAL.pantsColor,
          speed = 1.9f,
          appearance = CartoonPresets.CHUBBY_CORAL,
        ),
        PedestrianSpawnSpec(
          id = "ped_uptown_duplex",
          waypoints = listOf(Pair(116f, 23.0f), Pair(138f, 23.0f)),
          shirtColor = CartoonPresets.LEAN_BUDDY_GOLD.tunicColor,
          pantsColor = CartoonPresets.LEAN_BUDDY_GOLD.pantsColor,
          speed = 1.85f,
          appearance = CartoonPresets.LEAN_BUDDY_GOLD,
        ),
        PedestrianSpawnSpec(
          id = "ped_uptown_plaza",
          waypoints = listOf(Pair(140f, -10f), Pair(156f, -10f), Pair(156f, 2f), Pair(140f, 2f)),
          shirtColor = CartoonPresets.CHUBBY_PURPLE.tunicColor,
          pantsColor = CartoonPresets.CHUBBY_PURPLE.pantsColor,
          speed = 2.0f,
          appearance = CartoonPresets.CHUBBY_PURPLE,
          isBraveDefender = true,
        ),
        PedestrianSpawnSpec(
          id = "ped_uptown_north",
          waypoints = listOf(Pair(118f, -32.5f), Pair(176f, -32.5f)),
          shirtColor = CartoonPresets.INSPECTOR_KHAKI.tunicColor,
          pantsColor = CartoonPresets.INSPECTOR_KHAKI.pantsColor,
          speed = 2.05f,
          appearance = CartoonPresets.INSPECTOR_KHAKI,
        ),
      )

    // ---------------------------------------------------------
    // 8. ISLAND 3: ROOFTOP TOWN STREET (WEST ISLAND x = -212..-84)
    // ---------------------------------------------------------
    TownAndValleyBuilder.buildRooftopTownDistrict(
      staticBoxes = staticBoxes,
      staticCylinders = staticCylinders,
      glassBoxes = glassBoxes,
      aabbs = aabbs,
      circles = circles,
      walkableFloors = walkableFloors,
      trees = trees,
      streetLights = streetLights,
      houses = houses,
      vehicleSpawns = vehicleSpawns,
      boatSpawns = boatSpawns,
      pedestrianSpawns = pedestrianSpawns,
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
      roadLoopWaypointsOuter = outerRoadLoop,
      roadLoopWaypointsInner = innerRoadLoop,
      roadLoopWaypointsUptown = uptownRoadLoop,
      roadLoopWaypointsTown = townRoadLoop,
      pedestrianSpawns = pedestrianSpawns,
      playerSpawnX = -17.5f,
      playerSpawnZ = 22.0f,
      playerSpawnYaw = 0.72f,
      gunShopCenter = Pair(27.5f, -2.0f),
      gunShopBuyPos = Pair(27.5f, 3.2f),
    )
  }

  private fun buildChannelBridge(
    zCenter: Float,
    accentNeon: FloatArray,
    staticBoxes: MutableList<PropBoxSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
  ) {
    // Seamless asphalt bridge approach & span across channel (x = 47..101)
    staticBoxes +=
      PropBoxSpec(
        cx = 74f,
        cy = 0.04f,
        cz = zCenter,
        sx = 54f,
        sy = 0.16f,
        sz = 10f,
        color = floatArrayOf(0.20f, 0.21f, 0.25f),
        surface = MaterialSurface.ASPHALT,
        roughness = 0.70f,
      )
    walkableFloors +=
      WalkableFloorSpec(
        minX = 47f,
        minZ = zCenter - 5.0f,
        maxX = 101f,
        maxZ = zCenter + 5.0f,
        baseElevationY = 0.12f,
        tag = "Channel Bridge",
      )

    // Dashed yellow center lane markings across the bridge
    val stripeColor = floatArrayOf(0.95f, 0.78f, 0.25f)
    for (x in 50..98 step 6) {
      staticBoxes +=
        PropBoxSpec(
          cx = x.toFloat(),
          cy = 0.13f,
          cz = zCenter,
          sx = 2.8f,
          sy = 0.02f,
          sz = 0.24f,
          color = stripeColor,
          surface = MaterialSurface.STUCCO,
          castShadow = false,
        )
    }

    // Raised Sidewalks, Guard Rails & Channel Support Pylons (x = 63..85)
    for (sideSign in listOf(-1f, 1f)) {
      val railZ = zCenter + sideSign * 4.85f
      staticBoxes +=
        PropBoxSpec(
          cx = 74f,
          cy = 0.32f,
          cz = railZ,
          sx = 24f,
          sy = 0.42f,
          sz = 0.42f,
          color = floatArrayOf(0.82f, 0.84f, 0.88f),
          surface = MaterialSurface.STUCCO,
        )
      staticBoxes +=
        PropBoxSpec(
          cx = 74f,
          cy = 0.72f,
          cz = railZ,
          sx = 24f,
          sy = 0.14f,
          sz = 0.22f,
          color = accentNeon,
          surface = MaterialSurface.EMISSIVE,
          emissive = 0.85f,
          castShadow = false,
        )
      aabbs +=
        AabbCollider(
          minX = 62.5f,
          minZ = railZ - 0.32f,
          maxX = 85.5f,
          maxZ = railZ + 0.32f,
          minY = 0f,
          maxY = 2.0f,
          tag = "Bridge Rail",
        )
    }

    // Bridge Concrete Support Pilings in Water & Iconic Neon Gateway Towers
    for (px in listOf(66f, 82f)) {
      for (pz in listOf(zCenter - 4.4f, zCenter + 4.4f)) {
        staticBoxes +=
          PropBoxSpec(
            cx = px,
            cy = -0.8f,
            cz = pz,
            sx = 0.9f,
            sy = 1.8f,
            sz = 0.9f,
            color = floatArrayOf(0.45f, 0.47f, 0.50f),
            surface = MaterialSurface.SLATE,
          )
        staticBoxes +=
          PropBoxSpec(
            cx = px,
            cy = 2.6f,
            cz = pz,
            sx = 0.55f,
            sy = 4.6f,
            sz = 0.55f,
            color = floatArrayOf(0.22f, 0.24f, 0.30f),
            surface = MaterialSurface.SLATE,
          )
      }
      // Overhead neon bridge arch beam
      staticBoxes +=
        PropBoxSpec(
          cx = px,
          cy = 4.9f,
          cz = zCenter,
          sx = 0.48f,
          sy = 0.34f,
          sz = 9.4f,
          color = accentNeon,
          surface = MaterialSurface.EMISSIVE,
          emissive = 0.95f,
          castShadow = false,
        )
    }
  }

  private fun buildGunShopKiosk(
    cx: Float,
    cz: Float,
    staticBoxes: MutableList<PropBoxSpec>,
    glassBoxes: MutableList<PropBoxSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
  ) {
    val w = 7.4f
    val d = 5.6f
    val hw = w * 0.5f
    val hd = d * 0.5f
    val wallH = 3.5f
    val darkWall = floatArrayOf(0.16f, 0.17f, 0.22f)
    val crimsonTrim = floatArrayOf(0.98f, 0.24f, 0.34f)
    val amberNeon = floatArrayOf(1.0f, 0.72f, 0.16f)

    // Paved tile forecourt & raised plinth leading toward Sunset Blvd
    staticBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = 0.06f,
        cz = cz + 1.4f,
        sx = w + 2.4f,
        sy = 0.12f,
        sz = d + 4.4f,
        color = floatArrayOf(0.28f, 0.29f, 0.34f),
        surface = MaterialSurface.TILE,
        castShadow = false,
      )
    // Main tactical store enclosure (solid back & sides, open sales counter facing South +Z)
    staticBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = wallH * 0.5f + 0.12f,
        cz = cz - 0.3f,
        sx = w,
        sy = wallH,
        sz = d - 0.8f,
        color = darkWall,
        surface = MaterialSurface.STUCCO,
      )
    aabbs += AabbCollider(cx - hw, cz - hd + 0.1f, cx + hw, cz + hd - 0.7f, minY = 0f, maxY = wallH + 0.56f, tag = "Vice Arms Shop")

    // Front counter bar + glowing gun display shelf
    staticBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = 0.70f,
        cz = cz + hd - 0.3f,
        sx = w - 0.8f,
        sy = 1.15f,
        sz = 0.75f,
        color = floatArrayOf(0.24f, 0.20f, 0.18f),
        surface = MaterialSurface.WOOD,
      )
    aabbs += AabbCollider(cx - hw + 0.3f, cz + hd - 0.7f, cx + hw - 0.3f, cz + hd + 0.1f, minY = 0f, maxY = 1.28f, tag = "Arms Counter")
    walkableFloors +=
      WalkableFloorSpec(
        minX = cx - hw + 0.3f,
        minZ = cz + hd - 0.7f,
        maxX = cx + hw - 0.3f,
        maxZ = cz + hd + 0.1f,
        baseElevationY = 1.28f,
        tag = "Arms Counter",
      )

    // Illuminated glass weapon rack display on counter front
    glassBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = 1.55f,
        cz = cz + hd - 0.1f,
        sx = w - 1.6f,
        sy = 0.52f,
        sz = 0.22f,
        color = floatArrayOf(0.95f, 0.75f, 0.28f),
        surface = MaterialSurface.GLASS,
        castShadow = false,
      )

    // Roof canopy + Glowing Neon "GUN SHOP" Marquee Sign facing South
    staticBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = wallH + 0.35f,
        cz = cz + 0.2f,
        sx = w + 1.4f,
        sy = 0.42f,
        sz = d + 1.4f,
        color = floatArrayOf(0.12f, 0.13f, 0.18f),
        surface = MaterialSurface.SLATE,
      )
    walkableFloors +=
      WalkableFloorSpec(
        minX = cx - hw - 0.7f,
        minZ = cz + 0.2f - hd - 0.7f,
        maxX = cx + hw + 0.7f,
        maxZ = cz + 0.2f + hd + 0.7f,
        baseElevationY = wallH + 0.56f,
        tag = "Vice Arms Roof",
      )
    staticBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = wallH + 0.72f,
        cz = cz + hd + 0.45f,
        sx = 4.6f,
        sy = 0.62f,
        sz = 0.32f,
        color = crimsonTrim,
        surface = MaterialSurface.EMISSIVE,
        emissive = 0.95f,
        castShadow = false,
      )
    staticBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = wallH + 0.18f,
        cz = cz + hd + 0.90f,
        sx = w + 1.42f,
        sy = 0.14f,
        sz = 0.16f,
        color = amberNeon,
        surface = MaterialSurface.EMISSIVE,
        emissive = 0.90f,
        castShadow = false,
      )
  }

  private fun buildEnterableHouse(
    h: HouseSpec,
    staticBoxes: MutableList<PropBoxSpec>,
    glassBoxes: MutableList<PropBoxSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
  ) {
    val cx = h.centerX
    val cz = h.centerZ
    val hw = h.width / 2f
    val hd = h.depth / 2f
    val wallT = 0.42f
    val wh = h.wallHeight
    val floorY = 0.14f

    walkableFloors +=
      WalkableFloorSpec(
        minX = cx - hw - 0.6f,
        minZ = cz - hd - 0.8f,
        maxX = cx + hw + 0.6f,
        maxZ = cz + hd + 0.8f,
        baseElevationY = floorY,
        tag = "${h.name} 1F",
      )

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

  /**
   * Builds a full 2-story walkable building with an open ground-floor lobby, a walkable
   * solid 3D interior staircase on the right wing, an open 2nd-floor lounge loft, and an
   * outdoor 2nd-floor panoramic balcony with waist-high guard rails.
   */
  private fun buildTwoStoryHouse(
    h: HouseSpec,
    staticBoxes: MutableList<PropBoxSpec>,
    glassBoxes: MutableList<PropBoxSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
  ) {
    val cx = h.centerX
    val cz = h.centerZ
    val hw = h.width * 0.5f
    val hd = h.depth * 0.5f
    val wallT = 0.42f
    val wh = h.wallHeight
    val floor1Y = 0.14f
    val floor2Y = floor1Y + wh // e.g., 3.54f
    val roofY = floor2Y + wh   // e.g., 6.94f
    val totalWallH = wh * 2f

    // 1. Ground Floor Slab + Entrance Walkway
    staticBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = floor1Y * 0.5f,
        cz = cz,
        sx = h.width + 1.2f,
        sy = floor1Y,
        sz = h.depth + 1.6f,
        color = floatArrayOf(0.78f, 0.75f, 0.71f),
        surface = MaterialSurface.TILE,
        roughness = 0.45f,
        castShadow = false,
      )
    walkableFloors +=
      WalkableFloorSpec(
        minX = cx - hw - 0.6f,
        minZ = cz - hd - 0.8f,
        maxX = cx + hw + 0.6f,
        maxZ = cz + hd + 0.8f,
        baseElevationY = floor1Y,
        tag = "${h.name} 1F",
      )

    val walkZ = if (h.facingSouth) cz + hd + 5.2f else cz - hd - 5.2f
    staticBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = 0.03f,
        cz = walkZ,
        sx = 3.8f,
        sy = 0.05f,
        sz = 9.2f,
        color = floatArrayOf(0.72f, 0.70f, 0.66f),
        surface = MaterialSurface.TILE,
        castShadow = false,
      )

    // 2. Side Walls (2 stories tall with picture windows on both floors!)
    for (side in listOf(-1f, 1f)) {
      val wx = cx + side * (hw - wallT * 0.5f)
      // Lower wainscot & upper header + mid floor fascia
      for (floorBase in listOf(floor1Y, floor2Y)) {
        staticBoxes +=
          PropBoxSpec(
            cx = wx,
            cy = floorBase + 0.52f,
            cz = cz,
            sx = wallT,
            sy = 1.04f,
            sz = h.depth,
            color = h.primaryColor,
            surface = MaterialSurface.STUCCO,
          )
        staticBoxes +=
          PropBoxSpec(
            cx = wx,
            cy = floorBase + wh - 0.45f,
            cz = cz,
            sx = wallT,
            sy = 0.90f,
            sz = h.depth,
            color = h.primaryColor,
            surface = MaterialSurface.STUCCO,
          )
        glassBoxes +=
          PropBoxSpec(
            cx = wx,
            cy = floorBase + wh * 0.52f,
            cz = cz,
            sx = 0.08f,
            sy = wh - 1.94f,
            sz = h.depth - 4.2f,
            color = floatArrayOf(0.55f, 0.86f, 0.96f),
            surface = MaterialSurface.GLASS,
            roughness = 0.08f,
            castShadow = false,
          )
      }
      for (corner in listOf(-1f, 1f)) {
        staticBoxes +=
          PropBoxSpec(
            cx = wx,
            cy = floor1Y + totalWallH * 0.5f,
            cz = cz + corner * (hd - 1.05f),
            sx = wallT,
            sy = totalWallH,
            sz = 2.1f,
            color = h.primaryColor,
            surface = MaterialSurface.STUCCO,
          )
      }
      aabbs += AabbCollider(wx - 0.30f, cz - hd, wx + 0.30f, cz + hd, minY = 0f, maxY = roofY, tag = h.name)
    }

    // 3. Solid Back Wall (full 2 stories)
    val backZ = if (h.facingSouth) cz - hd + wallT * 0.5f else cz + hd - wallT * 0.5f
    staticBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = floor1Y + totalWallH * 0.5f,
        cz = backZ,
        sx = h.width,
        sy = totalWallH,
        sz = wallT,
        color = h.primaryColor,
        surface = MaterialSurface.STUCCO,
      )
    aabbs += AabbCollider(cx - hw, backZ - 0.30f, cx + hw, backZ + 0.30f, minY = 0f, maxY = roofY, tag = h.name)

    // 4. Front Wall with Open Entrance Doorway (1F) AND Open Balcony Doorway (2F)
    val frontZ = if (h.facingSouth) cz + hd - wallT * 0.5f else cz - hd + wallT * 0.5f
    val doorHalf = 1.65f
    val segWidth = hw - doorHalf
    staticBoxes +=
      PropBoxSpec(
        cx = cx - doorHalf - segWidth * 0.5f,
        cy = floor1Y + totalWallH * 0.5f,
        cz = frontZ,
        sx = segWidth,
        sy = totalWallH,
        sz = wallT,
        color = h.primaryColor,
        surface = MaterialSurface.STUCCO,
      )
    aabbs += AabbCollider(cx - hw, frontZ - 0.30f, cx - doorHalf, frontZ + 0.30f, minY = 0f, maxY = roofY, tag = h.name)

    staticBoxes +=
      PropBoxSpec(
        cx = cx + doorHalf + segWidth * 0.5f,
        cy = floor1Y + totalWallH * 0.5f,
        cz = frontZ,
        sx = segWidth,
        sy = totalWallH,
        sz = wallT,
        color = h.primaryColor,
        surface = MaterialSurface.STUCCO,
      )
    aabbs += AabbCollider(cx + doorHalf, frontZ - 0.30f, cx + hw, frontZ + 0.30f, minY = 0f, maxY = roofY, tag = h.name)

    // Door lintels above 1F and 2F doorways
    for (floorBase in listOf(floor1Y, floor2Y)) {
      staticBoxes +=
        PropBoxSpec(
          cx = cx,
          cy = floorBase + wh - 0.48f,
          cz = frontZ,
          sx = doorHalf * 2f,
          sy = 0.96f,
          sz = wallT,
          color = h.accentColor,
          surface = MaterialSurface.STUCCO,
        )
    }

    // 5. Interior Staircase on Right Wing (walkable up & down!) + 2nd Floor Cutout Slab
    val stairMinX = cx + hw - 3.65f
    val stairMaxX = cx + hw - 0.55f
    val stairWidth = stairMaxX - stairMinX
    val stairCx = (stairMinX + stairMaxX) * 0.5f

    // Stair runs along Z from front-interior (y=floor1Y) toward back-interior landing (y=floor2Y)
    val stairFootZ = if (h.facingSouth) cz + hd - 2.3f else cz - hd + 2.3f
    val stairTopZ = if (h.facingSouth) cz - hd + 2.6f else cz + hd - 2.6f
    val stairZMin = kotlin.math.min(stairFootZ, stairTopZ)
    val stairZMax = kotlin.math.max(stairFootZ, stairTopZ)
    val rampAxis = if (h.facingSouth) RampAxis.NEG_Z else RampAxis.POS_Z

    // Walkable Stair Ramp Physics Surface
    walkableFloors +=
      WalkableFloorSpec(
        minX = stairMinX - 0.1f,
        minZ = stairZMin,
        maxX = stairMaxX + 0.1f,
        maxZ = stairZMax,
        baseElevationY = floor1Y,
        endElevationY = floor2Y,
        rampAxis = rampAxis,
        tag = "${h.name} Stairs",
      )

    // Render 11 clean stepped 3D wood stair treads & riser stringer
    val stepCount = 11
    val stepRise = (floor2Y - floor1Y) / stepCount
    val stepRunZ = (stairTopZ - stairFootZ) / stepCount
    for (s in 0 until stepCount) {
      val sy = floor1Y + stepRise * (s + 0.5f)
      val szPos = stairFootZ + stepRunZ * (s + 0.5f)
      staticBoxes +=
        PropBoxSpec(
          cx = stairCx,
          cy = floor1Y + (sy - floor1Y) * 0.5f,
          cz = szPos,
          sx = stairWidth - 0.1f,
          sy = (sy - floor1Y) + stepRise * 0.5f,
          sz = kotlin.math.abs(stepRunZ) + 0.08f,
          color = floatArrayOf(0.62f, 0.40f, 0.23f),
          surface = MaterialSurface.WOOD,
        )
    }

    // Left Loft 2nd Floor Slab (full depth on left of stairwell: x from wall to stairMinX)
    val leftLoftW = stairMinX - (cx - hw + wallT * 0.5f)
    val leftLoftCx = (cx - hw + wallT * 0.5f) + leftLoftW * 0.5f
    staticBoxes +=
      PropBoxSpec(
        cx = leftLoftCx,
        cy = floor2Y - 0.12f,
        cz = cz,
        sx = leftLoftW,
        sy = 0.24f,
        sz = h.depth - wallT,
        color = floatArrayOf(0.84f, 0.78f, 0.68f),
        surface = MaterialSurface.WOOD,
      )
    walkableFloors +=
      WalkableFloorSpec(
        minX = cx - hw + 0.25f,
        minZ = cz - hd + 0.25f,
        maxX = stairMinX + 0.15f,
        maxZ = cz + hd - 0.25f,
        baseElevationY = floor2Y,
        tag = "${h.name} 2F",
      )

    // Back Landing 2nd Floor Slab behind top of stairs (connects stair top cleanly to left loft!)
    val backLandDepth = kotlin.math.abs((if (h.facingSouth) backZ else backZ) - stairTopZ) - wallT * 0.4f
    val backLandCz = if (h.facingSouth) (backZ + stairTopZ) * 0.5f else (stairTopZ + backZ) * 0.5f
    staticBoxes +=
      PropBoxSpec(
        cx = stairCx,
        cy = floor2Y - 0.12f,
        cz = backLandCz,
        sx = stairWidth + 0.3f,
        sy = 0.24f,
        sz = backLandDepth.coerceAtLeast(1.8f),
        color = floatArrayOf(0.84f, 0.78f, 0.68f),
        surface = MaterialSurface.WOOD,
      )
    val landZMin = kotlin.math.min(backZ, stairTopZ)
    val landZMax = kotlin.math.max(backZ, stairTopZ)
    walkableFloors +=
      WalkableFloorSpec(
        minX = stairMinX - 0.35f,
        minZ = landZMin,
        maxX = cx + hw - 0.25f,
        maxZ = landZMax,
        baseElevationY = floor2Y,
        tag = "${h.name} 2F Landing",
      )

    // Stairwell Glass/Steel Guardrail on 2F along x = stairMinX so player uses landing safely
    staticBoxes +=
      PropBoxSpec(
        cx = stairMinX - 0.08f,
        cy = floor2Y + 0.52f,
        cz = (stairZMin + stairZMax) * 0.5f,
        sx = 0.14f,
        sy = 1.04f,
        sz = stairZMax - stairZMin,
        color = h.accentColor,
        surface = MaterialSurface.STUCCO,
      )
    aabbs +=
      AabbCollider(
        minX = stairMinX - 0.24f,
        minZ = stairZMin,
        maxX = stairMinX + 0.10f,
        maxZ = stairZMax,
        minY = floor2Y,
        maxY = floor2Y + 1.2f,
        tag = "${h.name} Stair Rail",
      )

    // 6. Outdoor 2F Panoramic Balcony Overhanging Front Porch!
    val balcSign = if (h.facingSouth) 1f else -1f
    val balcDepth = 2.9f
    val balcCz = frontZ + balcSign * (balcDepth * 0.5f + 0.15f)
    staticBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = floor2Y - 0.12f,
        cz = balcCz,
        sx = h.width,
        sy = 0.24f,
        sz = balcDepth,
        color = floatArrayOf(0.76f, 0.74f, 0.70f),
        surface = MaterialSurface.TILE,
      )
    val balcZMin = kotlin.math.min(frontZ, frontZ + balcSign * balcDepth)
    val balcZMax = kotlin.math.max(frontZ, frontZ + balcSign * balcDepth)
    walkableFloors +=
      WalkableFloorSpec(
        minX = cx - hw + 0.3f,
        minZ = balcZMin,
        maxX = cx + hw - 0.3f,
        maxZ = balcZMax,
        baseElevationY = floor2Y,
        tag = "${h.name} 2F Balcony",
      )

    // Balcony front & side waist-high railings (only collide on 2nd floor!)
    val outerBalcZ = frontZ + balcSign * balcDepth
    staticBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = floor2Y + 0.52f,
        cz = outerBalcZ,
        sx = h.width,
        sy = 1.04f,
        sz = 0.18f,
        color = h.accentColor,
        surface = MaterialSurface.STUCCO,
      )
    aabbs +=
      AabbCollider(
        cx - hw,
        outerBalcZ - 0.22f,
        cx + hw,
        outerBalcZ + 0.22f,
        minY = floor2Y,
        maxY = floor2Y + 1.2f,
        tag = "${h.name} Balcony Rail",
      )
    for (bx in listOf(cx - hw + 0.15f, cx + hw - 0.15f)) {
      staticBoxes +=
        PropBoxSpec(
          cx = bx,
          cy = floor2Y + 0.52f,
          cz = balcCz,
          sx = 0.18f,
          sy = 1.04f,
          sz = balcDepth,
          color = h.accentColor,
          surface = MaterialSurface.STUCCO,
        )
      aabbs +=
        AabbCollider(
          bx - 0.22f,
          balcZMin,
          bx + 0.22f,
          balcZMax,
          minY = floor2Y,
          maxY = floor2Y + 1.2f,
          tag = "${h.name} Balcony Side",
        )
    }

    // Porch support pillars holding up 2F balcony corners
    val porchPillarZ = outerBalcZ - balcSign * 0.25f
    for (px in listOf(cx - hw + 0.6f, cx + hw - 0.6f)) {
      staticBoxes +=
        PropBoxSpec(
          cx = px,
          cy = floor1Y + wh * 0.5f,
          cz = porchPillarZ,
          sx = 0.38f,
          sy = wh,
          sz = 0.38f,
          color = h.accentColor,
          surface = MaterialSurface.STUCCO,
        )
      aabbs +=
        AabbCollider(
          px - 0.25f,
          porchPillarZ - 0.25f,
          px + 0.25f,
          porchPillarZ + 0.25f,
          minY = 0f,
          maxY = floor2Y,
          tag = h.name,
        )
    }

    // 7. Walkable Rooftop Terrace & Roof Access Staircase (Left Wing of 2F -> Open Roof Deck!)
    if (h.hasRooftop) {
      val rStairMinX = cx - hw + 0.55f
      val rStairMaxX = cx - hw + 2.65f
      val rStairCx = (rStairMinX + rStairMaxX) * 0.5f
      val rStairW = rStairMaxX - rStairMinX
      val rFootZ = if (h.facingSouth) cz - hd + 2.6f else cz + hd - 2.6f
      val rTopZ = if (h.facingSouth) cz + hd - 2.5f else cz - hd + 2.5f
      val rZMin = kotlin.math.min(rFootZ, rTopZ)
      val rZMax = kotlin.math.max(rFootZ, rTopZ)

      walkableFloors +=
        WalkableFloorSpec(
          minX = rStairMinX - 0.1f,
          minZ = rZMin,
          maxX = rStairMaxX + 0.1f,
          maxZ = rZMax,
          baseElevationY = floor2Y,
          endElevationY = roofY,
          rampAxis = if (h.facingSouth) RampAxis.POS_Z else RampAxis.NEG_Z,
          tag = "${h.name} Roof Stairs",
        )

      val rSteps = 10
      val rRise = (roofY - floor2Y) / rSteps
      val rRunZ = (rTopZ - rFootZ) / rSteps
      for (s in 0 until rSteps) {
        val sy = floor2Y + rRise * (s + 0.5f)
        val szPos = rFootZ + rRunZ * (s + 0.5f)
        staticBoxes +=
          PropBoxSpec(
            cx = rStairCx,
            cy = floor2Y + (sy - floor2Y) * 0.5f,
            cz = szPos,
            sx = rStairW - 0.1f,
            sy = (sy - floor2Y) + rRise * 0.5f,
            sz = kotlin.math.abs(rRunZ) + 0.08f,
            color = floatArrayOf(0.76f, 0.74f, 0.69f),
            surface = MaterialSurface.STUCCO,
          )
      }

      // Main roof deck right of roof stairwell opening [rStairMaxX .. cx + hw + 0.4]
      val mainRoofW = (cx + hw + 0.6f) - rStairMaxX
      val mainRoofCx = rStairMaxX + mainRoofW * 0.5f
      staticBoxes +=
        PropBoxSpec(
          cx = mainRoofCx,
          cy = roofY - 0.12f,
          cz = cz,
          sx = mainRoofW,
          sy = 0.24f,
          sz = h.depth + 1.2f,
          color = h.roofColor,
          surface = MaterialSurface.STUCCO,
          roughness = 0.50f,
        )
      walkableFloors +=
        WalkableFloorSpec(
          minX = rStairMaxX - 0.15f,
          minZ = cz - hd - 0.4f,
          maxX = cx + hw + 0.5f,
          maxZ = cz + hd + 0.4f,
          baseElevationY = roofY,
          tag = "${h.name} Rooftop",
        )

      // Landing roof slab behind top of roof stairs
      val rLandZ0 = kotlin.math.min(frontZ, rTopZ)
      val rLandZ1 = kotlin.math.max(frontZ, rTopZ)
      val rLandDepth = (rLandZ1 - rLandZ0).coerceAtLeast(2.0f)
      staticBoxes +=
        PropBoxSpec(
          cx = rStairCx,
          cy = roofY - 0.12f,
          cz = (rLandZ0 + rLandZ1) * 0.5f,
          sx = rStairW + 0.6f,
          sy = 0.24f,
          sz = rLandDepth,
          color = h.roofColor,
          surface = MaterialSurface.STUCCO,
        )
      walkableFloors +=
        WalkableFloorSpec(
          minX = cx - hw - 0.4f,
          minZ = rLandZ0 - 0.1f,
          maxX = rStairMaxX + 0.2f,
          maxZ = rLandZ1 + 0.1f,
          baseElevationY = roofY,
          tag = "${h.name} Roof Landing",
        )

      // Rooftop Stair Guardrail along x = rStairMaxX
      staticBoxes +=
        PropBoxSpec(
          cx = rStairMaxX + 0.08f,
          cy = roofY + 0.48f,
          cz = (rZMin + rZMax) * 0.5f,
          sx = 0.16f,
          sy = 0.96f,
          sz = rZMax - rZMin,
          color = h.accentColor,
          surface = MaterialSurface.STUCCO,
        )
      aabbs +=
        AabbCollider(
          minX = rStairMaxX - 0.10f,
          minZ = rZMin,
          maxX = rStairMaxX + 0.22f,
          maxZ = rZMax,
          minY = roofY,
          maxY = roofY + 1.15f,
          tag = "${h.name} Roof Rail",
        )

      // Rooftop Perimeter Parapets (waist-high walls on rooftop level only)
      for (rx in listOf(cx - hw + 0.14f, cx + hw - 0.14f)) {
        staticBoxes += PropBoxSpec(rx, roofY + 0.42f, cz, 0.28f, 0.84f, h.depth, h.primaryColor, MaterialSurface.STUCCO)
        aabbs += AabbCollider(rx - 0.22f, cz - hd, rx + 0.22f, cz + hd, minY = roofY, maxY = roofY + 1.15f, tag = "${h.name} Parapet")
      }
      for (rz in listOf(cz - hd + 0.14f, cz + hd - 0.14f)) {
        staticBoxes += PropBoxSpec(cx, roofY + 0.42f, rz, h.width, 0.84f, 0.28f, h.primaryColor, MaterialSurface.STUCCO)
        aabbs += AabbCollider(cx - hw, rz - 0.22f, cx + hw, rz + 0.22f, minY = roofY, maxY = roofY + 1.15f, tag = "${h.name} Parapet")
      }
    } else {
      staticBoxes +=
        PropBoxSpec(
          cx = cx,
          cy = roofY + 0.24f,
          cz = cz,
          sx = h.width + 1.8f,
          sy = 0.48f,
          sz = h.depth + 2.0f,
          color = h.roofColor,
          surface = MaterialSurface.SLATE,
          roughness = 0.50f,
        )
    }
    staticBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = roofY + 0.08f,
        cz = frontZ + balcSign * 1.0f,
        sx = h.width + 1.2f,
        sy = 0.14f,
        sz = 0.16f,
        color = h.neonColor,
        surface = MaterialSurface.EMISSIVE,
        emissive = 0.85f,
        castShadow = false,
      )
    staticBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = floor2Y - 0.04f,
        cz = outerBalcZ,
        sx = h.width,
        sy = 0.12f,
        sz = 0.22f,
        color = h.neonColor,
        surface = MaterialSurface.EMISSIVE,
        emissive = 0.78f,
        castShadow = false,
      )

    // 8. Height-Aware 1st Floor + 2nd Floor Furniture & Neon Wall Art
    val sofa1Z = if (h.facingSouth) cz - 1.9f else cz + 1.9f
    staticBoxes +=
      PropBoxSpec(
        cx = cx - 2.6f,
        cy = floor1Y + 0.32f,
        cz = sofa1Z,
        sx = 3.2f,
        sy = 0.55f,
        sz = 1.15f,
        color = floatArrayOf(0.92f, 0.89f, 0.84f),
        surface = MaterialSurface.STUCCO,
      )
    aabbs +=
      AabbCollider(
        cx - 4.2f,
        sofa1Z - 0.60f,
        cx - 1.0f,
        sofa1Z + 0.60f,
        minY = floor1Y,
        maxY = floor1Y + 1.1f,
        tag = "${h.name} 1F Sofa",
      )

    // 2F Upstairs Lounge Coffee Table & Rug on Left Loft
    staticBoxes +=
      PropBoxSpec(
        cx = cx - 3.0f,
        cy = floor2Y + 0.26f,
        cz = cz,
        sx = 2.0f,
        sy = 0.45f,
        sz = 1.2f,
        color = floatArrayOf(0.42f, 0.28f, 0.18f),
        surface = MaterialSurface.WOOD,
      )
    aabbs +=
      AabbCollider(
        cx - 4.0f,
        cz - 0.60f,
        cx - 2.0f,
        cz + 0.60f,
        minY = floor2Y,
        maxY = floor2Y + 1.0f,
        tag = "${h.name} 2F Table",
      )

    // Ceiling lamps on both 1F and 2F
    for (ceilY in listOf(floor2Y - 0.30f, roofY - 0.14f)) {
      staticBoxes +=
        PropBoxSpec(
          cx = cx - 1.5f,
          cy = ceilY,
          cz = cz,
          sx = 1.6f,
          sy = 0.10f,
          sz = 1.6f,
          color = floatArrayOf(1.0f, 0.92f, 0.76f),
          surface = MaterialSurface.EMISSIVE,
          emissive = 0.90f,
          castShadow = false,
        )
    }

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
          color = floatArrayOf(0.08f, 0.82f, 0.76f),
          surface = MaterialSurface.WATER,
          roughness = 0.10f,
          castShadow = false,
        )
    }
  }
}
