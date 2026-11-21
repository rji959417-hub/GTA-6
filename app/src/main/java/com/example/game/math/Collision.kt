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
  ): Triple<Float, Float, Boolean> {
    var rx = x
    var rz = z
    var hit = false
    repeat(2) {
      for (box in aabbs) {
        val push = resolveCircleVsAabb(rx, rz, radius, box)
        if (push != null) {
          rx += push.first
          rz += push.second
          hit = true
        }
      }
      for (c in circles) {
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

  fun isOnPierOrDock(x: Float, z: Float): Boolean {
    val onMainPier = abs(x - 22f) <= 2.6f && z in 53.5f..84.5f
    val onTDock = x in 15.7f..28.3f && z in 80.2f..84.8f
    return onMainPier || onTDock
  }

  fun isInDeepWater(x: Float, z: Float): Boolean {
    if (isOnPierOrDock(x, z)) return false
    return abs(x) > 64.5f || z < -64.5f || z > 65.2f
  }

  fun clampCarToIsland(x: Float, z: Float, margin: Float = 2.0f): Pair<Float, Float> {
    val cx = x.coerceIn(-63.5f + margin, 63.5f - margin)
    val cz = z.coerceIn(-63.5f + margin, 64.5f - margin)
    return Pair(cx, cz)
  }

  fun clampSwimmerToWorld(x: Float, z: Float): Pair<Float, Float> =
    Pair(x.coerceIn(-165f, 165f), z.coerceIn(-165f, 165f))

  fun resolveBoatWaterBounds(x: Float, z: Float): Triple<Float, Float, Boolean> {
    var bx = x.coerceIn(-168f, 168f)
    var bz = z.coerceIn(-168f, 168f)
    var hit = (bx != x || bz != z)

    // Keep boats outside the island shoreline (-67..67, -67..67.5)
    val islandBox = AabbCollider(-67.0f, -67.0f, 67.0f, 67.5f, tag = "Island Shore")
    resolveCircleVsAabb(bx, bz, 2.0f, islandBox)?.let { push ->
      bx += push.first
      bz += push.second
      hit = true
    }

    // Keep boats from clipping through the wooden pier and T-dock
    val pierBox = AabbCollider(19.3f, 54.0f, 24.7f, 84.8f, tag = "Ocean Pier")
    resolveCircleVsAabb(bx, bz, 1.8f, pierBox)?.let { push ->
      bx += push.first
      bz += push.second
      hit = true
    }
    val dockBox = AabbCollider(15.7f, 80.2f, 28.3f, 84.8f, tag = "Boat Dock")
    resolveCircleVsAabb(bx, bz, 1.8f, dockBox)?.let { push ->
      bx += push.first
      bz += push.second
      hit = true
    }

    return Triple(bx, bz, hit)
  }
}
