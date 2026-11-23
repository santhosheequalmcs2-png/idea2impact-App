package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.analytics.LocalAnalyticsTracker
import com.example.data.model.BackendComment
import com.example.data.model.BackendInnovation
import com.example.data.model.DigitalNdaRecord
import com.example.data.model.PitchDeckResult
import com.example.data.model.UiState
import com.example.ui.components.CollaborationProposalModal
import com.example.ui.components.MutualNdaModal
import com.example.ui.components.PitchDeckModal
import com.example.ui.components.Prototype3DViewer
import com.example.ui.components.formatCurrency
import com.example.ui.components.shareInnovationSheet
import com.example.ui.components.sharedInnovationBounds
import com.example.ui.components.sharedInnovationElement
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.util.LocalHapticFeedbackManager

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    innovation: BackendInnovation,
    comments: List<BackendComment>,
    currentUserName: String,
    signedNda: DigitalNdaRecord?,
    pitchDeckState: UiState<PitchDeckResult>?,
    isBusy: Boolean,
    isInIdeaPortfolio: Boolean = false,
    isInInvestorPortfolio: Boolean = false,
    onBack: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleIdeaPortfolio: () -> Unit = {},
    onToggleInvestorPortfolio: () -> Unit = {},
    onPostComment: (text: String, onDone: () -> Unit) -> Unit,
    onSubmitProposal: (
        type: String,
        amount: String,
        message: String,
        contactInfo: String,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    onSignNda: (signerName: String, signerOrg: String) -> Unit,
    onGeneratePitchDeck: () -> Unit,
    onDismissPitchDeck: () -> Unit,
    onAnalyzeWithCopilot: () -> Unit = {},
    onFindMatches: () -> Unit = {},
    onMessageCreator: () -> Unit = {}
) {
    var commentText by remember { mutableStateOf("") }
    var showProposalModal by remember { mutableStateOf(false) }
    var proposalError by remember { mutableStateOf<String?>(null) }
    var showNdaModal by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptics = LocalHapticFeedbackManager.current
    val analytics = LocalAnalyticsTracker.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text("← Back to Marketplace", style = MaterialTheme.typography.titleMedium) },
            navigationIcon = {
                IconButton(
                    onClick = {
                        haptics.tap()
                        onBack()
                    },
                    modifier = Modifier.testTag("detail_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Marketplace")
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        haptics.tap()
                        analytics.logInnovationShared(
                            innovationId = innovation.id,
                            title = innovation.title,
                            category = innovation.category,
                            source = "detail_screen"
                        )
                        shareInnovationSheet(context, innovation)
                    },
                    modifier = Modifier.testTag("detail_share_button")
                ) {
                    Icon(Icons.Filled.Share, contentDescription = "Share innovation")
                }
                IconButton(
                    onClick = {
                        haptics.heartLike(willBeLiked = !innovation.isLiked)
                        onToggleLike()
                    },
                    modifier = Modifier.testTag("detail_like_button")
                ) {
                    Icon(
                        imageVector = if (innovation.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like innovation",
                        tint = if (innovation.isLiked) RoseError else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = {
                        haptics.toggle(!innovation.isBookmarked)
                        onToggleBookmark()
                    },
                    modifier = Modifier.testTag("detail_bookmark_button")
                ) {
                    Icon(
                        imageVector = if (innovation.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Bookmark innovation",
                        tint = if (innovation.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hero Image, Badges & Title
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .sharedInnovationElement("innovation_image_${innovation.id}")
                                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        ) {
                            if (!innovation.imageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = innovation.imageUrl,
                                    contentDescription = innovation.title,
                                    contentScale = ContentScale.Crop,
                                    placeholder = painterResource(R.drawable.img_hero_innovation),
                                    error = painterResource(R.drawable.img_hero_innovation),
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Image(
                                    painter = painterResource(R.drawable.img_hero_innovation),
                                    contentDescription = innovation.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Category badge, Stage pill, Verified shield
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.sharedInnovationElement("innovation_category_${innovation.id}")
                                ) {
                                    Text(
                                        text = innovation.category,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                                Surface(
                                    color = EmeraldSuccess,
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.sharedInnovationElement("innovation_stage_${innovation.id}")
                                ) {
                                    Text(
                                        text = innovation.stage,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Shield,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Verified Shield",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }

                            Text(
                                text = innovation.title,
                                style = MaterialTheme.typography.headlineLarge,
                                modifier = Modifier.sharedInnovationBounds("innovation_title_${innovation.id}")
                            )

                            // Creator profile box
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (!innovation.authorAvatar.isNullOrBlank()) {
                                        AsyncImage(
                                            model = innovation.authorAvatar,
                                            contentDescription = innovation.authorName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(42.dp).clip(CircleShape)
                                        )
                                    } else {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(42.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = innovation.authorName.take(1).uppercase(),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(innovation.authorName, style = MaterialTheme.typography.titleSmall)
                                            Icon(
                                                Icons.Filled.Verified,
                                                contentDescription = "Verified Creator",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        Text(
                                            text = "${innovation.authorTitle} · Verified Creator",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            haptics.tap()
                                            onMessageCreator()
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("detail_message_creator_button")
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Chat,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text("Message", maxLines = 1)
                                    }
                                }
                            }

                            // Funding & Valuation metrics
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Funding Goal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(formatCurrency(innovation.fundingGoal), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Estimated Valuation", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(formatCurrency(innovation.valuation), style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }

                            Text(
                                text = innovation.description,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            // 2. Interactive 3D CAD Explorer
            item {
                Prototype3DViewer(
                    title = innovation.title,
                    category = innovation.category
                )
            }

            // 3. Deal Room Banner
            item {
                val isNdaSigned = signedNda != null
                Surface(
                    color = if (isNdaSigned) EmeraldSuccess.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(
                        1.dp,
                        if (isNdaSigned) EmeraldSuccess.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isNdaSigned) {
                            "🛡️ Confidential Deal Room Unlocked · Signed (${signedNda!!.signatureHash})"
                        } else {
                            "🛡️ Confidential Deal Room Active · Mutual NDA Required for Full CAD & Financials"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isNdaSigned) EmeraldSuccess else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            // 4. Action Buttons Row
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            haptics.tap()
                            showProposalModal = true
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("detail_proposal_button")
                    ) {
                        Icon(Icons.Filled.Handshake, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Submit Collaboration Proposal")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                haptics.tap()
                                showNdaModal = true
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("detail_nda_button")
                        ) {
                            Icon(Icons.Filled.Shield, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (signedNda != null) "NDA Signed" else "Sign Digital NDA")
                        }
                        OutlinedButton(
                            onClick = {
                                haptics.tap()
                                onGeneratePitchDeck()
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("detail_pitch_deck_button")
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Generate AI Pitch Deck")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            haptics.tap()
                            analytics.logCopilotOpenedFromDetail(
                                innovationId = innovation.id,
                                category = innovation.category
                            )
                            onAnalyzeWithCopilot()
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("detail_ask_copilot_button")
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Analyze with AI Sana")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                haptics.toggle(!isInIdeaPortfolio)
                                onToggleIdeaPortfolio()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("detail_idea_portfolio_button")
                        ) {
                            Icon(
                                imageVector = if (isInIdeaPortfolio) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
                                contentDescription = null,
                                tint = if (isInIdeaPortfolio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isInIdeaPortfolio) "In Idea Portfolio" else "Idea Portfolio", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                haptics.toggle(!isInInvestorPortfolio)
                                onToggleInvestorPortfolio()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("detail_investor_portfolio_button")
                        ) {
                            Icon(
                                imageVector = if (isInInvestorPortfolio) Icons.Filled.AccountBalanceWallet else Icons.Outlined.AccountBalanceWallet,
                                contentDescription = null,
                                tint = if (isInInvestorPortfolio) EmeraldSuccess else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isInInvestorPortfolio) "In Investor Portfolio" else "Investor Portfolio", maxLines = 1)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            haptics.tap()
                            onFindMatches()
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("detail_find_matches_button")
                    ) {
                        Icon(Icons.Filled.Hub, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Find Ecosystem & Talent Matches")
                    }
                }
            }

            // 5. Comments / Discussion Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Discussion (${comments.size})", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = { Text("Share technical feedback, prior-art notes, or partnership questions...") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth().testTag("detail_comment_input")
                    )

                    Button(
                        onClick = {
                            haptics.success()
                            onPostComment(commentText) {
                                commentText = ""
                                keyboardController?.hide()
                            }
                        },
                        enabled = !isBusy && commentText.trim().isNotBlank(),
                        modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp).testTag("detail_post_comment_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Post Comment")
                    }
                }
            }

            if (comments.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No comments yet. Start the discussion on this innovation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(comments, key = { it.id }) { comment ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!comment.authorAvatar.isNullOrBlank()) {
                                AsyncImage(
                                    model = comment.authorAvatar,
                                    contentDescription = comment.authorName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(34.dp).clip(CircleShape)
                                )
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            comment.authorName.take(1).uppercase(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(comment.authorName, style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        comment.createdAt.take(10),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(comment.body, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showProposalModal) {
        CollaborationProposalModal(
            innovationTitle = innovation.title,
            isBusy = isBusy,
            errorMessage = proposalError,
            onClearError = { proposalError = null },
            onSubmit = { type, amount, msg, contact ->
                proposalError = null
                onSubmitProposal(
                    type,
                    amount,
                    msg,
                    contact,
                    {
                        proposalError = null
                        showProposalModal = false
                    },
                    { err ->
                        proposalError = err
                    }
                )
            },
            onDismiss = {
                proposalError = null
                showProposalModal = false
            }
        )
    }

    if (showNdaModal) {
        MutualNdaModal(
            innovation = innovation,
            defaultSignerName = currentUserName,
            existingRecord = signedNda,
            onSign = { name, org -> onSignNda(name, org) },
            onDismiss = { showNdaModal = false }
        )
    }

    if (pitchDeckState != null) {
        PitchDeckModal(
            state = pitchDeckState,
            onRetry = onGeneratePitchDeck,
            onDismiss = onDismissPitchDeck
        )
    }
}
