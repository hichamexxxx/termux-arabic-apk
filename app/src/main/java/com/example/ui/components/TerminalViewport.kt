package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ArabicWordEntry
import com.example.engine.AnsiParser
import com.example.model.TerminalLine
import com.example.ui.theme.IbmPlexArabicFontFamily
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalTerminalPalette

@Composable
fun TerminalViewport(
    lines: List<TerminalLine>,
    promptPath: String,
    input: TextFieldValue,
    fontSizeSp: Float,
    isRunningProcess: Boolean,
    runningProcessLabel: String?,
    ctrlArmed: Boolean,
    isArabicRtlMode: Boolean,
    isReverseSearchActive: Boolean,
    reverseSearchQuery: String,
    reverseSearchMatch: String?,
    autocompleteSuggestions: List<String>,
    arabicSuggestions: List<ArabicWordEntry>,
    scrollTrigger: Int,
    onInputChange: (TextFieldValue) -> Unit,
    onSubmit: () -> Unit,
    onTabPressed: () -> Unit,
    onHistoryUp: () -> Unit,
    onHistoryDown: () -> Unit,
    onCtrlChar: (Char) -> Unit,
    onApplySuggestion: (String) -> Unit,
    onApplyArabicSuggestion: (ArabicWordEntry) -> Unit,
    onQuickArabicCommand: (String, Boolean) -> Unit,
    onReverseSearchQueryChange: (String) -> Unit,
    onAcceptReverseSearch: () -> Unit,
    onCancelReverseSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalTerminalPalette.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val infiniteTransition = rememberInfiniteTransition(label = "cursor_blink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    LaunchedEffect(lines.size, scrollTrigger) {
        val targetIndex = lines.size.coerceAtLeast(0)
        runCatching {
            listState.animateScrollToItem(targetIndex)
        }
    }

    LaunchedEffect(isArabicRtlMode) {
        if (isArabicRtlMode) {
            runCatching {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    }

    val monoStyle = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * 1.42f).sp,
        color = palette.foreground,
        textDirection = if (isArabicRtlMode) TextDirection.ContentOrRtl else TextDirection.Ltr
    )

    val activeDirection = if (isArabicRtlMode) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides activeDirection) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(palette.background)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
        ) {
            SelectionContainer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    items(lines, key = { it.id }) { line ->
                        if (line.isCommandEcho) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "${line.promptPrefix} ",
                                    style = monoStyle.copy(
                                        color = palette.accentGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = line.rawText,
                                    style = monoStyle.copy(color = palette.foreground)
                                )
                            }
                        } else {
                            val annotated = remember(line.rawText, palette) {
                                AnsiParser.parse(line.rawText, palette)
                            }
                            Text(
                                text = annotated,
                                style = monoStyle,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Active command prompt row
                    item(key = "active_prompt") {
                        Spacer(modifier = Modifier.height(2.dp))
                        if (isRunningProcess) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "⟳ جاري تنفيذ: ${runningProcessLabel ?: "..."}  (اضغط Ctrl+C للإيقاف)",
                                    style = monoStyle.copy(
                                        fontFamily = IbmPlexArabicFontFamily,
                                        color = palette.accentYellow
                                    )
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "$promptPath $ ",
                                    style = monoStyle.copy(
                                        color = palette.accentGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    BasicTextField(
                                        value = input,
                                        onValueChange = { newVal ->
                                            if (newVal.text.contains('\n')) {
                                                onInputChange(newVal.copy(text = newVal.text.replace("\n", "")))
                                                onSubmit()
                                            } else {
                                                onInputChange(newVal)
                                            }
                                        },
                                        textStyle = monoStyle,
                                        cursorBrush = SolidColor(palette.cursor),
                                        singleLine = false,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Text,
                                            imeAction = ImeAction.Send,
                                            autoCorrectEnabled = true
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onSend = { onSubmit() },
                                            onDone = { onSubmit() }
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(focusRequester)
                                            .testTag("terminal_input")
                                            .onPreviewKeyEvent { keyEvent ->
                                                if (keyEvent.type == KeyEventType.KeyDown) {
                                                    when {
                                                        keyEvent.isCtrlPressed && keyEvent.key == Key.C -> {
                                                            onCtrlChar('c'); true
                                                        }
                                                        keyEvent.isCtrlPressed && keyEvent.key == Key.D -> {
                                                            onCtrlChar('d'); true
                                                        }
                                                        keyEvent.isCtrlPressed && keyEvent.key == Key.L -> {
                                                            onCtrlChar('l'); true
                                                        }
                                                        keyEvent.isCtrlPressed && keyEvent.key == Key.Z -> {
                                                            onCtrlChar('z'); true
                                                        }
                                                        keyEvent.isCtrlPressed && keyEvent.key == Key.A -> {
                                                            onCtrlChar('a'); true
                                                        }
                                                        keyEvent.isCtrlPressed && keyEvent.key == Key.E -> {
                                                            onCtrlChar('e'); true
                                                        }
                                                        keyEvent.isCtrlPressed && keyEvent.key == Key.U -> {
                                                            onCtrlChar('u'); true
                                                        }
                                                        keyEvent.isCtrlPressed && keyEvent.key == Key.K -> {
                                                            onCtrlChar('k'); true
                                                        }
                                                        keyEvent.isCtrlPressed && keyEvent.key == Key.W -> {
                                                            onCtrlChar('w'); true
                                                        }
                                                        keyEvent.isCtrlPressed && keyEvent.key == Key.R -> {
                                                            onCtrlChar('r'); true
                                                        }
                                                        keyEvent.key == Key.Tab -> {
                                                            onTabPressed(); true
                                                        }
                                                        keyEvent.key == Key.DirectionUp -> {
                                                            onHistoryUp(); true
                                                        }
                                                        keyEvent.key == Key.DirectionDown -> {
                                                            onHistoryDown(); true
                                                        }
                                                        keyEvent.key == Key.Enter -> {
                                                            onSubmit(); true
                                                        }
                                                        else -> false
                                                    }
                                                } else false
                                            }
                                    )
                                    // Blinking block cursor shown when input is empty for authentic Termux look
                                    if (input.text.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .alpha(cursorAlpha)
                                                .width(8.dp)
                                                .height((fontSizeSp * 1.25f).dp)
                                                .background(palette.cursor)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // Live Arabic Word Dictionary suggestions while typing Arabic
            AnimatedVisibility(visible = arabicSuggestions.isNotEmpty()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.surfaceElevated)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "القاموس:",
                            fontFamily = IbmPlexArabicFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentGreen
                        )
                        arabicSuggestions.forEach { entry ->
                            Surface(
                                color = palette.extraKeysButton,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { onApplyArabicSuggestion(entry) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = entry.word,
                                        fontFamily = IbmPlexArabicFontFamily,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.accentCyan
                                    )
                                    if (entry.englishHint.isNotEmpty()) {
                                        Text(
                                            text = entry.englishHint,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 10.sp,
                                            color = palette.mutedForeground
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Autocomplete suggestions banner
            AnimatedVisibility(visible = autocompleteSuggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.surfaceElevated)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    autocompleteSuggestions.forEach { suggestion ->
                        Surface(
                            color = palette.extraKeysButton,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable { onApplySuggestion(suggestion) }
                        ) {
                            Text(
                                text = suggestion,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                color = palette.accentCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // Arabic RTL Command Helper Strip
            AnimatedVisibility(visible = isArabicRtlMode) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val arabicCommands = listOf(
                        Triple("طباعة عربية", "arprint -b \"\"", false),
                        Triple("عرض الملفات", "عرض", true),
                        Triple("المسار", "مسار", true),
                        Triple("قاموس", "قاموس ", false),
                        Triple("مجلد جديد", "مجلد ", false),
                        Triple("ملف جديد", "إنشاء ", false),
                        Triple("قراءة", "قراءة ", false),
                        Triple("محرر nano", "تحرير ", false),
                        Triple("تشغيل .sh", "تشغيل ", false),
                        Triple("حزم pkg", "حزم search ", false),
                        Triple("تنظيف", "تنظيف", true),
                        Triple("مساعدة", "مساعدة", true)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.accentGreen.copy(alpha = 0.12f))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "أوامر عربية (RTL):",
                            fontFamily = IbmPlexArabicFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentGreen
                        )
                        arabicCommands.forEach { (label, template, runNow) ->
                            Surface(
                                onClick = {
                                    onQuickArabicCommand(template, runNow)
                                    if (!runNow) {
                                        focusRequester.requestFocus()
                                        keyboardController?.show()
                                    }
                                },
                                color = palette.surfaceElevated,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontFamily = IbmPlexArabicFontFamily,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = palette.accentCyan,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Ctrl Armed Quick Action Hint Bar
            AnimatedVisibility(visible = ctrlArmed) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.extraKeysActive.copy(alpha = 0.22f))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CTRL مفعّل:",
                            fontFamily = IbmPlexArabicFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentGreen
                        )
                        val ctrlShortcuts = listOf(
                            'c' to "^C إيقاف",
                            'l' to "^L تنظيف",
                            'd' to "^D إنهاء",
                            'z' to "^Z تعليق",
                            'a' to "^A بداية",
                            'e' to "^E نهاية",
                            'u' to "^U مسح قبل",
                            'k' to "^K مسح بعد",
                            'w' to "^W حذف كلمة",
                            'r' to "^R بحث"
                        )
                        ctrlShortcuts.forEach { (ch, label) ->
                            Surface(
                                color = palette.surfaceElevated,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.clickable { onCtrlChar(ch) }
                            ) {
                                Text(
                                    text = label,
                                    fontFamily = IbmPlexArabicFontFamily,
                                    fontSize = 11.sp,
                                    color = palette.foreground,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Reverse Search Bar (Ctrl + R)
            AnimatedVisibility(visible = isReverseSearchActive) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.surfaceElevated)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "بحث في التاريخ (Ctrl+R):",
                                fontFamily = IbmPlexArabicFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.accentYellow
                            )
                            BasicTextField(
                                value = reverseSearchQuery,
                                onValueChange = onReverseSearchQueryChange,
                                textStyle = TextStyle(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 13.sp,
                                    color = palette.foreground
                                ),
                                cursorBrush = SolidColor(palette.accentYellow),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(palette.background, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            Surface(
                                color = palette.accentGreen.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.clickable { onAcceptReverseSearch() }
                            ) {
                                Text(
                                    text = "تحديد",
                                    fontFamily = IbmPlexArabicFontFamily,
                                    fontSize = 11.sp,
                                    color = palette.accentGreen,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                color = palette.accentRed.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.clickable { onCancelReverseSearch() }
                            ) {
                                Text(
                                    text = "إلغاء",
                                    fontFamily = IbmPlexArabicFontFamily,
                                    fontSize = 11.sp,
                                    color = palette.accentRed,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = reverseSearchMatch ?: "(لا توجد نتيجة مطابقة)",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            color = if (reverseSearchMatch != null) palette.accentCyan else palette.mutedForeground
                        )
                    }
                }
            }
        }
    }
}
