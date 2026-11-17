package com.kashvault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kashvault.ui.components.KashCard
import com.kashvault.ui.components.LiquidityChart
import com.kashvault.ui.components.MarkdownText
import com.kashvault.util.buildHorizonSeries
import com.kashvault.util.formatINR

@Composable
fun LiquidityForecastSection() {
    val vm = LocalFinanceViewModel.current
    val forecast by vm.liquidityForecast.collectAsState()
    val isForecasting by vm.isForecasting.collectAsState()
    val hasKey = vm.state.collectAsState().value.userProfile?.geminiApiKey?.isNotBlank() == true
    val input = vm.getLiquidityInput()
    val series = buildHorizonSeries(input)
    var showGeminiDisclosure by remember { mutableStateOf(false) }

    KashCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Predictive horizon · 30 days", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Cash ${formatINR(input.currentLiquidity)} · Lien ${formatINR(input.upcomingLien)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Button(
                onClick = {
                    if (hasKey) showGeminiDisclosure = true else vm.runLiquidityForecast(useGemini = false)
                },
                enabled = !isForecasting,
            ) {
                if (isForecasting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Forecast")
                }
            }

            if (showGeminiDisclosure) {
                AlertDialog(
                    onDismissRequest = { showGeminiDisclosure = false },
                    title = { Text("Share cash-flow totals with Google Gemini?") },
                    text = {
                        Text(
                            "Continuing sends your current cash, average monthly spending, estimated monthly income, and upcoming fixed-bill totals to Google Gemini for a forecast.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showGeminiDisclosure = false
                            vm.runLiquidityForecast(useGemini = true)
                        }) {
                            Text("Continue to Gemini")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showGeminiDisclosure = false
                            vm.runLiquidityForecast(useGemini = false)
                        }) {
                            Text("Use local forecast")
                        }
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (!hasKey) {
            Text(
                "Gemini key optional — tap Forecast for a local 30-day projection, or add a key in Profile for AI tips.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        LiquidityChart(points = series)
        forecast?.let { result ->
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Risk: ${result.riskLevel}", fontWeight = FontWeight.SemiBold)
                AssistChip(onClick = {}, enabled = false, label = { Text("${result.runwayDays}d runway") })
            }
            MarkdownText(result.forecast, style = MaterialTheme.typography.bodySmall)
            result.tips.take(3).forEach { tip ->
                MarkdownText("• $tip", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
