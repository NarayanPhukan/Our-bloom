package com.ourbloom.app.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
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
import com.ourbloom.app.fcm.DirectFcmSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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
    private val currentSlotBitmaps = mutableListOf<Bitmap>()

    // Views
    private lateinit var ivWallpaperPreview: ImageView
    private lateinit var tvWallpaperStatusBadge: TextView
    private lateinit var tvPreviewDate: TextView
    private lateinit var pbWallpaperLoading: ProgressBar
    private lateinit var switchDaysCounter: MaterialSwitch
    private lateinit var switchCoupleNames: MaterialSwitch
    private lateinit var switchAutoApply: MaterialSwitch
    private lateinit var sliderDimLevel: Slider
    private lateinit var llCollageControls: LinearLayout

    private val pickPhotoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedLocalUri = uri
            currentConfig = currentConfig.copy(templateId = "CLASSIC")
            llCollageControls.visibility = View.GONE
            loadBitmapFromUri(uri)
        }
    }

    private val pickMultiplePhotosLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            loadBitmapsFromUris(uris)
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
        switchAutoApply = view.findViewById(R.id.switch_auto_apply)
        sliderDimLevel = view.findViewById(R.id.slider_dim_level)
        llCollageControls = view.findViewById(R.id.ll_collage_controls)

        val wallpaperPrefs = requireContext().getSharedPreferences("ourbloom_wallpaper_prefs", Context.MODE_PRIVATE)
        switchAutoApply.isChecked = wallpaperPrefs.getBoolean("auto_apply_partner_wallpaper", true)
        switchAutoApply.setOnCheckedChangeListener { _, isChecked ->
            wallpaperPrefs.edit().putBoolean("auto_apply_partner_wallpaper", isChecked).apply()
            val msg = if (isChecked) "Auto-apply enabled! Sweetheart's updates will set automatically 💕"
                      else "Auto-apply off. You'll receive alerts to apply manually ✨"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        view.findViewById<ImageButton>(R.id.btn_back_wallpaper).setOnClickListener {
            findNavController().navigateUp()
        }

        // Templates Button
        view.findViewById<MaterialButton>(R.id.btn_choose_template).setOnClickListener {
            showTemplatesDialog()
        }

        // Single Photo Button
        view.findViewById<MaterialButton>(R.id.btn_pick_photo).setOnClickListener {
            pickPhotoLauncher.launch("image/*")
        }

        // Color Themes Button
        view.findViewById<MaterialButton>(R.id.btn_theme_presets).setOnClickListener {
            llCollageControls.visibility = View.GONE
            showThemePresetsDialog()
        }

        // Collage Controls
        view.findViewById<MaterialButton>(R.id.btn_shuffle_memories).setOnClickListener {
            shuffleAndRenderCollage()
        }

        view.findViewById<MaterialButton>(R.id.btn_reload_gallery).setOnClickListener {
            loadPhotosFromMemoriesGallery()
        }

        view.findViewById<MaterialButton>(R.id.btn_pick_device_photos).setOnClickListener {
            pickMultiplePhotosLauncher.launch("image/*")
        }

        switchDaysCounter.setOnCheckedChangeListener { _, isChecked ->
            currentConfig = currentConfig.copy(showDaysCounter = isChecked)
            if (currentConfig.templateId == "HEART_MEMORIES" && currentSlotBitmaps.isNotEmpty()) {
                renderHeartCollage()
            } else {
                renderWallpaperPreview()
            }
        }

        switchCoupleNames.setOnCheckedChangeListener { _, isChecked ->
            currentConfig = currentConfig.copy(showCoupleNames = isChecked)
            if (currentConfig.templateId == "HEART_MEMORIES" && currentSlotBitmaps.isNotEmpty()) {
                renderHeartCollage()
            } else {
                renderWallpaperPreview()
            }
        }

        sliderDimLevel.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                currentConfig = currentConfig.copy(dimLevel = value)
                if (currentConfig.templateId == "HEART_MEMORIES" && currentSlotBitmaps.isNotEmpty()) {
                    renderHeartCollage()
                } else {
                    renderWallpaperPreview()
                }
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
            try {
                val clamped = config.dimLevel.coerceIn(sliderDimLevel.valueFrom, sliderDimLevel.valueTo)
                sliderDimLevel.value = Math.round(clamped * 100f) / 100f
            } catch (e: Exception) {
                Log.e("CoupleWallpaper", "Failed to set sliderDimLevel value: ${config.dimLevel}", e)
            }

            if (config.templateId == "HEART_MEMORIES") {
                llCollageControls.visibility = View.VISIBLE
            } else {
                llCollageControls.visibility = View.GONE
            }

            if (config.updatedByName.isNotBlank() && config.updatedByUid != currentUserId) {
                tvWallpaperStatusBadge.text = "Updated by ${config.updatedByName} 💕"
                val prefs = requireContext().getSharedPreferences("ourbloom_wallpaper_prefs", Context.MODE_PRIVATE)
                val autoApply = prefs.getBoolean("auto_apply_partner_wallpaper", true)
                val lastAppliedAt = prefs.getLong("last_applied_wallpaper_ts", 0L)
                if (autoApply && config.updatedAt > lastAppliedAt) {
                    prefs.edit().putLong("last_applied_wallpaper_ts", config.updatedAt).apply()
                    lifecycleScope.launch(Dispatchers.Default) {
                        val wallpaper = WallpaperGenerator.generateWallpaperBitmap(
                            sourceBitmap = currentSourceBitmap,
                            config = config,
                            daysTogether = daysTogether,
                            coupleNames = coupleNamesDisplay,
                            anniversaryDateStr = anniversaryDateStr,
                            targetWidth = resources.displayMetrics.widthPixels,
                            targetHeight = resources.displayMetrics.heightPixels
                        )
                        lastGeneratedWallpaper = wallpaper
                        val applied = WallpaperHelper.applyWallpaper(requireContext(), wallpaper, WallpaperTarget.BOTH)
                        if (applied) {
                            withContext(Dispatchers.Main) {
                                if (isAdded) {
                                    Toast.makeText(requireContext(), "Matching wallpaper updated by ${config.updatedByName}! 💕", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
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

    private fun showTemplatesDialog() {
        val templates = arrayOf(
            "💖 Heart Memories Collage (First Template)\n     • 18 photo slots with watercolor bows",
            "📷 Classic Single Photo\n     • Full-screen custom photo with overlay",
            "🎨 Romantic Color Themes\n     • Rose Sunset, Starry Love, Cherry Blossom"
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Choose Wallpaper Template 🖼️")
            .setItems(templates) { _, which ->
                when (which) {
                    0 -> {
                        // Heart Memories Collage (First Template)
                        currentConfig = currentConfig.copy(templateId = "HEART_MEMORIES")
                        loadPhotosFromMemoriesGallery()
                    }
                    1 -> {
                        // Classic Single Photo
                        llCollageControls.visibility = View.GONE
                        currentConfig = currentConfig.copy(templateId = "CLASSIC")
                        pickPhotoLauncher.launch("image/*")
                    }
                    2 -> {
                        // Romantic Color Themes
                        llCollageControls.visibility = View.GONE
                        currentConfig = currentConfig.copy(templateId = "CLASSIC")
                        showThemePresetsDialog()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadPhotosFromMemoriesGallery() {
        if (coupleId.isBlank()) {
            Toast.makeText(requireContext(), "Pair with your partner first! 🌸", Toast.LENGTH_SHORT).show()
            return
        }

        pbWallpaperLoading.visibility = View.VISIBLE
        Toast.makeText(requireContext(), "Fetching photos from your Love Gallery... 📸", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val memories = repository.getAllMemories(coupleId)
                val validUrls = memories.mapNotNull { it.imageUrl.takeIf { url -> url.isNotBlank() } }

                if (validUrls.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        pbWallpaperLoading.visibility = View.GONE
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("No Photos in Gallery Yet 🌸")
                            .setMessage("Your couple gallery doesn't have photos yet. Would you like to select photos from your device to prepare your Heart Collage?")
                            .setPositiveButton("Choose Photos 📱") { _, _ ->
                                pickMultiplePhotosLauncher.launch("image/*")
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    return@launch
                }

                val loadedBitmaps = mutableListOf<Bitmap>()
                for (url in validUrls.take(18)) {
                    try {
                        val bmp = Glide.with(requireContext())
                            .asBitmap()
                            .load(url)
                            .submit(1200, 1200)
                            .get(15, TimeUnit.SECONDS)
                        if (bmp != null) loadedBitmaps.add(bmp)
                    } catch (e: Exception) {
                        Log.w("CoupleWallpaper", "Failed loading memory photo: $url", e)
                    }
                }

                withContext(Dispatchers.Main) {
                    pbWallpaperLoading.visibility = View.GONE
                    if (loadedBitmaps.isNotEmpty()) {
                        currentSlotBitmaps.clear()
                        currentSlotBitmaps.addAll(loadedBitmaps)
                        selectedLocalUri = null
                        currentConfig = currentConfig.copy(
                            templateId = "HEART_MEMORIES",
                            presetTheme = "CUSTOM",
                            memoryPhotoUrls = validUrls
                        )
                        renderHeartCollage()
                        Toast.makeText(requireContext(), "Heart Collage created with ${loadedBitmaps.size} gallery photos! 💖", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), "Could not download gallery photos. Please check internet connection.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pbWallpaperLoading.visibility = View.GONE
                    Toast.makeText(requireContext(), "Failed loading gallery: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadBitmapsFromUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        pbWallpaperLoading.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val bitmaps = mutableListOf<Bitmap>()
            for (uri in uris.take(18)) {
                try {
                    val input = requireContext().contentResolver.openInputStream(uri)
                    val bmp = BitmapFactory.decodeStream(input)
                    input?.close()
                    if (bmp != null) bitmaps.add(bmp)
                } catch (e: Exception) {
                    Log.w("CoupleWallpaper", "Error loading uri: $uri", e)
                }
            }
            withContext(Dispatchers.Main) {
                pbWallpaperLoading.visibility = View.GONE
                if (bitmaps.isNotEmpty()) {
                    currentSlotBitmaps.clear()
                    currentSlotBitmaps.addAll(bitmaps)
                    selectedLocalUri = null
                    currentConfig = currentConfig.copy(templateId = "HEART_MEMORIES", presetTheme = "CUSTOM")
                    renderHeartCollage()
                    Toast.makeText(requireContext(), "Heart collage updated with ${bitmaps.size} photos! 💖", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shuffleAndRenderCollage() {
        if (currentSlotBitmaps.isEmpty()) {
            loadPhotosFromMemoriesGallery()
            return
        }
        currentSlotBitmaps.shuffle()
        renderHeartCollage()
        Toast.makeText(requireContext(), "Collage photos shuffled! 🔀", Toast.LENGTH_SHORT).show()
    }

    private fun renderHeartCollage() {
        val displayMetrics = resources.displayMetrics
        val screenW = if (displayMetrics.widthPixels > 0) displayMetrics.widthPixels else 1080
        val screenH = if (displayMetrics.heightPixels > 0) displayMetrics.heightPixels else 2400

        pbWallpaperLoading.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.Default) {
            val collage = WallpaperGenerator.generateHeartCollageBitmap(
                context = requireContext().applicationContext,
                photos = currentSlotBitmaps,
                config = currentConfig,
                daysTogether = daysTogether,
                coupleNames = coupleNamesDisplay,
                anniversaryDateStr = anniversaryDateStr,
                targetWidth = screenW,
                targetHeight = screenH
            )
            currentSourceBitmap = collage
            lastGeneratedWallpaper = collage

            withContext(Dispatchers.Main) {
                pbWallpaperLoading.visibility = View.GONE
                if (isAdded) {
                    ivWallpaperPreview.setImageBitmap(collage)
                    llCollageControls.visibility = View.VISIBLE
                }
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
                    currentConfig = currentConfig.copy(presetTheme = "CUSTOM", templateId = "CLASSIC")
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

            // 1. If user selected a local photo, upload it
            val localUri = selectedLocalUri
            if (localUri != null) {
                val url = repository.uploadImage(requireContext(), localUri, coupleId)
                if (!url.isNullOrBlank()) {
                    uploadedUrl = url
                }
            } else if (currentConfig.templateId == "HEART_MEMORIES" && lastGeneratedWallpaper != null) {
                // 2. If Heart Collage template is active, upload the generated custom collage composite
                try {
                    val stream = ByteArrayOutputStream()
                    lastGeneratedWallpaper!!.compress(Bitmap.CompressFormat.JPEG, 98, stream)
                    val bytes = stream.toByteArray()
                    val url = repository.uploadImageBytes(bytes, "wallpaper_heart_collage_${coupleId}_${System.currentTimeMillis()}.jpg", coupleId)
                    if (!url.isNullOrBlank()) {
                        uploadedUrl = url
                    }
                } catch (e: Exception) {
                    Log.e("CoupleWallpaper", "Error uploading heart collage composite: ${e.message}", e)
                }
            }

            val updatedConfig = currentConfig.copy(
                imageUrl = uploadedUrl,
                templateId = currentConfig.templateId,
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

                // Dispatch instant high-priority FCM push notification to partner
                val currentCoupleId = coupleId
                val currentUid = currentUserId
                val myName = currentUserName.ifBlank { "Your sweetheart" }

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val partnerFcmToken = repository.getPartnerFcmToken(currentCoupleId, currentUid) ?: ""
                        val notifTitle = if (updatedConfig.templateId == "HEART_MEMORIES") {
                            "$myName sent a Heart Memories Wallpaper! 💖"
                        } else {
                            "$myName updated Couple Wallpaper! 💕"
                        }
                        val notifBody = if (updatedConfig.templateId == "HEART_MEMORIES") {
                            "18 gallery memories framed with love! Tap to preview & apply ✨"
                        } else {
                            "Tap to view or sync your matching couple wallpaper ✨"
                        }

                        val pushData = mapOf(
                            "type" to "couple_wallpaper",
                            "action" to "open_couple_wallpaper",
                            "coupleId" to currentCoupleId,
                            "senderId" to currentUid,
                            "senderName" to myName,
                            "title" to notifTitle,
                            "body" to notifBody,
                            "imageUrl" to (updatedConfig.imageUrl ?: ""),
                            "templateId" to updatedConfig.templateId,
                            "presetTheme" to updatedConfig.presetTheme,
                            "dimLevel" to updatedConfig.dimLevel.toString(),
                            "showDaysCounter" to updatedConfig.showDaysCounter.toString(),
                            "showCoupleNames" to updatedConfig.showCoupleNames.toString(),
                            "showMilestone" to updatedConfig.showMilestone.toString(),
                            "badgePosition" to updatedConfig.badgePosition,
                            "quoteText" to updatedConfig.quoteText,
                            "daysTogether" to daysTogether.toString(),
                            "coupleNames" to coupleNamesDisplay,
                            "anniversaryDateStr" to anniversaryDateStr
                        )
                        DirectFcmSender.sendPush(
                            context = requireContext().applicationContext,
                            token = partnerFcmToken,
                            title = notifTitle,
                            body = notifBody,
                            data = pushData
                        )
                    } catch (e: Exception) {
                        Log.e("CoupleWallpaper", "Failed sending wallpaper push to partner", e)
                    }
                }
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
                currentConfig = currentConfig.copy(presetTheme = presetKeys[which], templateId = "CLASSIC")
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
