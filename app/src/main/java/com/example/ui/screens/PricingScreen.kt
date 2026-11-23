package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.TierDiamond3D
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GoldAmber
import com.example.util.LocalHapticFeedbackManager

private data class PricingTier(
    val name: String,
    val price: String,
    val features: List<String>,
    val accent: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricingScreen(
    onBack: () -> Unit
) {
    val tiers = listOf(
        PricingTier(
            name = "FREE",
            price = "$0",
            features = listOf(
                "Browse innovations",
                "Basic profile",
                "Save opportunities",
                "Community access"
            ),
            accent = EmeraldSuccess
        ),
        PricingTier(
            name = "CREATOR PRO",
            price = "$12/mo",
            features = listOf(
                "Enhanced creator profile",
                "Advanced listing insights",
                "Featured eligibility",
                "Creator tools"
            ),
            accent = MaterialTheme.colorScheme.primary
        ),
        PricingTier(
            name = "COMPANY",
            price = "$29/mo",
            features = listOf(
                "Company profile",
                "Advanced discovery",
                "Team workspace",
                "Collaboration opportunities"
            ),
            accent = MaterialTheme.colorScheme.secondary
        ),
        PricingTier(
            name = "INVESTOR PRO",
            price = "$39/mo",
            features = listOf(
                "Advanced opportunity discovery",
                "Saved pipeline",
                "Enhanced matching",
                "Creator connections"
            ),
            accent = GoldAmber
        )
    )

    val haptics = LocalHapticFeedbackManager.current
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Plans & Pricing") },
            navigationIcon = {
                IconButton(
                    onClick = {
                        haptics.tap()
                        onBack()
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "pricing_3d_diamond") {
                TierDiamond3D()
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "SIMPLE PLANS. BIG POSSIBILITIES.",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Find your way to what’s next.",
                        style = MaterialTheme.typography.displayMedium
                    )
                }
            }

            items(tiers) { tier ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, tier.accent.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(tier.name, style = MaterialTheme.typography.titleLarge)
                            Surface(
                                color = tier.accent.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = tier.price,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = tier.accent,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(2.dp))
                        tier.features.forEach { feat ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = tier.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(feat, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
