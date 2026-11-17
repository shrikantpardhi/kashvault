package com.kashvault.domain

val INITIAL_CATEGORIES = listOf(
    Category(
        "cat_grocery",
        "Grocery",
        "🛒",
        "#51CF66",
        "expense",
        listOf(
            "grocery", "groceries", "kirana", "vegetables", "sabzi",
            "blinkit", "zepto", "instamart", "bigbasket", "dmart", "jiomart",
            "reliance fresh", "more supermarket", "nature's basket",
        ),
    ),
    Category(
        "cat_utils",
        "Utility bills",
        "💡",
        "#FCC419",
        "expense",
        listOf(
            "electricity", "water bill", "piped gas", "indane", "hp gas",
            "broadband", "wifi", "fiber", "airtel", "jio", "bsnl", "vodafone",
            "postpaid", "recharge", "tata power", "bescom", "msedcl", "utility",
        ),
    ),
    Category(
        "cat_rent",
        "Home Rent",
        "🏠",
        "#2F9E44",
        "expense",
        listOf("home rent", "house rent", "rent", "landlord", "pg rent"),
    ),
    Category(
        "cat_household",
        "Household",
        "🧹",
        "#94D82D",
        "expense",
        listOf("society", "maintenance", "maid", "plumber", "carpenter", "ikea", "urban company"),
    ),
    Category(
        "cat_food",
        "Food & Dining",
        "🍕",
        "#FF6B6B",
        "expense",
        listOf("swiggy", "zomato", "eatclub", "starbucks", "dominos", "mcdonald", "kfc", "dining", "restaurant"),
    ),
    Category(
        "cat_transport",
        "Transport",
        "🚗",
        "#339AF0",
        "expense",
        listOf("uber", "ola", "rapido", "irctc", "metro", "petrol", "diesel", "fuel", "hpcl", "bpcl", "iocl", "fastag"),
    ),
    Category(
        "cat_shopping",
        "Shopping",
        "🛍️",
        "#F06595",
        "expense",
        listOf("amazon", "flipkart", "myntra", "ajio", "nykaa", "meesho"),
    ),
    Category(
        "cat_personal",
        "Personal care",
        "💈",
        "#F783AC",
        "expense",
        listOf("salon", "spa", "gym", "cult", "barber"),
    ),
    Category(
        "cat_health",
        "Health",
        "💊",
        "#FF8787",
        "expense",
        listOf("pharmacy", "apollo", "1mg", "pharmeasy", "hospital", "clinic", "dentist"),
    ),
    Category(
        "cat_edu",
        "Education",
        "📚",
        "#5C7CFA",
        "expense",
        listOf("udemy", "coursera", "byju", "unacademy", "tuition", "school fees", "college"),
    ),
    Category(
        "cat_ent",
        "Entertainment",
        "🎬",
        "#845EF7",
        "expense",
        listOf("netflix", "hotstar", "jiohotstar", "spotify", "bookmyshow", "youtube", "prime video", "disney"),
    ),
    Category(
        "cat_travel",
        "Travel",
        "✈️",
        "#15AABF",
        "expense",
        listOf("makemytrip", "goibibo", "airbnb", "hotel", "flight", "indigo", "airindia", "booking.com"),
    ),
    Category(
        "cat_family",
        "Family",
        "👨‍👩‍👧",
        "#FF922B",
        "expense",
        listOf("school", "daycare", "kids", "family"),
    ),
    Category(
        "cat_giving",
        "Gifts & donations",
        "🎁",
        "#E8590C",
        "expense",
        listOf("donation", "charity", "temple", "zakat", "gift"),
    ),
    Category("cat_wealth", "Wealth & SIP", "💎", "#845EF7", "expense", listOf("groww", "zerodha", "sip", "mutual fund", "nps")),
    Category("cat_insurance", "Insurance", "🛡️", "#339AF0", "expense", listOf("lic", "policybazaar", "insurance", "premium")),
    Category("cat_loans", "Loan EMI", "📉", "#FF6B6B", "expense", listOf("emi", "loan", "homeloan", "bajaj finserv")),
    Category("cat_other_exp", "Others", "🏷️", "#ADB5BD", "expense"),
    Category("cat_salary", "Salary", "💼", "#1DD1A1", "income", listOf("salary", "payroll", "credited salary")),
    Category("cat_freelance", "Freelance", "💻", "#48DBFB", "income", listOf("upwork", "freelance", "consulting")),
    Category("cat_business", "Business", "🏢", "#20C997", "income", listOf("business", "invoice", "gst")),
    Category("cat_interest", "Interest", "📈", "#4ECDC4", "income", listOf("interest", "dividend", "cashback")),
    Category("cat_refund", "Refund", "↩️", "#74C0FC", "income", listOf("refund", "reversal")),
    Category("cat_gift", "Gift", "🎉", "#FF922B", "income", listOf("gift received", "bonus")),
    Category("cat_other_inc", "Others", "💰", "#BB8BFF", "income"),
)

val LEGACY_BUILTIN_NAMES: Map<String, Set<String>> = mapOf(
    "cat_rent" to setOf("Rent & Home", "Rent"),
    "cat_utils" to setOf("Utilities"),
)

/** Old vaults kept leftover names next to the current builtins. */
val LEGACY_CATEGORY_ALIASES: Map<String, String> = mapOf(
    "food" to "cat_food",
    "fuel" to "cat_transport",
    "petrol" to "cat_transport",
    "loans" to "cat_loans",
    "loan emi" to "cat_loans",
    "utilities" to "cat_utils",
    "rent" to "cat_rent",
    "rent & home" to "cat_rent",
)

data class CategoryMerge(
    val keepId: String,
    val dropId: String,
)

val GROCERY_KEYWORD_SET: Set<String> = INITIAL_CATEGORIES
    .first { it.id == "cat_grocery" }
    .keywords
    .orEmpty()
    .map { it.lowercase() }
    .toSet()

fun missingBuiltinCategories(existingIds: Set<String>): List<Category> =
    INITIAL_CATEGORIES.filter { it.id !in existingIds }

fun refreshedBuiltinCategory(existing: Category): Category? {
    val desired = INITIAL_CATEGORIES.find { it.id == existing.id } ?: return null
    var next = existing
    var changed = false

    if (existing.name in (LEGACY_BUILTIN_NAMES[existing.id] ?: emptySet())) {
        next = next.copy(name = desired.name, icon = desired.icon, color = desired.color)
        changed = true
    }

    if (existing.id == "cat_food") {
        val cleaned = next.keywords.orEmpty().filterNot { it.lowercase() in GROCERY_KEYWORD_SET }
        if (cleaned.size != next.keywords.orEmpty().size) {
            next = next.copy(keywords = cleaned.ifEmpty { desired.keywords })
            changed = true
        }
    }

    return if (changed) next else null
}

fun categoryMerges(categories: List<Category>): List<CategoryMerge> {
    val byId = categories.associateBy { it.id }
    val merges = linkedMapOf<String, CategoryMerge>()

    fun addMerge(keepId: String, dropId: String) {
        if (keepId == dropId) return
        if (keepId !in byId || dropId !in byId) return
        if (merges.containsKey(dropId)) return
        merges[dropId] = CategoryMerge(keepId = keepId, dropId = dropId)
    }

    categories.forEach { category ->
        if (isBuiltinCategory(category.id)) return@forEach
        val alias = LEGACY_CATEGORY_ALIASES[category.name.trim().lowercase()] ?: return@forEach
        addMerge(alias, category.id)
    }

    categories
        .groupBy { (it.type ?: "expense") to it.name.trim().lowercase() }
        .values
        .filter { it.size > 1 }
        .forEach { group ->
            val keep = group.firstOrNull { isBuiltinCategory(it.id) } ?: group.minBy { it.id }
            group.filter { it.id != keep.id }.forEach { drop -> addMerge(keep.id, drop.id) }
        }

    return merges.values.toList()
}
