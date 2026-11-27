package com.example.data.remote

import androidx.annotation.Keep
import com.example.data.local.FarmEntity
import com.example.data.local.LedgerEntryEntity
import com.example.data.local.ReportCommentEntity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class FarmRemoteDto(
    @DocumentId val id: String = "",
    val name: String = "",
    val shepherds: List<String> = emptyList(),
    val initialFlockCount: Long = 0L,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toLocalEntity(): FarmEntity = FarmEntity(
        id = id,
        name = name.trim().ifEmpty { "My Sheep Farm" }.take(80),
        shepherdsJson = FarmEntity.encodeShepherds(shepherds),
        initialFlockCount = initialFlockCount.coerceIn(0L, 100_000L),
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )
}

@Keep
@IgnoreExtraProperties
data class LedgerEntryRemoteDto(
    @DocumentId val id: String = "",
    val farmId: String = "",
    val createdByName: String = "",
    val category: String = "EXPENSE",
    val subCategory: String = "",
    val amount: Double = 0.0,
    val livestockCount: Long = 0L,
    val shepherdName: String = "",
    val title: String = "",
    val note: String = "",
    val receiptBase64: String? = null,
    val entryDateMillis: Long = 0L,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val approvalStatus: String = "APPROVED",
    val paymentMethod: String = "CASH",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toLocalEntity(): LedgerEntryEntity = LedgerEntryEntity(
        id = id,
        farmId = farmId,
        createdByName = createdByName.take(40),
        category = category,
        subCategory = subCategory,
        amount = amount,
        livestockCount = livestockCount.coerceIn(0L, 100_000L),
        shepherdName = shepherdName.take(80),
        title = title.ifBlank { "Entry" }.take(120),
        note = note.take(500),
        receiptBase64 = receiptBase64,
        entryDateMillis = entryDateMillis,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        approvalStatus = approvalStatus,
        paymentMethod = paymentMethod,
    )
}

@Keep
@IgnoreExtraProperties
data class ReportCommentRemoteDto(
    @DocumentId val id: String = "",
    val farmId: String = "",
    val entryId: String? = null,
    val entryTitleSnapshot: String = "",
    val authorRole: String = "Caretaker",
    val commentText: String = "",
    val status: String = "OPEN",
    val adminResponse: String = "",
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toLocalEntity(): ReportCommentEntity = ReportCommentEntity(
        id = id,
        farmId = farmId,
        entryId = entryId,
        entryTitleSnapshot = entryTitleSnapshot.take(120),
        authorRole = authorRole.ifBlank { "Caretaker" }.take(40),
        commentText = commentText.take(600),
        status = status,
        adminResponse = adminResponse.take(400),
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )
}

fun FarmEntity.toFirestoreCreateMap(): Map<String, Any> {
    val domain = toDomain()
    return mapOf(
        "name" to domain.name.trim().ifEmpty { "My Sheep Farm" }.take(80),
        "shepherds" to domain.shepherds.take(30),
        "initialFlockCount" to domain.initialFlockCount.coerceIn(0L, 100_000L),
        "createdAtMillis" to domain.createdAtMillis.coerceAtLeast(0L),
        "updatedAtMillis" to domain.updatedAtMillis.coerceAtLeast(0L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )
}

fun FarmEntity.toFirestoreUpdateMap(): Map<String, Any> {
    val domain = toDomain()
    return mapOf(
        "name" to domain.name.trim().ifEmpty { "My Sheep Farm" }.take(80),
        "shepherds" to domain.shepherds.take(30),
        "initialFlockCount" to domain.initialFlockCount.coerceIn(0L, 100_000L),
        "updatedAtMillis" to domain.updatedAtMillis.coerceAtLeast(0L),
        "updatedAt" to FieldValue.serverTimestamp(),
    )
}

fun LedgerEntryEntity.toFirestoreCreateMap(): Map<String, Any?> {
    val cleanReceipt = receiptBase64?.takeIf { it.isNotBlank() && it.length <= 350_000 }
    val safeStatus = if (approvalStatus in setOf("APPROVED", "PENDING", "REJECTED")) approvalStatus else "APPROVED"
    val safeMethod = if (paymentMethod in setOf("CASH", "CREDIT")) paymentMethod else "CASH"
    return mapOf(
        "farmId" to farmId,
        "createdByName" to createdByName.take(40),
        "category" to category.ifBlank { "EXPENSE" }.take(40),
        "subCategory" to subCategory.take(40),
        "amount" to amount.coerceIn(0.0, 100_000_000.0),
        "livestockCount" to livestockCount.coerceIn(0L, 100_000L),
        "shepherdName" to shepherdName.take(80),
        "title" to title.trim().ifEmpty { "Entry" }.take(120),
        "note" to note.take(500),
        "receiptBase64" to cleanReceipt,
        "entryDateMillis" to entryDateMillis.coerceAtLeast(0L),
        "createdAtMillis" to createdAtMillis.coerceAtLeast(0L),
        "updatedAtMillis" to updatedAtMillis.coerceAtLeast(0L),
        "approvalStatus" to safeStatus,
        "paymentMethod" to safeMethod,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )
}

fun LedgerEntryEntity.toFirestoreUpdateMap(): Map<String, Any?> {
    val cleanReceipt = receiptBase64?.takeIf { it.isNotBlank() && it.length <= 350_000 }
    val safeStatus = if (approvalStatus in setOf("APPROVED", "PENDING", "REJECTED")) approvalStatus else "APPROVED"
    val safeMethod = if (paymentMethod in setOf("CASH", "CREDIT")) paymentMethod else "CASH"
    return mapOf(
        "category" to category.ifBlank { "EXPENSE" }.take(40),
        "subCategory" to subCategory.take(40),
        "amount" to amount.coerceIn(0.0, 100_000_000.0),
        "livestockCount" to livestockCount.coerceIn(0L, 100_000L),
        "shepherdName" to shepherdName.take(80),
        "title" to title.trim().ifEmpty { "Entry" }.take(120),
        "note" to note.take(500),
        "receiptBase64" to cleanReceipt,
        "entryDateMillis" to entryDateMillis.coerceAtLeast(0L),
        "updatedAtMillis" to updatedAtMillis.coerceAtLeast(0L),
        "approvalStatus" to safeStatus,
        "paymentMethod" to safeMethod,
        "updatedAt" to FieldValue.serverTimestamp(),
    )
}

fun ReportCommentEntity.toFirestoreCreateMap(): Map<String, Any?> {
    val safeStatus = if (status in setOf("OPEN", "RESOLVED")) status else "OPEN"
    return mapOf(
        "farmId" to farmId,
        "entryId" to entryId?.takeIf { it.isNotBlank() }?.take(128),
        "entryTitleSnapshot" to entryTitleSnapshot.take(120),
        "authorRole" to authorRole.ifBlank { "Caretaker" }.take(40),
        "commentText" to commentText.trim().ifEmpty { "Note" }.take(600),
        "status" to safeStatus,
        "adminResponse" to adminResponse.take(400),
        "createdAtMillis" to createdAtMillis.coerceAtLeast(0L),
        "updatedAtMillis" to updatedAtMillis.coerceAtLeast(0L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )
}

fun ReportCommentEntity.toFirestoreUpdateMap(): Map<String, Any> {
    val safeStatus = if (status in setOf("OPEN", "RESOLVED")) status else "OPEN"
    return mapOf(
        "status" to safeStatus,
        "adminResponse" to adminResponse.take(400),
        "updatedAtMillis" to updatedAtMillis.coerceAtLeast(0L),
        "updatedAt" to FieldValue.serverTimestamp(),
    )
}
