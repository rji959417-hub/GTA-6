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
import com.example.game.state.LootCategory
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
        // Zoom out automatically while airborne so the expanded 4-sided world & flight route fit cleanly
        val scale = if (hud.dropPhase != DropPhase.GROUND) 0.25f else 0.96f

        drawRect(color = Color(0xFF0A243D))

        val camYawDeg = Math.toDegrees(hud.cameraYawRad.toDouble()).toFloat()

        rotate(degrees = -camYawDeg, pivot = Offset(cx, cy)) {
          fun toRadar(wx: Float, wz: Float): Offset {
            val dx = (wx - hud.playerX) * scale
            val dz = (wz - hud.playerZ) * scale
            return Offset(cx + dx, cy + dz)
          }

          val deepWater = Color(0xFF0A243D)
          val shallowReef = Color(0xFF155E75)
          val moatTurquoise = Color(0xFF1985A1)
          val oliveLand = Color(0xFF284D35)
          val farmTerracotta = Color(0xFF8C5332)
          val farmSage = Color(0xFF447849)
          val desertSand = Color(0xFF8E7A56)
          val mudClay = Color(0xFF6B5D44)
          val roadColor = Color(0xFF424554)
          val woodColor = Color(0xFF755236)
          val pinkMosaic = Color(0xFFD678B0)

          drawRect(color = deepWater, topLeft = toRadar(-340f, -340f), size = Size(680f * scale, 680f * scale))

          // Subtle Nextera 7x7 tactical map grid (B..H x J..P)
          for (g in -240..240 step 80) {
            drawLine(
              color = Color.White.copy(alpha = 0.08f),
              start = toRadar(g.toFloat(), -270f),
              end = toRadar(g.toFloat(), 270f),
              strokeWidth = 0.8f,
            )
            drawLine(
              color = Color.White.copy(alpha = 0.08f),
              start = toRadar(-270f, g.toFloat()),
              end = toRadar(270f, g.toFloat()),
              strokeWidth = 0.8f,
            )
          }

          // Coastal turquoise halo around Nextera lobes
          val coastalLobes =
            listOf(
              Triple(-18f, -184f, 84f),  // Intellect Island
              Triple(-218f, 6f, 46f),    // Turbine Island
              Triple(-172f, -86f, 68f),  // Museum
              Triple(-24f, 22f, 134f),   // Central Island
              Triple(-158f, 166f, 74f),  // Grav Labs
              Triple(-6f, 168f, 94f),    // Plazaria & Deca Square
              Triple(146f, 134f, 78f),   // Mud Site
              Triple(164f, -14f, 92f),   // Rust & Mortar East
              Triple(126f, -146f, 68f),  // Twin Bridge
            )
          for ((lx, lz, lr) in coastalLobes) {
            drawCircle(color = shallowReef, radius = lr * scale, center = toRadar(lx, lz))
          }

          // Dry Nextera landmasses
          drawCircle(color = oliveLand, radius = 71f * scale, center = toRadar(-18f, -184f))
          drawCircle(color = oliveLand, radius = 37f * scale, center = toRadar(-218f, 6f))
          drawCircle(color = oliveLand, radius = 58f * scale, center = toRadar(-172f, -86f))
          drawRect(color = oliveLand, topLeft = toRadar(-148f, -96f), size = Size(248f * scale, 236f * scale))
          drawCircle(color = oliveLand, radius = 63f * scale, center = toRadar(-158f, 166f))
          drawCircle(color = oliveLand, radius = 84f * scale, center = toRadar(-6f, 168f))
          drawCircle(color = oliveLand, radius = 68f * scale, center = toRadar(146f, 134f))
          drawCircle(color = desertSand, radius = 80f * scale, center = toRadar(164f, -14f))
          drawCircle(color = oliveLand, radius = 58f * scale, center = toRadar(126f, -146f))

          // Carve Turbine Cove & Intellect Center South Curved Moat
          drawCircle(color = moatTurquoise, radius = 40f * scale, center = toRadar(-162f, 6f))
          drawCircle(
            color = moatTurquoise,
            radius = 84f * scale,
            center = toRadar(-18f, -182f),
            style = Stroke(width = 24f * scale),
          )
          // Re-fill North Intellect Island core clean over moat ring
          drawCircle(color = oliveLand, radius = 69f * scale, center = toRadar(-18f, -184f))
          drawCircle(color = Color(0xFF4A5848), radius = 34f * scale, center = toRadar(-18f, -180f))
          drawCircle(color = Color(0xFFCBD5E1), radius = 14f * scale, center = toRadar(-18f, -180f))

          // Farmtopia patchwork fields (terracotta & sage)
          drawRect(color = farmTerracotta, topLeft = toRadar(-106f, -38f), size = Size(68f * scale, 84f * scale))
          drawRect(color = farmSage, topLeft = toRadar(-94f, -16f), size = Size(44f * scale, 44f * scale))
          drawRect(color = Color(0xFFE0A83E), topLeft = toRadar(-79f, -1f), size = Size(14f * scale, 14f * scale))

          // Museum white plaza & gold hall
          drawRect(color = Color(0xFFEAE6DC), topLeft = toRadar(-194f, -96f), size = Size(32f * scale, 32f * scale))
          drawRect(color = GoldenHour, topLeft = toRadar(-185f, -87f), size = Size(14f * scale, 14f * scale))

          // Grav Labs golden reactor ring & geodesic dome
          drawCircle(color = GoldenHour, radius = 24f * scale, center = toRadar(-158f, 168f))
          drawCircle(color = Color(0xFF28303E), radius = 14f * scale, center = toRadar(-158f, 168f))
          drawCircle(color = GoldenHour, radius = 12f * scale, center = toRadar(-134f, 168f))

          // Grav Labs SW Marina Pier
          drawRect(color = woodColor, topLeft = toRadar(-201f, 61f), size = Size(6f * scale, 32f * scale))
          drawRect(color = woodColor, topLeft = toRadar(-206f, 87f), size = Size(16f * scale, 5.5f * scale))

          // Plazaria courtyard
          drawRect(color = Color(0xFFE5DFCE), topLeft = toRadar(-84f, 136f), size = Size(36f * scale, 30f * scale))
          drawRect(color = GoldenHour.copy(alpha = 0.85f), topLeft = toRadar(-74f, 144f), size = Size(16f * scale, 14f * scale))

          // Deca Square iconic pink mosaic plaza + central golden octagon
          drawCircle(color = pinkMosaic, radius = 25f * scale, center = toRadar(24f, 172f))
          drawCircle(color = GoldenHour, radius = 10f * scale, center = toRadar(24f, 172f))

          // Mud Site earthen quarry terraces
          drawCircle(color = mudClay, radius = 28f * scale, center = toRadar(154f, 148f))

          // Mortar Ruins crater + Rust Town cranes
          drawCircle(color = Color(0xFF5C4E38), radius = 17f * scale, center = toRadar(176f, -68f))
          drawRect(color = Color(0xFFD49830), topLeft = toRadar(168f, 16f), size = Size(34f * scale, 8f * scale))

          // Shared Nextera Highway Network from TerrainHeightfield.roadSegments
          for (seg in com.example.game.world.TerrainHeightfield.roadSegments) {
            drawLine(
              color = roadColor,
              start = toRadar(seg.x1, seg.z1),
              end = toRadar(seg.x2, seg.z2),
              strokeWidth = (seg.halfWidth * 1.9f * scale).coerceAtLeast(1.8f),
            )
          }

          // Iconic Turbine Red Suspension Bridge
          drawLine(
            color = Color(0xFFDC2626),
            start = toRadar(-170f, -38f),
            end = toRadar(-170f, 46f),
            strokeWidth = (10f * scale).coerceAtLeast(2.4f),
          )
          // Intellect Center 3 Moat Bridges
          drawLine(color = Color(0xFFD1D5DB), start = toRadar(-16f, -84f), end = toRadar(-16f, -142f), strokeWidth = (8f * scale).coerceAtLeast(2.0f))
          drawLine(color = Color(0xFFD1D5DB), start = toRadar(-96f, -92f), end = toRadar(-58f, -142f), strokeWidth = (8f * scale).coerceAtLeast(2.0f))
          drawLine(color = Color(0xFFD1D5DB), start = toRadar(66f, -148f), end = toRadar(24f, -162f), strokeWidth = (8f * scale).coerceAtLeast(2.0f))

          // Farmtopia Produce Market & Waterfalls
          drawCircle(color = PalmMint, radius = 4.6f * scale, center = toRadar(hud.marketMapPos.first, hud.marketMapPos.second))
          drawCircle(color = ElectricCyan.copy(alpha = 0.9f), radius = 8f * scale, center = toRadar(-48f, -118f))
          drawCircle(color = ElectricCyan.copy(alpha = 0.9f), radius = 7f * scale, center = toRadar(-196f, -12f))

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

          // House & Dropped Loot Markers on Radar
          for ((lx, lz, category) in hud.lootMapPos) {
            val lPos = toRadar(lx, lz)
            val lColor =
              when (category) {
                LootCategory.CURRENCY, LootCategory.WEAPON -> GoldenHour
                LootCategory.MEDICAL -> PalmMint
                LootCategory.VEST, LootCategory.HELMET, LootCategory.GLOO_WALL -> ElectricCyan
              }
            drawCircle(color = lColor.copy(alpha = 0.85f), radius = 1.9f, center = lPos)
          }

          // Active Gloo Walls on Radar
          for ((gx, gz) in hud.glooWallsMapPos) {
            drawCircle(color = ElectricCyan, radius = 2.6f, center = toRadar(gx, gz))
          }

          // Armed Rival Fighters on Radar in pulsing SunsetCoral
          for ((rx, rz) in hud.rivalsMapPos) {
            val rPos = toRadar(rx, rz)
            drawCircle(color = ViceVoid, radius = 5.2f, center = rPos)
            drawCircle(color = SunsetCoral, radius = 3.4f, center = rPos)
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

          // Express Train (only if active) & Patrolling Helicopters on radar
          hud.trainMapPos?.let { (tx, tz) ->
            val tPos = toRadar(tx, tz)
            drawCircle(color = ViceVoid, radius = 6.0f, center = tPos)
            drawCircle(color = SunsetCoral, radius = 4.0f, center = tPos)
          }
          for ((hx, hz) in hud.helicoptersMapPos) {
            val hPos = toRadar(hx, hz)
            drawCircle(color = ViceVoid, radius = 5.5f, center = hPos)
            drawCircle(color = ElectricCyan, radius = 3.5f, center = hPos)
          }

          // Aerial Drop Flight Path & Airplane Silhouette spanning Nextera Island
          if (hud.dropPhase == DropPhase.AIRPLANE || hud.planeCountdownSec > 0) {
            val pathStart = toRadar(-230f, -18f)
            val pathEnd = toRadar(230f, 14f)
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
