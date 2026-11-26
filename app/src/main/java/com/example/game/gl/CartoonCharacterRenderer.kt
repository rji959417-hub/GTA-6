package com.example.game.gl

import android.opengl.GLES20
import android.opengl.Matrix
import com.example.game.state.CombatMoveType
import com.example.game.state.GunSkin
import com.example.game.state.HitZone
import com.example.game.state.LandingReactionType
import com.example.game.state.ParkourActionType
import com.example.game.state.WeaponType
import com.example.game.world.BodyBuild
import com.example.game.world.CharacterAppearance
import com.example.game.world.MaterialSurface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

internal class CartoonCharacterRenderer(
  private val meshes: CharacterMeshBuilder.CharacterMeshes,
) {
  private val headRenderer = CartoonHeadRenderer(meshes)

  private val tmpMat = FloatArray(16)
  private val modelMat = FloatArray(16)
  private val rootMat = FloatArray(16)
  private val pelvisMat = FloatArray(16)
  private val torsoMat = FloatArray(16)
  private val jointMat = FloatArray(16)
  private val lowerJointMat = FloatArray(16)
  private val wristMat = FloatArray(16)

  private val warmButtonColor = floatArrayOf(0.84f, 0.72f, 0.46f)
  private val gunMetalDark = floatArrayOf(0.14f, 0.15f, 0.18f)
  private val gunSteel = floatArrayOf(0.32f, 0.35f, 0.40f)
  private val gunWoodGrip = floatArrayOf(0.45f, 0.27f, 0.14f)
  private val scopeLensCyan = floatArrayOf(0.28f, 0.82f, 0.96f)
  private val muzzleGlow = floatArrayOf(1.0f, 0.78f, 0.24f)

  fun drawWalkingCharacter(
    x: Float,
    y: Float,
    z: Float,
    yawRad: Float,
    walkCycle: Float,
    speedNorm: Float,
    isSprinting: Boolean,
    isSwimming: Boolean,
    armReachNorm: Float,
    fallProgress: Float,
    worldTimeSec: Float,
    appearance: CharacterAppearance,
    uModel: Int,
    uBaseColor: Int,
    uMaterialType: Int,
    uEmissive: Int,
    uMetallic: Int,
    uRoughness: Int,
    sitProgress: Float = 0f,
    armsUpNorm: Float = 0f,
    attackMove: CombatMoveType? = null,
    attackProgress: Float = 0f,
    equippedWeapon: WeaponType = WeaponType.FISTS,
    aimNorm: Float = 0f,
    muzzleFlash: Float = 0f,
    hitReactZone: HitZone = HitZone.NONE,
    hitReactProgress: Float = 0f,
    hitReactDirSign: Float = 1f,
    isSkydiving: Boolean = false,
    isParachuting: Boolean = false,
    parkourType: ParkourActionType? = null,
    parkourProgress: Float = 0f,
    airborneFallNorm: Float = 0f,
    landingReaction: LandingReactionType? = null,
    landingProgress: Float = 0f,
    vestLevel: Int = 0,
    helmetLevel: Int = 0,
    gunSkin: GunSkin = GunSkin.STANDARD_STEEL,
  ) {
    val yawDeg = Math.toDegrees(yawRad.toDouble()).toFloat()
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

    val pReact = hitReactProgress.coerceIn(0f, 1f)
    val reactCurve =
      if (hitReactZone != HitZone.NONE && fallProgress < 0.25f) pReact * (2f - pReact) else 0f

    val strikeCurve =
      if (attackMove != null) sin(attackProgress.coerceIn(0f, 1f) * PI.toFloat()) else 0f

    val breath = sin(worldTimeSec * 2.2f)
    val idleShiftX = sin(worldTimeSec * 0.9f) * 0.018f * idleFactor

    val bounceY =
      if (isSwimming || fallProgress > 0.1f || sitProgress > 0.1f || activeOverride || airborneFallNorm > 0.1f) 0f
      else abs(sin(walkCycle)) * 0.048f * strideAmp + breath * 0.006f * idleFactor
    val pelvisSwayX = cos(walkCycle) * 0.024f * strideAmp + idleShiftX
    val legHitRoll =
      if (hitReactZone == HitZone.LEGS) hitReactDirSign * 14f * reactCurve else 0f
    val pelvisRollDeg =
      cos(walkCycle) * 3.2f * strideAmp +
        sin(worldTimeSec * 0.9f) * 1.4f * idleFactor +
        legHitRoll +
        (if (isVaulting) 22f * vaultCurve else 0f) +
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
    val hitTorsoYaw =
      if (hitReactZone == HitZone.HEAD) hitReactDirSign * 18f * reactCurve else 0f
    val torsoCounterYawDeg = sin(walkCycle) * 14.0f * strideAmp + attackTorsoYaw + hitTorsoYaw

    val baseTorsoLean =
      when {
        fallProgress > 0.01f -> -fallProgress * 86f
        isSkydiving -> 72f + sin(worldTimeSec * 4.5f) * 4f
        isParachuting -> 8f + sin(worldTimeSec * 2.2f) * 3f
        isClimbing ->
          if (parkourP <= 0.45f) 10f + (parkourP / 0.45f) * 18f
          else 28f * (1f - ((parkourP - 0.45f) / 0.55f))
        isVaulting -> 34f * vaultCurve
        isHardStagger -> 36f * staggerCurve
        airborneFallNorm > 0.05f -> -12f * airborneFallNorm + sin(worldTimeSec * 11f) * 4f * airborneFallNorm
        isSwimming -> 54f
        attackMove == CombatMoveType.PUNCH_UPPERCUT -> 10f * strikeCurve
        attackMove != null && attackMove.isKick -> -14f * strikeCurve
        sitProgress > 0.05f -> 4.5f * sitProgress + breath * 0.8f
        isSprinting -> 13f
        clampedSpeed > 0.1f -> 4.2f * clampedSpeed
        armReachNorm > 0.05f -> 15f * armReachNorm
        aimNorm > 0.05f -> 6f * aimNorm
        else -> 1.2f + breath * 1.0f + 11f * softLandCurve
      }
    val torsoLeanDeg =
      when {
        reactCurve > 0.02f && hitReactZone == HitZone.HEAD ->
          baseTorsoLean * (1f - reactCurve) + (-24f * reactCurve)
        reactCurve > 0.02f && hitReactZone == HitZone.TORSO ->
          baseTorsoLean * (1f - reactCurve) + (34f * reactCurve)
        reactCurve > 0.02f && hitReactZone == HitZone.LEGS ->
          baseTorsoLean * (1f - reactCurve) + (14f * reactCurve)
        else -> baseTorsoLean
      }

    val fallDropY = fallProgress * 0.22f
    val isChubby = appearance.build == BodyBuild.ROUND_CHUBBY
    val isTall = appearance.build == BodyBuild.TALL_LEAN

    val sitDropHeight = if (isChubby) 0.30f else if (isTall) 0.41f else 0.36f
    val sitDropY = sitProgress * sitDropHeight * (1f - fallProgress)
    val reactDropY = staggerCurve * 0.45f + softLandCurve * 0.14f + rollCurve * 0.28f

    Matrix.setIdentityM(rootMat, 0)
    Matrix.translateM(rootMat, 0, x, y + bounceY + fallDropY - sitDropY - reactDropY, z)
    Matrix.rotateM(rootMat, 0, yawDeg, 0f, 1f, 0f)

    val legSpacing = if (isChubby) 0.165f else 0.125f
    val thighWidth = if (isChubby) 0.21f else if (isTall) 0.15f else 0.175f
    val calfWidth = thighWidth * 0.78f
    val hipY = if (isChubby) 0.62f else if (isTall) 0.86f else 0.76f
    val legLen = hipY - 0.07f

    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, pelvisSwayX, 0f, 0f)
    if (isCombatRoll) {
      val rollPivotY = hipY * 0.72f
      Matrix.translateM(tmpMat, 0, 0f, rollPivotY, 0f)
      Matrix.rotateM(tmpMat, 0, landP * 360f, 1f, 0f, 0f)
      Matrix.translateM(tmpMat, 0, 0f, -rollPivotY, 0f)
    } else if (fallProgress > 0.01f || isSwimming || isSkydiving) {
      Matrix.rotateM(tmpMat, 0, torsoLeanDeg, 1f, 0f, 0f)
    }
    Matrix.rotateM(tmpMat, 0, pelvisYawDeg, 0f, 1f, 0f)
    Matrix.rotateM(tmpMat, 0, pelvisRollDeg, 0f, 0f, 1f)
    Matrix.multiplyMM(pelvisMat, 0, rootMat, 0, tmpMat, 0)

    val shoulderX = if (isChubby) 0.365f else if (isTall) 0.245f else 0.295f
    val shoulderY = if (isChubby) 1.22f else if (isTall) 1.46f else 1.32f
    val armWidth = if (isChubby) 0.165f else if (isTall) 0.12f else 0.14f
    val armLen = if (isChubby) 0.50f else if (isTall) 0.58f else 0.54f
    val baseArmOutDeg = if (isChubby) 15f else 7f

    val headY = if (isChubby) 1.61f else if (isTall) 1.77f else 1.60f
    val headScaleX = if (isChubby) 0.46f else 0.35f
    val headScaleY = if (isChubby) 0.47f else if (isTall) 0.40f else 0.37f
    val headScaleZ = if (isChubby) 0.43f else 0.35f

    GLES20.glUniform1f(uEmissive, 0f)
    GLES20.glUniform1f(uMetallic, 0.02f)
    GLES20.glUniform1f(uRoughness, 0.68f)

    // 1. Articulated 2-Joint Legs (Walking, Kicking, Sitting, Leg-Stumble, Swimming, or Knocked Down)
    for (legSign in listOf(-1f, 1f)) {
      val phase = if (legSign < 0f) walkCycle else walkCycle + PI.toFloat()
      var baseThighPitch: Float
      var baseKneeBend: Float
      when {
        fallProgress > 0.1f -> {
          baseThighPitch = -24f * fallProgress
          baseKneeBend = 34f * fallProgress
        }
        isSkydiving -> {
          baseThighPitch = 16f + sin(worldTimeSec * 6.5f + legSign * 1.4f) * 14f
          baseKneeBend = 38f + cos(worldTimeSec * 6.5f + legSign) * 14f
        }
        isParachuting -> {
          baseThighPitch = -14f + sin(worldTimeSec * 2.5f + legSign) * 7f
          baseKneeBend = 24f + cos(worldTimeSec * 2.1f) * 5f
        }
        isCombatRoll -> {
          baseThighPitch = -86f * rollCurve
          baseKneeBend = 108f * rollCurve
        }
        isHardStagger -> {
          if (legSign > 0f) {
            baseThighPitch = -82f * staggerCurve
            baseKneeBend = 94f * staggerCurve
          } else {
            baseThighPitch = -48f * staggerCurve
            baseKneeBend = 108f * staggerCurve
          }
        }
        isClimbing -> {
          if (parkourP <= 0.45f) {
            val t = (parkourP / 0.45f).coerceIn(0f, 1f)
            baseThighPitch = if (legSign > 0f) -84f * t else 14f - 38f * t
            baseKneeBend = if (legSign > 0f) 94f * t else 28f * t
          } else {
            val t = ((parkourP - 0.45f) / 0.55f).coerceIn(0f, 1f)
            baseThighPitch = if (legSign > 0f) -84f * (1f - t) else -24f * (1f - t)
            baseKneeBend = if (legSign > 0f) 94f * (1f - t) else 28f * (1f - t)
          }
        }
        isVaulting -> {
          baseThighPitch = -74f * vaultCurve
          baseKneeBend = 100f * vaultCurve
        }
        airborneFallNorm > 0.05f -> {
          val cycle = sin(worldTimeSec * 12f + legSign * 1.8f) * 14f
          baseThighPitch = -30f * airborneFallNorm + cycle * airborneFallNorm
          baseKneeBend = 48f * airborneFallNorm
        }
        isSwimming -> {
          baseThighPitch = -sin(phase) * 28f
          baseKneeBend = max(6f, cos(phase) * 32f + 12f)
        }
        attackMove == CombatMoveType.KICK_FRONT && legSign > 0f -> {
          baseThighPitch = -98f * strikeCurve
          baseKneeBend = max(4f, 52f * (1f - strikeCurve))
        }
        attackMove == CombatMoveType.KICK_ROUNDHOUSE && legSign > 0f -> {
          baseThighPitch = -88f * strikeCurve
          baseKneeBend = max(6f, 38f * (1f - strikeCurve * 0.85f))
        }
        else -> {
          baseThighPitch = -sin(phase) * 33f * strideAmp
          val swingLift = max(0f, (cos(phase) + 0.30f) / 1.30f)
          baseKneeBend = 4.5f + swingLift * 48f * strideAmp + 32f * softLandCurve
        }
      }

      if (reactCurve > 0.02f) {
        when (hitReactZone) {
          HitZone.LEGS -> {
            if (legSign * hitReactDirSign > 0f) {
              baseThighPitch -= 24f * reactCurve
              baseKneeBend += 48f * reactCurve
            } else {
              baseKneeBend += 16f * reactCurve
            }
          }
          HitZone.TORSO -> {
            baseThighPitch -= 16f * reactCurve
            baseKneeBend += 24f * reactCurve
          }
          HitZone.HEAD -> {
            baseKneeBend += 12f * reactCurve
          }
          HitZone.NONE -> Unit
        }
      }

      // Smooth human sitting leg articulation: thighs horizontal (-84°), calves vertical down (+86°)
      val sitThigh = -84f
      val sitKnee = 86f
      val thighPitch = baseThighPitch * (1f - sitProgress) + sitThigh * sitProgress
      val kneeBend = baseKneeBend * (1f - sitProgress) + sitKnee * sitProgress
      val anklePitch =
        ((-thighPitch * 0.28f - kneeBend * 0.44f) * (1f - sitProgress)).coerceIn(-24f, 26f)
      val kickOutDeg =
        if (attackMove == CombatMoveType.KICK_ROUNDHOUSE && legSign > 0f) 34f * strikeCurve else 0f

      drawArticulatedLeg(
        parent = pelvisMat,
        hipX = legSign * legSpacing,
        hipY = hipY,
        thighW = thighWidth,
        calfW = calfWidth,
        legLen = legLen,
        thighPitchDeg = thighPitch,
        thighOutDeg = kickOutDeg,
        kneeBendDeg = kneeBend,
        anklePitchDeg = anklePitch,
        footYawDeg = legSign * (5.5f + sitProgress * 6.5f),
        pantsColor = appearance.pantsColor,
        shoeColor = appearance.shoeColor,
        uModel = uModel,
        uBaseColor = uBaseColor,
        uMaterialType = uMaterialType,
        uMetallic = uMetallic,
        uRoughness = uRoughness,
      )
    }

    // 2. Upper Spine & Torso
    val spineLean = if (fallProgress > 0.01f || isSwimming || isSkydiving) 0f else torsoLeanDeg
    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, 0f, hipY, 0f)
    Matrix.rotateM(tmpMat, 0, torsoCounterYawDeg, 0f, 1f, 0f)
    Matrix.rotateM(tmpMat, 0, spineLean, 1f, 0f, 0f)
    Matrix.translateM(tmpMat, 0, 0f, -hipY + breath * 0.008f, 0f)
    Matrix.multiplyMM(torsoMat, 0, pelvisMat, 0, tmpMat, 0)

    drawTorsoAndVest(
      parent = torsoMat,
      appearance = appearance,
      isChubby = isChubby,
      isTall = isTall,
      hipY = hipY,
      shoulderY = shoulderY,
      vestLevel = vestLevel,
      uModel = uModel,
      uBaseColor = uBaseColor,
      uMaterialType = uMaterialType,
      uEmissive = uEmissive,
      uMetallic = uMetallic,
      uRoughness = uRoughness,
    )

    // 3. Articulated 2-Joint Arms + Hit-Zone Reactions + Held 3D Weapons
    val holdingGun =
      equippedWeapon != WeaponType.FISTS &&
        fallProgress < 0.25f &&
        !isSwimming &&
        !isSkydiving &&
        !isParachuting &&
        !isClimbing &&
        !isVaulting &&
        !isCombatRoll
    for (armSign in listOf(-1f, 1f)) {
      val phase = if (armSign < 0f) walkCycle + PI.toFloat() else walkCycle
      var shoulderPitch: Float
      var elbowBend: Float
      var armOutDeg = baseArmOutDeg

      when {
        isSkydiving -> {
          shoulderPitch = -104f + sin(worldTimeSec * 5.2f + armSign) * 8f
          elbowBend = -36f
          armOutDeg = 54f
        }
        isParachuting -> {
          shoulderPitch = -152f
          elbowBend = -38f
          armOutDeg = 18f
        }
        isClimbing -> {
          // Phase 1: Reach BOTH hands high up to grab ledge edge -> pull chest up -> press body onto rim!
          if (parkourP <= 0.45f) {
            val t = (parkourP / 0.45f).coerceIn(0f, 1f)
            shoulderPitch = -164f + 36f * t
            elbowBend = -22f - 80f * t
            armOutDeg = 12f
          } else {
            val t = ((parkourP - 0.45f) / 0.55f).coerceIn(0f, 1f)
            shoulderPitch = -128f + 108f * t
            elbowBend = -102f + 84f * t
            armOutDeg = 12f
          }
        }
        isVaulting -> {
          // Two-hand plant downward onto railing/wall top then push off cleanly
          shoulderPitch = -76f * vaultCurve
          elbowBend = -26f * vaultCurve
          armOutDeg = baseArmOutDeg + 14f * vaultCurve
        }
        isCombatRoll -> {
          // Arms tucked across chest/head during combat roll
          shoulderPitch = -66f * rollCurve
          elbowBend = -98f * rollCurve
          armOutDeg = 4f
        }
        isHardStagger -> {
          // Hands brace downward against ground/knees after hard rooftop drop
          shoulderPitch = -64f * staggerCurve
          elbowBend = -28f * staggerCurve
          armOutDeg = baseArmOutDeg + 14f * staggerCurve
        }
        airborneFallNorm > 0.05f -> {
          // Arms flare out & flail while falling from rooftop height
          val wave = sin(worldTimeSec * 14f + armSign * 1.4f) * 14f * airborneFallNorm
          shoulderPitch = -126f * airborneFallNorm + wave
          elbowBend = -34f * airborneFallNorm
          armOutDeg = baseArmOutDeg + 38f * airborneFallNorm
        }
        armReachNorm > 0.05f -> {
          val reachMix = if (armSign > 0f) 1.0f else 0.72f
          shoulderPitch = -92f * armReachNorm * reachMix
          elbowBend = -28f * armReachNorm
        }
        fallProgress > 0.2f -> {
          shoulderPitch = if (armSign < 0f) -122f * fallProgress else -102f * fallProgress
          elbowBend = -30f
        }
        reactCurve > 0.12f && hitReactZone == HitZone.HEAD -> {
          // Both hands fly up to shield head/face on head punch/headshot!
          shoulderPitch = -114f * reactCurve
          elbowBend = -112f * reactCurve
          armOutDeg = baseArmOutDeg - 6f * reactCurve
        }
        reactCurve > 0.12f && hitReactZone == HitZone.TORSO -> {
          // Both arms clutch inward over injured stomach/ribs!
          shoulderPitch = -68f * reactCurve
          elbowBend = -88f * reactCurve
          armOutDeg = -9f * reactCurve
        }
        reactCurve > 0.12f && hitReactZone == HitZone.LEGS -> {
          // Arms fling out to keep balance during leg stumble
          shoulderPitch = -36f * reactCurve
          elbowBend = -28f * reactCurve
          armOutDeg = baseArmOutDeg + 34f * reactCurve
        }
        armsUpNorm > 0.1f -> {
          val panicWave = sin(worldTimeSec * 12f + armSign * 1.4f) * 14f * armsUpNorm
          shoulderPitch = (-146f * armsUpNorm) + panicWave
          armOutDeg = baseArmOutDeg + 20f * armsUpNorm
          elbowBend = -24f * armsUpNorm
        }
        attackMove != null && !attackMove.isKick -> {
          when (attackMove) {
            CombatMoveType.PUNCH_JAB ->
              if (armSign > 0f) {
                shoulderPitch = -92f * strikeCurve
                elbowBend = -10f
              } else {
                shoulderPitch = -48f * strikeCurve
                elbowBend = -75f * strikeCurve
              }
            CombatMoveType.PUNCH_HOOK ->
              if (armSign > 0f) {
                shoulderPitch = -80f * strikeCurve
                armOutDeg = baseArmOutDeg + 38f * strikeCurve
                elbowBend = -68f * strikeCurve
              } else {
                shoulderPitch = -50f * strikeCurve
                elbowBend = -72f * strikeCurve
              }
            else -> // PUNCH_UPPERCUT
              if (armSign > 0f) {
                shoulderPitch = -118f * strikeCurve
                elbowBend = -82f * strikeCurve
              } else {
                shoulderPitch = -46f * strikeCurve
                elbowBend = -70f * strikeCurve
              }
          }
        }
        holdingGun && armSign > 0f -> {
          // Right arm carries gun down slightly at rest (-34°), snaps straight to aim (-90°) when shooting or scoped!
          shoulderPitch = -34f - 56f * aimNorm
          elbowBend = -44f + 36f * aimNorm
        }
        holdingGun && equippedWeapon.twoHanded && armSign < 0f -> {
          shoulderPitch = -40f - 40f * aimNorm
          elbowBend = -52f + 12f * aimNorm
        }
        isSwimming -> {
          shoulderPitch = sin(phase) * 58f - 48f
          elbowBend = -38f - cos(phase) * 24f
        }
        else -> {
          val walkPitch = -7f - sin(phase) * 32f * strideAmp
          val fwdSwing = max(0f, sin(phase))
          val walkElbow =
            -18f - fwdSwing * (if (isSprinting) 46f else 32f) * strideAmp - breath * 1.8f * idleFactor
          // Sitting blends arms calmly resting over lap
          shoulderPitch = walkPitch * (1f - sitProgress) + (-22f) * sitProgress
          elbowBend = walkElbow * (1f - sitProgress) + (-48f) * sitProgress
          armOutDeg = baseArmOutDeg + sitProgress * 4f
        }
      }

      drawArticulatedArm(
        parent = torsoMat,
        armSign = armSign,
        shoulderX = armSign * shoulderX,
        shoulderY = shoulderY,
        armW = armWidth,
        armLen = armLen,
        shoulderPitchDeg = shoulderPitch,
        armOutDeg = armOutDeg,
        elbowBendDeg = elbowBend,
        tunicColor = appearance.tunicColor,
        skinColor = appearance.skinColor,
        uModel = uModel,
        uBaseColor = uBaseColor,
        uMaterialType = uMaterialType,
        uRoughness = uRoughness,
        heldWeapon = if (armSign > 0f && holdingGun) equippedWeapon else WeaponType.FISTS,
        muzzleFlash = if (armSign > 0f && holdingGun) muzzleFlash else 0f,
        uEmissive = uEmissive,
        uMetallic = uMetallic,
        gunSkin = gunSkin,
      )
    }

    // 4. Expressive Sculpted Head (with dramatic whip/flinch on head strikes & climb/fall looks!)
    val headHitYaw = if (hitReactZone == HitZone.HEAD) hitReactDirSign * 34f * reactCurve else 0f
    val headHitPitch =
      when {
        hitReactZone == HitZone.HEAD -> -40f * reactCurve
        isClimbing -> -26f * sin(parkourP * PI.toFloat())
        isCombatRoll -> 34f * rollCurve
        isHardStagger -> 24f * staggerCurve
        else -> 0f
      }
    val headHitRoll = if (hitReactZone == HitZone.HEAD) -hitReactDirSign * 18f * reactCurve else 0f

    val headYaw = -torsoCounterYawDeg * 0.45f + sin(worldTimeSec * 0.85f) * 6.5f * idleFactor + headHitYaw
    val headPitch =
      -spineLean * 0.55f + cos(worldTimeSec * 1.1f) * 2.2f * idleFactor - breath * 1.1f + headHitPitch
    val headRoll = cos(worldTimeSec * 0.62f) * 2.5f * idleFactor - pelvisRollDeg * 0.4f + headHitRoll

    headRenderer.drawExpressiveHead(
      parent = torsoMat,
      headX = 0f,
      headY = headY,
      headZ = 0.04f,
      sx = headScaleX,
      sy = headScaleY,
      sz = headScaleZ,
      headYawDeg = headYaw,
      headPitchDeg = headPitch,
      headRollDeg = headRoll,
      worldTimeSec = worldTimeSec,
      appearance = appearance,
      uModel = uModel,
      uBaseColor = uBaseColor,
      uMaterialType = uMaterialType,
      uEmissive = uEmissive,
      uMetallic = uMetallic,
      uRoughness = uRoughness,
      helmetLevel = helmetLevel,
    )
  }

  fun drawSeatedDriver(
    parent: FloatArray,
    seatX: Float,
    seatY: Float,
    seatZ: Float,
    worldTimeSec: Float,
    appearance: CharacterAppearance,
    uModel: Int,
    uBaseColor: Int,
    uMaterialType: Int,
    uEmissive: Int,
    uMetallic: Int,
    uRoughness: Int,
  ) {
    val seatRoot = FloatArray(16)
    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, seatX, seatY, seatZ)
    val scale = if (appearance.build == BodyBuild.ROUND_CHUBBY) 0.84f else 0.86f
    Matrix.scaleM(tmpMat, 0, scale, scale, scale)
    Matrix.multiplyMM(seatRoot, 0, parent, 0, tmpMat, 0)

    val isChubby = appearance.build == BodyBuild.ROUND_CHUBBY
    val breath = sin(worldTimeSec * 2.2f)

    GLES20.glUniform1i(uMaterialType, MaterialSurface.CLOTH.id)
    GLES20.glUniform1f(uEmissive, 0f)
    GLES20.glUniform1f(uMetallic, 0.02f)
    GLES20.glUniform1f(uRoughness, 0.74f)
    GLES20.glUniform3fv(uBaseColor, 1, appearance.tunicColor, 0)
    if (isChubby) {
      drawPart(seatRoot, 0f, 0.28f, 0.02f, 0.72f, 0.66f, 0.68f, uModel, meshes.kurtaTorsoMesh)
    } else {
      drawPart(seatRoot, 0f, 0.28f, 0.02f, 0.48f, 0.56f, 0.34f, uModel, meshes.capsuleMesh)
    }

    val vest = appearance.vestColor
    if (vest != null) {
      GLES20.glUniform3fv(uBaseColor, 1, vest, 0)
      val vx = if (isChubby) 0.74f else 0.54f
      val vz = if (isChubby) 0.71f else 0.42f
      drawPart(seatRoot, 0f, 0.31f, 0.01f, vx, 0.58f, vz, uModel, meshes.vestMesh)
      if (appearance.brassButtons) {
        GLES20.glUniform1i(uMaterialType, MaterialSurface.SLATE.id)
        GLES20.glUniform1f(uMetallic, 0.45f)
        GLES20.glUniform1f(uRoughness, 0.28f)
        GLES20.glUniform3fv(uBaseColor, 1, warmButtonColor, 0)
        drawPart(
          seatRoot,
          if (isChubby) -0.17f else -0.12f,
          0.28f,
          if (isChubby) 0.26f else 0.16f,
          0.050f,
          0.050f,
          0.028f,
          uModel,
          meshes.sphereMesh,
        )
      }
    }

    val armX = if (isChubby) 0.32f else 0.24f
    for (armSign in listOf(-1f, 1f)) {
      drawArticulatedArm(
        parent = seatRoot,
        armSign = armSign,
        shoulderX = armSign * armX,
        shoulderY = 0.50f,
        armW = 0.145f,
        armLen = 0.44f,
        shoulderPitchDeg = -46f,
        armOutDeg = 10f,
        elbowBendDeg = -42f,
        tunicColor = appearance.tunicColor,
        skinColor = appearance.skinColor,
        uModel = uModel,
        uBaseColor = uBaseColor,
        uMaterialType = uMaterialType,
        uRoughness = uRoughness,
        heldWeapon = WeaponType.FISTS,
        muzzleFlash = 0f,
        uEmissive = uEmissive,
        uMetallic = uMetallic,
      )
    }

    val headScaleX = if (isChubby) 0.46f else 0.37f
    val headScaleY = if (isChubby) 0.47f else 0.39f
    val headScaleZ = if (isChubby) 0.43f else 0.37f
    headRenderer.drawExpressiveHead(
      parent = seatRoot,
      headX = 0f,
      headY = 0.77f + breath * 0.006f,
      headZ = 0.03f,
      sx = headScaleX,
      sy = headScaleY,
      sz = headScaleZ,
      headYawDeg = sin(worldTimeSec * 0.75f) * 5.0f,
      headPitchDeg = 3.0f + breath * 1.2f,
      headRollDeg = cos(worldTimeSec * 0.6f) * 1.8f,
      worldTimeSec = worldTimeSec,
      appearance = appearance,
      uModel = uModel,
      uBaseColor = uBaseColor,
      uMaterialType = uMaterialType,
      uEmissive = uEmissive,
      uMetallic = uMetallic,
      uRoughness = uRoughness,
    )
  }

  private fun drawArticulatedLeg(
    parent: FloatArray,
    hipX: Float,
    hipY: Float,
    thighW: Float,
    calfW: Float,
    legLen: Float,
    thighPitchDeg: Float,
    thighOutDeg: Float,
    kneeBendDeg: Float,
    anklePitchDeg: Float,
    footYawDeg: Float,
    pantsColor: FloatArray,
    shoeColor: FloatArray,
    uModel: Int,
    uBaseColor: Int,
    uMaterialType: Int,
    uMetallic: Int,
    uRoughness: Int,
  ) {
    val thighLen = legLen * 0.54f
    val calfLen = legLen * 0.52f

    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, hipX, hipY, 0f)
    if (abs(thighOutDeg) > 0.1f) Matrix.rotateM(tmpMat, 0, thighOutDeg, 0f, 0f, 1f)
    Matrix.rotateM(tmpMat, 0, thighPitchDeg, 1f, 0f, 0f)
    Matrix.multiplyMM(jointMat, 0, parent, 0, tmpMat, 0)

    GLES20.glUniform1i(uMaterialType, MaterialSurface.CLOTH.id)
    GLES20.glUniform1f(uMetallic, 0.01f)
    GLES20.glUniform1f(uRoughness, 0.72f)
    GLES20.glUniform3fv(uBaseColor, 1, pantsColor, 0)
    drawPart(jointMat, 0f, -thighLen * 0.48f, 0f, thighW, thighLen * 1.12f, thighW, uModel, meshes.capsuleMesh)

    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, 0f, -thighLen * 0.92f, 0f)
    Matrix.rotateM(tmpMat, 0, kneeBendDeg, 1f, 0f, 0f)
    Matrix.multiplyMM(lowerJointMat, 0, jointMat, 0, tmpMat, 0)

    drawPart(lowerJointMat, 0f, -calfLen * 0.50f, 0f, calfW, calfLen * 1.10f, calfW, uModel, meshes.capsuleMesh)

    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, 0f, -calfLen * 0.96f, 0.02f)
    Matrix.rotateM(tmpMat, 0, footYawDeg, 0f, 1f, 0f)
    Matrix.rotateM(tmpMat, 0, anklePitchDeg, 1f, 0f, 0f)
    Matrix.multiplyMM(wristMat, 0, lowerJointMat, 0, tmpMat, 0)

    GLES20.glUniform1i(uMaterialType, MaterialSurface.SLATE.id)
    GLES20.glUniform1f(uMetallic, 0.08f)
    GLES20.glUniform1f(uRoughness, 0.38f)
    GLES20.glUniform3fv(uBaseColor, 1, shoeColor, 0)
    drawPart(wristMat, 0f, -0.02f, 0.04f, calfW * 1.38f, 0.145f, 0.30f, uModel, meshes.shoeMesh)
  }

  private fun drawArticulatedArm(
    parent: FloatArray,
    armSign: Float,
    shoulderX: Float,
    shoulderY: Float,
    armW: Float,
    armLen: Float,
    shoulderPitchDeg: Float,
    armOutDeg: Float,
    elbowBendDeg: Float,
    tunicColor: FloatArray,
    skinColor: FloatArray,
    uModel: Int,
    uBaseColor: Int,
    uMaterialType: Int,
    uRoughness: Int,
    heldWeapon: WeaponType,
    muzzleFlash: Float,
    uEmissive: Int,
    uMetallic: Int,
    gunSkin: GunSkin = GunSkin.STANDARD_STEEL,
  ) {
    val upperLen = armLen * 0.56f
    val foreLen = armLen * 0.52f
    val foreW = armW * 0.86f

    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, shoulderX, shoulderY, 0.02f)
    Matrix.rotateM(tmpMat, 0, armSign * armOutDeg, 0f, 0f, 1f)
    Matrix.rotateM(tmpMat, 0, shoulderPitchDeg, 1f, 0f, 0f)
    Matrix.multiplyMM(jointMat, 0, parent, 0, tmpMat, 0)

    GLES20.glUniform1i(uMaterialType, MaterialSurface.CLOTH.id)
    GLES20.glUniform1f(uEmissive, 0f)
    GLES20.glUniform1f(uRoughness, 0.74f)
    GLES20.glUniform3fv(uBaseColor, 1, tunicColor, 0)
    drawPart(jointMat, -armSign * 0.025f, -0.02f, 0f, armW * 1.14f, armW * 1.12f, armW * 1.12f, uModel, meshes.sphereMesh)
    drawPart(jointMat, 0f, -upperLen * 0.50f, 0f, armW, upperLen * 1.14f, armW, uModel, meshes.capsuleMesh)

    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, 0f, -upperLen * 0.92f, 0f)
    Matrix.rotateM(tmpMat, 0, elbowBendDeg, 1f, 0f, 0f)
    Matrix.rotateM(tmpMat, 0, armSign * -4f, 0f, 0f, 1f)
    Matrix.multiplyMM(lowerJointMat, 0, jointMat, 0, tmpMat, 0)

    drawPart(lowerJointMat, 0f, -foreLen * 0.46f, 0f, foreW, foreLen * 1.06f, foreW, uModel, meshes.capsuleMesh)

    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, 0f, -foreLen * 0.96f, 0f)
    Matrix.multiplyMM(wristMat, 0, lowerJointMat, 0, tmpMat, 0)

    GLES20.glUniform1i(uMaterialType, MaterialSurface.SKIN.id)
    GLES20.glUniform1f(uRoughness, 0.50f)
    GLES20.glUniform3fv(uBaseColor, 1, skinColor, 0)
    drawPart(wristMat, 0f, -0.02f, 0.008f, foreW * 0.98f, foreW * 1.08f, foreW * 1.08f, uModel, meshes.sphereMesh)
    drawRotatedPart(
      wristMat,
      -armSign * foreW * 0.42f,
      -0.01f,
      foreW * 0.34f,
      28f,
      0f,
      armSign * 22f,
      foreW * 0.42f,
      foreW * 0.65f,
      foreW * 0.42f,
      uModel,
      meshes.capsuleMesh,
    )

    if (heldWeapon != WeaponType.FISTS) {
      drawHeldWeapon(
        wrist = wristMat,
        weapon = heldWeapon,
        muzzleFlash = muzzleFlash,
        gunSkin = gunSkin,
        uModel = uModel,
        uBaseColor = uBaseColor,
        uMaterialType = uMaterialType,
        uEmissive = uEmissive,
        uMetallic = uMetallic,
        uRoughness = uRoughness,
      )
    }
  }

  private fun drawHeldWeapon(
    wrist: FloatArray,
    weapon: WeaponType,
    muzzleFlash: Float,
    gunSkin: GunSkin,
    uModel: Int,
    uBaseColor: Int,
    uMaterialType: Int,
    uEmissive: Int,
    uMetallic: Int,
    uRoughness: Int,
  ) {
    // Orient gun barrel forward along the aiming line (local -Y of hanging wrist when shoulder is -90°)
    val gunMat = FloatArray(16)
    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, 0f, -0.06f, 0.02f)
    Matrix.rotateM(tmpMat, 0, 90f, 1f, 0f, 0f)
    Matrix.multiplyMM(gunMat, 0, wrist, 0, tmpMat, 0)

    GLES20.glUniform1i(uMaterialType, MaterialSurface.SLATE.id)
    GLES20.glUniform1f(uEmissive, gunSkin.emissive)
    GLES20.glUniform1f(uMetallic, gunSkin.metallic)
    GLES20.glUniform1f(uRoughness, gunSkin.roughness)

    val barrelTipZ: Float
    when (weapon) {
      WeaponType.PISTOL_9MM -> {
        GLES20.glUniform3fv(uBaseColor, 1, gunSkin.primaryColor, 0)
        drawPart(gunMat, 0f, 0.06f, 0.14f, 0.06f, 0.07f, 0.28f, uModel, meshes.boxMesh)
        GLES20.glUniform3fv(uBaseColor, 1, gunSkin.gripColor, 0)
        drawPart(gunMat, 0f, -0.02f, 0.03f, 0.055f, 0.13f, 0.075f, uModel, meshes.boxMesh)
        barrelTipZ = 0.29f
      }
      WeaponType.SMG_MICRO -> {
        GLES20.glUniform3fv(uBaseColor, 1, gunSkin.primaryColor, 0)
        drawPart(gunMat, 0f, 0.06f, 0.18f, 0.075f, 0.09f, 0.36f, uModel, meshes.boxMesh)
        GLES20.glUniform3fv(uBaseColor, 1, gunSkin.secondaryColor, 0)
        drawPart(gunMat, 0f, 0.06f, 0.40f, 0.035f, 0.035f, 0.16f, uModel, meshes.boxMesh)
        drawPart(gunMat, 0f, -0.05f, 0.21f, 0.045f, 0.18f, 0.06f, uModel, meshes.boxMesh)
        barrelTipZ = 0.49f
      }
      WeaponType.SHOTGUN -> {
        GLES20.glUniform3fv(uBaseColor, 1, gunSkin.gripColor, 0)
        drawPart(gunMat, 0f, 0.03f, -0.10f, 0.075f, 0.11f, 0.26f, uModel, meshes.boxMesh)
        GLES20.glUniform3fv(uBaseColor, 1, gunSkin.primaryColor, 0)
        drawPart(gunMat, 0f, 0.08f, 0.24f, 0.07f, 0.075f, 0.56f, uModel, meshes.boxMesh)
        GLES20.glUniform3fv(uBaseColor, 1, gunSkin.secondaryColor, 0)
        drawPart(gunMat, 0f, 0.03f, 0.34f, 0.085f, 0.065f, 0.22f, uModel, meshes.boxMesh)
        barrelTipZ = 0.54f
      }
      WeaponType.SNIPER_RIFLE -> {
        GLES20.glUniform3fv(uBaseColor, 1, gunSkin.primaryColor, 0)
        drawPart(gunMat, 0f, 0.04f, -0.15f, 0.068f, 0.11f, 0.28f, uModel, meshes.boxMesh)
        drawPart(gunMat, 0f, 0.07f, 0.18f, 0.072f, 0.095f, 0.48f, uModel, meshes.boxMesh)
        GLES20.glUniform3fv(uBaseColor, 1, gunSkin.secondaryColor, 0)
        drawPart(gunMat, 0f, 0.09f, 0.62f, 0.032f, 0.032f, 0.46f, uModel, meshes.boxMesh)
        drawPart(gunMat, 0f, 0.09f, 0.84f, 0.046f, 0.046f, 0.08f, uModel, meshes.boxMesh)
        drawPart(gunMat, 0f, -0.05f, 0.22f, 0.046f, 0.15f, 0.09f, uModel, meshes.boxMesh)
        drawRotatedPart(gunMat, 0f, 0.155f, 0.20f, 90f, 0f, 0f, 0.052f, 0.28f, 0.052f, uModel, meshes.capsuleMesh)
        GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
        GLES20.glUniform1f(uEmissive, 0.45f)
        GLES20.glUniform3fv(uBaseColor, 1, scopeLensCyan, 0)
        drawPart(gunMat, 0f, 0.155f, 0.34f, 0.042f, 0.042f, 0.018f, uModel, meshes.sphereMesh)
        GLES20.glUniform1f(uEmissive, gunSkin.emissive)
        barrelTipZ = 0.88f
      }
      WeaponType.ASSAULT_RIFLE -> {
        GLES20.glUniform3fv(uBaseColor, 1, gunSkin.gripColor, 0)
        drawPart(gunMat, 0f, 0.04f, -0.12f, 0.07f, 0.11f, 0.24f, uModel, meshes.boxMesh)
        GLES20.glUniform3fv(uBaseColor, 1, gunSkin.primaryColor, 0)
        drawPart(gunMat, 0f, 0.08f, 0.20f, 0.075f, 0.095f, 0.46f, uModel, meshes.boxMesh)
        GLES20.glUniform3fv(uBaseColor, 1, gunSkin.secondaryColor, 0)
        drawPart(gunMat, 0f, 0.09f, 0.52f, 0.035f, 0.035f, 0.26f, uModel, meshes.boxMesh)
        drawPart(gunMat, 0f, -0.06f, 0.25f, 0.045f, 0.19f, 0.08f, uModel, meshes.boxMesh)
        barrelTipZ = 0.66f
      }
      WeaponType.FISTS -> barrelTipZ = 0f
    }
    GLES20.glUniform1f(uEmissive, 0f)

    if (muzzleFlash > 0.05f && barrelTipZ > 0f) {
      GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
      GLES20.glUniform1f(uEmissive, 1.8f * muzzleFlash)
      GLES20.glUniform3fv(uBaseColor, 1, muzzleGlow, 0)
      val flashS = (0.14f + muzzleFlash * 0.16f)
      drawPart(gunMat, 0f, 0.08f, barrelTipZ + 0.08f, flashS, flashS, flashS * 1.7f, uModel, meshes.sphereMesh)
      GLES20.glUniform1f(uEmissive, 0f)
    }
  }

  private fun drawTorsoAndVest(
    parent: FloatArray,
    appearance: CharacterAppearance,
    isChubby: Boolean,
    isTall: Boolean,
    hipY: Float,
    shoulderY: Float,
    vestLevel: Int,
    uModel: Int,
    uBaseColor: Int,
    uMaterialType: Int,
    uEmissive: Int,
    uMetallic: Int,
    uRoughness: Int,
  ) {
    GLES20.glUniform1i(uMaterialType, MaterialSurface.CLOTH.id)
    GLES20.glUniform1f(uMetallic, 0.02f)
    GLES20.glUniform1f(uRoughness, 0.76f)
    GLES20.glUniform3fv(uBaseColor, 1, appearance.tunicColor, 0)

    if (isChubby) {
      drawPart(parent, 0f, 0.96f, 0.02f, 0.80f, 0.82f, 0.76f, uModel, meshes.kurtaTorsoMesh)
    } else if (isTall) {
      drawPart(parent, 0f, 1.15f, 0f, 0.44f, 0.72f, 0.32f, uModel, meshes.capsuleMesh)
      drawPart(parent, 0f, 1.32f, 0.01f, 0.48f, 0.34f, 0.34f, uModel, meshes.sphereMesh)
    } else {
      drawPart(parent, 0f, 1.07f, 0f, 0.52f, 0.66f, 0.36f, uModel, meshes.capsuleMesh)
      drawPart(parent, 0f, 1.22f, 0.01f, 0.56f, 0.36f, 0.38f, uModel, meshes.sphereMesh)
    }

    if (vestLevel >= 3) {
      val isLv4 = vestLevel >= 4
      val plateColor = if (isLv4) floatArrayOf(0.11f, 0.12f, 0.16f) else floatArrayOf(0.16f, 0.21f, 0.29f)
      val trimColor = if (isLv4) floatArrayOf(1.0f, 0.78f, 0.22f) else floatArrayOf(0.18f, 0.90f, 0.98f)
      val vestY = if (isChubby) 1.10f else shoulderY - 0.18f
      val vx = if (isChubby) 0.86f else if (isTall) 0.50f else 0.58f
      val vz = if (isChubby) 0.82f else if (isTall) 0.38f else 0.44f

      GLES20.glUniform1i(uMaterialType, MaterialSurface.SLATE.id)
      GLES20.glUniform1f(uMetallic, if (isLv4) 0.75f else 0.55f)
      GLES20.glUniform1f(uRoughness, 0.26f)
      GLES20.glUniform3fv(uBaseColor, 1, plateColor, 0)
      drawPart(parent, 0f, vestY, 0.01f, vx, if (isChubby) 0.58f else 0.48f, vz, uModel, meshes.vestMesh)

      // Chest & back ballistic plate + Level 3/4 glowing emblem
      GLES20.glUniform3fv(uBaseColor, 1, plateColor, 0)
      drawPart(parent, 0f, vestY + 0.02f, vz * 0.46f, vx * 0.62f, 0.34f, 0.08f, uModel, meshes.boxMesh)
      drawPart(parent, 0f, vestY + 0.02f, -vz * 0.46f, vx * 0.62f, 0.34f, 0.08f, uModel, meshes.boxMesh)
      if (isLv4) {
        // Extra heavy Titan shoulder pads for Level 4 armor
        drawPart(parent, -vx * 0.54f, shoulderY - 0.01f, 0.01f, 0.16f, 0.14f, 0.22f, uModel, meshes.sphereMesh)
        drawPart(parent, vx * 0.54f, shoulderY - 0.01f, 0.01f, 0.16f, 0.14f, 0.22f, uModel, meshes.sphereMesh)
      }
      GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
      GLES20.glUniform1f(uEmissive, 0.75f)
      GLES20.glUniform3fv(uBaseColor, 1, trimColor, 0)
      drawPart(parent, 0f, vestY + 0.05f, vz * 0.51f, vx * 0.40f, 0.08f, 0.03f, uModel, meshes.boxMesh)
      GLES20.glUniform1f(uEmissive, 0f)
      return
    }

    val vest = appearance.vestColor ?: return
    GLES20.glUniform3fv(uBaseColor, 1, vest, 0)
    GLES20.glUniform1f(uRoughness, 0.68f)

    if (isChubby) {
      drawPart(parent, 0f, 1.11f, 0.02f, 0.82f, 0.58f, 0.79f, uModel, meshes.vestMesh)
      if (appearance.brassButtons) {
        GLES20.glUniform1i(uMaterialType, MaterialSurface.SLATE.id)
        GLES20.glUniform1f(uMetallic, 0.45f)
        GLES20.glUniform1f(uRoughness, 0.28f)
        GLES20.glUniform3fv(uBaseColor, 1, warmButtonColor, 0)
        drawPart(parent, -0.17f, 1.11f, 0.31f, 0.052f, 0.052f, 0.026f, uModel, meshes.sphereMesh)
      }
    } else {
      val vestY = shoulderY - 0.16f
      val vx = if (isTall) 0.47f else 0.55f
      val vz = if (isTall) 0.35f else 0.41f
      drawPart(parent, 0f, vestY, 0.01f, vx, 0.48f, vz, uModel, meshes.vestMesh)
      if (appearance.brassButtons) {
        GLES20.glUniform1i(uMaterialType, MaterialSurface.SLATE.id)
        GLES20.glUniform1f(uMetallic, 0.65f)
        GLES20.glUniform1f(uRoughness, 0.24f)
        GLES20.glUniform3fv(uBaseColor, 1, warmButtonColor, 0)
        drawPart(parent, -vx * 0.22f, vestY - 0.04f, vz * 0.42f, 0.038f, 0.038f, 0.022f, uModel, meshes.sphereMesh)
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
