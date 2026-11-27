package com.example

import com.example.game.gl.GlbGpuModel
import com.example.game.gl.GlbLoader
import java.io.ByteArrayOutputStream
import java.io.File
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
      rawMaxY = 6.0f,
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

  @Test
  fun realCyberpunkCharacterGlbStandsUprightFacesForwardAndAnimatesRunStride() {
    val file =
      listOf(
        File("public/assets/aistudio/cyberpunk_character.glb"),
        File("../public/assets/aistudio/cyberpunk_character.glb"),
      ).firstOrNull { it.isFile } ?: return

    val parsed = GlbLoader.parseGlbBytes(file.readBytes(), targetHeightMeters = 1.76f)

    // 1. Stands upright on +Y (not lying flat along Z!) and sits flush on y = 0
    assertEquals(0f, parsed.normalizedBounds.minY, 0.01f)
    assertEquals(1.76f, parsed.normalizedBounds.maxY, 0.01f)
    assertTrue("Height must clearly exceed Z depth so character stands upright", parsed.normalizedBounds.height > parsed.normalizedBounds.depth * 2.0f)
    assertTrue("Character must be rotated +Z forward", parsed.rotated180Y)

    // 2. Humanoid locomotion bones detected
    assertTrue(parsed.rig.hasLocomotionRig)
    assertTrue(parsed.rig.hips >= 0)
    assertTrue(parsed.rig.leftThigh >= 0 && parsed.rig.rightThigh >= 0)
    assertTrue(parsed.rig.leftArm >= 0 && parsed.rig.rightArm >= 0)

    // 3. Toes face +Z in bind pose after normalization
    val lFootZ = parsed.nodes[parsed.rig.leftFoot].bindPosZ
    val lToesZ = parsed.nodes[parsed.rig.leftToes].bindPosZ
    assertTrue("Toes ($lToesZ) should point +Z ahead of heel ($lFootZ)", lToesZ > lFootZ + 0.05f)

    // 4. Running sprint pose swings left & right feet in opposite Z directions
    val ws = GlbLoader.PoseWorkspace(parsed)
    val runPose =
      GlbGpuModel.buildHumanoidPose(
        walkCycle = kotlin.math.PI.toFloat() * 0.5f,
        speedNorm = 1.25f,
        isSprinting = true,
        isSwimming = false,
        fallProgress = 0f,
        worldTimeSec = 1.0f,
      )
    ws.evaluatePose(runPose)
    val lFootRunZ = ws.nodeAnimMats[parsed.rig.leftFoot][14]
    val rFootRunZ = ws.nodeAnimMats[parsed.rig.rightFoot][14]
    assertTrue("Left foot should swing forward (+Z) and right foot backward (-Z) at walkCycle=PI/2: L=$lFootRunZ R=$rFootRunZ", lFootRunZ > rFootRunZ + 0.35f)
  }

  private fun buildSyntheticCharacterGlb(
    rawMinY: Float,
    rawMaxY: Float,
    nodeScale: Float,
    nodeTranslationY: Float,
  ): ByteArray {
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

    putIntLE(0x46546C67)
    putIntLE(2)
    putIntLE(totalLen)

    putIntLE(jsonPaddedLen)
    putIntLE(0x4E4F534A)
    out.write(jsonBytesRaw)
    repeat(jsonPaddedLen - jsonBytesRaw.size) { out.write(' '.code) }

    putIntLE(binPaddedLen)
    putIntLE(0x004E4942)
    out.write(binBytes)
    repeat(binPaddedLen - binBytes.size) { out.write(0) }

    return out.toByteArray()
  }
}
