package com.wonderfood.core.data.backend

public object NotionUrlParser {
    private val compactIdPattern = Regex("^[0-9a-fA-F]{32}$")
    private val dashedIdPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    public fun parse(input: String): NotionPageReference {
        val trimmed = input.trim()
        require(trimmed.isNotBlank()) { "Notion page URL must not be blank." }

        val rawId = extractRawId(trimmed)
            ?: trimmed.takeIf { compactIdPattern.matches(it) || dashedIdPattern.matches(it) }
        require(!rawId.isNullOrBlank()) { "Paste a Notion page link or page ID." }

        val pageId = rawId.normalizeNotionPageId()
        return NotionPageReference(
            pageId = pageId,
            canonicalUrl = "https://www.notion.so/$pageId",
        )
    }

    private fun extractRawId(value: String): String? {
        val clean = value.substringBefore('?').substringBefore('#').trimEnd('/')
        val token = clean.substringAfterLast('/').substringAfterLast('-')
        return token.takeIf { compactIdPattern.matches(it) || dashedIdPattern.matches(it) }
    }

    private fun String.normalizeNotionPageId(): String {
        val compact = replace("-", "").lowercase()
        require(compactIdPattern.matches(compact)) { "Notion page ID must have 32 hex characters." }
        return listOf(
            compact.substring(0, 8),
            compact.substring(8, 12),
            compact.substring(12, 16),
            compact.substring(16, 20),
            compact.substring(20, 32),
        ).joinToString("-")
    }
}

public data class NotionPageReference(
    val pageId: String,
    val canonicalUrl: String,
) {
    init {
        require(pageId.isNotBlank()) { "Notion page ID must not be blank." }
        require(canonicalUrl.isNotBlank()) { "Notion canonical URL must not be blank." }
    }
}
