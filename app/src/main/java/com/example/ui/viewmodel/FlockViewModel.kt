package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.backup.BackupExecutionResult
import com.example.data.backup.BackupManager
import com.example.data.model.EntryCategory
import com.example.data.model.Farm
import com.example.data.model.FarmSummary
import com.example.data.model.LedgerEntry
import com.example.data.model.computeFarmSummary
import com.example.data.pdf.GeneratedPdfResult
import com.example.data.pdf.PdfReportGenerator
import com.example.data.preferences.AppPreferencesRepository
import com.example.data.preferences.UserPreferences
import com.example.data.repository.FlockRepository
import com.example.i18n.AppLanguage
import com.google.firebase.firestore.FirebaseFirestoreException
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
}

data class DashboardData(
    val farms: List<Farm>,
    val activeFarm: Farm?,
    val entries: List<LedgerEntry>,
    val summary: FarmSummary,
    val isUserAdminOnActiveFarm: Boolean,
)

class FlockViewModel(
    private val repository: FlockRepository,
    private val prefsRepository: AppPreferencesRepository,
    private val backupManager: BackupManager,
    private val pdfGenerator: PdfReportGenerator,
    private val currentUserId: String,
    private val currentUserDisplayName: String?,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = prefsRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UserPreferences()
        )

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    private val farmsState: StateFlow<UiState<List<Farm>>> = repository.observeUserFarms(currentUserId)
        .map<List<Farm>, UiState<List<Farm>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing farms", root)
            emit(UiState.Error(root.message ?: "Failed to load farms"))
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
                                        isUserAdminOnActiveFarm = true,
                                    )
                                )
                            )
                        } else {
                            repository.observeFarmEntries(activeFarm.id, currentUserId)
                                .map<List<LedgerEntry>, UiState<DashboardData>> { entries ->
                                    if (activeFarm.isUserAdmin(currentUserId)) {
                                        viewModelScope.launch {
                                            repository.syncMemberUidsToEntries(activeFarm, entries)
                                        }
                                    }
                                    UiState.Success(
                                        DashboardData(
                                            farms = farms,
                                            activeFarm = activeFarm,
                                            entries = entries,
                                            summary = computeFarmSummary(activeFarm, entries),
                                            isUserAdminOnActiveFarm = activeFarm.isUserAdmin(currentUserId),
                                        )
                                    )
                                }
                                .catch { error ->
                                    val firestoreError = generateSequence(error) { it.cause }
                                        .filterIsInstance<FirebaseFirestoreException>()
                                        .firstOrNull()
                                    if (firestoreError == null && error is CancellationException) throw error
                                    val root = firestoreError ?: error
                                    Log.w(TAG, "Error observing entries", root)
                                    emit(UiState.Error(root.message ?: "Failed to load ledger entries"))
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
            repository.ensureDefaultFarm(currentUserId, currentUserDisplayName)
        }
    }

    fun retryInitialization() {
        viewModelScope.launch {
            _isBusy.value = true
            repository.ensureDefaultFarm(currentUserId, currentUserDisplayName)
            _isBusy.value = false
        }
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

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { prefsRepository.setLanguage(language) }
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            prefsRepository.setDarkMode(!preferences.value.darkMode)
        }
    }

    fun setPreferCaretakerView(preferCaretaker: Boolean) {
        viewModelScope.launch {
            prefsRepository.setPreferCaretakerView(preferCaretaker)
        }
    }

    fun selectFarm(farmId: String) {
        viewModelScope.launch {
            prefsRepository.setSelectedFarmId(farmId)
        }
    }

    fun saveEntry(
        editingEntryId: String?,
        category: EntryCategory,
        title: String,
        amount: Double,
        livestockCount: Long,
        shepherdName: String,
        note: String,
        receiptBase64: String?,
        entryDateMillis: Long,
        savedMessage: String,
        autoBackupMessage: String,
    ) {
        val currentData = (dashboardState.value as? UiState.Success)?.data ?: return
        val farm = currentData.activeFarm ?: return

        viewModelScope.launch {
            _isBusy.value = true
            val result = if (editingEntryId == null) {
                repository.addEntry(
                    farm = farm,
                    category = category,
                    title = title,
                    amount = amount,
                    livestockCount = livestockCount,
                    shepherdName = shepherdName,
                    note = note,
                    receiptBase64 = receiptBase64,
                    entryDateMillis = entryDateMillis,
                )
            } else {
                repository.updateEntry(
                    farm = farm,
                    entryId = editingEntryId,
                    category = category,
                    title = title,
                    amount = amount,
                    livestockCount = livestockCount,
                    shepherdName = shepherdName,
                    note = note,
                    receiptBase64 = receiptBase64,
                    entryDateMillis = entryDateMillis,
                )
            }
            _isBusy.value = false

            result.onSuccess {
                _events.tryEmit(AppEvent.ShowMessage(savedMessage))
                if (editingEntryId == null) {
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

    fun deleteEntry(entryId: String) {
        val farm = (dashboardState.value as? UiState.Success)?.data?.activeFarm ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.deleteEntry(farm, entryId)
            _isBusy.value = false
            res.onFailure { err ->
                _events.tryEmit(AppEvent.ShowMessage(err.localizedMessage ?: "Delete failed"))
            }
        }
    }

    fun addShepherd(name: String) {
        val farm = (dashboardState.value as? UiState.Success)?.data?.activeFarm ?: return
        val clean = name.trim()
        if (clean.isEmpty() || clean in farm.shepherds) return
        viewModelScope.launch {
            repository.updateFarmSettings(
                farm = farm,
                newName = farm.name,
                initialFlockCount = farm.initialFlockCount,
                shepherds = farm.shepherds + clean,
            )
        }
    }

    fun updateFarmSettings(newName: String, initialFlockCount: Long, shepherds: List<String>) {
        val farm = (dashboardState.value as? UiState.Success)?.data?.activeFarm ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateFarmSettings(farm, newName, initialFlockCount, shepherds)
            _isBusy.value = false
            res.onFailure { err ->
                _events.tryEmit(AppEvent.ShowMessage(err.localizedMessage ?: "Update failed"))
            }
        }
    }

    fun joinFarmByCode(code: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.joinFarmByCode(code)
            _isBusy.value = false
            res.onSuccess { farmId ->
                prefsRepository.setSelectedFarmId(farmId)
                _events.tryEmit(AppEvent.ShowMessage("Joined farm!"))
            }.onFailure { err ->
                _events.tryEmit(AppEvent.ShowMessage(err.localizedMessage ?: "Could not join farm"))
            }
        }
    }

    fun syncMemberAccess() {
        val data = (dashboardState.value as? UiState.Success)?.data ?: return
        val farm = data.activeFarm ?: return
        viewModelScope.launch {
            repository.syncMemberUidsToEntries(farm, data.entries)
        }
    }

    fun saveBackupConfiguration(
        googleSheetUrl: String,
        siaEndpointUrl: String,
        siaBucket: String,
        siaToken: String,
    ) {
        viewModelScope.launch {
            prefsRepository.saveBackupEndpoints(googleSheetUrl, siaEndpointUrl, siaBucket, siaToken)
            _events.tryEmit(AppEvent.ShowMessage("Backup configuration saved"))
        }
    }

    fun runBackupNow() {
        viewModelScope.launch {
            triggerBackupInternal(resetCounter = true)
        }
    }

    private suspend fun triggerBackupInternal(resetCounter: Boolean) {
        val data = (dashboardState.value as? UiState.Success)?.data ?: return
        val farm = data.activeFarm ?: return
        _isBusy.value = true
        val result = backupManager.performBackup(
            farm = farm,
            entries = data.entries,
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

    fun generatePdfReport() {
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
            val uri = backupManager.exportCsvUri(farm, data.entries)
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
