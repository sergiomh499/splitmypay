package com.splitmypay.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.splitmypay.app.R
import com.splitmypay.app.SplitMyPayApp
import com.splitmypay.app.data.local.entity.PaymentCaptureEntity
import com.splitmypay.app.data.local.entity.SettingEntity
import com.splitmypay.app.data.parser.NotificationPaymentParser
import com.splitmypay.app.receiver.QuickSplitReceiver
import com.splitmypay.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WalletNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        val WALLET_PACKAGES = setOf(
            "com.google.android.apps.walletnfcsic",
            "com.google.android.gms"
        )

        val BANK_PACKAGES = setOf(
            "com.revolut.revolut",
            "de.number26",
            "com.bbva.bbvacontigo",
            "es.bancosantander.apps",
            "com.transferwise.android",
            "com.chase.sig.android",
            "com.caixabank.caixabankmobile"
        )

        const val ACTION_CUSTOMIZE = "com.splitmypay.app.CUSTOMIZE"
        const val EXTRA_CAPTURE_ID = "extra_capture_id"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName
        // Avoid intercepting our own prompt notifications
        if (pkg == packageName) return

        serviceScope.launch {
            val db = SplitMyPayApp.instance.database
            val bankEnabledSetting = db.settingDao().getSettingSync(SettingEntity.KEY_LISTEN_BANK_NOTIFICATIONS)
            val bankEnabled = bankEnabledSetting == null || bankEnabledSetting == "true" // enabled by default

            val isWallet = WALLET_PACKAGES.contains(pkg)
            val isBank = bankEnabled && BANK_PACKAGES.contains(pkg)

            if (!isWallet && !isBank) return@launch

            val notification = sbn.notification ?: return@launch
            val extras = notification.extras ?: return@launch

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

            val parsed = NotificationPaymentParser.parse(title, bigText ?: text) ?: return@launch

            val capture = PaymentCaptureEntity(
                timestamp = System.currentTimeMillis(),
                rawPackage = pkg,
                merchant = parsed.merchant,
                amount = parsed.amount,
                currency = parsed.currency,
                status = PaymentCaptureEntity.STATUS_PENDING
            )

            val captureId = db.paymentCaptureDao().insertCapture(capture)
            showPaymentPromptNotification(this@WalletNotificationListenerService, captureId, parsed.merchant, parsed.amount, parsed.currency)
        }
    }

    private fun showPaymentPromptNotification(
        context: Context,
        captureId: Long,
        merchant: String,
        amount: Double,
        currency: String
    ) {
        val formattedAmount = String.format(java.util.Locale.US, "%.2f", amount)

        // 1. Quick Split Action
        val quickSplitIntent = Intent(context, QuickSplitReceiver::class.java).apply {
            action = QuickSplitReceiver.ACTION_QUICK_SPLIT
            putExtra(EXTRA_CAPTURE_ID, captureId)
        }
        val quickSplitPendingIntent = PendingIntent.getBroadcast(
            context,
            (captureId * 10 + 1).toInt(),
            quickSplitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Customize Action (opens app at /split/{captureId})
        val customizeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("splitmypay://split/$captureId"), context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CAPTURE_ID, captureId)
        }
        val customizePendingIntent = PendingIntent.getActivity(
            context,
            (captureId * 10 + 2).toInt(),
            customizeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Dismiss Action
        val dismissIntent = Intent(context, QuickSplitReceiver::class.java).apply {
            action = QuickSplitReceiver.ACTION_DISMISS
            putExtra(EXTRA_CAPTURE_ID, captureId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            (captureId * 10 + 3).toInt(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, SplitMyPayApp.CHANNEL_PAYMENT_CAPTURES)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("Payment: $merchant")
            .setContentText("$formattedAmount $currency • Tap to split into Tricount")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(customizePendingIntent)
            .addAction(android.R.drawable.ic_menu_send, "Quick Split", quickSplitPendingIntent)
            .addAction(android.R.drawable.ic_menu_edit, "Customize", customizePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(captureId.toInt(), builder.build())
        } catch (_: SecurityException) {
            // Notification permission might not be granted yet
        }
    }
}
