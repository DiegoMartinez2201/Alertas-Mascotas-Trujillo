package com.primero.alertamascota

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class AlertDetailActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AlertDetailActivity"
        const val EXTRA_ALERT_ID = "alert_id"
        private const val REQUEST_MARK_SAVED = 1001
    }

    private lateinit var ivAlertPhoto: ImageView
    private lateinit var tvNoPhoto: TextView
    private lateinit var tvAlertState: TextView
    private lateinit var tvAlertPetType: TextView
    private lateinit var tvAlertStatus: TextView
    private lateinit var tvAlertDescription: TextView
    private lateinit var tvAlertAddress: TextView
    private lateinit var tvAlertCoords: TextView
    private lateinit var tvReporterEmail: TextView
    private lateinit var tvReportDate: TextView
    private lateinit var btnMarkAsSaved: Button
    private lateinit var btnClose: Button
    private lateinit var btnShare: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var btnContactReporter: Button

    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()
    private var alertId: String? = null
    private var currentStatus: String = "active"
    private var alertData: Map<String, Any?> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alert_detail)

        alertId = when {
            // Si viene desde un Deep Link (QR escaneado)
            intent?.action == Intent.ACTION_VIEW -> {
                intent.data?.lastPathSegment
            }
            // Si viene desde Intent normal (click en mapa)
            else -> intent.getStringExtra(EXTRA_ALERT_ID)
        }

        if (alertId == null) {
            Toast.makeText(this, "Error: ID de alerta no válido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupListeners()
        loadAlertData()
    }

    private fun initViews() {
        ivAlertPhoto = findViewById(R.id.ivAlertPhoto)
        tvNoPhoto = findViewById(R.id.tvNoPhoto)
        tvAlertState = findViewById(R.id.tvAlertState)
        tvAlertStatus = findViewById(R.id.tvAlertStatus)
        tvAlertDescription = findViewById(R.id.tvAlertDescription)
        tvAlertAddress = findViewById(R.id.tvAlertAddress)
        tvAlertCoords = findViewById(R.id.tvAlertCoords)
        tvReporterEmail = findViewById(R.id.tvReporterEmail)
        tvReportDate = findViewById(R.id.tvReportDate)
        btnMarkAsSaved = findViewById(R.id.btnMarkAsSaved)
        btnClose = findViewById(R.id.btnClose)
        btnShare = findViewById(R.id.btnShare)
        progressBar = findViewById(R.id.progressBar)
        tvAlertPetType = findViewById(R.id.tvAlertPetType)
        btnContactReporter = findViewById(R.id.btnContactReporter)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    private fun setupListeners() {
        btnClose.setOnClickListener {
            finish()
        }

        btnMarkAsSaved.setOnClickListener {
            openMarkAsSavedActivity()
        }

        btnShare.setOnClickListener {
            showShareDialog()
        }

        btnContactReporter.setOnClickListener {
            openChatWithReporter()
        }
    }

    // ✨ NUEVO: Configurar email clickeable para ver perfil
    private fun setupReporterEmailClick() {
        // Hacer el email clickeable para abrir el perfil público
        tvReporterEmail.setOnClickListener {
            val reporterEmail = alertData["ownerEmail"] as? String

            if (!reporterEmail.isNullOrEmpty() &&
                android.util.Patterns.EMAIL_ADDRESS.matcher(reporterEmail).matches()) {
                openPublicProfile(reporterEmail)
            } else {
                Toast.makeText(this, "Email no válido", Toast.LENGTH_SHORT).show()
            }
        }

        Log.d(TAG, "Email clickeable configurado")
    }

    // ✨ NUEVO: Abrir perfil público del reportante
    private fun openPublicProfile(email: String) {
        val intent = Intent(this, PublicProfileActivity::class.java)
        intent.putExtra(PublicProfileActivity.EXTRA_USER_EMAIL, email)
        startActivity(intent)
    }

    private fun openChatWithReporter() {
        val reporterEmail = alertData["ownerEmail"] as? String
        val alertId = alertData["id"] as? String
        val state = alertData["state"] as? String

        if (reporterEmail.isNullOrEmpty() || alertId.isNullOrEmpty()) {
            Toast.makeText(this, "Datos no disponibles", Toast.LENGTH_SHORT).show()
            return
        }

        // No permitir chat consigo mismo
        if (reporterEmail == auth.currentUser?.email) {
            Toast.makeText(this, "Esta es tu propia alerta", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("ALERT_ID", alertId)
        intent.putExtra("ALERT_CODE", "#${alertId.takeLast(7)}")
        intent.putExtra("OTHER_USER_EMAIL", reporterEmail)
        startActivity(intent)
    }

    private fun loadAlertData() {
        progressBar.visibility = View.VISIBLE

        alertId?.let { id ->
            db.collection("alerts")
                .document(id)
                .get()
                .addOnSuccessListener { document ->
                    progressBar.visibility = View.GONE

                    if (document.exists()) {
                        val photoUrl = document.getString("photoUrl")
                        val state = document.getString("state") ?: "Desconocido"
                        val status = document.getString("status") ?: "active"
                        val description = document.getString("description") ?: "Sin descripción"
                        val address = document.getString("address") ?: "Dirección no disponible"
                        val lat = document.getDouble("lat") ?: 0.0
                        val lng = document.getDouble("lng") ?: 0.0
                        val ownerEmail = document.getString("ownerEmail") ?: "Desconocido"
                        val createdAt = document.getTimestamp("createdAt")
                        val petType = document.getString("petType") ?: "Desconocido"

                        currentStatus = status

                        // Guardar datos para compartir
                        alertData = mapOf(
                            "id" to id,
                            "petType" to petType,
                            "state" to state,
                            "status" to status,
                            "description" to description,
                            "address" to address,
                            "lat" to lat,
                            "lng" to lng,
                            "ownerEmail" to ownerEmail,
                            "photoUrl" to (photoUrl ?: "")
                        )

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
                        tvAlertState.text = "$state 🐾"
                        tvAlertDescription.text = description
                        tvAlertAddress.text = address
                        tvAlertCoords.text = "Lat: %.6f, Lng: %.6f".format(lat, lng)
                        tvReporterEmail.text = ownerEmail
                        tvAlertPetType.text = petType

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
                                tvAlertStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark))
                                tvAlertStatus.setBackgroundColor(resources.getColor(android.R.color.holo_green_light))
                                btnMarkAsSaved.visibility = View.VISIBLE
                            }
                            "saved" -> {
                                tvAlertStatus.text = "✅ Salvada"
                                tvAlertStatus.setTextColor(resources.getColor(android.R.color.holo_blue_dark))
                                tvAlertStatus.setBackgroundColor(resources.getColor(android.R.color.holo_blue_light))
                                btnMarkAsSaved.visibility = View.GONE
                            }
                        }

                        // ✨ NUEVO: Configurar email clickeable después de cargar los datos
                        setupReporterEmailClick()

                    } else {
                        Toast.makeText(this, "Alerta no encontrada", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = View.GONE
                    Log.e(TAG, "Error cargando alerta: ${e.message}", e)
                    Toast.makeText(this, "Error al cargar alerta", Toast.LENGTH_SHORT).show()
                    finish()
                }
        }
    }

    private fun showShareDialog() {
        val options = arrayOf(
            "📱 Compartir como texto",
            "🗺️ Compartir ubicación en mapa",
            "📷 Compartir con foto",
            "🔗 Compartir link directo",
            "📄 Ver código QR"
        )

        AlertDialog.Builder(this)
            .setTitle("¿Cómo deseas compartir?")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> shareAsText()
                    1 -> shareLocation()
                    2 -> shareWithPhoto()
                    3 -> shareDirectLink()
                    4 -> showQRCode()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun shareDirectLink() {
        val deepLink = "alertamascotas://alert/${alertData["id"]}"
        val alertCode = "#${alertData["id"].toString().takeLast(7)}"

        val shareText = """
        🐾 ¡ALERTA DE MASCOTA!
        
        ${alertData["state"]} - ${alertData["petType"]}
        Código: $alertCode
        
        ${alertData["description"]}
        📍 ${alertData["address"]}
        
        👉 Abrir en app: $deepLink
        
        (Necesitas tener instalada AlertaMascotas)
        
        #RescateAnimal
    """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, "Compartir link"))
    }

    private fun shareAsText() {
        val shareText = buildShareText()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "🐾 Alerta de Mascota")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, "Compartir alerta"))
    }

    private fun shareLocation() {
        val lat = alertData["lat"] as? Double ?: 0.0
        val lng = alertData["lng"] as? Double ?: 0.0
        val state = alertData["state"] as? String ?: "Mascota"

        val uri = "https://www.google.com/maps?q=$lat,$lng"
        val shareText = """
            🐾 $state necesita ayuda
            
            📍 Ubicación: ${alertData["address"]}
            🗺️ Ver en mapa: $uri
            
            ¡Ayúdanos a rescatar esta mascota!
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, "Compartir ubicación"))
    }

    private fun shareWithPhoto() {
        val photoUrl = alertData["photoUrl"] as? String

        if (photoUrl.isNullOrEmpty()) {
            Toast.makeText(this, "Esta alerta no tiene foto", Toast.LENGTH_SHORT).show()
            shareAsText()
            return
        }

        val shareText = buildShareText()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "🐾 Alerta de Mascota")
            putExtra(Intent.EXTRA_TEXT, "$shareText\n\nFoto: $photoUrl")
        }
        startActivity(Intent.createChooser(intent, "Compartir con foto"))
    }

    private fun showQRCode() {
        val qrBitmap = generateQRCode()
        if (qrBitmap == null) {
            Toast.makeText(this, "Error al generar código QR", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_qr_code, null)
        val ivQR = dialogView.findViewById<ImageView>(R.id.ivQRCode)
        val btnShareQR = dialogView.findViewById<Button>(R.id.btnShareQR)
        val tvQRInfo = dialogView.findViewById<TextView>(R.id.tvQRInfo)

        ivQR.setImageBitmap(qrBitmap)

        // Información más clara
        val alertCode = "#${alertData["id"].toString().takeLast(7)}"
        tvQRInfo.text = """
        ${alertData["state"]} - ${alertData["petType"]}
        Código: $alertCode
        
        📱 Escanea para abrir en la app
    """.trimIndent()

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setNegativeButton("Cerrar", null)
            .create()

        btnShareQR.setOnClickListener {
            shareQRCode(qrBitmap)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun generateQRCode(): Bitmap? {
        return try {
            // Deep Link simple - abre directamente la alerta en la app
            val deepLink = "alertamascotas://alert/${alertData["id"]}"

            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(deepLink, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }

            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error generando QR: ${e.message}", e)
            null
        }
    }

    private fun shareQRCode(qrBitmap: Bitmap) {
        try {
            // Guardar QR temporalmente
            val cachePath = File(cacheDir, "qr_codes")
            cachePath.mkdirs()
            val file = File(cachePath, "alert_qr_${alertData["id"]}.png")
            val stream = FileOutputStream(file)
            qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            // Compartir
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )

            val shareText = buildShareText()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "Compartir código QR"))

        } catch (e: Exception) {
            Log.e(TAG, "Error compartiendo QR: ${e.message}", e)
            Toast.makeText(this, "Error al compartir QR", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildShareText(): String {
        return """
            🐾 ¡ALERTA DE MASCOTA! 
            
            🐾 Tipo: ${alertData["petType"]}
            📋 Estado: ${alertData["state"]}
            📝 Descripción: ${alertData["description"]}
            📍 Ubicación: ${alertData["address"]}
            🗺️ Coordenadas: ${alertData["lat"]}, ${alertData["lng"]}
            
            👤 Reportado por: ${alertData["ownerEmail"]}
            
            ${if (currentStatus == "active") "⚠️ URGENTE: Esta mascota necesita ayuda" else "✅ Esta mascota ya fue salvada"}
            
            #RescateAnimal #AyudaMascotas
        """.trimIndent()
    }

    private fun openMarkAsSavedActivity() {
        val intent = Intent(this, MarkSavedActivity::class.java)
        intent.putExtra(MarkSavedActivity.EXTRA_ALERT_ID, alertId)
        startActivityForResult(intent, REQUEST_MARK_SAVED)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_MARK_SAVED && resultCode == RESULT_OK) {
            loadAlertData()
            setResult(RESULT_OK)
        }
    }
}