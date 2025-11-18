package com.primero.alertamascota

import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.*

class LostFoundActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LostFoundActivity"
        private const val ALERT_RADIUS_KM =3.0 // Radio de 10 km
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var chipGroup: ChipGroup
    private lateinit var chipActive: Chip

    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var alertsList = mutableListOf<AlertItem>()
    private lateinit var adapter: AlertsAdapter
    private var currentUserLocation: Location? = null
    private var selectedFilter = "active"

    private lateinit var locationCallback: LocationCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lost_found)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Configurar LocationCallback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val oldLocation = currentUserLocation
                    currentUserLocation = location
                    adapter.updateUserLocation(location)

                    // Recargar si se movió más de 500m
                    if (oldLocation != null) {
                        val distance = FloatArray(1)
                        Location.distanceBetween(
                            oldLocation.latitude,
                            oldLocation.longitude,
                            location.latitude,
                            location.longitude,
                            distance
                        )
                        if (distance[0] > 500) {
                            Log.d(TAG, "Usuario se movió, recargando alertas")
                            loadAlerts()
                        }
                    }
                }
            }
        }

        initViews()
        setupRecyclerView()
        setupFilters()
        getUserLocation()
        startLocationUpdates()
        loadAlerts()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        tvEmpty = findViewById(R.id.tvEmpty)
        chipGroup = findViewById(R.id.chipGroup)
        chipActive = findViewById(R.id.chipActive)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = AlertsAdapter(alertsList) { alertItem ->
            openAlertDetail(alertItem.id)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupFilters() {
        chipActive.isChecked = true
        selectedFilter = "active"
    }

    private fun getUserLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                currentUserLocation = location
                if (location != null) {
                    Log.d(TAG, "Ubicación obtenida: ${location.latitude}, ${location.longitude}")
                    adapter.updateUserLocation(location)
                    loadAlerts() // Recargar con ubicación
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error obteniendo ubicación: ${e.message}")
        }
    }

    private fun startLocationUpdates() {
        try {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                30000 // 30 segundos
            ).apply {
                setMinUpdateIntervalMillis(15000)
                setMaxUpdateDelayMillis(60000)
            }.build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Error al solicitar actualizaciones de ubicación", e)
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun loadAlerts() {
        showLoading(true)

        var query: Query = db.collection("alerts")
            .orderBy("createdAt", Query.Direction.DESCENDING)

        when (selectedFilter) {
            "active" -> query = query.whereEqualTo("status", "active")
            "saved" -> query = query.whereEqualTo("status", "saved")
        }

        query.addSnapshotListener { snapshots, e ->
            showLoading(false)

            if (e != null) {
                Log.w(TAG, "Error cargando alertas", e)
                Toast.makeText(this, "Error al cargar alertas", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            alertsList.clear()

            val userLocation = currentUserLocation
            var alertsInRadius = 0
            var totalAlerts = 0

            snapshots?.documents?.forEach { doc ->
                totalAlerts++
                val id = doc.id
                val state = doc.getString("state") ?: "Desconocido"
                val address = doc.getString("address") ?: "Sin dirección"
                val petType = doc.getString("petType") ?: "Desconocido"
                val photoUrl = doc.getString("photoUrl") ?: ""
                val status = doc.getString("status") ?: "active"
                val lat = doc.getDouble("lat") ?: 0.0
                val lng = doc.getDouble("lng") ?: 0.0
                val createdAt = doc.getTimestamp("createdAt")

                // Filtrar por radio si hay ubicación del usuario
                var shouldAdd = true
                if (userLocation != null) {
                    val distance = FloatArray(1)
                    Location.distanceBetween(
                        userLocation.latitude,
                        userLocation.longitude,
                        lat,
                        lng,
                        distance
                    )

                    val distanceKm = distance[0] / 1000
                    shouldAdd = distanceKm <= ALERT_RADIUS_KM
                }

                if (shouldAdd) {
                    alertsInRadius++
                    val alertItem = AlertItem(
                        id = id,
                        code = "#${doc.id.takeLast(7)}",
                        state = state,
                        petType = petType,
                        address = address,
                        photoUrl = photoUrl,
                        status = status,
                        lat = lat,
                        lng = lng,
                        createdAt = createdAt?.toDate()
                    )
                    alertsList.add(alertItem)
                }
            }

            adapter.notifyDataSetChanged()
            updateEmptyState()

            Log.d(TAG, "Alertas mostradas: $alertsInRadius de $totalAlerts (dentro de ${ALERT_RADIUS_KM}km)")

            if (userLocation != null && alertsInRadius < totalAlerts) {
                Toast.makeText(
                    this,
                    "Mostrando $alertsInRadius alertas cercanas (${ALERT_RADIUS_KM}km)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updateEmptyState() {
        if (alertsList.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = if (currentUserLocation != null) {
                "No hay alertas cerca de tu ubicación\n(Radio de ${ALERT_RADIUS_KM}km)"
            } else {
                "Esperando ubicación..."
            }
            recyclerView.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun openAlertDetail(alertId: String) {
        val intent = Intent(this, AlertDetailActivity::class.java)
        intent.putExtra(AlertDetailActivity.EXTRA_ALERT_ID, alertId)
        startActivity(intent)
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onResume() {
        super.onResume()
        startLocationUpdates()
    }

    // ==================== Data Class ====================
    data class AlertItem(
        val id: String,
        val code: String,
        val state: String,
        val petType: String,
        val address: String,
        val photoUrl: String,
        val status: String,
        val lat: Double,
        val lng: Double,
        val createdAt: Date?
    )

    // ==================== Adapter ====================
    inner class AlertsAdapter(
        private val items: List<AlertItem>,
        private val onItemClick: (AlertItem) -> Unit
    ) : RecyclerView.Adapter<AlertsAdapter.ViewHolder>() {

        private var userLocation: Location? = null

        fun updateUserLocation(location: Location) {
            userLocation = location
            notifyDataSetChanged()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivPhoto: CircleImageView = view.findViewById(R.id.ivAlertPhoto)
            val tvCode: TextView = view.findViewById(R.id.tvAlertCode)
            val tvState: TextView = view.findViewById(R.id.tvAlertState)
            val tvPetType: TextView = view.findViewById(R.id.tvAlertPetType)

            val tvAddress: TextView = view.findViewById(R.id.tvAlertAddress)
            val tvDistance: TextView = view.findViewById(R.id.tvDistance)
            val tvTime: TextView = view.findViewById(R.id.tvTime)
            val btnStatus: Button = view.findViewById(R.id.btnStatus)
            val cardView: View = view.findViewById(R.id.cardAlert)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_alert, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]

            // Código
            holder.tvCode.text = item.code

            // Estado
            holder.tvState.text = "${item.state} - ${item.address}"
            holder.tvPetType.text = item.petType
            holder.tvState.text = "${item.petType} - ${item.state}"
            // Dirección
            holder.tvAddress.text = item.address

            // Foto
            if (item.photoUrl.isNotEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(item.photoUrl)
                    .placeholder(R.drawable.ic_person_placeholder)
                    .into(holder.ivPhoto)
            } else {
                holder.ivPhoto.setImageResource(R.drawable.ic_person_placeholder)
            }

            // Distancia
            if (userLocation != null) {
                val distance = FloatArray(1)
                Location.distanceBetween(
                    userLocation!!.latitude,
                    userLocation!!.longitude,
                    item.lat,
                    item.lng,
                    distance
                )
                val distanceKm = distance[0] / 1000
                holder.tvDistance.text = "%.1f km".format(distanceKm)
            } else {
                holder.tvDistance.text = "-- km"
            }

            // Tiempo transcurrido
            if (item.createdAt != null) {
                holder.tvTime.text = getTimeAgo(item.createdAt)
            } else {
                holder.tvTime.text = "Hace un momento"
            }

            // Estado del botón
            when (item.status) {
                "active" -> {
                    holder.btnStatus.text = "URGENTE"
                    holder.btnStatus.setBackgroundColor(
                        holder.itemView.context.getColor(android.R.color.holo_red_light)
                    )
                }
                "saved" -> {
                    holder.btnStatus.text = "SALVADO"
                    holder.btnStatus.setBackgroundColor(
                        holder.itemView.context.getColor(android.R.color.holo_green_light)
                    )
                }
            }

            // Click
            holder.cardView.setOnClickListener {
                onItemClick(item)
            }
        }

        override fun getItemCount() = items.size

        private fun getTimeAgo(date: Date): String {
            val diff = System.currentTimeMillis() - date.time
            val minutes = diff / (60 * 1000)
            val hours = diff / (60 * 60 * 1000)
            val days = diff / (24 * 60 * 60 * 1000)

            return when {
                minutes < 1 -> "Hace un momento"
                minutes < 60 -> "Hace ${minutes.toInt()} min"
                hours < 24 -> "Hace ${hours.toInt()} h"
                days < 7 -> "Hace ${days.toInt()} días"
                else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
            }
        }
    }
}