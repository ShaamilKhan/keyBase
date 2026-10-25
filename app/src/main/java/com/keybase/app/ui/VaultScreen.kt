package com.keybase.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keybase.app.data.Entry
import com.keybase.app.data.Group

private enum class Sort(val label: String) { TITLE("Title"), RECENT("Recently changed"), OLDEST_PW("Oldest password") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    entries: List<Entry>,
    groups: List<Group>,
    currentGroup: String?,
    pathOf: (String?) -> String,
    onGroupChange: (String?) -> Unit,
    onOpen: (Entry) -> Unit,
    onAdd: () -> Unit,
    onAddGroup: (String) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onToggleFav: (String) -> Unit,
    onSettings: () -> Unit,
    onGenerator: () -> Unit,
    onLock: () -> Unit,
    backupNag: String?,
) {
    var q by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(Sort.TITLE) }
    var sortMenu by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var renameFolder by remember { mutableStateOf(false) }
    var deleteFolder by remember { mutableStateOf(false) }
    var folderMenu by remember { mutableStateOf(false) }

    val here = groups.firstOrNull { it.id == currentGroup }
    val searching = q.isNotBlank()

    val subfolders = remember(groups.toList(), currentGroup) {
        groups.filter { it.parentId == currentGroup }.sortedBy { it.name.lowercase() }
    }
    val shown = remember(entries.toList(), currentGroup, q, sort) {
        val base = if (searching) entries.filter {
            it.title.contains(q, true) || it.username.contains(q, true) || it.url.contains(q, true)
        } else entries.filter { it.groupId == currentGroup }
        when (sort) {
            Sort.TITLE -> base.sortedWith(compareByDescending<Entry> { it.favorite }.thenBy { it.title.lowercase() })
            Sort.RECENT -> base.sortedByDescending { it.updated }
            Sort.OLDEST_PW -> base.sortedBy { it.pwChanged }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = KB.Blue,
                contentColor = androidx.compose.ui.graphics.Color.White,
                shape = MaterialTheme.shapes.medium,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
            ) { Icon(Icons.Filled.Add, "Add login") }
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (here != null) {
                    IconButton(onClick = { onGroupChange(here.parentId) }, modifier = Modifier.offset(x = (-12).dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Up one folder")
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(here?.name ?: "Vault", style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (here == null) "${entries.size} logins" else pathOf(currentGroup),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { newFolder = true }) { Icon(Icons.Filled.CreateNewFolder, "Create folder") }
                if (here != null) {
                    Box {
                        IconButton(onClick = { folderMenu = true }) { Icon(Icons.Filled.MoreVert, "Folder options") }
                        DropdownMenu(expanded = folderMenu, onDismissRequest = { folderMenu = false }) {
                            DropdownMenuItem(text = { Text("Rename folder") }, onClick = { folderMenu = false; renameFolder = true })
                            DropdownMenuItem(text = { Text("Delete folder", color = MaterialTheme.colorScheme.error) }, onClick = { folderMenu = false; deleteFolder = true })
                        }
                    }
                } else {
                    IconButton(onClick = onGenerator) { Icon(Icons.Filled.AutoAwesome, "Password generator") }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
                    IconButton(onClick = onLock) { Icon(Icons.Filled.Lock, "Lock now") }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = q, onValueChange = { q = it },
                    placeholder = { Text("Search all logins") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    colors = fieldColors(),
                )
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        Sort.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.label, color = if (s == sort) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                                onClick = { sort = s; sortMenu = false },
                            )
                        }
                    }
                }
            }

            if (backupNag != null && !searching) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onSettings),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Shield, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Text(backupNag, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            val empty = shown.isEmpty() && (searching || subfolders.isEmpty())
            if (empty) {
                Box(Modifier.fillMaxSize().padding(bottom = 96.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            when {
                                searching -> "No matches"
                                entries.isEmpty() && here == null -> "Nothing here yet.\nTap + to add your first login."
                                else -> "This folder is empty."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!searching) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { newFolder = true }) {
                                Icon(Icons.Filled.CreateNewFolder, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (here == null) "Create a folder" else "Create a folder in ${here.name}")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 112.dp)) {
                    if (!searching) {
                        items(subfolders, key = { "g" + it.id }) { g ->
                            val n = entries.count { it.groupId == g.id } + groups.count { it.parentId == g.id }
                            Row(
                                Modifier.fillMaxWidth().clickable { onGroupChange(g.id) }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.Folder, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp).padding(6.dp),
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(g.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("$n items", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                }
                                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Hairline()
                        }
                    }
                    items(shown, key = { it.id }) { e ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpen(e) }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(e.title.ifBlank { e.url })
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sub = if (searching && e.groupId != null) pathOf(e.groupId) + (if (e.username.isNotBlank()) " · " + e.username else "") else e.username
                                if (sub.isNotBlank())
                                    Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { onToggleFav(e.id) }) {
                                if (e.favorite) Icon(Icons.Filled.Star, "Remove from favorites", tint = KB.Warn)
                                else Icon(Icons.Outlined.StarBorder, "Add to favorites", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Hairline()
                    }
                    if (!searching) {
                        item {
                            TextButton(onClick = { newFolder = true }) {
                                Icon(Icons.Filled.CreateNewFolder, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (here == null) "New folder" else "New folder in ${here.name}")
                            }
                        }
                    }
                }
            }
        }
    }

    if (newFolder) NameDialog("New folder", "", "Create", { onAddGroup(it); newFolder = false }, { newFolder = false })
    if (renameFolder && here != null) NameDialog("Rename folder", here.name, "Save", { onRenameGroup(here.id, it); renameFolder = false }, { renameFolder = false })
    if (deleteFolder && here != null) ConfirmDialog(
        title = "Delete \"${here.name}\"?",
        text = "Logins and folders inside it move up one level. Nothing else is deleted.",
        confirm = "Delete folder", danger = true,
        onConfirm = { deleteFolder = false; onDeleteGroup(here.id) },
        onDismiss = { deleteFolder = false },
    )
}
