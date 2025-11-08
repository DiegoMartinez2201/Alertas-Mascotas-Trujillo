package com.primero.alertamascota

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class NoConnectionActivity : AppCompatActivity() {

    private lateinit var btnRetry: MaterialButton
    private lateinit var btnSettings: MaterialButton
    private lateinit var tvStatus: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var isChecking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_no_connection)

        initViews()
        setupListeners()
    }

    private fun initViews() {
        btnRetry = findViewById(R.id.btnRetry)
        btnSettings = findViewById(R.id.btnSettings)
        tvStatus = findViewById(R.id.tvConnectionStatus)
    }

    private fun setupListeners() {
        btnRetry.setOnClickListener {
            checkConnectionAndProceed()
        }

        btnSettings.setOnClickListener {
            openNetworkSettings()
        }
    }

    private fun checkConnectionAndProceed() {
        if (isChecking) return

        isChecking = true
        tvStatus.text = "Verificando conexión..."
        btnRetry.isEnabled = false
        btnRetry.text = "Verificando..."

        handler.postDelayed({
            if (NetworkUtils.isNetworkAvailable(this)) {
                tvStatus.text = "✅ Conexión restablecida"

                // Esperar un momento y volver a la app
                handler.postDelayed({
                    finishAndGoBack()
                }, 500)
            } else {
                tvStatus.text = "❌ Sin conexión a Internet"
                btnRetry.isEnabled = true
                btnRetry.text = "🔄 Reintentar"
                isChecking = false
            }
        }, 1500)
    }

    private fun openNetworkSettings() {
        try {
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun finishAndGoBack() {
        setResult(RESULT_OK)
        finish()
    }

    override fun onResume() {
        super.onResume()
        // Verificar automáticamente cuando el usuario vuelva de configuración
        handler.postDelayed({
            if (NetworkUtils.isNetworkAvailable(this)) {
                finishAndGoBack()
            }
        }, 1000)
    }

    override fun onBackPressed() {
        // No permitir volver atrás sin conexión
        checkConnectionAndProceed()
    }
}