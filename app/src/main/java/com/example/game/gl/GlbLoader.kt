package com.example.game.gl

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
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

data class GlbMeshPartData(
  val vertices: FloatArray, // interleaved 8 floats: x, y, z, nx, ny, nz, u, v
  val indices: ShortArray,
  val material: GlbMaterialData,
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
  val rawBounds: GlbBounds,
  val normalizedBounds: GlbBounds,
  val appliedScale: Float,
  val groundShiftY: Float,
) {
  val totalTriangles: Int
    get() = parts.sumOf { it.indices.size / 3 }
}

/**
 * Pure-Kotlin binary glTF 2.0 (.glb) loader that parses node transforms, meshes, accessors,
 * materials, and embedded textures, automatically scales characters to realistic human height,
 * centers XZ, and aligns the lowest foot vertex cleanly to ground plane y = 0.
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

    val collectedPrimitives = ArrayList<RawPrimitiveGeometry>()
    val nodesArr = root["nodes"] as? JArr
    if (nodesArr != null && nodesArr.items.isNotEmpty()) {
      val rootNodes = resolveRootNodes(root, nodesArr)
      val identity = FloatArray(16).apply { setIdentity4(this) }
      for (nodeIdx in rootNodes) {
        traverseNode(
          nodeIndex = nodeIdx,
          parentMatrix = identity,
          nodesArr = nodesArr,
          meshesArr = meshesArr,
          buffers = buffers,
          bufferViews = bufferViews,
          accessors = accessors,
          materials = materials,
          images = images,
          out = collectedPrimitives,
        )
      }
    } else {
      val identity = FloatArray(16).apply { setIdentity4(this) }
      for (mIdx in meshesArr.items.indices) {
        extractMeshPrimitives(
          meshObj = meshesArr.objAt(mIdx) ?: continue,
          worldMatrix = identity,
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

    var minX = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    var minZ = Float.POSITIVE_INFINITY
    var maxZ = Float.NEGATIVE_INFINITY

    for (prim in collectedPrimitives) {
      val pos = prim.positions
      var i = 0
      while (i < pos.size) {
        val x = pos[i]
        val y = pos[i + 1]
        val z = pos[i + 2]
        if (x < minX) minX = x
        if (x > maxX) maxX = x
        if (y < minY) minY = y
        if (y > maxY) maxY = y
        if (z < minZ) minZ = z
        if (z > maxZ) maxZ = z
        i += 3
      }
    }

    val rawBounds = GlbBounds(minX, maxX, minY, maxY, minZ, maxZ)
    val rawHeight = max(rawBounds.height, 0.001f)
    val scale = targetHeightMeters.coerceIn(0.25f, 4.0f) / rawHeight
    val centerX = (minX + maxX) * 0.5f
    val centerZ = (minZ + maxZ) * 0.5f
    val groundY = minY

    val finalParts = ArrayList<GlbMeshPartData>(collectedPrimitives.size)
    var nMinX = Float.POSITIVE_INFINITY
    var nMaxX = Float.NEGATIVE_INFINITY
    var nMinY = Float.POSITIVE_INFINITY
    var nMaxY = Float.NEGATIVE_INFINITY
    var nMinZ = Float.POSITIVE_INFINITY
    var nMaxZ = Float.NEGATIVE_INFINITY

    for (prim in collectedPrimitives) {
      val vCount = prim.positions.size / 3
      val normalizedVerts = FloatArray(vCount * 8)
      for (v in 0 until vCount) {
        val pOff = v * 3
        val uOff = v * 2
        val oOff = v * 8
        val nx = (prim.positions[pOff] - centerX) * scale
        val ny = (prim.positions[pOff + 1] - groundY) * scale
        val nz = (prim.positions[pOff + 2] - centerZ) * scale

        if (nx < nMinX) nMinX = nx
        if (nx > nMaxX) nMaxX = nx
        if (ny < nMinY) nMinY = ny
        if (ny > nMaxY) nMaxY = ny
        if (nz < nMinZ) nMinZ = nz
        if (nz > nMaxZ) nMaxZ = nz

        normalizedVerts[oOff] = nx
        normalizedVerts[oOff + 1] = ny
        normalizedVerts[oOff + 2] = nz
        normalizedVerts[oOff + 3] = prim.normals[pOff]
        normalizedVerts[oOff + 4] = prim.normals[pOff + 1]
        normalizedVerts[oOff + 5] = prim.normals[pOff + 2]
        normalizedVerts[oOff + 6] = prim.uvs[uOff]
        normalizedVerts[oOff + 7] = prim.uvs[uOff + 1]
      }

      splitIntoEs2Parts(
        vertices = normalizedVerts,
        indices = prim.indices,
        material = prim.material,
        out = finalParts,
      )
    }

    return ParsedGlbModel(
      parts = finalParts,
      images = images,
      rawBounds = rawBounds,
      normalizedBounds = GlbBounds(nMinX, nMaxX, nMinY, nMaxY, nMinZ, nMaxZ),
      appliedScale = scale,
      groundShiftY = -groundY * scale,
    )
  }

  private fun splitIntoEs2Parts(
    vertices: FloatArray,
    indices: IntArray,
    material: GlbMaterialData,
    out: MutableList<GlbMeshPartData>,
  ) {
    val vertexCount = vertices.size / 8
    val maxIdx = indices.maxOrNull() ?: -1
    if (vertexCount <= MAX_ES2_VERTICES_PER_PART && maxIdx in 0..MAX_ES2_VERTICES_PER_PART) {
      val shortIdx = ShortArray(indices.size) { indices[it].toShort() }
      out.add(GlbMeshPartData(vertices, shortIdx, material))
      return
    }

    // Batch triangles cleanly into <= 65000 unique-vertex chunks for ES 2.0 GL_UNSIGNED_SHORT
    val remap = HashMap<Int, Int>(4096)
    val chunkVerts = FloatArrayList(4096 * 8)
    val chunkIdx = ShortArrayList(8192)

    fun flushChunk() {
      if (chunkIdx.size > 0) {
        out.add(GlbMeshPartData(chunkVerts.toArray(), chunkIdx.toArray(), material))
      }
      remap.clear()
      chunkVerts.clear()
      chunkIdx.clear()
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
            chunkVerts.add(vertices[base + k])
          }
          nextId
        }
        chunkIdx.add(local.toShort())
      }
      i += 3
    }
    flushChunk()
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
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val indices: IntArray,
    val material: GlbMaterialData,
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

  private fun resolveRootNodes(root: JObj, nodesArr: JArr): List<Int> {
    val scenesArr = root["scenes"] as? JArr
    val sceneIdx = root.int("scene", 0)
    val sceneObj = scenesArr?.objAt(sceneIdx) ?: scenesArr?.objAt(0)
    val sceneNodes = sceneObj?.get("nodes") as? JArr
    if (sceneNodes != null && sceneNodes.items.isNotEmpty()) {
      return sceneNodes.items.mapNotNull { (it as? JNum)?.value?.toInt() }
    }
    val hasParent = BooleanArray(nodesArr.items.size)
    for (i in nodesArr.items.indices) {
      val children = (nodesArr.objAt(i)?.get("children") as? JArr) ?: continue
      for (c in children.items) {
        val ci = (c as? JNum)?.value?.toInt() ?: continue
        if (ci in hasParent.indices) hasParent[ci] = true
      }
    }
    return hasParent.indices.filter { !hasParent[it] }
  }

  private fun traverseNode(
    nodeIndex: Int,
    parentMatrix: FloatArray,
    nodesArr: JArr,
    meshesArr: JArr,
    buffers: List<ByteArray>,
    bufferViews: List<BufferViewDef>,
    accessors: List<AccessorDef>,
    materials: List<GlbMaterialData>,
    images: MutableList<GlbImageData>,
    out: MutableList<RawPrimitiveGeometry>,
  ) {
    val nodeObj = nodesArr.objAt(nodeIndex) ?: return
    val local = computeNodeMatrix(nodeObj)
    val world = FloatArray(16)
    mulMat4(world, parentMatrix, local)

    val meshIdx = nodeObj.int("mesh", -1)
    if (meshIdx in meshesArr.items.indices) {
      val meshObj = meshesArr.objAt(meshIdx)
      if (meshObj != null) {
        extractMeshPrimitives(
          meshObj = meshObj,
          worldMatrix = world,
          buffers = buffers,
          bufferViews = bufferViews,
          accessors = accessors,
          materials = materials,
          images = images,
          out = out,
        )
      }
    }

    val children = nodeObj["children"] as? JArr ?: return
    for (c in children.items) {
      val childIdx = (c as? JNum)?.value?.toInt() ?: continue
      traverseNode(
        nodeIndex = childIdx,
        parentMatrix = world,
        nodesArr = nodesArr,
        meshesArr = meshesArr,
        buffers = buffers,
        bufferViews = bufferViews,
        accessors = accessors,
        materials = materials,
        images = images,
        out = out,
      )
    }
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
      qx /= qLen
      qy /= qLen
      qz /= qLen
      qw /= qLen
    } else {
      qx = 0f; qy = 0f; qz = 0f; qw = 1f
    }

    val sArr = nodeObj["scale"] as? JArr
    val sx = sArr?.floatAt(0, 1f) ?: 1f
    val sy = sArr?.floatAt(1, 1f) ?: 1f
    val sz = sArr?.floatAt(2, 1f) ?: 1f

    // Column-major T * R * S
    val xx = qx * qx
    val yy = qy * qy
    val zz = qz * qz
    val xy = qx * qy
    val xz = qx * qz
    val yz = qy * qz
    val wx = qw * qx
    val wy = qw * qy
    val wz = qw * qz

    val r00 = 1f - 2f * (yy + zz)
    val r10 = 2f * (xy + wz)
    val r20 = 2f * (xz - wy)

    val r01 = 2f * (xy - wz)
    val r11 = 1f - 2f * (xx + zz)
    val r21 = 2f * (yz + wx)

    val r02 = 2f * (xz + wy)
    val r12 = 2f * (yz - wx)
    val r22 = 1f - 2f * (xx + yy)

    return floatArrayOf(
      r00 * sx, r10 * sx, r20 * sx, 0f,
      r01 * sy, r11 * sy, r21 * sy, 0f,
      r02 * sz, r12 * sz, r22 * sz, 0f,
      tx, ty, tz, 1f,
    )
  }

  private fun extractMeshPrimitives(
    meshObj: JObj,
    worldMatrix: FloatArray,
    buffers: List<ByteArray>,
    bufferViews: List<BufferViewDef>,
    accessors: List<AccessorDef>,
    materials: List<GlbMaterialData>,
    images: MutableList<GlbImageData>,
    out: MutableList<RawPrimitiveGeometry>,
  ) {
    val prims = meshObj["primitives"] as? JArr ?: return
    val normalMat = computeNormalMatrix3x3(worldMatrix)
    val flipWinding = determinant3x3(worldMatrix) < 0f

    for (item in prims.items) {
      val prim = item as? JObj ?: continue
      val mode = prim.int("mode", 4)
      if (mode != 4 && mode != 5 && mode != 6) continue // Only triangle modes
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
      val triIndices = convertModeToTriangles(rawIndices, mode, flipWinding)
      if (triIndices.size < 3) continue

      val worldPos = FloatArray(vCount * 3)
      for (v in 0 until vCount) {
        val o = v * 3
        val x = rawPos[o]
        val y = rawPos[o + 1]
        val z = rawPos[o + 2]
        worldPos[o] = worldMatrix[0] * x + worldMatrix[4] * y + worldMatrix[8] * z + worldMatrix[12]
        worldPos[o + 1] = worldMatrix[1] * x + worldMatrix[5] * y + worldMatrix[9] * z + worldMatrix[13]
        worldPos[o + 2] = worldMatrix[2] * x + worldMatrix[6] * y + worldMatrix[10] * z + worldMatrix[14]
      }

      val normAccIdx = attrs.int("NORMAL", -1)
      val worldNorms =
        if (normAccIdx in accessors.indices) {
          val rawNorm = readFloatAttribute(accessors[normAccIdx], 3, bufferViews, buffers)
          FloatArray(vCount * 3).also { dst ->
            for (v in 0 until vCount) {
              val o = v * 3
              val nx = rawNorm.getOrElse(o) { 0f }
              val ny = rawNorm.getOrElse(o + 1) { 1f }
              val nz = rawNorm.getOrElse(o + 2) { 0f }
              val tx = normalMat[0] * nx + normalMat[3] * ny + normalMat[6] * nz
              val ty = normalMat[1] * nx + normalMat[4] * ny + normalMat[7] * nz
              val tz = normalMat[2] * nx + normalMat[5] * ny + normalMat[8] * nz
              val len = sqrt(tx * tx + ty * ty + tz * tz).coerceAtLeast(1e-5f)
              dst[o] = tx / len
              dst[o + 1] = ty / len
              dst[o + 2] = tz / len
            }
          }
        } else {
          computeSmoothNormals(worldPos, triIndices)
        }

      var material =
        materials.getOrElse(prim.int("material", -1)) { GlbMaterialData() }
      val uvAccIdx = attrs.int("TEXCOORD_0", -1)
      var uvs =
        if (uvAccIdx in accessors.indices) {
          val rawUv = readFloatAttribute(accessors[uvAccIdx], 2, bufferViews, buffers)
          FloatArray(vCount * 2) { i -> rawUv.getOrElse(i) { 0f } }
        } else {
          FloatArray(vCount * 2)
        }

      // If untextured primitive carries vertex COLOR_0, bake a compact 1D RGBA palette texture
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
          rgba[pOff] = r
          rgba[pOff + 1] = g
          rgba[pOff + 2] = b
          rgba[pOff + 3] = a
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

      out.add(RawPrimitiveGeometry(worldPos, worldNorms, uvs, triIndices, material))
    }
  }

  private fun convertModeToTriangles(src: IntArray, mode: Int, flipWinding: Boolean): IntArray {
    val raw =
      when (mode) {
        4 -> {
          val n = (src.size / 3) * 3
          if (n == src.size) src else src.copyOf(n)
        }
        5 -> {
          if (src.size < 3) return IntArray(0)
          val out = IntArray((src.size - 2) * 3)
          var k = 0
          for (i in 0 until src.size - 2) {
            if (i % 2 == 0) {
              out[k++] = src[i]; out[k++] = src[i + 1]; out[k++] = src[i + 2]
            } else {
              out[k++] = src[i]; out[k++] = src[i + 2]; out[k++] = src[i + 1]
            }
          }
          out
        }
        6 -> {
          if (src.size < 3) return IntArray(0)
          val out = IntArray((src.size - 2) * 3)
          var k = 0
          for (i in 1 until src.size - 1) {
            out[k++] = src[0]; out[k++] = src[i]; out[k++] = src[i + 1]
          }
          out
        }
        else -> return IntArray(0)
      }
    if (flipWinding) {
      var i = 0
      while (i + 2 < raw.size) {
        val tmp = raw[i + 1]
        raw[i + 1] = raw[i + 2]
        raw[i + 2] = tmp
        i += 3
      }
    }
    return raw
  }

  private fun computeSmoothNormals(pos: FloatArray, indices: IntArray): FloatArray {
    val norms = FloatArray(pos.size)
    var i = 0
    while (i + 2 < indices.size) {
      val a = indices[i] * 3
      val b = indices[i + 1] * 3
      val c = indices[i + 2] * 3
      if (a + 2 < pos.size && b + 2 < pos.size && c + 2 < pos.size) {
        val abx = pos[b] - pos[a]
        val aby = pos[b + 1] - pos[a + 1]
        val abz = pos[b + 2] - pos[a + 2]
        val acx = pos[c] - pos[a]
        val acy = pos[c + 1] - pos[a + 1]
        val acz = pos[c + 2] - pos[a + 2]
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
      val nx = norms[v]
      val ny = norms[v + 1]
      val nz = norms[v + 2]
      val len = sqrt(nx * nx + ny * ny + nz * nz)
      if (len > 1e-5f) {
        norms[v] = nx / len
        norms[v + 1] = ny / len
        norms[v + 2] = nz / len
      } else {
        norms[v] = 0f
        norms[v + 1] = 1f
        norms[v + 2] = 0f
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
      5126 -> bb.getFloat(pos) // FLOAT
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
    return if (header.contains(";base64")) {
      decodeBase64(payload)
    } else {
      ByteArray(0)
    }
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

  private fun mulMat4(dst: FloatArray, a: FloatArray, b: FloatArray) {
    for (c in 0 until 4) {
      val c4 = c * 4
      for (r in 0 until 4) {
        dst[c4 + r] =
          a[r] * b[c4] +
            a[4 + r] * b[c4 + 1] +
            a[8 + r] * b[c4 + 2] +
            a[12 + r] * b[c4 + 3]
      }
    }
  }

  private fun determinant3x3(m: FloatArray): Float {
    val a00 = m[0]; val a10 = m[1]; val a20 = m[2]
    val a01 = m[4]; val a11 = m[5]; val a21 = m[6]
    val a02 = m[8]; val a12 = m[9]; val a22 = m[10]
    return a00 * (a11 * a22 - a12 * a21) -
      a01 * (a10 * a22 - a12 * a20) +
      a02 * (a10 * a21 - a11 * a20)
  }

  private fun computeNormalMatrix3x3(m: FloatArray): FloatArray {
    // Inverse transpose of upper-left 3x3 (column-major -> stored column-major)
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
    if (abs(det) < 1e-8f) {
      return floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    }
    val inv = 1f / det
    // Transpose of cofactor matrix is inverse; transpose again for normal matrix -> cofactor * inv
    return floatArrayOf(
      c00 * inv, c01 * inv, c02 * inv,
      c10 * inv, c11 * inv, c12 * inv,
      c20 * inv, c21 * inv, c22 * inv,
    )
  }

  // Lightweight self-contained JSON AST & parser (zero Android/3P dependencies)
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
        pos++
        return JObj(map)
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
        pos++
        return JArr(items)
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
