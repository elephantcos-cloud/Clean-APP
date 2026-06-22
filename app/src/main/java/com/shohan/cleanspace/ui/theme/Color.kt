package com.shohan.cleanspace.ui.theme

import androidx.compose.ui.graphics.Color

// ---------- Primary: Emerald green (Reclaim brand) ----------
val Emerald50  = Color(0xFFECFDF5)
val Emerald100 = Color(0xFFD1FAE5)
val Emerald400 = Color(0xFF34D399)
val Emerald600 = Color(0xFF059669)
val Emerald700 = Color(0xFF047857)

// ---------- Accent palette ----------
val Sky     = Color(0xFF0EA5E9)
val Amber   = Color(0xFFF59E0B)
val Violet  = Color(0xFF8B5CF6)
val Rose    = Color(0xFFF43F5E)
val Orange  = Color(0xFFF97316)

// ---------- Neutrals (light theme — off-white, clean) ----------
val LightBackground    = Color(0xFFF8FAF9)
val LightSurface       = Color(0xFFFFFFFF)
val LightSurfaceVar    = Color(0xFFF0F7F4)
val LightOnSurface     = Color(0xFF111827)
val LightOnSurfaceMuted = Color(0xFF6B7280)
val LightOutline       = Color(0xFFE5E7EB)

// ---------- Neutrals (dark theme) ----------
val DarkBackground  = Color(0xFF0A0F0D)
val DarkSurface     = Color(0xFF131A16)
val DarkSurfaceVar  = Color(0xFF1C2620)
val DarkOnSurface   = Color(0xFFF9FAFB)
val DarkOnSurfaceMuted = Color(0xFF9CA3AF)
val DarkOutline     = Color(0xFF2D3A34)

// ---------- Backward-compatible aliases ----------
val BluePrimary  = Emerald600
val GreenAccent  = Emerald600
val OrangeAccent = Amber
val RedAccent    = Rose
val PurpleAccent = Violet
val TealAccent   = Sky
val Cyan         = Sky
val Indigo600    = Emerald600
val Indigo50     = Emerald50
val Indigo100    = Emerald100
val Indigo400    = Emerald400
val Indigo700    = Emerald700

val CategoryColors = listOf(
    Emerald600, Sky, Amber, Violet, Orange, Rose
)
