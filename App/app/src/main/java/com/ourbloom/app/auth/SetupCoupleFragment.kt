package com.ourbloom.app.auth

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.ourbloom.app.R
import com.ourbloom.app.data.FirestoreRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SetupCoupleFragment : Fragment() {

    private lateinit var repository: FirestoreRepository

    private lateinit var toggleMode: MaterialButtonToggleGroup
    private lateinit var layoutCreateForm: LinearLayout
    private lateinit var layoutJoinForm: LinearLayout
    private lateinit var layoutCelebration: LinearLayout
    private lateinit var pbLoading: ProgressBar

    private lateinit var etStartDate: EditText
    private lateinit var etStartTime: EditText
    private lateinit var etSpecialPhrase: EditText
    private lateinit var btnCreateGarden: MaterialButton

    private lateinit var etInviteCode: EditText
    private lateinit var btnJoinGarden: MaterialButton

    private lateinit var tvCelebrationCode: TextView
    private lateinit var btnShareCode: MaterialButton
    private lateinit var btnEnterGarden: MaterialButton

    private var selectedCalendar: Calendar = Calendar.getInstance()
    private var generatedInviteCode: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_setup_couple, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = FirestoreRepository()

        toggleMode = view.findViewById(R.id.toggle_setup_mode)
        layoutCreateForm = view.findViewById(R.id.layout_create_form)
        layoutJoinForm = view.findViewById(R.id.layout_join_form)
        layoutCelebration = view.findViewById(R.id.layout_celebration)
        pbLoading = view.findViewById(R.id.pb_setup_loading)

        etStartDate = view.findViewById(R.id.et_start_date)
        etStartTime = view.findViewById(R.id.et_start_time)
        etSpecialPhrase = view.findViewById(R.id.et_special_phrase)
        btnCreateGarden = view.findViewById(R.id.btn_create_garden)

        etInviteCode = view.findViewById(R.id.et_invite_code)
        btnJoinGarden = view.findViewById(R.id.btn_join_garden)

        tvCelebrationCode = view.findViewById(R.id.tv_celebration_code)
        btnShareCode = view.findViewById(R.id.btn_share_code)
        btnEnterGarden = view.findViewById(R.id.btn_enter_garden)

        // Set default date to today
        val defaultDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        etStartDate.setText(defaultDateFormat.format(selectedCalendar.time))

        // Date Picker
        etStartDate.setOnClickListener {
            val datePicker = DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    selectedCalendar.set(Calendar.YEAR, year)
                    selectedCalendar.set(Calendar.MONTH, month)
                    selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    etStartDate.setText(defaultDateFormat.format(selectedCalendar.time))
                },
                selectedCalendar.get(Calendar.YEAR),
                selectedCalendar.get(Calendar.MONTH),
                selectedCalendar.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.show()
        }

        // Time Picker
        etStartTime.setOnClickListener {
            val timePicker = TimePickerDialog(
                requireContext(),
                { _, hourOfDay, minute ->
                    selectedCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    selectedCalendar.set(Calendar.MINUTE, minute)
                    etStartTime.setText(String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute))
                },
                selectedCalendar.get(Calendar.HOUR_OF_DAY),
                selectedCalendar.get(Calendar.MINUTE),
                true
            )
            timePicker.show()
        }

        // Mode Switching
        toggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btn_mode_create) {
                    layoutCreateForm.visibility = View.VISIBLE
                    layoutJoinForm.visibility = View.GONE
                } else {
                    layoutCreateForm.visibility = View.GONE
                    layoutJoinForm.visibility = View.VISIBLE
                }
            }
        }

        // Create Garden Click
        btnCreateGarden.setOnClickListener {
            val dateStr = etStartDate.text.toString().trim()
            val timeStr = etStartTime.text.toString().trim().ifEmpty { "00:00" }
            val phrase = etSpecialPhrase.text.toString().trim()

            if (dateStr.isEmpty()) {
                Toast.makeText(requireContext(), "Please select an anniversary date.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            setLoading(true)
            viewLifecycleOwner.lifecycleScope.launch {
                val result = repository.createCoupleViaServer(dateStr, timeStr, phrase)
                setLoading(false)
                if (result.success && !result.inviteCode.isNullOrBlank()) {
                    generatedInviteCode = result.inviteCode
                    showCelebration(result.inviteCode)
                } else {
                    Toast.makeText(requireContext(), result.error ?: "Failed to create garden", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Join Garden Click
        btnJoinGarden.setOnClickListener {
            val code = etInviteCode.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter an invite code.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            setLoading(true)
            viewLifecycleOwner.lifecycleScope.launch {
                val result = repository.joinCoupleViaServer(code)
                setLoading(false)
                if (result.success) {
                    Toast.makeText(requireContext(), "Connected to partner! Welcome to your garden 🌸", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(R.id.action_setupCoupleFragment_to_dashboardFragment)
                } else {
                    Toast.makeText(requireContext(), result.error ?: "Invalid invite code", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Share & Copy Code Click
        val copyAndShare = {
            if (generatedInviteCode.isNotEmpty()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("OurBloom Invite Code", generatedInviteCode)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Invite code copied to clipboard! 📋", Toast.LENGTH_SHORT).show()

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Join my private garden on Our Bloom! 🌸\nDownload the app and enter my invite code: $generatedInviteCode"
                    )
                }
                startActivity(Intent.createChooser(shareIntent, "Share Garden Invite Code"))
            }
        }

        btnShareCode.setOnClickListener { copyAndShare() }
        tvCelebrationCode.setOnClickListener { copyAndShare() }

        // Enter Garden Click
        btnEnterGarden.setOnClickListener {
            findNavController().navigate(R.id.action_setupCoupleFragment_to_dashboardFragment)
        }
    }

    private fun showCelebration(code: String) {
        toggleMode.visibility = View.GONE
        layoutCreateForm.visibility = View.GONE
        layoutJoinForm.visibility = View.GONE
        layoutCelebration.visibility = View.VISIBLE
        tvCelebrationCode.text = code
    }

    private fun setLoading(loading: Boolean) {
        pbLoading.visibility = if (loading) View.VISIBLE else View.GONE
        btnCreateGarden.isEnabled = !loading
        btnJoinGarden.isEnabled = !loading
    }
}
