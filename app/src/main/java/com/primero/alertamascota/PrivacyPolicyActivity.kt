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

class PrivacyPolicyActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var tvContactEmail: TextView
    private lateinit var btnUnderstand: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)

        // Configurar Toolbar
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Referencias
        tvContactEmail = findViewById(R.id.tvContactEmail)
        btnUnderstand = findViewById(R.id.btnUnderstand)

        // Click en email
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

            openEmailClient(email)
        }

        // Click en botón entendido
        btnUnderstand.setOnClickListener {
            Toast.makeText(
                this,
                "Gracias por revisar nuestra política de privacidad 🔒",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }

    private fun openEmailClient(email: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                putExtra(Intent.EXTRA_SUBJECT, "Consulta sobre Privacidad - Alerta Mascotas")
            }

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(Intent.createChooser(intent, "Enviar correo"))
            }
        } catch (e: Exception) {
            // Si falla, el email ya está copiado
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}