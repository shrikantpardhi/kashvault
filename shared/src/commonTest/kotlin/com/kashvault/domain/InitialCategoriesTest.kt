package com.kashvault.domain

import com.kashvault.inbox.parseInboxMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InitialCategoriesTest {
    @Test
    fun requiredDefaultsArePresent() {
        val names = INITIAL_CATEGORIES.filter { it.type == "expense" }.map { it.name }
        assertTrue("Grocery" in names)
        assertTrue("Utility bills" in names)
        assertTrue("Home Rent" in names)
        assertTrue(INITIAL_CATEGORIES.any { it.id == "cat_grocery" && it.type == "expense" })
        assertTrue(INITIAL_CATEGORIES.any { it.id == "cat_utils" && it.name == "Utility bills" })
        assertTrue(INITIAL_CATEGORIES.any { it.id == "cat_rent" && it.name == "Home Rent" })
    }

    @Test
    fun missingIdsAreInsertedForExistingVaults() {
        val missing = missingBuiltinCategories(setOf("cat_food", "cat_salary"))
        assertTrue(missing.any { it.id == "cat_grocery" })
        assertTrue(missing.any { it.id == "cat_household" })
        assertTrue(missing.none { it.id == "cat_food" })
    }

    @Test
    fun legacyRentAndUtilitiesAreRenamed() {
        val rent = refreshedBuiltinCategory(
            Category("cat_rent", "Rent & Home", "🏠", "#51CF66", "expense", listOf("rent")),
        )
        assertEquals("Home Rent", rent?.name)
        val bills = refreshedBuiltinCategory(
            Category("cat_utils", "Utilities", "💡", "#FCC419", "expense", listOf("electricity")),
        )
        assertEquals("Utility bills", bills?.name)
        assertEquals(null, refreshedBuiltinCategory(Category("cat_rent", "Office rent", "🏠", "#51CF66", "expense")))
    }

    @Test
    fun groceryTagsMoveOffDining() {
        val dining = refreshedBuiltinCategory(
            Category("cat_food", "Food & Dining", "🍕", "#FF6B6B", "expense", listOf("swiggy", "blinkit", "zepto")),
        )
        assertNotNull(dining)
        assertTrue("swiggy" in dining.keywords.orEmpty())
        assertTrue(dining.keywords.orEmpty().none { it.equals("blinkit", ignoreCase = true) })
    }

    @Test
    fun blinkitMapsToGroceryNotDining() {
        val parsed = parseInboxMessage(
            body = "Rs.420 debited at BLINKIT from a/c XX4321",
            sender = "HDFCBK",
            categories = INITIAL_CATEGORIES,
            sources = emptyList(),
        )
        assertEquals("cat_grocery", parsed?.categoryId)
        val dining = parseInboxMessage(
            body = "You paid Rs.249 to Swiggy using Google Pay UPI",
            sender = "GPay",
            categories = INITIAL_CATEGORIES,
            sources = emptyList(),
        )
        assertEquals("cat_food", dining?.categoryId)
    }

    @Test
    fun leftoverNamedCategoriesMergeIntoBuiltins() {
        val leftover = listOf(
            Category("old_food", "Food", "🍕", "#FF6B6B", "expense"),
            Category("old_fuel", "Fuel", "⛽", "#339AF0", "expense"),
            Category("extra", "Extra", "🏷️", "#ADB5BD", "expense"),
        ) + INITIAL_CATEGORIES
        val merges = categoryMerges(leftover)
        assertEquals("cat_food", merges.first { it.dropId == "old_food" }.keepId)
        assertEquals("cat_transport", merges.first { it.dropId == "old_fuel" }.keepId)
        assertTrue(merges.none { it.dropId == "extra" })
    }

    @Test
    fun sameNameDuplicatesKeepTheBuiltin() {
        val extraDining = Category("dup_food", "Food & Dining", "🍕", "#FF6B6B", "expense")
        val merges = categoryMerges(INITIAL_CATEGORIES + extraDining)
        assertEquals(CategoryMerge("cat_food", "dup_food"), merges.single())
    }

    @Test
    fun addAndInboxDefaultToOthersNotEducation() {
        assertEquals("cat_other_exp", preferredExpenseCategoryId(INITIAL_CATEGORIES))
        assertEquals("cat_edu", preferredExpenseCategoryId(INITIAL_CATEGORIES, suggestedId = "cat_edu"))
        assertEquals("cat_food", preferredExpenseCategoryId(INITIAL_CATEGORIES, lastUsedId = "cat_food"))
        val sorted = sortedCategoriesForDisplay(INITIAL_CATEGORIES.shuffled())
        assertEquals("cat_grocery", sorted.first { it.type != "income" }.id)
    }
}
