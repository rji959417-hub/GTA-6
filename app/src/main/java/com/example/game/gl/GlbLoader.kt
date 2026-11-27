package com.example.game.gl

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class GlbImageData(
  val mimeType: String,
  val encodedBytes: ByteArray? = null,
  val rawRgbaWidth: Int = 0,
  val rawRgbaHeight: Int = 0,
  val rawRgbaPixels: ByteArray? = null,
)

data class GlbMaterialData(
  val baseColor: FloatArray = floatArrayOf(1f, 1f, 1f, 1f),
  val metallic: Float = 0.25f,
  val roughness: Float = 0.65f,
  val emissive: Float = 0f,
  val baseColorImageIndex: Int = -1,
  val doubleSided: Boolean = false,
)

data class GlbNodeDef(
  val index: Int,
  val name: String,
  val parentIndex: Int,
  val localMatrix: FloatArray,
  val bindWorldMatrix: FloatArray,
  val bindNormMatrix: FloatArray,
  val bindNormInverse: FloatArray,
  val bindPosX: Float,
  val bindPosY: Float,
  val bindPosZ: Float,
)

data class GlbSkinDef(
  val joints: IntArray,
  val inverseBindMatrices: Array<FloatArray>,
)

data class HumanRigMapping(
  val hips: Int = -1,
  val spine1: Int = -1,
  val spine2: Int = -1,
  val spine3: Int = -1,
  val neck: Int = -1,
  val head: Int = -1,
  val leftArm: Int = -1,
  val leftForearm: Int = -1,
  val leftHand: Int = -1,
  val rightArm: Int = -1,
  val rightForearm: Int = -1,
  val rightHand: Int = -1,
  val leftThigh: Int = -1,
  val leftShin: Int = -1,
  val leftFoot: Int = -1,
  val rightThigh: Int = -1,
  val rightShin: Int = -1,
  val rightFoot: Int = -1,
  val leftToes: Int = -1,
  val rightToes: Int = -1,
) {
  val hasLocomotionRig: Boolean
    get() = (leftThigh >= 0 && rightThigh >= 0) || (leftArm >= 0 && rightArm >= 0)
}

data class GlbCharacterPose(
  val pelvisBounceY: Float = 0f,
  val pelvisSwayX: Float = 0f,
  val pelvisYawDeg: Float = 0f,
  val pelvisRollDeg: Float = 0f,
  val rootPitchDeg: Float = 0f,
  val torsoYawDeg: Float = 0f,
  val torsoLeanDeg: Float = 0f,
  val headPitchDeg: Float = 0f,
  val headYawDeg: Float = 0f,
  val leftThighPitchDeg: Float = 0f,
  val leftKneeBendDeg: Float = 0f,
  val leftAnklePitchDeg: Float = 0f,
  val rightThighPitchDeg: Float = 0f,
  val rightThighOutDeg: Float = 0f,
  val rightKneeBendDeg: Float = 0f,
  val rightAnklePitchDeg: Float = 0f,
  val leftArmPitchDeg: Float = 0f,
  val leftArmOutDeg: Float = 0f,
  val leftForearmBendDeg: Float = 0f,
  val rightArmPitchDeg: Float = 0f,
  val rightArmOutDeg: Float = 0f,
  val rightForearmBendDeg: Float = 0f,
)

data class GlbMeshPartData(
  val vertices: FloatArray, // bind-pose normalized interleaved 8 floats: x, y, z, nx, ny, nz, u, v
  val rawVertices: FloatArray, // pre-skin/node interleaved 8 floats for dynamic pose evaluation
  val indices: ShortArray,
  val material: GlbMaterialData,
  val skinIndex: Int = -1,
  val rigidNodeIndex: Int = -1,
  val joints: IntArray? = null, // 4 joint indices per vertex if skinIndex >= 0
  val weights: FloatArray? = null, // 4 normalized weights per vertex if skinIndex >= 0
)

data class GlbBounds(
  val minX: Float,
  val maxX: Float,
  val minY: Float,
  val maxY: Float,
  val minZ: Float,
  val maxZ: Float,
) {
  val width: Float get() = maxX - minX
  val height: Float get() = maxY - minY
  val depth: Float get() = maxZ - minZ
}

data class ParsedGlbModel(
  val parts: List<GlbMeshPartData>,
  val images: List<GlbImageData>,
  val nodes: List<GlbNodeDef>,
  val dfsOrder: IntArray,
  val skins: List<GlbSkinDef>,
  val rig: HumanRigMapping,
  val normMatrix: FloatArray,
  val rawBounds: GlbBounds,
  val normalizedBounds: GlbBounds,
  val appliedScale: Float,
  val groundShiftY: Float,
  val rotated180Y: Boolean,
) {
  val totalTriangles: Int
    get() = parts.sumOf { it.indices.size / 3 }
}

/**
 * Pure-Kotlin binary glTF 2.0 (.glb) loader that parses node hierarchies, skins, meshes,
 * accessors, materials, and embedded textures, resolves full skeletal bind poses, auto-aligns
 * upright (+Y up, +Z forward) to realistic human height grounded on y = 0, and supports
 * fast real-time humanoid skeletal animation.
 */
object GlbLoader {
  private const val GLB_MAGIC = 0x46546C67 // "glTF"
  private const val GLB_VERSION_2 = 2
  private const val CHUNK_JSON = 0x4E4F534A // "JSON"
  private const val CHUNK_BIN = 0x004E4942 // "BIN\0"
  private const val MAX_ES2_VERTICES_PER_PART = 65000

  fun parseGlbBytes(
    glbBytes: ByteArray,
    targetHeightMeters: Float = 1.76f,
  ): ParsedGlbModel {
    require(glbBytes.size >= 20) { "GLB file too small (${glbBytes.size} bytes)" }
    val bb = ByteBuffer.wrap(glbBytes).order(ByteOrder.LITTLE_ENDIAN)
    val magic = bb.getInt(0)
    val version = bb.getInt(4)
    val totalLength = bb.getInt(8)
    require(magic == GLB_MAGIC) { "Invalid GLB magic header: 0x${magic.toString(16)}" }
    require(version == GLB_VERSION_2) { "Unsupported glTF version: $version" }
    require(totalLength <= glbBytes.size) { "Truncated GLB container ($totalLength > ${glbBytes.size})" }

    var jsonText: String? = null
    var binChunk: ByteArray? = null

    var offset = 12
    while (offset + 8 <= glbBytes.size) {
      val chunkLen = bb.getInt(offset)
      val chunkType = bb.getInt(offset + 4)
      val dataStart = offset + 8
      require(chunkLen >= 0 && dataStart + chunkLen <= glbBytes.size) {
        "Invalid GLB chunk bounds at offset=$offset len=$chunkLen"
      }
      when (chunkType) {
        CHUNK_JSON -> if (jsonText == null) {
          jsonText = String(glbBytes, dataStart, chunkLen, Charsets.UTF_8).trimEnd('\u0000', ' ')
        }
        CHUNK_BIN -> if (binChunk == null) {
          binChunk = glbBytes.copyOfRange(dataStart, dataStart + chunkLen)
        }
      }
      offset = dataStart + ((chunkLen + 3) and 3.inv())
    }

    val root = JsonParser(requireNotNull(jsonText) { "GLB missing JSON chunk" }).parseObject()
    val buffers = parseBuffers(root["buffers"] as? JArr, binChunk)
    val bufferViews = parseBufferViews(root["bufferViews"] as? JArr)
    val accessors = parseAccessors(root["accessors"] as? JArr)
    val images = parseImages(root["images"] as? JArr, bufferViews, buffers).toMutableList()
    val textures = parseTextures(root["textures"] as? JArr)
    val materials = parseMaterials(root["materials"] as? JArr, textures)
    val meshesArr = root["meshes"] as? JArr ?: throw IllegalArgumentException("GLB contains no meshes")

    val nodesArr = root["nodes"] as? JArr
    val nodeCount = nodesArr?.items?.size ?: 0
    val localMats = Array(nodeCount) { idx -> computeNodeMatrix(nodesArr!!.objAt(idx)!!) }
    val nodeNames = Array(nodeCount) { idx -> nodesArr!!.objAt(idx)?.str("name").orEmpty() }
    val parentIdx = IntArray(nodeCount) { -1 }
    val childrenList = Array(nodeCount) { IntArray(0) }
    for (i in 0 until nodeCount) {
      val cArr = nodesArr!!.objAt(i)?.get("children") as? JArr
      if (cArr != null && cArr.items.isNotEmpty()) {
        val list = ArrayList<Int>(cArr.items.size)
        for (item in cArr.items) {
          val ci = (item as? JNum)?.value?.toInt() ?: continue
          if (ci in 0 until nodeCount) {
            parentIdx[ci] = i
            list.add(ci)
          }
        }
        childrenList[i] = list.toIntArray()
      }
    }

    val rootNodes =
      if (nodesArr != null && nodeCount > 0) resolveRootNodes(root, nodesArr, parentIdx) else emptyList()
    val bindWorlds = Array(nodeCount) { FloatArray(16).apply { setIdentity4(this) } }
    val dfsOrderList = ArrayList<Int>(nodeCount)
    val visited = BooleanArray(nodeCount)

    fun dfs(idx: Int, parentWorld: FloatArray?) {
      if (idx !in 0 until nodeCount || visited[idx]) return
      visited[idx] = true
      dfsOrderList.add(idx)
      if (parentWorld != null) {
        mulMat4(bindWorlds[idx], parentWorld, localMats[idx])
      } else {
        System.arraycopy(localMats[idx], 0, bindWorlds[idx], 0, 16)
      }
      for (c in childrenList[idx]) {
        dfs(c, bindWorlds[idx])
      }
    }

    val identity = FloatArray(16).apply { setIdentity4(this) }
    for (r in rootNodes) dfs(r, identity)
    for (i in 0 until nodeCount) if (!visited[i]) dfs(i, identity)
    val dfsOrder = dfsOrderList.toIntArray()

    val skins = parseSkins(root["skins"] as? JArr, bufferViews, buffers, accessors)
    val rig = detectHumanRig(nodeNames)

    val collectedPrimitives = ArrayList<RawPrimitiveGeometry>()
    if (nodesArr != null && nodeCount > 0) {
      for (nodeIdx in dfsOrder) {
        val nodeObj = nodesArr.objAt(nodeIdx) ?: continue
        val meshIdx = nodeObj.int("mesh", -1)
        if (meshIdx !in meshesArr.items.indices) continue
        val skinIdx = nodeObj.int("skin", -1)
        val meshObj = meshesArr.objAt(meshIdx) ?: continue
        extractMeshPrimitives(
          meshObj = meshObj,
          nodeIndex = nodeIdx,
          skinIndex = if (skinIdx in skins.indices) skinIdx else -1,
          buffers = buffers,
          bufferViews = bufferViews,
          accessors = accessors,
          materials = materials,
          images = images,
          out = collectedPrimitives,
        )
      }
    } else {
      for (mIdx in meshesArr.items.indices) {
        extractMeshPrimitives(
          meshObj = meshesArr.objAt(mIdx) ?: continue,
          nodeIndex = -1,
          skinIndex = -1,
          buffers = buffers,
          bufferViews = bufferViews,
          accessors = accessors,
          materials = materials,
          images = images,
          out = collectedPrimitives,
        )
      }
    }

    require(collectedPrimitives.isNotEmpty()) { "GLB contained zero drawable triangle primitives" }

    // Precompute bind skinning joint matrices = bindWorld[joint] * IBM
    val bindSkinJointMats =
      skins.map { skin ->
        Array(skin.joints.size) { jIdx ->
          val outMat = FloatArray(16)
          val jointNode = skin.joints[jIdx]
          val jWorld = if (jointNode in bindWorlds.indices) bindWorlds[jointNode] else identity
          mulMat4(outMat, jWorld, skin.inverseBindMatrices[jIdx])
          outMat
        }
      }

    // Evaluate bind-pose world positions for every primitive to determine uprightness & facing
    val evaluatedBindPositions =
      collectedPrimitives.map { prim ->
        evaluateBindPositions(prim, bindWorlds, bindSkinJointMats)
      }

    var rawMinX = Float.POSITIVE_INFINITY
    var rawMaxX = Float.NEGATIVE_INFINITY
    var rawMinY = Float.POSITIVE_INFINITY
    var rawMaxY = Float.NEGATIVE_INFINITY
    var rawMinZ = Float.POSITIVE_INFINITY
    var rawMaxZ = Float.NEGATIVE_INFINITY

    for (pos in evaluatedBindPositions) {
      var i = 0
      while (i + 2 < pos.size) {
        val x = pos[i]
        val y = pos[i + 1]
        val z = pos[i + 2]
        if (x < rawMinX) rawMinX = x
        if (x > rawMaxX) rawMaxX = x
        if (y < rawMinY) rawMinY = y
        if (y > rawMaxY) rawMaxY = y
        if (z < rawMinZ) rawMinZ = z
        if (z > rawMaxZ) rawMaxZ = z
        i += 3
      }
    }

    val rawBounds = GlbBounds(rawMinX, rawMaxX, rawMinY, rawMaxY, rawMinZ, rawMaxZ)

    // Detect if a static (unrigged) character was exported lying flat on Z instead of standing on Y
    val preRotMat = FloatArray(16).apply { setIdentity4(this) }
    if (!rig.hasLocomotionRig && rawBounds.depth > rawBounds.height * 1.45f) {
      setRotateX(preRotMat, -90f)
    }

    // Detect whether character faces -Z (e.g., toes/eyes/glasses behind foot/head in +Z) and rotate 180° Y to face +Z
    val rotated180Y = shouldRotate180Y(rig, bindWorlds, nodeNames, preRotMat)
    if (rotated180Y) {
      val rotY180 = FloatArray(16)
      setRotateY(rotY180, 180f)
      val combined = FloatArray(16)
      mulMat4(combined, rotY180, preRotMat)
      System.arraycopy(combined, 0, preRotMat, 0, 16)
    }

    // Compute bounds AFTER preRotMat so scale, centerXZ, and groundY = 0 are exact
    var rMinX = Float.POSITIVE_INFINITY
    var rMaxX = Float.NEGATIVE_INFINITY
    var rMinY = Float.POSITIVE_INFINITY
    var rMaxY = Float.NEGATIVE_INFINITY
    var rMinZ = Float.POSITIVE_INFINITY
    var rMaxZ = Float.NEGATIVE_INFINITY

    for (pos in evaluatedBindPositions) {
      var i = 0
      while (i + 2 < pos.size) {
        val px = pos[i]; val py = pos[i + 1]; val pz = pos[i + 2]
        val rx = preRotMat[0] * px + preRotMat[4] * py + preRotMat[8] * pz + preRotMat[12]
        val ry = preRotMat[1] * px + preRotMat[5] * py + preRotMat[9] * pz + preRotMat[13]
        val rz = preRotMat[2] * px + preRotMat[6] * py + preRotMat[10] * pz + preRotMat[14]
        if (rx < rMinX) rMinX = rx
        if (rx > rMaxX) rMaxX = rx
        if (ry < rMinY) rMinY = ry
        if (ry > rMaxY) rMaxY = ry
        if (rz < rMinZ) rMinZ = rz
        if (rz > rMaxZ) rMaxZ = rz
        i += 3
      }
    }

    val rawHeight = max(rMaxY - rMinY, 0.001f)
    val scale = targetHeightMeters.coerceIn(0.25f, 4.0f) / rawHeight
    val centerX = (rMinX + rMaxX) * 0.5f
    val centerZ = (rMinZ + rMaxZ) * 0.5f
    val groundY = rMinY

    // normMatrix = Scale(scale) * Translate(-centerX, -groundY, -centerZ) * preRotMat
    val normMatrix = FloatArray(16)
    val transScaleMat = floatArrayOf(
      scale, 0f, 0f, 0f,
      0f, scale, 0f, 0f,
      0f, 0f, scale, 0f,
      -centerX * scale, -groundY * scale, -centerZ * scale, 1f,
    )
    mulMat4(normMatrix, transScaleMat, preRotMat)

    val normNodes =
      List(nodeCount) { idx ->
        val bindNorm = FloatArray(16)
        mulMat4(bindNorm, normMatrix, bindWorlds[idx])
        val bindNormInv = invertRigidAffine4x4(bindNorm)
        GlbNodeDef(
          index = idx,
          name = nodeNames[idx],
          parentIndex = parentIdx[idx],
          localMatrix = localMats[idx],
          bindWorldMatrix = bindWorlds[idx],
          bindNormMatrix = bindNorm,
          bindNormInverse = bindNormInv,
          bindPosX = bindNorm[12],
          bindPosY = bindNorm[13],
          bindPosZ = bindNorm[14],
        )
      }

    // Check triangle winding flip only if normMatrix inverted handedness
    val normDetSign = determinant3x3(normMatrix) < 0f

    val finalParts = ArrayList<GlbMeshPartData>(collectedPrimitives.size)
    var nMinX = Float.POSITIVE_INFINITY
    var nMaxX = Float.NEGATIVE_INFINITY
    var nMinY = Float.POSITIVE_INFINITY
    var nMaxY = Float.NEGATIVE_INFINITY
    var nMinZ = Float.POSITIVE_INFINITY
    var nMaxZ = Float.NEGATIVE_INFINITY

    val skinAnimMats =
      skins.map { skin ->
        Array(skin.joints.size) { jIdx ->
          val out = FloatArray(16)
          val jNode = skin.joints[jIdx]
          val nMat = if (jNode in normNodes.indices) normNodes[jNode].bindNormMatrix else normMatrix
          mulMat4(out, nMat, skin.inverseBindMatrices[jIdx])
          out
        }
      }

    for (pIdx in collectedPrimitives.indices) {
      val prim = collectedPrimitives[pIdx]
      val vCount = prim.rawPositions.size / 3
      val rawVerts = FloatArray(vCount * 8)
      val normVerts = FloatArray(vCount * 8)
      val skinMatsForPrim = if (prim.skinIndex in skinAnimMats.indices) skinAnimMats[prim.skinIndex] else null
      val rigidMat =
        if (prim.skinIndex < 0 && prim.rigidNodeIndex in normNodes.indices) {
          normNodes[prim.rigidNodeIndex].bindNormMatrix
        } else {
          normMatrix
        }
      val rigidFlip = determinant3x3(rigidMat) < 0f
      val indicesForPrim =
        if (rigidFlip xor normDetSign) {
          prim.indices.copyOf().also { arr ->
            var k = 0
            while (k + 2 < arr.size) {
              val t = arr[k + 1]
              arr[k + 1] = arr[k + 2]
              arr[k + 2] = t
              k += 3
            }
          }
        } else {
          prim.indices
        }

      for (v in 0 until vCount) {
        val pOff = v * 3
        val uOff = v * 2
        val oOff = v * 8
        val px = prim.rawPositions[pOff]
        val py = prim.rawPositions[pOff + 1]
        val pz = prim.rawPositions[pOff + 2]
        val nx = prim.rawNormals[pOff]
        val ny = prim.rawNormals[pOff + 1]
        val nz = prim.rawNormals[pOff + 2]

        rawVerts[oOff] = px
        rawVerts[oOff + 1] = py
        rawVerts[oOff + 2] = pz
        rawVerts[oOff + 3] = nx
        rawVerts[oOff + 4] = ny
        rawVerts[oOff + 5] = nz
        rawVerts[oOff + 6] = prim.uvs[uOff]
        rawVerts[oOff + 7] = prim.uvs[uOff + 1]

        var wx = 0f; var wy = 0f; var wz = 0f
        var wnx = 0f; var wny = 0f; var wnz = 0f
        if (skinMatsForPrim != null && prim.joints != null && prim.weights != null) {
          val jOff = v * 4
          var totalW = 0f
          for (k in 0 until 4) {
            val w = prim.weights[jOff + k]
            if (w <= 1e-6f) continue
            val jIdx = prim.joints[jOff + k].coerceIn(0, skinMatsForPrim.lastIndex)
            val m = skinMatsForPrim[jIdx]
            wx += (m[0] * px + m[4] * py + m[8] * pz + m[12]) * w
            wy += (m[1] * px + m[5] * py + m[9] * pz + m[13]) * w
            wz += (m[2] * px + m[6] * py + m[10] * pz + m[14]) * w
            wnx += (m[0] * nx + m[4] * ny + m[8] * nz) * w
            wny += (m[1] * nx + m[5] * ny + m[9] * nz) * w
            wnz += (m[2] * nx + m[6] * ny + m[10] * nz) * w
            totalW += w
          }
          if (totalW <= 1e-5f) {
            wx = normMatrix[0] * px + normMatrix[4] * py + normMatrix[8] * pz + normMatrix[12]
            wy = normMatrix[1] * px + normMatrix[5] * py + normMatrix[9] * pz + normMatrix[13]
            wz = normMatrix[2] * px + normMatrix[6] * py + normMatrix[10] * pz + normMatrix[14]
            wnx = nx; wny = ny; wnz = nz
          }
        } else {
          wx = rigidMat[0] * px + rigidMat[4] * py + rigidMat[8] * pz + rigidMat[12]
          wy = rigidMat[1] * px + rigidMat[5] * py + rigidMat[9] * pz + rigidMat[13]
          wz = rigidMat[2] * px + rigidMat[6] * py + rigidMat[10] * pz + rigidMat[14]
          wnx = rigidMat[0] * nx + rigidMat[4] * ny + rigidMat[8] * nz
          wny = rigidMat[1] * nx + rigidMat[5] * ny + rigidMat[9] * nz
          wnz = rigidMat[2] * nx + rigidMat[6] * ny + rigidMat[10] * nz
        }

        val nLen = sqrt(wnx * wnx + wny * wny + wnz * wnz).coerceAtLeast(1e-5f)
        if (wx < nMinX) nMinX = wx
        if (wx > nMaxX) nMaxX = wx
        if (wy < nMinY) nMinY = wy
        if (wy > nMaxY) nMaxY = wy
        if (wz < nMinZ) nMinZ = wz
        if (wz > nMaxZ) nMaxZ = wz

        normVerts[oOff] = wx
        normVerts[oOff + 1] = wy
        normVerts[oOff + 2] = wz
        normVerts[oOff + 3] = wnx / nLen
        normVerts[oOff + 4] = wny / nLen
        normVerts[oOff + 5] = wnz / nLen
        normVerts[oOff + 6] = prim.uvs[uOff]
        normVerts[oOff + 7] = prim.uvs[uOff + 1]
      }

      splitIntoEs2Parts(
        normVertices = normVerts,
        rawVertices = rawVerts,
        indices = indicesForPrim,
        material = prim.material,
        skinIndex = prim.skinIndex,
        rigidNodeIndex = prim.rigidNodeIndex,
        joints = prim.joints,
        weights = prim.weights,
        out = finalParts,
      )
    }

    return ParsedGlbModel(
      parts = finalParts,
      images = images,
      nodes = normNodes,
      dfsOrder = dfsOrder,
      skins = skins,
      rig = rig,
      normMatrix = normMatrix,
      rawBounds = rawBounds,
      normalizedBounds = GlbBounds(nMinX, nMaxX, nMinY, nMaxY, nMinZ, nMaxZ),
      appliedScale = scale,
      groundShiftY = -groundY * scale,
      rotated180Y = rotated180Y,
    )
  }

  /**
   * Reusable scratch workspace for zero-allocation real-time skeletal animation on CPU.
   */
  class PoseWorkspace(val model: ParsedGlbModel) {
    val nodeAnimMats = Array(model.nodes.size) { FloatArray(16) }
    val nodeDeltaMats = Array(model.nodes.size) { FloatArray(16) }
    val skinAnimMats = model.skins.map { skin -> Array(skin.joints.size) { FloatArray(16) } }
    val partAnimatedVerts = model.parts.map { it.vertices.copyOf() }
    val rightHandPos = FloatArray(3)
    private val rotX = FloatArray(16)
    private val rotY = FloatArray(16)
    private val rotZ = FloatArray(16)
    private val tmp1 = FloatArray(16)
    private val tmp2 = FloatArray(16)
    private val localDelta = FloatArray(16)

    fun evaluatePose(pose: GlbCharacterPose) {
      val nodes = model.nodes
      val rig = model.rig
      if (nodes.isEmpty()) return

      for (nodeIdx in model.dfsOrder) {
        val node = nodes[nodeIdx]
        val hasParent = node.parentIndex in nodes.indices
        val parentDelta = if (hasParent) nodeDeltaMats[node.parentIndex] else null

        computeNodeDelta(nodeIdx, rig, pose, localDelta)
        if (parentDelta != null) {
          mulMat4(nodeDeltaMats[nodeIdx], parentDelta, localDelta)
        } else {
          System.arraycopy(localDelta, 0, nodeDeltaMats[nodeIdx], 0, 16)
        }
        mulMat4(nodeAnimMats[nodeIdx], nodeDeltaMats[nodeIdx], node.bindNormMatrix)
      }

      if (rig.rightHand in nodeAnimMats.indices) {
        val m = nodeAnimMats[rig.rightHand]
        rightHandPos[0] = m[12]
        rightHandPos[1] = m[13]
        rightHandPos[2] = m[14]
      } else {
        rightHandPos[0] = -0.26f
        rightHandPos[1] = 1.02f
        rightHandPos[2] = 0.28f
      }

      for (sIdx in model.skins.indices) {
        val skin = model.skins[sIdx]
        val dstArr = skinAnimMats[sIdx]
        for (j in skin.joints.indices) {
          val jNode = skin.joints[j]
          val nMat = if (jNode in nodeAnimMats.indices) nodeAnimMats[jNode] else model.normMatrix
          mulMat4(dstArr[j], nMat, skin.inverseBindMatrices[j])
        }
      }

      for (pIdx in model.parts.indices) {
        val part = model.parts[pIdx]
        val src = part.rawVertices
        val dst = partAnimatedVerts[pIdx]
        val vCount = src.size / 8
        val skinMats = if (part.skinIndex in skinAnimMats.indices) skinAnimMats[part.skinIndex] else null
        val rigidMat =
          if (part.skinIndex < 0 && part.rigidNodeIndex in nodeAnimMats.indices) {
            nodeAnimMats[part.rigidNodeIndex]
          } else {
            model.normMatrix
          }

        if (skinMats != null && part.joints != null && part.weights != null) {
          val jArr = part.joints
          val wArr = part.weights
          val maxJoint = skinMats.lastIndex
          for (v in 0 until vCount) {
            val o = v * 8
            val px = src[o]; val py = src[o + 1]; val pz = src[o + 2]
            val nx = src[o + 3]; val ny = src[o + 4]; val nz = src[o + 5]
            val jOff = v * 4
            var wx = 0f; var wy = 0f; var wz = 0f
            var wnx = 0f; var wny = 0f; var wnz = 0f
            for (k in 0 until 4) {
              val w = wArr[jOff + k]
              if (w <= 1e-6f) continue
              val m = skinMats[jArr[jOff + k].coerceIn(0, maxJoint)]
              wx += (m[0] * px + m[4] * py + m[8] * pz + m[12]) * w
              wy += (m[1] * px + m[5] * py + m[9] * pz + m[13]) * w
              wz += (m[2] * px + m[6] * py + m[10] * pz + m[14]) * w
              wnx += (m[0] * nx + m[4] * ny + m[8] * nz) * w
              wny += (m[1] * nx + m[5] * ny + m[9] * nz) * w
              wnz += (m[2] * nx + m[6] * ny + m[10] * nz) * w
            }
            val nLen = sqrt(wnx * wnx + wny * wny + wnz * wnz).coerceAtLeast(1e-5f)
            dst[o] = wx
            dst[o + 1] = wy
            dst[o + 2] = wz
            dst[o + 3] = wnx / nLen
            dst[o + 4] = wny / nLen
            dst[o + 5] = wnz / nLen
          }
        } else {
          for (v in 0 until vCount) {
            val o = v * 8
            val px = src[o]; val py = src[o + 1]; val pz = src[o + 2]
            val nx = src[o + 3]; val ny = src[o + 4]; val nz = src[o + 5]
            dst[o] = rigidMat[0] * px + rigidMat[4] * py + rigidMat[8] * pz + rigidMat[12]
            dst[o + 1] = rigidMat[1] * px + rigidMat[5] * py + rigidMat[9] * pz + rigidMat[13]
            dst[o + 2] = rigidMat[2] * px + rigidMat[6] * py + rigidMat[10] * pz + rigidMat[14]
            val wnx = rigidMat[0] * nx + rigidMat[4] * ny + rigidMat[8] * nz
            val wny = rigidMat[1] * nx + rigidMat[5] * ny + rigidMat[9] * nz
            val wnz = rigidMat[2] * nx + rigidMat[6] * ny + rigidMat[10] * nz
            val nLen = sqrt(wnx * wnx + wny * wny + wnz * wnz).coerceAtLeast(1e-5f)
            dst[o + 3] = wnx / nLen
            dst[o + 4] = wny / nLen
            dst[o + 5] = wnz / nLen
          }
        }
      }
    }

    private fun computeNodeDelta(
      nodeIdx: Int,
      rig: HumanRigMapping,
      pose: GlbCharacterPose,
      out: FloatArray,
    ) {
      var rx = 0f
      var ry = 0f
      var rz = 0f
      var tx = 0f
      var ty = 0f

      when (nodeIdx) {
        rig.hips -> {
          tx = pose.pelvisSwayX
          ty = pose.pelvisBounceY
          rx = pose.rootPitchDeg
          ry = pose.pelvisYawDeg
          rz = pose.pelvisRollDeg
        }
        rig.spine1 -> {
          val f = if (rig.spine2 >= 0 || rig.spine3 >= 0) 0.40f else 1.0f
          rx = pose.torsoLeanDeg * f
          ry = pose.torsoYawDeg * f
        }
        rig.spine2 -> {
          val f = if (rig.spine3 >= 0) 0.35f else 0.60f
          rx = pose.torsoLeanDeg * f
          ry = pose.torsoYawDeg * f
        }
        rig.spine3 -> {
          rx = pose.torsoLeanDeg * 0.25f
          ry = pose.torsoYawDeg * 0.25f
        }
        rig.neck -> {
          rx = pose.headPitchDeg * 0.40f
          ry = pose.headYawDeg * 0.40f
        }
        rig.head -> {
          rx = pose.headPitchDeg * 0.60f
          ry = pose.headYawDeg * 0.60f
        }
        rig.leftThigh -> rx = pose.leftThighPitchDeg
        rig.leftShin -> rx = pose.leftKneeBendDeg
        rig.leftFoot -> rx = pose.leftAnklePitchDeg
        rig.rightThigh -> {
          rx = pose.rightThighPitchDeg
          rz = -pose.rightThighOutDeg
        }
        rig.rightShin -> rx = pose.rightKneeBendDeg
        rig.rightFoot -> rx = pose.rightAnklePitchDeg
        rig.leftArm -> {
          rx = pose.leftArmPitchDeg
          rz = pose.leftArmOutDeg
        }
        rig.leftForearm -> rx = pose.leftForearmBendDeg
        rig.rightArm -> {
          rx = pose.rightArmPitchDeg
          rz = -pose.rightArmOutDeg
        }
        rig.rightForearm -> rx = pose.rightForearmBendDeg
        else -> {
          setIdentity4(out)
          return
        }
      }

      if (abs(rx) < 0.01f && abs(ry) < 0.01f && abs(rz) < 0.01f && abs(tx) < 1e-4f && abs(ty) < 1e-4f) {
        setIdentity4(out)
        return
      }

      val node = model.nodes[nodeIdx]
      val px = node.bindPosX
      val py = node.bindPosY
      val pz = node.bindPosZ

      setRotateY(rotY, ry)
      setRotateX(rotX, rx)
      setRotateZ(rotZ, rz)
      mulMat4(tmp1, rotY, rotX)
      mulMat4(tmp2, tmp1, rotZ)

      // out = Translate(px + tx, py + ty, pz) * R * Translate(-px, -py, -pz)
      System.arraycopy(tmp2, 0, out, 0, 16)
      out[12] = px + tx - (tmp2[0] * px + tmp2[4] * py + tmp2[8] * pz)
      out[13] = py + ty - (tmp2[1] * px + tmp2[5] * py + tmp2[9] * pz)
      out[14] = pz - (tmp2[2] * px + tmp2[6] * py + tmp2[10] * pz)
      out[15] = 1f
    }
  }

  private fun evaluateBindPositions(
    prim: RawPrimitiveGeometry,
    bindWorlds: Array<FloatArray>,
    bindSkinJointMats: List<Array<FloatArray>>,
  ): FloatArray {
    val vCount = prim.rawPositions.size / 3
    val out = FloatArray(prim.rawPositions.size)
    val skinMats = if (prim.skinIndex in bindSkinJointMats.indices) bindSkinJointMats[prim.skinIndex] else null
    val rigidMat =
      if (prim.skinIndex < 0 && prim.rigidNodeIndex in bindWorlds.indices) {
        bindWorlds[prim.rigidNodeIndex]
      } else {
        FloatArray(16).apply { setIdentity4(this) }
      }

    for (v in 0 until vCount) {
      val o = v * 3
      val px = prim.rawPositions[o]
      val py = prim.rawPositions[o + 1]
      val pz = prim.rawPositions[o + 2]
      if (skinMats != null && prim.joints != null && prim.weights != null) {
        val jOff = v * 4
        var sx = 0f; var sy = 0f; var sz = 0f
        var sumW = 0f
        for (k in 0 until 4) {
          val w = prim.weights[jOff + k]
          if (w <= 1e-6f) continue
          val m = skinMats[prim.joints[jOff + k].coerceIn(0, skinMats.lastIndex)]
          sx += (m[0] * px + m[4] * py + m[8] * pz + m[12]) * w
          sy += (m[1] * px + m[5] * py + m[9] * pz + m[13]) * w
          sz += (m[2] * px + m[6] * py + m[10] * pz + m[14]) * w
          sumW += w
        }
        if (sumW > 1e-5f) {
          out[o] = sx; out[o + 1] = sy; out[o + 2] = sz
        } else {
          out[o] = px; out[o + 1] = py; out[o + 2] = pz
        }
      } else {
        out[o] = rigidMat[0] * px + rigidMat[4] * py + rigidMat[8] * pz + rigidMat[12]
        out[o + 1] = rigidMat[1] * px + rigidMat[5] * py + rigidMat[9] * pz + rigidMat[13]
        out[o + 2] = rigidMat[2] * px + rigidMat[6] * py + rigidMat[10] * pz + rigidMat[14]
      }
    }
    return out
  }

  private fun shouldRotate180Y(
    rig: HumanRigMapping,
    bindWorlds: Array<FloatArray>,
    nodeNames: Array<String>,
    preRotMat: FloatArray,
  ): Boolean {
    fun worldZAfterPreRot(idx: Int): Float? {
      if (idx !in bindWorlds.indices) return null
      val m = bindWorlds[idx]
      return preRotMat[2] * m[12] + preRotMat[6] * m[13] + preRotMat[10] * m[14] + preRotMat[14]
    }
    // Check toes vs feet along Z
    val lToeZ = worldZAfterPreRot(rig.leftToes)
    val lFootZ = worldZAfterPreRot(rig.leftFoot)
    if (lToeZ != null && lFootZ != null && abs(lToeZ - lFootZ) > 0.02f) {
      return lToeZ < lFootZ
    }
    val rToeZ = worldZAfterPreRot(rig.rightToes)
    val rFootZ = worldZAfterPreRot(rig.rightFoot)
    if (rToeZ != null && rFootZ != null && abs(rToeZ - rFootZ) > 0.02f) {
      return rToeZ < rFootZ
    }
    // Fallback: check eye/glasses nodes vs head Z
    val headZ = worldZAfterPreRot(rig.head)
    if (headZ != null) {
      val eyeIdx = nodeNames.indexOfFirst {
        val lc = it.lowercase()
        lc.contains("eye") && !lc.contains("eyelash")
      }
      val eyeZ = worldZAfterPreRot(eyeIdx)
      if (eyeZ != null && abs(eyeZ - headZ) > 0.01f) {
        return eyeZ < headZ
      }
    }
    return false
  }

  private fun detectHumanRig(nodeNames: Array<String>): HumanRigMapping {
    var hips = -1
    var spine1 = -1
    var spine2 = -1
    var spine3 = -1
    var neck = -1
    var head = -1
    var leftArm = -1
    var leftForearm = -1
    var leftHand = -1
    var rightArm = -1
    var rightForearm = -1
    var rightHand = -1
    var leftThigh = -1
    var leftShin = -1
    var leftFoot = -1
    var rightThigh = -1
    var rightShin = -1
    var rightFoot = -1
    var leftToes = -1
    var rightToes = -1

    fun isLeft(lc: String): Boolean =
      lc.contains("left") || lc.startsWith("l ") || lc.startsWith("l_") ||
        lc.contains(".l") || lc.contains("_l_") || lc.contains("mixamorig:left")

    fun isRight(lc: String): Boolean =
      lc.contains("right") || lc.startsWith("r ") || lc.startsWith("r_") ||
        lc.contains(".r") || lc.contains("_r_") || lc.contains("mixamorig:right")

    for (i in nodeNames.indices) {
      val lc = nodeNames[i].lowercase().trim()
      if (lc.isEmpty()) continue
      val left = isLeft(lc)
      val right = isRight(lc)

      when {
        hips < 0 && (lc.contains("hips") || lc.contains("pelvis")) -> hips = i
        lc.contains("spine1") -> if (spine1 < 0) spine1 = i
        lc.contains("spine2") -> if (spine2 < 0) spine2 = i
        lc.contains("spine3") -> if (spine3 < 0) spine3 = i
        lc.contains("spine") && spine1 < 0 -> spine1 = i
        neck < 0 && lc.contains("neck") -> neck = i
        head < 0 && (lc.startsWith("head") || lc.contains(":head") || lc.contains("_head") || lc.contains(" head")) ->
          if (!lc.contains("headtop") && !lc.contains("cpd_")) head = i
        left && !right -> when {
          leftForearm < 0 && (lc.contains("forearm") || lc.contains("lowerarm")) -> leftForearm = i
          leftArm < 0 && (lc.contains("upperarm") || (lc.contains("arm") && !lc.contains("forearm") && !lc.contains("armature"))) -> leftArm = i
          leftHand < 0 && lc.contains("hand") -> leftHand = i
          leftToes < 0 && (lc.contains("toe") || lc.contains("toes")) -> leftToes = i
          leftFoot < 0 && (lc.contains("foot") || lc.contains("ankle")) -> leftFoot = i
          leftShin < 0 && (lc.contains("knee") || lc.contains("shin") || lc.contains("calf") || lc.contains("lowerleg") || lc.contains("upleg") == false && lc.contains("leg") && (lc.contains("low") || lc.contains("2"))) -> leftShin = i
          leftThigh < 0 && (lc.contains("thigh") || lc.contains("upleg") || (lc.contains("leg") && !lc.contains("lower"))) -> leftThigh = i
        }
        right && !left -> when {
          rightForearm < 0 && (lc.contains("forearm") || lc.contains("lowerarm")) -> rightForearm = i
          rightArm < 0 && (lc.contains("upperarm") || (lc.contains("arm") && !lc.contains("forearm") && !lc.contains("armature"))) -> rightArm = i
          rightHand < 0 && lc.contains("hand") -> rightHand = i
          rightToes < 0 && (lc.contains("toe") || lc.contains("toes")) -> rightToes = i
          rightFoot < 0 && (lc.contains("foot") || lc.contains("ankle")) -> rightFoot = i
          rightShin < 0 && (lc.contains("knee") || lc.contains("shin") || lc.contains("calf") || lc.contains("lowerleg")) -> rightShin = i
          rightThigh < 0 && (lc.contains("thigh") || lc.contains("upleg") || (lc.contains("leg") && !lc.contains("lower"))) -> rightThigh = i
        }
      }
    }

    return HumanRigMapping(
      hips = hips,
      spine1 = spine1,
      spine2 = spine2,
      spine3 = spine3,
      neck = neck,
      head = head,
      leftArm = leftArm,
      leftForearm = leftForearm,
      leftHand = leftHand,
      rightArm = rightArm,
      rightForearm = rightForearm,
      rightHand = rightHand,
      leftThigh = leftThigh,
      leftShin = leftShin,
      leftFoot = leftFoot,
      rightThigh = rightThigh,
      rightShin = rightShin,
      rightFoot = rightFoot,
      leftToes = leftToes,
      rightToes = rightToes,
    )
  }

  private fun parseSkins(
    skinsArr: JArr?,
    bufferViews: List<BufferViewDef>,
    buffers: List<ByteArray>,
    accessors: List<AccessorDef>,
  ): List<GlbSkinDef> {
    if (skinsArr == null) return emptyList()
    return skinsArr.items.map { item ->
      val obj = item as? JObj ?: return@map GlbSkinDef(IntArray(0), emptyArray())
      val jointsArr = obj["joints"] as? JArr
      val joints =
        if (jointsArr != null) {
          IntArray(jointsArr.items.size) { i -> (jointsArr.items[i] as? JNum)?.value?.toInt() ?: 0 }
        } else {
          IntArray(0)
        }
      val ibmAccIdx = obj.int("inverseBindMatrices", -1)
      val ibms =
        if (ibmAccIdx in accessors.indices) {
          val flat = readFloatAttribute(accessors[ibmAccIdx], 16, bufferViews, buffers)
          Array(joints.size) { i ->
            val off = i * 16
            if (off + 16 <= flat.size) {
              flat.copyOfRange(off, off + 16)
            } else {
              FloatArray(16).apply { setIdentity4(this) }
            }
          }
        } else {
          Array(joints.size) { FloatArray(16).apply { setIdentity4(this) } }
        }
      GlbSkinDef(joints = joints, inverseBindMatrices = ibms)
    }
  }

  private fun splitIntoEs2Parts(
    normVertices: FloatArray,
    rawVertices: FloatArray,
    indices: ShortArray,
    material: GlbMaterialData,
    skinIndex: Int,
    rigidNodeIndex: Int,
    joints: IntArray?,
    weights: FloatArray?,
    out: MutableList<GlbMeshPartData>,
  ) {
    val vertexCount = normVertices.size / 8
    if (vertexCount <= MAX_ES2_VERTICES_PER_PART) {
      out.add(
        GlbMeshPartData(
          vertices = normVertices,
          rawVertices = rawVertices,
          indices = indices,
          material = material,
          skinIndex = skinIndex,
          rigidNodeIndex = rigidNodeIndex,
          joints = joints,
          weights = weights,
        ),
      )
      return
    }
    splitLargePrimitive(normVertices, rawVertices, indices.map { it.toInt() and 0xFFFF }.toIntArray(), material, skinIndex, rigidNodeIndex, joints, weights, out)
  }

  private fun splitLargePrimitive(
    normVertices: FloatArray,
    rawVertices: FloatArray,
    indices: IntArray,
    material: GlbMaterialData,
    skinIndex: Int,
    rigidNodeIndex: Int,
    joints: IntArray?,
    weights: FloatArray?,
    out: MutableList<GlbMeshPartData>,
  ) {
    val remap = HashMap<Int, Int>(4096)
    val chunkNorm = FloatArrayList(4096 * 8)
    val chunkRaw = FloatArrayList(4096 * 8)
    val chunkIdx = ShortArrayList(8192)
    val chunkJoints = if (joints != null) IntArrayList(4096 * 4) else null
    val chunkWeights = if (weights != null) FloatArrayList(4096 * 4) else null

    fun flushChunk() {
      if (chunkIdx.size > 0) {
        out.add(
          GlbMeshPartData(
            vertices = chunkNorm.toArray(),
            rawVertices = chunkRaw.toArray(),
            indices = chunkIdx.toArray(),
            material = material,
            skinIndex = skinIndex,
            rigidNodeIndex = rigidNodeIndex,
            joints = chunkJoints?.toArray(),
            weights = chunkWeights?.toArray(),
          ),
        )
      }
      remap.clear()
      chunkNorm.clear()
      chunkRaw.clear()
      chunkIdx.clear()
      chunkJoints?.clear()
      chunkWeights?.clear()
    }

    var i = 0
    while (i + 2 < indices.size) {
      val i0 = indices[i]
      val i1 = indices[i + 1]
      val i2 = indices[i + 2]
      val neededNew =
        (if (remap.containsKey(i0)) 0 else 1) +
          (if (remap.containsKey(i1)) 0 else 1) +
          (if (remap.containsKey(i2)) 0 else 1)
      if (remap.size + neededNew > MAX_ES2_VERTICES_PER_PART) {
        flushChunk()
      }
      for (srcIdx in intArrayOf(i0, i1, i2)) {
        val local = remap.getOrPut(srcIdx) {
          val nextId = remap.size
          val base = srcIdx * 8
          for (k in 0 until 8) {
            chunkNorm.add(normVertices[base + k])
            chunkRaw.add(rawVertices[base + k])
          }
          if (joints != null && weights != null && chunkJoints != null && chunkWeights != null) {
            val jBase = srcIdx * 4
            for (k in 0 until 4) {
              chunkJoints.add(joints[jBase + k])
              chunkWeights.add(weights[jBase + k])
            }
          }
          nextId
        }
        chunkIdx.add(local.toShort())
      }
      i += 3
    }
    flushChunk()
  }

  private fun splitIntoEs2Parts(
    normVertices: FloatArray,
    rawVertices: FloatArray,
    indices: IntArray,
    material: GlbMaterialData,
    skinIndex: Int,
    rigidNodeIndex: Int,
    joints: IntArray?,
    weights: FloatArray?,
    out: MutableList<GlbMeshPartData>,
  ) {
    val vertexCount = normVertices.size / 8
    val maxIdx = indices.maxOrNull() ?: -1
    if (vertexCount <= MAX_ES2_VERTICES_PER_PART && maxIdx in 0..MAX_ES2_VERTICES_PER_PART) {
      val shortIdx = ShortArray(indices.size) { indices[it].toShort() }
      out.add(
        GlbMeshPartData(
          vertices = normVertices,
          rawVertices = rawVertices,
          indices = shortIdx,
          material = material,
          skinIndex = skinIndex,
          rigidNodeIndex = rigidNodeIndex,
          joints = joints,
          weights = weights,
        ),
      )
      return
    }
    splitLargePrimitive(normVertices, rawVertices, indices, material, skinIndex, rigidNodeIndex, joints, weights, out)
  }

  private data class BufferViewDef(
    val buffer: Int,
    val byteOffset: Int,
    val byteLength: Int,
    val byteStride: Int,
  )

  private data class AccessorDef(
    val bufferView: Int,
    val byteOffset: Int,
    val componentType: Int,
    val count: Int,
    val type: String,
    val normalized: Boolean,
  ) {
    val numComponents: Int
      get() =
        when (type) {
          "SCALAR" -> 1
          "VEC2" -> 2
          "VEC3" -> 3
          "VEC4", "MAT2" -> 4
          "MAT3" -> 9
          "MAT4" -> 16
          else -> 1
        }
  }

  private data class RawPrimitiveGeometry(
    val rawPositions: FloatArray,
    val rawNormals: FloatArray,
    val uvs: FloatArray,
    val indices: IntArray,
    val material: GlbMaterialData,
    val skinIndex: Int,
    val rigidNodeIndex: Int,
    val joints: IntArray?,
    val weights: FloatArray?,
  )

  private fun parseBuffers(buffersArr: JArr?, binChunk: ByteArray?): List<ByteArray> {
    if (buffersArr == null || buffersArr.items.isEmpty()) {
      return if (binChunk != null) listOf(binChunk) else emptyList()
    }
    return buffersArr.items.mapIndexed { idx, item ->
      val obj = item as? JObj
      val uri = obj?.str("uri")
      when {
        uri == null && idx == 0 && binChunk != null -> binChunk
        uri != null && uri.startsWith("data:") -> decodeDataUri(uri)
        else -> binChunk ?: ByteArray(0)
      }
    }
  }

  private fun parseBufferViews(arr: JArr?): List<BufferViewDef> {
    if (arr == null) return emptyList()
    return arr.items.mapNotNull { it as? JObj }.map { obj ->
      BufferViewDef(
        buffer = obj.int("buffer", 0),
        byteOffset = obj.int("byteOffset", 0),
        byteLength = obj.int("byteLength", 0),
        byteStride = obj.int("byteStride", 0),
      )
    }
  }

  private fun parseAccessors(arr: JArr?): List<AccessorDef> {
    if (arr == null) return emptyList()
    return arr.items.mapNotNull { it as? JObj }.map { obj ->
      AccessorDef(
        bufferView = obj.int("bufferView", -1),
        byteOffset = obj.int("byteOffset", 0),
        componentType = obj.int("componentType", 5126),
        count = obj.int("count", 0),
        type = obj.str("type") ?: "SCALAR",
        normalized = obj.bool("normalized", false),
      )
    }
  }

  private fun parseImages(
    arr: JArr?,
    bufferViews: List<BufferViewDef>,
    buffers: List<ByteArray>,
  ): List<GlbImageData> {
    if (arr == null) return emptyList()
    return arr.items.map { item ->
      val obj = item as? JObj ?: return@map GlbImageData("image/png", null)
      val mime = obj.str("mimeType") ?: "image/png"
      val bvIdx = obj.int("bufferView", -1)
      val uri = obj.str("uri")
      val bytes =
        when {
          bvIdx in bufferViews.indices -> {
            val bv = bufferViews[bvIdx]
            val buf = buffers.getOrNull(bv.buffer)
            if (buf != null && bv.byteOffset + bv.byteLength <= buf.size) {
              buf.copyOfRange(bv.byteOffset, bv.byteOffset + bv.byteLength)
            } else {
              null
            }
          }
          uri != null && uri.startsWith("data:") -> decodeDataUri(uri)
          else -> null
        }
      GlbImageData(mimeType = mime, encodedBytes = bytes)
    }
  }

  private fun parseTextures(arr: JArr?): List<Int> {
    if (arr == null) return emptyList()
    return arr.items.map { item ->
      val obj = item as? JObj
      obj?.int("source", -1) ?: -1
    }
  }

  private fun parseMaterials(arr: JArr?, textureSources: List<Int>): List<GlbMaterialData> {
    if (arr == null) return emptyList()
    return arr.items.map { item ->
      val obj = item as? JObj ?: return@map GlbMaterialData()
      val pbr = obj["pbrMetallicRoughness"] as? JObj
      val factorArr = pbr?.get("baseColorFactor") as? JArr
      val baseColor =
        if (factorArr != null && factorArr.items.size >= 3) {
          floatArrayOf(
            factorArr.floatAt(0, 1f),
            factorArr.floatAt(1, 1f),
            factorArr.floatAt(2, 1f),
            factorArr.floatAt(3, 1f),
          )
        } else {
          floatArrayOf(1f, 1f, 1f, 1f)
        }
      val baseTexObj = pbr?.get("baseColorTexture") as? JObj
      val texIndex = baseTexObj?.int("index", -1) ?: -1
      val imgIndex = textureSources.getOrElse(texIndex) { -1 }

      val metallic = pbr?.float("metallicFactor", 0.35f) ?: 0.25f
      val roughness = pbr?.float("roughnessFactor", 0.65f) ?: 0.65f
      val emissiveArr = obj["emissiveFactor"] as? JArr
      val emissive =
        if (emissiveArr != null && emissiveArr.items.size >= 3) {
          max(emissiveArr.floatAt(0, 0f), max(emissiveArr.floatAt(1, 0f), emissiveArr.floatAt(2, 0f))) * 0.45f
        } else {
          0f
        }
      val doubleSided = obj.bool("doubleSided", false)
      GlbMaterialData(
        baseColor = baseColor,
        metallic = metallic.coerceIn(0f, 1f),
        roughness = roughness.coerceIn(0.08f, 1f),
        emissive = emissive,
        baseColorImageIndex = imgIndex,
        doubleSided = doubleSided,
      )
    }
  }

  private fun resolveRootNodes(root: JObj, nodesArr: JArr, parentIdx: IntArray): List<Int> {
    val scenesArr = root["scenes"] as? JArr
    val sceneIdx = root.int("scene", 0)
    val sceneObj = scenesArr?.objAt(sceneIdx) ?: scenesArr?.objAt(0)
    val sceneNodes = sceneObj?.get("nodes") as? JArr
    if (sceneNodes != null && sceneNodes.items.isNotEmpty()) {
      return sceneNodes.items.mapNotNull { (it as? JNum)?.value?.toInt() }
    }
    return parentIdx.indices.filter { parentIdx[it] < 0 }
  }

  private fun computeNodeMatrix(nodeObj: JObj): FloatArray {
    val mArr = nodeObj["matrix"] as? JArr
    if (mArr != null && mArr.items.size == 16) {
      return FloatArray(16) { mArr.floatAt(it, if (it % 5 == 0) 1f else 0f) }
    }
    val tArr = nodeObj["translation"] as? JArr
    val tx = tArr?.floatAt(0, 0f) ?: 0f
    val ty = tArr?.floatAt(1, 0f) ?: 0f
    val tz = tArr?.floatAt(2, 0f) ?: 0f

    val rArr = nodeObj["rotation"] as? JArr
    var qx = rArr?.floatAt(0, 0f) ?: 0f
    var qy = rArr?.floatAt(1, 0f) ?: 0f
    var qz = rArr?.floatAt(2, 0f) ?: 0f
    var qw = rArr?.floatAt(3, 1f) ?: 1f
    val qLen = sqrt(qx * qx + qy * qy + qz * qz + qw * qw)
    if (qLen > 1e-6f) {
      qx /= qLen; qy /= qLen; qz /= qLen; qw /= qLen
    } else {
      qx = 0f; qy = 0f; qz = 0f; qw = 1f
    }

    val sArr = nodeObj["scale"] as? JArr
    val sx = sArr?.floatAt(0, 1f) ?: 1f
    val sy = sArr?.floatAt(1, 1f) ?: 1f
    val sz = sArr?.floatAt(2, 1f) ?: 1f

    val xx = qx * qx; val yy = qy * qy; val zz = qz * qz
    val xy = qx * qy; val xz = qx * qz; val yz = qy * qz
    val wx = qw * qx; val wy = qw * qy; val wz = qw * qz

    return floatArrayOf(
      (1f - 2f * (yy + zz)) * sx, (2f * (xy + wz)) * sx, (2f * (xz - wy)) * sx, 0f,
      (2f * (xy - wz)) * sy, (1f - 2f * (xx + zz)) * sy, (2f * (yz + wx)) * sy, 0f,
      (2f * (xz + wy)) * sz, (2f * (yz - wx)) * sz, (1f - 2f * (xx + yy)) * sz, 0f,
      tx, ty, tz, 1f,
    )
  }

  private fun extractMeshPrimitives(
    meshObj: JObj,
    nodeIndex: Int,
    skinIndex: Int,
    buffers: List<ByteArray>,
    bufferViews: List<BufferViewDef>,
    accessors: List<AccessorDef>,
    materials: List<GlbMaterialData>,
    images: MutableList<GlbImageData>,
    out: MutableList<RawPrimitiveGeometry>,
  ) {
    val prims = meshObj["primitives"] as? JArr ?: return

    for (item in prims.items) {
      val prim = item as? JObj ?: continue
      val mode = prim.int("mode", 4)
      if (mode != 4 && mode != 5 && mode != 6) continue
      val attrs = prim["attributes"] as? JObj ?: continue
      val posAccIdx = attrs.int("POSITION", -1)
      if (posAccIdx !in accessors.indices) continue

      val rawPos = readFloatAttribute(accessors[posAccIdx], 3, bufferViews, buffers)
      val vCount = rawPos.size / 3
      if (vCount <= 0) continue

      val indicesAccIdx = prim.int("indices", -1)
      val rawIndices =
        if (indicesAccIdx in accessors.indices) {
          readIntIndices(accessors[indicesAccIdx], bufferViews, buffers)
        } else {
          IntArray(vCount) { it }
        }
      val triIndices = convertModeToTriangles(rawIndices, mode)
      if (triIndices.size < 3) continue

      val normAccIdx = attrs.int("NORMAL", -1)
      val rawNorms =
        if (normAccIdx in accessors.indices) {
          val n = readFloatAttribute(accessors[normAccIdx], 3, bufferViews, buffers)
          FloatArray(vCount * 3) { i -> n.getOrElse(i) { if (i % 3 == 1) 1f else 0f } }
        } else {
          computeSmoothNormals(rawPos, triIndices)
        }

      var material = materials.getOrElse(prim.int("material", -1)) { GlbMaterialData() }
      val uvAccIdx = attrs.int("TEXCOORD_0", -1)
      var uvs =
        if (uvAccIdx in accessors.indices) {
          val rawUv = readFloatAttribute(accessors[uvAccIdx], 2, bufferViews, buffers)
          FloatArray(vCount * 2) { i -> rawUv.getOrElse(i) { 0f } }
        } else {
          FloatArray(vCount * 2)
        }

      val jointsAccIdx = attrs.int("JOINTS_0", -1)
      val weightsAccIdx = attrs.int("WEIGHTS_0", -1)
      var jointsArr: IntArray? = null
      var weightsArr: FloatArray? = null
      if (skinIndex >= 0 && jointsAccIdx in accessors.indices && weightsAccIdx in accessors.indices) {
        jointsArr = readJointVec4(accessors[jointsAccIdx], bufferViews, buffers)
        val rawW = readFloatAttribute(accessors[weightsAccIdx], 4, bufferViews, buffers)
        weightsArr =
          FloatArray(vCount * 4).also { dst ->
            for (v in 0 until vCount) {
              val o = v * 4
              val w0 = rawW.getOrElse(o) { 0f }.coerceAtLeast(0f)
              val w1 = rawW.getOrElse(o + 1) { 0f }.coerceAtLeast(0f)
              val w2 = rawW.getOrElse(o + 2) { 0f }.coerceAtLeast(0f)
              val w3 = rawW.getOrElse(o + 3) { 0f }.coerceAtLeast(0f)
              val sum = w0 + w1 + w2 + w3
              if (sum > 1e-5f) {
                dst[o] = w0 / sum
                dst[o + 1] = w1 / sum
                dst[o + 2] = w2 / sum
                dst[o + 3] = w3 / sum
              } else {
                dst[o] = 1f
              }
            }
          }
      }

      val colorAccIdx = attrs.int("COLOR_0", -1)
      if (material.baseColorImageIndex < 0 && colorAccIdx in accessors.indices) {
        val acc = accessors[colorAccIdx]
        val comps = acc.numComponents.coerceIn(3, 4)
        val rawCols = readFloatAttribute(acc, comps, bufferViews, buffers)
        val rgba = ByteArray(vCount * 4)
        val palUvs = FloatArray(vCount * 2)
        for (v in 0 until vCount) {
          val cOff = v * comps
          val r = (rawCols.getOrElse(cOff) { 1f }.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
          val g = (rawCols.getOrElse(cOff + 1) { 1f }.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
          val b = (rawCols.getOrElse(cOff + 2) { 1f }.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
          val a =
            if (comps >= 4) (rawCols.getOrElse(cOff + 3) { 1f }.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
            else 0xFF.toByte()
          val pOff = v * 4
          rgba[pOff] = r; rgba[pOff + 1] = g; rgba[pOff + 2] = b; rgba[pOff + 3] = a
          palUvs[v * 2] = (v + 0.5f) / vCount.toFloat()
          palUvs[v * 2 + 1] = 0.5f
        }
        val bakedImgIdx = images.size
        images.add(
          GlbImageData(
            mimeType = "image/raw-rgba",
            rawRgbaWidth = vCount,
            rawRgbaHeight = 1,
            rawRgbaPixels = rgba,
          ),
        )
        material = material.copy(baseColorImageIndex = bakedImgIdx)
        uvs = palUvs
      }

      out.add(
        RawPrimitiveGeometry(
          rawPositions = rawPos,
          rawNormals = rawNorms,
          uvs = uvs,
          indices = triIndices,
          material = material,
          skinIndex = if (jointsArr != null) skinIndex else -1,
          rigidNodeIndex = nodeIndex,
          joints = jointsArr,
          weights = weightsArr,
        ),
      )
    }
  }

  private fun convertModeToTriangles(src: IntArray, mode: Int): IntArray =
    when (mode) {
      4 -> {
        val n = (src.size / 3) * 3
        if (n == src.size) src else src.copyOf(n)
      }
      5 -> {
        if (src.size < 3) IntArray(0)
        else IntArray((src.size - 2) * 3).also { out ->
          var k = 0
          for (i in 0 until src.size - 2) {
            if (i % 2 == 0) {
              out[k++] = src[i]; out[k++] = src[i + 1]; out[k++] = src[i + 2]
            } else {
              out[k++] = src[i]; out[k++] = src[i + 2]; out[k++] = src[i + 1]
            }
          }
        }
      }
      6 -> {
        if (src.size < 3) IntArray(0)
        else IntArray((src.size - 2) * 3).also { out ->
          var k = 0
          for (i in 1 until src.size - 1) {
            out[k++] = src[0]; out[k++] = src[i]; out[k++] = src[i + 1]
          }
        }
      }
      else -> IntArray(0)
    }

  private fun computeSmoothNormals(pos: FloatArray, indices: IntArray): FloatArray {
    val norms = FloatArray(pos.size)
    var i = 0
    while (i + 2 < indices.size) {
      val a = indices[i] * 3
      val b = indices[i + 1] * 3
      val c = indices[i + 2] * 3
      if (a + 2 < pos.size && b + 2 < pos.size && c + 2 < pos.size) {
        val abx = pos[b] - pos[a]; val aby = pos[b + 1] - pos[a + 1]; val abz = pos[b + 2] - pos[a + 2]
        val acx = pos[c] - pos[a]; val acy = pos[c + 1] - pos[a + 1]; val acz = pos[c + 2] - pos[a + 2]
        val nx = aby * acz - abz * acy
        val ny = abz * acx - abx * acz
        val nz = abx * acy - aby * acx
        norms[a] += nx; norms[a + 1] += ny; norms[a + 2] += nz
        norms[b] += nx; norms[b + 1] += ny; norms[b + 2] += nz
        norms[c] += nx; norms[c + 1] += ny; norms[c + 2] += nz
      }
      i += 3
    }
    var v = 0
    while (v + 2 < norms.size) {
      val nx = norms[v]; val ny = norms[v + 1]; val nz = norms[v + 2]
      val len = sqrt(nx * nx + ny * ny + nz * nz)
      if (len > 1e-5f) {
        norms[v] = nx / len; norms[v + 1] = ny / len; norms[v + 2] = nz / len
      } else {
        norms[v] = 0f; norms[v + 1] = 1f; norms[v + 2] = 0f
      }
      v += 3
    }
    return norms
  }

  private fun componentByteSize(componentType: Int): Int =
    when (componentType) {
      5120, 5121 -> 1
      5122, 5123 -> 2
      5125, 5126 -> 4
      else -> 4
    }

  private fun readJointVec4(
    acc: AccessorDef,
    bufferViews: List<BufferViewDef>,
    buffers: List<ByteArray>,
  ): IntArray {
    val out = IntArray(acc.count * 4)
    if (acc.bufferView !in bufferViews.indices) return out
    val bv = bufferViews[acc.bufferView]
    val buf = buffers.getOrNull(bv.buffer) ?: return out
    val compSize = componentByteSize(acc.componentType)
    val stride = max(bv.byteStride, acc.numComponents * compSize)
    val baseOffset = bv.byteOffset + acc.byteOffset
    val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
    for (i in 0 until acc.count) {
      val elemBase = baseOffset + i * stride
      for (c in 0 until min(4, acc.numComponents)) {
        val pos = elemBase + c * compSize
        if (pos + compSize > buf.size) break
        out[i * 4 + c] =
          when (acc.componentType) {
            5121 -> bb.get(pos).toInt() and 0xFF
            5123 -> bb.getShort(pos).toInt() and 0xFFFF
            5125 -> bb.getInt(pos)
            else -> 0
          }
      }
    }
    return out
  }

  private fun readFloatAttribute(
    acc: AccessorDef,
    targetComps: Int,
    bufferViews: List<BufferViewDef>,
    buffers: List<ByteArray>,
  ): FloatArray {
    val out = FloatArray(acc.count * targetComps)
    if (acc.bufferView !in bufferViews.indices) return out
    val bv = bufferViews[acc.bufferView]
    val buf = buffers.getOrNull(bv.buffer) ?: return out
    val compSize = componentByteSize(acc.componentType)
    val naturalStride = acc.numComponents * compSize
    val stride = max(bv.byteStride, naturalStride)
    val baseOffset = bv.byteOffset + acc.byteOffset
    val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
    val readComps = min(acc.numComponents, targetComps)

    for (i in 0 until acc.count) {
      val elemBase = baseOffset + i * stride
      for (c in 0 until readComps) {
        val pos = elemBase + c * compSize
        if (pos + compSize > buf.size) break
        out[i * targetComps + c] = readComponentAsFloat(bb, pos, acc.componentType, acc.normalized)
      }
    }
    return out
  }

  private fun readComponentAsFloat(
    bb: ByteBuffer,
    pos: Int,
    componentType: Int,
    normalized: Boolean,
  ): Float =
    when (componentType) {
      5126 -> bb.getFloat(pos)
      5120 -> {
        val v = bb.get(pos).toFloat()
        if (normalized) max(v / 127f, -1f) else v
      }
      5121 -> {
        val v = (bb.get(pos).toInt() and 0xFF).toFloat()
        if (normalized) v / 255f else v
      }
      5122 -> {
        val v = bb.getShort(pos).toFloat()
        if (normalized) max(v / 32767f, -1f) else v
      }
      5123 -> {
        val v = (bb.getShort(pos).toInt() and 0xFFFF).toFloat()
        if (normalized) v / 65535f else v
      }
      5125 -> {
        val v = (bb.getInt(pos).toLong() and 0xFFFFFFFFL).toFloat()
        if (normalized) v / 4294967295f else v
      }
      else -> 0f
    }

  private fun readIntIndices(
    acc: AccessorDef,
    bufferViews: List<BufferViewDef>,
    buffers: List<ByteArray>,
  ): IntArray {
    val out = IntArray(acc.count)
    if (acc.bufferView !in bufferViews.indices) return out
    val bv = bufferViews[acc.bufferView]
    val buf = buffers.getOrNull(bv.buffer) ?: return out
    val compSize = componentByteSize(acc.componentType)
    val stride = max(bv.byteStride, compSize)
    val baseOffset = bv.byteOffset + acc.byteOffset
    val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)

    for (i in 0 until acc.count) {
      val pos = baseOffset + i * stride
      if (pos + compSize > buf.size) break
      out[i] =
        when (acc.componentType) {
          5121 -> bb.get(pos).toInt() and 0xFF
          5123 -> bb.getShort(pos).toInt() and 0xFFFF
          5125 -> bb.getInt(pos)
          else -> bb.getShort(pos).toInt() and 0xFFFF
        }
    }
    return out
  }

  private fun decodeDataUri(uri: String): ByteArray {
    val comma = uri.indexOf(',')
    if (comma < 0) return ByteArray(0)
    val header = uri.substring(0, comma)
    val payload = uri.substring(comma + 1)
    return if (header.contains(";base64")) decodeBase64(payload) else ByteArray(0)
  }

  private fun decodeBase64(input: String): ByteArray {
    val table = IntArray(256) { -1 }
    val alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    for (i in alpha.indices) table[alpha[i].code] = i
    val out = ByteArrayList((input.length * 3) / 4 + 4)
    var buf = 0
    var bits = 0
    for (ch in input) {
      if (ch == '=') break
      val code = ch.code
      val v = if (code in table.indices) table[code] else -1
      if (v < 0) continue
      buf = (buf shl 6) or v
      bits += 6
      if (bits >= 8) {
        bits -= 8
        out.add(((buf ushr bits) and 0xFF).toByte())
      }
    }
    return out.toArray()
  }

  private fun setIdentity4(m: FloatArray) {
    for (i in m.indices) m[i] = 0f
    m[0] = 1f; m[5] = 1f; m[10] = 1f; m[15] = 1f
  }

  private fun setRotateX(m: FloatArray, deg: Float) {
    setIdentity4(m)
    if (abs(deg) < 1e-4f) return
    val r = deg * (PI.toFloat() / 180f)
    val c = cos(r); val s = sin(r)
    m[5] = c; m[6] = s; m[9] = -s; m[10] = c
  }

  private fun setRotateY(m: FloatArray, deg: Float) {
    setIdentity4(m)
    if (abs(deg) < 1e-4f) return
    val r = deg * (PI.toFloat() / 180f)
    val c = cos(r); val s = sin(r)
    m[0] = c; m[2] = -s; m[8] = s; m[10] = c
  }

  private fun setRotateZ(m: FloatArray, deg: Float) {
    setIdentity4(m)
    if (abs(deg) < 1e-4f) return
    val r = deg * (PI.toFloat() / 180f)
    val c = cos(r); val s = sin(r)
    m[0] = c; m[1] = s; m[4] = -s; m[5] = c
  }

  private fun mulMat4(dst: FloatArray, a: FloatArray, b: FloatArray) {
    for (c in 0 until 4) {
      val c4 = c * 4
      val b0 = b[c4]; val b1 = b[c4 + 1]; val b2 = b[c4 + 2]; val b3 = b[c4 + 3]
      dst[c4] = a[0] * b0 + a[4] * b1 + a[8] * b2 + a[12] * b3
      dst[c4 + 1] = a[1] * b0 + a[5] * b1 + a[9] * b2 + a[13] * b3
      dst[c4 + 2] = a[2] * b0 + a[6] * b1 + a[10] * b2 + a[14] * b3
      dst[c4 + 3] = a[3] * b0 + a[7] * b1 + a[11] * b2 + a[15] * b3
    }
  }

  private fun determinant3x3(m: FloatArray): Float =
    m[0] * (m[5] * m[10] - m[9] * m[6]) -
      m[4] * (m[1] * m[10] - m[9] * m[2]) +
      m[8] * (m[1] * m[6] - m[5] * m[2])

  private fun invertRigidAffine4x4(m: FloatArray): FloatArray {
    val a00 = m[0]; val a10 = m[1]; val a20 = m[2]
    val a01 = m[4]; val a11 = m[5]; val a21 = m[6]
    val a02 = m[8]; val a12 = m[9]; val a22 = m[10]
    val c00 = a11 * a22 - a12 * a21
    val c10 = -(a01 * a22 - a02 * a21)
    val c20 = a01 * a12 - a02 * a11
    val c01 = -(a10 * a22 - a12 * a20)
    val c11 = a00 * a22 - a02 * a20
    val c21 = -(a00 * a12 - a02 * a10)
    val c02 = a10 * a21 - a11 * a20
    val c12 = -(a00 * a21 - a01 * a20)
    val c22 = a00 * a11 - a01 * a10
    val det = a00 * c00 + a01 * c01 + a02 * c02
    if (abs(det) < 1e-12f) return FloatArray(16).apply { setIdentity4(this) }
    val inv = 1f / det
    val i00 = c00 * inv; val i10 = c01 * inv; val i20 = c02 * inv
    val i01 = c10 * inv; val i11 = c11 * inv; val i21 = c12 * inv
    val i02 = c20 * inv; val i12 = c21 * inv; val i22 = c22 * inv
    val tx = m[12]; val ty = m[13]; val tz = m[14]
    return floatArrayOf(
      i00, i10, i20, 0f,
      i01, i11, i21, 0f,
      i02, i12, i22, 0f,
      -(i00 * tx + i01 * ty + i02 * tz),
      -(i10 * tx + i11 * ty + i12 * tz),
      -(i20 * tx + i21 * ty + i22 * tz),
      1f,
    )
  }

  private sealed interface JVal
  private class JObj(val map: Map<String, JVal>) : JVal {
    operator fun get(key: String): JVal? = map[key]
    fun str(key: String): String? = (map[key] as? JStr)?.value
    fun int(key: String, default: Int): Int = (map[key] as? JNum)?.value?.toInt() ?: default
    fun float(key: String, default: Float): Float = (map[key] as? JNum)?.value?.toFloat() ?: default
    fun bool(key: String, default: Boolean): Boolean = (map[key] as? JBool)?.value ?: default
  }
  private class JArr(val items: List<JVal>) : JVal {
    fun objAt(idx: Int): JObj? = items.getOrNull(idx) as? JObj
    fun floatAt(idx: Int, default: Float): Float = (items.getOrNull(idx) as? JNum)?.value?.toFloat() ?: default
  }
  private class JStr(val value: String) : JVal
  private class JNum(val value: Double) : JVal
  private class JBool(val value: Boolean) : JVal
  private object JNull : JVal

  private class JsonParser(private val src: String) {
    private var pos = 0

    fun parseObject(): JObj = readObject()

    private fun skipWs() {
      while (pos < src.length && src[pos].isWhitespace()) pos++
    }

    private fun readVal(): JVal {
      skipWs()
      if (pos >= src.length) throw IllegalArgumentException("Unexpected end of JSON")
      return when (val ch = src[pos]) {
        '{' -> readObject()
        '[' -> readArray()
        '"' -> JStr(readString())
        't' -> { pos += 4; JBool(true) }
        'f' -> { pos += 5; JBool(false) }
        'n' -> { pos += 4; JNull }
        '-', in '0'..'9' -> readNumber()
        else -> throw IllegalArgumentException("Unexpected JSON char '$ch' at $pos")
      }
    }

    private fun readObject(): JObj {
      skipWs()
      require(src[pos] == '{') { "Expected '{' at $pos" }
      pos++
      val map = LinkedHashMap<String, JVal>()
      skipWs()
      if (pos < src.length && src[pos] == '}') {
        pos++; return JObj(map)
      }
      while (pos < src.length) {
        skipWs()
        val key = readString()
        skipWs()
        require(src[pos] == ':') { "Expected ':' after key $key" }
        pos++
        map[key] = readVal()
        skipWs()
        if (pos < src.length && src[pos] == ',') {
          pos++
        } else {
          require(pos < src.length && src[pos] == '}') { "Expected '}' at $pos" }
          pos++
          break
        }
      }
      return JObj(map)
    }

    private fun readArray(): JArr {
      skipWs()
      require(src[pos] == '[') { "Expected '[' at $pos" }
      pos++
      val items = ArrayList<JVal>()
      skipWs()
      if (pos < src.length && src[pos] == ']') {
        pos++; return JArr(items)
      }
      while (pos < src.length) {
        items.add(readVal())
        skipWs()
        if (pos < src.length && src[pos] == ',') {
          pos++
        } else {
          require(pos < src.length && src[pos] == ']') { "Expected ']' at $pos" }
          pos++
          break
        }
      }
      return JArr(items)
    }

    private fun readString(): String {
      skipWs()
      require(src[pos] == '"') { "Expected '\"' at $pos" }
      pos++
      val sb = StringBuilder()
      while (pos < src.length) {
        val ch = src[pos++]
        when (ch) {
          '"' -> return sb.toString()
          '\\' -> {
            if (pos >= src.length) break
            when (val esc = src[pos++]) {
              '"' -> sb.append('"')
              '\\' -> sb.append('\\')
              '/' -> sb.append('/')
              'b' -> sb.append('\b')
              'f' -> sb.append('\u000C')
              'n' -> sb.append('\n')
              'r' -> sb.append('\r')
              't' -> sb.append('\t')
              'u' -> {
                if (pos + 4 <= src.length) {
                  sb.append(src.substring(pos, pos + 4).toInt(16).toChar())
                  pos += 4
                }
              }
              else -> sb.append(esc)
            }
          }
          else -> sb.append(ch)
        }
      }
      return sb.toString()
    }

    private fun readNumber(): JNum {
      val start = pos
      while (pos < src.length) {
        val ch = src[pos]
        if (ch in '0'..'9' || ch == '-' || ch == '+' || ch == '.' || ch == 'e' || ch == 'E') {
          pos++
        } else {
          break
        }
      }
      return JNum(src.substring(start, pos).toDouble())
    }
  }

  private class FloatArrayList(cap: Int) {
    private var arr = FloatArray(cap)
    var size = 0; private set
    fun add(v: Float) {
      if (size == arr.size) arr = arr.copyOf(arr.size * 2)
      arr[size++] = v
    }
    fun clear() { size = 0 }
    fun toArray(): FloatArray = arr.copyOf(size)
  }

  private class ShortArrayList(cap: Int) {
    private var arr = ShortArray(cap)
    var size = 0; private set
    fun add(v: Short) {
      if (size == arr.size) arr = arr.copyOf(arr.size * 2)
      arr[size++] = v
    }
    fun clear() { size = 0 }
    fun toArray(): ShortArray = arr.copyOf(size)
  }

  private class IntArrayList(cap: Int) {
    private var arr = IntArray(cap)
    var size = 0; private set
    fun add(v: Int) {
      if (size == arr.size) arr = arr.copyOf(arr.size * 2)
      arr[size++] = v
    }
    fun clear() { size = 0 }
    fun toArray(): IntArray = arr.copyOf(size)
  }

  private class ByteArrayList(cap: Int) {
    private var arr = ByteArray(cap)
    var size = 0; private set
    fun add(v: Byte) {
      if (size == arr.size) arr = arr.copyOf(arr.size * 2)
      arr[size++] = v
    }
    fun toArray(): ByteArray = arr.copyOf(size)
  }
}
