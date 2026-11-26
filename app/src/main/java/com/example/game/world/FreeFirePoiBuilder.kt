package com.example.game.world

import com.example.game.math.AabbCollider
import com.example.game.math.CircleCollider
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Populates Free Fire-inspired realistic POIs across the undulating terrain:
 * Farmtopia pitched-roof barns/cottages, climbable 2-tier Sniper Watchtowers, Mortar Ruins,
 * Rust Town hangars & shipping containers, Museum & Grav Labs geodesic domes, Turbine wind farms,
 * Intellect Center hilltop citadel, Deca Square terrace, mixed forests, and craggy boulders.
 *
 * All structures sit cleanly on [TerrainHeightfield.elevationAt] and never alter `world.houses`.
 */
object FreeFirePoiBuilder {
  fun buildAll(
    houses: List<HouseSpec>,
    trees: MutableList<TreeSpec>,
    staticBoxes: MutableList<PropBoxSpec>,
    staticCylinders: MutableList<PropCylinderSpec>,
    aabbs: MutableList<AabbCollider>,
    circles: MutableList<CircleCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
    gabledRoofs: MutableList<GabledRoofSpec>,
    boulders: MutableList<BoulderSpec>,
    windTurbines: MutableList<WindTurbineSpec>,
    domes: MutableList<DomeSpec>,
  ) {
    // 1. FARMTOPIA: Red Gabled Barn, Silo & Pitched-Roof Farmhouse Cottages
    buildFarmtopiaBarn(36f, 36f, staticBoxes, staticCylinders, aabbs, circles, walkableFloors, gabledRoofs)
    buildPitchedCottage(-36f, -42f, yawDeg = 0f, wallCol = floatArrayOf(0.88f, 0.80f, 0.68f), roofCol = floatArrayOf(0.70f, 0.26f, 0.20f), staticBoxes, aabbs, walkableFloors, gabledRoofs)
    buildPitchedCottage(118f, -110f, yawDeg = 0f, wallCol = floatArrayOf(0.84f, 0.86f, 0.78f), roofCol = floatArrayOf(0.24f, 0.42f, 0.56f), staticBoxes, aabbs, walkableFloors, gabledRoofs)

    // 2. SNIPER WATCHTOWERS (3 climbable 2-tier wood/steel tactical towers with pitched roofs!)
    buildSniperWatchtower(-120f, -45f, staticBoxes, staticCylinders, aabbs, walkableFloors, gabledRoofs)
    buildSniperWatchtower(120f, -44f, staticBoxes, staticCylinders, aabbs, walkableFloors, gabledRoofs)
    buildSniperWatchtower(66f, -114f, staticBoxes, staticCylinders, aabbs, walkableFloors, gabledRoofs)

    // 3. MORTAR RUINS: Crater Bunkers, Sandbags & Weathered Ruin Walls near (246, -45)
    buildMortarRuins(246f, -45f, staticBoxes, aabbs, walkableFloors)

    // 4. RUST TOWN: Industrial Hangar Warehouse, Climbable Shipping Containers & Crane at (276, 38)
    buildRustTownDepot(276f, 38f, staticBoxes, staticCylinders, aabbs, walkableFloors, gabledRoofs)

    // 5. MUSEUM & GRAV LABS: Futuristic Geodesic Domes + Stepped Observation Plazas
    buildGeodesicPoiDome(-182f, 38f, radius = 9.0f, domeH = 6.2f, domeColor = floatArrayOf(0.22f, 0.78f, 0.92f), plazaColor = floatArrayOf(0.84f, 0.84f, 0.86f), staticBoxes, staticCylinders, aabbs, walkableFloors, domes)
    buildGeodesicPoiDome(-184f, -44f, radius = 8.4f, domeH = 5.8f, domeColor = floatArrayOf(0.96f, 0.62f, 0.22f), plazaColor = floatArrayOf(0.74f, 0.78f, 0.82f), staticBoxes, staticCylinders, aabbs, walkableFloors, domes)

    // 6. TURBINE HEADLAND: Spinning Wind Turbines on Coastal Bluffs
    for ((tx, tz, yaw) in listOf(
      Triple(-260f, -46f, 20f),
      Triple(-284f, -40f, 32f),
      Triple(-244f, 40f, -15f),
    )) {
      val ty = TerrainHeightfield.elevationAt(tx, tz)
      windTurbines += WindTurbineSpec(tx, ty, tz, towerHeight = 16.5f, bladeRadius = 6.8f, yawDeg = yaw)
      circles += CircleCollider(tx, tz, 1.15f, "Wind Turbine Base")
    }

    // 7. INTELLECT CENTER: North Sierra Foothills Stepped Citadel at (0, -119)
    buildIntellectCitadel(0f, -119f, staticBoxes, staticCylinders, aabbs, walkableFloors, gabledRoofs, domes)

    // 8. DECA SQUARE / PLAZARIA TERRACE: Stepped Warm Terracotta Pavilion at (182, -42)
    buildDecaSquarePavilion(182f, -42f, staticBoxes, staticCylinders, aabbs, walkableFloors, gabledRoofs)

    // 9. REALISTIC MIXED FORESTS & ANGULAR ROCKY BOULDERS
    populateMixedForestAndBoulders(houses, trees, circles, boulders)
  }

  private fun buildFarmtopiaBarn(
    cx: Float,
    cz: Float,
    staticBoxes: MutableList<PropBoxSpec>,
    staticCylinders: MutableList<PropCylinderSpec>,
    aabbs: MutableList<AabbCollider>,
    circles: MutableList<CircleCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
    gabledRoofs: MutableList<GabledRoofSpec>,
  ) {
    val y0 = TerrainHeightfield.elevationAt(cx, cz) - 0.12f
    val barnRed = floatArrayOf(0.68f, 0.18f, 0.16f)
    val trimWhite = floatArrayOf(0.94f, 0.92f, 0.88f)
    val woodDark = floatArrayOf(0.38f, 0.24f, 0.16f)
    val hayGold = floatArrayOf(0.86f, 0.72f, 0.28f)
    val siloMetal = floatArrayOf(0.76f, 0.80f, 0.84f)

    // Stone Barn Plinth + Walkable Floor
    staticBoxes += PropBoxSpec(cx, y0 + 0.18f, cz, 11.4f, 0.44f, 13.4f, floatArrayOf(0.48f, 0.45f, 0.42f), MaterialSurface.SLATE, castShadow = false)
    walkableFloors += WalkableFloorSpec(cx - 5.6f, cz - 6.6f, cx + 5.6f, cz + 6.6f, y0 + 0.40f, tag = "Farmtopia Barn")

    // Left & Right Side Walls + Back Wall + Front Twin Door Jambs (open walk-in front south)
    staticBoxes += PropBoxSpec(cx - 4.8f, y0 + 2.4f, cz, 0.45f, 4.0f, 12.0f, barnRed, MaterialSurface.WOOD)
    staticBoxes += PropBoxSpec(cx + 4.8f, y0 + 2.4f, cz, 0.45f, 4.0f, 12.0f, barnRed, MaterialSurface.WOOD)
    staticBoxes += PropBoxSpec(cx, y0 + 2.4f, cz - 5.8f, 10.0f, 4.0f, 0.45f, barnRed, MaterialSurface.WOOD)
    staticBoxes += PropBoxSpec(cx - 3.3f, y0 + 2.4f, cz + 5.8f, 3.4f, 4.0f, 0.45f, barnRed, MaterialSurface.WOOD)
    staticBoxes += PropBoxSpec(cx + 3.3f, y0 + 2.4f, cz + 5.8f, 3.4f, 4.0f, 0.45f, barnRed, MaterialSurface.WOOD)
    // White Barn X-brace trim
    staticBoxes += PropBoxSpec(cx, y0 + 4.35f, cz + 5.95f, 10.2f, 0.28f, 0.22f, trimWhite, MaterialSurface.WOOD)

    aabbs += AabbCollider(cx - 5.1f, cz - 6.1f, cx - 4.5f, cz + 6.1f, y0, y0 + 4.5f, "Barn West Wall")
    aabbs += AabbCollider(cx + 4.5f, cz - 6.1f, cx + 5.1f, cz + 6.1f, y0, y0 + 4.5f, "Barn East Wall")
    aabbs += AabbCollider(cx - 5.1f, cz - 6.1f, cx + 5.1f, cz - 5.5f, y0, y0 + 4.5f, "Barn North Wall")
    aabbs += AabbCollider(cx - 5.1f, cz + 5.5f, cx - 1.5f, cz + 6.1f, y0, y0 + 4.5f, "Barn Front L")
    aabbs += AabbCollider(cx + 1.5f, cz + 5.5f, cx + 5.1f, cz + 6.1f, y0, y0 + 4.5f, "Barn Front R")

    // Pitched Gabled Roof
    gabledRoofs += GabledRoofSpec(cx, y0 + 4.38f, cz, width = 11.4f, height = 3.2f, length = 13.0f, yawDeg = 0f, color = floatArrayOf(0.28f, 0.25f, 0.24f), surface = MaterialSurface.SLATE)

    // Cylindrical Grain Silo beside the Barn
    val sx = cx + 7.2f
    val sz = cz - 3.6f
    val sy = TerrainHeightfield.elevationAt(sx, sz) - 0.1f
    staticCylinders += PropCylinderSpec(sx, sy + 4.2f, sz, 1.9f, 8.4f, siloMetal, MaterialSurface.SLATE, metallic = 0.72f, roughness = 0.28f)
    staticCylinders += PropCylinderSpec(sx, sy + 8.6f, sz, 2.0f, 0.55f, barnRed, MaterialSurface.SLATE)
    circles += CircleCollider(sx, sz, 2.0f, "Farmtopia Silo")

    // Interior & Exterior Hay Bales (cover for gunfights!)
    for ((hx, hz) in listOf(-2.8f to -2.4f, 2.8f to 1.8f, -6.8f to 3.4f)) {
      val by = y0 + 0.85f
      staticBoxes += PropBoxSpec(cx + hx, by, cz + hz, 1.6f, 1.1f, 1.1f, hayGold, MaterialSurface.GRASS)
      aabbs += AabbCollider(cx + hx - 0.85f, cz + hz - 0.6f, cx + hx + 0.85f, cz + hz + 0.6f, y0, by + 0.6f, "Hay Bale")
    }
    // Timber Post Fence Line
    for (fx in listOf(-6.4f, -3.8f, 3.8f, 6.4f)) {
      staticBoxes += PropBoxSpec(cx + fx, y0 + 0.6f, cz + 8.4f, 0.24f, 1.2f, 0.24f, woodDark, MaterialSurface.WOOD)
    }
  }

  private fun buildPitchedCottage(
    cx: Float,
    cz: Float,
    yawDeg: Float,
    wallCol: FloatArray,
    roofCol: FloatArray,
    staticBoxes: MutableList<PropBoxSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
    gabledRoofs: MutableList<GabledRoofSpec>,
  ) {
    val y0 = TerrainHeightfield.elevationAt(cx, cz) - 0.12f
    staticBoxes += PropBoxSpec(cx, y0 + 0.16f, cz, 8.6f, 0.40f, 7.6f, floatArrayOf(0.54f, 0.50f, 0.45f), MaterialSurface.SLATE, yawDeg = yawDeg, castShadow = false)
    walkableFloors += WalkableFloorSpec(cx - 4.2f, cz - 3.7f, cx + 4.2f, cz + 3.7f, y0 + 0.36f, tag = "Farmhouse Cottage")

    // Simple enterable cottage shell (open front doorway toward +Z)
    staticBoxes += PropBoxSpec(cx - 3.8f, y0 + 1.85f, cz, 0.38f, 3.0f, 6.8f, wallCol, MaterialSurface.STUCCO)
    staticBoxes += PropBoxSpec(cx + 3.8f, y0 + 1.85f, cz, 0.38f, 3.0f, 6.8f, wallCol, MaterialSurface.STUCCO)
    staticBoxes += PropBoxSpec(cx, y0 + 1.85f, cz - 3.3f, 8.0f, 3.0f, 0.38f, wallCol, MaterialSurface.STUCCO)
    staticBoxes += PropBoxSpec(cx - 2.3f, y0 + 1.85f, cz + 3.3f, 3.0f, 3.0f, 0.38f, wallCol, MaterialSurface.STUCCO)
    staticBoxes += PropBoxSpec(cx + 2.3f, y0 + 1.85f, cz + 3.3f, 3.0f, 3.0f, 0.38f, wallCol, MaterialSurface.STUCCO)
    staticBoxes += PropBoxSpec(cx, y0 + 3.05f, cz + 3.3f, 2.0f, 0.65f, 0.38f, wallCol, MaterialSurface.STUCCO)

    aabbs += AabbCollider(cx - 4.0f, cz - 3.5f, cx - 3.6f, cz + 3.5f, y0, y0 + 3.4f, "Cottage Wall L")
    aabbs += AabbCollider(cx + 3.6f, cz - 3.5f, cx + 4.0f, cz + 3.5f, y0, y0 + 3.4f, "Cottage Wall R")
    aabbs += AabbCollider(cx - 4.0f, cz - 3.5f, cx + 4.0f, cz - 3.1f, y0, y0 + 3.4f, "Cottage Wall B")
    aabbs += AabbCollider(cx - 4.0f, cz + 3.1f, cx - 0.9f, cz + 3.5f, y0, y0 + 3.4f, "Cottage Front L")
    aabbs += AabbCollider(cx + 0.9f, cz + 3.1f, cx + 4.0f, cz + 3.5f, y0, y0 + 3.4f, "Cottage Front R")

    gabledRoofs += GabledRoofSpec(cx, y0 + 3.32f, cz, width = 9.0f, height = 2.4f, length = 7.8f, yawDeg = yawDeg, color = roofCol, surface = MaterialSurface.TILE)
  }

  private fun buildSniperWatchtower(
    cx: Float,
    cz: Float,
    staticBoxes: MutableList<PropBoxSpec>,
    staticCylinders: MutableList<PropCylinderSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
    gabledRoofs: MutableList<GabledRoofSpec>,
  ) {
    val y0 = TerrainHeightfield.elevationAt(cx, cz) - 0.1f
    val wood = floatArrayOf(0.46f, 0.30f, 0.18f)
    val darkSteel = floatArrayOf(0.24f, 0.27f, 0.32f)
    val deckY = y0 + 4.2f

    // 4 Corner Support Pillars
    for ((px, pz) in listOf(-2.0f to -2.0f, 2.0f to -2.0f, -2.0f to 2.0f, 2.0f to 2.0f)) {
      staticCylinders += PropCylinderSpec(cx + px, y0 + 3.4f, cz + pz, 0.20f, 6.8f, wood, MaterialSurface.WOOD)
      aabbs += AabbCollider(cx + px - 0.25f, cz + pz - 0.25f, cx + px + 0.25f, cz + pz + 0.25f, y0, deckY, "Tower Leg")
    }

    // Elevated Sniper Deck + Protective Chest-High Guardrails (open on South for ramp entry)
    staticBoxes += PropBoxSpec(cx, deckY - 0.12f, cz, 4.8f, 0.24f, 4.8f, wood, MaterialSurface.WOOD)
    walkableFloors += WalkableFloorSpec(cx - 2.4f, cz - 2.4f, cx + 2.4f, cz + 2.4f, deckY, tag = "Sniper Watchtower Deck")

    staticBoxes += PropBoxSpec(cx, deckY + 0.55f, cz - 2.25f, 4.6f, 1.05f, 0.22f, darkSteel, MaterialSurface.SLATE)
    staticBoxes += PropBoxSpec(cx - 2.25f, deckY + 0.55f, cz, 0.22f, 1.05f, 4.6f, darkSteel, MaterialSurface.SLATE)
    staticBoxes += PropBoxSpec(cx + 2.25f, deckY + 0.55f, cz, 0.22f, 1.05f, 4.6f, darkSteel, MaterialSurface.SLATE)
    aabbs += AabbCollider(cx - 2.4f, cz - 2.4f, cx + 2.4f, cz - 2.1f, deckY, deckY + 1.1f, "Tower Rail N")
    aabbs += AabbCollider(cx - 2.4f, cz - 2.4f, cx - 2.1f, cz + 2.4f, deckY, deckY + 1.1f, "Tower Rail W")
    aabbs += AabbCollider(cx + 2.1f, cz - 2.4f, cx + 2.4f, cz + 2.4f, deckY, deckY + 1.1f, "Tower Rail E")

    // Climbable Access Ramp from South (+Z climbing toward -Z)
    staticBoxes += PropBoxSpec(cx, y0 + 2.0f, cz + 4.9f, 1.7f, 0.20f, 5.6f, wood, MaterialSurface.WOOD)
    walkableFloors +=
      WalkableFloorSpec(
        minX = cx - 0.9f,
        minZ = cz + 2.2f,
        maxX = cx + 0.9f,
        maxZ = cz + 7.5f,
        baseElevationY = y0,
        endElevationY = deckY,
        rampAxis = RampAxis.NEG_Z,
        tag = "Watchtower Stairs",
      )

    // Canopy Roof Overhead
    gabledRoofs += GabledRoofSpec(cx, y0 + 6.75f, cz, width = 5.4f, height = 1.6f, length = 5.4f, yawDeg = 0f, color = floatArrayOf(0.24f, 0.36f, 0.28f), surface = MaterialSurface.SLATE)
  }

  private fun buildMortarRuins(
    cx: Float,
    cz: Float,
    staticBoxes: MutableList<PropBoxSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
  ) {
    val stone = floatArrayOf(0.56f, 0.52f, 0.46f)
    val sandbag = floatArrayOf(0.68f, 0.60f, 0.44f)
    val bunkerDark = floatArrayOf(0.36f, 0.36f, 0.38f)

    // 4 Ruined Concrete Walls + Sandbag Barricades radiating around crater rim
    for (i in 0 until 6) {
      val ang = i * (Math.PI.toFloat() / 3f)
      val rx = cx + cos(ang) * 11.5f
      val rz = cz + sin(ang) * 11.5f
      val ry = TerrainHeightfield.elevationAt(rx, rz) - 0.1f
      val yaw = Math.toDegrees(ang.toDouble()).toFloat()
      if (i % 2 == 0) {
        staticBoxes += PropBoxSpec(rx, ry + 1.4f, rz, 0.65f, 2.8f, 4.4f, stone, MaterialSurface.STUCCO, yawDeg = yaw)
        aabbs += AabbCollider(rx - 1.6f, rz - 1.6f, rx + 1.6f, rz + 1.6f, ry, ry + 2.8f, "Mortar Ruin Wall")
      } else {
        staticBoxes += PropBoxSpec(rx, ry + 0.55f, rz, 1.1f, 1.1f, 3.8f, sandbag, MaterialSurface.CLOTH, yawDeg = yaw)
        aabbs += AabbCollider(rx - 1.4f, rz - 1.4f, rx + 1.4f, rz + 1.4f, ry, ry + 1.1f, "Sandbag Cover")
      }
    }
    // Central Pillbox Bunker Slab
    val cy = TerrainHeightfield.elevationAt(cx, cz)
    staticBoxes += PropBoxSpec(cx, cy + 0.3f, cz, 4.4f, 0.7f, 4.4f, bunkerDark, MaterialSurface.SLATE)
    walkableFloors += WalkableFloorSpec(cx - 2.2f, cz - 2.2f, cx + 2.2f, cz + 2.2f, cy + 0.65f, tag = "Mortar Crater Bunker")
  }

  private fun buildRustTownDepot(
    cx: Float,
    cz: Float,
    staticBoxes: MutableList<PropBoxSpec>,
    staticCylinders: MutableList<PropCylinderSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
    gabledRoofs: MutableList<GabledRoofSpec>,
  ) {
    val y0 = TerrainHeightfield.elevationAt(cx, cz) - 0.1f
    val rustSteel = floatArrayOf(0.46f, 0.34f, 0.28f)
    val hangarSlate = floatArrayOf(0.32f, 0.36f, 0.42f)
    val blueCont = floatArrayOf(0.18f, 0.44f, 0.68f)
    val orangeCont = floatArrayOf(0.84f, 0.40f, 0.14f)
    val craneYellow = floatArrayOf(0.92f, 0.72f, 0.16f)

    // Industrial Hangar (open on East & West ends for run-through gunfights!)
    staticBoxes += PropBoxSpec(cx, y0 + 0.14f, cz, 12.0f, 0.36f, 10.0f, hangarSlate, MaterialSurface.SLATE, castShadow = false)
    walkableFloors += WalkableFloorSpec(cx - 6.0f, cz - 5.0f, cx + 6.0f, cz + 5.0f, y0 + 0.32f, tag = "Rust Town Hangar")
    staticBoxes += PropBoxSpec(cx, y0 + 2.5f, cz - 4.8f, 11.8f, 4.6f, 0.42f, hangarSlate, MaterialSurface.SLATE, metallic = 0.45f)
    staticBoxes += PropBoxSpec(cx, y0 + 2.5f, cz + 4.8f, 11.8f, 4.6f, 0.42f, hangarSlate, MaterialSurface.SLATE, metallic = 0.45f)
    aabbs += AabbCollider(cx - 5.9f, cz - 5.0f, cx + 5.9f, cz - 4.5f, y0, y0 + 4.8f, "Hangar North Wall")
    aabbs += AabbCollider(cx - 5.9f, cz + 4.5f, cx + 5.9f, cz + 5.0f, y0, y0 + 4.8f, "Hangar South Wall")
    gabledRoofs += GabledRoofSpec(cx, y0 + 4.78f, cz, width = 10.8f, height = 2.2f, length = 12.4f, yawDeg = 90f, color = rustSteel, surface = MaterialSurface.SLATE)

    // 2 Climbable Steel Shipping Containers beside the Hangar
    val c1x = cx - 9.2f
    val c1z = cz - 2.0f
    val c1y = TerrainHeightfield.elevationAt(c1x, c1z) - 0.08f
    staticBoxes += PropBoxSpec(c1x, c1y + 1.45f, c1z, 2.8f, 2.9f, 6.2f, blueCont, MaterialSurface.SLATE, metallic = 0.55f)
    aabbs += AabbCollider(c1x - 1.45f, c1z - 3.15f, c1x + 1.45f, c1z + 3.15f, c1y, c1y + 2.9f, "Blue Container")
    walkableFloors += WalkableFloorSpec(c1x - 1.4f, c1z - 3.1f, c1x + 1.4f, c1z + 3.1f, c1y + 2.9f, tag = "Container Roof")

    val c2x = cx + 8.8f
    val c2z = cz + 2.5f
    val c2y = TerrainHeightfield.elevationAt(c2x, c2z) - 0.08f
    staticBoxes += PropBoxSpec(c2x, c2y + 1.45f, c2z, 6.2f, 2.9f, 2.8f, orangeCont, MaterialSurface.SLATE, metallic = 0.55f)
    aabbs += AabbCollider(c2x - 3.15f, c2z - 1.45f, c2x + 3.15f, c2z + 1.45f, c2y, c2y + 2.9f, "Orange Container")
    walkableFloors += WalkableFloorSpec(c2x - 3.1f, c2z - 1.4f, c2x + 3.1f, c2z + 1.4f, c2y + 2.9f, tag = "Container Roof")

    // Tall Industrial Yard Crane
    val crx = cx + 8.5f
    val crz = cz - 6.0f
    val cry = TerrainHeightfield.elevationAt(crx, crz)
    staticCylinders += PropCylinderSpec(crx, cry + 7.5f, crz, 0.55f, 15.0f, craneYellow, MaterialSurface.SLATE, metallic = 0.45f)
    staticBoxes += PropBoxSpec(crx - 4.5f, cry + 14.8f, crz, 13.5f, 0.75f, 0.85f, craneYellow, MaterialSurface.SLATE, metallic = 0.45f)
    aabbs += AabbCollider(crx - 0.65f, crz - 0.65f, crx + 0.65f, crz + 0.65f, cry, cry + 15.0f, "Rust Crane Mast")
  }

  private fun buildGeodesicPoiDome(
    cx: Float,
    cz: Float,
    radius: Float,
    domeH: Float,
    domeColor: FloatArray,
    plazaColor: FloatArray,
    staticBoxes: MutableList<PropBoxSpec>,
    staticCylinders: MutableList<PropCylinderSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
    domes: MutableList<DomeSpec>,
  ) {
    val y0 = TerrainHeightfield.elevationAt(cx, cz) - 0.1f
    staticCylinders += PropCylinderSpec(cx, y0 + 0.35f, cz, radius + 1.8f, 0.9f, plazaColor, MaterialSurface.TILE)
    walkableFloors += WalkableFloorSpec(cx - radius - 1.5f, cz - radius - 1.5f, cx + radius + 1.5f, cz + radius + 1.5f, y0 + 0.80f, tag = "Lab Dome Terrace")

    // Twin Flanking Wing Entry Arch Pillars
    staticBoxes += PropBoxSpec(cx - radius * 0.52f, y0 + 2.1f, cz + radius * 0.86f, 1.4f, 2.8f, 2.2f, plazaColor, MaterialSurface.STUCCO)
    staticBoxes += PropBoxSpec(cx + radius * 0.52f, y0 + 2.1f, cz + radius * 0.86f, 1.4f, 2.8f, 2.2f, plazaColor, MaterialSurface.STUCCO)
    aabbs += AabbCollider(cx - radius * 0.72f, cz - radius * 0.72f, cx + radius * 0.72f, cz + radius * 0.6f, y0, y0 + domeH, "Science Dome")

    domes += DomeSpec(cx, y0 + 0.75f, cz, radiusX = radius, height = domeH, radiusZ = radius, color = domeColor, surface = MaterialSurface.GLASS, metallic = 0.72f, roughness = 0.12f, alpha = 0.76f)
  }

  private fun buildIntellectCitadel(
    cx: Float,
    cz: Float,
    staticBoxes: MutableList<PropBoxSpec>,
    staticCylinders: MutableList<PropCylinderSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
    gabledRoofs: MutableList<GabledRoofSpec>,
    domes: MutableList<DomeSpec>,
  ) {
    val y0 = TerrainHeightfield.elevationAt(cx, cz) - 0.15f
    val stone = floatArrayOf(0.76f, 0.78f, 0.82f)
    val accent = floatArrayOf(0.20f, 0.62f, 0.88f)
    // Stepped Circular Terrace Rings
    staticCylinders += PropCylinderSpec(cx, y0 + 0.35f, cz, 12.2f, 0.9f, stone, MaterialSurface.TILE)
    staticCylinders += PropCylinderSpec(cx, y0 + 0.95f, cz, 7.6f, 0.7f, floatArrayOf(0.62f, 0.66f, 0.72f), MaterialSurface.TILE)
    walkableFloors += WalkableFloorSpec(cx - 11.5f, cz - 11.5f, cx + 11.5f, cz + 11.5f, y0 + 0.80f, tag = "Intellect Citadel")
    walkableFloors += WalkableFloorSpec(cx - 7.0f, cz - 7.0f, cx + 7.0f, cz + 7.0f, y0 + 1.30f, tag = "Intellect Inner Plinth")

    // Central Observatory Dome + Twin Flanking Wing Buildings
    domes += DomeSpec(cx, y0 + 1.30f, cz, radiusX = 4.6f, height = 3.8f, radiusZ = 4.6f, color = accent, surface = MaterialSurface.GLASS, alpha = 0.82f)
    aabbs += AabbCollider(cx - 3.6f, cz - 3.6f, cx + 3.6f, cz + 3.6f, y0 + 1.2f, y0 + 5.0f, "Intellect Core")

    for (ox in listOf(-8.5f, 8.5f)) {
      staticBoxes += PropBoxSpec(cx + ox, y0 + 2.1f, cz, 3.6f, 2.6f, 6.2f, stone, MaterialSurface.STUCCO)
      aabbs += AabbCollider(cx + ox - 1.9f, cz - 3.2f, cx + ox + 1.9f, cz + 3.2f, y0 + 0.8f, y0 + 3.5f, "Intellect Wing")
      gabledRoofs += GabledRoofSpec(cx + ox, y0 + 3.38f, cz, width = 4.1f, height = 1.4f, length = 6.6f, yawDeg = 0f, color = floatArrayOf(0.18f, 0.36f, 0.56f), surface = MaterialSurface.SLATE)
    }
  }

  private fun buildDecaSquarePavilion(
    cx: Float,
    cz: Float,
    staticBoxes: MutableList<PropBoxSpec>,
    staticCylinders: MutableList<PropCylinderSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
    gabledRoofs: MutableList<GabledRoofSpec>,
  ) {
    val y0 = TerrainHeightfield.elevationAt(cx, cz) - 0.1f
    val terracotta = floatArrayOf(0.84f, 0.52f, 0.36f)
    val cream = floatArrayOf(0.92f, 0.88f, 0.80f)
    staticBoxes += PropBoxSpec(cx, y0 + 0.25f, cz, 11.0f, 0.6f, 9.5f, cream, MaterialSurface.TILE, castShadow = false)
    walkableFloors += WalkableFloorSpec(cx - 5.4f, cz - 4.7f, cx + 5.4f, cz + 4.7f, y0 + 0.55f, tag = "Deca Square Plaza")

    for ((ox, oz) in listOf(-3.8f to -3.2f, 3.8f to -3.2f, -3.8f to 3.2f, 3.8f to 3.2f)) {
      staticCylinders += PropCylinderSpec(cx + ox, y0 + 2.2f, cz + oz, 0.32f, 3.4f, cream, MaterialSurface.STUCCO)
      aabbs += AabbCollider(cx + ox - 0.35f, cz + oz - 0.35f, cx + ox + 0.35f, cz + oz + 0.35f, y0, y0 + 3.9f, "Plaza Pillar")
    }
    gabledRoofs += GabledRoofSpec(cx, y0 + 3.85f, cz, width = 9.2f, height = 2.1f, length = 7.8f, yawDeg = 0f, color = terracotta, surface = MaterialSurface.TILE)
  }

  private fun populateMixedForestAndBoulders(
    houses: List<HouseSpec>,
    trees: MutableList<TreeSpec>,
    circles: MutableList<CircleCollider>,
    boulders: MutableList<BoulderSpec>,
  ) {
    fun isClearOfRoadsAndStructures(x: Float, z: Float): Boolean {
      if (abs(z - 16f) < 7.8f || abs(z + 24f) < 7.8f) return false
      if (abs(z + 96f) < 8.5f) return false
      if (abs(z + 81f) < 5.5f || abs(z - 48.8f) < 5.5f) return false
      if (abs(x - 226.5f) < 5.5f || abs(x + 226.5f) < 5.5f) return false
      for (rx in floatArrayOf(-273f, -233f, -194f, -148f, -104f, -42f, 0f, 42f, 106f, 148f, 190f, 233f, 299f)) {
        if (abs(x - rx) < 7.5f && z in -102f..55f) return false
      }
      if (hypot(x - 27.5f, z + 2f) < 12f) return false // Gun Shop
      if (x in 236f..296f && z in -25f..18f) return false // Market Plaza
      if (x in 130f..186f && z in -20f..14f) return false // Central Park & Tennis
      if (hypot(x - 48f, z + 106f) < 13f) return false // Helipad
      if (hypot(x + 14f, z + 135f) < 15f || hypot(x - 168f, z + 139f) < 14f) return false // Waterfalls
      if (hypot(x - 36f, z - 36f) < 12f || hypot(x + 36f, z + 42f) < 10f) return false // Farmtopia
      if (hypot(x - 276f, z - 38f) < 14f || hypot(x, z + 119f) < 14f) return false // Rust Town & Intellect
      if (hypot(x + 182f, z - 38f) < 12f || hypot(x + 184f, z + 44f) < 12f) return false // Museum & Grav Labs
      if (hypot(x, z - 178f) < 8f) return false // Atoll Lighthouse
      for (h in houses) {
        if (abs(x - h.centerX) < h.width * 0.5f + 5.5f && abs(z - h.centerZ) < h.depth * 0.5f + 5.5f) return false
      }
      return true
    }

    // Deterministic grid-jittered natural tree & boulder scatter across all landmasses
    val step = 22f
    var gx = -298f
    var idx = 0
    while (gx <= 298f) {
      var gz = -196f
      while (gz <= 202f) {
        idx++
        val h1 = fractHash(idx * 17.31f + gx * 0.13f)
        val h2 = fractHash(idx * 29.73f + gz * 0.19f)
        val h3 = fractHash(idx * 43.17f)
        val px = gx + (h1 - 0.5f) * 14f
        val pz = gz + (h2 - 0.5f) * 14f
        val elev = TerrainHeightfield.elevationAt(px, pz)
        val coast = TerrainHeightfield.signedCoastDistance(px, pz)

        if (elev in 0.45f..22f && coast < -4.5f && isClearOfRoadsAndStructures(px, pz)) {
          val n = FloatArray(3)
          TerrainHeightfield.normalAt(px, pz, n)
          val slope = n[1]
          if (h3 > 0.34f && slope > 0.72f) {
            val kind =
              when {
                elev > 6.5f || pz < -88f -> TreeKind.PINE_CONIFER
                px < -218f -> if (h1 > 0.45f) TreeKind.CYPRESS_ACACIA else TreeKind.PINE_CONIFER
                pz > 40f || pz > 148f -> if (h2 > 0.4f) TreeKind.PALM else TreeKind.TROPICAL_SHADE
                else ->
                  when ((idx + (h1 * 10).toInt()) % 4) {
                    0 -> TreeKind.PINE_CONIFER
                    1 -> TreeKind.CYPRESS_ACACIA
                    2 -> TreeKind.TROPICAL_SHADE
                    else -> TreeKind.PALM
                  }
              }
            val height = 6.8f + h1 * 4.4f
            trees += TreeSpec(px, pz, height, (h2 * 360f), kind, tintShift = h1 * 0.25f)
            circles += CircleCollider(px, pz, 0.56f, "Forest Tree")
          } else if (h3 in 0.14f..0.33f && (elev > 1.2f || coast > -9f)) {
            val rx = 0.9f + h1 * 1.9f
            val ry = 0.65f + h2 * 1.45f
            val rz = 0.85f + fractHash(h1 + h2) * 1.8f
            val rockColor =
              if (px < -218f) floatArrayOf(0.68f, 0.38f, 0.24f)
              else if (elev > 14f) floatArrayOf(0.56f, 0.58f, 0.62f)
              else floatArrayOf(0.46f, 0.46f, 0.44f)
            boulders += BoulderSpec(px, elev + ry * 0.25f, pz, rx, ry, rz, yawDeg = h1 * 360f, pitchDeg = (h2 - 0.5f) * 24f, color = rockColor)
            if (rx > 1.35f) {
              circles += CircleCollider(px, pz, rx * 0.78f, "Boulder Outcrop")
            }
          }
        }
        gz += step
      }
      gx += step
    }
  }

  private fun fractHash(v: Float): Float {
    val s = sin(v * 12.9898f) * 43758.5453f
    return s - kotlin.math.floor(s)
  }
}
