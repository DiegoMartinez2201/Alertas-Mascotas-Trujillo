package com.primero.alertamascota

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import de.hdodenhof.circleimageview.CircleImageView

class UserProfileActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "UserProfileActivity"
    }

    private lateinit var auth: FirebaseAuth
    private val db = Firebase.firestore
    private val storage = FirebaseStorage.getInstance()

    // UI Elements
    private lateinit var progressBar: ProgressBar
    private lateinit var ivProfilePhoto: CircleImageView
    private lateinit var btnChangePhoto: ImageButton
    private lateinit var tvTitle: TextView
    private lateinit var etFirstName: TextInputEditText
    private lateinit var etLastName: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var etAddress: TextInputEditText
    private lateinit var etCity: TextInputEditText
    private lateinit var etCountry: TextInputEditText
    private lateinit var etBio: TextInputEditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var tvEmail: TextView
    private lateinit var tvMemberSince: TextView

    private var profilePhotoUri: Uri? = null
    private var currentPhotoUrl: String? = null
    private var isEditing = false

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            profilePhotoUri = result.data?.data
            if (profilePhotoUri != null) {
                Glide.with(this)
                    .load(profilePhotoUri)
                    .placeholder(R.drawable.ic_person_placeholder)
                    .into(ivProfilePhoto)
                Log.d(TAG, "Foto de perfil seleccionada: $profilePhotoUri")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        auth = FirebaseAuth.getInstance()

        if (auth.currentUser == null) {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupListeners()
        loadUserProfile()
    }

    private fun initViews() {
        progressBar = findViewById(R.id.progressBar)
        ivProfilePhoto = findViewById(R.id.ivProfilePhoto)
        btnChangePhoto = findViewById(R.id.btnChangePhoto)
        tvTitle = findViewById(R.id.tvTitle)
        etFirstName = findViewById(R.id.etFirstName)
        etLastName = findViewById(R.id.etLastName)
        etPhone = findViewById(R.id.etPhone)
        etAddress = findViewById(R.id.etAddress)
        etCity = findViewById(R.id.etCity)
        etCountry = findViewById(R.id.etCountry)
        etBio = findViewById(R.id.etBio)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)
        tvEmail = findViewById(R.id.tvEmail)
        tvMemberSince = findViewById(R.id.tvMemberSince)

        tvEmail.text = auth.currentUser?.email ?: "Sin email"
    }

    private fun setupListeners() {
        btnChangePhoto.setOnClickListener {
            openImagePicker()
        }

        ivProfilePhoto.setOnClickListener {
            openImagePicker()
        }

        btnSave.setOnClickListener {
            saveUserProfile()
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        pickImageLauncher.launch(intent)
    }

    private fun loadUserProfile() {
        showLoading(true)

        val userId = auth.currentUser?.uid ?: return

        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                showLoading(false)

                if (document.exists()) {
                    isEditing = true
                    tvTitle.text = "Mi Perfil"
                    btnSave.text = "Actualizar"

                    // Cargar datos
                    etFirstName.setText(document.getString("firstName") ?: "")
                    etLastName.setText(document.getString("lastName") ?: "")
                    etPhone.setText(document.getString("phone") ?: "")
                    etAddress.setText(document.getString("address") ?: "")
                    etCity.setText(document.getString("city") ?: "")
                    etCountry.setText(document.getString("country") ?: "")
                    etBio.setText(document.getString("bio") ?: "")

                    // Cargar foto de perfil
                    currentPhotoUrl = document.getString("photoUrl")
                    if (!currentPhotoUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(currentPhotoUrl)
                            .placeholder(R.drawable.ic_person_placeholder)
                            .into(ivProfilePhoto)
                    }

                    // Mostrar fecha de registro
                    val createdAt = document.getTimestamp("createdAt")
                    if (createdAt != null) {
                        val sdf = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale("es", "ES"))
                        tvMemberSince.text = "Miembro desde ${sdf.format(createdAt.toDate())}"
                    }

                    Log.d(TAG, "Perfil cargado")
                } else {
                    isEditing = false
                    tvTitle.text = "Crear Perfil"
                    btnSave.text = "Guardar"
                    tvMemberSince.visibility = View.GONE

                    Log.d(TAG, "Nuevo usuario")
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e(TAG, "Error: ${e.message}", e)
                Toast.makeText(this, "Error al cargar perfil", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveUserProfile() {
        val firstName = etFirstName.text.toString().trim()
        val lastName = etLastName.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val address = etAddress.text.toString().trim()
        val city = etCity.text.toString().trim()
        val country = etCountry.text.toString().trim()
        val bio = etBio.text.toString().trim()

        if (firstName.isEmpty()) {
            etFirstName.error = "Campo requerido"
            etFirstName.requestFocus()
            return
        }

        if (lastName.isEmpty()) {
            etLastName.error = "Campo requerido"
            etLastName.requestFocus()
            return
        }

        // Si hay nueva foto, subirla primero
        if (profilePhotoUri != null) {
            uploadPhotoAndSaveProfile(firstName, lastName, phone, address, city, country, bio)
        } else {
            saveProfileData(firstName, lastName, phone, address, city, country, bio, currentPhotoUrl)
        }
    }

    private fun uploadPhotoAndSaveProfile(
        firstName: String, lastName: String, phone: String,
        address: String, city: String, country: String, bio: String
    ) {
        showLoading(true)

        val userId = auth.currentUser?.uid ?: return
        val photoRef = storage.reference
            .child("profile_photos")
            .child("$userId.jpg")

        profilePhotoUri?.let { uri ->
            photoRef.putFile(uri)
                .addOnSuccessListener {
                    photoRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        saveProfileData(firstName, lastName, phone, address, city, country, bio, downloadUri.toString())
                    }
                }
                .addOnFailureListener { e ->
                    showLoading(false)
                    Log.e(TAG, "Error subiendo foto: ${e.message}", e)
                    Toast.makeText(this, "Error al subir foto", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun saveProfileData(
        firstName: String, lastName: String, phone: String,
        address: String, city: String, country: String, bio: String, photoUrl: String?
    ) {
        showLoading(true)

        val userId = auth.currentUser?.uid ?: return
        val userEmail = auth.currentUser?.email ?: ""

        val userData = hashMapOf(
            "firstName" to firstName,
            "lastName" to lastName,
            "phone" to phone,
            "address" to address,
            "city" to city,
            "country" to country,
            "bio" to bio,
            "email" to userEmail,
            "photoUrl" to (photoUrl ?: ""),
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        if (!isEditing) {
            userData["createdAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
        }

        db.collection("users")
            .document(userId)
            .set(userData)
            .addOnSuccessListener {
                showLoading(false)
                Toast.makeText(this, "Perfil guardado exitosamente", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e(TAG, "Error: ${e.message}", e)
                Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnSave.isEnabled = !show
        btnCancel.isEnabled = !show
        btnChangePhoto.isEnabled = !show
    }
}