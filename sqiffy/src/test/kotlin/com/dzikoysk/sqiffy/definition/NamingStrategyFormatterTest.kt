package com.dzikoysk.sqiffy.definition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class NamingStrategyFormatterTest {

    @Test
    fun `should convert snake to camel`() {
        assertEquals("snakeCase", NamingStrategyFormatter.format(NamingStrategy.CAMEL_CASE, "snake_case"))
        assertEquals("createdAt", NamingStrategyFormatter.format(NamingStrategy.CAMEL_CASE, "created_at"))
    }

    @Test
    fun `should convert camel to snake`() {
        assertEquals("camel_case", NamingStrategyFormatter.format(NamingStrategy.SNAKE_CASE, "camelCase"))
        assertEquals("camel_uuid_case", NamingStrategyFormatter.format(NamingStrategy.SNAKE_CASE, "camelUUIDCase"))
    }

}