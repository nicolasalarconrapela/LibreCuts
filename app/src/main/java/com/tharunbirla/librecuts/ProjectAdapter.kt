package com.tharunbirla.librecuts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProjectAdapter(
    private var projects: List<File>,
    private val onProjectClick: (File) -> Unit
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

        val modified = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(project.lastModified()))
        val sizeKb = (project.length() / 1024).coerceAtLeast(1)
        holder.tvProjectMeta.text = "${sizeKb} KB · ${modified}"

        holder.itemView.setOnClickListener { onProjectClick(project) }
    }

    override fun getItemCount(): Int = projects.size

    fun updateProjects(newProjects: List<File>) {
        projects = newProjects
        notifyDataSetChanged()
    }
}
