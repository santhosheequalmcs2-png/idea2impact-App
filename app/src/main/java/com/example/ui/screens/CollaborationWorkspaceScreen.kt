package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BackendTalent
import com.example.data.model.CollaborationActivity
import com.example.data.model.CollaborationFile
import com.example.data.model.CollaborationProject
import com.example.data.model.CollaborationStatus
import com.example.data.model.CollaborationTask
import com.example.data.model.TaskComment
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GoldAmber
import com.example.ui.theme.PurpleGlow
import com.example.ui.theme.RoseError
import com.example.util.LocalHapticFeedbackManager
import com.example.util.MediaAttachmentHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CollaborationWorkspaceScreen(
    project: CollaborationProject?,
    myUid: String,
    tasks: List<CollaborationTask>,
    files: List<CollaborationFile>,
    activity: List<CollaborationActivity>,
    activeTaskComments: List<TaskComment>,
    availableTalents: List<BackendTalent>,
    isBusy: Boolean,
    onBack: () -> Unit,
    onOpenProjectChat: (String) -> Unit,
    onUpdateProjectStatus: (CollaborationStatus) -> Unit,
    onPostUpdate: (String) -> Unit,
    onCreateTask: (
        title: String,
        description: String,
        assigneeUid: String?,
        assigneeName: String?,
        priority: TaskPriority,
        dueAtEpochMs: Long?,
        attachmentName: String?
    ) -> Unit,
    onUpdateTaskStatus: (CollaborationTask, TaskStatus) -> Unit,
    onDeleteTask: (CollaborationTask) -> Unit,
    onSelectTaskForComments: (String?) -> Unit,
    onAddTaskComment: (CollaborationTask, String) -> Unit,
    onUploadFile: (
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
        referenceUrl: String?,
        previewBase64: String?,
        notes: String?
    ) -> Unit,
    onDeleteFile: (String) -> Unit,
    onInviteMember: (talent: BackendTalent, role: String, message: String) -> Unit,
    onUpdateMemberRole: (memberUid: String, role: String) -> Unit,
    onRemoveMember: (memberUid: String) -> Unit,
    onLeaveProject: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedbackManager.current

    if (project == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Workspace") }
            )
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select a collaboration workspace to open.")
            }
        }
        return
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Tasks (${tasks.size})", "Files (${files.size})", "Team & Log")
    var showStatusMenu by remember { mutableStateOf(false) }
    var showCreateTaskDialog by rememberSaveable { mutableStateOf(false) }
    var commentTargetTask by remember { mutableStateOf<CollaborationTask?>(null) }
    var showAddFileDialog by rememberSaveable { mutableStateOf(false) }
    var showInviteMemberDialog by rememberSaveable { mutableStateOf(false) }
    var updateDraft by rememberSaveable { mutableStateOf("") }

    val isAdmin = myUid in project.adminUids
    val isOwner = myUid == project.ownerId
    val pendingTasksCount = remember(tasks) { tasks.count { it.parsedStatus() != TaskStatus.COMPLETED } }
    val completedTasksCount = remember(tasks) { tasks.count { it.parsedStatus() == TaskStatus.COMPLETED } }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val payload = MediaAttachmentHelper.extractCompressedImage(context, uri)
                if (payload != null) {
                    onUploadFile(
                        payload.fileName,
                        payload.mimeType,
                        payload.sizeBytes,
                        null,
                        payload.previewBase64,
                        "Uploaded workspace image"
                    )
                }
            }
        }
    }

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val payload = MediaAttachmentHelper.extractDocumentMetadata(context, uri)
                onUploadFile(
                    payload.fileName,
                    payload.mimeType,
                    payload.sizeBytes,
                    uri.toString(),
                    null,
                    "Private team document"
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .testTag("collaboration_workspace_screen")
    ) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            navigationIcon = {
                IconButton(
                    onClick = {
                        haptics.tap()
                        onBack()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Collaborations")
                }
            },
            title = {
                Column {
                    Text(
                        text = project.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${project.category} · ${project.progressPercent}% Complete",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            actions = {
                if (!project.conversationId.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = {
                            haptics.tap()
                            onOpenProjectChat(project.conversationId)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .heightIn(min = 38.dp)
                            .testTag("workspace_open_chat_button")
                    ) {
                        Icon(Icons.Filled.ChatBubble, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Chat")
                    }
                }
                Box {
                    TextButton(
                        onClick = { showStatusMenu = true },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(project.parsedStatus().label)
                    }
                    DropdownMenu(
                        expanded = showStatusMenu,
                        onDismissRequest = { showStatusMenu = false }
                    ) {
                        CollaborationStatus.entries.forEach { st ->
                            DropdownMenuItem(
                                text = { Text(st.label) },
                                onClick = {
                                    showStatusMenu = false
                                    onUpdateProjectStatus(st)
                                }
                            )
                        }
                    }
                }
            }
        )

        SecondaryScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {
            tabs.forEachIndexed { idx, label ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = {
                        haptics.tap()
                        selectedTab = idx
                    },
                    text = { Text(label, maxLines = 1) },
                    modifier = Modifier.testTag("workspace_tab_$idx")
                )
            }
        }

        when (selectedTab) {
            // --- Tab 0: Overview & Progress ---
            0 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Progress & Deadlines Summary Card
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, CobaltBlue.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Collaboration Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${project.progressPercent}%",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = CobaltBlue
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (project.progressPercent / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(50)),
                                    color = CobaltBlue,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Completed: $completedTasksCount", style = MaterialTheme.typography.labelMedium, color = EmeraldSuccess)
                                    Text("Pending: $pendingTasksCount", style = MaterialTheme.typography.labelMedium, color = GoldAmber)
                                    Text("Team: ${project.memberUids.size}", style = MaterialTheme.typography.labelMedium, color = PurpleGlow)
                                }
                            }
                        }
                    }

                    // Project Overview Specs
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Project Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(project.description, style = MaterialTheme.typography.bodyMedium)
                                if (!project.relatedInnovationTitle.isNullOrBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    ) {
                                        Text(
                                            text = "Linked Innovation: ${project.relatedInnovationTitle}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                                if (project.objectives.isNotBlank()) {
                                    Text("Objectives", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                    Text(project.objectives, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (project.expectedDeliverables.isNotBlank()) {
                                    Text("Expected Deliverables", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                    Text(project.expectedDeliverables, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (project.proposedTimeline.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Text("Timeline: ${project.proposedTimeline}", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                if (project.requiredSkills.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        project.requiredSkills.forEach { skill ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text(
                                                    text = skill,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Post Project Update Composer
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Post Project Update", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val updateInteractionSource = remember { MutableInteractionSource() }
                                    val updateFocused by updateInteractionSource.collectIsFocusedAsState()
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = BorderStroke(
                                            width = if (updateFocused) 1.5.dp else 1.dp,
                                            color = if (updateFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 38.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (updateDraft.isEmpty()) {
                                                Text(
                                                    text = "Share milestone update with team...",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            BasicTextField(
                                                value = updateDraft,
                                                onValueChange = { updateDraft = it },
                                                singleLine = true,
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                    color = MaterialTheme.colorScheme.onSurface
                                                ),
                                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                                interactionSource = updateInteractionSource,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            haptics.tap()
                                            onPostUpdate(updateDraft)
                                            updateDraft = ""
                                        },
                                        enabled = updateDraft.isNotBlank(),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Post update", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Upcoming Deadlines
                    val upcoming = tasks.filter { it.parsedStatus() != TaskStatus.COMPLETED && it.dueAtEpochMs != null }
                    if (upcoming.isNotEmpty()) {
                        item {
                            Text("Upcoming Deadlines", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        items(upcoming.take(4), key = { "due_${it.id}" }) { t ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = GoldAmber.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, GoldAmber.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(t.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "Assigned: ${t.assigneeName ?: "Unassigned"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = MediaAttachmentHelper.formatDueDate(t.dueAtEpochMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = GoldAmber
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- Tab 1: Tasks Board ---
            1 -> {
                var taskFilter by remember { mutableStateOf<TaskStatus?>(null) }
                val visibleTasks = remember(tasks, taskFilter) {
                    if (taskFilter == null) tasks else tasks.filter { it.parsedStatus() == taskFilter }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            item {
                                FilterChip(
                                    selected = taskFilter == null,
                                    onClick = { taskFilter = null },
                                    label = { Text("All") }
                                )
                            }
                            items(TaskStatus.entries) { st ->
                                FilterChip(
                                    selected = taskFilter == st,
                                    onClick = { taskFilter = st },
                                    label = { Text(st.label) }
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                haptics.tap()
                                showCreateTaskDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.heightIn(min = 44.dp).testTag("workspace_add_task_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Task")
                        }
                    }

                    if (visibleTasks.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No tasks here yet. Tap + Task to assign deliverables and deadlines.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(visibleTasks, key = { it.id }) { task ->
                                WorkspaceTaskCard(
                                    task = task,
                                    canDelete = task.createdBy == myUid || isOwner,
                                    onChangeStatus = { nextStatus ->
                                        haptics.tap()
                                        onUpdateTaskStatus(task, nextStatus)
                                    },
                                    onOpenComments = {
                                        onSelectTaskForComments(task.id)
                                        commentTargetTask = task
                                    },
                                    onDelete = { onDeleteTask(task) }
                                )
                            }
                        }
                    }
                }
            }

            // --- Tab 2: Private Shared Files ---
            2 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = PurpleGlow.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, PurpleGlow.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = PurpleGlow, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Member-Private Workspace Files · Hidden from public feeds; accessible only to accepted collaborators.",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Photo")
                            }
                            OutlinedButton(
                                onClick = {
                                    documentPicker.launch(arrayOf("*/*"))
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Document")
                            }
                            Button(
                                onClick = { showAddFileDialog = true },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Link")
                            }
                        }
                    }

                    if (files.isEmpty()) {
                        item {
                            Text(
                                text = "No private files shared yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(files, key = { it.id }) { file ->
                            val bitmap = remember(file.previewBase64) {
                                MediaAttachmentHelper.decodeBase64Bitmap(file.previewBase64)
                            }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = file.fileName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(150.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Filled.Description, contentDescription = null, tint = CobaltBlue)
                                            Column {
                                                Text(file.fileName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                Text(
                                                    text = "Shared by ${file.uploadedByName} · ${MediaAttachmentHelper.formatBytes(file.sizeBytes)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        if (!file.referenceUrl.isNullOrBlank() && file.referenceUrl.startsWith("http")) {
                                            IconButton(onClick = {
                                                runCatching {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(file.referenceUrl)))
                                                }
                                            }) {
                                                Icon(Icons.Filled.Link, contentDescription = "Open external link")
                                            }
                                        }
                                        if (file.uploadedBy == myUid || isOwner) {
                                            IconButton(onClick = { onDeleteFile(file.id) }) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Delete file", tint = RoseError)
                                            }
                                        }
                                    }
                                    if (!file.notes.isNullOrBlank()) {
                                        Text(file.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- Tab 3: Team Management & Activity Feed ---
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Project Members (${project.memberUids.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (isAdmin) {
                                OutlinedButton(
                                    onClick = { showInviteMemberDialog = true },
                                    modifier = Modifier.heightIn(min = 44.dp)
                                ) {
                                    Icon(Icons.Filled.GroupAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Invite")
                                }
                            }
                        }
                    }

                    items(project.memberUids, key = { "member_$it" }) { memberUid ->
                        val name = project.memberNames[memberUid] ?: if (memberUid == myUid) "You" else "Collaborator"
                        val role = project.memberRoles[memberUid] ?: "Collaborator"
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = if (memberUid == myUid) "$name (You)" else name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(role, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                if (isAdmin && memberUid != project.ownerId) {
                                    IconButton(onClick = { onRemoveMember(memberUid) }) {
                                        Icon(Icons.Filled.PersonRemove, contentDescription = "Remove $name", tint = RoseError)
                                    }
                                }
                            }
                        }
                    }

                    if (!isOwner) {
                        item {
                            OutlinedButton(
                                onClick = onLeaveProject,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseError),
                                border = BorderStroke(1.dp, RoseError.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Leave Collaboration")
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(4.dp))
                        Text("Activity Log (${activity.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    items(activity, key = { "act_${it.id}" }) { entry ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(entry.actorName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        MediaAttachmentHelper.formatConversationTime(entry.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(entry.summary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateTaskDialog) {
        CreateTaskDialog(
            project = project,
            isBusy = isBusy,
            onDismiss = { showCreateTaskDialog = false },
            onCreate = { title, desc, assigneeUid, assigneeName, priority, dueMs, attName ->
                onCreateTask(title, desc, assigneeUid, assigneeName, priority, dueMs, attName)
                showCreateTaskDialog = false
            }
        )
    }

    val activeTask = commentTargetTask
    if (activeTask != null) {
        TaskCommentsSheet(
            task = activeTask,
            comments = activeTaskComments,
            onDismiss = {
                commentTargetTask = null
                onSelectTaskForComments(null)
            },
            onSendComment = { text ->
                onAddTaskComment(activeTask, text)
            }
        )
    }

    if (showAddFileDialog) {
        AddProjectLinkDialog(
            onDismiss = { showAddFileDialog = false },
            onAdd = { name, url, notes ->
                onUploadFile(name, "text/uri-list", 0L, url, null, notes)
                showAddFileDialog = false
            }
        )
    }

    if (showInviteMemberDialog) {
        InviteProjectMemberDialog(
            existingMemberUids = project.memberUids.toSet(),
            talents = availableTalents,
            onDismiss = { showInviteMemberDialog = false },
            onInvite = { talent, role, msg ->
                onInviteMember(talent, role, msg)
                showInviteMemberDialog = false
            }
        )
    }
}

@Composable
private fun WorkspaceTaskCard(
    task: CollaborationTask,
    canDelete: Boolean,
    onChangeStatus: (TaskStatus) -> Unit,
    onOpenComments: () -> Unit,
    onDelete: () -> Unit
) {
    val priority = task.parsedPriority()
    val status = task.parsedStatus()
    val priorityColor = when (priority) {
        TaskPriority.URGENT -> RoseError
        TaskPriority.HIGH -> GoldAmber
        TaskPriority.MEDIUM -> CobaltBlue
        TaskPriority.LOW -> EmeraldSuccess
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().testTag("workspace_task_${task.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(6.dp), color = priorityColor.copy(alpha = 0.14f)) {
                        Text(
                            priority.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = priorityColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = task.assigneeName ?: "Unassigned",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete task", tint = RoseError, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Text(task.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (task.description.isNotBlank()) {
                Text(task.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(TaskStatus.entries) { candidate ->
                    FilterChip(
                        selected = status == candidate,
                        onClick = { if (status != candidate) onChangeStatus(candidate) },
                        label = { Text(candidate.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Due: ${MediaAttachmentHelper.formatDueDate(task.dueAtEpochMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onOpenComments,
                    modifier = Modifier.heightIn(min = 40.dp)
                ) {
                    Icon(Icons.Filled.Comment, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Comments (${task.commentCount})")
                }
            }
        }
    }
}

@Composable
private fun CreateTaskDialog(
    project: CollaborationProject,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (
        title: String,
        description: String,
        assigneeUid: String?,
        assigneeName: String?,
        priority: TaskPriority,
        dueAtEpochMs: Long?,
        attachmentName: String?
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(TaskPriority.MEDIUM) }
    var assigneeUid by remember { mutableStateOf<String?>(project.memberUids.firstOrNull()) }
    var dueDaysOffset by remember { mutableStateOf<Int?>(7) }
    var attachmentName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Task", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Priority", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(TaskPriority.entries) { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.label) }
                        )
                    }
                }
                Text("Assign To", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(project.memberUids) { uid ->
                        val name = project.memberNames[uid] ?: "Member"
                        FilterChip(
                            selected = assigneeUid == uid,
                            onClick = { assigneeUid = uid },
                            label = { Text(name) }
                        )
                    }
                }
                Text("Deadline", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf(null to "None", 1 to "Tomorrow", 3 to "3 Days", 7 to "1 Week", 14 to "2 Weeks")) { (days, label) ->
                        FilterChip(
                            selected = dueDaysOffset == days,
                            onClick = { dueDaysOffset = days },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = attachmentName,
                    onValueChange = { attachmentName = it },
                    label = { Text("Attachment Ref (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val dueMs = dueDaysOffset?.let { System.currentTimeMillis() + it * 86_400_000L }
                    val assigneeName = assigneeUid?.let { project.memberNames[it] }
                    onCreate(title, description, assigneeUid, assigneeName, priority, dueMs, attachmentName.takeIf { it.isNotBlank() })
                },
                enabled = title.trim().length >= 2 && !isBusy,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Create Task")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskCommentsSheet(
    task: CollaborationTask,
    comments: List<TaskComment>,
    onDismiss: () -> Unit,
    onSendComment: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var commentDraft by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (comments.isEmpty()) {
                    item {
                        Text("No comments on this task yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    items(comments, key = { it.id }) { comment ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(comment.authorName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        MediaAttachmentHelper.formatConversationTime(comment.createdAt),
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val commentInteractionSource = remember { MutableInteractionSource() }
                val commentFocused by commentInteractionSource.collectIsFocusedAsState()
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(
                        width = if (commentFocused) 1.5.dp else 1.dp,
                        color = if (commentFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (commentDraft.isEmpty()) {
                            Text(
                                text = "Add a task comment...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = commentDraft,
                            onValueChange = { commentDraft = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            interactionSource = commentInteractionSource,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Button(
                    onClick = {
                        onSendComment(commentDraft)
                        commentDraft = ""
                    },
                    enabled = commentDraft.isNotBlank(),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Post comment", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun AddProjectLinkDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String, notes: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share Private Reference", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("File / Spec Name *") }, singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Link URL (Figma, GitHub, Drive)") }, singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (Optional)") }, maxLines = 3)
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name, url, notes.takeIf { it.isNotBlank() }) },
                enabled = name.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Share Privately")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun InviteProjectMemberDialog(
    existingMemberUids: Set<String>,
    talents: List<BackendTalent>,
    onDismiss: () -> Unit,
    onInvite: (BackendTalent, String, String) -> Unit
) {
    val candidates = remember(talents, existingMemberUids) {
        talents.filter {
            !it.id.startsWith("t_") &&
                !it.id.startsWith("seed_") &&
                it.id !in existingMemberUids
        }
    }
    var selected by remember { mutableStateOf(candidates.firstOrNull()) }
    var role by remember { mutableStateOf("Co-Founder") }
    var note by remember { mutableStateOf("Join our IDEA2IMPACT collaboration workspace!") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite Collaborator", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (candidates.isEmpty()) {
                    Text(
                        "No other signed-in collaborator accounts are currently discoverable. Signed-in IDEA2IMPACT builders appear here automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(candidates, key = { it.id }) { c ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected?.id == c.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth().clickable { selected = c }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(c.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(c.role, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    OutlinedTextField(value = role, onValueChange = { role = it }, label = { Text("Proposed Role") }, singleLine = true)
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Message") }, maxLines = 3)
                }
            }
        },
        confirmButton = {
            if (candidates.isNotEmpty()) {
                Button(
                    onClick = { selected?.let { onInvite(it, role, note) } },
                    enabled = selected != null,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Send Invite")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Close")
            }
        }
    )
}
