package com.primero.alertamascota

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

class MyApplication : Application() {

    companion object {
        private const val TAG = "MyApplication"
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "========================================")
        Log.d(TAG, "🚀 Iniciando aplicación...")
        Log.d(TAG, "========================================")

        // Inicializar Firebase
        FirebaseApp.initializeApp(this)
        Log.d(TAG, "✅ Firebase inicializado")

        // Configurar Firestore con persistencia offline
        configureFirestore()
    }

    private fun configureFirestore() {
        try {
            val firestore = FirebaseFirestore.getInstance()

            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true) // ✅ Habilitar cache offline
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED) // Cache ilimitado
                .build()

            firestore.firestoreSettings = settings

            Log.d(TAG, "========================================")
            Log.d(TAG, "✅ Firestore configurado correctamente")
            Log.d(TAG, "   - Persistencia offline: HABILITADA")
            Log.d(TAG, "   - Tamaño de cache: ILIMITADO")
            Log.d(TAG, "========================================")

        } catch (e: IllegalStateException) {
            // Ya estaba configurado (puede pasar si se reinicia la app)
            Log.w(TAG, "⚠️ Firestore ya estaba configurado: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error configurando Firestore: ${e.message}", e)
        }
    }
}