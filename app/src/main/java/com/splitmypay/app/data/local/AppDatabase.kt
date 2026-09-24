package com.splitmypay.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.splitmypay.app.data.local.dao.MemberDao
import com.splitmypay.app.data.local.dao.PaymentCaptureDao
import com.splitmypay.app.data.local.dao.SettingDao
import com.splitmypay.app.data.local.dao.TricountDao
import com.splitmypay.app.data.local.entity.MemberEntity
import com.splitmypay.app.data.local.entity.PaymentCaptureEntity
import com.splitmypay.app.data.local.entity.SettingEntity
import com.splitmypay.app.data.local.entity.TricountEntity

@Database(
    entities = [
        TricountEntity::class,
        MemberEntity::class,
        PaymentCaptureEntity::class,
        SettingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tricountDao(): TricountDao
    abstract fun memberDao(): MemberDao
    abstract fun paymentCaptureDao(): PaymentCaptureDao
    abstract fun settingDao(): SettingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "splitmypay.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
