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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.IconCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.lifecycleScope
import com.clony.ui.theme.ClonyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.niunaijun.blackbox.BlackBoxCore
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

data class AppTile(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    val virtualUserId: Int? = null,
    val copyNumber: Int? = null,
    val copyCount: Int = 0,
    val hasSplitApks: Boolean = false
) {
    val isCloned: Boolean get() = virtualUserId != null
    val displayName: String
        get() = if (isCloned) "$label ${copyNumber ?: 1}" else label
}

class MainActivity : ComponentActivity() {
    private val appsState = mutableStateOf<List<AppTile>>(emptyList())
    private val runtimeReadyState = mutableStateOf(false)
    private val messageState = mutableStateOf<String?>(null)
    private val busyPackageState = mutableStateOf<String?>(null)
    private val messageIsErrorState = mutableStateOf(false)
    private val retryPackageState = mutableStateOf<String?>(null)
    private val retryCopyCountState = mutableStateOf(1)
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
                ClonyHome(
                    apps = appsState.value,
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
                    onPinClone = ::pinCloneShortcut,
                    onDeleteClone = ::deleteClone
                )
            }
        }
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
            val (tiles, runtimeReady) = withContext(Dispatchers.IO) {
                val launcherApps = getSystemService(LauncherApps::class.java)
                val runtime = BlackBoxCore.get()
                val runtimeReady = runCatching { runtime.isServicesAvailable() }.getOrDefault(false)
                val virtualUsers = runCatching { runtime.getUsers().orEmpty() }.getOrDefault(emptyList())
                val tiles = runCatching {
                    launcherApps.getActivityList(null, Process.myUserHandle())
                        .groupBy { it.applicationInfo.packageName }
                        .flatMap { (packageName, activities) ->
                            val activity = activities.firstOrNull() ?: return@flatMap emptyList()
                            if (packageName == this@MainActivity.packageName) return@flatMap emptyList()
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
                                    copyUserIds.mapIndexed { index, userId ->
                                        AppTile(
                                            packageName = packageName,
                                            label = label,
                                            icon = icon,
                                            virtualUserId = userId,
                                            copyNumber = index + 1,
                                            copyCount = copyUserIds.size,
                                            hasSplitApks = hasSplitApks
                                        )
                                    }
                                }
                            }.getOrNull() ?: emptyList()
                        }
                        .sortedBy { it.label.lowercase(Locale.ROOT) }
                }.getOrDefault(emptyList())
                tiles to runtimeReady
            }
            appsState.value = tiles
            runtimeReadyState.value = runtimeReady
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


    private fun pinCloneShortcut(tile: AppTile) {
        val userId = tile.virtualUserId ?: return
        val pinSupported = ShortcutManagerCompat.isRequestPinShortcutSupported(this)
        Log.i(TRACE_TAG, "shortcut_pin stage=begin package=${tile.packageName} userId=$userId supported=$pinSupported")
        if (!pinSupported) {
            showError("The current launcher doesn't support adding home screen icons.", null)
            return
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
            return
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
            return
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
    }

    private fun deleteClone(tile: AppTile) {
        val userId = tile.virtualUserId ?: return
        if (busyPackageState.value != null) return
        val label = "${tile.label} Copy ${tile.copyNumber ?: 1}"
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
                    Log.i(TRACE_TAG, "clone_delete stage=delete_user_begin package=${tile.packageName} userId=$userId")
                    runtime.deleteUser(userId)
                    Log.i(TRACE_TAG, "clone_delete stage=delete_user_complete package=${tile.packageName} userId=$userId")
                }
                Log.i(TRACE_TAG, "clone_delete stage=shortcut_cleanup_begin package=${tile.packageName} userId=$userId")
                CloneShortcutCleanup.removeForClone(this@MainActivity, tile.packageName, userId)
                Log.i(TRACE_TAG, "clone_delete stage=shortcut_cleanup_complete package=${tile.packageName} userId=$userId")
                refreshApps()
                showInfo("Deleted $label and its app data.")
            } catch (error: Throwable) {
                Log.e(TRACE_TAG, "clone_delete stage=failed package=${tile.packageName} userId=$userId label=$label", error)
                showError(error.message?.takeIf(String::isNotBlank) ?: "Couldn't delete $label.", null)
                refreshApps()
            }
        }
    }

    private fun shortcutPreferenceKey(packageName: String, userId: Int) = "$packageName:$userId"

    private fun createCopies(tile: AppTile, requestedCopies: Int) {
        if (busyPackageState.value != null) return
        val copiesToCreate = requestedCopies.coerceIn(1, MAX_COPIES_PER_APP)
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
                withContext(Dispatchers.IO) {
                    val runtime = BlackBoxCore.get()
                    val users = runtime.getUsers().orEmpty()
                    val existingCopyCount = users.count { runtime.isInstalled(tile.packageName, it.id) }
                    if (existingCopyCount + copiesToCreate > MAX_COPIES_PER_APP) {
                        error("${tile.label} already has the maximum of $MAX_COPIES_PER_APP copies.")
                    }
                    var nextId = maxOf(RUNTIME_USER_ID_START, (users.maxOfOrNull { it.id } ?: 0) + 1)
                    val createdUserIds = mutableListOf<Int>()
                    try {
                        repeat(copiesToCreate) { index ->
                            val user = runtime.createUser(nextId++)
                                ?: error("The virtual runtime couldn't create copy ${index + 1}.")
                            createdUserIds += user.id
                            val result = runtime.installInstalledPackageAsUser(tile.packageName, user.id)
                            if (result == null) {
                                error("The virtual package service stopped while creating copy ${index + 1}.")
                            }
                            if (!result.success) {
                                val reason = result.msg?.takeIf(String::isNotBlank)
                                    ?: "The runtime rejected the app package."
                                error("Copy ${index + 1} failed: $reason")
                            }
                            val googleServices = runtime.installGoogleSignInServices(user.id)
                            if (googleServices.success) {
                                Log.i(TRACE_TAG, "clone_create google_services_ready package=" +
                                    tile.packageName + " userId=" + user.id)
                            } else {
                                Log.w(TRACE_TAG, "clone_create google_services_unavailable package=" +
                                    tile.packageName + " userId=" + user.id + " reason=" + googleServices.msg)
                            }
                        }
                    } catch (error: Throwable) {
                        createdUserIds.asReversed().forEach { createdUserId ->
                            runCatching { runtime.deleteUser(createdUserId) }
                        }
                        throw error
                    }
                }
                refreshApps()
                showInfo("Created $copiesToCreate ${tile.label} ${if (copiesToCreate == 1) "copy" else "copies"} with separate data. Tap a copy to open it.")
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
                refreshApps()
            }
        }
    }

    private fun retryClone(packageName: String) {
        val tile = appsState.value.firstOrNull { it.packageName == packageName } ?: return
        messageState.value = null
        createCopies(tile, retryCopyCountState.value)
    }

    private fun showProgress(packageName: String, message: String) {
        busyPackageState.value = packageName
        messageState.value = message
        messageIsErrorState.value = false
        retryPackageState.value = null
        retryCopyCountState.value = 1
    }

    private fun showInfo(message: String) {
        busyPackageState.value = null
        messageState.value = message
        messageIsErrorState.value = false
        retryPackageState.value = null
        retryCopyCountState.value = 1
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
            .setPrimaryClip(ClipData.newPlainText("Clony message", report))
        Toast.makeText(this, "Message copied", Toast.LENGTH_SHORT).show()
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ClonyHome(
    apps: List<AppTile>,
    message: String?,
    messageIsError: Boolean,
    busyPackageName: String?,
    retryPackageName: String?,
    onMessageDismissed: () -> Unit,
    onCopyDiagnostics: () -> Unit,
    onRetry: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenClone: (AppTile) -> Unit,
    onCreateCopies: (AppTile, Int) -> Unit,
    onPinClone: (AppTile) -> Unit,
    onDeleteClone: (AppTile) -> Unit
) {
    var isPickerOpen by remember { mutableStateOf(false) }
    var pickerQuery by remember { mutableStateOf("") }
    var copySelectionApp by remember { mutableStateOf<AppTile?>(null) }
    var deleteSelectionApp by remember { mutableStateOf<AppTile?>(null) }
    var requestedCopyCount by remember { mutableStateOf(1) }
    val clonedApps = remember(apps) {
        apps.filter { it.isCloned }
            .sortedWith(compareBy<AppTile> { it.label.lowercase() }.thenBy { it.copyNumber })
    }
    val pickerApps = remember(apps, pickerQuery) {
        val sources = apps.groupBy { it.packageName }.values.map { copies ->
            val app = copies.first()
            app.copy(
                virtualUserId = null,
                copyNumber = null,
                copyCount = copies.count { it.isCloned }
            )
        }
        val matches = if (pickerQuery.isBlank()) sources else sources.filter {
            it.label.contains(pickerQuery, ignoreCase = true) ||
                it.packageName.contains(pickerQuery, ignoreCase = true)
        }
        matches.sortedBy { it.label.lowercase() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    pickerQuery = ""
                    isPickerOpen = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("C", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("CLONY", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text("Local app clones", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onRefresh) { Text("Refresh") }
                }
            }

            if (message != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (messageIsError) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (busyPackageName != null) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(12.dp))
                                }
                                Text(
                                    message,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (messageIsError) MaterialTheme.colorScheme.onErrorContainer
                                    else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (messageIsError) {
                                    TextButton(onClick = onCopyDiagnostics) { Text("Copy message") }
                                }
                                if (messageIsError && retryPackageName != null) {
                                    TextButton(onClick = { onRetry(retryPackageName) }) { Text("Retry") }
                                }
                                if (busyPackageName == null) {
                                    TextButton(onClick = onMessageDismissed) { Text("OK") }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cloned apps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Separate copies with their own app data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text("${clonedApps.size}", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            if (clonedApps.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("＋", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                            Text("No local clones yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (apps.isEmpty()) "No launchable apps were found on this device."
                                else "Choose an installed app to create its own copy in the virtual runtime.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = { isPickerOpen = true }, enabled = apps.isNotEmpty() && busyPackageName == null) {
                                Text("Add an app")
                            }
                        }
                    }
                }
            } else {
                items(
                    clonedApps.chunked(2),
                    key = { row -> row.joinToString("|") { "${it.packageName}:${it.virtualUserId}" } }
                ) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { app ->
                            AppGridTile(
                                app = app,
                                isBusy = busyPackageName == app.packageName,
                                isEnabled = busyPackageName == null,
                                modifier = Modifier.weight(1f),
                                onClick = { onOpenClone(app) },
                                onPin = { onPinClone(app) },
                                onDelete = { deleteSelectionApp = app }
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }


        }
    }

    if (isPickerOpen) {
        ModalBottomSheet(onDismissRequest = { isPickerOpen = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Create another copy", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Each copy has separate app data. You can create up to $MAX_COPIES_PER_APP copies of an app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AppSearchField(value = pickerQuery, onValueChange = { pickerQuery = it })
                if (pickerApps.isEmpty()) {
                    Text(
                        if (apps.isEmpty()) "No launchable apps were found on this device."
                        else "No apps match “$pickerQuery”.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                        contentPadding = PaddingValues(bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(pickerApps, key = { it.packageName }) { app ->
                            AppRow(
                                app = app,
                                isBusy = busyPackageName == app.packageName,
                                isEnabled = busyPackageName == null && app.copyCount < MAX_COPIES_PER_APP,
                                onClick = {
                                    isPickerOpen = false
                                    requestedCopyCount = 1
                                    copySelectionApp = app
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    copySelectionApp?.let { app ->
        val maxForSelection = (MAX_COPIES_PER_APP - app.copyCount).coerceAtLeast(1)
        AlertDialog(
            onDismissRequest = { copySelectionApp = null },
            title = { Text("Create copies of ${app.label}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${app.copyCount} copies exist. Each new copy gets its own app data.")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { requestedCopyCount = (requestedCopyCount - 1).coerceAtLeast(1) },
                            enabled = requestedCopyCount > 1
                        ) { Text("−") }
                        Text(
                            "$requestedCopyCount",
                            modifier = Modifier.padding(horizontal = 24.dp),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = { requestedCopyCount = (requestedCopyCount + 1).coerceAtMost(maxForSelection) },
                            enabled = requestedCopyCount < maxForSelection
                        ) { Text("+") }
                    }
                    Text("Up to $MAX_COPIES_PER_APP copies per app.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreateCopies(app, requestedCopyCount)
                        copySelectionApp = null
                    }
                ) { Text("Create $requestedCopyCount") }
            },
            dismissButton = {
                TextButton(onClick = { copySelectionApp = null }) { Text("Cancel") }
            }
        )
    }

    deleteSelectionApp?.let { app ->
        AlertDialog(
            onDismissRequest = { deleteSelectionApp = null },
            title = { Text("Delete ${app.label} Copy ${app.copyNumber ?: 1}?") },
            text = {
                Text("This permanently removes the clone and its app data. Clony will disable its home screen shortcut; your launcher may require you to remove the pinned icon.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClone(app)
                        deleteSelectionApp = null
                    }
                ) {
                    Text("Delete copy", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteSelectionApp = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AppGridTile(
    app: AppTile,
    isBusy: Boolean,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
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
                    text = { Text("Add copy icon to Home screen") },
                    onClick = {
                        actionsExpanded = false
                        onPin()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete copy", color = MaterialTheme.colorScheme.error) },
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
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                app.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        }
    }
}

@Composable
private fun AppSearchField(value: String, onValueChange: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun AppRow(app: AppTile, isBusy: Boolean, isEnabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
            )
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        app.copyCount > 0 -> "${app.copyCount} copies · separate data"
                        app.hasSplitApks -> "Split APKs · support may vary"
                        else -> "Installed on this device"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            OutlinedButton(
                onClick = onClick,
                enabled = isEnabled,
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 9.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    when {
                        isBusy -> "Working"
                        app.copyCount >= MAX_COPIES_PER_APP -> "Max copies"
                        app.copyCount > 0 -> "Add copies"
                        else -> "Clone"
                    }
                )
            }
        }
    }
}
