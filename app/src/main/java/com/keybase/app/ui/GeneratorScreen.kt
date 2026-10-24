package com.keybase.app.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Stand-alone generator: shows each result on screen, with a Copy button when copying is on. */
@Composable
fun GeneratorScreen(ctx: Context, canCopy: Boolean, onCopy: (String) -> Unit, onBack: () -> Unit) {
    var last by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        TopBar("Generator", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GeneratorPanel(ctx, url = "", onGenerated = { last = it }, showResult = true)
            if (canCopy && last.isNotEmpty()) {
                TextButton(onClick = { onCopy(last) }) {
                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy password")
                }
            }
            Text(
                "To keep a password, open a login and tap Generate there.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
