package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.StackedBarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ConversationSession
import com.example.data.model.DoubtItem
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonViolet
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

enum class CategoryFrequencyPeriod(val label: String) {
    SEVEN_DAYS("7 Days"),
    FOUR_WEEKS("4 Weeks"),
    THREE_MONTHS("3 Months")
}

enum class CategoryGroupingDimension(val label: String) {
    TAGS("Session Tags"),
    SUBJECTS("Subject Categories")
}

enum class CategoryBarStyle(val label: String) {
    GROUPED("Grouped"),
    STACKED("Stacked")
}

data class CategoryTimeBucket(
    val bucketIndex: Int,
    val shortLabel: String,
    val fullLabel: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val countsByCategory: Map<String, Int>,
    val totalEntries: Int
)

data class CategoryFrequencySeries(
    val categoryName: String,
    val totalCount: Int
)

data class CategoryFrequencySummary(
    val period: CategoryFrequencyPeriod,
    val dimension: CategoryGroupingDimension,
    val buckets: List<CategoryTimeBucket>,
    val categories: List<CategoryFrequencySeries>,
    val maxBucketCategoryCount: Int,
    val maxBucketStackedTotal: Int,
    val totalSessionsCounted: Int
)

object CategoryFrequencyCalculator {

    private data class TimeRangeWindow(
        val shortLabel: String,
        val fullLabel: String,
        val startMillis: Long,
        val endMillis: Long
    )

    private data class TaggedEntry(
        val timestamp: Long,
        val labels: List<String>
    )

    /**
     * Computes frequency buckets for doubt categories/tags across the selected time period.
     * Supports multi-tagged sessions (e.g., "Study, Work" increments both Study and Work)
     * and gracefully falls back to stored doubts if no ConversationSession records exist yet.
     */
    fun computeCategoryFrequencyOverTime(
        sessions: List<ConversationSession>,
        doubts: List<DoubtItem> = emptyList(),
        period: CategoryFrequencyPeriod = CategoryFrequencyPeriod.SEVEN_DAYS,
        dimension: CategoryGroupingDimension = CategoryGroupingDimension.TAGS,
        nowMillis: Long = System.currentTimeMillis(),
        maxCategories: Int = 5
    ): CategoryFrequencySummary {
        val windows = buildTimeWindows(period, nowMillis)
        val entries: List<TaggedEntry> = if (sessions.isNotEmpty()) {
            sessions.map { session ->
                val ts = if (session.endTime > 0L) session.endTime else session.startTime
                val labels = when (dimension) {
                    CategoryGroupingDimension.TAGS -> {
                        session.getTagList().ifEmpty { listOf("General") }
                    }
                    CategoryGroupingDimension.SUBJECTS -> {
                        listOf(session.category.trim().ifBlank { "General" })
                    }
                }
                TaggedEntry(ts, labels.distinctBy { it.lowercase(Locale.US) })
            }
        } else {
            doubts.map { doubt ->
                val labels = when (dimension) {
                    CategoryGroupingDimension.TAGS -> {
                        ConversationSession.parseTagList(doubt.customTag).ifEmpty { listOf("General") }
                    }
                    CategoryGroupingDimension.SUBJECTS -> {
                        listOf(
                            doubt.subject.trim().ifBlank {
                                doubt.topic.substringBefore(":").trim().ifBlank { "General" }
                            }
                        )
                    }
                }
                TaggedEntry(doubt.timestamp, labels.distinctBy { it.lowercase(Locale.US) })
            }
        }

        // Canonical casing map so "study" and "Study" merge cleanly
        val canonicalNames = mutableMapOf<String, String>()
        val windowStart = windows.firstOrNull()?.startMillis ?: 0L
        val windowEnd = windows.lastOrNull()?.endMillis ?: Long.MAX_VALUE
        val inWindowEntries = entries.filter { it.timestamp in windowStart until windowEnd }

        val totalCountsByLower = mutableMapOf<String, Int>()
        inWindowEntries.forEach { entry ->
            entry.labels.forEach { rawLabel ->
                val clean = rawLabel.trim().ifBlank { "General" }
                val key = clean.lowercase(Locale.US)
                canonicalNames.putIfAbsent(key, clean)
                totalCountsByLower[key] = (totalCountsByLower[key] ?: 0) + 1
            }
        }

        val sortedKeys = totalCountsByLower.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { canonicalNames[it.key] ?: it.key }
            )
            .map { it.key }

        val keptKeys = sortedKeys.take(maxCategories.coerceAtLeast(1))
        val overflowKeys = sortedKeys.drop(maxCategories.coerceAtLeast(1)).toSet()
        val hasOther = overflowKeys.isNotEmpty()

        val displaySeries = mutableListOf<CategoryFrequencySeries>()
        keptKeys.forEach { key ->
            displaySeries.add(
                CategoryFrequencySeries(
                    categoryName = canonicalNames[key] ?: key,
                    totalCount = totalCountsByLower[key] ?: 0
                )
            )
        }
        if (hasOther) {
            displaySeries.add(
                CategoryFrequencySeries(
                    categoryName = "Other",
                    totalCount = overflowKeys.sumOf { totalCountsByLower[it] ?: 0 }
                )
            )
        }

        val keptKeyToDisplayName = keptKeys.associateWith { canonicalNames[it] ?: it }

        val buckets = windows.mapIndexed { idx, win ->
            val bucketEntries = inWindowEntries.filter { it.timestamp in win.startMillis until win.endMillis }
            val counts = linkedMapOf<String, Int>()
            displaySeries.forEach { counts[it.categoryName] = 0 }

            bucketEntries.forEach { entry ->
                val seenInEntry = mutableSetOf<String>()
                entry.labels.forEach { raw ->
                    val key = raw.trim().ifBlank { "General" }.lowercase(Locale.US)
                    val targetName = when {
                        keptKeyToDisplayName.containsKey(key) -> keptKeyToDisplayName[key]!!
                        overflowKeys.contains(key) -> "Other"
                        else -> null
                    }
                    if (targetName != null && seenInEntry.add(targetName)) {
                        counts[targetName] = (counts[targetName] ?: 0) + 1
                    }
                }
            }

            CategoryTimeBucket(
                bucketIndex = idx,
                shortLabel = win.shortLabel,
                fullLabel = win.fullLabel,
                startTimeMillis = win.startMillis,
                endTimeMillis = win.endMillis,
                countsByCategory = counts,
                totalEntries = bucketEntries.size
            )
        }

        val maxSingle = buckets.maxOfOrNull { bucket ->
            bucket.countsByCategory.values.maxOrNull() ?: 0
        } ?: 0
        val maxStacked = buckets.maxOfOrNull { bucket ->
            bucket.countsByCategory.values.sum()
        } ?: 0

        return CategoryFrequencySummary(
            period = period,
            dimension = dimension,
            buckets = buckets,
            categories = displaySeries,
            maxBucketCategoryCount = maxSingle,
            maxBucketStackedTotal = maxStacked,
            totalSessionsCounted = inWindowEntries.size
        )
    }

    private fun buildTimeWindows(
        period: CategoryFrequencyPeriod,
        nowMillis: Long
    ): List<TimeRangeWindow> {
        val windows = mutableListOf<TimeRangeWindow>()
        when (period) {
            CategoryFrequencyPeriod.SEVEN_DAYS -> {
                val shortFmt = SimpleDateFormat("EEE", Locale.getDefault())
                val fullFmt = SimpleDateFormat("MMM d", Locale.getDefault())
                val startCal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_YEAR, -6)
                }
                repeat(7) { idx ->
                    val s = startCal.timeInMillis
                    val next = (startCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
                    val e = next.timeInMillis
                    val date = Date(s)
                    val full = if (idx == 6) "${fullFmt.format(date)} (Today)" else fullFmt.format(date)
                    windows.add(
                        TimeRangeWindow(
                            shortLabel = if (idx == 6) "Today" else shortFmt.format(date),
                            fullLabel = full,
                            startMillis = s,
                            endMillis = e
                        )
                    )
                    startCal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            CategoryFrequencyPeriod.FOUR_WEEKS -> {
                val rangeFmt = SimpleDateFormat("MMM d", Locale.getDefault())
                // End of today (start of tomorrow)
                val tomorrowStart = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_YEAR, 1)
                }
                val startCal = (tomorrowStart.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, -28)
                }
                repeat(4) { idx ->
                    val s = startCal.timeInMillis
                    val next = (startCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 7) }
                    val e = next.timeInMillis
                    val inclusiveEnd = e - 1L
                    val shortLbl = if (idx == 3) "This Wk" else "Wk ${idx + 1}"
                    val fullLbl = "${rangeFmt.format(Date(s))} – ${rangeFmt.format(Date(inclusiveEnd))}"
                    windows.add(
                        TimeRangeWindow(
                            shortLabel = shortLbl,
                            fullLabel = fullLbl,
                            startMillis = s,
                            endMillis = e
                        )
                    )
                    startCal.add(Calendar.DAY_OF_YEAR, 7)
                }
            }
            CategoryFrequencyPeriod.THREE_MONTHS -> {
                val shortFmt = SimpleDateFormat("MMM", Locale.getDefault())
                val fullFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                val monthCal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.MONTH, -2)
                }
                repeat(3) {
                    val s = monthCal.timeInMillis
                    val next = (monthCal.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                    val e = next.timeInMillis
                    val d = Date(s)
                    windows.add(
                        TimeRangeWindow(
                            shortLabel = shortFmt.format(d),
                            fullLabel = fullFmt.format(d),
                            startMillis = s,
                            endMillis = e
                        )
                    )
                    monthCal.add(Calendar.MONTH, 1)
                }
            }
        }
        return windows
    }
}

/**
 * Visual Dashboard bar chart displaying the frequency of doubt categories/tags over time
 * (for example comparing sessions tagged 'Study' vs 'Work' across 7 Days, 4 Weeks, or 3 Months).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryFrequencyBarChart(
    sessions: List<ConversationSession>,
    doubts: List<DoubtItem> = emptyList(),
    onSelectTagFilter: (String) -> Unit = {},
    onSelectSubjectFilter: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    nowMillis: Long = System.currentTimeMillis()
) {
    var selectedPeriod by remember { mutableStateOf(CategoryFrequencyPeriod.SEVEN_DAYS) }
    var selectedDimension by remember { mutableStateOf(CategoryGroupingDimension.TAGS) }
    var barStyle by remember { mutableStateOf(CategoryBarStyle.GROUPED) }
    var hiddenCategories by remember(selectedDimension) { mutableStateOf<Set<String>>(emptySet()) }

    val summary = remember(sessions, doubts, selectedPeriod, selectedDimension, nowMillis) {
        CategoryFrequencyCalculator.computeCategoryFrequencyOverTime(
            sessions = sessions,
            doubts = doubts,
            period = selectedPeriod,
            dimension = selectedDimension,
            nowMillis = nowMillis
        )
    }

    val activeSeries = remember(summary.categories, hiddenCategories) {
        val visible = summary.categories.filter { !hiddenCategories.contains(it.categoryName) }
        visible.ifEmpty { summary.categories }
    }

    var selectedBucketIndex by remember(selectedPeriod, summary.buckets.size) {
        mutableIntStateOf((summary.buckets.size - 1).coerceAtLeast(0))
    }

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(
        selectedPeriod,
        selectedDimension,
        barStyle,
        summary.totalSessionsCounted,
        activeSeries.size
    ) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
        )
    }

    val yAxisMax = remember(summary, activeSeries, barStyle) {
        val rawMax = when (barStyle) {
            CategoryBarStyle.GROUPED -> {
                summary.buckets.maxOfOrNull { bucket ->
                    activeSeries.maxOfOrNull { s -> bucket.countsByCategory[s.categoryName] ?: 0 } ?: 0
                } ?: 0
            }
            CategoryBarStyle.STACKED -> {
                summary.buckets.maxOfOrNull { bucket ->
                    activeSeries.sumOf { s -> bucket.countsByCategory[s.categoryName] ?: 0 }
                } ?: 0
            }
        }
        val coerced = rawMax.coerceAtLeast(2)
        if (coerced <= 4) coerced else ceil(coerced / 2.0).toInt() * 2
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline
    val surfaceColor = MaterialTheme.colorScheme.surface

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("category_frequency_bar_chart"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, outlineColor.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = stringResource(R.string.cd_cat_freq_chart),
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.dashboard_cat_freq_title),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = stringResource(R.string.dashboard_cat_freq_subtitle),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = labelColor,
                                fontSize = 11.sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ElectricCyan.copy(alpha = 0.16f)
                ) {
                    val unit = if (sessions.isNotEmpty()) "session(s)" else "doubt(s)"
                    Text(
                        text = "${summary.totalSessionsCounted} $unit",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("cat_freq_total_badge")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Time Period & Grouping Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryFrequencyPeriod.entries.forEach { period ->
                    val isSel = selectedPeriod == period
                    FilterChip(
                        selected = isSel,
                        onClick = { selectedPeriod = period },
                        label = { Text(period.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan.copy(alpha = 0.22f),
                            selectedLabelColor = ElectricCyan
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("cat_freq_period_${period.name.lowercase(Locale.US)}")
                    )
                }

                Surface(
                    onClick = {
                        barStyle = if (barStyle == CategoryBarStyle.GROUPED) {
                            CategoryBarStyle.STACKED
                        } else {
                            CategoryBarStyle.GROUPED
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = surfaceColor,
                    border = BorderStroke(1.dp, NeonViolet.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("cat_freq_style_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (barStyle == CategoryBarStyle.GROUPED) {
                                Icons.Default.StackedBarChart
                            } else {
                                Icons.Default.BarChart
                            },
                            contentDescription = "Switch between grouped and stacked bars",
                            tint = NeonViolet,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = barStyle.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = NeonViolet,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            // Dimension switcher (Session Tags vs Subject Categories)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryGroupingDimension.entries.forEach { dim ->
                    val isSel = selectedDimension == dim
                    FilterChip(
                        selected = isSel,
                        onClick = { selectedDimension = dim },
                        leadingIcon = {
                            Icon(
                                imageVector = if (dim == CategoryGroupingDimension.TAGS) {
                                    Icons.AutoMirrored.Filled.Label
                                } else {
                                    Icons.Default.Category
                                },
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        label = { Text(dim.label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonViolet.copy(alpha = 0.2f),
                            selectedLabelColor = NeonViolet
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("cat_freq_dim_${dim.name.lowercase(Locale.US)}")
                    )
                }
            }

            if (summary.categories.isEmpty() || summary.totalSessionsCounted == 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = surfaceColor,
                    border = BorderStroke(1.dp, outlineColor.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cat_freq_empty_state")
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_cat_freq_empty),
                        style = MaterialTheme.typography.bodySmall.copy(color = labelColor),
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(6.dp))

                // Interactive Color-Coded Category Legend (tap to isolate/compare categories)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    summary.categories.forEach { series ->
                        val isActive = activeSeries.any { it.categoryName == series.categoryName }
                        val accent = tagAccentColor(series.categoryName)
                        Surface(
                            onClick = {
                                val nextHidden = if (hiddenCategories.contains(series.categoryName)) {
                                    hiddenCategories - series.categoryName
                                } else {
                                    val candidate = hiddenCategories + series.categoryName
                                    if (candidate.size >= summary.categories.size) emptySet() else candidate
                                }
                                hiddenCategories = nextHidden
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isActive) accent.copy(alpha = 0.16f) else surfaceColor.copy(alpha = 0.6f),
                            border = BorderStroke(
                                1.dp,
                                if (isActive) accent.copy(alpha = 0.55f) else outlineColor.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .testTag("cat_freq_legend_${series.categoryName}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isActive) accent else labelColor.copy(alpha = 0.4f))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${series.categoryName} (${series.totalCount})",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isActive) accent else labelColor,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }

                    if (hiddenCategories.isNotEmpty()) {
                        TextButton(
                            onClick = { hiddenCategories = emptySet() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .testTag("cat_freq_legend_reset")
                        ) {
                            Text("Show All", fontSize = 11.sp, color = ElectricCyan)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val safeSelectedIdx = selectedBucketIndex.coerceIn(0, summary.buckets.lastIndex.coerceAtLeast(0))
                val selectedBucket = summary.buckets.getOrNull(safeSelectedIdx)
                val progress = animProgress.value

                // Native Compose Grouped / Stacked Bar Chart Canvas
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(196.dp)
                        .pointerInput(summary.buckets) {
                            val leftMargin = 26.dp.toPx()
                            val rightMargin = 8.dp.toPx()
                            detectTapGestures { offset ->
                                val count = summary.buckets.size.coerceAtLeast(1)
                                val plotW = (size.width - leftMargin - rightMargin).coerceAtLeast(1f)
                                val relX = (offset.x - leftMargin).coerceIn(0f, plotW - 0.01f)
                                val idx = ((relX / plotW) * count).toInt().coerceIn(0, count - 1)
                                selectedBucketIndex = idx
                            }
                        }
                        .testTag("cat_freq_bar_chart_canvas")
                ) {
                    val bucketCount = summary.buckets.size.coerceAtLeast(1)
                    val leftMargin = 26.dp.toPx()
                    val rightMargin = 8.dp.toPx()
                    val topPadding = 20.dp.toPx()
                    val bottomPadding = 28.dp.toPx()
                    val plotWidth = (size.width - leftMargin - rightMargin).coerceAtLeast(20f)
                    val plotHeight = (size.height - topPadding - bottomPadding).coerceAtLeast(20f)
                    val colWidth = plotWidth / bucketCount.toFloat()

                    val yAxisPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.RIGHT
                        textSize = 9.5.sp.toPx()
                        color = labelColor.toArgb()
                    }
                    val xLabelPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSize = 10.sp.toPx()
                        color = labelColor.toArgb()
                    }
                    val countPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSize = 9.5.sp.toPx()
                        isFakeBoldText = true
                        color = primaryColor.toArgb()
                    }

                    // Horizontal dashed grid lines + integer Y-axis ticks
                    listOf(0f, 0.5f, 1f).forEach { frac ->
                        val y = topPadding + plotHeight * (1f - frac)
                        drawLine(
                            color = outlineColor.copy(alpha = 0.35f),
                            start = Offset(leftMargin, y),
                            end = Offset(size.width - rightMargin, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        )
                        val tickVal = (yAxisMax * frac).toInt()
                        drawContext.canvas.nativeCanvas.drawText(
                            "$tickVal",
                            leftMargin - 6.dp.toPx(),
                            y + 3.dp.toPx(),
                            yAxisPaint
                        )
                    }

                    summary.buckets.forEachIndexed { bIndex, bucket ->
                        val colStartX = leftMargin + colWidth * bIndex
                        val colCenterX = colStartX + colWidth / 2f
                        val isSelected = bIndex == safeSelectedIdx

                        // Highlight selected time column softly
                        if (isSelected) {
                            drawRoundRect(
                                color = primaryColor.copy(alpha = 0.10f),
                                topLeft = Offset(colStartX + 3.dp.toPx(), topPadding - 4.dp.toPx()),
                                size = Size((colWidth - 6.dp.toPx()).coerceAtLeast(4f), plotHeight + 8.dp.toPx()),
                                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                            )
                        }

                        if (barStyle == CategoryBarStyle.GROUPED) {
                            val seriesCount = activeSeries.size.coerceAtLeast(1)
                            val groupUsableW = (colWidth * 0.78f).coerceAtLeast(8.dp.toPx())
                            val gap = if (seriesCount > 1) 3.dp.toPx() else 0f
                            val singleBarW = ((groupUsableW - gap * (seriesCount - 1)) / seriesCount.toFloat())
                                .coerceIn(4.dp.toPx(), 24.dp.toPx())
                            val totalGroupW = singleBarW * seriesCount + gap * (seriesCount - 1)
                            val groupStartX = colCenterX - totalGroupW / 2f

                            activeSeries.forEachIndexed { sIndex, series ->
                                val count = bucket.countsByCategory[series.categoryName] ?: 0
                                val accent = tagAccentColor(series.categoryName)
                                val barLeft = groupStartX + sIndex * (singleBarW + gap)

                                if (count > 0) {
                                    val ratio = (count.toFloat() / yAxisMax.toFloat()).coerceIn(0f, 1f)
                                    val barH = (plotHeight * ratio * progress).coerceAtLeast(4.dp.toPx())
                                    val barTop = topPadding + plotHeight - barH

                                    drawRoundRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                accent,
                                                accent.copy(alpha = if (isSelected) 0.85f else 0.68f)
                                            ),
                                            startY = barTop,
                                            endY = barTop + barH
                                        ),
                                        topLeft = Offset(barLeft, barTop),
                                        size = Size(singleBarW, barH),
                                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                    )

                                    if (singleBarW >= 10.dp.toPx()) {
                                        countPaint.color = accent.toArgb()
                                        drawContext.canvas.nativeCanvas.drawText(
                                            "$count",
                                            barLeft + singleBarW / 2f,
                                            (barTop - 4.dp.toPx()).coerceAtLeast(10.dp.toPx()),
                                            countPaint
                                        )
                                    }
                                } else {
                                    val minH = 2.dp.toPx()
                                    drawRoundRect(
                                        color = outlineColor.copy(alpha = 0.25f),
                                        topLeft = Offset(barLeft, topPadding + plotHeight - minH),
                                        size = Size(singleBarW, minH),
                                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                    )
                                }
                            }
                        } else {
                            // STACKED bar style
                            val barW = (colWidth * 0.48f).coerceIn(10.dp.toPx(), 34.dp.toPx())
                            val barLeft = colCenterX - barW / 2f
                            val bucketTotal = activeSeries.sumOf { bucket.countsByCategory[it.categoryName] ?: 0 }

                            if (bucketTotal == 0) {
                                val minH = 2.dp.toPx()
                                drawRoundRect(
                                    color = outlineColor.copy(alpha = 0.25f),
                                    topLeft = Offset(barLeft, topPadding + plotHeight - minH),
                                    size = Size(barW, minH),
                                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                )
                            } else {
                                var currentBottomY = topPadding + plotHeight
                                val nonZeroSegments = activeSeries.mapNotNull { series ->
                                    val c = bucket.countsByCategory[series.categoryName] ?: 0
                                    if (c > 0) series.categoryName to c else null
                                }
                                nonZeroSegments.forEachIndexed { segIdx, (catName, c) ->
                                    val segRatio = (c.toFloat() / yAxisMax.toFloat()).coerceIn(0f, 1f)
                                    val segH = (plotHeight * segRatio * progress).coerceAtLeast(3.dp.toPx())
                                    val segTop = (currentBottomY - segH).coerceAtLeast(topPadding)
                                    val accent = tagAccentColor(catName)
                                    val isTopSegment = segIdx == nonZeroSegments.lastIndex
                                    val radius = if (isTopSegment) 5.dp.toPx() else 1.5.dp.toPx()

                                    drawRoundRect(
                                        color = if (isSelected) accent else accent.copy(alpha = 0.84f),
                                        topLeft = Offset(barLeft, segTop),
                                        size = Size(barW, (currentBottomY - segTop).coerceAtLeast(2f)),
                                        cornerRadius = CornerRadius(radius, radius)
                                    )
                                    currentBottomY = segTop
                                }

                                countPaint.color = primaryColor.toArgb()
                                drawContext.canvas.nativeCanvas.drawText(
                                    "$bucketTotal",
                                    colCenterX,
                                    (currentBottomY - 4.dp.toPx()).coerceAtLeast(10.dp.toPx()),
                                    countPaint
                                )
                            }
                        }

                        xLabelPaint.isFakeBoldText = isSelected
                        xLabelPaint.color = if (isSelected) primaryColor.toArgb() else labelColor.toArgb()
                        drawContext.canvas.nativeCanvas.drawText(
                            bucket.shortLabel,
                            colCenterX,
                            size.height - 6.dp.toPx(),
                            xLabelPaint
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Selected Period Breakdown Summary + Quick Filter Action
                if (selectedBucket != null) {
                    val activeEntriesInBucket = activeSeries
                        .map { series -> series.categoryName to (selectedBucket.countsByCategory[series.categoryName] ?: 0) }
                        .filter { it.second > 0 }
                    val breakdownText = if (activeEntriesInBucket.isEmpty()) {
                        "No categorized sessions recorded in this period"
                    } else {
                        activeEntriesInBucket.joinToString(" • ") { (name, count) -> "$name: $count" }
                    }
                    val topCategoryInWindow = activeEntriesInBucket.maxByOrNull { it.second }?.first
                        ?: activeSeries.firstOrNull()?.categoryName

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = surfaceColor,
                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {}
                            .testTag("cat_freq_selected_summary")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedBucket.fullLabel,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = primaryColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = breakdownText,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (!topCategoryInWindow.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    onClick = {
                                        if (selectedDimension == CategoryGroupingDimension.TAGS) {
                                            onSelectTagFilter(topCategoryInWindow)
                                        } else {
                                            onSelectSubjectFilter(topCategoryInWindow)
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = ElectricCyan.copy(alpha = 0.14f),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("cat_freq_open_logs_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FilterList,
                                            contentDescription = "Filter history logs by $topCategoryInWindow",
                                            tint = ElectricCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "View $topCategoryInWindow",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = ElectricCyan,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
