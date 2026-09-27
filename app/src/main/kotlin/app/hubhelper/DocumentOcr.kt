package app.hubhelper

import android.content.Context
import androidx.work.*
import app.hubhelper.data.*
import app.hubhelper.domain.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DocumentOcr(private val context: Context, private val repository: DocumentRepository) {
    fun enqueue(document: WorkDocument) {
        require(!document.originalDeleted) { "Original has been deleted" }
        WorkManager.getInstance(context).enqueueUniqueWork("ocr-${document.id}", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DocumentOcrWorker>().setInputData(workDataOf("documentId" to document.id)).addTag("document-ocr").build())
    }
    fun cancel(document: WorkDocument) { WorkManager.getInstance(context).cancelUniqueWork("ocr-${document.id}") }

    suspend fun recognize(document: WorkDocument): String? = withContext(Dispatchers.IO) {
        StorageCoordinator.mutex.withLock {
            require(repository.get(document.id)?.originalDeleted == false) { "Original unavailable" }
            val pages = HubHelperDatabase.get(context).documentPageDao()
            repository.updateOcr(document.id, document.ocrText, OcrStatus.PROCESSING)
            try {
                val count = DocumentPages.count(document)
                require(count > 0) { "Unsupported document format" }
                val complete = pages.pages(document.id).filter { it.status == "COMPLETE" }.associateBy { it.pageNumber }
                for (index in 0 until count) {
                    currentCoroutineContext().ensureActive()
                    if (complete.containsKey(index + 1)) continue
                    val bitmap = DocumentPages.render(document, index)
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    var blocks = "[]"
                    val text = try {
                        val result = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text> { continuation ->
                            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                                .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
                        }
                        blocks = org.json.JSONArray(result.textBlocks.flatMap { it.lines }.mapIndexed { order, line ->
                            org.json.JSONObject().put("order", order).put("text", line.text).apply {
                                line.boundingBox?.let { box -> put("left", box.left); put("top", box.top); put("right", box.right); put("bottom", box.bottom) }
                                put("imageWidth", bitmap.width); put("imageHeight", bitmap.height)
                            }
                        }).toString()
                        if (document.category == DocumentCategory.ATTENDANCE) attendanceReadingOrder(result) else result.text
                    } finally { recognizer.close(); bitmap.recycle() }
                    pages.put(DocumentPageEntity(documentId = document.id, pageNumber = index + 1, text = text, status = "COMPLETE", error = null, blocksJson = blocks))
                }
                val text = pages.pages(document.id).joinToString("\n\n") { "--- Page ${it.pageNumber} ---\n${it.text}" }
                repository.updateOcr(document.id, text, OcrStatus.COMPLETE)
                text
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) { repository.updateOcr(document.id, document.ocrText, OcrStatus.NOT_STARTED) }
                throw cancelled
            } catch (error: Exception) {
                repository.updateOcr(document.id, document.ocrText, OcrStatus.FAILED)
                throw error
            }
        }
    }

    private fun attendanceReadingOrder(result: com.google.mlkit.vision.text.Text): String {
        data class Line(val text: String, val left: Int, val center: Int, val height: Int)
        val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { line -> line.boundingBox?.let { Line(line.text, it.left, it.centerY(), it.height()) } }
            .sortedWith(compareBy<Line> { it.center }.thenBy { it.left })
        val rows = mutableListOf<MutableList<Line>>()
        lines.forEach { line ->
            val row = rows.lastOrNull()
            if (row != null && kotlin.math.abs(line.center - row.map { it.center }.average()) <= maxOf(line.height, row.maxOf { it.height }) / 2) row.add(line)
            else rows.add(mutableListOf(line))
        }
        return if (rows.isEmpty()) result.text else rows.joinToString("\n") { row -> row.sortedBy { it.left }.joinToString(" ") { it.text } }
    }
}

class DocumentOcrWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = DocumentRepository.create(applicationContext)
        val document = inputData.getString("documentId")?.let { repository.get(it) } ?: return Result.failure()
        if (document.originalDeleted) return Result.failure()
        return try {
            DocumentOcr(applicationContext, repository).recognize(document)
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(workDataOf("error" to (error.message ?: "OCR failed"))) }
    }
}
