package com.splitmypay.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payment_captures")
data class PaymentCaptureEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val rawPackage: String,
    val merchant: String,
    val amount: Double,
    val currency: String,
    val status: String = STATUS_PENDING,
    val targetTricountId: Long? = null,
    val syncedTransactionId: Long? = null
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SYNCED = "SYNCED"
        const val STATUS_DISMISSED = "DISMISSED"
    }
}
