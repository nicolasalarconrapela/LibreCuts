package com.tharunbirla.librecuts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tharunbirla.librecuts.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var projectAdapter: ProjectAdapter
    private val selectVideoLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                Log.d("VideoSelection", "Video selected: $uri")
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    Log.w("VideoSelection", "No se pudo persistir permiso del URI: ${e.message}")
                }
                navigateToEditingScreen(uri)
            } else {
                Log.e("VideoSelectionError", "No video selected")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPermissions()
        setupProjectsList()

        binding.addVideoButton.setOnClickListener {
            if (arePermissionsGranted()) {
                Log.d("ButtonClick", "Permissions granted, launching video selection.")
                selectVideo()
            } else {
                Log.w("PermissionCheck", "Permissions not granted, showing request dialog.")
                showPermissionRequestDialog()
            }
        }
    }


    override fun onResume() {
        super.onResume()
        loadSavedProjects()
    }

    private fun setupProjectsList() {
        projectAdapter = ProjectAdapter(
            emptyList(),
            onProjectClick = { project ->
                if (project.videoUri.isBlank()) {
                    showToast(getString(R.string.project_open_error))
                    loadSavedProjects()
                    return@ProjectAdapter
                }
                navigateToEditingScreen(project)
            },
            onProjectLongClick = { project ->
                showProjectCrudDialog(project)
            },
            onProjectDeleteClick = { project ->
                confirmDeleteProject(project)
            }
        )

        binding.rvSavedProjects.layoutManager = LinearLayoutManager(this)
        binding.rvSavedProjects.adapter = projectAdapter
        loadSavedProjects()
    }

    private fun loadSavedProjects() {
        val projectFiles = ProjectStorage.listProjects(this)

        projectAdapter.updateProjects(projectFiles)
        binding.instructionText.text = if (projectFiles.isEmpty()) {
            getString(R.string.no_saved_projects)
        } else {
            getString(R.string.create_engaging_videos)
        }
    }


    private fun showProjectCrudDialog(project: SavedProject) {
        val options = arrayOf(
            getString(R.string.project_action_open),
            getString(R.string.project_action_rename),
            getString(R.string.project_action_delete)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(project.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> navigateToEditingScreen(project)
                    1 -> promptRenameProject(project)
                    2 -> confirmDeleteProject(project)
                }
            }
            .show()
    }

    private fun promptRenameProject(project: SavedProject) {
        val currentName = project.name
        val input = EditText(this).apply {
            setText(currentName)
            setSelection(currentName.length)
            setSingleLine(true)
            hint = getString(R.string.project_rename_hint)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.project_rename_title))
            .setView(input)
            .setPositiveButton(getString(R.string.save_project_exit)) { _, _ ->
                val newNameRaw = input.text?.toString()?.trim().orEmpty()
                val newName = newNameRaw.replace(Regex("[^A-Za-z0-9_-]"), "_")
                if (newName.isBlank()) {
                    showToast(getString(R.string.project_rename_invalid))
                    return@setPositiveButton
                }

                val renamed = ProjectStorage.renameProject(project, newName)
                if (renamed) {
                    showToast(getString(R.string.project_rename_success))
                    loadSavedProjects()
                } else {
                    showToast(getString(R.string.project_rename_error))
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun confirmDeleteProject(project: SavedProject) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.project_delete_title))
            .setMessage(getString(R.string.project_delete_message, project.name))
            .setPositiveButton(getString(R.string.project_action_delete)) { _, _ ->
                val deleted = ProjectStorage.deleteProject(project)

                if (deleted) {
                    showToast(getString(R.string.project_delete_success))
                } else {
                    showToast(getString(R.string.project_delete_error))
                }
                loadSavedProjects()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun setupPermissions() {
        requestPermissionsLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                val allGranted = permissions.values.all { it }
                if (allGranted) {
                    Log.d("PermissionResult", "All permissions granted.")
                    showToast("Permissions granted")
                } else {
                    Log.w("PermissionResult", "Some permissions were denied.")
                    showToast("Some permissions were denied")
                }
            }

        if (!arePermissionsGranted()) {
            Log.i("PermissionSetup", "Requesting permissions.")
            showPermissionRequestDialog()
        }
    }

    private fun arePermissionsGranted(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                checkPermissions(
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                checkPermissions(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }
            else -> {
                checkPermissions(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
        }
    }

    private fun checkPermissions(vararg permissions: String): Boolean {
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }.also { result ->
            Log.d("PermissionCheck", "Permissions checked: $result")
        }
    }

    private fun showPermissionRequestDialog() {
        Log.i("PermissionDialog", "Displaying permission request dialog.")
        MaterialAlertDialogBuilder(this)
            .setTitle("Permissions Required")
            .setMessage("This app needs permissions to access media files and show notifications.")
            .setPositiveButton("Grant") { _, _ -> requestPermissions() }
            .setNegativeButton("Cancel") { dialog, _ ->
                Log.i("PermissionDialog", "User canceled the permission request.")
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestPermissions() {
        val permissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            else -> {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
        }
        Log.d("PermissionRequest", "Requesting permissions: ${permissions.joinToString()}")
        requestPermissionsLauncher.launch(permissions)
    }

    private fun selectVideo() {
        Log.d("VideoSelection", "Launching video selector.")
        selectVideoLauncher.launch(arrayOf("video/*"))
    }

    private fun navigateToEditingScreen(videoUri: Uri) {
        Log.d("Navigation", "Navigating to editing screen with URI: $videoUri")
        val intent = Intent(this, VideoEditingActivity::class.java)
        intent.putExtra("VIDEO_URI", videoUri)
        startActivity(intent)
    }

    private fun navigateToEditingScreen(project: SavedProject) {
        if (project.videoUri.isBlank()) {
            showToast(getString(R.string.project_open_error))
            return
        }
        val intent = Intent(this, VideoEditingActivity::class.java)
        intent.putExtra("VIDEO_URI", Uri.parse(project.videoUri))
        intent.putExtra("PROJECT_POSITION", project.playbackPositionMs)
        intent.putExtra("PROJECT_ZOOM", project.zoom)
        intent.putExtra("PROJECT_NAME", project.name)
        startActivity(intent)
    }

    private fun showToast(message: String) {
        Log.d("ToastMessage", "Showing toast: $message")
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}