package com.kashvault.data

import com.kashvault.ai.normalizeGeminiModel
import com.kashvault.auth.hashVaultPin
import com.kashvault.auth.isValidVaultPin
import com.kashvault.auth.newVaultPinSalt
import com.kashvault.auth.overlayGoogleSession
import com.kashvault.auth.vaultPinMatches
import com.kashvault.backup.DatabaseBackupException
import com.kashvault.backup.assertSqliteDatabase
import com.kashvault.backup.deleteFileIfExists
import com.kashvault.backup.fileExists
import com.kashvault.backup.listFileNames
import com.kashvault.backup.readFileBytes
import com.kashvault.backup.writeFileBytes
import com.kashvault.db.DatabaseDriverFactory
import com.kashvault.db.KashVaultDatabase
import com.kashvault.db.executePragma
import com.kashvault.db.toDomain
import com.kashvault.domain.*
import com.kashvault.inbox.bestInboxDuplicate
import com.kashvault.inbox.DEFAULT_SMS_SCAN_KEYWORDS
import com.kashvault.inbox.defaultSourceTags
import com.kashvault.inbox.duplicateInboxIdsToRemove
import com.kashvault.inbox.expenseMatchesInboxPurchase
import com.kashvault.inbox.inboxDedupKeys
import com.kashvault.inbox.inboxItemDedupKeys
import com.kashvault.inbox.inboxMerchantHint
import com.kashvault.inbox.merchantQuality
import com.kashvault.inbox.learnedCategoryKeywords
import com.kashvault.inbox.learnedSourceKeywords
import com.kashvault.inbox.messageContainsScanKeyword
import com.kashvault.inbox.parseInboxMessage
import com.kashvault.reminder.parseInboxReminderSettings
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FinanceRepository(private val driverFactory: DatabaseDriverFactory) {
    private val json = Json { ignoreUnknownKeys = true }

    private val dbLock = Any()
    private var driverInternal: SqlDriver? = null
    private var databaseInternal: KashVaultDatabase? = null

    private val driver: SqlDriver
        get() {
            if (driverInternal == null) {
                synchronized(dbLock) {
                    if (driverInternal == null) {
                        val ms = kotlin.system.measureTimeMillis { driverInternal = driverFactory.createDriver() }
                        println("[Startup] created SqlDriver in ${ms}ms")
                    }
                }
            }
            return driverInternal!!
        }

    private val database: KashVaultDatabase
        get() {
            if (databaseInternal == null) {
                synchronized(dbLock) {
                    if (databaseInternal == null) {
                        val ms = kotlin.system.measureTimeMillis { databaseInternal = KashVaultDatabase(driver) }
                        println("[Startup] created KashVaultDatabase in ${ms}ms")
                    }
                }
            }
            return databaseInternal!!
        }

    private inline fun <T> withDb(block: () -> T): T = synchronized(dbLock, block)

    suspend fun initialize() {
        initializeBlocking()
    }

    fun ensureReady() = initializeBlocking()

    private fun ensureGoogleSessionTable() {
        driver.execute(
            null,
            """
            CREATE TABLE IF NOT EXISTS google_session (
                id TEXT NOT NULL PRIMARY KEY,
                user_id TEXT NOT NULL,
                email TEXT,
                display_name TEXT,
                image_url TEXT,
                access_token TEXT,
                refresh_token TEXT,
                id_token TEXT,
                expires_at TEXT
            )
            """.trimIndent(),
            0,
        )
        runCatching {
            driver.execute(null, "ALTER TABLE google_session ADD COLUMN refresh_token TEXT", 0).value
        }
    }

    private fun ensurePaymentSourceSchema() {
        driver.execute(
            null,
            """
            CREATE TABLE IF NOT EXISTS payment_sources (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                kind TEXT NOT NULL,
                last4 TEXT,
                icon TEXT NOT NULL,
                color TEXT NOT NULL,
                keywords_json TEXT
            )
            """.trimIndent(),
            0,
        )
        runCatching {
            driver.execute(null, "ALTER TABLE expenses ADD COLUMN source_id TEXT", 0).value
        }
        runCatching {
            driver.execute(null, "ALTER TABLE payment_sources ADD COLUMN keywords_json TEXT", 0).value
        }
        runCatching {
            driver.execute(null, "ALTER TABLE expenses ADD COLUMN destination_id TEXT", 0).value
        }
        runCatching {
            driver.execute(null, "ALTER TABLE payment_sources ADD COLUMN opening_balance REAL", 0).value
        }
        runCatching {
            driver.execute(null, "ALTER TABLE borrow_lend ADD COLUMN person_id TEXT", 0).value
        }
        runCatching {
            driver.execute(null, "ALTER TABLE investments ADD COLUMN source_id TEXT", 0).value
        }
        runCatching {
            driver.execute(null, "ALTER TABLE insurances ADD COLUMN source_id TEXT", 0).value
        }
        runCatching {
            driver.execute(null, "ALTER TABLE loans ADD COLUMN source_id TEXT", 0).value
        }
        runCatching {
            driver.execute(null, "ALTER TABLE expenses ADD COLUMN due_kind TEXT", 0).value
        }
        runCatching {
            driver.execute(null, "ALTER TABLE expenses ADD COLUMN due_entity_id TEXT", 0).value
        }
        runCatching {
            driver.execute(null, "ALTER TABLE payment_sources ADD COLUMN statement_due_date TEXT", 0).value
        }
        runCatching {
            driver.execute(null, "ALTER TABLE payment_sources ADD COLUMN last_processed_month TEXT", 0).value
        }
        runCatching {
            driver.execute(null, "ALTER TABLE payment_sources ADD COLUMN pay_from_source_id TEXT", 0).value
        }
        runCatching {
            driver.execute(null, "ALTER TABLE user_profile ADD COLUMN image_path TEXT", 0).value
        }
        driver.execute(
            null,
            """
            CREATE TABLE IF NOT EXISTS recurring_bills (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                amount REAL NOT NULL,
                category_id TEXT NOT NULL,
                frequency TEXT NOT NULL,
                due_date TEXT NOT NULL,
                last_processed_month TEXT,
                source_id TEXT,
                type TEXT NOT NULL
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE IF NOT EXISTS people (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                phone TEXT
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE IF NOT EXISTS inbox_items (
                id TEXT NOT NULL PRIMARY KEY,
                captured_at TEXT NOT NULL,
                channel TEXT NOT NULL,
                sender TEXT NOT NULL,
                body TEXT NOT NULL,
                amount REAL NOT NULL,
                type TEXT NOT NULL,
                suggested_category_id TEXT,
                suggested_source_id TEXT,
                note TEXT,
                status TEXT NOT NULL,
                fingerprint TEXT NOT NULL,
                matched_category_tag TEXT,
                matched_source_tag TEXT
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE IF NOT EXISTS inbox_seen (
                fingerprint TEXT NOT NULL PRIMARY KEY,
                seen_at TEXT NOT NULL
            )
            """.trimIndent(),
            0,
        )
        collapseDuplicateInboxItems()
        runCatching {
            driver.execute(
                null,
                "CREATE UNIQUE INDEX IF NOT EXISTS inbox_items_fingerprint ON inbox_items(fingerprint)",
                0,
            )
        }
        backfillInboxSeen()
        runCatching { database.kashVaultQueries.clearProcessedInboxBodies() }
    }

    private fun collapseDuplicateInboxItems() {
        val items = runCatching {
            database.kashVaultQueries.selectAllInboxItems().executeAsList().map { it.toDomain() }
        }.getOrDefault(emptyList())
        if (items.isEmpty()) return
        val removeIds = duplicateInboxIdsToRemove(items)
        items.forEach { item ->
            markInboxSeen(inboxItemDedupKeys(item), item.capturedAt)
        }
        removeIds.forEach { id ->
            database.kashVaultQueries.deleteInboxItem(id)
        }
    }

    private fun backfillInboxSeen() {
        if (database.kashVaultQueries.getAppMeta("inbox_seen_backfill").executeAsOneOrNull() == "1") return
        database.kashVaultQueries.selectAllInboxItems().executeAsList().map { it.toDomain() }.forEach { item ->
            markInboxSeen(inboxItemDedupKeys(item), item.capturedAt)
        }
        database.kashVaultQueries.setAppMeta("inbox_seen_backfill", "1")
    }

    private fun hasSeenInbox(keys: List<String>): Boolean =
        keys.any { key ->
            database.kashVaultQueries.selectInboxSeen(key).executeAsOneOrNull() != null ||
                database.kashVaultQueries.selectInboxByFingerprint(key).executeAsOneOrNull() != null
        }

    private fun markInboxSeen(keys: List<String>, seenAt: String) {
        keys.distinct().filter { it.isNotBlank() }.forEach { key ->
            database.kashVaultQueries.insertInboxSeen(key, seenAt)
        }
    }

    private fun ensureBuiltinCategories() {
        val existing = database.kashVaultQueries.selectAllCategories().executeAsList().map { it.toDomain() }
        val byId = existing.associateBy { it.id }
        missingBuiltinCategories(byId.keys).forEach { category ->
            database.kashVaultQueries.insertCategory(
                id = category.id,
                name = category.name,
                icon = category.icon,
                color = category.color,
                type = category.type,
                keywords_json = category.keywords?.let { json.encodeToString(it) },
            )
        }
        existing.forEach { category ->
            val refreshed = refreshedBuiltinCategory(category) ?: return@forEach
            saveCategory(refreshed)
        }
        mergeDuplicateCategories()
    }

    private fun mergeDuplicateCategories() {
        val existing = database.kashVaultQueries.selectAllCategories().executeAsList().map { it.toDomain() }
        categoryMerges(existing).forEach { merge ->
            database.kashVaultQueries.reassignExpenseCategory(merge.keepId, merge.dropId)
            database.kashVaultQueries.reassignBudgetCategory(merge.keepId, merge.dropId)
            database.kashVaultQueries.reassignInboxCategory(merge.keepId, merge.dropId)
            runCatching { database.kashVaultQueries.reassignBillCategory(merge.keepId, merge.dropId) }
            database.kashVaultQueries.deleteCategory(merge.dropId)
        }
    }

    private fun seedInboxTags() {
        val defaults = INITIAL_CATEGORIES.associate { it.id to it.keywords.orEmpty() }
        database.kashVaultQueries.selectAllCategories().executeAsList().map { it.toDomain() }.forEach { category ->
            if (category.keywords.isNullOrEmpty()) {
                val seeded = defaults[category.id]
                if (!seeded.isNullOrEmpty()) {
                    saveCategory(category.copy(keywords = seeded))
                }
            }
        }
        database.kashVaultQueries.selectAllPaymentSources().executeAsList().map { it.toDomain() }.forEach { source ->
            if (source.keywords.isNullOrEmpty()) {
                savePaymentSource(source.copy(keywords = defaultSourceTags(source.name, source.last4)))
            }
        }
    }

    private fun ensureDefaults() {
        ensureBuiltinCategories()
        seedInboxTags()

        if (database.kashVaultQueries.selectEmergencyFund().executeAsOneOrNull() == null) {
            database.kashVaultQueries.insertEmergencyFund(
                id = Constants.SINGLETON_ID,
                target_months = 6L,
                monthly_requirement = 0.0,
                current_saved = 0.0,
            )
        }

        if (database.kashVaultQueries.selectSyncSettings().executeAsOneOrNull() == null) {
            database.kashVaultQueries.insertSyncSettings(
                id = Constants.SINGLETON_ID,
                auto_sync = 0L,
                last_backup_at = null,
                last_restore_at = null,
                last_sync_at = null,
                last_local_change_at = null,
                cloud_backup_at = null,
            )
        }

        if (database.kashVaultQueries.selectAppSettings().executeAsOneOrNull() == null) {
            database.kashVaultQueries.insertAppSettings(id = Constants.SINGLETON_ID, local_mode = 0L)
        }
        seedSmsScanKeywords()
        ensurePeopleFromLending()
    }

    private fun seedSmsScanKeywords() {
        if (database.kashVaultQueries.getAppMeta(Constants.META_SMS_SCAN_KEYWORDS).executeAsOneOrNull() != null) return
        database.kashVaultQueries.setAppMeta(
            Constants.META_SMS_SCAN_KEYWORDS,
            keywordsToText(DEFAULT_SMS_SCAN_KEYWORDS),
        )
    }

    fun getSmsScanKeywords(): List<String> {
        val stored = database.kashVaultQueries.getAppMeta(Constants.META_SMS_SCAN_KEYWORDS).executeAsOneOrNull()
        return if (stored == null) DEFAULT_SMS_SCAN_KEYWORDS else keywordsFromText(stored)
    }

    fun saveSmsScanKeywords(keywords: List<String>) {
        database.kashVaultQueries.setAppMeta(Constants.META_SMS_SCAN_KEYWORDS, keywordsToText(keywords))
    }

    fun getAccountKey(): String {
        return database.kashVaultQueries.getAppMeta(Constants.META_ACCOUNT_KEY).executeAsOneOrNull()?.trim().orEmpty()
    }

    fun setAccountKey(key: String) {
        database.kashVaultQueries.setAppMeta(Constants.META_ACCOUNT_KEY, key.trim())
    }

    fun parkedVaultPath(accountKey: String): String {
        val path = driverFactory.databaseFilePath()
        val slash = path.lastIndexOf('/')
        val dir = if (slash >= 0) path.substring(0, slash) else "."
        val safe = accountKey.trim().ifEmpty { Constants.ACCOUNT_KEY_LOCAL }
        return "$dir/kashvault.park.$safe.db"
    }

    fun hasParkedVault(accountKey: String): Boolean {
        val path = parkedVaultPath(accountKey)
        if (!fileExists(path)) return false
        return runCatching {
            val bytes = readFileBytes(path)
            assertSqliteDatabase(bytes)
            true
        }.getOrDefault(false)
    }

    fun parkCurrentVault(accountKey: String) {
        val bytes = exportDatabaseBytes()
        writeFileBytes(parkedVaultPath(accountKey), bytes)
    }

    fun deleteParkedVault(accountKey: String) {
        val path = parkedVaultPath(accountKey)
        deleteFileIfExists(path)
    }

    fun restoreParkedVault(accountKey: String): Boolean {
        if (!hasParkedVault(accountKey)) return false
        restoreDatabaseBytes(readFileBytes(parkedVaultPath(accountKey)))
        return true
    }

    fun resetActiveVaultToEmpty() {
        val clientId = database.kashVaultQueries.getAppMeta("google_web_client_id").executeAsOneOrNull().orEmpty()
        val path = driverFactory.databaseFilePath()
        runCatching { driver.close() }
        deleteFileIfExists(path)
        deleteFileIfExists("$path-wal")
        deleteFileIfExists("$path-shm")
        reopen()
        initializeBlocking()
        setAuthChoice(null)
        setLocalMode(false)
        clearGoogleSession()
        if (clientId.isNotBlank()) saveGoogleWebClientId(clientId)
    }

    fun eraseAllLocalData() {
        val clientId = database.kashVaultQueries.getAppMeta("google_web_client_id").executeAsOneOrNull().orEmpty()
        val path = requireDatabasePath()
        val slash = path.lastIndexOf('/')
        val directory = if (slash >= 0) path.substring(0, slash) else "."
        val databaseName = path.substring(slash + 1)
        val files = listFileNames(directory).filter { name ->
            name == databaseName ||
                name == "$databaseName.pre-restore" ||
                name.startsWith("$databaseName-") ||
                (name.startsWith("kashvault.park.") &&
                    (name.endsWith(".db") || name.contains(".db-"))) ||
                name == "profile.jpg"
        }
        driver.close()
        val failures = mutableListOf<String>()
        try {
            files.forEach { name ->
                val filePath = "$directory/$name"
                try {
                    deleteFileIfExists(filePath)
                    if (fileExists(filePath)) {
                        throw IllegalStateException("Deletion did not remove the file.")
                    }
                } catch (_: Exception) {
                    failures += name
                }
            }
        } finally {
            reopen()
            initializeBlocking()
        }
        setAuthChoice(null)
        setLocalMode(false)
        clearGoogleSession()
        if (clientId.isNotBlank()) saveGoogleWebClientId(clientId)
        if (failures.isNotEmpty()) {
            throw IllegalStateException("Could not erase all local vault files: ${failures.joinToString(", ")}.")
        }
    }

    fun hasUserFinanceData(): Boolean {
        return database.kashVaultQueries.selectAllExpenses().executeAsList().isNotEmpty() ||
            database.kashVaultQueries.selectAllPaymentSources().executeAsList().isNotEmpty() ||
            database.kashVaultQueries.selectAllBorrowLend().executeAsList().isNotEmpty() ||
            runCatching { database.kashVaultQueries.selectAllRecurringBills().executeAsList() }.getOrDefault(emptyList()).isNotEmpty() ||
            database.kashVaultQueries.selectAllLoans().executeAsList().isNotEmpty() ||
            database.kashVaultQueries.selectAllInvestments().executeAsList().isNotEmpty() ||
            database.kashVaultQueries.selectAllInsurances().executeAsList().isNotEmpty() ||
            database.kashVaultQueries.selectAllSavingsGoals().executeAsList().isNotEmpty()
    }

    fun getInboxReminderSettings(): InboxReminderSettings = withDb { inboxReminderSettingsLocked() }

    fun saveInboxReminderSettings(settings: InboxReminderSettings) = withDb {
        val hour = settings.hour.coerceIn(0, 23)
        val minute = settings.minute.coerceIn(0, 59)
        database.kashVaultQueries.setAppMeta(Constants.META_INBOX_REMINDER_ENABLED, if (settings.enabled) "1" else "0")
        database.kashVaultQueries.setAppMeta(Constants.META_INBOX_REMINDER_HOUR, hour.toString())
        database.kashVaultQueries.setAppMeta(Constants.META_INBOX_REMINDER_MINUTE, minute.toString())
    }

    fun getGeminiModel(): String = withDb { geminiModelLocked() }

    fun saveGeminiModel(model: String) = withDb {
        database.kashVaultQueries.setAppMeta(Constants.META_GEMINI_MODEL, normalizeGeminiModel(model))
    }

    private fun geminiModelLocked(): String =
        normalizeGeminiModel(database.kashVaultQueries.getAppMeta(Constants.META_GEMINI_MODEL).executeAsOneOrNull())

    private fun vaultLockEnabledLocked(): Boolean =
        !database.kashVaultQueries.getAppMeta(Constants.META_VAULT_PIN_HASH).executeAsOneOrNull().isNullOrBlank()

    fun vaultLockEnabled(): Boolean = withDb { vaultLockEnabledLocked() }

    fun setVaultPin(pin: String) = withDb {
        require(isValidVaultPin(pin)) { "PIN must be 4 to 8 digits" }
        val salt = newVaultPinSalt()
        database.kashVaultQueries.setAppMeta(Constants.META_VAULT_PIN_SALT, salt)
        database.kashVaultQueries.setAppMeta(Constants.META_VAULT_PIN_HASH, hashVaultPin(pin, salt))
    }

    fun verifyVaultPin(pin: String): Boolean = withDb {
        val salt = database.kashVaultQueries.getAppMeta(Constants.META_VAULT_PIN_SALT).executeAsOneOrNull().orEmpty()
        val hash = database.kashVaultQueries.getAppMeta(Constants.META_VAULT_PIN_HASH).executeAsOneOrNull().orEmpty()
        vaultPinMatches(pin, salt, hash)
    }

    fun clearVaultPin() = withDb {
        database.kashVaultQueries.setAppMeta(Constants.META_VAULT_PIN_HASH, "")
        database.kashVaultQueries.setAppMeta(Constants.META_VAULT_PIN_SALT, "")
    }

    fun countPendingInbox(): Int = withDb {
        database.kashVaultQueries.countPendingInbox().executeAsOne().toInt()
    }

    private fun inboxReminderSettingsLocked(): InboxReminderSettings = parseInboxReminderSettings(
        enabledRaw = database.kashVaultQueries.getAppMeta(Constants.META_INBOX_REMINDER_ENABLED).executeAsOneOrNull(),
        hourRaw = database.kashVaultQueries.getAppMeta(Constants.META_INBOX_REMINDER_HOUR).executeAsOneOrNull(),
        minuteRaw = database.kashVaultQueries.getAppMeta(Constants.META_INBOX_REMINDER_MINUTE).executeAsOneOrNull(),
    )

    private fun ensurePeopleFromLending() {
        val existing = database.kashVaultQueries.selectAllPeople().executeAsList().map { it.toDomain() }
        val byName = existing.associateBy { it.name.trim().lowercase() }.toMutableMap()
        val logs = database.kashVaultQueries.selectAllBorrowLend().executeAsList().map { it.toDomain() }
        logs.forEach { item ->
            val key = item.person.trim().lowercase()
            if (key.isEmpty()) return@forEach
            val person = byName[key] ?: Person(
                id = "person_${key.hashCode()}_${item.person.hashCode()}",
                name = item.person.trim(),
            ).also { created ->
                database.kashVaultQueries.insertPerson(created.id, created.name, created.phone)
                byName[key] = created
            }
            if (item.personId.isNullOrBlank()) {
                database.kashVaultQueries.insertBorrowLend(
                    id = item.id,
                    person = item.person,
                    amount = item.amount,
                    type = item.type,
                    settled = if (item.settled) 1L else 0L,
                    timestamp = item.timestamp,
                    note = item.note,
                    person_id = person.id,
                )
            }
        }
    }

    fun loadSnapshot(): FinanceSnapshot = withDb {
        val categories = database.kashVaultQueries.selectAllCategories().executeAsList().map { it.toDomain() }
        val paymentSources = database.kashVaultQueries.selectAllPaymentSources().executeAsList().map { it.toDomain() }
        val expenses = database.kashVaultQueries.selectAllExpenses().executeAsList().map { it.toDomain() }
        val borrowLend = database.kashVaultQueries.selectAllBorrowLend().executeAsList().map { it.toDomain() }
        val budgets = database.kashVaultQueries.selectAllBudgets().executeAsList().map { it.toDomain() }
        val investments = database.kashVaultQueries.selectAllInvestments().executeAsList().map { it.toDomain() }
        val insurances = database.kashVaultQueries.selectAllInsurances().executeAsList().map { it.toDomain() }
        val loans = database.kashVaultQueries.selectAllLoans().executeAsList().map { it.toDomain() }
        val savingsGoals = database.kashVaultQueries.selectAllSavingsGoals().executeAsList().map { it.toDomain() }
        val emergencyFund = database.kashVaultQueries.selectEmergencyFund().executeAsOneOrNull()?.toDomain()
            ?: EmergencyFund(6, 0.0, 0.0)
        val userProfile = database.kashVaultQueries.selectUserProfile().executeAsOneOrNull()?.toDomain()
        val syncSettings = database.kashVaultQueries.selectSyncSettings().executeAsOneOrNull()?.toDomain()
            ?: SyncSettings(autoSync = false)
        val isLocalMode = database.kashVaultQueries.selectAppSettings().executeAsOneOrNull()?.local_mode == 1L
        val googleSession = database.kashVaultQueries.selectGoogleSession().executeAsOneOrNull()?.toDomain()
        val storedChoice = database.kashVaultQueries.getAppMeta("auth_choice").executeAsOneOrNull()?.takeIf { it.isNotBlank() }
        val authChoice = storedChoice ?: if (isLocalMode) "local" else null
        val googleWebClientId = database.kashVaultQueries.getAppMeta("google_web_client_id").executeAsOneOrNull().orEmpty()
        val lastPaymentSourceId = database.kashVaultQueries.getAppMeta("last_source_id").executeAsOneOrNull()?.takeIf { it.isNotBlank() }
        val inboxItems = run {
            val pending = database.kashVaultQueries.selectPendingInbox().executeAsList().map { it.toDomain() }
            val drop = duplicateInboxIdsToRemove(pending)
            if (drop.isEmpty()) pending else pending.filter { it.id !in drop }
        }
        val people = database.kashVaultQueries.selectAllPeople().executeAsList().map { it.toDomain() }
        val recurringBills = runCatching {
            database.kashVaultQueries.selectAllRecurringBills().executeAsList().map { it.toDomain() }
        }.getOrDefault(emptyList())
        val setupCompleted = database.kashVaultQueries.getAppMeta("setup_completed").executeAsOneOrNull() == "1"

        return FinanceSnapshot(
            categories = sortedCategoriesForDisplay(if (categories.isEmpty()) INITIAL_CATEGORIES else categories),
            paymentSources = paymentSources,
            expenses = expenses,
            borrowLend = borrowLend,
            budgets = budgets,
            investments = investments,
            insurances = insurances,
            loans = loans,
            savingsGoals = savingsGoals,
            emergencyFund = emergencyFund,
            userProfile = userProfile,
            syncSettings = syncSettings,
            isLocalMode = isLocalMode,
            googleSession = googleSession,
            authChoice = authChoice,
            googleWebClientId = googleWebClientId,
            lastPaymentSourceId = lastPaymentSourceId,
            inboxItems = inboxItems,
            people = people,
            recurringBills = recurringBills,
            setupCompleted = setupCompleted,
            smsScanKeywords = getSmsScanKeywords(),
            inboxReminder = inboxReminderSettingsLocked(),
            geminiModel = geminiModelLocked(),
            vaultLockEnabled = vaultLockEnabledLocked(),
            useDeviceLock = database.kashVaultQueries.getAppMeta("use_device_lock").executeAsOneOrNull() == "1",
        )
    }

    fun addExpense(expense: Expense) = withDb {
        database.kashVaultQueries.insertExpense(
            id = expense.id,
            amount = expense.amount,
            category_id = expense.categoryId,
            timestamp = expense.timestamp,
            note = expense.note,
            type = expense.type,
            is_auto_generated = if (expense.isAutoGenerated == true) 1L else 0L,
            source_id = expense.sourceId,
            destination_id = expense.destinationId,
            due_kind = expense.dueKind,
            due_entity_id = expense.dueEntityId,
        )
    }

    fun updateExpense(expense: Expense) = addExpense(expense)

    fun deleteExpense(id: String) = withDb {
        database.kashVaultQueries.deleteExpense(id)
    }

    fun savePaymentSource(source: PaymentSource) {
        database.kashVaultQueries.insertPaymentSource(
            id = source.id,
            name = source.name,
            kind = source.kind,
            last4 = source.last4,
            icon = source.icon,
            color = source.color,
            keywords_json = source.keywords?.let { json.encodeToString(it) },
            opening_balance = source.openingBalance,
            statement_due_date = source.statementDueDate,
            last_processed_month = source.lastProcessedMonth,
            pay_from_source_id = source.payFromSourceId,
        )
    }

    fun deletePaymentSource(id: String) {
        database.kashVaultQueries.deletePaymentSource(id)
    }

    fun setLastPaymentSourceId(id: String?) {
        database.kashVaultQueries.setAppMeta("last_source_id", id.orEmpty())
    }

    fun ingestCapturedMessage(
        channel: String,
        sender: String,
        body: String,
        capturedAt: String = kotlinx.datetime.Clock.System.now().toString(),
    ): InboxItem? {
        ensurePaymentSourceSchema()
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null
        if (channel == Constants.INBOX_SMS || channel == Constants.INBOX_NOTIFICATION) {
            if (!messageContainsScanKeyword(sender, trimmed, getSmsScanKeywords())) return null
        }
        val parsed = parseInboxMessage(
            body = trimmed,
            sender = sender,
            categories = database.kashVaultQueries.selectAllCategories().executeAsList().map { it.toDomain() }
                .ifEmpty { INITIAL_CATEGORIES },
            sources = database.kashVaultQueries.selectAllPaymentSources().executeAsList().map { it.toDomain() },
        )
        val merchant = parsed?.let {
            inboxMerchantHint(trimmed, it.note, it.matchedCategoryTag, sender)
        } ?: inboxMerchantHint(trimmed, sender = sender)
        val keys = inboxDedupKeys(
            sender = sender,
            body = trimmed,
            amount = parsed?.amount,
            type = parsed?.type,
            capturedAt = capturedAt,
            merchant = merchant,
        )
        if (keys.isEmpty()) return null
        if (hasSeenInbox(keys)) return null
        if (parsed != null) {
            val existingInbox = bestInboxDuplicate(
                amount = parsed.amount,
                type = parsed.type,
                capturedAt = capturedAt,
                merchant = merchant,
                candidates = database.kashVaultQueries.selectAllInboxItems().executeAsList().map { it.toDomain() },
            )
            val alreadyBooked = existingInbox?.status == Constants.INBOX_ACCEPTED ||
                database.kashVaultQueries.selectAllExpenses().executeAsList().map { it.toDomain() }
                    .any { expenseMatchesInboxPurchase(it, parsed.amount, parsed.type, capturedAt, merchant) }
            if (alreadyBooked) {
                markInboxSeen(keys, capturedAt)
                return null
            }
            if (existingInbox != null && existingInbox.status == Constants.INBOX_PENDING) {
                val existingMerchant = inboxMerchantHint(
                    existingInbox.body,
                    existingInbox.note,
                    existingInbox.matchedCategoryTag,
                    existingInbox.sender,
                )
                if (merchantQuality(merchant) > merchantQuality(existingMerchant)) {
                    database.kashVaultQueries.deleteInboxItem(existingInbox.id)
                } else {
                    markInboxSeen(keys, capturedAt)
                    return null
                }
            }
        }
        markInboxSeen(keys, capturedAt)
        if (parsed == null) return null
        val item = InboxItem(
            id = "${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}_${keys.first().hashCode()}",
            capturedAt = capturedAt,
            channel = channel,
            sender = sender,
            body = trimmed,
            amount = parsed.amount,
            type = parsed.type,
            suggestedCategoryId = parsed.categoryId,
            suggestedSourceId = parsed.sourceId,
            note = parsed.note,
            status = Constants.INBOX_PENDING,
            fingerprint = keys.first(),
            matchedCategoryTag = parsed.matchedCategoryTag,
            matchedSourceTag = parsed.matchedSourceTag,
        )
        return runCatching {
            persistInbox(item)
            item
        }.getOrNull()
    }

    fun updateInboxSuggestion(id: String, categoryId: String?, sourceId: String?, note: String?) {
        database.kashVaultQueries.updateInboxSuggestion(categoryId, sourceId, note, id)
    }

    fun acceptInboxItem(
        id: String,
        timestamp: String? = null,
        amount: Double? = null,
        type: String? = null,
        categoryId: String? = null,
        sourceId: String? = null,
        note: String? = null,
    ): Expense? {
        val item = database.kashVaultQueries.selectPendingInbox().executeAsList()
            .map { it.toDomain() }
            .firstOrNull { it.id == id }
            ?: return null
        val resolvedType = type?.takeIf { it.isNotBlank() } ?: item.type
        val resolvedAmount = amount?.takeIf { it.isFinite() && it > 0 } ?: item.amount
        val fallbackCategoryId = categoryId?.takeIf { it.isNotBlank() } ?: preferredExpenseCategoryId(
            categories = database.kashVaultQueries.selectAllCategories().executeAsList()
                .map { it.toDomain() }
                .ifEmpty { INITIAL_CATEGORIES },
            suggestedId = item.suggestedCategoryId,
            type = resolvedType,
        )
        val resolvedSourceId = sourceId?.takeIf { it.isNotBlank() } ?: item.suggestedSourceId
        val resolvedNote = note?.takeIf { it.isNotBlank() } ?: item.note
        val expense = Expense(
            id = "${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}_inbox",
            amount = com.kashvault.util.roundCurrency(resolvedAmount),
            categoryId = fallbackCategoryId,
            timestamp = timestamp ?: item.capturedAt,
            note = resolvedNote,
            type = resolvedType,
            isAutoGenerated = true,
            sourceId = resolvedSourceId,
        )
        addExpense(expense)
        database.kashVaultQueries.updateInboxStatus(Constants.INBOX_ACCEPTED, id)
        database.kashVaultQueries.clearInboxBody(id)
        if (!expense.sourceId.isNullOrBlank()) setLastPaymentSourceId(expense.sourceId)
        return expense
    }

    fun markInboxAccepted(id: String) {
        database.kashVaultQueries.updateInboxStatus(Constants.INBOX_ACCEPTED, id)
        database.kashVaultQueries.clearInboxBody(id)
    }

    fun dismissInboxItem(id: String) {
        database.kashVaultQueries.updateInboxStatus(Constants.INBOX_DISMISSED, id)
        database.kashVaultQueries.clearInboxBody(id)
    }

    fun restoreInboxPending(id: String) {
        database.kashVaultQueries.updateInboxStatus(Constants.INBOX_PENDING, id)
    }

    private fun persistInbox(item: InboxItem) {
        database.kashVaultQueries.insertInboxItem(
            id = item.id,
            captured_at = item.capturedAt,
            channel = item.channel,
            sender = item.sender,
            body = item.body,
            amount = item.amount,
            type = item.type,
            suggested_category_id = item.suggestedCategoryId,
            suggested_source_id = item.suggestedSourceId,
            note = item.note,
            status = item.status,
            fingerprint = item.fingerprint,
            matched_category_tag = item.matchedCategoryTag,
            matched_source_tag = item.matchedSourceTag,
        )
    }

    fun addBorrowLend(item: BorrowLend) {
        database.kashVaultQueries.insertBorrowLend(
            id = item.id,
            person = item.person,
            amount = item.amount,
            type = item.type,
            settled = if (item.settled) 1L else 0L,
            timestamp = item.timestamp,
            note = item.note,
            person_id = item.personId,
        )
    }

    fun deleteBorrowLend(id: String) {
        database.kashVaultQueries.deleteBorrowLend(id)
    }

    fun saveRecurringBills(bills: List<RecurringBill>) {
        database.kashVaultQueries.deleteAllRecurringBills()
        bills.forEach { bill ->
            database.kashVaultQueries.insertRecurringBill(
                id = bill.id,
                name = bill.name,
                amount = bill.amount,
                category_id = bill.categoryId,
                frequency = bill.frequency,
                due_date = bill.dueDate,
                last_processed_month = bill.lastProcessedMonth,
                source_id = bill.sourceId,
                type = bill.type,
            )
        }
    }

    fun setSetupCompleted(completed: Boolean) {
        database.kashVaultQueries.setAppMeta("setup_completed", if (completed) "1" else "0")
    }

    fun saveBudgets(budgets: List<Budget>) {
        database.kashVaultQueries.deleteAllBudgets()
        budgets.forEach { budget ->
            database.kashVaultQueries.insertBudget(category_id = budget.categoryId, budget_limit = budget.limit)
        }
    }

    fun saveInvestments(investments: List<Investment>) {
        database.kashVaultQueries.deleteAllInvestments()
        investments.forEach { inv ->
            database.kashVaultQueries.insertInvestment(
                id = inv.id,
                name = inv.name,
                amount = inv.amount,
                invested_amount = inv.investedAmount,
                type = inv.type,
                frequency = inv.frequency,
                timestamp = inv.timestamp,
                due_date = inv.dueDate,
                last_processed_month = inv.lastProcessedMonth,
                source_id = inv.sourceId,
            )
        }
    }

    fun saveInsurances(insurances: List<Insurance>) {
        database.kashVaultQueries.deleteAllInsurances()
        insurances.forEach { ins ->
            database.kashVaultQueries.insertInsurance(
                id = ins.id,
                name = ins.name,
                premium = ins.premium,
                frequency = ins.frequency,
                due_date = ins.dueDate,
                last_processed_month = ins.lastProcessedMonth,
                source_id = ins.sourceId,
            )
        }
    }

    fun saveLoans(loans: List<Loan>) {
        database.kashVaultQueries.deleteAllLoans()
        loans.forEach { loan ->
            database.kashVaultQueries.insertLoan(
                id = loan.id,
                name = loan.name,
                total_amount = loan.totalAmount,
                annual_interest_rate = loan.annualInterestRate,
                tenure_months = loan.tenureMonths?.toLong(),
                emi = loan.emi,
                remaining_amount = loan.remainingAmount,
                remaining_interest = loan.remainingInterest,
                outstanding_total = loan.outstandingTotal,
                total_prepaid = loan.totalPrepaid,
                emis_paid = loan.emisPaid?.toLong(),
                due_date = loan.dueDate,
                start_date = loan.startDate,
                last_processed_month = loan.lastProcessedMonth,
                principal_per_emi = loan.principalPerEmi,
                interest_per_emi = loan.interestPerEmi,
                source_id = loan.sourceId,
            )
        }
    }

    fun saveSavingsGoals(goals: List<SavingsGoal>) {
        database.kashVaultQueries.deleteAllSavingsGoals()
        goals.forEach { goal ->
            database.kashVaultQueries.insertSavingsGoal(
                id = goal.id,
                name = goal.name,
                target = goal.target,
                current = goal.current,
                completed = if (goal.completed == true) 1L else 0L,
            )
        }
    }

    fun setUseDeviceLock(enabled: Boolean) = withDb {
        database.kashVaultQueries.setAppMeta("use_device_lock", if (enabled) "1" else "0")
    }

    fun saveEmergencyFund(fund: EmergencyFund) {
        database.kashVaultQueries.insertEmergencyFund(
            id = Constants.SINGLETON_ID,
            target_months = fund.targetMonths.toLong(),
            monthly_requirement = fund.monthlyRequirement,
            current_saved = fund.currentSaved,
        )
    }

    fun learnCaptureFromInbox(item: InboxItem) {
        val categories = database.kashVaultQueries.selectAllCategories().executeAsList().map { it.toDomain() }
        val sources = database.kashVaultQueries.selectAllPaymentSources().executeAsList().map { it.toDomain() }
        val category = categories.find { it.id == item.suggestedCategoryId }
        if (category != null) {
            learnedCategoryKeywords(category, item)?.let { keywords ->
                saveCategory(category.copy(keywords = keywords))
            }
        }
        val source = sources.find { it.id == item.suggestedSourceId } ?: return
        learnedSourceKeywords(source, item)?.let { keywords ->
            savePaymentSource(source.copy(keywords = keywords))
        }
    }

    fun saveCategory(category: Category) {
        database.kashVaultQueries.insertCategory(
            id = category.id,
            name = category.name,
            icon = category.icon,
            color = category.color,
            type = category.type,
            keywords_json = category.keywords?.let { json.encodeToString(it) },
        )
    }

    fun deleteCategory(id: String) {
        database.kashVaultQueries.deleteCategory(id)
    }

    fun savePerson(person: Person) {
        database.kashVaultQueries.insertPerson(id = person.id, name = person.name, phone = person.phone)
    }

    fun deletePerson(id: String) {
        database.kashVaultQueries.deletePerson(id)
    }

    fun saveProfile(profile: UserProfile) = withDb {
        database.kashVaultQueries.insertUserProfile(
            id = profile.id,
            name = profile.name,
            email = profile.email,
            mobile = profile.mobile,
            gemini_api_key = profile.geminiApiKey,
            theme = profile.theme,
            image_path = profile.imagePath,
        )
    }

    fun profileImageFilePath(): String {
        val dbPath = driverFactory.databaseFilePath()
        val slash = dbPath.lastIndexOf('/')
        val dir = if (slash >= 0) dbPath.substring(0, slash) else "."
        return "$dir/profile.jpg"
    }

    fun setLocalMode(enabled: Boolean) {
        database.kashVaultQueries.insertAppSettings(
            id = Constants.SINGLETON_ID,
            local_mode = if (enabled) 1L else 0L,
        )
    }

    fun setAuthChoice(choice: String?) {
        database.kashVaultQueries.setAppMeta("auth_choice", choice.orEmpty())
    }

    fun saveGoogleWebClientId(clientId: String) {
        database.kashVaultQueries.setAppMeta("google_web_client_id", clientId.trim())
    }

    fun saveGoogleSession(session: GoogleSession) {
        database.kashVaultQueries.insertGoogleSession(
            id = Constants.SINGLETON_ID,
            user_id = session.userId,
            email = session.email,
            display_name = session.displayName,
            image_url = session.imageUrl,
            access_token = session.accessToken,
            refresh_token = session.refreshToken,
            id_token = session.idToken,
            expires_at = session.expiresAtEpochMs?.toString(),
        )
    }

    fun clearGoogleSession() {
        database.kashVaultQueries.deleteGoogleSession()
    }

    fun exportDatabaseBytes(): ByteArray {
        checkpoint()
        val path = requireDatabasePath()
        val bytes = readFileBytes(path)
        assertSqliteDatabase(bytes)
        return bytes
    }

    fun restoreDatabaseBytes(bytes: ByteArray) {
        assertSqliteDatabase(bytes)
        val session = database.kashVaultQueries.selectGoogleSession().executeAsOneOrNull()?.toDomain()
        val authChoice = database.kashVaultQueries.getAppMeta("auth_choice").executeAsOneOrNull()
        val clientId = database.kashVaultQueries.getAppMeta("google_web_client_id").executeAsOneOrNull().orEmpty()
        val path = requireDatabasePath()
        val safetyCopy = "$path.pre-restore"
        checkpoint()
        writeFileBytes(safetyCopy, readFileBytes(path))
        driver.close()
        try {
            writeFileBytes(path, bytes)
            deleteFileIfExists("$path-wal")
            deleteFileIfExists("$path-shm")
            reopen()
            val integrity = integrityCheck()
            if (!integrity.equals("ok", ignoreCase = true)) {
                throw IllegalStateException("Restored database failed integrity check ($integrity).")
            }
            database.kashVaultQueries.selectAllCategories().executeAsList()
            initializeBlocking()
            val parkedSession = database.kashVaultQueries.selectGoogleSession().executeAsOneOrNull()?.toDomain()
            overlayGoogleSession(session, parkedSession)?.let { saveGoogleSession(it) }
            if (!authChoice.isNullOrBlank()) setAuthChoice(authChoice)
            if (clientId.isNotBlank()) saveGoogleWebClientId(clientId)
            markRestore()
        } catch (error: Exception) {
            runCatching { driver.close() }
            writeFileBytes(path, readFileBytes(safetyCopy))
            deleteFileIfExists("$path-wal")
            deleteFileIfExists("$path-shm")
            reopen()
            if (error is DatabaseBackupException) throw error
            throw DatabaseBackupException(
                error.message ?: "Could not restore the SQLite vault.",
                error,
            )
        } finally {
            deleteFileIfExists(safetyCopy)
        }
    }

    fun markBackup() {
        val now = kotlinx.datetime.Clock.System.now().toString()
        val current = database.kashVaultQueries.selectSyncSettings().executeAsOneOrNull()?.toDomain()
            ?: SyncSettings(autoSync = false)
        persistSync(current.copy(lastBackupAt = now, cloudBackupAt = now, lastSyncAt = now))
    }

    private fun markRestore() {
        val now = kotlinx.datetime.Clock.System.now().toString()
        val current = database.kashVaultQueries.selectSyncSettings().executeAsOneOrNull()?.toDomain()
            ?: SyncSettings(autoSync = false)
        persistSync(current.copy(lastRestoreAt = now, lastSyncAt = now))
    }

    private fun persistSync(settings: SyncSettings) {
        database.kashVaultQueries.insertSyncSettings(
            id = Constants.SINGLETON_ID,
            auto_sync = if (settings.autoSync) 1L else 0L,
            last_backup_at = settings.lastBackupAt,
            last_restore_at = settings.lastRestoreAt,
            last_sync_at = settings.lastSyncAt,
            last_local_change_at = settings.lastLocalChangeAt,
            cloud_backup_at = settings.cloudBackupAt,
        )
    }

    private var isInitialized = false

    private fun initializeBlocking() = withDb {
        if (isInitialized) return@withDb
        val version = runCatching {
            database.kashVaultQueries.getSchemaVersion().executeAsOneOrNull()?.toIntOrNull() ?: 0
        }.getOrDefault(0)

        if (version < Constants.SCHEMA_VERSION) {
            database.transaction {
                ensureGoogleSessionTable()
                ensurePaymentSourceSchema()
                ensureDefaults()
                database.kashVaultQueries.setSchemaVersion(Constants.SCHEMA_VERSION.toString())
            }
        } else {
            val hasCategories = database.kashVaultQueries.selectAllCategories().executeAsList().isNotEmpty()
            if (!hasCategories) {
                database.transaction {
                    ensureDefaults()
                }
            }
        }
        isInitialized = true
    }

    private fun reopen() {
        synchronized(dbLock) {
            isInitialized = false
            driverInternal = driverFactory.createDriver()
            databaseInternal = KashVaultDatabase(driverInternal!!)
        }
    }

    private fun checkpoint() {
        driver.executePragma("PRAGMA wal_checkpoint(TRUNCATE);")
    }

    private fun integrityCheck(): String {
        return driver.executeQuery(
            identifier = null,
            sql = "PRAGMA integrity_check;",
            mapper = { cursor ->
                val status = if (cursor.next().value) cursor.getString(0) ?: "unknown" else "unknown"
                QueryResult.Value(status)
            },
            parameters = 0,
        ).value
    }

    private fun requireDatabasePath(): String {
        val fromSqlite = queryDatabaseListPath()
        val candidates = listOf(fromSqlite, driverFactory.databaseFilePath())
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != ":memory:" }
            .distinct()
        return candidates.firstOrNull { fileExists(it) }
            ?: candidates.firstOrNull()
            ?: throw IllegalStateException("Could not locate the on-device SQLite file.")
    }

    private fun queryDatabaseListPath(): String {
        return driver.executeQuery(
            identifier = null,
            sql = "PRAGMA database_list;",
            mapper = { cursor ->
                var file = ""
                while (cursor.next().value) {
                    if (cursor.getString(1) == "main") {
                        file = cursor.getString(2).orEmpty()
                    }
                }
                QueryResult.Value(file)
            },
            parameters = 0,
        ).value
    }
}
