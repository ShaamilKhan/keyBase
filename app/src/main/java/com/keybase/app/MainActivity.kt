package com.keybase.app

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import android.view.View
import android.view.WindowManager
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.keybase.app.crypto.Biometrics
import com.keybase.app.crypto.DeviceKey
import com.keybase.app.crypto.HardwareKeyException
import com.keybase.app.crypto.KeyFile
import com.keybase.app.crypto.KeyFileException
import com.keybase.app.crypto.LegacyVaultException
import com.keybase.app.crypto.QuickUnlock
import com.keybase.app.crypto.VaultCrypto
import com.keybase.app.crypto.WrongCredentialsException
import com.keybase.app.data.Clip
import com.keybase.app.data.Entry
import com.keybase.app.data.Settings
import com.keybase.app.data.Vault
import com.keybase.app.data.VaultStore
import com.keybase.app.ui.EntryScreen
import com.keybase.app.ui.GeneratorScreen
import com.keybase.app.ui.KeyBaseTheme
import com.keybase.app.ui.LockScreen
import com.keybase.app.ui.SettingsScreen
import com.keybase.app.ui.VaultScreen
import com.keybase.app.ui.isDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A system picker (key file, backup) may keep us in the background this long before we lock anyway. */
private const val PICKER_LIMIT_MS = 120_000L

private sealed interface Screen {
    data object Home : Screen
    data object Prefs : Screen
    data object Gen : Screen
    data class Editor(val entry: Entry, val isNew: Boolean) : Screen
}

/** ACTION_OPEN_DOCUMENT that asks for a read grant that survives restarts. */
private class OpenPersistentDoc : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

private class CreatePersistentDoc : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/octet-stream")
            .putExtra(Intent.EXTRA_TITLE, input)
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

class MainActivity : FragmentActivity() {
    private lateinit var store: VaultStore
    private lateinit var settings: Settings
    private val handler = Handler(Looper.getMainLooper())
    private val autoLockRun = Runnable { lockNow() }

    private var locked by mutableStateOf(true)
    private var screen by mutableStateOf<Screen>(Screen.Home)
    private var currentGroup by mutableStateOf<String?>(null)
    private var themeMode by mutableIntStateOf(0)
    private var quickEnabled by mutableStateOf(false)
    private var securityLabel by mutableStateOf<String?>(null)
    private var lockedUntil by mutableLongStateOf(0L)
    private var lastBackup by mutableLongStateOf(0L)
    private var autoLockSec by mutableIntStateOf(60)
    private var allowCopy by mutableStateOf(true)
    private var clipSec by mutableIntStateOf(20)
    private var keyUriText by mutableStateOf<String?>(null)
    private var keyName by mutableStateOf<String?>(null)
    private var vaultExists by mutableStateOf(false)
    private var autofillOn by mutableStateOf(false)

    private var holdLock = false      // a system picker is open
    private var prompting = false     // a fingerprint prompt is showing
    private var leftAt = 0L
    private var awayLimit = 0L

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { if (store.isUnlocked) lockNow() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No screenshots, screen recording or recents thumbnail.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        applyPlatformHardening()

        store = Vault.store(this)
        settings = Settings(this)
        themeMode = settings.themeMode
        autoLockSec = settings.autoLockSec
        allowCopy = settings.allowCopy
        clipSec = settings.clipClearSec
        lastBackup = settings.lastBackup
        lockedUntil = settings.lockUntil.coerceAtMost(System.currentTimeMillis() + 300_000L)
        loadKeyFile()
        securityLabel = DeviceKey.levelLabel()
        vaultExists = store.exists()

        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)

        setContent {
            val dark = isDarkTheme(themeMode)
            SideEffect {
                val bar = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
            }
            KeyBaseTheme(themeMode) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { App() }
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOff) }
        handler.removeCallbacks(autoLockRun)
        if (isFinishing) { store.lock(); Clip.clearIfOurs(this) }
        super.onDestroy()
    }

    /** Newest protections first; each is skipped on OS versions that lack it (floor: Android 12). */
    private fun applyPlatformHardening() {
        // Android 12+: other apps' overlays are hidden while we are on screen (anti-tapjacking).
        runCatching { window.setHideOverlayWindows(true) }
        window.decorView.filterTouchesWhenObscured = true
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false)
        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14+: hide this window from accessibility services that are not declared accessibility tools.
            runCatching { window.decorView.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES) }
        }
        if (Build.VERSION.SDK_INT >= 35) {
            // Android 15+: marks the content sensitive so it is hidden from screen sharing and untrusted assistants.
            window.decorView.setContentSensitivity(View.CONTENT_SENSITIVITY_SENSITIVE)
        }
    }

    // ---- locking ----------------------------------------------------------------------------

    override fun onStart() {
        super.onStart()
        Vault.uiActive = true
        handler.removeCallbacks(autoLockRun)
        if (store.isUnlocked) {
            // Anything unlocked while we were away (e.g. by autofill) is locked again: you unlock here.
            if (SystemClock.elapsedRealtime() - leftAt >= awayLimit) lockNow()
        } else {
            locked = true
        }
        quickEnabled = QuickUnlock.isEnabled(this)
        autofillOn = isAutofillOn()
        if (!store.isUnlocked && vaultExists && quickEnabled && !holdLock && !prompting) {
            window.decorView.post { startQuickUnlock() }
        }
    }

    override fun onStop() {
        super.onStop()
        Vault.uiActive = false
        if (isChangingConfigurations || !store.isUnlocked) return
        awayLimit = if (holdLock) PICKER_LIMIT_MS else autoLockSec * 1000L
        leftAt = SystemClock.elapsedRealtime()
        if (awayLimit == 0L) lockNow() else handler.postDelayed(autoLockRun, awayLimit)
    }

    private fun lockNow() {
        handler.removeCallbacks(autoLockRun)
        store.lock()
        Clip.clearIfOurs(this)
        screen = Screen.Home
        currentGroup = null
        locked = true
    }

    /** True when keyBase itself is the phone's selected autofill service. */
    private fun isAutofillOn(): Boolean = runCatching {
        getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true
    }.getOrDefault(false)

    // ---- key file ---------------------------------------------------------------------------

    private fun loadKeyFile() {
        val s = settings.keyFileUri
        if (s == null) { keyUriText = null; keyName = null; return }
        val ok = runCatching { KeyFile.isRemembered(contentResolver, Uri.parse(s)) }.getOrDefault(false)
        if (ok) {
            keyUriText = s
            keyName = settings.keyFileName
        } else {
            settings.keyFileUri = null; settings.keyFileName = null
            keyUriText = null; keyName = null
        }
    }

    private fun setKeyFile(uri: Uri) {
        val old = settings.keyFileUri
        if (old != null && old != uri.toString()) KeyFile.release(contentResolver, old)
        KeyFile.remember(contentResolver, uri)
        val name = KeyFile.displayName(contentResolver, uri) ?: "Key file"
        settings.keyFileUri = uri.toString(); settings.keyFileName = name
        keyUriText = uri.toString(); keyName = name
    }

    private fun forgetKeyFile() {
        KeyFile.release(contentResolver, settings.keyFileUri)
        settings.keyFileUri = null; settings.keyFileName = null
        keyUriText = null; keyName = null
    }

    // ---- unlock -----------------------------------------------------------------------------

    private fun penaltyMs(fails: Int): Long =
        if (fails < 3) 0L else longArrayOf(5, 10, 30, 60, 120, 300)[minOf(fails - 3, 5)] * 1000L

    private fun recordFailure() {
        val n = settings.failCount + 1
        settings.failCount = n
        val until = System.currentTimeMillis() + penaltyMs(n)
        settings.lockUntil = until
        lockedUntil = until
    }

    private fun clearFailures() {
        settings.failCount = 0; settings.lockUntil = 0L; lockedUntil = 0L
    }

    private fun startQuickUnlock() {
        if (prompting || store.isUnlocked || !QuickUnlock.isEnabled(this)) return
        prompting = true
        QuickUnlock.unlock(
            this,
            onPayload = { payload ->
                prompting = false
                try {
                    store.unlockWithKeys(payload)
                    clearFailures()
                    screen = Screen.Home; currentGroup = null
                    locked = false
                } catch (e: Exception) {
                    QuickUnlock.wipe(this)
                    quickEnabled = false
                    toast("Fingerprint unlock stopped working. Use your master password.")
                } finally {
                    payload.fill(0)
                }
            },
            onFail = { cancelled, msg ->
                prompting = false
                if (!cancelled) toast(msg)
                quickEnabled = QuickUnlock.isEnabled(this)
            },
        )
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun copyToClipboard(label: String, value: String) {
        if (value.isEmpty()) return
        Clip.copy(this, label, value, clipSec)
        toast("$label copied. Clears in ${clipSec}s")
    }

    private fun resetEverything() {
        store.deleteFile()
        DeviceKey.delete()
        QuickUnlock.wipe(this)
        forgetKeyFile()
        clearFailures()
        settings.lastBackup = 0L; lastBackup = 0L
        quickEnabled = false
        securityLabel = null
        vaultExists = false
        locked = true
    }

    // ---- UI ---------------------------------------------------------------------------------

    @Composable
    private fun App() {
        val scope = rememberCoroutineScope()
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var backupUri by remember { mutableStateOf<Uri?>(null) }
        var newKeyNote by remember { mutableStateOf(false) }
        val firstRun = !vaultExists
        val version = remember { runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "" }

        val pickKey = rememberLauncherForActivityResult(OpenPersistentDoc()) { uri ->
            holdLock = false
            if (uri != null) { setKeyFile(uri); error = null }
        }
        val createKey = rememberLauncherForActivityResult(CreatePersistentDoc()) { uri ->
            holdLock = false
            if (uri != null) {
                try {
                    KeyFile.writeNew(contentResolver, uri)
                    setKeyFile(uri); error = null; newKeyNote = true
                } catch (e: Exception) {
                    error = "Couldn't write the key file there. Pick another location."
                }
            }
        }
        val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            holdLock = false
            backupUri = uri
        }
        val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            holdLock = false
            if (uri != null && store.isUnlocked) {
                try {
                    val bytes = store.exportPortable()
                    contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
                    val now = System.currentTimeMillis()
                    settings.lastBackup = now; lastBackup = now
                    toast("Backup saved")
                } catch (e: Exception) {
                    toast("Couldn't save the backup")
                }
            }
        }

        fun submit(pw: String) {
            val ku = keyUriText?.let(Uri::parse) ?: return
            if (busy) return
            if (System.currentTimeMillis() < lockedUntil) return
            error = null
            val creating = firstRun
            val restoreUri = backupUri
            if (!Biometrics.available(this)) {
                error = "Set up a fingerprint (strong biometric) in system settings first."
                return
            }
            if (creating) {
                try { DeviceKey.ensure() } catch (e: Exception) {
                    error = "This phone can't keep the key in secure hardware, so keyBase won't create a vault."
                    return
                }
            }
            busy = true
            DeviceKey.deviceSecret(
                this, if (creating) "Create your vault" else "Unlock keyBase",
                onSecret = { secret ->
                    scope.launch {
                        val result = withContext(Dispatchers.Default) {
                            runCatching {
                                val kh = KeyFile.hash(contentResolver, ku)
                                try {
                                    if (creating) {
                                        val restore = restoreUri?.let { KeyFile.readBounded(contentResolver, it, VaultCrypto.MAX_FILE_BYTES) }
                                        store.create(kh, pw, secret, restore)
                                    } else {
                                        store.unlock(kh, pw, secret)
                                    }
                                } finally {
                                    kh.fill(0); secret.fill(0)
                                }
                            }
                        }
                        busy = false
                        val t = result.exceptionOrNull()
                        when {
                            t == null -> {
                                clearFailures()
                                vaultExists = true
                                backupUri = null
                                securityLabel = DeviceKey.levelLabel()
                                screen = Screen.Home; currentGroup = null
                                locked = false
                            }
                            t is WrongCredentialsException -> {
                                recordFailure()
                                error = if (creating) "That backup didn't open. Check the key file and password you used for it."
                                else "Wrong password or key file."
                            }
                            t is LegacyVaultException ->
                                error = if (creating) "That backup was made by an early test version and can't be restored."
                                else "This vault was made by an early test version and can't be opened. Erase it below to start fresh."
                            t is KeyFileException -> {
                                error = t.message
                                if (!creating) forgetKeyFile()
                            }
                            t is HardwareKeyException -> error = t.message
                            t is IllegalArgumentException -> error = "That file isn't a valid keyBase vault."
                            else -> error = "Couldn't open the vault (${t.javaClass.simpleName})."
                        }
                    }
                },
                onFail = { cancelled, msg ->
                    busy = false
                    if (!cancelled) error = msg
                },
            )
        }

        fun changePassword(current: String, new: String, report: (String) -> Unit) {
            val ku = keyUriText?.let(Uri::parse)
            if (ku == null) { report("Choose your key file first."); return }
            DeviceKey.deviceSecret(
                this, "Confirm password change",
                onSecret = { secret ->
                    scope.launch {
                        val msg = withContext(Dispatchers.Default) {
                            try {
                                val kh = KeyFile.hash(contentResolver, ku)
                                try {
                                    if (!store.checkPassword(kh, current)) "Current password is wrong."
                                    else { store.changePassword(kh, new, secret); "" }
                                } finally {
                                    kh.fill(0); secret.fill(0)
                                }
                            } catch (e: KeyFileException) {
                                e.message ?: "Key file problem."
                            } catch (e: Exception) {
                                "Couldn't change the password (${e.javaClass.simpleName})."
                            }
                        }
                        if (msg.isEmpty()) {
                            // The saved fingerprint copy holds the old keys, so it must be redone.
                            QuickUnlock.wipe(this@MainActivity)
                            quickEnabled = false
                            toast("Master password changed. Turn fingerprint-only unlock back on if you use it.")
                        }
                        report(msg)
                    }
                },
                onFail = { cancelled, m -> report(if (cancelled) "Cancelled." else m) },
            )
        }

        BackHandler(enabled = !locked && (screen != Screen.Home || currentGroup != null)) {
            if (screen != Screen.Home) screen = Screen.Home
            else currentGroup = store.groups.firstOrNull { it.id == currentGroup }?.parentId
        }

        if (locked) {
            LockScreen(
                firstRun = firstRun,
                keyFileName = keyName,
                quickAvailable = quickEnabled && !firstRun,
                busy = busy,
                error = error,
                canReset = !firstRun,
                securityLabel = securityLabel,
                lockedUntil = lockedUntil,
                restoreName = backupUri?.let { KeyFile.displayName(contentResolver, it) ?: "backup" },
                onPickKeyFile = { holdLock = true; pickKey.launch(Unit) },
                onCreateKeyFile = { holdLock = true; createKey.launch("keybase.key") },
                onPickBackup = { holdLock = true; pickBackup.launch(arrayOf("*/*")) },
                onClearBackup = { backupUri = null },
                onSubmit = ::submit,
                onQuickUnlock = ::startQuickUnlock,
                onReset = { backupUri = null; error = null; resetEverything() },
            )
        } else {
            when (val s = screen) {
                Screen.Home -> VaultScreen(
                    entries = store.entries,
                    groups = store.groups,
                    currentGroup = currentGroup,
                    pathOf = store::pathOf,
                    onGroupChange = { currentGroup = it },
                    onOpen = { screen = Screen.Editor(it, false) },
                    onAdd = { screen = Screen.Editor(Entry(groupId = currentGroup), true) },
                    onAddGroup = { store.addGroup(it, currentGroup) },
                    onRenameGroup = store::renameGroup,
                    onDeleteGroup = { id ->
                        val parent = store.groups.firstOrNull { it.id == id }?.parentId
                        store.deleteGroup(id)
                        currentGroup = parent
                    },
                    onToggleFav = store::toggleFavorite,
                    onSettings = { screen = Screen.Prefs },
                    onGenerator = { screen = Screen.Gen },
                    onLock = ::lockNow,
                    backupNag = lastBackup == 0L && store.entries.isNotEmpty(),
                )
                is Screen.Editor -> EntryScreen(
                    ctx = this@MainActivity,
                    initial = s.entry,
                    isNew = s.isNew,
                    groups = store.groups,
                    pathOf = store::pathOf,
                    canCopyPassword = allowCopy,
                    onCopyUser = { copyToClipboard("Username", it) },
                    onCopyPassword = { if (allowCopy) copyToClipboard("Password", it) },
                    onSave = { store.upsert(it); screen = Screen.Home },
                    onDelete = { store.delete(s.entry.id); screen = Screen.Home },
                    onBack = { screen = Screen.Home },
                )
                Screen.Gen -> GeneratorScreen(
                    ctx = this@MainActivity,
                    canCopy = allowCopy,
                    onCopy = { copyToClipboard("Password", it) },
                    onBack = { screen = Screen.Home },
                )
                Screen.Prefs -> SettingsScreen(
                    themeMode = themeMode,
                    autoLockSec = autoLockSec,
                    quickEnabled = quickEnabled,
                    quickPossible = Biometrics.available(this@MainActivity),
                    allowCopy = allowCopy,
                    clipSec = clipSec,
                    autofillOn = autofillOn,
                    keyFileName = keyName,
                    lastBackup = lastBackup,
                    securityLabel = securityLabel,
                    version = version,
                    onTheme = { themeMode = it; settings.themeMode = it },
                    onAutoLock = { autoLockSec = it; settings.autoLockSec = it },
                    onAllowCopy = { allowCopy = it; settings.allowCopy = it; if (!it) Clip.clearIfOurs(this@MainActivity) },
                    onClipSec = { clipSec = it; settings.clipClearSec = it },
                    onAutofill = {
                        runCatching {
                            startActivity(
                                Intent(AndroidSettings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, Uri.parse("package:$packageName"))
                            )
                        }.onFailure { toast("Open Settings > Passwords > Autofill service and choose keyBase.") }
                    },
                    onQuick = { on ->
                        if (!on) {
                            QuickUnlock.wipe(this@MainActivity); quickEnabled = false
                        } else {
                            val payload = store.keysPayload()
                            QuickUnlock.enable(
                                this@MainActivity, payload,
                                onDone = { payload.fill(0); quickEnabled = true },
                                onFail = { cancelled, msg -> payload.fill(0); if (!cancelled) toast(msg) },
                            )
                        }
                    },
                    onPickKeyFile = { holdLock = true; pickKey.launch(Unit) },
                    onForgetKeyFile = { forgetKeyFile() },
                    onBackup = { holdLock = true; exportBackup.launch("keybase-backup.kbv") },
                    onChangePassword = ::changePassword,
                    onLock = ::lockNow,
                    onBack = { screen = Screen.Home },
                )
            }
        }

        if (newKeyNote) AlertDialog(
            onDismissRequest = { newKeyNote = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Key file created") },
            text = {
                Text(
                    "Make a second copy of this file and keep it somewhere safe, away from your backups. " +
                        "If it is lost or changed, your vault can never be opened again."
                )
            },
            confirmButton = { TextButton(onClick = { newKeyNote = false }) { Text("I understand") } },
        )
    }
}
