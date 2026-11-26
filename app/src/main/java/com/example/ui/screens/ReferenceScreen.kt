package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ReferenceCategory
import com.example.model.ReferenceItem
import com.example.model.UsageContextBadge
import com.example.ui.theme.IbmPlexArabicFontFamily
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalTerminalPalette

@Composable
fun ReferenceScreen(
    items: List<ReferenceItem>,
    selectedCategory: ReferenceCategory,
    searchQuery: String,
    onSelectCategory: (ReferenceCategory) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onTryInTerminal: (ReferenceItem) -> Unit,
    onCopied: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalTerminalPalette.current
    val clipboard = LocalClipboardManager.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
        ) {
            // Search + Categories Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surface)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "فهرس الأحرف والرموز والمدخلات",
                        fontFamily = IbmPlexArabicFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = palette.foreground
                    )
                    Surface(
                        color = palette.accentGreen.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${items.size} عنصر",
                            fontFamily = IbmPlexArabicFontFamily,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.accentGreen,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Search bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.background)
                        .border(1.dp, palette.borderSubtle, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "بحث",
                        tint = palette.mutedForeground,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "ابحث عن رمز أو اختصار (مثل Ctrl+C، >>، |، grep...)",
                                fontFamily = IbmPlexArabicFontFamily,
                                fontSize = 12.5.sp,
                                color = palette.mutedForeground
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            textStyle = TextStyle(
                                fontFamily = IbmPlexArabicFontFamily,
                                fontSize = 13.5.sp,
                                color = palette.foreground
                            ),
                            cursorBrush = SolidColor(palette.accentGreen),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reference_search_input")
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "مسح البحث",
                                tint = palette.mutedForeground,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Category chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReferenceCategory.entries.forEach { category ->
                        val selected = category == selectedCategory
                        Surface(
                            color = if (selected) palette.accentGreen else palette.surfaceElevated,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .clickable { onSelectCategory(category) }
                                .testTag("cat_${category.name.lowercase()}")
                        ) {
                            Text(
                                text = category.arabicLabel,
                                fontFamily = IbmPlexArabicFontFamily,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) palette.background else palette.foreground,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            if (items.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = palette.mutedForeground,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "لم يتم العثور على رموز مطابقة لـ \"$searchQuery\"",
                            fontFamily = IbmPlexArabicFontFamily,
                            fontSize = 14.sp,
                            color = palette.mutedForeground
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                ) {
                    items(items, key = { it.id }) { item ->
                        ReferenceCard(
                            item = item,
                            onCopy = {
                                clipboard.setText(AnnotatedString(item.insertText))
                                onCopied("تم نسخ: ${item.insertText}")
                            },
                            onTry = { onTryInTerminal(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferenceCard(
    item: ReferenceItem,
    onCopy: () -> Unit,
    onTry: () -> Unit
) {
    val palette = LocalTerminalPalette.current
    val badgeColor = when (item.contextBadge) {
        UsageContextBadge.SHELL_SYSTEM -> palette.accentGreen
        UsageContextBadge.INSIDE_TOOLS -> palette.accentMagenta
        UsageContextBadge.BOTH -> palette.accentYellow
        UsageContextBadge.KEYBOARD_CONTROL -> palette.accentCyan
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface)
            .border(1.dp, palette.borderSubtle, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Symbol Badge + Context Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Surface(
                    color = palette.background,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.border(
                        1.dp,
                        palette.accentGreen.copy(alpha = 0.45f),
                        RoundedCornerShape(8.dp)
                    )
                ) {
                    Text(
                        text = item.symbol,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = palette.accentGreen,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Surface(
                color = badgeColor.copy(alpha = 0.14f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = item.contextBadge.arabicLabel,
                    fontFamily = IbmPlexArabicFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Arabic Title & Description
        Text(
            text = item.titleAr,
            fontFamily = IbmPlexArabicFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = palette.foreground
        )

        Text(
            text = item.descriptionAr,
            fontFamily = IbmPlexArabicFontFamily,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = palette.foreground.copy(alpha = 0.88f)
        )

        // Shell vs Tools Clarification Callout
        if (!item.shellVsToolNoteAr.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.accentYellow.copy(alpha = 0.1f))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = palette.accentYellow,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = item.shellVsToolNoteAr,
                    fontFamily = IbmPlexArabicFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = palette.accentYellow
                )
            }
        }

        // Code Example Box (LTR)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(palette.background)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = "$ ${item.exampleCommand}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    color = palette.accentCyan
                )
            }
            Text(
                text = "النتيجة: ${item.exampleResultAr}",
                fontFamily = IbmPlexArabicFontFamily,
                fontSize = 11.5.sp,
                color = palette.mutedForeground
            )
        }

        // Action Buttons: Copy + Try in Terminal
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = palette.surfaceElevated,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onCopy)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "نسخ",
                        tint = palette.foreground,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "نسخ",
                        fontFamily = IbmPlexArabicFontFamily,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.foreground
                    )
                }
            }

            Surface(
                color = palette.accentGreen.copy(alpha = 0.18f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1.4f)
                    .clickable(onClick = onTry)
                    .testTag("try_${item.id}")
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "جرّب في الطرفية",
                        tint = palette.accentGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (item.runDirectlyInTerminal) "تنفيذ في الطرفية" else "إدراج في الطرفية",
                        fontFamily = IbmPlexArabicFontFamily,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentGreen
                    )
                }
            }
        }
    }
}
