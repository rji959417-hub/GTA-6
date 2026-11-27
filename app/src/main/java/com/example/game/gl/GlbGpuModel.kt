package com.example.game.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import com.example.game.world.MaterialSurface
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class GlbGpuModel private constructor(
  private val gpuParts: List<GpuPart>,
  private val textureIds: IntArray,
  val stats: ParsedGlbModel,
) {
  private data class GpuPart(
    val mesh: Mesh,
    val material: GlbMaterialData,
    val textureId: Int,
  )

  private val modelMat = FloatArray(16)

  fun drawShadow(
    baseMatrix: FloatArray,
    lightViewProjMat: FloatArray,
    mvpMat: FloatArray,
    uLightMvp: Int,
  ) {
    Matrix.multiplyMM(mvpMat, 0, lightViewProjMat, 0, baseMatrix, 0)
    GLES20.glUniformMatrix4fv(uLightMvp, 1, false, mvpMat, 0)
    for (part in gpuParts) {
      part.mesh.draw()
    }
  }

  fun drawLit(
    baseMatrix: FloatArray,
    uModel: Int,
    uBaseColor: Int,
    uMaterialType: Int,
    uEmissive: Int,
    uMetallic: Int,
    uRoughness: Int,
    uAlpha: Int,
    uUseBaseTex: Int,
    uBaseTex: Int,
  ) {
    System.arraycopy(baseMatrix, 0, modelMat, 0, 16)
    GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)

    for (part in gpuParts) {
      val mat = part.material
      if (mat.doubleSided) {
        GLES20.glDisable(GLES20.GL_CULL_FACE)
      }
      GLES20.glUniform3fv(uBaseColor, 1, mat.baseColor, 0)
      GLES20.glUniform1i(uMaterialType, MaterialSurface.CLOTH.id)
      GLES20.glUniform1f(uEmissive, mat.emissive)
      GLES20.glUniform1f(uMetallic, mat.metallic)
      GLES20.glUniform1f(uRoughness, mat.roughness)
      GLES20.glUniform1f(uAlpha, mat.baseColor.getOrElse(3) { 1f })

      if (part.textureId != 0 && uUseBaseTex >= 0 && uBaseTex >= 0) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, part.textureId)
        GLES20.glUniform1i(uBaseTex, 1)
        GLES20.glUniform1f(uUseBaseTex, 1f)
      } else if (uUseBaseTex >= 0) {
        GLES20.glUniform1f(uUseBaseTex, 0f)
      }

      part.mesh.draw()

      if (mat.doubleSided) {
        GLES20.glEnable(GLES20.GL_CULL_FACE)
      }
    }

    if (uUseBaseTex >= 0) {
      GLES20.glUniform1f(uUseBaseTex, 0f)
      GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    }
    GLES20.glUniform1f(uEmissive, 0f)
    GLES20.glUniform1f(uAlpha, 1f)
  }

  fun release() {
    for (part in gpuParts) {
      part.mesh.release()
    }
    val nonZero = textureIds.filter { it != 0 }.toIntArray()
    if (nonZero.isNotEmpty()) {
      GLES20.glDeleteTextures(nonZero.size, nonZero, 0)
    }
  }

  companion object {
    private const val TAG = "ViceGlbLoader"

    fun computePlayerTransform(
      outMatrix: FloatArray,
      x: Float,
      y: Float,
      z: Float,
      yawRad: Float,
      walkCycle: Float,
      speedNorm: Float,
      isSprinting: Boolean,
      isSwimming: Boolean,
      fallProgress: Float,
      worldTimeSec: Float,
      sitProgress: Float,
      isSkydiving: Boolean,
      isParachuting: Boolean,
      airborneFallNorm: Float,
    ) {
      val yawDeg = Math.toDegrees(yawRad.toDouble()).toFloat()
      val clampedSpeed = speedNorm.coerceIn(0f, 1.35f) * (1f - sitProgress * 0.85f)
      val strideAmp = clampedSpeed * (if (isSprinting) 1.15f else 0.9f)
      val idleFactor = (1f - clampedSpeed).coerceIn(0f, 1f) * (1f - sitProgress * 0.5f)
      val breath = sin(worldTimeSec * 2.2f)

      val bounceY =
        if (isSwimming || fallProgress > 0.1f || sitProgress > 0.1f || airborneFallNorm > 0.1f) 0f
        else abs(sin(walkCycle)) * 0.055f * strideAmp
      val crouchScaleY = 1f - sitProgress * 0.30f
      val crouchScaleXZ = 1f + sitProgress * 0.06f
      val breathScaleY = 1f + breath * 0.008f * idleFactor

      val pitchDeg =
        when {
          fallProgress > 0.05f -> -fallProgress * 86f
          isSkydiving -> 68f + sin(worldTimeSec * 4.5f) * 3.5f
          isParachuting -> 8f
          isSwimming -> 52f
          isSprinting -> 10f
          clampedSpeed > 0.15f -> 4.5f * clampedSpeed
          else -> 0f
        }
      val rollDeg =
        if (fallProgress > 0.05f) 0f
        else cos(walkCycle) * 2.8f * strideAmp + sin(worldTimeSec * 0.9f) * 1.1f * idleFactor
      val swayYawDeg = -sin(walkCycle) * 4.5f * strideAmp

      Matrix.setIdentityM(outMatrix, 0)
      Matrix.translateM(outMatrix, 0, x, y + bounceY + fallProgress * 0.14f, z)
      Matrix.rotateM(outMatrix, 0, yawDeg + swayYawDeg, 0f, 1f, 0f)
      if (abs(pitchDeg) > 0.05f) {
        Matrix.rotateM(outMatrix, 0, pitchDeg, 1f, 0f, 0f)
      }
      if (abs(rollDeg) > 0.05f) {
        Matrix.rotateM(outMatrix, 0, rollDeg, 0f, 0f, 1f)
      }
      Matrix.scaleM(outMatrix, 0, crouchScaleXZ, crouchScaleY * breathScaleY, crouchScaleXZ)
    }

    fun tryLoadFromAssets(
      context: Context?,
      assetPath: String = "/cyberpunk_character.glb",
      targetHeightMeters: Float = 1.76f,
    ): GlbGpuModel? {
      if (context == null) return null
      val cleanName = assetPath.trimStart('/')
      val candidateAssetNames = listOf(cleanName, "public/$cleanName", "assets/$cleanName").distinct()

      var bytes: ByteArray? = null
      var sourceLabel = cleanName
      for (name in candidateAssetNames) {
        bytes =
          runCatching {
            context.assets.open(name).use { it.readBytes() }
          }.getOrNull()
        if (bytes != null) {
          sourceLabel = "asset://$name"
          break
        }
      }

      if (bytes == null) {
        val fileCandidates =
          listOf(
            File(context.filesDir, cleanName),
            File(context.filesDir, "public/$cleanName"),
          )
        for (f in fileCandidates) {
          if (f.isFile && f.canRead()) {
            bytes = runCatching { f.readBytes() }.getOrNull()
            if (bytes != null) {
              sourceLabel = f.absolutePath
              break
            }
          }
        }
      }

      if (bytes == null) {
        Log.i(TAG, "GLB not found at $candidateAssetNames; using procedural hero character.")
        return null
      }

      return try {
        val parsed = GlbLoader.parseGlbBytes(bytes, targetHeightMeters = targetHeightMeters)
        val texIds = IntArray(parsed.images.size) { idx -> uploadImageTexture(parsed.images[idx]) }
        val gpuParts =
          parsed.parts.map { part ->
            val texId = texIds.getOrElse(part.material.baseColorImageIndex) { 0 }
            GpuPart(
              mesh = Mesh(part.vertices, part.indices),
              material = part.material,
              textureId = texId,
            )
          }
        Log.i(
          TAG,
          "Loaded $sourceLabel: ${parsed.parts.size} parts, ${parsed.totalTriangles} tris, " +
            "scale=${"%.4f".format(parsed.appliedScale)}, minY=${parsed.normalizedBounds.minY}, " +
            "height=${"%.2f".format(parsed.normalizedBounds.height)}m",
        )
        GlbGpuModel(gpuParts, texIds, parsed)
      } catch (t: Throwable) {
        Log.e(TAG, "Failed to load GLB from $sourceLabel", t)
        null
      }
    }

    private fun uploadImageTexture(img: GlbImageData): Int {
      val tex = IntArray(1)
      GLES20.glGenTextures(1, tex, 0)
      val texId = tex[0]
      if (texId == 0) return 0

      if (img.rawRgbaPixels != null && img.rawRgbaWidth > 0 && img.rawRgbaHeight > 0) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val buf = ByteBuffer.allocateDirect(img.rawRgbaPixels.size).put(img.rawRgbaPixels)
        buf.position(0)
        GLES20.glTexImage2D(
          GLES20.GL_TEXTURE_2D,
          0,
          GLES20.GL_RGBA,
          img.rawRgbaWidth,
          img.rawRgbaHeight,
          0,
          GLES20.GL_RGBA,
          GLES20.GL_UNSIGNED_BYTE,
          buf,
        )
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return texId
      }

      val encoded = img.encodedBytes ?: run {
        GLES20.glDeleteTextures(1, tex, 0)
        return 0
      }
      val decoded = BitmapFactory.decodeByteArray(encoded, 0, encoded.size) ?: run {
        GLES20.glDeleteTextures(1, tex, 0)
        return 0
      }
      val potBitmap = ensurePowerOfTwo(decoded, maxDim = 1024)
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
      GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, potBitmap, 0)
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

      if (potBitmap !== decoded) potBitmap.recycle()
      decoded.recycle()
      return texId
    }

    private fun ensurePowerOfTwo(src: Bitmap, maxDim: Int): Bitmap {
      val potW = nearestPow2(min(src.width, maxDim))
      val potH = nearestPow2(min(src.height, maxDim))
      return if (src.width == potW && src.height == potH) {
        src
      } else {
        Bitmap.createScaledBitmap(src, potW, potH, true)
      }
    }

    private fun nearestPow2(n: Int): Int {
      var p = 1
      while (p < n && p < 1024) p = p shl 1
      return p.coerceIn(1, 1024)
    }
  }
}
