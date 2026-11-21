package com.example.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldenHour
import com.example.ui.theme.NeonPink
import com.example.ui.theme.PalmMint
import com.example.ui.theme.SunsetCoral
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.ViceSurface
import kotlin.math.hypot

@Composable
fun VirtualJoystick(
  onMove: (Float, Float) -> Unit,
  modifier: Modifier = Modifier,
  size: Dp = 128.dp,
) {
  var knobOffset by remember { mutableStateOf(Offset.Zero) }
  var isActive by remember { mutableStateOf(false) }

  Box(
    modifier =
      modifier
        .size(size)
        .clip(CircleShape)
        .background(ViceSurface.copy(alpha = if (isActive) 0.85f else 0.60f))
        .border(
          width = 1.5.dp,
          color = if (isActive) ElectricCyan else Color.White.copy(alpha = 0.25f),
          shape = CircleShape,
        )
        .testTag("movement_joystick")
        .pointerInput(Unit) {
          val maxRadius = this.size.width * 0.36f
          val center = Offset(this.size.width * 0.5f, this.size.height * 0.5f)
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            isActive = true

            fun updateStick(pos: Offset) {
              val dx = pos.x - center.x
              val dy = pos.y - center.y
              val dist = hypot(dx, dy)
              val clampedDist = dist.coerceAtMost(maxRadius)
              val nx = if (dist > 1e-3f) (dx / dist) * (clampedDist / maxRadius) else 0f
              val ny = if (dist > 1e-3f) (dy / dist) * (clampedDist / maxRadius) else 0f
              knobOffset = Offset(nx * maxRadius, ny * maxRadius)
              onMove(nx, ny)
            }

            updateStick(down.position)

            while (true) {
              val event = awaitPointerEvent()
              val change = event.changes.firstOrNull { it.id == down.id }
              if (change == null || !change.pressed) {
                break
              }
              change.consume()
              updateStick(change.position)
            }

            isActive = false
            knobOffset = Offset.Zero
            onMove(0f, 0f)
          }
        },
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.matchParentSize()) {
      val c = Offset(this.size.width * 0.5f, this.size.height * 0.5f)
      drawCircle(
        color = Color.White.copy(alpha = 0.08f),
        radius = this.size.minDimension * 0.28f,
        center = c,
      )
      val knobCenter = c + knobOffset
      drawCircle(
        brush =
          Brush.radialGradient(
            colors = listOf(ElectricCyan, Color(0xFF158A9E)),
            center = knobCenter,
            radius = 28.dp.toPx(),
          ),
        radius = 22.dp.toPx(),
        center = knobCenter,
      )
      drawCircle(
        color = Color.White.copy(alpha = 0.7f),
        radius = 8.dp.toPx(),
        center = knobCenter,
      )
    }
  }
}

@Composable
fun HoldControlButton(
  label: String,
  icon: ImageVector,
  accentColor: Color,
  onPressChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  width: Dp = 68.dp,
  height: Dp = 68.dp,
  testTagName: String = "",
) {
  var pressed by remember { mutableStateOf(false) }

  Box(
    modifier =
      modifier
        .size(width = width, height = height)
        .clip(RoundedCornerShape(18.dp))
        .background(
          if (pressed) accentColor.copy(alpha = 0.38f) else ViceSurface.copy(alpha = 0.78f)
        )
        .border(
          width = if (pressed) 2.dp else 1.2.dp,
          color = if (pressed) accentColor else accentColor.copy(alpha = 0.50f),
          shape = RoundedCornerShape(18.dp),
        )
        .then(if (testTagName.isNotEmpty()) Modifier.testTag(testTagName) else Modifier)
        .pointerInput(Unit) {
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            pressed = true
            onPressChange(true)
            while (true) {
              val event = awaitPointerEvent()
              val change = event.changes.firstOrNull { it.id == down.id }
              if (change == null || !change.pressed) break
              change.consume()
            }
            pressed = false
            onPressChange(false)
          }
        },
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (pressed) Color.White else accentColor,
        modifier = Modifier.size(24.dp),
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = TextPrimary,
      )
    }
  }
}

@Composable
fun OnFootActionPad(
  nearbyVehicleName: String?,
  nearbyVehicleOccupied: Boolean,
  nearbyBoatName: String?,
  nearbyBoatOccupied: Boolean,
  onSprintChange: (Boolean) -> Unit,
  onJumpTap: () -> Unit,
  onEnterCraftTap: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    horizontalAlignment = Alignment.End,
    verticalArrangement = Arrangement.spacedBy(10.dp),
    modifier = modifier,
  ) {
    if (nearbyBoatName != null) {
      TapControlButton(
        label = if (nearbyBoatOccupied) "HIJACK" else "SAIL BOAT",
        icon = Icons.Filled.DirectionsBoat,
        accentColor = if (nearbyBoatOccupied) NeonPink else PalmMint,
        onClick = onEnterCraftTap,
        width = 98.dp,
        height = 56.dp,
        testTagName = "enter_boat_button",
      )
    } else if (nearbyVehicleName != null) {
      TapControlButton(
        label = if (nearbyVehicleOccupied) "HIJACK" else "DRIVE",
        icon = Icons.Filled.DirectionsCar,
        accentColor = if (nearbyVehicleOccupied) NeonPink else GoldenHour,
        onClick = onEnterCraftTap,
        width = 88.dp,
        height = 56.dp,
        testTagName = "enter_car_button",
      )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      TapControlButton(
        label = "JUMP",
        icon = Icons.Filled.ArrowUpward,
        accentColor = ElectricCyan,
        onClick = onJumpTap,
        width = 66.dp,
        height = 66.dp,
        testTagName = "jump_button",
      )
      HoldControlButton(
        label = "SPRINT",
        icon = Icons.AutoMirrored.Filled.DirectionsRun,
        accentColor = NeonPink,
        onPressChange = onSprintChange,
        width = 72.dp,
        height = 66.dp,
        testTagName = "sprint_button",
      )
    }
  }
}

@Composable
fun DrivingControlsLeft(
  onSteerLeft: (Boolean) -> Unit,
  onSteerRight: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier) {
    HoldControlButton(
      label = "LEFT",
      icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
      accentColor = ElectricCyan,
      onPressChange = onSteerLeft,
      width = 74.dp,
      height = 72.dp,
      testTagName = "steer_left_button",
    )
    HoldControlButton(
      label = "RIGHT",
      icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
      accentColor = ElectricCyan,
      onPressChange = onSteerRight,
      width = 74.dp,
      height = 72.dp,
      testTagName = "steer_right_button",
    )
  }
}

@Composable
fun DrivingControlsRight(
  speedKmh: Int,
  isDrifting: Boolean,
  isBoat: Boolean,
  onGasChange: (Boolean) -> Unit,
  onBrakeChange: (Boolean) -> Unit,
  onDriftChange: (Boolean) -> Unit,
  onExitCarTap: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    horizontalAlignment = Alignment.End,
    verticalArrangement = Arrangement.spacedBy(10.dp),
    modifier = modifier,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Box(
        modifier =
          Modifier.clip(RoundedCornerShape(14.dp))
            .background(ViceSurface.copy(alpha = 0.85f))
            .border(
              1.dp,
              if (isDrifting) NeonPink else ElectricCyan.copy(alpha = 0.5f),
              RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
      ) {
        Row(verticalAlignment = Alignment.Bottom) {
          Text(
            text = "$speedKmh",
            style = MaterialTheme.typography.headlineMedium,
            color = if (isDrifting) NeonPink else ElectricCyan,
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = if (isDrifting && !isBoat) "DRIFT" else if (isBoat) "KNOTS" else "KM/H",
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary.copy(alpha = 0.85f),
            modifier = Modifier.padding(bottom = 4.dp),
          )
        }
      }

      TapControlButton(
        label = "EXIT",
        icon = Icons.AutoMirrored.Filled.ExitToApp,
        accentColor = SunsetCoral,
        onClick = onExitCarTap,
        width = 64.dp,
        height = 52.dp,
        testTagName = "exit_car_button",
      )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
      HoldControlButton(
        label = if (isBoat) "TURN+" else "DRIFT",
        icon = Icons.Filled.Speed,
        accentColor = GoldenHour,
        onPressChange = onDriftChange,
        width = 62.dp,
        height = 64.dp,
        testTagName = "drift_button",
      )
      HoldControlButton(
        label = "REV",
        icon = Icons.Filled.ArrowUpward,
        accentColor = SunsetCoral,
        onPressChange = onBrakeChange,
        width = 62.dp,
        height = 72.dp,
        testTagName = "brake_button",
      )
      HoldControlButton(
        label = if (isBoat) "THROTTLE" else "GAS",
        icon = Icons.Filled.ArrowUpward,
        accentColor = if (isBoat) PalmMint else NeonPink,
        onPressChange = onGasChange,
        width = 78.dp,
        height = 92.dp,
        testTagName = "gas_button",
      )
    }
  }
}

@Composable
fun TapControlButton(
  label: String,
  icon: ImageVector,
  accentColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  width: Dp = 68.dp,
  height: Dp = 68.dp,
  testTagName: String = "",
) {
  var pressed by remember { mutableStateOf(false) }

  Box(
    modifier =
      modifier
        .size(width = width, height = height)
        .clip(RoundedCornerShape(18.dp))
        .background(
          if (pressed) accentColor.copy(alpha = 0.42f) else ViceSurface.copy(alpha = 0.80f)
        )
        .border(
          width = 1.4.dp,
          color = accentColor.copy(alpha = if (pressed) 1f else 0.65f),
          shape = RoundedCornerShape(18.dp),
        )
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
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (pressed) Color.White else accentColor,
        modifier = Modifier.size(22.dp),
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = TextPrimary,
      )
    }
  }
}
