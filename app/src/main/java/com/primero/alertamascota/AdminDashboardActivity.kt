package com.primero.alertamascota

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.*

class AdminDashboardActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AdminDashboard"
    }

    // ✅ LISTA DE EMAILS ADMINISTRADORES
    private val ADMIN_EMAILS = listOf(
        "alexiagonzales200516@gmail.com"
        // Agrega más emails admin aquí
    )

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var tvStats: TextView
    private lateinit var chipAll: Chip
    private lateinit var chipPending: Chip
    private lateinit var chipResolved: Chip
    private lateinit var btnLogout: ImageButton
    private lateinit var btnAdminChats: ImageButton // ✅ NUEVO
    private lateinit var tvAdminEmail: TextView
    private lateinit var searchView: SearchView

    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()
    private lateinit var googleSignInClient: GoogleSignInClient

    private var casesList = mutableListOf<AdminCaseItem>()
    private var filteredList = mutableListOf<AdminCaseItem>()
    private lateinit var adapter: AdminCasesAdapter
    private var currentFilter = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        // Verificar si es administrador
        verifyAdminAccess()

        // Configurar Google Sign-In para logout
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        initViews()
        setupRecyclerView()
        setupFilters()
        loadAllCases()
    }

    private fun verifyAdminAccess() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            goToLogin()
            return
        }

        val email = currentUser.email?.lowercase(Locale.getDefault()) ?: ""

        if (!ADMIN_EMAILS.contains(email)) {
            Toast.makeText(
                this,
                "⛔ No tienes permisos de administrador",
                Toast.LENGTH_LONG
            ).show()
            goToLogin()
        }
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerViewAdmin)
        progressBar = findViewById(R.id.progressBarAdmin)
        tvEmpty = findViewById(R.id.tvEmptyAdmin)
        tvStats = findViewById(R.id.tvAdminStats)
        chipAll = findViewById(R.id.chipAll)
        chipPending = findViewById(R.id.chipPending)
        chipResolved = findViewById(R.id.chipResolved)
        btnLogout = findViewById(R.id.btnAdminLogout)
        btnAdminChats = findViewById(R.id.btnAdminChats) // ✅ NUEVO
        tvAdminEmail = findViewById(R.id.tvAdminEmail)
        searchView = findViewById(R.id.searchViewAdmin)

        tvAdminEmail.text = "Admin: ${auth.currentUser?.email}"

        btnLogout.setOnClickListener {
            showLogoutDialog()
        }

        // ✅ NUEVO: Botón de chats
        btnAdminChats.setOnClickListener {
            openAdminChats()
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterCases(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterCases(newText)
                return true
            }
        })
    }

    private fun setupRecyclerView() {
        adapter = AdminCasesAdapter(filteredList) { caseItem ->
            openCaseManagement(caseItem)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupFilters() {
        chipAll.setOnClickListener {
            currentFilter = "all"
            updateChipSelection()
            applyFilter()
        }

        chipPending.setOnClickListener {
            currentFilter = "active"
            updateChipSelection()
            applyFilter()
        }

        chipResolved.setOnClickListener {
            currentFilter = "saved"
            updateChipSelection()
            applyFilter()
        }
    }

    private fun updateChipSelection() {
        chipAll.isChecked = currentFilter == "all"
        chipPending.isChecked = currentFilter == "active"
        chipResolved.isChecked = currentFilter == "saved"
    }

    private fun loadAllCases() {
        showLoading(true)

        db.collection("alerts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                showLoading(false)

                if (e != null) {
                    Log.w(TAG, "Error cargando casos", e)
                    Toast.makeText(this, "Error al cargar casos", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                casesList.clear()

                snapshots?.documents?.forEach { doc ->
                    val id = doc.id
                    val status = doc.getString("status") ?: "active"
                    val state = doc.getString("state") ?: "Desconocido"
                    val petType = doc.getString("petType") ?: "Desconocido"
                    val address = doc.getString("address") ?: "Sin dirección"
                    val photoUrl = doc.getString("photoUrl") ?: ""
                    val savedPhotoUrl = doc.getString("savedPhotoUrl") ?: ""
                    val savedNotes = doc.getString("savedNotes") ?: ""
                    val ownerEmail = doc.getString("ownerEmail") ?: "Desconocido"
                    val resolvedBy = doc.getString("resolvedBy") ?: ""
                    val createdAt = doc.getTimestamp("createdAt")
                    val resolvedAt = doc.getTimestamp("resolvedAt")
                    val lat = doc.getDouble("lat")
                    val lng = doc.getDouble("lng")

                    val caseItem = AdminCaseItem(
                        id = id,
                        code = "#${doc.id.takeLast(7)}",
                        status = status,
                        state = state,
                        petType = petType,
                        address = address,
                        originalPhotoUrl = photoUrl,
                        savedPhotoUrl = savedPhotoUrl,
                        savedNotes = savedNotes,
                        reportedBy = ownerEmail,
                        resolvedBy = resolvedBy,
                        createdAt = createdAt?.toDate(),
                        resolvedAt = resolvedAt?.toDate(),
                        lat = lat,
                        lng = lng
                    )
                    casesList.add(caseItem)
                }

                applyFilter()
                updateStats()

                Log.d(TAG, "Casos totales cargados: ${casesList.size}")
            }
    }

    private fun applyFilter() {
        filteredList.clear()

        when (currentFilter) {
            "all" -> filteredList.addAll(casesList)
            "active" -> filteredList.addAll(casesList.filter { it.status == "active" })
            "saved" -> filteredList.addAll(casesList.filter { it.status == "saved" })
        }

        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun filterCases(query: String?) {
        if (query.isNullOrEmpty()) {
            applyFilter()
            return
        }

        val searchQuery = query.lowercase(Locale.getDefault())
        filteredList.clear()

        val baseList = when (currentFilter) {
            "active" -> casesList.filter { it.status == "active" }
            "saved" -> casesList.filter { it.status == "saved" }
            else -> casesList
        }

        filteredList.addAll(baseList.filter {
            it.code.lowercase(Locale.getDefault()).contains(searchQuery) ||
                    it.state.lowercase(Locale.getDefault()).contains(searchQuery) ||
                    it.address.lowercase(Locale.getDefault()).contains(searchQuery) ||
                    it.reportedBy.lowercase(Locale.getDefault()).contains(searchQuery) ||
                    it.resolvedBy.lowercase(Locale.getDefault()).contains(searchQuery)
        })

        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun updateStats() {
        val total = casesList.size
        val active = casesList.count { it.status == "active" }
        val resolved = casesList.count { it.status == "saved" }

        tvStats.text = "📊 Total: $total | 🔔 Activos: $active | ✅ Resueltos: $resolved"
    }

    private fun updateEmptyState() {
        if (filteredList.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = when (currentFilter) {
                "active" -> "📋 No hay casos activos"
                "saved" -> "✅ No hay casos resueltos"
                else -> "📋 No hay casos registrados"
            }
            recyclerView.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun openCaseManagement(caseItem: AdminCaseItem) {
        val intent = Intent(this, CaseManagementActivity::class.java)
        intent.putExtra("CASE_ID", caseItem.id)
        intent.putExtra("CASE_CODE", caseItem.code)
        intent.putExtra("CASE_STATUS", caseItem.status)
        intent.putExtra("CASE_STATE", caseItem.state)
        intent.putExtra("CASE_ADDRESS", caseItem.address)
        intent.putExtra("REPORTED_BY", caseItem.reportedBy)
        intent.putExtra("RESOLVED_BY", caseItem.resolvedBy)
        startActivity(intent)
    }

    // ✅ NUEVA FUNCIÓN: Abrir lista de chats del admin
    private fun openAdminChats() {
        val intent = Intent(this, ChatsListActivity::class.java)
        startActivity(intent)
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Cerrar Sesión")
            .setMessage("¿Estás seguro que deseas cerrar sesión como administrador?")
            .setPositiveButton("Sí") { _, _ ->
                logout()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun logout() {
        auth.signOut()
        googleSignInClient.signOut().addOnCompleteListener {
            goToLogin()
        }
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // ==================== Data Class ====================
    data class AdminCaseItem(
        val id: String,
        val code: String,
        val status: String,
        val petType: String,
        val state: String,
        val address: String,
        val originalPhotoUrl: String,
        val savedPhotoUrl: String,
        val savedNotes: String,
        val reportedBy: String,
        val resolvedBy: String,
        val createdAt: Date?,
        val resolvedAt: Date?,
        val lat: Double?,
        val lng: Double?
    )

    // ==================== Adapter ====================
    inner class AdminCasesAdapter(
        private val items: List<AdminCaseItem>,
        private val onItemClick: (AdminCaseItem) -> Unit
    ) : RecyclerView.Adapter<AdminCasesAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivPhoto: CircleImageView = view.findViewById(R.id.ivAdminCasePhoto)
            val tvCode: TextView = view.findViewById(R.id.tvAdminCaseCode)
            val tvState: TextView = view.findViewById(R.id.tvAdminCaseState)
            val tvAddress: TextView = view.findViewById(R.id.tvAdminCaseAddress)
            val tvReportedBy: TextView = view.findViewById(R.id.tvAdminReportedBy)
            val tvResolvedBy: TextView = view.findViewById(R.id.tvAdminResolvedBy)
            val tvPetType: TextView = view.findViewById(R.id.tvAdminPetType)
            val tvDate: TextView = view.findViewById(R.id.tvAdminCaseDate)
            val chipStatus: Chip = view.findViewById(R.id.chipAdminStatus)
            val cardView: View = view.findViewById(R.id.cardAdminCase)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_admin_case, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]

            holder.tvCode.text = item.code
            holder.tvState.text = item.state
            holder.tvPetType.text = "🐾 ${item.petType}"
            holder.tvAddress.text = item.address
            holder.tvReportedBy.text = "Reportado por: ${item.reportedBy}"

            // Estado
            when (item.status) {
                "active" -> {
                    holder.chipStatus.text = "🔔 Activo"
                    holder.chipStatus.setChipBackgroundColorResource(android.R.color.holo_orange_light)
                    holder.tvResolvedBy.visibility = View.GONE
                }
                "saved" -> {
                    holder.chipStatus.text = "✅ Resuelto"
                    holder.chipStatus.setChipBackgroundColorResource(android.R.color.holo_green_light)
                    holder.tvResolvedBy.visibility = View.VISIBLE
                    holder.tvResolvedBy.text = "Resuelto por: ${item.resolvedBy}"
                }
            }

            // Fecha
            if (item.status == "saved" && item.resolvedAt != null) {
                holder.tvDate.text = "Resuelto ${getTimeAgo(item.resolvedAt)}"
            } else if (item.createdAt != null) {
                holder.tvDate.text = "Creado ${getTimeAgo(item.createdAt)}"
            }

            // Foto
            val photoUrl = if (item.status == "saved" && item.savedPhotoUrl.isNotEmpty()) {
                item.savedPhotoUrl
            } else {
                item.originalPhotoUrl
            }

            if (photoUrl.isNotEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(photoUrl)
                    .placeholder(R.drawable.ic_person_placeholder)
                    .into(holder.ivPhoto)
            } else {
                holder.ivPhoto.setImageResource(R.drawable.ic_person_placeholder)
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
                minutes < 1 -> "hace un momento"
                minutes < 60 -> "hace ${minutes.toInt()} min"
                hours < 24 -> "hace ${hours.toInt()} h"
                days < 7 -> "hace ${days.toInt()} días"
                else -> "el ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)}"
            }
        }
    }
}