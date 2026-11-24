package com.example

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DoubtItem
import com.example.data.model.VerificationSource
import com.example.data.repository.DailyInsightSummary
import com.example.service.ListeningStateManager
import android.Manifest
import android.app.Application
import android.app.NotificationManager
import com.example.ui.components.DailyInsightCard
import com.example.ui.components.DoubtCard
import com.example.ui.components.DoubtFrequency30DayChart
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.ExportReminderManager
import com.example.util.NotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Before
  fun resetRobolectricFileProviderCache() {
    runCatching {
      androidx.core.content.FileProvider::class.java.declaredFields.forEach { field ->
        if (java.util.Map::class.java.isAssignableFrom(field.type)) {
          field.isAccessible = true
          (field.get(null) as? MutableMap<*, *>)?.clear()
        }
      }
    }
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("DoubtSense", appName)
  }

  @Test
  fun `test DoubtItem model creation`() {
    val item = DoubtItem(
      id = 1L,
      doubtSnippet = "Why does warm air rise?",
      topic = "Physics: Thermodynamics",
      quickAnswer = "Warm air is less dense than cooler air, causing it to float upwards.",
      detailedAnswer = "When air warms up, its molecules move faster and spread apart, lowering its density. The surrounding denser cool air pushes underneath, buoying the warm air upward.",
      notificationSummary = "Physics: Warm air is less dense and rises due to buoyant forces."
    )
    assertNotNull(item)
    assertEquals("Physics: Thermodynamics", item.topic)
    assertEquals(false, item.isAmbiguous)
  }

  @Test
  fun `room database persists conversation sessions and linked doubts`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(
      context,
      com.example.data.local.DoubtDatabase::class.java
    ).allowMainThreadQueries().build()

    try {
      val repo = com.example.data.repository.DoubtRepository(db.doubtDao(), db.conversationSessionDao())
      val sessionId = repo.saveSession(
        com.example.data.model.ConversationSession(
          title = "Algorithms Review",
          category = "Computer Science",
          transcriptJson = com.example.data.model.ConversationSession.encodeTranscriptTurns(
            listOf("Peer: Merge sort is always O(n log n).", "Me: Why is quicksort sometimes O(n^2)?")
          ),
          transcriptPreview = "Me: Why is quicksort sometimes O(n^2)?",
          averageDoubtIntensity = 0.72f,
          peakDoubtIntensity = 0.85f,
          doubtCount = 1
        )
      )

      repo.saveDoubt(
        DoubtItem(
          sessionId = sessionId,
          doubtSnippet = "Why is quicksort sometimes O(n^2)?",
          subject = "Computer Science",
          topic = "Quicksort Complexity",
          quickAnswer = "Unbalanced pivot picks create O(n) recursion depth.",
          detailedAnswer = "Picking min/max repeatedly yields quadratic comparisons.",
          notificationSummary = "Quicksort worst case is O(n^2)."
        )
      )

      val fetchedSession = repo.getSessionById(sessionId)
      val linkedDoubts = repo.getDoubtsForSessionOnce(sessionId)

      assertNotNull(fetchedSession)
      assertEquals("Computer Science", fetchedSession!!.category)
      assertEquals(2, fetchedSession.getTranscriptTurns().size)
      assertEquals(1, linkedDoubts.size)
      assertEquals("Quicksort Complexity", linkedDoubts.first().topic)
    } finally {
      db.close()
    }
  }

  @Test
  fun `factCheckingMode persists preference and Room stores verification sources`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.service.ListeningStateManager.setFactCheckingMode(true, context)
    assertEquals(true, com.example.service.ListeningStateManager.factCheckingMode.value)

    val prefs = context.getSharedPreferences("doubtsense_prefs", Context.MODE_PRIVATE)
    assertEquals(true, prefs.getBoolean("fact_checking_mode", false))

    val db = androidx.room.Room.inMemoryDatabaseBuilder(
      context,
      com.example.data.local.DoubtDatabase::class.java
    ).allowMainThreadQueries().build()

    try {
      val repo = com.example.data.repository.DoubtRepository(db.doubtDao(), db.conversationSessionDao())
      val sources = com.example.ai.ContextAnalysisService.defaultVerificationSourcesForTopic(
        subject = "Computer Science",
        topic = "Algorithms: Merge Sort vs Quicksort",
        queryText = "Why does quicksort degrade?"
      )
      val id = repo.saveDoubt(
        DoubtItem(
          doubtSnippet = "Why does quicksort degrade?",
          subject = "Computer Science",
          topic = "Algorithms: Merge Sort vs Quicksort",
          quickAnswer = "Unbalanced pivots cause O(n^2).",
          detailedAnswer = "Detailed explanation",
          notificationSummary = "Quicksort O(n^2)",
          isFactChecked = true,
          verificationStatus = "Verified via ${sources.size} External Source(s)",
          verificationSourcesJson = DoubtItem.encodeVerificationSources(sources)
        )
      )
      val saved = repo.getDoubtById(id)
      assertNotNull(saved)
      assertEquals(true, saved!!.isFactChecked)
      assertEquals(2, saved.getVerificationSources().size)
    } finally {
      db.close()
      com.example.service.ListeningStateManager.setFactCheckingMode(false, context)
    }
  }

  @Test
  fun `sources expander toggles reference URLs and snippets when Fact-Checking Mode is active`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    ListeningStateManager.setFactCheckingMode(true, context)
    try {
      val sources = listOf(
        VerificationSource(
          title = "MIT OpenCourseWare: Quicksort",
          url = "https://ocw.mit.edu/algorithms/quicksort",
          excerpt = "Worst-case O(n^2) occurs when pivots split arrays asymmetrically."
        ),
        VerificationSource(
          title = "CP-Algorithms Reference",
          url = "https://cp-algorithms.com/sorting/quicksort.html",
          excerpt = "Randomized pivot selection yields expected O(n log n) runtime."
        )
      )
      val doubt = DoubtItem(
        id = 42L,
        doubtSnippet = "Why is quicksort sometimes O(n^2)?",
        subject = "Computer Science",
        topic = "Algorithms: Quicksort",
        quickAnswer = "Unbalanced pivot selection causes quadratic comparisons.",
        detailedAnswer = "Repeatedly choosing an extreme element as pivot leads to O(n) recursion depth.",
        notificationSummary = "Quicksort worst case is O(n^2).",
        isFactChecked = true,
        verificationStatus = "Verified via 2 External Sources",
        verificationSourcesJson = DoubtItem.encodeVerificationSources(sources)
      )

      composeTestRule.setContent {
        MyApplicationTheme {
          DoubtCard(doubt = doubt)
        }
      }
      composeTestRule.waitForIdle()

      // Toggle is visible, reference panel is initially collapsed
      composeTestRule.onNodeWithTag("sources_toggle_42").assertIsDisplayed()
      composeTestRule.onNodeWithText("Sources (2)", substring = true).assertIsDisplayed()
      composeTestRule.onNodeWithTag("fact_checked_panel_42").assertDoesNotExist()

      // Clicking the Sources toggle reveals the specific URLs and search snippets
      composeTestRule.onNodeWithTag("sources_toggle_42").performClick()
      composeTestRule.waitForIdle()

      composeTestRule.onNodeWithTag("fact_checked_panel_42").assertIsDisplayed()
      composeTestRule.onNodeWithTag("verified_source_link_42_0").assertIsDisplayed()
      composeTestRule.onNodeWithText("https://ocw.mit.edu/algorithms/quicksort").assertIsDisplayed()
      composeTestRule.onNodeWithText("Worst-case O(n^2) occurs when pivots split arrays asymmetrically.", substring = true).assertIsDisplayed()

      // Clicking again collapses the Sources panel
      composeTestRule.onNodeWithTag("sources_toggle_42").performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("fact_checked_panel_42").assertDoesNotExist()
    } finally {
      ListeningStateManager.setFactCheckingMode(false, context)
    }
  }

  @Test
  fun `sources expander is hidden when Fact-Checking Mode is disabled and doubt is unverified`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    ListeningStateManager.setFactCheckingMode(false, context)

    val doubt = DoubtItem(
      id = 99L,
      doubtSnippet = "How does osmosis work?",
      subject = "Biology",
      topic = "Cell Transport",
      quickAnswer = "Water moves across a semipermeable membrane toward higher solute concentration.",
      detailedAnswer = "Osmosis equalizes solute concentrations across membranes.",
      notificationSummary = "Water moves toward higher solute concentration.",
      isFactChecked = false
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        DoubtCard(doubt = doubt)
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("sources_toggle_99").assertDoesNotExist()
  }

  @Test
  fun `dailyInsightCard renders highlight summary expands full takeaway and triggers review action`() {
    var inspectedDoubtId: Long? = null
    val highlightedDoubt = DoubtItem(
      id = 77L,
      doubtSnippet = "Why does quicksort degrade to O(n^2)?",
      subject = "Computer Science",
      topic = "Quicksort Worst-Case",
      quickAnswer = "Repeated extreme pivots skew partitions.",
      detailedAnswer = "Choosing min or max as pivot produces n-1 partitions at every recursive step.",
      notificationSummary = "Skewed pivot selection yields quadratic complexity.",
      confidenceScore = 0.88f,
      isFactChecked = true
    )
    val summary = DailyInsightSummary(
      dateLabel = "Today • Nov 12",
      isFromToday = true,
      mostSignificantDoubt = highlightedDoubt,
      significanceScore = 0.91f,
      todayDoubtCount = 2,
      todaySessionCount = 1,
      todayAverageIntensity = 0.52f,
      previousAverageIntensity = 0.78f,
      intensityDelta = -0.26f,
      headline = "Quicksort Worst-Case",
      momentQuote = "\"Why does quicksort degrade to O(n^2)?\"",
      resolutionTakeaway = "Skewed pivot selection yields quadratic complexity.",
      detailedExplanation = "Choosing min or max as pivot produces n-1 partitions at every recursive step.",
      progressNarrative = "Uncertainty dropped 26% vs prior days — steady mastery progress in Computer Science.",
      subjectCategory = "Computer Science"
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          DailyInsightCard(
            insight = summary,
            onInspectDoubt = { inspectedDoubtId = it },
            onSelectCategory = {},
            onNavigateToLiveSense = {}
          )
        }
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("daily_insight_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("daily_insight_headline").assertIsDisplayed()
    composeTestRule.onNodeWithTag("daily_insight_takeaway").assertIsDisplayed()
    composeTestRule.onNodeWithText("-26% Uncertainty").assertIsDisplayed()
    composeTestRule.onNodeWithTag("daily_insight_expanded_details").assertDoesNotExist()

    // Expand full takeaway
    composeTestRule.onNodeWithTag("daily_insight_expand_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("daily_insight_expanded_details").assertIsDisplayed()

    // Click Review Moment button
    composeTestRule.onNodeWithTag("daily_insight_inspect_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(77L, inspectedDoubtId)
  }

  @Test
  fun `dailyInsightCard renders empty state when no data exists yet`() {
    var navigated = false
    composeTestRule.setContent {
      MyApplicationTheme {
        DailyInsightCard(
          insight = null,
          onInspectDoubt = {},
          onSelectCategory = {},
          onNavigateToLiveSense = { navigated = true }
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("daily_insight_empty_state").assertIsDisplayed()
    composeTestRule.onNodeWithTag("daily_insight_empty_state").performClick()
    assertEquals(true, navigated)
  }

  @Test
  fun `doubtFrequency30DayChart renders stats filters by subject and toggles chart mode`() {
    val now = 1_760_020_000_000L
    val day = 86_400_000L
    val doubts = listOf(
      DoubtItem(
        id = 1L,
        timestamp = now - 3_600_000L,
        doubtSnippet = "Why does entropy rise?",
        subject = "Physics",
        topic = "Thermodynamics",
        quickAnswer = "Microstates increase.",
        detailedAnswer = "Detailed",
        notificationSummary = "Entropy"
      ),
      DoubtItem(
        id = 2L,
        timestamp = now - 1_800_000L,
        doubtSnippet = "How does Carnot work?",
        subject = "Physics",
        topic = "Carnot",
        quickAnswer = "Reversible cycle.",
        detailedAnswer = "Detailed",
        notificationSummary = "Carnot"
      ),
      DoubtItem(
        id = 3L,
        timestamp = now - (4 * day),
        doubtSnippet = "Why is merge sort O(n log n)?",
        subject = "Computer Science",
        topic = "Merge Sort",
        quickAnswer = "Balanced splits.",
        detailedAnswer = "Detailed",
        notificationSummary = "Merge Sort"
      )
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          DoubtFrequency30DayChart(
            doubts = doubts,
            sessions = emptyList(),
            nowMillis = now
          )
        }
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("doubt_frequency_30d_chart").assertIsDisplayed()
    composeTestRule.onNodeWithTag("frequency_30d_canvas").assertIsDisplayed()
    composeTestRule.onNodeWithTag("frequency_selected_day_callout").assertIsDisplayed()
    composeTestRule.onNodeWithText("3").assertIsDisplayed() // 30d Total = 3

    // Switch chart visualization mode from Bars to Curve (toggle button shows "Curve" -> after click shows "Bars")
    composeTestRule.onNodeWithText("Curve").assertIsDisplayed()
    composeTestRule.onNodeWithTag("frequency_chart_mode_toggle").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Bars").assertIsDisplayed()

    // Filter by Computer Science -> total drops to 1
    composeTestRule.onNodeWithTag("freq_filter_Computer Science").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("1 doubt").assertIsDisplayed()
  }

  @Test
  fun `doubtFrequency30DayChart displays empty hint when no doubts exist in last 30 days`() {
    composeTestRule.setContent {
      MyApplicationTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          DoubtFrequency30DayChart(
            doubts = emptyList(),
            sessions = emptyList()
          )
        }
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("doubt_frequency_30d_chart").assertIsDisplayed()
    composeTestRule.onNodeWithTag("frequency_empty_hint").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `doubtFrequency30DayChart shows interactive tooltip with specific date and intensity when tapping data point`() {
    val now = 1_760_020_000_000L
    val day = 86_400_000L
    val midDay = now - (14L * day)

    val expectedTodayDate = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).format(java.util.Date(now))
    val expectedMidDate = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).format(java.util.Date(midDay))

    val doubts = listOf(
      DoubtItem(
        id = 10L,
        timestamp = now - 1_800_000L,
        doubtSnippet = "Why does entropy always increase?",
        subject = "Physics",
        topic = "Thermodynamics",
        quickAnswer = "Microstates maximize probability.",
        detailedAnswer = "Detailed",
        notificationSummary = "Entropy",
        confidenceScore = 0.84f
      ),
      DoubtItem(
        id = 11L,
        timestamp = midDay,
        doubtSnippet = "How does merge sort split arrays?",
        subject = "Computer Science",
        topic = "Algorithms",
        quickAnswer = "Divide and conquer.",
        detailedAnswer = "Detailed",
        notificationSummary = "Merge Sort",
        confidenceScore = 0.42f
      )
    )

    lateinit var composeDensity: androidx.compose.ui.unit.Density
    composeTestRule.setContent {
      composeDensity = androidx.compose.ui.platform.LocalDensity.current
      MyApplicationTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          DoubtFrequency30DayChart(
            doubts = doubts,
            sessions = emptyList(),
            nowMillis = now
          )
        }
      }
    }
    composeTestRule.waitForIdle()

    val leftMarginPx = with(composeDensity) { androidx.compose.ui.unit.Dp(32f).toPx() }
    val rightMarginPx = with(composeDensity) { androidx.compose.ui.unit.Dp(12f).toPx() }

    // Tooltip is hidden until a data point is tapped
    composeTestRule.onNodeWithTag("frequency_point_tooltip").assertDoesNotExist()

    // Tap Today (dayIndex = 29) in the bar plot area
    composeTestRule.onNodeWithTag("frequency_30d_canvas").performScrollTo()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("frequency_30d_canvas").performTouchInput {
      val plotStart = left + leftMarginPx
      val plotEnd = right - rightMarginPx
      val todayX = plotStart + (plotEnd - plotStart) * (29.5f / 30f)
      val barTapY = bottom - (bottom - top) * 0.25f
      click(Offset(todayX, barTapY))
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("frequency_point_tooltip").assertIsDisplayed()
    composeTestRule.onNodeWithTag("frequency_tooltip_date").assertTextContains(expectedTodayDate, substring = true)
    composeTestRule.onNodeWithTag("frequency_tooltip_intensity").assertTextContains("84% Intensity")

    // Tap 14 days ago (dayIndex = 15) in the bar plot area
    composeTestRule.onNodeWithTag("frequency_30d_canvas").performTouchInput {
      val plotStart = left + leftMarginPx
      val plotEnd = right - rightMarginPx
      val midX = plotStart + (plotEnd - plotStart) * (15.5f / 30f)
      val barTapY = bottom - (bottom - top) * 0.25f
      click(Offset(midX, barTapY))
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("frequency_point_tooltip").assertIsDisplayed()
    composeTestRule.onNodeWithTag("frequency_tooltip_date").assertTextContains(expectedMidDate, substring = true)
    composeTestRule.onNodeWithTag("frequency_tooltip_intensity").assertTextContains("42% Intensity")

    // Dismiss tooltip via close button
    composeTestRule.onNodeWithTag("frequency_tooltip_dismiss").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("frequency_point_tooltip").assertDoesNotExist()
  }

  @Test
  fun `exportReminderManager dispatches local notification when stale and clears upon export`() = kotlinx.coroutines.runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

    val db = com.example.data.local.DoubtDatabase.getInstance(app)
    db.conversationSessionDao().clearAll()
    db.doubtDao().clearAll()

    val now = System.currentTimeMillis()
    val fourDaysAgo = now - (4L * 86_400_000L)
    val repo = com.example.data.repository.DoubtRepository(db.doubtDao(), db.conversationSessionDao())
    repo.saveSession(
      com.example.data.model.ConversationSession(
        startTime = fourDaysAgo,
        endTime = fourDaysAgo + 60_000L,
        title = "Thermodynamics Notes",
        category = "Physics",
        doubtCount = 1
      )
    )

    app.getSharedPreferences("doubtsense_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    ExportReminderManager.init(app)
    ExportReminderManager.setRemindersEnabled(app, true)
    ExportReminderManager.setIntervalDays(app, 3)

    val triggered = ExportReminderManager.checkAndNotifyIfDue(app, now)
    assertTrue(triggered)

    val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val shadowNm = Shadows.shadowOf(nm)
    assertNotNull(shadowNm.getNotification(NotificationHelper.NOTIFICATION_EXPORT_REMINDER_ID))

    // Recording an export cancels the reminder notification and prevents immediate re-trigger
    ExportReminderManager.markHistoryExported(app, now)
    assertEquals(null, shadowNm.getNotification(NotificationHelper.NOTIFICATION_EXPORT_REMINDER_ID))
    assertFalse(ExportReminderManager.checkAndNotifyIfDue(app, now + 3_600_000L))
  }

  @Test
  fun `settingsScreen renders periodic export reminder controls and updates interval`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    ExportReminderManager.init(context)
    ExportReminderManager.setRemindersEnabled(context, true)
    ExportReminderManager.setIntervalDays(context, 3)

    composeTestRule.setContent {
      MyApplicationTheme {
        SettingsScreen()
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("settings_list")
      .performScrollToNode(hasTestTag("export_reminder_card"))
    composeTestRule.onNodeWithTag("export_reminder_card").assertIsDisplayed()

    composeTestRule.onNodeWithTag("settings_list")
      .performScrollToNode(hasTestTag("export_interval_7"))
    composeTestRule.onNodeWithTag("export_interval_7").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(7, ExportReminderManager.intervalDays.value)
  }

  @Test
  fun `contextAnalysisService preserves grounded prose and DuckDuckGo instant answers`() {
    val service = com.example.ai.ContextAnalysisService()
    val grounding = listOf(
      VerificationSource(
        title = "MDN Web Docs: HTTP/3",
        url = "https://developer.mozilla.org/en-US/docs/Glossary/HTTP_3",
        excerpt = "HTTP/3 runs over QUIC instead of TCP."
      )
    )

    // Non-JSON grounded prose from Tool.googleSearch() should be preserved, not discarded
    val proseParsed = service.parseJsonOrProseResult(
      rawText = "**HTTP/3** uses QUIC over UDP to eliminate head-of-line blocking at the transport layer. It also integrates TLS 1.3 encryption directly into the handshake for faster connection setup.",
      trimmedSegment = "Why is HTTP/3 faster than HTTP/2?",
      surroundingContext = emptyList(),
      factCheckingMode = false,
      groundingSources = grounding,
      forceDoubt = true
    )
    assertNotNull(proseParsed)
    assertTrue(proseParsed!!.isDoubt)
    assertTrue(proseParsed.quickAnswer.contains("QUIC over UDP"))
    assertTrue(proseParsed.isFactChecked)
    assertEquals(1, proseParsed.verificationSources.size)
    assertTrue(proseParsed.verificationStatus.contains("Google Search"))

    // JSON response with isDoubt=false is overridden when forceDoubt=true for explicit manual queries
    val forcedJson = service.parseJsonOrProseResult(
      rawText = """
        {
          "isDoubt": false,
          "subject": "Physics",
          "topic": "Bernoulli Principle",
          "quickAnswer": "Faster fluid speed corresponds to lower static pressure.",
          "detailedAnswer": "In steady inviscid flow, total mechanical energy along a streamline remains constant."
        }
      """.trimIndent(),
      trimmedSegment = "Bernoulli principle",
      surroundingContext = emptyList(),
      factCheckingMode = false,
      groundingSources = emptyList(),
      forceDoubt = true
    )
    assertNotNull(forcedJson)
    assertTrue(forcedJson!!.isDoubt)
    assertEquals("Bernoulli Principle", forcedJson.topic)

    // DuckDuckGo Instant Answer JSON parser
    val ddgRaw = """
      {
        "Heading": "Newton's laws of motion",
        "AbstractText": "Newton's laws of motion are three physical laws describing the relationship between the motion of an object and the forces acting on it.",
        "AbstractURL": "https://en.wikipedia.org/wiki/Newton%27s_laws_of_motion",
        "AbstractSource": "Wikipedia"
      }
    """.trimIndent()
    val ddgResult = com.example.ai.OfflineKnowledgeBase.parseDuckDuckGoJson(ddgRaw, "Newton's laws")
    assertNotNull(ddgResult)
    assertEquals("Newton's laws of motion", ddgResult!!.title)
    assertTrue(ddgResult.quickSummary.contains("three physical laws"))
    assertTrue(ddgResult.sourceUrl.startsWith("https://"))
  }

  @Test
  fun `answerEngineScreen renders voice console submits query and shows immediate answer`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)

    val testEngine = com.example.ai.AnswerEngine(
      localAnalysisProvider = { query, ctx, factCheck ->
        com.example.ai.OfflineKnowledgeBase.matchBuiltInKnowledge(query, ctx, factCheck)
          ?: error("Expected built-in STEM match for '$query'")
      }
    )
    val viewModel = com.example.ui.viewmodel.AnswerEngineViewModel(app, testEngine)

    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.screens.AnswerEngineScreen(viewModel = viewModel)
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("answer_engine_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("answer_engine_mic_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("mode_auto_stt").assertIsDisplayed()
    composeTestRule.onNodeWithTag("mode_direct_audio").assertIsDisplayed()

    // Enter a spoken/typed doubt sentence and submit to Answer Engine
    composeTestRule.onNodeWithTag("answer_engine_query_input")
      .performScrollTo()
      .performTextInput("Why does warm air rise?")
    composeTestRule.onNodeWithTag("answer_engine_ask_button")
      .performScrollTo()
      .performClick()

    // Wait for async AnswerEngine coroutine + Room persistence to complete on Dispatchers.Main
    composeTestRule.waitUntil(timeoutMillis = 10_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      val state = viewModel.uiState.value
      state is com.example.ui.viewmodel.AnswerEngineUiState.Success ||
        state is com.example.ui.viewmodel.AnswerEngineUiState.Error
    }
    val finalState = viewModel.uiState.value
    assertTrue(
      "Expected Success but got $finalState (queryInput='${viewModel.queryInput.value}')",
      finalState is com.example.ui.viewmodel.AnswerEngineUiState.Success
    )

    composeTestRule.onNodeWithTag("answer_engine_screen")
      .performScrollToNode(hasTestTag("answer_engine_result_card"))
    composeTestRule.onNodeWithTag("answer_engine_result_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("answer_engine_echoed_query")
      .performScrollTo()
      .assertTextContains("Why does warm air rise?", substring = true)
    composeTestRule.onNodeWithTag("answer_engine_quick_answer")
      .performScrollTo()
      .assertTextContains("buoyant", substring = true, ignoreCase = true)
  }

  @Test
  fun `doubtSenseApp bottom navigation opens Answer Engine screen`() {
    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.DoubtSenseApp()
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("nav_answer_engine").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("answer_engine_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("answer_engine_mic_button").assertIsDisplayed()
  }

  @Test
  fun `answerEngineResultCard copy button writes AI answer to device clipboard and updates state`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

    val sampleResult = com.example.ai.AnswerEngineResult(
      doubtId = 42L,
      query = "Why is the sky blue?",
      subject = "Physics",
      topic = "Rayleigh Scattering",
      quickAnswer = "Short blue wavelengths scatter far more strongly off atmospheric gas molecules than red light.",
      detailedAnswer = "Rayleigh scattering intensity scales inversely with the fourth power of wavelength (1/λ⁴).",
      keyConcepts = listOf("Rayleigh Scattering", "Wavelength"),
      confidence = 0.95f,
      groundingLabel = "Gemini + Google Search",
      isFactChecked = true,
      verificationStatus = "Verified via Google Search",
      sources = emptyList()
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          com.example.ui.components.AnswerEngineResultCard(result = sampleResult)
        }
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("answer_engine_copy_button")
      .performScrollTo()
      .assertTextContains("Copy")
      .performClick()
    composeTestRule.waitForIdle()

    val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
    assertTrue(clipText.contains("Short blue wavelengths scatter"))
    assertTrue(clipText.contains("Rayleigh scattering intensity"))
    composeTestRule.onNodeWithTag("answer_engine_copy_button")
      .assertTextContains("Copied")
  }

  @Test
  fun `doubtCard copy button writes active AI response to device clipboard`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

    val doubt = DoubtItem(
      id = 77L,
      doubtSnippet = "What is backpropagation?",
      subject = "Machine Learning",
      topic = "Backpropagation",
      quickAnswer = "Backpropagation applies the calculus chain rule backward from the loss to compute each weight's gradient.",
      detailedAnswer = "1. Forward pass computes loss. 2. Backward pass propagates dL/dw gradients through each layer.",
      notificationSummary = "Computes weight gradients via chain rule.",
      conversationalContext = "Student asked during lecture"
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          DoubtCard(doubt = doubt)
        }
      }
    }
    composeTestRule.waitForIdle()

    // Quick Answer mode copies quickAnswer
    composeTestRule.onNodeWithTag("copy_button_77")
      .performScrollTo()
      .assertTextContains("Copy")
      .performClick()
    composeTestRule.waitForIdle()

    val quickClip = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
    assertEquals(doubt.quickAnswer, quickClip)
    composeTestRule.onNodeWithTag("copy_button_77").assertTextContains("Copied")

    // Switch to Detailed Answer and copy full explanation
    composeTestRule.onNodeWithTag("toggle_detailed_77").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("copy_button_77").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    val detailedClip = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
    assertTrue(detailedClip.contains(doubt.quickAnswer))
    assertTrue(detailedClip.contains(doubt.detailedAnswer))
  }

  @Test
  fun `answerEngineScreen shows Room saved QA history list selects item and deletes entry`() = kotlinx.coroutines.runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val repo = ListeningStateManager.getRepository(app)
    repo.clearAll()

    val savedId = repo.saveDoubt(
      DoubtItem(
        timestamp = System.currentTimeMillis() - 60_000L,
        doubtSnippet = "How does photosynthesis convert sunlight?",
        subject = "Biology",
        topic = "Photosynthesis",
        quickAnswer = "Chlorophyll absorbs photons to drive light reactions and the Calvin cycle produces glucose.",
        detailedAnswer = "Light-dependent reactions generate ATP and NADPH; Calvin cycle fixes CO2 into sugar.",
        notificationSummary = "Converts light energy to chemical glucose."
      )
    )

    val viewModel = com.example.ui.viewmodel.AnswerEngineViewModel(app)
    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.screens.AnswerEngineScreen(viewModel = viewModel)
      }
    }

    composeTestRule.waitUntil(timeoutMillis = 8_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      viewModel.recentAnswers.value.any { it.doubtId == savedId }
    }

    composeTestRule.onNodeWithTag("answer_engine_screen")
      .performScrollToNode(hasTestTag("answer_engine_history_section"))
    composeTestRule.onNodeWithTag("answer_engine_history_section").assertIsDisplayed()

    composeTestRule.onNodeWithTag("answer_engine_screen")
      .performScrollToNode(hasTestTag("answer_history_item_$savedId"))
    composeTestRule.onNodeWithTag("answer_history_item_$savedId")
      .assertTextContains("How does photosynthesis convert sunlight?", substring = true)
      .performClick()

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("answer_engine_screen")
      .performScrollToNode(hasTestTag("answer_engine_result_card"))
    composeTestRule.onNodeWithTag("answer_engine_quick_answer")
      .performScrollTo()
      .assertTextContains("Chlorophyll absorbs photons", substring = true)

    // Delete from Room history after resetting selection so row is visible in history list
    viewModel.cancelMicrophoneCapture()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("answer_engine_screen")
      .performScrollToNode(hasTestTag("answer_history_delete_$savedId"))
    composeTestRule.onNodeWithTag("answer_history_delete_$savedId").performClick()

    composeTestRule.waitUntil(timeoutMillis = 8_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      viewModel.recentAnswers.value.none { it.doubtId == savedId }
    }
    assertTrue(viewModel.recentAnswers.value.none { it.doubtId == savedId })
  }

  @Test
  fun `answerEngineScreen activates animated audio visualizer and pulse rings while recording`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = com.example.ui.viewmodel.AnswerEngineViewModel(app)

    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.screens.AnswerEngineScreen(viewModel = viewModel)
      }
    }
    composeTestRule.waitForIdle()

    // Simulate live microphone capture active
    composeTestRule.runOnUiThread {
      viewModel.captureListener.onCaptureStarted("Live Speech-to-Text")
      viewModel.captureListener.onAudioLevelChanged(0.75f)
      viewModel.captureListener.onPartialTranscript("What is Bayes theorem")
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("answer_engine_pulse_rings").assertExists()
    composeTestRule.onNodeWithTag("answer_engine_audio_visualizer").assertIsDisplayed()
    composeTestRule.onNodeWithTag("waveform_visualizer").assertIsDisplayed()
    composeTestRule.onNodeWithTag("waveform_visualizer_status")
      .assertTextContains("LIVE AUDIO CAPTURE")
    composeTestRule.onNodeWithTag("answer_engine_live_transcript")
      .assertTextContains("What is Bayes theorem", substring = true)

    // Cancel recording and verify visualizer deactivates cleanly
    composeTestRule.runOnUiThread {
      viewModel.cancelMicrophoneCapture()
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("answer_engine_pulse_rings").assertDoesNotExist()
  }

  @Test
  fun `answerEngineResultCard listen button activates native TTS playback and toggles stop`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    com.example.util.TtsManager.shutdown()
    com.example.util.TtsManager.init(app)
    composeTestRule.waitForIdle()
    // Ensure native TTS engine is marked initialized in Robolectric sandbox
    com.example.util.TtsManager.onInit(android.speech.tts.TextToSpeech.SUCCESS)

    val sampleResult = com.example.ai.AnswerEngineResult(
      doubtId = 99L,
      query = "Explain osmosis",
      subject = "Biology",
      topic = "Osmosis",
      quickAnswer = "Water moves across a semipermeable membrane toward higher solute concentration.",
      detailedAnswer = "Osmosis equalizes chemical potential across the membrane without solute transport.",
      keyConcepts = listOf("Osmosis", "Membrane"),
      confidence = 0.93f,
      groundingLabel = "Verified AI Engine",
      isFactChecked = false,
      verificationStatus = "",
      sources = emptyList()
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          com.example.ui.components.AnswerEngineResultCard(result = sampleResult)
        }
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("answer_engine_listen_button")
      .performScrollTo()
      .assertTextContains("Listen")
      .performClick()
    composeTestRule.waitForIdle()

    val spokenText = com.example.util.TtsManager.tts?.let { Shadows.shadowOf(it).lastSpokenText }.orEmpty()
    assertTrue("Expected native TextToSpeech to receive response, got '$spokenText'", spokenText.contains("Osmosis"))
    assertTrue(spokenText.contains("semipermeable membrane"))

    // Verify Stop button & active TTS status indicator when speaking is active, then toggle Stop
    if (com.example.util.TtsManager.isSpeaking.value) {
      composeTestRule.onNodeWithTag("answer_engine_listen_button").assertTextContains("Stop")
      composeTestRule.onNodeWithTag("answer_engine_tts_status").assertIsDisplayed()
      composeTestRule.onNodeWithTag("answer_engine_listen_button").performClick()
      composeTestRule.waitForIdle()
    }
    assertFalse(com.example.util.TtsManager.isSpeaking.value)
    composeTestRule.onNodeWithTag("answer_engine_listen_button").assertTextContains("Listen")

    com.example.util.TtsManager.shutdown()
  }

  @Test
  fun `answerEngineScreen exports saved Room history list to shareable TXT and PDF files via Intent`() = kotlinx.coroutines.runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val shadowApp = Shadows.shadowOf(app)
    shadowApp.clearNextStartedActivities()

    val exportsDir = java.io.File(app.cacheDir, "exports")
    exportsDir.deleteRecursively()

    val repo = ListeningStateManager.getRepository(app)
    repo.clearAll()
    repo.saveDoubt(
      DoubtItem(
        timestamp = System.currentTimeMillis() - 120_000L,
        doubtSnippet = "Why does ice float on liquid water?",
        subject = "Chemistry",
        topic = "Hydrogen Bonding",
        quickAnswer = "Hydrogen bonds lock ice into an open crystal lattice that is less dense than liquid water.",
        detailedAnswer = "Ice density is ~0.917 g/cm³ versus 1.0 g/cm³ for liquid water at 0 °C.",
        notificationSummary = "Ice forms a less-dense hydrogen-bonded lattice."
      )
    )
    repo.saveDoubt(
      DoubtItem(
        timestamp = System.currentTimeMillis() - 30_000L,
        doubtSnippet = "What is the time complexity of merge sort?",
        subject = "Computer Science",
        topic = "Merge Sort",
        quickAnswer = "Merge sort runs in O(n log n) worst-case, average, and best-case time.",
        detailedAnswer = "Splitting takes log2(n) levels and linear O(n) merging occurs at each level.",
        notificationSummary = "O(n log n) guaranteed."
      )
    )

    val viewModel = com.example.ui.viewmodel.AnswerEngineViewModel(app)
    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.screens.AnswerEngineScreen(viewModel = viewModel)
      }
    }

    composeTestRule.waitUntil(timeoutMillis = 8_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      viewModel.recentAnswers.value.size >= 2
    }

    composeTestRule.onNodeWithTag("answer_engine_screen")
      .performScrollToNode(hasTestTag("answer_engine_export_button"))
    composeTestRule.onNodeWithTag("answer_engine_export_button").assertIsDisplayed()

    // 1. Export as Shareable Text (.txt) via Intent
    composeTestRule.onNodeWithTag("answer_engine_export_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("answer_engine_export_txt").performClick()
    composeTestRule.waitForIdle()

    val txtChooser = shadowApp.nextStartedActivity
    assertNotNull("Expected Intent chooser for TXT export", txtChooser)
    assertEquals(android.content.Intent.ACTION_CHOOSER, txtChooser.action)
    val txtSendIntent = androidx.core.content.IntentCompat.getParcelableExtra(
      txtChooser,
      android.content.Intent.EXTRA_INTENT,
      android.content.Intent::class.java
    )
    assertNotNull(txtSendIntent)
    assertEquals(android.content.Intent.ACTION_SEND, txtSendIntent!!.action)
    assertEquals("text/plain", txtSendIntent.type)
    assertTrue((txtSendIntent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    val txtStreamUri = androidx.core.content.IntentCompat.getParcelableExtra(
      txtSendIntent,
      android.content.Intent.EXTRA_STREAM,
      android.net.Uri::class.java
    )
    assertNotNull(txtStreamUri)
    assertEquals("content", txtStreamUri!!.scheme)
    assertEquals("${app.packageName}.fileprovider", txtStreamUri.authority)

    val txtFiles = exportsDir.listFiles { _, name -> name.endsWith(".txt") } ?: emptyArray()
    assertTrue("Expected generated .txt file in cacheDir/exports", txtFiles.isNotEmpty())
    val txtContent = txtFiles.first().readText(Charsets.UTF_8)
    assertTrue(txtContent.contains("Why does ice float on liquid water?"))
    assertTrue(txtContent.contains("What is the time complexity of merge sort?"))
    assertTrue(txtContent.contains("O(n log n)"))

    // 2. Export as Shareable PDF (.pdf) via Intent
    shadowApp.clearNextStartedActivities()
    composeTestRule.onNodeWithTag("answer_engine_export_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("answer_engine_export_pdf").performClick()
    composeTestRule.waitForIdle()

    val pdfChooser = shadowApp.nextStartedActivity
    assertNotNull("Expected Intent chooser for PDF export", pdfChooser)
    assertEquals(android.content.Intent.ACTION_CHOOSER, pdfChooser.action)
    val pdfSendIntent = androidx.core.content.IntentCompat.getParcelableExtra(
      pdfChooser,
      android.content.Intent.EXTRA_INTENT,
      android.content.Intent::class.java
    )
    assertNotNull(pdfSendIntent)
    assertEquals(android.content.Intent.ACTION_SEND, pdfSendIntent!!.action)
    assertEquals("application/pdf", pdfSendIntent.type)
    assertTrue((pdfSendIntent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    val pdfStreamUri = androidx.core.content.IntentCompat.getParcelableExtra(
      pdfSendIntent,
      android.content.Intent.EXTRA_STREAM,
      android.net.Uri::class.java
    )
    assertNotNull(pdfStreamUri)
    assertEquals("content", pdfStreamUri!!.scheme)
    assertEquals("${app.packageName}.fileprovider", pdfStreamUri.authority)

    val pdfFiles = exportsDir.listFiles { _, name -> name.endsWith(".pdf") } ?: emptyArray()
    assertTrue("Expected generated .pdf file in cacheDir/exports", pdfFiles.isNotEmpty())
    assertTrue("Expected non-empty PDF file", pdfFiles.first().length() > 100L)
    val pdfHeader = pdfFiles.first().readBytes().take(4).toByteArray().toString(Charsets.US_ASCII)
    assertEquals("%PDF", pdfHeader)
  }

  @Test
  fun `answerEngineScreen supports swipe-to-delete and long-press delete confirmation on saved history items`() = kotlinx.coroutines.runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val repo = ListeningStateManager.getRepository(app)
    repo.clearAll()

    val swipeItemId = repo.saveDoubt(
      DoubtItem(
        timestamp = System.currentTimeMillis() - 120_000L,
        doubtSnippet = "What is Hooke's law?",
        subject = "Physics",
        topic = "Elasticity",
        quickAnswer = "Restoring spring force is proportional to displacement: F = -kx.",
        detailedAnswer = "Valid within the elastic limit of linear springs.",
        notificationSummary = "F = -kx"
      )
    )
    val longPressItemId = repo.saveDoubt(
      DoubtItem(
        timestamp = System.currentTimeMillis() - 30_000L,
        doubtSnippet = "What is Snell's law?",
        subject = "Physics",
        topic = "Refraction",
        quickAnswer = "n1 * sin(theta1) = n2 * sin(theta2) relates incident and refracted angles.",
        detailedAnswer = "Light bends toward the normal when entering a higher-index medium.",
        notificationSummary = "n1 sin(theta1) = n2 sin(theta2)"
      )
    )

    val viewModel = com.example.ui.viewmodel.AnswerEngineViewModel(app)
    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.screens.AnswerEngineScreen(viewModel = viewModel)
      }
    }

    composeTestRule.waitUntil(timeoutMillis = 8_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      viewModel.recentAnswers.value.size == 2
    }

    // 1. Long-press delete confirmation on longPressItemId
    composeTestRule.onNodeWithTag("answer_engine_screen")
      .performScrollToNode(hasTestTag("answer_history_item_$longPressItemId"))
    composeTestRule.onNodeWithTag("answer_history_item_$longPressItemId")
      .performTouchInput { longClick() }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("confirm_delete_history_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("confirm_delete_history_button").performClick()

    composeTestRule.waitUntil(timeoutMillis = 8_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      viewModel.recentAnswers.value.none { it.doubtId == longPressItemId }
    }
    assertTrue(viewModel.recentAnswers.value.none { it.doubtId == longPressItemId })
    assertEquals(1, repo.getAllDoubtsOnce().size)

    // 2. Swipe-left to delete on swipeItemId
    composeTestRule.onNodeWithTag("answer_engine_screen")
      .performScrollToNode(hasTestTag("answer_history_item_$swipeItemId"))
    composeTestRule.onNodeWithTag("answer_history_item_$swipeItemId")
      .performTouchInput { swipeLeft() }

    composeTestRule.waitUntil(timeoutMillis = 8_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      viewModel.recentAnswers.value.none { it.doubtId == swipeItemId }
    }
    assertTrue(viewModel.recentAnswers.value.isEmpty())
    assertTrue(repo.getAllDoubtsOnce().isEmpty())
  }

  @Test
  fun `historyScreen supports long-press delete confirmation on saved session logs`() = kotlinx.coroutines.runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val repo = ListeningStateManager.getRepository(app)
    repo.clearAll()

    val sessionId = repo.saveSession(
      com.example.data.model.ConversationSession(
        startTime = System.currentTimeMillis() - 60_000L,
        endTime = System.currentTimeMillis(),
        title = "Linear Algebra Review",
        category = "Mathematics",
        transcriptPreview = "How do eigenvalues relate to eigenvectors?"
      )
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.screens.HistoryScreen()
      }
    }

    composeTestRule.waitUntil(timeoutMillis = 8_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      try {
        composeTestRule.onNodeWithTag("session_card_$sessionId").assertExists()
        true
      } catch (_: AssertionError) {
        false
      }
    }

    composeTestRule.onNodeWithTag("session_card_$sessionId")
      .performTouchInput { longClick() }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("confirm_delete_log_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("confirm_delete_log_button").performClick()

    composeTestRule.waitUntil(timeoutMillis = 8_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      try {
        composeTestRule.onNodeWithTag("session_card_$sessionId").assertDoesNotExist()
        true
      } catch (_: AssertionError) {
        false
      }
    }
    assertTrue(repo.getAllSessionsOnce().isEmpty())
  }

  @Test
  fun `doubtRepository deleteOlderThanDays removes expired records and keeps recent ones`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(
      context,
      com.example.data.local.DoubtDatabase::class.java
    ).allowMainThreadQueries().build()

    try {
      val repo = com.example.data.repository.DoubtRepository(db.doubtDao(), db.conversationSessionDao())
      val now = System.currentTimeMillis()
      val day = 86_400_000L

      val oldId = repo.saveDoubt(
        DoubtItem(
          timestamp = now - (45L * day),
          doubtSnippet = "Old question from 45 days ago",
          topic = "Old Topic",
          quickAnswer = "Old quick",
          detailedAnswer = "Old detailed",
          notificationSummary = "Old"
        )
      )
      val recentId = repo.saveDoubt(
        DoubtItem(
          timestamp = now - (5L * day),
          doubtSnippet = "Recent question from 5 days ago",
          topic = "Recent Topic",
          quickAnswer = "Recent quick",
          detailedAnswer = "Recent detailed",
          notificationSummary = "Recent"
        )
      )

      val deletedCount = repo.deleteOlderThanDays(days = 30, nowMillis = now)
      assertEquals(1, deletedCount)
      val remaining = repo.getAllDoubtsOnce()
      assertEquals(1, remaining.size)
      assertEquals(recentId, remaining.first().id)
      assertFalse(remaining.any { it.id == oldId })
    } finally {
      db.close()
    }
  }

  @Test
  fun `offlineResponseCache retains at most 10 most recent responses and matches semantically`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    com.example.data.local.OfflineResponseCache.clearCache(app)

    for (i in 1..12) {
      com.example.data.local.OfflineResponseCache.cacheResult(
        app,
        com.example.ai.AnswerEngineResult(
          doubtId = i.toLong(),
          query = if (i == 11) "Explain quicksort sorting runtime" else "Cached question number $i",
          subject = if (i == 11) "Computer Science" else "Science",
          topic = if (i == 11) "Quicksort" else "Topic $i",
          quickAnswer = if (i == 11) "Average sorting time complexity is O(n log n)." else "Quick answer $i",
          detailedAnswer = "Detailed $i",
          keyConcepts = listOf("Concept $i"),
          confidence = 0.90f,
          groundingLabel = "Offline Cache",
          isFactChecked = false,
          verificationStatus = "",
          sources = emptyList()
        )
      )
    }

    val cached = com.example.data.local.OfflineResponseCache.getCachedResults(app)
    assertEquals(10, cached.size)
    assertEquals("Cached question number 12", cached.first().query)
    assertFalse(cached.any { it.doubtId == 1L || it.doubtId == 2L })

    val semanticHit = com.example.data.local.OfflineResponseCache.findBestCachedMatch(
      app,
      "fast sorting algorithm performance"
    )
    assertNotNull(semanticHit)
    assertEquals("Quicksort", semanticHit!!.topic)

    com.example.data.local.OfflineResponseCache.clearCache(app)
    assertTrue(com.example.data.local.OfflineResponseCache.getCachedResults(app).isEmpty())
  }

  @Test
  fun `shortcutsXml and CalendarHelper produce valid shortcuts and Calendar insert intent`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val parser = app.resources.getXml(R.xml.shortcuts)
    val shortcutActions = mutableListOf<String>()
    var eventType = parser.eventType
    while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
      if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "extra") {
        val value = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "value")
        if (!value.isNullOrBlank()) shortcutActions.add(value)
      }
      eventType = parser.next()
    }
    assertTrue(shortcutActions.contains(MainActivity.ACTION_SHORTCUT_NEW_DOUBT))
    assertTrue(shortcutActions.contains(MainActivity.ACTION_SHORTCUT_VIEW_HISTORY))
    assertTrue(shortcutActions.contains(MainActivity.ACTION_SHORTCUT_OPEN_SETTINGS))

    val calendarIntent = com.example.util.CalendarHelper.buildScheduleReviewIntent(
      title = "Quicksort",
      description = "Review pivot complexity"
    )
    assertEquals(android.content.Intent.ACTION_INSERT, calendarIntent.action)
    assertEquals(android.provider.CalendarContract.Events.CONTENT_URI, calendarIntent.data)
    assertTrue(
      calendarIntent.getStringExtra(android.provider.CalendarContract.Events.TITLE)
        ?.contains("Quicksort") == true
    )
  }

  @Test
  fun `weeklyResolvedDoubtsChart renders bars and selected day summary in Compose`() {
    val now = System.currentTimeMillis()
    val doubts = listOf(
      DoubtItem(
        id = 1L,
        timestamp = now - 10_000L,
        doubtSnippet = "What is entropy?",
        subject = "Physics",
        topic = "Thermodynamics",
        quickAnswer = "Measure of microstate multiplicity.",
        detailedAnswer = "Detailed",
        notificationSummary = "Entropy"
      ),
      DoubtItem(
        id = 2L,
        timestamp = now - (2L * 86_400_000L),
        doubtSnippet = "What is Bayes theorem?",
        subject = "Mathematics",
        topic = "Probability",
        quickAnswer = "Updates prior belief using likelihood.",
        detailedAnswer = "Detailed",
        notificationSummary = "Bayes"
      )
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          com.example.ui.components.WeeklyResolvedDoubtsChart(doubts = doubts)
        }
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("weekly_resolved_bar_chart").assertIsDisplayed()
    composeTestRule.onNodeWithTag("weekly_bar_chart_canvas").assertIsDisplayed()
    composeTestRule.onNodeWithTag("weekly_bar_selected_summary")
      .assertTextContains("1 doubt(s) resolved", substring = true)
    composeTestRule.onNodeWithText("2 this week").assertIsDisplayed()
  }

  @Test
  fun `settingsScreen theme listening capture STT language offline toggles and CSV export update prefs and show Toast`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    app.getSharedPreferences("doubtsense_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    com.example.util.AppPreferencesManager.init(app)

    val repo = ListeningStateManager.getRepository(app)
    kotlinx.coroutines.runBlocking {
      repo.clearAll()
      repo.saveDoubt(
        DoubtItem(
          doubtSnippet = "Explain HTTP/3",
          subject = "Networking",
          topic = "HTTP/3",
          quickAnswer = "Runs over QUIC/UDP.",
          detailedAnswer = "Avoids TCP head-of-line blocking.",
          notificationSummary = "HTTP/3 uses QUIC"
        )
      )
    }

    var hostActivity: android.app.Activity? = null
    composeTestRule.setContent {
      hostActivity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
      MyApplicationTheme {
        SettingsScreen()
      }
    }
    composeTestRule.waitForIdle()

    // Theme picker -> High Contrast
    composeTestRule.onNodeWithTag("settings_list")
      .performScrollToNode(hasTestTag("theme_option_HIGH_CONTRAST"))
    composeTestRule.onNodeWithTag("theme_option_HIGH_CONTRAST").performClick()
    composeTestRule.waitForIdle()
    assertEquals(com.example.util.ThemeMode.HIGH_CONTRAST, com.example.util.AppPreferencesManager.themeMode.value)

    // Listening capture mode -> Continuous Listening
    composeTestRule.onNodeWithTag("settings_list")
      .performScrollToNode(hasTestTag("listening_mode_CONTINUOUS_LISTENING"))
    composeTestRule.onNodeWithTag("listening_mode_CONTINUOUS_LISTENING").performClick()
    composeTestRule.waitForIdle()
    assertEquals(
      com.example.util.ListeningCaptureMode.CONTINUOUS_LISTENING,
      com.example.util.AppPreferencesManager.listeningCaptureMode.value
    )

    // STT language selector -> Spanish
    composeTestRule.onNodeWithTag("settings_list")
      .performScrollToNode(hasTestTag("stt_language_dropdown"))
    composeTestRule.onNodeWithTag("stt_language_dropdown").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("stt_lang_option_es-ES").performClick()
    composeTestRule.waitForIdle()
    assertEquals("es-ES", com.example.util.AppPreferencesManager.sttLanguageTag.value)

    // TTS Voice Pitch slider in Settings
    composeTestRule.onNodeWithTag("settings_list")
      .performScrollToNode(hasTestTag("tts_speech_pitch_slider"))
    composeTestRule.onNodeWithTag("tts_speech_pitch_slider").assertIsDisplayed()
    composeTestRule.onNodeWithTag("tts_speech_pitch_slider")
      .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(1.5f) }
    composeTestRule.waitForIdle()
    assertEquals(1.5f, com.example.util.AppPreferencesManager.ttsSpeechPitch.value, 0.05f)
    com.example.util.AppPreferencesManager.setTtsSpeechPitch(app, 1.0f)

    // Battery saver, Offline mode, 30-day auto-cleanup switches
    composeTestRule.onNodeWithTag("settings_list")
      .performScrollToNode(hasTestTag("battery_saver_switch"))
    composeTestRule.onNodeWithTag("battery_saver_switch").performClick()
    composeTestRule.onNodeWithTag("settings_list")
      .performScrollToNode(hasTestTag("offline_mode_switch"))
    composeTestRule.onNodeWithTag("offline_mode_switch").performClick()
    composeTestRule.onNodeWithTag("settings_list")
      .performScrollToNode(hasTestTag("auto_cleanup_switch"))
    composeTestRule.onNodeWithTag("auto_cleanup_switch").performClick()
    composeTestRule.waitForIdle()

    assertTrue(com.example.util.AppPreferencesManager.batterySaverMode.value)
    assertTrue(com.example.util.AppPreferencesManager.offlineMode.value)
    assertTrue(com.example.util.AppPreferencesManager.autoCleanup30Days.value)

    // Cloud Sync card present
    composeTestRule.onNodeWithTag("settings_list")
      .performScrollToNode(hasTestTag("cloud_sync_card"))
    composeTestRule.onNodeWithTag("cloud_sync_card").assertIsDisplayed()

    // CSV export triggers share chooser and Toast confirmation
    val exportsDir = java.io.File(app.cacheDir, "exports")
    exportsDir.deleteRecursively()
    Shadows.shadowOf(app).clearNextStartedActivities()
    hostActivity?.let { Shadows.shadowOf(it).clearNextStartedActivities() }
    org.robolectric.shadows.ShadowToast.reset()
    composeTestRule.onNodeWithTag("settings_list")
      .performScrollToNode(hasTestTag("settings_export_csv_button"))
    composeTestRule.onNodeWithTag("settings_export_csv_button")
      .performScrollTo()
      .performClick()
    composeTestRule.waitUntil(timeoutMillis = 8_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      org.robolectric.shadows.ShadowToast.getTextOfLatestToast() != null ||
        Shadows.shadowOf(app).peekNextStartedActivity() != null ||
        hostActivity?.let { Shadows.shadowOf(it).peekNextStartedActivity() } != null
    }

    val toastText = org.robolectric.shadows.ShadowToast.getTextOfLatestToast()
    assertTrue(
      "Expected export ready CSV toast confirmation, got '$toastText'",
      toastText?.contains("ready", ignoreCase = true) == true &&
        toastText.contains("CSV", ignoreCase = true)
    )
    val csvChooser = Shadows.shadowOf(app).nextStartedActivity
      ?: hostActivity?.let { Shadows.shadowOf(it).nextStartedActivity }
    assertNotNull("Expected chooser Intent after Settings CSV export (toast='$toastText')", csvChooser)
    val csvFiles = exportsDir.listFiles { _, name -> name.endsWith(".csv") } ?: emptyArray()
    assertTrue("Expected generated .csv file in cacheDir/exports", csvFiles.isNotEmpty())
    assertTrue(csvFiles.first().readText(Charsets.UTF_8).contains("Explain HTTP/3"))
  }

  @Test
  fun `historyScreen pins favorites displays confidence badge filters 7d and tags and exports CSV`() = kotlinx.coroutines.runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val repo = ListeningStateManager.getRepository(app)
    repo.clearAll()

    val now = System.currentTimeMillis()
    val workId = repo.saveDoubt(
      DoubtItem(
        timestamp = now - 60_000L,
        doubtSnippet = "Why does PostgreSQL vacuum matter?",
        subject = "Databases",
        topic = "PostgreSQL Vacuum",
        quickAnswer = "Reclaims dead tuple storage and prevents transaction ID wraparound.",
        detailedAnswer = "MVCC marks old row versions dead until vacuum cleans them.",
        notificationSummary = "Reclaims MVCC dead tuples.",
        confidenceScore = 0.91f,
        customTag = "Work",
        isStarred = false
      )
    )
    val oldEduId = repo.saveDoubt(
      DoubtItem(
        timestamp = now - (14L * 86_400_000L),
        doubtSnippet = "How does mitosis divide chromosomes?",
        subject = "Biology",
        topic = "Mitosis",
        quickAnswer = "Produces two genetically identical daughter cells.",
        detailedAnswer = "Prophase, metaphase, anaphase, telophase.",
        notificationSummary = "Equal chromosome division.",
        confidenceScore = 0.87f,
        customTag = "Education",
        isStarred = false
      )
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.screens.HistoryScreen()
      }
    }

    composeTestRule.onNodeWithTag("tab_doubts").performClick()
    composeTestRule.waitUntil(timeoutMillis = 8_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      try {
        composeTestRule.onNodeWithTag("doubt_card_$workId").assertExists()
        true
      } catch (_: AssertionError) {
        false
      }
    }

    // Confidence badge displayed alongside question
    composeTestRule.onNodeWithTag("doubt_confidence_badge_$workId")
      .assertTextContains("91% clear")

    // Favorite pinning persists to Room
    composeTestRule.onNodeWithTag("favorite_button_$workId").performClick()
    composeTestRule.waitUntil(timeoutMillis = 6_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      kotlinx.coroutines.runBlocking { repo.getDoubtById(workId)?.isStarred == true }
    }

    // Filter by Recent (7d) excludes 14-day-old Education doubt
    composeTestRule.onNodeWithTag("date_chip_last_7_days").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("doubt_card_$workId").assertIsDisplayed()
    composeTestRule.onNodeWithTag("doubt_card_$oldEduId").assertDoesNotExist()

    // Filter by Work tag keeps Work item
    composeTestRule.onNodeWithTag("history_tag_chip_Work").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("doubt_card_$workId").assertIsDisplayed()

    // Export CSV from History screen
    Shadows.shadowOf(app).clearNextStartedActivities()
    composeTestRule.onNodeWithTag("export_history_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("history_export_csv").performClick()
    composeTestRule.waitForIdle()
    assertNotNull(Shadows.shadowOf(app).nextStartedActivity)
  }

  @Test
  fun `answerEngineScreen renders confidence badge floating language toggle smart replies followUp and calendar schedule`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
    app.getSharedPreferences("doubtsense_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    com.example.util.AppPreferencesManager.init(app)

    val testEngine = com.example.ai.AnswerEngine(
      localAnalysisProvider = { query, ctx, factCheck ->
        com.example.ai.OfflineKnowledgeBase.matchBuiltInKnowledge(query, ctx, factCheck)
          ?: com.example.ai.ContextAnalysisResult(
            isDoubt = true,
            subject = "Physics",
            topic = "Thermodynamics",
            intent = "Understand convective buoyancy",
            importantConcepts = listOf("Buoyancy", "Density"),
            doubtType = "Working principle",
            doubtSnippet = query,
            quickAnswer = "Warm air expands and rises.",
            detailedAnswer = "Lower density causes upward buoyant force.",
            notificationSummary = "Warm air rises.",
            isAmbiguous = false,
            clarificationQuestion = null,
            suggestedOptions = emptyList(),
            referenceMaterials = "",
            conversationSuggestions = "",
            confidence = 0.90f
          )
      }
    )
    val viewModel = com.example.ui.viewmodel.AnswerEngineViewModel(app, testEngine)

    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.screens.AnswerEngineScreen(viewModel = viewModel)
      }
    }
    composeTestRule.waitForIdle()

    // Floating language button at TopEnd switches AI response language to Spanish
    composeTestRule.onNodeWithTag("floating_language_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("lang_option_ES").performClick()
    composeTestRule.waitForIdle()
    assertEquals("Spanish", com.example.util.AppPreferencesManager.aiResponseLanguage.value)

    // Quick-questions carousel exists
    composeTestRule.onNodeWithTag("quick_questions_carousel").performScrollTo().assertIsDisplayed()

    // Submit query and inspect Doubt Confidence Score badge, Smart Replies, Follow-up, Schedule
    composeTestRule.onNodeWithTag("answer_engine_query_input")
      .performScrollTo()
      .performTextInput("Why does warm air rise?")
    composeTestRule.onNodeWithTag("answer_engine_ask_button")
      .performScrollTo()
      .performClick()

    composeTestRule.waitUntil(timeoutMillis = 10_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      viewModel.uiState.value is com.example.ui.viewmodel.AnswerEngineUiState.Success
    }

    composeTestRule.onNodeWithTag("answer_engine_screen")
      .performScrollToNode(hasTestTag("answer_engine_result_card"))
    composeTestRule.onNodeWithTag("answer_engine_confidence_badge")
      .performScrollTo()
      .assertIsDisplayed()
      .assertTextContains("%", substring = true)

    composeTestRule.onNodeWithTag("smart_replies_row")
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule.onNodeWithTag("smart_reply_0").assertIsDisplayed()

    // Tap Schedule -> verifies Android Calendar ACTION_INSERT intent
    Shadows.shadowOf(app).clearNextStartedActivities()
    composeTestRule.onNodeWithTag("answer_engine_schedule_button")
      .performScrollTo()
      .performClick()
    composeTestRule.waitForIdle()
    val calIntent = Shadows.shadowOf(app).nextStartedActivity
    assertNotNull("Expected Calendar ACTION_INSERT intent", calIntent)
    assertEquals(android.content.Intent.ACTION_INSERT, calIntent.action)
    assertEquals(android.provider.CalendarContract.Events.CONTENT_URI, calIntent.data)

    // Tap Follow-up voice action -> attaches follow-up context banner
    composeTestRule.onNodeWithTag("answer_engine_followup_button")
      .performScrollTo()
      .performClick()
    composeTestRule.waitForIdle()
    assertTrue(viewModel.followUpParentContext.value.isNotEmpty())
    viewModel.cancelMicrophoneCapture()
  }

  @Test
  fun `answerEngineResultCard ttsControlPanel supports pause resume speed presets and pitch slider`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    app.getSharedPreferences("doubtsense_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    com.example.util.AppPreferencesManager.init(app)
    com.example.util.TtsManager.shutdown()
    com.example.util.TtsManager.init(app)
    composeTestRule.waitForIdle()
    com.example.util.TtsManager.onInit(android.speech.tts.TextToSpeech.SUCCESS)
    // Detach ShadowTextToSpeech's immediate synchronous onDone so mid-utterance Pause/Resume UI stays active
    com.example.util.TtsManager.tts?.setOnUtteranceProgressListener(null)

    val sampleResult = com.example.ai.AnswerEngineResult(
      doubtId = 108L,
      query = "How does Doppler effect shift frequency?",
      subject = "Physics",
      topic = "Waves & Acoustics",
      quickAnswer = "Relative motion compresses or stretches wavefronts, shifting observed frequency.",
      detailedAnswer = "Approaching sources produce higher observed pitch while receding sources lower frequency.",
      keyConcepts = listOf("Doppler Effect", "Wavelength"),
      confidence = 0.94f,
      groundingLabel = "Verified AI Engine",
      isFactChecked = false,
      verificationStatus = "",
      sources = emptyList()
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          com.example.ui.components.AnswerEngineResultCard(result = sampleResult)
        }
      }
    }
    composeTestRule.waitForIdle()

    // Panel and 4 speed chips are visible on the answer card
    composeTestRule.onNodeWithTag("answer_engine_tts_control_panel").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("answer_engine_tts_speed_0_5x").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("answer_engine_tts_speed_1_0x").assertIsDisplayed()
    composeTestRule.onNodeWithTag("answer_engine_tts_speed_1_5x").assertIsDisplayed()
    composeTestRule.onNodeWithTag("answer_engine_tts_speed_2_0x").assertIsDisplayed()

    // Start playback via Play button inside TTS panel
    composeTestRule.onNodeWithTag("answer_engine_tts_pause_resume_button")
      .performScrollTo()
      .assertTextContains("Play")
      .performClick()
    composeTestRule.waitForIdle()

    assertTrue(com.example.util.TtsManager.isSpeaking.value)
    assertFalse(com.example.util.TtsManager.isPaused.value)
    composeTestRule.onNodeWithTag("answer_engine_tts_pause_resume_button").assertTextContains("Pause")
    composeTestRule.onNodeWithTag("answer_engine_tts_stop_button").assertIsDisplayed()

    // Simulate active spoken word progress at "wavefronts" before pausing
    val fullSpoken = com.example.util.TtsManager.spokenText.value
    val waveStart = fullSpoken.indexOf("wavefronts")
    assertTrue(waveStart > 0)
    com.example.util.TtsManager.utteranceProgressListener?.onRangeStart(
      com.example.util.TtsManager.currentUtteranceId,
      waveStart,
      waveStart + "wavefronts".length,
      0
    )
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("answer_engine_tts_status")
      .assertIsDisplayed()
      .assertTextContains("wavefronts", substring = true, ignoreCase = true)

    // Pause playback
    composeTestRule.onNodeWithTag("answer_engine_tts_pause_resume_button").performClick()
    composeTestRule.waitForIdle()

    assertFalse(com.example.util.TtsManager.isSpeaking.value)
    assertTrue(com.example.util.TtsManager.isPaused.value)
    composeTestRule.onNodeWithTag("answer_engine_tts_pause_resume_button").assertTextContains("Resume")
    composeTestRule.onNodeWithTag("answer_engine_tts_status")
      .assertIsDisplayed()
      .assertTextContains("Paused", substring = true, ignoreCase = true)

    // Resume playback -> must continue from "wavefronts..." rather than restarting at word 0
    composeTestRule.onNodeWithTag("answer_engine_tts_pause_resume_button").performClick()
    composeTestRule.waitForIdle()

    assertTrue(com.example.util.TtsManager.isSpeaking.value)
    assertFalse(com.example.util.TtsManager.isPaused.value)
    composeTestRule.onNodeWithTag("answer_engine_tts_pause_resume_button").assertTextContains("Pause")
    val resumedSpoken = com.example.util.TtsManager.tts?.let { Shadows.shadowOf(it).lastSpokenText }.orEmpty()
    assertTrue("Expected resume to start at 'wavefronts', got '$resumedSpoken'", resumedSpoken.startsWith("wavefronts"))

    // Switch playback speeds (0.5x -> 1.5x -> 2.0x)
    composeTestRule.onNodeWithTag("answer_engine_tts_speed_0_5x").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(0.5f, com.example.util.AppPreferencesManager.ttsSpeechRate.value, 0.01f)
    assertEquals(0.5f, com.example.util.TtsManager.getSpeechRate(), 0.01f)

    composeTestRule.onNodeWithTag("answer_engine_tts_speed_1_5x").performClick()
    composeTestRule.waitForIdle()
    assertEquals(1.5f, com.example.util.AppPreferencesManager.ttsSpeechRate.value, 0.01f)
    assertEquals(1.5f, com.example.util.TtsManager.getSpeechRate(), 0.01f)

    composeTestRule.onNodeWithTag("answer_engine_tts_speed_2_0x").performClick()
    composeTestRule.waitForIdle()
    assertEquals(2.0f, com.example.util.AppPreferencesManager.ttsSpeechRate.value, 0.01f)

    // Adjust voice pitch via slider
    composeTestRule.onNodeWithTag("answer_engine_tts_pitch_slider")
      .performScrollTo()
      .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(1.5f) }
    composeTestRule.waitForIdle()
    assertEquals(1.5f, com.example.util.TtsManager.getSpeechPitch(), 0.05f)
    assertEquals(1.5f, com.example.util.AppPreferencesManager.ttsSpeechPitch.value, 0.05f)

    // Reset button restores 1.0x speed and 1.0x pitch
    composeTestRule.onNodeWithTag("answer_engine_tts_reset_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(1.0f, com.example.util.TtsManager.getSpeechRate(), 0.01f)
    assertEquals(1.0f, com.example.util.TtsManager.getSpeechPitch(), 0.01f)

    // Stop playback completely
    composeTestRule.onNodeWithTag("answer_engine_tts_stop_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertFalse(com.example.util.TtsManager.isSpeaking.value)
    assertFalse(com.example.util.TtsManager.isPaused.value)

    com.example.util.TtsManager.shutdown()
  }

  @Test
  fun `doubtCard expands ttsControlPanel and supports pause resume speed and pitch`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    app.getSharedPreferences("doubtsense_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    com.example.util.AppPreferencesManager.init(app)
    com.example.util.TtsManager.shutdown()
    com.example.util.TtsManager.init(app)
    composeTestRule.waitForIdle()
    com.example.util.TtsManager.onInit(android.speech.tts.TextToSpeech.SUCCESS)
    com.example.util.TtsManager.tts?.setOnUtteranceProgressListener(null)

    val doubt = DoubtItem(
      id = 77L,
      doubtSnippet = "Why is the sky blue?",
      topic = "Physics: Rayleigh Scattering",
      quickAnswer = "Shorter blue wavelengths scatter more strongly across atmospheric molecules.",
      detailedAnswer = "Rayleigh scattering scales inversely with the fourth power of wavelength.",
      notificationSummary = "Short blue wavelengths scatter more."
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          DoubtCard(doubt = doubt)
        }
      }
    }
    composeTestRule.waitForIdle()

    // Toggle voice speed & pitch panel open
    composeTestRule.onNodeWithTag("tts_controls_toggle_77").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("doubt_77_tts_control_panel").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("doubt_77_tts_speed_1_5x").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(1.5f, com.example.util.TtsManager.getSpeechRate(), 0.01f)

    // Play -> Pause -> Stop
    composeTestRule.onNodeWithTag("doubt_77_tts_pause_resume_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertTrue(com.example.util.TtsManager.isSpeaking.value)

    composeTestRule.onNodeWithTag("doubt_77_tts_pause_resume_button").performClick()
    composeTestRule.waitForIdle()
    assertTrue(com.example.util.TtsManager.isPaused.value)

    composeTestRule.onNodeWithTag("doubt_77_tts_stop_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertFalse(com.example.util.TtsManager.isSpeaking.value)
    assertFalse(com.example.util.TtsManager.isPaused.value)

    com.example.util.TtsManager.shutdown()
  }

  @Test
  fun `historyScreen stores sessions opens full AI answers with quick detailed toggle and revisits in AnswerEngine`() = kotlinx.coroutines.runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    app.getSharedPreferences("doubtsense_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    com.example.util.AppPreferencesManager.init(app)
    com.example.util.TtsManager.shutdown()
    com.example.util.TtsManager.init(app)
    com.example.util.TtsManager.onInit(android.speech.tts.TextToSpeech.SUCCESS)
    com.example.util.TtsManager.tts?.setOnUtteranceProgressListener(null)

    val repo = ListeningStateManager.getRepository(app)
    repo.clearAll()

    val now = System.currentTimeMillis()
    val sessionId = repo.saveSession(
      com.example.data.model.ConversationSession(
        startTime = now - 90_000L,
        endTime = now,
        title = "Distributed Caching Architecture",
        category = "Computer Science",
        customTag = "Education",
        transcriptJson = com.example.data.model.ConversationSession.encodeTranscriptTurns(
          listOf(
            "Colleague: Let's pick a Redis caching pattern.",
            "Me: What is the difference between write-through and write-back caching?"
          )
        ),
        transcriptPreview = "What is the difference between write-through and write-back caching?",
        averageDoubtIntensity = 0.84f,
        peakDoubtIntensity = 0.91f,
        doubtCount = 1,
        summary = "Write-through updates cache and DB synchronously; write-back batches DB writes."
      )
    )
    val doubtId = repo.saveDoubt(
      DoubtItem(
        sessionId = sessionId,
        timestamp = now - 30_000L,
        doubtSnippet = "What is the difference between write-through and write-back caching?",
        subject = "Computer Science",
        topic = "Caching Strategies",
        importantConcepts = "Write-Through, Write-Back, Consistency",
        quickAnswer = "Write-through writes to cache and backing store together; write-back buffers writes asynchronously.",
        detailedAnswer = "Step 1: Write-through guarantees immediate consistency at higher write latency.\nStep 2: Write-back reduces latency via batching but requires dirty-flag durability.",
        notificationSummary = "Write-through is synchronous; write-back is asynchronous.",
        confidenceScore = 0.92f,
        customTag = "Education"
      )
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.DoubtSenseApp(initialDestination = com.example.ui.AppDestination.HISTORY)
      }
    }

    composeTestRule.waitUntil(timeoutMillis = 8_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      try {
        composeTestRule.onNodeWithTag("session_card_$sessionId").assertExists()
        true
      } catch (_: AssertionError) {
        false
      }
    }

    // Pin session from Sessions list and verify persistence
    composeTestRule.onNodeWithTag("pin_session_button_$sessionId").performClick()
    composeTestRule.waitUntil(timeoutMillis = 6_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      kotlinx.coroutines.runBlocking { repo.getSessionById(sessionId)?.isPinned == true }
    }

    // Open session detail to revisit full AI-generated answers
    composeTestRule.onNodeWithTag("sessions_lazy_column")
      .performTouchInput { swipeUp(startY = bottom * 0.8f, endY = top + 40f) }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("session_revisit_button_$sessionId").performClick()
    composeTestRule.waitUntil(timeoutMillis = 6_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      try {
        composeTestRule.onNodeWithTag("session_detail_dialog").assertIsDisplayed()
        true
      } catch (_: AssertionError) {
        false
      }
    }

    composeTestRule.onNodeWithTag("session_doubt_card_$doubtId").performScrollTo().assertExists()
    composeTestRule.onNodeWithTag("session_doubt_confidence_$doubtId").assertTextContains("92% clear")
    composeTestRule.onNodeWithTag("session_doubt_answer_text_$doubtId")
      .assertTextContains("Write-through writes to cache and backing store together", substring = true)

    // Switch to Detailed Answer inside the revisited session
    composeTestRule.onNodeWithTag("session_doubt_toggle_detailed_$doubtId").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("session_doubt_answer_text_$doubtId")
      .assertTextContains("dirty-flag durability", substring = true)

    // Expand inline TTS control panel inside session detail and test speed + playback
    composeTestRule.onNodeWithTag("session_doubt_tts_controls_toggle_$doubtId").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("session_doubt_${doubtId}_tts_control_panel").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("session_doubt_${doubtId}_tts_speed_1_5x").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(1.5f, com.example.util.TtsManager.getSpeechRate(), 0.01f)

    // Revisit the past AI answer directly inside the Answer Engine screen
    composeTestRule.onNodeWithTag("session_doubt_revisit_button_$doubtId").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 6_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      try {
        composeTestRule.onNodeWithTag("answer_engine_screen").assertExists()
        true
      } catch (_: AssertionError) {
        false
      }
    }

    composeTestRule.onNodeWithTag("answer_engine_screen")
      .performScrollToNode(hasTestTag("answer_engine_result_card"))
    composeTestRule.onNodeWithTag("answer_engine_result_card").assertIsDisplayed()
    com.example.util.TtsManager.shutdown()
  }

  @Test
  @Config(sdk = [34], qualifiers = "w411dp-h915dp")
  fun `writeSessionToUri exports structured text and pdf files and HistoryScreen launches SAF save intents`(): Unit = kotlinx.coroutines.runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    app.getSharedPreferences("doubtsense_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    com.example.util.AppPreferencesManager.init(app)

    val repo = ListeningStateManager.getRepository(app)
    repo.clearAll()

    val now = System.currentTimeMillis()
    val session = com.example.data.model.ConversationSession(
      startTime = now - 60_000L,
      endTime = now,
      title = "Quantum Tunneling Lecture",
      category = "Physics",
      customTag = "Education",
      transcriptJson = com.example.data.model.ConversationSession.encodeTranscriptTurns(
        listOf(
          "Professor: Particles can cross a potential barrier classically forbidden.",
          "Student: Why does barrier width decay the probability exponentially?"
        )
      ),
      transcriptPreview = "Why does barrier width decay the probability exponentially?",
      averageDoubtIntensity = 0.78f,
      peakDoubtIntensity = 0.89f,
      doubtCount = 1,
      summary = "Wavefunction amplitude decays exponentially inside a classically forbidden barrier."
    )
    val sessionId = repo.saveSession(session)
    val savedSession = repo.getSessionById(sessionId)!!
    val doubt = DoubtItem(
      sessionId = sessionId,
      timestamp = now - 10_000L,
      doubtSnippet = "Why does barrier width decay the probability exponentially?",
      subject = "Physics",
      topic = "Quantum Tunneling",
      quickAnswer = "Inside the barrier, the Schrodinger solution becomes a decaying real exponential.",
      detailedAnswer = "Transmission T scales roughly as exp(-2 * kappa * width), making barrier thickness critical.",
      notificationSummary = "Wavefunction decays exponentially across barriers.",
      confidenceScore = 0.91f,
      customTag = "Education"
    )
    repo.saveDoubt(doubt)

    // 1. Verify writeSessionToUri writes complete structured .txt and valid %PDF-1. .pdf files
    val outDir = java.io.File(app.cacheDir, "saf_test_out").apply {
      deleteRecursively()
      mkdirs()
    }
    val txtFile = java.io.File(outDir, "session_export.txt")
    val pdfFile = java.io.File(outDir, "session_export.pdf")

    val txtOk = com.example.util.DocumentExportHelper.writeSessionToUri(
      context = app,
      targetUri = android.net.Uri.fromFile(txtFile),
      session = savedSession,
      doubts = listOf(doubt),
      format = com.example.util.ExportDocumentFormat.STRUCTURED_TEXT
    )
    assertTrue("Expected writeSessionToUri TXT to succeed", txtOk)
    val txtText = txtFile.readText(Charsets.UTF_8)
    assertTrue(txtText.contains("Quantum Tunneling Lecture"))
    assertTrue(txtText.contains("Session Takeaway:"))
    assertTrue(txtText.contains("Why does barrier width decay the probability exponentially?"))
    assertTrue(txtText.contains("decaying real exponential"))

    val pdfOk = com.example.util.DocumentExportHelper.writeSessionToUri(
      context = app,
      targetUri = android.net.Uri.fromFile(pdfFile),
      session = savedSession,
      doubts = listOf(doubt),
      format = com.example.util.ExportDocumentFormat.PDF
    )
    assertTrue("Expected writeSessionToUri PDF to succeed", pdfOk)
    val pdfBytes = pdfFile.readBytes()
    assertTrue(pdfBytes.size > 100)
    assertTrue(String(pdfBytes.copyOfRange(0, 8), Charsets.US_ASCII).startsWith("%PDF-1."))

    // 2. Verify HistoryScreen Export menu launches SAF CreateDocument intents for .txt and .pdf
    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.DoubtSenseApp(initialDestination = com.example.ui.AppDestination.HISTORY)
      }
    }

    composeTestRule.waitUntil(timeoutMillis = 8_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      try {
        composeTestRule.onNodeWithTag("session_card_$sessionId").assertExists()
        true
      } catch (_: AssertionError) {
        false
      }
    }

    composeTestRule.onNodeWithTag("sessions_lazy_column")
      .performScrollToNode(hasTestTag("session_card_$sessionId"))
    composeTestRule.waitForIdle()

    val shadowApp = Shadows.shadowOf(app)
    val testActivity = (composeTestRule as? androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>)?.activity
    shadowApp.clearNextStartedActivities()
    testActivity?.let { Shadows.shadowOf(it).clearNextStartedActivities() }

    composeTestRule.onNodeWithTag("session_card_export_button_$sessionId").assertIsDisplayed().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      try {
        composeTestRule.onNodeWithTag("session_save_txt_$sessionId").assertExists()
        true
      } catch (_: AssertionError) {
        false
      }
    }
    composeTestRule.onNodeWithTag("session_save_txt_$sessionId").performClick()
    composeTestRule.waitForIdle()

    val createTxtIntent = shadowApp.nextStartedActivity
      ?: testActivity?.let { Shadows.shadowOf(it).nextStartedActivityForResult?.intent }
    assertNotNull("Expected ACTION_CREATE_DOCUMENT intent for session .txt save", createTxtIntent)
    assertEquals(android.content.Intent.ACTION_CREATE_DOCUMENT, createTxtIntent!!.action)
    assertEquals("text/plain", createTxtIntent.type)
    assertTrue(
      createTxtIntent.getStringExtra(android.content.Intent.EXTRA_TITLE)?.endsWith(".txt") == true
    )

    // Open SessionDetailDialog and trigger Save as PDF File (.pdf)
    shadowApp.clearNextStartedActivities()
    testActivity?.let { Shadows.shadowOf(it).clearNextStartedActivities() }
    composeTestRule.onNodeWithTag("session_revisit_button_$sessionId").assertIsDisplayed().performClick()
    composeTestRule.waitUntil(timeoutMillis = 6_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      try {
        composeTestRule.onNodeWithTag("session_detail_dialog").assertIsDisplayed()
        true
      } catch (_: AssertionError) {
        false
      }
    }

    composeTestRule.onNodeWithTag("session_detail_export_button").assertIsDisplayed().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      try {
        composeTestRule.onNodeWithTag("session_detail_save_pdf").assertExists()
        true
      } catch (_: AssertionError) {
        false
      }
    }
    composeTestRule.onNodeWithTag("session_detail_save_pdf").performClick()
    composeTestRule.waitForIdle()

    val createPdfIntent = shadowApp.nextStartedActivity
      ?: testActivity?.let { Shadows.shadowOf(it).nextStartedActivityForResult?.intent }
    assertNotNull("Expected ACTION_CREATE_DOCUMENT intent for session .pdf save", createPdfIntent)
    assertEquals(android.content.Intent.ACTION_CREATE_DOCUMENT, createPdfIntent!!.action)
    assertEquals("application/pdf", createPdfIntent.type)
    assertTrue(
      createPdfIntent.getStringExtra(android.content.Intent.EXTRA_TITLE)?.endsWith(".pdf") == true
    )
  }

  @Test
  @Config(sdk = [34], qualifiers = "w411dp-h915dp")
  fun `historyScreen tagging system assigns Work Personal Study and custom labels to sessions and filters list`(): Unit = kotlinx.coroutines.runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    app.getSharedPreferences("doubtsense_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    com.example.util.AppPreferencesManager.init(app)

    val repo = ListeningStateManager.getRepository(app)
    repo.clearAll()

    val now = System.currentTimeMillis()
    val workSessionId = repo.saveSession(
      com.example.data.model.ConversationSession(
        startTime = now - 120_000L,
        endTime = now - 60_000L,
        title = "API Rate Limiter Design",
        category = "System Design",
        customTag = "General",
        transcriptPreview = "Token bucket vs leaky bucket trade-offs",
        doubtCount = 1
      )
    )
    val personalSessionId = repo.saveSession(
      com.example.data.model.ConversationSession(
        startTime = now - 50_000L,
        endTime = now - 10_000L,
        title = "Tax Deduction Basics",
        category = "Finance",
        customTag = "Personal",
        transcriptPreview = "Standard vs itemized deduction thresholds",
        doubtCount = 1
      )
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.DoubtSenseApp(initialDestination = com.example.ui.AppDestination.HISTORY)
      }
    }

    composeTestRule.waitUntil(timeoutMillis = 8_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      try {
        composeTestRule.onNodeWithTag("session_card_$workSessionId").assertExists()
        composeTestRule.onNodeWithTag("session_card_$personalSessionId").assertExists()
        true
      } catch (_: AssertionError) {
        false
      }
    }

    // Default tag filter chips (Work, Personal, Study) are available in History view
    composeTestRule.onNodeWithTag("history_tag_chip_Work").performScrollTo().assertExists()
    composeTestRule.onNodeWithTag("history_tag_chip_Personal").performScrollTo().assertExists()
    composeTestRule.onNodeWithTag("history_tag_chip_Study").performScrollTo().assertExists()

    // Create a custom label ("Thesis") from History view's + New Tag dialog
    composeTestRule.onNodeWithTag("history_add_tag_button").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      try {
        composeTestRule.onNodeWithTag("history_tag_manager_dialog").assertIsDisplayed()
        true
      } catch (_: AssertionError) {
        false
      }
    }
    composeTestRule.onNodeWithTag("history_new_tag_input").performTextInput("Thesis")
    composeTestRule.onNodeWithTag("history_confirm_add_tag_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("history_tag_manager_done_button").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("history_tag_chip_Thesis").performScrollTo().assertExists()

    // Assign 'Work', 'Study', and a new inline custom label 'Research' to the first session
    composeTestRule.onNodeWithTag("sessions_lazy_column")
      .performScrollToNode(hasTestTag("session_card_$workSessionId"))
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("session_tag_chip_$workSessionId").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      try {
        composeTestRule.onNodeWithTag("session_tag_picker_dialog").assertIsDisplayed()
        true
      } catch (_: AssertionError) {
        false
      }
    }
    composeTestRule.onNodeWithTag("tag_picker_chip_Work").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("tag_picker_chip_Study").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("custom_tag_name_input").performScrollTo().performTextInput("Research")
    composeTestRule.onNodeWithTag("confirm_add_custom_tag_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("save_session_tags_button").performClick()

    composeTestRule.waitUntil(timeoutMillis = 6_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      kotlinx.coroutines.runBlocking {
        val updated = repo.getSessionById(workSessionId)
        updated != null && updated.hasTag("Work") && updated.hasTag("Study") && updated.hasTag("Research")
      }
    }

    // Filter History sessions by 'Study' -> shows API Rate Limiter Design, hides Personal session
    composeTestRule.onNodeWithTag("history_tag_chip_Study").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("session_card_$workSessionId").assertExists()
    composeTestRule.onNodeWithTag("session_card_$personalSessionId").assertDoesNotExist()

    // Reset tag filter via 'All' chip -> restores both sessions
    composeTestRule.onNodeWithTag("history_tag_chip_all").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("session_card_$workSessionId").assertExists()
    composeTestRule.onNodeWithTag("session_card_$personalSessionId").assertExists()
  }

  @Test
  @Config(sdk = [34], qualifiers = "w411dp-h915dp")
  fun `categoryDashboardScreen bar chart compares Study vs Work session frequency over time and filters logs`(): Unit = kotlinx.coroutines.runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    app.getSharedPreferences("doubtsense_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    com.example.util.AppPreferencesManager.init(app)

    val repo = ListeningStateManager.getRepository(app)
    repo.clearAll()

    val now = System.currentTimeMillis()
    val day = 86_400_000L
    val studySession1 = repo.saveSession(
      com.example.data.model.ConversationSession(
        startTime = now - 4_000L,
        endTime = now - 1_000L,
        title = "Graph Traversal Algorithms",
        category = "Computer Science",
        customTag = "Study",
        transcriptPreview = "BFS vs DFS memory trade-offs",
        doubtCount = 1
      )
    )
    val studySession2 = repo.saveSession(
      com.example.data.model.ConversationSession(
        startTime = now - day,
        endTime = now - day + 1_000L,
        title = "Thermodynamics Entropy",
        category = "Physics",
        customTag = "Study",
        transcriptPreview = "Why does entropy never decrease in isolated systems?",
        doubtCount = 1
      )
    )
    val workSession1 = repo.saveSession(
      com.example.data.model.ConversationSession(
        startTime = now - 3_000L,
        endTime = now - 500L,
        title = "Microservice Circuit Breaker",
        category = "Architecture",
        customTag = "Work",
        transcriptPreview = "Half-open state threshold tuning",
        doubtCount = 1
      )
    )
    val oldPersonalSession = repo.saveSession(
      com.example.data.model.ConversationSession(
        startTime = now - (15L * day),
        endTime = now - (15L * day) + 1_000L,
        title = "Index Fund Allocation",
        category = "Finance",
        customTag = "Personal",
        transcriptPreview = "Rebalancing schedule",
        doubtCount = 1
      )
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.DoubtSenseApp(initialDestination = com.example.ui.AppDestination.CATEGORY_DASHBOARD)
      }
    }

    composeTestRule.waitUntil(timeoutMillis = 8_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      try {
        composeTestRule.onNodeWithTag("category_dashboard_list")
          .performScrollToNode(hasTestTag("category_frequency_bar_chart"))
        composeTestRule.onNodeWithTag("cat_freq_legend_Study").assertExists()
        true
      } catch (_: Throwable) {
        false
      }
    }

    // 7-day window counts 3 recent sessions (2 Study, 1 Work) and excludes the 15-day-old Personal session
    composeTestRule.onNodeWithTag("cat_freq_total_badge").assertTextContains("3", substring = true)
    composeTestRule.onNodeWithTag("cat_freq_legend_Study").assertTextContains("Study (2)")
    composeTestRule.onNodeWithTag("cat_freq_legend_Work").assertTextContains("Work (1)")
    composeTestRule.onNodeWithTag("cat_freq_bar_chart_canvas").assertIsDisplayed()
    composeTestRule.onNodeWithTag("cat_freq_selected_summary").assertIsDisplayed()

    // Switch to 4 Weeks -> includes the 15-day-old Personal session (4 sessions total)
    composeTestRule.onNodeWithTag("cat_freq_period_four_weeks").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("cat_freq_total_badge").assertTextContains("4", substring = true)
    composeTestRule.onNodeWithTag("cat_freq_legend_Personal").assertTextContains("Personal (1)")

    // Toggle Grouped -> Stacked bars and back
    composeTestRule.onNodeWithTag("cat_freq_style_toggle").performScrollTo().assertTextContains("Grouped").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("cat_freq_style_toggle").assertTextContains("Stacked")

    // Switch grouping dimension to Subject Categories -> displays Computer Science, Physics, Architecture, Finance
    composeTestRule.onNodeWithTag("cat_freq_dim_subjects").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("cat_freq_legend_Architecture").assertExists()

    // Switch back to Tags and isolate Work vs Study
    composeTestRule.onNodeWithTag("cat_freq_dim_tags").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("cat_freq_period_seven_days").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    // Tapping 'View ...' button opens History filtered by the selected tag
    composeTestRule.onNodeWithTag("cat_freq_open_logs_button").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 6_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      try {
        composeTestRule.onNodeWithTag("sessions_lazy_column").assertExists()
        true
      } catch (_: AssertionError) {
        false
      }
    }
    composeTestRule.onNodeWithTag("session_card_$studySession1").assertExists()
    composeTestRule.onNodeWithTag("session_card_$studySession2").assertExists()
    composeTestRule.onNodeWithTag("session_card_$workSession1").assertDoesNotExist()
    composeTestRule.onNodeWithTag("session_card_$oldPersonalSession").assertDoesNotExist()
  }

  @Test
  @Config(sdk = [34], qualifiers = "w411dp-h915dp")
  fun `dashboardScreen highlights detected doubts in real time within chat interface with subtle visual indicator`(): Unit = kotlinx.coroutines.runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    app.getSharedPreferences("doubtsense_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    com.example.util.AppPreferencesManager.init(app)
    ListeningStateManager.clearTranscript()
    ListeningStateManager.setAutoReadAloud(false)

    val repo = ListeningStateManager.getRepository(app)
    repo.clearAll()

    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.DoubtSenseApp(initialDestination = com.example.ui.AppDestination.CONVERSATION)
      }
    }

    composeTestRule.waitUntil(timeoutMillis = 6_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      try {
        composeTestRule.onNodeWithTag("dashboard_lazy_column")
          .performScrollToNode(hasTestTag("live_transcript_card"))
        true
      } catch (_: Throwable) {
        false
      }
    }

    // 1. Live partial speech with uncertainty triggers the subtle real-time partial doubt indicator
    ListeningStateManager.updateLiveTranscript("I don't understand how ATP synthase works")
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      try {
        composeTestRule.onNodeWithTag("live_partial_doubt_indicator").assertIsDisplayed()
        true
      } catch (_: AssertionError) {
        false
      }
    }

    // 2. Finalizing a neutral non-doubt statement leaves it unhighlighted (no visual doubt banner)
    ListeningStateManager.onUtteranceFinalized(
      utterance = "Hey guys, let's grab coffee at 3pm after class.",
      context = app
    )
    composeTestRule.waitUntil(timeoutMillis = 6_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      ListeningStateManager.liveChatTurns.value.isNotEmpty() && !ListeningStateManager.isAnalyzing.value
    }
    composeTestRule.onNodeWithTag("live_doubt_visual_banner").assertDoesNotExist()

    // 3. Finalizing a genuine doubt highlights that chat turn with a subtle color indicator & banner
    ListeningStateManager.onUtteranceFinalized(
      utterance = "Wait, why does warm air actually rise when heated?",
      context = app,
      isExplicitQuery = true
    )
    composeTestRule.waitUntil(timeoutMillis = 8_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      ListeningStateManager.liveDoubtAlert.value != null && !ListeningStateManager.isAnalyzing.value
    }

    val alert = ListeningStateManager.liveDoubtAlert.value!!
    composeTestRule.onNodeWithTag("dashboard_lazy_column")
      .performScrollToNode(hasTestTag("live_transcript_card"))
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("live_doubt_visual_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("chat_turn_row_${alert.turnId}").assertExists()
    composeTestRule.onNodeWithTag("chat_turn_doubt_badge_${alert.turnId}", useUnmergedTree = true).assertExists()

    // 4. Tapping Inspect on the visual banner opens the highlighted doubt details dialog
    composeTestRule.onNodeWithTag("inspect_live_doubt_banner", useUnmergedTree = true).performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      try {
        composeTestRule.onNodeWithTag("live_doubt_inspect_dialog").assertIsDisplayed()
        true
      } catch (_: AssertionError) {
        false
      }
    }
    composeTestRule.onNodeWithTag("close_live_doubt_inspect_dialog").performClick()
    composeTestRule.waitForIdle()

    // 5. Dismissing the visual banner hides it while keeping the highlighted chat turn in place
    composeTestRule.onNodeWithTag("dismiss_live_doubt_banner", useUnmergedTree = true).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("live_doubt_visual_banner").assertDoesNotExist()
    composeTestRule.onNodeWithTag("chat_turn_row_${alert.turnId}").assertExists()
    ListeningStateManager.clearTranscript()
  }

  @Test
  @Config(sdk = [34], qualifiers = "w411dp-h915dp")
  fun `historyAndAnalytics export doubt history and session analysis to shareable PDF and Text files`(): Unit = kotlinx.coroutines.runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    app.getSharedPreferences("doubtsense_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    com.example.util.AppPreferencesManager.init(app)
    val exportsDir = java.io.File(app.cacheDir, "exports")
    exportsDir.deleteRecursively()

    val repo = ListeningStateManager.getRepository(app)
    repo.clearAll()

    val now = System.currentTimeMillis()
    val sessionId = repo.saveSession(
      com.example.data.model.ConversationSession(
        startTime = now - 240_000L,
        endTime = now,
        title = "Cache Coherence Protocols",
        category = "Computer Architecture",
        customTag = "Work, Study",
        transcriptJson = com.example.data.model.ConversationSession.encodeTranscriptTurns(
          listOf(
            "Lead: MESI tracks Modified, Exclusive, Shared, and Invalid states.",
            "Dev: Why does MESI need an Exclusive state separate from Shared?"
          )
        ),
        transcriptPreview = "Dev: Why does MESI need an Exclusive state separate from Shared?",
        averageDoubtIntensity = 0.68f,
        peakDoubtIntensity = 0.88f,
        doubtCount = 1,
        summary = "Exclusive avoids bus invalidation traffic on subsequent writes."
      )
    )
    repo.saveDoubt(
      DoubtItem(
        sessionId = sessionId,
        timestamp = now - 60_000L,
        doubtSnippet = "Why does MESI need an Exclusive state separate from Shared?",
        subject = "Computer Architecture",
        topic = "MESI Cache Coherence",
        quickAnswer = "Exclusive means only one cache holds the clean line, so upgrading to Modified needs no bus broadcast.",
        detailedAnswer = "Tracking clean unshared lines avoids unnecessary bus invalidation transactions when a core writes to its own private data.",
        notificationSummary = "Exclusive eliminates redundant invalidations on private cache writes.",
        confidenceScore = 0.88f,
        customTag = "Work, Study"
      )
    )

    val shadowApp = Shadows.shadowOf(app)

    // 1. Export Doubt History & Session Analysis as shareable Text (.txt) from HistoryScreen
    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.DoubtSenseApp(initialDestination = com.example.ui.AppDestination.HISTORY)
      }
    }

    composeTestRule.waitUntil(timeoutMillis = 8_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      try {
        composeTestRule.onNodeWithTag("export_history_button").assertExists()
        true
      } catch (_: AssertionError) {
        false
      }
    }

    shadowApp.clearNextStartedActivities()
    composeTestRule.onNodeWithTag("export_history_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("history_export_txt").performClick()
    composeTestRule.waitForIdle()

    val txtChooser = shadowApp.nextStartedActivity
    assertNotNull("Expected share chooser Intent for History & Analysis TXT", txtChooser)
    assertEquals(android.content.Intent.ACTION_CHOOSER, txtChooser!!.action)
    val txtSendIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
      txtChooser.getParcelableExtra(android.content.Intent.EXTRA_INTENT, android.content.Intent::class.java)
    } else {
      @Suppress("DEPRECATION")
      txtChooser.getParcelableExtra(android.content.Intent.EXTRA_INTENT)
    }
    assertNotNull(txtSendIntent)
    assertEquals("text/plain", txtSendIntent!!.type)
    assertNotNull(txtSendIntent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM))

    val txtFiles = exportsDir.listFiles { _, name -> name.endsWith(".txt") } ?: emptyArray()
    assertTrue("Expected generated .txt export file", txtFiles.isNotEmpty())
    val txtContent = txtFiles.first().readText(Charsets.UTF_8)
    assertTrue(txtContent.contains("SESSION ANALYSIS OVERVIEW"))
    assertTrue(txtContent.contains("Overall Avg Doubt Intensity: 68% (Peak: 88%)"))
    assertTrue(txtContent.contains("MESI Cache Coherence"))

    // 2. Navigate to Analytics Dashboard and share Doubt History & Session Analysis as PDF (.pdf)
    shadowApp.clearNextStartedActivities()
    composeTestRule.onNodeWithTag("nav_category_dashboard").performClick()
    composeTestRule.waitUntil(timeoutMillis = 6_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      try {
        composeTestRule.onNodeWithTag("dashboard_export_report_button").assertIsDisplayed()
        true
      } catch (_: AssertionError) {
        false
      }
    }

    composeTestRule.onNodeWithTag("dashboard_export_report_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("analytics_export_pdf").performClick()
    composeTestRule.waitForIdle()

    val pdfChooser = shadowApp.nextStartedActivity
    assertNotNull("Expected share chooser Intent for Analytics History & Session Analysis PDF", pdfChooser)
    assertEquals(android.content.Intent.ACTION_CHOOSER, pdfChooser!!.action)
    val pdfSendIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
      pdfChooser.getParcelableExtra(android.content.Intent.EXTRA_INTENT, android.content.Intent::class.java)
    } else {
      @Suppress("DEPRECATION")
      pdfChooser.getParcelableExtra(android.content.Intent.EXTRA_INTENT)
    }
    assertNotNull(pdfSendIntent)
    assertEquals("application/pdf", pdfSendIntent!!.type)
    assertNotNull(pdfSendIntent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM))

    val pdfFiles = exportsDir.listFiles { _, name -> name.endsWith(".pdf") } ?: emptyArray()
    assertTrue("Expected generated .pdf export file", pdfFiles.isNotEmpty())
    val pdfBytes = pdfFiles.first().readBytes()
    assertTrue(pdfBytes.size > 100)
    assertTrue(String(pdfBytes.copyOfRange(0, 8), Charsets.US_ASCII).startsWith("%PDF-1."))
  }

  @Test
  @Config(sdk = [34], qualifiers = "w411dp-h915dp")
  fun `dashboardScreen chat voice handles microphone permission denial rationale and permanently denied settings intent`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    Shadows.shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
    ListeningStateManager.clearTranscript()
    ListeningStateManager.setAutoReadAloud(false)

    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.DoubtSenseApp(initialDestination = com.example.ui.AppDestination.CONVERSATION)
      }
    }

    composeTestRule.waitUntil(timeoutMillis = 6_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      try {
        composeTestRule.onNodeWithTag("dashboard_lazy_column")
          .performScrollToNode(hasTestTag("chat_voice_mic_button"))
        true
      } catch (_: Throwable) {
        false
      }
    }

    composeTestRule.onNodeWithTag("chat_voice_mic_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("chat_hands_free_toggle").assertIsDisplayed()

    // 1. Denied permission with rationale -> shows rationale banner & Allow Mic CTA
    composeTestRule.runOnUiThread {
      ListeningStateManager.onChatMicPermissionResult(
        granted = false,
        shouldShowRationale = true,
        context = app,
        startImmediately = false
      )
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("chat_mic_permission_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("grant_chat_mic_permission_button").assertIsDisplayed()

    // 2. Permanently denied -> shows Open Settings CTA launching ACTION_APPLICATION_DETAILS_SETTINGS
    val shadowApp = Shadows.shadowOf(app)
    shadowApp.clearNextStartedActivities()
    composeTestRule.runOnUiThread {
      ListeningStateManager.onChatMicPermissionResult(
        granted = false,
        shouldShowRationale = false,
        context = app,
        startImmediately = false
      )
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("open_app_settings_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    val settingsIntent = shadowApp.nextStartedActivity
    assertNotNull("Expected App Settings intent when permission permanently denied", settingsIntent)
    assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settingsIntent!!.action)
    assertEquals("package:${app.packageName}", settingsIntent.dataString)

    // 3. Returning with permission granted clears banner cleanly
    Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
    composeTestRule.runOnUiThread {
      ListeningStateManager.refreshChatMicPermissionState(app)
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("chat_mic_permission_banner").assertDoesNotExist()
    ListeningStateManager.clearTranscript()
  }

  @Test
  @Config(sdk = [34], qualifiers = "w411dp-h915dp")
  fun `dashboardScreen real-time voice-to-text in chat streams partial words and submits doubt hands-free`(): Unit = kotlinx.coroutines.runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
    app.getSharedPreferences("doubtsense_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    com.example.util.AppPreferencesManager.init(app)
    ListeningStateManager.clearTranscript()
    ListeningStateManager.setAutoReadAloud(false)

    val repo = ListeningStateManager.getRepository(app)
    repo.clearAll()

    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.DoubtSenseApp(initialDestination = com.example.ui.AppDestination.CONVERSATION)
      }
    }

    composeTestRule.waitUntil(timeoutMillis = 6_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      try {
        composeTestRule.onNodeWithTag("dashboard_lazy_column")
          .performScrollToNode(hasTestTag("chat_voice_mic_button"))
        true
      } catch (_: Throwable) {
        false
      }
    }

    // Start inline chat capture (sets app context) and stream real-time speech-to-text words
    composeTestRule.runOnUiThread {
      ListeningStateManager.startChatVoiceInput(app)
      ListeningStateManager.chatCaptureListener.onCaptureStarted("Live Speech-to-Text")
      ListeningStateManager.chatCaptureListener.onAudioLevelChanged(0.68f)
      ListeningStateManager.chatCaptureListener.onElapsedSecondsChanged(2)
      ListeningStateManager.chatCaptureListener.onPartialTranscript("Why does warm air rise when heated?")
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      try {
        composeTestRule.onNodeWithTag("chat_voice_recording_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_voice_live_text")
          .assertTextContains("Why does warm air rise when heated?", substring = true)
        composeTestRule.onNodeWithTag("live_partial_doubt_indicator").assertIsDisplayed()
        true
      } catch (_: AssertionError) {
        false
      }
    }

    // Composer input field mirrors live spoken words while recording
    composeTestRule.onNodeWithTag("manual_speech_input")
      .assertTextContains("Why does warm air rise when heated?", substring = true)

    // Finalizing utterance hands-free submits the question without tapping Send
    composeTestRule.runOnUiThread {
      ListeningStateManager.chatCaptureListener.onFinalTranscriptReady(
        "Wait, why does warm air actually rise when heated?"
      )
    }

    composeTestRule.waitUntil(timeoutMillis = 8_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      ListeningStateManager.chatVoiceInputState.value is com.example.service.ChatVoiceInputState.Idle &&
        ListeningStateManager.liveDoubtAlert.value != null &&
        !ListeningStateManager.isAnalyzing.value
    }

    composeTestRule.onNodeWithTag("dashboard_lazy_column")
      .performScrollToNode(hasTestTag("live_doubt_visual_banner"))
    composeTestRule.onNodeWithTag("live_doubt_visual_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("chat_voice_recording_bar").assertDoesNotExist()
    assertTrue(
      ListeningStateManager.liveChatTurns.value.any {
        it.text.contains("warm air actually rise", ignoreCase = true)
      }
    )

    ListeningStateManager.clearTranscript()
  }

  @Test
  @Config(sdk = [34])
  fun `doubtDatabase migration 5 to 6 preserves legacy sessions and doubts with unique cloudIds`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val dbName = "test_migration_5_to_6_${System.nanoTime()}.db"
    val dbFile = app.getDatabasePath(dbName)
    dbFile.parentFile?.mkdirs()

    // Create a v5 database using SQLite directly
    val rawDb = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    rawDb.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `conversation_sessions` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        `startTime` INTEGER NOT NULL,
        `endTime` INTEGER NOT NULL,
        `title` TEXT NOT NULL,
        `category` TEXT NOT NULL,
        `transcriptJson` TEXT NOT NULL,
        `transcriptPreview` TEXT NOT NULL,
        `averageDoubtIntensity` REAL NOT NULL,
        `peakDoubtIntensity` REAL NOT NULL,
        `doubtCount` INTEGER NOT NULL,
        `summary` TEXT NOT NULL,
        `customTag` TEXT NOT NULL,
        `isPinned` INTEGER NOT NULL
      )
      """.trimIndent()
    )
    rawDb.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `doubts` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        `sessionId` INTEGER NOT NULL,
        `timestamp` INTEGER NOT NULL,
        `doubtSnippet` TEXT NOT NULL,
        `topic` TEXT NOT NULL,
        `quickAnswer` TEXT NOT NULL,
        `detailedAnswer` TEXT NOT NULL,
        `notificationSummary` TEXT NOT NULL,
        `conversationalContext` TEXT NOT NULL,
        `isAmbiguous` INTEGER NOT NULL,
        `clarificationQuestion` TEXT,
        `confidenceScore` REAL NOT NULL,
        `isStarred` INTEGER NOT NULL,
        `subject` TEXT NOT NULL,
        `importantConcepts` TEXT NOT NULL,
        `doubtType` TEXT NOT NULL,
        `intent` TEXT NOT NULL,
        `suggestedOptionsJson` TEXT NOT NULL,
        `referenceMaterials` TEXT NOT NULL,
        `conversationSuggestions` TEXT NOT NULL,
        `userClarificationResponse` TEXT,
        `clarificationHistory` TEXT NOT NULL,
        `preferredStyle` TEXT NOT NULL,
        `clarificationMode` TEXT NOT NULL,
        `isResolvedWithClarification` INTEGER NOT NULL,
        `isFactChecked` INTEGER NOT NULL,
        `verificationStatus` TEXT NOT NULL,
        `verificationSourcesJson` TEXT NOT NULL,
        `customTag` TEXT NOT NULL,
        `oneSentenceSummary` TEXT NOT NULL
      )
      """.trimIndent()
    )
    rawDb.execSQL(
      """
      INSERT INTO `conversation_sessions`
      (`id`, `startTime`, `endTime`, `title`, `category`, `transcriptJson`, `transcriptPreview`, `averageDoubtIntensity`, `peakDoubtIntensity`, `doubtCount`, `summary`, `customTag`, `isPinned`)
      VALUES (1, 1760000000000, 1760000060000, 'Legacy V5 Session', 'Physics', '["Why is sky blue?"]', 'Why is sky blue?', 0.75, 0.88, 1, 'Rayleigh scattering summary', 'Study', 1)
      """.trimIndent()
    )
    rawDb.execSQL(
      """
      INSERT INTO `doubts`
      (`id`, `sessionId`, `timestamp`, `doubtSnippet`, `topic`, `quickAnswer`, `detailedAnswer`, `notificationSummary`, `conversationalContext`, `isAmbiguous`, `clarificationQuestion`, `confidenceScore`, `isStarred`, `subject`, `importantConcepts`, `doubtType`, `intent`, `suggestedOptionsJson`, `referenceMaterials`, `conversationSuggestions`, `userClarificationResponse`, `clarificationHistory`, `preferredStyle`, `clarificationMode`, `isResolvedWithClarification`, `isFactChecked`, `verificationStatus`, `verificationSourcesJson`, `customTag`, `oneSentenceSummary`)
      VALUES (1, 1, 1760000030000, 'Why is sky blue?', 'Rayleigh Scattering', 'Short waves scatter more.', 'Detailed Rayleigh explanation.', 'Blue light scatters.', 'Physics class', 0, NULL, 0.88, 1, 'Physics', 'Wavelength', 'Cause', 'Learn optics', '[]', '', '', NULL, '', 'Detailed', 'PRIVATE', 0, 0, '', '[]', 'Study', 'Short blue wavelengths scatter most.')
      """.trimIndent()
    )
    rawDb.version = 5
    rawDb.close()

    val migratedDb = androidx.room.Room.databaseBuilder(
      app,
      com.example.data.local.DoubtDatabase::class.java,
      dbName
    )
      .addMigrations(com.example.data.local.DoubtDatabase.MIGRATION_5_6)
      .allowMainThreadQueries()
      .build()

    try {
      val sessions = kotlinx.coroutines.runBlocking { migratedDb.conversationSessionDao().getAllSessionsOnce() }
      val doubts = kotlinx.coroutines.runBlocking { migratedDb.doubtDao().getAllDoubtsOnce() }
      assertEquals(1, sessions.size)
      assertEquals(1, doubts.size)
      val migratedSession = sessions.first()
      val migratedDoubt = doubts.first()
      assertEquals("Legacy V5 Session", migratedSession.title)
      assertTrue(com.example.data.model.ConversationSession.isValidCloudId(migratedSession.cloudId))
      assertTrue(com.example.data.model.ConversationSession.isValidCloudId(migratedDoubt.cloudId))
      assertEquals(migratedSession.cloudId, migratedDoubt.sessionCloudId)
      assertEquals(1760000060000L, migratedSession.updatedAtMillis)
    } finally {
      migratedDb.close()
      dbFile.delete()
    }
  }

  @Test
  @Config(sdk = [34])
  fun `doubtRepository mergeRemoteSnapshot merges multi-device sessions without ID collisions or duplicates`(): Unit = kotlinx.coroutines.runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val repo = ListeningStateManager.getRepository(app)
    repo.clearAll()

    val seedTime = System.currentTimeMillis()
    val deviceASessionId = repo.saveSession(
      com.example.data.model.ConversationSession(
        startTime = seedTime - 60_000L,
        endTime = seedTime,
        title = "Device A Local Session",
        category = "Computer Science",
        customTag = "Study",
        cloudId = "session_devA_1",
        updatedAtMillis = seedTime
      )
    )
    repo.saveDoubt(
      DoubtItem(
        sessionId = deviceASessionId,
        timestamp = seedTime - 30_000L,
        doubtSnippet = "How does B-tree balancing work?",
        topic = "B-Trees",
        quickAnswer = "Splits full nodes upward.",
        detailedAnswer = "All leaves stay at uniform depth.",
        notificationSummary = "B-Trees keep balanced height.",
        cloudId = "doubt_devA_1",
        sessionCloudId = "session_devA_1",
        updatedAtMillis = seedTime
      )
    )
    val now = System.currentTimeMillis().coerceAtLeast(seedTime)

    // Simulated snapshot from Firestore originating from Device B (notice localId=1 collides with Device A's id=1!)
    // plus a newer tag update for Device A's session
    val remoteSessions = listOf(
      com.example.data.model.ConversationSession(
        id = 1L, // Same local Room PK as Device A!
        startTime = now + 10_000L,
        endTime = now + 20_000L,
        title = "Device B Architecture Discussion",
        category = "Systems",
        customTag = "Work",
        summary = "Discussed event-driven queues.",
        cloudId = "session_devB_1",
        updatedAtMillis = now + 20_000L
      ),
      com.example.data.model.ConversationSession(
        id = 1L,
        startTime = seedTime - 60_000L,
        endTime = seedTime,
        title = "Device A Local Session",
        category = "Computer Science",
        customTag = "Work, Study",
        cloudId = "session_devA_1",
        updatedAtMillis = now + 30_000L
      )
    )
    val remoteDoubts = listOf(
      DoubtItem(
        id = 1L,
        sessionId = 0L,
        timestamp = now + 15_000L,
        doubtSnippet = "Why use dead-letter queues?",
        topic = "Message Queues",
        quickAnswer = "Isolate poison messages for retry inspection.",
        detailedAnswer = "Prevents failing messages from blocking consumer partitions.",
        notificationSummary = "DLQs isolate poison pills.",
        cloudId = "doubt_devB_1",
        sessionCloudId = "session_devB_1",
        updatedAtMillis = now + 15_000L
      )
    )

    val stats = repo.mergeRemoteSnapshot(remoteSessions, remoteDoubts)
    assertEquals(1, stats.sessionsInserted)
    assertEquals(1, stats.sessionsUpdated)
    assertEquals(1, stats.doubtsInserted)

    val allSessions = repo.getAllSessionsOnce()
    assertEquals(2, allSessions.size)
    val devA = allSessions.first { it.cloudId == "session_devA_1" }
    val devB = allSessions.first { it.cloudId == "session_devB_1" }
    assertEquals("Work, Study", devA.customTag)
    assertEquals("Device B Architecture Discussion", devB.title)

    val devBDoubts = repo.getDoubtsForSessionOnce(devB.id)
    assertEquals(1, devBDoubts.size)
    assertEquals("doubt_devB_1", devBDoubts.first().cloudId)
    assertEquals(devB.id, devBDoubts.first().sessionId)

    // Merging identical snapshot a second time is idempotent
    val secondStats = repo.mergeRemoteSnapshot(remoteSessions, remoteDoubts)
    assertEquals(0, secondStats.totalChanges)
  }

  @Test
  @Config(sdk = [34], qualifiers = "w411dp-h915dp")
  fun `cloudSyncUi renders sync controls across Dashboard, History, and Settings screens`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    app.getSharedPreferences("doubtsense_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    com.example.util.AppPreferencesManager.init(app)

    // 1. Dashboard & History Cloud Sync UI pills/cards
    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.DoubtSenseApp(initialDestination = com.example.ui.AppDestination.CONVERSATION)
      }
    }

    composeTestRule.waitUntil(timeoutMillis = 6_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      try {
        composeTestRule.onNodeWithTag("dashboard_cloud_sync_pill").assertIsDisplayed()
        true
      } catch (_: AssertionError) {
        false
      }
    }

    composeTestRule.onNodeWithTag("nav_history").performClick()
    composeTestRule.waitUntil(timeoutMillis = 6_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      try {
        composeTestRule.onNodeWithTag("history_cloud_sync_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("history_google_signin_button").assertIsDisplayed()
        true
      } catch (_: AssertionError) {
        false
      }
    }

    // 2. Settings Auto-Sync switch & Google Sign-In button
    composeTestRule.onNodeWithTag("nav_settings").performClick()
    composeTestRule.waitUntil(timeoutMillis = 6_000L) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      try {
        composeTestRule.onNodeWithTag("settings_list")
          .performScrollToNode(hasTestTag("cloud_sync_card"))
        true
      } catch (_: Throwable) {
        false
      }
    }

    composeTestRule.onNodeWithTag("auto_cloud_sync_switch").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("google_signin_button").performScrollTo().assertIsDisplayed()
    assertTrue(com.example.util.AppPreferencesManager.autoCloudSyncEnabled.value)
    composeTestRule.onNodeWithTag("auto_cloud_sync_switch").performClick()
    composeTestRule.waitForIdle()
    assertFalse(com.example.util.AppPreferencesManager.autoCloudSyncEnabled.value)
  }
}


