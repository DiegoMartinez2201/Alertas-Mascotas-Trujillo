package com.primero.alertamascota

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.*

class MyCasesActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MyCasesActivity"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var tvCounter: TextView

    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()

    private var casesList = mutableListOf<ResolvedCaseItem>()
    private lateinit var adapter: ResolvedCasesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_cases)

        initViews()
        setupRecyclerView()
        loadMyCases()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerViewCases)
        progressBar = findViewById(R.id.progressBarCases)
        tvEmpty = findViewById(R.id.tvEmptyCases)
        tvCounter = findViewById(R.id.tvCasesCounter)

        findViewById<ImageButton>(R.id.btnBackCases).setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = ResolvedCasesAdapter(casesList) { caseItem ->
            openCaseDetail(caseItem.id)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadMyCases() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val userEmail = currentUser.email?.lowercase(Locale.getDefault()) ?: ""

        if (userEmail.isEmpty()) {
            Toast.makeText(this, "No se pudo obtener el email del usuario", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d(TAG, "Buscando casos resueltos por: $userEmail")
        showLoading(true)

        // Buscar por email normalizado (lowercase)
        db.collection("alerts")
            .whereEqualTo("status", "saved")
            .orderBy("resolvedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                showLoading(false)

                if (e != null) {
                    Log.w(TAG, "Error cargando casos resueltos", e)
                    Toast.makeText(this, "Error al cargar tus casos", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                casesList.clear()

                snapshots?.documents?.forEach { doc ->
                    val resolvedBy = doc.getString("resolvedBy")?.lowercase(Locale.getDefault()) ?: ""

                    // Filtrar solo los casos resueltos por este usuario
                    if (resolvedBy == userEmail) {
                        val id = doc.id
                        val state = doc.getString("state") ?: "Desconocido"
                        val address = doc.getString("address") ?: "Sin dirección"
                        val petType = doc.getString("petType") ?: "Desconocido"
                        val photoUrl = doc.getString("photoUrl") ?: ""
                        val savedPhotoUrl = doc.getString("savedPhotoUrl") ?: ""
                        val savedNotes = doc.getString("savedNotes") ?: ""
                        val resolvedAt = doc.getTimestamp("resolvedAt")
                        val createdAt = doc.getTimestamp("createdAt")
                        val ownerEmail = doc.getString("ownerEmail") ?: "Desconocido"

                        val caseItem = ResolvedCaseItem(
                            id = id,
                            code = "#${doc.id.takeLast(7)}",
                            state = state,
                            petType = petType,
                            address = address,
                            originalPhotoUrl = photoUrl,
                            savedPhotoUrl = savedPhotoUrl,
                            savedNotes = savedNotes,
                            resolvedAt = resolvedAt?.toDate(),
                            createdAt = createdAt?.toDate(),
                            reportedBy = ownerEmail
                        )
                        casesList.add(caseItem)
                    }
                }

                adapter.notifyDataSetChanged()
                updateEmptyState()
                updateCounter()

                Log.d(TAG, "Casos resueltos cargados: ${casesList.size}")
            }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updateEmptyState() {
        if (casesList.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = "🐾 Aún no has resuelto ningún caso\n\n¡Sal a ayudar mascotas!"
            recyclerView.visibility = View.GONE
            tvCounter.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            tvCounter.visibility = View.VISIBLE
        }
    }

    private fun updateCounter() {
        tvCounter.text = "Has salvado ${casesList.size} mascota${if (casesList.size != 1) "s" else ""} 🎉"
    }

    private fun openCaseDetail(alertId: String) {
        val intent = Intent(this, AlertDetailActivity::class.java)
        intent.putExtra(AlertDetailActivity.EXTRA_ALERT_ID, alertId)
        startActivity(intent)
    }

    // ==================== Data Class ====================
    data class ResolvedCaseItem(
        val id: String,
        val code: String,
        val state: String,
        val petType: String,
        val address: String,
        val originalPhotoUrl: String,
        val savedPhotoUrl: String,
        val savedNotes: String,
        val resolvedAt: Date?,
        val createdAt: Date?,
        val reportedBy: String
    )

    // ==================== Adapter ====================
    inner class ResolvedCasesAdapter(
        private val items: List<ResolvedCaseItem>,
        private val onItemClick: (ResolvedCaseItem) -> Unit
    ) : RecyclerView.Adapter<ResolvedCasesAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivOriginalPhoto: CircleImageView = view.findViewById(R.id.ivOriginalPhoto)
            val ivSavedPhoto: CircleImageView = view.findViewById(R.id.ivSavedPhotoSmall)
            val tvCode: TextView = view.findViewById(R.id.tvCaseCode)
            val tvState: TextView = view.findViewById(R.id.tvCaseState)
            val tvPetType: TextView = view.findViewById(R.id.tvCasePetType)

            val tvAddress: TextView = view.findViewById(R.id.tvCaseAddress)
            val tvResolvedDate: TextView = view.findViewById(R.id.tvResolvedDate)
            val tvReportedBy: TextView = view.findViewById(R.id.tvReportedBy)
            val tvNotes: TextView = view.findViewById(R.id.tvSavedNotes)
            val cardView: View = view.findViewById(R.id.cardResolvedCase)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_resolved_cases, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvPetType.text = item.petType
            holder.tvCode.text = item.code
            holder.tvState.text = item.state
            holder.tvAddress.text = item.address
            holder.tvReportedBy.text = "Reportado por: ${item.reportedBy}"

            // Fecha de resolución
            if (item.resolvedAt != null) {
                holder.tvResolvedDate.text = "Salvado ${getTimeAgo(item.resolvedAt)}"
            } else {
                holder.tvResolvedDate.text = "Salvado recientemente"
            }

            // Notas
            if (item.savedNotes.isNotEmpty()) {
                holder.tvNotes.visibility = View.VISIBLE
                holder.tvNotes.text = "📝 ${item.savedNotes}"
            } else {
                holder.tvNotes.visibility = View.GONE
            }

            // Foto original
            if (item.originalPhotoUrl.isNotEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(item.originalPhotoUrl)
                    .placeholder(R.drawable.ic_person_placeholder)
                    .into(holder.ivOriginalPhoto)
            } else {
                holder.ivOriginalPhoto.setImageResource(R.drawable.ic_person_placeholder)
            }

            // Foto de salvamento
            if (item.savedPhotoUrl.isNotEmpty()) {
                holder.ivSavedPhoto.visibility = View.VISIBLE
                Glide.with(holder.itemView.context)
                    .load(item.savedPhotoUrl)
                    .placeholder(R.drawable.ic_person_placeholder)
                    .into(holder.ivSavedPhoto)
            } else {
                holder.ivSavedPhoto.visibility = View.GONE
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