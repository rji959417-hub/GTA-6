package com.example.game.cloud

import androidx.annotation.Keep
import com.example.game.state.LobbyDropZone
import com.example.game.state.WeaponType
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.abs

@Keep
@IgnoreExtraProperties
data class UserAccount(
  @DocumentId val id: String = "",
  val userId: String = "",
  val playerCode: String = "",
  val displayName: String = "VICE HERO",
  val email: String? = null,
  val phoneNumber: String? = null,
  val authProvider: String = "GOOGLE",
  val heroIndex: Long = 0L,
  val equippedWeapon: String = WeaponType.FISTS.name,
  val ownedWeapons: List<String> = listOf(WeaponType.FISTS.name),
  val playerCash: Long = 1200L,
  val koCount: Long = 0L,
  val peerId: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun safeWeapon(): WeaponType =
    WeaponType.entries.firstOrNull { it.name == equippedWeapon } ?: WeaponType.FISTS

  fun safeOwnedWeapons(): Set<WeaponType> {
    val parsed =
      ownedWeapons.mapNotNull { name -> WeaponType.entries.firstOrNull { it.name == name } }
    return if (parsed.isEmpty()) setOf(WeaponType.FISTS) else parsed.toSet() + WeaponType.FISTS
  }
}

@Keep
@IgnoreExtraProperties
data class InviteCodeEntry(
  @DocumentId val id: String = "",
  val userId: String = "",
  val playerCode: String = "",
  val peerId: String = "",
  val displayName: String = "VICE HERO",
  val heroIndex: Long = 0L,
  val weapon: String = WeaponType.FISTS.name,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun safeWeapon(): WeaponType =
    WeaponType.entries.firstOrNull { it.name == weapon } ?: WeaponType.FISTS
}

@Keep
@IgnoreExtraProperties
data class SquadRoom(
  @DocumentId val id: String = "",
  val hostUid: String = "",
  val status: String = "LOBBY",
  val dropZone: String = LobbyDropZone.FULL_WORLD.name,
  val memberUids: List<String> = emptyList(),
  val invitedUids: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun safeDropZone(): LobbyDropZone =
    LobbyDropZone.entries.firstOrNull { it.name == dropZone } ?: LobbyDropZone.FULL_WORLD
}

@Keep
@IgnoreExtraProperties
data class SquadMemberDoc(
  @DocumentId val id: String = "",
  val userId: String = "",
  val visibleUids: List<String> = emptyList(),
  val displayName: String = "VICE HERO",
  val playerCode: String = "",
  val peerId: String = "",
  val heroIndex: Long = 0L,
  val weapon: String = WeaponType.FISTS.name,
  val ready: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun safeWeapon(): WeaponType =
    WeaponType.entries.firstOrNull { it.name == weapon } ?: WeaponType.FISTS
}

@Keep
@IgnoreExtraProperties
data class SquadInviteDoc(
  @DocumentId val id: String = "",
  val senderUid: String = "",
  val recipientUid: String = "",
  val senderName: String = "VICE HERO",
  val senderCode: String = "",
  val senderPeerId: String = "",
  val senderHeroIndex: Long = 0L,
  val senderWeapon: String = WeaponType.FISTS.name,
  val squadId: String = "",
  val status: String = "PENDING",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun safeWeapon(): WeaponType =
    WeaponType.entries.firstOrNull { it.name == senderWeapon } ?: WeaponType.FISTS
}

@Keep
@IgnoreExtraProperties
data class VoiceSignalDoc(
  @DocumentId val id: String = "",
  val squadId: String = "",
  val senderUid: String = "",
  val recipientUid: String = "",
  val type: String = "",
  val payload: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

fun generatePermanentPlayerCode(uid: String): String {
  var hash = 2166136261L
  for (ch in uid) {
    hash = (hash xor ch.code.toLong()) * 16777619L
  }
  val sixDigits = 100000L + (abs(hash) % 900000L)
  return "VH-$sixDigits"
}

fun normalizeLookupCandidates(rawInput: String): List<String> {
  val trimmed = rawInput.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "")
  if (trimmed.isEmpty()) return emptyList()
  val candidates = LinkedHashSet<String>()
  candidates.add(trimmed)
  candidates.add(trimmed.uppercase())
  candidates.add(trimmed.lowercase())
  if (trimmed.all { it.isDigit() } && trimmed.length in 4..10) {
    candidates.add("VH-$trimmed")
    candidates.add("vh-$trimmed")
  }
  return candidates.filter { it.matches(Regex("^[a-zA-Z0-9_\\-]+$")) && it.length <= 64 }
}
