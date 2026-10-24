package com.keybase.app.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.keybase.app.data.Entry
import com.keybase.app.data.Group
import com.keybase.app.data.SiteRules

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    ctx: Context,
    initial: Entry,
    isNew: Boolean,
    groups: List<Group>,
    pathOf: (String?) -> String,
    canCopyPassword: Boolean,
    onCopyUser: (String) -> Unit,
    onCopyPassword: (String) -> Unit,
    onSave: (Entry) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var e by remember { mutableStateOf(initial) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var revealHistory by remember { mutableStateOf(false) }
    var revealPw by remember { mutableStateOf(false) }
    var editingPw by remember { mutableStateOf(isNew) }
    var folderMenu by remember { mutableStateOf(false) }
    val dirty = e != initial
    val strength = rememberStrength(e.password)
    val flat = remember(groups.toList()) { flattenGroups(groups) }

    fun back() { if (dirty) confirmDiscard = true else onBack() }
    androidx.activity.compose.BackHandler(onBack = ::back)

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        TopBar(
            title = if (isNew) "New login" else e.title.ifBlank { "Login" },
            onBack = ::back,
            actions = {
                if (!isNew) IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, "Delete login", tint = MaterialTheme.colorScheme.error)
                }
            },
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            KField(e.title, { e = e.copy(title = it) }, "Title")
            KField(e.username, { e = e.copy(username = it) }, "Username or email", onCopy = { onCopyUser(e.username) })

            if (editingPw) {
                KField(e.password, { e = e.copy(password = it) }, "Password", secret = true, mono = true)
                if (canCopyPassword && e.password.isNotEmpty()) {
                    TextButton(onClick = { onCopyPassword(e.password) }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy password")
                    }
                }
            } else {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Password", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        TextButton(onClick = { editingPw = true }) { Text("Edit") }
                    }
                    if (revealPw) ColoredPassword(e.password) else Text("••••••••••••", style = Mono)
                    Spacer(Modifier.height(4.dp))
                    Row {
                        TextButton(onClick = { revealPw = !revealPw }, contentPadding = PaddingValues(end = 12.dp)) {
                            Icon(if (revealPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (revealPw) "Hide" else "Show")
                        }
                        if (canCopyPassword) {
                            TextButton(onClick = { onCopyPassword(e.password) }) {
                                Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy")
                            }
                        }
                    }
                }
            }
            if (e.password.isNotEmpty()) StrengthMeter(strength)

            if (editingPw) {
                GeneratorPanel(ctx, e.url, onGenerated = { e = e.copy(password = it) })
            }

            KField(
                e.url, { e = e.copy(url = it) }, "Website",
                supporting = "Exact address, e.g. https://github.com. Used for autofill and the site's password rules.",
            )
            KField(
                e.appId, { e = e.copy(appId = it.trim()) }, "App package (optional)",
                supporting = "For autofill in an app, e.g. com.netflix.mediaclient. It must match exactly.",
            )

            // Folder
            Box {
                OutlinedButton(
                    onClick = { folderMenu = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("Folder", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(if (e.groupId == null) "None" else pathOf(e.groupId), color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                }
                DropdownMenu(expanded = folderMenu, onDismissRequest = { folderMenu = false }) {
                    DropdownMenuItem(text = { Text("None") }, onClick = { e = e.copy(groupId = null); folderMenu = false })
                    flat.forEach { (g, depth) ->
                        DropdownMenuItem(
                            text = { Text("    ".repeat(depth) + g.name) },
                            onClick = { e = e.copy(groupId = g.id); folderMenu = false },
                        )
                    }
                }
            }

            KField(e.notes, { e = e.copy(notes = it) }, "Notes", singleLine = false, minLines = 3)

            if (!isNew) {
                SectionCard {
                    Text("Details", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    DetailRow("Created", formatDate(e.created))
                    DetailRow("Last changed", formatDate(e.updated))
                    DetailRow("Password changed", formatDate(e.pwChanged))
                    if (e.history.isNotEmpty()) {
                        TextButton(onClick = { showHistory = !showHistory }, contentPadding = PaddingValues(0.dp)) {
                            Text(if (showHistory) "Hide old passwords" else "Old passwords (${e.history.size})")
                        }
                        if (showHistory) {
                            e.history.forEach { h ->
                                Column(Modifier.padding(vertical = 6.dp)) {
                                    Text("Replaced ${formatDate(h.replaced)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (revealHistory) ColoredPassword(h.password, size = 16) else Text("••••••••••••", style = Mono)
                                }
                            }
                            TextButton(onClick = { revealHistory = !revealHistory }, contentPadding = PaddingValues(0.dp)) {
                                Text(if (revealHistory) "Hide" else "Show on screen")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            PrimaryButton("Save", { onSave(e) }, enabled = dirty && (e.title.isNotBlank() || e.url.isNotBlank()))
        }
    }

    if (confirmDelete) ConfirmDialog(
        title = "Delete this login?", text = "This can't be undone.", confirm = "Delete", danger = true,
        onConfirm = { confirmDelete = false; onDelete() }, onDismiss = { confirmDelete = false },
    )
    if (confirmDiscard) ConfirmDialog(
        title = "Discard changes?", text = "Your edits to this login haven't been saved.", confirm = "Discard", danger = true,
        onConfirm = { confirmDiscard = false; onBack() }, onDismiss = { confirmDiscard = false },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
