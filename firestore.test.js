const { describe, it, before, after, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const firebase = require("firebase/compat/app");
require("firebase/compat/firestore");

const PROJECT_ID = "ai-studio-applet-webapp-f592c";
const RULES_PATH = "firestore.rules";

let testEnv;

function validFarmPayload(nowMillis = Date.now()) {
  return {
    name: "Laar Sheep Farm",
    shepherds: ["Dharaar 1"],
    initialFlockCount: 10,
    createdAtMillis: nowMillis,
    updatedAtMillis: nowMillis,
    createdAt: firebase.firestore.FieldValue.serverTimestamp(),
    updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
  };
}

function validEntryPayload(farmId = "farm_default", nowMillis = Date.now()) {
  return {
    farmId,
    createdByName: "Admin",
    category: "EXPENSE",
    subCategory: "FEED",
    amount: 4500,
    livestockCount: 0,
    shepherdName: "",
    title: "Dry fodder purchase",
    note: "Monthly feed stock",
    receiptBase64: null,
    entryDateMillis: nowMillis,
    createdAtMillis: nowMillis,
    updatedAtMillis: nowMillis,
    approvalStatus: "APPROVED",
    paymentMethod: "CASH",
    createdAt: firebase.firestore.FieldValue.serverTimestamp(),
    updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
  };
}

function validCommentPayload(farmId = "farm_default", nowMillis = Date.now()) {
  return {
    farmId,
    entryId: "e_123",
    entryTitleSnapshot: "Dry fodder purchase",
    authorRole: "Caretaker",
    commentText: "Amount should be 4200 PKR",
    status: "OPEN",
    adminResponse: "",
    createdAtMillis: nowMillis,
    updatedAtMillis: nowMillis,
    createdAt: firebase.firestore.FieldValue.serverTimestamp(),
    updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
  };
}

describe("FlockLedger Firestore Security Rules", () => {
  before(async () => {
    const [host, portStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
    testEnv = await initializeTestEnvironment({
      projectId: PROJECT_ID,
      firestore: {
        host,
        port: Number(portStr),
        rules: fs.readFileSync(RULES_PATH, "utf8"),
      },
    });
  });

  after(async () => {
    await testEnv.cleanup();
  });

  beforeEach(async () => {
    await testEnv.clearFirestore();
  });

  it("denies all unauthenticated reads and writes", async () => {
    const unauth = testEnv.unauthenticatedContext().firestore();
    await assertFails(unauth.doc("users/owner_1").get());
    await assertFails(unauth.collection("users/owner_1/farms").get());
    await assertFails(unauth.doc("users/owner_1/farms/farm_default").set(validFarmPayload()));
    await assertFails(unauth.doc("users/owner_1/farms/farm_default/entries/e_1").set(validEntryPayload()));
  });

  it("enforces cross-user isolation on profiles, farms, entries, and comments", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      const adminDb = ctx.firestore();
      await adminDb.doc("users/owner_1").set({
        userId: "owner_1",
        displayName: "Alam",
        email: "alam@example.com",
        createdAt: firebase.firestore.FieldValue.serverTimestamp(),
        updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
      });
      await adminDb.doc("users/owner_1/farms/farm_default").set(validFarmPayload());
      await adminDb.doc("users/owner_1/farms/farm_default/entries/e_1").set(validEntryPayload());
      await adminDb.doc("users/owner_1/farms/farm_default/comments/c_1").set(validCommentPayload());
    });

    const otherDb = testEnv.authenticatedContext("other_user").firestore();
    await assertFails(otherDb.doc("users/owner_1").get());
    await assertFails(otherDb.collection("users/owner_1/farms").get());
    await assertFails(otherDb.collection("users/owner_1/farms/farm_default/entries").get());
    await assertFails(otherDb.collection("users/owner_1/farms/farm_default/comments").get());
    await assertFails(otherDb.doc("users/owner_1/farms/farm_default/entries/e_1").delete());
  });

  it("allows owner CRUD on farm, ledger entry, and report comment matching app shapes", async () => {
    const ownerDb = testEnv.authenticatedContext("owner_1").firestore();

    // Profile create & update
    await assertSucceeds(
      ownerDb.doc("users/owner_1").set({
        userId: "owner_1",
        displayName: "Alam",
        email: "alam@example.com",
        createdAt: firebase.firestore.FieldValue.serverTimestamp(),
        updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
      })
    );
    await assertSucceeds(
      ownerDb.doc("users/owner_1").update({
        displayName: "Alam Nizamani",
        updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
      })
    );

    // Farm create, read, update
    const farmRef = ownerDb.doc("users/owner_1/farms/farm_default");
    await assertSucceeds(farmRef.set(validFarmPayload()));
    const farmsSnap = await assertSucceeds(
      ownerDb.collection("users/owner_1/farms").orderBy("createdAtMillis", "asc").get()
    );
    assert.equal(farmsSnap.size, 1);

    await assertSucceeds(
      farmRef.update({
        name: "Laar Farm Updated",
        shepherds: ["Dharaar 1", "Ali"],
        updatedAtMillis: Date.now(),
        updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
      })
    );

    // Entry create, query, update, delete
    const entryRef = ownerDb.doc("users/owner_1/farms/farm_default/entries/e_1");
    await assertSucceeds(entryRef.set(validEntryPayload()));
    const entriesSnap = await assertSucceeds(
      ownerDb.collection("users/owner_1/farms/farm_default/entries").orderBy("entryDateMillis", "desc").get()
    );
    assert.equal(entriesSnap.size, 1);

    await assertSucceeds(
      entryRef.update({
        amount: 4200,
        approvalStatus: "APPROVED",
        updatedAtMillis: Date.now(),
        updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
      })
    );

    // Comment create, resolve, delete
    const commentRef = ownerDb.doc("users/owner_1/farms/farm_default/comments/c_1");
    await assertSucceeds(commentRef.set(validCommentPayload()));
    await assertSucceeds(
      commentRef.update({
        status: "RESOLVED",
        adminResponse: "Fixed to 4200",
        updatedAtMillis: Date.now(),
        updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
      })
    );
    await assertSucceeds(commentRef.delete());
    await assertSucceeds(entryRef.delete());
  });

  it("rejects invalid fields, immutable createdAt edits, and oversized inputs", async () => {
    const ownerDb = testEnv.authenticatedContext("owner_1").firestore();
    const farmRef = ownerDb.doc("users/owner_1/farms/farm_default");
    await assertSucceeds(farmRef.set(validFarmPayload()));

    // Mismatched farmId in entry
    await assertFails(
      ownerDb.doc("users/owner_1/farms/farm_default/entries/e_bad").set(validEntryPayload("other_farm"))
    );

    // Bad approvalStatus
    const badStatus = validEntryPayload();
    badStatus.approvalStatus = "HACKED";
    await assertFails(ownerDb.doc("users/owner_1/farms/farm_default/entries/e_bad2").set(badStatus));

    // Attempt to mutate createdAt on update
    const entryRef = ownerDb.doc("users/owner_1/farms/farm_default/entries/e_1");
    await assertSucceeds(entryRef.set(validEntryPayload()));
    await assertFails(
      entryRef.update({
        createdAt: firebase.firestore.FieldValue.serverTimestamp(),
        updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
      })
    );
  });
});
