package com.obg.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.mock.web.MockMultipartFile
import java.nio.file.Path

class UploadServiceTest {

    @Test
    fun `saveFile creates unique filenames and saves correctly`(@TempDir tempDir: Path) {
        val service = UploadService()
        service.uploadDir = tempDir.toString()

        val mockFile = MockMultipartFile("file", "test.txt", "text/plain", "content".toByteArray())
        val url = service.saveFile(mockFile, "docs")

        assertTrue(url.startsWith("/uploads/docs/test.txt"))
        
        // Save again to test unique naming
        val url2 = service.saveFile(mockFile, "docs")
        assertTrue(url2.startsWith("/uploads/docs/test_1.txt"))
    }

    @Test
    fun `saveFile handles empty original filename`(@TempDir tempDir: Path) {
        val service = UploadService()
        service.uploadDir = tempDir.toString()

        val mockFile = MockMultipartFile("file", "", "text/plain", "content".toByteArray())
        val url = service.saveFile(mockFile, "docs")

        assertTrue(url.startsWith("/uploads/docs/file"))
    }

    @Test
    fun `deleteFile ignores invalid URLs`(@TempDir tempDir: Path) {
        val service = UploadService()
        service.uploadDir = tempDir.toString()
        assertDoesNotThrow { service.deleteFile(null) }
        assertDoesNotThrow { service.deleteFile("") }
        assertDoesNotThrow { service.deleteFile("/invalid/url") }
    }
}
