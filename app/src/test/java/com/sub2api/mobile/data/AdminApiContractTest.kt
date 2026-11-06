package com.sub2api.mobile.data

import com.google.gson.JsonParser
import com.sub2api.mobile.AppUiState
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.concurrent.TimeUnit

class AdminApiContractTest {
    private fun repository(server: MockWebServer): ApiRepository {
        val store = SessionStore(MemorySessionPersistence())
        store.saveServer(null, "Test", server.url("/").toString())
        store.setTokens(store.state.value, "access", "refresh", 3600)
        return ApiRepository(store, ApiClientFactory(store))
    }
    private fun response(data: String) = MockResponse().setHeader("Content-Type", "application/json").setBody("""{"code":0,"data":$data}""")

    @Test
    fun personalUsageUsesPersonalEndpointsWithFiftyRecordsPerPageAndMatchingDates() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val repository = repository(server)
            for (page in 1..2) {
                server.enqueue(response("""{"total_requests":51}"""))
                server.enqueue(response("""{"items":[{"id":$page,"model":"test"}],"total":51,"page":$page,"pages":2}"""))
                val result = repository.usage("week", page = page, filters = UsageFilters(userId = 99))
                val stats = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
                val logs = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
                assertEquals("/api/v1/usage/stats", stats.encodedPath)
                assertEquals("/api/v1/usage", logs.encodedPath)
                assertNull(logs.queryParameter("user_id"))
                assertEquals("50", logs.queryParameter("page_size"))
                assertEquals(page.toString(), logs.queryParameter("page"))
                assertEquals(stats.queryParameter("start_date"), logs.queryParameter("start_date"))
                assertEquals(stats.queryParameter("end_date"), logs.queryParameter("end_date"))
                assertEquals(page, result.records.page)
                assertEquals(2, result.records.pages)
                assertEquals(51, result.records.total)
                assertEquals(page.toLong(), result.records.items.single().id)
            }
        }
    }

    @Test
    fun adminUsageAppliesUserAccountModelAndPaginationToAdminEndpoints() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(response("""{"total_requests":51}"""))
            server.enqueue(response("""{"items":[{"id":51}],"total":51,"page":2,"pages":2}"""))
            val result = repository(server).usage("today", 2, true, UsageFilters(7, 8, "mapped-model"))
            val stats = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
            val logs = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("/api/v1/admin/usage/stats", stats.encodedPath)
            assertEquals("/api/v1/admin/usage", logs.encodedPath)
            for (url in listOf(stats, logs)) {
                assertEquals("7", url.queryParameter("user_id"))
                assertEquals("8", url.queryParameter("account_id"))
                assertEquals("mapped-model", url.queryParameter("model"))
            }
            assertEquals("2", logs.queryParameter("page"))
            assertEquals("50", logs.queryParameter("page_size"))
            assertEquals("true", logs.queryParameter("exact_total"))
            assertEquals(2, result.records.pages)
        }
    }

    @Test
    fun rollingRankingReadsAllPagesAndDoesNotUseDateOnlyRankingEndpoint() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val now = Instant.now()
            val inside = now.minusSeconds(3600).toString()
            val outside = now.minusSeconds(25 * 3600).toString()
            server.enqueue(response("""{"items":[{"id":1,"user_id":1,"output_tokens":20,"created_at":"$inside"},{"id":2,"user_id":1,"output_tokens":1000,"created_at":"$outside"}],"total":3,"page":1,"pages":2}"""))
            server.enqueue(response("""{"items":[{"id":3,"user_id":2,"output_tokens":30,"created_at":"$inside"}],"total":3,"page":2,"pages":2}"""))
            val progress = mutableListOf<Int>()
            val result = repository(server).userRanking("24h", "total_tokens") { progress += it }
            assertEquals(listOf(2, 3), progress)
            assertEquals(50L, result.rows.sumOf { it.totalTokens })
            assertEquals(2L, result.rows.sumOf { it.requests })
            for (page in 1..2) {
                val url = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
                assertEquals("/api/v1/admin/usage", url.encodedPath)
                assertEquals(page.toString(), url.queryParameter("page"))
                assertEquals("asc", url.queryParameter("sort_order"))
                assertEquals("true", url.queryParameter("exact_total"))
            }
        }
    }

    @Test
    fun defaultRankingUsesOneServerAggregationRequestWithSortAndTimeZone() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(response("""{"users":[{"user_id":9,"email":"user@example.com","requests":12,"total_tokens":99}]}"""))
            val result = repository(server).userRanking(AppUiState().rankingPeriod, "requests")
            val url = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("/api/v1/admin/dashboard/user-breakdown", url.encodedPath)
            assertEquals("requests", url.queryParameter("sort_by"))
            assertNotNull(url.queryParameter("timezone"))
            assertEquals(url.queryParameter("start_date"), url.queryParameter("end_date"))
            assertEquals(12L, result.rows.single().requests)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun deniedAdminAccessIsNotSilentlyReplacedWithPersonalUsage() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"message":"forbidden"}"""))
            val error = runCatching { repository(server).usage("today", admin = true) }.exceptionOrNull()
            assertTrue(error is ApiException)
            assertEquals(403, (error as ApiException).status)
            assertEquals(1, server.requestCount)
            assertEquals("/api/v1/admin/usage/stats", server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!.encodedPath)
        }
    }

    @Test
    fun selectedModelIsSentInAccountTestJsonAndActualModelIsReported() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"type\":\"test_start\",\"model\":\"selected-model\"}\n\ndata: {\"type\":\"test_complete\",\"success\":true}\n\n"))
            val result = repository(server).testAiAccount(7, "selected-model")
            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("/api/v1/admin/accounts/7/test", request.requestUrl!!.encodedPath)
            assertEquals("selected-model", JsonParser.parseString(request.body.readUtf8()).asJsonObject["model_id"].asString)
            assertEquals("selected-model", result.model)
            assertTrue(result.success)
            assertNotNull(result.latencyMs)
        }
    }

    @Test
    fun loadsAccountSpecificModelsInsteadOfHardcodingAProviderList() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(response("""[{"id":"custom-upstream-model","display_name":"Custom"}]"""))
            assertEquals("custom-upstream-model", repository(server).accountModels(8).single().modelId)
            assertEquals("/api/v1/admin/accounts/8/models", server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!.encodedPath)
        }
    }

    @Test
    fun schedulingUsesDedicatedWebEndpointAndPreservesAccountStatus() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val repository = repository(server)
            for (schedulable in listOf(false, true)) {
                server.enqueue(response("""{"id":7,"status":"error","schedulable":$schedulable,"priority":8}"""))
                val updated = repository.setAiAccountSchedulable(7, schedulable)
                val request = server.takeRequest(5, TimeUnit.SECONDS)!!
                assertEquals("POST", request.method)
                assertEquals("/api/v1/admin/accounts/7/schedulable", request.requestUrl!!.encodedPath)
                val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
                assertEquals(setOf("schedulable"), body.keySet())
                assertEquals(schedulable, body["schedulable"].asBoolean)
                assertEquals(schedulable, updated.schedulable)
                assertEquals("error", updated.status)
                assertEquals(8, updated.priority)
            }
        }
    }

    @Test
    fun priorityOnlyEditDoesNotOverwriteSchedulingStatusOrCredentials() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(response("""{"id":7,"priority":-3,"status":"active","schedulable":false}"""))
            val result = repository(server).updateAiAccount(7, UpdateAiAccountRequest(priority = -3))
            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("PUT", request.method)
            assertEquals("/api/v1/admin/accounts/7", request.requestUrl!!.encodedPath)
            val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
            assertEquals(setOf("priority"), body.keySet())
            assertEquals(-3, body["priority"].asInt)
            assertEquals(-3, result.priority)
            assertFalse(result.schedulable)
        }
    }
}
