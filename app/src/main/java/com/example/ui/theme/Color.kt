package com.example.ui.theme

import androidx.compose.ui.graphics.Color

data class TerminalPalette(
    val id: String,
    val arabicName: String,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val extraKeysBg: Color,
    val extraKeysButton: Color,
    val extraKeysActive: Color,
    val foreground: Color,
    val mutedForeground: Color,
    val cursor: Color,
    val accentGreen: Color,
    val accentCyan: Color,
    val accentYellow: Color,
    val accentRed: Color,
    val accentBlue: Color,
    val accentMagenta: Color,
    val borderSubtle: Color
)

val TermuxClassicPalette = TerminalPalette(
    id = "classic",
    arabicName = "Termux الكلاسيكي",
    background = Color(0xFF000000),
    surface = Color(0xFF0D1117),
    surfaceElevated = Color(0xFF161B22),
    extraKeysBg = Color(0xFF11161D),
    extraKeysButton = Color(0xFF1D242E),
    extraKeysActive = Color(0xFF238636),
    foreground = Color(0xFFE6EDF3),
    mutedForeground = Color(0xFF8B949E),
    cursor = Color(0xFF3FB950),
    accentGreen = Color(0xFF3FB950),
    accentCyan = Color(0xFF39C5CF),
    accentYellow = Color(0xFFE3B341),
    accentRed = Color(0xFFFF5555),
    accentBlue = Color(0xFF58A6FF),
    accentMagenta = Color(0xFFBC8CFF),
    borderSubtle = Color(0xFF262D37)
)

val DraculaPalette = TerminalPalette(
    id = "dracula",
    arabicName = "Dracula الداكن",
    background = Color(0xFF1E1F29),
    surface = Color(0xFF282A36),
    surfaceElevated = Color(0xFF343746),
    extraKeysBg = Color(0xFF21222C),
    extraKeysButton = Color(0xFF383A4B),
    extraKeysActive = Color(0xFFBD93F9),
    foreground = Color(0xFFF8F8F2),
    mutedForeground = Color(0xFF8B93B8),
    cursor = Color(0xFF50FA7B),
    accentGreen = Color(0xFF50FA7B),
    accentCyan = Color(0xFF8BE9FD),
    accentYellow = Color(0xFFF1FA8C),
    accentRed = Color(0xFFFF5555),
    accentBlue = Color(0xFF6272A4),
    accentMagenta = Color(0xFFFF79C6),
    borderSubtle = Color(0xFF44475A)
)

val SolarizedDarkPalette = TerminalPalette(
    id = "solarized",
    arabicName = "Solarized الداكن",
    background = Color(0xFF002B36),
    surface = Color(0xFF073642),
    surfaceElevated = Color(0xFF0B4352),
    extraKeysBg = Color(0xFF00212B),
    extraKeysButton = Color(0xFF0A3D4B),
    extraKeysActive = Color(0xFF2AA198),
    foreground = Color(0xFFEEE8D5),
    mutedForeground = Color(0xFF93A1A1),
    cursor = Color(0xFF859900),
    accentGreen = Color(0xFF859900),
    accentCyan = Color(0xFF2AA198),
    accentYellow = Color(0xFFB58900),
    accentRed = Color(0xFFDC322F),
    accentBlue = Color(0xFF268BD2),
    accentMagenta = Color(0xFFD33682),
    borderSubtle = Color(0xFF164F5E)
)

val GruvboxDarkPalette = TerminalPalette(
    id = "gruvbox",
    arabicName = "Gruvbox الدافئ",
    background = Color(0xFF1D2021),
    surface = Color(0xFF282828),
    surfaceElevated = Color(0xFF3C3836),
    extraKeysBg = Color(0xFF1B1B1B),
    extraKeysButton = Color(0xFF32302F),
    extraKeysActive = Color(0xFFB8BB26),
    foreground = Color(0xFFEBDBB2),
    mutedForeground = Color(0xFFA89984),
    cursor = Color(0xFFFE8019),
    accentGreen = Color(0xFFB8BB26),
    accentCyan = Color(0xFF8EC07C),
    accentYellow = Color(0xFFFABD2F),
    accentRed = Color(0xFFFB4934),
    accentBlue = Color(0xFF83A598),
    accentMagenta = Color(0xFFD3869B),
    borderSubtle = Color(0xFF504945)
)

val AllTerminalPalettes = listOf(
    TermuxClassicPalette,
    DraculaPalette,
    SolarizedDarkPalette,
    GruvboxDarkPalette
)
