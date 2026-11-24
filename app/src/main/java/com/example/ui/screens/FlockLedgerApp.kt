package com.example.ui.screens

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.firebase.attemptAutoSignIn
import com.example.data.firebase.authStateFlow
import com.example.data.firebase.signOutUser
import com.example.data.model.EntryCategory
import com.example.data.model.LedgerEntry
import com.example.data.preferences.AppPreferencesRepository
import com.example.data.preferences.UserPreferences
import com.example.i18n.AppLanguage
import com.example.i18n.AppStrings
import com.example.i18n.englishStrings
import com.example.i18n.sindhiStrings
import com.example.ui.components.EntryEditorDialog
import com.example.ui.components.ReceiptViewerDialog
import com.example.ui.theme.FlockLedgerTheme
import com.example.ui.viewmodel.AppEvent
import com.example.ui.viewmodel.FlockViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

@Composable
fun FlockLedgerRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val prefsRepo = remember(context) { AppPreferencesRepository(context.applicationContext) }
    val rootPrefs by prefsRepo.preferencesFlow.collectAsStateWithLifecycle(initialValue = UserPreferences())

    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope,
            )
        }
    }

    val isSindhi = rootPrefs.language == AppLanguage.SINDHI
    val strings = remember(isSindhi) { if (isSindhi) sindhiStrings() else englishStrings() }

    FlockLedgerTheme(
        darkTheme = rootPrefs.darkMode,
        isSindhiRtl = isSindhi
    ) {
        val user = currentUser
        if (user == null) {
            AuthScreen(
                strings = strings,
                language = rootPrefs.language,
                darkMode = rootPrefs.darkMode,
                onToggleLanguage = {
                    scope.launch {
                        val next = if (isSindhi) AppLanguage.ENGLISH else AppLanguage.SINDHI
                        prefsRepo.setLanguage(next)
                    }
                },
                onToggleDarkMode = {
                    scope.launch { prefsRepo.setDarkMode(!rootPrefs.darkMode) }
                },
                onAuthSuccess = {}
            )
        } else {
            val uid = user.uid
            key(uid) {
                val vm: FlockViewModel = viewModel(
                    key = uid,
                    factory = viewModelFactory {
                        initializer {
                            val app = checkNotNull(this[APPLICATION_KEY]) as Application
                            val deps = FlockViewModel.createDependencies(app)
                            FlockViewModel(
                                repository = deps.repository,
                                prefsRepository = deps.prefsRepository,
                                backupManager = deps.backupManager,
                                pdfGenerator = deps.pdfGenerator,
                                currentUserId = uid,
                                currentUserDisplayName = user.displayName ?: user.email,
                            )
                        }
                    }
                )
                AuthenticatedWorkspace(
                    viewModel = vm,
                    userName = user.displayName ?: user.email ?: "",
                    userEmail = user.email ?: "",
                    onSignOut = {
                        signOutUser(credentialManager, scope)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedWorkspace(
    viewModel: FlockViewModel,
    userName: String,
    userEmail: String,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }

    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val isSindhi = prefs.language == AppLanguage.SINDHI
    val strings: AppStrings = remember(isSindhi) { if (isSindhi) sindhiStrings() else englishStrings() }

    var showEditorDialog by remember { mutableStateOf(false) }
    var editorInitialCategory by remember { mutableStateOf(EntryCategory.EXPENSE) }
    var editingEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var prefilledShepherd by remember { mutableStateOf("") }
    var viewingReceiptEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showFarmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel, strings) {
        viewModel.events.collect { event ->
            when (event) {
                is AppEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is AppEvent.AutoBackupCompleted -> {
                    snackbarHostState.showSnackbar(
                        "Backup: ${event.result.googleSheetStatus} | Sia: ${event.result.siaStorageStatus}"
                    )
                }
                is AppEvent.PdfReady -> {
                    val res = snackbarHostState.showSnackbar(
                        message = strings.pdfGeneratedSuccess,
                        actionLabel = strings.openOrSharePdf,
                        duration = SnackbarDuration.Long
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, event.result.shareUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, strings.openOrSharePdf))
                    }
                }
                is AppEvent.CsvReady -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, strings.exportCsvShare))
                }
            }
        }
    }

    val successData = (dashboardState as? UiState.Success)?.data
    val isAdmin = successData?.isUserAdminOnActiveFarm == true
    val showAdminView = isAdmin && !prefs.preferCaretakerView

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = strings.appTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = userName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (userEmail.isNotBlank() && userEmail != userName) {
                                Text(
                                    text = userEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = if (showAdminView) strings.adminMode else strings.caretakerMode,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Role switcher (Admin can preview Caretaker Simple Mode)
                    if (isAdmin) {
                        NavigationDrawerItem(
                            icon = {
                                Icon(
                                    imageVector = if (showAdminView) Icons.Rounded.AdminPanelSettings else Icons.Rounded.Person,
                                    contentDescription = strings.roleModeLabel
                                )
                            },
                            label = {
                                Text(if (showAdminView) strings.caretakerMode else strings.adminMode)
                            },
                            selected = false,
                            onClick = {
                                viewModel.setPreferCaretakerView(!prefs.preferCaretakerView)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.testTag("drawer_role_toggle")
                        )
                    }

                    // Language Toggle (Sindhi / English)
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Rounded.Translate, contentDescription = strings.languageLabel) },
                        label = { Text("${strings.languageLabel}: ${if (isSindhi) strings.switchToEnglish else strings.switchToSindhi}") },
                        selected = false,
                        onClick = {
                            viewModel.toggleLanguage()
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.testTag("drawer_language_toggle")
                    )

                    // Dark / Light Mode Toggle
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = if (prefs.darkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = strings.themeLabel
                            )
                        },
                        label = { Text(if (prefs.darkMode) strings.lightMode else strings.darkMode) },
                        badge = {
                            Switch(
                                checked = prefs.darkMode,
                                onCheckedChange = { viewModel.toggleDarkMode() }
                            )
                        },
                        selected = false,
                        onClick = { viewModel.toggleDarkMode() },
                        modifier = Modifier.testTag("drawer_theme_toggle")
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // PDF Summary Report Download
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Rounded.PictureAsPdf, contentDescription = strings.downloadPdfReport) },
                        label = { Text(strings.downloadPdfReport) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.generatePdfReport()
                        },
                        modifier = Modifier.testTag("drawer_pdf_button")
                    )

                    // Auto-Backup Settings (Google Sheet & Sia Storage)
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Rounded.CloudDone, contentDescription = strings.backupSettingsMenu) },
                        label = { Text(strings.backupSettingsMenu) },
                        badge = {
                            Badge {
                                Text("${prefs.entriesSinceBackup.coerceAtMost(5)}/5")
                            }
                        },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            showBackupDialog = true
                        },
                        modifier = Modifier.testTag("drawer_backup_button")
                    )

                    // Farm & Team Setup
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Rounded.Groups, contentDescription = strings.farmTeamMenu) },
                        label = { Text(strings.farmTeamMenu) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            showFarmDialog = true
                        },
                        modifier = Modifier.testTag("drawer_farm_button")
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Sign Out
                    NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = strings.signOut) },
                        label = { Text(strings.signOut) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onSignOut()
                        },
                        modifier = Modifier.testTag("drawer_sign_out_button")
                    )
                }
            }
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("hamburger_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Menu,
                                contentDescription = strings.menuTitle
                            )
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = strings.appTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (showAdminView) strings.adminMode else strings.caretakerMode,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.toggleLanguage() },
                            modifier = Modifier.testTag("topbar_language_toggle")
                        ) {
                            Icon(Icons.Rounded.Translate, contentDescription = strings.languageLabel)
                        }
                        IconButton(
                            onClick = { viewModel.toggleDarkMode() },
                            modifier = Modifier.testTag("topbar_theme_toggle")
                        ) {
                            Icon(
                                imageVector = if (prefs.darkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = strings.themeLabel
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                if (successData?.activeFarm != null) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            editingEntry = null
                            editorInitialCategory = EntryCategory.EXPENSE
                            prefilledShepherd = ""
                            showEditorDialog = true
                        },
                        icon = { Icon(Icons.Rounded.Add, contentDescription = strings.addEntryTitle) },
                        text = { Text(strings.quickExpense) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("add_entry_fab")
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (isBusy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    when (val state = dashboardState) {
                        is UiState.Loading -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CircularProgressIndicator()
                                    Text(strings.loadingText, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        is UiState.Error -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(state.message, color = MaterialTheme.colorScheme.error)
                                    Button(onClick = { viewModel.retryInitialization() }) {
                                        Text(strings.retry)
                                    }
                                }
                            }
                        }

                        is UiState.Success -> {
                            val farm = state.data.activeFarm
                            if (farm == null) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator()
                                        Button(onClick = { viewModel.retryInitialization() }) {
                                            Text(strings.retry)
                                        }
                                    }
                                }
                            } else if (showAdminView) {
                                AdminDashboardScreen(
                                    farm = farm,
                                    entries = state.data.entries,
                                    summary = state.data.summary,
                                    entriesSinceBackup = prefs.entriesSinceBackup,
                                    strings = strings,
                                    onEditEntry = { entry ->
                                        editingEntry = entry
                                        editorInitialCategory = entry.parsedCategory()
                                        prefilledShepherd = entry.shepherdName
                                        showEditorDialog = true
                                    },
                                    onViewReceipt = { entry -> viewingReceiptEntry = entry },
                                    onAddShepherd = { name -> viewModel.addShepherd(name) },
                                    onPayShepherd = { shepherdName ->
                                        editingEntry = null
                                        editorInitialCategory = EntryCategory.SHEPHERD_ADVANCE
                                        prefilledShepherd = shepherdName
                                        showEditorDialog = true
                                    },
                                    onDownloadPdf = { viewModel.generatePdfReport() },
                                    onOpenBackupSettings = { showBackupDialog = true }
                                )
                            } else {
                                CaretakerHomeScreen(
                                    farmName = farm.name,
                                    entries = state.data.entries,
                                    entriesSinceBackup = prefs.entriesSinceBackup,
                                    strings = strings,
                                    onQuickAddCategory = { category ->
                                        editingEntry = null
                                        editorInitialCategory = category
                                        prefilledShepherd = ""
                                        showEditorDialog = true
                                    },
                                    onViewReceipt = { entry -> viewingReceiptEntry = entry }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditorDialog && successData?.activeFarm != null) {
        EntryEditorDialog(
            strings = strings,
            initialCategory = editorInitialCategory,
            editingEntry = editingEntry,
            shepherds = successData.activeFarm.shepherds,
            prefilledShepherd = prefilledShepherd,
            onDismiss = { showEditorDialog = false },
            onSave = { cat, title, amount, sheepCount, shepherd, note, receiptB64, dateMillis ->
                viewModel.saveEntry(
                    editingEntryId = editingEntry?.id,
                    category = cat,
                    title = title,
                    amount = amount,
                    livestockCount = sheepCount,
                    shepherdName = shepherd,
                    note = note,
                    receiptBase64 = receiptB64,
                    entryDateMillis = dateMillis,
                    savedMessage = strings.entrySavedMessage,
                    autoBackupMessage = strings.autoBackupTriggeredMsg,
                )
                showEditorDialog = false
            },
            onDelete = if (editingEntry != null && isAdmin) {
                {
                    viewModel.deleteEntry(editingEntry!!.id)
                    showEditorDialog = false
                }
            } else null
        )
    }

    val receiptTarget = viewingReceiptEntry
    if (receiptTarget != null && !receiptTarget.receiptBase64.isNullOrBlank()) {
        ReceiptViewerDialog(
            base64 = receiptTarget.receiptBase64,
            title = receiptTarget.title,
            strings = strings,
            onDismiss = { viewingReceiptEntry = null }
        )
    }

    if (showBackupDialog) {
        BackupSettingsDialog(
            prefs = prefs,
            strings = strings,
            onDismiss = { showBackupDialog = false },
            onSaveConfig = { sheetUrl, siaUrl, bucket, token ->
                viewModel.saveBackupConfiguration(sheetUrl, siaUrl, bucket, token)
            },
            onBackupNow = { viewModel.runBackupNow() },
            onExportCsv = { viewModel.exportCsv() }
        )
    }

    if (showFarmDialog && successData?.activeFarm != null) {
        FarmSettingsDialog(
            farm = successData.activeFarm,
            isAdmin = isAdmin,
            strings = strings,
            onDismiss = { showFarmDialog = false },
            onSaveFarmSettings = { name, initFlock, shepherds ->
                viewModel.updateFarmSettings(name, initFlock, shepherds)
            },
            onJoinFarmByCode = { code -> viewModel.joinFarmByCode(code) },
            onSyncMembers = { viewModel.syncMemberAccess() }
        )
    }
}
