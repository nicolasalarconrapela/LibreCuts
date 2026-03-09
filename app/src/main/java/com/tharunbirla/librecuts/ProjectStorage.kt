package com.tharunbirla.librecuts

import android.content.Context
import android.os.Environment
import java.io.File

object ProjectStorage {

    private const val INTERNAL_PROJECTS_DIR = "projects"
    private const val PUBLIC_PROJECTS_DIR = "LibreCutsProjects"

    fun getInternalProjectsDir(context: Context): File {
        val dir = File(context.filesDir, INTERNAL_PROJECTS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getPublicProjectsDir(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            PUBLIC_PROJECTS_DIR
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listProjects(context: Context): List<File> {
        val dir = getInternalProjectsDir(context)
        return dir.listFiles { file -> file.isFile && file.extension.equals("mp4", true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun sanitizeProjectName(raw: String): String {
        return raw.trim().replace(Regex("[^A-Za-z0-9_-]"), "_")
    }

    fun buildProjectFileName(projectName: String): String {
        val safe = sanitizeProjectName(projectName)
        return if (safe.endsWith(".mp4", true)) safe else "$safe.mp4"
    }

    fun saveProjectCopies(context: Context, sourceFile: File, projectName: String): Pair<File, File> {
        val fileName = buildProjectFileName(projectName)
        val internalFile = File(getInternalProjectsDir(context), fileName)
        val publicFile = File(getPublicProjectsDir(), fileName)
        sourceFile.copyTo(internalFile, overwrite = true)
        sourceFile.copyTo(publicFile, overwrite = true)
        return internalFile to publicFile
    }

    fun renameProject(context: Context, oldInternalFile: File, newProjectName: String): Boolean {
        val newFileName = buildProjectFileName(newProjectName)
        val newInternalFile = File(oldInternalFile.parentFile, newFileName)
        if (newInternalFile.exists()) return false

        val renamed = oldInternalFile.renameTo(newInternalFile)
        if (!renamed) return false

        val publicDir = getPublicProjectsDir()
        val publicOld = File(publicDir, oldInternalFile.name)
        val publicNew = File(publicDir, newFileName)
        if (publicOld.exists()) {
            publicOld.renameTo(publicNew)
        }
        return true
    }

    fun deleteProject(context: Context, internalFile: File): Boolean {
        val deleted = internalFile.delete()
        val publicFile = File(getPublicProjectsDir(), internalFile.name)
        if (publicFile.exists()) publicFile.delete()
        return deleted
    }

    fun getAutoSaveSnapshotFile(context: Context): File {
        return File(getInternalProjectsDir(context), "autosave_project.mp4")
    }
}
