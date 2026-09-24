package com.splitmypay.app.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.splitmypay.app.SplitMyPayApp
import com.splitmypay.app.data.local.entity.PaymentCaptureEntity
import com.splitmypay.app.data.local.entity.SettingEntity
import com.splitmypay.app.data.util.SplitCalculator
import com.splitmypay.app.service.WalletNotificationListenerService
import com.splitmypay.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class QuickSplitReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_QUICK_SPLIT = "com.splitmypay.app.ACTION_QUICK_SPLIT"
        const val ACTION_DISMISS = "com.splitmypay.app.ACTION_DISMISS"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val captureId = intent.getLongExtra(WalletNotificationListenerService.EXTRA_CAPTURE_ID, -1L)
        if (captureId <= 0L) return

        val app = context.applicationContext as SplitMyPayApp
        val db = app.database
        val apiClient = app.apiClient
        val notificationManager = NotificationManagerCompat.from(context)

        when (intent.action) {
            ACTION_DISMISS -> {
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        db.paymentCaptureDao().updateStatus(captureId, PaymentCaptureEntity.STATUS_DISMISSED)
                        notificationManager.cancel(captureId.toInt())
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_QUICK_SPLIT -> {
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val capture = db.paymentCaptureDao().getCaptureByIdSync(captureId)
                        if (capture == null) {
                            notificationManager.cancel(captureId.toInt())
                            return@launch
                        }

                        // Determine target Tricount: default or first available
                        val defaultTricount = db.tricountDao().getDefaultTricountSync()
                            ?: db.tricountDao().getAllTricounts().let {
                                // Fallback to first if no default set
                                null
                            }

                        val targetTricount = defaultTricount ?: run {
                            // Notify user to link group
                            showErrorNotification(
                                context,
                                captureId,
                                "No Default Tricount",
                                "Tap to select or import a Tricount group"
                            )
                            return@launch
                        }

                        val members = db.memberDao().getMembersForTricountSync(targetTricount.id)
                        if (members.isEmpty()) {
                            showErrorNotification(
                                context,
                                captureId,
                                "No Members in Group",
                                "Open SplitMyPay to sync group members"
                            )
                            return@launch
                        }

                        // Determine payer: default setting, or current user in group, or first member
                        val defaultPayerSetting = db.settingDao().getSettingSync(SettingEntity.KEY_DEFAULT_PAYER_UUID)
                        val currentUserMember = db.memberDao().getCurrentUserForTricountSync(targetTricount.id)
                        val payerUuid = defaultPayerSetting
                            ?: currentUserMember?.uuid
                            ?: members.first().uuid

                        val memberUuids = members.map { it.uuid }
                        val allocations = SplitCalculator.calculateEqualSplit(capture.amount, memberUuids)

                        val result = apiClient.createExpense(
                            tricountId = targetTricount.id,
                            description = capture.merchant,
                            amount = capture.amount,
                            currency = capture.currency,
                            payerUuid = payerUuid,
                            allocations = allocations,
                            category = "OTHER"
                        )

                        if (result.isSuccess) {
                            val txId = result.getOrNull()
                            db.paymentCaptureDao().markSynced(captureId, targetTricount.id, txId)

                            val successBuilder = NotificationCompat.Builder(context, SplitMyPayApp.CHANNEL_PAYMENT_CAPTURES)
                                .setSmallIcon(android.R.drawable.stat_notify_more)
                                .setContentTitle("Split into ${targetTricount.title} ✓")
                                .setContentText("${"%.2f".format(capture.amount)} ${capture.currency} split equally among ${members.size} members")
                                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                .setAutoCancel(true)

                            notificationManager.notify(captureId.toInt(), successBuilder.build())
                        } else {
                            val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                            showErrorNotification(
                                context,
                                captureId,
                                "Quick Split Failed",
                                "Tap to split manually: $errorMsg"
                            )
                        }
                    } catch (e: Exception) {
                        showErrorNotification(
                            context,
                            captureId,
                            "Quick Split Failed",
                            "Error: ${e.message}"
                        )
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private fun showErrorNotification(
        context: Context,
        captureId: Long,
        title: String,
        message: String
    ) {
        val customizeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("splitmypay://split/$captureId"), context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(WalletNotificationListenerService.EXTRA_CAPTURE_ID, captureId)
        }
        val customizePendingIntent = PendingIntent.getActivity(
            context,
            (captureId * 10 + 2).toInt(),
            customizeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, SplitMyPayApp.CHANNEL_PAYMENT_CAPTURES)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(customizePendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(captureId.toInt(), builder.build())
        } catch (_: SecurityException) {
        }
    }
}
