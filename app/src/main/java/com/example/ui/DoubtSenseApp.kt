package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.AnswerEngineResult
import com.example.service.ListeningStateManager
import com.example.ui.screens.AnswerEngineScreen
import com.example.ui.screens.CategoryDashboardScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.Slate950
import com.example.util.DocumentExportHelper
import com.example.util.ExportDocumentFormat
import com.example.util.ShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppDestination(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    CONVERSATION("Live Sense", Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq, "nav_dashboard"),
    ANSWER_ENGINE("Answer", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_answer_engine"),
    CATEGORY_DASHBOARD("Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "nav_category_dashboard"),
    HISTORY("Logs", Icons.Filled.History, Icons.Outlined.History, "nav_history"),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
}

@Composable
fun DoubtSenseApp(
    targetDoubtId: Long? = null,
    openHistoryTrigger: Int = 0,
    initialDestination: AppDestination? = null,
    shortcutTrigger: Int = 0,
    autoStartVoiceTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentDestination by remember { mutableStateOf(initialDestination ?: AppDestination.CONVERSATION) }
    var focusedDoubtId by remember { mutableStateOf(targetDoubtId) }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var selectedTagFilter by remember { mutableStateOf<String?>(null) }
    var showFabExportMenu by remember { mutableStateOf(false) }
    var revisitedAnswerResult by remember { mutableStateOf<AnswerEngineResult?>(null) }
    var revisitTrigger by remember { mutableIntStateOf(0) }

    val conversationHistory by ListeningStateManager.conversationHistory.collectAsState()
    val latestDoubt by ListeningStateManager.latestDetectedDoubt.collectAsState()
    val repository = remember { ListeningStateManager.getRepository(context) }
    val recentDoubts by repository.allDoubts.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    fun currentLiveSessionDoubts() = if (latestDoubt != null) {
        listOf(latestDoubt!!) + recentDoubts.filter { it.id != latestDoubt!!.id }.take(4)
    } else {
        recentDoubts.take(5)
    }

    fun currentLiveSessionSnapshot() = listOfNotNull(ListeningStateManager.getLiveSessionAnalysisSnapshot())

    val saveFabSessionTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { targetUri ->
        if (targetUri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                DocumentExportHelper.writeExportToUri(
                    context = context,
                    targetUri = targetUri,
                    conversationTurns = conversationHistory,
                    doubts = currentLiveSessionDoubts(),
                    format = ExportDocumentFormat.STRUCTURED_TEXT,
                    documentTitle = "DoubtSense Live Session & Analysis",
                    sessions = currentLiveSessionSnapshot()
                )
            }
        }
    }

    val saveFabSessionPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { targetUri ->
        if (targetUri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                DocumentExportHelper.writeExportToUri(
                    context = context,
                    targetUri = targetUri,
                    conversationTurns = conversationHistory,
                    doubts = currentLiveSessionDoubts(),
                    format = ExportDocumentFormat.PDF,
                    documentTitle = "DoubtSense Live Session & Analysis",
                    sessions = currentLiveSessionSnapshot()
                )
            }
        }
    }

    LaunchedEffect(targetDoubtId) {
        if (targetDoubtId != null && targetDoubtId > 0) {
            focusedDoubtId = targetDoubtId
            currentDestination = AppDestination.HISTORY
        }
    }

    LaunchedEffect(openHistoryTrigger) {
        if (openHistoryTrigger > 0) {
            focusedDoubtId = null
            selectedCategoryFilter = null
            currentDestination = AppDestination.HISTORY
        }
    }

    LaunchedEffect(shortcutTrigger, initialDestination) {
        if (shortcutTrigger > 0 && initialDestination != null) {
            focusedDoubtId = null
            selectedCategoryFilter = null
            currentDestination = initialDestination
        }
    }

    // BackHandler to return to Live Sense conversation view if in other tabs
    if (currentDestination != AppDestination.CONVERSATION) {
        BackHandler {
            currentDestination = AppDestination.CONVERSATION
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        floatingActionButton = {
            if (currentDestination == AppDestination.CONVERSATION) {
                Box {
                    ExtendedFloatingActionButton(
                        onClick = { showFabExportMenu = true },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Export Current Session"
                            )
                        },
                        text = {
                            Text(
                                text = "Export Session",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        containerColor = ElectricCyan,
                        contentColor = Slate950,
                        modifier = Modifier.testTag("export_session_fab")
                    )

                    DropdownMenu(
                        expanded = showFabExportMenu,
                        onDismissRequest = { showFabExportMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("💾 Save Session as Text (.txt)") },
                            onClick = {
                                showFabExportMenu = false
                                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                saveFabSessionTxtLauncher.launch("doubtsense_live_session_$ts.txt")
                            },
                            modifier = Modifier.testTag("fab_save_session_txt")
                        )
                        DropdownMenuItem(
                            text = { Text("💾 Save Session as PDF (.pdf)") },
                            onClick = {
                                showFabExportMenu = false
                                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                saveFabSessionPdfLauncher.launch("doubtsense_live_session_$ts.pdf")
                            },
                            modifier = Modifier.testTag("fab_save_session_pdf")
                        )
                        DropdownMenuItem(
                            text = { Text("📄 Export Session as PDF") },
                            onClick = {
                                showFabExportMenu = false
                                ShareHelper.exportFullConversation(
                                    context = context,
                                    conversationTurns = conversationHistory,
                                    doubts = currentLiveSessionDoubts(),
                                    format = ExportDocumentFormat.PDF,
                                    title = "DoubtSense Live Session & Analysis",
                                    sessions = currentLiveSessionSnapshot()
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("🖨️ Print / Save Printable PDF") },
                            onClick = {
                                showFabExportMenu = false
                                val sessionDoubts = currentLiveSessionDoubts()
                                DocumentExportHelper.printPdfDocument(
                                    context = context,
                                    conversationTurns = conversationHistory,
                                    doubts = sessionDoubts,
                                    documentTitle = "DoubtSense Live Session & Analysis",
                                    sessions = currentLiveSessionSnapshot()
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("📊 Export Session as CSV (.csv)") },
                            onClick = {
                                showFabExportMenu = false
                                val sessionDoubts = currentLiveSessionDoubts()
                                ShareHelper.exportFullConversation(
                                    context = context,
                                    conversationTurns = conversationHistory,
                                    doubts = sessionDoubts,
                                    format = ExportDocumentFormat.CSV,
                                    title = "DoubtSense Live Session Digest",
                                    sessions = currentLiveSessionSnapshot()
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("📝 Export as Structured Text (.txt)") },
                            onClick = {
                                showFabExportMenu = false
                                val sessionDoubts = currentLiveSessionDoubts()
                                ShareHelper.exportFullConversation(
                                    context = context,
                                    conversationTurns = conversationHistory,
                                    doubts = sessionDoubts,
                                    format = ExportDocumentFormat.STRUCTURED_TEXT,
                                    title = "DoubtSense Live Session & Analysis",
                                    sessions = currentLiveSessionSnapshot()
                                )
                            }
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("bottom_nav_bar")
            ) {
                AppDestination.entries.forEach { destination ->
                    val isSelected = currentDestination == destination
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            if (destination != AppDestination.HISTORY) {
                                selectedCategoryFilter = null
                                selectedTagFilter = null
                            }
                            currentDestination = destination
                        },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.title
                            )
                        },
                        label = {
                            Text(
                                text = destination.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag(destination.testTag)
                    )
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Crossfade(
            targetState = currentDestination,
            label = "screen_crossfade",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { destination ->
            when (destination) {
                AppDestination.CONVERSATION -> {
                    DashboardScreen(
                        onNavigateToHistory = {
                            currentDestination = AppDestination.HISTORY
                        }
                    )
                }
                AppDestination.ANSWER_ENGINE -> {
                    AnswerEngineScreen(
                        autoStartVoiceTrigger = autoStartVoiceTrigger,
                        revisitedResult = revisitedAnswerResult,
                        revisitTrigger = revisitTrigger
                    )
                }
                AppDestination.CATEGORY_DASHBOARD -> {
                    CategoryDashboardScreen(
                        onSelectCategory = { categoryName ->
                            focusedDoubtId = null
                            selectedTagFilter = null
                            selectedCategoryFilter = categoryName
                            currentDestination = AppDestination.HISTORY
                        },
                        onSelectTag = { tagName ->
                            focusedDoubtId = null
                            selectedCategoryFilter = null
                            selectedTagFilter = tagName
                            currentDestination = AppDestination.HISTORY
                        },
                        onInspectDoubt = { doubtId ->
                            selectedCategoryFilter = null
                            selectedTagFilter = null
                            focusedDoubtId = doubtId
                            currentDestination = AppDestination.HISTORY
                        },
                        onNavigateToLiveSense = {
                            currentDestination = AppDestination.CONVERSATION
                        }
                    )
                }
                AppDestination.HISTORY -> {
                    HistoryScreen(
                        targetDoubtId = focusedDoubtId,
                        initialCategoryFilter = selectedCategoryFilter,
                        onClearInitialCategory = { selectedCategoryFilter = null },
                        initialTagFilter = selectedTagFilter,
                        onClearInitialTag = { selectedTagFilter = null },
                        onRevisitDoubtInAnswerEngine = { doubt ->
                            revisitedAnswerResult = AnswerEngineResult.fromDoubtItem(doubt)
                            revisitTrigger++
                            currentDestination = AppDestination.ANSWER_ENGINE
                        }
                    )
                }
                AppDestination.SETTINGS -> {
                    SettingsScreen()
                }
            }
        }
    }
}
