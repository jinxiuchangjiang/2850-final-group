package com.obg.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

@Service
class UploadService {
    @Value("\${upload.dir:uploads/}")
    lateinit var uploadDir: String

    /** Save an uploaded file and return its public URL path. */
    fun saveFile(file: MultipartFile, subDir: String): String {
        val dir = Paths.get(uploadDir, subDir)
        Files.createDirectories(dir)
        val original = file.originalFilename?.trim()?.ifBlank { null } ?: "file"
        val name = resolveUniqueFilename(dir.toString(), original)
        Files.write(dir.resolve(name), file.bytes)
        return "/uploads/$subDir/$name"
    }


    /**
     * Delete a previously uploaded file by its URL path.
     * Safe to call with null or paths outside the uploads dir (no-op).
     */
    fun deleteFile(url: String?) {
        if (url.isNullOrBlank()) return
        // URL format: /uploads/<subDir>/<filename>
        if (!url.startsWith("/uploads/")) return
        try {
            val relativePath = url.removePrefix("/uploads/")
            val path = Paths.get(uploadDir, relativePath)
            Files.deleteIfExists(path)
        } catch (_: Exception) {
            // Non-fatal: log and continue
        }
    }

    private fun resolveUniqueFilename(dirPath: String, original: String): String {
        val dotIndex = original.lastIndexOf('.')
        val baseName = if (dotIndex >= 0) original.substring(0, dotIndex) else original
        val ext      = if (dotIndex >= 0) original.substring(dotIndex)    else ""  // includes the dot
        // Sanitize: replace spaces and special characters with underscores
        val safeBase = baseName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        var candidate = "$safeBase$ext"
        var counter   = 1
        while (Files.exists(Paths.get(dirPath, candidate))) {
            candidate = "${safeBase}_$counter$ext"
            counter++
        }

        return candidate
    }
}
