package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DoubtItem
import com.example.service.ListeningStateManager
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate850
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import org.json.JSONArray

enum class ClarificationAction {
    DETAILED_DIRECT,
    ASK_QUESTIONS_FIRST,
    REFERENCE_MATERIALS,
    SHORT_ANSWER
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractiveClarificationCard(
    doubt: DoubtItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isRefining by ListeningStateManager.isRefiningAnswer.collectAsState()
    val globalClarificationMode by ListeningStateManager.clarificationMode.collectAsState()

    var selectedAction by remember(doubt.id) {
        mutableStateOf(
            if (doubt.isAmbiguous) ClarificationAction.ASK_QUESTIONS_FIRST
            else ClarificationAction.DETAILED_DIRECT
        )
    }

    var selectedStyle by remember(doubt.id) { mutableStateOf("Detailed technical explanation") }
    var userClarificationInput by remember(doubt.id) { mutableStateOf("") }
    var selectedOptionChip by remember(doubt.id) { mutableStateOf<String?>(null) }
    var activeMode by remember(doubt.id, globalClarificationMode) { mutableStateOf(globalClarificationMode) }

    // Parse options from JSON safely
    val parsedOptions = remember(doubt.suggestedOptionsJson) {
        val list = mutableListOf<String>()
        try {
            if (doubt.suggestedOptionsJson.isNotBlank()) {
                val array = JSONArray(doubt.suggestedOptionsJson)
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
            }
        } catch (e: Exception) {
            list.add("Core Principle")
            list.add("Practical Example")
        }
        if (list.isEmpty()) {
            list.add("Core Mechanism")
            list.add("Practical Example")
        }
        list
    }

    val styleOptions = listOf(
        "Simple explanation",
        "Detailed technical explanation",
        "Practical examples",
        "Mathematical explanation",
        "Step-by-step solution",
        "Python code or implementation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("interactive_clarification_card_${doubt.id}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Slate850),
        border = BorderStroke(
            1.5.dp,
            if (doubt.isAmbiguous) AmberWarning.copy(alpha = 0.6f) else NeonViolet.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Context Intelligence Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Context-Aware Doubt Understanding",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                if (doubt.isAmbiguous) {
                    Surface(
                        color = AmberWarning.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Ambiguity Detected",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = AmberWarning,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Identified Context Breakdown Grid
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Slate900,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Slate800)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Subject: ${doubt.subject.ifBlank { "General Discipline" }}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = ElectricCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = "Topic: ${doubt.topic}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = NeonViolet,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }

                    if (doubt.doubtType.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Doubt Type: ${doubt.doubtType}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }

                    if (doubt.intent.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Intention: ${doubt.intent}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }

                    if (doubt.importantConcepts.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Concepts: ${doubt.importantConcepts}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Mode Selector Tabs (Mode A: Private vs Mode B: Conversation-Based)
            TabRow(
                selectedTabIndex = if (activeMode == "PRIVATE") 0 else 1,
                containerColor = Slate900,
                contentColor = ElectricCyan,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[if (activeMode == "PRIVATE") 0 else 1]),
                        color = ElectricCyan
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = activeMode == "PRIVATE",
                    onClick = {
                        activeMode = "PRIVATE"
                        ListeningStateManager.setClarificationMode("PRIVATE")
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Private Clarification", fontSize = 12.sp)
                        }
                    }
                )
                Tab(
                    selected = activeMode == "CONVERSATION",
                    onClick = {
                        activeMode = "CONVERSATION"
                        ListeningStateManager.setClarificationMode("CONVERSATION")
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Classroom / Discussion", fontSize = 12.sp)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Question Prompt: "What would you like DoubtSense to do with this doubt?"
            Text(
                text = "What would you like DoubtSense to do with this doubt?",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 4 Core Options
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionOptionCard(
                    title = "1. Give me a detailed explanation directly",
                    subtitle = "Generates a full conceptual breakdown immediately",
                    isSelected = selectedAction == ClarificationAction.DETAILED_DIRECT,
                    onClick = { selectedAction = ClarificationAction.DETAILED_DIRECT }
                )

                ActionOptionCard(
                    title = "2. Ask me questions first to understand my doubt",
                    subtitle = "Interactive adaptive questions tailored to ${doubt.topic}",
                    isSelected = selectedAction == ClarificationAction.ASK_QUESTIONS_FIRST,
                    isRecommended = doubt.isAmbiguous,
                    onClick = { selectedAction = ClarificationAction.ASK_QUESTIONS_FIRST }
                )

                ActionOptionCard(
                    title = "3. Find relevant reference materials",
                    subtitle = "Recommended documentation, formulas, and textbook references",
                    isSelected = selectedAction == ClarificationAction.REFERENCE_MATERIALS,
                    onClick = { selectedAction = ClarificationAction.REFERENCE_MATERIALS }
                )

                ActionOptionCard(
                    title = "4. Give me a short answer",
                    subtitle = "Direct, 2-3 sentence high-level summary",
                    isSelected = selectedAction == ClarificationAction.SHORT_ANSWER,
                    onClick = { selectedAction = ClarificationAction.SHORT_ANSWER }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Content Area based on Selection
            when (selectedAction) {
                ClarificationAction.ASK_QUESTIONS_FIRST -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Slate900,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, NeonViolet.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.QuestionAnswer,
                                    contentDescription = null,
                                    tint = NeonViolet,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Adaptive Clarification Question",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = NeonViolet
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Dynamically generated clarification question
                            Text(
                                text = doubt.clarificationQuestion?.ifBlank {
                                    "Which aspect of ${doubt.topic} would you like clarified?"
                                } ?: "Which aspect of ${doubt.topic} would you like clarified?",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Selectable Options
                            Text(
                                text = "Select focus or enter details below:",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                parsedOptions.forEach { option ->
                                    val isSelected = selectedOptionChip == option
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedOptionChip = if (isSelected) null else option
                                            if (!isSelected) {
                                                userClarificationInput = option
                                            }
                                        },
                                        label = { Text(option, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NeonViolet.copy(alpha = 0.3f),
                                            selectedLabelColor = NeonViolet
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSelected,
                                            borderColor = Slate800,
                                            selectedBorderColor = NeonViolet
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Custom Input Text Field
                            OutlinedTextField(
                                value = userClarificationInput,
                                onValueChange = { userClarificationInput = it },
                                placeholder = { Text("Or type your specific doubt/confusion...") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("custom_clarification_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonViolet,
                                    unfocusedBorderColor = Slate800,
                                    focusedContainerColor = Slate850,
                                    unfocusedContainerColor = Slate850
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Preferred Explanation Style Picker
                            Text(
                                text = "Preferred Answer Style:",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                styleOptions.forEach { style ->
                                    FilterChip(
                                        selected = selectedStyle == style,
                                        onClick = { selectedStyle = style },
                                        label = { Text(style, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ElectricCyan.copy(alpha = 0.25f),
                                            selectedLabelColor = ElectricCyan
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Submit Clarification Button
                            Button(
                                onClick = {
                                    val inputToSubmit = userClarificationInput.ifBlank {
                                        selectedOptionChip ?: "Detailed clarification"
                                    }
                                    ListeningStateManager.submitClarification(
                                        doubt = doubt,
                                        userClarification = inputToSubmit,
                                        style = selectedStyle,
                                        context = context
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonViolet,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isRefining,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("submit_clarification_button")
                            ) {
                                if (isRefining) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Personalizing Answer...")
                                } else {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Generate Tailored Answer", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                ClarificationAction.REFERENCE_MATERIALS -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Slate900,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Slate800)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MenuBook, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Relevant Reference Materials",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = ElectricCyan
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = doubt.referenceMaterials.ifBlank {
                                    "Reference materials for ${doubt.topic}: Consult standard textbooks, research publications, or official documentation on ${doubt.importantConcepts}."
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )
                            )
                        }
                    }
                }

                ClarificationAction.SHORT_ANSWER -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Slate900,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Slate800)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "⚡ Short Answer",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricCyan
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = doubt.quickAnswer,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 22.sp
                                )
                            )
                        }
                    }
                }

                ClarificationAction.DETAILED_DIRECT -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Slate900,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Slate800)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "📖 Direct Detailed Explanation",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NeonViolet
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = doubt.detailedAnswer,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 24.sp
                                )
                            )
                        }
                    }
                }
            }

            // Mode B: Conversation-Based Clarification Suggestion Banner
            if (activeMode == "CONVERSATION" && doubt.conversationSuggestions.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = EmeraldSuccess.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forum,
                            contentDescription = "Discussion Suggestion",
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Suggested Question to Ask Aloud:",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldSuccess
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "\"${doubt.conversationSuggestions}\"",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    isRecommended: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Slate900 else Slate850,
        border = BorderStroke(
            1.dp,
            if (isSelected) ElectricCyan else Slate800
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) ElectricCyan else Color.Transparent)
                    .then(
                        if (!isSelected) Modifier.background(Slate800) else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Slate950,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) ElectricCyan else MaterialTheme.colorScheme.onSurface
                        )
                    )
                    if (isRecommended) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = AmberWarning.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "RECOMMENDED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = AmberWarning,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}
