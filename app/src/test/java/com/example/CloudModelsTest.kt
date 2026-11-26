package com.example

import com.example.game.cloud.SquadRoom
import com.example.game.cloud.UserAccount
import com.example.game.cloud.generatePermanentPlayerCode
import com.example.game.cloud.normalizeLookupCandidates
import com.example.game.state.LobbyDropZone
import com.example.game.state.WeaponType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudModelsTest {
  @Test
  fun permanentPlayerCodeIsDeterministicAcrossLogins() {
    val uid = "user_abc_12345"
    val first = generatePermanentPlayerCode(uid)
    val second = generatePermanentPlayerCode(uid)
    assertEquals(first, second)
    assertTrue(first.matches(Regex("^VH-\\d{6}$")))
  }

  @Test
  fun lookupCandidatesNormalizePlayerCodesAndPeerIds() {
    val fromDigits = normalizeLookupCandidates("482910")
    assertTrue(fromDigits.contains("482910"))
    assertTrue(fromDigits.contains("VH-482910"))

    val fromFullCode = normalizeLookupCandidates(" vh-482910 ")
    assertTrue(fromFullCode.contains("VH-482910"))
    assertTrue(fromFullCode.contains("vh-482910"))

    val fromPeer = normalizeLookupCandidates("vh-k9x2ab")
    assertTrue(fromPeer.contains("vh-k9x2ab"))
  }

  @Test
  fun enumParsersFallbackSafelyForUnknownStrings() {
    val account =
      UserAccount(
        equippedWeapon = "UNKNOWN_WEAPON_V9",
        ownedWeapons = listOf("UNKNOWN_WEAPON_V9", WeaponType.SNIPER_RIFLE.name),
      )
    assertEquals(WeaponType.FISTS, account.safeWeapon())
    assertEquals(setOf(WeaponType.FISTS, WeaponType.SNIPER_RIFLE), account.safeOwnedWeapons())

    val room = SquadRoom(dropZone = "UNKNOWN_ZONE")
    assertEquals(LobbyDropZone.FULL_WORLD, room.safeDropZone())
  }
}
