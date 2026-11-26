package com.example.game.state

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.game.ai.MultilingualAiAssistant
import com.example.game.audio.SoundEngine
import com.example.game.cloud.SquadInviteDoc
import com.example.game.cloud.SquadRoom
import com.example.game.cloud.UserAccount
import com.example.game.cloud.ViceCloudRepository
import com.example.game.cloud.generatePermanentPlayerCode
import com.example.game.voice.IncomingSquadInvite
import com.example.game.voice.VoiceChatManager
import com.example.game.world.WorldBuilder
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class ViceGameViewModel(
  private val cloudRepository: ViceCloudRepository? = null,
  private val currentUserId: String = "",
  private val initialFirebaseUser: FirebaseUser? = null,
  private val localPrefs: SharedPreferences? = null,
) : ViewModel() {
  val world = WorldBuilder.buildKeyBayside()
  val simulation = GameSimulation(world)
  private val soundEngine = SoundEngine()
  val voiceChatManager = VoiceChatManager()
  val aiAssistant = MultilingualAiAssistant(viewModelScope)

  private val _hudState = MutableStateFlow(simulation.snapshotHud(isMuted = false))
  val hudState: StateFlow<HudSnapshot> = _hudState.asStateFlow()

  private val _cloudAccount = MutableStateFlow<UserAccount?>(null)
  val cloudAccount: StateFlow<UserAccount?> = _cloudAccount.asStateFlow()

  private val _permanentPlayerCode =
    MutableStateFlow(
      if (cloudRepository != null && currentUserId.isNotBlank()) {
        generatePermanentPlayerCode(currentUserId)
      } else {
        ""
      }
    )
  val permanentPlayerCode: StateFlow<String> = _permanentPlayerCode.asStateFlow()

  private var activeCloudInvite: SquadInviteDoc? = null
  private var hasDisplayedCloudInvite = false
  private var activeSquadId: String? = null
  private var currentSquadRoom: SquadRoom? = null
  private var incomingInvitesJob: Job? = null
  private var sentInvitesJob: Job? = null
  private var squadRoomJob: Job? = null
  private var squadMembersJob: Job? = null
  private var voiceSignalsJob: Job? = null
  private var lastLaunchedSquadMatchId: String? = null
  private val seenSentStatuses = LinkedHashMap<String, String>()
  private val processedSignalIds = LinkedHashSet<String>()
  private var lastSavedCash: Int = -1
  private var lastSavedKo: Int = -1

  init {
    simulation.onCombatSfx = { event -> soundEngine.playCombatSfx(event) }
    simulation.onEconomyChanged = {
      val snap = simulation.snapshotHud(isMuted = soundEngine.isMuted)
      lastSavedCash = snap.playerCash
      lastSavedKo = snap.npcDefeatedCount
      saveLocalProgress(snap)
    }
    voiceChatManager.onRemoteSquadChanged = { peers ->
      simulation.syncRemoteP2PSquad(peers)
      refreshHudFromGlThread()
    }
    voiceChatManager.onRemotePoseReceived = { pose ->
      simulation.updateRemotePlayerPose(pose)
    }
    voiceChatManager.onRemoteStartMatch = { zoneName ->
      val zone = LobbyDropZone.entries.firstOrNull { it.name == zoneName }
      simulation.startAirplaneDrop(zone)
      refreshHudFromGlThread()
    }
    voiceChatManager.onSquadNotice = { notice ->
      simulation.showBanner(notice)
      refreshHudFromGlThread()
    }
    voiceChatManager.onPeerIdOpened = { peerId ->
      syncLivePeerIdToCloud(peerId)
    }
    voiceChatManager.onSendWebRtcSignal = { recipientUid, type, payload ->
      val sqId = activeSquadId
      val repo = cloudRepository
      if (!sqId.isNullOrBlank() && repo != null && currentUserId.isNotBlank()) {
        viewModelScope.launch {
          repo.sendVoiceSignal(
            squadId = sqId,
            senderUid = currentUserId,
            recipientUid = recipientUid,
            type = type,
            payload = payload,
          )
        }
      }
    }
    soundEngine.start()
    loadLocalProgressIfPresent()
    initializeCloudSession()
  }

  private fun loadLocalProgressIfPresent() {
    val prefs = localPrefs ?: return
    if (!prefs.contains("hero_index") && !prefs.contains("player_cash")) return
    val heroIndex = prefs.getInt("hero_index", 0)
    val equippedName = prefs.getString("equipped_weapon", WeaponType.FISTS.name).orEmpty()
    val equipped = runCatching { WeaponType.valueOf(equippedName) }.getOrDefault(WeaponType.FISTS)
    val ownedRaw = prefs.getStringSet("owned_weapons", setOf(WeaponType.FISTS.name)).orEmpty()
    val owned =
      ownedRaw.mapNotNull { runCatching { WeaponType.valueOf(it) }.getOrNull() }.toSet() +
        WeaponType.FISTS
    val cash = prefs.getInt("player_cash", 1200)
    val koCount = prefs.getInt("ko_count", 0)
    simulation.applyCloudAccountDefaults(
      heroIndex = heroIndex,
      equipped = equipped,
      owned = owned,
      cash = cash,
      koCount = koCount,
    )

    val cloth =
      prefs.getString("equipped_cloth", ClothStyle.PRESET_DEFAULT.name).orEmpty().let { name ->
        runCatching { ClothStyle.valueOf(name) }.getOrDefault(ClothStyle.PRESET_DEFAULT)
      }
    val ownedCloths =
      prefs.getStringSet("owned_cloths", emptySet()).orEmpty()
        .mapNotNull { runCatching { ClothStyle.valueOf(it) }.getOrNull() }
        .toSet()

    val hair =
      prefs.getString("equipped_hair", HairCustomization.PRESET_DEFAULT.name).orEmpty().let { name ->
        runCatching { HairCustomization.valueOf(name) }.getOrDefault(HairCustomization.PRESET_DEFAULT)
      }
    val ownedHairs =
      prefs.getStringSet("owned_hairs", emptySet()).orEmpty()
        .mapNotNull { runCatching { HairCustomization.valueOf(it) }.getOrNull() }
        .toSet()

    val pants =
      prefs.getString("equipped_pants", PantsStyle.PRESET_DEFAULT.name).orEmpty().let { name ->
        runCatching { PantsStyle.valueOf(name) }.getOrDefault(PantsStyle.PRESET_DEFAULT)
      }
    val ownedPants =
      prefs.getStringSet("owned_pants", emptySet()).orEmpty()
        .mapNotNull { runCatching { PantsStyle.valueOf(it) }.getOrNull() }
        .toSet()

    val gunSkin =
      prefs.getString("equipped_gun_skin", GunSkin.STANDARD_STEEL.name).orEmpty().let { name ->
        runCatching { GunSkin.valueOf(name) }.getOrDefault(GunSkin.STANDARD_STEEL)
      }
    val ownedGunSkins =
      prefs.getStringSet("owned_gun_skins", emptySet()).orEmpty()
        .mapNotNull { runCatching { GunSkin.valueOf(it) }.getOrNull() }
        .toSet()

    val carSkin =
      prefs.getString("equipped_car_skin", CarSkin.FACTORY_DEFAULT.name).orEmpty().let { name ->
        runCatching { CarSkin.valueOf(name) }.getOrDefault(CarSkin.FACTORY_DEFAULT)
      }
    val ownedCarSkins =
      prefs.getStringSet("owned_car_skins", emptySet()).orEmpty()
        .mapNotNull { runCatching { CarSkin.valueOf(it) }.getOrNull() }
        .toSet()

    simulation.applyCustomizationState(
      cloth = cloth,
      ownedClothsSet = ownedCloths,
      hair = hair,
      ownedHairsSet = ownedHairs,
      pants = pants,
      ownedPantsSet = ownedPants,
      gunSkin = gunSkin,
      ownedGunSkinsSet = ownedGunSkins,
      carSkin = carSkin,
      ownedCarSkinsSet = ownedCarSkins,
    )
    lastSavedCash = cash.coerceAtLeast(0)
    lastSavedKo = koCount.coerceAtLeast(0)
    _hudState.value = simulation.snapshotHud(isMuted = soundEngine.isMuted)
  }

  private fun saveLocalProgress(snap: HudSnapshot) {
    val prefs = localPrefs ?: return
    prefs.edit()
      .putInt("hero_index", snap.heroPresetIndex)
      .putString("equipped_weapon", snap.equippedWeapon.name)
      .putStringSet("owned_weapons", snap.ownedWeapons.map { it.name }.toSet())
      .putInt("player_cash", snap.playerCash)
      .putInt("ko_count", snap.npcDefeatedCount)
      .putString("equipped_cloth", snap.equippedCloth.name)
      .putStringSet("owned_cloths", snap.ownedCloths.map { it.name }.toSet())
      .putString("equipped_hair", snap.equippedHair.name)
      .putStringSet("owned_hairs", snap.ownedHairs.map { it.name }.toSet())
      .putString("equipped_pants", snap.equippedPants.name)
      .putStringSet("owned_pants", snap.ownedPants.map { it.name }.toSet())
      .putString("equipped_gun_skin", snap.equippedGunSkin.name)
      .putStringSet("owned_gun_skins", snap.ownedGunSkins.map { it.name }.toSet())
      .putString("equipped_car_skin", snap.equippedCarSkin.name)
      .putStringSet("owned_car_skins", snap.ownedCarSkins.map { it.name }.toSet())
      .apply()
  }

  private fun initializeCloudSession() {
    val repo = cloudRepository ?: return
    val user = initialFirebaseUser ?: return
    if (currentUserId.isBlank()) return

    viewModelScope.launch {
      repo.ensureOrLoadAccount(user).onSuccess { loaded ->
        _cloudAccount.value = loaded
        _permanentPlayerCode.value = loaded.playerCode
        simulation.applyCloudAccountDefaults(
          heroIndex = loaded.heroIndex.toInt(),
          equipped = loaded.safeWeapon(),
          owned = loaded.safeOwnedWeapons(),
          cash = loaded.playerCash.toInt(),
          koCount = loaded.koCount.toInt(),
        )
        refreshHudFromGlThread()
        val livePeerId = voiceChatManager.uiState.value.myPeerId
        if (livePeerId.isNotBlank()) {
          syncLivePeerIdToCloud(livePeerId)
        }
      }
    }

    startCloudInviteListeners(repo, currentUserId)
  }

  private fun startCloudInviteListeners(repo: ViceCloudRepository, uid: String) {
    incomingInvitesJob?.cancel()
    incomingInvitesJob =
      viewModelScope.launch {
        repo.observeIncomingInvites(uid)
          .catch { err ->
            if (err is CancellationException) throw err
          }
          .collect { pendingInvites ->
            val latest =
              pendingInvites.maxByOrNull { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
            activeCloudInvite = latest
            if (latest != null) {
              hasDisplayedCloudInvite = true
              voiceChatManager.setCloudIncomingInvite(
                IncomingSquadInvite(
                  peerId = latest.senderPeerId.ifBlank { latest.senderCode },
                  senderName = latest.senderName,
                  heroIndex = latest.senderHeroIndex.toInt().coerceIn(0, 10),
                  weapon = latest.safeWeapon(),
                )
              )
              simulation.showBanner("LOBBY INVITE FROM ${latest.senderName}")
              refreshHudFromGlThread()
            } else if (hasDisplayedCloudInvite) {
              hasDisplayedCloudInvite = false
              voiceChatManager.setCloudIncomingInvite(null)
            }
          }
      }

    sentInvitesJob?.cancel()
    sentInvitesJob =
      viewModelScope.launch {
        repo.observeSentInvites(uid)
          .catch { err ->
            if (err is CancellationException) throw err
          }
          .collect { sentList ->
            for (inv in sentList) {
              val prevStatus = seenSentStatuses.put(inv.id, inv.status)
              if (prevStatus == "PENDING" && inv.status == "DECLINED") {
                voiceChatManager.setStatusDetail("Friend clicked NO (declined)")
                simulation.showBanner("INVITE DECLINED (NO)")
                refreshHudFromGlThread()
              } else if (prevStatus == "PENDING" && inv.status == "ACCEPTED") {
                voiceChatManager.setStatusDetail("Friend clicked YES · Joined Lobby!")
                simulation.showBanner("FRIEND JOINED LOBBY!")
                observeActiveCloudSquad(inv.squadId)
                refreshHudFromGlThread()
              }
            }
          }
      }
  }

  private fun observeActiveCloudSquad(squadId: String) {
    val repo = cloudRepository ?: return
    if (currentUserId.isBlank() || squadId.isBlank()) return
    if (activeSquadId == squadId && squadRoomJob?.isActive == true) return
    activeSquadId = squadId
    lastLaunchedSquadMatchId = null

    squadRoomJob?.cancel()
    squadRoomJob =
      viewModelScope.launch {
        repo.observeSquadRoom(squadId, currentUserId)
          .catch { err ->
            if (err is CancellationException) throw err
          }
          .collect { room ->
            currentSquadRoom = room
            if (room == null) return@collect
            if (room.hostUid != currentUserId &&
              room.status == "STARTED" &&
              lastLaunchedSquadMatchId != squadId
            ) {
              lastLaunchedSquadMatchId = squadId
              simulation.startAirplaneDrop(room.safeDropZone())
              refreshHudFromGlThread()
            } else if (!simulation.isPlaying && room.hostUid != currentUserId) {
              simulation.lobbyDropZone = room.safeDropZone()
              refreshHudFromGlThread()
            }

            // Ensure our own member doc keeps latest invitedUids for visibility
            val acct = _cloudAccount.value
            if (acct != null) {
              val snap = _hudState.value
              repo.syncMySquadMemberState(
                squadId = squadId,
                account = acct,
                visibleUids = room.invitedUids + room.memberUids,
                livePeerId = voiceChatManager.uiState.value.myPeerId,
                heroIndex = snap.heroPresetIndex,
                weapon = snap.equippedWeapon,
              )
            }
            voiceChatManager.syncRoomAudioPeers(
              localUid = currentUserId,
              memberUids = room.memberUids,
            )
          }
      }

    voiceSignalsJob?.cancel()
    processedSignalIds.clear()
    voiceSignalsJob =
      viewModelScope.launch {
        repo.observeIncomingVoiceSignals(squadId, currentUserId)
          .catch { err ->
            if (err is CancellationException) throw err
          }
          .collect { signals ->
            for (sig in signals) {
              if (sig.id.isNotBlank() && processedSignalIds.add(sig.id)) {
                voiceChatManager.receiveWebRtcSignal(sig.senderUid, sig.type, sig.payload)
                launch { repo.deleteVoiceSignal(squadId, sig.id) }
              }
            }
          }
      }

    squadMembersJob?.cancel()
    squadMembersJob =
      viewModelScope.launch {
        repo.observeSquadMembers(squadId, currentUserId)
          .catch { err ->
            if (err is CancellationException) throw err
          }
          .collect { members ->
            val joinedUids = currentSquadRoom?.memberUids?.toSet()
            val uidPeerMap = LinkedHashMap<String, String>()
            val remoteMembers =
              members
                .filter { it.userId != currentUserId && (joinedUids == null || it.userId in joinedUids) }
                .map { doc ->
                  if (doc.userId.isNotBlank() && doc.peerId.isNotBlank()) {
                    uidPeerMap[doc.userId] = doc.peerId
                  }
                  LobbySquadMember(
                    id = doc.peerId.ifBlank { doc.userId },
                    name = doc.displayName,
                    badge = doc.playerCode.ifBlank { "ONLINE" },
                    heroIndex = doc.heroIndex.toInt().coerceIn(0, 10),
                    weapon = doc.safeWeapon(),
                    isReady = doc.ready,
                    isRemoteP2P = true,
                  )
                }
            if (uidPeerMap.isNotEmpty()) {
              voiceChatManager.syncRoomAudioPeers(
                localUid = currentUserId,
                memberUids = currentSquadRoom?.memberUids ?: emptyList(),
                uidPeerMap = uidPeerMap,
              )
            }
            val snap = _hudState.value
            val isHost = currentSquadRoom?.hostUid == currentUserId
            voiceChatManager.syncCloudSquadPeers(
              peers = remoteMembers,
              isHost = isHost,
              localName = snap.heroPresetName,
              heroIndex = snap.heroPresetIndex,
              weapon = snap.equippedWeapon,
            )
          }
      }
  }

  private fun syncLivePeerIdToCloud(livePeerId: String) {
    val repo = cloudRepository ?: return
    val acct = _cloudAccount.value ?: return
    if (currentUserId.isBlank() || livePeerId.isBlank()) return
    viewModelScope.launch {
      val snap = _hudState.value
      repo.saveProgression(
        account = acct,
        heroIndex = snap.heroPresetIndex,
        equippedWeapon = snap.equippedWeapon,
        ownedWeapons = snap.ownedWeapons,
        playerCash = snap.playerCash,
        koCount = snap.npcDefeatedCount,
        livePeerId = livePeerId,
      )
      val sqId = activeSquadId
      val room = currentSquadRoom
      if (sqId != null && room != null) {
        repo.syncMySquadMemberState(
          squadId = sqId,
          account = acct,
          visibleUids = room.invitedUids + room.memberUids,
          livePeerId = livePeerId,
          heroIndex = snap.heroPresetIndex,
          weapon = snap.equippedWeapon,
        )
      }
    }
  }

  fun refreshHudFromGlThread() {
    val snap = simulation.snapshotHud(isMuted = soundEngine.isMuted)
    soundEngine.update(
      playing =
        snap.isPlaying &&
          !snap.isPaused &&
          !snap.isGunShopOpen &&
          !snap.isStyleShopOpen &&
          !snap.isPlayerEliminated,
      driving = snap.isDriving,
      speedKmh = snap.speedKmh,
      drifting = snap.isDrifting,
    )
    soundEngine.updateAmbient(simulation.computeAmbientAudioSnapshot())
    if (snap.isPlaying && !snap.isPaused && !snap.isPlayerEliminated) {
      voiceChatManager.sendInGamePoseIfReady(
        nowMs = System.currentTimeMillis(),
        x = simulation.playerX,
        y = simulation.playerY,
        z = simulation.playerZ,
        yawRad = simulation.playerYawRad,
        speedNorm = simulation.playerSpeedNorm,
        isSprinting = snap.isSprinting,
        isSitting = snap.isSitting,
        dropPhase = snap.dropPhase,
        weapon = snap.equippedWeapon,
        heroIndex = snap.heroPresetIndex,
      )
    }
    if (snap.playerCash != lastSavedCash || snap.npcDefeatedCount != lastSavedKo) {
      lastSavedCash = snap.playerCash
      lastSavedKo = snap.npcDefeatedCount
      saveLocalProgress(snap)
    }
    _hudState.value = snap
  }

  fun startMatchFromLobby() {
    val zone = simulation.lobbyDropZone
    val sqId = activeSquadId
    if (sqId != null && currentUserId.isNotBlank() && currentSquadRoom?.hostUid == currentUserId) {
      lastLaunchedSquadMatchId = sqId
      viewModelScope.launch {
        cloudRepository?.updateSquadRoomState(
          squadId = sqId,
          uid = currentUserId,
          status = "STARTED",
          dropZone = zone,
        )
      }
    }
    voiceChatManager.broadcastStartMatch(zone.name)
    simulation.startAirplaneDrop(zone)
    refreshHudFromGlThread()
  }

  fun invitePeerById(peerOrPlayerId: String) {
    val raw = peerOrPlayerId.trim()
    if (raw.isEmpty()) {
      voiceChatManager.setStatusDetail("Enter friend's Player ID or Peer ID")
      return
    }
    val snap = _hudState.value
    val repo = cloudRepository
    val acct = _cloudAccount.value
    if (repo != null && acct != null && currentUserId.isNotBlank()) {
      voiceChatManager.setStatusDetail("Finding $raw...")
      viewModelScope.launch {
        val lookupResult = repo.lookupTargetByCodeOrPeerId(raw)
        val target = lookupResult.getOrNull()
        if (target == null) {
          // Fallback to direct PeerJS attempt while reporting clear hint
          voiceChatManager.invitePeerToLobby(
            remoteIdInput = raw,
            localName = acct.displayName,
            heroIndex = snap.heroPresetIndex,
            weapon = snap.equippedWeapon,
          )
          voiceChatManager.setStatusDetail("ID '$raw' not found online · Ask friend to log in & share ID")
          simulation.showBanner("PLAYER NOT FOUND · CHECK ID")
          refreshHudFromGlThread()
          return@launch
        }
        if (target.userId == currentUserId) {
          voiceChatManager.setStatusDetail("Cannot invite your own Player ID")
          return@launch
        }
        val sendRes =
          repo.sendCloudSquadInvite(
            account = acct,
            target = target,
            livePeerId = voiceChatManager.uiState.value.myPeerId,
            heroIndex = snap.heroPresetIndex,
            weapon = snap.equippedWeapon,
            dropZone = simulation.lobbyDropZone,
            existingSquadId = activeSquadId?.takeIf { currentSquadRoom?.hostUid == currentUserId },
          )
        sendRes
          .onSuccess { squadId ->
            observeActiveCloudSquad(squadId)
            voiceChatManager.setStatusDetail("Invite sent to ${target.displayName} (${target.playerCode}) · Waiting YES/NO...")
            simulation.showBanner("INVITE SENT TO ${target.displayName}")
            if (target.peerId.isNotBlank()) {
              voiceChatManager.invitePeerToLobby(
                remoteIdInput = target.peerId,
                localName = acct.displayName,
                heroIndex = snap.heroPresetIndex,
                weapon = snap.equippedWeapon,
              )
            }
          }
          .onFailure { err ->
            voiceChatManager.setStatusDetail(err.localizedMessage ?: "Failed to send invite")
          }
        refreshHudFromGlThread()
      }
    } else {
      voiceChatManager.invitePeerToLobby(
        remoteIdInput = raw,
        localName = snap.heroPresetName,
        heroIndex = snap.heroPresetIndex,
        weapon = snap.equippedWeapon,
      )
      refreshHudFromGlThread()
    }
  }

  fun acceptPeerInvite() {
    if (simulation.isPlaying) {
      simulation.returnToLobby()
    }
    val snap = simulation.snapshotHud(isMuted = soundEngine.isMuted)
    val cloudInv = activeCloudInvite
    val repo = cloudRepository
    val acct = _cloudAccount.value
    if (cloudInv != null && repo != null && acct != null) {
      activeCloudInvite = null
      voiceChatManager.setCloudIncomingInvite(null)
      voiceChatManager.setStatusDetail("Joining ${cloudInv.senderName}'s Lobby...")
      viewModelScope.launch {
        repo.respondToCloudInvite(
          invite = cloudInv,
          accept = true,
          account = acct,
          livePeerId = voiceChatManager.uiState.value.myPeerId,
          heroIndex = snap.heroPresetIndex,
          weapon = snap.equippedWeapon,
        )
          .onSuccess { joinedSquadId ->
            if (!joinedSquadId.isNullOrBlank()) {
              observeActiveCloudSquad(joinedSquadId)
              simulation.showBanner("${cloudInv.senderName} LOBBY JOINED · MIC ON TO TALK")
              voiceChatManager.setStatusDetail("Same Lobby with ${cloudInv.senderName} · Turn Mic ON!")
            }
          }
          .onFailure { err ->
            voiceChatManager.setStatusDetail(err.localizedMessage ?: "Could not join squad")
          }
        refreshHudFromGlThread()
      }
    } else {
      voiceChatManager.acceptIncomingInvite(
        localName = snap.heroPresetName,
        heroIndex = snap.heroPresetIndex,
        weapon = snap.equippedWeapon,
      )
      refreshHudFromGlThread()
    }
  }

  fun declinePeerInvite() {
    val cloudInv = activeCloudInvite
    val repo = cloudRepository
    val acct = _cloudAccount.value
    if (cloudInv != null && repo != null && acct != null) {
      activeCloudInvite = null
      voiceChatManager.setCloudIncomingInvite(null)
      voiceChatManager.setStatusDetail("Invite rejected (NO)")
      simulation.showBanner("INVITE DECLINED")
      viewModelScope.launch {
        val snap = _hudState.value
        repo.respondToCloudInvite(
          invite = cloudInv,
          accept = false,
          account = acct,
          livePeerId = voiceChatManager.uiState.value.myPeerId,
          heroIndex = snap.heroPresetIndex,
          weapon = snap.equippedWeapon,
        )
      }
      refreshHudFromGlThread()
    } else {
      voiceChatManager.declineIncomingInvite()
      refreshHudFromGlThread()
    }
  }

  fun removeRemoteSquadMember(peerIdOrUid: String) {
    val sqId = activeSquadId
    if (sqId != null && currentUserId.isNotBlank()) {
      viewModelScope.launch {
        cloudRepository?.leaveSquad(sqId, currentUserId)
      }
      squadRoomJob?.cancel()
      squadMembersJob?.cancel()
      voiceSignalsJob?.cancel()
      processedSignalIds.clear()
      voiceChatManager.syncRoomAudioPeers(currentUserId, emptyList())
      activeSquadId = null
      currentSquadRoom = null
    }
    voiceChatManager.removeRemoteSquadMember(peerIdOrUid)
    refreshHudFromGlThread()
  }

  fun syncLobbyStateToPeers() {
    val snap = _hudState.value
    saveLocalProgress(snap)
    val acct = _cloudAccount.value
    val repo = cloudRepository
    if (acct != null && repo != null && currentUserId.isNotBlank()) {
      viewModelScope.launch {
        repo.saveProgression(
          account = acct,
          heroIndex = snap.heroPresetIndex,
          equippedWeapon = snap.equippedWeapon,
          ownedWeapons = snap.ownedWeapons,
          playerCash = snap.playerCash,
          koCount = snap.npcDefeatedCount,
          livePeerId = voiceChatManager.uiState.value.myPeerId,
        )
        val sqId = activeSquadId
        val room = currentSquadRoom
        if (sqId != null && room != null) {
          repo.syncMySquadMemberState(
            squadId = sqId,
            account = acct,
            visibleUids = room.invitedUids + room.memberUids,
            livePeerId = voiceChatManager.uiState.value.myPeerId,
            heroIndex = snap.heroPresetIndex,
            weapon = snap.equippedWeapon,
          )
          if (room.hostUid == currentUserId) {
            repo.updateSquadRoomState(
              squadId = sqId,
              uid = currentUserId,
              dropZone = simulation.lobbyDropZone,
            )
          }
        }
      }
    }
    voiceChatManager.broadcastLobbySync(
      localName = acct?.displayName ?: snap.heroPresetName,
      heroIndex = snap.heroPresetIndex,
      weapon = snap.equippedWeapon,
      ready = true,
    )
  }

  suspend fun prepareSignOut() {
    val snap = _hudState.value
    val acct = _cloudAccount.value
    val repo = cloudRepository
    if (acct != null && repo != null && currentUserId.isNotBlank()) {
      runCatching {
        repo.saveProgression(
          account = acct,
          heroIndex = snap.heroPresetIndex,
          equippedWeapon = snap.equippedWeapon,
          ownedWeapons = snap.ownedWeapons,
          playerCash = snap.playerCash,
          koCount = snap.npcDefeatedCount,
          livePeerId = "",
        )
      }
    }
    incomingInvitesJob?.cancel()
    sentInvitesJob?.cancel()
    squadRoomJob?.cancel()
    squadMembersJob?.cancel()
    voiceSignalsJob?.cancel()
  }

  fun toggleMute() {
    soundEngine.isMuted = !soundEngine.isMuted
    refreshHudFromGlThread()
  }

  override fun onCleared() {
    super.onCleared()
    saveLocalProgress(simulation.snapshotHud(isMuted = soundEngine.isMuted))
    incomingInvitesJob?.cancel()
    sentInvitesJob?.cancel()
    squadRoomJob?.cancel()
    squadMembersJob?.cancel()
    voiceSignalsJob?.cancel()
    simulation.onCombatSfx = null
    simulation.onEconomyChanged = null
    voiceChatManager.onRemoteSquadChanged = null
    voiceChatManager.onRemotePoseReceived = null
    voiceChatManager.onRemoteStartMatch = null
    voiceChatManager.onSquadNotice = null
    voiceChatManager.onPeerIdOpened = null
    voiceChatManager.onSendWebRtcSignal = null
    soundEngine.stop()
    voiceChatManager.shutdown()
    aiAssistant.shutdown()
  }
}
