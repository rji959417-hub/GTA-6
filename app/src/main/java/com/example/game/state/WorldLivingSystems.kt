package com.example.game.state

import com.example.game.world.CartoonPresets
import com.example.game.world.CharacterAppearance

enum class WeatherMode(
  val label: String,
  val shortTag: String,
  val rainAmount: Float,
  val stormFactor: Float,
) {
  CLEAR_BREEZE("CLEAR BREEZE", "CLEAR", 0f, 0f),
  LIGHT_RAIN("TROPICAL RAIN", "RAIN", 0.55f, 0.35f),
  THUNDERSTORM("MONSOON STORM", "STORM", 1.0f, 0.95f),
  MISTY_PETALS("MISTY PETALS", "MIST", 0.25f, 0.15f),
}

data class WaterfallSpec(
  val id: String,
  val x: Float,
  val topY: Float,
  val baseY: Float,
  val z: Float,
  val width: Float,
  val poolRadius: Float,
)

enum class MarketStallKind(
  val label: String,
  val canopyColor: FloatArray,
  val crateColor: FloatArray,
  val itemNames: List<String>,
) {
  VEGETABLES(
    label = "FRESH VEGETABLES",
    canopyColor = floatArrayOf(0.18f, 0.72f, 0.34f),
    crateColor = floatArrayOf(0.88f, 0.24f, 0.20f),
    itemNames = listOf("Tomatoes", "Carrots", "Spinach", "Pumpkins"),
  ),
  FRUITS(
    label = "TROPICAL FRUITS",
    canopyColor = floatArrayOf(0.96f, 0.62f, 0.16f),
    crateColor = floatArrayOf(0.96f, 0.84f, 0.18f),
    itemNames = listOf("Mangoes", "Bananas", "Melons", "Oranges"),
  ),
  SPICES(
    label = "MASALA & SPICES",
    canopyColor = floatArrayOf(0.90f, 0.24f, 0.28f),
    crateColor = floatArrayOf(0.92f, 0.48f, 0.14f),
    itemNames = listOf("Saffron", "Turmeric", "Cardamom", "Chili"),
  ),
  POTTERY(
    label = "CLAY POTTERY",
    canopyColor = floatArrayOf(0.18f, 0.68f, 0.82f),
    crateColor = floatArrayOf(0.76f, 0.44f, 0.26f),
    itemNames = listOf("Terracotta Pot", "Clay Pitcher", "Glazed Bowl"),
  ),
  TEXTILES(
    label = "CUSTOM CLOTHS",
    canopyColor = floatArrayOf(0.82f, 0.26f, 0.76f),
    crateColor = floatArrayOf(0.24f, 0.82f, 0.92f),
    itemNames = listOf("Silk Scarf", "Bazaar Shawl", "Neon Tunic"),
  ),
  BAKERY(
    label = "CHAI & SNACKS",
    canopyColor = floatArrayOf(0.94f, 0.78f, 0.22f),
    crateColor = floatArrayOf(0.74f, 0.50f, 0.28f),
    itemNames = listOf("Hot Samosa", "Masala Chai", "Sweet Bun"),
  ),
}

data class MarketStallSpec(
  val id: String,
  val name: String,
  val kind: MarketStallKind,
  val x: Float,
  val z: Float,
  val browseX: Float,
  val browseZ: Float,
  val yawDeg: Float = 0f,
)

enum class MarketBotActivity {
  WALKING,
  INSPECTING,
  BUYING,
}

data class MarketBotState(
  val id: String,
  val waypoints: List<Pair<Float, Float>>,
  val speed: Float,
  var x: Float,
  var z: Float,
  var y: Float = 0.08f,
  val appearance: CharacterAppearance = CartoonPresets.LEAN_BUDDY_BLUE,
  var yawRad: Float = 0f,
  var targetIdx: Int = 1,
  var walkCycle: Float = 0f,
  var activity: MarketBotActivity = MarketBotActivity.WALKING,
  var pauseTimerSec: Float = 0f,
  var currentStallName: String = "",
  var boughtItemName: String = "",
  var carryingBag: Boolean = false,
  var bagColor: FloatArray = floatArrayOf(0.20f, 0.78f, 0.36f),
  var armsUpNorm: Float = 0f,
)

data class TrainRuntimeState(
  var progressDist: Float = 0f,
  val speed: Float = 15.5f,
  var x: Float = 0f,
  var y: Float = 0.28f,
  var z: Float = -82f,
  var yawRad: Float = 1.57f,
  val carCount: Int = 4,
  val carSpacing: Float = 7.4f,
)

data class HelicopterRuntimeState(
  val id: String,
  val waypoints: List<Triple<Float, Float, Float>>,
  val bodyColor: FloatArray,
  val stripeColor: FloatArray,
  var x: Float,
  var y: Float,
  var z: Float,
  var yawRad: Float = 0f,
  var targetIdx: Int = 1,
  var speed: Float = 16.5f,
  var rotorAngleDeg: Float = 0f,
  var pitchRad: Float = 0f,
  var rollRad: Float = 0f,
  var hoverTimerSec: Float = 0f,
)

data class AmbientAirplaneState(
  val id: String,
  val centerX: Float,
  val centerZ: Float,
  val radiusX: Float,
  val radiusZ: Float,
  val altitudeY: Float,
  var angleRad: Float,
  val angularSpeed: Float,
  val bodyColor: FloatArray,
  val accentColor: FloatArray,
  var x: Float = centerX,
  var y: Float = altitudeY,
  var z: Float = centerZ,
  var yawRad: Float = 0f,
  var rollRad: Float = 0f,
)

enum class BirdKind {
  SEAGULL,
  EAGLE,
}

data class BirdRuntimeState(
  val id: String,
  val kind: BirdKind,
  val centerX: Float,
  val centerZ: Float,
  val radiusX: Float,
  val radiusZ: Float,
  val baseY: Float,
  var angleRad: Float,
  val angularSpeed: Float,
  var flapPhase: Float,
  val wingSpan: Float,
  var x: Float = centerX,
  var y: Float = baseY,
  var z: Float = centerZ,
  var yawRad: Float = 0f,
  var bankRad: Float = 0f,
)

data class CloudPuffState(
  var x: Float,
  val y: Float,
  var z: Float,
  val scaleX: Float,
  val scaleY: Float,
  val scaleZ: Float,
  val driftSpeedX: Float,
  val driftSpeedZ: Float,
  val seed: Float,
)

data class WeatherParticleState(
  var x: Float,
  var y: Float,
  var z: Float,
  val speedY: Float,
  val driftX: Float,
  val length: Float,
)

data class WaterRippleState(
  val x: Float,
  val z: Float,
  var ageSec: Float = 0f,
  val maxAgeSec: Float = 1.6f,
  val maxRadius: Float = 4.5f,
  val intensity: Float = 0.85f,
)

data class AmbientAudioSnapshot(
  val trafficLevel: Float = 0f,
  val trainLevel: Float = 0f,
  val helicopterLevel: Float = 0f,
  val birdLevel: Float = 0f,
  val marketPeopleLevel: Float = 0f,
  val waterSurfLevel: Float = 0f,
  val rainLevel: Float = 0f,
  val thunderPulse: Float = 0f,
)
