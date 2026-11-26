package com.example.game.world

import com.example.game.math.AabbCollider
import com.example.game.math.CircleCollider
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object TownAndValleyBuilder {

  /**
   * Adds authentic architectural features from the "Green Valley Enclave" design onto
   * Island 2 (x = 84..212): Grand Sandstone Double-Arch Gate, Perimeter Compound Walls,
   * Central Emerald Park + Octagonal Gazebo, Outdoor Green Tennis Court, and Clubhouse + Pool.
   */
  fun buildGreenValleyFeatures(
    staticBoxes: MutableList<PropBoxSpec>,
    staticCylinders: MutableList<PropCylinderSpec>,
    glassBoxes: MutableList<PropBoxSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
  ) {
    val creamStone = floatArrayOf(0.91f, 0.88f, 0.80f)
    val sandstoneTrim = floatArrayOf(0.80f, 0.67f, 0.48f)
    val parkGreen = floatArrayOf(0.24f, 0.58f, 0.28f)
    val parkPath = floatArrayOf(0.84f, 0.80f, 0.72f)
    val darkSlate = floatArrayOf(0.20f, 0.22f, 0.26f)
    val oliveRoof = floatArrayOf(0.68f, 0.34f, 0.22f)

    // ---------------------------------------------------------
    // 1. GRAND SANDSTONE ARCH ENTRANCE GATE STRADDLING ROAD AT x = 97.5, z = 16
    // ---------------------------------------------------------
    val gateX = 97.5f
    val gateZ = 16.0f
    // Main flanking stone gate piers (outside the 10m vehicle road: z = 9.9 and z = 22.1)
    for (pz in listOf(gateZ - 6.1f, gateZ + 6.1f)) {
      staticBoxes +=
        PropBoxSpec(
          cx = gateX,
          cy = 2.8f,
          cz = pz,
          sx = 1.65f,
          sy = 5.6f,
          sz = 1.65f,
          color = creamStone,
          surface = MaterialSurface.STUCCO,
        )
      staticBoxes +=
        PropBoxSpec(
          cx = gateX,
          cy = 5.75f,
          cz = pz,
          sx = 1.95f,
          sy = 0.45f,
          sz = 1.95f,
          color = sandstoneTrim,
          surface = MaterialSurface.STUCCO,
        )
      aabbs +=
        AabbCollider(
          minX = gateX - 0.85f,
          minZ = pz - 0.85f,
          maxX = gateX + 0.85f,
          maxZ = pz + 0.85f,
          minY = 0f,
          maxY = 6.0f,
          tag = "Green Valley Gate Pier",
        )
    }
    // Side pedestrian archway piers
    for (pz in listOf(gateZ - 9.8f, gateZ + 9.8f)) {
      staticBoxes +=
        PropBoxSpec(
          cx = gateX,
          cy = 2.2f,
          cz = pz,
          sx = 1.25f,
          sy = 4.4f,
          sz = 1.25f,
          color = creamStone,
          surface = MaterialSurface.STUCCO,
        )
      aabbs +=
        AabbCollider(
          minX = gateX - 0.65f,
          minZ = pz - 0.65f,
          maxX = gateX + 0.65f,
          maxZ = pz + 0.65f,
          minY = 0f,
          maxY = 4.6f,
          tag = "Green Valley Side Gate",
        )
    }
    // Overhead Grand Archway Beam & Emblem Pediment
    staticBoxes +=
      PropBoxSpec(
        cx = gateX,
        cy = 5.25f,
        cz = gateZ,
        sx = 1.55f,
        sy = 1.25f,
        sz = 20.8f,
        color = creamStone,
        surface = MaterialSurface.STUCCO,
      )
    staticBoxes +=
      PropBoxSpec(
        cx = gateX,
        cy = 6.20f,
        cz = gateZ,
        sx = 1.85f,
        sy = 0.80f,
        sz = 12.2f,
        color = sandstoneTrim,
        surface = MaterialSurface.STUCCO,
      )
    staticBoxes +=
      PropBoxSpec(
        cx = gateX - 0.85f,
        cy = 5.30f,
        cz = gateZ,
        sx = 0.14f,
        sy = 0.52f,
        sz = 8.4f,
        color = floatArrayOf(0.22f, 0.86f, 0.48f),
        surface = MaterialSurface.EMISSIVE,
        emissive = 0.70f,
        castShadow = false,
      )
    // Security guardhouse booth next to entrance gate
    staticBoxes +=
      PropBoxSpec(
        cx = gateX + 3.2f,
        cy = 1.4f,
        cz = gateZ - 8.2f,
        sx = 3.2f,
        sy = 2.8f,
        sz = 2.6f,
        color = creamStone,
        surface = MaterialSurface.STUCCO,
      )
    staticBoxes +=
      PropBoxSpec(
        cx = gateX + 3.2f,
        cy = 2.95f,
        cz = gateZ - 8.2f,
        sx = 3.8f,
        sy = 0.30f,
        sz = 3.2f,
        color = darkSlate,
        surface = MaterialSurface.SLATE,
      )
    aabbs +=
      AabbCollider(
        minX = gateX + 1.5f,
        minZ = gateZ - 9.6f,
        maxX = gateX + 4.9f,
        maxZ = gateZ - 6.8f,
        minY = 0f,
        maxY = 3.10f,
        tag = "Enclave Security Gatehouse",
      )
    walkableFloors +=
      WalkableFloorSpec(
        minX = gateX + 1.3f,
        minZ = gateZ - 9.8f,
        maxX = gateX + 5.1f,
        maxZ = gateZ - 6.6f,
        baseElevationY = 3.10f,
        tag = "Enclave Gatehouse Roof",
      )

    // ---------------------------------------------------------
    // 2. ENCLAVE PERIMETER COMPOUND WALLS (WITH WIDE ROAD GAPS)
    // ---------------------------------------------------------
    // West boundary wall along x = 94.5 (gaps for bridge roads at z=-29..-19 and z=11..21)
    val westSegments =
      listOf(
        Pair(-58f, -30f),
        Pair(-18f, 6f),
        Pair(26f, 47f),
      )
    for ((z0, z1) in westSegments) {
      val len = z1 - z0
      val cz = (z0 + z1) * 0.5f
      staticBoxes +=
        PropBoxSpec(
          cx = 94.5f,
          cy = 0.90f,
          cz = cz,
          sx = 0.55f,
          sy = 1.80f,
          sz = len,
          color = creamStone,
          surface = MaterialSurface.STUCCO,
        )
      aabbs += AabbCollider(94.1f, z0, 94.9f, z1, minY = 0f, maxY = 2.0f, tag = "Enclave Wall")
    }
    // North & East compound boundary walls (with open road gates to North Highlands & East Market)
    for ((x0, x1) in listOf(95f to 139f, 157f to 205f)) {
      val cx = (x0 + x1) * 0.5f
      val len = x1 - x0
      staticBoxes +=
        PropBoxSpec(
          cx = cx,
          cy = 0.90f,
          cz = -58.5f,
          sx = len,
          sy = 1.80f,
          sz = 0.55f,
          color = creamStone,
          surface = MaterialSurface.STUCCO,
        )
      aabbs += AabbCollider(x0, -58.9f, x1, -58.1f, minY = 0f, maxY = 2.0f, tag = "Enclave North Wall")
    }
    for ((z0, z1) in listOf(-58.5f to -29.8f, -18.2f to 10.2f, 21.8f to 46.5f)) {
      val cz = (z0 + z1) * 0.5f
      val len = z1 - z0
      staticBoxes +=
        PropBoxSpec(
          cx = 204.5f,
          cy = 0.90f,
          cz = cz,
          sx = 0.55f,
          sy = 1.80f,
          sz = len,
          color = creamStone,
          surface = MaterialSurface.STUCCO,
        )
      aabbs += AabbCollider(204.1f, z0, 204.9f, z1, minY = 0f, maxY = 2.0f, tag = "Enclave East Wall")
    }

    // ---------------------------------------------------------
    // 3. CENTRAL GREEN VALLEY PARK & OCTAGONAL GAZEBO (148, -4)
    // ---------------------------------------------------------
    staticBoxes +=
      PropBoxSpec(
        cx = 148f,
        cy = 0.04f,
        cz = -4f,
        sx = 28f,
        sy = 0.08f,
        sz = 26f,
        color = parkGreen,
        surface = MaterialSurface.GRASS,
        castShadow = false,
      )
    // Cross garden stone walkways
    staticBoxes +=
      PropBoxSpec(
        cx = 148f,
        cy = 0.085f,
        cz = -4f,
        sx = 26f,
        sy = 0.04f,
        sz = 2.4f,
        color = parkPath,
        surface = MaterialSurface.TILE,
        castShadow = false,
      )
    staticBoxes +=
      PropBoxSpec(
        cx = 148f,
        cy = 0.085f,
        cz = -4f,
        sx = 2.4f,
        sy = 0.04f,
        sz = 24f,
        color = parkPath,
        surface = MaterialSurface.TILE,
        castShadow = false,
      )

    // Central Octagonal/Hexagonal Gazebo Pavilion at (148, -5.5) — open walk-in center!
    val gazX = 148f
    val gazZ = -5.5f
    staticCylinders +=
      PropCylinderSpec(
        cx = gazX,
        cy = 0.22f,
        cz = gazZ,
        radius = 3.2f,
        height = 0.28f,
        color = parkPath,
        surface = MaterialSurface.STUCCO,
      )
    for (i in 0 until 6) {
      val a = (i * (2.0 * PI / 6.0)).toFloat()
      val px = gazX + cos(a) * 2.65f
      val pz = gazZ + sin(a) * 2.65f
      staticCylinders +=
        PropCylinderSpec(
          cx = px,
          cy = 1.65f,
          cz = pz,
          radius = 0.16f,
          height = 2.8f,
          color = floatArrayOf(0.55f, 0.34f, 0.20f),
          surface = MaterialSurface.WOOD,
        )
    }
    // Layered Pyramidal Gazebo Roof
    staticCylinders +=
      PropCylinderSpec(
        cx = gazX,
        cy = 3.25f,
        cz = gazZ,
        radius = 3.55f,
        height = 0.36f,
        color = oliveRoof,
        surface = MaterialSurface.STUCCO,
      )
    staticCylinders +=
      PropCylinderSpec(
        cx = gazX,
        cy = 3.68f,
        cz = gazZ,
        radius = 2.25f,
        height = 0.50f,
        color = oliveRoof,
        surface = MaterialSurface.STUCCO,
      )
    staticCylinders +=
      PropCylinderSpec(
        cx = gazX,
        cy = 4.10f,
        cz = gazZ,
        radius = 0.90f,
        height = 0.40f,
        color = sandstoneTrim,
        surface = MaterialSurface.STUCCO,
      )

    // ---------------------------------------------------------
    // 4. OUTDOOR GREEN TENNIS COURT (164..183, -14..4)
    // ---------------------------------------------------------
    val tcX = 173.5f
    val tcZ = -5.0f
    // Outer Terracotta court apron
    staticBoxes +=
      PropBoxSpec(
        cx = tcX,
        cy = 0.05f,
        cz = tcZ,
        sx = 18.0f,
        sy = 0.10f,
        sz = 19.0f,
        color = floatArrayOf(0.76f, 0.38f, 0.28f),
        surface = MaterialSurface.TILE,
        castShadow = false,
      )
    // Inner Forest-Green Acrylic Play Surface
    staticBoxes +=
      PropBoxSpec(
        cx = tcX,
        cy = 0.07f,
        cz = tcZ,
        sx = 13.6f,
        sy = 0.08f,
        sz = 15.6f,
        color = floatArrayOf(0.15f, 0.44f, 0.26f),
        surface = MaterialSurface.TILE,
        castShadow = false,
      )
    // Crisp white boundary markings
    val whiteLine = floatArrayOf(0.96f, 0.97f, 0.98f)
    staticBoxes += PropBoxSpec(tcX, 0.115f, tcZ - 7.4f, 13.0f, 0.02f, 0.16f, whiteLine, MaterialSurface.STUCCO, castShadow = false)
    staticBoxes += PropBoxSpec(tcX, 0.115f, tcZ + 7.4f, 13.0f, 0.02f, 0.16f, whiteLine, MaterialSurface.STUCCO, castShadow = false)
    staticBoxes += PropBoxSpec(tcX - 6.4f, 0.115f, tcZ, 0.16f, 0.02f, 14.9f, whiteLine, MaterialSurface.STUCCO, castShadow = false)
    staticBoxes += PropBoxSpec(tcX + 6.4f, 0.115f, tcZ, 0.16f, 0.02f, 14.9f, whiteLine, MaterialSurface.STUCCO, castShadow = false)
    // Tennis Net & Posts along tcZ
    for (nx in listOf(tcX - 6.8f, tcX + 6.8f)) {
      staticCylinders += PropCylinderSpec(nx, 0.58f, tcZ, 0.10f, 1.0f, darkSlate, MaterialSurface.SLATE)
    }
    glassBoxes +=
      PropBoxSpec(
        cx = tcX,
        cy = 0.56f,
        cz = tcZ,
        sx = 13.5f,
        sy = 0.86f,
        sz = 0.06f,
        color = floatArrayOf(0.92f, 0.94f, 0.96f),
        surface = MaterialSurface.GLASS,
        castShadow = false,
      )
    staticBoxes += PropBoxSpec(tcX, 1.00f, tcZ, 13.6f, 0.08f, 0.08f, whiteLine, MaterialSurface.STUCCO)
    aabbs += AabbCollider(tcX - 6.8f, tcZ - 0.15f, tcX + 6.8f, tcZ + 0.15f, minY = 0f, maxY = 1.15f, tag = "Tennis Net")

    // ---------------------------------------------------------
    // 5. CLUBHOUSE PAVILION & TURQUOISE SWIMMING POOL (126..137, 0..9)
    // ---------------------------------------------------------
    val chX = 128.0f
    val chZ = 1.0f
    staticBoxes += PropBoxSpec(chX, 0.09f, chZ + 2.5f, 13.0f, 0.18f, 12.5f, floatArrayOf(0.84f, 0.78f, 0.70f), MaterialSurface.TILE, castShadow = false)
    // Clubhouse modern wood & cream walls
    staticBoxes += PropBoxSpec(chX, 1.65f, chZ - 1.5f, 8.2f, 3.1f, 3.8f, creamStone, MaterialSurface.STUCCO)
    staticBoxes += PropBoxSpec(chX, 3.35f, chZ - 1.2f, 9.6f, 0.38f, 5.4f, floatArrayOf(0.45f, 0.28f, 0.18f), MaterialSurface.WOOD)
    aabbs += AabbCollider(chX - 4.1f, chZ - 3.4f, chX + 4.1f, chZ + 0.4f, minY = 0f, maxY = 3.54f, tag = "Green Valley Clubhouse")
    walkableFloors +=
      WalkableFloorSpec(
        minX = chX - 4.8f,
        minZ = chZ - 3.9f,
        maxX = chX + 4.8f,
        maxZ = chZ + 1.5f,
        baseElevationY = 3.54f,
        tag = "Green Valley Clubhouse Roof",
      )
    // Turquoise Resort Swimming Pool
    staticBoxes +=
      PropBoxSpec(
        cx = chX,
        cy = 0.12f,
        cz = chZ + 5.2f,
        sx = 9.4f,
        sy = 0.10f,
        sz = 4.6f,
        color = floatArrayOf(0.10f, 0.84f, 0.88f),
        surface = MaterialSurface.WATER,
        roughness = 0.10f,
        castShadow = false,
      )
  }

  /**
   * Builds Map 3 ("Rooftop Town Street") on West Island (x = -212..-84, centered at x = -148):
   * - Connected to Key Bayside via Dual West Channel Bridges (-101..-47 at z=16 & z=-24)
   * - Straight North-South asphalt street at x = -148 with dashed WHITE centerlines & sidewalks
   * - 4 Multi-Floor + Walkable Rooftop Town Houses facing the main street (2 Blue/Ochre, 2 Red/Cream)
   * - Steel Water Tower landmark, West Town cars, traffic & citizens
   */
  fun buildRooftopTownDistrict(
    staticBoxes: MutableList<PropBoxSpec>,
    staticCylinders: MutableList<PropCylinderSpec>,
    glassBoxes: MutableList<PropBoxSpec>,
    aabbs: MutableList<AabbCollider>,
    circles: MutableList<CircleCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
    trees: MutableList<TreeSpec>,
    streetLights: MutableList<StreetLightSpec>,
    houses: MutableList<HouseSpec>,
    vehicleSpawns: MutableList<VehicleSpawnSpec>,
    boatSpawns: MutableList<BoatSpawnSpec>,
    pedestrianSpawns: MutableList<PedestrianSpawnSpec>,
  ) {
    val roadColor = floatArrayOf(0.20f, 0.21f, 0.24f)
    val sidewalkColor = floatArrayOf(0.76f, 0.74f, 0.69f)
    val whiteStripe = floatArrayOf(0.95f, 0.95f, 0.96f)
    val earthGrass = floatArrayOf(0.32f, 0.48f, 0.23f)
    val sandyShoulder = floatArrayOf(0.78f, 0.68f, 0.48f)

    // West Island Terrain surface rendered seamlessly by TerrainHeightfield chunks
    staticBoxes +=
      PropBoxSpec(
        cx = -148f,
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

    // Dual West Bridges connecting Key Bayside (-47) <-> Rooftop Town (-101)
    buildChannelBridgeSegment(
      xCenter = -74f,
      zCenter = 16f,
      accentNeon = floatArrayOf(1.0f, 0.68f, 0.20f),
      staticBoxes = staticBoxes,
      aabbs = aabbs,
      walkableFloors = walkableFloors,
    )
    buildChannelBridgeSegment(
      xCenter = -74f,
      zCenter = -24f,
      accentNeon = floatArrayOf(0.24f, 0.88f, 0.96f),
      staticBoxes = staticBoxes,
      aabbs = aabbs,
      walkableFloors = walkableFloors,
    )

    // Cross Streets connecting West Bridges to Main Town Avenue & Outer Loop
    staticBoxes += PropBoxSpec(-148f, 0.015f, 16f, 98f, 0.05f, 10f, roadColor, MaterialSurface.ASPHALT, roughness = 0.74f, castShadow = false)
    staticBoxes += PropBoxSpec(-148f, 0.015f, -24f, 98f, 0.05f, 10f, roadColor, MaterialSurface.ASPHALT, roughness = 0.74f, castShadow = false)
    // Outer loop North-South streets (x = -194 & x = -104)
    for (ax in listOf(-194f, -104f)) {
      staticBoxes += PropBoxSpec(ax, 0.015f, -4f, 10f, 0.05f, 50f, roadColor, MaterialSurface.ASPHALT, roughness = 0.74f, castShadow = false)
    }

    // MAIN ROOFTOP TOWN STREET along x = -148 (straight broad North-South boulevard with white stripes & sidewalks)
    staticBoxes +=
      PropBoxSpec(
        cx = -148f,
        cy = 0.02f,
        cz = -5f,
        sx = 10.6f,
        sy = 0.06f,
        sz = 108f,
        color = roadColor,
        surface = MaterialSurface.ASPHALT,
        roughness = 0.74f,
        castShadow = false,
      )
    // Concrete Sidewalks flanking the Main Town Street
    for (sx in listOf(-154.5f, -141.5f)) {
      staticBoxes +=
        PropBoxSpec(
          cx = sx,
          cy = 0.06f,
          cz = -5f,
          sx = 2.4f,
          sy = 0.12f,
          sz = 108f,
          color = sidewalkColor,
          surface = MaterialSurface.TILE,
          castShadow = false,
        )
    }
    // Crisp White Dashed Centerline Stripes & Curbs along x = -148
    for (z in -54..46 step 5) {
      staticBoxes +=
        PropBoxSpec(
          cx = -148f,
          cy = 0.052f,
          cz = z.toFloat(),
          sx = 0.24f,
          sy = 0.015f,
          sz = 2.4f,
          color = whiteStripe,
          surface = MaterialSurface.STUCCO,
          castShadow = false,
        )
    }

    // Northwest Elevated Steel Water Tower Monument at (-186, -48)
    val wtX = -186f
    val wtZ = -48f
    val steelColor = floatArrayOf(0.28f, 0.30f, 0.34f)
    for (dx in listOf(-1.6f, 1.6f)) {
      for (dz in listOf(-1.6f, 1.6f)) {
        staticCylinders += PropCylinderSpec(wtX + dx, 5.2f, wtZ + dz, 0.22f, 10.4f, steelColor, MaterialSurface.SLATE, metallic = 0.7f)
        aabbs += AabbCollider(wtX + dx - 0.3f, wtZ + dz - 0.3f, wtX + dx + 0.3f, wtZ + dz + 0.3f, tag = "Water Tower Leg")
      }
    }
    staticCylinders += PropCylinderSpec(wtX, 10.4f, wtZ, 2.75f, 0.35f, steelColor, MaterialSurface.SLATE, metallic = 0.6f)
    staticCylinders += PropCylinderSpec(wtX, 12.6f, wtZ, 2.15f, 4.1f, floatArrayOf(0.58f, 0.64f, 0.68f), MaterialSurface.STUCCO, metallic = 0.45f)
    staticCylinders += PropCylinderSpec(wtX, 14.8f, wtZ, 2.35f, 0.65f, steelColor, MaterialSurface.SLATE, metallic = 0.6f)

    // 4 Walkable 2-Story + Rooftop Town Street Houses facing x = -148
    val townHouses =
      listOf(
        HouseSpec(
          id = "house_town_blue_south",
          name = "Azure Bazaar House",
          subtitle = "Town 2F + Exterior Stairs & Rooftop",
          centerX = -173f,
          centerZ = -1.5f,
          width = 13.0f,
          depth = 12.0f,
          wallHeight = 3.4f,
          facingSouth = false,
          primaryColor = floatArrayOf(0.24f, 0.56f, 0.84f), // Sky Blue plaster
          accentColor = floatArrayOf(0.86f, 0.76f, 0.54f),  // Warm Ochre plaster
          roofColor = floatArrayOf(0.70f, 0.68f, 0.63f),
          neonColor = floatArrayOf(0.22f, 0.84f, 0.96f),
          floors = 2,
          facingX = +1,
          hasRooftop = true,
        ),
        HouseSpec(
          id = "house_town_red_south",
          name = "Crimson Corner Inn",
          subtitle = "Town 2F + Awning, Stairs & Rooftop",
          centerX = -123f,
          centerZ = -1.5f,
          width = 13.0f,
          depth = 12.0f,
          wallHeight = 3.4f,
          facingSouth = false,
          primaryColor = floatArrayOf(0.84f, 0.25f, 0.26f), // Crimson Red plaster
          accentColor = floatArrayOf(0.88f, 0.82f, 0.64f),  // Cream sandstone
          roofColor = floatArrayOf(0.70f, 0.68f, 0.63f),
          neonColor = floatArrayOf(0.98f, 0.34f, 0.36f),
          floors = 2,
          facingX = -1,
          hasRooftop = true,
        ),
        HouseSpec(
          id = "house_town_blue_north",
          name = "Cobalt Market Loft",
          subtitle = "Town 2F + Roof Water Tank Deck",
          centerX = -173f,
          centerZ = -39.5f,
          width = 13.0f,
          depth = 12.0f,
          wallHeight = 3.4f,
          facingSouth = false,
          primaryColor = floatArrayOf(0.26f, 0.58f, 0.86f),
          accentColor = floatArrayOf(0.90f, 0.54f, 0.22f),
          roofColor = floatArrayOf(0.72f, 0.69f, 0.64f),
          neonColor = floatArrayOf(1.0f, 0.72f, 0.24f),
          floors = 2,
          facingX = +1,
          hasRooftop = true,
        ),
        HouseSpec(
          id = "house_town_coral_north",
          name = "Terracotta Roof Flats",
          subtitle = "Town 2F + Stairs & Rooftop View",
          centerX = -123f,
          centerZ = -39.5f,
          width = 13.0f,
          depth = 12.0f,
          wallHeight = 3.4f,
          facingSouth = false,
          primaryColor = floatArrayOf(0.88f, 0.74f, 0.32f),
          accentColor = floatArrayOf(0.26f, 0.54f, 0.82f),
          roofColor = floatArrayOf(0.72f, 0.69f, 0.64f),
          neonColor = floatArrayOf(0.24f, 0.90f, 0.76f),
          floors = 2,
          facingX = -1,
          hasRooftop = true,
        ),
      )

    for (th in townHouses) {
      houses += th
      buildRooftopTownHouse(th, staticBoxes, staticCylinders, glassBoxes, aabbs, walkableFloors)
    }

    // Town Shade Trees & Streetlights lining the boulevard & fields
    val townTrees =
      listOf(
        TreeSpec(-160f, 34f, 7.0f, 15f, TreeKind.TROPICAL_SHADE, 0.04f),
        TreeSpec(-136f, 34f, 6.8f, 75f, TreeKind.TROPICAL_SHADE, -0.04f),
        TreeSpec(-160f, -15f, 6.6f, 125f, TreeKind.TROPICAL_SHADE, 0.05f),
        TreeSpec(-136f, -15f, 6.9f, 215f, TreeKind.TROPICAL_SHADE, -0.02f),
        TreeSpec(-182f, 24f, 7.4f, 45f, TreeKind.TROPICAL_SHADE, 0.06f),
        TreeSpec(-112f, 24f, 7.2f, 165f, TreeKind.TROPICAL_SHADE, -0.05f),
        TreeSpec(-184f, -18f, 7.1f, 245f, TreeKind.TROPICAL_SHADE, 0.02f),
        TreeSpec(-112f, -18f, 6.8f, 310f, TreeKind.TROPICAL_SHADE, 0.04f),
        TreeSpec(-178f, 49f, 8.2f, 30f, TreeKind.PALM),
        TreeSpec(-148f, 49f, 7.8f, 110f, TreeKind.PALM),
        TreeSpec(-116f, 49f, 8.3f, 220f, TreeKind.PALM),
      )
    for (t in townTrees) {
      trees += t
      circles += CircleCollider(t.x, t.z, 0.55f, "Tree")
    }

    val townLights =
      listOf(
        StreetLightSpec(-154f, 8f, 90f, floatArrayOf(1.0f, 0.78f, 0.30f)),
        StreetLightSpec(-142f, 8f, 270f, floatArrayOf(1.0f, 0.78f, 0.30f)),
        StreetLightSpec(-154f, -12f, 90f, floatArrayOf(0.24f, 0.90f, 0.98f)),
        StreetLightSpec(-142f, -12f, 270f, floatArrayOf(0.24f, 0.90f, 0.98f)),
        StreetLightSpec(-154f, -34f, 90f, floatArrayOf(1.0f, 0.78f, 0.30f)),
        StreetLightSpec(-142f, -34f, 270f, floatArrayOf(1.0f, 0.78f, 0.30f)),
      )
    for (sl in townLights) {
      streetLights += sl
      circles += CircleCollider(sl.x, sl.z, 0.28f, "Streetlight")
    }

    // Drivable Cars + Autonomous AI Town Cars
    vehicleSpawns +=
      listOf(
        VehicleSpawnSpec(
          id = "car_town_jeep",
          displayName = "Outback Street Rover",
          x = -144.8f,
          z = 27.0f,
          yawRad = PI.toFloat(),
          bodyColor = floatArrayOf(0.88f, 0.42f, 0.18f),
          trimColor = floatArrayOf(0.14f, 0.14f, 0.18f),
          maxSpeed = 35f,
          accel = 17f,
          isAiTraffic = false,
        ),
        VehicleSpawnSpec(
          id = "car_town_sedan",
          displayName = "Metro Azure Turbo",
          x = -151.0f,
          z = -46.0f,
          yawRad = 0f,
          bodyColor = floatArrayOf(0.22f, 0.64f, 0.94f),
          trimColor = floatArrayOf(0.12f, 0.13f, 0.16f),
          maxSpeed = 36f,
          accel = 18f,
          isAiTraffic = false,
        ),
        VehicleSpawnSpec(
          id = "ai_car_town_1",
          displayName = "Town Taxi Yellow",
          x = -148f,
          z = 16.0f,
          yawRad = (-PI / 2.0).toFloat(),
          bodyColor = floatArrayOf(0.96f, 0.80f, 0.18f),
          trimColor = floatArrayOf(0.14f, 0.14f, 0.16f),
          maxSpeed = 30f,
          accel = 15f,
          isAiTraffic = true,
          loopRouteIndex = 0,
          driverShirtColor = CartoonPresets.CHUBBY_SAFFRON.tunicColor,
          appearance = CartoonPresets.CHUBBY_SAFFRON,
        ),
        VehicleSpawnSpec(
          id = "ai_car_town_2",
          displayName = "Bazaar Crimson Coupe",
          x = -194f,
          z = -6.0f,
          yawRad = 0f,
          bodyColor = floatArrayOf(0.86f, 0.22f, 0.28f),
          trimColor = floatArrayOf(0.12f, 0.12f, 0.16f),
          maxSpeed = 31f,
          accel = 15f,
          isAiTraffic = true,
          loopRouteIndex = 2,
          driverShirtColor = CartoonPresets.LEAN_BUDDY_BLUE.tunicColor,
          appearance = CartoonPresets.LEAN_BUDDY_BLUE,
        ),
      )

    // West Shoreline Cruise Boat
    boatSpawns +=
      BoatSpawnSpec(
        id = "ai_boat_town",
        displayName = "West Harbor Cutter",
        x = -242f,
        z = 0f,
        yawRad = 0f,
        hullColor = floatArrayOf(0.95f, 0.94f, 0.90f),
        accentColor = floatArrayOf(0.96f, 0.44f, 0.20f),
        maxSpeed = 16f,
        isAiCruise = true,
        routeCenterX = -148f,
        routeRadiusX = 96f,
        routeRadiusZ = 96f,
        routePhase = 3.1f,
        captainShirtColor = CartoonPresets.COASTAL_CAPTAIN.tunicColor,
        appearance = CartoonPresets.COASTAL_CAPTAIN,
      )

    // Rooftop Town Street Citizens
    pedestrianSpawns +=
      listOf(
        PedestrianSpawnSpec(
          id = "ped_town_west_walk",
          waypoints = listOf(Pair(-154.5f, -34f), Pair(-154.5f, 12f)),
          shirtColor = CartoonPresets.CHUBBY_EMERALD.tunicColor,
          pantsColor = CartoonPresets.CHUBBY_EMERALD.pantsColor,
          speed = 1.95f,
          appearance = CartoonPresets.CHUBBY_EMERALD,
        ),
        PedestrianSpawnSpec(
          id = "ped_town_east_walk",
          waypoints = listOf(Pair(-141.5f, 10f), Pair(-141.5f, -34f)),
          shirtColor = CartoonPresets.LEAN_BUDDY_GOLD.tunicColor,
          pantsColor = CartoonPresets.LEAN_BUDDY_GOLD.pantsColor,
          speed = 2.05f,
          appearance = CartoonPresets.LEAN_BUDDY_GOLD,
        ),
        PedestrianSpawnSpec(
          id = "ped_town_west_bridge",
          waypoints = listOf(Pair(-100f, 12.2f), Pair(-48f, 12.2f)),
          shirtColor = CartoonPresets.INSPECTOR_KHAKI.tunicColor,
          pantsColor = CartoonPresets.INSPECTOR_KHAKI.pantsColor,
          speed = 2.0f,
          appearance = CartoonPresets.INSPECTOR_KHAKI,
          isBraveDefender = true,
        ),
        PedestrianSpawnSpec(
          id = "ped_town_boardwalk",
          waypoints = listOf(Pair(-180f, 52.2f), Pair(-116f, 52.2f)),
          shirtColor = CartoonPresets.CHUBBY_CORAL.tunicColor,
          pantsColor = CartoonPresets.CHUBBY_CORAL.pantsColor,
          speed = 1.85f,
          appearance = CartoonPresets.CHUBBY_CORAL,
        ),
      )
  }

  /**
   * Builds a symmetrical dual-lane channel bridge centered at (xCenter, zCenter) spanning 54m along X.
   */
  fun buildChannelBridgeSegment(
    xCenter: Float,
    zCenter: Float,
    accentNeon: FloatArray,
    staticBoxes: MutableList<PropBoxSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
  ) {
    staticBoxes +=
      PropBoxSpec(
        cx = xCenter,
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
        minX = xCenter - 27f,
        minZ = zCenter - 5.0f,
        maxX = xCenter + 27f,
        maxZ = zCenter + 5.0f,
        baseElevationY = 0.12f,
        tag = "Channel Bridge",
      )

    val stripeColor = floatArrayOf(0.95f, 0.78f, 0.25f)
    val startX = (xCenter - 24f).toInt()
    val endX = (xCenter + 24f).toInt()
    for (x in startX..endX step 6) {
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

    for (sideSign in listOf(-1f, 1f)) {
      val railZ = zCenter + sideSign * 4.85f
      staticBoxes +=
        PropBoxSpec(
          cx = xCenter,
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
          cx = xCenter,
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
          minX = xCenter - 11.5f,
          minZ = railZ - 0.32f,
          maxX = xCenter + 11.5f,
          maxZ = railZ + 0.32f,
          minY = 0f,
          maxY = 2.0f,
          tag = "Bridge Rail",
        )
    }

    for (px in listOf(xCenter - 8f, xCenter + 8f)) {
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

  /**
   * Builds a multi-floor, full-interior + walkable-rooftop Town Street building facing East (+1) or West (-1):
   * - Compound yard wall with wide street gate & wooden crates
   * - Striped front porch awning + 1F open interior with sofa & table
   * - Iconic wide Exterior Front Concrete Staircase up to a 2F Front Terrace Balcony
   * - 2F open interior room + Interior Staircase up to a Walkable Concrete Rooftop Deck
   * - Rooftop perimeter parapet rails, supply crates & iconic Blue Cylindrical Water Tank on legs
   */
  fun buildRooftopTownHouse(
    h: HouseSpec,
    staticBoxes: MutableList<PropBoxSpec>,
    staticCylinders: MutableList<PropCylinderSpec>,
    glassBoxes: MutableList<PropBoxSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
  ) {
    val cx = h.centerX
    val cz = h.centerZ
    val hw = h.width * 0.5f // 6.5f along X
    val hd = h.depth * 0.5f // 6.0f along Z
    val dirX = if (h.facingX >= 0) 1f else -1f
    val wallT = 0.40f
    val wh = h.wallHeight // 3.4f
    val floor1Y = 0.14f
    val floor2Y = floor1Y + wh // 3.54f
    val roofY = floor2Y + wh   // 6.94f
    val totalWallH = wh * 2f

    val frontX = cx + dirX * (hw - wallT * 0.5f)
    val backX = cx - dirX * (hw - wallT * 0.5f)
    val balcDepthX = 3.2f
    val balcCenterX = frontX + dirX * (balcDepthX * 0.5f + 0.10f)
    val balcOuterX = frontX + dirX * balcDepthX
    val balcMinX = min(frontX, balcOuterX)
    val balcMaxX = max(frontX, balcOuterX)

    val concreteTone = floatArrayOf(0.76f, 0.74f, 0.69f)
    val checkTile = floatArrayOf(0.84f, 0.72f, 0.42f)
    val woodCrate = floatArrayOf(0.60f, 0.42f, 0.24f)
    val ironRail = floatArrayOf(0.24f, 0.26f, 0.30f)

    // 1. 1F Ground Slab + Checkered Porch Veranda Floor
    staticBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = floor1Y * 0.5f,
        cz = cz,
        sx = h.width + 0.8f,
        sy = floor1Y,
        sz = h.depth + 0.8f,
        color = concreteTone,
        surface = MaterialSurface.TILE,
        castShadow = false,
      )
    staticBoxes +=
      PropBoxSpec(
        cx = balcCenterX,
        cy = 0.07f,
        cz = cz,
        sx = balcDepthX,
        sy = 0.14f,
        sz = h.depth + 0.4f,
        color = checkTile,
        surface = MaterialSurface.TILE,
        castShadow = false,
      )
    walkableFloors +=
      WalkableFloorSpec(
        minX = min(backX - 0.5f, balcOuterX),
        minZ = cz - hd - 0.4f,
        maxX = max(backX - 0.5f, balcOuterX).coerceAtLeast(max(cx + hw, balcMaxX)),
        maxZ = cz + hd + 0.4f,
        baseElevationY = floor1Y,
        tag = "${h.name} 1F",
      )

    // 2. Front Courtyard Compound Wall + Open Wide Gate (gate leaves doorway + stair foot unobstructed)
    val yardWallX = balcOuterX + dirX * 5.8f
    val yardMinX = min(balcOuterX, yardWallX)
    val yardMaxX = max(balcOuterX, yardWallX)
    // Courtyard side walls (North & South edges)
    for (szSign in listOf(-1f, 1f)) {
      val wz = cz + szSign * (hd + 0.2f)
      staticBoxes += PropBoxSpec((balcOuterX + yardWallX) * 0.5f, 0.58f, wz, 5.8f, 1.16f, 0.36f, concreteTone, MaterialSurface.STUCCO)
      aabbs += AabbCollider(yardMinX, wz - 0.22f, yardMaxX, wz + 0.22f, minY = 0f, maxY = 1.3f, tag = "${h.name} Yard Wall")
    }
    // Front street-facing yard wall pieces leaving central 7.6m wide gate opening [cz - 2.6 .. cz + 5.2]
    staticBoxes += PropBoxSpec(yardWallX, 0.58f, cz - 4.4f, 0.36f, 1.16f, 3.6f, concreteTone, MaterialSurface.STUCCO)
    aabbs += AabbCollider(yardWallX - 0.22f, cz - 6.2f, yardWallX + 0.22f, cz - 2.6f, minY = 0f, maxY = 1.3f, tag = "${h.name} Gate")
    staticBoxes += PropBoxSpec(yardWallX, 0.58f, cz + 5.75f, 0.36f, 1.16f, 1.1f, concreteTone, MaterialSurface.STUCCO)
    aabbs += AabbCollider(yardWallX - 0.22f, cz + 5.2f, yardWallX + 0.22f, cz + 6.3f, minY = 0f, maxY = 1.3f, tag = "${h.name} Gate")

    // Striped porch awning above 1F veranda doorway
    val awningCz = cz - 1.5f
    for (stripeIdx in -2..2) {
      val stripeColor =
        if (stripeIdx % 2 == 0) floatArrayOf(0.16f, 0.44f, 0.88f) else floatArrayOf(0.96f, 0.96f, 0.98f)
      staticBoxes +=
        PropBoxSpec(
          cx = frontX + dirX * 1.45f,
          cy = floor1Y + 2.45f,
          cz = awningCz + stripeIdx * 0.64f,
          sx = 2.6f,
          sy = 0.14f,
          sz = 0.62f,
          color = stripeColor,
          surface = MaterialSurface.CLOTH,
        )
    }

    // 3. Back Wall & Side Walls (full 2 stories with glazed windows on 1F & 2F)
    staticBoxes +=
      PropBoxSpec(
        cx = backX,
        cy = floor1Y + totalWallH * 0.5f,
        cz = cz,
        sx = wallT,
        sy = totalWallH,
        sz = h.depth,
        color = h.primaryColor,
        surface = MaterialSurface.STUCCO,
      )
    aabbs += AabbCollider(backX - 0.28f, cz - hd, backX + 0.28f, cz + hd, minY = 0f, maxY = roofY, tag = h.name)

    for (zSide in listOf(-1f, 1f)) {
      val wz = cz + zSide * (hd - wallT * 0.5f)
      for (floorBase in listOf(floor1Y, floor2Y)) {
        val tone = if (floorBase == floor1Y) h.primaryColor else h.accentColor
        staticBoxes += PropBoxSpec(cx, floorBase + 0.52f, wz, h.width, 1.04f, wallT, tone, MaterialSurface.STUCCO)
        staticBoxes += PropBoxSpec(cx, floorBase + wh - 0.45f, wz, h.width, 0.90f, wallT, tone, MaterialSurface.STUCCO)
        glassBoxes +=
          PropBoxSpec(
            cx = cx,
            cy = floorBase + wh * 0.52f,
            cz = wz,
            sx = h.width - 4.4f,
            sy = wh - 1.94f,
            sz = 0.08f,
            color = floatArrayOf(0.54f, 0.84f, 0.95f),
            surface = MaterialSurface.GLASS,
            roughness = 0.08f,
            castShadow = false,
          )
      }
      for (cornerX in listOf(-1f, 1f)) {
        staticBoxes +=
          PropBoxSpec(
            cx = cx + cornerX * (hw - 1.1f),
            cy = floor1Y + totalWallH * 0.5f,
            cz = wz,
            sx = 2.2f,
            sy = totalWallH,
            sz = wallT,
            color = h.primaryColor,
            surface = MaterialSurface.STUCCO,
          )
      }
      aabbs += AabbCollider(cx - hw, wz - 0.28f, cx + hw, wz + 0.28f, minY = 0f, maxY = roofY, tag = h.name)
    }

    // 4. Front Street-Facing Wall with Wide Open Doorways on BOTH 1F and 2F (z = cz - 3.0 .. cz + 0.2)
    val doorCz = cz - 1.4f
    val doorHalfZ = 1.60f
    val southWallStart = doorCz + doorHalfZ // cz + 0.2
    val southWallEnd = cz + hd              // cz + 6.0
    val southSegLen = southWallEnd - southWallStart
    val northWallStart = cz - hd            // cz - 6.0
    val northWallEnd = doorCz - doorHalfZ   // cz - 3.0
    val northSegLen = northWallEnd - northWallStart

    staticBoxes +=
      PropBoxSpec(
        cx = frontX,
        cy = floor1Y + wh * 0.5f,
        cz = (northWallStart + northWallEnd) * 0.5f,
        sx = wallT,
        sy = wh,
        sz = northSegLen,
        color = h.primaryColor,
        surface = MaterialSurface.STUCCO,
      )
    staticBoxes +=
      PropBoxSpec(
        cx = frontX,
        cy = floor2Y + wh * 0.5f,
        cz = (northWallStart + northWallEnd) * 0.5f,
        sx = wallT,
        sy = wh,
        sz = northSegLen,
        color = h.accentColor,
        surface = MaterialSurface.STUCCO,
      )
    aabbs += AabbCollider(frontX - 0.28f, northWallStart, frontX + 0.28f, northWallEnd, minY = 0f, maxY = roofY, tag = h.name)

    staticBoxes +=
      PropBoxSpec(
        cx = frontX,
        cy = floor1Y + wh * 0.5f,
        cz = (southWallStart + southWallEnd) * 0.5f,
        sx = wallT,
        sy = wh,
        sz = southSegLen,
        color = h.primaryColor,
        surface = MaterialSurface.STUCCO,
      )
    staticBoxes +=
      PropBoxSpec(
        cx = frontX,
        cy = floor2Y + wh * 0.5f,
        cz = (southWallStart + southWallEnd) * 0.5f,
        sx = wallT,
        sy = wh,
        sz = southSegLen,
        color = h.accentColor,
        surface = MaterialSurface.STUCCO,
      )
    aabbs += AabbCollider(frontX - 0.28f, southWallStart, frontX + 0.28f, southWallEnd, minY = 0f, maxY = roofY, tag = h.name)

    // Doorways Headers (1F & 2F)
    for (floorBase in listOf(floor1Y, floor2Y)) {
      staticBoxes +=
        PropBoxSpec(
          cx = frontX,
          cy = floorBase + wh - 0.45f,
          cz = doorCz,
          sx = wallT,
          sy = 0.90f,
          sz = doorHalfZ * 2f,
          color = h.accentColor,
          surface = MaterialSurface.STUCCO,
        )
    }

    // 5. WIDE EXTERIOR FRONT CONCRETE STAIRCASE (yard foot -> 2F Front Terrace Balcony!)
    val extStairZMin = cz + 2.15f
    val extStairZMax = cz + 5.05f
    val extStairCz = (extStairZMin + extStairZMax) * 0.5f
    val extStairW = extStairZMax - extStairZMin
    val extFootX = balcOuterX + dirX * 4.8f
    val extTopX = balcOuterX
    val extMinX = min(extFootX, extTopX)
    val extMaxX = max(extFootX, extTopX)

    walkableFloors +=
      WalkableFloorSpec(
        minX = extMinX - 0.2f,
        minZ = extStairZMin,
        maxX = extMaxX + 0.2f,
        maxZ = extStairZMax,
        baseElevationY = floor1Y,
        endElevationY = floor2Y,
        rampAxis = if (dirX > 0f) RampAxis.NEG_X else RampAxis.POS_X,
        tag = "${h.name} Exterior Stairs",
      )

    val extSteps = 12
    val extStepRise = (floor2Y - floor1Y) / extSteps
    val extStepRunX = (extTopX - extFootX) / extSteps
    for (s in 0 until extSteps) {
      val sy = floor1Y + extStepRise * (s + 0.5f)
      val sxPos = extFootX + extStepRunX * (s + 0.5f)
      staticBoxes +=
        PropBoxSpec(
          cx = sxPos,
          cy = floor1Y + (sy - floor1Y) * 0.5f,
          cz = extStairCz,
          sx = kotlin.math.abs(extStepRunX) + 0.10f,
          sy = (sy - floor1Y) + extStepRise * 0.5f,
          sz = extStairW - 0.14f,
          color = concreteTone,
          surface = MaterialSurface.STUCCO,
        )
    }

    // 6. 2F Balcony Terrace Overhanging Front Porch (walkable + perimeter rails leaving stair top open!)
    staticBoxes +=
      PropBoxSpec(
        cx = balcCenterX,
        cy = floor2Y - 0.12f,
        cz = cz,
        sx = balcDepthX,
        sy = 0.24f,
        sz = h.depth,
        color = concreteTone,
        surface = MaterialSurface.TILE,
      )
    walkableFloors +=
      WalkableFloorSpec(
        minX = balcMinX - 0.15f,
        minZ = cz - hd + 0.2f,
        maxX = balcMaxX + 0.15f,
        maxZ = cz + hd - 0.2f,
        baseElevationY = floor2Y,
        tag = "${h.name} 2F Balcony",
      )
    // Porch pillars supporting 2F balcony corners
    val pillarX = balcOuterX - dirX * 0.24f
    for (pz in listOf(cz - hd + 0.55f, cz + 0.8f, cz + hd - 0.55f)) {
      staticBoxes += PropBoxSpec(pillarX, floor1Y + wh * 0.5f, pz, 0.36f, wh, 0.36f, concreteTone, MaterialSurface.STUCCO)
      aabbs += AabbCollider(pillarX - 0.24f, pz - 0.24f, pillarX + 0.24f, pz + 0.24f, minY = 0f, maxY = floor2Y, tag = h.name)
    }
    // 2F Balcony railings: Outer North rail [cz - hd .. extStairZMin], plus North & South side rails
    val northRailLen = extStairZMin - (cz - hd)
    val northRailCz = (cz - hd + extStairZMin) * 0.5f
    staticBoxes += PropBoxSpec(balcOuterX, floor2Y + 0.45f, northRailCz, 0.16f, 0.90f, northRailLen, ironRail, MaterialSurface.SLATE)
    aabbs += AabbCollider(balcOuterX - 0.18f, cz - hd, balcOuterX + 0.18f, extStairZMin, minY = floor2Y, maxY = floor2Y + 1.15f, tag = "${h.name} Balcony Rail")

    val southRailLen = (cz + hd) - extStairZMax
    if (southRailLen > 0.2f) {
      val southRailCz = (extStairZMax + cz + hd) * 0.5f
      staticBoxes += PropBoxSpec(balcOuterX, floor2Y + 0.45f, southRailCz, 0.16f, 0.90f, southRailLen, ironRail, MaterialSurface.SLATE)
      aabbs += AabbCollider(balcOuterX - 0.18f, extStairZMax, balcOuterX + 0.18f, cz + hd, minY = floor2Y, maxY = floor2Y + 1.15f, tag = "${h.name} Balcony Rail")
    }
    for (zRail in listOf(cz - hd + 0.14f, cz + hd - 0.14f)) {
      staticBoxes += PropBoxSpec(balcCenterX, floor2Y + 0.45f, zRail, balcDepthX, 0.90f, 0.16f, ironRail, MaterialSurface.SLATE)
      aabbs += AabbCollider(balcMinX, zRail - 0.18f, balcMaxX, zRail + 0.18f, minY = floor2Y, maxY = floor2Y + 1.15f, tag = "${h.name} Balcony Side")
    }

    // 7. 2F Interior Floor Slab (solid across entire 2F interior, since 1F->2F stairs are outside!)
    staticBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = floor2Y - 0.12f,
        cz = cz,
        sx = h.width - wallT * 0.5f,
        sy = 0.24f,
        sz = h.depth - wallT * 0.5f,
        color = floatArrayOf(0.84f, 0.80f, 0.72f),
        surface = MaterialSurface.WOOD,
      )
    walkableFloors +=
      WalkableFloorSpec(
        minX = cx - hw + 0.2f,
        minZ = cz - hd + 0.2f,
        maxX = cx + hw - 0.2f,
        maxZ = cz + hd - 0.2f,
        baseElevationY = floor2Y,
        tag = "${h.name} 2F",
      )

    // 8. Interior 2F -> ROOFTOP Staircase + Rooftop Cutout & Walkable Concrete Roof Deck!
    val roofStairZMin = cz + 2.2f
    val roofStairZMax = cz + 4.9f
    val roofStairCz = (roofStairZMin + roofStairZMax) * 0.5f
    val roofStairW = roofStairZMax - roofStairZMin
    val intFootX = cx + dirX * 2.5f
    val intTopX = cx - dirX * 2.5f
    val intMinX = min(intFootX, intTopX)
    val intMaxX = max(intFootX, intTopX)

    walkableFloors +=
      WalkableFloorSpec(
        minX = intMinX - 0.1f,
        minZ = roofStairZMin,
        maxX = intMaxX + 0.1f,
        maxZ = roofStairZMax,
        baseElevationY = floor2Y,
        endElevationY = roofY,
        rampAxis = if (dirX > 0f) RampAxis.NEG_X else RampAxis.POS_X,
        tag = "${h.name} Roof Stairs",
      )

    val roofSteps = 11
    val roofStepRise = (roofY - floor2Y) / roofSteps
    val roofStepRunX = (intTopX - intFootX) / roofSteps
    for (s in 0 until roofSteps) {
      val sy = floor2Y + roofStepRise * (s + 0.5f)
      val sxPos = intFootX + roofStepRunX * (s + 0.5f)
      staticBoxes +=
        PropBoxSpec(
          cx = sxPos,
          cy = floor2Y + (sy - floor2Y) * 0.5f,
          cz = roofStairCz,
          sx = kotlin.math.abs(roofStepRunX) + 0.08f,
          sy = (sy - floor2Y) + roofStepRise * 0.5f,
          sz = roofStairW - 0.12f,
          color = concreteTone,
          surface = MaterialSurface.STUCCO,
        )
    }

    // Roof Slabs split around interior stairwell cutout:
    // (A) Main North/Mid Rooftop Deck [cz - hd .. roofStairZMin] across full building width
    val mainRoofDepthZ = roofStairZMin - (cz - hd)
    val mainRoofCz = (cz - hd + roofStairZMin) * 0.5f
    staticBoxes +=
      PropBoxSpec(
        cx = cx,
        cy = roofY - 0.12f,
        cz = mainRoofCz,
        sx = h.width + 0.6f,
        sy = 0.24f,
        sz = mainRoofDepthZ,
        color = h.roofColor,
        surface = MaterialSurface.STUCCO,
      )
    walkableFloors +=
      WalkableFloorSpec(
        minX = cx - hw - 0.2f,
        minZ = cz - hd - 0.1f,
        maxX = cx + hw + 0.2f,
        maxZ = roofStairZMin + 0.15f,
        baseElevationY = roofY,
        tag = "${h.name} Rooftop",
      )

    // (B) Rear Roof Landing Strip behind top of interior stairs [backX .. intTopX, roofStairZMin .. cz + hd]
    val rearRoofMinX = min(cx - dirX * (hw + 0.2f), intTopX)
    val rearRoofMaxX = max(cx - dirX * (hw + 0.2f), intTopX)
    val rearRoofZSpan = (cz + hd) - roofStairZMin
    staticBoxes +=
      PropBoxSpec(
        cx = (rearRoofMinX + rearRoofMaxX) * 0.5f,
        cy = roofY - 0.12f,
        cz = (roofStairZMin + cz + hd) * 0.5f,
        sx = rearRoofMaxX - rearRoofMinX,
        sy = 0.24f,
        sz = rearRoofZSpan,
        color = h.roofColor,
        surface = MaterialSurface.STUCCO,
      )
    walkableFloors +=
      WalkableFloorSpec(
        minX = rearRoofMinX,
        minZ = roofStairZMin - 0.15f,
        maxX = rearRoofMaxX,
        maxZ = cz + hd,
        baseElevationY = roofY,
        tag = "${h.name} Roof Landing",
      )

    // Stairwell Safety Guardrail on Rooftop along z = roofStairZMin so player doesn't accidentally drop into shaft
    staticBoxes +=
      PropBoxSpec(
        cx = (intMinX + intMaxX) * 0.5f,
        cy = roofY + 0.50f,
        cz = roofStairZMin - 0.08f,
        sx = intMaxX - intMinX + 0.2f,
        sy = 1.0f,
        sz = 0.16f,
        color = concreteTone,
        surface = MaterialSurface.STUCCO,
      )
    aabbs +=
      AabbCollider(
        minX = intMinX - 0.15f,
        minZ = roofStairZMin - 0.24f,
        maxX = intMaxX + 0.15f,
        maxZ = roofStairZMin + 0.10f,
        minY = roofY,
        maxY = roofY + 1.15f,
        tag = "${h.name} Roof Stair Rail",
      )

    // Rooftop Perimeter Parapets & Pipe Railings (height 0.95m, roof collision band minY = roofY..roofY + 1.2)
    for (pxSide in listOf(frontX, backX)) {
      staticBoxes += PropBoxSpec(pxSide, roofY + 0.42f, cz, 0.28f, 0.84f, h.depth, concreteTone, MaterialSurface.STUCCO)
      aabbs += AabbCollider(pxSide - 0.22f, cz - hd, pxSide + 0.22f, cz + hd, minY = roofY, maxY = roofY + 1.2f, tag = "${h.name} Roof Parapet")
    }
    for (pzSide in listOf(cz - hd + 0.14f, cz + hd - 0.14f)) {
      staticBoxes += PropBoxSpec(cx, roofY + 0.42f, pzSide, h.width, 0.84f, 0.28f, concreteTone, MaterialSurface.STUCCO)
      aabbs += AabbCollider(cx - hw, pzSide - 0.22f, cx + hw, pzSide + 0.22f, minY = roofY, maxY = roofY + 1.2f, tag = "${h.name} Roof Parapet")
    }

    // 9. Signature Blue Cylindrical Rooftop Water Tank on Black Frame Legs + Wooden Crates
    val tankX = cx + dirX * 2.4f
    val tankZ = cz - 3.6f
    for (lx in listOf(-0.42f, 0.42f)) {
      for (lz in listOf(-0.42f, 0.42f)) {
        staticBoxes += PropBoxSpec(tankX + lx, roofY + 0.35f, tankZ + lz, 0.10f, 0.70f, 0.10f, ironRail, MaterialSurface.SLATE)
      }
    }
    staticBoxes += PropBoxSpec(tankX, roofY + 0.68f, tankZ, 1.05f, 0.10f, 1.05f, ironRail, MaterialSurface.SLATE)
    staticCylinders +=
      PropCylinderSpec(
        cx = tankX,
        cy = roofY + 1.32f,
        cz = tankZ,
        radius = 0.72f,
        height = 1.22f,
        color = floatArrayOf(0.10f, 0.34f, 0.86f), // Vivid royal blue rooftop water tank!
        surface = MaterialSurface.STUCCO,
        metallic = 0.22f,
        roughness = 0.35f,
      )
    aabbs += AabbCollider(tankX - 0.75f, tankZ - 0.75f, tankX + 0.75f, tankZ + 0.75f, minY = roofY, maxY = roofY + 2.0f, tag = "${h.name} Water Tank")

    // Wooden supply crates on Rooftop & Front Yard (climbable parkour blocks!)
    val roofCrateX = cx - dirX * 2.2f
    val roofCrateZ = cz - 1.5f
    staticBoxes += PropBoxSpec(roofCrateX, roofY + 0.38f, roofCrateZ, 0.85f, 0.76f, 0.85f, woodCrate, MaterialSurface.WOOD)
    aabbs += AabbCollider(roofCrateX - 0.44f, roofCrateZ - 0.44f, roofCrateX + 0.44f, roofCrateZ + 0.44f, minY = roofY, maxY = roofY + 0.76f, tag = "${h.name} Roof Crate")
    walkableFloors += WalkableFloorSpec(roofCrateX - 0.44f, roofCrateZ - 0.44f, roofCrateX + 0.44f, roofCrateZ + 0.44f, baseElevationY = roofY + 0.76f, tag = "${h.name} Roof Crate")

    val yardCrateX = frontX + dirX * 1.4f
    val yardCrateZ = cz + 0.6f
    staticBoxes += PropBoxSpec(yardCrateX, floor1Y + 0.38f, yardCrateZ, 0.82f, 0.76f, 0.82f, woodCrate, MaterialSurface.WOOD)
    aabbs += AabbCollider(yardCrateX - 0.42f, yardCrateZ - 0.42f, yardCrateX + 0.42f, yardCrateZ + 0.42f, minY = floor1Y, maxY = floor1Y + 0.76f, tag = "${h.name} Yard Crate")
    walkableFloors += WalkableFloorSpec(yardCrateX - 0.42f, yardCrateZ - 0.42f, yardCrateX + 0.42f, yardCrateZ + 0.42f, baseElevationY = floor1Y + 0.76f, tag = "${h.name} Yard Crate")

    // 10. Interior Furniture & Warm Lighting on 1F and 2F
    staticBoxes += PropBoxSpec(cx - dirX * 2.2f, floor1Y + 0.32f, cz - 2.2f, 1.4f, 0.62f, 2.8f, floatArrayOf(0.56f, 0.35f, 0.22f), MaterialSurface.WOOD)
    aabbs += AabbCollider(cx - dirX * 2.2f - 0.75f, cz - 3.6f, cx - dirX * 2.2f + 0.75f, cz - 0.8f, minY = floor1Y, maxY = floor1Y + 1.0f, tag = "${h.name} Table")

    staticBoxes += PropBoxSpec(cx - dirX * 2.4f, floor2Y + 0.30f, cz - 2.0f, 1.2f, 0.56f, 2.6f, floatArrayOf(0.90f, 0.86f, 0.80f), MaterialSurface.STUCCO)
    aabbs += AabbCollider(cx - dirX * 2.4f - 0.65f, cz - 3.3f, cx - dirX * 2.4f + 0.65f, cz - 0.7f, minY = floor2Y, maxY = floor2Y + 1.0f, tag = "${h.name} 2F Sofa")

    for (lampY in listOf(floor2Y - 0.26f, roofY - 0.26f)) {
      staticBoxes +=
        PropBoxSpec(
          cx = cx,
          cy = lampY,
          cz = cz - 1.2f,
          sx = 1.4f,
          sy = 0.10f,
          sz = 1.4f,
          color = floatArrayOf(1.0f, 0.90f, 0.70f),
          surface = MaterialSurface.EMISSIVE,
          emissive = 0.92f,
          castShadow = false,
        )
    }
  }
}
