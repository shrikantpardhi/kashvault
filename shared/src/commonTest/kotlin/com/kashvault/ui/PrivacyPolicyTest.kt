package com.kashvault.ui

import com.kashvault.ui.navigation.chromeTitle
import com.kashvault.ui.screens.PrivacyPolicyScreen
import kotlin.test.Test
import kotlin.test.assertEquals

class PrivacyPolicyTest {

    @Test
    fun privacyPolicyScreenHasStableTitle() {
        assertEquals("Privacy & Safety", chromeTitle(PrivacyPolicyScreen))
        assertEquals("privacy-policy", PrivacyPolicyScreen.key)
    }
}

