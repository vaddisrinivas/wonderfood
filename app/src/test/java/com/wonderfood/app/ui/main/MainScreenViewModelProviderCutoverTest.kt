package com.wonderfood.app.ui.main

import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenViewModelProviderCutoverTest {
    private val source: String =
        String(
            Files.readAllBytes(mainScreenViewModelPath()),
            StandardCharsets.UTF_8,
        )

    @Test
    fun googleSheetsConnectUsesOnlyV4WorkspaceImportAndGraphExport() {
        val method = source.methodBody("fun connectGoogleSheetsBackend(")

        assertTrue(method.contains("createBackendSwitchSafety(\"Google Sheets\")"))
        assertTrue(method.contains("readWorkspaceRows("))
        assertTrue(method.contains("GoogleSheetsV4InboundWorkspaceImporter.importRows("))
        assertTrue(method.contains("exportGraph("))
        assertFalse(method.contains("readRemoteWorkspaceMerge("))
        assertFalse(method.contains("WonderFoodSnapshot"))
        assertFalse(method.contains("readRemoteSnapshot("))
        assertFalse(method.contains("rawSnapshotImportPreview"))
        assertFalse(method.contains("Google Sheets import check failed"))
    }

    @Test
    fun notionConnectUsesOnlyV4WorkspaceImportAndGraphExport() {
        val method = source.methodBody("fun connectNotionBackend(")

        assertTrue(method.contains("createBackendSwitchSafety(\"Notion\")"))
        assertTrue(method.contains("readWorkspaceRows("))
        assertTrue(method.contains("GoogleSheetsV4InboundWorkspaceImporter.importRows("))
        assertTrue(method.contains("exportWorkspace("))
        assertFalse(method.contains("readRemoteWorkspaceMerge("))
        assertFalse(method.contains("WonderFoodSnapshot"))
        assertFalse(method.contains("readRemoteSnapshot("))
        assertFalse(method.contains("rawSnapshotImportPreview"))
        assertFalse(method.contains("Notion import check failed"))
    }

    private fun String.methodBody(signature: String): String {
        val signatureIndex = indexOf(signature)
        require(signatureIndex >= 0) { "Missing method signature: $signature" }
        val bodyStart = indexOf('{', signatureIndex)
        require(bodyStart >= 0) { "Missing method body: $signature" }

        var depth = 0
        for (index in bodyStart until length) {
            when (this[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return substring(bodyStart, index + 1)
                }
            }
        }
        error("Unterminated method body: $signature")
    }

    private fun mainScreenViewModelPath(): Path =
        listOf(
            Path.of("src/main/java/com/wonderfood/app/ui/main/MainScreenViewModel.kt"),
            Path.of("app/src/main/java/com/wonderfood/app/ui/main/MainScreenViewModel.kt"),
        ).first(Files::exists)
}
