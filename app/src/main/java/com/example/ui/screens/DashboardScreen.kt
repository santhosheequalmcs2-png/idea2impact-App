package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.IdeaPortfolioEntity
import com.example.data.local.InvestorPortfolioEntity
import com.example.data.local.SavedProfileEntity
import com.example.data.model.AuthSyncStatus
import com.example.data.model.AuthUser
import com.example.data.model.BackendInnovation
import com.example.data.model.DashboardStats
import com.example.data.model.UnifiedAuthSessionState
import com.example.data.model.Proposal
import com.example.data.model.UiState
import com.example.ui.components.BackendInnovationCard
import com.example.ui.components.CapitalPipeline3D
import com.example.ui.components.FirebaseDiagnosticsCard
import com.example.ui.components.IdeaPortfolioSection
import com.example.ui.components.InnovationCardSkeleton
import com.example.ui.components.InvestorPortfolioSection
import com.example.ui.components.SavedProfilesSection
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GoldAmber
import com.example.ui.theme.RoseError
import com.example.util.FirebaseDiagnosticsReport
import com.example.util.LocalHapticFeedbackManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    currentUser: AuthUser?,
    dashboardState: UiState<DashboardStats>,
    isRefreshing: Boolean = false,
    incomingProposals: List<Proposal> = emptyList(),
    sentProposals: List<Proposal> = emptyList(),
    ideaPortfolioItems: List<IdeaPortfolioEntity> = emptyList(),
    ideaPortfolioQuery: String = "",
    investorPortfolioItems: List<InvestorPortfolioEntity> = emptyList(),
    investorPortfolioQuery: String = "",
    investorStatusFilter: String = "All",
    isBusy: Boolean = false,
    onOpenAuth: () -> Unit,
    onRefresh: () -> Unit,
    onOpenDetail: (BackendInnovation) -> Unit,
    onToggleLike: (BackendInnovation) -> Unit,
    onToggleBookmark: (BackendInnovation) -> Unit,
    onOpenPricing: () -> Unit,
    onOpenMatches: () -> Unit = {},
    diagnosticsReport: FirebaseDiagnosticsReport = FirebaseDiagnosticsReport(),
    onRunDiagnostics: () -> Unit = {},
    onRespondToProposal: (proposalId: String, accept: Boolean) -> Unit = { _, _ -> },
    onIdeaPortfolioQueryChange: (String) -> Unit = {},
    onAddIdeaToPortfolio: (title: String, summary: String, category: String, stage: String, priorityTag: String, notes: String) -> Unit = { _, _, _, _, _, _ -> },
    onUpdateIdeaNotes: (id: String, priorityTag: String, notes: String) -> Unit = { _, _, _ -> },
    onRemoveIdeaFromPortfolio: (String) -> Unit = {},
    onOpenIdeaPortfolioItem: (IdeaPortfolioEntity) -> Unit = {},
    onInvestorPortfolioQueryChange: (String) -> Unit = {},
    onInvestorStatusFilterChange: (String) -> Unit = {},
    onAddInvestorDeal: (title: String, summary: String, category: String, stage: String, founderName: String, fundingGoal: Double, valuation: Double, diligenceStatus: String, targetTicketSize: String, notes: String) -> Unit = { _, _, _, _, _, _, _, _, _, _ -> },
    onUpdateInvestorDiligence: (id: String, status: String, ticketSize: String, notes: String) -> Unit = { _, _, _, _ -> },
    onRemoveInvestorDeal: (String) -> Unit = {},
    onOpenInvestorPortfolioItem: (InvestorPortfolioEntity) -> Unit = {},
    savedProfiles: List<SavedProfileEntity> = emptyList(),
    savedProfilesQuery: String = "",
    onSavedProfilesQueryChange: (String) -> Unit = {},
    onRemoveSavedProfile: (String) -> Unit = {},
    onBrowseCollaborators: () -> Unit = {},
    onMessageSavedProfile: (SavedProfileEntity) -> Unit = {},
    unreadMessagesCount: Int = 0,
    collaborationsCount: Int = 0,
    pendingInvitesCount: Int = 0,
    onOpenMessages: () -> Unit = {},
    onOpenCollaborations: () -> Unit = {},
    authSessionState: UnifiedAuthSessionState = UnifiedAuthSessionState(),
    onRetryAuthSync: () -> Unit = {},
    onSignOut: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "My Innovations",
        "Saved",
        "Idea Portfolio (${ideaPortfolioItems.size})",
        "Investor Portfolio (${investorPortfolioItems.size})",
        "Saved Profiles (${savedProfiles.size})",
        "Proposals"
    )
    val haptics = LocalHapticFeedbackManager.current

    val stats = (dashboardState as? UiState.Success)?.data ?: DashboardStats()
    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            haptics.success()
            onRefresh()
        },
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = isRefreshing,
                state = pullToRefreshState,
                containerColor = MaterialTheme.colorScheme.surface,
                color = MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_pull_refresh")
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header & Auth CTA
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (currentUser != null) "${currentUser.name}'s Dashboard" else "Innovator Dashboard",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = currentUser?.email ?: "Sign in to sync your saved pipeline & proposals.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (currentUser != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                haptics.tap()
                                onRefresh()
                            },
                            enabled = !isRefreshing,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("dashboard_sync_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Sync dashboard with backend",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                haptics.reject()
                                onSignOut()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseError),
                            border = BorderStroke(1.dp, RoseError.copy(alpha = 0.5f)),
                            modifier = Modifier.heightIn(min = 42.dp).testTag("dashboard_sign_out")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Sign Out")
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            haptics.tap()
                            onOpenAuth()
                        },
                        modifier = Modifier.heightIn(min = 44.dp).testTag("dashboard_login_cta")
                    ) {
                        Icon(Icons.Filled.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Login")
                    }
                }
            }
        }

        if (currentUser != null) {
            item {
                UnifiedIdentitySyncCard(
                    sessionState = authSessionState,
                    onRetrySync = {
                        haptics.tap()
                        onRetryAuthSync()
                    }
                )
            }
        }

        // 4 Exact Stats Cards
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DashboardStatCard(
                        label = "Innovations Posted",
                        value = stats.innovationsPosted.toString(),
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    DashboardStatCard(
                        label = "Saved Opportunities",
                        value = stats.savedOpportunities.toString(),
                        accent = GoldAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DashboardStatCard(
                        label = "Proposals Received",
                        value = stats.proposalsReceived.toString(),
                        accent = EmeraldSuccess,
                        modifier = Modifier.weight(1f)
                    )
                    DashboardStatCard(
                        label = "Total Upvotes / Likes",
                        value = stats.totalUpvotes.toString(),
                        accent = RoseError,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Interactive 3D Capital & Pipeline Skyline
        item(key = "dashboard_3d_pipeline") {
            CapitalPipeline3D(
                postedCount = stats.innovationsPosted,
                savedCount = stats.savedOpportunities,
                proposalsCount = stats.proposalsReceived,
                upvotesCount = stats.totalUpvotes
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        haptics.tap()
                        onOpenPricing()
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("dashboard_pricing_button")
                ) {
                    Icon(Icons.Filled.RocketLaunch, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Plans & Pricing", maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        haptics.tap()
                        onOpenMatches()
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("dashboard_find_matches_button")
                ) {
                    Icon(Icons.Filled.Hub, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Find Matches", maxLines = 1)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        haptics.tap()
                        onOpenMessages()
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("dashboard_messages_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (unreadMessagesCount > 0) "Messages ($unreadMessagesCount)" else "Messages",
                        maxLines = 1
                    )
                }
                OutlinedButton(
                    onClick = {
                        haptics.tap()
                        onOpenCollaborations()
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("dashboard_collaborations_button")
                ) {
                    Icon(Icons.Filled.Handshake, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    val badgeCount = if (pendingInvitesCount > 0) pendingInvitesCount else collaborationsCount
                    Text(
                        text = when {
                            pendingInvitesCount > 0 -> "Collabs ($pendingInvitesCount new)"
                            badgeCount > 0 -> "Collabs ($badgeCount)"
                            else -> "Collaborations"
                        },
                        maxLines = 1
                    )
                }
            }
        }

        item(key = "dashboard_firebase_diagnostics") {
            FirebaseDiagnosticsCard(
                report = diagnosticsReport,
                onRunDiagnostics = onRunDiagnostics
            )
        }

        item {
            SecondaryScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp
            ) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            haptics.tap()
                            selectedTab = index
                        },
                        text = { Text(label, maxLines = 1) },
                        modifier = Modifier.testTag("dashboard_tab_$index")
                    )
                }
            }
        }

        if (selectedTab == 2) {
            item(key = "dashboard_idea_portfolio") {
                IdeaPortfolioSection(
                    items = ideaPortfolioItems,
                    searchQuery = ideaPortfolioQuery,
                    onSearchQueryChange = onIdeaPortfolioQueryChange,
                    onAddIdea = onAddIdeaToPortfolio,
                    onUpdateNotes = onUpdateIdeaNotes,
                    onRemove = onRemoveIdeaFromPortfolio,
                    onOpenOrAnalyze = onOpenIdeaPortfolioItem
                )
            }
        } else if (selectedTab == 3) {
            item(key = "dashboard_investor_portfolio") {
                InvestorPortfolioSection(
                    items = investorPortfolioItems,
                    searchQuery = investorPortfolioQuery,
                    statusFilter = investorStatusFilter,
                    onSearchQueryChange = onInvestorPortfolioQueryChange,
                    onStatusFilterChange = onInvestorStatusFilterChange,
                    onAddDeal = onAddInvestorDeal,
                    onUpdateDiligence = onUpdateInvestorDiligence,
                    onRemove = onRemoveInvestorDeal,
                    onOpenOrAnalyze = onOpenInvestorPortfolioItem
                )
            }
        } else if (selectedTab == 4) {
            item(key = "dashboard_saved_profiles") {
                SavedProfilesSection(
                    items = savedProfiles,
                    searchQuery = savedProfilesQuery,
                    onSearchQueryChange = onSavedProfilesQueryChange,
                    onRemove = onRemoveSavedProfile,
                    onBrowseCollaborators = onBrowseCollaborators,
                    onMessageProfile = onMessageSavedProfile
                )
            }
        } else if (currentUser == null) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Sign in to access your Dashboard", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Track your posted innovations, bookmarked opportunities, and inbound collaboration proposals.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                haptics.tap()
                                onOpenAuth()
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text("Sign In / Create Account")
                        }
                    }
                }
            }
        } else {
            when (dashboardState) {
                UiState.Loading -> {
                    items(3) {
                        InnovationCardSkeleton(
                            modifier = Modifier.testTag("dashboard_skeleton_loader")
                        )
                    }
                }
                is UiState.Error -> {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(dashboardState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                Button(
                                    onClick = {
                                        haptics.tap()
                                        onRefresh()
                                    }
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Refresh")
                                }
                            }
                        }
                    }
                }
                is UiState.Success -> {
                    item {
                        AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = {
                                if (targetState > initialState) {
                                    (slideInHorizontally(tween(240)) { it / 4 } + fadeIn(tween(240)))
                                        .togetherWith(slideOutHorizontally(tween(200)) { -it / 4 } + fadeOut(tween(180)))
                                } else {
                                    (slideInHorizontally(tween(240)) { -it / 4 } + fadeIn(tween(240)))
                                        .togetherWith(slideOutHorizontally(tween(200)) { it / 4 } + fadeOut(tween(180)))
                                }
                            },
                            label = "dashboard_tab_transition"
                        ) { tabIdx ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                when (tabIdx) {
                                    0 -> {
                                        if (stats.myInnovations.isEmpty()) {
                                            EmptyTabMessage("No posted innovations yet.")
                                        } else {
                                            stats.myInnovations.forEach { item ->
                                                BackendInnovationCard(
                                                    innovation = item,
                                                    onClick = { onOpenDetail(item) },
                                                    onToggleLike = { onToggleLike(item) },
                                                    onToggleBookmark = { onToggleBookmark(item) },
                                                    analyticsSource = "dashboard_card"
                                                )
                                            }
                                        }
                                    }
                                    1 -> {
                                        if (stats.savedList.isEmpty()) {
                                            EmptyTabMessage("No saved opportunities yet. Bookmark innovations in the Marketplace.")
                                        } else {
                                            stats.savedList.forEach { item ->
                                                BackendInnovationCard(
                                                    innovation = item,
                                                    onClick = { onOpenDetail(item) },
                                                    onToggleLike = { onToggleLike(item) },
                                                    onToggleBookmark = { onToggleBookmark(item) },
                                                    analyticsSource = "dashboard_card"
                                                )
                                            }
                                        }
                                    }
                                    else -> {
                                        if (stats.inboundProposals.isEmpty() && incomingProposals.isEmpty() && sentProposals.isEmpty()) {
                                            EmptyTabMessage("No collaboration proposals yet.")
                                        } else {
                                            if (incomingProposals.isNotEmpty()) {
                                                Text(
                                                    text = "Inbound Proposals (${incomingProposals.size})",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                incomingProposals.forEach { prop ->
                                                    FirestoreProposalCard(
                                                        proposal = prop,
                                                        isInbound = true,
                                                        isBusy = isBusy,
                                                        onAccept = { onRespondToProposal(prop.id, true) },
                                                        onDecline = { onRespondToProposal(prop.id, false) }
                                                    )
                                                }
                                            }
                                            if (stats.inboundProposals.isNotEmpty()) {
                                                Text(
                                                    text = "Marketplace Inbound (${stats.inboundProposals.size})",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                stats.inboundProposals.forEach { prop ->
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.surface,
                                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                                        shape = RoundedCornerShape(16.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(14.dp),
                                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(prop.innovationTitle, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                                                Surface(
                                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                                    shape = RoundedCornerShape(8.dp)
                                                                ) {
                                                                    Text(
                                                                        prop.proposalType,
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                                    )
                                                                }
                                                            }
                                                            Text(
                                                                "From ${prop.senderName}${if (prop.amount.isNotBlank()) " · ${prop.amount}" else ""}",
                                                                style = MaterialTheme.typography.labelMedium,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                            Text(prop.message, style = MaterialTheme.typography.bodySmall)
                                                            if (prop.contactInfo.isNotBlank()) {
                                                                Text(
                                                                    "Contact: ${prop.contactInfo}",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            if (sentProposals.isNotEmpty()) {
                                                Text(
                                                    text = "Sent Requests (${sentProposals.size})",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                sentProposals.forEach { prop ->
                                                    FirestoreProposalCard(
                                                        proposal = prop,
                                                        isInbound = false,
                                                        isBusy = isBusy,
                                                        onAccept = {},
                                                        onDecline = {}
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
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
    }
}

@Composable
private fun DashboardStatCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyTabMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FirestoreProposalCard(
    proposal: Proposal,
    isInbound: Boolean,
    isBusy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val haptics = LocalHapticFeedbackManager.current
    val statusColor = when (proposal.status) {
        "ACCEPTED" -> EmeraldSuccess
        "DECLINED" -> RoseError
        else -> GoldAmber
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag("proposal_card_${proposal.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = proposal.innovationTitle,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = statusColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = proposal.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = if (isInbound) {
                    "From ${proposal.senderName} · ${proposal.proposalType}${proposal.amount?.let { " · $it" }.orEmpty()}"
                } else {
                    "To ${proposal.recipientName ?: "Partner"} · ${proposal.proposalType}${proposal.amount?.let { " · $it" }.orEmpty()}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Shared scope: ${proposal.sharedIdeaScope}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!proposal.sharedIdeaSummary.isNullOrBlank()) {
                Text(
                    text = proposal.sharedIdeaSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(proposal.message, style = MaterialTheme.typography.bodySmall)
            if (!proposal.contactInfo.isNullOrBlank()) {
                Text(
                    text = "Contact: ${proposal.contactInfo}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isInbound && proposal.status == "PENDING") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            haptics.success()
                            onAccept()
                        },
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Accept")
                    }
                    OutlinedButton(
                        onClick = {
                            haptics.reject()
                            onDecline()
                        },
                        enabled = !isBusy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseError),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Decline")
                    }
                }
            }
        }
    }
}

@Composable
private fun UnifiedIdentitySyncCard(
    sessionState: UnifiedAuthSessionState,
    onRetrySync: () -> Unit
) {
    val isDegraded = sessionState.syncStatus == AuthSyncStatus.BACKEND_DEGRADED ||
        sessionState.syncStatus == AuthSyncStatus.FIREBASE_DEGRADED
    val isSyncing = sessionState.syncStatus == AuthSyncStatus.SYNCING
    val accentColor = when (sessionState.syncStatus) {
        AuthSyncStatus.SYNCED -> EmeraldSuccess
        AuthSyncStatus.SYNCING -> MaterialTheme.colorScheme.primary
        AuthSyncStatus.BACKEND_DEGRADED, AuthSyncStatus.FIREBASE_DEGRADED -> GoldAmber
        AuthSyncStatus.SIGNED_OUT -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusTitle = when (sessionState.syncStatus) {
        AuthSyncStatus.SYNCED -> "Cloud & Marketplace Synced"
        AuthSyncStatus.SYNCING -> sessionState.statusNotice ?: "Syncing unified account..."
        AuthSyncStatus.BACKEND_DEGRADED -> sessionState.statusNotice ?: "Cloud workspace active · Marketplace sync pending"
        AuthSyncStatus.FIREBASE_DEGRADED -> sessionState.statusNotice ?: "Marketplace session active · Cloud sync pending"
        AuthSyncStatus.SIGNED_OUT -> "Not synced"
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_unified_auth_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = accentColor
                )
            } else {
                Icon(
                    imageVector = if (sessionState.syncStatus == AuthSyncStatus.SYNCED) {
                        Icons.Filled.CloudDone
                    } else {
                        Icons.Filled.Sync
                    },
                    contentDescription = statusTitle,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = statusTitle,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (sessionState.linkedProviders.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sessionState.linkedProviders.forEach { provider ->
                            val label = when (provider.lowercase()) {
                                "google" -> "Google Linked"
                                "password" -> "Email & Password"
                                else -> provider.replaceFirstChar { it.uppercase() }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(accentColor)
                                    )
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isDegraded) {
                TextButton(
                    onClick = onRetrySync,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("dashboard_retry_auth_sync")
                ) {
                    Text(
                        text = "Sync Now",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}


