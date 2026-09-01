package com.ourbloom.app.dashboard

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.ourbloom.app.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.media.MediaRecorder
import android.os.Build
import android.view.MotionEvent
import android.widget.Button
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class MemoriesFragment : Fragment() {

    private val viewModel: MemoriesViewModel by viewModels()
    private lateinit var memoriesAdapter: MemoriesAdapter
    
    private var cameraImageUri: Uri? = null
    private var audioRecorder: MediaRecorder? = null
    private var audioFileUri: Uri? = null
    private var isRecording = false
    
    private val takePicture = registerForActivityResult(object : ActivityResultContracts.TakePicture() {
        override fun createIntent(context: android.content.Context, input: Uri): android.content.Intent {
            val intent = super.createIntent(context, input)
            intent.clipData = android.content.ClipData.newUri(context.contentResolver, "A photo", input)
            intent.addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            return intent
        }
    }) { success ->
        if (success) {
            cameraImageUri?.let { promptForTitleAndUpload(it) }
        }
    }

    private val pickVisualMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            promptForTitleAndUpload(uri)
        }
    }

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            launchCameraIntent()
        } else {
            Toast.makeText(requireContext(), "Camera permission is required to take photos.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_memories, container, false)

        val rvGallery = view.findViewById<RecyclerView>(R.id.rv_memories)
        memoriesAdapter = MemoriesAdapter { memory ->
            showLightbox(memory)
        }
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val tvError = view.findViewById<TextView>(R.id.tv_error)
        val fabAddMemory = view.findViewById<FloatingActionButton>(R.id.fab_add_memory)

        rvGallery.adapter = memoriesAdapter

        viewModel.memories.observe(viewLifecycleOwner) { memories ->
            memoriesAdapter.submitList(memories)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                tvError.text = error
                tvError.visibility = View.VISIBLE
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            } else {
                tvError.visibility = View.GONE
            }
        }

        viewModel.uploadStatus.observe(viewLifecycleOwner) { status ->
            if (status != null) {
                Toast.makeText(requireContext(), status, Toast.LENGTH_SHORT).show()
            }
        }

        fabAddMemory.setOnClickListener {
            showImageSourceDialog()
        }

        return view
    }
    
    override fun onResume() {
        super.onResume()
        viewModel.loadMemories()
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        AlertDialog.Builder(requireContext())
            .setTitle("Add Memory")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchCamera()
                    1 -> launchGallery()
                }
            }
            .show()
    }

    private fun launchCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCameraIntent()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCameraIntent() {
        val file = File(requireContext().cacheDir, "images")
        file.mkdirs()
        val imageFile = File(file, "memory_${System.currentTimeMillis()}.jpg")
        cameraImageUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            imageFile
        )
        cameraImageUri?.let { takePicture.launch(it) }
    }

    private fun launchGallery() {
        pickVisualMedia.launch(PickVisualMediaRequest(
            ActivityResultContracts.PickVisualMedia.ImageOnly
        ))
    }

    private fun promptForTitleAndUpload(imageUri: Uri) {
        val context = requireContext()
        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(64, 32, 64, 16)
        }

        val titleLayout = TextInputLayout(context).apply {
            hint = "Memory Title"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setPadding(0, 0, 0, 32)
        }
        val titleInput = TextInputEditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        titleLayout.addView(titleInput)

        val dateLayout = TextInputLayout(context).apply {
            hint = "Date Label (e.g. Oct 12, 2023)"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setPadding(0, 0, 0, 48)
        }
        val dateInput = TextInputEditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        dateLayout.addView(dateInput)

        val recordButton = Button(context).apply {
            text = "Hold to Record Voice Note"
            setBackgroundColor(resources.getColor(R.color.bloom_primary, null))
            setTextColor(resources.getColor(android.R.color.white, null))
            
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startRecording()
                        text = "Recording..."
                        setBackgroundColor(resources.getColor(android.R.color.holo_red_light, null))
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        stopRecording()
                        text = "Voice Note Attached!"
                        setBackgroundColor(resources.getColor(android.R.color.holo_green_dark, null))
                        true
                    }
                    else -> false
                }
            }
        }

        layout.addView(titleLayout)
        layout.addView(dateLayout)
        layout.addView(recordButton)

        AlertDialog.Builder(context)
            .setTitle("Plant a Memory")
            .setView(layout)
            .setPositiveButton("Add to Gallery") { _, _ ->
                val title = titleInput.text.toString().trim()
                val dateStr = dateInput.text.toString().trim()
                val finalTitle = if (title.isNotEmpty()) title else "New Memory"
                val finalDate = if (dateStr.isNotEmpty()) dateStr else
                    SimpleDateFormat("MMMM d, yyyy", Locale.US).format(Date()).uppercase()
                viewModel.addMemory(imageUri, finalTitle, finalDate, audioFileUri)
                audioFileUri = null // reset
            }
            .setNegativeButton("Cancel") { _, _ ->
                audioFileUri = null
            }
            .show()
    }
    
    private fun startRecording() {
        val file = File(requireContext().cacheDir, "audio_${System.currentTimeMillis()}.3gp")
        audioFileUri = Uri.fromFile(file)
        
        audioRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(requireContext())
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(file.absolutePath)
            try {
                prepare()
                start()
                isRecording = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopRecording() {
        if (isRecording) {
            try {
                audioRecorder?.stop()
                audioRecorder?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            audioRecorder = null
            isRecording = false
        }
    }
}
