package com.kashvault.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kashvault.ui.markdown.parseStyledMarkdown

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val parsed = remember(markdown) { parseStyledMarkdown(markdown) }
    val bodySize = style.fontSize
    val headingTwo = (if (bodySize.isSp) bodySize.value * 1.15f else 16f).sp
    val headingOne = (if (bodySize.isSp) bodySize.value * 1.28f else 18f).sp
    val annotated = remember(parsed, color, style) {
        buildAnnotatedString {
            append(parsed.text)
            parsed.styles.forEach { range ->
                if (range.start >= range.end || range.start < 0 || range.end > parsed.text.length) return@forEach
                addStyle(
                    SpanStyle(
                        fontWeight = if (range.bold || range.heading > 0) FontWeight.SemiBold else FontWeight.Normal,
                        fontStyle = if (range.italic) FontStyle.Italic else FontStyle.Normal,
                        fontFamily = if (range.code) FontFamily.Monospace else style.fontFamily,
                        fontSize = when (range.heading) {
                            1 -> headingOne
                            2, 3 -> headingTwo
                            else -> bodySize
                        },
                    ),
                    range.start,
                    range.end,
                )
            }
        }
    }
    Text(
        text = annotated,
        modifier = modifier,
        color = color,
        style = style,
    )
}
