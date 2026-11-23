package com.example.game.gl

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Procedural smooth organic 3D character meshes (zero boxy edges) with analytical outward CCW winding
 * and vertex-averaged smooth normals so SKIN and CLOTH shading render like a Pixar/3D cartoon film.
 */
object CharacterMeshBuilder {
  data class RawMesh(
    val vertices: FloatArray,
    val indices: ShortArray,
  ) {
    fun toGlMesh(): Mesh = Mesh(vertices, indices)
  }

  private fun buildSmoothIndexedMesh(
    pos: FloatArray,
    uvs: FloatArray,
    indices: ShortArray,
  ): RawMesh {
    val vertCount = pos.size / 3
    val normals = FloatArray(pos.size)
    var i = 0
    while (i < indices.size) {
      val i0 = indices[i].toInt() and 0xFFFF
      val i1 = indices[i + 1].toInt() and 0xFFFF
      val i2 = indices[i + 2].toInt() and 0xFFFF

      val x0 = pos[i0 * 3]; val y0 = pos[i0 * 3 + 1]; val z0 = pos[i0 * 3 + 2]
      val x1 = pos[i1 * 3]; val y1 = pos[i1 * 3 + 1]; val z1 = pos[i1 * 3 + 2]
      val x2 = pos[i2 * 3]; val y2 = pos[i2 * 3 + 1]; val z2 = pos[i2 * 3 + 2]

      val ux = x1 - x0; val uy = y1 - y0; val uz = z1 - z0
      val vx = x2 - x0; val vy = y2 - y0; val vz = z2 - z0
      val nx = uy * vz - uz * vy
      val ny = uz * vx - ux * vz
      val nz = ux * vy - uy * vx

      normals[i0 * 3] += nx; normals[i0 * 3 + 1] += ny; normals[i0 * 3 + 2] += nz
      normals[i1 * 3] += nx; normals[i1 * 3 + 1] += ny; normals[i1 * 3 + 2] += nz
      normals[i2 * 3] += nx; normals[i2 * 3 + 1] += ny; normals[i2 * 3 + 2] += nz
      i += 3
    }

    val interleaved = FloatArray(vertCount * 8)
    for (v in 0 until vertCount) {
      var nx = normals[v * 3]
      var ny = normals[v * 3 + 1]
      var nz = normals[v * 3 + 2]
      var len = sqrt(nx * nx + ny * ny + nz * nz)
      if (len < 1e-5f) {
        nx = pos[v * 3]
        ny = pos[v * 3 + 1]
        nz = pos[v * 3 + 2]
        len = sqrt(nx * nx + ny * ny + nz * nz)
        if (len < 1e-5f) {
          nx = 0f; ny = 1f; nz = 0f; len = 1f
        }
      }
      nx /= len; ny /= len; nz /= len
      val dst = v * 8
      interleaved[dst] = pos[v * 3]
      interleaved[dst + 1] = pos[v * 3 + 1]
      interleaved[dst + 2] = pos[v * 3 + 2]
      interleaved[dst + 3] = nx
      interleaved[dst + 4] = ny
      interleaved[dst + 5] = nz
      interleaved[dst + 6] = uvs[v * 2]
      interleaved[dst + 7] = uvs[v * 2 + 1]
    }
    return RawMesh(interleaved, indices)
  }

  private fun sweepGridMesh(
    rings: Int,
    sectors: Int,
    samplePoint: (ringIdx: Int, sectorIdx: Int) -> FloatArray,
  ): RawMesh {
    val stride = sectors + 1
    val vertCount = (rings + 1) * stride
    val pos = FloatArray(vertCount * 3)
    val uvs = FloatArray(vertCount * 2)
    for (r in 0..rings) {
      val v = r.toFloat() / rings
      for (s in 0..sectors) {
        val u = s.toFloat() / sectors
        val p = samplePoint(r, s)
        val idx = r * stride + s
        pos[idx * 3] = p[0]
        pos[idx * 3 + 1] = p[1]
        pos[idx * 3 + 2] = p[2]
        uvs[idx * 2] = u
        uvs[idx * 2 + 1] = v
      }
    }
    val indices = ArrayList<Short>(rings * sectors * 6)
    for (r in 0 until rings) {
      for (s in 0 until sectors) {
        val i0 = (r * stride + s).toShort()
        val i1 = (i0 + 1).toShort()
        val i2 = ((r + 1) * stride + s).toShort()
        val i3 = (i2 + 1).toShort()
        indices.add(i0); indices.add(i1); indices.add(i2)
        indices.add(i1); indices.add(i3); indices.add(i2)
      }
    }
    return buildSmoothIndexedMesh(pos, uvs, indices.toShortArray())
  }

  /**
   * Smooth normalized limb capsule (height -0.5..+0.5, diameter 1.0 in X/Z) with rounded dome caps
   * so elbows, knees, shoulders, neck, and hips look organic with zero hard cylinder edges.
   */
  fun createRoundedCapsuleRaw(sectors: Int = 16, capRings: Int = 5): RawMesh {
    val totalRings = capRings * 2 + 2
    val ys = FloatArray(totalRings + 1)
    val rs = FloatArray(totalRings + 1)

    // Bottom hemisphere cap (-0.5 -> -0.28)
    val cylHalf = 0.26f
    val capH = 0.5f - cylHalf
    for (i in 0..capRings) {
      val a = -PI.toFloat() * 0.5f + (i.toFloat() / capRings) * (PI.toFloat() * 0.5f)
      ys[i] = -cylHalf + sin(a) * capH
      rs[i] = cos(a).coerceAtLeast(0.001f) * 0.5f
    }
    // Slight organic muscle taper along shaft
    ys[capRings + 1] = 0f
    rs[capRings + 1] = 0.515f
    // Top hemisphere cap (+0.28 -> +0.5)
    for (i in 0..capRings) {
      val a = (i.toFloat() / capRings) * (PI.toFloat() * 0.5f)
      val idx = capRings + 2 + i
      ys[idx] = cylHalf + sin(a) * capH
      rs[idx] = cos(a).coerceAtLeast(0.001f) * 0.48f
    }

    return sweepGridMesh(totalRings, sectors) { r, s ->
      val a = (s.toFloat() / sectors) * 2f * PI.toFloat()
      // -cos/sin winding with upward +Y sweep produces outward CCW normals
      floatArrayOf(rs[r] * sin(a), ys[r], rs[r] * cos(a))
    }
  }

  /**
   * Sculpted chubby human-cartoon head (diameter ~1.0) with:
   * - Smooth glossy bald cranium
   * - Subtle brow & eye-socket contour
   * - Soft plump lower cheeks (left/right)
   * - Natural soft chin & under-chin jowl curve matching Motu close-up photo
   */
  fun createSculptedChubbyHeadRaw(rings: Int = 22, sectors: Int = 28): RawMesh {
    val pos = FloatArray((rings + 1) * (sectors + 1) * 3)
    val uvs = FloatArray((rings + 1) * (sectors + 1) * 2)
    val stride = sectors + 1

    for (r in 0..rings) {
      val v = r.toFloat() / rings
      val phi = v * PI.toFloat()
      val baseY = cos(phi)
      val sinPhi = sin(phi).coerceAtLeast(1e-3f)
      for (s in 0..sectors) {
        val u = s.toFloat() / sectors
        val theta = u * 2f * PI.toFloat()
        val nx = sinPhi * cos(theta)
        val nz = sinPhi * sin(theta)

        // Base ellipsoid slightly narrower on upper temples, wider in lower cheeks
        val cheekZone = exp(-((baseY + 0.24f) / 0.34f).pow(2f))
        val jawWidthBoost = 1.0f + 0.14f * cheekZone * (1.0f - 0.35f * max(0f, nz))
        val craniumTaper = 1.0f - 0.06f * max(0f, baseY - 0.18f)

        var x = nx * 0.49f * jawWidthBoost * craniumTaper
        var y = baseY * 0.50f
        var z = nz * 0.47f

        if (nz > 0f) {
          // Chubby apples of the cheeks on left & right of mouth/nose
          val leftCheek = exp(-((nx + 0.56f) / 0.34f).pow(2f) - ((baseY + 0.20f) / 0.26f).pow(2f))
          val rightCheek = exp(-((nx - 0.56f) / 0.34f).pow(2f) - ((baseY + 0.20f) / 0.26f).pow(2f))
          val cheekPuff = (leftCheek + rightCheek) * nz
          x += nx * 0.048f * cheekPuff
          z += 0.065f * cheekPuff

          // Gentle eye-socket hollow so eyeballs nestle into face instead of sticking out
          val eyeL = exp(-((nx + 0.32f) / 0.22f).pow(2f) - ((baseY - 0.16f) / 0.16f).pow(2f))
          val eyeR = exp(-((nx - 0.32f) / 0.22f).pow(2f) - ((baseY - 0.16f) / 0.16f).pow(2f))
          z -= 0.022f * (eyeL + eyeR) * nz

          // Warm soft double-chin puff below lower lip
          val chinPuff = exp(-((nx) / 0.48f).pow(2f) - ((baseY + 0.68f) / 0.24f).pow(2f))
          z += 0.046f * chinPuff * nz
          y -= 0.028f * chinPuff * nz
        }

        val idx = r * stride + s
        pos[idx * 3] = x
        pos[idx * 3 + 1] = y
        pos[idx * 3 + 2] = z
        uvs[idx * 2] = u
        uvs[idx * 2 + 1] = v
      }
    }

    val indices = ArrayList<Short>(rings * sectors * 6)
    for (r in 0 until rings) {
      for (s in 0 until sectors) {
        val i0 = (r * stride + s).toShort()
        val i1 = (i0 + 1).toShort()
        val i2 = ((r + 1) * stride + s).toShort()
        val i3 = (i2 + 1).toShort()
        indices.add(i0); indices.add(i1); indices.add(i2)
        indices.add(i1); indices.add(i3); indices.add(i2)
      }
    }
    return buildSmoothIndexedMesh(pos, uvs, indices.toShortArray())
  }

  /**
   * Seamless smooth pear/bell kurta torso (normalized height -0.5..+0.5) for chubby hero & townsfolk:
   * smooth collar shoulder slope, soft chest, prominent round front belly, and flared hem bottom.
   */
  fun createKurtaTorsoRaw(rings: Int = 18, sectors: Int = 24): RawMesh {
    return sweepGridMesh(rings, sectors) { r, s ->
      val t = r.toFloat() / rings // 0 bottom hem -> 1 collar neck
      val y = -0.5f + t
      val a = (s.toFloat() / sectors) * 2f * PI.toFloat()
      val sa = sin(a)
      val ca = cos(a)

      // Bottom closed flat skirt tuck -> wide flared belly -> tapered collar
      val radiusX: Float
      val radiusZ: Float
      val zShift: Float
      when {
        t < 0.06f -> {
          val k = t / 0.06f
          radiusX = 0.01f + 0.45f * sin(k * PI.toFloat() * 0.5f)
          radiusZ = 0.01f + 0.39f * sin(k * PI.toFloat() * 0.5f)
          zShift = 0.03f * k
        }
        t > 0.92f -> {
          val k = (1f - t) / 0.08f
          radiusX = 0.01f + 0.33f * sin(k * PI.toFloat() * 0.5f)
          radiusZ = 0.01f + 0.25f * sin(k * PI.toFloat() * 0.5f)
          zShift = 0.0f
        }
        else -> {
          val u = (t - 0.06f) / 0.86f // 0 skirt hem .. 1 shoulder top
          val bellyCurve = sin(u * PI.toFloat() * 0.88f + 0.22f)
          val flareHem = exp(-((u - 0.14f) / 0.22f).pow(2f)) * 0.05f
          radiusX = (0.34f + 0.16f * bellyCurve + flareHem) * (1.0f - 0.08f * u)
          radiusZ = 0.26f + 0.18f * bellyCurve + flareHem * 0.7f
          zShift = 0.095f * exp(-((u - 0.38f) / 0.32f).pow(2f))
        }
      }
      // Gentle kurta front V-slit subtle dimple at upper chest
      val slitIndent =
        if (t in 0.72f..0.91f && ca > 0.86f) {
          val w = 1f - abs(sa) / 0.50f
          val h = sin(((t - 0.72f) / 0.19f) * PI.toFloat())
          max(0f, w) * h * 0.028f
        } else 0f

      floatArrayOf(radiusX * sa, y, zShift + (radiusZ - slitIndent) * ca)
    }
  }

  /**
   * Sculpted open-front V-neck waistcoat/vest shell with curved shoulders, seamless upper back,
   * and smooth diagonal V-opening tapering down the upper front chest — zero flat boxes!
   */
  fun createSculptedVestRaw(uSteps: Int = 26, vSteps: Int = 14): RawMesh {
    // u wraps horizontally from Left Front Lapel (-thetaStart) around Left Side -> Back -> Right Side -> Right Front Lapel (+thetaStart)
    // v sweeps vertically from bottom edge (0) to top collar/shoulder edge (1)
    // Built as a closed thick shell (outer surface + inner surface) around a 2D closed cross-section loop?
    // Even cleaner: sweep a closed oval tube loop of thickness around (u, v) by wrapping s around cross-section?
    // Actually we can build outer surface (uSteps x vSteps) + inner surface (uSteps x vSteps) stitched at edges, OR
    // a single thick sculpted panel parametrized by (u: 0..1 around horseshoe perimeter, w: 0..1 around height loop)!
    // Sweeping vertical profile (w: 0..1 loops: up outside -> curved top shoulder lip -> down inside -> curved bottom hem)
    // around horizontal horseshoe (u: 0..1 from front-left opening around back to front-right opening)!
    // Wait: end caps at left/right front opening! Let's include rounded tapering at u=0 and u=1 so thickness shrinks smoothly to rounded trim!
    val rings = uSteps
    val sectors = vSteps * 2 // Closed loop around vertical cross-section!

    return sweepGridMesh(rings, sectors) { r, s ->
      val u = r.toFloat() / rings // 0 = front-left chest edge, 0.5 = center back, 1 = front-right chest edge
      val w = -(s.toFloat() / sectors) * 2f * PI.toFloat()

      // Smooth thickness tapers cleanly at the very tips of front opening edges so ends close seamlessly!
      val endFade = sin(u * PI.toFloat()).coerceAtLeast(0.02f).pow(0.28f)
      // Angle around torso: leaves a V-shaped chest gap in front centered at theta=0 (+Z)
      // Gap half-angle is wide near bottom chest (48 deg), narrower near collar top (18 deg)
      val crossCos = cos(w) // +1 outside, -1 inside
      val crossSin = sin(w) // +1 top/bottom loop
      val vPos = 0.5f + 0.5f * crossSin // 0 bottom hem .. 1 top collar
      val isOuter = crossCos >= 0f

      // V-neck gap widens lower on chest & opens cleanly
      val chestGapHalfRad = (0.28f + 0.58f * (1f - vPos).pow(0.85f))
      // Map u in [0..1] to theta wrapping around back:
      // u=0 -> +chestGapHalfRad (right of viewer / left side of torso - wait: front is +Z: x=R*sin(theta), z=R*cos(theta))
      // So theta = chestGapHalfRad -> 2*PI - chestGapHalfRad!
      val theta = chestGapHalfRad + u * (2f * PI.toFloat() - 2f * chestGapHalfRad)

      // Torso shell contour to hug Motu's upper kurta chest & belly seamlessly
      val yTop = 0.44f - 0.08f * max(0f, cos(theta)) // slightly lower at front collar than back neck
      val yBot = -0.18f - 0.06f * (1f - cos(theta)) // gentle curved waistcoat bottom edge
      val y = yBot + vPos * (yTop - yBot)

      // Radius matches upper kurta torso curvature
      val bellyCurve = sin((0.52f + vPos * 0.44f) * PI.toFloat() * 0.90f)
      val baseRx = 0.34f + 0.145f * bellyCurve
      val baseRz = 0.27f + 0.155f * bellyCurve
      val zShift = 0.075f * exp(-((vPos - 0.15f) / 0.45f).pow(2f))

      val wallThick = 0.030f * endFade * (0.45f + 0.55f * sqrt(max(0.02f, abs(crossCos))))
      val offset = if (isOuter) wallThick else -wallThick * 0.35f

      val px = (baseRx + offset) * sin(theta)
      val pz = zShift + (baseRz + offset) * cos(theta)
      floatArrayOf(px, y, pz)
    }
  }

  /**
   * Sculpted curved tapered hair lock (origin at root, sweeps outward +Z and droops gracefully toward -Y).
   * Replaces boxy blocks on hair fringe!
   */
  fun createHairSpikeRaw(rings: Int = 8, sectors: Int = 10): RawMesh {
    return sweepGridMesh(rings, sectors) { r, s ->
      val t = r.toFloat() / rings // 0 root .. 1 sharp soft tip
      val a = (s.toFloat() / sectors) * 2f * PI.toFloat()

      val cz = t * 1.0f
      val cy = sin(t * PI.toFloat() * 0.72f) * 0.14f - t * t * 0.32f
      val radius = (1f - t).pow(0.75f) * 0.44f + 0.008f
      val widthScale = 1.15f
      val heightScale = 0.82f

      val rx = cos(a) * radius * widthScale
      val ry = sin(a) * radius * heightScale
      floatArrayOf(rx, cy + ry, cz)
    }
  }

  /**
   * Smooth sculpted crescent eyebrow arch along X (-0.5..+0.5) curving gently around the forehead.
   */
  fun createCurvedEyebrowRaw(rings: Int = 14, sectors: Int = 10): RawMesh {
    return sweepGridMesh(rings, sectors) { r, s ->
      val t = r.toFloat() / rings // 0 inner .. 1 outer
      val u = t * 2f - 1f // -1 .. +1
      val a = (s.toFloat() / sectors) * 2f * PI.toFloat()

      val cx = u * 0.5f
      val cy = 0.16f * (1f - u * u) // arched brow peak
      val cz = -0.10f * (u * u) // wraps slightly backward around brow bone

      val rTap = sin(t * PI.toFloat()).coerceAtLeast(0.04f).pow(0.55f)
      val thickY = 0.11f * rTap
      val thickZ = 0.12f * rTap

      // Tangent +X -> (cos=N=+Y, sin=B=+Z) ensures outward CCW normals
      floatArrayOf(cx, cy + cos(a) * thickY, cz + sin(a) * thickZ)
    }
  }

  /**
   * Seamless 3D handlebar mustache centered at (0,0,0) with full plump center under the nose
   * and symmetrical upturned curled tips on both sides (-0.5..+0.5 along X).
   */
  fun createHandlebarMustacheRaw(rings: Int = 24, sectors: Int = 12): RawMesh {
    return sweepGridMesh(rings, sectors) { r, s ->
      val t = r.toFloat() / rings // 0 left tip .. 0.5 philtrum center .. 1 right tip
      val u = t * 2f - 1f // -1 .. +1
      val au = abs(u)
      val a = (s.toFloat() / sectors) * 2f * PI.toFloat()

      val cx = u * 0.5f
      // Center dips gently at septum (au=0), curves down across cheek (au=0.45), curls UP boldly at tips (au=1.0)
      val cy = -0.10f * sin(au * PI.toFloat() * 0.72f) + 0.34f * au.pow(2.7f)
      val cz = -0.22f * (u * u) // wraps cleanly around round chubby upper lip!

      // Plump thickness across mustache wings, sharp jaunty curl at ends, slight dip in center notch
      val centerNotch = 1f - 0.18f * exp(-(au / 0.10f).pow(2f))
      val endTaper = sin(t * PI.toFloat()).coerceAtLeast(0.02f).pow(0.52f)
      val lobeBoost = 1f + 0.28f * sin(au * PI.toFloat() * 0.9f)
      val rad = 0.115f * endTaper * lobeBoost * centerNotch

      floatArrayOf(cx, cy + cos(a) * rad * 0.95f, cz + sin(a) * rad * 1.15f)
    }
  }

  /**
   * Sculpted cheerful D-arc smiling mouth cavity / teeth arc hugging front face curvature.
   */
  fun createSmileArcRaw(rings: Int = 14, sectors: Int = 10, isTeethStrip: Boolean = false): RawMesh {
    return sweepGridMesh(rings, sectors) { r, s ->
      val t = r.toFloat() / rings
      val u = t * 2f - 1f
      val a = (s.toFloat() / sectors) * 2f * PI.toFloat()

      val cx = u * 0.5f
      val arcSmile =
        if (isTeethStrip) {
          0.11f * (u * u) // subtle upward smile curve along upper teeth
        } else {
          0.16f * (u * u) - 0.08f * (1f - u * u) // deep joyful open D-smile bowl
        }
      val cz = -0.20f * (u * u)

      val endTaper = sin(t * PI.toFloat()).coerceAtLeast(0.03f).pow(0.50f)
      val radY = (if (isTeethStrip) 0.085f else 0.155f) * endTaper
      val radZ = (if (isTeethStrip) 0.08f else 0.10f) * endTaper

      floatArrayOf(cx, arcSmile + cos(a) * radY, cz + sin(a) * radZ)
    }
  }

  /**
   * Smooth rounded loafer cartoon shoe (heel at -0.36, plump rounded toe at +0.58, flat bottom sole).
   */
  fun createRoundedShoeRaw(rings: Int = 14, sectors: Int = 16): RawMesh {
    return sweepGridMesh(rings, sectors) { r, s ->
      val t = r.toFloat() / rings // 0 heel back -> 1 toe tip
      val a = (s.toFloat() / sectors) * 2f * PI.toFloat()

      val cz = -0.38f + t * 0.96f
      val endFade = sin(t * PI.toFloat()).coerceAtLeast(0.02f).pow(0.45f)
      val toeWidth = 1f + 0.18f * smoothstep(0.45f, 0.85f, t)
      val halfW = 0.42f * endFade * toeWidth
      val height = 0.46f * endFade * (1f - 0.25f * smoothstep(0.35f, 0.92f, t))

      val sx = cos(a) * halfW
      // Flatten sole underneath slightly so shoe plants stably on pavement
      val rawSy = sin(a)
      val sy = if (rawSy < 0f) rawSy * height * 0.55f else rawSy * height
      floatArrayOf(sx, sy + 0.08f * (1f - t), cz)
    }
  }

  private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
  }

  fun allRawMeshes(): List<Pair<String, RawMesh>> =
    listOf(
      "capsule" to createRoundedCapsuleRaw(),
      "chubbyHead" to createSculptedChubbyHeadRaw(),
      "kurtaTorso" to createKurtaTorsoRaw(),
      "vest" to createSculptedVestRaw(),
      "hairSpike" to createHairSpikeRaw(),
      "eyebrow" to createCurvedEyebrowRaw(),
      "mustache" to createHandlebarMustacheRaw(),
      "smileMouth" to createSmileArcRaw(isTeethStrip = false),
      "teethArc" to createSmileArcRaw(isTeethStrip = true),
      "shoe" to createRoundedShoeRaw(),
    )

  class CharacterMeshes(
    val boxMesh: Mesh,
    val cylinderMesh: Mesh,
    val sphereMesh: Mesh,
  ) {
    val capsuleMesh: Mesh = createRoundedCapsuleRaw().toGlMesh()
    val chubbyHeadMesh: Mesh = createSculptedChubbyHeadRaw().toGlMesh()
    val kurtaTorsoMesh: Mesh = createKurtaTorsoRaw().toGlMesh()
    val vestMesh: Mesh = createSculptedVestRaw().toGlMesh()
    val hairSpikeMesh: Mesh = createHairSpikeRaw().toGlMesh()
    val eyebrowMesh: Mesh = createCurvedEyebrowRaw().toGlMesh()
    val mustacheMesh: Mesh = createHandlebarMustacheRaw().toGlMesh()
    val smileMouthMesh: Mesh = createSmileArcRaw(isTeethStrip = false).toGlMesh()
    val teethArcMesh: Mesh = createSmileArcRaw(isTeethStrip = true).toGlMesh()
    val shoeMesh: Mesh = createRoundedShoeRaw().toGlMesh()
  }
}
