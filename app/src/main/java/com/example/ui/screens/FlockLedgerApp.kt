package com.example.ui.screens

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.EntryCategory
import com.example.data.model.LedgerEntry
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
import kotlinx.coroutines.launch

@Composable
fun FlockLedgerRoot() {
    val vm: FlockViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val deps = FlockViewModel.createDependencies(app)
                FlockViewModel(
                    repository = deps.repository,
                    prefsRepository = deps.prefsRepository,
                    backupManager = deps.backupManager,
                    pdfGenerator = deps.pdfGenerator,
                )
            }
        }
    )

    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val isSindhi = prefs.language == AppLanguage.SINDHI

    FlockLedgerTheme(
        darkTheme = prefs.darkMode,
        isSindhiRtl = isSindhi
    ) {
        MainWorkspace(viewModel = vm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainWorkspace(viewModel: FlockViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_FUNDFLOW

    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val isSindhi = prefs.language == AppLanguage.SINDHI
    val strings: AppStrings = remember(isSindhi) { if (isSindhi) sindhiStrings() else englishStrings() }
    val navItems = remember(strings) { buildFlockNavItems(strings) }

    var showEditorDialog by remember { mutableStateOf(false) }
    var editorInitialCategory by remember { mutableStateOf(EntryCategory.EXPENSE) }
    var editingEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var prefilledShepherd by remember { mutableStateOf("") }
    var viewingReceiptEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showFarmDialog by remember { mutableStateOf(false) }

    val navigateToRoute: (String) -> Unit = { route ->
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

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
    val showAdminView = !prefs.preferCaretakerView

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            FlockDrawerSheetContent(
                farmName = successData?.activeFarm?.name.orEmpty(),
                navItems = navItems,
                currentRoute = currentRoute,
                prefs = prefs,
                isAdminView = showAdminView,
                isSindhi = isSindhi,
                strings = strings,
                onNavigate = { route ->
                    navigateToRoute(route)
                    scope.launch { drawerState.close() }
                },
                onToggleRoleMode = {
                    viewModel.setPreferCaretakerView(!prefs.preferCaretakerView)
                    scope.launch { drawerState.close() }
                },
                onToggleLanguage = {
                    viewModel.toggleLanguage()
                    scope.launch { drawerState.close() }
                },
                onToggleDarkMode = { viewModel.toggleDarkMode() },
                onDownloadPdf = {
                    scope.launch { drawerState.close() }
                    viewModel.generatePdfReport()
                },
                onOpenBackupSettings = {
                    scope.launch { drawerState.close() }
                    showBackupDialog = true
                },
                onOpenFarmSettings = {
                    scope.launch { drawerState.close() }
                    showFarmDialog = true
                }
            )
        }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useSideRail = maxWidth >= 600.dp

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
                bottomBar = {
                    if (!useSideRail && successData?.activeFarm != null) {
                        FlockBottomNavBar(
                            items = navItems,
                            currentRoute = currentRoute,
                            onNavigate = navigateToRoute,
                        )
                    }
                },
                floatingActionButton = {
                    if (successData?.activeFarm != null) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                editingEntry = null
                                editorInitialCategory = when (currentRoute) {
                                    ROUTE_TRADE -> EntryCategory.LIVESTOCK_BUY
                                    ROUTE_SALARIES -> EntryCategory.SHEPHERD_ADVANCE
                                    ROUTE_MEDICAL -> EntryCategory.MEDICINE_VET
                                    else -> EntryCategory.EXPENSE
                                }
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
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useSideRail && successData?.activeFarm != null) {
                        FlockSideNavRail(
                            items = navItems,
                            currentRoute = currentRoute,
                            onNavigate = navigateToRoute,
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        if (isBusy) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        when (val state = dashboardState) {
                            is UiState.Loading -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
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
                                } else {
                                    val openEditorForCategory: (EntryCategory) -> Unit = { cat ->
                                        editingEntry = null
                                        editorInitialCategory = cat
                                        prefilledShepherd = ""
                                        showEditorDialog = true
                                    }
                                    val openEditorForEntry: (LedgerEntry) -> Unit = { entry ->
                                        editingEntry = entry
                                        editorInitialCategory = entry.parsedCategory()
                                        prefilledShepherd = entry.shepherdName
                                        showEditorDialog = true
                                    }

                                    NavHost(
                                        navController = navController,
                                        startDestination = ROUTE_FUNDFLOW,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        composable(ROUTE_FUNDFLOW) {
                                            FundflowScreen(
                                                farm = farm,
                                                entries = state.data.entries,
                                                summary = state.data.summary,
                                                entriesSinceBackup = prefs.entriesSinceBackup,
                                                isAdminView = showAdminView,
                                                strings = strings,
                                                onQuickAddCategory = openEditorForCategory,
                                                onEditEntry = openEditorForEntry,
                                                onViewReceipt = { viewingReceiptEntry = it },
                                                onDownloadPdf = { viewModel.generatePdfReport() },
                                                onOpenBackupSettings = { showBackupDialog = true }
                                            )
                                        }
                                        composable(ROUTE_LEDGER) {
                                            LedgerScreen(
                                                entries = state.data.entries,
                                                canEdit = showAdminView,
                                                strings = strings,
                                                onEditEntry = openEditorForEntry,
                                                onViewReceipt = { viewingReceiptEntry = it }
                                            )
                                        }
                                        composable(ROUTE_TRADE) {
                                            TradeScreen(
                                                farm = farm,
                                                entries = state.data.entries,
                                                summary = state.data.summary,
                                                canEdit = showAdminView,
                                                strings = strings,
                                                onAddTrade = openEditorForCategory,
                                                onEditEntry = openEditorForEntry,
                                                onViewReceipt = { viewingReceiptEntry = it }
                                            )
                                        }
                                        composable(ROUTE_SALARIES) {
                                            SalariesScreen(
                                                entries = state.data.entries,
                                                summary = state.data.summary,
                                                canEdit = showAdminView,
                                                strings = strings,
                                                onAddShepherd = { viewModel.addShepherd(it) },
                                                onPayShepherd = { shepherdName ->
                                                    editingEntry = null
                                                    editorInitialCategory = EntryCategory.SHEPHERD_ADVANCE
                                                    prefilledShepherd = shepherdName
                                                    showEditorDialog = true
                                                },
                                                onEditEntry = openEditorForEntry,
                                                onViewReceipt = { viewingReceiptEntry = it }
                                            )
                                        }
                                        composable(ROUTE_MEDICAL) {
                                            MedicalScreen(
                                                entries = state.data.entries,
                                                summary = state.data.summary,
                                                canEdit = showAdminView,
                                                strings = strings,
                                                onAddMedical = { openEditorForCategory(EntryCategory.MEDICINE_VET) },
                                                onEditEntry = openEditorForEntry,
                                                onViewReceipt = { viewingReceiptEntry = it }
                                            )
                                        }
                                    }
                                }
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
                    editingEntry = editingEntry,
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
            onDelete = if (editingEntry != null && showAdminView) {
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
            allFarms = successData.farms,
            isAdmin = showAdminView,
            strings = strings,
            onDismiss = { showFarmDialog = false },
            onSelectFarm = { viewModel.selectFarm(it) },
            onCreateFarm = { name, initFlock -> viewModel.createFarm(name, initFlock) },
            onSaveFarmSettings = { name, initFlock, shepherds ->
                viewModel.updateFarmSettings(name, initFlock, shepherds)
            }
        )
    }
}
