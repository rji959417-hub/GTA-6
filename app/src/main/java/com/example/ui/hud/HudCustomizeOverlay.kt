package com.example.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.game.state.HudElementId
import com.example.game.state.HudLayoutConfig
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldenHour
import com.example.ui.theme.NeonPink
import com.example.ui.theme.PalmMint
import com.example.ui.theme.SunsetCoral
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ViceSurface
import com.example.ui.theme.ViceSurfaceSolid
import com.example.ui.theme.ViceVoid
import kotlin.math.roundToInt

private sealed interface CustomHudTarget {
  data object AllIcons : CustomHudTarget
  data class Single(val id: HudElementId) : CustomHudTarget
}

@Composable
private fun rememberDraggableHudModifier(
  id: HudElementId,
  draft: HudLayoutConfig,
  containerWidthPx: Float,
  containerHeightPx: Float,
  onSelect: () -> Unit,
  onDragNormDelta: (Float, Float) -> Unit,
): Modifier {
  var nodeCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
  val latestOnSelect by rememberUpdatedState(onSelect)
  val latestOnDrag by rememberUpdatedState(onDragNormDelta)

  return Modifier
    .hudElementOffset(id, draft, containerWidthPx, containerHeightPx)
    .onGloballyPositioned { nodeCoords = it }
    .pointerInput(id, containerWidthPx, containerHeightPx) {
      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        latestOnSelect()
        var lastWin = nodeCoords?.localToWindow(down.position) ?: down.position
        while (true) {
          val event = awaitPointerEvent()
          val change = event.changes.firstOrNull { it.id == down.id }
          if (change == null || !change.pressed) break
          change.consume()
          val curWin = nodeCoords?.localToWindow(change.position) ?: change.position
          val dx = curWin.x - lastWin.x
          val dy = curWin.y - lastWin.y
          lastWin = curWin
          if (containerWidthPx > 1f && containerHeightPx > 1f && (dx != 0f || dy != 0f)) {
            latestOnDrag(dx / containerWidthPx, dy / containerHeightPx)
          }
        }
      }
    }
}

@Composable
fun HudCustomizeOverlay(
  initialConfig: HudLayoutConfig,
  onSave: (HudLayoutConfig) -> Unit,
  onCancel: () -> Unit,
) {
  var draft by remember(initialConfig) { mutableStateOf(initialConfig) }
  var target by remember { mutableStateOf<CustomHudTarget>(CustomHudTarget.AllIcons) }
  var previewDriving by remember { mutableStateOf(false) }
  var panelCollapsed by remember { mutableStateOf(false) }
  var panelDragPx by remember { mutableStateOf(Offset.Zero) }
  var panelCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

  val activeScale =
    when (val t = target) {
      CustomHudTarget.AllIcons -> draft.masterScale
      is CustomHudTarget.Single -> draft.rawScale(t.id)
    }
  val minRange =
    if (target is CustomHudTarget.AllIcons) HudLayoutConfig.MIN_MASTER_SCALE
    else HudLayoutConfig.MIN_ELEMENT_SCALE
  val maxRange =
    if (target is CustomHudTarget.AllIcons) HudLayoutConfig.MAX_MASTER_SCALE
    else HudLayoutConfig.MAX_ELEMENT_SCALE

  fun applyNewScale(value: Float) {
    draft =
      when (val t = target) {
        CustomHudTarget.AllIcons -> draft.withMasterScale(value)
        is CustomHudTarget.Single -> draft.withElementScale(t.id, value)
      }
  }

  fun nudgeOffset(id: HudElementId, dx: Float, dy: Float) {
    draft = draft.nudgeElementOffset(id, dx, dy)
  }

  BoxWithConstraints(
    modifier =
      Modifier.fillMaxSize()
        .background(ViceVoid.copy(alpha = 0.72f))
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 7.dp, vertical = 5.dp)
        .testTag("hud_customize_overlay")
  ) {
    val compactLandscape = maxHeight <= 500.dp || maxWidth <= 950.dp
    val density = LocalDensity.current
    val containerW = with(density) { maxWidth.toPx() }
    val containerH = with(density) { maxHeight.toPx() }
    val baseRadarDp = if (compactLandscape) 72f else 102f
    val baseJoystickDp = if (compactLandscape) 86f else 116f
    val controlScale = if (compactLandscape) 0.74f else 1f

    fun isSelected(id: HudElementId): Boolean =
      target == CustomHudTarget.AllIcons || (target as? CustomHudTarget.Single)?.id == id

    fun selectSingle(id: HudElementId) {
      target = CustomHudTarget.Single(id)
    }

    // =========================================================================
    // 1. LIVE DRAGGABLE TOP-LEFT HUD PREVIEW (Minimap, Status Bar, Voice Bar)
    // =========================================================================
    Column(
      verticalArrangement = Arrangement.spacedBy(if (compactLandscape) 3.dp else 5.dp),
      modifier = Modifier.align(Alignment.TopStart),
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Top,
      ) {
        // MINIMAP PREVIEW
        val radarSize = (baseRadarDp * draft.effectiveScale(HudElementId.MINIMAP)).dp
        PreviewSelectableBox(
          selected = isSelected(HudElementId.MINIMAP),
          accent = ElectricCyan,
          tag = "hud_preview_minimap",
          modifier =
            rememberDraggableHudModifier(
              id = HudElementId.MINIMAP,
              draft = draft,
              containerWidthPx = containerW,
              containerHeightPx = containerH,
              onSelect = { selectSingle(HudElementId.MINIMAP) },
              onDragNormDelta = { dx, dy -> nudgeOffset(HudElementId.MINIMAP, dx, dy) },
            ),
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.size(radarSize)
                .clip(RoundedCornerShape(10.dp))
                .background(ViceSurface.copy(alpha = 0.85f))
                .border(1.dp, ElectricCyan.copy(alpha = 0.55f), RoundedCornerShape(10.dp)),
          ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
              drawCircle(
                color = ElectricCyan.copy(alpha = 0.25f),
                radius = size.minDimension * 0.36f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4f),
              )
              drawCircle(color = PalmMint, radius = 4f, center = Offset(size.width * 0.5f, size.height * 0.5f))
            }
            Text(
              text = "RADAR",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
              color = ElectricCyan,
              modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
            )
          }
        }

        // STATUS BAR PREVIEW (HP / CASH / ZONE)
        val statusScale = draft.effectiveScale(HudElementId.STATUS_BAR)
        PreviewSelectableBox(
          selected = isSelected(HudElementId.STATUS_BAR),
          accent = PalmMint,
          tag = "hud_preview_status",
          modifier =
            rememberDraggableHudModifier(
              id = HudElementId.STATUS_BAR,
              draft = draft,
              containerWidthPx = containerW,
              containerHeightPx = containerH,
              onSelect = { selectSingle(HudElementId.STATUS_BAR) },
              onDragNormDelta = { dx, dy -> nudgeOffset(HudElementId.STATUS_BAR, dx, dy) },
            ),
        ) {
          Column(
            modifier =
              Modifier.clip(RoundedCornerShape((9f * statusScale).dp))
                .background(ViceSurface.copy(alpha = 0.85f))
                .border(0.9.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape((9f * statusScale).dp))
                .padding(horizontal = (7f * statusScale).dp, vertical = (4f * statusScale).dp),
            verticalArrangement = Arrangement.spacedBy((2.5f * statusScale).dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = "HP Preview",
                tint = NeonPink,
                modifier = Modifier.size((11f * statusScale).dp),
              )
              Spacer(modifier = Modifier.width((4f * statusScale).dp))
              Box(
                modifier =
                  Modifier.width((46f * statusScale).dp)
                    .height((5f * statusScale).dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(PalmMint)
              )
              Spacer(modifier = Modifier.width((4f * statusScale).dp))
              Text(
                text = "100",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = (8.5f * statusScale).coerceIn(6.5f, 14f).sp),
                color = TextPrimary,
              )
            }
            Text(
              text = "$1200 · ZONE",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = (8.5f * statusScale).coerceIn(6.5f, 14f).sp, fontWeight = FontWeight.Bold),
              color = PalmMint,
            )
          }
        }
      }

      // VOICE HUD PREVIEW
      val voiceScale = draft.effectiveScale(HudElementId.VOICE_HUD)
      PreviewSelectableBox(
        selected = isSelected(HudElementId.VOICE_HUD),
        accent = PalmMint,
        tag = "hud_preview_voice",
        modifier =
          rememberDraggableHudModifier(
            id = HudElementId.VOICE_HUD,
            draft = draft,
            containerWidthPx = containerW,
            containerHeightPx = containerH,
            onSelect = { selectSingle(HudElementId.VOICE_HUD) },
            onDragNormDelta = { dx, dy -> nudgeOffset(HudElementId.VOICE_HUD, dx, dy) },
          ),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy((4f * voiceScale).dp),
          modifier =
            Modifier.clip(RoundedCornerShape((18f * voiceScale).dp))
              .background(ViceSurfaceSolid.copy(alpha = 0.85f))
              .border(0.9.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape((18f * voiceScale).dp))
              .padding(horizontal = (6f * voiceScale).dp, vertical = (3f * voiceScale).dp),
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.size((28f * voiceScale).dp)
                .clip(CircleShape)
                .background(ElectricCyan.copy(alpha = 0.22f)),
          ) {
            Icon(
              Icons.AutoMirrored.Filled.VolumeUp,
              contentDescription = null,
              tint = ElectricCyan,
              modifier = Modifier.size((14f * voiceScale).dp),
            )
          }
          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.size((28f * voiceScale).dp)
                .clip(CircleShape)
                .background(SunsetCoral.copy(alpha = 0.22f)),
          ) {
            Icon(
              Icons.Filled.Mic,
              contentDescription = null,
              tint = SunsetCoral,
              modifier = Modifier.size((14f * voiceScale).dp),
            )
          }
          Text(
            text = "VOICE",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = (8.5f * voiceScale).coerceIn(6.5f, 14f).sp, fontWeight = FontWeight.Bold),
            color = TextPrimary,
          )
        }
      }
    }

    // =========================================================================
    // 2. LIVE DRAGGABLE TOP-RIGHT HUD PREVIEW (Top Utilities & Bag/Weapon/Shop Pills)
    // =========================================================================
    Column(
      horizontalAlignment = Alignment.End,
      verticalArrangement = Arrangement.spacedBy(4.dp),
      modifier = Modifier.align(Alignment.TopEnd),
    ) {
      val utilScale = (if (compactLandscape) 0.86f else 1f) * draft.effectiveScale(HudElementId.TOP_UTILITIES)
      PreviewSelectableBox(
        selected = isSelected(HudElementId.TOP_UTILITIES),
        accent = GoldenHour,
        tag = "hud_preview_top_utilities",
        modifier =
          rememberDraggableHudModifier(
            id = HudElementId.TOP_UTILITIES,
            draft = draft,
            containerWidthPx = containerW,
            containerHeightPx = containerH,
            onSelect = { selectSingle(HudElementId.TOP_UTILITIES) },
            onDragNormDelta = { dx, dy -> nudgeOffset(HudElementId.TOP_UTILITIES, dx, dy) },
          ),
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy((4f * utilScale).dp)) {
          PreviewMiniPill(Icons.Filled.WbTwilight, "SUN", GoldenHour, utilScale)
          PreviewMiniPill(Icons.Filled.Videocam, null, ElectricCyan, utilScale)
          PreviewMiniPill(Icons.AutoMirrored.Filled.VolumeUp, null, Color.White, utilScale)
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
              Modifier.clip(RoundedCornerShape((9f * utilScale).dp))
                .background(ViceSurface.copy(alpha = 0.85f))
                .border(0.9.dp, GoldenHour.copy(alpha = 0.55f), RoundedCornerShape((9f * utilScale).dp))
                .padding(horizontal = (6f * utilScale).dp, vertical = (4f * utilScale).dp),
          ) {
            Icon(
              painter = painterResource(R.drawable.ic_hud_settings),
              contentDescription = null,
              tint = GoldenHour,
              modifier = Modifier.size((14f * utilScale).dp),
            )
          }
          PreviewMiniPill(Icons.Filled.Menu, "MAP", NeonPink, utilScale)
        }
      }

      val actionPillScale = (if (compactLandscape) 0.86f else 1f) * draft.effectiveScale(HudElementId.ACTION_PILLS)
      PreviewSelectableBox(
        selected = isSelected(HudElementId.ACTION_PILLS),
        accent = ElectricCyan,
        tag = "hud_preview_action_pills",
        modifier =
          rememberDraggableHudModifier(
            id = HudElementId.ACTION_PILLS,
            draft = draft,
            containerWidthPx = containerW,
            containerHeightPx = containerH,
            onSelect = { selectSingle(HudElementId.ACTION_PILLS) },
            onDragNormDelta = { dx, dy -> nudgeOffset(HudElementId.ACTION_PILLS, dx, dy) },
          ),
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy((4f * actionPillScale).dp)) {
          PreviewMiniPill(Icons.Filled.GpsFixed, "GUN", ElectricCyan, actionPillScale)
          PreviewMiniPill(Icons.Filled.Backpack, "BAG", ElectricCyan, actionPillScale)
          PreviewMiniPill(Icons.Filled.MedicalServices, "MED", PalmMint, actionPillScale)
          PreviewMiniPill(Icons.Filled.Storefront, "SHOP", GoldenHour, actionPillScale)
        }
      }
    }

    // =========================================================================
    // 3. LIVE DRAGGABLE BOTTOM-LEFT & BOTTOM-RIGHT CONTROLS PREVIEW
    // =========================================================================
    if (!previewDriving) {
      // JOYSTICK PREVIEW
      val joySize = (baseJoystickDp * draft.effectiveScale(HudElementId.JOYSTICK)).dp
      PreviewSelectableBox(
        selected = isSelected(HudElementId.JOYSTICK),
        accent = ElectricCyan,
        tag = "hud_preview_joystick",
        modifier =
          Modifier.align(Alignment.BottomStart)
            .padding(bottom = 4.dp)
            .then(
              rememberDraggableHudModifier(
                id = HudElementId.JOYSTICK,
                draft = draft,
                containerWidthPx = containerW,
                containerHeightPx = containerH,
                onSelect = { selectSingle(HudElementId.JOYSTICK) },
                onDragNormDelta = { dx, dy -> nudgeOffset(HudElementId.JOYSTICK, dx, dy) },
              )
            ),
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier.size(joySize)
              .clip(CircleShape)
              .background(ViceSurface.copy(alpha = 0.72f))
              .border(1.4.dp, ElectricCyan.copy(alpha = 0.65f), CircleShape),
        ) {
          Box(
            modifier =
              Modifier.size(joySize * 0.36f)
                .clip(CircleShape)
                .background(ElectricCyan.copy(alpha = 0.85f))
          )
        }
      }

      // ON-FOOT RIGHT COMBAT BUTTONS PREVIEW (Each button independently draggable anywhere!)
      Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 4.dp),
      ) {
        val ctxScale = controlScale * draft.effectiveScale(HudElementId.CONTEXT_ACTION)
        PreviewButtonTile(
          label = "ACTION",
          icon = Icons.Filled.DirectionsCar,
          accent = GoldenHour,
          width = (92f * ctxScale).dp,
          height = (46f * ctxScale).dp,
          selected = isSelected(HudElementId.CONTEXT_ACTION),
          tag = "hud_preview_context",
          modifier =
            rememberDraggableHudModifier(
              id = HudElementId.CONTEXT_ACTION,
              draft = draft,
              containerWidthPx = containerW,
              containerHeightPx = containerH,
              onSelect = { selectSingle(HudElementId.CONTEXT_ACTION) },
              onDragNormDelta = { dx, dy -> nudgeOffset(HudElementId.CONTEXT_ACTION, dx, dy) },
            ),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
          val glooScale = controlScale * draft.effectiveScale(HudElementId.GLOO_WALL)
          PreviewButtonTile(
            label = "GLOO",
            icon = Icons.Filled.Shield,
            accent = ElectricCyan,
            width = (68f * glooScale).dp,
            height = (52f * glooScale).dp,
            selected = isSelected(HudElementId.GLOO_WALL),
            tag = "hud_preview_gloo",
            modifier =
              rememberDraggableHudModifier(
                id = HudElementId.GLOO_WALL,
                draft = draft,
                containerWidthPx = containerW,
                containerHeightPx = containerH,
                onSelect = { selectSingle(HudElementId.GLOO_WALL) },
                onDragNormDelta = { dx, dy -> nudgeOffset(HudElementId.GLOO_WALL, dx, dy) },
              ),
          )
          val sitScale = controlScale * draft.effectiveScale(HudElementId.SIT)
          PreviewButtonTile(
            label = "SIT",
            icon = Icons.Filled.EventSeat,
            accent = ElectricCyan,
            width = (60f * sitScale).dp,
            height = (52f * sitScale).dp,
            selected = isSelected(HudElementId.SIT),
            tag = "hud_preview_sit",
            modifier =
              rememberDraggableHudModifier(
                id = HudElementId.SIT,
                draft = draft,
                containerWidthPx = containerW,
                containerHeightPx = containerH,
                onSelect = { selectSingle(HudElementId.SIT) },
                onDragNormDelta = { dx, dy -> nudgeOffset(HudElementId.SIT, dx, dy) },
              ),
          )
          val kickScale = controlScale * draft.effectiveScale(HudElementId.KICK)
          PreviewButtonTile(
            label = "KICK",
            icon = Icons.Filled.Bolt,
            accent = GoldenHour,
            width = (60f * kickScale).dp,
            height = (52f * kickScale).dp,
            selected = isSelected(HudElementId.KICK),
            tag = "hud_preview_kick",
            modifier =
              rememberDraggableHudModifier(
                id = HudElementId.KICK,
                draft = draft,
                containerWidthPx = containerW,
                containerHeightPx = containerH,
                onSelect = { selectSingle(HudElementId.KICK) },
                onDragNormDelta = { dx, dy -> nudgeOffset(HudElementId.KICK, dx, dy) },
              ),
          )
          val punchScale = controlScale * draft.effectiveScale(HudElementId.PUNCH)
          PreviewButtonTile(
            label = "PUNCH",
            icon = Icons.Filled.SportsMma,
            accent = NeonPink,
            width = (66f * punchScale).dp,
            height = (52f * punchScale).dp,
            selected = isSelected(HudElementId.PUNCH),
            tag = "hud_preview_punch",
            modifier =
              rememberDraggableHudModifier(
                id = HudElementId.PUNCH,
                draft = draft,
                containerWidthPx = containerW,
                containerHeightPx = containerH,
                onSelect = { selectSingle(HudElementId.PUNCH) },
                onDragNormDelta = { dx, dy -> nudgeOffset(HudElementId.PUNCH, dx, dy) },
              ),
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
          val scopeScale = controlScale * draft.effectiveScale(HudElementId.SCOPE)
          PreviewButtonTile(
            label = "SCOPE",
            icon = Icons.Filled.CenterFocusStrong,
            accent = ElectricCyan,
            width = (64f * scopeScale).dp,
            height = (58f * scopeScale).dp,
            selected = isSelected(HudElementId.SCOPE),
            tag = "hud_preview_scope",
            modifier =
              rememberDraggableHudModifier(
                id = HudElementId.SCOPE,
                draft = draft,
                containerWidthPx = containerW,
                containerHeightPx = containerH,
                onSelect = { selectSingle(HudElementId.SCOPE) },
                onDragNormDelta = { dx, dy -> nudgeOffset(HudElementId.SCOPE, dx, dy) },
              ),
          )
          val shootScale = controlScale * draft.effectiveScale(HudElementId.SHOOT)
          PreviewButtonTile(
            label = "SHOOT",
            icon = Icons.Filled.GpsFixed,
            accent = SunsetCoral,
            width = (72f * shootScale).dp,
            height = (58f * shootScale).dp,
            selected = isSelected(HudElementId.SHOOT),
            tag = "hud_preview_shoot",
            modifier =
              rememberDraggableHudModifier(
                id = HudElementId.SHOOT,
                draft = draft,
                containerWidthPx = containerW,
                containerHeightPx = containerH,
                onSelect = { selectSingle(HudElementId.SHOOT) },
                onDragNormDelta = { dx, dy -> nudgeOffset(HudElementId.SHOOT, dx, dy) },
              ),
          )
          val jumpScale = controlScale * draft.effectiveScale(HudElementId.JUMP)
          PreviewButtonTile(
            label = "JUMP",
            icon = Icons.Filled.ArrowUpward,
            accent = ElectricCyan,
            width = (58f * jumpScale).dp,
            height = (58f * jumpScale).dp,
            selected = isSelected(HudElementId.JUMP),
            tag = "hud_preview_jump",
            modifier =
              rememberDraggableHudModifier(
                id = HudElementId.JUMP,
                draft = draft,
                containerWidthPx = containerW,
                containerHeightPx = containerH,
                onSelect = { selectSingle(HudElementId.JUMP) },
                onDragNormDelta = { dx, dy -> nudgeOffset(HudElementId.JUMP, dx, dy) },
              ),
          )
          val sprintScale = controlScale * draft.effectiveScale(HudElementId.SPRINT)
          PreviewButtonTile(
            label = "SPRINT",
            icon = Icons.AutoMirrored.Filled.DirectionsRun,
            accent = NeonPink,
            width = (62f * sprintScale).dp,
            height = (58f * sprintScale).dp,
            selected = isSelected(HudElementId.SPRINT),
            tag = "hud_preview_sprint",
            modifier =
              rememberDraggableHudModifier(
                id = HudElementId.SPRINT,
                draft = draft,
                containerWidthPx = containerW,
                containerHeightPx = containerH,
                onSelect = { selectSingle(HudElementId.SPRINT) },
                onDragNormDelta = { dx, dy -> nudgeOffset(HudElementId.SPRINT, dx, dy) },
              ),
          )
        }
      }
    } else {
      // DRIVING PAD PREVIEW (Steer Pad + Pedals independently draggable & resizable)
      val steerScale = controlScale * draft.effectiveScale(HudElementId.DRIVING_PAD)
      PreviewSelectableBox(
        selected = isSelected(HudElementId.DRIVING_PAD),
        accent = GoldenHour,
        tag = "hud_preview_driving_left",
        modifier =
          Modifier.align(Alignment.BottomStart)
            .padding(bottom = 4.dp)
            .then(
              rememberDraggableHudModifier(
                id = HudElementId.DRIVING_PAD,
                draft = draft,
                containerWidthPx = containerW,
                containerHeightPx = containerH,
                onSelect = { selectSingle(HudElementId.DRIVING_PAD) },
                onDragNormDelta = { dx, dy -> nudgeOffset(HudElementId.DRIVING_PAD, dx, dy) },
              )
            ),
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy((10f * steerScale).dp)) {
          PreviewButtonTile("LEFT", Icons.AutoMirrored.Filled.KeyboardArrowLeft, ElectricCyan, (74f * steerScale).dp, (72f * steerScale).dp, false, "")
          PreviewButtonTile("RIGHT", Icons.AutoMirrored.Filled.KeyboardArrowRight, ElectricCyan, (74f * steerScale).dp, (72f * steerScale).dp, false, "")
        }
      }

      val pedalsScale = controlScale * draft.effectiveScale(HudElementId.DRIVING_PEDALS)
      PreviewSelectableBox(
        selected = isSelected(HudElementId.DRIVING_PEDALS),
        accent = GoldenHour,
        tag = "hud_preview_driving_right",
        modifier =
          Modifier.align(Alignment.BottomEnd)
            .padding(bottom = 4.dp)
            .then(
              rememberDraggableHudModifier(
                id = HudElementId.DRIVING_PEDALS,
                draft = draft,
                containerWidthPx = containerW,
                containerHeightPx = containerH,
                onSelect = { selectSingle(HudElementId.DRIVING_PEDALS) },
                onDragNormDelta = { dx, dy -> nudgeOffset(HudElementId.DRIVING_PEDALS, dx, dy) },
              )
            ),
      ) {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
          PreviewButtonTile("EXIT", Icons.AutoMirrored.Filled.ExitToApp, SunsetCoral, (64f * pedalsScale).dp, (52f * pedalsScale).dp, false, "")
          Row(horizontalArrangement = Arrangement.spacedBy((8f * pedalsScale).dp), verticalAlignment = Alignment.Bottom) {
            PreviewButtonTile("DRIFT", Icons.Filled.Speed, GoldenHour, (62f * pedalsScale).dp, (64f * pedalsScale).dp, false, "")
            PreviewButtonTile("REV", Icons.Filled.ArrowUpward, SunsetCoral, (62f * pedalsScale).dp, (72f * pedalsScale).dp, false, "")
            PreviewButtonTile("GAS", Icons.Filled.ArrowUpward, NeonPink, (78f * pedalsScale).dp, (92f * pedalsScale).dp, false, "")
          }
        }
      }
    }

    // =========================================================================
    // 4. FLOATING DRAGGABLE & COLLAPSIBLE CENTER FREE FIRE CUSTOM HUD PANEL
    // =========================================================================
    if (panelCollapsed) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
          Modifier.align(Alignment.TopCenter)
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(50))
            .background(ViceSurfaceSolid.copy(alpha = 0.94f))
            .border(1.2.dp, ElectricCyan.copy(alpha = 0.85f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
          modifier =
            Modifier.clickable { panelCollapsed = false }
              .testTag("hud_panel_expand_button"),
        ) {
          Icon(Icons.Filled.Visibility, contentDescription = "Show HUD Panel", tint = ElectricCyan, modifier = Modifier.size(15.dp))
          Text(
            text = "OPEN PANEL",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold),
            color = ElectricCyan,
          )
        }
        Box(
          modifier =
            Modifier.clip(RoundedCornerShape(8.dp))
              .background(GoldenHour)
              .clickable { onSave(draft) }
              .padding(horizontal = 9.dp, vertical = 4.dp)
              .testTag("hud_save_collapsed_button"),
        ) {
          Text(
            text = "SAVE",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold),
            color = ViceVoid,
          )
        }
      }
    } else {
      Column(
        verticalArrangement = Arrangement.spacedBy(if (compactLandscape) 5.dp else 7.dp),
        modifier =
          Modifier.align(Alignment.Center)
            .offset { IntOffset(panelDragPx.x.roundToInt(), panelDragPx.y.roundToInt()) }
            .widthIn(max = if (compactLandscape) 372.dp else 446.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ViceSurfaceSolid.copy(alpha = 0.94f))
            .border(1.2.dp, ElectricCyan.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
            .padding(horizontal = if (compactLandscape) 10.dp else 14.dp, vertical = if (compactLandscape) 8.dp else 11.dp),
      ) {
        // Header Row (also acts as drag handle to move the panel out of the way!)
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier =
            Modifier.fillMaxWidth()
              .onGloballyPositioned { panelCoords = it }
              .pointerInput(containerW, containerH) {
                awaitEachGesture {
                  val down = awaitFirstDown(requireUnconsumed = false)
                  down.consume()
                  var lastWin = panelCoords?.localToWindow(down.position) ?: down.position
                  while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) break
                    change.consume()
                    val curWin = panelCoords?.localToWindow(change.position) ?: change.position
                    val dx = curWin.x - lastWin.x
                    val dy = curWin.y - lastWin.y
                    lastWin = curWin
                    panelDragPx =
                      Offset(
                        x = (panelDragPx.x + dx).coerceIn(-containerW * 0.36f, containerW * 0.36f),
                        y = (panelDragPx.y + dy).coerceIn(-containerH * 0.36f, containerH * 0.36f),
                      )
                  }
                }
              },
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.weight(1f),
          ) {
            Icon(
              imageVector = Icons.Filled.DragIndicator,
              contentDescription = "Drag Panel",
              tint = ElectricCyan,
              modifier = Modifier.size(16.dp),
            )
            Icon(
              painter = painterResource(R.drawable.ic_hud_settings),
              contentDescription = "Custom HUD Settings",
              tint = GoldenHour,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "DRAG TO MOVE · TAP TO RESIZE",
              style = MaterialTheme.typography.labelMedium.copy(fontSize = if (compactLandscape) 9.5.sp else 11.sp, fontWeight = FontWeight.ExtraBold),
              color = TextPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Box(
              modifier =
                Modifier.clip(RoundedCornerShape(8.dp))
                  .background(if (previewDriving) GoldenHour.copy(alpha = 0.24f) else ElectricCyan.copy(alpha = 0.20f))
                  .border(0.9.dp, if (previewDriving) GoldenHour else ElectricCyan, RoundedCornerShape(8.dp))
                  .clickable {
                    previewDriving = !previewDriving
                    if (previewDriving) {
                      target = CustomHudTarget.Single(HudElementId.DRIVING_PAD)
                    }
                  }
                  .padding(horizontal = 6.dp, vertical = 3.dp)
                  .testTag("hud_toggle_car_preview"),
            ) {
              Text(
                text = if (previewDriving) "CAR HUD" else "ON-FOOT",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                color = if (previewDriving) GoldenHour else ElectricCyan,
              )
            }
            Box(
              contentAlignment = Alignment.Center,
              modifier =
                Modifier.size(25.dp)
                  .clip(CircleShape)
                  .background(Color.White.copy(alpha = 0.10f))
                  .clickable { panelCollapsed = true }
                  .testTag("hud_collapse_panel_button"),
            ) {
              Icon(Icons.Filled.VisibilityOff, contentDescription = "Hide Panel", tint = ElectricCyan, modifier = Modifier.size(13.dp))
            }
            Box(
              contentAlignment = Alignment.Center,
              modifier =
                Modifier.size(25.dp)
                  .clip(CircleShape)
                  .background(Color.White.copy(alpha = 0.10f))
                  .clickable(onClick = onCancel)
                  .testTag("hud_close_cancel_button"),
            ) {
              Icon(Icons.Filled.Close, contentDescription = "Close Custom HUD", tint = TextPrimary, modifier = Modifier.size(14.dp))
            }
          }
        }

        // Scrollable Selector Chips for ALL Icons (All Icons + every individual HUD element)
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(5.dp),
          contentPadding = PaddingValues(horizontal = 1.dp),
        ) {
          item {
            val allSelected = target == CustomHudTarget.AllIcons
            Box(
              modifier =
                Modifier.clip(RoundedCornerShape(9.dp))
                  .background(if (allSelected) GoldenHour else ViceSurface.copy(alpha = 0.85f))
                  .border(1.dp, if (allSelected) GoldenHour else Color.White.copy(alpha = 0.22f), RoundedCornerShape(9.dp))
                  .clickable { target = CustomHudTarget.AllIcons }
                  .padding(horizontal = 8.dp, vertical = 4.dp)
                  .testTag("hud_chip_all_icons"),
            ) {
              Text(
                text = "ALL ICONS",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold),
                color = if (allSelected) ViceVoid else TextPrimary,
              )
            }
          }
          items(HudElementId.entries) { id ->
            val chipSelected = (target as? CustomHudTarget.Single)?.id == id
            val pct = (draft.rawScale(id) * 100f).roundToInt()
            val off = draft.offsetOf(id)
            val moved = kotlin.math.abs(off.normX) >= 0.01f || kotlin.math.abs(off.normY) >= 0.01f
            Box(
              modifier =
                Modifier.clip(RoundedCornerShape(9.dp))
                  .background(if (chipSelected) ElectricCyan else ViceSurface.copy(alpha = 0.85f))
                  .border(1.dp, if (chipSelected) ElectricCyan else Color.White.copy(alpha = 0.20f), RoundedCornerShape(9.dp))
                  .clickable {
                    target = CustomHudTarget.Single(id)
                    if (id == HudElementId.DRIVING_PAD || id == HudElementId.DRIVING_PEDALS) previewDriving = true
                    else if (id == HudElementId.JOYSTICK || id == HudElementId.SHOOT) previewDriving = false
                  }
                  .padding(horizontal = 8.dp, vertical = 4.dp)
                  .testTag("hud_chip_${id.name.lowercase()}"),
            ) {
              val suffix =
                when {
                  pct != 100 && moved -> " $pct%·M"
                  pct != 100 -> " $pct%"
                  moved -> " ·MOVED"
                  else -> ""
                }
              Text(
                text = "${id.shortTag}$suffix",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                color = if (chipSelected) ViceVoid else TextPrimary,
              )
            }
          }
        }

        // Active Target Title + Position Offset Badge + Percentage Badge
        val singleId = (target as? CustomHudTarget.Single)?.id
        val targetLabel =
          if (singleId == null) "ALL SCREEN ICONS (GLOBAL SIZE)"
          else singleId.displayName.uppercase()
        val pctInt = (activeScale * 100f).roundToInt()
        val off = singleId?.let { draft.offsetOf(it) }
        val posLabel =
          if (off == null) null
          else {
            val ox = (off.normX * 100f).roundToInt()
            val oy = (off.normY * 100f).roundToInt()
            "X ${if (ox >= 0) "+$ox" else "$ox"}% · Y ${if (oy >= 0) "+$oy" else "$oy"}%"
          }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = targetLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
            color = ElectricCyan,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          if (posLabel != null) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(2.dp),
              modifier =
                Modifier.clip(RoundedCornerShape(6.dp))
                  .background(PalmMint.copy(alpha = 0.14f))
                  .border(0.7.dp, PalmMint.copy(alpha = 0.50f), RoundedCornerShape(6.dp))
                  .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
              Icon(Icons.Filled.OpenWith, contentDescription = null, tint = PalmMint, modifier = Modifier.size(10.dp))
              Text(
                text = posLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                color = PalmMint,
              )
            }
          }
          Text(
            text = "$pctInt%",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
            color = GoldenHour,
            modifier = Modifier.testTag("hud_selected_scale_text"),
          )
        }

        // Slider with - / + Fine Controls
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.size(30.dp)
                .clip(CircleShape)
                .background(ViceSurface)
                .border(1.dp, ElectricCyan.copy(alpha = 0.6f), CircleShape)
                .clickable { applyNewScale((activeScale - 0.05f).coerceIn(minRange, maxRange)) }
                .testTag("hud_size_minus_button"),
          ) {
            Icon(Icons.Filled.Remove, contentDescription = "Make Smaller", tint = ElectricCyan, modifier = Modifier.size(16.dp))
          }

          Slider(
            value = activeScale.coerceIn(minRange, maxRange),
            onValueChange = { applyNewScale(it) },
            valueRange = minRange..maxRange,
            colors =
              SliderDefaults.colors(
                thumbColor = GoldenHour,
                activeTrackColor = ElectricCyan,
                inactiveTrackColor = Color.White.copy(alpha = 0.22f),
              ),
            modifier = Modifier.weight(1f).height(26.dp).testTag("hud_size_slider"),
          )

          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.size(30.dp)
                .clip(CircleShape)
                .background(ViceSurface)
                .border(1.dp, GoldenHour.copy(alpha = 0.6f), CircleShape)
                .clickable { applyNewScale((activeScale + 0.05f).coerceIn(minRange, maxRange)) }
                .testTag("hud_size_plus_button"),
          ) {
            Icon(Icons.Filled.Add, contentDescription = "Make Larger", tint = GoldenHour, modifier = Modifier.size(16.dp))
          }
        }

        // Quick Presets + Reset Pos + Reset + Save Footer
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("75%" to 0.75f, "100%" to 1.0f, "130%" to 1.30f).forEach { (label, presetVal) ->
              Box(
                modifier =
                  Modifier.clip(RoundedCornerShape(7.dp))
                    .background(ViceSurface.copy(alpha = 0.9f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(7.dp))
                    .clickable { applyNewScale(presetVal) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("hud_preset_${label.replace("%", "")}"),
              ) {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                  color = TextSecondary,
                )
              }
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            if (singleId != null) {
              Box(
                modifier =
                  Modifier.clip(RoundedCornerShape(8.dp))
                    .background(ElectricCyan.copy(alpha = 0.16f))
                    .border(0.8.dp, ElectricCyan.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .clickable { draft = draft.resetElementOffset(singleId) }
                    .padding(horizontal = 6.dp, vertical = 5.dp)
                    .testTag("hud_reset_pos_button"),
              ) {
                Text(
                  text = "CENTER",
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                  color = ElectricCyan,
                )
              }
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp),
              modifier =
                Modifier.clip(RoundedCornerShape(8.dp))
                  .background(SunsetCoral.copy(alpha = 0.18f))
                  .border(0.9.dp, SunsetCoral.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                  .clickable {
                    draft =
                      when (val t = target) {
                        CustomHudTarget.AllIcons -> draft.resetAll()
                        is CustomHudTarget.Single -> draft.resetElement(t.id)
                      }
                  }
                  .padding(horizontal = 6.dp, vertical = 5.dp)
                  .testTag("hud_reset_button"),
            ) {
              Icon(Icons.Filled.Refresh, contentDescription = "Reset", tint = SunsetCoral, modifier = Modifier.size(11.dp))
              Text(
                text = if (target == CustomHudTarget.AllIcons) "RESET ALL" else "RESET",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                color = SunsetCoral,
              )
            }

            Button(
              onClick = { onSave(draft) },
              colors = ButtonDefaults.buttonColors(containerColor = GoldenHour, contentColor = ViceVoid),
              shape = RoundedCornerShape(8.dp),
              contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
              modifier = Modifier.height(27.dp).testTag("hud_save_button"),
            ) {
              Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(12.dp))
              Spacer(modifier = Modifier.width(3.dp))
              Text(
                text = "SAVE",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PreviewSelectableBox(
  selected: Boolean,
  accent: Color,
  tag: String,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Box(
    modifier =
      modifier
        .clip(RoundedCornerShape(10.dp))
        .then(
          if (selected) {
            Modifier.border(1.8.dp, GoldenHour, RoundedCornerShape(10.dp))
              .background(accent.copy(alpha = 0.14f))
          } else {
            Modifier.border(0.8.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
          }
        )
        .padding(2.dp)
        .testTag(tag),
  ) {
    content()
  }
}

@Composable
private fun PreviewMiniPill(
  icon: ImageVector,
  label: String?,
  accent: Color,
  scale: Float,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.clip(RoundedCornerShape((9f * scale).dp))
        .background(ViceSurface.copy(alpha = 0.85f))
        .border(0.9.dp, accent.copy(alpha = 0.50f), RoundedCornerShape((9f * scale).dp))
        .padding(horizontal = (6.5f * scale).dp, vertical = (4f * scale).dp),
  ) {
    Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size((13f * scale).dp))
    if (label != null) {
      Spacer(modifier = Modifier.width((3f * scale).dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = (8.5f * scale).coerceIn(6.5f, 14f).sp, fontWeight = FontWeight.Bold),
        color = TextPrimary,
      )
    }
  }
}

@Composable
private fun PreviewButtonTile(
  label: String,
  icon: ImageVector,
  accent: Color,
  width: Dp,
  height: Dp,
  selected: Boolean,
  tag: String,
  modifier: Modifier = Modifier,
) {
  val iconDp = (height.value * 0.34f).coerceIn(10f, 32f).dp
  val labelSp = (height.value * 0.165f).coerceIn(6.5f, 14f).sp
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .size(width = width, height = height)
        .clip(RoundedCornerShape(12.dp))
        .background(if (selected) accent.copy(alpha = 0.28f) else ViceSurface.copy(alpha = 0.84f))
        .border(
          width = if (selected) 2.0.dp else 1.1.dp,
          color = if (selected) GoldenHour else accent.copy(alpha = 0.65f),
          shape = RoundedCornerShape(12.dp),
        )
        .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier),
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(iconDp))
      Spacer(modifier = Modifier.height(1.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = labelSp, fontWeight = FontWeight.Bold),
        color = TextPrimary,
        maxLines = 1,
      )
    }
  }
}
