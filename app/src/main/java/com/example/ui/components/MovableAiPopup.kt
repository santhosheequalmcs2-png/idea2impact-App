package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.analytics.LocalAnalyticsTracker
import com.example.data.model.AiRequestPhase
import com.example.data.model.AiStatusUiState
import com.example.data.model.BackendInnovation
import com.example.data.model.CopilotMessage
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GoldAmber
import com.example.ui.theme.RoseError
import com.example.util.AiNetworkHealthStatus
import com.example.util.LocalHapticFeedbackManager
import com.example.util.NetworkDiagnosticManager
import com.example.util.NetworkDiagnosticState
import kotlin.math.roundToInt

private val POPUP_INNOVATION_PROMPTS = listOf(
    "Summarize" to "Give a crisp 3-bullet executive summary of this innovation and its core edge.",
    "Valuation" to "Estimate realistic pre-seed/seed valuation benchmarks and key value drivers.",
    "IP Risks" to "List top 3 patentability / prior-art risks and how to protect the IP."
)

private val POPUP_GENERAL_PROMPTS = listOf(
    "Review Idea" to "Evaluate my startup idea with strengths, risks, missing info, and a calibrated 1-10 early rating.",
    "Validate Demand" to "Suggest 3 concrete customer discovery or demand validation tests for an early-stage idea.",
    "Find Matches" to "What verified organizations or talent profiles in IDEA2IMPACT could support my concept?"
)

@Composable
fun MovableAiPopup(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    messages: List<CopilotMessage>,
    isLoading: Boolean,
    aiStatus: AiStatusUiState = AiStatusUiState(),
    useFastModel: Boolean,
    activeContext: BackendInnovation?,
    canRegenerate: Boolean = false,
    networkDiagnosticManager: NetworkDiagnosticManager? = null,
    networkDiagnosticState: NetworkDiagnosticState? = null,
    onToggleFastModel: (Boolean) -> Unit,
    onSendPrompt: (prompt: String, promptSource: String) -> Unit,
    onStopGeneration: () -> Unit = {},
    onRegenerateLast: () -> Unit = {},
    onRetryTimedOut: (String) -> Unit = {},
    onSwitchToFastAndRetry: (String) -> Unit = {},
    onSwitchToDefaultAiAndRetry: (String) -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onOpenFullCopilot: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedbackManager.current
    val analytics = LocalAnalyticsTracker.current
    val resolvedDiagnosticManager = remember(networkDiagnosticManager, context) {
        networkDiagnosticManager ?: NetworkDiagnosticManager.getInstance(context)
    }
    val collectedDiagnosticState by resolvedDiagnosticManager.state.collectAsStateWithLifecycle()
    val effectiveDiagnosticState = networkDiagnosticState ?: collectedDiagnosticState
    val showStreamNetworkStatus = effectiveDiagnosticState.isChecking || effectiveDiagnosticState.health in setOf(
        AiNetworkHealthStatus.CHECKING,
        AiNetworkHealthStatus.NO_INTERNET,
        AiNetworkHealthStatus.FIREBASE_UNREACHABLE,
        AiNetworkHealthStatus.REQUESTS_BLOCKED,
        AiNetworkHealthStatus.APP_CHECK_ISSUE
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .zIndex(15f)
    ) {
        val marginPx = with(density) { 12.dp.toPx() }
        val topMinPx = with(density) { 64.dp.toPx() }
        val bottomReservedPx = with(density) { 84.dp.toPx() }
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }

        val bubbleSizeDp = 58.dp
        val bubbleSizePx = with(density) { bubbleSizeDp.toPx() }

        val popupWidthDp = (maxWidth - 24.dp).coerceAtMost(356.dp)
        val popupHeightDp = (maxHeight * 0.58f).coerceIn(320.dp, 450.dp)
        val popupWidthPx = with(density) { popupWidthDp.toPx() }
        val popupHeightPx = with(density) { popupHeightDp.toPx() }

        // Separate draggable coordinates for bubble vs popup, initialized cleanly on first measure
        var bubbleInitialized by rememberSaveable { mutableStateOf(false) }
        var bubbleX by rememberSaveable { mutableFloatStateOf(0f) }
        var bubbleY by rememberSaveable { mutableFloatStateOf(0f) }

        var popupInitialized by rememberSaveable { mutableStateOf(false) }
        var popupX by rememberSaveable { mutableFloatStateOf(0f) }
        var popupY by rememberSaveable { mutableFloatStateOf(0f) }

        val maxBubbleX = (maxWidthPx - bubbleSizePx - marginPx).coerceAtLeast(marginPx)
        val maxBubbleY = (maxHeightPx - bubbleSizePx - bottomReservedPx).coerceAtLeast(topMinPx)

        val maxPopupX = (maxWidthPx - popupWidthPx - marginPx).coerceAtLeast(marginPx)
        val maxPopupY = (maxHeightPx - popupHeightPx - marginPx).coerceAtLeast(topMinPx)

        LaunchedEffect(maxWidthPx, maxHeightPx) {
            if (!bubbleInitialized && maxWidthPx > 0f && maxHeightPx > 0f) {
                bubbleX = maxBubbleX
                bubbleY = (maxBubbleY - with(density) { 18.dp.toPx() }).coerceAtLeast(topMinPx)
                bubbleInitialized = true
            } else {
                bubbleX = bubbleX.coerceIn(marginPx, maxBubbleX)
                bubbleY = bubbleY.coerceIn(topMinPx, maxBubbleY)
            }

            if (!popupInitialized && maxWidthPx > 0f && maxHeightPx > 0f) {
                popupX = ((maxWidthPx - popupWidthPx) / 2f).coerceIn(marginPx, maxPopupX)
                popupY = (maxHeightPx - popupHeightPx - bottomReservedPx).coerceIn(topMinPx, maxPopupY)
                popupInitialized = true
            } else {
                popupX = popupX.coerceIn(marginPx, maxPopupX)
                popupY = popupY.coerceIn(topMinPx, maxPopupY)
            }
        }

        AnimatedVisibility(
            visible = !isExpanded,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.75f, animationSpec = tween(200)),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.75f, animationSpec = tween(140))
        ) {
            var isDraggingBubble by remember { mutableStateOf(false) }
            val bubbleScale by animateFloatAsState(
                targetValue = if (isDraggingBubble) 1.1f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "ai_bubble_drag_scale"
            )

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 10.dp,
                border = BorderStroke(
                    1.5.dp,
                    Brush.linearGradient(listOf(ElectricCyan, CobaltBlue, MaterialTheme.colorScheme.primary))
                ),
                modifier = Modifier
                    .offset {
                        IntOffset(
                            bubbleX.coerceIn(marginPx, maxBubbleX).roundToInt(),
                            bubbleY.coerceIn(topMinPx, maxBubbleY).roundToInt()
                        )
                    }
                    .size(bubbleSizeDp)
                    .scale(bubbleScale)
                    .pointerInput(maxBubbleX, maxBubbleY) {
                        detectDragGestures(
                            onDragStart = {
                                isDraggingBubble = true
                                haptics.dragStart()
                            },
                            onDragEnd = { isDraggingBubble = false },
                            onDragCancel = { isDraggingBubble = false },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                bubbleX = (bubbleX + dragAmount.x).coerceIn(marginPx, maxBubbleX)
                                bubbleY = (bubbleY + dragAmount.y).coerceIn(topMinPx, maxBubbleY)
                            }
                        )
                    }
                    .clip(CircleShape)
                    .clickable {
                        haptics.tap()
                        // Anchor popup near current bubble position clamped to screen
                        popupX = (bubbleX - popupWidthPx + bubbleSizePx).coerceIn(marginPx, maxPopupX)
                        popupY = (bubbleY - popupHeightPx + bubbleSizePx).coerceIn(topMinPx, maxPopupY)
                        onExpandedChange(true)
                    }
                    .testTag("movable_ai_bubble")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "Open movable AI Sana",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "AI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(7.dp)
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(EmeraldSuccess)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.86f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)),
            exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.88f, animationSpec = tween(160))
        ) {
            var inputText by rememberSaveable { mutableStateOf("") }
            var isDraggingPopup by remember { mutableStateOf(false) }
            val listState = rememberLazyListState()

            LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length, showStreamNetworkStatus) {
                val targetIndex = (messages.size + (if (showStreamNetworkStatus) 1 else 0) - 1)
                    .coerceAtLeast(0)
                if (messages.isNotEmpty() || showStreamNetworkStatus) {
                    listState.animateScrollToItem(targetIndex)
                }
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                border = BorderStroke(
                    width = if (isDraggingPopup) 2.dp else 1.2.dp,
                    brush = Brush.linearGradient(listOf(ElectricCyan.copy(alpha = 0.8f), MaterialTheme.colorScheme.primary))
                ),
                modifier = Modifier
                    .offset {
                        IntOffset(
                            popupX.coerceIn(marginPx, maxPopupX).roundToInt(),
                            popupY.coerceIn(topMinPx, maxPopupY).roundToInt()
                        )
                    }
                    .size(width = popupWidthDp, height = popupHeightDp)
                    .testTag("movable_ai_popup")
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Draggable Pop-Up Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .pointerInput(maxPopupX, maxPopupY) {
                                detectDragGestures(
                                    onDragStart = {
                                        isDraggingPopup = true
                                        haptics.dragStart()
                                    },
                                    onDragEnd = { isDraggingPopup = false },
                                    onDragCancel = { isDraggingPopup = false },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        popupX = (popupX + dragAmount.x).coerceIn(marginPx, maxPopupX)
                                        popupY = (popupY + dragAmount.y).coerceIn(topMinPx, maxPopupY)
                                    }
                                )
                            }
                            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                            .testTag("movable_ai_drag_handle"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DragIndicator,
                            contentDescription = "Drag AI pop-up",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        val popupStatusLabel = when {
                            effectiveDiagnosticState.isChecking -> NetworkDiagnosticManager.LABEL_CHECKING_CONNECTION
                            effectiveDiagnosticState.health == AiNetworkHealthStatus.NO_INTERNET ->
                                NetworkDiagnosticManager.LABEL_NO_INTERNET
                            else -> aiStatus.label.ifBlank { if (isLoading) "Thinking..." else "" }
                        }
                        val hasPopupNetIssue = effectiveDiagnosticState.health in setOf(
                            AiNetworkHealthStatus.NO_INTERNET,
                            AiNetworkHealthStatus.FIREBASE_UNREACHABLE,
                            AiNetworkHealthStatus.REQUESTS_BLOCKED,
                            AiNetworkHealthStatus.APP_CHECK_ISSUE
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AI Sana Pop-up",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            val hasBlankThinkingBubble = isLoading &&
                                messages.lastOrNull()?.let { it.isAssistant && it.content.isBlank() } == true
                            if (isLoading && popupStatusLabel.isNotBlank() && !hasBlankThinkingBubble) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.testTag("popup_status_indicator")
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(10.dp),
                                        strokeWidth = 1.6.dp,
                                        color = if (aiStatus.phase == AiRequestPhase.STREAMING) {
                                            EmeraldSuccess
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                    )
                                    Text(
                                        text = popupStatusLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (aiStatus.phase == AiRequestPhase.STREAMING) {
                                            EmeraldSuccess
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else if (effectiveDiagnosticState.isChecking) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.testTag("popup_network_status_banner")
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(10.dp),
                                        strokeWidth = 1.6.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = effectiveDiagnosticState.streamLabel.ifBlank {
                                            NetworkDiagnosticManager.LABEL_CHECKING_CONNECTION
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else if (hasPopupNetIssue) {
                                val issueColor = if (effectiveDiagnosticState.health == AiNetworkHealthStatus.APP_CHECK_ISSUE) {
                                    GoldAmber
                                } else {
                                    RoseError
                                }
                                Text(
                                    text = effectiveDiagnosticState.streamLabel.ifBlank {
                                        effectiveDiagnosticState.statusMessage
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = issueColor,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .clickable {
                                            haptics.tap()
                                            onOpenDiagnostics()
                                        }
                                        .testTag("popup_network_status_banner")
                                )
                            } else {
                                Text(
                                    text = activeContext?.title ?: "Drag bar to move anywhere",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        FilterChip(
                            selected = useFastModel,
                            onClick = {
                                val nextFast = !useFastModel
                                haptics.toggle(nextFast)
                                onToggleFastModel(nextFast)
                            },
                            leadingIcon = { Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = {
                                Text(
                                    text = if (useFastModel) "Fast" else "Deep",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )

                        IconButton(
                            onClick = {
                                haptics.tap()
                                analytics.logCopilotExpandedFromPopup()
                                onOpenFullCopilot()
                            },
                            modifier = Modifier.size(40.dp).testTag("movable_ai_expand_tab")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.OpenInFull,
                                contentDescription = "Open full AI Sana",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                haptics.tap()
                                onExpandedChange(false)
                            },
                            modifier = Modifier.size(40.dp).testTag("movable_ai_close")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Minimize AI pop-up",
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    // Quick Prompt Chips
                    val quickPrompts = if (activeContext != null) POPUP_INNOVATION_PROMPTS else POPUP_GENERAL_PROMPTS
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(quickPrompts) { (label, prompt) ->
                            AssistChip(
                                onClick = {
                                    if (!isLoading) {
                                        haptics.tap()
                                        analytics.logCopilotQuickActionTapped(
                                            actionLabel = label,
                                            surface = "floating_popup"
                                        )
                                        onSendPrompt(prompt, "popup_quick_action")
                                    }
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    // Streaming Chat Messages
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            val isLastMessage = msg.id == messages.lastOrNull()?.id
                            val isCurrentlyStreaming = isLoading && msg.isAssistant && isLastMessage
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (msg.isAssistant) Arrangement.Start else Arrangement.End
                            ) {
                                Surface(
                                    color = when {
                                        msg.isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
                                        msg.isAssistant -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                        else -> MaterialTheme.colorScheme.primaryContainer
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (msg.isError) MaterialTheme.colorScheme.error.copy(alpha = 0.65f)
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier.widthIn(max = popupWidthDp - 44.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        if (msg.isAssistant && msg.content.isBlank() && isLoading) {
                                            val bubbleStatus = when {
                                                effectiveDiagnosticState.isChecking ->
                                                    NetworkDiagnosticManager.LABEL_CHECKING_CONNECTION
                                                effectiveDiagnosticState.health == AiNetworkHealthStatus.NO_INTERNET ->
                                                    NetworkDiagnosticManager.LABEL_NO_INTERNET
                                                else -> aiStatus.label.ifBlank { "Thinking..." }
                                            }
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                                modifier = Modifier.testTag("popup_status_indicator")
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(14.dp),
                                                        strokeWidth = 2.dp
                                                    )
                                                    Text(
                                                        text = bubbleStatus,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                if (aiStatus.hint.isNotBlank()) {
                                                    Text(
                                                        text = aiStatus.hint,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        } else {
                                            if (msg.isAssistant && !isCurrentlyStreaming && !msg.respondingModelLabel.isNullOrBlank() && msg.id != "welcome") {
                                                Text(
                                                    text = msg.respondingModelLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (msg.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(bottom = 4.dp)
                                                )
                                            }
                                            if (msg.isAssistant && !msg.modelNotice.isNullOrBlank()) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(bottom = 6.dp)
                                                        .testTag("popup_model_notice")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Bolt,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Text(
                                                            text = msg.modelNotice,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                    }
                                                }
                                            }
                                            MarkdownMessageContent(
                                                raw = msg.content,
                                                textColor = if (msg.isAssistant) {
                                                    MaterialTheme.colorScheme.onSurface
                                                } else {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                },
                                                primaryAccent = MaterialTheme.colorScheme.primary,
                                                codeBg = MaterialTheme.colorScheme.surfaceVariant,
                                                textStyle = MaterialTheme.typography.bodySmall,
                                                compact = true
                                            )
                                            if (msg.isAssistant && !isCurrentlyStreaming && msg.isError) {
                                                val isResume = msg.isTimeout && msg.hasPartialTimeoutOutput
                                                val retryTag = if (msg.isTimeout) {
                                                    "popup_timeout_action_button"
                                                } else {
                                                    "popup_error_retry_button"
                                                }
                                                Column(
                                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            haptics.tap()
                                                            onRetryTimedOut(msg.id)
                                                        },
                                                        enabled = !isLoading,
                                                        shape = RoundedCornerShape(10.dp),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = MaterialTheme.colorScheme.primary,
                                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                                        ),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .heightIn(min = 48.dp)
                                                            .testTag(retryTag)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isResume) Icons.Filled.PlayArrow else Icons.Filled.Refresh,
                                                            contentDescription = if (isResume) {
                                                                "Resume timed-out AI Sana response without re-typing"
                                                            } else {
                                                                "Retry failed AI Sana request without re-typing"
                                                            },
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(
                                                            text = if (isResume) "Resume" else "Retry",
                                                            style = MaterialTheme.typography.labelMedium
                                                        )
                                                    }

                                                    if (msg.offerFastFallback && !useFastModel) {
                                                        OutlinedButton(
                                                            onClick = {
                                                                haptics.tap()
                                                                onSwitchToFastAndRetry(msg.id)
                                                            },
                                                            enabled = !isLoading,
                                                            shape = RoundedCornerShape(10.dp),
                                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .heightIn(min = 48.dp)
                                                                .testTag("popup_switch_fast_button")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Filled.Bolt,
                                                                contentDescription = "Switch to AI Sana Fast mode and retry",
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Spacer(Modifier.width(6.dp))
                                                            Text(
                                                                text = "Switch to Fast Mode",
                                                                style = MaterialTheme.typography.labelMedium
                                                            )
                                                        }
                                                    }

                                                    if (msg.offerDefaultAiFallback) {
                                                        OutlinedButton(
                                                            onClick = {
                                                                haptics.tap()
                                                                onSwitchToDefaultAiAndRetry(msg.id)
                                                            },
                                                            enabled = !isLoading,
                                                            shape = RoundedCornerShape(10.dp),
                                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .heightIn(min = 48.dp)
                                                                .testTag("popup_switch_default_ai_button")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Filled.AutoAwesome,
                                                                contentDescription = "Use default AI Sana and retry",
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Spacer(Modifier.width(6.dp))
                                                            Text(
                                                                text = "Use Default AI",
                                                                style = MaterialTheme.typography.labelMedium
                                                            )
                                                        }
                                                    }

                                                    OutlinedButton(
                                                        onClick = {
                                                            haptics.tap()
                                                            onOpenDiagnostics()
                                                        },
                                                        enabled = !isLoading,
                                                        shape = RoundedCornerShape(10.dp),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .heightIn(min = 48.dp)
                                                            .testTag("popup_run_diagnostics_button")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.NetworkCheck,
                                                            contentDescription = "Check device internet and Firebase reachability",
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(
                                                            text = "Run Connection Check",
                                                            style = MaterialTheme.typography.labelMedium
                                                        )
                                                    }
                                                }
                                            } else if (msg.isAssistant && isLastMessage && !isCurrentlyStreaming && canRegenerate && msg.id != "welcome" && !msg.isError) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.End
                                                ) {
                                                    TextButton(
                                                        onClick = {
                                                            haptics.tap()
                                                            onRegenerateLast()
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                        modifier = Modifier.testTag("popup_regenerate_reply")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Refresh,
                                                            contentDescription = "Retry response",
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Text(
                                                            text = " Retry",
                                                            style = MaterialTheme.typography.labelSmall
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (showStreamNetworkStatus) {
                            item(key = "popup_chat_stream_network_status") {
                                ChatStreamNetworkStatusPill(
                                    diagnosticState = effectiveDiagnosticState,
                                    compact = true,
                                    onClick = onOpenDiagnostics
                                )
                            }
                        }
                    }

                    // Compact Input Composer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    text = "Ask AI Sana...",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            textStyle = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f).testTag("movable_ai_input")
                        )

                        Surface(
                            shape = CircleShape,
                            color = when {
                                isLoading -> MaterialTheme.colorScheme.errorContainer
                                inputText.isNotBlank() -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(enabled = isLoading || inputText.isNotBlank()) {
                                    if (isLoading) {
                                        haptics.tap()
                                        onStopGeneration()
                                    } else {
                                        val outgoing = inputText.trim()
                                        if (outgoing.isNotBlank()) {
                                            haptics.success()
                                            onSendPrompt(outgoing, "popup_input")
                                            inputText = ""
                                        }
                                    }
                                }
                                .testTag("movable_ai_send")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isLoading) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                                    contentDescription = if (isLoading) "Stop generation" else "Send prompt",
                                    tint = when {
                                        isLoading -> MaterialTheme.colorScheme.onErrorContainer
                                        inputText.isNotBlank() -> MaterialTheme.colorScheme.onPrimary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
