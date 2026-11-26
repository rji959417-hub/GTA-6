package com.example.game.state

import android.content.SharedPreferences

enum class HudElementId(
  val displayName: String,
  val shortTag: String,
  val categoryLabel: String,
) {
  JOYSTICK("Movement Joystick", "JOYSTICK", "CONTROLS"),
  SHOOT("Shoot / Fire Button", "SHOOT", "COMBAT"),
  SCOPE("ADS Scope Button", "SCOPE", "COMBAT"),
  JUMP("Jump / Parachute", "JUMP", "MOVEMENT"),
  SPRINT("Sprint Button", "SPRINT", "MOVEMENT"),
  GLOO_WALL("Gloo Wall Shield", "GLOO", "COMBAT"),
  PUNCH("Punch Button", "PUNCH", "COMBAT"),
  KICK("Kick Button", "KICK", "COMBAT"),
  SIT("Sit / Stand Button", "SIT", "MOVEMENT"),
  CONTEXT_ACTION("Climb / Drive / Buy", "ACTION", "CONTROLS"),
  DRIVING_PAD("Steer Left / Right", "STEER", "VEHICLE"),
  DRIVING_PEDALS("Gas, Brake & Drift", "PEDALS", "VEHICLE"),
  MINIMAP("Minimap Radar", "RADAR", "TOP HUD"),
  STATUS_BAR("HP, Cash & Zone", "HP / CASH", "TOP HUD"),
  VOICE_HUD("Mic & Speaker Bar", "VOICE", "TOP HUD"),
  TOP_UTILITIES("Time / Cam / Map", "TOP BAR", "TOP HUD"),
  ACTION_PILLS("Gun / Bag / Med / Shop", "BAG / GUN", "TOP HUD"),
  LOBBY_UI("Lobby Panels & Icons", "LOBBY", "LOBBY"),
}

data class HudOffset(
  val normX: Float = 0f,
  val normY: Float = 0f,
)

data class HudLayoutConfig(
  val masterScale: Float = 1.0f,
  val elementScales: Map<HudElementId, Float> = emptyMap(),
  val elementOffsets: Map<HudElementId, HudOffset> = emptyMap(),
) {
  fun rawScale(id: HudElementId): Float =
    (elementScales[id] ?: 1.0f).coerceIn(MIN_ELEMENT_SCALE, MAX_ELEMENT_SCALE)

  fun effectiveScale(id: HudElementId): Float =
    (masterScale * rawScale(id)).coerceIn(MIN_EFFECTIVE_SCALE, MAX_EFFECTIVE_SCALE)

  fun offsetOf(id: HudElementId): HudOffset =
    elementOffsets[id] ?: HudOffset(0f, 0f)

  fun withMasterScale(scale: Float): HudLayoutConfig =
    copy(masterScale = snapScale(scale).coerceIn(MIN_MASTER_SCALE, MAX_MASTER_SCALE))

  fun withElementScale(id: HudElementId, scale: Float): HudLayoutConfig {
    val snapped = snapScale(scale).coerceIn(MIN_ELEMENT_SCALE, MAX_ELEMENT_SCALE)
    val next = elementScales.toMutableMap()
    if (kotlin.math.abs(snapped - 1.0f) < 0.01f) {
      next.remove(id)
    } else {
      next[id] = snapped
    }
    return copy(elementScales = next)
  }

  fun withElementOffset(id: HudElementId, normX: Float, normY: Float): HudLayoutConfig {
    val cx = snapOffset(normX.coerceIn(MIN_OFFSET, MAX_OFFSET))
    val cy = snapOffset(normY.coerceIn(MIN_OFFSET, MAX_OFFSET))
    val next = elementOffsets.toMutableMap()
    if (kotlin.math.abs(cx) < 0.002f && kotlin.math.abs(cy) < 0.002f) {
      next.remove(id)
    } else {
      next[id] = HudOffset(cx, cy)
    }
    return copy(elementOffsets = next)
  }

  fun nudgeElementOffset(id: HudElementId, dNormX: Float, dNormY: Float): HudLayoutConfig {
    val cur = offsetOf(id)
    return withElementOffset(id, cur.normX + dNormX, cur.normY + dNormY)
  }

  fun resetElementOffset(id: HudElementId): HudLayoutConfig {
    if (!elementOffsets.containsKey(id)) return this
    val next = elementOffsets.toMutableMap()
    next.remove(id)
    return copy(elementOffsets = next)
  }

  fun resetElement(id: HudElementId): HudLayoutConfig {
    val nextScales = elementScales.toMutableMap().apply { remove(id) }
    val nextOffsets = elementOffsets.toMutableMap().apply { remove(id) }
    return copy(elementScales = nextScales, elementOffsets = nextOffsets)
  }

  fun resetAll(): HudLayoutConfig = DEFAULT

  fun serialize(): String {
    val parts = mutableListOf("MASTER=${formatScale(masterScale)}")
    for (id in HudElementId.entries) {
      val s = elementScales[id]
      if (s != null) {
        parts += "${id.name}=${formatScale(s)}"
      }
      val off = elementOffsets[id]
      if (off != null) {
        parts += "OFF_${id.name}=${formatOffset(off.normX)},${formatOffset(off.normY)}"
      }
    }
    return parts.joinToString(";")
  }

  companion object {
    const val MIN_ELEMENT_SCALE = 0.55f
    const val MAX_ELEMENT_SCALE = 1.60f
    const val MIN_MASTER_SCALE = 0.65f
    const val MAX_MASTER_SCALE = 1.45f
    const val MIN_EFFECTIVE_SCALE = 0.50f
    const val MAX_EFFECTIVE_SCALE = 1.75f
    const val MIN_OFFSET = -0.88f
    const val MAX_OFFSET = 0.88f

    val DEFAULT = HudLayoutConfig()

    private fun snapScale(value: Float): Float =
      kotlin.math.round(value * 20f) / 20f

    private fun snapOffset(value: Float): Float =
      kotlin.math.round(value * 1000f) / 1000f

    private fun formatScale(value: Float): String =
      String.format(java.util.Locale.US, "%.2f", value)

    private fun formatOffset(value: Float): String =
      String.format(java.util.Locale.US, "%.3f", value)

    fun deserialize(raw: String?): HudLayoutConfig {
      if (raw.isNullOrBlank()) return DEFAULT
      var master = 1.0f
      val scales = mutableMapOf<HudElementId, Float>()
      val offsets = mutableMapOf<HudElementId, HudOffset>()
      for (token in raw.split(";")) {
        val eq = token.indexOf('=')
        if (eq <= 0 || eq >= token.length - 1) continue
        val key = token.substring(0, eq).trim()
        val valRaw = token.substring(eq + 1).trim()
        if (key == "MASTER") {
          val parsed = valRaw.toFloatOrNull() ?: continue
          if (!parsed.isFinite()) continue
          master = snapScale(parsed).coerceIn(MIN_MASTER_SCALE, MAX_MASTER_SCALE)
        } else if (key.startsWith("OFF_")) {
          val id = HudElementId.entries.firstOrNull { it.name == key.removePrefix("OFF_") } ?: continue
          val coords = valRaw.split(",")
          if (coords.size != 2) continue
          val ox = coords[0].trim().toFloatOrNull() ?: continue
          val oy = coords[1].trim().toFloatOrNull() ?: continue
          if (!ox.isFinite() || !oy.isFinite()) continue
          val cx = snapOffset(ox.coerceIn(MIN_OFFSET, MAX_OFFSET))
          val cy = snapOffset(oy.coerceIn(MIN_OFFSET, MAX_OFFSET))
          if (kotlin.math.abs(cx) >= 0.002f || kotlin.math.abs(cy) >= 0.002f) {
            offsets[id] = HudOffset(cx, cy)
          }
        } else {
          val parsed = valRaw.toFloatOrNull() ?: continue
          if (!parsed.isFinite()) continue
          val id = HudElementId.entries.firstOrNull { it.name == key } ?: continue
          val clamped = snapScale(parsed).coerceIn(MIN_ELEMENT_SCALE, MAX_ELEMENT_SCALE)
          if (kotlin.math.abs(clamped - 1.0f) >= 0.01f) {
            scales[id] = clamped
          }
        }
      }
      return HudLayoutConfig(masterScale = master, elementScales = scales, elementOffsets = offsets)
    }
  }
}

object HudLayoutStore {
  private const val KEY_HUD_LAYOUT_V1 = "hud_layout_config_v1"

  fun load(prefs: SharedPreferences?): HudLayoutConfig {
    if (prefs == null) return HudLayoutConfig.DEFAULT
    return HudLayoutConfig.deserialize(prefs.getString(KEY_HUD_LAYOUT_V1, null))
  }

  fun save(prefs: SharedPreferences?, config: HudLayoutConfig) {
    prefs?.edit()?.putString(KEY_HUD_LAYOUT_V1, config.serialize())?.apply()
  }
}
