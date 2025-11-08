package com.primero.alertamascota

import android.app.Activity
import android.content.Intent
import android.location.Geocoder
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
    private lateinit var spinnerPetType: Spinner // ✨ NUEVO
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

        // Obtener coordenadas del Intent
        latitude = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
        longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)

        Log.d(TAG, "Iniciando formulario con coordenadas: $latitude, $longitude")

        initViews()
        setupSpinners() // ✨ ACTUALIZADO
        setupListeners()

        // Mostrar coordenadas
        tvLocationCoords.text = "Lat: %.6f, Lng: %.6f".format(latitude, longitude)

        // Obtener dirección automáticamente
        getAddressFromLocation(latitude, longitude)
    }

    private fun initViews() {
        layoutAddPhoto = findViewById(R.id.layoutAddPhoto)
        ivPhotoPreview = findViewById(R.id.ivPhotoPreview)
        layoutPhotoPlaceholder = findViewById(R.id.layoutPhotoPlaceholder)
        tvLocationCoords = findViewById(R.id.tvLocationCoords)
        etAddress = findViewById(R.id.etAddress)
        spinnerPetType = findViewById(R.id.spinnerPetType) // ✨ NUEVO
        spinnerState = findViewById(R.id.spinnerState)
        etDescription = findViewById(R.id.etDescription)
        btnCancel = findViewById(R.id.btnCancel)
        btnRegister = findViewById(R.id.btnRegister)
        progressBar = findViewById(R.id.progressBar)
    }

    // ✨ ACTUALIZADO: Configurar ambos spinners
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
                Log.d(TAG, "Dirección obtenida: $fullAddress")
            } else {
                etAddress.setText("No se pudo obtener la dirección")
                Log.w(TAG, "No se encontraron direcciones")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error obteniendo dirección: ${e.message}", e)
            etAddress.setText("Error obteniendo dirección")
        }
    }

    // ✨ ACTUALIZADO: Validar tipo de mascota también
    private fun validateAndRegisterAlert() {
        val address = etAddress.text.toString().trim()
        val selectedPetTypePosition = spinnerPetType.selectedItemPosition
        val selectedStatePosition = spinnerState.selectedItemPosition
        val description = etDescription.text.toString().trim()

        Log.d(TAG, "Iniciando validación...")

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
        Log.d(TAG, "Validación exitosa. Tipo: $petType, Estado: $state, Dirección: $address")

        // Si hay foto, primero subir la foto
        if (photoUri != null) {
            Log.d(TAG, "Hay foto, subiendo...")
            uploadPhotoAndRegisterAlert(address, petType, state, description)
        } else {
            Log.d(TAG, "No hay foto, registrando directamente...")
            registerAlert(address, petType, state, description, null)
        }
    }

    // ✨ ACTUALIZADO: Agregar petType
    private fun uploadPhotoAndRegisterAlert(address: String, petType: String, state: String, description: String) {
        showLoading(true)

        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            showLoading(false)
            Log.e(TAG, "Usuario no autenticado")
            return
        }

        // Referencia única para la foto
        val fileName = "${System.currentTimeMillis()}.jpg"
        val photoRef: StorageReference = storage.reference
            .child("alert_photos")
            .child(user.uid)
            .child(fileName)

        Log.d(TAG, "Subiendo foto a: alert_photos/${user.uid}/$fileName")

        // Subir imagen
        photoUri?.let { uri ->
            photoRef.putFile(uri)
                .addOnProgressListener { taskSnapshot ->
                    val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                    Log.d(TAG, "Progreso de subida: $progress%")
                }
                .addOnSuccessListener { taskSnapshot ->
                    Log.d(TAG, "Foto subida exitosamente, obteniendo URL...")
                    // Obtener URL de descarga
                    photoRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        Log.d(TAG, "URL obtenida: $downloadUri")
                        registerAlert(address, petType, state, description, downloadUri.toString())
                    }.addOnFailureListener { e ->
                        Log.e(TAG, "Error obteniendo URL de foto: ${e.message}", e)
                        Toast.makeText(this, "Error al obtener URL de foto: ${e.message}", Toast.LENGTH_LONG).show()
                        showLoading(false)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error subiendo foto: ${e.message}", e)
                    Toast.makeText(this, "Error al subir foto: ${e.message}", Toast.LENGTH_LONG).show()
                    showLoading(false)
                }
        }
    }

    // ✨ ACTUALIZADO: Guardar petType en Firestore
    private fun registerAlert(address: String, petType: String, state: String, description: String, photoUrl: String?) {
        showLoading(true)

        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            showLoading(false)
            Log.e(TAG, "Usuario no autenticado al registrar")
            return
        }

        Log.d(TAG, "Registrando alerta en Firestore...")

        // Crear documento de alerta
        val alertData = hashMapOf(
            "lat" to latitude,
            "lng" to longitude,
            "address" to address,
            "petType" to petType, // ✨ NUEVO CAMPO
            "state" to state,
            "description" to description,
            "photoUrl" to photoUrl,
            "ownerUid" to user.uid,
            "ownerEmail" to user.email,
            "createdAt" to FieldValue.serverTimestamp(),
            "status" to "active"
        )

        Log.d(TAG, "Datos de alerta: $alertData")

        db.collection("alerts")
            .add(alertData)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "✅ Alerta registrada con ID: ${documentReference.id}")
                Toast.makeText(this, "¡Alerta registrada exitosamente! 🐾", Toast.LENGTH_LONG).show()
                showLoading(false)

                // Retornar a MapsActivity
                setResult(Activity.RESULT_OK)
                finish()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Error registrando alerta: ${e.message}", e)
                e.printStackTrace()
                Toast.makeText(this, "Error al registrar alerta: ${e.message}", Toast.LENGTH_LONG).show()
                showLoading(false)
            }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnRegister.isEnabled = !show
        btnCancel.isEnabled = !show
        layoutAddPhoto.isEnabled = !show
    }
}