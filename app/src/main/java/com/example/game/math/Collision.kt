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

  fun isOnBridge(x: Float, z: Float): Boolean {
    val onEastBridge = x in 61.5f..86.5f
    val onWestBridge = x in -86.5f..-61.5f
    if (!onEastBridge && !onWestBridge) return false
    return abs(z - 16f) <= 5.0f || abs(z + 24f) <= 5.0f
  }

  fun isOnPierOrDock(x: Float, z: Float): Boolean {
    val baysidePier = abs(x - 22f) <= 2.6f && z in 53.5f..84.5f
    val baysideDock = x in 15.7f..28.3f && z in 80.2f..84.8f
    val uptownPier = abs(x - 168f) <= 2.6f && z in 53.5f..84.5f
    val uptownDock = x in 161.7f..174.3f && z in 80.2f..84.8f
    return baysidePier || baysideDock || uptownPier || uptownDock
  }

  fun isOnLand(x: Float, z: Float): Boolean {
    val onIsland1 = x in -64.5f..64.5f && z in -64.5f..65.2f
    val onIsland2 = x in 83.5f..212.5f && z in -64.5f..65.2f // Green Valley Enclave (East)
    val onIsland3 = x in -212.5f..-83.5f && z in -64.5f..65.2f // Rooftop Town Street (West)
    return onIsland1 || onIsland2 || onIsland3 || isOnBridge(x, z)
  }

  fun isInDeepWater(x: Float, z: Float): Boolean {
    if (isOnLand(x, z) || isOnPierOrDock(x, z)) return false
    return true
  }

  fun clampCarToIsland(x: Float, z: Float, margin: Float = 2.0f): Pair<Float, Float> {
    val cz = z.coerceIn(-63.5f + margin, 64.5f - margin)
    val inSouthBridgeBand = abs(cz - 16f) <= 4.3f
    val inNorthBridgeBand = abs(cz + 24f) <= 4.3f

    // East Channel Bridges (x = 63.5..84.5)
    if (x in (63.5f - margin)..(84.5f + margin)) {
      if (inSouthBridgeBand) return Pair(x, cz.coerceIn(16f - 4.0f, 16f + 4.0f))
      if (inNorthBridgeBand) return Pair(x, cz.coerceIn(-24f - 4.0f, -24f + 4.0f))
      val clampedX = if (x < 74f) 63.5f - margin else 84.5f + margin
      return Pair(clampedX, cz)
    }

    // West Channel Bridges (x = -84.5..-63.5)
    if (x in (-84.5f - margin)..(-63.5f + margin)) {
      if (inSouthBridgeBand) return Pair(x, cz.coerceIn(16f - 4.0f, 16f + 4.0f))
      if (inNorthBridgeBand) return Pair(x, cz.coerceIn(-24f - 4.0f, -24f + 4.0f))
      val clampedX = if (x > -74f) -63.5f + margin else -84.5f - margin
      return Pair(clampedX, cz)
    }

    val cx =
      when {
        x < -74f -> x.coerceIn(-211.5f + margin, -84.5f - margin)
        x > 74f -> x.coerceIn(84.5f + margin, 211.5f - margin)
        else -> x.coerceIn(-63.5f + margin, 63.5f - margin)
      }
    return Pair(cx, cz)
  }

  fun clampSwimmerToWorld(x: Float, z: Float): Pair<Float, Float> =
    Pair(x.coerceIn(-300f, 300f), z.coerceIn(-160f, 160f))

  fun resolveBoatWaterBounds(x: Float, z: Float): Triple<Float, Float, Boolean> {
    var bx = x.coerceIn(-303f, 303f)
    var bz = z.coerceIn(-163f, 163f)
    var hit = (bx != x || bz != z)

    val shoreObstacles =
      listOf(
        // Island 1 (Key Bayside)
        AabbCollider(-67.0f, -67.0f, 67.0f, 67.5f, tag = "Key Bayside Shore"),
        // Island 2 (Green Valley Enclave East)
        AabbCollider(81.0f, -67.0f, 215.0f, 67.5f, tag = "Green Valley Shore"),
        // Island 3 (Rooftop Town Street West)
        AabbCollider(-215.0f, -67.0f, -81.0f, 67.5f, tag = "Rooftop Town Shore"),
        // East channel bridge spans
        AabbCollider(63.5f, 10.6f, 84.5f, 21.4f, tag = "East South Bridge"),
        AabbCollider(63.5f, -29.4f, 84.5f, -18.6f, tag = "East North Bridge"),
        // West channel bridge spans
        AabbCollider(-84.5f, 10.6f, -63.5f, 21.4f, tag = "West South Bridge"),
        AabbCollider(-84.5f, -29.4f, -63.5f, -18.6f, tag = "West North Bridge"),
        // Bayside Pier & T-Dock
        AabbCollider(19.3f, 54.0f, 24.7f, 84.8f, tag = "Bayside Pier"),
        AabbCollider(15.7f, 80.2f, 28.3f, 84.8f, tag = "Bayside Dock"),
        // Green Valley Pier & T-Dock
        AabbCollider(165.3f, 54.0f, 170.7f, 84.8f, tag = "Valley Pier"),
        AabbCollider(161.7f, 80.2f, 174.3f, 84.8f, tag = "Valley Dock"),
      )

    for (box in shoreObstacles) {
      resolveCircleVsAabb(bx, bz, 1.9f, box)?.let { push ->
        bx += push.first
        bz += push.second
        hit = true
      }
    }

    return Triple(bx, bz, hit)
  }
}
