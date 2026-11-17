package com.kashvault.lock

fun interface AppLockBinding {
    fun cancel()
}

expect fun bindAppLockOnBackground(onBackground: () -> Unit): AppLockBinding
