package com.keybase.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.keybase.app.crypto.GenOptions
import com.keybase.app.crypto.PasswordTools
import com.keybase.app.data.Entry

private val Pad = 24.dp

@Composable
fun LockScreen(
    firstRun: Boolean,
    keyfileName: String?,
    busy: Boolean,
    error: String?,
    onPickKeyfile: () -> Unit,
    onSubmit: (password: String) -> Unit,
    onPickBackup: (() -> Unit)? = null,
) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    val ready = keyfileName != null && pw.isNotEmpty() && (!firstRun || (pw == pw2 && PasswordTools.estimate(pw).bits >= 40))

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(Pad),
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(20.dp))
        Text(if (firstRun) "Create your vault" else "Welcome back", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            if (firstRun) "Pick a key file, set a master password, then confirm with your fingerprint."
            else "Key file, master password, then fingerprint.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        GhostButton(keyfileName ?: "Choose key file", onPickKeyfile)
        Spacer(Modifier.height(12.dp))
        KField(pw, { pw = it }, "Master password", secret = true)
        if (firstRun) {
            Spacer(Modifier.height(12.dp))
            KField(pw2, { pw2 = it }, "Repeat password", secret = true)
            Spacer(Modifier.height(16.dp))
            StrengthMeter(PasswordTools.estimate(pw))
            if (pw2.isNotEmpty() && pw != pw2) {
                Spacer(Modifier.height(8.dp))
                Text("Passwords don't match", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            if (busy) "Working…" else if (firstRun) "Create vault" else "Unlock",
            { onSubmit(pw) },
            enabled = ready && !busy,
        )
        if (firstRun && onPickBackup != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onPickBackup, modifier = Modifier.fillMaxWidth()) { Text("Restore from backup") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    entries: List<Entry>,
    onOpen: (Entry) -> Unit,
    onAdd: () -> Unit,
    onLock: () -> Unit,
    onBackup: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val list = remember(entries.toList(), q) {
        entries.filter { q.isBlank() || it.title.contains(q, true) || it.username.contains(q, true) || it.url.contains(q, true) }
            .sortedBy { it.title.lowercase() }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.medium,
            ) { Icon(Icons.Filled.Add, "Add") }
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).systemBarsPadding().padding(horizontal = Pad)) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Vault", style = MaterialTheme.typography.headlineLarge)
                    Text("${entries.size} items", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onLock) { Icon(Icons.Filled.Lock, "Lock now") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Export backup") }, onClick = { menu = false; onBackup() })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = q, onValueChange = { q = it },
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = fieldColors(),
            )
            Spacer(Modifier.height(12.dp))
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (entries.isEmpty()) "Nothing here yet.\nTap + to add your first login." else "No matches",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(list, key = { it.id }) { e ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpen(e) }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(e.title.ifBlank { e.url })
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(e.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium)
                                if (e.username.isNotBlank())
                                    Text(e.username, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    initial: Entry,
    isNew: Boolean,
    onSave: (Entry) -> Unit,
    onDelete: () -> Unit,
    onCopy: (label: String, value: String) -> Unit,
    onBack: () -> Unit,
) {
    var e by remember { mutableStateOf(initial) }
    var gen by remember { mutableStateOf(GenOptions()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val strength = remember(e.password) { PasswordTools.estimate(e.password) }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
            Text(if (isNew) "New login" else "Edit login", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (!isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Pad), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            KField(e.title, { e = e.copy(title = it) }, "Title")
            KField(e.username, { e = e.copy(username = it) }, "Username or email")
            if (e.username.isNotBlank()) TextButton(onClick = { onCopy("Username", e.username) }) { Text("Copy username") }
            KField(e.password, { e = e.copy(password = it) }, "Password", secret = true, mono = true)
            StrengthMeter(strength)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onCopy("Password", e.password) }, enabled = e.password.isNotEmpty()) { Text("Copy password") }
            }

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Generator", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = { e = e.copy(password = PasswordTools.generate(gen)) }, shape = MaterialTheme.shapes.small) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("Generate")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Length ${gen.length}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = gen.length.toFloat(), onValueChange = { gen = gen.copy(length = it.toInt()) }, valueRange = 8f..64f)
                ToggleRow("Uppercase", gen.upper) { gen = gen.copy(upper = it) }
                ToggleRow("Lowercase", gen.lower) { gen = gen.copy(lower = it) }
                ToggleRow("Numbers", gen.digits) { gen = gen.copy(digits = it) }
                ToggleRow("Symbols", gen.symbols) { gen = gen.copy(symbols = it) }
                ToggleRow("Avoid look-alikes", gen.avoidAmbiguous) { gen = gen.copy(avoidAmbiguous = it) }
            }

            KField(e.url, { e = e.copy(url = it) }, "Website")
            KField(e.notes, { e = e.copy(notes = it) }, "Notes", singleLine = false, minLines = 3)
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.padding(Pad)) {
            PrimaryButton("Save", { onSave(e) }, enabled = e.title.isNotBlank() || e.url.isNotBlank())
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this login?") },
        text = { Text("This can't be undone.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun ToggleRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = FontWeight.Normal)
        Switch(checked = on, onCheckedChange = onChange)
    }
}
