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
import com.example.data.counter.SheepCountRecord
import com.example.data.counter.SheepCountStorageManager
import com.example.data.model.ApprovalStatus
import com.example.data.model.BudgetAlertLevel
import com.example.data.model.EntryCategory
import com.example.data.model.ExpenseSubCategory
import com.example.data.model.Farm
import com.example.data.model.FarmSummary
import com.example.data.model.LedgerEntry
import com.example.data.model.MonthlyBudgetStatus
import com.example.data.model.PaymentMethod
import com.example.data.model.ReportComment
import com.example.data.model.computeFarmSummary
import com.example.data.model.computeMonthlyBudgetStatus
import com.example.data.pdf.GeneratedPdfResult
import com.example.data.pdf.PdfReportGenerator
import com.example.data.preferences.AppPreferencesRepository
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserRole
import com.example.data.remote.CloudSyncStatus
import com.example.data.remote.FirestoreSyncRepository
import com.example.data.remote.GoogleAuthManager
import com.example.data.remote.GoogleSignInCancelledException
import com.example.data.repository.FlockRepository
import com.example.i18n.AppLanguage
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    data class PrintPdfReady(val internalFilePath: String, val jobName: String) : AppEvent
    data class CsvReady(val uri: Uri) : AppEvent
    data class SheepCountFileReady(val uri: Uri, val fileName: String) : AppEvent
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
    val reportComments: List<ReportComment> = emptyList(),
)

class FlockViewModel(
    private val repository: FlockRepository,
    private val prefsRepository: AppPreferencesRepository,
    private val backupManager: BackupManager,
    private val pdfGenerator: PdfReportGenerator,
    private val authManager: GoogleAuthManager,
    private val firestoreSync: FirestoreSyncRepository,
    private val sheepCountManager: SheepCountStorageManager,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = prefsRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UserPreferences()
        )

    val authUser: StateFlow<FirebaseUser?> = authManager.authUserFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = authManager.auth.currentUser
        )

    val cloudSyncStatus: StateFlow<CloudSyncStatus> = firestoreSync.syncStatus

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    private val _signInError = MutableStateFlow<String?>(null)
    val signInError: StateFlow<String?> = _signInError.asStateFlow()

    private val _activeRole = MutableStateFlow<UserRole?>(null)
    val activeRole: StateFlow<UserRole?> = _activeRole.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _postUpdateBanner = MutableStateFlow<PostUpdateBanner?>(null)
    val postUpdateBanner: StateFlow<PostUpdateBanner?> = _postUpdateBanner.asStateFlow()

    private val _availableUpdate = MutableStateFlow<AvailableAppUpdate?>(null)
    val availableUpdate: StateFlow<AvailableAppUpdate?> = _availableUpdate.asStateFlow()

    private val _sheepCountHistory = MutableStateFlow<List<SheepCountRecord>>(emptyList())
    val sheepCountHistory: StateFlow<List<SheepCountRecord>> = _sheepCountHistory.asStateFlow()

    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    private var silentSignInAttempted = false

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
                            combine(
                                repository.observeFarmEntries(activeFarm.id),
                                repository.observeCommentsForFarm(activeFarm.id)
                            ) { entries, comments ->
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
                                        reportComments = comments,
                                    )
                                ) as UiState<DashboardData>
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
            refreshSheepCountHistory()
        }

        // Auth-gated Firestore sync & realtime listeners: automatically start on sign-in and tear down on sign-out
        viewModelScope.launch {
            authUser
                .map { it?.uid }
                .distinctUntilChanged()
                .collectLatest { uid ->
                    val currentUser = authManager.auth.currentUser
                    if (uid == null || currentUser == null) {
                        firestoreSync.setSignedOut()
                        return@collectLatest
                    }
                    repository.ensureDefaultFarm()
                    repository.syncWithCloud(currentUser)
                    coroutineScope {
                        launch {
                            repository.observeRemoteFarms(uid)?.collect { remoteFarms ->
                                repository.mergeRemoteFarms(remoteFarms)
                            }
                        }
                        launch {
                            combine(
                                farmsState.map { (it as? UiState.Success)?.data ?: emptyList() },
                                preferences.map { it.selectedFarmId }
                            ) { farms, selectedId ->
                                farms.firstOrNull { it.id == selectedId }?.id ?: farms.firstOrNull()?.id
                            }
                                .distinctUntilChanged()
                                .collectLatest { farmId ->
                                    if (farmId.isNullOrBlank()) return@collectLatest
                                    coroutineScope {
                                        launch {
                                            repository.observeRemoteEntries(uid, farmId)?.collect { entries ->
                                                repository.mergeRemoteEntries(entries)
                                            }
                                        }
                                        launch {
                                            repository.observeRemoteComments(uid, farmId)?.collect { comments ->
                                                repository.mergeRemoteComments(comments)
                                            }
                                        }
                                    }
                                }
                        }
                    }
                }
        }
    }

    fun attemptSilentSignIn(activityContext: Context) {
        if (silentSignInAttempted || authManager.auth.currentUser != null) return
        silentSignInAttempted = true
        viewModelScope.launch {
            _isSigningIn.value = true
            authManager.attemptSilentSignIn(activityContext)
            _isSigningIn.value = false
        }
    }

    fun signInWithGoogle(activityContext: Context) {
        if (_isSigningIn.value) return
        viewModelScope.launch {
            _isSigningIn.value = true
            _signInError.value = null
            val res = authManager.signInWithGoogle(activityContext)
            _isSigningIn.value = false
            res.onFailure { err ->
                if (err !is GoogleSignInCancelledException) {
                    _signInError.value = err.localizedMessage ?: "Google Sign-In failed"
                }
            }
        }
    }

    fun signOutGoogle() {
        viewModelScope.launch {
            _activeRole.value = null
            firestoreSync.setSignedOut()
            authManager.signOut()
        }
    }

    fun syncCloudNow(doneMsgTemplate: String, errorPrefix: String) {
        val user = authManager.auth.currentUser ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.syncWithCloud(user)
            _isBusy.value = false
            res.onSuccess { (uploaded, downloaded) ->
                _events.tryEmit(
                    AppEvent.ShowMessage(String.format(doneMsgTemplate, uploaded, downloaded))
                )
            }.onFailure { err ->
                _events.tryEmit(
                    AppEvent.ShowMessage("$errorPrefix ${err.localizedMessage ?: "Unknown error"}")
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
            authManager.auth.currentUser?.let { repository.syncWithCloud(it) }
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

        // Caretaker can submit EXPENSE, MEDICINE_VET, LIVESTOCK_BUY, LIVESTOCK_SELL, and SHEPHERD_SALARY
        val allowedForCaretaker = setOf(
            EntryCategory.EXPENSE,
            EntryCategory.MEDICINE_VET,
            EntryCategory.LIVESTOCK_BUY,
            EntryCategory.LIVESTOCK_SELL,
            EntryCategory.SHEPHERD_SALARY,
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

    fun submitReportComment(
        entryId: String?,
        entryTitleSnapshot: String,
        commentText: String,
        savedMessage: String,
    ) {
        val currentData = (dashboardState.value as? UiState.Success)?.data ?: return
        val farm = currentData.activeFarm ?: return
        val role = _activeRole.value ?: return
        val roleName = if (role == UserRole.ADMIN) "Admin" else "Caretaker"
        viewModelScope.launch {
            val res = repository.addReportComment(
                farmId = farm.id,
                entryId = entryId,
                entryTitleSnapshot = entryTitleSnapshot,
                authorRole = roleName,
                commentText = commentText,
            )
            res.onSuccess {
                _events.tryEmit(AppEvent.ShowMessage(savedMessage))
            }.onFailure { err ->
                _events.tryEmit(AppEvent.ShowMessage(err.localizedMessage ?: "Comment failed"))
            }
        }
    }

    fun resolveReportComment(commentId: String, adminResponse: String, resolvedMessage: String) {
        if (_activeRole.value != UserRole.ADMIN) return
        viewModelScope.launch {
            val res = repository.resolveReportComment(commentId, adminResponse)
            res.onSuccess {
                _events.tryEmit(AppEvent.ShowMessage(resolvedMessage))
            }.onFailure { err ->
                _events.tryEmit(AppEvent.ShowMessage(err.localizedMessage ?: "Update failed"))
            }
        }
    }

    fun deleteReportComment(commentId: String) {
        if (_activeRole.value != UserRole.ADMIN) return
        viewModelScope.launch {
            repository.deleteReportComment(commentId)
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

    fun generatePdfReport(
        includePending: Boolean = false,
        customEntries: List<LedgerEntry>? = null,
        customSummary: FarmSummary? = null,
        periodLabel: String = "",
        triggerPrint: Boolean = false,
    ) {
        val data = (dashboardState.value as? UiState.Success)?.data ?: return
        val farm = data.activeFarm ?: return
        val isSindhi = preferences.value.language == AppLanguage.SINDHI
        val targetEntries = customEntries ?: data.entries
        val targetSummary = customSummary ?: data.summary
        viewModelScope.launch {
            _isBusy.value = true
            val res = pdfGenerator.generateSummaryPdf(
                farm = farm,
                summary = targetSummary,
                entries = targetEntries,
                isSindhi = isSindhi,
                includePending = includePending,
                periodLabel = periodLabel,
            )
            _isBusy.value = false
            res.onSuccess { pdfResult ->
                if (triggerPrint && pdfResult.internalFilePath.isNotBlank()) {
                    val jobTitle = if (periodLabel.isNotBlank()) {
                        "${farm.name.ifBlank { "FlockLedger" }} - $periodLabel"
                    } else {
                        "${farm.name.ifBlank { "FlockLedger" }} Report"
                    }
                    _events.tryEmit(AppEvent.PrintPdfReady(pdfResult.internalFilePath, jobTitle))
                } else {
                    _events.tryEmit(AppEvent.PdfReady(pdfResult))
                }
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

    fun refreshSheepCountHistory() {
        viewModelScope.launch {
            val selectedFarmId = (dashboardState.value as? UiState.Success)?.data?.activeFarm?.id
            _sheepCountHistory.value = sheepCountManager.loadHistory(selectedFarmId)
        }
    }

    suspend fun persistCounterCameraPhoto(uri: Uri): String? {
        return sheepCountManager.copyCameraPhotoToPersistentFile(uri)
    }

    fun saveSheepCountResult(
        countedSheep: Int,
        note: String,
        photoFilePath: String?,
        savedMsg: String,
        errorMsg: String,
    ) {
        val data = (dashboardState.value as? UiState.Success)?.data ?: return
        val farm = data.activeFarm ?: return
        val recordedBy = if (_activeRole.value == UserRole.ADMIN) "Admin" else "Caretaker"
        viewModelScope.launch {
            _isBusy.value = true
            val res = sheepCountManager.saveCountResultToFile(
                farmId = farm.id,
                farmName = farm.name,
                countedSheep = countedSheep,
                expectedSheep = data.summary.currentFlockCount.toInt(),
                note = note,
                photoFilePath = photoFilePath,
                recordedBy = recordedBy,
            )
            _isBusy.value = false
            res.onSuccess {
                _sheepCountHistory.value = sheepCountManager.loadHistory(farm.id)
                _events.tryEmit(AppEvent.ShowMessage(savedMsg))
            }.onFailure {
                _events.tryEmit(AppEvent.ShowMessage(errorMsg))
            }
        }
    }

    fun shareSheepCountFile(filePath: String) {
        val uri = sheepCountManager.getShareUri(filePath) ?: return
        _events.tryEmit(AppEvent.SheepCountFileReady(uri, java.io.File(filePath).name))
    }

    fun showTransientMessage(message: String) {
        _events.tryEmit(AppEvent.ShowMessage(message))
    }

    companion object {
        private const val TAG = "FlockVM"

        fun createDependencies(context: Context): QuadrupleDeps {
            val appCtx = context.applicationContext
            val authManager = GoogleAuthManager(appCtx)
            val firestoreSync = FirestoreSyncRepository(appCtx, authManager.auth)
            return QuadrupleDeps(
                repository = FlockRepository(appCtx, firestoreSync),
                prefsRepository = AppPreferencesRepository(appCtx),
                backupManager = BackupManager(appCtx),
                pdfGenerator = PdfReportGenerator(appCtx),
                authManager = authManager,
                firestoreSync = firestoreSync,
                sheepCountManager = SheepCountStorageManager(appCtx),
            )
        }
    }
}

data class QuadrupleDeps(
    val repository: FlockRepository,
    val prefsRepository: AppPreferencesRepository,
    val backupManager: BackupManager,
    val pdfGenerator: PdfReportGenerator,
    val authManager: GoogleAuthManager,
    val firestoreSync: FirestoreSyncRepository,
    val sheepCountManager: SheepCountStorageManager,
)
