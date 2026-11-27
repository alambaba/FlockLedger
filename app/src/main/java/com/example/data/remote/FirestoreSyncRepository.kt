package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.FarmDao
import com.example.data.local.FarmEntity
import com.example.data.local.LedgerEntryDao
import com.example.data.local.LedgerEntryEntity
import com.example.data.local.ReportCommentDao
import com.example.data.local.ReportCommentEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

sealed interface CloudSyncStatus {
    data object SignedOut : CloudSyncStatus
    data object Syncing : CloudSyncStatus
    data class Synced(val lastSyncMillis: Long, val uploadedCount: Int = 0, val downloadedCount: Int = 0) : CloudSyncStatus
    data class Error(val message: String) : CloudSyncStatus
}

class FirestoreSyncRepository(
    appContext: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    private val databaseId: String = appContext.getString(R.string.firestore_database_id)
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(databaseId)

    private val _syncStatus = MutableStateFlow<CloudSyncStatus>(
        if (auth.currentUser == null) CloudSyncStatus.SignedOut else CloudSyncStatus.Synced(0L)
    )
    val syncStatus: StateFlow<CloudSyncStatus> = _syncStatus.asStateFlow()

    fun setSignedOut() {
        _syncStatus.value = CloudSyncStatus.SignedOut
    }

    suspend fun ensureUserProfile(user: FirebaseUser): Result<Unit> {
        val path = "users/${user.uid}"
        val docRef = db.collection("users").document(user.uid)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val payload = mapOf(
                    "userId" to user.uid,
                    "displayName" to (user.displayName?.take(120) ?: ""),
                    "email" to (user.email?.take(200) ?: ""),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(payload).await()
            } else {
                val updatePayload = mapOf(
                    "displayName" to (user.displayName?.take(120) ?: ""),
                    "email" to (user.email?.take(200) ?: ""),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.logStructuredError(e, FirestoreOperationType.WRITE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun upsertFarm(userId: String, farm: FarmEntity): Result<Unit> {
        val path = "users/$userId/farms/${farm.id}"
        val docRef = db.collection("users").document(userId).collection("farms").document(farm.id)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                docRef.set(farm.toFirestoreCreateMap()).await()
            } else {
                docRef.update(farm.toFirestoreUpdateMap()).await()
            }
            _syncStatus.value = CloudSyncStatus.Synced(System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.logStructuredError(e, FirestoreOperationType.WRITE, path, auth)
            _syncStatus.value = CloudSyncStatus.Error(e.localizedMessage ?: "Cloud sync error")
            Result.failure(e)
        }
    }

    suspend fun upsertEntry(userId: String, entry: LedgerEntryEntity): Result<Unit> {
        val path = "users/$userId/farms/${entry.farmId}/entries/${entry.id}"
        val docRef = db.collection("users").document(userId)
            .collection("farms").document(entry.farmId)
            .collection("entries").document(entry.id)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                docRef.set(entry.toFirestoreCreateMap()).await()
            } else {
                docRef.update(entry.toFirestoreUpdateMap()).await()
            }
            _syncStatus.value = CloudSyncStatus.Synced(System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.logStructuredError(e, FirestoreOperationType.WRITE, path, auth)
            _syncStatus.value = CloudSyncStatus.Error(e.localizedMessage ?: "Cloud save error")
            Result.failure(e)
        }
    }

    suspend fun deleteEntry(userId: String, farmId: String, entryId: String): Result<Unit> {
        val path = "users/$userId/farms/$farmId/entries/$entryId"
        return try {
            db.collection("users").document(userId)
                .collection("farms").document(farmId)
                .collection("entries").document(entryId)
                .delete().await()
            _syncStatus.value = CloudSyncStatus.Synced(System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.logStructuredError(e, FirestoreOperationType.DELETE, path, auth)
            _syncStatus.value = CloudSyncStatus.Error(e.localizedMessage ?: "Cloud delete error")
            Result.failure(e)
        }
    }

    suspend fun upsertComment(userId: String, comment: ReportCommentEntity): Result<Unit> {
        val path = "users/$userId/farms/${comment.farmId}/comments/${comment.id}"
        val docRef = db.collection("users").document(userId)
            .collection("farms").document(comment.farmId)
            .collection("comments").document(comment.id)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                docRef.set(comment.toFirestoreCreateMap()).await()
            } else {
                docRef.update(comment.toFirestoreUpdateMap()).await()
            }
            _syncStatus.value = CloudSyncStatus.Synced(System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.logStructuredError(e, FirestoreOperationType.WRITE, path, auth)
            _syncStatus.value = CloudSyncStatus.Error(e.localizedMessage ?: "Cloud comment sync error")
            Result.failure(e)
        }
    }

    suspend fun deleteComment(userId: String, farmId: String, commentId: String): Result<Unit> {
        val path = "users/$userId/farms/$farmId/comments/$commentId"
        return try {
            db.collection("users").document(userId)
                .collection("farms").document(farmId)
                .collection("comments").document(commentId)
                .delete().await()
            _syncStatus.value = CloudSyncStatus.Synced(System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.logStructuredError(e, FirestoreOperationType.DELETE, path, auth)
            _syncStatus.value = CloudSyncStatus.Error(e.localizedMessage ?: "Cloud delete error")
            Result.failure(e)
        }
    }

    /**
     * Preserves all existing Room records while synchronizing farms, ledger entries,
     * and report comments bi-directionally with Firestore by updatedAtMillis.
     */
    suspend fun syncAllLocalAndRemote(
        user: FirebaseUser,
        farmDao: FarmDao,
        entryDao: LedgerEntryDao,
        commentDao: ReportCommentDao,
    ): Result<Pair<Int, Int>> {
        _syncStatus.value = CloudSyncStatus.Syncing
        val userId = user.uid
        var uploaded = 0
        var downloaded = 0
        return try {
            ensureUserProfile(user).getOrThrow()

            val localFarms = farmDao.getAllFarmsOnce()
            val localEntries = entryDao.getAllEntriesOnce()
            val localComments = commentDao.getAllCommentsOnce()

            val farmsPath = "users/$userId/farms"
            val remoteFarmsSnap = db.collection("users").document(userId)
                .collection("farms")
                .orderBy("createdAtMillis", Query.Direction.ASCENDING)
                .get().await()

            val remoteFarms = remoteFarmsSnap.documents.mapNotNull { doc ->
                runCatching {
                    doc.toObject(FarmRemoteDto::class.java)?.copy(id = doc.id)
                }.getOrNull()
            }
            val remoteFarmMap = remoteFarms.associateBy { it.id }
            val localFarmMap = localFarms.associateBy { it.id }

            // 1) Push new/newer local farms to Firestore
            for (localFarm in localFarms) {
                val remote = remoteFarmMap[localFarm.id]
                if (remote == null) {
                    db.collection("users").document(userId)
                        .collection("farms").document(localFarm.id)
                        .set(localFarm.toFirestoreCreateMap()).await()
                    uploaded++
                } else if (localFarm.updatedAtMillis > remote.updatedAtMillis) {
                    db.collection("users").document(userId)
                        .collection("farms").document(localFarm.id)
                        .update(localFarm.toFirestoreUpdateMap()).await()
                    uploaded++
                }
            }

            // 2) Pull missing/newer remote farms into Room (without deleting local farms)
            val farmsToStoreLocally = mutableListOf<FarmEntity>()
            for (remoteFarm in remoteFarms) {
                val local = localFarmMap[remoteFarm.id]
                if (local == null || remoteFarm.updatedAtMillis > local.updatedAtMillis) {
                    farmsToStoreLocally.add(remoteFarm.toLocalEntity())
                    downloaded++
                }
            }
            if (farmsToStoreLocally.isNotEmpty()) {
                farmDao.upsertFarms(farmsToStoreLocally)
            }

            // Combined set of farm IDs to sync entries and comments
            val allFarmIds = (localFarms.map { it.id } + remoteFarms.map { it.id }).distinct()
            val localEntriesById = localEntries.associateBy { it.id }
            val localCommentsById = localComments.associateBy { it.id }

            for (farmId in allFarmIds) {
                // Entries sync for farmId
                val entriesCol = db.collection("users").document(userId)
                    .collection("farms").document(farmId)
                    .collection("entries")
                val remoteEntriesSnap = entriesCol.get().await()
                val remoteEntries = remoteEntriesSnap.documents.mapNotNull { doc ->
                    runCatching { doc.toObject(LedgerEntryRemoteDto::class.java)?.copy(id = doc.id) }.getOrNull()
                }
                val remoteEntryMap = remoteEntries.associateBy { it.id }

                for (localEntry in localEntries.filter { it.farmId == farmId }) {
                    val remote = remoteEntryMap[localEntry.id]
                    if (remote == null) {
                        entriesCol.document(localEntry.id).set(localEntry.toFirestoreCreateMap()).await()
                        uploaded++
                    } else if (localEntry.updatedAtMillis > remote.updatedAtMillis) {
                        entriesCol.document(localEntry.id).update(localEntry.toFirestoreUpdateMap()).await()
                        uploaded++
                    }
                }

                val entriesToSaveLocally = mutableListOf<LedgerEntryEntity>()
                for (remoteEntry in remoteEntries) {
                    val local = localEntriesById[remoteEntry.id]
                    if (local == null || remoteEntry.updatedAtMillis > local.updatedAtMillis) {
                        entriesToSaveLocally.add(remoteEntry.toLocalEntity())
                        downloaded++
                    }
                }
                if (entriesToSaveLocally.isNotEmpty()) {
                    entryDao.upsertEntries(entriesToSaveLocally)
                }

                // Comments sync for farmId
                val commentsCol = db.collection("users").document(userId)
                    .collection("farms").document(farmId)
                    .collection("comments")
                val remoteCommentsSnap = commentsCol.get().await()
                val remoteComments = remoteCommentsSnap.documents.mapNotNull { doc ->
                    runCatching { doc.toObject(ReportCommentRemoteDto::class.java)?.copy(id = doc.id) }.getOrNull()
                }
                val remoteCommentMap = remoteComments.associateBy { it.id }

                for (localComment in localComments.filter { it.farmId == farmId }) {
                    val remote = remoteCommentMap[localComment.id]
                    if (remote == null) {
                        commentsCol.document(localComment.id).set(localComment.toFirestoreCreateMap()).await()
                        uploaded++
                    } else if (localComment.updatedAtMillis > remote.updatedAtMillis) {
                        commentsCol.document(localComment.id).update(localComment.toFirestoreUpdateMap()).await()
                        uploaded++
                    }
                }

                val commentsToSaveLocally = mutableListOf<ReportCommentEntity>()
                for (remoteComment in remoteComments) {
                    val local = localCommentsById[remoteComment.id]
                    if (local == null || remoteComment.updatedAtMillis > local.updatedAtMillis) {
                        commentsToSaveLocally.add(remoteComment.toLocalEntity())
                        downloaded++
                    }
                }
                if (commentsToSaveLocally.isNotEmpty()) {
                    commentDao.upsertComments(commentsToSaveLocally)
                }
            }

            Log.d(TAG, "Firebase sync complete at $farmsPath: uploaded=$uploaded, downloaded=$downloaded")
            _syncStatus.value = CloudSyncStatus.Synced(
                lastSyncMillis = System.currentTimeMillis(),
                uploadedCount = uploaded,
                downloadedCount = downloaded,
            )
            Result.success(uploaded to downloaded)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.logStructuredError(e, FirestoreOperationType.LIST, "users/$userId/farms", auth)
            _syncStatus.value = CloudSyncStatus.Error(e.localizedMessage ?: "Cloud sync failed")
            Result.failure(e)
        }
    }

    fun observeRemoteFarms(userId: String): Flow<List<FarmEntity>> = callbackFlow {
        val path = "users/$userId/farms"
        val listener = db.collection("users").document(userId)
            .collection("farms")
            .orderBy("createdAtMillis", Query.Direction.ASCENDING)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                if (err != null) {
                    FirestoreErrorHandler.logStructuredError(err, FirestoreOperationType.LIST, path, auth)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    runCatching {
                        doc.toObject(FarmRemoteDto::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            ?.copy(id = doc.id)
                            ?.toLocalEntity()
                    }.getOrNull()
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun observeRemoteEntries(userId: String, farmId: String): Flow<List<LedgerEntryEntity>> = callbackFlow {
        val path = "users/$userId/farms/$farmId/entries"
        val listener = db.collection("users").document(userId)
            .collection("farms").document(farmId)
            .collection("entries")
            .orderBy("entryDateMillis", Query.Direction.DESCENDING)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                if (err != null) {
                    FirestoreErrorHandler.logStructuredError(err, FirestoreOperationType.LIST, path, auth)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    runCatching {
                        doc.toObject(LedgerEntryRemoteDto::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            ?.copy(id = doc.id)
                            ?.toLocalEntity()
                    }.getOrNull()
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun observeRemoteComments(userId: String, farmId: String): Flow<List<ReportCommentEntity>> = callbackFlow {
        val path = "users/$userId/farms/$farmId/comments"
        val listener = db.collection("users").document(userId)
            .collection("farms").document(farmId)
            .collection("comments")
            .orderBy("createdAtMillis", Query.Direction.DESCENDING)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                if (err != null) {
                    FirestoreErrorHandler.logStructuredError(err, FirestoreOperationType.LIST, path, auth)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    runCatching {
                        doc.toObject(ReportCommentRemoteDto::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            ?.copy(id = doc.id)
                            ?.toLocalEntity()
                    }.getOrNull()
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    companion object {
        private const val TAG = "FirestoreSyncRepo"
    }
}
