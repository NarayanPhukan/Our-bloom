package com.ourbloom.admin.config

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.firebase.firestore.ListenerRegistration
import com.ourbloom.admin.R
import com.ourbloom.admin.data.AdminFirestoreRepository
import com.ourbloom.admin.data.models.AppControlConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppControlDialog(
    context: Context,
    private val repository: AdminFirestoreRepository,
    private val coroutineScope: CoroutineScope
) : Dialog(context) {

    private var currentConfig = AppControlConfig()
    private var configListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_app_control)

        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        initViews()
        observeConfig()
    }

    private fun initViews() {
        findViewById<ImageButton>(R.id.btn_close_app_control)?.setOnClickListener { dismiss() }
        findViewById<MaterialButton>(R.id.btn_cancel_app_control)?.setOnClickListener { dismiss() }

        findViewById<MaterialButton>(R.id.btn_save_app_control)?.setOnClickListener {
            saveConfig()
        }
    }

    private fun observeConfig() {
        configListener = repository.observeAppControlConfig { config ->
            currentConfig = config
            populateFields(config)
        }
    }

    private fun populateFields(config: AppControlConfig) {
        findViewById<MaterialSwitch>(R.id.sw_maintenance_mode)?.isChecked = config.maintenanceMode
        findViewById<EditText>(R.id.et_maintenance_msg)?.setText(config.maintenanceMessage)
        findViewById<MaterialSwitch>(R.id.sw_deposits_enabled)?.isChecked = config.depositsEnabled
        findViewById<MaterialSwitch>(R.id.sw_withdrawals_enabled)?.isChecked = config.withdrawalsEnabled
        findViewById<EditText>(R.id.et_min_version)?.setText(config.minSupportedVersion.toString())
        findViewById<EditText>(R.id.et_force_update_url)?.setText(config.forceUpdateUrl)
    }

    private fun saveConfig() {
        val swMaintenance = findViewById<MaterialSwitch>(R.id.sw_maintenance_mode)?.isChecked ?: false
        val etMsg = findViewById<EditText>(R.id.et_maintenance_msg)?.text?.toString()?.trim() ?: ""
        val swDeposits = findViewById<MaterialSwitch>(R.id.sw_deposits_enabled)?.isChecked ?: true
        val swWithdrawals = findViewById<MaterialSwitch>(R.id.sw_withdrawals_enabled)?.isChecked ?: true
        val minVersionStr = findViewById<EditText>(R.id.et_min_version)?.text?.toString()?.trim() ?: "1"
        val minVersion = minVersionStr.toIntOrNull() ?: 1
        val forceUrl = findViewById<EditText>(R.id.et_force_update_url)?.text?.toString()?.trim() ?: ""

        val updated = currentConfig.copy(
            maintenanceMode = swMaintenance,
            maintenanceMessage = etMsg.ifEmpty { "OurBloom is undergoing scheduled maintenance." },
            depositsEnabled = swDeposits,
            withdrawalsEnabled = swWithdrawals,
            minSupportedVersion = minVersion,
            forceUpdateUrl = forceUrl,
            updatedBy = "admin"
        )

        AlertDialog.Builder(context)
            .setTitle("Publish Configuration")
            .setMessage("Are you sure you want to publish these remote settings? They will immediately affect all user mobile apps.")
            .setPositiveButton("Publish Now") { _, _ ->
                coroutineScope.launch {
                    val success = repository.updateAppControlConfig(updated)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            Toast.makeText(context, "Remote configuration published successfully!", Toast.LENGTH_SHORT).show()
                            dismiss()
                        } else {
                            Toast.makeText(context, "Failed to publish configuration", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        configListener?.remove()
    }
}
