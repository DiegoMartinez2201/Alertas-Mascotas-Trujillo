package com.primero.alertamascota

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.*

class PublicProfileActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PublicProfileActivity"
        const val EXTRA_USER_EMAIL = "user_email"
    }

    private lateinit var progressBar: ProgressBar
    private lateinit var ivProfilePhoto: CircleImageView
    private lateinit var tvFullName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvBio: TextView
    private lateinit var tvMemberSince: TextView

    // Tabs/Botones para cambiar vista
    private lateinit var btnResolvedTab: androidx.cardview.widget.CardView
    private lateinit var btnReportedTab: androidx.cardview.widget.CardView
    private lateinit var tvResolvedCount: TextView
    private lateinit var tvReportedCount: TextView
    private lateinit var tvResolvedLabel: TextView
    private lateinit var tvReportedLabel: TextView

    private lateinit var recyclerViewCases: RecyclerView
    private lateinit var tvNoCases: TextView
    private lateinit var tvSectionTitle: TextView
    private lateinit var cardPhone: androidx.cardview.widget.CardView
    private lateinit var cardLocation: androidx.cardview.widget.CardView
    private lateinit var cardBio: androidx.cardview.widget.CardView

    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()
    private var userEmail: String = ""

    private var resolvedCases = mutableListOf<CaseItem>()
    private var reportedCases = mutableListOf<CaseItem>()
    private var currentCasesList = mutableListOf<CaseItem>()

    private lateinit var casesAdapter: UserCasesAdapter
    private var currentTab: String = "resolved" // "resolved" o "reported"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_public_profile)

        userEmail = intent.getStringExtra(EXTRA_USER_EMAIL) ?: ""

        if (userEmail.isEmpty()) {
            Toast.makeText(this, "Error: Email no proporcionado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupRecyclerView()
        setupTabListeners()
        loadUserProfile()
        loadUserCases()
    }

    private fun initViews() {
        progressBar = findViewById(R.id.progressBarProfile)
        ivProfilePhoto = findViewById(R.id.ivPublicProfilePhoto)
        tvFullName = findViewById(R.id.tvPublicFullName)
        tvEmail = findViewById(R.id.tvPublicEmail)
        tvPhone = findViewById(R.id.tvPublicPhone)
        tvLocation = findViewById(R.id.tvPublicLocation)
        tvBio = findViewById(R.id.tvPublicBio)
        tvMemberSince = findViewById(R.id.tvPublicMemberSince)

        // Tabs
        btnResolvedTab = findViewById(R.id.cardResolvedTab)
        btnReportedTab = findViewById(R.id.cardReportedTab)
        tvResolvedCount = findViewById(R.id.tvResolvedCount)
        tvReportedCount = findViewById(R.id.tvReportedCount)
        tvResolvedLabel = findViewById(R.id.tvResolvedLabel)
        tvReportedLabel = findViewById(R.id.tvReportedLabel)

        recyclerViewCases = findViewById(R.id.recyclerViewUserCases)
        tvNoCases = findViewById(R.id.tvNoCasesPublic)
        tvSectionTitle = findViewById(R.id.tvSectionTitle)
        cardPhone = findViewById(R.id.cardPhonePublic)
        cardLocation = findViewById(R.id.cardLocationPublic)
        cardBio = findViewById(R.id.cardBioPublic)

        findViewById<ImageButton>(R.id.btnBackPublicProfile).setOnClickListener {
            finish()
        }

        // Seleccionar tab "Resueltos" por defecto
        selectTab("resolved")
    }

    private fun setupRecyclerView() {
        casesAdapter = UserCasesAdapter(currentCasesList) { caseId ->
            // Abrir detalle del caso
            val intent = android.content.Intent(this, AlertDetailActivity::class.java)
            intent.putExtra(AlertDetailActivity.EXTRA_ALERT_ID, caseId)
            startActivity(intent)
        }
        recyclerViewCases.layoutManager = LinearLayoutManager(this)
        recyclerViewCases.adapter = casesAdapter
    }

    private fun setupTabListeners() {
        btnResolvedTab.setOnClickListener {
            selectTab("resolved")
            showResolvedCases()
        }

        btnReportedTab.setOnClickListener {
            selectTab("reported")
            showReportedCases()
        }
    }

    private fun selectTab(tab: String) {
        currentTab = tab

        if (tab == "resolved") {
            // Activar tab Resueltos
            btnResolvedTab.setCardBackgroundColor(getColor(R.color.tab_selected))
            btnReportedTab.setCardBackgroundColor(getColor(R.color.tab_unselected))

            tvResolvedCount.setTextColor(getColor(android.R.color.white))
            tvResolvedLabel.setTextColor(getColor(android.R.color.white))

            tvReportedCount.setTextColor(getColor(R.color.text_secondary))
            tvReportedLabel.setTextColor(getColor(R.color.text_secondary))

            tvSectionTitle.text = "Casos Resueltos Recientes 🎉"

        } else {
            // Activar tab Reportados
            btnReportedTab.setCardBackgroundColor(getColor(R.color.tab_selected_secondary))
            btnResolvedTab.setCardBackgroundColor(getColor(R.color.tab_unselected))

            tvReportedCount.setTextColor(getColor(android.R.color.white))
            tvReportedLabel.setTextColor(getColor(android.R.color.white))

            tvResolvedCount.setTextColor(getColor(R.color.text_secondary))
            tvResolvedLabel.setTextColor(getColor(R.color.text_secondary))

            tvSectionTitle.text = "Casos Reportados Recientes 📢"
        }
    }

    private fun showResolvedCases() {
        Log.d(TAG, "showResolvedCases() - Mostrando ${resolvedCases.size} casos")
        currentCasesList.clear()
        currentCasesList.addAll(resolvedCases)
        casesAdapter.notifyDataSetChanged()
        updateEmptyState()

        Log.d(TAG, "Lista actualizada. currentCasesList tiene ${currentCasesList.size} items")
    }

    private fun showReportedCases() {
        Log.d(TAG, "showReportedCases() - Mostrando ${reportedCases.size} casos")
        currentCasesList.clear()
        currentCasesList.addAll(reportedCases)
        casesAdapter.notifyDataSetChanged()
        updateEmptyState()

        Log.d(TAG, "Lista actualizada. currentCasesList tiene ${currentCasesList.size} items")
    }

    private fun loadUserProfile() {
        showLoading(true)

        db.collection("users")
            .whereEqualTo("email", userEmail)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // Usuario no tiene perfil completo, mostrar solo email
                    tvFullName.text = "Usuario sin perfil"
                    tvEmail.text = userEmail
                    cardPhone.visibility = View.GONE
                    cardLocation.visibility = View.GONE
                    cardBio.visibility = View.GONE
                    tvMemberSince.visibility = View.GONE
                    showLoading(false)
                    Log.d(TAG, "Usuario no tiene perfil completo")
                } else {
                    val document = documents.documents[0]

                    val firstName = document.getString("firstName") ?: ""
                    val lastName = document.getString("lastName") ?: ""
                    val phone = document.getString("phone") ?: ""
                    val city = document.getString("city") ?: ""
                    val country = document.getString("country") ?: ""
                    val bio = document.getString("bio") ?: ""
                    val photoUrl = document.getString("photoUrl") ?: ""
                    val createdAt = document.getTimestamp("createdAt")

                    // Nombre completo
                    if (firstName.isNotEmpty() || lastName.isNotEmpty()) {
                        tvFullName.text = "$firstName $lastName".trim()
                    } else {
                        tvFullName.text = "Usuario"
                    }

                    // Email
                    tvEmail.text = userEmail

                    // Foto de perfil
                    if (photoUrl.isNotEmpty()) {
                        Glide.with(this)
                            .load(photoUrl)
                            .placeholder(R.drawable.ic_person_placeholder)
                            .into(ivProfilePhoto)
                    }

                    // Teléfono
                    if (phone.isNotEmpty()) {
                        tvPhone.text = phone
                        cardPhone.visibility = View.VISIBLE
                    } else {
                        cardPhone.visibility = View.GONE
                    }

                    // Ubicación
                    if (city.isNotEmpty() || country.isNotEmpty()) {
                        val location = listOf(city, country).filter { it.isNotEmpty() }.joinToString(", ")
                        tvLocation.text = location
                        cardLocation.visibility = View.VISIBLE
                    } else {
                        cardLocation.visibility = View.GONE
                    }

                    // Bio
                    if (bio.isNotEmpty()) {
                        tvBio.text = bio
                        cardBio.visibility = View.VISIBLE
                    } else {
                        cardBio.visibility = View.GONE
                    }

                    // Fecha de registro
                    if (createdAt != null) {
                        val sdf = SimpleDateFormat("MMMM yyyy", Locale("es", "ES"))
                        tvMemberSince.text = "Miembro desde ${sdf.format(createdAt.toDate())}"
                        tvMemberSince.visibility = View.VISIBLE
                    } else {
                        tvMemberSince.visibility = View.GONE
                    }

                    showLoading(false)
                    Log.d(TAG, "Perfil cargado para: $userEmail")
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e(TAG, "Error cargando perfil", e)
                Toast.makeText(this, "Error al cargar perfil", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadUserCases() {
        val normalizedEmail = userEmail.lowercase(Locale.getDefault())

        Log.d(TAG, "=== INICIANDO CARGA DE CASOS ===")
        Log.d(TAG, "Email original: $userEmail")
        Log.d(TAG, "Email normalizado: $normalizedEmail")

        // Cargar casos resueltos
        Log.d(TAG, "Buscando casos resueltos...")
        db.collection("alerts")
            .whereEqualTo("status", "saved")
            .get()
            .addOnSuccessListener { documents ->
                resolvedCases.clear()

                Log.d(TAG, "Total de alertas con status='saved': ${documents.size()}")

                documents.forEach { doc ->
                    val resolvedBy = doc.getString("resolvedBy")
                    val resolvedByNormalized = resolvedBy?.lowercase(Locale.getDefault())

                    Log.d(TAG, "Alerta ${doc.id}:")
                    Log.d(TAG, "  - resolvedBy: $resolvedBy")
                    Log.d(TAG, "  - resolvedBy normalizado: $resolvedByNormalized")
                    Log.d(TAG, "  - Match: ${resolvedByNormalized == normalizedEmail}")

                    if (resolvedByNormalized == normalizedEmail) {
                        val item = CaseItem(
                            id = doc.id,
                            type = "resolved",
                            state = doc.getString("state") ?: "",
                            petType = doc.getString("petType") ?: "",
                            photoUrl = doc.getString("savedPhotoUrl") ?: doc.getString("photoUrl") ?: "",
                            date = doc.getTimestamp("resolvedAt")?.toDate(),
                            address = doc.getString("address") ?: ""
                        )
                        resolvedCases.add(item)
                        Log.d(TAG, "  ✓ Caso agregado: ${item.petType} - ${item.state}")
                    }
                }

                tvResolvedCount.text = "${resolvedCases.size}"

                Log.d(TAG, "TOTAL casos resueltos encontrados: ${resolvedCases.size}")

                // Si estamos en el tab de resueltos, actualizar la vista
                if (currentTab == "resolved") {
                    showResolvedCases()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "ERROR cargando casos resueltos: ${e.message}", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }

        // Cargar casos reportados
        Log.d(TAG, "Buscando casos reportados...")
        db.collection("alerts")
            .get()
            .addOnSuccessListener { documents ->
                reportedCases.clear()

                Log.d(TAG, "Total de alertas en Firestore: ${documents.size()}")

                documents.forEach { doc ->
                    val ownerEmail = doc.getString("ownerEmail")
                    val ownerEmailNormalized = ownerEmail?.lowercase(Locale.getDefault())

                    Log.d(TAG, "Alerta ${doc.id}:")
                    Log.d(TAG, "  - ownerEmail: $ownerEmail")
                    Log.d(TAG, "  - ownerEmail normalizado: $ownerEmailNormalized")
                    Log.d(TAG, "  - Match: ${ownerEmailNormalized == normalizedEmail}")

                    if (ownerEmailNormalized == normalizedEmail) {
                        val item = CaseItem(
                            id = doc.id,
                            type = "reported",
                            state = doc.getString("state") ?: "",
                            petType = doc.getString("petType") ?: "",
                            photoUrl = doc.getString("photoUrl") ?: "",
                            date = doc.getTimestamp("createdAt")?.toDate(),
                            address = doc.getString("address") ?: ""
                        )
                        reportedCases.add(item)
                        Log.d(TAG, "  ✓ Caso agregado: ${item.petType} - ${item.state}")
                    }
                }

                tvReportedCount.text = "${reportedCases.size}"

                Log.d(TAG, "TOTAL casos reportados encontrados: ${reportedCases.size}")

                // Si estamos en el tab de reportados, actualizar la vista
                if (currentTab == "reported") {
                    showReportedCases()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "ERROR cargando casos reportados: ${e.message}", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun updateEmptyState() {
        if (currentCasesList.isEmpty()) {
            tvNoCases.visibility = View.VISIBLE
            recyclerViewCases.visibility = View.GONE

            tvNoCases.text = if (currentTab == "resolved") {
                "No hay casos resueltos"
            } else {
                "No hay casos reportados"
            }
        } else {
            tvNoCases.visibility = View.GONE
            recyclerViewCases.visibility = View.VISIBLE
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    // Data classes
    data class CaseItem(
        val id: String,
        val type: String,
        val state: String,
        val petType: String,
        val photoUrl: String,
        val date: Date?,
        val address: String
    )

    // Adapter
    inner class UserCasesAdapter(
        private val items: List<CaseItem>,
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.Adapter<UserCasesAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivPhoto: CircleImageView = view.findViewById(R.id.ivCasePhotoSmall)
            val tvPetType: TextView = view.findViewById(R.id.tvCasePetTypeSmall)
            val tvState: TextView = view.findViewById(R.id.tvCaseStateSmall)
            val tvDate: TextView = view.findViewById(R.id.tvCaseDateSmall)
            val tvAddress: TextView = view.findViewById(R.id.tvCaseAddressSmall)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_user_case_small, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]

            holder.tvPetType.text = item.petType
            holder.tvState.text = item.state
            holder.tvAddress.text = item.address

            if (item.date != null) {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                holder.tvDate.text = sdf.format(item.date)
            } else {
                holder.tvDate.text = "Fecha desconocida"
            }

            if (item.photoUrl.isNotEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(item.photoUrl)
                    .placeholder(R.drawable.ic_person_placeholder)
                    .into(holder.ivPhoto)
            }

            holder.itemView.setOnClickListener {
                onItemClick(item.id)
            }
        }

        override fun getItemCount() = items.size
    }
}