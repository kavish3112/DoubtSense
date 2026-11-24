package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ai.AnswerEngine
import com.example.data.model.ConversationSession
import com.example.data.model.DoubtItem
import com.example.data.remote.CloudSyncManager
import com.example.data.remote.CloudSyncUiState
import com.example.data.remote.GoogleAuthHelper
import com.example.data.remote.GoogleSignInOutcome
import com.example.data.repository.DoubtRepository
import com.example.service.ListeningStateManager
import com.example.ui.components.DoubtCard
import com.example.ui.components.DoubtIntensityTrendChart
import com.example.ui.components.SessionDetailDialog
import com.example.ui.components.SessionDetailPane
import com.example.ui.components.SessionLogCard
import com.example.ui.components.SwipeToDeleteContainer
import com.example.ui.components.tagAccentColor
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate950
import com.example.util.AppPreferencesManager
import com.example.util.DocumentExportHelper
import com.example.util.ExportDocumentFormat
import com.example.util.SemanticSearchHelper
import com.example.util.ShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class DateFilterOption(val label: String) {
    ALL("All Dates"),
    TODAY("Today"),
    LAST_7_DAYS("Recent (7d)")
}

@Composable
fun HistoryScreen(
    targetDoubtId: Long? = null,
    initialCategoryFilter: String? = null,
    onClearInitialCategory: () -> Unit = {},
    initialTagFilter: String? = null,
    onClearInitialTag: () -> Unit = {},
    onRevisitDoubtInAnswerEngine: (DoubtItem) -> Unit = {},
    onContinueSessionInChat: (ConversationSession) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val repository = remember { ListeningStateManager.getRepository(context) }
    val allDoubts by repository.allDoubts.collectAsState(initial = emptyList())
    val allSessions by repository.allSessions.collectAsState(initial = emptyList())
    val customTags by AppPreferencesManager.customTags.collectAsState()
    val aiLanguage by AppPreferencesManager.aiResponseLanguage.collectAsState()
    val cloudSyncState by CloudSyncManager.syncState.collectAsState()
    val answerEngine = remember { AnswerEngine() }
    val coroutineScope = rememberCoroutineScope()
    var isAuthActionBusy by remember { mutableStateOf(false) }
    var authActionMessage by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember(initialCategoryFilter) { mutableStateOf(initialCategoryFilter) }
    var selectedTagFilter by remember(initialTagFilter) { mutableStateOf(initialTagFilter) }
    var selectedTopic by remember { mutableStateOf<String?>(null) }
    var dateFilter by remember { mutableStateOf(DateFilterOption.ALL) }
    var showOnlyStarred by remember { mutableStateOf(false) }
    var showOnlyAmbiguous by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showTrendChart by remember { mutableStateOf(false) }
    var selectedSessionIdForDetail by remember { mutableStateOf<Long?>(null) }
    var pendingDeleteSession by remember { mutableStateOf<ConversationSession?>(null) }
    var pendingDeleteDoubt by remember { mutableStateOf<DoubtItem?>(null) }
    var summarizingDoubtIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pendingSaveSession by remember { mutableStateOf<Pair<ConversationSession, ExportDocumentFormat>?>(null) }
    var sessionExportProgress by remember { mutableStateOf<Float?>(null) }
    var sessionExportStatusMessage by remember { mutableStateOf<String?>(null) }
    var showTagManagerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sessionExportStatusMessage) {
        if (sessionExportStatusMessage != null) {
            delay(4000L)
            sessionExportStatusMessage = null
        }
    }

    fun handleSaveSessionUri(targetUri: Uri?) {
        val target = pendingSaveSession
        pendingSaveSession = null
        if (targetUri == null || target == null) return
        val (sessionToSave, format) = target
        coroutineScope.launch {
            sessionExportProgress = 0.2f
            val formatLabel = if (format == ExportDocumentFormat.PDF) "PDF (.pdf)" else "Text (.txt)"
            val saved = withContext(Dispatchers.IO) {
                val doubts = repository.getDoubtsForSessionOnce(sessionToSave.id)
                DocumentExportHelper.writeSessionToUri(
                    context = context,
                    targetUri = targetUri,
                    session = sessionToSave,
                    doubts = doubts,
                    format = format,
                    onProgress = { p -> sessionExportProgress = p }
                )
            }
            sessionExportProgress = 1.0f
            sessionExportStatusMessage = if (saved) {
                "Saved \"${sessionToSave.title.ifBlank { "Session" }}\" as $formatLabel"
            } else {
                "Failed to save session as $formatLabel"
            }
            delay(650L)
            sessionExportProgress = null
        }
    }

    val saveSessionTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { targetUri ->
        handleSaveSessionUri(targetUri)
    }

    val saveSessionPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { targetUri ->
        handleSaveSessionUri(targetUri)
    }

    fun launchSaveSession(session: ConversationSession, format: ExportDocumentFormat) {
        pendingSaveSession = session to format
        val suggestedName = DocumentExportHelper.buildSuggestedSessionFileName(session, format)
        when (format) {
            ExportDocumentFormat.STRUCTURED_TEXT -> saveSessionTxtLauncher.launch(suggestedName)
            ExportDocumentFormat.PDF -> saveSessionPdfLauncher.launch(suggestedName)
            ExportDocumentFormat.CSV -> saveSessionTxtLauncher.launch(suggestedName)
        }
    }

    val selectedSessionForDetail = remember(allSessions, selectedSessionIdForDetail) {
        allSessions.firstOrNull { it.id == selectedSessionIdForDetail }
    }

    // 0 = Sessions, 1 = Doubts
    var selectedTab by remember {
        mutableIntStateOf(if (targetDoubtId != null && targetDoubtId > 0) 1 else 0)
    }

    LaunchedEffect(targetDoubtId) {
        if (targetDoubtId != null && targetDoubtId > 0) {
            selectedTab = 1
        }
    }

    val topicFrequencies = remember(allDoubts) {
        allDoubts.groupingBy { it.topic }.eachCount().toList().sortedByDescending { it.second }
    }

    val allAvailableTagLabels = remember(customTags, allSessions) {
        (customTags + allSessions.flatMap { it.getTagList() })
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("General", ignoreCase = true) }
            .distinctBy { it.lowercase() }
    }

    val sessionCountsByTag = remember(allSessions, allAvailableTagLabels) {
        allAvailableTagLabels.associateWith { tag ->
            allSessions.count { it.hasTag(tag) }
        }
    }

    val filteredSessions = remember(
        allSessions,
        searchQuery,
        selectedCategory,
        selectedTagFilter,
        dateFilter,
        showOnlyStarred
    ) {
        val base = allSessions.filter { session ->
            val matchesCat = selectedCategory == null || session.category.equals(selectedCategory, ignoreCase = true)
            val matchesTag = selectedTagFilter == null || session.hasTag(selectedTagFilter!!)
            val matchesDate = matchesDateFilter(session.startTime, dateFilter)
            val matchesStar = !showOnlyStarred || session.isPinned
            matchesCat && matchesTag && matchesDate && matchesStar
        }
        val searched = if (searchQuery.isBlank()) {
            base
        } else {
            val rankedSemantic = SemanticSearchHelper.rankBySemanticAndKeyword(base, searchQuery) { s ->
                "${s.title} ${s.category} ${s.customTag} ${s.transcriptPreview} ${s.transcriptJson} ${s.summary}"
            }
            val dateOrExactMatches = base.filter { matchesSessionQuery(it, searchQuery) }
            (dateOrExactMatches + rankedSemantic).distinctBy { it.id }
        }
        searched.sortedWith(
            compareByDescending<ConversationSession> { it.isPinned }
                .thenByDescending { it.endTime }
        )
    }

    val filteredDoubts = remember(
        allDoubts,
        searchQuery,
        selectedCategory,
        selectedTagFilter,
        selectedTopic,
        dateFilter,
        showOnlyStarred,
        showOnlyAmbiguous
    ) {
        val base = allDoubts.filter { item ->
            val matchesCat = selectedCategory == null || item.subject.equals(selectedCategory, ignoreCase = true) ||
                item.topic.contains(selectedCategory!!, ignoreCase = true)
            val matchesTag = selectedTagFilter == null ||
                ConversationSession.parseTagList(item.customTag).any { it.equals(selectedTagFilter, ignoreCase = true) }
            val matchesDate = matchesDateFilter(item.timestamp, dateFilter)
            val matchesTopic = selectedTopic == null || item.topic.equals(selectedTopic, ignoreCase = true)
            val matchesStar = !showOnlyStarred || item.isStarred
            val matchesAmbiguous = !showOnlyAmbiguous || item.isAmbiguous

            matchesCat && matchesTag && matchesDate && matchesTopic && matchesStar && matchesAmbiguous
        }
        val searched = if (searchQuery.isBlank()) {
            base
        } else {
            val rankedSemantic = SemanticSearchHelper.rankBySemanticAndKeyword(base, searchQuery) { d ->
                "${d.topic} ${d.subject} ${d.customTag} ${d.doubtSnippet} ${d.quickAnswer} ${d.detailedAnswer} ${d.importantConcepts} ${d.oneSentenceSummary}"
            }
            val dateOrExactMatches = base.filter { matchesDoubtQuery(it, searchQuery) }
            (dateOrExactMatches + rankedSemantic).distinctBy { it.id }
        }
        DoubtRepository.sortWithPinnedFirst(searched)
    }

    val isFilterActive = searchQuery.isNotBlank() ||
        selectedCategory != null ||
        selectedTagFilter != null ||
        selectedTopic != null ||
        dateFilter != DateFilterOption.ALL ||
        showOnlyStarred ||
        showOnlyAmbiguous

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Header + Trend / Export / Clear controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Conversation Logs",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Text(
                    text = "${filteredSessions.size} sessions • ${filteredDoubts.size} doubts",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { showTrendChart = !showTrendChart },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("toggle_trend_chart_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = "Trend Chart",
                        modifier = Modifier.size(15.dp),
                        tint = if (showTrendChart) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Trend", fontSize = 12.sp)
                }

                if (allDoubts.isNotEmpty() || allSessions.isNotEmpty()) {
                    var showExportMenu by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { showExportMenu = true },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("export_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.cd_export_history),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.action_export_history), fontSize = 12.sp)
                        }

                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_analysis_pdf)) },
                                onClick = {
                                    showExportMenu = false
                                    val exportDoubts = filteredDoubts.ifEmpty { allDoubts }
                                    val exportSessions = filteredSessions.ifEmpty { allSessions }
                                    ShareHelper.exportHistoryWithSessionAnalysis(
                                        context = context,
                                        sessions = exportSessions,
                                        doubts = exportDoubts,
                                        format = ExportDocumentFormat.PDF,
                                        title = "DoubtSense Doubt History & Session Analysis"
                                    )
                                },
                                modifier = Modifier.testTag("history_export_pdf")
                            )
                            DropdownMenuItem(
                                text = { Text("🖨️ Print / Save Printable PDF") },
                                leadingIcon = {
                                    Icon(Icons.Default.Print, contentDescription = null, tint = ElectricCyan)
                                },
                                onClick = {
                                    showExportMenu = false
                                    val exportDoubts = filteredDoubts.ifEmpty { allDoubts }
                                    val exportSessions = filteredSessions.ifEmpty { allSessions }
                                    val turns = exportSessions.flatMap { s ->
                                        val sTurns = s.getTranscriptTurns()
                                        if (sTurns.isNotEmpty()) listOf("[${s.title}]") + sTurns else emptyList()
                                    }
                                    DocumentExportHelper.printPdfDocument(
                                        context = context,
                                        conversationTurns = turns,
                                        doubts = exportDoubts,
                                        documentTitle = "DoubtSense Printable History & Analysis",
                                        sessions = exportSessions
                                    )
                                },
                                modifier = Modifier.testTag("history_export_print_pdf")
                            )
                            DropdownMenuItem(
                                text = { Text("📊 Export Filtered Logs as CSV (.csv)") },
                                leadingIcon = {
                                    Icon(Icons.Default.TableChart, contentDescription = null, tint = EmeraldSuccess)
                                },
                                onClick = {
                                    showExportMenu = false
                                    val exportDoubts = filteredDoubts.ifEmpty { allDoubts }
                                    val exportSessions = filteredSessions.ifEmpty { allSessions }
                                    val turns = exportSessions.flatMap { s ->
                                        val sTurns = s.getTranscriptTurns()
                                        if (sTurns.isNotEmpty()) listOf("[${s.title}]") + sTurns else emptyList()
                                    }
                                    ShareHelper.exportFullConversation(
                                        context = context,
                                        conversationTurns = turns,
                                        doubts = exportDoubts,
                                        format = ExportDocumentFormat.CSV,
                                        title = "DoubtSense Saved History CSV",
                                        sessions = exportSessions
                                    )
                                },
                                modifier = Modifier.testTag("history_export_csv")
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_analysis_txt)) },
                                onClick = {
                                    showExportMenu = false
                                    val exportDoubts = filteredDoubts.ifEmpty { allDoubts }
                                    val exportSessions = filteredSessions.ifEmpty { allSessions }
                                    ShareHelper.exportHistoryWithSessionAnalysis(
                                        context = context,
                                        sessions = exportSessions,
                                        doubts = exportDoubts,
                                        format = ExportDocumentFormat.STRUCTURED_TEXT,
                                        title = "DoubtSense Doubt History & Session Analysis"
                                    )
                                },
                                modifier = Modifier.testTag("history_export_txt")
                            )
                        }
                    }

                    IconButton(
                        onClick = { showClearConfirmDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear all logs",
                            tint = RoseError.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Top Search Bar (filters by semantic concepts, keyword, or date)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Semantic or keyword search (e.g. 'fast code', 'Today', 'Trees')...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search sessions and doubts by concept, keyword, or date",
                    tint = if (searchQuery.isNotEmpty()) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("clear_search_query_button")
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search query")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("history_search_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Segmented Switch: Sessions | Doubts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sessions (${filteredSessions.size})", fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ElectricCyan.copy(alpha = 0.22f),
                    selectedLabelColor = ElectricCyan
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("tab_sessions")
            )
            FilterChip(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Doubt Logs (${filteredDoubts.size})", fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonViolet.copy(alpha = 0.22f),
                    selectedLabelColor = NeonViolet
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("tab_doubts")
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Date Filter & Category Chip Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DateFilterOption.entries.forEach { option ->
                val isSelected = dateFilter == option
                FilterChip(
                    selected = isSelected,
                    onClick = { dateFilter = option },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(option.label, fontSize = 11.sp)
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                        selectedLabelColor = ElectricCyan
                    ),
                    modifier = Modifier.testTag("date_chip_${option.name.lowercase()}")
                )
            }

            FilterChip(
                selected = showOnlyStarred,
                onClick = { showOnlyStarred = !showOnlyStarred },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pinned", fontSize = 11.sp)
                    }
                },
                modifier = Modifier.testTag("filter_starred")
            )

            if (selectedCategory != null) {
                FilterChip(
                    selected = true,
                    onClick = {
                        selectedCategory = null
                        onClearInitialCategory()
                    },
                    label = { Text("Category: $selectedCategory ✕", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonViolet.copy(alpha = 0.25f),
                        selectedLabelColor = NeonViolet
                    ),
                    modifier = Modifier.testTag("active_category_filter_chip")
                )
            }

            if (selectedTab == 1) {
                FilterChip(
                    selected = showOnlyAmbiguous,
                    onClick = { showOnlyAmbiguous = !showOnlyAmbiguous },
                    label = { Text("Clarifications", fontSize = 11.sp) },
                    modifier = Modifier.testTag("filter_ambiguous")
                )

                topicFrequencies.take(6).forEach { (topic, count) ->
                    val isTopicSel = selectedTopic == topic
                    FilterChip(
                        selected = isTopicSel,
                        onClick = { selectedTopic = if (isTopicSel) null else topic },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Topic, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("$topic ($count)", fontSize = 11.sp)
                            }
                        },
                        modifier = Modifier.testTag("topic_chip_$topic")
                    )
                }
            }

            if (isFilterActive) {
                TextButton(
                    onClick = {
                        searchQuery = ""
                        selectedCategory = null
                        selectedTagFilter = null
                        selectedTopic = null
                        dateFilter = DateFilterOption.ALL
                        showOnlyStarred = false
                        showOnlyAmbiguous = false
                        onClearInitialCategory()
                        onClearInitialTag()
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("reset_all_filters_button")
                ) {
                    Text("Reset", fontSize = 11.sp, color = ElectricCyan)
                }
            }
        }

        // Tag-based Categories Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tags:",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            )
            FilterChip(
                selected = selectedTagFilter == null,
                onClick = {
                    selectedTagFilter = null
                    onClearInitialTag()
                },
                label = { Text("All", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                    selectedLabelColor = ElectricCyan
                ),
                modifier = Modifier.testTag("history_tag_chip_all")
            )
            allAvailableTagLabels.forEach { tag ->
                val isTagSel = selectedTagFilter.equals(tag, ignoreCase = true)
                val accent = tagAccentColor(tag)
                val count = sessionCountsByTag[tag] ?: 0
                FilterChip(
                    selected = isTagSel,
                    onClick = { selectedTagFilter = if (isTagSel) null else tag },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.AutoMirrored.Filled.Label,
                                contentDescription = null,
                                tint = if (isTagSel) accent else accent.copy(alpha = 0.8f),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (count > 0) "$tag ($count)" else tag,
                                fontSize = 11.sp
                            )
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent.copy(alpha = 0.22f),
                        selectedLabelColor = accent
                    ),
                    modifier = Modifier.testTag("history_tag_chip_$tag")
                )
            }
            FilterChip(
                selected = false,
                onClick = { showTagManagerDialog = true },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create or manage custom labels",
                            tint = ElectricCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Tag", fontSize = 11.sp, color = ElectricCyan, fontWeight = FontWeight.SemiBold)
                    }
                },
                modifier = Modifier.testTag("history_add_tag_button")
            )
        }

        // Collapsible Trend Chart
        AnimatedVisibility(
            visible = showTrendChart,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            DoubtIntensityTrendChart(
                doubts = allDoubts,
                sessions = allSessions,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        if (sessionExportProgress != null) {
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (sessionExportProgress ?: 0f).coerceIn(0f, 1f) },
                color = ElectricCyan,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_session_export_progress")
            )
        }

        if (!sessionExportStatusMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = EmeraldSuccess.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("session_export_status_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = sessionExportStatusMessage ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = EmeraldSuccess,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Main Adaptive Content Area
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val useExpandedListDetail = maxWidth >= 840.dp

            if (selectedTab == 0) {
                val syncBanner: @Composable () -> Unit = {
                    HistoryCloudSyncBanner(
                        cloudSyncState = cloudSyncState,
                        isAuthActionBusy = isAuthActionBusy,
                        authActionMessage = authActionMessage,
                        onSignInClick = {
                            val auth = CloudSyncManager.resolveFirebaseAuthOrNull()
                            if (auth == null) {
                                authActionMessage = "Google Sign-In unavailable in this environment."
                            } else {
                                coroutineScope.launch {
                                    isAuthActionBusy = true
                                    when (val outcome = GoogleAuthHelper.signInInteractive(context, auth)) {
                                        is GoogleSignInOutcome.Success -> {
                                            authActionMessage = null
                                            CloudSyncManager.bindAuthenticatedSession(context, repository, outcome.user)
                                        }
                                        is GoogleSignInOutcome.Cancelled -> {
                                            authActionMessage = "Sign-in cancelled"
                                        }
                                        is GoogleSignInOutcome.Error -> {
                                            authActionMessage = outcome.message
                                        }
                                    }
                                    isAuthActionBusy = false
                                }
                            }
                        },
                        onSyncNowClick = {
                            coroutineScope.launch {
                                isAuthActionBusy = true
                                val res = CloudSyncManager.syncNow(context)
                                res.onFailure { err ->
                                    authActionMessage = err.localizedMessage
                                }
                                isAuthActionBusy = false
                            }
                        }
                    )
                }

                if (filteredSessions.isEmpty()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        syncBanner()
                        Spacer(modifier = Modifier.height(6.dp))
                        EmptyHistoryState(
                            isFilterActive = isFilterActive,
                            emptyTitle = "No past conversation sessions recorded",
                            emptyBody = "Speak in Live Sense or ask in Answer Engine to store sessions and revisit past AI-generated answers here.",
                            searchQuery = searchQuery,
                            onResetFilters = {
                                searchQuery = ""
                                selectedCategory = null
                                selectedTagFilter = null
                                dateFilter = DateFilterOption.ALL
                                showOnlyStarred = false
                                onClearInitialCategory()
                                onClearInitialTag()
                            },
                            onSeedSamples = { ListeningStateManager.seedSampleSessions(context) },
                            showSeedButton = allSessions.isEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                } else if (useExpandedListDetail) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(0.45f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredSessions, key = { it.id }) { session ->
                                SwipeToDeleteContainer(
                                    itemKey = "session_${session.id}",
                                    cornerRadius = 20.dp,
                                    onDelete = {
                                        if (selectedSessionIdForDetail == session.id) {
                                            selectedSessionIdForDetail = null
                                        }
                                        coroutineScope.launch(Dispatchers.IO) {
                                            repository.deleteSession(session.id)
                                        }
                                    },
                                    backgroundTestTag = "session_swipe_bg_${session.id}"
                                ) {
                                    SessionLogCard(
                                        session = session,
                                        searchQuery = searchQuery,
                                        availableTags = allAvailableTagLabels,
                                        isSelected = selectedSessionForDetail?.id == session.id,
                                        onOpenDetail = { selectedSessionIdForDetail = session.id },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            pendingDeleteSession = session
                                        },
                                        onTogglePin = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            coroutineScope.launch(Dispatchers.IO) {
                                                repository.updateSession(session.copy(isPinned = !session.isPinned))
                                            }
                                        },
                                        onAssignTag = { newTag ->
                                            coroutineScope.launch(Dispatchers.IO) {
                                                repository.updateSession(session.copy(customTag = newTag))
                                            }
                                        },
                                        onCreateCustomTag = { newTag ->
                                            AppPreferencesManager.addCustomTag(context, newTag)
                                        },
                                        onExportPdf = {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                val doubts = repository.getDoubtsForSessionOnce(session.id)
                                                launch(Dispatchers.Main) {
                                                    ShareHelper.exportSession(context, session, doubts, ExportDocumentFormat.PDF)
                                                }
                                            }
                                        },
                                        onExportText = {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                val doubts = repository.getDoubtsForSessionOnce(session.id)
                                                launch(Dispatchers.Main) {
                                                    ShareHelper.exportSession(context, session, doubts, ExportDocumentFormat.STRUCTURED_TEXT)
                                                }
                                            }
                                        },
                                        onSaveSessionAsText = {
                                            launchSaveSession(session, ExportDocumentFormat.STRUCTURED_TEXT)
                                        },
                                        onSaveSessionAsPdf = {
                                            launchSaveSession(session, ExportDocumentFormat.PDF)
                                        },
                                        onDelete = {
                                            if (selectedSessionIdForDetail == session.id) {
                                                selectedSessionIdForDetail = null
                                            }
                                            coroutineScope.launch(Dispatchers.IO) {
                                                repository.deleteSession(session.id)
                                            }
                                        }
                                    )
                                }
                            }
                            item(key = "history_cloud_sync_banner") {
                                syncBanner()
                            }
                            item { Spacer(modifier = Modifier.height(24.dp)) }
                        }

                        SessionDetailPane(
                            session = selectedSessionForDetail,
                            repository = repository,
                            availableTags = allAvailableTagLabels,
                            exportProgress = sessionExportProgress,
                            onToggleSessionPin = { s ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    repository.updateSession(s.copy(isPinned = !s.isPinned))
                                }
                            },
                            onAssignSessionTag = { s, newTag ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    repository.updateSession(s.copy(customTag = newTag))
                                }
                            },
                            onCreateCustomTag = { newTag ->
                                AppPreferencesManager.addCustomTag(context, newTag)
                            },
                            onToggleDoubtStar = { doubt ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    repository.updateDoubt(doubt.copy(isStarred = !doubt.isStarred))
                                }
                            },
                            onRevisitDoubtInAnswerEngine = onRevisitDoubtInAnswerEngine,
                            onSaveSessionAsText = { s ->
                                launchSaveSession(s, ExportDocumentFormat.STRUCTURED_TEXT)
                            },
                            onSaveSessionAsPdf = { s ->
                                launchSaveSession(s, ExportDocumentFormat.PDF)
                            },
                            onContinueInChat = onContinueSessionInChat,
                            onClearSelection = { selectedSessionIdForDetail = null },
                            modifier = Modifier
                                .weight(0.55f)
                                .fillMaxHeight()
                                .padding(bottom = 16.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("sessions_lazy_column"),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredSessions, key = { it.id }) { session ->
                            SwipeToDeleteContainer(
                                itemKey = "session_${session.id}",
                                cornerRadius = 20.dp,
                                onDelete = {
                                    if (selectedSessionIdForDetail == session.id) {
                                        selectedSessionIdForDetail = null
                                    }
                                    coroutineScope.launch(Dispatchers.IO) {
                                        repository.deleteSession(session.id)
                                    }
                                },
                                backgroundTestTag = "session_swipe_bg_${session.id}"
                            ) {
                                SessionLogCard(
                                    session = session,
                                    searchQuery = searchQuery,
                                    availableTags = allAvailableTagLabels,
                                    isSelected = selectedSessionForDetail?.id == session.id,
                                    onOpenDetail = { selectedSessionIdForDetail = session.id },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        pendingDeleteSession = session
                                    },
                                    onTogglePin = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        coroutineScope.launch(Dispatchers.IO) {
                                            repository.updateSession(session.copy(isPinned = !session.isPinned))
                                        }
                                    },
                                    onAssignTag = { newTag ->
                                        coroutineScope.launch(Dispatchers.IO) {
                                            repository.updateSession(session.copy(customTag = newTag))
                                        }
                                    },
                                    onCreateCustomTag = { newTag ->
                                        AppPreferencesManager.addCustomTag(context, newTag)
                                    },
                                    onExportPdf = {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val doubts = repository.getDoubtsForSessionOnce(session.id)
                                            launch(Dispatchers.Main) {
                                                ShareHelper.exportSession(context, session, doubts, ExportDocumentFormat.PDF)
                                            }
                                        }
                                    },
                                    onExportText = {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val doubts = repository.getDoubtsForSessionOnce(session.id)
                                            launch(Dispatchers.Main) {
                                                ShareHelper.exportSession(context, session, doubts, ExportDocumentFormat.STRUCTURED_TEXT)
                                            }
                                        }
                                    },
                                    onSaveSessionAsText = {
                                        launchSaveSession(session, ExportDocumentFormat.STRUCTURED_TEXT)
                                    },
                                    onSaveSessionAsPdf = {
                                        launchSaveSession(session, ExportDocumentFormat.PDF)
                                    },
                                    onDelete = {
                                        if (selectedSessionIdForDetail == session.id) {
                                            selectedSessionIdForDetail = null
                                        }
                                        coroutineScope.launch(Dispatchers.IO) {
                                            repository.deleteSession(session.id)
                                        }
                                    }
                                )
                            }
                        }
                        item(key = "history_cloud_sync_banner") {
                            syncBanner()
                        }
                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }

                    // Compact/Medium Modal Session Detail Dialog
                    selectedSessionForDetail?.let { session ->
                        SessionDetailDialog(
                            session = session,
                            repository = repository,
                            availableTags = allAvailableTagLabels,
                            exportProgress = sessionExportProgress,
                            onToggleSessionPin = { s ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    repository.updateSession(s.copy(isPinned = !s.isPinned))
                                }
                            },
                            onAssignSessionTag = { s, newTag ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    repository.updateSession(s.copy(customTag = newTag))
                                }
                            },
                            onCreateCustomTag = { newTag ->
                                AppPreferencesManager.addCustomTag(context, newTag)
                            },
                            onToggleDoubtStar = { doubt ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    repository.updateDoubt(doubt.copy(isStarred = !doubt.isStarred))
                                }
                            },
                            onRevisitDoubtInAnswerEngine = onRevisitDoubtInAnswerEngine,
                            onSaveSessionAsText = { s ->
                                launchSaveSession(s, ExportDocumentFormat.STRUCTURED_TEXT)
                            },
                            onSaveSessionAsPdf = { s ->
                                launchSaveSession(s, ExportDocumentFormat.PDF)
                            },
                            onContinueInChat = onContinueSessionInChat,
                            onDismiss = { selectedSessionIdForDetail = null }
                        )
                    }
                }
            } else {
                val syncBanner: @Composable () -> Unit = {
                    HistoryCloudSyncBanner(
                        cloudSyncState = cloudSyncState,
                        isAuthActionBusy = isAuthActionBusy,
                        authActionMessage = authActionMessage,
                        onSignInClick = {
                            val auth = CloudSyncManager.resolveFirebaseAuthOrNull()
                            if (auth == null) {
                                authActionMessage = "Google Sign-In unavailable in this environment."
                            } else {
                                coroutineScope.launch {
                                    isAuthActionBusy = true
                                    when (val outcome = GoogleAuthHelper.signInInteractive(context, auth)) {
                                        is GoogleSignInOutcome.Success -> {
                                            authActionMessage = null
                                            CloudSyncManager.bindAuthenticatedSession(context, repository, outcome.user)
                                        }
                                        is GoogleSignInOutcome.Cancelled -> {
                                            authActionMessage = "Sign-in cancelled"
                                        }
                                        is GoogleSignInOutcome.Error -> {
                                            authActionMessage = outcome.message
                                        }
                                    }
                                    isAuthActionBusy = false
                                }
                            }
                        },
                        onSyncNowClick = {
                            coroutineScope.launch {
                                isAuthActionBusy = true
                                val res = CloudSyncManager.syncNow(context)
                                res.onFailure { err ->
                                    authActionMessage = err.localizedMessage
                                }
                                isAuthActionBusy = false
                            }
                        }
                    )
                }

                if (filteredDoubts.isEmpty()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        syncBanner()
                        Spacer(modifier = Modifier.height(6.dp))
                        EmptyHistoryState(
                            isFilterActive = isFilterActive,
                            emptyTitle = "No doubts in log yet",
                            emptyBody = "Detected doubts, quick answers, and references will appear here automatically.",
                            searchQuery = searchQuery,
                            onResetFilters = {
                                searchQuery = ""
                                selectedCategory = null
                                selectedTagFilter = null
                                selectedTopic = null
                                dateFilter = DateFilterOption.ALL
                                showOnlyStarred = false
                                showOnlyAmbiguous = false
                                onClearInitialCategory()
                            },
                            onSeedSamples = { ListeningStateManager.seedSampleSessions(context) },
                            showSeedButton = allDoubts.isEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(filteredDoubts, key = { it.id }) { doubt ->
                            val shouldExpand = targetDoubtId == doubt.id
                            SwipeToDeleteContainer(
                                itemKey = "doubt_${doubt.id}",
                                cornerRadius = 20.dp,
                                onDelete = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        repository.deleteDoubt(doubt.id)
                                    }
                                },
                                backgroundTestTag = "doubt_swipe_bg_${doubt.id}"
                            ) {
                                DoubtCard(
                                    doubt = doubt,
                                    initialExpandedDetailed = shouldExpand,
                                    searchQuery = searchQuery,
                                    availableTags = customTags,
                                    isSummarizing = doubt.id in summarizingDoubtIds,
                                    onToggleStar = { item ->
                                        coroutineScope.launch(Dispatchers.IO) {
                                            repository.updateDoubt(item.copy(isStarred = !item.isStarred))
                                        }
                                    },
                                    onAssignTag = { item, newTag ->
                                        coroutineScope.launch(Dispatchers.IO) {
                                            repository.updateDoubt(item.copy(customTag = newTag))
                                        }
                                    },
                                    onGenerateSummary = { item ->
                                        summarizingDoubtIds = summarizingDoubtIds + item.id
                                        coroutineScope.launch {
                                            try {
                                                val summary = withContext(Dispatchers.IO) {
                                                    answerEngine.generateOneSentenceSummary(
                                                        question = item.doubtSnippet,
                                                        answer = item.quickAnswer,
                                                        language = aiLanguage
                                                    )
                                                }
                                                withContext(Dispatchers.IO) {
                                                    repository.updateDoubt(item.copy(oneSentenceSummary = summary))
                                                }
                                            } finally {
                                                summarizingDoubtIds = summarizingDoubtIds - item.id
                                            }
                                        }
                                    },
                                    onDelete = { item ->
                                        coroutineScope.launch(Dispatchers.IO) {
                                            repository.deleteDoubt(item.id)
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        pendingDeleteDoubt = doubt
                                    }
                                )
                            }
                        }
                        item(key = "history_cloud_sync_banner") {
                            syncBanner()
                        }
                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }

    // Clear All Confirmation
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear All Conversation Logs?") },
            text = { Text("This will permanently delete all stored conversation sessions and detected doubt analyses.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedSessionIdForDetail = null
                        coroutineScope.launch(Dispatchers.IO) {
                            repository.clearAll()
                        }
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = RoseError)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Long-press single item delete confirmation
    pendingDeleteSession?.let { sessionToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteSession = null },
            modifier = Modifier.testTag("confirm_delete_log_dialog"),
            title = { Text(stringResource(R.string.delete_history_dialog_title)) },
            text = {
                Text("Delete conversation session \"${sessionToDelete.title.ifBlank { "Conversation Session" }}\" from local history?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (selectedSessionIdForDetail == sessionToDelete.id) {
                            selectedSessionIdForDetail = null
                        }
                        coroutineScope.launch(Dispatchers.IO) {
                            repository.deleteSession(sessionToDelete.id)
                        }
                        pendingDeleteSession = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = RoseError),
                    modifier = Modifier.testTag("confirm_delete_log_button")
                ) {
                    Text(stringResource(R.string.action_delete_item))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeleteSession = null },
                    modifier = Modifier.testTag("cancel_delete_log_button")
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    pendingDeleteDoubt?.let { doubtToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteDoubt = null },
            modifier = Modifier.testTag("confirm_delete_log_dialog"),
            title = { Text(stringResource(R.string.delete_history_dialog_title)) },
            text = {
                Text("Delete saved doubt \"${doubtToDelete.doubtSnippet}\" from local history?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            repository.deleteDoubt(doubtToDelete.id)
                        }
                        pendingDeleteDoubt = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = RoseError),
                    modifier = Modifier.testTag("confirm_delete_log_button")
                ) {
                    Text(stringResource(R.string.action_delete_item))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeleteDoubt = null },
                    modifier = Modifier.testTag("cancel_delete_log_button")
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showTagManagerDialog) {
        HistoryTagManagerDialog(
            tags = allAvailableTagLabels,
            sessionCountsByTag = sessionCountsByTag,
            onAddTag = { newTag ->
                AppPreferencesManager.addCustomTag(context, newTag)
            },
            onDeleteTag = { tagToRemove ->
                if (selectedTagFilter.equals(tagToRemove, ignoreCase = true)) {
                    selectedTagFilter = null
                }
                AppPreferencesManager.removeCustomTag(context, tagToRemove)
            },
            onSelectFilterTag = { tag ->
                selectedTagFilter = tag
                showTagManagerDialog = false
            },
            onDismiss = { showTagManagerDialog = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryTagManagerDialog(
    tags: List<String>,
    sessionCountsByTag: Map<String, Int>,
    onAddTag: (String) -> Unit,
    onDeleteTag: (String) -> Unit,
    onSelectFilterTag: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var draftTag by remember { mutableStateOf("") }
    val quickSuggestions = remember(tags) {
        listOf("Interview", "Research", "Revision", "Client", "Project")
            .filter { suggestion -> tags.none { it.equals(suggestion, ignoreCase = true) } }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 580.dp)
                .testTag("history_tag_manager_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Custom Session Labels",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Create custom labels to organize your saved doubt sessions (or tap a label to filter).",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = draftTag,
                            onValueChange = { draftTag = it.take(28) },
                            placeholder = { Text("New label name...", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("history_new_tag_input")
                        )

                        FilledTonalButton(
                            onClick = {
                                if (draftTag.isNotBlank()) {
                                    onAddTag(draftTag)
                                    draftTag = ""
                                }
                            },
                            enabled = draftTag.isNotBlank(),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("history_confirm_add_tag_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add", fontSize = 12.sp)
                        }
                    }

                    if (quickSuggestions.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            quickSuggestions.forEach { suggestion ->
                                FilterChip(
                                    selected = false,
                                    onClick = { onAddTag(suggestion) },
                                    label = { Text("+ $suggestion", fontSize = 11.sp) },
                                    modifier = Modifier.testTag("history_quick_tag_suggestion_$suggestion")
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tags.forEach { tag ->
                            val accent = tagAccentColor(tag)
                            val count = sessionCountsByTag[tag] ?: 0
                            val canDelete = !AppPreferencesManager.isDefaultTag(tag)
                            Surface(
                                onClick = { onSelectFilterTag(tag) },
                                color = accent.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(0.75.dp, accent.copy(alpha = 0.45f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        start = 10.dp,
                                        end = if (canDelete) 4.dp else 10.dp,
                                        top = 6.dp,
                                        bottom = 6.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Label,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "$tag ($count)",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = accent,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    if (canDelete) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { onDeleteTag(tag) },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .testTag("history_delete_custom_tag_$tag")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove custom label $tag",
                                                tint = accent,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            if (draftTag.isNotBlank()) {
                                onAddTag(draftTag)
                            }
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Slate950),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("history_tag_manager_done_button")
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCloudSyncBanner(
    cloudSyncState: CloudSyncUiState,
    isAuthActionBusy: Boolean,
    authActionMessage: String?,
    onSignInClick: () -> Unit,
    onSyncNowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = when (cloudSyncState) {
            is CloudSyncUiState.Synced -> EmeraldSuccess.copy(alpha = 0.12f)
            is CloudSyncUiState.Error -> RoseError.copy(alpha = 0.12f)
            is CloudSyncUiState.Syncing -> ElectricCyan.copy(alpha = 0.14f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(
            1.dp,
            when (cloudSyncState) {
                is CloudSyncUiState.Synced -> EmeraldSuccess.copy(alpha = 0.4f)
                is CloudSyncUiState.Error -> RoseError.copy(alpha = 0.45f)
                is CloudSyncUiState.Syncing -> ElectricCyan.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("history_cloud_sync_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                when (cloudSyncState) {
                    is CloudSyncUiState.Syncing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = ElectricCyan
                        )
                    }
                    is CloudSyncUiState.Synced -> {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = stringResource(R.string.cloud_sync_status_synced),
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    is CloudSyncUiState.Error -> {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "Cloud sync error",
                            tint = RoseError,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = stringResource(R.string.cloud_sync_status_signed_out),
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                val titleText = when (val st = cloudSyncState) {
                    is CloudSyncUiState.Synced ->
                        "Synced across devices${st.userEmail?.let { " • $it" } ?: ""}"
                    is CloudSyncUiState.Syncing -> st.message
                    is CloudSyncUiState.OfflinePaused ->
                        "Sync paused${st.userEmail?.let { " ($it)" } ?: ""}"
                    is CloudSyncUiState.Error -> st.message
                    CloudSyncUiState.SignedOut ->
                        authActionMessage ?: stringResource(R.string.cloud_sync_status_signed_out)
                }
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (cloudSyncState is CloudSyncUiState.SignedOut) {
                FilledTonalButton(
                    onClick = onSignInClick,
                    enabled = !isAuthActionBusy,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = ElectricCyan.copy(alpha = 0.2f),
                        contentColor = ElectricCyan
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("history_google_signin_button")
                ) {
                    Text(stringResource(R.string.cloud_sync_sign_in), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = onSyncNowClick,
                    enabled = !isAuthActionBusy && cloudSyncState !is CloudSyncUiState.Syncing,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("history_sync_now_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = stringResource(R.string.cloud_sync_now),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.cloud_sync_now), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryState(
    isFilterActive: Boolean,
    emptyTitle: String,
    emptyBody: String,
    searchQuery: String,
    onResetFilters: () -> Unit,
    onSeedSamples: () -> Unit,
    showSeedButton: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (isFilterActive) Icons.Default.Search else Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.size(52.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (searchQuery.isNotBlank()) "No logs matching \"$searchQuery\"" else emptyTitle,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isFilterActive) {
                    "Try searching a different keyword or date (e.g. 'Today', 'Nov 12'), or clear your filters."
                } else {
                    emptyBody
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    lineHeight = 18.sp
                )
            )
            Spacer(modifier = Modifier.height(14.dp))
            if (isFilterActive) {
                OutlinedButton(
                    onClick = onResetFilters,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("empty_state_reset_filters")
                ) {
                    Text("Clear Search & Filters")
                }
            } else if (showSeedButton) {
                Button(
                    onClick = onSeedSamples,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = Slate950
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("history_seed_samples_button")
                ) {
                    Text("Load Sample Study Sessions", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// =========================================================================
// Pure-Kotlin Date & Keyword Search Helpers (unit-tested in ExampleUnitTest)
// =========================================================================

internal fun matchesDateFilter(
    timestamp: Long,
    filter: DateFilterOption,
    nowMillis: Long = System.currentTimeMillis()
): Boolean {
    return when (filter) {
        DateFilterOption.ALL -> true
        DateFilterOption.TODAY -> isSameDay(timestamp, nowMillis)
        DateFilterOption.LAST_7_DAYS -> timestamp >= (nowMillis - 7L * 86_400_000L)
    }
}

internal fun isSameDay(ts1: Long, ts2: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = ts1 }
    val c2 = Calendar.getInstance().apply { timeInMillis = ts2 }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
        c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}

internal fun formatSearchableDateTokens(
    timestamp: Long,
    nowMillis: Long = System.currentTimeMillis()
): String {
    val date = Date(timestamp)
    val fullFmt = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(date)
    val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
    val shortFmt = SimpleDateFormat("MMM d", Locale.US).format(date)
    val dayFmt = SimpleDateFormat("EEEE", Locale.US).format(date)
    val rel = when {
        isSameDay(timestamp, nowMillis) -> "Today"
        isSameDay(timestamp, nowMillis - 86_400_000L) -> "Yesterday"
        else -> ""
    }
    return "$fullFmt $isoFmt $shortFmt $dayFmt $rel".trim()
}

internal fun matchesSessionQuery(
    session: ConversationSession,
    query: String,
    nowMillis: Long = System.currentTimeMillis()
): Boolean {
    if (query.isBlank()) return true
    val q = query.trim()
    val dateTokens = formatSearchableDateTokens(session.startTime, nowMillis)
    return session.title.contains(q, ignoreCase = true) ||
        session.category.contains(q, ignoreCase = true) ||
        session.customTag.contains(q, ignoreCase = true) ||
        session.transcriptPreview.contains(q, ignoreCase = true) ||
        session.transcriptJson.contains(q, ignoreCase = true) ||
        session.summary.contains(q, ignoreCase = true) ||
        dateTokens.contains(q, ignoreCase = true)
}

internal fun matchesDoubtQuery(
    doubt: DoubtItem,
    query: String,
    nowMillis: Long = System.currentTimeMillis()
): Boolean {
    if (query.isBlank()) return true
    val q = query.trim()
    val dateTokens = formatSearchableDateTokens(doubt.timestamp, nowMillis)
    return doubt.topic.contains(q, ignoreCase = true) ||
        doubt.subject.contains(q, ignoreCase = true) ||
        doubt.customTag.contains(q, ignoreCase = true) ||
        doubt.doubtSnippet.contains(q, ignoreCase = true) ||
        doubt.quickAnswer.contains(q, ignoreCase = true) ||
        doubt.detailedAnswer.contains(q, ignoreCase = true) ||
        doubt.notificationSummary.contains(q, ignoreCase = true) ||
        doubt.conversationalContext.contains(q, ignoreCase = true) ||
        dateTokens.contains(q, ignoreCase = true)
}
