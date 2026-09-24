package com.splitmypay.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tricounts",
    indices = [Index(value = ["publicToken"], unique = true)]
)
data class TricountEntity(
    @PrimaryKey
    val id: Long,
    val publicToken: String,
    val title: String,
    val currency: String,
    val category: String? = null,
    val isDefault: Boolean = false
)
