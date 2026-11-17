package com.kashvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kashvault.ui.rememberSingleClick
import com.kashvault.ui.components.BrandMark
import com.kashvault.ui.components.LoadingScreen
import com.kashvault.ui.components.MarkdownText
import com.kashvault.ui.navigation.KashVaultScaffold
import com.kashvault.ui.navigation.TabsScreen
import com.kashvault.util.needsFirstRunSetup
import com.kashvault.util.predefinedAdvisorAsks

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet

object AdvisorScreen : Screen {
    override val key = "advisor"

    @Composable
    override fun Content() {
        KashVaultScaffold(currentScreen = this, title = "AI") { AdvisorPane(it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdvisorPane(modifier: Modifier = Modifier) {
        val vm = LocalFinanceViewModel.current
        val state by vm.state.collectAsState()
        val isLoading by vm.isLoading.collectAsState()
        val messages by vm.advisorMessages.collectAsState()
        val asking by vm.isAskingAdvisor.collectAsState()

        var message by remember { mutableStateOf("") }
        var showPromptsSheet by remember { mutableStateOf(false) }
        var showGeminiDisclosure by remember { mutableStateOf(false) }
        var pendingGeminiQuestion by remember { mutableStateOf<String?>(null) }
        val listState = rememberLazyListState()
        val clicks = rememberSingleClick()

        if (isLoading) {
            LoadingScreen()
            return
        }

        fun requestAdvisor(text: String) {
            if (state.userProfile?.geminiApiKey.isNullOrBlank()) {
                vm.askAdvisor(text)
                message = ""
            } else {
                pendingGeminiQuestion = text
                showGeminiDisclosure = true
            }
        }

        fun send() {
            clicks("ask") {
                val text = message.trim()
                if (text.isBlank() || asking) return@clicks
                requestAdvisor(text)
            }
        }

        LaunchedEffect(messages.size, asking) {
            val last = messages.lastIndex + if (asking) 1 else 0
            if (last >= 0) listState.animateScrollToItem(last)
        }

            Column(modifier = modifier.fillMaxSize().imePadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SuggestionChip(
                        onClick = { showPromptsSheet = true },
                        label = { Text("⚡ Select Prompt") },
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(messages) { chat ->
                        val mine = chat.role == "user"
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth(if (mine) 0.86f else 0.92f)
                                    .align(if (mine) Alignment.CenterEnd else Alignment.CenterStart),
                                shape = RoundedCornerShape(
                                    topStart = 20.dp,
                                    topEnd = 20.dp,
                                    bottomStart = if (mine) 20.dp else 6.dp,
                                    bottomEnd = if (mine) 6.dp else 20.dp,
                                ),
                                color = if (mine) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            ) {
                                if (mine) {
                                    Text(
                                        chat.text,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                } else {
                                    MarkdownText(
                                        chat.text,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                    if (asking) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("Thinking…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                val canSend = message.isNotBlank() && !asking
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    tonalElevation = 3.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(28.dp))
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = message,
                            onValueChange = { message = it },
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                                .heightIn(min = 36.dp, max = 120.dp),
                            enabled = !asking,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { send() }),
                            maxLines = 5,
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 36.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (message.isEmpty()) {
                                        Text(
                                            "Ask anything",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (canSend) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                                )
                                .clickable(enabled = canSend, onClick = { send() }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (canSend) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .size(18.dp)
                                    .offset(x = 1.5.dp),
                            )
                        }
                    }
                }

                if (showPromptsSheet) {
                    ModalBottomSheet(onDismissRequest = { showPromptsSheet = false }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                "Select a Prompt",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Choose a question or analysis topic to ask the AI Copilot:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.heightIn(max = 420.dp),
                            ) {
                                items(predefinedAdvisorAsks, key = { it.title }) { ask ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable {
                                                showPromptsSheet = false
                                                requestAdvisor(ask.prompt)
                                            },
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            0.5.dp,
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                        ),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Text(ask.icon, style = MaterialTheme.typography.titleMedium)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    ask.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                                Text(
                                                    ask.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            Text(
                                                "Ask ›",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }

                                        if (showGeminiDisclosure) {
                                    AlertDialog(
                                        onDismissRequest = {
                                            showGeminiDisclosure = false
                                            pendingGeminiQuestion = null
                                        },
                                        title = { Text("Share this request with Google Gemini?") },
                                        text = {
                                            Text(
                                                "Google receives your question and a vault summary with net worth, liquidity, investments, loan liability, emergency-fund balance, lending totals, and monthly expense count. The full transaction ledger is not sent.",
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        },
                                        confirmButton = {
                                            TextButton(onClick = {
                                                pendingGeminiQuestion?.let(vm::askAdvisor)
                                                pendingGeminiQuestion = null
                                                showGeminiDisclosure = false
                                                message = ""
                                            }) {
                                                Text("Continue to Gemini")
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = {
                                                showGeminiDisclosure = false
                                                pendingGeminiQuestion = null
                                            }) {
                                                Text("Cancel")
                                            }
                                        },
                                    )
                                        }
                                }
                            }
                        }
                    }
                }
            }
}

object CategoriesScreen : Screen {
    override val key = "categories-alias"

    @Composable
    override fun Content() {
        CategoryStudioScreen.Content()
    }
}
