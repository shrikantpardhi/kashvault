package com.kashvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.kashvault.ui.components.KashCard
import com.kashvault.ui.navigation.KashVaultScaffold
import com.kashvault.ui.theme.ScreenContentPadding
import com.kashvault.ui.theme.ScreenGap

object PrivacyPolicyScreen : Screen {
    override val key = "privacy-policy"

    @Composable
    override fun Content() {
        KashVaultScaffold(currentScreen = this, title = "Privacy Policy & Safety") { modifier ->
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = ScreenContentPadding,
                verticalArrangement = Arrangement.spacedBy(ScreenGap),
            ) {
                item {
                    PrivacyHeroCard()
                }

                item {
                    PrivacySectionCard(
                        icon = Icons.Default.CloudOff,
                        title = "Local Storage",
                        description = "Financial records and settings are stored in a standard SQLite database in this app's private storage. KashVault does not encrypt the database. The app PIN gates access through the app but does not encrypt the database or backups.",
                    )
                }

                item {
                    PrivacySectionCard(
                        icon = Icons.Default.Lock,
                        title = "PIN & Data Protection",
                        description = "The app PIN is an access gate, not encryption. Exported and Google Drive database backups are ordinary SQLite files and are not encrypted by KashVault. Protect exported files as sensitive financial data.",
                    )
                }

                item {
                    PrivacySectionCard(
                        icon = Icons.Default.Key,
                        title = "Gemini AI & API Key Privacy",
                        description = "Gemini requests go directly to Google. Magic Log sends the text you enter and available category/account labels. The advisor sends your question and summary totals for net worth, liquidity, investments, loans, emergency savings, lending, and expense count. Forecasting sends cash-flow totals. The API key is stored in the local, unencrypted database.",
                    )
                }

                item {
                    PrivacySectionCard(
                        icon = Icons.Default.Shield,
                        title = "SMS & Notification Processing",
                        description = "Notification text is processed locally when you enable notification access. SMS is scanned only when you request a scan and grant READ_SMS; there is no background SMS receiver. Pasted message text is also processed locally.",
                    )
                }

                item {
                    PrivacySectionCard(
                        icon = Icons.Default.CheckCircle,
                        title = "Google Sign-In & Drive (Optional)",
                        description = "Sign-in and Drive requests are sent directly to Google. Drive backups are unencrypted copies of the SQLite database in your account's appDataFolder. Google handles these services under its own terms.",
                    )
                }

                item {
                    PrivacySectionCard(
                        icon = Icons.Default.DeleteForever,
                        title = "User Data Control & Deletion Rights",
                        description = "Local data remains until you reset the vault, clear app data, or uninstall. Exported files remain where you save or share them. Google Drive backups remain until you delete them.",
                    )
                }

                item {
                    PrivacySectionCard(
                        icon = Icons.Default.Info,
                        title = "No Developer Backend",
                        description = "KashVault has no developer-operated backend, advertising SDK, or analytics SDK. Optional Google and Gemini services receive only the requests described above.",
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyHeroCard() {
    KashCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Your Data, Clearly Explained",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "KashVault stores your vault locally, but does not encrypt its database or backups. Review what optional Google services receive before using them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PrivacySectionCard(
    icon: ImageVector,
    title: String,
    description: String,
) {
    KashCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp).size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                )
            }
        }
    }
}
