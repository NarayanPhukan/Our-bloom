package com.ourbloom.app.chat

import android.app.Activity
import android.content.Context
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
import com.ourbloom.app.data.FirestoreRepository
import com.ourbloom.app.data.models.Couple
import com.ourbloom.app.data.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // Reply preview views
    private lateinit var layoutReplyPreview: View
    private lateinit var tvReplyPreviewName: TextView
    private lateinit var tvReplyPreviewText: TextView
    private lateinit var btnCancelReply: ImageButton

    // Selection & Reply State
    private var selectedMessage: ChatMessage? = null
    private var replyingToMessage: ChatMessage? = null

    private var messagesListener: ListenerRegistration? = null
    private var typingListener: ListenerRegistration? = null
    private var currentCouple: Couple? = null
    private var currentUser: User? = null
    private var partnerUser: User? = null
    private var mySenderName: String = "My Love"

    private var pendingBackupJson: String? = null
    private var settingsDialog: BottomSheetDialog? = null

    // Typing debounce handler
    private val typingHandler = Handler(Looper.getMainLooper())
    private var isCurrentlyTyping = false
    private val stopTypingRunnable = Runnable {
        if (isCurrentlyTyping) {
            isCurrentlyTyping = false
            val cId = currentCouple?.id ?: return@Runnable
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@Runnable
            viewLifecycleOwner.lifecycleScope.launch {
                repository.setTypingStatus(cId, uid, "idle")
            }
        }
    }

    // Voice recording
    private var mediaRecorder: MediaRecorder? = null
    private var audioRecordingFile: File? = null
    private var isRecordingAudio = false
    private var recordingStartTime = 0L

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(requireContext(), "Microphone ready! Hold mic to record.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Microphone permission needed to record audio.", Toast.LENGTH_SHORT).show()
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

    // Photo attachment launcher
    private val attachPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            uploadAndSendPhoto(uri)
        }
    }

    // Direct Camera launcher
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
        clearReplyMode()

        Toast.makeText(requireContext(), "Uploading photo...", Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
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
                    replyToSenderName = replySenderName
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

        // Action Bar & Reply Preview Views
        layoutActionBar = view.findViewById(R.id.layout_chat_action_bar)
        chatHeader = view.findViewById(R.id.chat_header)
        btnActionClose = view.findViewById(R.id.btn_action_close)
        tvActionCount = view.findViewById(R.id.tv_action_count)
        btnActionReply = view.findViewById(R.id.btn_action_reply)
        btnActionCopy = view.findViewById(R.id.btn_action_copy)
        btnActionDelete = view.findViewById(R.id.btn_action_delete)

        layoutReplyPreview = view.findViewById(R.id.layout_reply_preview)
        tvReplyPreviewName = view.findViewById(R.id.tv_reply_preview_name)
        tvReplyPreviewText = view.findViewById(R.id.tv_reply_preview_text)
        btnCancelReply = view.findViewById(R.id.btn_cancel_reply)

        val btnEmoji = view.findViewById<ImageButton>(R.id.btn_chat_emoji)
        val btnCamera = view.findViewById<ImageButton>(R.id.btn_chat_camera)

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        chatAdapter = ChatAdapter(currentUid) { imageUrl ->
            // Photo clicked - preview
            Toast.makeText(requireContext(), "Viewing photo", Toast.LENGTH_SHORT).show()
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
                val density = resources.displayMetrics.density
                val maxSwipe = 85f * density
                val triggerThreshold = 46f * density

                // Smooth elastic translation
                val translationX = if (dX > 0) {
                    (dX * 0.6f).coerceAtMost(maxSwipe)
                } else 0f

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
                }

                // Draw WhatsApp reply indicator icon behind the sliding message
                if (translationX > 4f && replyIcon != null) {
                    val circleRadius = 18f * density
                    val iconSize = (20f * density).toInt()
                    val marginStart = 16f * density
                    val centerY = itemView.top + (itemView.height / 2f)
                    val circleCenterX = itemView.left + marginStart + circleRadius

                    val progress = (translationX / triggerThreshold).coerceIn(0f, 1f)

                    // Circular background
                    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (progress >= 1f) {
                            ContextCompat.getColor(requireContext(), R.color.chat_quote_accent)
                        } else {
                            ContextCompat.getColor(requireContext(), R.color.bloom_surface_variant)
                        }
                        alpha = (progress * 240).toInt()
                    }
                    c.drawCircle(circleCenterX, centerY, circleRadius * (0.6f + 0.4f * progress), circlePaint)

                    // Reply arrow icon inside circle
                    val iconLeft = (circleCenterX - iconSize / 2f).toInt()
                    val iconTop = (centerY - iconSize / 2f).toInt()
                    val iconRight = iconLeft + iconSize
                    val iconBottom = iconTop + iconSize

                    replyIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    replyIcon.setTint(if (progress >= 1f) Color.WHITE else ContextCompat.getColor(requireContext(), R.color.chat_header_subtitle))
                    replyIcon.alpha = (progress * 255).toInt()

                    c.save()
                    c.scale(0.7f + 0.3f * progress, 0.7f + 0.3f * progress, circleCenterX, centerY)
                    replyIcon.draw(c)
                    c.restore()
                }

                getDefaultUIUtil().onDraw(c, recyclerView, itemView, translationX, dY, actionState, isCurrentlyActive)
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                getDefaultUIUtil().clearView(viewHolder.itemView)

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
                !msg.imageUrl.isNullOrBlank() -> msg.imageUrl
                !msg.audioUrl.isNullOrBlank() -> msg.audioUrl
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
                                repository.setTypingStatus(cId, uid, "typing")
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
                                repository.setTypingStatus(cId, uid, "idle")
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

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        startVoiceRecording()
                    } else {
                        requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    stopVoiceRecording(send = true)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    stopVoiceRecording(send = false)
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
            takePhotoPreviewLauncher.launch(null)
        }

        btnEmoji.setOnClickListener {
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
                        setupTypingListener(cId, partnerId)
                    }
                }
            }
        }
    }

    private fun setupTypingListener(coupleId: String, partnerId: String) {
        typingListener?.remove()
        typingListener = repository.listenTypingStatus(coupleId, partnerId) { status ->
            when (status) {
                "typing" -> {
                    tvChatStatus.text = "typing..."
                    tvChatStatus.setTextColor(0xFF25D366.toInt()) // WhatsApp green
                }
                "recording" -> {
                    tvChatStatus.text = "🎙️ recording audio..."
                    tvChatStatus.setTextColor(0xFFE85D75.toInt()) // Rose accent
                }
                else -> {
                    tvChatStatus.text = "Forever blooming together 🌸"
                    tvChatStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.chat_header_subtitle))
                }
            }
        }
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

            // Real-time WhatsApp double blue ticks: mark partner messages as read & delivered
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            val unreadPartnerIds = messages.filter { 
                it.senderId.isNotBlank() && it.senderId != currentUid && (!it.isRead || !it.isDelivered) 
            }.map { it.id }
            if (unreadPartnerIds.isNotEmpty()) {
                viewLifecycleOwner.lifecycleScope.launch {
                    repository.markMessagesReadByIds(unreadPartnerIds)
                }
            }
        }
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
            triggerSendHaptic()

            // Broadcast recording status to partner
            viewLifecycleOwner.lifecycleScope.launch {
                repository.setTypingStatus(coupleId, uid, "recording")
            }
            Toast.makeText(requireContext(), "Recording voice note... 🎙️", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("ChatFragment", "Failed to start audio recording", e)
            isRecordingAudio = false
            try { mediaRecorder?.release() } catch (_: Exception) {}
            mediaRecorder = null
        }
    }

    private fun selectMessage(message: ChatMessage) {
        selectedMessage = message
        chatAdapter.setSelectedMessage(message.id)
        layoutActionBar.visibility = View.VISIBLE
        chatHeader.visibility = View.GONE
        tvActionCount.text = "1"
        triggerSendHaptic()
    }

    private fun clearSelection() {
        selectedMessage = null
        chatAdapter.setSelectedMessage(null)
        layoutActionBar.visibility = View.GONE
        chatHeader.visibility = View.VISIBLE
    }

    private fun enterReplyMode(message: ChatMessage) {
        replyingToMessage = message
        layoutReplyPreview.visibility = View.VISIBLE
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val senderLabel = if (message.senderId == currentUid) "You" else message.senderName.ifBlank { "My Love" }
        tvReplyPreviewName.text = "Replying to $senderLabel"
        tvReplyPreviewText.text = when {
            message.text.isNotBlank() -> message.text
            !message.imageUrl.isNullOrBlank() -> "📷 Photo"
            !message.audioUrl.isNullOrBlank() -> "🎙️ Voice note"
            else -> "Message"
        }
        etInput.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.showSoftInput(etInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        triggerSendHaptic()
    }

    private fun clearReplyMode() {
        replyingToMessage = null
        layoutReplyPreview.visibility = View.GONE
    }

    private fun stopVoiceRecording(send: Boolean) {
        if (!isRecordingAudio) return
        isRecordingAudio = false

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
            clearReplyMode()

            triggerSendHaptic()
            Toast.makeText(requireContext(), "Sending voice note...", Toast.LENGTH_SHORT).show()
            viewLifecycleOwner.lifecycleScope.launch {
                val uploadedUrl = repository.uploadAudioFile(file)
                file.delete()
                if (!uploadedUrl.isNullOrBlank()) {
                    repository.sendChatMessage(
                        coupleId = coupleId,
                        text = "🎙️ Voice note",
                        imageUrl = null,
                        audioUrl = uploadedUrl,
                        senderName = mySenderName,
                        replyToId = replyId,
                        replyToText = replyText,
                        replyToSenderName = replySenderName
                    )
                } else {
                    Toast.makeText(requireContext(), "Failed to send voice note. Check connection.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            file?.delete()
        }
        audioRecordingFile = null
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
        clearReplyMode()

        etInput.setText("")
        triggerSendHaptic()

        if (isCurrentlyTyping) {
            isCurrentlyTyping = false
            typingHandler.removeCallbacks(stopTypingRunnable)
            viewLifecycleOwner.lifecycleScope.launch {
                repository.setTypingStatus(coupleId, currentUid, "idle")
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
                replyToSenderName = replySenderName
            )
        }
    }

    private fun uploadAndSendPhoto(uri: Uri) {
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
        clearReplyMode()

        Toast.makeText(requireContext(), "Uploading photo...", Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            val uploadedUrl = repository.uploadImage(requireContext(), uri)
            if (!uploadedUrl.isNullOrBlank()) {
                repository.sendChatMessage(
                    coupleId = coupleId,
                    text = "",
                    imageUrl = uploadedUrl,
                    senderName = mySenderName,
                    replyToId = replyId,
                    replyToText = replyText,
                    replyToSenderName = replySenderName
                )
                triggerSendHaptic()
            } else {
                Toast.makeText(requireContext(), "Failed to upload photo", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(requireContext(), "Backup error: ${e.message}", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(requireContext(), "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isChatVisible = true
        val cId = currentCouple?.id
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (!cId.isNullOrBlank() && !uid.isNullOrBlank()) {
            viewLifecycleOwner.lifecycleScope.launch {
                repository.markMessagesAsRead(cId, uid)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        isChatVisible = false
        if (isCurrentlyTyping) {
            isCurrentlyTyping = false
            typingHandler.removeCallbacks(stopTypingRunnable)
            val cId = currentCouple?.id
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (cId != null && uid != null) {
                viewLifecycleOwner.lifecycleScope.launch {
                    repository.setTypingStatus(cId, uid, "idle")
                }
            }
        }
        if (isRecordingAudio) {
            stopVoiceRecording(send = false)
        }
        chatAdapter.releaseAudioPlayer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isChatVisible = false
        chatAdapter.releaseAudioPlayer()
        typingHandler.removeCallbacks(stopTypingRunnable)
        messagesListener?.remove()
        messagesListener = null
        typingListener?.remove()
        typingListener = null
    }

    companion object {
        var isChatVisible: Boolean = false
    }
}
