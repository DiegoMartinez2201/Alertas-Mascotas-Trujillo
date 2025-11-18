package com.primero.alertamascota

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.io.IOException
import java.util.*

class AlertFormActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AlertFormActivity"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
    }

    private lateinit var layoutAddPhoto: LinearLayout
    private lateinit var ivPhotoPreview: ImageView
    private lateinit var layoutPhotoPlaceholder: LinearLayout
    private lateinit var tvLocationCoords: TextView
    private lateinit var etAddress: TextInputEditText
    private lateinit var spinnerPetType: Spinner
    private lateinit var spinnerState: Spinner
    private lateinit var etDescription: TextInputEditText
    private lateinit var btnCancel: Button
    private lateinit var btnRegister: Button
    private lateinit var progressBar: ProgressBar

    private var photoUri: Uri? = null
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0

    private val db = Firebase.firestore
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Launcher para seleccionar imagen
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            photoUri = result.data?.data
            if (photoUri != null) {
                Log.d(TAG, "Imagen seleccionada: $photoUri")
                ivPhotoPreview.setImageURI(photoUri)
                ivPhotoPreview.visibility = View.VISIBLE
                layoutPhotoPlaceholder.visibility = View.GONE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alert_form)

        // ✅ Habilitar persistencia offline de Firestore
        enableFirestoreOfflineMode()

        // Obtener coordenadas del Intent
        latitude = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
        longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)

        Log.d(TAG, "========================================")
        Log.d(TAG, "🚀 Iniciando formulario con coordenadas: $latitude, $longitude")
        Log.d(TAG, "========================================")

        // ✅ Verificar conexión a Firestore al iniciar
        verifyFirestoreConnection()

        // ✅ Verificar usuario autenticado
        val user = auth.currentUser
        Log.d(TAG, "👤 Usuario actual: ${user?.email ?: "NO AUTENTICADO"}")
        Log.d(TAG, "👤 Usuario UID: ${user?.uid ?: "N/A"}")

        initViews()
        setupSpinners()
        setupListeners()

        // Mostrar coordenadas
        tvLocationCoords.text = "Lat: %.6f, Lng: %.6f".format(latitude, longitude)

        // Obtener dirección automáticamente
        getAddressFromLocation(latitude, longitude)
    }

    private fun enableFirestoreOfflineMode() {
        try {
            val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()
            db.firestoreSettings = settings
            Log.d(TAG, "✅ Modo offline de Firestore habilitado")
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Error habilitando modo offline (puede que ya esté habilitado): ${e.message}")
        }
    }

    private fun verifyFirestoreConnection() {
        Log.d(TAG, "🔥 Verificando conexión a Firestore...")
        db.collection("alerts")
            .limit(1)
            .get()
            .addOnSuccessListener {
                Log.d(TAG, "✅ Firestore conectado correctamente (${it.size()} documentos encontrados)")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Error conectando a Firestore: ${e.message}")
                Toast.makeText(this, "Advertencia: Problema de conexión con Firestore", Toast.LENGTH_SHORT).show()
            }
    }

    private fun initViews() {
        layoutAddPhoto = findViewById(R.id.layoutAddPhoto)
        ivPhotoPreview = findViewById(R.id.ivPhotoPreview)
        layoutPhotoPlaceholder = findViewById(R.id.layoutPhotoPlaceholder)
        tvLocationCoords = findViewById(R.id.tvLocationCoords)
        etAddress = findViewById(R.id.etAddress)
        spinnerPetType = findViewById(R.id.spinnerPetType)
        spinnerState = findViewById(R.id.spinnerState)
        etDescription = findViewById(R.id.etDescription)
        btnCancel = findViewById(R.id.btnCancel)
        btnRegister = findViewById(R.id.btnRegister)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupSpinners() {
        // Spinner de tipo de mascota
        val petTypeAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.pet_types,
            android.R.layout.simple_spinner_item
        )
        petTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPetType.adapter = petTypeAdapter

        // Spinner de estado
        val stateAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.alert_states,
            android.R.layout.simple_spinner_item
        )
        stateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerState.adapter = stateAdapter
    }

    private fun setupListeners() {
        // Click en agregar foto
        layoutAddPhoto.setOnClickListener {
            openImagePicker()
        }

        // Botón cancelar
        btnCancel.setOnClickListener {
            finish()
        }

        // Botón registrar
        btnRegister.setOnClickListener {
            validateAndRegisterAlert()
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        pickImageLauncher.launch(intent)
    }

    private fun getAddressFromLocation(lat: Double, lng: Double) {
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val fullAddress = address.getAddressLine(0)
                etAddress.setText(fullAddress)
                Log.d(TAG, "📍 Dirección obtenida: $fullAddress")
            } else {
                etAddress.setText("No se pudo obtener la dirección")
                Log.w(TAG, "⚠️ No se encontraron direcciones")
            }
        } catch (e: IOException) {
            Log.e(TAG, "❌ Error obteniendo dirección: ${e.message}", e)
            etAddress.setText("Error obteniendo dirección")
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        val isConnected = capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                )

        Log.d(TAG, "🌐 Red disponible: $isConnected")
        Log.d(TAG, "🌐 Tipo de red: ${when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Datos móviles"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "Sin conexión"
        }}")

        return isConnected
    }

    private fun validateAndRegisterAlert() {
        val address = etAddress.text.toString().trim()
        val selectedPetTypePosition = spinnerPetType.selectedItemPosition
        val selectedStatePosition = spinnerState.selectedItemPosition
        val description = etDescription.text.toString().trim()

        Log.d(TAG, "========================================")
        Log.d(TAG, "🔍 Iniciando validación...")
        Log.d(TAG, "========================================")

        // ✅ Verificar red ANTES de validar
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "❌ Sin conexión a internet. Verifica tu WiFi o datos móviles.", Toast.LENGTH_LONG).show()
            return
        }

        // Validaciones
        if (selectedPetTypePosition == 0) {
            Toast.makeText(this, "Por favor selecciona el tipo de mascota", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedStatePosition == 0) {
            Toast.makeText(this, "Por favor selecciona un estado", Toast.LENGTH_SHORT).show()
            return
        }

        if (address.isEmpty()) {
            Toast.makeText(this, "Por favor ingresa una dirección", Toast.LENGTH_SHORT).show()
            return
        }

        val petType = spinnerPetType.selectedItem.toString()
        val state = spinnerState.selectedItem.toString()
        Log.d(TAG, "✅ Validación exitosa")
        Log.d(TAG, "   - Tipo: $petType")
        Log.d(TAG, "   - Estado: $state")
        Log.d(TAG, "   - Dirección: $address")
        Log.d(TAG, "   - Tiene foto: ${photoUri != null}")

        // Si hay foto, primero subir la foto
        if (photoUri != null) {
            Log.d(TAG, "📸 Hay foto, subiendo primero...")
            uploadPhotoAndRegisterAlert(address, petType, state, description)
        } else {
            Log.d(TAG, "📝 No hay foto, registrando directamente...")
            registerAlert(address, petType, state, description, null)
        }
    }

    private fun uploadPhotoAndRegisterAlert(address: String, petType: String, state: String, description: String) {
        showLoading(true)

        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            showLoading(false)
            Log.e(TAG, "❌ Usuario no autenticado")
            return
        }

        // Referencia única para la foto
        val fileName = "${System.currentTimeMillis()}.jpg"
        val photoRef: StorageReference = storage.reference
            .child("alert_photos")
            .child(user.uid)
            .child(fileName)

        Log.d(TAG, "📸 Subiendo foto a: alert_photos/${user.uid}/$fileName")

        // Subir imagen
        photoUri?.let { uri ->
            photoRef.putFile(uri)
                .addOnProgressListener { taskSnapshot ->
                    val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                    Log.d(TAG, "📊 Progreso de subida: $progress%")
                }
                .addOnSuccessListener { taskSnapshot ->
                    Log.d(TAG, "✅ Foto subida exitosamente, obteniendo URL...")
                    // Obtener URL de descarga
                    photoRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        Log.d(TAG, "🔗 URL obtenida: $downloadUri")
                        registerAlert(address, petType, state, description, downloadUri.toString())
                    }.addOnFailureListener { e ->
                        Log.e(TAG, "❌ Error obteniendo URL de foto: ${e.message}", e)
                        Toast.makeText(this, "Error al obtener URL de foto: ${e.message}", Toast.LENGTH_LONG).show()
                        showLoading(false)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Error subiendo foto: ${e.message}", e)
                    Toast.makeText(this, "Error al subir foto: ${e.message}", Toast.LENGTH_LONG).show()
                    showLoading(false)
                }
        }
    }

    private fun registerAlert(address: String, petType: String, state: String, description: String, photoUrl: String?) {
        showLoading(true)

        val user = auth.currentUser
        if (user == null) {
            Log.e(TAG, "❌ Usuario es NULL")
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            showLoading(false)
            return
        }

        Log.d(TAG, "========================================")
        Log.d(TAG, "🔥 REGISTRANDO ALERTA EN FIRESTORE")
        Log.d(TAG, "========================================")
        Log.d(TAG, "🔐 User UID: ${user.uid}")
        Log.d(TAG, "📧 User Email: ${user.email}")
        Log.d(TAG, "✅ User isAnonymous: ${user.isAnonymous}")
        Log.d(TAG, "🔥 Firestore instance: $db")

        // Verificar red nuevamente
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        Log.d(TAG, "🌐 Red activa: ${network != null}")
        Log.d(TAG, "🌐 Capacidades: $capabilities")

        // Crear documento de alerta
        val alertData = hashMapOf(
            "lat" to latitude,
            "lng" to longitude,
            "address" to address,
            "petType" to petType,
            "state" to state,
            "description" to description,
            "photoUrl" to (photoUrl ?: ""),
            "ownerUid" to user.uid,
            "ownerEmail" to (user.email ?: "sin_email"),
            "createdAt" to com.google.firebase.Timestamp.now(),
            "status" to "active"
        )

        Log.d(TAG, "📦 Datos a enviar:")
        alertData.forEach { (key, value) ->
            Log.d(TAG, "   - $key: $value")
        }
        Log.d(TAG, "========================================")

        // Timeout handler
        val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
        var timedOut = false
        val timeoutRunnable = Runnable {
            timedOut = true
            Log.e(TAG, "========================================")
            Log.e(TAG, "⏱️ TIMEOUT: La operación tardó más de 15 segundos")
            Log.e(TAG, "========================================")
            Log.e(TAG, "Posibles causas:")
            Log.e(TAG, "1. Reglas de Firestore bloqueando la escritura")
            Log.e(TAG, "2. Conexión de red muy lenta")
            Log.e(TAG, "3. Firestore no responde")
            Log.e(TAG, "========================================")
            Toast.makeText(this, "La operación está tardando mucho. Verifica:\n1. Reglas de Firestore\n2. Tu conexión", Toast.LENGTH_LONG).show()
            showLoading(false)
        }
        timeoutHandler.postDelayed(timeoutRunnable, 15000)

        Log.d(TAG, "🚀 Llamando a db.collection('alerts').add()...")

        db.collection("alerts")
            .add(alertData)
            .addOnSuccessListener { documentReference ->
                if (timedOut) {
                    Log.w(TAG, "⚠️ Éxito llegó después del timeout")
                    return@addOnSuccessListener
                }
                timeoutHandler.removeCallbacks(timeoutRunnable)
                Log.d(TAG, "========================================")
                Log.d(TAG, "✅✅✅ ÉXITO TOTAL ✅✅✅")
                Log.d(TAG, "========================================")
                Log.d(TAG, "📄 Alerta registrada con ID: ${documentReference.id}")
                Log.d(TAG, "========================================")
                Toast.makeText(this, "¡Alerta registrada exitosamente! 🐾", Toast.LENGTH_LONG).show()
                showLoading(false)
                setResult(Activity.RESULT_OK)
                finish()
            }
            .addOnFailureListener { e ->
                if (timedOut) {
                    Log.w(TAG, "⚠️ Error llegó después del timeout")
                    return@addOnFailureListener
                }
                timeoutHandler.removeCallbacks(timeoutRunnable)
                Log.e(TAG, "========================================")
                Log.e(TAG, "❌❌❌ ERROR AL REGISTRAR ❌❌❌")
                Log.e(TAG, "========================================")
                Log.e(TAG, "Clase de error: ${e.javaClass.name}")
                Log.e(TAG, "Mensaje: ${e.message}")
                Log.e(TAG, "Mensaje localizado: ${e.localizedMessage}")
                Log.e(TAG, "Causa: ${e.cause}")
                Log.e(TAG, "========================================")
                Log.e(TAG, "Stack trace completo:")
                e.printStackTrace()
                Log.e(TAG, "========================================")

                val errorMsg = when {
                    e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true -> {
                        Log.e(TAG, "🔒 PROBLEMA DE PERMISOS: Las reglas de Firestore están bloqueando la escritura")
                        "❌ Error de permisos en Firestore.\n\nVerifica las reglas en Firebase Console."
                    }
                    e.message?.contains("UNAVAILABLE", ignoreCase = true) == true -> {
                        Log.e(TAG, "🌐 FIRESTORE NO DISPONIBLE: Problema de red o servicio caído")
                        "❌ Firestore no disponible.\n\nVerifica tu conexión a internet."
                    }
                    e.message?.contains("UNAUTHENTICATED", ignoreCase = true) == true -> {
                        Log.e(TAG, "🔐 NO AUTENTICADO: El usuario no está correctamente autenticado")
                        "❌ Usuario no autenticado.\n\nIntenta cerrar sesión y volver a entrar."
                    }
                    e.message?.contains("DEADLINE_EXCEEDED", ignoreCase = true) == true -> {
                        Log.e(TAG, "⏱️ TIMEOUT DE FIRESTORE: La red es muy lenta")
                        "❌ Timeout de Firestore.\n\nTu conexión es muy lenta."
                    }
                    else -> {
                        Log.e(TAG, "❓ ERROR DESCONOCIDO")
                        "❌ Error desconocido: ${e.message}"
                    }
                }

                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                showLoading(false)
            }

        Log.d(TAG, "⏳ Esperando respuesta de Firestore...")
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnRegister.isEnabled = !show
        btnCancel.isEnabled = !show
        layoutAddPhoto.isEnabled = !show

        if (show) {
            Log.d(TAG, "⏳ Mostrando loading...")
        } else {
            Log.d(TAG, "✅ Ocultando loading")
        }
    }
}