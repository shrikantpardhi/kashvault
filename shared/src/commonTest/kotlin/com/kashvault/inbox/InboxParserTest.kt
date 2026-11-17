package com.kashvault.inbox

import com.kashvault.domain.Category
import com.kashvault.domain.Constants
import com.kashvault.domain.Expense
import com.kashvault.domain.InboxItem
import com.kashvault.domain.PaymentSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InboxParserTest {
    private val food = Category("cat_food", "Food", "🍕", "#FF6B6B", "expense", listOf("swiggy", "zomato"))
    private val salary = Category("cat_salary", "Salary", "💼", "#1DD1A1", "income", listOf("salary", "payroll"))
    private val hdfc = PaymentSource(
        id = "src_hdfc",
        name = "HDFC Bank",
        kind = Constants.SOURCE_BANK,
        last4 = "4321",
        keywords = listOf("hdfc", "upi"),
    )
    private val shopping = Category(
        "cat_shopping",
        "Shopping",
        "🛍️",
        "#F06595",
        "expense",
        listOf("amazon", "flipkart", "myntra"),
    )
    private val millennia = PaymentSource(
        id = "src_card",
        name = "HDFC Millennia",
        kind = Constants.SOURCE_CREDIT_CARD,
        last4 = "8888",
        keywords = listOf("millennia"),
    )

    @Test
    fun hdfcDebitMatchesAmountTypeCategoryAndLast4() {
        val parsed = parseInboxMessage(
            body = "Rs.500.00 debited from a/c XX4321 on 19-08-26 at SWIGGY. Avl Bal Rs.12,000.00",
            sender = "HDFCBK",
            categories = listOf(food, salary),
            sources = listOf(hdfc, millennia),
        )
        assertNotNull(parsed)
        assertEquals(500.0, parsed.amount)
        assertEquals("expense", parsed.type)
        assertEquals("cat_food", parsed.categoryId)
        assertEquals("src_hdfc", parsed.sourceId)
        assertEquals("swiggy", parsed.matchedCategoryTag)
        assertEquals("4321", parsed.matchedSourceTag)
    }

    @Test
    fun gpaySwiggyNotification() {
        val parsed = parseInboxMessage(
            body = "You paid Rs.249 to Swiggy using Google Pay UPI",
            sender = "GPay",
            categories = listOf(food, salary),
            sources = listOf(hdfc.copy(keywords = listOf("gpay", "google pay", "upi"))),
        )
        assertNotNull(parsed)
        assertEquals(249.0, parsed.amount)
        assertEquals("expense", parsed.type)
        assertEquals("cat_food", parsed.categoryId)
        assertEquals("src_hdfc", parsed.sourceId)
    }

    @Test
    fun salaryCreditUsesIncomeCategory() {
        val parsed = parseInboxMessage(
            body = "INR 85000.00 credited to your a/c XX1111 towards SALARY from ACME PVT LTD",
            sender = "HDFCBK",
            categories = listOf(food, salary),
            sources = listOf(hdfc.copy(last4 = "1111", keywords = listOf("hdfc"))),
        )
        assertNotNull(parsed)
        assertEquals(85000.0, parsed.amount)
        assertEquals("income", parsed.type)
        assertEquals("cat_salary", parsed.categoryId)
        assertEquals("src_hdfc", parsed.sourceId)
        assertEquals("salary", parsed.matchedCategoryTag)
    }

    @Test
    fun ignoresMessagesWithoutAmount() {
        assertNull(
            parseInboxMessage(
                body = "Your OTP is 4321 for Swiggy",
                sender = "HDFCBK",
                categories = listOf(food),
                sources = listOf(hdfc),
            ),
        )
    }

    @Test
    fun shortTagDoesNotMatchInsideAnotherWord() {
        assertFalse(containsTag("premium deducted", "emi"))
        assertTrue(containsTag("EMI of Rs.10 deducted", "emi"))
        assertTrue(containsTag("a/c XX4321 debited", "4321"))
        assertFalse(containsTag("a/c XX14321 debited", "4321"))
    }

    @Test
    fun defaultSourceTagsIncludeNamePartsAndLast4() {
        assertEquals(listOf("HDFC", "Bank", "4321"), defaultSourceTags("HDFC Bank", "4321"))
    }

    @Test
    fun scanRulesAlsoMatchCategoryAndPaymentSourceTags() {
        val rules = InboxScanRules(
            keywords = listOf("debited"),
            categories = listOf(food),
            sources = listOf(hdfc),
        )

        assertTrue(rules.matches("BANK", "Payment at SWIGGY"))
        assertTrue(rules.matches("BANK", "HDFC account alert"))
        assertFalse(rules.matches("BANK", "Your OTP is 123456"))

        val tags = rules.tagsFor("BANK", "Payment at SWIGGY using HDFC")
        assertEquals("cat_food", tags.categoryId)
        assertEquals("swiggy", tags.categoryTag)
        assertEquals("src_hdfc", tags.sourceId)
        assertEquals("hdfc", tags.sourceTag)
    }

    @Test
    fun unparsedInboxItemsRequireAValidAmountAndAreNotLearnedWhenDismissed() {
        val reviewItem = InboxItem(
            id = "review",
            capturedAt = "2026-08-19T10:00:00Z",
            channel = Constants.INBOX_SMS,
            sender = "HDFCBK",
            body = "Payment at SWIGGY",
            amount = 0.0,
            type = "expense",
            suggestedCategoryId = "cat_food",
            suggestedSourceId = null,
            note = null,
            status = Constants.INBOX_PENDING,
            fingerprint = inboxFingerprint("Payment at SWIGGY"),
            needsReview = true,
        )

        assertNull(resolvedInboxAmount(reviewItem, null))
        assertNull(resolvedInboxAmount(reviewItem, 0.0))
        assertEquals(249.0, resolvedInboxAmount(reviewItem, 249.0))
        assertFalse(shouldLearnFromInboxItem(reviewItem))
        assertFalse(shouldLearnFromInboxItem(reviewItem.copy(status = Constants.INBOX_DISMISSED)))
        assertTrue(shouldLearnFromInboxItem(reviewItem.copy(status = Constants.INBOX_ACCEPTED)))
        assertTrue(inboxItemDedupKeys(reviewItem).none { it.startsWith("sem:") })
        assertTrue(shouldRecoverUnparsedInboxMessage(null, alreadyHasInboxItem = false))
        assertFalse(shouldRecoverUnparsedInboxMessage(null, alreadyHasInboxItem = true))
    }

    @Test
    fun fingerprintIgnoresChannelSenderCaseAndPunctuation() {
        val sms = "Rs.500.00 debited from a/c XX4321 at SWIGGY."
        assertEquals(
            inboxFingerprint(sms),
            inboxFingerprint("  RS.500.00 DEBITED from a/c XX4321 at swiggy  "),
        )
        val smsKeys = inboxLookupKeys("HDFCBK", sms)
        val notifKeys = inboxLookupKeys(
            "HDFC Bank",
            "HDFC Bank\nRs.500.00 debited from a/c XX4321 at SWIGGY.",
        )
        assertTrue(smsKeys.any { it in notifKeys })
        assertTrue(inboxLookupKeys("paste", sms).any { it in smsKeys })
        val withBalance = "Rs.500.00 debited from a/c XX4321 at SWIGGY. Avl Bal Rs.12,000.00 UPI Ref 998877"
        assertTrue(inboxLookupKeys("HDFCBK", sms).any { it in inboxLookupKeys("HDFCBK", withBalance) })
    }

    @Test
    fun extractMerchantStopsAtUsingAndAvailableBalance() {
        assertEquals(
            "Swiggy",
            extractMerchant("You paid Rs.249 to Swiggy using Google Pay UPI"),
        )
        assertEquals(
            "SWIGGY",
            extractMerchant("Rs.500.00 debited from a/c XX4321 at SWIGGY. Avl Bal Rs.12,000.00"),
        )
    }

    @Test
    fun smsAndUpiShareSemanticKey() {
        val capturedAt = "2026-08-19T10:15:00Z"
        val sms = "Rs.249.00 debited from a/c XX4321 on 19-08-26 at SWIGGY. Avl Bal Rs.12,000.00"
        val gpay = "You paid Rs.249 to Swiggy using Google Pay UPI"
        val smsMerchant = inboxMerchantHint(sms)
        val gpayMerchant = inboxMerchantHint(gpay)
        val smsKey = inboxSemanticKey(249.0, "expense", capturedAt, smsMerchant)
        val gpayKey = inboxSemanticKey(249.0, "expense", capturedAt, gpayMerchant)
        assertEquals(smsKey, gpayKey)
        assertNotNull(smsKey)
        assertTrue(
            inboxDedupKeys("HDFCBK", sms, 249.0, "expense", capturedAt, smsMerchant)
                .any { it in inboxDedupKeys("GPay", gpay, 249.0, "expense", capturedAt, gpayMerchant) },
        )
    }

    @Test
    fun duplicatePendingInboxItemsAreCollapsed() {
        fun item(id: String, body: String, sender: String, capturedAt: String, status: String = Constants.INBOX_PENDING) =
            InboxItem(
                id = id,
                capturedAt = capturedAt,
                channel = Constants.INBOX_SMS,
                sender = sender,
                body = body,
                amount = 249.0,
                type = "expense",
                suggestedCategoryId = "cat_food",
                suggestedSourceId = "src_hdfc",
                note = "Swiggy",
                status = status,
                fingerprint = inboxFingerprint(body),
                matchedCategoryTag = "swiggy",
            )
        val sms = item(
            id = "sms",
            body = "Rs.249.00 debited from a/c XX4321 at SWIGGY. Avl Bal Rs.8,000.00",
            sender = "HDFCBK",
            capturedAt = "2026-08-19T10:00:00Z",
        )
        val notif = item(
            id = "gpay",
            body = "You paid Rs.249 to Swiggy using Google Pay UPI",
            sender = "GPay",
            capturedAt = "2026-08-19T10:01:00Z",
        ).copy(channel = Constants.INBOX_NOTIFICATION, fingerprint = inboxFingerprint("You paid Rs.249 to Swiggy using Google Pay UPI"))
        val other = item(
            id = "other",
            body = "Rs.120.00 debited from a/c XX4321 at ZOMATO",
            sender = "HDFCBK",
            capturedAt = "2026-08-19T12:00:00Z",
            status = Constants.INBOX_PENDING,
        ).copy(amount = 120.0, note = "Zomato", fingerprint = inboxFingerprint("Rs.120.00 debited from a/c XX4321 at ZOMATO"), matchedCategoryTag = "zomato")
        val remove = duplicateInboxIdsToRemove(listOf(sms, notif, other))
        assertTrue("sms" in remove || "gpay" in remove)
        assertFalse("other" in remove)
        assertEquals(1, listOf("sms", "gpay").count { it !in remove })
    }

    @Test
    fun differentAmountsMinutesApartAreNotDuplicates() {
        val footer = "Thank you for banking with HDFC Bank. Call 1800123456 if this was not you."
        fun debit(id: String, amount: String, merchant: String, capturedAt: String) = InboxItem(
            id = id,
            capturedAt = capturedAt,
            channel = Constants.INBOX_SMS,
            sender = "HDFCBK",
            body = "Rs.$amount debited from a/c XX4321 at $merchant.\n$footer",
            amount = amount.toDouble(),
            type = "expense",
            suggestedCategoryId = "cat_food",
            suggestedSourceId = "src_hdfc",
            note = merchant,
            status = Constants.INBOX_PENDING,
            fingerprint = inboxFingerprint("Rs.$amount debited from a/c XX4321 at $merchant.\n$footer"),
            matchedCategoryTag = merchant.lowercase(),
        )
        val first = debit("first", "199.00", "SWIGGY", "2026-08-19T10:00:00Z")
        val second = debit("second", "450.00", "SWIGGY", "2026-08-19T10:01:30Z")
        assertFalse(isLikelySamePurchase(first, second))
        assertTrue(inboxItemDedupKeys(first).none { it in inboxItemDedupKeys(second) })
        assertTrue(duplicateInboxIdsToRemove(listOf(first, second)).isEmpty())
        assertTrue(
            inboxLookupKeys(first.sender, first.body)
                .none { it in inboxLookupKeys(second.sender, second.body) },
        )
    }

    @Test
    fun sameAmountOneMinuteApartStillCollapsesSmsAndUpi() {
        val sms = InboxItem(
            id = "sms",
            capturedAt = "2026-08-19T10:00:00Z",
            channel = Constants.INBOX_SMS,
            sender = "HDFCBK",
            body = "Rs.249.00 debited from a/c XX4321 at SWIGGY.",
            amount = 249.0,
            type = "expense",
            suggestedCategoryId = "cat_food",
            suggestedSourceId = "src_hdfc",
            note = "Swiggy",
            status = Constants.INBOX_PENDING,
            fingerprint = inboxFingerprint("Rs.249.00 debited from a/c XX4321 at SWIGGY."),
            matchedCategoryTag = "swiggy",
        )
        val upi = InboxItem(
            id = "upi",
            capturedAt = "2026-08-19T10:01:00Z",
            channel = Constants.INBOX_NOTIFICATION,
            sender = "GPay",
            body = "You paid Rs.249 to Swiggy using Google Pay UPI",
            amount = 249.0,
            type = "expense",
            suggestedCategoryId = "cat_food",
            suggestedSourceId = "src_hdfc",
            note = "Swiggy",
            status = Constants.INBOX_PENDING,
            fingerprint = inboxFingerprint("You paid Rs.249 to Swiggy using Google Pay UPI"),
            matchedCategoryTag = "swiggy",
        )
        assertTrue(isLikelySamePurchase(sms, upi))
        val remove = duplicateInboxIdsToRemove(listOf(sms, upi))
        assertEquals(1, remove.size)
    }

    @Test
    fun scanKeywordsMatchContainsAndIgnoreEmpty() {
        val sms = "Rs.500.00 debited from a/c XX4321 at SWIGGY"
        assertTrue(messageContainsScanKeyword("HDFCBK", sms, listOf("debited", "upi")))
        assertTrue(messageContainsScanKeyword("GPAY", "You paid Rs.249 using UPI", listOf("UPI")))
        assertFalse(messageContainsScanKeyword("JIO", "Your OTP is 123456", listOf("debited", "upi")))
        assertTrue(messageContainsScanKeyword("JIO", "Your OTP is 123456", emptyList()))
        assertTrue(messageContainsScanKeyword("HDFCBK", sms, DEFAULT_SMS_SCAN_KEYWORDS))
    }

    @Test
    fun flipkartLegalNameAndAliasShareSemanticKey() {
        val capturedAt = "2026-08-21T10:15:00Z"
        val legal = "FLIPKART INTERNET PRIVATE LIMITED"
        val short = "Flipkart"
        val alias = "FKRT"
        val legalKey = inboxSemanticKey(1299.0, "expense", capturedAt, legal)
        assertEquals(legalKey, inboxSemanticKey(1299.0, "expense", capturedAt, short))
        assertEquals(legalKey, inboxSemanticKey(1299.0, "expense", capturedAt, alias))
        assertEquals("flipkart", canonicalizeMerchant(legal))
        assertEquals("flipkart", merchantIdentity("flipkart.com")?.family)
        assertTrue(merchantIdentity("PhonePe")?.rail == true)
        assertNull(inboxSemanticKey(1299.0, "expense", capturedAt, "PhonePe"))
    }

    @Test
    fun flipkartOrderNotificationAndCardSmsAreTheSamePurchase() {
        val capturedAt = "2026-08-21T11:04:00Z"
        val order = "Your Flipkart order of Rs.1299 is confirmed"
        val card = "Rs.1299.00 spent on HDFC Bank Card XX8888 at FLIPKART.COM on 21-08-26"
        val parsedOrder = parseInboxMessage(order, "Flipkart", listOf(shopping, food), listOf(millennia))
        val parsedCard = parseInboxMessage(card, "HDFCBK", listOf(shopping, food), listOf(millennia))
        assertNotNull(parsedOrder)
        assertNotNull(parsedCard)
        assertEquals("cat_shopping", parsedOrder.categoryId)
        assertEquals(
            inboxSemanticKey(1299.0, "expense", capturedAt, parsedOrder.note),
            inboxSemanticKey(1299.0, "expense", capturedAt, parsedCard.note),
        )
        assertEquals("flipkart", extractVpaHandle("INR 1299.00 debited from A/c XX4321 to VPA flipkart.pay@okaxis"))
        assertEquals("flipkart", findKnownMerchantBrand(order))
    }

    @Test
    fun flipkartAppUpiAndCardMessagesCollapseToOneInboxItem() {
        fun item(id: String, body: String, sender: String, capturedAt: String, amount: Double = 1299.0) =
            InboxItem(
                id = id,
                capturedAt = capturedAt,
                channel = Constants.INBOX_NOTIFICATION,
                sender = sender,
                body = body,
                amount = amount,
                type = "expense",
                suggestedCategoryId = "cat_shopping",
                suggestedSourceId = "src_hdfc",
                note = preferredMerchant(sender, body, "flipkart"),
                status = Constants.INBOX_PENDING,
                fingerprint = inboxFingerprint(body),
                matchedCategoryTag = "flipkart",
            )
        val app = item(
            id = "app",
            body = "Paid Rs.1299 for your Flipkart order OD123",
            sender = "Flipkart",
            capturedAt = "2026-08-21T11:00:00Z",
        )
        val upi = item(
            id = "upi",
            body = "You paid Rs.1299 using PhonePe UPI",
            sender = "PhonePe",
            capturedAt = "2026-08-21T11:01:00Z",
        ).copy(channel = Constants.INBOX_NOTIFICATION, matchedCategoryTag = null, note = null)
        val card = item(
            id = "card",
            body = "Rs.1299.00 spent on HDFC Bank Card XX8888 at FLIPKART",
            sender = "HDFCBK",
            capturedAt = "2026-08-21T11:02:00Z",
        ).copy(channel = Constants.INBOX_SMS)
        val amazon = item(
            id = "amazon",
            body = "Rs.1299.00 debited from a/c XX4321 at AMAZON",
            sender = "HDFCBK",
            capturedAt = "2026-08-21T18:00:00Z",
        ).copy(matchedCategoryTag = "amazon", note = "Amazon")
        val remove = duplicateInboxIdsToRemove(listOf(app, upi, card, amazon))
        assertTrue("upi" in remove)
        assertTrue("card" in remove)
        assertFalse("app" in remove)
        assertFalse("amazon" in remove)
        assertTrue(isLikelySamePurchase(app, upi))
        assertTrue(isLikelySamePurchase(app, card))
        assertFalse(isLikelySamePurchase(app, amazon))
    }

    @Test
    fun bookedExpenseBlocksMatchingFlipkartCardSms() {
        val expense = Expense(
            id = "e1",
            amount = 1299.0,
            categoryId = "cat_shopping",
            timestamp = "2026-08-21T11:00:00Z",
            note = "Flipkart",
            type = "expense",
        )
        assertTrue(
            expenseMatchesInboxPurchase(
                expense,
                amount = 1299.0,
                type = "expense",
                capturedAt = "2026-08-21T11:03:00Z",
                merchant = "FLIPKART INTERNET PRIVATE LIMITED",
            ),
        )
        assertFalse(
            expenseMatchesInboxPurchase(
                expense,
                amount = 1299.0,
                type = "expense",
                capturedAt = "2026-08-21T11:03:00Z",
                merchant = "Amazon",
            ),
        )
    }

    @Test
    fun cardSpendSmsMatchesCardNotBankWhenBothNamedHdfc() {
        val sms = "Rs.1299.00 spent on HDFC Bank Card XX8888 at FLIPKART.COM on 21-08-26"
        assertTrue(isCardSpend(sms))
        assertFalse(isCardBillPayment(sms))
        val parsed = parseInboxMessage(
            body = sms,
            sender = "HDFCBK",
            categories = listOf(shopping, food),
            sources = listOf(hdfc, millennia),
        )
        assertNotNull(parsed)
        assertEquals(1299.0, parsed.amount)
        assertEquals("expense", parsed.type)
        assertEquals("src_card", parsed.sourceId)
        assertEquals("8888", parsed.matchedSourceTag)
        assertEquals(
            "src_card" to "8888",
            matchSource(sms, listOf(hdfc, millennia)),
        )
    }

    @Test
    fun cardSpendSmsIsNotABillPayment() {
        assertFalse(
            isCardBillPayment("Rs.1299.00 spent on HDFC Bank Card XX8888 at FLIPKART.COM on 21-08-26"),
        )
        assertFalse(isCardBillPayment("You paid Rs.249 to Swiggy using Google Pay UPI"))
        assertFalse(isCardBillPayment("Paid Rs.1299 for your Flipkart order OD123"))
    }

    @Test
    fun cardBillSmsPrefersBankAsSource() {
        val parsed = parseInboxMessage(
            body = "Rs.3500.00 debited from a/c XX4321 towards credit card payment HDFC Millennia",
            sender = "HDFCBK",
            categories = listOf(food, shopping),
            sources = listOf(hdfc, millennia),
        )
        assertNotNull(parsed)
        assertEquals(3500.0, parsed.amount)
        assertEquals("expense", parsed.type)
        assertEquals("src_hdfc", parsed.sourceId)
        assertEquals("Card bill · HDFC Millennia", parsed.note)
        assertTrue(isCardBillPayment("HDFC Credit Card payment of Rs 3500 from savings account"))
        assertTrue(isCardBillPayment("Rs 3500 paid towards Ace card"))
    }

    @Test
    fun cardBillSmsMatchesOutstandingAndPayFromBank() {
        val expenses = listOf(
            Expense("e1", 3_500.0, "cat_food", "2026-08-02T00:00:00Z", type = "expense", sourceId = "src_card"),
        )
        val match = matchInboxToCardBill(
            amount = 3_500.0,
            suggestedSourceId = "src_hdfc",
            sender = "HDFCBK",
            body = "Rs.3500.00 debited from a/c XX4321 towards credit card payment",
            sources = listOf(hdfc, millennia),
            expenses = expenses,
        )
        assertNotNull(match)
        assertEquals("src_card", match.cardId)
        assertEquals("src_hdfc", match.bankId)
        assertEquals(3_500.0, match.outstanding)
    }

    @Test
    fun namedCardBillCanBeAPartialPayment() {
        val expenses = listOf(
            Expense("e1", 5_000.0, "cat_food", "2026-08-02T00:00:00Z", type = "expense", sourceId = "src_card"),
        )
        val match = matchInboxToCardBill(
            amount = 2_000.0,
            suggestedSourceId = null,
            sender = "HDFCBK",
            body = "Thank you for paying Rs.2000 towards your HDFC Millennia credit card",
            sources = listOf(hdfc, millennia.copy(payFromSourceId = "src_hdfc")),
            expenses = expenses,
        )
        assertNotNull(match)
        assertEquals("src_card", match.cardId)
        assertEquals("src_hdfc", match.bankId)
    }

    @Test
    fun cardBillDoesNotMatchWhenNothingIsOutstanding() {
        val match = matchInboxToCardBill(
            amount = 3_500.0,
            suggestedSourceId = "src_hdfc",
            sender = "HDFCBK",
            body = "Rs.3500 paid towards Millennia credit card",
            sources = listOf(hdfc, millennia),
            expenses = emptyList(),
        )
        assertNull(match)
    }

    @Test
    fun categoryBadgeUsesMerchantAndFilters() {
        val swiggy = InboxItem(
            id = "i1",
            capturedAt = "2026-08-19T10:00:00Z",
            channel = Constants.INBOX_NOTIFICATION,
            sender = "GPAY",
            body = "Paid ₹250 to Swiggy via UPI",
            amount = 250.0,
            type = "expense",
            suggestedCategoryId = "cat_food",
            suggestedSourceId = "src_hdfc",
            note = "Swiggy",
            status = Constants.INBOX_PENDING,
            fingerprint = "f1",
            matchedCategoryTag = "swiggy",
        )
        assertEquals("Food", inboxCategoryBadge(swiggy, listOf(food, salary, shopping)))
        assertTrue(inboxFilterMatches(swiggy, "all", listOf(food)))
        assertTrue(inboxFilterMatches(swiggy, "pending", listOf(food)))
        assertTrue(inboxFilterMatches(swiggy, "upi", listOf(food)))
        assertFalse(inboxFilterMatches(swiggy, "salary", listOf(food, salary)))
        val payroll = swiggy.copy(
            id = "i2",
            body = "Salary credited INR 80000",
            type = "income",
            suggestedCategoryId = "cat_salary",
            matchedCategoryTag = "salary",
            channel = Constants.INBOX_SMS,
        )
        assertEquals("Salary", inboxCategoryBadge(payroll, listOf(food, salary)))
        assertTrue(inboxFilterMatches(payroll, "salary", listOf(salary)))
        val petrol = swiggy.copy(
            id = "i3",
            body = "INR 1200 spent at HPCL petrol pump",
            suggestedCategoryId = null,
            matchedCategoryTag = null,
            note = "HPCL",
        )
        assertEquals("Fuel", inboxCategoryBadge(petrol, emptyList()))
        assertTrue(inboxFilterMatches(petrol, "fuel", emptyList()))
        assertTrue(inboxFilterMatches(swiggy, "food", listOf(food)))
    }

    @Test
    fun captureReasonExplainsKeepAndTags() {
        val item = InboxItem(
            id = "i1",
            capturedAt = "2026-08-20T10:00:00Z",
            channel = Constants.INBOX_SMS,
            sender = "HDFCBK",
            body = "Rs.249.00 debited from a/c XX4321 at SWIGGY.",
            amount = 249.0,
            type = "expense",
            suggestedCategoryId = "cat_food",
            suggestedSourceId = "src_hdfc",
            note = "Swiggy",
            status = Constants.INBOX_PENDING,
            fingerprint = "f1",
            matchedCategoryTag = "swiggy",
            matchedSourceTag = "4321",
        )
        val reason = inboxCaptureReason(item, listOf(food), listOf(hdfc), listOf("debited", "upi"))
        assertTrue(reason.contains("debited"))
        assertTrue(reason.contains("Food"))
        assertTrue(reason.contains("4321"))
        val keywords = learnedCategoryKeywords(food, item)
        assertNull(keywords)
        val unknownFood = food.copy(keywords = emptyList())
        assertEquals(listOf("swiggy"), learnedCategoryKeywords(unknownFood, item))
    }

    @Test
    fun rejectsTenDigitMobileNumberSenders() {
        val message = "Rs.500.00 debited from a/c XX4321 at SWIGGY"
        val phoneResult = extractFinancialTransaction("+919876543210", message)
        assertFalse(phoneResult.is_valid_transaction)
        assertTrue(phoneResult.rejection_reason?.contains("Invalid") == true)

        val localResult = extractFinancialTransaction("9876543210", message)
        assertFalse(localResult.is_valid_transaction)

        val validBulkResult = extractFinancialTransaction("HDFCBK", message)
        assertTrue(validBulkResult.is_valid_transaction)
    }

    @Test
    fun rejectsOtpMessages() {
        val otp1 = extractFinancialTransaction("HDFCBK", "Your OTP for transaction of Rs 500 at Swiggy is 432190. Do not share this with anyone.")
        assertFalse(otp1.is_valid_transaction)
        assertEquals("OTP message", otp1.rejection_reason)

        val otp2 = extractFinancialTransaction("SBIUPI", "123456 is your One Time Password (OTP) for SBI Bank. Never share your OTP.")
        assertFalse(otp2.is_valid_transaction)
        assertEquals("OTP message", otp2.rejection_reason)
    }

    @Test
    fun rejectsBillReminders() {
        val bill1 = extractFinancialTransaction("HDFCBK", "Statement generated for HDFC Bank Credit Card XX4321. Minimum amount due is Rs.1,200 due on 15-Aug-2026.")
        assertFalse(bill1.is_valid_transaction)
        assertEquals("Bill Reminder", bill1.rejection_reason)

        val bill2 = extractFinancialTransaction("SBICRD", "Payment due for your SBI Card XX8888. Total amount due Rs.4,500 due on 20-Aug.")
        assertFalse(bill2.is_valid_transaction)
        assertEquals("Bill Reminder", bill2.rejection_reason)
    }

    @Test
    fun rejectsSpamAndPromotions() {
        val loan = extractFinancialTransaction("AXISBK", "Congratulations! Pre-approved personal loan approved of Rs 5,00,000. Apply now!")
        assertFalse(loan.is_valid_transaction)
        assertEquals("Promotional", loan.rejection_reason)

        val coupon = extractFinancialTransaction("VK-OFFERS", "Use coupon SAVE50 to get 50% discount on your next order.")
        assertFalse(coupon.is_valid_transaction)
        assertEquals("Promotional", coupon.rejection_reason)

        val rummy = extractFinancialTransaction("AD-RUMMY", "Play Rummy online and win Rs 10,000 real cash today!")
        assertFalse(rummy.is_valid_transaction)
        assertEquals("Promotional", rummy.rejection_reason)
    }

    @Test
    fun rejectsLimitOnlyUpdates() {
        val limit = extractFinancialTransaction("HDFCBK", "Your available credit limit is Rs. 1,50,000 on Card XX8888.")
        assertFalse(limit.is_valid_transaction)
        assertEquals("Limit Update", limit.rejection_reason)
    }

    @Test
    fun extractsValidCreditCardDebitTransaction() {
        val sms = "Rs.1299.00 spent on HDFC Bank Card XX8888 at FLIPKART.COM on 21-08-26. Avl Limit: INR 85,000.00"
        val result = extractFinancialTransaction("HDFCBK", sms)
        assertTrue(result.is_valid_transaction)
        assertNull(result.rejection_reason)

        val data = result.transaction_data
        assertNotNull(data)
        assertEquals("HDFC Bank", data.institution)
        assertEquals("CREDIT_CARD", data.instrument_type)
        assertEquals("DEBIT", data.transaction_type)
        assertEquals(1299.0, data.amount)
        assertEquals("INR", data.currency)
        assertEquals("8888", data.account_identifier)
        assertEquals("Flipkart", data.merchant)
        assertEquals(85000.0, data.available_balance)
    }

    @Test
    fun extractsValidUpiDebitTransaction() {
        val notif = "You paid Rs.249 to Swiggy using Google Pay UPI"
        val result = extractFinancialTransaction("Google Pay", notif)
        assertTrue(result.is_valid_transaction)

        val data = result.transaction_data
        assertNotNull(data)
        assertEquals("Google Pay", data.institution)
        assertEquals("UPI_APP", data.instrument_type)
        assertEquals("DEBIT", data.transaction_type)
        assertEquals(249.0, data.amount)
        assertEquals("Swiggy", data.merchant)
    }

    @Test
    fun extractsValidBankAccountCreditTransaction() {
        val sms = "INR 80,000.00 credited to A/c XX4321 on 01-Aug-2026 by Salary transfer. Avl Bal INR 1,20,500.00"
        val result = extractFinancialTransaction("SBIINB", sms)
        assertTrue(result.is_valid_transaction)

        val data = result.transaction_data
        assertNotNull(data)
        assertEquals("SBI", data.institution)
        assertEquals("BANK_ACCOUNT", data.instrument_type)
        assertEquals("CREDIT", data.transaction_type)
        assertEquals(80000.0, data.amount)
        assertEquals("4321", data.account_identifier)
        assertEquals(120500.0, data.available_balance)
    }

    @Test
    fun extractsJsonConformingToSchema() {
        val sms = "Rs.500.00 debited from a/c XX4321 at SWIGGY. Avl Bal Rs.8,000.00"
        val json = extractFinancialTransactionJson("HDFCBK", sms)
        assertTrue(json.contains("\"is_valid_transaction\": true"))
        assertTrue(json.contains("\"institution\": \"HDFC Bank\""))
        assertTrue(json.contains("\"amount\": 500.0"))
        assertTrue(json.contains("\"currency\": \"INR\""))
    }

    @Test
    fun defaultsSmsToSpecificCardByLast4OrInstitutionOrSingleCard() {
        val shopping = Category(id = "cat_shop", name = "Shopping", icon = "🛍️", color = "#ff9900", keywords = listOf("flipkart"))
        val hdfcCard = PaymentSource(id = "card_hdfc", name = "HDFC Regalia", kind = "credit_card", last4 = "8888")
        val iciciCard = PaymentSource(id = "card_icici", name = "ICICI Amazon Pay", kind = "credit_card", last4 = "2222")
        val sbiBank = PaymentSource(id = "bank_sbi", name = "SBI Savings", kind = "bank", last4 = "4321")

        val sources = listOf(hdfcCard, iciciCard, sbiBank)

        // 1. Matches HDFC Card by last 4 digits
        val sms1 = "Rs.1299.00 spent on Card XX8888 at FLIPKART"
        val parsed1 = parseInboxMessage(sms1, "HDFCBK", listOf(shopping), sources)
        assertNotNull(parsed1)
        assertEquals("card_hdfc", parsed1.sourceId)

        // 2. Matches ICICI Card by institution when last4 is absent
        val sms2 = "Rs.2499.00 spent on your ICICI Credit Card at FLIPKART"
        val parsed2 = parseInboxMessage(sms2, "ICICIB", listOf(shopping), sources)
        assertNotNull(parsed2)
        assertEquals("card_icici", parsed2.sourceId)

        // 3. Defaults to single card when only 1 credit card is registered
        val singleCardSources = listOf(hdfcCard, sbiBank)
        val sms3 = "Rs.750.00 spent on your credit card at FLIPKART"
        val parsed3 = parseInboxMessage(sms3, "SMS", listOf(shopping), singleCardSources)
        assertNotNull(parsed3)
        assertEquals("card_hdfc", parsed3.sourceId)
    }

    @Test
    fun defaultsSmsToSpecificBankByLast4OrInstitutionOrSingleBank() {
        val food = Category(id = "cat_food", name = "Food", icon = "🍔", color = "#ff5500", keywords = listOf("swiggy"))
        val sbiBank = PaymentSource(id = "bank_sbi", name = "SBI Savings A/c", kind = "bank", last4 = "4321")
        val hdfcBank = PaymentSource(id = "bank_hdfc", name = "HDFC Salary A/c", kind = "bank", last4 = "9999")
        val hdfcCard = PaymentSource(id = "card_hdfc", name = "HDFC Regalia", kind = "credit_card", last4 = "8888")

        val sources = listOf(sbiBank, hdfcBank, hdfcCard)

        // 1. Matches SBI Bank by last 4
        val sms1 = "INR 350.00 debited from A/c XX4321 at SWIGGY"
        val parsed1 = parseInboxMessage(sms1, "SBIINB", listOf(food), sources)
        assertNotNull(parsed1)
        assertEquals("bank_sbi", parsed1.sourceId)

        // 2. Matches HDFC Bank by institution when debiting from bank account
        val sms2 = "INR 1200.00 debited from HDFC Bank account at SWIGGY"
        val parsed2 = parseInboxMessage(sms2, "HDFCBK", listOf(food), sources)
        assertNotNull(parsed2)
        assertEquals("bank_hdfc", parsed2.sourceId)

        // 3. Defaults UPI/debit to single bank when only 1 bank is registered
        val singleBankSources = listOf(sbiBank, hdfcCard)
        val sms3 = "Rs.200.00 debited via UPI at SWIGGY"
        val parsed3 = parseInboxMessage(sms3, "Google Pay", listOf(food), singleBankSources)
        assertNotNull(parsed3)
        assertEquals("bank_sbi", parsed3.sourceId)
    }

    @Test
    fun amountParserIgnoresAvailableBalancePrecedingTransactionAmount() {
        val food = Category(id = "cat_food", name = "Food", icon = "🍔", color = "#ff5500", keywords = listOf("swiggy"))
        val sbiBank = PaymentSource(id = "bank_sbi", name = "SBI Savings A/c", kind = "bank", last4 = "1234")
        val msg = "Avl Bal: Rs. 15,000.00. Account XX1234 debited for Rs. 500.00 at Swiggy"
        val parsed = parseInboxMessage(msg, "SBIINB", listOf(food), listOf(sbiBank))
        assertNotNull(parsed)
        assertEquals(500.0, parsed.amount)
        assertEquals("expense", parsed.type)
        assertEquals("Swiggy", parsed.note)
    }

    @Test
    fun amountParserIgnoresCreditLimitPrecedingSpentAmount() {
        val shopping = Category(id = "cat_shop", name = "Shopping", icon = "🛍️", color = "#ff5500", keywords = listOf("flipkart"))
        val card = PaymentSource(id = "card_hdfc", name = "HDFC Regalia", kind = "credit_card", last4 = "8888")
        val msg = "Your available credit limit is Rs. 1,50,000. Rs. 1,299.00 spent on Card XX8888 at FLIPKART"
        val parsed = parseInboxMessage(msg, "HDFCBK", listOf(shopping), listOf(card))
        assertNotNull(parsed)
        assertEquals(1299.0, parsed.amount)
        assertEquals("expense", parsed.type)
        assertEquals("Flipkart", parsed.note)
    }
}
