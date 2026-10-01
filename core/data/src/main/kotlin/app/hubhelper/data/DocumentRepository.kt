package app.hubhelper.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.sync.withLock
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

    suspend fun import(uri: Uri, category: DocumentCategory): WorkDocument = StorageCoordinator.mutex.withLock { importInternal(uri, category) }
    private suspend fun importInternal(uri: Uri, category: DocumentCategory): WorkDocument = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val originalName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "Imported document"
        val mimeType = normalizedDocumentMimeType(resolver.getType(uri), originalName)
        val id = UUID.randomUUID().toString()
        val extension = originalName.substringAfterLast('.', "bin").takeIf { it.matches(Regex("[A-Za-z0-9]{1,10}")) } ?: "bin"
        val directory = File(context.filesDir, "documents").apply { mkdirs() }
        val destination = File(directory, "$id.$extension")
        val digest = MessageDigest.getInstance("SHA-256")
        require((resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1) <= 100L * 1024 * 1024) { "Document exceeds 100 MB" }
        try {
            resolver.openInputStream(uri).use { source ->
                requireNotNull(source) { "Unable to read selected document" }
                DigestInputStream(source, digest).use { input -> destination.outputStream().use { output -> copyLimited(input, output) } }
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
        } catch (failure: Throwable) {
            destination.delete()
            throw failure
        }
    }

    suspend fun importPages(uris: List<Uri>, category: DocumentCategory): WorkDocument = StorageCoordinator.mutex.withLock { importPagesInternal(uris, category) }
    private suspend fun importPagesInternal(uris: List<Uri>, category: DocumentCategory): WorkDocument = withContext(Dispatchers.IO) {
        require(uris.isNotEmpty() && uris.size <= 500) { "Choose between 1 and 500 pages" }
        if (uris.size == 1) return@withContext importInternal(uris.single(), category)

        val resolver = context.contentResolver
        val id = UUID.randomUUID().toString()
        val directory = File(context.filesDir, "documents").apply { mkdirs() }
        val destination = File(directory, "$id.pages.zip")
        try {
            ZipOutputStream(destination.outputStream()).use { zip ->
                uris.forEachIndexed { index, uri ->
                    val reportedMimeType = resolver.getType(uri)
                    val sourceName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: "page-${index + 1}"
                    val extension = supportedDocumentExtension(sourceName, reportedMimeType)
                    zip.putNextEntry(ZipEntry("page-${(index + 1).toString().padStart(3, '0')}.$extension"))
                    resolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Unable to read page ${index + 1}" }
                        copyLimited(input, zip)
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
        } catch (failure: Throwable) {
            destination.delete()
            throw failure
        }
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
        val extension = originalName.substringAfterLast('.', "bin").takeIf { it.matches(Regex("[A-Za-z0-9]{1,10}")) } ?: "bin"
        val directory = File(context.filesDir, "documents").apply { mkdirs() }
        val destination = File(directory, "$id.$extension")
        val digest = MessageDigest.getInstance("SHA-256")
        val journal = RestoreJournal(context)
        val promoted = journal.read()
        promoted.put(destination.absolutePath)
        journal.write(promoted)
        DigestInputStream(archivedFile.inputStream(), digest).use { input -> destination.outputStream().use { output -> copyLimited(input, output) } }
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

    suspend fun get(id: String): WorkDocument? = dao.get(id)?.toDomain()
    suspend fun rename(id: String, title: String) { require(title.isNotBlank()); dao.rename(id, title.trim()) }
    suspend fun delete(document: WorkDocument) = withContext(Dispatchers.IO) {
        StorageCoordinator.mutex.withLock {
            val database = HubHelperDatabase.get(context)
            // Tombstone first: a failed file removal can be retried without hiding the lost-source state.
            database.withTransaction {
                dao.markDeleted(document.id)
                database.documentPageDao().delete(document.id)
                database.businessStateDao().audit(AuditEntryEntity(kind = "source deleted", payload = document.id))
            }
            val file = File(document.privatePath)
            check(!file.exists() || file.delete()) { "Unable to remove private document file; retry deletion" }
        }
    }

}

internal fun normalizedDocumentMimeType(reportedMimeType: String?, originalName: String): String {
    val reported = reportedMimeType?.substringBefore(';')?.trim()?.lowercase()
    val extension = originalName.substringAfterLast('.', "").lowercase()
    return when {
        reported == "application/pdf" || reported?.startsWith("image/") == true -> reported
        extension == "pdf" -> "application/pdf"
        extension in setOf("jpg", "jpeg") -> "image/jpeg"
        extension == "png" -> "image/png"
        extension == "webp" -> "image/webp"
        extension in setOf("heic", "heif") -> "image/heic"
        else -> reported ?: "application/octet-stream"
    }
}

private fun supportedDocumentExtension(originalName: String, reportedMimeType: String?): String {
    val extension = originalName.substringAfterLast('.', "").lowercase()
    if (extension in setOf("pdf", "jpg", "jpeg", "png", "webp", "heic", "heif")) return extension
    return when (normalizedDocumentMimeType(reportedMimeType, originalName)) {
        "application/pdf" -> "pdf"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic", "image/heif" -> "heic"
        else -> "jpg"
    }
}

private fun DocumentEntity.toDomain() = WorkDocument(
    id, title, DocumentCategory.valueOf(category), mimeType, originalName, privatePath,
    importedAtEpochMillis, sha256, ocrText, OcrStatus.valueOf(ocrStatus), originalDeleted,
)

private fun WorkDocument.toEntity() = DocumentEntity(
    id, title, category.name, mimeType, originalName, privatePath,
    importedAtEpochMillis, sha256, ocrText, ocrStatus.name, originalDeleted,
)

private fun copyLimited(input: java.io.InputStream, output: java.io.OutputStream) {
    val buffer = ByteArray(8192)
    var total = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        require(total <= 100L * 1024 * 1024) { "Document exceeds 100 MB" }
        output.write(buffer, 0, count)
    }
}
