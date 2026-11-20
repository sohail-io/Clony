package com.clony

import android.content.ClipData
import android.content.ClipboardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.PendingIntent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.IconCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.contract.ActivityResultContracts
import com.clony.ui.theme.ClonyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.core.CloneBuildPropsStore
import top.niunaijun.blackbox.core.CloneDeviceModelStore
import top.niunaijun.blackbox.core.CloneHardwareIdentityStore
import top.niunaijun.blackbox.core.CloneFingerprintStore
import top.niunaijun.blackbox.core.CloneNetworkIdentityStore
import top.niunaijun.blackbox.fake.service.VirtualDeviceIdentity
import top.niunaijun.blackbox.fake.frameworks.BAccountManager
import java.util.Locale
import java.util.UUID

private const val RUNTIME_USER_ID_START = 1
private const val MAX_COPIES_PER_APP = 10
internal const val ACTION_OPEN_CLONE = "com.clony.action.OPEN_CLONE"
internal const val EXTRA_CLONE_PACKAGE = "com.clony.extra.CLONE_PACKAGE"
internal const val EXTRA_CLONE_USER_ID = "com.clony.extra.CLONE_USER_ID"
internal const val ACTION_SHORTCUT_PINNED = "com.clony.action.SHORTCUT_PINNED"
internal const val EXTRA_SHORTCUT_LABEL = "com.clony.extra.SHORTCUT_LABEL"
private const val SHORTCUT_PREFERENCES = "clone_shortcuts"
private const val TRACE_TAG = "ClonyTrace"

private enum class CloneFeatureScreen {
    CLONE_SETTINGS,
    IDENTITY_OPERATIONS,
    NETWORK_RADIO_IDENTITY,
    CONNECTION_OPERATOR_INFO,
    DEVICE_PROFILE_HARDWARE
}

private fun packageNameDrafts(
    app: AppTile,
    count: Int,
    occupiedPackageNames: Set<String>,
    firstCopyNumber: Int,
    existingDrafts: List<String> = emptyList()
): List<String> {
    val drafts = existingDrafts.take(count).toMutableList()
    var copyNumber = firstCopyNumber
    while (drafts.size < count) {
        var candidate = ClonePackageNameStore.suggestedName(app.packageName, copyNumber++)
        while (candidate in occupiedPackageNames || candidate in drafts) {
            candidate = ClonePackageNameStore.suggestedName(app.packageName, copyNumber++)
        }
        drafts += candidate
    }
    return drafts
}

data class AppTile(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    val virtualUserId: Int? = null,
    val copyNumber: Int? = null,
    val copyCount: Int = 0,
    val hasSplitApks: Boolean = false,
    val launcherPackage: String? = null,
    val launcherInstalled: Boolean = false,
    val customPackageName: String? = null
) {
    val isCloned: Boolean get() = virtualUserId != null
    val displayName: String get() = if (isCloned) "$label ${copyNumber ?: 1}" else label
}

class MainActivity : ComponentActivity() {
    private val networkIdentitySettingsMutex = Mutex()
    private val hardwareIdentitySettingsMutex = Mutex()
    private var launcherQueue = LauncherInstallQueue()
    private var waitingForInstallPermission = false
    private val pendingHomeShortcutPins = mutableListOf<AppTile>()
    private var homeShortcutPinInFlight = false
    private val launcherPermissionResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        waitingForInstallPermission = false
        if (canInstallLaunchers()) {
            advanceLauncherQueue()
        } else {
            launcherQueue.complete(false)
            showInfo("Copies were created. Allow installs from TikViral, then choose Install launcher to add their app icons.")
        }
    }
    private val launcherInstallResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val current = launcherQueue.active
        val installed = current != null && CloneLaunchers.isInstalled(this, current)
        launcherQueue.complete(installed)
        refreshApps()
        if (installed) {
            if (launcherQueue.pending.isEmpty()) showInfo("Copy app icons installed. Each one opens its assigned copy.")
            else advanceLauncherQueue()
        } else {
            showInfo("The copy was created, but its app icon wasn't installed. Use Install launcher to try again.")
        }
    }

    private val appsState = mutableStateOf<List<AppTile>>(emptyList())
    private val containerAccountsState = mutableStateOf<Map<Int, List<String>>>(emptyMap())
    private val runtimeReadyState = mutableStateOf(false)
    private val messageState = mutableStateOf<String?>(null)
    private val busyPackageState = mutableStateOf<String?>(null)
    private val messageIsErrorState = mutableStateOf(false)
    private val retryPackageState = mutableStateOf<String?>(null)
    private val retryCopyCountState = mutableStateOf(1)
    private val retryPackageNamesState = mutableStateOf<List<String>>(emptyList())
    private var refreshJob: Job? = null
    private var cloneUpdateJob: Job? = null
    private var packageUpdateReceiverRegistered = false
    private val packageUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val updatedPackage = intent?.data?.schemeSpecificPart ?: return
            if (updatedPackage != packageName) syncOutdatedClones(updatedPackage)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val fromLegacyCloneShortcut = intent?.action == ACTION_OPEN_CLONE
        if (fromLegacyCloneShortcut) setTheme(android.R.style.Theme_NoDisplay)
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            launcherQueue = LauncherInstallQueue(
                savedInstanceState.getStringArrayList("launcher_pending") ?: arrayListOf(),
                savedInstanceState.getString("launcher_active")
            )
            waitingForInstallPermission = savedInstanceState.getBoolean("launcher_permission")
        }
        logLifecycle("onCreate")
        if (fromLegacyCloneShortcut) {
            Log.i(TRACE_TAG, "shortcut_dispatch stage=forward package=" + intent?.getStringExtra(EXTRA_CLONE_PACKAGE) +
                " userId=" + intent?.getIntExtra(EXTRA_CLONE_USER_ID, -1))
            forwardLegacyCloneShortcut(intent)
            finish()
            return
        }
        enableEdgeToEdge()
        refreshApps()
        setContent {
            ClonyTheme {
                ClonyHomeRedesigned(
                    apps = appsState.value,
                    containerAccounts = containerAccountsState.value,
                    message = messageState.value,
                    messageIsError = messageIsErrorState.value,
                    busyPackageName = busyPackageState.value,
                    retryPackageName = retryPackageState.value,
                    onMessageDismissed = {
                        messageState.value = null
                        messageIsErrorState.value = false
                        retryPackageState.value = null
                    },
                    onCopyDiagnostics = ::copyMessage,
                    onRetry = ::retryClone,
                    onRefresh = ::refreshApps,
                    onOpenClone = ::openClone,
                    onCreateCopies = ::createCopies,
                    onInstallIntoContainer = ::installIntoContainer,
                    onSaveBuildPropsTemplate = ::saveBuildPropsTemplate,
                    onSaveNetworkIdentitySettings = ::saveNetworkIdentitySettings,
                    onSaveHardwareIdentitySettings = ::saveHardwareIdentitySettings,
                    onSaveRecaptchaWorkaround = ::saveRecaptchaWorkaround,
                    onPinClone = { tile -> pinCloneShortcut(tile) },
                    onDeleteClone = ::deleteClone
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList("launcher_pending", ArrayList(launcherQueue.pending))
        outState.putString("launcher_active", launcherQueue.active)
        outState.putBoolean("launcher_permission", waitingForInstallPermission)
        super.onSaveInstanceState(outState)
    }

    private fun canInstallLaunchers() =
        android.os.Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()

    private fun advanceLauncherQueue() {
        if (waitingForInstallPermission || launcherQueue.active != null) return
        launcherQueue.pending.removeAll { CloneLaunchers.isInstalled(this, it) }
        if (launcherQueue.pending.isEmpty()) return
        try {
            if (!canInstallLaunchers()) {
                waitingForInstallPermission = true
                launcherPermissionResult.launch(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        android.net.Uri.parse("package:$packageName")
                    )
                )
                return
            }
            val next = launcherQueue.startNext { CloneLaunchers.isInstalled(this, it) } ?: return
            launcherInstallResult.launch(CloneLaunchers.installIntent(this, next))
        } catch (error: Exception) {
            waitingForInstallPermission = false
            launcherQueue.complete(false)
            showError("Copies were created, but their app icon setup couldn't start: ${error.message.orEmpty()}", null)
        }
    }

    private fun installLauncher(tile: AppTile) {
        val launcher = tile.launcherPackage
            ?: return showError("No app icon slot is available for this copy.", null)
        launcherQueue.add(listOf(launcher))
        advanceLauncherQueue()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        logLifecycle("onNewIntent", intent)
        if (intent.action == ACTION_OPEN_CLONE) {
            window.decorView.alpha = 0f
            forwardLegacyCloneShortcut(intent)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        logLifecycle("onResume")
        if (homeShortcutPinInFlight) {
            homeShortcutPinInFlight = false
            advanceHomeShortcutPins()
        }
        refreshApps()
        syncOutdatedClones()
    }

    override fun onStart() {
        super.onStart()
        logLifecycle("onStart")
        if (!packageUpdateReceiverRegistered) {
            val filter = IntentFilter(Intent.ACTION_PACKAGE_REPLACED).apply {
                addDataScheme("package")
            }
            ContextCompat.registerReceiver(
                this,
                packageUpdateReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            packageUpdateReceiverRegistered = true
        }
    }

    override fun onStop() {
        logLifecycle("onStop")
        if (packageUpdateReceiverRegistered) {
            unregisterReceiver(packageUpdateReceiver)
            packageUpdateReceiverRegistered = false
        }
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        logLifecycle("onPause")
    }

    override fun onDestroy() {
        logLifecycle("onDestroy")
        super.onDestroy()
    }

    private fun logLifecycle(event: String, sourceIntent: Intent? = intent) {
        Log.i(
            TRACE_TAG,
            "activity=" + event +
                " taskId=" + taskId +
                " action=" + sourceIntent?.action +
                " clonePackage=" + sourceIntent?.getStringExtra(EXTRA_CLONE_PACKAGE) +
                " cloneUserId=" + sourceIntent?.getIntExtra(EXTRA_CLONE_USER_ID, -1) +
                " finishing=" + isFinishing +
                " changingConfig=" + isChangingConfigurations +
                " elapsedMs=" + SystemClock.elapsedRealtime()
        )
    }

    private fun syncOutdatedClones(packageFilter: String? = null) {
        if (busyPackageState.value != null || cloneUpdateJob?.isActive == true) {
            Log.d(TRACE_TAG, "clone_sync skipped packageFilter=" + packageFilter +
                " busyPackage=" + busyPackageState.value +
                " updateJobActive=" + (cloneUpdateJob?.isActive == true))
            return
        }
        Log.i(TRACE_TAG, "clone_sync scheduled packageFilter=" + packageFilter)
        cloneUpdateJob = lifecycleScope.launch {
            updateOutdatedClones(packageFilter)
        }
    }

    private suspend fun updateOutdatedClones(packageFilter: String?): Boolean {
        if (busyPackageState.value != null) return false
        val updates = withContext(Dispatchers.IO) {
            ClonePackageUpdater.findOutdatedClones(this@MainActivity, packageFilter)
        }
        for (update in updates) {
            if (busyPackageState.value != null || !updateClonePackage(update)) return false
        }
        return true
    }

    private suspend fun updateClonePackage(update: ClonePackageUpdate): Boolean {
        val copyCount = update.userIds.size
        showProgress(
            update.packageName,
            "Updating ${update.label} for $copyCount ${if (copyCount == 1) "copy" else "copies"}…"
        )
        val outcome = ClonePackageUpdater.updateIfNeeded(this@MainActivity, update.packageName)
        if (outcome.error != null) {
            showError("Couldn't update ${update.label} for its clones: ${outcome.error}", null)
            return false
        }
        if (outcome.updated) {
            refreshApps()
            val version = outcome.versionName?.let { " $it" }.orEmpty()
            showInfo(
                "Updated ${outcome.label ?: update.label}$version across ${outcome.copyCount.coerceAtLeast(copyCount)} ${if (copyCount == 1) "copy" else "copies"}. Their separate app data was kept."
            )
        }
        return true
    }

    private fun refreshApps() {
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            val (tiles, runtimeReady, containerAccounts) = withContext(Dispatchers.IO) {
                val launcherApps = getSystemService(LauncherApps::class.java)
                val runtime = BlackBoxCore.get()
                val runtimeReady = runCatching { runtime.isServicesAvailable() }.getOrDefault(false)
                val virtualUsers = runCatching { runtime.getUsers().orEmpty() }.getOrDefault(emptyList())
                val containerAccounts = virtualUsers.associate { user ->
                    user.id to runCatching {
                        BAccountManager.get().getAccountsAsUser("com.google", user.id)
                            .orEmpty().map { it.name }
                    }.getOrDefault(emptyList())
                }
                val tiles = runCatching {
                    launcherApps.getActivityList(null, Process.myUserHandle())
                        .groupBy { it.applicationInfo.packageName }
                        .flatMap { (packageName, activities) ->
                            val activity = activities.firstOrNull() ?: return@flatMap emptyList()
                            if (packageName == this@MainActivity.packageName || LauncherRegistry.slot(packageName) != null) {
                                return@flatMap emptyList()
                            }
                            runCatching {
                                val label = activity.label?.toString()?.takeIf(String::isNotBlank) ?: packageName
                                val icon = activity.getIcon(0).toBitmap(128, 128).asImageBitmap()
                                val copyUserIds = virtualUsers.asSequence()
                                    .filter { user -> runtime.isInstalled(packageName, user.id) }
                                    .map { it.id }
                                    .sorted()
                                    .toList()
                                val hasSplitApks = !activity.applicationInfo.splitSourceDirs.isNullOrEmpty()
                                if (copyUserIds.isEmpty()) {
                                    listOf(
                                        AppTile(
                                            packageName = packageName,
                                            label = label,
                                            icon = icon,
                                            hasSplitApks = hasSplitApks
                                        )
                                    )
                                } else {
                                    copyUserIds.map { userId ->
                                        // Preserve launcher APKs installed by older versions. New copies
                                        // use Android's pinned shortcut so their icon and name match the app.
                                        val launcher = CloneLaunchers.existing(this@MainActivity, packageName, userId)
                                        val customPackageName = ClonePackageNameStore.read(
                                            this@MainActivity, packageName, userId
                                        )
                                        AppTile(
                                            packageName = packageName,
                                            label = label,
                                            icon = icon,
                                            virtualUserId = userId,
                                            launcherPackage = launcher,
                                            launcherInstalled = launcher != null && CloneLaunchers.isInstalled(this@MainActivity, launcher),
                                            copyNumber = CloneCopyNameStore.assign(
                                                this@MainActivity, packageName, userId, customPackageName
                                            ),
                                            copyCount = copyUserIds.size,
                                            hasSplitApks = hasSplitApks,
                                            customPackageName = customPackageName
                                        )
                                    }
                                }
                            }.getOrNull() ?: emptyList()
                        }
                        .sortedBy { it.label.lowercase(Locale.ROOT) }
                }.getOrDefault(emptyList())
                Triple(tiles, runtimeReady, containerAccounts)
            }
            appsState.value = tiles
            runtimeReadyState.value = runtimeReady
            containerAccountsState.value = containerAccounts
        }
    }

    private fun openClone(tile: AppTile) {
        val userId = tile.virtualUserId ?: return
        Log.i(TRACE_TAG, "clone_open tapped package=" + tile.packageName +
            " userId=" + userId + " copy=" + tile.copyNumber)
        if (busyPackageState.value != null) {
            Log.w(TRACE_TAG, "clone_open rejected reason=busy package=" + tile.packageName + " userId=" + userId +
                " busyPackage=" + busyPackageState.value)
            return
        }
        runCatching {
            startActivity(Intent(this, CloneShortcutActivity::class.java).apply {
                action = ACTION_OPEN_CLONE
                putExtra(EXTRA_CLONE_PACKAGE, tile.packageName)
                putExtra(EXTRA_CLONE_USER_ID, userId)
            })
        }.onSuccess {
            Log.i(TRACE_TAG, "clone_open stage=start_activity_accepted package=" + tile.packageName + " userId=" + userId)
        }.onFailure {
            Log.e(TRACE_TAG, "clone_open stage=start_activity_failed package=" + tile.packageName + " userId=" + userId, it)
        }
    }

    private fun forwardLegacyCloneShortcut(intent: Intent?) {
        if (intent?.action != ACTION_OPEN_CLONE) return
        val packageName = intent.getStringExtra(EXTRA_CLONE_PACKAGE) ?: return
        val userId = intent.getIntExtra(EXTRA_CLONE_USER_ID, -1)
        if (userId < RUNTIME_USER_ID_START) return
        startActivity(Intent(this, CloneShortcutActivity::class.java).apply {
            action = ACTION_OPEN_CLONE
            putExtra(EXTRA_CLONE_PACKAGE, packageName)
            putExtra(EXTRA_CLONE_USER_ID, userId)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        })
        overridePendingTransition(0, 0)
    }


    private fun pinCloneShortcut(tile: AppTile): Boolean {
        val userId = tile.virtualUserId ?: return false
        val pinSupported = ShortcutManagerCompat.isRequestPinShortcutSupported(this)
        Log.i(TRACE_TAG, "shortcut_pin stage=begin package=${tile.packageName} userId=$userId supported=$pinSupported")
        if (!pinSupported) {
            showError("The current launcher doesn't support adding home screen icons.", null)
            return false
        }

        val shortcutLabel = tile.displayName
        val shortcutId = "clone-${UUID.nameUUIDFromBytes("${tile.packageName}:$userId".toByteArray())}"
        // Use a dedicated transient task for the shortcut launch bridge.
        val launchIntent = Intent(this, CloneShortcutActivity::class.java).apply {
            action = ACTION_OPEN_CLONE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
            putExtra(EXTRA_CLONE_PACKAGE, tile.packageName)
            putExtra(EXTRA_CLONE_USER_ID, userId)
        }
        val shortcut = runCatching {
            androidx.core.content.pm.ShortcutInfoCompat.Builder(this, shortcutId)
                .setShortLabel(shortcutLabel.take(25))
                .setLongLabel(shortcutLabel)
                .setIcon(IconCompat.createWithBitmap(tile.icon.asAndroidBitmap()))
                .setActivity(ComponentName(this, CloneShortcutActivity::class.java))
                .setIntent(launchIntent)
                .build()
        }.getOrElse { error ->
            Log.e(TRACE_TAG, "shortcut_pin stage=build_failed package=${tile.packageName} userId=$userId", error)
            showError(error.message?.takeIf(String::isNotBlank) ?: "Couldn't create the home screen icon.", null)
            return false
        }
        val callbackIntent = Intent(this, ShortcutPinResultReceiver::class.java).apply {
            action = ACTION_SHORTCUT_PINNED
            putExtra(EXTRA_SHORTCUT_LABEL, shortcutLabel)
        }
        val callback = PendingIntent.getBroadcast(
            this,
            shortcutId.hashCode(),
            callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Publish the shortcut first so launcher implementations that resolve home-screen
        // shortcuts through LauncherApps can look up this ID when the user taps the pinned icon.
        runCatching {
            ShortcutManagerCompat.addDynamicShortcuts(this, listOf(shortcut))
        }.onSuccess {
            Log.i(TRACE_TAG, "shortcut_pin stage=dynamic_publish_ok package=${tile.packageName} userId=$userId shortcutId=$shortcutId")
        }.onFailure {
            Log.e(TRACE_TAG, "shortcut_pin stage=dynamic_publish_failed package=${tile.packageName} userId=$userId shortcutId=$shortcutId", it)
        }
        val alreadyPinned = runCatching {
            ShortcutManagerCompat.getShortcuts(this, ShortcutManagerCompat.FLAG_MATCH_PINNED)
                .any { it.id == shortcutId }
        }.onFailure {
            Log.e(TRACE_TAG, "shortcut_pin stage=query_pinned_failed package=${tile.packageName} userId=$userId shortcutId=$shortcutId", it)
        }.getOrDefault(false)
        Log.i(TRACE_TAG, "shortcut_pin stage=checked_existing package=${tile.packageName} userId=$userId shortcutId=$shortcutId alreadyPinned=$alreadyPinned")
        if (alreadyPinned) {
            showInfo("$shortcutLabel already has a home screen icon.")
            return false
        }
        val requested = runCatching {
            ShortcutManagerCompat.requestPinShortcut(this, shortcut, callback.intentSender)
        }.onFailure {
            Log.e(TRACE_TAG, "shortcut_pin stage=request_failed package=${tile.packageName} userId=$userId shortcutId=$shortcutId", it)
        }.getOrDefault(false)
        Log.i(TRACE_TAG, "shortcut_pin stage=request_result package=${tile.packageName} userId=$userId shortcutId=$shortcutId requested=$requested")
        if (requested) {
            val stored = getSharedPreferences(SHORTCUT_PREFERENCES, MODE_PRIVATE).edit()
                .putString(shortcutPreferenceKey(tile.packageName, userId), shortcutId)
                .commit()
            Log.i(TRACE_TAG, "shortcut_pin stage=preference_saved package=${tile.packageName} userId=$userId shortcutId=$shortcutId stored=$stored")
        } else {
            showError("The launcher couldn't add this copy icon.", null)
        }
        return requested
    }

    private fun queueHomeShortcutsForCopies(source: AppTile, userIds: List<Int>) {
        pendingHomeShortcutPins.clear()
        userIds.forEach { userId ->
            pendingHomeShortcutPins += source.copy(
                virtualUserId = userId,
                copyNumber = CloneCopyNameStore.assign(
                    this,
                    source.packageName,
                    userId,
                    ClonePackageNameStore.read(this, source.packageName, userId)
                )
            )
        }
        advanceHomeShortcutPins()
    }

    /** Ask for one home-screen pin at a time; returning from the launcher advances the queue. */
    private fun advanceHomeShortcutPins() {
        if (homeShortcutPinInFlight) return
        while (pendingHomeShortcutPins.isNotEmpty()) {
            val next = pendingHomeShortcutPins.removeAt(0)
            homeShortcutPinInFlight = true
            if (pinCloneShortcut(next)) return
            homeShortcutPinInFlight = false
        }
    }

    private fun deleteClone(tile: AppTile) {
        val userId = tile.virtualUserId ?: return
        if (busyPackageState.value != null) return
        val label = tile.displayName
        Log.i(TRACE_TAG, "clone_delete stage=begin package=${tile.packageName} userId=$userId label=$label")
        showProgress(tile.packageName, "Deleting $label and its app data…")
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val runtime = BlackBoxCore.get()
                    val installed = runtime.isInstalled(tile.packageName, userId)
                    Log.i(TRACE_TAG, "clone_delete stage=installed_check package=${tile.packageName} userId=$userId installed=$installed")
                    if (!installed) {
                        error("$label was already removed.")
                    }
                    Log.i(TRACE_TAG, "clone_delete stage=stop_begin package=${tile.packageName} userId=$userId")
                    runtime.stopPackage(tile.packageName, userId)
                    Log.i(TRACE_TAG, "clone_delete stage=stop_complete package=${tile.packageName} userId=$userId")
                    val otherPackagesRemain = appsState.value.any {
                        it.isCloned && it.virtualUserId == userId && it.packageName != tile.packageName
                    }
                    if (otherPackagesRemain) {
                        Log.i(TRACE_TAG, "clone_delete stage=uninstall_package_begin package=${tile.packageName} userId=$userId")
                        runtime.uninstallPackageAsUser(tile.packageName, userId)
                        Log.i(TRACE_TAG, "clone_delete stage=uninstall_package_complete package=${tile.packageName} userId=$userId")
                    } else {
                        Log.i(TRACE_TAG, "clone_delete stage=delete_user_begin package=${tile.packageName} userId=$userId")
                        runtime.deleteUser(userId)
                        Log.i(TRACE_TAG, "clone_delete stage=delete_user_complete package=${tile.packageName} userId=$userId")
                    }
                    runCatching {
                        top.niunaijun.blackbox.core.network.CloneProxyStore.delete(
                            this@MainActivity, tile.packageName, userId
                        )
                    }.onFailure {
                        Log.w(TRACE_TAG, "clone_delete stage=proxy_settings_cleanup_failed package=${tile.packageName} userId=$userId", it)
                    }
                    runCatching {
                        CloneFingerprintStore.write(this@MainActivity, tile.packageName, userId, null)
                    }.onFailure {
                        Log.w(TRACE_TAG, "clone_delete stage=fingerprint_cleanup_failed package=${tile.packageName} userId=$userId", it)
                    }
                    runCatching {
                        CloneDeviceModelStore.write(this@MainActivity, tile.packageName, userId, null)
                    }.onFailure {
                        Log.w(TRACE_TAG, "clone_delete stage=device_model_cleanup_failed package=${tile.packageName} userId=$userId", it)
                    }
                    runCatching {
                        CloneNetworkIdentityStore.delete(this@MainActivity, tile.packageName, userId)
                    }.onFailure {
                        Log.w(TRACE_TAG, "clone_delete stage=network_identity_cleanup_failed package=${tile.packageName} userId=$userId", it)
                    }
                    runCatching {
                        CloneHardwareIdentityStore.delete(this@MainActivity, tile.packageName, userId)
                    }.onFailure {
                        Log.w(TRACE_TAG, "clone_delete stage=hardware_identity_cleanup_failed package=${tile.packageName} userId=$userId", it)
                    }
                    runCatching {
                        CloneRecaptchaWorkaroundStore.delete(this@MainActivity, tile.packageName, userId)
                    }.onFailure {
                        Log.w(TRACE_TAG, "clone_delete stage=recaptcha_preference_cleanup_failed package=${tile.packageName} userId=$userId", it)
                    }
                    if (!CloneBuildPropsStore.delete(this@MainActivity, tile.packageName, userId)) {
                        Log.w(TRACE_TAG, "clone_delete stage=build_props_cleanup_failed package=${tile.packageName} userId=$userId")
                    }
                    if (!ClonePackageNameStore.delete(this@MainActivity, tile.packageName, userId)) {
                        Log.w(TRACE_TAG, "clone_delete stage=package_name_cleanup_failed package=${tile.packageName} userId=$userId")
                    }
                    if (!CloneCopyNameStore.remove(this@MainActivity, tile.packageName, userId)) {
                        Log.w(TRACE_TAG, "clone_delete stage=copy_name_cleanup_failed package=${tile.packageName} userId=$userId")
                    }
                    if (!VirtualDeviceIdentity.deleteAndroidId(this@MainActivity, tile.packageName, userId)) {
                        Log.w(TRACE_TAG, "clone_delete stage=android_id_cleanup_failed package=${tile.packageName} userId=$userId")
                    }
                    if (!VirtualDeviceIdentity.deleteTelephonyIds(this@MainActivity, tile.packageName, userId)) {
                        Log.w(TRACE_TAG, "clone_delete stage=telephony_id_cleanup_failed package=${tile.packageName} userId=$userId")
                    }
                }
                Log.i(TRACE_TAG, "clone_delete stage=shortcut_cleanup_begin package=${tile.packageName} userId=$userId")
                CloneShortcutCleanup.removeForClone(this@MainActivity, tile.packageName, userId)
                Log.i(TRACE_TAG, "clone_delete stage=shortcut_cleanup_complete package=${tile.packageName} userId=$userId")
                CloneLaunchers.remove(this@MainActivity, tile.packageName, userId)
                refreshApps()
                val launcherUninstallRequested = tile.launcherPackage?.let { launcherPackage ->
                    if (CloneLaunchers.isInstalled(this@MainActivity, launcherPackage)) {
                        runCatching {
                            startActivity(Intent(
                                Intent.ACTION_UNINSTALL_PACKAGE,
                                android.net.Uri.parse("package:$launcherPackage")
                            ).putExtra(Intent.EXTRA_RETURN_RESULT, true))
                        }.onFailure {
                            Log.w(TRACE_TAG, "clone_delete stage=launcher_uninstall_prompt_failed package=$launcherPackage", it)
                        }.isSuccess
                    } else {
                        false
                    }
                } ?: false
                showInfo(
                    if (launcherUninstallRequested) {
                        "Deleted $label and its app data. Its pinned Home screen shortcut is disabled. Confirm Android's prompt to remove its separate launcher icon."
                    } else {
                        "Deleted $label and its app data. Its pinned Home screen shortcut is disabled."
                    }
                )
            } catch (error: Throwable) {
                Log.e(TRACE_TAG, "clone_delete stage=failed package=${tile.packageName} userId=$userId label=$label", error)
                showError(error.message?.takeIf(String::isNotBlank) ?: "Couldn't delete $label.", null)
                refreshApps()
            }
        }
    }

    private fun saveFingerprint(tile: AppTile, fingerprint: String?) {
        val userId = tile.virtualUserId ?: return
        val value = fingerprint?.trim()?.takeIf(String::isNotEmpty)
        if (value != null && !CloneFingerprintStore.isValid(value)) {
            showError("Use printable characters with no spaces; fingerprints can be up to ${CloneFingerprintStore.MAX_LENGTH} characters.", null)
            return
        }

        showProgress(tile.packageName, "Saving the device fingerprint for ${tile.displayName}…")
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (!CloneFingerprintStore.write(this@MainActivity, tile.packageName, userId, value)) {
                        error("Couldn't save the fingerprint setting.")
                    }
                    BlackBoxCore.get().stopPackage(tile.packageName, userId)
                }
                showInfo(
                    if (value == null) "${tile.displayName} will use TikViral's default device fingerprint the next time it starts."
                    else "Saved the device fingerprint for ${tile.displayName}. It will be used the next time the copy starts."
                )
            } catch (error: Throwable) {
                Log.e(TRACE_TAG, "fingerprint_save failed package=${tile.packageName} userId=$userId", error)
                showError(error.message?.takeIf(String::isNotBlank) ?: "Couldn't save the device fingerprint.", null)
            }
        }
    }

    private fun saveBuildPropsTemplate(tile: AppTile, props: CloneBuildProps) {
        val normalized = props.normalized()
        if (!CloneBuildPropsStore.isValid(normalized.toValues())) {
            showError("Build properties contain an invalid value.", null)
            return
        }
        lifecycleScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    CloneBuildPropsStore.writeTemplate(this@MainActivity, tile.packageName, normalized.toValues())
                }
                if (!saved) error("Couldn't save the build properties.")
                val status = if (normalized.overrideCount == 0) "reset" else "saved"
                showInfo("Build props " + status + " for future " + tile.label + " clones.")
            } catch (error: Throwable) {
                Log.e(TRACE_TAG, "build_props_template_save failed package=" + tile.packageName, error)
                showError(error.message?.takeIf(String::isNotBlank) ?: "Couldn't save build props.", null)
            }
        }
    }

    private fun saveDeviceModel(tile: AppTile, model: String?) {
        val userId = tile.virtualUserId ?: return
        val value = model?.trim()?.takeIf(String::isNotEmpty)
        if (value != null && !CloneDeviceModelStore.isValid(value)) {
            showError("Use printable characters with no more than ${CloneDeviceModelStore.MAX_LENGTH} characters.", null)
            return
        }

        showProgress(tile.packageName, "Saving the phone model for ${tile.displayName}…")
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (!CloneDeviceModelStore.write(this@MainActivity, tile.packageName, userId, value)) {
                        error("Couldn't save the phone model setting.")
                    }
                    BlackBoxCore.get().stopPackage(tile.packageName, userId)
                }
                showInfo(
                    if (value == null) "${tile.displayName} will use its default phone model behavior the next time it starts."
                    else "Saved the phone model for ${tile.displayName}. It will be used the next time the copy starts."
                )
            } catch (error: Throwable) {
                Log.e(TRACE_TAG, "device_model_save failed package=${tile.packageName} userId=$userId", error)
                showError(error.message?.takeIf(String::isNotBlank) ?: "Couldn't save the phone model setting.", null)
            }
        }
    }

    private fun saveNetworkIdentitySettings(
        tile: AppTile,
        userId: Int?,
        settings: CloneNetworkIdentitySettings
    ) {
        lifecycleScope.launch {
            try {
                networkIdentitySettingsMutex.withLock {
                    val saved = withContext(Dispatchers.IO) {
                        if (userId == null) {
                            CloneNetworkIdentityStore.writeTemplate(
                                this@MainActivity, tile.packageName, settings.toStoreOptions()
                            )
                        } else {
                            CloneNetworkIdentityStore.write(
                                this@MainActivity, tile.packageName, userId, settings.toStoreOptions()
                            )
                        }
                    }
                    if (!saved) error("Couldn't save the network identity settings.")
                    if (userId != null) {
                        withContext(Dispatchers.IO) {
                            BlackBoxCore.get().stopPackage(tile.packageName, userId)
                        }
                    }
                }
                if (userId != null) {
                    Toast.makeText(
                        this@MainActivity,
                        "Saved for ${tile.label}. Reopen the copy to apply the changes.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (error: Throwable) {
                Log.e(TRACE_TAG, "network_identity_save_failed package=${tile.packageName} userId=$userId", error)
                Toast.makeText(
                    this@MainActivity,
                    error.message?.takeIf(String::isNotBlank) ?: "Couldn't save network identity settings.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun saveHardwareIdentitySettings(
        tile: AppTile,
        userId: Int?,
        settings: CloneHardwareIdentitySettings
    ) {
        lifecycleScope.launch {
            try {
                hardwareIdentitySettingsMutex.withLock {
                    val saved = withContext(Dispatchers.IO) {
                        if (userId == null) {
                            CloneHardwareIdentityStore.writeTemplate(
                                this@MainActivity, tile.packageName, settings.toStoreOptions()
                            )
                        } else {
                            CloneHardwareIdentityStore.write(
                                this@MainActivity, tile.packageName, userId, settings.toStoreOptions()
                            )
                        }
                    }
                    if (!saved) error("Couldn't save the hardware settings.")
                    if (userId != null) {
                        withContext(Dispatchers.IO) {
                            BlackBoxCore.get().stopPackage(tile.packageName, userId)
                        }
                    }
                }
                if (userId != null) {
                    Toast.makeText(
                        this@MainActivity,
                        "Saved hardware settings for ${tile.displayName}. Reopen the copy to apply them.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (error: Throwable) {
                Log.e(TRACE_TAG, "hardware_identity_save_failed package=${tile.packageName} userId=$userId", error)
                Toast.makeText(
                    this@MainActivity,
                    error.message?.takeIf(String::isNotBlank) ?: "Couldn't save hardware settings.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun saveRecaptchaWorkaround(tile: AppTile, userId: Int?, enabled: Boolean) {
        val saved = if (userId == null) {
            CloneRecaptchaWorkaroundStore.writeTemplate(this, tile.packageName, enabled)
        } else {
            CloneRecaptchaWorkaroundStore.write(this, tile.packageName, userId, enabled)
        }
        if (!saved) {
            Toast.makeText(this, "Couldn't save the reCAPTCHA preference.", Toast.LENGTH_LONG).show()
        } else if (userId != null) {
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        BlackBoxCore.get().stopPackage(tile.packageName, userId)
                    }
                    Toast.makeText(
                        this@MainActivity,
                        "Saved for ${tile.displayName}. Reopen the copy to apply it.",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (error: Throwable) {
                    Log.e(TRACE_TAG, "recaptcha_compatibility_restart_failed package=${tile.packageName} userId=$userId", error)
                    Toast.makeText(
                        this@MainActivity,
                        "Saved, but couldn't restart ${tile.displayName}. Reopen it to apply the change.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun shortcutPreferenceKey(packageName: String, userId: Int) = "$packageName:$userId"

    private fun createCopies(
        tile: AppTile,
        packageNames: List<String>,
        buildProps: CloneBuildProps = CloneBuildProps(),
        identitySettings: CloneNetworkIdentitySettings = CloneNetworkIdentitySettings(),
        hardwareSettings: CloneHardwareIdentitySettings = CloneHardwareIdentitySettings(),
        recaptchaWorkaround: Boolean = false
    ) {
        if (busyPackageState.value != null) return
        val requestedPackageNames = packageNames.map { it.trim() }
        val copiesToCreate = requestedPackageNames.size
        if (copiesToCreate !in 1..MAX_COPIES_PER_APP || requestedPackageNames.any { !ClonePackageNameStore.isValid(it) }) {
            showError("Enter a valid package name for every copy.", null)
            return
        }
        if (requestedPackageNames.distinct().size != requestedPackageNames.size) {
            showError("Each copy needs a different package name.", null)
            return
        }
        val existingPackageNames = appsState.value.flatMap { app ->
            listOfNotNull(app.packageName, app.customPackageName)
        }.toSet()
        if (requestedPackageNames.any(existingPackageNames::contains)) {
            showError("Those package names are already in use. Choose unique names for the copies.", null)
            return
        }
        val currentCount = appsState.value
            .filter { it.packageName == tile.packageName && it.isCloned }
            .mapNotNull { it.virtualUserId }
            .distinct()
            .size
        if (currentCount + copiesToCreate > MAX_COPIES_PER_APP) {
            showError("${tile.label} already has the maximum of $MAX_COPIES_PER_APP copies.", null)
            return
        }

        showProgress(
            tile.packageName,
            "Creating $copiesToCreate ${tile.label} ${if (copiesToCreate == 1) "copy" else "copies"}…"
        )
        lifecycleScope.launch {
            try {
                val (createdUserIds, googleServicesAvailable) = withContext(Dispatchers.IO) {
                    val runtime = BlackBoxCore.get()
                    val users = runtime.getUsers().orEmpty()
                    if (!CloneBuildPropsStore.writeTemplate(
                            this@MainActivity, tile.packageName, buildProps.toValues()
                        )) {
                        error("Couldn't save the build properties for future copies.")
                    }
                    if (!CloneNetworkIdentityStore.writeTemplate(
                            this@MainActivity, tile.packageName, identitySettings.toStoreOptions()
                        )) {
                        error("Couldn't save network identity settings for future copies.")
                    }
                    if (!CloneHardwareIdentityStore.writeTemplate(
                            this@MainActivity, tile.packageName, hardwareSettings.toStoreOptions()
                        )) {
                        error("Couldn't save hardware settings for future copies.")
                    }
                    if (!CloneRecaptchaWorkaroundStore.writeTemplate(
                            this@MainActivity, tile.packageName, recaptchaWorkaround
                        )) {
                        error("Couldn't save the reCAPTCHA preference for future copies.")
                    }
                    val existingCopyCount = users.count { runtime.isInstalled(tile.packageName, it.id) }
                    if (existingCopyCount + copiesToCreate > MAX_COPIES_PER_APP) {
                        error("${tile.label} already has the maximum of $MAX_COPIES_PER_APP copies.")
                    }
                    var nextId = maxOf(RUNTIME_USER_ID_START, (users.maxOfOrNull { it.id } ?: 0) + 1)
                    val createdUserIds = mutableListOf<Int>()
                    var allGoogleServicesAvailable = true
                    try {
                repeat(copiesToCreate) { index ->
                            val user = runtime.createUser(nextId++)
                                ?: error("The virtual runtime couldn't create copy ${index + 1}.")
                            createdUserIds += user.id
                            if (VirtualDeviceIdentity.createAndroidId(
                                    this@MainActivity, tile.packageName, user.id
                                ) == null
                            ) {
                                error("Couldn't create a private Android ID for copy ${index + 1}.")
                            }
                            if (!VirtualDeviceIdentity.initializeTelephonyIds(
                                    this@MainActivity, tile.packageName, user.id
                                )
                            ) {
                                error("Couldn't prepare private telephony IDs for copy ${index + 1}.")
                            }
                            if (!CloneBuildPropsStore.write(
                                    this@MainActivity, tile.packageName, user.id, buildProps.toValues()
                                )
                            ) {
                                error("Couldn't save build properties for copy ${index + 1}.")
                            }
                            if (!CloneNetworkIdentityStore.write(
                                    this@MainActivity, tile.packageName, user.id, identitySettings.toStoreOptions()
                                )
                            ) {
                                error("Couldn't save network identity settings for copy ${index + 1}.")
                            }
                            if (!CloneHardwareIdentityStore.write(
                                    this@MainActivity,
                                    tile.packageName,
                                    user.id,
                                    hardwareSettings.toStoreOptions()
                                )
                            ) {
                                error("Couldn't save hardware settings for copy ${index + 1}.")
                            }
                            if (!CloneRecaptchaWorkaroundStore.write(
                                    this@MainActivity,
                                    tile.packageName,
                                    user.id,
                                    recaptchaWorkaround
                                )
                            ) {
                                error("Couldn't save the reCAPTCHA preference for copy ${index + 1}.")
                            }
                            val result = runtime.installInstalledPackageAsUser(tile.packageName, user.id)
                            if (result == null) {
                                error("The virtual package service stopped while creating copy ${index + 1}.")
                            }
                            if (!result.success) {
                                val reason = result.msg?.takeIf(String::isNotBlank)
                                    ?: "The runtime rejected the app package."
                                error("Copy ${index + 1} failed: $reason")
                            }
                            if (!ClonePackageNameStore.write(
                                    this@MainActivity,
                                    tile.packageName,
                                    user.id,
                                    requestedPackageNames[index]
                                )
                            ) {
                                error("Couldn't save the package name for copy ${index + 1}.")
                            }
                            val googleServices = runtime.installGoogleSignInServices(user.id)
                            if (googleServices.success) {
                                Log.i(TRACE_TAG, "clone_create google_services_ready package=" +
                                    tile.packageName + " userId=" + user.id)
                            } else {
                                allGoogleServicesAvailable = false
                                Log.w(TRACE_TAG, "clone_create google_services_unavailable package=" +
                                    tile.packageName + " userId=" + user.id + " reason=" + googleServices.msg)
                            }
                        }
                        createdUserIds.toList() to allGoogleServicesAvailable
                    } catch (error: Throwable) {
                        createdUserIds.asReversed().forEach { createdUserId ->
                            runCatching {
                                VirtualDeviceIdentity.deleteAndroidId(
                                    this@MainActivity, tile.packageName, createdUserId
                                )
                            }
                            runCatching {
                                VirtualDeviceIdentity.deleteTelephonyIds(
                                    this@MainActivity, tile.packageName, createdUserId
                                )
                            }
                            runCatching {
                                CloneBuildPropsStore.delete(this@MainActivity, tile.packageName, createdUserId)
                            }
                            runCatching {
                                CloneNetworkIdentityStore.delete(this@MainActivity, tile.packageName, createdUserId)
                            }
                            runCatching {
                                CloneHardwareIdentityStore.delete(this@MainActivity, tile.packageName, createdUserId)
                            }
                            runCatching {
                                CloneRecaptchaWorkaroundStore.delete(
                                    this@MainActivity,
                                    tile.packageName,
                                    createdUserId
                                )
                            }
                            runCatching {
                                top.niunaijun.blackbox.core.network.CloneProxyStore.delete(
                                    this@MainActivity, tile.packageName, createdUserId
                                )
                            }
                            runCatching {
                                ClonePackageNameStore.delete(this@MainActivity, tile.packageName, createdUserId)
                            }
                            runCatching { runtime.deleteUser(createdUserId) }
                        }
                        throw error
                    }
                }
                refreshApps()
                val googleServicesNotice = if (googleServicesAvailable) {
                    ""
                } else {
                    " Play Services is unavailable; apps that depend on it may not work."
                }
                showInfo(
                    "Created $copiesToCreate ${tile.label} ${if (copiesToCreate == 1) "copy" else "copies"}. " +
                        "Confirm each Home screen prompt to add its icon with the original app name and icon." +
                        googleServicesNotice
                )
                queueHomeShortcutsForCopies(tile, createdUserIds)
            } catch (error: Throwable) {
                val detail = error.message?.takeIf(String::isNotBlank)
                showError(
                    detail?.let { "Couldn't clone ${tile.label}: $it" }
                        ?: buildString {
                            append("Couldn't clone ${tile.label}. This app may use a package format the runtime can't load.")
                            if (tile.hasSplitApks) append(" It is installed as split APKs, which may be unsupported.")
                        },
                    tile.packageName,
                    copiesToCreate
                )
                retryPackageNamesState.value = requestedPackageNames
                refreshApps()
            }
        }
    }

    private fun installIntoContainer(tile: AppTile, userId: Int) {
        if (busyPackageState.value != null) return
        val copyCount = appsState.value.count { it.packageName == tile.packageName && it.isCloned }
        if (copyCount >= MAX_COPIES_PER_APP) {
            showError("${tile.label} already has the maximum of $MAX_COPIES_PER_APP copies.", null)
            return
        }
        val occupiedPackageNames = appsState.value.flatMap { app ->
            listOfNotNull(app.packageName, app.customPackageName)
        }.toSet()
        val proposedPackageName = packageNameDrafts(
            tile,
            1,
            occupiedPackageNames,
            CloneCopyNameStore.nextNumber(this, tile.packageName)
        ).first()
        showProgress(tile.packageName, "Adding ${tile.label} to container $userId…")
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val runtime = BlackBoxCore.get()
                    if (runtime.getUsers().orEmpty().none { it.id == userId }) {
                        error("Container $userId no longer exists.")
                    }
                    if (runtime.isInstalled(tile.packageName, userId)) {
                        error("${tile.label} is already in this container.")
                    }
                    if (VirtualDeviceIdentity.createAndroidId(this@MainActivity, tile.packageName, userId) == null) {
                        error("Couldn't prepare private app identity for ${tile.label}.")
                    }
                    if (!VirtualDeviceIdentity.initializeTelephonyIds(this@MainActivity, tile.packageName, userId)) {
                        error("Couldn't prepare private radio identity for ${tile.label}.")
                    }
                    if (!CloneBuildPropsStore.write(this@MainActivity, tile.packageName, userId,
                            CloneBuildPropsStore.readTemplate(this@MainActivity, tile.packageName))) {
                        error("Couldn't save build properties for ${tile.label}.")
                    }
                    if (!CloneNetworkIdentityStore.write(this@MainActivity, tile.packageName, userId,
                            CloneNetworkIdentityStore.readTemplate(this@MainActivity, tile.packageName))) {
                        error("Couldn't save network settings for ${tile.label}.")
                    }
                    if (!CloneHardwareIdentityStore.write(this@MainActivity, tile.packageName, userId,
                            CloneHardwareIdentityStore.readTemplate(this@MainActivity, tile.packageName))) {
                        error("Couldn't save hardware settings for ${tile.label}.")
                    }
                    val install = runtime.installInstalledPackageAsUser(tile.packageName, userId)
                        ?: error("The virtual package service stopped while adding ${tile.label}.")
                    if (!install.success) error(install.msg?.takeIf(String::isNotBlank)
                        ?: "The runtime couldn't add ${tile.label} to this container.")
                    if (!ClonePackageNameStore.write(this@MainActivity, tile.packageName, userId,
                            proposedPackageName)) {
                        runtime.uninstallPackageAsUser(tile.packageName, userId)
                        error("Couldn't save the app's private container name.")
                    }
                    runtime.installGoogleSignInServices(userId)
                }
                refreshApps()
                showInfo("Added ${tile.label} to container $userId. Its app data is separate from other apps in that container.")
            } catch (error: Throwable) {
                showError(error.message?.takeIf(String::isNotBlank) ?: "Couldn't add ${tile.label} to this container.", null)
                refreshApps()
            }
        }
    }

    private fun retryClone(packageName: String) {
        val tile = appsState.value.firstOrNull { it.packageName == packageName } ?: return
        messageState.value = null
        val buildProps = CloneBuildProps.fromValues(
            CloneBuildPropsStore.readTemplate(this, packageName)
        )
        val identitySettings = CloneNetworkIdentitySettings.fromStore(
            CloneNetworkIdentityStore.readTemplate(this, packageName)
        )
        val hardwareSettings = CloneHardwareIdentitySettings.fromStore(
            CloneHardwareIdentityStore.readTemplate(this, packageName)
        )
        val packageNames = retryPackageNamesState.value
            .takeIf { it.size == retryCopyCountState.value }
            ?: List(retryCopyCountState.value) { index ->
                ClonePackageNameStore.suggestedName(
                    tile.packageName,
                    CloneCopyNameStore.nextNumber(this, tile.packageName) + index
                )
            }
        createCopies(
            tile,
            packageNames,
            buildProps,
            identitySettings,
            hardwareSettings,
            CloneRecaptchaWorkaroundStore.readTemplate(this, packageName)
        )
    }

    private fun showProgress(packageName: String, message: String) {
        busyPackageState.value = packageName
        messageState.value = message
        messageIsErrorState.value = false
        retryPackageState.value = null
        retryCopyCountState.value = 1
        retryPackageNamesState.value = emptyList()
    }

    private fun showInfo(message: String) {
        busyPackageState.value = null
        messageState.value = message
        messageIsErrorState.value = false
        retryPackageState.value = null
        retryCopyCountState.value = 1
        retryPackageNamesState.value = emptyList()
    }

    private fun showError(message: String, packageName: String?, retryCopies: Int = 1) {
        busyPackageState.value = null
        messageState.value = message
        messageIsErrorState.value = true
        retryPackageState.value = packageName
        retryCopyCountState.value = retryCopies
    }

    private fun copyMessage() {
        val report = messageState.value ?: return
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("TikViral message", report))
        Toast.makeText(this, "Message copied", Toast.LENGTH_SHORT).show()
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ClonyHomeRedesigned(
    apps: List<AppTile>,
    containerAccounts: Map<Int, List<String>>,
    message: String?,
    messageIsError: Boolean,
    busyPackageName: String?,
    retryPackageName: String?,
    onMessageDismissed: () -> Unit,
    onCopyDiagnostics: () -> Unit,
    onRetry: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenClone: (AppTile) -> Unit,
    onCreateCopies: (AppTile, List<String>, CloneBuildProps, CloneNetworkIdentitySettings, CloneHardwareIdentitySettings, Boolean) -> Unit,
    onInstallIntoContainer: (AppTile, Int) -> Unit,
    onSaveBuildPropsTemplate: (AppTile, CloneBuildProps) -> Unit,
    onSaveNetworkIdentitySettings: (AppTile, Int?, CloneNetworkIdentitySettings) -> Unit,
    onSaveHardwareIdentitySettings: (AppTile, Int?, CloneHardwareIdentitySettings) -> Unit,
    onSaveRecaptchaWorkaround: (AppTile, Int?, Boolean) -> Unit,
    onPinClone: (AppTile) -> Unit,
    onDeleteClone: (AppTile) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var detailPackage by remember { mutableStateOf<String?>(null) }
    var editingClone by remember { mutableStateOf<AppTile?>(null) }
    var query by remember { mutableStateOf("") }
    var countSheetApp by remember { mutableStateOf<AppTile?>(null) }
    var containerChoiceApp by remember { mutableStateOf<AppTile?>(null) }
    var deleteApp by remember { mutableStateOf<AppTile?>(null) }
    var networkApp by remember { mutableStateOf<AppTile?>(null) }
    var showBuildProps by remember { mutableStateOf(false) }
    var cloneFeatureScreen by remember { mutableStateOf(CloneFeatureScreen.CLONE_SETTINGS) }
    var cloneCount by remember { mutableStateOf(1) }
    var packageNames by remember { mutableStateOf(emptyList<String>()) }
    var buildProps by remember { mutableStateOf(CloneBuildProps()) }
    var networkIdentitySettings by remember { mutableStateOf(CloneNetworkIdentitySettings()) }
    var hardwareIdentitySettings by remember { mutableStateOf(CloneHardwareIdentitySettings()) }
    var recaptchaWorkaroundEnabled by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val screenScope = rememberCoroutineScope()
    val systemApps = remember(apps) {
        apps.groupBy { it.packageName }.values.map { copies ->
            copies.first().copy(
                virtualUserId = null, copyNumber = null,
                copyCount = copies.count { it.isCloned }, customPackageName = null
            )
        }.sortedBy { it.label.lowercase(Locale.ROOT) }
    }
    val clones = remember(apps) {
        apps.filter { it.isCloned }.sortedWith(compareBy<AppTile> { it.label.lowercase() }.thenBy { it.copyNumber })
    }
    val containers = remember(clones) {
        clones.mapNotNull { app -> app.virtualUserId?.let { it to app } }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()
    }
    val occupiedNames = remember(apps) { apps.flatMap { listOfNotNull(it.packageName, it.customPackageName) }.toSet() }
    val detailApp = systemApps.firstOrNull { it.packageName == detailPackage }
    val matchingApps = remember(systemApps, query) {
        if (query.isBlank()) systemApps else systemApps.filter {
            it.label.contains(query, true) || it.packageName.contains(query, true)
        }
    }
    val namesForRequest = packageNames.take(cloneCount).map(String::trim)
    val identitySettingsForNewCopies = remember(
        detailApp?.packageName,
        editingClone?.virtualUserId,
        networkIdentitySettings
    ) {
        if (detailApp == null || editingClone == null) networkIdentitySettings
        else CloneNetworkIdentitySettings.fromStore(
            CloneNetworkIdentityStore.readTemplate(context, detailApp.packageName)
        )
    }
    val hardwareSettingsForNewCopies = remember(
        detailApp?.packageName,
        editingClone?.virtualUserId,
        hardwareIdentitySettings
    ) {
        if (detailApp == null || editingClone == null) hardwareIdentitySettings
        else CloneHardwareIdentitySettings.fromStore(
            CloneHardwareIdentityStore.readTemplate(context, detailApp.packageName)
        )
    }
    val recaptchaWorkaroundForNewCopies = remember(
        detailApp?.packageName,
        editingClone?.virtualUserId,
        recaptchaWorkaroundEnabled
    ) {
        if (detailApp == null || editingClone == null) recaptchaWorkaroundEnabled
        else CloneRecaptchaWorkaroundStore.readTemplate(context, detailApp.packageName)
    }
    val requestValid = detailApp != null &&
        cloneCount in 1..(MAX_COPIES_PER_APP - (detailApp?.copyCount ?: 0)) &&
        namesForRequest.size == cloneCount &&
        namesForRequest.all(ClonePackageNameStore::isValid) &&
        namesForRequest.distinct().size == namesForRequest.size &&
        namesForRequest.none(occupiedNames::contains)
    var previousCopyCount by remember(detailPackage) { mutableStateOf<Int?>(null) }

    androidx.compose.runtime.LaunchedEffect(detailPackage, detailApp?.copyCount) {
        val currentCount = detailApp?.copyCount ?: return@LaunchedEffect
        val previousCount = previousCopyCount
        if (previousCount != null && currentCount > previousCount) {
            cloneCount = 1
            packageNames = packageNameDrafts(
                detailApp,
                1,
                occupiedNames,
                CloneCopyNameStore.nextNumber(context, detailApp.packageName)
            )
        }
        previousCopyCount = currentCount
    }

    androidx.activity.compose.BackHandler(enabled = detailPackage != null) {
        when (cloneFeatureScreen) {
            CloneFeatureScreen.NETWORK_RADIO_IDENTITY -> {
                cloneFeatureScreen = CloneFeatureScreen.IDENTITY_OPERATIONS
            }
            CloneFeatureScreen.CONNECTION_OPERATOR_INFO -> {
                cloneFeatureScreen = CloneFeatureScreen.IDENTITY_OPERATIONS
            }
            CloneFeatureScreen.DEVICE_PROFILE_HARDWARE -> {
                cloneFeatureScreen = CloneFeatureScreen.IDENTITY_OPERATIONS
            }
            CloneFeatureScreen.IDENTITY_OPERATIONS -> {
                cloneFeatureScreen = CloneFeatureScreen.CLONE_SETTINGS
            }
            CloneFeatureScreen.CLONE_SETTINGS -> {
                detailPackage = null
                editingClone = null
            }
        }
    }

    fun openSystemApp(app: AppTile, cloneToEdit: AppTile? = null) {
        detailPackage = app.packageName
        editingClone = cloneToEdit
        cloneFeatureScreen = CloneFeatureScreen.CLONE_SETTINGS
        cloneCount = 1
        packageNames = packageNameDrafts(
            app, 1, occupiedNames, CloneCopyNameStore.nextNumber(context, app.packageName)
        )
        buildProps = CloneBuildProps.fromValues(CloneBuildPropsStore.readTemplate(context, app.packageName))
        networkIdentitySettings = CloneNetworkIdentitySettings.fromStore(
            cloneToEdit?.virtualUserId?.let { CloneNetworkIdentityStore.read(context, app.packageName, it) }
                ?: CloneNetworkIdentityStore.readTemplate(context, app.packageName)
        )
        hardwareIdentitySettings = CloneHardwareIdentitySettings.fromStore(
            cloneToEdit?.virtualUserId?.let { CloneHardwareIdentityStore.read(context, app.packageName, it) }
                ?: CloneHardwareIdentityStore.readTemplate(context, app.packageName)
        )
        recaptchaWorkaroundEnabled = cloneToEdit?.virtualUserId?.let {
            CloneRecaptchaWorkaroundStore.read(context, app.packageName, it)
        } ?: CloneRecaptchaWorkaroundStore.readTemplate(context, app.packageName)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (detailApp == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(30.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 3.dp,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(5.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            HomeBottomTab("Cloned apps", selectedTab == 0, Modifier.weight(1f), false) {
                                selectedTab = 0
                            }
                            HomeBottomTab("System apps", selectedTab == 1, Modifier.weight(1f), true) {
                                selectedTab = 1
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (detailApp != null && cloneFeatureScreen == CloneFeatureScreen.CLONE_SETTINGS) {
                FloatingActionButton(
                    onClick = {
                        if (requestValid && busyPackageName == null) {
                            onCreateCopies(
                                detailApp,
                                namesForRequest,
                                buildProps,
                                identitySettingsForNewCopies,
                                hardwareSettingsForNewCopies,
                                recaptchaWorkaroundForNewCopies
                            )
                        }
                    },
                    containerColor = if (requestValid && busyPackageName == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (requestValid && busyPackageName == null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Row(
                        Modifier.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("+", style = MaterialTheme.typography.titleLarge)
                        Text("Clone", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (detailApp == null) {
                    Image(
                        painter = painterResource(R.drawable.tikviral_logo),
                        contentDescription = null,
                        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                            .background(colorResource(R.color.tikviral_logo_background))
                            .padding(2.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text("Private app copies", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onRefresh) { Text("Refresh") }
                } else {
                    if (cloneFeatureScreen == CloneFeatureScreen.CLONE_SETTINGS) {
                        TextButton(onClick = { detailPackage = null; editingClone = null }) { Text("← Back") }
                        Image(detailApp.icon, null, Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(detailApp.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                editingClone?.let { "Editing " + it.displayName } ?: "System app",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onRefresh) { Text("Refresh") }
                    } else {
                        val pageTitle = when (cloneFeatureScreen) {
                            CloneFeatureScreen.IDENTITY_OPERATIONS -> "Identity & tracking operations"
                            CloneFeatureScreen.NETWORK_RADIO_IDENTITY -> "Network & radio identity"
                            CloneFeatureScreen.CONNECTION_OPERATOR_INFO -> "Connection & operator info"
                            CloneFeatureScreen.DEVICE_PROFILE_HARDWARE -> "Device profile & hardware"
                            CloneFeatureScreen.CLONE_SETTINGS -> "Clone settings"
                        }
                        val backTarget = if (
                            cloneFeatureScreen == CloneFeatureScreen.NETWORK_RADIO_IDENTITY ||
                            cloneFeatureScreen == CloneFeatureScreen.CONNECTION_OPERATOR_INFO ||
                            cloneFeatureScreen == CloneFeatureScreen.DEVICE_PROFILE_HARDWARE
                        ) {
                            CloneFeatureScreen.IDENTITY_OPERATIONS
                        } else {
                            CloneFeatureScreen.CLONE_SETTINGS
                        }
                        TextButton(onClick = { cloneFeatureScreen = backTarget }) { Text("← Back") }
                        Column(Modifier.weight(1f)) {
                            Text(pageTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                editingClone?.displayName ?: detailApp.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (detailApp != null) {
                when (cloneFeatureScreen) {
                    CloneFeatureScreen.CLONE_SETTINGS -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (message != null) item {
                            HomeMessageCard(message, messageIsError, busyPackageName != null, retryPackageName, onCopyDiagnostics, onRetry, onMessageDismissed)
                        }
                        item { Text("Cloning features", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                        item {
                            DetailActionCard(
                                title = "Number of clones",
                                description = when {
                                    detailApp.copyCount >= MAX_COPIES_PER_APP -> "Maximum of ${MAX_COPIES_PER_APP} clones reached"
                                    detailApp.copyCount == 0 -> "No copies yet · tap to choose how many to add"
                                    else -> "${detailApp.copyCount} existing · tap to choose how many to add"
                                },
                                value = detailApp.copyCount.toString(),
                                enabled = detailApp.copyCount < MAX_COPIES_PER_APP
                            ) { countSheetApp = detailApp }
                        }
                        if (containers.isNotEmpty() && detailApp.copyCount < MAX_COPIES_PER_APP) item {
                            DetailActionCard(
                                title = "Add to an existing container",
                                description = "Share a virtual container with other apps while keeping each app's data separate",
                                value = "Choose"
                            ) { containerChoiceApp = detailApp }
                        }
                        item {
                            DetailActionCard(
                                "Build props",
                                if (buildProps.overrideCount == 0) "TikViral defaults will be used"
                                else "${buildProps.overrideCount} custom values for new copies",
                                "Edit"
                            ) { showBuildProps = true }
                        }
                        item {
                            RecaptchaWorkaroundCard(
                                enabled = recaptchaWorkaroundEnabled,
                                onEnabledChange = { enabled ->
                                    recaptchaWorkaroundEnabled = enabled
                                    onSaveRecaptchaWorkaround(
                                        detailApp,
                                        editingClone?.virtualUserId,
                                        enabled
                                    )
                                }
                            )
                        }
                        item {
                            DetailActionCard(
                                title = "Identity, privacy & spoofing",
                                description = "Device, network and radio identity options",
                                value = "Open"
                            ) { cloneFeatureScreen = CloneFeatureScreen.IDENTITY_OPERATIONS }
                        }
                        item { Text(detailApp.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    CloneFeatureScreen.IDENTITY_OPERATIONS -> IdentityTrackingOperationsScreen(
                        appLabel = detailApp.label,
                        editingCloneName = editingClone?.displayName,
                        onOpenNetworkRadioIdentity = {
                            cloneFeatureScreen = CloneFeatureScreen.NETWORK_RADIO_IDENTITY
                        },
                        onOpenConnectionOperatorInfo = {
                            cloneFeatureScreen = CloneFeatureScreen.CONNECTION_OPERATOR_INFO
                        },
                        onOpenDeviceProfileHardware = {
                            cloneFeatureScreen = CloneFeatureScreen.DEVICE_PROFILE_HARDWARE
                        }
                    )
                    CloneFeatureScreen.NETWORK_RADIO_IDENTITY -> NetworkRadioIdentityScreen(
                        appLabel = detailApp.label,
                        editingCloneName = editingClone?.displayName,
                        settings = networkIdentitySettings,
                        onSettingsChange = { updatedSettings ->
                            networkIdentitySettings = updatedSettings
                            onSaveNetworkIdentitySettings(
                                detailApp,
                                editingClone?.virtualUserId,
                                updatedSettings
                            )
                        }
                    )
                    CloneFeatureScreen.CONNECTION_OPERATOR_INFO -> ConnectionOperatorInfoScreen(
                        appLabel = detailApp.label,
                        packageName = detailApp.packageName,
                        editingCloneName = editingClone?.displayName,
                        settings = networkIdentitySettings,
                        onSettingsChange = { updatedSettings ->
                            networkIdentitySettings = updatedSettings
                            onSaveNetworkIdentitySettings(
                                detailApp,
                                editingClone?.virtualUserId,
                                updatedSettings
                            )
                        },
                        onLoadUaeSample = {
                            screenScope.launch {
                                try {
                                    val sample = withContext(Dispatchers.IO) {
                                        SimProfileDirectory.fetchUaeSample()
                                    }
                                    val updatedSettings = networkIdentitySettings.copy(
                                        simulateSimProfile = true,
                                        simPhoneNumber = sample.phoneNumber,
                                        simCarrierName = sample.carrierName,
                                        simCountryIso = sample.countryIso,
                                        simOperatorCode = sample.operatorCode
                                    )
                                    networkIdentitySettings = updatedSettings
                                    onSaveNetworkIdentitySettings(
                                        detailApp,
                                        editingClone?.virtualUserId,
                                        updatedSettings
                                    )
                                    Toast.makeText(
                                        context,
                                        "Loaded UAE sample carrier data. Review the phone placeholder before testing.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } catch (error: Throwable) {
                                    Toast.makeText(
                                        context,
                                        error.message?.takeIf(String::isNotBlank)
                                            ?: "Couldn't load the online carrier data.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    )
                    CloneFeatureScreen.DEVICE_PROFILE_HARDWARE -> DeviceProfileHardwareScreen(
                        appLabel = detailApp.label,
                        editingCloneName = editingClone?.displayName,
                        settings = hardwareIdentitySettings,
                        onSettingsChange = { updatedSettings ->
                            hardwareIdentitySettings = updatedSettings
                            onSaveHardwareIdentitySettings(
                                detailApp,
                                editingClone?.virtualUserId,
                                updatedSettings
                            )
                        }
                    )
                }
            } else {
                if (selectedTab == 0) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (message != null) item {
                            HomeMessageCard(message, messageIsError, busyPackageName != null, retryPackageName, onCopyDiagnostics, onRetry, onMessageDismissed)
                        }
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Your clones", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text("Each copy keeps its own app data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text("${clones.size}", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        if (clones.isEmpty()) {
                            item {
                                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                                    Column(
                                        Modifier.fillMaxWidth().padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text("＋", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                                        Text("No clones yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Text("Open System apps to choose an app and create its first copy.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Button(onClick = { selectedTab = 1 }, enabled = systemApps.isNotEmpty()) { Text("Browse system apps") }
                                    }
                                }
                            }
                        } else {
                            items(clones.chunked(2), key = { row -> row.joinToString("|") { "${it.packageName}:${it.virtualUserId}" } }) { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    row.forEach { app ->
                                        AppGridTile(
                                            app, busyPackageName == app.packageName, busyPackageName == null,
                                            Modifier.weight(1f),
                                            onClick = { onOpenClone(app) },
                                            onEditClone = {
                                                systemApps.firstOrNull { it.packageName == app.packageName }
                                                    ?.let { openSystemApp(it, app) }
                                            },
                                            onProxy = { networkApp = app },
                                            onPin = { onPinClone(app) },
                                            onDelete = { deleteApp = app }
                                        )
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        AppSearchField(query, { query = it }, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (message != null) item {
                                HomeMessageCard(message, messageIsError, busyPackageName != null, retryPackageName, onCopyDiagnostics, onRetry, onMessageDismissed)
                            }
                            item { Text("Installed apps · ${systemApps.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                            if (matchingApps.isEmpty()) {
                                item {
                                    Text(
                                        if (systemApps.isEmpty()) "No launchable apps were found on this device."
                                        else "No apps match “$query”.",
                                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else items(matchingApps, key = { it.packageName }) { app ->
                                SystemAppRow(app, busyPackageName == app.packageName) { openSystemApp(app) }
                            }
                        }
                    }
                }
            }
        }
    }

    countSheetApp?.let { app ->
        CloneCountSheet(
            app, cloneCount, packageNames, occupiedNames,
            CloneCopyNameStore.nextNumber(context, app.packageName),
            onDismiss = { countSheetApp = null },
            onApply = { count, names -> cloneCount = count; packageNames = names; countSheetApp = null }
        )
    }
    containerChoiceApp?.let { app ->
        AlertDialog(
            onDismissRequest = { containerChoiceApp = null },
            title = { Text("Choose a container") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    containers.forEach { (userId, installedApps) ->
                        val alreadyInstalled = installedApps.any { it.packageName == app.packageName }
                        TextButton(
                            enabled = !alreadyInstalled && busyPackageName == null,
                            onClick = {
                                containerChoiceApp = null
                                onInstallIntoContainer(app, userId)
                            }
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text("Container $userId", fontWeight = FontWeight.SemiBold)
                                Text(
                                    buildString {
                                        append(if (alreadyInstalled) "${app.label} is already here" else installedApps.joinToString(" · ") { it.label })
                                        val accounts = containerAccounts[userId].orEmpty()
                                        append("\nGoogle accounts: ")
                                        append(accounts.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: "none added")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { containerChoiceApp = null }) { Text("Cancel") } }
        )
    }
    if (showBuildProps && detailApp != null) {
        CloneBuildPropsDialog(
            detailApp.label, buildProps,
            onDismiss = { showBuildProps = false },
            onSave = { props -> buildProps = props; showBuildProps = false; onSaveBuildPropsTemplate(detailApp, props) }
        )
    }
    deleteApp?.let { app ->
        AlertDialog(
            onDismissRequest = { deleteApp = null },
            title = { Text("Delete ${app.displayName}?") },
            text = {
                Text(
                    "This permanently removes the copy and its app data. TikViral disables its pinned Home screen shortcut. " +
                        "If this copy has a separate launcher app icon, Android will ask whether to uninstall it."
                )
            },
            confirmButton = { TextButton(onClick = { onDeleteClone(app); deleteApp = null }) { Text("Delete Clone", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteApp = null }) { Text("Cancel") } }
        )
    }
    networkApp?.let { app ->
        CloneProxyDialog(
            app,
            onDismiss = { networkApp = null },
            onSaved = { networkApp = null; onRefresh() }
        )
    }
}

@Composable
private fun HomeBottomTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    systemApps: Boolean,
    onClick: () -> Unit
) {
    val glyphColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 58.dp),
        shape = RoundedCornerShape(26.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Canvas(Modifier.size(20.dp)) {
                val stroke = Stroke(width = 1.8.dp.toPx())
                val corner = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                if (systemApps) {
                    val cell = 6.dp.toPx()
                    val gap = 3.dp.toPx()
                    val start = (size.width - cell * 2 - gap) / 2
                    for (row in 0..1) {
                        for (column in 0..1) {
                            drawRoundRect(
                                color = glyphColor,
                                topLeft = Offset(start + column * (cell + gap), start + row * (cell + gap)),
                                size = Size(cell, cell),
                                cornerRadius = corner
                            )
                        }
                    }
                } else {
                    drawRoundRect(
                        color = glyphColor,
                        topLeft = Offset(size.width * 0.30f, size.height * 0.10f),
                        size = Size(size.width * 0.58f, size.height * 0.68f),
                        cornerRadius = corner,
                        style = stroke
                    )
                    drawRoundRect(
                        color = glyphColor,
                        topLeft = Offset(size.width * 0.12f, size.height * 0.30f),
                        size = Size(size.width * 0.58f, size.height * 0.68f),
                        cornerRadius = corner,
                        style = stroke
                    )
                }
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = glyphColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HomeMessageCard(
    message: String, isError: Boolean, isBusy: Boolean, retryPackageName: String?,
    onCopy: () -> Unit, onRetry: (String) -> Unit, onDismiss: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isBusy) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)) }
                Text(message, Modifier.weight(1f), color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (isError) TextButton(onClick = onCopy) { Text("Copy message") }
                if (isError && retryPackageName != null) TextButton(onClick = { onRetry(retryPackageName) }) { Text("Retry") }
                if (!isBusy) TextButton(onClick = onDismiss) { Text("OK") }
            }
        }
    }
}

@Composable
internal fun DetailActionCard(title: String, description: String, value: String, enabled: Boolean = true, onClick: () -> Unit) {
    Card(
        onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(value, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RecaptchaWorkaroundCard(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("reCAPTCHA workaround", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Enables JavaScript, DOM storage, and third-party cookies in clone WebViews. Challenges stay interactive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }
}

@Composable
private fun SystemAppRow(app: AppTile, isBusy: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(app.icon, null, Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)))
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(app.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when { isBusy -> "Creating copy…"; app.copyCount == 0 -> "No clones yet"; app.copyCount == 1 -> "1 clone"; else -> "${app.copyCount} clones" },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CloneCountSheet(
    app: AppTile, initialCount: Int, initialNames: List<String>, occupiedNames: Set<String>,
    firstCopyNumber: Int, onDismiss: () -> Unit, onApply: (Int, List<String>) -> Unit
) {
    var count by remember(app.packageName, initialCount) { mutableStateOf(initialCount.coerceAtLeast(1)) }
    var names by remember(app.packageName, initialCount, initialNames) {
        mutableStateOf(packageNameDrafts(app, initialCount, occupiedNames, firstCopyNumber, initialNames))
    }
    val maxCount = (MAX_COPIES_PER_APP - app.copyCount).coerceAtLeast(0)
    val normalized = names.take(count).map(String::trim)
    val valid = count in 1..maxCount && normalized.size == count &&
        normalized.all(ClonePackageNameStore::isValid) && normalized.distinct().size == count &&
        normalized.none(occupiedNames::contains)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 680.dp).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Number of clones", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${app.copyCount} existing · up to ${maxCount} more for ${app.label}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    val next = (count - 1).coerceAtLeast(1)
                    names = packageNameDrafts(app, next, occupiedNames, firstCopyNumber, names); count = next
                }, enabled = count > 1) { Text("−") }
                Text(count.toString(), Modifier.padding(horizontal = 28.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = {
                    val next = (count + 1).coerceAtMost(maxCount.coerceAtLeast(1))
                    names = packageNameDrafts(app, next, occupiedNames, firstCopyNumber, names); count = next
                }, enabled = count < maxCount) { Text("+") }
            }
            Text("Copy identifiers", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            repeat(count) { index ->
                val value = names.getOrElse(index) { "" }
                val id = value.trim()
                val duplicate = id.isNotEmpty() && normalized.count { it == id } > 1
                val error = when {
                    !ClonePackageNameStore.isValid(id) -> "Enter a valid dotted package name."
                    duplicate -> "Each clone needs a different name."
                    id in occupiedNames -> "This package name is already in use."
                    else -> null
                }
                OutlinedTextField(
                    value, { updated -> names = names.toMutableList().also { if (index < it.size) it[index] = updated } },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Clone ${index + 1} package name") },
                    singleLine = true, isError = error != null,
                    supportingText = if (error != null) ({ Text(error) }) else null
                )
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onApply(count, normalized) }, enabled = valid) { Text("Done") }
            }
        }
    }
}

@Composable
private fun AppGridTile(
    app: AppTile,
    isBusy: Boolean,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onEditClone: () -> Unit,
    onProxy: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val displayedPackageName = app.customPackageName ?: app.packageName
    var actionsExpanded by remember(app.packageName, app.virtualUserId) { mutableStateOf(false) }
    Card(
        onClick = onClick,
        enabled = isEnabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextButton(
                onClick = { actionsExpanded = true },
                enabled = isEnabled
            ) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
            DropdownMenu(
                expanded = actionsExpanded,
                onDismissRequest = { actionsExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Edit Clone") },
                    onClick = {
                        actionsExpanded = false
                        onEditClone()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Proxy / network") },
                    onClick = {
                        actionsExpanded = false
                        onProxy()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add icon to home screen") },
                    onClick = {
                        actionsExpanded = false
                        onPin()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete Clone", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        actionsExpanded = false
                        onDelete()
                    }
                )
            }
        }
            Box(contentAlignment = Alignment.Center) {
                Image(
                    bitmap = app.icon,
                    contentDescription = "${app.displayName} app icon",
                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(18.dp))
                )
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(30.dp), strokeWidth = 2.dp)
                }
            }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                app.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                displayedPackageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClickLabel = "Copy package name",
                    onLongClick = {
                        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                            ClipData.newPlainText("Package name", displayedPackageName)
                        )
                        Toast.makeText(context, "Package name copied", Toast.LENGTH_SHORT).show()
                    }
                )
            )
        }
        }
    }
}

@Composable
private fun AppSearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⌕", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) Text("Search apps", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        innerTextField()
                    }
                }
            )
        }
    }
}
