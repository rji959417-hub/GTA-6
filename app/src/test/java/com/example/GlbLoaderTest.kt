package com.example

import com.example.game.gl.GlbLoader
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlbLoaderTest {
  @Test
  fun parseGlbBytesNormalizesScaleAndGroundsCharacterToZeroY() {
    val glb = buildSyntheticCharacterGlb(
      rawMinY = -2.0f,
      rawMaxY = 6.0f, // raw height = 8.0 units -> with node scale 0.5 => 4.0 units -> normalized to 1.76m
      nodeScale = 0.5f,
      nodeTranslationY = 5.0f,
    )

    val parsed = GlbLoader.parseGlbBytes(glb, targetHeightMeters = 1.76f)

    assertEquals(1, parsed.parts.size)
    assertEquals(1, parsed.totalTriangles)
    assertEquals(0f, parsed.normalizedBounds.minY, 0.001f)
    assertEquals(1.76f, parsed.normalizedBounds.maxY, 0.001f)
    assertEquals(1.76f, parsed.normalizedBounds.height, 0.001f)
    assertEquals(1.76f / 4.0f, parsed.appliedScale, 0.001f)
    assertEquals(0f, (parsed.normalizedBounds.minX + parsed.normalizedBounds.maxX) * 0.5f, 0.001f)
    assertEquals(0f, (parsed.normalizedBounds.minZ + parsed.normalizedBounds.maxZ) * 0.5f, 0.001f)
    assertEquals(1, parsed.images.size)
    assertEquals(0, parsed.parts[0].material.baseColorImageIndex)
    assertTrue((parsed.images[0].encodedBytes?.size ?: 0) == 4)
  }

  private fun buildSyntheticCharacterGlb(
    rawMinY: Float,
    rawMaxY: Float,
    nodeScale: Float,
    nodeTranslationY: Float,
  ): ByteArray {
    // 3 vertices (pos vec3 + uv vec2) + 3 unsigned short indices + 4-byte dummy image payload
    val positions = floatArrayOf(
      1.0f, rawMinY, 2.0f,
      3.0f, rawMinY, 2.0f,
      2.0f, rawMaxY, 4.0f,
    )
    val uvs = floatArrayOf(
      0f, 0f,
      1f, 0f,
      0.5f, 1f,
    )
    val indices = shortArrayOf(0, 1, 2)
    val fakePng = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    val binBuf = ByteBuffer.allocate(36 + 24 + 8 + 4).order(ByteOrder.LITTLE_ENDIAN)
    val posOffset = 0
    for (f in positions) binBuf.putFloat(f)
    val uvOffset = binBuf.position()
    for (f in uvs) binBuf.putFloat(f)
    val idxOffset = binBuf.position()
    for (s in indices) binBuf.putShort(s)
    // Pad indices to 4-byte boundary (6 bytes -> +2 bytes)
    binBuf.putShort(0)
    val imgOffset = binBuf.position()
    binBuf.put(fakePng)
    val binBytes = binBuf.array().copyOf(binBuf.position())

    val json =
      """{
        "asset":{"version":"2.0"},
        "scene":0,
        "scenes":[{"nodes":[0]}],
        "nodes":[{"mesh":0,"translation":[0,$nodeTranslationY,0],"scale":[$nodeScale,$nodeScale,$nodeScale]}],
        "meshes":[{"primitives":[{"attributes":{"POSITION":0,"TEXCOORD_0":1},"indices":2,"material":0}]}],
        "materials":[{"pbrMetallicRoughness":{"baseColorFactor":[0.9,0.95,1.0,1.0],"baseColorTexture":{"index":0}}}],
        "textures":[{"source":0}],
        "images":[{"bufferView":3,"mimeType":"image/png"}],
        "bufferViews":[
          {"buffer":0,"byteOffset":$posOffset,"byteLength":36},
          {"buffer":0,"byteOffset":$uvOffset,"byteLength":24},
          {"buffer":0,"byteOffset":$idxOffset,"byteLength":6},
          {"buffer":0,"byteOffset":$imgOffset,"byteLength":4}
        ],
        "accessors":[
          {"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
          {"bufferView":1,"componentType":5126,"count":3,"type":"VEC2"},
          {"bufferView":2,"componentType":5123,"count":3,"type":"SCALAR"}
        ],
        "buffers":[{"byteLength":${binBytes.size}}]
      }""".trimIndent()

    val jsonBytesRaw = json.toByteArray(Charsets.UTF_8)
    val jsonPaddedLen = (jsonBytesRaw.size + 3) and 3.inv()
    val binPaddedLen = (binBytes.size + 3) and 3.inv()
    val totalLen = 12 + 8 + jsonPaddedLen + 8 + binPaddedLen

    val out = ByteArrayOutputStream(totalLen)
    fun putIntLE(v: Int) {
      out.write(v and 0xFF)
      out.write((v ushr 8) and 0xFF)
      out.write((v ushr 16) and 0xFF)
      out.write((v ushr 24) and 0xFF)
    }

    putIntLE(0x46546C67) // glTF
    putIntLE(2)
    putIntLE(totalLen)

    putIntLE(jsonPaddedLen)
    putIntLE(0x4E4F534A) // JSON
    out.write(jsonBytesRaw)
    repeat(jsonPaddedLen - jsonBytesRaw.size) { out.write(' '.code) }

    putIntLE(binPaddedLen)
    putIntLE(0x004E4942) // BIN\0
    out.write(binBytes)
    repeat(binPaddedLen - binBytes.size) { out.write(0) }

    return out.toByteArray()
  }
}
