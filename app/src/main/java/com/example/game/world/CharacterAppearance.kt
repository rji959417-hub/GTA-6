package com.example.game.world

enum class BodyBuild {
  ROUND_CHUBBY,
  TALL_LEAN,
  ATHLETIC_TOON,
}

enum class HairStyle {
  BALD_FRINGE,
  SWOOP_TOP,
  ROUND_AFRO,
  CAP_HAT,
  SPIKY_CROWN,
  MOHAWK_NEON,
}

data class CharacterAppearance(
  val build: BodyBuild,
  val hairStyle: HairStyle,
  val skinColor: FloatArray,
  val tunicColor: FloatArray,
  val vestColor: FloatArray?,
  val pantsColor: FloatArray,
  val shoeColor: FloatArray,
  val hairColor: FloatArray,
  val hasMustache: Boolean = true,
  val hasRoundGlasses: Boolean = false,
  val brassButtons: Boolean = true,
)

object CartoonPresets {
  // Exact match to uploaded 3D turnaround sheet:
  // Bald top + black spiky side/back fringe, big nose, curled black handlebar mustache,
  // bright red rotund kurta, dark navy vest with brass buttons, mustard yellow pants, dark brown shoes.
  val PLAYER_HERO =
    CharacterAppearance(
      build = BodyBuild.ROUND_CHUBBY,
      hairStyle = HairStyle.BALD_FRINGE,
      skinColor = floatArrayOf(0.95f, 0.70f, 0.54f),
      tunicColor = floatArrayOf(0.86f, 0.10f, 0.11f),
      vestColor = floatArrayOf(0.08f, 0.11f, 0.28f),
      pantsColor = floatArrayOf(0.94f, 0.74f, 0.06f),
      shoeColor = floatArrayOf(0.22f, 0.13f, 0.08f),
      hairColor = floatArrayOf(0.08f, 0.07f, 0.08f),
      hasMustache = true,
      hasRoundGlasses = false,
      brassButtons = true,
    )

  // Tall lean bespectacled best-friend buddy
  val LEAN_BUDDY_BLUE =
    CharacterAppearance(
      build = BodyBuild.TALL_LEAN,
      hairStyle = HairStyle.SWOOP_TOP,
      skinColor = floatArrayOf(0.94f, 0.72f, 0.56f),
      tunicColor = floatArrayOf(0.14f, 0.54f, 0.90f),
      vestColor = null,
      pantsColor = floatArrayOf(0.92f, 0.90f, 0.84f),
      shoeColor = floatArrayOf(0.24f, 0.15f, 0.10f),
      hairColor = floatArrayOf(0.10f, 0.08f, 0.08f),
      hasMustache = true,
      hasRoundGlasses = true,
      brassButtons = false,
    )

  // Tall lean yellow-kurta buddy
  val LEAN_BUDDY_GOLD =
    CharacterAppearance(
      build = BodyBuild.TALL_LEAN,
      hairStyle = HairStyle.SWOOP_TOP,
      skinColor = floatArrayOf(0.90f, 0.66f, 0.48f),
      tunicColor = floatArrayOf(0.96f, 0.76f, 0.16f),
      vestColor = floatArrayOf(0.44f, 0.22f, 0.12f),
      pantsColor = floatArrayOf(0.95f, 0.94f, 0.90f),
      shoeColor = floatArrayOf(0.20f, 0.12f, 0.08f),
      hairColor = floatArrayOf(0.09f, 0.08f, 0.09f),
      hasMustache = false,
      hasRoundGlasses = true,
      brassButtons = true,
    )

  // Jovial round emerald kurta uncle
  val CHUBBY_EMERALD =
    CharacterAppearance(
      build = BodyBuild.ROUND_CHUBBY,
      hairStyle = HairStyle.BALD_FRINGE,
      skinColor = floatArrayOf(0.92f, 0.68f, 0.50f),
      tunicColor = floatArrayOf(0.10f, 0.66f, 0.36f),
      vestColor = floatArrayOf(0.92f, 0.88f, 0.74f),
      pantsColor = floatArrayOf(0.95f, 0.94f, 0.92f),
      shoeColor = floatArrayOf(0.26f, 0.14f, 0.08f),
      hairColor = floatArrayOf(0.14f, 0.13f, 0.14f),
      hasMustache = true,
      hasRoundGlasses = false,
      brassButtons = true,
    )

  // Round saffron kurta neighbor
  val CHUBBY_SAFFRON =
    CharacterAppearance(
      build = BodyBuild.ROUND_CHUBBY,
      hairStyle = HairStyle.ROUND_AFRO,
      skinColor = floatArrayOf(0.86f, 0.61f, 0.45f),
      tunicColor = floatArrayOf(0.96f, 0.52f, 0.12f),
      vestColor = floatArrayOf(0.38f, 0.10f, 0.16f),
      pantsColor = floatArrayOf(0.20f, 0.24f, 0.36f),
      shoeColor = floatArrayOf(0.18f, 0.12f, 0.09f),
      hairColor = floatArrayOf(0.08f, 0.07f, 0.07f),
      hasMustache = true,
      hasRoundGlasses = false,
      brassButtons = true,
    )

  // Cartoon town inspector with cap & thick mustache
  val INSPECTOR_KHAKI =
    CharacterAppearance(
      build = BodyBuild.ATHLETIC_TOON,
      hairStyle = HairStyle.CAP_HAT,
      skinColor = floatArrayOf(0.92f, 0.68f, 0.52f),
      tunicColor = floatArrayOf(0.76f, 0.64f, 0.42f),
      vestColor = floatArrayOf(0.32f, 0.22f, 0.12f),
      pantsColor = floatArrayOf(0.68f, 0.56f, 0.36f),
      shoeColor = floatArrayOf(0.12f, 0.10f, 0.10f),
      hairColor = floatArrayOf(0.12f, 0.10f, 0.10f),
      hasMustache = true,
      hasRoundGlasses = false,
      brassButtons = true,
    )

  // Coral sunset resort neighbor
  val CHUBBY_CORAL =
    CharacterAppearance(
      build = BodyBuild.ROUND_CHUBBY,
      hairStyle = HairStyle.SWOOP_TOP,
      skinColor = floatArrayOf(0.94f, 0.70f, 0.54f),
      tunicColor = floatArrayOf(0.96f, 0.32f, 0.54f),
      vestColor = floatArrayOf(0.12f, 0.18f, 0.36f),
      pantsColor = floatArrayOf(0.94f, 0.92f, 0.86f),
      shoeColor = floatArrayOf(0.25f, 0.15f, 0.10f),
      hairColor = floatArrayOf(0.10f, 0.08f, 0.08f),
      hasMustache = true,
      hasRoundGlasses = false,
      brassButtons = true,
    )

  // Teal coastal captain / boardwalk buddy
  val COASTAL_CAPTAIN =
    CharacterAppearance(
      build = BodyBuild.ATHLETIC_TOON,
      hairStyle = HairStyle.CAP_HAT,
      skinColor = floatArrayOf(0.90f, 0.66f, 0.50f),
      tunicColor = floatArrayOf(0.14f, 0.74f, 0.78f),
      vestColor = floatArrayOf(0.10f, 0.15f, 0.32f),
      pantsColor = floatArrayOf(0.94f, 0.94f, 0.96f),
      shoeColor = floatArrayOf(0.18f, 0.20f, 0.26f),
      hairColor = floatArrayOf(0.12f, 0.10f, 0.10f),
      hasMustache = true,
      hasRoundGlasses = false,
      brassButtons = true,
    )

  // Royal purple & gold neighbor
  val CHUBBY_PURPLE =
    CharacterAppearance(
      build = BodyBuild.ROUND_CHUBBY,
      hairStyle = HairStyle.BALD_FRINGE,
      skinColor = floatArrayOf(0.93f, 0.69f, 0.52f),
      tunicColor = floatArrayOf(0.56f, 0.24f, 0.84f),
      vestColor = floatArrayOf(0.94f, 0.74f, 0.14f),
      pantsColor = floatArrayOf(0.96f, 0.95f, 0.92f),
      shoeColor = floatArrayOf(0.22f, 0.14f, 0.09f),
      hairColor = floatArrayOf(0.12f, 0.11f, 0.12f),
      hasMustache = true,
      hasRoundGlasses = true,
      brassButtons = true,
    )

  // Crisp mint tropical stroller
  val ATHLETIC_MINT =
    CharacterAppearance(
      build = BodyBuild.ATHLETIC_TOON,
      hairStyle = HairStyle.ROUND_AFRO,
      skinColor = floatArrayOf(0.84f, 0.58f, 0.42f),
      tunicColor = floatArrayOf(0.22f, 0.86f, 0.62f),
      vestColor = null,
      pantsColor = floatArrayOf(0.16f, 0.22f, 0.36f),
      shoeColor = floatArrayOf(0.92f, 0.92f, 0.94f),
      hairColor = floatArrayOf(0.08f, 0.07f, 0.07f),
      hasMustache = false,
      hasRoundGlasses = false,
      brassButtons = false,
    )
}
