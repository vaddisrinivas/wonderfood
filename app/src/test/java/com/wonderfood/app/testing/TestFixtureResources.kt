package com.wonderfood.app.testing

import java.nio.charset.StandardCharsets

object TestFixtureResources {
    fun readText(path: String): String {
        val normalized = path.trimStart('/')
        val loader = Thread.currentThread().contextClassLoader ?: TestFixtureResources::class.java.classLoader
        val stream = loader.getResourceAsStream(normalized)
            ?: error("Missing test resource: $normalized")
        return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    fun probe(path: String): JsonFixtureProbe = JsonFixtureProbe(readText(path))
}

class JsonFixtureProbe(private val raw: String) {
    fun isLikelyJsonObject(): Boolean =
        raw.trim().startsWith("{") && raw.trim().endsWith("}") && balancedJsonDelimiters()

    fun stringValue(key: String): String? {
        val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        return pattern.find(raw)?.groupValues?.get(1)?.replace("\\\"", "\"")
    }

    fun containsKey(key: String): Boolean =
        Regex("\"${Regex.escape(key)}\"\\s*:").containsMatchIn(raw)

    fun countKey(key: String): Int =
        Regex("\"${Regex.escape(key)}\"\\s*:").findAll(raw).count()

    fun containsForbiddenTestData(): Boolean {
        val forbidden = listOf(
            "api_key",
            "authorization",
            "bearer ",
            "password",
            "token",
            "secret",
            "@gmail.com",
            "@outlook.com",
            "@icloud.com",
            "notion",
            "john.doe@",
        )
        val lower = raw.lowercase()
        return forbidden.any { it in lower }
    }

    private fun balancedJsonDelimiters(): Boolean {
        var braceDepth = 0
        var bracketDepth = 0
        var inString = false
        var escaping = false
        raw.forEach { char ->
            when {
                escaping -> escaping = false
                inString && char == '\\' -> escaping = true
                char == '"' -> inString = !inString
                !inString && char == '{' -> braceDepth++
                !inString && char == '}' -> braceDepth--
                !inString && char == '[' -> bracketDepth++
                !inString && char == ']' -> bracketDepth--
            }
            if (braceDepth < 0 || bracketDepth < 0) return false
        }
        return braceDepth == 0 && bracketDepth == 0 && !inString && !escaping
    }
}
