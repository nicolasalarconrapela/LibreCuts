package com.tharunbirla.librecuts

import android.graphics.Bitmap
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ProjectAdapter(
    private var projects: List<SavedProject>,
    private val onProjectClick: (SavedProject) -> Unit,
    private val onProjectLongClick: (SavedProject) -> Unit,
    private val onProjectDeleteClick: (SavedProject) -> Unit
) : RecyclerView.Adapter<ProjectAdapter.ProjectViewHolder>() {

    private val thumbnailCache = mutableMapOf<String, Bitmap?>()

    class ProjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivProjectThumbnail: ImageView = itemView.findViewById(R.id.ivProjectThumbnail)
        val tvProjectName: TextView = itemView.findViewById(R.id.tvProjectName)
        val tvProjectMeta: TextView = itemView.findViewById(R.id.tvProjectMeta)
        val btnDeleteProject: ImageButton = itemView.findViewById(R.id.btnDeleteProject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_project, parent, false)
        return ProjectViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        val project = projects[position]
        holder.tvProjectName.text = project.name
        holder.tvProjectName.setTypeface(holder.tvProjectName.typeface, Typeface.BOLD)

        val sizeKb = (project.sizeBytes / 1024).coerceAtLeast(1)
        val totalSeconds = (project.durationMs / 1000).toInt().coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        holder.tvProjectMeta.text = "${sizeKb} KB · ${String.format("%02d:%02d", minutes, seconds)}"

        bindProjectThumbnail(holder, project)

        holder.itemView.setOnClickListener { onProjectClick(project) }
        holder.btnDeleteProject.setOnClickListener { onProjectDeleteClick(project) }
        holder.itemView.setOnLongClickListener {
            onProjectLongClick(project)
            true
        }
    }

    override fun getItemCount(): Int = projects.size

    fun updateProjects(newProjects: List<SavedProject>) {
        projects = newProjects
        notifyDataSetChanged()
    }

    private fun bindProjectThumbnail(holder: ProjectViewHolder, project: SavedProject) {
        if (thumbnailCache.containsKey(project.videoUri)) {
            val cached = thumbnailCache[project.videoUri]
            if (cached != null) holder.ivProjectThumbnail.setImageBitmap(cached)
            else holder.ivProjectThumbnail.setImageResource(android.R.drawable.ic_menu_report_image)
            return
        }

        holder.ivProjectThumbnail.setImageResource(android.R.drawable.ic_menu_report_image)
        val generated = generateThumbnail(holder.itemView, project.videoUri)
        thumbnailCache[project.videoUri] = generated

        if (generated != null) {
            holder.ivProjectThumbnail.setImageBitmap(generated)
        }
    }

    private fun generateThumbnail(view: View, videoUri: String): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(view.context, Uri.parse(videoUri))
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            frame
        } catch (_: Exception) {
            null
        }
    }
}
