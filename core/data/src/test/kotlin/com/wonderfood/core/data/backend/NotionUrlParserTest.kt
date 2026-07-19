package com.wonderfood.core.data.backend

import org.junit.Assert.assertEquals
import org.junit.Test

class NotionUrlParserTest {
    @Test
    fun parsesNotionSlugUrl() {
        val reference = NotionUrlParser.parse(
            "https://www.notion.so/Home-Kitchen-0123456789abcdef0123456789abcdef?pvs=4",
        )

        assertEquals("01234567-89ab-cdef-0123-456789abcdef", reference.pageId)
        assertEquals("https://www.notion.so/01234567-89ab-cdef-0123-456789abcdef", reference.canonicalUrl)
    }

    @Test
    fun acceptsDashedPageId() {
        val reference = NotionUrlParser.parse("01234567-89ab-cdef-0123-456789abcdef")

        assertEquals("01234567-89ab-cdef-0123-456789abcdef", reference.pageId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonNotionPageInput() {
        NotionUrlParser.parse("https://example.com/page")
    }
}
