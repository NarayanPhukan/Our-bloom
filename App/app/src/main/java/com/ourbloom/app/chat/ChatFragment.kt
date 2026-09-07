package com.ourbloom.app.chat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.ByteArrayOutputStream
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.ourbloom.app.R
import com.ourbloom.app.MainActivity
import com.ourbloom.app.data.FirestoreRepository
import com.ourbloom.app.data.models.Couple
import com.ourbloom.app.data.models.User
import com.ourbloom.app.dashboard.showChatImageLightbox
import com.ourbloom.app.dashboard.showChatAlbumLightbox
import com.ourbloom.app.util.ErrorReporter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.content.res.ColorStateList
import androidx.core.widget.ImageViewCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import com.ourbloom.app.fcm.MyFirebaseMessagingService
import com.ourbloom.app.chat.stickers.StickerPickerBottomSheet
import com.ourbloom.app.chat.stickers.StickerManager
import java.util.UUID

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import java.io.File
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.activity.OnBackPressedCallback
import androidx.recyclerview.widget.ItemTouchHelper
import com.ourbloom.app.data.models.ChatMessage

class ChatFragment : Fragment() {

    private lateinit var repository: FirestoreRepository
    private lateinit var driveHelper: GoogleDriveBackupHelper
    private lateinit var chatAdapter: ChatAdapter

    private lateinit var rvMessages: RecyclerView
    private lateinit var etInput: EditText
    private lateinit var btnSend: FloatingActionButton
    private lateinit var btnAttach: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var tvPartnerName: TextView
    private lateinit var tvChatStatus: TextView
    private lateinit var ivPartnerAvatar: ImageView
    private lateinit var layoutEmpty: View

    // Contextual Action Bar views
    private lateinit var layoutActionBar: View
    private lateinit var chatHeader: View
    private lateinit var btnActionClose: ImageButton
    private lateinit var tvActionCount: TextView
    private lateinit var btnActionReply: ImageButton
    private lateinit var btnActionCopy: ImageButton
    private lateinit var btnActionDelete: ImageButton
    private lateinit var btnActionInfo: ImageButton
    private var btnActionStar: ImageButton? = null

    // Reply preview views
    private lateinit var layoutReplyPreview: View
    private var viewReplyPreviewStripe: View? = null
    private lateinit var tvReplyPreviewName: TextView
    private lateinit var tvReplyPreviewText: TextView
    private var cardReplyPreviewThumb: View? = null
    private var ivReplyPreviewThumb: ImageView? = null
    private lateinit var btnCancelReply: ImageButton

    // Selection & Reply State
    private var selectedMessage: ChatMessage? = null
    private var replyingToMessage: ChatMessage? = null

    private var messagesListener: ListenerRegistration? = null
    private var typingListener: ListenerRegistration? = null
    private var coupleListener: ListenerRegistration? = null
    private var currentCouple: Couple? = null
    private var currentUser: User? = null
    private var partnerUser: User? = null
    private var mySenderName: String = "My Love"

    private var ivChatBackground: ImageView? = null
    private var viewChatWallpaperDim: View? = null

    private var pendingBackupJson: String? = null
    private var settingsDialog: BottomSheetDialog? = null
    private var heartbeatJob: Job? = null

    // Typing debounce handler
    private val typingHandler = Handler(Looper.getMainLooper())
    private var isCurrentlyTyping = false
    private val stopTypingRunnable = Runnable {
        if (isCurrentlyTyping) {
            isCurrentlyTyping = false
            val cId = currentCouple?.id ?: return@Runnable
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@Runnable
            viewLifecycleOwner.lifecycleScope.launch {
                repository.setUserPresence(cId, uid, "online", System.currentTimeMillis())
            }
        }
    }

    // Voice recording
    private var mediaRecorder: MediaRecorder? = null
    private var audioRecordingFile: File? = null
    private var isRecordingAudio = false
    private var recordingStartTime = 0L

    // Voice recording UI views & animation state
    private lateinit var layoutWhatsappPill: View
    private lateinit var layoutRecordingPanel: View
    private lateinit var btnRecordingTrash: ImageView
    private lateinit var ivRecordingPulseDot: ImageView
    private lateinit var tvRecordingTimer: TextView
    private lateinit var layoutSlideCancel: View
    private lateinit var tvSlideCancelHint: TextView
    private var eqBar1: View? = null
    private var eqBar2: View? = null
    private var eqBar3: View? = null
    private var eqBar4: View? = null
    private var eqBar5: View? = null

    private val recordingHandler = Handler(Looper.getMainLooper())
    private var recordingTimerRunnable: Runnable? = null
    private var recordStartX = 0f
    private var isSlideCancelled = false

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(requireContext(), "Microphone ready! Hold mic to record.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Microphone permission needed to record audio.", Toast.LENGTH_SHORT).show()
        }
    }

    // Video call permissions launcher (Camera + Microphone)
    private val callPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[android.Manifest.permission.CAMERA] == true
        val audioGranted = permissions[android.Manifest.permission.RECORD_AUDIO] == true
        if (cameraGranted && audioGranted) {
            launchVideoCall()
        } else {
            Toast.makeText(requireContext(), "Camera and Microphone permissions are required for video calls", Toast.LENGTH_SHORT).show()
        }
    }

    // Google Sign-In launcher for connecting account
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.result
                val email = account?.email
                if (!email.isNullOrBlank()) {
                    driveHelper.setConnectedAccountEmail(email)
                    updateSettingsAccountUi(email)
                    viewLifecycleOwner.lifecycleScope.launch {
                        repository.updateConnectedGoogleEmail(email)
                    }
                    Toast.makeText(requireContext(), "Connected to $email", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ChatFragment", "Google Sign-In failed", e)
                Toast.makeText(requireContext(), "Failed to link Google account", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Storage Access Framework launcher for creating backup file in Google Drive
    private val createBackupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri: Uri? = result.data?.data
        val json = pendingBackupJson
        if (result.resultCode == Activity.RESULT_OK && uri != null && json != null) {
            val success = driveHelper.writeBackupToUri(uri, json)
            if (success) {
                val count = chatAdapter.itemCount
                driveHelper.setLastBackupTime(count)
                updateSettingsBackupUi()
                Toast.makeText(requireContext(), "Backup saved to Google Drive! ☁️", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), "Failed to write backup file", Toast.LENGTH_SHORT).show()
            }
        }
        pendingBackupJson = null
    }

    // Storage Access Framework launcher for restoring backup file from Google Drive
    private val openBackupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri: Uri? = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            val json = driveHelper.readBackupFromUri(uri)
            if (json != null) {
                restoreBackup(json)
            } else {
                Toast.makeText(requireContext(), "Could not read selected file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Photo attachment launcher (Supports selecting multiple images at once)
    private val attachPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uploadAndSendPhotos(uris)
        }
    }

    // Direct Camera launcher (Full-Resolution via FileProvider)
    private var cameraPhotoUri: Uri? = null

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            cameraPhotoUri?.let { uri ->
                uploadAndSendPhoto(uri)
            }
        }
    }

    // Direct Camera thumbnail launcher (fallback)
    private val takePhotoPreviewLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            uploadAndSendBitmap(bitmap)
        }
    }

    private fun uploadAndSendBitmap(bitmap: Bitmap) {
        val coupleId = currentCouple?.id ?: return
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val replyId = replyingToMessage?.id
        val replyText = replyingToMessage?.let {
            if (it.text.isNotBlank()) it.text
            else if (!it.imageUrl.isNullOrBlank()) "📷 Photo"
            else if (!it.audioUrl.isNullOrBlank()) "🎙️ Voice note"
            else "Message"
        }
        val replySenderName = replyingToMessage?.let {
            if (it.senderId == currentUid) "You" else it.senderName.ifBlank { "My Love" }
        }
        val replyImageUrl = replyingToMessage?.imageUrl
        clearReplyMode()

        Toast.makeText(requireContext(), "Uploading photo...", Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            val bytes = stream.toByteArray()
            val uploadedUrl = repository.uploadImageBytes(bytes)
            if (!uploadedUrl.isNullOrBlank()) {
                repository.sendChatMessage(
                    coupleId = coupleId,
                    text = "",
                    imageUrl = uploadedUrl,
                    senderName = mySenderName,
                    replyToId = replyId,
                    replyToText = replyText,
                    replyToSenderName = replySenderName,
                    replyToImageUrl = replyImageUrl
                )
                triggerSendHaptic()
            } else {
                Toast.makeText(requireContext(), "Failed to upload photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Profile photo launcher
    private val pickProfileImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            uploadProfilePicture(uri)
        }
    }

    private fun uploadProfilePicture(uri: Uri) {
        Toast.makeText(requireContext(), "Uploading profile picture...", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val uploadedUrl = repository.uploadImage(requireContext(), uri)
            if (!uploadedUrl.isNullOrBlank()) {
                val success = repository.updateAvatarUrl(uploadedUrl)
                if (success) {
                    currentUser = currentUser?.copy(avatarUrl = uploadedUrl)
                    Toast.makeText(requireContext(), "Profile picture updated! ✨", Toast.LENGTH_SHORT).show()
                    val ivSheetAvatar = settingsDialog?.findViewById<ImageView>(R.id.iv_chat_settings_avatar)
                    if (ivSheetAvatar != null) {
                        Glide.with(this@ChatFragment)
                            .load(uploadedUrl)
                            .circleCrop()
                            .into(ivSheetAvatar)
                        ivSheetAvatar.imageTintList = null
                    }
                } else {
                    Toast.makeText(requireContext(), "Failed to save profile picture", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Failed to upload image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Shared Chat Wallpaper launcher
    private val pickWallpaperLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            uploadChatWallpaper(uri)
        }
    }

    private fun uploadChatWallpaper(uri: Uri) {
        val coupleId = currentCouple?.id ?: return
        Toast.makeText(requireContext(), "Setting chat wallpaper for both... 🌸", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val uploadedUrl = repository.uploadImage(requireContext(), uri)
            if (!uploadedUrl.isNullOrBlank()) {
                val success = repository.updateChatBackground(coupleId, uploadedUrl)
                if (success) {
                    currentCouple = currentCouple?.copy(chatBackgroundUrl = uploadedUrl)
                    applyChatWallpaper(uploadedUrl)
                    updateSettingsWallpaperUi(uploadedUrl)
                    Toast.makeText(requireContext(), "Chat wallpaper updated for both of you! 💕", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to save chat wallpaper", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Failed to upload wallpaper", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resetChatWallpaper() {
        val coupleId = currentCouple?.id ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val success = repository.updateChatBackground(coupleId, "")
            if (success) {
                currentCouple = currentCouple?.copy(chatBackgroundUrl = "")
                applyChatWallpaper("")
                updateSettingsWallpaperUi("")
                Toast.makeText(requireContext(), "Chat wallpaper reset to default", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Failed to reset wallpaper", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyChatWallpaper(url: String?) {
        if (!isAdded) return
        val iv = ivChatBackground ?: return
        val dim = viewChatWallpaperDim
        if (!url.isNullOrBlank()) {
            iv.visibility = View.VISIBLE
            dim?.visibility = View.VISIBLE
            Glide.with(this@ChatFragment)
                .load(url)
                .centerCrop()
                .into(iv)
        } else {
            Glide.with(this@ChatFragment).clear(iv)
            iv.setImageDrawable(null)
            iv.visibility = View.GONE
            dim?.visibility = View.GONE
        }
    }

    private fun startVideoCallFlow() {
        val hasCamera = ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasAudio = ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasCamera && hasAudio) {
            launchVideoCall()
        } else {
            callPermissionsLauncher.launch(arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO))
        }
    }

    private fun launchVideoCall() {
        val coupleId = currentCouple?.id ?: run {
            Toast.makeText(requireContext(), "Couple connection not loaded yet", Toast.LENGTH_SHORT).show()
            return
        }
        val pUser = partnerUser
        val partnerName = tvPartnerName?.text?.toString()?.takeIf { it.isNotBlank() } ?: pUser?.name ?: "My Love"
        val partnerAvatar = pUser?.avatarUrl ?: ""
        val partnerId = pUser?.uid ?: (if (currentCouple?.user1 == FirebaseAuth.getInstance().currentUser?.uid) currentCouple?.user2 else currentCouple?.user1) ?: ""

        val intent = Intent(requireContext(), com.ourbloom.app.call.VideoCallActivity::class.java).apply {
            putExtra(com.ourbloom.app.call.VideoCallActivity.EXTRA_COUPLE_ID, coupleId)
            putExtra(com.ourbloom.app.call.VideoCallActivity.EXTRA_PARTNER_NAME, partnerName)
            putExtra(com.ourbloom.app.call.VideoCallActivity.EXTRA_PARTNER_AVATAR, partnerAvatar)
            putExtra(com.ourbloom.app.call.VideoCallActivity.EXTRA_PARTNER_ID, partnerId)
            putExtra(com.ourbloom.app.call.VideoCallActivity.EXTRA_IS_CALLER, true)
        }
        startActivity(intent)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = FirestoreRepository()
        driveHelper = GoogleDriveBackupHelper(requireContext())

        rvMessages = view.findViewById(R.id.rv_chat_messages)
        etInput = view.findViewById(R.id.et_chat_input)
        btnSend = view.findViewById(R.id.btn_send_chat)
        btnAttach = view.findViewById(R.id.btn_attach_photo)
        btnSettings = view.findViewById(R.id.btn_chat_settings)
        tvPartnerName = view.findViewById(R.id.tv_chat_partner_name)
        tvChatStatus = view.findViewById(R.id.tv_chat_status)
        ivPartnerAvatar = view.findViewById(R.id.iv_partner_avatar)
        layoutEmpty = view.findViewById(R.id.layout_chat_empty)
        ivChatBackground = view.findViewById(R.id.iv_chat_background)
        viewChatWallpaperDim = view.findViewById(R.id.view_chat_wallpaper_dim)

        // Action Bar & Reply Preview Views
        layoutActionBar = view.findViewById(R.id.layout_chat_action_bar)
        chatHeader = view.findViewById(R.id.chat_header)
        btnActionClose = view.findViewById(R.id.btn_action_close)
        tvActionCount = view.findViewById(R.id.tv_action_count)
        btnActionReply = view.findViewById(R.id.btn_action_reply)
        btnActionCopy = view.findViewById(R.id.btn_action_copy)
        btnActionDelete = view.findViewById(R.id.btn_action_delete)
        btnActionInfo = view.findViewById(R.id.btn_action_info)
        btnActionStar = view.findViewById(R.id.btn_action_star)
        btnActionStar?.setOnClickListener {
            selectedMessage?.let { msg ->
                val target = msg.imageUrl ?: msg.text
                if (target.isNotBlank()) {
                    val isFav = StickerManager.toggleFavorite(requireContext(), target)
                    Toast.makeText(requireContext(), if (isFav) "Starred ⭐" else "Unstarred", Toast.LENGTH_SHORT).show()
                }
            }
            clearSelection()
        }

        layoutReplyPreview = view.findViewById(R.id.layout_reply_preview)
        viewReplyPreviewStripe = view.findViewById(R.id.view_reply_preview_stripe)
        tvReplyPreviewName = view.findViewById(R.id.tv_reply_preview_name)
        tvReplyPreviewText = view.findViewById(R.id.tv_reply_preview_text)
        cardReplyPreviewThumb = view.findViewById(R.id.card_reply_preview_thumb)
        ivReplyPreviewThumb = view.findViewById(R.id.iv_reply_preview_thumb)
        btnCancelReply = view.findViewById(R.id.btn_cancel_reply)

        // Voice Recording Panel Views
        layoutWhatsappPill = view.findViewById(R.id.layout_whatsapp_pill)
        layoutRecordingPanel = view.findViewById(R.id.layout_recording_panel)
        btnRecordingTrash = view.findViewById(R.id.btn_recording_trash)
        ivRecordingPulseDot = view.findViewById(R.id.iv_recording_pulse_dot)
        tvRecordingTimer = view.findViewById(R.id.tv_recording_timer)
        layoutSlideCancel = view.findViewById(R.id.layout_slide_cancel)
        tvSlideCancelHint = view.findViewById(R.id.tv_slide_cancel_hint)
        eqBar1 = view.findViewById(R.id.eq_bar_1)
        eqBar2 = view.findViewById(R.id.eq_bar_2)
        eqBar3 = view.findViewById(R.id.eq_bar_3)
        eqBar4 = view.findViewById(R.id.eq_bar_4)
        eqBar5 = view.findViewById(R.id.eq_bar_5)

        btnRecordingTrash.setOnClickListener {
            stopVoiceRecording(send = false)
        }

        // WhatsApp-style header back button
        view.findViewById<ImageButton>(R.id.btn_chat_back)?.setOnClickListener {
            findNavController().navigateUp()
        }

        // WhatsApp-style header Video Call button
        view.findViewById<ImageButton>(R.id.btn_video_call)?.setOnClickListener {
            startVideoCallFlow()
        }

        val btnEmoji = view.findViewById<ImageButton>(R.id.btn_chat_emoji)
        val btnCamera = view.findViewById<ImageButton>(R.id.btn_chat_camera)

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        chatAdapter = ChatAdapter(currentUid) { message ->
            val url = message.imageUrl
            if (!url.isNullOrBlank()) {
                val timeStr = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(message.timestamp))
                val senderLabel = if (message.senderId == currentUid) "You" else message.senderName.ifBlank { "My Love" }
                showChatImageLightbox(url, senderLabel, timeStr)
            }
        }

        chatAdapter.onAlbumImageClick = { albumMessages, clickedIndex ->
            val urls = albumMessages.mapNotNull { it.imageUrl }.filter { it.isNotBlank() }
            val initialMsg = albumMessages.getOrNull(clickedIndex) ?: albumMessages.firstOrNull()
            if (urls.isNotEmpty() && initialMsg != null) {
                val timeStr = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(initialMsg.timestamp))
                val senderLabel = if (initialMsg.senderId == currentUid) "You" else initialMsg.senderName.ifBlank { "My Love" }
                showChatAlbumLightbox(urls, clickedIndex.coerceIn(0, urls.size - 1), senderLabel, timeStr)
            }
        }

        chatAdapter.onMessageLongClick = { message ->
            selectMessage(message)
        }

        chatAdapter.onMessageClick = { message ->
            if (selectedMessage != null) {
                if (selectedMessage?.id == message.id) {
                    clearSelection()
                } else {
                    selectMessage(message)
                }
            }
        }

        chatAdapter.onQuoteClick = { targetMsgId ->
            val pos = chatAdapter.getMessagePosition(targetMsgId)
            if (pos != -1) {
                rvMessages.smoothScrollToPosition(pos)
                chatAdapter.flashHighlightMessage(targetMsgId)
            }
        }

        val layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        rvMessages.layoutManager = layoutManager
        rvMessages.adapter = chatAdapter

        // WhatsApp-style Swipe-to-Reply ItemTouchHelper
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            private var isSwipeTriggered = false
            private var hasVibrated = false
            private var wasActive = false
            private val replyIcon: Drawable? = ContextCompat.getDrawable(requireContext(), R.drawable.ic_reply)

            override fun isLongPressDragEnabled(): Boolean = false
            override fun isItemViewSwipeEnabled(): Boolean = true

            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                if (chatAdapter.getSelectedMessage() != null) {
                    return makeMovementFlags(0, 0)
                }
                return makeMovementFlags(0, ItemTouchHelper.RIGHT)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            // Set high threshold so ItemTouchHelper NEVER dismisses the item
            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 10f
            override fun getSwipeEscapeVelocity(defaultValue: Float): Float = Float.MAX_VALUE
            override fun getSwipeVelocityThreshold(defaultValue: Float): Float = Float.MAX_VALUE

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not called because threshold is 10f
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val bubbleContainer: View? = itemView.findViewById(R.id.layout_bubble_container) ?: itemView
                val partnerAvatar: View? = itemView.findViewById(R.id.iv_chat_partner_avatar)
                val density = resources.displayMetrics.density
                val maxSwipe = 75f * density
                val triggerThreshold = 42f * density

                // Smooth elastic translation
                val translationX = if (dX > 0) {
                    (dX * 0.5f).coerceAtMost(maxSwipe)
                } else 0f

                // Translate ONLY the bubble container so avatar stays pinned
                bubbleContainer?.translationX = translationX

                if (isCurrentlyActive) {
                    wasActive = true
                    if (translationX >= triggerThreshold) {
                        if (!hasVibrated) {
                            triggerSendHaptic()
                            hasVibrated = true
                        }
                        isSwipeTriggered = true
                    } else {
                        isSwipeTriggered = false
                        hasVibrated = false
                    }
                } else {
                    // Finger released! Check if swipe threshold was met
                    if (wasActive) {
                        wasActive = false
                        if (isSwipeTriggered) {
                            isSwipeTriggered = false
                            hasVibrated = false
                            val position = viewHolder.adapterPosition
                            if (position != RecyclerView.NO_POSITION) {
                                val message = chatAdapter.getMessageAt(position)
                                if (message != null) {
                                    enterReplyMode(message)
                                }
                            }
                        }
                    }
                    if (translationX == 0f) {
                        bubbleContainer?.translationX = 0f
                    }
                }

                // Draw WhatsApp reply indicator icon behind or beside the sliding message bubble
                if (translationX > 3f && replyIcon != null && bubbleContainer != null) {
                    val circleRadius = 17f * density
                    val iconSize = (18f * density).toInt()
                    val centerY = itemView.top + bubbleContainer.top + (bubbleContainer.height / 2f)
                    val currentBubbleLeft = itemView.left + bubbleContainer.left + translationX

                    // Position circle cleanly for both received messages (in expanding gap) and sent messages (beside bubble)
                    val circleCenterX = if (partnerAvatar != null) {
                        val avatarRight = itemView.left + (partnerAvatar.parent as? View ?: partnerAvatar).right.toFloat()
                        (avatarRight + currentBubbleLeft) / 2f
                    } else {
                        currentBubbleLeft - circleRadius - (8f * density)
                    }

                    val progress = (translationX / triggerThreshold).coerceIn(0f, 1f)

                    // Circular background
                    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (progress >= 1f) {
                            0xFFE85D75.toInt() // Primary accent rose
                        } else {
                            0x25000000.toInt() // Soft translucent
                        }
                        alpha = (progress * 255).toInt()
                    }
                    val scaledRadius = circleRadius * (0.4f + 0.6f * progress)
                    c.drawCircle(circleCenterX, centerY, scaledRadius, circlePaint)

                    // Reply arrow icon inside circle with rotation
                    val iconLeft = (circleCenterX - iconSize / 2f).toInt()
                    val iconTop = (centerY - iconSize / 2f).toInt()
                    val iconRight = iconLeft + iconSize
                    val iconBottom = iconTop + iconSize

                    replyIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    replyIcon.setTint(if (progress >= 1f) Color.WHITE else 0xFF65676B.toInt())
                    replyIcon.alpha = (progress * 255).toInt()

                    c.save()
                    c.scale(0.5f + 0.5f * progress, 0.5f + 0.5f * progress, circleCenterX, centerY)
                    c.rotate(-30f * (1f - progress), circleCenterX, centerY)
                    replyIcon.draw(c)
                    c.restore()
                }

                // Pass dX = 0f so ItemTouchHelper does NOT move the entire row
                getDefaultUIUtil().onDraw(c, recyclerView, itemView, 0f, dY, actionState, isCurrentlyActive)
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                getDefaultUIUtil().clearView(viewHolder.itemView)
                viewHolder.itemView.findViewById<View>(R.id.layout_bubble_container)
                    ?.animate()?.translationX(0f)?.setDuration(180)?.start()

                if (isSwipeTriggered) {
                    isSwipeTriggered = false
                    hasVibrated = false
                    val position = viewHolder.adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        val message = chatAdapter.getMessageAt(position)
                        if (message != null) {
                            enterReplyMode(message)
                        }
                    }
                }
                wasActive = false
                hasVibrated = false
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(rvMessages)

        // Action Bar Buttons
        btnActionClose.setOnClickListener {
            clearSelection()
        }

        btnActionInfo.setOnClickListener {
            val msg = selectedMessage ?: return@setOnClickListener
            clearSelection()
            showMessageInfoBottomSheet(msg)
        }

        btnActionReply.setOnClickListener {
            val msg = selectedMessage
            clearSelection()
            if (msg != null) {
                enterReplyMode(msg)
            }
        }

        btnActionCopy.setOnClickListener {
            val msg = selectedMessage ?: return@setOnClickListener
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val textToCopy = when {
                msg.text.isNotBlank() -> msg.text
                !msg.imageUrl.isNullOrBlank() -> msg.imageUrl ?: ""
                !msg.audioUrl.isNullOrBlank() -> msg.audioUrl ?: ""
                else -> ""
            }
            if (textToCopy.isNotBlank()) {
                val clip = android.content.ClipData.newPlainText("Chat Message", textToCopy)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
                triggerSendHaptic()
            }
            clearSelection()
        }

        btnActionDelete.setOnClickListener {
            val msg = selectedMessage ?: return@setOnClickListener
            val isMine = msg.senderId == currentUid

            val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            builder.setTitle("Delete message?")

            if (isMine) {
                val options = arrayOf("Delete for everyone", "Delete for me", "Cancel")
                builder.setItems(options) { dialog, which ->
                    when (which) {
                        0 -> {
                            viewLifecycleOwner.lifecycleScope.launch {
                                val ok = repository.deleteChatMessageForEveryone(msg.id)
                                if (ok) {
                                    Toast.makeText(requireContext(), "Deleted for everyone", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(requireContext(), "Failed to delete", Toast.LENGTH_SHORT).show()
                                }
                            }
                            clearSelection()
                        }
                        1 -> {
                            viewLifecycleOwner.lifecycleScope.launch {
                                val ok = repository.deleteChatMessageForMe(msg.id, currentUid)
                                if (ok) {
                                    Toast.makeText(requireContext(), "Deleted for you", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(requireContext(), "Failed to delete", Toast.LENGTH_SHORT).show()
                                }
                            }
                            clearSelection()
                        }
                        2 -> dialog.dismiss()
                    }
                }
            } else {
                val options = arrayOf("Delete for me", "Cancel")
                builder.setItems(options) { dialog, which ->
                    when (which) {
                        0 -> {
                            viewLifecycleOwner.lifecycleScope.launch {
                                val ok = repository.deleteChatMessageForMe(msg.id, currentUid)
                                if (ok) {
                                    Toast.makeText(requireContext(), "Deleted for you", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(requireContext(), "Failed to delete", Toast.LENGTH_SHORT).show()
                                }
                            }
                            clearSelection()
                        }
                        1 -> dialog.dismiss()
                    }
                }
            }
            builder.show()
        }

        btnCancelReply.setOnClickListener {
            clearReplyMode()
        }

        // Handle Back button to clear selection or cancel reply first
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (selectedMessage != null) {
                    clearSelection()
                } else if (replyingToMessage != null) {
                    clearReplyMode()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Dynamically toggle Mic / Send icon like WhatsApp & push typing state
        etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrBlank()
                val cId = currentCouple?.id
                val uid = FirebaseAuth.getInstance().currentUser?.uid

                if (hasText) {
                    btnSend.setImageResource(R.drawable.ic_send_rounded)
                    btnSend.contentDescription = "Send Message"

                    if (cId != null && uid != null) {
                        typingHandler.removeCallbacks(stopTypingRunnable)
                        if (!isCurrentlyTyping) {
                            isCurrentlyTyping = true
                            viewLifecycleOwner.lifecycleScope.launch {
                                repository.setUserPresence(cId, uid, "typing", System.currentTimeMillis())
                            }
                        }
                        typingHandler.postDelayed(stopTypingRunnable, 3000)
                    }
                } else {
                    btnSend.setImageResource(R.drawable.ic_mic_whatsapp)
                    btnSend.contentDescription = "Voice Note"

                    if (isCurrentlyTyping) {
                        isCurrentlyTyping = false
                        typingHandler.removeCallbacks(stopTypingRunnable)
                        if (cId != null && uid != null) {
                            viewLifecycleOwner.lifecycleScope.launch {
                                repository.setUserPresence(cId, uid, "online", System.currentTimeMillis())
                            }
                        }
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // WhatsApp-style dual button: Tap to Send (when text present) / Hold to Record (when empty)
        btnSend.setOnTouchListener { _, event ->
            val hasText = !etInput.text.isNullOrBlank()
            if (hasText) {
                // Return false so normal OnClickListener executes for sending text
                return@setOnTouchListener false
            }

            val density = resources.displayMetrics.density
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    recordStartX = event.rawX
                    isSlideCancelled = false
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        startVoiceRecording()
                    } else {
                        requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isRecordingAudio) {
                        val deltaX = event.rawX - recordStartX
                        if (deltaX < -90f * density) {
                            if (!isSlideCancelled) {
                                isSlideCancelled = true
                                tvSlideCancelHint.text = "Release to cancel"
                                tvSlideCancelHint.setTextColor(0xFFE85D75.toInt())
                                btnRecordingTrash.setColorFilter(0xFFE85D75.toInt())
                                triggerSendHaptic()
                            }
                        } else {
                            if (isSlideCancelled) {
                                isSlideCancelled = false
                                tvSlideCancelHint.text = "Slide to cancel"
                                tvSlideCancelHint.setTextColor(0xFF888888.toInt())
                                btnRecordingTrash.setColorFilter(0xFF888888.toInt())
                            }
                            layoutSlideCancel.translationX = (deltaX * 0.35f).coerceIn(-35f * density, 0f)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isRecordingAudio) {
                        if (isSlideCancelled) {
                            stopVoiceRecording(send = false)
                        } else {
                            stopVoiceRecording(send = true)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (isRecordingAudio) {
                        stopVoiceRecording(send = false)
                    }
                    true
                }
                else -> false
            }
        }

        btnSend.setOnClickListener {
            val text = etInput.text?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) {
                sendMessage()
            }
        }

        btnAttach.setOnClickListener {
            attachPhotoLauncher.launch("image/*")
        }

        btnCamera.setOnClickListener {
            try {
                val imagesDir = File(requireContext().cacheDir, "images")
                imagesDir.mkdirs()
                val photoFile = File(imagesDir, "camera_${System.currentTimeMillis()}.jpg")
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    photoFile
                )
                cameraPhotoUri = uri
                takePictureLauncher.launch(uri)
            } catch (e: Exception) {
                Log.e("ChatFragment", "Failed to launch full-resolution camera: ${e.message}")
                takePhotoPreviewLauncher.launch(null)
            }
        }

        btnEmoji.setOnClickListener {
            val stickerSheet = StickerPickerBottomSheet.newInstance { file ->
                uploadAndSendSticker(file)
            }
            stickerSheet.show(childFragmentManager, StickerPickerBottomSheet.TAG)
        }

        btnEmoji.setOnLongClickListener {
            val popup = PopupMenu(requireContext(), btnEmoji)
            val emojis = listOf("❤️", "🌸", "🥰", "✨", "😘", "💖", "🫂", "🌹", "😍", "🙈")
            emojis.forEach { emoji ->
                popup.menu.add(emoji)
            }
            popup.setOnMenuItemClickListener { item ->
                etInput.append(item.title)
                true
            }
            popup.show()
            true
        }

        btnSettings.setOnClickListener {
            showChatSettingsBottomSheet()
        }

        loadUserData()
    }

    private fun loadUserData() {
        viewLifecycleOwner.lifecycleScope.launch {
            val user = repository.getCurrentUser()
            currentUser = user
            if (user != null && !user.connectedGoogleEmail.isNullOrBlank() && driveHelper.getConnectedAccountEmail().isNullOrBlank()) {
                driveHelper.setConnectedAccountEmail(user.connectedGoogleEmail)
            }
            val cId = user?.coupleId
            if (user != null && !cId.isNullOrEmpty()) {
                val couple = repository.getCouple(cId)
                currentCouple = couple

                if (couple != null) {
                    val partnerId = if (couple.user1 == user.uid) couple.user2 else couple.user1
                    if (partnerId.isNotBlank()) {
                        partnerUser = repository.getUser(partnerId)
                    }

                    // Load partner's avatar
                    val partnerAvatarUrl = partnerUser?.avatarUrl
                    if (!partnerAvatarUrl.isNullOrBlank()) {
                        Glide.with(this@ChatFragment)
                            .load(partnerAvatarUrl)
                            .circleCrop()
                            .placeholder(R.drawable.ic_favorite)
                            .into(ivPartnerAvatar)
                        ivPartnerAvatar.imageTintList = null
                        ivPartnerAvatar.setPadding(0, 0, 0, 0)
                    }
                    chatAdapter.partnerAvatarUrl = partnerAvatarUrl

                    // Partner's nickname for header
                    val partnerDisplayName = user.nicknameForPartner?.takeIf { it.isNotBlank() }
                        ?: partnerUser?.name?.takeIf { it.isNotBlank() }
                        ?: "My Love"
                    tvPartnerName.text = partnerDisplayName
                    etInput.hint = "Type a message"

                    // What the partner calls me
                    mySenderName = partnerUser?.nicknameForPartner?.takeIf { it.isNotBlank() }
                        ?: user.name?.takeIf { it.isNotBlank() }
                        ?: "Your Love"

                    setupMessagesListener(cId)
                    if (partnerId.isNotBlank()) {
                        setupPresenceListener(cId, partnerId)
                    }

                    // Apply and listen to shared chat wallpaper in real time
                    applyChatWallpaper(couple.chatBackgroundUrl)
                    setupCoupleListener(cId)
                }
            }
        }
    }

    private fun setupCoupleListener(coupleId: String) {
        coupleListener?.remove()
        coupleListener = repository.getCoupleListener(coupleId) { couple ->
            if (couple != null) {
                currentCouple = couple
                applyChatWallpaper(couple.chatBackgroundUrl)
                updateSettingsWallpaperUi(couple.chatBackgroundUrl)
            }
        }
    }

    private fun setupPresenceListener(coupleId: String, partnerId: String) {
        typingListener?.remove()
        typingListener = repository.listenPresenceAndTyping(coupleId, partnerId) { presence ->
            if (!isAdded) return@listenPresenceAndTyping
            when (presence.status) {
                "typing" -> {
                    tvChatStatus.text = "typing..."
                    tvChatStatus.setTextColor(0xFF25D366.toInt()) // WhatsApp green
                }
                "recording" -> {
                    tvChatStatus.text = "🎙️ recording audio..."
                    tvChatStatus.setTextColor(0xFFE85D75.toInt()) // Rose accent
                }
                "online" -> {
                    tvChatStatus.text = "online"
                    tvChatStatus.setTextColor(0xFF25D366.toInt()) // WhatsApp green
                }
                else -> {
                    tvChatStatus.text = formatLastSeenTime(presence.lastSeen)
                    tvChatStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.chat_header_subtitle))
                }
            }
        }
    }

    private fun formatLastSeenTime(lastSeen: Long): String {
        if (lastSeen <= 0L) return "Forever blooming together 🌸"
        val now = System.currentTimeMillis()
        val diff = now - lastSeen
        if (diff < 60_000L) {
            return "last seen just now"
        }

        val calNow = Calendar.getInstance().apply { timeInMillis = now }
        val calSeen = Calendar.getInstance().apply { timeInMillis = lastSeen }

        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val formattedTime = timeFormat.format(Date(lastSeen)).lowercase(Locale.getDefault())

        val isToday = calNow.get(Calendar.YEAR) == calSeen.get(Calendar.YEAR) &&
                calNow.get(Calendar.DAY_OF_YEAR) == calSeen.get(Calendar.DAY_OF_YEAR)

        if (isToday) {
            return "last seen today at $formattedTime"
        }

        calNow.add(Calendar.DAY_OF_YEAR, -1)
        val isYesterday = calNow.get(Calendar.YEAR) == calSeen.get(Calendar.YEAR) &&
                calNow.get(Calendar.DAY_OF_YEAR) == calSeen.get(Calendar.DAY_OF_YEAR)

        if (isYesterday) {
            return "last seen yesterday at $formattedTime"
        }

        val dateFormat = SimpleDateFormat("d MMM 'at' h:mm a", Locale.getDefault())
        return "last seen " + dateFormat.format(Date(lastSeen)).lowercase(Locale.getDefault())
    }

    private fun setupMessagesListener(coupleId: String) {
        messagesListener?.remove()
        messagesListener = repository.getChatMessagesListener(coupleId) { messages ->
            chatAdapter.submitList(messages)
            if (messages.isNotEmpty()) {
                layoutEmpty.visibility = View.GONE
                rvMessages.scrollToPosition(messages.size - 1)
            } else {
                layoutEmpty.visibility = View.VISIBLE
            }

            // Pre-cache recent voice notes in background for instant 0ms playback
            val appContext = context?.applicationContext
            if (appContext != null) {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    messages.filter { !it.audioUrl.isNullOrBlank() }.takeLast(5).forEach { msg ->
                        msg.audioUrl?.let { url ->
                            AudioCacheManager.getOrDownloadAudio(appContext, url)
                        }
                    }
                }
            }

            // Real-time WhatsApp double blue ticks: ONLY mark partner messages as read
            // if the user is ACTUALLY present and actively viewing the chat screen!
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            if (currentUid.isNotBlank() && isChatVisible && isResumed && isAdded) {
                val unreadPartnerIds = messages.filter { 
                    it.senderId.isNotBlank() && it.senderId != currentUid && !it.isRead
                }.map { it.id }
                if (unreadPartnerIds.isNotEmpty()) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        repository.markMessagesReadByIds(unreadPartnerIds)
                        try {
                            MyFirebaseMessagingService.dismissChatNotifications(requireContext())
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    private fun resetRecordingUi() {
        recordingTimerRunnable?.let { recordingHandler.removeCallbacks(it) }
        recordingTimerRunnable = null
        ivRecordingPulseDot.clearAnimation()
        btnSend.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
        layoutRecordingPanel.visibility = View.GONE
        layoutWhatsappPill.visibility = View.VISIBLE
        layoutSlideCancel.translationX = 0f
        tvSlideCancelHint.text = "Slide to cancel"
        tvSlideCancelHint.setTextColor(0xFF888888.toInt())
        btnRecordingTrash.setColorFilter(0xFF888888.toInt())
    }

    private fun startVoiceRecording() {
        val coupleId = currentCouple?.id ?: return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        try {
            val file = File(requireContext().cacheDir, "voice_chat_${System.currentTimeMillis()}.m4a")
            audioRecordingFile = file
            recordingStartTime = System.currentTimeMillis()

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(requireContext())
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            isRecordingAudio = true
            isSlideCancelled = false
            triggerSendHaptic()

            // Visual UI transition
            layoutWhatsappPill.visibility = View.GONE
            layoutRecordingPanel.visibility = View.VISIBLE
            tvRecordingTimer.text = "0:00"
            layoutSlideCancel.translationX = 0f
            tvSlideCancelHint.text = "Slide to cancel"
            tvSlideCancelHint.setTextColor(0xFF888888.toInt())
            btnRecordingTrash.setColorFilter(0xFF888888.toInt())

            // Pulsing Red Dot Animation
            val pulse = AlphaAnimation(1f, 0.2f).apply {
                duration = 450
                repeatMode = Animation.REVERSE
                repeatCount = Animation.INFINITE
            }
            ivRecordingPulseDot.startAnimation(pulse)

            // Tactile scaling animation on mic button
            btnSend.animate().scaleX(1.22f).scaleY(1.22f).setDuration(150).start()

            // Live Timer & Equalizer Waveform Animation
            val density = resources.displayMetrics.density
            val minHeightPx = (4f * density).toInt()
            val maxDynamicPx = (18f * density).toInt()

            recordingTimerRunnable?.let { recordingHandler.removeCallbacks(it) }
            recordingTimerRunnable = object : Runnable {
                override fun run() {
                    if (!isRecordingAudio) return
                    val elapsedMs = System.currentTimeMillis() - recordingStartTime
                    val totalSec = elapsedMs / 1000
                    tvRecordingTimer.text = String.format(Locale.getDefault(), "%d:%02d", totalSec / 60, totalSec % 60)

                    // Amplitude sampling for live 5-bar equalizer
                    try {
                        val maxAmp = mediaRecorder?.maxAmplitude ?: 0
                        val norm = (maxAmp / 32767f).coerceIn(0f, 1f)

                        val h1 = (minHeightPx + (norm * 0.7f + 0.15f) * maxDynamicPx).toInt().coerceIn(minHeightPx, (22f * density).toInt())
                        val h2 = (minHeightPx + (norm * 1.0f + 0.35f) * maxDynamicPx).toInt().coerceIn(minHeightPx, (22f * density).toInt())
                        val h3 = (minHeightPx + (norm * 1.2f + 0.50f) * maxDynamicPx).toInt().coerceIn(minHeightPx, (22f * density).toInt())
                        val h4 = (minHeightPx + (norm * 0.9f + 0.28f) * maxDynamicPx).toInt().coerceIn(minHeightPx, (22f * density).toInt())
                        val h5 = (minHeightPx + (norm * 0.6f + 0.18f) * maxDynamicPx).toInt().coerceIn(minHeightPx, (22f * density).toInt())

                        eqBar1?.layoutParams?.height = h1
                        eqBar1?.requestLayout()
                        eqBar2?.layoutParams?.height = h2
                        eqBar2?.requestLayout()
                        eqBar3?.layoutParams?.height = h3
                        eqBar3?.requestLayout()
                        eqBar4?.layoutParams?.height = h4
                        eqBar4?.requestLayout()
                        eqBar5?.layoutParams?.height = h5
                        eqBar5?.requestLayout()
                    } catch (_: Exception) {}

                    recordingHandler.postDelayed(this, 90)
                }
            }
            recordingHandler.post(recordingTimerRunnable!!)

            // Broadcast recording status to partner
            viewLifecycleOwner.lifecycleScope.launch {
                repository.setUserPresence(coupleId, uid, "recording", System.currentTimeMillis())
            }
        } catch (e: Exception) {
            Log.e("ChatFragment", "Failed to start audio recording", e)
            isRecordingAudio = false
            try { mediaRecorder?.release() } catch (_: Exception) {}
            mediaRecorder = null
            resetRecordingUi()
        }
    }

    private fun selectMessage(message: ChatMessage) {
        selectedMessage = message
        chatAdapter.setSelectedMessage(message.id)
        layoutActionBar.visibility = View.VISIBLE
        chatHeader.visibility = View.GONE
        tvActionCount.text = "1"
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        btnActionInfo.visibility = if (message.senderId == currentUid) View.VISIBLE else View.GONE
        triggerSendHaptic()
    }

    private fun clearSelection() {
        selectedMessage = null
        chatAdapter.setSelectedMessage(null)
        btnActionInfo.visibility = View.GONE
        layoutActionBar.visibility = View.GONE
        chatHeader.visibility = View.VISIBLE
    }

    private fun enterReplyMode(message: ChatMessage) {
        replyingToMessage = message
        layoutReplyPreview.visibility = View.VISIBLE

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val isSenderYou = message.senderId == currentUid
        val senderLabel = if (isSenderYou) "You" else message.senderName.ifBlank { "My Love" }
        tvReplyPreviewName.text = "Replying to $senderLabel"

        // Dynamic WhatsApp accent colors:
        // You -> Rose (#E85D75), Partner -> Emerald Green (#00A884)
        val accentColor = if (isSenderYou) 0xFFE85D75.toInt() else 0xFF00A884.toInt()
        viewReplyPreviewStripe?.setBackgroundColor(accentColor)
        tvReplyPreviewName.setTextColor(accentColor)

        tvReplyPreviewText.text = when {
            message.text.isNotBlank() -> message.text
            !message.imageUrl.isNullOrBlank() -> "📷 Photo"
            !message.audioUrl.isNullOrBlank() -> "🎙️ Voice note"
            else -> "Message"
        }

        val imgUrl = message.imageUrl
        if (!imgUrl.isNullOrBlank() && cardReplyPreviewThumb != null && ivReplyPreviewThumb != null) {
            cardReplyPreviewThumb?.visibility = View.VISIBLE
            Glide.with(this)
                .load(imgUrl)
                .centerCrop()
                .into(ivReplyPreviewThumb!!)
        } else {
            cardReplyPreviewThumb?.visibility = View.GONE
        }

        etInput.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.showSoftInput(etInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        triggerSendHaptic()
    }

    private fun clearReplyMode() {
        replyingToMessage = null
        layoutReplyPreview.visibility = View.GONE
        cardReplyPreviewThumb?.visibility = View.GONE
    }

    private fun stopVoiceRecording(send: Boolean) {
        if (!isRecordingAudio) return
        isRecordingAudio = false
        resetRecordingUi()

        val coupleId = currentCouple?.id
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (coupleId != null && uid != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                repository.setTypingStatus(coupleId, uid, "idle")
            }
        }

        val durationMs = System.currentTimeMillis() - recordingStartTime
        val file = audioRecordingFile

        if (durationMs < 800) {
            try { mediaRecorder?.stop() } catch (_: Exception) {}
            try { mediaRecorder?.release() } catch (_: Exception) {}
            mediaRecorder = null
            file?.delete()
            audioRecordingFile = null
            Toast.makeText(requireContext(), "Hold to record voice note, release to send", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e("ChatFragment", "Error stopping recorder", e)
        }
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null

        if (send && file != null && file.exists() && file.length() > 500 && coupleId != null) {
            val replyId = replyingToMessage?.id
            val replyText = replyingToMessage?.let {
                if (it.text.isNotBlank()) it.text
                else if (!it.imageUrl.isNullOrBlank()) "📷 Photo"
                else if (!it.audioUrl.isNullOrBlank()) "🎙️ Voice note"
                else "Message"
            }
            val replySenderName = replyingToMessage?.let {
                if (it.senderId == uid) "You" else it.senderName.ifBlank { "My Love" }
            }
            val replyImageUrl = replyingToMessage?.imageUrl
            clearReplyMode()

            triggerSendHaptic()
            val appContext = requireContext().applicationContext
            Toast.makeText(requireContext(), "Sending voice note...", Toast.LENGTH_SHORT).show()
            viewLifecycleOwner.lifecycleScope.launch {
                val uploadedUrl = repository.uploadAudioFile(file)
                if (!uploadedUrl.isNullOrBlank()) {
                    // Save to local cache so sender never has to stream it
                    AudioCacheManager.saveToCache(appContext, uploadedUrl, file)
                    file.delete()
                    repository.sendChatMessage(
                        coupleId = coupleId,
                        text = "🎙️ Voice note",
                        imageUrl = null,
                        audioUrl = uploadedUrl,
                        audioDurationMs = durationMs,
                        senderName = mySenderName,
                        replyToId = replyId,
                        replyToText = replyText,
                        replyToSenderName = replySenderName,
                        replyToImageUrl = replyImageUrl
                    )
                } else {
                    Toast.makeText(requireContext(), "Failed to send voice note. Check connection.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            file?.delete()
            if (!send) {
                triggerSendHaptic()
            }
        }
        audioRecordingFile = null

        val cId = currentCouple?.id
        val uId = FirebaseAuth.getInstance().currentUser?.uid
        if (cId != null && uId != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                repository.setUserPresence(cId, uId, "online", System.currentTimeMillis())
            }
        }
    }

    private fun sendMessage() {
        val text = etInput.text.toString().trim()
        val coupleId = currentCouple?.id ?: return
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (text.isBlank()) return

        val replyId = replyingToMessage?.id
        val replyText = replyingToMessage?.let {
            if (it.text.isNotBlank()) it.text
            else if (!it.imageUrl.isNullOrBlank()) "📷 Photo"
            else if (!it.audioUrl.isNullOrBlank()) "🎙️ Voice note"
            else "Message"
        }
        val replySenderName = replyingToMessage?.let {
            if (it.senderId == currentUid) "You" else it.senderName.ifBlank { "My Love" }
        }
        val replyImageUrl = replyingToMessage?.imageUrl
        clearReplyMode()

        etInput.setText("")
        triggerSendHaptic()

        if (isCurrentlyTyping) {
            isCurrentlyTyping = false
            typingHandler.removeCallbacks(stopTypingRunnable)
            viewLifecycleOwner.lifecycleScope.launch {
                repository.setUserPresence(coupleId, currentUid, "online", System.currentTimeMillis())
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repository.sendChatMessage(
                coupleId = coupleId,
                text = text,
                imageUrl = null,
                audioUrl = null,
                senderName = mySenderName,
                replyToId = replyId,
                replyToText = replyText,
                replyToSenderName = replySenderName,
                replyToImageUrl = replyImageUrl
            )
        }
    }

    private fun uploadAndSendPhoto(uri: Uri) {
        uploadAndSendPhotos(listOf(uri))
    }

    private fun uploadAndSendSticker(file: File) {
        val cId = currentCouple?.id ?: currentUser?.coupleId
        if (cId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Couple not connected", Toast.LENGTH_SHORT).show()
            return
        }

        val senderName = currentUser?.name?.ifBlank { "Me" } ?: "Me"

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { file.readBytes() }
                val filename = "sticker_${UUID.randomUUID()}.webp"
                val uploadedUrl = repository.uploadImageBytes(bytes, filename)
                if (!uploadedUrl.isNullOrBlank()) {
                    repository.sendChatMessage(
                        coupleId = cId,
                        text = "",
                        imageUrl = uploadedUrl,
                        senderName = senderName,
                        isSticker = true
                    )
                } else {
                    Toast.makeText(requireContext(), "Failed to send sticker", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ChatFragment", "Error sending sticker", e)
                Toast.makeText(requireContext(), "Failed to send sticker", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun uploadAndSendPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val coupleId = currentCouple?.id ?: return
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val replyId = replyingToMessage?.id
        val replyText = replyingToMessage?.let {
            if (it.text.isNotBlank()) it.text
            else if (!it.imageUrl.isNullOrBlank()) "📷 Photo"
            else if (!it.audioUrl.isNullOrBlank()) "🎙️ Voice note"
            else "Message"
        }
        val replySenderName = replyingToMessage?.let {
            if (it.senderId == currentUid) "You" else it.senderName.ifBlank { "My Love" }
        }
        val replyImageUrl = replyingToMessage?.imageUrl
        clearReplyMode()

        val captionText = etInput.text?.toString()?.trim() ?: ""
        if (captionText.isNotEmpty()) {
            etInput.text?.clear()
        }

        val totalCount = uris.size
        if (totalCount == 1) {
            Toast.makeText(requireContext(), "Uploading photo...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Sending $totalCount photos...", Toast.LENGTH_SHORT).show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val baseTimestamp = System.currentTimeMillis()
            var successCount = 0

            // Upload photos concurrently for fast throughput while preserving index
            val uploadJobs = uris.mapIndexed { index, uri ->
                async(Dispatchers.IO) {
                    val url = repository.uploadImage(requireContext(), uri)
                    Pair(index, url)
                }
            }
            val results = uploadJobs.awaitAll().sortedBy { it.first }

            for ((index, uploadedUrl) in results) {
                if (!uploadedUrl.isNullOrBlank()) {
                    val isFirst = (index == 0)
                    val textToSend = if (isFirst) captionText else ""
                    val sent = repository.sendChatMessage(
                        coupleId = coupleId,
                        text = textToSend,
                        imageUrl = uploadedUrl,
                        senderName = mySenderName,
                        replyToId = if (isFirst) replyId else null,
                        replyToText = if (isFirst) replyText else null,
                        replyToSenderName = if (isFirst) replySenderName else null,
                        replyToImageUrl = if (isFirst) replyImageUrl else null,
                        timestamp = baseTimestamp + (index * 50L)
                    )
                    if (sent) successCount++
                }
            }

            if (successCount > 0) {
                triggerSendHaptic()
                if (totalCount > 1 && successCount < totalCount) {
                    Toast.makeText(requireContext(), "Sent $successCount of $totalCount photos", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Failed to upload photo(s)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun triggerSendHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val v = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(35)
                }
            }
        } catch (e: Exception) {
            // Haptic error ignored
        }
    }

    // ==========================================
    // CHAT & GOOGLE DRIVE SETTINGS BOTTOM SHEET
    // ==========================================
    private fun showChatSettingsBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_chat_settings, null)
        dialog.setContentView(sheetView)
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
        }
        settingsDialog = dialog

        val tvAccount = sheetView.findViewById<TextView>(R.id.tv_connected_google_account)
        val btnConnect = sheetView.findViewById<MaterialButton>(R.id.btn_connect_google_account)
        val tvBackupStatus = sheetView.findViewById<TextView>(R.id.tv_last_backup_status)
        val btnBackupNow = sheetView.findViewById<MaterialButton>(R.id.btn_backup_now)
        val btnRestore = sheetView.findViewById<MaterialButton>(R.id.btn_restore_backup)
        val ivSettingsAvatar = sheetView.findViewById<ImageView>(R.id.iv_chat_settings_avatar)
        val tvSettingsName = sheetView.findViewById<TextView>(R.id.tv_chat_settings_user_name)
        val btnChangeAvatar = sheetView.findViewById<MaterialButton>(R.id.btn_change_profile_photo)

        tvSettingsName?.text = currentUser?.name?.takeIf { it.isNotBlank() } ?: "Your Profile"
        if (!currentUser?.avatarUrl.isNullOrBlank()) {
            Glide.with(this)
                .load(currentUser!!.avatarUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_person_rounded)
                .into(ivSettingsAvatar)
            ivSettingsAvatar?.imageTintList = null
        }
        btnChangeAvatar?.setOnClickListener {
            pickProfileImageLauncher.launch("image/*")
        }

        val connectedEmail = driveHelper.getConnectedAccountEmail()
        if (!connectedEmail.isNullOrBlank()) {
            tvAccount.text = connectedEmail
            btnConnect.text = "Switch Google Account"
        } else {
            tvAccount.text = "No Google Account connected"
            btnConnect.text = "Connect Google Account"
        }

        tvBackupStatus.text = "Last backup: ${driveHelper.getLastBackupTime()}"

        btnConnect.setOnClickListener {
            val client = driveHelper.getGoogleSignInClient()
            googleSignInLauncher.launch(client.signInIntent)
        }

        btnBackupNow.setOnClickListener {
            startBackupFlow()
        }

        btnRestore.setOnClickListener {
            startRestoreFlow()
        }

        // Shared Wallpaper Setup
        val btnChangeWallpaper = sheetView.findViewById<MaterialButton>(R.id.btn_change_chat_wallpaper)
        val btnResetWallpaper = sheetView.findViewById<MaterialButton>(R.id.btn_reset_chat_wallpaper)
        updateSettingsWallpaperUi(currentCouple?.chatBackgroundUrl)

        btnChangeWallpaper?.setOnClickListener {
            pickWallpaperLauncher.launch("image/*")
        }

        btnResetWallpaper?.setOnClickListener {
            resetChatWallpaper()
        }

        val btnCheckUpdates = sheetView.findViewById<MaterialButton>(R.id.btn_check_updates)
        btnCheckUpdates?.setOnClickListener {
            (activity as? MainActivity)?.getAppUpdateHelper()?.checkForUpdates(manualCheck = true)
        }

        val btnReportChatIssue = sheetView.findViewById<MaterialButton>(R.id.btn_report_chat_issue)
        btnReportChatIssue?.setOnClickListener {
            dialog.dismiss()
            ErrorReporter.showReportSheet(
                requireActivity(),
                com.ourbloom.app.util.DetectedError(
                    title = "User Reported Issue",
                    message = "User requested support from Chat Settings.",
                    screenName = "ChatFragment"
                )
            )
        }

        dialog.show()
    }

    private fun updateSettingsAccountUi(email: String) {
        settingsDialog?.findViewById<TextView>(R.id.tv_connected_google_account)?.text = email
        settingsDialog?.findViewById<MaterialButton>(R.id.btn_connect_google_account)?.text = "Switch Google Account"
    }

    private fun updateSettingsBackupUi() {
        settingsDialog?.findViewById<TextView>(R.id.tv_last_backup_status)?.text = 
            "Last backup: ${driveHelper.getLastBackupTime()}"
    }

    private fun updateSettingsWallpaperUi(wallpaperUrl: String?) {
        val dialog = settingsDialog ?: return
        val ivPreview = dialog.findViewById<ImageView>(R.id.iv_chat_settings_wallpaper_preview)
        val tvStatus = dialog.findViewById<TextView>(R.id.tv_chat_settings_wallpaper_status)
        val btnReset = dialog.findViewById<MaterialButton>(R.id.btn_reset_chat_wallpaper)

        if (!wallpaperUrl.isNullOrBlank()) {
            if (ivPreview != null) {
                Glide.with(this)
                    .load(wallpaperUrl)
                    .centerCrop()
                    .into(ivPreview)
                ivPreview.imageTintList = null
                ivPreview.setPadding(0, 0, 0, 0)
            }
            tvStatus?.text = "Custom wallpaper synced for both of you 💕"
            btnReset?.visibility = View.VISIBLE
        } else {
            if (ivPreview != null) {
                Glide.with(this).clear(ivPreview)
                ivPreview.setImageResource(R.drawable.ic_photo_library_rounded)
                ivPreview.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.chat_action_pink)
                val pad = (10 * resources.displayMetrics.density).toInt()
                ivPreview.setPadding(pad, pad, pad, pad)
            }
            tvStatus?.text = "Default romantic theme"
            btnReset?.visibility = View.GONE
        }
    }

    private fun startBackupFlow() {
        val coupleId = currentCouple?.id ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Toast.makeText(requireContext(), "Preparing chat backup...", Toast.LENGTH_SHORT).show()
                val json = repository.exportChatBackupJson(coupleId)
                pendingBackupJson = json
                val fileName = driveHelper.createBackupFileName()
                createBackupLauncher.launch(driveHelper.createSaveDocumentIntent(fileName))
            } catch (e: Exception) {
                val err = e.message ?: "Backup failed"
                Toast.makeText(requireContext(), "Backup error: $err", Toast.LENGTH_SHORT).show()
                ErrorReporter.notifyError("Chat Backup Failed", err, e, "ChatFragment")
            }
        }
    }

    private fun startRestoreFlow() {
        openBackupLauncher.launch(driveHelper.createOpenDocumentIntent())
    }

    private fun restoreBackup(jsonContent: String) {
        val coupleId = currentCouple?.id ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Toast.makeText(requireContext(), "Restoring chats...", Toast.LENGTH_SHORT).show()
                val count = repository.importChatBackupJson(coupleId, jsonContent)
                Toast.makeText(requireContext(), "Successfully restored $count messages! 🎉", Toast.LENGTH_LONG).show()
                settingsDialog?.dismiss()
            } catch (e: Exception) {
                val err = e.message ?: "Restore failed"
                Toast.makeText(requireContext(), "Restore error: $err", Toast.LENGTH_SHORT).show()
                ErrorReporter.notifyError("Chat Restore Failed", err, e, "ChatFragment")
            }
        }
    }

    private fun startPresenceHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                val cId = currentCouple?.id
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (!cId.isNullOrBlank() && !uid.isNullOrBlank() && !isCurrentlyTyping && !isRecordingAudio) {
                    repository.setUserPresence(cId, uid, "online", System.currentTimeMillis())
                }
                delay(25000L)
            }
        }
    }

    private fun showMessageInfoBottomSheet(message: ChatMessage) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_message_info, null)
        dialog.setContentView(sheetView)
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
        }

        val ivPreviewImage = sheetView.findViewById<ImageView>(R.id.iv_info_preview_image)
        val tvPreviewText = sheetView.findViewById<TextView>(R.id.tv_info_preview_text)
        val tvReadTime = sheetView.findViewById<TextView>(R.id.tv_info_read_time)
        val tvDeliveredTime = sheetView.findViewById<TextView>(R.id.tv_info_delivered_time)
        val tvSentTime = sheetView.findViewById<TextView>(R.id.tv_info_sent_time)
        val ivReadTick = sheetView.findViewById<ImageView>(R.id.iv_info_read_tick)
        val ivDeliveredTick = sheetView.findViewById<ImageView>(R.id.iv_info_delivered_tick)

        if (!message.imageUrl.isNullOrBlank()) {
            ivPreviewImage.visibility = View.VISIBLE
            Glide.with(this)
                .load(message.imageUrl)
                .centerCrop()
                .into(ivPreviewImage)
        } else {
            ivPreviewImage.visibility = View.GONE
        }

        tvPreviewText.text = when {
            message.text.isNotBlank() -> message.text
            !message.audioUrl.isNullOrBlank() -> "🎙️ Voice note"
            !message.imageUrl.isNullOrBlank() -> "📷 Photo"
            else -> "Message"
        }

        val fullDateFormat = SimpleDateFormat("d MMMM yyyy, h:mm a", Locale.getDefault())
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        fun formatTimestamp(ts: Long?): String {
            if (ts == null || ts <= 0L) return ""
            val now = System.currentTimeMillis()
            val calNow = Calendar.getInstance().apply { timeInMillis = now }
            val calTs = Calendar.getInstance().apply { timeInMillis = ts }
            val isToday = calNow.get(Calendar.YEAR) == calTs.get(Calendar.YEAR) &&
                    calNow.get(Calendar.DAY_OF_YEAR) == calTs.get(Calendar.DAY_OF_YEAR)
            return if (isToday) "Today, ${timeFormat.format(Date(ts))}" else fullDateFormat.format(Date(ts))
        }

        tvSentTime.text = formatTimestamp(message.timestamp)

        val hasDelivered = message.hasDelivered || (message.deliveredAt != null && message.deliveredAt > 0L)
        if (hasDelivered) {
            val delTimeStr = formatTimestamp(message.deliveredAt?.takeIf { it > 0L } ?: message.timestamp)
            tvDeliveredTime.text = delTimeStr
            ImageViewCompat.setImageTintList(ivDeliveredTick, ColorStateList.valueOf(0xFF9E9E9E.toInt()))
        } else {
            tvDeliveredTime.text = "Not delivered yet"
            ImageViewCompat.setImageTintList(ivDeliveredTick, ColorStateList.valueOf(0xFFBDBDBD.toInt()))
        }

        val hasRead = message.isSeen || (message.readAt != null && message.readAt > 0L)
        if (hasRead) {
            val readTimeStr = formatTimestamp(message.readAt?.takeIf { it > 0L } ?: (message.deliveredAt?.takeIf { it > 0L } ?: message.timestamp))
            tvReadTime.text = readTimeStr
            ImageViewCompat.setImageTintList(ivReadTick, ColorStateList.valueOf(0xFF34B7F1.toInt()))
        } else {
            tvReadTime.text = "Not read yet"
            ImageViewCompat.setImageTintList(ivReadTick, ColorStateList.valueOf(0xFFBDBDBD.toInt()))
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        isChatVisible = true
        startPresenceHeartbeat()
        try {
            MyFirebaseMessagingService.dismissChatNotifications(requireContext())
        } catch (_: Exception) {}
        val cId = currentCouple?.id
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (!cId.isNullOrBlank() && !uid.isNullOrBlank()) {
            if (messagesListener == null) {
                setupMessagesListener(cId)
            }
            viewLifecycleOwner.lifecycleScope.launch {
                repository.markMessagesAsRead(cId, uid)
                try {
                    MyFirebaseMessagingService.dismissChatNotifications(requireContext())
                } catch (_: Exception) {}
            }
        }
    }

    override fun onPause() {
        super.onPause()
        isChatVisible = false
        messagesListener?.remove()
        messagesListener = null
        heartbeatJob?.cancel()
        val cId = currentCouple?.id
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (!cId.isNullOrBlank() && !uid.isNullOrBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                repository.setUserPresence(cId, uid, "offline", System.currentTimeMillis())
            }
        }
        if (isCurrentlyTyping) {
            isCurrentlyTyping = false
            typingHandler.removeCallbacks(stopTypingRunnable)
        }
        if (isRecordingAudio) {
            stopVoiceRecording(send = false)
        }
        chatAdapter.releaseAudioPlayer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isChatVisible = false
        heartbeatJob?.cancel()
        val cId = currentCouple?.id
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (!cId.isNullOrBlank() && !uid.isNullOrBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                repository.setUserPresence(cId, uid, "offline", System.currentTimeMillis())
            }
        }
        chatAdapter.releaseAudioPlayer()
        recordingTimerRunnable?.let { recordingHandler.removeCallbacks(it) }
        typingHandler.removeCallbacks(stopTypingRunnable)
        messagesListener?.remove()
        messagesListener = null
        typingListener?.remove()
        typingListener = null
        coupleListener?.remove()
        coupleListener = null
    }

    companion object {
        var isChatVisible: Boolean = false
    }
}
