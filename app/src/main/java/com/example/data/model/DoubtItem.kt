package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a detected doubt with deep contextual understanding,
 * classification, adaptive clarification exchanges, and tailored answers.
 */
@Entity(tableName = "doubts")
data class DoubtItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val doubtSnippet: String,
    val topic: String,
    val quickAnswer: String,
    val detailedAnswer: String,
    val notificationSummary: String,
    val conversationalContext: String = "",
    val isAmbiguous: Boolean = false,
    val clarificationQuestion: String? = null,
    val confidenceScore: Float = 0.9f,
    val isStarred: Boolean = false,

    // Enhanced Context Understanding & Intent
    val subject: String = "",
    val importantConcepts: String = "",
    val doubtType: String = "",
    val intent: String = "",

    // Interactive Clarification & Adaptive Options
    val suggestedOptionsJson: String = "[]",
    val referenceMaterials: String = "",
    val conversationSuggestions: String = "",
    val userClarificationResponse: String? = null,
    val clarificationHistory: String = "",
    val preferredStyle: String = "Detailed",
    val clarificationMode: String = "PRIVATE", // "PRIVATE" or "CONVERSATION"
    val isResolvedWithClarification: Boolean = false
)
