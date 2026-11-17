package com.kashvault.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import com.kashvault.ui.theme.FieldHeight
import com.kashvault.ui.theme.FieldHeightMax
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kashvault.domain.Constants
import com.kashvault.domain.PaymentSource
import com.kashvault.domain.Person
import com.kashvault.domain.displayLabel
import com.kashvault.domain.kindLabel
import com.kashvault.ui.theme.ButtonHeight
import com.kashvault.ui.theme.CardRadius
import com.kashvault.ui.theme.vaultPalette
import com.kashvault.util.formatDisplayDate
import com.kashvault.util.formatINR
import com.kashvault.util.formatINRSigned
import com.kashvault.util.localDateFromUtcMillis
import com.kashvault.util.personInitials
import com.kashvault.util.todayLocalDate
import com.kashvault.util.utcMillisFromLocalDate
import com.kashvault.reminder.formatReminderClock
import kashvault.shared.generated.resources.Res
import kashvault.shared.generated.resources.app_icon
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource

@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Int = 32,
    onBrand: Boolean = true,
) {
    Image(
        painter = painterResource(Res.drawable.app_icon),
        contentDescription = "KashVault",
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .then(
                if (onBrand) Modifier.background(Color.White)
                else Modifier,
            ),
        contentScale = ContentScale.Crop,
    )
}

@Composable
fun MoneyText(
    amount: Double,
    modifier: Modifier = Modifier,
    type: String = "neutral",
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    fontWeight: FontWeight? = FontWeight.Medium,
) {
    val palette = vaultPalette()
    val color = when (type) {
        "income" -> palette.income
        "expense" -> palette.spend
        Constants.TYPE_TRANSFER -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = formatINRSigned(amount, type, alwaysShowDecimals = amount % 1.0 != 0.0),
        modifier = modifier,
        style = style,
        fontWeight = fontWeight,
        color = color,
    )
}

@Composable
fun KashCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: androidx.compose.ui.unit.Dp = 14.dp,
    cardElevation: androidx.compose.ui.unit.Dp = 1.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.elevatedCardColors(containerColor = containerColor)
    val elevation = CardDefaults.elevatedCardElevation(defaultElevation = cardElevation, pressedElevation = 3.dp)
    val shape = RoundedCornerShape(CardRadius)
    if (onClick != null) {
        ElevatedCard(
            modifier = modifier.fillMaxWidth(),
            onClick = onClick,
            colors = colors,
            elevation = elevation,
            shape = shape,
        ) {
            Column(modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(contentPadding), content = content)
        }
    } else {
        ElevatedCard(
            modifier = modifier.fillMaxWidth(),
            colors = colors,
            elevation = elevation,
            shape = shape,
        ) {
            Column(modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(contentPadding), content = content)
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(ButtonHeight),
        shape = RoundedCornerShape(CardRadius),
    ) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(ButtonHeight),
        shape = RoundedCornerShape(CardRadius),
    ) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
fun HeroPanel(
    label: String,
    value: String,
    caption: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    KashCard(modifier = modifier, onClick = onClick) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (caption != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(caption, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    caption: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = accent.copy(alpha = 0.14f),
) {
    KashCard(
        modifier = modifier,
        onClick = onClick,
        containerColor = containerColor,
        contentPadding = 12.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accent),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = accent)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (caption != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun LoadingScreen(message: String = "Loading your vault…") {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text(action)
            }
        }
    }
}

@Composable
fun ProgressBar(
    percent: Double,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)),
) {
    val clamped = (percent / 100.0).coerceIn(0.0, 1.0).toFloat()
    LinearProgressIndicator(
        progress = { clamped },
        modifier = modifier,
        color = color,
        trackColor = color.copy(alpha = 0.16f),
    )
}

@Composable
fun ProgressStat(
    title: String,
    caption: String,
    percent: Double,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    KashCard(modifier = modifier, onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text("${percent.toInt().coerceAtMost(999)}%", style = MaterialTheme.typography.labelLarge, color = color)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(10.dp))
        ProgressBar(percent, color)
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    KashCard(modifier = modifier, contentPadding = 20.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    icon ?: "✨",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(actionLabel, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun CategorySpendingBar(
    name: String,
    icon: String,
    amount: Double,
    percent: Double,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(icon.ifBlank { "🏷️" }, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "${percent.toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatINR(amount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        val clamped = (percent / 100.0).coerceIn(0.0, 1.0).toFloat()
        LinearProgressIndicator(
            progress = { clamped },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.14f),
        )
    }
}

@Composable
fun PersonAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Int = 44,
    image: ImageBitmap? = null,
) {
    val palette = listOf(
        Color(0xFF7C3AED),
        Color(0xFFC026D3),
        Color(0xFFB45309),
        Color(0xFF1D4ED8),
        Color(0xFFBE123C),
        Color(0xFF0E7490),
    )
    val color = palette[name.hashCode().and(0x7FFFFFFF) % palette.size]
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = name,
            modifier = modifier
                .size(size.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        return
    }
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            personInitials(name),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size * 0.34f).sp,
        )
    }
}

data class PickerOption(val id: String, val label: String, val caption: String? = null)

@Composable
fun SearchablePickerField(
    label: String,
    selectedLabel: String,
    options: List<PickerOption>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        VaultOutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            readOnly = true,
            enabled = false,
            singleLine = true,
            trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Open") },
            colors = pickerFieldColors(),
        )
        Box(modifier = Modifier.matchParentSize().clickable { open = true })
    }
    if (open) {
        SearchablePickerDialog(
            title = label,
            placeholder = placeholder,
            options = options,
            onSelect = {
                onSelect(it)
                open = false
            },
            onDismiss = { open = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchablePickerDialog(
    title: String,
    placeholder: String,
    options: List<PickerOption>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, options) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) options
        else options.filter {
            it.label.lowercase().contains(needle) || it.caption.orEmpty().lowercase().contains(needle)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .height(460.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(12.dp))
            VaultOutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(placeholder) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                if (filtered.isEmpty()) {
                    item { Text("No matches", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(filtered.distinctBy { it.id }, key = { it.id }) { option ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelect(option.id) }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                    ) {
                        Text(option.label, fontWeight = FontWeight.Medium)
                        if (option.caption != null) {
                            Text(option.caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).height(48.dp)) { Text("Close") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    label: String,
    date: LocalDate,
    onDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        VaultOutlinedTextField(
            value = formatDisplayDate(date),
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            readOnly = true,
            enabled = false,
            singleLine = true,
            colors = pickerFieldColors(),
        )
        Box(modifier = Modifier.matchParentSize().clickable { open = true })
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = utcMillisFromLocalDate(date))
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = state.selectedDateMillis
                        if (millis != null) onDate(localDateFromUtcMillis(millis))
                        open = false
                    },
                ) { Text("Set date") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onDate(todayLocalDate())
                    open = false
                }) { Text("Today") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun PersonAutocompleteField(
    query: String,
    onQuery: (String) -> Unit,
    people: List<Person>,
    selectedPersonId: String?,
    onSelectPerson: (Person) -> Unit,
    onCreateNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val matches = remember(query, people) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) people.take(8)
        else people.filter {
            it.name.lowercase().contains(needle) || it.phone.orEmpty().contains(needle)
        }.take(8)
    }
    val exact = people.any { it.name.equals(query.trim(), ignoreCase = true) }
    val showMenu = focused && (matches.isNotEmpty() || query.isNotBlank())
    Column(modifier = modifier.fillMaxWidth()) {
        VaultOutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            label = { Text("Person") },
            singleLine = true,
            placeholder = { Text("Search or type a name") },
        )
        if (showMenu) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                matches.forEach { row ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectPerson(row)
                                focused = false
                            }
                            .background(
                                if (selectedPersonId == row.id) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                } else {
                                    Color.Transparent
                                },
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(row.name, fontWeight = FontWeight.Medium)
                        if (!row.phone.isNullOrBlank()) {
                            Text(
                                row.phone,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (!exact || selectedPersonId == null) {
                    Text(
                        if (query.trim().isEmpty()) "Create new person" else "Create “${query.trim()}”",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onCreateNew()
                                focused = false
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerField(
    label: String,
    hour: Int,
    minute: Int,
    onTime: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        VaultOutlinedTextField(
            value = formatReminderClock(hour, minute),
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            readOnly = true,
            enabled = false,
            singleLine = true,
            colors = pickerFieldColors(),
        )
        Box(modifier = Modifier.matchParentSize().clickable(enabled = enabled) { open = true })
    }
    if (open) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTime(state.hour, state.minute)
                        open = false
                    },
                ) { Text("Set time") }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = state) },
        )
    }
}

@Composable
fun TransactionRow(
    icon: String,
    title: String,
    subtitle: String,
    amount: Double,
    type: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    chipColor: Color = MaterialTheme.colorScheme.primaryContainer,
    chipLabel: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MerchantMark(name = title, glyph = icon, accent = chipColor, size = 40)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (chipLabel != null) {
                Spacer(modifier = Modifier.height(4.dp))
                SoftChip(chipLabel, container = chipColor)
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MoneyText(amount, type = type, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ItemActionRow(
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    if (onEdit == null && onDelete == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (onEdit != null) {
            TextButton(onClick = onEdit) { Text("Edit") }
        }
        if (onDelete != null) {
            TextButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun FilterRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (id, label) ->
            FilterChip(
                selected = selected == id,
                onClick = { onSelect(id) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        KashCard(contentPadding = 0.dp, content = content)
    }
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    leading: @Composable (() -> Unit)? = null,
    showDivider: Boolean = true,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                leading != null -> {
                    leading()
                    Spacer(modifier = Modifier.size(12.dp))
                }
                icon != null -> {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 62.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
fun SettingsNavRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    leading: @Composable (() -> Unit)? = null,
) {
    KashCard(onClick = onClick) {
        SettingsRow(
            title = title,
            subtitle = subtitle,
            onClick = onClick,
            icon = icon,
            leading = leading,
            showDivider = false,
        )
    }
}

@Composable
fun FilterIconButton(
    activeCount: Int,
    onClick: () -> Unit,
    contentDescription: String = "Filters",
) {
    IconButton(onClick = onClick) {
        BadgedBox(
            badge = {
                if (activeCount > 0) Badge { Text(activeCount.toString()) }
            },
        ) {
            Icon(Icons.Default.FilterList, contentDescription = contentDescription)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onReset: (() -> Unit)? = null,
    title: String = "Filters",
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (onReset != null) {
                    TextButton(onClick = onReset) { Text("Reset") }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

@Composable
fun FilterSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
    )
    Spacer(modifier = Modifier.height(8.dp))
    content()
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
fun PayCardBillDialog(
    cardName: String,
    amount: Double,
    banks: List<PaymentSource>,
    selectedBankId: String?,
    onSelectBank: (String) -> Unit,
    onPay: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pay $cardName") },
        text = {
            Column {
                Text(
                    "${formatINR(amount)} outstanding. Pays now from the bank you pick — no due date.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (banks.isEmpty()) {
                    Text("Add a bank first so this bill can be paid from cash.")
                } else {
                    PaidFromChips(
                        sources = banks,
                        selectedId = selectedBankId,
                        onSelect = onSelectBank,
                        label = "Pay from",
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onPay,
                enabled = !selectedBankId.isNullOrBlank() && banks.isNotEmpty() && amount > 0.0,
            ) { Text("Pay now") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun PaidFromChips(
    sources: List<PaymentSource>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    label: String = "Paid from",
) {
    if (sources.isEmpty()) return
    val selected = sources.find { it.id == selectedId }
    SearchablePickerField(
        label = label,
        selectedLabel = selected?.displayLabel() ?: "Choose a bank or card",
        options = sources.map { PickerOption(it.id, it.displayLabel(), it.kindLabel()) },
        onSelect = onSelect,
        placeholder = "Search banks and cards",
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: androidx.compose.ui.text.TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = RoundedCornerShape(16.dp),
    colors: TextFieldColors = vaultTextFieldColors(),
    compact: Boolean = true,
) {
    val useCompact = compact && minLines <= 1
    if (!useCompact) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            label = label,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            prefix = prefix,
            suffix = suffix,
            supportingText = supportingText,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            shape = shape,
            colors = colors,
        )
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val compactText = MaterialTheme.typography.bodySmall.copy(
        fontSize = 13.sp,
        lineHeight = 16.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val mergedText = compactText.merge(textStyle.copy(fontSize = 13.sp, lineHeight = 16.sp, color = compactText.color))
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .defaultMinSize(minHeight = FieldHeight)
            .heightIn(min = FieldHeight, max = FieldHeightMax),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = mergedText,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true,
        maxLines = 1,
        visualTransformation = visualTransformation,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = inner,
                enabled = enabled,
                singleLine = true,
                visualTransformation = visualTransformation,
                interactionSource = interactionSource,
                isError = isError,
                label = label?.let { slot ->
                    { CompactFieldChrome { slot() } }
                },
                placeholder = placeholder?.let { slot ->
                    { CompactFieldChrome { slot() } }
                },
                leadingIcon = leadingIcon?.let { slot ->
                    { CompactFieldChrome { slot() } }
                },
                trailingIcon = trailingIcon?.let { slot ->
                    { CompactFieldChrome { slot() } }
                },
                prefix = prefix?.let { slot ->
                    { CompactFieldChrome { slot() } }
                },
                suffix = suffix?.let { slot ->
                    { CompactFieldChrome { slot() } }
                },
                supportingText = supportingText,
                colors = colors,
                contentPadding = OutlinedTextFieldDefaults.contentPadding(
                    start = 12.dp,
                    top = if (label != null) 6.dp else 8.dp,
                    end = 12.dp,
                    bottom = if (label != null) 6.dp else 8.dp,
                ),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = enabled,
                        isError = isError,
                        interactionSource = interactionSource,
                        colors = colors,
                        shape = shape,
                    )
                },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactFieldChrome(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalTextStyle provides MaterialTheme.typography.labelSmall.copy(
            fontSize = 12.sp,
            lineHeight = 14.sp,
        ),
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
    ) {
        content()
    }
}

@Composable
fun vaultTextFieldColors(
    focusedContainerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    unfocusedContainerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    disabledContainerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
    errorContainerColor: Color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
    focusedBorderColor: Color = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
    disabledBorderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
    errorBorderColor: Color = MaterialTheme.colorScheme.error,
    focusedTextColor: Color = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor: Color = MaterialTheme.colorScheme.onSurface,
    disabledTextColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    errorTextColor: Color = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor: Color = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledLabelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
    errorLabelColor: Color = MaterialTheme.colorScheme.error,
    focusedPlaceholderColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    unfocusedPlaceholderColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    disabledPlaceholderColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
    errorPlaceholderColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    focusedLeadingIconColor: Color = MaterialTheme.colorScheme.primary,
    unfocusedLeadingIconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledLeadingIconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
    errorLeadingIconColor: Color = MaterialTheme.colorScheme.error,
    focusedTrailingIconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedTrailingIconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledTrailingIconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
    errorTrailingIconColor: Color = MaterialTheme.colorScheme.error,
    cursorColor: Color = MaterialTheme.colorScheme.primary,
    errorCursorColor: Color = MaterialTheme.colorScheme.error,
): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = focusedContainerColor,
    unfocusedContainerColor = unfocusedContainerColor,
    disabledContainerColor = disabledContainerColor,
    errorContainerColor = errorContainerColor,
    focusedBorderColor = focusedBorderColor,
    unfocusedBorderColor = unfocusedBorderColor,
    disabledBorderColor = disabledBorderColor,
    errorBorderColor = errorBorderColor,
    focusedTextColor = focusedTextColor,
    unfocusedTextColor = unfocusedTextColor,
    disabledTextColor = disabledTextColor,
    errorTextColor = errorTextColor,
    focusedLabelColor = focusedLabelColor,
    unfocusedLabelColor = unfocusedLabelColor,
    disabledLabelColor = disabledLabelColor,
    errorLabelColor = errorLabelColor,
    focusedPlaceholderColor = focusedPlaceholderColor,
    unfocusedPlaceholderColor = unfocusedPlaceholderColor,
    disabledPlaceholderColor = disabledPlaceholderColor,
    errorPlaceholderColor = errorPlaceholderColor,
    focusedLeadingIconColor = focusedLeadingIconColor,
    unfocusedLeadingIconColor = unfocusedLeadingIconColor,
    disabledLeadingIconColor = disabledLeadingIconColor,
    errorLeadingIconColor = errorLeadingIconColor,
    focusedTrailingIconColor = focusedTrailingIconColor,
    unfocusedTrailingIconColor = unfocusedTrailingIconColor,
    disabledTrailingIconColor = disabledTrailingIconColor,
    errorTrailingIconColor = errorTrailingIconColor,
    cursorColor = cursorColor,
    errorCursorColor = errorCursorColor,
)

@Composable
private fun pickerFieldColors() = vaultTextFieldColors(
    disabledTextColor = MaterialTheme.colorScheme.onSurface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
    disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
)
