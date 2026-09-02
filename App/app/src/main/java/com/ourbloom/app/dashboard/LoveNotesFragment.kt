package com.ourbloom.app.dashboard

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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

    private fun showAddNoteDialog() {
        selectedImageUri = null
        
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_love_note, null)
        val etContent = dialogView.findViewById<EditText>(R.id.et_content)
        val btnAddImage = dialogView.findViewById<Button>(R.id.btn_add_image)
        previewImageView = dialogView.findViewById<ImageView>(R.id.iv_preview)
        
        btnAddImage.setOnClickListener {
            pickImage.launch("image/*")
        }
        
        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Bloom") { _, _ ->
                val content = etContent.text.toString()
                if (content.isNotBlank() || selectedImageUri != null) {
                    viewModel.addLoveNote(requireContext(), content, selectedImageUri)
                } else {
                    Toast.makeText(requireContext(), "Please write a note or select a photo", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
