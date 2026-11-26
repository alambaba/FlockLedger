package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.backup.AvailableAppUpdate
import com.example.data.backup.BackupExecutionResult
import com.example.data.backup.BackupManager
import com.example.data.model.ApprovalStatus
import com.example.data.model.BudgetAlertLevel
import com.example.data.model.EntryCategory
import com.example.data.model.ExpenseSubCategory
import com.example.data.model.Farm
import com.example.data.model.FarmSummary
import com.example.data.model.LedgerEntry
import com.example.data.model.MonthlyBudgetStatus
import com.example.data.model.PaymentMethod
import com.example.data.model.computeFarmSummary
import com.example.data.model.computeMonthlyBudgetStatus
import com.example.data.pdf.GeneratedPdfResult
import com.example.data.pdf.PdfReportGenerator
import com.example.data.preferences.AppPreferencesRepository
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserRole
import com.example.data.repository.FlockRepository
import com.example.i18n.AppLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface AppEvent {
    data class ShowMessage(val message: String) : AppEvent
    data class PdfReady(val result: GeneratedPdfResult) : AppEvent
    data class CsvReady(val uri: Uri) : AppEvent
    data class AutoBackupCompleted(val result: BackupExecutionResult) : AppEvent
    data class OpenUpdateUrl(val url: String) : AppEvent
    data class BudgetThresholdAlert(
        val isFeed: Boolean,
        val alertLevel: BudgetAlertLevel,
        val percentUsed: Int,
        val spentAmount: Double,
    ) : AppEvent
}

data class PostUpdateBanner(
    val versionName: String,
    val totalEntriesCount: Int,
)

data class DashboardData(
    val farms: List<Farm>,
    val activeFarm: Farm?,
    val entries: List<LedgerEntry>,
    val summary: FarmSummary,
    val budgetStatus: MonthlyBudgetStatus,
)

class FlockViewModel(
    private val repository: FlockRepository,
    private val prefsRepository: AppPreferencesRepository,
    private val backupManager: BackupManager,
    private val pdfGenerator: PdfReportGenerator,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = prefsRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UserPreferences()
        )

    private val _activeRole = MutableStateFlow<UserRole?>(null)
    val activeRole: StateFlow<UserRole?> = _activeRole.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _postUpdateBanner = MutableStateFlow<PostUpdateBanner?>(null)
    val postUpdateBanner: StateFlow<PostUpdateBanner?> = _postUpdateBanner.asStateFlow()

    private val _availableUpdate = MutableStateFlow<AvailableAppUpdate?>(null)
    val availableUpdate: StateFlow<AvailableAppUpdate?> = _availableUpdate.asStateFlow()

    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    private val farmsState: StateFlow<UiState<List<Farm>>> = repository.observeFarms()
        .map<List<Farm>, UiState<List<Farm>>> { UiState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing farms", error)
            emit(UiState.Error(error.message ?: "Failed to load farms"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val dashboardState: StateFlow<UiState<DashboardData>> =
        combine(farmsState, preferences) { fState, prefs -> fState to prefs }
            .flatMapLatest { (fState, prefs) ->
                when (fState) {
                    is UiState.Loading -> flowOf(UiState.Loading)
                    is UiState.Error -> flowOf(UiState.Error(fState.message))
                    is UiState.Success -> {
                        val farms = fState.data
                        val activeFarm = farms.firstOrNull { it.id == prefs.selectedFarmId }
                            ?: farms.firstOrNull()
                        if (activeFarm == null) {
                            flowOf(
                                UiState.Success(
                                    DashboardData(
                                        farms = emptyList(),
                                        activeFarm = null,
                                        entries = emptyList(),
                                        summary = computeFarmSummary(null, emptyList()),
                                        budgetStatus = computeMonthlyBudgetStatus(
                                            entries = emptyList(),
                                            feedCap = prefs.monthlyFeedBudgetCap,
                                            medVetCap = prefs.monthlyMedVetBudgetCap,
                                            alertThresholdPercent = prefs.budgetAlertThresholdPercent,
                                        ),
                                    )
                                )
                            )
                        } else {
                            repository.observeFarmEntries(activeFarm.id)
                                .map<List<LedgerEntry>, UiState<DashboardData>> { entries ->
                                    UiState.Success(
                                        DashboardData(
                                            farms = farms,
                                            activeFarm = activeFarm,
                                            entries = entries,
                                            summary = computeFarmSummary(activeFarm, entries),
                                            budgetStatus = computeMonthlyBudgetStatus(
                                                entries = entries,
                                                feedCap = prefs.monthlyFeedBudgetCap,
                                                medVetCap = prefs.monthlyMedVetBudgetCap,
                                                alertThresholdPercent = prefs.budgetAlertThresholdPercent,
                                            ),
                                        )
                                    )
                                }
                                .catch { error ->
                                    if (error is CancellationException) throw error
                                    Log.w(TAG, "Error observing entries", error)
                                    emit(UiState.Error(error.message ?: "Failed to load ledger entries"))
                                }
                        }
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    init {
        viewModelScope.launch {
            repository.ensureDefaultFarm()
            val prefs = prefsRepository.preferencesFlow.first()
            val entryCount = repository.countAllEntries()
            val currentCode = BuildConfig.VERSION_CODE.toLong()
            val currentName = BuildConfig.VERSION_NAME
            val isAppUpdated = (prefs.lastSeenVersionCode in 1L until currentCode) ||
                (prefs.lastSeenVersionCode == 0L && entryCount > 0)

            if (isAppUpdated) {
                refreshSafetyMirrorInternal()
                _postUpdateBanner.value = PostUpdateBanner(
                    versionName = currentName,
                    totalEntriesCount = entryCount,
                )
            }
            if (prefs.lastSeenVersionCode != currentCode || prefs.lastSeenVersionName != currentName) {
                prefsRepository.markVersionSeen(currentCode, currentName)
            }
            if (prefs.updateSourceRepo.isNotBlank()) {
                _availableUpdate.value = backupManager.checkAppUpdate(
                    repoOrUrl = prefs.updateSourceRepo,
                    currentVersionCode = currentCode,
                    currentVersionName = currentName,
                )
            }
        }
    }

    private suspend fun refreshSafetyMirrorInternal() {
        val (farms, entries) = repository.getAllFarmsAndEntries()
        backupManager.saveSafetySnapshotEverywhere(farms, entries)
    }

    fun retryInitialization() {
        viewModelScope.launch {
            _isBusy.value = true
            repository.ensureDefaultFarm()
            _isBusy.value = false
        }
    }

    fun setupPins(adminPin: String, caretakerPin: String, initialRole: UserRole = UserRole.ADMIN) {
        viewModelScope.launch {
            prefsRepository.savePins(adminPin, caretakerPin)
            prefsRepository.setPreferCaretakerView(initialRole == UserRole.CARETAKER)
            _activeRole.value = initialRole
        }
    }

    fun unlockWithPin(pin: String): UserRole? {
        val role = AppPreferencesRepository.resolveRoleForPin(pin, preferences.value) ?: return null
        _activeRole.value = role
        viewModelScope.launch {
            prefsRepository.setPreferCaretakerView(role == UserRole.CARETAKER)
        }
        return role
    }

    fun lockWorkspace() {
        _activeRole.value = null
    }

    fun toggleLanguage() {
        viewModelScope.launch {
            val next = if (preferences.value.language == AppLanguage.SINDHI) {
                AppLanguage.ENGLISH
            } else {
                AppLanguage.SINDHI
            }
            prefsRepository.setLanguage(next)
        }
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            prefsRepository.setDarkMode(!preferences.value.darkMode)
        }
    }

    fun selectFarm(farmId: String) {
        viewModelScope.launch {
            prefsRepository.setSelectedFarmId(farmId)
        }
    }

    fun createFarm(name: String, initialFlockCount: Long) {
        if (_activeRole.value != UserRole.ADMIN) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.createFarm(name, initialFlockCount)
            _isBusy.value = false
            res.onSuccess { farmId ->
                prefsRepository.setSelectedFarmId(farmId)
                _events.tryEmit(AppEvent.ShowMessage("Farm created"))
            }.onFailure { err ->
                _events.tryEmit(AppEvent.ShowMessage(err.localizedMessage ?: "Could not create farm"))
            }
        }
    }

    fun saveEntry(
        editingEntry: LedgerEntry?,
        category: EntryCategory,
        subCategory: String,
        title: String,
        amount: Double,
        livestockCount: Long,
        shepherdName: String,
        note: String,
        receiptBase64: String?,
        entryDateMillis: Long,
        paymentMethod: PaymentMethod = PaymentMethod.CASH,
        savedMessage: String,
        pendingMessage: String,
        autoBackupMessage: String,
    ) {
        val currentData = (dashboardState.value as? UiState.Success)?.data ?: return
        val farm = currentData.activeFarm ?: return
        val role = _activeRole.value ?: return
        val isAdmin = role == UserRole.ADMIN

        // Caretaker can only submit EXPENSE, MEDICINE_VET, LIVESTOCK_BUY, LIVESTOCK_SELL
        val allowedForCaretaker = setOf(
            EntryCategory.EXPENSE,
            EntryCategory.MEDICINE_VET,
            EntryCategory.LIVESTOCK_BUY,
            EntryCategory.LIVESTOCK_SELL,
        )
        if (!isAdmin && (editingEntry != null || category !in allowedForCaretaker)) {
            return
        }

        val loggerRole = if (isAdmin) "Admin" else "Caretaker"
        val status = if (isAdmin) ApprovalStatus.APPROVED else ApprovalStatus.PENDING

        viewModelScope.launch {
            _isBusy.value = true
            val result = if (editingEntry == null) {
                repository.addEntry(
                    farm = farm,
                    createdByName = loggerRole,
                    category = category,
                    subCategory = subCategory,
                    title = title,
                    amount = amount,
                    livestockCount = livestockCount,
                    shepherdName = shepherdName,
                    note = note,
                    receiptBase64 = receiptBase64,
                    entryDateMillis = entryDateMillis,
                    approvalStatus = status,
                    paymentMethod = paymentMethod,
                )
            } else {
                repository.updateEntry(
                    farm = farm,
                    existingEntry = editingEntry,
                    category = category,
                    subCategory = subCategory,
                    title = title,
                    amount = amount,
                    livestockCount = livestockCount,
                    shepherdName = shepherdName,
                    note = note,
                    receiptBase64 = receiptBase64,
                    entryDateMillis = entryDateMillis,
                    paymentMethod = paymentMethod,
                )
            }
            _isBusy.value = false

            result.onSuccess {
                refreshSafetyMirrorInternal()
                _events.tryEmit(AppEvent.ShowMessage(if (status == ApprovalStatus.PENDING) pendingMessage else savedMessage))
                if (status == ApprovalStatus.APPROVED) {
                    emitBudgetAlertIfCrossed(farm.id, category, subCategory)
                }
                if (editingEntry == null && status == ApprovalStatus.APPROVED) {
                    val count = prefsRepository.incrementEntryCounter()
                    if (count >= 5) {
                        _events.tryEmit(AppEvent.ShowMessage(autoBackupMessage))
                        triggerBackupInternal(resetCounter = true)
                    }
                }
            }.onFailure { err ->
                _events.tryEmit(AppEvent.ShowMessage(err.localizedMessage ?: "Failed to save entry"))
            }
        }
    }

    fun approveEntry(entryId: String, approvedMsg: String, autoBackupMessage: String) {
        if (_activeRole.value != UserRole.ADMIN) return
        val currentData = (dashboardState.value as? UiState.Success)?.data
        val targetEntry = currentData?.entries?.firstOrNull { it.id == entryId }
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.setApprovalStatus(entryId, ApprovalStatus.APPROVED)
            _isBusy.value = false
            res.onSuccess {
                refreshSafetyMirrorInternal()
                _events.tryEmit(AppEvent.ShowMessage(approvedMsg))
                if (targetEntry != null) {
                    emitBudgetAlertIfCrossed(
                        farmId = targetEntry.farmId,
                        category = targetEntry.parsedCategory(),
                        subCategory = targetEntry.subCategory,
                    )
                }
                val count = prefsRepository.incrementEntryCounter()
                if (count >= 5) {
                    _events.tryEmit(AppEvent.ShowMessage(autoBackupMessage))
                    triggerBackupInternal(resetCounter = true)
                }
            }.onFailure { err ->
                _events.tryEmit(AppEvent.ShowMessage(err.localizedMessage ?: "Approval failed"))
            }
        }
    }

    fun saveMonthlyBudgets(
        feedCap: Double,
        medVetCap: Double,
        alertThresholdPercent: Int,
        savedMessage: String,
    ) {
        if (_activeRole.value != UserRole.ADMIN) return
        viewModelScope.launch {
            prefsRepository.saveMonthlyBudgets(feedCap, medVetCap, alertThresholdPercent)
            _events.tryEmit(AppEvent.ShowMessage(savedMessage))
        }
    }

    private suspend fun emitBudgetAlertIfCrossed(
        farmId: String,
        category: EntryCategory,
        subCategory: String,
    ) {
        val isFeed = category == EntryCategory.EXPENSE &&
            ExpenseSubCategory.fromRaw(subCategory) == ExpenseSubCategory.FEED
        val isMedVet = category == EntryCategory.MEDICINE_VET ||
            (category == EntryCategory.EXPENSE &&
                ExpenseSubCategory.fromRaw(subCategory) == ExpenseSubCategory.TREATMENT)
        if (!isFeed && !isMedVet) return

        val prefs = preferences.value
        val entries = repository.observeFarmEntries(farmId).first()
        val status = computeMonthlyBudgetStatus(
            entries = entries,
            feedCap = prefs.monthlyFeedBudgetCap,
            medVetCap = prefs.monthlyMedVetBudgetCap,
            alertThresholdPercent = prefs.budgetAlertThresholdPercent,
        )
        val catStatus = if (isFeed) status.feedBudget else status.medVetBudget
        if (catStatus.alertLevel != BudgetAlertLevel.NONE) {
            _events.tryEmit(
                AppEvent.BudgetThresholdAlert(
                    isFeed = isFeed,
                    alertLevel = catStatus.alertLevel,
                    percentUsed = catStatus.percentUsed,
                    spentAmount = catStatus.spentThisMonth,
                )
            )
        }
    }

    fun rejectEntry(entryId: String, rejectedMsg: String) {
        if (_activeRole.value != UserRole.ADMIN) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.setApprovalStatus(entryId, ApprovalStatus.REJECTED)
            _isBusy.value = false
            res.onSuccess {
                refreshSafetyMirrorInternal()
                _events.tryEmit(AppEvent.ShowMessage(rejectedMsg))
            }.onFailure { err ->
                _events.tryEmit(AppEvent.ShowMessage(err.localizedMessage ?: "Reject failed"))
            }
        }
    }

    fun deleteEntry(entryId: String) {
        if (_activeRole.value != UserRole.ADMIN) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.deleteEntry(entryId)
            _isBusy.value = false
            res.onSuccess {
                refreshSafetyMirrorInternal()
            }.onFailure { err ->
                _events.tryEmit(AppEvent.ShowMessage(err.localizedMessage ?: "Delete failed"))
            }
        }
    }

    fun addShepherd(name: String) {
        if (_activeRole.value != UserRole.ADMIN) return
        val farm = (dashboardState.value as? UiState.Success)?.data?.activeFarm ?: return
        val clean = name.trim()
        if (clean.isEmpty() || clean in farm.shepherds) return
        viewModelScope.launch {
            val res = repository.updateFarmSettings(
                farm = farm,
                newName = farm.name,
                initialFlockCount = farm.initialFlockCount,
                shepherds = farm.shepherds + clean,
            )
            if (res.isSuccess) refreshSafetyMirrorInternal()
        }
    }

    fun updateFarmSettings(newName: String, initialFlockCount: Long, shepherds: List<String>) {
        if (_activeRole.value != UserRole.ADMIN) return
        val farm = (dashboardState.value as? UiState.Success)?.data?.activeFarm ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateFarmSettings(farm, newName, initialFlockCount, shepherds)
            _isBusy.value = false
            res.onSuccess {
                refreshSafetyMirrorInternal()
            }.onFailure { err ->
                _events.tryEmit(AppEvent.ShowMessage(err.localizedMessage ?: "Update failed"))
            }
        }
    }

    fun saveBackupConfiguration(
        googleSheetUrl: String,
        siaEndpointUrl: String,
        siaBucket: String,
        siaToken: String,
        updateSourceRepo: String = preferences.value.updateSourceRepo,
    ) {
        if (_activeRole.value != UserRole.ADMIN) return
        viewModelScope.launch {
            prefsRepository.saveBackupEndpoints(googleSheetUrl, siaEndpointUrl, siaBucket, siaToken)
            prefsRepository.saveUpdateSource(updateSourceRepo)
            if (updateSourceRepo.isNotBlank()) {
                _availableUpdate.value = backupManager.checkAppUpdate(
                    repoOrUrl = updateSourceRepo,
                    currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
                    currentVersionName = BuildConfig.VERSION_NAME,
                )
            } else {
                _availableUpdate.value = null
            }
            _events.tryEmit(AppEvent.ShowMessage("Backup configuration saved"))
        }
    }

    fun dismissPostUpdateBanner() {
        _postUpdateBanner.value = null
    }

    fun saveSafetyBackupToDownloadsNow(savedMsg: String) {
        viewModelScope.launch {
            _isBusy.value = true
            refreshSafetyMirrorInternal()
            _isBusy.value = false
            _events.tryEmit(AppEvent.ShowMessage(savedMsg))
        }
    }

    fun exportFullBackupToUri(uri: Uri, savedMsg: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val (farms, entries) = repository.getAllFarmsAndEntries()
            val res = backupManager.exportBackupToUri(uri, farms, entries)
            _isBusy.value = false
            res.onSuccess {
                _events.tryEmit(AppEvent.ShowMessage(savedMsg))
            }.onFailure { err ->
                _events.tryEmit(AppEvent.ShowMessage(err.localizedMessage ?: "Export failed"))
            }
        }
    }

    fun restoreBackupFromUri(uri: Uri, successTemplate: String, errorMsg: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val parsed = backupManager.readBackupFromUri(uri)
            parsed.onSuccess { (farms, entries) ->
                val restoreRes = repository.restoreBackupData(farms, entries)
                restoreRes.onSuccess { stats ->
                    refreshSafetyMirrorInternal()
                    _events.tryEmit(
                        AppEvent.ShowMessage(
                            String.format(successTemplate, stats.farmsRestored, stats.entriesRestored)
                        )
                    )
                }.onFailure {
                    _events.tryEmit(AppEvent.ShowMessage(errorMsg))
                }
            }.onFailure {
                _events.tryEmit(AppEvent.ShowMessage(errorMsg))
            }
            _isBusy.value = false
        }
    }

    fun checkForAppUpdateNow(repoOrUrl: String, noUpdateMsg: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val found = backupManager.checkAppUpdate(
                repoOrUrl = repoOrUrl,
                currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
                currentVersionName = BuildConfig.VERSION_NAME,
            )
            _availableUpdate.value = found
            _isBusy.value = false
            if (found == null) {
                _events.tryEmit(AppEvent.ShowMessage(noUpdateMsg))
            }
        }
    }

    fun backupAndLaunchUpdate(updateUrl: String, backupSavedMsg: String) {
        viewModelScope.launch {
            _isBusy.value = true
            refreshSafetyMirrorInternal()
            _isBusy.value = false
            _events.tryEmit(AppEvent.ShowMessage(backupSavedMsg))
            _events.tryEmit(AppEvent.OpenUpdateUrl(updateUrl))
        }
    }

    fun runBackupNow() {
        viewModelScope.launch {
            refreshSafetyMirrorInternal()
            triggerBackupInternal(resetCounter = true)
        }
    }

    private suspend fun triggerBackupInternal(resetCounter: Boolean) {
        val data = (dashboardState.value as? UiState.Success)?.data ?: return
        val farm = data.activeFarm ?: return
        _isBusy.value = true
        val result = backupManager.performBackup(
            farm = farm,
            entries = data.entries.filter { it.parsedApprovalStatus() == ApprovalStatus.APPROVED },
            summary = data.summary,
            prefs = preferences.value,
        )
        prefsRepository.recordBackupResult(
            timestampMillis = result.timestampMillis,
            summary = result.overallSummary,
            resetCounter = resetCounter,
        )
        _isBusy.value = false
        _events.tryEmit(AppEvent.AutoBackupCompleted(result))
    }

    fun generatePdfReport(includePending: Boolean = false) {
        val data = (dashboardState.value as? UiState.Success)?.data ?: return
        val farm = data.activeFarm ?: return
        val isSindhi = preferences.value.language == AppLanguage.SINDHI
        viewModelScope.launch {
            _isBusy.value = true
            val res = pdfGenerator.generateSummaryPdf(
                farm = farm,
                summary = data.summary,
                entries = data.entries,
                isSindhi = isSindhi,
                includePending = includePending,
            )
            _isBusy.value = false
            res.onSuccess { pdfResult ->
                _events.tryEmit(AppEvent.PdfReady(pdfResult))
            }.onFailure { err ->
                _events.tryEmit(AppEvent.ShowMessage(err.localizedMessage ?: "PDF error"))
            }
        }
    }

    fun exportCsv() {
        val data = (dashboardState.value as? UiState.Success)?.data ?: return
        val farm = data.activeFarm ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val uri = backupManager.exportCsvUri(
                farm,
                data.entries.filter { it.parsedApprovalStatus() == ApprovalStatus.APPROVED }
            )
            _isBusy.value = false
            _events.tryEmit(AppEvent.CsvReady(uri))
        }
    }

    companion object {
        private const val TAG = "FlockVM"

        fun createDependencies(context: Context): QuadrupleDeps {
            val appCtx = context.applicationContext
            return QuadrupleDeps(
                repository = FlockRepository(appCtx),
                prefsRepository = AppPreferencesRepository(appCtx),
                backupManager = BackupManager(appCtx),
                pdfGenerator = PdfReportGenerator(appCtx),
            )
        }
    }
}

data class QuadrupleDeps(
    val repository: FlockRepository,
    val prefsRepository: AppPreferencesRepository,
    val backupManager: BackupManager,
    val pdfGenerator: PdfReportGenerator,
)
