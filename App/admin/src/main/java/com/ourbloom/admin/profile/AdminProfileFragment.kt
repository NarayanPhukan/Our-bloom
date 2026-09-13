package com.ourbloom.admin.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.ourbloom.admin.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AdminProfileFragment : Fragment() {

    private lateinit var tvAvatar: TextView
    private lateinit var tvHeaderName: TextView
    private lateinit var tvHeaderSub: TextView

    private lateinit var etName: EditText
    private lateinit var etMobile: EditText
    private lateinit var etEmail: EditText
    private lateinit var btnSaveDetails: Button

    private lateinit var etCurrentPass: TextInputEditText
    private lateinit var etNewPass: TextInputEditText
    private lateinit var etConfirmPass: TextInputEditText
    private lateinit var btnChangePassword: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_admin_profile, container, false)
        initViews(root)
        setupObservers()
        return root
    }

    private fun initViews(root: View) {
        tvAvatar = root.findViewById(R.id.tv_profile_avatar)
        tvHeaderName = root.findViewById(R.id.tv_profile_header_name)
        tvHeaderSub = root.findViewById(R.id.tv_profile_header_sub)

        etName = root.findViewById(R.id.et_profile_name)
        etMobile = root.findViewById(R.id.et_profile_mobile)
        etEmail = root.findViewById(R.id.et_profile_email)
        btnSaveDetails = root.findViewById(R.id.btn_save_profile_details)

        etCurrentPass = root.findViewById(R.id.et_current_password)
        etNewPass = root.findViewById(R.id.et_new_password)
        etConfirmPass = root.findViewById(R.id.et_confirm_password)
        btnChangePassword = root.findViewById(R.id.btn_change_password)

        // Save Admin Details
        btnSaveDetails.setOnClickListener {
            val name = etName.text.toString()
            val mobile = etMobile.text.toString()
            val email = etEmail.text.toString()

            val result = AdminProfileRepository.updateProfile(
                name = name,
                mobile = mobile,
                email = email,
                isBiometric = true
            )
            Toast.makeText(requireContext(), result.second, Toast.LENGTH_SHORT).show()
        }

        // Change Password
        btnChangePassword.setOnClickListener {
            val currentPass = etCurrentPass.text.toString()
            val newPass = etNewPass.text.toString()
            val confirmPass = etConfirmPass.text.toString()

            if (currentPass.isBlank() || newPass.isBlank() || confirmPass.isBlank()) {
                Toast.makeText(requireContext(), "Please fill in all password fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val result = AdminProfileRepository.updatePassword(
                currentPassInput = currentPass,
                newPassInput = newPass,
                confirmPassInput = confirmPass
            )

            Toast.makeText(requireContext(), result.second, Toast.LENGTH_LONG).show()

            if (result.first) {
                etCurrentPass.text?.clear()
                etNewPass.text?.clear()
                etConfirmPass.text?.clear()
            }
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            AdminProfileRepository.profileState.collectLatest { profile ->
                if (!isAdded) return@collectLatest

                val initial = if (profile.adminName.isNotBlank()) profile.adminName.first().uppercase() else "A"
                tvAvatar.text = initial
                tvHeaderName.text = profile.adminName
                tvHeaderSub.text = "+91 ${profile.mobileNumber} • ${profile.email}"

                // Fill text fields if not currently focused
                if (!etName.isFocused && etName.text.toString() != profile.adminName) {
                    etName.setText(profile.adminName)
                }
                if (!etMobile.isFocused && etMobile.text.toString() != profile.mobileNumber) {
                    etMobile.setText(profile.mobileNumber)
                }
                if (!etEmail.isFocused && etEmail.text.toString() != profile.email) {
                    etEmail.setText(profile.email)
                }
            }
        }
    }
}
