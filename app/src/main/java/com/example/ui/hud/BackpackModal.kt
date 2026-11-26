package com.example.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.state.HudSnapshot
import com.example.game.state.LootCategory
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldenHour
import com.example.ui.theme.PalmMint
import com.example.ui.theme.SunsetCoral
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ViceSurfaceSolid
import com.example.ui.theme.ViceVoid

@Composable
fun BackpackModal(
  hud: HudSnapshot,
  onUseOrEquip: (String) -> Unit,
  onRemove: (String) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(
    modifier =
      modifier
        .fillMaxSize()
        .background(ViceVoid.copy(alpha = 0.80f))
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(14.dp)
        .testTag("bag_modal"),
    contentAlignment = Alignment.Center,
  ) {
    val wideLayout = maxWidth >= 560.dp
    val bagAccent = if (hud.isBagFull) SunsetCoral else ElectricCyan

    Column(
      modifier =
        Modifier.widthIn(max = if (wideLayout) 700.dp else 520.dp)
          .clip(RoundedCornerShape(24.dp))
          .background(ViceSurfaceSolid)
          .border(1.5.dp, bagAccent.copy(alpha = 0.72f), RoundedCornerShape(24.dp))
          .verticalScroll(rememberScrollState())
          .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Header + Slot Capacity Badge + Close
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Filled.Backpack,
            contentDescription = "Tactical Backpack",
            tint = bagAccent,
            modifier = Modifier.size(26.dp),
          )
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "TACTICAL BACKPACK",
              style = MaterialTheme.typography.headlineMedium,
              color = TextPrimary,
            )
            Text(
              text =
                if (hud.isBagFull) "Bag full! Remove items to drop & loot new gear"
                else "Auto-looted guns, medkits & Lv.3–4 armor",
              style = MaterialTheme.typography.bodyMedium,
              color = if (hud.isBagFull) SunsetCoral else TextSecondary,
            )
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "${hud.bagItems.size}/${hud.bagCapacity}",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = bagAccent,
            modifier =
              Modifier.clip(RoundedCornerShape(10.dp))
                .background(bagAccent.copy(alpha = 0.16f))
                .border(1.dp, bagAccent.copy(alpha = 0.50f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("bag_slot_counter"),
          )
          IconButton(
            onClick = onClose,
            modifier = Modifier.size(48.dp).testTag("close_bag_button"),
          ) {
            Icon(Icons.Filled.Close, contentDescription = "Close Backpack", tint = TextPrimary)
          }
        }
      }

      // Equipped Tactical Armor & Loadout Summary Card
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ViceVoid.copy(alpha = 0.70f))
            .border(1.dp, GoldenHour.copy(alpha = 0.42f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "EQUIPPED ARMOR & WEAPON",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = GoldenHour,
          )
          Text(
            text = "HP ${hud.playerHealth}/100 · ${hud.equippedWeapon.displayName}",
            style = MaterialTheme.typography.labelSmall,
            color = PalmMint,
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          EquippedArmorBox(
            label = if (hud.vestLevel >= 3) "VEST LV.${hud.vestLevel}" else "NO VEST",
            durability = hud.vestDurability,
            maxDurability = hud.vestMaxDurability,
            level = hud.vestLevel,
            icon = Icons.Filled.Shield,
            modifier = Modifier.weight(1f),
          )
          EquippedArmorBox(
            label = if (hud.helmetLevel >= 3) "HELMET LV.${hud.helmetLevel}" else "NO HELMET",
            durability = hud.helmetDurability,
            maxDurability = hud.helmetMaxDurability,
            level = hud.helmetLevel,
            icon = Icons.Filled.Security,
            modifier = Modifier.weight(1f),
          )
        }
      }

      // Bag Loot Items
      if (hud.bagItems.isEmpty()) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(ViceVoid.copy(alpha = 0.55f))
              .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
              .padding(vertical = 26.dp, horizontal = 16.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "Backpack is empty. Enter houses across all 3 islands to auto-loot Guns, Medkits, Coins, and Lv.3–4 Vests & Helmets!",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
          )
        }
      } else {
        hud.bagItems.forEach { item ->
          val itemAccent =
            when (item.kind.category) {
              LootCategory.MEDICAL -> PalmMint
              LootCategory.WEAPON -> GoldenHour
              LootCategory.VEST, LootCategory.HELMET ->
                if (item.kind.armorLevel >= 4) GoldenHour else ElectricCyan
              LootCategory.CURRENCY -> GoldenHour
              LootCategory.GLOO_WALL -> ElectricCyan
            }
          val itemIcon =
            when (item.kind.category) {
              LootCategory.MEDICAL -> Icons.Filled.MedicalServices
              LootCategory.WEAPON -> Icons.Filled.GpsFixed
              LootCategory.VEST -> Icons.Filled.Shield
              LootCategory.HELMET -> Icons.Filled.Security
              LootCategory.CURRENCY -> Icons.Filled.Backpack
              LootCategory.GLOO_WALL -> Icons.Filled.Shield
            }
          val actionLabel =
            when (item.kind.category) {
              LootCategory.MEDICAL -> "HEAL"
              LootCategory.WEAPON ->
                if (hud.equippedWeapon == item.kind.weapon) "ACTIVE" else "EQUIP"
              LootCategory.VEST, LootCategory.HELMET -> "EQUIP"
              LootCategory.CURRENCY -> "USE"
              LootCategory.GLOO_WALL -> "DEPLOY"
            }
          val detailText =
            when (item.kind.category) {
              LootCategory.MEDICAL -> "Restores +${item.kind.healAmount.toInt()} HP"
              LootCategory.WEAPON -> "DMG ${item.kind.weapon?.damage?.toInt() ?: 0} · Range ${item.kind.weapon?.range?.toInt() ?: 0}m"
              LootCategory.VEST, LootCategory.HELMET ->
                "Level ${item.kind.armorLevel} Armor · Durability ${item.durability.toInt()}"
              LootCategory.CURRENCY -> "$${item.kind.coinValue}"
              LootCategory.GLOO_WALL -> "Deploys tactical ice shield cover (240 HP)"
            }

          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ViceVoid.copy(alpha = 0.68f))
                .border(1.dp, itemAccent.copy(alpha = 0.42f), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.weight(1f).padding(end = 8.dp),
            ) {
              Icon(
                imageVector = itemIcon,
                contentDescription = item.kind.displayName,
                tint = itemAccent,
                modifier = Modifier.size(22.dp),
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = item.kind.displayName,
                  style = MaterialTheme.typography.titleMedium,
                  color = TextPrimary,
                )
                Text(
                  text = detailText,
                  style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                  color = TextSecondary,
                )
              }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
              Button(
                onClick = { onUseOrEquip(item.uid) },
                enabled =
                  !(item.kind.category == LootCategory.WEAPON && hud.equippedWeapon == item.kind.weapon),
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = itemAccent,
                    contentColor = ViceVoid,
                    disabledContainerColor = itemAccent.copy(alpha = 0.35f),
                    disabledContentColor = TextPrimary,
                  ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp).testTag("bag_use_${item.uid}"),
              ) {
                Text(actionLabel, fontWeight = FontWeight.Bold, fontSize = 12.sp)
              }

              OutlinedButton(
                onClick = { onRemove(item.uid) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SunsetCoral),
                border = androidx.compose.foundation.BorderStroke(1.dp, SunsetCoral.copy(alpha = 0.68f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp).testTag("bag_remove_${item.uid}"),
              ) {
                Icon(
                  Icons.Filled.DeleteOutline,
                  contentDescription = "Remove ${item.kind.displayName}",
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text("DROP", fontWeight = FontWeight.Bold, fontSize = 11.sp)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun EquippedArmorBox(
  label: String,
  durability: Int,
  maxDurability: Int,
  level: Int,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  modifier: Modifier = Modifier,
) {
  val accent =
    when {
      level >= 4 -> GoldenHour
      level == 3 -> ElectricCyan
      else -> TextSecondary
    }
  val frac = if (maxDurability > 0) (durability.toFloat() / maxDurability.toFloat()).coerceIn(0f, 1f) else 0f

  Column(
    modifier =
      modifier
        .clip(RoundedCornerShape(12.dp))
        .background(ViceSurfaceSolid.copy(alpha = 0.72f))
        .border(1.dp, accent.copy(alpha = if (level >= 3) 0.60f else 0.20f), RoundedCornerShape(12.dp))
        .padding(horizontal = 10.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = accent)
      }
      if (level >= 3) {
        Text("$durability/$maxDurability", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextPrimary)
      }
    }
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(6.dp)
          .clip(RoundedCornerShape(3.dp))
          .background(Color.White.copy(alpha = 0.12f))
    ) {
      if (level >= 3) {
        Box(
          modifier =
            Modifier.fillMaxWidth(frac)
              .fillMaxHeight()
              .clip(RoundedCornerShape(3.dp))
              .background(accent)
        )
      }
    }
  }
}
