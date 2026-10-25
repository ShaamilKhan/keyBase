package com.keybase.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Monospace block of pre-rendered ASCII lettering. [glyphDp] is the width-ish size of one character in dp,
 * so the art keeps its size when the user changes the system font scale.
 */
@Composable
fun AsciiText(
    lines: List<String>,
    glyphDp: Float,
    color: Color,
    description: String,
    modifier: Modifier = Modifier,
    bold: Boolean = true,
) {
    val size = with(LocalDensity.current) { glyphDp.dp.toSp() }
    Text(
        text = lines.joinToString("\n"),
        color = color,
        softWrap = false,
        maxLines = lines.size,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontSize = size,
            lineHeight = size * 1.08f,
            letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        ),
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    )
}

/** The lock, the app name in italic ASCII lettering next to it, and "by shaamil" underneath. */
@Composable
fun BrandHeader(availableWidth: Dp) {
    val iconSize = 56f
    val gap = 14f
    // A monospace character is about 0.6 of its size wide; leave a little margin.
    val fit = (availableWidth.value - iconSize - gap) / (AsciiArt.NAME_COLS * 0.62f)
    val nameGlyph = minOf(9f, fit).coerceAtLeast(4f)
    val byGlyph = nameGlyph * 0.66f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(iconSize.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(gap.dp))
        Column {
            AsciiText(AsciiArt.NAME, nameGlyph, MaterialTheme.colorScheme.onBackground, "keyBase")
            Spacer(Modifier.height(4.dp))
            AsciiText(AsciiArt.BY, byGlyph, MaterialTheme.colorScheme.secondary, "by Shaamil", bold = false)
        }
    }
}
