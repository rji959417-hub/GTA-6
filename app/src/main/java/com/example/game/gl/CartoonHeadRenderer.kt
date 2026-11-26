package com.example.game.gl

import android.opengl.GLES20
import android.opengl.Matrix
import com.example.game.world.BodyBuild
import com.example.game.world.CharacterAppearance
import com.example.game.world.HairStyle
import com.example.game.world.MaterialSurface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal class CartoonHeadRenderer(
  private val meshes: CharacterMeshBuilder.CharacterMeshes,
) {
  private val tmpMat = FloatArray(16)
  private val modelMat = FloatArray(16)
  private val headRoot = FloatArray(16)

  private val eyeWhite = floatArrayOf(0.98f, 0.98f, 0.99f)
  private val irisBrown = floatArrayOf(0.30f, 0.13f, 0.05f)
  private val darkInk = floatArrayOf(0.05f, 0.05f, 0.07f)
  private val innerMouth = floatArrayOf(0.20f, 0.05f, 0.06f)
  private val tonguePink = floatArrayOf(0.88f, 0.38f, 0.40f)
  private val glassRim = floatArrayOf(0.16f, 0.18f, 0.24f)

  private val noseSkin = FloatArray(3)
  private val innerEarSkin = FloatArray(3)

  fun drawExpressiveHead(
    parent: FloatArray,
    headX: Float,
    headY: Float,
    headZ: Float,
    sx: Float,
    sy: Float,
    sz: Float,
    headYawDeg: Float,
    headPitchDeg: Float,
    headRollDeg: Float,
    worldTimeSec: Float,
    appearance: CharacterAppearance,
    uModel: Int,
    uBaseColor: Int,
    uMaterialType: Int,
    uEmissive: Int,
    uMetallic: Int,
    uRoughness: Int,
    helmetLevel: Int = 0,
  ) {
    // Organic neck + pivoting articulated head transform
    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, headX, headY - sy * 0.42f, headZ - 0.01f)
    Matrix.rotateM(tmpMat, 0, headYawDeg * 0.35f, 0f, 1f, 0f)
    Matrix.rotateM(tmpMat, 0, headPitchDeg * 0.35f, 1f, 0f, 0f)
    Matrix.scaleM(tmpMat, 0, sx * 0.56f, 0.22f, sz * 0.56f)
    Matrix.multiplyMM(modelMat, 0, parent, 0, tmpMat, 0)
    GLES20.glUniform1i(uMaterialType, MaterialSurface.SKIN.id)
    GLES20.glUniform1f(uEmissive, 0f)
    GLES20.glUniform1f(uMetallic, 0.0f)
    GLES20.glUniform1f(uRoughness, 0.52f)
    GLES20.glUniform3fv(uBaseColor, 1, appearance.skinColor, 0)
    GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
    meshes.capsuleMesh.draw()

    // Articulated head root
    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, headX, headY, headZ)
    if (abs(headYawDeg) > 0.01f) Matrix.rotateM(tmpMat, 0, headYawDeg, 0f, 1f, 0f)
    if (abs(headPitchDeg) > 0.01f) Matrix.rotateM(tmpMat, 0, headPitchDeg, 1f, 0f, 0f)
    if (abs(headRollDeg) > 0.01f) Matrix.rotateM(tmpMat, 0, headRollDeg, 0f, 0f, 1f)
    Matrix.multiplyMM(headRoot, 0, parent, 0, tmpMat, 0)

    val rx = sx * 0.5f
    val ry = sy * 0.5f
    val rz = sz * 0.5f

    // 1. Sculpted Smooth Head (chubby cheeks, bald dome, gentle double-chin & eye sockets)
    val headMesh = if (appearance.build == BodyBuild.ROUND_CHUBBY) meshes.chubbyHeadMesh else meshes.sphereMesh
    GLES20.glUniform1i(uMaterialType, MaterialSurface.SKIN.id)
    GLES20.glUniform1f(uRoughness, 0.48f)
    GLES20.glUniform3fv(uBaseColor, 1, appearance.skinColor, 0)
    drawPart(headRoot, 0f, 0f, 0f, sx, sy, sz, uModel, headMesh)

    // 2. Rounded Ears + Soft Warm Inner Ear Recess
    innerEarSkin[0] = (appearance.skinColor[0] * 0.84f).coerceIn(0f, 1f)
    innerEarSkin[1] = (appearance.skinColor[1] * 0.72f).coerceIn(0f, 1f)
    innerEarSkin[2] = (appearance.skinColor[2] * 0.72f).coerceIn(0f, 1f)
    for (side in listOf(-1f, 1f)) {
      GLES20.glUniform3fv(uBaseColor, 1, appearance.skinColor, 0)
      drawRotatedPart(headRoot, side * rx * 0.96f, -0.008f, -0.01f, 0f, side * -12f, side * -8f, 0.082f, 0.128f, 0.072f, uModel, meshes.sphereMesh)
      GLES20.glUniform3fv(uBaseColor, 1, innerEarSkin, 0)
      drawRotatedPart(headRoot, side * rx * 0.99f, -0.010f, 0.008f, 0f, side * -12f, side * -8f, 0.045f, 0.076f, 0.042f, uModel, meshes.sphereMesh)
    }

    // 3. Detailed Alive Eyes (Sclera, Warm Brown Iris, Pupil, Specular Catchlight & Natural Blink)
    val blinkCycle = worldTimeSec % 3.6f
    val blinkClosure =
      if (blinkCycle > 3.46f) {
        sin(((blinkCycle - 3.46f) / 0.14f) * Math.PI.toFloat()).coerceIn(0f, 1f)
      } else {
        0f
      }
    val eyeOpenScale = 1f - 0.86f * blinkClosure

    val eyeY = ry * 0.16f
    val eyeZ = rz * 0.79f
    val eyeXOff = rx * 0.31f

    for (side in listOf(-1f, 1f)) {
      val ex = side * eyeXOff
      val inwardLook = -side * 0.003f

      // White Sclera (glossy wet finish)
      GLES20.glUniform1i(uMaterialType, MaterialSurface.CAR_PAINT.id)
      GLES20.glUniform1f(uMetallic, 0.05f)
      GLES20.glUniform1f(uRoughness, 0.16f)
      GLES20.glUniform3fv(uBaseColor, 1, eyeWhite, 0)
      drawPart(headRoot, ex, eyeY, eyeZ, 0.088f, 0.104f * eyeOpenScale, 0.058f, uModel, meshes.sphereMesh)

      if (eyeOpenScale > 0.25f) {
        // Warm Brown Iris Disc
        GLES20.glUniform3fv(uBaseColor, 1, irisBrown, 0)
        drawPart(headRoot, ex + inwardLook, eyeY - 0.002f, eyeZ + 0.020f, 0.054f, 0.064f * eyeOpenScale, 0.030f, uModel, meshes.sphereMesh)

        // Black Pupil
        GLES20.glUniform1f(uRoughness, 0.12f)
        GLES20.glUniform3fv(uBaseColor, 1, darkInk, 0)
        drawPart(headRoot, ex + inwardLook, eyeY - 0.002f, eyeZ + 0.027f, 0.032f, 0.038f * eyeOpenScale, 0.022f, uModel, meshes.sphereMesh)

        // Crisp Catchlight Glint (subtle upper-left sparkle like photo!)
        GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
        GLES20.glUniform1f(uEmissive, 0.65f)
        GLES20.glUniform3fv(uBaseColor, 1, eyeWhite, 0)
        drawPart(headRoot, ex - 0.011f, eyeY + 0.014f * eyeOpenScale, eyeZ + 0.035f, 0.013f, 0.013f, 0.009f, uModel, meshes.sphereMesh)
        GLES20.glUniform1f(uEmissive, 0f)
      }

      // Soft Skin Upper Eyelid Hood
      GLES20.glUniform1i(uMaterialType, MaterialSurface.SKIN.id)
      GLES20.glUniform1f(uMetallic, 0f)
      GLES20.glUniform1f(uRoughness, 0.48f)
      GLES20.glUniform3fv(uBaseColor, 1, appearance.skinColor, 0)
      val lidY = eyeY + 0.038f - 0.032f * blinkClosure
      val lidH = 0.034f + 0.058f * blinkClosure
      drawRotatedPart(headRoot, ex, lidY, eyeZ + 0.006f, 18f, 0f, side * -6f, 0.092f, lidH, 0.062f, uModel, meshes.sphereMesh)

      // Smooth Arched Sculpted Eyebrows (cheerful upward curve!)
      GLES20.glUniform1i(uMaterialType, MaterialSurface.CLOTH.id)
      GLES20.glUniform1f(uRoughness, 0.42f)
      GLES20.glUniform3fv(uBaseColor, 1, appearance.hairColor, 0)
      drawRotatedPart(
        headRoot,
        ex,
        eyeY + 0.072f,
        eyeZ + 0.014f,
        -8f,
        side * -14f,
        side * -12f,
        0.105f,
        0.092f,
        0.068f,
        uModel,
        meshes.eyebrowMesh,
      )
    }

    // 4. Optional Round Glasses for Buddy NPCs
    if (appearance.hasRoundGlasses) {
      GLES20.glUniform1i(uMaterialType, MaterialSurface.SLATE.id)
      GLES20.glUniform1f(uMetallic, 0.72f)
      GLES20.glUniform1f(uRoughness, 0.22f)
      GLES20.glUniform3fv(uBaseColor, 1, glassRim, 0)
      for (side in listOf(-1f, 1f)) {
        drawRotatedPart(headRoot, side * eyeXOff, eyeY, eyeZ + 0.038f, 90f, 0f, 0f, 0.118f, 0.016f, 0.118f, uModel, meshes.cylinderMesh)
      }
      drawPart(headRoot, 0f, eyeY + 0.008f, eyeZ + 0.042f, 0.078f, 0.014f, 0.016f, uModel, meshes.capsuleMesh)
    }

    // 5. Big Plump Warm-Tinted Bulbous Nose
    noseSkin[0] = (appearance.skinColor[0] * 1.03f).coerceIn(0f, 1f)
    noseSkin[1] = (appearance.skinColor[1] * 0.90f).coerceIn(0f, 1f)
    noseSkin[2] = (appearance.skinColor[2] * 0.86f).coerceIn(0f, 1f)
    GLES20.glUniform1i(uMaterialType, MaterialSurface.SKIN.id)
    GLES20.glUniform1f(uMetallic, 0f)
    GLES20.glUniform1f(uRoughness, 0.44f)
    GLES20.glUniform3fv(uBaseColor, 1, noseSkin, 0)
    drawPart(headRoot, 0f, -ry * 0.08f, rz * 0.96f, 0.128f, 0.104f, 0.138f, uModel, meshes.sphereMesh)

    // 6. Cheerful Open Laughing Mouth + White Upper Teeth + Coral Tongue
    val smileY = -ry * 0.48f
    val smileZ = rz * 0.80f
    GLES20.glUniform1i(uMaterialType, MaterialSurface.CLOTH.id)
    GLES20.glUniform1f(uRoughness, 0.65f)
    GLES20.glUniform3fv(uBaseColor, 1, innerMouth, 0)
    drawRotatedPart(headRoot, 0f, smileY, smileZ, 8f, 0f, 0f, 0.156f, 0.185f, 0.105f, uModel, meshes.smileMouthMesh)

    // White upper teeth arc tucked neatly under the upper lip
    GLES20.glUniform1i(uMaterialType, MaterialSurface.CAR_PAINT.id)
    GLES20.glUniform1f(uRoughness, 0.25f)
    GLES20.glUniform3fv(uBaseColor, 1, eyeWhite, 0)
    drawRotatedPart(headRoot, 0f, smileY + 0.017f, smileZ + 0.008f, 8f, 0f, 0f, 0.126f, 0.115f, 0.095f, uModel, meshes.teethArcMesh)

    // Warm pink tongue resting inside lower smile
    GLES20.glUniform1i(uMaterialType, MaterialSurface.SKIN.id)
    GLES20.glUniform1f(uRoughness, 0.45f)
    GLES20.glUniform3fv(uBaseColor, 1, tonguePink, 0)
    drawPart(headRoot, 0f, smileY - 0.014f, smileZ + 0.006f, 0.072f, 0.030f, 0.045f, uModel, meshes.sphereMesh)

    // 7. Sculpted Seamless Handlebar Mustache
    if (appearance.hasMustache) {
      GLES20.glUniform1i(uMaterialType, MaterialSurface.CLOTH.id)
      GLES20.glUniform1f(uMetallic, 0.04f)
      GLES20.glUniform1f(uRoughness, 0.38f)
      GLES20.glUniform3fv(uBaseColor, 1, appearance.hairColor, 0)
      drawRotatedPart(
        headRoot,
        0f,
        -ry * 0.26f,
        rz * 0.90f,
        12f,
        0f,
        0f,
        0.275f,
        0.135f,
        0.130f,
        uModel,
        meshes.mustacheMesh,
      )
    }

    // 8. Sculpted Hair / Hat Styles (100% organic curved spikes & shells) + Lv.3/4 Combat Helmet
    if (helmetLevel < 3 || appearance.hairStyle == HairStyle.BALD_FRINGE) {
      drawHairOrHat(rx, ry, rz, sx, sy, sz, appearance, uModel, uBaseColor, uMaterialType, uRoughness)
    }
    if (helmetLevel >= 3) {
      drawCombatHelmet(
        rx = rx,
        ry = ry,
        rz = rz,
        sx = sx,
        sy = sy,
        sz = sz,
        helmetLevel = helmetLevel,
        uModel = uModel,
        uBaseColor = uBaseColor,
        uMaterialType = uMaterialType,
        uEmissive = uEmissive,
        uMetallic = uMetallic,
        uRoughness = uRoughness,
      )
    }
  }

  private fun drawCombatHelmet(
    rx: Float,
    ry: Float,
    rz: Float,
    sx: Float,
    sy: Float,
    sz: Float,
    helmetLevel: Int,
    uModel: Int,
    uBaseColor: Int,
    uMaterialType: Int,
    uEmissive: Int,
    uMetallic: Int,
    uRoughness: Int,
  ) {
    val isLv4 = helmetLevel >= 4
    val shellColor = if (isLv4) floatArrayOf(0.11f, 0.12f, 0.16f) else floatArrayOf(0.18f, 0.22f, 0.29f)
    val accentColor = if (isLv4) floatArrayOf(1.0f, 0.78f, 0.22f) else floatArrayOf(0.18f, 0.90f, 0.98f)

    // Ballistic helmet dome + rear nape guard
    GLES20.glUniform1i(uMaterialType, MaterialSurface.SLATE.id)
    GLES20.glUniform1f(uEmissive, 0f)
    GLES20.glUniform1f(uMetallic, if (isLv4) 0.78f else 0.56f)
    GLES20.glUniform1f(uRoughness, 0.24f)
    GLES20.glUniform3fv(uBaseColor, 1, shellColor, 0)
    drawPart(headRoot, 0f, ry * 0.42f, -0.02f, sx * 1.18f, sy * 0.72f, sz * 1.20f, uModel, meshes.sphereMesh)
    drawPart(headRoot, 0f, ry * 0.06f, -rz * 0.62f, sx * 0.96f, sy * 0.42f, sz * 0.56f, uModel, meshes.sphereMesh)
    // Ear armor cheek guards
    for (side in listOf(-1f, 1f)) {
      drawPart(headRoot, side * rx * 1.02f, 0f, -0.02f, 0.11f, 0.22f, 0.22f, uModel, meshes.sphereMesh)
    }

    // Glowing Tactical Brow Crest & Combat Visor
    GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
    GLES20.glUniform1f(uEmissive, 0.75f)
    GLES20.glUniform3fv(uBaseColor, 1, accentColor, 0)
    drawRotatedPart(headRoot, 0f, ry * 0.46f, rz * 0.86f, 12f, 0f, 0f, sx * 0.92f, 0.055f, 0.18f, uModel, meshes.capsuleMesh)
    drawPart(headRoot, 0f, ry * 0.72f, -0.02f, 0.08f, 0.10f, sz * 0.94f, uModel, meshes.boxMesh)
    GLES20.glUniform1f(uEmissive, 0f)
  }

  private fun drawHairOrHat(
    rx: Float,
    ry: Float,
    rz: Float,
    sx: Float,
    sy: Float,
    sz: Float,
    appearance: CharacterAppearance,
    uModel: Int,
    uBaseColor: Int,
    uMaterialType: Int,
    uRoughness: Int,
  ) {
    GLES20.glUniform1i(uMaterialType, MaterialSurface.CLOTH.id)
    GLES20.glUniform1f(uRoughness, 0.42f)
    GLES20.glUniform3fv(uBaseColor, 1, appearance.hairColor, 0)

    when (appearance.hairStyle) {
      HairStyle.BALD_FRINGE -> {
        // Layered tapered curved locks behind ears and across back, leaving warm glossy bald top!
        val upperAngles = listOf(88f, 108f, 128f, 148f, 165f, 180f, 195f, 212f, 232f, 252f, 272f)
        for ((idx, deg) in upperAngles.withIndex()) {
          val rad = Math.toRadians(deg.toDouble()).toFloat()
          val px = sin(rad) * (rx * 0.88f)
          val pz = cos(rad) * (rz * 0.88f)
          val tiltPitch = if (idx % 2 == 0) -14f else 4f
          val lockLen = if (deg in 105f..255f) 0.155f else 0.135f
          drawRotatedPart(
            headRoot,
            px,
            ry * 0.14f,
            pz,
            tiltPitch,
            deg,
            0f,
            0.095f,
            0.095f,
            lockLen,
            uModel,
            meshes.hairSpikeMesh,
          )
          // Second softer lower layer fanning around nape & behind ears
          drawRotatedPart(
            headRoot,
            sin(rad) * (rx * 0.90f),
            -ry * 0.02f,
            cos(rad) * (rz * 0.90f),
            20f,
            deg + (if (idx % 2 == 0) 6f else -6f),
            0f,
            0.088f,
            0.088f,
            0.140f,
            uModel,
            meshes.hairSpikeMesh,
          )
        }
      }
      HairStyle.SWOOP_TOP -> {
        drawPart(headRoot, 0f, ry * 0.56f, -0.02f, sx * 1.02f, sy * 0.52f, sz * 1.02f, uModel, meshes.sphereMesh)
        drawRotatedPart(headRoot, 0.03f, ry * 0.72f, rz * 0.34f, -18f, 12f, -10f, 0.14f, 0.12f, 0.22f, uModel, meshes.hairSpikeMesh)
        drawRotatedPart(headRoot, -0.05f, ry * 0.68f, rz * 0.30f, -12f, -16f, 8f, 0.12f, 0.10f, 0.18f, uModel, meshes.hairSpikeMesh)
      }
      HairStyle.ROUND_AFRO -> {
        drawPart(headRoot, 0f, ry * 0.42f, -0.02f, sx * 1.24f, sy * 0.88f, sz * 1.22f, uModel, meshes.sphereMesh)
      }
      HairStyle.CAP_HAT -> {
        drawPart(headRoot, 0f, ry * 0.48f, -0.02f, sx * 1.04f, sy * 0.46f, sz * 1.04f, uModel, meshes.sphereMesh)
        GLES20.glUniform3fv(uBaseColor, 1, appearance.tunicColor, 0)
        drawPart(headRoot, 0f, ry * 0.66f, -0.01f, sx * 1.08f, 0.15f, sz * 1.10f, uModel, meshes.capsuleMesh)
        GLES20.glUniform3fv(uBaseColor, 1, darkInk, 0)
        drawRotatedPart(headRoot, 0f, ry * 0.48f, rz * 0.84f, 14f, 0f, 0f, sx * 0.88f, 0.045f, 0.22f, uModel, meshes.sphereMesh)
      }
      HairStyle.SPIKY_CROWN -> {
        drawPart(headRoot, 0f, ry * 0.50f, -0.02f, sx * 1.04f, sy * 0.48f, sz * 1.04f, uModel, meshes.sphereMesh)
        val crownAngles = listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f)
        for ((idx, deg) in crownAngles.withIndex()) {
          val rad = Math.toRadians(deg.toDouble()).toFloat()
          val px = sin(rad) * (rx * 0.58f)
          val pz = cos(rad) * (rz * 0.58f)
          val spikeLen = if (idx % 2 == 0) 0.24f else 0.19f
          drawRotatedPart(headRoot, px, ry * 0.68f, pz, -36f, deg, 0f, 0.11f, 0.11f, spikeLen, uModel, meshes.hairSpikeMesh)
        }
        drawRotatedPart(headRoot, 0f, ry * 0.86f, 0f, -72f, 0f, 0f, 0.13f, 0.13f, 0.25f, uModel, meshes.hairSpikeMesh)
      }
      HairStyle.MOHAWK_NEON -> {
        val finZs = listOf(0.46f, 0.24f, 0.02f, -0.22f, -0.46f)
        for ((idx, zFrac) in finZs.withIndex()) {
          val pitch = -74f + idx * 14f
          drawRotatedPart(headRoot, 0f, ry * 0.72f, rz * zFrac, pitch, 0f, 0f, 0.10f, 0.15f, 0.26f, uModel, meshes.hairSpikeMesh)
        }
      }
    }
  }

  private fun drawPart(
    parent: FloatArray,
    lx: Float,
    ly: Float,
    lz: Float,
    sx: Float,
    sy: Float,
    sz: Float,
    uModel: Int,
    mesh: Mesh,
  ) {
    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, lx, ly, lz)
    Matrix.scaleM(tmpMat, 0, sx, sy, sz)
    Matrix.multiplyMM(modelMat, 0, parent, 0, tmpMat, 0)
    GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
    mesh.draw()
  }

  private fun drawRotatedPart(
    parent: FloatArray,
    lx: Float,
    ly: Float,
    lz: Float,
    rotXDeg: Float,
    rotYDeg: Float,
    rotZDeg: Float,
    sx: Float,
    sy: Float,
    sz: Float,
    uModel: Int,
    mesh: Mesh,
  ) {
    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, lx, ly, lz)
    if (abs(rotYDeg) > 0.01f) Matrix.rotateM(tmpMat, 0, rotYDeg, 0f, 1f, 0f)
    if (abs(rotXDeg) > 0.01f) Matrix.rotateM(tmpMat, 0, rotXDeg, 1f, 0f, 0f)
    if (abs(rotZDeg) > 0.01f) Matrix.rotateM(tmpMat, 0, rotZDeg, 0f, 0f, 1f)
    Matrix.scaleM(tmpMat, 0, sx, sy, sz)
    Matrix.multiplyMM(modelMat, 0, parent, 0, tmpMat, 0)
    GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
    mesh.draw()
  }
}
