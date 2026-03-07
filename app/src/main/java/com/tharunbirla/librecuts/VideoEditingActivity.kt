package com.tharunbirla.librecuts

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ProgressBar
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.slider.RangeSlider
import com.google.android.material.textfield.TextInputEditText
import com.tharunbirla.librecuts.customviews.CustomVideoSeeker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale


@Suppress("DEPRECATION")
class VideoEditingActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: StyledPlayerView
    private lateinit var tvDuration: TextView
    private lateinit var frameRecyclerView: RecyclerView
    private lateinit var customVideoSeeker: CustomVideoSeeker
    private lateinit var trimPreviewControls: View
    private lateinit var trimRangeSlider: RangeSlider
    private lateinit var btnApplyTrimInline: Button
    private lateinit var btnCancelTrimInline: Button
    private lateinit var btnPrevFrame: ImageButton
    private lateinit var btnNextFrame: ImageButton
    private lateinit var btnCaptureFrame: ImageButton
    private lateinit var btnResetZoom: ImageButton
    private lateinit var btnUndo: Button
    private lateinit var btnRedo: Button
    private lateinit var btnSaveProject: Button
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var playerZoomLevel: Float = 1f
    private var frameStepMs: Long = 33L
    private var videoUri: Uri? = null
    private var videoFileName: String = ""
    private lateinit var tempInputFile: File
    private lateinit var loadingScreen: View
    private lateinit var lottieAnimationView: LottieAnimationView
    private var exportProgressDialog: AlertDialog? = null
    private var exportProgressJob: Job? = null
    private var exportProgressBar: ProgressBar? = null
    private var exportProgressText: TextView? = null
    private var projectSaveProgressDialog: AlertDialog? = null
    private var projectSaveProgressJob: Job? = null
    private var projectSaveProgressBar: ProgressBar? = null
    private var projectSaveProgressText: TextView? = null
    private val projectPrefs by lazy { getSharedPreferences(PROJECT_PREFS, Context.MODE_PRIVATE) }

    private var activeFFmpegSessions = mutableListOf<FFmpegSession>()
    private val undoHistory = mutableListOf<Uri>()
    private val redoHistory = mutableListOf<Uri>()
    private var isVideoLoaded = false
    private var hasPendingRestoredPlaybackState = false
    private var shouldPersistProjectState = true
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_editing)

        // Set fullscreen flags
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

        // Set loading and animation view
        loadingScreen = findViewById(R.id.loadingScreen)
        lottieAnimationView = findViewById(R.id.lottieAnimation)
        try {
            lottieAnimationView.playAnimation()
        } catch (e: Exception) {
            Log.e("LottieError", "Error loading Lottie animation: ${e.message}")
            // Handle the error gracefully
        }

        // Initialize UI components and setup the player
        initializeViews()
        setupExoPlayer()
        setupCustomSeeker()
        setupFrameRecyclerView()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                promptSaveProjectBeforeExit()
            }
        })
    }

    private fun initializeViews() {
        playerView = findViewById(R.id.playerView)
        tvDuration = findViewById(R.id.tvDuration)
        frameRecyclerView = findViewById(R.id.frameRecyclerView)
        customVideoSeeker = findViewById(R.id.customVideoSeeker)
        trimPreviewControls = findViewById(R.id.trimPreviewControls)
        trimRangeSlider = findViewById(R.id.trimRangeSlider)
        btnApplyTrimInline = findViewById(R.id.btnApplyTrimInline)
        btnCancelTrimInline = findViewById(R.id.btnCancelTrimInline)
        btnPrevFrame = findViewById(R.id.btnPrevFrame)
        btnNextFrame = findViewById(R.id.btnNextFrame)
        btnCaptureFrame = findViewById(R.id.btnCaptureFrame)
        btnResetZoom = findViewById(R.id.btnResetZoom)
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)
        btnSaveProject = findViewById(R.id.btnSaveProject)

        setupTrimPreviewControls()
        setupFrameStepControls()
        setupZoomControls()

        // Set up button click listeners
        findViewById<ImageButton>(R.id.btnHome).setOnClickListener { promptSaveProjectBeforeExit() }
        btnSaveProject.setOnClickListener { onSaveProjectClicked() }
        findViewById<ImageButton>(R.id.btnSave).setOnClickListener { saveAction() }
        findViewById<ImageButton>(R.id.btnTrim).setOnClickListener { trimAction() }
        findViewById<ImageButton>(R.id.btnText).setOnClickListener { textAction() }
        findViewById<ImageButton>(R.id.btnAudio).setOnClickListener { audioAction() }
        findViewById<ImageButton>(R.id.btnCrop).setOnClickListener { cropAction() }
        findViewById<ImageButton>(R.id.btnMerge).setOnClickListener { mergeAction() }
    }

    private fun mergeAction() {
        openFilePickerMerge()
    }

    private fun openFilePickerMerge() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "video/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(Intent.createChooser(intent, "Select Video"), PICK_VIDEO_REQUEST)
    }

    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)}\n      with the appropriate {@link ActivityResultContract} and handling the result in the\n      {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_VIDEO_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.let {
                val currentVideoUri = videoUri // The current video URI
                val selectedVideoUris = mutableListOf<Uri>()

                if (it.clipData != null) {
                    val itemCount = it.clipData!!.itemCount
                    for (i in 0 until itemCount) {
                        selectedVideoUris.add(it.clipData!!.getItemAt(i).uri)
                    }
                } else {
                    it.data?.let { selectedUri -> selectedVideoUris.add(selectedUri) }
                }

                if (currentVideoUri != null) {
                    mergeVideos(currentVideoUri, selectedVideoUris)
                } // Pass the URIs to mergeVideos
            }
        }
    }

    private fun mergeVideos(currentVideoUri: Uri, selectedVideoUris: List<Uri>) {
        lifecycleScope.launch {
            try {
                // Fetch metadata for the current video
                val currentMedia = getVideoMetadata(this@VideoEditingActivity, currentVideoUri)
                val currentInputPath = currentMedia.uri.toString()

                val outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!outputDir.exists()) {
                    outputDir.mkdirs()
                }

                val outputPath = File(outputDir, "merged_video_${System.currentTimeMillis()}.mp4").absolutePath
                Log.d("MergeAction", "Output file path: $outputPath")

                // Create a temporary file to store the list of input files
                val listFile = File(cacheDir, "videolist.txt")
                val builder = StringBuilder().append("file '$currentInputPath'\n")
                for (uri in selectedVideoUris) {
                    val selectedMedia = getVideoMetadata(this@VideoEditingActivity, uri)
                    val selectedInputPath = selectedMedia.uri.toString()
                    builder.append("file '$selectedInputPath'\n")
                }
                listFile.writeText(builder.toString())

                // Build the FFmpeg command for merging videos
                val command = "-f concat -safe 0 -i ${listFile.absolutePath} -c:v copy -c:a copy $outputPath"
                Log.d("MergeCommand", "FFmpeg command: $command")

                lifecycleScope.launch {
                    try {
                        // Execute the command in a background thread
                        val session = withContext(Dispatchers.IO) {
                            FFmpegKit.execute(command)
                        }

                        val state = session.state
                        val returnCode = session.returnCode

                        Log.d("FFmpegSession", "FFmpeg process exited with state $state and rc $returnCode.")

                        if (ReturnCode.isSuccess(returnCode)) {
                            Toast.makeText(this@VideoEditingActivity, "Videos merged successfully!", Toast.LENGTH_SHORT).show()

                            // Update video URI to the merged video
                            val previousUri = videoUri
                            videoUri = Uri.fromFile(File(outputPath))
                            recordEditHistory(previousUri, videoUri)
                            persistAutoSavedProjectState(0L)
                            refreshPlayer() // Refresh player with new video
                            refreshUI()     // Refresh UI
                        } else {
                            Log.e("FFmpegError", "Error merging videos: ${session.failStackTrace}")
                            Toast.makeText(this@VideoEditingActivity, "Error merging videos.", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("FFmpegError", "Exception during FFmpeg execution: ${e.message}")
                        Toast.makeText(this@VideoEditingActivity, "Error merging videos: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }



            } catch (e: Exception) {
                Log.e("MetadataError", "Error fetching video metadata: ${e.message}")
                Toast.makeText(this@VideoEditingActivity, "Error fetching video metadata: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }


    @SuppressLint("InflateParams")
    private fun cropAction() {
        // Create BottomSheetDialog
        val bottomSheetDialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.crop_bottom_sheet_dialog, null)

        // Set title
        sheetView.findViewById<TextView>(R.id.tvTitleCrop).text =
            getString(R.string.select_aspect_ratio)

        // Set button click listeners for aspect ratios
        sheetView.findViewById<FrameLayout>(R.id.frameAspectRatio1).setOnClickListener {
            cropVideo("16:9")
            bottomSheetDialog.dismiss()
        }

        sheetView.findViewById<FrameLayout>(R.id.frameAspectRatio2).setOnClickListener {
            cropVideo("9:16")
            bottomSheetDialog.dismiss()
        }

        sheetView.findViewById<FrameLayout>(R.id.frameAspectRatio3).setOnClickListener {
            cropVideo("1:1")
            bottomSheetDialog.dismiss()
        }

        // Set cancel button listener
        sheetView.findViewById<Button>(R.id.btnCancelCrop).setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.setContentView(sheetView)
        bottomSheetDialog.show()
    }

    private fun cropVideo(aspectRatio: String) {
        val currentVideoUri = videoUri
        if (currentVideoUri == null) {
            Toast.makeText(this, "Error retrieving video URI", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val inputPath = getFilePathFromUri(currentVideoUri) ?: currentVideoUri.path
                if (inputPath.isNullOrEmpty()) {
                    showError("Error loading video path")
                    return@launch
                }
                val outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

                if (!outputDir.exists()) {
                    outputDir.mkdirs() // Create output directory if it doesn't exist
                }

                val outputPath = File(outputDir, "cropped_${System.currentTimeMillis()}.mp4").absolutePath
                Log.d("CropAction", "Output file path: $outputPath")

                // Define crop parameters based on aspect ratio
                    val cropCommand = when (aspectRatio) {
                        "16:9" -> "-vf \"crop=iw:iw*9/16\""
                        "9:16" -> "-vf \"crop=ih*9/16:ih\""
                        "1:1" -> "-vf \"crop=iw:iw\""
                        else -> return@launch
                    }

                // Build the FFmpeg command correctly
                val command = "-i \"$inputPath\" $cropCommand -c:a copy \"$outputPath\""
                Log.d("FFmpegCommand", "FFmpeg command: $command")

                executeFFmpegCommand(command, outputPath)

            } catch (e: Exception) {
                Log.e("MetadataError", "Error fetching video metadata: ${e.message}")
                Toast.makeText(this@VideoEditingActivity, "Error fetching video metadata: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun audioAction() {
        TODO("Not yet implemented")
    }

    private fun textAction() {
        FFmpegKitConfig.setFontDirectory(this@VideoEditingActivity, "/system/fonts", null)
        // Create the BottomSheetDialog
        val bottomSheetDialog = BottomSheetDialog(this)

        // Inflate the layout for the bottom sheet
        val view = layoutInflater.inflate(R.layout.text_bottom_sheet_dialog, null)

        // Initialize the EditText, Spinners, and Buttons from the inflated view
        val etTextInput = view.findViewById<TextInputEditText>(R.id.etTextInput)
        val fontSizeInput = view.findViewById<TextInputEditText>(R.id.fontSize)
        val spinnerTextPosition = view.findViewById<Spinner>(R.id.spinnerTextPosition)
        val btnDone = view.findViewById<Button>(R.id.btnDoneText)

        // Define position values for the spinner
        val positionOptions = arrayOf(
            "Bottom Right",
            "Top Right",
            "Top Left",
            "Bottom Left",
            "Center Bottom",
            "Center Top",
            "Center Align"
        )

        // Create an adapter to set the spinner data
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, positionOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTextPosition.adapter = adapter

        btnDone.setOnClickListener {
            // Retrieve the text input, font size, and position selection
            val text = etTextInput.text.toString()
            val fontSize = fontSizeInput.text.toString().toIntOrNull() ?: 16 // Default font size is 16 if invalid input
            val textPosition = spinnerTextPosition.selectedItem.toString()

            // Map the position string to FFmpeg position parameters (x and y coordinates)
            val positionString = when (textPosition) {
                "Bottom Right" -> "x=w-tw:y=h-th"
                "Top Right" -> "x=w-tw:y=0"
                "Top Left" -> "x=0:y=0"
                "Bottom Left" -> "x=0:y=h-th"
                "Center Bottom" -> "x=(w-text_w)/2:y=h-th"
                "Center Top" -> "x=(w-text_w)/2:y=0"
                "Center Align" -> "x=(w-text_w)/2:y=(h-text_h)/2"
                else -> "x=(w-text_w)/2:y=(h-text_h)/2"
            }

            // Show a toast with the entered data (for debugging purposes)
            Toast.makeText(this, "Text: $text, Font Size: $fontSize, Position: $positionString", Toast.LENGTH_SHORT).show()

            // Call function to add the text to the video
            addTextToVideo(text, fontSize, positionString)

            // Close the bottom sheet
            bottomSheetDialog.dismiss()
        }

        // Set the view for the bottom sheet dialog
        bottomSheetDialog.setContentView(view)

        // Show the bottom sheet dialog
        bottomSheetDialog.show()
    }

    private fun addTextToVideo(text: String, fontSize: Int, position: String) {
        lifecycleScope.launch {
            val videoUri = videoUri // Assuming this is your video URI
            val media = videoUri?.let { getVideoMetadata(this@VideoEditingActivity, it) }
            val realFilePath = media?.uri.toString()

            val outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!outputDir.exists()) {
                outputDir.mkdirs() // Create output directory if it doesn't exist
            }
            val outputPath = File(outputDir, "video_with_text_${System.currentTimeMillis()}.mp4").absolutePath
            Log.d("TextAction", "Output file path: $outputPath")

            // FFmpeg command to overlay text on the video
            val command = "-i \"$realFilePath\" -vf \"drawtext=text='$text':fontcolor=white:fontsize=$fontSize:$position\" -c:a copy \"$outputPath\""
            Log.d("FFmpegCommand", "FFmpeg command: $command")
            executeFFmpegCommand(command, outputPath)
        }
    }

    @SuppressLint("InflateParams")
    private fun trimAction() {
        val videoDuration = player.duration
        if (videoDuration <= 0) {
            Toast.makeText(this, "Video duration is invalid.", Toast.LENGTH_SHORT).show()
            return
        }

        val isOpening = trimPreviewControls.visibility != View.VISIBLE
        trimPreviewControls.visibility = if (isOpening) View.VISIBLE else View.GONE

        if (isOpening) {
            configureTrimRangeSlider(videoDuration)
        }
    }

    private fun setupTrimPreviewControls() {
        trimRangeSlider.addOnChangeListener { slider, value, fromUser ->
            if (!fromUser) return@addOnChangeListener

            val start = slider.values[0].toLong() * 1000
            val end = slider.values[1].toLong() * 1000
            if (value == slider.values[0]) {
                player.seekTo(start)
            } else if (value == slider.values[1]) {
                player.seekTo(end)
            }
        }

        btnApplyTrimInline.setOnClickListener {
            val start = trimRangeSlider.values[0].toLong()
            val end = trimRangeSlider.values[1].toLong()
            trimPreviewControls.visibility = View.GONE
            trimVideo(start, end)
        }

        btnCancelTrimInline.setOnClickListener {
            trimPreviewControls.visibility = View.GONE
        }
    }

    private fun configureTrimRangeSlider(videoDuration: Long) {
        val totalSeconds = (videoDuration / 1000f).coerceAtLeast(1f)
        trimRangeSlider.valueFrom = 0f
        trimRangeSlider.valueTo = totalSeconds
        trimRangeSlider.values = listOf(0f, totalSeconds)
        Log.d("RangeSlider", "Value from: ${trimRangeSlider.valueFrom}, Value to: ${trimRangeSlider.valueTo}")
    }

    private fun trimVideo(trimBeginingTime: Long, trimEndTime: Long) {
        lifecycleScope.launch {
            val currentVideoUri = videoUri
            if (currentVideoUri == null) {
                showError("Error loading video")
                return@launch
            }

            val realFilePath = getFilePathFromUri(currentVideoUri) ?: currentVideoUri.path
            if (realFilePath.isNullOrEmpty()) {
                showError("Error loading video path")
                return@launch
            }

            val outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!outputDir.exists()) {
                outputDir.mkdirs() // Create output directory if it doesn't exist
            }
            val outputPath = File(outputDir, "trimmed_video_${System.currentTimeMillis()}.mp4").absolutePath
            Log.d("TrimAction", "Output file path: $outputPath")
            val command = "-ss $trimBeginingTime -i \"$realFilePath\" -to $trimEndTime -c copy \"$outputPath\""
            Log.d("FFmpegCommand", "FFmpeg command: $command")
            executeFFmpegCommand(command, outputPath)
        }
    }

    private fun executeFFmpegCommand(command: String, outputPath: String) {
        coroutineScope.launch {
            loadingScreen.visibility = View.VISIBLE
            try {
                // Cancel any ongoing FFmpeg operations
                FFmpegKit.cancel()

                val session = withContext(Dispatchers.IO) {
                    FFmpegKit.execute(command)
                }

                activeFFmpegSessions.add(session)

                if (ReturnCode.isSuccess(session.returnCode)) {
                    val previousUri = videoUri
                    tempInputFile = File(outputPath)
                    videoUri = Uri.fromFile(File(outputPath))
                    recordEditHistory(previousUri, videoUri)
                    persistAutoSavedProjectState(0L)
                    refreshPlayer()
                    refreshUI()
                } else {
                    showError("Error processing video: ${session.returnCode}")
                    loadingScreen.visibility = View.GONE
                }

                // Remove completed session
                activeFFmpegSessions.remove(session)

            } catch (e: Exception) {
                showError("Error executing command: ${e.message}")
                loadingScreen.visibility = View.GONE
            }
        }
    }

    private fun showError(error: String) {
        Log.e("VideoEditingError", error)
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
    }

    private fun refreshUI() {
        // Update UI elements based on the player's current state
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    customVideoSeeker.setVideoDuration(player.duration)
                    updateDurationDisplay(player.currentPosition.toInt(), player.duration.toInt())
                    extractVideoFrames() // Refresh frame list for the trimmed video
                }
            }
        })

        // Call to extract frames for display
        extractVideoFrames()
    }

    private fun refreshPlayer() {
        player.release() // Release the current player instance

        player = ExoPlayer.Builder(this).build().apply {
            playerView.player = this // Bind player to the player view
            setMediaItem(MediaItem.fromUri(videoUri!!)) // Set the new media item
            prepare() // Prepare the player
            playWhenReady = false // Start playback automatically
            seekTo(0) // Seek to the start of the video
        }

        updateFrameStepFromVideo(videoUri)
        resetPlayerZoom()

        // Update the custom seeker to reflect the new video's duration
        customVideoSeeker.setVideoDuration(player.duration)
        updateDurationDisplay(0, player.duration.toInt()) // Reset duration display
    }


    private fun onSaveProjectClicked() {
        val existingProjectName = projectPrefs.getString(KEY_CURRENT_PROJECT_NAME, null)
        if (existingProjectName.isNullOrBlank()) {
            promptProjectNameAndSave()
        } else {
            saveProjectToDeviceAction(existingProjectName)
        }
    }

    private fun promptProjectNameAndSave() {
        val input = EditText(this).apply {
            hint = getString(R.string.save_project_name_hint)
            setSingleLine(true)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.save_project_name_title))
            .setView(input)
            .setPositiveButton(getString(R.string.save_project_exit)) { _, _ ->
                val rawName = input.text?.toString()?.trim().orEmpty()
                val sanitizedName = rawName.replace(Regex("[^A-Za-z0-9_-]"), "_")
                if (sanitizedName.isBlank()) {
                    showError(getString(R.string.save_project_name_empty))
                    return@setPositiveButton
                }
                projectPrefs.edit().putString(KEY_CURRENT_PROJECT_NAME, sanitizedName).apply()
                saveProjectToDeviceAction(sanitizedName)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun saveProjectToDeviceAction(projectName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val currentVideoUri = videoUri
                if (currentVideoUri == null) {
                    withContext(Dispatchers.Main) { showError(getString(R.string.project_save_error)) }
                    return@launch
                }

                val inputPath = getFilePathFromUri(currentVideoUri) ?: currentVideoUri.path
                if (inputPath.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) { showError(getString(R.string.project_save_error)) }
                    return@launch
                }

                val sourceFile = File(inputPath)
                if (!sourceFile.exists()) {
                    withContext(Dispatchers.Main) { showError(getString(R.string.project_save_error)) }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    showProjectSaveProgressDialog()
                    updateProjectSaveProgress(1)
                }

                projectSaveProgressJob?.cancel()
                projectSaveProgressJob = lifecycleScope.launch {
                    var progress = 1
                    while (isActive && progress < 95) {
                        delay(150)
                        progress += 1
                        updateProjectSaveProgress(progress)
                    }
                }

                val fileName = if (projectName.endsWith(".mp4", true)) projectName else "$projectName.mp4"

                val internalProjectDir = File(filesDir, "projects")
                if (!internalProjectDir.exists()) {
                    internalProjectDir.mkdirs()
                }
                val internalProjectFile = File(internalProjectDir, fileName)
                sourceFile.copyTo(internalProjectFile, overwrite = true)

                val publicProjectDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "LibreCutsProjects"
                )
                if (!publicProjectDir.exists()) {
                    publicProjectDir.mkdirs()
                }
                val publicProjectFile = File(publicProjectDir, fileName)
                sourceFile.copyTo(publicProjectFile, overwrite = true)

                MediaScannerConnection.scanFile(
                    this@VideoEditingActivity,
                    arrayOf(publicProjectFile.absolutePath),
                    arrayOf("video/mp4"),
                    null
                )

                withContext(Dispatchers.Main) {
                    projectSaveProgressJob?.cancel()
                    updateProjectSaveProgress(100)
                    delay(250)
                    dismissProjectSaveProgressDialog()
                    Toast.makeText(this@VideoEditingActivity, getString(R.string.project_saved_device), Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    projectSaveProgressJob?.cancel()
                    dismissProjectSaveProgressDialog()
                    showError("${getString(R.string.project_save_error)}: ${e.message}")
                }
            }
        }
    }

    private fun showProjectSaveProgressDialog() {
        if (projectSaveProgressDialog?.isShowing == true) return

        val dialogView = layoutInflater.inflate(R.layout.export_progress_dialog, null)
        projectSaveProgressBar = dialogView.findViewById(R.id.progressExport)
        projectSaveProgressText = dialogView.findViewById(R.id.tvExportPercent)
        dialogView.findViewById<TextView>(R.id.tvExportTitle).text = getString(R.string.project_saving_title)

        projectSaveProgressDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        projectSaveProgressDialog?.show()
    }

    private fun updateProjectSaveProgress(progress: Int) {
        val safeProgress = progress.coerceIn(1, 100)
        projectSaveProgressBar?.progress = safeProgress
        projectSaveProgressText?.text = "$safeProgress%"
    }

    private fun dismissProjectSaveProgressDialog() {
        projectSaveProgressDialog?.dismiss()
        projectSaveProgressDialog = null
        projectSaveProgressBar = null
        projectSaveProgressText = null
    }

    private fun saveAction() {
        lifecycleScope.launch {
            val currentVideoUri = videoUri
            if (currentVideoUri == null) {
                showError("Error loading video")
                return@launch
            }

            val inputPath = getFilePathFromUri(currentVideoUri) ?: currentVideoUri.path
            if (inputPath.isNullOrEmpty()) {
                showError("Error loading video path")
                return@launch
            }

            val outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val outputPath = File(outputDir, "saved_video_${System.currentTimeMillis()}.mp4").absolutePath
            val command = "-i \"$inputPath\" -map 0 -c:v copy -c:a copy -c:s copy -dn \"$outputPath\""

            showExportProgressDialog()
            updateExportProgress(1)

            exportProgressJob?.cancel()
            exportProgressJob = launch {
                var simulatedProgress = 1
                while (isActive && simulatedProgress < 99) {
                    delay(300)
                    simulatedProgress += 1
                    updateExportProgress(simulatedProgress)
                }
            }

            try {
                val session = withContext(Dispatchers.IO) {
                    FFmpegKit.execute(command)
                }

                exportProgressJob?.cancel()

                if (ReturnCode.isSuccess(session.returnCode)) {
                    val outputFile = File(outputPath)
                    if (!outputFile.exists() || outputFile.length() <= 0L) {
                        dismissExportProgressDialog()
                        showError("Error saving video: output file was not generated")
                        return@launch
                    }

                    MediaScannerConnection.scanFile(
                        this@VideoEditingActivity,
                        arrayOf(outputFile.absolutePath),
                        arrayOf("video/mp4"),
                        null
                    )

                    updateExportProgress(100)
                    delay(300)
                    dismissExportProgressDialog()
                    Toast.makeText(this@VideoEditingActivity, getString(R.string.export_success), Toast.LENGTH_SHORT).show()
                } else {
                    dismissExportProgressDialog()
                    showError("Error saving video: ${session.returnCode}")
                }
            } catch (e: Exception) {
                exportProgressJob?.cancel()
                dismissExportProgressDialog()
                showError("Error saving video: ${e.message}")
            }
        }
    }

    private fun showExportProgressDialog() {
        if (exportProgressDialog?.isShowing == true) return

        val dialogView = layoutInflater.inflate(R.layout.export_progress_dialog, null)
        exportProgressBar = dialogView.findViewById(R.id.progressExport)
        exportProgressText = dialogView.findViewById(R.id.tvExportPercent)

        exportProgressDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        exportProgressDialog?.show()
    }

    private fun updateExportProgress(progress: Int) {
        val safeProgress = progress.coerceIn(1, 100)
        exportProgressBar?.progress = safeProgress
        exportProgressText?.text = "$safeProgress%"
    }

    private fun dismissExportProgressDialog() {
        exportProgressDialog?.dismiss()
        exportProgressDialog = null
        exportProgressBar = null
        exportProgressText = null
    }


    private fun setupFrameStepControls() {
        btnPrevFrame.setOnClickListener { stepFrame(-1) }
        btnNextFrame.setOnClickListener { stepFrame(1) }
        btnCaptureFrame.setOnClickListener { captureCurrentFrameInOriginalQuality() }
        btnResetZoom.setOnClickListener { resetPlayerZoom() }
        btnUndo.setOnClickListener { undoLastEdit() }
        btnRedo.setOnClickListener { redoLastEdit() }
    }

    private fun stepFrame(direction: Int) {
        val duration = player.duration
        if (duration <= 0) return

        val targetPosition = (player.currentPosition + (frameStepMs * direction)).coerceIn(0L, duration)
        player.seekTo(targetPosition)
        updateDurationDisplay(targetPosition.toInt(), duration.toInt())
    }

    private fun updateFrameStepFromVideo(uri: Uri?) {
        if (uri == null) {
            frameStepMs = 33L
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            val step = try {
                val path = getFilePathFromUri(uri) ?: uri.path
                if (!path.isNullOrEmpty()) {
                    retriever.setDataSource(path)
                    val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()
                    if (frameRate != null && frameRate > 0f) {
                        (1000f / frameRate).toLong().coerceAtLeast(1L)
                    } else {
                        33L
                    }
                } else {
                    33L
                }
            } catch (e: Exception) {
                33L
            } finally {
                retriever.release()
            }

            withContext(Dispatchers.Main) {
                frameStepMs = step
            }
        }
    }

    private fun setupZoomControls() {
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newScale = (playerZoomLevel * detector.scaleFactor).coerceIn(1f, 4f)
                applyPlayerZoom(newScale)
                return true
            }
        })

        playerView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP && event.pointerCount <= 1) {
                return@setOnTouchListener false
            }
            scaleGestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun applyPlayerZoom(zoom: Float) {
        playerZoomLevel = zoom
        playerView.videoSurfaceView?.apply {
            scaleX = zoom
            scaleY = zoom
            pivotX = width / 2f
            pivotY = height / 2f
        }
    }

    private fun resetPlayerZoom() {
        applyPlayerZoom(1f)
    }

    private fun captureCurrentFrameInOriginalQuality() {
        lifecycleScope.launch(Dispatchers.IO) {
            val currentVideoUri = videoUri
            if (currentVideoUri == null) {
                withContext(Dispatchers.Main) { showError("Error loading video") }
                return@launch
            }

            val inputPath = getFilePathFromUri(currentVideoUri) ?: currentVideoUri.path
            if (inputPath.isNullOrEmpty()) {
                withContext(Dispatchers.Main) { showError("Error loading video path") }
                return@launch
            }

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(inputPath)
                val frameTimeUs = player.currentPosition * 1000
                val originalFrame = retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)

                if (originalFrame == null) {
                    withContext(Dispatchers.Main) { showError("No fue posible capturar el frame") }
                    return@launch
                }

                val outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                if (!outputDir.exists()) {
                    outputDir.mkdirs()
                }

                val outputFile = File(outputDir, "frame_${System.currentTimeMillis()}.png")
                FileOutputStream(outputFile).use { out ->
                    originalFrame.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VideoEditingActivity, getString(R.string.frame_captured), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showError("Error capturando frame: ${e.message}")
                }
            } finally {
                retriever.release()
            }
        }
    }

    private fun updateHistoryButtonsState() {
        btnUndo.isEnabled = undoHistory.isNotEmpty()
        btnRedo.isEnabled = redoHistory.isNotEmpty()
    }

    private fun recordEditHistory(previousUri: Uri?, newUri: Uri?) {
        if (previousUri == null || newUri == null || previousUri == newUri) {
            updateHistoryButtonsState()
            return
        }
        undoHistory.add(previousUri)
        redoHistory.clear()
        updateHistoryButtonsState()
    }

    private fun undoLastEdit() {
        if (undoHistory.isEmpty() || videoUri == null) {
            Toast.makeText(this, "No hay cambios para deshacer", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUri = videoUri ?: return
        val previousUri = undoHistory.removeLast()
        redoHistory.add(currentUri)
        applyHistoryVideo(previousUri)
    }

    private fun redoLastEdit() {
        if (redoHistory.isEmpty() || videoUri == null) {
            Toast.makeText(this, "No hay cambios para rehacer", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUri = videoUri ?: return
        val nextUri = redoHistory.removeLast()
        undoHistory.add(currentUri)
        applyHistoryVideo(nextUri)
    }

    private fun applyHistoryVideo(targetUri: Uri) {
        videoUri = targetUri
        val filePath = getFilePathFromUri(targetUri) ?: targetUri.path
        if (!filePath.isNullOrEmpty()) {
            tempInputFile = File(filePath)
        }
        persistAutoSavedProjectState(0L)
        refreshPlayer()
        refreshUI()
        updateHistoryButtonsState()
    }

    private fun setupExoPlayer() {
        restoreAutoSavedProjectState()
        if (videoUri == null) {
            videoUri = intent.getParcelableExtra("VIDEO_URI")
            hasPendingRestoredPlaybackState = false
        }
        if (videoUri != null) {
            player = ExoPlayer.Builder(this).build()
            playerView.player = player
            updateHistoryButtonsState()

            val mediaItem = MediaItem.fromUri(videoUri!!)
            player.setMediaItem(mediaItem)
            updateFrameStepFromVideo(videoUri)
            resetPlayerZoom()
            loadingScreen.visibility = View.VISIBLE

            player.prepare()

            // Add listener for player events
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        isVideoLoaded = true
                        customVideoSeeker.setVideoDuration(player.duration)
                        if (hasPendingRestoredPlaybackState) {
                            applyRestoredPlaybackState()
                        }
                        updateDurationDisplay(player.currentPosition.toInt(), player.duration.toInt())
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying && isVideoLoaded) {
                        updateDurationDisplay(player.currentPosition.toInt(), player.duration.toInt())
                    }
                }
            })

            // Initialize video metadata and frames
            initializeVideoData()
        } else {
            showError("Error loading video")
        }
    }

    private fun initializeVideoData() {
        coroutineScope.launch {
            try {
                val videoFilePath = getFilePathFromUri(videoUri!!)
                if (videoFilePath != null) {
                    tempInputFile = File(videoFilePath)
                    videoFileName = tempInputFile.name

                    extractVideoFrames()
                }
            } catch (e: Exception) {
                showError("Error initializing video: ${e.message}")
            }
        }
    }


    private fun getFilePathFromUri(uri: Uri): String? {
        var filePath: String? = null

        when (uri.scheme) {
            "content" -> {
                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val dataIndex = it.getColumnIndex(MediaStore.Video.Media.DATA)
                        if (dataIndex != -1) {
                            filePath = it.getString(dataIndex) // Fetch file path
                        } else {
                            Log.e("PathError", "Column '_data' not found in cursor")
                        }
                    } else {
                        Log.e("PathError", "Cursor is empty for URI: $uri")
                    }
                } ?: Log.e("PathError", "Cursor is null for URI: $uri")
            }
            "file" -> {
                filePath = uri.path // Directly get path from URI
            }
            else -> {
                Log.e("PathError", "Unsupported URI scheme: ${uri.scheme}")
            }
        }

        Log.d("PathInfo", "File path: $filePath")
        return filePath
    }


    private fun setupCustomSeeker() {
        // Configure the custom video seeker for seeking playback
        customVideoSeeker.onSeekListener = { seekPosition ->
            val newSeekTime = (player.duration * seekPosition).toLong()

            // Ensure new seek time is within valid bounds
            if (newSeekTime >= 0 && newSeekTime <= player.duration) {
                player.seekTo(newSeekTime) // Seek to new position
                updateDurationDisplay(newSeekTime.toInt(), player.duration.toInt()) // Update duration display
            } else {
                Log.d("SeekError", "Seek position out of bounds.")
            }
        }
    }

    private fun setupFrameRecyclerView() {
        frameRecyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        frameRecyclerView.adapter = FrameAdapter(emptyList()) // Initialize frame adapter with video name
    }

    private fun extractVideoFrames() {
        lifecycleScope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(tempInputFile.absolutePath)

            val duration = withContext(Dispatchers.Main) { player.duration }
            val frameInterval = duration / 10 // Extract fewer frames

            val frameBitmaps = mutableListOf<Bitmap>()
            val frameCount = 10 // Adjust to change how many frames you extract

            try {
                for (i in 0 until frameCount) {
                    val frameTime = (i * frameInterval) // Time in microseconds
                    val bitmap = retriever.getFrameAtTime(frameTime * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    bitmap?.let {
                        val processedBitmap = Bitmap.createScaledBitmap(it, 200, 150, false)
                        frameBitmaps.add(processedBitmap)
                    }
                }
            } finally {
                retriever.release()
            }

            withContext(Dispatchers.Main) {
                frameRecyclerView.adapter = FrameAdapter(frameBitmaps)
                // Update Loading screen after completion
                loadingScreen.visibility = View.GONE
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateDurationDisplay(current: Int, total: Int) {
        if (!isVideoLoaded || total <= 0) return

        val currentFormatted = formatDuration(current)
        val totalFormatted = formatDuration(total)

        tvDuration.text = "$currentFormatted / $totalFormatted"
    }

    private fun formatDuration(milliseconds: Int): String {
        val minutes = milliseconds / 60000
        val seconds = (milliseconds % 60000) / 1000
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun promptSaveProjectBeforeExit() {
        if (!shouldPersistProjectState) {
            finish()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.exit_editor_title))
            .setMessage(getString(R.string.exit_editor_message))
            .setPositiveButton(getString(R.string.save_project_exit)) { _, _ ->
                shouldPersistProjectState = true
                persistAutoSavedProjectState(forceSnapshot = true)
                finish()
            }
            .setNegativeButton(getString(R.string.discard_project_exit)) { _, _ ->
                shouldPersistProjectState = false
                clearAutoSavedProjectState()
                finish()
            }
            .setNeutralButton(getString(R.string.cancel), null)
            .show()
    }

    private fun persistAutoSavedProjectState(positionOverride: Long? = null, forceSnapshot: Boolean = false) {
        val currentUri = videoUri?.toString() ?: return
        val playbackPosition = positionOverride ?: if (::player.isInitialized) player.currentPosition else 0L

        val editor = projectPrefs.edit()
            .putString(KEY_PROJECT_VIDEO_URI, currentUri)
            .putLong(KEY_PROJECT_POSITION, playbackPosition)
            .putFloat(KEY_PROJECT_ZOOM, playerZoomLevel)

        if (forceSnapshot) {
            val sourcePath = videoUri?.let { getFilePathFromUri(it) ?: it.path }
            if (!sourcePath.isNullOrEmpty()) {
                val sourceFile = File(sourcePath)
                if (sourceFile.exists()) {
                    val projectDir = File(filesDir, "projects")
                    if (!projectDir.exists()) {
                        projectDir.mkdirs()
                    }
                    val snapshotFile = File(projectDir, "autosave_project.mp4")
                    sourceFile.copyTo(snapshotFile, overwrite = true)
                    editor.putString(KEY_PROJECT_SNAPSHOT_PATH, snapshotFile.absolutePath)
                }
            }
        }

        editor.apply()
    }

    private fun restoreAutoSavedProjectState() {
        val snapshotPath = projectPrefs.getString(KEY_PROJECT_SNAPSHOT_PATH, null)
        if (!snapshotPath.isNullOrEmpty()) {
            val snapshotFile = File(snapshotPath)
            if (snapshotFile.exists() && snapshotFile.length() > 0L) {
                videoUri = Uri.fromFile(snapshotFile)
                hasPendingRestoredPlaybackState = true
                return
            }
        }

        val savedUri = projectPrefs.getString(KEY_PROJECT_VIDEO_URI, null)
        if (savedUri.isNullOrEmpty()) return

        val savedFile = Uri.parse(savedUri)
        val path = if (savedFile.scheme == "file") savedFile.path else getFilePathFromUri(savedFile)
        if (path.isNullOrEmpty() || !File(path).exists()) {
            clearAutoSavedProjectState()
            return
        }

        videoUri = savedFile
        hasPendingRestoredPlaybackState = true
    }

    private fun applyRestoredPlaybackState() {
        val savedPosition = projectPrefs.getLong(KEY_PROJECT_POSITION, 0L)
        val savedZoom = projectPrefs.getFloat(KEY_PROJECT_ZOOM, 1f)
        if (savedPosition > 0L) {
            player.seekTo(savedPosition)
        }
        applyPlayerZoom(savedZoom.coerceIn(1f, 4f))
        hasPendingRestoredPlaybackState = false
    }

    private fun clearAutoSavedProjectState() {
        val snapshotPath = projectPrefs.getString(KEY_PROJECT_SNAPSHOT_PATH, null)
        if (!snapshotPath.isNullOrEmpty()) {
            File(snapshotPath).delete()
        }
        projectPrefs.edit().clear().apply()
    }

    override fun onPause() {
        super.onPause()
        if (shouldPersistProjectState) {
            persistAutoSavedProjectState()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel all active FFmpeg sessions
        activeFFmpegSessions.forEach { session ->
            FFmpegKit.cancel(session.sessionId)
        }
        activeFFmpegSessions.clear()

        // Release resources
        if (shouldPersistProjectState) {
            persistAutoSavedProjectState()
        }
        exportProgressJob?.cancel()
        dismissExportProgressDialog()
        projectSaveProgressJob?.cancel()
        dismissProjectSaveProgressDialog()
        if (::player.isInitialized) {
            player.release()
        }
        coroutineScope.cancel()
    }

    private suspend fun getVideoMetadata(context: Context, uri: Uri): Media {
        return withContext(Dispatchers.IO) {
            val contentUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            // Define projection for querying video metadata
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DATA // Fetch the real file path
            )

            val selection = "${MediaStore.Video.Media._ID} = ?"
            val selectionArgs = arrayOf(uri.lastPathSegment)

            context.contentResolver.query(
                contentUri,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val displayNameColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val mimeTypeColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val dataColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

                if (cursor.moveToFirst()) {
                    // Retrieve metadata from cursor
                    val fileName = cursor.getString(displayNameColumnIndex)
                    val size = cursor.getLong(sizeColumnIndex)
                    val mimeType = cursor.getString(mimeTypeColumnIndex)
                    val realFilePath = cursor.getString(dataColumnIndex)

                    // Log metadata for debugging
                    Log.d(TAG, "File Name: $fileName")
                    Log.d(TAG, "File Size: $size bytes")
                    Log.d(TAG, "MIME Type: $mimeType")
                    Log.d("MetadataInfo", "File Name: $fileName, Size: $size bytes, MIME Type: $mimeType, Real File Path: $realFilePath")


                    // Return Media object containing the metadata
                    return@use Media(Uri.parse(realFilePath), fileName, size, mimeType)
                } else {
                    Log.e("MetadataError", "cursor.moveToFirst() returned false")
                    throw Error("cursor.moveToFirst() method returned false")
                }
            } ?: run {
                Log.e("MetadataError", "Unexpected null from contentResolver query")
                throw Error("Unexpected null returned by contentResolver query")
            }
        }
    }

    data class Media(
        val uri: Uri,
        val name: String,
        val size: Long,
        val mimeType: String
    )

    companion object {
        private const val TAG = "VideoMetadata"
        private const val PICK_VIDEO_REQUEST = 1
        private const val PROJECT_PREFS = "librecuts_project_prefs"
        private const val KEY_PROJECT_VIDEO_URI = "project_video_uri"
        private const val KEY_PROJECT_POSITION = "project_position"
        private const val KEY_PROJECT_ZOOM = "project_zoom"
        private const val KEY_PROJECT_SNAPSHOT_PATH = "project_snapshot_path"
        private const val KEY_CURRENT_PROJECT_NAME = "current_project_name"
    }
}
