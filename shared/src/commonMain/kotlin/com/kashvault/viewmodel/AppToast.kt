package com.kashvault.viewmodel

import com.kashvault.domain.InboxItem

data class AppToast(
    val message: String,
    val actionLabel: String? = null,
    val id: Long = 0L,
)

internal data class InboxUndoAction(
    val inboxId: String,
    val item: InboxItem? = null,
    val expenseId: String? = null,
    val dueKind: String? = null,
    val dueEntityId: String? = null,
)


