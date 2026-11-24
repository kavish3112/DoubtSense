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
    cloudId: "doubt_abc123",
    sessionCloudId: "session_xyz789",
    query: "How does backpressure work in Kotlin Flow?",
    summary: "Backpressure buffers or suspends fast producers when collectors are slower.",
    oneSentenceSummary: "Kotlin Flow handles backpressure via suspension and buffering.",
    category: "Kotlin Flow: Backpressure",
    subject: "Computer Science",
    customTag: "Work, Study",
    timestamp: 1700000000000,
    isResolved: true,
    isStarred: false,
    confidenceScore: 0.92,
    followUpCount: 0,
    quickAnswer: "Backpressure buffers or suspends fast producers.",
    notificationSummary: "Kotlin Flow backpressure uses suspension.",
    conversationalContext: "Discussion on reactive streams",
    importantConcepts: "Flow, Buffer, Suspend",
    doubtType: "Mechanism",
    intent: "Understand reactive backpressure",
    isAmbiguous: false,
    suggestedOptionsJson: "[\"Buffering\", \"Conflation\"]",
    isFactChecked: true,
    verificationStatus: "Verified via 1 Source",
    verificationSourcesJson: "[{\"title\":\"Kotlin Docs\",\"url\":\"https://kotlinlang.org\",\"excerpt\":\"Flow is cold\"}]",
    updatedAtMillis: 1700000000500,
    createdAt: new Date(Date.now() - 2000),
    updatedAt: new Date(Date.now() - 2000),
    ...overrides,
  };
}

function validSessionPayload(overrides = {}) {
  return {
    localId: 10,
    cloudId: "session_xyz789",
    title: "Coroutines Deep Dive",
    answerJson: "[\"How does backpressure work?\"]",
    customTag: "Work, Study",
    category: "Engineering",
    isPinned: false,
    timestamp: 1700000005000,
    startTime: 1700000000000,
    endTime: 1700000005000,
    transcriptPreview: "How does backpressure work?",
    averageDoubtIntensity: 0.72,
    peakDoubtIntensity: 0.92,
    doubtCount: 1,
    summary: "Explained Flow buffering and suspension.",
    updatedAtMillis: 1700000005000,
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
      .doc("doubt_abc123")
      .set(validDoubtPayload())
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("sessions").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sessions")
      .doc("session_xyz789")
      .set(validSessionPayload())
  );
});

test("Owner: can create, update, list, and delete their own doubt sync document", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("doubts")
    .doc("doubt_abc123");

  await assertSucceeds(docRef.set(validDoubtPayload()));
  await assertSucceeds(docRef.get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("doubts").get()
  );
  await assertSucceeds(
    docRef.update({
      isStarred: true,
      customTag: "Work, Research",
      updatedAtMillis: 1700000010000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(docRef.delete());
});

test("Owner: can create, update tags/pin/transcript, list, and delete their own session sync document", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("sessions")
    .doc("session_xyz789");

  await assertSucceeds(docRef.set(validSessionPayload()));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").get()
  );
  await assertSucceeds(
    docRef.update({
      isPinned: true,
      customTag: "Work, Study, Thesis",
      doubtCount: 2,
      averageDoubtIntensity: 0.78,
      updatedAtMillis: 1700000010000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(docRef.delete());
});

test("Cross-user isolation: Bob cannot read, list, update, or delete Alice's doubts or sessions", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("doubts")
      .doc("doubt_abc123")
      .set(validDoubtPayload());
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("sessions")
      .doc("session_xyz789")
      .set(validSessionPayload());
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceDoubt = bobDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("doubts")
    .doc("doubt_abc123");
  const aliceSession = bobDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("sessions")
    .doc("session_xyz789");

  await assertFails(aliceDoubt.get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("doubts").get()
  );
  await assertFails(
    aliceDoubt.update({ isStarred: true, updatedAt: new Date(Date.now() - 1000) })
  );
  await assertFails(aliceDoubt.delete());

  await assertFails(aliceSession.get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("sessions").get()
  );
  await assertFails(
    aliceSession.update({ isPinned: true, updatedAt: new Date(Date.now() - 1000) })
  );
  await assertFails(aliceSession.delete());
});

test("Schema validation: rejects ghost fields, out-of-range intensity/score, invalid IDs, and createdAt tampering", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const doubtCol = aliceDb.collection("users").doc(ALICE_UID).collection("doubts");
  const sessionCol = aliceDb.collection("users").doc(ALICE_UID).collection("sessions");

  // Ghost field on create
  await assertFails(
    doubtCol.doc("doubt_ghost").set(validDoubtPayload({ isAdmin: true }))
  );
  await assertFails(
    sessionCol.doc("session_ghost").set(validSessionPayload({ isAdmin: true }))
  );

  // Invalid bounds
  await assertFails(
    doubtCol.doc("doubt_bad_score").set(validDoubtPayload({ confidenceScore: 2.5 }))
  );
  await assertFails(
    sessionCol.doc("session_bad_intensity").set(validSessionPayload({ averageDoubtIntensity: 1.5 }))
  );

  // Invalid document ID characters & invalid cloudId field
  await assertFails(
    doubtCol.doc("bad id with spaces").set(validDoubtPayload())
  );
  await assertFails(
    sessionCol.doc("session_bad_cloud").set(validSessionPayload({ cloudId: "bad cloud id!" }))
  );

  // Valid create, then attempt to mutate immutable createdAt
  const validSessionRef = sessionCol.doc("session_immutable");
  await assertSucceeds(validSessionRef.set(validSessionPayload({ cloudId: "session_immutable" })));
  await assertFails(
    validSessionRef.update({
      createdAt: new Date(1600000000000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});
