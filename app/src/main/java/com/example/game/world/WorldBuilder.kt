package com.example.game.world

import com.example.game.math.AabbCollider

object WorldBuilder {
  fun buildKeyBayside(): WorldData = NexteraWorldBuilder.build()

  internal fun buildGunShopKiosk(
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

    // Paved tile forecourt & raised plinth leading toward road
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

  /**
   * Builds a large 2-story Nextera Police Station headquarters facing North (-Z toward Central Highway),
   * complete with stepped stone portico, glass lobby windows, glowing POLICE marquee, twin red & blue
   * rooftop beacons, walkable helipad roof with exterior stairs, and an East patrol cruiser bay.
   */
  internal fun buildPoliceStation(
    cx: Float,
    cz: Float,
    staticBoxes: MutableList<PropBoxSpec>,
    staticCylinders: MutableList<PropCylinderSpec>,
    glassBoxes: MutableList<PropBoxSpec>,
    aabbs: MutableList<AabbCollider>,
    walkableFloors: MutableList<WalkableFloorSpec>,
  ) {
    val stoneLight = floatArrayOf(0.86f, 0.88f, 0.92f)
    val precinctNavy = floatArrayOf(0.11f, 0.21f, 0.42f)
    val darkSlate = floatArrayOf(0.18f, 0.20f, 0.25f)
    val concretePlinth = floatArrayOf(0.36f, 0.38f, 0.43f)
    val policeBlueGlow = floatArrayOf(0.14f, 0.56f, 1.0f)
    val sirenRed = floatArrayOf(1.0f, 0.14f, 0.20f)
    val goldBadge = floatArrayOf(0.96f, 0.78f, 0.22f)
    val steelStair = floatArrayOf(0.46f, 0.50f, 0.56f)

    // 1. Wide Concrete Precinct Forecourt & Patrol Cruiser Parking Apron
    staticBoxes += PropBoxSpec(cx + 2.0f, 0.06f, cz - 1.5f, 34f, 0.12f, 24f, concretePlinth, MaterialSurface.ASPHALT, castShadow = false)
    walkableFloors += WalkableFloorSpec(cx - 15f, cz - 13.5f, cx + 19f, cz + 10.5f, 0.12f, tag = "Police Station Plaza")

    // East Patrol Cruiser Parking Bay Markings
    for (bx in listOf(cx + 11.4f, cx + 16.2f)) {
      staticBoxes += PropBoxSpec(bx, 0.125f, cz - 7.8f, 0.18f, 0.02f, 6.2f, policeBlueGlow, MaterialSurface.EMISSIVE, emissive = 0.55f, castShadow = false)
      staticBoxes += PropBoxSpec(bx + 2.3f, 0.125f, cz - 4.7f, 4.6f, 0.02f, 0.18f, goldBadge, MaterialSurface.EMISSIVE, emissive = 0.45f, castShadow = false)
    }

    // 2. Main 2-Story Precinct Headquarters Mass (20m wide x 12.6m deep x 7.4m tall, centered at cz + 3.5)
    val bCz = cz + 3.5f
    val bw = 20f
    val bd = 12.6f
    val hbw = bw * 0.5f
    val hbd = bd * 0.5f
    val wallH = 7.4f

    // Raised precinct podium floor + monumental North entry steps
    staticBoxes += PropBoxSpec(cx, 0.24f, bCz, bw + 1.2f, 0.24f, bd + 2.6f, stoneLight, MaterialSurface.TILE)
    walkableFloors += WalkableFloorSpec(cx - hbw - 0.6f, bCz - hbd - 1.3f, cx + hbw + 0.6f, bCz + hbd + 1.3f, 0.36f, tag = "Police Station Podium")
    staticBoxes += PropBoxSpec(cx, 0.18f, bCz - hbd - 1.85f, 9.2f, 0.14f, 1.4f, stoneLight, MaterialSurface.TILE)

    // Solid outer structural wings (leaving grand open recessed front vestibule in middle 5.4m facing North -Z)
    val leftWingW = 7.2f
    val rightWingW = 7.2f
    staticBoxes += PropBoxSpec(cx - hbw + leftWingW * 0.5f, 0.36f + wallH * 0.5f, bCz, leftWingW, wallH, bd, stoneLight, MaterialSurface.STUCCO)
    staticBoxes += PropBoxSpec(cx + hbw - rightWingW * 0.5f, 0.36f + wallH * 0.5f, bCz, rightWingW, wallH, bd, stoneLight, MaterialSurface.STUCCO)
    aabbs += AabbCollider(cx - hbw, bCz - hbd, cx - 2.8f, bCz + hbd, 0f, wallH + 0.6f, "Police HQ West Wing")
    aabbs += AabbCollider(cx + 2.8f, bCz - hbd, cx + hbw, bCz + hbd, 0f, wallH + 0.6f, "Police HQ East Wing")

    // Center Back Hall + Upper 2nd Story Bridge over Lobby Entryway (allows entering front desk lobby!)
    staticBoxes += PropBoxSpec(cx, 0.36f + wallH * 0.5f, bCz + 2.6f, 5.8f, wallH, bd - 5.2f, stoneLight, MaterialSurface.STUCCO)
    aabbs += AabbCollider(cx - 2.9f, bCz - 1.2f, cx + 2.9f, bCz + hbd, 0f, wallH + 0.6f, "Police HQ Center Core")
    staticBoxes += PropBoxSpec(cx, 3.8f + (wallH - 3.44f) * 0.5f, bCz - hbd + 2.6f, 5.8f, wallH - 3.44f, 5.2f, precinctNavy, MaterialSurface.STUCCO)

    // Front Desk Counter & Dispatch Screen inside Recessed Lobby
    staticBoxes += PropBoxSpec(cx, 0.92f, bCz - 2.2f, 4.4f, 1.12f, 0.85f, darkSlate, MaterialSurface.WOOD)
    aabbs += AabbCollider(cx - 2.2f, bCz - 2.65f, cx + 2.2f, bCz - 1.75f, 0f, 1.5f, "Police Dispatch Desk")
    staticBoxes += PropBoxSpec(cx, 2.25f, bCz - 1.32f, 3.4f, 1.05f, 0.14f, policeBlueGlow, MaterialSurface.EMISSIVE, emissive = 0.85f, castShadow = false)

    // Deep Navy Police Banding & Cornice Trim around 2nd Story
    staticBoxes += PropBoxSpec(cx, 4.15f, bCz - hbd - 0.08f, bw + 0.3f, 0.72f, 0.22f, precinctNavy, MaterialSurface.STUCCO)
    staticBoxes += PropBoxSpec(cx, 4.15f, bCz + hbd + 0.08f, bw + 0.3f, 0.72f, 0.22f, precinctNavy, MaterialSurface.STUCCO)
    staticBoxes += PropBoxSpec(cx - hbw - 0.08f, 4.15f, bCz, 0.22f, 0.72f, bd + 0.3f, precinctNavy, MaterialSurface.STUCCO)
    staticBoxes += PropBoxSpec(cx + hbw + 0.08f, 4.15f, bCz, 0.22f, 0.72f, bd + 0.3f, precinctNavy, MaterialSurface.STUCCO)

    // Illuminated Front & Side Glass Windows (Ground + 2F)
    for (wx in listOf(cx - 7.8f, cx - 4.8f, cx + 4.8f, cx + 7.8f)) {
      glassBoxes += PropBoxSpec(wx, 2.2f, bCz - hbd - 0.04f, 2.1f, 1.65f, 0.16f, floatArrayOf(0.45f, 0.78f, 0.98f), MaterialSurface.GLASS, castShadow = false)
      glassBoxes += PropBoxSpec(wx, 5.75f, bCz - hbd - 0.04f, 2.1f, 1.75f, 0.16f, floatArrayOf(0.45f, 0.78f, 0.98f), MaterialSurface.GLASS, castShadow = false)
    }

    // Grand Front Portico Columns & Canopy overhanging North entrance
    val porticoZ = bCz - hbd - 1.15f
    staticBoxes += PropBoxSpec(cx, 3.92f, porticoZ, 9.4f, 0.42f, 2.6f, precinctNavy, MaterialSurface.SLATE)
    for (px in listOf(cx - 4.1f, cx - 2.3f, cx + 2.3f, cx + 4.1f)) {
      staticCylinders += PropCylinderSpec(px, 2.14f, porticoZ - 0.95f, 0.26f, 3.55f, stoneLight, MaterialSurface.STUCCO)
    }

    // 3. Walkable Rooftop Helipad Deck & Parapet
    val roofY = 0.36f + wallH
    staticBoxes += PropBoxSpec(cx, roofY + 0.20f, bCz, bw + 0.8f, 0.40f, bd + 0.8f, darkSlate, MaterialSurface.SLATE)
    walkableFloors += WalkableFloorSpec(cx - hbw - 0.4f, bCz - hbd - 0.4f, cx + hbw + 0.4f, bCz + hbd + 0.4f, roofY + 0.40f, tag = "Police HQ Rooftop")

    // Helipad "H" & Gold Ring on Roof
    staticCylinders += PropCylinderSpec(cx, roofY + 0.41f, bCz + 0.8f, 3.8f, 0.04f, floatArrayOf(0.24f, 0.27f, 0.33f), MaterialSurface.ASPHALT)
    staticBoxes += PropBoxSpec(cx - 1.1f, roofY + 0.435f, bCz + 0.8f, 0.42f, 0.03f, 3.2f, goldBadge, MaterialSurface.EMISSIVE, emissive = 0.55f, castShadow = false)
    staticBoxes += PropBoxSpec(cx + 1.1f, roofY + 0.435f, bCz + 0.8f, 0.42f, 0.03f, 3.2f, goldBadge, MaterialSurface.EMISSIVE, emissive = 0.55f, castShadow = false)
    staticBoxes += PropBoxSpec(cx, roofY + 0.435f, bCz + 0.8f, 2.2f, 0.03f, 0.42f, goldBadge, MaterialSurface.EMISSIVE, emissive = 0.55f, castShadow = false)

    // Glowing "POLICE" Roof Sign Board + Red/Blue Roof Siren Beacons + Radio Mast
    staticBoxes += PropBoxSpec(cx, roofY + 1.25f, bCz - hbd + 0.35f, 10.6f, 1.35f, 0.42f, precinctNavy, MaterialSurface.SLATE)
    staticBoxes += PropBoxSpec(cx, roofY + 1.25f, bCz - hbd + 0.12f, 9.6f, 0.92f, 0.18f, policeBlueGlow, MaterialSurface.EMISSIVE, emissive = 1.15f, castShadow = false)
    staticBoxes += PropBoxSpec(cx - 4.4f, roofY + 2.20f, bCz - hbd + 0.35f, 1.25f, 0.52f, 0.52f, sirenRed, MaterialSurface.EMISSIVE, emissive = 1.45f, castShadow = false)
    staticBoxes += PropBoxSpec(cx + 4.4f, roofY + 2.20f, bCz - hbd + 0.35f, 1.25f, 0.52f, 0.52f, policeBlueGlow, MaterialSurface.EMISSIVE, emissive = 1.45f, castShadow = false)
    staticCylinders += PropCylinderSpec(cx + 6.8f, roofY + 2.4f, bCz + 3.2f, 0.14f, 4.2f, steelStair, MaterialSurface.SLATE, metallic = 0.7f)
    staticBoxes += PropBoxSpec(cx + 6.8f, roofY + 4.6f, bCz + 3.2f, 0.36f, 0.36f, 0.36f, sirenRed, MaterialSurface.EMISSIVE, emissive = 1.3f, castShadow = false)

    // 4. Exterior West Steel Staircase leading cleanly onto the Police HQ Rooftop
    val stairX = cx - hbw - 1.15f
    staticBoxes += PropBoxSpec(stairX, roofY * 0.5f + 0.18f, bCz, 2.0f, 0.24f, 10.2f, steelStair, MaterialSurface.SLATE, metallic = 0.5f)
    walkableFloors +=
      WalkableFloorSpec(
        minX = stairX - 1.0f,
        minZ = bCz - 5.1f,
        maxX = stairX + 1.0f,
        maxZ = bCz + 5.1f,
        baseElevationY = 0.36f,
        endElevationY = roofY + 0.40f,
        rampAxis = RampAxis.POS_Z,
        tag = "Police Roof Stairs",
      )
  }

  internal fun buildEnterableHouse(
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
  internal fun buildTwoStoryHouse(
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

    val stairFootZ = if (h.facingSouth) cz + hd - 2.3f else cz - hd + 2.3f
    val stairTopZ = if (h.facingSouth) cz - hd + 2.6f else cz + hd - 2.6f
    val stairZMin = kotlin.math.min(stairFootZ, stairTopZ)
    val stairZMax = kotlin.math.max(stairFootZ, stairTopZ)
    val rampAxis = if (h.facingSouth) RampAxis.NEG_Z else RampAxis.POS_Z

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

    val backLandDepth = kotlin.math.abs(backZ - stairTopZ) - wallT * 0.4f
    val backLandCz = (backZ + stairTopZ) * 0.5f
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
