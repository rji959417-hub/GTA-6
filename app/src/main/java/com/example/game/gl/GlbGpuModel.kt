package com.example.game.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import com.example.game.state.CombatMoveType
import com.example.game.state.LandingReactionType
import com.example.game.state.ParkourActionType
import com.example.game.state.WeaponType
import com.example.game.world.MaterialSurface
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class GlbGpuModel private constructor(
  private val gpuParts: List<GpuPart>,
  private val textureIds: IntArray,
  val stats: ParsedGlbModel,
  private val runningFbxClip: FbxAnimationClip? = null,
) {
  private data class GpuPart(
    val mesh: Mesh,
    val material: GlbMaterialData,
    val textureId: Int,
  )

  private val poseWorkspace = GlbLoader.PoseWorkspace(stats)
  private val modelMat = FloatArray(16)
  private var fbxAnimTimeSec = 0f
  private var fbxBlendWeight = 0f
  val rightHandLocalPos: FloatArray
    get() = poseWorkspace.rightHandPos

  val hasRunningFbxClip: Boolean
    get() = runningFbxClip != null

  val currentFbxBlendWeight: Float
    get() = fbxBlendWeight

  fun updateAnimation(
    walkCycle: Float,
    speedNorm: Float,
    isSprinting: Boolean,
    isSwimming: Boolean,
    fallProgress: Float,
    worldTimeSec: Float,
    dtSec: Float = 0.016f,
    sitProgress: Float = 0f,
    proneProgress: Float = 0f,
    attackMove: CombatMoveType? = null,
    attackProgress: Float = 0f,
    equippedWeapon: WeaponType = WeaponType.FISTS,
    aimNorm: Float = 0f,
    muzzleFlash: Float = 0f,
    isSkydiving: Boolean = false,
    isParachuting: Boolean = false,
    parkourType: ParkourActionType? = null,
    parkourProgress: Float = 0f,
    airborneFallNorm: Float = 0f,
    landingReaction: LandingReactionType? = null,
    landingProgress: Float = 0f,
  ) {
    if (!stats.rig.hasLocomotionRig) return
    val basePose =
      buildHumanoidPose(
        walkCycle = walkCycle,
        speedNorm = speedNorm,
        isSprinting = isSprinting,
        isSwimming = isSwimming,
        fallProgress = fallProgress,
        worldTimeSec = worldTimeSec,
        sitProgress = sitProgress,
        proneProgress = proneProgress,
        attackMove = attackMove,
        attackProgress = attackProgress,
        equippedWeapon = equippedWeapon,
        aimNorm = aimNorm,
        muzzleFlash = muzzleFlash,
        isSkydiving = isSkydiving,
        isParachuting = isParachuting,
        parkourType = parkourType,
        parkourProgress = parkourProgress,
        airborneFallNorm = airborneFallNorm,
        landingReaction = landingReaction,
        landingProgress = landingProgress,
      )

    val safeDt = dtSec.coerceIn(0.001f, 0.1f)
    val isMovingOnFoot =
      speedNorm > 0.08f &&
        !isSwimming &&
        fallProgress < 0.05f &&
        !isSkydiving &&
        !isParachuting &&
        sitProgress < 0.25f &&
        proneProgress < 0.25f &&
        attackMove == null &&
        parkourType == null &&
        airborneFallNorm < 0.1f &&
        landingReaction == null

    val fadeStep = (safeDt / 0.2f).coerceIn(0f, 1f)
    if (runningFbxClip != null && isMovingOnFoot) {
      fbxBlendWeight = (fbxBlendWeight + fadeStep).coerceIn(0f, 1f)
      fbxAnimTimeSec += safeDt * (0.78f + speedNorm.coerceIn(0f, 1.35f) * 0.32f)
    } else {
      fbxBlendWeight = (fbxBlendWeight - fadeStep).coerceIn(0f, 1f)
      if (fbxBlendWeight > 0.001f) {
        fbxAnimTimeSec += safeDt * 0.65f
      } else {
        fbxAnimTimeSec = 0f
      }
    }

    val finalPose =
      if (runningFbxClip != null && fbxBlendWeight > 0.001f) {
        val rawFbxPose = runningFbxClip.sampleAsHumanPose(fbxAnimTimeSec)
        val fbxPose =
          if (equippedWeapon != WeaponType.FISTS) {
            rawFbxPose.copy(
              rightArmPitchDeg = basePose.rightArmPitchDeg,
              rightArmOutDeg = basePose.rightArmOutDeg,
              rightForearmBendDeg = basePose.rightForearmBendDeg,
              leftArmPitchDeg = if (aimNorm > 0.1f) basePose.leftArmPitchDeg else rawFbxPose.leftArmPitchDeg,
              leftForearmBendDeg = if (aimNorm > 0.1f) basePose.leftForearmBendDeg else rawFbxPose.leftForearmBendDeg,
            )
          } else {
            rawFbxPose
          }
        FbxAnimationClip.lerpPose(basePose, fbxPose, fbxBlendWeight)
      } else {
        basePose
      }

    poseWorkspace.evaluatePose(finalPose)
    for (i in gpuParts.indices) {
      gpuParts[i].mesh.updateVertices(poseWorkspace.partAnimatedVerts[i])
    }
  }

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

    fun buildHumanoidPose(
      walkCycle: Float,
      speedNorm: Float,
      isSprinting: Boolean,
      isSwimming: Boolean,
      fallProgress: Float,
      worldTimeSec: Float,
      sitProgress: Float = 0f,
      proneProgress: Float = 0f,
      attackMove: CombatMoveType? = null,
      attackProgress: Float = 0f,
      equippedWeapon: WeaponType = WeaponType.FISTS,
      aimNorm: Float = 0f,
      muzzleFlash: Float = 0f,
      isSkydiving: Boolean = false,
      isParachuting: Boolean = false,
      parkourType: ParkourActionType? = null,
      parkourProgress: Float = 0f,
      airborneFallNorm: Float = 0f,
      landingReaction: LandingReactionType? = null,
      landingProgress: Float = 0f,
    ): GlbCharacterPose {
      val isClimbing = parkourType == ParkourActionType.CLIMB_LEDGE
      val isVaulting = parkourType == ParkourActionType.VAULT_OVER
      val isCombatRoll = landingReaction == LandingReactionType.COMBAT_ROLL && landingProgress < 0.96f
      val isHardStagger = landingReaction == LandingReactionType.HARD_STAGGER
      val isSoftLand = landingReaction == LandingReactionType.SOFT_LAND
      val parkourP = parkourProgress.coerceIn(0f, 1f)
      val landP = landingProgress.coerceIn(0f, 1f)
      val staggerCurve = if (isHardStagger) sin(landP * PI.toFloat()) else 0f
      val softLandCurve = if (isSoftLand) sin(landP * PI.toFloat()) else 0f
      val rollCurve = if (isCombatRoll) sin(landP * PI.toFloat()) else 0f
      val vaultCurve = if (isVaulting) sin(parkourP * PI.toFloat()) else 0f

      val activeOverride = isClimbing || isVaulting || isCombatRoll || isHardStagger
      val clampedSpeed =
        if (activeOverride) 0f
        else speedNorm.coerceIn(0f, 1.35f) * (1f - sitProgress * 0.9f)
      val strideAmp = clampedSpeed * (if (isSprinting) 1.22f else 1.0f)
      val idleFactor =
        if (activeOverride) 0f
        else ((1f - clampedSpeed) * (1f - sitProgress * 0.5f)).coerceIn(0f, 1f)
      val strikeCurve =
        if (attackMove != null) sin(attackProgress.coerceIn(0f, 1f) * PI.toFloat()) else 0f
      val breath = sin(worldTimeSec * 2.2f)

      val bounceY =
        if (isSwimming || fallProgress > 0.1f || sitProgress > 0.1f || activeOverride || airborneFallNorm > 0.1f) 0f
        else abs(sin(walkCycle)) * 0.048f * strideAmp + breath * 0.006f * idleFactor
      val pelvisSwayX = cos(walkCycle) * 0.024f * strideAmp + sin(worldTimeSec * 0.9f) * 0.014f * idleFactor
      val pelvisRollDeg =
        cos(walkCycle) * 3.2f * strideAmp +
          sin(worldTimeSec * 0.9f) * 1.4f * idleFactor +
          (if (isVaulting) 20f * vaultCurve else 0f) +
          (if (attackMove == CombatMoveType.KICK_ROUNDHOUSE) -16f * strikeCurve else 0f)
      val pelvisYawDeg =
        -sin(walkCycle) * 7.5f * strideAmp +
          (if (attackMove == CombatMoveType.KICK_ROUNDHOUSE) -46f * strikeCurve else 0f)

      val attackTorsoYaw =
        when (attackMove) {
          CombatMoveType.PUNCH_JAB -> -26f * strikeCurve
          CombatMoveType.PUNCH_HOOK -> -48f * strikeCurve
          CombatMoveType.PUNCH_UPPERCUT -> -22f * strikeCurve
          CombatMoveType.KICK_FRONT -> 12f * strikeCurve
          CombatMoveType.KICK_ROUNDHOUSE -> 32f * strikeCurve
          null -> 0f
        }
      val torsoYawDeg = sin(walkCycle) * 14.0f * strideAmp + attackTorsoYaw

      val torsoLeanDeg =
        when {
          fallProgress > 0.01f -> 0f
          isSkydiving -> 14f
          isParachuting -> 8f + sin(worldTimeSec * 2.2f) * 3f
          isClimbing -> if (parkourP <= 0.45f) 10f + (parkourP / 0.45f) * 18f else 28f * (1f - ((parkourP - 0.45f) / 0.55f))
          isVaulting -> 34f * vaultCurve
          isHardStagger -> 36f * staggerCurve
          airborneFallNorm > 0.05f -> -10f * airborneFallNorm
          isSwimming -> 18f
          attackMove == CombatMoveType.PUNCH_UPPERCUT -> 10f * strikeCurve
          attackMove != null && attackMove.isKick -> -14f * strikeCurve
          sitProgress > 0.05f -> 4.5f * sitProgress + breath * 0.8f
          isSprinting -> 13f
          clampedSpeed > 0.1f -> 4.2f * clampedSpeed
          aimNorm > 0.05f -> 6f * aimNorm
          else -> 1.2f + breath * 1.0f + 11f * softLandCurve
        }

      val proneP = proneProgress.coerceIn(0f, 1f)
      val rootPitchDeg =
        when {
          fallProgress > 0.01f -> -fallProgress * 86f
          isSkydiving -> 68f + sin(worldTimeSec * 4.5f) * 4f
          isSwimming -> 44f
          proneP > 0.01f -> 84f * proneP
          else -> 0f
        }

      // Legs (phase L = walkCycle, R = walkCycle + PI)
      fun calcLeg(phase: Float, isRight: Boolean): Triple<Float, Float, Float> {
        var thighPitch: Float
        var kneeBend: Float
        when {
          fallProgress > 0.1f -> {
            thighPitch = -24f * fallProgress
            kneeBend = 34f * fallProgress
          }
          isSkydiving -> {
            val s = if (isRight) 1f else -1f
            thighPitch = 16f + sin(worldTimeSec * 6.5f + s * 1.4f) * 14f
            kneeBend = 38f + cos(worldTimeSec * 6.5f + s) * 14f
          }
          isParachuting -> {
            val s = if (isRight) 1f else -1f
            thighPitch = -14f + sin(worldTimeSec * 2.5f + s) * 7f
            kneeBend = 24f + cos(worldTimeSec * 2.1f) * 5f
          }
          isCombatRoll -> {
            thighPitch = -86f * rollCurve
            kneeBend = 104f * rollCurve
          }
          isHardStagger -> {
            val s = if (isRight) 1f else -1f
            thighPitch = (-14f + s * 18f) * staggerCurve
            kneeBend = 42f * staggerCurve
          }
          isClimbing -> {
            thighPitch =
              if (parkourP <= 0.45f) {
                val t = (parkourP / 0.45f).coerceIn(0f, 1f)
                if (isRight) -84f * t else 14f - 38f * t
              } else {
                val t = ((parkourP - 0.45f) / 0.55f).coerceIn(0f, 1f)
                if (isRight) -84f * (1f - t) else -24f * (1f - t)
              }
            kneeBend =
              if (parkourP <= 0.45f) {
                val t = (parkourP / 0.45f).coerceIn(0f, 1f)
                if (isRight) 94f * t else 28f * t
              } else {
                val t = ((parkourP - 0.45f) / 0.55f).coerceIn(0f, 1f)
                if (isRight) 94f * (1f - t) else 28f * (1f - t)
              }
          }
          isVaulting -> {
            thighPitch = -74f * vaultCurve
            kneeBend = 100f * vaultCurve
          }
          airborneFallNorm > 0.05f -> {
            val s = if (isRight) 1f else -1f
            val cycle = sin(worldTimeSec * 12f + s * 1.8f) * 14f
            thighPitch = -30f * airborneFallNorm + cycle * airborneFallNorm
            kneeBend = 48f * airborneFallNorm
          }
          isSwimming -> {
            thighPitch = -sin(phase) * 28f
            kneeBend = max(6f, cos(phase) * 32f + 12f)
          }
          attackMove == CombatMoveType.KICK_FRONT && isRight -> {
            thighPitch = -98f * strikeCurve
            kneeBend = max(4f, 52f * (1f - strikeCurve))
          }
          attackMove == CombatMoveType.KICK_ROUNDHOUSE && isRight -> {
            thighPitch = -88f * strikeCurve
            kneeBend = max(6f, 38f * (1f - strikeCurve * 0.85f))
          }
          else -> {
            thighPitch = -sin(phase) * 33f * strideAmp
            val swingLift = max(0f, (cos(phase) + 0.30f) / 1.30f)
            kneeBend = 4.5f + swingLift * 48f * strideAmp + 32f * softLandCurve
          }
        }
        val sitThigh = -84f
        val sitKnee = 86f
        val proneCrawl = sin(phase) * 14f * speedNorm.coerceIn(0f, 1f)
        val proneThigh = 6f + proneCrawl
        val proneKnee = (16f - sin(phase) * 14f * speedNorm.coerceIn(0f, 1f)).coerceAtLeast(4f)
        val seatedThigh = thighPitch * (1f - sitProgress) + sitThigh * sitProgress
        val seatedKnee = kneeBend * (1f - sitProgress) + sitKnee * sitProgress
        val finalThigh = seatedThigh * (1f - proneP) + proneThigh * proneP
        val finalKnee = seatedKnee * (1f - proneP) + proneKnee * proneP
        val ankle = (((-finalThigh * 0.28f - finalKnee * 0.44f) * (1f - sitProgress)) * (1f - proneP) + (38f * proneP)).coerceIn(-24f, 42f)
        return Triple(finalThigh, finalKnee, ankle)
      }

      val (lThigh, lKnee, lAnkle) = calcLeg(walkCycle, isRight = false)
      val (rThigh, rKnee, rAnkle) = calcLeg(walkCycle + PI.toFloat(), isRight = true)
      val rThighOut = if (attackMove == CombatMoveType.KICK_ROUNDHOUSE) 34f * strikeCurve else 0f

      // Arms (opposite swing to legs: L arm swings opposite to L leg -> +sin(walkCycle))
      fun calcArm(legPhase: Float, isRight: Boolean): Triple<Float, Float, Float> {
        var armPitch: Float
        var armOut = 3f
        var forearmBend: Float
        val forwardSwing = max(0f, -sin(legPhase + PI.toFloat()))
        when {
          fallProgress > 0.1f -> {
            armPitch = -20f * fallProgress
            armOut = 18f * fallProgress
            forearmBend = -18f * fallProgress
          }
          isSkydiving -> {
            val s = if (isRight) 1f else -1f
            armPitch = -32f + sin(worldTimeSec * 5.2f + s) * 14f
            armOut = 42f
            forearmBend = -28f
          }
          isParachuting -> {
            armPitch = -112f
            armOut = 26f
            forearmBend = -22f
          }
          isCombatRoll -> {
            armPitch = -64f * rollCurve
            forearmBend = -84f * rollCurve
          }
          isSwimming -> {
            armPitch = -sin(legPhase) * 48f - 36f
            armOut = 22f
            forearmBend = -24f
          }
          attackMove == CombatMoveType.PUNCH_JAB && isRight -> {
            armPitch = -88f * strikeCurve
            forearmBend = -8f
          }
          attackMove == CombatMoveType.PUNCH_HOOK && !isRight -> {
            armPitch = -76f * strikeCurve
            armOut = 26f * strikeCurve
            forearmBend = -58f * strikeCurve
          }
          attackMove == CombatMoveType.PUNCH_UPPERCUT && isRight -> {
            armPitch = -96f * strikeCurve
            forearmBend = -68f * strikeCurve
          }
          else -> {
            armPitch = sin(legPhase) * 34f * strideAmp + breath * 1.4f * idleFactor
            forearmBend = -12f - 30f * strideAmp - forwardSwing * 24f * strideAmp
          }
        }

        // Aim or gun carry override on right arm (plus left support when aiming)
        if (equippedWeapon != WeaponType.FISTS && fallProgress < 0.1f && !isSkydiving && !isParachuting && !isCombatRoll) {
          val recoil = muzzleFlash * 10f
          if (isRight) {
            val carryPitch = -38f + sin(walkCycle) * 8f * strideAmp
            val carryBend = -54f
            val aimPitch = -84f + recoil
            val aimBend = -16f - recoil * 0.5f
            val aimBlend = aimNorm.coerceIn(0f, 1f)
            armPitch = carryPitch * (1f - aimBlend) + aimPitch * aimBlend
            forearmBend = carryBend * (1f - aimBlend) + aimBend * aimBlend
            armOut = 4f
          } else if (aimNorm > 0.1f) {
            val aimBlend = aimNorm.coerceIn(0f, 1f)
            armPitch = armPitch * (1f - aimBlend) + (-72f) * aimBlend
            forearmBend = forearmBend * (1f - aimBlend) + (-44f) * aimBlend
          }
        }

        if (sitProgress > 0.05f && !isRight && aimNorm < 0.1f) {
          armPitch = armPitch * (1f - sitProgress) + (-36f) * sitProgress
          forearmBend = forearmBend * (1f - sitProgress) + (-48f) * sitProgress
        }
        if (proneP > 0.05f) {
          val crawlSwing = sin(legPhase) * 18f * speedNorm.coerceIn(0f, 1f)
          val proneArmPitch = -114f + crawlSwing
          val proneForearm = -72f
          val proneOut = 14f
          armPitch = armPitch * (1f - proneP) + proneArmPitch * proneP
          forearmBend = forearmBend * (1f - proneP) + proneForearm * proneP
          armOut = armOut * (1f - proneP) + proneOut * proneP
        }

        return Triple(armPitch, armOut, forearmBend)
      }

      val (lArm, lOut, lForearm) = calcArm(walkCycle, isRight = false)
      val (rArm, rOut, rForearm) = calcArm(walkCycle + PI.toFloat(), isRight = true)

      return GlbCharacterPose(
        pelvisBounceY = bounceY * (1f - proneP),
        pelvisSwayX = pelvisSwayX * (1f - proneP * 0.6f),
        pelvisYawDeg = pelvisYawDeg * (1f - proneP),
        pelvisRollDeg = pelvisRollDeg * (1f - proneP),
        rootPitchDeg = rootPitchDeg,
        torsoYawDeg = torsoYawDeg * (1f - proneP),
        torsoLeanDeg = torsoLeanDeg * (1f - proneP) + (-14f * proneP),
        headPitchDeg = (sin(worldTimeSec * 1.4f) * 1.6f * idleFactor - (if (isSkydiving) 45f else 0f)) * (1f - proneP) + (-54f * proneP),
        headYawDeg = -torsoYawDeg * 0.45f * (1f - proneP),
        leftThighPitchDeg = lThigh,
        leftKneeBendDeg = lKnee,
        leftAnklePitchDeg = lAnkle,
        rightThighPitchDeg = rThigh,
        rightThighOutDeg = rThighOut,
        rightKneeBendDeg = rKnee,
        rightAnklePitchDeg = rAnkle,
        leftArmPitchDeg = lArm,
        leftArmOutDeg = lOut,
        leftForearmBendDeg = lForearm,
        rightArmPitchDeg = rArm,
        rightArmOutDeg = rOut,
        rightForearmBendDeg = rForearm,
      )
    }

    fun computePlayerTransform(
      outMatrix: FloatArray,
      x: Float,
      y: Float,
      z: Float,
      yawRad: Float,
      fallProgress: Float,
      sitProgress: Float,
      proneProgress: Float = 0f,
      landingReaction: LandingReactionType? = null,
      landingProgress: Float = 0f,
      hasSkeletalRig: Boolean = true,
      walkCycle: Float = 0f,
      speedNorm: Float = 0f,
      isSprinting: Boolean = false,
    ) {
      val yawDeg = Math.toDegrees(yawRad.toDouble()).toFloat()
      val isCombatRoll = landingReaction == LandingReactionType.COMBAT_ROLL && landingProgress < 0.96f
      val landP = landingProgress.coerceIn(0f, 1f)
      val rollCurve = if (isCombatRoll) sin(landP * PI.toFloat()) else 0f
      val sitDropY = sitProgress * 0.36f * (1f - fallProgress)
      val proneDropY = proneProgress.coerceIn(0f, 1f) * 0.80f * (1f - fallProgress)
      val rollDropY = rollCurve * 0.28f
      val fallOffsetY = fallProgress * 0.16f
      val rigidBounceY =
        if (hasSkeletalRig || fallProgress > 0.1f || sitProgress > 0.1f || proneProgress > 0.1f) 0f
        else abs(sin(walkCycle)) * 0.055f * speedNorm.coerceIn(0f, 1.35f) * (if (isSprinting) 1.15f else 0.9f)

      Matrix.setIdentityM(outMatrix, 0)
      Matrix.translateM(outMatrix, 0, x, y + rigidBounceY + fallOffsetY - sitDropY - proneDropY - rollDropY, z)
      Matrix.rotateM(outMatrix, 0, yawDeg, 0f, 1f, 0f)
      if (isCombatRoll) {
        Matrix.translateM(outMatrix, 0, 0f, 0.72f, 0f)
        Matrix.rotateM(outMatrix, 0, landP * 360f, 1f, 0f, 0f)
        Matrix.translateM(outMatrix, 0, 0f, -0.72f, 0f)
      }
      if (!hasSkeletalRig && fallProgress > 0.05f) {
        Matrix.rotateM(outMatrix, 0, -fallProgress * 86f, 1f, 0f, 0f)
      } else if (!hasSkeletalRig && proneProgress > 0.05f) {
        Matrix.translateM(outMatrix, 0, 0f, 0.22f, 0f)
        Matrix.rotateM(outMatrix, 0, proneProgress * 84f, 1f, 0f, 0f)
      }
    }

    fun tryLoadFromAssets(
      context: Context?,
      assetPath: String = "/cyberpunk_character.glb",
      targetHeightMeters: Float = 2.02f,
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
        val isDynamic = parsed.rig.hasLocomotionRig
        val texIds = IntArray(parsed.images.size) { idx -> uploadImageTexture(parsed.images[idx]) }
        val gpuParts =
          parsed.parts.map { part ->
            val texId = texIds.getOrElse(part.material.baseColorImageIndex) { 0 }
            GpuPart(
              mesh = Mesh(part.vertices, part.indices, isDynamic = isDynamic),
              material = part.material,
              textureId = texId,
            )
          }
        val runningFbxClip = tryLoadFbxAnimation(context)
        Log.i(
          TAG,
          "Loaded $sourceLabel: ${parsed.parts.size} parts, ${parsed.totalTriangles} tris, " +
            "rig=${parsed.rig.hasLocomotionRig}, rotated180Y=${parsed.rotated180Y}, " +
            "fbxRunClip=${runningFbxClip != null}, " +
            "minY=${parsed.normalizedBounds.minY}, height=${"%.2f".format(parsed.normalizedBounds.height)}m",
        )
        GlbGpuModel(gpuParts, texIds, parsed, runningFbxClip = runningFbxClip)
      } catch (t: Throwable) {
        Log.e(TAG, "Failed to load GLB from $sourceLabel", t)
        null
      }
    }

    private fun tryLoadFbxAnimation(context: Context): FbxAnimationClip? {
      val candidates =
        listOf(
          "Running (1).fbx",
          "assets/aistudio/Running (1).fbx",
          "public/assets/aistudio/Running (1).fbx",
          "Running%20(1).fbx",
          "assets/aistudio/Running%20(1).fbx",
          "Running.fbx",
        )
      var bytes: ByteArray? = null
      var source = ""
      for (name in candidates) {
        bytes =
          runCatching {
            context.assets.open(name).use { it.readBytes() }
          }.getOrNull()
        if (bytes != null) {
          source = "asset://$name"
          break
        }
      }
      if (bytes == null) {
        val fileCandidates =
          listOf(
            File(context.filesDir, "Running (1).fbx"),
            File(context.filesDir, "assets/aistudio/Running (1).fbx"),
            File(context.filesDir, "public/assets/aistudio/Running (1).fbx"),
          )
        for (f in fileCandidates) {
          if (f.isFile && f.canRead()) {
            bytes = runCatching { f.readBytes() }.getOrNull()
            if (bytes != null) {
              source = f.absolutePath
              break
            }
          }
        }
      }
      if (bytes == null) {
        Log.i(TAG, "FBX clip Running (1).fbx not bundled in assets; using fallback humanoid locomotion.")
        return null
      }
      return runCatching {
        FbxAnimLoader.parseFbxBytes(bytes, fallbackName = "Running (1)").also { clip ->
          Log.i(TAG, "Loaded FBX animation $source: duration=${"%.2f".format(clip.durationSec)}s, bones=${clip.orderedBones.size}")
        }
      }.onFailure { err ->
        Log.e(TAG, "Failed to parse FBX animation from $source", err)
      }.getOrNull()
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
