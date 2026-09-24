package com.splitmypay.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "members",
    foreignKeys = [
        ForeignKey(
            entity = TricountEntity::class,
            parentColumns = ["id"],
            childColumns = ["tricountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tricountId"])]
)
data class MemberEntity(
    @PrimaryKey
    val uuid: String,
    val tricountId: Long,
    val displayName: String,
    val isCurrentUser: Boolean = false
)
