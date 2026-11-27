package com.example.game.gl

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class FbxKeyCurve(
  val timesSec: FloatArray,
  val valuesDeg: FloatArray,
) {
  fun sample(timeSec: Float, defaultDeg: Float): Float {
    if (timesSec.isEmpty() || valuesDeg.isEmpty()) return defaultDeg
    if (timesSec.size == 1 || timeSec <= timesSec[0]) return valuesDeg[0]
    val last = timesSec.lastIndex
    if (timeSec >= timesSec[last]) return valuesDeg[last]

    var lo = 0
    var hi = last
    while (lo + 1 < hi) {
      val mid = (lo + hi) ushr 1
      if (timesSec[mid] <= timeSec) lo = mid else hi = mid
    }
    val t0 = timesSec[lo]
    val t1 = timesSec[hi]
    val span = (t1 - t0).coerceAtLeast(1e-6f)
    val alpha = ((timeSec - t0) / span).coerceIn(0f, 1f)
    return valuesDeg[lo] + (valuesDeg[hi] - valuesDeg[lo]) * alpha
  }
}

data class FbxBoneTrack(
  val id: Long,
  val cleanName: String,
  var parentId: Long = -1L,
  val preRotationDeg: FloatArray = floatArrayOf(0f, 0f, 0f),
  val defaultLclRotationDeg: FloatArray = floatArrayOf(0f, 0f, 0f),
  var rotCurveX: FbxKeyCurve? = null,
  var rotCurveY: FbxKeyCurve? = null,
  var rotCurveZ: FbxKeyCurve? = null,
  var defaultCurveX: Float? = null,
  var defaultCurveY: Float? = null,
  var defaultCurveZ: Float? = null,
)

data class FbxAnimationClip(
  val name: String,
  val durationSec: Float,
  val bones: Map<String, FbxBoneTrack>,
  val orderedBones: List<FbxBoneTrack>,
) {
  /**
   * Samples the Mixamo FBX animation clip at [timeSec] and retargets bone rotations into
   * character-root space (+Y up, +Z forward, +X left) matching [GlbCharacterPose].
   */
  fun sampleAsHumanPose(timeSec: Float): GlbCharacterPose {
    val dur = durationSec.coerceAtLeast(0.05f)
    val t = ((timeSec % dur) + dur) % dur

    val restWorld = HashMap<Long, FloatArray>(orderedBones.size * 2)
    val charDeltaEuler = HashMap<String, FloatArray>(orderedBones.size * 2)
    val rotA = FloatArray(9)
    val rotB = FloatArray(9)
    val tmp9 = FloatArray(9)

    for (bone in orderedBones) {
      val pRest = restWorld[bone.parentId] ?: IDENTITY_3X3

      eulerXyzToMat3(bone.preRotationDeg[0], bone.preRotationDeg[1], bone.preRotationDeg[2], rotA)
      eulerXyzToMat3(
        bone.defaultLclRotationDeg[0],
        bone.defaultLclRotationDeg[1],
        bone.defaultLclRotationDeg[2],
        rotB,
      )
      mulMat3(tmp9, rotA, rotB)
      val boneRest = FloatArray(9)
      mulMat3(boneRest, pRest, tmp9)
      restWorld[bone.id] = boneRest

      val ax = bone.rotCurveX?.sample(t, bone.defaultCurveX ?: bone.defaultLclRotationDeg[0])
        ?: bone.defaultCurveX ?: bone.defaultLclRotationDeg[0]
      val ay = bone.rotCurveY?.sample(t, bone.defaultCurveY ?: bone.defaultLclRotationDeg[1])
        ?: bone.defaultCurveY ?: bone.defaultLclRotationDeg[1]
      val az = bone.rotCurveZ?.sample(t, bone.defaultCurveZ ?: bone.defaultLclRotationDeg[2])
        ?: bone.defaultCurveZ ?: bone.defaultLclRotationDeg[2]

      // Local animated rotation vs default rest orientation, conjugated into character space:
      // Delta_char = pRest * PreRot * R(anim) * inv(PreRot * R(rest)) * inv(pRest)
      eulerXyzToMat3(ax, ay, az, rotB)
      val animLocal = FloatArray(9)
      mulMat3(animLocal, rotA, rotB)
      val worldAnim = FloatArray(9)
      mulMat3(worldAnim, pRest, animLocal)

      val boneRestInv = transposeMat3(boneRest)
      val deltaChar = FloatArray(9)
      mulMat3(deltaChar, worldAnim, boneRestInv)

      val euler = mat3ToEulerXyzDeg(deltaChar)
      charDeltaEuler[bone.cleanName.lowercase()] = euler
    }

    fun boneEuler(vararg names: String): FloatArray {
      for (n in names) {
        val hit = charDeltaEuler[n]
        if (hit != null) return hit
      }
      return ZERO_3
    }

    val hips = boneEuler("hips", "pelvis")
    val spine1 = boneEuler("spine")
    val spine2 = boneEuler("spine1", "spine2")
    val neck = boneEuler("neck")
    val head = boneEuler("head")

    val lThigh = boneEuler("leftupleg", "leftthigh", "l_thigh", "l leg")
    val lKnee = boneEuler("leftleg", "leftknee", "leftshin", "l_calf", "l knee")
    val lFoot = boneEuler("leftfoot", "leftankle", "l_foot", "l foot")

    val rThigh = boneEuler("rightupleg", "rightthigh", "r_thigh", "r leg")
    val rKnee = boneEuler("rightleg", "rightknee", "rightshin", "r_calf", "r knee")
    val rFoot = boneEuler("rightfoot", "rightankle", "r_foot", "r foot")

    val lArm = boneEuler("leftarm", "leftupperarm", "l_upperarm", "l arm")
    val lFore = boneEuler("leftforearm", "leftlowerarm", "l_forearm", "l forearm")
    val rArm = boneEuler("rightarm", "rightupperarm", "r_upperarm", "r arm")
    val rFore = boneEuler("rightforearm", "rightlowerarm", "r_forearm", "r forearm")

    // Mixamo legs swing around +X; note Mixamo T-pose arms drop ~65° along Z when running,
    // while our GLB A-pose already has arms lowered by the sides -> use forward swing X & bend.
    val lThighX = lThigh[0].coerceIn(-75f, 75f)
    val rThighX = rThigh[0].coerceIn(-75f, 75f)
    val lKneeX = max(4f, lKnee[0]).coerceIn(0f, 125f)
    val rKneeX = max(4f, rKnee[0]).coerceIn(0f, 125f)

    val lArmSwing = (lArm[0] - lArm[1] * 0.25f).coerceIn(-68f, 68f)
    val rArmSwing = (rArm[0] + rArm[1] * 0.25f).coerceIn(-68f, 68f)
    val lElbow = (-abs(lFore[1]) - abs(lFore[0]) * 0.6f - 28f).coerceIn(-115f, -10f)
    val rElbow = (-abs(rFore[1]) - abs(rFore[0]) * 0.6f - 28f).coerceIn(-115f, -10f)

    // Natural stride vertical bounce derived from leg swing phase when root translation isn't baked
    val strideBounce = (abs(sin((lThighX - rThighX) * (PI.toFloat() / 180f) * 0.5f)) * 0.048f).coerceIn(0f, 0.06f)

    return GlbCharacterPose(
      pelvisBounceY = strideBounce,
      pelvisSwayX = sin(hips[1] * (PI.toFloat() / 180f)) * 0.025f,
      pelvisYawDeg = hips[1].coerceIn(-22f, 22f),
      pelvisRollDeg = hips[2].coerceIn(-10f, 10f),
      torsoYawDeg = (spine1[1] + spine2[1]).coerceIn(-26f, 26f),
      torsoLeanDeg = (10f + (spine1[0] + spine2[0]) * 0.55f).coerceIn(2f, 26f),
      headPitchDeg = (neck[0] + head[0]).coerceIn(-20f, 20f),
      headYawDeg = (neck[1] + head[1]).coerceIn(-25f, 25f),
      leftThighPitchDeg = lThighX,
      leftKneeBendDeg = lKneeX,
      leftAnklePitchDeg = lFoot[0].coerceIn(-32f, 32f),
      rightThighPitchDeg = rThighX,
      rightKneeBendDeg = rKneeX,
      rightAnklePitchDeg = rFoot[0].coerceIn(-32f, 32f),
      leftArmPitchDeg = lArmSwing,
      leftArmOutDeg = 5f,
      leftForearmBendDeg = lElbow,
      rightArmPitchDeg = rArmSwing,
      rightArmOutDeg = 5f,
      rightForearmBendDeg = rElbow,
    )
  }

  companion object {
    private val ZERO_3 = floatArrayOf(0f, 0f, 0f)
    private val IDENTITY_3X3 = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    fun lerpPose(a: GlbCharacterPose, b: GlbCharacterPose, alpha: Float): GlbCharacterPose {
      val t = alpha.coerceIn(0f, 1f)
      if (t <= 0.001f) return a
      if (t >= 0.999f) return b
      fun mix(x: Float, y: Float) = x + (y - x) * t
      return GlbCharacterPose(
        pelvisBounceY = mix(a.pelvisBounceY, b.pelvisBounceY),
        pelvisSwayX = mix(a.pelvisSwayX, b.pelvisSwayX),
        pelvisYawDeg = mix(a.pelvisYawDeg, b.pelvisYawDeg),
        pelvisRollDeg = mix(a.pelvisRollDeg, b.pelvisRollDeg),
        rootPitchDeg = mix(a.rootPitchDeg, b.rootPitchDeg),
        torsoYawDeg = mix(a.torsoYawDeg, b.torsoYawDeg),
        torsoLeanDeg = mix(a.torsoLeanDeg, b.torsoLeanDeg),
        headPitchDeg = mix(a.headPitchDeg, b.headPitchDeg),
        headYawDeg = mix(a.headYawDeg, b.headYawDeg),
        leftThighPitchDeg = mix(a.leftThighPitchDeg, b.leftThighPitchDeg),
        leftKneeBendDeg = mix(a.leftKneeBendDeg, b.leftKneeBendDeg),
        leftAnklePitchDeg = mix(a.leftAnklePitchDeg, b.leftAnklePitchDeg),
        rightThighPitchDeg = mix(a.rightThighPitchDeg, b.rightThighPitchDeg),
        rightThighOutDeg = mix(a.rightThighOutDeg, b.rightThighOutDeg),
        rightKneeBendDeg = mix(a.rightKneeBendDeg, b.rightKneeBendDeg),
        rightAnklePitchDeg = mix(a.rightAnklePitchDeg, b.rightAnklePitchDeg),
        leftArmPitchDeg = mix(a.leftArmPitchDeg, b.leftArmPitchDeg),
        leftArmOutDeg = mix(a.leftArmOutDeg, b.leftArmOutDeg),
        leftForearmBendDeg = mix(a.leftForearmBendDeg, b.leftForearmBendDeg),
        rightArmPitchDeg = mix(a.rightArmPitchDeg, b.rightArmPitchDeg),
        rightArmOutDeg = mix(a.rightArmOutDeg, b.rightArmOutDeg),
        rightForearmBendDeg = mix(a.rightForearmBendDeg, b.rightForearmBendDeg),
      )
    }

    private fun eulerXyzToMat3(rxDeg: Float, ryDeg: Float, rzDeg: Float, out: FloatArray) {
      val rad = PI.toFloat() / 180f
      val x = rxDeg * rad; val y = ryDeg * rad; val z = rzDeg * rad
      val cx = cos(x); val sx = sin(x)
      val cy = cos(y); val sy = sin(y)
      val cz = cos(z); val sz = sin(z)
      // Rz * Ry * Rx (FBX standard XYZ intrinsic / ZYX extrinsic column-major)
      out[0] = cy * cz
      out[1] = cy * sz
      out[2] = -sy
      out[3] = sx * sy * cz - cx * sz
      out[4] = sx * sy * sz + cx * cz
      out[5] = sx * cy
      out[6] = cx * sy * cz + sx * sz
      out[7] = cx * sy * sz - sx * cz
      out[8] = cx * cy
    }

    private fun mulMat3(dst: FloatArray, a: FloatArray, b: FloatArray) {
      for (c in 0 until 3) {
        val c3 = c * 3
        val b0 = b[c3]; val b1 = b[c3 + 1]; val b2 = b[c3 + 2]
        dst[c3] = a[0] * b0 + a[3] * b1 + a[6] * b2
        dst[c3 + 1] = a[1] * b0 + a[4] * b1 + a[7] * b2
        dst[c3 + 2] = a[2] * b0 + a[5] * b1 + a[8] * b2
      }
    }

    private fun transposeMat3(m: FloatArray): FloatArray =
      floatArrayOf(
        m[0], m[3], m[6],
        m[1], m[4], m[7],
        m[2], m[5], m[8],
      )

    private fun mat3ToEulerXyzDeg(m: FloatArray): FloatArray {
      val sy = (-m[2]).coerceIn(-1f, 1f)
      val y = asin(sy)
      val x: Float
      val z: Float
      if (abs(m[2]) < 0.9999f) {
        x = atan2(m[5], m[8])
        z = atan2(m[1], m[0])
      } else {
        x = atan2(-m[7], m[4])
        z = 0f
      }
      val radToDeg = 180f / PI.toFloat()
      return floatArrayOf(x * radToDeg, y * radToDeg, z * radToDeg)
    }
  }
}

/**
 * Pure-Kotlin Kaydara FBX Binary (v7.1–v7.7) animation clip extractor.
 * Parses bone Models, AnimationCurveNodes, AnimationCurves (including zlib-compressed arrays),
 * and Connections, strips Mixamo prefixes ("mixamorig:" / "mixamorig"), and produces a retargeted
 * [FbxAnimationClip] without modifying any scene meshes or geometry.
 */
object FbxAnimLoader {
  private const val FBX_MAGIC_PREFIX = "Kaydara FBX Binary  "
  private const val KTIME_TICKS_PER_SEC = 46186158000.0

  private class CurveNodeInfo(
    val id: Long,
    var defX: Float? = null,
    var defY: Float? = null,
    var defZ: Float? = null,
  )

  private class Connection(
    val type: String,
    val srcId: Long,
    val dstId: Long,
    val propName: String,
  )

  fun stripMixamoPrefix(rawName: String): String {
    var clean = rawName.substringBefore('\u0000')
    clean = clean.substringAfterLast("::")
    clean = clean.replace("mixamorig:", "", ignoreCase = true)
    clean = clean.replace("mixamorig", "", ignoreCase = true)
    return clean.trim()
  }

  fun parseFbxBytes(bytes: ByteArray, fallbackName: String = "Running"): FbxAnimationClip {
    require(bytes.size >= 27) { "FBX file too small (${bytes.size} bytes)" }
    val magic = String(bytes, 0, 20, Charsets.US_ASCII)
    require(magic == FBX_MAGIC_PREFIX) { "Expected Binary FBX header (got '$magic')" }

    val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val version = bb.getInt(23)
    val is64Bit = version >= 7500
    val sentinelBytes = if (is64Bit) 25 else 13

    val bonesById = LinkedHashMap<Long, FbxBoneTrack>()
    val curveNodesById = LinkedHashMap<Long, CurveNodeInfo>()
    val curvesById = LinkedHashMap<Long, FbxKeyCurve>()
    val connections = ArrayList<Connection>()
    var stackDurationSec = 0f

    fun readHeader(pos: Int): Triple<Long, Long, Int> {
      return if (is64Bit) {
        val endOffset = bb.getLong(pos)
        val numProps = bb.getLong(pos + 8)
        // propListLen at pos + 16 (8 bytes)
        val nameLen = bb.get(pos + 24).toInt() and 0xFF
        Triple(endOffset, numProps, nameLen)
      } else {
        val endOffset = bb.getInt(pos).toLong() and 0xFFFFFFFFL
        val numProps = bb.getInt(pos + 4).toLong() and 0xFFFFFFFFL
        val nameLen = bb.get(pos + 12).toInt() and 0xFF
        Triple(endOffset, numProps, nameLen)
      }
    }

    val headerSize = if (is64Bit) 25 else 13

    fun readPropListEnd(pos: Int): Long {
      return if (is64Bit) bb.getLong(pos + 16) else (bb.getInt(pos + 8).toLong() and 0xFFFFFFFFL)
    }

    fun isZeroSentinel(pos: Int): Boolean {
      if (pos + sentinelBytes > bytes.size) return true
      for (k in 0 until sentinelBytes) {
        if (bytes[pos + k] != 0.toByte()) return false
      }
      return true
    }

    class PropCursor(var offset: Int)

    fun readProperty(cur: PropCursor): Any? {
      if (cur.offset >= bytes.size) return null
      val typeCode = bytes[cur.offset++].toInt().toChar()
      return when (typeCode) {
        'Y' -> bb.getShort(cur.offset).also { cur.offset += 2 }
        'C' -> (bytes[cur.offset++] != 0.toByte())
        'I' -> bb.getInt(cur.offset).also { cur.offset += 4 }
        'F' -> bb.getFloat(cur.offset).also { cur.offset += 4 }
        'D' -> bb.getDouble(cur.offset).also { cur.offset += 8 }
        'L' -> bb.getLong(cur.offset).also { cur.offset += 8 }
        'S', 'R' -> {
          val len = bb.getInt(cur.offset)
          cur.offset += 4
          val safeLen = len.coerceIn(0, bytes.size - cur.offset)
          val result =
            if (typeCode == 'S') String(bytes, cur.offset, safeLen, Charsets.UTF_8)
            else bytes.copyOfRange(cur.offset, cur.offset + safeLen)
          cur.offset += safeLen
          result
        }
        'f', 'd', 'l', 'i', 'b' -> {
          val arrayLen = bb.getInt(cur.offset)
          val encoding = bb.getInt(cur.offset + 4)
          val compLen = bb.getInt(cur.offset + 8)
          cur.offset += 12
          val safeCompLen = compLen.coerceIn(0, bytes.size - cur.offset)
          val rawPayload =
            if (encoding == 1) {
              inflateZlib(bytes, cur.offset, safeCompLen, arrayLen * elemByteSize(typeCode))
            } else {
              bytes.copyOfRange(cur.offset, cur.offset + safeCompLen)
            }
          cur.offset += safeCompLen
          decodeTypedArray(typeCode, arrayLen, rawPayload)
        }
        else -> null
      }
    }

    fun readAllProps(start: Int, count: Int): List<Any?> {
      val list = ArrayList<Any?>(count)
      val cur = PropCursor(start)
      repeat(count) {
        list.add(readProperty(cur))
      }
      return list
    }

    fun walkNodes(startPos: Int, endLimit: Int, section: String, parentObjectId: Long, parentType: String) {
      var pos = startPos
      while (pos + headerSize <= endLimit) {
        if (isZeroSentinel(pos)) break
        val (endOffsetLong, numPropsLong, nameLen) = readHeader(pos)
        val endOffset = endOffsetLong.toInt()
        if (endOffset <= pos || endOffset > bytes.size) break
        val propListLen = readPropListEnd(pos).toInt()
        val nameStart = pos + headerSize
        val propsStart = nameStart + nameLen
        val childrenStart = propsStart + propListLen
        val nodeName = String(bytes, nameStart, nameLen.coerceAtMost(bytes.size - nameStart), Charsets.UTF_8)
        val props = readAllProps(propsStart, numPropsLong.toInt().coerceIn(0, 64))

        when (section) {
          "" -> {
            if (nodeName == "Objects" || nodeName == "Connections") {
              walkNodes(childrenStart, endOffset, nodeName, -1L, "")
            }
          }
          "Objects" -> {
            val id = (props.getOrNull(0) as? Number)?.toLong() ?: -1L
            val rawObjName = props.getOrNull(1) as? String ?: ""
            when (nodeName) {
              "Model" -> {
                val bone = FbxBoneTrack(id = id, cleanName = stripMixamoPrefix(rawObjName))
                bonesById[id] = bone
                walkNodes(childrenStart, endOffset, "ObjectChildren", id, "Model")
              }
              "AnimationCurveNode" -> {
                val cn = CurveNodeInfo(id = id)
                curveNodesById[id] = cn
                walkNodes(childrenStart, endOffset, "ObjectChildren", id, "AnimationCurveNode")
              }
              "AnimationCurve" -> {
                var keyTimes: LongArray? = null
                var keyValues: FloatArray? = null
                var cPos = childrenStart
                while (cPos + headerSize <= endOffset && !isZeroSentinel(cPos)) {
                  val (cEndL, cNumP, cNameLen) = readHeader(cPos)
                  val cEnd = cEndL.toInt()
                  if (cEnd <= cPos || cEnd > endOffset) break
                  val cName = String(bytes, cPos + headerSize, cNameLen, Charsets.UTF_8)
                  val cProps = readAllProps(cPos + headerSize + cNameLen, cNumP.toInt().coerceIn(0, 8))
                  when (cName) {
                    "KeyTime" -> keyTimes = cProps.firstOrNull() as? LongArray
                    "KeyValueFloat" -> keyValues = cProps.firstOrNull() as? FloatArray
                  }
                  cPos = cEnd
                }
                if (keyTimes != null && keyValues != null && keyTimes.isNotEmpty() && keyValues.isNotEmpty()) {
                  val n = min(keyTimes.size, keyValues.size)
                  val timesSec = FloatArray(n) { i -> (keyTimes[i].toDouble() / KTIME_TICKS_PER_SEC).toFloat() }
                  val vals = if (keyValues.size == n) keyValues else keyValues.copyOf(n)
                  curvesById[id] = FbxKeyCurve(timesSec, vals)
                }
              }
              "AnimationStack" -> {
                walkNodes(childrenStart, endOffset, "ObjectChildren", id, "AnimationStack")
              }
            }
          }
          "ObjectChildren" -> {
            if (nodeName == "Properties70" || nodeName == "Properties60") {
              walkNodes(childrenStart, endOffset, "Props", parentObjectId, parentType)
            }
          }
          "Props" -> {
            if (nodeName == "P" && props.size >= 5) {
              val pName = props[0] as? String ?: ""
              when (parentType) {
                "Model" -> {
                  val bone = bonesById[parentObjectId]
                  if (bone != null) {
                    val x = (props.getOrNull(4) as? Number)?.toFloat() ?: 0f
                    val y = (props.getOrNull(5) as? Number)?.toFloat() ?: 0f
                    val z = (props.getOrNull(6) as? Number)?.toFloat() ?: 0f
                    if (pName == "PreRotation") {
                      bone.preRotationDeg[0] = x; bone.preRotationDeg[1] = y; bone.preRotationDeg[2] = z
                    } else if (pName == "Lcl Rotation") {
                      bone.defaultLclRotationDeg[0] = x; bone.defaultLclRotationDeg[1] = y; bone.defaultLclRotationDeg[2] = z
                    }
                  }
                }
                "AnimationCurveNode" -> {
                  val cn = curveNodesById[parentObjectId]
                  val v = (props.getOrNull(4) as? Number)?.toFloat()
                  if (cn != null && v != null) {
                    when (pName) {
                      "d|X" -> cn.defX = v
                      "d|Y" -> cn.defY = v
                      "d|Z" -> cn.defZ = v
                    }
                  }
                }
                "AnimationStack" -> {
                  if (pName == "LocalStop") {
                    val ticks = (props.getOrNull(4) as? Number)?.toDouble() ?: 0.0
                    val sec = (ticks / KTIME_TICKS_PER_SEC).toFloat()
                    if (sec > stackDurationSec) stackDurationSec = sec
                  }
                }
              }
            }
          }
          "Connections" -> {
            if (nodeName == "C" && props.size >= 3) {
              val cType = props[0] as? String ?: ""
              val srcId = (props[1] as? Number)?.toLong() ?: -1L
              val dstId = (props[2] as? Number)?.toLong() ?: -1L
              val propName = props.getOrNull(3) as? String ?: ""
              connections.add(Connection(cType, srcId, dstId, propName))
            }
          }
        }
        pos = endOffset
      }
    }

    walkNodes(27, bytes.size, "", -1L, "")

    // Resolve bone parenting and curve -> curveNode -> bone connections
    val curveNodeToBone = HashMap<Long, FbxBoneTrack>()
    for (c in connections) {
      val srcBone = bonesById[c.srcId]
      val dstBone = bonesById[c.dstId]
      if (c.type == "OO" && srcBone != null && dstBone != null) {
        srcBone.parentId = dstBone.id
      }
      val srcCn = curveNodesById[c.srcId]
      if (srcCn != null && dstBone != null && c.propName.contains("Rotation", ignoreCase = true)) {
        curveNodeToBone[srcCn.id] = dstBone
        if (srcCn.defX != null) dstBone.defaultCurveX = srcCn.defX
        if (srcCn.defY != null) dstBone.defaultCurveY = srcCn.defY
        if (srcCn.defZ != null) dstBone.defaultCurveZ = srcCn.defZ
      }
    }

    var maxCurveTime = 0f
    for (c in connections) {
      val curve = curvesById[c.srcId] ?: continue
      val bone = curveNodeToBone[c.dstId] ?: continue
      if (curve.timesSec.isNotEmpty()) {
        maxCurveTime = max(maxCurveTime, curve.timesSec.last())
      }
      when {
        c.propName.endsWith("X", ignoreCase = true) -> bone.rotCurveX = curve
        c.propName.endsWith("Y", ignoreCase = true) -> bone.rotCurveY = curve
        c.propName.endsWith("Z", ignoreCase = true) -> bone.rotCurveZ = curve
      }
    }

    // Topological parent-before-child ordering of bones
    val childrenMap = HashMap<Long, MutableList<FbxBoneTrack>>()
    val roots = ArrayList<FbxBoneTrack>()
    for (b in bonesById.values) {
      if (b.parentId !in bonesById) {
        roots.add(b)
      } else {
        childrenMap.getOrPut(b.parentId) { ArrayList() }.add(b)
      }
    }
    val ordered = ArrayList<FbxBoneTrack>(bonesById.size)
    val seen = HashSet<Long>()
    fun dfs(b: FbxBoneTrack) {
      if (!seen.add(b.id)) return
      ordered.add(b)
      childrenMap[b.id]?.forEach { dfs(it) }
    }
    roots.forEach { dfs(it) }
    bonesById.values.forEach { dfs(it) }

    val byCleanName = LinkedHashMap<String, FbxBoneTrack>()
    for (b in ordered) {
      byCleanName[b.cleanName.lowercase()] = b
    }

    val duration = max(stackDurationSec, maxCurveTime).coerceAtLeast(0.5f)
    return FbxAnimationClip(
      name = fallbackName,
      durationSec = duration,
      bones = byCleanName,
      orderedBones = ordered,
    )
  }

  private fun elemByteSize(typeCode: Char): Int =
    when (typeCode) {
      'f', 'i' -> 4
      'd', 'l' -> 8
      'b' -> 1
      else -> 1
    }

  private fun inflateZlib(src: ByteArray, offset: Int, len: Int, expectedBytes: Int): ByteArray {
    val inflater = Inflater()
    return try {
      inflater.setInput(src, offset, len)
      val out = ByteArray(max(expectedBytes, 64))
      var total = 0
      while (!inflater.finished()) {
        if (total == out.size) break
        val count = inflater.inflate(out, total, out.size - total)
        if (count == 0 && inflater.needsInput()) break
        total += count
      }
      if (total == out.size) out else out.copyOf(total)
    } finally {
      inflater.end()
    }
  }

  private fun decodeTypedArray(typeCode: Char, count: Int, payload: ByteArray): Any {
    val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
    return when (typeCode) {
      'f' -> FloatArray(min(count, payload.size / 4)) { i -> bb.getFloat(i * 4) }
      'd' -> FloatArray(min(count, payload.size / 8)) { i -> bb.getDouble(i * 8).toFloat() }
      'l' -> LongArray(min(count, payload.size / 8)) { i -> bb.getLong(i * 8) }
      'i' -> IntArray(min(count, payload.size / 4)) { i -> bb.getInt(i * 4) }
      'b' -> BooleanArray(min(count, payload.size)) { i -> payload[i] != 0.toByte() }
      else -> payload
    }
  }
}
