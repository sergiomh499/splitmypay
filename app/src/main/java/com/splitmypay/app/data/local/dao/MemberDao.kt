package com.splitmypay.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.splitmypay.app.data.local.entity.MemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    @Query("SELECT * FROM members WHERE tricountId = :tricountId ORDER BY displayName ASC")
    fun getMembersForTricount(tricountId: Long): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE tricountId = :tricountId ORDER BY displayName ASC")
    suspend fun getMembersForTricountSync(tricountId: Long): List<MemberEntity>

    @Query("SELECT * FROM members WHERE tricountId = :tricountId AND isCurrentUser = 1 LIMIT 1")
    suspend fun getCurrentUserForTricountSync(tricountId: Long): MemberEntity?

    @Query("SELECT * FROM members WHERE uuid = :uuid LIMIT 1")
    suspend fun getMemberByUuid(uuid: String): MemberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<MemberEntity>)

    @Query("DELETE FROM members WHERE tricountId = :tricountId")
    suspend fun deleteMembersForTricount(tricountId: Long)

    @Query("UPDATE members SET isCurrentUser = 0 WHERE tricountId = :tricountId")
    suspend fun clearCurrentUserFlags(tricountId: Long)

    @Query("UPDATE members SET isCurrentUser = 1 WHERE tricountId = :tricountId AND uuid = :memberUuid")
    suspend fun markAsCurrentUser(tricountId: Long, memberUuid: String)

    @Transaction
    suspend fun setCurrentUser(tricountId: Long, memberUuid: String) {
        clearCurrentUserFlags(tricountId)
        markAsCurrentUser(tricountId, memberUuid)
    }
}
