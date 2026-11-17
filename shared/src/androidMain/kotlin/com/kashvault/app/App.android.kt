package com.kashvault.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.kashvault.db.DatabaseDriverFactory

@Composable
fun App() {
    val context = LocalContext.current.applicationContext
    KashVaultApp(DatabaseDriverFactory(context))
}
