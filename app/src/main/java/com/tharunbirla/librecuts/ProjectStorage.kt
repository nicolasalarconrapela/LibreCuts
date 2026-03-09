package com.tharunbirla.librecuts

import android.content.Context
import org.json.JSONObject
import java.io.File

data class SavedProject(
    val file: File,
    val name: String,
    val videoUri: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val playbackPositionMs: Long,
    val zoom: Float,
    val updatedAt: Long
)

object ProjectStorage {

    private const val INTERNAL_PROJECTS_DIR = "projects"

    fun getInternalProjectsDir(context: Context): File {
        val dir = File(context.filesDir, INTERNAL_PROJECTS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun sanitizeProjectName(raw: String): String {
        return raw.trim().replace(Regex("[^A-Za-z0-9_-]"), "_")
    }

    fun buildProjectFileName(projectName: String): String {
        val safe = sanitizeProjectName(projectName)
        return if (safe.endsWith(".lcp", true)) safe else "$safe.lcp"
    }

    fun listProjects(context: Context): List<SavedProject> {
        val dir = getInternalProjectsDir(context)
        val files = dir.listFiles { file -> file.isFile && file.extension.equals("lcp", true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

        return files.mapNotNull { parseProjectFile(it) }
    }

    fun saveProjectMetadata(
        context: Context,
        projectName: String,
        videoUri: String,
        sizeBytes: Long,
        durationMs: Long,
        playbackPositionMs: Long,
        zoom: Float
    ): File {
        val fileName = buildProjectFileName(projectName)
        val file = File(getInternalProjectsDir(context), fileName)

        val json = JSONObject()
            .put("name", projectName)
            .put("videoUri", videoUri)
            .put("sizeBytes", sizeBytes)
            .put("durationMs", durationMs)
            .put("playbackPositionMs", playbackPositionMs)
            .put("zoom", zoom)
            .put("updatedAt", System.currentTimeMillis())

        file.writeText(json.toString())
        return file
    }

    fun renameProject(project: SavedProject, newProjectName: String): Boolean {
        val newFileName = buildProjectFileName(newProjectName)
        val newFile = File(project.file.parentFile, newFileName)
        if (newFile.exists()) return false

        val renamed = project.file.renameTo(newFile)
        if (!renamed) return false

        val updatedJson = JSONObject(newFile.readText()).put("name", newProjectName)
        newFile.writeText(updatedJson.toString())
        return true
    }

    fun deleteProject(project: SavedProject): Boolean {
        return project.file.delete()
    }

    private fun parseProjectFile(file: File): SavedProject? {
        return try {
            val json = JSONObject(file.readText())
            SavedProject(
                file = file,
                name = json.optString("name", file.nameWithoutExtension),
                videoUri = json.optString("videoUri", ""),
                sizeBytes = json.optLong("sizeBytes", 0L),
                durationMs = json.optLong("durationMs", 0L),
                playbackPositionMs = json.optLong("playbackPositionMs", 0L),
                zoom = json.optDouble("zoom", 1.0).toFloat(),
                updatedAt = json.optLong("updatedAt", file.lastModified())
            )
        } catch (e: Exception) {
            null
        }
    }
}
