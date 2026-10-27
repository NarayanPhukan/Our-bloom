package com.ourbloom.app.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.firebase.firestore.ListenerRegistration
import com.ourbloom.app.OurBloomApp
import com.ourbloom.app.R
import com.ourbloom.app.data.FirestoreRepository
import com.ourbloom.app.data.models.Couple
import com.ourbloom.app.data.models.CoupleWallpaperConfig
import com.ourbloom.app.data.models.User
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
    private var myDisplayName: String = "You"
    private var partnerName: String = "Sweetheart"
    private var coupleStartDateMs: Long = System.currentTimeMillis()
    private var daysTogether: Long = 1L
    private var anniversaryDateStr: String = ""
    private var coupleNamesDisplay: String = ""

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

    private val safeAppContext: Context
        get() = context?.applicationContext ?: OurBloomApp.instance.applicationContext

    private fun showToastSafely(message: String, duration: Int = Toast.LENGTH_SHORT) {
        val ctx = context ?: activity ?: OurBloomApp.instance
        Toast.makeText(ctx, message, duration).show()
    }

    private fun getScreenDimensions(): Pair<Int, Int> {
        val dm = context?.resources?.displayMetrics ?: OurBloomApp.instance.resources.displayMetrics
        val width = if (dm.widthPixels > 0) dm.widthPixels else 1080
        val height = if (dm.heightPixels > 0) dm.heightPixels else 2400
        return Pair(width, height)
    }

    private val pickPhotoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedLocalUri = uri
            val targetTemplateId = if (currentConfig.templateId == "VINTAGE_SCRAPBOOK") "VINTAGE_SCRAPBOOK" else "CLASSIC"
            currentConfig = currentConfig.copy(templateId = targetTemplateId)
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
            if (!isAdded) return@launch
            if (user != null) {
                currentUserId = user.uid
                currentUserName = user.name
                coupleId = user.coupleId ?: ""

                if (coupleId.isNotBlank()) {
                    val couple = repository.getCouple(coupleId)
                    if (!isAdded) return@launch
                    setupCoupleDetails(couple, user)
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

        val wallpaperPrefs = safeAppContext.getSharedPreferences("ourbloom_wallpaper_prefs", Context.MODE_PRIVATE)
        switchAutoApply.isChecked = wallpaperPrefs.getBoolean("auto_apply_partner_wallpaper", true)
        switchAutoApply.setOnCheckedChangeListener { _, isChecked ->
            wallpaperPrefs.edit().putBoolean("auto_apply_partner_wallpaper", isChecked).apply()
            val msg = if (isChecked) "Auto-apply enabled! Sweetheart's updates will set automatically 💕"
                      else "Auto-apply off. You'll receive alerts to apply manually ✨"
            showToastSafely(msg)
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

    private suspend fun setupCoupleDetails(couple: Couple?, currentUser: User?) {
        if (couple == null) return
        val partnerId = if (couple.user1 == currentUserId) couple.user2 else couple.user1
        val partnerUser = if (partnerId.isNotBlank()) repository.getUser(partnerId) else null
        if (!isAdded) return

        val prefs = try {
            safeAppContext.getSharedPreferences("OurBloomPrefs", Context.MODE_PRIVATE)
        } catch (e: Exception) {
            null
        }

        // Intimate Nicknames resolution:
        // currentUser.nicknameForPartner = what current user calls their partner (e.g. Guxaini)
        // partnerUser.nicknameForPartner = what partner calls the current user (e.g. Kuchupuchu)
        val myNickFromPartner = partnerUser?.nicknameForPartner?.takeIf { it.isNotBlank() }
            ?: prefs?.getString("my_nickname", null)?.takeIf { it.isNotBlank() }
        val partnerNickFromMe = currentUser?.nicknameForPartner?.takeIf { it.isNotBlank() }
            ?: prefs?.getString("partner_nickname", null)?.takeIf { it.isNotBlank() }

        // User 1 & User 2 Nicknames (consistent romantic order for the couple)
        val user1Nick: String
        val user2Nick: String

        if (couple.user1 == currentUserId) {
            user1Nick = myNickFromPartner
                ?: currentUser?.name?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: currentUserName.split(" ").firstOrNull()?.takeIf { it.isNotBlank() }
                ?: "You"
            user2Nick = partnerNickFromMe
                ?: partnerUser?.name?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: "Sweetheart"
            partnerName = user2Nick
            myDisplayName = user1Nick
        } else {
            user1Nick = partnerNickFromMe
                ?: partnerUser?.name?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: "Sweetheart"
            user2Nick = myNickFromPartner
                ?: currentUser?.name?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: currentUserName.split(" ").firstOrNull()?.takeIf { it.isNotBlank() }
                ?: "You"
            partnerName = user1Nick
            myDisplayName = user2Nick
        }

        coupleNamesDisplay = "$user1Nick & $user2Nick 🌸"

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

            val isPartnerUpdate = config.updatedByName.isNotBlank() && config.updatedByUid != currentUserId
            if (isPartnerUpdate) {
                val displayName = partnerName.takeIf { it.isNotBlank() && it != "Sweetheart" }
                    ?: config.updatedByName.takeIf { it.isNotBlank() }
                    ?: "Sweetheart"
                tvWallpaperStatusBadge.text = "Updated by $displayName 💕"
            } else {
                tvWallpaperStatusBadge.text = "Synced for Both 💕"
            }

            if (config.imageUrl.isNotBlank() && selectedLocalUri == null) {
                loadBitmapFromUrl(
                    url = config.imageUrl,
                    isPartnerUpdate = isPartnerUpdate,
                    updatedAt = config.updatedAt,
                    partnerName = config.updatedByName
                )
            } else if (selectedLocalUri == null) {
                renderWallpaperPreview()
            }
        }
    }

    private fun showTemplatesDialog() {
        val ctx = context ?: return
        val bottomSheet = BottomSheetDialog(ctx)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_wallpaper_templates, null)
        bottomSheet.setContentView(sheetView)

        val rvTemplates = sheetView.findViewById<RecyclerView>(R.id.rv_wallpaper_templates)
        val btnClose = sheetView.findViewById<View>(R.id.btn_close_template_sheet)

        btnClose.setOnClickListener {
            bottomSheet.dismiss()
        }

        val currentActiveId = when {
            currentConfig.templateId == "HEART_MEMORIES" -> "HEART_MEMORIES"
            currentConfig.templateId == "VINTAGE_SCRAPBOOK" -> "VINTAGE_SCRAPBOOK"
            currentSourceBitmap != null -> "CLASSIC"
            else -> "THEMES"
        }

        val adapter = WallpaperTemplateAdapter(
            templates = WallpaperTemplateRegistry.TEMPLATES,
            selectedTemplateId = currentActiveId
        ) { template ->
            bottomSheet.dismiss()
            when (template.id) {
                "VINTAGE_SCRAPBOOK" -> {
                    llCollageControls.visibility = View.GONE
                    currentConfig = currentConfig.copy(templateId = "VINTAGE_SCRAPBOOK")
                    if (currentSourceBitmap == null) {
                        pickPhotoLauncher.launch("image/*")
                    } else {
                        renderWallpaperPreview()
                    }
                }
                "HEART_MEMORIES" -> {
                    currentConfig = currentConfig.copy(templateId = "HEART_MEMORIES")
                    loadPhotosFromMemoriesGallery()
                }
                "CLASSIC" -> {
                    llCollageControls.visibility = View.GONE
                    currentConfig = currentConfig.copy(templateId = "CLASSIC")
                    pickPhotoLauncher.launch("image/*")
                }
                "THEMES" -> {
                    llCollageControls.visibility = View.GONE
                    currentConfig = currentConfig.copy(templateId = "CLASSIC")
                    showThemePresetsDialog()
                }
                else -> {
                    showToastSafely("${template.title} selected!")
                }
            }
        }

        rvTemplates.layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
        rvTemplates.adapter = adapter

        bottomSheet.show()
    }

    private fun loadPhotosFromMemoriesGallery() {
        if (coupleId.isBlank()) {
            showToastSafely("Pair with your partner first! 🌸")
            return
        }

        pbWallpaperLoading.visibility = View.VISIBLE
        showToastSafely("Fetching photos from your Love Gallery... 📸")

        val appContext = safeAppContext

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val memories = repository.getAllMemories(coupleId)
                val validUrls = memories.mapNotNull { it.imageUrl.takeIf { url -> url.isNotBlank() } }

                if (validUrls.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        if (isAdded) {
                            pbWallpaperLoading.visibility = View.GONE
                            context?.let { ctx ->
                                MaterialAlertDialogBuilder(ctx)
                                    .setTitle("No Photos in Gallery Yet 🌸")
                                    .setMessage("Your couple gallery doesn't have photos yet. Would you like to select photos from your device to prepare your Heart Collage?")
                                    .setPositiveButton("Choose Photos 📱") { _, _ ->
                                        pickMultiplePhotosLauncher.launch("image/*")
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            }
                        }
                    }
                    return@launch
                }

                val loadedBitmaps = mutableListOf<Bitmap>()
                for (url in validUrls.take(18)) {
                    try {
                        val bmp = Glide.with(appContext)
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
                    if (isAdded) {
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
                            showToastSafely("Heart Collage created with ${loadedBitmaps.size} gallery photos! 💖", Toast.LENGTH_LONG)
                        } else {
                            showToastSafely("Could not download gallery photos. Please check internet connection.")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        pbWallpaperLoading.visibility = View.GONE
                        showToastSafely("Failed loading gallery: ${e.message}")
                    }
                }
            }
        }
    }

    private fun loadBitmapsFromUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        pbWallpaperLoading.visibility = View.VISIBLE
        val appContext = safeAppContext

        lifecycleScope.launch(Dispatchers.IO) {
            val bitmaps = mutableListOf<Bitmap>()
            for (uri in uris.take(18)) {
                try {
                    val input = appContext.contentResolver.openInputStream(uri)
                    val bmp = BitmapFactory.decodeStream(input)
                    input?.close()
                    if (bmp != null) bitmaps.add(bmp)
                } catch (e: Exception) {
                    Log.w("CoupleWallpaper", "Error loading uri: $uri", e)
                }
            }
            withContext(Dispatchers.Main) {
                if (isAdded) {
                    pbWallpaperLoading.visibility = View.GONE
                    if (bitmaps.isNotEmpty()) {
                        currentSlotBitmaps.clear()
                        currentSlotBitmaps.addAll(bitmaps)
                        selectedLocalUri = null
                        currentConfig = currentConfig.copy(templateId = "HEART_MEMORIES", presetTheme = "CUSTOM")
                        renderHeartCollage()
                        showToastSafely("Heart collage updated with ${bitmaps.size} photos! 💖")
                    }
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
        showToastSafely("Collage photos shuffled! 🔀")
    }

    private fun renderHeartCollage() {
        if (!isAdded || context == null) return
        val (screenW, screenH) = getScreenDimensions()
        val appContext = safeAppContext

        pbWallpaperLoading.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.Default) {
            val collage = WallpaperGenerator.generateHeartCollageBitmap(
                context = appContext,
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
                if (isAdded) {
                    pbWallpaperLoading.visibility = View.GONE
                    ivWallpaperPreview.setImageBitmap(collage)
                    llCollageControls.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun loadBitmapFromUri(uri: Uri) {
        pbWallpaperLoading.visibility = View.VISIBLE
        val appContext = safeAppContext

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = appContext.contentResolver.openInputStream(uri)
                val bmp = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        currentSourceBitmap = bmp
                        val targetTemplateId = if (currentConfig.templateId == "VINTAGE_SCRAPBOOK") "VINTAGE_SCRAPBOOK" else "CLASSIC"
                        currentConfig = currentConfig.copy(presetTheme = "CUSTOM", templateId = targetTemplateId)
                        pbWallpaperLoading.visibility = View.GONE
                        renderWallpaperPreview()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        pbWallpaperLoading.visibility = View.GONE
                        showToastSafely("Could not load image")
                    }
                }
            }
        }
    }

    private fun loadBitmapFromUrl(
        url: String,
        isPartnerUpdate: Boolean = false,
        updatedAt: Long = 0L,
        partnerName: String = ""
    ) {
        val appContext = safeAppContext
        pbWallpaperLoading.visibility = View.VISIBLE

        Glide.with(appContext)
            .asBitmap()
            .load(url)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    currentSourceBitmap = resource
                    lastGeneratedWallpaper = resource

                    if (isAdded) {
                        pbWallpaperLoading.visibility = View.GONE
                        ivWallpaperPreview.setImageBitmap(resource)
                    }

                    if (isPartnerUpdate) {
                        val prefs = appContext.getSharedPreferences("ourbloom_wallpaper_prefs", Context.MODE_PRIVATE)
                        val autoApply = prefs.getBoolean("auto_apply_partner_wallpaper", true)
                        val lastAppliedAt = prefs.getLong("last_applied_wallpaper_ts", 0L)
                        if (autoApply && updatedAt > lastAppliedAt) {
                            prefs.edit().putLong("last_applied_wallpaper_ts", updatedAt).apply()
                            lifecycleScope.launch(Dispatchers.IO) {
                                val applied = WallpaperHelper.applyWallpaper(appContext, resource, WallpaperTarget.BOTH)
                                if (applied) {
                                    withContext(Dispatchers.Main) {
                                        if (isAdded) {
                                            val name = partnerName.ifBlank { "Sweetheart" }
                                            showToastSafely("Matching wallpaper updated by $name applied! 💕")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    if (isAdded) {
                        pbWallpaperLoading.visibility = View.GONE
                    }
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    if (isAdded) {
                        pbWallpaperLoading.visibility = View.GONE
                        renderWallpaperPreview()
                    }
                }
            })
    }

    private fun renderWallpaperPreview() {
        if (!isAdded || context == null) return
        val (screenW, screenH) = getScreenDimensions()
        val appContext = safeAppContext

        lifecycleScope.launch(Dispatchers.Default) {
            val wallpaper = WallpaperGenerator.generateWallpaperBitmap(
                context = appContext,
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
            showToastSafely("Please wait for wallpaper to generate...")
            return
        }

        val appContext = safeAppContext
        pbWallpaperLoading.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val success = WallpaperHelper.applyWallpaper(appContext, wallpaper, target)
            withContext(Dispatchers.Main) {
                if (isAdded) {
                    pbWallpaperLoading.visibility = View.GONE
                    if (success) {
                        val targetName = when (target) {
                            WallpaperTarget.LOCK_SCREEN -> "Lock Screen 🔒"
                            WallpaperTarget.HOME_SCREEN -> "Home Screen 📱"
                            WallpaperTarget.BOTH -> "Lock & Home Screen ✨"
                        }
                        showToastSafely("Wallpaper applied to $targetName! 🌸", Toast.LENGTH_LONG)
                    } else {
                        showToastSafely("Failed to apply wallpaper. Check permissions.")
                    }
                }
            }
        }
    }

    private fun saveWallpaperToGallery() {
        val wallpaper = lastGeneratedWallpaper ?: return
        val appContext = safeAppContext
        pbWallpaperLoading.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val uri = WallpaperHelper.saveToGallery(appContext, wallpaper)
            withContext(Dispatchers.Main) {
                if (isAdded) {
                    pbWallpaperLoading.visibility = View.GONE
                    if (uri != null) {
                        showToastSafely("Saved to Photos / Gallery! 💾")
                    } else {
                        showToastSafely("Failed saving image")
                    }
                }
            }
        }
    }

    private fun syncWallpaperWithPartner() {
        if (coupleId.isBlank()) {
            showToastSafely("Pair with your partner first! 🌸")
            return
        }

        val appContext = safeAppContext
        pbWallpaperLoading.visibility = View.VISIBLE
        showToastSafely("Syncing couple wallpaper with partner... 💕")

        lifecycleScope.launch {
            var uploadedUrl = currentConfig.imageUrl

            // Always upload the exact rendered wallpaper so partner gets the 100% identical wallpaper!
            val wallpaperToUpload = lastGeneratedWallpaper
            if (wallpaperToUpload != null && !wallpaperToUpload.isRecycled) {
                try {
                    val stream = ByteArrayOutputStream()
                    wallpaperToUpload.compress(Bitmap.CompressFormat.JPEG, 98, stream)
                    val bytes = stream.toByteArray()
                    val filename = "wallpaper_${coupleId}_${System.currentTimeMillis()}.jpg"
                    val url = repository.uploadImageBytes(bytes, filename, coupleId)
                    if (!url.isNullOrBlank()) {
                        uploadedUrl = url
                    }
                } catch (e: Exception) {
                    Log.e("CoupleWallpaper", "Error uploading wallpaper composite: ${e.message}", e)
                }
            }

            val myName = myDisplayName.takeIf { it.isNotBlank() && it != "You" }
                ?: currentUserName.ifBlank { "Your sweetheart" }

            val updatedConfig = currentConfig.copy(
                imageUrl = uploadedUrl,
                templateId = currentConfig.templateId,
                updatedByUid = currentUserId,
                updatedByName = myName,
                updatedAt = System.currentTimeMillis()
            )

            val success = repository.updateCoupleWallpaper(coupleId, updatedConfig)

            if (!isAdded) return@launch
            pbWallpaperLoading.visibility = View.GONE

            if (success) {
                selectedLocalUri = null
                currentConfig = updatedConfig

                // Automatically apply locally too so both sweethearts match instantly
                if (wallpaperToUpload != null && !wallpaperToUpload.isRecycled) {
                    WallpaperHelper.applyWallpaper(appContext, wallpaperToUpload, WallpaperTarget.BOTH)
                }
                showToastSafely("Wallpaper synchronized with $partnerName! 💕", Toast.LENGTH_LONG)

                // Dispatch instant high-priority FCM push notification to partner
                val currentCoupleId = coupleId
                val currentUid = currentUserId

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val partnerFcmToken = repository.getPartnerFcmToken(currentCoupleId, currentUid) ?: ""
                        val notifTitle = when (updatedConfig.templateId) {
                            "HEART_MEMORIES" -> "$myName sent a Heart Memories Wallpaper! 💖"
                            "VINTAGE_SCRAPBOOK" -> "$myName sent a Vintage Scrapbook Wallpaper! 💋"
                            else -> "$myName updated Couple Wallpaper! 💕"
                        }
                        val notifBody = when (updatedConfig.templateId) {
                            "HEART_MEMORIES" -> "18 gallery memories framed with love! Tap to preview & apply ✨"
                            "VINTAGE_SCRAPBOOK" -> "Framed with kiss prints & thumbprint heart! Tap to preview & apply ✨"
                            else -> "Tap to view or sync your matching couple wallpaper ✨"
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
                            "updatedAt" to updatedConfig.updatedAt.toString(),
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
                            context = appContext,
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
                showToastSafely("Failed to sync wallpaper. Check internet connection.")
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
            "STARRY_NIGHT",
            "CHERRY_BLOSSOM",
            "GOLDEN_HOUR"
        )

        val ctx = context ?: return
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Select Romantic Color Theme ✨")
            .setItems(presets) { _, which ->
                selectedLocalUri = null
                currentSourceBitmap = null
                currentConfig = currentConfig.copy(
                    presetTheme = presetKeys[which],
                    templateId = "CLASSIC"
                )
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
