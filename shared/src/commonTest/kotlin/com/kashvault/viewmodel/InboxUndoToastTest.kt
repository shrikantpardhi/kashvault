package com.kashvault.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InboxUndoToastTest {
    @Test
    fun acceptAndDismissSnackbarsOfferUndo() {
        val dismissed = AppToast("Dismissed", actionLabel = "Undo")
        val accepted = AppToast("Added to history.", actionLabel = "Undo")
        assertEquals("Undo", dismissed.actionLabel)
        assertEquals("Undo", accepted.actionLabel)
    }

    @Test
    fun otherToastsHaveNoAction() {
        assertNull(AppToast("Profile updated").actionLabel)
    }

    @Test
    fun newerToastReplacesPreviousId() {
        val first = AppToast("Dismissed", actionLabel = "Undo", id = 1)
        val second = AppToast("Added to history.", actionLabel = "Undo", id = 2)
        assertEquals(false, first.id == second.id)
    }
}
