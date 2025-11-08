package com.primero.alertamascota

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class NotificationService : Service() {

    companion object {
        private const val TAG = "NotificationService"
        private const val CHANNEL_ID = "pet_alerts_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 1
        private const val ALERT_RADIUS_KM = 3.0
        private const val PREFS_NAME = "NotificationPrefs"
    }

    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()
    private var alertsListener: ListenerRegistration? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var locationPrefs: SharedPreferences
    private val notifiedAlerts = mutableSetOf<String>() // Para evitar duplicados

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Servicio de notificaciones creado")
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        locationPrefs = getSharedPreferences("UserLocation", MODE_PRIVATE)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Servicio de notificaciones iniciado")

        // Verificar si las notificaciones están habilitadas
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", false)

        if (!notificationsEnabled) {
            Log.d(TAG, "Notificaciones deshabilitadas, deteniendo servicio")
            stopSelf()
            return START_NOT_STICKY
        }

        // Iniciar como servicio foreground
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification())

        // Escuchar nuevas alertas
        listenForNewAlerts()

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Canal de notificaciones creado")
        }
    }

    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, MapsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🐾 Alertas de Mascotas Activas")
            .setContentText("Buscando alertas cercanas...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun listenForNewAlerts() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "Usuario no autenticado")
            stopSelf()
            return
        }

        // Obtener ubicación del usuario
        val latitude = locationPrefs.getString("latitude", null)?.toDoubleOrNull()
        val longitude = locationPrefs.getString("longitude", null)?.toDoubleOrNull()

        if (latitude == null || longitude == null) {
            Log.w(TAG, "No hay ubicación del usuario guardada")
            return
        }

        Log.d(TAG, "Escuchando alertas cerca de: $latitude, $longitude")

        // Escuchar solo alertas activas
        alertsListener = db.collection("alerts")
            .whereEqualTo("status", "active")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "Error al escuchar alertas", e)
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val doc = change.document
                        val alertId = doc.id

                        // Evitar notificar alertas ya notificadas
                        if (notifiedAlerts.contains(alertId)) {
                            return@forEach
                        }

                        val lat = doc.getDouble("lat")
                        val lng = doc.getDouble("lng")
                        val state = doc.getString("state") ?: "Desconocido"
                        val petType = doc.getString("petType") ?: "Desconocido"
                        val ownerUid = doc.getString("ownerUid")
                        val address = doc.getString("address") ?: "Ubicación desconocida"

                        // No notificar mis propias alertas
                        if (ownerUid == currentUser.uid) {
                            notifiedAlerts.add(alertId)
                            return@forEach
                        }

                        if (lat != null && lng != null) {
                            // Calcular distancia
                            val distance = FloatArray(1)
                            Location.distanceBetween(
                                latitude,
                                longitude,
                                lat,
                                lng,
                                distance
                            )

                            val distanceKm = distance[0] / 1000

                            // Verificar si está dentro del radio
                            if (distanceKm <= ALERT_RADIUS_KM) {
                                // Verificar filtros del usuario
                                if (shouldNotify(petType, state)) {
                                    Log.d(TAG, "Nueva alerta cerca: $petType - $state a ${distanceKm}km")
                                    sendNotification(alertId, petType, state, distanceKm, address)
                                    notifiedAlerts.add(alertId)
                                } else {
                                    Log.d(TAG, "Alerta filtrada por preferencias: $petType - $state")
                                    notifiedAlerts.add(alertId)
                                }
                            }
                        }
                    }
                }
            }
    }

    private fun shouldNotify(petType: String, state: String): Boolean {
        // Verificar filtro de estados
        val notifyInjured = prefs.getBoolean("notify_injured", true)
        val notifyLost = prefs.getBoolean("notify_lost", true)
        val notifyAbandoned = prefs.getBoolean("notify_abandoned", true)
        val notifyDanger = prefs.getBoolean("notify_danger", true)
        val notifyAdoption = prefs.getBoolean("notify_adoption", true)
        val notifySick = prefs.getBoolean("notify_sick", true)

        val stateMatch = when (state) {
            "Herido" -> notifyInjured
            "Perdido" -> notifyLost
            "Abandonado" -> notifyAbandoned
            "En Peligro" -> notifyDanger
            "Necesita Adopción" -> notifyAdoption
            "Enfermo" -> notifySick
            else -> true
        }

        if (!stateMatch) return false

        // Verificar filtro de tipos de mascota
        val notifyDog = prefs.getBoolean("notify_dog", true)
        val notifyCat = prefs.getBoolean("notify_cat", true)
        val notifyBird = prefs.getBoolean("notify_bird", true)
        val notifyOther = prefs.getBoolean("notify_other", true)

        val petTypeMatch = when (petType) {
            "🐕 Perro" -> notifyDog
            "🐱 Gato" -> notifyCat
            "🐦 Ave" -> notifyBird
            "❓ Otro", "🐹 Roedor", "🐰 Conejo", "🐢 Reptil" -> notifyOther
            else -> notifyOther
        }

        return petTypeMatch
    }

    private fun sendNotification(alertId: String, petType: String, state: String, distanceKm: Float, address: String) {
        // Intent para abrir el detalle de la alerta
        val intent = Intent(this, AlertDetailActivity::class.java).apply {
            putExtra(AlertDetailActivity.EXTRA_ALERT_ID, alertId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            alertId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Emoji según el estado
        val stateEmoji = when (state) {
            "Herido" -> "🩹"
            "Perdido" -> "🔍"
            "Abandonado" -> "😢"
            "En Peligro" -> "⚠️"
            "Necesita Adopción" -> "🏠"
            "Enfermo" -> "🤒"
            else -> "🐾"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("$stateEmoji Nueva alerta cerca")
            .setContentText("$petType - $state")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$petType - $state\n📍 A %.1f km de ti\n$address".format(distanceKm)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(alertId.hashCode(), notification)

        Log.d(TAG, "Notificación enviada: $petType - $state")
    }

    override fun onDestroy() {
        super.onDestroy()
        alertsListener?.remove()
        Log.d(TAG, "Servicio de notificaciones detenido")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}