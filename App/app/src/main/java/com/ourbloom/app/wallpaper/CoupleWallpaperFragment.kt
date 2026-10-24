package com.ourbloom.app.wallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.firebase.firestore.ListenerRegistration
import com.ourbloom.app.R
import com.ourbloom.app.data.FirestoreRepository
import com.ourbloom.app.data.models.Couple
import com.ourbloom.app.data.models.CoupleWallpaperConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

class CoupleWallpaperFragment : Fragment() {

    private val repository = FirestoreRepository()
    private var coupleId: String = ""
    private var currentUserId: String = ""
    private var currentUserName: String = ""
    private var partnerName: String = "Sweetheart"
    private var coupleStartDateMs: Long = System.currentTimeMillis()
    private var daysTogether: Long = 1L
    private var anniversaryDateStr: String = ""
    private var coupleNamesDisplay: String = "Narayan & Tanaya 🌸"

    private var currentConfig = CoupleWallpaperConfig()
    private var currentSourceBitmap: Bitmap? = null
    private var lastGeneratedWallpaper: Bitmap? = null
    private var wallpaperListener: ListenerRegistration? = null
    private var selectedLocalUri: Uri? = null

    // Views
    private lateinit var ivWallpaperPreview: ImageView
    private lateinit var tvWallpaperStatusBadge: TextView
    private lateinit var tvPreviewDate: TextView
    private lateinit var pbWallpaperLoading: ProgressBar
    private lateinit var switchDaysCounter: MaterialSwitch
    private lateinit var switchCoupleNames: MaterialSwitch
    private lateinit var sliderDimLevel: Slider

    private val pickPhotoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedLocalUri = uri
            loadBitmapFromUri(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_couple_wallpaper, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)

        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        tvPreviewDate.text = dateFormat.format(Date())

        lifecycleScope.launch {
            val user = repository.getCurrentUser()
            if (user != null) {
                currentUserId = user.uid
                currentUserName = user.name
                coupleId = user.coupleId ?: ""

                if (coupleId.isNotBlank()) {
                    val couple = repository.getCouple(coupleId)
                    setupCoupleDetails(couple)
                    startListeningToWallpaperConfig()
                } else {
                    renderWallpaperPreview()
                }
            } else {
                renderWallpaperPreview()
            }
        }
    }

    private fun initViews(view: View) {
        ivWallpaperPreview = view.findViewById(R.id.iv_wallpaper_preview)
        tvWallpaperStatusBadge = view.findViewById(R.id.tv_wallpaper_status_badge)
        tvPreviewDate = view.findViewById(R.id.tv_preview_date)
        pbWallpaperLoading = view.findViewById(R.id.pb_wallpaper_loading)
        switchDaysCounter = view.findViewById(R.id.switch_days_counter)
        switchCoupleNames = view.findViewById(R.id.switch_couple_names)
        sliderDimLevel = view.findViewById(R.id.slider_dim_level)

        view.findViewById<ImageButton>(R.id.btn_back_wallpaper).setOnClickListener {
            findNavController().navigateUp()
        }

        view.findViewById<MaterialButton>(R.id.btn_pick_photo).setOnClickListener {
            pickPhotoLauncher.launch("image/*")
        }

        view.findViewById<MaterialButton>(R.id.btn_theme_presets).setOnClickListener {
            showThemePresetsDialog()
        }

        switchDaysCounter.setOnCheckedChangeListener { _, isChecked ->
            currentConfig = currentConfig.copy(showDaysCounter = isChecked)
            renderWallpaperPreview()
        }

        switchCoupleNames.setOnCheckedChangeListener { _, isChecked ->
            currentConfig = currentConfig.copy(showCoupleNames = isChecked)
            renderWallpaperPreview()
        }

        sliderDimLevel.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                currentConfig = currentConfig.copy(dimLevel = value)
                renderWallpaperPreview()
            }
        }

        // Apply Buttons
        view.findViewById<MaterialButton>(R.id.btn_set_both).setOnClickListener {
            applyWallpaperToDevice(WallpaperTarget.BOTH)
        }

        view.findViewById<MaterialButton>(R.id.btn_set_lock).setOnClickListener {
            applyWallpaperToDevice(WallpaperTarget.LOCK_SCREEN)
        }

        view.findViewById<MaterialButton>(R.id.btn_set_home).setOnClickListener {
            applyWallpaperToDevice(WallpaperTarget.HOME_SCREEN)
        }

        view.findViewById<MaterialButton>(R.id.btn_save_gallery).setOnClickListener {
            saveWallpaperToGallery()
        }

        view.findViewById<MaterialButton>(R.id.btn_sync_partner).setOnClickListener {
            syncWallpaperWithPartner()
        }
    }

    private suspend fun setupCoupleDetails(couple: Couple?) {
        if (couple == null) return
        val partnerId = if (couple.user1 == currentUserId) couple.user2 else couple.user1
        val partnerUser = if (partnerId.isNotBlank()) repository.getUser(partnerId) else null
        val pName = partnerUser?.name?.ifBlank { "Sweetheart" } ?: "Sweetheart"
        partnerName = pName

        val u1 = currentUserName.ifBlank { "You" }
        val u2 = pName
        coupleNamesDisplay = "$u1 & $u2 🌸"

        // Parse start date
        try {
            val formatIn = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = formatIn.parse(couple.startDate)
            if (date != null) {
                coupleStartDateMs = date.time
                val diffMs = System.currentTimeMillis() - date.time
                daysTogether = max(1L, TimeUnit.MILLISECONDS.toDays(diffMs))
                val dateFmt = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                anniversaryDateStr = "Together since " + dateFmt.format(date)
            } else {
                daysTogether = 1L
                anniversaryDateStr = "Our Bloom Love Story"
            }
        } catch (e: Exception) {
            val rawLong = couple.startDate.toLongOrNull()
            if (rawLong != null && rawLong > 0) {
                coupleStartDateMs = rawLong
                val diffMs = System.currentTimeMillis() - rawLong
                daysTogether = max(1L, TimeUnit.MILLISECONDS.toDays(diffMs))
                val dateFmt = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                anniversaryDateStr = "Together since " + dateFmt.format(Date(rawLong))
            } else {
                daysTogether = 1L
                anniversaryDateStr = "Our Bloom Love Story"
            }
        }
    }

    private fun startListeningToWallpaperConfig() {
        wallpaperListener?.remove()
        wallpaperListener = repository.observeCoupleWallpaper(coupleId) { config ->
            if (!isAdded) return@observeCoupleWallpaper

            currentConfig = config
            switchDaysCounter.isChecked = config.showDaysCounter
            switchCoupleNames.isChecked = config.showCoupleNames
            sliderDimLevel.value = config.dimLevel.coerceIn(0.10f, 0.75f)

            if (config.updatedByName.isNotBlank() && config.updatedByUid != currentUserId) {
                tvWallpaperStatusBadge.text = "Updated by ${config.updatedByName} 💕"
            } else {
                tvWallpaperStatusBadge.text = "Synced for Both 💕"
            }

            if (config.imageUrl.isNotBlank() && selectedLocalUri == null) {
                loadBitmapFromUrl(config.imageUrl)
            } else {
                renderWallpaperPreview()
            }
        }
    }

    private fun loadBitmapFromUri(uri: Uri) {
        pbWallpaperLoading.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val bmp = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                withContext(Dispatchers.Main) {
                    currentSourceBitmap = bmp
                    currentConfig = currentConfig.copy(presetTheme = "CUSTOM")
                    pbWallpaperLoading.visibility = View.GONE
                    renderWallpaperPreview()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pbWallpaperLoading.visibility = View.GONE
                    Toast.makeText(requireContext(), "Could not load image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadBitmapFromUrl(url: String) {
        pbWallpaperLoading.visibility = View.VISIBLE
        Glide.with(this)
            .asBitmap()
            .load(url)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    currentSourceBitmap = resource
                    pbWallpaperLoading.visibility = View.GONE
                    renderWallpaperPreview()
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    pbWallpaperLoading.visibility = View.GONE
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    pbWallpaperLoading.visibility = View.GONE
                    renderWallpaperPreview()
                }
            })
    }

    private fun renderWallpaperPreview() {
        val displayMetrics = resources.displayMetrics
        val screenW = displayMetrics.widthPixels
        val screenH = displayMetrics.heightPixels

        lifecycleScope.launch(Dispatchers.Default) {
            val wallpaper = WallpaperGenerator.generateWallpaperBitmap(
                sourceBitmap = currentSourceBitmap,
                config = currentConfig,
                daysTogether = daysTogether,
                coupleNames = coupleNamesDisplay,
                anniversaryDateStr = anniversaryDateStr,
                targetWidth = screenW,
                targetHeight = screenH
            )
            lastGeneratedWallpaper = wallpaper

            withContext(Dispatchers.Main) {
                if (isAdded) {
                    ivWallpaperPreview.setImageBitmap(wallpaper)
                }
            }
        }
    }

    private fun applyWallpaperToDevice(target: WallpaperTarget) {
        val wallpaper = lastGeneratedWallpaper
        if (wallpaper == null) {
            Toast.makeText(requireContext(), "Please wait for wallpaper to generate...", Toast.LENGTH_SHORT).show()
            return
        }

        pbWallpaperLoading.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val success = WallpaperHelper.applyWallpaper(requireContext(), wallpaper, target)
            withContext(Dispatchers.Main) {
                pbWallpaperLoading.visibility = View.GONE
                if (success) {
                    val targetName = when (target) {
                        WallpaperTarget.LOCK_SCREEN -> "Lock Screen 🔒"
                        WallpaperTarget.HOME_SCREEN -> "Home Screen 📱"
                        WallpaperTarget.BOTH -> "Lock & Home Screen ✨"
                    }
                    Toast.makeText(requireContext(), "Wallpaper applied to $targetName! 🌸", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to apply wallpaper. Check permissions.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveWallpaperToGallery() {
        val wallpaper = lastGeneratedWallpaper ?: return
        pbWallpaperLoading.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val uri = WallpaperHelper.saveToGallery(requireContext(), wallpaper)
            withContext(Dispatchers.Main) {
                pbWallpaperLoading.visibility = View.GONE
                if (uri != null) {
                    Toast.makeText(requireContext(), "Saved to Photos / Gallery! 💾", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Failed saving image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun syncWallpaperWithPartner() {
        if (coupleId.isBlank()) {
            Toast.makeText(requireContext(), "Pair with your partner first! 🌸", Toast.LENGTH_SHORT).show()
            return
        }

        pbWallpaperLoading.visibility = View.VISIBLE
        Toast.makeText(requireContext(), "Syncing couple wallpaper with partner... 💕", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            var uploadedUrl = currentConfig.imageUrl

            // If user selected a local photo, upload it first
            val localUri = selectedLocalUri
            if (localUri != null) {
                val url = repository.uploadImage(requireContext(), localUri)
                if (!url.isNullOrBlank()) {
                    uploadedUrl = url
                }
            }

            val updatedConfig = currentConfig.copy(
                imageUrl = uploadedUrl,
                updatedByUid = currentUserId,
                updatedByName = currentUserName,
                updatedAt = System.currentTimeMillis()
            )

            val success = repository.updateCoupleWallpaper(coupleId, updatedConfig)
            pbWallpaperLoading.visibility = View.GONE

            if (success) {
                selectedLocalUri = null
                currentConfig = updatedConfig
                Toast.makeText(requireContext(), "Wallpaper synchronized with $partnerName! 💕", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), "Failed to sync wallpaper. Check internet connection.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showThemePresetsDialog() {
        val presets = arrayOf(
            "Rose Sunset 🌅",
            "Starry Love 🌌",
            "Cherry Blossom 🌸",
            "Golden Hour 🌇"
        )
        val presetKeys = arrayOf(
            "ROSE_SUNSET",
            "STARRY_LOVE",
            "CHERRY_BLOSSOM",
            "GOLDEN_HOUR"
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Choose Color Theme 🎨")
            .setItems(presets) { _, which ->
                currentSourceBitmap = null
                selectedLocalUri = null
                currentConfig = currentConfig.copy(presetTheme = presetKeys[which])
                renderWallpaperPreview()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        wallpaperListener?.remove()
        wallpaperListener = null
    }
}
