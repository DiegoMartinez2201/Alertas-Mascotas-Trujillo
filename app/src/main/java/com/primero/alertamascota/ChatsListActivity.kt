package com.primero.alertamascota

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.*

class ChatsListActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ChatsListActivity"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView

    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()

    private var chatsList = mutableListOf<ChatItem>()
    private lateinit var adapter: ChatsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chats_list)

        initViews()
        setupRecyclerView()
        loadChats()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerViewChats)
        progressBar = findViewById(R.id.progressBarChats)
        tvEmpty = findViewById(R.id.tvEmptyChats)

        findViewById<ImageButton>(R.id.btnBackChats).setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = ChatsAdapter(chatsList) { chatItem ->
            openChat(chatItem)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadChats() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        showLoading(true)

        db.collection("chats")
            .whereArrayContains("participants", currentUser.email ?: "")
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                showLoading(false)

                if (e != null) {
                    Log.w(TAG, "Error cargando chats", e)
                    Toast.makeText(this, "Error al cargar chats", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                chatsList.clear()

                snapshots?.documents?.forEach { doc ->
                    val chatId = doc.id
                    val alertId = doc.getString("alertId") ?: ""
                    val alertCode = doc.getString("alertCode") ?: ""
                    val participants = doc.get("participants") as? List<String> ?: emptyList()
                    val lastMessage = doc.getString("lastMessage") ?: ""
                    val lastMessageAt = doc.getTimestamp("lastMessageAt")
                    val unreadCount = (doc.getLong("unreadCount_${currentUser.email}") ?: 0).toInt()

                    // Obtener el otro participante
                    val otherParticipant = participants.firstOrNull { it != currentUser.email } ?: "Desconocido"

                    val chatItem = ChatItem(
                        chatId = chatId,
                        alertId = alertId,
                        alertCode = alertCode,
                        otherUserEmail = otherParticipant,
                        lastMessage = lastMessage,
                        lastMessageAt = lastMessageAt?.toDate(),
                        unreadCount = unreadCount
                    )
                    chatsList.add(chatItem)
                }

                adapter.notifyDataSetChanged()
                updateEmptyState()

                Log.d(TAG, "Chats cargados: ${chatsList.size}")
            }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updateEmptyState() {
        if (chatsList.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = "💬 No tienes conversaciones aún"
            recyclerView.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun openChat(chatItem: ChatItem) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("CHAT_ID", chatItem.chatId)
        intent.putExtra("ALERT_ID", chatItem.alertId)
        intent.putExtra("ALERT_CODE", chatItem.alertCode)
        intent.putExtra("OTHER_USER_EMAIL", chatItem.otherUserEmail)
        startActivity(intent)
    }

    // ==================== Data Class ====================
    data class ChatItem(
        val chatId: String,
        val alertId: String,
        val alertCode: String,
        val otherUserEmail: String,
        val lastMessage: String,
        val lastMessageAt: Date?,
        val unreadCount: Int
    )

    // ==================== Adapter ====================
    inner class ChatsAdapter(
        private val items: List<ChatItem>,
        private val onItemClick: (ChatItem) -> Unit
    ) : RecyclerView.Adapter<ChatsAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivAvatar: CircleImageView = view.findViewById(R.id.ivChatAvatar)
            val tvUserEmail: TextView = view.findViewById(R.id.tvChatUserEmail)
            val tvAlertCode: TextView = view.findViewById(R.id.tvChatAlertCode)
            val tvLastMessage: TextView = view.findViewById(R.id.tvChatLastMessage)
            val tvTime: TextView = view.findViewById(R.id.tvChatTime)
            val tvUnreadBadge: TextView = view.findViewById(R.id.tvUnreadBadge)
            val cardView: View = view.findViewById(R.id.cardChat)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]

            holder.tvUserEmail.text = item.otherUserEmail
            holder.tvAlertCode.text = "Caso ${item.alertCode}"
            holder.tvLastMessage.text = item.lastMessage

            // Tiempo
            if (item.lastMessageAt != null) {
                holder.tvTime.text = getTimeAgo(item.lastMessageAt)
            } else {
                holder.tvTime.text = ""
            }

            // Badge de no leídos
            if (item.unreadCount > 0) {
                holder.tvUnreadBadge.visibility = View.VISIBLE
                holder.tvUnreadBadge.text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString()
            } else {
                holder.tvUnreadBadge.visibility = View.GONE
            }

            // Avatar por defecto
            holder.ivAvatar.setImageResource(R.drawable.ic_person_placeholder)

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
                minutes < 1 -> "Ahora"
                minutes < 60 -> "${minutes.toInt()}m"
                hours < 24 -> "${hours.toInt()}h"
                days < 7 -> "${days.toInt()}d"
                else -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(date)
            }
        }
    }
}