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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.state.DropPhase
import com.example.game.state.HudSnapshot
import com.example.game.world.HouseSpec
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldenHour
import com.example.ui.theme.NeonPink
import com.example.ui.theme.PalmMint
import com.example.ui.theme.SunsetCoral
import com.example.ui.theme.ViceSurface
import com.example.ui.theme.ViceVoid
import kotlin.math.cos
import kotlin.math.sin

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
        // Zoom out automatically while airborne so all 3 islands, bridges, and airplane route fit cleanly
        val scale = if (hud.dropPhase != DropPhase.GROUND) 0.36f else 1.12f

        drawRect(color = Color(0xFF0A243D))

        val camYawDeg = Math.toDegrees(hud.cameraYawRad.toDouble()).toFloat()

        rotate(degrees = -camYawDeg, pivot = Offset(cx, cy)) {
          fun toRadar(wx: Float, wz: Float): Offset {
            val dx = (wx - hud.playerX) * scale
            val dz = (wz - hud.playerZ) * scale
            return Offset(cx + dx, cy + dz)
          }

          val landColor1 = Color(0xFF1F422D)
          val landColor2 = Color(0xFF214A34)
          val landColor3 = Color(0xFF2B4B24)
          val sandColor = Color(0xFF8C7754)
          val roadColor = Color(0xFF3E4052)
          val woodColor = Color(0xFF755236)

          // Map 3: Rooftop Town Street West Island (-212..-84)
          drawRect(
            color = landColor3,
            topLeft = toRadar(-212f, -64f),
            size = Size(128f * scale, 120f * scale),
          )
          drawRect(
            color = sandColor,
            topLeft = toRadar(-213f, 53f),
            size = Size(130f * scale, 13f * scale),
          )
          // Dual West Channel Bridges (-101..-47 at z = 16 & z = -24)
          drawRect(color = Color(0xFF4B5066), topLeft = toRadar(-101f, 11f), size = Size(54f * scale, 10f * scale))
          drawRect(color = GoldenHour.copy(alpha = 0.7f), topLeft = toRadar(-101f, 11f), size = Size(54f * scale, 1.4f * scale))
          drawRect(color = Color(0xFF4B5066), topLeft = toRadar(-101f, -29f), size = Size(54f * scale, 10f * scale))
          drawRect(color = ElectricCyan.copy(alpha = 0.7f), topLeft = toRadar(-101f, -29f), size = Size(54f * scale, 1.4f * scale))

          // West Town Road Grid + Straight Main Rooftop Town Street at x = -148
          drawRect(color = roadColor, topLeft = toRadar(-194f, 11f), size = Size(90f * scale, 10f * scale))
          drawRect(color = roadColor, topLeft = toRadar(-194f, -29f), size = Size(90f * scale, 10f * scale))
          drawRect(color = roadColor, topLeft = toRadar(-199f, -29f), size = Size(10f * scale, 50f * scale))
          drawRect(color = roadColor, topLeft = toRadar(-109f, -29f), size = Size(10f * scale, 50f * scale))
          drawRect(color = roadColor, topLeft = toRadar(-153.3f, -59f), size = Size(10.6f * scale, 108f * scale))
          drawRect(color = Color.White.copy(alpha = 0.65f), topLeft = toRadar(-148.2f, -56f), size = Size(0.5f * scale, 102f * scale))
          // Elevated Steel Water Tower beacon
          drawCircle(color = ElectricCyan.copy(alpha = 0.8f), radius = 2.6f * scale, center = toRadar(-186f, -48f))

          // Map 1: Key Bayside Island (-64..64)
          drawRect(
            color = landColor1,
            topLeft = toRadar(-64f, -64f),
            size = Size(128f * scale, 120f * scale),
          )
          drawRect(
            color = sandColor,
            topLeft = toRadar(-64f, 53f),
            size = Size(128f * scale, 13f * scale),
          )
          // Key Bayside Boat Pier
          drawRect(
            color = woodColor,
            topLeft = toRadar(19.3f, 54f),
            size = Size(5.4f * scale, 30f * scale),
          )

          // Dual East Connecting Channel Bridges (x = 47..101 at z = 16 & z = -24)
          drawRect(
            color = Color(0xFF4B5066),
            topLeft = toRadar(47f, 11f),
            size = Size(54f * scale, 10f * scale),
          )
          drawRect(
            color = ElectricCyan.copy(alpha = 0.65f),
            topLeft = toRadar(47f, 11f),
            size = Size(54f * scale, 1.4f * scale),
          )
          drawRect(
            color = Color(0xFF4B5066),
            topLeft = toRadar(47f, -29f),
            size = Size(54f * scale, 10f * scale),
          )
          drawRect(
            color = NeonPink.copy(alpha = 0.65f),
            topLeft = toRadar(47f, -29f),
            size = Size(54f * scale, 1.4f * scale),
          )

          // Map 2: Green Valley Enclave East Island (84..212)
          drawRect(
            color = landColor2,
            topLeft = toRadar(84f, -64f),
            size = Size(128f * scale, 120f * scale),
          )
          drawRect(
            color = sandColor,
            topLeft = toRadar(83f, 53f),
            size = Size(130f * scale, 13f * scale),
          )
          // Green Valley Boat Pier
          drawRect(
            color = woodColor,
            topLeft = toRadar(165.3f, 54f),
            size = Size(5.4f * scale, 30f * scale),
          )

          // Key Bayside Road Loop
          drawRect(color = roadColor, topLeft = toRadar(-47f, 11f), size = Size(94f * scale, 10f * scale))
          drawRect(color = roadColor, topLeft = toRadar(-47f, -29f), size = Size(94f * scale, 10f * scale))
          drawRect(color = roadColor, topLeft = toRadar(-47f, -29f), size = Size(10f * scale, 50f * scale))
          drawRect(color = roadColor, topLeft = toRadar(37f, -29f), size = Size(10f * scale, 50f * scale))
          drawRect(color = roadColor, topLeft = toRadar(-4.5f, -21f), size = Size(9f * scale, 34f * scale))

          // Green Valley Enclave Road Grid
          drawRect(color = roadColor, topLeft = toRadar(101f, 11f), size = Size(94f * scale, 10f * scale))
          drawRect(color = roadColor, topLeft = toRadar(101f, -29f), size = Size(94f * scale, 10f * scale))
          drawRect(color = roadColor, topLeft = toRadar(101f, -29f), size = Size(10f * scale, 50f * scale))
          drawRect(color = roadColor, topLeft = toRadar(143f, -29f), size = Size(10f * scale, 50f * scale))
          drawRect(color = roadColor, topLeft = toRadar(185f, -29f), size = Size(10f * scale, 50f * scale))

          // Green Valley Central Park, Gazebo, Tennis Court & Clubhouse Pool
          drawRect(color = Color(0xFF2A6E36), topLeft = toRadar(134f, -17f), size = Size(28f * scale, 26f * scale))
          drawCircle(color = SunsetCoral.copy(alpha = 0.85f), radius = 3.2f * scale, center = toRadar(148f, -5.5f))
          drawRect(color = Color(0xFF267042), topLeft = toRadar(166.7f, -12.8f), size = Size(13.6f * scale, 15.6f * scale))
          drawRect(color = Color.White.copy(alpha = 0.6f), topLeft = toRadar(166.7f, -5.2f), size = Size(13.6f * scale, 0.7f * scale))
          drawRect(color = ElectricCyan.copy(alpha = 0.85f), topLeft = toRadar(123.3f, 3.9f), size = Size(9.4f * scale, 4.6f * scale))
          // Grand Entrance Gate emblem at x = 97.5, z = 16
          drawRect(color = GoldenHour.copy(alpha = 0.9f), topLeft = toRadar(96.5f, 9f), size = Size(2.0f * scale, 14f * scale))

          // Bayside Plaza Fountain
          drawCircle(color = NeonPink.copy(alpha = 0.7f), radius = 3.6f * scale, center = toRadar(0f, -4f))

          // All 10 Houses (2F & Rooftop houses highlighted in PalmMint + ElectricCyan stroke!)
          for (h in houses) {
            val tl = toRadar(h.centerX - h.width * 0.5f, h.centerZ - h.depth * 0.5f)
            val houseSz = Size(h.width * scale, h.depth * scale)
            val multiFloor = h.hasRooftop || h.floors >= 2
            drawRect(
              color = if (multiFloor) PalmMint else GoldenHour,
              topLeft = tl,
              size = houseSz,
            )
            if (multiFloor) {
              drawRect(
                color = ElectricCyan,
                topLeft = tl,
                size = houseSz,
                style = Stroke(width = 1.4f),
              )
            }
          }

          // Vice Arms Gun Shop Beacon on Radar
          val gunShopPos = toRadar(hud.gunShopMapPos.first, hud.gunShopMapPos.second)
          drawCircle(color = ViceVoid, radius = 8.5f, center = gunShopPos)
          drawCircle(color = SunsetCoral, radius = 6.5f, center = gunShopPos)
          drawCircle(color = GoldenHour, radius = 3.0f, center = gunShopPos)

          // Pedestrians on Radar (orange highlight when panicking/fleeing)
          for ((px, pz, panicking) in hud.pedestriansMapPos) {
            val pos = toRadar(px, pz)
            drawCircle(
              color = if (panicking) SunsetCoral else Color(0xFFD0F0E8).copy(alpha = 0.72f),
              radius = if (panicking) 3.2f else 2.0f,
              center = pos,
            )
          }

          // Squad Friends on Radar in bright PalmMint
          for ((fx, fz) in hud.friendsMapPos) {
            val pos = toRadar(fx, fz)
            drawCircle(color = ViceVoid, radius = 5.0f, center = pos)
            drawCircle(color = PalmMint, radius = 3.4f, center = pos)
          }

          // Vehicles on map
          for ((id, vx, vz) in hud.vehiclesMapPos) {
            val pos = toRadar(vx, vz)
            val color =
              when {
                id.contains("cheetah") || id.contains("turismo") -> NeonPink
                id.contains("banshee") -> ElectricCyan
                else -> GoldenHour.copy(alpha = 0.85f)
              }
            drawCircle(color = ViceVoid, radius = 5.2f, center = pos)
            drawCircle(color = color, radius = 3.4f, center = pos)
          }

          // Boats on map
          for ((id, bx, bz) in hud.boatsMapPos) {
            val pos = toRadar(bx, bz)
            val color = if (id.startsWith("ai_")) Color(0xFF9DF2FF) else Color(0xFF39E5B6)
            drawCircle(color = ViceVoid, radius = 5.6f, center = pos)
            drawCircle(color = color, radius = 3.8f, center = pos)
          }

          // Aerial Drop Flight Path & Airplane Silhouette spanning all 3 islands
          if (hud.dropPhase == DropPhase.AIRPLANE || hud.planeCountdownSec > 0) {
            val pathStart = toRadar(-210f, -23.5f)
            val pathEnd = toRadar(245f, 16.5f)
            drawLine(
              color = ElectricCyan.copy(alpha = 0.7f),
              start = pathStart,
              end = pathEnd,
              strokeWidth = 2.0f,
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)),
            )

            val planePos = toRadar(hud.planeX, hud.planeZ)
            val fx = sin(hud.planeYawRad)
            val fz = cos(hud.planeYawRad)
            val rx = fz
            val rz = -fx
            val planePath =
              Path().apply {
                moveTo(planePos.x + fx * 11f, planePos.y + fz * 11f)
                lineTo(planePos.x + rx * 9f - fx * 2f, planePos.y + rz * 9f - fz * 2f)
                lineTo(planePos.x - fx * 6f, planePos.y - fz * 6f)
                lineTo(planePos.x - rx * 9f - fx * 2f, planePos.y - rz * 9f - fz * 2f)
                close()
              }
            drawPath(path = planePath, color = GoldenHour)
          }
        }

        // Player directional triangle / icon at center
        val playerRelDeg = Math.toDegrees((hud.playerYawRad - hud.cameraYawRad).toDouble()).toFloat() + 180f
        rotate(degrees = playerRelDeg, pivot = Offset(cx, cy)) {
          val arrow =
            Path().apply {
              moveTo(cx, cy - 9.5f)
              lineTo(cx + 7f, cy + 7f)
              lineTo(cx, cy + 3.2f)
              lineTo(cx - 7f, cy + 7f)
              close()
            }
          drawPath(path = arrow, color = Color.White)
        }
      }

      val badgeText =
        when (hud.dropPhase) {
          DropPhase.AIRPLANE -> "DROP ${hud.planeCountdownSec}s"
          DropPhase.FREEFALL, DropPhase.PARACHUTE -> "${hud.altitudeMeters}m"
          DropPhase.GROUND -> "MAP"
        }
      Text(
        text = badgeText,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
        color = if (hud.dropPhase == DropPhase.GROUND) ElectricCyan else GoldenHour,
        modifier =
          Modifier.align(Alignment.BottomEnd)
            .padding(6.dp)
            .background(ViceVoid.copy(alpha = 0.78f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
      )
    }
  }
}
