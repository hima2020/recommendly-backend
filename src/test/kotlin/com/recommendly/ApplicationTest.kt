package com.recommendly

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {

    @Test
    fun `health endpoint returns 200`() = testApplication {
        application {
            // We test only the serialization + routing layers here.
            // DB and Redis are integration-tested separately.
        }
        val response = client.get("/api/v1/health")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
