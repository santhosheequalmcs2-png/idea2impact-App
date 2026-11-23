package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BackendInnovation
import com.example.data.model.BackendTalent
import com.example.data.model.Conversation
import com.example.ui.theme.RoseError

@Composable
fun NewMessageOrGroupDialog(
    talents: List<BackendTalent>,
    onDismiss: () -> Unit,
    onStartDirect: (BackendTalent) -> Unit,
    onCreateGroup: (title: String, members: List<BackendTalent>) -> Unit
) {
    var isGroupMode by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var groupTitle by remember { mutableStateOf("") }
    val selectedMembers = remember { mutableStateListOf<BackendTalent>() }

    val filteredTalents = remember(talents, search) {
        val q = search.trim()
        if (q.isBlank()) talents
        else talents.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.role.contains(q, ignoreCase = true) ||
                it.skills.any { s -> s.contains(q, ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isGroupMode) "Create Group Chat" else "New Message", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isGroupMode,
                        onClick = { isGroupMode = false },
                        label = { Text("Direct Chat") }
                    )
                    FilterChip(
                        selected = isGroupMode,
                        onClick = { isGroupMode = true },
                        label = { Text("Group Chat") }
                    )
                }

                if (isGroupMode) {
                    OutlinedTextField(
                        value = groupTitle,
                        onValueChange = { groupTitle = it },
                        label = { Text("Group Name *") },
                        placeholder = { Text("e.g., Robotics Founding Team") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val peopleSearchInteractionSource = remember { MutableInteractionSource() }
                val peopleSearchFocused by peopleSearchInteractionSource.collectIsFocusedAsState()
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(
                        width = if (peopleSearchFocused) 1.5.dp else 1.dp,
                        color = if (peopleSearchFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
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
                            if (search.isEmpty()) {
                                Text(
                                    text = "Search creators, builders, investors...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = search,
                                onValueChange = { search = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                interactionSource = peopleSearchInteractionSource,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredTalents, key = { it.id }) { talent ->
                        val isPicked = selectedMembers.any { it.id == talent.id }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isPicked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(
                                1.dp,
                                if (isPicked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isGroupMode) {
                                        onStartDirect(talent)
                                    } else {
                                        if (isPicked) selectedMembers.removeAll { it.id == talent.id }
                                        else selectedMembers.add(talent)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isGroupMode) {
                                    Checkbox(
                                        checked = isPicked,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                if (!isPicked) selectedMembers.add(talent)
                                            } else {
                                                selectedMembers.removeAll { it.id == talent.id }
                                            }
                                        }
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(talent.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${talent.role} · ${talent.location}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isGroupMode) {
                Button(
                    onClick = { onCreateGroup(groupTitle.trim(), selectedMembers.toList()) },
                    enabled = groupTitle.trim().isNotBlank() && selectedMembers.isNotEmpty(),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Create (${selectedMembers.size})")
                }
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
fun ShareInnovationInChatDialog(
    innovations: List<BackendInnovation>,
    onDismiss: () -> Unit,
    onSelectInnovation: (BackendInnovation) -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(innovations, search) {
        val q = search.trim()
        if (q.isBlank()) innovations
        else innovations.filter {
            it.title.contains(q, ignoreCase = true) || it.category.contains(q, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share Innovation Listing", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search innovations...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered.take(20), key = { it.id }) { inn ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectInnovation(inn) }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    inn.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${inn.category} · ${inn.stage}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ReportUserDialog(
    peerName: String,
    onDismiss: () -> Unit,
    onSubmitReport: (reason: String, details: String) -> Unit
) {
    val reasons = listOf("Spam or scam", "Harassment", "Confidential IP leak risk", "Impersonation")
    var selectedReason by remember { mutableStateOf(reasons.first()) }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report $peerName", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reasons.forEach { reason ->
                    FilterChip(
                        selected = selectedReason == reason,
                        onClick = { selectedReason = reason },
                        label = { Text(reason) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text("Additional details (optional)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmitReport(selectedReason, details) },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Submit Report")
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
fun GroupMembersDialog(
    conversation: Conversation,
    myUid: String,
    availableTalents: List<BackendTalent>,
    onDismiss: () -> Unit,
    onAddMember: (BackendTalent) -> Unit,
    onRemoveMember: (String) -> Unit
) {
    val isAdmin = myUid in conversation.adminUids
    val addable = remember(availableTalents, conversation.memberUids) {
        availableTalents.filter { it.id !in conversation.memberUids }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Participants (${conversation.memberSummaries.size})", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(conversation.memberSummaries.entries.toList(), key = { it.key }) { (key, summary) ->
                    val name = (summary["name"] as? String) ?: "Member"
                    val role = (summary["role"] as? String) ?: "Collaborator"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (key == myUid) "$name (You)" else name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(role, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isAdmin && key in conversation.memberUids && key != conversation.createdBy) {
                            IconButton(onClick = { onRemoveMember(key) }) {
                                Icon(Icons.Filled.PersonRemove, contentDescription = "Remove $name", tint = RoseError)
                            }
                        }
                    }
                }
                if (isAdmin && addable.isNotEmpty()) {
                    item {
                        Text(
                            "Add Collaborator",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    items(addable.take(8), key = { "add_${it.id}" }) { candidate ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth().clickable { onAddMember(candidate) }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(candidate.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                    Text(candidate.role, style = MaterialTheme.typography.labelSmall)
                                }
                                Icon(Icons.Filled.GroupAdd, contentDescription = "Add ${candidate.name}", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Done")
            }
        }
    )
}
