package com.wonderfood.app.sync

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

class GoogleDriveAppDataGateway {
    fun uploadBackup(accessToken: String, payload: GoogleDriveBackupPayload): GoogleDriveBackupFile {
        val metadata = JSONObject()
            .put("name", payload.fileName)
            .put("mimeType", BACKUP_MIME_TYPE)
            .put("parents", JSONArray().put(APP_DATA_FOLDER))
            .put(
                "appProperties",
                JSONObject()
                    .put("wonderfood_format", CLOUD_BACKUP_FORMAT)
                    .put("item_count", payload.snapshot.itemCount.toString())
                    .put("created_at_millis", payload.snapshot.createdAtMillis.toString()),
            )
        val body = multipartBody(metadata, payload.bytes, BACKUP_MIME_TYPE)
        val response = request(
            method = "POST",
            url = "$UPLOAD_BASE/files?uploadType=multipart&fields=${url("id,name,modifiedTime,size")}",
            accessToken = accessToken,
            contentType = "multipart/related; boundary=$MULTIPART_BOUNDARY",
            body = body,
        )
        return JSONObject(response.asText()).toBackupFile()
    }

    fun latestBackup(accessToken: String): GoogleDriveBackupFile? {
        val query = "mimeType = '$BACKUP_MIME_TYPE' and trashed = false"
        val response = request(
            method = "GET",
            url = "$DRIVE_BASE/files?spaces=$APP_DATA_FOLDER&pageSize=1&orderBy=modifiedTime desc&q=${url(query)}&fields=${url("files(id,name,modifiedTime,size)")}",
            accessToken = accessToken,
        )
        val files = JSONObject(response.asText()).optJSONArray("files") ?: return null
        if (files.length() == 0) return null
        return files.getJSONObject(0).toBackupFile()
    }

    fun downloadLatestBackup(accessToken: String): GoogleDriveBackupDownload {
        val file = latestBackup(accessToken) ?: error("No WonderFood backup found in Google Drive yet.")
        val bytes = request(
            method = "GET",
            url = "$DRIVE_BASE/files/${urlPath(file.id)}?alt=media",
            accessToken = accessToken,
        )
        return GoogleDriveBackupDownload(file, bytes)
    }

    private fun request(
        method: String,
        url: String,
        accessToken: String,
        contentType: String? = null,
        body: ByteArray? = null,
    ): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType ?: "application/octet-stream")
                setRequestProperty("Content-Length", body.size.toString())
            }
        }
        if (body != null) {
            connection.outputStream.use { it.write(body) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.use { it.readBytes() } ?: ByteArray(0)
        connection.disconnect()
        if (code !in 200..299) {
            val message = response.asText().ifBlank { "HTTP $code" }
            error("Google Drive request failed: $message")
        }
        return response
    }

    private fun multipartBody(metadata: JSONObject, media: ByteArray, mediaType: String): ByteArray =
        ByteArrayOutputStream().use { output ->
            output.writeUtf8("--$MULTIPART_BOUNDARY\r\n")
            output.writeUtf8("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            output.writeUtf8(metadata.toString())
            output.writeUtf8("\r\n--$MULTIPART_BOUNDARY\r\n")
            output.writeUtf8("Content-Type: $mediaType\r\n\r\n")
            output.write(media)
            output.writeUtf8("\r\n--$MULTIPART_BOUNDARY--\r\n")
            output.toByteArray()
        }

    private fun JSONObject.toBackupFile(): GoogleDriveBackupFile =
        GoogleDriveBackupFile(
            id = getString("id"),
            name = optString("name"),
            modifiedTime = optString("modifiedTime"),
            sizeBytes = optString("size").toLongOrNull(),
        )

    private fun ByteArray.asText(): String = toString(StandardCharsets.UTF_8)

    private fun ByteArrayOutputStream.writeUtf8(value: String) {
        write(value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun url(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun urlPath(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    companion object {
        private const val DRIVE_BASE = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
        private const val APP_DATA_FOLDER = "appDataFolder"
        private const val BACKUP_MIME_TYPE = "application/vnd.wonderfood.backup"
        private const val CLOUD_BACKUP_FORMAT = "wonderfood.cloud-backup.v1"
        private const val MULTIPART_BOUNDARY = "wonderfood-drive-sync-boundary"
        private const val TIMEOUT_MILLIS = 30_000
    }
}
