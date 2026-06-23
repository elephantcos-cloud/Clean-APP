package com.shohan.cleanspace.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

// Single shared DataStore instance for all CleanSpace preferences (theme,
// ignore list, etc). DataStore throws at runtime if two different
// `preferencesDataStore(name = "...")` delegates are declared for the same
// file name, so this must be declared exactly once and reused everywhere.
val Context.cleanSpaceDataStore by preferencesDataStore(name = "cleanspace_settings")
