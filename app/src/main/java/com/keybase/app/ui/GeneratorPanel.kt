package com.keybase.app.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.keybase.app.crypto.GenOptions
import com.keybase.app.crypto.PasswordTools
import com.keybase.app.data.SiteRules

/** Generator controls. [onGenerated] receives each new password. */
@Composable
fun GeneratorPanel(
    ctx: Context,
    url: String,
    onGenerated: (String) -> Unit,
    showResult: Boolean = false,
) {
    var o by remember { mutableStateOf(GenOptions()) }
    var useSite by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf("") }
    val site = remember(url) { SiteRules.lookup(ctx, url) }
    val rules = if (useSite) site?.second else null

    fun run() {
        val p = PasswordTools.generate(ctx, o, rules)
        result = p
        if (p.isNotEmpty()) onGenerated(p)
    }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Generator", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            FilledTonalButton(
                onClick = { run() },
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text("Generate")
            }
        }

        if (showResult && result.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            ColoredPassword(result)
            Spacer(Modifier.height(10.dp))
            StrengthMeter(rememberStrength(result))
        }

        if (site != null) {
            Spacer(Modifier.height(12.dp))
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("Rules for ${site.first}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(SiteRules.describe(site.second), style = MaterialTheme.typography.bodyMedium)
                    ToggleRow("Follow these rules", useSite) { useSite = it }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        val usingSite = rules != null
        if (!usingSite) {
            TabRow(
                selectedTabIndex = if (o.passphrase) 1 else 0,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Tab(selected = !o.passphrase, onClick = { o = o.copy(passphrase = false) }, text = { Text("Characters") })
                Tab(selected = o.passphrase, onClick = { o = o.copy(passphrase = true) }, text = { Text("Words") })
            }
            Spacer(Modifier.height(8.dp))
        }

        if (o.passphrase && !usingSite) {
            Row {
                Text("Words", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${o.words}")
            }
            Slider(value = o.words.toFloat(), onValueChange = { o = o.copy(words = it.toInt()) }, valueRange = 3f..10f, steps = 6)
            Text(
                "About ${"%.0f".format(o.words * 10.3)} bits. Easier to type by hand.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row {
                Text("Length", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (rules != null) "${o.length.coerceIn(rules.minLen.coerceAtLeast(1), rules.maxLen)}" else "${o.length}")
            }
            Slider(value = o.length.toFloat(), onValueChange = { o = o.copy(length = it.toInt()) }, valueRange = 8f..64f)
            if (!usingSite) {
                ToggleRow("Uppercase", o.upper) { o = o.copy(upper = it) }
                ToggleRow("Lowercase", o.lower) { o = o.copy(lower = it) }
                ToggleRow("Numbers", o.digits) { o = o.copy(digits = it) }
                ToggleRow("Symbols", o.symbols) { o = o.copy(symbols = it) }
            }
            ToggleRow("Avoid look-alikes", o.avoidAmbiguous, "Skips I, l, 1, O, 0, o") { o = o.copy(avoidAmbiguous = it) }
        }
    }
}
