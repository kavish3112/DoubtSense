package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.example.util.ExportDocumentFormat
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DoubtItem
import com.example.service.ListeningStateManager
import com.example.ui.components.DoubtCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate850
import com.example.util.ShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(
    targetDoubtId: Long? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { ListeningStateManager.getRepository(context) }
    val allDoubts by repository.allDoubts.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTopic by remember { mutableStateOf<String?>(null) }
    var showOnlyStarred by remember { mutableStateOf(false) }
    var showOnlyAmbiguous by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    // Dynamically derive distinct topics and their frequency counts
    val topicFrequencies = remember(allDoubts) {
        allDoubts.groupingBy { it.topic }.eachCount().toList().sortedByDescending { it.second }
    }

    // Filter doubts based on keyword search, selected topic, star status, and ambiguity
    val filteredDoubts = remember(allDoubts, searchQuery, selectedTopic, showOnlyStarred, showOnlyAmbiguous) {
        allDoubts.filter { item ->
            val matchesQuery = if (searchQuery.isBlank()) {
                true
            } else {
                val query = searchQuery.trim()
                item.topic.contains(query, ignoreCase = true) ||
                    item.doubtSnippet.contains(query, ignoreCase = true) ||
                    item.quickAnswer.contains(query, ignoreCase = true) ||
                    item.detailedAnswer.contains(query, ignoreCase = true) ||
                    item.notificationSummary.contains(query, ignoreCase = true) ||
                    item.conversationalContext.contains(query, ignoreCase = true)
            }

            val matchesTopic = selectedTopic == null || item.topic.equals(selectedTopic, ignoreCase = true)
            val matchesStar = !showOnlyStarred || item.isStarred
            val matchesAmbiguous = !showOnlyAmbiguous || item.isAmbiguous

            matchesQuery && matchesTopic && matchesStar && matchesAmbiguous
        }
    }

    val isFilterActive = searchQuery.isNotBlank() || selectedTopic != null || showOnlyStarred || showOnlyAmbiguous

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Title and Export / Clear History buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Doubt History",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Text(
                    text = if (isFilterActive) {
                        "Showing ${filteredDoubts.size} of ${allDoubts.size} questions"
                    } else {
                        "${allDoubts.size} total questions recorded"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            if (allDoubts.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var showExportMenu by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { showExportMenu = true },
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("export_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Export notes",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export", fontSize = 12.sp)
                        }

                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("📄 Export as PDF Document") },
                                onClick = {
                                    showExportMenu = false
                                    ShareHelper.exportDoubtsDigest(context, filteredDoubts, ExportDocumentFormat.PDF)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("📝 Export as Structured Text (.txt)") },
                                onClick = {
                                    showExportMenu = false
                                    ShareHelper.exportDoubtsDigest(context, filteredDoubts, ExportDocumentFormat.STRUCTURED_TEXT)
                                }
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { showClearConfirmDialog = true },
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear all",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Interactive Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by topic, keyword, or concept...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (searchQuery.isNotEmpty()) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.testTag("clear_search_query_button")
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search query")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricCyan,
                unfocusedBorderColor = Slate800,
                focusedContainerColor = Slate850,
                unfocusedContainerColor = Slate850
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("history_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Topic Quick-Filter Scrollable Chips (Horizontal Row)
        if (topicFrequencies.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // All topics chip
                FilterChip(
                    selected = selectedTopic == null,
                    onClick = { selectedTopic = null },
                    label = { Text("All Topics (${allDoubts.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                        selectedLabelColor = ElectricCyan
                    ),
                    modifier = Modifier.testTag("topic_chip_all")
                )

                // Topic specific chips
                topicFrequencies.forEach { (topic, count) ->
                    val isSelected = selectedTopic == topic
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedTopic = if (isSelected) null else topic
                        },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Topic,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = if (isSelected) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("$topic ($count)", fontSize = 12.sp)
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan.copy(alpha = 0.25f),
                            selectedLabelColor = ElectricCyan
                        ),
                        modifier = Modifier.testTag("topic_chip_$topic")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Secondary Filters: Starred & Needs Clarification
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = showOnlyStarred,
                onClick = { showOnlyStarred = !showOnlyStarred },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Saved (${allDoubts.count { it.isStarred }})", fontSize = 12.sp)
                    }
                },
                modifier = Modifier.testTag("filter_starred")
            )

            FilterChip(
                selected = showOnlyAmbiguous,
                onClick = { showOnlyAmbiguous = !showOnlyAmbiguous },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = if (showOnlyAmbiguous) AmberWarning else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Clarifications (${allDoubts.count { it.isAmbiguous }})",
                            fontSize = 12.sp,
                            color = if (showOnlyAmbiguous) AmberWarning else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AmberWarning.copy(alpha = 0.2f),
                    selectedLabelColor = AmberWarning
                ),
                modifier = Modifier.testTag("filter_ambiguous")
            )

            if (isFilterActive) {
                TextButton(
                    onClick = {
                        searchQuery = ""
                        selectedTopic = null
                        showOnlyStarred = false
                        showOnlyAmbiguous = false
                    },
                    modifier = Modifier.testTag("reset_all_filters_button")
                ) {
                    Text("Reset Filters", fontSize = 12.sp, color = ElectricCyan)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Results List or Empty State
        if (filteredDoubts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isFilterActive) Icons.Default.Search else Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) {
                            "No doubts matching \"$searchQuery\""
                        } else if (selectedTopic != null) {
                            "No doubts found under topic \"$selectedTopic\""
                        } else if (showOnlyStarred) {
                            "No saved doubts yet"
                        } else {
                            "No doubts in history yet"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isFilterActive) {
                            "Try searching for another concept, or tap 'Reset Filters' to see all recorded questions."
                        } else {
                            "Questions and doubts detected during conversations will automatically be logged here."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            lineHeight = 18.sp
                        )
                    )

                    if (isFilterActive) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = {
                                searchQuery = ""
                                selectedTopic = null
                                showOnlyStarred = false
                                showOnlyAmbiguous = false
                            },
                            modifier = Modifier.testTag("empty_state_reset_filters")
                        ) {
                            Text("Clear Search & Filters")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(filteredDoubts, key = { it.id }) { doubt ->
                    val shouldExpand = targetDoubtId == doubt.id
                    DoubtCard(
                        doubt = doubt,
                        initialExpandedDetailed = shouldExpand,
                        onToggleStar = { item ->
                            coroutineScope.launch(Dispatchers.IO) {
                                repository.updateDoubt(item.copy(isStarred = !item.isStarred))
                            }
                        },
                        onDelete = { item ->
                            coroutineScope.launch(Dispatchers.IO) {
                                repository.deleteDoubt(item.id)
                            }
                        }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Confirmation dialog for Clear All
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear Doubt History?") },
            text = { Text("This will permanently remove all stored speech doubts, questions, and explanations.") },
            confirmButton = {
                TextButton(
                    onClick = {
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
}
