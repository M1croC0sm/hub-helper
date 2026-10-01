package app.hubhelper.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentRepositoryTest {
    @Test
    fun `generic provider mime type is inferred from supported filename`() {
        assertEquals("application/pdf", normalizedDocumentMimeType("application/octet-stream", "attendance.pdf"))
        assertEquals("image/jpeg", normalizedDocumentMimeType(null, "attendance.JPG"))
        assertEquals("image/png", normalizedDocumentMimeType("application/octet-stream", "attendance.png"))
    }

    @Test
    fun `specific supported provider mime type remains authoritative`() {
        assertEquals("application/pdf", normalizedDocumentMimeType("application/pdf", "scan.bin"))
        assertEquals("image/heic", normalizedDocumentMimeType("image/heic", "scan.bin"))
    }
}
