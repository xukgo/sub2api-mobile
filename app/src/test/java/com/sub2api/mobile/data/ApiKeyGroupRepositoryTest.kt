package com.sub2api.mobile.data

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class ApiKeyGroupRepositoryTest {
    private fun repository(server: MockWebServer): ApiRepository {
        val store = SessionStore(MemorySessionPersistence())
        store.saveServer(null, "Test", server.url("/").toString())
        store.setTokens(store.state.value, "access", "refresh", 3600)
        return ApiRepository(store, ApiClientFactory(store))
    }

    private fun response(data: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody("""{"code":0,"data":$data}""")

    @Test
    fun changingGroupSendsOnlyGroupIdAndReadsUpdatedKey() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(response("""{"id":7,"name":"codex-2","key":"unchanged-key","group_id":42,"status":"inactive","quota":10,"quota_used":2,"group":{"id":42,"name":"Grok"}}"""))

            val updated = repository(server).setKeyGroup(7, Group(id = 42, name = "Grok"))

            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("PUT", request.method)
            assertEquals("/api/v1/keys/7", request.path)
            val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
            assertEquals(setOf("group_id"), body.keySet())
            assertEquals(42L, body["group_id"].asLong)
            assertEquals(42L, updated.groupId)
            assertEquals("Grok", updated.group!!.name)
            assertEquals("unchanged-key", updated.key)
            assertEquals("inactive", updated.status)
            assertEquals(2.0, updated.quotaUsed, 0.0)
        }
    }

    @Test
    fun statusChangeDoesNotSendOrClearGroup() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(response("""{"id":7,"status":"inactive","group_id":42}"""))
            repository(server).setKeyStatus(ApiKey(id = 7, groupId = 42), active = false)
            val body = JsonParser.parseString(server.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8()).asJsonObject
            assertEquals(setOf("status"), body.keySet())
            assertEquals("inactive", body["status"].asString)
        }
    }

    @Test
    fun deniedGroupChangePreservesServerErrorAndCanBeRetried() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val repository = repository(server)
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"message":"Group not allowed"}"""))
            val group = Group(id = 42, name = "Grok")
            val error = runCatching { repository.setKeyGroup(7, group) }.exceptionOrNull()
            assertTrue(error is ApiException)
            assertEquals(403, (error as ApiException).status)
            assertEquals("Group not allowed", error.message)
            server.enqueue(response("""{"id":7,"group_id":42}"""))
            assertEquals(42L, repository.setKeyGroup(7, group).groupId)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun groupOptionsComeFromCurrentUsersAvailableEndpoint() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(response("""[{"id":42,"name":"Grok"}]"""))
            assertEquals(listOf(42L), repository(server).groups().map { it.id })
            assertEquals("/api/v1/groups/available", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
        }
    }

    @Test
    fun staleOrMissingNestedGroupUsesConfirmedNewGroupIdForDisplay() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val repository = repository(server)
            val group = Group(id = 42, name = "Grok")
            for (nested in listOf("", """, "group":{"id":10,"name":"Old group"}""")) {
                server.enqueue(response("""{"id":7,"group_id":42$nested}"""))
                val updated = repository.setKeyGroup(7, group)
                assertEquals(42L, updated.groupId)
                assertEquals(group, updated.group)
            }
        }
    }
}
