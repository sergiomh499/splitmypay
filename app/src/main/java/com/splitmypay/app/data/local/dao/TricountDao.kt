package com.splitmypay.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.splitmypay.app.data.local.entity.TricountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TricountDao {
    @Query("SELECT * FROM tricounts ORDER BY isDefault DESC, title ASC")
    fun getAllTricounts(): Flow<List<TricountEntity>>

    @Query("SELECT * FROM tricounts WHERE id = :id")
    fun getTricountById(id: Long): Flow<TricountEntity?>

    @Query("SELECT * FROM tricounts WHERE id = :id")
    suspend fun getTricountByIdSync(id: Long): TricountEntity?

    @Query("SELECT * FROM tricounts WHERE publicToken = :token LIMIT 1")
    suspend fun getTricountByToken(token: String): TricountEntity?

    @Query("SELECT * FROM tricounts WHERE isDefault = 1 LIMIT 1")
    fun getDefaultTricount(): Flow<TricountEntity?>

    @Query("SELECT * FROM tricounts WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultTricountSync(): TricountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTricount(tricount: TricountEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTricounts(tricounts: List<TricountEntity>)

    @Update
    suspend fun updateTricount(tricount: TricountEntity)

    @Delete
    suspend fun deleteTricount(tricount: TricountEntity)

    @Query("UPDATE tricounts SET isDefault = 0")
    suspend fun clearDefaultFlags()

    @Query("UPDATE tricounts SET isDefault = 1 WHERE id = :id")
    suspend fun setAsDefault(id: Long)

    @Transaction
    suspend fun setDefaultTricount(id: Long) {
        clearDefaultFlags()
        setAsDefault(id)
    }
}
