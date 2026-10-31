package com.slashnote.app.ui.settings

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slashnote.app.security.AppSettings
import com.slashnote.app.security.SecureStorage
import com.slashnote.app.utils.StorageUtils
import com.slashnote.app.sync.GitSyncWorker
import com.slashnote.app.sync.GitignoreManager
import com.slashnote.app.ui.components.BottomNavBar
import com.slashnote.app.ui.components.StitchBottomTab
import com.slashnote.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.slash_notes_core.previewNoteName
import uniffi.slash_notes_core.checkGitConnection
import uniffi.slash_notes_core.syncGitRepository
import uniffi.slash_notes_core.initGitRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    notesDir: String,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    appTheme: AppTheme = AppTheme.SLASHNOTE,
    onSelectAppTheme: (AppTheme) -> Unit = {},
    softPaperFolderColors: Map<String, String> = emptyMap(),
    onSetSoftPaperFolderColor: (key: String, hexColor: String) -> Unit = { _, _ -> },
    onResetSoftPaperFolderColor: (key: String) -> Unit = {},
    onResetAllSoftPaperFolderColors: () -> Unit = {},
    fontSizeSp: Int = 16,
    onFontSizeChange: (Int) -> Unit = {},
    onBack: () -> Unit,
    onPickCustomVaultPath: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableStateOf(0) }

    var token by remember { mutableStateOf(SecureStorage.getGitToken(context)) }
    var remoteUrl by remember { mutableStateOf(SecureStorage.getRemoteUrl(context)) }
    var branch by remember { mutableStateOf(SecureStorage.getGitBranch(context)) }

    var customVaultPath by remember { mutableStateOf(AppSettings.getCustomVaultPath(context).ifEmpty { notesDir }) }
    var showAllFiles by remember { mutableStateOf(AppSettings.getShowAllFiles(context)) }
    var template by remember { mutableStateOf(AppSettings.getNoteTemplate(context)) }
    var ignored by remember { mutableStateOf(AppSettings.getIgnoredPatterns(context)) }
    var currentFontSizeSp by remember(fontSizeSp) { mutableStateOf(fontSizeSp) }
    var isRtl by remember { mutableStateOf(AppSettings.getIsRtl(context)) }
    var autoSaveEnabled by remember { mutableStateOf(true) }
    var liveParseEnabled by remember { mutableStateOf(true) }

    var livePreview by remember { mutableStateOf("") }

    LaunchedEffect(template) {
        livePreview = withContext(Dispatchers.IO) {
            previewNoteName(template)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Vault & Settings", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onSurface,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = MaterialTheme.colorScheme.onBackground,
                        height = 3.dp
                    )
                }
            ) {
                listOf("General", "Git & Sync", "Appearance", "Shortcuts").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                title,
                                maxLines = 1,
                                softWrap = false,
                                fontSize = 14.sp,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTabIndex == index) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when (selectedTabIndex) {
                    0 -> GeneralStitchTab(
                        vaultPath = customVaultPath,
                        onVaultPathChange = {
                            customVaultPath = it
                            AppSettings.saveCustomVaultPath(context, it)
                        },
                        onPickCustomVaultPath = onPickCustomVaultPath,
                        showAllFiles = showAllFiles,
                        onToggleShowAllFiles = {
                            showAllFiles = !showAllFiles
                            AppSettings.saveShowAllFiles(context, showAllFiles)
                        },
                        template = template,
                        onTemplateChange = {
                            template = it
                            AppSettings.saveNoteTemplate(context, it)
                        },
                        ignored = ignored,
                        onIgnoredChange = {
                            ignored = it
                            AppSettings.saveIgnoredPatterns(context, it)
                        },
                        liveParseEnabled = liveParseEnabled,
                        onToggleLiveParse = { liveParseEnabled = !liveParseEnabled },
                        autoSaveEnabled = autoSaveEnabled,
                        onToggleAutoSave = { autoSaveEnabled = !autoSaveEnabled }
                    )
                    1 -> GitSyncTab(
                        notesDir = notesDir,
                        remoteUrl = remoteUrl,
                        onRemoteUrlChange = {
                            remoteUrl = it
                            SecureStorage.saveRemoteUrl(context, it)
                        },
                        token = token,
                        onTokenChange = {
                            token = it
                            SecureStorage.saveGitToken(context, it)
                        },
                        branch = branch,
                        onBranchChange = {
                            branch = it
                            SecureStorage.saveGitBranch(context, it)
                        },
                        onSyncNow = {
                            val tokenVal = SecureStorage.getGitToken(context)
                            val remoteVal = SecureStorage.getRemoteUrl(context)
                            val branchVal = SecureStorage.getGitBranch(context)
                            if (remoteVal.isEmpty() || tokenVal.isEmpty()) {
                                Toast.makeText(context, "Sync Failed: Remote URL or PAT token missing in Settings", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Syncing with GitHub...", Toast.LENGTH_SHORT).show()
                                StorageUtils.checkAndRequestStoragePermission(context)
                                val targetRepoDir = StorageUtils.getSafeVaultPath(context, notesDir)
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        initGitRepository(targetRepoDir)
                                        val msg = syncGitRepository(targetRepoDir, tokenVal, remoteVal, branchVal)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Log.e("GitSync", "Full Sync Error Details:", e)
                                        val errMsg = e.message ?: "Git sync failed"
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Sync Failed: $errMsg", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        }
                    )
                    2 -> AppearanceTab(
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = onToggleTheme,
                        appTheme = appTheme,
                        onSelectAppTheme = onSelectAppTheme,
                        softPaperFolderColors = softPaperFolderColors,
                        onSetSoftPaperFolderColor = onSetSoftPaperFolderColor,
                        onResetSoftPaperFolderColor = onResetSoftPaperFolderColor,
                        onResetAllSoftPaperFolderColors = onResetAllSoftPaperFolderColors,
                        fontSizeSp = currentFontSizeSp,
                        onFontSizeChange = {
                            currentFontSizeSp = it
                            onFontSizeChange(it)
                            AppSettings.saveFontSizeSp(context, it)
                        },
                        isRtl = isRtl,
                        onToggleRtl = {
                            isRtl = !isRtl
                            AppSettings.saveIsRtl(context, isRtl)
                        }
                    )
                    3 -> ShortcutsTab()
                }
            }
        }
    }
}

@Composable
private fun GeneralStitchTab(
    vaultPath: String,
    onVaultPathChange: (String) -> Unit,
    onPickCustomVaultPath: () -> Unit,
    showAllFiles: Boolean,
    onToggleShowAllFiles: () -> Unit,
    template: String,
    onTemplateChange: (String) -> Unit,
    ignored: String,
    onIgnoredChange: (String) -> Unit,
    liveParseEnabled: Boolean,
    onToggleLiveParse: () -> Unit,
    autoSaveEnabled: Boolean,
    onToggleAutoSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Section 1: General Vault Configuration
        Text(
            text = "General Vault Configuration",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        StitchCardContainer {
            // Field 1: Local Vault Path
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Local Vault Path", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "The physical location of your markdown files on this device.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = vaultPath,
                        onValueChange = onVaultPathChange,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background,
                            focusedBorderColor = MaterialTheme.colorScheme.outline,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Button(
                        onClick = onPickCustomVaultPath,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Browse", fontSize = 13.sp)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Field 2: Show All Files Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Show All Files", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Display all file extensions alongside Markdown notes in the sidebar explorer",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = showAllFiles,
                    onCheckedChange = { onToggleShowAllFiles() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.background
                    )
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Field 3: Default Note Template
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Default Note Template", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Template applied to newly created notes in the root directory.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = template,
                    onValueChange = onTemplateChange,
                    singleLine = true,
                    trailingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background,
                        focusedBorderColor = StitchBorder,
                        unfocusedBorderColor = StitchBorder
                    )
                )
            }

            HorizontalDivider(color = StitchBorder)

            // Field 4: Live Filename Preview Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Live Filename Preview", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Automatically parse H1 tags to display as filename in the explorer.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = liveParseEnabled,
                    onCheckedChange = { onToggleLiveParse() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = StitchAccentCoral,
                        uncheckedThumbColor = StitchTextMuted,
                        uncheckedTrackColor = StitchBackground
                    )
                )
            }
        }

        // Section 2: Indexing & Search
        Text(
            text = "Indexing & Search",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        StitchCardContainer {
            // Excluded Folders Chips
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Excluded Folders", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Paths to ignore during global search and indexing.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                val patterns = ignored.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    patterns.forEach { pat ->
                        StitchChipTag(path = pat, onRemove = {
                            val newPat = patterns.filter { it != pat }.joinToString(",")
                            onIgnoredChange(newPat)
                        })
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.clickable {
                            onIgnoredChange(if (ignored.isEmpty()) ".git/" else "$ignored,.new_path/")
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("+ Add Path", color = StitchAccentCoral, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalDivider(color = StitchBorder)

            // Aggressive Auto-Save Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Aggressive Auto-Save", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Save changes immediately on every keystroke rather than on blur.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = autoSaveEnabled,
                    onCheckedChange = { onToggleAutoSave() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = StitchAccentCoral,
                        uncheckedThumbColor = StitchTextMuted,
                        uncheckedTrackColor = StitchBackground
                    )
                )
            }
        }
    }
}

@Composable
private fun StitchCardContainer(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(content = content)
    }
}

@Composable
private fun StitchChipTag(path: String, onRemove: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(path, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.Clear, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun GitSyncTab(
    notesDir: String,
    remoteUrl: String,
    onRemoteUrlChange: (String) -> Unit,
    token: String,
    onTokenChange: (String) -> Unit,
    branch: String,
    onBranchChange: (String) -> Unit,
    onSyncNow: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var connectionState by remember { mutableStateOf<ConnectionState>(ConnectionState.Idle) }

    fun testConnection() {
        if (remoteUrl.isBlank()) {
            connectionState = ConnectionState.Error("Remote URL is empty")
            return
        }
        connectionState = ConnectionState.Testing
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val successMsg = checkGitConnection(
                        repoPath = notesDir,
                        token = token,
                        remoteUrl = remoteUrl
                    )
                    ConnectionState.Success(successMsg)
                } catch (e: Exception) {
                    // UniFFI wraps Rust errors as "msg=<actual>"; strip that prefix.
                    val raw = e.message ?: e.javaClass.simpleName
                    val clean = raw.removePrefix("msg=")
                    ConnectionState.Error(clean)
                }
            }.let { connectionState = it }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Embedded Git Engine Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

        StitchCardContainer {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = remoteUrl,
                    onValueChange = onRemoteUrlChange,
                    label = { Text("Repository Remote URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = token,
                    onValueChange = onTokenChange,
                    label = { Text("Personal Access Token (PAT)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = branch,
                    onValueChange = onBranchChange,
                    label = { Text("Git Branch") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Button(
                    onClick = onSyncNow,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sync Repository Now")
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Connection status card
                Text("Repository Connection", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                when (connectionState) {
                    is ConnectionState.Idle -> Text(
                        "Not tested yet. Press \"Test Connection\" below to verify your remote.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    is ConnectionState.Testing -> Text(
                        "Testing connection to remote...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    is ConnectionState.Success -> {
                        val msg = (connectionState as ConnectionState.Success).message
                        Text(msg, fontSize = 12.sp, color = Color(0xFF2DD4BF), modifier = Modifier.padding(top = 4.dp))
                    }
                    is ConnectionState.Error -> {
                        val err = (connectionState as ConnectionState.Error).message
                        Text("Error: $err", fontSize = 12.sp, color = Color(0xFFEF4444), modifier = Modifier.padding(top = 4.dp))
                    }
                }

                Button(
                    onClick = { testConnection() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Default.CloudDone, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test Connection")
                }
            }
        }

        IgnoredFileTypesCard(notesDir = notesDir)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IgnoredFileTypesCard(notesDir: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val defaults = remember { GitignoreManager.getDefaultExtensions() }

    var savedTypes by remember {
        mutableStateOf(AppSettings.getIgnoredFileTypes(context))
    }
    val currentTypes = savedTypes ?: defaults

    var inputVal by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    fun save(updated: List<String>?) {
        isSaving = true
        AppSettings.saveIgnoredFileTypes(context, updated)
        savedTypes = updated
        val effective = updated ?: defaults
        scope.launch(Dispatchers.IO) {
            GitignoreManager.syncGitignore(notesDir, effective)
            isSaving = false
        }
    }

    fun handleAdd() {
        errorMessage = null
        val trimmed = inputVal.trim()
        if (trimmed.isEmpty()) return
        val parseRes = GitignoreManager.parseAndNormalizeList(trimmed)
        parseRes.fold(
            onSuccess = { newItems ->
                val filtered = newItems.filter { !currentTypes.contains(it) }
                if (filtered.isEmpty()) {
                    errorMessage = "File types already in ignore list"
                    return
                }
                inputVal = ""
                save(currentTypes + filtered)
            },
            onFailure = { err ->
                errorMessage = err.message ?: "Invalid file extension format"
            }
        )
    }

    fun handleRemove(ext: String) {
        save(currentTypes.filter { it != ext })
    }

    fun handleReset() {
        errorMessage = null
        save(null)
    }

    val customTypes = currentTypes.filter { !defaults.contains(it) }
    val defaultTypes = currentTypes.filter { defaults.contains(it) }
    val isDefault = savedTypes == null || (currentTypes.size == defaults.size && currentTypes.containsAll(defaults))

    Text(
        "Ignore file types",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )

    StitchCardContainer {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Non-note file extensions excluded from version control (.gitignore) and sync.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputVal,
                    onValueChange = {
                        inputVal = it
                        errorMessage = null
                    },
                    placeholder = { Text(".mp4, .mp3, .pdf, .docx", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                )

                Button(
                    onClick = { handleAdd() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    enabled = inputVal.isNotBlank() && !isSaving
                ) {
                    Text("Add")
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    fontSize = 12.sp,
                    color = Color(0xFFEF4444)
                )
            }

            // Custom Rules
            if (customTypes.isNotEmpty()) {
                Text(
                    "Custom rules (${customTypes.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = StitchAccentCoral
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    customTypes.forEach { ext ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = StitchAccentCoral.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, StitchAccentCoral.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    ext,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove $ext",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { handleRemove(ext) }
                                )
                            }
                        }
                    }
                }
            }

            // Default Rules
            Text(
                "Default rules (${defaultTypes.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                defaultTypes.forEach { ext ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                ext,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove $ext",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { handleRemove(ext) }
                            )
                        }
                    }
                }
            }

            // Reset to defaults
            if (!isDefault) {
                Text(
                    text = "Reset to defaults",
                    color = StitchAccentCoral,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { handleReset() }
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

private sealed class ConnectionState {
    data object Idle : ConnectionState()
    data object Testing : ConnectionState()
    data class Success(val message: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

private val SoftPaperFolderPaletteSwatches = listOf(
    "#BF7B87", "#CF7878", "#9D8AB5", "#A888C2",
    "#CF9855", "#B8B040", "#72A3A8", "#1FA3AC",
    "#6B9E78", "#7E93B8", "#C4836A", "#918E8A",
    "#DAD7D2", "#5C6274"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceTab(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    appTheme: AppTheme,
    onSelectAppTheme: (AppTheme) -> Unit,
    softPaperFolderColors: Map<String, String>,
    onSetSoftPaperFolderColor: (key: String, hexColor: String) -> Unit,
    onResetSoftPaperFolderColor: (key: String) -> Unit,
    onResetAllSoftPaperFolderColors: () -> Unit,
    fontSizeSp: Int,
    onFontSizeChange: (Int) -> Unit,
    isRtl: Boolean,
    onToggleRtl: () -> Unit
) {
    var editingFolderSpec by remember { mutableStateOf<SoftPaperFolderColorSpec?>(null) }
    var hexInputText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Appearance & Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

        StitchCardContainer {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Theme", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Choose the color theme and typography style",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppTheme.entries.forEach { option ->
                        val isSelected = appTheme == option
                        OutlinedButton(
                            onClick = { onSelectAppTheme(option) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = option.displayName,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Dark Theme Mode", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        if (appTheme == AppTheme.OBSIDIAN_SOFT_PAPER) "Deep charcoal/navy Soft Paper palette" else "Warm charcoal dark visual palette",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = { onToggleTheme() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.background
                    )
                )
            }

            if (appTheme == AppTheme.OBSIDIAN_SOFT_PAPER) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Sidebar Folder Colors",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Customize folder pills in ${if (isDarkTheme) "Dark" else "Light"} mode",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (softPaperFolderColors.isNotEmpty()) {
                            TextButton(onClick = onResetAllSoftPaperFolderColors) {
                                Text(
                                    "Reset all",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    SoftPaperFolderColorSpecs.forEach { spec ->
                        val defaultHex = if (isDarkTheme) spec.defaultDarkHex else spec.defaultLightHex
                        val activeHex = softPaperFolderColors[spec.key] ?: defaultHex
                        val defaultColor = parseHexColorOrDefault(defaultHex, MaterialTheme.colorScheme.primary)
                        val activeColor = parseHexColorOrDefault(activeHex, defaultColor)
                        val pillTextColor = readableTextForBackground(activeColor, Color.White)
                        val isCustomized = softPaperFolderColors.containsKey(spec.key)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = spec.label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isCustomized) {
                                    Text(
                                        text = "Reset",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .clickable { onResetSoftPaperFolderColor(spec.key) }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                }
                                Surface(
                                    modifier = Modifier
                                        .clickable {
                                            editingFolderSpec = spec
                                            hexInputText = activeHex
                                        },
                                    shape = RoundedCornerShape(6.dp),
                                    color = activeColor,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Text(
                                        text = activeHex.uppercase(),
                                        color = pillTextColor,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Editor Font Size", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("${fontSizeSp} sp", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { if (fontSizeSp > 12) onFontSizeChange(fontSizeSp - 1) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text("A-", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Slider(
                        value = fontSizeSp.toFloat(),
                        onValueChange = { onFontSizeChange(it.toInt()) },
                        valueRange = 12f..24f,
                        steps = 11,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    IconButton(
                        onClick = { if (fontSizeSp < 24) onFontSizeChange(fontSizeSp + 1) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text("A+", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("RTL Text Direction", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Support Right-To-Left language layouts", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = isRtl,
                    onCheckedChange = { onToggleRtl() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        }
    }

    if (editingFolderSpec != null) {
        val spec = editingFolderSpec!!
        val defaultHex = if (isDarkTheme) spec.defaultDarkHex else spec.defaultLightHex
        AlertDialog(
            onDismissRequest = { editingFolderSpec = null },
            title = {
                Text(
                    text = spec.label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Choose a preset or enter a custom 6-digit hex color:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SoftPaperFolderPaletteSwatches.forEach { swatchHex ->
                            val swatchColor = parseHexColorOrDefault(swatchHex, MaterialTheme.colorScheme.primary)
                            val isSelected = hexInputText.equals(swatchHex, ignoreCase = true)
                            Surface(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable {
                                        hexInputText = swatchHex
                                        onSetSoftPaperFolderColor(spec.key, swatchHex)
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = swatchColor,
                                border = BorderStroke(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                                )
                            ) {}
                        }
                    }
                    OutlinedTextField(
                        value = hexInputText,
                        onValueChange = { next ->
                            hexInputText = next
                            val clean = next.trim().removePrefix("#")
                            if (clean.length == 6 && clean.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                                onSetSoftPaperFolderColor(spec.key, "#${clean.uppercase()}")
                            }
                        },
                        label = { Text("Hex Color (#RRGGBB)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clean = hexInputText.trim().removePrefix("#")
                        if (clean.length == 6 && clean.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                            onSetSoftPaperFolderColor(spec.key, "#${clean.uppercase()}")
                        }
                        editingFolderSpec = null
                    }
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onResetSoftPaperFolderColor(spec.key)
                        hexInputText = defaultHex
                        editingFolderSpec = null
                    }
                ) {
                    Text("Reset Default")
                }
            }
        )
    }
}

@Composable
private fun ShortcutsTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Commands & Keyboard Shortcuts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

        ShortcutReferenceRow(trigger = "/", description = "Open Slash Command popup (H1-H3, Lists, Checkboxes, Tables, Math, Mermaid)")
        ShortcutReferenceRow(trigger = "[[", description = "Insert & autocomplete Wikilinks to existing vault notes")
        ShortcutReferenceRow(trigger = "$$", description = "Insert LaTeX Block Math rendering")
        ShortcutReferenceRow(trigger = "```mermaid", description = "Insert Mermaid diagram block")
        ShortcutReferenceRow(trigger = "Command Palette Tab", description = "Open Quick Action Sheet for fast command execution")
        ShortcutReferenceRow(trigger = "Swipe Right Edge", description = "Open VS Code style navigation sidebar drawer")
    }
}

@Composable
private fun ShortcutReferenceRow(trigger: String, description: String) {
    StitchCardContainer {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = trigger,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
