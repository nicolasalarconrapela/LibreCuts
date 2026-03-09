package com.tharunbirla.librecuts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ProjectAdapter(
    private var projects: List<SavedProject>,
    private val onProjectClick: (SavedProject) -> Unit,
    private val onProjectLongClick: (SavedProject) -> Unit
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

        val sizeKb = (project.sizeBytes / 1024).coerceAtLeast(1)
        val totalSeconds = (project.durationMs / 1000).toInt().coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        holder.tvProjectMeta.text = "${sizeKb} KB · ${String.format("%02d:%02d", minutes, seconds)}"

        holder.itemView.setOnClickListener { onProjectClick(project) }
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
}
