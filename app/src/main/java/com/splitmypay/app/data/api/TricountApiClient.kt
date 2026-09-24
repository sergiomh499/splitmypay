package com.splitmypay.app.data.api

import android.content.Context
import android.content.SharedPreferences
import com.splitmypay.app.data.api.model.ActiveRegistryToken
import com.splitmypay.app.data.api.model.AllocationItem
import com.splitmypay.app.data.api.model.AmountValue
import com.splitmypay.app.data.api.model.EntryCreatedResponse
import com.splitmypay.app.data.api.model.RegistryDetail
import com.splitmypay.app.data.api.model.RegistryEntryRequest
import com.splitmypay.app.data.api.model.RegistrySyncRequest
import com.splitmypay.app.data.api.model.RegistrySyncResponse
import com.splitmypay.app.data.api.model.SessionInstallationRequest
import com.splitmypay.app.data.api.model.SessionResponse
import com.splitmypay.app.data.util.SplitCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.UUID

class TricountApiClient(
    private val context: Context? = null,
    private val baseUrl: String = "https://api.tricount.bunq.com",
    private val client: OkHttpClient = OkHttpClient()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val sessionMutex = Mutex()

    private val prefs: SharedPreferences? by lazy {
        context?.getSharedPreferences("tricount_api_prefs", Context.MODE_PRIVATE)
    }

    var cachedInstallationUuid: String? = null
    var cachedSessionToken: String? = null
    var cachedUserId: Long? = null

    init {
        prefs?.let {
            cachedInstallationUuid = it.getString("installation_uuid", null)
            cachedSessionToken = it.getString("session_token", null)
            val uid = it.getLong("user_id", -1L)
            if (uid > 0) cachedUserId = uid
        }
    }

    companion object {
        const val USER_AGENT = "com.bunq.tricount.android:RELEASE:7.0.7:3174:ANDROID:13:C"

        fun formatPublicKeyToPem(publicKey: PublicKey): String {
            val encoded = Base64.getEncoder().encodeToString(publicKey.encoded)
            return "-----BEGIN PUBLIC KEY-----\n$encoded\n-----END PUBLIC KEY-----"
        }
    }

    suspend fun ensureSession(): Pair<String, Long> = sessionMutex.withLock {
        withContext(Dispatchers.IO) {
            val existingToken = cachedSessionToken
            val existingUserId = cachedUserId
            if (!existingToken.isNullOrBlank() && existingUserId != null && existingUserId > 0) {
                return@withContext Pair(existingToken, existingUserId)
            }

            var installUuid = cachedInstallationUuid
            if (installUuid.isNullOrBlank()) {
                installUuid = UUID.randomUUID().toString()
                cachedInstallationUuid = installUuid
                prefs?.edit()?.putString("installation_uuid", installUuid)?.apply()
            }

            // Generate RSA 2048 keypair
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048)
            val keyPair = kpg.generateKeyPair()
            val publicKeyPem = formatPublicKeyToPem(keyPair.public)

            val installRequest = SessionInstallationRequest(
                appInstallationUuid = installUuid,
                clientPublicKey = publicKeyPem,
                deviceDescription = "Android"
            )

            val requestBody = json.encodeToString(installRequest).toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$baseUrl/v1/session-registry-installation")
                .header("User-Agent", USER_AGENT)
                .header("app-id", installUuid)
                .header("X-Bunq-Client-Request-Id", UUID.randomUUID().toString())
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Tricount session installation failed (${response.code}): $bodyStr")
                }

                val sessionResponse = json.decodeFromString<SessionResponse>(bodyStr)
                var token: String? = null
                var userId: Long? = null

                for (item in sessionResponse.response) {
                    if (item.token?.token != null) {
                        token = item.token.token
                    }
                    if (item.userPerson?.id != null) {
                        userId = item.userPerson.id
                    }
                }

                if (token.isNullOrBlank() || userId == null) {
                    throw IllegalStateException("Incomplete session response from Tricount API: $bodyStr")
                }

                cachedSessionToken = token
                cachedUserId = userId
                prefs?.edit()
                    ?.putString("session_token", token)
                    ?.putLong("user_id", userId)
                    ?.apply()

                Pair(token, userId)
            }
        }
    }

    suspend fun syncRegistry(publicToken: String): Result<RegistryDetail> = withContext(Dispatchers.IO) {
        runCatching {
            val (token, userId) = ensureSession()
            val syncRequest = RegistrySyncRequest(
                allRegistryActive = listOf(ActiveRegistryToken(publicToken.trim()))
            )
            val requestBody = json.encodeToString(syncRequest).toRequestBody(jsonMediaType)

            val syncHttpReq = Request.Builder()
                .url("$baseUrl/v1/user/$userId/registry-synchronization")
                .header("User-Agent", USER_AGENT)
                .header("app-id", cachedInstallationUuid.orEmpty())
                .header("X-Bunq-Client-Authentication", token)
                .header("X-Bunq-Client-Request-Id", UUID.randomUUID().toString())
                .post(requestBody)
                .build()

            var registryId: Long? = null
            var directRegistry: RegistryDetail? = null

            client.newCall(syncHttpReq).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Registry sync failed (${response.code}): $bodyStr")
                }
                val syncResp = json.decodeFromString<RegistrySyncResponse>(bodyStr)
                val firstRegistry = syncResp.response.firstOrNull()?.registry
                if (firstRegistry != null) {
                    registryId = firstRegistry.id
                    if (firstRegistry.allMembership.isNotEmpty()) {
                        directRegistry = firstRegistry
                    }
                }
            }

            if (directRegistry != null && directRegistry!!.allMembership.isNotEmpty()) {
                return@runCatching directRegistry!!
            }

            val targetId = registryId ?: throw IllegalStateException("Registry token $publicToken not found")

            // Fetch detailed registry with members
            val detailReq = Request.Builder()
                .url("$baseUrl/v1/user/$userId/registry/$targetId")
                .header("User-Agent", USER_AGENT)
                .header("app-id", cachedInstallationUuid.orEmpty())
                .header("X-Bunq-Client-Authentication", token)
                .header("X-Bunq-Client-Request-Id", UUID.randomUUID().toString())
                .get()
                .build()

            client.newCall(detailReq).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Failed to fetch registry detail (${response.code}): $bodyStr")
                }
                val detailResp = json.decodeFromString<RegistrySyncResponse>(bodyStr)
                detailResp.response.firstOrNull()?.registry ?: throw IllegalStateException("Registry details missing in response: $bodyStr")
            }
        }
    }

    suspend fun createExpense(
        tricountId: Long,
        description: String,
        amount: Double,
        currency: String,
        payerUuid: String,
        allocations: List<SplitCalculator.Allocation>,
        category: String = "OTHER"
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val (token, userId) = ensureSession()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS", Locale.US)
            val dateStr = dateFormat.format(Date())

            val totalValStr = String.format(Locale.US, "-%.2f", amount)
            val allocationItems = allocations.map { alloc ->
                AllocationItem(
                    membershipUuid = alloc.memberUuid,
                    amount = AmountValue(
                        value = String.format(Locale.US, "-%.2f", alloc.amount),
                        currency = currency
                    ),
                    type = "AMOUNT"
                )
            }

            val payload = RegistryEntryRequest(
                uuid = UUID.randomUUID().toString(),
                description = description.ifBlank { "Payment" },
                amount = AmountValue(value = totalValStr, currency = currency),
                membershipOwned = payerUuid,
                allocations = allocationItems,
                typeTransaction = "NORMAL",
                status = "ACTIVE",
                date = dateStr,
                category = category
            )

            val requestBody = json.encodeToString(payload).toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$baseUrl/v1/user/$userId/registry/$tricountId/registry-entry")
                .header("User-Agent", USER_AGENT)
                .header("app-id", cachedInstallationUuid.orEmpty())
                .header("X-Bunq-Client-Authentication", token)
                .header("X-Bunq-Client-Request-Id", UUID.randomUUID().toString())
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Failed to create expense (${response.code}): $bodyStr")
                }
                val createdResp = json.decodeFromString<EntryCreatedResponse>(bodyStr)
                createdResp.response.firstOrNull()?.id?.id ?: 0L
            }
        }
    }
}
