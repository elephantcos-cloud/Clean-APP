package com.shohan.cleanspace.ui.theme

import androidx.compose.ui.graphics.Color

// ---------- Primary: deep pine green (CleanSpace brand, muted for a more
// restrained, professional feel than the original bright emerald) ----------
val Emerald50  = Color(0xFFF1F5F3)
val Emerald100 = Color(0xFFDCE7E1)
val Emerald400 = Color(0xFF5C8C76)
val Emerald600 = Color(0xFF2F5D4C)
val Emerald700 = Color(0xFF234A3C)

// ---------- Accent palette — muted, low-saturation tones ----------
val Sky     = Color(0xFF4A6FA5)
val Amber   = Color(0xFFB8860B)
val Violet  = Color(0xFF6B5B95)
val Rose    = Color(0xFF8B3A3A)
val Orange  = Color(0xFFA8662D)

// ---------- Neutrals (light theme — warm, true neutral, not green-tinted) ----------
val LightBackground     = Color(0xFFF7F7F5)
val LightSurface        = Color(0xFFFFFFFF)
val LightSurfaceVar     = Color(0xFFEDEDE9)
val LightOnSurface      = Color(0xFF1C1C1A)
val LightOnSurfaceMuted = Color(0xFF5F5F5A)
val LightOutline        = Color(0xFFDDDDD8)

// ---------- Neutrals (dark theme) ----------
val DarkBackground     = Color(0xFF14140F)
val DarkSurface        = Color(0xFF1E1E18)
val DarkSurfaceVar      = Color(0xFF2A2A22)
val DarkOnSurface       = Color(0xFFF2F2EE)
val DarkOnSurfaceMuted  = Color(0xFFA8A89E)
val DarkOutline         = Color(0xFF3A3A30)

// ---------- Backward-compatible aliases ----------
val BluePrimary  = Emerald600
val GreenAccent  = Emerald600
val OrangeAccent = Amber
val RedAccent    = Rose
val PurpleAccent = Violet
val TealAccent   = Sky
val Cyan         = Sky

val CategoryColors = listOf(
    Emerald600, Sky, Amber, Violet, Orange, Rose
)
