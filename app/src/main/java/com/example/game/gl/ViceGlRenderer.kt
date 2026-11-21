package com.example.game.gl

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.game.state.BoatRuntimeState
import com.example.game.state.GameSimulation
import com.example.game.state.PedestrianRuntimeState
import com.example.game.state.TimeOfDayPreset
import com.example.game.state.VehicleRuntimeState
import com.example.game.world.MaterialSurface
import com.example.game.world.TreeKind
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class ViceGlRenderer(
  private val sim: GameSimulation,
  private val onHudTick: () -> Unit,
) : GLSurfaceView.Renderer {

  private lateinit var worldShader: ShaderProgram
  private lateinit var shadowShader: ShaderProgram
  private lateinit var skyShader: ShaderProgram
  private lateinit var waterShader: ShaderProgram

  private lateinit var boxMesh: Mesh
  private lateinit var cylinderMesh: Mesh
  private lateinit var sphereMesh: Mesh
  private lateinit var palmFrondMesh: Mesh
  private lateinit var carBodyMesh: Mesh
  private lateinit var boatHullMesh: Mesh
  private lateinit var skyMesh: Mesh
  private lateinit var waterOceanMesh: Mesh

  private var shadowFbo = 0
  private var shadowDepthTex = 0
  private var hasShadowMap = false
  private val shadowSize = 1024

  private var viewWidth = 1280
  private var viewHeight = 720
  private var lastFrameNs = System.nanoTime()
  private var hudAccumSec = 0f

  private val viewMat = FloatArray(16)
  private val projMat = FloatArray(16)
  private val viewProjMat = FloatArray(16)
  private val lightViewMat = FloatArray(16)
  private val lightProjMat = FloatArray(16)
  private val lightViewProjMat = FloatArray(16)
  private val modelMat = FloatArray(16)
  private val tmpMat = FloatArray(16)
  private val mvpMat = FloatArray(16)
  private val skyViewMat = FloatArray(16)

  private val sunDir = FloatArray(3)
  private val sunColor = FloatArray(3)
  private val skyZenith = FloatArray(3)
  private val skyHorizon = FloatArray(3)
  private var nightFactor = 0.15f

  private val playerShirtColor = floatArrayOf(0.16f, 0.80f, 0.78f)
  private val playerPantsColor = floatArrayOf(0.16f, 0.22f, 0.36f)
  private val skinTone = floatArrayOf(0.86f, 0.66f, 0.52f)

  override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
    GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    GLES30.glEnable(GLES30.GL_CULL_FACE)
    GLES30.glCullFace(GLES30.GL_BACK)

    worldShader = ShaderProgram(ViceShaders.WORLD_VERTEX, ViceShaders.WORLD_FRAGMENT)
    shadowShader = ShaderProgram(ViceShaders.SHADOW_VERTEX, ViceShaders.SHADOW_FRAGMENT)
    skyShader = ShaderProgram(ViceShaders.SKY_VERTEX, ViceShaders.SKY_FRAGMENT)
    waterShader = ShaderProgram(ViceShaders.WATER_VERTEX, ViceShaders.WATER_FRAGMENT)

    boxMesh = MeshBuilder.createUnitBox()
    cylinderMesh = MeshBuilder.createCylinder(segments = 16)
    sphereMesh = MeshBuilder.createSphere(rings = 12, sectors = 18, inverted = false)
    palmFrondMesh = MeshBuilder.createPalmFrond()
    carBodyMesh = MeshBuilder.createSportsCarBody()
    boatHullMesh = MeshBuilder.createBoatHull()
    skyMesh = MeshBuilder.createSphere(rings = 18, sectors = 28, inverted = true)
    waterOceanMesh = MeshBuilder.createWaterPlane(size = 440f, grid = 36)

    initShadowFbo()
    lastFrameNs = System.nanoTime()
  }

  private fun initShadowFbo() {
    val fbos = IntArray(1)
    val texs = IntArray(1)
    GLES30.glGenFramebuffers(1, fbos, 0)
    GLES30.glGenTextures(1, texs, 0)
    shadowFbo = fbos[0]
    shadowDepthTex = texs[0]

    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadowDepthTex)
    GLES30.glTexImage2D(
      GLES30.GL_TEXTURE_2D,
      0,
      GLES30.GL_DEPTH_COMPONENT24,
      shadowSize,
      shadowSize,
      0,
      GLES30.GL_DEPTH_COMPONENT,
      GLES30.GL_UNSIGNED_INT,
      null,
    )
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shadowFbo)
    GLES30.glFramebufferTexture2D(
      GLES30.GL_FRAMEBUFFER,
      GLES30.GL_DEPTH_ATTACHMENT,
      GLES30.GL_TEXTURE_2D,
      shadowDepthTex,
      0,
    )
    GLES30.glDrawBuffers(1, intArrayOf(GLES30.GL_NONE), 0)
    GLES30.glReadBuffer(GLES30.GL_NONE)

    val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
    hasShadowMap = (status == GLES30.GL_FRAMEBUFFER_COMPLETE)
    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
  }

  override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
    viewWidth = width.coerceAtLeast(1)
    viewHeight = height.coerceAtLeast(1)
    GLES30.glViewport(0, 0, viewWidth, viewHeight)
  }

  override fun onDrawFrame(gl: GL10?) {
    val now = System.nanoTime()
    val dt = ((now - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
    lastFrameNs = now

    sim.step(dt)
    hudAccumSec += dt
    if (hudAccumSec >= 0.045f) {
      hudAccumSec = 0f
      onHudTick()
    }

    updateAtmosphere()

    val aspect = viewWidth.toFloat() / viewHeight.toFloat()
    Matrix.perspectiveM(projMat, 0, sim.camFovDeg, aspect, 0.25f, 360f)
    Matrix.setLookAtM(
      viewMat,
      0,
      sim.camPos.x,
      sim.camPos.y,
      sim.camPos.z,
      sim.camLookAt.x,
      sim.camLookAt.y,
      sim.camLookAt.z,
      0f,
      1f,
      0f,
    )
    Matrix.multiplyMM(viewProjMat, 0, projMat, 0, viewMat, 0)

    val focusX = if (sim.isPlaying) sim.playerX else 0f
    val focusZ = if (sim.isPlaying) sim.playerZ else 0f
    Matrix.setLookAtM(
      lightViewMat,
      0,
      focusX + sunDir[0] * 60f,
      sunDir[1] * 60f,
      focusZ + sunDir[2] * 60f,
      focusX,
      0f,
      focusZ,
      0f,
      1f,
      0f,
    )
    Matrix.orthoM(lightProjMat, 0, -56f, 56f, -56f, 56f, 5f, 130f)
    Matrix.multiplyMM(lightViewProjMat, 0, lightProjMat, 0, lightViewMat, 0)

    if (hasShadowMap) {
      GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shadowFbo)
      GLES30.glViewport(0, 0, shadowSize, shadowSize)
      GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
      GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
      GLES30.glPolygonOffset(2.0f, 4.0f)

      shadowShader.use()
      val uLightMvp = shadowShader.loc("uLightMvp")
      val uTime = shadowShader.loc("uTime")
      val uWind = shadowShader.loc("uWind")
      GLES30.glUniform1f(uTime, sim.worldTimeSec)

      renderShadowCasters(uLightMvp, uWind)

      GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
      GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    GLES30.glViewport(0, 0, viewWidth, viewHeight)
    GLES30.glClearColor(skyHorizon[0], skyHorizon[1], skyHorizon[2], 1f)
    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

    drawSkyDome()
    drawWorldOpaque()
    drawWater()
    drawGlassWindowsAndWindshields()
  }

  private fun updateAtmosphere() {
    when (sim.timePreset) {
      TimeOfDayPreset.SUNSET -> {
        setVec3(sunDir, -0.72f, 0.34f, -0.60f)
        setVec3(sunColor, 1.00f, 0.68f, 0.44f)
        setVec3(skyZenith, 0.16f, 0.10f, 0.36f)
        setVec3(skyHorizon, 0.96f, 0.43f, 0.38f)
        nightFactor = 0.18f
      }
      TimeOfDayPreset.DAYLIGHT -> {
        setVec3(sunDir, -0.36f, 0.88f, -0.32f)
        setVec3(sunColor, 1.00f, 0.96f, 0.88f)
        setVec3(skyZenith, 0.14f, 0.48f, 0.88f)
        setVec3(skyHorizon, 0.66f, 0.86f, 0.98f)
        nightFactor = 0.0f
      }
      TimeOfDayPreset.NEON_NIGHT -> {
        setVec3(sunDir, 0.42f, 0.52f, -0.74f)
        setVec3(sunColor, 0.34f, 0.44f, 0.72f)
        setVec3(skyZenith, 0.03f, 0.02f, 0.10f)
        setVec3(skyHorizon, 0.25f, 0.09f, 0.30f)
        nightFactor = 0.90f
      }
    }
  }

  private fun drawSkyDome() {
    GLES30.glDepthMask(false)
    GLES30.glDepthFunc(GLES30.GL_LEQUAL)
    skyShader.use()

    System.arraycopy(viewMat, 0, skyViewMat, 0, 16)
    skyViewMat[12] = 0f
    skyViewMat[13] = 0f
    skyViewMat[14] = 0f
    Matrix.multiplyMM(mvpMat, 0, projMat, 0, skyViewMat, 0)

    GLES30.glUniformMatrix4fv(skyShader.loc("uMvp"), 1, false, mvpMat, 0)
    GLES30.glUniform3fv(skyShader.loc("uSunDir"), 1, sunDir, 0)
    GLES30.glUniform3fv(skyShader.loc("uSkyZenith"), 1, skyZenith, 0)
    GLES30.glUniform3fv(skyShader.loc("uSkyHorizon"), 1, skyHorizon, 0)
    GLES30.glUniform3fv(skyShader.loc("uSunColor"), 1, sunColor, 0)
    GLES30.glUniform1f(skyShader.loc("uTime"), sim.worldTimeSec)
    GLES30.glUniform1f(skyShader.loc("uNightFactor"), nightFactor)

    skyMesh.draw()

    GLES30.glDepthFunc(GLES30.GL_LESS)
    GLES30.glDepthMask(true)
  }

  private fun renderShadowCasters(uLightMvp: Int, uWind: Int) {
    GLES30.glUniform1f(uWind, 0f)
    for (box in sim.world.staticBoxes) {
      if (!box.castShadow || box.surface == MaterialSurface.WATER) continue
      setBoxModel(box.cx, box.cy, box.cz, box.sx, box.sy, box.sz, box.yawDeg)
      Matrix.multiplyMM(mvpMat, 0, lightViewProjMat, 0, modelMat, 0)
      GLES30.glUniformMatrix4fv(uLightMvp, 1, false, mvpMat, 0)
      boxMesh.draw()
    }

    for (tree in sim.world.trees) {
      if (tree.kind == TreeKind.PALM) {
        GLES30.glUniform1f(uWind, 0f)
        setBoxModel(tree.x, tree.height * 0.45f, tree.z, 0.42f, tree.height * 0.9f, 0.42f, tree.yawDeg)
        Matrix.multiplyMM(mvpMat, 0, lightViewProjMat, 0, modelMat, 0)
        GLES30.glUniformMatrix4fv(uLightMvp, 1, false, mvpMat, 0)
        cylinderMesh.draw()

        GLES30.glUniform1f(uWind, 1.0f)
        for (i in 0 until 7) {
          val a = tree.yawDeg + i * (360f / 7f)
          Matrix.setIdentityM(modelMat, 0)
          Matrix.translateM(modelMat, 0, tree.x, tree.height * 0.88f, tree.z)
          Matrix.rotateM(modelMat, 0, a, 0f, 1f, 0f)
          Matrix.multiplyMM(mvpMat, 0, lightViewProjMat, 0, modelMat, 0)
          GLES30.glUniformMatrix4fv(uLightMvp, 1, false, mvpMat, 0)
          palmFrondMesh.draw()
        }
      } else {
        GLES30.glUniform1f(uWind, 0f)
        setBoxModel(tree.x, tree.height * 0.28f, tree.z, 0.55f, tree.height * 0.56f, 0.55f, tree.yawDeg)
        Matrix.multiplyMM(mvpMat, 0, lightViewProjMat, 0, modelMat, 0)
        GLES30.glUniformMatrix4fv(uLightMvp, 1, false, mvpMat, 0)
        cylinderMesh.draw()

        GLES30.glUniform1f(uWind, 0.4f)
        setBoxModel(tree.x, tree.height * 0.68f, tree.z, 3.6f, 3.0f, 3.6f, tree.yawDeg)
        Matrix.multiplyMM(mvpMat, 0, lightViewProjMat, 0, modelMat, 0)
        GLES30.glUniformMatrix4fv(uLightMvp, 1, false, mvpMat, 0)
        sphereMesh.draw()
      }
    }

    GLES30.glUniform1f(uWind, 0f)
    for (v in sim.vehicles) {
      Matrix.setIdentityM(modelMat, 0)
      Matrix.translateM(modelMat, 0, v.x, 0.04f, v.z)
      Matrix.rotateM(modelMat, 0, Math.toDegrees(v.yawRad.toDouble()).toFloat(), 0f, 1f, 0f)
      Matrix.multiplyMM(mvpMat, 0, lightViewProjMat, 0, modelMat, 0)
      GLES30.glUniformMatrix4fv(uLightMvp, 1, false, mvpMat, 0)
      carBodyMesh.draw()
    }

    if (sim.activeVehicle == null && sim.activeBoat == null) {
      setBoxModel(
        sim.playerX,
        sim.playerY + 0.9f,
        sim.playerZ,
        0.6f,
        1.7f,
        0.5f,
        Math.toDegrees(sim.playerYawRad.toDouble()).toFloat(),
      )
      Matrix.multiplyMM(mvpMat, 0, lightViewProjMat, 0, modelMat, 0)
      GLES30.glUniformMatrix4fv(uLightMvp, 1, false, mvpMat, 0)
      boxMesh.draw()
    }
  }

  private fun drawWorldOpaque() {
    worldShader.use()
    bindCommonWorldUniforms()

    val uModel = worldShader.loc("uModel")
    val uBaseColor = worldShader.loc("uBaseColor")
    val uMaterialType = worldShader.loc("uMaterialType")
    val uEmissive = worldShader.loc("uEmissive")
    val uMetallic = worldShader.loc("uMetallic")
    val uRoughness = worldShader.loc("uRoughness")
    val uAlpha = worldShader.loc("uAlpha")
    val uWind = worldShader.loc("uWind")

    GLES30.glUniform1f(uAlpha, 1.0f)
    GLES30.glUniform1f(uWind, 0f)

    // 1. Static Boxes
    for (box in sim.world.staticBoxes) {
      if (box.surface == MaterialSurface.WATER) continue
      setBoxModel(box.cx, box.cy, box.cz, box.sx, box.sy, box.sz, box.yawDeg)
      GLES30.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      GLES30.glUniform3fv(uBaseColor, 1, box.color, 0)
      GLES30.glUniform1i(uMaterialType, box.surface.id)
      GLES30.glUniform1f(uEmissive, box.emissive)
      GLES30.glUniform1f(uMetallic, box.metallic)
      GLES30.glUniform1f(uRoughness, box.roughness)
      boxMesh.draw()
    }

    // 2. Palm Trees & Tropical Shade Trees
    val trunkColor = floatArrayOf(0.46f, 0.34f, 0.24f)
    val palmGreen = floatArrayOf(0.18f, 0.56f, 0.24f)
    val shadeGreen1 = floatArrayOf(0.16f, 0.50f, 0.22f)
    val shadeGreen2 = floatArrayOf(0.22f, 0.58f, 0.26f)

    for (tree in sim.world.trees) {
      if (tree.kind == TreeKind.PALM) {
        GLES30.glUniform1f(uWind, 0f)
        GLES30.glUniform1i(uMaterialType, MaterialSurface.WOOD.id)
        GLES30.glUniform1f(uEmissive, 0f)
        GLES30.glUniform1f(uMetallic, 0f)
        GLES30.glUniform1f(uRoughness, 0.85f)
        GLES30.glUniform3fv(uBaseColor, 1, trunkColor, 0)

        val segH = tree.height * 0.24f
        val leanDir = Math.toRadians(tree.yawDeg.toDouble()).toFloat()
        var curX = tree.x
        var curZ = tree.z
        var curY = 0f
        for (s in 0 until 4) {
          val radius = 0.42f - s * 0.055f
          val lean = s * 0.20f
          curX += sin(leanDir) * lean * 0.35f
          curZ += cos(leanDir) * lean * 0.35f
          Matrix.setIdentityM(modelMat, 0)
          Matrix.translateM(modelMat, 0, curX, curY + segH * 0.5f, curZ)
          Matrix.rotateM(modelMat, 0, tree.yawDeg, 0f, 1f, 0f)
          Matrix.rotateM(modelMat, 0, s * 2.5f, 1f, 0f, 0f)
          Matrix.scaleM(modelMat, 0, radius, segH * 1.05f, radius)
          GLES30.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
          cylinderMesh.draw()
          curY += segH * 0.96f
        }

        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glUniform1i(uMaterialType, MaterialSurface.FOLIAGE.id)
        GLES30.glUniform3fv(uBaseColor, 1, palmGreen, 0)
        GLES30.glUniform1f(uRoughness, 0.62f)
        GLES30.glUniform1f(uWind, 1.0f)

        for (i in 0 until 8) {
          val frondYaw = tree.yawDeg + i * 45f
          val scale = 0.85f + (i % 2) * 0.22f
          Matrix.setIdentityM(modelMat, 0)
          Matrix.translateM(modelMat, 0, curX, curY, curZ)
          Matrix.rotateM(modelMat, 0, frondYaw, 0f, 1f, 0f)
          Matrix.rotateM(modelMat, 0, if (i % 2 == 0) -14f else 6f, 1f, 0f, 0f)
          Matrix.scaleM(modelMat, 0, scale, scale, scale)
          GLES30.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
          palmFrondMesh.draw()
        }
        GLES30.glEnable(GLES30.GL_CULL_FACE)
      } else {
        GLES30.glUniform1f(uWind, 0f)
        GLES30.glUniform1i(uMaterialType, MaterialSurface.WOOD.id)
        GLES30.glUniform1f(uEmissive, 0f)
        GLES30.glUniform1f(uMetallic, 0f)
        GLES30.glUniform1f(uRoughness, 0.85f)
        GLES30.glUniform3fv(uBaseColor, 1, trunkColor, 0)

        setBoxModel(tree.x, tree.height * 0.26f, tree.z, 0.56f, tree.height * 0.54f, 0.56f, tree.yawDeg)
        GLES30.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
        cylinderMesh.draw()

        GLES30.glUniform1i(uMaterialType, MaterialSurface.FOLIAGE.id)
        GLES30.glUniform1f(uRoughness, 0.70f)
        GLES30.glUniform1f(uWind, 0.45f)

        val crownY = tree.height * 0.64f
        val puffs =
          arrayOf(
            floatArrayOf(0f, 0.35f, 0f, 3.6f, 2.8f, 3.6f),
            floatArrayOf(-1.1f, -0.15f, 0.7f, 2.5f, 2.2f, 2.5f),
            floatArrayOf(1.1f, -0.10f, -0.6f, 2.6f, 2.2f, 2.6f),
          )
        puffs.forEachIndexed { idx, p ->
          GLES30.glUniform3fv(uBaseColor, 1, if (idx == 0) shadeGreen1 else shadeGreen2, 0)
          setBoxModel(tree.x + p[0], crownY + p[1], tree.z + p[2], p[3], p[4], p[5], tree.yawDeg)
          GLES30.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
          sphereMesh.draw()
        }
      }
    }

    // 3. Streetlights
    GLES30.glUniform1f(uWind, 0f)
    val poleColor = floatArrayOf(0.18f, 0.20f, 0.26f)
    for (sl in sim.world.streetLights) {
      GLES30.glUniform1i(uMaterialType, MaterialSurface.SLATE.id)
      GLES30.glUniform1f(uEmissive, 0f)
      GLES30.glUniform1f(uMetallic, 0.6f)
      GLES30.glUniform1f(uRoughness, 0.35f)
      GLES30.glUniform3fv(uBaseColor, 1, poleColor, 0)

      setBoxModel(sl.x, 2.75f, sl.z, 0.22f, 5.5f, 0.22f, sl.yawDeg)
      GLES30.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      cylinderMesh.draw()

      val rad = Math.toRadians(sl.yawDeg.toDouble()).toFloat()
      val armX = sl.x + sin(rad) * 0.75f
      val armZ = sl.z + cos(rad) * 0.75f
      setBoxModel(armX, 5.45f, armZ, 0.24f, 0.16f, 1.5f, sl.yawDeg)
      GLES30.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      boxMesh.draw()

      val lampX = sl.x + sin(rad) * 1.35f
      val lampZ = sl.z + cos(rad) * 1.35f
      GLES30.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
      GLES30.glUniform1f(uEmissive, 0.45f + nightFactor * 0.65f)
      GLES30.glUniform3fv(uBaseColor, 1, sl.neonColor, 0)
      setBoxModel(lampX, 5.34f, lampZ, 0.48f, 0.16f, 0.52f, sl.yawDeg)
      GLES30.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      boxMesh.draw()
    }

    // 4. Open-Top Sports Cars (Player + Autonomous AI Traffic Cars, with Visible Seated Drivers!)
    for (car in sim.vehicles) {
      drawOpenTopVehicle(car, uModel, uBaseColor, uMaterialType, uEmissive, uMetallic, uRoughness)
    }

    // 5. Speedboats (Pier Boats + Autonomous AI Ocean Cruisers, with Visible Seated Captains!)
    for (boat in sim.boats) {
      drawSpeedboat(boat, uModel, uBaseColor, uMaterialType, uEmissive, uMetallic, uRoughness)
    }

    // 6. Strolling AI Pedestrians
    for (ped in sim.pedestrians) {
      drawWalkingPerson(
        x = ped.x,
        y = 0.04f,
        z = ped.z,
        yawRad = ped.yawRad,
        walkCycle = ped.walkCycle,
        speedNorm = 0.55f,
        isSprinting = false,
        isSwimming = false,
        shirtColor = ped.shirtColor,
        pantsColor = ped.pantsColor,
        uModel = uModel,
        uBaseColor = uBaseColor,
        uMaterialType = uMaterialType,
        uEmissive = uEmissive,
        uMetallic = uMetallic,
        uRoughness = uRoughness,
      )
    }

    // 7. Ejected / Knocked-Down Carjacked NPCs
    for (npc in sim.ejectedNpcs) {
      drawWalkingPerson(
        x = npc.x,
        y = npc.y,
        z = npc.z,
        yawRad = npc.yawRad,
        walkCycle = npc.walkCycle,
        speedNorm = if (npc.fallProgress > 0.1f) 0.15f else 0.55f,
        isSprinting = false,
        isSwimming = npc.inWater,
        armReachNorm = 0f,
        fallProgress = npc.fallProgress,
        shirtColor = npc.shirtColor,
        pantsColor = npc.pantsColor,
        uModel = uModel,
        uBaseColor = uBaseColor,
        uMaterialType = uMaterialType,
        uEmissive = uEmissive,
        uMetallic = uMetallic,
        uRoughness = uRoughness,
      )
    }

    // 8. Player Character (on foot, swimming, or carjacking at door)
    if (sim.activeVehicle == null && sim.activeBoat == null) {
      drawWalkingPerson(
        x = sim.playerX,
        y = sim.playerY,
        z = sim.playerZ,
        yawRad = sim.playerYawRad,
        walkCycle = sim.playerWalkCycle,
        speedNorm = sim.playerSpeedNorm,
        isSprinting = sim.input.sprintHeld && sim.playerSpeedNorm > 0.5f,
        isSwimming = sim.isSwimming,
        armReachNorm = sim.playerArmReachNorm,
        fallProgress = 0f,
        shirtColor = playerShirtColor,
        pantsColor = playerPantsColor,
        uModel = uModel,
        uBaseColor = uBaseColor,
        uMaterialType = uMaterialType,
        uEmissive = uEmissive,
        uMetallic = uMetallic,
        uRoughness = uRoughness,
      )
    }
  }

  private fun drawOpenTopVehicle(
    car: VehicleRuntimeState,
    uModel: Int,
    uBaseColor: Int,
    uMaterialType: Int,
    uEmissive: Int,
    uMetallic: Int,
    uRoughness: Int,
  ) {
    val yawDeg = Math.toDegrees(car.yawRad.toDouble()).toFloat()
    val pitchDeg = Math.toDegrees(car.pitchRad.toDouble()).toFloat()
    val rollDeg = Math.toDegrees(car.rollRad.toDouble()).toFloat()

    val base = FloatArray(16)
    Matrix.setIdentityM(base, 0)
    Matrix.translateM(base, 0, car.x, 0.04f, car.z)
    Matrix.rotateM(base, 0, yawDeg, 0f, 1f, 0f)
    Matrix.rotateM(base, 0, pitchDeg, 1f, 0f, 0f)
    Matrix.rotateM(base, 0, rollDeg, 0f, 0f, 1f)

    // Lower sculpted aerodynamic body shell
    GLES30.glUniformMatrix4fv(uModel, 1, false, base, 0)
    GLES30.glUniform3fv(uBaseColor, 1, car.bodyColor, 0)
    GLES30.glUniform1i(uMaterialType, MaterialSurface.CAR_PAINT.id)
    GLES30.glUniform1f(uEmissive, 0f)
    GLES30.glUniform1f(uMetallic, 0.88f)
    GLES30.glUniform1f(uRoughness, 0.14f)
    carBodyMesh.draw()

    // Open-Top Targa Cockpit Interior (recessed seats, dashboard, steering wheel, roll-bar!)
    val interiorDark = floatArrayOf(0.11f, 0.11f, 0.14f)
    val seatLeather = floatArrayOf(0.32f, 0.16f, 0.14f)
    GLES30.glUniform1i(uMaterialType, MaterialSurface.SLATE.id)
    GLES30.glUniform1f(uMetallic, 0.25f)
    GLES30.glUniform1f(uRoughness, 0.55f)
    // Cockpit deck tub
    GLES30.glUniform3fv(uBaseColor, 1, interiorDark, 0)
    drawChildBox(base, 0f, 0.68f, -0.15f, 1.48f, 0.14f, 1.50f, uModel)
    boxMesh.draw()
    // Dashboard cowl & Steering Wheel (on right/left side - let's put driver at x = 0.34f or -0.34f)
    drawChildBox(base, 0f, 0.96f, 0.56f, 1.50f, 0.26f, 0.34f, uModel)
    boxMesh.draw()

    // Steering Wheel Ring angled toward driver at x = -0.34f
    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, -0.34f, 1.06f, 0.34f)
    Matrix.rotateM(tmpMat, 0, -32f, 1f, 0f, 0f)
    Matrix.rotateM(tmpMat, 0, Math.toDegrees(car.steerAngle.toDouble()).toFloat() * 1.8f, 0f, 1f, 0f)
    Matrix.scaleM(tmpMat, 0, 0.34f, 0.06f, 0.34f)
    Matrix.multiplyMM(modelMat, 0, base, 0, tmpMat, 0)
    GLES30.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
    cylinderMesh.draw()

    // Twin Sport Bucket Seats
    GLES30.glUniform3fv(uBaseColor, 1, seatLeather, 0)
    for (sx in listOf(-0.36f, 0.36f)) {
      drawChildBox(base, sx, 0.80f, -0.18f, 0.46f, 0.16f, 0.52f, uModel)
      boxMesh.draw()
      drawChildBox(base, sx, 1.12f, -0.44f, 0.44f, 0.58f, 0.14f, uModel)
      boxMesh.draw()
    }

    // Sleek Rear Targa Roll Hoop / Pillars (leaves top & sides wide open so driver is 100% visible!)
    GLES30.glUniform3fv(uBaseColor, 1, car.bodyColor, 0)
    GLES30.glUniform1i(uMaterialType, MaterialSurface.CAR_PAINT.id)
    GLES30.glUniform1f(uMetallic, 0.88f)
    GLES30.glUniform1f(uRoughness, 0.14f)
    drawChildBox(base, -0.72f, 1.24f, -0.78f, 0.12f, 0.62f, 0.36f, uModel)
    boxMesh.draw()
    drawChildBox(base, 0.72f, 1.24f, -0.78f, 0.12f, 0.62f, 0.36f, uModel)
    boxMesh.draw()
    drawChildBox(base, 0f, 1.52f, -0.82f, 1.56f, 0.10f, 0.28f, uModel)
    boxMesh.draw()

    // Front Windshield A-Pillars
    GLES30.glUniform3fv(uBaseColor, 1, car.trimColor, 0)
    drawChildBox(base, -0.74f, 1.20f, 0.58f, 0.08f, 0.54f, 0.10f, uModel)
    boxMesh.draw()
    drawChildBox(base, 0.74f, 1.20f, 0.58f, 0.08f, 0.54f, 0.10f, uModel)
    boxMesh.draw()
    drawChildBox(base, 0f, 1.46f, 0.48f, 1.56f, 0.07f, 0.10f, uModel)
    boxMesh.draw()

    // Rear GT Wing Spoiler & Front Carbon Splitter
    GLES30.glUniform1f(uMetallic, 0.35f)
    GLES30.glUniform1f(uRoughness, 0.35f)
    drawChildBox(base, 0f, 1.18f, -1.96f, 1.84f, 0.08f, 0.36f, uModel)
    boxMesh.draw()
    drawChildBox(base, -0.64f, 1.04f, -1.96f, 0.10f, 0.24f, 0.24f, uModel)
    boxMesh.draw()
    drawChildBox(base, 0.64f, 1.04f, -1.96f, 0.10f, 0.24f, 0.24f, uModel)
    boxMesh.draw()
    drawChildBox(base, 0f, 0.20f, 2.12f, 1.88f, 0.08f, 0.24f, uModel)
    boxMesh.draw()

    // Visible Seated Driver inside the Cockpit!
    val isPlayerInside = (sim.activeVehicle?.id == car.id)
    if (isPlayerInside || car.isAiTraffic) {
      val shirt = if (isPlayerInside) playerShirtColor else car.driverShirtColor
      drawSeatedDriver(
        parent = base,
        seatX = -0.34f,
        seatY = 0.86f,
        seatZ = -0.14f,
        shirtColor = shirt,
        uModel = uModel,
        uBaseColor = uBaseColor,
        uMaterialType = uMaterialType,
        uEmissive = uEmissive,
        uMetallic = uMetallic,
        uRoughness = uRoughness,
      )
    }

    // Glowing Twin Headlights (+Z front)
    val headGlow =
      if (isPlayerInside || car.isAiTraffic || nightFactor > 0.15f) 0.95f else 0.30f
    GLES30.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
    GLES30.glUniform1f(uEmissive, headGlow)
    GLES30.glUniform3fv(uBaseColor, 1, floatArrayOf(0.84f, 0.96f, 1.0f), 0)
    drawChildBox(base, -0.66f, 0.48f, 2.09f, 0.36f, 0.13f, 0.08f, uModel)
    boxMesh.draw()
    drawChildBox(base, 0.66f, 0.48f, 2.09f, 0.36f, 0.13f, 0.08f, uModel)
    boxMesh.draw()

    // Rear Tail-Light Bar
    val brakeGlow = if (car.isBraking) 1.45f else 0.60f
    GLES30.glUniform1f(uEmissive, brakeGlow)
    GLES30.glUniform3fv(uBaseColor, 1, floatArrayOf(1.0f, 0.12f, 0.20f), 0)
    drawChildBox(base, 0f, 0.68f, -2.13f, 1.66f, 0.14f, 0.07f, uModel)
    boxMesh.draw()

    // Underglow at Sunset / Night
    if (nightFactor > 0.1f) {
      GLES30.glUniform1f(uEmissive, 0.75f)
      GLES30.glUniform3fv(uBaseColor, 1, car.bodyColor, 0)
      drawChildBox(base, 0f, 0.12f, 0f, 1.62f, 0.04f, 3.2f, uModel)
      boxMesh.draw()
    }

    // 4 Rotating Wheels
    val wheelPositions =
      arrayOf(
        floatArrayOf(-0.92f, 0.34f, 1.32f, 1f),
        floatArrayOf(0.92f, 0.34f, 1.32f, 1f),
        floatArrayOf(-0.92f, 0.34f, -1.30f, 0f),
        floatArrayOf(0.92f, 0.34f, -1.30f, 0f),
      )
    val spinDeg = Math.toDegrees(car.wheelSpinRad.toDouble()).toFloat()
    val steerDeg = Math.toDegrees(car.steerAngle.toDouble()).toFloat()
    val tireColor = floatArrayOf(0.08f, 0.08f, 0.10f)
    val rimColor = floatArrayOf(0.80f, 0.84f, 0.90f)

    for (wp in wheelPositions) {
      val steer = if (wp[3] > 0.5f) steerDeg else 0f
      Matrix.setIdentityM(tmpMat, 0)
      Matrix.translateM(tmpMat, 0, car.x, 0.04f, car.z)
      Matrix.rotateM(tmpMat, 0, yawDeg, 0f, 1f, 0f)
      Matrix.translateM(tmpMat, 0, wp[0], wp[1], wp[2])
      Matrix.rotateM(tmpMat, 0, steer, 0f, 1f, 0f)
      Matrix.rotateM(tmpMat, 0, spinDeg, 1f, 0f, 0f)
      Matrix.rotateM(tmpMat, 0, 90f, 0f, 0f, 1f)

      System.arraycopy(tmpMat, 0, modelMat, 0, 16)
      Matrix.scaleM(modelMat, 0, 0.72f, 0.28f, 0.72f)
      GLES30.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      GLES30.glUniform1i(uMaterialType, MaterialSurface.SLATE.id)
      GLES30.glUniform1f(uEmissive, 0f)
      GLES30.glUniform1f(uMetallic, 0.05f)
      GLES30.glUniform1f(uRoughness, 0.85f)
      GLES30.glUniform3fv(uBaseColor, 1, tireColor, 0)
      cylinderMesh.draw()

      System.arraycopy(tmpMat, 0, modelMat, 0, 16)
      Matrix.scaleM(modelMat, 0, 0.44f, 0.31f, 0.44f)
      GLES30.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      GLES30.glUniform1f(uMetallic, 0.92f)
      GLES30.glUniform1f(uRoughness, 0.18f)
      GLES30.glUniform3fv(uBaseColor, 1, rimColor, 0)
      cylinderMesh.draw()
    }
  }

  private fun drawSpeedboat(
    boat: BoatRuntimeState,
    uModel: Int,
    uBaseColor: Int,
    uMaterialType: Int,
    uEmissive: Int,
    uMetallic: Int,
    uRoughness: Int,
  ) {
    val yawDeg = Math.toDegrees(boat.yawRad.toDouble()).toFloat()
    val pitchDeg = Math.toDegrees(boat.pitchRad.toDouble()).toFloat()
    val rollDeg = Math.toDegrees(boat.rollRad.toDouble()).toFloat()

    val base = FloatArray(16)
    Matrix.setIdentityM(base, 0)
    Matrix.translateM(base, 0, boat.x, -0.34f + boat.bobY, boat.z)
    Matrix.rotateM(base, 0, yawDeg, 0f, 1f, 0f)
    Matrix.rotateM(base, 0, pitchDeg, 1f, 0f, 0f)
    Matrix.rotateM(base, 0, rollDeg, 0f, 0f, 1f)

    // V-hull fiberglass shell
    GLES30.glDisable(GLES30.GL_CULL_FACE)
    GLES30.glUniformMatrix4fv(uModel, 1, false, base, 0)
    GLES30.glUniform3fv(uBaseColor, 1, boat.hullColor, 0)
    GLES30.glUniform1i(uMaterialType, MaterialSurface.CAR_PAINT.id)
    GLES30.glUniform1f(uEmissive, 0f)
    GLES30.glUniform1f(uMetallic, 0.65f)
    GLES30.glUniform1f(uRoughness, 0.18f)
    boatHullMesh.draw()
    GLES30.glEnable(GLES30.GL_CULL_FACE)

    // Boat Cockpit Deck & Racing Accent Trim
    GLES30.glUniform3fv(uBaseColor, 1, floatArrayOf(0.84f, 0.78f, 0.70f), 0)
    GLES30.glUniform1i(uMaterialType, MaterialSurface.WOOD.id)
    drawChildBox(base, 0f, 0.36f, -0.8f, 1.66f, 0.14f, 2.9f, uModel)
    boxMesh.draw()

    GLES30.glUniform3fv(uBaseColor, 1, boat.accentColor, 0)
    GLES30.glUniform1i(uMaterialType, MaterialSurface.CAR_PAINT.id)
    drawChildBox(base, 0f, 0.74f, 0.35f, 1.54f, 0.18f, 0.45f, uModel) // Helm console
    boxMesh.draw()
    drawChildBox(base, 0f, 0.88f, 1.45f, 0.56f, 0.06f, 1.85f, uModel) // Bow stripe
    boxMesh.draw()

    // Captain's Seat & Helm Wheel
    GLES30.glUniform3fv(uBaseColor, 1, floatArrayOf(0.92f, 0.90f, 0.88f), 0)
    drawChildBox(base, 0f, 0.52f, -0.45f, 0.68f, 0.20f, 0.56f, uModel)
    boxMesh.draw()
    drawChildBox(base, 0f, 0.86f, -0.72f, 0.64f, 0.54f, 0.14f, uModel)
    boxMesh.draw()

    GLES30.glUniform3fv(uBaseColor, 1, floatArrayOf(0.14f, 0.14f, 0.18f), 0)
    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, 0f, 0.96f, 0.08f)
    Matrix.rotateM(tmpMat, 0, -35f, 1f, 0f, 0f)
    Matrix.scaleM(tmpMat, 0, 0.36f, 0.06f, 0.36f)
    Matrix.multiplyMM(modelMat, 0, base, 0, tmpMat, 0)
    GLES30.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
    cylinderMesh.draw()

    // Twin Outboard Motors at Stern
    for (mx in listOf(-0.34f, 0.34f)) {
      drawChildBox(base, mx, 0.56f, -2.56f, 0.34f, 0.68f, 0.44f, uModel)
      boxMesh.draw()
    }

    // Glowing Bow & Accent Neon Lights
    GLES30.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
    GLES30.glUniform1f(uEmissive, 0.85f)
    GLES30.glUniform3fv(uBaseColor, 1, boat.accentColor, 0)
    drawChildBox(base, 0f, 0.80f, 2.45f, 0.24f, 0.14f, 0.28f, uModel)
    boxMesh.draw()

    // Water Wake Foam Trail behind moving boats!
    if (boat.wakeIntensity > 0.1f) {
      GLES30.glUniform1f(uEmissive, 0.45f)
      GLES30.glUniform3fv(uBaseColor, 1, floatArrayOf(0.86f, 0.96f, 1.0f), 0)
      val wakeLen = 2.5f + boat.wakeIntensity * 6.5f
      drawChildBox(base, -0.65f, 0.02f, -2.6f - wakeLen * 0.5f, 0.48f, 0.04f, wakeLen, uModel)
      boxMesh.draw()
      drawChildBox(base, 0.65f, 0.02f, -2.6f - wakeLen * 0.5f, 0.48f, 0.04f, wakeLen, uModel)
      boxMesh.draw()
    }

    // Visible Seated Captain (Player or AI!)
    val isPlayerCaptain = (sim.activeBoat?.id == boat.id)
    if (isPlayerCaptain || boat.isAiCruise) {
      val shirt = if (isPlayerCaptain) playerShirtColor else boat.captainShirtColor
      drawSeatedDriver(
        parent = base,
        seatX = 0f,
        seatY = 0.60f,
        seatZ = -0.44f,
        shirtColor = shirt,
        uModel = uModel,
        uBaseColor = uBaseColor,
        uMaterialType = uMaterialType,
        uEmissive = uEmissive,
        uMetallic = uMetallic,
        uRoughness = uRoughness,
      )
    }
  }

  private fun drawSeatedDriver(
    parent: FloatArray,
    seatX: Float,
    seatY: Float,
    seatZ: Float,
    shirtColor: FloatArray,
    uModel: Int,
    uBaseColor: Int,
    uMaterialType: Int,
    uEmissive: Int,
    uMetallic: Int,
    uRoughness: Int,
  ) {
    GLES30.glUniform1i(uMaterialType, MaterialSurface.STUCCO.id)
    GLES30.glUniform1f(uEmissive, 0f)
    GLES30.glUniform1f(uMetallic, 0.05f)
    GLES30.glUniform1f(uRoughness, 0.72f)

    // Seated Torso
    GLES30.glUniform3fv(uBaseColor, 1, shirtColor, 0)
    drawChildBox(parent, seatX, seatY + 0.28f, seatZ, 0.42f, 0.50f, 0.24f, uModel)
    boxMesh.draw()

    // Arms reaching forward toward steering wheel
    GLES30.glUniform3fv(uBaseColor, 1, skinTone, 0)
    for (side in listOf(-0.26f, 0.26f)) {
      Matrix.setIdentityM(tmpMat, 0)
      Matrix.translateM(tmpMat, 0, seatX + side, seatY + 0.42f, seatZ + 0.20f)
      Matrix.rotateM(tmpMat, 0, -68f, 1f, 0f, 0f)
      Matrix.scaleM(tmpMat, 0, 0.12f, 0.44f, 0.12f)
      Matrix.multiplyMM(modelMat, 0, parent, 0, tmpMat, 0)
      GLES30.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      boxMesh.draw()
    }

    // Head
    drawChildBox(parent, seatX, seatY + 0.68f, seatZ + 0.02f, 0.28f, 0.30f, 0.28f, uModel)
    sphereMesh.draw()

    // Hair & Sunglasses
    GLES30.glUniform3fv(uBaseColor, 1, floatArrayOf(0.12f, 0.09f, 0.08f), 0)
    drawChildBox(parent, seatX, seatY + 0.78f, seatZ, 0.29f, 0.15f, 0.29f, uModel)
    sphereMesh.draw()
    GLES30.glUniform1f(uMetallic, 0.85f)
    GLES30.glUniform1f(uRoughness, 0.1f)
    drawChildBox(parent, seatX, seatY + 0.70f, seatZ + 0.14f, 0.22f, 0.06f, 0.06f, uModel)
    boxMesh.draw()
  }

  private fun drawWalkingPerson(
    x: Float,
    y: Float,
    z: Float,
    yawRad: Float,
    walkCycle: Float,
    speedNorm: Float,
    isSprinting: Boolean,
    isSwimming: Boolean,
    armReachNorm: Float = 0f,
    fallProgress: Float = 0f,
    shirtColor: FloatArray,
    pantsColor: FloatArray,
    uModel: Int,
    uBaseColor: Int,
    uMaterialType: Int,
    uEmissive: Int,
    uMetallic: Int,
    uRoughness: Int,
  ) {
    val yawDeg = Math.toDegrees(yawRad.toDouble()).toFloat()
    val swing = sin(walkCycle) * (0.58f * speedNorm)
    val swingDeg = Math.toDegrees(swing.toDouble()).toFloat()
    val bounceY = if (isSwimming || fallProgress > 0.1f) 0f else abs(sin(walkCycle)) * 0.06f * speedNorm
    val bodyLean =
      when {
        fallProgress > 0.01f -> -fallProgress * 86f // Knocked backward onto ground!
        isSwimming -> 52f
        isSprinting -> 11f
        armReachNorm > 0.1f -> 14f * armReachNorm
        else -> 0f
      }
    val fallDropY = fallProgress * 0.22f

    val root = FloatArray(16)
    Matrix.setIdentityM(root, 0)
    Matrix.translateM(root, 0, x, y + bounceY + fallDropY, z)
    Matrix.rotateM(root, 0, yawDeg, 0f, 1f, 0f)
    Matrix.rotateM(root, 0, bodyLean, 1f, 0f, 0f)

    GLES30.glUniform1i(uMaterialType, MaterialSurface.STUCCO.id)
    GLES30.glUniform1f(uEmissive, 0f)
    GLES30.glUniform1f(uMetallic, 0.05f)
    GLES30.glUniform1f(uRoughness, 0.72f)

    // Legs
    GLES30.glUniform3fv(uBaseColor, 1, pantsColor, 0)
    drawLimb(root, -0.13f, 0.76f, 0f, swingDeg, 0.18f, 0.74f, 0.20f, uModel)
    boxMesh.draw()
    drawLimb(root, 0.13f, 0.76f, 0f, -swingDeg, 0.18f, 0.74f, 0.20f, uModel)
    boxMesh.draw()

    // Sneakers
    GLES30.glUniform3fv(uBaseColor, 1, floatArrayOf(0.95f, 0.95f, 0.97f), 0)
    drawLimb(root, -0.13f, 0.76f, 0f, swingDeg, 0.20f, 0.14f, 0.28f, uModel, footOffset = -0.68f)
    boxMesh.draw()
    drawLimb(root, 0.13f, 0.76f, 0f, -swingDeg, 0.20f, 0.14f, 0.28f, uModel, footOffset = -0.68f)
    boxMesh.draw()

    // Torso
    GLES30.glUniform3fv(uBaseColor, 1, shirtColor, 0)
    drawChildBox(root, 0f, 1.08f, 0f, 0.48f, 0.62f, 0.28f, uModel)
    boxMesh.draw()

    // Arms (both arms reach straight forward during carjacking grab, or splay when falling!)
    val leftArmAngle: Float
    val rightArmAngle: Float
    when {
      armReachNorm > 0.05f -> {
        val reach = -85f * armReachNorm
        leftArmAngle = reach
        rightArmAngle = reach
      }
      fallProgress > 0.2f -> {
        leftArmAngle = -125f * fallProgress
        rightArmAngle = -105f * fallProgress
      }
      isSwimming -> {
        leftArmAngle = swingDeg * 1.6f
        rightArmAngle = -swingDeg * 1.6f
      }
      else -> {
        leftArmAngle = -swingDeg * 0.85f
        rightArmAngle = swingDeg * 0.85f
      }
    }
    GLES30.glUniform3fv(uBaseColor, 1, skinTone, 0)
    drawLimb(root, -0.32f, 1.32f, 0f, leftArmAngle, 0.14f, 0.56f, 0.15f, uModel)
    boxMesh.draw()
    drawLimb(root, 0.32f, 1.32f, 0f, rightArmAngle, 0.14f, 0.56f, 0.15f, uModel)
    boxMesh.draw()

    // Head
    drawChildBox(root, 0f, 1.56f, 0f, 0.30f, 0.32f, 0.30f, uModel)
    sphereMesh.draw()

    // Hair & Sunglasses
    GLES30.glUniform3fv(uBaseColor, 1, floatArrayOf(0.12f, 0.09f, 0.08f), 0)
    drawChildBox(root, 0f, 1.66f, -0.02f, 0.31f, 0.16f, 0.31f, uModel)
    sphereMesh.draw()
    GLES30.glUniform1f(uMetallic, 0.85f)
    GLES30.glUniform1f(uRoughness, 0.1f)
    drawChildBox(root, 0f, 1.58f, 0.14f, 0.24f, 0.07f, 0.06f, uModel)
    boxMesh.draw()
  }

  private fun drawWater() {
    GLES30.glEnable(GLES30.GL_BLEND)
    GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

    waterShader.use()
    GLES30.glUniformMatrix4fv(waterShader.loc("uViewProj"), 1, false, viewProjMat, 0)
    GLES30.glUniform1f(waterShader.loc("uTime"), sim.worldTimeSec)
    GLES30.glUniform3f(waterShader.loc("uCamPos"), sim.camPos.x, sim.camPos.y, sim.camPos.z)
    GLES30.glUniform3fv(waterShader.loc("uSunDir"), 1, sunDir, 0)
    GLES30.glUniform3fv(waterShader.loc("uSunColor"), 1, sunColor, 0)
    GLES30.glUniform3fv(waterShader.loc("uSkyZenith"), 1, skyZenith, 0)
    GLES30.glUniform3fv(waterShader.loc("uSkyHorizon"), 1, skyHorizon, 0)

    Matrix.setIdentityM(modelMat, 0)
    Matrix.translateM(modelMat, 0, 0f, -0.42f, 0f)
    GLES30.glUniformMatrix4fv(waterShader.loc("uModel"), 1, false, modelMat, 0)
    GLES30.glUniform3f(waterShader.loc("uWaterTint"), 0.06f, 0.42f, 0.56f)
    GLES30.glUniform1f(waterShader.loc("uWaveAmp"), 1.0f)
    GLES30.glUniform1f(waterShader.loc("uIsOcean"), 1.0f)
    waterOceanMesh.draw()

    GLES30.glUniform1f(waterShader.loc("uWaveAmp"), 0.0f)
    GLES30.glUniform1f(waterShader.loc("uIsOcean"), 0.0f)
    for (box in sim.world.staticBoxes) {
      if (box.surface != MaterialSurface.WATER) continue
      setBoxModel(box.cx, box.cy + 0.04f, box.cz, box.sx, box.sy, box.sz, box.yawDeg)
      GLES30.glUniformMatrix4fv(waterShader.loc("uModel"), 1, false, modelMat, 0)
      GLES30.glUniform3fv(waterShader.loc("uWaterTint"), 1, box.color, 0)
      boxMesh.draw()
    }

    GLES30.glDisable(GLES30.GL_BLEND)
  }

  private fun drawGlassWindowsAndWindshields() {
    GLES30.glEnable(GLES30.GL_BLEND)
    GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
    GLES30.glDepthMask(false)

    worldShader.use()
    bindCommonWorldUniforms()
    val uModel = worldShader.loc("uModel")
    val uBaseColor = worldShader.loc("uBaseColor")
    val uMaterialType = worldShader.loc("uMaterialType")
    val uEmissive = worldShader.loc("uEmissive")
    val uMetallic = worldShader.loc("uMetallic")
    val uRoughness = worldShader.loc("uRoughness")
    val uAlpha = worldShader.loc("uAlpha")
    val uWind = worldShader.loc("uWind")

    GLES30.glUniform1f(uWind, 0f)
    GLES30.glUniform1i(uMaterialType, MaterialSurface.GLASS.id)
    GLES30.glUniform1f(uEmissive, 0f)
    GLES30.glUniform1f(uMetallic, 0.85f)
    GLES30.glUniform1f(uRoughness, 0.06f)
    GLES30.glUniform1f(uAlpha, 0.34f)

    // House glass windows
    for (g in sim.world.glassBoxes) {
      setBoxModel(g.cx, g.cy, g.cz, g.sx, g.sy, g.sz, g.yawDeg)
      GLES30.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      GLES30.glUniform3fv(uBaseColor, 1, g.color, 0)
      boxMesh.draw()
    }

    // Translucent angled car windshields so seated driver is crystal clear from outside!
    val windshieldTint = floatArrayOf(0.45f, 0.78f, 0.92f)
    GLES30.glUniform3fv(uBaseColor, 1, windshieldTint, 0)
    for (car in sim.vehicles) {
      val yawDeg = Math.toDegrees(car.yawRad.toDouble()).toFloat()
      Matrix.setIdentityM(tmpMat, 0)
      Matrix.translateM(tmpMat, 0, car.x, 0.04f, car.z)
      Matrix.rotateM(tmpMat, 0, yawDeg, 0f, 1f, 0f)
      Matrix.translateM(tmpMat, 0, 0f, 1.18f, 0.54f)
      Matrix.rotateM(tmpMat, 0, -34f, 1f, 0f, 0f)
      Matrix.scaleM(tmpMat, 0, 1.44f, 0.56f, 0.05f)
      GLES30.glUniformMatrix4fv(uModel, 1, false, tmpMat, 0)
      boxMesh.draw()
    }

    // Translucent boat windshields
    for (boat in sim.boats) {
      val yawDeg = Math.toDegrees(boat.yawRad.toDouble()).toFloat()
      Matrix.setIdentityM(tmpMat, 0)
      Matrix.translateM(tmpMat, 0, boat.x, -0.34f + boat.bobY, boat.z)
      Matrix.rotateM(tmpMat, 0, yawDeg, 0f, 1f, 0f)
      Matrix.translateM(tmpMat, 0, 0f, 0.96f, 0.42f)
      Matrix.rotateM(tmpMat, 0, -40f, 1f, 0f, 0f)
      Matrix.scaleM(tmpMat, 0, 1.48f, 0.42f, 0.05f)
      GLES30.glUniformMatrix4fv(uModel, 1, false, tmpMat, 0)
      boxMesh.draw()
    }

    GLES30.glDepthMask(true)
    GLES30.glDisable(GLES30.GL_BLEND)
  }

  private fun bindCommonWorldUniforms() {
    GLES30.glUniformMatrix4fv(worldShader.loc("uViewProj"), 1, false, viewProjMat, 0)
    GLES30.glUniformMatrix4fv(worldShader.loc("uLightViewProj"), 1, false, lightViewProjMat, 0)
    GLES30.glUniform1f(worldShader.loc("uTime"), sim.worldTimeSec)
    GLES30.glUniform3f(worldShader.loc("uCamPos"), sim.camPos.x, sim.camPos.y, sim.camPos.z)
    GLES30.glUniform3fv(worldShader.loc("uSunDir"), 1, sunDir, 0)
    GLES30.glUniform3fv(worldShader.loc("uSunColor"), 1, sunColor, 0)
    GLES30.glUniform3fv(worldShader.loc("uSkyZenith"), 1, skyZenith, 0)
    GLES30.glUniform3fv(worldShader.loc("uSkyHorizon"), 1, skyHorizon, 0)
    GLES30.glUniform1f(worldShader.loc("uNightFactor"), nightFactor)

    val car = sim.activeVehicle ?: sim.vehicles.firstOrNull()
    if (car != null && (sim.activeVehicle != null || nightFactor > 0.1f)) {
      val fx = sin(car.yawRad)
      val fz = cos(car.yawRad)
      GLES30.glUniform3f(worldShader.loc("uHeadlightPos"), car.x + fx * 2.0f, 0.65f, car.z + fz * 2.0f)
      GLES30.glUniform3f(worldShader.loc("uHeadlightDir"), fx, -0.14f, fz)
      GLES30.glUniform1f(worldShader.loc("uHeadlightOn"), 1f)
    } else {
      GLES30.glUniform1f(worldShader.loc("uHeadlightOn"), 0f)
    }

    if (hasShadowMap) {
      GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
      GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadowDepthTex)
      GLES30.glUniform1i(worldShader.loc("uShadowMap"), 0)
      GLES30.glUniform1f(worldShader.loc("uHasShadowMap"), 1f)
    } else {
      GLES30.glUniform1f(worldShader.loc("uHasShadowMap"), 0f)
    }
  }

  private fun setBoxModel(
    cx: Float,
    cy: Float,
    cz: Float,
    sx: Float,
    sy: Float,
    sz: Float,
    yawDeg: Float,
  ) {
    Matrix.setIdentityM(modelMat, 0)
    Matrix.translateM(modelMat, 0, cx, cy, cz)
    if (abs(yawDeg) > 0.01f) {
      Matrix.rotateM(modelMat, 0, yawDeg, 0f, 1f, 0f)
    }
    Matrix.scaleM(modelMat, 0, sx, sy, sz)
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
    GLES30.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
  }

  private fun drawLimb(
    parent: FloatArray,
    pivotX: Float,
    pivotY: Float,
    pivotZ: Float,
    swingDeg: Float,
    sx: Float,
    sy: Float,
    sz: Float,
    uModel: Int,
    footOffset: Float = -sy * 0.5f,
  ) {
    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, pivotX, pivotY, pivotZ)
    Matrix.rotateM(tmpMat, 0, swingDeg, 1f, 0f, 0f)
    Matrix.translateM(tmpMat, 0, 0f, footOffset, if (footOffset < -0.45f) 0.05f else 0f)
    Matrix.scaleM(tmpMat, 0, sx, sy, sz)
    Matrix.multiplyMM(modelMat, 0, parent, 0, tmpMat, 0)
    GLES30.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
  }

  private fun setVec3(dst: FloatArray, x: Float, y: Float, z: Float) {
    dst[0] = x
    dst[1] = y
    dst[2] = z
  }
}
