package com.ourbloom.app.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ourbloom.app.R
import com.ourbloom.app.widget.LoveTimerWidgetProvider

class DashboardFragment : Fragment() {

    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var galleryAdapter: GalleryAdapter

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

        // Observers
        viewModel.memories.observe(viewLifecycleOwner) { memoryList ->
            galleryAdapter.submitList(memoryList)
        }
        viewModel.couple.observe(viewLifecycleOwner) { couple ->
            if (couple != null) {
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

        // Update the special note author with the user's nickname
        viewModel.currentUser.observe(viewLifecycleOwner) { user ->
            val tvSpecialNoteAuthor = view.findViewById<TextView>(R.id.tv_special_note_author)
            val nickname = user?.nicknameForPartner?.takeIf { it.isNotBlank() } ?: "Your Love"
            tvSpecialNoteAuthor?.text = "— Forever Yours, $nickname"
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
}
