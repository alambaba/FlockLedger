package com.example.data.repository

import android.content.Context
import com.example.data.local.FarmDao
import com.example.data.local.FarmEntity
import com.example.data.local.FlockDatabase
import com.example.data.local.LedgerEntryDao
import com.example.data.local.LedgerEntryEntity
import com.example.data.model.ApprovalStatus
import com.example.data.model.EntryCategory
import com.example.data.model.Farm
import com.example.data.model.LedgerEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FlockRepository(
    private val farmDao: FarmDao,
    private val entryDao: LedgerEntryDao,
) {
    constructor(context: Context) : this(
        FlockDatabase.getInstance(context).farmDao(),
        FlockDatabase.getInstance(context).ledgerEntryDao(),
    )

    fun observeFarms(): Flow<List<Farm>> =
        farmDao.observeFarms().map { list -> list.map { it.toDomain() } }

    fun observeFarmEntries(farmId: String): Flow<List<LedgerEntry>> =
        entryDao.observeEntriesForFarm(farmId).map { list -> list.map { it.toDomain() } }

    suspend fun ensureDefaultFarm(): Result<Unit> = try {
        if (farmDao.countFarms() == 0) {
            val now = System.currentTimeMillis()
            farmDao.upsertFarm(
                FarmEntity(
                    id = "farm_default",
                    name = "My Sheep Farm",
                    shepherdsJson = FarmEntity.encodeShepherds(listOf("ڌراڙ 1")),
                    initialFlockCount = 0L,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                )
            )
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
        farmDao.upsertFarm(
            FarmEntity(
                id = farmId,
                name = cleanName,
                shepherdsJson = FarmEntity.encodeShepherds(listOf("ڌراڙ 1")),
                initialFlockCount = initialFlockCount.coerceIn(0L, 100_000L),
                createdAtMillis = now,
                updatedAtMillis = now,
            )
        )
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
    ): Result<Unit> = try {
        val cleanTitle = title.trim().take(120)
        require(cleanTitle.isNotEmpty()) { "Title cannot be empty" }
        require(amount > 0.0 && amount <= 100_000_000.0) { "Amount out of bounds" }
        require(livestockCount in 0L..100_000L) { "Livestock count out of bounds" }

        val now = System.currentTimeMillis()
        val entryId = "e_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val cleanShepherd = shepherdName.trim().take(80)
        val cleanSub = if (category == EntryCategory.EXPENSE) subCategory.trim() else ""

        entryDao.upsertEntry(
            LedgerEntryEntity(
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
            )
        )

        val isShepherdCategory = category == EntryCategory.SHEPHERD_ADVANCE || category == EntryCategory.SHEPHERD_SALARY
        if (isShepherdCategory && cleanShepherd.isNotEmpty() && cleanShepherd !in farm.shepherds && farm.shepherds.size < 30) {
            farmDao.updateFarmSettings(
                farmId = farm.id,
                name = farm.name,
                initialFlockCount = farm.initialFlockCount,
                shepherdsJson = FarmEntity.encodeShepherds(farm.shepherds + cleanShepherd),
                updatedAtMillis = now,
            )
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
    ): Result<Unit> = try {
        val cleanTitle = title.trim().take(120)
        require(cleanTitle.isNotEmpty()) { "Title cannot be empty" }
        require(amount > 0.0 && amount <= 100_000_000.0) { "Amount out of bounds" }

        val now = System.currentTimeMillis()
        val cleanShepherd = shepherdName.trim().take(80)
        val cleanSub = if (category == EntryCategory.EXPENSE) subCategory.trim() else ""
        entryDao.upsertEntry(
            LedgerEntryEntity(
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
            )
        )
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun setApprovalStatus(entryId: String, status: ApprovalStatus): Result<Unit> = try {
        entryDao.updateApprovalStatus(entryId, status.name, System.currentTimeMillis())
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteEntry(entryId: String): Result<Unit> = try {
        entryDao.deleteEntryById(entryId)
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
