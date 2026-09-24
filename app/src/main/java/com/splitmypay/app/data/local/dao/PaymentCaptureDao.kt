package com.splitmypay.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.splitmypay.app.data.local.entity.PaymentCaptureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentCaptureDao {
    @Query("SELECT * FROM payment_captures ORDER BY timestamp DESC")
    fun getAllCaptures(): Flow<List<PaymentCaptureEntity>>

    @Query("SELECT * FROM payment_captures WHERE status = 'PENDING' ORDER BY timestamp DESC")
    fun getPendingCaptures(): Flow<List<PaymentCaptureEntity>>

    @Query("SELECT * FROM payment_captures WHERE id = :id")
    fun getCaptureById(id: Long): Flow<PaymentCaptureEntity?>

    @Query("SELECT * FROM payment_captures WHERE id = :id")
    suspend fun getCaptureByIdSync(id: Long): PaymentCaptureEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCapture(capture: PaymentCaptureEntity): Long

    @Update
    suspend fun updateCapture(capture: PaymentCaptureEntity)

    @Delete
    suspend fun deleteCapture(capture: PaymentCaptureEntity)

    @Query("UPDATE payment_captures SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE payment_captures SET status = 'SYNCED', targetTricountId = :tricountId, syncedTransactionId = :transactionId WHERE id = :id")
    suspend fun markSynced(id: Long, tricountId: Long, transactionId: Long? = null)

    @Query("DELETE FROM payment_captures")
    suspend fun clearAll()
}
