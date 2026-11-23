package com.example.game.gl

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

class ViceGlSurfaceView(context: Context, renderer: ViceGlRenderer) : GLSurfaceView(context) {
  init {
    setEGLContextClientVersion(2)
    setEGLConfigChooser(ResilientEglConfigChooser())
    preserveEGLContextOnPause = false
    setRenderer(renderer)
    renderMode = RENDERMODE_CONTINUOUSLY
  }
}

/**
 * Selects the best available OpenGL ES 2/3 compatible EGLConfig without crashing with
 * "IllegalArgumentException: No config chosen" on devices/emulators that expose 16-bit depth,
 * non-alpha, or non-exact EGL configurations.
 */
private class ResilientEglConfigChooser : GLSurfaceView.EGLConfigChooser {
  override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
    val numConfigs = IntArray(1)
    val esSpec =
      intArrayOf(
        EGL10.EGL_RENDERABLE_TYPE,
        4, // EGL_OPENGL_ES2_BIT (compatible with GLES 2.0 & 3.0 on Android)
        EGL10.EGL_SURFACE_TYPE,
        EGL10.EGL_WINDOW_BIT,
        EGL10.EGL_NONE,
      )
    val anySpec = intArrayOf(EGL10.EGL_NONE)

    var specToUse = esSpec
    if (!egl.eglChooseConfig(display, specToUse, null, 0, numConfigs) || numConfigs[0] <= 0) {
      specToUse = anySpec
      if (!egl.eglChooseConfig(display, specToUse, null, 0, numConfigs) || numConfigs[0] <= 0) {
        throw IllegalArgumentException("No EGL configs available on device")
      }
    }

    val total = numConfigs[0]
    val configs = arrayOfNulls<EGLConfig>(total)
    if (!egl.eglChooseConfig(display, specToUse, configs, total, numConfigs)) {
      throw IllegalArgumentException("Failed to enumerate EGL configs")
    }

    val value = IntArray(1)
    fun attr(cfg: EGLConfig, attribute: Int): Int {
      return if (egl.eglGetConfigAttrib(display, cfg, attribute, value)) value[0] else 0
    }

    var bestConfig: EGLConfig? = null
    var bestScore = Int.MIN_VALUE

    for (cfg in configs) {
      if (cfg == null) continue
      val r = attr(cfg, EGL10.EGL_RED_SIZE)
      val g = attr(cfg, EGL10.EGL_GREEN_SIZE)
      val b = attr(cfg, EGL10.EGL_BLUE_SIZE)
      val a = attr(cfg, EGL10.EGL_ALPHA_SIZE)
      val d = attr(cfg, EGL10.EGL_DEPTH_SIZE)
      val caveat = attr(cfg, EGL10.EGL_CONFIG_CAVEAT)
      val surfaceType = attr(cfg, EGL10.EGL_SURFACE_TYPE)

      if ((surfaceType and EGL10.EGL_WINDOW_BIT) == 0) continue

      var score = 0
      if (d >= 24) score += 4000 else if (d >= 16) score += 2000 else if (d > 0) score += 500
      if (r >= 8 && g >= 8 && b >= 8) score += 1000 else if (r >= 5 && g >= 6 && b >= 5) score += 300
      if (a >= 8) score += 200
      if (caveat == EGL10.EGL_NONE) score += 100
      if (caveat == EGL10.EGL_SLOW_CONFIG) score -= 500

      if (score > bestScore) {
        bestScore = score
        bestConfig = cfg
      }
    }

    val chosen = bestConfig ?: configs.firstOrNull { it != null }
      ?: throw IllegalArgumentException("No usable EGL config found among $total configs")
    Log.i(
      "ViceGL",
      "Chosen EGL config: R=${attr(chosen, EGL10.EGL_RED_SIZE)} G=${attr(chosen, EGL10.EGL_GREEN_SIZE)} " +
        "B=${attr(chosen, EGL10.EGL_BLUE_SIZE)} A=${attr(chosen, EGL10.EGL_ALPHA_SIZE)} " +
        "D=${attr(chosen, EGL10.EGL_DEPTH_SIZE)}",
    )
    return chosen
  }
}

@Composable
fun Vice3DViewport(
  renderer: ViceGlRenderer,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val glView = remember(context, renderer) {
    ViceGlSurfaceView(context, renderer).apply {
      layoutParams =
        ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }
  }

  DisposableEffect(lifecycleOwner, glView) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> glView.onResume()
        Lifecycle.Event.ON_PAUSE -> glView.onPause()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
      glView.onResume()
    }
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      glView.onPause()
    }
  }

  AndroidView(
    modifier = modifier,
    factory = { glView },
  )
}

