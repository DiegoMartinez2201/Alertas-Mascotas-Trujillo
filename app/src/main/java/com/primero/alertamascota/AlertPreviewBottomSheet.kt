package com.primero.alertamascota

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

class AlertPreviewBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "AlertPreviewBS"
        private const val ARG_ALERT_ID = "alert_id"

        fun newInstance(alertId: String): AlertPreviewBottomSheet {
            val fragment = AlertPreviewBottomSheet()
            val args = Bundle()
            args.putString(ARG_ALERT_ID, alertId)
            fragment.arguments = args
            return fragment
        }
    }

    private lateinit var ivAlertPhoto: ImageView
    private lateinit var tvNoPhoto: TextView
    private lateinit var tvAlertState: TextView
    private lateinit var tvAlertPetType: TextView
    private lateinit var tvAlertStatus: TextView
    private lateinit var tvAlertDescription: TextView
    private lateinit var tvAlertAddress: TextView
    private lateinit var tvReporterEmail: TextView
    private lateinit var tvReportDate: TextView
    private lateinit var btnViewFullDetails: Button
    private lateinit var btnClosePreview: ImageButton

    private val db = Firebase.firestore
    private var alertId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        alertId = arguments?.getString(ARG_ALERT_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_alert_preview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupBottomSheetBehavior(view)
        setupListeners()
        loadAlertData()
    }

    private fun initViews(view: View) {
        ivAlertPhoto = view.findViewById(R.id.ivAlertPhoto)
        tvNoPhoto = view.findViewById(R.id.tvNoPhoto)
        tvAlertState = view.findViewById(R.id.tvAlertState)
        tvAlertPetType = view.findViewById(R.id.tvAlertPetType)
        tvAlertStatus = view.findViewById(R.id.tvAlertStatus)
        tvAlertDescription = view.findViewById(R.id.tvAlertDescription)
        tvAlertAddress = view.findViewById(R.id.tvAlertAddress)
        tvReporterEmail = view.findViewById(R.id.tvReporterEmail)
        tvReportDate = view.findViewById(R.id.tvReportDate)
        btnViewFullDetails = view.findViewById(R.id.btnViewFullDetails)
        btnClosePreview = view.findViewById(R.id.btnClosePreview)
    }

    private fun setupBottomSheetBehavior(view: View) {
        view.post {
            val bottomSheet = view.findViewById<View>(R.id.bottomSheet)
            val behavior = BottomSheetBehavior.from(bottomSheet)

            // Obtener altura de la pantalla
            val displayMetrics = resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels

            // Configurar comportamiento
            behavior.isFitToContents = false
            behavior.expandedOffset = 0 // Permitir expandir hasta arriba

            // CONFIGURACIÓN CLAVE: Altura inicial más alta (80% de la pantalla)
            behavior.halfExpandedRatio = 0.80f

            // Altura mínima colapsada
            behavior.peekHeight = (screenHeight * 0.20).toInt()

            // Configurar estados
            behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
            behavior.isHideable = true
            behavior.skipCollapsed = false
            behavior.isDraggable = true

            Log.d("BottomSheet", "Screen height: $screenHeight, Half expanded at: ${screenHeight * 0.80}px")
        }
    }

    private fun setupListeners() {
        btnClosePreview.setOnClickListener {
            dismiss()
        }

        btnViewFullDetails.setOnClickListener {
            openFullDetail()
        }
    }

    private fun loadAlertData() {
        alertId?.let { id ->
            db.collection("alerts")
                .document(id)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val photoUrl = document.getString("photoUrl")
                        val state = document.getString("state") ?: "Desconocido"
                        val status = document.getString("status") ?: "active"
                        val description = document.getString("description") ?: "Sin descripción"
                        val address = document.getString("address") ?: "Dirección no disponible"
                        val ownerEmail = document.getString("ownerEmail") ?: "Desconocido"
                        val createdAt = document.getTimestamp("createdAt")
                        val petType = document.getString("petType") ?: "Desconocido"

                        // Cargar foto
                        if (!photoUrl.isNullOrEmpty()) {
                            Glide.with(this)
                                .load(photoUrl)
                                .placeholder(android.R.drawable.ic_menu_gallery)
                                .error(android.R.drawable.ic_menu_gallery)
                                .into(ivAlertPhoto)
                            tvNoPhoto.visibility = View.GONE
                        } else {
                            ivAlertPhoto.setImageResource(android.R.drawable.ic_menu_gallery)
                            tvNoPhoto.visibility = View.VISIBLE
                        }

                        // Mostrar información
                        tvAlertState.text = state
                        tvAlertPetType.text = petType
                        tvAlertDescription.text = description
                        tvAlertAddress.text = address
                        tvReporterEmail.text = ownerEmail

                        // Fecha
                        if (createdAt != null) {
                            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                            tvReportDate.text = sdf.format(createdAt.toDate())
                        } else {
                            tvReportDate.text = "Fecha desconocida"
                        }

                        // Estado de la alerta
                        when (status) {
                            "active" -> {
                                tvAlertStatus.text = "🐾 Activa"
                                tvAlertStatus.setTextColor(resources.getColor(android.R.color.white))
                                tvAlertStatus.setBackgroundColor(resources.getColor(android.R.color.holo_green_dark))
                            }
                            "saved" -> {
                                tvAlertStatus.text = "✅ Salvada"
                                tvAlertStatus.setTextColor(resources.getColor(android.R.color.white))
                                tvAlertStatus.setBackgroundColor(resources.getColor(android.R.color.holo_blue_dark))
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error cargando alerta: ${e.message}", e)
                    dismiss()
                }
        }
    }

    private fun openFullDetail() {
        val intent = Intent(requireContext(), AlertDetailActivity::class.java)
        intent.putExtra(AlertDetailActivity.EXTRA_ALERT_ID, alertId)
        startActivity(intent)
        dismiss()
    }

    override fun getTheme(): Int {
        return R.style.BottomSheetDialogTheme
    }
}