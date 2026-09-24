package com.splitmypay.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey
    val key: String,
    val value: String
) {
    companion object {
        const val KEY_DEFAULT_TRICOUNT_ID = "default_tricount_id"
        const val KEY_DEFAULT_PAYER_UUID = "default_payer_uuid"
        const val KEY_LISTEN_BANK_NOTIFICATIONS = "listen_bank_notifications_enabled"
    }
}
