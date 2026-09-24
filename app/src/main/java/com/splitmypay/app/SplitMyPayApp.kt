package com.splitmypay.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.splitmypay.app.data.local.AppDatabase

class SplitMyPayApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val apiClient: com.splitmypay.app.data.api.TricountApiClient by lazy { com.splitmypay.app.data.api.TricountApiClient(this) }
    companion object {
        const val CHANNEL_PAYMENT_CAPTURES = "payment_captures_channel"
        lateinit var instance: SplitMyPayApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_PAYMENT_CAPTURES,
                "Payment Captures",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for detected wallet and card payments"
                enableVibration(true)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
