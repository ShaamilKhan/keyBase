package com.keybase.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ASCII lettering drawn as lines on a fixed grid. It is drawn, not typed, so it looks the same on every phone:
 * a font would change the width and height of every character from one device to the next and distort the art.
 */
@Composable
fun AsciiLettering(
    lines: List<String>,
    cellWidth: Dp,
    color: Color,
    description: String,
    modifier: Modifier = Modifier,
    minStroke: Dp = 1.1.dp,
) {
    val cols = remember(lines) { lines.maxOf { it.length } }
    val cellHeight = cellWidth * AsciiGlyphs.CELL_ASPECT
    val stroke = maxOf(minStroke.value, cellWidth.value * 0.17f)
    Canvas(
        modifier
            .size(cellWidth * cols, cellHeight * lines.size)
            .clearAndSetSemantics { contentDescription = description }
    ) {
        val cw = cellWidth.toPx()
        val chPx = cellHeight.toPx()
        val style = Stroke(width = stroke.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        lines.forEachIndexed { row, line ->
            line.forEachIndexed { col, c ->
                val polylines = AsciiGlyphs.STROKES[c] ?: return@forEachIndexed
                for (pts in polylines) {
                    val path = Path()
                    var i = 0
                    while (i + 1 < pts.size) {
                        val x = (col + pts[i]) * cw
                        val y = (row + pts[i + 1]) * chPx
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        i += 2
                    }
                    drawPath(path, color, style = style)
                }
            }
        }
    }
}

/** "keybase" with "by shaamil" under it in blue, sized to fit [maxWidth] (never wider, never above [maxCell] per character). */
@Composable
fun BrandLettering(maxWidth: Dp, maxCell: Dp = 8.5.dp, byShare: Float = 0.75f) {
    val nameCols = remember { AsciiArt.NAME.maxOf { it.length } }
    val byCols = remember { AsciiArt.BY.maxOf { it.length } }
    val nameCell = minOf(maxWidth.value / nameCols, maxCell.value).coerceAtLeast(2f)
    // The tagline is a fixed share of the name's width, so the two always keep the same proportions.
    val byCell = nameCell * nameCols * byShare / byCols
    Column {
        AsciiLettering(AsciiArt.NAME, nameCell.dp, MaterialTheme.colorScheme.onBackground, "keybase")
        Spacer(Modifier.height(6.dp))
        AsciiLettering(AsciiArt.BY, byCell.dp, MaterialTheme.colorScheme.secondary, "by shaamil", minStroke = 0.9.dp)
    }
}

/** The lock tile with the lettering next to it. */
@Composable
fun BrandHeader(availableWidth: Dp) {
    val tile = 52.dp
    val gap = 12.dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(tile).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(gap))
        BrandLettering(availableWidth - tile - gap)
    }
}
