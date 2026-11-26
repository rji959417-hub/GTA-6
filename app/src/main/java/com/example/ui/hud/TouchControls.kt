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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Paragliding
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Upgrade
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.state.DropPhase
import com.example.game.state.HudElementId
import com.example.game.state.HudLayoutConfig
import com.example.game.state.WeaponType
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldenHour
import com.example.ui.theme.NeonPink
import com.example.ui.theme.PalmMint
import com.example.ui.theme.SunsetCoral
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.ViceSurface
import kotlin.math.hypot
import kotlin.math.roundToInt

internal fun Modifier.hudElementOffset(
  id: HudElementId,
  hudConfig: HudLayoutConfig,
  containerWidthPx: Float,
  containerHeightPx: Float,
): Modifier {
  if (containerWidthPx <= 1f || containerHeightPx <= 1f) return this
  val off = hudConfig.offsetOf(id)
  if (kotlin.math.abs(off.normX) < 0.001f && kotlin.math.abs(off.normY) < 0.001f) return this
  return this.offset {
    IntOffset(
      x = (off.normX * containerWidthPx).roundToInt(),
      y = (off.normY * containerHeightPx).roundToInt(),
    )
  }
}

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
      val dim = this.size.minDimension
      drawCircle(
        color = Color.White.copy(alpha = 0.08f),
        radius = dim * 0.28f,
        center = c,
      )
      val knobCenter = c + knobOffset
      val knobRadius = (dim * 0.172f).coerceIn(10f, 70f)
      drawCircle(
        brush =
          Brush.radialGradient(
            colors = listOf(ElectricCyan, Color(0xFF158A9E)),
            center = knobCenter,
            radius = (dim * 0.24f).coerceAtLeast(12f),
          ),
        radius = knobRadius,
        center = knobCenter,
      )
      drawCircle(
        color = Color.White.copy(alpha = 0.7f),
        radius = (dim * 0.062f).coerceIn(4f, 26f),
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
  val isCompact = height < 52.dp || width < 52.dp

  val iconDp = (minOf(width.value, height.value) * 0.34f).coerceIn(10f, 34f).dp
  val labelSp = (minOf(width.value, height.value) * 0.175f).coerceIn(6.8f, 14f).sp
  Box(
    modifier =
      modifier
        .size(width = width, height = height)
        .clip(RoundedCornerShape(if (isCompact) 12.dp else 18.dp))
        .background(
          if (pressed) accentColor.copy(alpha = 0.38f) else ViceSurface.copy(alpha = 0.78f)
        )
        .border(
          width = if (pressed) 1.8.dp else 1.1.dp,
          color = if (pressed) accentColor else accentColor.copy(alpha = 0.50f),
          shape = RoundedCornerShape(if (isCompact) 12.dp else 18.dp),
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
        modifier = Modifier.size(iconDp),
      )
      Spacer(modifier = Modifier.height(if (isCompact) 1.dp else 2.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = labelSp),
        color = TextPrimary,
        maxLines = 1,
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
  nearbyGunShop: Boolean,
  nearbyMarketStallLabel: String? = null,
  nearbyFriendCandidate: Boolean,
  nearbyParkourLabel: String?,
  isSitting: Boolean,
  isScoped: Boolean,
  equippedWeapon: WeaponType,
  glooWallCount: Int = 0,
  isAutoSprinting: Boolean = false,
  onSprintToggleTap: () -> Unit = {},
  onJumpTap: () -> Unit,
  onClimbTap: () -> Unit,
  onEnterCraftTap: () -> Unit,
  onSitToggleTap: () -> Unit,
  onBefriendTap: () -> Unit,
  onScopeToggleTap: () -> Unit,
  onPunchTap: () -> Unit,
  onKickTap: () -> Unit,
  onFireChange: (Boolean) -> Unit,
  onOpenGunShopTap: () -> Unit,
  onBuyMarketSnackTap: () -> Unit = {},
  onDeployGlooWallTap: () -> Unit = {},
  modifier: Modifier = Modifier,
  compactScale: Float = 1f,
  hudConfig: HudLayoutConfig = HudLayoutConfig.DEFAULT,
  containerWidthPx: Float = 0f,
  containerHeightPx: Float = 0f,
) {
  val gap = (8f * compactScale).dp
  val ctxScale = compactScale * hudConfig.effectiveScale(HudElementId.CONTEXT_ACTION)
  val glooScale = compactScale * hudConfig.effectiveScale(HudElementId.GLOO_WALL)
  val sitScale = compactScale * hudConfig.effectiveScale(HudElementId.SIT)
  val kickScale = compactScale * hudConfig.effectiveScale(HudElementId.KICK)
  val punchScale = compactScale * hudConfig.effectiveScale(HudElementId.PUNCH)
  val scopeScale = compactScale * hudConfig.effectiveScale(HudElementId.SCOPE)
  val shootScale = compactScale * hudConfig.effectiveScale(HudElementId.SHOOT)
  val jumpScale = compactScale * hudConfig.effectiveScale(HudElementId.JUMP)
  val sprintScale = compactScale * hudConfig.effectiveScale(HudElementId.SPRINT)

  Column(
    horizontalAlignment = Alignment.End,
    verticalArrangement = Arrangement.spacedBy(gap),
    modifier = modifier,
  ) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(gap),
      modifier = Modifier.hudElementOffset(HudElementId.CONTEXT_ACTION, hudConfig, containerWidthPx, containerHeightPx),
    ) {
      if (nearbyParkourLabel != null) {
        TapControlButton(
          label = nearbyParkourLabel,
          icon = Icons.Filled.Upgrade,
          accentColor = ElectricCyan,
          onClick = onClimbTap,
          width = (108f * ctxScale).dp,
          height = (50f * ctxScale).dp,
          testTagName = "climb_button",
        )
      }
      if (nearbyFriendCandidate) {
        TapControlButton(
          label = "FRIEND",
          icon = Icons.Filled.PersonAdd,
          accentColor = PalmMint,
          onClick = onBefriendTap,
          width = (88f * ctxScale).dp,
          height = (50f * ctxScale).dp,
          testTagName = "befriend_button",
        )
      }
      when {
        nearbyBoatName != null -> {
          TapControlButton(
            label = if (nearbyBoatOccupied) "HIJACK" else "SAIL BOAT",
            icon = Icons.Filled.DirectionsBoat,
            accentColor = if (nearbyBoatOccupied) NeonPink else PalmMint,
            onClick = onEnterCraftTap,
            width = (104f * ctxScale).dp,
            height = (50f * ctxScale).dp,
            testTagName = "enter_boat_button",
          )
        }
        nearbyVehicleName != null -> {
          TapControlButton(
            label = if (nearbyVehicleOccupied) "HIJACK" else "DRIVE",
            icon = Icons.Filled.DirectionsCar,
            accentColor = if (nearbyVehicleOccupied) NeonPink else GoldenHour,
            onClick = onEnterCraftTap,
            width = (96f * ctxScale).dp,
            height = (50f * ctxScale).dp,
            testTagName = "enter_car_button",
          )
        }
        nearbyGunShop -> {
          TapControlButton(
            label = "BUY GUNS",
            icon = Icons.Filled.Storefront,
            accentColor = GoldenHour,
            onClick = onOpenGunShopTap,
            width = (106f * ctxScale).dp,
            height = (50f * ctxScale).dp,
            testTagName = "context_gun_shop_button",
          )
        }
        nearbyMarketStallLabel != null -> {
          TapControlButton(
            label = "BUY $25",
            icon = Icons.Filled.ShoppingBasket,
            accentColor = PalmMint,
            onClick = onBuyMarketSnackTap,
            width = (104f * ctxScale).dp,
            height = (50f * ctxScale).dp,
            testTagName = "buy_market_snack_button",
          )
        }
      }
    }

    // Upper Combat & Posture Row: GLOO · SIT/STAND · KICK · PUNCH
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
      if (glooWallCount > 0) {
        TapControlButton(
          label = "GLOO×$glooWallCount",
          icon = Icons.Filled.Shield,
          accentColor = ElectricCyan,
          onClick = onDeployGlooWallTap,
          modifier = Modifier.hudElementOffset(HudElementId.GLOO_WALL, hudConfig, containerWidthPx, containerHeightPx),
          width = (72f * glooScale).dp,
          height = (52f * glooScale).dp,
          testTagName = "gloo_wall_button",
        )
      }
      TapControlButton(
        label = if (isSitting) "STAND" else "SIT",
        icon = Icons.Filled.EventSeat,
        accentColor = if (isSitting) PalmMint else ElectricCyan,
        onClick = onSitToggleTap,
        modifier = Modifier.hudElementOffset(HudElementId.SIT, hudConfig, containerWidthPx, containerHeightPx),
        width = (60f * sitScale).dp,
        height = (52f * sitScale).dp,
        testTagName = "sit_button",
      )
      TapControlButton(
        label = "KICK",
        icon = Icons.Filled.Bolt,
        accentColor = GoldenHour,
        onClick = onKickTap,
        modifier = Modifier.hudElementOffset(HudElementId.KICK, hudConfig, containerWidthPx, containerHeightPx),
        width = (60f * kickScale).dp,
        height = (52f * kickScale).dp,
        testTagName = "kick_button",
      )
      TapControlButton(
        label = "PUNCH",
        icon = Icons.Filled.SportsMma,
        accentColor = NeonPink,
        onClick = onPunchTap,
        modifier = Modifier.hudElementOffset(HudElementId.PUNCH, hudConfig, containerWidthPx, containerHeightPx),
        width = (66f * punchScale).dp,
        height = (52f * punchScale).dp,
        testTagName = "punch_button",
      )
    }

    // Lower Action Row: SCOPE + SHOOT (when gun equipped) · JUMP · SPRINT
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
      if (equippedWeapon != WeaponType.FISTS) {
        TapControlButton(
          label = if (isScoped) "UNSCOPE" else "SCOPE",
          icon = Icons.Filled.CenterFocusStrong,
          accentColor = if (isScoped) GoldenHour else ElectricCyan,
          onClick = onScopeToggleTap,
          modifier = Modifier.hudElementOffset(HudElementId.SCOPE, hudConfig, containerWidthPx, containerHeightPx),
          width = (64f * scopeScale).dp,
          height = (58f * scopeScale).dp,
          testTagName = "scope_button",
        )
        HoldControlButton(
          label = "SHOOT",
          icon = Icons.Filled.GpsFixed,
          accentColor = SunsetCoral,
          onPressChange = onFireChange,
          modifier = Modifier.hudElementOffset(HudElementId.SHOOT, hudConfig, containerWidthPx, containerHeightPx),
          width = (72f * shootScale).dp,
          height = (58f * shootScale).dp,
          testTagName = "fire_button",
        )
      }
      TapControlButton(
        label = "JUMP",
        icon = Icons.Filled.ArrowUpward,
        accentColor = ElectricCyan,
        onClick = onJumpTap,
        modifier = Modifier.hudElementOffset(HudElementId.JUMP, hudConfig, containerWidthPx, containerHeightPx),
        width = (58f * jumpScale).dp,
        height = (58f * jumpScale).dp,
        testTagName = "jump_button",
      )
      TapControlButton(
        label = if (isAutoSprinting) "RUNNING" else "SPRINT",
        icon = Icons.AutoMirrored.Filled.DirectionsRun,
        accentColor = if (isAutoSprinting) PalmMint else NeonPink,
        onClick = onSprintToggleTap,
        modifier = Modifier.hudElementOffset(HudElementId.SPRINT, hudConfig, containerWidthPx, containerHeightPx),
        width = (64f * sprintScale).dp,
        height = (58f * sprintScale).dp,
        isActive = isAutoSprinting,
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
  compactScale: Float = 1f,
  hudConfig: HudLayoutConfig = HudLayoutConfig.DEFAULT,
  containerWidthPx: Float = 0f,
  containerHeightPx: Float = 0f,
) {
  val driveScale = compactScale * hudConfig.effectiveScale(HudElementId.DRIVING_PAD)
  Row(
    horizontalArrangement = Arrangement.spacedBy((12f * driveScale).dp),
    modifier = modifier.hudElementOffset(HudElementId.DRIVING_PAD, hudConfig, containerWidthPx, containerHeightPx),
  ) {
    HoldControlButton(
      label = "LEFT",
      icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
      accentColor = ElectricCyan,
      onPressChange = onSteerLeft,
      width = (74f * driveScale).dp,
      height = (72f * driveScale).dp,
      testTagName = "steer_left_button",
    )
    HoldControlButton(
      label = "RIGHT",
      icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
      accentColor = ElectricCyan,
      onPressChange = onSteerRight,
      width = (74f * driveScale).dp,
      height = (72f * driveScale).dp,
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
  compactScale: Float = 1f,
  hudConfig: HudLayoutConfig = HudLayoutConfig.DEFAULT,
  containerWidthPx: Float = 0f,
  containerHeightPx: Float = 0f,
) {
  val driveScale = compactScale * hudConfig.effectiveScale(HudElementId.DRIVING_PEDALS)
  val gap = (10f * driveScale).dp
  Column(
    horizontalAlignment = Alignment.End,
    verticalArrangement = Arrangement.spacedBy(gap),
    modifier = modifier.hudElementOffset(HudElementId.DRIVING_PEDALS, hudConfig, containerWidthPx, containerHeightPx),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy((8f * compactScale).dp),
    ) {
      Box(
        modifier =
          Modifier.clip(RoundedCornerShape(10.dp))
            .background(ViceSurface.copy(alpha = 0.85f))
            .border(
              1.dp,
              if (isDrifting) NeonPink else ElectricCyan.copy(alpha = 0.5f),
              RoundedCornerShape(10.dp),
            )
            .padding(horizontal = (10f * compactScale).dp, vertical = (5f * compactScale).dp)
      ) {
        Row(verticalAlignment = Alignment.Bottom) {
          Text(
            text = "$speedKmh",
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = (22f * compactScale).sp),
            color = if (isDrifting) NeonPink else ElectricCyan,
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = if (isDrifting && !isBoat) "DRIFT" else if (isBoat) "KNOTS" else "KM/H",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = (10f * compactScale).coerceAtLeast(8f).sp),
            color = TextPrimary.copy(alpha = 0.85f),
            modifier = Modifier.padding(bottom = 2.dp),
          )
        }
      }

      TapControlButton(
        label = "EXIT",
        icon = Icons.AutoMirrored.Filled.ExitToApp,
        accentColor = SunsetCoral,
        onClick = onExitCarTap,
        width = (64f * driveScale).dp,
        height = (52f * driveScale).dp,
        testTagName = "exit_car_button",
      )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Bottom) {
      HoldControlButton(
        label = if (isBoat) "TURN+" else "DRIFT",
        icon = Icons.Filled.Speed,
        accentColor = GoldenHour,
        onPressChange = onDriftChange,
        width = (62f * driveScale).dp,
        height = (64f * driveScale).dp,
        testTagName = "drift_button",
      )
      HoldControlButton(
        label = "REV",
        icon = Icons.Filled.ArrowUpward,
        accentColor = SunsetCoral,
        onPressChange = onBrakeChange,
        width = (62f * driveScale).dp,
        height = (72f * driveScale).dp,
        testTagName = "brake_button",
      )
      HoldControlButton(
        label = if (isBoat) "THROTTLE" else "GAS",
        icon = Icons.Filled.ArrowUpward,
        accentColor = if (isBoat) PalmMint else NeonPink,
        onPressChange = onGasChange,
        width = (78f * driveScale).dp,
        height = (92f * driveScale).dp,
        testTagName = "gas_button",
      )
    }
  }
}

@Composable
fun AirborneDropActionPad(
  dropPhase: DropPhase,
  planeCountdownSec: Int,
  altitudeMeters: Int,
  onJumpFromPlaneTap: () -> Unit,
  onDeployChuteTap: () -> Unit,
  modifier: Modifier = Modifier,
  compactScale: Float = 1f,
  hudConfig: HudLayoutConfig = HudLayoutConfig.DEFAULT,
  containerWidthPx: Float = 0f,
  containerHeightPx: Float = 0f,
) {
  val dropScale = compactScale * hudConfig.effectiveScale(HudElementId.JUMP)
  Column(
    horizontalAlignment = Alignment.End,
    verticalArrangement = Arrangement.spacedBy((8f * dropScale).dp),
    modifier = modifier.hudElementOffset(HudElementId.JUMP, hudConfig, containerWidthPx, containerHeightPx),
  ) {
    Box(
      modifier =
        Modifier.clip(RoundedCornerShape(10.dp))
          .background(ViceSurface.copy(alpha = 0.86f))
          .border(1.dp, ElectricCyan.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
          .padding(horizontal = (10f * dropScale).dp, vertical = (5f * dropScale).dp)
    ) {
      val statusText =
        when (dropPhase) {
          DropPhase.AIRPLANE -> "AUTO-EJECT IN ${planeCountdownSec}s"
          DropPhase.FREEFALL -> "FREEFALL · ALT ${altitudeMeters}m"
          DropPhase.PARACHUTE -> "CHUTE OPEN · ALT ${altitudeMeters}m"
          DropPhase.GROUND -> "GROUNDED"
        }
      Text(
        text = statusText,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = (10f * dropScale).coerceAtLeast(8.5f).sp),
        color = if (dropPhase == DropPhase.AIRPLANE) GoldenHour else PalmMint,
      )
    }

    when (dropPhase) {
      DropPhase.AIRPLANE -> {
        TapControlButton(
          label = "JUMP NOW",
          icon = Icons.Filled.FlightTakeoff,
          accentColor = NeonPink,
          onClick = onJumpFromPlaneTap,
          width = (112f * dropScale).dp,
          height = (76f * dropScale).dp,
          testTagName = "plane_jump_button",
        )
      }
      DropPhase.FREEFALL -> {
        TapControlButton(
          label = "PARACHUTE",
          icon = Icons.Filled.Paragliding,
          accentColor = ElectricCyan,
          onClick = onDeployChuteTap,
          width = (118f * dropScale).dp,
          height = (76f * dropScale).dp,
          testTagName = "deploy_chute_button",
        )
      }
      DropPhase.PARACHUTE -> {
        Box(
          modifier =
            Modifier.clip(RoundedCornerShape(12.dp))
              .background(PalmMint.copy(alpha = 0.22f))
              .border(1.2.dp, PalmMint, RoundedCornerShape(12.dp))
              .padding(horizontal = (12f * compactScale).dp, vertical = (10f * compactScale).dp)
              .testTag("parachute_glide_badge")
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Paragliding, contentDescription = "Parachute Glide", tint = PalmMint, modifier = Modifier.size((20f * compactScale).dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text("STEER LANDING", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp), color = TextPrimary)
          }
        }
      }
      DropPhase.GROUND -> Unit
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
  isActive: Boolean = false,
  testTagName: String = "",
) {
  var pressed by remember { mutableStateOf(false) }
  val isCompact = height < 52.dp || width < 52.dp
  val lit = pressed || isActive

  Box(
    modifier =
      modifier
        .size(width = width, height = height)
        .clip(RoundedCornerShape(if (isCompact) 12.dp else 18.dp))
        .background(
          if (lit) accentColor.copy(alpha = 0.42f) else ViceSurface.copy(alpha = 0.80f)
        )
        .border(
          width = if (isActive) 1.8.dp else 1.2.dp,
          color = accentColor.copy(alpha = if (lit) 1f else 0.65f),
          shape = RoundedCornerShape(if (isCompact) 12.dp else 18.dp),
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
      val iconDp = (minOf(width.value, height.value) * 0.34f).coerceIn(10f, 34f).dp
      val labelSp = (minOf(width.value, height.value) * 0.175f).coerceIn(6.8f, 14f).sp
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (lit) Color.White else accentColor,
        modifier = Modifier.size(iconDp),
      )
      Spacer(modifier = Modifier.height(if (isCompact) 1.dp else 2.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = labelSp),
        color = TextPrimary,
        maxLines = 1,
      )
    }
  }
}
