package com.tharunbirla.librecuts

import android.media.MediaMetadataRetriever
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class ProjectAdapter(
    private var projects: List<File>,
    private val onProjectClick: (File) -> Unit,
    private val onProjectLongClick: (File) -> Unit
) : RecyclerView.Adapter<ProjectAdapter.ProjectViewHolder>() {

    class ProjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvProjectName: TextView = itemView.findViewById(R.id.tvProjectName)
        val tvProjectMeta: TextView = itemView.findViewById(R.id.tvProjectMeta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_project, parent, false)
        return ProjectViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        val project = projects[position]
        holder.tvProjectName.text = project.name

        val sizeKb = (project.length() / 1024).coerceAtLeast(1)
        val duration = getVideoDuration(project)
        holder.tvProjectMeta.text = "${sizeKb} KB · ${duration}"

        holder.itemView.setOnClickListener { onProjectClick(project) }
        holder.itemView.setOnLongClickListener {
            onProjectLongClick(project)
            true
        }
    }

    override fun getItemCount(): Int = projects.size

    fun updateProjects(newProjects: List<File>) {
        projects = newProjects
        notifyDataSetChanged()
    }

    private fun getVideoDuration(file: File): String {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val totalSeconds = (durationMs / 1000).toInt()
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            String.format("%02d:%02d", minutes, seconds)
        } catch (e: Exception) {
            "--:--"
        } finally {
            retriever.release()
        }
    }
}
