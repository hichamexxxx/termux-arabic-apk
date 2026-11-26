package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AppTab
import com.example.ui.components.ExtraKeysBar
import com.example.ui.components.NanoEditorOverlay
import com.example.ui.components.SessionDrawerContent
import com.example.ui.components.TerminalViewport
import com.example.ui.theme.IbmPlexArabicFontFamily
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalTerminalPalette
import com.example.ui.theme.TermuxArabicTheme
import com.example.viewmodel.TerminalViewModel
import kotlinx.coroutines.launch

@Composable
fun TermuxAppScreen(
    viewModel: TerminalViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.toastMessage) {
        val msg = uiState.toastMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeToast()
        }
    }

    val activeSession = uiState.activeSession

    // Handle Back navigation cleanly when on Reference tab, Nano editor, or Reverse Search
    BackHandler(
        enabled = drawerState.isOpen ||
            uiState.activeTab != AppTab.TERMINAL ||
            activeSession?.nanoState != null ||
            uiState.isReverseSearchActive
    ) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            uiState.activeTab != AppTab.TERMINAL -> viewModel.selectTab(AppTab.TERMINAL)
            activeSession?.nanoState != null -> viewModel.closeNanoEditor()
            uiState.isReverseSearchActive -> viewModel.cancelReverseSearch()
        }
    }

    TermuxArabicTheme(palette = uiState.palette) {
        val palette = LocalTerminalPalette.current

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    SessionDrawerContent(
                        sessions = uiState.sessions,
                        activeSessionId = uiState.activeSessionId,
                        shellScripts = uiState.shellScripts,
                        recentHistory = activeSession?.history.orEmpty().takeLast(8).reversed(),
                        fontSizeSp = uiState.fontSizeSp,
                        selectedPalette = uiState.palette,
                        formatPath = { viewModel.vfs.formatPromptPath(it) },
                        onSelectSession = { id ->
                            viewModel.switchSession(id)
                            scope.launch { drawerState.close() }
                        },
                        onCloseSession = { id -> viewModel.closeSession(id) },
                        onNewSession = {
                            viewModel.createNewSession()
                            scope.launch { drawerState.close() }
                        },
                        onOpenReference = {
                            viewModel.selectTab(AppTab.REFERENCE)
                            scope.launch { drawerState.close() }
                        },
                        onRunQuickCommand = { cmd ->
                            viewModel.selectTab(AppTab.TERMINAL)
                            viewModel.submitCommand(cmd)
                            scope.launch { drawerState.close() }
                        },
                        onClearHistory = {
                            viewModel.clearSavedHistory()
                        },
                        onRunScript = { path ->
                            viewModel.runShellScript(path)
                            scope.launch { drawerState.close() }
                        },
                        onEditScript = { path ->
                            viewModel.openScriptInNano(path)
                            scope.launch { drawerState.close() }
                        },
                        onCreateNewScript = {
                            viewModel.createNewShellScript()
                            scope.launch { drawerState.close() }
                        },
                        onFontSizeChange = { viewModel.setFontSize(it) },
                        onPaletteChange = { viewModel.setPalette(it) },
                        onResetEnvironment = {
                            viewModel.resetEnvironment()
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            ) {
                Scaffold(
                    containerColor = palette.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(palette.surface)
                                .statusBarsPadding()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier.testTag("menu_drawer_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "فتح قائمة الجلسات",
                                        tint = palette.foreground
                                    )
                                }

                                // Tab Switcher: الطرفية | القاموس | فهرس الرموز
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(palette.background)
                                        .padding(3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    TopTabChip(
                                        title = "الطرفية",
                                        icon = Icons.Default.Terminal,
                                        selected = uiState.activeTab == AppTab.TERMINAL,
                                        onClick = { viewModel.selectTab(AppTab.TERMINAL) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("tab_terminal")
                                    )
                                    TopTabChip(
                                        title = "القاموس",
                                        icon = Icons.Default.Book,
                                        selected = uiState.activeTab == AppTab.DICTIONARY,
                                        onClick = {
                                            viewModel.refreshDictionaryState()
                                            viewModel.selectTab(AppTab.DICTIONARY)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("tab_dictionary")
                                    )
                                    TopTabChip(
                                        title = "الفهرس",
                                        icon = Icons.Default.MenuBook,
                                        selected = uiState.activeTab == AppTab.REFERENCE,
                                        onClick = { viewModel.selectTab(AppTab.REFERENCE) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("tab_reference")
                                    )
                                }

                                if (uiState.activeTab == AppTab.TERMINAL) {
                                    IconButton(
                                        onClick = { viewModel.toggleExtraKeys() },
                                        modifier = Modifier.testTag("toggle_extra_keys")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Keyboard,
                                            contentDescription = "إظهار أو إخفاء شريط الاختصارات",
                                            tint = if (uiState.showExtraKeys) palette.accentGreen else palette.mutedForeground
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                            }

                            // Subtle Session Status Strip + Arabic RTL & clear buttons
                            if (uiState.activeTab == AppTab.TERMINAL && activeSession != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(palette.surfaceElevated.copy(alpha = 0.55f))
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = activeSession.title,
                                        fontFamily = IbmPlexArabicFontFamily,
                                        fontSize = 11.sp,
                                        color = palette.mutedForeground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (activeSession.nanoState == null) {
                                            Surface(
                                                onClick = { viewModel.toggleArabicRtlMode() },
                                                color = if (uiState.isArabicRtlMode) {
                                                    palette.accentGreen.copy(alpha = 0.22f)
                                                } else {
                                                    palette.surface
                                                },
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (uiState.isArabicRtlMode) {
                                                        palette.accentGreen
                                                    } else {
                                                        palette.borderSubtle
                                                    }
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.testTag("arabic_rtl_button")
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Translate,
                                                        contentDescription = "تبديل وضع الكتابة العربية من اليمين إلى اليسار RTL",
                                                        tint = if (uiState.isArabicRtlMode) {
                                                            palette.accentGreen
                                                        } else {
                                                            palette.accentCyan
                                                        },
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Text(
                                                        text = "عربي RTL",
                                                        fontFamily = IbmPlexArabicFontFamily,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (uiState.isArabicRtlMode) {
                                                            palette.accentGreen
                                                        } else {
                                                            palette.foreground
                                                        },
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                            Surface(
                                                onClick = { viewModel.clearCurrentLog() },
                                                color = palette.accentRed.copy(alpha = 0.14f),
                                                border = BorderStroke(1.dp, palette.accentRed.copy(alpha = 0.35f)),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.testTag("clear_log_button")
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.DeleteSweep,
                                                        contentDescription = "تنظيف السجل الحالي (clear)",
                                                        tint = palette.accentRed,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Text(
                                                        text = "clear",
                                                        fontFamily = JetBrainsMonoFontFamily,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = palette.accentRed,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Text(
                                                text = viewModel.vfs.formatPromptPath(activeSession.currentDir),
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontSize = 11.sp,
                                                color = palette.accentCyan,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(thickness = 1.dp, color = palette.borderSubtle)
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (uiState.activeTab) {
                            AppTab.TERMINAL -> {
                                if (activeSession?.nanoState != null) {
                                    NanoEditorOverlay(
                                        state = activeSession.nanoState,
                                        fontSizeSp = uiState.fontSizeSp,
                                        onContentChange = viewModel::updateNanoContent,
                                        onSave = viewModel::saveNanoFile,
                                        onSaveAndRun = viewModel::saveAndRunNanoScript,
                                        onCutLine = viewModel::cutLastLineNano,
                                        onToggleSearch = viewModel::toggleNanoSearch,
                                        onSearchQueryChange = viewModel::updateNanoSearchQuery,
                                        onClose = viewModel::closeNanoEditor,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .imePadding()
                                            .navigationBarsPadding()
                                    )
                                } else if (activeSession != null) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .imePadding()
                                            .navigationBarsPadding()
                                    ) {
                                        TerminalViewport(
                                            lines = activeSession.lines,
                                            promptPath = viewModel.vfs.formatPromptPath(activeSession.currentDir),
                                            input = uiState.input,
                                            fontSizeSp = uiState.fontSizeSp,
                                            isRunningProcess = activeSession.isRunningProcess,
                                            runningProcessLabel = activeSession.runningProcessLabel,
                                            ctrlArmed = uiState.ctrlArmed,
                                            isArabicRtlMode = uiState.isArabicRtlMode,
                                            isReverseSearchActive = uiState.isReverseSearchActive,
                                            reverseSearchQuery = uiState.reverseSearchQuery,
                                            reverseSearchMatch = uiState.reverseSearchMatch,
                                            autocompleteSuggestions = uiState.autocompleteSuggestions,
                                            arabicSuggestions = uiState.arabicSuggestions,
                                            scrollTrigger = uiState.scrollTrigger,
                                            onInputChange = viewModel::onInputChange,
                                            onSubmit = { viewModel.submitCommand() },
                                            onTabPressed = viewModel::onTabPressed,
                                            onHistoryUp = viewModel::navigateHistoryUp,
                                            onHistoryDown = viewModel::navigateHistoryDown,
                                            onCtrlChar = { viewModel.handleCtrlCharacter(it) },
                                            onApplySuggestion = viewModel::applyAutocompleteSuggestion,
                                            onApplyArabicSuggestion = viewModel::applyArabicSuggestion,
                                            onQuickArabicCommand = viewModel::applyArabicCommandTemplate,
                                            onReverseSearchQueryChange = viewModel::onReverseSearchQueryChange,
                                            onAcceptReverseSearch = viewModel::acceptReverseSearch,
                                            onCancelReverseSearch = viewModel::cancelReverseSearch,
                                            modifier = Modifier.weight(1f)
                                        )

                                        AnimatedVisibility(visible = uiState.showExtraKeys) {
                                            ExtraKeysBar(
                                                ctrlArmed = uiState.ctrlArmed,
                                                altArmed = uiState.altArmed,
                                                onEsc = viewModel::onEscPressed,
                                                onTab = viewModel::onTabPressed,
                                                onToggleCtrl = viewModel::toggleCtrl,
                                                onToggleAlt = viewModel::toggleAlt,
                                                onHome = viewModel::moveCursorHome,
                                                onEnd = viewModel::moveCursorEnd,
                                                onArrowUp = viewModel::navigateHistoryUp,
                                                onArrowDown = viewModel::navigateHistoryDown,
                                                onArrowLeft = viewModel::moveCursorLeft,
                                                onArrowRight = viewModel::moveCursorRight,
                                                onInsertText = viewModel::insertExtraText,
                                                onEnter = { viewModel.submitCommand() }
                                            )
                                        }
                                    }
                                }
                            }
                            AppTab.DICTIONARY -> {
                                DictionaryScreen(
                                    entries = uiState.dictEntries,
                                    searchQuery = uiState.dictSearchQuery,
                                    onSearchQueryChange = viewModel::updateDictSearch,
                                    onAddWord = viewModel::addCustomDictWord,
                                    onDeleteCustomWord = viewModel::removeCustomDictWord,
                                    onInsertWord = viewModel::insertDictWordToTerminal,
                                    onRunArprint = viewModel::runArprintForWord
                                )
                            }
                            AppTab.REFERENCE -> {
                                ReferenceScreen(
                                    items = uiState.filteredReferenceItems,
                                    selectedCategory = uiState.refCategory,
                                    searchQuery = uiState.refSearchQuery,
                                    onSelectCategory = viewModel::selectReferenceCategory,
                                    onSearchQueryChange = viewModel::updateReferenceSearch,
                                    onTryInTerminal = viewModel::tryReferenceItemInTerminal,
                                    onCopied = { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopTabChip(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalTerminalPalette.current
    Surface(
        color = if (selected) palette.surfaceElevated else palette.background,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) palette.accentGreen else palette.mutedForeground,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontFamily = IbmPlexArabicFontFamily,
                fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) palette.foreground else palette.mutedForeground,
                maxLines = 1
            )
        }
    }
}
