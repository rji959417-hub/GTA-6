package com.example.game.gl

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

class ViceGlSurfaceView(context: Context, renderer: ViceGlRenderer) : GLSurfaceView(context) {
  init {
    setEGLContextClientVersion(3)
    setEGLConfigChooser(8, 8, 8, 8, 24, 0)
    preserveEGLContextOnPause = true
    setRenderer(renderer)
    renderMode = RENDERMODE_CONTINUOUSLY
  }
}

@Composable
fun Vice3DViewport(
  renderer: ViceGlRenderer,
  modifier: Modifier = Modifier,
) {
  AndroidView(
    modifier = modifier,
    factory = { ctx ->
      ViceGlSurfaceView(ctx, renderer).apply {
        layoutParams =
          ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
          )
      }
    },
  )
}
