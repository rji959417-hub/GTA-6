package com.example.game.gl

import android.opengl.GLES20
import android.opengl.Matrix
import com.example.game.state.BirdKind
import com.example.game.state.GameSimulation
import com.example.game.state.WeatherMode
import com.example.game.world.MaterialSurface
import kotlin.math.sin

internal class ExpandedWorldRenderer(
  private val boxMesh: Mesh,
  private val cylinderMesh: Mesh,
  private val sphereMesh: Mesh,
  private val leftWingMesh: Mesh,
  private val rightWingMesh: Mesh,
  private val characterRenderer: CartoonCharacterRenderer,
) {
  private val modelMat = FloatArray(16)
  private val tmpMat = FloatArray(16)
  private val baseMat = FloatArray(16)

  fun drawOpaqueLivingEntities(
    sim: GameSimulation,
    uModel: Int,
    uBaseColor: Int,
    uMaterialType: Int,
    uEmissive: Int,
    uMetallic: Int,
    uRoughness: Int,
    uAlpha: Int,
    nightFactor: Float,
  ) {
    val lw = sim.livingWorld
    GLES20.glUniform1f(uAlpha, 1.0f)

    // 1. Volumetric-style Drifting 3D Sky Clouds
    val stormDarken = 1f - lw.weatherMode.stormFactor * 0.42f + lw.lightningFlash * 0.35f
    val cloudCol =
      floatArrayOf(
        (0.95f * stormDarken).coerceIn(0.32f, 1.0f),
        (0.94f * stormDarken).coerceIn(0.34f, 1.0f),
        (0.98f * stormDarken).coerceIn(0.42f, 1.0f),
      )
    GLES20.glUniform1i(uMaterialType, MaterialSurface.STUCCO.id)
    GLES20.glUniform1f(uEmissive, if (nightFactor > 0.4f) 0.08f else 0.18f)
    GLES20.glUniform1f(uMetallic, 0f)
    GLES20.glUniform1f(uRoughness, 0.95f)
    GLES20.glUniform3fv(uBaseColor, 1, cloudCol, 0)
    for (c in lw.clouds) {
      val bob = sin(sim.worldTimeSec * 0.5f + c.seed) * 0.6f
      drawTransformedBox(c.x, c.y + bob, c.z, c.scaleX, c.scaleY, c.scaleZ, 0f, uModel)
      sphereMesh.draw()
      drawTransformedBox(c.x - c.scaleX * 0.28f, c.y + bob - 0.5f, c.z + 1.2f, c.scaleX * 0.62f, c.scaleY * 0.78f, c.scaleZ * 0.68f, 0f, uModel)
      sphereMesh.draw()
      drawTransformedBox(c.x + c.scaleX * 0.28f, c.y + bob - 0.4f, c.z - 1.1f, c.scaleX * 0.58f, c.scaleY * 0.74f, c.scaleZ * 0.64f, 0f, uModel)
      sphereMesh.draw()
    }
    GLES20.glUniform1f(uEmissive, 0f)

    // 2. Scenic Perimeter Express Train (only if world defines railway waypoints)
    if (sim.world.railwayLoopWaypoints.size >= 2) {
      drawTrain(sim, uModel, uBaseColor, uMaterialType, uEmissive, uMetallic, uRoughness)
    }

    // 3. Patrolling Helicopters & Ambient Sky Cruisers
    drawHelicopters(sim, uModel, uBaseColor, uMaterialType, uEmissive, uMetallic, uRoughness)
    drawAmbientAirplanes(sim, uModel, uBaseColor, uMaterialType, uEmissive, uMetallic, uRoughness)

    // 4. Flapping Seagulls & Mountain Eagles
    drawBirds(sim, uModel, uBaseColor, uMaterialType, uEmissive, uMetallic, uRoughness)
  }

  fun drawBlendedLivingEffects(
    sim: GameSimulation,
    uModel: Int,
    uBaseColor: Int,
    uMaterialType: Int,
    uEmissive: Int,
    uMetallic: Int,
    uRoughness: Int,
    uAlpha: Int,
  ) {
    val lw = sim.livingWorld
    GLES20.glEnable(GLES20.GL_BLEND)
    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    GLES20.glDepthMask(false)

    // 1. Animated Waterfalls (Jharna): Flowing cascades + mist spray + splash foam
    val cascadeCyan = floatArrayOf(0.54f, 0.88f, 0.98f)
    val cascadeFoam = floatArrayOf(0.92f, 0.98f, 1.0f)
    GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
    GLES20.glUniform1f(uMetallic, 0.1f)
    GLES20.glUniform1f(uRoughness, 0.2f)
    for (wf in sim.world.waterfalls) {
      val height = wf.topY - wf.baseY
      val bands = 5
      for (b in 0 until bands) {
        val scroll = ((sim.worldTimeSec * 0.78f + b / bands.toFloat()) % 1f)
        val bandY = wf.topY - scroll * height
        val bandH = (height / bands) * 1.15f
        val isFoam = b % 2 == 0
        GLES20.glUniform3fv(uBaseColor, 1, if (isFoam) cascadeFoam else cascadeCyan, 0)
        GLES20.glUniform1f(uEmissive, if (isFoam) 0.52f else 0.34f)
        GLES20.glUniform1f(uAlpha, if (isFoam) 0.68f else 0.78f)
        drawTransformedBox(wf.x, bandY, wf.z + 0.15f + scroll * 0.55f, wf.width * (0.94f + scroll * 0.08f), bandH, 0.55f, 0f, uModel)
        boxMesh.draw()
      }
      // Billowing mist clouds at plunge basin
      GLES20.glUniform3fv(uBaseColor, 1, cascadeFoam, 0)
      GLES20.glUniform1f(uEmissive, 0.45f)
      for (m in 0 until 4) {
        val phase = (sim.worldTimeSec * 1.6f + m * 1.57f) % 6.28f
        val mx = wf.x + sin(phase) * wf.width * 0.42f
        val my = wf.baseY + 1.0f + ((sim.worldTimeSec * 1.3f + m * 0.4f) % 1.4f)
        val mz = wf.z + 1.6f + (m % 2) * 1.4f
        GLES20.glUniform1f(uAlpha, 0.46f)
        drawTransformedBox(mx, my, mz, 2.8f, 2.0f, 2.8f, 0f, uModel)
        sphereMesh.draw()
      }
    }

    // 2. Expanding Water Ripple Rings on ocean & waterfall lagoons
    GLES20.glUniform3fv(uBaseColor, 1, floatArrayOf(0.84f, 0.96f, 1.0f), 0)
    GLES20.glUniform1f(uEmissive, 0.42f)
    for (rip in lw.waterRipples) {
      val progress = (rip.ageSec / rip.maxAgeSec).coerceIn(0f, 1f)
      val rad = 0.6f + progress * rip.maxRadius
      val alpha = (1f - progress) * 0.45f * rip.intensity
      if (alpha > 0.02f) {
        GLES20.glUniform1f(uAlpha, alpha)
        drawTransformedBox(rip.x, -0.34f, rip.z, rad * 2f, 0.04f, rad * 2f, 0f, uModel)
        cylinderMesh.draw()
      }
    }

    // 3. Live Weather Particles (3D Rain Streaks or Floating Misty Petals)
    if (lw.rainIntensity > 0.04f) {
      val isPetals = lw.weatherMode == WeatherMode.MISTY_PETALS
      val dropCol = if (isPetals) floatArrayOf(0.98f, 0.72f, 0.86f) else floatArrayOf(0.74f, 0.86f, 0.98f)
      GLES20.glUniform3fv(uBaseColor, 1, dropCol, 0)
      GLES20.glUniform1f(uEmissive, if (isPetals) 0.55f else 0.35f)
      GLES20.glUniform1f(uAlpha, (0.38f + lw.rainIntensity * 0.36f).coerceIn(0f, 0.75f))
      val activeCount = (lw.weatherDrops.size * lw.rainIntensity).toInt().coerceIn(8, lw.weatherDrops.size)
      for (i in 0 until activeCount) {
        val d = lw.weatherDrops[i]
        if (isPetals) {
          drawTransformedBox(d.x, d.y, d.z, 0.16f, 0.10f, 0.16f, sim.worldTimeSec * 90f + i * 25f, uModel)
        } else {
          drawTransformedBox(d.x, d.y, d.z, 0.04f, d.length, 0.04f, 0f, uModel)
        }
        boxMesh.draw()
      }
    }

    GLES20.glUniform1f(uAlpha, 1.0f)
    GLES20.glUniform1f(uEmissive, 0f)
    GLES20.glDepthMask(true)
    GLES20.glDisable(GLES20.GL_BLEND)
  }

  private fun drawTrain(
    sim: GameSimulation,
    uModel: Int,
    uBaseColor: Int,
    uMaterialType: Int,
    uEmissive: Int,
    uMetallic: Int,
    uRoughness: Int,
  ) {
    val lw = sim.livingWorld
    val locoRed = floatArrayOf(0.86f, 0.18f, 0.22f)
    val coachTeal = floatArrayOf(0.14f, 0.66f, 0.78f)
    val coachGold = floatArrayOf(0.94f, 0.72f, 0.22f)
    val darkSteel = floatArrayOf(0.16f, 0.17f, 0.21f)
    val windowGlass = floatArrayOf(0.62f, 0.88f, 0.98f)

    for (carIdx in 0 until lw.trainState.carCount) {
      val (cx, cz, yawRad) = lw.trainCarPose(carIdx)
      val yawDeg = Math.toDegrees(yawRad.toDouble()).toFloat()
      Matrix.setIdentityM(baseMat, 0)
      Matrix.translateM(baseMat, 0, cx, 0.32f, cz)
      Matrix.rotateM(baseMat, 0, yawDeg, 0f, 1f, 0f)

      GLES20.glUniform1i(uMaterialType, MaterialSurface.CAR_PAINT.id)
      GLES20.glUniform1f(uEmissive, 0f)
      GLES20.glUniform1f(uMetallic, 0.45f)
      GLES20.glUniform1f(uRoughness, 0.30f)

      // Chassis bogie frame
      GLES20.glUniform3fv(uBaseColor, 1, darkSteel, 0)
      drawChildBox(baseMat, 0f, 0.30f, 0f, 2.35f, 0.45f, 6.4f, uModel)
      boxMesh.draw()

      if (carIdx == 0) {
        // Express Locomotive Engine
        GLES20.glUniform3fv(uBaseColor, 1, locoRed, 0)
        drawChildBox(baseMat, 0f, 1.35f, 0.25f, 2.25f, 1.80f, 5.9f, uModel)
        boxMesh.draw()
        // Cab Roof & Smokestack
        GLES20.glUniform3fv(uBaseColor, 1, darkSteel, 0)
        drawChildBox(baseMat, 0f, 2.40f, -1.35f, 2.35f, 0.35f, 2.5f, uModel)
        boxMesh.draw()
        drawChildBox(baseMat, 0f, 2.55f, 1.95f, 0.48f, 0.72f, 0.48f, uModel)
        cylinderMesh.draw()
        // Glowing Headlight
        GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
        GLES20.glUniform1f(uEmissive, 1.35f)
        GLES20.glUniform3fv(uBaseColor, 1, floatArrayOf(1.0f, 0.92f, 0.68f), 0)
        drawChildBox(baseMat, 0f, 1.25f, 3.24f, 0.56f, 0.42f, 0.14f, uModel)
        sphereMesh.draw()
      } else {
        // Passenger / Cargo Coaches
        val bodyCol = if (carIdx % 2 == 1) coachTeal else coachGold
        GLES20.glUniform3fv(uBaseColor, 1, bodyCol, 0)
        drawChildBox(baseMat, 0f, 1.35f, 0f, 2.22f, 1.72f, 6.1f, uModel)
        boxMesh.draw()
        GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
        GLES20.glUniform1f(uEmissive, 0.35f)
        GLES20.glUniform3fv(uBaseColor, 1, windowGlass, 0)
        drawChildBox(baseMat, 0f, 1.55f, 0f, 2.26f, 0.56f, 5.1f, uModel)
        boxMesh.draw()
      }
      GLES20.glUniform1f(uEmissive, 0f)
    }
  }

  private fun drawHelicopters(
    sim: GameSimulation,
    uModel: Int,
    uBaseColor: Int,
    uMaterialType: Int,
    uEmissive: Int,
    uMetallic: Int,
    uRoughness: Int,
  ) {
    val darkMetal = floatArrayOf(0.18f, 0.20f, 0.24f)
    val glassTint = floatArrayOf(0.42f, 0.80f, 0.96f)
    for (heli in sim.livingWorld.helicopters) {
      val yawDeg = Math.toDegrees(heli.yawRad.toDouble()).toFloat()
      val pitchDeg = Math.toDegrees(heli.pitchRad.toDouble()).toFloat()
      val rollDeg = Math.toDegrees(heli.rollRad.toDouble()).toFloat()

      Matrix.setIdentityM(baseMat, 0)
      Matrix.translateM(baseMat, 0, heli.x, heli.y, heli.z)
      Matrix.rotateM(baseMat, 0, yawDeg, 0f, 1f, 0f)
      Matrix.rotateM(baseMat, 0, pitchDeg, 1f, 0f, 0f)
      Matrix.rotateM(baseMat, 0, rollDeg, 0f, 0f, 1f)

      GLES20.glUniform1i(uMaterialType, MaterialSurface.CAR_PAINT.id)
      GLES20.glUniform1f(uEmissive, 0f)
      GLES20.glUniform1f(uMetallic, 0.55f)
      GLES20.glUniform1f(uRoughness, 0.28f)
      GLES20.glUniform3fv(uBaseColor, 1, heli.bodyColor, 0)
      // Fuselage Cabin Pod
      drawChildBox(baseMat, 0f, 0f, 0.2f, 1.95f, 1.65f, 3.8f, uModel)
      sphereMesh.draw()
      // Tail Boom & Fin
      drawChildBox(baseMat, 0f, 0.12f, -3.2f, 0.36f, 0.36f, 4.4f, uModel)
      boxMesh.draw()
      GLES20.glUniform3fv(uBaseColor, 1, heli.stripeColor, 0)
      drawChildBox(baseMat, 0f, 0.65f, -5.2f, 0.16f, 1.25f, 0.72f, uModel)
      boxMesh.draw()

      // Cockpit Nose Canopy & Skids
      GLES20.glUniform3fv(uBaseColor, 1, glassTint, 0)
      drawChildBox(baseMat, 0f, 0.18f, 1.55f, 1.48f, 0.92f, 1.35f, uModel)
      sphereMesh.draw()
      GLES20.glUniform3fv(uBaseColor, 1, darkMetal, 0)
      drawChildBox(baseMat, -0.88f, -1.05f, 0.15f, 0.10f, 0.10f, 3.2f, uModel)
      boxMesh.draw()
      drawChildBox(baseMat, 0.88f, -1.05f, 0.15f, 0.10f, 0.10f, 3.2f, uModel)
      boxMesh.draw()

      // Spinning Main 4-Blade Rotor
      for (bladeYaw in listOf(heli.rotorAngleDeg, heli.rotorAngleDeg + 90f)) {
        Matrix.setIdentityM(tmpMat, 0)
        Matrix.translateM(tmpMat, 0, 0f, 1.25f, 0f)
        Matrix.rotateM(tmpMat, 0, bladeYaw, 0f, 1f, 0f)
        Matrix.scaleM(tmpMat, 0, 7.6f, 0.06f, 0.24f)
        Matrix.multiplyMM(modelMat, 0, baseMat, 0, tmpMat, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
        boxMesh.draw()
      }
    }
  }

  private fun drawAmbientAirplanes(
    sim: GameSimulation,
    uModel: Int,
    uBaseColor: Int,
    uMaterialType: Int,
    uEmissive: Int,
    uMetallic: Int,
    uRoughness: Int,
  ) {
    for (plane in sim.livingWorld.ambientAirplanes) {
      val yawDeg = Math.toDegrees(plane.yawRad.toDouble()).toFloat()
      val rollDeg = Math.toDegrees(plane.rollRad.toDouble()).toFloat()
      Matrix.setIdentityM(baseMat, 0)
      Matrix.translateM(baseMat, 0, plane.x, plane.y, plane.z)
      Matrix.rotateM(baseMat, 0, yawDeg, 0f, 1f, 0f)
      Matrix.rotateM(baseMat, 0, rollDeg, 0f, 0f, 1f)

      GLES20.glUniform1i(uMaterialType, MaterialSurface.CAR_PAINT.id)
      GLES20.glUniform1f(uEmissive, 0f)
      GLES20.glUniform1f(uMetallic, 0.6f)
      GLES20.glUniform1f(uRoughness, 0.25f)
      GLES20.glUniform3fv(uBaseColor, 1, plane.bodyColor, 0)
      drawChildBox(baseMat, 0f, 0f, 0f, 1.6f, 1.5f, 9.6f, uModel)
      sphereMesh.draw()

      GLES20.glDisable(GLES20.GL_CULL_FACE)
      GLES20.glUniform3fv(uBaseColor, 1, plane.accentColor, 0)
      drawChildBox(baseMat, -0.5f, 0f, 0.3f, 6.2f, 0.6f, 1.4f, uModel)
      leftWingMesh.draw()
      drawChildBox(baseMat, 0.5f, 0f, 0.3f, 6.2f, 0.6f, 1.4f, uModel)
      rightWingMesh.draw()
      GLES20.glEnable(GLES20.GL_CULL_FACE)

      drawChildBox(baseMat, 0f, 1.2f, -3.9f, 0.22f, 2.0f, 1.3f, uModel)
      boxMesh.draw()
    }
  }

  private fun drawBirds(
    sim: GameSimulation,
    uModel: Int,
    uBaseColor: Int,
    uMaterialType: Int,
    uEmissive: Int,
    uMetallic: Int,
    uRoughness: Int,
  ) {
    val gullWhite = floatArrayOf(0.96f, 0.96f, 0.98f)
    val eagleBrown = floatArrayOf(0.36f, 0.24f, 0.16f)
    val beakGold = floatArrayOf(0.98f, 0.74f, 0.18f)

    for (bird in sim.livingWorld.birds) {
      val yawDeg = Math.toDegrees(bird.yawRad.toDouble()).toFloat()
      val bankDeg = Math.toDegrees(bird.bankRad.toDouble()).toFloat()
      val flapDeg = sin(bird.flapPhase) * 34f
      val bodyColor = if (bird.kind == BirdKind.EAGLE) eagleBrown else gullWhite

      Matrix.setIdentityM(baseMat, 0)
      Matrix.translateM(baseMat, 0, bird.x, bird.y, bird.z)
      Matrix.rotateM(baseMat, 0, yawDeg, 0f, 1f, 0f)
      Matrix.rotateM(baseMat, 0, bankDeg, 0f, 0f, 1f)

      GLES20.glUniform1i(uMaterialType, MaterialSurface.CLOTH.id)
      GLES20.glUniform1f(uEmissive, 0f)
      GLES20.glUniform1f(uMetallic, 0.02f)
      GLES20.glUniform1f(uRoughness, 0.7f)
      GLES20.glUniform3fv(uBaseColor, 1, bodyColor, 0)

      // Streamlined bird body
      drawChildBox(baseMat, 0f, 0f, 0f, 0.26f * bird.wingSpan, 0.22f * bird.wingSpan, 0.75f * bird.wingSpan, uModel)
      sphereMesh.draw()

      // Left & Right Flapping Wings
      for (side in listOf(-1f, 1f)) {
        Matrix.setIdentityM(tmpMat, 0)
        Matrix.rotateM(tmpMat, 0, side * flapDeg, 0f, 0f, 1f)
        Matrix.translateM(tmpMat, 0, side * 0.42f * bird.wingSpan, 0.02f, 0f)
        Matrix.scaleM(tmpMat, 0, 0.78f * bird.wingSpan, 0.05f, 0.30f * bird.wingSpan)
        Matrix.multiplyMM(modelMat, 0, baseMat, 0, tmpMat, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
        boxMesh.draw()
      }

      // Golden Beak
      GLES20.glUniform3fv(uBaseColor, 1, beakGold, 0)
      drawChildBox(baseMat, 0f, 0.02f, 0.44f * bird.wingSpan, 0.08f, 0.08f, 0.18f, uModel)
      boxMesh.draw()
    }
  }

  private fun drawTransformedBox(
    cx: Float,
    cy: Float,
    cz: Float,
    sx: Float,
    sy: Float,
    sz: Float,
    yawDeg: Float,
    uModel: Int,
  ) {
    Matrix.setIdentityM(modelMat, 0)
    Matrix.translateM(modelMat, 0, cx, cy, cz)
    if (yawDeg != 0f) Matrix.rotateM(modelMat, 0, yawDeg, 0f, 1f, 0f)
    Matrix.scaleM(modelMat, 0, sx, sy, sz)
    GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
  }

  private fun drawChildBox(
    parent: FloatArray,
    lx: Float,
    ly: Float,
    lz: Float,
    sx: Float,
    sy: Float,
    sz: Float,
    uModel: Int,
  ) {
    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, lx, ly, lz)
    Matrix.scaleM(tmpMat, 0, sx, sy, sz)
    Matrix.multiplyMM(modelMat, 0, parent, 0, tmpMat, 0)
    GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
  }
}
