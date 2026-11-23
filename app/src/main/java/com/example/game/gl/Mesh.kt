package com.example.game.gl

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Interleaved VBO/EBO mesh with layout:
 * location 0: vec3 position
 * location 1: vec3 normal
 * location 2: vec2 texcoord
 */
class Mesh(vertices: FloatArray, indices: ShortArray) {
  private val vboId: Int
  private val eboId: Int
  private val indexCount: Int = indices.size

  init {
    val bufs = IntArray(2)
    GLES20.glGenBuffers(2, bufs, 0)
    vboId = bufs[0]
    eboId = bufs[1]

    val vBuf =
      ByteBuffer.allocateDirect(vertices.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .put(vertices)
    vBuf.position(0)

    val iBuf =
      ByteBuffer.allocateDirect(indices.size * 2)
        .order(ByteOrder.nativeOrder())
        .asShortBuffer()
        .put(indices)
    iBuf.position(0)

    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
    GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.size * 4, vBuf, GLES20.GL_STATIC_DRAW)

    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, eboId)
    GLES20.glBufferData(
      GLES20.GL_ELEMENT_ARRAY_BUFFER,
      indices.size * 2,
      iBuf,
      GLES20.GL_STATIC_DRAW,
    )

    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
  }

  fun draw() {
    if (vboId == 0 || eboId == 0 || indexCount <= 0) return
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
    val stride = 8 * 4
    GLES20.glEnableVertexAttribArray(0)
    GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, stride, 0)
    GLES20.glEnableVertexAttribArray(1)
    GLES20.glVertexAttribPointer(1, 3, GLES20.GL_FLOAT, false, stride, 3 * 4)
    GLES20.glEnableVertexAttribArray(2)
    GLES20.glVertexAttribPointer(2, 2, GLES20.GL_FLOAT, false, stride, 6 * 4)
    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, eboId)
    GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, 0)
  }

  fun release() {
    GLES20.glDeleteBuffers(2, intArrayOf(vboId, eboId), 0)
  }
}

