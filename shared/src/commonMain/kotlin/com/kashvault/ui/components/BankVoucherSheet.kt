package com.kashvault.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kashvault.ui.theme.NeoAmber
import com.kashvault.ui.theme.NeoCardBg
import com.kashvault.ui.theme.NeoCardBorder
import com.kashvault.ui.theme.NeoCyan
import com.kashvault.ui.theme.NeoCyanCardGradient
import com.kashvault.ui.theme.NeoDarkBg
import com.kashvault.ui.theme.NeoEmerald
import com.kashvault.ui.theme.NeoEmeraldGlow
import com.kashvault.ui.theme.NeoFintechMeshGradient
import com.kashvault.ui.theme.NeoIndigo
import com.kashvault.util.TransactionReceipt
import com.kashvault.util.formatINR

/**
 * Transaction receipt generated from the local ledger.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankVoucherSheet(
    voucher: TransactionReceipt,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(NeoEmerald.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Transaction record",
                            tint = NeoEmerald,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "TRANSACTION RECEIPT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = NeoEmerald,
                            letterSpacing = 1.2.sp,
                        )
                        Text(
                            text = voucher.voucherNumber,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close voucher")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Certificate Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Bank Header Banner
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "KASHVAULT INSTITUTIONAL",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Electronic Transaction Certificate",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(NeoEmerald.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                    contentDescription = "Local record",
                                    tint = NeoEmerald,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "LOCAL RECORD",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NeoEmerald,
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Amount Display
                    Text(
                        text = voucher.typeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatINR(voucher.amount),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    PerforatedDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // Transaction Metadata Rows
                    VoucherFieldRow(label = "Issuing Account", value = voucher.originAccountName)
                    voucher.destinationAccountName?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        VoucherFieldRow(label = "Beneficiary Account", value = it)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    VoucherFieldRow(label = "Category / Ref", value = voucher.counterpartyOrCategory)
                    Spacer(modifier = Modifier.height(8.dp))
                    VoucherFieldRow(label = "Date & Time", value = voucher.issuedAt)
                    Spacer(modifier = Modifier.height(8.dp))
                    VoucherFieldRow(label = "Reference", value = voucher.referenceNumber, isMonospace = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    VoucherFieldRow(label = "Memo / Notes", value = voucher.note)

                    Spacer(modifier = Modifier.height(20.dp))

                    // Digital Verification Stamp Display
                    DigitalVerificationSeal(
                        modifier = Modifier
                            .size(140.dp)
                            .rotate(-5f),
                        voucherNumber = voucher.voucherNumber,
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    PerforatedDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // Cryptographic Hash Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Hash",
                                    tint = NeoCyan,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "SHA-256 RECORD FINGERPRINT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NeoCyan,
                                    fontSize = 10.sp,
                                )
                            }
                            Text(
                                text = "INFORMATIONAL ONLY",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = voucher.sha256Hash,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Copy & Export Action
            PrimaryButton(
                text = if (copied) "Record Copied to Clipboard!" else "Copy Transaction Record",
                onClick = {
                    val proofText = """
                        === KASHVAULT TRANSACTION RECORD ===
                        Voucher No: ${voucher.voucherNumber}
                        Reference: ${voucher.referenceNumber}
                        Type: ${voucher.typeLabel}
                        Amount: ${formatINR(voucher.amount)}
                        Origin: ${voucher.originAccountName}
                        Date: ${voucher.issuedAt}
                        Memo: ${voucher.note}
                        SHA-256 Fingerprint: ${voucher.sha256Hash}
                        Note: This fingerprint does not verify authenticity or detect edits.
                    """.trimIndent()
                    clipboardManager.setText(AnnotatedString(proofText))
                    copied = true
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun VoucherFieldRow(
    label: String,
    value: String,
    isMonospace: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f),
        )
    }
}

/**
 * Decorative marker for a locally generated transaction record.
 */
@Composable
fun DigitalVerificationSeal(
    modifier: Modifier = Modifier,
    voucherNumber: String,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokeWidth = 2.5.dp.toPx()
            val dashedStroke = Stroke(
                width = strokeWidth,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f),
            )
            val solidStroke = Stroke(width = 1.5.dp.toPx())

            // Outer dashed circle
            drawCircle(
                color = NeoEmerald,
                radius = size.minDimension / 2f - strokeWidth,
                style = dashedStroke,
            )

            // Inner solid circle
            drawCircle(
                color = NeoEmerald.copy(alpha = 0.6f),
                radius = size.minDimension / 2f - 8.dp.toPx(),
                style = solidStroke,
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = NeoEmerald,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "LOCAL",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                color = NeoEmerald,
                letterSpacing = 1.2.sp,
            )
            Text(
                text = "NOT BANK VERIFIED",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp,
                color = NeoEmerald.copy(alpha = 0.85f),
                letterSpacing = 0.8.sp,
            )
            Text(
                text = voucherNumber.takeLast(8),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                color = NeoEmerald,
            )
        }
    }
}

/**
 * Perforated ticket-like divider for transaction receipts.
 */
@Composable
fun PerforatedDivider(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp),
    ) {
        drawLine(
            color = Color.Gray.copy(alpha = 0.35f),
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
        )
    }
}
