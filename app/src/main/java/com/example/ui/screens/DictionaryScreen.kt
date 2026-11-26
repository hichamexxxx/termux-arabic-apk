package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ArabicWordEntry
import com.example.ui.theme.IbmPlexArabicFontFamily
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalTerminalPalette

@Composable
fun DictionaryScreen(
    entries: List<ArabicWordEntry>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onAddWord: (String, String) -> Unit,
    onDeleteCustomWord: (String) -> Unit,
    onInsertWord: (String) -> Unit,
    onRunArprint: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalTerminalPalette.current
    var showAddForm by remember { mutableStateOf(false) }
    var newWord by remember { mutableStateOf("") }
    var newMeaning by remember { mutableStateOf("") }

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
            // Header + Search + Add button
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
                    Column {
                        Text(
                            text = "قاموس الكلمات العربية",
                            fontFamily = IbmPlexArabicFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = palette.foreground
                        )
                        Text(
                            text = "إكمال تلقائي أثناء الكتابة + طباعة مباشرة عبر arprint",
                            fontFamily = IbmPlexArabicFontFamily,
                            fontSize = 11.5.sp,
                            color = palette.mutedForeground
                        )
                    }

                    Surface(
                        color = palette.accentGreen.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clickable { showAddForm = !showAddForm }
                            .testTag("dict_add_toggle")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = if (showAddForm) Icons.Default.Close else Icons.Default.Add,
                                contentDescription = "إضافة كلمة",
                                tint = palette.accentGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showAddForm) "إلغاء" else "إضافة كلمة",
                                fontFamily = IbmPlexArabicFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.accentGreen
                            )
                        }
                    }
                }

                // Inline Add Custom Word form
                AnimatedVisibility(visible = showAddForm) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surfaceElevated)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "إضافة كلمة جديدة للقاموس الشخصي",
                            fontFamily = IbmPlexArabicFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            color = palette.accentCyan
                        )
                        DictInputField(
                            value = newWord,
                            onValueChange = { newWord = it },
                            placeholder = "الكلمة العربية (مثال: برمجية)",
                            modifier = Modifier.testTag("dict_new_word_input")
                        )
                        DictInputField(
                            value = newMeaning,
                            onValueChange = { newMeaning = it },
                            placeholder = "الشرح أو المعنى (اختياري)",
                            modifier = Modifier.testTag("dict_new_meaning_input")
                        )
                        Surface(
                            color = palette.accentGreen,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (newWord.isNotBlank()) {
                                        onAddWord(newWord, newMeaning)
                                        newWord = ""
                                        newMeaning = ""
                                        showAddForm = false
                                    }
                                }
                                .testTag("dict_save_word_button")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 9.dp)
                            ) {
                                Text(
                                    text = "حفظ في القاموس",
                                    fontFamily = IbmPlexArabicFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = palette.background
                                )
                            }
                        }
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
                        contentDescription = "بحث في القاموس",
                        tint = palette.mutedForeground,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "ابحث عن كلمة عربية أو معنى أو مصطلح إنجليزي...",
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
                                .testTag("dict_search_input")
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
            }

            if (entries.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "لا توجد كلمات مطابقة في القاموس",
                            fontFamily = IbmPlexArabicFontFamily,
                            fontSize = 14.sp,
                            color = palette.mutedForeground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "يمكنك إضافتها عبر زر «إضافة كلمة» أو الأمر arprint --add",
                            fontFamily = IbmPlexArabicFontFamily,
                            fontSize = 12.sp,
                            color = palette.accentCyan
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                ) {
                    items(entries, key = { it.word }) { entry ->
                        DictionaryCard(
                            entry = entry,
                            onInsert = { onInsertWord(entry.word) },
                            onPrint = { onRunArprint(entry.word) },
                            onDelete = if (entry.isCustom) {
                                { onDeleteCustomWord(entry.word) }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DictInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalTerminalPalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(palette.background)
            .border(1.dp, palette.borderSubtle, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                fontFamily = IbmPlexArabicFontFamily,
                fontSize = 12.5.sp,
                color = palette.mutedForeground
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                fontFamily = IbmPlexArabicFontFamily,
                fontSize = 13.5.sp,
                color = palette.foreground
            ),
            cursorBrush = SolidColor(palette.accentGreen),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DictionaryCard(
    entry: ArabicWordEntry,
    onInsert: () -> Unit,
    onPrint: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val palette = LocalTerminalPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface)
            .border(1.dp, palette.borderSubtle, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = entry.word,
                    fontFamily = IbmPlexArabicFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = palette.accentGreen
                )
                if (entry.englishHint.isNotEmpty()) {
                    Surface(
                        color = palette.background,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = entry.englishHint,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = palette.accentCyan,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = palette.surfaceElevated,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = entry.categoryAr,
                        fontFamily = IbmPlexArabicFontFamily,
                        fontSize = 11.sp,
                        color = palette.accentYellow,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "حذف الكلمة",
                            tint = palette.accentRed,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }

        Text(
            text = entry.meaningAr,
            fontFamily = IbmPlexArabicFontFamily,
            fontSize = 13.sp,
            color = palette.foreground.copy(alpha = 0.9f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = palette.surfaceElevated,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onInsert)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 9.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = "إدراج في الطرفية",
                        tint = palette.foreground,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "إدراج بالطرفية",
                        fontFamily = IbmPlexArabicFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.foreground
                    )
                }
            }

            Surface(
                color = palette.accentCyan.copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1.2f)
                    .clickable(onClick = onPrint)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 9.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = "طباعة عبر arprint",
                        tint = palette.accentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "طباعة بـ arprint",
                        fontFamily = IbmPlexArabicFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentCyan
                    )
                }
            }
        }
    }
}
