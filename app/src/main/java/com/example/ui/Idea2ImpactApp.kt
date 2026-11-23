package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.R
import com.example.analytics.FirebaseAnalyticsTracker
import com.example.analytics.LocalAnalyticsTracker
import com.example.data.local.Idea2ImpactDatabase
import com.example.data.local.toBackendInnovation
import com.example.data.local.toBackendTalent
import com.example.data.model.UiState
import com.example.data.preferences.NotificationLocalDataStore
import com.example.data.preferences.ThemePreferences
import com.example.data.preferences.TokenManager
import com.example.data.repository.AiCopilotRepository
import com.example.data.repository.BackendRepository
import com.example.data.repository.CollaborationRepository
import com.example.data.repository.EcosystemRepository
import com.example.data.repository.IdeaRepository
import com.example.data.repository.MessagingRepository
import com.example.data.repository.PortfolioRepository
import com.example.data.repository.StartupWorkbookRepository
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.signOutWithCredentialClear
import com.example.ui.components.BackendAuthModal
import com.example.ui.components.FirebaseDiagnosticsDialog
import com.example.ui.components.LocalNavAnimatedVisibilityScope
import com.example.ui.components.LocalSharedTransitionScope
import com.example.ui.components.MovableAiPopup
import com.example.ui.components.NotificationCenterSheet
import com.example.ui.components.OpeningSplashOverlay
import com.example.ui.screens.CollaborationWorkspaceScreen
import com.example.ui.screens.CollaborationsScreen
import com.example.ui.screens.CopilotScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MarketplaceScreen
import com.example.ui.screens.MatchesScreen
import com.example.ui.screens.MessagesScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PricingScreen
import com.example.ui.screens.TalentScreen
import com.example.ui.theme.Idea2ImpactTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await
import com.example.ui.viewmodel.AppViewModel
import com.example.util.LocalHapticFeedbackManager
import com.example.util.StartupSoundPlayer
import com.example.util.rememberHapticFeedbackManager
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute {
    @Serializable data object Home : AppRoute
    @Serializable data object Marketplace : AppRoute
    @Serializable data object Copilot : AppRoute
    @Serializable data object Talents : AppRoute
    @Serializable data object Dashboard : AppRoute
    @Serializable data object Detail : AppRoute
    @Serializable data object Pricing : AppRoute
    @Serializable data object Matches : AppRoute
    @Serializable data object Messages : AppRoute
    @Serializable data object Collaborations : AppRoute
    @Serializable data object CollaborationWorkspace : AppRoute
}

private data class BottomTab(
    val route: AppRoute,
    val qualifiedName: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private fun routeOrder(route: String?): Int {
    val name = route.orEmpty()
    return when {
        name.contains("Home") -> 0
        name.contains("Marketplace") -> 1
        name.contains("Copilot") -> 2
        name.contains("Talents") -> 3
        name.contains("Dashboard") -> 4
        name.contains("Detail") || name.contains("Pricing") || name.contains("Matches") ||
            name.contains("Messages") || name.contains("Collaboration") -> 5
        else -> 0
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun Idea2ImpactApp() {
    val context = LocalContext.current
    val authScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val analyticsTracker = remember(context) { FirebaseAnalyticsTracker(context) }
    val viewModel: AppViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val database = Idea2ImpactDatabase.getInstance(app)
                val tokenManager = TokenManager(app)
                val themePreferences = ThemePreferences(app)
                val notificationDataStore = NotificationLocalDataStore(app)
                val ecosystemRepository = runCatching { EcosystemRepository(app) }.getOrNull()
                val ideaRepository = runCatching { IdeaRepository(app) }.getOrNull()
                val messagingRepository = runCatching { MessagingRepository(app) }.getOrNull()
                val collaborationRepository = runCatching { CollaborationRepository(app) }.getOrNull()
                val repository = BackendRepository(
                    tokenManager = tokenManager,
                    innovationDao = database.innovationDao(),
                    searchHistoryDao = database.searchHistoryDao(),
                    firebaseIdTokenProvider = { forceRefresh ->
                        runCatching {
                            Firebase.auth.currentUser?.getIdToken(forceRefresh)?.await()?.token
                        }.getOrNull()
                    }
                )
                val portfolioRepository = PortfolioRepository(
                    ideaPortfolioDao = database.ideaPortfolioDao(),
                    investorPortfolioDao = database.investorPortfolioDao(),
                    savedProfileDao = database.savedProfileDao()
                )
                AppViewModel(
                    repository = repository,
                    tokenManager = tokenManager,
                    themePreferences = themePreferences,
                    aiCopilotRepository = AiCopilotRepository(app.applicationContext),
                    notificationDataStore = notificationDataStore,
                    analyticsTracker = analyticsTracker,
                    ecosystemRepository = ecosystemRepository,
                    ideaRepository = ideaRepository,
                    appContext = app.applicationContext,
                    portfolioRepository = portfolioRepository,
                    messagingRepository = messagingRepository,
                    collaborationRepository = collaborationRepository,
                    startupWorkbookRepository = StartupWorkbookRepository(app.applicationContext)
                )
            }
        }
    )

    val isDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val isSoundEnabled by viewModel.isSoundEnabled.collectAsStateWithLifecycle()
    val hasCompletedOnboarding by viewModel.hasCompletedOnboarding.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val unreadNotificationsCount by viewModel.unreadNotificationsCount.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val authSessionState by viewModel.authSessionState.collectAsStateWithLifecycle()
    val showAuthModal by viewModel.showAuthModal.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val isAuthBusy by viewModel.isAuthBusy.collectAsStateWithLifecycle()

    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val innovationsState by viewModel.innovationsState.collectAsStateWithLifecycle()
    val isSyncingInnovations by viewModel.isSyncingInnovations.collectAsStateWithLifecycle()
    val isShowingOfflineCache by viewModel.isShowingOfflineCache.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedStage by viewModel.selectedStage.collectAsStateWithLifecycle()
    val selectedSort by viewModel.selectedSort.collectAsStateWithLifecycle()

    val selectedDetail by viewModel.selectedDetail.collectAsStateWithLifecycle()
    val detailComments by viewModel.detailComments.collectAsStateWithLifecycle()
    val signedNdas by viewModel.signedNdas.collectAsStateWithLifecycle()
    val pitchDeckState by viewModel.pitchDeckState.collectAsStateWithLifecycle()

    val talentsState by viewModel.talentsState.collectAsStateWithLifecycle()
    val selectedTalentRole by viewModel.selectedTalentRole.collectAsStateWithLifecycle()
    val myTalentProfile by viewModel.myTalentProfile.collectAsStateWithLifecycle()
    val verifiedOrganizations by viewModel.verifiedOrganizations.collectAsStateWithLifecycle()
    val savedOpportunityIds by viewModel.savedOpportunityIds.collectAsStateWithLifecycle()
    val incomingFirestoreProposals by viewModel.incomingFirestoreProposals.collectAsStateWithLifecycle()
    val sentFirestoreProposals by viewModel.sentFirestoreProposals.collectAsStateWithLifecycle()

    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val isRefreshingDashboard by viewModel.isRefreshingDashboard.collectAsStateWithLifecycle()
    val ideaPortfolioItems by viewModel.ideaPortfolioItems.collectAsStateWithLifecycle()
    val ideaPortfolioIds by viewModel.ideaPortfolioIds.collectAsStateWithLifecycle()
    val ideaPortfolioQuery by viewModel.ideaPortfolioQuery.collectAsStateWithLifecycle()
    val investorPortfolioItems by viewModel.investorPortfolioItems.collectAsStateWithLifecycle()
    val investorPortfolioIds by viewModel.investorPortfolioIds.collectAsStateWithLifecycle()
    val investorPortfolioQuery by viewModel.investorPortfolioQuery.collectAsStateWithLifecycle()
    val investorStatusFilter by viewModel.investorStatusFilter.collectAsStateWithLifecycle()
    val savedProfiles by viewModel.savedProfiles.collectAsStateWithLifecycle()
    val savedProfileIds by viewModel.savedProfileIds.collectAsStateWithLifecycle()
    val savedProfilesQuery by viewModel.savedProfilesQuery.collectAsStateWithLifecycle()

    val availableInnovations by viewModel.availableInnovations.collectAsStateWithLifecycle()
    val focusedInnovation by viewModel.focusedInnovation.collectAsStateWithLifecycle()
    val useFastAiModel by viewModel.useFastAiModel.collectAsStateWithLifecycle()
    val copilotMessages by viewModel.copilotMessages.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
    val aiStatusState by viewModel.aiStatusState.collectAsStateWithLifecycle()
    val canRegenerateLastReply by viewModel.canRegenerateLastReply.collectAsStateWithLifecycle()
    val customApiKey by viewModel.customApiKey.collectAsStateWithLifecycle()

    val isBusy by viewModel.isActionBusy.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val networkDiagnosticState by viewModel.networkDiagnosticState.collectAsStateWithLifecycle()
    val diagnosticsReport by viewModel.diagnosticsReport.collectAsStateWithLifecycle()

    val firebaseUid by viewModel.firebaseUid.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val unreadMessagesCount by viewModel.unreadMessagesCount.collectAsStateWithLifecycle()
    val activeConversation by viewModel.activeConversation.collectAsStateWithLifecycle()
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val collaborations by viewModel.collaborations.collectAsStateWithLifecycle()
    val incomingCollabInvites by viewModel.incomingCollabInvites.collectAsStateWithLifecycle()
    val pendingInvitesCount by viewModel.pendingInvitesCount.collectAsStateWithLifecycle()
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()
    val activeProjectTasks by viewModel.activeProjectTasks.collectAsStateWithLifecycle()
    val activeProjectFiles by viewModel.activeProjectFiles.collectAsStateWithLifecycle()
    val activeProjectActivity by viewModel.activeProjectActivity.collectAsStateWithLifecycle()
    val activeTaskComments by viewModel.activeTaskComments.collectAsStateWithLifecycle()

    var showOpeningSplash by rememberSaveable { mutableStateOf(true) }
    var isAiPopupExpanded by rememberSaveable { mutableStateOf(false) }
    var showNotificationsSheet by rememberSaveable { mutableStateOf(false) }
    var showDiagnosticsDialog by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = isAiPopupExpanded) {
        isAiPopupExpanded = false
    }

    LaunchedEffect(isSoundEnabled) {
        StartupSoundPlayer.setSoundEnabled(context, isSoundEnabled)
    }

    LaunchedEffect(pitchDeckState) {
        if (pitchDeckState is UiState.Success) {
            StartupSoundPlayer.playSuccess(context)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(Unit) {
        if (currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    viewModel.onGoogleAuthSuccess(isAutoSignIn = true)
                },
                onUnauthenticated = {},
                scope = authScope
            )
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRouteName = navBackStackEntry?.destination?.route.orEmpty()

    var hasTrackedInitialOnboarding by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(hasCompletedOnboarding) {
        if (hasCompletedOnboarding == false) {
            analyticsTracker.logScreenView("onboarding", "OnboardingScreen")
            if (!hasTrackedInitialOnboarding) {
                hasTrackedInitialOnboarding = true
                analyticsTracker.logOnboardingStarted(source = "first_launch")
            }
        }
    }

    LaunchedEffect(currentRouteName, hasCompletedOnboarding) {
        if (hasCompletedOnboarding == true && currentRouteName.isNotBlank()) {
            val screenName = when {
                currentRouteName.contains("Home") -> "home"
                currentRouteName.contains("Marketplace") -> "marketplace"
                currentRouteName.contains("Copilot") -> "ai_copilot"
                currentRouteName.contains("Talents") -> "talents"
                currentRouteName.contains("Dashboard") -> "dashboard"
                currentRouteName.contains("Detail") -> "innovation_detail"
                currentRouteName.contains("Pricing") -> "pricing"
                currentRouteName.contains("Matches") -> "ecosystem_matches"
                currentRouteName.contains("Messages") -> "messages"
                currentRouteName.contains("CollaborationWorkspace") -> "collaboration_workspace"
                currentRouteName.contains("Collaborations") -> "collaborations"
                else -> currentRouteName.substringAfterLast('.')
            }
            analyticsTracker.logScreenView(screenName, currentRouteName.substringAfterLast('.'))
        }
    }

    // Exact 5 Bottom Navigation Tabs in exact requested order
    val bottomTabs = remember {
        listOf(
            BottomTab(AppRoute.Home, AppRoute.Home::class.qualifiedName.orEmpty(), "Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
            BottomTab(AppRoute.Marketplace, AppRoute.Marketplace::class.qualifiedName.orEmpty(), "Marketplace", Icons.Filled.Explore, Icons.Outlined.Explore, "nav_marketplace"),
            BottomTab(AppRoute.Copilot, AppRoute.Copilot::class.qualifiedName.orEmpty(), "AI Sana", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_copilot"),
            BottomTab(AppRoute.Talents, AppRoute.Talents::class.qualifiedName.orEmpty(), "Talents", Icons.Filled.Groups, Icons.Outlined.Groups, "nav_talents"),
            BottomTab(AppRoute.Dashboard, AppRoute.Dashboard::class.qualifiedName.orEmpty(), "Dashboard", Icons.Filled.BarChart, Icons.Outlined.BarChart, "nav_dashboard")
        )
    }

    val showMainChrome = !currentRouteName.contains("Detail") &&
        !currentRouteName.contains("Pricing") &&
        !currentRouteName.contains("Matches") &&
        !currentRouteName.contains("Messages") &&
        !currentRouteName.contains("Collaboration")

    val hapticManager = rememberHapticFeedbackManager()

    fun navigateTopTab(tab: BottomTab) {
        hapticManager.tap()
        StartupSoundPlayer.playTap(context)
        navController.navigate(tab.route) {
            popUpTo(AppRoute.Home) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Idea2ImpactTheme(darkTheme = isDark) {
        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this@SharedTransitionLayout,
                LocalHapticFeedbackManager provides hapticManager,
                LocalAnalyticsTracker provides analyticsTracker
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 700.dp
            val isCompactTopBar = maxWidth < 480.dp
            val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

            Row(modifier = Modifier.fillMaxSize()) {
                if (useRail && showMainChrome) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        bottomTabs.forEach { tab ->
                            val selected = currentRouteName.startsWith(tab.qualifiedName)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopTab(tab) },
                                icon = { Icon(if (selected) tab.selectedIcon else tab.unselectedIcon, contentDescription = null) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.weight(1f),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        if (showMainChrome) {
                            var showUserMenu by remember { mutableStateOf(false) }
                            var showCompactMoreMenu by remember { mutableStateOf(false) }
                            TopAppBar(
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                title = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(if (isCompactTopBar) 6.dp else 8.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                hapticManager.tap()
                                                showOpeningSplash = true
                                                StartupSoundPlayer.playAppOpenChime(context)
                                            }
                                            .padding(horizontal = 4.dp, vertical = 4.dp)
                                            .testTag("brand_logo_replay_intro")
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(9.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                                            modifier = Modifier.size(if (isCompactTopBar) 28.dp else 30.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Filled.AutoAwesome,
                                                    contentDescription = stringResource(R.string.cd_brand_mark),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(if (isCompactTopBar) 16.dp else 18.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "IDEA2IMPACT",
                                            style = if (isCompactTopBar) {
                                                MaterialTheme.typography.titleSmall.copy(
                                                    fontSize = 15.sp,
                                                    letterSpacing = (-0.2).sp
                                                )
                                            } else {
                                                MaterialTheme.typography.titleMedium
                                            },
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Clip
                                        )
                                    }
                                },
                                actions = {
                                    IconButton(
                                        onClick = {
                                            hapticManager.tap()
                                            StartupSoundPlayer.playTap(context)
                                            navController.navigate(AppRoute.Messages)
                                        },
                                        modifier = Modifier.size(if (isCompactTopBar) 44.dp else 48.dp).testTag("header_messages_button")
                                    ) {
                                        BadgedBox(
                                            badge = {
                                                if (unreadMessagesCount > 0) {
                                                    Badge {
                                                        Text(
                                                            text = if (unreadMessagesCount > 9) "9+" else unreadMessagesCount.toString()
                                                        )
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                                contentDescription = "Open messages",
                                                tint = if (unreadMessagesCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            hapticManager.tap()
                                            StartupSoundPlayer.playTap(context)
                                            showNotificationsSheet = true
                                        },
                                        modifier = Modifier.size(if (isCompactTopBar) 44.dp else 48.dp).testTag("header_notifications_button")
                                    ) {
                                        BadgedBox(
                                            badge = {
                                                if (unreadNotificationsCount > 0) {
                                                    Badge {
                                                        Text(
                                                            text = if (unreadNotificationsCount > 9) "9+" else unreadNotificationsCount.toString()
                                                        )
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Notifications,
                                                contentDescription = "Open notifications",
                                                tint = if (unreadNotificationsCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (!isCompactTopBar) {
                                        IconButton(
                                            onClick = {
                                                val nextEnabled = !isSoundEnabled
                                                hapticManager.toggle(nextEnabled)
                                                StartupSoundPlayer.setSoundEnabled(context, nextEnabled)
                                                viewModel.setSoundEnabled(nextEnabled)
                                                if (nextEnabled) {
                                                    StartupSoundPlayer.playTap(context)
                                                }
                                            },
                                            modifier = Modifier.size(48.dp).testTag("sound_toggle_button")
                                        ) {
                                            Icon(
                                                imageVector = if (isSoundEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                                                contentDescription = if (isSoundEnabled) "Mute sound effects" else "Enable sound effects",
                                                tint = if (isSoundEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                hapticManager.toggle(!isDark)
                                                StartupSoundPlayer.playTap(context)
                                                viewModel.toggleTheme()
                                            },
                                            modifier = Modifier.size(48.dp).testTag("theme_toggle_button")
                                        ) {
                                            Icon(
                                                imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                                contentDescription = if (isDark) "Switch to Light Mode" else "Switch to Dark Mode"
                                            )
                                        }
                                    } else {
                                        Box {
                                            IconButton(
                                                onClick = {
                                                    hapticManager.tap()
                                                    showCompactMoreMenu = true
                                                },
                                                modifier = Modifier.size(44.dp).testTag("header_more_menu")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.MoreVert,
                                                    contentDescription = "Appearance and sound settings",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showCompactMoreMenu,
                                                onDismissRequest = { showCompactMoreMenu = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(if (isSoundEnabled) "Sound: On" else "Sound: Off") },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = if (isSoundEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                                                            contentDescription = null
                                                        )
                                                    },
                                                    onClick = {
                                                        val nextEnabled = !isSoundEnabled
                                                        hapticManager.toggle(nextEnabled)
                                                        StartupSoundPlayer.setSoundEnabled(context, nextEnabled)
                                                        viewModel.setSoundEnabled(nextEnabled)
                                                        if (nextEnabled) StartupSoundPlayer.playTap(context)
                                                        showCompactMoreMenu = false
                                                    },
                                                    modifier = Modifier.testTag("sound_toggle_button")
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(if (isDark) "Light Theme" else "Dark Theme") },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                                            contentDescription = null
                                                        )
                                                    },
                                                    onClick = {
                                                        hapticManager.toggle(!isDark)
                                                        StartupSoundPlayer.playTap(context)
                                                        viewModel.toggleTheme()
                                                        showCompactMoreMenu = false
                                                    },
                                                    modifier = Modifier.testTag("theme_toggle_button")
                                                )
                                            }
                                        }
                                    }

                                    val user = currentUser
                                    if (user == null) {
                                        Button(
                                            onClick = {
                                                hapticManager.tap()
                                                StartupSoundPlayer.playTap(context)
                                                viewModel.openAuthModal()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(
                                                horizontal = if (isCompactTopBar) 10.dp else 12.dp,
                                                vertical = 6.dp
                                            ),
                                            modifier = Modifier
                                                .padding(end = if (isCompactTopBar) 6.dp else 10.dp)
                                                .heightIn(min = 36.dp)
                                                .testTag("header_login_button")
                                        ) {
                                            Text("Login", maxLines = 1)
                                        }
                                    } else {
                                        Box(modifier = Modifier.padding(end = if (isCompactTopBar) 8.dp else 12.dp)) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clickable {
                                                        hapticManager.tap()
                                                        showUserMenu = true
                                                    }
                                                    .testTag("header_user_avatar")
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    if (!user.avatarUrl.isNullOrBlank()) {
                                                        AsyncImage(
                                                            model = user.avatarUrl,
                                                            contentDescription = "${user.name} profile photo",
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .clip(CircleShape)
                                                        )
                                                    } else {
                                                        Text(
                                                            text = user.name.take(1).uppercase(),
                                                            style = MaterialTheme.typography.labelLarge,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                                        )
                                                    }
                                                }
                                            }

                                            DropdownMenu(
                                                expanded = showUserMenu,
                                                onDismissRequest = { showUserMenu = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Dashboard") },
                                                    onClick = {
                                                        showUserMenu = false
                                                        navigateTopTab(bottomTabs[4])
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            if (unreadMessagesCount > 0) "Messages ($unreadMessagesCount)"
                                                            else "Messages"
                                                        )
                                                    },
                                                    onClick = {
                                                        hapticManager.tap()
                                                        showUserMenu = false
                                                        navController.navigate(AppRoute.Messages)
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            if (pendingInvitesCount > 0) "Collaborations ($pendingInvitesCount invite)"
                                                            else "Collaborations & Projects"
                                                        )
                                                    },
                                                    onClick = {
                                                        hapticManager.tap()
                                                        showUserMenu = false
                                                        navController.navigate(AppRoute.Collaborations)
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Plans & Pricing") },
                                                    onClick = {
                                                        hapticManager.tap()
                                                        showUserMenu = false
                                                        navController.navigate(AppRoute.Pricing)
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Sign Out") },
                                                    onClick = {
                                                        hapticManager.reject()
                                                        showUserMenu = false
                                                        signOutWithCredentialClear(
                                                            credentialManager = credentialManager,
                                                            scope = authScope
                                                        ) {
                                                            viewModel.logout()
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    },
                    bottomBar = {
                        if (!useRail && showMainChrome && !isImeVisible) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 2.dp
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                                ) {
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .padding(horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        bottomTabs.forEach { tab ->
                                            val selected = currentRouteName.startsWith(tab.qualifiedName)
                                            val activeColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 48.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .selectable(
                                                        selected = selected,
                                                        onClick = { navigateTopTab(tab) },
                                                        role = Role.Tab
                                                    )
                                                    .padding(vertical = 2.dp, horizontal = 2.dp)
                                                    .testTag(tab.testTag),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(48.dp)
                                                        .height(24.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (selected) MaterialTheme.colorScheme.secondaryContainer
                                                            else Color.Transparent
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                        contentDescription = null,
                                                        tint = if (selected) activeColor else inactiveColor,
                                                        modifier = Modifier.size(19.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(1.dp))
                                                Text(
                                                    text = tab.label,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.sp,
                                                        lineHeight = 11.sp,
                                                        letterSpacing = (-0.1).sp,
                                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                                    ),
                                                    color = if (selected) MaterialTheme.colorScheme.onSurface else inactiveColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = AppRoute.Home,
                        modifier = Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding),
                        enterTransition = {
                            val from = routeOrder(initialState.destination.route)
                            val to = routeOrder(targetState.destination.route)
                            when {
                                to >= 5 -> fadeIn(tween(340, easing = FastOutSlowInEasing)) +
                                    scaleIn(initialScale = 0.94f, animationSpec = tween(340, easing = FastOutSlowInEasing))
                                to > from -> fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                                    slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { (it * 0.22f).toInt() }
                                to < from -> fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                                    slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { -(it * 0.22f).toInt() }
                                else -> fadeIn(tween(240))
                            }
                        },
                        exitTransition = {
                            val from = routeOrder(initialState.destination.route)
                            val to = routeOrder(targetState.destination.route)
                            when {
                                to >= 5 -> fadeOut(tween(300)) + scaleOut(targetScale = 0.96f, animationSpec = tween(300))
                                to > from -> fadeOut(tween(240)) +
                                    slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { -(it * 0.16f).toInt() }
                                to < from -> fadeOut(tween(240)) +
                                    slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { (it * 0.16f).toInt() }
                                else -> fadeOut(tween(200))
                            }
                        },
                        popEnterTransition = {
                            val from = routeOrder(initialState.destination.route)
                            val to = routeOrder(targetState.destination.route)
                            when {
                                from >= 5 -> fadeIn(tween(320, easing = FastOutSlowInEasing)) +
                                    scaleIn(initialScale = 0.96f, animationSpec = tween(320, easing = FastOutSlowInEasing))
                                to < from -> fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                                    slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { -(it * 0.22f).toInt() }
                                else -> fadeIn(tween(240))
                            }
                        },
                        popExitTransition = {
                            val from = routeOrder(initialState.destination.route)
                            val to = routeOrder(targetState.destination.route)
                            when {
                                from >= 5 -> fadeOut(tween(300)) + scaleOut(targetScale = 0.94f, animationSpec = tween(300))
                                to < from -> fadeOut(tween(240)) +
                                    slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { (it * 0.16f).toInt() }
                                else -> fadeOut(tween(200))
                            }
                        }
                    ) {
                        composable<AppRoute.Home> {
                            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                val liveTalentsCount = (talentsState as? UiState.Success)?.data?.size
                                    ?: com.example.data.seed.SeedCatalog.talents.size
                                HomeScreen(
                                    categories = categories,
                                    featuredInnovations = availableInnovations,
                                    talentsCount = liveTalentsCount,
                                    verifiedOrgsCount = verifiedOrganizations.size,
                                    entranceReady = !showOpeningSplash && hasCompletedOnboarding == true,
                                    onOpenDetail = { item ->
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.openInnovationDetail(item, source = "home")
                                        navController.navigate(AppRoute.Detail)
                                    },
                                    onExploreInnovations = { cat ->
                                        viewModel.setSelectedCategory(cat, source = "home_category")
                                        navigateTopTab(bottomTabs[1])
                                    },
                                    onMeetBuilders = {
                                        navigateTopTab(bottomTabs[3])
                                    },
                                    onOpenPricing = {
                                        StartupSoundPlayer.playTap(context)
                                        navController.navigate(AppRoute.Pricing)
                                    },
                                    onReplayOnboarding = {
                                        StartupSoundPlayer.playTap(context)
                                        hasTrackedInitialOnboarding = true
                                        viewModel.replayOnboarding()
                                    }
                                )
                            }
                        }

                        composable<AppRoute.Marketplace> {
                            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                MarketplaceScreen(
                                    innovationsState = innovationsState,
                                    isRefreshing = isSyncingInnovations,
                                    isOfflineCache = isShowingOfflineCache,
                                    searchQuery = searchQuery,
                                    recentSearches = recentSearches,
                                    selectedCategory = selectedCategory,
                                    selectedStage = selectedStage,
                                    selectedSort = selectedSort,
                                    onSearchChange = { viewModel.setSearchQuery(it) },
                                    onSubmitSearch = { viewModel.submitSearchQuery(it) },
                                    onSelectRecentSearch = {
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.selectRecentSearch(it)
                                    },
                                    onDeleteRecentSearch = { viewModel.deleteRecentSearch(it) },
                                    onClearRecentSearches = { viewModel.clearRecentSearches() },
                                    onCategoryChange = {
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.setSelectedCategory(it, source = "filter_chip")
                                    },
                                    onCycleCategory = {
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.setSelectedCategory(it, source = "tesseract_3d")
                                    },
                                    onStageChange = {
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.setSelectedStage(it)
                                    },
                                    onSortChange = {
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.setSelectedSort(it)
                                    },
                                    onClearFilters = {
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.clearMarketplaceFilters()
                                    },
                                    onRetry = {
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.refreshMarketplace(source = "retry_button")
                                    },
                                    onPullRefresh = {
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.refreshMarketplace(source = "pull_to_refresh")
                                    },
                                    onOfflineSync = {
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.refreshMarketplace(source = "offline_sync")
                                    },
                                    onOpenDetail = { item ->
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.openInnovationDetail(item, source = "marketplace")
                                        navController.navigate(AppRoute.Detail)
                                    },
                                    onToggleLike = {
                                        StartupSoundPlayer.playSuccess(context)
                                        viewModel.toggleLike(it)
                                    },
                                    onToggleBookmark = {
                                        StartupSoundPlayer.playSuccess(context)
                                        viewModel.toggleBookmark(it)
                                    }
                                )
                            }
                        }

                        composable<AppRoute.Copilot> {
                            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                CopilotScreen(
                                    messages = copilotMessages,
                                    isLoading = isAiLoading,
                                    aiStatus = aiStatusState,
                                    customApiKey = customApiKey,
                                    availableInnovations = availableInnovations,
                                    focusedInnovation = focusedInnovation,
                                    useFastModel = useFastAiModel,
                                    canRegenerate = canRegenerateLastReply,
                                    networkDiagnosticManager = viewModel.networkDiagnosticManager,
                                    networkDiagnosticState = networkDiagnosticState,
                                    onSelectFocusedInnovation = {
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.setFocusedInnovation(it)
                                    },
                                    onToggleFastModel = {
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.setFastAiModel(it, surface = "full_screen")
                                    },
                                    onSaveApiKey = {
                                        StartupSoundPlayer.playSuccess(context)
                                        viewModel.saveCustomApiKey(it)
                                    },
                                    onSendPrompt = { prompt, bitmap, promptSource ->
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.sendCopilotPrompt(
                                            prompt = prompt,
                                            attachedImage = bitmap,
                                            promptSource = promptSource,
                                            isPopup = false
                                        )
                                    },
                                    onStopGeneration = {
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.stopCopilotGeneration()
                                    },
                                    onRegenerateLast = {
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.regenerateLastCopilotReply(isPopup = false)
                                    },
                                    onRetryTimedOut = { messageId ->
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.retryTimedOutCopilotMessage(messageId, isPopup = false)
                                    },
                                    onSwitchToFastAndRetry = { messageId ->
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.switchToFastAndRetry(messageId, isPopup = false)
                                    },
                                    onSwitchToDefaultAiAndRetry = { messageId ->
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.switchToDefaultAiAndRetry(messageId, isPopup = false)
                                    },
                                    onOpenDiagnostics = {
                                        StartupSoundPlayer.playTap(context)
                                        showDiagnosticsDialog = true
                                        viewModel.runConnectivityDiagnostics(context)
                                    },
                                    onResetChat = {
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.resetCopilotChat()
                                    }
                                )
                            }
                        }

                        composable<AppRoute.Talents> {
                            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                TalentScreen(
                                    talentsState = talentsState,
                                    selectedRole = selectedTalentRole,
                                    onRoleChange = {
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.setSelectedTalentRole(it)
                                    },
                                    onRetry = {
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.loadTalents()
                                    },
                                    myTalentProfile = myTalentProfile,
                                    availableInnovations = availableInnovations,
                                    verifiedOrganizations = verifiedOrganizations,
                                    savedOpportunityIds = savedOpportunityIds,
                                    isBusy = isBusy,
                                    onToggleTalentVisibility = { viewModel.toggleTalentVisibility(it) },
                                    onSaveMyTalentProfile = { name, role, headline, bio, skills, loc, isPub, onDone ->
                                        viewModel.saveMyTalentProfile(name, role, headline, bio, skills, loc, isPub) {
                                            StartupSoundPlayer.playSuccess(context)
                                            onDone()
                                        }
                                    },
                                    onSendCollaborationProposal = { talent, inn, type, amt, msg, contact, scope, summary, onDone ->
                                        viewModel.sendCollaborationProposal(talent, inn, type, amt, msg, contact, scope, summary) {
                                            StartupSoundPlayer.playSuccess(context)
                                            onDone()
                                        }
                                    },
                                    onImportVerifiedJson = { raw -> viewModel.importVerifiedDirectoryJson(raw) },
                                    onClearImportedDirectory = { viewModel.clearImportedDirectory() },
                                    onToggleSaveOrganization = { viewModel.toggleSaveOpportunity(it) },
                                    onOpenMatches = {
                                        StartupSoundPlayer.playTap(context)
                                        navController.navigate(AppRoute.Matches)
                                    },
                                    savedProfiles = savedProfiles,
                                    savedProfileIds = savedProfileIds,
                                    onToggleSaveTalent = { talent ->
                                        StartupSoundPlayer.playSuccess(context)
                                        viewModel.toggleSaveCollaboratorProfile(talent)
                                    },
                                    onMessageTalent = { talent ->
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.openDirectChatWithTalent(talent) {
                                            navController.navigate(AppRoute.Messages)
                                        }
                                    }
                                )
                            }
                        }

                        composable<AppRoute.Dashboard> {
                            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                DashboardScreen(
                                    currentUser = currentUser,
                                    dashboardState = dashboardState,
                                    isRefreshing = isRefreshingDashboard,
                                    incomingProposals = incomingFirestoreProposals,
                                    sentProposals = sentFirestoreProposals,
                                    ideaPortfolioItems = ideaPortfolioItems,
                                    ideaPortfolioQuery = ideaPortfolioQuery,
                                    investorPortfolioItems = investorPortfolioItems,
                                    investorPortfolioQuery = investorPortfolioQuery,
                                    investorStatusFilter = investorStatusFilter,
                                    isBusy = isBusy,
                                    onOpenAuth = {
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.openAuthModal()
                                    },
                                    onRefresh = {
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.loadDashboardStats(isPullRefresh = true)
                                    },
                                    onOpenDetail = { item ->
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.openInnovationDetail(item, source = "dashboard")
                                        navController.navigate(AppRoute.Detail)
                                    },
                                    onToggleLike = {
                                        StartupSoundPlayer.playSuccess(context)
                                        viewModel.toggleLike(it)
                                    },
                                    onToggleBookmark = {
                                        StartupSoundPlayer.playSuccess(context)
                                        viewModel.toggleBookmark(it)
                                    },
                                    onOpenPricing = {
                                        StartupSoundPlayer.playTap(context)
                                        navController.navigate(AppRoute.Pricing)
                                    },
                                    onOpenMatches = {
                                        StartupSoundPlayer.playTap(context)
                                        navController.navigate(AppRoute.Matches)
                                    },
                                    diagnosticsReport = diagnosticsReport,
                                    onRunDiagnostics = {
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.runConnectivityDiagnostics(context)
                                    },
                                    onRespondToProposal = { propId, accept ->
                                        viewModel.respondToProposal(propId, accept)
                                    },
                                    onIdeaPortfolioQueryChange = { viewModel.setIdeaPortfolioQuery(it) },
                                    onAddIdeaToPortfolio = { title, summary, cat, stage, priority, notes ->
                                        StartupSoundPlayer.playSuccess(context)
                                        viewModel.saveCustomIdeaToPortfolio(title, summary, cat, stage, priority, notes)
                                    },
                                    onUpdateIdeaNotes = { id, priority, notes ->
                                        StartupSoundPlayer.playSuccess(context)
                                        viewModel.updateIdeaPortfolioNotes(id, priority, notes)
                                    },
                                    onRemoveIdeaFromPortfolio = { id ->
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.removeFromIdeaPortfolio(id)
                                    },
                                    onOpenIdeaPortfolioItem = { entry ->
                                        StartupSoundPlayer.playTap(context)
                                        val linked = entry.innovationId?.let { id ->
                                            availableInnovations.firstOrNull { it.id == id }
                                        }
                                        if (linked != null) {
                                            viewModel.openInnovationDetail(linked, source = "idea_portfolio")
                                            navController.navigate(AppRoute.Detail)
                                        } else {
                                            viewModel.setFocusedInnovation(entry.toBackendInnovation())
                                            navigateTopTab(bottomTabs[2])
                                        }
                                    },
                                    onInvestorPortfolioQueryChange = { viewModel.setInvestorPortfolioQuery(it) },
                                    onInvestorStatusFilterChange = { viewModel.setInvestorStatusFilter(it) },
                                    onAddInvestorDeal = { title, summary, cat, stage, founder, goal, valEst, status, ticket, notes ->
                                        StartupSoundPlayer.playSuccess(context)
                                        viewModel.saveCustomProjectToInvestorPortfolio(
                                            title, summary, cat, stage, founder, goal, valEst, status, ticket, notes
                                        )
                                    },
                                    onUpdateInvestorDiligence = { id, status, ticket, notes ->
                                        StartupSoundPlayer.playSuccess(context)
                                        viewModel.updateInvestorDiligence(id, status, ticket, notes)
                                    },
                                    onRemoveInvestorDeal = { id ->
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.removeFromInvestorPortfolio(id)
                                    },
                                    onOpenInvestorPortfolioItem = { deal ->
                                        StartupSoundPlayer.playTap(context)
                                        val linked = deal.innovationId?.let { id ->
                                            availableInnovations.firstOrNull { it.id == id }
                                        }
                                        if (linked != null) {
                                            viewModel.openInnovationDetail(linked, source = "investor_portfolio")
                                            navController.navigate(AppRoute.Detail)
                                        } else {
                                            viewModel.setFocusedInnovation(deal.toBackendInnovation())
                                            navigateTopTab(bottomTabs[2])
                                        }
                                    },
                                    savedProfiles = savedProfiles,
                                    savedProfilesQuery = savedProfilesQuery,
                                    onSavedProfilesQueryChange = { viewModel.setSavedProfilesQuery(it) },
                                    onRemoveSavedProfile = { id ->
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.removeSavedCollaboratorProfile(id)
                                    },
                                    onBrowseCollaborators = {
                                        StartupSoundPlayer.playTap(context)
                                        navigateTopTab(bottomTabs[3])
                                    },
                                    onMessageSavedProfile = { profile ->
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.openDirectChatWithTalent(profile.toBackendTalent()) {
                                            navController.navigate(AppRoute.Messages)
                                        }
                                    },
                                    unreadMessagesCount = unreadMessagesCount,
                                    collaborationsCount = collaborations.size,
                                    pendingInvitesCount = pendingInvitesCount,
                                    onOpenMessages = {
                                        StartupSoundPlayer.playTap(context)
                                        navController.navigate(AppRoute.Messages)
                                    },
                                    onOpenCollaborations = {
                                        StartupSoundPlayer.playTap(context)
                                        navController.navigate(AppRoute.Collaborations)
                                    },
                                    authSessionState = authSessionState,
                                    onRetryAuthSync = {
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.retryUnifiedAuthSync()
                                    },
                                    onSignOut = {
                                        StartupSoundPlayer.playTap(context)
                                        signOutWithCredentialClear(
                                            credentialManager = credentialManager,
                                            scope = authScope
                                        ) {
                                            viewModel.logout()
                                        }
                                    }
                                )
                            }
                        }

                        composable<AppRoute.Detail> {
                            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                BackHandler { navController.popBackStack() }
                                val detail = selectedDetail
                                if (detail != null) {
                                    DetailScreen(
                                        innovation = detail,
                                        comments = detailComments,
                                        currentUserName = currentUser?.name ?: "Authorized Signer",
                                        signedNda = signedNdas[detail.id],
                                        pitchDeckState = pitchDeckState,
                                        isBusy = isBusy,
                                        isInIdeaPortfolio = ideaPortfolioIds.contains(detail.id),
                                        isInInvestorPortfolio = investorPortfolioIds.contains(detail.id),
                                        onBack = {
                                            StartupSoundPlayer.playTap(context)
                                            navController.popBackStack()
                                        },
                                        onToggleLike = {
                                            StartupSoundPlayer.playSuccess(context)
                                            viewModel.toggleLike(detail)
                                        },
                                        onToggleBookmark = {
                                            StartupSoundPlayer.playSuccess(context)
                                            viewModel.toggleBookmark(detail)
                                        },
                                        onToggleIdeaPortfolio = {
                                            StartupSoundPlayer.playSuccess(context)
                                            viewModel.toggleSaveToIdeaPortfolio(detail)
                                        },
                                        onToggleInvestorPortfolio = {
                                            StartupSoundPlayer.playSuccess(context)
                                            viewModel.toggleSaveToInvestorPortfolio(detail)
                                        },
                                        onPostComment = { text, onDone ->
                                            viewModel.postComment(detail.id, text) {
                                                StartupSoundPlayer.playSuccess(context)
                                                onDone()
                                            }
                                        },
                                        onSubmitProposal = { type, amount, msg, contact, onDone, onError ->
                                            viewModel.submitProposal(
                                                innovationId = detail.id,
                                                type = type,
                                                amount = amount,
                                                message = msg,
                                                contactInfo = contact,
                                                onSuccess = {
                                                    StartupSoundPlayer.playSuccess(context)
                                                    onDone()
                                                },
                                                onError = onError
                                            )
                                        },
                                        onSignNda = { name, org ->
                                            StartupSoundPlayer.playSuccess(context)
                                            viewModel.signDigitalNda(detail, name, org)
                                        },
                                        onGeneratePitchDeck = {
                                            StartupSoundPlayer.playTap(context)
                                            viewModel.generatePitchDeck(detail)
                                        },
                                        onDismissPitchDeck = { viewModel.clearPitchDeck() },
                                        onAnalyzeWithCopilot = {
                                            StartupSoundPlayer.playTap(context)
                                            viewModel.setFocusedInnovation(detail)
                                            navigateTopTab(bottomTabs[2])
                                        },
                                        onFindMatches = {
                                            StartupSoundPlayer.playTap(context)
                                            viewModel.setFocusedInnovation(detail)
                                            navController.navigate(AppRoute.Matches)
                                        },
                                        onMessageCreator = {
                                            StartupSoundPlayer.playTap(context)
                                            viewModel.openDirectChatWithInnovationCreator(detail) {
                                                navController.navigate(AppRoute.Messages)
                                            }
                                        }
                                    )
                                } else {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Select an innovation from Marketplace.")
                                    }
                                }
                            }
                        }

                        composable<AppRoute.Pricing> {
                            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                BackHandler { navController.popBackStack() }
                                PricingScreen(
                                    onBack = {
                                        StartupSoundPlayer.playTap(context)
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }

                        composable<AppRoute.Matches> {
                            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                BackHandler { navController.popBackStack() }
                                MatchesScreen(
                                    activeInnovation = focusedInnovation ?: selectedDetail,
                                    availableInnovations = availableInnovations,
                                    savedOpportunityIds = savedOpportunityIds,
                                    isBusy = isBusy,
                                    onComputeMatches = { title, desc, cat, stage, loc ->
                                        viewModel.computeEcosystemMatches(title, desc, cat, stage, loc)
                                    },
                                    onToggleSaveOrganization = { viewModel.toggleSaveOpportunity(it) },
                                    onSendCollaborationProposal = { talent, inn, type, amt, msg, contact, scope, summary, onDone ->
                                        viewModel.sendCollaborationProposal(talent, inn, type, amt, msg, contact, scope, summary) {
                                            StartupSoundPlayer.playSuccess(context)
                                            onDone()
                                        }
                                    },
                                    onBack = {
                                        StartupSoundPlayer.playTap(context)
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }

                        composable<AppRoute.Messages> {
                            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                BackHandler { navController.popBackStack() }
                                val allTalents = (talentsState as? UiState.Success)?.data.orEmpty()
                                MessagesScreen(
                                    isSignedIn = currentUser != null && firebaseUid != null,
                                    myUid = firebaseUid.orEmpty(),
                                    conversations = conversations,
                                    activeConversation = activeConversation,
                                    messages = activeMessages,
                                    availableTalents = allTalents,
                                    availableInnovations = availableInnovations,
                                    pendingInvitesCount = pendingInvitesCount,
                                    isBusy = isBusy,
                                    onBack = {
                                        StartupSoundPlayer.playTap(context)
                                        navController.popBackStack()
                                    },
                                    onOpenAuth = {
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.openAuthModal()
                                    },
                                    onSelectConversation = { id ->
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.selectConversation(id)
                                    },
                                    onMarkActiveRead = { viewModel.markActiveConversationRead() },
                                    onStartDirectChat = { talent ->
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.openDirectChatWithTalent(talent)
                                    },
                                    onCreateGroupChat = { title, members ->
                                        StartupSoundPlayer.playSuccess(context)
                                        viewModel.createGroupChat(title, members)
                                    },
                                    onSendMessage = { body, type, replyTo, aName, aMime, aSize, aPreview, aUrl, sharedInn ->
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.sendChatMessage(
                                            body = body,
                                            messageType = type,
                                            replyTo = replyTo,
                                            attachmentName = aName,
                                            attachmentMimeType = aMime,
                                            attachmentSizeBytes = aSize,
                                            attachmentPreviewBase64 = aPreview,
                                            attachmentUrl = aUrl,
                                            sharedInnovation = sharedInn
                                        )
                                    },
                                    onEditMessage = { msgId, newBody ->
                                        viewModel.editChatMessage(msgId, newBody)
                                    },
                                    onDeleteMessage = { msgId ->
                                        viewModel.deleteChatMessage(msgId)
                                    },
                                    onToggleReaction = { msg, emoji ->
                                        viewModel.toggleChatReaction(msg, emoji)
                                    },
                                    onSetTyping = { typing ->
                                        viewModel.setTypingIndicator(typing)
                                    },
                                    onTogglePin = { viewModel.togglePinConversation(it) },
                                    onToggleMute = { viewModel.toggleMuteConversation(it) },
                                    onToggleArchive = { viewModel.toggleArchiveConversation(it) },
                                    onAddGroupMember = { viewModel.addMemberToActiveGroup(it) },
                                    onRemoveGroupMember = { viewModel.removeMemberFromActiveGroup(it) },
                                    onLeaveGroup = { viewModel.leaveActiveGroup() },
                                    onToggleBlockPeer = { viewModel.toggleBlockPeerInActiveChat() },
                                    onReportPeer = { reason, details ->
                                        viewModel.reportActiveConversationPeer(reason, details)
                                    },
                                    onOpenCollaborationsHub = {
                                        StartupSoundPlayer.playTap(context)
                                        navController.navigate(AppRoute.Collaborations)
                                    },
                                    onOpenCollaborationWorkspace = { projectId ->
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.selectCollaborationProject(projectId)
                                        navController.navigate(AppRoute.CollaborationWorkspace)
                                    },
                                    onCreateCollaboration = { draft ->
                                        StartupSoundPlayer.playSuccess(context)
                                        viewModel.createCollaborationFromDraft(draft) { _, _ ->
                                            navController.navigate(AppRoute.CollaborationWorkspace)
                                        }
                                    },
                                    onOpenInnovation = { innId ->
                                        val match = availableInnovations.firstOrNull { it.id == innId }
                                        if (match != null) {
                                            StartupSoundPlayer.playTap(context)
                                            viewModel.openInnovationDetail(match, source = "chat_share")
                                            navController.navigate(AppRoute.Detail)
                                        } else {
                                            viewModel.showMessage("Innovation listing not in local cache.")
                                        }
                                    },
                                    onNotifySnackbar = { msg -> viewModel.showMessage(msg) }
                                )
                            }
                        }

                        composable<AppRoute.Collaborations> {
                            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                BackHandler { navController.popBackStack() }
                                val allTalents = (talentsState as? UiState.Success)?.data.orEmpty()
                                CollaborationsScreen(
                                    isSignedIn = currentUser != null && firebaseUid != null,
                                    projects = collaborations,
                                    incomingInvites = incomingCollabInvites,
                                    availableInnovations = availableInnovations,
                                    availableTalents = allTalents,
                                    isBusy = isBusy,
                                    onBack = {
                                        StartupSoundPlayer.playTap(context)
                                        navController.popBackStack()
                                    },
                                    onOpenAuth = {
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.openAuthModal()
                                    },
                                    onOpenProjectWorkspace = { project ->
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.selectCollaborationProject(project.id)
                                        navController.navigate(AppRoute.CollaborationWorkspace)
                                    },
                                    onRespondToInvite = { invite, accept ->
                                        viewModel.respondToCollaborationInvite(invite, accept) { projectId ->
                                            viewModel.selectCollaborationProject(projectId)
                                            navController.navigate(AppRoute.CollaborationWorkspace)
                                        }
                                    },
                                    onCreateCollaboration = { draft ->
                                        StartupSoundPlayer.playSuccess(context)
                                        viewModel.createCollaborationFromDraft(draft) { _, _ ->
                                            navController.navigate(AppRoute.CollaborationWorkspace)
                                        }
                                    }
                                )
                            }
                        }

                        composable<AppRoute.CollaborationWorkspace> {
                            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                BackHandler { navController.popBackStack() }
                                val allTalents = (talentsState as? UiState.Success)?.data.orEmpty()
                                CollaborationWorkspaceScreen(
                                    project = activeProject,
                                    myUid = firebaseUid.orEmpty(),
                                    tasks = activeProjectTasks,
                                    files = activeProjectFiles,
                                    activity = activeProjectActivity,
                                    activeTaskComments = activeTaskComments,
                                    availableTalents = allTalents,
                                    isBusy = isBusy,
                                    onBack = {
                                        StartupSoundPlayer.playTap(context)
                                        navController.popBackStack()
                                    },
                                    onOpenProjectChat = { convId ->
                                        StartupSoundPlayer.playTap(context)
                                        viewModel.selectConversation(convId)
                                        navController.navigate(AppRoute.Messages)
                                    },
                                    onUpdateProjectStatus = { viewModel.updateCollaborationStatus(it) },
                                    onPostUpdate = { viewModel.postProjectUpdate(it) },
                                    onCreateTask = { title, desc, aUid, aName, priority, dueMs, attach ->
                                        viewModel.createProjectTask(title, desc, aUid, aName, priority, dueMs, attach)
                                    },
                                    onUpdateTaskStatus = { task, status ->
                                        viewModel.updateProjectTaskStatus(task, status)
                                    },
                                    onDeleteTask = { viewModel.deleteProjectTask(it) },
                                    onSelectTaskForComments = { viewModel.selectTaskForComments(it) },
                                    onAddTaskComment = { task, body ->
                                        viewModel.addProjectTaskComment(task, body)
                                    },
                                    onUploadFile = { fName, mime, size, refUrl, preview, notes ->
                                        viewModel.uploadProjectFile(fName, mime, size, refUrl, preview, notes)
                                    },
                                    onDeleteFile = { viewModel.deleteProjectFile(it) },
                                    onInviteMember = { talent, role, msg ->
                                        viewModel.inviteCollaboratorToActiveProject(talent, role, msg)
                                    },
                                    onUpdateMemberRole = { uid, role ->
                                        viewModel.updateCollaborationMemberRole(uid, role)
                                    },
                                    onRemoveMember = { viewModel.removeCollaborationMember(it) },
                                    onLeaveProject = {
                                        viewModel.leaveActiveCollaboration {
                                            navController.popBackStack()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (
                !showOpeningSplash &&
                hasCompletedOnboarding == true &&
                !currentRouteName.contains("Copilot") &&
                !currentRouteName.contains("Messages") &&
                !currentRouteName.contains("CollaborationWorkspace") &&
                !showAuthModal
            ) {
                MovableAiPopup(
                    isExpanded = isAiPopupExpanded,
                    onExpandedChange = { expanded ->
                        StartupSoundPlayer.playTap(context)
                        analyticsTracker.logCopilotPopupToggled(expanded)
                        isAiPopupExpanded = expanded
                    },
                    messages = copilotMessages,
                    isLoading = isAiLoading,
                    aiStatus = aiStatusState,
                    canRegenerate = canRegenerateLastReply,
                    useFastModel = useFastAiModel,
                    activeContext = focusedInnovation ?: selectedDetail,
                    networkDiagnosticManager = viewModel.networkDiagnosticManager,
                    networkDiagnosticState = networkDiagnosticState,
                    onToggleFastModel = {
                        StartupSoundPlayer.playTap(context)
                        viewModel.setFastAiModel(it, surface = "floating_popup")
                    },
                    onSendPrompt = { prompt, promptSource ->
                        StartupSoundPlayer.playTap(context)
                        viewModel.sendCopilotPrompt(
                            prompt = prompt,
                            promptSource = promptSource,
                            isPopup = true
                        )
                    },
                    onStopGeneration = {
                        StartupSoundPlayer.playTap(context)
                        viewModel.stopCopilotGeneration()
                    },
                    onRegenerateLast = {
                        StartupSoundPlayer.playTap(context)
                        viewModel.regenerateLastCopilotReply(isPopup = true)
                    },
                    onRetryTimedOut = { messageId ->
                        StartupSoundPlayer.playTap(context)
                        viewModel.retryTimedOutCopilotMessage(messageId, isPopup = true)
                    },
                    onSwitchToFastAndRetry = { messageId ->
                        StartupSoundPlayer.playTap(context)
                        viewModel.switchToFastAndRetry(messageId, isPopup = true)
                    },
                    onSwitchToDefaultAiAndRetry = { messageId ->
                        StartupSoundPlayer.playTap(context)
                        viewModel.switchToDefaultAiAndRetry(messageId, isPopup = true)
                    },
                    onOpenDiagnostics = {
                        StartupSoundPlayer.playTap(context)
                        showDiagnosticsDialog = true
                        viewModel.runConnectivityDiagnostics(context)
                    },
                    onOpenFullCopilot = {
                        isAiPopupExpanded = false
                        navigateTopTab(bottomTabs[2])
                    }
                )
            }

            if (showNotificationsSheet && !showOpeningSplash && hasCompletedOnboarding == true) {
                NotificationCenterSheet(
                    alerts = notifications,
                    unreadCount = unreadNotificationsCount,
                    onDismiss = { showNotificationsSheet = false },
                    onAlertClick = { alert ->
                        hapticManager.tap()
                        StartupSoundPlayer.playTap(context)
                        viewModel.markNotificationRead(alert.id)
                        when {
                            alert.targetRoute == "messages" && !alert.targetId.isNullOrBlank() -> {
                                viewModel.selectConversation(alert.targetId)
                                showNotificationsSheet = false
                                navController.navigate(AppRoute.Messages)
                            }
                            alert.targetRoute == "collaborations" -> {
                                val pid = alert.targetId
                                if (!pid.isNullOrBlank() && collaborations.any { it.id == pid }) {
                                    viewModel.selectCollaborationProject(pid)
                                    showNotificationsSheet = false
                                    navController.navigate(AppRoute.CollaborationWorkspace)
                                } else {
                                    showNotificationsSheet = false
                                    navController.navigate(AppRoute.Collaborations)
                                }
                            }
                            else -> {
                                val targetId = alert.innovationId
                                if (!targetId.isNullOrBlank()) {
                                    val match = availableInnovations.firstOrNull { it.id == targetId }
                                    if (match != null) {
                                        showNotificationsSheet = false
                                        viewModel.openInnovationDetail(match, source = "notification")
                                        navController.navigate(AppRoute.Detail)
                                    }
                                }
                            }
                        }
                    },
                    onMarkAllRead = {
                        hapticManager.success()
                        StartupSoundPlayer.playTap(context)
                        viewModel.markAllNotificationsRead()
                    },
                    onDeleteAlert = { id ->
                        hapticManager.reject()
                        StartupSoundPlayer.playTap(context)
                        viewModel.deleteNotification(id)
                    },
                    onClearAll = {
                        hapticManager.reject()
                        StartupSoundPlayer.playTap(context)
                        viewModel.clearAllNotifications()
                    },
                    onSyncUpdates = {
                        hapticManager.tap()
                        StartupSoundPlayer.playTap(context)
                        viewModel.refreshMarketplace()
                        viewModel.loadDashboardStats(isPullRefresh = true)
                    }
                )
            }

            if (showDiagnosticsDialog && !showOpeningSplash && hasCompletedOnboarding == true) {
                FirebaseDiagnosticsDialog(
                    report = diagnosticsReport,
                    onRunDiagnostics = { viewModel.runConnectivityDiagnostics(context) },
                    onDismiss = { showDiagnosticsDialog = false }
                )
            }

            if (showAuthModal && hasCompletedOnboarding == true) {
                BackendAuthModal(
                    isBusy = isAuthBusy,
                    errorMessage = authError,
                    onLogin = { email, password -> viewModel.login(email, password) },
                    onRegister = { name, email, password -> viewModel.register(name, email, password) },
                    onGoogleSignInSuccess = { viewModel.onGoogleAuthSuccess() },
                    onGoogleSignInError = { err -> viewModel.setAuthError(err) },
                    onDismiss = { viewModel.dismissAuthModal() }
                )
            }

            AnimatedVisibility(
                visible = hasCompletedOnboarding == false,
                enter = fadeIn(tween(260)),
                exit = fadeOut(tween(340)),
                modifier = Modifier.fillMaxSize().zIndex(15f)
            ) {
                OnboardingScreen(
                    onFinishOnboarding = {
                        viewModel.completeOnboarding()
                    }
                )
            }

            AnimatedVisibility(
                visible = showOpeningSplash,
                enter = fadeIn(tween(240)),
                exit = fadeOut(tween(480)),
                modifier = Modifier.fillMaxSize().zIndex(20f)
            ) {
                OpeningSplashOverlay(
                    onFinished = { showOpeningSplash = false }
                )
            }
        }
            }
        }
    }
}
