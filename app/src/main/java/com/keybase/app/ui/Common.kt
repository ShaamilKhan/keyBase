package com.keybase.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keybase.app.crypto.Entropy
import com.keybase.app.crypto.Strength
import com.keybase.app.data.Group
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Strength of [pw], computed off the main thread and debounced while typing. */
@Composable
fun rememberStrength(pw: String): Strength {
    var s by remember { mutableStateOf(Strength(0.0, "Empty", 0)) }
    LaunchedEffect(pw) {
        if (pw.isEmpty()) { s = Strength(0.0, "Empty", 0); return@LaunchedEffect }
        delay(120)
        s = withContext(Dispatchers.Default) { Entropy.estimate(pw) }
    }
    return s
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        } else {
            Spacer(Modifier.width(16.dp))
        }
        Text(
            title, style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = v, onValueChange = { v = it.take(60) }, singleLine = true,
                shape = MaterialTheme.shapes.medium, colors = fieldColors(),
            )
        },
        confirmButton = { TextButton(onClick = { onDone(v) }, enabled = v.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String, text: String, confirm: String, danger: Boolean = false,
    onConfirm: () -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Folders in display order with their depth. Guards against cycles in damaged data. */
fun flattenGroups(groups: List<Group>): List<Pair<Group, Int>> {
    val out = ArrayList<Pair<Group, Int>>()
    val seen = HashSet<String>()
    fun walk(parent: String?, depth: Int) {
        if (depth > 12) return
        groups.filter { it.parentId == parent }.sortedBy { it.name.lowercase() }.forEach { g ->
            if (seen.add(g.id)) { out.add(g to depth); walk(g.id, depth + 1) }
        }
    }
    walk(null, 0)
    return out
}

fun formatDate(ts: Long): String =
    if (ts <= 0L) "Unknown" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ts))
