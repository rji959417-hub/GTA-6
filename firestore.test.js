const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_1";
const BOB_UID = "bob_uid_2";
const MALLORY_UID = "mallory_uid_3";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function tsPast(msAgo = 1000) {
  return new Date(Date.now() - msAgo);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("invite_codes").doc("VH-100001").get());
  await assertFails(unauthDb.collection("squad_invites").where("recipientUid", "==", ALICE_UID).get());
  await assertFails(unauthDb.collection("squads").doc("sq_1").get());
});

test("UserAccount PII isolation: owner create/update works, cross-user & playerCode mutation blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = tsPast(5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      playerCode: "VH-100001",
      displayName: "VICE HERO",
      email: "alice@example.com",
      authProvider: "GOOGLE",
      heroIndex: 0,
      equippedWeapon: "FISTS",
      ownedWeapons: ["FISTS"],
      playerCash: 1200,
      koCount: 0,
      peerId: "vh-100001",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update of mutable progression works
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      heroIndex: 2,
      equippedWeapon: "ASSAULT_RIFLE",
      ownedWeapons: ["FISTS", "ASSAULT_RIFLE"],
      playerCash: 900,
      peerId: "vh-777888",
      updatedAt: tsPast(1000),
    })
  );

  // Permanent playerCode cannot be altered after creation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      playerCode: "VH-999999",
      updatedAt: tsPast(500),
    })
  );

  // Ghost field injection blocked
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: tsPast(500),
    })
  );

  // Bob cannot read or list Alice's PII user account
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());
});

test("InviteCode exact lookup succeeds without PII; list scraping is blocked", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertSucceeds(
    bobDb.collection("invite_codes").doc("VH-200002").set({
      userId: BOB_UID,
      playerCode: "VH-200002",
      peerId: "vh-200002",
      displayName: "NEON STRIKER",
      heroIndex: 2,
      weapon: "ASSAULT_RIFLE",
      updatedAt: tsPast(1000),
    })
  );

  // Alice can get a single code directly by ID
  await assertSucceeds(aliceDb.collection("invite_codes").doc("VH-200002").get());

  // Blanket list/scraping is strictly denied
  await assertFails(aliceDb.collection("invite_codes").get());

  // Alice cannot overwrite Bob's invite code entry
  await assertFails(
    aliceDb.collection("invite_codes").doc("VH-200002").set({
      userId: ALICE_UID,
      playerCode: "VH-200002",
      peerId: "vh-100001",
      displayName: "HIJACK",
      heroIndex: 0,
      weapon: "FISTS",
      updatedAt: tsPast(500),
    })
  );
});

test("Cross-device YES/NO squad invite & shared 3D lobby join flow enforces zero-trust rules", async () => {
  const created = tsPast(5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      playerCode: "VH-100001",
      displayName: "VICE HERO",
      authProvider: "GOOGLE",
      heroIndex: 0,
      equippedWeapon: "FISTS",
      playerCash: 1200,
      koCount: 0,
      createdAt: created,
      updatedAt: created,
    });
    await adminDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      playerCode: "VH-200002",
      displayName: "NEON STRIKER",
      authProvider: "PHONE",
      heroIndex: 2,
      equippedWeapon: "ASSAULT_RIFLE",
      playerCash: 1200,
      koCount: 0,
      createdAt: created,
      updatedAt: created,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const malloryDb = testEnv.authenticatedContext(MALLORY_UID).firestore();

  const squadId = "sq_alice_1";

  // 1. Alice creates SquadRoom inviting Bob
  await assertSucceeds(
    aliceDb.collection("squads").doc(squadId).set({
      hostUid: ALICE_UID,
      status: "LOBBY",
      dropZone: "FULL_WORLD",
      memberUids: [ALICE_UID],
      invitedUids: [ALICE_UID, BOB_UID],
      createdAt: created,
      updatedAt: created,
    })
  );

  // Alice writes her own squad member card
  await assertSucceeds(
    aliceDb.collection("squads").doc(squadId).collection("members").doc(ALICE_UID).set({
      userId: ALICE_UID,
      visibleUids: [ALICE_UID, BOB_UID],
      displayName: "VICE HERO",
      playerCode: "VH-100001",
      peerId: "vh-100001",
      heroIndex: 0,
      weapon: "FISTS",
      ready: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // 2. Alice sends PENDING invite to Bob
  await assertSucceeds(
    aliceDb.collection("squad_invites").doc("inv_1").set({
      senderUid: ALICE_UID,
      recipientUid: BOB_UID,
      senderName: "VICE HERO",
      senderCode: "VH-100001",
      senderPeerId: "vh-100001",
      senderHeroIndex: 0,
      senderWeapon: "FISTS",
      squadId,
      status: "PENDING",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Filtered query matching rule succeeds for Bob; unfiltered list fails
  await assertSucceeds(
    bobDb.collection("squad_invites").where("recipientUid", "==", BOB_UID).where("status", "==", "PENDING").get()
  );
  await assertFails(bobDb.collection("squad_invites").get());

  // Mallory cannot accept/decline Bob's invite or read Squad
  await assertFails(
    malloryDb.collection("squad_invites").doc("inv_1").update({ status: "ACCEPTED", updatedAt: tsPast(500) })
  );
  await assertFails(malloryDb.collection("squads").doc(squadId).get());

  // 3. Bob clicks YES -> accepts invite, joins squad memberUids, creates member doc
  await assertSucceeds(
    bobDb.collection("squad_invites").doc("inv_1").update({
      status: "ACCEPTED",
      updatedAt: tsPast(400),
    })
  );

  // Terminal state lock: once ACCEPTED, invite cannot be toggled again
  await assertFails(
    bobDb.collection("squad_invites").doc("inv_1").update({
      status: "DECLINED",
      updatedAt: tsPast(200),
    })
  );

  await assertSucceeds(
    bobDb.collection("squads").doc(squadId).update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: tsPast(300),
    })
  );

  await assertSucceeds(
    bobDb.collection("squads").doc(squadId).collection("members").doc(BOB_UID).set({
      userId: BOB_UID,
      visibleUids: [ALICE_UID, BOB_UID],
      displayName: "NEON STRIKER",
      playerCode: "VH-200002",
      peerId: "vh-200002",
      heroIndex: 2,
      weapon: "ASSAULT_RIFLE",
      ready: true,
      createdAt: created,
      updatedAt: tsPast(300),
    })
  );

  // Both Alice and Bob can list squad members with visibleUids filter
  await assertSucceeds(
    aliceDb.collection("squads").doc(squadId).collection("members").where("visibleUids", "array-contains", ALICE_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("squads").doc(squadId).collection("members").where("visibleUids", "array-contains", BOB_UID).get()
  );

  // Unfiltered member list fails
  await assertFails(bobDb.collection("squads").doc(squadId).collection("members").get());

  // Bob cannot hijack hostUid or start match directly (only host Alice starts)
  await assertFails(
    bobDb.collection("squads").doc(squadId).update({
      status: "STARTED",
      updatedAt: tsPast(100),
    })
  );

  await assertSucceeds(
    aliceDb.collection("squads").doc(squadId).update({
      status: "STARTED",
      updatedAt: tsPast(100),
    })
  );

  // 4. WebRTC voice signaling between joined squad members succeeds; outsider blocked
  await assertSucceeds(
    aliceDb.collection("squads").doc(squadId).collection("voice_signals").doc("sig_1").set({
      squadId,
      senderUid: ALICE_UID,
      recipientUid: BOB_UID,
      type: "OFFER",
      payload: "{\"type\":\"offer\",\"sdp\":\"v=0\"}",
      createdAt: tsPast(50),
    })
  );

  await assertSucceeds(
    bobDb.collection("squads").doc(squadId).collection("voice_signals")
      .where("recipientUid", "==", BOB_UID)
      .get()
  );

  await assertFails(
    malloryDb.collection("squads").doc(squadId).collection("voice_signals").doc("sig_bad").set({
      squadId,
      senderUid: MALLORY_UID,
      recipientUid: BOB_UID,
      type: "OFFER",
      payload: "{\"type\":\"offer\",\"sdp\":\"v=0\"}",
      createdAt: tsPast(50),
    })
  );

  await assertFails(
    malloryDb.collection("squads").doc(squadId).collection("voice_signals")
      .where("recipientUid", "==", BOB_UID)
      .get()
  );

  await assertSucceeds(
    bobDb.collection("squads").doc(squadId).collection("voice_signals").doc("sig_1").delete()
  );
});
