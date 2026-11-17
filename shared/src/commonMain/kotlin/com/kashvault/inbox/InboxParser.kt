package com.kashvault.inbox

import com.kashvault.domain.Category
import com.kashvault.domain.Constants
import com.kashvault.domain.Expense
import com.kashvault.domain.InboxItem
import com.kashvault.domain.PaymentSource
import com.kashvault.domain.displayLabel
import com.kashvault.domain.isTransfer
import com.kashvault.util.amountsClose
import com.kashvault.util.outstandingCards
import com.kashvault.util.parseIsoTimestampOrNull
import com.kashvault.util.parseLocalDateOrNull
import com.kashvault.util.parseMoneyInput
import com.kashvault.util.sourceDisplayedBalance
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.round

@Serializable
data class FinancialExtractionResult(
    val is_valid_transaction: Boolean,
    val rejection_reason: String? = null,
    val transaction_data: ExtractedTransactionData? = null,
)

@Serializable
data class ExtractedTransactionData(
    val institution: String? = null,
    val instrument_type: String? = null,
    val transaction_type: String? = null,
    val amount: Double? = null,
    val currency: String = "INR",
    val account_identifier: String? = null,
    val merchant: String? = null,
    val available_balance: Double? = null,
)

private val EXTRACTION_JSON = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

data class ParsedInbox(
    val amount: Double,
    val type: String,
    val categoryId: String?,
    val sourceId: String?,
    val note: String?,
    val matchedCategoryTag: String?,
    val matchedSourceTag: String?,
)

data class InboxTagMatches(
    val categoryId: String? = null,
    val categoryTag: String? = null,
    val sourceId: String? = null,
    val sourceTag: String? = null,
)

data class InboxScanRules(
    val keywords: List<String>,
    val categories: List<Category>,
    val sources: List<PaymentSource>,
) {
    fun matches(sender: String, body: String): Boolean {
        val haystack = "$sender $body"
        return messageContainsScanKeyword(sender, body, keywords) ||
            matchCategory(haystack, categories) != null ||
            matchSource(haystack, sources) != null
    }

    fun tagsFor(sender: String, body: String): InboxTagMatches {
        val haystack = "$sender $body"
        val category = matchCategory(haystack, categories)
        val source = matchSource(haystack, sources)
        return InboxTagMatches(
            categoryId = category?.first,
            categoryTag = category?.second,
            sourceId = source?.first,
            sourceTag = source?.second,
        )
    }
}

private val AMOUNT_PATTERNS = listOf(
    Regex("""(?i)(?:inr|rs\.?|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""),
    Regex("""(?i)([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:inr|rs\.?)"""),
)

private val INCOME_WORDS = listOf(
    "credited", "received", "deposited", "refund", "cashback", "salary",
)
private val EXPENSE_WORDS = listOf(
    "debited", "spent", "paid", "withdrawn", "deducted", "purchase", "sent",
)

private val CARD_SPEND_HINTS = listOf(
    "spent on", "spent at", "purchase at", "purchase on", "used at",
    "txn at", "transaction at",
)

private val CARD_BILL_HINTS = listOf(
    "credit card payment", "card payment", "cc payment", "card bill",
    "paid towards", "payment towards", "towards your card", "towards credit card",
    "payment received", "bill payment", "paid to your", "payment for your card",
    "towards your hdfc", "towards your sbi", "towards your icici", "towards your axis",
)

fun isCardBillPayment(text: String): Boolean {
    val lower = text.lowercase()
    if (CARD_SPEND_HINTS.any { it in lower }) return false
    if (CARD_BILL_HINTS.any { it in lower }) return true
    val mentionsCard = "credit card" in lower ||
        "creditcard" in lower ||
        Regex("""\bcc\b""").containsMatchIn(lower) ||
        Regex("""\bcard\s+(?:ending|xx|no|number|#)""").containsMatchIn(lower)
    val mentionsPay = "paid" in lower || "payment" in lower || "paying" in lower
    return mentionsCard && mentionsPay
}

fun isCardSpend(text: String): Boolean {
    if (isCardBillPayment(text)) return false
    val lower = text.lowercase()
    return CARD_SPEND_HINTS.any { it in lower }
}

data class CardBillMatch(
    val cardId: String,
    val cardName: String,
    val bankId: String,
    val bankName: String,
    val outstanding: Double,
)

fun normalizeInboxText(text: String): String =
    text.lowercase().replace(Regex("[^a-z0-9]+"), "")

fun inboxFingerprint(body: String): String =
    "msg:${normalizeInboxText(body).take(200)}"

private val VOLATILE_INBOX_PATTERNS = listOf(
    Regex("""(?i)(?:avl|available)\s*bal(?:ance)?\s*[:\-]?\s*(?:inr|rs\.?|₹)?\s*[0-9,]+(?:\.\d{1,2})?"""),
    Regex("""(?i)(?:upi\s*)?(?:ref(?:erence)?(?:\s*(?:no|num|number))?|txn(?:\s*id)?|transaction\s*id)\s*[:\-#]?\s*[a-z0-9]+"""),
    Regex("""(?i)\b(?:not enough|tap for (?:more|details)|see details|view details)\b"""),
)

fun stabilizeInboxBody(body: String): String {
    var text = body
    VOLATILE_INBOX_PATTERNS.forEach { pattern ->
        text = pattern.replace(text, " ")
    }
    return text.replace(Regex("\\s+"), " ").trim()
}

fun inboxLookupKeys(sender: String, body: String): List<String> {
    val trimmed = body.trim()
    if (trimmed.isEmpty()) return emptyList()
    val keys = linkedSetOf<String>()
    val variants = linkedSetOf<String>()
    fun consider(raw: String, requireAmount: Boolean) {
        val text = raw.trim()
        if (text.isEmpty()) return
        if (requireAmount && extractAmount(text) == null) return
        variants += text
        val stable = stabilizeInboxBody(text)
        if (stable.isEmpty()) return
        if (requireAmount && extractAmount(stable) == null) return
        variants += stable
    }
    consider(trimmed, requireAmount = false)
    val withoutFirstLine = trimmed.lineSequence().drop(1).joinToString("\n").trim()
    consider(withoutFirstLine, requireAmount = true)
    trimmed.lineSequence().map { it.trim() }.filter { it.length >= 16 }.forEach { line ->
        consider(line, requireAmount = true)
    }
    val senderTrim = sender.trim()
    variants.filter { it.isNotBlank() }.forEach { variant ->
        keys += inboxFingerprint(variant)
        if (senderTrim.isNotEmpty()) keys += inboxFingerprint("$senderTrim $variant")
    }
    return keys.toList()
}

private const val CROSS_CHANNEL_WINDOW_MS = 4L * 60 * 60 * 1000

private val MERCHANT_LEGAL_SUFFIXES = listOf(
    "privatelimited", "private", "limited", "pvtltd", "pvt", "ltd", "llp",
    "india", "internet", "stores", "retail", "payments",
)

private val MERCHANT_ALIASES = mapOf(
    "fkrt" to "flipkart",
    "flipkartinternet" to "flipkart",
    "flipkartint" to "flipkart",
    "flipkartcom" to "flipkart",
    "amzn" to "amazon",
    "amazonin" to "amazon",
    "amazoncom" to "amazon",
    "amazonpay" to "amazon",
    "swiggyltd" to "swiggy",
    "googlepay" to "gpay",
    "googpay" to "gpay",
    "tez" to "gpay",
    "phpe" to "phonepe",
)

private val PAYMENT_RAILS = setOf(
    "gpay", "googlepay", "phonepe", "paytm", "bhim", "upi", "cred",
    "mobikwik", "tez", "whatsapp", "visa", "mastercard", "rupay", "card",
    "imps", "neft", "rtgs",
)

private val KNOWN_MERCHANT_BRANDS = listOf(
    "flipkart", "amazon", "myntra", "ajio", "nykaa", "meesho", "tata neu",
    "swiggy", "zomato", "blinkit", "zepto", "instamart", "bigbasket", "dmart",
    "uber", "ola", "rapido", "irctc", "makemytrip", "easemytrip", "cleartrip", "goibibo",
    "starbucks", "dominos", "mcdonald", "kfc", "burger king", "pizza hut", "subway",
    "netflix", "spotify", "hotstar", "youtube", "bookmyshow", "cultfit", "decathlon",
    "apollo", "netmeds", "pharmeasy", "tata 1mg", "1mg", "reliance smart", "reliance digital",
    "croma", "ikea", "hpcl", "bpcl", "iocl", "shell", "chai point",
).sortedByDescending { it.length }

data class MerchantIdentity(val family: String, val rail: Boolean)

fun canonicalizeMerchant(raw: String?): String? {
    var text = normalizeInboxText(raw.orEmpty())
    if (text.length < 3) return null
    var changed = true
    while (changed) {
        changed = false
        MERCHANT_LEGAL_SUFFIXES.forEach { suffix ->
            if (!changed && text.length - suffix.length >= 4 && text.endsWith(suffix)) {
                text = text.removeSuffix(suffix)
                changed = true
            }
        }
    }
    val aliased = MERCHANT_ALIASES[text] ?: text
    return aliased.takeIf { it.length >= 3 }
}

fun merchantIdentity(raw: String?): MerchantIdentity? {
    val canon = canonicalizeMerchant(raw) ?: return null
    val brand = KNOWN_MERCHANT_BRANDS.firstOrNull { known ->
        canon == known || (known.length >= 4 && canon.startsWith(known))
    }
    val family = MERCHANT_ALIASES[canon] ?: brand ?: canon.takeIf { it.length <= 24 }?.take(16) ?: return null
    return MerchantIdentity(family, family in PAYMENT_RAILS)
}

fun merchantQuality(raw: String?): Int {
    val identity = merchantIdentity(raw) ?: return 0
    return if (identity.rail) 1 else 2
}

fun inboxPurchaseDate(capturedAt: String?): String? {
    parseIsoTimestampOrNull(capturedAt.orEmpty())?.let { instant ->
        return instant.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
    }
    return parseLocalDateOrNull(capturedAt)?.toString()
}

fun inboxSemanticKey(
    amount: Double,
    type: String,
    capturedAt: String?,
    merchant: String?,
): String? {
    val identity = merchantIdentity(merchant) ?: return null
    if (identity.rail) return null
    val date = inboxPurchaseDate(capturedAt) ?: return null
    val paise = round(amount * 100.0).toLong()
    return "sem:$type:$paise:$date:${identity.family}"
}

fun preferredMerchant(
    sender: String = "",
    body: String,
    categoryTag: String? = null,
    note: String? = null,
): String? {
    val extracted = extractMerchant(body)
    val vpa = extractVpaHandle(body)
    val brand = findKnownMerchantBrand("$sender $body")
    val candidates = listOfNotNull(brand, extracted, vpa, categoryTag, note)
        .filter { normalizeInboxText(it).length >= 3 }
    val real = candidates.firstOrNull { identity ->
        merchantIdentity(identity)?.rail == false
    }
    return real ?: extracted ?: brand ?: vpa ?: categoryTag ?: note
}

fun inboxMerchantHint(
    body: String,
    note: String? = null,
    categoryTag: String? = null,
    sender: String = "",
): String? {
    val raw = preferredMerchant(sender, body, categoryTag, note)
    val norm = raw?.let { normalizeInboxText(it) }.orEmpty()
    return raw.takeIf { norm.length >= 3 }
}

fun inboxDedupKeys(
    sender: String,
    body: String,
    amount: Double? = null,
    type: String? = null,
    capturedAt: String? = null,
    merchant: String? = null,
    existingFingerprint: String? = null,
): List<String> {
    val keys = linkedSetOf<String>()
    existingFingerprint?.takeIf { it.isNotBlank() }?.let { keys += it }
    keys += inboxLookupKeys(sender, body)
    if (amount != null && !type.isNullOrBlank()) {
        inboxSemanticKey(amount, type, capturedAt, merchant)?.let { keys += it }
    }
    return keys.toList()
}

fun inboxItemDedupKeys(item: InboxItem): List<String> =
    inboxDedupKeys(
        sender = item.sender,
        body = item.body,
        amount = item.amount,
        type = item.type,
        capturedAt = item.capturedAt,
        merchant = inboxMerchantHint(item.body, item.note, item.matchedCategoryTag, item.sender),
        existingFingerprint = item.fingerprint,
    )

private fun inboxStatusRank(status: String): Int = when (status) {
    Constants.INBOX_ACCEPTED -> 2
    Constants.INBOX_PENDING -> 1
    else -> 0
}

private fun inboxItemMerchant(item: InboxItem): String? =
    inboxMerchantHint(item.body, item.note, item.matchedCategoryTag, item.sender)

fun merchantsCompatible(first: String?, second: String?): Boolean {
    val a = merchantIdentity(first)
    val b = merchantIdentity(second)
    val realA = a != null && !a.rail
    val realB = b != null && !b.rail
    if (realA && realB) {
        val familyA = a?.family ?: return false
        val familyB = b?.family ?: return false
        if (familyA == familyB) return true
        val shortest = min(familyA.length, familyB.length)
        return shortest >= 5 && (familyA.startsWith(familyB) || familyB.startsWith(familyA))
    }
    return realA || realB
}

fun sameInboxAmount(first: Double, second: Double): Boolean =
    round(first * 100.0).toLong() == round(second * 100.0).toLong()

fun isLikelySamePurchase(
    amount: Double,
    type: String,
    capturedAt: String?,
    merchant: String?,
    otherAmount: Double,
    otherType: String,
    otherCapturedAt: String?,
    otherMerchant: String?,
): Boolean {
    if (type != otherType) return false
    if (!sameInboxAmount(amount, otherAmount)) return false
    val first = merchantIdentity(merchant)
    val second = merchantIdentity(otherMerchant)
    val realFirst = first != null && !first.rail
    val realSecond = second != null && !second.rail
    val close = capturedWithinWindow(capturedAt, otherCapturedAt)
    val sameDay = inboxPurchaseDate(capturedAt) != null &&
        inboxPurchaseDate(capturedAt) == inboxPurchaseDate(otherCapturedAt)
    return when {
        realFirst && realSecond && merchantsCompatible(merchant, otherMerchant) -> sameDay || close
        realFirst && realSecond -> false
        (realFirst || realSecond) -> close
        else -> false
    }
}

fun isLikelySamePurchase(first: InboxItem, second: InboxItem): Boolean =
    isLikelySamePurchase(
        amount = first.amount,
        type = first.type,
        capturedAt = first.capturedAt,
        merchant = inboxItemMerchant(first),
        otherAmount = second.amount,
        otherType = second.type,
        otherCapturedAt = second.capturedAt,
        otherMerchant = inboxItemMerchant(second),
    )

fun expenseMatchesInboxPurchase(
    expense: Expense,
    amount: Double,
    type: String,
    capturedAt: String?,
    merchant: String?,
): Boolean {
    if (expense.isTransfer() || !expense.dueKind.isNullOrBlank()) return false
    val expenseType = expense.type ?: "expense"
    return isLikelySamePurchase(
        amount = amount,
        type = type,
        capturedAt = capturedAt,
        merchant = merchant,
        otherAmount = expense.amount,
        otherType = expenseType,
        otherCapturedAt = expense.timestamp,
        otherMerchant = expense.note,
    )
}

fun bestInboxDuplicate(item: InboxItem, candidates: List<InboxItem>): InboxItem? =
    bestInboxDuplicate(
        amount = item.amount,
        type = item.type,
        capturedAt = item.capturedAt,
        merchant = inboxItemMerchant(item),
        candidates = candidates.filter { it.id != item.id },
    )

fun bestInboxDuplicate(
    amount: Double,
    type: String,
    capturedAt: String?,
    merchant: String?,
    candidates: List<InboxItem>,
): InboxItem? {
    val matches = candidates.filter { candidate ->
        candidate.status != Constants.INBOX_DISMISSED &&
            isLikelySamePurchase(
                amount = amount,
                type = type,
                capturedAt = capturedAt,
                merchant = merchant,
                otherAmount = candidate.amount,
                otherType = candidate.type,
                otherCapturedAt = candidate.capturedAt,
                otherMerchant = inboxItemMerchant(candidate),
            )
    }
    if (matches.isEmpty()) return null
    val targetInstant = parseIsoTimestampOrNull(capturedAt.orEmpty())?.toEpochMilliseconds()
    return matches.minBy { candidate ->
        val other = parseIsoTimestampOrNull(candidate.capturedAt)?.toEpochMilliseconds()
        if (targetInstant != null && other != null) abs(targetInstant - other) else Long.MAX_VALUE
    }
}

private fun capturedWithinWindow(first: String?, second: String?): Boolean {
    val a = parseIsoTimestampOrNull(first.orEmpty()) ?: return false
    val b = parseIsoTimestampOrNull(second.orEmpty()) ?: return false
    return abs(a.toEpochMilliseconds() - b.toEpochMilliseconds()) <= CROSS_CHANNEL_WINDOW_MS
}

fun duplicateInboxIdsToRemove(items: List<InboxItem>): Set<String> {
    if (items.size <= 1) return emptySet()
    val parent = IntArray(items.size) { it }
    fun find(index: Int): Int {
        var current = index
        while (parent[current] != current) {
            parent[current] = parent[parent[current]]
            current = parent[current]
        }
        return current
    }
    fun union(left: Int, right: Int) {
        val a = find(left)
        val b = find(right)
        if (a != b) parent[a] = b
    }
    val keyOwner = mutableMapOf<String, Int>()
    items.forEachIndexed { index, item ->
        inboxItemDedupKeys(item).forEach { key ->
            val owner = keyOwner.put(key, index)
            if (
                owner != null &&
                items[index].type == items[owner].type &&
                sameInboxAmount(items[index].amount, items[owner].amount)
            ) {
                union(index, owner)
            }
        }
        for (other in 0 until index) {
            if (isLikelySamePurchase(item, items[other])) union(index, other)
        }
    }
    val ranked = items.sortedWith(
        compareByDescending<InboxItem> { inboxStatusRank(it.status) }
            .thenByDescending { merchantQuality(inboxItemMerchant(it)) }
            .thenByDescending { if (findKnownMerchantBrand(it.sender) != null) 1 else 0 }
            .thenByDescending { it.capturedAt }
            .thenBy { it.id },
    )
    val keep = items.indices
        .groupBy { find(it) }
        .values
        .map { group ->
            val ids = group.map { items[it].id }.toSet()
            ranked.first { it.id in ids }.id
        }
        .toSet()
    return items.map { it.id }.toSet() - keep
}

fun inboxChannelLabel(channel: String): String = when (channel) {
    Constants.INBOX_SMS -> "SMS"
    Constants.INBOX_NOTIFICATION -> "UPI / notification"
    Constants.INBOX_PASTE -> "Pasted"
    else -> channel
}

fun isValidSender(sender: String): Pair<Boolean, String?> {
    val clean = sender.trim()
    if (clean.isEmpty() || clean.equals("paste", ignoreCase = true)) {
        return true to null
    }
    val digitsOnly = clean.filter { it.isDigit() }
    val hasLetters = clean.any { it.isLetter() }
    if (!hasLetters && digitsOnly.length >= 10) {
        return false to "Invalid sender (10-digit mobile number)"
    }
    return true to null
}

fun isSpamOrPromotional(text: String): Boolean {
    val lower = text.lowercase()
    val hasExecutedTransaction = listOf(
        "debited from", "has been debited", "is debited", "spent on", "spent at",
        "credited to", "has been credited", "is credited",
    ).any { it in lower }
    if (hasExecutedTransaction) return false

    val spamKeywords = listOf(
        "loan approved", "pre-approved", "discount", "coupon",
        "win cashback", "cashback offer", "flat discount",
    )
    if (spamKeywords.any { it in lower }) return true
    if (Regex("""\boffer\b""").containsMatchIn(lower)) return true
    if (Regex("""\brummy\b""").containsMatchIn(lower)) return true
    if (Regex("""\bwin\b""").containsMatchIn(lower) && "withdraw" !in lower) return true
    if (Regex("""\bclaim\b""").containsMatchIn(lower)) return true
    return false
}

fun isOtp(text: String): Boolean {
    val lower = text.lowercase()
    val hasExecutedTransaction = listOf(
        "debited from", "has been debited", "is debited", "debited by", "debited for", "debited with",
        "spent on", "spent at", "charge on", "charge of",
        "credited to your", "credited to a/c", "has been credited", "is credited", "credited with", "credited by",
        "payment received towards", "paid towards",
        "withdrawn from", "cash withdrawal",
    ).any { it in lower }

    if (hasExecutedTransaction) {
        return false
    }

    val otpPatterns = listOf(
        Regex("""(?i)\b(?:is\s+your\s+(?:one\s*time\s*password|otp|verification\s*code))\b"""),
        Regex("""(?i)\b(?:your\s+(?:one\s*time\s*password|otp|verification\s*code)\s+is)\b"""),
        Regex("""(?i)\b(?:otp\s+(?:for|to)\s+(?:transaction|login|verify|auth|approve|register)[^.]*?\bis\s+[0-9]{4,8})\b"""),
        Regex("""(?i)\b(?:use\s+otp\s+[0-9]{4,8})\b"""),
        Regex("""(?i)\b(?:otp\s*[:\-]\s*[0-9]{4,8})\b"""),
        Regex("""(?i)\b(?:one\s*time\s*password\s*\([a-z0-9]+\)\s*is)\b"""),
        Regex("""(?i)\b(?:secret\s+otp\s+is)\b"""),
        Regex("""(?i)\b(?:verification\s+code\s+is)\b"""),
        Regex("""(?i)\b(?:valid\s+for\s+[0-9]+\s*(?:mins|minutes))\b"""),
    )
    if (otpPatterns.any { it.containsMatchIn(text) }) return true

    if (Regex("""\botp\b""").containsMatchIn(lower) && ("never share" in lower || "do not share" in lower || "is" in lower)) {
        return true
    }

    if ("one time password" in lower || "verification code" in lower) return true

    return false
}

fun isBillDue(text: String): Boolean {
    val lower = text.lowercase()
    val isPaymentDone = listOf(
        "payment received", "paid towards", "thank you for your payment",
        "has been debited", "debited from", "debited for", "spent on",
    ).any { it in lower }

    if (isPaymentDone && "minimum amount due is" !in lower && "total amount due rs" !in lower) {
        return false
    }

    val billDueKeywords = listOf(
        "statement generated",
        "minimum amount due",
        "total amount due",
        "payment due",
        "due on",
        "bill due",
        "payment is due",
        "bill generated",
    )
    return billDueKeywords.any { it in lower }
}

fun isLimitUpdateOnly(text: String, amount: Double?): Boolean {
    val lower = text.lowercase()
    val hasLimit = "available credit limit is" in lower ||
        "credit limit has been increased" in lower ||
        "available limit is" in lower ||
        "credit limit is" in lower ||
        "total credit limit is" in lower
    val hasTransactionWord = listOf(
        "debited", "spent", "paid", "withdrawn", "credited", "received",
        "purchase", "sent", "charged", "transferred", "deposited",
    ).any { it in lower }
    return hasLimit && !hasTransactionWord
}

fun extractInstitution(sender: String, body: String): String? {
    val haystack = "$sender $body".lowercase()
    return when {
        "hdfc" in haystack -> "HDFC Bank"
        "icici" in haystack || "imobile" in haystack -> "ICICI Bank"
        "sbi card" in haystack || "sbicrd" in haystack -> "SBI Card"
        "sbi" in haystack || "state bank" in haystack -> "SBI"
        "axis" in haystack -> "Axis Bank"
        "kotak" in haystack -> "Kotak Mahindra Bank"
        "pnb" in haystack || "punjab national" in haystack -> "Punjab National Bank"
        "bob" in haystack || "bank of baroda" in haystack -> "Bank of Baroda"
        "indus" in haystack -> "IndusInd Bank"
        "idfc" in haystack -> "IDFC FIRST Bank"
        "yes bank" in haystack || "yesbnk" in haystack -> "Yes Bank"
        "canara" in haystack -> "Canara Bank"
        "union bank" in haystack || "ubi" in haystack -> "Union Bank"
        "federal bank" in haystack -> "Federal Bank"
        "rbl" in haystack -> "RBL Bank"
        "aubank" in haystack || "au small finance" in haystack -> "AU Small Finance Bank"
        "amex" in haystack || "american express" in haystack -> "American Express"
        "onecard" in haystack -> "OneCard"
        "google pay" in haystack || "gpay" in haystack || "tez" in haystack -> "Google Pay"
        "phonepe" in haystack || "phpe" in haystack -> "PhonePe"
        "paytm" in haystack -> "Paytm"
        "amazon pay" in haystack -> "Amazon Pay"
        "bhim" in haystack -> "BHIM"
        "cred" in haystack -> "CRED"
        "mobikwik" in haystack -> "Mobikwik"
        "standard chartered" in haystack || "stanchart" in haystack -> "Standard Chartered"
        "citi" in haystack || "citibank" in haystack -> "Citibank"
        "hsbc" in haystack -> "HSBC"
        "dbs" in haystack -> "DBS Bank"
        "slice" in haystack -> "Slice"
        "jupiter" in haystack -> "Jupiter"
        "fi money" in haystack || "federal bank/fi" in haystack -> "Fi Money"
        else -> null
    }
}

fun extractInstrumentType(sender: String, body: String): String {
    val haystack = "$sender $body".lowercase()
    return when {
        "credit card" in haystack || "creditcard" in haystack || "sbi card" in haystack ||
            "amex" in haystack || "onecard" in haystack || Regex("""\bcc\b""").containsMatchIn(haystack) ||
            Regex("""card\s+(?:ending|no|number|xx)""").containsMatchIn(haystack) && "debit" !in haystack -> "CREDIT_CARD"
        "debit card" in haystack || "dc ending" in haystack -> "DEBIT_CARD"
        "wallet" in haystack || "amazon pay balance" in haystack || "paytm wallet" in haystack || "mobikwik wallet" in haystack -> "WALLET"
        "upi" in haystack || "vpa" in haystack || listOf("google pay", "gpay", "phonepe", "bhim", "paytm upi").any { it in haystack } -> "UPI_APP"
        "a/c" in haystack || "acct" in haystack || "account" in haystack || "savings" in haystack || "current a/c" in haystack -> "BANK_ACCOUNT"
        else -> "BANK_ACCOUNT"
    }
}

fun extractTransactionType(body: String): String {
    val lower = body.lowercase()
    if (isCardBillPayment(body)) {
        return if ("received" in lower || "credited" in lower || "paid to your" in lower) "CREDIT" else "DEBIT"
    }

    val explicitDebitPatterns = listOf(
        "debited from", "has been debited", "is debited", "debited by", "debited for", "debited with", "debited towards", "debit of",
        "spent on", "spent at", "purchase at", "purchase on", "purchase of",
        "withdrawn from", "cash withdrawal", "withdrawn at",
        "paid to", "you paid", "paid rs", "paid inr", "paid ₹", "paid for", "payment towards",
        "sent to", "you sent", "sent rs", "sent inr", "sent ₹",
        "charge of", "transferred to", "transfer to", "payment of rs", "payment of inr", "payment of ₹",
        "used at", "used on",
    )
    val explicitCreditPatterns = listOf(
        "credited to your", "credited to a/c", "credited to account", "has been credited", "is credited",
        "credited with", "credited by", "credited in", "credited towards", "credit of",
        "received from", "you received", "received rs", "received inr", "received ₹", "received into", "received in",
        "deposited in", "deposited to", "salary credited", "salary of", "salary received", "refund of", "refund credited", "cashback of", "cashback credited",
        "interest credited", "interest of",
    )

    val hasExplicitDebit = explicitDebitPatterns.any { it in lower }
    val hasExplicitCredit = explicitCreditPatterns.any { it in lower }

    if (hasExplicitDebit && !hasExplicitCredit) return "DEBIT"
    if (hasExplicitCredit && !hasExplicitDebit) return "CREDIT"

    if (hasExplicitDebit && hasExplicitCredit) {
        if (Regex("""(?i)(?:debited\s+from\s+(?:your\s+)?(?:a/c|account)|debited\s+from)""").containsMatchIn(lower)) {
            return "DEBIT"
        }
        if (Regex("""(?i)(?:credited\s+to\s+(?:your\s+)?(?:a/c|account)|credited\s+by\s+salary)""").containsMatchIn(lower)) {
            return "CREDIT"
        }
    }

    val incomeHits = INCOME_WORDS.count { it in lower }
    val expenseHits = EXPENSE_WORDS.count { it in lower }
    return if (incomeHits > expenseHits) "CREDIT" else "DEBIT"
}

fun extractAccountIdentifier(body: String): String? {
    val acctMatch = Regex("""(?i)(?:a/c|acct|account|card|ending|xx|#)\s*(?:no\.?)?\s*[:\-]?\s*(?:[xX*]+)?([0-9]{3,6})\b""").find(body)
    if (acctMatch != null) return acctMatch.groupValues[1]

    val digitsOnly = Regex("""(?i)\b(?:xx|x+|\*+)([0-9]{3,6})\b""").find(body)
    if (digitsOnly != null) return digitsOnly.groupValues[1]

    val vpaMatch = Regex("""(?i)\b([a-zA-Z0-9._-]+@[a-zA-Z0-9]+)\b""").find(body)
    if (vpaMatch != null) return vpaMatch.groupValues[1]

    return null
}

fun extractAvailableBalance(body: String): Double? {
    val patterns = listOf(
        Regex("""(?i)(?:avl|available|clear)\s*(?:bal(?:ance)?|credit\s*limit|limit)\s*[:\-]?\s*(?:inr|rs\.?|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""),
        Regex("""(?i)(?:bal|balance)\s*[:\-]?\s*(?:inr|rs\.?|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""),
        Regex("""(?i)(?:total\s+credit\s+limit|credit\s+limit)\s*[:\-]?\s*(?:inr|rs\.?|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""),
    )
    patterns.forEach { pattern ->
        val match = pattern.find(body) ?: return@forEach
        val amount = parseMoneyInput(match.groupValues[1])
        if (amount.isFinite() && amount >= 0) return amount
    }
    return null
}

fun formatMerchantDisplay(merchant: String?): String? {
    if (merchant.isNullOrBlank()) return null
    val clean = merchant.trim()
    val known = findKnownMerchantBrand(clean)
    if (known != null) {
        return known.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
    if (clean.length > 3 && clean.all { !it.isLetter() || it.isUpperCase() }) {
        return clean.lowercase().split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
    return clean
}

fun extractFinancialTransaction(sender: String, message: String): FinancialExtractionResult {
    val (senderValid, senderReason) = isValidSender(sender)
    if (!senderValid) {
        return FinancialExtractionResult(
            is_valid_transaction = false,
            rejection_reason = senderReason ?: "Invalid Sender",
            transaction_data = null,
        )
    }

    val haystack = "$sender $message"
    if (isOtp(haystack)) {
        return FinancialExtractionResult(
            is_valid_transaction = false,
            rejection_reason = "OTP message",
            transaction_data = null,
        )
    }

    if (isBillDue(haystack)) {
        return FinancialExtractionResult(
            is_valid_transaction = false,
            rejection_reason = "Bill Reminder",
            transaction_data = null,
        )
    }

    if (isSpamOrPromotional(haystack)) {
        return FinancialExtractionResult(
            is_valid_transaction = false,
            rejection_reason = "Promotional",
            transaction_data = null,
        )
    }

    val amount = extractAmount(haystack)
    if (amount == null) {
        return FinancialExtractionResult(
            is_valid_transaction = false,
            rejection_reason = "No transaction amount found",
            transaction_data = null,
        )
    }

    if (isLimitUpdateOnly(haystack, amount)) {
        return FinancialExtractionResult(
            is_valid_transaction = false,
            rejection_reason = "Limit Update",
            transaction_data = null,
        )
    }

    val institution = extractInstitution(sender, message)
    val instrumentType = extractInstrumentType(sender, message)
    val transactionType = extractTransactionType(message)
    val accountIdentifier = extractAccountIdentifier(message)
    val rawMerchant = preferredMerchant(sender, message)
    val merchant = formatMerchantDisplay(rawMerchant)
    val availableBalance = extractAvailableBalance(message)

    return FinancialExtractionResult(
        is_valid_transaction = true,
        rejection_reason = null,
        transaction_data = ExtractedTransactionData(
            institution = institution,
            instrument_type = instrumentType,
            transaction_type = transactionType,
            amount = amount,
            currency = "INR",
            account_identifier = accountIdentifier,
            merchant = merchant,
            available_balance = availableBalance,
        ),
    )
}

fun extractFinancialTransactionJson(sender: String, message: String): String {
    val result = extractFinancialTransaction(sender, message)
    return EXTRACTION_JSON.encodeToString(result)
}

fun matchesInstitution(source: PaymentSource, institution: String?): Boolean {
    if (institution.isNullOrBlank()) return false
    val inst = institution.lowercase()
    val sourceName = source.name.lowercase()
    val sourceKeywords = source.keywords.orEmpty().map { it.lowercase() }

    val bankAliases = mapOf(
        "hdfc" to listOf("hdfc", "millennia", "regalia", "infinia", "tata neu", "swiggy hdfc"),
        "sbi" to listOf("sbi", "state bank", "sbicrd", "simplyclick", "simplysave", "aurum", "cashback sbi", "sbi card"),
        "icici" to listOf("icici", "coral", "rubyx", "sapphiro", "amazon pay icici", "imobile"),
        "axis" to listOf("axis", "flipkart axis", "ace", "magnus", "neo", "aura", "my zone"),
        "kotak" to listOf("kotak", "811", "league", "mojo", "zen", "white"),
        "pnb" to listOf("pnb", "punjab national"),
        "bob" to listOf("bob", "bank of baroda", "baroda"),
        "idfc" to listOf("idfc", "first classic", "first millennia", "first wealth", "first select", "first wow", "club vistara idfc"),
        "indus" to listOf("indus", "indusind", "legend", "tiger", "pinnacle", "eazydiner"),
        "yes" to listOf("yes bank", "yes", "prosperity", "marquee"),
        "rbl" to listOf("rbl", "shoprite", "cookies", "world safari", "duet"),
        "amex" to listOf("amex", "american express", "smartearn", "platinum", "gold"),
        "onecard" to listOf("onecard", "one card"),
        "au" to listOf("aubank", "au bank", "au small finance", "zenith", "vetta", "altura", "lit"),
        "paytm" to listOf("paytm", "paytm wallet", "paytm payments bank"),
        "phonepe" to listOf("phonepe", "phone pe"),
        "gpay" to listOf("google pay", "gpay", "tez"),
        "amazon pay" to listOf("amazon pay", "amazon pay balance"),
    )

    for ((key, aliases) in bankAliases) {
        if (key in inst || aliases.any { it in inst }) {
            if (aliases.any { it in sourceName || sourceKeywords.any { kw -> it in kw } }) {
                return true
            }
        }
    }

    return sourceName.contains(inst) || inst.contains(sourceName) || sourceKeywords.any { it.isNotEmpty() && (it in inst || inst in it) }
}

fun matchSourceWithContext(
    haystack: String,
    sources: List<PaymentSource>,
    accountIdentifier: String? = null,
    institution: String? = null,
    instrumentType: String? = null,
    isCardSpend: Boolean = false,
    isBillPay: Boolean = false,
): Pair<String, String>? {
    if (sources.isEmpty()) return null

    val banks = sources.filter { it.kind != Constants.SOURCE_CREDIT_CARD }
    val cards = sources.filter { it.kind == Constants.SOURCE_CREDIT_CARD }

    val candidates = when {
        isBillPay -> banks.ifEmpty { sources }
        isCardSpend || instrumentType == "CREDIT_CARD" -> cards.ifEmpty { sources }
        instrumentType == "BANK_ACCOUNT" || instrumentType == "DEBIT_CARD" || instrumentType == "UPI_APP" || instrumentType == "WALLET" -> banks.ifEmpty { sources }
        else -> sources
    }

    // 1. Exact match by account identifier / last4
    if (!accountIdentifier.isNullOrBlank()) {
        val digits = accountIdentifier.trim().filter { it.isDigit() }
        if (digits.length >= 3) {
            val last4Target = digits.takeLast(4)
            val matchedByLast4 = candidates.find { source ->
                val sourceLast4 = source.last4?.trim().orEmpty()
                sourceLast4.isNotEmpty() && (sourceLast4 == last4Target || last4Target.endsWith(sourceLast4) || sourceLast4.endsWith(last4Target))
            } ?: sources.find { source ->
                val sourceLast4 = source.last4?.trim().orEmpty()
                sourceLast4.isNotEmpty() && (sourceLast4 == last4Target || last4Target.endsWith(sourceLast4) || sourceLast4.endsWith(last4Target))
            }
            if (matchedByLast4 != null) {
                return matchedByLast4.id to (matchedByLast4.last4 ?: last4Target)
            }
        }
    }

    // 2. Direct tag/keyword match in text
    val directMatch = matchSource(haystack, candidates) ?: matchSource(haystack, sources)
    if (directMatch != null) {
        return directMatch
    }

    // 3. Institution match
    if (!institution.isNullOrBlank()) {
        val matchedByInst = candidates.find { matchesInstitution(it, institution) }
            ?: sources.find { matchesInstitution(it, institution) }
        if (matchedByInst != null) {
            return matchedByInst.id to institution
        }
    }

    // 4. Default to single available instrument in candidates
    if (candidates.size == 1) {
        val single = candidates.single()
        return single.id to single.name
    }

    // 5. Default fallback to single source overall if only 1 exists
    if (sources.size == 1) {
        val single = sources.single()
        return single.id to single.name
    }

    return null
}

fun parseInboxMessage(
    body: String,
    sender: String = "",
    categories: List<Category>,
    sources: List<PaymentSource>,
): ParsedInbox? {
    val extraction = extractFinancialTransaction(sender, body)
    if (!extraction.is_valid_transaction || extraction.transaction_data == null) {
        return null
    }
    val data = extraction.transaction_data
    val amount = data.amount ?: return null
    val type = if (data.transaction_type == "CREDIT") "income" else "expense"
    val haystack = "$sender $body"
    val billPay = type != "income" && isCardBillPayment(haystack)
    val cardSpend = type != "income" && (isCardSpend(haystack) || data.instrument_type == "CREDIT_CARD")
    val banks = sources.filter { it.kind != Constants.SOURCE_CREDIT_CARD }
    val cards = sources.filter { it.kind == Constants.SOURCE_CREDIT_CARD }
    val sourceHit = matchSourceWithContext(
        haystack = haystack,
        sources = sources,
        accountIdentifier = data.account_identifier,
        institution = data.institution,
        instrumentType = data.instrument_type,
        isCardSpend = cardSpend,
        isBillPay = billPay,
    )
    val cardHit = if (billPay) {
        matchSourceWithContext(
            haystack = haystack,
            sources = cards,
            accountIdentifier = data.account_identifier,
            institution = data.institution,
            instrumentType = "CREDIT_CARD",
            isCardSpend = true,
            isBillPay = false,
        )
    } else null
    val typedCategories = categories.filter { category ->
        val categoryType = category.type ?: "expense"
        categoryType == type || (type == "expense" && categoryType != "income")
    }
    val categoryHit = matchCategory(haystack, typedCategories.ifEmpty { categories })
    val merchant = data.merchant ?: preferredMerchant(sender, body, categoryHit?.second)
    val cardName = cardHit?.first?.let { id -> sources.find { it.id == id }?.name }
    val note = when {
        billPay && !cardName.isNullOrBlank() -> "Card bill · $cardName"
        !merchant.isNullOrBlank() -> merchant
        else -> null
    }
    return ParsedInbox(
        amount = amount,
        type = type,
        categoryId = categoryHit?.first,
        sourceId = sourceHit?.first,
        note = note,
        matchedCategoryTag = categoryHit?.second,
        matchedSourceTag = sourceHit?.second,
    )
}

private val BALANCE_OR_LIMIT_PATTERNS = listOf(
    Regex("""(?i)(?:avl|available|clear|total|closing|updated)?\s*(?:bal(?:ance)?|credit\s*limit|limit)\s*[:\-]?\s*(?:inr|rs\.?|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""),
    Regex("""(?i)(?:bal|balance)\s*[:\-]?\s*(?:inr|rs\.?|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""),
    Regex("""(?i)(?:min(?:imum)?|total)?\s*(?:amount)?\s*due\s*(?:is|on)?\s*[:\-]?\s*(?:inr|rs\.?|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""),
)

private val VERB_AMOUNT_PATTERNS = listOf(
    Regex("""(?i)(?:debited\s*(?:by|for|with|towards)?|spent\s*(?:on|at)?|paid\s*(?:to|for)?|sent\s*(?:to)?|withdrawn\s*(?:at|from)?|credited\s*(?:by|with|to|in)?|received\s*(?:from|in|into)?|deposited\s*(?:in|to)?|refund\s*(?:of|for)?|cashback\s*(?:of)?|charge\s*(?:of)?|payment\s*(?:of)?|purchase\s*(?:of)?|transfer\s*(?:of)?|txn\s*(?:of)?|transaction\s*(?:of)?|salary\s*(?:of)?|emi\s*(?:of)?|bill\s*(?:of)?|interest\s*(?:of)?|amount\s*(?:of)?|amt\s*(?:of)?)\s*[:\-]?\s*(?:inr|rs\.?|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""),
    Regex("""(?i)(?:inr|rs\.?|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:debited|spent|paid|credited|received|withdrawn|transferred|deposited|charged)"""),
)

fun extractAmount(text: String): Double? {
    val balanceRanges = mutableListOf<IntRange>()
    BALANCE_OR_LIMIT_PATTERNS.forEach { pattern ->
        pattern.findAll(text).forEach { match ->
            if (match.groups.size > 1 && match.groups[1] != null) {
                balanceRanges += match.groups[1]!!.range
            } else {
                balanceRanges += match.range
            }
        }
    }

    fun isBalanceAmount(range: IntRange): Boolean {
        return balanceRanges.any { bRange ->
            range.first <= bRange.last && range.last >= bRange.first
        }
    }

    for (pattern in VERB_AMOUNT_PATTERNS) {
        for (match in pattern.findAll(text)) {
            val numGroup = match.groups[1] ?: continue
            if (!isBalanceAmount(numGroup.range)) {
                val amount = parseMoneyInput(numGroup.value)
                if (amount.isFinite() && amount > 0) return amount
            }
        }
    }

    for (pattern in AMOUNT_PATTERNS) {
        for (match in pattern.findAll(text)) {
            val numGroup = match.groups[1] ?: continue
            if (!isBalanceAmount(numGroup.range)) {
                val amount = parseMoneyInput(numGroup.value)
                if (amount.isFinite() && amount > 0) return amount
            }
        }
    }

    val standaloneVerbRegex = Regex("""(?i)\b(?:paid|spent|debited|sent|received|credited)\s+([0-9][0-9,]*(?:\.[0-9]{1,2})?)\b""")
    for (match in standaloneVerbRegex.findAll(text)) {
        val numGroup = match.groups[1] ?: continue
        if (!isBalanceAmount(numGroup.range)) {
            val amount = parseMoneyInput(numGroup.value)
            if (amount.isFinite() && amount > 0) return amount
        }
    }

    AMOUNT_PATTERNS.forEach { pattern ->
        val match = pattern.find(text) ?: return@forEach
        val amount = parseMoneyInput(match.groupValues[1])
        if (amount.isFinite() && amount > 0) return amount
    }

    return null
}

fun matchCategory(text: String, categories: List<Category>): Pair<String, String>? {
    var best: Pair<String, String>? = null
    var bestLen = 0
    categories.forEach { category ->
        category.keywords.orEmpty().forEach { keyword ->
            val hit = keyword.trim()
            if (hit.length >= 2 && containsTag(text, hit) && hit.length > bestLen) {
                best = category.id to hit
                bestLen = hit.length
            }
        }
    }
    return best
}

private const val LAST4_MATCH_SCORE = 1000

fun matchSource(text: String, sources: List<PaymentSource>): Pair<String, String>? {
    var best: Pair<String, String>? = null
    var bestScore = 0
    sources.forEach { source ->
        val last4 = source.last4?.trim().orEmpty()
        if (last4.length == 4 && containsTag(text, last4)) {
            val score = LAST4_MATCH_SCORE + last4.length
            if (score >= bestScore) {
                best = source.id to last4
                bestScore = score
            }
        }
        val tags = source.keywords.orEmpty() + listOfNotNull(source.name.takeIf { it.length >= 3 })
        tags.forEach { keyword ->
            val hit = keyword.trim()
            if (hit.length >= 2 && containsTag(text, hit) && hit.length > bestScore) {
                best = source.id to hit
                bestScore = hit.length
            }
        }
    }
    return best
}

fun matchInboxToCardBill(
    amount: Double,
    suggestedSourceId: String?,
    sender: String,
    body: String,
    sources: List<PaymentSource>,
    expenses: List<Expense>,
    lastPaymentSourceId: String? = null,
): CardBillMatch? {
    if (amount <= 0.0) return null
    val haystack = "$sender $body"
    if (!isCardBillPayment(haystack)) return null
    val banks = sources.filter { it.kind != Constants.SOURCE_CREDIT_CARD }
    val cards = sources.filter { it.kind == Constants.SOURCE_CREDIT_CARD }
    if (banks.isEmpty() || cards.isEmpty()) return null
    val namedCards = cards.filter { matchSource(haystack, listOf(it)) != null }
    val owedRows = outstandingCards(sources, expenses)
    val card = pickCardForBillPayment(amount, namedCards, owedRows) ?: return null
    val owed = sourceDisplayedBalance(card, expenses)
    if (owed <= 0.0) return null
    val bankHit = matchSource(haystack, banks)
    val bank = banks.find { it.id == bankHit?.first }
        ?: banks.find { it.id == suggestedSourceId }
        ?: banks.find { it.id == card.payFromSourceId }
        ?: banks.find { it.id == lastPaymentSourceId }
        ?: banks.singleOrNull()
        ?: return null
    return CardBillMatch(
        cardId = card.id,
        cardName = card.name,
        bankId = bank.id,
        bankName = bank.name,
        outstanding = owed,
    )
}

private fun pickCardForBillPayment(
    amount: Double,
    namedCards: List<PaymentSource>,
    owedRows: List<Pair<PaymentSource, Double>>,
): PaymentSource? {
    val namedOwed = owedRows.filter { row -> namedCards.any { it.id == row.first.id } }
    if (namedOwed.isNotEmpty()) {
        val close = namedOwed.filter { amountsClose(amount, it.second) }
        return (close.ifEmpty { namedOwed }).minBy { abs(it.second - amount) }.first
    }
    val close = owedRows.filter { amountsClose(amount, it.second) }
    if (close.size == 1) return close.first().first
    if (owedRows.size == 1 && amount <= owedRows.first().second) return owedRows.first().first
    return null
}

fun containsTag(text: String, tag: String): Boolean {
    val hay = text.lowercase()
    val needle = tag.lowercase()
    if (needle.isEmpty()) return false
    val digitsOnly = needle.all { it.isDigit() }
    val shortWord = !digitsOnly && needle.length <= 3
    var start = 0
    while (true) {
        val idx = hay.indexOf(needle, start)
        if (idx < 0) return false
        val before = if (idx == 0) null else hay[idx - 1]
        val after = hay.getOrNull(idx + needle.length)
        val ok = when {
            digitsOnly -> (before == null || !before.isDigit()) && (after == null || !after.isDigit())
            shortWord -> (before == null || !before.isLetterOrDigit()) && (after == null || !after.isLetterOrDigit())
            else -> true
        }
        if (ok) return true
        start = idx + 1
    }
}

fun extractMerchant(body: String): String? {
    val upiInfoMatch = Regex("""(?i)\bupi\s*(?:ref\s*(?:no)?)?\s*[:/]\s*(?:dr/|cr/|p2m/|p2a/)?(?:[0-9]{6,16}/)?([a-z0-9 .&'_-]{3,30})[/ \n]""").find(body)
    if (upiInfoMatch != null) {
        val cand = upiInfoMatch.groupValues[1].trim().trimEnd('.', ',', ';', '-')
        if (cand.length >= 3 && !cand.all { it.isDigit() } && merchantIdentity(cand)?.rail != true) {
            return formatMerchantDisplay(cand)
        }
    }

    val patterns = listOf(
        Regex("""(?i)\bat\s+([A-Za-z0-9 .&'_-]{3,40})"""),
        Regex("""(?i)\bto\s+([A-Za-z0-9 .@&'_-]{3,40})"""),
        Regex("""(?i)\btowards\s+([A-Za-z0-9 .&'_-]{3,40})"""),
        Regex("""(?i)\btransfer\s+to\s+([A-Za-z0-9 .&'_-]{3,40})"""),
        Regex("""(?i)\btransfer\s+from\s+([A-Za-z0-9 .&'_-]{3,40})"""),
        Regex("""(?i)\btransferred\s+to\s+([A-Za-z0-9 .&'_-]{3,40})"""),
        Regex("""(?i)\breceived\s+from\s+([A-Za-z0-9 .&'_-]{3,40})"""),
        Regex("""(?i)\bsent\s+to\s+([A-Za-z0-9 .&'_-]{3,40})"""),
        Regex("""(?i)\bfor\s+(?:your\s+)?([A-Za-z][A-Za-z0-9 .&'_-]{2,40}?)\s+order"""),
    )
    patterns.forEach { pattern ->
        val value = pattern.find(body)?.groupValues?.getOrNull(1)?.trim()?.trimEnd('.', ',', ';')
        val lowerVal = value?.lowercase().orEmpty()
        val isAccountTerm = lowerVal.startsWith("a/c") || lowerVal.startsWith("acc") ||
            lowerVal.startsWith("your a/c") || lowerVal.startsWith("your account") ||
            lowerVal.startsWith("your card") || lowerVal.startsWith("account") ||
            lowerVal.startsWith("vpa") || lowerVal.startsWith("card")
        if (!value.isNullOrBlank() && !isAccountTerm) {
            val cleaned = value
                .split(Regex("""(?i)\s+(?:using|via|with|from|on|for|through|avl|available|is\s+successful|successful)\s+"""))
                .first()
                .substringBefore('@')
                .trim()
                .trimEnd('.', ',', ';', '-')
            if (cleaned.length >= 3 && !cleaned.all { it.isDigit() }) {
                val finalClean = if (cleaned.lowercase().startsWith("vpa ")) cleaned.drop(4).trim() else cleaned
                if (finalClean.length >= 3 && !finalClean.all { it.isDigit() }) {
                    return finalClean.take(40)
                }
            }
        }
    }

    extractVpaHandle(body)?.let { return it }

    return findKnownMerchantBrand(body)
}

fun extractVpaHandle(body: String): String? {
    val match = Regex("""(?i)\b(?:vpa|upi)\b[^a-z0-9]{0,12}([a-z0-9._-]{3,40})@""")
        .find(body)
    val local = match?.groupValues?.getOrNull(1)?.substringBefore('.')?.trim().orEmpty()
    return local.takeIf { it.length >= 3 && merchantIdentity(it)?.rail != true }
}

fun findKnownMerchantBrand(text: String): String? {
    var best: String? = null
    var bestLen = 0
    val names = (KNOWN_MERCHANT_BRANDS + MERCHANT_ALIASES.keys).distinct()
    names.forEach { brand ->
        if (brand.length < 4) return@forEach
        val family = merchantIdentity(brand) ?: return@forEach
        if (family.rail) return@forEach
        if (containsTag(text, brand) && brand.length > bestLen) {
            best = family.family
            bestLen = brand.length
        }
    }
    return best
}

fun defaultSourceTags(name: String, last4: String?): List<String> {
    val fromName = name.split(Regex("\\s+")).map { it.trim() }.filter { it.length >= 3 }
    return (fromName + listOfNotNull(last4?.takeIf { it.length == 4 })).distinct()
}

val DEFAULT_SMS_SCAN_KEYWORDS = listOf(
    "debited", "credited", "spent", "paid", "withdrawn", "deducted",
    "purchase", "sent", "received", "deposited", "refund", "cashback",
    "salary", "upi", "imps", "neft", "rtgs", "rs.", "inr", "₹", "a/c",
)

fun messageContainsScanKeyword(sender: String, body: String, keywords: List<String>): Boolean =
    matchingScanKeyword(sender, body, keywords) != null ||
        keywords.none { it.trim().isNotEmpty() }

fun matchingScanKeyword(sender: String, body: String, keywords: List<String>): String? {
    val terms = keywords.map { it.trim() }.filter { it.isNotEmpty() }
    if (terms.isEmpty()) return null
    val haystack = "$sender $body".lowercase()
    return terms
        .filter { term -> haystack.contains(term.lowercase()) }
        .maxByOrNull { it.length }
}

fun inboxCaptureReason(
    item: InboxItem,
    categories: List<Category>,
    sources: List<PaymentSource>,
    scanKeywords: List<String>,
): String {
    val kept = matchingScanKeyword(item.sender, item.body, scanKeywords)?.let { "Kept for $it" }
    val categoryName = categories.find { it.id == item.suggestedCategoryId }?.name
    val category = when {
        !item.matchedCategoryTag.isNullOrBlank() && !categoryName.isNullOrBlank() ->
            "$categoryName · ${item.matchedCategoryTag}"
        !item.matchedCategoryTag.isNullOrBlank() -> item.matchedCategoryTag
        !categoryName.isNullOrBlank() -> categoryName
        else -> null
    }
    val sourceName = sources.find { it.id == item.suggestedSourceId }?.displayLabel()
    val source = when {
        !item.matchedSourceTag.isNullOrBlank() && !sourceName.isNullOrBlank() ->
            "$sourceName · ${item.matchedSourceTag}"
        !sourceName.isNullOrBlank() -> sourceName
        !item.matchedSourceTag.isNullOrBlank() -> item.matchedSourceTag
        else -> null
    }
    return listOfNotNull(kept, category, source).joinToString(" · ")
}

fun inboxAlreadyInHistory(item: InboxItem, expenses: List<Expense>): Boolean {
    val merchant = inboxMerchantHint(item.body, item.note, item.matchedCategoryTag, item.sender)
    return expenses.any { expenseMatchesInboxPurchase(it, item.amount, item.type, item.capturedAt, merchant) }
}

private val SKIP_LEARN_TOKENS = (
    DEFAULT_SMS_SCAN_KEYWORDS + listOf(
        "google", "gpay", "phonepe", "paytm", "bhim", "bank", "account", "wallet",
    )
).map { it.lowercase() }.toSet()

fun learnableKeywordToken(item: InboxItem): String? {
    val merchant = inboxMerchantHint(item.body, item.note, item.matchedCategoryTag, item.sender)
    val candidates = listOfNotNull(
        item.matchedCategoryTag,
        merchant?.split(Regex("[^A-Za-z0-9]+"))?.firstOrNull { it.length >= 4 },
    ).map { it.trim().lowercase() }
    return candidates.firstOrNull { token ->
        token.length in 3..24 &&
            !token.all { ch -> ch.isDigit() } &&
            token !in SKIP_LEARN_TOKENS
    }
}

fun learnedCategoryKeywords(category: Category, item: InboxItem): List<String>? {
    val token = learnableKeywordToken(item) ?: return null
    val current = category.keywords.orEmpty()
    if (current.any { it.equals(token, ignoreCase = true) }) return null
    return current + token
}

fun shouldLearnFromInboxItem(item: InboxItem): Boolean =
    item.status == Constants.INBOX_ACCEPTED

fun resolvedInboxAmount(item: InboxItem, proposedAmount: Double?): Double? {
    val proposed = proposedAmount?.takeIf { it.isFinite() && it > 0 }
    return proposed ?: item.amount.takeIf { it.isFinite() && it > 0 }
}

fun learnedSourceKeywords(source: PaymentSource, item: InboxItem): List<String>? {
    val tag = item.matchedSourceTag?.trim().orEmpty()
    if (tag.length < 3 || tag.all { it.isDigit() }) return null
    val token = tag.lowercase()
    if (token in SKIP_LEARN_TOKENS) return null
    val current = source.keywords.orEmpty()
    if (current.any { it.equals(token, ignoreCase = true) }) return null
    if (source.name.contains(token, ignoreCase = true)) return null
    return current + token
}

fun inboxCategoryBadge(item: InboxItem, categories: List<Category>): String {
    categories.find { it.id == item.suggestedCategoryId }?.name?.takeIf { it.isNotBlank() }?.let { return it }
    item.matchedCategoryTag?.trim()?.takeIf { it.isNotBlank() }?.let { tag ->
        return tag.replaceFirstChar { ch -> ch.uppercase() }
    }
    val haystack = "${item.body} ${item.note.orEmpty()} ${item.sender}".lowercase()
    return when {
        "salary" in haystack -> "Salary"
        "refund" in haystack || "cashback" in haystack -> "Refund"
        listOf("swiggy", "zomato", "eats", "restaurant").any { it in haystack } -> "Food"
        listOf("petrol", "fuel", "hpcl", "bpcl", "iocl").any { it in haystack } -> "Fuel"
        listOf("uber", "ola", "metro").any { it in haystack } -> "Transport"
        listOf("blinkit", "zepto", "grocery", "bigbasket").any { it in haystack } -> "Grocery"
        listOf("amazon", "flipkart", "myntra", "ajio").any { it in haystack } -> "Shopping"
        "upi" in haystack -> "UPI"
        item.type == "income" -> "Income"
        else -> "Expense"
    }
}

fun inboxFilterMatches(
    item: InboxItem,
    filter: String,
    categories: List<Category> = emptyList(),
): Boolean {
    val badge = inboxCategoryBadge(item, categories).lowercase()
    val haystack = "${item.body} ${item.note.orEmpty()} ${item.sender} ${item.channel} $badge".lowercase()
    val category = categories.find { it.id == item.suggestedCategoryId }
    val catHay = "${category?.name.orEmpty()} ${category?.keywords.orEmpty().joinToString()}".lowercase()
    return when (filter) {
        "pending" -> item.status == Constants.INBOX_PENDING
        "shopping" -> "shopping" in badge || "shop" in haystack || "shop" in catHay ||
            listOf("amazon", "flipkart", "myntra").any { it in haystack }
        "food" -> "food" in badge || listOf("swiggy", "zomato", "eats", "restaurant").any { it in haystack }
        "grocery" -> "grocery" in badge || listOf("blinkit", "zepto", "grocery", "bigbasket").any { it in haystack }
        "fuel" -> "fuel" in badge || listOf("petrol", "fuel", "hpcl", "bpcl").any { it in haystack }
        "salary" -> "salary" in badge || "salary" in haystack || "salary" in catHay
        "upi" -> "upi" in haystack || item.channel == Constants.INBOX_NOTIFICATION
        "refund" -> "refund" in badge || "refund" in haystack || "cashback" in haystack
        else -> true
    }
}

