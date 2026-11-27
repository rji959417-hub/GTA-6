package com.example

import com.example.game.gl.FbxAnimLoader
import com.example.game.gl.FbxAnimationClip
import com.example.game.gl.GlbCharacterPose
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FbxAnimLoaderTest {
  @Test
  fun stripMixamoPrefixCleansAllCommonVariants() {
    assertEquals("Hips", FbxAnimLoader.stripMixamoPrefix("Model::mixamorig:Hips"))
    assertEquals("LeftUpLeg", FbxAnimLoader.stripMixamoPrefix("mixamorig:LeftUpLeg"))
    assertEquals("Spine1", FbxAnimLoader.stripMixamoPrefix("mixamorigSpine1\u0000Model"))
    assertEquals("RightFoot", FbxAnimLoader.stripMixamoPrefix("RightFoot"))
  }

  @Test
  fun parseFbxBytesExtractsAnimationClipAndSamplesRunningStride() {
    val fbxBytes = buildSyntheticMixamoRunFbx()
    val clip = FbxAnimLoader.parseFbxBytes(fbxBytes, fallbackName = "Running (1)")

    assertEquals("Running (1)", clip.name)
    assertEquals(1.0f, clip.durationSec, 0.01f)
    assertTrue("mixamorig prefix must be stripped", clip.bones.containsKey("hips"))
    assertTrue("mixamorig prefix must be stripped", clip.bones.containsKey("leftupleg"))
    assertTrue("mixamorig prefix must be stripped", clip.bones.containsKey("rightupleg"))
    assertNotNull(clip.bones["leftupleg"]?.rotCurveX)
    assertNotNull(clip.bones["rightupleg"]?.rotCurveX)

    val startPose = clip.sampleAsHumanPose(0.0f)
    val midPose = clip.sampleAsHumanPose(0.5f)

    assertTrue(
      "At t=0s left thigh (${startPose.leftThighPitchDeg}) and right thigh (${startPose.rightThighPitchDeg}) must swing opposite",
      startPose.leftThighPitchDeg < -20f && startPose.rightThighPitchDeg > 20f,
    )
    assertTrue(
      "At t=0.5s left thigh (${midPose.leftThighPitchDeg}) and right thigh (${midPose.rightThighPitchDeg}) must reverse stride",
      midPose.leftThighPitchDeg > 20f && midPose.rightThighPitchDeg < -20f,
    )
    assertTrue("Stride bounce must be positive during running swing", startPose.pelvisBounceY > 0.01f)
  }

  @Test
  fun lerpPoseBlendsSmoothlyBetweenIdleAndFbxRun() {
    val idle = GlbCharacterPose()
    val run = GlbCharacterPose(
      pelvisBounceY = 0.04f,
      torsoLeanDeg = 14f,
      leftThighPitchDeg = -40f,
      rightThighPitchDeg = 36f,
    )

    val atZero = FbxAnimationClip.lerpPose(idle, run, 0f)
    val atHalf = FbxAnimationClip.lerpPose(idle, run, 0.5f)
    val atOne = FbxAnimationClip.lerpPose(idle, run, 1f)

    assertEquals(0f, atZero.leftThighPitchDeg, 0.001f)
    assertEquals(-20f, atHalf.leftThighPitchDeg, 0.001f)
    assertEquals(18f, atHalf.rightThighPitchDeg, 0.001f)
    assertEquals(0.02f, atHalf.pelvisBounceY, 0.0001f)
    assertEquals(36f, atOne.rightThighPitchDeg, 0.001f)
  }

  private fun buildSyntheticMixamoRunFbx(): ByteArray {
    val out = ByteArrayOutputStream()
    // Kaydara FBX Binary header (27 bytes total, version 7400 -> 32-bit record headers)
    out.write("Kaydara FBX Binary  \u0000".toByteArray(Charsets.US_ASCII))
    out.write(byteArrayOf(0x1A, 0x00))
    out.write(intLe(7400))

    val kTimeSec = 46186158000L
    val idHips = 100L
    val idLeftThigh = 101L
    val idRightThigh = 102L
    val idStack = 200L
    val idCnLeft = 301L
    val idCnRight = 302L
    val idCurveLeftX = 401L
    val idCurveRightX = 402L

    val times = longArrayOf(0L, kTimeSec / 2L, kTimeSec)
    val leftVals = floatArrayOf(-38f, 36f, -38f)
    val rightVals = floatArrayOf(36f, -38f, 36f)

    val objectsChildren = listOf(
      node(
        "Model",
        props = listOf(propLong(idHips), propStr("Model::mixamorig:Hips"), propStr("LimbNode")),
      ),
      node(
        "Model",
        props = listOf(propLong(idLeftThigh), propStr("Model::mixamorig:LeftUpLeg"), propStr("LimbNode")),
      ),
      node(
        "Model",
        props = listOf(propLong(idRightThigh), propStr("Model::mixamorig:RightUpLeg"), propStr("LimbNode")),
      ),
      node(
        "AnimationStack",
        props = listOf(propLong(idStack), propStr("AnimStack::mixamo.com"), propStr("")),
        children = listOf(
          node(
            "Properties70",
            children = listOf(
              node(
                "P",
                props = listOf(
                  propStr("LocalStop"),
                  propStr("KTime"),
                  propStr("Time"),
                  propStr(""),
                  propLong(kTimeSec),
                ),
              ),
            ),
          ),
        ),
      ),
      node(
        "AnimationCurveNode",
        props = listOf(propLong(idCnLeft), propStr("AnimCurveNode::R"), propStr("")),
        children = listOf(
          node(
            "Properties70",
            children = listOf(
              node("P", props = listOf(propStr("d|X"), propStr("Number"), propStr(""), propStr("A"), propDouble(0.0))),
              node("P", props = listOf(propStr("d|Y"), propStr("Number"), propStr(""), propStr("A"), propDouble(0.0))),
              node("P", props = listOf(propStr("d|Z"), propStr("Number"), propStr(""), propStr("A"), propDouble(0.0))),
            ),
          ),
        ),
      ),
      node(
        "AnimationCurveNode",
        props = listOf(propLong(idCnRight), propStr("AnimCurveNode::R"), propStr("")),
        children = listOf(
          node(
            "Properties70",
            children = listOf(
              node("P", props = listOf(propStr("d|X"), propStr("Number"), propStr(""), propStr("A"), propDouble(0.0))),
            ),
          ),
        ),
      ),
      node(
        "AnimationCurve",
        props = listOf(propLong(idCurveLeftX), propStr("AnimCurve::"), propStr("")),
        children = listOf(
          node("KeyTime", props = listOf(propLongArrayCompressed(times))),
          node("KeyValueFloat", props = listOf(propFloatArrayCompressed(leftVals))),
        ),
      ),
      node(
        "AnimationCurve",
        props = listOf(propLong(idCurveRightX), propStr("AnimCurve::"), propStr("")),
        children = listOf(
          node("KeyTime", props = listOf(propLongArrayCompressed(times))),
          node("KeyValueFloat", props = listOf(propFloatArrayCompressed(rightVals))),
        ),
      ),
    )

    val connectionsChildren = listOf(
      node("C", props = listOf(propStr("OO"), propLong(idLeftThigh), propLong(idHips))),
      node("C", props = listOf(propStr("OO"), propLong(idRightThigh), propLong(idHips))),
      node("C", props = listOf(propStr("OP"), propLong(idCnLeft), propLong(idLeftThigh), propStr("Lcl Rotation"))),
      node("C", props = listOf(propStr("OP"), propLong(idCnRight), propLong(idRightThigh), propStr("Lcl Rotation"))),
      node("C", props = listOf(propStr("OP"), propLong(idCurveLeftX), propLong(idCnLeft), propStr("d|X"))),
      node("C", props = listOf(propStr("OP"), propLong(idCurveRightX), propLong(idCnRight), propStr("d|X"))),
    )

    val rootNodes = listOf(
      node("Objects", children = objectsChildren),
      node("Connections", children = connectionsChildren),
    )

    var cursor = 27
    for (n in rootNodes) {
      val serialized = n.serialize(cursor)
      out.write(serialized)
      cursor += serialized.size
    }
    out.write(ByteArray(13))
    return out.toByteArray()
  }

  private inner class FbxNodeBuilder(
    val name: String,
    val props: List<ByteArray>,
    val children: List<FbxNodeBuilder>,
  ) {
    fun serialize(startOffset: Int): ByteArray {
      val nameBytes = name.toByteArray(Charsets.UTF_8)
      val propsPayload = ByteArrayOutputStream().apply {
        props.forEach { write(it) }
      }.toByteArray()

      val childrenStart = startOffset + 13 + nameBytes.size + propsPayload.size
      val childrenPayload = ByteArrayOutputStream()
      if (children.isNotEmpty()) {
        var childCursor = childrenStart
        for (c in children) {
          val bytes = c.serialize(childCursor)
          childrenPayload.write(bytes)
          childCursor += bytes.size
        }
        childrenPayload.write(ByteArray(13)) // FBX child list terminator
      }
      val childBytes = childrenPayload.toByteArray()
      val endOffset = childrenStart + childBytes.size

      return ByteArrayOutputStream().apply {
        write(intLe(endOffset))
        write(intLe(props.size))
        write(intLe(propsPayload.size))
        write(nameBytes.size and 0xFF)
        write(nameBytes)
        write(propsPayload)
        write(childBytes)
      }.toByteArray()
    }
  }

  private fun node(
    name: String,
    props: List<ByteArray> = emptyList(),
    children: List<FbxNodeBuilder> = emptyList(),
  ): FbxNodeBuilder = FbxNodeBuilder(name, props, children)

  private fun intLe(v: Int): ByteArray =
    ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

  private fun propLong(v: Long): ByteArray =
    ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN).put('L'.code.toByte()).putLong(v).array()

  private fun propDouble(v: Double): ByteArray =
    ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN).put('D'.code.toByte()).putDouble(v).array()

  private fun propStr(s: String): ByteArray {
    val raw = s.toByteArray(Charsets.UTF_8)
    return ByteBuffer.allocate(5 + raw.size).order(ByteOrder.LITTLE_ENDIAN)
      .put('S'.code.toByte())
      .putInt(raw.size)
      .put(raw)
      .array()
  }

  private fun propLongArrayCompressed(arr: LongArray): ByteArray {
    val raw = ByteBuffer.allocate(arr.size * 8).order(ByteOrder.LITTLE_ENDIAN).apply {
      arr.forEach { putLong(it) }
    }.array()
    val compressed = zlibCompress(raw)
    return ByteBuffer.allocate(13 + compressed.size).order(ByteOrder.LITTLE_ENDIAN)
      .put('l'.code.toByte())
      .putInt(arr.size)
      .putInt(1)
      .putInt(compressed.size)
      .put(compressed)
      .array()
  }

  private fun propFloatArrayCompressed(arr: FloatArray): ByteArray {
    val raw = ByteBuffer.allocate(arr.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply {
      arr.forEach { putFloat(it) }
    }.array()
    val compressed = zlibCompress(raw)
    return ByteBuffer.allocate(13 + compressed.size).order(ByteOrder.LITTLE_ENDIAN)
      .put('f'.code.toByte())
      .putInt(arr.size)
      .putInt(1)
      .putInt(compressed.size)
      .put(compressed)
      .array()
  }

  private fun zlibCompress(raw: ByteArray): ByteArray {
    val deflater = Deflater()
    deflater.setInput(raw)
    deflater.finish()
    val out = ByteArrayOutputStream()
    val buf = ByteArray(256)
    while (!deflater.finished()) {
      val n = deflater.deflate(buf)
      if (n > 0) out.write(buf, 0, n)
    }
    deflater.end()
    return out.toByteArray()
  }
}
