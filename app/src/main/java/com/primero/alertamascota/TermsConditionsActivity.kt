package com.primero.alertamascota

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton

class TermsConditionsActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var tvContactEmail: TextView
    private lateinit var btnAcceptTerms: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terms_conditions)

        // Configurar Toolbar
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Referencias
        tvContactEmail = findViewById(R.id.tvContactEmail)
        btnAcceptTerms = findViewById(R.id.btnAcceptTerms)

        // Click en email - Copiar al portapapeles
        tvContactEmail.setOnClickListener {
            val email = tvContactEmail.text.toString()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Email", email)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(
                this,
                "📋 Correo copiado: $email",
                Toast.LENGTH_SHORT
            ).show()

            // Opcionalmente, abrir cliente de correo
            openEmailClient(email)
        }

        // Click en botón de aceptación
        btnAcceptTerms.setOnClickListener {
            // Guardar que el usuario aceptó los términos
            val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("terms_accepted", true).apply()

            Toast.makeText(
                this,
                "✅ Términos aceptados correctamente",
                Toast.LENGTH_SHORT
            ).show()

            // Volver a la pantalla anterior
            finish()
        }
    }

    private fun openEmailClient(email: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                putExtra(Intent.EXTRA_SUBJECT, "Consulta sobre Alerta Mascotas Trujillo")
            }

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(Intent.createChooser(intent, "Enviar correo"))
            }
        } catch (e: Exception) {
            // Si falla, no hacer nada (ya se copió al portapapeles)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}