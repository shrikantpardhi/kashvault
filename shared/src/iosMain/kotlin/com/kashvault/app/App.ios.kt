package com.kashvault.app

import androidx.compose.runtime.Composable
import com.kashvault.db.DatabaseDriverFactory

@Composable
fun App() {
    KashVaultApp(DatabaseDriverFactory())
}
