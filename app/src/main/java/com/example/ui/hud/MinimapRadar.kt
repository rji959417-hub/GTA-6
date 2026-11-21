package com.example.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.state.HudSnapshot
import com.example.game.world.HouseSpec
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldenHour
import com.example.ui.theme.NeonPink
import com.example.ui.theme.ViceSurface
import com.example.ui.theme.ViceVoid
import kotlin.math.PI

@Composable
fun MinimapRadar(
  hud: HudSnapshot,
  houses: List<HouseSpec>,
  onTapMap: () -> Unit,
  modifier: Modifier = Modifier,
  radarSize: Dp = 116.dp,
) {
  Column(horizontalAlignment = Alignment.Start, modifier = modifier) {
    Box(
      modifier =
        Modifier.size(radarSize)
          .clip(RoundedCornerShape(18.dp))
          .background(ViceSurface)
          .border(1.5.dp, ElectricCyan.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
          .clickable(onClick = onTapMap)
          .testTag("minimap_radar")
    ) {
      Canvas(modifier = Modifier.matchParentSize()) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val scale = 1.45f // pixels per world meter

        // Ocean backdrop
        drawRect(color = Color(0xFF0B2742))

        val camYawDeg = Math.toDegrees(hud.cameraYawRad.toDouble()).toFloat()

        rotate(degrees = -camYawDeg, pivot = Offset(cx, cy)) {
          fun toRadar(wx: Float, wz: Float): Offset {
            val dx = (wx - hud.playerX) * scale
            val dz = (wz - hud.playerZ) * scale
            return Offset(cx + dx, cy + dz)
          }

          // Island Grass landmass (-64..64, -64..56)
          val landTL = toRadar(-64f, -64f)
          drawRect(
            color = Color(0xFF1F422D),
            topLeft = landTL,
            size = Size(128f * scale, 120f * scale),
          )

          // South Sand Beach (-64..64, 53..66)
          val beachTL = toRadar(-64f, 53f)
          drawRect(
            color = Color(0xFF8C7754),
            topLeft = beachTL,
            size = Size(128f * scale, 13f * scale),
          )

          // Sunset Blvd Road Loop
          val roadColor = Color(0xFF3E4052)
          drawRect(
            color = roadColor,
            topLeft = toRadar(-47f, 11f),
            size = Size(94f * scale, 10f * scale),
          )
          drawRect(
            color = roadColor,
            topLeft = toRadar(-47f, -29f),
            size = Size(94f * scale, 10f * scale),
          )
          drawRect(
            color = roadColor,
            topLeft = toRadar(-47f, -29f),
            size = Size(10f * scale, 50f * scale),
          )
          drawRect(
            color = roadColor,
            topLeft = toRadar(37f, -29f),
            size = Size(10f * scale, 50f * scale),
          )
          drawRect(
            color = roadColor,
            topLeft = toRadar(-4.5f, -21f),
            size = Size(9f * scale, 34f * scale),
          )

          // Pier
          drawRect(
            color = Color(0xFF755236),
            topLeft = toRadar(19.5f, 54f),
            size = Size(5f * scale, 30f * scale),
          )

          // 3 Houses
          for (h in houses) {
            val tl = toRadar(h.centerX - h.width * 0.5f, h.centerZ - h.depth * 0.5f)
            drawRect(
              color = GoldenHour,
              topLeft = tl,
              size = Size(h.width * scale, h.depth * scale),
            )
          }

          // Vehicles on map
          for ((id, vx, vz) in hud.vehiclesMapPos) {
            val pos = toRadar(vx, vz)
            val color =
              when {
                id.contains("cheetah") -> NeonPink
                id.contains("banshee") -> ElectricCyan
                else -> GoldenHour.copy(alpha = 0.85f)
              }
            drawCircle(color = ViceVoid, radius = 6.0f, center = pos)
            drawCircle(color = color, radius = 4.0f, center = pos)
          }

          // Boats on map
          for ((id, bx, bz) in hud.boatsMapPos) {
            val pos = toRadar(bx, bz)
            val color = if (id.startsWith("ai_")) Color(0xFF9DF2FF) else Color(0xFF39E5B6)
            drawCircle(color = ViceVoid, radius = 6.5f, center = pos)
            drawCircle(color = color, radius = 4.5f, center = pos)
          }
        }

        // Player directional triangle at center
        val playerRelDeg = Math.toDegrees((hud.playerYawRad - hud.cameraYawRad).toDouble()).toFloat() + 180f
        rotate(degrees = playerRelDeg, pivot = Offset(cx, cy)) {
          val arrow =
            Path().apply {
              moveTo(cx, cy - 10f)
              lineTo(cx + 7.5f, cy + 7.5f)
              lineTo(cx, cy + 3.5f)
              lineTo(cx - 7.5f, cy + 7.5f)
              close()
            }
          drawPath(path = arrow, color = Color.White)
        }
      }

      Text(
        text = "MAP",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
        color = ElectricCyan,
        modifier =
          Modifier.align(Alignment.BottomEnd)
            .padding(6.dp)
            .background(ViceVoid.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
      )
    }
  }
}
