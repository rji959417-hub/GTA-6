package com.example.game.cloud

import android.content.Context
import com.example.R
import com.example.game.state.LobbyDropZone
import com.example.game.state.WeaponType
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ViceCloudRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in before accessing cloud game data.")

  suspend fun ensureOrLoadAccount(
    user: FirebaseUser,
    displayNameOverride: String? = null,
  ): Result<UserAccount> {
    val uid = user.uid
    val docPath = "users/$uid"
    return try {
      require(requireUserId() == uid) { "Active user mismatch" }
      val docRef = db.collection("users").document(uid)
      val existingSnap = docRef.get().await()
      val account =
        if (existingSnap.exists()) {
          val loaded =
            existingSnap.toObject(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
              ?: UserAccount(userId = uid, playerCode = generatePermanentPlayerCode(uid))
          loaded.copy(id = existingSnap.id, userId = loaded.userId.ifBlank { uid })
        } else {
          val provider =
            if (!user.phoneNumber.isNullOrBlank() && user.email.isNullOrBlank()) "PHONE"
            else "GOOGLE"
          val rawName =
            displayNameOverride?.trim()?.takeIf { it.isNotEmpty() }
              ?: user.displayName?.trim()?.takeIf { it.isNotEmpty() }
              ?: user.email?.substringBefore('@')?.takeIf { it.isNotBlank() }
              ?: user.phoneNumber?.takeLast(4)?.let { "PLAYER-$it" }
              ?: "VICE HERO"
          val safeName = rawName.take(30)
          val code = generatePermanentPlayerCode(uid)
          val createMap =
            linkedMapOf<String, Any?>(
              "userId" to uid,
              "playerCode" to code,
              "displayName" to safeName,
              "email" to user.email?.take(160)?.takeIf { it.isNotBlank() },
              "phoneNumber" to user.phoneNumber?.take(30)?.takeIf { it.isNotBlank() },
              "authProvider" to provider,
              "heroIndex" to 0L,
              "equippedWeapon" to WeaponType.FISTS.name,
              "ownedWeapons" to listOf(WeaponType.FISTS.name),
              "playerCash" to 1200L,
              "koCount" to 0L,
              "peerId" to "",
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null } as Map<String, Any>
          docRef.set(createMap).await()
          UserAccount(
            id = uid,
            userId = uid,
            playerCode = code,
            displayName = safeName,
            email = user.email,
            phoneNumber = user.phoneNumber,
            authProvider = provider,
            heroIndex = 0L,
            equippedWeapon = WeaponType.FISTS.name,
            ownedWeapons = listOf(WeaponType.FISTS.name),
            playerCash = 1200L,
            koCount = 0L,
            peerId = "",
          )
        }

      publishInviteCodeAliases(
        account = account,
        livePeerId = account.peerId,
        heroIndex = account.heroIndex.toInt(),
        weapon = account.safeWeapon(),
      )
      Result.success(account)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docPath)
      Result.failure(e)
    }
  }

  private suspend fun publishInviteCodeAliases(
    account: UserAccount,
    livePeerId: String,
    heroIndex: Int,
    weapon: WeaponType,
  ) {
    val uid = account.userId
    if (uid.isBlank() || account.playerCode.isBlank()) return
    val basePayload =
      mapOf(
        "userId" to uid,
        "playerCode" to account.playerCode,
        "peerId" to livePeerId.take(64),
        "displayName" to account.displayName.take(30).ifBlank { "VICE HERO" },
        "heroIndex" to heroIndex.coerceIn(0, 10).toLong(),
        "weapon" to weapon.name,
        "updatedAt" to FieldValue.serverTimestamp(),
      )

    val aliases = LinkedHashSet<String>()
    aliases.add(account.playerCode.uppercase())
    val digitsOnly = account.playerCode.removePrefix("VH-")
    if (digitsOnly.matches(Regex("^[A-Za-z0-9_\\-]+$"))) {
      aliases.add(digitsOnly)
    }
    if (livePeerId.isNotBlank() && livePeerId.matches(Regex("^[A-Za-z0-9_\\-]+$"))) {
      aliases.add(livePeerId.lowercase())
    }

    for (codeKey in aliases) {
      try {
        db.collection("invite_codes").document(codeKey).set(basePayload).await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "invite_codes/$codeKey")
      }
    }
  }

  suspend fun saveProgression(
    account: UserAccount,
    heroIndex: Int,
    equippedWeapon: WeaponType,
    ownedWeapons: Set<WeaponType>,
    playerCash: Int,
    koCount: Int,
    livePeerId: String,
  ): Result<Unit> {
    val uid = account.userId
    val docPath = "users/$uid"
    return try {
      check(requireUserId() == uid) { "Signed-in user changed" }
      val updates =
        mapOf(
          "heroIndex" to heroIndex.coerceIn(0, 10).toLong(),
          "equippedWeapon" to equippedWeapon.name,
          "ownedWeapons" to ownedWeapons.map { it.name }.take(10),
          "playerCash" to playerCash.coerceIn(0, 100_000_000).toLong(),
          "koCount" to koCount.coerceIn(0, 10_000_000).toLong(),
          "peerId" to livePeerId.take(64),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      db.collection("users").document(uid).update(updates).await()
      publishInviteCodeAliases(account, livePeerId, heroIndex, equippedWeapon)
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun lookupTargetByCodeOrPeerId(rawInput: String): Result<InviteCodeEntry?> {
    val candidates = normalizeLookupCandidates(rawInput)
    if (candidates.isEmpty()) return Result.success(null)
    return try {
      requireUserId()
      for (candidate in candidates) {
        val docPath = "invite_codes/$candidate"
        val snap = db.collection("invite_codes").document(candidate).get().await()
        if (snap.exists()) {
          val entry = snap.toObject(InviteCodeEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          if (entry != null && entry.userId.isNotBlank()) {
            return Result.success(entry)
          }
        }
      }
      Result.success(null)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, "invite_codes/${candidates.first()}")
      Result.failure(e)
    }
  }

  suspend fun sendCloudSquadInvite(
    account: UserAccount,
    target: InviteCodeEntry,
    livePeerId: String,
    heroIndex: Int,
    weapon: WeaponType,
    dropZone: LobbyDropZone,
    existingSquadId: String?,
  ): Result<String> {
    val uid = account.userId
    return try {
      check(requireUserId() == uid) { "Signed-in user changed" }
      require(target.userId != uid) { "Cannot invite yourself" }
      val canReuseSquad =
        existingSquadId?.takeIf { it.isNotBlank() }?.let { candId ->
          val candSnap = db.collection("squads").document(candId).get().await()
          val candRoom = candSnap.toObject(SquadRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          candSnap.exists() && candRoom?.hostUid == uid
        } ?: false
      val squadId =
        if (canReuseSquad) existingSquadId!!
        else "sq_${uid.take(12)}_${System.currentTimeMillis()}"
      val squadRef = db.collection("squads").document(squadId)
      val existingSquad = squadRef.get().await()
      val invitedSet = LinkedHashSet<String>()
      val memberSet = LinkedHashSet<String>()
      if (existingSquad.exists()) {
        val room = existingSquad.toObject(SquadRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        if (room != null) {
          invitedSet.addAll(room.invitedUids)
          memberSet.addAll(room.memberUids)
        }
      }
      invitedSet.add(uid)
      invitedSet.add(target.userId)
      memberSet.add(uid)

      if (!existingSquad.exists()) {
        squadRef.set(
          mapOf(
            "hostUid" to uid,
            "status" to "LOBBY",
            "dropZone" to dropZone.name,
            "memberUids" to memberSet.toList().take(4),
            "invitedUids" to invitedSet.toList().take(8),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      } else {
        squadRef.update(
          mapOf(
            "status" to "LOBBY",
            "dropZone" to dropZone.name,
            "memberUids" to memberSet.toList().take(4),
            "invitedUids" to invitedSet.toList().take(8),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      }

      val visibleList = invitedSet.toList().take(8)
      val memberRef = squadRef.collection("members").document(uid)
      val memberSnap = memberRef.get().await()
      if (!memberSnap.exists()) {
        memberRef.set(
          mapOf(
            "userId" to uid,
            "visibleUids" to visibleList,
            "displayName" to account.displayName.take(30),
            "playerCode" to account.playerCode,
            "peerId" to livePeerId.take(64),
            "heroIndex" to heroIndex.coerceIn(0, 10).toLong(),
            "weapon" to weapon.name,
            "ready" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      } else {
        memberRef.update(
          mapOf(
            "visibleUids" to visibleList,
            "displayName" to account.displayName.take(30),
            "playerCode" to account.playerCode,
            "peerId" to livePeerId.take(64),
            "heroIndex" to heroIndex.coerceIn(0, 10).toLong(),
            "weapon" to weapon.name,
            "ready" to true,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      }

      val inviteRef = db.collection("squad_invites").document()
      inviteRef.set(
        mapOf(
          "senderUid" to uid,
          "recipientUid" to target.userId,
          "senderName" to account.displayName.take(30),
          "senderCode" to account.playerCode,
          "senderPeerId" to livePeerId.take(64),
          "senderHeroIndex" to heroIndex.coerceIn(0, 10).toLong(),
          "senderWeapon" to weapon.name,
          "squadId" to squadId,
          "status" to "PENDING",
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()

      Result.success(squadId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "squad_invites")
      Result.failure(e)
    }
  }

  fun observeIncomingInvites(uid: String): Flow<List<SquadInviteDoc>> {
    val path = "squad_invites"
    return db.collection(path)
      .whereEqualTo("recipientUid", uid)
      .whereEqualTo("status", "PENDING")
      .snapshots()
      .map { snap -> snap.toObjects(SquadInviteDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
      .catch { error ->
        val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        if (fsErr == null && error is CancellationException) throw error
        if (auth.currentUser?.uid == uid) {
          handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
        }
        throw error
      }
  }

  fun observeSentInvites(uid: String): Flow<List<SquadInviteDoc>> {
    val path = "squad_invites"
    return db.collection(path)
      .whereEqualTo("senderUid", uid)
      .snapshots()
      .map { snap -> snap.toObjects(SquadInviteDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
      .catch { error ->
        val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        if (fsErr == null && error is CancellationException) throw error
        if (auth.currentUser?.uid == uid) {
          handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
        }
        throw error
      }
  }

  suspend fun respondToCloudInvite(
    invite: SquadInviteDoc,
    accept: Boolean,
    account: UserAccount,
    livePeerId: String,
    heroIndex: Int,
    weapon: WeaponType,
  ): Result<String?> {
    val uid = account.userId
    val invitePath = "squad_invites/${invite.id}"
    return try {
      check(requireUserId() == uid) { "Signed-in user changed" }
      db.collection("squad_invites").document(invite.id).update(
        mapOf(
          "status" to if (accept) "ACCEPTED" else "DECLINED",
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()

      if (!accept) {
        return Result.success(null)
      }

      val squadRef = db.collection("squads").document(invite.squadId)
      val squadSnap = squadRef.get().await()
      val room = squadSnap.toObject(SquadRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        ?: throw IllegalStateException("Squad lobby no longer exists")
      if (uid !in room.memberUids && room.memberUids.size >= 4) {
        throw IllegalStateException("Squad is already full (4/4)")
      }
      val nextMembers = (room.memberUids + uid).distinct().take(4)
      squadRef.update(
        mapOf(
          "memberUids" to nextMembers,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()

      val visibleList = (room.invitedUids + nextMembers).distinct().take(8)
      val memberRef = squadRef.collection("members").document(uid)
      val memberSnap = memberRef.get().await()
      if (!memberSnap.exists()) {
        memberRef.set(
          mapOf(
            "userId" to uid,
            "visibleUids" to visibleList,
            "displayName" to account.displayName.take(30),
            "playerCode" to account.playerCode,
            "peerId" to livePeerId.take(64),
            "heroIndex" to heroIndex.coerceIn(0, 10).toLong(),
            "weapon" to weapon.name,
            "ready" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      } else {
        memberRef.update(
          mapOf(
            "visibleUids" to visibleList,
            "displayName" to account.displayName.take(30),
            "playerCode" to account.playerCode,
            "peerId" to livePeerId.take(64),
            "heroIndex" to heroIndex.coerceIn(0, 10).toLong(),
            "weapon" to weapon.name,
            "ready" to true,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      }

      Result.success(invite.squadId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, invitePath)
      Result.failure(e)
    }
  }

  fun observeSquadRoom(squadId: String, uid: String): Flow<SquadRoom?> {
    val path = "squads/$squadId"
    return db.collection("squads").document(squadId)
      .snapshots()
      .map { snap ->
        if (!snap.exists()) null
        else snap.toObject(SquadRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .catch { error ->
        val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        if (fsErr == null && error is CancellationException) throw error
        if (auth.currentUser?.uid == uid) {
          handleFirestoreError(fsErr ?: error, OperationType.GET, path)
        }
        throw error
      }
  }

  fun observeSquadMembers(squadId: String, uid: String): Flow<List<SquadMemberDoc>> {
    val path = "squads/$squadId/members"
    return db.collection("squads").document(squadId).collection("members")
      .whereArrayContains("visibleUids", uid)
      .snapshots()
      .map { snap -> snap.toObjects(SquadMemberDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
      .catch { error ->
        val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        if (fsErr == null && error is CancellationException) throw error
        if (auth.currentUser?.uid == uid) {
          handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
        }
        throw error
      }
  }

  suspend fun syncMySquadMemberState(
    squadId: String,
    account: UserAccount,
    visibleUids: List<String>,
    livePeerId: String,
    heroIndex: Int,
    weapon: WeaponType,
  ): Result<Unit> {
    val uid = account.userId
    val path = "squads/$squadId/members/$uid"
    return try {
      check(requireUserId() == uid) { "Signed-in user changed" }
      val finalVisible = (visibleUids + uid).distinct().take(8)
      val memberRef = db.collection("squads").document(squadId).collection("members").document(uid)
      val snap = memberRef.get().await()
      if (snap.exists()) {
        memberRef.update(
          mapOf(
            "visibleUids" to finalVisible,
            "displayName" to account.displayName.take(30),
            "playerCode" to account.playerCode,
            "peerId" to livePeerId.take(64),
            "heroIndex" to heroIndex.coerceIn(0, 10).toLong(),
            "weapon" to weapon.name,
            "ready" to true,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun updateSquadRoomState(
    squadId: String,
    uid: String,
    status: String? = null,
    dropZone: LobbyDropZone? = null,
  ): Result<Unit> {
    val path = "squads/$squadId"
    return try {
      check(requireUserId() == uid) { "Signed-in user changed" }
      val updates = linkedMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
      if (status != null) updates["status"] = status
      if (dropZone != null) updates["dropZone"] = dropZone.name
      db.collection("squads").document(squadId).update(updates).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun sendVoiceSignal(
    squadId: String,
    senderUid: String,
    recipientUid: String,
    type: String,
    payload: String,
  ): Result<Unit> {
    val path = "squads/$squadId/voice_signals"
    return try {
      check(requireUserId() == senderUid) { "Signed-in user changed" }
      if (recipientUid.isBlank() || recipientUid == senderUid) return Result.success(Unit)
      if (payload.isBlank() || payload.length > 16384) return Result.success(Unit)
      db.collection("squads").document(squadId).collection("voice_signals").document().set(
        mapOf(
          "squadId" to squadId,
          "senderUid" to senderUid,
          "recipientUid" to recipientUid,
          "type" to type,
          "payload" to payload,
          "createdAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  fun observeIncomingVoiceSignals(squadId: String, uid: String): Flow<List<VoiceSignalDoc>> {
    val path = "squads/$squadId/voice_signals"
    return db.collection("squads").document(squadId).collection("voice_signals")
      .whereEqualTo("recipientUid", uid)
      .snapshots()
      .map { snap -> snap.toObjects(VoiceSignalDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
      .catch { error ->
        val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        if (fsErr == null && error is CancellationException) throw error
        if (auth.currentUser?.uid == uid) {
          handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
        }
        throw error
      }
  }

  suspend fun deleteVoiceSignal(squadId: String, signalId: String) {
    if (squadId.isBlank() || signalId.isBlank()) return
    runCatching {
      db.collection("squads").document(squadId).collection("voice_signals").document(signalId).delete().await()
    }
  }

  suspend fun leaveSquad(squadId: String, uid: String): Result<Unit> {
    val path = "squads/$squadId"
    return try {
      check(requireUserId() == uid) { "Signed-in user changed" }
      val squadRef = db.collection("squads").document(squadId)
      val snap = squadRef.get().await()
      val room = snap.toObject(SquadRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      runCatching { squadRef.collection("members").document(uid).delete().await() }
      if (room != null) {
        val remaining = room.memberUids.filter { it != uid }
        if (remaining.isEmpty() && room.hostUid == uid) {
          squadRef.delete().await()
        } else if (remaining.isNotEmpty()) {
          squadRef.update(
            mapOf(
              "memberUids" to remaining,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          ).await()
        }
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }
}
