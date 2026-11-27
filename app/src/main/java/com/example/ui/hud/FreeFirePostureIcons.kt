package com.example.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.PalmMint

enum class FreeFirePostureKind(val label: String) {
  RUN("Run"),
  JUMP("Jump"),
  SIT("Sit"),
  SLEEP("Sleep"),
}

@Composable
fun FreeFireCircleTapButton(
  kind: FreeFirePostureKind,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  diameter: Dp = 56.dp,
  isActive: Boolean = false,
  activeAccent: Color = PalmMint,
  testTagName: String = "",
) {
  var pressed by remember { mutableStateOf(false) }
  val lit = pressed || isActive

  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .size(diameter)
        .clip(CircleShape)
        .background(
          if (isActive) activeAccent.copy(alpha = 0.28f)
          else if (pressed) Color.White.copy(alpha = 0.26f)
          else Color(0xFF13171F).copy(alpha = 0.62f)
        )
        .border(
          width = if (lit) 1.8.dp else 1.2.dp,
          color = if (isActive) activeAccent else if (pressed) Color.White else Color.White.copy(alpha = 0.38f),
          shape = CircleShape,
        )
        .semantics { contentDescription = kind.label }
        .then(if (testTagName.isNotEmpty()) Modifier.testTag(testTagName) else Modifier)
        .pointerInput(onClick) {
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            pressed = true
            var fired = false
            while (true) {
              val event = awaitPointerEvent()
              val change = event.changes.firstOrNull { it.id == down.id }
              if (change == null || !change.pressed) {
                fired = true
                break
              }
              change.consume()
            }
            pressed = false
            if (fired) onClick()
          }
        },
  ) {
    FreeFirePostureVectorIcon(
      kind = kind,
      tint = if (isActive) Color.White else Color.White.copy(alpha = 0.96f),
      modifier = Modifier.size(diameter * 0.68f),
    )
  }
}

@Composable
fun FreeFirePostureVectorIcon(
  kind: FreeFirePostureKind,
  tint: Color = Color.White,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier) {
    when (kind) {
      FreeFirePostureKind.RUN -> drawRunSilhouette(tint)
      FreeFirePostureKind.JUMP -> drawJumpSilhouette(tint)
      FreeFirePostureKind.SIT -> drawSitSilhouette(tint)
      FreeFirePostureKind.SLEEP -> drawSleepSilhouette(tint)
    }
  }
}

private fun DrawScope.drawRunSilhouette(tint: Color) {
  val w = size.width
  val h = size.height
  val strokeLimb = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
  val strokeTorso = Stroke(width = w * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
  val strokeGun = Stroke(width = w * 0.068f, cap = StrokeCap.Round, join = StrokeJoin.Round)

  // Head
  drawCircle(color = tint, radius = w * 0.095f, center = Offset(w * 0.56f, h * 0.21f))

  // Forward-leaning torso
  drawLine(
    color = tint,
    start = Offset(w * 0.52f, h * 0.34f),
    end = Offset(w * 0.43f, h * 0.56f),
    strokeWidth = strokeTorso.width,
    cap = StrokeCap.Round,
  )

  // Front striding leg (raised knee forward)
  val frontLeg = Path().apply {
    moveTo(w * 0.45f, h * 0.56f)
    lineTo(w * 0.65f, h * 0.64f)
    lineTo(w * 0.60f, h * 0.87f)
  }
  drawPath(frontLeg, color = tint, style = strokeLimb)

  // Back pushing leg
  val backLeg = Path().apply {
    moveTo(w * 0.42f, h * 0.56f)
    lineTo(w * 0.28f, h * 0.72f)
    lineTo(w * 0.18f, h * 0.84f)
  }
  drawPath(backLeg, color = tint, style = strokeLimb)

  // Arms holding rifle across chest
  drawLine(
    color = tint,
    start = Offset(w * 0.50f, h * 0.37f),
    end = Offset(w * 0.62f, h * 0.46f),
    strokeWidth = w * 0.09f,
    cap = StrokeCap.Round,
  )
  drawLine(
    color = tint,
    start = Offset(w * 0.47f, h * 0.38f),
    end = Offset(w * 0.38f, h * 0.49f),
    strokeWidth = w * 0.09f,
    cap = StrokeCap.Round,
  )

  // Rifle angled down-forward
  drawLine(
    color = tint,
    start = Offset(w * 0.30f, h * 0.43f),
    end = Offset(w * 0.84f, h * 0.58f),
    strokeWidth = strokeGun.width,
    cap = StrokeCap.Round,
  )
}

private fun DrawScope.drawJumpSilhouette(tint: Color) {
  val w = size.width
  val h = size.height
  val strokeLimb = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
  val strokeTorso = Stroke(width = w * 0.155f, cap = StrokeCap.Round, join = StrokeJoin.Round)
  val strokeGun = Stroke(width = w * 0.068f, cap = StrokeCap.Round, join = StrokeJoin.Round)

  // Head
  drawCircle(color = tint, radius = w * 0.095f, center = Offset(w * 0.51f, h * 0.20f))

  // Upright leaping torso
  drawLine(
    color = tint,
    start = Offset(w * 0.49f, h * 0.33f),
    end = Offset(w * 0.44f, h * 0.54f),
    strokeWidth = strokeTorso.width,
    cap = StrokeCap.Round,
  )

  // Front tucked knee high in air
  val frontLeg = Path().apply {
    moveTo(w * 0.46f, h * 0.54f)
    lineTo(w * 0.64f, h * 0.60f)
    lineTo(w * 0.54f, h * 0.78f)
  }
  drawPath(frontLeg, color = tint, style = strokeLimb)

  // Rear bent leg trailing down
  val backLeg = Path().apply {
    moveTo(w * 0.42f, h * 0.54f)
    lineTo(w * 0.30f, h * 0.70f)
    lineTo(w * 0.24f, h * 0.84f)
  }
  drawPath(backLeg, color = tint, style = strokeLimb)

  // Arms holding rifle forward
  drawLine(
    color = tint,
    start = Offset(w * 0.49f, h * 0.36f),
    end = Offset(w * 0.62f, h * 0.42f),
    strokeWidth = w * 0.09f,
    cap = StrokeCap.Round,
  )
  drawLine(
    color = tint,
    start = Offset(w * 0.36f, h * 0.42f),
    end = Offset(w * 0.85f, h * 0.46f),
    strokeWidth = strokeGun.width,
    cap = StrokeCap.Round,
  )
}

private fun DrawScope.drawSitSilhouette(tint: Color) {
  val w = size.width
  val h = size.height
  val strokeLimb = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
  val strokeTorso = Stroke(width = w * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
  val strokeGun = Stroke(width = w * 0.068f, cap = StrokeCap.Round, join = StrokeJoin.Round)

  // Lowered head
  drawCircle(color = tint, radius = w * 0.095f, center = Offset(w * 0.46f, h * 0.28f))

  // Crouched forward torso
  drawLine(
    color = tint,
    start = Offset(w * 0.43f, h * 0.40f),
    end = Offset(w * 0.33f, h * 0.62f),
    strokeWidth = strokeTorso.width,
    cap = StrokeCap.Round,
  )

  // Front bent leg planted forward
  val frontLeg = Path().apply {
    moveTo(w * 0.35f, h * 0.62f)
    lineTo(w * 0.58f, h * 0.62f)
    lineTo(w * 0.58f, h * 0.82f)
  }
  drawPath(frontLeg, color = tint, style = strokeLimb)

  // Rear knee resting back
  val backLeg = Path().apply {
    moveTo(w * 0.32f, h * 0.62f)
    lineTo(w * 0.24f, h * 0.78f)
    lineTo(w * 0.36f, h * 0.82f)
  }
  drawPath(backLeg, color = tint, style = strokeLimb)

  // Arms aiming rifle forward horizontally
  drawLine(
    color = tint,
    start = Offset(w * 0.43f, h * 0.43f),
    end = Offset(w * 0.58f, h * 0.47f),
    strokeWidth = w * 0.09f,
    cap = StrokeCap.Round,
  )
  drawLine(
    color = tint,
    start = Offset(w * 0.37f, h * 0.46f),
    end = Offset(w * 0.84f, h * 0.47f),
    strokeWidth = strokeGun.width,
    cap = StrokeCap.Round,
  )
}

private fun DrawScope.drawSleepSilhouette(tint: Color) {
  val w = size.width
  val h = size.height
  val strokeLimb = Stroke(width = w * 0.115f, cap = StrokeCap.Round, join = StrokeJoin.Round)
  val strokeTorso = Stroke(width = w * 0.145f, cap = StrokeCap.Round, join = StrokeJoin.Round)
  val strokeGun = Stroke(width = w * 0.064f, cap = StrokeCap.Round, join = StrokeJoin.Round)

  // Prone facing left (matching Free Fire HUD icon)
  // Raised head at left-center
  drawCircle(color = tint, radius = w * 0.088f, center = Offset(w * 0.38f, h * 0.46f))

  // Horizontal torso along ground
  drawLine(
    color = tint,
    start = Offset(w * 0.43f, h * 0.57f),
    end = Offset(w * 0.64f, h * 0.63f),
    strokeWidth = strokeTorso.width,
    cap = StrokeCap.Round,
  )

  // Legs stretched out flat to the right
  drawLine(
    color = tint,
    start = Offset(w * 0.64f, h * 0.63f),
    end = Offset(w * 0.89f, h * 0.66f),
    strokeWidth = strokeLimb.width,
    cap = StrokeCap.Round,
  )
  // Boot / foot tip
  drawLine(
    color = tint,
    start = Offset(w * 0.89f, h * 0.66f),
    end = Offset(w * 0.91f, h * 0.57f),
    strokeWidth = w * 0.085f,
    cap = StrokeCap.Round,
  )

  // Propped elbows holding rifle forward to the left
  drawLine(
    color = tint,
    start = Offset(w * 0.42f, h * 0.57f),
    end = Offset(w * 0.31f, h * 0.66f),
    strokeWidth = w * 0.082f,
    cap = StrokeCap.Round,
  )
  drawLine(
    color = tint,
    start = Offset(w * 0.31f, h * 0.66f),
    end = Offset(w * 0.25f, h * 0.58f),
    strokeWidth = w * 0.075f,
    cap = StrokeCap.Round,
  )

  // Rifle aimed forward horizontally to the left
  drawLine(
    color = tint,
    start = Offset(w * 0.44f, h * 0.56f),
    end = Offset(w * 0.11f, h * 0.56f),
    strokeWidth = strokeGun.width,
    cap = StrokeCap.Round,
  )
}
