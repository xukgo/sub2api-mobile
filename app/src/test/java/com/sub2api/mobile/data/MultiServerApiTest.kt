package com.sub2api.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MultiServerApiTest {
    @Test
    fun clientsRemainBoundToTheirServerAfterSelectionChanges() = runBlocking {
        MockWebServer().use { a -> MockWebServer().use { b ->
            a.start(); b.start()
            val store = SessionStore(MemorySessionPersistence())
            val factory = ApiClientFactory(store)
            store.saveServer(null, "A", a.url("/").toString())
            store.setTokens(store.state.value, "access-a", "refresh-a", 60)
            val clientA = factory.service(store.state.value)
            val idB = store.saveServer(null, "B", b.url("/").toString())
            store.selectServer(idB)
            store.setTokens(store.state.value, "access-b", "refresh-b", 60)
            val clientB = factory.service(store.state.value)
            a.enqueue(userResponse("a@example.com"))
            b.enqueue(userResponse("b@example.com"))
            assertEquals("a@example.com", clientA.currentUser().body()!!.data!!.email)
            assertEquals("b@example.com", clientB.currentUser().body()!!.data!!.email)
            assertEquals("Bearer access-a", a.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("Authorization"))
            assertEquals("Bearer access-b", b.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("Authorization"))
        } }
    }

    @Test
    fun refreshInFlightOnlyUpdatesItsOriginalServer() = refreshRace(removeOriginal = false)

    @Test
    fun removingServerDuringRefreshDoesNotRestoreItsSession() = refreshRace(removeOriginal = true)

    private fun refreshRace(removeOriginal: Boolean) = runBlocking {
        MockWebServer().use { a -> MockWebServer().use { b ->
            val refreshStarted = CountDownLatch(1)
            val releaseRefresh = CountDownLatch(1)
            a.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.path == "/api/v1/auth/refresh" -> {
                        refreshStarted.countDown()
                        if (!releaseRefresh.await(5, TimeUnit.SECONDS)) MockResponse().setResponseCode(500)
                        else MockResponse().setBody("""{"code":0,"data":{"access_token":"new-a","refresh_token":"new-ra","expires_in":3600}}""")
                    }
                    request.getHeader("Authorization") == "Bearer new-a" -> userResponse("a@example.com")
                    else -> MockResponse().setResponseCode(401)
                }
            }
            a.start(); b.start()
            val store = SessionStore(MemorySessionPersistence())
            val factory = ApiClientFactory(store)
            val idA = store.saveServer(null, "A", a.url("/").toString())
            store.setTokens(store.state.value, "old-a", "old-ra", 60)
            val sessionA = store.state.value
            val clientA = factory.service(sessionA)
            val result = async(Dispatchers.IO) { clientA.currentUser() }
            try {
                assertTrue(refreshStarted.await(5, TimeUnit.SECONDS))
                val idB = store.saveServer(null, "B", b.url("/").toString())
                store.selectServer(idB)
                store.setTokens(store.state.value, "access-b", "refresh-b", 60)
                if (removeOriginal) store.removeServer(idA)
                releaseRefresh.countDown()
                assertEquals(if (removeOriginal) 401 else 200, result.await().code())
                assertEquals("access-b", store.state.value.accessToken)
                assertEquals("refresh-b", store.state.value.refreshToken)
                assertEquals(0, b.requestCount)
                val requests = List(if (removeOriginal) 2 else 3) { a.takeRequest(5, TimeUnit.SECONDS)!! }
                assertTrue(requests[1].body.readUtf8().contains("old-ra"))
                if (removeOriginal) {
                    assertNull(store.sessionFor(sessionA))
                    assertEquals(1, store.profiles.value.size)
                } else {
                    assertEquals("new-a", store.sessionFor(sessionA)!!.accessToken)
                    assertEquals("Bearer new-a", requests[2].getHeader("Authorization"))
                    store.selectServer(idA)
                    assertEquals("new-ra", store.state.value.refreshToken)
                }
            } finally {
                releaseRefresh.countDown()
            }
        } }
    }

    @Test
    fun unauthorizedServerDoesNotLogOutOtherServers() = runBlocking {
        MockWebServer().use { a ->
            a.start()
            val store = SessionStore(MemorySessionPersistence())
            store.saveServer(null, "A", a.url("/").toString())
            store.setTokens(store.state.value, "access-a", "refresh-a", 60)
            val sessionA = store.state.value
            val clientA = ApiClientFactory(store).service(sessionA)
            val b = store.saveServer(null, "B", "https://b.example.com")
            store.selectServer(b)
            store.setTokens(store.state.value, "access-b", "refresh-b", 60)
            a.enqueue(MockResponse().setResponseCode(401))
            a.enqueue(MockResponse().setResponseCode(401))
            assertEquals(401, clientA.currentUser().code())
            assertEquals("access-b", store.state.value.accessToken)
            assertNull(store.sessionFor(sessionA))
        }
    }

    @Test
    fun logoutInFlightClearsOnlyItsOriginalServer() = runBlocking {
        MockWebServer().use { a ->
            val started = CountDownLatch(1)
            val finish = CountDownLatch(1)
            a.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    started.countDown()
                    return if (finish.await(5, TimeUnit.SECONDS)) MockResponse().setBody("""{"code":0,"data":null}""")
                    else MockResponse().setResponseCode(500)
                }
            }
            a.start()
            val store = SessionStore(MemorySessionPersistence())
            val idA = store.saveServer(null, "A", a.url("/").toString())
            store.setTokens(store.state.value, "access-a", "refresh-a", 60)
            val repo = ApiRepository(store, ApiClientFactory(store))
            val logout = async(Dispatchers.IO) { repo.logout() }
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS))
                val idB = store.saveServer(null, "B", "https://b.example.com")
                store.selectServer(idB)
                store.setTokens(store.state.value, "access-b", "refresh-b", 60)
                finish.countDown()
                logout.await()
                assertEquals("access-b", store.state.value.accessToken)
                val request = a.takeRequest(5, TimeUnit.SECONDS)!!
                assertEquals("Bearer access-a", request.getHeader("Authorization"))
                assertTrue(request.body.readUtf8().contains("refresh-a"))
                store.selectServer(idA)
                assertFalse(store.state.value.isAuthenticated)
            } finally {
                finish.countDown()
            }
        }
    }

    @Test
    fun secureStorageFailureDuringRefreshIsReportedAsNetworkFailure() = runBlocking {
        MockWebServer().use { a ->
            a.start()
            var saved = StoredSessions()
            var failWrites = false
            val store = SessionStore(object : SessionPersistence {
                override fun read() = saved
                override fun write(sessions: StoredSessions) {
                    check(!failWrites) { "storage unavailable" }
                    saved = sessions
                }
            })
            store.saveServer(null, "A", a.url("/").toString())
            store.setTokens(store.state.value, "old-access", "old-refresh", 60)
            val client = ApiClientFactory(store).service(store.state.value)
            failWrites = true
            a.enqueue(MockResponse().setResponseCode(401))
            a.enqueue(MockResponse().setBody("""{"code":0,"data":{"access_token":"new-access","refresh_token":"new-refresh"}}"""))
            val result = runCatching { client.currentUser() }
            assertTrue(result.exceptionOrNull() is java.io.IOException)
            assertEquals("old-access", store.state.value.accessToken)
        }
    }

    @Test
    fun redirectCannotForwardLoginBodyToAnotherServer() = runBlocking {
        MockWebServer().use { a -> MockWebServer().use { b ->
            a.start(); b.start()
            val store = SessionStore(MemorySessionPersistence())
            store.saveServer(null, "A", a.url("/").toString())
            a.enqueue(MockResponse().setResponseCode(307).setHeader("Location", b.url("/collect")))
            val response = ApiClientFactory(store).service(store.state.value).login(LoginRequest("test@example.com", "secret"))
            assertEquals(307, response.code())
            assertEquals(0, b.requestCount)
        } }
    }

    private fun userResponse(email: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody("""{"code":0,"data":{"id":1,"email":"$email","username":"test","role":"admin"}}""")
}
