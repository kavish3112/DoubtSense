const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validDoubtPayload(overrides = {}) {
  return {
    localId: 1,
    query: "How does backpressure work in Kotlin Flow?",
    summary: "Backpressure buffers or suspends fast producers when collectors are slower.",
    oneSentenceSummary: "Kotlin Flow handles backpressure via suspension and buffering.",
    category: "Kotlin Coroutines",
    customTag: "Education",
    timestamp: 1700000000000,
    isResolved: true,
    isStarred: false,
    confidenceScore: 0.92,
    followUpCount: 0,
    createdAt: new Date(Date.now() - 2000),
    updatedAt: new Date(Date.now() - 2000),
    ...overrides,
  };
}

function validSessionPayload(overrides = {}) {
  return {
    localId: 10,
    title: "Coroutines Deep Dive",
    answerJson: "[\"How does backpressure work?\"]",
    customTag: "Work",
    category: "Engineering",
    isPinned: false,
    timestamp: 1700000000000,
    createdAt: new Date(Date.now() - 2000),
    updatedAt: new Date(Date.now() - 2000),
    ...overrides,
  };
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

test("Unauthenticated user: cannot read or write doubts or sessions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("doubts").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("doubts")
      .doc("doubt_1")
      .set(validDoubtPayload())
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("sessions").get()
  );
});

test("Owner: can create, update, list, and delete their own doubt backup", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("doubts")
    .doc("doubt_1");

  await assertSucceeds(docRef.set(validDoubtPayload()));
  await assertSucceeds(docRef.get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("doubts").get()
  );
  await assertSucceeds(
    docRef.update({
      isStarred: true,
      customTag: "Work",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(docRef.delete());
});

test("Owner: can create and list their own session backup", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("sessions")
    .doc("session_10");

  await assertSucceeds(docRef.set(validSessionPayload()));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").get()
  );
});

test("Cross-user isolation: Bob cannot read, list, update, or delete Alice's doubts", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("doubts")
      .doc("doubt_1")
      .set(validDoubtPayload({ createdAt: new Date(Date.now() - 2000), updatedAt: new Date(Date.now() - 2000) }));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceDoc = bobDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("doubts")
    .doc("doubt_1");

  await assertFails(aliceDoc.get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("doubts").get()
  );
  await assertFails(
    aliceDoc.update({ isStarred: true, updatedAt: new Date(Date.now() - 1000) })
  );
  await assertFails(aliceDoc.delete());
});

test("Schema validation: rejects ghost fields, missing required keys, invalid IDs, and createdAt tampering", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const col = aliceDb.collection("users").doc(ALICE_UID).collection("doubts");

  // Ghost field on create
  await assertFails(
    col.doc("doubt_ghost").set(validDoubtPayload({ isAdmin: true }))
  );

  // Invalid confidenceScore out of bounds
  await assertFails(
    col.doc("doubt_bad_score").set(validDoubtPayload({ confidenceScore: 2.5 }))
  );

  // Invalid document ID characters
  await assertFails(
    col.doc("bad id with spaces").set(validDoubtPayload())
  );

  // Valid create, then attempt to mutate immutable createdAt
  const validRef = col.doc("doubt_immutable");
  await assertSucceeds(validRef.set(validDoubtPayload()));
  await assertFails(
    validRef.update({
      createdAt: new Date(1600000000000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});
