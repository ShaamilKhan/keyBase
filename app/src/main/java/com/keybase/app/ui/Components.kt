package com.keybase.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.keybase.app.crypto.Strength

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
)

@Composable
fun KField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    mono: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    var shown by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        shape = MaterialTheme.shapes.medium,
        colors = fieldColors(),
        textStyle = if (mono) Mono else MaterialTheme.typography.bodyLarge,
        visualTransformation = if (secret && !shown) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text,
            autoCorrectEnabled = false,
        ),
        trailingIcon = if (secret) {
            { TextButton(onClick = { shown = !shown }) { Text(if (shown) "Hide" else "Show") } }
        } else null,
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = MaterialTheme.shapes.medium,
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = KB.Blue,
            contentColor = androidx.compose.ui.graphics.Color.White,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface) }
}

@Composable
fun Avatar(title: String, size: Int = 44) {
    val letter = title.trim().firstOrNull()?.uppercase() ?: "•"
    val hue = (title.hashCode().and(0x7fffffff) % 360).toFloat()
    val bg = Color.hsl(hue, 0.35f, 0.22f)
    val fg = Color.hsl(hue, 0.75f, 0.78f)
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(14.dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) { Text(letter, color = fg, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun StrengthMeter(s: Strength, modifier: Modifier = Modifier) {
    val target = when {
        s.bits < 28 -> KB.Bad
        s.bits < 40 -> Color(0xFFFB923C)
        s.bits < 60 -> KB.Warn
        else -> KB.Ok
    }
    val color by animateColorAsState(target, label = "c")
    val frac by animateFloatAsState(s.fraction.coerceAtLeast(0.03f), label = "f")
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) { Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(CircleShape).background(color)) }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(s.label, style = MaterialTheme.typography.labelMedium, color = color)
            Text(
                "${s.bits.toInt()} bits",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) { Column(Modifier.padding(20.dp), content = content) }
}
