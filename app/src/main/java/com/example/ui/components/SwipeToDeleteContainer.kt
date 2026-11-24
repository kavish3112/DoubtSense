package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate900

/**
 * Reusable Material 3 SwipeToDismissBox wrapper providing end-to-start (swipe-left) deletion
 * with an animated crimson action background.
 */
@Composable
fun SwipeToDeleteContainer(
    itemKey: Any,
    enabled: Boolean = true,
    cornerRadius: Dp = 16.dp,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundTestTag: String? = null,
    content: @Composable () -> Unit
) {
    val currentOnDelete by rememberUpdatedState(onDelete)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { targetValue ->
            enabled && targetValue == SwipeToDismissBoxValue.EndToStart
        }
    )

    LaunchedEffect(itemKey, dismissState.currentValue) {
        if (enabled && dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            currentOnDelete()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = enabled,
        backgroundContent = {
            val isTriggered = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
            val bgColor by animateColorAsState(
                targetValue = if (isTriggered) {
                    RoseError.copy(alpha = 0.88f)
                } else {
                    Slate900.copy(alpha = 0.75f)
                },
                animationSpec = spring(),
                label = "swipeDeleteBg"
            )
            val iconScale by animateFloatAsState(
                targetValue = if (isTriggered) 1.15f else 0.92f,
                animationSpec = spring(),
                label = "swipeDeleteIconScale"
            )
            val contentTint = if (isTriggered) Color.White else RoseError.copy(alpha = 0.85f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(bgColor)
                    .padding(horizontal = 20.dp)
                    .then(
                        if (backgroundTestTag != null) Modifier.testTag(backgroundTestTag) else Modifier
                    ),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.scale(iconScale)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.cd_swipe_to_delete),
                        tint = contentTint,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = stringResource(R.string.action_delete_item),
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = contentTint,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    ) {
        content()
    }
}
