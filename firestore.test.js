const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_123";
const CARETAKER_UID = "caretaker_456";
const STRANGER_UID = "stranger_789";
const FARM_ID = "farm_admin_123";
const JOIN_CODE = "FLOCK8";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

async function seedFarmAndInvite() {
  const now = new Date(Date.now() - 5000);
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  await assertSucceeds(
    adminDb.collection("farms").doc(FARM_ID).set({
      ownerId: ADMIN_UID,
      name: "Sindh Sheep Farm",
      joinCode: JOIN_CODE,
      memberUids: [ADMIN_UID],
      adminUids: [ADMIN_UID],
      shepherds: ["Ali", "Sajan"],
      initialFlockCount: 40,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    adminDb.collection("farm_invites").doc(JOIN_CODE).set({
      farmId: FARM_ID,
      farmName: "Sindh Sheep Farm",
      ownerId: ADMIN_UID,
      createdAt: now,
    })
  );
}

test("Unauthenticated users cannot read or write farms or entries", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("farms").get());
  await assertFails(
    unauthDb.collection("farms").doc(FARM_ID).set({
      ownerId: ADMIN_UID,
      name: "Hack",
      joinCode: "HACK12",
      memberUids: [ADMIN_UID],
      adminUids: [ADMIN_UID],
      shepherds: [],
      initialFlockCount: 0,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Admin creates farm, invite, and entries; unfiltered queries fail while member queries succeed", async () => {
  await seedFarmAndInvite();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const now = new Date(Date.now() - 2000);

  // Filtered farm list succeeds; unfiltered fails
  await assertSucceeds(
    adminDb.collection("farms").where("memberUids", "array-contains", ADMIN_UID).get()
  );
  await assertFails(adminDb.collection("farms").get());

  // Admin creates entry without @DocumentId `id` field and without null receiptBase64
  await assertSucceeds(
    adminDb.collection("farms").doc(FARM_ID).collection("entries").doc("entry_1").set({
      farmId: FARM_ID,
      createdByUid: ADMIN_UID,
      createdByName: "Malik",
      memberUids: [ADMIN_UID],
      adminUids: [ADMIN_UID],
      category: "EXPENSE",
      amount: 3500,
      livestockCount: 0,
      shepherdName: "",
      title: "Fodder",
      note: "Green hay",
      entryDateMillis: Date.now() - 1000,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject ghost field / @DocumentId `id` field
  await assertFails(
    adminDb.collection("farms").doc(FARM_ID).collection("entries").doc("entry_bad").set({
      id: "entry_bad",
      farmId: FARM_ID,
      createdByUid: ADMIN_UID,
      createdByName: "Malik",
      memberUids: [ADMIN_UID],
      adminUids: [ADMIN_UID],
      category: "EXPENSE",
      amount: 100,
      livestockCount: 0,
      shepherdName: "",
      title: "Bad",
      note: "",
      entryDateMillis: Date.now() - 1000,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Filtered entries list succeeds; unfiltered fails
  await assertSucceeds(
    adminDb
      .collection("farms")
      .doc(FARM_ID)
      .collection("entries")
      .where("memberUids", "array-contains", ADMIN_UID)
      .get()
  );
  await assertFails(
    adminDb.collection("farms").doc(FARM_ID).collection("entries").get()
  );
});

test("Caretaker can resolve invite, join farm, and add entries, but cannot edit/delete entries or escalate to admin", async () => {
  await seedFarmAndInvite();
  const caretakerDb = testEnv.authenticatedContext(CARETAKER_UID).firestore();
  const now = new Date(Date.now() - 1000);

  // Cannot list invites
  await assertFails(caretakerDb.collection("farm_invites").get());

  // Can get specific invite
  await assertSucceeds(caretakerDb.collection("farm_invites").doc(JOIN_CODE).get());

  // Cannot escalate adminUids
  await assertFails(
    caretakerDb.collection("farms").doc(FARM_ID).update({
      memberUids: [ADMIN_UID, CARETAKER_UID],
      adminUids: [ADMIN_UID, CARETAKER_UID],
      updatedAt: now,
    })
  );

  // Can join memberUids
  await assertSucceeds(
    caretakerDb.collection("farms").doc(FARM_ID).update({
      memberUids: [ADMIN_UID, CARETAKER_UID],
      updatedAt: now,
    })
  );

  // Can create entry with denormalized memberUids/adminUids
  await assertSucceeds(
    caretakerDb.collection("farms").doc(FARM_ID).collection("entries").doc("entry_c1").set({
      farmId: FARM_ID,
      createdByUid: CARETAKER_UID,
      createdByName: "Sajan",
      memberUids: [ADMIN_UID, CARETAKER_UID],
      adminUids: [ADMIN_UID],
      category: "MEDICINE_VET",
      amount: 1200,
      livestockCount: 0,
      shepherdName: "",
      title: "Vet Vaccine",
      note: "20 doses",
      entryDateMillis: Date.now() - 1000,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Caretaker CANNOT edit or delete entry (Admin only)
  await assertFails(
    caretakerDb.collection("farms").doc(FARM_ID).collection("entries").doc("entry_c1").update({
      amount: 9999,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    caretakerDb.collection("farms").doc(FARM_ID).collection("entries").doc("entry_c1").delete()
  );

  // Admin CAN edit entry while preserving createdAt
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  await assertSucceeds(
    adminDb.collection("farms").doc(FARM_ID).collection("entries").doc("entry_c1").update({
      amount: 1400,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Stranger cannot access farm or entries", async () => {
  await seedFarmAndInvite();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const now = new Date(Date.now() - 2000);
  await assertSucceeds(
    adminDb.collection("farms").doc(FARM_ID).collection("entries").doc("entry_s1").set({
      farmId: FARM_ID,
      createdByUid: ADMIN_UID,
      createdByName: "Malik",
      memberUids: [ADMIN_UID],
      adminUids: [ADMIN_UID],
      category: "EXPENSE",
      amount: 500.5,
      livestockCount: 0,
      shepherdName: "",
      title: "Private Feed",
      note: "",
      entryDateMillis: Date.now() - 1000,
      createdAt: now,
      updatedAt: now,
    })
  );

  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
  await assertFails(strangerDb.collection("farms").doc(FARM_ID).get());
  await assertFails(
    strangerDb.collection("farms").doc(FARM_ID).collection("entries").doc("entry_s1").get()
  );
  await assertFails(
    strangerDb.collection("farms").doc(FARM_ID).collection("entries").get()
  );
  await assertFails(
    strangerDb
      .collection("farms")
      .doc(FARM_ID)
      .collection("entries")
      .where("memberUids", "array-contains", ADMIN_UID)
      .get()
  );
});
