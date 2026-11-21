package com.example.game.math

import android.opengl.Matrix

class Mat4(val data: FloatArray = FloatArray(16)) {
  init {
    if (data.all { it == 0f }) {
      Matrix.setIdentityM(data, 0)
    }
  }

  fun setIdentity(): Mat4 {
    Matrix.setIdentityM(data, 0)
    return this
  }

  fun translate(x: Float, y: Float, z: Float): Mat4 {
    Matrix.translateM(data, 0, x, y, z)
    return this
  }

  fun rotateY(deg: Float): Mat4 {
    Matrix.rotateM(data, 0, deg, 0f, 1f, 0f)
    return this
  }

  fun rotateX(deg: Float): Mat4 {
    Matrix.rotateM(data, 0, deg, 1f, 0f, 0f)
    return this
  }

  fun rotateZ(deg: Float): Mat4 {
    Matrix.rotateM(data, 0, deg, 0f, 0f, 1f)
    return this
  }

  fun scale(x: Float, y: Float, z: Float): Mat4 {
    Matrix.scaleM(data, 0, x, y, z)
    return this
  }

  fun copyFrom(other: Mat4): Mat4 {
    System.arraycopy(other.data, 0, data, 0, 16)
    return this
  }

  companion object {
    fun multiply(out: FloatArray, lhs: FloatArray, rhs: FloatArray) {
      Matrix.multiplyMM(out, 0, lhs, 0, rhs, 0)
    }
  }
}
