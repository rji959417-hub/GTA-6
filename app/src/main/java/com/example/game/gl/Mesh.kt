package com.example.game.gl

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Interleaved VBO/VAO mesh with layout:
 * location 0: vec3 position
 * location 1: vec3 normal
 * location 2: vec2 texcoord
 */
class Mesh(vertices: FloatArray, indices: ShortArray) {
  private val vaoId: Int
  private val vboId: Int
  private val eboId: Int
  private val indexCount: Int = indices.size

  init {
    val vaos = IntArray(1)
    val vbos = IntArray(1)
    val ebos = IntArray(1)
    GLES30.glGenVertexArrays(1, vaos, 0)
    GLES30.glGenBuffers(1, vbos, 0)
    GLES30.glGenBuffers(1, ebos, 0)

    vaoId = vaos[0]
    vboId = vbos[0]
    eboId = ebos[0]

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

    GLES30.glBindVertexArray(vaoId)

    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
    GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, vBuf, GLES30.GL_STATIC_DRAW)

    GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, eboId)
    GLES30.glBufferData(
      GLES30.GL_ELEMENT_ARRAY_BUFFER,
      indices.size * 2,
      iBuf,
      GLES30.GL_STATIC_DRAW,
    )

    val stride = 8 * 4
    GLES30.glEnableVertexAttribArray(0)
    GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
    GLES30.glEnableVertexAttribArray(1)
    GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 3 * 4)
    GLES30.glEnableVertexAttribArray(2)
    GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, stride, 6 * 4)

    GLES30.glBindVertexArray(0)
  }

  fun draw() {
    GLES30.glBindVertexArray(vaoId)
    GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, 0)
    GLES30.glBindVertexArray(0)
  }

  fun release() {
    GLES30.glDeleteVertexArrays(1, intArrayOf(vaoId), 0)
    GLES30.glDeleteBuffers(1, intArrayOf(vboId), 0)
    GLES30.glDeleteBuffers(1, intArrayOf(eboId), 0)
  }
}
