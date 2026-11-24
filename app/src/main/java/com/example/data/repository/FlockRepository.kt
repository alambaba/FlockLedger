package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.EntryCategory
import com.example.data.model.Farm
import com.example.data.model.LedgerEntry
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FlockRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    fun observeUserFarms(uid: String): Flow<List<Farm>> = flow {
        require(uid.isNotBlank()) { "UID must not be blank" }
        val path = "farms"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Farm::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeFarmEntries(farmId: String, uid: String): Flow<List<LedgerEntry>> = flow {
        require(farmId.isNotBlank() && uid.isNotBlank()) { "farmId and uid must not be blank" }
        val path = "farms/$farmId/entries"
        emitAll(
            db.collection("farms").document(farmId).collection("entries")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(LedgerEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareByDescending<LedgerEntry> { it.entryDateMillis }
                                .thenByDescending { it.createdAt?.seconds ?: 0L }
                        )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureDefaultFarm(uid: String, displayName: String?): Result<Farm> {
        val farmId = "farm_${uid.take(20)}"
        val farmPath = "farms/$farmId"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "Signed-in user changed" }

            val existing = db.collection("farms")
                .whereArrayContains("memberUids", uid)
                .limit(1)
                .get()
                .await()
                .toObjects(Farm::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

            if (existing.isNotEmpty()) {
                return Result.success(existing.first())
            }

            val farmName = if (!displayName.isNullOrBlank()) {
                "${displayName.trim().take(40)} Farm"
            } else {
                "My Sheep Farm"
            }
            val joinCode = generateJoinCode()

            val farmPayload = mapOf(
                "ownerId" to uid,
                "name" to farmName,
                "joinCode" to joinCode,
                "memberUids" to listOf(uid),
                "adminUids" to listOf(uid),
                "shepherds" to listOf("ڌراڙ 1"),
                "initialFlockCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            db.collection("farms").document(farmId).set(farmPayload).await()

            val invitePayload = mapOf(
                "farmId" to farmId,
                "farmName" to farmName,
                "ownerId" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
            )
            db.collection("farm_invites").document(joinCode).set(invitePayload).await()

            val created = db.collection("farms").document(farmId).get().await()
                .toObject(Farm::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: Farm(
                    id = farmId,
                    ownerId = uid,
                    name = farmName,
                    joinCode = joinCode,
                    memberUids = listOf(uid),
                    adminUids = listOf(uid),
                    shepherds = listOf("ڌراڙ 1"),
                    initialFlockCount = 0L,
                )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, farmPath)
            Result.failure(e)
        }
    }

    suspend fun joinFarmByCode(rawCode: String): Result<String> {
        val code = rawCode.trim().uppercase()
        if (!code.matches(Regex("^[A-Z0-9]{6,16}$"))) {
            return Result.failure(IllegalArgumentException("Invalid invite code format"))
        }
        val invitePath = "farm_invites/$code"
        return try {
            val uid = requireUserId()
            val inviteSnap = db.collection("farm_invites").document(code).get().await()
            if (!inviteSnap.exists()) {
                return Result.failure(IllegalArgumentException("Invite code not found"))
            }
            val farmId = inviteSnap.getString("farmId")
                ?: return Result.failure(IllegalStateException("Malformed invite"))

            db.collection("farms").document(farmId).update(
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(farmId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, invitePath)
            Result.failure(e)
        }
    }

    suspend fun syncMemberUidsToEntries(farm: Farm, entries: List<LedgerEntry>): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        if (!farm.isUserAdmin(uid)) return Result.success(Unit)
        val outdated = entries.filter { entry ->
            entry.memberUids != farm.memberUids || entry.adminUids != farm.adminUids
        }
        if (outdated.isEmpty()) return Result.success(Unit)

        return try {
            val batch = db.batch()
            outdated.take(400).forEach { entry ->
                val ref = db.collection("farms").document(farm.id)
                    .collection("entries").document(entry.id)
                batch.update(
                    ref,
                    mapOf(
                        "memberUids" to farm.memberUids,
                        "adminUids" to farm.adminUids,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "farms/${farm.id}/entries")
            Result.failure(e)
        }
    }

    suspend fun addEntry(
        farm: Farm,
        category: EntryCategory,
        title: String,
        amount: Double,
        livestockCount: Long,
        shepherdName: String,
        note: String,
        receiptBase64: String?,
        entryDateMillis: Long,
    ): Result<Unit> {
        val entryId = "e_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "farms/${farm.id}/entries/$entryId"
        return try {
            val uid = requireUserId()
            val userName = (auth.currentUser?.displayName ?: auth.currentUser?.email ?: "Member").take(80)
            val cleanTitle = title.trim().take(120)
            require(cleanTitle.isNotEmpty()) { "Title cannot be empty" }
            require(amount > 0.0 && amount <= 100_000_000.0) { "Amount out of bounds" }
            require(livestockCount in 0L..100_000L) { "Livestock count out of bounds" }
            if (receiptBase64 != null) {
                require(receiptBase64.length <= 350_000) { "Receipt image too large" }
            }

            val payload = linkedMapOf<String, Any?>(
                "farmId" to farm.id,
                "createdByUid" to uid,
                "createdByName" to userName,
                "memberUids" to farm.memberUids,
                "adminUids" to farm.adminUids,
                "category" to category.name,
                "amount" to amount,
                "livestockCount" to livestockCount,
                "shepherdName" to shepherdName.trim().take(80),
                "title" to cleanTitle,
                "note" to note.trim().take(500),
                "receiptBase64" to receiptBase64,
                "entryDateMillis" to entryDateMillis,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null }

            db.collection("farms").document(farm.id)
                .collection("entries").document(entryId)
                .set(payload)
                .await()

            // Also ensure shepherd name is in farm roster if applicable
            val cleanShepherd = shepherdName.trim().take(80)
            if (cleanShepherd.isNotEmpty() && cleanShepherd !in farm.shepherds && farm.isUserAdmin(uid) && farm.shepherds.size < 30) {
                db.collection("farms").document(farm.id)
                    .update(
                        mapOf(
                            "shepherds" to FieldValue.arrayUnion(cleanShepherd),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    ).await()
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateEntry(
        farm: Farm,
        entryId: String,
        category: EntryCategory,
        title: String,
        amount: Double,
        livestockCount: Long,
        shepherdName: String,
        note: String,
        receiptBase64: String?,
        entryDateMillis: Long,
    ): Result<Unit> {
        val path = "farms/${farm.id}/entries/$entryId"
        return try {
            val uid = requireUserId()
            require(farm.isUserAdmin(uid)) { "Only admin can edit entries" }
            require(entryId.isNotBlank()) { "Entry ID required" }
            val cleanTitle = title.trim().take(120)
            require(cleanTitle.isNotEmpty()) { "Title cannot be empty" }
            require(amount > 0.0 && amount <= 100_000_000.0) { "Amount out of bounds" }

            val updates = linkedMapOf<String, Any>(
                "category" to category.name,
                "amount" to amount,
                "livestockCount" to livestockCount.coerceIn(0L, 100_000L),
                "shepherdName" to shepherdName.trim().take(80),
                "title" to cleanTitle,
                "note" to note.trim().take(500),
                "entryDateMillis" to entryDateMillis,
                "memberUids" to farm.memberUids,
                "adminUids" to farm.adminUids,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (receiptBase64 != null) {
                require(receiptBase64.length <= 350_000) { "Receipt image too large" }
                updates["receiptBase64"] = receiptBase64
            } else {
                updates["receiptBase64"] = FieldValue.delete()
            }

            db.collection("farms").document(farm.id)
                .collection("entries").document(entryId)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteEntry(farm: Farm, entryId: String): Result<Unit> {
        val path = "farms/${farm.id}/entries/$entryId"
        return try {
            val uid = requireUserId()
            require(farm.isUserAdmin(uid)) { "Only admin can delete entries" }
            db.collection("farms").document(farm.id)
                .collection("entries").document(entryId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun updateFarmSettings(
        farm: Farm,
        newName: String,
        initialFlockCount: Long,
        shepherds: List<String>,
    ): Result<Unit> {
        val path = "farms/${farm.id}"
        return try {
            val uid = requireUserId()
            require(farm.isUserAdmin(uid)) { "Only admin can update farm settings" }
            val cleanName = newName.trim().take(80)
            require(cleanName.isNotEmpty()) { "Farm name required" }
            val cleanShepherds = shepherds.map { it.trim().take(60) }.filter { it.isNotEmpty() }.distinct().take(30)

            db.collection("farms").document(farm.id)
                .update(
                    mapOf(
                        "name" to cleanName,
                        "initialFlockCount" to initialFlockCount.coerceIn(0L, 100_000L),
                        "shepherds" to cleanShepherds,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
