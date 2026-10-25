package com.keybase.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keybase.app.crypto.Entropy
import kotlinx.coroutines.delay

/** File types that apps and cloud services routinely rewrite, which would silently break a key file. */
private val RISKY_KEY_FILE_TYPES = setOf(
    "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "tif", "tiff",
    "mp4", "mov", "mkv", "avi", "3gp", "mp3", "m4a", "wav",
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockScreen(
    firstRun: Boolean,
    keyFileName: String?,
    quickAvailable: Boolean,
    busy: Boolean,
    error: String?,
    canErase: Boolean,
    eraseNeedsPassword: Boolean,
    securityLabel: String?,
    version: String,
    lockedUntil: Long,
    restoreName: String?,
    onPickKeyFile: () -> Unit,
    onForgetKeyFile: () -> Unit,
    onCreateKeyFile: () -> Unit,
    onPickBackup: () -> Unit,
    onClearBackup: () -> Unit,
    onSubmit: (String) -> Unit,
    onQuickUnlock: () -> Unit,
    onErase: (String, (String) -> Unit) -> Unit,
) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var erasing by remember { mutableStateOf(false) }
    var restoreTab by remember { mutableStateOf(false) }

    // Restoring is its own mode: no repeat box and no strength gate, because the password already exists.
    val restoring = firstRun && (restoreTab || restoreName != null)
    val creating = firstRun && !restoring
    val strength = rememberStrength(if (creating) pw else "")

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lockedUntil) {
        while (System.currentTimeMillis() < lockedUntil) { now = System.currentTimeMillis(); delay(500) }
        now = System.currentTimeMillis()
    }
    val wait = ((lockedUntil - now + 999) / 1000).toInt().coerceAtLeast(0)

    val strongEnough = strength.bits >= Entropy.MIN_MASTER_BITS
    val ready = keyFileName != null && pw.isNotEmpty() && wait == 0 && !busy &&
        (!creating || (pw == pw2 && strongEnough)) &&
        (!restoring || restoreName != null)

    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val minH = maxHeight
        val contentWidth = maxWidth - 48.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = minH).padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                BrandHeader(contentWidth)
                Spacer(Modifier.height(28.dp))
                Text(
                    when {
                        creating -> "Create your vault"
                        restoring -> "Restore your vault"
                        else -> "Welcome back"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        creating -> "Choose a key file, set a master password, and everything is encrypted on this phone."
                        restoring -> "Bring back the logins from a backup you exported earlier."
                        else -> "Enter your master password, then confirm with your fingerprint."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )

                if (firstRun) {
                    Spacer(Modifier.height(20.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !restoring, onClick = { restoreTab = false; onClearBackup() },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) { Text("New vault") }
                        SegmentedButton(
                            selected = restoring, onClick = { restoreTab = true },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) { Text("Restore backup") }
                    }
                }
                Spacer(Modifier.height(24.dp))

                // 1. Backup file (restore only)
                if (restoring) {
                    if (restoreName != null) {
                        FileRow(Icons.Filled.Save, "Backup file", restoreName, onPickBackup)
                    } else {
                        GhostButton("Choose backup file", onPickBackup, icon = Icons.Filled.Save)
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // 2. Key file
                if (keyFileName != null) {
                    FileRow(
                        Icons.Filled.Key, "Key file", keyFileName,
                        onChange = onPickKeyFile,
                        onRemove = onForgetKeyFile,
                        maxNameLines = 2,
                    )
                    if (creating) {
                        TextButton(onClick = onCreateKeyFile, modifier = Modifier.fillMaxWidth()) { Text("Create a new key file instead") }
                    }
                } else if (creating) {
                    GhostButton("Create a new key file (recommended)", onCreateKeyFile, icon = Icons.Filled.Key)
                    Text(
                        "keyBase makes a small random file and you save it somewhere safe, like a USB drive. " +
                            "It works like the key file in KeePassDX.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
                    TextButton(onClick = onPickKeyFile, modifier = Modifier.fillMaxWidth()) { Text("Use a file I already have") }
                } else if (restoring) {
                    GhostButton("Choose the key file from that time", onPickKeyFile, icon = Icons.Filled.Key)
                    Text(
                        "It must be the same file you used when you exported the backup. Don't create a new one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
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

                // 3. Master password
                Spacer(Modifier.height(12.dp))
                KField(
                    pw, { pw = it },
                    if (restoring) "Master password of the backup" else "Master password",
                    secret = true,
                )
                if (creating) {
                    Spacer(Modifier.height(12.dp))
                    KField(pw2, { pw2 = it }, "Repeat master password", secret = true)
                    Spacer(Modifier.height(16.dp))
                    StrengthMeter(strength)
                    Spacer(Modifier.height(8.dp))
                    val mismatch = pw2.isNotEmpty() && pw != pw2
                    val tooWeak = pw.isNotEmpty() && !strongEnough
                    Text(
                        when {
                            tooWeak -> "Needs at least ${Entropy.MIN_MASTER_BITS.toInt()} bits. Add more words or characters."
                            mismatch -> "The two passwords don't match."
                            else -> "Longer is better. A few random words work well."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (tooWeak || mismatch) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                    when {
                        busy -> "Working…"
                        creating -> "Create vault"
                        restoring -> "Restore vault"
                        else -> "Unlock"
                    },
                    { onSubmit(pw) }, enabled = ready,
                    icon = if (busy) null else Icons.Filled.Fingerprint,
                )
                if (!firstRun && quickAvailable) {
                    Spacer(Modifier.height(12.dp))
                    GhostButton("Fingerprint only, no password", onQuickUnlock, icon = Icons.Filled.Fingerprint)
                }
                if (canErase) {
                    TextButton(onClick = { erasing = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Erase this vault and start over", color = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    buildString {
                        append("v").append(version).append(" · encrypted on this phone, no internet access")
                        if (securityLabel != null) append(" · key in ").append(securityLabel)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (erasing) EraseVaultDialog(
        needsPassword = eraseNeedsPassword,
        hasKeyFile = keyFileName != null,
        onErase = { password, report -> onErase(password) { msg -> if (msg.isEmpty()) { pw = ""; pw2 = "" }; report(msg) } },
        onDismiss = { erasing = false },
    )
}

@Composable
private fun FileRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    name: String,
    onChange: () -> Unit,
    onRemove: (() -> Unit)? = null,
    maxNameLines: Int = 1,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(name, maxLines = maxNameLines, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (onRemove != null) TextButton(onClick = onRemove) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onChange) { Text("Change") }
            }
        }
    }
}
