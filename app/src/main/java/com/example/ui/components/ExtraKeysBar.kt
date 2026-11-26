package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalTerminalPalette

@Composable
fun ExtraKeysBar(
    ctrlArmed: Boolean,
    altArmed: Boolean,
    onEsc: () -> Unit,
    onTab: () -> Unit,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onHome: () -> Unit,
    onEnd: () -> Unit,
    onArrowUp: () -> Unit,
    onArrowDown: () -> Unit,
    onArrowLeft: () -> Unit,
    onArrowRight: () -> Unit,
    onInsertText: (String) -> Unit,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalTerminalPalette.current

    // Keep extra keys strictly LTR so arrow directions and symbols match standard Termux layout
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(palette.extraKeysBg)
        ) {
            HorizontalDivider(thickness = 1.dp, color = palette.borderSubtle)

            // Quick Shell Symbols Strip
            val quickSymbols = listOf("|", ">", ">>", "<", "&&", "||", ";", "&", "~", "$", "*", "?", "\\", "\"", "'", "{}")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                quickSymbols.forEach { sym ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .heightIn(min = 32.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(palette.surfaceElevated)
                            .clickable {
                                val text = if (sym == "{}") "{}" else sym
                                onInsertText(text)
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = sym,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.accentCyan
                        )
                    }
                }
            }

            // Row 1: ESC, /, -, HOME, ▲, END, PGUP (mapped to Enter/Run for convenience or PgUp? Let's include Enter/Run!)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                ExtraKeyButton("ESC", onClick = onEsc, modifier = Modifier.weight(1f).testTag("key_esc"))
                ExtraKeyButton("/", onClick = { onInsertText("/") }, modifier = Modifier.weight(1f).testTag("key_slash"))
                ExtraKeyButton("-", onClick = { onInsertText("-") }, modifier = Modifier.weight(1f).testTag("key_dash"))
                ExtraKeyButton("HOME", onClick = onHome, modifier = Modifier.weight(1f).testTag("key_home"))
                ExtraKeyButton("▲", onClick = onArrowUp, modifier = Modifier.weight(1f).testTag("key_up"))
                ExtraKeyButton("END", onClick = onEnd, modifier = Modifier.weight(1f).testTag("key_end"))
                ExtraKeyButton("↵", onClick = onEnter, textColor = palette.accentGreen, modifier = Modifier.weight(1f).testTag("key_enter"))
            }

            // Row 2: TAB, CTRL, ALT, ◄, ▼, ►, |
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                ExtraKeyButton("TAB", onClick = onTab, modifier = Modifier.weight(1f).testTag("key_tab"))
                ExtraKeyButton(
                    label = "CTRL",
                    isActive = ctrlArmed,
                    onClick = onToggleCtrl,
                    modifier = Modifier.weight(1f).testTag("key_ctrl")
                )
                ExtraKeyButton(
                    label = "ALT",
                    isActive = altArmed,
                    onClick = onToggleAlt,
                    modifier = Modifier.weight(1f).testTag("key_alt")
                )
                ExtraKeyButton("◄", onClick = onArrowLeft, modifier = Modifier.weight(1f).testTag("key_left"))
                ExtraKeyButton("▼", onClick = onArrowDown, modifier = Modifier.weight(1f).testTag("key_down"))
                ExtraKeyButton("►", onClick = onArrowRight, modifier = Modifier.weight(1f).testTag("key_right"))
                ExtraKeyButton("|", onClick = { onInsertText("|") }, modifier = Modifier.weight(1f).testTag("key_pipe"))
            }
        }
    }
}

@Composable
private fun ExtraKeyButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    textColor: Color? = null
) {
    val palette = LocalTerminalPalette.current
    val bg = if (isActive) palette.extraKeysActive else palette.extraKeysButton
    val fg = when {
        isActive -> Color.White
        textColor != null -> textColor
        else -> palette.foreground
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 11.5.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = fg,
            maxLines = 1
        )
    }
}
