package com.primero.alertamascota

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class FeedbackActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "FeedbackActivity"
    }

    private lateinit var btnBack: ImageButton
    private lateinit var ratingBar: RatingBar
    private lateinit var tvRatingText: TextView
    private lateinit var chipGroup: ChipGroup
    private lateinit var chipSuggestion: Chip
    private lateinit var chipBug: Chip
    private lateinit var chipCompliment: Chip
    private lateinit var chipQuestion: Chip
    private lateinit var chipOther: Chip
    private lateinit var etFeedbackTitle: TextInputEditText
    private lateinit var etFeedbackMessage: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var btnSubmitFeedback: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutSuccess: LinearLayout
    private lateinit var btnBackToMenu: Button

    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback)

        initViews()
        setupListeners()
        loadUserEmail()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBackFeedback)
        ratingBar = findViewById(R.id.ratingBar)
        tvRatingText = findViewById(R.id.tvRatingText)
        chipGroup = findViewById(R.id.chipGroupCategory)
        chipSuggestion = findViewById(R.id.chipSuggestion)
        chipBug = findViewById(R.id.chipBug)
        chipCompliment = findViewById(R.id.chipCompliment)
        chipQuestion = findViewById(R.id.chipQuestion)
        chipOther = findViewById(R.id.chipOther)
        etFeedbackTitle = findViewById(R.id.etFeedbackTitle)
        etFeedbackMessage = findViewById(R.id.etFeedbackMessage)
        etEmail = findViewById(R.id.etEmail)
        btnSubmitFeedback = findViewById(R.id.btnSubmitFeedback)
        progressBar = findViewById(R.id.progressBarFeedback)
        layoutSuccess = findViewById(R.id.layoutSuccess)
        btnBackToMenu = findViewById(R.id.btnBackToMenu)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        ratingBar.setOnRatingBarChangeListener { _, rating, _ ->
            updateRatingText(rating)
        }

        btnSubmitFeedback.setOnClickListener {
            submitFeedback()
        }

        btnBackToMenu.setOnClickListener {
            finish()
        }
    }

    private fun loadUserEmail() {
        val currentUser = auth.currentUser
        if (currentUser != null && !currentUser.email.isNullOrEmpty()) {
            etEmail.setText(currentUser.email)
        }
    }

    private fun updateRatingText(rating: Float) {
        tvRatingText.text = when {
            rating == 0f -> "Toca las estrellas para calificar"
            rating <= 1f -> "😞 Muy malo"
            rating <= 2f -> "😕 Malo"
            rating <= 3f -> "😐 Regular"
            rating <= 4f -> "😊 Bueno"
            else -> "🤩 ¡Excelente!"
        }
    }

    private fun getSelectedCategory(): String {
        return when (chipGroup.checkedChipId) {
            R.id.chipSuggestion -> "Sugerencia"
            R.id.chipBug -> "Reporte de Error"
            R.id.chipCompliment -> "Felicitación"
            R.id.chipQuestion -> "Pregunta"
            R.id.chipOther -> "Otro"
            else -> "Sin categoría"
        }
    }

    private fun submitFeedback() {
        val title = etFeedbackTitle.text.toString().trim()
        val message = etFeedbackMessage.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val rating = ratingBar.rating
        val category = getSelectedCategory()

        // Validaciones
        if (rating == 0f) {
            Toast.makeText(this, "Por favor, califica tu experiencia", Toast.LENGTH_SHORT).show()
            ratingBar.requestFocus()
            return
        }

        if (chipGroup.checkedChipId == View.NO_ID) {
            Toast.makeText(this, "Por favor, selecciona una categoría", Toast.LENGTH_SHORT).show()
            return
        }

        if (title.isEmpty()) {
            etFeedbackTitle.error = "Ingresa un título"
            etFeedbackTitle.requestFocus()
            return
        }

        if (message.isEmpty()) {
            etFeedbackMessage.error = "Ingresa tu comentario"
            etFeedbackMessage.requestFocus()
            return
        }

        if (message.length < 10) {
            etFeedbackMessage.error = "El comentario debe tener al menos 10 caracteres"
            etFeedbackMessage.requestFocus()
            return
        }

        if (email.isEmpty()) {
            etEmail.error = "Ingresa tu email"
            etEmail.requestFocus()
            return
        }

        // Enviar a Firestore
        saveFeedbackToFirestore(title, message, email, rating, category)
    }

    private fun saveFeedbackToFirestore(
        title: String,
        message: String,
        email: String,
        rating: Float,
        category: String
    ) {
        showLoading(true)

        val currentUser = auth.currentUser
        val feedbackData = hashMapOf(
            "title" to title,
            "message" to message,
            "email" to email,
            "rating" to rating,
            "category" to category,
            "userId" to (currentUser?.uid ?: "anonymous"),
            "userName" to (currentUser?.displayName ?: "Anónimo"),
            "createdAt" to FieldValue.serverTimestamp(),
            "status" to "pending", // pending, reviewed, resolved
            "platform" to "Android"
        )

        db.collection("feedback")
            .add(feedbackData)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "Feedback guardado con ID: ${documentReference.id}")
                showLoading(false)
                showSuccessMessage()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error guardando feedback: ${e.message}", e)
                showLoading(false)
                Toast.makeText(
                    this,
                    "Error al enviar comentario: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnSubmitFeedback.isEnabled = !show
        btnSubmitFeedback.text = if (show) "Enviando..." else "📤 Enviar Comentario"
    }

    private fun showSuccessMessage() {
        // Ocultar formulario
        findViewById<ScrollView>(R.id.scrollViewFeedback).visibility = View.GONE

        // Mostrar mensaje de éxito
        layoutSuccess.visibility = View.VISIBLE

        // Limpiar campos (opcional)
        clearForm()
    }

    private fun clearForm() {
        etFeedbackTitle.text?.clear()
        etFeedbackMessage.text?.clear()
        ratingBar.rating = 0f
        chipGroup.clearCheck()
        updateRatingText(0f)
    }
}