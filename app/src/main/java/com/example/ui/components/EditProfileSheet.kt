package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AuthUser
import com.example.data.model.TalentProfile
import com.example.data.model.UserProfile
import com.example.util.LocalHapticFeedbackManager

private val PROFILE_ROLES = listOf(
    "Creator",
    "Builder",
    "Investor",
    "Patent Professional",
    "Company",
    "Customer / Buyer"
)

private val SUGGESTED_PROFILE_SKILLS = listOf(
    "AI / ML",
    "Product Design",
    "Full-Stack",
    "Hardware / IoT",
    "Fundraising",
    "Go-to-Market",
    "Mobile Dev",
    "Patent Strategy"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditProfileSheet(
    currentUser: AuthUser?,
    firestoreProfile: UserProfile?,
    talentProfile: TalentProfile?,
    isBusy: Boolean,
    errorMessage: String? = null,
    onClearError: () -> Unit = {},
    onSave: (
        name: String,
        role: String,
        headline: String,
        bio: String,
        skills: List<String>,
        location: String,
        isPublic: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalHapticFeedbackManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val initialName = firestoreProfile?.name?.ifBlank { null }
        ?: talentProfile?.name?.ifBlank { null }
        ?: currentUser?.name.orEmpty()
    val initialRole = firestoreProfile?.role?.takeIf { it in PROFILE_ROLES }
        ?: talentProfile?.role?.takeIf { it in PROFILE_ROLES }
        ?: currentUser?.role?.takeIf { it in PROFILE_ROLES }
        ?: "Creator"
    val initialHeadline = firestoreProfile?.headline?.ifBlank { null }
        ?: talentProfile?.headline.orEmpty()
    val initialBio = firestoreProfile?.bio?.ifBlank { null }
        ?: talentProfile?.bio.orEmpty()
    val initialLocation = firestoreProfile?.location?.ifBlank { null }
        ?: talentProfile?.location?.ifBlank { null }
        ?: ""
    val initialSkills = (firestoreProfile?.skills?.takeIf { it.isNotEmpty() }
        ?: talentProfile?.skills.orEmpty()).joinToString(", ")
    val initialIsPublic = talentProfile?.isPublic ?: true
    val emailDisplay = firestoreProfile?.email?.ifBlank { null } ?: currentUser?.email.orEmpty()

    var name by rememberSaveable { mutableStateOf(initialName) }
    var role by rememberSaveable { mutableStateOf(initialRole) }
    var headline by rememberSaveable { mutableStateOf(initialHeadline) }
    var location by rememberSaveable { mutableStateOf(initialLocation) }
    var skillsCsv by rememberSaveable { mutableStateOf(initialSkills) }
    var bio by rememberSaveable { mutableStateOf(initialBio) }
    var isPublic by rememberSaveable { mutableStateOf(initialIsPublic) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("edit_profile_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Edit Your Profile",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (emailDisplay.isNotBlank()) {
                            Text(
                                text = emailDisplay,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                IconButton(
                    onClick = {
                        haptics.tap()
                        onDismiss()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close edit profile")
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        onClearError()
                    },
                    label = { Text("Full Name *") },
                    placeholder = { Text("Enter your name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_name_input")
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Your Role in IDEA2IMPACT *",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        PROFILE_ROLES.forEach { option ->
                            FilterChip(
                                selected = role == option,
                                onClick = {
                                    haptics.tap()
                                    role = option
                                    onClearError()
                                },
                                label = { Text(option) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = headline,
                    onValueChange = {
                        headline = it
                        onClearError()
                    },
                    label = { Text("Headline / Title") },
                    placeholder = { Text("e.g. Hardware Founder, AI Engineer, Seed Investor") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_headline_input")
                )

                OutlinedTextField(
                    value = location,
                    onValueChange = {
                        location = it
                        onClearError()
                    },
                    label = { Text("Location (City, Country)") },
                    placeholder = { Text("e.g. Bengaluru, India") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_location_input")
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = skillsCsv,
                        onValueChange = {
                            skillsCsv = it
                            onClearError()
                        },
                        label = { Text("Skills & Expertise (comma-separated)") },
                        placeholder = { Text("e.g. Kotlin, Product Design, Fundraising") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_profile_skills_input")
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val existingTokens = remember(skillsCsv) {
                            skillsCsv.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
                        }
                        SUGGESTED_PROFILE_SKILLS.filter { it.lowercase() !in existingTokens }.take(6).forEach { skill ->
                            SuggestionChip(
                                onClick = {
                                    haptics.tap()
                                    skillsCsv = if (skillsCsv.isBlank()) skill
                                    else "${skillsCsv.trimEnd(',', ' ')}, $skill"
                                },
                                icon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                label = { Text(skill, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = bio,
                    onValueChange = {
                        bio = it
                        onClearError()
                    },
                    label = { Text("About You & Collaboration Goals") },
                    placeholder = { Text("Share what you're building, investing in, or looking to collaborate on...") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_bio_input")
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(
                                text = "Public Collaborator Profile",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Allow founders, builders, and investors to discover you in Talents.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isPublic,
                            onCheckedChange = {
                                haptics.toggle(it)
                                isPublic = it
                            }
                        )
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            Button(
                onClick = {
                    haptics.success()
                    val parsedSkills = skillsCsv
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                    onSave(
                        name.trim(),
                        role,
                        headline.trim(),
                        bio.trim(),
                        parsedSkills,
                        location.trim().ifBlank { "Global" },
                        isPublic
                    )
                },
                enabled = !isBusy && name.trim().isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("edit_profile_save_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Save Profile")
            }
        }
    }
}
