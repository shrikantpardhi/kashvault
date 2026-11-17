package com.kashvault.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val ScreenHorizontal = 16.dp
val ScreenVertical = 16.dp
val ScreenGap = 16.dp
val ScreenContentPadding = PaddingValues(horizontal = ScreenHorizontal, vertical = ScreenVertical)
val CardRadius = 20.dp
val ButtonHeight = 48.dp
val FieldHeight = 48.dp
val FieldHeightMax = 50.dp
val FabSize = 56.dp

fun Modifier.vaultField(): Modifier =
    this.defaultMinSize(minHeight = FieldHeight).heightIn(min = FieldHeight, max = FieldHeightMax)
