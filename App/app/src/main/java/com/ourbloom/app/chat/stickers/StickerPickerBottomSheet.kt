package com.ourbloom.app.chat.stickers

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.ourbloom.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class StickerPickerBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "StickerPickerBottomSheet"
        private const val TAB_RECENTS = 0
        private const val TAB_FAVORITES = 1
        private const val TAB_WHATSAPP = 2

        fun newInstance(onSelected: (File) -> Unit): StickerPickerBottomSheet {
            val sheet = StickerPickerBottomSheet()
            sheet.onStickerSelected = onSelected
            return sheet
        }
    }

    var onStickerSelected: ((File) -> Unit)? = null

    private var currentTab = TAB_WHATSAPP
    private lateinit var adapter: StickerAdapter
    private lateinit var rvStickers: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var tvEmptyMessage: TextView
    private lateinit var tabRecents: TextView
    private lateinit var tabFavorites: TextView
    private lateinit var tabWhatsapp: TextView

    // SAF Document Tree (Folder) picker
    private val pickFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            try {
                requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: Exception) {}

            lifecycleScope.launch {
                val count = StickerManager.importFromTreeUri(requireContext(), uri)
                Toast.makeText(requireContext(), "Imported $count stickers! 🌸", Toast.LENGTH_SHORT).show()
                loadCurrentTab()
            }
        }
    }

    // Multiple .webp files picker
    private val pickFilesLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            lifecycleScope.launch {
                val count = StickerManager.importFromUris(requireContext(), uris)
                Toast.makeText(requireContext(), "Imported $count stickers! 🌸", Toast.LENGTH_SHORT).show()
                loadCurrentTab()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_sticker_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvStickers = view.findViewById(R.id.rv_stickers_grid)
        layoutEmpty = view.findViewById(R.id.layout_sticker_empty)
        tvEmptyMessage = view.findViewById(R.id.tv_empty_message)
        tabRecents = view.findViewById(R.id.tab_recents)
        tabFavorites = view.findViewById(R.id.tab_favorites)
        tabWhatsapp = view.findViewById(R.id.tab_whatsapp)

        rvStickers.layoutManager = GridLayoutManager(requireContext(), 4)
        adapter = StickerAdapter(
            onStickerClick = { sticker ->
                StickerManager.addRecent(requireContext(), sticker.path)
                onStickerSelected?.invoke(sticker.file)
                dismiss()
            },
            onStickerLongClick = { sticker, anchorView ->
                showStickerContextMenu(sticker, anchorView)
            }
        )
        rvStickers.adapter = adapter

        view.findViewById<ImageButton>(R.id.btn_close_stickers).setOnClickListener {
            dismiss()
        }

        view.findViewById<MaterialButton>(R.id.btn_import_whatsapp).setOnClickListener { anchor ->
            showImportOptionsMenu(anchor)
        }

        view.findViewById<MaterialButton>(R.id.btn_auto_scan).setOnClickListener {
            triggerAutoScan()
        }

        view.findViewById<MaterialButton>(R.id.btn_pick_folder).setOnClickListener {
            showImportOptionsMenu(it)
        }

        tabRecents.setOnClickListener { switchTab(TAB_RECENTS) }
        tabFavorites.setOnClickListener { switchTab(TAB_FAVORITES) }
        tabWhatsapp.setOnClickListener { switchTab(TAB_WHATSAPP) }

        // Start on WhatsApp tab, or Recents if available
        val recents = StickerManager.getRecentStickers(requireContext())
        if (recents.isNotEmpty()) {
            switchTab(TAB_RECENTS)
        } else {
            switchTab(TAB_WHATSAPP)
        }
    }

    private fun switchTab(tab: Int) {
        currentTab = tab

        val activeColor = 0xFFE85D75.toInt()
        val inactiveColor = 0xFF78716C.toInt()

        tabRecents.setTextColor(if (tab == TAB_RECENTS) activeColor else inactiveColor)
        tabRecents.setTypeface(null, if (tab == TAB_RECENTS) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)

        tabFavorites.setTextColor(if (tab == TAB_FAVORITES) activeColor else inactiveColor)
        tabFavorites.setTypeface(null, if (tab == TAB_FAVORITES) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)

        tabWhatsapp.setTextColor(if (tab == TAB_WHATSAPP) activeColor else inactiveColor)
        tabWhatsapp.setTypeface(null, if (tab == TAB_WHATSAPP) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)

        loadCurrentTab()
    }

    private fun loadCurrentTab() {
        lifecycleScope.launch {
            val stickers = withContext(Dispatchers.IO) {
                when (currentTab) {
                    TAB_RECENTS -> StickerManager.getRecentStickers(requireContext())
                    TAB_FAVORITES -> StickerManager.getFavoriteStickers(requireContext())
                    else -> StickerManager.getAllWhatsAppStickers(requireContext())
                }
            }

            adapter.submitList(stickers)

            if (stickers.isEmpty()) {
                layoutEmpty.visibility = View.VISIBLE
                rvStickers.visibility = View.GONE
                tvEmptyMessage.text = when (currentTab) {
                    TAB_RECENTS -> "No recently used stickers yet"
                    TAB_FAVORITES -> "No starred stickers yet.\nLong-press any sticker to star it ⭐"
                    else -> "No WhatsApp stickers imported yet"
                }
            } else {
                layoutEmpty.visibility = View.GONE
                rvStickers.visibility = View.VISIBLE
            }
        }
    }

    private fun triggerAutoScan() {
        lifecycleScope.launch {
            Toast.makeText(requireContext(), "Scanning device for WhatsApp stickers...", Toast.LENGTH_SHORT).show()
            val files = withContext(Dispatchers.IO) {
                StickerManager.scanDeviceWhatsAppStickers()
            }
            if (files.isNotEmpty()) {
                val imported = StickerManager.importFiles(requireContext(), files)
                Toast.makeText(requireContext(), "Found and imported $imported stickers! 🌸", Toast.LENGTH_SHORT).show()
                switchTab(TAB_WHATSAPP)
            } else {
                Toast.makeText(requireContext(), "No stickers found directly. Pick folder with SAF!", Toast.LENGTH_LONG).show()
                pickFolderLauncher.launch(null)
            }
        }
    }

    private fun showImportOptionsMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, "⚡ Auto-Scan WhatsApp Storage")
        popup.menu.add(0, 2, 1, "📁 Pick WhatsApp Stickers Folder")
        popup.menu.add(0, 3, 2, "🖼️ Select .webp Sticker Files")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> triggerAutoScan()
                2 -> pickFolderLauncher.launch(null)
                3 -> pickFilesLauncher.launch(arrayOf("image/webp", "*/*"))
            }
            true
        }
        popup.show()
    }

    private fun showStickerContextMenu(sticker: StickerManager.Sticker, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        val isFav = sticker.isFavorite
        popup.menu.add(0, 1, 0, if (isFav) "★ Remove from Starred" else "⭐ Add to Starred")
        popup.menu.add(0, 2, 1, "🗑️ Delete Sticker")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    StickerManager.toggleFavorite(requireContext(), sticker.path)
                    loadCurrentTab()
                }
                2 -> {
                    try {
                        sticker.file.delete()
                        loadCurrentTab()
                    } catch (_: Exception) {}
                }
            }
            true
        }
        popup.show()
    }
}
