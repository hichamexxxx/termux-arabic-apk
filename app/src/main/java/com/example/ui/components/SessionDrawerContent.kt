package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TerminalSession
import com.example.ui.theme.AllTerminalPalettes
import com.example.ui.theme.IbmPlexArabicFontFamily
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalTerminalPalette
import com.example.ui.theme.TerminalPalette

@Composable
fun SessionDrawerContent(
    sessions: List<TerminalSession>,
    activeSessionId: Int,
    shellScripts: List<String>,
    recentHistory: List<String>,
    fontSizeSp: Float,
    selectedPalette: TerminalPalette,
    formatPath: (String) -> String,
    onSelectSession: (Int) -> Unit,
    onCloseSession: (Int) -> Unit,
    onNewSession: () -> Unit,
    onOpenReference: () -> Unit,
    onRunQuickCommand: (String) -> Unit,
    onClearHistory: () -> Unit,
    onRunScript: (String) -> Unit,
    onEditScript: (String) -> Unit,
    onCreateNewScript: () -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onPaletteChange: (TerminalPalette) -> Unit,
    onResetEnvironment: () -> Unit
) {
    val palette = LocalTerminalPalette.current
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            containerColor = palette.surfaceElevated,
            titleContentColor = palette.foreground,
            textContentColor = palette.mutedForeground,
            title = {
                Text(
                    text = "إعادة ضبط البيانات الافتراضية",
                    fontFamily = IbmPlexArabicFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "سيتم إرجاع نظام الملفات الافتراضي، الحزم المثبتة، كلمات القاموس المخصصة، إعدادات المظهر، وسجل الأوامر في Preferences إلى حالتها الأولية. هل تريد المتابعة؟",
                    fontFamily = IbmPlexArabicFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirmDialog = false
                        onResetEnvironment()
                    },
                    modifier = Modifier.testTag("confirm_reset_button")
                ) {
                    Text(
                        text = "إعادة الضبط",
                        fontFamily = IbmPlexArabicFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentRed
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text(
                        text = "إلغاء",
                        fontFamily = IbmPlexArabicFontFamily,
                        color = palette.foreground
                    )
                }
            }
        )
    }

    ModalDrawerSheet(
        drawerContainerColor = palette.surface,
        drawerContentColor = palette.foreground,
        modifier = Modifier.width(300.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Drawer Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.background)
                        .border(1.dp, palette.accentGreen.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                ) {
                    Text(
                        text = ">_",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = palette.accentGreen
                    )
                }
                Column {
                    Text(
                        text = "Termux عربي",
                        fontFamily = IbmPlexArabicFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = palette.foreground
                    )
                    Text(
                        text = "بيئة Linux / bash التفاعلية",
                        fontFamily = IbmPlexArabicFontFamily,
                        fontSize = 11.5.sp,
                        color = palette.mutedForeground
                    )
                }
            }

            HorizontalDivider(color = palette.borderSubtle)

            // Sessions section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الجلسات المفتوحة (${sessions.size})",
                    fontFamily = IbmPlexArabicFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = palette.mutedForeground
                )
                Surface(
                    color = palette.accentGreen.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clickable { onNewSession() }
                        .testTag("drawer_new_session")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "جلسة جديدة",
                            tint = palette.accentGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "جلسة جديدة",
                            fontFamily = IbmPlexArabicFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.accentGreen
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sessions.forEach { session ->
                    val isSelected = session.id == activeSessionId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) palette.surfaceElevated else palette.background.copy(alpha = 0.5f))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) palette.accentGreen.copy(alpha = 0.6f) else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectSession(session.id) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = if (isSelected) palette.accentGreen else palette.mutedForeground,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = session.title,
                                fontFamily = IbmPlexArabicFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.foreground
                            )
                            Text(
                                text = formatPath(session.currentDir),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = palette.accentCyan
                            )
                        }
                        IconButton(
                            onClick = { onCloseSession(session.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إغلاق الجلسة",
                                tint = palette.mutedForeground,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = palette.borderSubtle)

            // Reference Guide Shortcut
            Surface(
                color = palette.accentCyan.copy(alpha = 0.14f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenReference() }
                    .testTag("drawer_open_reference")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = "فهرس الرموز والمدخلات",
                        tint = palette.accentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "فهرس الرموز والمدخلات",
                            fontFamily = IbmPlexArabicFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            color = palette.accentCyan
                        )
                        Text(
                            text = "دليل الاختصارات، Shell، والترقيم",
                            fontFamily = IbmPlexArabicFontFamily,
                            fontSize = 11.sp,
                            color = palette.mutedForeground
                        )
                    }
                }
            }

            // Quick commands
            Text(
                text = "أوامر سريعة",
                fontFamily = IbmPlexArabicFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = palette.mutedForeground
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("help", "keys", "arprint --help", "ls -la").forEach { cmd ->
                    Surface(
                        color = palette.surfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { onRunQuickCommand(cmd) }
                    ) {
                        Text(
                            text = cmd,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.5.sp,
                            color = palette.foreground,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            if (recentHistory.isNotEmpty()) {
                HorizontalDivider(color = palette.borderSubtle)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = palette.accentCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "سجل الأوامر المحفوظ (${recentHistory.size})",
                            fontFamily = IbmPlexArabicFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            color = palette.mutedForeground
                        )
                    }
                    Surface(
                        color = palette.accentRed.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .clickable { onClearHistory() }
                            .testTag("drawer_clear_history")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "مسح سجل الأوامر",
                                tint = palette.accentRed,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "مسح",
                                fontFamily = IbmPlexArabicFontFamily,
                                fontSize = 11.sp,
                                color = palette.accentRed
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    recentHistory.forEach { cmd ->
                        Surface(
                            color = palette.background.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(7.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRunQuickCommand(cmd) }
                        ) {
                            Text(
                                text = "$ $cmd",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = palette.foreground,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = palette.borderSubtle)

            // Practical .sh Scripts Runner & Editor Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "السكربتات (.sh)",
                    fontFamily = IbmPlexArabicFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = palette.mutedForeground
                )
                Surface(
                    color = palette.accentYellow.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clickable { onCreateNewScript() }
                        .testTag("drawer_new_script")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "سكربت جديد",
                            tint = palette.accentYellow,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "سكربت جديد",
                            fontFamily = IbmPlexArabicFontFamily,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.accentYellow
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                shellScripts.forEach { scriptPath ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.background.copy(alpha = 0.55f))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = scriptPath,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.5.sp,
                            color = palette.accentCyan,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onEditScript(scriptPath) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "تعديل السكربت",
                                tint = palette.mutedForeground,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = { onRunScript(scriptPath) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "تشغيل السكربت",
                                tint = palette.accentGreen,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = palette.borderSubtle)

            // Appearance: Font Size
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "حجم خط الطرفية",
                        fontFamily = IbmPlexArabicFontFamily,
                        fontSize = 13.sp,
                        color = palette.foreground
                    )
                    Text(
                        text = "${fontSizeSp.toInt()} sp",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        color = palette.accentGreen
                    )
                }
                Slider(
                    value = fontSizeSp,
                    onValueChange = onFontSizeChange,
                    valueRange = 10f..20f,
                    colors = SliderDefaults.colors(
                        thumbColor = palette.accentGreen,
                        activeTrackColor = palette.accentGreen
                    )
                )
            }

            // Color Schemes
            Text(
                text = "نظام الألوان (Termux:Styling)",
                fontFamily = IbmPlexArabicFontFamily,
                fontSize = 13.sp,
                color = palette.foreground
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AllTerminalPalettes.forEach { p ->
                    val active = p.id == selectedPalette.id
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) palette.surfaceElevated else Color.Transparent)
                            .clickable { onPaletteChange(p) }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(p.background)
                                .border(2.dp, p.accentGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = p.arabicName,
                            fontFamily = IbmPlexArabicFontFamily,
                            fontSize = 12.5.sp,
                            color = if (active) palette.accentGreen else palette.foreground
                        )
                    }
                }
            }

            HorizontalDivider(color = palette.borderSubtle)

            // Full Default Data Reset button
            Surface(
                onClick = { showResetConfirmDialog = true },
                color = palette.accentRed.copy(alpha = 0.14f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drawer_reset_environment")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "إعادة ضبط البيانات الافتراضية",
                        tint = palette.accentRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "إعادة ضبط البيانات الافتراضية",
                        fontFamily = IbmPlexArabicFontFamily,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.accentRed
                    )
                }
            }
        }
    }
}
