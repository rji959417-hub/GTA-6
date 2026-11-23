package com.example.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.game.state.HudSnapshot
import com.example.game.state.TimeOfDayPreset
import com.example.game.world.HouseSpec
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TitleScreenOverlay(
  onPlayClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(
    modifier =
      modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors =
              listOf(
                ViceVoid.copy(alpha = 0.42f),
                ViceVoid.copy(alpha = 0.22f),
                ViceVoid.copy(alpha = 0.86f),
              )
          )
        )
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 24.dp, vertical = 14.dp),
    contentAlignment = Alignment.BottomCenter,
  ) {
    val isWideLandscape = maxWidth >= 560.dp && maxHeight < 500.dp

    if (isWideLandscape) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier =
          Modifier.widthIn(max = 780.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(ViceSurface.copy(alpha = 0.84f))
            .border(
              1.5.dp,
              Brush.linearGradient(listOf(NeonPink, GoldenHour, ElectricCyan)),
              RoundedCornerShape(24.dp),
            )
            .padding(horizontal = 24.dp, vertical = 18.dp),
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.tagline_subtitle),
            style = MaterialTheme.typography.labelSmall,
            color = ElectricCyan,
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "VICE HORIZON",
            style = MaterialTheme.typography.displayMedium,
            color = TextPrimary,
          )
          Spacer(modifier = Modifier.height(8.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            FeatureChip(Icons.Filled.FlightTakeoff, "Plane & Chute Drop", ElectricCyan)
            FeatureChip(Icons.Filled.Home, "3 Maps · Rooftops", PalmMint)
            FeatureChip(Icons.Filled.SportsMma, "Hand & Foot Fight", NeonPink)
            FeatureChip(Icons.Filled.Storefront, "Gun Shop", GoldenHour)
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = stringResource(R.string.label_controls_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
          )
        }

        Button(
          onClick = onPlayClick,
          colors =
            ButtonDefaults.buttonColors(containerColor = NeonPink, contentColor = ViceVoid),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.width(190.dp).height(56.dp).testTag("play_button"),
        ) {
          Icon(Icons.Filled.PlayArrow, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.action_play),
            style = MaterialTheme.typography.labelLarge,
          )
        }
      }
    } else {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
          Modifier.widthIn(max = 540.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(ViceSurface.copy(alpha = 0.84f))
            .border(
              1.5.dp,
              Brush.linearGradient(listOf(NeonPink, GoldenHour, ElectricCyan)),
              RoundedCornerShape(28.dp),
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 22.dp),
      ) {
        Text(
          text = stringResource(R.string.tagline_subtitle),
          style = MaterialTheme.typography.labelSmall,
          color = ElectricCyan,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "VICE HORIZON",
          style = MaterialTheme.typography.displayLarge,
          color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FeatureChip(Icons.Filled.FlightTakeoff, "Plane & Chute Drop", ElectricCyan)
          FeatureChip(Icons.Filled.Home, "3 Maps · Rooftops", PalmMint)
          FeatureChip(Icons.Filled.SportsMma, "Hand & Foot Fight", NeonPink)
          FeatureChip(Icons.Filled.Storefront, "Gun Shop", GoldenHour)
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
          text = stringResource(R.string.label_controls_hint),
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(18.dp))

        Button(
          onClick = onPlayClick,
          colors =
            ButtonDefaults.buttonColors(containerColor = NeonPink, contentColor = ViceVoid),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth().height(54.dp).testTag("play_button"),
        ) {
          Icon(Icons.Filled.PlayArrow, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.action_play),
            style = MaterialTheme.typography.labelLarge,
          )
        }
      }
    }
  }
}

@Composable
private fun FeatureChip(icon: ImageVector, label: String, color: Color) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.clip(RoundedCornerShape(50))
        .background(color.copy(alpha = 0.14f))
        .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
        .padding(horizontal = 10.dp, vertical = 5.dp),
  ) {
    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
    Spacer(modifier = Modifier.width(5.dp))
    Text(label, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PauseMapModal(
  hud: HudSnapshot,
  houses: List<HouseSpec>,
  onResume: () -> Unit,
  onTeleportHouse: (Int) -> Unit,
  onTeleportGunShop: () -> Unit,
  onTeleportPier: () -> Unit,
  onSummonCar: () -> Unit,
  onResetWorld: () -> Unit,
  onSelectTime: (TimeOfDayPreset) -> Unit,
  onCycleCamera: () -> Unit,
  onAirplaneDrop: () -> Unit,
  onReturnToLobby: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(
    modifier =
      modifier
        .fillMaxSize()
        .background(ViceVoid.copy(alpha = 0.72f))
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(16.dp),
    contentAlignment = Alignment.Center,
  ) {
    val wideModal = maxWidth >= 560.dp
    Column(
      modifier =
        Modifier.widthIn(max = if (wideModal) 680.dp else 520.dp)
          .clip(RoundedCornerShape(24.dp))
          .background(ViceSurfaceSolid)
          .border(1.5.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
          .verticalScroll(rememberScrollState())
          .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text("VICE HORIZON WORLD MAP", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
          Text(
            text = "Rooftop Town ↔ Key Bayside ↔ Green Valley · 3D Multi-Floor Maps",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
          )
        }
        Button(
          onClick = onResume,
          colors = ButtonDefaults.buttonColors(containerColor = NeonPink, contentColor = ViceVoid),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.testTag("resume_button"),
        ) {
          Text(stringResource(R.string.action_resume), fontWeight = FontWeight.Bold)
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Vice Arms Gun Shop Fast Travel Card
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(ViceVoid.copy(alpha = 0.65f))
              .border(1.dp, SunsetCoral.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
              .clickable(onClick = onTeleportGunShop)
              .padding(horizontal = 14.dp, vertical = 10.dp)
              .testTag("teleport_gun_shop"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              Icons.Filled.Storefront,
              contentDescription = "Vice Arms Gun Shop",
              tint = SunsetCoral,
              modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text("Vice Arms Gun Shop", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
              Text("Pistols, SMGs, Shotguns & Rifles · Cash $${hud.playerCash}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
          }
          Text("SHOP", style = MaterialTheme.typography.labelLarge, color = SunsetCoral)
        }

        houses.forEachIndexed { index, house ->
          val accent = if (house.floors >= 2) PalmMint else GoldenHour
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ViceVoid.copy(alpha = 0.6f))
                .border(1.dp, accent.copy(alpha = 0.42f), RoundedCornerShape(14.dp))
                .clickable { onTeleportHouse(index) }
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("teleport_house_$index"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                Icons.Filled.Home,
                contentDescription = house.name,
                tint = accent,
                modifier = Modifier.size(22.dp),
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(house.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(house.subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
              }
            }
            Text(
              text =
                when {
                  house.hasRooftop -> "ROOF · 2F"
                  house.floors >= 2 -> "2F · GO"
                  else -> "GO"
                },
              style = MaterialTheme.typography.labelLarge,
              color = if (house.floors >= 2) PalmMint else ElectricCyan,
            )
          }
        }

        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(ViceVoid.copy(alpha = 0.6f))
              .border(1.dp, PalmMint.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
              .clickable(onClick = onTeleportPier)
              .padding(horizontal = 14.dp, vertical = 10.dp)
              .testTag("teleport_boat_pier"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              Icons.Filled.DirectionsBoat,
              contentDescription = "Marina Boat Pier",
              tint = PalmMint,
              modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text("Marina Boat Pier", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
              Text("2 Docked Speedboats · Open Ocean Sailing", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
          }
          Text("SAIL", style = MaterialTheme.typography.labelLarge, color = PalmMint)
        }
      }

      Text("ATMOSPHERE", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeOfDayPreset.entries.forEach { preset ->
          val active = hud.timePreset == preset
          Box(
            modifier =
              Modifier.clip(RoundedCornerShape(12.dp))
                .background(if (active) SunsetCoral.copy(alpha = 0.25f) else ViceVoid)
                .border(
                  1.dp,
                  if (active) SunsetCoral else Color.White.copy(alpha = 0.2f),
                  RoundedCornerShape(12.dp),
                )
                .clickable { onSelectTime(preset) }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("time_preset_${preset.name}")
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                Icons.Filled.WbTwilight,
                contentDescription = null,
                tint = if (active) GoldenHour else TextSecondary,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(preset.label, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
            }
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = onSummonCar,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f).testTag("summon_car_button"),
        ) {
          Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(5.dp))
          Text("CAR", color = TextPrimary, style = MaterialTheme.typography.labelSmall)
        }
        OutlinedButton(
          onClick = onAirplaneDrop,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f).testTag("plane_redrop_button"),
        ) {
          Icon(Icons.Filled.FlightTakeoff, contentDescription = null, tint = NeonPink, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(5.dp))
          Text("PLANE DROP", color = TextPrimary, style = MaterialTheme.typography.labelSmall)
        }
        OutlinedButton(
          onClick = onCycleCamera,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f).testTag("cycle_camera_modal_button"),
        ) {
          Icon(Icons.Filled.Videocam, contentDescription = null, tint = GoldenHour, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(5.dp))
          Text(hud.cameraMode.label, color = TextPrimary, style = MaterialTheme.typography.labelSmall)
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = onResetWorld,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f).testTag("reset_world_button"),
        ) {
          Icon(Icons.Filled.Refresh, contentDescription = null, tint = NeonPink, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.action_reset_pos), color = TextPrimary, style = MaterialTheme.typography.labelSmall)
        }
        OutlinedButton(
          onClick = onReturnToLobby,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f).testTag("return_to_lobby_button"),
        ) {
          Icon(Icons.Filled.Home, contentDescription = null, tint = GoldenHour, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("BACK TO LOBBY", color = GoldenHour, style = MaterialTheme.typography.labelSmall)
        }
      }
    }
  }
}
