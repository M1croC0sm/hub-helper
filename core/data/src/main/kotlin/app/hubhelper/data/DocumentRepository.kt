package app.hubhelper.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.hubhelper.domain.DocumentCategory
import app.hubhelper.domain.OcrStatus
import app.hubhelper.domain.WorkDocument
import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DocumentRepository internal constructor(
    private val context: Context,
    private val dao: DocumentDao,
) {
    companion object {
        const val MULTI_PAGE_MIME = "application/vnd.hubhelper.pages+zip"

        fun create(context: Context): DocumentRepository = DocumentRepository(
            context.applicationContext,
            HubHelperDatabase.get(context).documentDao(),
        )
    }

    val documents: Flow<List<WorkDocument>> = dao.observeAll().map { rows -> rows.map(DocumentEntity::toDomain) }

    suspend fun import(uri: Uri, category: DocumentCategory): WorkDocument = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val originalName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "Imported document"
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        val id = UUID.randomUUID().toString()
        val extension = originalName.substringAfterLast('.', "bin").take(10)
        val directory = File(context.filesDir, "documents").apply { mkdirs() }
        val destination = File(directory, "$id.$extension")
        val digest = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(uri).use { source ->
            requireNotNull(source) { "Unable to read selected document" }
            DigestInputStream(source, digest).use { input -> destination.outputStream().use(input::copyTo) }
        }
        val document = WorkDocument(
            id = id,
            title = originalName.substringBeforeLast('.'),
            category = category,
            mimeType = mimeType,
            originalName = originalName,
            privatePath = destination.absolutePath,
            importedAtEpochMillis = System.currentTimeMillis(),
            sha256 = digest.digest().joinToString("") { "%02x".format(it) },
            ocrText = null,
            ocrStatus = OcrStatus.NOT_STARTED,
        )
        dao.insert(document.toEntity())
        document
    }

    suspend fun importPages(uris: List<Uri>, category: DocumentCategory): WorkDocument = withContext(Dispatchers.IO) {
        require(uris.isNotEmpty()) { "Choose at least one page" }
        if (uris.size == 1) return@withContext import(uris.single(), category)

        val resolver = context.contentResolver
        val id = UUID.randomUUID().toString()
        val directory = File(context.filesDir, "documents").apply { mkdirs() }
        val destination = File(directory, "$id.pages.zip")
        ZipOutputStream(destination.outputStream()).use { zip ->
            uris.forEachIndexed { index, uri ->
                val sourceName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "page-${index + 1}.jpg"
                val extension = sourceName.substringAfterLast('.', "jpg").take(10)
                zip.putNextEntry(ZipEntry("page-${(index + 1).toString().padStart(3, '0')}.$extension"))
                resolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Unable to read page ${index + 1}" }
                    input.copyTo(zip)
                }
                zip.closeEntry()
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        destination.inputStream().use { input ->
            DigestInputStream(input, digest).use { digestInput ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (digestInput.read(buffer) != -1) {
                    // Reading through DigestInputStream updates the checksum.
                }
            }
        }
        val label = when (category) {
            DocumentCategory.ATTENDANCE -> "Attendance sheet"
            DocumentCategory.HOLIDAY_CALENDAR -> "Holiday calendar"
            else -> "${category.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)} document"
        }
        val document = WorkDocument(
            id = id,
            title = "$label (${uris.size} pages)",
            category = category,
            mimeType = MULTI_PAGE_MIME,
            originalName = "$label-${uris.size}-pages.pages.zip",
            privatePath = destination.absolutePath,
            importedAtEpochMillis = System.currentTimeMillis(),
            sha256 = digest.digest().joinToString("") { "%02x".format(it) },
            ocrText = null,
            ocrStatus = OcrStatus.NOT_STARTED,
        )
        dao.insert(document.toEntity())
        document
    }

    suspend fun updateOcr(id: String, text: String?, status: OcrStatus) = dao.updateOcr(id, text, status.name)

    suspend fun restore(
        title: String,
        category: DocumentCategory,
        mimeType: String,
        originalName: String,
        expectedSha256: String,
        ocrText: String?,
        ocrStatus: OcrStatus,
        archivedFile: File,
    ): WorkDocument = withContext(Dispatchers.IO) {
        require(archivedFile.isFile) { "Backup document is missing" }
        val id = UUID.randomUUID().toString()
        val extension = originalName.substringAfterLast('.', "bin").take(10)
        val directory = File(context.filesDir, "documents").apply { mkdirs() }
        val destination = File(directory, "$id.$extension")
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(archivedFile.inputStream(), digest).use { input -> destination.outputStream().use(input::copyTo) }
        val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
        if (expectedSha256.isNotBlank() && !actualSha256.equals(expectedSha256, ignoreCase = true)) {
            destination.delete()
            error("Backup document checksum does not match")
        }
        val document = WorkDocument(
            id = id,
            title = title,
            category = category,
            mimeType = mimeType,
            originalName = originalName,
            privatePath = destination.absolutePath,
            importedAtEpochMillis = System.currentTimeMillis(),
            sha256 = actualSha256,
            ocrText = ocrText,
            ocrStatus = ocrStatus,
        )
        dao.insert(document.toEntity())
        document
    }

    suspend fun delete(document: WorkDocument) = withContext(Dispatchers.IO) {
        val file = File(document.privatePath)
        check(!file.exists() || file.delete()) { "Unable to remove private document file" }
        dao.delete(document.toEntity())
    }

}

private fun DocumentEntity.toDomain() = WorkDocument(
    id, title, DocumentCategory.valueOf(category), mimeType, originalName, privatePath,
    importedAtEpochMillis, sha256, ocrText, OcrStatus.valueOf(ocrStatus),
)

private fun WorkDocument.toEntity() = DocumentEntity(
    id, title, category.name, mimeType, originalName, privatePath,
    importedAtEpochMillis, sha256, ocrText, ocrStatus.name,
)
