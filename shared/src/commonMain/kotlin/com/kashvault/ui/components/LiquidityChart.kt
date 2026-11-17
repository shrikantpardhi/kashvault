package com.kashvault.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kashvault.util.HorizonPoint

@Composable
fun LiquidityChart(
    points: List<HorizonPoint>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        if (points.size < 2) return@Canvas

        val min = points.minOf { it.balance }.toFloat()
        val max = points.maxOf { it.balance }.toFloat().coerceAtLeast(min + 1f)
        val range = max - min
        val stepX = size.width / (points.size - 1).toFloat()

        for (i in 0..3) {
            val y = size.height * i / 3f
            drawLine(color = gridColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
        }

        val path = Path()
        points.forEachIndexed { index, point ->
            val x = stepX * index
            val y = size.height - ((point.balance.toFloat() - min) / range) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = lineColor, style = Stroke(width = 6f, cap = StrokeCap.Round))
    }
}
