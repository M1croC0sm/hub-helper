package app.hubhelper

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/** Read-only summary. Full validation is always repeated before restore. */
object BackupPreview {
    suspend fun read(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        var total = 0L
        var count = 0
        ZipInputStream(requireNotNull(context.contentResolver.openInputStream(uri))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: error("No backup manifest found")
                require(++count <= BackupFormat.MAX_ENTRIES)
                val bytes = if (entry.name == "manifest.json") ByteArrayOutputStream() else null
                val buffer = ByteArray(8192)
                while (true) {
                    val read = zip.read(buffer); if (read < 0) break
                    total += read; require(total <= BackupFormat.MAX_BYTES) { "Backup too large" }
                    if (bytes != null) { require(bytes.size() + read <= BackupFormat.MAX_MANIFEST_BYTES); bytes.write(buffer, 0, read) }
                }
                if (bytes != null) {
                    val json = JSONObject(bytes.toString("UTF-8"))
                    val version = json.getInt("formatVersion")
                    require(version == 7 || version in BackupFormat.supportedVersions) { "Unsupported backup format" }
                    val data = json.optJSONObject("tables") ?: json
                    val attendance = data.optJSONArray(if(version == 7) "attendance_events" else "attendanceEvents")?.length() ?: 0
                    val documents = data.optJSONArray("documents")?.length() ?: 0
                    return@withContext "Format $version • $attendance attendance records • $documents documents. Original files and all records will be validated before restore."
                }
            }
        }
        @Suppress("UNREACHABLE_CODE") error("No manifest")
    }
}
