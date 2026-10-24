package com.keybase.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
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

/** Text field with autocorrect off. Secret fields use the password keyboard type, which turns suggestions off. */
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
    supporting: String? = null,
    onCopy: (() -> Unit)? = null,
) {
    var shown by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        // Marks secret fields as password fields for screen readers even while the text is shown.
        modifier = modifier.fillMaxWidth().then(if (secret) Modifier.semantics { password() } else Modifier),
        singleLine = singleLine,
        minLines = minLines,
        shape = MaterialTheme.shapes.medium,
        colors = fieldColors(),
        textStyle = if (mono) Mono.copy(color = MaterialTheme.colorScheme.onSurface) else MaterialTheme.typography.bodyLarge,
        visualTransformation = if (secret && !shown) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text,
            autoCorrectEnabled = false,
        ),
        supportingText = supporting?.let { { Text(it) } },
        trailingIcon = when {
            secret -> ({ TextButton(onClick = { shown = !shown }) { Text(if (shown) "Hide" else "Show") } })
            onCopy != null -> ({
                IconButton(onClick = onCopy, enabled = value.isNotEmpty()) {
                    Icon(Icons.Filled.ContentCopy, "Copy $label")
                }
            })
            else -> null
        },
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = MaterialTheme.shapes.medium,
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = KB.Blue,
            contentColor = Color.White,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Password with letters, digits and symbols in different colours, easier to read and type by hand. */
@Composable
fun ColoredPassword(pw: String, modifier: Modifier = Modifier, size: Int = 20) {
    val dark = LocalDark.current
    val digit = if (dark) KB.BlueSoft else KB.Blue
    val symbol = if (dark) KB.Orange else KB.BadLight
    val letter = MaterialTheme.colorScheme.onSurface
    val text = buildAnnotatedString {
        pw.forEach { c ->
            val col = when {
                c.isDigit() -> digit
                c.isLetter() -> letter
                else -> symbol
            }
            withStyle(SpanStyle(color = col)) { append(c) }
        }
    }
    // clearAndSetSemantics removes the text from the accessibility tree, so screen-reading apps can't see it.
    Text(
        text,
        modifier = modifier.clearAndSetSemantics { },
        style = Mono.copy(fontSize = size.sp, lineHeight = (size * 1.5f).sp, letterSpacing = 1.sp),
    )
}

@Composable
fun Avatar(title: String, size: Int = 44) {
    val dark = LocalDark.current
    val letter = title.trim().firstOrNull()?.uppercase() ?: "•"
    val hue = (title.hashCode().and(0x7fffffff) % 360).toFloat()
    val bg = if (dark) Color.hsl(hue, 0.35f, 0.18f) else Color.hsl(hue, 0.55f, 0.90f)
    val fg = if (dark) Color.hsl(hue, 0.75f, 0.75f) else Color.hsl(hue, 0.60f, 0.32f)
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size / 3.1f).dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) { Text(letter, color = fg, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun StrengthMeter(s: Strength, modifier: Modifier = Modifier) {
    val target = strengthColor(s.level)
    val color by animateColorAsState(target, label = "c")
    val frac by animateFloatAsState((s.bits / 100.0).toFloat().coerceIn(0.03f, 1f), label = "f")
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) { Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(CircleShape).background(color)) }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(s.label, style = MaterialTheme.typography.labelMedium, color = color)
            Text(
                "${"%.1f".format(s.bits)} bits",
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) { Column(Modifier.padding(20.dp), content = content) }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
fun ToggleRow(label: String, on: Boolean, detail: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!on) }.heightIn(min = 48.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
fun NavRow(label: String, detail: String? = null, icon: ImageVector? = null, danger: Boolean = false, onClick: () -> Unit) {
    val c = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = if (danger) c else MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(16.dp)) }
        Column(Modifier.weight(1f)) {
            Text(label, color = c, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Hairline() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
