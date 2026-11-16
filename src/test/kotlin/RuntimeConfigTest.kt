package com.example

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RuntimeConfigTest {
    @Test
    fun requiredValueIsPreserved() {
        assertEquals(" value with spaces ", environmentValue("TEST_SECRET", environment = { " value with spaces " }))
    }

    @Test
    fun missingRequiredValueFailsWithoutDisclosingSecrets() {
        val error = assertFailsWith<IllegalStateException> {
            environmentValue("TEST_SECRET", environment = { null })
        }
        assertEquals("Environment variable TEST_SECRET must be set", error.message)
    }

    @Test
    fun blankRequiredValueFails() {
        assertFailsWith<IllegalStateException> {
            environmentValue("TEST_SECRET", environment = { "  " })
        }
    }

    @Test
    fun optionalValueUsesLocalDefault() {
        assertEquals("localhost", environmentValue("TEST_HOST", "localhost", environment = { null }))
        assertEquals("localhost", environmentValue("TEST_HOST", "localhost", environment = { "" }))
        assertEquals("custom-host", environmentValue("TEST_HOST", "localhost", environment = { "custom-host" }))
    }
}
