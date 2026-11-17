package com.kashvault.ui.markdown

data class MarkdownStyleRange(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val heading: Int = 0,
)

data class StyledMarkdown(
    val text: String,
    val styles: List<MarkdownStyleRange> = emptyList(),
)

fun parseStyledMarkdown(source: String): StyledMarkdown {
    val text = StringBuilder()
    val styles = mutableListOf<MarkdownStyleRange>()
    val normalized = source.replace("\r\n", "\n").trim()
    if (normalized.isEmpty()) return StyledMarkdown("")

    val lines = normalized.split('\n')
    var i = 0
    var firstBlock = true
    while (i < lines.size) {
        val line = lines[i]
        val fence = line.trimStart().startsWith("```")
        if (fence) {
            i++
            val code = StringBuilder()
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                if (code.isNotEmpty()) code.append('\n')
                code.append(lines[i])
                i++
            }
            if (i < lines.size) i++
            if (!firstBlock) text.append("\n\n")
            val start = text.length
            text.append(code)
            styles += MarkdownStyleRange(start, text.length, code = true)
            firstBlock = false
            continue
        }

        if (line.isBlank()) {
            if (!firstBlock) text.append('\n')
            i++
            continue
        }

        if (line.trim().matches(Regex("^-{3,}$|^\\*{3,}$|^_{3,}$"))) {
            if (!firstBlock) text.append('\n')
            text.append("────────")
            firstBlock = false
            i++
            continue
        }

        if (!firstBlock) text.append('\n')
        firstBlock = false
        appendMarkdownLine(line, text, styles)
        i++
    }

    return StyledMarkdown(text.toString(), styles)
}

private fun appendMarkdownLine(
    raw: String,
    text: StringBuilder,
    styles: MutableList<MarkdownStyleRange>,
) {
    val trimmed = raw.trimEnd()
    val heading = headingLevel(trimmed)
    val body = when {
        heading > 0 -> trimmed.dropWhile { it == '#' }.trimStart()
        trimmed.startsWith("> ") -> trimmed.drop(2)
        trimmed.startsWith(">") -> trimmed.drop(1).trimStart()
        bulletPrefix(trimmed) != null -> "• " + bulletPrefix(trimmed)
        else -> trimmed
    }
    val start = text.length
    parseInline(body, text, styles)
    if (heading > 0 && text.length > start) {
        styles += MarkdownStyleRange(start, text.length, bold = true, heading = heading)
    }
}

private fun headingLevel(line: String): Int {
    var n = 0
    while (n < line.length && n < 3 && line[n] == '#') n++
    return if (n > 0 && n < line.length && line[n] == ' ') n else 0
}

private fun bulletPrefix(line: String): String? {
    Regex("^\\d+[.)]\\s+(.*)$").matchEntire(line)?.let { return it.groupValues[1] }
    Regex("^[-*+]\\s+(.*)$").matchEntire(line)?.let { return it.groupValues[1] }
    return null
}

private fun parseInline(
    input: String,
    out: StringBuilder,
    styles: MutableList<MarkdownStyleRange>,
) {
    var i = 0
    while (i < input.length) {
        val rest = input.substring(i)
        when {
            rest.startsWith("***") -> {
                val end = input.indexOf("***", i + 3)
                if (end >= 0) {
                    val start = out.length
                    out.append(input.substring(i + 3, end))
                    styles += MarkdownStyleRange(start, out.length, bold = true, italic = true)
                    i = end + 3
                } else {
                    out.append(input[i])
                    i++
                }
            }
            rest.startsWith("**") || rest.startsWith("__") -> {
                val token = if (rest.startsWith("**")) "**" else "__"
                val end = input.indexOf(token, i + token.length)
                if (end >= 0) {
                    val start = out.length
                    parseInline(input.substring(i + token.length, end), out, styles)
                    styles += MarkdownStyleRange(start, out.length, bold = true)
                    i = end + token.length
                } else {
                    out.append(input[i])
                    i++
                }
            }
            rest.startsWith("*") || (rest.startsWith("_") && !rest.startsWith("__")) -> {
                val token = rest.take(1)
                val end = input.indexOf(token, i + 1)
                if (end > i + 1) {
                    val start = out.length
                    out.append(input.substring(i + 1, end))
                    styles += MarkdownStyleRange(start, out.length, italic = true)
                    i = end + 1
                } else {
                    out.append(input[i])
                    i++
                }
            }
            rest.startsWith("`") -> {
                val end = input.indexOf('`', i + 1)
                if (end >= 0) {
                    val start = out.length
                    out.append(input.substring(i + 1, end))
                    styles += MarkdownStyleRange(start, out.length, code = true)
                    i = end + 1
                } else {
                    out.append(input[i])
                    i++
                }
            }
            rest.startsWith("[") -> {
                val close = input.indexOf(']', i + 1)
                val openParen = if (close >= 0 && close + 1 < input.length && input[close + 1] == '(') {
                    input.indexOf(')', close + 2)
                } else {
                    -1
                }
                if (close > i && openParen > close) {
                    out.append(input.substring(i + 1, close))
                    i = openParen + 1
                } else {
                    out.append(input[i])
                    i++
                }
            }
            else -> {
                out.append(input[i])
                i++
            }
        }
    }
}
