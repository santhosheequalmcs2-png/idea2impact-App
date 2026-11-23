package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.BackendInnovation
import com.example.data.model.DigitalNdaRecord
import com.example.data.model.ExactCategories
import com.example.data.model.PitchDeckResult
import com.example.data.model.UiState
import com.example.ui.theme.EmeraldSuccess
import com.example.util.LocalHapticFeedbackManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollaborationProposalModal(
    innovationTitle: String,
    isBusy: Boolean,
    errorMessage: String? = null,
    onClearError: () -> Unit = {},
    onSubmit: (type: String, amount: String, message: String, contactInfo: String) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalHapticFeedbackManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var expanded by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(ExactCategories.proposalTypes.first()) }
    var amount by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var contactInfo by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Handshake, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Collaboration Proposal", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = innovationTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = {
                        haptics.tap()
                        onDismiss()
                    }
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close proposal modal")
                }
            }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = {
                    haptics.tap()
                    expanded = !expanded
                }
            ) {
                OutlinedTextField(
                    value = selectedType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Proposal Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .testTag("proposal_type_dropdown")
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    ExactCategories.proposalTypes.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                haptics.tap()
                                selectedType = option
                                expanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount ($)") },
                placeholder = { Text("e.g. $50,000 or 12% Equity") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth().testTag("proposal_amount_input")
            )

            OutlinedTextField(
                value = message,
                onValueChange = {
                    message = it
                    onClearError()
                },
                label = { Text("Proposal Message *") },
                placeholder = { Text("Share your thesis, relevant engineering capabilities, or strategic value...") },
                supportingText = if (message.isNotEmpty() && message.trim().length < 5) {
                    { Text("Please enter at least 5 characters.") }
                } else null,
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().testTag("proposal_message_input")
            )

            OutlinedTextField(
                value = contactInfo,
                onValueChange = {
                    contactInfo = it
                    onClearError()
                },
                label = { Text("Contact Info (Email / LinkedIn / Phone)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("proposal_contact_input")
            )

            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("proposal_error_banner")
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick = {
                    onClearError()
                    haptics.success()
                    onSubmit(selectedType, amount, message, contactInfo)
                },
                enabled = !isBusy && message.trim().length >= 5,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("proposal_submit_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Submit Proposal")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MutualNdaModal(
    innovation: BackendInnovation,
    defaultSignerName: String,
    existingRecord: DigitalNdaRecord?,
    onSign: (signerName: String, signerOrg: String) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalHapticFeedbackManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var signerName by remember { mutableStateOf(defaultSignerName) }
    var signerOrg by remember { mutableStateOf("") }
    var agreed by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Sign Digital Mutual NDA", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Zero-knowledge encrypted deal room",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = {
                        haptics.tap()
                        onDismiss()
                    }
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close NDA modal")
                }
            }

            if (existingRecord != null) {
                Surface(
                    color = EmeraldSuccess.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldSuccess)
                            Text(
                                "Mutual NDA Executed & Verified",
                                style = MaterialTheme.typography.titleSmall,
                                color = EmeraldSuccess
                            )
                        }
                        Text("Signer: ${existingRecord.signerName} (${existingRecord.signerOrg.ifBlank { "Independent" }})", style = MaterialTheme.typography.bodySmall)
                        Text("Signature ID: ${existingRecord.signatureHash}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("Timestamp: ${existingRecord.signedAt}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(
                    onClick = {
                        haptics.tap()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text("Done")
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "MUTUAL NON-DISCLOSURE AGREEMENT (IDEA2IMPACT)\n\n" +
                            "1. Both parties agree to protect all CAD blueprints, source repositories, patent claims, and financial projections related to \"${innovation.title}\".\n" +
                            "2. Confidential disclosures shall be evaluated strictly for investment, licensing, or co-building discussions.\n" +
                            "3. Cryptographic signature confirmation is timestamped and bound to this deal room session.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                OutlinedTextField(
                    value = signerName,
                    onValueChange = { signerName = it },
                    label = { Text("Authorized Signer Full Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("nda_signer_name_input")
                )
                OutlinedTextField(
                    value = signerOrg,
                    onValueChange = { signerOrg = it },
                    label = { Text("Organization / Venture Fund (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = agreed,
                        onCheckedChange = {
                            haptics.toggle(it)
                            agreed = it
                        },
                        modifier = Modifier.testTag("nda_agree_checkbox")
                    )
                    Text(
                        "I legally accept the Mutual Non-Disclosure Agreement terms.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                Button(
                    onClick = {
                        haptics.success()
                        onSign(signerName, signerOrg)
                    },
                    enabled = agreed && signerName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("nda_confirm_button")
                ) {
                    Text("Confirm Digital Signature")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PitchDeckModal(
    state: UiState<PitchDeckResult>,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalHapticFeedbackManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("5-Slide AI Investor Pitch Deck", style = MaterialTheme.typography.titleMedium)
                }
                IconButton(
                    onClick = {
                        haptics.tap()
                        onDismiss()
                    }
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close pitch deck modal")
                }
            }

            when (state) {
                UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                "Generating 5-slide executive investor summary...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is UiState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            Button(
                                onClick = {
                                    haptics.tap()
                                    onRetry()
                                }
                            ) { Text("Retry") }
                        }
                    }
                }
                is UiState.Success -> {
                    Text(
                        text = state.data.innovationTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    state.data.slides.forEach { slide ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "SLIDE 0${slide.slideNumber} · ${slide.subtitle.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(slide.title, style = MaterialTheme.typography.titleSmall)
                                slide.bullets.forEach { bullet ->
                                    Text(
                                        text = "• $bullet",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
