package com.example.game.world

import com.example.game.math.AabbCollider
import com.example.game.math.CircleCollider
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Builds the 12 signature Nextera map POIs matching the reference overhead map:
 * Intellect Center, Museum, Turbine, Farmtopia, Grav Labs, Plazaria, Deca Square,
 * Mud Site, Zipway, Twin Bridge, Mortar Ruins, and Rust Town, plus mixed forests & boulders.
 */
object NexteraLandmarksBuilder {
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
    buildIntellectCenter(-18f, -180f, staticBoxes, staticCylinders, aabbs, walkableFloors, domes)
    buildMuseum(-178f, -80f, staticBoxes, staticCylinders, aabbs, walkableFloors, gabledRoofs)
    buildTurbineHeadland(staticCylinders, circles, windTurbines)
    buildFarmtopiaBarn(-72f, 6f, staticBoxes, staticCylinders, aabbs, circles, walkableFloors, gabledRoofs)
    buildGravLabs(-158f, 168f, staticBoxes, staticCylinders, aabbs, walkableFloors, domes)
    buildPlazaria(-66f, 152f, staticBoxes, aabbs, walkableFloors, gabledRoofs)
    buildDecaSquare(24f, 172f, staticBoxes, staticCylinders, aabbs, walkableFloors, gabledRoofs)
    buildMudSite(154f, 148f, staticBoxes, aabbs, walkableFloors)
    buildZipwayAndZiplines(76f, 14f, staticBoxes, staticCylinders, aabbs, walkableFloors)
    buildTwinBridgeOverpass(116f, -148f, staticBoxes, staticCylinders, aabbs, walkableFloors)
    buildMortarRuins(172f, -68f, staticBoxes, staticCylinders, aabbs)
    buildRustTown(188f, 22f, staticBoxes, staticCylinders, aabbs, walkableFloors, gabledRoofs)

    // 3 Climbable 2-Tier Sniper Watchtowers overlooking Nextera valleys
    buildSniperWatchtower(-108f, -44f, staticBoxes, staticCylinders, aabbs, walkableFloors, gabledRoofs)
    buildSniperWatchtower(82f, -92f, staticBoxes, staticCylinders, aabbs, walkableFloors, gabledRoofs)
    buildSniperWatchtower(108f, 66f, staticBoxes, staticCylinders, aabbs, walkableFloors, gabledRoofs)

    populateMixedForestAndBoulders(houses, trees, circles, boulders)
  }

  private fun buildIntellectCenter(
    cx: Float,
    cz: Float,
    staticBoxes: MutableList<PropBoxSpec>,
    staticCylinders: MutableList<PropCylinderSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
    domes: MutableList<DomeSpec>,
  ) {
    val y0 = TerrainHeightfield.elevationAt(cx, cz) - 0.10f
    val stone = floatArrayOf(0.76f, 0.78f, 0.74f)
    val lawnTrim = floatArrayOf(0.46f, 0.56f, 0.42f)
    val goldRoof = floatArrayOf(0.92f, 0.76f, 0.28f)
    val darkSteel = floatArrayOf(0.24f, 0.27f, 0.32f)

    // Outer Concentric Citadel Ring Platform
    staticCylinders += PropCylinderSpec(cx, y0 + 0.35f, cz, 19.5f, 0.70f, lawnTrim, MaterialSurface.TILE)
    staticCylinders += PropCylinderSpec(cx, y0 + 0.90f, cz, 12.2f, 1.15f, stone, MaterialSurface.STUCCO)
    walkableFloors += WalkableFloorSpec(cx - 18f, cz - 18f, cx + 18f, cz + 18f, y0 + 0.70f, tag = "Intellect Outer Plaza")
    walkableFloors += WalkableFloorSpec(cx - 11f, cz - 11f, cx + 11f, cz + 11f, y0 + 1.48f, tag = "Intellect Citadel Deck")

    // Central Octagonal Core + Futuristic Geodesic Dome
    staticCylinders += PropCylinderSpec(cx, y0 + 2.8f, cz, 5.8f, 2.8f, darkSteel, MaterialSurface.SLATE, metallic = 0.5f)
    domes += DomeSpec(cx, y0 + 4.15f, cz, radiusX = 5.6f, height = 3.8f, radiusZ = 5.6f, color = floatArrayOf(0.24f, 0.82f, 0.96f))

    // Radial Wing Pavilions matching Intellect Center ring buildings on map
    for (i in 0 until 6) {
      val ang = i * (PI.toFloat() / 3f)
      val wx = cx + cos(ang) * 14.5f
      val wz = cz + sin(ang) * 14.5f
      val wy = TerrainHeightfield.elevationAt(wx, wz)
      staticBoxes += PropBoxSpec(wx, wy + 1.65f, wz, 5.2f, 3.1f, 3.6f, goldRoof, MaterialSurface.STUCCO, yawDeg = (ang * 180f / PI.toFloat()))
      aabbs += AabbCollider(wx - 2.7f, wz - 2.3f, wx + 2.7f, wz + 2.3f, wy, wy + 3.4f, "Intellect Wing")
    }

    // Northern Elevated Semicircular Viaduct Arc (Row J on fmap.png)
    for (step in -3..3) {
      val ang = (-PI / 2.0 + step * 0.34).toFloat()
      val vx = cx + cos(ang) * 44f
      val vz = cz + sin(ang) * 34f
      val vy = TerrainHeightfield.elevationAt(vx, vz) + 2.2f
      staticBoxes += PropBoxSpec(vx, vy, vz, 14f, 0.65f, 5.2f, floatArrayOf(0.68f, 0.70f, 0.72f), MaterialSurface.ASPHALT, yawDeg = (ang * 180f / PI.toFloat()) + 90f)
      staticCylinders += PropCylinderSpec(vx, vy - 1.4f, vz, 0.75f, 3.2f, darkSteel, MaterialSurface.SLATE)
    }
  }

  private fun buildMuseum(
    cx: Float,
    cz: Float,
    staticBoxes: MutableList<PropBoxSpec>,
    staticCylinders: MutableList<PropCylinderSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
    gabledRoofs: MutableList<GabledRoofSpec>,
  ) {
    val y0 = TerrainHeightfield.elevationAt(cx, cz) - 0.10f
    val marble = floatArrayOf(0.95f, 0.94f, 0.90f)
    val goldRoof = floatArrayOf(0.92f, 0.76f, 0.24f)

    // Wide bright cream Museum Forecourt Plaza
    staticBoxes += PropBoxSpec(cx, y0 + 0.24f, cz, 24f, 0.48f, 20f, marble, MaterialSurface.TILE, castShadow = false)
    walkableFloors += WalkableFloorSpec(cx - 12f, cz - 10f, cx + 12f, cz + 10f, y0 + 0.48f, tag = "Museum Plaza")

    // Central Golden Museum Hall + Flanking Wings
    staticBoxes += PropBoxSpec(cx, y0 + 3.0f, cz, 11.5f, 5.2f, 10.5f, marble, MaterialSurface.STUCCO)
    aabbs += AabbCollider(cx - 5.8f, cz - 5.3f, cx + 5.8f, cz + 5.3f, y0, y0 + 5.8f, "Museum Main Hall")
    gabledRoofs += GabledRoofSpec(cx, y0 + 5.6f, cz, width = 12.6f, height = 2.6f, length = 11.4f, color = goldRoof, surface = MaterialSurface.SLATE)

    // Colonnade Pillars across East & South Facade
    for (pz in listOf(-4.2f, -1.4f, 1.4f, 4.2f)) {
      staticCylinders += PropCylinderSpec(cx + 6.4f, y0 + 2.6f, cz + pz, 0.42f, 4.6f, marble, MaterialSurface.STUCCO)
    }
    // North Exhibition Wing
    staticBoxes += PropBoxSpec(cx - 2.0f, y0 + 2.1f, cz - 8.2f, 10.5f, 3.6f, 4.8f, goldRoof, MaterialSurface.STUCCO)
    aabbs += AabbCollider(cx - 7.3f, cz - 10.6f, cx + 3.3f, cz - 5.8f, y0, y0 + 4.0f, "Museum North Wing")
  }

  private fun buildTurbineHeadland(
    staticCylinders: MutableList<PropCylinderSpec>,
    circles: MutableList<CircleCollider>,
    windTurbines: MutableList<WindTurbineSpec>,
  ) {
    val specs =
      listOf(
        Triple(-224f, -12f, 22f),
        Triple(-228f, 14f, 34f),
        Triple(-206f, 28f, 12f),
        Triple(-208f, -24f, -14f),
      )
    for ((tx, tz, yaw) in specs) {
      val ty = TerrainHeightfield.elevationAt(tx, tz)
      staticCylinders += PropCylinderSpec(tx, ty + 0.35f, tz, 2.2f, 0.7f, floatArrayOf(0.54f, 0.56f, 0.58f), MaterialSurface.SLATE)
      windTurbines += WindTurbineSpec(tx, ty + 0.5f, tz, towerHeight = 17.5f, bladeRadius = 7.2f, yawDeg = yaw)
      circles += CircleCollider(tx, tz, 1.3f, "Turbine Mast")
    }
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
    val barnRed = floatArrayOf(0.72f, 0.24f, 0.18f)
    val barnGold = floatArrayOf(0.92f, 0.74f, 0.26f)
    val trimWhite = floatArrayOf(0.94f, 0.92f, 0.88f)
    val hayGold = floatArrayOf(0.88f, 0.74f, 0.28f)
    val siloMetal = floatArrayOf(0.78f, 0.82f, 0.86f)

    // Central Barn Plinth + Walkable Floor
    staticBoxes += PropBoxSpec(cx, y0 + 0.18f, cz, 12.6f, 0.44f, 14.0f, floatArrayOf(0.56f, 0.48f, 0.38f), MaterialSurface.TILE, castShadow = false)
    walkableFloors += WalkableFloorSpec(cx - 6.2f, cz - 7.0f, cx + 6.2f, cz + 7.0f, y0 + 0.40f, tag = "Farmtopia Barn")

    // Walk-in Barn Walls (open South + North barn gates!)
    staticBoxes += PropBoxSpec(cx - 5.2f, y0 + 2.4f, cz, 0.48f, 4.0f, 12.8f, barnRed, MaterialSurface.WOOD)
    staticBoxes += PropBoxSpec(cx + 5.2f, y0 + 2.4f, cz, 0.48f, 4.0f, 12.8f, barnRed, MaterialSurface.WOOD)
    staticBoxes += PropBoxSpec(cx - 3.6f, y0 + 2.4f, cz + 6.2f, 3.6f, 4.0f, 0.48f, barnRed, MaterialSurface.WOOD)
    staticBoxes += PropBoxSpec(cx + 3.6f, y0 + 2.4f, cz + 6.2f, 3.6f, 4.0f, 0.48f, barnRed, MaterialSurface.WOOD)
    staticBoxes += PropBoxSpec(cx - 3.6f, y0 + 2.4f, cz - 6.2f, 3.6f, 4.0f, 0.48f, barnRed, MaterialSurface.WOOD)
    staticBoxes += PropBoxSpec(cx + 3.6f, y0 + 2.4f, cz - 6.2f, 3.6f, 4.0f, 0.48f, barnRed, MaterialSurface.WOOD)
    staticBoxes += PropBoxSpec(cx, y0 + 4.35f, cz + 6.3f, 11.0f, 0.30f, 0.24f, trimWhite, MaterialSurface.WOOD)

    aabbs += AabbCollider(cx - 5.5f, cz - 6.5f, cx - 4.9f, cz + 6.5f, y0, y0 + 4.5f, "Barn West Wall")
    aabbs += AabbCollider(cx + 4.9f, cz - 6.5f, cx + 5.5f, cz + 6.5f, y0, y0 + 4.5f, "Barn East Wall")
    aabbs += AabbCollider(cx - 5.5f, cz + 5.9f, cx - 1.7f, cz + 6.5f, y0, y0 + 4.5f, "Barn Front L")
    aabbs += AabbCollider(cx + 1.7f, cz + 5.9f, cx + 5.5f, cz + 6.5f, y0, y0 + 4.5f, "Barn Front R")
    aabbs += AabbCollider(cx - 5.5f, cz - 6.5f, cx - 1.7f, cz - 5.9f, y0, y0 + 4.5f, "Barn Back L")
    aabbs += AabbCollider(cx + 1.7f, cz - 6.5f, cx + 5.5f, cz - 5.9f, y0, y0 + 4.5f, "Barn Back R")

    gabledRoofs += GabledRoofSpec(cx, y0 + 4.4f, cz, width = 12.2f, height = 3.2f, length = 13.6f, color = barnGold, surface = MaterialSurface.WOOD)

    // Tall Cylindrical Grain Silo
    val sx = cx + 8.0f
    val sz = cz - 3.8f
    val sy = TerrainHeightfield.elevationAt(sx, sz) - 0.1f
    staticCylinders += PropCylinderSpec(sx, sy + 4.4f, sz, 2.0f, 8.8f, siloMetal, MaterialSurface.SLATE, metallic = 0.72f, roughness = 0.28f)
    staticCylinders += PropCylinderSpec(sx, sy + 9.0f, sz, 2.15f, 0.6f, barnRed, MaterialSurface.SLATE)
    circles += CircleCollider(sx, sz, 2.1f, "Farmtopia Silo")

    // Hay Bales around barn for cover
    for ((hx, hz) in listOf(-2.8f to -2.0f, 2.8f to 2.2f, -8.2f to 4.2f, 8.2f to 4.5f)) {
      val by = TerrainHeightfield.elevationAt(cx + hx, cz + hz) + 0.55f
      staticBoxes += PropBoxSpec(cx + hx, by, cz + hz, 1.7f, 1.1f, 1.15f, hayGold, MaterialSurface.GRASS)
      aabbs += AabbCollider(cx + hx - 0.9f, cz + hz - 0.6f, cx + hx + 0.9f, cz + hz + 0.6f, by - 0.6f, by + 0.6f, "Hay Bale")
    }
  }

  private fun buildGravLabs(
    cx: Float,
    cz: Float,
    staticBoxes: MutableList<PropBoxSpec>,
    staticCylinders: MutableList<PropCylinderSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
    domes: MutableList<DomeSpec>,
  ) {
    val y0 = TerrainHeightfield.elevationAt(cx, cz) - 0.10f
    val gravGold = floatArrayOf(0.92f, 0.76f, 0.24f)
    val darkSlate = floatArrayOf(0.24f, 0.28f, 0.34f)
    val glowCyan = floatArrayOf(0.22f, 0.88f, 0.98f)

    // Huge Iconic Circular Concrete Ring (matching SW corner of fmap.png!)
    staticCylinders += PropCylinderSpec(cx, y0 + 0.55f, cz, 19.5f, 1.1f, gravGold, MaterialSurface.STUCCO, roughness = 0.42f)
    staticCylinders += PropCylinderSpec(cx, y0 + 1.20f, cz, 11.2f, 0.7f, darkSlate, MaterialSurface.SLATE)
    walkableFloors += WalkableFloorSpec(cx - 19f, cz - 19f, cx + 19f, cz + 19f, y0 + 1.10f, tag = "Grav Labs Ring")

    // Central Futuristic Geodesic Gravity Dome
    domes += DomeSpec(cx, y0 + 1.5f, cz, radiusX = 9.4f, height = 6.8f, radiusZ = 9.4f, color = glowCyan)
    // Flanking Golden Reactor Columns
    for (i in 0 until 8) {
      val a = i * (PI.toFloat() / 4f)
      val px = cx + cos(a) * 14.5f
      val pz = cz + sin(a) * 14.5f
      staticBoxes += PropBoxSpec(px, y0 + 3.2f, pz, 2.2f, 4.4f, 2.2f, gravGold, MaterialSurface.SLATE)
      aabbs += AabbCollider(px - 1.1f, pz - 1.1f, px + 1.1f, pz + 1.1f, y0, y0 + 5.5f, "Grav Labs Pylon")
    }

    // East Circular Launch Pad Dome Annex (twin circle right of main ring on fmap.png!)
    val ex = cx + 22f
    val ez = cz + 2f
    val ey = TerrainHeightfield.elevationAt(ex, ez) - 0.10f
    staticCylinders += PropCylinderSpec(ex, ey + 0.50f, ez, 9.6f, 1.0f, gravGold, MaterialSurface.STUCCO)
    walkableFloors += WalkableFloorSpec(ex - 9.2f, ez - 9.2f, ex + 9.2f, ez + 9.2f, ey + 1.0f, tag = "Grav Labs Annex Pad")
    domes += DomeSpec(ex, ey + 1.0f, ez, radiusX = 6.4f, height = 4.2f, radiusZ = 6.4f, color = floatArrayOf(0.96f, 0.66f, 0.22f))
  }

  private fun buildPlazaria(
    cx: Float,
    cz: Float,
    staticBoxes: MutableList<PropBoxSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
    gabledRoofs: MutableList<GabledRoofSpec>,
  ) {
    val y0 = TerrainHeightfield.elevationAt(cx, cz) - 0.08f
    val cream = floatArrayOf(0.94f, 0.89f, 0.78f)
    val gold = floatArrayOf(0.92f, 0.74f, 0.26f)
    staticBoxes += PropBoxSpec(cx, y0 + 0.20f, cz, 22f, 0.40f, 16f, cream, MaterialSurface.TILE, yawDeg = 18f, castShadow = false)
    walkableFloors += WalkableFloorSpec(cx - 11f, cz - 8f, cx + 11f, cz + 8f, y0 + 0.40f, tag = "Plazaria Courtyard")

    for ((ox, oz, w, d, h) in listOf(
      arrayOf(-6.8f, 1.2f, 6.4f, 9.2f, 4.4f),
      arrayOf(6.8f, -1.2f, 6.4f, 9.2f, 4.4f),
      arrayOf(0f, -4.8f, 8.2f, 5.4f, 5.2f),
    )) {
      staticBoxes += PropBoxSpec(cx + ox, y0 + h * 0.5f, cz + oz, w, h, d, cream, MaterialSurface.STUCCO)
      aabbs += AabbCollider(cx + ox - w * 0.5f, cz + oz - d * 0.5f, cx + ox + w * 0.5f, cz + oz + d * 0.5f, y0, y0 + h, "Plazaria Hall")
      gabledRoofs += GabledRoofSpec(cx + ox, y0 + h, cz + oz, w + 0.8f, 1.8f, d + 0.8f, color = gold, surface = MaterialSurface.TILE)
    }
  }

  private fun buildDecaSquare(
    cx: Float,
    cz: Float,
    staticBoxes: MutableList<PropBoxSpec>,
    staticCylinders: MutableList<PropCylinderSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
    gabledRoofs: MutableList<GabledRoofSpec>,
  ) {
    val y0 = TerrainHeightfield.elevationAt(cx, cz) - 0.10f
    val rosePink = floatArrayOf(0.88f, 0.54f, 0.74f)
    val goldRoof = floatArrayOf(0.94f, 0.76f, 0.24f)
    val marble = floatArrayOf(0.93f, 0.90f, 0.88f)

    // Iconic Pink / Rose Mosaic Plaza Floor (matches pink zone in Row O-P Col E on fmap.png!)
    staticBoxes += PropBoxSpec(cx, y0 + 0.22f, cz, 28f, 0.44f, 24f, rosePink, MaterialSurface.TILE, yawDeg = 22f, castShadow = false)
    walkableFloors += WalkableFloorSpec(cx - 14f, cz - 12f, cx + 14f, cz + 12f, y0 + 0.44f, tag = "Deca Square Plaza")

    // Central Golden Polygonal Pavilion
    staticCylinders += PropCylinderSpec(cx, y0 + 2.4f, cz, 5.2f, 4.0f, marble, MaterialSurface.STUCCO)
    staticCylinders += PropCylinderSpec(cx, y0 + 4.7f, cz, 6.2f, 0.9f, goldRoof, MaterialSurface.SLATE)
    aabbs += AabbCollider(cx - 4.5f, cz - 4.5f, cx + 4.5f, cz + 4.5f, y0, y0 + 5.2f, "Deca Square Pavilion")

    // Flanking Commercial Gallery Halls
    staticBoxes += PropBoxSpec(cx + 10.5f, y0 + 2.5f, cz + 5.5f, 7.6f, 4.8f, 10.5f, marble, MaterialSurface.STUCCO, yawDeg = 32f)
    aabbs += AabbCollider(cx + 6.5f, cz + 0.5f, cx + 14.5f, cz + 10.5f, y0, y0 + 5.0f, "Deca East Block")
    gabledRoofs += GabledRoofSpec(cx + 10.5f, y0 + 4.9f, cz + 5.5f, 8.2f, 2.0f, 11.0f, yawDeg = 32f, color = goldRoof, surface = MaterialSurface.TILE)
  }

  private fun buildMudSite(
    cx: Float,
    cz: Float,
    staticBoxes: MutableList<PropBoxSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
  ) {
    val y0 = TerrainHeightfield.elevationAt(cx, cz) - 0.10f
    val concrete = floatArrayOf(0.70f, 0.68f, 0.62f)
    val ochre = floatArrayOf(0.84f, 0.72f, 0.34f)
    staticBoxes += PropBoxSpec(cx, y0 + 0.20f, cz, 18f, 0.40f, 14f, concrete, MaterialSurface.SLATE, yawDeg = -34f, castShadow = false)
    walkableFloors += WalkableFloorSpec(cx - 9f, cz - 7f, cx + 9f, cz + 7f, y0 + 0.40f, tag = "Mud Site Pad")

    for ((ox, oz, w, h, d) in listOf(
      floatArrayOf(-4.5f, -2.5f, 6.4f, 3.6f, 5.2f),
      floatArrayOf(4.8f, 2.2f, 6.2f, 3.2f, 5.6f),
    )) {
      staticBoxes += PropBoxSpec(cx + ox, y0 + h * 0.5f, cz + oz, w, h, d, ochre, MaterialSurface.STUCCO)
      aabbs += AabbCollider(cx + ox - w * 0.5f, cz + oz - d * 0.5f, cx + ox + w * 0.5f, cz + oz + d * 0.5f, y0, y0 + h, "Mud Site Structure")
    }
  }

  private fun buildZipwayAndZiplines(
    cx: Float,
    cz: Float,
    staticBoxes: MutableList<PropBoxSpec>,
    staticCylinders: MutableList<PropCylinderSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
  ) {
    val concrete = floatArrayOf(0.68f, 0.70f, 0.72f)
    val darkSteel = floatArrayOf(0.24f, 0.26f, 0.30f)
    val cableGold = floatArrayOf(0.98f, 0.80f, 0.22f)

    // Twin Curved Elevated Highway Ramp Segments in Zipway (Col F, Row M-N)
    val segments =
      listOf(
        Triple(cx - 10f, cz - 14f, 16f),
        Triple(cx - 5f, cz, 4f),
        Triple(cx - 8f, cz + 14f, -14f),
        Triple(cx + 12f, cz + 20f, 72f),
      )
    for ((sx, sz, yaw) in segments) {
      val sy = TerrainHeightfield.elevationAt(sx, sz)
      staticBoxes += PropBoxSpec(sx, sy + 2.6f, sz, 5.8f, 0.55f, 14f, concrete, MaterialSurface.ASPHALT, yawDeg = yaw)
      staticCylinders += PropCylinderSpec(sx, sy + 1.25f, sz, 0.75f, 2.6f, darkSteel, MaterialSurface.SLATE)
      walkableFloors += WalkableFloorSpec(sx - 3.0f, sz - 6.8f, sx + 3.0f, sz + 6.8f, sy + 2.88f, tag = "Zipway Overpass")
    }

    // Yellow Zipline Pylons & Overhead Cables spanning Nextera POIs
    val ziplines =
      listOf(
        Pair(Pair(cx + 6f, cz - 6f), Pair(164f, -44f)),     // Zipway -> Mortar Ruins
        Pair(Pair(-140f, 126f), Pair(-82f, 108f)),          // Grav Labs -> Plazaria Hills
        Pair(Pair(-96f, -52f), Pair(-116f, -124f)),         // Farmtopia -> Intellect Moat
      )
    for ((a, b) in ziplines) {
      val ya = TerrainHeightfield.elevationAt(a.first, a.second)
      val yb = TerrainHeightfield.elevationAt(b.first, b.second)
      staticCylinders += PropCylinderSpec(a.first, ya + 3.2f, a.second, 0.32f, 6.4f, cableGold, MaterialSurface.SLATE)
      staticCylinders += PropCylinderSpec(b.first, yb + 3.2f, b.second, 0.32f, 6.4f, cableGold, MaterialSurface.SLATE)
      val mx = (a.first + b.first) * 0.5f
      val mz = (a.second + b.second) * 0.5f
      val my = (ya + yb) * 0.5f + 5.8f
      val len = hypot(b.first - a.first, b.second - a.second)
      val yawDeg = (kotlin.math.atan2(b.first - a.first, b.second - a.second) * 180.0 / PI).toFloat()
      staticBoxes += PropBoxSpec(mx, my, mz, 0.14f, 0.14f, len, cableGold, MaterialSurface.EMISSIVE, yawDeg = yawDeg, emissive = 0.55f, castShadow = false)
    }
  }

  private fun buildTwinBridgeOverpass(
    cx: Float,
    cz: Float,
    staticBoxes: MutableList<PropBoxSpec>,
    staticCylinders: MutableList<PropCylinderSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
  ) {
    val concrete = floatArrayOf(0.72f, 0.72f, 0.70f)
    val darkSteel = floatArrayOf(0.24f, 0.26f, 0.30f)
    // Parallel Twin Highway Spans curving NE
    for (offset in listOf(-4.2f, 4.2f)) {
      val sx = cx + offset * 0.6f
      val sz = cz - offset * 0.8f
      val sy = TerrainHeightfield.elevationAt(sx, sz)
      staticBoxes += PropBoxSpec(sx, sy + 2.8f, sz, 4.4f, 0.55f, 34f, concrete, MaterialSurface.ASPHALT, yawDeg = -52f)
      staticCylinders += PropCylinderSpec(sx - 6f, sy + 1.4f, sz + 5f, 0.75f, 2.8f, darkSteel, MaterialSurface.SLATE)
      staticCylinders += PropCylinderSpec(sx + 6f, sy + 1.4f, sz - 5f, 0.75f, 2.8f, darkSteel, MaterialSurface.SLATE)
      walkableFloors += WalkableFloorSpec(sx - 12f, sz - 12f, sx + 12f, sz + 12f, sy + 3.08f, tag = "Twin Bridge Deck")
    }
    aabbs += AabbCollider(cx - 2f, cz - 2f, cx + 2f, cz + 2f, 0f, 3.0f, "Twin Bridge Pier")
  }

  private fun buildMortarRuins(
    cx: Float,
    cz: Float,
    staticBoxes: MutableList<PropBoxSpec>,
    staticCylinders: MutableList<PropCylinderSpec>,
    aabbs: MutableList<AabbCollider>,
  ) {
    val sandStone = floatArrayOf(0.68f, 0.59f, 0.45f)
    val ruinGray = floatArrayOf(0.48f, 0.47f, 0.45f)

    // Iconic Giant Fallen/Tilted White-Gray Silo Cylinder near Mortar Ruins (G-L/M on fmap.png)
    val sx = cx - 8f
    val sz = cz + 10f
    val sy = TerrainHeightfield.elevationAt(sx, sz)
    staticBoxes += PropBoxSpec(sx, sy + 1.8f, sz, 4.2f, 3.8f, 11.5f, floatArrayOf(0.84f, 0.84f, 0.82f), MaterialSurface.STUCCO, yawDeg = 38f)
    aabbs += AabbCollider(sx - 4.2f, sz - 4.8f, sx + 4.2f, sz + 4.8f, sy, sy + 3.8f, "Mortar Fallen Silo")

    // Curved Concrete Amphitheater / Coastal Defense Tiers on NE bluff
    for (tier in 0..2) {
      val bx = cx + 22f + tier * 2.4f
      val bz = cz - 14f - tier * 1.4f
      val by = TerrainHeightfield.elevationAt(bx, bz)
      staticBoxes += PropBoxSpec(bx, by + 1.4f, bz, 3.2f, 2.8f, 14f, ruinGray, MaterialSurface.SLATE, yawDeg = -32f)
    }

    // Weathered Crater Cover Walls
    for (i in 0 until 6) {
      val a = i * (2f * PI.toFloat() / 6f) + 0.3f
      val rx = cx + cos(a) * 11.5f
      val rz = cz + sin(a) * 11.5f
      val ry = TerrainHeightfield.elevationAt(rx, rz)
      staticBoxes += PropBoxSpec(rx, ry + 0.9f, rz, 4.4f, 1.8f, 0.85f, sandStone, MaterialSurface.STUCCO, yawDeg = (a * 180f / PI.toFloat()) + 90f)
      aabbs += AabbCollider(rx - 2.1f, rz - 2.1f, rx + 2.1f, rz + 2.1f, ry, ry + 1.9f, "Mortar Ruin Cover")
    }
  }

  private fun buildRustTown(
    cx: Float,
    cz: Float,
    staticBoxes: MutableList<PropBoxSpec>,
    staticCylinders: MutableList<PropCylinderSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
    gabledRoofs: MutableList<GabledRoofSpec>,
  ) {
    val y0 = TerrainHeightfield.elevationAt(cx, cz) - 0.10f
    val rustWall = floatArrayOf(0.56f, 0.38f, 0.28f)
    val steelBlue = floatArrayOf(0.24f, 0.44f, 0.62f)
    val craneYellow = floatArrayOf(0.95f, 0.74f, 0.16f)
    val concreteViaduct = floatArrayOf(0.62f, 0.60f, 0.56f)

    // Corrugated Industrial Hangar
    staticBoxes += PropBoxSpec(cx - 6f, y0 + 2.4f, cz - 8f, 12f, 4.8f, 0.45f, rustWall, MaterialSurface.SLATE, metallic = 0.45f)
    staticBoxes += PropBoxSpec(cx - 6f, y0 + 2.4f, cz + 2f, 12f, 4.8f, 0.45f, rustWall, MaterialSurface.SLATE, metallic = 0.45f)
    aabbs += AabbCollider(cx - 12f, cz - 8.3f, cx, cz - 7.7f, y0, y0 + 5f, "Rust Hangar Wall")
    aabbs += AabbCollider(cx - 12f, cz + 1.7f, cx, cz + 2.3f, y0, y0 + 5f, "Rust Hangar Wall")
    gabledRoofs += GabledRoofSpec(cx - 6f, y0 + 4.8f, cz - 3f, 13f, 2.4f, 11f, color = rustWall, surface = MaterialSurface.SLATE)

    // Climbable Stacked Shipping Containers
    for ((posYaw, col) in listOf(
      Pair(Triple(8f, -10f, 12f), steelBlue),
      Pair(Triple(8f, -18f, -8f), floatArrayOf(0.84f, 0.32f, 0.22f)),
      Pair(Triple(12f, 12f, 45f), floatArrayOf(0.24f, 0.62f, 0.42f)),
    )) {
      val (ox, oz, yaw) = posYaw
      val cy = TerrainHeightfield.elevationAt(cx + ox, cz + oz)
      staticBoxes += PropBoxSpec(cx + ox, cy + 1.35f, cz + oz, 2.6f, 2.7f, 6.2f, col, MaterialSurface.SLATE, yawDeg = yaw, metallic = 0.35f)
      aabbs += AabbCollider(cx + ox - 2.4f, cz + oz - 2.6f, cx + ox + 2.4f, cz + oz + 2.6f, cy, cy + 2.7f, "Rust Container")
      walkableFloors += WalkableFloorSpec(cx + ox - 2.2f, cz + oz - 2.5f, cx + ox + 2.2f, cz + oz + 2.5f, cy + 2.7f, tag = "Shipping Container")
    }

    // 2 Tall Yellow Construction Tower Cranes (matching Rust Town yellow crane booms on fmap.png!)
    for ((kx, kz, boomYaw) in listOf(Triple(cx + 6f, cz - 2f, -80f), Triple(cx - 4f, cz + 18f, 15f))) {
      val ky = TerrainHeightfield.elevationAt(kx, kz)
      staticBoxes += PropBoxSpec(kx, ky + 7.5f, kz, 1.3f, 15f, 1.3f, craneYellow, MaterialSurface.SLATE)
      staticBoxes += PropBoxSpec(kx, ky + 14.8f, kz, 1.1f, 1.0f, 24f, craneYellow, MaterialSurface.SLATE, yawDeg = boomYaw)
      aabbs += AabbCollider(kx - 0.8f, kz - 0.8f, kx + 0.8f, kz + 0.8f, ky, ky + 15f, "Tower Crane")
    }

    // Curved Outer Concrete Viaduct Rim on Far East Coast
    for (step in -3..3) {
      val ang = step * 0.28f
      val vx = cx + 22f * cos(ang)
      val vz = cz + 28f * sin(ang)
      val vy = TerrainHeightfield.elevationAt(vx, vz)
      staticBoxes += PropBoxSpec(vx, vy + 2.2f, vz, 3.8f, 0.75f, 11f, concreteViaduct, MaterialSurface.ASPHALT, yawDeg = (ang * 180f / PI.toFloat()))
      staticCylinders += PropCylinderSpec(vx, vy + 1.1f, vz, 0.65f, 2.2f, concreteViaduct, MaterialSurface.SLATE)
    }
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
    val y0 = TerrainHeightfield.elevationAt(cx, cz) - 0.08f
    val timber = floatArrayOf(0.42f, 0.28f, 0.16f)
    val plank = floatArrayOf(0.62f, 0.44f, 0.26f)
    val roofCol = floatArrayOf(0.24f, 0.36f, 0.28f)
    val deckY = y0 + 3.6f

    for (px in listOf(-2.0f, 2.0f)) {
      for (pz in listOf(-2.0f, 2.0f)) {
        staticCylinders += PropCylinderSpec(cx + px, y0 + 3.1f, cz + pz, 0.18f, 6.2f, timber, MaterialSurface.WOOD)
      }
    }
    staticBoxes += PropBoxSpec(cx, deckY, cz, 4.8f, 0.28f, 4.8f, plank, MaterialSurface.WOOD)
    walkableFloors += WalkableFloorSpec(cx - 2.4f, cz - 2.4f, cx + 2.4f, cz + 2.4f, deckY + 0.14f, tag = "Sniper Watchtower")

    for ((rx, rz, sx, sz) in listOf(
      arrayOf(0f, -2.25f, 4.8f, 0.22f),
      arrayOf(-2.25f, 0f, 0.22f, 4.8f),
      arrayOf(2.25f, 0f, 0.22f, 4.8f),
    )) {
      staticBoxes += PropBoxSpec(cx + rx, deckY + 0.65f, cz + rz, sx, 0.95f, sz, timber, MaterialSurface.WOOD)
      aabbs += AabbCollider(cx + rx - sx * 0.5f, cz + rz - sz * 0.5f, cx + rx + sx * 0.5f, cz + rz + sz * 0.5f, deckY + 0.14f, deckY + 1.15f, "Tower Guardrail")
    }

    staticBoxes += PropBoxSpec(cx, y0 + 1.8f, cz + 3.8f, 1.55f, 0.22f, 4.2f, plank, MaterialSurface.WOOD)
    walkableFloors +=
      WalkableFloorSpec(
        minX = cx - 0.85f,
        minZ = cz + 2.2f,
        maxX = cx + 0.85f,
        maxZ = cz + 5.8f,
        baseElevationY = deckY + 0.14f,
        endElevationY = y0 + 0.10f,
        rampAxis = RampAxis.POS_Z,
        tag = "Watchtower Ramp",
      )
    gabledRoofs += GabledRoofSpec(cx, deckY + 2.65f, cz, width = 5.4f, height = 1.85f, length = 5.4f, color = roofCol, surface = MaterialSurface.WOOD)
  }

  private fun populateMixedForestAndBoulders(
    houses: List<HouseSpec>,
    trees: MutableList<TreeSpec>,
    circles: MutableList<CircleCollider>,
    boulders: MutableList<BoulderSpec>,
  ) {
    fun isClearOfRoadsAndLots(tx: Float, tz: Float, minDist: Float): Boolean {
      if (TerrainHeightfield.signedCoastDistance(tx, tz) > -4.5f) return false
      for (seg in TerrainHeightfield.roadSegments) {
        val dx = seg.x2 - seg.x1
        val dz = seg.z2 - seg.z1
        val lenSq = dx * dx + dz * dz
        val t = if (lenSq < 1e-4f) 0f else (((tx - seg.x1) * dx + (tz - seg.z1) * dz) / lenSq).coerceIn(0f, 1f)
        val d = hypot(tx - (seg.x1 + t * dx), tz - (seg.z1 + t * dz))
        if (d < seg.halfWidth + minDist) return false
      }
      for (h in houses) {
        if (abs(tx - h.centerX) < h.width * 0.65f + 5f && abs(tz - h.centerZ) < h.depth * 0.65f + 5f) return false
      }
      // Keep Farmtopia barn/market/gun shop/Police Station open
      if (hypot(tx + 72f, tz - 6f) < 15f || hypot(tx + 50f, tz - 24f) < 12f || hypot(tx + 46f, tz + 14f) < 10f) return false
      if (hypot(tx + 12f, tz - 44f) < 22f) return false
      if (hypot(tx + 178f, tz + 80f) < 16f || hypot(tx + 158f, tz - 168f) < 22f || hypot(tx - 24f, tz - 172f) < 18f) return false
      if (hypot(tx + 18f, tz + 180f) < 22f) return false
      return true
    }

    // 1. Mixed Nextera Trees (Pine Conifers, Cypress/Acacia, Palm & Tropical Shade)
    var gx = -225f
    var idx = 0
    while (gx <= 225f) {
      var gz = -225f
      while (gz <= 225f) {
        val jx = gx + (((idx * 37) % 19) - 9).toFloat() * 0.85f
        val jz = gz + (((idx * 53) % 19) - 9).toFloat() * 0.85f
        idx++
        if (isClearOfRoadsAndLots(jx, jz, 3.8f)) {
          val coast = TerrainHeightfield.signedCoastDistance(jx, jz)
          val kind =
            when {
              coast > -13f -> TreeKind.PALM
              jx > 105f && jz in -125f..90f -> TreeKind.CYPRESS_ACACIA // Dry Mortar Ruins / Rust Town
              jz < -100f || jx < -190f -> TreeKind.PINE_CONIFER       // Intellect & Turbine highlands
              idx % 3 == 0 -> TreeKind.CYPRESS_ACACIA
              idx % 2 == 0 -> TreeKind.PINE_CONIFER
              else -> TreeKind.TROPICAL_SHADE
            }
          val h =
            when (kind) {
              TreeKind.PINE_CONIFER -> 7.6f + (idx % 4) * 1.1f
              TreeKind.CYPRESS_ACACIA -> 6.4f + (idx % 3) * 0.9f
              TreeKind.PALM -> 6.8f + (idx % 3) * 0.8f
              TreeKind.TROPICAL_SHADE -> 6.2f + (idx % 3) * 0.8f
            }
          trees += TreeSpec(jx, jz, h, ((idx * 47) % 360).toFloat(), kind, tintShift = ((idx % 5) - 2) * 0.05f)
          circles += CircleCollider(jx, jz, 0.72f, "Nextera Tree")
        }
        gz += 26f
      }
      gx += 26f
    }

    // 2. Angular Rocky Boulders scattered across Turbine Bluffs, Mortar Ruins, Intellect Hills & Coast
    val boulderSpots =
      listOf(
        Triple(-224f, -26f, 2.5f),
        Triple(-230f, 4f, 2.9f),
        Triple(164f, -84f, 2.7f),
        Triple(196f, -52f, 3.1f),
        Triple(142f, -158f, 3.2f),
        Triple(-56f, -212f, 2.8f),
        Triple(22f, -206f, 2.6f),
        Triple(-192f, 182f, 2.8f),
        Triple(178f, 134f, 2.5f),
        Triple(54f, -34f, 2.4f),
        Triple(-196f, -104f, 2.6f),
        Triple(138f, 168f, 2.4f),
      )
    for ((bi, spot) in boulderSpots.withIndex()) {
      val (bx, bz, scale) = spot
      val by = TerrainHeightfield.elevationAt(bx, bz) + scale * 0.25f
      val col =
        if (bx > 105f && bz in -128f..92f) floatArrayOf(0.64f, 0.54f, 0.41f)
        else floatArrayOf(0.48f, 0.50f, 0.52f)
      boulders +=
        BoulderSpec(
          x = bx,
          y = by,
          z = bz,
          rx = scale * (1.05f + (bi % 3) * 0.18f),
          ry = scale * (0.72f + (bi % 2) * 0.16f),
          rz = scale * (0.95f + ((bi + 1) % 3) * 0.15f),
          yawDeg = ((bi * 53) % 360).toFloat(),
          pitchDeg = ((bi % 3) - 1) * 8f,
          color = col,
        )
      circles += CircleCollider(bx, bz, scale * 0.85f, "Boulder Outcrop")
    }
  }
}
