package com.example.ui.screens

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.data.model.BudgetAlertLevel
import com.example.data.model.EntryCategory
import com.example.data.model.ExpenseSubCategory
import com.example.data.model.LedgerEntry
import com.example.data.preferences.UserRole
import com.example.i18n.AppLanguage
import com.example.i18n.AppStrings
import com.example.i18n.englishStrings
import com.example.i18n.sindhiStrings
import com.example.ui.components.EntryEditorDialog
import com.example.ui.components.ReceiptViewerDialog
import com.example.ui.components.formatCurrency
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
    val activeRole by vm.activeRole.collectAsStateWithLifecycle()
    val isSindhi = prefs.language == AppLanguage.SINDHI
    val strings = remember(isSindhi) { if (isSindhi) sindhiStrings() else englishStrings() }

    FlockLedgerTheme(
        darkTheme = prefs.darkMode,
        isSindhiRtl = isSindhi
    ) {
        when {
            !prefs.pinsConfigured -> {
                PinSetupScreen(
                    strings = strings,
                    darkMode = prefs.darkMode,
                    onToggleLanguage = { vm.toggleLanguage() },
                    onToggleDarkMode = { vm.toggleDarkMode() },
                    onSavePins = { adminPin, caretakerPin ->
                        vm.setupPins(adminPin, caretakerPin, initialRole = UserRole.ADMIN)
                    }
                )
            }
            activeRole == null -> {
                PinUnlockScreen(
                    strings = strings,
                    darkMode = prefs.darkMode,
                    onToggleLanguage = { vm.toggleLanguage() },
                    onToggleDarkMode = { vm.toggleDarkMode() },
                    onVerifyPin = { pin -> vm.unlockWithPin(pin) }
                )
            }
            else -> {
                MainWorkspace(
                    viewModel = vm,
                    activeRole = activeRole!!,
                    strings = strings,
                    isSindhi = isSindhi,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainWorkspace(
    viewModel: FlockViewModel,
    activeRole: UserRole,
    strings: AppStrings,
    isSindhi: Boolean,
) {
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
    val postUpdateBanner by viewModel.postUpdateBanner.collectAsStateWithLifecycle()
    val availableUpdate by viewModel.availableUpdate.collectAsStateWithLifecycle()

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportFullBackupToUri(uri, strings.autoSafetyBackupSavedMsg)
        }
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreBackupFromUri(
                uri = uri,
                successTemplate = strings.restoreSuccessMsg,
                errorMsg = strings.restoreFailedMsg,
            )
        }
    }

    val isAdminView = activeRole == UserRole.ADMIN
    val bottomNavItems = remember(strings) { buildBottomNavItems(strings) }
    val allNavItems = remember(strings) { buildAllNavItems(strings) }

    val allowedCategories = remember(isAdminView) {
        if (isAdminView) {
            EntryCategory.entries
        } else {
            listOf(
                EntryCategory.EXPENSE,
                EntryCategory.MEDICINE_VET,
                EntryCategory.LIVESTOCK_BUY,
                EntryCategory.LIVESTOCK_SELL,
            )
        }
    }

    var showEditorDialog by remember { mutableStateOf(false) }
    var editorInitialCategory by remember { mutableStateOf(EntryCategory.EXPENSE) }
    var editorInitialSubCategory by remember { mutableStateOf<ExpenseSubCategory?>(null) }
    var editingEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var prefilledShepherdOrParty by remember { mutableStateOf("") }
    var viewingReceiptEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showFarmDialog by remember { mutableStateOf(false) }
    var showChangePinsDialog by remember { mutableStateOf(false) }
    var showStorageInfoDialog by remember { mutableStateOf(false) }
    var showPdfOptionsDialog by remember { mutableStateOf(false) }
    var includePendingInPdf by remember { mutableStateOf(false) }
    var showMonthlyBudgetDialog by remember { mutableStateOf(false) }

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
                is AppEvent.OpenUpdateUrl -> {
                    val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(event.url))
                    context.startActivity(viewIntent)
                }
                is AppEvent.BudgetThresholdAlert -> {
                    val catName = if (event.isFeed) strings.subFeed else strings.medicineAndVet
                    val msg = if (event.alertLevel == BudgetAlertLevel.EXCEEDED) {
                        String.format(
                            strings.budgetExceededAlertMsg,
                            catName,
                            formatCurrency(event.spentAmount, strings)
                        )
                    } else {
                        String.format(
                            strings.budgetApproachingAlertMsg,
                            catName,
                            event.percentUsed
                        )
                    }
                    snackbarHostState.showSnackbar(
                        message = msg,
                        duration = SnackbarDuration.Long
                    )
                }
            }
        }
    }

    val successData = (dashboardState as? UiState.Success)?.data

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            FlockDrawerSheetContent(
                farmName = successData?.activeFarm?.name.orEmpty(),
                navItems = allNavItems,
                currentRoute = currentRoute,
                pendingApprovalCount = successData?.summary?.pendingApprovalCount ?: 0,
                prefs = prefs,
                isAdminView = isAdminView,
                isSindhi = isSindhi,
                strings = strings,
                onNavigate = { route ->
                    navigateToRoute(route)
                    scope.launch { drawerState.close() }
                },
                onSwitchUserLock = {
                    scope.launch { drawerState.close() }
                    viewModel.lockWorkspace()
                },
                onOpenChangePins = {
                    scope.launch { drawerState.close() }
                    showChangePinsDialog = true
                },
                onToggleLanguage = {
                    viewModel.toggleLanguage()
                    scope.launch { drawerState.close() }
                },
                onToggleDarkMode = { viewModel.toggleDarkMode() },
                onDownloadPdf = {
                    scope.launch { drawerState.close() }
                    showPdfOptionsDialog = true
                },
                onOpenBackupSettings = {
                    scope.launch { drawerState.close() }
                    showBackupDialog = true
                },
                onOpenMonthlyBudgets = {
                    scope.launch { drawerState.close() }
                    showMonthlyBudgetDialog = true
                },
                onOpenFarmSettings = {
                    scope.launch { drawerState.close() }
                    showFarmDialog = true
                },
                onOpenStorageInfo = {
                    scope.launch { drawerState.close() }
                    showStorageInfoDialog = true
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
                                    text = if (isAdminView) strings.adminMode else strings.caretakerMode,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { viewModel.lockWorkspace() },
                                modifier = Modifier.testTag("topbar_lock_switch")
                            ) {
                                Icon(Icons.Rounded.Lock, contentDescription = strings.switchUserLock)
                            }
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
                            items = bottomNavItems,
                            currentRoute = currentRoute,
                            onNavigate = navigateToRoute,
                        )
                    }
                },
                floatingActionButton = {
                    val allowFab = successData?.activeFarm != null &&
                        (isAdminView || (currentRoute != ROUTE_SALARIES && currentRoute != ROUTE_LOANS))
                    if (allowFab) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                editingEntry = null
                                editorInitialCategory = when (currentRoute) {
                                    ROUTE_TRADE -> EntryCategory.LIVESTOCK_BUY
                                    ROUTE_SALARIES -> if (isAdminView) EntryCategory.SHEPHERD_ADVANCE else EntryCategory.EXPENSE
                                    ROUTE_MEDICAL -> EntryCategory.MEDICINE_VET
                                    ROUTE_LOANS -> if (isAdminView) EntryCategory.LOAN_TAKEN else EntryCategory.EXPENSE
                                    else -> EntryCategory.EXPENSE
                                }
                                editorInitialSubCategory = null
                                prefilledShepherdOrParty = ""
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
                            items = allNavItems,
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
                                    val openEditorForCategory: (EntryCategory, String) -> Unit = { cat, party ->
                                        editingEntry = null
                                        editorInitialCategory = cat
                                        editorInitialSubCategory = null
                                        prefilledShepherdOrParty = party
                                        showEditorDialog = true
                                    }
                                    val openEditorForFarmSubCategory: (ExpenseSubCategory) -> Unit = { sub ->
                                        editingEntry = null
                                        editorInitialCategory = EntryCategory.EXPENSE
                                        editorInitialSubCategory = sub
                                        prefilledShepherdOrParty = ""
                                        showEditorDialog = true
                                    }
                                    val openEditorForEntry: (LedgerEntry) -> Unit = { entry ->
                                        if (isAdminView) {
                                            editingEntry = entry
                                            editorInitialCategory = entry.parsedCategory()
                                            editorInitialSubCategory = entry.parsedSubCategory()
                                            prefilledShepherdOrParty = entry.shepherdName
                                            showEditorDialog = true
                                        }
                                    }
                                    val approveEntry: (String) -> Unit = { id ->
                                        viewModel.approveEntry(
                                            entryId = id,
                                            approvedMsg = strings.entryApprovedMsg,
                                            autoBackupMessage = strings.autoBackupTriggeredMsg,
                                        )
                                    }
                                    val rejectEntry: (String) -> Unit = { id ->
                                        viewModel.rejectEntry(id, strings.entryRejectedMsg)
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
                                                budgetStatus = state.data.budgetStatus,
                                                entriesSinceBackup = prefs.entriesSinceBackup,
                                                isAdminView = isAdminView,
                                                strings = strings,
                                                postUpdateBanner = postUpdateBanner,
                                                availableUpdate = availableUpdate,
                                                onQuickAddCategory = { cat -> openEditorForCategory(cat, "") },
                                                onQuickAddFarmSubCategory = openEditorForFarmSubCategory,
                                                onEditEntry = openEditorForEntry,
                                                onApproveEntry = approveEntry,
                                                onRejectEntry = rejectEntry,
                                                onViewReceipt = { viewingReceiptEntry = it },
                                                onDownloadPdf = { showPdfOptionsDialog = true },
                                                onOpenBackupSettings = { showBackupDialog = true },
                                                onOpenMonthlyBudgets = { showMonthlyBudgetDialog = true },
                                                onOpenLoansPage = { navigateToRoute(ROUTE_LOANS) },
                                                onSaveSafetyBackupFile = {
                                                    viewModel.saveSafetyBackupToDownloadsNow(strings.autoSafetyBackupSavedMsg)
                                                },
                                                onRestoreBackupFile = {
                                                    restoreBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                                },
                                                onDismissPostUpdateBanner = { viewModel.dismissPostUpdateBanner() },
                                                onDownloadAvailableUpdate = { url ->
                                                    viewModel.backupAndLaunchUpdate(url, strings.autoSafetyBackupSavedMsg)
                                                }
                                            )
                                        }
                                        composable(ROUTE_LEDGER) {
                                            LedgerScreen(
                                                entries = state.data.entries,
                                                isAdminView = isAdminView,
                                                strings = strings,
                                                onEditEntry = openEditorForEntry,
                                                onApproveEntry = approveEntry,
                                                onRejectEntry = rejectEntry,
                                                onViewReceipt = { viewingReceiptEntry = it }
                                            )
                                        }
                                        composable(ROUTE_TRADE) {
                                            TradeScreen(
                                                farm = farm,
                                                entries = state.data.entries,
                                                summary = state.data.summary,
                                                isAdminView = isAdminView,
                                                strings = strings,
                                                onAddTrade = { cat -> openEditorForCategory(cat, "") },
                                                onEditEntry = openEditorForEntry,
                                                onApproveEntry = approveEntry,
                                                onRejectEntry = rejectEntry,
                                                onViewReceipt = { viewingReceiptEntry = it }
                                            )
                                        }
                                        composable(ROUTE_SALARIES) {
                                            SalariesScreen(
                                                entries = state.data.entries,
                                                summary = state.data.summary,
                                                isAdminView = isAdminView,
                                                strings = strings,
                                                onAddShepherd = { viewModel.addShepherd(it) },
                                                onPayShepherd = { shepherdName ->
                                                    openEditorForCategory(EntryCategory.SHEPHERD_ADVANCE, shepherdName)
                                                },
                                                onEditEntry = openEditorForEntry,
                                                onViewReceipt = { viewingReceiptEntry = it }
                                            )
                                        }
                                        composable(ROUTE_MEDICAL) {
                                            MedicalScreen(
                                                entries = state.data.entries,
                                                summary = state.data.summary,
                                                budgetStatus = state.data.budgetStatus,
                                                isAdminView = isAdminView,
                                                strings = strings,
                                                onAddMedical = { openEditorForCategory(EntryCategory.MEDICINE_VET, "") },
                                                onOpenMonthlyBudgets = { showMonthlyBudgetDialog = true },
                                                onEditEntry = openEditorForEntry,
                                                onApproveEntry = approveEntry,
                                                onRejectEntry = rejectEntry,
                                                onViewReceipt = { viewingReceiptEntry = it }
                                            )
                                        }
                                        composable(ROUTE_LOANS) {
                                            LoansScreen(
                                                entries = state.data.entries,
                                                summary = state.data.summary,
                                                isAdminView = isAdminView,
                                                strings = strings,
                                                onAddLoan = openEditorForCategory,
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
            initialSubCategory = editorInitialSubCategory,
            allowedCategories = allowedCategories,
            editingEntry = editingEntry,
            shepherds = successData.activeFarm.shepherds,
            prefilledShepherd = prefilledShepherdOrParty,
            onDismiss = { showEditorDialog = false },
            onSave = { cat, subCat, title, amount, sheepCount, shepherd, note, receiptB64, dateMillis, payMethod ->
                viewModel.saveEntry(
                    editingEntry = editingEntry,
                    category = cat,
                    subCategory = subCat,
                    title = title,
                    amount = amount,
                    livestockCount = sheepCount,
                    shepherdName = shepherd,
                    note = note,
                    receiptBase64 = receiptB64,
                    entryDateMillis = dateMillis,
                    paymentMethod = payMethod,
                    savedMessage = strings.entrySavedMessage,
                    pendingMessage = strings.entryPendingMsg,
                    autoBackupMessage = strings.autoBackupTriggeredMsg,
                )
                showEditorDialog = false
            },
            onDelete = if (editingEntry != null && isAdminView) {
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

    if (showBackupDialog && isAdminView) {
        BackupSettingsDialog(
            prefs = prefs,
            strings = strings,
            onDismiss = { showBackupDialog = false },
            onSaveConfig = { sheetUrl, siaUrl, bucket, token, updateRepo ->
                viewModel.saveBackupConfiguration(sheetUrl, siaUrl, bucket, token, updateRepo)
            },
            onBackupNow = { viewModel.runBackupNow() },
            onExportCsv = { viewModel.exportCsv() },
            onExportFullBackup = {
                exportBackupLauncher.launch("FlockLedger_Backup_${System.currentTimeMillis()}.json")
            },
            onRestoreBackup = {
                restoreBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            },
            onCheckAppUpdate = { repoOrUrl ->
                viewModel.checkForAppUpdateNow(repoOrUrl, strings.noUpdateAvailableMsg)
            }
        )
    }

    if (showFarmDialog && successData?.activeFarm != null && isAdminView) {
        FarmSettingsDialog(
            farm = successData.activeFarm,
            allFarms = successData.farms,
            isAdmin = true,
            strings = strings,
            onDismiss = { showFarmDialog = false },
            onSelectFarm = { viewModel.selectFarm(it) },
            onCreateFarm = { name, initFlock -> viewModel.createFarm(name, initFlock) },
            onSaveFarmSettings = { name, initFlock, shepherds ->
                viewModel.updateFarmSettings(name, initFlock, shepherds)
            }
        )
    }

    if (showChangePinsDialog && isAdminView) {
        ChangePinsDialog(
            strings = strings,
            onDismiss = { showChangePinsDialog = false },
            onSavePins = { adminPin, caretakerPin ->
                viewModel.setupPins(adminPin, caretakerPin, initialRole = UserRole.ADMIN)
            }
        )
    }

    if (showStorageInfoDialog) {
        StorageInfoDialog(
            strings = strings,
            onExportFullBackup = {
                exportBackupLauncher.launch("FlockLedger_Backup_${System.currentTimeMillis()}.json")
            },
            onRestoreBackup = {
                restoreBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            },
            onDismiss = { showStorageInfoDialog = false }
        )
    }

    if (showPdfOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showPdfOptionsDialog = false },
            title = { Text(strings.downloadPdfReport, fontWeight = FontWeight.Bold) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.includePendingPdfLabel,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    )
                    Switch(
                        checked = includePendingInPdf,
                        onCheckedChange = { includePendingInPdf = it }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showPdfOptionsDialog = false
                    viewModel.generatePdfReport(includePending = includePendingInPdf)
                }) {
                    Text(strings.generatePdfAction)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPdfOptionsDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showMonthlyBudgetDialog && isAdminView) {
        MonthlyBudgetDialog(
            prefs = prefs,
            strings = strings,
            onDismiss = { showMonthlyBudgetDialog = false },
            onSaveBudgets = { feedCap, medCap, thresholdPct ->
                viewModel.saveMonthlyBudgets(
                    feedCap = feedCap,
                    medVetCap = medCap,
                    alertThresholdPercent = thresholdPct,
                    savedMessage = strings.budgetSavedMsg,
                )
            }
        )
    }
}
