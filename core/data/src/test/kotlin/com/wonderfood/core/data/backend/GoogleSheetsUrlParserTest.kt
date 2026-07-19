package com.wonderfood.core.data.backend

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleSheetsUrlParserTest {
    @Test
    fun parsesStandardSheetEditUrl() {
        val reference = GoogleSheetsUrlParser.parse(
            "https://docs.google.com/spreadsheets/d/1AbC_defGHIjklMNopQRstUVWxyz-1234567890/edit#gid=0",
        )

        assertEquals("1AbC_defGHIjklMNopQRstUVWxyz-1234567890", reference.spreadsheetId)
        assertEquals(
            "https://docs.google.com/spreadsheets/d/1AbC_defGHIjklMNopQRstUVWxyz-1234567890/edit",
            reference.canonicalUrl,
        )
    }

    @Test
    fun acceptsRawSpreadsheetId() {
        val reference = GoogleSheetsUrlParser.parse("1AbC_defGHIjklMNopQRstUVWxyz-1234567890")

        assertEquals("1AbC_defGHIjklMNopQRstUVWxyz-1234567890", reference.spreadsheetId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonSheetLink() {
        GoogleSheetsUrlParser.parse("https://example.com/not-a-sheet")
    }
}
