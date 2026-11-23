package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.analytics.AnalyticsTracker
import com.example.analytics.NoOpAnalyticsTracker
import com.example.data.local.IdeaPortfolioEntity
import com.example.data.local.InvestorPortfolioEntity
import com.example.data.local.SavedProfileEntity
import com.example.data.model.AiFailureCategory
import com.example.data.model.AiRequestPhase
import com.example.data.model.AiStatusUiState
import com.example.data.model.AuthSyncStatus
import com.example.data.model.AuthUser
import com.example.data.model.UnifiedAuthSessionState
import com.example.data.model.BackendComment
import com.example.data.model.BackendInnovation
import com.example.data.model.BackendTalent
import com.example.data.model.BlockedUserRecord
import com.example.data.model.CategoryItem
import com.example.data.model.ChatMessage
import com.example.data.model.CloudNotification
import com.example.data.model.CollaborationActivity
import com.example.data.model.CollaborationFile
import com.example.data.model.CollaborationInvitation
import com.example.data.model.CollaborationProject
import com.example.data.model.CollaborationStatus
import com.example.data.model.CollaborationTask
import com.example.data.model.ConversationContextType
import com.example.data.model.ConversationUiItem
import com.example.data.model.CopilotMessage
import com.example.data.model.DashboardStats
import com.example.data.model.DigitalNdaRecord
import com.example.data.model.EcosystemMatchResult
import com.example.data.model.ExactCategories
import com.example.data.model.InvitationStatus
import com.example.data.model.MessageType
import com.example.data.model.NotificationAlert
import com.example.data.model.NotificationType
import com.example.data.model.PitchDeckResult
import com.example.data.model.Proposal
import com.example.data.model.ShareIdeaScope
import com.example.data.model.StartCollaborationDraft
import com.example.data.model.TalentProfile
import com.example.data.model.TaskComment
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.data.model.UiState
import com.example.data.model.UserConversationPref
import com.example.data.model.UserPresence
import com.example.data.model.UserProfile
import com.example.data.model.VerifiedOrganization
import com.example.data.preferences.NotificationLocalDataStore
import com.example.data.preferences.ThemePreferences
import com.example.data.preferences.TokenManager
import com.example.data.repository.AiCopilotRepository
import com.example.data.repository.BackendApiException
import com.example.data.repository.BackendRepository
import com.example.data.repository.ChatPeerDescriptor
import com.example.data.repository.CollaborationRepository
import com.example.data.repository.EcosystemMatcher
import com.example.data.repository.EcosystemRepository
import com.example.data.repository.IdeaRepository
import com.example.data.repository.MessagingRepository
import com.example.data.repository.PortfolioRepository
import com.example.data.repository.SanaPromptEngine
import com.example.data.repository.StartupWorkbookRepository
import com.example.data.repository.WorkbookAccessStatus
import com.example.data.repository.WorkbookSearchResult
import com.example.util.FirebaseDiagnosticsReport
import com.example.util.NetworkDiagnosticManager
import com.example.util.NetworkDiagnosticState
import com.example.util.SecurityCryptoUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID

class AppViewModel(
    private val repository: BackendRepository,
    private val tokenManager: TokenManager,
    private val themePreferences: ThemePreferences,
    private val aiCopilotRepository: AiCopilotRepository = AiCopilotRepository(),
    private val notificationDataStore: NotificationLocalDataStore? = null,
    private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker,
    private val ecosystemRepository: EcosystemRepository? = null,
    private val ideaRepository: IdeaRepository? = null,
    private val appContext: Context? = null,
    val networkDiagnosticManager: NetworkDiagnosticManager = NetworkDiagnosticManager.getInstance(appContext),
    private val portfolioRepository: PortfolioRepository? = null,
    private val messagingRepository: MessagingRepository? = null,
    private val collaborationRepository: CollaborationRepository? = null,
    private val startupWorkbookRepository: StartupWorkbookRepository? = appContext?.let { StartupWorkbookRepository(it) }
) : ViewModel() {

    val isDarkTheme: StateFlow<Boolean> = themePreferences.isDarkTheme

    val isSoundEnabled: StateFlow<Boolean> = themePreferences.isSoundEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

    val hasCompletedOnboarding: StateFlow<Boolean?> = themePreferences.hasCompletedOnboarding
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    private val _authSessionState = MutableStateFlow(UnifiedAuthSessionState())
    val authSessionState: StateFlow<UnifiedAuthSessionState> = _authSessionState.asStateFlow()

    private val _firebaseUid = MutableStateFlow<String?>(runCatching { Firebase.auth.currentUser?.uid }.getOrNull())
    val firebaseUid: StateFlow<String?> = _firebaseUid.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val cloudNotifications: StateFlow<List<CloudNotification>> = _firebaseUid
        .flatMapLatest { uid ->
            if (uid != null && messagingRepository != null) {
                messagingRepository.observeCloudNotifications(uid).map { it }
            } else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val notifications: StateFlow<List<NotificationAlert>> = combine(
        notificationDataStore?.alerts ?: flowOf(emptyList()),
        cloudNotifications
    ) { localAlerts, cloudAlerts ->
        mergeNotificationAlerts(localAlerts, cloudAlerts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val unreadNotificationsCount: StateFlow<Int> = notifications
        .map { list -> list.count { !it.isRead } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    private val _showAuthModal = MutableStateFlow(false)
    val showAuthModal: StateFlow<Boolean> = _showAuthModal.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isAuthBusy = MutableStateFlow(false)
    val isAuthBusy: StateFlow<Boolean> = _isAuthBusy.asStateFlow()

    val categories: StateFlow<List<CategoryItem>> = repository.observeCachedCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ExactCategories.defaultItems)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val recentSearches: StateFlow<List<String>> = repository.observeRecentSearches(limit = 10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _selectedStage = MutableStateFlow("All")
    val selectedStage: StateFlow<String> = _selectedStage.asStateFlow()

    private val _selectedSort = MutableStateFlow("Featured")
    val selectedSort: StateFlow<String> = _selectedSort.asStateFlow()

    private val _isSyncingInnovations = MutableStateFlow(true)
    val isSyncingInnovations: StateFlow<Boolean> = _isSyncingInnovations.asStateFlow()

    private val _isShowingOfflineCache = MutableStateFlow(false)
    val isShowingOfflineCache: StateFlow<Boolean> = _isShowingOfflineCache.asStateFlow()

    private val _innovationsSyncError = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val cachedFilteredInnovations = combine(
        _searchQuery,
        _selectedCategory,
        _selectedStage
    ) { query, category, stage ->
        Triple(query, category, stage)
    }.flatMapLatest { (query, category, stage) ->
        repository.observeCachedInnovations(
            search = query,
            category = category,
            stage = stage
        )
    }

    private val hasAnyCachedInnovations = repository.observeCachedInnovations()
        .map { it.isNotEmpty() }

    val innovationsState: StateFlow<UiState<List<BackendInnovation>>> = combine(
        cachedFilteredInnovations,
        hasAnyCachedInnovations,
        _isSyncingInnovations,
        _innovationsSyncError,
        _selectedSort
    ) { filtered, hasAnyCached, isSyncing, syncError, sortMode ->
        val sorted = when (sortMode) {
            "Top Voted" -> filtered.sortedByDescending { it.likeCount }
            "Funding Goal" -> filtered.sortedByDescending { it.fundingGoal }
            else -> filtered
        }
        when {
            sorted.isNotEmpty() -> UiState.Success(sorted)
            hasAnyCached -> UiState.Success(emptyList())
            isSyncing -> UiState.Loading
            syncError != null -> UiState.Error(syncError)
            else -> UiState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _selectedDetail = MutableStateFlow<BackendInnovation?>(null)
    val selectedDetail: StateFlow<BackendInnovation?> = _selectedDetail.asStateFlow()

    private val _detailComments = MutableStateFlow<List<BackendComment>>(emptyList())
    val detailComments: StateFlow<List<BackendComment>> = _detailComments.asStateFlow()

    private val _isDetailLoading = MutableStateFlow(false)
    val isDetailLoading: StateFlow<Boolean> = _isDetailLoading.asStateFlow()

    private val _pitchDeckState = MutableStateFlow<UiState<PitchDeckResult>?>(null)
    val pitchDeckState: StateFlow<UiState<PitchDeckResult>?> = _pitchDeckState.asStateFlow()

    private val _signedNdas = MutableStateFlow<Map<String, DigitalNdaRecord>>(emptyMap())
    val signedNdas: StateFlow<Map<String, DigitalNdaRecord>> = _signedNdas.asStateFlow()

    private val _backendTalentsState = MutableStateFlow<UiState<List<BackendTalent>>>(UiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val firestoreTalents: StateFlow<List<TalentProfile>> = _firebaseUid
        .flatMapLatest { uid ->
            if (uid != null && ideaRepository != null) ideaRepository.observeTalents()
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val talentsState: StateFlow<UiState<List<BackendTalent>>> = combine(
        _backendTalentsState,
        firestoreTalents
    ) { backendState, cloudTalents ->
        when (backendState) {
            is UiState.Success -> {
                val mappedCloud = cloudTalents.map { it.toBackendTalent() }
                val merged = linkedMapOf<String, BackendTalent>()
                mappedCloud.forEach { merged[it.id] = it }
                backendState.data.forEach { if (!merged.containsKey(it.id)) merged[it.id] = it }
                UiState.Success(merged.values.toList())
            }
            is UiState.Error -> {
                if (cloudTalents.isNotEmpty()) {
                    UiState.Success(cloudTalents.map { it.toBackendTalent() })
                } else {
                    backendState
                }
            }
            UiState.Loading -> {
                if (cloudTalents.isNotEmpty()) {
                    UiState.Success(cloudTalents.map { it.toBackendTalent() })
                } else {
                    UiState.Loading
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val myTalentProfile: StateFlow<TalentProfile?> = _firebaseUid
        .flatMapLatest { uid ->
            if (uid != null && ideaRepository != null) ideaRepository.observeMyTalentProfile(uid)
            else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val myFirestoreProfile: StateFlow<UserProfile?> = _firebaseUid
        .flatMapLatest { uid ->
            if (uid != null && ideaRepository != null) ideaRepository.observeUserProfile(uid)
            else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val incomingFirestoreProposals: StateFlow<List<Proposal>> = _firebaseUid
        .flatMapLatest { uid ->
            if (uid != null && ideaRepository != null) ideaRepository.observeIncomingProposals(uid)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val sentFirestoreProposals: StateFlow<List<Proposal>> = _firebaseUid
        .flatMapLatest { uid ->
            if (uid != null && ideaRepository != null) ideaRepository.observeSentProposals(uid)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val verifiedOrganizations: StateFlow<List<VerifiedOrganization>> = _firebaseUid
        .flatMapLatest { uid ->
            ecosystemRepository?.observeCombinedOrganizations(isSignedIn = uid != null)
                ?: flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val savedOpportunityIds: StateFlow<Set<String>> = _firebaseUid
        .flatMapLatest { uid ->
            val eco = ecosystemRepository ?: return@flatMapLatest flowOf(emptySet())
            if (uid != null) {
                combine(eco.observeLocalSavedOpportunityIds(), eco.observeCloudSavedOpportunityIds(uid)) { local, cloud ->
                    local + cloud
                }
            } else {
                eco.observeLocalSavedOpportunityIds()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    private val _selectedTalentRole = MutableStateFlow("All")
    val selectedTalentRole: StateFlow<String> = _selectedTalentRole.asStateFlow()

    private val _dashboardState = MutableStateFlow<UiState<DashboardStats>>(UiState.Success(DashboardStats()))
    val dashboardState: StateFlow<UiState<DashboardStats>> = _dashboardState.asStateFlow()

    private val _isRefreshingDashboard = MutableStateFlow(false)
    val isRefreshingDashboard: StateFlow<Boolean> = _isRefreshingDashboard.asStateFlow()

    private val _ideaPortfolioQuery = MutableStateFlow("")
    val ideaPortfolioQuery: StateFlow<String> = _ideaPortfolioQuery.asStateFlow()

    private val _investorPortfolioQuery = MutableStateFlow("")
    val investorPortfolioQuery: StateFlow<String> = _investorPortfolioQuery.asStateFlow()

    private val _investorStatusFilter = MutableStateFlow("All")
    val investorStatusFilter: StateFlow<String> = _investorStatusFilter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val ideaPortfolioItems: StateFlow<List<IdeaPortfolioEntity>> = _ideaPortfolioQuery
        .flatMapLatest { q ->
            portfolioRepository?.observeIdeaPortfolio(q) ?: flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val ideaPortfolioIds: StateFlow<Set<String>> = (portfolioRepository?.observeIdeaPortfolioIds() ?: flowOf(emptySet()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    val investorPortfolioItems: StateFlow<List<InvestorPortfolioEntity>> = combine(
        _investorPortfolioQuery,
        _investorStatusFilter
    ) { q, status -> q to status }
        .flatMapLatest { (q, status) ->
            portfolioRepository?.observeInvestorPortfolio(query = q, statusFilter = status) ?: flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val investorPortfolioIds: StateFlow<Set<String>> = (portfolioRepository?.observeInvestorPortfolioIds() ?: flowOf(emptySet()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    private val _savedProfilesQuery = MutableStateFlow("")
    val savedProfilesQuery: StateFlow<String> = _savedProfilesQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val savedProfiles: StateFlow<List<SavedProfileEntity>> = _savedProfilesQuery
        .flatMapLatest { q ->
            portfolioRepository?.observeSavedProfiles(q) ?: flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val savedProfileIds: StateFlow<Set<String>> = (portfolioRepository?.observeSavedProfileIds() ?: flowOf(emptySet()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val availableInnovations: StateFlow<List<BackendInnovation>> = repository.observeCachedInnovations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _focusedInnovation = MutableStateFlow<BackendInnovation?>(null)
    val focusedInnovation: StateFlow<BackendInnovation?> = _focusedInnovation.asStateFlow()

    private val _useFastAiModel = MutableStateFlow(false)
    val useFastAiModel: StateFlow<Boolean> = _useFastAiModel.asStateFlow()

    private val _copilotMessages = MutableStateFlow(listOf(createWelcomeMessage()))
    val copilotMessages: StateFlow<List<CopilotMessage>> = _copilotMessages.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _aiStatusState = MutableStateFlow(AiStatusUiState())
    val aiStatusState: StateFlow<AiStatusUiState> = _aiStatusState.asStateFlow()

    private val _canRegenerateLastReply = MutableStateFlow(false)
    val canRegenerateLastReply: StateFlow<Boolean> = _canRegenerateLastReply.asStateFlow()

    private val _customApiKey = MutableStateFlow(tokenManager.getCustomApiKey().orEmpty())
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    private val _isActionBusy = MutableStateFlow(false)
    val isActionBusy: StateFlow<Boolean> = _isActionBusy.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    val networkDiagnosticState: StateFlow<NetworkDiagnosticState> = networkDiagnosticManager.state
    val diagnosticsReport: StateFlow<FirebaseDiagnosticsReport> = networkDiagnosticManager.diagnosticsReport

    // --- Real-Time Messaging & Collaborations StateFlows ---

    @OptIn(ExperimentalCoroutinesApi::class)
    val peerPresences: StateFlow<Map<String, UserPresence>> = _firebaseUid
        .flatMapLatest { uid ->
            if (uid != null && messagingRepository != null) messagingRepository.observeAllPresence()
            else flowOf(emptyMap())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rawConversations = _firebaseUid
        .flatMapLatest { uid ->
            if (uid != null && messagingRepository != null) messagingRepository.observeConversations(uid)
            else flowOf(emptyList())
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val conversationPrefs = _firebaseUid
        .flatMapLatest { uid ->
            if (uid != null && messagingRepository != null) messagingRepository.observeConversationPrefs(uid)
            else flowOf(emptyMap())
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val blockedUsersList: StateFlow<List<BlockedUserRecord>> = _firebaseUid
        .flatMapLatest { uid ->
            if (uid != null && messagingRepository != null) messagingRepository.observeBlockedUsers(uid)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val blockedUserIds: StateFlow<Set<String>> = blockedUsersList
        .map { list -> list.map { it.blockedUid.ifBlank { it.id } }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val conversations: StateFlow<List<ConversationUiItem>> = combine(
        _firebaseUid,
        rawConversations,
        conversationPrefs,
        peerPresences,
        blockedUserIds
    ) { myUid, convs, prefs, presences, blockedSet ->
        if (myUid == null) emptyList()
        else {
            convs.map { conv ->
                val pref = prefs[conv.id] ?: UserConversationPref(userId = myUid, conversationId = conv.id)
                val peerUid = conv.peerUidFor(myUid)
                val presence = peerUid?.let { presences[it] }
                val isBlocked = peerUid != null && blockedSet.contains(peerUid)
                ConversationUiItem(
                    conversation = conv,
                    pref = pref,
                    peerPresence = presence,
                    isPeerBlocked = isBlocked
                )
            }.sortedWith(
                compareByDescending<ConversationUiItem> { it.pref.pinned }
                    .thenByDescending { it.conversation.lastMessageAt?.toDate()?.time ?: 0L }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val unreadMessagesCount: StateFlow<Int> = combine(_firebaseUid, conversations) { uid, items ->
        if (uid == null) 0
        else items.filter { !it.pref.archived && !it.pref.muted }
            .sumOf { it.conversation.unreadFor(uid) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    val activeConversation: StateFlow<ConversationUiItem?> = combine(
        conversations,
        _activeConversationId
    ) { list, activeId ->
        activeId?.let { id -> list.firstOrNull { it.conversation.id == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeMessages: StateFlow<List<ChatMessage>> = combine(
        _activeConversationId,
        _firebaseUid
    ) { convId, uid -> convId to uid }
        .flatMapLatest { (convId, uid) ->
            if (convId != null && uid != null && messagingRepository != null) {
                messagingRepository.observeMessages(convId, uid)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val collaborations: StateFlow<List<CollaborationProject>> = _firebaseUid
        .flatMapLatest { uid ->
            if (uid != null && collaborationRepository != null) collaborationRepository.observeMyProjects(uid)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val incomingCollabInvites: StateFlow<List<CollaborationInvitation>> = _firebaseUid
        .flatMapLatest { uid ->
            if (uid != null && collaborationRepository != null) collaborationRepository.observeIncomingInvitations(uid)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val sentCollabInvites: StateFlow<List<CollaborationInvitation>> = _firebaseUid
        .flatMapLatest { uid ->
            if (uid != null && collaborationRepository != null) collaborationRepository.observeSentInvitations(uid)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val pendingInvitesCount: StateFlow<Int> = incomingCollabInvites
        .map { list -> list.count { it.parsedStatus() == InvitationStatus.PENDING } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    private val _activeProjectId = MutableStateFlow<String?>(null)
    val activeProjectId: StateFlow<String?> = _activeProjectId.asStateFlow()

    val activeProject: StateFlow<CollaborationProject?> = combine(
        collaborations,
        _activeProjectId
    ) { projects, id ->
        id?.let { pid -> projects.firstOrNull { it.id == pid } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeProjectTasks: StateFlow<List<CollaborationTask>> = combine(_activeProjectId, _firebaseUid) { pid, uid -> pid to uid }
        .flatMapLatest { (pid, uid) ->
            if (pid != null && uid != null && collaborationRepository != null) {
                collaborationRepository.observeProjectTasks(pid, uid)
            } else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeProjectFiles: StateFlow<List<CollaborationFile>> = combine(_activeProjectId, _firebaseUid) { pid, uid -> pid to uid }
        .flatMapLatest { (pid, uid) ->
            if (pid != null && uid != null && collaborationRepository != null) {
                collaborationRepository.observeProjectFiles(pid, uid)
            } else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeProjectActivity: StateFlow<List<CollaborationActivity>> = combine(_activeProjectId, _firebaseUid) { pid, uid -> pid to uid }
        .flatMapLatest { (pid, uid) ->
            if (pid != null && uid != null && collaborationRepository != null) {
                collaborationRepository.observeProjectActivity(pid, uid)
            } else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _activeTaskId = MutableStateFlow<String?>(null)
    val activeTaskId: StateFlow<String?> = _activeTaskId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeTaskComments: StateFlow<List<TaskComment>> = combine(_activeProjectId, _activeTaskId, _firebaseUid) { pid, tid, uid ->
        Triple(pid, tid, uid)
    }.flatMapLatest { (pid, tid, uid) ->
        if (pid != null && tid != null && uid != null && collaborationRepository != null) {
            collaborationRepository.observeTaskComments(pid, tid, uid)
        } else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private var searchJob: Job? = null
    private var innovationsJob: Job? = null
    private var talentsJob: Job? = null
    private var aiStreamJob: Job? = null
    private var activeStreamMsgId: String? = null
    private var userRequestedAiStop = false
    private var lastPromptPayload: Triple<String, Bitmap?, String>? = null
    private val timedOutPayloads = mutableMapOf<String, TimedOutPromptPayload>()

    private data class TimedOutPromptPayload(
        val prompt: String,
        val attachedImage: Bitmap?,
        val promptSource: String,
        val userMessageId: String,
        val hasPartialOutput: Boolean
    )

    init {
        viewModelScope.launch {
            notificationDataStore?.ensureSeeded()
        }
        if (appContext != null) {
            networkDiagnosticManager.startMonitoring(appContext)
            viewModelScope.launch {
                runCatching { networkDiagnosticManager.checkConnectivity(appContext) }
            }
        }
        if (_firebaseUid.value != null) {
            viewModelScope.launch {
                val fb = Firebase.auth.currentUser
                messagingRepository?.updateMyPresence(
                    online = true,
                    displayName = fb?.displayName ?: _currentUser.value?.name ?: "Innovator",
                    role = _currentUser.value?.role ?: "Creator"
                )
            }
        }
        refreshAllInitialData()
    }

    fun refreshAllInitialData() {
        loadInnovations()
        loadTalents()
        loadCategories()
        viewModelScope.launch {
            val fbUser = runCatching { Firebase.auth.currentUser }.getOrNull()
            if (repository.hasSavedToken()) {
                runCatching { repository.fetchCurrentUser() }
                    .onSuccess { user ->
                        val resolved = user ?: fbUser?.toAuthUser()
                        _currentUser.value = resolved
                        if (resolved != null) {
                            loadDashboardStats()
                            if (fbUser == null && user != null) {
                                syncBackendUserToFirebase(user, preIssuedCustomToken = null)
                            } else {
                                _firebaseUid.value = fbUser?.uid
                                updateAuthSessionState(
                                    status = if (fbUser != null && repository.hasSavedToken()) {
                                        AuthSyncStatus.SYNCED
                                    } else {
                                        AuthSyncStatus.BACKEND_DEGRADED
                                    }
                                )
                            }
                        } else {
                            updateAuthSessionState(AuthSyncStatus.SIGNED_OUT)
                        }
                    }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        Log.w(TAG, "Deferred profile refresh skipped: ${e.message}")
                        if (_currentUser.value == null && fbUser != null) {
                            _firebaseUid.value = fbUser.uid
                            _currentUser.value = fbUser.toAuthUser()
                            loadDashboardStats()
                            syncFirebaseUserToBackend(fbUser, isAutoSignIn = true)
                        }
                    }
            } else if (fbUser != null) {
                _firebaseUid.value = fbUser.uid
                _currentUser.value = fbUser.toAuthUser()
                loadDashboardStats()
                syncFirebaseUserToBackend(fbUser, isAutoSignIn = true)
            } else {
                updateAuthSessionState(AuthSyncStatus.SIGNED_OUT)
            }
        }
    }

    fun toggleTheme() {
        val next = !isDarkTheme.value
        themePreferences.setDarkThemeImmediate(next)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            themePreferences.persistDarkTheme(next)
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setSoundEnabled(enabled)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            themePreferences.setOnboardingCompleted(true)
        }
    }

    fun replayOnboarding() {
        analyticsTracker.logOnboardingStarted(source = "replay")
        viewModelScope.launch {
            themePreferences.setOnboardingCompleted(false)
        }
    }

    fun markNotificationRead(alertId: String) {
        viewModelScope.launch {
            if (alertId.startsWith("cloud_")) {
                messagingRepository?.markCloudNotificationRead(alertId.removePrefix("cloud_"))
            } else {
                notificationDataStore?.markAsRead(alertId)
            }
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            notificationDataStore?.markAllRead()
            val unreadCloud = cloudNotifications.value.filter { !it.read }.map { it.id }
            messagingRepository?.markAllCloudNotificationsRead(unreadCloud)
        }
    }

    fun deleteNotification(alertId: String) {
        viewModelScope.launch {
            if (alertId.startsWith("cloud_")) {
                messagingRepository?.deleteCloudNotification(alertId.removePrefix("cloud_"))
            } else {
                notificationDataStore?.deleteAlert(alertId)
            }
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            notificationDataStore?.clearAll()
            cloudNotifications.value.forEach { n ->
                messagingRepository?.deleteCloudNotification(n.id)
            }
        }
    }

    fun setSelectedSort(sort: String) {
        _selectedSort.value = sort
        analyticsTracker.logMarketplaceSortSelected(sort)
    }

    fun clearMarketplaceFilters() {
        _searchQuery.value = ""
        _selectedCategory.value = "All"
        _selectedStage.value = "All"
        _selectedSort.value = "Featured"
        analyticsTracker.logMarketplaceFiltersCleared()
        loadInnovations()
    }

    fun openAuthModal() {
        _authError.value = null
        _showAuthModal.value = true
    }

    fun dismissAuthModal() {
        _authError.value = null
        _showAuthModal.value = false
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun runConnectivityDiagnostics(context: Context? = appContext) {
        if (networkDiagnosticManager.state.value.isChecking) return
        val resolvedContext = context ?: appContext
        viewModelScope.launch {
            runCatching {
                networkDiagnosticManager.checkConnectivity(resolvedContext)
            }.onFailure { e ->
                if (e is CancellationException) throw e
            }
        }
    }

    // --- Auth ---

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authError.value = "Please enter both email and password."
            return
        }
        viewModelScope.launch {
            _isAuthBusy.value = true
            _authError.value = null
            try {
                val exchange = repository.loginWithExchange(email, password)
                val user = exchange.user.copy(
                    authProviders = (exchange.user.authProviders + "password").distinct()
                )
                _currentUser.value = user
                _showAuthModal.value = false
                _snackbarMessage.value = "Welcome back, ${user.name}!"
                loadDashboardStats()
                loadInnovations()
                syncBackendUserToFirebase(user, preIssuedCustomToken = exchange.firebaseCustomToken)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _authError.value = e.localizedMessage ?: "Login failed. Verify your credentials."
            } finally {
                _isAuthBusy.value = false
            }
        }
    }

    fun register(name: String, email: String, password: String) {
        if (name.isBlank() || email.isBlank() || password.length < 6) {
            _authError.value = "Enter your name, valid email, and password (6+ chars)."
            return
        }
        viewModelScope.launch {
            _isAuthBusy.value = true
            _authError.value = null
            try {
                val exchange = repository.registerWithExchange(name, email, password)
                val user = exchange.user.copy(
                    authProviders = (exchange.user.authProviders + "password").distinct()
                )
                _currentUser.value = user
                _showAuthModal.value = false
                _snackbarMessage.value = "Account created! Welcome, ${user.name}."
                loadDashboardStats()
                loadInnovations()
                syncBackendUserToFirebase(user, preIssuedCustomToken = exchange.firebaseCustomToken)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _authError.value = e.localizedMessage ?: "Registration failed."
            } finally {
                _isAuthBusy.value = false
            }
        }
    }

    fun setAuthError(message: String) {
        _authError.value = message
    }

    fun onGoogleAuthSuccess(isAutoSignIn: Boolean = false) {
        val fbUser = runCatching { Firebase.auth.currentUser }.getOrNull() ?: return
        val authUser = fbUser.toAuthUser()
        _firebaseUid.value = fbUser.uid
        _currentUser.value = authUser
        _authError.value = null
        _showAuthModal.value = false
        if (!isAutoSignIn) {
            _snackbarMessage.value = "Signed in with Google as ${authUser.name}!"
        }
        viewModelScope.launch {
            bootstrapFirestoreForCurrentUser(fbUser, authUser)
            syncFirebaseUserToBackend(fbUser, isAutoSignIn = isAutoSignIn)
        }
        loadDashboardStats()
    }

    fun retryUnifiedAuthSync() {
        viewModelScope.launch {
            val fbUser = runCatching { Firebase.auth.currentUser }.getOrNull()
            when {
                fbUser != null && !repository.hasSavedToken() -> {
                    syncFirebaseUserToBackend(fbUser, isAutoSignIn = false)
                }
                repository.hasSavedToken() && fbUser == null -> {
                    val user = _currentUser.value ?: runCatching { repository.fetchCurrentUser() }.getOrNull()
                    if (user != null) {
                        _currentUser.value = user
                        syncBackendUserToFirebase(user, preIssuedCustomToken = null)
                    }
                }
                fbUser != null && repository.hasSavedToken() -> {
                    syncFirebaseUserToBackend(fbUser, isAutoSignIn = true)
                }
            }
        }
    }

    private suspend fun bootstrapFirestoreForCurrentUser(fbUser: FirebaseUser, authUser: AuthUser) {
        runCatching {
            ideaRepository?.ensureUserAndSeedCatalog(
                displayName = fbUser.displayName ?: authUser.name,
                email = fbUser.email ?: authUser.email,
                photoUrl = fbUser.photoUrl?.toString() ?: authUser.avatarUrl
            )
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Log.w(TAG, "Firestore user bootstrap skipped/deferred: ${e.message}")
        }
        runCatching {
            messagingRepository?.updateMyPresence(
                online = true,
                displayName = authUser.name,
                role = myFirestoreProfile.value?.role ?: authUser.role
            )
        }
    }

    private suspend fun syncFirebaseUserToBackend(fbUser: FirebaseUser, isAutoSignIn: Boolean) {
        updateAuthSessionState(
            status = AuthSyncStatus.SYNCING,
            notice = "Syncing marketplace account..."
        )
        try {
            val idToken = fbUser.getIdToken(false).await().token
            if (idToken.isNullOrBlank()) {
                updateAuthSessionState(
                    status = AuthSyncStatus.BACKEND_DEGRADED,
                    notice = "Cloud workspace active · Marketplace session token unavailable."
                )
                return
            }
            val exchange = repository.exchangeFirebaseIdToken(idToken)
            val mergedProviders = (exchange.user.authProviders + "google").distinct()
            val canonicalUser = exchange.user.copy(
                name = exchange.user.name.ifBlank { fbUser.displayName ?: "Innovator" },
                email = exchange.user.email.ifBlank { fbUser.email.orEmpty() },
                avatarUrl = exchange.user.avatarUrl ?: fbUser.photoUrl?.toString(),
                firebaseUid = exchange.user.firebaseUid ?: fbUser.uid,
                authProviders = mergedProviders,
                emailVerified = exchange.user.emailVerified || fbUser.isEmailVerified
            )
            _currentUser.value = canonicalUser
            updateAuthSessionState(status = AuthSyncStatus.SYNCED, notice = null)
            if (exchange.accountLinked && !isAutoSignIn) {
                _snackbarMessage.value = "Google account linked to your existing IDEA2IMPACT profile!"
            }
            loadDashboardStats()
            loadInnovations()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Firebase-to-Express token exchange deferred: ${SanaPromptEngine.redactSensitiveInfo(e.message.orEmpty())}")
            updateAuthSessionState(
                status = AuthSyncStatus.BACKEND_DEGRADED,
                notice = "Cloud workspace active · Marketplace sync pending"
            )
        }
    }

    private suspend fun syncBackendUserToFirebase(user: AuthUser, preIssuedCustomToken: String?) {
        val existingFb = runCatching { Firebase.auth.currentUser }.getOrNull()
        if (existingFb != null && (user.firebaseUid == null || existingFb.uid == user.firebaseUid)) {
            _firebaseUid.value = existingFb.uid
            updateAuthSessionState(status = AuthSyncStatus.SYNCED, notice = null)
            return
        }
        updateAuthSessionState(
            status = AuthSyncStatus.SYNCING,
            notice = "Connecting real-time cloud workspace..."
        )
        try {
            val customToken = preIssuedCustomToken?.takeIf { it.isNotBlank() }
                ?: repository.requestFirebaseCustomToken()
            if (customToken.isNullOrBlank()) {
                updateAuthSessionState(
                    status = AuthSyncStatus.FIREBASE_DEGRADED,
                    notice = "Marketplace session active · Cloud sync pending"
                )
                return
            }
            val authResult = Firebase.auth.signInWithCustomToken(customToken).await()
            val fbUser = authResult.user
            if (fbUser != null) {
                _firebaseUid.value = fbUser.uid
                _currentUser.value = user.copy(firebaseUid = fbUser.uid)
                bootstrapFirestoreForCurrentUser(fbUser, user)
                updateAuthSessionState(status = AuthSyncStatus.SYNCED, notice = null)
            } else {
                updateAuthSessionState(
                    status = AuthSyncStatus.FIREBASE_DEGRADED,
                    notice = "Marketplace session active · Cloud sync pending"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Express-to-Firebase custom token sync deferred: ${SanaPromptEngine.redactSensitiveInfo(e.message.orEmpty())}")
            updateAuthSessionState(
                status = AuthSyncStatus.FIREBASE_DEGRADED,
                notice = "Marketplace session active · Cloud sync pending"
            )
        }
    }

    private fun updateAuthSessionState(status: AuthSyncStatus, notice: String? = null) {
        val fbUser = runCatching { Firebase.auth.currentUser }.getOrNull()
        val hasJwt = repository.hasSavedToken()
        val providers = buildSet {
            _currentUser.value?.authProviders?.forEach { add(it.lowercase()) }
            fbUser?.providerData?.forEach { info ->
                when (info.providerId) {
                    "google.com" -> add("google")
                    "password" -> add("password")
                }
            }
            if (hasJwt && isEmpty()) add("password")
            if (fbUser != null && isEmpty()) add("google")
        }.toList()

        _authSessionState.value = UnifiedAuthSessionState(
            syncStatus = status,
            isFirebaseSignedIn = fbUser != null,
            isBackendJwtActive = hasJwt,
            linkedProviders = providers,
            statusNotice = notice
        )
    }

    fun logout() {
        viewModelScope.launch {
            runCatching {
                messagingRepository?.updateMyPresence(
                    online = false,
                    displayName = _currentUser.value?.name ?: "Innovator",
                    role = _currentUser.value?.role ?: "Creator"
                )
            }
        }
        repository.signOut()
        runCatching { Firebase.auth.signOut() }
        _firebaseUid.value = null
        _currentUser.value = null
        _authSessionState.value = UnifiedAuthSessionState(syncStatus = AuthSyncStatus.SIGNED_OUT)
        _activeConversationId.value = null
        _activeProjectId.value = null
        _activeTaskId.value = null
        _dashboardState.value = UiState.Success(DashboardStats())
        _snackbarMessage.value = "Signed out."
    }

    private fun isUserSignedIn(): Boolean {
        return repository.hasSavedToken() || _currentUser.value != null || Firebase.auth.currentUser != null
    }

    private fun FirebaseUser.toAuthUser(): AuthUser {
        val resolvedName = displayName?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: "Google Innovator"
        return AuthUser(
            id = uid,
            name = resolvedName,
            email = email.orEmpty(),
            role = "Creator",
            headline = "Google Account · IDEA2IMPACT",
            avatarUrl = photoUrl?.toString(),
            verified = isEmailVerified,
            firebaseUid = uid,
            authProviders = listOf("google"),
            emailVerified = isEmailVerified
        )
    }

    // --- Categories & Marketplace ---

    fun loadCategories() {
        viewModelScope.launch {
            try {
                repository.fetchCategories()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Using cached/default 8 categories while offline or warming up: ${e.message}")
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(320L)
            val clean = query.trim()
            if (clean.isNotEmpty()) {
                analyticsTracker.logMarketplaceSearch(
                    queryPreview = clean,
                    queryLength = clean.length
                )
            }
            loadInnovations()
            if (clean.length >= 2) {
                delay(900L)
                repository.recordMarketplaceSearch(clean)
            }
        }
    }

    fun submitSearchQuery(query: String = _searchQuery.value) {
        val clean = query.trim()
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (clean.isNotEmpty()) {
                analyticsTracker.logMarketplaceSearch(
                    queryPreview = clean,
                    queryLength = clean.length
                )
                repository.recordMarketplaceSearch(clean)
            }
            loadInnovations()
        }
    }

    fun selectRecentSearch(query: String) {
        submitSearchQuery(query)
    }

    fun deleteRecentSearch(query: String) {
        viewModelScope.launch {
            repository.deleteRecentSearch(query)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            repository.clearRecentSearches()
        }
    }

    fun setSelectedCategory(category: String, source: String = "filter_chip") {
        _selectedCategory.value = category
        analyticsTracker.logMarketplaceCategorySelected(category = category, source = source)
        loadInnovations()
    }

    fun setSelectedStage(stage: String) {
        _selectedStage.value = stage
        analyticsTracker.logMarketplaceStageSelected(stage)
        loadInnovations()
    }

    fun refreshMarketplace(source: String = "manual") {
        analyticsTracker.logMarketplaceRefreshed(source)
        loadInnovations()
        loadCategories()
    }

    fun loadInnovations() {
        innovationsJob?.cancel()
        innovationsJob = viewModelScope.launch {
            _isSyncingInnovations.value = true
            _innovationsSyncError.value = null
            try {
                repository.fetchInnovations(
                    search = _searchQuery.value,
                    category = _selectedCategory.value,
                    stage = _selectedStage.value
                )
                _isShowingOfflineCache.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Unable to sync innovations from network: ${e.message}")
                val hasCache = repository.hasCachedInnovations()
                _isShowingOfflineCache.value = hasCache
                if (!hasCache) {
                    _innovationsSyncError.value = friendlyErrorMessage(
                        e,
                        "Unable to connect to production marketplace."
                    )
                }
            } finally {
                _isSyncingInnovations.value = false
            }
        }
    }

    // --- Innovation Detail & Actions ---

    fun openInnovationDetail(item: BackendInnovation, source: String = "marketplace") {
        _selectedDetail.value = item
        _focusedInnovation.value = item
        _pitchDeckState.value = null
        val activeQuery = _searchQuery.value.trim()
        if (source == "marketplace" && activeQuery.isNotBlank()) {
            viewModelScope.launch {
                repository.recordMarketplaceSearch(activeQuery)
            }
        }
        analyticsTracker.logInnovationSelected(
            innovationId = item.id,
            title = item.title,
            category = item.category,
            stage = item.stage,
            source = source
        )
        refreshInnovationDetail(item.id)
    }

    fun setFocusedInnovation(item: BackendInnovation?) {
        _focusedInnovation.value = item
        analyticsTracker.logCopilotContextSelected(
            innovationId = item?.id,
            innovationCategory = item?.category
        )
    }

    fun setFastAiModel(useFast: Boolean, surface: String = "full_screen") {
        _useFastAiModel.value = useFast
        analyticsTracker.logCopilotModelToggled(useFastModel = useFast, surface = surface)
    }

    fun refreshInnovationDetail(id: String) {
        viewModelScope.launch {
            _isDetailLoading.value = true
            try {
                val (detail, comments) = repository.fetchInnovationDetail(id)
                if (detail != null) {
                    _selectedDetail.value = detail
                }
                _detailComments.value = comments
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed loading detail for $id", e)
            } finally {
                _isDetailLoading.value = false
            }
        }
    }

    fun toggleLike(innovation: BackendInnovation) {
        if (!isUserSignedIn()) {
            _snackbarMessage.value = "Sign in to upvote innovations."
            openAuthModal()
            return
        }
        val prevLiked = innovation.isLiked
        val prevCount = innovation.likeCount
        val optimistic = innovation.copy(
            isLiked = !prevLiked,
            likeCount = if (prevLiked) (prevCount - 1).coerceAtLeast(0) else prevCount + 1
        )
        applyInnovationUpdate(optimistic)

        viewModelScope.launch {
            if (!repository.hasSavedToken()) {
                analyticsTracker.logInnovationLiked(
                    innovationId = optimistic.id,
                    category = optimistic.category,
                    liked = optimistic.isLiked
                )
                if (!prevLiked && optimistic.isLiked) {
                    notificationDataStore?.recordLikeAction(optimistic)
                }
                return@launch
            }
            try {
                val (liked, count) = repository.toggleLike(innovation.id)
                val synced = optimistic.copy(
                    isLiked = liked ?: optimistic.isLiked,
                    likeCount = count ?: optimistic.likeCount
                )
                applyInnovationUpdate(synced)
                analyticsTracker.logInnovationLiked(
                    innovationId = synced.id,
                    category = synced.category,
                    liked = synced.isLiked
                )
                if (!prevLiked && synced.isLiked) {
                    notificationDataStore?.recordLikeAction(synced)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackendApiException) {
                applyInnovationUpdate(innovation)
                if (e.code == 401) openAuthModal()
                _snackbarMessage.value = e.message
            } catch (e: Exception) {
                applyInnovationUpdate(innovation)
                _snackbarMessage.value = e.localizedMessage ?: "Unable to update like."
            }
        }
    }

    fun toggleBookmark(innovation: BackendInnovation) {
        if (!isUserSignedIn()) {
            _snackbarMessage.value = "Sign in to bookmark innovations."
            openAuthModal()
            return
        }
        val optimistic = innovation.copy(isBookmarked = !innovation.isBookmarked)
        applyInnovationUpdate(optimistic)

        viewModelScope.launch {
            if (!repository.hasSavedToken()) {
                analyticsTracker.logInnovationBookmarked(
                    innovationId = optimistic.id,
                    category = optimistic.category,
                    bookmarked = optimistic.isBookmarked
                )
                _snackbarMessage.value = if (optimistic.isBookmarked) "Saved to bookmarks" else "Removed from bookmarks"
                loadDashboardStats()
                return@launch
            }
            try {
                val bookmarked = repository.toggleFavourite(innovation.id)
                val synced = optimistic.copy(isBookmarked = bookmarked ?: optimistic.isBookmarked)
                applyInnovationUpdate(synced)
                analyticsTracker.logInnovationBookmarked(
                    innovationId = synced.id,
                    category = synced.category,
                    bookmarked = synced.isBookmarked
                )
                _snackbarMessage.value = if (synced.isBookmarked) "Saved to bookmarks" else "Removed from bookmarks"
                loadDashboardStats()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackendApiException) {
                applyInnovationUpdate(innovation)
                if (e.code == 401) openAuthModal()
                _snackbarMessage.value = e.message
            } catch (e: Exception) {
                applyInnovationUpdate(innovation)
                _snackbarMessage.value = e.localizedMessage ?: "Unable to update bookmark."
            }
        }
    }

    private fun applyInnovationUpdate(updated: BackendInnovation) {
        if (_selectedDetail.value?.id == updated.id) {
            _selectedDetail.value = updated
        }
        viewModelScope.launch {
            repository.cacheLikeState(updated.id, updated.isLiked, updated.likeCount)
            repository.cacheBookmarkState(updated.id, updated.isBookmarked)
        }
    }

    fun postComment(innovationId: String, text: String, onDone: () -> Unit) {
        val clean = text.trim()
        if (clean.isBlank()) return
        if (!isUserSignedIn()) {
            _snackbarMessage.value = "Sign in to join the discussion."
            openAuthModal()
            return
        }
        viewModelScope.launch {
            _isActionBusy.value = true
            try {
                if (!repository.hasSavedToken()) {
                    val user = _currentUser.value
                    val localComment = BackendComment(
                        id = UUID.randomUUID().toString(),
                        innovationId = innovationId,
                        authorName = user?.name ?: "Google Innovator",
                        authorAvatar = user?.avatarUrl,
                        body = clean,
                        createdAt = SecurityCryptoUtils.formatTime()
                    )
                    _detailComments.update { listOf(localComment) + it }
                    _snackbarMessage.value = "Comment posted."
                    onDone()
                    return@launch
                }
                val created = repository.postComment(innovationId, clean)
                if (created != null && created.body.isNotBlank()) {
                    _detailComments.update { listOf(created) + it }
                } else {
                    refreshInnovationDetail(innovationId)
                }
                _snackbarMessage.value = "Comment posted."
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackendApiException) {
                if (e.code == 401) openAuthModal()
                _snackbarMessage.value = e.message
            } catch (e: Exception) {
                _snackbarMessage.value = e.localizedMessage ?: "Failed to post comment."
            } finally {
                _isActionBusy.value = false
            }
        }
    }

    fun submitProposal(
        innovationId: String,
        type: String,
        amount: String,
        message: String,
        contactInfo: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        if (!isUserSignedIn()) {
            val msg = "Sign in to submit a collaboration proposal."
            _snackbarMessage.value = msg
            onError(msg)
            openAuthModal()
            return
        }
        viewModelScope.launch {
            _isActionBusy.value = true
            try {
                val targetInnovation = _selectedDetail.value?.takeIf { it.id == innovationId }
                    ?: availableInnovations.value.firstOrNull { it.id == innovationId }
                val fbUid = Firebase.auth.currentUser?.uid
                if (repository.hasSavedToken()) {
                    repository.submitProposal(innovationId, type, amount, message, contactInfo)
                } else if (fbUid != null && ideaRepository != null && targetInnovation != null) {
                    val recipientId = targetInnovation.authorId.takeIf { it.isNotBlank() && it != fbUid }
                    if (recipientId != null) {
                        ideaRepository.submitCollaborationProposal(
                            recipientId = recipientId,
                            recipientName = targetInnovation.authorName,
                            innovationId = innovationId,
                            innovationTitle = targetInnovation.title,
                            proposalType = type,
                            amount = amount,
                            message = message,
                            contactInfo = contactInfo,
                            sharedIdeaScope = ShareIdeaScope.PROBLEM_SUMMARY,
                            sharedIdeaSummary = targetInnovation.description.take(280),
                            senderProfile = myFirestoreProfile.value
                        )
                    }
                }
                notificationDataStore?.recordProposalSubmitted(
                    innovation = targetInnovation,
                    innovationId = innovationId,
                    proposalType = type,
                    amount = amount
                )
                analyticsTracker.logProposalSubmitted(
                    innovationId = innovationId,
                    proposalType = type
                )
                _snackbarMessage.value = "Collaboration proposal submitted!"
                loadDashboardStats()
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackendApiException) {
                if (e.code == 401) openAuthModal()
                val errMsg = e.message ?: "Failed to submit proposal."
                _snackbarMessage.value = errMsg
                onError(errMsg)
            } catch (e: Exception) {
                val errMsg = e.localizedMessage ?: "Failed to submit proposal."
                _snackbarMessage.value = errMsg
                onError(errMsg)
            } finally {
                _isActionBusy.value = false
            }
        }
    }

    fun signDigitalNda(innovation: BackendInnovation, signerName: String, signerOrg: String) {
        val cleanName = signerName.trim()
        if (cleanName.isBlank()) return
        val hash = SecurityCryptoUtils.sha256Hex("${innovation.id}|$cleanName|${signerOrg.trim()}|${System.currentTimeMillis()}")
            .take(24)
            .uppercase()
        val record = DigitalNdaRecord(
            innovationId = innovation.id,
            signerName = cleanName,
            signerOrg = signerOrg.trim(),
            signatureHash = "NDA-$hash",
            signedAt = SecurityCryptoUtils.formatTime()
        )
        _signedNdas.update { it + (innovation.id to record) }
        analyticsTracker.logDigitalNdaSigned(
            innovationId = innovation.id,
            category = innovation.category
        )
        _snackbarMessage.value = "Digital Mutual NDA confirmed (${record.signatureHash})."
    }

    fun generatePitchDeck(innovation: BackendInnovation) {
        viewModelScope.launch {
            _pitchDeckState.value = UiState.Loading
            try {
                val deck = aiCopilotRepository.generateBackendPitchDeck(innovation)
                _pitchDeckState.value = UiState.Success(deck)
                analyticsTracker.logCopilotPitchDeckGenerated(
                    innovationId = innovation.id,
                    category = innovation.category,
                    success = true
                )
                notificationDataStore?.recordPlatformUpdate(
                    title = "AI Pitch Deck Ready",
                    message = "Synthesized a 5-slide investor deck for \"${innovation.title}\".",
                    innovationId = innovation.id,
                    dedupeKey = "pitch_deck_${innovation.id}"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate 5-slide AI pitch deck", e)
                analyticsTracker.logCopilotPitchDeckGenerated(
                    innovationId = innovation.id,
                    category = innovation.category,
                    success = false
                )
                _pitchDeckState.value = UiState.Error(
                    e.localizedMessage ?: "Failed to generate 5-slide AI pitch deck."
                )
            }
        }
    }

    fun clearPitchDeck() {
        _pitchDeckState.value = null
    }

    // --- Talents ---

    fun setSelectedTalentRole(role: String) {
        _selectedTalentRole.value = role
    }

    fun loadTalents() {
        talentsJob?.cancel()
        talentsJob = viewModelScope.launch {
            _backendTalentsState.value = UiState.Loading
            try {
                val talents = repository.fetchTalents()
                _backendTalentsState.value = UiState.Success(talents)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Unable to load talents: ${e.message}")
                _backendTalentsState.value = UiState.Error(
                    friendlyErrorMessage(e, "Failed to load builders & partners directory.")
                )
            }
        }
    }

    // --- Step 4, 5, 6, 7: Directory, Matching, Connections & Funding ---

    fun importVerifiedDirectoryJson(rawJson: String): Result<Int> {
        val eco = ecosystemRepository
            ?: return Result.failure(IllegalStateException("Directory repository unavailable."))
        val result = eco.importVerifiedJson(rawJson)
        result.onSuccess { count ->
            _snackbarMessage.value = "Loaded $count source-verified organization record(s)."
        }
        return result
    }

    fun clearImportedDirectory() {
        ecosystemRepository?.clearImportedOrganizations()
        _snackbarMessage.value = "Cleared locally imported directory records."
    }

    fun toggleSaveOpportunity(organization: VerifiedOrganization) {
        val eco = ecosystemRepository ?: return
        val currentlySaved = savedOpportunityIds.value.contains(organization.id)
        viewModelScope.launch {
            runCatching {
                eco.toggleSavedOpportunity(organization, currentlySaved)
            }.onSuccess {
                _snackbarMessage.value = if (currentlySaved) {
                    "Removed \"${organization.name}\" from saved opportunities."
                } else {
                    "Saved \"${organization.name}\" to your opportunities."
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _snackbarMessage.value = e.localizedMessage ?: "Unable to update saved opportunity."
            }
        }
    }

    fun computeEcosystemMatches(
        ideaTitle: String,
        ideaDescription: String,
        ideaCategory: String,
        ideaStage: String = "",
        preferredLocation: String = ""
    ): List<EcosystemMatchResult> {
        val talents = (talentsState.value as? UiState.Success)?.data.orEmpty()
        return EcosystemMatcher.matchIdeaToEcosystem(
            ideaTitle = ideaTitle,
            ideaDescription = ideaDescription,
            ideaCategory = ideaCategory,
            ideaStage = ideaStage,
            preferredLocation = preferredLocation,
            organizations = verifiedOrganizations.value,
            talents = talents
        )
    }

    fun toggleTalentVisibility(isPublic: Boolean) {
        if (Firebase.auth.currentUser == null || ideaRepository == null) {
            _snackbarMessage.value = "Sign in with Google to control your public Firestore profile visibility."
            openAuthModal()
            return
        }
        viewModelScope.launch {
            _isActionBusy.value = true
            try {
                ideaRepository.updateTalentVisibility(isPublic)
                _snackbarMessage.value = if (isPublic) {
                    "Your profile is now discoverable in Collaborators & Builders."
                } else {
                    "Your profile is now hidden from public talent discovery."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarMessage.value = e.localizedMessage ?: "Failed to update profile visibility."
            } finally {
                _isActionBusy.value = false
            }
        }
    }

    fun saveMyTalentProfile(
        name: String,
        role: String,
        headline: String,
        bio: String,
        skills: List<String>,
        location: String,
        isPublic: Boolean,
        onDone: () -> Unit = {}
    ) {
        if (Firebase.auth.currentUser == null || ideaRepository == null) {
            _snackbarMessage.value = "Sign in with Google to update your public collaborator profile."
            openAuthModal()
            return
        }
        viewModelScope.launch {
            _isActionBusy.value = true
            try {
                val count = (dashboardState.value as? UiState.Success)?.data?.innovationsPosted?.toLong() ?: 0L
                ideaRepository.updateUserProfile(
                    name = name,
                    role = role,
                    headline = headline,
                    bio = bio,
                    skills = skills,
                    location = location,
                    existingProjectsCount = count,
                    isPublic = isPublic
                )
                _snackbarMessage.value = "Collaborator profile saved."
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarMessage.value = e.localizedMessage ?: "Failed to save profile."
            } finally {
                _isActionBusy.value = false
            }
        }
    }

    fun sendCollaborationProposal(
        recipientTalent: BackendTalent,
        innovation: BackendInnovation?,
        proposalType: String,
        amount: String,
        message: String,
        contactInfo: String,
        shareScope: ShareIdeaScope,
        customProblemSummary: String = "",
        onSuccess: () -> Unit
    ) {
        if (!isUserSignedIn()) {
            _snackbarMessage.value = "Sign in to send a collaboration proposal."
            openAuthModal()
            return
        }
        val currentFbUid = Firebase.auth.currentUser?.uid
        val controlledSummary = if (innovation != null) {
            EcosystemMatcher.buildControlledShareSummary(
                ideaTitle = innovation.title,
                ideaCategory = innovation.category,
                ideaStage = innovation.stage,
                ideaDescription = innovation.description,
                scope = shareScope,
                customProblemSummary = customProblemSummary
            )
        } else null

        val sharedInnovationId = if (shareScope == ShareIdeaScope.NONE) null else innovation?.id
        val sharedTitle = when {
            shareScope == ShareIdeaScope.NONE || innovation == null -> "Direct $proposalType Intro"
            else -> innovation.title
        }

        viewModelScope.launch {
            _isActionBusy.value = true
            try {
                if (currentFbUid != null && ideaRepository != null && recipientTalent.id != currentFbUid) {
                    ideaRepository.submitCollaborationProposal(
                        recipientId = recipientTalent.id,
                        recipientName = recipientTalent.name,
                        innovationId = sharedInnovationId,
                        innovationTitle = sharedTitle,
                        proposalType = proposalType,
                        amount = amount,
                        message = message,
                        contactInfo = contactInfo,
                        sharedIdeaScope = shareScope,
                        sharedIdeaSummary = controlledSummary,
                        senderProfile = myFirestoreProfile.value
                    )
                    _snackbarMessage.value = "Proposal sent to ${recipientTalent.name}!"
                    onSuccess()
                } else if (repository.hasSavedToken() && sharedInnovationId != null) {
                    val composedMsg = buildString {
                        append("To ${recipientTalent.name}: $message")
                        if (!controlledSummary.isNullOrBlank()) {
                            append("\n\n$controlledSummary")
                        }
                    }
                    repository.submitProposal(sharedInnovationId, proposalType, amount, composedMsg, contactInfo)
                    _snackbarMessage.value = "Proposal sent to ${recipientTalent.name}!"
                    loadDashboardStats()
                    onSuccess()
                } else if (currentFbUid == null) {
                    _snackbarMessage.value = "Sign in with Google to send direct collaboration proposals."
                    openAuthModal()
                } else {
                    _snackbarMessage.value = "You cannot send a proposal to your own profile."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarMessage.value = e.localizedMessage
                    ?: "Could not deliver proposal. Target profile may not yet be synced on Firestore."
            } finally {
                _isActionBusy.value = false
            }
        }
    }

    fun respondToProposal(proposalId: String, accept: Boolean) {
        if (Firebase.auth.currentUser == null || ideaRepository == null) {
            _snackbarMessage.value = "Sign in with Google to respond to proposals."
            openAuthModal()
            return
        }
        viewModelScope.launch {
            _isActionBusy.value = true
            try {
                ideaRepository.updateProposalStatus(proposalId, if (accept) "ACCEPTED" else "DECLINED")
                _snackbarMessage.value = if (accept) "Proposal accepted." else "Proposal declined."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarMessage.value = e.localizedMessage ?: "Unable to update proposal status."
            } finally {
                _isActionBusy.value = false
            }
        }
    }

    // --- Dashboard ---

    fun loadDashboardStats(isPullRefresh: Boolean = false) {
        if (!repository.hasSavedToken()) {
            _isRefreshingDashboard.value = false
            if (isUserSignedIn()) {
                val saved = availableInnovations.value.filter { it.isBookmarked }
                val totalLikes = availableInnovations.value.count { it.isLiked }
                val cloudProposalCount = incomingFirestoreProposals.value.size
                val postedCount = myTalentProfile.value?.projectsCount?.toInt() ?: 0
                _dashboardState.value = UiState.Success(
                    DashboardStats(
                        innovationsPosted = postedCount,
                        savedOpportunities = saved.size + savedOpportunityIds.value.size,
                        proposalsReceived = cloudProposalCount,
                        totalUpvotes = totalLikes,
                        savedList = saved
                    )
                )
            } else {
                _dashboardState.value = UiState.Success(DashboardStats())
                if (isPullRefresh) {
                    _snackbarMessage.value = "Sign in to sync your personal dashboard."
                }
            }
            return
        }
        viewModelScope.launch {
            if (isPullRefresh) {
                _isRefreshingDashboard.value = true
            }
            if (!isPullRefresh || _dashboardState.value !is UiState.Success) {
                _dashboardState.value = UiState.Loading
            }
            try {
                val stats = repository.fetchDashboardStats()
                _dashboardState.value = UiState.Success(stats)
                notificationDataStore?.syncDashboardActivity(stats)
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackendApiException) {
                if (e.code == 401) {
                    repository.signOut()
                    _currentUser.value = null
                }
                if (!isPullRefresh || _dashboardState.value !is UiState.Success) {
                    _dashboardState.value = UiState.Error(e.message ?: "Failed to load dashboard metrics.")
                } else {
                    _snackbarMessage.value = e.message ?: "Unable to re-sync dashboard right now."
                }
            } catch (e: Exception) {
                val message = friendlyErrorMessage(e, "Unable to load dashboard stats.")
                if (!isPullRefresh || _dashboardState.value !is UiState.Success) {
                    _dashboardState.value = UiState.Error(message)
                } else {
                    _snackbarMessage.value = message
                }
            } finally {
                _isRefreshingDashboard.value = false
            }
        }
    }

    // --- AI Copilot ---

    fun saveCustomApiKey(key: String) {
        val clean = key.trim()
        tokenManager.saveCustomApiKey(clean)
        _customApiKey.value = clean
        analyticsTracker.logCopilotApiKeyUpdated(hasCustomKey = clean.isNotBlank())
        _snackbarMessage.value = if (clean.isBlank()) {
            "Custom Google AI Studio key cleared."
        } else {
            "Custom Google AI Studio key saved securely."
        }
    }

    fun sendCopilotPrompt(
        prompt: String,
        attachedImage: Bitmap? = null,
        promptSource: String = "custom_input",
        isPopup: Boolean = false
    ) {
        val trimmed = prompt.trim()
        if (trimmed.isBlank() || _isAiLoading.value) return

        lastPromptPayload = Triple(trimmed, attachedImage, promptSource)
        userRequestedAiStop = false

        val displayUserText = if (attachedImage != null) {
            "[Attached Prototype Image]\n$trimmed"
        } else {
            trimmed
        }

        val userMsg = CopilotMessage(
            id = UUID.randomUUID().toString(),
            isAssistant = false,
            content = displayUserText,
            timestamp = SecurityCryptoUtils.formatTime()
        )
        val priorHistory = _copilotMessages.value
        val streamMsgId = UUID.randomUUID().toString()
        activeStreamMsgId = streamMsgId
        val streamingPlaceholder = CopilotMessage(
            id = streamMsgId,
            isAssistant = true,
            content = "",
            timestamp = SecurityCryptoUtils.formatTime()
        )

        _copilotMessages.value = priorHistory + userMsg + streamingPlaceholder
        _isAiLoading.value = true
        _canRegenerateLastReply.value = false

        val targetInnovation = _focusedInnovation.value ?: _selectedDetail.value
        val catalogSnapshot = if (targetInnovation == null) availableInnovations.value.take(6) else emptyList()
        val talentSnapshot = (talentsState.value as? UiState.Success)?.data.orEmpty().take(8)
        val userKey = _customApiKey.value.takeIf { it.isNotBlank() }
        val modelTier = when {
            userKey != null && attachedImage == null -> "custom_key"
            _useFastAiModel.value -> "fast_flash_lite"
            else -> "deep_flash"
        }
        analyticsTracker.logCopilotPromptSent(
            promptSource = promptSource,
            modelTier = modelTier,
            hasAttachedImage = attachedImage != null,
            hasInnovationContext = targetInnovation != null,
            innovationCategory = targetInnovation?.category,
            promptLength = trimmed.length,
            surface = if (isPopup) "floating_popup" else "full_screen"
        )

        val directoryLines = verifiedOrganizations.value
            .take(15)
            .map { it.toDirectorySummaryLine() }

        val isCustomRoute = userKey != null && attachedImage == null
        val requestStartMs = System.currentTimeMillis()
        val activePhase = MutableStateFlow(AiRequestPhase.CHECKING_CONNECTION)

        fun updateAiStatus(phase: AiRequestPhase) {
            activePhase.value = phase
            val elapsedMs = System.currentTimeMillis() - requestStartMs
            _aiStatusState.value = AiStatusUiState(
                phase = phase,
                label = SanaPromptEngine.resolveStatusLabel(
                    phase = phase,
                    elapsedMs = elapsedMs,
                    hasAttachedImage = attachedImage != null,
                    isCustomKey = isCustomRoute
                ),
                hint = SanaPromptEngine.resolveStatusHint(
                    phase = phase,
                    elapsedMs = elapsedMs,
                    isFastMode = _useFastAiModel.value
                )
            )
        }

        updateAiStatus(AiRequestPhase.CHECKING_CONNECTION)

        var resolvedModelLabel = when {
            isCustomRoute -> "Custom AI Backend"
            _useFastAiModel.value -> "AI Sana Fast (gemini-3.5-flash-lite)"
            else -> "AI Sana Deep (gemini-3.8-flash)"
        }
        var resolvedModelNotice: String? = null

        aiStreamJob = viewModelScope.launch {
            var responseLength = 0
            val statusTickerJob = launch {
                while (isActive) {
                    delay(1000L)
                    updateAiStatus(activePhase.value)
                }
            }
            try {
                val lowerPrompt = trimmed.lowercase()
                val needsWorkbookSearch = SanaPromptEngine.isIdeaSubmissionOrEvaluation(trimmed) ||
                    lowerPrompt.contains("workbook") ||
                    lowerPrompt.contains("dataset")
                val workbookResult = if (needsWorkbookSearch) {
                    startupWorkbookRepository?.searchAsync(
                        prompt = trimmed,
                        activeInnovation = targetInnovation,
                        limit = 5
                    ) ?: WorkbookSearchResult(
                        status = WorkbookAccessStatus.UNAVAILABLE,
                        unavailabilityReason = "Reference workbook repository is not initialized in this session.",
                        querySummary = trimmed.take(160)
                    )
                } else {
                    null
                }

                if (isCustomRoute) {
                    val contextPayload = SanaPromptEngine.buildCustomKeyContextString(
                        prompt = trimmed,
                        history = priorHistory,
                        activeInnovation = targetInnovation,
                        catalogContext = catalogSnapshot,
                        talentContext = talentSnapshot,
                        directorySummaryLines = directoryLines,
                        isFastMode = _useFastAiModel.value,
                        workbookSearchResult = workbookResult
                    )
                    updateAiStatus(AiRequestPhase.THINKING)
                    val reply = repository.sendAiChat(
                        prompt = trimmed,
                        context = contextPayload,
                        customApiKey = userKey!!,
                        useFastModel = _useFastAiModel.value
                    )
                    updateAiStatus(AiRequestPhase.STREAMING)
                    responseLength = reply.length
                    _copilotMessages.update { msgs ->
                        msgs.map {
                            if (it.id == streamMsgId) {
                                it.copy(
                                    content = reply,
                                    respondingModelLabel = resolvedModelLabel
                                )
                            } else it
                        }
                    }
                } else {
                    aiCopilotRepository.streamBackendCopilotReply(
                        prompt = trimmed,
                        history = priorHistory,
                        activeInnovation = targetInnovation,
                        attachedImage = attachedImage,
                        useFastModel = _useFastAiModel.value,
                        catalogContext = catalogSnapshot,
                        talentContext = talentSnapshot,
                        directorySummaryLines = directoryLines,
                        workbookSearchResult = workbookResult,
                        onPhaseChange = { phase -> updateAiStatus(phase) },
                        onModelResolved = { route, modelName, switchedFromModel, fallbackReason ->
                            val isFastResolved = route == "fast_flash_lite"
                            resolvedModelLabel = if (isFastResolved) {
                                "AI Sana Fast ($modelName)"
                            } else {
                                "AI Sana Deep ($modelName)"
                            }
                            if (switchedFromModel != null) {
                                _useFastAiModel.value = true
                                val reasonLabel = when (fallbackReason) {
                                    AiFailureCategory.MODEL_UNAVAILABLE -> "Deep mode was unavailable"
                                    AiFailureCategory.QUOTA_OR_RATE_LIMIT -> "Deep mode hit a rate/quota limit"
                                    AiFailureCategory.TIMEOUT -> "Deep mode timed out before responding"
                                    else -> "Deep mode hit temporary provider overload"
                                }
                                resolvedModelNotice = "Switched from $switchedFromModel to AI Sana Fast ($modelName) ($reasonLabel)."
                                _snackbarMessage.value = "Switched to AI Sana Fast ($modelName)."
                            }
                            _copilotMessages.update { msgs ->
                                msgs.map {
                                    if (it.id == streamMsgId) {
                                        it.copy(
                                            respondingModelLabel = resolvedModelLabel,
                                            modelNotice = resolvedModelNotice
                                        )
                                    } else it
                                }
                            }
                        }
                    ).collect { partial ->
                        responseLength = partial.length
                        _copilotMessages.update { msgs ->
                            msgs.map {
                                if (it.id == streamMsgId) {
                                    it.copy(
                                        content = partial,
                                        respondingModelLabel = resolvedModelLabel,
                                        modelNotice = resolvedModelNotice
                                    )
                                } else it
                            }
                        }
                    }
                }
                analyticsTracker.logCopilotResponseCompleted(
                    modelTier = if (resolvedModelNotice != null) "fast_flash_lite_fallback" else modelTier,
                    success = true,
                    responseLength = responseLength
                )
            } catch (e: CancellationException) {
                if (userRequestedAiStop) {
                    _copilotMessages.update { msgs ->
                        msgs.map {
                            if (it.id == streamMsgId) {
                                val updated = if (it.content.isBlank()) {
                                    "*(Generation stopped)*"
                                } else {
                                    "${it.content.trimEnd()}\n\n*(Generation stopped)*"
                                }
                                it.copy(
                                    content = updated,
                                    respondingModelLabel = resolvedModelLabel,
                                    modelNotice = resolvedModelNotice
                                )
                            } else it
                        }
                    }
                }
                throw e
            } catch (e: Exception) {
                val category = SanaPromptEngine.classifyAiError(e)
                val httpCode = (e as? BackendApiException)?.code
                val diag = e as? com.example.data.repository.AiCopilotDiagnosticException
                val safeCause = SanaPromptEngine.redactSensitiveInfo(e.message.orEmpty()).take(160)
                Log.e(
                    TAG,
                    "AI Sana failure [tier=$modelTier, category=$category, code=$httpCode, phase=${diag?.phase ?: "request"}, appCheck=${diag?.appCheckStatus ?: "n/a"}, fallbackAttempted=${diag?.fallbackAttempted ?: false}, class=${e.javaClass.simpleName}, detail=$safeCause]"
                )
                analyticsTracker.logCopilotResponseCompleted(
                    modelTier = modelTier,
                    success = false,
                    responseLength = responseLength
                )
                if (category in setOf(
                        AiFailureCategory.NETWORK_OFFLINE,
                        AiFailureCategory.NETWORK_BLOCKED,
                        AiFailureCategory.APP_CHECK_OR_AUTH,
                        AiFailureCategory.TIMEOUT
                    )
                ) {
                    viewModelScope.launch {
                        runCatching { networkDiagnosticManager.checkConnectivity(appContext) }
                    }
                }
                val isTimeout = category == AiFailureCategory.TIMEOUT
                val diagnosed = SanaPromptEngine.diagnoseAiError(e, modelTier)
                val partialPrefix = diag
                    ?.partialContent
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                val hasPartialOutput = partialPrefix != null
                timedOutPayloads[streamMsgId] = TimedOutPromptPayload(
                    prompt = trimmed,
                    attachedImage = attachedImage,
                    promptSource = promptSource,
                    userMessageId = userMsg.id,
                    hasPartialOutput = hasPartialOutput
                )
                val offerFast = !isCustomRoute &&
                    !_useFastAiModel.value &&
                    !hasPartialOutput &&
                    diag?.fallbackAttempted != true &&
                    category in setOf(
                        AiFailureCategory.PROVIDER_OVERLOAD,
                        AiFailureCategory.TIMEOUT,
                        AiFailureCategory.QUOTA_OR_RATE_LIMIT,
                        AiFailureCategory.MODEL_UNAVAILABLE
                    )
                val offerDefault = isCustomRoute
                val actionHint = when {
                    isTimeout && hasPartialOutput -> "Tap **Resume** below to re-trigger your request without re-typing."
                    isTimeout -> "Tap **Retry** below to re-trigger your request without re-typing."
                    offerDefault -> "Tap **Use Default AI** or **Retry** below, or verify your key in Key Settings."
                    offerFast -> "Tap **Switch to Fast Mode** or **Retry** below."
                    else -> "Tap **Retry** below or adjust model/key settings."
                }
                val errorText = if (partialPrefix != null) {
                    "$partialPrefix\n\n---\n### AI Sana Error\n- $diagnosed\n- $actionHint"
                } else {
                    "### AI Sana Error\n- $diagnosed\n- $actionHint"
                }
                _copilotMessages.update { msgs ->
                    msgs.map {
                        if (it.id == streamMsgId) {
                            it.copy(
                                content = errorText,
                                isError = true,
                                isTimeout = isTimeout,
                                hasPartialTimeoutOutput = hasPartialOutput,
                                respondingModelLabel = resolvedModelLabel,
                                modelNotice = resolvedModelNotice,
                                offerFastFallback = offerFast,
                                offerDefaultAiFallback = offerDefault
                            )
                        } else {
                            it
                        }
                    }
                }
            } finally {
                statusTickerJob.cancel()
                _isAiLoading.value = false
                _aiStatusState.value = AiStatusUiState()
                _canRegenerateLastReply.value = lastPromptPayload != null
                if (activeStreamMsgId == streamMsgId) {
                    activeStreamMsgId = null
                }
            }
        }
    }

    fun stopCopilotGeneration() {
        if (!_isAiLoading.value) return
        userRequestedAiStop = true
        aiStreamJob?.cancel()
        _isAiLoading.value = false
        _aiStatusState.value = AiStatusUiState()
        _canRegenerateLastReply.value = lastPromptPayload != null
    }

    fun switchToFastAndRetry(messageId: String, isPopup: Boolean = false) {
        if (_isAiLoading.value) return
        setFastAiModel(true, surface = if (isPopup) "floating_popup_fallback" else "full_screen_fallback")
        retryTimedOutCopilotMessage(messageId, isPopup = isPopup)
    }

    fun switchToDefaultAiAndRetry(messageId: String, isPopup: Boolean = false) {
        if (_isAiLoading.value) return
        saveCustomApiKey("")
        retryTimedOutCopilotMessage(messageId, isPopup = isPopup)
    }

    fun retryTimedOutCopilotMessage(messageId: String, isPopup: Boolean = false) {
        if (_isAiLoading.value) return
        val payload = timedOutPayloads.remove(messageId)
        val prompt: String
        val bitmap: Bitmap?
        val userMessageId: String?
        val retrySource: String
        if (payload != null) {
            prompt = payload.prompt
            bitmap = payload.attachedImage
            userMessageId = payload.userMessageId
            retrySource = if (payload.hasPartialOutput) "timeout_resume" else "timeout_retry"
        } else {
            val fallback = lastPromptPayload ?: return
            prompt = fallback.first
            bitmap = fallback.second
            userMessageId = null
            retrySource = "timeout_retry"
        }

        _copilotMessages.update { current ->
            val updated = current.toMutableList()
            val errIdx = updated.indexOfFirst { it.id == messageId }
            if (errIdx >= 0) {
                val isLastTurn = errIdx == updated.lastIndex
                updated.removeAt(errIdx)
                if (isLastTurn) {
                    val prevUserIdx = if (userMessageId != null) {
                        updated.indexOfLast { it.id == userMessageId && !it.isAssistant }
                    } else {
                        if (updated.lastOrNull()?.isAssistant == false) updated.lastIndex else -1
                    }
                    if (prevUserIdx >= 0 && prevUserIdx == updated.lastIndex) {
                        updated.removeAt(prevUserIdx)
                    }
                }
            }
            updated
        }

        sendCopilotPrompt(
            prompt = prompt,
            attachedImage = bitmap,
            promptSource = retrySource,
            isPopup = isPopup
        )
    }

    fun regenerateLastCopilotReply(isPopup: Boolean = false) {
        val (prompt, bitmap, _) = lastPromptPayload ?: return
        if (_isAiLoading.value) return
        _copilotMessages.update { current ->
            val trimmedList = current.toMutableList()
            val lastErrId = trimmedList.lastOrNull()?.id
            if (trimmedList.lastOrNull()?.isAssistant == true && lastErrId != "welcome") {
                if (lastErrId != null) timedOutPayloads.remove(lastErrId)
                trimmedList.removeAt(trimmedList.lastIndex)
            }
            if (trimmedList.lastOrNull()?.isAssistant == false) {
                trimmedList.removeAt(trimmedList.lastIndex)
            }
            trimmedList
        }
        sendCopilotPrompt(
            prompt = prompt,
            attachedImage = bitmap,
            promptSource = "regenerate",
            isPopup = isPopup
        )
    }

    fun resetCopilotChat() {
        userRequestedAiStop = false
        aiStreamJob?.cancel()
        _isAiLoading.value = false
        _aiStatusState.value = AiStatusUiState()
        lastPromptPayload = null
        timedOutPayloads.clear()
        _canRegenerateLastReply.value = false
        analyticsTracker.logCopilotChatReset()
        _copilotMessages.value = listOf(createWelcomeMessage())
    }

    fun setIdeaPortfolioQuery(query: String) {
        _ideaPortfolioQuery.value = query
    }

    fun setInvestorPortfolioQuery(query: String) {
        _investorPortfolioQuery.value = query
    }

    fun setInvestorStatusFilter(status: String) {
        _investorStatusFilter.value = status
    }

    fun toggleSaveToIdeaPortfolio(innovation: BackendInnovation) {
        val repo = portfolioRepository ?: return
        viewModelScope.launch {
            val saved = repo.toggleInnovationInIdeaPortfolio(innovation)
            _snackbarMessage.value = if (saved) {
                "Saved \"${innovation.title}\" to Idea Portfolio."
            } else {
                "Removed \"${innovation.title}\" from Idea Portfolio."
            }
        }
    }

    fun saveCustomIdeaToPortfolio(
        title: String,
        summary: String,
        category: String,
        stage: String,
        priorityTag: String,
        notes: String
    ) {
        val repo = portfolioRepository ?: return
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) {
            _snackbarMessage.value = "Enter a project idea title to save."
            return
        }
        viewModelScope.launch {
            repo.saveCustomIdeaToPortfolio(
                title = cleanTitle,
                summary = summary.trim().ifBlank { cleanTitle },
                category = category.trim().ifBlank { "General" },
                stage = stage.trim().ifBlank { "Concept" },
                creatorName = _currentUser.value?.name ?: "Founder",
                priorityTag = priorityTag,
                notes = notes
            )
            _snackbarMessage.value = "Saved \"$cleanTitle\" to Idea Portfolio."
        }
    }

    fun updateIdeaPortfolioNotes(id: String, priorityTag: String, notes: String) {
        val repo = portfolioRepository ?: return
        viewModelScope.launch {
            repo.updateIdeaPortfolioNotes(id = id, priorityTag = priorityTag, notes = notes)
            _snackbarMessage.value = "Idea Portfolio notes updated."
        }
    }

    fun removeFromIdeaPortfolio(id: String) {
        val repo = portfolioRepository ?: return
        viewModelScope.launch {
            repo.removeFromIdeaPortfolio(id)
            _snackbarMessage.value = "Removed from Idea Portfolio."
        }
    }

    fun toggleSaveToInvestorPortfolio(innovation: BackendInnovation) {
        val repo = portfolioRepository ?: return
        viewModelScope.launch {
            val ndaSigned = _signedNdas.value.containsKey(innovation.id)
            val saved = repo.toggleInnovationInInvestorPortfolio(innovation, ndaSigned = ndaSigned)
            _snackbarMessage.value = if (saved) {
                "Saved \"${innovation.title}\" to Investor Portfolio."
            } else {
                "Removed \"${innovation.title}\" from Investor Portfolio."
            }
        }
    }

    fun saveCustomProjectToInvestorPortfolio(
        title: String,
        summary: String,
        category: String,
        stage: String,
        founderName: String,
        fundingGoal: Double,
        valuation: Double,
        diligenceStatus: String,
        targetTicketSize: String,
        notes: String
    ) {
        val repo = portfolioRepository ?: return
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) {
            _snackbarMessage.value = "Enter a project title to add to Investor Portfolio."
            return
        }
        viewModelScope.launch {
            repo.saveCustomProjectToInvestorPortfolio(
                title = cleanTitle,
                summary = summary.trim().ifBlank { cleanTitle },
                category = category.trim().ifBlank { "General" },
                stage = stage.trim().ifBlank { "Seed" },
                founderName = founderName.trim().ifBlank { "Founder" },
                fundingGoal = fundingGoal,
                valuation = valuation,
                diligenceStatus = diligenceStatus,
                targetTicketSize = targetTicketSize,
                notes = notes
            )
            _snackbarMessage.value = "Saved \"$cleanTitle\" to Investor Portfolio."
        }
    }

    fun updateInvestorDiligence(
        id: String,
        diligenceStatus: String,
        targetTicketSize: String,
        notes: String
    ) {
        val repo = portfolioRepository ?: return
        viewModelScope.launch {
            repo.updateInvestorDiligence(
                id = id,
                diligenceStatus = diligenceStatus,
                targetTicketSize = targetTicketSize,
                notes = notes
            )
            _snackbarMessage.value = "Investor Portfolio diligence updated."
        }
    }

    fun removeFromInvestorPortfolio(id: String) {
        val repo = portfolioRepository ?: return
        viewModelScope.launch {
            repo.removeFromInvestorPortfolio(id)
            _snackbarMessage.value = "Removed from Investor Portfolio."
        }
    }

    fun setSavedProfilesQuery(query: String) {
        _savedProfilesQuery.value = query
    }

    fun toggleSaveCollaboratorProfile(talent: BackendTalent) {
        val repo = portfolioRepository ?: return
        viewModelScope.launch {
            val saved = repo.toggleCollaboratorProfile(talent)
            _snackbarMessage.value = if (saved) {
                "Saved \"${talent.name}\" to Saved Profiles."
            } else {
                "Removed \"${talent.name}\" from Saved Profiles."
            }
        }
    }

    fun removeSavedCollaboratorProfile(id: String) {
        val repo = portfolioRepository ?: return
        viewModelScope.launch {
            repo.removeSavedProfile(id)
            _snackbarMessage.value = "Removed from Saved Profiles."
        }
    }

    // --- Messaging & Collaboration Actions ---

    fun selectConversation(conversationId: String?) {
        _activeConversationId.value = conversationId
        if (conversationId != null) {
            val item = conversations.value.firstOrNull { it.conversation.id == conversationId }
            if (item != null) {
                viewModelScope.launch {
                    messagingRepository?.markConversationRead(item.conversation, activeMessages.value)
                }
            }
        }
    }

    fun markActiveConversationRead() {
        val conv = activeConversation.value?.conversation ?: return
        val msgs = activeMessages.value
        viewModelScope.launch {
            messagingRepository?.markConversationRead(conv, msgs)
        }
    }

    fun openDirectChatWithTalent(
        talent: BackendTalent,
        innovation: BackendInnovation? = null,
        onOpened: (String) -> Unit = {}
    ) {
        val myUid = _firebaseUid.value
        val repo = messagingRepository
        if (myUid == null || repo == null) {
            _snackbarMessage.value = "Sign in with Google to message ${talent.name}."
            openAuthModal()
            return
        }
        val myProfile = myFirestoreProfile.value
        val myName = myProfile?.name ?: _currentUser.value?.name ?: "Innovator"
        val myRole = myProfile?.role ?: _currentUser.value?.role ?: "Creator"
        val myAvatar = myProfile?.avatarUrl ?: _currentUser.value?.avatarUrl
        viewModelScope.launch {
            _isActionBusy.value = true
            try {
                val convId = repo.openOrCreateDirectConversation(
                    myUid = myUid,
                    myName = myName,
                    myRole = myRole,
                    myAvatar = myAvatar,
                    peer = ChatPeerDescriptor(
                        rawId = talent.id,
                        name = talent.name,
                        role = talent.role,
                        avatarUrl = talent.avatarUrl,
                        isRealFirebaseUid = !talent.id.startsWith("t_") && !talent.id.startsWith("seed_")
                    ),
                    contextType = if (innovation != null) ConversationContextType.INNOVATION else ConversationContextType.GENERAL,
                    innovationId = innovation?.id,
                    innovationTitle = innovation?.title
                )
                _activeConversationId.value = convId
                onOpened(convId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarMessage.value = e.localizedMessage ?: "Unable to open conversation."
            } finally {
                _isActionBusy.value = false
            }
        }
    }

    fun openDirectChatWithInnovationCreator(
        innovation: BackendInnovation,
        onOpened: (String) -> Unit = {}
    ) {
        val talentPeer = BackendTalent(
            id = innovation.authorId.ifBlank { "creator_${innovation.id}" },
            name = innovation.authorName.ifBlank { "Creator" },
            role = innovation.authorTitle.ifBlank { "Creator" },
            roleCategory = "Creators",
            headline = "Creator of ${innovation.title}",
            skills = listOf(innovation.category, innovation.stage),
            location = "IDEA2IMPACT",
            avatarUrl = innovation.authorAvatar,
            verified = innovation.isVerifiedCreator
        )
        openDirectChatWithTalent(talentPeer, innovation = innovation, onOpened = onOpened)
    }

    fun createGroupChat(
        title: String,
        peers: List<BackendTalent>,
        innovation: BackendInnovation? = null,
        onCreated: (String) -> Unit = {}
    ) {
        val myUid = _firebaseUid.value
        val repo = messagingRepository
        if (myUid == null || repo == null) {
            _snackbarMessage.value = "Sign in with Google to create a group conversation."
            openAuthModal()
            return
        }
        val myProfile = myFirestoreProfile.value
        val myName = myProfile?.name ?: _currentUser.value?.name ?: "Innovator"
        val myRole = myProfile?.role ?: _currentUser.value?.role ?: "Creator"
        val myAvatar = myProfile?.avatarUrl ?: _currentUser.value?.avatarUrl
        viewModelScope.launch {
            _isActionBusy.value = true
            try {
                val convId = repo.createGroupConversation(
                    myUid = myUid,
                    myName = myName,
                    myRole = myRole,
                    myAvatar = myAvatar,
                    title = title,
                    peers = peers.map {
                        ChatPeerDescriptor(
                            rawId = it.id,
                            name = it.name,
                            role = it.role,
                            avatarUrl = it.avatarUrl,
                            isRealFirebaseUid = !it.id.startsWith("t_") && !it.id.startsWith("seed_")
                        )
                    },
                    contextType = if (innovation != null) ConversationContextType.INNOVATION else ConversationContextType.GENERAL,
                    innovationId = innovation?.id,
                    innovationTitle = innovation?.title
                )
                _activeConversationId.value = convId
                _snackbarMessage.value = "Created group \"$title\"."
                onCreated(convId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarMessage.value = e.localizedMessage ?: "Failed to create group chat."
            } finally {
                _isActionBusy.value = false
            }
        }
    }

    fun sendChatMessage(
        body: String,
        messageType: MessageType = MessageType.TEXT,
        replyTo: ChatMessage? = null,
        attachmentName: String? = null,
        attachmentMimeType: String? = null,
        attachmentSizeBytes: Long? = null,
        attachmentPreviewBase64: String? = null,
        attachmentUrl: String? = null,
        sharedInnovation: BackendInnovation? = null,
        onSent: () -> Unit = {}
    ) {
        val conv = activeConversation.value?.conversation ?: return
        val repo = messagingRepository ?: return
        if (_firebaseUid.value == null) {
            openAuthModal()
            return
        }
        val myProfile = myFirestoreProfile.value
        val myName = myProfile?.name ?: _currentUser.value?.name ?: "Innovator"
        val myRole = myProfile?.role ?: _currentUser.value?.role ?: "Creator"
        val myAvatar = myProfile?.avatarUrl ?: _currentUser.value?.avatarUrl
        viewModelScope.launch {
            try {
                repo.sendMessage(
                    conversation = conv,
                    senderName = myName,
                    senderRole = myRole,
                    senderAvatar = myAvatar,
                    body = body,
                    messageType = messageType,
                    replyTo = replyTo,
                    attachmentName = attachmentName,
                    attachmentMimeType = attachmentMimeType,
                    attachmentSizeBytes = attachmentSizeBytes,
                    attachmentPreviewBase64 = attachmentPreviewBase64,
                    attachmentUrl = attachmentUrl,
                    sharedInnovationId = sharedInnovation?.id,
                    sharedInnovationTitle = sharedInnovation?.title
                )
                onSent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarMessage.value = e.localizedMessage ?: "Failed to send message."
            }
        }
    }

    fun editChatMessage(messageId: String, newBody: String) {
        val convId = _activeConversationId.value ?: return
        val repo = messagingRepository ?: return
        viewModelScope.launch {
            runCatching { repo.editMessage(convId, messageId, newBody) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _snackbarMessage.value = e.localizedMessage ?: "Unable to edit message."
                }
        }
    }

    fun deleteChatMessage(messageId: String) {
        val convId = _activeConversationId.value ?: return
        val repo = messagingRepository ?: return
        viewModelScope.launch {
            runCatching { repo.deleteMessage(convId, messageId) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _snackbarMessage.value = e.localizedMessage ?: "Unable to delete message."
                }
        }
    }

    fun toggleChatReaction(message: ChatMessage, emoji: String) {
        val convId = _activeConversationId.value ?: return
        val repo = messagingRepository ?: return
        viewModelScope.launch {
            runCatching { repo.toggleMessageReaction(convId, message, emoji) }
        }
    }

    fun setTypingIndicator(isTyping: Boolean) {
        val convId = _activeConversationId.value ?: return
        val repo = messagingRepository ?: return
        viewModelScope.launch {
            repo.setTypingStatus(convId, isTyping)
        }
    }

    fun togglePinConversation(item: ConversationUiItem) {
        val repo = messagingRepository ?: return
        viewModelScope.launch {
            runCatching {
                repo.updateConversationPref(
                    conversationId = item.conversation.id,
                    pinned = !item.pref.pinned,
                    muted = item.pref.muted,
                    archived = item.pref.archived
                )
            }
        }
    }

    fun toggleMuteConversation(item: ConversationUiItem) {
        val repo = messagingRepository ?: return
        viewModelScope.launch {
            val next = !item.pref.muted
            runCatching {
                repo.updateConversationPref(
                    conversationId = item.conversation.id,
                    pinned = item.pref.pinned,
                    muted = next,
                    archived = item.pref.archived
                )
            }.onSuccess {
                _snackbarMessage.value = if (next) "Notifications muted for this chat." else "Conversation unmuted."
            }
        }
    }

    fun toggleArchiveConversation(item: ConversationUiItem) {
        val repo = messagingRepository ?: return
        viewModelScope.launch {
            val next = !item.pref.archived
            runCatching {
                repo.updateConversationPref(
                    conversationId = item.conversation.id,
                    pinned = item.pref.pinned,
                    muted = item.pref.muted,
                    archived = next
                )
            }.onSuccess {
                _snackbarMessage.value = if (next) "Conversation archived." else "Restored from Archive."
            }
        }
    }

    fun addMemberToActiveGroup(talent: BackendTalent) {
        val conv = activeConversation.value?.conversation ?: return
        val repo = messagingRepository ?: return
        viewModelScope.launch {
            runCatching {
                repo.addGroupMember(
                    conv,
                    ChatPeerDescriptor(
                        rawId = talent.id,
                        name = talent.name,
                        role = talent.role,
                        avatarUrl = talent.avatarUrl,
                        isRealFirebaseUid = !talent.id.startsWith("t_") && !talent.id.startsWith("seed_")
                    )
                )
            }.onSuccess {
                _snackbarMessage.value = "Added ${talent.name} to group."
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _snackbarMessage.value = e.localizedMessage ?: "Failed to add member."
            }
        }
    }

    fun removeMemberFromActiveGroup(memberUid: String) {
        val conv = activeConversation.value?.conversation ?: return
        val repo = messagingRepository ?: return
        viewModelScope.launch {
            runCatching { repo.removeGroupMember(conv, memberUid) }
                .onSuccess { _snackbarMessage.value = "Member removed from group." }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _snackbarMessage.value = e.localizedMessage ?: "Unable to remove member."
                }
        }
    }

    fun leaveActiveGroup(onLeft: () -> Unit = {}) {
        val conv = activeConversation.value?.conversation ?: return
        val repo = messagingRepository ?: return
        viewModelScope.launch {
            runCatching { repo.leaveGroupConversation(conv) }
                .onSuccess {
                    _activeConversationId.value = null
                    _snackbarMessage.value = "Left conversation."
                    onLeft()
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _snackbarMessage.value = e.localizedMessage ?: "Unable to leave conversation."
                }
        }
    }

    fun toggleBlockPeerInActiveChat() {
        val item = activeConversation.value ?: return
        val myUid = _firebaseUid.value ?: return
        val peerUid = item.conversation.peerUidFor(myUid) ?: return
        val peerName = item.conversation.displayTitleFor(myUid)
        val repo = messagingRepository ?: return
        viewModelScope.launch {
            runCatching {
                repo.toggleBlockPeer(item.conversation, peerUid, peerName, item.isPeerBlocked)
            }.onSuccess {
                _snackbarMessage.value = if (item.isPeerBlocked) {
                    "Unblocked $peerName."
                } else {
                    "Blocked $peerName."
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _snackbarMessage.value = e.localizedMessage ?: "Failed to update block status."
            }
        }
    }

    fun reportActiveConversationPeer(reason: String, details: String) {
        val item = activeConversation.value ?: return
        val myUid = _firebaseUid.value ?: return
        val peerUid = item.conversation.peerUidFor(myUid) ?: item.conversation.createdBy
        val repo = messagingRepository ?: return
        viewModelScope.launch {
            runCatching {
                repo.submitModerationReport(
                    targetUserId = peerUid,
                    conversationId = item.conversation.id,
                    reason = reason,
                    details = details
                )
            }.onSuccess {
                _snackbarMessage.value = "Safety report submitted to IDEA2IMPACT moderation."
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _snackbarMessage.value = e.localizedMessage ?: "Unable to submit report."
            }
        }
    }

    // --- Collaborations & Workspaces Actions ---

    fun selectCollaborationProject(projectId: String?) {
        _activeProjectId.value = projectId
        _activeTaskId.value = null
    }

    fun selectTaskForComments(taskId: String?) {
        _activeTaskId.value = taskId
    }

    fun createCollaborationFromDraft(
        draft: StartCollaborationDraft,
        onCreated: (projectId: String, conversationId: String) -> Unit = { _, _ -> }
    ) {
        val repo = collaborationRepository ?: return
        if (_firebaseUid.value == null) {
            _snackbarMessage.value = "Sign in with Google to launch a collaboration workspace."
            openAuthModal()
            return
        }
        val sourceConv = draft.sourceConversationId?.let { id ->
            conversations.value.firstOrNull { it.conversation.id == id }?.conversation
        }
        val myProfile = myFirestoreProfile.value
        val myName = myProfile?.name ?: _currentUser.value?.name ?: "Innovator"
        val myRole = myProfile?.role ?: _currentUser.value?.role ?: "Creator"
        val myAvatar = myProfile?.avatarUrl ?: _currentUser.value?.avatarUrl
        viewModelScope.launch {
            _isActionBusy.value = true
            try {
                val (projectId, convId) = repo.createCollaborationWorkspace(
                    draft = draft,
                    myName = myName,
                    myRole = myRole,
                    myAvatar = myAvatar,
                    sourceConversation = sourceConv
                )
                _activeProjectId.value = projectId
                _snackbarMessage.value = "Collaboration workspace \"${draft.title}\" launched!"
                onCreated(projectId, convId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarMessage.value = e.localizedMessage ?: "Failed to create collaboration project."
            } finally {
                _isActionBusy.value = false
            }
        }
    }

    fun inviteCollaboratorToActiveProject(
        talent: BackendTalent,
        proposedRole: String,
        message: String
    ) {
        val project = activeProject.value ?: return
        val repo = collaborationRepository ?: return
        val myName = myFirestoreProfile.value?.name ?: _currentUser.value?.name ?: "Innovator"
        viewModelScope.launch {
            _isActionBusy.value = true
            try {
                repo.inviteCollaboratorToProject(
                    project = project,
                    inviteeId = talent.id,
                    inviteeName = talent.name,
                    proposedRole = proposedRole,
                    message = message,
                    myName = myName
                )
                _snackbarMessage.value = "Sent collaboration invite to ${talent.name}."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarMessage.value = e.localizedMessage ?: "Unable to invite collaborator."
            } finally {
                _isActionBusy.value = false
            }
        }
    }

    fun respondToCollaborationInvite(
        invite: CollaborationInvitation,
        accept: Boolean,
        onAccepted: (String) -> Unit = {}
    ) {
        val repo = collaborationRepository ?: return
        val myName = myFirestoreProfile.value?.name ?: _currentUser.value?.name ?: "Collaborator"
        val myAvatar = myFirestoreProfile.value?.avatarUrl ?: _currentUser.value?.avatarUrl
        viewModelScope.launch {
            _isActionBusy.value = true
            try {
                repo.respondToInvitation(invite, accept, myName, myAvatar)
                if (accept) {
                    _activeProjectId.value = invite.projectId
                    _snackbarMessage.value = "Joined \"${invite.projectTitle}\"!"
                    onAccepted(invite.projectId)
                } else {
                    _snackbarMessage.value = "Declined invitation to \"${invite.projectTitle}\"."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarMessage.value = e.localizedMessage ?: "Failed to respond to invitation."
            } finally {
                _isActionBusy.value = false
            }
        }
    }

    fun createProjectTask(
        title: String,
        description: String,
        assigneeUid: String?,
        assigneeName: String?,
        priority: TaskPriority,
        dueAtEpochMs: Long?,
        attachmentName: String?
    ) {
        val project = activeProject.value ?: return
        val repo = collaborationRepository ?: return
        val myName = myFirestoreProfile.value?.name ?: _currentUser.value?.name ?: "Member"
        viewModelScope.launch {
            _isActionBusy.value = true
            try {
                repo.createTask(
                    project = project,
                    existingTasks = activeProjectTasks.value,
                    title = title,
                    description = description,
                    assigneeUid = assigneeUid,
                    assigneeName = assigneeName,
                    priority = priority,
                    dueAtEpochMs = dueAtEpochMs,
                    attachmentName = attachmentName,
                    myName = myName
                )
                _snackbarMessage.value = "Added task \"$title\"."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarMessage.value = e.localizedMessage ?: "Unable to create task."
            } finally {
                _isActionBusy.value = false
            }
        }
    }

    fun updateProjectTaskStatus(task: CollaborationTask, status: TaskStatus) {
        val project = activeProject.value ?: return
        val repo = collaborationRepository ?: return
        val myName = myFirestoreProfile.value?.name ?: _currentUser.value?.name ?: "Member"
        viewModelScope.launch {
            runCatching {
                repo.updateTaskStatus(
                    project = project,
                    allTasks = activeProjectTasks.value,
                    task = task,
                    newStatus = status,
                    myName = myName
                )
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _snackbarMessage.value = e.localizedMessage ?: "Failed to update task."
            }
        }
    }

    fun deleteProjectTask(task: CollaborationTask) {
        val project = activeProject.value ?: return
        val repo = collaborationRepository ?: return
        viewModelScope.launch {
            runCatching {
                repo.deleteTask(project, activeProjectTasks.value, task)
            }.onSuccess {
                _snackbarMessage.value = "Task deleted."
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _snackbarMessage.value = e.localizedMessage ?: "Unable to delete task."
            }
        }
    }

    fun addProjectTaskComment(task: CollaborationTask, body: String) {
        val project = activeProject.value ?: return
        val repo = collaborationRepository ?: return
        val myName = myFirestoreProfile.value?.name ?: _currentUser.value?.name ?: "Member"
        viewModelScope.launch {
            runCatching {
                repo.addTaskComment(project, task, body, myName)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _snackbarMessage.value = e.localizedMessage ?: "Failed to add task comment."
            }
        }
    }

    fun uploadProjectFile(
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
        referenceUrl: String?,
        previewBase64: String?,
        notes: String?
    ) {
        val project = activeProject.value ?: return
        val repo = collaborationRepository ?: return
        val myName = myFirestoreProfile.value?.name ?: _currentUser.value?.name ?: "Member"
        viewModelScope.launch {
            _isActionBusy.value = true
            try {
                repo.uploadSharedFile(
                    project = project,
                    fileName = fileName,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes,
                    referenceUrl = referenceUrl,
                    previewBase64 = previewBase64,
                    notes = notes,
                    myName = myName
                )
                _snackbarMessage.value = "Shared private file \"$fileName\" with workspace."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarMessage.value = e.localizedMessage ?: "Failed to share file."
            } finally {
                _isActionBusy.value = false
            }
        }
    }

    fun deleteProjectFile(fileId: String) {
        val projectId = _activeProjectId.value ?: return
        val repo = collaborationRepository ?: return
        viewModelScope.launch {
            runCatching { repo.deleteSharedFile(projectId, fileId) }
                .onSuccess { _snackbarMessage.value = "Removed shared file." }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _snackbarMessage.value = e.localizedMessage ?: "Unable to delete file."
                }
        }
    }

    fun postProjectUpdate(message: String) {
        val project = activeProject.value ?: return
        val repo = collaborationRepository ?: return
        val myName = myFirestoreProfile.value?.name ?: _currentUser.value?.name ?: "Member"
        viewModelScope.launch {
            runCatching { repo.postProjectUpdate(project, message, myName) }
                .onSuccess { _snackbarMessage.value = "Posted update to workspace." }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _snackbarMessage.value = e.localizedMessage ?: "Failed to post update."
                }
        }
    }

    fun updateCollaborationStatus(newStatus: CollaborationStatus) {
        val project = activeProject.value ?: return
        val repo = collaborationRepository ?: return
        val myName = myFirestoreProfile.value?.name ?: _currentUser.value?.name ?: "Member"
        viewModelScope.launch {
            runCatching { repo.updateProjectStatus(project, newStatus, myName) }
                .onSuccess { _snackbarMessage.value = "Project marked ${newStatus.label}." }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _snackbarMessage.value = e.localizedMessage ?: "Unable to update status."
                }
        }
    }

    fun updateCollaborationMemberRole(memberUid: String, newRole: String) {
        val project = activeProject.value ?: return
        val repo = collaborationRepository ?: return
        viewModelScope.launch {
            runCatching { repo.updateMemberRole(project, memberUid, newRole) }
                .onSuccess { _snackbarMessage.value = "Updated member role." }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _snackbarMessage.value = e.localizedMessage ?: "Unable to update member role."
                }
        }
    }

    fun removeCollaborationMember(memberUid: String) {
        val project = activeProject.value ?: return
        val repo = collaborationRepository ?: return
        viewModelScope.launch {
            runCatching { repo.removeProjectMember(project, memberUid) }
                .onSuccess { _snackbarMessage.value = "Removed member from project." }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _snackbarMessage.value = e.localizedMessage ?: "Unable to remove member."
                }
        }
    }

    fun leaveActiveCollaboration(onLeft: () -> Unit = {}) {
        val project = activeProject.value ?: return
        val repo = collaborationRepository ?: return
        viewModelScope.launch {
            runCatching { repo.leaveCollaboration(project) }
                .onSuccess {
                    _activeProjectId.value = null
                    _snackbarMessage.value = "Left collaboration workspace."
                    onLeft()
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _snackbarMessage.value = e.localizedMessage ?: "Unable to leave collaboration."
                }
        }
    }

    private fun createWelcomeMessage(): CopilotMessage {
        return CopilotMessage(
            id = "welcome",
            isAssistant = true,
            content = "### Meet AI Sana\n" +
                "Your conversational assistant and startup/idea advisor inside IDEA2IMPACT. Ask any question, share a startup idea for honest feedback, or attach a prototype image.\n\n" +
                "- **1,600-Concept Workbook Comparison:** Share your startup idea and I will summarize it, search the bundled India Startup Ideas reference workbooks (1,600 concept briefs — one comparison source, not an exhaustive company list), highlight similarities and differences, and suggest tailored differentiation angles with low-cost tests.\n" +
                "- **Idea Validation & Venture Advice:** Honest early assessment, risks and open questions, architecture planning, and browsing verified organizations and collaborators.",
            timestamp = SecurityCryptoUtils.formatTime()
        )
    }

    private fun friendlyErrorMessage(e: Throwable, fallback: String): String = when (e) {
        is SocketTimeoutException, is InterruptedIOException ->
            "Cloud server is waking up and timed out. Please tap Retry to connect."
        is UnknownHostException, is ConnectException ->
            "Unable to reach the IDEA2IMPACT server. Check your internet connection and tap Retry."
        else -> e.localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
    }

    private fun TalentProfile.toBackendTalent(): BackendTalent {
        val roleCat = when (role) {
            "Creator" -> "Creators"
            "Investor" -> "Investors"
            "Patent Professional" -> "Patent Professionals"
            else -> "Builders"
        }
        return BackendTalent(
            id = userId.ifBlank { id },
            name = name,
            role = role,
            roleCategory = roleCat,
            headline = headline,
            skills = skills,
            location = location.ifBlank { "India" },
            avatarUrl = avatarUrl,
            verified = projectsCount > 0
        )
    }

    override fun onCleared() {
        networkDiagnosticManager.stopMonitoring()
        super.onCleared()
    }

    companion object {
        private const val TAG = "AppViewModel"

        fun mergeNotificationAlerts(
            localAlerts: List<NotificationAlert>,
            cloudAlerts: List<CloudNotification>
        ): List<NotificationAlert> {
            val mappedCloud = cloudAlerts.map { c ->
                val type = when (c.notificationType) {
                    "MESSAGE" -> NotificationType.MESSAGE
                    "TASK_ASSIGNED", "TASK_COMPLETED", "DEADLINE_ALERT" -> NotificationType.TASK
                    "COLLAB_INVITE", "COLLAB_RESPONSE", "PROJECT_UPDATE" -> NotificationType.COLLABORATION
                    "PROPOSAL" -> NotificationType.PROPOSAL
                    else -> NotificationType.PLATFORM_UPDATE
                }
                val targetRoute = when (c.targetType) {
                    "CONVERSATION" -> "messages"
                    "COLLABORATION" -> "collaborations"
                    else -> null
                }
                NotificationAlert(
                    id = "cloud_${c.id}",
                    type = type,
                    title = c.title,
                    message = c.body,
                    createdAtEpochMs = c.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                    isRead = c.read,
                    innovationId = if (c.targetType == "INNOVATION") c.targetId else null,
                    targetRoute = targetRoute,
                    targetId = c.targetId
                )
            }
            return (mappedCloud + localAlerts)
                .distinctBy { it.id }
                .sortedByDescending { it.createdAtEpochMs }
        }
    }
}
