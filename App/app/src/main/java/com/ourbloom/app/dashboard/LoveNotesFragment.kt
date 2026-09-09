package com.ourbloom.app.dashboard

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.ourbloom.app.R

class LoveNotesFragment : Fragment() {

    private val viewModel: LoveNotesViewModel by viewModels()
    private lateinit var adapter: LoveNotesAdapter
    
    private var selectedImageUri: Uri? = null
    private var previewImageView: ImageView? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            previewImageView?.setImageURI(uri)
            previewImageView?.visibility = View.VISIBLE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_love_notes, container, false)
        
        val rvNotes = view.findViewById<RecyclerView>(R.id.rv_notes)
        adapter = LoveNotesAdapter()
        adapter.onNoteRevealed = { note ->
            viewModel.markNoteRevealed(note)
        }
        adapter.onNoteLongClick = { note ->
            confirmAndDeleteNote(note)
        }
        rvNotes.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        rvNotes.adapter = adapter

        val fabAddNote = view.findViewById<FloatingActionButton>(R.id.fab_add_note)
        fabAddNote.setOnClickListener {
            showAddNoteDialog()
        }

        viewModel.notes.observe(viewLifecycleOwner) { notes ->
            adapter.submitList(notes)
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMsg ->
            if (errorMsg != null) {
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.loadNotes()
        
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter.stopAudioPlayback()
    }

    private var activeMediaRecorder: android.media.MediaRecorder? = null
    private var recordedAudioFile: java.io.File? = null
    private var recordedAudioDuration = 0

    private fun showAddNoteDialog() {
        selectedImageUri = null
        recordedAudioFile = null
        recordedAudioDuration = 0
        var isRecording = false
        var recordStartTime = 0L
        
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_love_note, null)
        val etContent = dialogView.findViewById<EditText>(R.id.et_content)
        val btnAddImage = dialogView.findViewById<Button>(R.id.btn_add_image)
        val btnRecordVoice = dialogView.findViewById<Button>(R.id.btn_record_voice)
        val layoutVoicePreview = dialogView.findViewById<View>(R.id.layout_voice_preview)
        val tvVoiceStatus = dialogView.findViewById<TextView>(R.id.tv_voice_status)
        val btnRemoveVoice = dialogView.findViewById<ImageButton>(R.id.btn_remove_voice)
        previewImageView = dialogView.findViewById<ImageView>(R.id.iv_preview)
        val switchScratchSecret = dialogView.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switch_scratch_secret)
        
        btnAddImage.setOnClickListener {
            pickImage.launch("image/*")
        }

        btnRecordVoice.setOnClickListener {
            if (!isRecording) {
                try {
                    val outputFile = java.io.File(requireContext().cacheDir, "love_voice_${System.currentTimeMillis()}.m4a")
                    val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        android.media.MediaRecorder(requireContext())
                    } else {
                        @Suppress("DEPRECATION")
                        android.media.MediaRecorder()
                    }
                    recorder.apply {
                        setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                        setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                        setAudioEncodingBitRate(128000)
                        setAudioSamplingRate(44100)
                        setOutputFile(outputFile.absolutePath)
                        prepare()
                        start()
                    }
                    activeMediaRecorder = recorder
                    recordedAudioFile = outputFile
                    recordStartTime = System.currentTimeMillis()
                    isRecording = true
                    btnRecordVoice.text = "⏹️ Stop Recording"
                    btnRecordVoice.setTextColor(android.graphics.Color.parseColor("#FF4D6D"))
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Could not record: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                try {
                    activeMediaRecorder?.stop()
                    activeMediaRecorder?.release()
                } catch (_: Exception) {}
                activeMediaRecorder = null
                isRecording = false
                btnRecordVoice.text = "🎙️ Record Voice"
                btnRecordVoice.setTextColor(ContextCompat.getColor(requireContext(), R.color.bloom_primary))

                val durSec = ((System.currentTimeMillis() - recordStartTime) / 1000).toInt().coerceAtLeast(1)
                recordedAudioDuration = durSec
                layoutVoicePreview.visibility = View.VISIBLE
                tvVoiceStatus.text = "Voice letter attached (${durSec}s) 🎙️"
            }
        }

        btnRemoveVoice.setOnClickListener {
            try {
                recordedAudioFile?.delete()
            } catch (_: Exception) {}
            recordedAudioFile = null
            recordedAudioDuration = 0
            layoutVoicePreview.visibility = View.GONE
        }
        
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Bloom") { _, _ ->
                if (isRecording) {
                    try {
                        activeMediaRecorder?.stop()
                        activeMediaRecorder?.release()
                    } catch (_: Exception) {}
                    activeMediaRecorder = null
                }
                val content = etContent.text.toString()
                val isScratch = switchScratchSecret?.isChecked == true
                if (content.isNotBlank() || selectedImageUri != null || recordedAudioFile != null) {
                    viewModel.addLoveNote(
                        requireContext(), 
                        content, 
                        selectedImageUri, 
                        recordedAudioFile, 
                        recordedAudioDuration,
                        isScratchSecret = isScratch
                    )
                } else {
                    Toast.makeText(requireContext(), "Please write a note, attach a photo, or record a voice letter", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                if (isRecording) {
                    try {
                        activeMediaRecorder?.stop()
                        activeMediaRecorder?.release()
                    } catch (_: Exception) {}
                    activeMediaRecorder = null
                }
                try {
                    recordedAudioFile?.delete()
                } catch (_: Exception) {}
            }
            .create()

        dialog.setOnDismissListener {
            if (isRecording) {
                try {
                    activeMediaRecorder?.stop()
                    activeMediaRecorder?.release()
                } catch (_: Exception) {}
                activeMediaRecorder = null
            }
        }

        dialog.show()
    }

    private fun confirmAndDeleteNote(note: com.ourbloom.app.data.models.LoveNote) {
        val excerpt = if (note.content.length > 40) note.content.take(40) + "..." else note.content.ifBlank { "this love note" }
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Love Note")
            .setMessage("Are you sure you want to delete \"$excerpt\"?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteLoveNote(note) { success ->
                    if (success) {
                        Toast.makeText(requireContext(), "Love note deleted", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to delete note", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
