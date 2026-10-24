package com.keybase.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

const val MIN_MASTER_BITS = 50.0

/** File types that apps and cloud services routinely rewrite, which would silently break a key file. */
private val RISKY_KEY_FILE_TYPES = setOf(
    "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "tif", "tiff",
    "mp4", "mov", "mkv", "avi", "3gp", "mp3", "m4a", "wav",
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt",
)

@Composable
fun LockScreen(
    firstRun: Boolean,
    keyFileName: String?,
    quickAvailable: Boolean,
    busy: Boolean,
    error: String?,
    canReset: Boolean,
    securityLabel: String?,
    lockedUntil: Long,
    restoreName: String?,
    onPickKeyFile: () -> Unit,
    onCreateKeyFile: () -> Unit,
    onPickBackup: () -> Unit,
    onClearBackup: () -> Unit,
    onSubmit: (String) -> Unit,
    onQuickUnlock: () -> Unit,
    onReset: () -> Unit,
) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }
    val strength = rememberStrength(if (firstRun) pw else "")

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lockedUntil) {
        while (System.currentTimeMillis() < lockedUntil) { now = System.currentTimeMillis(); delay(500) }
        now = System.currentTimeMillis()
    }
    val wait = ((lockedUntil - now + 999) / 1000).toInt().coerceAtLeast(0)

    val strongEnough = !firstRun || strength.bits >= MIN_MASTER_BITS
    val ready = keyFileName != null && pw.isNotEmpty() && wait == 0 && !busy &&
        (!firstRun || (pw == pw2 && strongEnough))

    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val minH = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = minH).padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp)) }
                Spacer(Modifier.height(24.dp))
                Text(if (firstRun) "Create your vault" else "Welcome back", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (firstRun) "Choose a key file, set a master password, and everything is encrypted on this phone."
                    else "Enter your master password, then confirm with your fingerprint.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(28.dp))

                if (keyFileName != null) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Key, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Key file", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(keyFileName, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                            }
                            TextButton(onClick = onPickKeyFile) { Text("Change") }
                        }
                    }
                } else if (firstRun) {
                    GhostButton("Create a new key file (recommended)", onCreateKeyFile, icon = Icons.Filled.Key)
                    Text(
                        "keyBase makes a small random file and you save it somewhere safe, like a USB drive. " +
                            "It works like the key file in KeePassDX.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
                    TextButton(onClick = onPickKeyFile, modifier = Modifier.fillMaxWidth()) { Text("Use a file I already have") }
                } else {
                    GhostButton("Choose key file", onPickKeyFile, icon = Icons.Filled.Key)
                }
                val risky = (keyFileName ?: "").substringAfterLast('.', "").lowercase() in RISKY_KEY_FILE_TYPES
                if (risky) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This looks like a photo, video or document. Those get edited, compressed and synced without you noticing, " +
                            "and any change makes the vault impossible to open. A key file made by keyBase is safer.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(12.dp))
                KField(pw, { pw = it }, "Master password", secret = true)
                if (firstRun) {
                    Spacer(Modifier.height(12.dp))
                    KField(pw2, { pw2 = it }, "Repeat master password", secret = true)
                    Spacer(Modifier.height(16.dp))
                    StrengthMeter(strength)
                    Spacer(Modifier.height(8.dp))
                    val hint = when {
                        pw.isNotEmpty() && !strongEnough -> "Needs at least ${MIN_MASTER_BITS.toInt()} bits. Try more words or characters."
                        pw2.isNotEmpty() && pw != pw2 -> "The two passwords don't match."
                        else -> "Longer is better. Aim for 80 bits or more."
                    }
                    Text(
                        hint, style = MaterialTheme.typography.bodyMedium,
                        color = if (pw.isNotEmpty() && (!strongEnough || (pw2.isNotEmpty() && pw != pw2))) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (restoreName != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Restoring from backup", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = onClearBackup) { Text("Remove") }
                        }
                    }
                }

                if (wait > 0) {
                    Spacer(Modifier.height(16.dp))
                    Text("Too many attempts. Try again in ${wait}s.", color = MaterialTheme.colorScheme.error)
                } else if (error != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(error, color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.height(24.dp))
                PrimaryButton(
                    if (busy) "Working…" else if (firstRun) (if (restoreName != null) "Restore vault" else "Create vault") else "Unlock",
                    { onSubmit(pw) }, enabled = ready,
                    icon = if (busy) null else Icons.Filled.Fingerprint,
                )
                if (!firstRun && quickAvailable) {
                    Spacer(Modifier.height(12.dp))
                    GhostButton("Fingerprint only, no password", onQuickUnlock, icon = Icons.Filled.Fingerprint)
                }
                if (firstRun && restoreName == null) {
                    TextButton(onClick = onPickBackup, modifier = Modifier.fillMaxWidth()) { Text("Restore from a backup") }
                }
                if (canReset) {
                    TextButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Erase this vault and start over", color = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    buildString {
                        append("Encrypted on this phone, no internet access")
                        if (securityLabel != null) append(" · key in $securityLabel")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (confirmReset) ConfirmDialog(
        title = "Erase this vault?",
        text = "Everything stored in it is deleted from this phone. Backups you exported are not affected. This can't be undone.",
        confirm = "Erase",
        danger = true,
        onConfirm = { confirmReset = false; pw = ""; pw2 = ""; onReset() },
        onDismiss = { confirmReset = false },
    )
}
