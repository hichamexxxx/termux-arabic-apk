package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.NanoEditorState
import com.example.ui.theme.IbmPlexArabicFontFamily
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalTerminalPalette

@Composable
fun NanoEditorOverlay(
    state: NanoEditorState,
    fontSizeSp: Float,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onSaveAndRun: (() -> Unit)? = null,
    onCutLine: () -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalTerminalPalette.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        // Authentic nano inverted top bar
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.foreground)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GNU nano 8.2",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = "File: ${state.displayFileName}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
                Text(
                    text = if (state.isModified) "[Modified]" else "[Saved]",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = if (state.isModified) Color(0xFFB31D28) else Color(0xFF1B7F37)
                )
            }
        }

        // Optional search bar
        AnimatedVisibility(visible = state.showSearchBar) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surfaceElevated)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "بحث في الملف:",
                    fontFamily = IbmPlexArabicFontFamily,
                    fontSize = 12.sp,
                    color = palette.accentYellow
                )
                BasicTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChange,
                    textStyle = TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 13.sp,
                        color = palette.foreground
                    ),
                    cursorBrush = SolidColor(palette.accentYellow),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .background(palette.background, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Editable monospace text area
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                BasicTextField(
                    value = state.content,
                    onValueChange = onContentChange,
                    textStyle = TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp * 1.45f).sp,
                        color = palette.foreground
                    ),
                    cursorBrush = SolidColor(palette.cursor),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("nano_editor_input")
                )
            }
        }

        // Arabic status line
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surfaceElevated)
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                text = state.statusMessage,
                fontFamily = IbmPlexArabicFontFamily,
                fontSize = 12.sp,
                color = palette.accentCyan
            )
        }

        // Bottom nano action shortcuts
        val isShScript = state.displayFileName.endsWith(".sh") || state.filePath.endsWith(".sh")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.extraKeysBg)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NanoShortcutButton("^O حفظ", palette.accentGreen, onSave, Modifier.weight(1f).testTag("nano_save"))
            if (isShScript && onSaveAndRun != null) {
                NanoShortcutButton("▶ تشغيل", palette.accentYellow, onSaveAndRun, Modifier.weight(1.1f).testTag("nano_run_sh"))
            } else {
                NanoShortcutButton("^W بحث", palette.accentYellow, onToggleSearch, Modifier.weight(1f).testTag("nano_search"))
            }
            NanoShortcutButton("^K قص", palette.accentCyan, onCutLine, Modifier.weight(1f).testTag("nano_cut"))
            NanoShortcutButton("^X خروج", palette.accentRed, onClose, Modifier.weight(1f).testTag("nano_exit"))
        }
    }
}

@Composable
private fun NanoShortcutButton(
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalTerminalPalette.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(palette.extraKeysButton)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontFamily = IbmPlexArabicFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            maxLines = 1
        )
    }
}
