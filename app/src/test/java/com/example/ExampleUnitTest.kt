package com.example

import com.example.data.model.DoubtItem
import com.example.service.ListeningStateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
}

