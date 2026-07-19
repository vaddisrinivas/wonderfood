package com.wonderfood.core.data.backend

public object GoogleSheetsUrlParser {
    private val idPattern = Regex("^[A-Za-z0-9_-]{20,}$")

    public fun parse(input: String): GoogleSheetReference {
        val trimmed = input.trim()
        require(trimmed.isNotBlank()) { "Google Sheet URL must not be blank." }

        val id = extractPathId(trimmed) ?: trimmed.takeIf { idPattern.matches(it) }
        require(!id.isNullOrBlank()) { "Paste a Google Sheet link or spreadsheet ID." }

        return GoogleSheetReference(
            spreadsheetId = id,
            canonicalUrl = "https://docs.google.com/spreadsheets/d/$id/edit",
        )
    }

    private fun extractPathId(value: String): String? {
        val marker = "/spreadsheets/d/"
        val start = value.indexOf(marker)
        if (start < 0) return null
        val afterMarker = value.substring(start + marker.length)
        val id = afterMarker.substringBefore('/').substringBefore('?').substringBefore('#')
        return id.takeIf { idPattern.matches(it) }
    }
}

public data class GoogleSheetReference(
    val spreadsheetId: String,
    val canonicalUrl: String,
) {
    init {
        require(spreadsheetId.isNotBlank()) { "Google spreadsheet ID must not be blank." }
        require(canonicalUrl.isNotBlank()) { "Google Sheet canonical URL must not be blank." }
    }
}
