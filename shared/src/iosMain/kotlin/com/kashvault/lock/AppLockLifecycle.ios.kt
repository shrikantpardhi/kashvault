package com.kashvault.lock

import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidEnterBackgroundNotification

actual fun bindAppLockOnBackground(onBackground: () -> Unit): AppLockBinding {
    val observer = NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIApplicationDidEnterBackgroundNotification,
        `object` = null,
        queue = null,
    ) { _ ->
        onBackground()
    }
    return AppLockBinding {
        NSNotificationCenter.defaultCenter.removeObserver(observer)
    }
}
