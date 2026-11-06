package com.sub2api.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerUrlTest {
    @Test
    fun addsHttpsAndApiPath() {
        assertEquals("https://example.com/api/v1/", ServerUrl.normalize("example.com"))
    }

    @Test
    fun keepsExplicitHttpAndExistingApiPath() {
        assertEquals("http://192.168.1.8:8080/api/v1/", ServerUrl.normalize("http://192.168.1.8:8080/api/v1/"))
    }

    @Test
    fun webRootRemovesApiSuffix() {
        assertEquals("https://demo.example.com", ServerUrl.webRoot("https://demo.example.com/api/v1/"))
    }

    @Test
    fun rejectsUnsupportedScheme() {
        assertThrows(IllegalArgumentException::class.java) { ServerUrl.normalize("ftp://example.com") }
    }

    @Test
    fun rejectsBlankInput() {
        assertThrows(IllegalArgumentException::class.java) { ServerUrl.normalize("  ") }
    }

    @Test
    fun rejectsQueryParameters() {
        assertThrows(IllegalArgumentException::class.java) {
            ServerUrl.normalize("https://example.com?token=secret")
        }
    }
    @Test
    fun canonicalizesHostSchemeAndDefaultPortForServiceIdentity() {
        assertEquals("https://example.com/api/v1/", ServerUrl.normalize("HTTPS://EXAMPLE.COM:443/"))
        assertEquals("http://example.com/api/v1/", ServerUrl.normalize("http://EXAMPLE.COM:80"))
        assertEquals("https://example.com/tenant/api/v1/", ServerUrl.normalize("https://example.com/tenant/"))
    }

    @Test
    fun rejectsCredentialsEmbeddedInAddress() {
        assertThrows(IllegalArgumentException::class.java) { ServerUrl.normalize("https://user:password@example.com") }
    }

}
