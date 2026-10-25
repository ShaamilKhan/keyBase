package com.keybase.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.keybase.app.crypto.Entropy
import com.keybase.app.crypto.QuickUnlock

private val LOCK_CHOICES = listOf(0 to "Immediately", 15 to "15 seconds", 60 to "1 minute", 300 to "5 minutes")
private val THEME_CHOICES = listOf(0 to "System", 1 to "Light", 2 to "Dark")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: Int,
    autoLockSec: Int,
    quickEnabled: Boolean,
    quickPossible: Boolean,
    quickDays: Int,
    allowCopy: Boolean,
    clipSec: Int,
    autofillOn: Boolean,
    keyFileName: String?,
    lastBackup: Long,
    changesSinceBackup: Int,
    securityLabel: String?,
    version: String,
    onTheme: (Int) -> Unit,
    onAutoLock: (Int) -> Unit,
    onQuick: (Boolean) -> Unit,
    onQuickDays: (Int) -> Unit,
    onAllowCopy: (Boolean) -> Unit,
    onClipSec: (Int) -> Unit,
    onAutofill: () -> Unit,
    onPickKeyFile: () -> Unit,
    onForgetKeyFile: () -> Unit,
    onBackup: () -> Unit,
    onCheckBackup: () -> Unit,
    onChangePassword: (current: String, new: String, onError: (String) -> Unit) -> Unit,
    onLock: () -> Unit,
    onBack: () -> Unit,
) {
    var pwDialog by remember { mutableStateOf(false) }
    var askQuick by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        TopBar("Settings", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            SectionLabel("Appearance")
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                THEME_CHOICES.forEachIndexed { i, (v, label) ->
                    SegmentedButton(
                        selected = themeMode == v,
                        onClick = { onTheme(v) },
                        shape = SegmentedButtonDefaults.itemShape(i, THEME_CHOICES.size),
                    ) { Text(label) }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("Security")
            var lockMenu by remember { mutableStateOf(false) }
            Box {
                NavRow(
                    "Auto-lock", LOCK_CHOICES.firstOrNull { it.first == autoLockSec }?.second ?: "${autoLockSec}s",
                    Icons.Filled.Timer, onClick = { lockMenu = true },
                )
                DropdownMenu(expanded = lockMenu, onDismissRequest = { lockMenu = false }) {
                    LOCK_CHOICES.forEach { (v, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { onAutoLock(v); lockMenu = false })
                    }
                }
            }
            Text(
                "How long keyBase stays open after you switch away. Leaving the screen off always locks it.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 40.dp, bottom = 8.dp),
            )
            Hairline()
            if (quickPossible) {
                ToggleRow(
                    "Open with fingerprint only", quickEnabled,
                    "Skips the master password and key file. Read the warning before turning this on.",
                    onChange = { on -> if (on) askQuick = true else onQuick(false) },
                )
                if (quickEnabled) {
                    var daysMenu by remember { mutableStateOf(false) }
                    Box {
                        NavRow(
                            "Switch off after", "$quickDays ${if (quickDays == 1) "day" else "days"}",
                            onClick = { daysMenu = true },
                        )
                        DropdownMenu(expanded = daysMenu, onDismissRequest = { daysMenu = false }) {
                            QuickUnlock.DAY_CHOICES.forEach { d ->
                                DropdownMenuItem(
                                    text = { Text("$d ${if (d == 1) "day" else "days"}") },
                                    onClick = { onQuickDays(d); daysMenu = false },
                                )
                            }
                        }
                    }
                }
                Hairline()
            }
            NavRow("Change master password", null, Icons.Filled.Password, onClick = { pwDialog = true })
            Hairline()
            NavRow("Lock now", null, Icons.Filled.Lock, onClick = onLock)

            Spacer(Modifier.height(24.dp))
            SectionLabel("Copy and autofill")
            ToggleRow(
                "Copy passwords", allowCopy,
                "Adds a Copy button for passwords. Usernames can always be copied.",
                onChange = onAllowCopy,
            )
            if (allowCopy) {
                var clipMenu by remember { mutableStateOf(false) }
                Box {
                    NavRow("Clear clipboard after", "$clipSec seconds", onClick = { clipMenu = true })
                    DropdownMenu(expanded = clipMenu, onDismissRequest = { clipMenu = false }) {
                        listOf(10, 20, 30, 60).forEach { s ->
                            DropdownMenuItem(text = { Text("$s seconds") }, onClick = { onClipSec(s); clipMenu = false })
                        }
                    }
                }
            }
            Hairline()
            NavRow(
                "Autofill",
                if (autofillOn) "On. Each fill asks for your fingerprint." else "Off. Tap to choose keyBase as your autofill service.",
                Icons.Filled.Password,
                onClick = onAutofill,
            )
            Text(
                "Autofill needs an exact match on the login's website address (https) or app package, inside Chrome, Firefox, Brave, Edge, DuckDuckGo, Vivaldi or Chromium. " +
                    "A locked vault is only offered for fingerprint-only unlock.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 40.dp, bottom = 8.dp),
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("Key file")
            NavRow(
                keyFileName ?: "No key file remembered",
                "Remembered so you only type your password. Tap to point keyBase at another copy of the same file.",
                Icons.Filled.Key, onClick = onPickKeyFile,
            )
            if (keyFileName != null) {
                Hairline()
                NavRow("Forget key file", "You will choose it again at the next unlock", Icons.Filled.LinkOff, onClick = onForgetKeyFile)
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("Backup")
            NavRow(
                "Export encrypted backup",
                when {
                    lastBackup <= 0L -> "Never backed up"
                    changesSinceBackup > 0 -> "Last backup ${formatDate(lastBackup)}. $changesSinceBackup change${if (changesSinceBackup == 1) "" else "s"} since."
                    else -> "Last backup ${formatDate(lastBackup)}. Up to date."
                },
                Icons.Filled.Save, onClick = onBackup,
            )
            Hairline()
            NavRow("Check a backup file", "Make sure a backup opens before you need it", Icons.Filled.Verified, onClick = onCheckBackup)
            Text(
                "A backup is a snapshot. To use it later: erase the vault (or install on a new phone), tap Restore backup on the " +
                    "first screen, then choose the backup, the same key file and the master password you had when you exported it. " +
                    "Keep it away from your key file.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 40.dp, bottom = 8.dp),
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("About")
            Spacer(Modifier.height(8.dp))
            SectionCard {
                AsciiText(AsciiArt.NAME, 6f, MaterialTheme.colorScheme.onBackground, "keyBase")
                Spacer(Modifier.height(4.dp))
                AsciiText(AsciiArt.BY, 4f, MaterialTheme.colorScheme.secondary, "by Shaamil", bold = false)
                Spacer(Modifier.height(14.dp))
                InfoRow("Version", version)
                InfoRow("Encryption", "AES-256-GCM")
                InfoRow("Key derivation", "Argon2id, 128 MiB")
                InfoRow("Hardware key", securityLabel ?: "Not created")
                InfoRow("Network", "No internet permission")
                InfoRow("Clipboard", if (allowCopy) "Cleared after ${clipSec}s" else "Passwords never copied")
                InfoRow("Autofill", if (autofillOn) "On" else "Off")
            }
            Spacer(Modifier.height(16.dp))
            SectionCard {
                Text("Developed by", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Shaamil Khan A", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                InfoRow("GitHub", "github.com/ShaamilKhan")
                InfoRow("Instagram", "@afriafk")
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (askQuick) FingerprintOnlyDisclaimer(
        days = quickDays,
        onConfirm = { askQuick = false; onQuick(true) },
        onDismiss = { askQuick = false },
    )
    if (pwDialog) ChangePasswordDialog(
        onSubmit = onChangePassword,
        onDone = { pwDialog = false },
    )
}

@Composable
private fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(k, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(v, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ChangePasswordDialog(
    onSubmit: (String, String, (String) -> Unit) -> Unit,
    onDone: () -> Unit,
) {
    var cur by remember { mutableStateOf("") }
    var n1 by remember { mutableStateOf("") }
    var n2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val strength = rememberStrength(n1)
    val ok = cur.isNotEmpty() && n1 == n2 && strength.bits >= Entropy.MIN_MASTER_BITS && !working

    AlertDialog(
        onDismissRequest = { if (!working) onDone() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Change master password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KField(cur, { cur = it }, "Current password", secret = true)
                KField(n1, { n1 = it }, "New password", secret = true)
                KField(n2, { n2 = it }, "Repeat new password", secret = true)
                if (n1.isNotEmpty()) StrengthMeter(strength)
                if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(
                enabled = ok,
                onClick = {
                    working = true; err = null
                    onSubmit(cur, n1) { msg -> if (msg.isEmpty()) onDone() else { err = msg; working = false } }
                },
            ) { Text(if (working) "Working…" else "Change") }
        },
        dismissButton = { TextButton(onClick = onDone, enabled = !working) { Text("Cancel") } },
    )
}
