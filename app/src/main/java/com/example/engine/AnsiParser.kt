package com.example.engine

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.example.ui.theme.TerminalPalette

object AnsiParser {
    private val ansiRegex = Regex("\u001B\\[([0-9;]*)m")

    fun parse(text: String, palette: TerminalPalette): AnnotatedString {
        if (!text.contains('\u001B')) {
            return AnnotatedString(text)
        }

        return buildAnnotatedString {
            var currentFg: Color? = null
            var currentBg: Color? = null
            var isBold = false
            var isDim = false
            var isUnderline = false

            var lastIndex = 0
            for (match in ansiRegex.findAll(text)) {
                if (match.range.first > lastIndex) {
                    val segment = text.substring(lastIndex, match.range.first)
                    pushStyle(
                        SpanStyle(
                            color = resolveFg(currentFg, isDim, palette),
                            background = currentBg ?: Color.Unspecified,
                            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                            textDecoration = if (isUnderline) TextDecoration.Underline else TextDecoration.None
                        )
                    )
                    append(segment)
                    pop()
                }

                val codesRaw = match.groupValues[1]
                val codes = if (codesRaw.isEmpty()) listOf(0) else codesRaw.split(";").mapNotNull { it.toIntOrNull() }
                for (code in codes) {
                    when (code) {
                        0 -> {
                            currentFg = null
                            currentBg = null
                            isBold = false
                            isDim = false
                            isUnderline = false
                        }
                        1 -> isBold = true
                        2 -> isDim = true
                        4 -> isUnderline = true
                        22 -> {
                            isBold = false
                            isDim = false
                        }
                        24 -> isUnderline = false
                        30 -> currentFg = palette.surfaceElevated
                        31 -> currentFg = palette.accentRed
                        32 -> currentFg = palette.accentGreen
                        33 -> currentFg = palette.accentYellow
                        34 -> currentFg = palette.accentBlue
                        35 -> currentFg = palette.accentMagenta
                        36 -> currentFg = palette.accentCyan
                        37 -> currentFg = palette.foreground
                        39 -> currentFg = null
                        90 -> currentFg = palette.mutedForeground
                        91 -> currentFg = palette.accentRed
                        92 -> currentFg = palette.accentGreen
                        93 -> currentFg = palette.accentYellow
                        94 -> currentFg = palette.accentBlue
                        95 -> currentFg = palette.accentMagenta
                        96 -> currentFg = palette.accentCyan
                        97 -> currentFg = Color.White
                        40 -> currentBg = palette.surfaceElevated
                        41 -> currentBg = palette.accentRed.copy(alpha = 0.28f)
                        42 -> currentBg = palette.accentGreen.copy(alpha = 0.28f)
                        43 -> currentBg = palette.accentYellow.copy(alpha = 0.28f)
                        44 -> currentBg = palette.accentBlue.copy(alpha = 0.28f)
                        45 -> currentBg = palette.accentMagenta.copy(alpha = 0.28f)
                        46 -> currentBg = palette.accentCyan.copy(alpha = 0.28f)
                        47 -> currentBg = palette.foreground.copy(alpha = 0.2f)
                        49 -> currentBg = null
                    }
                }
                lastIndex = match.range.last + 1
            }

            if (lastIndex < text.length) {
                val tail = text.substring(lastIndex)
                pushStyle(
                    SpanStyle(
                        color = resolveFg(currentFg, isDim, palette),
                        background = currentBg ?: Color.Unspecified,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                        textDecoration = if (isUnderline) TextDecoration.Underline else TextDecoration.None
                    )
                )
                append(tail)
                pop()
            }
        }
    }

    private fun resolveFg(fg: Color?, isDim: Boolean, palette: TerminalPalette): Color {
        val base = fg ?: palette.foreground
        return if (isDim) base.copy(alpha = 0.65f) else base
    }
}
