package app.hubhelper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import app.hubhelper.data.DocumentRepository
import app.hubhelper.domain.WorkDocument
import java.io.File
import java.util.zip.ZipFile

/** Originals are read-only; pages are decoded on demand with bounded dimensions. */
object DocumentPages {
    const val MAX_PAGES = 500
    private const val MAX_IMAGE_BYTES = 30L * 1024 * 1024
    fun count(document: WorkDocument): Int {
        val file = File(document.privatePath)
        if (!file.isFile || document.originalDeleted) return 0
        return when {
            document.mimeType == DocumentRepository.MULTI_PAGE_MIME -> ZipFile(file).use { zip -> zip.entries().asSequence().filter { !it.isDirectory }.sumOf { entry ->
                if (entry.name.endsWith(".pdf", true)) withPdfEntry(zip, entry, file) { renderer -> renderer.pageCount } else 1
            } }
            document.mimeType == "application/pdf" -> PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { it.pageCount }
            document.mimeType.startsWith("image/") -> 1
            else -> 0
        }.also { require(it <= MAX_PAGES) { "Document exceeds $MAX_PAGES pages" } }
    }

    fun render(document: WorkDocument, page: Int): Bitmap {
        require(page >= 0 && page < MAX_PAGES)
        val file = File(document.privatePath)
        return when {
            document.mimeType == "application/pdf" -> PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                renderer.openPage(page).use { source ->
                    val factor = minOf(2f, 2400f / maxOf(source.width, source.height).coerceAtLeast(1))
                    Bitmap.createBitmap((source.width * factor).toInt().coerceAtLeast(1), (source.height * factor).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(android.graphics.Color.WHITE)
                        source.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
            document.mimeType == DocumentRepository.MULTI_PAGE_MIME -> ZipFile(file).use { zip ->
                var offset = 0
                for (entry in zip.entries().asSequence().filter { !it.isDirectory }) {
                    if (entry.name.endsWith(".pdf", true)) {
                        val rendered = withPdfEntry(zip, entry, file) { renderer ->
                            if (page >= offset && page < offset + renderer.pageCount) renderer.openPage(page - offset).use { source ->
                                val factor = minOf(2f, 2400f / maxOf(source.width, source.height).coerceAtLeast(1))
                                Bitmap.createBitmap((source.width * factor).toInt().coerceAtLeast(1), (source.height * factor).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
                                    it.eraseColor(android.graphics.Color.WHITE); source.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            } else { offset += renderer.pageCount; null }
                        }
                        if (rendered != null) return@use rendered
                    } else {
                        if (offset == page) {
                            require(entry.size in 1..MAX_IMAGE_BYTES) { "Unsupported or oversized image page" }
                            return@use zip.getInputStream(entry).use { decode(it.readBytes()) }
                        }
                        offset++
                    }
                }
                error("Page missing")
            }
            else -> { require(file.length() <= MAX_IMAGE_BYTES) { "Image exceeds 30 MB" }; decode(file.readBytes()) }
        }
    }

    private fun <T> withPdfEntry(zip: ZipFile, entry: java.util.zip.ZipEntry, original: File, block: (PdfRenderer) -> T): T {
        require(entry.size in 1..(100L * 1024 * 1024)) { "PDF page collection exceeds 100 MB" }
        val temp = File.createTempFile("render-", ".pdf", original.parentFile)
        try {
            zip.getInputStream(entry).use { input -> temp.outputStream().use { input.copyTo(it) } }
            return PdfRenderer(ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)).use(block)
        } finally { temp.delete() }
    }

    private fun decode(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unreadable image" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }))
        val orientation = bytes.inputStream().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }
        val matrix = Matrix().apply { when (orientation) {
            2 -> setScale(-1f, 1f); 3 -> setRotate(180f); 4 -> setScale(1f, -1f)
            5 -> { setRotate(90f); postScale(-1f, 1f) }; 6 -> setRotate(90f)
            7 -> { setRotate(-90f); postScale(-1f, 1f) }; 8 -> setRotate(-90f)
        } }
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { if (it !== bitmap) bitmap.recycle() }
    }
}
