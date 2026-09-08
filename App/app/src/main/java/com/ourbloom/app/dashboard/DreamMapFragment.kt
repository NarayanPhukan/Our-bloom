package com.ourbloom.app.dashboard

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.ourbloom.app.R
import com.ourbloom.app.data.FirestoreRepository
import com.ourbloom.app.data.models.DreamLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class DreamMapFragment : Fragment() {

    private val repository = FirestoreRepository()
    private var coupleId: String = ""
    private var currentLocations: List<DreamLocation> = emptyList()

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvDreamsCount: TextView
    private var isMapReady = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dream_map, container, false)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnBack = view.findViewById<ImageButton>(R.id.btn_back)
        val btnAdd = view.findViewById<View>(R.id.btn_add_location)
        val cardListToggle = view.findViewById<View>(R.id.card_list_toggle)
        tvDreamsCount = view.findViewById(R.id.tv_dreams_count)
        progressBar = view.findViewById(R.id.progress_map)
        webView = view.findViewById(R.id.web_view_map)

        btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        btnAdd.setOnClickListener {
            showAddLocationDialog(20.0, 10.0)
        }

        cardListToggle.setOnClickListener {
            showLocationsListDialog()
        }

        setupWebView()

        lifecycleScope.launch {
            val user = repository.getCurrentUser()
            coupleId = user?.coupleId ?: ""
            if (coupleId.isNotBlank()) {
                loadLocations()
            } else {
                Toast.makeText(requireContext(), "No couple profile found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
            }
        }

        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onMapReady() {
                activity?.runOnUiThread {
                    isMapReady = true
                    if (currentLocations.isNotEmpty()) {
                        sendLocationsToMap(currentLocations)
                    }
                }
            }

            @JavascriptInterface
            fun onMapClicked(lat: Double, lng: Double) {
                activity?.runOnUiThread {
                    showAddLocationDialog(lat, lng)
                }
            }

            @JavascriptInterface
            fun onMarkerClicked(locationId: String) {
                activity?.runOnUiThread {
                    val loc = currentLocations.find { it.id == locationId }
                    if (loc != null) {
                        showLocationDetailsDialog(loc)
                    }
                }
            }
        }, "AndroidBridge")

        webView.loadUrl("file:///android_asset/dream_map.html")
    }

    private fun loadLocations() {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            val locations = repository.getDreamLocations(coupleId)
            currentLocations = locations
            progressBar.visibility = View.GONE

            val count = locations.size
            tvDreamsCount.text = if (count == 0) "No pinned dreams yet" else "$count Pinned Dreams ✨"

            if (isMapReady) {
                sendLocationsToMap(locations)
            }
        }
    }

    private fun sendLocationsToMap(locations: List<DreamLocation>) {
        val jsonArray = JSONArray()
        for (loc in locations) {
            val obj = JSONObject()
            obj.put("id", loc.id)
            obj.put("title", loc.title)
            obj.put("description", loc.description)
            obj.put("lat", loc.lat)
            obj.put("lng", loc.lng)
            obj.put("status", loc.status)
            obj.put("photoUrl", loc.photoUrl)
            jsonArray.put(obj)
        }

        val jsonStr = jsonArray.toString().replace("'", "\\'")
        val script = "setLocations('$jsonStr');"
        webView.evaluateJavascript(script, null)
    }

    private fun showAddLocationDialog(lat: Double, lng: Double) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_dream_location, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etTitle = dialogView.findViewById<TextInputEditText>(R.id.et_location_title)
        val etDesc = dialogView.findViewById<TextInputEditText>(R.id.et_location_desc)
        val chipGroup = dialogView.findViewById<ChipGroup>(R.id.chip_group_status)
        val tvCoords = dialogView.findViewById<TextView>(R.id.tv_coordinates_hint)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel_location)
        val btnSave = dialogView.findViewById<Button>(R.id.btn_save_location)

        tvCoords.text = String.format("Coordinates: %.4f, %.4f", lat, lng)

        btnCancel.setOnClickListener {
            dialog.dismiss()
            webView.evaluateJavascript("removeDraftMarker();", null)
        }

        btnSave.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val desc = etDesc.text.toString().trim()

            if (title.isBlank()) {
                etTitle.error = "Please enter a destination name"
                return@setOnClickListener
            }

            val selectedChipId = chipGroup.checkedChipId
            val status = when (selectedChipId) {
                R.id.chip_planning -> "Planning"
                R.id.chip_booked -> "Booked"
                R.id.chip_visited -> "Visited"
                else -> "Dreaming"
            }

            val newLoc = DreamLocation(
                coupleId = coupleId,
                title = title,
                description = desc,
                lat = lat,
                lng = lng,
                status = status
            )

            btnSave.isEnabled = false
            lifecycleScope.launch {
                val success = repository.createDreamLocation(newLoc)
                if (success) {
                    Toast.makeText(requireContext(), "Dream location pinned! ✨", Toast.LENGTH_SHORT).show()
                    loadLocations()
                } else {
                    Toast.makeText(requireContext(), "Failed to save location", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showLocationDetailsDialog(location: DreamLocation) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("${location.title} (${location.status})")
        builder.setMessage(location.description.ifBlank { "No description added." })

        builder.setPositiveButton("Fly To ✈️") { dialog, _ ->
            webView.evaluateJavascript("flyToLocation(${location.lat}, ${location.lng}, 9);", null)
            dialog.dismiss()
        }

        builder.setNegativeButton("Delete") { dialog, _ ->
            AlertDialog.Builder(requireContext())
                .setTitle("Delete Location")
                .setMessage("Remove '${location.title}' from your dream map?")
                .setPositiveButton("Delete") { _, _ ->
                    lifecycleScope.launch {
                        val success = repository.deleteDreamLocation(location.id)
                        if (success) {
                            Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
                            loadLocations()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
            dialog.dismiss()
        }

        builder.setNeutralButton("Close", null)
        builder.show()
    }

    private fun showLocationsListDialog() {
        if (currentLocations.isEmpty()) {
            Toast.makeText(requireContext(), "No dream locations pinned yet! Tap the map to add one.", Toast.LENGTH_SHORT).show()
            return
        }

        val titles = currentLocations.map { "${it.title} [${it.status}]" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Our Dream Destinations ✈️")
            .setItems(titles) { _, which ->
                val loc = currentLocations[which]
                webView.evaluateJavascript("flyToLocation(${loc.lat}, ${loc.lng}, 9);", null)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webView.destroy()
    }
}
