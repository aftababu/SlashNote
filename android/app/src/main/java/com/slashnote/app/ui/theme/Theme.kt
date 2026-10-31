package com.slashnote.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat

enum class AppTheme(val id: String, val displayName: String) {
    SLASHNOTE("slashnote", "SlashNote"),
    OBSIDIAN_SOFT_PAPER("obsidian-soft-paper", "Obsidian Soft Paper");

    companion object {
        fun fromId(id: String?): AppTheme {
            return entries.find { it.id == id } ?: SLASHNOTE
        }
    }
}

data class SoftPaperFolderColorSpec(
    val key: String,
    val label: String,
    val defaultLightHex: String,
    val defaultDarkHex: String
)

val SoftPaperFolderColorSpecs = listOf(
    SoftPaperFolderColorSpec("folder-1", "Folder Category 1 (Rose)", "#BF7B87", "#CF7878"),
    SoftPaperFolderColorSpec("folder-2", "Folder Category 2 (Lavender)", "#9D8AB5", "#A888C2"),
    SoftPaperFolderColorSpec("folder-3", "Folder Category 3 (Amber)", "#CF9855", "#B8B040"),
    SoftPaperFolderColorSpec("folder-expanded", "Expanded / Active Folder", "#72A3A8", "#1FA3AC"),
    SoftPaperFolderColorSpec("folder-default", "Other Folders", "#918E8A", "#8C8F94"),
    SoftPaperFolderColorSpec("root-note", "Root Note Pill", "#DAD7D2", "#5C6274")
)

fun parseHexColorOrDefault(hex: String?, defaultColor: Color): Color {
    if (hex.isNullOrBlank()) return defaultColor
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 6 && clean.length != 8) return defaultColor
    return try {
        val parsed = clean.toLong(16)
        if (clean.length == 6) {
            Color(0xFF000000L or parsed)
        } else {
            Color(parsed)
        }
    } catch (_: Exception) {
        defaultColor
    }
}

fun readableTextForBackground(bg: Color, fallback: Color): Color {
    // WCAG relative luminance
    fun channel(c: Float): Float {
        return if (c <= 0.03928f) c / 12.92f else Math.pow(((c + 0.055) / 1.055), 2.4).toFloat()
    }
    val lum = 0.2126f * channel(bg.red) + 0.7152f * channel(bg.green) + 0.0722f * channel(bg.blue)
    return if (lum > 0.36f) Color(0xFF1E1E28) else Color(0xFFFAF6F0)
}

@Immutable
data class SlashNoteExtendedColors(
    val isSoftPaper: Boolean,
    val isDark: Boolean,
    val h1: Color,
    val h2: Color,
    val h3: Color,
    val h4: Color,
    val h5: Color,
    val h6: Color,
    val boldAccent: Color,
    val linkColor: Color,
    val codeBg: Color,
    val codeBorder: Color,
    val syntaxKeyword: Color,
    val syntaxType: Color,
    val syntaxString: Color,
    val syntaxComment: Color,
    val syntaxNumber: Color,
    val folderPill1: Color,
    val folderPill2: Color,
    val folderPill3: Color,
    val folderPillExpanded: Color,
    val folderPillDefault: Color,
    val folderPillText: Color,
    val rootNotePillBg: Color,
    val rootNotePillText: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    SlashNoteExtendedColors(
        isSoftPaper = false,
        isDark = false,
        h1 = LightTextPrimary,
        h2 = LightTextPrimary,
        h3 = LightTextPrimary,
        h4 = LightTextPrimary,
        h5 = LightTextSecondary,
        h6 = LightTextSecondary,
        boldAccent = LightTextPrimary,
        linkColor = Color(0xFF0096FF),
        codeBg = LightSurfaceElevated,
        codeBorder = LightBorder,
        syntaxKeyword = LightTextPrimary,
        syntaxType = LightTextPrimary,
        syntaxString = LightTextPrimary,
        syntaxComment = LightTextSecondary,
        syntaxNumber = LightTextPrimary,
        folderPill1 = Color.Transparent,
        folderPill2 = Color.Transparent,
        folderPill3 = Color.Transparent,
        folderPillExpanded = Color.Transparent,
        folderPillDefault = Color.Transparent,
        folderPillText = LightTextPrimary,
        rootNotePillBg = Color.Transparent,
        rootNotePillText = LightTextPrimary
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = DarkTextPrimary,
    onPrimary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = LightTextPrimary,
    onPrimary = LightSurface,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder
)

private val SoftPaperLightColorScheme = lightColorScheme(
    primary = SoftPaperLightAccent,
    onPrimary = SoftPaperLightSurfaceElevated,
    background = SoftPaperLightBackground,
    onBackground = SoftPaperLightTextPrimary,
    surface = SoftPaperLightSurface,
    onSurface = SoftPaperLightTextPrimary,
    surfaceVariant = SoftPaperLightSurfaceElevated,
    onSurfaceVariant = SoftPaperLightTextSecondary,
    outline = SoftPaperLightBorder
)

private val SoftPaperDarkColorScheme = darkColorScheme(
    primary = SoftPaperDarkAccent,
    onPrimary = SoftPaperDarkSurface,
    background = SoftPaperDarkBackground,
    onBackground = SoftPaperDarkTextPrimary,
    surface = SoftPaperDarkSurface,
    onSurface = SoftPaperDarkTextPrimary,
    surfaceVariant = SoftPaperDarkSurfaceElevated,
    onSurfaceVariant = SoftPaperDarkTextSecondary,
    outline = SoftPaperDarkBorder
)

private fun buildExtendedColors(
    appTheme: AppTheme,
    darkTheme: Boolean,
    softPaperFolderColors: Map<String, String> = emptyMap()
): SlashNoteExtendedColors {
    return when (appTheme) {
        AppTheme.SLASHNOTE -> {
            if (darkTheme) {
                SlashNoteExtendedColors(
                    isSoftPaper = false,
                    isDark = true,
                    h1 = DarkTextPrimary,
                    h2 = DarkTextPrimary,
                    h3 = DarkTextPrimary,
                    h4 = DarkTextPrimary,
                    h5 = DarkTextSecondary,
                    h6 = DarkTextSecondary,
                    boldAccent = DarkTextPrimary,
                    linkColor = Color(0xFF0096FF),
                    codeBg = DarkSurfaceElevated,
                    codeBorder = DarkBorder,
                    syntaxKeyword = DarkTextPrimary,
                    syntaxType = DarkTextPrimary,
                    syntaxString = DarkTextPrimary,
                    syntaxComment = DarkTextSecondary,
                    syntaxNumber = DarkTextPrimary,
                    folderPill1 = Color.Transparent,
                    folderPill2 = Color.Transparent,
                    folderPill3 = Color.Transparent,
                    folderPillExpanded = Color.Transparent,
                    folderPillDefault = Color.Transparent,
                    folderPillText = DarkTextPrimary,
                    rootNotePillBg = Color.Transparent,
                    rootNotePillText = DarkTextPrimary
                )
            } else {
                SlashNoteExtendedColors(
                    isSoftPaper = false,
                    isDark = false,
                    h1 = LightTextPrimary,
                    h2 = LightTextPrimary,
                    h3 = LightTextPrimary,
                    h4 = LightTextPrimary,
                    h5 = LightTextSecondary,
                    h6 = LightTextSecondary,
                    boldAccent = LightTextPrimary,
                    linkColor = Color(0xFF0096FF),
                    codeBg = LightSurfaceElevated,
                    codeBorder = LightBorder,
                    syntaxKeyword = LightTextPrimary,
                    syntaxType = LightTextPrimary,
                    syntaxString = LightTextPrimary,
                    syntaxComment = LightTextSecondary,
                    syntaxNumber = LightTextPrimary,
                    folderPill1 = Color.Transparent,
                    folderPill2 = Color.Transparent,
                    folderPill3 = Color.Transparent,
                    folderPillExpanded = Color.Transparent,
                    folderPillDefault = Color.Transparent,
                    folderPillText = LightTextPrimary,
                    rootNotePillBg = Color.Transparent,
                    rootNotePillText = LightTextPrimary
                )
            }
        }
        AppTheme.OBSIDIAN_SOFT_PAPER -> {
            if (darkTheme) {
                val rootNoteBg = parseHexColorOrDefault(softPaperFolderColors["root-note"], Color(0xFF5C6274))
                SlashNoteExtendedColors(
                    isSoftPaper = true,
                    isDark = true,
                    h1 = Color(0xFFB8C4E0),
                    h2 = Color(0xFFD9936A),
                    h3 = Color(0xFFCF7682),
                    h4 = Color(0xFFB39DDB),
                    h5 = Color(0xFF76B5BD),
                    h6 = Color(0xFF9BA2B2),
                    boldAccent = Color(0xFFCF7682),
                    linkColor = Color(0xFF76B5BD),
                    codeBg = SoftPaperDarkCodeBg,
                    codeBorder = SoftPaperDarkCodeBorder,
                    syntaxKeyword = Color(0xFFD97798),
                    syntaxType = Color(0xFFCFBD63),
                    syntaxString = Color(0xFF78B886),
                    syntaxComment = Color(0xFF9BA2B2),
                    syntaxNumber = Color(0xFF76B5BD),
                    folderPill1 = parseHexColorOrDefault(softPaperFolderColors["folder-1"], Color(0xFFCF7878)),
                    folderPill2 = parseHexColorOrDefault(softPaperFolderColors["folder-2"], Color(0xFFA888C2)),
                    folderPill3 = parseHexColorOrDefault(softPaperFolderColors["folder-3"], Color(0xFFB8B040)),
                    folderPillExpanded = parseHexColorOrDefault(softPaperFolderColors["folder-expanded"], Color(0xFF1FA3AC)),
                    folderPillDefault = parseHexColorOrDefault(softPaperFolderColors["folder-default"], Color(0xFF8C8F94)),
                    folderPillText = Color(0xFF1C1E26),
                    rootNotePillBg = rootNoteBg,
                    rootNotePillText = if (softPaperFolderColors.containsKey("root-note")) {
                        readableTextForBackground(rootNoteBg, Color(0xFFE6EAF2))
                    } else Color(0xFFE6EAF2)
                )
            } else {
                val rootNoteBg = parseHexColorOrDefault(softPaperFolderColors["root-note"], Color(0xFFDAD7D2))
                SlashNoteExtendedColors(
                    isSoftPaper = true,
                    isDark = false,
                    h1 = Color(0xFF54507A),
                    h2 = Color(0xFFC9725B),
                    h3 = Color(0xFF9C5668),
                    h4 = Color(0xFF7B6294),
                    h5 = Color(0xFF528085),
                    h6 = Color(0xFF7A7485),
                    boldAccent = Color(0xFF9C5668),
                    linkColor = Color(0xFF54507A),
                    codeBg = SoftPaperLightCodeBg,
                    codeBorder = SoftPaperLightCodeBorder,
                    syntaxKeyword = Color(0xFFB85478),
                    syntaxType = Color(0xFFB87D3B),
                    syntaxString = Color(0xFF528A60),
                    syntaxComment = Color(0xFF625E78),
                    syntaxNumber = Color(0xFF48788A),
                    folderPill1 = parseHexColorOrDefault(softPaperFolderColors["folder-1"], Color(0xFFBF7B87)),
                    folderPill2 = parseHexColorOrDefault(softPaperFolderColors["folder-2"], Color(0xFF9D8AB5)),
                    folderPill3 = parseHexColorOrDefault(softPaperFolderColors["folder-3"], Color(0xFFCF9855)),
                    folderPillExpanded = parseHexColorOrDefault(softPaperFolderColors["folder-expanded"], Color(0xFF72A3A8)),
                    folderPillDefault = parseHexColorOrDefault(softPaperFolderColors["folder-default"], Color(0xFF918E8A)),
                    folderPillText = Color(0xFFFBF9F6),
                    rootNotePillBg = rootNoteBg,
                    rootNotePillText = if (softPaperFolderColors.containsKey("root-note")) {
                        readableTextForBackground(rootNoteBg, Color(0xFF464255))
                    } else Color(0xFF464255)
                )
            }
        }
    }
}

@Composable
fun SlashNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    appTheme: AppTheme = AppTheme.SLASHNOTE,
    softPaperFolderColors: Map<String, String> = emptyMap(),
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        AppTheme.SLASHNOTE -> if (darkTheme) DarkColorScheme else LightColorScheme
        AppTheme.OBSIDIAN_SOFT_PAPER -> if (darkTheme) SoftPaperDarkColorScheme else SoftPaperLightColorScheme
    }
    val extendedColors = buildExtendedColors(appTheme, darkTheme, softPaperFolderColors)
    val view = LocalView.current

    if (!view.isInEditMode) {
        DisposableEffect(darkTheme, appTheme) {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowInsetsControllerCompat(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
            onDispose {}
        }
    }

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
