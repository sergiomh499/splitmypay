package com.splitmypay.app

import com.splitmypay.app.data.api.TricountApiClient
import com.splitmypay.app.data.util.SplitCalculator
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TricountApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var apiClient: TricountApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        apiClient = TricountApiClient(
            context = null,
            baseUrl = server.url("").toString().removeSuffix("/")
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testEnsureSessionRegistrationPayloadAndResponse() = runBlocking {
        // Enqueue session registration mock response
        val sessionJson = """
            {
              "Response": [
                { "Id": { "id": 101 } },
                { "Token": { "id": 202, "token": "mock-token-xyz-12345" } },
                { "UserPerson": { "id": 999, "display_name": "SplitMyPay Test" } }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(sessionJson))

        val (token, userId) = apiClient.ensureSession()

        assertEquals("mock-token-xyz-12345", token)
        assertEquals(999L, userId)

        val recordedRequest = server.takeRequest()
        assertEquals("/v1/session-registry-installation", recordedRequest.path)
        assertEquals("POST", recordedRequest.method)
        assertEquals(TricountApiClient.USER_AGENT, recordedRequest.getHeader("User-Agent"))

        val body = recordedRequest.body.readUtf8()
        assertTrue(body.contains("client_public_key"))
        assertTrue(body.contains("-----BEGIN PUBLIC KEY-----"))
        assertTrue(body.contains("app_installation_uuid"))
    }

    @Test
    fun testCreateExpensePayloadMathAndNegativeSignConvention() = runBlocking {
        // Pre-populate cached session
        apiClient.cachedSessionToken = "test-token"
        apiClient.cachedUserId = 42L
        apiClient.cachedInstallationUuid = "install-uuid-1"

        val entryCreatedJson = """
            {
              "Response": [
                { "Id": { "id": 7777 } }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(entryCreatedJson))

        val allocations = listOf(
            SplitCalculator.Allocation("user-uuid-1", 9.23),
            SplitCalculator.Allocation("user-uuid-2", 9.22)
        )

        val result = apiClient.createExpense(
            tricountId = 12345L,
            description = "Dinner at Mercadona",
            amount = 18.45,
            currency = "EUR",
            payerUuid = "user-uuid-1",
            allocations = allocations,
            category = "FOOD_AND_DRINK"
        )

        assertTrue(result.isSuccess)
        assertEquals(7777L, result.getOrNull())

        val recordedRequest = server.takeRequest()
        assertEquals("/v1/user/42/registry/12345/registry-entry", recordedRequest.path)
        assertEquals("POST", recordedRequest.method)
        assertEquals("test-token", recordedRequest.getHeader("X-Bunq-Client-Authentication"))

        val body = recordedRequest.body.readUtf8()
        // Tricount negative expense sign convention
        assertTrue("Body must contain negative total amount: $body", body.contains("\"value\":\"-18.45\""))
        assertTrue("Body must contain negative allocation 1: $body", body.contains("\"value\":\"-9.23\""))
        assertTrue("Body must contain negative allocation 2: $body", body.contains("\"value\":\"-9.22\""))
        assertTrue("Body must contain payer UUID: $body", body.contains("\"membership_owned\":\"user-uuid-1\""))
        assertTrue("Body must contain description: $body", body.contains("\"description\":\"Dinner at Mercadona\""))
    }

    @Test
    fun testSyncRegistryParsesMembersAndDetails() = runBlocking {
        apiClient.cachedSessionToken = "test-token"
        apiClient.cachedUserId = 42L
        apiClient.cachedInstallationUuid = "install-uuid-1"

        // Mock sync response
        val syncRespJson = """
            {
              "Response": [
                {
                  "Registry": {
                    "id": 888,
                    "title": "Road Trip Barcelona",
                    "currency": "EUR",
                    "public_identifier_token": "tMjbqgwJxaikhUbkNz",
                    "all_membership": [
                      {
                        "RegistryMembership": {
                          "uuid": "mem-1",
                          "alias": { "display_name": "Alice" }
                        }
                      },
                      {
                        "RegistryMembership": {
                          "uuid": "mem-2",
                          "public_nick_name": "Bob"
                        }
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(syncRespJson))

        val result = apiClient.syncRegistry("tMjbqgwJxaikhUbkNz")
        assertTrue(result.isSuccess)
        val detail = result.getOrNull()
        assertEquals(888L, detail?.id)
        assertEquals("Road Trip Barcelona", detail?.title)
        assertEquals(2, detail?.allMembership?.size)
        assertEquals("Alice", detail?.allMembership?.get(0)?.registryMembership?.effectiveDisplayName)
        assertEquals("Bob", detail?.allMembership?.get(1)?.registryMembership?.effectiveDisplayName)
    }
}
