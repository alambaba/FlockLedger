package com.example.data.repository

import android.content.Context
import com.example.data.local.FarmDao
import com.example.data.local.FarmEntity
import com.example.data.local.FlockDatabase
import com.example.data.local.LedgerEntryDao
import com.example.data.local.LedgerEntryEntity
import com.example.data.local.ReportCommentDao
import com.example.data.local.ReportCommentEntity
import com.example.data.model.ApprovalStatus
import com.example.data.model.CommentStatus
import com.example.data.model.EntryCategory
import com.example.data.model.Farm
import com.example.data.model.LedgerEntry
import com.example.data.model.PaymentMethod
import com.example.data.model.ReportComment
import com.example.data.remote.CloudSyncStatus
import com.example.data.remote.FirestoreSyncRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FlockRepository(
    private val farmDao: FarmDao,
    private val entryDao: LedgerEntryDao,
    private val commentDao: ReportCommentDao,
    private val firestoreSync: FirestoreSyncRepository? = null,
) {
    constructor(context: Context, firestoreSync: FirestoreSyncRepository? = null) : this(
        FlockDatabase.getInstance(context).farmDao(),
        FlockDatabase.getInstance(context).ledgerEntryDao(),
        FlockDatabase.getInstance(context).reportCommentDao(),
        firestoreSync,
    )

    val cloudSyncStatus: StateFlow<CloudSyncStatus> =
        firestoreSync?.syncStatus ?: MutableStateFlow(CloudSyncStatus.SignedOut)

    private fun currentSignedInUid(): String? =
        if (firestoreSync != null) FirebaseAuth.getInstance().currentUser?.uid else null

    fun observeFarms(): Flow<List<Farm>> =
        farmDao.observeFarms().map { list -> list.map { it.toDomain() } }

    fun observeFarmEntries(farmId: String): Flow<List<LedgerEntry>> =
        entryDao.observeEntriesForFarm(farmId).map { list -> list.map { it.toDomain() } }

    fun observeCommentsForFarm(farmId: String): Flow<List<ReportComment>> =
        commentDao.observeCommentsForFarm(farmId).map { list -> list.map { it.toDomain() } }

    suspend fun syncWithCloud(user: FirebaseUser): Result<Pair<Int, Int>> {
        val sync = firestoreSync ?: return Result.success(0 to 0)
        return sync.syncAllLocalAndRemote(user, farmDao, entryDao, commentDao)
    }

    fun observeRemoteFarms(userId: String): Flow<List<FarmEntity>>? =
        firestoreSync?.observeRemoteFarms(userId)

    fun observeRemoteEntries(userId: String, farmId: String): Flow<List<LedgerEntryEntity>>? =
        firestoreSync?.observeRemoteEntries(userId, farmId)

    fun observeRemoteComments(userId: String, farmId: String): Flow<List<ReportCommentEntity>>? =
        firestoreSync?.observeRemoteComments(userId, farmId)

    suspend fun mergeRemoteFarms(remoteFarms: List<FarmEntity>) {
        if (remoteFarms.isEmpty()) return
        val localMap = farmDao.getAllFarmsOnce().associateBy { it.id }
        val toSave = remoteFarms.filter { remote ->
            val local = localMap[remote.id]
            local == null || remote.updatedAtMillis > local.updatedAtMillis
        }
        if (toSave.isNotEmpty()) {
            farmDao.upsertFarms(toSave)
        }
    }

    suspend fun mergeRemoteEntries(remoteEntries: List<LedgerEntryEntity>) {
        if (remoteEntries.isEmpty()) return
        val localMap = entryDao.getAllEntriesOnce().associateBy { it.id }
        val toSave = remoteEntries.filter { remote ->
            val local = localMap[remote.id]
            local == null || remote.updatedAtMillis > local.updatedAtMillis
        }
        if (toSave.isNotEmpty()) {
            entryDao.upsertEntries(toSave)
        }
    }

    suspend fun mergeRemoteComments(remoteComments: List<ReportCommentEntity>) {
        if (remoteComments.isEmpty()) return
        val localMap = commentDao.getAllCommentsOnce().associateBy { it.id }
        val toSave = remoteComments.filter { remote ->
            val local = localMap[remote.id]
            local == null || remote.updatedAtMillis > local.updatedAtMillis
        }
        if (toSave.isNotEmpty()) {
            commentDao.upsertComments(toSave)
        }
    }

    suspend fun ensureDefaultFarm(): Result<Unit> = try {
        if (farmDao.countFarms() == 0) {
            val now = System.currentTimeMillis()
            val defaultFarm = FarmEntity(
                id = "farm_default",
                name = "My Sheep Farm",
                shepherdsJson = FarmEntity.encodeShepherds(listOf("ڌراڙ 1")),
                initialFlockCount = 0L,
                createdAtMillis = now,
                updatedAtMillis = now,
            )
            farmDao.upsertFarm(defaultFarm)
            currentSignedInUid()?.let { uid ->
                firestoreSync?.upsertFarm(uid, defaultFarm)
            }
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun createFarm(name: String, initialFlockCount: Long): Result<String> = try {
        val cleanName = name.trim().take(80)
        require(cleanName.isNotEmpty()) { "Farm name required" }
        val farmId = "farm_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val now = System.currentTimeMillis()
        val entity = FarmEntity(
            id = farmId,
            name = cleanName,
            shepherdsJson = FarmEntity.encodeShepherds(listOf("ڌراڙ 1")),
            initialFlockCount = initialFlockCount.coerceIn(0L, 100_000L),
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        farmDao.upsertFarm(entity)
        currentSignedInUid()?.let { uid ->
            firestoreSync?.upsertFarm(uid, entity)
        }
        Result.success(farmId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun addEntry(
        farm: Farm,
        createdByName: String,
        category: EntryCategory,
        subCategory: String,
        title: String,
        amount: Double,
        livestockCount: Long,
        shepherdName: String,
        note: String,
        receiptBase64: String?,
        entryDateMillis: Long,
        approvalStatus: ApprovalStatus,
        paymentMethod: PaymentMethod = PaymentMethod.CASH,
    ): Result<Unit> = try {
        val cleanTitle = title.trim().take(120)
        require(cleanTitle.isNotEmpty()) { "Title cannot be empty" }
        if (category == EntryCategory.LIVESTOCK_DEATH) {
            require(amount >= 0.0 && amount <= 100_000_000.0) { "Amount out of bounds" }
            require(livestockCount > 0L && livestockCount <= 100_000L) { "Livestock count out of bounds" }
        } else {
            require(amount > 0.0 && amount <= 100_000_000.0) { "Amount out of bounds" }
            require(livestockCount in 0L..100_000L) { "Livestock count out of bounds" }
        }

        val now = System.currentTimeMillis()
        val entryId = "e_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val cleanShepherd = shepherdName.trim().take(80)
        val cleanSub = if (category == EntryCategory.EXPENSE) subCategory.trim() else ""

        val entity = LedgerEntryEntity(
            id = entryId,
            farmId = farm.id,
            createdByName = createdByName.trim().take(40),
            category = category.name,
            amount = amount,
            livestockCount = livestockCount,
            shepherdName = cleanShepherd,
            title = cleanTitle,
            note = note.trim().take(500),
            receiptBase64 = receiptBase64,
            entryDateMillis = entryDateMillis,
            createdAtMillis = now,
            updatedAtMillis = now,
            approvalStatus = approvalStatus.name,
            subCategory = cleanSub,
            paymentMethod = paymentMethod.name,
        )
        entryDao.upsertEntry(entity)

        val uid = currentSignedInUid()
        if (uid != null) {
            firestoreSync?.upsertEntry(uid, entity)
        }

        val isShepherdCategory = category == EntryCategory.SHEPHERD_ADVANCE ||
            category == EntryCategory.SHEPHERD_ADVANCE_RECOVERY ||
            category == EntryCategory.SHEPHERD_SALARY
        if (isShepherdCategory && cleanShepherd.isNotEmpty() && cleanShepherd !in farm.shepherds && farm.shepherds.size < 30) {
            val updatedShepherdsJson = FarmEntity.encodeShepherds(farm.shepherds + cleanShepherd)
            farmDao.updateFarmSettings(
                farmId = farm.id,
                name = farm.name,
                initialFlockCount = farm.initialFlockCount,
                shepherdsJson = updatedShepherdsJson,
                updatedAtMillis = now,
            )
            if (uid != null) {
                farmDao.getFarmById(farm.id)?.let { updatedFarm ->
                    firestoreSync?.upsertFarm(uid, updatedFarm)
                }
            }
        }

        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun updateEntry(
        farm: Farm,
        existingEntry: LedgerEntry,
        category: EntryCategory,
        subCategory: String,
        title: String,
        amount: Double,
        livestockCount: Long,
        shepherdName: String,
        note: String,
        receiptBase64: String?,
        entryDateMillis: Long,
        approvalStatus: ApprovalStatus = existingEntry.parsedApprovalStatus(),
        paymentMethod: PaymentMethod = existingEntry.parsedPaymentMethod(),
    ): Result<Unit> = try {
        val cleanTitle = title.trim().take(120)
        require(cleanTitle.isNotEmpty()) { "Title cannot be empty" }
        if (category == EntryCategory.LIVESTOCK_DEATH) {
            require(amount >= 0.0 && amount <= 100_000_000.0) { "Amount out of bounds" }
        } else {
            require(amount > 0.0 && amount <= 100_000_000.0) { "Amount out of bounds" }
        }

        val now = System.currentTimeMillis()
        val cleanShepherd = shepherdName.trim().take(80)
        val cleanSub = if (category == EntryCategory.EXPENSE) subCategory.trim() else ""
        val entity = LedgerEntryEntity(
            id = existingEntry.id,
            farmId = farm.id,
            createdByName = existingEntry.createdByName,
            category = category.name,
            amount = amount,
            livestockCount = livestockCount.coerceIn(0L, 100_000L),
            shepherdName = cleanShepherd,
            title = cleanTitle,
            note = note.trim().take(500),
            receiptBase64 = receiptBase64,
            entryDateMillis = entryDateMillis,
            createdAtMillis = existingEntry.createdAtMillis,
            updatedAtMillis = now,
            approvalStatus = approvalStatus.name,
            subCategory = cleanSub,
            paymentMethod = paymentMethod.name,
        )
        entryDao.upsertEntry(entity)
        currentSignedInUid()?.let { uid ->
            firestoreSync?.upsertEntry(uid, entity)
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun setApprovalStatus(entryId: String, status: ApprovalStatus): Result<Unit> = try {
        val now = System.currentTimeMillis()
        entryDao.updateApprovalStatus(entryId, status.name, now)
        currentSignedInUid()?.let { uid ->
            entryDao.getEntryById(entryId)?.let { updated ->
                firestoreSync?.upsertEntry(uid, updated)
            }
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteEntry(entryId: String): Result<Unit> = try {
        val existing = entryDao.getEntryById(entryId)
        entryDao.deleteEntryById(entryId)
        val uid = currentSignedInUid()
        if (uid != null && existing != null) {
            firestoreSync?.deleteEntry(uid, existing.farmId, entryId)
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun addReportComment(
        farmId: String,
        entryId: String?,
        entryTitleSnapshot: String,
        authorRole: String,
        commentText: String,
    ): Result<Unit> = try {
        val cleanText = commentText.trim().take(600)
        require(cleanText.isNotEmpty()) { "Comment cannot be empty" }
        val now = System.currentTimeMillis()
        val id = "c_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val entity = ReportCommentEntity(
            id = id,
            farmId = farmId,
            entryId = entryId?.takeIf { it.isNotBlank() },
            entryTitleSnapshot = entryTitleSnapshot.trim().take(120),
            authorRole = authorRole.trim().take(40),
            commentText = cleanText,
            status = CommentStatus.OPEN.name,
            adminResponse = "",
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        commentDao.upsertComment(entity)
        currentSignedInUid()?.let { uid ->
            firestoreSync?.upsertComment(uid, entity)
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun resolveReportComment(commentId: String, adminResponse: String): Result<Unit> = try {
        val now = System.currentTimeMillis()
        commentDao.updateCommentStatus(
            commentId = commentId,
            status = CommentStatus.RESOLVED.name,
            adminResponse = adminResponse.trim().take(400),
            updatedAtMillis = now,
        )
        currentSignedInUid()?.let { uid ->
            commentDao.getCommentById(commentId)?.let { updated ->
                firestoreSync?.upsertComment(uid, updated)
            }
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteReportComment(commentId: String): Result<Unit> = try {
        val existing = commentDao.getCommentById(commentId)
        commentDao.deleteCommentById(commentId)
        val uid = currentSignedInUid()
        if (uid != null && existing != null) {
            firestoreSync?.deleteComment(uid, existing.farmId, commentId)
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun updateFarmSettings(
        farm: Farm,
        newName: String,
        initialFlockCount: Long,
        shepherds: List<String>,
    ): Result<Unit> = try {
        val cleanName = newName.trim().take(80)
        require(cleanName.isNotEmpty()) { "Farm name required" }
        val cleanShepherds = shepherds.map { it.trim().take(60) }.filter { it.isNotEmpty() }.distinct().take(30)
        farmDao.updateFarmSettings(
            farmId = farm.id,
            name = cleanName,
            initialFlockCount = initialFlockCount.coerceIn(0L, 100_000L),
            shepherdsJson = FarmEntity.encodeShepherds(cleanShepherds),
            updatedAtMillis = System.currentTimeMillis(),
        )
        currentSignedInUid()?.let { uid ->
            farmDao.getFarmById(farm.id)?.let { updatedFarm ->
                firestoreSync?.upsertFarm(uid, updatedFarm)
            }
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getAllFarmsAndEntries(): Pair<List<Farm>, List<LedgerEntry>> {
        val farms = farmDao.getAllFarmsOnce().map { it.toDomain() }
        val entries = entryDao.getAllEntriesOnce().map { it.toDomain() }
        return farms to entries
    }

    suspend fun countAllEntries(): Int = entryDao.countAllEntries()

    suspend fun restoreBackupData(
        farms: List<Farm>,
        entries: List<LedgerEntry>,
    ): Result<RestoreResult> = try {
        val farmEntities = farms.map { farm ->
            FarmEntity(
                id = farm.id.ifBlank { "farm_default" },
                name = farm.name.trim().ifEmpty { "My Sheep Farm" },
                shepherdsJson = FarmEntity.encodeShepherds(farm.shepherds),
                initialFlockCount = farm.initialFlockCount.coerceIn(0L, 100_000L),
                createdAtMillis = farm.createdAtMillis,
                updatedAtMillis = farm.updatedAtMillis,
            )
        }
        val validFarmIds = farmEntities.map { it.id }.toSet()
        val entryEntities = entries.map { entry ->
            val targetFarmId = if (entry.farmId in validFarmIds) {
                entry.farmId
            } else {
                farmEntities.firstOrNull()?.id ?: "farm_default"
            }
            LedgerEntryEntity(
                id = entry.id,
                farmId = targetFarmId,
                createdByName = entry.createdByName,
                category = entry.category,
                amount = entry.amount,
                livestockCount = entry.livestockCount,
                shepherdName = entry.shepherdName,
                title = entry.title,
                note = entry.note,
                receiptBase64 = entry.receiptBase64,
                entryDateMillis = entry.entryDateMillis,
                createdAtMillis = entry.createdAtMillis,
                updatedAtMillis = entry.updatedAtMillis,
                approvalStatus = entry.approvalStatus,
                subCategory = entry.subCategory,
                paymentMethod = entry.paymentMethod,
            )
        }
        if (farmEntities.isNotEmpty()) {
            farmDao.upsertFarms(farmEntities)
        } else {
            ensureDefaultFarm()
        }
        if (entryEntities.isNotEmpty()) {
            entryDao.upsertEntries(entryEntities)
        }
        val uid = currentSignedInUid()
        if (uid != null && firestoreSync != null) {
            farmEntities.forEach { firestoreSync.upsertFarm(uid, it) }
            entryEntities.forEach { firestoreSync.upsertEntry(uid, it) }
        }
        Result.success(RestoreResult(farmsRestored = farmEntities.size, entriesRestored = entryEntities.size))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

data class RestoreResult(
    val farmsRestored: Int,
    val entriesRestored: Int,
)
