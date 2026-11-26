package com.example.game.world

import com.example.game.math.AabbCollider
import com.example.game.math.CircleCollider
import com.example.game.state.MarketStallKind
import com.example.game.state.MarketStallSpec
import com.example.game.state.WaterfallSpec

data class ExpandedWorldAdditions(
  val waterfalls: List<WaterfallSpec>,
  val marketStalls: List<MarketStallSpec>,
  val railwayLoopWaypoints: List<Pair<Float, Float>>,
  val marketBotRoutes: List<List<Pair<Float, Float>>>,
)

object ExpandedWorldBuilder {
  fun buildAll(
    staticBoxes: MutableList<PropBoxSpec>,
    staticCylinders: MutableList<PropCylinderSpec>,
    glassBoxes: MutableList<PropBoxSpec>,
    aabbs: MutableList<AabbCollider>,
    circles: MutableList<CircleCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
    trees: MutableList<TreeSpec>,
    streetLights: MutableList<StreetLightSpec>,
  ): ExpandedWorldAdditions {
    val asphaltColor = floatArrayOf(0.20f, 0.21f, 0.25f)
    val creamStone = floatArrayOf(0.88f, 0.84f, 0.78f)
    val warmSand = floatArrayOf(0.88f, 0.77f, 0.56f)
    val cyanNeon = floatArrayOf(0.18f, 0.94f, 0.96f)
    val pinkNeon = floatArrayOf(0.98f, 0.28f, 0.58f)
    val goldNeon = floatArrayOf(1.0f, 0.80f, 0.26f)
    val mintNeon = floatArrayOf(0.22f, 0.92f, 0.62f)

    // ---------------------------------------------------------
    // 1. NORTH EXPANSION: SIERRA HIGHLANDS, MOUNTAINS, 2 WATERFALLS & HELIPAD
    // Terrain & sculpted Sierra alpine ridges rendered continuously by TerrainHeightfield
    // ---------------------------------------------------------

    // Extend Bayside North Avenue (x = 0, z = -64..-21) and Rooftop Town Ave north edge (z = -64..-56)
    staticBoxes +=
      PropBoxSpec(
        cx = 0f,
        cy = 0.03f,
        cz = -42f,
        sx = 9.5f,
        sy = 0.08f,
        sz = 44f,
        color = asphaltColor,
        surface = MaterialSurface.ASPHALT,
        castShadow = false,
      )
    staticBoxes +=
      PropBoxSpec(
        cx = 148f,
        cy = 0.03f,
        cz = -44f,
        sx = 9.5f,
        sy = 0.08f,
        sz = 40f,
        color = asphaltColor,
        surface = MaterialSurface.ASPHALT,
        castShadow = false,
      )
    staticBoxes +=
      PropBoxSpec(
        cx = -148f,
        cy = 0.03f,
        cz = -60f,
        sx = 9.5f,
        sy = 0.08f,
        sz = 10f,
        color = asphaltColor,
        surface = MaterialSurface.ASPHALT,
        castShadow = false,
      )

    // 3 North Parkway Bridges connecting Bayside (0), Green Valley (148), & Rooftop Town (-148)
    for ((bx, neon) in listOf(0f to cyanNeon, 148f to mintNeon, -148f to goldNeon)) {
      buildNorthSouthBridge(bx, -84f, -58f, neon, staticBoxes, aabbs, walkableFloors)
    }

    // Highland Drive East-West Scenic Boulevard (z = -96, x = -266..266)
    staticBoxes +=
      PropBoxSpec(
        cx = 0f,
        cy = 0.04f,
        cz = -96f,
        sx = 532f,
        sy = 0.10f,
        sz = 10f,
        color = asphaltColor,
        surface = MaterialSurface.ASPHALT,
        roughness = 0.78f,
        castShadow = false,
      )
    // Connectors from North Parkway bridges to Highland Drive
    for (bx in listOf(-148f, 0f, 148f)) {
      staticBoxes +=
        PropBoxSpec(
          cx = bx,
          cy = 0.04f,
          cz = -87f,
          sx = 9.8f,
          sy = 0.10f,
          sz = 18f,
          color = asphaltColor,
          surface = MaterialSurface.ASPHALT,
          castShadow = false,
        )
    }
    for (lx in listOf(-210f, -154f, -90f, -32f, 32f, 90f, 154f, 210f)) {
      streetLights += StreetLightSpec(lx, -102f, 0f, if (lx < 0f) goldNeon else cyanNeon)
    }

    // 4 Sculpted Sierra Mountain Peak colliders matching continuous TerrainHeightfield peaks
    for ((mx, mz, rad, peakH) in
      listOf(
        floatArrayOf(-22f, -168f, 34f, 41f),
        floatArrayOf(-148f, -168f, 31f, 36f),
        floatArrayOf(162f, -168f, 33f, 39f),
        floatArrayOf(68f, -156f, 25f, 28f),
      )
    ) {
      aabbs += AabbCollider(mx - rad * 0.42f, mz - rad * 0.42f, mx + rad * 0.42f, mz + rad * 0.42f, minY = 0f, maxY = peakH, tag = "Mountain Peak")
    }

    // 2 Animated Waterfalls (Jharna) pouring down mountain cliffs into turquoise plunge lagoons
    val waterfalls =
      listOf(
        WaterfallSpec(
          id = "sierra_main_jharna",
          x = -14f,
          topY = 26f,
          baseY = 0.05f,
          z = -141f,
          width = 7.4f,
          poolRadius = 12.5f,
        ),
        WaterfallSpec(
          id = "east_emerald_jharna",
          x = 168f,
          topY = 24f,
          baseY = 0.05f,
          z = -145f,
          width = 6.2f,
          poolRadius = 10.5f,
        ),
      )
    for (wf in waterfalls) {
      // Rocky cliff backing behind waterfall
      staticBoxes +=
        PropBoxSpec(
          cx = wf.x,
          cy = wf.topY * 0.52f,
          cz = wf.z - 2.8f,
          sx = wf.width + 8.5f,
          sy = wf.topY * 1.05f,
          sz = 5.4f,
          color = floatArrayOf(0.36f, 0.35f, 0.34f),
          surface = MaterialSurface.SLATE,
          roughness = 0.90f,
        )
      aabbs +=
        AabbCollider(
          wf.x - (wf.width + 8.5f) * 0.5f,
          wf.z - 5.5f,
          wf.x + (wf.width + 8.5f) * 0.5f,
          wf.z - 0.1f,
          minY = 0f,
          maxY = wf.topY,
          tag = "Waterfall Cliff",
        )
      // Shimmering plunge pool water surface
      staticBoxes +=
        PropBoxSpec(
          cx = wf.x,
          cy = 0.03f,
          cz = wf.z + 8.5f,
          sx = wf.poolRadius * 2.0f,
          sy = 0.06f,
          sz = wf.poolRadius * 1.6f,
          color = floatArrayOf(0.10f, 0.72f, 0.84f),
          surface = MaterialSurface.WATER,
          castShadow = false,
        )
      // Rocky pool rim boulders
      for ((ox, oz, rad) in
        listOf(
          Triple(-wf.poolRadius, 6f, 2.2f),
          Triple(wf.poolRadius, 7f, 2.4f),
          Triple(-wf.poolRadius * 0.7f, 15f, 1.8f),
          Triple(wf.poolRadius * 0.7f, 15f, 1.9f),
        )
      ) {
        staticCylinders +=
          PropCylinderSpec(
            cx = wf.x + ox,
            cy = 0.8f,
            cz = wf.z + oz,
            radius = rad,
            height = 1.6f,
            color = floatArrayOf(0.42f, 0.41f, 0.39f),
            surface = MaterialSurface.SLATE,
          )
        circles += CircleCollider(wf.x + ox, wf.z + oz, rad * 0.95f, "Waterfall Boulder")
      }
    }
    // Wooden viewing deck beside Sierra Waterfall plunge pool
    staticBoxes +=
      PropBoxSpec(
        cx = -14f,
        cy = 0.14f,
        cz = -114f,
        sx = 12f,
        sy = 0.24f,
        sz = 4.6f,
        color = floatArrayOf(0.56f, 0.38f, 0.24f),
        surface = MaterialSurface.WOOD,
      )
    walkableFloors += WalkableFloorSpec(-20f, -116.3f, -8f, -111.7f, 0.26f, tag = "Waterfall Overlook Deck")

    // Sierra Mountain Helipad at (48, -106)
    staticBoxes +=
      PropBoxSpec(
        cx = 48f,
        cy = 0.08f,
        cz = -106f,
        sx = 14f,
        sy = 0.16f,
        sz = 14f,
        color = floatArrayOf(0.24f, 0.26f, 0.30f),
        surface = MaterialSurface.SLATE,
        castShadow = false,
      )
    // Glowing 'H' helipad bars
    staticBoxes += PropBoxSpec(45.5f, 0.17f, -106f, 0.9f, 0.04f, 7.2f, goldNeon, MaterialSurface.EMISSIVE, emissive = 0.9f, castShadow = false)
    staticBoxes += PropBoxSpec(50.5f, 0.17f, -106f, 0.9f, 0.04f, 7.2f, goldNeon, MaterialSurface.EMISSIVE, emissive = 0.9f, castShadow = false)
    staticBoxes += PropBoxSpec(48f, 0.17f, -106f, 4.4f, 0.04f, 0.9f, goldNeon, MaterialSurface.EMISSIVE, emissive = 0.9f, castShadow = false)
    walkableFloors += WalkableFloorSpec(41f, -113f, 55f, -99f, 0.16f, tag = "Sierra Helipad")

    // Alpine Pine / Shade trees in North Highlands
    for ((tx, tz) in
      listOf(
        -255f to -118f, -210f to -126f, -182f to -118f, -105f to -122f, -66f to -118f,
        24f to -122f, 95f to -118f, 128f to -126f, 212f to -122f, 255f to -118f,
      )
    ) {
      trees += TreeSpec(tx, tz, 9.2f, ((tx * 17f) % 360f), TreeKind.TROPICAL_SHADE)
      circles += CircleCollider(tx, tz, 0.55f, "Highland Tree")
    }

    // ---------------------------------------------------------
    // 2. EAST EXPANSION: VEGETABLE & CUSTOM MARKET DISTRICT (x = 222..308, z = -58..56)
    // Terrain surface rendered continuously by TerrainHeightfield
    // ---------------------------------------------------------
    // East Bazaar Connecting Bridges from Green Valley (x = 195..232 at z = 16 & z = -24)
    buildEastWestBridge(214f, 16f, 38f, goldNeon, staticBoxes, aabbs, walkableFloors)
    buildEastWestBridge(214f, -24f, 38f, pinkNeon, staticBoxes, aabbs, walkableFloors)

    // Perimeter Bazaar Roads & Warm Cobblestone Market Square (x = 236..298, z = -34..26)
    staticBoxes += PropBoxSpec(266f, 0.03f, 16f, 68f, 0.08f, 10f, asphaltColor, MaterialSurface.ASPHALT, castShadow = false)
    staticBoxes += PropBoxSpec(266f, 0.03f, -24f, 68f, 0.08f, 10f, asphaltColor, MaterialSurface.ASPHALT, castShadow = false)
    staticBoxes += PropBoxSpec(233f, 0.03f, -4f, 10f, 0.08f, 52f, asphaltColor, MaterialSurface.ASPHALT, castShadow = false)
    staticBoxes += PropBoxSpec(299f, 0.03f, -4f, 10f, 0.08f, 52f, asphaltColor, MaterialSurface.ASPHALT, castShadow = false)

    // Paved Bazaar Tile Plaza inside road loop
    staticBoxes +=
      PropBoxSpec(
        cx = 266f,
        cy = 0.04f,
        cz = -4f,
        sx = 56f,
        sy = 0.10f,
        sz = 32f,
        color = floatArrayOf(0.84f, 0.78f, 0.68f),
        surface = MaterialSurface.TILE,
        roughness = 0.68f,
        castShadow = false,
      )

    // Neon Market Archway Entrance at x = 238, z = -4
    for (az in listOf(-8.5f, 0.5f)) {
      staticBoxes += PropBoxSpec(239.5f, 3.2f, az, 0.9f, 6.4f, 0.9f, creamStone, MaterialSurface.STUCCO)
      aabbs += AabbCollider(239.0f, az - 0.5f, 240.0f, az + 0.5f, minY = 0f, maxY = 6.4f, tag = "Bazaar Gate Pillar")
    }
    staticBoxes += PropBoxSpec(239.5f, 6.2f, -4f, 1.1f, 1.0f, 10.6f, floatArrayOf(0.88f, 0.24f, 0.34f), MaterialSurface.STUCCO)
    staticBoxes += PropBoxSpec(238.9f, 6.2f, -4f, 0.18f, 0.55f, 9.2f, goldNeon, MaterialSurface.EMISSIVE, emissive = 1.25f)

    // Central Market Fountain & Canopy Pavilion (266, -4)
    staticCylinders += PropCylinderSpec(266f, 0.45f, -4f, 3.2f, 0.9f, creamStone, MaterialSurface.STUCCO)
    staticCylinders += PropCylinderSpec(266f, 1.4f, -4f, 0.65f, 2.2f, floatArrayOf(0.18f, 0.78f, 0.90f), MaterialSurface.EMISSIVE, emissive = 0.45f)
    circles += CircleCollider(266f, -4f, 3.2f, "Market Fountain")

    // 10 Detailed Vegetable, Fruit & Custom Bazaar Stalls
    val marketStalls =
      listOf(
        MarketStallSpec("stall_veg_1", "Fresh Tomato & Greens", MarketStallKind.VEGETABLES, 250f, -15f, 250f, -11.4f, 0f),
        MarketStallSpec("stall_veg_2", "Organic Carrot & Pumpkin", MarketStallKind.VEGETABLES, 258f, -15f, 258f, -11.4f, 0f),
        MarketStallSpec("stall_fruit_1", "Mango & Banana Mandi", MarketStallKind.FRUITS, 274f, -15f, 274f, -11.4f, 0f),
        MarketStallSpec("stall_fruit_2", "Melon & Citrus Cart", MarketStallKind.FRUITS, 282f, -15f, 282f, -11.4f, 0f),
        MarketStallSpec("stall_spice_1", "Royal Masala & Spices", MarketStallKind.SPICES, 250f, 7f, 250f, 3.4f, 180f),
        MarketStallSpec("stall_pottery_1", "Handcrafted Clay Pottery", MarketStallKind.POTTERY, 258f, 7f, 258f, 3.4f, 180f),
        MarketStallSpec("stall_cloth_1", "Custom Silk & Cloths", MarketStallKind.TEXTILES, 274f, 7f, 274f, 3.4f, 180f),
        MarketStallSpec("stall_bakery_1", "Hot Chai & Samosa Corner", MarketStallKind.BAKERY, 282f, 7f, 282f, 3.4f, 180f),
        MarketStallSpec("stall_veg_3", "Bazaar Leafy Sabzi", MarketStallKind.VEGETABLES, 289f, -8f, 285.4f, -8f, 90f),
        MarketStallSpec("stall_fruit_3", "Coconut & Berry Stand", MarketStallKind.FRUITS, 289f, 0f, 285.4f, 0f, 90f),
      )

    for (stall in marketStalls) {
      buildMarketStallGeometry(stall, staticBoxes, staticCylinders, aabbs)
    }

    for (lx in listOf(244f, 266f, 288f)) {
      streetLights += StreetLightSpec(lx, -19.5f, 0f, goldNeon)
      streetLights += StreetLightSpec(lx, 11.5f, 180f, cyanNeon)
    }
    for ((px, pz) in listOf(242f to 44f, 258f to 44f, 276f to 44f, 294f to 44f, 298f to -42f, 246f to -42f)) {
      trees += TreeSpec(px, pz, 8.0f, (px % 360f), TreeKind.PALM)
      circles += CircleCollider(px, pz, 0.5f, "Bazaar Palm")
    }

    // Decorative shopper routes removed — only player-like Battle Royale AI bots roam & trade fire across all districts
    val marketBotRoutes: List<List<Pair<Float, Float>>> = emptyList()

    // ---------------------------------------------------------
    // 3. WEST EXPANSION: SUNSET TIMBER CANYON (x = -308..-222, z = -62..56)
    // ---------------------------------------------------------
    buildEastWestBridge(-214f, 16f, 38f, goldNeon, staticBoxes, aabbs, walkableFloors)
    buildEastWestBridge(-214f, -24f, 38f, cyanNeon, staticBoxes, aabbs, walkableFloors)

    // Canyon Loop Road (x = -276..-233, z = -24..16)
    staticBoxes += PropBoxSpec(-254f, 0.03f, 16f, 44f, 0.08f, 10f, asphaltColor, MaterialSurface.ASPHALT, castShadow = false)
    staticBoxes += PropBoxSpec(-254f, 0.03f, -24f, 44f, 0.08f, 10f, asphaltColor, MaterialSurface.ASPHALT, castShadow = false)
    staticBoxes += PropBoxSpec(-233f, 0.03f, -4f, 10f, 0.08f, 52f, asphaltColor, MaterialSurface.ASPHALT, castShadow = false)
    staticBoxes += PropBoxSpec(-273f, 0.03f, -4f, 10f, 0.08f, 52f, asphaltColor, MaterialSurface.ASPHALT, castShadow = false)

    // West Canyon Terracotta Mesa colliders aligned to TerrainHeightfield mesas
    aabbs += AabbCollider(-306f, -32f, -280f, -4f, minY = 0f, maxY = 18f, tag = "West Canyon Mesa N")
    aabbs += AabbCollider(-304f, 12f, -280f, 36f, minY = 0f, maxY = 14f, tag = "West Canyon Mesa S")

    // Timber Canyon Cabin Overlook seated on local elevation
    val lodgeY = TerrainHeightfield.elevationAt(-253f, -4f)
    staticBoxes += PropBoxSpec(-253f, lodgeY + 1.8f, -4f, 10f, 3.6f, 8f, floatArrayOf(0.54f, 0.34f, 0.20f), MaterialSurface.WOOD)
    staticBoxes += PropBoxSpec(-253f, lodgeY + 4.1f, -4f, 11.4f, 1.0f, 9.4f, floatArrayOf(0.32f, 0.22f, 0.16f), MaterialSurface.WOOD)
    aabbs += AabbCollider(-258f, -8f, -248f, 0f, minY = lodgeY, maxY = lodgeY + 4.6f, tag = "Timber Lodge")
    for ((tx, tz) in listOf(-246f to 36f, -264f to 36f, -248f to -42f, -266f to -42f, -280f to 42f)) {
      trees += TreeSpec(tx, tz, 8.8f, (tx % 360f), TreeKind.PINE_CONIFER)
      circles += CircleCollider(tx, tz, 0.55f, "Canyon Pine")
    }

    // ---------------------------------------------------------
    // 4. SOUTH EXPANSION: CORAL ATOLL ISLAND & LIGHTHOUSE (x = -36..36, z = 146..210)
    // ---------------------------------------------------------
    // Southern Wooden Atoll Boat Pier (x = 0, z = 132..148)
    staticBoxes += PropBoxSpec(0f, 0.12f, 140f, 5.2f, 0.24f, 17f, floatArrayOf(0.54f, 0.36f, 0.22f), MaterialSurface.WOOD)
    staticBoxes += PropBoxSpec(0f, 0.12f, 132.5f, 12.0f, 0.24f, 4.4f, floatArrayOf(0.58f, 0.40f, 0.24f), MaterialSurface.WOOD)
    // Tall Coral Reef Lighthouse seated at summit of Atoll knoll (0, 178)
    val lightY = TerrainHeightfield.elevationAt(0f, 178f) - 0.15f
    staticCylinders += PropCylinderSpec(0f, lightY + 1.5f, 178f, 4.2f, 3.0f, creamStone, MaterialSurface.STUCCO)
    staticCylinders += PropCylinderSpec(0f, lightY + 11.0f, 178f, 2.8f, 16.0f, floatArrayOf(0.95f, 0.96f, 0.98f), MaterialSurface.STUCCO)
    staticCylinders += PropCylinderSpec(0f, lightY + 11.0f, 178f, 2.92f, 3.4f, floatArrayOf(0.92f, 0.24f, 0.26f), MaterialSurface.STUCCO)
    staticCylinders += PropCylinderSpec(0f, lightY + 20.6f, 178f, 2.3f, 2.8f, goldNeon, MaterialSurface.EMISSIVE, emissive = 1.6f)
    staticCylinders += PropCylinderSpec(0f, lightY + 23.0f, 178f, 2.8f, 1.8f, floatArrayOf(0.18f, 0.20f, 0.24f), MaterialSurface.SLATE)
    circles += CircleCollider(0f, 178f, 4.3f, "Coral Reef Lighthouse")

    for ((px, pz) in listOf(-18f to 164f, 18f to 164f, -20f to 192f, 20f to 192f)) {
      trees += TreeSpec(px, pz, 8.2f, (px * 13f) % 360f, TreeKind.PALM)
      circles += CircleCollider(px, pz, 0.5f, "Atoll Palm")
    }

    // ---------------------------------------------------------
    // 5. SCENIC PERIMETER RAILWAY LOOP & VIADUCT BRIDGES
    // ---------------------------------------------------------
    val railwayLoopWaypoints =
      listOf(
        Pair(-226.5f, -81.0f),
        Pair(226.5f, -81.0f),
        Pair(226.5f, 48.8f),
        Pair(-226.5f, 48.8f),
      )
    buildRailwayTrackGeometry(railwayLoopWaypoints, staticBoxes, staticCylinders)

    return ExpandedWorldAdditions(
      waterfalls = waterfalls,
      marketStalls = marketStalls,
      railwayLoopWaypoints = railwayLoopWaypoints,
      marketBotRoutes = marketBotRoutes,
    )
  }

  private fun buildNorthSouthBridge(
    xCenter: Float,
    zMin: Float,
    zMax: Float,
    accentNeon: FloatArray,
    staticBoxes: MutableList<PropBoxSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
  ) {
    val cz = (zMin + zMax) * 0.5f
    val length = zMax - zMin
    staticBoxes +=
      PropBoxSpec(
        cx = xCenter,
        cy = 0.04f,
        cz = cz,
        sx = 10f,
        sy = 0.16f,
        sz = length,
        color = floatArrayOf(0.20f, 0.21f, 0.25f),
        surface = MaterialSurface.ASPHALT,
        castShadow = false,
      )
    for (sideX in listOf(xCenter - 4.75f, xCenter + 4.75f)) {
      staticBoxes +=
        PropBoxSpec(
          cx = sideX,
          cy = 0.55f,
          cz = cz,
          sx = 0.45f,
          sy = 0.90f,
          sz = length,
          color = floatArrayOf(0.76f, 0.78f, 0.82f),
          surface = MaterialSurface.STUCCO,
        )
      staticBoxes +=
        PropBoxSpec(
          cx = sideX,
          cy = 1.04f,
          cz = cz,
          sx = 0.16f,
          sy = 0.12f,
          sz = length,
          color = accentNeon,
          surface = MaterialSurface.EMISSIVE,
          emissive = 0.9f,
          castShadow = false,
        )
      aabbs += AabbCollider(sideX - 0.35f, zMin, sideX + 0.35f, zMax, minY = 0f, maxY = 1.2f, tag = "North Bridge Rail")
    }
    walkableFloors += WalkableFloorSpec(xCenter - 4.5f, zMin, xCenter + 4.5f, zMax, 0.12f, tag = "North Parkway Bridge")
  }

  private fun buildEastWestBridge(
    xCenter: Float,
    zCenter: Float,
    length: Float,
    accentNeon: FloatArray,
    staticBoxes: MutableList<PropBoxSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
  ) {
    val xMin = xCenter - length * 0.5f
    val xMax = xCenter + length * 0.5f
    staticBoxes +=
      PropBoxSpec(
        cx = xCenter,
        cy = 0.04f,
        cz = zCenter,
        sx = length,
        sy = 0.16f,
        sz = 10f,
        color = floatArrayOf(0.20f, 0.21f, 0.25f),
        surface = MaterialSurface.ASPHALT,
        castShadow = false,
      )
    for (sideZ in listOf(zCenter - 4.75f, zCenter + 4.75f)) {
      staticBoxes +=
        PropBoxSpec(
          cx = xCenter,
          cy = 0.55f,
          cz = sideZ,
          sx = length,
          sy = 0.90f,
          sz = 0.45f,
          color = floatArrayOf(0.76f, 0.78f, 0.82f),
          surface = MaterialSurface.STUCCO,
        )
      staticBoxes +=
        PropBoxSpec(
          cx = xCenter,
          cy = 1.04f,
          cz = sideZ,
          sx = length,
          sy = 0.12f,
          sz = 0.16f,
          color = accentNeon,
          surface = MaterialSurface.EMISSIVE,
          emissive = 0.9f,
          castShadow = false,
        )
      aabbs += AabbCollider(xMin, sideZ - 0.35f, xMax, sideZ + 0.35f, minY = 0f, maxY = 1.2f, tag = "Outer Bridge Rail")
    }
    walkableFloors += WalkableFloorSpec(xMin, zCenter - 4.5f, xMax, zCenter + 4.5f, 0.12f, tag = "Outer Bridge")
  }

  private fun buildMountainMassif(
    cx: Float,
    cz: Float,
    baseRadius: Float,
    peakHeight: Float,
    staticBoxes: MutableList<PropBoxSpec>,
    staticCylinders: MutableList<PropCylinderSpec>,
    aabbs: MutableList<AabbCollider>,
    snowCap: Boolean,
  ) {
    val tiers = 5
    val grassTint = floatArrayOf(0.20f, 0.42f, 0.22f)
    val rockTint = floatArrayOf(0.38f, 0.37f, 0.36f)
    val highRockTint = floatArrayOf(0.48f, 0.47f, 0.46f)
    val snowTint = floatArrayOf(0.94f, 0.96f, 0.99f)

    for (t in 0 until tiers) {
      val frac = t / tiers.toFloat()
      val r = baseRadius * (1f - frac * 0.76f)
      val h = peakHeight * (0.24f)
      val cy = peakHeight * frac + h * 0.4f
      val color =
        when {
          t == tiers - 1 && snowCap -> snowTint
          t >= 2 -> highRockTint
          t == 1 -> rockTint
          else -> grassTint
        }
      staticCylinders +=
        PropCylinderSpec(
          cx = cx,
          cy = cy,
          cz = cz,
          radius = r,
          height = h * 1.15f,
          color = color,
          surface = MaterialSurface.SLATE,
          metallic = 0.02f,
          roughness = 0.92f,
        )
      // Flanking rocky ridge spurs
      val spurOffset = r * 0.56f
      staticBoxes +=
        PropBoxSpec(
          cx = cx - spurOffset,
          cy = cy * 0.75f,
          cz = cz + r * 0.2f,
          sx = r * 0.78f,
          sy = h * 1.1f,
          sz = r * 0.78f,
          color = if (t == 0) grassTint else rockTint,
          surface = MaterialSurface.SLATE,
          yawDeg = 22f,
        )
      staticBoxes +=
        PropBoxSpec(
          cx = cx + spurOffset,
          cy = cy * 0.72f,
          cz = cz - r * 0.15f,
          sx = r * 0.72f,
          sy = h * 1.0f,
          sz = r * 0.72f,
          color = if (t == 0) grassTint else rockTint,
          surface = MaterialSurface.SLATE,
          yawDeg = -18f,
        )
    }
    aabbs +=
      AabbCollider(
        cx - baseRadius * 0.72f,
        cz - baseRadius * 0.72f,
        cx + baseRadius * 0.72f,
        cz + baseRadius * 0.72f,
        minY = 0f,
        maxY = peakHeight,
        tag = "Mountain Peak",
      )
  }

  private fun buildCanyonMesa(
    cx: Float,
    cz: Float,
    radius: Float,
    height: Float,
    staticBoxes: MutableList<PropBoxSpec>,
    aabbs: MutableList<AabbCollider>,
  ) {
    val colors =
      listOf(
        floatArrayOf(0.58f, 0.30f, 0.20f),
        floatArrayOf(0.72f, 0.38f, 0.22f),
        floatArrayOf(0.82f, 0.50f, 0.28f),
      )
    for (i in 0..2) {
      val s = radius * (1f - i * 0.25f)
      val h = height * 0.36f
      val cy = i * h + h * 0.45f
      staticBoxes +=
        PropBoxSpec(
          cx = cx,
          cy = cy,
          cz = cz,
          sx = s * 1.4f,
          sy = h,
          sz = s * 1.6f,
          color = colors[i],
          surface = MaterialSurface.SLATE,
          yawDeg = i * 11f,
        )
    }
    aabbs +=
      AabbCollider(
        cx - radius * 0.72f,
        cz - radius * 0.80f,
        cx + radius * 0.72f,
        cz + radius * 0.80f,
        minY = 0f,
        maxY = height,
        tag = "Timber Canyon Mesa",
      )
  }

  private fun buildMarketStallGeometry(
    stall: MarketStallSpec,
    staticBoxes: MutableList<PropBoxSpec>,
    staticCylinders: MutableList<PropCylinderSpec>,
    aabbs: MutableList<AabbCollider>,
  ) {
    val woodCounter = floatArrayOf(0.58f, 0.39f, 0.24f)
    val postDark = floatArrayOf(0.28f, 0.20f, 0.15f)
    val sx = if (stall.yawDeg == 90f) 3.2f else 5.4f
    val sz = if (stall.yawDeg == 90f) 5.4f else 3.2f

    // Stall Counter Table
    staticBoxes +=
      PropBoxSpec(
        cx = stall.x,
        cy = 0.56f,
        cz = stall.z,
        sx = sx * 0.88f,
        sy = 0.96f,
        sz = sz * 0.62f,
        color = woodCounter,
        surface = MaterialSurface.WOOD,
      )
    aabbs +=
      AabbCollider(
        stall.x - sx * 0.45f,
        stall.z - sz * 0.38f,
        stall.x + sx * 0.45f,
        stall.z + sz * 0.38f,
        minY = 0f,
        maxY = 2.6f,
        tag = "Market Stall ${stall.name}",
      )

    // 4 Corner Canopy Posts
    for (ox in listOf(-sx * 0.44f, sx * 0.44f)) {
      for (oz in listOf(-sz * 0.38f, sz * 0.38f)) {
        staticCylinders +=
          PropCylinderSpec(
            cx = stall.x + ox,
            cy = 1.45f,
            cz = stall.z + oz,
            radius = 0.09f,
            height = 2.9f,
            color = postDark,
            surface = MaterialSurface.WOOD,
          )
      }
    }

    // Colorful Canopy Awning + White Trim Stripe
    staticBoxes +=
      PropBoxSpec(
        cx = stall.x,
        cy = 2.92f,
        cz = stall.z,
        sx = sx,
        sy = 0.22f,
        sz = sz,
        color = stall.kind.canopyColor,
        surface = MaterialSurface.CLOTH,
      )
    staticBoxes +=
      PropBoxSpec(
        cx = stall.x,
        cy = 3.05f,
        cz = stall.z,
        sx = sx * 0.42f,
        sy = 0.08f,
        sz = sz * 0.96f,
        color = floatArrayOf(0.96f, 0.96f, 0.94f),
        surface = MaterialSurface.CLOTH,
      )

    // Produce / Custom Goods Crates & Displays on Top of Counter!
    val crateAccent1 = stall.kind.crateColor
    val crateAccent2 =
      when (stall.kind) {
        MarketStallKind.VEGETABLES -> floatArrayOf(0.24f, 0.78f, 0.28f)
        MarketStallKind.FRUITS -> floatArrayOf(0.96f, 0.42f, 0.14f)
        MarketStallKind.SPICES -> floatArrayOf(0.98f, 0.80f, 0.16f)
        MarketStallKind.POTTERY -> floatArrayOf(0.88f, 0.62f, 0.42f)
        MarketStallKind.TEXTILES -> floatArrayOf(0.96f, 0.72f, 0.20f)
        MarketStallKind.BAKERY -> floatArrayOf(0.92f, 0.56f, 0.20f)
      }
    val step = if (stall.yawDeg == 90f) 0f else 1.2f
    val stepZ = if (stall.yawDeg == 90f) 1.2f else 0f
    for (idx in -1..1) {
      val col = if (idx % 2 == 0) crateAccent1 else crateAccent2
      staticBoxes +=
        PropBoxSpec(
          cx = stall.x + idx * step,
          cy = 1.18f,
          cz = stall.z + idx * stepZ,
          sx = 0.82f,
          sy = 0.34f,
          sz = 0.82f,
          color = col,
          surface = MaterialSurface.CLOTH,
        )
    }
    // Warm market lantern bulb under awning
    staticBoxes +=
      PropBoxSpec(
        cx = stall.x,
        cy = 2.66f,
        cz = stall.z,
        sx = 0.24f,
        sy = 0.24f,
        sz = 0.24f,
        color = floatArrayOf(1.0f, 0.86f, 0.46f),
        surface = MaterialSurface.EMISSIVE,
        emissive = 0.95f,
        castShadow = false,
      )
  }

  private fun buildRailwayTrackGeometry(
    waypoints: List<Pair<Float, Float>>,
    staticBoxes: MutableList<PropBoxSpec>,
    staticCylinders: MutableList<PropCylinderSpec>,
  ) {
    if (waypoints.size < 4) return
    val ballastColor = floatArrayOf(0.32f, 0.30f, 0.29f)
    val railColor = floatArrayOf(0.68f, 0.72f, 0.78f)
    val sleeperColor = floatArrayOf(0.42f, 0.26f, 0.16f)
    val viaductColor = floatArrayOf(0.46f, 0.46f, 0.50f)

    // 4 straight track segments along the perimeter loop
    // Segment 0: North (-226.5..-81 -> +226.5,-81), Segment 1: East (+226.5,-81 -> +226.5,48.8)
    // Segment 2: South (+226.5,48.8 -> -226.5,48.8), Segment 3: West (-226.5,48.8 -> -226.5,-81)
    // North track at z = -81.0f
    staticBoxes += PropBoxSpec(0f, 0.12f, -81.0f, 456f, 0.16f, 3.4f, ballastColor, MaterialSurface.SLATE, castShadow = false)
    staticBoxes += PropBoxSpec(0f, 0.26f, -82.0f, 455f, 0.10f, 0.16f, railColor, MaterialSurface.SLATE, metallic = 0.85f, roughness = 0.22f, castShadow = false)
    staticBoxes += PropBoxSpec(0f, 0.26f, -80.0f, 455f, 0.10f, 0.16f, railColor, MaterialSurface.SLATE, metallic = 0.85f, roughness = 0.22f, castShadow = false)

    // South track at z = 48.8f (spanning across islands & water channels via viaduct piers!)
    staticBoxes += PropBoxSpec(0f, 0.12f, 48.8f, 456f, 0.16f, 3.4f, viaductColor, MaterialSurface.SLATE, castShadow = false)
    staticBoxes += PropBoxSpec(0f, 0.26f, 47.8f, 455f, 0.10f, 0.16f, railColor, MaterialSurface.SLATE, metallic = 0.85f, roughness = 0.22f, castShadow = false)
    staticBoxes += PropBoxSpec(0f, 0.26f, 49.8f, 455f, 0.10f, 0.16f, railColor, MaterialSurface.SLATE, metallic = 0.85f, roughness = 0.22f, castShadow = false)
    // Channel piers supporting South viaduct
    for (px in listOf(-217f, -79f, -74f, -69f, 69f, 74f, 79f, 217f)) {
      staticCylinders += PropCylinderSpec(px, -0.45f, 48.8f, 0.85f, 1.4f, viaductColor, MaterialSurface.SLATE)
    }

    // East track at x = 226.5f (z = -81..48.8)
    staticBoxes += PropBoxSpec(226.5f, 0.12f, -16.1f, 3.4f, 0.16f, 132f, ballastColor, MaterialSurface.SLATE, castShadow = false)
    staticBoxes += PropBoxSpec(225.5f, 0.26f, -16.1f, 0.16f, 0.10f, 131f, railColor, MaterialSurface.SLATE, metallic = 0.85f, roughness = 0.22f, castShadow = false)
    staticBoxes += PropBoxSpec(227.5f, 0.26f, -16.1f, 0.16f, 0.10f, 131f, railColor, MaterialSurface.SLATE, metallic = 0.85f, roughness = 0.22f, castShadow = false)

    // West track at x = -226.5f (z = -81..48.8)
    staticBoxes += PropBoxSpec(-226.5f, 0.12f, -16.1f, 3.4f, 0.16f, 132f, ballastColor, MaterialSurface.SLATE, castShadow = false)
    staticBoxes += PropBoxSpec(-225.5f, 0.26f, -16.1f, 0.16f, 0.10f, 131f, railColor, MaterialSurface.SLATE, metallic = 0.85f, roughness = 0.22f, castShadow = false)
    staticBoxes += PropBoxSpec(-227.5f, 0.26f, -16.1f, 0.16f, 0.10f, 131f, railColor, MaterialSurface.SLATE, metallic = 0.85f, roughness = 0.22f, castShadow = false)

    // Wooden sleepers spaced cleanly along all 4 sides
    var sx = -220f
    while (sx <= 220f) {
      staticBoxes += PropBoxSpec(sx, 0.21f, -81.0f, 0.34f, 0.06f, 2.5f, sleeperColor, MaterialSurface.WOOD, castShadow = false)
      staticBoxes += PropBoxSpec(sx, 0.21f, 48.8f, 0.34f, 0.06f, 2.5f, sleeperColor, MaterialSurface.WOOD, castShadow = false)
      sx += 5.5f
    }
    var sz = -76f
    while (sz <= 44f) {
      staticBoxes += PropBoxSpec(226.5f, 0.21f, sz, 2.5f, 0.06f, 0.34f, sleeperColor, MaterialSurface.WOOD, castShadow = false)
      staticBoxes += PropBoxSpec(-226.5f, 0.21f, sz, 2.5f, 0.06f, 0.34f, sleeperColor, MaterialSurface.WOOD, castShadow = false)
      sz += 5.5f
    }
  }
}
