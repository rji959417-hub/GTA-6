package com.example.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.game.state.HudSnapshot
import com.example.game.state.WeaponType
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldenHour
import com.example.ui.theme.NeonPink
import com.example.ui.theme.PalmMint
import com.example.ui.theme.SunsetCoral
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ViceSurfaceSolid
import com.example.ui.theme.ViceVoid

@Composable
fun GunShopModal(
  hud: HudSnapshot,
  onBuyOrEquip: (WeaponType) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(
    modifier =
      modifier
        .fillMaxSize()
        .background(ViceVoid.copy(alpha = 0.78f))
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(14.dp),
    contentAlignment = Alignment.Center,
  ) {
    val wideLayout = maxWidth >= 560.dp
    val compactMobile = maxHeight <= 500.dp || maxWidth <= 950.dp

    Column(
      modifier =
        Modifier.widthIn(max = if (wideLayout) 620.dp else 460.dp)
          .clip(RoundedCornerShape(if (compactMobile) 16.dp else 24.dp))
          .background(ViceSurfaceSolid)
          .border(1.2.dp, GoldenHour.copy(alpha = 0.65f), RoundedCornerShape(if (compactMobile) 16.dp else 24.dp))
          .verticalScroll(rememberScrollState())
          .padding(if (compactMobile) 12.dp else 18.dp),
      verticalArrangement = Arrangement.spacedBy(if (compactMobile) 6.dp else 10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Filled.Storefront,
            contentDescription = "Vice Arms Gun Shop",
            tint = GoldenHour,
            modifier = Modifier.size(26.dp),
          )
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "VICE ARMS · GUN SHOP",
              style = MaterialTheme.typography.headlineMedium,
              color = TextPrimary,
            )
            Text(
              text = "Buy weapons anytime or equip your arsenal",
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary,
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "$${hud.playerCash}",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = PalmMint,
            modifier =
              Modifier.clip(RoundedCornerShape(10.dp))
                .background(PalmMint.copy(alpha = 0.14f))
                .border(1.dp, PalmMint.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("gun_shop_cash"),
          )
          IconButton(
            onClick = onClose,
            modifier = Modifier.size(48.dp).testTag("close_gun_shop_button"),
          ) {
            Icon(Icons.Filled.Close, contentDescription = "Close Gun Shop", tint = TextPrimary)
          }
        }
      }

      WeaponType.entries.forEach { weapon ->
        val owned = weapon in hud.ownedWeapons
        val equipped = hud.equippedWeapon == weapon
        val canAfford = hud.playerCash >= weapon.price

        val subtitle =
          when (weapon) {
            WeaponType.FISTS -> "Punch Jab / Hook / Uppercut + Front & Roundhouse Kicks"
            WeaponType.PISTOL_9MM -> "High precision semi-auto sidearm · Range 38m"
            WeaponType.SMG_MICRO -> "Rapid-fire street automatic · 10 rounds/sec"
            WeaponType.SHOTGUN -> "5-pellet wide scatter blast · Massive knockback"
            WeaponType.SNIPER_RIFLE -> "Long-range scoped rifle (105m) · Precision headshots"
            WeaponType.ASSAULT_RIFLE -> "Long-range tactical rifle · High stopping power"
          }

        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(if (compactMobile) 11.dp else 16.dp))
              .background(ViceVoid.copy(alpha = 0.65f))
              .border(
                width = if (equipped) 1.4.dp else 0.9.dp,
                color =
                  when {
                    equipped -> ElectricCyan
                    owned -> PalmMint.copy(alpha = 0.45f)
                    else -> SunsetCoral.copy(alpha = 0.35f)
                  },
                shape = RoundedCornerShape(if (compactMobile) 11.dp else 16.dp),
              )
              .padding(horizontal = if (compactMobile) 10.dp else 14.dp, vertical = if (compactMobile) 6.dp else 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
          ) {
            Icon(
              imageVector = if (weapon == WeaponType.FISTS) Icons.Filled.SportsMma else Icons.Filled.GpsFixed,
              contentDescription = weapon.displayName,
              tint = if (equipped) ElectricCyan else GoldenHour,
              modifier = Modifier.size(if (compactMobile) 18.dp else 22.dp),
            )
            Spacer(modifier = Modifier.width(if (compactMobile) 7.dp else 10.dp))
            Column {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  weapon.displayName,
                  style = if (compactMobile) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                  color = TextPrimary,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "DMG ${weapon.damage.toInt()}${if (weapon.pelletCount > 1) "×${weapon.pelletCount}" else ""}",
                  style = MaterialTheme.typography.labelSmall,
                  color = GoldenHour,
                )
              }
              Text(
                subtitle,
                style = if (compactMobile) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
              )
            }
          }

          Button(
            onClick = { onBuyOrEquip(weapon) },
            enabled = owned || canAfford,
            colors =
              ButtonDefaults.buttonColors(
                containerColor =
                  when {
                    equipped -> ElectricCyan
                    owned -> PalmMint
                    canAfford -> NeonPink
                    else -> Color(0xFF393948)
                  },
                contentColor = ViceVoid,
              ),
            shape = RoundedCornerShape(if (compactMobile) 9.dp else 12.dp),
            contentPadding =
              if (compactMobile) androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
              else ButtonDefaults.ContentPadding,
            modifier = Modifier.height(if (compactMobile) 34.dp else 48.dp).testTag("weapon_action_${weapon.name}"),
          ) {
            if (equipped) {
              Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("EQUIPPED", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            } else if (owned) {
              Text("EQUIP", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            } else {
              Text("BUY $${weapon.price}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
          }
        }
      }
    }
  }
}
