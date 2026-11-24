package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.util.ExportDocumentFormat
import com.example.util.ShareHelper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.CategorySummary
import com.example.data.repository.DailyInsightCalculator
import com.example.data.repository.DoubtRepository
import com.example.service.ListeningStateManager
import com.example.ui.components.CategoryFrequencyBarChart
import com.example.ui.components.DailyInsightCard
import com.example.ui.components.DoubtFrequency30DayChart
import com.example.ui.components.DoubtIntensityTrendChart
import com.example.ui.components.WeeklyResolvedDoubtsChart
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.RoseError

@Composable
fun CategoryDashboardScreen(
    onSelectCategory: (String) -> Unit,
    onSelectTag: (String) -> Unit = {},
    onInspectDoubt: (Long) -> Unit = {},
    onNavigateToLiveSense: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { ListeningStateManager.getRepository(context) }
    val allSessions by repository.allSessions.collectAsState(initial = emptyList())
    val allDoubts by repository.allDoubts.collectAsState(initial = emptyList())

    var isGridLayout by remember { mutableStateOf(true) }

    val categorySummaries = remember(allSessions, allDoubts) {
        DoubtRepository.computeCategorySummaries(allSessions, allDoubts)
    }

    val dailyInsight = remember(allDoubts, allSessions) {
        DailyInsightCalculator.computeDailyInsight(allDoubts, allSessions)
    }

    val overallAvgIntensity = remember(allSessions, allDoubts) {
        when {
            allSessions.isNotEmpty() -> allSessions.map { it.averageDoubtIntensity }.average().toFloat()
            allDoubts.isNotEmpty() -> allDoubts.map { it.confidenceScore }.average().toFloat()
            else -> 0f
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        val columns = if (!isGridLayout) 1 else if (maxWidth >= 720.dp) 3 else 2

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("category_dashboard_list"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header + Grid/List Layout Toggle
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Category Dashboard",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                        Text(
                            text = "Average doubt intensity calculated from stored Room sessions",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (allSessions.isNotEmpty() || allDoubts.isNotEmpty()) {
                            var showAnalyticsExportMenu by remember { mutableStateOf(false) }
                            Box {
                                FilledTonalIconButton(
                                    onClick = { showAnalyticsExportMenu = true },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = ElectricCyan
                                    ),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("dashboard_export_report_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = stringResource(R.string.cd_export_analysis_report)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showAnalyticsExportMenu,
                                    onDismissRequest = { showAnalyticsExportMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.export_analysis_pdf)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = ElectricCyan)
                                        },
                                        onClick = {
                                            showAnalyticsExportMenu = false
                                            ShareHelper.exportHistoryWithSessionAnalysis(
                                                context = context,
                                                sessions = allSessions,
                                                doubts = allDoubts,
                                                format = ExportDocumentFormat.PDF
                                            )
                                        },
                                        modifier = Modifier.testTag("analytics_export_pdf")
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.export_analysis_txt)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Description, contentDescription = null, tint = NeonViolet)
                                        },
                                        onClick = {
                                            showAnalyticsExportMenu = false
                                            ShareHelper.exportHistoryWithSessionAnalysis(
                                                context = context,
                                                sessions = allSessions,
                                                doubts = allDoubts,
                                                format = ExportDocumentFormat.STRUCTURED_TEXT
                                            )
                                        },
                                        modifier = Modifier.testTag("analytics_export_txt")
                                    )
                                }
                            }
                        }

                        FilledTonalIconButton(
                            onClick = { isGridLayout = !isGridLayout },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("dashboard_layout_toggle")
                        ) {
                            Icon(
                                imageVector = if (isGridLayout) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = if (isGridLayout) "Switch to List View" else "Switch to Grid View"
                            )
                        }
                    }
                }
            }

            // Overall KPI Summary Strip
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardKpiCard(
                        label = "Stored Sessions",
                        value = "${allSessions.size}",
                        accent = ElectricCyan,
                        modifier = Modifier.weight(1f)
                    )
                    DashboardKpiCard(
                        label = "Logged Doubts",
                        value = "${allDoubts.size}",
                        accent = NeonViolet,
                        modifier = Modifier.weight(1f)
                    )
                    DashboardKpiCard(
                        label = "Overall Avg",
                        value = "${(overallAvgIntensity * 100).toInt()}%",
                        accent = intensityColor(overallAvgIntensity),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Daily Insight Spotlight Card (Most Significant Doubt Moment & Progress)
            item {
                DailyInsightCard(
                    insight = dailyInsight,
                    onInspectDoubt = onInspectDoubt,
                    onSelectCategory = onSelectCategory,
                    onNavigateToLiveSense = onNavigateToLiveSense
                )
            }

            // Category / Tag Frequency Over Time Bar Chart (e.g., Study vs Work)
            item {
                CategoryFrequencyBarChart(
                    sessions = allSessions,
                    doubts = allDoubts,
                    onSelectTagFilter = onSelectTag,
                    onSelectSubjectFilter = onSelectCategory
                )
            }

            // Weekly Resolved Doubts Bar Chart (7-Day Progress)
            item {
                WeeklyResolvedDoubtsChart(doubts = allDoubts)
            }

            // Detailed 30-Day Doubt Frequency Graph
            item {
                DoubtFrequency30DayChart(
                    doubts = allDoubts,
                    sessions = allSessions
                )
            }

            // Category Breakdown Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Subject Categories (${categorySummaries.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Text(
                        text = if (isGridLayout) "Grid Layout" else "List Layout",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ElectricCyan,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            if (categorySummaries.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dashboard,
                                contentDescription = null,
                                tint = ElectricCyan.copy(alpha = 0.7f),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No Stored Sessions Yet",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Start a live listening session or load representative study sessions to calculate per-category doubt intensity analytics.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { ListeningStateManager.seedSampleSessions(context) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("dashboard_seed_samples_button")
                            ) {
                                Text("Load Sample Study Sessions", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                val rows = categorySummaries.chunked(columns)
                items(rows) { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { summary ->
                            CategorySummaryCard(
                                summary = summary,
                                isCompactGrid = columns > 1,
                                onClick = { onSelectCategory(summary.categoryName) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowItems.size < columns) {
                            repeat(columns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Multi-session Doubt Intensity Line Chart integrated on Dashboard
            item {
                Spacer(modifier = Modifier.height(4.dp))
                DoubtIntensityTrendChart(
                    doubts = allDoubts,
                    sessions = allSessions
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DashboardKpiCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = accent
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CategorySummaryCard(
    summary: CategorySummary,
    isCompactGrid: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = intensityColor(summary.averageIntensity)
    val riskLabel = when {
        summary.averageIntensity >= 0.70f -> "High Doubt"
        summary.averageIntensity >= 0.40f -> "Moderate"
        else -> "Low / Clear"
    }
    val animatedProgress by animateFloatAsState(
        targetValue = summary.averageIntensity.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 700),
        label = "category_intensity_progress"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("category_card_${summary.categoryName}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon(summary.categoryName),
                        contentDescription = summary.categoryName,
                        tint = accent,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Surface(
                    color = accent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = riskLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = summary.categoryName,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                maxLines = if (isCompactGrid) 2 else 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = summary.latestTopic,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "Avg Intensity",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                )
                Text(
                    text = "${(summary.averageIntensity * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = accent
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { animatedProgress },
                color = accent,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${summary.sessionCount} session(s)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                )
                Text(
                    text = "${summary.totalDoubts} doubt(s)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = ElectricCyan,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

private fun intensityColor(intensity: Float): Color = when {
    intensity >= 0.70f -> RoseError
    intensity >= 0.40f -> AmberWarning
    else -> EmeraldSuccess
}

private fun categoryIcon(category: String): ImageVector {
    val lower = category.lowercase()
    return when {
        lower.contains("machine") || lower.contains("ai") || lower.contains("neural") -> Icons.Default.Psychology
        lower.contains("bio") || lower.contains("cell") || lower.contains("med") -> Icons.Default.Biotech
        lower.contains("computer") || lower.contains("alg") || lower.contains("code") -> Icons.Default.Code
        lower.contains("phys") || lower.contains("chem") || lower.contains("thermo") -> Icons.Default.Science
        else -> Icons.Default.School
    }
}
