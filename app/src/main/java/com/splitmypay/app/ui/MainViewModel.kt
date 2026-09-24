package com.splitmypay.app.ui

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitmypay.app.SplitMyPayApp
import com.splitmypay.app.data.local.entity.MemberEntity
import com.splitmypay.app.data.local.entity.PaymentCaptureEntity
import com.splitmypay.app.data.local.entity.SettingEntity
import com.splitmypay.app.data.local.entity.TricountEntity
import com.splitmypay.app.data.util.SplitCalculator
import com.splitmypay.app.receiver.QuickSplitReceiver
import com.splitmypay.app.service.WalletNotificationListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as SplitMyPayApp).database
    private val apiClient = (application as SplitMyPayApp).apiClient

    val allCaptures: StateFlow<List<PaymentCaptureEntity>> = db.paymentCaptureDao()
        .getAllCaptures()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingCaptures: StateFlow<List<PaymentCaptureEntity>> = db.paymentCaptureDao()
        .getPendingCaptures()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tricounts: StateFlow<List<TricountEntity>> = db.tricountDao()
        .getAllTricounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val defaultTricount: StateFlow<TricountEntity?> = db.tricountDao()
        .getDefaultTricount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val bankNotificationsEnabled: StateFlow<Boolean> = db.settingDao()
        .getSetting(SettingEntity.KEY_LISTEN_BANK_NOTIFICATIONS)
        .map { it == null || it == "true" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun getMembersForTricount(tricountId: Long): Flow<List<MemberEntity>> {
        return db.memberDao().getMembersForTricount(tricountId)
    }

    fun getCapture(captureId: Long): Flow<PaymentCaptureEntity?> {
        return db.paymentCaptureDao().getCaptureById(captureId)
    }

    fun extractTokenFromUrl(raw: String): String {
        val trimmed = raw.trim()
        val regex = Regex("""(?:tricount\.com/|/)?([a-zA-Z0-9_-]{10,30})/?$""")
        val match = regex.find(trimmed)
        return match?.groups?.get(1)?.value ?: trimmed
    }

    suspend fun importTricount(urlOrToken: String): Result<TricountEntity> = withContext(Dispatchers.IO) {
        val token = extractTokenFromUrl(urlOrToken)
        val syncResult = apiClient.syncRegistry(token)
        if (syncResult.isFailure) {
            return@withContext Result.failure(syncResult.exceptionOrNull() ?: Exception("Failed to sync group"))
        }

        val detail = syncResult.getOrThrow()
        val existing = db.tricountDao().getDefaultTricountSync()
        val isFirst = existing == null

        val tricountEntity = TricountEntity(
            id = detail.id,
            publicToken = token,
            title = detail.title,
            currency = detail.currency,
            category = detail.category,
            isDefault = isFirst
        )

        db.tricountDao().insertTricount(tricountEntity)

        val members = detail.allMembership.map { wrapper ->
            val m = wrapper.registryMembership
            MemberEntity(
                uuid = m.uuid,
                tricountId = detail.id,
                displayName = m.effectiveDisplayName,
                isCurrentUser = false
            )
        }
        db.memberDao().insertMembers(members)

        Result.success(tricountEntity)
    }

    fun setDefaultTricount(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.tricountDao().setDefaultTricount(id)
            db.settingDao().put(SettingEntity.KEY_DEFAULT_TRICOUNT_ID, id.toString())
        }
    }

    fun setCurrentUser(tricountId: Long, memberUuid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.memberDao().setCurrentUser(tricountId, memberUuid)
            db.settingDao().put(SettingEntity.KEY_DEFAULT_PAYER_UUID, memberUuid)
        }
    }

    fun dismissCapture(captureId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.paymentCaptureDao().updateStatus(captureId, PaymentCaptureEntity.STATUS_DISMISSED)
        }
    }

    fun setBankNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().put(SettingEntity.KEY_LISTEN_BANK_NOTIFICATIONS, enabled.toString())
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            db.paymentCaptureDao().clearAll()
        }
    }

    fun reauthenticateSession(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            apiClient.cachedSessionToken = null
            apiClient.cachedUserId = null
            val success = try {
                apiClient.ensureSession()
                true
            } catch (_: Exception) {
                false
            }
            withContext(Dispatchers.Main) {
                onComplete(success)
            }
        }
    }

    suspend fun submitSplit(
        captureId: Long,
        tricountId: Long,
        description: String,
        amount: Double,
        currency: String,
        payerUuid: String,
        allocations: List<SplitCalculator.Allocation>,
        category: String
    ): Result<Long> = withContext(Dispatchers.IO) {
        val result = apiClient.createExpense(
            tricountId = tricountId,
            description = description,
            amount = amount,
            currency = currency,
            payerUuid = payerUuid,
            allocations = allocations,
            category = category
        )

        if (result.isSuccess) {
            val txId = result.getOrNull()
            if (captureId > 0) {
                db.paymentCaptureDao().markSynced(captureId, tricountId, txId)
            }
        }
        result
    }

    fun simulateWalletPayment(
        merchant: String = "Mercadona",
        amount: Double = 18.45,
        currency: String = "EUR"
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val capture = PaymentCaptureEntity(
                timestamp = System.currentTimeMillis(),
                rawPackage = "com.google.android.apps.walletnfcsic",
                merchant = merchant,
                amount = amount,
                currency = currency,
                status = PaymentCaptureEntity.STATUS_PENDING
            )
            val captureId = db.paymentCaptureDao().insertCapture(capture)
            showSimulatorNotification(captureId, merchant, amount, currency)
        }
    }

    private fun showSimulatorNotification(
        captureId: Long,
        merchant: String,
        amount: Double,
        currency: String
    ) {
        val context = getApplication<Application>()
        val formattedAmount = String.format(java.util.Locale.US, "%.2f", amount)

        val quickSplitIntent = Intent(context, QuickSplitReceiver::class.java).apply {
            action = QuickSplitReceiver.ACTION_QUICK_SPLIT
            putExtra(WalletNotificationListenerService.EXTRA_CAPTURE_ID, captureId)
        }
        val quickSplitPendingIntent = PendingIntent.getBroadcast(
            context,
            (captureId * 10 + 1).toInt(),
            quickSplitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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

        val dismissIntent = Intent(context, QuickSplitReceiver::class.java).apply {
            action = QuickSplitReceiver.ACTION_DISMISS
            putExtra(WalletNotificationListenerService.EXTRA_CAPTURE_ID, captureId)
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
        }
    }
}
