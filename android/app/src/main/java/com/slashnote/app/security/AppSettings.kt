package com.slashnote.app.security

import android.content.Context
import android.content.SharedPreferences

object AppSettings {
    private const val PREFS_NAME = "slashnote_settings"
    private const val KEY_NOTE_TEMPLATE = "key_note_template"
    private const val KEY_IGNORED_PATTERNS = "key_ignored_patterns"
    private const val KEY_IGNORED_FILE_TYPES = "key_ignored_file_types"
    private const val KEY_PINNED_NOTE_IDS = "key_pinned_note_ids"
    private const val KEY_FONT_SIZE_SP = "key_font_size_sp"
    private const val KEY_IS_RTL = "key_is_rtl"
    private const val KEY_CUSTOM_VAULT_PATH = "key_custom_vault_path"
    private const val KEY_SHOW_ALL_FILES = "key_show_all_files"
    private const val KEY_RECENTLY_VISITED_NOTES = "key_recently_visited_notes"
    private const val KEY_THEME_NAME = "key_theme_name"
    private const val KEY_DARK_THEME = "key_dark_theme"
    private const val KEY_SP_FOLDER_COLORS_LIGHT = "key_sp_folder_colors_light"
    private const val KEY_SP_FOLDER_COLORS_DARK = "key_sp_folder_colors_dark"
    private const val KEY_FOLDER_CATEGORIES = "key_folder_categories"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getRecentlyVisitedNotes(context: Context): List<String> {
        val raw = getPrefs(context).getString(KEY_RECENTLY_VISITED_NOTES, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("|").filter { it.isNotBlank() }
    }

    fun recordNoteVisited(context: Context, relativePath: String) {
        if (relativePath.isBlank()) return
        val current = getRecentlyVisitedNotes(context).toMutableList()
        current.remove(relativePath)
        current.add(0, relativePath)
        val trimmed = current.take(10)
        getPrefs(context).edit().putString(KEY_RECENTLY_VISITED_NOTES, trimmed.joinToString("|")).apply()
    }

    fun updateNoteVisitedPath(context: Context, oldPath: String, newPath: String) {
        if (oldPath.isBlank() || newPath.isBlank()) return
        val current = getRecentlyVisitedNotes(context).toMutableList()
        val index = current.indexOf(oldPath)
        if (index != -1) {
            current[index] = newPath
        } else {
            current.add(0, newPath)
        }
        val trimmed = current.distinct().take(10)
        getPrefs(context).edit().putString(KEY_RECENTLY_VISITED_NOTES, trimmed.joinToString("|")).apply()
    }

    fun removeNoteVisitedPath(context: Context, path: String) {
        if (path.isBlank()) return
        val current = getRecentlyVisitedNotes(context).toMutableList()
        current.remove(path)
        getPrefs(context).edit().putString(KEY_RECENTLY_VISITED_NOTES, current.joinToString("|")).apply()
    }

    fun getShowAllFiles(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHOW_ALL_FILES, false)
    }

    fun saveShowAllFiles(context: Context, showAll: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_ALL_FILES, showAll).apply()
    }

    fun getCustomVaultPath(context: Context): String {
        return getPrefs(context).getString(KEY_CUSTOM_VAULT_PATH, "") ?: ""
    }

    fun saveCustomVaultPath(context: Context, path: String) {
        getPrefs(context).edit().putString(KEY_CUSTOM_VAULT_PATH, path).apply()
    }

    fun getNoteTemplate(context: Context): String {
        return getPrefs(context).getString(KEY_NOTE_TEMPLATE, "Note-{year}-{month}-{day}") ?: "Note-{year}-{month}-{day}"
    }

    fun saveNoteTemplate(context: Context, template: String) {
        getPrefs(context).edit().putString(KEY_NOTE_TEMPLATE, template).apply()
    }

    fun getIgnoredPatterns(context: Context): String {
        return getPrefs(context).getString(KEY_IGNORED_PATTERNS, ".git,node_modules,.trash,dist,target") ?: ".git,node_modules,.trash,dist,target"
    }

    fun saveIgnoredPatterns(context: Context, patterns: String) {
        getPrefs(context).edit().putString(KEY_IGNORED_PATTERNS, patterns).apply()
    }

    fun getIgnoredFileTypes(context: Context): List<String>? {
        val raw = getPrefs(context).getString(KEY_IGNORED_FILE_TYPES, null) ?: return null
        if (raw.isBlank()) return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    fun saveIgnoredFileTypes(context: Context, types: List<String>?) {
        if (types == null) {
            getPrefs(context).edit().remove(KEY_IGNORED_FILE_TYPES).apply()
        } else {
            val joined = types.joinToString(",")
            getPrefs(context).edit().putString(KEY_IGNORED_FILE_TYPES, joined).apply()
        }
    }

    fun getPinnedNoteIds(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_PINNED_NOTE_IDS, emptySet()) ?: emptySet()
    }

    fun togglePinnedNoteId(context: Context, noteId: String): Boolean {
        val current = getPinnedNoteIds(context).toMutableSet()
        val isNowPinned = if (current.contains(noteId)) {
            current.remove(noteId)
            false
        } else {
            current.add(noteId)
            true
        }
        getPrefs(context).edit().putStringSet(KEY_PINNED_NOTE_IDS, current).apply()
        return isNowPinned
    }

    fun getFontSizeSp(context: Context): Int {
        return getPrefs(context).getInt(KEY_FONT_SIZE_SP, 16)
    }

    fun saveFontSizeSp(context: Context, sizeSp: Int) {
        getPrefs(context).edit().putInt(KEY_FONT_SIZE_SP, sizeSp).apply()
    }

    fun getIsRtl(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_RTL, false)
    }

    fun saveIsRtl(context: Context, isRtl: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IS_RTL, isRtl).apply()
    }

    fun getThemeName(context: Context): String {
        return getPrefs(context).getString(KEY_THEME_NAME, "slashnote") ?: "slashnote"
    }

    fun saveThemeName(context: Context, themeName: String) {
        getPrefs(context).edit().putString(KEY_THEME_NAME, themeName).apply()
    }

    fun getDarkTheme(context: Context, systemDefault: Boolean): Boolean {
        val prefs = getPrefs(context)
        return if (prefs.contains(KEY_DARK_THEME)) {
            prefs.getBoolean(KEY_DARK_THEME, systemDefault)
        } else {
            systemDefault
        }
    }

    fun saveDarkTheme(context: Context, isDark: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DARK_THEME, isDark).apply()
    }

    fun getSoftPaperFolderColors(context: Context, isDark: Boolean): Map<String, String> {
        val prefKey = if (isDark) KEY_SP_FOLDER_COLORS_DARK else KEY_SP_FOLDER_COLORS_LIGHT
        val raw = getPrefs(context).getString(prefKey, "") ?: ""
        if (raw.isBlank()) return emptyMap()
        return raw.split(";")
            .mapNotNull { entry ->
                val parts = entry.split("=", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    parts[0].trim() to parts[1].trim()
                } else null
            }
            .toMap()
    }

    fun saveSoftPaperFolderColor(context: Context, isDark: Boolean, key: String, hexColor: String) {
        val current = getSoftPaperFolderColors(context, isDark).toMutableMap()
        current[key] = hexColor
        val prefKey = if (isDark) KEY_SP_FOLDER_COLORS_DARK else KEY_SP_FOLDER_COLORS_LIGHT
        val serialized = current.entries.joinToString(";") { "${it.key}=${it.value}" }
        getPrefs(context).edit().putString(prefKey, serialized).apply()
    }

    fun resetSoftPaperFolderColor(context: Context, isDark: Boolean, key: String) {
        val current = getSoftPaperFolderColors(context, isDark).toMutableMap()
        current.remove(key)
        val prefKey = if (isDark) KEY_SP_FOLDER_COLORS_DARK else KEY_SP_FOLDER_COLORS_LIGHT
        if (current.isEmpty()) {
            getPrefs(context).edit().remove(prefKey).apply()
        } else {
            val serialized = current.entries.joinToString(";") { "${it.key}=${it.value}" }
            getPrefs(context).edit().putString(prefKey, serialized).apply()
        }
    }

    fun resetAllSoftPaperFolderColors(context: Context, isDark: Boolean) {
        val prefKey = if (isDark) KEY_SP_FOLDER_COLORS_DARK else KEY_SP_FOLDER_COLORS_LIGHT
        getPrefs(context).edit().remove(prefKey).apply()
    }

    fun getFolderCategories(context: Context): Map<String, Int> {
        val raw = getPrefs(context).getString(KEY_FOLDER_CATEGORIES, "") ?: ""
        if (raw.isBlank()) return emptyMap()
        return raw.split(";").mapNotNull { entry ->
            val parts = entry.split("=", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank()) {
                val cat = parts[1].toIntOrNull()
                if (cat != null && cat in 1..3) parts[0].trim() to cat else null
            } else null
        }.toMap()
    }

    fun setFolderCategory(context: Context, folderPath: String, category: Int?) {
        val current = getFolderCategories(context).toMutableMap()
        if (category == null || category !in 1..3) {
            current.remove(folderPath)
        } else {
            current[folderPath] = category
        }
        if (current.isEmpty()) {
            getPrefs(context).edit().remove(KEY_FOLDER_CATEGORIES).apply()
        } else {
            val serialized = current.entries.joinToString(";") { "${it.key}=${it.value}" }
            getPrefs(context).edit().putString(KEY_FOLDER_CATEGORIES, serialized).apply()
        }
    }

    fun updateFolderCategoryPath(context: Context, oldPath: String, newPath: String) {
        val current = getFolderCategories(context).toMutableMap()
        val cat = current.remove(oldPath)
        if (cat != null) {
            current[newPath] = cat
            val serialized = current.entries.joinToString(";") { "${it.key}=${it.value}" }
            getPrefs(context).edit().putString(KEY_FOLDER_CATEGORIES, serialized).apply()
        }
    }
}
