package com.example.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.example.game.state.CarSkin
import com.example.game.state.ClothStyle
import com.example.game.state.GunSkin
import com.example.game.state.HairCustomization
import com.example.game.state.HudSnapshot
import com.example.game.state.PantsStyle
import com.example.game.state.StyleCategory
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldenHour
import com.example.ui.theme.NeonPink
import com.example.ui.theme.PalmMint
import com.example.ui.theme.SunsetCoral
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ViceSurfaceSolid
import com.example.ui.theme.ViceVoid

private data class StyleCatalogRow(
  val enumName: String,
  val title: String,
  val subtitle: String,
  val price: Int,
  val accentColor: Color,
  val secondaryColor: Color?,
  val owned: Boolean,
  val equipped: Boolean,
  val onSelect: () -> Unit,
)

@Composable
fun StyleShopModal(
  hud: HudSnapshot,
  onSelectCloth: (ClothStyle) -> Unit,
  onSelectHair: (HairCustomization) -> Unit,
  onSelectPants: (PantsStyle) -> Unit,
  onSelectGunSkin: (GunSkin) -> Unit,
  onSelectCarSkin: (CarSkin) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedCategory by remember { mutableStateOf(StyleCategory.CLOTHES) }

  val rows: List<StyleCatalogRow> =
    when (selectedCategory) {
      StyleCategory.CLOTHES ->
        ClothStyle.entries.map { item ->
          StyleCatalogRow(
            enumName = item.name,
            title = item.displayName,
            subtitle = item.subtitle,
            price = item.price,
            accentColor = Color(item.accentHex),
            secondaryColor =
              item.vestColor?.let { c -> Color(c[0], c[1], c[2], 1f) },
            owned = item in hud.ownedCloths,
            equipped = hud.equippedCloth == item,
            onSelect = { onSelectCloth(item) },
          )
        }
      StyleCategory.HAIR ->
        HairCustomization.entries.map { item ->
          StyleCatalogRow(
            enumName = item.name,
            title = item.displayName,
            subtitle = item.subtitle,
            price = item.price,
            accentColor = Color(item.accentHex),
            secondaryColor = null,
            owned = item in hud.ownedHairs,
            equipped = hud.equippedHair == item,
            onSelect = { onSelectHair(item) },
          )
        }
      StyleCategory.PANTS ->
        PantsStyle.entries.map { item ->
          StyleCatalogRow(
            enumName = item.name,
            title = item.displayName,
            subtitle = item.subtitle,
            price = item.price,
            accentColor = Color(item.accentHex),
            secondaryColor = null,
            owned = item in hud.ownedPants,
            equipped = hud.equippedPants == item,
            onSelect = { onSelectPants(item) },
          )
        }
      StyleCategory.GUN_SKIN ->
        GunSkin.entries.map { item ->
          StyleCatalogRow(
            enumName = item.name,
            title = item.displayName,
            subtitle = item.subtitle,
            price = item.price,
            accentColor = Color(item.accentHex),
            secondaryColor =
              Color(item.secondaryColor[0], item.secondaryColor[1], item.secondaryColor[2], 1f),
            owned = item in hud.ownedGunSkins,
            equipped = hud.equippedGunSkin == item,
            onSelect = { onSelectGunSkin(item) },
          )
        }
      StyleCategory.CAR_SKIN ->
        CarSkin.entries.map { item ->
          StyleCatalogRow(
            enumName = item.name,
            title = item.displayName,
            subtitle = item.subtitle,
            price = item.price,
            accentColor = Color(item.accentHex),
            secondaryColor =
              item.trimColor?.let { c -> Color(c[0], c[1], c[2], 1f) },
            owned = item in hud.ownedCarSkins,
            equipped = hud.equippedCarSkin == item,
            onSelect = { onSelectCarSkin(item) },
          )
        }
    }

  BoxWithConstraints(
    modifier =
      modifier
        .fillMaxSize()
        .background(ViceVoid.copy(alpha = 0.78f))
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(14.dp)
        .testTag("style_shop_modal"),
    contentAlignment = Alignment.Center,
  ) {
    val compactMobile = maxHeight <= 500.dp || maxWidth <= 950.dp
    val wideLayout = maxWidth >= 560.dp

    Column(
      modifier =
        Modifier.widthIn(max = if (wideLayout) (if (compactMobile) 620.dp else 740.dp) else 460.dp)
          .heightIn(max = maxHeight - 12.dp)
          .clip(RoundedCornerShape(if (compactMobile) 16.dp else 24.dp))
          .background(ViceSurfaceSolid)
          .border(1.3.dp, NeonPink.copy(alpha = 0.65f), RoundedCornerShape(if (compactMobile) 16.dp else 24.dp))
          .padding(if (compactMobile) 12.dp else 18.dp),
      verticalArrangement = Arrangement.spacedBy(if (compactMobile) 8.dp else 12.dp),
    ) {
      // Header row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f).padding(end = 8.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Palette,
            contentDescription = "Style & Skins Store",
            tint = NeonPink,
            modifier = Modifier.size(if (compactMobile) 20.dp else 26.dp),
          )
          Spacer(modifier = Modifier.width(if (compactMobile) 7.dp else 10.dp))
          Column {
            Text(
              text = "VICE STYLE · SKINS & OUTFITS",
              style =
                (if (compactMobile) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)
                  .copy(fontWeight = FontWeight.ExtraBold),
              color = TextPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "Spend match coins on clothes, hair, pants, gun paint & car skins",
              style = MaterialTheme.typography.bodySmall.copy(fontSize = if (compactMobile) 10.sp else 12.sp),
              color = TextSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = "$${hud.playerCash}",
            style =
              (if (compactMobile) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium)
                .copy(fontWeight = FontWeight.Bold),
            color = GoldenHour,
            modifier =
              Modifier.clip(RoundedCornerShape(if (compactMobile) 8.dp else 10.dp))
                .background(GoldenHour.copy(alpha = 0.14f))
                .border(1.dp, GoldenHour.copy(alpha = 0.55f), RoundedCornerShape(if (compactMobile) 8.dp else 10.dp))
                .padding(horizontal = if (compactMobile) 8.dp else 10.dp, vertical = if (compactMobile) 4.dp else 6.dp)
                .testTag("style_shop_cash"),
          )
          IconButton(
            onClick = onClose,
            modifier = Modifier.size(if (compactMobile) 34.dp else 48.dp).testTag("close_style_shop_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = "Close Style Store",
              tint = TextPrimary,
              modifier = Modifier.size(if (compactMobile) 18.dp else 24.dp),
            )
          }
        }
      }

      // Category Tabs
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      ) {
        StyleCategory.entries.forEach { category ->
          val selected = category == selectedCategory
          val tabIcon: ImageVector =
            when (category) {
              StyleCategory.CLOTHES -> Icons.Filled.Checkroom
              StyleCategory.HAIR -> Icons.Filled.Face
              StyleCategory.PANTS -> Icons.Filled.Brush
              StyleCategory.GUN_SKIN -> Icons.Filled.GpsFixed
              StyleCategory.CAR_SKIN -> Icons.Filled.DirectionsCar
            }
          val accent =
            when (category) {
              StyleCategory.CLOTHES -> NeonPink
              StyleCategory.HAIR -> GoldenHour
              StyleCategory.PANTS -> ElectricCyan
              StyleCategory.GUN_SKIN -> SunsetCoral
              StyleCategory.CAR_SKIN -> PalmMint
            }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier =
              Modifier.let { m ->
                if (compactMobile) m.height(32.dp) else m.minimumInteractiveComponentSize().defaultMinSize(minHeight = 48.dp)
              }
                .clip(RoundedCornerShape(if (compactMobile) 9.dp else 12.dp))
                .background(
                  if (selected) accent.copy(alpha = 0.24f) else ViceVoid.copy(alpha = 0.58f)
                )
                .border(
                  width = if (selected) 1.3.dp else 0.9.dp,
                  color = if (selected) accent else Color.White.copy(alpha = 0.16f),
                  shape = RoundedCornerShape(if (compactMobile) 9.dp else 12.dp),
                )
                .clickable { selectedCategory = category }
                .padding(horizontal = if (compactMobile) 8.dp else 12.dp, vertical = if (compactMobile) 4.dp else 8.dp)
                .testTag("style_tab_${category.name}"),
          ) {
            Icon(
              imageVector = tabIcon,
              contentDescription = category.label,
              tint = if (selected) accent else TextSecondary,
              modifier = Modifier.size(if (compactMobile) 14.dp else 18.dp),
            )
            Text(
              text = category.label,
              style =
                MaterialTheme.typography.labelSmall.copy(
                  fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                ),
              color = if (selected) TextPrimary else TextSecondary,
            )
          }
        }
      }

      // Items List
      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
      ) {
        items(rows, key = { "${selectedCategory.name}_${it.enumName}" }) { row ->
          val canAfford = hud.playerCash >= row.price
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(if (compactMobile) 11.dp else 16.dp))
                .background(ViceVoid.copy(alpha = 0.68f))
                .border(
                  width = if (row.equipped) 1.4.dp else 0.9.dp,
                  color =
                    when {
                      row.equipped -> ElectricCyan
                      row.owned -> PalmMint.copy(alpha = 0.45f)
                      else -> row.accentColor.copy(alpha = 0.38f)
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
              // Color Finish Swatch
              Box(
                modifier =
                  Modifier.size(if (compactMobile) 26.dp else 34.dp)
                    .clip(CircleShape)
                    .background(
                      if (row.secondaryColor != null) {
                        Brush.linearGradient(listOf(row.accentColor, row.secondaryColor))
                      } else {
                        Brush.linearGradient(listOf(row.accentColor, row.accentColor.copy(alpha = 0.72f)))
                      }
                    )
                    .border(1.2.dp, Color.White.copy(alpha = 0.55f), CircleShape)
              )
              Spacer(modifier = Modifier.width(if (compactMobile) 8.dp else 12.dp))
              Column {
                Text(
                  text = row.title,
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = TextPrimary,
                )
                Text(
                  text = row.subtitle,
                  style = MaterialTheme.typography.bodySmall,
                  color = TextSecondary,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }

            Button(
              onClick = row.onSelect,
              enabled = row.owned || canAfford,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor =
                    when {
                      row.equipped -> ElectricCyan
                      row.owned -> PalmMint
                      canAfford -> NeonPink
                      else -> Color(0xFF393948)
                    },
                  contentColor = ViceVoid,
                  disabledContainerColor = Color(0xFF2D2E3B),
                  disabledContentColor = TextSecondary,
                ),
              shape = RoundedCornerShape(if (compactMobile) 9.dp else 12.dp),
              contentPadding =
                if (compactMobile) PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                else ButtonDefaults.ContentPadding,
              modifier = Modifier.height(if (compactMobile) 34.dp else 48.dp).testTag("style_item_${row.enumName}"),
            ) {
              when {
                row.equipped -> {
                  Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("EQUIPPED", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                }
                row.owned -> Text("EQUIP", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                else -> Text("BUY $${row.price}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
              }
            }
          }
        }
      }
    }
  }
}
