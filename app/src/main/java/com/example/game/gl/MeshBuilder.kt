package com.example.game.gl

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object MeshBuilder {
  fun createUnitBox(): Mesh {
    val v =
      floatArrayOf(
        // Front (+Z)
        -0.5f, -0.5f, 0.5f, 0f, 0f, 1f, 0f, 0f,
        0.5f, -0.5f, 0.5f, 0f, 0f, 1f, 1f, 0f,
        0.5f, 0.5f, 0.5f, 0f, 0f, 1f, 1f, 1f,
        -0.5f, 0.5f, 0.5f, 0f, 0f, 1f, 0f, 1f,
        // Back (-Z)
        0.5f, -0.5f, -0.5f, 0f, 0f, -1f, 0f, 0f,
        -0.5f, -0.5f, -0.5f, 0f, 0f, -1f, 1f, 0f,
        -0.5f, 0.5f, -0.5f, 0f, 0f, -1f, 1f, 1f,
        0.5f, 0.5f, -0.5f, 0f, 0f, -1f, 0f, 1f,
        // Right (+X)
        0.5f, -0.5f, 0.5f, 1f, 0f, 0f, 0f, 0f,
        0.5f, -0.5f, -0.5f, 1f, 0f, 0f, 1f, 0f,
        0.5f, 0.5f, -0.5f, 1f, 0f, 0f, 1f, 1f,
        0.5f, 0.5f, 0.5f, 1f, 0f, 0f, 0f, 1f,
        // Left (-X)
        -0.5f, -0.5f, -0.5f, -1f, 0f, 0f, 0f, 0f,
        -0.5f, -0.5f, 0.5f, -1f, 0f, 0f, 1f, 0f,
        -0.5f, 0.5f, 0.5f, -1f, 0f, 0f, 1f, 1f,
        -0.5f, 0.5f, -0.5f, -1f, 0f, 0f, 0f, 1f,
        // Top (+Y)
        -0.5f, 0.5f, 0.5f, 0f, 1f, 0f, 0f, 0f,
        0.5f, 0.5f, 0.5f, 0f, 1f, 0f, 1f, 0f,
        0.5f, 0.5f, -0.5f, 0f, 1f, 0f, 1f, 1f,
        -0.5f, 0.5f, -0.5f, 0f, 1f, 0f, 0f, 1f,
        // Bottom (-Y)
        -0.5f, -0.5f, -0.5f, 0f, -1f, 0f, 0f, 0f,
        0.5f, -0.5f, -0.5f, 0f, -1f, 0f, 1f, 0f,
        0.5f, -0.5f, 0.5f, 0f, -1f, 0f, 1f, 1f,
        -0.5f, -0.5f, 0.5f, 0f, -1f, 0f, 0f, 1f,
      )
    val idx =
      shortArrayOf(
        0, 1, 2, 0, 2, 3,
        4, 5, 6, 4, 6, 7,
        8, 9, 10, 8, 10, 11,
        12, 13, 14, 12, 14, 15,
        16, 17, 18, 16, 18, 19,
        20, 21, 22, 20, 22, 23,
      )
    return Mesh(v, idx)
  }

  fun createCylinder(segments: Int = 18, radiusTop: Float = 0.5f, radiusBottom: Float = 0.5f): Mesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Short>()

    fun pushVert(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float, v: Float) {
      verts.add(x)
      verts.add(y)
      verts.add(z)
      verts.add(nx)
      verts.add(ny)
      verts.add(nz)
      verts.add(u)
      verts.add(v)
    }

    val slope = radiusBottom - radiusTop
    // Side ring
    for (i in 0..segments) {
      val a = (i.toFloat() / segments) * (2f * PI.toFloat())
      val ca = cos(a)
      val sa = sin(a)
      val nLen = sqrt(ca * ca + sa * sa + slope * slope).coerceAtLeast(1e-5f)
      val nx = ca / nLen
      val ny = slope / nLen
      val nz = sa / nLen
      pushVert(ca * radiusBottom, -0.5f, sa * radiusBottom, nx, ny, nz, i.toFloat() / segments, 0f)
      pushVert(ca * radiusTop, 0.5f, sa * radiusTop, nx, ny, nz, i.toFloat() / segments, 1f)
    }
    for (i in 0 until segments) {
      val base = (i * 2).toShort()
      indices.add(base)
      indices.add((base + 1).toShort())
      indices.add((base + 2).toShort())
      indices.add((base + 1).toShort())
      indices.add((base + 3).toShort())
      indices.add((base + 2).toShort())
    }

    // Top cap
    val topCenterIdx = (verts.size / 8).toShort()
    pushVert(0f, 0.5f, 0f, 0f, 1f, 0f, 0.5f, 0.5f)
    for (i in 0..segments) {
      val a = (i.toFloat() / segments) * (2f * PI.toFloat())
      val ca = cos(a)
      val sa = sin(a)
      pushVert(ca * radiusTop, 0.5f, sa * radiusTop, 0f, 1f, 0f, 0.5f + ca * 0.5f, 0.5f + sa * 0.5f)
    }
    for (i in 0 until segments) {
      indices.add(topCenterIdx)
      indices.add((topCenterIdx + 1 + i + 1).toShort())
      indices.add((topCenterIdx + 1 + i).toShort())
    }

    // Bottom cap
    val botCenterIdx = (verts.size / 8).toShort()
    pushVert(0f, -0.5f, 0f, 0f, -1f, 0f, 0.5f, 0.5f)
    for (i in 0..segments) {
      val a = (i.toFloat() / segments) * (2f * PI.toFloat())
      val ca = cos(a)
      val sa = sin(a)
      pushVert(
        ca * radiusBottom,
        -0.5f,
        sa * radiusBottom,
        0f,
        -1f,
        0f,
        0.5f + ca * 0.5f,
        0.5f + sa * 0.5f,
      )
    }
    for (i in 0 until segments) {
      indices.add(botCenterIdx)
      indices.add((botCenterIdx + 1 + i).toShort())
      indices.add((botCenterIdx + 1 + i + 1).toShort())
    }

    return Mesh(verts.toFloatArray(), indices.toShortArray())
  }

  fun createSphere(rings: Int = 14, sectors: Int = 22, inverted: Boolean = false): Mesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Short>()
    val sign = if (inverted) -1f else 1f

    for (r in 0..rings) {
      val v = r.toFloat() / rings
      val phi = v * PI.toFloat()
      val y = cos(phi)
      val sinPhi = sin(phi)
      for (s in 0..sectors) {
        val u = s.toFloat() / sectors
        val theta = u * 2f * PI.toFloat()
        val x = sinPhi * cos(theta)
        val z = sinPhi * sin(theta)
        verts.add(x * 0.5f)
        verts.add(y * 0.5f)
        verts.add(z * 0.5f)
        verts.add(x * sign)
        verts.add(y * sign)
        verts.add(z * sign)
        verts.add(u)
        verts.add(v)
      }
    }

    val stride = sectors + 1
    for (r in 0 until rings) {
      for (s in 0 until sectors) {
        val i0 = (r * stride + s).toShort()
        val i1 = (i0 + 1).toShort()
        val i2 = ((r + 1) * stride + s).toShort()
        val i3 = (i2 + 1).toShort()
        if (inverted) {
          indices.add(i0); indices.add(i1); indices.add(i2)
          indices.add(i1); indices.add(i3); indices.add(i2)
        } else {
          indices.add(i0); indices.add(i2); indices.add(i1)
          indices.add(i1); indices.add(i2); indices.add(i3)
        }
      }
    }
    return Mesh(verts.toFloatArray(), indices.toShortArray())
  }

  /**
   * Curved, gracefully drooping palm frond blade extending along +Z from origin (0,0,0) to ~2.8m.
   */
  fun createPalmFrond(): Mesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Short>()
    val segs = 7

    for (i in 0..segs) {
      val t = i.toFloat() / segs
      val z = t * 2.9f
      val y = sin(t * PI.toFloat() * 0.8f) * 0.45f - t * t * 1.15f
      val halfW = (0.48f * sin(t * PI.toFloat() * 0.92f + 0.12f)).coerceAtLeast(0.04f)
      val edgeDroop = halfW * 0.35f

      // Left edge, spine center, right edge (V-folded leaflet ridge!)
      verts.addAll(listOf(-halfW, y - edgeDroop, z, -0.35f, 0.9f, 0f, 0f, t))
      verts.addAll(listOf(0f, y, z, 0f, 1f, 0f, 0.5f, t))
      verts.addAll(listOf(halfW, y - edgeDroop, z, 0.35f, 0.9f, 0f, 1f, t))
    }

    for (i in 0 until segs) {
      val r0 = (i * 3).toShort()
      val r1 = ((i + 1) * 3).toShort()
      // Left quad
      indices.add(r0); indices.add((r0 + 1).toShort()); indices.add(r1)
      indices.add((r0 + 1).toShort()); indices.add((r1 + 1).toShort()); indices.add(r1)
      // Right quad
      indices.add((r0 + 1).toShort()); indices.add((r0 + 2).toShort()); indices.add((r1 + 1).toShort())
      indices.add((r0 + 2).toShort()); indices.add((r1 + 2).toShort()); indices.add((r1 + 1).toShort())
    }
    return Mesh(verts.toFloatArray(), indices.toShortArray())
  }

  fun createWaterPlane(size: Float = 440f, grid: Int = 36): Mesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Short>()
    val half = size * 0.5f
    for (r in 0..grid) {
      val v = r.toFloat() / grid
      val z = -half + v * size
      for (c in 0..grid) {
        val u = c.toFloat() / grid
        val x = -half + u * size
        verts.addAll(listOf(x, 0f, z, 0f, 1f, 0f, u * 40f, v * 40f))
      }
    }
    val stride = grid + 1
    for (r in 0 until grid) {
      for (c in 0 until grid) {
        val i0 = (r * stride + c).toShort()
        val i1 = (i0 + 1).toShort()
        val i2 = ((r + 1) * stride + c).toShort()
        val i3 = (i2 + 1).toShort()
        indices.add(i0); indices.add(i2); indices.add(i1)
        indices.add(i1); indices.add(i2); indices.add(i3)
      }
    }
    return Mesh(verts.toFloatArray(), indices.toShortArray())
  }

  /**
   * Sculpted wedge-shaped sports car lower body shell (length ~4.2m along Z, width ~1.9m along X).
   */
  fun createSportsCarBody(): Mesh {
    // Build a clean faceted aerodynamic body using extruded profile along X
    val profile =
      arrayOf(
        // (z, yBottom, yTop) from rear (-2.15) to front (+2.15)
        floatArrayOf(-2.12f, 0.24f, 0.96f), // Rear bumper / deck
        floatArrayOf(-1.20f, 0.18f, 1.02f), // Rear fenders
        floatArrayOf(-0.10f, 0.16f, 0.94f), // Mid waist
        floatArrayOf(1.15f, 0.16f, 0.86f), // Front hood slope
        floatArrayOf(2.08f, 0.20f, 0.62f), // Low nose cone
      )

    val verts = ArrayList<Float>()
    val indices = ArrayList<Short>()
    val halfW = 0.94f

    fun addQuad(
      x0: Float, y0: Float, z0: Float,
      x1: Float, y1: Float, z1: Float,
      x2: Float, y2: Float, z2: Float,
      x3: Float, y3: Float, z3: Float,
    ) {
      val ux = x1 - x0; val uy = y1 - y0; val uz = z1 - z0
      val vx = x2 - x0; val vy = y2 - y0; val vz = z2 - z0
      var nx = uy * vz - uz * vy
      var ny = uz * vx - ux * vz
      var nz = ux * vy - uy * vx
      val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-5f)
      nx /= len; ny /= len; nz /= len
      val base = (verts.size / 8).toShort()
      verts.addAll(listOf(x0, y0, z0, nx, ny, nz, 0f, 0f))
      verts.addAll(listOf(x1, y1, z1, nx, ny, nz, 1f, 0f))
      verts.addAll(listOf(x2, y2, z2, nx, ny, nz, 1f, 1f))
      verts.addAll(listOf(x3, y3, z3, nx, ny, nz, 0f, 1f))
      indices.add(base); indices.add((base + 1).toShort()); indices.add((base + 2).toShort())
      indices.add(base); indices.add((base + 2).toShort()); indices.add((base + 3).toShort())
    }

    for (i in 0 until profile.size - 1) {
      val (z0, b0, t0) = profile[i]
      val (z1, b1, t1) = profile[i + 1]
      // Top deck
      addQuad(-halfW, t0, z0, -halfW, t1, z1, halfW, t1, z1, halfW, t0, z0)
      // Bottom underbody
      addQuad(-halfW, b0, z0, halfW, b0, z0, halfW, b1, z1, -halfW, b1, z1)
      // Right side (+X)
      addQuad(halfW, b0, z0, halfW, t0, z0, halfW, t1, z1, halfW, b1, z1)
      // Left side (-X)
      addQuad(-halfW, b1, z1, -halfW, t1, z1, -halfW, t0, z0, -halfW, b0, z0)
    }

    // Front face & Rear face
    val (zf, bf, tf) = profile.last()
    addQuad(-halfW, bf, zf, halfW, bf, zf, halfW, tf, zf, -halfW, tf, zf)
    val (zr, br, tr) = profile.first()
    addQuad(halfW, br, zr, -halfW, br, zr, -halfW, tr, zr, halfW, tr, zr)

    return Mesh(verts.toFloatArray(), indices.toShortArray())
  }

  /**
   * Sleek pointed V-hull speedboat shell (~5.2m along Z, width ~2.1m along X).
   */
  fun createBoatHull(): Mesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Short>()

    fun addQuad(
      x0: Float, y0: Float, z0: Float,
      x1: Float, y1: Float, z1: Float,
      x2: Float, y2: Float, z2: Float,
      x3: Float, y3: Float, z3: Float,
    ) {
      val ux = x1 - x0; val uy = y1 - y0; val uz = z1 - z0
      val vx = x2 - x0; val vy = y2 - y0; val vz = z2 - z0
      var nx = uy * vz - uz * vy
      var ny = uz * vx - ux * vz
      var nz = ux * vy - uy * vx
      val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-5f)
      nx /= len; ny /= len; nz /= len
      val base = (verts.size / 8).toShort()
      verts.addAll(listOf(x0, y0, z0, nx, ny, nz, 0f, 0f))
      verts.addAll(listOf(x1, y1, z1, nx, ny, nz, 1f, 0f))
      verts.addAll(listOf(x2, y2, z2, nx, ny, nz, 1f, 1f))
      verts.addAll(listOf(x3, y3, z3, nx, ny, nz, 0f, 1f))
      indices.add(base); indices.add((base + 1).toShort()); indices.add((base + 2).toShort())
      indices.add(base); indices.add((base + 2).toShort()); indices.add((base + 3).toShort())
    }

    // Hull cross-sections: (z, keelY, chineY, deckY, halfWidth)
    val sections =
      arrayOf(
        floatArrayOf(-2.45f, -0.32f, 0.08f, 0.68f, 1.02f), // Stern transom
        floatArrayOf(-0.40f, -0.36f, 0.04f, 0.72f, 1.08f), // Mid cockpit
        floatArrayOf(1.20f, -0.30f, 0.12f, 0.78f, 0.88f),  // Fore deck taper
        floatArrayOf(2.65f, -0.05f, 0.34f, 0.86f, 0.10f),  // Sharp pointed bow
      )

    for (i in 0 until sections.size - 1) {
      val (z0, k0, c0, d0, w0) = sections[i]
      val (z1, k1, c1, d1, w1) = sections[i + 1]
      val cw0 = w0 * 0.76f
      val cw1 = w1 * 0.76f

      // Right keel -> chine -> gunwale
      addQuad(0f, k0, z0, cw0, c0, z0, cw1, c1, z1, 0f, k1, z1)
      addQuad(cw0, c0, z0, w0, d0, z0, w1, d1, z1, cw1, c1, z1)
      // Left keel -> chine -> gunwale
      addQuad(-cw0, c0, z0, 0f, k0, z0, 0f, k1, z1, -cw1, c1, z1)
      addQuad(-w0, d0, z0, -cw0, c0, z0, -cw1, c1, z1, -w1, d1, z1)
      // Top deck (for bow section fully covered; cockpit section left recessed via lower floor box)
      if (i >= 1) {
        addQuad(-w0, d0, z0, -w1, d1, z1, w1, d1, z1, w0, d0, z0)
      }
    }

    // Stern transom cap
    val (zs, ks, cs, ds, ws) = sections.first()
    val cws = ws * 0.76f
    addQuad(-ws, ds, zs, -cws, cs, zs, 0f, ks, zs, 0f, ds, zs)
    addQuad(0f, ds, zs, 0f, ks, zs, cws, cs, zs, ws, ds, zs)

    return Mesh(verts.toFloatArray(), indices.toShortArray())
  }
}
