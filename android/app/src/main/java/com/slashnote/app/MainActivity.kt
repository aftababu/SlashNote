package com.slashnote.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.lazy.rememberLazyListState
import com.slashnote.app.ui.editor.DraggableEditorScrollbar
import com.slashnote.app.ui.editor.FormattingToolbar
import com.slashnote.app.ui.editor.isCursorInTaskList
import com.slashnote.app.ui.editor.toggleTaskListAtCursor
import com.slashnote.app.ui.editor.MarkdownEditor
import com.slashnote.app.ui.editor.MdBlock
import com.slashnote.app.ui.editor.parseMarkdownBlocks
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.SnippetFolder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.slashnote.app.security.AppSettings
import com.slashnote.app.security.SecureStorage
import com.slashnote.app.sync.GitSyncWorker
import com.slashnote.app.utils.StorageUtils
import com.slashnote.app.utils.UndoRedoManager
import com.slashnote.app.ui.components.BottomNavBar
import com.slashnote.app.ui.components.CommandPaletteSheet
import com.slashnote.app.ui.components.StitchBottomTab
import com.slashnote.app.ui.components.UnifiedSearchOverlay
import com.slashnote.app.ui.drawer.FolderTreeView
import com.slashnote.app.ui.drawer.TrashBinSheet
import com.slashnote.app.ui.editor.InNoteSearchBar
import com.slashnote.app.ui.editor.MarkdownPreview
import com.slashnote.app.ui.editor.MarkdownSpanCache
import com.slashnote.app.ui.editor.MarkdownVisualTransformation
import com.slashnote.app.ui.editor.markdownHash
import com.slashnote.app.ui.settings.SettingsScreen
import com.slashnote.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.slash_notes_core.*
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Resolve a SAF `content://` tree/document URI into a normalized physical POSIX
 * path. Android exposes `primary` storage at `/storage/emulated/0`; other
 * volumes map to `/storage/<volume>`.
 *
 * libgit2 runs in native Rust and cannot accept `content://` URIs, so this must
 * be called before the value is passed through the UniFFI bridge.
 */
private fun Uri.toAbsolutePath(): String {
    val raw = path ?: return toString()
    val decoded = Uri.decode(raw)
    val (volume, subPath) = when {
        decoded.contains(":") -> {
            val head = decoded.substringBefore(":").substringAfterLast("/")
            val tail = decoded.substringAfter(":")
            head to tail
        }
        decoded.contains("/tree/") -> "primary" to decoded.substringAfter("/tree/")
        decoded.contains("/document/") -> "primary" to decoded.substringAfter("/document/")
        else -> "primary" to decoded
    }

    val base = if (volume.equals("primary", ignoreCase = true)) {
        Environment.getExternalStorageDirectory().absolutePath
    } else {
        "/storage/$volume"
    }

    return File(base, subPath.trim('/')).absolutePath
}

private fun exportNoteToPdf(context: Context, filename: String, content: String) {
    try {
        if (content.isBlank()) {
            Toast.makeText(context, "Cannot export empty note to PDF", Toast.LENGTH_SHORT).show()
            return
        }
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 14f
            isAntiAlias = true
        }

        val titlePaint = android.text.TextPaint().apply {
            color = android.graphics.Color.BLACK
            textSize = 20f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val bodyPaint = android.text.TextPaint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 12f
            isAntiAlias = true
        }

        val cleanTitle = File(filename).nameWithoutExtension.ifEmpty { "Note" }
        var y = 50f
        canvas.drawText("SlashNote: $cleanTitle", 40f, y, titlePaint)
        y += 30f
        canvas.drawLine(40f, y, 555f, y, paint.apply { strokeWidth = 1f })
        y += 25f

        val textLayoutWidth = 515
        val lines = content.split("\n")

        for (rawLine in lines) {
            val textLayout = android.text.StaticLayout.Builder.obtain(rawLine, 0, rawLine.length, bodyPaint, textLayoutWidth)
                .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.2f)
                .build()

            if (y + textLayout.height > 800f) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 50f
            }

            canvas.save()
            canvas.translate(40f, y)
            textLayout.draw(canvas)
            canvas.restore()
            y += textLayout.height + 10f
        }

        pdfDocument.finishPage(page)

        val sanitizedTitle = cleanTitle.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val pdfName = "${sanitizedTitle}_${System.currentTimeMillis()}.pdf"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, pdfName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    pdfDocument.writeTo(out)
                }
                Toast.makeText(context, "Exported PDF to Downloads/$pdfName", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Failed to create PDF in Downloads", Toast.LENGTH_SHORT).show()
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, pdfName)
            java.io.FileOutputStream(file).use { out ->
                pdfDocument.writeTo(out)
            }
            Toast.makeText(context, "Exported PDF to Downloads/$pdfName", Toast.LENGTH_LONG).show()
        }
        pdfDocument.close()
    } catch (e: Exception) {
        Log.e("PdfExport", "Error exporting PDF", e)
        Toast.makeText(context, "PDF Export Failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun scanVaultFilesRecursively(rootPath: String): List<NoteHeader> {
    if (rootPath.isBlank()) return emptyList()
    val dir = File(rootPath)
    if (!dir.exists() || !dir.isDirectory) return emptyList()

    val result = mutableListOf<Pair<Long, NoteHeader>>()
    fun walk(current: File) {
        val files = current.listFiles() ?: return
        for (f in files) {
            if (f.isDirectory) {
                if (!f.name.startsWith(".")) {
                    walk(f)
                }
            } else if (f.isFile) {
                val name = f.name
                if (name.endsWith(".md", ignoreCase = true) || name.endsWith(".markdown", ignoreCase = true)) {
                    val relPath = f.absolutePath.substringAfter(dir.absolutePath).removePrefix("/")
                    val title = name.substringBeforeLast(".")
                    val lastMod = f.lastModified()

                    result.add(lastMod to NoteHeader(id = relPath, title = title))
                }
            }
        }
    }
    walk(dir)
    return result.sortedByDescending { it.first }.map { it.second }
}

sealed interface VaultState {
    object Loading : VaultState
    data class Loaded(val vaultPath: String, val notes: List<NoteHeader>) : VaultState
    object NoVaultSelected : VaultState
}

data class NoteNavEntry(
    val filename: String,
    val isPreviewMode: Boolean = true,
    val previewIndex: Int = 0,
    val previewOffset: Int = 0,
    val editorScrollY: Int = 0,
    val editorCursor: Int = 0
)

val NoteBackStackSaver = listSaver<SnapshotStateList<NoteNavEntry>, Any>(
    save = { stack ->
        stack.map { entry ->
            listOf(
                entry.filename,
                if (entry.isPreviewMode) 1 else 0,
                entry.previewIndex,
                entry.previewOffset,
                entry.editorScrollY,
                entry.editorCursor
            )
        }
    },
    restore = { savedList ->
        val stateList = mutableStateListOf<NoteNavEntry>()
        savedList.forEach { item ->
            @Suppress("UNCHECKED_CAST")
            val l = item as List<Any>
            stateList.add(
                NoteNavEntry(
                    filename = l[0] as String,
                    isPreviewMode = (l[1] as Number).toInt() == 1,
                    previewIndex = (l[2] as Number).toInt(),
                    previewOffset = (l[3] as Number).toInt(),
                    editorScrollY = (l[4] as Number).toInt(),
                    editorCursor = (l[5] as Number).toInt()
                )
            )
        }
        stateList
    }
)

val NullableNoteNavEntrySaver = listSaver<NoteNavEntry?, Any>(
    save = { entry ->
        if (entry == null) emptyList() else listOf(
            entry.filename,
            if (entry.isPreviewMode) 1 else 0,
            entry.previewIndex,
            entry.previewOffset,
            entry.editorScrollY,
            entry.editorCursor
        )
    },
    restore = { list ->
        if (list.isEmpty()) null else {
            NoteNavEntry(
                filename = list[0] as String,
                isPreviewMode = (list[1] as Number).toInt() == 1,
                previewIndex = (list[2] as Number).toInt(),
                previewOffset = (list[3] as Number).toInt(),
                editorScrollY = (list[4] as Number).toInt(),
                editorCursor = (list[5] as Number).toInt()
            )
        }
    }
)

private const val TAG = "SlashNoteVault"

class MainActivity : ComponentActivity() {
    private var isVaultReady = false

    companion object {
        init {
            System.loadLibrary("slash_notes_core")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !isVaultReady }
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Schedule silent background git sync
        GitSyncWorker.schedulePeriodicSync(this)

        setContent {
            val context = LocalContext.current
            val systemDark = isSystemInDarkTheme()
            var darkTheme by remember { mutableStateOf(AppSettings.getDarkTheme(context, systemDark)) }
            var appTheme by remember { mutableStateOf(AppTheme.fromId(AppSettings.getThemeName(context))) }
            var spFolderColorsLight by remember {
                mutableStateOf(AppSettings.getSoftPaperFolderColors(context, isDark = false))
            }
            var spFolderColorsDark by remember {
                mutableStateOf(AppSettings.getSoftPaperFolderColors(context, isDark = true))
            }
            val activeSpFolderColors = if (darkTheme) spFolderColorsDark else spFolderColorsLight

            SlashNoteTheme(
                darkTheme = darkTheme,
                appTheme = appTheme,
                softPaperFolderColors = activeSpFolderColors
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SlashNoteApp(
                        isDarkTheme = darkTheme,
                        onToggleTheme = {
                            val nextDark = !darkTheme
                            darkTheme = nextDark
                            AppSettings.saveDarkTheme(context, nextDark)
                        },
                        appTheme = appTheme,
                        onSelectAppTheme = { selectedTheme ->
                            appTheme = selectedTheme
                            AppSettings.saveThemeName(context, selectedTheme.id)
                        },
                        softPaperFolderColors = activeSpFolderColors,
                        onSetSoftPaperFolderColor = { key, hexColor ->
                            AppSettings.saveSoftPaperFolderColor(context, darkTheme, key, hexColor)
                            if (darkTheme) {
                                spFolderColorsDark = AppSettings.getSoftPaperFolderColors(context, isDark = true)
                            } else {
                                spFolderColorsLight = AppSettings.getSoftPaperFolderColors(context, isDark = false)
                            }
                        },
                        onResetSoftPaperFolderColor = { key ->
                            AppSettings.resetSoftPaperFolderColor(context, darkTheme, key)
                            if (darkTheme) {
                                spFolderColorsDark = AppSettings.getSoftPaperFolderColors(context, isDark = true)
                            } else {
                                spFolderColorsLight = AppSettings.getSoftPaperFolderColors(context, isDark = false)
                            }
                        },
                        onResetAllSoftPaperFolderColors = {
                            AppSettings.resetAllSoftPaperFolderColors(context, darkTheme)
                            if (darkTheme) {
                                spFolderColorsDark = emptyMap()
                            } else {
                                spFolderColorsLight = emptyMap()
                            }
                        },
                        onVaultReady = { isVaultReady = true }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlashNoteApp(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    appTheme: AppTheme = AppTheme.SLASHNOTE,
    onSelectAppTheme: (AppTheme) -> Unit = {},
    softPaperFolderColors: Map<String, String> = emptyMap(),
    onSetSoftPaperFolderColor: (key: String, hexColor: String) -> Unit = { _, _ -> },
    onResetSoftPaperFolderColor: (key: String) -> Unit = {},
    onResetAllSoftPaperFolderColors: () -> Unit = {},
    onVaultReady: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var customVaultPathState by remember { mutableStateOf(AppSettings.getCustomVaultPath(context)) }
    var showAllFilesState by remember { mutableStateOf(AppSettings.getShowAllFiles(context)) }

    val notesDir = remember(customVaultPathState) {
        val custom = customVaultPathState.trim()
        val resolved = if (custom.isNotEmpty()) {
            val file = File(custom)
            if (!file.exists()) file.mkdirs()
            if (file.exists() && file.isDirectory && file.canWrite()) {
                file.absolutePath
            } else {
                File(context.filesDir, "notes").apply { if (!exists()) mkdirs() }.absolutePath
            }
        } else {
            File(context.filesDir, "notes").apply { if (!exists()) mkdirs() }.absolutePath
        }
        Log.i(TAG, "Notes directory (POSIX): $resolved")
        resolved
    }

    var selectedFilename by remember { mutableStateOf<String?>(null) }
    var currentRelativeDir by remember { mutableStateOf("") }
    var activeBottomTab by remember { mutableStateOf(StitchBottomTab.EDITOR) }

    val undoRedoManager = remember(selectedFilename) { UndoRedoManager() }
    var activeUndoAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var activeRedoAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(selectedFilename) {
        undoRedoManager.clear()
        activeUndoAction = null
        activeRedoAction = null
    }

    var isSettingsOpen by remember { mutableStateOf(false) }
    var isTrashOpen by remember { mutableStateOf(false) }
    var isCreateNoteOpen by remember { mutableStateOf(false) }
    var isCreateFolderOpen by remember { mutableStateOf(false) }
    var isCommandPaletteOpen by remember { mutableStateOf(false) }
    var isUnifiedSearchOpen by remember { mutableStateOf(false) }
    var isSidebarSearchOpen by remember { mutableStateOf(false) }
    var sidebarSearchQuery by remember { mutableStateOf("") }
    var itemToRename by remember { mutableStateOf<FolderItem?>(null) }

    var isFocusMode by remember { mutableStateOf(false) }
    var isSourceMode by remember { mutableStateOf(false) }
    var isGlobalPreviewMode by remember { mutableStateOf(false) }
    var isSlashMenuVisible by remember { mutableStateOf(false) }
    var isWikilinkMenuVisible by remember { mutableStateOf(false) }

    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentMatchIndex by remember { mutableStateOf(0) }
    var matches by remember { mutableStateOf<List<TextRange>>(emptyList()) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    var allNotesList by remember { mutableStateOf<List<NoteHeader>>(emptyList()) }
    var pinnedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var syncErrorDialogText by remember { mutableStateOf<String?>(null) }
    val noteBackStack = rememberSaveable(saver = NoteBackStackSaver) {
        mutableStateListOf<NoteNavEntry>()
    }
    var activeRestoreEntry by rememberSaveable(stateSaver = NullableNoteNavEntrySaver) {
        mutableStateOf<NoteNavEntry?>(null)
    }
    var activeHeadingAnchor by rememberSaveable { mutableStateOf<String?>(null) }
    var editorFontSizeSp by rememberSaveable { mutableIntStateOf(AppSettings.getFontSizeSp(context)) }
    var isRefreshingNotes by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val refreshNotesList = {
        if (!isRefreshingNotes) {
            isRefreshingNotes = true
            scope.launch {
                val notes = withContext(Dispatchers.IO) {
                    val rustNotes = listNotes(notesDir)
                    if (rustNotes.isNotEmpty()) rustNotes else scanVaultFilesRecursively(notesDir)
                }
                pinnedIds = AppSettings.getPinnedNoteIds(context)
                allNotesList = notes
                isRefreshingNotes = false
            }
        }
    }

    LaunchedEffect(Unit) {
        StorageUtils.checkAndRequestStoragePermission(context)
    }

    var vaultState by remember { mutableStateOf<VaultState>(VaultState.Loading) }

    val safFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                Log.w(TAG, "Could not persist SAF URI permission", e)
            }

            StorageUtils.checkAndRequestStoragePermission(context)

            // Convert content:// tree URI to a physical POSIX path that libgit2 can use.
            val resolvedPath = uri.toAbsolutePath()
            Log.i(TAG, "SAF vault resolved to POSIX path: $resolvedPath")

            val vaultDir = File(resolvedPath)
            if (!vaultDir.exists()) vaultDir.mkdirs()

            if (!vaultDir.exists() || !vaultDir.isDirectory) {
                Toast.makeText(context, "Invalid vault directory: $resolvedPath", Toast.LENGTH_LONG).show()
                return@rememberLauncherForActivityResult
            }
            if (!vaultDir.canWrite()) {
                Toast.makeText(context, "No write permission for vault: $resolvedPath", Toast.LENGTH_LONG).show()
                return@rememberLauncherForActivityResult
            }

            AppSettings.saveCustomVaultPath(context, resolvedPath)
            customVaultPathState = resolvedPath
            Toast.makeText(context, "Vault: $resolvedPath", Toast.LENGTH_SHORT).show()

            // Immediate Index Sync: scan vault files synchronously before UI transition out of Loading
            scope.launch {
                val notes = withContext(Dispatchers.IO) {
                    val rustNotes = listNotes(resolvedPath)
                    if (rustNotes.isNotEmpty()) rustNotes else scanVaultFilesRecursively(resolvedPath)
                }
                withContext(Dispatchers.Main) {
                    allNotesList = notes
                    pinnedIds = AppSettings.getPinnedNoteIds(context)
                    vaultState = VaultState.Loaded(resolvedPath, notes)
                    refreshNotesList()
                }
            }
        }
    }

    LaunchedEffect(notesDir) {
        withContext(Dispatchers.IO) {
            val dir = File(notesDir)
            val customPath = AppSettings.getCustomVaultPath(context)
            if (!dir.exists() && customPath.isNotBlank()) dir.mkdirs()

            val notes = if (customPath.isNotBlank()) {
                val rustNotes = listNotes(notesDir)
                if (rustNotes.isNotEmpty()) rustNotes else scanVaultFilesRecursively(notesDir)
            } else {
                emptyList()
            }
            withContext(Dispatchers.Main) {
                allNotesList = notes
                pinnedIds = AppSettings.getPinnedNoteIds(context)
                if (customPath.isNotBlank()) {
                    vaultState = VaultState.Loaded(notesDir, notes)
                } else {
                    vaultState = VaultState.NoVaultSelected
                }
                onVaultReady()
            }
        }
    }

    val handleBottomTabSelect: (StitchBottomTab) -> Unit = remember {
        { tab ->
            activeBottomTab = tab
            when (tab) {
                StitchBottomTab.EDITOR -> {
                    isSettingsOpen = false
                    selectedFilename = null
                }
                StitchBottomTab.COMMAND_PALETTE -> isCommandPaletteOpen = true
                StitchBottomTab.SETTINGS -> isSettingsOpen = true
            }
        }
    }

    if (vaultState is VaultState.Loading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
    } else if (isSettingsOpen) {
        SettingsScreen(
            notesDir = notesDir,
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            appTheme = appTheme,
            onSelectAppTheme = onSelectAppTheme,
            softPaperFolderColors = softPaperFolderColors,
            onSetSoftPaperFolderColor = onSetSoftPaperFolderColor,
            onResetSoftPaperFolderColor = onResetSoftPaperFolderColor,
            onResetAllSoftPaperFolderColors = onResetAllSoftPaperFolderColors,
            fontSizeSp = editorFontSizeSp,
            onFontSizeChange = { newSize ->
                editorFontSizeSp = newSize
                AppSettings.saveFontSizeSp(context, newSize)
            },
            onBack = { isSettingsOpen = false },
            onPickCustomVaultPath = { safFolderLauncher.launch(null) }
        )
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                    drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                    modifier = Modifier
                        .width(300.dp)
                        .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                ) {
                    val vaultName = remember(notesDir) { File(notesDir).name.ifEmpty { "Vault" } }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (isSidebarSearchOpen) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = sidebarSearchQuery,
                                onValueChange = { sidebarSearchQuery = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                singleLine = true,
                                decorationBox = { innerTextField ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(modifier = Modifier.weight(1f)) {
                                            if (sidebarSearchQuery.isEmpty()) {
                                                Text("Search notes...", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                            }
                                            innerTextField()
                                        }
                                        if (sidebarSearchQuery.isNotEmpty()) {
                                            IconButton(onClick = { sidebarSearchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = {
                                isSidebarSearchOpen = false
                                sidebarSearchQuery = ""
                            }, contentPadding = PaddingValues(0.dp)) {
                                Text("Cancel", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            val hasCustomVault = customVaultPathState.isNotBlank()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (!hasCustomVault) {
                                            safFolderLauncher.launch(null)
                                        }
                                    }
                            ) {
                                Icon(
                                    imageVector = if (hasCustomVault) Icons.Outlined.SnippetFolder else Icons.Outlined.FolderOpen,
                                    contentDescription = if (hasCustomVault) "Vault" else "Open Vault",
                                    tint = if (hasCustomVault) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (hasCustomVault) vaultName else "Open Vault",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { refreshNotesList() },
                                    enabled = !isRefreshingNotes,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    if (isRefreshingNotes) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = "Refresh Notes",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { isSidebarSearchOpen = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Search Notes", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                                }
                                var isNewMenuExpanded by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(
                                        onClick = { if (hasCustomVault) isNewMenuExpanded = true },
                                        enabled = hasCustomVault,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = "New Item",
                                            tint = if (hasCustomVault) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = isNewMenuExpanded,
                                        onDismissRequest = { isNewMenuExpanded = false },
                                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("New Note", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                                            },
                                            onClick = {
                                                isNewMenuExpanded = false
                                                isCreateNoteOpen = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("New Folder", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp) },
                                            leadingIcon = {
                                                Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                                            },
                                            onClick = {
                                                isNewMenuExpanded = false
                                                isCreateFolderOpen = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = StitchBorder.copy(alpha = 0.4f))

                    // Virtualized Nested Folder Tree View or Search Results
                    Box(modifier = Modifier.weight(1f)) {
                        FolderTreeView(
                            notesDir = notesDir,
                            currentRelativeDir = currentRelativeDir,
                            selectedFilename = selectedFilename,
                            searchQuery = sidebarSearchQuery,
                            showAllFiles = showAllFilesState,
                            isRefreshing = isRefreshingNotes,
                            onRefreshNotes = { refreshNotesList() },
                            onNavigateDir = { currentRelativeDir = it },
                            onSelectNote = { relPath ->
                                scope.launch {
                                    drawerState.close()
                                    noteBackStack.clear()
                                    activeHeadingAnchor = null
                                    activeRestoreEntry = null
                                    selectedFilename = relPath
                                }
                            },
                            onRenameClick = { item -> itemToRename = item },
                            onDeleteClick = { item ->
                                scope.launch {
                                    val deletedPath = item.relativePath
                                    val ok = withContext(Dispatchers.IO) {
                                        try {
                                            moveToTrash(notesDir, deletedPath)
                                            true
                                        } catch (e: Exception) {
                                            Log.w(TAG, "moveToTrash failed, falling back to deleteItem", e)
                                            deleteItem(notesDir, deletedPath)
                                        }
                                    }
                                    if (!item.isDir) {
                                        AppSettings.removeNoteVisitedPath(context, deletedPath)
                                        if (selectedFilename == deletedPath) {
                                            selectedFilename = null
                                        }
                                        noteBackStack.removeAll { it.filename == deletedPath }
                                        if (activeRestoreEntry?.filename == deletedPath) {
                                            activeRestoreEntry = null
                                        }
                                    }
                                    if (ok) {
                                        Toast.makeText(context, "Moved to Trash", Toast.LENGTH_SHORT).show()
                                    }
                                    refreshNotesList()
                                }
                            }
                        )
                    }

                    HorizontalDivider(color = StitchBorder.copy(alpha = 0.4f))

                    // Bottom Footer Bar (Git Sync on Left, Trash Bin + Settings on Right)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val gitBranch = remember { SecureStorage.getGitBranch(context).ifEmpty { "main" } }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable {
                                        val token = SecureStorage.getGitToken(context)
                                        val remoteUrl = SecureStorage.getRemoteUrl(context)
                                        val branch = SecureStorage.getGitBranch(context)
                                        if (remoteUrl.isEmpty() || token.isEmpty()) {
                                            Toast.makeText(context, "Sync Failed: Remote URL or PAT token missing in Settings", Toast.LENGTH_LONG).show()
                                        } else {
                                             Toast.makeText(context, "Syncing with GitHub...", Toast.LENGTH_SHORT).show()
                                             StorageUtils.checkAndRequestStoragePermission(context)
                                             val targetRepoDir = StorageUtils.getSafeVaultPath(context, notesDir)
                                             scope.launch(Dispatchers.IO) {
                                                 try {
                                                     initGitRepository(targetRepoDir)
                                                     val msg = syncGitRepository(targetRepoDir, token, remoteUrl, branch)
                                                     withContext(Dispatchers.Main) {
                                                         Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                         refreshNotesList()
                                                     }
                                                 } catch (e: Exception) {
                                                     Log.e("GitSync", "Full Sync Error Details:", e)
                                                     val errMsg = e.message ?: e.toString()
                                                     withContext(Dispatchers.Main) {
                                                         syncErrorDialogText = errMsg
                                                     }
                                                 }
                                             }
                                        }
                                    }
                                    .padding(vertical = 4.dp, horizontal = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Sync Vault",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = gitBranch,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StitchTextMuted,
                                    fontSize = 12.sp
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { isTrashOpen = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Trash Bin",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            drawerState.close()
                                            activeBottomTab = StitchBottomTab.SETTINGS
                                            isSettingsOpen = true
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) {
            if (selectedFilename == null) {
                NoteListScreen(
                    notesDir = notesDir,
                    hasCustomVault = customVaultPathState.isNotBlank(),
                    pinnedIds = pinnedIds,
                    onNoteSelected = { relPath ->
                        noteBackStack.clear()
                        activeHeadingAnchor = null
                        activeRestoreEntry = null
                        selectedFilename = relPath
                    },
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenSearch = { isUnifiedSearchOpen = true },
                    onCreateNote = { isCreateNoteOpen = true },
                    onOpenVaultPicker = { safFolderLauncher.launch(null) },
                    onTogglePin = { noteId ->
                        val isPinned = AppSettings.togglePinnedNoteId(context, noteId)
                        pinnedIds = AppSettings.getPinnedNoteIds(context)
                        val msg = if (isPinned) "Note pinned to top" else "Note unpinned"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    },
                    onDuplicate = { filename ->
                        scope.launch {
                            val newName = withContext(Dispatchers.IO) {
                                duplicateNote(notesDir, filename)
                            }
                            if (newName.isNotEmpty()) {
                                AppSettings.recordNoteVisited(context, newName)
                            }
                            refreshNotesList()
                        }
                    },
                    onCopyPath = { filename ->
                        val fullPath = if (notesDir.endsWith("/")) "$notesDir$filename" else "$notesDir/$filename"
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("File Path", fullPath))
                        Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    onTriggerSync = {
                        val token = SecureStorage.getGitToken(context)
                        val remoteUrl = SecureStorage.getRemoteUrl(context)
                        val branch = SecureStorage.getGitBranch(context)
                        if (remoteUrl.isEmpty() || token.isEmpty()) {
                            Toast.makeText(context, "Sync Failed: Remote URL or PAT token missing in Settings", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Syncing with GitHub...", Toast.LENGTH_SHORT).show()
                            StorageUtils.checkAndRequestStoragePermission(context)
                            val targetRepoDir = StorageUtils.getSafeVaultPath(context, notesDir)
                            scope.launch(Dispatchers.IO) {
                                try {
                                    initGitRepository(targetRepoDir)
                                    val msg = syncGitRepository(targetRepoDir, token, remoteUrl, branch)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        refreshNotesList()
                                    }
                                } catch (e: Exception) {
                                    Log.e("GitSync", "Full Sync Error Details:", e)
                                    val errMsg = e.message ?: e.toString()
                                    withContext(Dispatchers.Main) {
                                        syncErrorDialogText = errMsg
                                    }
                                }
                            }
                        }
                    }
                )
            } else {
                NoteEditorScreen(
                    notesDir = notesDir,
                    filename = selectedFilename!!,
                    headingAnchor = activeHeadingAnchor,
                    restoreEntry = activeRestoreEntry,
                    onRestoreConsumed = { activeRestoreEntry = null },
                    fontSizeSp = editorFontSizeSp,
                    isDarkTheme = isDarkTheme,
                    appTheme = appTheme,
                    isFocusMode = isFocusMode,
                    isSourceMode = isSourceMode,
                    isPreviewMode = isGlobalPreviewMode,
                    onTogglePreviewMode = { isGlobalPreviewMode = !isGlobalPreviewMode },
                    onToggleFocusMode = { isFocusMode = !isFocusMode },
                    onToggleSourceMode = { isSourceMode = !isSourceMode },
                    onOpenCommandPalette = { isCommandPaletteOpen = true },
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onBack = {
                        if (noteBackStack.isNotEmpty()) {
                            val prevEntry = noteBackStack.removeAt(noteBackStack.size - 1)
                            activeHeadingAnchor = null
                            activeRestoreEntry = prevEntry
                            isGlobalPreviewMode = prevEntry.isPreviewMode
                            selectedFilename = prevEntry.filename
                        } else {
                            activeHeadingAnchor = null
                            activeRestoreEntry = null
                            selectedFilename = null
                            refreshNotesList()
                        }
                    },
                    onNavigateToWikilink = { rawLink, currentEntry ->
                        scope.launch {
                            val parts = rawLink.split("#", limit = 2)
                            val rawPath = parts[0].trim()
                            val headingTag = if (parts.size > 1) parts[1].trim().removePrefix("#").trim() else null

                            if (rawPath.isNotEmpty()) {
                                val targetFileName = if (rawPath.endsWith(".md", ignoreCase = true)) rawPath else "$rawPath.md"
                                val allNotes = withContext(Dispatchers.IO) {
                                    getCachedNoteHeaders(notesDir)
                                }

                                val existingNote = allNotes.find { note ->
                                    note.relativePath.equals(targetFileName, ignoreCase = true) ||
                                    note.relativePath.equals(rawPath, ignoreCase = true) ||
                                    note.title.equals(rawPath, ignoreCase = true)
                                }

                                if (existingNote != null) {
                                    selectedFilename?.let { current ->
                                        if (current != existingNote.relativePath || headingTag != null) {
                                            val entryToPush = currentEntry ?: NoteNavEntry(filename = current, isPreviewMode = isGlobalPreviewMode)
                                            noteBackStack.add(entryToPush)
                                        }
                                    }
                                    activeHeadingAnchor = headingTag
                                    activeRestoreEntry = null
                                    selectedFilename = existingNote.relativePath
                                } else {
                                    Toast.makeText(context, "Note not found: $rawPath", Toast.LENGTH_SHORT).show()
                                }
                            } else if (headingTag != null) {
                                selectedFilename?.let { current ->
                                    val entryToPush = currentEntry ?: NoteNavEntry(filename = current, isPreviewMode = isGlobalPreviewMode)
                                    noteBackStack.add(entryToPush)
                                }
                                activeHeadingAnchor = headingTag
                            }
                        }
                    },
                    undoRedoManager = undoRedoManager,
                    onRegisterUndoRedo = { undo, redo ->
                        activeUndoAction = undo
                        activeRedoAction = redo
                    }
                )
            }
        }
    }

    if (isUnifiedSearchOpen) {
        UnifiedSearchOverlay(
            notesDir = notesDir,
            onDismiss = {
                isUnifiedSearchOpen = false
                activeBottomTab = StitchBottomTab.EDITOR
            },
            onSelectNote = { filename ->
                selectedFilename = filename
            }
        )
    }

    if (isCommandPaletteOpen) {
        CommandPaletteSheet(
            onDismiss = { isCommandPaletteOpen = false },
            onToggleFocusMode = { isFocusMode = !isFocusMode },
            onExportPdf = {
                val currentFile = selectedFilename
                if (currentFile.isNullOrBlank()) {
                    Toast.makeText(context, "No note open to export", Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch(Dispatchers.IO) {
                        val fullPath = if (notesDir.endsWith("/")) "$notesDir$currentFile" else "$notesDir/$currentFile"
                        val content = try {
                            val f = File(fullPath)
                            if (f.exists()) f.readText() else ""
                        } catch (e: Exception) { "" }

                        withContext(Dispatchers.Main) {
                            exportNoteToPdf(context, currentFile, content)
                        }
                    }
                }
            },
            onShareNote = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, selectedFilename ?: "")
                }
                context.startActivity(Intent.createChooser(intent, "Share Note"))
            },
            onTriggerGitSync = {
                val token = SecureStorage.getGitToken(context)
                val remoteUrl = SecureStorage.getRemoteUrl(context)
                val branch = SecureStorage.getGitBranch(context)
                if (remoteUrl.isEmpty() || token.isEmpty()) {
                    Toast.makeText(context, "Sync Failed: Remote URL or PAT token missing in Settings", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Syncing with GitHub...", Toast.LENGTH_SHORT).show()
                    StorageUtils.checkAndRequestStoragePermission(context)
                    val targetRepoDir = StorageUtils.getSafeVaultPath(context, notesDir)
                    scope.launch(Dispatchers.IO) {
                        try {
                            initGitRepository(targetRepoDir)
                            val msg = syncGitRepository(targetRepoDir, token, remoteUrl, branch)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                refreshNotesList()
                            }
                        } catch (e: Exception) {
                            Log.e("GitSync", "Full Sync Error Details:", e)
                            val errMsg = e.message ?: e.toString()
                            withContext(Dispatchers.Main) {
                                syncErrorDialogText = errMsg
                            }
                        }
                    }
                }
            },
            onOpenSettings = {
                isSettingsOpen = true
            },
            isNoteOpen = selectedFilename != null,
            canUndo = undoRedoManager.canUndo(),
            canRedo = undoRedoManager.canRedo(),
            onUndo = { activeUndoAction?.invoke() },
            onRedo = { activeRedoAction?.invoke() }
        )
    }

    if (isTrashOpen) {
        TrashBinSheet(
            notesDir = notesDir,
            onDismiss = { isTrashOpen = false }
        )
    }

    if (isCreateNoteOpen) {
        CreateNoteDialog(
            currentDir = currentRelativeDir,
            defaultTemplate = AppSettings.getNoteTemplate(context),
            onDismiss = { isCreateNoteOpen = false },
            onCreate = { titleOrTemplate ->
                if (notesDir.isBlank() || !File(notesDir).exists()) {
                    Toast.makeText(context, "No vault selected. Open a local vault first.", Toast.LENGTH_SHORT).show()
                    isCreateNoteOpen = false
                    return@CreateNoteDialog
                }
                scope.launch {
                    val fullPath = if (currentRelativeDir.isEmpty()) titleOrTemplate else "$currentRelativeDir/$titleOrTemplate"
                    val filename = withContext(Dispatchers.IO) {
                        createNote(notesDir, fullPath)
                    }
                    isCreateNoteOpen = false
                    if (filename.isNotEmpty()) {
                        AppSettings.recordNoteVisited(context, filename)
                        selectedFilename = filename
                        refreshNotesList()
                    }
                }
            }
        )
    }

    if (isCreateFolderOpen) {
        CreateFolderDialog(
            currentDir = currentRelativeDir,
            onDismiss = { isCreateFolderOpen = false },
            onCreate = { folderName ->
                if (notesDir.isBlank() || !File(notesDir).exists()) {
                    Toast.makeText(context, "No vault selected. Open a local vault first.", Toast.LENGTH_SHORT).show()
                    isCreateFolderOpen = false
                    return@CreateFolderDialog
                }
                scope.launch {
                    withContext(Dispatchers.IO) {
                        createFolder(notesDir, currentRelativeDir, folderName)
                    }
                    isCreateFolderOpen = false
                    refreshNotesList()
                }
            }
        )
    }

    if (itemToRename != null) {
        val targetItem = itemToRename!!
        RenameItemDialog(
            item = targetItem,
            onDismiss = { itemToRename = null },
            onRename = { newName ->
                scope.launch {
                    val oldPath = targetItem.relativePath
                    val parentDir = if (oldPath.contains("/")) oldPath.substringBeforeLast("/") + "/" else ""
                    val cleanNewName = if (targetItem.isDir) newName else if (newName.endsWith(".md")) newName else "$newName.md"
                    val newPath = "$parentDir$cleanNewName"

                    val success = withContext(Dispatchers.IO) {
                        renameItem(notesDir, oldPath, newName)
                    }

                    if (success) {
                        if (targetItem.isDir) {
                            AppSettings.updateFolderCategoryPath(context, oldPath, newPath)
                        } else {
                            if (selectedFilename == oldPath) {
                                selectedFilename = newPath
                            }
                            val renameIdx = noteBackStack.indexOfFirst { it.filename == oldPath }
                            if (renameIdx != -1) {
                                noteBackStack[renameIdx] = noteBackStack[renameIdx].copy(filename = newPath)
                            }
                            if (activeRestoreEntry?.filename == oldPath) {
                                activeRestoreEntry = activeRestoreEntry?.copy(filename = newPath)
                            }
                            AppSettings.updateNoteVisitedPath(context, oldPath, newPath)
                        }
                    }

                    itemToRename = null
                    refreshNotesList()
                }
            }
        )
    }

    if (syncErrorDialogText != null) {
        AlertDialog(
            onDismissRequest = { syncErrorDialogText = null },
            title = {
                Text(
                    text = "Git Sync Error Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        Text(
                            text = syncErrorDialogText!!,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { syncErrorDialogText = null }) {
                    Text("Close", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Git Error", syncErrorDialogText))
                    Toast.makeText(context, "Copied error to clipboard", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy Error", color = StitchAccentCoral)
                }
            },
            containerColor = StitchCardBg
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    notesDir: String,
    hasCustomVault: Boolean = false,
    pinnedIds: Set<String>,
    activeBottomTab: StitchBottomTab = StitchBottomTab.EDITOR,
    onSelectBottomTab: ((StitchBottomTab) -> Unit)? = null,
    onNoteSelected: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onCreateNote: () -> Unit,
    onOpenVaultPicker: () -> Unit,
    onTogglePin: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onCopyPath: (String) -> Unit,
    onTriggerSync: () -> Unit
) {
    var notes by remember { mutableStateOf<List<CachedNoteHeader>>(emptyList()) }
    var isNotesLoaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val refreshNotes = {
        scope.launch {
            val fetched = withContext(Dispatchers.IO) {
                getCachedNoteHeaders(notesDir)
            }
            val recentlyVisited = AppSettings.getRecentlyVisitedNotes(context)
            val visited = recentlyVisited.mapNotNull { relPath ->
                fetched.find { it.relativePath == relPath }
            }
            notes = visited.take(10)
            isNotesLoaded = true
        }
    }

    LaunchedEffect(notesDir, pinnedIds) {
        val fetched = withContext(Dispatchers.IO) {
            getCachedNoteHeaders(notesDir)
        }
        val recentlyVisited = AppSettings.getRecentlyVisitedNotes(context)
        val visited = recentlyVisited.mapNotNull { relPath ->
            fetched.find { it.relativePath == relPath }
        }
        notes = visited.take(10)
        isNotesLoaded = true
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.slashnote.app.R.drawable.slashnote_logo),
                            contentDescription = "Logo",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            alpha = 0.8f
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SlashNote", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenVaultPicker) {
                        Icon(Icons.Outlined.SnippetFolder, contentDescription = "Open Vault", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            AnimatedVisibility(visible = isNotesLoaded && hasCustomVault) {
                FloatingActionButton(
                    onClick = onCreateNote,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Note")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .imePadding()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "RECENT NOTES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            if (!hasCustomVault) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 2.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Icon Visual Anchor
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(16.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Headline
                                Text(
                                    text = "No Vault Selected",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Subtitle
                                Text(
                                    text = "Choose an existing directory of Markdown files or create a local workspace to get started.",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.widthIn(max = 280.dp)
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // Primary CTA — Open Local Vault
                                Button(
                                    onClick = onOpenVaultPicker,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open Local Vault", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else if (isNotesLoaded && notes.isEmpty()) {
                item {
                    Text(
                        text = "No recent notes viewed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    )
                }
            } else {
                items(notes, key = { it.relativePath }, contentType = { "note" }) { note ->
                    val isPinned = pinnedIds.contains(note.relativePath)
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        try {
                                            moveToTrash(notesDir, note.relativePath)
                                        } catch (e: Exception) {
                                            deleteNote(notesDir, note.relativePath)
                                        }
                                    }
                                    AppSettings.removeNoteVisitedPath(context, note.relativePath)
                                    Toast.makeText(context, "Moved to Trash", Toast.LENGTH_SHORT).show()
                                    refreshNotes()
                                }
                                true
                            } else {
                                false
                            }
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(10.dp))
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Note",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    ) {
                        NoteCardItem(
                            note = note,
                            isPinned = isPinned,
                            onClick = { onNoteSelected(note.relativePath) },
                            onTogglePin = { onTogglePin(note.relativePath) },
                            onDuplicate = { onDuplicate(note.relativePath) },
                            onCopyPath = { onCopyPath(note.relativePath) },
                            onDelete = {
                                AppSettings.removeNoteVisitedPath(context, note.relativePath)
                                Toast.makeText(context, "Cleared from recents", Toast.LENGTH_SHORT).show()
                                refreshNotes()
                            }
                        )
                    }
                }
            }
        }
    }
}

private val dateFormatSdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())

@Composable
fun NoteCardItem(
    note: CachedNoteHeader,
    isPinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDuplicate: () -> Unit,
    onCopyPath: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    // Rendered synchronously from the cached index timestamp — no disk I/O per card.
    val formattedDate = remember(note.lastModifiedUnix) {
        dateFormatSdf.format(java.util.Date(note.lastModifiedUnix * 1000L))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Accent Bar / Pill
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(
                        if (isPinned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)
                    )
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Rounded Icon Container Box
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Title & Subtitle Row
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isPinned) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = "Pinned",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = note.title,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "${note.relativePath}  •  $formattedDate",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = StitchTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (isPinned) "Unpin Note" else "Pin Note") },
                            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onTogglePin()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate Note") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onDuplicate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy Filepath") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onCopyPath()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear", color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}


fun getClipboardImageUri(context: Context): Uri? {
    return try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount > 0) {
            val item = clip.getItemAt(0)
            val uri = item.uri
            if (uri != null) {
                val desc = clip.description
                if (desc != null) {
                    if (desc.hasMimeType("image/*")) {
                        return uri
                    }
                    for (i in 0 until desc.mimeTypeCount) {
                        if (desc.getMimeType(i).startsWith("image/")) {
                            return uri
                        }
                    }
                }
                val type = try { context.contentResolver.getType(uri) } catch (_: Exception) { null }
                if (type != null && type.startsWith("image/")) {
                    return uri
                }
                val path = uri.path?.lowercase() ?: ""
                if (path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".webp") || path.endsWith(".gif") || path.endsWith(".svg")) {
                    return uri
                }
            }
        }
        null
    } catch (_: Exception) {
        null
    }
}

fun computeRelativeAssetPath(noteFilename: String, assetFilename: String): String {
    val normalized = noteFilename.replace('\\', '/').trimStart('/')
    val lastSlash = normalized.lastIndexOf('/')
    val depth = if (lastSlash == -1) {
        0
    } else {
        val folderPart = normalized.substring(0, lastSlash)
        folderPart.split('/').count { it.isNotEmpty() && it != "." }
    }
    val prefix = if (depth > 0) "../".repeat(depth) else ""
    return "${prefix}assets/$assetFilename"
}

fun saveImageToAssets(context: Context, notesDir: String, uri: Uri, noteFilename: String = ""): String? {
    return try {
        val rootDir = File(notesDir)
        if (!rootDir.exists() || !rootDir.isDirectory) {
            throw IOException("Notes vault directory does not exist: $notesDir")
        }

        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: "image/png"
        val ext = when {
            mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
            mimeType.contains("png") -> "png"
            mimeType.contains("webp") -> "webp"
            mimeType.contains("gif") -> "gif"
            mimeType.contains("svg") -> "svg"
            else -> "png"
        }

        // Auto-create assets directory if it does not exist, or reuse existing
        val assetsDir = File(rootDir, "assets")
        if (!assetsDir.exists()) {
            val created = assetsDir.mkdirs()
            if (!created && !assetsDir.exists()) {
                throw IOException("Failed to create assets directory in vault")
            }
        }

        // Generate unique filename with timestamp and counter to avoid collisions
        val timeFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
        val baseTime = timeFormat.format(Date())
        var targetName = "image-$baseTime.$ext"
        var destFile = File(assetsDir, targetName)
        var counter = 1
        while (destFile.exists()) {
            targetName = "image-$baseTime-$counter.$ext"
            destFile = File(assetsDir, targetName)
            counter++
        }

        // Write image stream
        resolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
                output.flush()
            }
        } ?: throw IOException("Unable to read image from clipboard")

        // Confirm file was written and is not empty
        if (!destFile.exists() || destFile.length() <= 0L) {
            destFile.delete()
            throw IOException("Image file was not written or is empty")
        }

        computeRelativeAssetPath(noteFilename, destFile.name)
    } catch (e: Exception) {
        Log.e("SlashNote", "Failed to save image to assets", e)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "Failed to save image: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
        }
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NoteEditorScreen(
    notesDir: String,
    filename: String,
    headingAnchor: String? = null,
    restoreEntry: NoteNavEntry? = null,
    onRestoreConsumed: () -> Unit = {},
    fontSizeSp: Int = 16,
    isDarkTheme: Boolean,
    appTheme: AppTheme = AppTheme.SLASHNOTE,
    isFocusMode: Boolean,
    isSourceMode: Boolean,
    isPreviewMode: Boolean,
    onTogglePreviewMode: () -> Unit,
    onToggleFocusMode: () -> Unit,
    onToggleSourceMode: () -> Unit,
    onOpenCommandPalette: () -> Unit,
    onOpenDrawer: () -> Unit,
    onBack: () -> Unit,
    onNavigateToWikilink: (targetTitle: String, currentEntry: NoteNavEntry?) -> Unit,
    undoRedoManager: UndoRedoManager,
    onRegisterUndoRedo: (undo: (() -> Unit)?, redo: (() -> Unit)?) -> Unit
) {
    val scope = rememberCoroutineScope()
    var textFieldValue by remember(filename) { mutableStateOf(TextFieldValue(text = "")) }
    val noteText = textFieldValue.text
    var initialLoadedContent by remember(filename) { mutableStateOf<String?>(null) }
    var isDirty by remember(filename) { mutableStateOf(false) }
    var saveStatus by remember(filename) { mutableStateOf("Saved just now") }

    val performUndo: () -> Unit = {
        val prev = undoRedoManager.undo(textFieldValue)
        if (prev != null) {
            textFieldValue = prev
        }
    }

    val performRedo: () -> Unit = {
        val next = undoRedoManager.redo(textFieldValue)
        if (next != null) {
            textFieldValue = next
        }
    }

    // Keep the latest undo/redo callbacks in a ref so the effect below keys only
    // on canUndo()/canRedo() (not the full TextFieldValue, which changes per keystroke).
    val performUndoState by rememberUpdatedState(performUndo)
    val performRedoState by rememberUpdatedState(performRedo)

    LaunchedEffect(undoRedoManager.canUndo(), undoRedoManager.canRedo()) {
        onRegisterUndoRedo(
            if (undoRedoManager.canUndo()) ({ performUndoState() }) else null,
            if (undoRedoManager.canRedo()) ({ performRedoState() }) else null
        )
    }

    var isSlashMenuVisible by remember { mutableStateOf(false) }
    var isWikilinkMenuVisible by remember { mutableStateOf(false) }
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentMatchIndex by remember { mutableStateOf(0) }

    var allNotesList by remember { mutableStateOf<List<NoteHeader>>(emptyList()) }

    // Shared span cache so the visual transformation only does hash lookups.
    val spanCache = remember { MarkdownSpanCache() }

    // Debounced background parse: typing never blocks the UI thread.
    LaunchedEffect(noteText, filename) {
        if (noteText.isEmpty()) return@LaunchedEffect
        delay(120)
        val text = noteText
        val hash = markdownHash(text)
        if (spanCache.get(hash) != null) return@LaunchedEffect
        val spans = withContext(Dispatchers.IO) {
            try {
                parseMarkdownTokens(text)
            } catch (e: Exception) {
                emptyList()
            }
        }
        spanCache.put(hash, spans)
    }

    val currentText by rememberUpdatedState(noteText)

    val matches = remember(noteText, searchQuery, isSearchOpen) {
        if (!isSearchOpen || searchQuery.trim().isEmpty()) emptyList() else {
            val list = mutableListOf<Int>()
            var idx = noteText.indexOf(searchQuery, ignoreCase = true)
            while (idx >= 0) {
                list.add(idx)
                idx = noteText.indexOf(searchQuery, idx + searchQuery.length, ignoreCase = true)
            }
            list
        }
    }

    val currentOnBack by rememberUpdatedState(onBack)
    val handleBack: () -> Unit = {
        scope.launch {
            if (isDirty) {
                saveStatus = "Saving..."
                withContext(Dispatchers.IO) {
                    saveNote(notesDir, filename, currentText)
                }
                isDirty = false
                saveStatus = "Saved just now"
            }
            currentOnBack()
        }
    }

    BackHandler {
        handleBack()
    }

    val context = LocalContext.current

    val defaultClipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val customClipboardManager = remember(defaultClipboardManager, context, notesDir, filename) {
        object : androidx.compose.ui.platform.ClipboardManager {
            override fun getText(): androidx.compose.ui.text.AnnotatedString? {
                val clipUri = try {
                    getClipboardImageUri(context)
                } catch (_: Exception) {
                    null
                }
                if (clipUri != null) {
                    try {
                        val relPath = saveImageToAssets(context, notesDir, clipUri, filename)
                        if (relPath != null) {
                            return androidx.compose.ui.text.AnnotatedString("![image]($relPath)")
                        }
                    } catch (e: Exception) {
                        Log.e("SlashNote", "Failed to paste image from clipboard", e)
                    }
                }
                return defaultClipboardManager.getText()
            }

            override fun setText(annotatedString: androidx.compose.ui.text.AnnotatedString) {
                defaultClipboardManager.setText(annotatedString)
            }

            override fun hasText(): Boolean {
                val hasImage = try {
                    getClipboardImageUri(context) != null
                } catch (_: Exception) {
                    false
                }
                return hasImage || defaultClipboardManager.hasText()
            }
        }
    }

    val editorScrollState = key(filename) { rememberScrollState() }
    val previewLazyListState = key(filename) { rememberLazyListState() }
    var textLayoutResult by remember(filename) { mutableStateOf<TextLayoutResult?>(null) }
    var savedScrollRatio by remember(filename) { mutableFloatStateOf(0f) }

    val handleTogglePreview = {
        val blocks = parseMarkdownBlocks(noteText)
        if (!isPreviewMode) {
            val topCharOffset = if (textLayoutResult != null && editorScrollState.value >= 0) {
                try {
                    val line = textLayoutResult!!.getLineForVerticalPosition(editorScrollState.value.toFloat())
                    textLayoutResult!!.getLineStart(line)
                } catch (_: Exception) {
                    textFieldValue.selection.start
                }
            } else {
                textFieldValue.selection.start
            }

            val targetBlockIdx = blocks.indexOfFirst { block ->
                topCharOffset in block.startOffset..block.endOffset
            }.coerceAtLeast(0)

            onTogglePreviewMode()
            scope.launch {
                try {
                    previewLazyListState.scrollToItem(targetBlockIdx)
                } catch (_: Exception) {}
            }
            spanCache.clear()
        } else {
            val firstVisibleIdx = previewLazyListState.firstVisibleItemIndex
            val activeBlock = blocks.getOrNull(firstVisibleIdx)
            val targetOffset = (activeBlock?.startOffset ?: 0).coerceIn(0, noteText.length)

            textFieldValue = textFieldValue.copy(selection = TextRange(targetOffset))

            spanCache.clear()
            onTogglePreviewMode()

            scope.launch {
                if (textLayoutResult != null) {
                    try {
                        val line = textLayoutResult!!.getLineForOffset(targetOffset)
                        val targetY = textLayoutResult!!.getLineTop(line).toInt()
                        editorScrollState.scrollTo(targetY)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    // Lazy load file body strictly on demand when editor opens.
    // IMPORTANT: Never call previewLazyListState.scrollToItem() before readNote(),
    // because when in Edit mode, MarkdownPreview's LazyColumn is not in the composition
    // tree and scrollToItem() suspends indefinitely waiting for layout!
    LaunchedEffect(notesDir, filename) {
        AppSettings.recordNoteVisited(context, filename)
        val content = withContext(Dispatchers.IO) {
            readNote(notesDir, filename)
        }
        val initialCursor = if (restoreEntry != null && restoreEntry.filename == filename) {
            restoreEntry.editorCursor.coerceIn(0, content.length)
        } else {
            0
        }
        initialLoadedContent = content
        textFieldValue = TextFieldValue(text = content, selection = TextRange(initialCursor))
        isDirty = false
        saveStatus = "Saved just now"

        val notes = withContext(Dispatchers.IO) {
            listNotes(notesDir)
        }
        allNotesList = notes
    }

    // Scroll restoration for Editor Mode
    LaunchedEffect(restoreEntry, textLayoutResult, initialLoadedContent, isPreviewMode) {
        if (restoreEntry != null && restoreEntry.filename == filename && initialLoadedContent != null && !isPreviewMode) {
            val targetY = if (editorScrollState.maxValue > 0) {
                restoreEntry.editorScrollY.coerceIn(0, editorScrollState.maxValue)
            } else {
                restoreEntry.editorScrollY
            }
            editorScrollState.scrollTo(targetY)
            onRestoreConsumed()
        }
    }

    // Auto-save debounced 400ms on user typing pause
    LaunchedEffect(noteText) {
        if (initialLoadedContent == null) return@LaunchedEffect

        if (noteText == initialLoadedContent && !isDirty) return@LaunchedEffect

        isDirty = true
        saveStatus = "Editing..."
        delay(400)
        saveStatus = "Saving..."
        val success = withContext(Dispatchers.IO) {
            saveNote(notesDir, filename, noteText)
        }
        if (success) {
            isDirty = false
            saveStatus = "Saved just now"
        } else {
            saveStatus = "Error saving"
        }
    }

    DisposableEffect(filename) {
        onDispose {
            if (isDirty && initialLoadedContent != null) {
                saveNote(notesDir, filename, currentText)
            }
        }
    }

    // Auto-scroll to active search match position in viewport
    LaunchedEffect(currentMatchIndex, matches, isSearchOpen) {
        if (isSearchOpen && matches.isNotEmpty() && currentMatchIndex in matches.indices) {
            val matchOffset = matches[currentMatchIndex]
            if (textLayoutResult != null) {
                try {
                    val line = textLayoutResult!!.getLineForOffset(matchOffset)
                    val targetY = textLayoutResult!!.getLineTop(line).toInt()
                    val targetScroll = (targetY - 180).coerceAtLeast(0)
                    editorScrollState.animateScrollTo(targetScroll)
                } catch (_: Exception) {}
            }
        }
    }

    val visualTransformation = remember(isDarkTheme, appTheme, isSearchOpen, searchQuery, matches, currentMatchIndex) {
        MarkdownVisualTransformation(
            isDarkTheme = isDarkTheme,
            appTheme = appTheme,
            cache = spanCache,
            searchQuery = if (isSearchOpen) searchQuery else "",
            searchMatches = if (isSearchOpen) matches else emptyList(),
            currentMatchIndex = currentMatchIndex
        )
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(visible = !isFocusMode) {
                Column {
                    TopAppBar(
                        title = {
                            val parentDir = if (filename.contains("/")) filename.substringBeforeLast("/") + "/" else ""
                            val noteName = if (filename.contains("/")) filename.substringAfterLast("/") else filename
                            val cleanTitle = noteName.removeSuffix(".md")

                            Column(
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (parentDir.isNotEmpty()) {
                                    Text(
                                        text = parentDir,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.sp,
                                        color = StitchTextMuted,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = cleanTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = true)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isPreviewMode) "Preview" else saveStatus,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        softWrap = false
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = handleBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        },
                        actions = {
                            IconButton(onClick = onOpenDrawer) {
                                Icon(Icons.Default.Menu, contentDescription = "Open Drawer", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(onClick = { isSearchOpen = !isSearchOpen }) {
                                Icon(Icons.Default.Search, contentDescription = "Find in note", tint = MaterialTheme.colorScheme.onSurface)
                            }

                            // Single Edit / Preview Toggle Button
                            IconButton(onClick = { handleTogglePreview() }) {
                                Icon(
                                    imageVector = if (isPreviewMode) Icons.Default.Edit else Icons.Outlined.Visibility,
                                    contentDescription = if (isPreviewMode) "Switch to Edit Mode" else "Switch to Preview Mode",
                                    tint = if (isPreviewMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            IconButton(onClick = onOpenCommandPalette) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Command Palette", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .imePadding()
                .padding(horizontal = 5.dp, vertical = 4.dp)
        ) {
            AnimatedContent(
                targetState = isPreviewMode,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(180, easing = LinearEasing)) +
                     scaleIn(initialScale = 0.98f, animationSpec = tween(180)))
                        .togetherWith(
                            fadeOut(animationSpec = tween(140, easing = LinearEasing))
                        )
                },
                label = "EditPreviewCrossfade",
                modifier = Modifier.fillMaxSize()
            ) { isPreview ->
                if (isPreview) {
                    MarkdownPreview(
                        content = noteText,
                        headingAnchor = headingAnchor,
                        restoreIndex = if (restoreEntry != null && restoreEntry.filename == filename) restoreEntry.previewIndex else null,
                        restoreOffset = if (restoreEntry != null && restoreEntry.filename == filename) restoreEntry.previewOffset else null,
                        onRestoreConsumed = onRestoreConsumed,
                        listState = previewLazyListState,
                        baseFontSizeSp = fontSizeSp,
                        notesDir = notesDir,
                        noteFilename = filename,
                        onWikilinkClick = { title ->
                            val currentEntry = NoteNavEntry(
                                filename = filename,
                                isPreviewMode = isPreviewMode,
                                previewIndex = previewLazyListState.firstVisibleItemIndex,
                                previewOffset = previewLazyListState.firstVisibleItemScrollOffset,
                                editorScrollY = editorScrollState.value,
                                editorCursor = textFieldValue.selection.start
                            )
                            onNavigateToWikilink(title, currentEntry)
                        },
                        onToggleTaskItem = { item ->
                            val text = textFieldValue.text
                            val start = item.startOffset.coerceIn(0, text.length)
                            val end = item.endOffset.coerceIn(0, text.length)
                            if (start < end) {
                                val line = text.substring(start, end)
                                val updatedLine = if (Regex("\\[[xX]\\]").containsMatchIn(line)) {
                                    line.replaceFirst(Regex("\\[[xX]\\]"), "[ ]")
                                } else if (Regex("\\[[ ]?\\]").containsMatchIn(line)) {
                                    line.replaceFirst(Regex("\\[[ ]?\\]"), "[x]")
                                } else {
                                    line
                                }
                                if (updatedLine != line) {
                                    val newText = text.substring(0, start) + updatedLine + text.substring(end)
                                    textFieldValue = TextFieldValue(text = newText, selection = textFieldValue.selection)
                                    saveNote(notesDir, filename, newText)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CompositionLocalProvider(androidx.compose.ui.platform.LocalClipboardManager provides customClipboardManager) {
                        MarkdownEditor(
                            value = textFieldValue,
                            fontSizeSp = fontSizeSp,
                            onValueChange = { newTfv ->
                                val oldTfv = textFieldValue
                                val oldText = oldTfv.text
                                val newText = newTfv.text
                                val cursor = newTfv.selection.start

                                if (newText != oldText) {
                                    undoRedoManager.registerChange(oldTfv, newTfv)
                                }

                                // 1. Check for Markdown Task-List Syntax Auto-Conversion when Space is typed
                                // Supports typing "- []", "- [ ]", "- [x]", "- [X]" (and with *, +) at line start followed by space
                                if (newText.length == oldText.length + 1 && cursor > 0 && newText[cursor - 1] == ' ') {
                                    val prevNewlineIdx = newText.lastIndexOf('\n', cursor - 2)
                                    val lineStart = if (prevNewlineIdx == -1) 0 else prevNewlineIdx + 1
                                    val lineBeforeCursor = newText.substring(lineStart, cursor)

                                    val taskPattern = Regex("^(\\s*[-*+])\\s*\\[([ xX]?)\\]\\s$")
                                    val match = taskPattern.find(lineBeforeCursor)
                                    if (match != null) {
                                        val listMarker = match.groupValues[1]
                                        val checkChar = match.groupValues[2]
                                        val normalizedMarker = when (checkChar) {
                                            "x" -> "$listMarker [x] "
                                            "X" -> "$listMarker [X] "
                                            else -> "$listMarker [ ] "
                                        }
                                        val beforeLine = newText.substring(0, lineStart)
                                        val afterCursor = newText.substring(cursor)
                                        val updatedText = "$beforeLine$normalizedMarker$afterCursor"
                                        val updatedCursor = lineStart + normalizedMarker.length
                                        textFieldValue = TextFieldValue(text = updatedText, selection = TextRange(updatedCursor))
                                        return@MarkdownEditor
                                    }
                                }

                                // 2. Check for Obsidian-Style Auto-List Continuation & Exit when Enter (\n) is pressed
                                if (newText.length == oldText.length + 1 && cursor > 0 && newText[cursor - 1] == '\n') {
                                    val prevNewlineIdx = newText.lastIndexOf('\n', cursor - 2)
                                    val lineStart = if (prevNewlineIdx == -1) 0 else prevNewlineIdx + 1
                                    val lineText = newText.substring(lineStart, cursor - 1)

                                    // Match Task List: e.g. "- [ ] ", "- [x] ", "- [] "
                                    val taskListMatch = Regex("^(\\s*[-*+]\\s+\\[[ xX]?\\]\\s*)(.*)$").find(lineText)
                                if (taskListMatch != null) {
                                    val prefix = taskListMatch.groupValues[1]
                                    val content = taskListMatch.groupValues[2]
                                    if (content.isBlank()) {
                                        val beforeLine = newText.substring(0, lineStart)
                                        val afterNewline = newText.substring(cursor)
                                        val updatedText = "$beforeLine$afterNewline"
                                        textFieldValue = TextFieldValue(text = updatedText, selection = TextRange(lineStart))
                                        return@MarkdownEditor
                                    } else {
                                        val indent = prefix.takeWhile { it.isWhitespace() }
                                        val continuation = "$indent- [ ] "
                                        val beforeCursor = newText.substring(0, cursor)
                                        val afterCursor = newText.substring(cursor)
                                        val updatedText = "$beforeCursor$continuation$afterCursor"
                                        val updatedCursor = cursor + continuation.length
                                        textFieldValue = TextFieldValue(text = updatedText, selection = TextRange(updatedCursor))
                                        return@MarkdownEditor
                                    }
                                }

                                // Match Ordered List: e.g. "1. ", "2) "
                                val orderedListMatch = Regex("^(\\s*)(\\d+)([.)])\\s+(.*)$").find(lineText)
                                if (orderedListMatch != null) {
                                    val indent = orderedListMatch.groupValues[1]
                                    val num = orderedListMatch.groupValues[2].toIntOrNull() ?: 1
                                    val delimiter = orderedListMatch.groupValues[3]
                                    val content = orderedListMatch.groupValues[4]
                                    if (content.isBlank()) {
                                        val beforeLine = newText.substring(0, lineStart)
                                        val afterNewline = newText.substring(cursor)
                                        val updatedText = "$beforeLine$afterNewline"
                                        textFieldValue = TextFieldValue(text = updatedText, selection = TextRange(lineStart))
                                        return@MarkdownEditor
                                    } else {
                                        val continuation = "$indent${num + 1}$delimiter "
                                        val beforeCursor = newText.substring(0, cursor)
                                        val afterCursor = newText.substring(cursor)
                                        val updatedText = "$beforeCursor$continuation$afterCursor"
                                        val updatedCursor = cursor + continuation.length
                                        textFieldValue = TextFieldValue(text = updatedText, selection = TextRange(updatedCursor))
                                        return@MarkdownEditor
                                    }
                                }

                                // Match Unordered List: e.g. "- ", "* ", "+ "
                                val unorderedListMatch = Regex("^(\\s*[-*+])\\s+(.*)$").find(lineText)
                                if (unorderedListMatch != null) {
                                    val prefix = unorderedListMatch.groupValues[1]
                                    val content = unorderedListMatch.groupValues[2]
                                    if (content.isBlank()) {
                                        val beforeLine = newText.substring(0, lineStart)
                                        val afterNewline = newText.substring(cursor)
                                        val updatedText = "$beforeLine$afterNewline"
                                        textFieldValue = TextFieldValue(text = updatedText, selection = TextRange(lineStart))
                                        return@MarkdownEditor
                                    } else {
                                        val continuation = "$prefix "
                                        val beforeCursor = newText.substring(0, cursor)
                                        val afterCursor = newText.substring(cursor)
                                        val updatedText = "$beforeCursor$continuation$afterCursor"
                                        val updatedCursor = cursor + continuation.length
                                        textFieldValue = TextFieldValue(text = updatedText, selection = TextRange(updatedCursor))
                                        return@MarkdownEditor
                                    }
                                }
                            }

                            // 2. Strict Inline Auto-Trigger for Slash Commands ('/') and Wikilinks ('[[')
                            if (newText.length == oldText.length + 1 && cursor > 0) {
                                val insertedChar = newText[cursor - 1]
                                if (insertedChar == '/') {
                                    val prevChar = newText.getOrNull(cursor - 2)
                                    if (prevChar == null || prevChar == ' ' || prevChar == '\n' || prevChar == '\t') {
                                        isSlashMenuVisible = true
                                    }
                                } else if (insertedChar == '[') {
                                    if (cursor >= 2 && newText.substring(cursor - 2, cursor) == "[[") {
                                        isWikilinkMenuVisible = true
                                    }
                                }
                            }

                            textFieldValue = newTfv
                        },
                        scrollState = editorScrollState,
                        visualTransformation = visualTransformation,
                        onTextLayout = { textLayoutResult = it },
                        modifier = Modifier.fillMaxSize()
                    )
                    }
                }
            }

            // Floating formatting toolbar docked above keyboard with imePadding
            if (!isPreviewMode && !isSearchOpen) {
                FormattingToolbar(
                    onTriggerSlashMenu = { isSlashMenuVisible = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 16.dp, end = 8.dp)
                )
            }

            // Floating Lock Exit Button for Focus Mode (TopEnd)
            if (isFocusMode) {
                IconButton(
                    onClick = onToggleFocusMode,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 8.dp, end = 16.dp)
                        .size(38.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(50)
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Exit Focus Mode",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Compact Floating In-Note Search Bar attached above keyboard
            if (isSearchOpen) {
                InNoteSearchBar(
                    query = searchQuery,
                    matchCount = matches.size,
                    currentMatchIndex = currentMatchIndex,
                    onQueryChange = {
                        searchQuery = it
                        currentMatchIndex = 0
                    },
                    onNextMatch = {
                        if (matches.isNotEmpty()) {
                            currentMatchIndex = (currentMatchIndex + 1) % matches.size
                        }
                    },
                    onPrevMatch = {
                        if (matches.isNotEmpty()) {
                            currentMatchIndex = if (currentMatchIndex - 1 < 0) matches.size - 1 else currentMatchIndex - 1
                        }
                    },
                    onClose = {
                        isSearchOpen = false
                        searchQuery = ""
                    }
                )
            }

            // Slash Command Menu Popup
            if (isSlashMenuVisible && !isPreviewMode) {
                SlashCommandPopup(
                    onDismiss = { isSlashMenuVisible = false },
                    onSelectOption = { prefix, suffix ->
                        isSlashMenuVisible = false
                        val activeDocText = textFieldValue.text
                        val selection = textFieldValue.selection
                        val cursor = selection.start.coerceIn(0, activeDocText.length)

                        // Check if '/' was typed immediately before cursor and strip it
                        val removeSlash = cursor > 0 && activeDocText.getOrNull(cursor - 1) == '/'
                        val insertPos = if (removeSlash) cursor - 1 else cursor

                        val before = activeDocText.substring(0, insertPos)
                        val after = activeDocText.substring(cursor)
                        val newText = "$before$prefix$suffix$after"
                        val newCursor = insertPos + prefix.length

                        textFieldValue = TextFieldValue(
                            text = newText,
                            selection = TextRange(newCursor)
                        )
                    }
                )
            }

            // Wikilink Autocomplete Popup
            if (isWikilinkMenuVisible && !isPreviewMode) {
                WikilinkAutocompletePopup(
                    notes = allNotesList,
                    onDismiss = { isWikilinkMenuVisible = false },
                    onSelectNote = { title ->
                        isWikilinkMenuVisible = false
                        val activeDocText = textFieldValue.text
                        val selection = textFieldValue.selection
                        val cursor = selection.start.coerceIn(0, activeDocText.length)

                        val removeBracket = cursor >= 2 && activeDocText.substring(cursor - 2, cursor) == "[["
                        val insertPos = if (removeBracket) cursor - 2 else cursor

                        val before = activeDocText.substring(0, insertPos)
                        val after = activeDocText.substring(cursor)
                        val linkText = "[[$title]]"
                        val newText = "$before$linkText$after"
                        val newCursor = insertPos + linkText.length

                        textFieldValue = TextFieldValue(
                            text = newText,
                            selection = TextRange(newCursor)
                        )
                    }
                )
            }
        }
    }
}

fun Modifier.simpleVerticalScrollbar(
    scrollState: ScrollState,
    color: Color,
    width: Dp = 3.dp
): Modifier = drawWithContent {
    drawContent()
    val showScrollbar = scrollState.maxValue > 0
    if (showScrollbar) {
        val viewPortLength = size.height
        val totalLength = size.height + scrollState.maxValue
        val thumbHeight = (viewPortLength / totalLength * viewPortLength).coerceAtLeast(24.dp.toPx())
        val thumbOffset = (scrollState.value.toFloat() / scrollState.maxValue) * (viewPortLength - thumbHeight)

        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - width.toPx() - 4.dp.toPx(), thumbOffset),
            size = Size(width.toPx(), thumbHeight),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
    }
}

@Composable
fun SlashCommandPopup(
    onDismiss: () -> Unit,
    onSelectOption: (prefix: String, suffix: String) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .imePadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        val maxModalHeight = maxHeight * 0.75f
        val scrollState = rememberScrollState()
        val scrollbarColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = maxModalHeight),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Slash Commands", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                val commands = listOf(
                    "Heading 1" to ("# " to ""),
                    "Heading 2" to ("## " to ""),
                    "Heading 3" to ("### " to ""),
                    "Heading 4" to ("#### " to ""),
                    "Bullet List" to ("- " to ""),
                    "Numbered List" to ("1. " to ""),
                    "Task Checklist" to ("- [ ] " to ""),
                    "Fenced Code Block" to ("```\n" to "\n```"),
                    "Mermaid Diagram" to ("```mermaid\ngraph TD\n  A[Start] --> B[End]\n" to "```"),
                    "Block Math ($$)" to ("$$\n" to "\n$$"),
                    "Blockquote" to ("> " to ""),
                    "Horizontal Rule" to ("---\n" to ""),
                    "Table (3x3)" to ("| Header 1 | Header 2 | Header 3 |\n| --- | --- | --- |\n| Cell 1 | Cell 2 | Cell 3 |\n" to "")
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .simpleVerticalScrollbar(scrollState = scrollState, color = scrollbarColor, width = 3.dp)
                        .verticalScroll(scrollState)
                ) {
                    commands.forEach { (name, format) ->
                        TextButton(
                            onClick = { onSelectOption(format.first, format.second) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                                Text(name, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WikilinkAutocompletePopup(
    notes: List<NoteHeader>,
    onDismiss: () -> Unit,
    onSelectNote: (title: String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .imePadding(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Insert Wikilink [[...]]", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Clear, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            if (notes.isEmpty()) {
                Text("No existing notes found.", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                notes.take(6).forEach { note ->
                    TextButton(
                        onClick = { onSelectNote(note.title) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(note.title, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun CreateNoteDialog(
    currentDir: String,
    defaultTemplate: String,
    onDismiss: () -> Unit,
    onCreate: (titleOrTemplate: String) -> Unit
) {
    var input by remember { mutableStateOf(defaultTemplate) }
    var preview by remember { mutableStateOf("") }

    LaunchedEffect(input) {
        preview = withContext(Dispatchers.IO) {
            previewNoteName(input)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (currentDir.isEmpty()) "Create New Note" else "Create Note in '$currentDir'") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Note Title or Template") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Filename Preview: $preview.md",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (input.isNotBlank()) {
                        onCreate(input)
                    }
                },
                enabled = input.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CreateFolderDialog(
    currentDir: String,
    onDismiss: () -> Unit,
    onCreate: (folderName: String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (currentDir.isEmpty()) "Create New Folder" else "Create Folder in '$currentDir'") },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text("Folder Name") },
                placeholder = { Text("Projects") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (folderName.isNotBlank()) {
                        onCreate(folderName)
                    }
                },
                enabled = folderName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Text("Create Folder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RenameItemDialog(
    item: FolderItem,
    onDismiss: () -> Unit,
    onRename: (newName: String) -> Unit
) {
    var newName by remember { mutableStateOf(item.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename ${if (item.isDir) "Folder" else "File"}") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newName.isNotBlank()) {
                        onRename(newName)
                    }
                },
                enabled = newName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
