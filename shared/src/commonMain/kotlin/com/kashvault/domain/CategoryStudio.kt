package com.kashvault.domain

val BUILTIN_CATEGORY_IDS: Set<String> = INITIAL_CATEGORIES.map { it.id }.toSet()

fun isBuiltinCategory(id: String): Boolean = id in BUILTIN_CATEGORY_IDS

fun preferredExpenseCategoryId(
    categories: List<Category>,
    lastUsedId: String? = null,
    suggestedId: String? = null,
    type: String = "expense",
): String {
    val pool = categories.filter {
        if (type == "income") it.type == "income" else it.type != "income"
    }.ifEmpty { categories }
    fun present(id: String?) = id?.takeIf { wanted -> pool.any { it.id == wanted } }
    present(suggestedId)?.let { return it }
    present(lastUsedId)?.let { return it }
    present(if (type == "income") "cat_other_inc" else "cat_other_exp")?.let { return it }
    return pool.firstOrNull()?.id ?: INITIAL_CATEGORIES.first().id
}

fun lastUsedExpenseCategoryId(expenses: List<Expense>): String? =
    expenses.firstOrNull { it.type != "income" && it.type != Constants.TYPE_TRANSFER }?.categoryId

fun sortedCategoriesForDisplay(categories: List<Category>): List<Category> {
    val order = INITIAL_CATEGORIES.mapIndexed { index, category -> category.id to index }.toMap()
    return categories.sortedWith(
        compareBy(
            { if (it.type == "income") 1 else 0 },
            { order[it.id] ?: Int.MAX_VALUE },
            { it.name.lowercase() },
        ),
    )
}

val CATEGORY_EMOJI_OPTIONS = listOf(
    "💰", "🏦", "💳", "💵", "🛍️", "🎁", "🛒", "🔖", "🏷️", "💎",
    "🍕", "🍔", "🍟", "🌭", "🍣", "🍱", "🍜", "🍝", "🥗", "🍲", "☕", "🍵", "🥤", "🍺", "🍷", "🍦", "🍩", "🍪", "🍎", "🥦",
    "🚗", "🚕", "🚌", "🚆", "✈️", "🚢", "🚲", "🛴", "⛽", "🅿️", "🚧",
    "🏠", "🏢", "🏗️", "🧹", "🛠️", "💡", "💧", "🔥", "🔌", "📡", "📱", "💻", "🖥️", "🖨️",
    "🍿", "🎬", "🎭", "🎨", "🎵", "🎸", "🎹", "🎮", "🕹️", "🎰", "🎲", "🎳", "🎯", "🎪", "🎡", "🎢",
    "💊", "🏥", "🚑", "💉", "🩺", "🦷", "👓", "🧘", "🏋️", "🏃", "🚴", "🏊", "⛹️", "🏇", "🏌️", "🎿", "⚽", "🏀", "🎾",
    "🏨", "🏖️", "⛰️", "🌋", "🏕️", "⛺", "🗺️", "🧭", "🌳", "🌴", "🌻", "🐶", "🐱", "🐹", "🐰", "🦁", "🐦", "🐬",
    "💼", "📊", "📈", "📉", "📚", "📖", "📒", "✏️", "✒️", "🎓", "🧪", "🔭", "🔬", "📁", "📆", "🔔",
    "👕", "👗", "🧥", "👠", "👟", "🎩", "💄", "💍", "🌂", "🛁", "🛌", "🧼", "🧴", "💈", "🍼", "🧸", "🎟️",
)

val CATEGORY_COLOR_OPTIONS = listOf(
    "#FF6B6B", "#FF8787", "#F06595", "#FF922B", "#FCC419", "#FFE66D",
    "#51CF66", "#1DD1A1", "#20C997", "#7C3AED", "#C026D3", "#339AF0",
    "#48DBFB", "#5C7CFA", "#4ECDC4", "#845EF7", "#BB8BFF", "#AE3EC9",
    "#99A3FF", "#B45309", "#1D4ED8", "#BE123C", "#CA8A04", "#ADB5BD",
)

fun keywordsFromText(text: String): List<String> =
    text.split(Regex("[,;\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }

fun keywordsToText(keywords: List<String>?): String = (keywords ?: emptyList()).joinToString(", ")
