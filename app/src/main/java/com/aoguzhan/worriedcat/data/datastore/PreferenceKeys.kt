package com.aoguzhan.worriedcat.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object PreferenceKeys {
    val SELECTED_LABELS = stringPreferencesKey("selected_labels")
    val FAMILIARITY_VALUE = floatPreferencesKey("familiarity_value")
}