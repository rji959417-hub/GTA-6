package com.example.game.world

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Continuous procedural 3D undulating heightfield sculpted to match the Nextera island layout:
 * - North Intellect Center island separated by a curved turquoise moat & 3 crossing bridges
 * - West Turbine rocky bluff island separated by a bay cove & Red Suspension Bridge
 * - Main organic island wrapping Museum, Farmtopia, Grav Labs, Plazaria, Deca Square,
 *   Mud Site, Zipway, Twin Bridge, Mortar Ruins, and Rust Town.
 */
object TerrainHeightfield {
  data class RoadSeg(val x1: Float, val z1: Float, val x2: Float, val z2: Float, val halfWidth: Float = 4.6f)

  /**
   * Shared road graph matching the Nextera map network so GL terrain shading, heightfield grading,
   * 3D asphalt ribbons, and the Compose minimap stay 100% aligned.
   */
  val roadSegments: List<RoadSeg> =
    listOf(
      // Outer West Coast: Museum -> Turbine Red Bridge -> Grav Labs -> South Coast
      RoadSeg(-178f, -80f, -170f, -38f, 4.6f),
      RoadSeg(-170f, -38f, -170f, 46f, 4.8f), // Turbine Red Bridge Corridor
      RoadSeg(-170f, 46f, -158f, 118f, 4.6f),
      RoadSeg(-158f, 118f, -156f, 166f, 4.8f),
      RoadSeg(-156f, 166f, -124f, 198f, 4.6f),
      RoadSeg(-124f, 198f, -66f, 198f, 4.6f),
      RoadSeg(-66f, 198f, 24f, 204f, 4.6f),
      RoadSeg(24f, 204f, 112f, 194f, 4.6f),
      RoadSeg(112f, 194f, 164f, 152f, 4.6f),
      // East Coast Loop: Mud Site -> Rust Town -> Mortar Ruins -> Twin Bridge
      RoadSeg(164f, 152f, 194f, 92f, 4.6f),
      RoadSeg(194f, 92f, 178f, 20f, 4.6f),
      RoadSeg(178f, 20f, 152f, -66f, 4.6f),
      RoadSeg(152f, -66f, 118f, -148f, 4.6f),
      // North Corridor: Twin Bridge -> Intellect East Bridge / North Hub -> Museum
      RoadSeg(118f, -148f, 66f, -148f, 4.6f),
      RoadSeg(66f, -148f, 14f, -122f, 4.6f),
      RoadSeg(14f, -122f, -16f, -84f, 4.6f),
      RoadSeg(-16f, -84f, -96f, -92f, 4.6f),
      RoadSeg(-96f, -92f, -178f, -80f, 4.6f),
      // Intellect Center 3 Moat Bridges + Inner Ring
      RoadSeg(-16f, -84f, -16f, -142f, 4.8f), // South Center Intellect Bridge
      RoadSeg(-96f, -92f, -58f, -142f, 4.6f), // SW Intellect Bridge
      RoadSeg(66f, -148f, 24f, -162f, 4.6f), // East Intellect Bridge
      // Interior Cross-Highways: Farmtopia, Zipway, Plazaria, Deca Square, Rust Town
      RoadSeg(-16f, -84f, -44f, -16f, 4.5f),
      RoadSeg(-44f, -16f, -44f, 52f, 4.5f),
      RoadSeg(-44f, 52f, -66f, 142f, 4.5f),
      RoadSeg(-66f, 142f, 24f, 172f, 4.5f),
      RoadSeg(-148f, 142f, -66f, 142f, 4.5f),
      RoadSeg(-142f, -64f, -118f, 16f, 4.4f),
      RoadSeg(-118f, 16f, -118f, 96f, 4.4f),
      RoadSeg(-118f, 96f, -66f, 142f, 4.4f),
      // Central E-W Highway: Farmtopia -> Zipway -> Rust Town -> Mortar Ruins
      RoadSeg(-44f, 16f, 58f, 22f, 4.5f),
      RoadSeg(58f, 22f, 152f, 22f, 4.5f),
      RoadSeg(58f, 22f, 128f, -72f, 4.4f),
      RoadSeg(128f, -72f, 152f, -66f, 4.4f),
      RoadSeg(24f, 172f, 112f, 128f, 4.4f),
      RoadSeg(112f, 128f, 164f, 152f, 4.4f),
      RoadSeg(112f, 128f, 118f, 22f, 4.4f),
    )

  private fun hash2(ix: Int, iz: Int): Float {
    var n = ix * 374761393 + iz * 668265263
    n = (n xor (n ushr 13)) * 1274126177
    val v = (n xor (n ushr 16)) and 0x7fffffff
    return v.toFloat() / 2147483647f
  }

  private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val span = edge1 - edge0
    if (abs(span) < 1e-5f) return if (x >= edge1) 1f else 0f
    val t = ((x - edge0) / span).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
  }

  private fun valueNoise(x: Float, z: Float): Float {
    val ix = floor(x).toInt()
    val iz = floor(z).toInt()
    val fx = x - ix
    val fz = z - iz
    val ux = fx * fx * (3f - 2f * fx)
    val uz = fz * fz * (3f - 2f * fz)
    val a = hash2(ix, iz)
    val b = hash2(ix + 1, iz)
    val c = hash2(ix, iz + 1)
    val d = hash2(ix + 1, iz + 1)
    val ab = a + (b - a) * ux
    val cd = c + (d - c) * ux
    return ab + (cd - ab) * uz
  }

  private fun fbm(x: Float, z: Float): Float {
    var sum = 0f
    var amp = 0.55f
    var freq = 1f
    for (i in 0 until 3) {
      sum += valueNoise(x * freq, z * freq) * amp
      freq *= 2.07f
      amp *= 0.48f
    }
    return sum
  }

  private fun ridged(x: Float, z: Float): Float {
    val n1 = abs(valueNoise(x, z) * 2f - 1f)
    val n2 = abs(valueNoise(x * 2.13f + 17.3f, z * 2.13f - 9.1f) * 2f - 1f)
    val r1 = 1f - n1
    val r2 = 1f - n2
    return r1 * r1 * 0.68f + r2 * r2 * 0.32f
  }

  private fun sdBox(px: Float, pz: Float, cx: Float, cz: Float, hx: Float, hz: Float): Float {
    val dx = abs(px - cx) - hx
    val dz = abs(pz - cz) - hz
    val ox = max(dx, 0f)
    val oz = max(dz, 0f)
    return hypot(ox, oz) + min(max(dx, dz), 0f)
  }

  private fun sdRoundBox(
    px: Float,
    pz: Float,
    cx: Float,
    cz: Float,
    hx: Float,
    hz: Float,
    radius: Float,
  ): Float = sdBox(px, pz, cx, cz, hx - radius, hz - radius) - radius

  private fun sdEllipse(px: Float, pz: Float, cx: Float, cz: Float, rx: Float, rz: Float): Float {
    val nx = (px - cx) / rx
    val nz = (pz - cz) / rz
    val k = hypot(nx, nz)
    return (k - 1f) * min(rx, rz)
  }

  private fun distToSegment(px: Float, pz: Float, x1: Float, z1: Float, x2: Float, z2: Float): Float {
    val dx = x2 - x1
    val dz = z2 - z1
    val lenSq = dx * dx + dz * dz
    if (lenSq < 1e-4f) return hypot(px - x1, pz - z1)
    val t = (((px - x1) * dx + (pz - z1) * dz) / lenSq).coerceIn(0f, 1f)
    return hypot(px - (x1 + t * dx), pz - (z1 + t * dz))
  }

  /**
   * Negative values lie inside dry land islands; positive values lie in turquoise coves or ocean.
   * Matches the Nextera map silhouette: North Intellect island + curved moat, West Turbine cove,
   * and organic coastline lobes around Museum, Grav Labs, Deca Square, Mud Site, Rust Town & Twin Bridge.
   */
  fun signedCoastDistance(x: Float, z: Float): Float {
    val warp = (valueNoise(x * 0.036f + 2.3f, z * 0.036f - 1.7f) - 0.5f) * 11.5f

    // Main Nextera landmass composed of organic overlapping lobes
    val dMuseum = sdEllipse(x, z, -172f, -86f, 62f, 56f)
    val dCenter = sdRoundBox(x, z, -24f, 22f, 126f, 118f, 34f)
    val dGrav = sdEllipse(x, z, -158f, 166f, 68f, 58f)
    val dSouth = sdEllipse(x, z, -6f, 168f, 108f, 60f)
    val dMud = sdEllipse(x, z, 146f, 134f, 68f, 70f)
    val dEast = sdEllipse(x, z, 164f, -14f, 74f, 122f)
    val dTwin = sdEllipse(x, z, 126f, -146f, 64f, 56f)

    var mainland =
      min(
        min(min(dMuseum, dCenter), min(dGrav, dSouth)),
        min(min(dMud, dEast), dTwin),
      )

    // Subtract Turbine Cove (deep turquoise bay between Museum coast and Grav Labs)
    val dTurbineCove = sdEllipse(x, z, -162f, 6f, 34f, 44f)
    mainland = max(mainland, -dTurbineCove)

    // Subtract Intellect Center Curved Moat on South crescent of Intellect Island
    val rIntellect = hypot(x + 18f, z + 182f)
    val inSouthMoatArc = z > -186f && x in -116f..78f
    if (inSouthMoatArc) {
      val dMoat = abs(rIntellect - 84f) - 12.5f
      mainland = max(mainland, -dMoat)
    }

    // Separate Islands: Intellect Center (North) & Turbine Headland (West)
    val dIntellectIsland = sdEllipse(x, z, -18f, -184f, 74f, 66f)
    val dTurbineIsland = sdEllipse(x, z, -218f, 6f, 33f, 44f)

    // Keep Marina Boat Pier spit solid at (-198, 62)
    val dPierApproach = sdBox(x, z, -198f, 62f, 8f, 10f)

    val landDist = min(min(mainland, min(dIntellectIsland, dTurbineIsland)), dPierApproach)
    return landDist + warp
  }

  /**
   * Distance from paved Nextera roads, bridges, Gun Shop, Market stalls, and enterable house lots
   * where ground elevation smoothly flattens to keep buildings and vehicles flush.
   */
  private fun distanceToFlatInfrastructure(x: Float, z: Float): Float {
    var d = Float.MAX_VALUE

    for (seg in roadSegments) {
      d = min(d, distToSegment(x, z, seg.x1, seg.z1, seg.x2, seg.z2) - seg.halfWidth)
    }

    // Gun Shop (-46, -14), Farmtopia Produce Market (-50, 24), Player Spawn Plaza (-56, 8)
    d = min(d, sdBox(x, z, -46f, -14f, 8.5f, 8.5f))
    d = min(d, sdBox(x, z, -50f, 24f, 12f, 10f))
    d = min(d, sdBox(x, z, -56f, 8f, 10f, 10f))

    // Boat Pier approach at Grav Labs / Turbine Cove (-198, 62..72)
    d = min(d, sdBox(x, z, -198f, 66f, 6f, 12f))

    // All 10 Enterable Multi-Story Houses (flat building pads padded by 4.5m for doorways & stairs)
    val houseLots =
      arrayOf(
        floatArrayOf(-72f, -18f, 12f, 10f),   // Farmtopia Manor
        floatArrayOf(-72f, 34f, 11f, 10f),    // Farmtopia Barn Villa
        floatArrayOf(-154f, -66f, 12f, 10f),  // Museum Gallery House
        floatArrayOf(-66f, 126f, 12f, 11f),   // Plazaria Courtyard Loft
        floatArrayOf(24f, 144f, 12f, 10f),    // Deca Square Penthouse
        floatArrayOf(-126f, 146f, 11f, 10f),  // Grav Labs Dormitory
        floatArrayOf(164f, 26f, 14f, 11f),    // Rust Town Barracks (with front concrete stairs)
        floatArrayOf(148f, -72f, 11f, 10f),   // Mortar Bunker House
        floatArrayOf(154f, 122f, 12f, 10f),   // Mud Site Quarters
        floatArrayOf(106f, -124f, 12f, 10f),  // Twin Bridge Villa
      )
    for (lot in houseLots) {
      d = min(d, sdBox(x, z, lot[0], lot[1], lot[2], lot[3]))
    }

    return d
  }

  private fun gaussianPeak(x: Float, z: Float, cx: Float, cz: Float, radius: Float, height: Float): Float {
    val d2 = (x - cx) * (x - cx) + (z - cz) * (z - cz)
    if (d2 > radius * radius * 5.2f) return 0f
    return height * exp(-d2 / (radius * radius) * 1.45f)
  }

  /**
   * True 3D Nextera terrain elevation in world meters.
   */
  fun elevationAt(x: Float, z: Float): Float {
    val coast = signedCoastDistance(x, z)
    if (coast > 8.5f) {
      return -1.18f
    }

    val landBlend = 1f - smoothstep(-6.0f, 3.2f, coast)
    val coastalLip = -1.18f + 1.42f * landBlend

    // Rolling undulating Nextera terrain + ridged hills + subtle micro-roughness
    val macroHills = (fbm(x * 0.018f + 3.7f, z * 0.018f - 1.9f) - 0.32f) * 7.8f
    val ridgeDetail = ridged(x * 0.042f - 2.1f, z * 0.042f + 4.4f) * 3.2f
    val microUneven = (valueNoise(x * 0.14f, z * 0.14f) - 0.5f) * 0.62f
    var hillHeight = max(0f, macroHills + ridgeDetail + microUneven) * smoothstep(-1.5f, -12.0f, coast)

    // Biome hills & coastal ridges matching fmap.png
    // 1. Intellect Center North Ridge & Citadel Rise
    hillHeight += gaussianPeak(x, z, -18f, -226f, 36f, 13.5f)
    hillHeight += gaussianPeak(x, z, -56f, -196f, 24f, 6.2f)
    hillHeight += gaussianPeak(x, z, 26f, -194f, 24f, 6.2f)

    // 2. NE Twin Bridge & Mortar Ruins Bluff Ridge
    hillHeight += gaussianPeak(x, z, 156f, -158f, 32f, 17.5f)
    hillHeight += gaussianPeak(x, z, 198f, -94f, 28f, 14.5f)

    // 3. West Turbine Rocky Coastal Bluff & Museum Headland
    hillHeight += gaussianPeak(x, z, -222f, -4f, 26f, 7.8f)
    hillHeight += gaussianPeak(x, z, -206f, -106f, 24f, 9.2f)

    // 4. Central Zipway / Farmtopia Ridge & South Grav Headland
    hillHeight += gaussianPeak(x, z, 48f, -32f, 28f, 6.8f)
    hillHeight += gaussianPeak(x, z, -194f, 184f, 24f, 8.6f)

    // 5. Mortar Ruins Artillery Crater near (176, -68)
    val distCrater = hypot(x - 176f, z + 68f)
    val craterMod =
      if (distCrater < 18f) {
        val rim = exp(-((distCrater - 11.5f) * (distCrater - 11.5f)) / 10f) * 2.9f
        val pit = smoothstep(10f, 2.5f, distCrater) * -1.6f
        rim + pit
      } else 0f

    val rawElev = coastalLip + hillHeight + craterMod

    // Flatten road corridors and enterable house lots smoothly
    val dInfra = distanceToFlatInfrastructure(x, z)
    val infraBlend = smoothstep(0.0f, 7.8f, dInfra)
    val flatTarget = if (coast > 1.5f) coastalLip else 0f
    var elev = flatTarget + (rawElev - flatTarget) * infraBlend

    // Grade crisp terraced building pads for signature Nextera POIs
    val poiWeight = smoothstep(1.5f, 5.5f, dInfra)
    if (coast < 0f && poiWeight > 0.001f) {
      elev = applyPoiTerrace(elev, x, z, cx = -18f, cz = -180f, radius = 22f, targetY = 3.4f, poiWeight) // Intellect Center Citadel
      elev = applyPoiTerrace(elev, x, z, cx = -178f, cz = -80f, radius = 16f, targetY = 1.9f, poiWeight) // Museum
      elev = applyPoiTerrace(elev, x, z, cx = -218f, cz = 6f, radius = 14f, targetY = 3.8f, poiWeight) // Turbine Bluff
      elev = applyPoiTerrace(elev, x, z, cx = -158f, cz = 168f, radius = 21f, targetY = 1.6f, poiWeight) // Grav Labs
      elev = applyPoiTerrace(elev, x, z, cx = -66f, cz = 152f, radius = 13f, targetY = 1.2f, poiWeight) // Plazaria
      elev = applyPoiTerrace(elev, x, z, cx = 24f, cz = 172f, radius = 16f, targetY = 1.4f, poiWeight) // Deca Square
      elev = applyPoiTerrace(elev, x, z, cx = 154f, cz = 148f, radius = 14f, targetY = 0.9f, poiWeight) // Mud Site
      elev = applyPoiTerrace(elev, x, z, cx = 76f, cz = 14f, radius = 13f, targetY = 1.8f, poiWeight) // Zipway
      elev = applyPoiTerrace(elev, x, z, cx = 188f, cz = 22f, radius = 18f, targetY = 1.5f, poiWeight) // Rust Town
      elev = applyPoiTerrace(elev, x, z, cx = 116f, cz = -148f, radius = 12f, targetY = 2.6f, poiWeight) // Twin Bridge
      elev = applyPoiTerrace(elev, x, z, cx = -72f, cz = 6f, radius = 13f, targetY = 0.8f, poiWeight) // Farmtopia Barn Pad
    }
    return elev
  }

  private fun applyPoiTerrace(
    currentY: Float,
    x: Float,
    z: Float,
    cx: Float,
    cz: Float,
    radius: Float,
    targetY: Float,
    infraWeight: Float,
  ): Float {
    val d = hypot(x - cx, z - cz)
    if (d > radius + 8f) return currentY
    val blend = (1f - smoothstep(radius, radius + 8f, d)) * infraWeight
    return currentY + (targetY - currentY) * blend
  }

  fun normalAt(x: Float, z: Float, out: FloatArray) {
    val eps = 0.65f
    val hL = elevationAt(x - eps, z)
    val hR = elevationAt(x + eps, z)
    val hD = elevationAt(x, z - eps)
    val hU = elevationAt(x, z + eps)
    val nx = hL - hR
    val ny = 2f * eps
    val nz = hD - hU
    val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-5f)
    out[0] = nx / len
    out[1] = ny / len
    out[2] = nz / len
  }

  fun computeSlopeAngles(x: Float, z: Float, yawRad: Float): Pair<Float, Float> {
    val fwdX = sin(yawRad)
    val fwdZ = cos(yawRad)
    val rightX = cos(yawRad)
    val rightZ = -sin(yawRad)
    val hF = elevationAt(x + fwdX * 1.4f, z + fwdZ * 1.4f)
    val hB = elevationAt(x - fwdX * 1.4f, z - fwdZ * 1.4f)
    val hR = elevationAt(x + rightX * 0.9f, z + rightZ * 0.9f)
    val hL = elevationAt(x - rightX * 0.9f, z - rightZ * 0.9f)
    val pitchRad = -atan2(hF - hB, 2.8f).coerceIn(-0.42f, 0.42f)
    val rollRad = atan2(hR - hL, 1.8f).coerceIn(-0.34f, 0.34f)
    return pitchRad to rollRad
  }
}
