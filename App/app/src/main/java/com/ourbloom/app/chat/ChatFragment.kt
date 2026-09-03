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
    private lateinit var ivPartnerAvatar: ImageView
    private lateinit var layoutEmpty: View

    private var messagesListener: ListenerRegistration? = null
    private var currentCouple: Couple? = null
    private var currentUser: User? = null
    private var partnerUser: User? = null
    private var mySenderName: String = "My Love"

    private var pendingBackupJson: String? = null
    private var settingsDialog: BottomSheetDialog? = null

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
                    senderName = mySenderName
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
        ivPartnerAvatar = view.findViewById(R.id.iv_partner_avatar)
        layoutEmpty = view.findViewById(R.id.layout_chat_empty)

        val btnEmoji = view.findViewById<ImageButton>(R.id.btn_chat_emoji)
        val btnCamera = view.findViewById<ImageButton>(R.id.btn_chat_camera)

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        chatAdapter = ChatAdapter(currentUid) { imageUrl ->
            // Photo clicked - preview
            Toast.makeText(requireContext(), "Viewing photo", Toast.LENGTH_SHORT).show()
        }

        val layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        rvMessages.layoutManager = layoutManager
        rvMessages.adapter = chatAdapter

        // Dynamically toggle Mic / Send icon like WhatsApp
        etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrBlank()
                if (hasText) {
                    btnSend.setImageResource(R.drawable.ic_send_rounded)
                    btnSend.contentDescription = "Send Message"
                } else {
                    btnSend.setImageResource(R.drawable.ic_mic_whatsapp)
                    btnSend.contentDescription = "Voice Note"
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnSend.setOnClickListener {
            val text = etInput.text?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) {
                sendMessage()
            } else {
                Toast.makeText(requireContext(), "Hold to record voice note 🎙️", Toast.LENGTH_SHORT).show()
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
        }
    }

    private fun sendMessage() {
        val text = etInput.text.toString().trim()
        val coupleId = currentCouple?.id ?: return
        if (text.isBlank()) return

        etInput.setText("")
        triggerSendHaptic()

        viewLifecycleOwner.lifecycleScope.launch {
            repository.sendChatMessage(
                coupleId = coupleId,
                text = text,
                imageUrl = null,
                senderName = mySenderName
            )
        }
    }

    private fun uploadAndSendPhoto(uri: Uri) {
        val coupleId = currentCouple?.id ?: return
        Toast.makeText(requireContext(), "Uploading photo...", Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            val uploadedUrl = repository.uploadImage(requireContext(), uri)
            if (!uploadedUrl.isNullOrBlank()) {
                repository.sendChatMessage(
                    coupleId = coupleId,
                    text = "",
                    imageUrl = uploadedUrl,
                    senderName = mySenderName
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

    override fun onDestroyView() {
        super.onDestroyView()
        messagesListener?.remove()
        messagesListener = null
    }
}
