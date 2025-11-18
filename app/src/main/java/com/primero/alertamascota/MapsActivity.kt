package com.primero.alertamascota

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.content.Context
import android.location.Location
import android.net.Network
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.android.material.button.MaterialButton
import com.google.android.gms.maps.model.BitmapDescriptor
import android.graphics.Bitmap
import android.graphics.Canvas

class MapsActivity : AppCompatActivity(), OnMapReadyCallback,
    NavigationView.OnNavigationItemSelectedListener {

    companion object {
        private const val TAG = "MapsActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val ALERT_RADIUS_KM = 3.0 // Radio de 3 km
        private const val REQUEST_CONNECTION_CHECK = 9999
    }

    private lateinit var mMap: GoogleMap
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private lateinit var noConnectionLayout: View

    // UI Elements
    private lateinit var fabCreateAlert: FloatingActionButton
    private lateinit var fabCancel: FloatingActionButton
    private lateinit var tvInstruction: TextView
    private lateinit var tvUserEmail: TextView
    private lateinit var btnMenu: ImageButton
    private lateinit var notificationSwitch: SwitchCompat
    private lateinit var btnFilters: ImageButton
    private lateinit var cardFilters: androidx.cardview.widget.CardView
    private lateinit var chipGroupPetType: com.google.android.material.chip.ChipGroup
    private lateinit var chipGroupState: com.google.android.material.chip.ChipGroup
    private lateinit var btnClearFilters: MaterialButton
    private lateinit var btnApplyFilters: MaterialButton
    private lateinit var tvFilterCount: TextView
    private var selectedPetTypes = mutableSetOf<String>()
    private var selectedStates = mutableSetOf<String>()
    private var isFilterPanelVisible = false

    // ✨ NUEVA: Leyenda expandible
    private lateinit var cardLegend: androidx.cardview.widget.CardView
    private lateinit var legendContent: LinearLayout
    private lateinit var ivLegendArrow: ImageView
    private var isLegendExpanded = false

    // ✨ NUEVA: Barra de iconos de alertas
    private lateinit var cardAlertLegend: androidx.cardview.widget.CardView
    private lateinit var alertLegendContent: LinearLayout
    private lateinit var ivAlertLegendArrow: ImageView
    private var isAlertLegendExpanded = false

    // SharedPreferences para notificaciones
    private lateinit var prefs: SharedPreferences
    private var notificationServiceIntent: Intent? = null

    // Estado para modo de selección
    private var isSelectionMode = false

    // Ubicación del usuario
    private var currentUserLocation: Location? = null
    private var radiusCircle: Circle? = null

    // LocationCallback para actualizaciones de ubicación
    private lateinit var locationCallback: LocationCallback

    // Firestore
    private val db = Firebase.firestore

    // Launcher para el resultado del formulario de alertas
    private val alertFormLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            loadAlertsInRadius()
        }
    }

    // Launcher para el detalle de alertas
    private val alertDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            loadAlertsInRadius()
        }
    }

    // Launcher para permisos de notificaciones (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startNotificationService()
            Toast.makeText(this, "Notificaciones activadas", Toast.LENGTH_SHORT).show()
        } else {
            notificationSwitch.isChecked = false
            Toast.makeText(this, "Permiso de notificaciones denegado", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!NetworkUtils.isNetworkAvailable(this)) {
            goToNoConnection()
            return
        }
        setContentView(R.layout.activity_maps)
        setupNetworkMonitoring()

        // Inicializar Firebase Auth
        auth = FirebaseAuth.getInstance()

        if (auth.currentUser == null) {
            goToLogin()
            return
        }

        // Inicializar FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Inicializar SharedPreferences
        prefs = getSharedPreferences("NotificationPrefs", MODE_PRIVATE)

        // Configurar LocationCallback para actualizaciones
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val oldLocation = currentUserLocation
                    currentUserLocation = location

                    // Guardar ubicación para el servicio de notificaciones
                    saveUserLocation(location)

                    // Actualizar círculo de radio
                    updateRadiusCircle(location)

                    // Recargar alertas solo si el usuario se movió significativamente (más de 500m)
                    if (oldLocation != null) {
                        val distance = FloatArray(1)
                        Location.distanceBetween(
                            oldLocation.latitude,
                            oldLocation.longitude,
                            location.latitude,
                            location.longitude,
                            distance
                        )
                        if (distance[0] > 500) { // 500 metros
                            Log.d(TAG, "Usuario se movió ${distance[0]}m, recargando alertas")
                            loadAlertsInRadius()
                        }
                    }
                }
            }
        }

        // Configurar Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // UI Elements
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        tvUserEmail = findViewById(R.id.tvUserEmail)
        fabCreateAlert = findViewById(R.id.fabCreateMarker)
        fabCancel = findViewById(R.id.fabCancel)
        tvInstruction = findViewById(R.id.tvInstruction)
        btnMenu = findViewById(R.id.btnMenu)
        btnFilters = findViewById(R.id.btnFilters)
        cardFilters = findViewById(R.id.cardFilters)
        chipGroupPetType = findViewById(R.id.chipGroupPetType)
        chipGroupState = findViewById(R.id.chipGroupState)
        btnClearFilters = findViewById(R.id.btnClearFilters)
        btnApplyFilters = findViewById(R.id.btnApplyFilters)
        tvFilterCount = findViewById(R.id.tvFilterCount)

        // ✨ NUEVA: Inicializar leyenda
        cardLegend = findViewById(R.id.cardLegend)
        legendContent = findViewById(R.id.legendContent)
        ivLegendArrow = findViewById(R.id.ivLegendArrow)

        // ✨ NUEVA: Inicializar barra de iconos
        cardAlertLegend = findViewById(R.id.cardAlertLegend)
        alertLegendContent = findViewById(R.id.alertLegendContent)
        ivAlertLegendArrow = findViewById(R.id.ivAlertLegendArrow)

        setupFilters()
        setupLegend()
        setupAlertLegend()

        navigationView.setNavigationItemSelectedListener(this)

        val headerView = navigationView.getHeaderView(0)
        val navHeaderEmail = headerView.findViewById<TextView>(R.id.navHeaderEmail)
        navHeaderEmail.text = auth.currentUser?.email ?: "Usuario"

        tvUserEmail.text = auth.currentUser?.email ?: "Usuario"

        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        fabCreateAlert.setOnClickListener {
            activateSelectionMode()
        }

        fabCancel.setOnClickListener {
            deactivateSelectionMode()
        }

        // Configurar el switch de notificaciones
        val menuItem = navigationView.menu.findItem(R.id.nav_notifications)
        val actionView = menuItem.actionView
        notificationSwitch = actionView?.findViewById(R.id.switchNotifications)
            ?: throw IllegalStateException("Switch no encontrado")

        // Cargar estado guardado
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", false)
        notificationSwitch.isChecked = notificationsEnabled

        if (notificationsEnabled) {
            startNotificationService()
        }

        // Listener del switch
        notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications_enabled", isChecked).apply()

            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Android 13+ requiere permiso explícito
                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        startNotificationService()
                        Toast.makeText(this, "Notificaciones activadas", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    startNotificationService()
                    Toast.makeText(this, "Notificaciones activadas", Toast.LENGTH_SHORT).show()
                }
            } else {
                stopNotificationService()
                Toast.makeText(this, "Notificaciones desactivadas", Toast.LENGTH_SHORT).show()
            }
        }

        // Preparar mapa
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val oldLocation = currentUserLocation
                    currentUserLocation = location

                    // Guardar ubicación para el servicio de notificaciones
                    saveUserLocation(location)

                    // Actualizar círculo de radio
                    updateRadiusCircle(location)

                    // Recargar alertas solo si el usuario se movió significativamente (más de 500m)
                    if (oldLocation != null) {
                        val distance = FloatArray(1)
                        Location.distanceBetween(
                            oldLocation.latitude,
                            oldLocation.longitude,
                            location.latitude,
                            location.longitude,
                            distance
                        )
                        if (distance[0] > 500) { // 500 metros
                            Log.d(TAG, "Usuario se movió ${distance[0]}m, recargando alertas")
                            loadAlertsInRadius()
                        }
                    }
                }
            }
        }
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun setupNetworkMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    super.onLost(network)
                    runOnUiThread {
                        showConnectionLostDialog()
                    }
                }

                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    runOnUiThread {
                        Toast.makeText(
                            this@MapsActivity,
                            "✅ Conexión restablecida",
                            Toast.LENGTH_SHORT
                        ).show()
                        loadAlertsInRadius()
                    }
                }
            }

            connectivityManager.registerDefaultNetworkCallback(networkCallback as ConnectivityManager.NetworkCallback)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CONNECTION_CHECK) {
            if (resultCode == RESULT_OK) {
                // Conexión restablecida, recargar
                loadAlertsInRadius()
            }
        }
    }

    private fun showConnectionLostDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("⚠️ Sin Conexión")
            .setMessage("Se perdió la conexión a Internet. Algunas funciones pueden no estar disponibles.")
            .setPositiveButton("Reintentar") { _, _ ->
                if (NetworkUtils.isNetworkAvailable(this)) {
                    Toast.makeText(this, "Conexión disponible", Toast.LENGTH_SHORT).show()
                    loadAlertsInRadius()
                } else {
                    goToNoConnection()
                }
            }
            .setNegativeButton("Cerrar", null)
            .setCancelable(false)
            .show()
    }

    private fun goToNoConnection() {
        val intent = Intent(this, NoConnectionActivity::class.java)
        startActivityForResult(intent, REQUEST_CONNECTION_CHECK)
    }

    private fun setupFilters() {
        btnFilters.setOnClickListener {
            toggleFilterPanel()
        }

        btnClearFilters.setOnClickListener {
            clearFilters()
        }

        btnApplyFilters.setOnClickListener {
            applyFilters()
            toggleFilterPanel()
        }
    }

    // ✨ NUEVA: Configurar leyenda expandible
    private fun setupLegend() {
        cardLegend.setOnClickListener {
            toggleLegend()
        }
    }

    private fun toggleLegend() {
        isLegendExpanded = !isLegendExpanded

        if (isLegendExpanded) {
            // Expandir
            legendContent.visibility = View.VISIBLE
            ivLegendArrow.animate()
                .rotation(0f)
                .setDuration(300)
                .start()
        } else {
            // Contraer
            legendContent.visibility = View.GONE
            ivLegendArrow.animate()
                .rotation(180f)
                .setDuration(300)
                .start()
        }
    }

    // ✨ NUEVA: Configurar barra de iconos de alertas
    private fun setupAlertLegend() {
        cardAlertLegend.setOnClickListener {
            toggleAlertLegend()
        }
    }

    private fun toggleAlertLegend() {
        isAlertLegendExpanded = !isAlertLegendExpanded

        if (isAlertLegendExpanded) {
            // Expandir (lado izquierdo, flecha apunta hacia la izquierda)
            alertLegendContent.visibility = View.VISIBLE
            ivAlertLegendArrow.animate()
                .rotation(180f)
                .setDuration(300)
                .start()
        } else {
            // Contraer (flecha apunta hacia la derecha)
            alertLegendContent.visibility = View.GONE
            ivAlertLegendArrow.animate()
                .rotation(0f)
                .setDuration(300)
                .start()
        }
    }

    private fun toggleFilterPanel() {
        isFilterPanelVisible = !isFilterPanelVisible
        cardFilters.visibility = if (isFilterPanelVisible) View.VISIBLE else View.GONE

        // Cambiar color del botón
        btnFilters.setColorFilter(
            if (isFilterPanelVisible)
                getColor(android.R.color.holo_blue_dark)
            else
                getColor(android.R.color.darker_gray)
        )
    }

    private fun clearFilters() {
        chipGroupPetType.clearCheck()
        chipGroupState.clearCheck()

        findViewById<com.google.android.material.chip.Chip>(R.id.chipAllPets).isChecked = true
        findViewById<com.google.android.material.chip.Chip>(R.id.chipAllStates).isChecked = true

        selectedPetTypes.clear()
        selectedStates.clear()

        loadAlertsInRadius()
    }

    private fun applyFilters() {
        selectedPetTypes.clear()
        selectedStates.clear()

        // Obtener tipos de mascota seleccionados
        val chipAllPets = findViewById<com.google.android.material.chip.Chip>(R.id.chipAllPets)
        if (!chipAllPets.isChecked) {
            if (findViewById<com.google.android.material.chip.Chip>(R.id.chipDog).isChecked) {
                selectedPetTypes.add("🐕 Perro")
            }
            if (findViewById<com.google.android.material.chip.Chip>(R.id.chipCat).isChecked) {
                selectedPetTypes.add("🐱 Gato")
            }
            if (findViewById<com.google.android.material.chip.Chip>(R.id.chipBird).isChecked) {
                selectedPetTypes.add("🐦 Ave")
            }
            if (findViewById<com.google.android.material.chip.Chip>(R.id.chipOther).isChecked) {
                selectedPetTypes.add("❓ Otro")
            }
        }

        // Obtener estados seleccionados
        val chipAllStates = findViewById<com.google.android.material.chip.Chip>(R.id.chipAllStates)
        if (!chipAllStates.isChecked) {
            if (findViewById<com.google.android.material.chip.Chip>(R.id.chipInjured).isChecked) {
                selectedStates.add("Herido")
            }
            if (findViewById<com.google.android.material.chip.Chip>(R.id.chipLost).isChecked) {
                selectedStates.add("Perdido")
            }
            if (findViewById<com.google.android.material.chip.Chip>(R.id.chipAbandoned).isChecked) {
                selectedStates.add("Abandonado")
            }
            if (findViewById<com.google.android.material.chip.Chip>(R.id.chipDanger).isChecked) {
                selectedStates.add("En Peligro")
            }
            if (findViewById<com.google.android.material.chip.Chip>(R.id.chipAdoption).isChecked) {
                selectedStates.add("Necesita Adopción")
            }
            if (findViewById<com.google.android.material.chip.Chip>(R.id.chipSick).isChecked) {
                selectedStates.add("Enfermo")
            }
        }

        loadAlertsInRadius()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_notification_settings -> {
                val intent = Intent(this, NotificationSettingsActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_account_info -> {
                val intent = Intent(this, UserProfileActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_lost_found -> {
                val intent = Intent(this, LostFoundActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_miscasosresueltos -> {
                val intent = Intent(this, MyCasesActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_feedback -> {
                val intent = Intent(this, FeedbackActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_terms -> {
                val intent = Intent(this, TermsConditionsActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_privacy -> {
                val intent = Intent(this, PrivacyPolicyActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_my_chats -> {
                val intent = Intent(this, ChatsListActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_sign_out -> {
                signOut()
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun activateSelectionMode() {
        isSelectionMode = true
        fabCreateAlert.visibility = View.GONE
        fabCancel.visibility = View.VISIBLE
        tvInstruction.visibility = View.VISIBLE
        tvInstruction.text = "🐾 Toca el mapa donde encontraste la mascota"
        Toast.makeText(this, "Toca el mapa para registrar una alerta", Toast.LENGTH_SHORT).show()
    }

    private fun deactivateSelectionMode() {
        isSelectionMode = false
        fabCreateAlert.visibility = View.VISIBLE
        fabCancel.visibility = View.GONE
        tvInstruction.visibility = View.GONE
    }

    private fun signOut() {
        auth.signOut()
        googleSignInClient.signOut().addOnCompleteListener {
            goToLogin()
        }
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (checkLocationPermission()) {
            enableMyLocation()
        } else {
            requestLocationPermission()
        }

        // Click en el mapa
        mMap.setOnMapClickListener { latLng ->
            if (!isSelectionMode) return@setOnMapClickListener

            val intent = Intent(this, AlertFormActivity::class.java)
            intent.putExtra(AlertFormActivity.EXTRA_LATITUDE, latLng.latitude)
            intent.putExtra(AlertFormActivity.EXTRA_LONGITUDE, latLng.longitude)
            alertFormLauncher.launch(intent)
            deactivateSelectionMode()
        }

        // Click en marcador
        mMap.setOnMarkerClickListener { marker ->
            val alertId = marker.tag as? String
            if (alertId != null) {
                openAlertDetail(alertId)
                true
            } else {
                false
            }
        }
    }

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun enableMyLocation() {
        if (!checkLocationPermission()) {
            return
        }

        try {
            mMap.isMyLocationEnabled = true
            mMap.uiSettings.isMyLocationButtonEnabled = true

            // Obtener ubicación inicial
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    currentUserLocation = location

                    // Guardar ubicación para el servicio de notificaciones
                    saveUserLocation(location)

                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 14f))

                    // Dibujar círculo de radio
                    updateRadiusCircle(location)

                    // Cargar alertas cercanas
                    loadAlertsInRadius()

                    Log.d(TAG, "Ubicación inicial: ${location.latitude}, ${location.longitude}")
                } else {
                    Log.w(TAG, "No se pudo obtener ubicación")
                    useDefaultLocation()
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "Error obteniendo ubicación", e)
                useDefaultLocation()
            }

            // Iniciar actualizaciones de ubicación
            startLocationUpdates()

        } catch (e: SecurityException) {
            Log.e(TAG, "Error de seguridad: ${e.message}")
        }
    }

    private fun startLocationUpdates() {
        if (!checkLocationPermission()) return

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            30000 // Actualizar cada 30 segundos
        ).apply {
            setMinUpdateIntervalMillis(15000) // Mínimo 15 segundos
            setMaxUpdateDelayMillis(60000)
        }.build()

        try {
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

    private fun updateRadiusCircle(location: Location) {
        // Remover círculo anterior
        radiusCircle?.remove()

        // Dibujar nuevo círculo (opcional, para visualizar el radio)
        val circleOptions = CircleOptions()
            .center(LatLng(location.latitude, location.longitude))
            .radius(ALERT_RADIUS_KM * 1000) // Convertir km a metros
            .strokeColor(0x220000FF) // Azul semi-transparente
            .fillColor(0x110000FF)
            .strokeWidth(2f)

        radiusCircle = mMap.addCircle(circleOptions)
    }

    private fun useDefaultLocation() {
        val defaultLocation = LatLng(-12.046374, -77.042793)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 12f))
        Toast.makeText(this, "No se pudo obtener tu ubicación", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    enableMyLocation()
                    Toast.makeText(this, "Permiso de ubicación concedido", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this,
                        "Permiso denegado. No podrás ver alertas cercanas.",
                        Toast.LENGTH_LONG
                    ).show()
                    useDefaultLocation()
                }
            }
        }
    }

    private fun clearMapMarkers() {
        if (::mMap.isInitialized) {
            mMap.clear()
            // Redibujar el círculo de radio si existe ubicación
            currentUserLocation?.let { updateRadiusCircle(it) }
        }
    }

    private fun loadAlertsInRadius() {
        val userLocation = currentUserLocation
        if (userLocation == null) {
            Log.w(TAG, "No hay ubicación del usuario, no se pueden cargar alertas")
            Toast.makeText(this, "Esperando ubicación...", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("alerts")
            .whereEqualTo("status", "active")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "Error al cargar alertas", e)
                    return@addSnapshotListener
                }

                clearMapMarkers()
                var alertsInRadius = 0
                var alertsFiltered = 0

                snapshots?.documents?.forEach { doc ->
                    val lat = doc.getDouble("lat")
                    val lng = doc.getDouble("lng")
                    val state = doc.getString("state") ?: "Desconocido"
                    val petType = doc.getString("petType") ?: "Desconocido"
                    val ownerEmail = doc.getString("ownerEmail") ?: "Desconocido"

                    if (lat != null && lng != null) {
                        // Calcular distancia
                        val distance = FloatArray(1)
                        Location.distanceBetween(
                            userLocation.latitude,
                            userLocation.longitude,
                            lat,
                            lng,
                            distance
                        )

                        val distanceKm = distance[0] / 1000

                        // Solo mostrar si está dentro del radio
                        if (distanceKm <= ALERT_RADIUS_KM) {
                            alertsInRadius++

                            // Aplicar filtros
                            val passesFilter = applyFilterLogic(petType, state)

                            if (passesFilter) {
                                alertsFiltered++
                                val pos = LatLng(lat, lng)

                                val customIcon = getMarkerIconForState(state)

                                val marker = mMap.addMarker(
                                    MarkerOptions()
                                        .position(pos)
                                        .title("$petType - $state 🐾")
                                        .snippet("A %.1f km • Reportado por: $ownerEmail".format(distanceKm))
                                        .icon(customIcon)
                                )

                                marker?.tag = doc.id
                            }
                        }
                    }
                }

                // Actualizar contador
                updateFilterCount(alertsFiltered, alertsInRadius)

                Log.d(TAG, "Alertas mostradas: $alertsFiltered de $alertsInRadius en radio de ${ALERT_RADIUS_KM}km")

                if (alertsFiltered == 0 && alertsInRadius > 0) {
                    Toast.makeText(
                        this,
                        "No hay alertas que coincidan con los filtros",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun applyFilterLogic(petType: String, state: String): Boolean {
        // Si no hay filtros activos, mostrar todo
        if (selectedPetTypes.isEmpty() && selectedStates.isEmpty()) {
            return true
        }

        // Verificar tipo de mascota
        val petTypeMatch = if (selectedPetTypes.isEmpty()) {
            true
        } else {
            selectedPetTypes.contains(petType)
        }

        // Verificar estado
        val stateMatch = if (selectedStates.isEmpty()) {
            true
        } else {
            selectedStates.contains(state)
        }

        // Debe cumplir ambos criterios (AND)
        return petTypeMatch && stateMatch
    }

    private fun updateFilterCount(filtered: Int, total: Int) {
        val filterText = if (selectedPetTypes.isEmpty() && selectedStates.isEmpty()) {
            "Mostrando $filtered alertas"
        } else {
            "Mostrando $filtered de $total alertas"
        }
        tvFilterCount.text = filterText
    }

    private fun openAlertDetail(alertId: String) {
        // Mostrar Bottom Sheet en lugar de abrir la actividad directamente
        val bottomSheet = AlertPreviewBottomSheet.newInstance(alertId)
        bottomSheet.show(supportFragmentManager, "AlertPreviewBottomSheet")
    }

    private fun startNotificationService() {
        notificationServiceIntent = Intent(this, NotificationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(notificationServiceIntent)
        } else {
            startService(notificationServiceIntent)
        }
    }

    private fun stopNotificationService() {
        notificationServiceIntent?.let {
            stopService(it)
        }
    }

    private fun saveUserLocation(location: Location) {
        // Guardar ubicación en SharedPreferences para el servicio de notificaciones
        val locationPrefs = getSharedPreferences("UserLocation", MODE_PRIVATE)
        locationPrefs.edit().apply {
            putString("latitude", location.latitude.toString())
            putString("longitude", location.longitude.toString())
            apply()
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onResume() {
        super.onResume()

        // Sincronizar el switch con las preferencias guardadas
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", false)
        notificationSwitch.isChecked = notificationsEnabled

        // Reanudar actualizaciones de ubicación
        if (checkLocationPermission() && ::mMap.isInitialized) {
            startLocationUpdates()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        // Si las notificaciones están desactivadas, detener el servicio
        if (!prefs.getBoolean("notifications_enabled", false)) {
            stopNotificationService()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && networkCallback != null) {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.unregisterNetworkCallback(networkCallback as ConnectivityManager.NetworkCallback)
        }
    }

    private fun getMarkerIconForState(state: String): BitmapDescriptor {
        val iconResource = when (state) {
            "Herido" -> R.drawable.ic_pin_injured
            "Perdido" -> R.drawable.ic_pin_lost
            "Abandonado" -> R.drawable.ic_pin_abandoned
            "En Peligro" -> R.drawable.ic_pin_danger
            "Necesita Adopción" -> R.drawable.ic_pin_adoption
            "Enfermo" -> R.drawable.ic_pin_sick
            else -> R.drawable.ic_pin_default
        }

        // Convertir el recurso drawable a BitmapDescriptor
        return bitmapDescriptorFromVector(iconResource)
    }

    private fun bitmapDescriptorFromVector(vectorResId: Int): BitmapDescriptor {
        val vectorDrawable = ContextCompat.getDrawable(this, vectorResId)
            ?: return BitmapDescriptorFactory.defaultMarker()

        // Tamaño del pin (ajusta si lo necesitas más grande o pequeño)
        val width = 96   // Ancho del pin
        val height = 128 // Alto del pin (más alto porque tiene la punta)

        vectorDrawable.setBounds(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}