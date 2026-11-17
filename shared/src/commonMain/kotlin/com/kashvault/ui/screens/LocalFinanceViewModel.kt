package com.kashvault.ui.screens

import androidx.compose.runtime.compositionLocalOf
import com.kashvault.viewmodel.FinanceViewModel

val LocalFinanceViewModel = compositionLocalOf<FinanceViewModel> {
    error("FinanceViewModel not provided")
}
