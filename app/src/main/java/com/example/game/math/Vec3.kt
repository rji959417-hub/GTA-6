package com.example.game.math

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f) {
  operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)

  operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)

  operator fun times(s: Float) = Vec3(x * s, y * s, z * s)

  operator fun div(s: Float) = Vec3(x / s, y / s, z / s)

  fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z

  fun cross(o: Vec3): Vec3 =
    Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)

  fun lengthSq(): Float = x * x + y * y + z * z

  fun length(): Float = sqrt(lengthSq())

  fun normalized(): Vec3 {
    val len = length()
    return if (len > 1e-6f) Vec3(x / len, y / len, z / len) else Vec3(0f, 0f, 0f)
  }

  companion object {
    val ZERO = Vec3(0f, 0f, 0f)
    val UP = Vec3(0f, 1f, 0f)

    fun lerp(a: Vec3, b: Vec3, t: Float): Vec3 =
      Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t)

    fun fromYaw(yawRad: Float): Vec3 = Vec3(sin(yawRad), 0f, cos(yawRad))
  }
}

fun normalizeAngleRad(angle: Float): Float {
  var a = angle
  while (a > Math.PI) a -= (2.0 * Math.PI).toFloat()
  while (a < -Math.PI) a += (2.0 * Math.PI).toFloat()
  return a
}

fun lerpAngleRad(from: Float, to: Float, t: Float): Float {
  val diff = normalizeAngleRad(to - from)
  return from + diff * t
}

fun angleBetweenXZ(dx: Float, dz: Float): Float = atan2(dx, dz)
