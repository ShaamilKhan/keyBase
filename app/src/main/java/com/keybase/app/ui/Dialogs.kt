package com.keybase.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*

/**
 * Erasing a vault needs the master password (and the key file and a fingerprint), so a stranger holding the
 * unlocked-looking lock screen can't wipe it. [needsPassword] is false only for a vault nobody can open anyway.
 * [onErase] must call back with "" on success or an error message.
 */
@Composable
fun EraseVaultDialog(
    needsPassword: Boolean,
    hasKeyFile: Boolean,
    onErase: (String, (String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var pw by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val canGo = !working && (!needsPassword || (hasKeyFile && pw.isNotEmpty()))

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Erase this vault?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Every login and folder in it is deleted from this phone for good. " +
                        "Backups you exported and your key file are not touched."
                )
                if (needsPassword) {
                    if (hasKeyFile) {
                        Text("To confirm it is you, enter your master password. You will then be asked for your fingerprint.")
                        KField(pw, { pw = it }, "Master password", secret = true)
                    } else {
                        Text("Choose your key file first. It is the file shown above the password box.")
                    }
                    Text(
                        "Forgot the password? Then nobody can open this vault, including you. " +
                            "To start over, clear keyBase's storage in Android Settings > Apps > keyBase > Storage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "This vault was made by an older test version and can't be opened any more, so no password is asked.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canGo,
                onClick = {
                    working = true; err = null
                    onErase(pw) { msg -> if (msg.isEmpty()) onDismiss() else { err = msg; working = false } }
                },
            ) {
                Text(
                    if (working) "Working…" else "Erase vault",
                    color = if (canGo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text("Cancel") } },
    )
}

/**
 * Proves a backup file works without touching the live vault: it must open with the remembered key file and
 * the master password the backup was made with. [onCheck] calls back with (ok, message).
 */
@Composable
fun CheckBackupDialog(
    fileName: String,
    hasKeyFile: Boolean,
    onCheck: (String, (Boolean, String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var pw by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Check a backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(fileName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!hasKeyFile) {
                    Text("Choose your key file first (Settings > Key file). The backup has to be opened with the key file you used when you made it.")
                } else if (result?.first != true) {
                    Text("Enter the master password you had when you exported this backup. Nothing in your vault is changed.")
                    KField(pw, { pw = it }, "Master password", secret = true)
                }
                result?.let { (ok, msg) ->
                    Text(msg, color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (result?.first == true) {
                TextButton(onClick = onDismiss) { Text("Done") }
            } else {
                TextButton(
                    enabled = hasKeyFile && pw.isNotEmpty() && !working,
                    onClick = {
                        working = true; result = null
                        onCheck(pw) { ok, msg -> result = ok to msg; working = false }
                    },
                ) { Text(if (working) "Checking…" else "Check") }
            }
        },
        dismissButton = {
            if (result?.first != true) TextButton(onClick = onDismiss, enabled = !working) { Text("Cancel") }
        },
    )
}

/** Shown before fingerprint-only unlock is turned on. */
@Composable
fun FingerprintOnlyDisclaimer(days: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Open with fingerprint only?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "With this on, anyone who can use your fingerprint on this phone can open your vault. " +
                        "They won't need your master password or your key file. " +
                        "That includes someone pressing your finger on the sensor while you are asleep or being forced."
                )
                Text(
                    "The vault still can't be opened on any other phone. This switches itself off after $days " +
                        "${if (days == 1) "day" else "days"}, and straight away if a new fingerprint is added to the phone. " +
                        "Then you use your master password again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("I understand, turn on") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
