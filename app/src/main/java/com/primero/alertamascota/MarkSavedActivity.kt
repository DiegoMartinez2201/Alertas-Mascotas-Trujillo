package com.primero.alertamascota

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MarkSavedActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MarkSavedActivity"
        const val EXTRA_ALERT_ID = "alert_id"
        private const val STORAGE_PERMISSION_CODE = 100
    }

    // UI Elements existentes
    private lateinit var layoutAddPhoto: LinearLayout
    private lateinit var ivSavedPhoto: ImageView
    private lateinit var layoutPhotoPlaceholder: LinearLayout
    private lateinit var etSavedNotes: TextInputEditText
    private lateinit var btnCancelSave: Button
    private lateinit var btnConfirmSaved: Button
    private lateinit var progressBar: ProgressBar

    // Nuevos UI Elements para datos personales y firma
    private lateinit var etRescuerName: TextInputEditText
    private lateinit var etRescuerDNI: TextInputEditText
    private lateinit var etRescuerPhone: TextInputEditText
    private lateinit var etRescuerAddress: TextInputEditText
    private lateinit var signatureCanvas: SignatureCanvas
    private lateinit var btnClearSignature: Button
    private lateinit var tvAlertInfo: TextView

    private var savedPhotoUri: Uri? = null
    private var alertId: String? = null
    private var alertData: Map<String, Any?>? = null

    private val db = Firebase.firestore
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            savedPhotoUri = result.data?.data
            if (savedPhotoUri != null) {
                Log.d(TAG, "Imagen de salvamento seleccionada: $savedPhotoUri")
                ivSavedPhoto.setImageURI(savedPhotoUri)
                ivSavedPhoto.visibility = View.VISIBLE
                layoutPhotoPlaceholder.visibility = View.GONE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mark_saved)

        alertId = intent.getStringExtra(EXTRA_ALERT_ID)

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
        // Views existentes
        layoutAddPhoto = findViewById(R.id.layoutAddPhoto)
        ivSavedPhoto = findViewById(R.id.ivSavedPhoto)
        layoutPhotoPlaceholder = findViewById(R.id.layoutPhotoPlaceholder)
        etSavedNotes = findViewById(R.id.etSavedNotes)
        btnCancelSave = findViewById(R.id.btnCancelSave)
        btnConfirmSaved = findViewById(R.id.btnConfirmSaved)
        progressBar = findViewById(R.id.progressBar)

        // Nuevas views para datos personales y firma
        etRescuerName = findViewById(R.id.etRescuerName)
        etRescuerDNI = findViewById(R.id.etRescuerDNI)
        etRescuerPhone = findViewById(R.id.etRescuerPhone)
        etRescuerAddress = findViewById(R.id.etRescuerAddress)
        signatureCanvas = findViewById(R.id.signatureCanvas)
        btnClearSignature = findViewById(R.id.btnClearSignature)
        tvAlertInfo = findViewById(R.id.tvAlertInfo)
    }

    private fun setupListeners() {
        layoutAddPhoto.setOnClickListener {
            openImagePicker()
        }

        btnClearSignature.setOnClickListener {
            signatureCanvas.clear()
        }

        btnCancelSave.setOnClickListener {
            finish()
        }

        btnConfirmSaved.setOnClickListener {
            if (validateForm()) {
                markAlertAsSaved()
            }
        }
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
                        alertData = mapOf(
                            "id" to id,
                            "petType" to (document.getString("petType") ?: "Desconocido"),
                            "state" to (document.getString("state") ?: "Desconocido"),
                            "description" to (document.getString("description") ?: "Sin descripción"),
                            "address" to (document.getString("address") ?: "Dirección no disponible"),
                            "lat" to (document.getDouble("lat") ?: 0.0),
                            "lng" to (document.getDouble("lng") ?: 0.0),
                            "ownerEmail" to (document.getString("ownerEmail") ?: "Desconocido"),
                            "createdAt" to document.getTimestamp("createdAt")
                        )

                        val petType = alertData!!["petType"] as String
                        val state = alertData!!["state"] as String
                        val address = alertData!!["address"] as String

                        tvAlertInfo.text = "📋 Caso: $petType - $state\n📍 $address"
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

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        pickImageLauncher.launch(intent)
    }

    private fun validateForm(): Boolean {
        val name = etRescuerName.text.toString().trim()
        val dni = etRescuerDNI.text.toString().trim()
        val phone = etRescuerPhone.text.toString().trim()
        val address = etRescuerAddress.text.toString().trim()

        if (name.isEmpty()) {
            etRescuerName.error = "Ingrese su nombre completo"
            return false
        }

        if (dni.isEmpty()) {
            etRescuerDNI.error = "Ingrese su DNI"
            return false
        }

        if (phone.isEmpty()) {
            etRescuerPhone.error = "Ingrese su teléfono"
            return false
        }

        if (address.isEmpty()) {
            etRescuerAddress.error = "Ingrese su dirección"
            return false
        }

        if (signatureCanvas.isEmpty()) {
            Toast.makeText(this, "Por favor firme el documento", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun markAlertAsSaved() {
        showLoading(true)
        Log.d(TAG, "Iniciando proceso de marcado como salvado...")

        // Si hay foto, primero subirla
        if (savedPhotoUri != null) {
            uploadPhotoThenGeneratePDF()
        } else {
            // No hay foto, generar PDF directamente
            checkPermissionsAndGeneratePDF(null)
        }
    }

    private fun uploadPhotoThenGeneratePDF() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            showLoading(false)
            return
        }

        val fileName = "${System.currentTimeMillis()}_saved.jpg"
        val photoRef = storage.reference
            .child("saved_photos")
            .child(alertId ?: "unknown")
            .child(fileName)

        Log.d(TAG, "Subiendo foto de salvamento...")

        savedPhotoUri?.let { uri ->
            photoRef.putFile(uri)
                .addOnSuccessListener {
                    Log.d(TAG, "Foto subida, obteniendo URL...")
                    photoRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        Log.d(TAG, "URL obtenida: $downloadUri")
                        // Foto subida, ahora generar PDF
                        checkPermissionsAndGeneratePDF(downloadUri.toString())
                    }.addOnFailureListener { e ->
                        Log.e(TAG, "Error obteniendo URL: ${e.message}", e)
                        showLoading(false)
                        Toast.makeText(this, "Error al obtener URL de foto", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error subiendo foto: ${e.message}", e)
                    showLoading(false)
                    Toast.makeText(this, "Error al subir foto: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun checkPermissionsAndGeneratePDF(savedPhotoUrl: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            generatePDFAndSave(savedPhotoUrl)
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_CODE
                )
            } else {
                generatePDFAndSave(savedPhotoUrl)
            }
        }
    }

    private fun generatePDFAndSave(savedPhotoUrl: String?) {
        try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            val paint = Paint()

            var yPosition = 50f
            val leftMargin = 50f
            val rightMargin = 545f

            // TÍTULO
            paint.color = Color.parseColor("#4CAF50")
            paint.textSize = 24f
            paint.isFakeBoldText = true
            canvas.drawText("CERTIFICADO DE RESCATE", leftMargin, yPosition, paint)
            yPosition += 40

            // Línea separadora
            paint.color = Color.GRAY
            paint.strokeWidth = 2f
            canvas.drawLine(leftMargin, yPosition, rightMargin, yPosition, paint)
            yPosition += 30

            // INFORMACIÓN DEL CASO
            paint.color = Color.BLACK
            paint.textSize = 16f
            paint.isFakeBoldText = true
            canvas.drawText("INFORMACIÓN DEL CASO", leftMargin, yPosition, paint)
            yPosition += 25

            paint.textSize = 12f
            paint.isFakeBoldText = false

            val caseCode = "#${alertId?.takeLast(7) ?: "N/A"}"
            canvas.drawText("Código de Caso: $caseCode", leftMargin + 20, yPosition, paint)
            yPosition += 20

            val petType = alertData?.get("petType") as? String ?: "Desconocido"
            canvas.drawText("Tipo de Animal: $petType", leftMargin + 20, yPosition, paint)
            yPosition += 20

            val state = alertData?.get("state") as? String ?: "Desconocido"
            canvas.drawText("Estado: $state", leftMargin + 20, yPosition, paint)
            yPosition += 20

            val description = alertData?.get("description") as? String ?: "Sin descripción"
            canvas.drawText("Descripción: $description", leftMargin + 20, yPosition, paint)
            yPosition += 20

            val address = alertData?.get("address") as? String ?: "Dirección no disponible"
            canvas.drawText("Ubicación del Rescate:", leftMargin + 20, yPosition, paint)
            yPosition += 18

            val addressLines = wrapText(address, 60)
            addressLines.forEach { line ->
                canvas.drawText(line, leftMargin + 40, yPosition, paint)
                yPosition += 18
            }
            yPosition += 10

            val lat = alertData?.get("lat") as? Double ?: 0.0
            val lng = alertData?.get("lng") as? Double ?: 0.0
            canvas.drawText("Coordenadas: %.6f, %.6f".format(lat, lng), leftMargin + 20, yPosition, paint)
            yPosition += 20

            val ownerEmail = alertData?.get("ownerEmail") as? String ?: "Desconocido"
            canvas.drawText("Reportado por: $ownerEmail", leftMargin + 20, yPosition, paint)
            yPosition += 20

            val createdAt = alertData?.get("createdAt") as? com.google.firebase.Timestamp
            if (createdAt != null) {
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                canvas.drawText("Fecha de Reporte: ${sdf.format(createdAt.toDate())}", leftMargin + 20, yPosition, paint)
            }
            yPosition += 40

            // INFORMACIÓN DEL RESCATISTA
            paint.textSize = 16f
            paint.isFakeBoldText = true
            canvas.drawText("DATOS DEL RESCATISTA", leftMargin, yPosition, paint)
            yPosition += 25

            paint.textSize = 12f
            paint.isFakeBoldText = false

            val rescuerName = etRescuerName.text.toString()
            canvas.drawText("Nombre Completo: $rescuerName", leftMargin + 20, yPosition, paint)
            yPosition += 20

            val rescuerDNI = etRescuerDNI.text.toString()
            canvas.drawText("DNI: $rescuerDNI", leftMargin + 20, yPosition, paint)
            yPosition += 20

            val rescuerPhone = etRescuerPhone.text.toString()
            canvas.drawText("Teléfono: $rescuerPhone", leftMargin + 20, yPosition, paint)
            yPosition += 20

            val rescuerAddress = etRescuerAddress.text.toString()
            canvas.drawText("Dirección: $rescuerAddress", leftMargin + 20, yPosition, paint)
            yPosition += 20

            val rescuerEmail = auth.currentUser?.email ?: "No disponible"
            canvas.drawText("Email: $rescuerEmail", leftMargin + 20, yPosition, paint)
            yPosition += 30

            val notes = etSavedNotes.text.toString().trim()
            if (notes.isNotEmpty()) {
                paint.isFakeBoldText = true
                canvas.drawText("Notas del Rescate:", leftMargin + 20, yPosition, paint)
                yPosition += 18
                paint.isFakeBoldText = false

                val notesLines = wrapText(notes, 60)
                notesLines.forEach { line ->
                    canvas.drawText(line, leftMargin + 40, yPosition, paint)
                    yPosition += 18
                }
                yPosition += 20
            }

            // FIRMA
            paint.textSize = 16f
            paint.isFakeBoldText = true
            canvas.drawText("FIRMA DEL RESCATISTA", leftMargin, yPosition, paint)
            yPosition += 20

            val signatureBitmap = signatureCanvas.getSignatureBitmap()
            val signatureWidth = 200f
            val signatureHeight = 100f
            val signatureRect = android.graphics.Rect(
                leftMargin.toInt() + 20,
                yPosition.toInt(),
                (leftMargin + 20 + signatureWidth).toInt(),
                (yPosition + signatureHeight).toInt()
            )
            canvas.drawBitmap(signatureBitmap, null, signatureRect, null)
            yPosition += signatureHeight + 10

            paint.color = Color.BLACK
            paint.strokeWidth = 1f
            canvas.drawLine(leftMargin, yPosition, rightMargin, yPosition, paint)
            yPosition += 15

            paint.textSize = 10f
            paint.isFakeBoldText = false
            canvas.drawText("Firma Digital", leftMargin + 80, yPosition, paint)
            yPosition += 30

            val currentDate = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
            paint.textSize = 10f
            paint.color = Color.GRAY
            canvas.drawText("Fecha de Certificación: $currentDate", leftMargin, yPosition, paint)
            yPosition += 15

            canvas.drawText("Este documento certifica el rescate del animal mencionado", leftMargin, yPosition, paint)
            yPosition += 12
            canvas.drawText("según lo reportado en nuestra plataforma.", leftMargin, yPosition, paint)

            pdfDocument.finishPage(page)

            val fileName = "Rescate_${caseCode}_${System.currentTimeMillis()}.pdf"
            val file = savePDFToFile(pdfDocument, fileName)

            pdfDocument.close()

            if (file != null) {
                updateAlertStatus(savedPhotoUrl, file)
            } else {
                showLoading(false)
                Toast.makeText(this, "Error al guardar PDF", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error generando PDF: ${e.message}", e)
            showLoading(false)
            Toast.makeText(this, "Error al generar PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun wrapText(text: String, maxChars: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""

        words.forEach { word ->
            if ((currentLine + word).length <= maxChars) {
                currentLine += "$word "
            } else {
                lines.add(currentLine.trim())
                currentLine = "$word "
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.trim())
        }

        return lines
    }

    private fun savePDFToFile(pdfDocument: PdfDocument, fileName: String): File? {
        return try {
            val file = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
            } else {
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    fileName
                )
            }

            file.parentFile?.mkdirs()
            pdfDocument.writeTo(FileOutputStream(file))
            Log.d(TAG, "PDF guardado en: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando PDF: ${e.message}", e)
            null
        }
    }

    private fun updateAlertStatus(savedPhotoUrl: String?, pdfFile: File) {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            showLoading(false)
            return
        }

        Log.d(TAG, "Actualizando estado de alerta a 'saved'...")

        val rescueData = hashMapOf(
            "rescuerName" to etRescuerName.text.toString(),
            "rescuerDNI" to etRescuerDNI.text.toString(),
            "rescuerPhone" to etRescuerPhone.text.toString(),
            "rescuerAddress" to etRescuerAddress.text.toString(),
            "rescuerEmail" to user.email,
            "notes" to etSavedNotes.text.toString(),
            "rescueDate" to FieldValue.serverTimestamp(),
            "pdfPath" to pdfFile.absolutePath
        )

        val updateData = hashMapOf<String, Any>(
            "status" to "saved",
            "savedBy" to user.uid,
            "savedByEmail" to (user.email ?: "Desconocido"),
            "savedAt" to FieldValue.serverTimestamp(),
            "savedNotes" to etSavedNotes.text.toString(),
            "savedPhotoUrl" to (savedPhotoUrl ?: ""),
            "resolvedBy" to (user.email ?: ""),
            "resolvedAt" to FieldValue.serverTimestamp(),
            "rescueInfo" to rescueData
        )

        alertId?.let { id ->
            db.collection("alerts")
                .document(id)
                .update(updateData)
                .addOnSuccessListener {
                    Log.d(TAG, "✅ Alerta marcada como salvada con certificado PDF")
                    showLoading(false)
                    showSuccessDialog(pdfFile)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Error actualizando alerta: ${e.message}", e)
                    showLoading(false)
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun showSuccessDialog(pdfFile: File) {
        AlertDialog.Builder(this)
            .setTitle("✅ ¡Rescate Certificado!")
            .setMessage("¡Gracias por salvar a esta mascota! 🐾❤️\n\nEl certificado PDF ha sido generado exitosamente.\n\n¿Qué deseas hacer?")
            .setPositiveButton("Abrir PDF") { _, _ ->
                openPDF(pdfFile)
            }
            .setNegativeButton("Compartir") { _, _ ->
                sharePDF(pdfFile)
            }
            .setNeutralButton("Cerrar") { _, _ ->
                setResult(Activity.RESULT_OK)
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun openPDF(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            startActivity(intent)
            setResult(Activity.RESULT_OK)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error abriendo PDF: ${e.message}", e)
            Toast.makeText(this, "No se puede abrir el PDF. Instala un lector de PDF.", Toast.LENGTH_LONG).show()
        }
    }

    private fun sharePDF(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Certificado de Rescate Animal")
                putExtra(Intent.EXTRA_TEXT, "Adjunto certificado de rescate animal.")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            startActivity(Intent.createChooser(intent, "Compartir Certificado"))
            setResult(Activity.RESULT_OK)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error compartiendo PDF: ${e.message}", e)
            Toast.makeText(this, "Error al compartir PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnConfirmSaved.isEnabled = !show
        btnCancelSave.isEnabled = !show
        layoutAddPhoto.isEnabled = !show
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Reintentar generación de PDF
                Toast.makeText(this, "Permiso concedido, reintentando...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permiso denegado para guardar PDF", Toast.LENGTH_LONG).show()
            }
        }
    }
}

// Custom View para firma digital
class SignatureCanvas(context: android.content.Context, attrs: android.util.AttributeSet) :
    View(context, attrs) {

    private val path = Path()
    private val paint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private var isEmpty = true

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(path, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                path.moveTo(x, y)
                isEmpty = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                path.lineTo(x, y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
                // No hacer nada
            }
            else -> return false
        }

        invalidate()
        return true
    }

    fun clear() {
        path.reset()
        isEmpty = true
        invalidate()
    }

    fun isEmpty(): Boolean = isEmpty

    fun getSignatureBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        draw(canvas)
        return bitmap
    }
}