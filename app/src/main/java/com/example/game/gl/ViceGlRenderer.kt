package com.example.game.gl

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.example.game.state.BoatRuntimeState
import com.example.game.state.DropPhase
import com.example.game.state.GameSimulation
import com.example.game.state.LootCategory
import com.example.game.state.NpcReactionState
import com.example.game.state.PedestrianRuntimeState
import com.example.game.state.TimeOfDayPreset
import com.example.game.state.VehicleRuntimeState
import com.example.game.state.WeaponType
import com.example.game.state.WorldLootItem
import com.example.game.world.CartoonPresets
import com.example.game.world.MaterialSurface
import com.example.game.world.TerrainHeightfield
import com.example.game.world.TreeKind
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class ViceGlRenderer(
  private val sim: GameSimulation,
  private val onHudTick: () -> Unit,
  private val appContext: Context? = null,
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
  private lateinit var leftWingMesh: Mesh
  private lateinit var rightWingMesh: Mesh
  private lateinit var skyMesh: Mesh
  private lateinit var waterOceanMesh: Mesh
  private lateinit var terrainChunks: List<Mesh>
  private lateinit var gabledRoofMesh: Mesh
  private lateinit var coneMesh: Mesh
  private lateinit var boulderMesh: Mesh
  private lateinit var domeMesh: Mesh
  private lateinit var characterRenderer: CartoonCharacterRenderer
  private lateinit var expandedRenderer: ExpandedWorldRenderer
  private var cyberpunkHeroModel: GlbGpuModel? = null
  private val glbPlayerMat = FloatArray(16)

  private var shadowFbo = 0
  private var shadowDepthTex = 0
  private var shadowColorRb = 0
  private var hasShadowMap = false
  private var surfaceReady = false
  private var worldInitStage = 0
  private var glVersionCached = ""
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

  private val playerAppearance = CartoonPresets.PLAYER_HERO
  private val lobbyRingGold = floatArrayOf(1.0f, 0.78f, 0.24f)
  private val lobbyRingCyan = floatArrayOf(0.18f, 0.90f, 0.96f)

  private fun currentPlayerAppearance() = sim.currentHeroAppearance()

  override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
    surfaceReady = false
    hasShadowMap = false
    worldInitStage = 0
    try {
      glVersionCached = GLES20.glGetString(GLES20.GL_VERSION).orEmpty()
      val renderer = GLES20.glGetString(GLES20.GL_RENDERER).orEmpty()
      Log.i("ViceGL", "GL_VERSION=$glVersionCached | GL_RENDERER=$renderer")

      GLES20.glEnable(GLES20.GL_DEPTH_TEST)
      GLES20.glEnable(GLES20.GL_CULL_FACE)
      GLES20.glCullFace(GLES20.GL_BACK)

      // Fast minimal boot so Frame 1 swaps buffers immediately and satisfies Android SurfaceSyncGroup
      skyShader = ShaderProgram(ViceShaders.SKY_VERTEX, ViceShaders.SKY_FRAGMENT)
      skyMesh = MeshBuilder.createSphere(rings = 16, sectors = 24, inverted = true)
      lastFrameNs = System.nanoTime()
      surfaceReady = true
    } catch (t: Throwable) {
      Log.e("ViceGL", "Failed in onSurfaceCreated", t)
      surfaceReady = false
    }
  }

  private fun completeDeferredSceneInit() {
    try {
      worldShader = ShaderProgram(ViceShaders.WORLD_VERTEX, ViceShaders.WORLD_FRAGMENT)
      shadowShader = ShaderProgram(ViceShaders.SHADOW_VERTEX, ViceShaders.SHADOW_FRAGMENT)
      waterShader = ShaderProgram(ViceShaders.WATER_VERTEX, ViceShaders.WATER_FRAGMENT)

      boxMesh = MeshBuilder.createUnitBox()
      cylinderMesh = MeshBuilder.createCylinder(segments = 16)
      sphereMesh = MeshBuilder.createSphere(rings = 12, sectors = 18, inverted = false)
      palmFrondMesh = MeshBuilder.createPalmFrond()
      carBodyMesh = MeshBuilder.createSportsCarBody()
      boatHullMesh = MeshBuilder.createBoatHull()
      leftWingMesh = MeshBuilder.createSweptWing(rightSide = false)
      rightWingMesh = MeshBuilder.createSweptWing(rightSide = true)
      waterOceanMesh = MeshBuilder.createWaterPlane(size = 980f, grid = 48)
      terrainChunks =
        listOf(
          MeshBuilder.createTerrainChunkMesh(-300f, 0f, -300f, 0f, segsX = 68, segsZ = 68),
          MeshBuilder.createTerrainChunkMesh(0f, 300f, -300f, 0f, segsX = 68, segsZ = 68),
          MeshBuilder.createTerrainChunkMesh(-300f, 0f, 0f, 300f, segsX = 68, segsZ = 68),
          MeshBuilder.createTerrainChunkMesh(0f, 300f, 0f, 300f, segsX = 68, segsZ = 68),
        )
      gabledRoofMesh = MeshBuilder.createGabledRoofMesh()
      coneMesh = MeshBuilder.createConeMesh(segments = 12)
      boulderMesh = MeshBuilder.createRuggedBoulderMesh()
      domeMesh = MeshBuilder.createDomeMesh(rings = 8, sectors = 18)
      val characterMeshes = CharacterMeshBuilder.CharacterMeshes(boxMesh, cylinderMesh, sphereMesh)
      characterRenderer = CartoonCharacterRenderer(characterMeshes)
      expandedRenderer =
        ExpandedWorldRenderer(
          boxMesh = boxMesh,
          cylinderMesh = cylinderMesh,
          sphereMesh = sphereMesh,
          leftWingMesh = leftWingMesh,
          rightWingMesh = rightWingMesh,
          characterRenderer = characterRenderer,
        )

      cyberpunkHeroModel?.release()
      cyberpunkHeroModel =
        GlbGpuModel.tryLoadFromAssets(
          context = appContext,
          assetPath = "/cyberpunk_character.glb",
          targetHeightMeters = 1.76f,
        )

      initShadowFbo(glVersionCached)
      surfaceReady = ::worldShader.isInitialized && worldShader.programId != 0
      worldInitStage = 2
      lastFrameNs = System.nanoTime()
      Log.i(
        "ViceGL",
        "Shaders ready: world=${worldShader.programId}, sky=${skyShader.programId}, water=${waterShader.programId}, shadow=$hasShadowMap",
      )
    } catch (t: Throwable) {
      Log.e("ViceGL", "Failed in completeDeferredSceneInit", t)
      surfaceReady = false
    }
  }

  private fun initShadowFbo(glVersion: String) {
    try {
      if (shadowShader.programId == 0) {
        hasShadowMap = false
        return
      }
      val extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS).orEmpty()
      val supportsDepthTex =
        extensions.contains("OES_depth_texture") ||
          extensions.contains("ANGLE_depth_texture") ||
          glVersion.contains("ES 3.")
      if (!supportsDepthTex) {
        hasShadowMap = false
        return
      }
      while (GLES20.glGetError() != GLES20.GL_NO_ERROR) {}

      val fbos = IntArray(1)
      val texs = IntArray(1)
      val rbs = IntArray(1)
      GLES20.glGenFramebuffers(1, fbos, 0)
      GLES20.glGenTextures(1, texs, 0)
      GLES20.glGenRenderbuffers(1, rbs, 0)
      shadowFbo = fbos[0]
      shadowDepthTex = texs[0]
      shadowColorRb = rbs[0]
      if (shadowFbo == 0 || shadowDepthTex == 0) {
        hasShadowMap = false
        return
      }

      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, shadowDepthTex)
      GLES20.glTexImage2D(
        GLES20.GL_TEXTURE_2D,
        0,
        GLES20.GL_DEPTH_COMPONENT,
        shadowSize,
        shadowSize,
        0,
        GLES20.GL_DEPTH_COMPONENT,
        GLES20.GL_UNSIGNED_SHORT,
        null,
      )
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, shadowFbo)
      GLES20.glFramebufferTexture2D(
        GLES20.GL_FRAMEBUFFER,
        GLES20.GL_DEPTH_ATTACHMENT,
        GLES20.GL_TEXTURE_2D,
        shadowDepthTex,
        0,
      )
      if (shadowColorRb != 0) {
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, shadowColorRb)
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_RGB565, shadowSize, shadowSize)
        GLES20.glFramebufferRenderbuffer(
          GLES20.GL_FRAMEBUFFER,
          GLES20.GL_COLOR_ATTACHMENT0,
          GLES20.GL_RENDERBUFFER,
          shadowColorRb,
        )
      }

      val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
      val err = GLES20.glGetError()
      hasShadowMap = (status == GLES20.GL_FRAMEBUFFER_COMPLETE && err == GLES20.GL_NO_ERROR)
      GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, 0)
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    } catch (t: Throwable) {
      Log.w("ViceGL", "Shadow FBO disabled due to driver quirk", t)
      hasShadowMap = false
      runCatching {
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
      }
    }
  }

  override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
    viewWidth = width.coerceAtLeast(1)
    viewHeight = height.coerceAtLeast(1)
    GLES20.glViewport(0, 0, viewWidth, viewHeight)
  }

  override fun onDrawFrame(gl: GL10?) {
    if (!surfaceReady) {
      GLES20.glClearColor(0.08f, 0.06f, 0.14f, 1f)
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
      return
    }

    if (worldInitStage == 0) {
      // Frame 1: fast twilight sky clear + swap so SurfaceView SurfaceSyncGroup completes in <30ms
      try {
        updateAtmosphere()
        val aspect = (viewWidth.toFloat() / viewHeight.coerceAtLeast(1).toFloat()).coerceIn(0.25f, 4.0f)
        Matrix.perspectiveM(projMat, 0, sim.camFovDeg.coerceIn(15f, 110f), aspect, 0.25f, 600f)
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
        GLES20.glViewport(0, 0, viewWidth, viewHeight)
        GLES20.glClearColor(skyHorizon[0], skyHorizon[1], skyHorizon[2], 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        drawSkyDome()
      } catch (t: Throwable) {
        Log.e("ViceGL", "First frame clear error", t)
      }
      worldInitStage = 1
      lastFrameNs = System.nanoTime()
      return
    }

    if (worldInitStage == 1) {
      completeDeferredSceneInit()
      if (!surfaceReady || worldInitStage < 2) return
    }

    val now = System.nanoTime()
    val dt = ((now - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
    lastFrameNs = now

    sim.step(dt)
    hudAccumSec += dt
    if (hudAccumSec >= 0.045f) {
      hudAccumSec = 0f
      onHudTick()
    }

    try {
      updateAtmosphere()

      val aspect = (viewWidth.toFloat() / viewHeight.coerceAtLeast(1).toFloat()).coerceIn(0.25f, 4.0f)
      val fov = sim.camFovDeg.coerceIn(15f, 110f)
      Matrix.perspectiveM(projMat, 0, fov, aspect, 0.25f, 820f)
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

      val focusX =
        when {
          !sim.isPlaying -> 0f
          sim.dropPhase != DropPhase.GROUND || sim.isSpectatingKiller -> sim.camLookAt.x
          else -> sim.playerX
        }
      val focusZ =
        when {
          !sim.isPlaying -> 0f
          sim.dropPhase != DropPhase.GROUND || sim.isSpectatingKiller -> sim.camLookAt.z
          else -> sim.playerZ
        }
      Matrix.setLookAtM(
        lightViewMat,
        0,
        focusX + sunDir[0] * 68f,
        sunDir[1] * 68f,
        focusZ + sunDir[2] * 68f,
        focusX,
        0f,
        focusZ,
        0f,
        1f,
        0f,
      )
      Matrix.orthoM(lightProjMat, 0, -72f, 72f, -72f, 72f, 5f, 160f)
      Matrix.multiplyMM(lightViewProjMat, 0, lightProjMat, 0, lightViewMat, 0)

      if (hasShadowMap) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, shadowFbo)
        GLES20.glViewport(0, 0, shadowSize, shadowSize)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glPolygonOffset(2.0f, 4.0f)

        shadowShader.use()
        val uLightMvp = shadowShader.loc("uLightMvp")
        val uTime = shadowShader.loc("uTime")
        val uWind = shadowShader.loc("uWind")
        GLES20.glUniform1f(uTime, sim.worldTimeSec)

        renderShadowCasters(uLightMvp, uWind)

        GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
      }

      GLES20.glViewport(0, 0, viewWidth, viewHeight)
      GLES20.glClearColor(skyHorizon[0], skyHorizon[1], skyHorizon[2], 1f)
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

      drawSkyDome()
      drawWorldOpaque()
      drawWater()
      drawGlassWindowsAndWindshields()
    } catch (t: Throwable) {
      Log.e("ViceGL", "Render frame error", t)
      runCatching { GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0) }
    }
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
    val storm = sim.livingWorld.weatherMode.stormFactor
    val flash = sim.livingWorld.lightningFlash
    if (storm > 0.01f || flash > 0.01f) {
      val darken = 1f - storm * 0.42f
      for (i in 0..2) {
        skyZenith[i] = (skyZenith[i] * darken + flash * 0.45f).coerceIn(0f, 1f)
        skyHorizon[i] = (skyHorizon[i] * darken + flash * 0.48f).coerceIn(0f, 1f)
        sunColor[i] = (sunColor[i] * (1f - storm * 0.32f) + flash * 0.35f).coerceIn(0f, 1.4f)
      }
    }
  }

  private fun drawSkyDome() {
    if (skyShader.programId == 0) return
    GLES20.glDepthMask(false)
    GLES20.glDepthFunc(GLES20.GL_LEQUAL)
    skyShader.use()

    System.arraycopy(viewMat, 0, skyViewMat, 0, 16)
    skyViewMat[12] = 0f
    skyViewMat[13] = 0f
    skyViewMat[14] = 0f
    Matrix.multiplyMM(mvpMat, 0, projMat, 0, skyViewMat, 0)

    GLES20.glUniformMatrix4fv(skyShader.loc("uMvp"), 1, false, mvpMat, 0)
    GLES20.glUniform3fv(skyShader.loc("uSunDir"), 1, sunDir, 0)
    GLES20.glUniform3fv(skyShader.loc("uSkyZenith"), 1, skyZenith, 0)
    GLES20.glUniform3fv(skyShader.loc("uSkyHorizon"), 1, skyHorizon, 0)
    GLES20.glUniform3fv(skyShader.loc("uSunColor"), 1, sunColor, 0)
    GLES20.glUniform1f(skyShader.loc("uTime"), sim.worldTimeSec)
    GLES20.glUniform1f(skyShader.loc("uNightFactor"), nightFactor)

    skyMesh.draw()

    GLES20.glDepthFunc(GLES20.GL_LESS)
    GLES20.glDepthMask(true)
  }

  private fun renderShadowCasters(uLightMvp: Int, uWind: Int) {
    GLES20.glUniform1f(uWind, 0f)
    for (box in sim.world.staticBoxes) {
      if (!box.castShadow || box.surface == MaterialSurface.WATER) continue
      setBoxModel(box.cx, box.cy, box.cz, box.sx, box.sy, box.sz, box.yawDeg)
      Matrix.multiplyMM(mvpMat, 0, lightViewProjMat, 0, modelMat, 0)
      GLES20.glUniformMatrix4fv(uLightMvp, 1, false, mvpMat, 0)
      boxMesh.draw()
    }
    for (cyl in sim.world.staticCylinders) {
      setBoxModel(cyl.cx, cyl.cy, cyl.cz, cyl.radius * 2f, cyl.height, cyl.radius * 2f, 0f)
      Matrix.multiplyMM(mvpMat, 0, lightViewProjMat, 0, modelMat, 0)
      GLES20.glUniformMatrix4fv(uLightMvp, 1, false, mvpMat, 0)
      cylinderMesh.draw()
    }

    for (tree in sim.world.trees) {
      val groundY = TerrainHeightfield.elevationAt(tree.x, tree.z)
      when (tree.kind) {
        TreeKind.PALM -> {
          GLES20.glUniform1f(uWind, 0f)
          setBoxModel(tree.x, groundY + tree.height * 0.45f, tree.z, 0.42f, tree.height * 0.9f, 0.42f, tree.yawDeg)
          Matrix.multiplyMM(mvpMat, 0, lightViewProjMat, 0, modelMat, 0)
          GLES20.glUniformMatrix4fv(uLightMvp, 1, false, mvpMat, 0)
          cylinderMesh.draw()

          GLES20.glUniform1f(uWind, 1.0f)
          for (i in 0 until 7) {
            val a = tree.yawDeg + i * (360f / 7f)
            Matrix.setIdentityM(modelMat, 0)
            Matrix.translateM(modelMat, 0, tree.x, groundY + tree.height * 0.88f, tree.z)
            Matrix.rotateM(modelMat, 0, a, 0f, 1f, 0f)
            Matrix.multiplyMM(mvpMat, 0, lightViewProjMat, 0, modelMat, 0)
            GLES20.glUniformMatrix4fv(uLightMvp, 1, false, mvpMat, 0)
            palmFrondMesh.draw()
          }
        }
        TreeKind.PINE_CONIFER -> {
          GLES20.glUniform1f(uWind, 0f)
          setBoxModel(tree.x, groundY + tree.height * 0.25f, tree.z, 0.44f, tree.height * 0.50f, 0.44f, tree.yawDeg)
          Matrix.multiplyMM(mvpMat, 0, lightViewProjMat, 0, modelMat, 0)
          GLES20.glUniformMatrix4fv(uLightMvp, 1, false, mvpMat, 0)
          cylinderMesh.draw()

          GLES20.glUniform1f(uWind, 0.35f)
          setBoxModel(tree.x, groundY + tree.height * 0.65f, tree.z, 3.3f, tree.height * 0.56f, 3.3f, tree.yawDeg)
          Matrix.multiplyMM(mvpMat, 0, lightViewProjMat, 0, modelMat, 0)
          GLES20.glUniformMatrix4fv(uLightMvp, 1, false, mvpMat, 0)
          coneMesh.draw()
        }
        else -> {
          GLES20.glUniform1f(uWind, 0f)
          setBoxModel(tree.x, groundY + tree.height * 0.28f, tree.z, 0.55f, tree.height * 0.56f, 0.55f, tree.yawDeg)
          Matrix.multiplyMM(mvpMat, 0, lightViewProjMat, 0, modelMat, 0)
          GLES20.glUniformMatrix4fv(uLightMvp, 1, false, mvpMat, 0)
          cylinderMesh.draw()

          GLES20.glUniform1f(uWind, 0.4f)
          setBoxModel(tree.x, groundY + tree.height * 0.68f, tree.z, 3.6f, 3.0f, 3.6f, tree.yawDeg)
          Matrix.multiplyMM(mvpMat, 0, lightViewProjMat, 0, modelMat, 0)
          GLES20.glUniformMatrix4fv(uLightMvp, 1, false, mvpMat, 0)
          sphereMesh.draw()
        }
      }
    }

    GLES20.glUniform1f(uWind, 0f)
    for (v in sim.vehicles) {
      Matrix.setIdentityM(modelMat, 0)
      Matrix.translateM(modelMat, 0, v.x, v.y + 0.04f, v.z)
      Matrix.rotateM(modelMat, 0, Math.toDegrees(v.yawRad.toDouble()).toFloat(), 0f, 1f, 0f)
      Matrix.multiplyMM(mvpMat, 0, lightViewProjMat, 0, modelMat, 0)
      GLES20.glUniformMatrix4fv(uLightMvp, 1, false, mvpMat, 0)
      carBodyMesh.draw()
    }

    if (sim.activeVehicle == null && sim.activeBoat == null && sim.dropPhase != DropPhase.AIRPLANE && sim.playerY < 34f) {
      val glbHero = cyberpunkHeroModel
      if (glbHero != null) {
        GlbGpuModel.computePlayerTransform(
          outMatrix = glbPlayerMat,
          x = sim.playerX,
          y = sim.playerY,
          z = sim.playerZ,
          yawRad = sim.playerYawRad,
          walkCycle = sim.playerWalkCycle,
          speedNorm = sim.playerSpeedNorm,
          isSprinting = (sim.input.sprintHeld || sim.input.autoSprint) && sim.playerSpeedNorm > 0.5f,
          isSwimming = sim.isSwimming,
          fallProgress = if (sim.isPlayerEliminated) sim.playerFallProgress else 0f,
          worldTimeSec = sim.worldTimeSec,
          sitProgress = sim.sitProgress,
          isSkydiving = sim.dropPhase == DropPhase.FREEFALL,
          isParachuting = sim.dropPhase == DropPhase.PARACHUTE,
          airborneFallNorm = sim.airborneFallNorm,
        )
        glbHero.drawShadow(glbPlayerMat, lightViewProjMat, mvpMat, uLightMvp)
      } else {
        setBoxModel(
          sim.playerX,
          sim.playerY + 0.92f,
          sim.playerZ,
          0.78f,
          1.85f,
          0.72f,
          Math.toDegrees(sim.playerYawRad.toDouble()).toFloat(),
        )
        Matrix.multiplyMM(mvpMat, 0, lightViewProjMat, 0, modelMat, 0)
        GLES20.glUniformMatrix4fv(uLightMvp, 1, false, mvpMat, 0)
        sphereMesh.draw()
      }
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

    GLES20.glUniform1f(uAlpha, 1.0f)
    GLES20.glUniform1f(uWind, 0f)

    // 0. Continuous 3D Undulating Terrain Heightfield Chunks
    Matrix.setIdentityM(modelMat, 0)
    GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
    GLES20.glUniform3fv(uBaseColor, 1, floatArrayOf(0.26f, 0.48f, 0.24f), 0)
    GLES20.glUniform1i(uMaterialType, MaterialSurface.TERRAIN.id)
    GLES20.glUniform1f(uEmissive, 0f)
    GLES20.glUniform1f(uMetallic, 0f)
    GLES20.glUniform1f(uRoughness, 0.90f)
    for (chunk in terrainChunks) {
      chunk.draw()
    }

    // 1. Static Boxes & Static Cylinders (Gazebos, Water Tanks, Water Tower, POI Foundations)
    for (box in sim.world.staticBoxes) {
      if (box.surface == MaterialSurface.WATER) continue
      setBoxModel(box.cx, box.cy, box.cz, box.sx, box.sy, box.sz, box.yawDeg)
      GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      GLES20.glUniform3fv(uBaseColor, 1, box.color, 0)
      GLES20.glUniform1i(uMaterialType, box.surface.id)
      GLES20.glUniform1f(uEmissive, box.emissive)
      GLES20.glUniform1f(uMetallic, box.metallic)
      GLES20.glUniform1f(uRoughness, box.roughness)
      boxMesh.draw()
    }
    for (cyl in sim.world.staticCylinders) {
      setBoxModel(cyl.cx, cyl.cy, cyl.cz, cyl.radius * 2f, cyl.height, cyl.radius * 2f, 0f)
      GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      GLES20.glUniform3fv(uBaseColor, 1, cyl.color, 0)
      GLES20.glUniform1i(uMaterialType, cyl.surface.id)
      GLES20.glUniform1f(uEmissive, cyl.emissive)
      GLES20.glUniform1f(uMetallic, cyl.metallic)
      GLES20.glUniform1f(uRoughness, cyl.roughness)
      cylinderMesh.draw()
    }

    // 1B. Pitched Gabled Roofs (Farmtopia Barn, Cottages, Watchtowers, Rust Hangar)
    GLES20.glUniform1f(uEmissive, 0f)
    GLES20.glUniform1f(uMetallic, 0.08f)
    GLES20.glUniform1f(uRoughness, 0.62f)
    for (roof in sim.world.gabledRoofs) {
      setBoxModel(roof.cx, roof.cy, roof.cz, roof.width, roof.height, roof.length, roof.yawDeg)
      GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      GLES20.glUniform3fv(uBaseColor, 1, roof.color, 0)
      GLES20.glUniform1i(uMaterialType, roof.surface.id)
      gabledRoofMesh.draw()
    }

    // 1C. Rugged Rocky Boulder Outcrops
    GLES20.glUniform1i(uMaterialType, MaterialSurface.SLATE.id)
    GLES20.glUniform1f(uMetallic, 0.02f)
    GLES20.glUniform1f(uRoughness, 0.92f)
    for (boulder in sim.world.boulders) {
      Matrix.setIdentityM(modelMat, 0)
      Matrix.translateM(modelMat, 0, boulder.x, boulder.y, boulder.z)
      Matrix.rotateM(modelMat, 0, boulder.yawDeg, 0f, 1f, 0f)
      Matrix.rotateM(modelMat, 0, boulder.pitchDeg, 1f, 0f, 0f)
      Matrix.scaleM(modelMat, 0, boulder.rx * 2f, boulder.ry * 2f, boulder.rz * 2f)
      GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      GLES20.glUniform3fv(uBaseColor, 1, boulder.color, 0)
      boulderMesh.draw()
    }

    // 1D. Spinning Wind Turbines on Coastal Bluffs
    val whiteTower = floatArrayOf(0.92f, 0.94f, 0.96f)
    val darkHub = floatArrayOf(0.22f, 0.25f, 0.30f)
    for ((idx, wt) in sim.world.windTurbines.withIndex()) {
      GLES20.glUniform1i(uMaterialType, MaterialSurface.SLATE.id)
      GLES20.glUniform1f(uMetallic, 0.55f)
      GLES20.glUniform1f(uRoughness, 0.30f)
      GLES20.glUniform3fv(uBaseColor, 1, whiteTower, 0)
      setBoxModel(wt.x, wt.y + wt.towerHeight * 0.5f, wt.z, 0.85f, wt.towerHeight, 0.85f, wt.yawDeg)
      GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      cylinderMesh.draw()

      val nacelleBase = FloatArray(16)
      Matrix.setIdentityM(nacelleBase, 0)
      Matrix.translateM(nacelleBase, 0, wt.x, wt.y + wt.towerHeight, wt.z)
      Matrix.rotateM(nacelleBase, 0, wt.yawDeg, 0f, 1f, 0f)
      drawChildBox(nacelleBase, 0f, 0f, 0f, 1.05f, 1.05f, 2.5f, uModel)
      GLES20.glUniform3fv(uBaseColor, 1, darkHub, 0)
      boxMesh.draw()

      val spinDeg = (sim.worldTimeSec * 48f + idx * 120f) % 360f
      GLES20.glUniform3fv(uBaseColor, 1, whiteTower, 0)
      for (b in 0 until 3) {
        Matrix.setIdentityM(tmpMat, 0)
        Matrix.translateM(tmpMat, 0, 0f, 0f, 1.32f)
        Matrix.rotateM(tmpMat, 0, spinDeg + b * 120f, 0f, 0f, 1f)
        Matrix.translateM(tmpMat, 0, 0f, wt.bladeRadius * 0.5f, 0f)
        Matrix.scaleM(tmpMat, 0, 0.32f, wt.bladeRadius, 0.12f)
        Matrix.multiplyMM(modelMat, 0, nacelleBase, 0, tmpMat, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
        boxMesh.draw()
      }
    }

    // 2. Diverse Tree Species (Palm, Shade, Pine Conifer, Cypress/Acacia) seated on 3D terrain!
    val trunkColor = floatArrayOf(0.46f, 0.34f, 0.24f)
    val pineTrunk = floatArrayOf(0.36f, 0.25f, 0.18f)
    val palmGreen = floatArrayOf(0.18f, 0.56f, 0.24f)
    val shadeGreen1 = floatArrayOf(0.16f, 0.50f, 0.22f)
    val shadeGreen2 = floatArrayOf(0.22f, 0.58f, 0.26f)
    val pineGreen1 = floatArrayOf(0.12f, 0.36f, 0.19f)
    val pineGreen2 = floatArrayOf(0.16f, 0.43f, 0.22f)
    val acaciaGreen = floatArrayOf(0.28f, 0.50f, 0.18f)

    for (tree in sim.world.trees) {
      val baseY = TerrainHeightfield.elevationAt(tree.x, tree.z) - 0.15f
      when (tree.kind) {
        TreeKind.PALM -> {
          GLES20.glUniform1f(uWind, 0f)
          GLES20.glUniform1i(uMaterialType, MaterialSurface.WOOD.id)
          GLES20.glUniform1f(uEmissive, 0f)
          GLES20.glUniform1f(uMetallic, 0f)
          GLES20.glUniform1f(uRoughness, 0.85f)
          GLES20.glUniform3fv(uBaseColor, 1, trunkColor, 0)

          val segH = tree.height * 0.24f
          val leanDir = Math.toRadians(tree.yawDeg.toDouble()).toFloat()
          var curX = tree.x
          var curZ = tree.z
          var curY = baseY
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
            GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
            cylinderMesh.draw()
            curY += segH * 0.96f
          }

          GLES20.glDisable(GLES20.GL_CULL_FACE)
          GLES20.glUniform1i(uMaterialType, MaterialSurface.FOLIAGE.id)
          GLES20.glUniform3fv(uBaseColor, 1, palmGreen, 0)
          GLES20.glUniform1f(uRoughness, 0.62f)
          GLES20.glUniform1f(uWind, 1.0f)

          for (i in 0 until 8) {
            val frondYaw = tree.yawDeg + i * 45f
            val scale = 0.85f + (i % 2) * 0.22f
            Matrix.setIdentityM(modelMat, 0)
            Matrix.translateM(modelMat, 0, curX, curY, curZ)
            Matrix.rotateM(modelMat, 0, frondYaw, 0f, 1f, 0f)
            Matrix.rotateM(modelMat, 0, if (i % 2 == 0) -14f else 6f, 1f, 0f, 0f)
            Matrix.scaleM(modelMat, 0, scale, scale, scale)
            GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
            palmFrondMesh.draw()
          }
          GLES20.glEnable(GLES20.GL_CULL_FACE)
        }
        TreeKind.PINE_CONIFER -> {
          GLES20.glUniform1f(uWind, 0f)
          GLES20.glUniform1i(uMaterialType, MaterialSurface.WOOD.id)
          GLES20.glUniform1f(uEmissive, 0f)
          GLES20.glUniform1f(uMetallic, 0f)
          GLES20.glUniform1f(uRoughness, 0.88f)
          GLES20.glUniform3fv(uBaseColor, 1, pineTrunk, 0)
          setBoxModel(tree.x, baseY + tree.height * 0.24f, tree.z, 0.48f, tree.height * 0.48f, 0.48f, tree.yawDeg)
          GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
          cylinderMesh.draw()

          GLES20.glUniform1i(uMaterialType, MaterialSurface.FOLIAGE.id)
          GLES20.glUniform1f(uRoughness, 0.72f)
          GLES20.glUniform1f(uWind, 0.30f)
          val tiers =
            arrayOf(
              floatArrayOf(tree.height * 0.44f, 3.8f, tree.height * 0.34f),
              floatArrayOf(tree.height * 0.64f, 2.9f, tree.height * 0.32f),
              floatArrayOf(tree.height * 0.84f, 1.9f, tree.height * 0.28f),
            )
          tiers.forEachIndexed { idx, t ->
            GLES20.glUniform3fv(uBaseColor, 1, if (idx == 2) pineGreen2 else pineGreen1, 0)
            setBoxModel(tree.x, baseY + t[0], tree.z, t[1], t[2], t[1], tree.yawDeg)
            GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
            coneMesh.draw()
          }
        }
        TreeKind.CYPRESS_ACACIA -> {
          GLES20.glUniform1f(uWind, 0f)
          GLES20.glUniform1i(uMaterialType, MaterialSurface.WOOD.id)
          GLES20.glUniform1f(uEmissive, 0f)
          GLES20.glUniform1f(uMetallic, 0f)
          GLES20.glUniform1f(uRoughness, 0.86f)
          GLES20.glUniform3fv(uBaseColor, 1, trunkColor, 0)
          setBoxModel(tree.x, baseY + tree.height * 0.28f, tree.z, 0.44f, tree.height * 0.58f, 0.44f, tree.yawDeg)
          GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
          cylinderMesh.draw()

          GLES20.glUniform1i(uMaterialType, MaterialSurface.FOLIAGE.id)
          GLES20.glUniform1f(uRoughness, 0.68f)
          GLES20.glUniform1f(uWind, 0.42f)
          GLES20.glUniform3fv(uBaseColor, 1, acaciaGreen, 0)
          setBoxModel(tree.x, baseY + tree.height * 0.64f, tree.z, 5.2f, 1.45f, 4.8f, tree.yawDeg)
          GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
          sphereMesh.draw()
          setBoxModel(tree.x + 1.2f, baseY + tree.height * 0.72f, tree.z - 0.8f, 3.2f, 1.15f, 3.0f, tree.yawDeg)
          GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
          sphereMesh.draw()
        }
        TreeKind.TROPICAL_SHADE -> {
          GLES20.glUniform1f(uWind, 0f)
          GLES20.glUniform1i(uMaterialType, MaterialSurface.WOOD.id)
          GLES20.glUniform1f(uEmissive, 0f)
          GLES20.glUniform1f(uMetallic, 0f)
          GLES20.glUniform1f(uRoughness, 0.85f)
          GLES20.glUniform3fv(uBaseColor, 1, trunkColor, 0)

          setBoxModel(tree.x, baseY + tree.height * 0.26f, tree.z, 0.56f, tree.height * 0.54f, 0.56f, tree.yawDeg)
          GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
          cylinderMesh.draw()

          GLES20.glUniform1i(uMaterialType, MaterialSurface.FOLIAGE.id)
          GLES20.glUniform1f(uRoughness, 0.70f)
          GLES20.glUniform1f(uWind, 0.45f)

          val crownY = baseY + tree.height * 0.64f
          val puffs =
            arrayOf(
              floatArrayOf(0f, 0.35f, 0f, 3.6f, 2.8f, 3.6f),
              floatArrayOf(-1.1f, -0.15f, 0.7f, 2.5f, 2.2f, 2.5f),
              floatArrayOf(1.1f, -0.10f, -0.6f, 2.6f, 2.2f, 2.6f),
            )
          puffs.forEachIndexed { idx, p ->
            GLES20.glUniform3fv(uBaseColor, 1, if (idx == 0) shadeGreen1 else shadeGreen2, 0)
            setBoxModel(tree.x + p[0], crownY + p[1], tree.z + p[2], p[3], p[4], p[5], tree.yawDeg)
            GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
            sphereMesh.draw()
          }
        }
      }
    }

    // 3. Streetlights seated on local TerrainHeightfield elevation
    GLES20.glUniform1f(uWind, 0f)
    val poleColor = floatArrayOf(0.18f, 0.20f, 0.26f)
    for (sl in sim.world.streetLights) {
      val baseY = TerrainHeightfield.elevationAt(sl.x, sl.z)
      GLES20.glUniform1i(uMaterialType, MaterialSurface.SLATE.id)
      GLES20.glUniform1f(uEmissive, 0f)
      GLES20.glUniform1f(uMetallic, 0.6f)
      GLES20.glUniform1f(uRoughness, 0.35f)
      GLES20.glUniform3fv(uBaseColor, 1, poleColor, 0)

      setBoxModel(sl.x, baseY + 2.75f, sl.z, 0.22f, 5.5f, 0.22f, sl.yawDeg)
      GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      cylinderMesh.draw()

      val rad = Math.toRadians(sl.yawDeg.toDouble()).toFloat()
      val armX = sl.x + sin(rad) * 0.75f
      val armZ = sl.z + cos(rad) * 0.75f
      setBoxModel(armX, baseY + 5.45f, armZ, 0.24f, 0.16f, 1.5f, sl.yawDeg)
      GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      boxMesh.draw()

      val lampX = sl.x + sin(rad) * 1.35f
      val lampZ = sl.z + cos(rad) * 1.35f
      GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
      GLES20.glUniform1f(uEmissive, 0.45f + nightFactor * 0.65f)
      GLES20.glUniform3fv(uBaseColor, 1, sl.neonColor, 0)
      setBoxModel(lampX, baseY + 5.34f, lampZ, 0.48f, 0.16f, 0.52f, sl.yawDeg)
      GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
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

    // 5B. Pulsing Vice Arms Gun Shop Buy Beacon
    val buyX = sim.world.gunShopBuyPos.first
    val buyZ = sim.world.gunShopBuyPos.second
    val pulse = 0.5f + 0.5f * sin(sim.worldTimeSec * 4.2f)
    GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
    GLES20.glUniform1f(uEmissive, 0.9f + pulse * 0.6f)
    GLES20.glUniform3fv(uBaseColor, 1, floatArrayOf(1.0f, 0.72f, 0.16f), 0)
    setBoxModel(buyX, 0.14f, buyZ, 1.55f + pulse * 0.25f, 0.05f, 1.55f + pulse * 0.25f, 0f)
    GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
    cylinderMesh.draw()
    setBoxModel(buyX, 1.28f + pulse * 0.16f, buyZ, 0.34f, 0.34f, 0.34f, sim.worldTimeSec * 70f)
    GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
    boxMesh.draw()

    // 5C. Collectible 3D House & Drop Loot Items (Guns, Medkits, Coins, Lv.3/4 Vests & Helmets)
    drawWorldLootItems(uModel, uBaseColor, uMaterialType, uEmissive, uMetallic, uRoughness)

    // 6. Townsfolk & Active Companions (Strolling, Panicking/Fleeing, Fighting Back, Companion, or Knocked Down)
    val inAnyVehicle = (sim.activeVehicle != null || sim.activeBoat != null)
    val companionRingColor = floatArrayOf(0.20f, 0.94f, 0.72f)
    val rivalRingColor = floatArrayOf(1.0f, 0.26f, 0.28f)
    for (ped in sim.pedestrians) {
      if (
        (ped.isFriend && inAnyVehicle && ped.reactionState != NpcReactionState.KNOCKED_DOWN) ||
          ped.drivingVehicleId != null
      ) {
        continue
      }
      val pedFleeing = ped.reactionState == NpcReactionState.FLEEING
      val pedFighting =
        ped.reactionState == NpcReactionState.FIGHTING_BACK ||
          (ped.reactionState == NpcReactionState.COMPANION && ped.attackMove != null)
      val pedSpeedNorm =
        when (ped.reactionState) {
          NpcReactionState.KNOCKED_DOWN -> 0f
          NpcReactionState.HANDS_UP_FLINCH -> 0.05f
          NpcReactionState.FLEEING -> 1.15f
          NpcReactionState.FIGHTING_BACK -> if (ped.attackMove != null) 0.15f else 0.95f
          NpcReactionState.COMPANION ->
            when {
              ped.sitProgress > 0.35f -> 0f
              ped.attackMove != null -> 0.15f
              else -> 0.85f
            }
          NpcReactionState.STROLLING -> 0.55f
        }

      if (ped.isFriend && ped.reactionState != NpcReactionState.KNOCKED_DOWN) {
        GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
        GLES20.glUniform1f(uEmissive, 0.85f)
        GLES20.glUniform3fv(uBaseColor, 1, companionRingColor, 0)
        setBoxModel(ped.x, ped.y + 0.06f, ped.z, 1.05f, 0.03f, 1.05f, sim.worldTimeSec * 45f)
        GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
        cylinderMesh.draw()
        GLES20.glUniform1f(uEmissive, 0f)
      } else if (ped.isRivalFighter && ped.reactionState != NpcReactionState.KNOCKED_DOWN) {
        GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
        GLES20.glUniform1f(uEmissive, if (pedFighting) 1.05f else 0.55f)
        GLES20.glUniform3fv(uBaseColor, 1, rivalRingColor, 0)
        setBoxModel(ped.x, ped.y + 0.06f, ped.z, 1.10f, 0.03f, 1.10f, sim.worldTimeSec * 55f)
        GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
        cylinderMesh.draw()
        GLES20.glUniform1f(uEmissive, 0f)
      }

      characterRenderer.drawWalkingCharacter(
        x = ped.x,
        y = ped.y + 0.04f,
        z = ped.z,
        yawRad = ped.yawRad,
        walkCycle = ped.walkCycle,
        speedNorm = pedSpeedNorm,
        isSprinting = pedFleeing,
        isSwimming = false,
        armReachNorm = 0f,
        fallProgress = ped.fallProgress,
        worldTimeSec = sim.worldTimeSec,
        appearance = ped.appearance,
        uModel = uModel,
        uBaseColor = uBaseColor,
        uMaterialType = uMaterialType,
        uEmissive = uEmissive,
        uMetallic = uMetallic,
        uRoughness = uRoughness,
        sitProgress = ped.sitProgress,
        armsUpNorm = ped.armsUpNorm,
        attackMove = if (pedFighting) ped.attackMove else null,
        attackProgress = ped.attackProgress,
        equippedWeapon = ped.weapon,
        aimNorm =
          if (
            (ped.isRivalFighter || ped.weapon != WeaponType.FISTS) &&
              ped.reactionState == NpcReactionState.FIGHTING_BACK &&
              ped.weapon != WeaponType.FISTS
          ) {
            1f
          } else {
            0f
          },
        hitReactZone = ped.hitReactZone,
        hitReactProgress = (ped.hitReactTimerSec / 0.72f).coerceIn(0f, 1f),
        hitReactDirSign = ped.hitReactDirSign,
        vestLevel = ped.vestLevel,
        helmetLevel = ped.helmetLevel,
      )
    }

    // 7. Ejected / Carjacked NPCs
    val ejectedSnapshot = synchronized(sim) { sim.ejectedNpcs.toList() }
    for (npc in ejectedSnapshot) {
      characterRenderer.drawWalkingCharacter(
        x = npc.x,
        y = npc.y,
        z = npc.z,
        yawRad = npc.yawRad,
        walkCycle = npc.walkCycle,
        speedNorm = if (npc.fallProgress > 0.1f) 0.15f else if (npc.isFleeing) 1.15f else 0.55f,
        isSprinting = npc.isFleeing,
        isSwimming = npc.inWater,
        armReachNorm = 0f,
        fallProgress = npc.fallProgress,
        worldTimeSec = sim.worldTimeSec,
        appearance = npc.appearance,
        uModel = uModel,
        uBaseColor = uBaseColor,
        uMaterialType = uMaterialType,
        uEmissive = uEmissive,
        uMetallic = uMetallic,
        uRoughness = uRoughness,
        armsUpNorm = if (npc.isFleeing && npc.fallProgress < 0.2f) 0.7f else 0f,
        hitReactZone = npc.hitReactZone,
        hitReactProgress = (npc.hitReactTimerSec / 0.70f).coerceIn(0f, 1f),
        hitReactDirSign = npc.hitReactDirSign,
      )
    }

    // 8. Playable Hero Character (3D Lobby Showcase, On Foot, Skydiving, or Parachute Glide)
    val heroAppearance = currentPlayerAppearance()
    if (!sim.isPlaying) {
      // Glowing Free Fire-style pedestal halo ring under Hero in 3D Lobby
      GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
      GLES20.glUniform1f(uEmissive, 0.90f)
      GLES20.glUniform3fv(uBaseColor, 1, lobbyRingGold, 0)
      setBoxModel(sim.playerX, sim.playerY + 0.05f, sim.playerZ, 1.38f, 0.025f, 1.38f, sim.worldTimeSec * 28f)
      GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      cylinderMesh.draw()
      GLES20.glUniform1f(uEmissive, 0f)

      // Joined Squadmates standing beside Hero in 3D Lobby
      val lobbySquad = sim.lobbySquadSnapshot()
      val lobbyOffsets = arrayOf(-1.26f to -0.58f, 1.30f to -0.62f, 2.35f to -1.28f)
      val lobbyYaws = floatArrayOf(0.32f, -0.26f, -0.38f)
      for ((idx, member) in lobbySquad.take(3).withIndex()) {
        val (ox, oz) = lobbyOffsets[idx]
        val mx = sim.playerX + ox
        val mz = sim.playerZ + oz
        val my = sim.computeGroundHeight(mx, mz, 0f)
        val memberApp = GameSimulation.HERO_PRESETS.getOrElse(member.heroIndex) { "MATE" to CartoonPresets.LEAN_BUDDY_BLUE }.second

        GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
        GLES20.glUniform1f(uEmissive, 0.78f)
        GLES20.glUniform3fv(uBaseColor, 1, lobbyRingCyan, 0)
        setBoxModel(mx, my + 0.05f, mz, 1.08f, 0.022f, 1.08f, sim.worldTimeSec * 36f)
        GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
        cylinderMesh.draw()
        GLES20.glUniform1f(uEmissive, 0f)

        characterRenderer.drawWalkingCharacter(
          x = mx,
          y = my,
          z = mz,
          yawRad = lobbyYaws[idx],
          walkCycle = 0f,
          speedNorm = 0f,
          isSprinting = false,
          isSwimming = false,
          armReachNorm = 0f,
          fallProgress = 0f,
          worldTimeSec = sim.worldTimeSec + idx * 1.3f,
          appearance = memberApp,
          uModel = uModel,
          uBaseColor = uBaseColor,
          uMaterialType = uMaterialType,
          uEmissive = uEmissive,
          uMetallic = uMetallic,
          uRoughness = uRoughness,
          equippedWeapon = member.weapon,
        )
      }
    }

    if (sim.activeVehicle == null && sim.activeBoat == null && sim.dropPhase != DropPhase.AIRPLANE) {
      val glbHero = cyberpunkHeroModel
      if (glbHero != null) {
        GlbGpuModel.computePlayerTransform(
          outMatrix = glbPlayerMat,
          x = sim.playerX,
          y = sim.playerY,
          z = sim.playerZ,
          yawRad = sim.playerYawRad,
          walkCycle = sim.playerWalkCycle,
          speedNorm = sim.playerSpeedNorm,
          isSprinting = (sim.input.sprintHeld || sim.input.autoSprint) && sim.playerSpeedNorm > 0.5f,
          isSwimming = sim.isSwimming,
          fallProgress = if (sim.isPlayerEliminated) sim.playerFallProgress else 0f,
          worldTimeSec = sim.worldTimeSec,
          sitProgress = sim.sitProgress,
          isSkydiving = sim.dropPhase == DropPhase.FREEFALL,
          isParachuting = sim.dropPhase == DropPhase.PARACHUTE,
          airborneFallNorm = sim.airborneFallNorm,
        )
        glbHero.drawLit(
          baseMatrix = glbPlayerMat,
          uModel = uModel,
          uBaseColor = uBaseColor,
          uMaterialType = uMaterialType,
          uEmissive = uEmissive,
          uMetallic = uMetallic,
          uRoughness = uRoughness,
          uAlpha = uAlpha,
          uUseBaseTex = worldShader.loc("uUseBaseTex"),
          uBaseTex = worldShader.loc("uBaseTex"),
        )
        if (sim.equippedWeapon != WeaponType.FISTS && sim.dropPhase == DropPhase.GROUND && !sim.isPlayerEliminated) {
          val sitMulY = 1f - sim.sitProgress * 0.30f
          GLES20.glUniform1i(uMaterialType, MaterialSurface.SLATE.id)
          GLES20.glUniform1f(uMetallic, 0.80f)
          GLES20.glUniform1f(uRoughness, 0.24f)
          GLES20.glUniform1f(uEmissive, 0.08f)
          GLES20.glUniform3fv(uBaseColor, 1, sim.equippedGunSkin.primaryColor, 0)
          drawChildBox(glbPlayerMat, 0.28f, 1.04f * sitMulY, 0.32f, 0.11f, 0.14f, 0.52f, uModel)
          boxMesh.draw()
          if (sim.muzzleFlashNorm > 0.05f) {
            GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
            GLES20.glUniform1f(uEmissive, 1.4f * sim.muzzleFlashNorm)
            GLES20.glUniform3fv(uBaseColor, 1, floatArrayOf(1.0f, 0.78f, 0.24f), 0)
            drawChildBox(glbPlayerMat, 0.28f, 1.06f * sitMulY, 0.64f, 0.18f, 0.18f, 0.22f, uModel)
            sphereMesh.draw()
          }
          GLES20.glUniform1f(uEmissive, 0f)
        }
      } else {
        characterRenderer.drawWalkingCharacter(
          x = sim.playerX,
          y = sim.playerY,
          z = sim.playerZ,
          yawRad = sim.playerYawRad,
          walkCycle = sim.playerWalkCycle,
          speedNorm = sim.playerSpeedNorm,
          isSprinting = (sim.input.sprintHeld || sim.input.autoSprint) && sim.playerSpeedNorm > 0.5f,
          isSwimming = sim.isSwimming,
          armReachNorm = sim.playerArmReachNorm,
          fallProgress = if (sim.isPlayerEliminated) sim.playerFallProgress else 0f,
          worldTimeSec = sim.worldTimeSec,
          appearance = heroAppearance,
          uModel = uModel,
          uBaseColor = uBaseColor,
          uMaterialType = uMaterialType,
          uEmissive = uEmissive,
          uMetallic = uMetallic,
          uRoughness = uRoughness,
          sitProgress = sim.sitProgress,
          attackMove = sim.activeAttackMove,
          attackProgress = sim.attackProgress,
          equippedWeapon = sim.equippedWeapon,
          aimNorm = sim.aimNorm,
          muzzleFlash = sim.muzzleFlashNorm,
          isSkydiving = sim.dropPhase == DropPhase.FREEFALL,
          isParachuting = sim.dropPhase == DropPhase.PARACHUTE,
          parkourType = sim.activeParkour?.type,
          parkourProgress = sim.parkourProgress,
          airborneFallNorm = sim.airborneFallNorm,
          landingReaction = sim.activeLandingReaction,
          landingProgress = sim.landingReactionProgress,
          vestLevel = sim.playerVestLevel,
          helmetLevel = sim.playerHelmetLevel,
          gunSkin = sim.equippedGunSkin,
        )
      }
    }

    // 8A. Remote P2P Multiplayer Squadmates synced over PeerJS DataChannel
    if (sim.isPlaying) {
      for (remote in sim.remotePosesSnapshot()) {
        if (remote.dropPhase == DropPhase.AIRPLANE) continue
        val remoteApp = GameSimulation.HERO_PRESETS.getOrElse(remote.heroIndex) { "P2P" to CartoonPresets.LEAN_BUDDY_BLUE }.second
        GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
        GLES20.glUniform1f(uEmissive, 0.85f)
        GLES20.glUniform3fv(uBaseColor, 1, lobbyRingCyan, 0)
        setBoxModel(remote.x, remote.y + 0.06f, remote.z, 1.05f, 0.03f, 1.05f, sim.worldTimeSec * 45f)
        GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
        cylinderMesh.draw()
        GLES20.glUniform1f(uEmissive, 0f)

        characterRenderer.drawWalkingCharacter(
          x = remote.x,
          y = remote.y,
          z = remote.z,
          yawRad = remote.yawRad,
          walkCycle = remote.walkCycle,
          speedNorm = remote.speedNorm,
          isSprinting = remote.isSprinting,
          isSwimming = false,
          armReachNorm = 0f,
          fallProgress = 0f,
          worldTimeSec = sim.worldTimeSec,
          appearance = remoteApp,
          uModel = uModel,
          uBaseColor = uBaseColor,
          uMaterialType = uMaterialType,
          uEmissive = uEmissive,
          uMetallic = uMetallic,
          uRoughness = uRoughness,
          sitProgress = if (remote.isSitting) 1f else 0f,
          equippedWeapon = remote.weapon,
          isSkydiving = remote.dropPhase == DropPhase.FREEFALL,
          isParachuting = remote.dropPhase == DropPhase.PARACHUTE,
        )
        if (remote.dropPhase == DropPhase.PARACHUTE) {
          drawParachuteCanopy(
            px = remote.x,
            py = remote.y,
            pz = remote.z,
            yawRad = remote.yawRad,
            uModel = uModel,
            uBaseColor = uBaseColor,
            uMaterialType = uMaterialType,
            uEmissive = uEmissive,
            uMetallic = uMetallic,
            uRoughness = uRoughness,
          )
        }
      }
    }

    // 8B. 3D Curved Parachute Canopy & Riser Cords
    if (sim.dropPhase == DropPhase.PARACHUTE) {
      drawParachuteCanopy(
        px = sim.playerX,
        py = sim.playerY,
        pz = sim.playerZ,
        yawRad = sim.playerYawRad,
        uModel = uModel,
        uBaseColor = uBaseColor,
        uMaterialType = uMaterialType,
        uEmissive = uEmissive,
        uMetallic = uMetallic,
        uRoughness = uRoughness,
      )
    }

    // 8C. PUBG-style Sky Transport Airplane Flying Over Dual Islands
    if (sim.planeActiveInSky) {
      drawTransportAirplane(
        ax = sim.planeX,
        ay = sim.planeY,
        az = sim.planeZ,
        yawRad = sim.planeYawRad,
        uModel = uModel,
        uBaseColor = uBaseColor,
        uMaterialType = uMaterialType,
        uEmissive = uEmissive,
        uMetallic = uMetallic,
        uRoughness = uRoughness,
      )
    }

    // 8D. Living World: Clouds, Express Train, Helicopters, Ambient Airplanes, Birds & Market AI Bots
    expandedRenderer.drawOpaqueLivingEntities(
      sim = sim,
      uModel = uModel,
      uBaseColor = uBaseColor,
      uMaterialType = uMaterialType,
      uEmissive = uEmissive,
      uMetallic = uMetallic,
      uRoughness = uRoughness,
      uAlpha = uAlpha,
      nightFactor = nightFactor,
    )

    // 9. Real Flying 3D Brass Bullet Projectiles & Hot Contrails + Hit Sparks
    val bulletsSnapshot = synchronized(sim) { sim.bulletProjectiles.toList() }
    val sparksSnapshot = synchronized(sim) { sim.hitSparks.toList() }
    if (bulletsSnapshot.isNotEmpty() || sparksSnapshot.isNotEmpty()) {
      val brassSlugColor = floatArrayOf(0.96f, 0.76f, 0.28f)
      val hotTrailColor = floatArrayOf(1.0f, 0.90f, 0.45f)

      for (b in bulletsSnapshot) {
        val speed = hypot(hypot(b.vx, b.vz), b.vy).coerceAtLeast(1e-3f)
        val yawDeg = Math.toDegrees(atan2(b.vx.toDouble(), b.vz.toDouble())).toFloat()
        val pitchDeg = -Math.toDegrees(asin((b.vy / speed).coerceIn(-1f, 1f).toDouble())).toFloat()

        // Physical metallic bullet slug head
        Matrix.setIdentityM(modelMat, 0)
        Matrix.translateM(modelMat, 0, b.x, b.y, b.z)
        Matrix.rotateM(modelMat, 0, yawDeg, 0f, 1f, 0f)
        Matrix.rotateM(modelMat, 0, pitchDeg, 1f, 0f, 0f)
        Matrix.scaleM(modelMat, 0, 0.055f, 0.055f, 0.22f)
        GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
        GLES20.glUniform1i(uMaterialType, MaterialSurface.SLATE.id)
        GLES20.glUniform1f(uMetallic, 0.92f)
        GLES20.glUniform1f(uRoughness, 0.16f)
        GLES20.glUniform1f(uEmissive, 0.45f)
        GLES20.glUniform3fv(uBaseColor, 1, brassSlugColor, 0)
        sphereMesh.draw()

        // Aerodynamic bright contrail wake behind the bullet
        val dx = b.x - b.prevX
        val dy = b.y - b.prevY
        val dz = b.z - b.prevZ
        val segLen = hypot(hypot(dx, dz), dy).coerceIn(0.65f, 1.85f)
        val dirX = b.vx / speed
        val dirY = b.vy / speed
        val dirZ = b.vz / speed
        Matrix.setIdentityM(modelMat, 0)
        Matrix.translateM(
          modelMat,
          0,
          b.x - dirX * (segLen * 0.55f),
          b.y - dirY * (segLen * 0.55f),
          b.z - dirZ * (segLen * 0.55f),
        )
        Matrix.rotateM(modelMat, 0, yawDeg, 0f, 1f, 0f)
        Matrix.rotateM(modelMat, 0, pitchDeg, 1f, 0f, 0f)
        Matrix.scaleM(modelMat, 0, 0.028f, 0.028f, segLen)
        val trailColor = if (b.fromEnemy) floatArrayOf(1.0f, 0.24f, 0.20f) else hotTrailColor
        GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
        GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
        GLES20.glUniform1f(uMetallic, 0f)
        GLES20.glUniform1f(uRoughness, 0.2f)
        GLES20.glUniform1f(uEmissive, 1.95f)
        GLES20.glUniform3fv(uBaseColor, 1, trailColor, 0)
        boxMesh.draw()
      }

      GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
      GLES20.glUniform1f(uMetallic, 0f)
      GLES20.glUniform1f(uRoughness, 0.2f)
      for (sp in sparksSnapshot) {
        val progress = (sp.ageSec / sp.durationSec).coerceIn(0f, 1f)
        val rad = 0.16f + progress * 0.42f
        val fade = 1f - progress
        Matrix.setIdentityM(modelMat, 0)
        Matrix.translateM(modelMat, 0, sp.x, sp.y, sp.z)
        Matrix.scaleM(modelMat, 0, rad, rad, rad)
        GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
        GLES20.glUniform1f(uEmissive, 1.6f * fade)
        GLES20.glUniform3fv(uBaseColor, 1, sp.color, 0)
        sphereMesh.draw()
      }
      GLES20.glUniform1f(uEmissive, 0f)
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
    Matrix.translateM(base, 0, car.x, car.y + 0.04f, car.z)
    Matrix.rotateM(base, 0, yawDeg, 0f, 1f, 0f)
    Matrix.rotateM(base, 0, pitchDeg, 1f, 0f, 0f)
    Matrix.rotateM(base, 0, rollDeg, 0f, 0f, 1f)

    val botDriver = car.driverBotId?.let { id -> sim.pedestrians.firstOrNull { it.id == id } }
    val useCustomCarSkin = (!car.isAiTraffic && botDriver == null) || (sim.activeVehicle?.id == car.id)
    val carSkin = if (useCustomCarSkin) sim.equippedCarSkin else com.example.game.state.CarSkin.FACTORY_DEFAULT
    val effectiveBodyColor = carSkin.bodyColor ?: car.bodyColor
    val effectiveTrimColor = carSkin.trimColor ?: car.trimColor

    // Lower sculpted aerodynamic body shell
    GLES20.glUniformMatrix4fv(uModel, 1, false, base, 0)
    GLES20.glUniform3fv(uBaseColor, 1, effectiveBodyColor, 0)
    GLES20.glUniform1i(uMaterialType, MaterialSurface.CAR_PAINT.id)
    GLES20.glUniform1f(uEmissive, 0f)
    GLES20.glUniform1f(uMetallic, carSkin.metallic)
    GLES20.glUniform1f(uRoughness, carSkin.roughness)
    carBodyMesh.draw()

    // Open-Top Targa Cockpit Interior (recessed seats, dashboard, steering wheel, roll-bar!)
    val interiorDark = floatArrayOf(0.11f, 0.11f, 0.14f)
    val seatLeather = floatArrayOf(0.32f, 0.16f, 0.14f)
    GLES20.glUniform1i(uMaterialType, MaterialSurface.SLATE.id)
    GLES20.glUniform1f(uMetallic, 0.25f)
    GLES20.glUniform1f(uRoughness, 0.55f)
    // Cockpit deck tub
    GLES20.glUniform3fv(uBaseColor, 1, interiorDark, 0)
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
    GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
    cylinderMesh.draw()

    // Twin Sport Bucket Seats
    GLES20.glUniform3fv(uBaseColor, 1, seatLeather, 0)
    for (sx in listOf(-0.36f, 0.36f)) {
      drawChildBox(base, sx, 0.80f, -0.18f, 0.46f, 0.16f, 0.52f, uModel)
      boxMesh.draw()
      drawChildBox(base, sx, 1.12f, -0.44f, 0.44f, 0.58f, 0.14f, uModel)
      boxMesh.draw()
    }

    // Sleek Rear Targa Roll Hoop / Pillars (leaves top & sides wide open so driver is 100% visible!)
    GLES20.glUniform3fv(uBaseColor, 1, effectiveBodyColor, 0)
    GLES20.glUniform1i(uMaterialType, MaterialSurface.CAR_PAINT.id)
    GLES20.glUniform1f(uMetallic, carSkin.metallic)
    GLES20.glUniform1f(uRoughness, carSkin.roughness)
    drawChildBox(base, -0.72f, 1.24f, -0.78f, 0.12f, 0.62f, 0.36f, uModel)
    boxMesh.draw()
    drawChildBox(base, 0.72f, 1.24f, -0.78f, 0.12f, 0.62f, 0.36f, uModel)
    boxMesh.draw()
    drawChildBox(base, 0f, 1.52f, -0.82f, 1.56f, 0.10f, 0.28f, uModel)
    boxMesh.draw()

    // Front Windshield A-Pillars
    GLES20.glUniform3fv(uBaseColor, 1, effectiveTrimColor, 0)
    drawChildBox(base, -0.74f, 1.20f, 0.58f, 0.08f, 0.54f, 0.10f, uModel)
    boxMesh.draw()
    drawChildBox(base, 0.74f, 1.20f, 0.58f, 0.08f, 0.54f, 0.10f, uModel)
    boxMesh.draw()
    drawChildBox(base, 0f, 1.46f, 0.48f, 1.56f, 0.07f, 0.10f, uModel)
    boxMesh.draw()

    // Hood & Rear Racing Stripe when a custom CarSkin is active
    if (carSkin != com.example.game.state.CarSkin.FACTORY_DEFAULT) {
      GLES20.glUniform1f(uEmissive, carSkin.emissiveTrim)
      drawChildBox(base, 0f, 0.87f, 1.28f, 0.42f, 0.03f, 1.32f, uModel)
      boxMesh.draw()
      drawChildBox(base, 0f, 0.94f, -1.42f, 0.42f, 0.03f, 0.94f, uModel)
      boxMesh.draw()
    }

    // Rear GT Wing Spoiler & Front Carbon Splitter
    GLES20.glUniform1f(uEmissive, carSkin.emissiveTrim * 0.5f)
    GLES20.glUniform1f(uMetallic, 0.35f)
    GLES20.glUniform1f(uRoughness, 0.35f)
    drawChildBox(base, 0f, 1.18f, -1.96f, 1.84f, 0.08f, 0.36f, uModel)
    boxMesh.draw()
    drawChildBox(base, -0.64f, 1.04f, -1.96f, 0.10f, 0.24f, 0.24f, uModel)
    boxMesh.draw()
    drawChildBox(base, 0.64f, 1.04f, -1.96f, 0.10f, 0.24f, 0.24f, uModel)
    boxMesh.draw()
    drawChildBox(base, 0f, 0.20f, 2.12f, 1.88f, 0.08f, 0.24f, uModel)
    boxMesh.draw()
    GLES20.glUniform1f(uEmissive, 0f)

    // Visible Seated Driver inside the Cockpit (+ Companion riding passenger seat!)
    val isPlayerInside = (sim.activeVehicle?.id == car.id)
    if (isPlayerInside || car.isAiTraffic || botDriver != null) {
      val driverAppearance =
        when {
          isPlayerInside -> currentPlayerAppearance()
          botDriver != null -> botDriver.appearance
          else -> car.appearance
        }
      characterRenderer.drawSeatedDriver(
        parent = base,
        seatX = -0.34f,
        seatY = 0.78f,
        seatZ = -0.14f,
        worldTimeSec = sim.worldTimeSec,
        appearance = driverAppearance,
        uModel = uModel,
        uBaseColor = uBaseColor,
        uMaterialType = uMaterialType,
        uEmissive = uEmissive,
        uMetallic = uMetallic,
        uRoughness = uRoughness,
      )
      if (isPlayerInside) {
        val companion =
          sim.pedestrians.firstOrNull {
            it.isFriend && it.reactionState != NpcReactionState.KNOCKED_DOWN
          }
        if (companion != null) {
          characterRenderer.drawSeatedDriver(
            parent = base,
            seatX = 0.34f,
            seatY = 0.78f,
            seatZ = -0.14f,
            worldTimeSec = sim.worldTimeSec,
            appearance = companion.appearance,
            uModel = uModel,
            uBaseColor = uBaseColor,
            uMaterialType = uMaterialType,
            uEmissive = uEmissive,
            uMetallic = uMetallic,
            uRoughness = uRoughness,
          )
        }
      }
    }

    // Glowing Twin Headlights (+Z front)
    val headGlow =
      if (isPlayerInside || car.isAiTraffic || botDriver != null || nightFactor > 0.15f) 0.95f else 0.30f
    GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
    GLES20.glUniform1f(uEmissive, headGlow)
    GLES20.glUniform3fv(uBaseColor, 1, floatArrayOf(0.84f, 0.96f, 1.0f), 0)
    drawChildBox(base, -0.66f, 0.48f, 2.09f, 0.36f, 0.13f, 0.08f, uModel)
    boxMesh.draw()
    drawChildBox(base, 0.66f, 0.48f, 2.09f, 0.36f, 0.13f, 0.08f, uModel)
    boxMesh.draw()

    // Rear Tail-Light Bar
    val brakeGlow = if (car.isBraking) 1.45f else 0.60f
    GLES20.glUniform1f(uEmissive, brakeGlow)
    GLES20.glUniform3fv(uBaseColor, 1, floatArrayOf(1.0f, 0.12f, 0.20f), 0)
    drawChildBox(base, 0f, 0.68f, -2.13f, 1.66f, 0.14f, 0.07f, uModel)
    boxMesh.draw()

    // Underglow at Sunset / Night
    if (nightFactor > 0.1f) {
      GLES20.glUniform1f(uEmissive, 0.75f)
      GLES20.glUniform3fv(uBaseColor, 1, car.bodyColor, 0)
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
      Matrix.translateM(tmpMat, 0, car.x, car.y + 0.04f, car.z)
      Matrix.rotateM(tmpMat, 0, yawDeg, 0f, 1f, 0f)
      Matrix.rotateM(tmpMat, 0, pitchDeg, 1f, 0f, 0f)
      Matrix.rotateM(tmpMat, 0, rollDeg, 0f, 0f, 1f)
      Matrix.translateM(tmpMat, 0, wp[0], wp[1], wp[2])
      Matrix.rotateM(tmpMat, 0, steer, 0f, 1f, 0f)
      Matrix.rotateM(tmpMat, 0, spinDeg, 1f, 0f, 0f)
      Matrix.rotateM(tmpMat, 0, 90f, 0f, 0f, 1f)

      System.arraycopy(tmpMat, 0, modelMat, 0, 16)
      Matrix.scaleM(modelMat, 0, 0.72f, 0.28f, 0.72f)
      GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      GLES20.glUniform1i(uMaterialType, MaterialSurface.SLATE.id)
      GLES20.glUniform1f(uEmissive, 0f)
      GLES20.glUniform1f(uMetallic, 0.05f)
      GLES20.glUniform1f(uRoughness, 0.85f)
      GLES20.glUniform3fv(uBaseColor, 1, tireColor, 0)
      cylinderMesh.draw()

      System.arraycopy(tmpMat, 0, modelMat, 0, 16)
      Matrix.scaleM(modelMat, 0, 0.44f, 0.31f, 0.44f)
      GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      GLES20.glUniform1f(uMetallic, 0.92f)
      GLES20.glUniform1f(uRoughness, 0.18f)
      GLES20.glUniform3fv(uBaseColor, 1, rimColor, 0)
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
    GLES20.glDisable(GLES20.GL_CULL_FACE)
    GLES20.glUniformMatrix4fv(uModel, 1, false, base, 0)
    GLES20.glUniform3fv(uBaseColor, 1, boat.hullColor, 0)
    GLES20.glUniform1i(uMaterialType, MaterialSurface.CAR_PAINT.id)
    GLES20.glUniform1f(uEmissive, 0f)
    GLES20.glUniform1f(uMetallic, 0.65f)
    GLES20.glUniform1f(uRoughness, 0.18f)
    boatHullMesh.draw()
    GLES20.glEnable(GLES20.GL_CULL_FACE)

    // Boat Cockpit Deck & Racing Accent Trim
    GLES20.glUniform3fv(uBaseColor, 1, floatArrayOf(0.84f, 0.78f, 0.70f), 0)
    GLES20.glUniform1i(uMaterialType, MaterialSurface.WOOD.id)
    drawChildBox(base, 0f, 0.36f, -0.8f, 1.66f, 0.14f, 2.9f, uModel)
    boxMesh.draw()

    GLES20.glUniform3fv(uBaseColor, 1, boat.accentColor, 0)
    GLES20.glUniform1i(uMaterialType, MaterialSurface.CAR_PAINT.id)
    drawChildBox(base, 0f, 0.74f, 0.35f, 1.54f, 0.18f, 0.45f, uModel) // Helm console
    boxMesh.draw()
    drawChildBox(base, 0f, 0.88f, 1.45f, 0.56f, 0.06f, 1.85f, uModel) // Bow stripe
    boxMesh.draw()

    // Captain's Seat & Helm Wheel
    GLES20.glUniform3fv(uBaseColor, 1, floatArrayOf(0.92f, 0.90f, 0.88f), 0)
    drawChildBox(base, 0f, 0.52f, -0.45f, 0.68f, 0.20f, 0.56f, uModel)
    boxMesh.draw()
    drawChildBox(base, 0f, 0.86f, -0.72f, 0.64f, 0.54f, 0.14f, uModel)
    boxMesh.draw()

    GLES20.glUniform3fv(uBaseColor, 1, floatArrayOf(0.14f, 0.14f, 0.18f), 0)
    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, 0f, 0.96f, 0.08f)
    Matrix.rotateM(tmpMat, 0, -35f, 1f, 0f, 0f)
    Matrix.scaleM(tmpMat, 0, 0.36f, 0.06f, 0.36f)
    Matrix.multiplyMM(modelMat, 0, base, 0, tmpMat, 0)
    GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
    cylinderMesh.draw()

    // Twin Outboard Motors at Stern
    for (mx in listOf(-0.34f, 0.34f)) {
      drawChildBox(base, mx, 0.56f, -2.56f, 0.34f, 0.68f, 0.44f, uModel)
      boxMesh.draw()
    }

    // Glowing Bow & Accent Neon Lights
    GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
    GLES20.glUniform1f(uEmissive, 0.85f)
    GLES20.glUniform3fv(uBaseColor, 1, boat.accentColor, 0)
    drawChildBox(base, 0f, 0.80f, 2.45f, 0.24f, 0.14f, 0.28f, uModel)
    boxMesh.draw()

    // Water Wake Foam Trail behind moving boats!
    if (boat.wakeIntensity > 0.1f) {
      GLES20.glUniform1f(uEmissive, 0.45f)
      GLES20.glUniform3fv(uBaseColor, 1, floatArrayOf(0.86f, 0.96f, 1.0f), 0)
      val wakeLen = 2.5f + boat.wakeIntensity * 6.5f
      drawChildBox(base, -0.65f, 0.02f, -2.6f - wakeLen * 0.5f, 0.48f, 0.04f, wakeLen, uModel)
      boxMesh.draw()
      drawChildBox(base, 0.65f, 0.02f, -2.6f - wakeLen * 0.5f, 0.48f, 0.04f, wakeLen, uModel)
      boxMesh.draw()
    }

    // Visible Seated Captain (Player or AI) + Companion passenger!
    val isPlayerCaptain = (sim.activeBoat?.id == boat.id)
    if (isPlayerCaptain || boat.isAiCruise) {
      val captainAppearance = if (isPlayerCaptain) currentPlayerAppearance() else boat.appearance
      characterRenderer.drawSeatedDriver(
        parent = base,
        seatX = 0f,
        seatY = 0.56f,
        seatZ = -0.44f,
        worldTimeSec = sim.worldTimeSec,
        appearance = captainAppearance,
        uModel = uModel,
        uBaseColor = uBaseColor,
        uMaterialType = uMaterialType,
        uEmissive = uEmissive,
        uMetallic = uMetallic,
        uRoughness = uRoughness,
      )
      if (isPlayerCaptain) {
        val companion =
          sim.pedestrians.firstOrNull {
            it.isFriend && it.reactionState != NpcReactionState.KNOCKED_DOWN
          }
        if (companion != null) {
          characterRenderer.drawSeatedDriver(
            parent = base,
            seatX = 0.44f,
            seatY = 0.56f,
            seatZ = -0.50f,
            worldTimeSec = sim.worldTimeSec,
            appearance = companion.appearance,
            uModel = uModel,
            uBaseColor = uBaseColor,
            uMaterialType = uMaterialType,
            uEmissive = uEmissive,
            uMetallic = uMetallic,
            uRoughness = uRoughness,
          )
        }
      }
    }
  }

  private fun drawWater() {
    if (waterShader.programId == 0) return
    GLES20.glEnable(GLES20.GL_BLEND)
    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

    waterShader.use()
    GLES20.glUniformMatrix4fv(waterShader.loc("uViewProj"), 1, false, viewProjMat, 0)
    GLES20.glUniform1f(waterShader.loc("uTime"), sim.worldTimeSec)
    GLES20.glUniform3f(waterShader.loc("uCamPos"), sim.camPos.x, sim.camPos.y, sim.camPos.z)
    GLES20.glUniform3fv(waterShader.loc("uSunDir"), 1, sunDir, 0)
    GLES20.glUniform3fv(waterShader.loc("uSunColor"), 1, sunColor, 0)
    GLES20.glUniform3fv(waterShader.loc("uSkyZenith"), 1, skyZenith, 0)
    GLES20.glUniform3fv(waterShader.loc("uSkyHorizon"), 1, skyHorizon, 0)
    GLES20.glUniform1f(waterShader.loc("uWeatherStorm"), sim.livingWorld.weatherMode.stormFactor)
    GLES20.glUniform1f(waterShader.loc("uLightning"), sim.livingWorld.lightningFlash)

    val ripples = sim.livingWorld.waterRipples
    for (idx in 0..2) {
      val loc = waterShader.loc("uRipple$idx")
      val r = ripples.getOrNull(ripples.size - 1 - idx)
      if (r != null) {
        val progress = (r.ageSec / r.maxAgeSec).coerceIn(0f, 1f)
        val radius = 0.6f + progress * r.maxRadius
        val fade = (1f - progress) * r.intensity
        GLES20.glUniform4f(loc, r.x, r.z, radius, fade)
      } else {
        GLES20.glUniform4f(loc, 0f, 0f, 0f, 0f)
      }
    }

    Matrix.setIdentityM(modelMat, 0)
    Matrix.translateM(modelMat, 0, 0f, -0.42f, 0f)
    GLES20.glUniformMatrix4fv(waterShader.loc("uModel"), 1, false, modelMat, 0)
    GLES20.glUniform3f(waterShader.loc("uWaterTint"), 0.06f, 0.42f, 0.56f)
    GLES20.glUniform1f(waterShader.loc("uWaveAmp"), 1.0f)
    GLES20.glUniform1f(waterShader.loc("uIsOcean"), 1.0f)
    waterOceanMesh.draw()

    GLES20.glUniform1f(waterShader.loc("uWaveAmp"), 0.0f)
    GLES20.glUniform1f(waterShader.loc("uIsOcean"), 0.0f)
    for (box in sim.world.staticBoxes) {
      if (box.surface != MaterialSurface.WATER) continue
      setBoxModel(box.cx, box.cy + 0.04f, box.cz, box.sx, box.sy, box.sz, box.yawDeg)
      GLES20.glUniformMatrix4fv(waterShader.loc("uModel"), 1, false, modelMat, 0)
      GLES20.glUniform3fv(waterShader.loc("uWaterTint"), 1, box.color, 0)
      boxMesh.draw()
    }

    GLES20.glDisable(GLES20.GL_BLEND)
  }

  private fun drawGlassWindowsAndWindshields() {
    GLES20.glEnable(GLES20.GL_BLEND)
    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    GLES20.glDepthMask(false)

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

    GLES20.glUniform1f(uWind, 0f)
    GLES20.glUniform1i(uMaterialType, MaterialSurface.GLASS.id)
    GLES20.glUniform1f(uEmissive, 0f)
    GLES20.glUniform1f(uMetallic, 0.85f)
    GLES20.glUniform1f(uRoughness, 0.06f)
    GLES20.glUniform1f(uAlpha, 0.34f)

    // House glass windows
    for (g in sim.world.glassBoxes) {
      setBoxModel(g.cx, g.cy, g.cz, g.sx, g.sy, g.sz, g.yawDeg)
      GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      GLES20.glUniform3fv(uBaseColor, 1, g.color, 0)
      boxMesh.draw()
    }

    // Translucent angled car windshields so seated driver is crystal clear from outside!
    val windshieldTint = floatArrayOf(0.45f, 0.78f, 0.92f)
    GLES20.glUniform3fv(uBaseColor, 1, windshieldTint, 0)
    for (car in sim.vehicles) {
      val yawDeg = Math.toDegrees(car.yawRad.toDouble()).toFloat()
      val pitchDeg = Math.toDegrees(car.pitchRad.toDouble()).toFloat()
      val rollDeg = Math.toDegrees(car.rollRad.toDouble()).toFloat()
      Matrix.setIdentityM(tmpMat, 0)
      Matrix.translateM(tmpMat, 0, car.x, car.y + 0.04f, car.z)
      Matrix.rotateM(tmpMat, 0, yawDeg, 0f, 1f, 0f)
      Matrix.rotateM(tmpMat, 0, pitchDeg, 1f, 0f, 0f)
      Matrix.rotateM(tmpMat, 0, rollDeg, 0f, 0f, 1f)
      Matrix.translateM(tmpMat, 0, 0f, 1.18f, 0.54f)
      Matrix.rotateM(tmpMat, 0, -34f, 1f, 0f, 0f)
      Matrix.scaleM(tmpMat, 0, 1.44f, 0.56f, 0.05f)
      GLES20.glUniformMatrix4fv(uModel, 1, false, tmpMat, 0)
      boxMesh.draw()
    }

    // Museum, Grav Labs & Intellect Center geodesic domes
    for (dome in sim.world.domes) {
      GLES20.glUniform1i(uMaterialType, dome.surface.id)
      GLES20.glUniform1f(uEmissive, 0.14f)
      GLES20.glUniform1f(uMetallic, dome.metallic)
      GLES20.glUniform1f(uRoughness, dome.roughness)
      GLES20.glUniform1f(uAlpha, dome.alpha)
      GLES20.glUniform3fv(uBaseColor, 1, dome.color, 0)
      setBoxModel(dome.cx, dome.cy, dome.cz, dome.radiusX * 2f, dome.height, dome.radiusZ * 2f, 0f)
      GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      domeMesh.draw()
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
      GLES20.glUniformMatrix4fv(uModel, 1, false, tmpMat, 0)
      boxMesh.draw()
    }

    // Translucent Free Fire-style Gloo Wall tactical shields
    val glooSnapshot = synchronized(sim) { sim.glooWalls.toList() }
    val glooPlayerTint = floatArrayOf(0.24f, 0.90f, 1.0f)
    val glooBotTint = floatArrayOf(0.18f, 0.78f, 0.96f)
    val glooRibTint = floatArrayOf(0.84f, 0.98f, 1.0f)
    for (gw in glooSnapshot) {
      val hpRatio = (gw.hp / gw.maxHp.coerceAtLeast(1f)).coerceIn(0.15f, 1f)
      val yawDeg = Math.toDegrees(gw.yawRad.toDouble()).toFloat()
      val baseTint = if (gw.isPlayerOwned) glooPlayerTint else glooBotTint

      // Main translucent ice-shield panel
      GLES20.glUniform1i(uMaterialType, MaterialSurface.GLASS.id)
      GLES20.glUniform1f(uEmissive, 0.25f + 0.35f * hpRatio)
      GLES20.glUniform1f(uMetallic, 0.45f)
      GLES20.glUniform1f(uRoughness, 0.14f)
      GLES20.glUniform1f(uAlpha, 0.45f + 0.30f * hpRatio)
      GLES20.glUniform3fv(uBaseColor, 1, baseTint, 0)
      setBoxModel(gw.x, gw.y + 1.075f, gw.z, 3.30f, 2.15f, 0.24f, yawDeg)
      GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      boxMesh.draw()

      // Glowing structural top rim & armored center brace
      GLES20.glUniform1f(uEmissive, 0.55f + 0.35f * hpRatio)
      GLES20.glUniform1f(uAlpha, 0.78f)
      GLES20.glUniform3fv(uBaseColor, 1, glooRibTint, 0)
      setBoxModel(gw.x, gw.y + 2.12f, gw.z, 3.38f, 0.12f, 0.30f, yawDeg)
      GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      boxMesh.draw()

      setBoxModel(gw.x, gw.y + 1.05f, gw.z, 0.22f, 2.05f, 0.30f, yawDeg)
      GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      boxMesh.draw()
    }

    GLES20.glDepthMask(true)
    GLES20.glDisable(GLES20.GL_BLEND)

    expandedRenderer.drawBlendedLivingEffects(
      sim = sim,
      uModel = uModel,
      uBaseColor = uBaseColor,
      uMaterialType = uMaterialType,
      uEmissive = uEmissive,
      uMetallic = uMetallic,
      uRoughness = uRoughness,
      uAlpha = uAlpha,
    )
  }

  private fun bindCommonWorldUniforms() {
    GLES20.glUniformMatrix4fv(worldShader.loc("uViewProj"), 1, false, viewProjMat, 0)
    GLES20.glUniformMatrix4fv(worldShader.loc("uLightViewProj"), 1, false, lightViewProjMat, 0)
    GLES20.glUniform1f(worldShader.loc("uTime"), sim.worldTimeSec)
    GLES20.glUniform3f(worldShader.loc("uCamPos"), sim.camPos.x, sim.camPos.y, sim.camPos.z)
    GLES20.glUniform3fv(worldShader.loc("uSunDir"), 1, sunDir, 0)
    GLES20.glUniform3fv(worldShader.loc("uSunColor"), 1, sunColor, 0)
    GLES20.glUniform3fv(worldShader.loc("uSkyZenith"), 1, skyZenith, 0)
    GLES20.glUniform3fv(worldShader.loc("uSkyHorizon"), 1, skyHorizon, 0)
    GLES20.glUniform1f(worldShader.loc("uNightFactor"), nightFactor)

    val car = sim.activeVehicle ?: sim.vehicles.firstOrNull()
    if (car != null && (sim.activeVehicle != null || nightFactor > 0.1f)) {
      val fx = sin(car.yawRad)
      val fz = cos(car.yawRad)
      GLES20.glUniform3f(worldShader.loc("uHeadlightPos"), car.x + fx * 2.0f, car.y + 0.65f, car.z + fz * 2.0f)
      GLES20.glUniform3f(worldShader.loc("uHeadlightDir"), fx, -0.14f, fz)
      GLES20.glUniform1f(worldShader.loc("uHeadlightOn"), 1f)
    } else {
      GLES20.glUniform1f(worldShader.loc("uHeadlightOn"), 0f)
    }

    if (hasShadowMap) {
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, shadowDepthTex)
      GLES20.glUniform1i(worldShader.loc("uShadowMap"), 0)
      GLES20.glUniform1f(worldShader.loc("uHasShadowMap"), 1f)
    } else {
      GLES20.glUniform1f(worldShader.loc("uHasShadowMap"), 0f)
    }
    val uUseBaseTex = worldShader.loc("uUseBaseTex")
    if (uUseBaseTex >= 0) {
      GLES20.glUniform1f(uUseBaseTex, 0f)
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
    GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
  }

  private fun drawParachuteCanopy(
    px: Float,
    py: Float,
    pz: Float,
    yawRad: Float,
    uModel: Int,
    uBaseColor: Int,
    uMaterialType: Int,
    uEmissive: Int,
    uMetallic: Int,
    uRoughness: Int,
  ) {
    val yawDeg = Math.toDegrees(yawRad.toDouble()).toFloat()
    val swayRoll = sin(sim.worldTimeSec * 2.4f) * 4.5f
    val base = FloatArray(16)
    Matrix.setIdentityM(base, 0)
    Matrix.translateM(base, 0, px, py, pz)
    Matrix.rotateM(base, 0, yawDeg, 0f, 1f, 0f)
    Matrix.rotateM(base, 0, swayRoll, 0f, 0f, 1f)

    // 4 Riser cords connecting hands/shoulders up to canopy rim
    GLES20.glUniform1i(uMaterialType, MaterialSurface.SLATE.id)
    GLES20.glUniform1f(uEmissive, 0f)
    GLES20.glUniform1f(uMetallic, 0.25f)
    GLES20.glUniform1f(uRoughness, 0.65f)
    GLES20.glUniform3fv(uBaseColor, 1, floatArrayOf(0.86f, 0.88f, 0.92f), 0)

    val cordOffsets =
      arrayOf(
        floatArrayOf(-0.85f, 2.75f, 0.42f, 14f, -24f),
        floatArrayOf(0.85f, 2.75f, 0.42f, 14f, 24f),
        floatArrayOf(-0.85f, 2.75f, -0.42f, -14f, -24f),
        floatArrayOf(0.85f, 2.75f, -0.42f, -14f, 24f),
      )
    for (c in cordOffsets) {
      Matrix.setIdentityM(tmpMat, 0)
      Matrix.translateM(tmpMat, 0, c[0], c[1], c[2])
      Matrix.rotateM(tmpMat, 0, c[3], 1f, 0f, 0f)
      Matrix.rotateM(tmpMat, 0, c[4], 0f, 0f, 1f)
      Matrix.scaleM(tmpMat, 0, 0.035f, 2.45f, 0.035f)
      Matrix.multiplyMM(modelMat, 0, base, 0, tmpMat, 0)
      GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      cylinderMesh.draw()
    }

    // 5-panel curved Vice Horizon wing canopy overhead
    val panelColors =
      arrayOf(
        floatArrayOf(0.98f, 0.22f, 0.56f), // Neon Pink wingtip
        floatArrayOf(0.98f, 0.74f, 0.20f), // Golden Hour
        floatArrayOf(0.16f, 0.88f, 0.96f), // Electric Cyan center
        floatArrayOf(0.98f, 0.74f, 0.20f), // Golden Hour
        floatArrayOf(0.98f, 0.22f, 0.56f), // Neon Pink wingtip
      )
    val panelX = floatArrayOf(-1.82f, -0.94f, 0f, 0.94f, 1.82f)
    val panelY = floatArrayOf(3.86f, 4.18f, 4.30f, 4.18f, 3.86f)
    val panelRoll = floatArrayOf(28f, 14f, 0f, -14f, -28f)

    GLES20.glDisable(GLES20.GL_CULL_FACE)
    GLES20.glUniform1i(uMaterialType, MaterialSurface.CLOTH.id)
    GLES20.glUniform1f(uMetallic, 0.05f)
    GLES20.glUniform1f(uRoughness, 0.55f)
    for (i in 0 until 5) {
      GLES20.glUniform3fv(uBaseColor, 1, panelColors[i], 0)
      GLES20.glUniform1f(uEmissive, if (i == 2) 0.18f else 0.08f)
      Matrix.setIdentityM(tmpMat, 0)
      Matrix.translateM(tmpMat, 0, panelX[i], panelY[i], 0f)
      Matrix.rotateM(tmpMat, 0, panelRoll[i], 0f, 0f, 1f)
      Matrix.scaleM(tmpMat, 0, 1.02f, 0.14f, 2.15f)
      Matrix.multiplyMM(modelMat, 0, base, 0, tmpMat, 0)
      GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      boxMesh.draw()
    }
    GLES20.glUniform1f(uEmissive, 0f)
    GLES20.glEnable(GLES20.GL_CULL_FACE)
  }

  private fun drawTransportAirplane(
    ax: Float,
    ay: Float,
    az: Float,
    yawRad: Float,
    uModel: Int,
    uBaseColor: Int,
    uMaterialType: Int,
    uEmissive: Int,
    uMetallic: Int,
    uRoughness: Int,
  ) {
    val yawDeg = Math.toDegrees(yawRad.toDouble()).toFloat()
    val bankDeg = sin(sim.worldTimeSec * 1.1f) * 3.2f
    val pitchDeg = sin(sim.worldTimeSec * 0.7f) * 1.2f
    val base = FloatArray(16)
    Matrix.setIdentityM(base, 0)
    Matrix.translateM(base, 0, ax, ay, az)
    Matrix.rotateM(base, 0, yawDeg, 0f, 1f, 0f)
    Matrix.rotateM(base, 0, pitchDeg, 1f, 0f, 0f)
    Matrix.rotateM(base, 0, bankDeg, 0f, 0f, 1f)

    val fuselageSilver = floatArrayOf(0.92f, 0.94f, 0.97f)
    val bellyAlloy = floatArrayOf(0.56f, 0.60f, 0.66f)
    val darkSlate = floatArrayOf(0.15f, 0.17f, 0.22f)
    val liveryNavy = floatArrayOf(0.12f, 0.24f, 0.48f)
    val liveryAccent = floatArrayOf(0.96f, 0.28f, 0.48f)
    val cockpitDark = floatArrayOf(0.10f, 0.20f, 0.30f)
    val cabinGlow = floatArrayOf(0.98f, 0.92f, 0.72f)

    // 1. Main Cylindrical Pressurized Fuselage + Belly Wing-Box Fairing
    GLES20.glUniform1i(uMaterialType, MaterialSurface.CAR_PAINT.id)
    GLES20.glUniform1f(uEmissive, 0f)
    GLES20.glUniform1f(uMetallic, 0.80f)
    GLES20.glUniform1f(uRoughness, 0.20f)
    GLES20.glUniform3fv(uBaseColor, 1, fuselageSilver, 0)

    Matrix.setIdentityM(tmpMat, 0)
    Matrix.rotateM(tmpMat, 0, 90f, 1f, 0f, 0f)
    Matrix.scaleM(tmpMat, 0, 2.75f, 14.8f, 2.75f)
    Matrix.multiplyMM(modelMat, 0, base, 0, tmpMat, 0)
    GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
    cylinderMesh.draw()

    GLES20.glUniform3fv(uBaseColor, 1, bellyAlloy, 0)
    drawChildBox(base, 0f, -0.78f, 0.2f, 2.35f, 0.90f, 8.2f, uModel)
    boxMesh.draw()

    // 2. Sculpted Forward Nose Cone (+Z) & Tapered Tailcone (-Z)
    GLES20.glUniform3fv(uBaseColor, 1, fuselageSilver, 0)
    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, 0f, -0.08f, 7.25f)
    Matrix.scaleM(tmpMat, 0, 2.66f, 2.52f, 4.4f)
    Matrix.multiplyMM(modelMat, 0, base, 0, tmpMat, 0)
    GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
    sphereMesh.draw()

    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, 0f, 0.14f, -7.2f)
    Matrix.scaleM(tmpMat, 0, 2.48f, 2.30f, 4.2f)
    Matrix.multiplyMM(modelMat, 0, base, 0, tmpMat, 0)
    GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
    sphereMesh.draw()

    // Dark Nose Radome Tip
    GLES20.glUniform3fv(uBaseColor, 1, darkSlate, 0)
    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, 0f, -0.14f, 9.2f)
    Matrix.scaleM(tmpMat, 0, 0.95f, 0.90f, 1.3f)
    Matrix.multiplyMM(modelMat, 0, base, 0, tmpMat, 0)
    GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
    sphereMesh.draw()

    // 3. Multi-Pane Flight Deck Cockpit Windshield + Airline Livery Cheatline & Cabin Windows
    GLES20.glUniform1i(uMaterialType, MaterialSurface.GLASS.id)
    GLES20.glUniform1f(uEmissive, 0.28f)
    GLES20.glUniform3fv(uBaseColor, 1, cockpitDark, 0)
    for (wx in listOf(-0.42f, 0.42f)) {
      Matrix.setIdentityM(tmpMat, 0)
      Matrix.translateM(tmpMat, 0, wx, 0.56f, 7.65f)
      Matrix.rotateM(tmpMat, 0, if (wx < 0f) -24f else 24f, 0f, 1f, 0f)
      Matrix.rotateM(tmpMat, 0, -28f, 1f, 0f, 0f)
      Matrix.scaleM(tmpMat, 0, 0.68f, 0.48f, 0.52f)
      Matrix.multiplyMM(modelMat, 0, base, 0, tmpMat, 0)
      GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      boxMesh.draw()
    }

    // Airline Cheatline Striping
    GLES20.glUniform1i(uMaterialType, MaterialSurface.CAR_PAINT.id)
    GLES20.glUniform1f(uEmissive, 0f)
    GLES20.glUniform3fv(uBaseColor, 1, liveryNavy, 0)
    drawChildBox(base, 0f, 0.08f, 0.1f, 2.80f, 0.34f, 13.8f, uModel)
    boxMesh.draw()
    GLES20.glUniform3fv(uBaseColor, 1, liveryAccent, 0)
    drawChildBox(base, 0f, -0.14f, 0.1f, 2.78f, 0.14f, 13.6f, uModel)
    boxMesh.draw()

    // 10 Illuminated Cabin Windows along Left & Right Fuselage
    GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
    GLES20.glUniform1f(uEmissive, 0.72f)
    GLES20.glUniform3fv(uBaseColor, 1, cabinGlow, 0)
    for (wIdx in 0 until 10) {
      val wz = 4.6f - wIdx * 1.05f
      drawChildBox(base, 0f, 0.46f, wz, 2.78f, 0.24f, 0.42f, uModel)
      boxMesh.draw()
    }

    // 4. Sculpted Swept Airfoil Main Wings + Upturned Winglets + Flap Track Fairings
    GLES20.glDisable(GLES20.GL_CULL_FACE)
    GLES20.glUniform1i(uMaterialType, MaterialSurface.CAR_PAINT.id)
    GLES20.glUniform1f(uEmissive, 0f)
    GLES20.glUniform1f(uMetallic, 0.75f)
    GLES20.glUniform1f(uRoughness, 0.24f)
    GLES20.glUniform3fv(uBaseColor, 1, fuselageSilver, 0)

    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, -1.0f, 0.64f, 0.4f)
    Matrix.scaleM(tmpMat, 0, 10.6f, 1.05f, 1.25f)
    Matrix.multiplyMM(modelMat, 0, base, 0, tmpMat, 0)
    GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
    leftWingMesh.draw()

    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, 1.0f, 0.64f, 0.4f)
    Matrix.scaleM(tmpMat, 0, 10.6f, 1.05f, 1.25f)
    Matrix.multiplyMM(modelMat, 0, base, 0, tmpMat, 0)
    GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
    rightWingMesh.draw()

    // Swept Horizontal Stabilizers at Tail
    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, -0.35f, 0.55f, -6.6f)
    Matrix.scaleM(tmpMat, 0, 3.8f, 0.65f, 0.68f)
    Matrix.multiplyMM(modelMat, 0, base, 0, tmpMat, 0)
    GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
    leftWingMesh.draw()

    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, 0.35f, 0.55f, -6.6f)
    Matrix.scaleM(tmpMat, 0, 3.8f, 0.65f, 0.68f)
    Matrix.multiplyMM(modelMat, 0, base, 0, tmpMat, 0)
    GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
    rightWingMesh.draw()
    GLES20.glEnable(GLES20.GL_CULL_FACE)

    // Swept Vertical Tail Stabilizer Fin + Upturned Blended Winglets
    GLES20.glUniform3fv(uBaseColor, 1, liveryNavy, 0)
    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, 0f, 2.55f, -6.3f)
    Matrix.rotateM(tmpMat, 0, -24f, 1f, 0f, 0f)
    Matrix.scaleM(tmpMat, 0, 0.32f, 3.65f, 2.2f)
    Matrix.multiplyMM(modelMat, 0, base, 0, tmpMat, 0)
    GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
    boxMesh.draw()

    for (wx in listOf(-11.6f, 11.6f)) {
      Matrix.setIdentityM(tmpMat, 0)
      Matrix.translateM(tmpMat, 0, wx, 1.90f, -1.65f)
      Matrix.rotateM(tmpMat, 0, if (wx > 0f) -16f else 16f, 0f, 0f, 1f)
      Matrix.scaleM(tmpMat, 0, 0.16f, 1.35f, 1.15f)
      Matrix.multiplyMM(modelMat, 0, base, 0, tmpMat, 0)
      GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      boxMesh.draw()
    }

    // 5. Open Rear Paratrooper Cargo Ramp & Bright Green Jump Light
    GLES20.glUniform1i(uMaterialType, MaterialSurface.SLATE.id)
    GLES20.glUniform3fv(uBaseColor, 1, darkSlate, 0)
    Matrix.setIdentityM(tmpMat, 0)
    Matrix.translateM(tmpMat, 0, 0f, -0.95f, -8.3f)
    Matrix.rotateM(tmpMat, 0, 22f, 1f, 0f, 0f)
    Matrix.scaleM(tmpMat, 0, 1.80f, 0.16f, 2.4f)
    Matrix.multiplyMM(modelMat, 0, base, 0, tmpMat, 0)
    GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
    boxMesh.draw()

    GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
    GLES20.glUniform1f(uEmissive, 1.3f)
    GLES20.glUniform3fv(uBaseColor, 1, floatArrayOf(0.24f, 0.96f, 0.56f), 0)
    drawChildBox(base, 0f, 0.35f, -7.8f, 1.45f, 0.24f, 0.24f, uModel)
    boxMesh.draw()

    // 6. Four Underwing Jet/Turboprop Engine Pods + Glowing Intake Cores + Spinning Blades
    val propAngle = (sim.worldTimeSec * 1080f) % 360f
    val enginePositions =
      arrayOf(
        floatArrayOf(-3.6f, 0.18f, 1.45f),
        floatArrayOf(3.6f, 0.18f, 1.45f),
        floatArrayOf(-7.0f, 0.35f, 0.15f),
        floatArrayOf(7.0f, 0.35f, 0.15f),
      )
    for (ep in enginePositions) {
      // Pylon strut connecting wing underside to nacelle
      GLES20.glUniform1i(uMaterialType, MaterialSurface.SLATE.id)
      GLES20.glUniform1f(uEmissive, 0f)
      GLES20.glUniform3fv(uBaseColor, 1, bellyAlloy, 0)
      drawChildBox(base, ep[0], ep[1] + 0.42f, ep[2] - 0.15f, 0.24f, 0.55f, 1.45f, uModel)
      boxMesh.draw()

      // Cowling nacelle body
      GLES20.glUniform3fv(uBaseColor, 1, darkSlate, 0)
      Matrix.setIdentityM(tmpMat, 0)
      Matrix.translateM(tmpMat, 0, ep[0], ep[1], ep[2])
      Matrix.rotateM(tmpMat, 0, 90f, 1f, 0f, 0f)
      Matrix.scaleM(tmpMat, 0, 0.96f, 2.55f, 0.96f)
      Matrix.multiplyMM(modelMat, 0, base, 0, tmpMat, 0)
      GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      cylinderMesh.draw()

      // Engine turbine afterburner glow
      GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
      GLES20.glUniform1f(uEmissive, 0.9f)
      GLES20.glUniform3fv(uBaseColor, 1, floatArrayOf(1.0f, 0.56f, 0.18f), 0)
      drawChildBox(base, ep[0], ep[1], ep[2] - 1.30f, 0.44f, 0.44f, 0.16f, uModel)
      sphereMesh.draw()

      // Spinning propeller / fan blades
      GLES20.glUniform1i(uMaterialType, MaterialSurface.SLATE.id)
      GLES20.glUniform1f(uEmissive, 0f)
      GLES20.glUniform3fv(uBaseColor, 1, floatArrayOf(0.84f, 0.88f, 0.94f), 0)
      Matrix.setIdentityM(tmpMat, 0)
      Matrix.translateM(tmpMat, 0, ep[0], ep[1], ep[2] + 1.32f)
      Matrix.rotateM(tmpMat, 0, propAngle, 0f, 0f, 1f)
      Matrix.scaleM(tmpMat, 0, 1.95f, 0.14f, 0.06f)
      Matrix.multiplyMM(modelMat, 0, base, 0, tmpMat, 0)
      GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      boxMesh.draw()
    }

    // 7. Pulsing Anti-Collision Beacon + Port/Starboard Nav Strobe Lights
    val strobeOn = sin(sim.worldTimeSec * 9f) > 0.25f
    GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
    GLES20.glUniform1f(uEmissive, if (strobeOn) 1.6f else 0.2f)
    GLES20.glUniform3fv(uBaseColor, 1, floatArrayOf(1.0f, 0.14f, 0.18f), 0)
    drawChildBox(base, 0f, 1.48f, 0.8f, 0.24f, 0.18f, 0.24f, uModel)
    sphereMesh.draw()

    GLES20.glUniform1f(uEmissive, 1.25f)
    drawChildBox(base, -11.75f, 2.55f, -1.65f, 0.26f, 0.26f, 0.36f, uModel)
    sphereMesh.draw()
    GLES20.glUniform3fv(uBaseColor, 1, floatArrayOf(0.18f, 0.96f, 0.54f), 0)
    drawChildBox(base, 11.75f, 2.55f, -1.65f, 0.26f, 0.26f, 0.36f, uModel)
    sphereMesh.draw()
    GLES20.glUniform1f(uEmissive, 0f)
  }

  private fun drawWorldLootItems(
    uModel: Int,
    uBaseColor: Int,
    uMaterialType: Int,
    uEmissive: Int,
    uMetallic: Int,
    uRoughness: Int,
  ) {
    val lootSnapshot = synchronized(sim) { sim.worldLootItems.toList() }
    if (lootSnapshot.isEmpty()) return

    val goldColor = floatArrayOf(1.0f, 0.80f, 0.18f)
    val cyanColor = floatArrayOf(0.18f, 0.90f, 0.98f)
    val mintColor = floatArrayOf(0.22f, 0.94f, 0.62f)
    val whiteColor = floatArrayOf(0.95f, 0.96f, 0.98f)
    val redColor = floatArrayOf(0.96f, 0.20f, 0.24f)
    val gunDark = floatArrayOf(0.14f, 0.15f, 0.19f)
    val gunSteel = floatArrayOf(0.38f, 0.42f, 0.48f)
    val kevlarSlate = floatArrayOf(0.18f, 0.22f, 0.30f)
    val titanObsidian = floatArrayOf(0.12f, 0.13f, 0.18f)
    val itemBase = FloatArray(16)

    for (item in lootSnapshot) {
      val ringColor =
        when (item.kind.category) {
          LootCategory.CURRENCY -> goldColor
          LootCategory.MEDICAL -> mintColor
          LootCategory.WEAPON -> goldColor
          LootCategory.VEST, LootCategory.HELMET ->
            if (item.kind.armorLevel >= 4) goldColor else cyanColor
          LootCategory.GLOO_WALL -> cyanColor
        }

      // Ground halo ring under each loot pickup
      val pulse = 0.5f + 0.5f * sin(sim.worldTimeSec * 4.0f + item.x)
      GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
      GLES20.glUniform1f(uEmissive, 0.65f + pulse * 0.45f)
      GLES20.glUniform1f(uMetallic, 0f)
      GLES20.glUniform1f(uRoughness, 0.3f)
      GLES20.glUniform3fv(uBaseColor, 1, ringColor, 0)
      setBoxModel(item.x, item.y + 0.06f, item.z, 0.92f + pulse * 0.14f, 0.03f, 0.92f + pulse * 0.14f, 0f)
      GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
      cylinderMesh.draw()

      val bobY = item.y + 0.42f + sin(sim.worldTimeSec * 3.1f + item.x * 0.7f) * 0.08f
      val spinDeg = (sim.worldTimeSec * 72f + item.z * 18f) % 360f
      Matrix.setIdentityM(itemBase, 0)
      Matrix.translateM(itemBase, 0, item.x, bobY, item.z)
      Matrix.rotateM(itemBase, 0, spinDeg, 0f, 1f, 0f)

      when (item.kind.category) {
        LootCategory.CURRENCY -> {
          GLES20.glUniform1i(uMaterialType, MaterialSurface.SLATE.id)
          GLES20.glUniform1f(uEmissive, 0.32f)
          GLES20.glUniform1f(uMetallic, 0.92f)
          GLES20.glUniform1f(uRoughness, 0.16f)
          GLES20.glUniform3fv(uBaseColor, 1, goldColor, 0)
          Matrix.setIdentityM(tmpMat, 0)
          Matrix.rotateM(tmpMat, 0, 90f, 1f, 0f, 0f)
          Matrix.scaleM(tmpMat, 0, 0.46f, 0.10f, 0.46f)
          Matrix.multiplyMM(modelMat, 0, itemBase, 0, tmpMat, 0)
          GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0)
          cylinderMesh.draw()
        }
        LootCategory.MEDICAL -> {
          GLES20.glUniform1i(uMaterialType, MaterialSurface.CAR_PAINT.id)
          GLES20.glUniform1f(uEmissive, 0.12f)
          GLES20.glUniform1f(uMetallic, 0.15f)
          GLES20.glUniform1f(uRoughness, 0.30f)
          GLES20.glUniform3fv(uBaseColor, 1, whiteColor, 0)
          drawChildBox(itemBase, 0f, 0f, 0f, 0.48f, 0.34f, 0.24f, uModel)
          boxMesh.draw()

          GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
          GLES20.glUniform1f(uEmissive, 0.75f)
          GLES20.glUniform3fv(uBaseColor, 1, redColor, 0)
          drawChildBox(itemBase, 0f, 0f, 0.13f, 0.24f, 0.08f, 0.04f, uModel)
          boxMesh.draw()
          drawChildBox(itemBase, 0f, 0f, 0.13f, 0.08f, 0.24f, 0.04f, uModel)
          boxMesh.draw()
        }
        LootCategory.WEAPON -> {
          GLES20.glUniform1i(uMaterialType, MaterialSurface.SLATE.id)
          GLES20.glUniform1f(uEmissive, 0.12f)
          GLES20.glUniform1f(uMetallic, 0.80f)
          GLES20.glUniform1f(uRoughness, 0.24f)
          GLES20.glUniform3fv(uBaseColor, 1, gunDark, 0)
          drawChildBox(itemBase, 0f, 0.04f, 0f, 0.12f, 0.14f, 0.56f, uModel)
          boxMesh.draw()
          GLES20.glUniform3fv(uBaseColor, 1, gunSteel, 0)
          drawChildBox(itemBase, 0f, 0.06f, 0.36f, 0.055f, 0.055f, 0.30f, uModel)
          boxMesh.draw()
          drawChildBox(itemBase, 0f, -0.09f, 0.04f, 0.07f, 0.20f, 0.10f, uModel)
          boxMesh.draw()
        }
        LootCategory.VEST -> {
          val isLv4 = item.kind.armorLevel >= 4
          GLES20.glUniform1i(uMaterialType, MaterialSurface.SLATE.id)
          GLES20.glUniform1f(uEmissive, 0.10f)
          GLES20.glUniform1f(uMetallic, 0.65f)
          GLES20.glUniform1f(uRoughness, 0.28f)
          GLES20.glUniform3fv(uBaseColor, 1, if (isLv4) titanObsidian else kevlarSlate, 0)
          drawChildBox(itemBase, 0f, 0f, 0f, 0.48f, 0.52f, 0.26f, uModel)
          boxMesh.draw()
          GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
          GLES20.glUniform1f(uEmissive, 0.65f)
          GLES20.glUniform3fv(uBaseColor, 1, if (isLv4) goldColor else cyanColor, 0)
          drawChildBox(itemBase, 0f, 0.06f, 0.14f, 0.34f, 0.18f, 0.04f, uModel)
          boxMesh.draw()
        }
        LootCategory.HELMET -> {
          val isLv4 = item.kind.armorLevel >= 4
          GLES20.glUniform1i(uMaterialType, MaterialSurface.SLATE.id)
          GLES20.glUniform1f(uEmissive, 0.10f)
          GLES20.glUniform1f(uMetallic, 0.70f)
          GLES20.glUniform1f(uRoughness, 0.24f)
          GLES20.glUniform3fv(uBaseColor, 1, if (isLv4) titanObsidian else kevlarSlate, 0)
          drawChildBox(itemBase, 0f, 0.04f, 0f, 0.44f, 0.38f, 0.46f, uModel)
          sphereMesh.draw()
          GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
          GLES20.glUniform1f(uEmissive, 0.72f)
          GLES20.glUniform3fv(uBaseColor, 1, if (isLv4) goldColor else cyanColor, 0)
          drawChildBox(itemBase, 0f, 0.02f, 0.19f, 0.34f, 0.11f, 0.10f, uModel)
          boxMesh.draw()
        }
        LootCategory.GLOO_WALL -> {
          GLES20.glUniform1i(uMaterialType, MaterialSurface.CAR_PAINT.id)
          GLES20.glUniform1f(uEmissive, 0.24f)
          GLES20.glUniform1f(uMetallic, 0.55f)
          GLES20.glUniform1f(uRoughness, 0.20f)
          GLES20.glUniform3fv(uBaseColor, 1, cyanColor, 0)
          drawChildBox(itemBase, 0f, 0f, 0f, 0.44f, 0.52f, 0.22f, uModel)
          boxMesh.draw()

          GLES20.glUniform1i(uMaterialType, MaterialSurface.EMISSIVE.id)
          GLES20.glUniform1f(uEmissive, 0.82f)
          GLES20.glUniform3fv(uBaseColor, 1, whiteColor, 0)
          drawChildBox(itemBase, 0f, 0.04f, 0.12f, 0.30f, 0.28f, 0.05f, uModel)
          boxMesh.draw()
        }
      }
      GLES20.glUniform1f(uEmissive, 0f)
    }
  }

  private fun setVec3(dst: FloatArray, x: Float, y: Float, z: Float) {
    dst[0] = x
    dst[1] = y
    dst[2] = z
  }
}
