package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.Farm
import com.example.data.model.LedgerEntry
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

@Entity(tableName = "farms")
data class FarmEntity(
    @PrimaryKey val id: String,
    val name: String,
    val shepherdsJson: String,
    val initialFlockCount: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    fun toDomain(): Farm {
        val list = mutableListOf<String>()
        try {
            val arr = JSONArray(shepherdsJson)
            for (i in 0 until arr.length()) {
                val item = arr.optString(i).trim()
                if (item.isNotEmpty()) list.add(item)
            }
        } catch (_: Exception) {
        }
        return Farm(
            id = id,
            name = name,
            shepherds = list,
            initialFlockCount = initialFlockCount,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
        )
    }

    companion object {
        fun encodeShepherds(shepherds: List<String>): String {
            val arr = JSONArray()
            shepherds.forEach { s ->
                val clean = s.trim()
                if (clean.isNotEmpty()) arr.put(clean)
            }
            return arr.toString()
        }
    }
}

@Entity(
    tableName = "ledger_entries",
    foreignKeys = [
        ForeignKey(
            entity = FarmEntity::class,
            parentColumns = ["id"],
            childColumns = ["farmId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("farmId"), Index("entryDateMillis")]
)
data class LedgerEntryEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val createdByName: String,
    val category: String,
    val amount: Double,
    val livestockCount: Long,
    val shepherdName: String,
    val title: String,
    val note: String,
    val receiptBase64: String?,
    val entryDateMillis: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    fun toDomain(): LedgerEntry = LedgerEntry(
        id = id,
        farmId = farmId,
        createdByName = createdByName,
        category = category,
        amount = amount,
        livestockCount = livestockCount,
        shepherdName = shepherdName,
        title = title,
        note = note,
        receiptBase64 = receiptBase64,
        entryDateMillis = entryDateMillis,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )
}

@Dao
interface FarmDao {
    @Query("SELECT * FROM farms ORDER BY createdAtMillis ASC")
    fun observeFarms(): Flow<List<FarmEntity>>

    @Query("SELECT COUNT(*) FROM farms")
    suspend fun countFarms(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFarm(farm: FarmEntity)

    @Query(
        "UPDATE farms SET name = :name, initialFlockCount = :initialFlockCount, shepherdsJson = :shepherdsJson, updatedAtMillis = :updatedAtMillis WHERE id = :farmId"
    )
    suspend fun updateFarmSettings(
        farmId: String,
        name: String,
        initialFlockCount: Long,
        shepherdsJson: String,
        updatedAtMillis: Long,
    )
}

@Dao
interface LedgerEntryDao {
    @Query("SELECT * FROM ledger_entries WHERE farmId = :farmId ORDER BY entryDateMillis DESC, createdAtMillis DESC")
    fun observeEntriesForFarm(farmId: String): Flow<List<LedgerEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: LedgerEntryEntity)

    @Query("DELETE FROM ledger_entries WHERE id = :entryId")
    suspend fun deleteEntryById(entryId: String)
}

@Database(
    entities = [FarmEntity::class, LedgerEntryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class FlockDatabase : RoomDatabase() {
    abstract fun farmDao(): FarmDao
    abstract fun ledgerEntryDao(): LedgerEntryDao

    companion object {
        @Volatile
        private var INSTANCE: FlockDatabase? = null

        fun getInstance(context: Context): FlockDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FlockDatabase::class.java,
                    "flock_ledger.db"
                ).build().also { INSTANCE = it }
            }
    }
}
