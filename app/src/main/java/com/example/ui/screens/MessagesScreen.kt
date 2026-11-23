package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.BackendInnovation
import com.example.data.model.BackendTalent
import com.example.data.model.ChatMessage
import com.example.data.model.ConversationType
import com.example.data.model.ConversationUiItem
import com.example.data.model.MessageType
import com.example.data.model.StartCollaborationDraft
import com.example.ui.components.GroupMembersDialog
import com.example.ui.components.NewMessageOrGroupDialog
import com.example.ui.components.ReportUserDialog
import com.example.ui.components.ShareInnovationInChatDialog
import com.example.ui.components.StartCollaborationSheet
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.PurpleGlow
import com.example.ui.theme.RoseError
import com.example.util.LocalHapticFeedbackManager
import com.example.util.MediaAttachmentHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ConversationFilter(val label: String) {
    ALL("All"),
    PINNED("Pinned"),
    GROUPS("Groups"),
    ARCHIVED("Archived")
}

private val QUICK_EMOJIS = listOf("👍", "🚀", "💡", "❤️", "🔥", "👏", "🤝", "🎉")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    isSignedIn: Boolean,
    myUid: String,
    conversations: List<ConversationUiItem>,
    activeConversation: ConversationUiItem?,
    messages: List<ChatMessage>,
    availableTalents: List<BackendTalent>,
    availableInnovations: List<BackendInnovation>,
    pendingInvitesCount: Int,
    isBusy: Boolean,
    onBack: () -> Unit,
    onOpenAuth: () -> Unit,
    onSelectConversation: (String?) -> Unit,
    onMarkActiveRead: () -> Unit,
    onStartDirectChat: (BackendTalent) -> Unit,
    onCreateGroupChat: (title: String, members: List<BackendTalent>) -> Unit,
    onSendMessage: (
        body: String,
        messageType: MessageType,
        replyTo: ChatMessage?,
        attachmentName: String?,
        attachmentMimeType: String?,
        attachmentSizeBytes: Long?,
        attachmentPreviewBase64: String?,
        attachmentUrl: String?,
        sharedInnovation: BackendInnovation?
    ) -> Unit,
    onEditMessage: (messageId: String, newBody: String) -> Unit,
    onDeleteMessage: (messageId: String) -> Unit,
    onToggleReaction: (ChatMessage, String) -> Unit,
    onSetTyping: (Boolean) -> Unit,
    onTogglePin: (ConversationUiItem) -> Unit,
    onToggleMute: (ConversationUiItem) -> Unit,
    onToggleArchive: (ConversationUiItem) -> Unit,
    onAddGroupMember: (BackendTalent) -> Unit,
    onRemoveGroupMember: (String) -> Unit,
    onLeaveGroup: () -> Unit,
    onToggleBlockPeer: () -> Unit,
    onReportPeer: (reason: String, details: String) -> Unit,
    onOpenCollaborationsHub: () -> Unit,
    onOpenCollaborationWorkspace: (String) -> Unit,
    onCreateCollaboration: (StartCollaborationDraft) -> Unit,
    onOpenInnovation: (String) -> Unit,
    onNotifySnackbar: (String) -> Unit
) {
    val haptics = LocalHapticFeedbackManager.current
    var showNewChatDialog by rememberSaveable { mutableStateOf(false) }
    var collabWizardTarget by remember { mutableStateOf<ConversationUiItem?>(null) }

    BackHandler {
        if (activeConversation != null) {
            onSelectConversation(null)
        } else {
            onBack()
        }
    }

    if (!isSignedIn) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Messages", fontWeight = FontWeight.Bold) }
            )
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                        Text("Professional Messaging & Deal Rooms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Connect directly with creators, builders, investors, and patent counsel — and turn any conversation into a real collaboration workspace.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onOpenAuth,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text("Sign in with Google")
                        }
                    }
                }
            }
        }
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag("messages_screen")
    ) {
        val isSplitPane = maxWidth >= 600.dp

        if (isSplitPane) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.width(330.dp).fillMaxHeight()) {
                    ConversationListPane(
                        myUid = myUid,
                        conversations = conversations,
                        selectedId = activeConversation?.conversation?.id,
                        pendingInvitesCount = pendingInvitesCount,
                        onBack = onBack,
                        onSelect = { item ->
                            haptics.tap()
                            onSelectConversation(item.conversation.id)
                        },
                        onNewChat = { showNewChatDialog = true },
                        onOpenCollaborations = onOpenCollaborationsHub,
                        onTogglePin = onTogglePin,
                        onToggleMute = onToggleMute,
                        onToggleArchive = onToggleArchive
                    )
                }
                VerticalDivider()
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (activeConversation != null) {
                        ChatWindowPane(
                            myUid = myUid,
                            conversationItem = activeConversation,
                            messages = messages,
                            availableTalents = availableTalents,
                            availableInnovations = availableInnovations,
                            showBackInHeader = false,
                            onBackToList = { onSelectConversation(null) },
                            onMarkRead = onMarkActiveRead,
                            onSendMessage = onSendMessage,
                            onEditMessage = onEditMessage,
                            onDeleteMessage = onDeleteMessage,
                            onToggleReaction = onToggleReaction,
                            onSetTyping = onSetTyping,
                            onTogglePin = { onTogglePin(activeConversation) },
                            onToggleMute = { onToggleMute(activeConversation) },
                            onToggleArchive = { onToggleArchive(activeConversation) },
                            onAddGroupMember = onAddGroupMember,
                            onRemoveGroupMember = onRemoveGroupMember,
                            onLeaveGroup = onLeaveGroup,
                            onToggleBlockPeer = onToggleBlockPeer,
                            onReportPeer = onReportPeer,
                            onStartCollaborationClick = { collabWizardTarget = activeConversation },
                            onOpenCollaborationWorkspace = onOpenCollaborationWorkspace,
                            onOpenInnovation = onOpenInnovation,
                            onNotifySnackbar = onNotifySnackbar
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, tint = CobaltBlue, modifier = Modifier.size(44.dp))
                                Text("Select a Conversation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "Pick a thread on the left or start a new 1-to-1 or group discussion.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(onClick = { showNewChatDialog = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text("New Message / Group")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Mobile: show conversation list first, full-screen chat when selected
            if (activeConversation == null) {
                ConversationListPane(
                    myUid = myUid,
                    conversations = conversations,
                    selectedId = null,
                    pendingInvitesCount = pendingInvitesCount,
                    onBack = onBack,
                    onSelect = { item ->
                        haptics.tap()
                        onSelectConversation(item.conversation.id)
                    },
                    onNewChat = { showNewChatDialog = true },
                    onOpenCollaborations = onOpenCollaborationsHub,
                    onTogglePin = onTogglePin,
                    onToggleMute = onToggleMute,
                    onToggleArchive = onToggleArchive
                )
            } else {
                ChatWindowPane(
                    myUid = myUid,
                    conversationItem = activeConversation,
                    messages = messages,
                    availableTalents = availableTalents,
                    availableInnovations = availableInnovations,
                    showBackInHeader = true,
                    onBackToList = { onSelectConversation(null) },
                    onMarkRead = onMarkActiveRead,
                    onSendMessage = onSendMessage,
                    onEditMessage = onEditMessage,
                    onDeleteMessage = onDeleteMessage,
                    onToggleReaction = onToggleReaction,
                    onSetTyping = onSetTyping,
                    onTogglePin = { onTogglePin(activeConversation) },
                    onToggleMute = { onToggleMute(activeConversation) },
                    onToggleArchive = { onToggleArchive(activeConversation) },
                    onAddGroupMember = onAddGroupMember,
                    onRemoveGroupMember = onRemoveGroupMember,
                    onLeaveGroup = onLeaveGroup,
                    onToggleBlockPeer = onToggleBlockPeer,
                    onReportPeer = onReportPeer,
                    onStartCollaborationClick = { collabWizardTarget = activeConversation },
                    onOpenCollaborationWorkspace = onOpenCollaborationWorkspace,
                    onOpenInnovation = onOpenInnovation,
                    onNotifySnackbar = onNotifySnackbar
                )
            }
        }
    }

    if (showNewChatDialog) {
        NewMessageOrGroupDialog(
            talents = availableTalents,
            onDismiss = { showNewChatDialog = false },
            onStartDirect = { talent ->
                showNewChatDialog = false
                onStartDirectChat(talent)
            },
            onCreateGroup = { title, members ->
                showNewChatDialog = false
                onCreateGroupChat(title, members)
            }
        )
    }

    val wizardConv = collabWizardTarget
    if (wizardConv != null) {
        val conv = wizardConv.conversation
        val peerUid = conv.peerUidFor(myUid)
        val peerName = peerUid?.let { conv.summaryString(it, "name") }
        val linkedInnovation = conv.innovationId?.let { id ->
            availableInnovations.firstOrNull { it.id == id }
        }
        StartCollaborationSheet(
            sourceConversationId = conv.id,
            preselectedPeerId = peerUid,
            preselectedPeerName = peerName,
            preselectedInnovation = linkedInnovation,
            availableInnovations = availableInnovations,
            availableTalents = availableTalents,
            isBusy = isBusy,
            onDismiss = { collabWizardTarget = null },
            onLaunchCollaboration = { draft ->
                onCreateCollaboration(draft)
                collabWizardTarget = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationListPane(
    myUid: String,
    conversations: List<ConversationUiItem>,
    selectedId: String?,
    pendingInvitesCount: Int,
    onBack: () -> Unit,
    onSelect: (ConversationUiItem) -> Unit,
    onNewChat: () -> Unit,
    onOpenCollaborations: () -> Unit,
    onTogglePin: (ConversationUiItem) -> Unit,
    onToggleMute: (ConversationUiItem) -> Unit,
    onToggleArchive: (ConversationUiItem) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ConversationFilter.ALL) }

    val filtered = remember(conversations, searchQuery, filter, myUid) {
        val q = searchQuery.trim()
        conversations.filter { item ->
            val matchesTab = when (filter) {
                ConversationFilter.ALL -> !item.pref.archived
                ConversationFilter.PINNED -> item.pref.pinned && !item.pref.archived
                ConversationFilter.GROUPS -> item.conversation.parsedType() != ConversationType.DIRECT && !item.pref.archived
                ConversationFilter.ARCHIVED -> item.pref.archived
            }
            if (!matchesTab) return@filter false
            if (q.isBlank()) return@filter true
            val title = item.conversation.displayTitleFor(myUid)
            val role = item.conversation.displayRoleFor(myUid)
            title.contains(q, ignoreCase = true) ||
                role.contains(q, ignoreCase = true) ||
                item.conversation.lastMessagePreview.contains(q, ignoreCase = true)
        }
    }

    val pinnedItems = remember(filtered, filter) {
        if (filter == ConversationFilter.ALL) filtered.filter { it.pref.pinned } else emptyList()
    }
    val recentItems = remember(filtered, filter) {
        if (filter == ConversationFilter.ALL) filtered.filter { !it.pref.pinned } else filtered
    }

    Column(modifier = Modifier.fillMaxSize().testTag("conversation_list_pane")) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("messages_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            title = {
                Text("Messages", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            },
            actions = {
                OutlinedButton(
                    onClick = onOpenCollaborations,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 38.dp).testTag("messages_open_collabs_button")
                ) {
                    Icon(Icons.Filled.FolderShared, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (pendingInvitesCount > 0) "Projects ($pendingInvitesCount)" else "Projects")
                }
                IconButton(
                    onClick = onNewChat,
                    modifier = Modifier.size(48.dp).testTag("messages_new_chat_button")
                ) {
                    Icon(Icons.Filled.AddComment, contentDescription = "New message or group", tint = MaterialTheme.colorScheme.primary)
                }
            }
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val inboxSearchInteractionSource = remember { MutableInteractionSource() }
            val inboxSearchFocused by inboxSearchInteractionSource.collectIsFocusedAsState()
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(
                    width = if (inboxSearchFocused) 1.5.dp else 1.dp,
                    color = if (inboxSearchFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search conversations & roles...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            interactionSource = inboxSearchInteractionSource,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("messages_search_input")
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(ConversationFilter.entries) { tab ->
                    FilterChip(
                        selected = filter == tab,
                        onClick = { filter = tab },
                        label = { Text(tab.label) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "No conversations matched \"$searchQuery\"" else "No conversations yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Message creators from any innovation, connect with builders in Talents, or tap + to start a direct or group chat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onNewChat, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Start a Conversation")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (pinnedItems.isNotEmpty()) {
                    item(key = "pinned_hdr") {
                        Text(
                            text = "Pinned",
                            style = MaterialTheme.typography.labelLarge,
                            color = CobaltBlue,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    items(pinnedItems, key = { "pin_${it.conversation.id}" }) { item ->
                        ConversationRowCard(
                            myUid = myUid,
                            item = item,
                            isSelected = item.conversation.id == selectedId,
                            onClick = { onSelect(item) },
                            onTogglePin = { onTogglePin(item) },
                            onToggleMute = { onToggleMute(item) },
                            onToggleArchive = { onToggleArchive(item) }
                        )
                    }
                }
                if (recentItems.isNotEmpty()) {
                    item(key = "recent_hdr") {
                        Text(
                            text = if (filter == ConversationFilter.ARCHIVED) "Archived" else "Recent Conversations",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                    }
                    items(recentItems, key = { "rec_${it.conversation.id}" }) { item ->
                        ConversationRowCard(
                            myUid = myUid,
                            item = item,
                            isSelected = item.conversation.id == selectedId,
                            onClick = { onSelect(item) },
                            onTogglePin = { onTogglePin(item) },
                            onToggleMute = { onToggleMute(item) },
                            onToggleArchive = { onToggleArchive(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRowCard(
    myUid: String,
    item: ConversationUiItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleArchive: () -> Unit
) {
    val conv = item.conversation
    val title = conv.displayTitleFor(myUid)
    val role = conv.displayRoleFor(myUid)
    val avatar = conv.displayAvatarFor(myUid)
    val unread = conv.unreadFor(myUid)
    val isOnline = item.peerPresence?.online == true
    val typing = conv.activeTypingNames(myUid)
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary
            else if (unread > 0) CobaltBlue.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("conversation_card_${conv.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar + online dot
            Box(modifier = Modifier.size(48.dp)) {
                if (!avatar.isNullOrBlank()) {
                    AsyncImage(
                        model = avatar,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = if (conv.parsedType() == ConversationType.DIRECT) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            PurpleGlow.copy(alpha = 0.18f)
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (conv.parsedType() != ConversationType.DIRECT) {
                                Icon(Icons.Filled.Groups, contentDescription = null, tint = PurpleGlow)
                            } else {
                                Text(title.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (isOnline) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(EmeraldSuccess)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (unread > 0) FontWeight.ExtraBold else FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (item.pref.pinned) {
                            Icon(Icons.Filled.PushPin, contentDescription = "Pinned", tint = CobaltBlue, modifier = Modifier.size(13.dp))
                        }
                        if (item.pref.muted) {
                            Icon(Icons.Filled.VolumeOff, contentDescription = "Muted", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                        }
                    }
                    Text(
                        text = MediaAttachmentHelper.formatConversationTime(conv.lastMessageAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (unread > 0) CobaltBlue else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (typing.isNotEmpty()) "${typing.first()} is typing…"
                        else conv.lastMessagePreview.ifBlank { role },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (typing.isNotEmpty()) EmeraldSuccess
                        else if (unread > 0) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (unread > 0) {
                        Surface(
                            shape = CircleShape,
                            color = CobaltBlue,
                            modifier = Modifier.padding(start = 6.dp)
                        ) {
                            Text(
                                text = if (unread > 99) "99+" else unread.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Conversation options", modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (item.pref.pinned) "Unpin" else "Pin") },
                        leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onTogglePin()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (item.pref.muted) "Unmute" else "Mute") },
                        leadingIcon = { Icon(if (item.pref.muted) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onToggleMute()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (item.pref.archived) "Restore" else "Archive") },
                        leadingIcon = { Icon(if (item.pref.archived) Icons.Filled.Unarchive else Icons.Filled.Archive, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onToggleArchive()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatWindowPane(
    myUid: String,
    conversationItem: ConversationUiItem,
    messages: List<ChatMessage>,
    availableTalents: List<BackendTalent>,
    availableInnovations: List<BackendInnovation>,
    showBackInHeader: Boolean,
    onBackToList: () -> Unit,
    onMarkRead: () -> Unit,
    onSendMessage: (
        body: String,
        messageType: MessageType,
        replyTo: ChatMessage?,
        attachmentName: String?,
        attachmentMimeType: String?,
        attachmentSizeBytes: Long?,
        attachmentPreviewBase64: String?,
        attachmentUrl: String?,
        sharedInnovation: BackendInnovation?
    ) -> Unit,
    onEditMessage: (String, String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onToggleReaction: (ChatMessage, String) -> Unit,
    onSetTyping: (Boolean) -> Unit,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleArchive: () -> Unit,
    onAddGroupMember: (BackendTalent) -> Unit,
    onRemoveGroupMember: (String) -> Unit,
    onLeaveGroup: () -> Unit,
    onToggleBlockPeer: () -> Unit,
    onReportPeer: (String, String) -> Unit,
    onStartCollaborationClick: () -> Unit,
    onOpenCollaborationWorkspace: (String) -> Unit,
    onOpenInnovation: (String) -> Unit,
    onNotifySnackbar: (String) -> Unit
) {
    val conv = conversationItem.conversation
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedbackManager.current
    val listState = rememberLazyListState()

    var composerText by rememberSaveable(conv.id) { mutableStateOf("") }
    var searchInChatOpen by rememberSaveable(conv.id) { mutableStateOf(false) }
    var chatSearchQuery by rememberSaveable(conv.id) { mutableStateOf("") }
    var showEmojiBar by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showShareInnovationDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showMembersDialog by remember { mutableStateOf(false) }

    var replyTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var editingTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var actionTargetMsg by remember { mutableStateOf<ChatMessage?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val payload = MediaAttachmentHelper.extractCompressedImage(context, uri)
                if (payload != null) {
                    onSendMessage(
                        composerText.trim(),
                        MessageType.IMAGE,
                        replyTarget,
                        payload.fileName,
                        payload.mimeType,
                        payload.sizeBytes,
                        payload.previewBase64,
                        null,
                        null
                    )
                    composerText = ""
                    replyTarget = null
                }
            }
        }
    }

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val doc = MediaAttachmentHelper.extractDocumentMetadata(context, uri)
                onSendMessage(
                    composerText.trim().ifBlank { "Shared document: ${doc.fileName}" },
                    MessageType.DOCUMENT,
                    replyTarget,
                    doc.fileName,
                    doc.mimeType,
                    doc.sizeBytes,
                    null,
                    uri.toString(),
                    null
                )
                composerText = ""
                replyTarget = null
            }
        }
    }

    LaunchedEffect(conv.id, messages.size) {
        onMarkRead()
        if (messages.isNotEmpty() && chatSearchQuery.isBlank()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LaunchedEffect(composerText) {
        if (composerText.isNotBlank() && editingTarget == null) {
            onSetTyping(true)
            delay(3500L)
            onSetTyping(false)
        } else {
            onSetTyping(false)
        }
    }

    val displayedMessages = remember(messages, chatSearchQuery) {
        val q = chatSearchQuery.trim()
        if (q.isBlank()) messages
        else messages.filter {
            it.body.contains(q, ignoreCase = true) ||
                (it.attachmentName?.contains(q, ignoreCase = true) == true) ||
                (it.sharedInnovationTitle?.contains(q, ignoreCase = true) == true)
        }
    }

    val title = conv.displayTitleFor(myUid)
    val peerPresence = conversationItem.peerPresence
    val subtitle = when {
        conv.parsedType() != ConversationType.DIRECT -> conv.displayRoleFor(myUid)
        peerPresence?.online == true -> "Online · ${conv.displayRoleFor(myUid)}"
        peerPresence?.lastSeen != null -> "${MediaAttachmentHelper.formatLastSeen(peerPresence.lastSeen)} · ${conv.displayRoleFor(myUid)}"
        else -> conv.displayRoleFor(myUid)
    }
    val typingNames = conv.activeTypingNames(myUid)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .testTag("chat_window_pane")
    ) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            navigationIcon = {
                if (showBackInHeader) {
                    IconButton(onClick = onBackToList, modifier = Modifier.size(48.dp).testTag("chat_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to messages")
                    }
                }
            },
            title = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (peerPresence?.online == true) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(EmeraldSuccess))
                        }
                    }
                    Text(
                        text = if (typingNames.isNotEmpty()) "${typingNames.first()} is typing…" else subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (typingNames.isNotEmpty()) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            actions = {
                if (conv.collaborationId != null) {
                    OutlinedButton(
                        onClick = {
                            haptics.tap()
                            onOpenCollaborationWorkspace(conv.collaborationId)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 38.dp).testTag("chat_open_workspace_button")
                    ) {
                        Icon(Icons.Filled.FolderShared, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Workspace")
                    }
                } else {
                    Button(
                        onClick = {
                            haptics.tap()
                            onStartCollaborationClick()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleGlow, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 38.dp).testTag("chat_start_collaboration_button")
                    ) {
                        Icon(Icons.Filled.RocketLaunch, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Start Collab", maxLines = 1)
                    }
                }
                IconButton(onClick = { searchInChatOpen = !searchInChatOpen }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.Search, contentDescription = "Search in chat")
                }
                Box {
                    IconButton(onClick = { showOverflowMenu = true }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More chat options")
                    }
                    DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (conversationItem.pref.pinned) "Unpin" else "Pin Conversation") },
                            leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                            onClick = { showOverflowMenu = false; onTogglePin() }
                        )
                        DropdownMenuItem(
                            text = { Text(if (conversationItem.pref.muted) "Unmute" else "Mute Notifications") },
                            leadingIcon = { Icon(Icons.Filled.VolumeOff, contentDescription = null) },
                            onClick = { showOverflowMenu = false; onToggleMute() }
                        )
                        DropdownMenuItem(
                            text = { Text(if (conversationItem.pref.archived) "Unarchive" else "Archive Chat") },
                            leadingIcon = { Icon(Icons.Filled.Archive, contentDescription = null) },
                            onClick = { showOverflowMenu = false; onToggleArchive() }
                        )
                        if (conv.parsedType() != ConversationType.DIRECT) {
                            DropdownMenuItem(
                                text = { Text("Manage Members") },
                                leadingIcon = { Icon(Icons.Filled.Groups, contentDescription = null) },
                                onClick = { showOverflowMenu = false; showMembersDialog = true }
                            )
                            if (conv.createdBy != myUid) {
                                DropdownMenuItem(
                                    text = { Text("Leave Group") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = RoseError) },
                                    onClick = { showOverflowMenu = false; onLeaveGroup() }
                                )
                            }
                        } else if (conv.peerUidFor(myUid) != null) {
                            DropdownMenuItem(
                                text = { Text(if (conversationItem.isPeerBlocked) "Unblock User" else "Block User") },
                                leadingIcon = { Icon(Icons.Filled.Block, contentDescription = null, tint = RoseError) },
                                onClick = { showOverflowMenu = false; onToggleBlockPeer() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Report Conversation") },
                            leadingIcon = { Icon(Icons.Filled.Report, contentDescription = null, tint = RoseError) },
                            onClick = { showOverflowMenu = false; showReportDialog = true }
                        )
                    }
                }
            }
        )

        AnimatedVisibility(visible = searchInChatOpen) {
            val chatSearchInteractionSource = remember { MutableInteractionSource() }
            val chatSearchFocused by chatSearchInteractionSource.collectIsFocusedAsState()
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(
                    width = if (chatSearchFocused) 1.5.dp else 1.dp,
                    color = if (chatSearchFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .height(38.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (chatSearchQuery.isEmpty()) {
                            Text(
                                text = "Search messages...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = chatSearchQuery,
                            onValueChange = { chatSearchQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            interactionSource = chatSearchInteractionSource,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    IconButton(
                        onClick = { chatSearchQuery = ""; searchInChatOpen = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close search",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        if (!conv.innovationTitle.isNullOrBlank() && conv.innovationId != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenInnovation(conv.innovationId) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Topic: ${conv.innovationTitle}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text("View", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Message List
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(displayedMessages, key = { it.id }) { msg ->
                val isMine = msg.senderId == myUid
                ChatMessageBubble(
                    message = msg,
                    isMine = isMine,
                    isDirect = conv.parsedType() == ConversationType.DIRECT,
                    myUid = myUid,
                    onClick = { actionTargetMsg = msg },
                    onOpenInnovation = onOpenInnovation,
                    onOpenCollaboration = onOpenCollaborationWorkspace,
                    onToggleReaction = { emoji -> onToggleReaction(msg, emoji) }
                )
            }
        }

        // Blocked banner or Composer
        if (conv.isBlockedByAny()) {
            Surface(
                color = RoseError.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (conv.isBlockedByMe(myUid)) "You blocked this user." else "Messaging is unavailable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RoseError
                    )
                    if (conv.isBlockedByMe(myUid)) {
                        TextButton(onClick = onToggleBlockPeer) {
                            Text("Unblock")
                        }
                    }
                }
            }
        } else {
            HorizontalDivider()

            // Reply or Edit bar
            val bannerReply = replyTarget
            val bannerEdit = editingTarget
            if (bannerReply != null || bannerEdit != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (bannerEdit != null) "Editing message" else "Replying to ${bannerReply?.senderName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = bannerEdit?.body ?: bannerReply?.body.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                replyTarget = null
                                editingTarget = null
                                if (bannerEdit != null) composerText = ""
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel")
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showEmojiBar) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(QUICK_EMOJIS) { emoji ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable {
                                    composerText += emoji
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(emoji)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box {
                    IconButton(onClick = { showAttachMenu = true }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Filled.AttachFile,
                            contentDescription = "Attach file or innovation",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(expanded = showAttachMenu, onDismissRequest = { showAttachMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Attach Photo") },
                            leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                            onClick = {
                                showAttachMenu = false
                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Attach Document") },
                            leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
                            onClick = {
                                showAttachMenu = false
                                documentPicker.launch(arrayOf("*/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share Innovation Listing") },
                            leadingIcon = { Icon(Icons.Filled.Lightbulb, contentDescription = null) },
                            onClick = {
                                showAttachMenu = false
                                showShareInnovationDialog = true
                            }
                        )
                    }
                }

                IconButton(onClick = { showEmojiBar = !showEmojiBar }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.EmojiEmotions,
                        contentDescription = "Emoji picker",
                        tint = if (showEmojiBar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                val composerInteractionSource = remember { MutableInteractionSource() }
                val composerFocused by composerInteractionSource.collectIsFocusedAsState()
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(
                        width = if (composerFocused) 1.5.dp else 1.dp,
                        color = if (composerFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 36.dp, max = 88.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (composerText.isEmpty()) {
                            Text(
                                text = "Message ${title.take(18)}…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = composerText,
                            onValueChange = { composerText = it },
                            singleLine = false,
                            maxLines = 4,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            interactionSource = composerInteractionSource,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chat_message_input")
                        )
                    }
                }

                IconButton(
                    onClick = {
                        val clean = composerText.trim()
                        if (clean.isBlank()) return@IconButton
                        haptics.tap()
                        val editMsg = editingTarget
                        if (editMsg != null) {
                            onEditMessage(editMsg.id, clean)
                            editingTarget = null
                            composerText = ""
                        } else {
                            val type = if (clean.startsWith("http://") || clean.startsWith("https://")) {
                                MessageType.LINK
                            } else MessageType.TEXT
                            onSendMessage(clean, type, replyTarget, null, null, null, null, null, null)
                            composerText = ""
                            replyTarget = null
                            showEmojiBar = false
                        }
                    },
                    enabled = composerText.trim().isNotBlank(),
                    modifier = Modifier.size(48.dp).testTag("chat_send_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        tint = if (composerText.trim().isNotBlank()) CobaltBlue else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Message Actions Sheet
    val targetMsg = actionTargetMsg
    if (targetMsg != null) {
        ModalBottomSheet(onDismissRequest = { actionTargetMsg = null }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    QUICK_EMOJIS.take(6).forEach { emoji ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(44.dp).clip(CircleShape).clickable {
                                onToggleReaction(targetMsg, emoji)
                                actionTargetMsg = null
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(emoji)
                            }
                        }
                    }
                }
                HorizontalDivider()
                TextButton(
                    onClick = {
                        replyTarget = targetMsg
                        editingTarget = null
                        actionTargetMsg = null
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Reply")
                }
                if (targetMsg.body.isNotBlank() && !targetMsg.deleted) {
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(targetMsg.body))
                            onNotifySnackbar("Message copied.")
                            actionTargetMsg = null
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Copy Text")
                    }
                }
                if (targetMsg.senderId == myUid && !targetMsg.deleted) {
                    TextButton(
                        onClick = {
                            editingTarget = targetMsg
                            replyTarget = null
                            composerText = targetMsg.body
                            actionTargetMsg = null
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Edit Message")
                    }
                    TextButton(
                        onClick = {
                            onDeleteMessage(targetMsg.id)
                            actionTargetMsg = null
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = RoseError)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Message", color = RoseError)
                    }
                }
            }
        }
    }

    if (showShareInnovationDialog) {
        ShareInnovationInChatDialog(
            innovations = availableInnovations,
            onDismiss = { showShareInnovationDialog = false },
            onSelectInnovation = { inn ->
                showShareInnovationDialog = false
                onSendMessage(
                    "Shared innovation: ${inn.title}",
                    MessageType.LINK,
                    replyTarget,
                    null,
                    null,
                    null,
                    null,
                    null,
                    inn
                )
                replyTarget = null
            }
        )
    }

    if (showReportDialog) {
        ReportUserDialog(
            peerName = title,
            onDismiss = { showReportDialog = false },
            onSubmitReport = { reason, details ->
                showReportDialog = false
                onReportPeer(reason, details)
            }
        )
    }

    if (showMembersDialog) {
        GroupMembersDialog(
            conversation = conv,
            myUid = myUid,
            availableTalents = availableTalents,
            onDismiss = { showMembersDialog = false },
            onAddMember = onAddGroupMember,
            onRemoveMember = onRemoveGroupMember
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    isDirect: Boolean,
    myUid: String,
    onClick: () -> Unit,
    onOpenInnovation: (String) -> Unit,
    onOpenCollaboration: (String) -> Unit,
    onToggleReaction: (String) -> Unit
) {
    val context = LocalContext.current
    val parsedType = message.parsedType()
    if (parsedType == MessageType.SYSTEM) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
        return
    }

    val bubbleColor = if (isMine) CobaltBlue.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val borderColor = if (isMine) CobaltBlue.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val bitmap = remember(message.attachmentPreviewBase64) {
        MediaAttachmentHelper.decodeBase64Bitmap(message.attachmentPreviewBase64)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        if (!isMine && !isDirect) {
            Text(
                text = message.senderName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
            )
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 16.dp
            ),
            color = bubbleColor,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (!message.replyToSnippet.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = message.replyToSenderName ?: "Reply",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = message.replyToSnippet,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = message.attachmentName ?: "Photo attachment",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }

                if (parsedType == MessageType.DOCUMENT && !message.attachmentName.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth().clickable {
                            message.attachmentUrl?.let { raw ->
                                if (raw.startsWith("http")) {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(raw))) }
                                }
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null, tint = CobaltBlue)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(message.attachmentName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(MediaAttachmentHelper.formatBytes(message.attachmentSizeBytes ?: 0L), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                if (!message.sharedInnovationId.isNullOrBlank() && !message.sharedInnovationTitle.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth().clickable { onOpenInnovation(message.sharedInnovationId) }
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text(message.sharedInnovationTitle, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("Open", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                if (!message.sharedCollaborationId.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = PurpleGlow.copy(alpha = 0.16f),
                        modifier = Modifier.fillMaxWidth().clickable { onOpenCollaboration(message.sharedCollaborationId) }
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.Handshake, contentDescription = null, tint = PurpleGlow, modifier = Modifier.size(16.dp))
                            Text("Open Collaboration Workspace", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = PurpleGlow)
                        }
                    }
                }

                if (message.body.isNotBlank()) {
                    Text(
                        text = message.body,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = if (message.deleted) FontStyle.Italic else FontStyle.Normal,
                        color = if (message.deleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (message.edited && !message.deleted) {
                        Text("edited", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = MediaAttachmentHelper.formatMessageTimestamp(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isMine) {
                        val readByOther = message.readByUids.any { it != myUid }
                        Icon(
                            imageVector = if (readByOther || message.deliveredToUids.size > 1) Icons.Filled.DoneAll else Icons.Filled.Done,
                            contentDescription = if (readByOther) "Read" else "Delivered",
                            tint = if (readByOther) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        if (message.reactions.isNotEmpty()) {
            val grouped = message.reactions.values.groupingBy { it }.eachCount()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                grouped.forEach { (emoji, count) ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { onToggleReaction(emoji) }
                    ) {
                        Text(
                            text = if (count > 1) "$emoji $count" else emoji,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
