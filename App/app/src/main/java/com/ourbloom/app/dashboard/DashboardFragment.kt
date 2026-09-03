package com.ourbloom.app.dashboard

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ourbloom.app.R
import com.ourbloom.app.widget.LoveTimerWidgetProvider

class DashboardFragment : Fragment() {

    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var galleryAdapter: GalleryAdapter
    private var heartbeatListener: ListenerRegistration? = null
    private var cooldownTimer: CountDownTimer? = null
    private val sessionStartTime = System.currentTimeMillis()
    private var profileBottomSheetDialog: BottomSheetDialog? = null

    private val pickProfileImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            uploadProfilePicture(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_dashboard, container, false)
        
        val tvHeaderTitle = view.findViewById<TextView>(R.id.tv_header_title)
        val tvFirstMilestoneTitle = view.findViewById<TextView>(R.id.tv_first_milestone_title)
        val tvFirstMilestoneDesc = view.findViewById<TextView>(R.id.tv_first_milestone_desc)
        
        val tvTimerDays = view.findViewById<TextView>(R.id.tv_timer_days)
        val tvTimerHours = view.findViewById<TextView>(R.id.tv_timer_hours)
        val tvTimerMins = view.findViewById<TextView>(R.id.tv_timer_mins)
        val tvTimerSecs = view.findViewById<TextView>(R.id.tv_timer_secs)
        val tvTimerSince = view.findViewById<TextView>(R.id.tv_timer_since)

        val tvDaysTogether = view.findViewById<TextView>(R.id.tv_days_together)
        val tvHoursTogether = view.findViewById<TextView>(R.id.tv_hours_together)
        val tvDaysAsNames = view.findViewById<TextView>(R.id.tv_days_as_names)

        val tvDailyNoteText = view.findViewById<TextView>(R.id.tv_daily_note_text)
        val tvDailyNoteAuthor = view.findViewById<TextView>(R.id.tv_daily_note_author)

        // Setup gallery RecyclerView
        galleryAdapter = GalleryAdapter { memory ->
            showLightbox(memory)
        }
        val rvGallery = view.findViewById<RecyclerView>(R.id.rv_memories_gallery)
        rvGallery.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvGallery.adapter = galleryAdapter

        // Set up default header while loading
        tvHeaderTitle.text = "Happy Months,\nmy beautiful Partner."

        // Enable nickname settings
        val showDialog = { showNicknameDialog() }
        tvHeaderTitle.setOnClickListener { showDialog() }
        tvDaysAsNames.setOnClickListener { showDialog() }

        // Home screen widget 1-tap add button
        val btnAddWidget = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_add_home_widget)
        btnAddWidget?.setOnClickListener {
            val context = requireContext()
            val pinned = LoveTimerWidgetProvider.requestPinWidget(context)
            if (!pinned) {
                android.app.AlertDialog.Builder(context)
                    .setTitle("Add Home Screen Widget")
                    .setMessage("Long-press anywhere on your phone's home screen wallpaper, tap 'Widgets', choose 'OurBloom', and drag the Love Counter to your home screen! ❤️")
                    .setPositiveButton("Got it!", null)
                    .show()
            } else {
                Toast.makeText(context, "Adding widget to home screen...", Toast.LENGTH_SHORT).show()
            }
        }

        // Real-time Heartbeat button
        val btnSendHeartbeat = view.findViewById<MaterialButton>(R.id.btn_send_heartbeat)
        val ivHeartIcon = view.findViewById<ImageView>(R.id.iv_heartbeat_icon)
        btnSendHeartbeat?.setOnClickListener {
            triggerHeartbeatHaptic()
            ivHeartIcon?.animate()?.scaleX(1.4f)?.scaleY(1.4f)?.setDuration(120)?.withEndAction {
                ivHeartIcon.animate().scaleX(1.0f).scaleY(1.0f).setDuration(180).start()
            }?.start()

            btnSendHeartbeat.isEnabled = false

            viewModel.sendHeartbeat { success ->
                if (success) {
                    val partnerNick = viewModel.currentUser.value?.nicknameForPartner?.takeIf { it.isNotBlank() }
                        ?: viewModel.partnerUser.value?.name
                        ?: "your partner"
                    Toast.makeText(context, "Heartbeat sent to $partnerNick! ❤️", Toast.LENGTH_SHORT).show()
                }
            }

            cooldownTimer?.cancel()
            cooldownTimer = object : CountDownTimer(15000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    btnSendHeartbeat.text = "Sent! (${millisUntilFinished / 1000}s)"
                }
                override fun onFinish() {
                    btnSendHeartbeat.isEnabled = true
                    btnSendHeartbeat.text = "Send Heartbeat 💓"
                }
            }.start()
        }

        // Observers
        viewModel.memories.observe(viewLifecycleOwner) { memoryList ->
            galleryAdapter.submitList(memoryList)
        }
        viewModel.couple.observe(viewLifecycleOwner) { couple ->
            if (couple != null) {
                setupHeartbeatListener(couple.id)
                // Format since date
                try {
                    val formatIn = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    val formatOut = java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.getDefault())
                    val date = formatIn.parse(couple.startDate)
                    tvTimerSince.text = "Since ${date?.let { formatOut.format(it) }}"
                } catch (e: Exception) {
                    tvTimerSince.text = "Since ${couple.startDate}"
                }
                updateDynamicText()
            }
        }
        
        viewModel.currentUser.observe(viewLifecycleOwner) { updateDynamicText() }
        viewModel.partnerUser.observe(viewLifecycleOwner) { updateDynamicText() }

        viewModel.timeElapsed.observe(viewLifecycleOwner) { time ->
            if (time != null) {
                // Ticking clock
                tvTimerDays.text = time.days.toString()
                tvTimerHours.text = String.format("%02d", time.hours)
                tvTimerMins.text = String.format("%02d", time.minutes)
                tvTimerSecs.text = String.format("%02d", time.seconds)

                // Metric cards
                tvDaysTogether.text = time.days.toString()
                tvHoursTogether.text = "${time.totalHours}+"
            }
        }

        val ivFirstMilestone = view.findViewById<ImageView>(R.id.iv_first_milestone)
        
        // Observe first milestone for title and description, and fallback image
        viewModel.firstMilestone.observe(viewLifecycleOwner) { milestone ->
            if (milestone != null) {
                tvFirstMilestoneTitle.text = milestone.title
                tvFirstMilestoneDesc.text = milestone.body
                
                // Set fallback image if couple.heroImageUrl is empty
                val couple = viewModel.couple.value
                if (couple == null || couple.heroImageUrl.isEmpty()) {
                    if (milestone.imageUrl.isNotEmpty()) {
                        com.bumptech.glide.Glide.with(this)
                            .load(milestone.imageUrl)
                            .centerCrop()
                            .into(ivFirstMilestone)
                    }
                }
            } else {
                tvFirstMilestoneTitle.text = "Where it all started"
                tvFirstMilestoneDesc.text = "The first time our eyes met..."
            }
        }
        
        // Observe couple for heroImageUrl
        viewModel.couple.observe(viewLifecycleOwner) { couple ->
            if (couple != null && couple.heroImageUrl.isNotEmpty()) {
                com.bumptech.glide.Glide.with(this)
                    .load(couple.heroImageUrl)
                    .centerCrop()
                    .into(ivFirstMilestone)
            } else {
                // If hero image is cleared, fallback to milestone image
                val milestone = viewModel.firstMilestone.value
                if (milestone != null && milestone.imageUrl.isNotEmpty()) {
                    com.bumptech.glide.Glide.with(this)
                        .load(milestone.imageUrl)
                        .centerCrop()
                        .into(ivFirstMilestone)
                }
            }
        }

        viewModel.dailyLoveNote.observe(viewLifecycleOwner) { note ->
            if (note != null) {
                tvDailyNoteText.text = "\"${note.content}\""
                tvDailyNoteAuthor.text = "— From ${note.author}"
            } else {
                tvDailyNoteText.text = "No daily love note today."
                tvDailyNoteAuthor.text = ""
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMsg ->
            if (errorMsg != null) {
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show()
            }
        }

        // Update profile avatar and nickname
        val btnProfileAvatar = view.findViewById<FrameLayout>(R.id.btn_profile_avatar)
        btnProfileAvatar?.setOnClickListener {
            showProfileBottomSheet()
        }

        viewModel.currentUser.observe(viewLifecycleOwner) { user ->
            val tvSpecialNoteAuthor = view.findViewById<TextView>(R.id.tv_special_note_author)
            val nickname = user?.nicknameForPartner?.takeIf { it.isNotBlank() } ?: "Your Love"
            tvSpecialNoteAuthor?.text = "— Forever Yours, $nickname"

            val ivDashboardAvatar = view.findViewById<ImageView>(R.id.iv_dashboard_avatar)
            if (ivDashboardAvatar != null) {
                if (!user?.avatarUrl.isNullOrBlank()) {
                    Glide.with(this)
                        .load(user.avatarUrl)
                        .circleCrop()
                        .placeholder(R.drawable.ic_person_rounded)
                        .into(ivDashboardAvatar)
                    ivDashboardAvatar.imageTintList = null
                } else {
                    ivDashboardAvatar.setImageResource(R.drawable.ic_person_rounded)
                }
            }
        }

        // Anthem FAB
        val fabAnthem = view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_anthem)
        fabAnthem.setOnClickListener {
            showAnthemDialog()
        }

        // Fetch data
        viewModel.loadDashboardData()

        return view
    }

    private fun showAnthemDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_anthem, null)
        val dialog = android.app.AlertDialog.Builder(requireContext(), android.R.style.Theme_Material_Light_Dialog_Alert)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnClose = dialogView.findViewById<android.widget.ImageButton>(R.id.btn_close_anthem)
        val etSpotifyId = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_spotify_id)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_save_anthem)
        val btnPlay = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_play_spotify)

        // Pre-fill current Spotify ID
        val currentTrackId = viewModel.couple.value?.spotifyTrackId ?: "4O2N861eOnF9q8EtpH8IJu"
        etSpotifyId.setText(currentTrackId)

        btnClose.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val trackId = etSpotifyId.text.toString().trim()
            if (trackId.isNotEmpty()) {
                viewModel.updateSpotifyTrackId(trackId) { success ->
                    if (success) {
                        Toast.makeText(requireContext(), "Anthem updated!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    } else {
                        Toast.makeText(requireContext(), "Failed to update Anthem", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnPlay.setOnClickListener {
            val trackId = etSpotifyId.text.toString().trim()
            if (trackId.isNotEmpty()) {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                // Extract ID if a full link was pasted
                val finalId = if (trackId.contains("spotify.com/track/")) {
                    trackId.substringAfter("track/").substringBefore("?")
                } else {
                    trackId
                }
                intent.data = android.net.Uri.parse("spotify:track:$finalId")
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to browser
                    intent.data = android.net.Uri.parse("https://open.spotify.com/track/$finalId")
                    startActivity(intent)
                }
            }
        }

        dialog.show()
    }

    private fun updateDynamicText() {
        val couple = viewModel.couple.value ?: return
        val currentUser = viewModel.currentUser.value
        val partnerUser = viewModel.partnerUser.value

        val months = viewModel.getMonthsTogether()
        
        var nickname = "Kuchupuchu"
        val partnerNick = partnerUser?.nicknameForPartner
        val myName = currentUser?.name
        
        if (!partnerNick.isNullOrBlank()) {
            nickname = partnerNick
        } else if (!myName.isNullOrBlank()) {
            nickname = myName
        }
            
        val tvHeaderTitle = view?.findViewById<TextView>(R.id.tv_header_title)
        tvHeaderTitle?.text = "Happy $months Months,\nmy beautiful $nickname."
        
        val tvDaysAsNames = view?.findViewById<TextView>(R.id.tv_days_as_names)
        
        val partnerNicknameForMe = partnerUser?.nicknameForPartner?.takeIf { it.isNotBlank() }
        val myNicknameForPartner = currentUser?.nicknameForPartner?.takeIf { it.isNotBlank() } ?: "Partner"
        val leftName = partnerNicknameForMe ?: (myName?.takeIf { it.isNotBlank() } ?: "You")
        
        tvDaysAsNames?.text = "DAYS AS ${leftName.uppercase()} & ${myNicknameForPartner.uppercase()}"

        val tvHeartbeatSubtitle = view?.findViewById<TextView>(R.id.tv_heartbeat_subtitle)
        tvHeartbeatSubtitle?.text = "Send a live tactile heartbeat pulse to $myNicknameForPartner ❤️"

        // Sync data to home screen widget
        context?.let { ctx ->
            LoveTimerWidgetProvider.saveWidgetData(
                ctx.applicationContext,
                couple.startDate,
                couple.startTime,
                myNicknameForPartner,
                myName,
                partnerUser?.name,
                leftName
            )
        }
    }

    private fun triggerHeartbeatHaptic() {
        try {
            val pattern = longArrayOf(0, 120, 80, 240)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val effect = VibrationEffect.createWaveform(pattern, -1)
                vibratorManager?.defaultVibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, -1)
                }
            }
        } catch (e: Throwable) {
            // Ignore
        }
    }

    private fun setupHeartbeatListener(coupleId: String) {
        heartbeatListener?.remove()
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        heartbeatListener = FirebaseFirestore.getInstance()
            .collection("heartbeats")
            .whereEqualTo("coupleId", coupleId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                for (change in snapshot.documentChanges) {
                    if (change.type == DocumentChange.Type.ADDED) {
                        val doc = change.document
                        val senderId = doc.getString("senderId")
                        val createdAt = doc.getLong("createdAt") ?: 0L
                        if (senderId != myUid && createdAt > sessionStartTime && (System.currentTimeMillis() - createdAt) < 30000) {
                            val senderName = doc.getString("senderName") ?: "Your Love"
                            triggerHeartbeatHaptic()
                            showIncomingHeartbeatDialog(senderName)
                        }
                    }
                }
            }
    }

    private fun showIncomingHeartbeatDialog(senderName: String) {
        if (!isAdded || context == null) return
        try {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("💓 Heartbeat Received")
                .setMessage("$senderName is thinking of you right now! ❤️")
                .setPositiveButton("Send Back 💓") { _, _ ->
                    val btn = view?.findViewById<MaterialButton>(R.id.btn_send_heartbeat)
                    btn?.performClick()
                }
                .setNegativeButton("Close", null)
                .show()
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        heartbeatListener?.remove()
        heartbeatListener = null
        cooldownTimer?.cancel()
        cooldownTimer = null
    }

    private fun showNicknameDialog() {
        val currentNick = viewModel.currentUser.value?.nicknameForPartner ?: ""
        
        val input = android.widget.EditText(requireContext()).apply {
            setText(currentNick)
            hint = "e.g., Kuchupuchu"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setPadding(48, 32, 48, 32)
            setBackgroundResource(android.R.color.transparent)
        }
        
        val container = android.widget.FrameLayout(requireContext())
        container.addView(input)

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Set Nickname")
            .setMessage("What do you call your partner?")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newNick = input.text.toString().trim()
                viewModel.updateNicknameForPartner(newNick)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun uploadProfilePicture(uri: Uri) {
        val progressBar = profileBottomSheetDialog?.findViewById<ProgressBar>(R.id.pb_avatar_upload)
        val btnChange = profileBottomSheetDialog?.findViewById<MaterialButton>(R.id.btn_change_avatar)

        progressBar?.visibility = View.VISIBLE
        btnChange?.isEnabled = false
        Toast.makeText(requireContext(), "Uploading profile picture...", Toast.LENGTH_SHORT).show()

        viewModel.updateAvatar(requireContext(), uri) { success, result ->
            progressBar?.visibility = View.GONE
            btnChange?.isEnabled = true
            if (success && result != null) {
                Toast.makeText(requireContext(), "Profile picture updated! ✨", Toast.LENGTH_SHORT).show()
                val ivSheetAvatar = profileBottomSheetDialog?.findViewById<ImageView>(R.id.iv_sheet_avatar)
                if (ivSheetAvatar != null) {
                    Glide.with(this)
                        .load(result)
                        .circleCrop()
                        .into(ivSheetAvatar)
                    ivSheetAvatar.imageTintList = null
                }
            } else {
                Toast.makeText(requireContext(), result ?: "Failed to upload", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showProfileBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_profile, null)
        dialog.setContentView(sheetView)
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
        }
        profileBottomSheetDialog = dialog

        val user = viewModel.currentUser.value
        val ivSheetAvatar = sheetView.findViewById<ImageView>(R.id.iv_sheet_avatar)
        val tvSheetName = sheetView.findViewById<TextView>(R.id.tv_sheet_user_name)
        val tvSheetEmail = sheetView.findViewById<TextView>(R.id.tv_sheet_user_email)
        val etPartnerNickname = sheetView.findViewById<EditText>(R.id.et_partner_nickname)
        val btnSaveNickname = sheetView.findViewById<MaterialButton>(R.id.btn_save_nickname)
        val btnChangeAvatar = sheetView.findViewById<MaterialButton>(R.id.btn_change_avatar)
        val btnAvatarFrame = sheetView.findViewById<FrameLayout>(R.id.btn_change_avatar_frame)

        tvSheetName.text = user?.name?.takeIf { it.isNotBlank() } ?: "OurBloom Lover"
        tvSheetEmail.text = user?.email?.takeIf { it.isNotBlank() } ?: "Connected with Google"
        etPartnerNickname.setText(user?.nicknameForPartner ?: "")

        if (!user?.avatarUrl.isNullOrBlank()) {
            Glide.with(this)
                .load(user!!.avatarUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_person_rounded)
                .into(ivSheetAvatar)
            ivSheetAvatar.imageTintList = null
        }

        val launchPicker = {
            pickProfileImageLauncher.launch("image/*")
        }
        btnAvatarFrame.setOnClickListener { launchPicker() }
        btnChangeAvatar.setOnClickListener { launchPicker() }

        btnSaveNickname.setOnClickListener {
            val newNick = etPartnerNickname.text.toString().trim()
            if (newNick.isNotEmpty()) {
                viewModel.updateNicknameForPartner(newNick)
                Toast.makeText(requireContext(), "Nickname saved as $newNick! ❤️", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}
