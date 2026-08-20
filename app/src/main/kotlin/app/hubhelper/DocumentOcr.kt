package app.hubhelper

import android.content.Context
import android.net.Uri
import app.hubhelper.data.DocumentRepository
import app.hubhelper.domain.OcrStatus
import app.hubhelper.domain.DocumentCategory
import app.hubhelper.domain.WorkDocument
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class DocumentOcr(
    private val context: Context,
    private val repository: DocumentRepository,
) {
    suspend fun recognize(document: WorkDocument): String? {
        if (document.mimeType == DocumentRepository.MULTI_PAGE_MIME) return recognizePages(document)
        if (!document.mimeType.startsWith("image/")) {
            repository.updateOcr(document.id, null, OcrStatus.UNSUPPORTED)
            return null
        }
        repository.updateOcr(document.id, null, OcrStatus.PROCESSING)
        try {
            val image = InputImage.fromFilePath(context, Uri.fromFile(File(document.privatePath)))
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
            }
            recognizer.close()
            val recognizedText = if (document.category == DocumentCategory.ATTENDANCE) {
                attendanceReadingOrder(result)
            } else {
                result.text
            }
            repository.updateOcr(document.id, recognizedText, OcrStatus.COMPLETE)
            return recognizedText
        } catch (error: Exception) {
            repository.updateOcr(document.id, error.message, OcrStatus.FAILED)
            return null
        }
    }

    private suspend fun recognizePages(document: WorkDocument): String? {
        repository.updateOcr(document.id, null, OcrStatus.PROCESSING)
        val tempDirectory = File(context.cacheDir, "ocr-pages/${UUID.randomUUID()}").apply { mkdirs() }
        return try {
            val pages = mutableListOf<String>()
            ZipInputStream(File(document.privatePath).inputStream()).use { zip ->
                var entry = zip.nextEntry
                var pageNumber = 1
                while (entry != null) {
                    if (!entry.isDirectory && !entry.name.endsWith(".pdf", ignoreCase = true)) {
                        val extension = entry.name.substringAfterLast('.', "jpg").take(10)
                        val pageFile = File(tempDirectory, "page-$pageNumber.$extension")
                        pageFile.outputStream().use(zip::copyTo)
                        recognizeImage(pageFile, document.category)?.takeIf(String::isNotBlank)?.let { text ->
                            pages += "--- Page $pageNumber ---\n$text"
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                    pageNumber++
                }
            }
            if (pages.isEmpty()) {
                repository.updateOcr(document.id, null, OcrStatus.UNSUPPORTED)
                null
            } else {
                pages.joinToString("\n\n").also { repository.updateOcr(document.id, it, OcrStatus.COMPLETE) }
            }
        } catch (error: Exception) {
            repository.updateOcr(document.id, error.message, OcrStatus.FAILED)
            null
        } finally {
            tempDirectory.deleteRecursively()
        }
    }

    private suspend fun recognizeImage(file: File, category: DocumentCategory): String? {
        val image = InputImage.fromFilePath(context, Uri.fromFile(file))
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val result = suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
            }
            if (category == DocumentCategory.ATTENDANCE) attendanceReadingOrder(result) else result.text
        } finally {
            recognizer.close()
        }
    }

    private fun attendanceReadingOrder(result: com.google.mlkit.vision.text.Text): String {
        data class PositionedLine(val text: String, val left: Int, val centerY: Int, val height: Int)
        val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            line.boundingBox?.let { box -> PositionedLine(line.text, box.left, box.centerY(), box.height()) }
        }.sortedWith(compareBy<PositionedLine> { it.centerY }.thenBy { it.left })
        if (lines.isEmpty()) return result.text
        val rows = mutableListOf<MutableList<PositionedLine>>()
        lines.forEach { line ->
            val current = rows.lastOrNull()
            val currentCenter = current?.map { it.centerY }?.average()
            val tolerance = current?.maxOfOrNull { it.height }?.coerceAtLeast(line.height)?.div(2) ?: 0
            if (current != null && currentCenter != null && kotlin.math.abs(line.centerY - currentCenter) <= tolerance) {
                current += line
            } else {
                rows += mutableListOf(line)
            }
        }
        return rows.joinToString("\n") { row -> row.sortedBy { it.left }.joinToString(" ") { it.text } }
    }
}
