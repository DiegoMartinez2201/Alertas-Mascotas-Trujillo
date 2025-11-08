package com.primero.alertamascota

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.*

class CaseManagementActivity : AppCompatActivity() {

    private lateinit var ivOriginalPhoto: CircleImageView
    private lateinit var ivSavedPhoto: CircleImageView
    private lateinit var tvCode: TextView
    private lateinit var tvState: TextView
    private lateinit var tvAddress: TextView
    private lateinit var tvReportedBy: TextView
    private lateinit var tvResolvedBy: TextView
    private lateinit var tvNotes: TextView
    private lateinit var tvCreatedDate: TextView
    private lateinit var tvResolvedDate: TextView
    private lateinit var tvManagementPetType: TextView
    private lateinit var chipStatus: Chip
    private lateinit var btnContactReporter: Button
    private lateinit var btnContactResolver: Button
    private lateinit var btnViewOnMap: Button
    private lateinit var btnMarkAsReviewed: Button
    private lateinit var btnBack: ImageButton
    private lateinit var progressBar: ProgressBar

    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()

    private var caseId: String? = null
    private var caseCode: String? = null
    private var reporterEmail: String? = null
    private var resolverEmail: String? = null
    private var latitude: Double? = null
    private var longitude: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_case_management)

        caseId = intent.getStringExtra("CASE_ID")
        caseCode = intent.getStringExtra("CASE_CODE")

        if (caseId == null) {
            Toast.makeText(this, "Error: ID de caso inválido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        loadCaseDetails()
    }

    private fun initViews() {
        ivOriginalPhoto = findViewById(R.id.ivManagementOriginalPhoto)
        ivSavedPhoto = findViewById(R.id.ivManagementSavedPhoto)
        tvCode = findViewById(R.id.tvManagementCode)
        tvState = findViewById(R.id.tvManagementState)
        tvAddress = findViewById(R.id.tvManagementAddress)
        tvReportedBy = findViewById(R.id.tvManagementReportedBy)
        tvResolvedBy = findViewById(R.id.tvManagementResolvedBy)
        tvNotes = findViewById(R.id.tvManagementNotes)
        tvCreatedDate = findViewById(R.id.tvManagementCreatedDate)
        tvResolvedDate = findViewById(R.id.tvManagementResolvedDate)
        tvManagementPetType = findViewById(R.id.tvManagementPetType)
        chipStatus = findViewById(R.id.chipManagementStatus)
        btnContactReporter = findViewById(R.id.btnContactReporter)
        btnContactResolver = findViewById(R.id.btnContactResolver)
        btnViewOnMap = findViewById(R.id.btnViewOnMap)
        btnMarkAsReviewed = findViewById(R.id.btnMarkAsReviewed)
        btnBack = findViewById(R.id.btnManagementBack)
        progressBar = findViewById(R.id.progressBarManagement)

        btnBack.setOnClickListener { finish() }

        // ✅ CORREGIDO: Abrir chat con el reportante
        btnContactReporter.setOnClickListener { openChatWithReporter() }

        // ✅ CORREGIDO: Abrir chat con quien resolvió el caso
        btnContactResolver.setOnClickListener { openChatWithResolver() }

        btnViewOnMap.setOnClickListener { openInMaps() }
        btnMarkAsReviewed.setOnClickListener { markAsReviewed() }
    }

    private fun loadCaseDetails() {
        showLoading(true)

        caseId?.let { id ->
            db.collection("alerts")
                .document(id)
                .get()
                .addOnSuccessListener { document ->
                    showLoading(false)

                    if (!document.exists()) {
                        Toast.makeText(this, "Caso no encontrado", Toast.LENGTH_SHORT).show()
                        finish()
                        return@addOnSuccessListener
                    }

                    val code = "#${document.id.takeLast(7)}"
                    val status = document.getString("status") ?: "active"
                    val state = document.getString("state") ?: "Desconocido"
                    val petType = document.getString("petType") ?: "Desconocido"
                    val address = document.getString("address") ?: "Sin dirección"
                    val photoUrl = document.getString("photoUrl") ?: ""
                    val savedPhotoUrl = document.getString("savedPhotoUrl") ?: ""
                    val savedNotes = document.getString("savedNotes") ?: ""
                    val ownerEmail = document.getString("ownerEmail") ?: "Desconocido"
                    resolverEmail = document.getString("resolvedBy") ?: ""
                    val createdAt = document.getTimestamp("createdAt")
                    val resolvedAt = document.getTimestamp("resolvedAt")
                    latitude = document.getDouble("lat")
                    longitude = document.getDouble("lng")

                    // ✅ GUARDAR DATOS PARA EL CHAT
                    caseCode = code
                    reporterEmail = ownerEmail

                    // Mostrar información
                    tvCode.text = code
                    tvState.text = state
                    tvManagementPetType.text = petType
                    tvAddress.text = address
                    tvReportedBy.text = "📧 $ownerEmail"

                    if (status == "saved" && !resolverEmail.isNullOrEmpty()) {
                        tvResolvedBy.visibility = View.VISIBLE
                        tvResolvedBy.text = "👤 $resolverEmail"
                        btnContactResolver.visibility = View.VISIBLE
                        chipStatus.text = "✅ Resuelto"
                        chipStatus.setChipBackgroundColorResource(android.R.color.holo_green_light)

                        tvResolvedDate.visibility = View.VISIBLE
                        if (resolvedAt != null) {
                            tvResolvedDate.text = "Resuelto: ${formatDate(resolvedAt.toDate())}"
                        }
                    } else {
                        tvResolvedBy.visibility = View.GONE
                        btnContactResolver.visibility = View.GONE
                        chipStatus.text = "🔔 Activo"
                        chipStatus.setChipBackgroundColorResource(android.R.color.holo_orange_light)
                        tvResolvedDate.visibility = View.GONE
                    }

                    if (createdAt != null) {
                        tvCreatedDate.text = "Creado: ${formatDate(createdAt.toDate())}"
                    }

                    if (savedNotes.isNotEmpty()) {
                        tvNotes.visibility = View.VISIBLE
                        tvNotes.text = "📝 Notas: $savedNotes"
                    } else {
                        tvNotes.visibility = View.GONE
                    }

                    // Cargar fotos
                    if (photoUrl.isNotEmpty()) {
                        Glide.with(this)
                            .load(photoUrl)
                            .placeholder(R.drawable.ic_person_placeholder)
                            .into(ivOriginalPhoto)
                    }

                    if (savedPhotoUrl.isNotEmpty()) {
                        ivSavedPhoto.visibility = View.VISIBLE
                        Glide.with(this)
                            .load(savedPhotoUrl)
                            .placeholder(R.drawable.ic_person_placeholder)
                            .into(ivSavedPhoto)
                    } else {
                        ivSavedPhoto.visibility = View.GONE
                    }
                }
                .addOnFailureListener { e ->
                    showLoading(false)
                    Toast.makeText(this, "Error cargando caso: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
        }
    }

    // ✅ FUNCIÓN CORREGIDA: Abrir chat con el reportante
    private fun openChatWithReporter() {
        if (reporterEmail.isNullOrEmpty()) {
            Toast.makeText(this, "Email del reportante no disponible", Toast.LENGTH_SHORT).show()
            return
        }

        if (caseId.isNullOrEmpty() || caseCode.isNullOrEmpty()) {
            Toast.makeText(this, "Datos del caso no disponibles", Toast.LENGTH_SHORT).show()
            return
        }

        // No permitir chat consigo mismo
        if (reporterEmail == auth.currentUser?.email) {
            Toast.makeText(this, "No puedes chatear contigo mismo", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("ALERT_ID", caseId)
        intent.putExtra("ALERT_CODE", caseCode)
        intent.putExtra("OTHER_USER_EMAIL", reporterEmail)
        startActivity(intent)
    }

    // ✅ NUEVA FUNCIÓN: Abrir chat con quien resolvió el caso
    private fun openChatWithResolver() {
        if (resolverEmail.isNullOrEmpty()) {
            Toast.makeText(this, "Email del resolvedor no disponible", Toast.LENGTH_SHORT).show()
            return
        }

        if (caseId.isNullOrEmpty() || caseCode.isNullOrEmpty()) {
            Toast.makeText(this, "Datos del caso no disponibles", Toast.LENGTH_SHORT).show()
            return
        }

        // No permitir chat consigo mismo
        if (resolverEmail == auth.currentUser?.email) {
            Toast.makeText(this, "No puedes chatear contigo mismo", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("ALERT_ID", caseId)
        intent.putExtra("ALERT_CODE", caseCode)
        intent.putExtra("OTHER_USER_EMAIL", resolverEmail)
        startActivity(intent)
    }

    private fun openInMaps() {
        if (latitude == null || longitude == null) {
            Toast.makeText(this, "Ubicación no disponible", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${tvState.text})")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.google.android.apps.maps")

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Google Maps no está instalado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun markAsReviewed() {
        AlertDialog.Builder(this)
            .setTitle("Marcar como revisado")
            .setMessage("¿Confirmas que has revisado este caso?")
            .setPositiveButton("Sí") { _, _ ->
                updateReviewedStatus()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun updateReviewedStatus() {
        showLoading(true)

        caseId?.let { id ->
            val updateData = hashMapOf<String, Any>(
                "reviewedByAdmin" to true,
                "reviewedAt" to FieldValue.serverTimestamp(),
                "reviewedBy" to (auth.currentUser?.email ?: "Admin")
            )

            db.collection("alerts")
                .document(id)
                .update(updateData)
                .addOnSuccessListener {
                    showLoading(false)
                    Toast.makeText(this, "✅ Caso marcado como revisado", Toast.LENGTH_SHORT).show()
                    btnMarkAsReviewed.isEnabled = false
                    btnMarkAsReviewed.text = "✅ Revisado"
                }
                .addOnFailureListener { e ->
                    showLoading(false)
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun formatDate(date: Date): String {
        return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date)
    }
}