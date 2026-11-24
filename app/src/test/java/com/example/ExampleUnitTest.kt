package com.example

import com.example.data.model.DoubtItem
import com.example.service.ListeningStateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun doubtItem_initialization_isCorrect() {
    val item = DoubtItem(
      id = 101L,
      doubtSnippet = "Why does warm air rise?",
      subject = "Physics",
      topic = "Physics: Thermodynamics",
      importantConcepts = "Convection, Density Gradients, Buoyant Force",
      doubtType = "Working Principle",
      intent = "Understand why warm air moves upward relative to cold air",
      quickAnswer = "Warm air is less dense than cooler air, causing it to float upwards.",
      detailedAnswer = "Detailed explanation of convective currents and density gradients.",
      notificationSummary = "Physics: Warm air is less dense and rises.",
      isAmbiguous = false
    )
    assertNotNull(item)
    assertEquals(101L, item.id)
    assertEquals("Physics", item.subject)
    assertEquals("Physics: Thermodynamics", item.topic)
    assertEquals("Working Principle", item.doubtType)
    assertTrue(item.quickAnswer.isNotEmpty())
    assertTrue(item.notificationSummary.startsWith("Physics:"))
  }

  @Test
  fun contextUnderstanding_randomForest_isAccurate() {
    val item = DoubtItem(
      id = 103L,
      doubtSnippet = "But how do they actually work together?",
      subject = "Machine Learning",
      topic = "Random Forest",
      importantConcepts = "Multiple Decision Trees, Ensemble, Bagging, Majority Voting",
      doubtType = "Working principle",
      intent = "Understand how multiple trees contribute to the final prediction",
      quickAnswer = "In Random Forest, multiple trees vote on the output, and the majority vote or average determines the final prediction.",
      detailedAnswer = "Random Forest builds hundreds of de-correlated decision trees through bootstrap aggregating. For classification, each tree casts a unit vote.",
      notificationSummary = "Random Forest: Trees vote and majority decides.",
      isAmbiguous = true,
      clarificationQuestion = "Do you want to understand the prediction voting mechanism, bootstrap sampling, or random feature splits?",
      suggestedOptionsJson = "[\"Voting Mechanism\", \"Bagging / Sampling\", \"Feature Randomness\"]"
    )
    assertEquals("Machine Learning", item.subject)
    assertEquals("Random Forest", item.topic)
    assertEquals("Working principle", item.doubtType)
    assertTrue(item.importantConcepts.contains("Multiple Decision Trees"))
    assertTrue(item.suggestedOptionsJson.contains("Voting Mechanism"))
  }

  @Test
  fun lowPowerMode_toggle_isReflected() {
    ListeningStateManager.setLowPowerMode(true)
    assertTrue(ListeningStateManager.lowPowerMode.value)

    ListeningStateManager.setLowPowerMode(false)
    assertFalse(ListeningStateManager.lowPowerMode.value)
  }

  @Test
  fun clarificationMode_toggle_isReflected() {
    ListeningStateManager.setClarificationMode("CONVERSATION")
    assertEquals("CONVERSATION", ListeningStateManager.clarificationMode.value)

    ListeningStateManager.setClarificationMode("PRIVATE")
    assertEquals("PRIVATE", ListeningStateManager.clarificationMode.value)
  }

  @Test
  fun historySearch_filtering_isAccurate() {
    val doubts = listOf(
      DoubtItem(
        id = 1L,
        doubtSnippet = "How does photosynthesis convert photons to glucose?",
        subject = "Cellular Biology",
        topic = "Biology: Photosynthesis",
        quickAnswer = "Chloroplasts capture photons in chlorophyll to synthesize sugars.",
        detailedAnswer = "Light-dependent and Calvin cycle reactions synthesize carbohydrates.",
        notificationSummary = "Biology: Chlorophyll uses light energy to make glucose."
      ),
      DoubtItem(
        id = 2L,
        doubtSnippet = "What is Big-O complexity of quicksort?",
        subject = "Computer Science",
        topic = "Algorithms: Sorting",
        quickAnswer = "Average time is O(n log n), worst case is O(n^2).",
        detailedAnswer = "Pivot selection determines partition balance.",
        notificationSummary = "Algorithms: Quicksort averages O(n log n)."
      )
    )

    val bioMatches = doubts.filter { it.topic.contains("Biology", ignoreCase = true) }
    assertEquals(1, bioMatches.size)
    assertEquals("Biology: Photosynthesis", bioMatches.first().topic)
  }

  @Test
  fun contextAnalysisResult_mapping_isAccurate() {
    val result = com.example.ai.ContextAnalysisResult(
      isDoubt = true,
      subject = "Machine Learning",
      topic = "Random Forest",
      intent = "Understand majority voting across trees",
      importantConcepts = listOf("Decision Trees", "Ensemble", "Bagging"),
      doubtType = "Working principle",
      doubtSnippet = "How do they work together?",
      quickAnswer = "Individual trees cast unit votes.",
      detailedAnswer = "Each decision tree in the ensemble predicts independently, and bagging averages the outputs.",
      notificationSummary = "Random Forest: Trees vote together.",
      isAmbiguous = false,
      clarificationQuestion = null,
      suggestedOptions = listOf("Voting", "Bagging"),
      referenceMaterials = "Breiman (2001)",
      conversationSuggestions = "How are ties handled?",
      confidence = 0.95f
    )

    val item = result.toDoubtItem("Person A: Random forest | Person B: How do they work?")
    assertEquals("Machine Learning", item.subject)
    assertEquals("Random Forest", item.topic)
    assertEquals("Understand majority voting across trees", item.intent)
    assertEquals("Working principle", item.doubtType)
    assertTrue(item.importantConcepts.contains("Decision Trees"))
    assertEquals(0.95f, item.confidenceScore)
  }

  @Test
  fun documentExport_buildStructuredText_isWellFormed() {
    val turns = listOf(
      "Person A: Random Forest uses multiple decision trees.",
      "Person B: But how do they actually work together?"
    )
    val doubts = listOf(
      DoubtItem(
        id = 201L,
        doubtSnippet = "But how do they actually work together?",
        subject = "Machine Learning",
        topic = "Random Forest",
        importantConcepts = "Decision Trees, Ensemble",
        doubtType = "Working principle",
        intent = "Understand how multiple trees contribute to the final prediction",
        quickAnswer = "Multiple trees vote on the output, and the majority vote decides.",
        detailedAnswer = "Each tree predicts independently, reducing variance.",
        notificationSummary = "Random Forest: Trees vote together."
      )
    )

    val doc = com.example.util.DocumentExportHelper.buildStructuredText(
      conversationTurns = turns,
      doubts = doubts,
      documentTitle = "Machine Learning Study Session"
    )

    assertTrue(doc.contains("DOUBTSENSE"))
    assertTrue(doc.contains("Machine Learning Study Session"))
    assertTrue(doc.contains("PART 1: FULL CONVERSATION TRANSCRIPT"))
    assertTrue(doc.contains("Person A: Random Forest uses multiple decision trees."))
    assertTrue(doc.contains("Person B: But how do they actually work together?"))
    assertTrue(doc.contains("PART 2: DETECTED DOUBTS & AI-GENERATED ANSWERS"))
    assertTrue(doc.contains("Random Forest"))
    assertTrue(doc.contains("Multiple trees vote on the output"))
    assertTrue(doc.contains("Each tree predicts independently"))
    assertTrue(doc.contains("Key Concepts: Decision Trees, Ensemble"))
  }

  @Test
  fun uncertaintyIntensity_updatesAndClamping_areAccurate() {
    ListeningStateManager.updateUncertainty(0.85f)
    assertEquals(0.85f, ListeningStateManager.uncertaintyIntensity.value, 0.01f)
    assertEquals("High Uncertainty", ListeningStateManager.uncertaintyLabel.value)

    ListeningStateManager.updateUncertainty(0.55f)
    assertEquals(0.55f, ListeningStateManager.uncertaintyIntensity.value, 0.01f)
    assertEquals("Moderate Doubt", ListeningStateManager.uncertaintyLabel.value)

    ListeningStateManager.updateUncertainty(0.25f)
    assertEquals(0.25f, ListeningStateManager.uncertaintyIntensity.value, 0.01f)
    assertEquals("Inquisitive Speech", ListeningStateManager.uncertaintyLabel.value)

    ListeningStateManager.updateUncertainty(0.04f)
    assertEquals(0.04f, ListeningStateManager.uncertaintyIntensity.value, 0.01f)
    assertEquals("Calm / Neutral", ListeningStateManager.uncertaintyLabel.value)
  }

  @Test
  fun realtimeUncertainty_speechHesitation_increasesGaugeValue() {
    ListeningStateManager.updateLiveTranscript("I don't understand how this works and I am confused")
    assertTrue(ListeningStateManager.uncertaintyIntensity.value >= 0.6f)
    assertTrue(ListeningStateManager.uncertaintyLabel.value.contains("Doubt") || ListeningStateManager.uncertaintyLabel.value.contains("Uncertainty"))
  }

  @Test
  fun sessionTrendPoints_derivation_isAccurate() {
    val sampleDoubts = listOf(
      DoubtItem(
        id = 1L,
        timestamp = 1000000L,
        doubtSnippet = "How does this work?",
        topic = "Machine Learning",
        quickAnswer = "A",
        detailedAnswer = "B",
        notificationSummary = "C",
        confidenceScore = 0.85f
      ),
      DoubtItem(
        id = 2L,
        timestamp = 2000000L,
        doubtSnippet = "Why does that happen?",
        topic = "Machine Learning",
        quickAnswer = "D",
        detailedAnswer = "E",
        notificationSummary = "F",
        confidenceScore = 0.75f
      ),
      DoubtItem(
        id = 3L,
        timestamp = 3000000L,
        doubtSnippet = "Can you clarify?",
        topic = "Biology",
        quickAnswer = "G",
        detailedAnswer = "H",
        notificationSummary = "I",
        confidenceScore = 0.60f
      )
    )

    val points = com.example.ui.components.deriveSessionTrendPoints(sampleDoubts)
    assertTrue(points.isNotEmpty())
    assertTrue(points.all { it.intensity in 0.0f..1.0f })
    assertTrue(points.all { it.sessionLabel.startsWith("Session") || it.sessionId.startsWith("S") })
  }

  @Test
  fun conversationSession_transcriptSerialization_roundTripIsAccurate() {
    val originalTurns = listOf(
      "Speaker A: Let's discuss \"Random Forest\" bagging.",
      "Speaker B: Wait,\nhow do trees vote together?"
    )
    val encoded = com.example.data.model.ConversationSession.encodeTranscriptTurns(originalTurns)
    val session = com.example.data.model.ConversationSession(
      title = "Random Forest",
      category = "Machine Learning",
      transcriptJson = encoded
    )
    val decoded = session.getTranscriptTurns()
    assertEquals(2, decoded.size)
    assertEquals("Speaker A: Let's discuss \"Random Forest\" bagging.", decoded[0])
    assertEquals("Speaker B: Wait,\nhow do trees vote together?", decoded[1])
  }

  @Test
  fun computeCategorySummaries_calculatesAverageDoubtIntensityPerCategory() {
    val sessions = listOf(
      com.example.data.model.ConversationSession(
        id = 1L,
        title = "Tree Ensembles",
        category = "Machine Learning",
        averageDoubtIntensity = 0.80f,
        peakDoubtIntensity = 0.90f,
        doubtCount = 2
      ),
      com.example.data.model.ConversationSession(
        id = 2L,
        title = "Gradient Boosting",
        category = "Machine Learning",
        averageDoubtIntensity = 0.60f,
        peakDoubtIntensity = 0.70f,
        doubtCount = 1
      ),
      com.example.data.model.ConversationSession(
        id = 3L,
        title = "Mitochondria ATP",
        category = "Biology",
        averageDoubtIntensity = 0.40f,
        peakDoubtIntensity = 0.50f,
        doubtCount = 1
      )
    )
    val doubts = listOf(
      DoubtItem(id = 10L, sessionId = 1L, subject = "Machine Learning", topic = "Bagging", doubtSnippet = "Q1", quickAnswer = "A1", detailedAnswer = "D1", notificationSummary = "N1"),
      DoubtItem(id = 11L, sessionId = 1L, subject = "Machine Learning", topic = "Voting", doubtSnippet = "Q2", quickAnswer = "A2", detailedAnswer = "D2", notificationSummary = "N2"),
      DoubtItem(id = 12L, sessionId = 3L, subject = "Biology", topic = "ATP", doubtSnippet = "Q3", quickAnswer = "A3", detailedAnswer = "D3", notificationSummary = "N3")
    )

    val summaries = com.example.data.repository.DoubtRepository.computeCategorySummaries(sessions, doubts)
    assertEquals(2, summaries.size)
    val ml = summaries.first { it.categoryName == "Machine Learning" }
    val bio = summaries.first { it.categoryName == "Biology" }
    assertEquals(0.70f, ml.averageIntensity, 0.01f)
    assertEquals(2, ml.sessionCount)
    assertEquals(2, ml.totalDoubts)
    assertEquals(0.40f, bio.averageIntensity, 0.01f)
    assertEquals(1, bio.sessionCount)
  }

  @Test
  fun sessionSearch_byKeywordAndDate_filtersCorrectly() {
    val now = 1_760_000_000_000L
    val yesterday = now - 86_400_000L
    val s1 = com.example.data.model.ConversationSession(
      id = 1L,
      startTime = now,
      endTime = now,
      title = "ATP Synthase Review",
      category = "Biology",
      transcriptPreview = "Proton gradient powers F0F1 rotor"
    )
    val s2 = com.example.data.model.ConversationSession(
      id = 2L,
      startTime = yesterday,
      endTime = yesterday,
      title = "Quicksort Pivot Analysis",
      category = "Computer Science",
      transcriptPreview = "Unbalanced splits lead to O(n^2)"
    )

    assertTrue(com.example.ui.screens.matchesSessionQuery(s1, "Today", now))
    assertFalse(com.example.ui.screens.matchesSessionQuery(s2, "Today", now))
    assertTrue(com.example.ui.screens.matchesSessionQuery(s2, "Yesterday", now))
    assertTrue(com.example.ui.screens.matchesSessionQuery(s2, "Quicksort", now))
    assertTrue(com.example.ui.screens.matchesDateFilter(s1.startTime, com.example.ui.screens.DateFilterOption.TODAY, now))
    assertFalse(com.example.ui.screens.matchesDateFilter(s2.startTime, com.example.ui.screens.DateFilterOption.TODAY, now))
  }

  @Test
  fun externalReferenceHelper_buildsContextualGoogleAndChatGptQueries() {
    val doubt = DoubtItem(
      id = 55L,
      subject = "Biology",
      topic = "Cellular Respiration: ATP Synthase",
      doubtSnippet = "How does proton gradient synthesize ATP?",
      conversationalContext = "Oxidative phosphorylation in mitochondria",
      quickAnswer = "Proton flow spins ATP synthase rotor.",
      detailedAnswer = "Detailed explanation",
      notificationSummary = "ATP Synthase"
    )
    val googleQuery = com.example.util.ExternalReferenceHelper.buildSearchQuery(doubt)
    assertTrue(googleQuery.contains("ATP Synthase"))
    assertTrue(googleQuery.contains("proton gradient"))

    val chatGptPrompt = com.example.util.ExternalReferenceHelper.buildChatGptPrompt(doubt)
    assertTrue(chatGptPrompt.contains("Subject: Biology"))
    assertTrue(chatGptPrompt.contains("Conversation Context: Oxidative phosphorylation"))
    assertTrue(chatGptPrompt.contains("How does proton gradient synthesize ATP?"))
  }

  @Test
  fun autoReadAloud_toggle_updatesState() {
    ListeningStateManager.setAutoReadAloud(false)
    assertFalse(ListeningStateManager.autoReadAloud.value)
    ListeningStateManager.setAutoReadAloud(true)
    assertTrue(ListeningStateManager.autoReadAloud.value)
  }

  @Test
  fun factCheckingMode_toggleAndVerificationSourceSerialization_areAccurate() {
    ListeningStateManager.setFactCheckingMode(true)
    assertTrue(ListeningStateManager.factCheckingMode.value)
    ListeningStateManager.setFactCheckingMode(false)
    assertFalse(ListeningStateManager.factCheckingMode.value)

    val sources = listOf(
      com.example.data.model.VerificationSource(
        title = "Scikit-learn Official Docs: Random Forest",
        url = "https://scikit-learn.org/stable/modules/ensemble.html#forest",
        excerpt = "Ensemble trees reduce variance via bagging."
      ),
      com.example.data.model.VerificationSource(
        title = "Breiman (2001)",
        url = "https://link.springer.com/article/10.1023/A:1010933404324",
        excerpt = "Independent trees vote by majority."
      )
    )
    val encoded = DoubtItem.encodeVerificationSources(sources)
    val doubt = DoubtItem(
      doubtSnippet = "How do decision trees work together?",
      topic = "Random Forest",
      quickAnswer = "Each tree votes and majority wins.",
      detailedAnswer = "Detailed ensemble explanation.",
      notificationSummary = "Random Forest",
      isFactChecked = true,
      verificationStatus = "Verified via 2 External Source(s)",
      verificationSourcesJson = encoded
    )

    val decoded = doubt.getVerificationSources()
    assertEquals(2, decoded.size)
    assertEquals("Scikit-learn Official Docs: Random Forest", decoded[0].title)
    assertTrue(decoded[0].url.startsWith("https://"))
    assertEquals("Ensemble trees reduce variance via bagging.", decoded[0].excerpt)

    val exported = com.example.util.DocumentExportHelper.buildStructuredText(
      conversationTurns = emptyList(),
      doubts = listOf(doubt),
      documentTitle = "Fact-Checked Export"
    )
    assertTrue(exported.contains("FACT-CHECKING VERIFICATION"))
    assertTrue(exported.contains("https://scikit-learn.org/stable/modules/ensemble.html#forest"))
  }

  @Test
  fun defaultVerificationSourcesForTopic_returnsAuthoritativeUrls() {
    val sources = com.example.ai.ContextAnalysisService.defaultVerificationSourcesForTopic(
      subject = "Biology",
      topic = "Cellular Respiration: ATP Synthase",
      queryText = "How does proton gradient synthesize ATP?"
    )
    assertTrue(sources.isNotEmpty())
    assertTrue(sources.all { it.url.startsWith("https://") && it.title.isNotBlank() })
  }

  @Test
  fun dailyInsightCalculator_selectsMostSignificantTodayDoubtAndTracksProgressDelta() {
    val now = 1_760_020_000_000L
    val yesterday = now - 86_400_000L

    val earlierSession = com.example.data.model.ConversationSession(
      id = 1L,
      startTime = yesterday,
      endTime = yesterday + 300_000L,
      title = "Ensemble Bagging",
      category = "Machine Learning",
      averageDoubtIntensity = 0.85f,
      peakDoubtIntensity = 0.90f,
      doubtCount = 1
    )
    val todaySession = com.example.data.model.ConversationSession(
      id = 2L,
      startTime = now - 3_600_000L,
      endTime = now - 1_800_000L,
      title = "Thermodynamics Review",
      category = "Physics",
      averageDoubtIntensity = 0.45f,
      peakDoubtIntensity = 0.72f,
      doubtCount = 2
    )

    val minorTodayDoubt = DoubtItem(
      id = 10L,
      sessionId = 2L,
      timestamp = now - 3_000_000L,
      doubtSnippet = "What is Kelvin?",
      subject = "Physics",
      topic = "Temperature Scales",
      quickAnswer = "Absolute temperature scale.",
      detailedAnswer = "Kelvin starts at absolute zero.",
      notificationSummary = "Kelvin is an absolute temperature scale.",
      confidenceScore = 0.35f
    )
    val majorTodayDoubt = DoubtItem(
      id = 11L,
      sessionId = 2L,
      timestamp = now - 1_800_000L,
      doubtSnippet = "Why does entropy always increase in an isolated thermodynamic system?",
      subject = "Physics",
      topic = "Second Law of Thermodynamics",
      quickAnswer = "Microstates naturally evolve toward higher probability equilibrium.",
      detailedAnswer = "Statistical mechanics shows spontaneous processes maximize accessible microstates.",
      notificationSummary = "Entropy increases because equilibrium macrostates have vastly more microstates.",
      confidenceScore = 0.82f,
      isAmbiguous = true
    )

    val insight = com.example.data.repository.DailyInsightCalculator.computeDailyInsight(
      doubts = listOf(minorTodayDoubt, majorTodayDoubt),
      sessions = listOf(earlierSession, todaySession),
      nowMillis = now
    )

    assertNotNull(insight)
    assertTrue(insight!!.isFromToday)
    assertEquals(11L, insight.mostSignificantDoubt?.id)
    assertEquals("Second Law of Thermodynamics", insight.headline)
    assertEquals(2, insight.todayDoubtCount)
    assertEquals(1, insight.todaySessionCount)
    assertEquals(0.45f, insight.todayAverageIntensity, 0.01f)
    assertEquals(0.85f, insight.previousAverageIntensity!!, 0.01f)
    assertEquals(-0.40f, insight.intensityDelta!!, 0.01f)
    assertTrue(insight.progressNarrative.contains("40%"))
  }

  @Test
  fun dailyInsightCalculator_returnsNullWhenEmptyAndFallsBackWhenOnlyPriorDaysExist() {
    val emptyInsight = com.example.data.repository.DailyInsightCalculator.computeDailyInsight(
      doubts = emptyList(),
      sessions = emptyList()
    )
    assertNull(emptyInsight)

    val now = 1_760_020_000_000L
    val twoDaysAgo = now - (2 * 86_400_000L)
    val priorDoubt = DoubtItem(
      id = 21L,
      timestamp = twoDaysAgo,
      doubtSnippet = "How does ATP synthase spin?",
      subject = "Biology",
      topic = "ATP Synthase",
      quickAnswer = "Proton flow drives the F0 rotor.",
      detailedAnswer = "Chemiosmotic proton gradient powers ATP phosphorylation.",
      notificationSummary = "Proton flow powers ATP synthesis.",
      confidenceScore = 0.76f
    )
    val fallbackInsight = com.example.data.repository.DailyInsightCalculator.computeDailyInsight(
      doubts = listOf(priorDoubt),
      sessions = emptyList(),
      nowMillis = now
    )
    assertNotNull(fallbackInsight)
    assertFalse(fallbackInsight!!.isFromToday)
    assertEquals("ATP Synthase", fallbackInsight.headline)
    assertTrue(fallbackInsight.progressNarrative.contains("No doubts analyzed today yet"))
  }

  @Test
  fun doubtFrequencyCalculator_bucketsLast30DaysComputesMetricsAndFiltersBySubject() {
    val now = 1_760_020_000_000L
    val day = 86_400_000L

    val doubts = listOf(
      DoubtItem(
        id = 1L,
        timestamp = now - 1_000L, // Today (day 29)
        doubtSnippet = "Why does entropy increase?",
        subject = "Physics",
        topic = "Thermodynamics",
        quickAnswer = "More microstates.",
        detailedAnswer = "Statistical equilibrium.",
        notificationSummary = "Entropy"
      ),
      DoubtItem(
        id = 2L,
        timestamp = now - 2_000L, // Today (day 29)
        doubtSnippet = "How does Carnot cycle reach max efficiency?",
        subject = "Physics",
        topic = "Carnot Engine",
        quickAnswer = "Reversible isothermal and adiabatic steps.",
        detailedAnswer = "Zero entropy generation.",
        notificationSummary = "Carnot"
      ),
      DoubtItem(
        id = 3L,
        timestamp = now - (3 * day), // 3 days ago (day 26)
        doubtSnippet = "Why does quicksort degrade?",
        subject = "Computer Science",
        topic = "Quicksort",
        quickAnswer = "Skewed pivots.",
        detailedAnswer = "O(n^2) worst case.",
        notificationSummary = "Quicksort"
      ),
      DoubtItem(
        id = 4L,
        timestamp = now - (10 * day), // 10 days ago (prior 7d window, day 19)
        doubtSnippet = "How does bagging reduce variance?",
        subject = "Machine Learning",
        topic = "Random Forest",
        quickAnswer = "Bootstrapped ensemble averaging.",
        detailedAnswer = "Independent trees average out variance.",
        notificationSummary = "Bagging"
      ),
      DoubtItem(
        id = 5L,
        timestamp = now - (45 * day), // Outside 30-day window (should be excluded)
        doubtSnippet = "Old question outside window",
        subject = "Biology",
        topic = "Osmosis",
        quickAnswer = "Water movement.",
        detailedAnswer = "Semipermeable membrane.",
        notificationSummary = "Osmosis"
      )
    )

    val summaryAll = com.example.data.repository.DoubtFrequencyCalculator.compute30DayFrequency(
      doubts = doubts,
      sessions = emptyList(),
      nowMillis = now
    )

    assertEquals(30, summaryAll.points.size)
    assertEquals(4, summaryAll.totalDoubts30d)
    assertEquals(3, summaryAll.activeDaysCount)
    assertEquals(2, summaryAll.peakDay?.doubtCount)
    assertEquals(true, summaryAll.peakDay?.isToday)
    assertEquals(3, summaryAll.recentSevenDayTotal)
    assertEquals(1, summaryAll.priorSevenDayTotal)
    assertEquals(2, summaryAll.weekOverWeekDelta)
    assertTrue(summaryAll.points.last().rollingSevenDayAverage > 0.4f)
    assertTrue(summaryAll.points.last().fullDateLabel.contains(","))
    assertTrue(summaryAll.points.last().averageIntensity > 0f)

    val physicsOnly = com.example.data.repository.DoubtFrequencyCalculator.compute30DayFrequency(
      doubts = doubts,
      sessions = emptyList(),
      subjectFilter = "Physics",
      nowMillis = now
    )
    assertEquals(2, physicsOnly.totalDoubts30d)
    assertEquals(1, physicsOnly.activeDaysCount)
  }

  @Test
  fun exportReminderManager_shouldSendExportReminder_evaluatesStalenessAndCooldown() {
    val now = 1_760_500_000_000L
    val day = 86_400_000L

    // Disabled -> never remind
    assertFalse(
      com.example.util.ExportReminderManager.shouldSendExportReminder(
        remindersEnabled = false,
        intervalDays = 3,
        lastExportTimestamp = 0L,
        lastReminderSentTimestamp = 0L,
        latestActivityTimestamp = now - (5 * day),
        totalSessionsCount = 4,
        totalDoubtsCount = 4,
        nowMillis = now
      )
    )

    // No history -> do not remind
    assertFalse(
      com.example.util.ExportReminderManager.shouldSendExportReminder(
        remindersEnabled = true,
        intervalDays = 3,
        lastExportTimestamp = 0L,
        lastReminderSentTimestamp = 0L,
        latestActivityTimestamp = 0L,
        totalSessionsCount = 0,
        totalDoubtsCount = 0,
        nowMillis = now
      )
    )

    // Exported recently (1 day ago with 3-day threshold) -> do not remind
    assertFalse(
      com.example.util.ExportReminderManager.shouldSendExportReminder(
        remindersEnabled = true,
        intervalDays = 3,
        lastExportTimestamp = now - day,
        lastReminderSentTimestamp = 0L,
        latestActivityTimestamp = now,
        totalSessionsCount = 5,
        totalDoubtsCount = 6,
        nowMillis = now
      )
    )

    // Exported 4 days ago (exceeds 3-day threshold) -> remind!
    assertTrue(
      com.example.util.ExportReminderManager.shouldSendExportReminder(
        remindersEnabled = true,
        intervalDays = 3,
        lastExportTimestamp = now - (4 * day),
        lastReminderSentTimestamp = 0L,
        latestActivityTimestamp = now - day,
        totalSessionsCount = 5,
        totalDoubtsCount = 6,
        nowMillis = now
      )
    )

    // Within cooldown after sending a reminder -> suppress duplicate
    assertFalse(
      com.example.util.ExportReminderManager.shouldSendExportReminder(
        remindersEnabled = true,
        intervalDays = 3,
        lastExportTimestamp = now - (4 * day),
        lastReminderSentTimestamp = now - day,
        latestActivityTimestamp = now,
        totalSessionsCount = 5,
        totalDoubtsCount = 6,
        nowMillis = now
      )
    )

    // Never exported & 3+ sessions accumulated -> remind
    assertTrue(
      com.example.util.ExportReminderManager.shouldSendExportReminder(
        remindersEnabled = true,
        intervalDays = 3,
        lastExportTimestamp = 0L,
        lastReminderSentTimestamp = 0L,
        latestActivityTimestamp = now,
        totalSessionsCount = 3,
        totalDoubtsCount = 3,
        nowMillis = now
      )
    )
  }

  @Test
  fun offlineKnowledgeBase_providesAccurateStemAnswersAndFiltersCasualChitChat() {
    assertFalse(com.example.ai.OfflineKnowledgeBase.isLikelyDoubtOrQuestion("Hey guys, let's grab coffee at 3pm."))
    assertTrue(com.example.ai.OfflineKnowledgeBase.isLikelyDoubtOrQuestion("Explain Bayes theorem with an example"))

    val bayesResult = com.example.ai.ContextAnalysisService().fallbackContextAnalysis(
      segment = "How does Bayes theorem update probabilities?",
      surroundingContext = emptyList(),
      factCheckingMode = true
    )
    assertTrue(bayesResult.isDoubt)
    assertEquals("Mathematics", bayesResult.subject)
    assertTrue(bayesResult.quickAnswer.contains("P(A|B)"))
    assertTrue(bayesResult.detailedAnswer.contains("Prior"))
    assertTrue(bayesResult.isFactChecked)

    val tcpResult = com.example.ai.ContextAnalysisService().fallbackContextAnalysis(
      segment = "What is the difference between TCP and UDP?",
      surroundingContext = emptyList(),
      factCheckingMode = false
    )
    assertTrue(tcpResult.isDoubt)
    assertEquals("Computer Science", tcpResult.subject)
    assertTrue(tcpResult.quickAnswer.contains("3-way handshake", ignoreCase = true))

    // Explicit query flag guarantees an answer even for terse phrases without question marks
    val explicitResult = com.example.ai.ContextAnalysisService().fallbackContextAnalysis(
      segment = "overfitting in random forest",
      surroundingContext = emptyList(),
      factCheckingMode = false,
      isExplicitDoubtQuery = true
    )
    assertTrue(explicitResult.isDoubt)
    assertEquals("Machine Learning", explicitResult.subject)
    assertTrue(explicitResult.quickAnswer.contains("majority vote", ignoreCase = true))
  }

  @Test
  fun audioWavEncoder_computesRmsAndGeneratesValidRiffWaveHeader() {
    val silent = ShortArray(160) { 0 }
    assertEquals(0f, com.example.audio.AudioWavEncoder.computeNormalizedRms(silent, silent.size), 0.001f)

    val active = ShortArray(160) { idx -> if (idx % 2 == 0) 4000 else -4000 }
    val rms = com.example.audio.AudioWavEncoder.computeNormalizedRms(active, active.size)
    assertTrue(rms > 0.3f && rms <= 1.0f)

    val pcmBytes = com.example.audio.AudioWavEncoder.shortsToLittleEndianBytes(active, active.size)
    assertEquals(320, pcmBytes.size)

    val wav = com.example.audio.AudioWavEncoder.encodePcm16ToWav(pcmBytes, 16_000, 1)
    assertEquals(44 + pcmBytes.size, wav.size)
    assertEquals('R'.code.toByte(), wav[0])
    assertEquals('I'.code.toByte(), wav[1])
    assertEquals('F'.code.toByte(), wav[2])
    assertEquals('F'.code.toByte(), wav[3])
    assertEquals('W'.code.toByte(), wav[8])
    assertEquals('A'.code.toByte(), wav[9])
    assertEquals('V'.code.toByte(), wav[10])
    assertEquals('E'.code.toByte(), wav[11])
  }

  @Test
  fun answerEngine_buildsStructuredImmediateAnswerFromAnalysis() {
    val engine = com.example.ai.AnswerEngine()
    val analysis = com.example.ai.ContextAnalysisResult(
      isDoubt = true,
      subject = "Physics",
      topic = "Convection & Buoyancy",
      intent = "Understand why heated air rises",
      importantConcepts = listOf("Density", "Buoyancy", "Thermal Expansion"),
      doubtType = "Working principle",
      doubtSnippet = "Why does warm air rise?",
      quickAnswer = "Warm air expands and becomes less dense than surrounding cool air, so buoyant force pushes it upward.",
      detailedAnswer = "Heating air increases molecular kinetic energy and volume, lowering density rho = m/V.",
      notificationSummary = "Warm air is less dense and rises.",
      isAmbiguous = false,
      clarificationQuestion = null,
      suggestedOptions = listOf("Ideal Gas Law"),
      referenceMaterials = "NOAA SciJinks",
      conversationSuggestions = "How does adiabatic cooling work?",
      confidence = 0.94f,
      isFactChecked = true,
      verificationStatus = "Verified via Google Search (2 Source(s))",
      verificationSources = listOf(
        com.example.data.model.VerificationSource(
          title = "NOAA Convection",
          url = "https://scijinks.gov/weather-convection/",
          excerpt = "Warm air expands and rises."
        )
      )
    )

    val result = engine.buildAnswerFromAnalysis(
      analysis = analysis,
      query = "Why does warm air rise?",
      capturedViaVoice = true,
      doubtId = 88L
    )

    assertEquals(88L, result.doubtId)
    assertEquals("Physics", result.subject)
    assertEquals("Gemini + Google Search", result.groundingLabel)
    assertTrue(result.capturedViaVoice)
    assertTrue(result.quickAnswer.contains("less dense"))
    assertEquals(1, result.sources.size)
  }

  @Test
  fun clipboardHelper_formatsQuickAndDetailedAnswersWithoutDuplication() {
    val combined = com.example.util.ClipboardHelper.formatAnswerForClipboard(
      quickAnswer = "  Quick core summary. ",
      detailedAnswer = "  Detailed breakdown with steps. ",
      includeDetailed = true
    )
    assertEquals("Quick core summary.\n\nDetailed breakdown with steps.", combined)

    val quickOnly = com.example.util.ClipboardHelper.formatAnswerForClipboard(
      quickAnswer = "Quick core summary.",
      detailedAnswer = "Detailed breakdown with steps.",
      includeDetailed = false
    )
    assertEquals("Quick core summary.", quickOnly)

    val identical = com.example.util.ClipboardHelper.formatAnswerForClipboard(
      quickAnswer = "Same text.",
      detailedAnswer = "Same text.",
      includeDetailed = true
    )
    assertEquals("Same text.", identical)
  }

  @Test
  fun answerEngineResult_fromDoubtItem_roundTripsQuestionAnswerAndSources() {
    val doubt = DoubtItem(
      id = 55L,
      timestamp = 1_760_600_000_000L,
      doubtSnippet = "What is Rayleigh scattering?",
      subject = "Physics",
      topic = "Optics: Rayleigh Scattering",
      importantConcepts = "Wavelength, Scattering, Atmosphere",
      doubtType = "Voice Answer Engine",
      quickAnswer = "Shorter blue wavelengths scatter much more strongly off air molecules.",
      detailedAnswer = "Intensity scales as 1 / lambda^4.",
      notificationSummary = "Blue light scatters more.",
      confidenceScore = 0.94f,
      isFactChecked = true,
      verificationStatus = "Verified via Google Search",
      verificationSourcesJson = DoubtItem.encodeVerificationSources(
        listOf(
          com.example.data.model.VerificationSource(
            title = "NASA Sky Color",
            url = "https://spaceplace.nasa.gov/blue-sky/",
            excerpt = "Blue light travels as shorter, smaller waves."
          )
        )
      )
    )

    val mapped = com.example.ai.AnswerEngineResult.fromDoubtItem(doubt)
    assertEquals(55L, mapped.doubtId)
    assertEquals("What is Rayleigh scattering?", mapped.query)
    assertEquals("Physics", mapped.subject)
    assertEquals("Optics: Rayleigh Scattering", mapped.topic)
    assertEquals("Gemini + Google Search", mapped.groundingLabel)
    assertTrue(mapped.capturedViaVoice)
    assertEquals(3, mapped.keyConcepts.size)
    assertEquals(1, mapped.sources.size)
    assertEquals("https://spaceplace.nasa.gov/blue-sky/", mapped.sources.first().url)
  }

  @Test
  fun ttsManager_sanitizeForSpeech_stripsMarkdownAndNormalizesWhitespace() {
    val cleaned = com.example.util.TtsManager.sanitizeForSpeech("**Key Point**:  Use   `Room` #1  for local   persistence.")
    assertEquals("Key Point : Use Room 1 for local persistence.", cleaned)
  }

  @Test
  fun documentExport_savedAnswerEngineHistory_omitsEmptyTranscriptAndFormatsQaEntries() {
    val savedResults = listOf(
      com.example.ai.AnswerEngineResult(
        doubtId = 101L,
        query = "What causes the greenhouse effect?",
        subject = "Physics",
        topic = "Greenhouse Effect",
        quickAnswer = "Greenhouse gases absorb and re-emit outgoing infrared thermal radiation.",
        detailedAnswer = "CO2, methane, and water vapor trap longwave infrared energy in the lower atmosphere.",
        keyConcepts = listOf("Infrared Radiation", "CO2"),
        confidence = 0.96f,
        groundingLabel = "Gemini + Google Search",
        isFactChecked = true,
        verificationStatus = "Verified via Google Search",
        sources = listOf(
          com.example.data.model.VerificationSource(
            title = "NASA Climate",
            url = "https://climate.nasa.gov/causes/",
            excerpt = "Gases trap heat radiating toward space."
          )
        ),
        capturedViaVoice = true
      ),
      com.example.ai.AnswerEngineResult(
        doubtId = 102L,
        query = "How does binary search achieve O(log n)?",
        subject = "Computer Science",
        topic = "Binary Search",
        quickAnswer = "It halves the remaining sorted search interval on every comparison.",
        detailedAnswer = "After k comparisons, at most n / 2^k elements remain to inspect.",
        keyConcepts = listOf("Divide and Conquer", "Logarithmic Time"),
        confidence = 0.95f,
        groundingLabel = "Offline Knowledge",
        isFactChecked = false,
        verificationStatus = "",
        sources = emptyList(),
        capturedViaVoice = false
      )
    )

    val doubtItems = savedResults.map { it.toDoubtItem(conversationalContext = "Saved History") }
    val exported = com.example.util.DocumentExportHelper.buildStructuredText(
      conversationTurns = emptyList(),
      doubts = doubtItems,
      documentTitle = "DoubtSense Saved Q&A History (2 Items)"
    )

    assertFalse(exported.contains("PART 1: FULL CONVERSATION TRANSCRIPT"))
    assertTrue(exported.contains("SAVED QUESTIONS & AI-GENERATED ANSWERS"))
    assertTrue(exported.contains("DoubtSense Saved Q&A History (2 Items)"))
    assertTrue(exported.contains("What causes the greenhouse effect?"))
    assertTrue(exported.contains("absorb and re-emit outgoing infrared thermal radiation"))
    assertTrue(exported.contains("How does binary search achieve O(log n)?"))
    assertTrue(exported.contains("halves the remaining sorted search interval"))
    assertTrue(exported.contains("https://climate.nasa.gov/causes/"))
  }

  @Test
  fun answerEngine_analyzeIntentClarity_scoresClearVsAmbiguousInputs() {
    val clearScore = com.example.ai.AnswerEngine.analyzeIntentClarity(
      query = "How does backpropagation update neural network weights?",
      conversationContext = emptyList(),
      modelConfidence = 0.90f,
      isAmbiguous = false
    )
    val ambiguousScore = com.example.ai.AnswerEngine.analyzeIntentClarity(
      query = "um it",
      conversationContext = emptyList(),
      modelConfidence = 0.72f,
      isAmbiguous = true
    )

    assertTrue("Expected clear question to score >= 0.90 but was $clearScore", clearScore >= 0.90f)
    assertTrue("Expected ambiguous terse query to score < 0.55 but was $ambiguousScore", ambiguousScore < 0.55f)

    val (pctClear, labelClear) = com.example.ui.components.formatConfidenceIndicator(0.91f)
    assertEquals(91, pctClear)
    assertEquals("91% clear", labelClear)

    val (pctUnclear, labelUnclear) = com.example.ui.components.formatConfidenceIndicator(0.45f)
    assertEquals(45, pctUnclear)
    assertEquals("45% unclear", labelUnclear)
  }

  @Test
  fun documentExport_buildCsv_outputsRfc4180RowsWithEscapingAndCustomTags() {
    val doubts = listOf(
      DoubtItem(
        id = 12L,
        timestamp = 1_760_600_000_000L,
        doubtSnippet = "Why use \"Quicksort\" in-place?",
        subject = "Computer Science",
        topic = "Quicksort",
        quickAnswer = "Low memory overhead\nand cache locality.",
        detailedAnswer = "O(log n) stack space on average.",
        notificationSummary = "Fast in-place divide and conquer.",
        customTag = "Work",
        isStarred = true,
        oneSentenceSummary = "Quicksort sorts in-place with O(log n) average stack overhead."
      )
    )

    val csv = com.example.util.DocumentExportHelper.buildCsv(
      conversationTurns = listOf("Colleague: Let's pick a sorting algorithm."),
      doubts = doubts
    )

    val lines = csv.trim().lines()
    assertTrue(lines.first().startsWith("ID,RecordedAt,Topic,Subject,CustomTag,Starred,Question,OneSentenceSummary"))
    assertTrue(csv.contains("\"Work\""))
    assertTrue(csv.contains(",true,"))
    assertTrue(csv.contains("\"Why use \"\"Quicksort\"\" in-place?\""))
    assertTrue(csv.contains("Quicksort sorts in-place with O(log n) average stack overhead."))
  }

  @Test
  fun semanticSearchHelper_ranksConceptualQueriesAndHighlightsMatches() {
    val items = listOf(
      DoubtItem(
        id = 1L,
        doubtSnippet = "How does chloroplast ATP synthesis work?",
        subject = "Biology",
        topic = "Photosynthesis",
        quickAnswer = "Light energy converts ADP into ATP inside thylakoids.",
        detailedAnswer = "Proton motive force drives synthase.",
        notificationSummary = "Photosynthesis energy"
      ),
      DoubtItem(
        id = 2L,
        doubtSnippet = "Explain quicksort time complexity",
        subject = "Computer Science",
        topic = "Algorithms",
        quickAnswer = "Partitioning yields efficient O(n log n) average runtime.",
        detailedAnswer = "Good pivots keep sorting fast.",
        notificationSummary = "Sorting runtime"
      )
    )

    // Conceptual query ("performance algorithm" has no exact substring in topic, matches cluster)
    val ranked = com.example.util.SemanticSearchHelper.rankBySemanticAndKeyword(
      items = items,
      query = "fast runtime optimization"
    ) { "${it.topic} ${it.doubtSnippet} ${it.quickAnswer} ${it.detailedAnswer}" }

    assertEquals(1, ranked.size)
    assertEquals(2L, ranked.first().id)

    val highlighted = com.example.util.SemanticSearchHelper.buildHighlightedAnnotatedString(
      text = "Partitioning yields fast O(n log n) runtime.",
      query = "fast runtime",
      highlightBackground = androidx.compose.ui.graphics.Color.Cyan,
      highlightForeground = androidx.compose.ui.graphics.Color.Black
    )
    assertTrue("Expected SpanStyle highlights for matched tokens", highlighted.spanStyles.isNotEmpty())
  }

  @Test
  fun doubtRepository_sortWithPinnedFirstAndFilterRecentDays_behavesAccurately() {
    val now = 1_760_700_000_000L
    val day = 86_400_000L
    val pinnedOlder = DoubtItem(
      id = 1L,
      timestamp = now - (10L * day),
      doubtSnippet = "Pinned older doubt",
      topic = "Math",
      quickAnswer = "Q1",
      detailedAnswer = "D1",
      notificationSummary = "N1",
      isStarred = true
    )
    val unpinnedRecent = DoubtItem(
      id = 2L,
      timestamp = now - (2L * day),
      doubtSnippet = "Recent unpinned doubt",
      topic = "Physics",
      quickAnswer = "Q2",
      detailedAnswer = "D2",
      notificationSummary = "N2",
      isStarred = false
    )

    val sorted = com.example.data.repository.DoubtRepository.sortWithPinnedFirst(
      listOf(unpinnedRecent, pinnedOlder)
    )
    assertEquals(listOf(1L, 2L), sorted.map { it.id })

    val recentOnly = com.example.data.repository.DoubtRepository.filterRecentDays(
      doubts = listOf(pinnedOlder, unpinnedRecent),
      days = 7,
      nowMillis = now
    )
    assertEquals(listOf(2L), recentOnly.map { it.id })
  }

  @Test
  fun voiceCommandProcessor_parsesAllSupportedVoiceCommands() {
    assertEquals(
      com.example.util.VoiceCommand.EXPORT_HISTORY,
      com.example.util.VoiceCommandProcessor.parseCommand("Please export my history!")
    )
    assertEquals(
      com.example.util.VoiceCommand.CLEAR_LIST,
      com.example.util.VoiceCommandProcessor.parseCommand("Clear list")
    )
    assertEquals(
      com.example.util.VoiceCommand.SWITCH_TO_DARK_MODE,
      com.example.util.VoiceCommandProcessor.parseCommand("Switch to dark mode")
    )
    assertEquals(
      com.example.util.VoiceCommand.SWITCH_TO_LIGHT_MODE,
      com.example.util.VoiceCommandProcessor.parseCommand("Enable light mode")
    )
    assertEquals(
      com.example.util.VoiceCommand.SWITCH_TO_HIGH_CONTRAST,
      com.example.util.VoiceCommandProcessor.parseCommand("Switch to high contrast")
    )
    assertNull(
      com.example.util.VoiceCommandProcessor.parseCommand("Why does warm air rise?")
    )
  }

  @Test
  fun computeWeeklyResolvedBuckets_countsResolvedDoubtsPerDayAcrossLast7Days() {
    val now = 1_760_700_000_000L
    val day = 86_400_000L
    val doubts = listOf(
      DoubtItem(
        id = 1L,
        timestamp = now - 1_000L,
        doubtSnippet = "Resolved today 1",
        topic = "Physics",
        quickAnswer = "Answer 1",
        detailedAnswer = "Detail 1",
        notificationSummary = "S1"
      ),
      DoubtItem(
        id = 2L,
        timestamp = now - 2_000L,
        doubtSnippet = "Resolved today 2",
        topic = "Math",
        quickAnswer = "Answer 2",
        detailedAnswer = "Detail 2",
        notificationSummary = "S2"
      ),
      DoubtItem(
        id = 3L,
        timestamp = now - (3L * day),
        doubtSnippet = "Resolved 3 days ago",
        topic = "CS",
        quickAnswer = "Answer 3",
        detailedAnswer = "",
        notificationSummary = "S3"
      ),
      DoubtItem(
        id = 4L,
        timestamp = now - (12L * day),
        doubtSnippet = "Too old",
        topic = "Bio",
        quickAnswer = "Old",
        detailedAnswer = "Old",
        notificationSummary = "Old"
      )
    )

    val buckets = com.example.ui.components.computeWeeklyResolvedBuckets(doubts, nowMillis = now)
    assertEquals(7, buckets.size)
    assertEquals(2, buckets.last().resolvedCount)
    assertEquals(1, buckets[3].resolvedCount)
    assertEquals(3, buckets.sumOf { it.resolvedCount })
  }

  @Test
  fun buildTtsHighlightedText_highlightsActiveSpokenWordInsideAnswer() {
    val quick = "Warm air rises because heating lowers its density."
    val fullSpoken = "$quick Cooler air sinks underneath."
    val start = fullSpoken.indexOf("density")
    val end = start + "density".length - 1

    val annotated = com.example.ui.components.buildTtsHighlightedText(
      displayText = quick,
      fullSpokenText = fullSpoken,
      spokenRange = start..end,
      highlightBg = androidx.compose.ui.graphics.Color.Cyan,
      highlightFg = androidx.compose.ui.graphics.Color.Black
    )

    assertEquals(1, annotated.spanStyles.size)
    val span = annotated.spanStyles.first()
    assertEquals("density", annotated.text.substring(span.start, span.end))
  }

  @Test
  fun ttsManager_snapToWordStartAndSkipLeadingSpaces_handleWordBoundaries() {
    val text = "Warm air rises because heating lowers its density."
    // Middle of "because" -> snaps back to index of 'b'
    val midBecause = text.indexOf("because") + 3
    assertEquals(text.indexOf("because"), com.example.util.TtsManager.snapToWordStart(text, midBecause))

    // Already at word start -> unchanged
    val startRises = text.indexOf("rises")
    assertEquals(startRises, com.example.util.TtsManager.snapToWordStart(text, startRises))

    // Negative / empty safety
    assertEquals(0, com.example.util.TtsManager.snapToWordStart("", 5))
    assertEquals(0, com.example.util.TtsManager.snapToWordStart(text, -2))

    // Skip whitespace
    val spaceBeforeAir = text.indexOf(" ")
    assertEquals(text.indexOf("air"), com.example.util.TtsManager.skipLeadingSpaces(text, spaceBeforeAir))
  }

  @Test
  fun ttsManager_speechRateAndPitch_clampToSupportedRangeAndUpdateState() {
    com.example.util.TtsManager.setSpeechRate(0.1f)
    assertEquals(0.5f, com.example.util.TtsManager.speechRate.value, 0.001f)
    com.example.util.TtsManager.setSpeechRate(1.5f)
    assertEquals(1.5f, com.example.util.TtsManager.getSpeechRate(), 0.001f)
    com.example.util.TtsManager.setSpeechRate(3.0f)
    assertEquals(2.0f, com.example.util.TtsManager.speechRate.value, 0.001f)

    com.example.util.TtsManager.setSpeechPitch(0.2f)
    assertEquals(0.5f, com.example.util.TtsManager.speechPitch.value, 0.001f)
    com.example.util.TtsManager.setSpeechPitch(1.3f)
    assertEquals(1.3f, com.example.util.TtsManager.getSpeechPitch(), 0.001f)
    com.example.util.TtsManager.setSpeechPitch(4.5f)
    assertEquals(2.0f, com.example.util.TtsManager.speechPitch.value, 0.001f)

    com.example.util.TtsManager.setSpeechRate(1.0f)
    com.example.util.TtsManager.setSpeechPitch(1.0f)
  }

  @Test
  fun formatSessionDuration_and_matchesSessionQuery_handleDurationsAndCustomTags() {
    assertEquals("45s", com.example.ui.components.formatSessionDuration(10_000L, 55_000L))
    assertEquals("2m 15s", com.example.ui.components.formatSessionDuration(0L, 135_000L))
    assertEquals("1h 5m", com.example.ui.components.formatSessionDuration(0L, 3_900_000L))
    assertEquals("1s", com.example.ui.components.formatSessionDuration(5_000L, 5_000L))

    val session = com.example.data.model.ConversationSession(
      id = 9L,
      startTime = 1_760_700_000_000L,
      endTime = 1_760_700_120_000L,
      title = "System Design Cache Layer",
      category = "Computer Science",
      customTag = "Work",
      transcriptPreview = "Should we use write-through or write-back caching?",
      summary = "Explained write-through vs write-back cache consistency."
    )
    assertTrue(com.example.ui.screens.matchesSessionQuery(session, "Work", nowMillis = 1_760_700_120_000L))
    assertTrue(com.example.ui.screens.matchesSessionQuery(session, "consistency", nowMillis = 1_760_700_120_000L))
    assertFalse(com.example.ui.screens.matchesSessionQuery(session, "mitosis", nowMillis = 1_760_700_120_000L))
  }

  @Test
  fun documentExportHelper_buildSuggestedSessionFileName_sanitizesTitleAndAppliesFormatExtension() {
    val session = com.example.data.model.ConversationSession(
      id = 42L,
      endTime = 1_760_700_120_000L,
      title = "Physics: Thermodynamics & Entropy!"
    )

    val txtName = com.example.util.DocumentExportHelper.buildSuggestedSessionFileName(
      session = session,
      format = com.example.util.ExportDocumentFormat.STRUCTURED_TEXT
    )
    val pdfName = com.example.util.DocumentExportHelper.buildSuggestedSessionFileName(
      session = session,
      format = com.example.util.ExportDocumentFormat.PDF
    )

    assertTrue(txtName.startsWith("doubtsense_physics_thermodynamics_entropy_"))
    assertTrue(txtName.endsWith(".txt"))
    assertFalse(txtName.contains("!"))
    assertTrue(pdfName.startsWith("doubtsense_physics_thermodynamics_entropy_"))
    assertTrue(pdfName.endsWith(".pdf"))
  }

  @Test
  fun conversationSession_tagHelpers_parseEncodeAndMatchMultipleLabels() {
    val encoded = com.example.data.model.ConversationSession.encodeTags(
      listOf("Work", " Study ", "work", "Exam, Prep", "")
    )
    assertEquals("Work, Study, Exam Prep", encoded)

    val session = com.example.data.model.ConversationSession(
      id = 7L,
      title = "System Architecture Review",
      customTag = encoded
    )
    assertEquals(listOf("Work", "Study", "Exam Prep"), session.getTagList())
    assertTrue(session.hasTag("work"))
    assertTrue(session.hasTag("Study"))
    assertTrue(session.hasTag("Exam Prep"))
    assertFalse(session.hasTag("Personal"))

    assertEquals("General", com.example.data.model.ConversationSession.encodeTags(emptyList()))
  }

  @Test
  fun categoryFrequencyCalculator_bucketsTaggedSessionsAcrossTimeAndDimensions() {
    val now = 1_760_700_000_000L
    val day = 86_400_000L
    val sessions = listOf(
      com.example.data.model.ConversationSession(
        id = 1L,
        startTime = now - 2_000L,
        endTime = now - 1_000L,
        title = "Distributed Locks",
        category = "Systems",
        customTag = "Work, Study"
      ),
      com.example.data.model.ConversationSession(
        id = 2L,
        startTime = now - (1L * day),
        endTime = now - (1L * day) + 1_000L,
        title = "Neural Net Backprop",
        category = "AI",
        customTag = "Study"
      ),
      com.example.data.model.ConversationSession(
        id = 3L,
        startTime = now - (2L * day),
        endTime = now - (2L * day) + 1_000L,
        title = "Sprint Planning API",
        category = "Systems",
        customTag = "Work"
      ),
      com.example.data.model.ConversationSession(
        id = 4L,
        startTime = now - (14L * day),
        endTime = now - (14L * day) + 1_000L,
        title = "Home Budget Review",
        category = "Finance",
        customTag = "Personal"
      )
    )

    // 7-day tag breakdown: excludes 14-day-old session, counts multi-tagged session under both Study and Work
    val summary7d = com.example.ui.components.CategoryFrequencyCalculator.computeCategoryFrequencyOverTime(
      sessions = sessions,
      period = com.example.ui.components.CategoryFrequencyPeriod.SEVEN_DAYS,
      dimension = com.example.ui.components.CategoryGroupingDimension.TAGS,
      nowMillis = now
    )
    assertEquals(7, summary7d.buckets.size)
    assertEquals(3, summary7d.totalSessionsCounted)
    val totals7d = summary7d.categories.associate { it.categoryName to it.totalCount }
    assertEquals(2, totals7d["Study"])
    assertEquals(2, totals7d["Work"])
    assertFalse(totals7d.containsKey("Personal"))
    // Today's bucket (last index) has Study=1 and Work=1
    val todayBucket = summary7d.buckets.last()
    assertEquals(1, todayBucket.countsByCategory["Study"])
    assertEquals(1, todayBucket.countsByCategory["Work"])

    // 4-week tag breakdown: includes the 14-day-old Personal session
    val summary4w = com.example.ui.components.CategoryFrequencyCalculator.computeCategoryFrequencyOverTime(
      sessions = sessions,
      period = com.example.ui.components.CategoryFrequencyPeriod.FOUR_WEEKS,
      dimension = com.example.ui.components.CategoryGroupingDimension.TAGS,
      nowMillis = now
    )
    assertEquals(4, summary4w.buckets.size)
    assertEquals(4, summary4w.totalSessionsCounted)
    val totals4w = summary4w.categories.associate { it.categoryName to it.totalCount }
    assertEquals(1, totals4w["Personal"])

    // Subject dimension over 4 weeks groups by session.category
    val summarySubjects = com.example.ui.components.CategoryFrequencyCalculator.computeCategoryFrequencyOverTime(
      sessions = sessions,
      period = com.example.ui.components.CategoryFrequencyPeriod.FOUR_WEEKS,
      dimension = com.example.ui.components.CategoryGroupingDimension.SUBJECTS,
      nowMillis = now
    )
    val subjectTotals = summarySubjects.categories.associate { it.categoryName to it.totalCount }
    assertEquals(2, subjectTotals["Systems"])
    assertEquals(1, subjectTotals["AI"])
    assertEquals(1, subjectTotals["Finance"])

    // 3-month bucketing creates 3 monthly buckets
    val summary3m = com.example.ui.components.CategoryFrequencyCalculator.computeCategoryFrequencyOverTime(
      sessions = sessions,
      period = com.example.ui.components.CategoryFrequencyPeriod.THREE_MONTHS,
      dimension = com.example.ui.components.CategoryGroupingDimension.TAGS,
      nowMillis = now
    )
    assertEquals(3, summary3m.buckets.size)
    assertEquals(4, summary3m.totalSessionsCounted)
  }

  @Test
  fun liveChatHighlight_classifiesDoubtSignalsVsNeutralSpeechAndResetsOnClear() {
    ListeningStateManager.clearTranscript()
    assertEquals(
      com.example.service.ChatTurnHighlightState.NEUTRAL,
      ListeningStateManager.classifyTextForChatHighlight("Let's grab coffee at 3pm after class.")
    )
    assertEquals(
      com.example.service.ChatTurnHighlightState.UNCERTAIN_LIVE,
      ListeningStateManager.classifyTextForChatHighlight("I don't understand how does backpropagation compute gradients?")
    )

    ListeningStateManager.updateLiveTranscript("Wait why does quicksort degrade to O(n squared)?")
    assertEquals(
      com.example.service.ChatTurnHighlightState.UNCERTAIN_LIVE,
      ListeningStateManager.liveTranscriptHighlight.value
    )

    ListeningStateManager.clearTranscript()
    assertEquals(com.example.service.ChatTurnHighlightState.NEUTRAL, ListeningStateManager.liveTranscriptHighlight.value)
    assertTrue(ListeningStateManager.liveChatTurns.value.isEmpty())
    assertNull(ListeningStateManager.liveDoubtAlert.value)
  }

  @Test
  fun documentExport_buildStructuredText_includesFullSessionAnalysisAndDoubtHistory() {
    val now = 1_760_800_000_000L
    val session = com.example.data.model.ConversationSession(
      id = 42L,
      startTime = now - 180_000L,
      endTime = now,
      title = "Distributed Consensus & Raft",
      category = "Distributed Systems",
      customTag = "Work, Study",
      transcriptJson = com.example.data.model.ConversationSession.encodeTranscriptTurns(
        listOf(
          "Engineer: Raft elects a leader via randomized election timeouts.",
          "Me: How does Raft prevent split brain during network partitions?"
        )
      ),
      averageDoubtIntensity = 0.74f,
      peakDoubtIntensity = 0.91f,
      doubtCount = 1,
      summary = "Majority quorum ensures at most one leader can commit entries per term."
    )
    val doubt = DoubtItem(
      id = 99L,
      sessionId = 42L,
      timestamp = now - 30_000L,
      doubtSnippet = "How does Raft prevent split brain during network partitions?",
      subject = "Distributed Systems",
      topic = "Raft Majority Quorum",
      quickAnswer = "A leader requires votes from a strict majority (> N/2) of nodes, which only one partition can have.",
      detailedAnswer = "Because any two majorities overlap in at least one node, two distinct leaders cannot be elected in the same term.",
      notificationSummary = "Strict majority quorums prevent split-brain leaders in Raft."
    )

    val report = com.example.util.DocumentExportHelper.buildStructuredText(
      conversationTurns = session.getTranscriptTurns(),
      doubts = listOf(doubt),
      documentTitle = "DoubtSense Doubt History & Session Analysis",
      sessions = listOf(session)
    )

    assertTrue(report.contains("SESSION ANALYSIS OVERVIEW"))
    assertTrue(report.contains("Sessions Analyzed: 1"))
    assertTrue(report.contains("Overall Avg Doubt Intensity: 74% (Peak: 91%)"))
    assertTrue(report.contains("Tags Covered: Work (1), Study (1)"))
    assertTrue(report.contains("Distributed Consensus & Raft"))
    assertTrue(report.contains("Duration: 3m 0s"))
    assertTrue(report.contains("Session Takeaway: Majority quorum ensures at most one leader"))
    assertTrue(report.contains("PART 1: FULL CONVERSATION TRANSCRIPT"))
    assertTrue(report.contains("PART 2: DETECTED DOUBTS & AI-GENERATED ANSWERS"))
    assertTrue(report.contains("Raft Majority Quorum"))
  }

  @Test
  fun chatVoiceInput_updatesPartialTranscriptSignalsAndResetsCleanly() {
    ListeningStateManager.clearTranscript()
    assertEquals(
      com.example.service.ChatVoiceInputState.Idle,
      ListeningStateManager.chatVoiceInputState.value
    )

    ListeningStateManager.chatCaptureListener.onCaptureStarted("Live Speech-to-Text")
    ListeningStateManager.chatCaptureListener.onAudioLevelChanged(0.72f)
    ListeningStateManager.chatCaptureListener.onElapsedSecondsChanged(4)
    ListeningStateManager.chatCaptureListener.onPartialTranscript("I don't understand how ATP synthase works")

    val recording = ListeningStateManager.chatVoiceInputState.value as? com.example.service.ChatVoiceInputState.Recording
    assertNotNull(recording)
    assertEquals("Live Speech-to-Text", recording!!.modeLabel)
    assertEquals(0.72f, recording.audioLevel, 0.01f)
    assertEquals(4, recording.elapsedSeconds)
    assertEquals("I don't understand how ATP synthase works", recording.partialTranscript)
    assertEquals("I don't understand how ATP synthase works", ListeningStateManager.liveTranscript.value)
    assertEquals(
      com.example.service.ChatTurnHighlightState.UNCERTAIN_LIVE,
      ListeningStateManager.liveTranscriptHighlight.value
    )

    ListeningStateManager.cancelChatVoiceInput()
    assertEquals(
      com.example.service.ChatVoiceInputState.Idle,
      ListeningStateManager.chatVoiceInputState.value
    )
    assertEquals("", ListeningStateManager.liveTranscript.value)
    assertEquals(0f, ListeningStateManager.audioRms.value, 0.001f)
  }

  @Test
  fun firestoreSessionAndDoubtBackup_fullFidelityMappingAndWriteMapsExcludeIdAndFilterNulls() {
    val session = com.example.data.model.ConversationSession(
      id = 5L,
      startTime = 1_760_900_000_000L,
      endTime = 1_760_900_060_000L,
      title = "Distributed Consensus & Raft",
      category = "Distributed Systems",
      transcriptJson = com.example.data.model.ConversationSession.encodeTranscriptTurns(
        listOf("Speaker: Raft uses randomized timeouts.", "Me: How does Raft prevent split brain?")
      ),
      transcriptPreview = "Me: How does Raft prevent split brain?",
      averageDoubtIntensity = 0.76f,
      peakDoubtIntensity = 0.91f,
      doubtCount = 2,
      summary = "Strict majority quorum prevents dual leaders.",
      customTag = "Work, Study",
      isPinned = true,
      cloudId = "session_deviceA_001",
      updatedAtMillis = 1_760_900_065_000L
    )

    val doubt = DoubtItem(
      id = 12L,
      sessionId = 5L,
      timestamp = 1_760_900_030_000L,
      doubtSnippet = "How does Raft prevent split brain?",
      topic = "Raft Majority Quorum",
      quickAnswer = "A leader needs votes from more than N/2 nodes.",
      detailedAnswer = "Two majorities always overlap in at least one node.",
      notificationSummary = "Majority quorum stops split brain.",
      conversationalContext = "Raft leader election discussion",
      confidenceScore = 0.92f,
      isStarred = true,
      subject = "Distributed Systems",
      importantConcepts = "Quorum, Leader Election, Term",
      doubtType = "Mechanism",
      intent = "Understand consensus safety",
      isAmbiguous = false,
      isFactChecked = true,
      verificationStatus = "Verified via 1 Source",
      verificationSourcesJson = DoubtItem.encodeVerificationSources(
        listOf(
          com.example.data.model.VerificationSource(
            title = "Raft Paper",
            url = "https://raft.github.io/",
            excerpt = "Election safety property."
          )
        )
      ),
      customTag = "Work, Study",
      oneSentenceSummary = "Strict majority quorums prevent dual Raft leaders.",
      cloudId = "doubt_deviceA_001",
      sessionCloudId = session.cloudId,
      updatedAtMillis = 1_760_900_035_000L
    )

    val sessionBackup = com.example.data.remote.FirestoreSessionBackup.fromRoomEntity(session)
    val sessionCreateMap = sessionBackup.toCreateMap()
    assertFalse("Document ID must never be written inside payload", sessionCreateMap.containsKey("id"))
    assertEquals("session_deviceA_001", sessionCreateMap["cloudId"])
    assertEquals(1_760_900_000_000L, sessionCreateMap["startTime"])
    assertEquals(1_760_900_060_000L, sessionCreateMap["endTime"])
    assertEquals(2L, sessionCreateMap["doubtCount"])
    assertEquals(true, sessionCreateMap["isPinned"])
    assertEquals(1_760_900_065_000L, sessionCreateMap["updatedAtMillis"])
    assertEquals(com.google.firebase.firestore.FieldValue.serverTimestamp(), sessionCreateMap["createdAt"])
    assertEquals(com.google.firebase.firestore.FieldValue.serverTimestamp(), sessionCreateMap["updatedAt"])

    val restoredSession = sessionBackup.toRoomEntity()
    assertEquals(session.cloudId, restoredSession.cloudId)
    assertEquals(session.startTime, restoredSession.startTime)
    assertEquals(session.endTime, restoredSession.endTime)
    assertEquals(session.averageDoubtIntensity, restoredSession.averageDoubtIntensity, 0.001f)
    assertEquals(session.peakDoubtIntensity, restoredSession.peakDoubtIntensity, 0.001f)
    assertEquals(session.doubtCount, restoredSession.doubtCount)
    assertEquals(session.summary, restoredSession.summary)
    assertEquals(session.customTag, restoredSession.customTag)
    assertEquals(session.isPinned, restoredSession.isPinned)
    assertEquals(session.updatedAtMillis, restoredSession.updatedAtMillis)

    val doubtBackup = com.example.data.remote.FirestoreDoubtBackup.fromRoomEntity(doubt)
    val doubtCreateMap = doubtBackup.toCreateMap()
    assertFalse("Document ID must never be written inside payload", doubtCreateMap.containsKey("id"))
    assertEquals("doubt_deviceA_001", doubtCreateMap["cloudId"])
    assertEquals("session_deviceA_001", doubtCreateMap["sessionCloudId"])
    assertEquals("A leader needs votes from more than N/2 nodes.", doubtCreateMap["quickAnswer"])
    assertEquals(true, doubtCreateMap["isFactChecked"])
    assertEquals(com.google.firebase.firestore.FieldValue.serverTimestamp(), doubtCreateMap["updatedAt"])

    val restoredDoubt = doubtBackup.toRoomEntity()
    assertEquals(doubt.cloudId, restoredDoubt.cloudId)
    assertEquals(doubt.sessionCloudId, restoredDoubt.sessionCloudId)
    assertEquals(doubt.quickAnswer, restoredDoubt.quickAnswer)
    assertEquals(doubt.detailedAnswer, restoredDoubt.detailedAnswer)
    assertEquals(doubt.subject, restoredDoubt.subject)
    assertTrue(restoredDoubt.isFactChecked)
    assertEquals(1, restoredDoubt.getVerificationSources().size)
  }

  @Test
  fun conversationSession_cloudIdGenerationAndSanitization_preventsCollisionsAndInvalidDocIds() {
    val first = com.example.data.model.ConversationSession.newCloudId("session")
    val second = com.example.data.model.ConversationSession.newCloudId("session")
    assertTrue(first.startsWith("session_"))
    assertTrue(second.startsWith("session_"))
    assertTrue("Generated cloudIds must be unique", first != second)
    assertTrue(com.example.data.model.ConversationSession.isValidCloudId(first))
    assertTrue(com.example.data.model.ConversationSession.isValidCloudId(second))

    val sanitized = com.example.data.model.ConversationSession.sanitizeCloudId("bad doc id / with spaces!")
    assertTrue(com.example.data.model.ConversationSession.isValidCloudId(sanitized))
    assertFalse(sanitized.contains(" "))
    assertFalse(sanitized.contains("/"))
  }

  @Test
  fun listeningStateManager_restoreSessionIntoLiveChat_repopulatesTurnsDoubtsAndHighlights() {
    ListeningStateManager.clearTranscript()
    val session = com.example.data.model.ConversationSession(
      id = 77L,
      startTime = 1_760_910_000_000L,
      endTime = 1_760_910_120_000L,
      title = "Thermodynamics Synced Session",
      category = "Physics",
      customTag = "Study",
      transcriptJson = com.example.data.model.ConversationSession.encodeTranscriptTurns(
        listOf(
          "Speaker: Heated air molecules move faster.",
          "Me: Wait, why does warm air actually rise?"
        )
      ),
      averageDoubtIntensity = 0.72f,
      peakDoubtIntensity = 0.85f,
      doubtCount = 1,
      summary = "Thermal expansion decreases density."
    )
    val linkedDoubt = DoubtItem(
      id = 88L,
      sessionId = 77L,
      sessionCloudId = session.cloudId,
      doubtSnippet = "Wait, why does warm air actually rise?",
      topic = "Convection & Buoyancy",
      subject = "Physics",
      quickAnswer = "Warm air is less dense than cooler air.",
      detailedAnswer = "Density decreases as temperature rises at constant pressure.",
      notificationSummary = "Warm air rises via buoyancy.",
      confidenceScore = 0.85f
    )

    ListeningStateManager.restoreSessionIntoLiveChat(session, listOf(linkedDoubt))

    assertEquals(77L, ListeningStateManager.activeSessionId.value)
    assertEquals(2, ListeningStateManager.conversationHistory.value.size)
    assertEquals(2, ListeningStateManager.liveChatTurns.value.size)
    assertEquals(
      com.example.service.ChatTurnHighlightState.DOUBT_CONFIRMED,
      ListeningStateManager.liveChatTurns.value.last().highlightState
    )
    assertEquals(88L, ListeningStateManager.latestDetectedDoubt.value?.id)
    val snapshot = ListeningStateManager.getLiveSessionAnalysisSnapshot()
    assertNotNull(snapshot)
    assertEquals("Thermodynamics Synced Session", snapshot!!.title)
    assertEquals(1, snapshot.doubtCount)

    ListeningStateManager.clearTranscript()
  }
}




