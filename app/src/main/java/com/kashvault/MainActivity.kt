package com.kashvault

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.kashvault.app.App
import com.kashvault.auth.AndroidAppContext
import com.kashvault.auth.DriveAuthorizationHost
import com.kashvault.backup.BackupCancelledException
import com.kashvault.backup.BackupDocumentHost
import com.kashvault.inbox.InboxPermissionHost
import com.kashvault.inbox.SmsScanWorkSchedulerHost
import com.kashvault.profile.ImagePickerHost
import com.kashvault.reminder.ACTION_OPEN_INBOX
import com.kashvault.reminder.EXTRA_OPEN_INBOX
import com.kashvault.reminder.InboxOpenRequest
import com.kashvault.reminder.NotificationPermissionHost
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi

class MainActivity : FragmentActivity(), BackupDocumentHost, DriveAuthorizationHost, InboxPermissionHost, NotificationPermissionHost, ImagePickerHost, SmsScanWorkSchedulerHost {
    private var pendingCreateBytes: ByteArray? = null
    private var pendingCreateResult: CompletableDeferred<Boolean>? = null
    private var pendingOpenResult: CompletableDeferred<ByteArray?>? = null
    private var pendingDriveResult: CompletableDeferred<com.kashvault.auth.GoogleAuthorizationGrant>? = null
    private var pendingSmsResult: CompletableDeferred<Boolean>? = null
    private var pendingNotificationResult: CompletableDeferred<Boolean>? = null
    private var pendingImageResult: CompletableDeferred<ByteArray?>? = null

    private val createDatabaseLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val bytes = pendingCreateBytes
        val deferred = pendingCreateResult
        pendingCreateBytes = null
        pendingCreateResult = null
        if (uri == null || bytes == null) {
            deferred?.complete(false)
            return@registerForActivityResult
        }
        val ok = runCatching {
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
        }.getOrDefault(false)
        deferred?.complete(ok)
    }

    private val openDatabaseLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val deferred = pendingOpenResult
        pendingOpenResult = null
        if (uri == null) {
            deferred?.complete(null)
            return@registerForActivityResult
        }
        val bytes = runCatching {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        deferred?.complete(bytes)
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        val deferred = pendingImageResult
        pendingImageResult = null
        if (uri == null) {
            deferred?.complete(null)
            return@registerForActivityResult
        }
        val bytes = runCatching {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        deferred?.complete(bytes)
    }

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        pendingSmsResult?.complete(grants[Manifest.permission.READ_SMS] == true)
        pendingSmsResult = null
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        pendingNotificationResult?.complete(granted)
        pendingNotificationResult = null
    }

    private val driveAuthorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val deferred = pendingDriveResult
        pendingDriveResult = null
        if (result.resultCode != Activity.RESULT_OK) {
            deferred?.completeExceptionally(BackupCancelledException())
            return@registerForActivityResult
        }
        try {
            val auth = Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(result.data)
            deferred?.complete(auth.toGrant())
        } catch (error: Exception) {
            deferred?.completeExceptionally(error)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidAppContext.application = applicationContext
        AndroidAppContext.activity = this
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        setContent {
            App()
        }
        openInboxIfRequested(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openInboxIfRequested(intent)
    }

    override fun onResume() {
        super.onResume()
        AndroidAppContext.activity = this
    }

    override fun onDestroy() {
        if (AndroidAppContext.activity === this) {
            AndroidAppContext.activity = null
        }
        super.onDestroy()
    }

    override fun launchCreateDatabase(
        suggestedName: String,
        bytes: ByteArray,
        result: CompletableDeferred<Boolean>,
    ) {
        pendingCreateBytes = bytes
        pendingCreateResult = result
        createDatabaseLauncher.launch(suggestedName)
    }

    override fun launchOpenDatabase(result: CompletableDeferred<ByteArray?>) {
        pendingOpenResult = result
        openDatabaseLauncher.launch(
            arrayOf(
                "application/octet-stream",
                "application/vnd.sqlite3",
                "application/x-sqlite3",
                "*/*",
            ),
        )
    }

    override fun launchPickImage(result: CompletableDeferred<ByteArray?>) {
        pendingImageResult = result
        pickImageLauncher.launch("image/*")
    }

    override fun requestSmsPermission(result: CompletableDeferred<Boolean>) {
        pendingSmsResult?.complete(false)
        if (checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            pendingSmsResult = null
            result.complete(true)
            return
        }
        pendingSmsResult = result
        smsPermissionLauncher.launch(
            arrayOf(Manifest.permission.READ_SMS),
        )
    }

    override fun requestPostNotificationsPermission(result: CompletableDeferred<Boolean>) {
        pendingNotificationResult?.complete(false)
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            pendingNotificationResult = null
            result.complete(true)
            return
        }

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            pendingNotificationResult = null
            result.complete(true)
            return
        }
        pendingNotificationResult = result
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun setPeriodicSmsScanEnabled(enabled: Boolean) {
        com.kashvault.sms.PeriodicSmsScanScheduler.setEnabled(applicationContext, enabled)
    }

    private fun openInboxIfRequested(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_INBOX, false) == true || intent?.action == ACTION_OPEN_INBOX) {
            InboxOpenRequest.request()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun launchDriveAuthorization(clientId: String, result: CompletableDeferred<String>) {
        val grantResult = CompletableDeferred<com.kashvault.auth.GoogleAuthorizationGrant>()
        grantResult.invokeOnCompletion { error ->
            if (error != null) {
                result.completeExceptionally(error)
            } else {
                val grant = grantResult.getCompleted()
                result.complete(grant.accessToken)
            }
        }
        launchGoogleAuthorization(
            clientId,
            listOf("https://www.googleapis.com/auth/drive.appdata"),
            grantResult,
        )
    }

    override fun launchGoogleAuthorization(
        clientId: String,
        scopes: List<String>,
        result: CompletableDeferred<com.kashvault.auth.GoogleAuthorizationGrant>,
    ) {
        pendingDriveResult = result
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(scopes.map { Scope(it) })
            .build()
        Identity.getAuthorizationClient(this)
            .authorize(request)
            .addOnSuccessListener { authResult ->
                if (authResult.hasResolution()) {
                    val pendingIntent = authResult.pendingIntent
                    if (pendingIntent == null) {
                        result.completeExceptionally(IllegalStateException("Google permission prompt was missing."))
                        pendingDriveResult = null
                        return@addOnSuccessListener
                    }
                    driveAuthorizationLauncher.launch(IntentSenderRequest.Builder(pendingIntent).build())
                } else {
                    pendingDriveResult = null
                    try {
                        result.complete(authResult.toGrant())
                    } catch (error: Exception) {
                        result.completeExceptionally(error)
                    }
                }
            }
            .addOnFailureListener { error ->
                pendingDriveResult = null
                result.completeExceptionally(error)
            }
    }
}

private fun com.google.android.gms.auth.api.identity.AuthorizationResult.toGrant():
    com.kashvault.auth.GoogleAuthorizationGrant {
    val account = runCatching { toGoogleSignInAccount() }.getOrNull()
    val token = accessToken?.takeIf { it.isNotBlank() }.orEmpty()
    val userId = account?.id?.takeIf { it.isNotBlank() }
    if (token.isBlank() && userId.isNullOrBlank()) {
        throw IllegalStateException("Google did not return an access token.")
    }
    return com.kashvault.auth.GoogleAuthorizationGrant(
        accessToken = token,
        userId = userId,
        email = account?.email?.takeIf { it.isNotBlank() },
        displayName = account?.displayName?.takeIf { it.isNotBlank() },
        imageUrl = account?.photoUrl?.toString()?.takeIf { it.isNotBlank() },
        idToken = account?.idToken?.takeIf { it.isNotBlank() },
    )
}
