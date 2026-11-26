package com.example.game.math

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class AabbCollider(
  val minX: Float,
  val minZ: Float,
  val maxX: Float,
  val maxZ: Float,
  val minY: Float = 0f,
  val maxY: Float = 6f,
  val tag: String = "",
)

data class CircleCollider(
  val x: Float,
  val z: Float,
  val radius: Float,
  val tag: String = "",
)

object CollisionSolver {
  fun resolveCircleVsAabb(
    cx: Float,
    cz: Float,
    radius: Float,
    box: AabbCollider,
  ): Pair<Float, Float>? {
    val closestX = max(box.minX, min(cx, box.maxX))
    val closestZ = max(box.minZ, min(cz, box.maxZ))
    val dx = cx - closestX
    val dz = cz - closestZ
    val distSq = dx * dx + dz * dz
    if (distSq >= radius * radius) return null

    if (distSq > 1e-6f) {
      val dist = sqrt(distSq)
      val push = radius - dist
      return Pair((dx / dist) * push, (dz / dist) * push)
    }

    val leftPen = cx - box.minX
    val rightPen = box.maxX - cx
    val topPen = cz - box.minZ
    val bottomPen = box.maxZ - cz
    val minPen = min(min(leftPen, rightPen), min(topPen, bottomPen))
    return when (minPen) {
      leftPen -> Pair(-(leftPen + radius), 0f)
      rightPen -> Pair(rightPen + radius, 0f)
      topPen -> Pair(0f, -(topPen + radius))
      else -> Pair(0f, bottomPen + radius)
    }
  }

  fun resolveCircleVsCircle(
    x1: Float,
    z1: Float,
    r1: Float,
    x2: Float,
    z2: Float,
    r2: Float,
  ): Pair<Float, Float>? {
    val dx = x1 - x2
    val dz = z1 - z2
    val minDist = r1 + r2
    val distSq = dx * dx + dz * dz
    if (distSq >= minDist * minDist) return null
    val dist = sqrt(max(1e-6f, distSq))
    val push = minDist - dist
    return if (dist > 1e-4f) {
      Pair((dx / dist) * push, (dz / dist) * push)
    } else {
      Pair(minDist, 0f)
    }
  }

  fun resolveAll(
    x: Float,
    z: Float,
    radius: Float,
    aabbs: List<AabbCollider>,
    circles: List<CircleCollider>,
    entityMinY: Float = 0f,
    entityMaxY: Float = 1.8f,
  ): Triple<Float, Float, Boolean> {
    var rx = x
    var rz = z
    var hit = false
    repeat(2) {
      for (box in aabbs) {
        if (box.maxY <= entityMinY + 0.38f || box.minY >= entityMaxY) continue
        val push = resolveCircleVsAabb(rx, rz, radius, box)
        if (push != null) {
          rx += push.first
          rz += push.second
          hit = true
        }
      }
      for (c in circles) {
        if (entityMinY >= 3.1f) continue // ground trees & streetlights do not block 2nd floor roaming
        val push = resolveCircleVsCircle(rx, rz, radius, c.x, c.z, c.radius)
        if (push != null) {
          rx += push.first
          rz += push.second
          hit = true
        }
      }
    }
    return Triple(rx, rz, hit)
  }

  fun distXZ(x1: Float, z1: Float, x2: Float, z2: Float): Float {
    val dx = x1 - x2
    val dz = z1 - z2
    return sqrt(dx * dx + dz * dz)
  }

  private fun distToSegment(px: Float, pz: Float, x1: Float, z1: Float, x2: Float, z2: Float): Float {
    val dx = x2 - x1
    val dz = z2 - z1
    val lenSq = dx * dx + dz * dz
    if (lenSq < 1e-4f) return sqrt((px - x1) * (px - x1) + (pz - z1) * (pz - z1))
    val t = (((px - x1) * dx + (pz - z1) * dz) / lenSq).coerceIn(0f, 1f)
    val cx = x1 + t * dx
    val cz = z1 + t * dz
    return sqrt((px - cx) * (px - cx) + (pz - cz) * (pz - cz))
  }

  fun isOnBridge(x: Float, z: Float): Boolean {
    val onTurbineRedBridge = abs(x + 170f) <= 5.4f && z in -38f..46f
    val onIntellectSouthBridge = abs(x + 16f) <= 5.4f && z in -132f..-84f
    val onIntellectSwBridge = distToSegment(x, z, -96f, -92f, -58f, -142f) <= 5.2f
    val onIntellectEastBridge = distToSegment(x, z, 66f, -148f, 24f, -162f) <= 5.2f
    return onTurbineRedBridge || onIntellectSouthBridge || onIntellectSwBridge || onIntellectEastBridge
  }

  fun isOnPierOrDock(x: Float, z: Float): Boolean {
    val marinaPier = abs(x + 198f) <= 3.0f && z in 62f..92.5f
    val marinaDock = x in -206.5f..-189.5f && z in 87.5f..93.0f
    return marinaPier || marinaDock
  }

  fun isOnLand(x: Float, z: Float): Boolean {
    if (isOnBridge(x, z)) return true
    return com.example.game.world.TerrainHeightfield.signedCoastDistance(x, z) < 1.0f
  }

  fun isInDeepWater(x: Float, z: Float): Boolean {
    if (isOnLand(x, z) || isOnPierOrDock(x, z)) return false
    return true
  }

  fun clampCarToIsland(x: Float, z: Float, margin: Float = 2.0f): Pair<Float, Float> {
    var cx = x.coerceIn(-296f, 296f)
    var cz = z.coerceIn(-296f, 296f)
    if (isOnBridge(cx, cz)) return Pair(cx, cz)
    val dCoast = com.example.game.world.TerrainHeightfield.signedCoastDistance(cx, cz)
    if (dCoast <= -margin * 0.4f) return Pair(cx, cz)

    val eps = 0.8f
    repeat(4) {
      val d = com.example.game.world.TerrainHeightfield.signedCoastDistance(cx, cz)
      if (d <= -margin * 0.3f || isOnBridge(cx, cz)) return Pair(cx, cz)
      val gx =
        com.example.game.world.TerrainHeightfield.signedCoastDistance(cx + eps, cz) -
          com.example.game.world.TerrainHeightfield.signedCoastDistance(cx - eps, cz)
      val gz =
        com.example.game.world.TerrainHeightfield.signedCoastDistance(cx, cz + eps) -
          com.example.game.world.TerrainHeightfield.signedCoastDistance(cx, cz - eps)
      val gLen = sqrt(gx * gx + gz * gz).coerceAtLeast(1e-4f)
      val step = (d + margin * 0.5f).coerceIn(0.4f, 6.0f)
      cx -= (gx / gLen) * step
      cz -= (gz / gLen) * step
    }
    return Pair(cx, cz)
  }

  fun clampSwimmerToWorld(x: Float, z: Float): Pair<Float, Float> =
    Pair(x.coerceIn(-340f, 340f), z.coerceIn(-340f, 340f))

  fun resolveBoatWaterBounds(x: Float, z: Float): Triple<Float, Float, Boolean> {
    var bx = x.coerceIn(-344f, 344f)
    var bz = z.coerceIn(-344f, 344f)
    var hit = (bx != x || bz != z)

    val pierBoxes =
      listOf(
        AabbCollider(-201f, 62f, -195f, 92.8f, tag = "Nextera Marina Pier"),
        AabbCollider(-206.8f, 87.2f, -189.2f, 93.2f, tag = "Nextera Marina Dock"),
      )
    for (box in pierBoxes) {
      resolveCircleVsAabb(bx, bz, 1.9f, box)?.let { push ->
        bx += push.first
        bz += push.second
        hit = true
      }
    }

    val eps = 0.9f
    repeat(3) {
      val dCoast = com.example.game.world.TerrainHeightfield.signedCoastDistance(bx, bz)
      if (dCoast < 2.2f) {
        val gx =
          com.example.game.world.TerrainHeightfield.signedCoastDistance(bx + eps, bz) -
            com.example.game.world.TerrainHeightfield.signedCoastDistance(bx - eps, bz)
        val gz =
          com.example.game.world.TerrainHeightfield.signedCoastDistance(bx, bz + eps) -
            com.example.game.world.TerrainHeightfield.signedCoastDistance(bx, bz - eps)
        val gLen = sqrt(gx * gx + gz * gz).coerceAtLeast(1e-4f)
        val push = (2.4f - dCoast).coerceIn(0.4f, 5.5f)
        bx += (gx / gLen) * push
        bz += (gz / gLen) * push
        hit = true
      }
    }

    return Triple(bx, bz, hit)
  }
}
