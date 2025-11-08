package com.primero.alertamascota

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.*


class ChatActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ChatActivity"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnAttach: ImageButton
    private lateinit var tvOtherUser: TextView
    private lateinit var tvAlertCode: TextView
    private lateinit var progressBar: ProgressBar

    private val db = Firebase.firestore
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var chatId: String? = null
    private var alertId: String? = null
    private var alertCode: String? = null
    private var otherUserEmail: String? = null

    private var messagesList = mutableListOf<MessageItem>()
    private lateinit var adapter: MessagesAdapter

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                uploadFileAndSendMessage(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        testStorageConnection() // ← Agregar esta línea

        chatId = intent.getStringExtra("CHAT_ID")
        alertId = intent.getStringExtra("ALERT_ID")
        alertCode = intent.getStringExtra("ALERT_CODE")
        otherUserEmail = intent.getStringExtra("OTHER_USER_EMAIL")

        if (alertId.isNullOrEmpty() || otherUserEmail.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Datos del chat inválidos", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupRecyclerView()
        setupListeners()

        // ✅ MEJORADO: Buscar chat existente antes de crear uno nuevo
        if (chatId.isNullOrEmpty()) {
            findOrCreateChat()
        } else {
            loadMessages()
            markMessagesAsRead()
        }
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerViewMessages)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        btnAttach = findViewById(R.id.btnAttach)
        tvOtherUser = findViewById(R.id.tvChatOtherUser)
        tvAlertCode = findViewById(R.id.tvChatAlertCode)
        progressBar = findViewById(R.id.progressBarChat)

        tvOtherUser.text = otherUserEmail
        tvAlertCode.text = "Caso $alertCode"

        findViewById<ImageButton>(R.id.btnBackChat).setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = MessagesAdapter(messagesList, auth.currentUser?.email ?: "")
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        btnSend.setOnClickListener {
            sendTextMessage()
        }

        btnAttach.setOnClickListener {
            showAttachmentOptions()
        }
    }
    private fun testStorageConnection() {
        Log.d(TAG, "🧪 === TEST DE STORAGE ===")

        val testRef = storage.reference.child("test/test.txt")
        val testData = "Hola Firebase".toByteArray()

        testRef.putBytes(testData)
            .addOnSuccessListener {
                Log.d(TAG, "✅ ¡Storage funciona correctamente!")
                Toast.makeText(this, "✅ Storage OK", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Storage falló: ${e.message}")
                if (e is com.google.firebase.storage.StorageException) {
                    Log.e(TAG, "❌ Error Code: ${e.errorCode}")
                    Log.e(TAG, "❌ HTTP Code: ${e.httpResultCode}")
                }
                Toast.makeText(this, "❌ Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
    // ✅ NUEVA FUNCIÓN: Buscar chat existente o crear uno nuevo
    private fun findOrCreateChat() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        showLoading(true)

        // Buscar si ya existe un chat entre estos usuarios para esta alerta
        db.collection("chats")
            .whereEqualTo("alertId", alertId)
            .whereArrayContains("participants", currentUser.email ?: "")
            .get()
            .addOnSuccessListener { documents ->
                var existingChat: String? = null

                // Verificar si existe un chat con ambos participantes
                for (doc in documents) {
                    val participants = doc.get("participants") as? List<String> ?: emptyList()
                    if (participants.contains(otherUserEmail)) {
                        existingChat = doc.id
                        break
                    }
                }

                if (existingChat != null) {
                    // ✅ Usar el chat existente
                    chatId = existingChat
                    Log.d(TAG, "Chat existente encontrado: $chatId")
                    showLoading(false)
                    loadMessages()
                    markMessagesAsRead()
                } else {
                    // ✅ Crear nuevo chat
                    createChat()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error buscando chat", e)
                showLoading(false)
                // Si falla la búsqueda, intentar crear uno nuevo
                createChat()
            }
    }

    private fun createChat() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        showLoading(true)

        val chatData = hashMapOf(
            "alertId" to alertId,
            "alertCode" to alertCode,
            "participants" to listOf(currentUser.email, otherUserEmail),
            "createdAt" to FieldValue.serverTimestamp(),
            "lastMessage" to "",
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "unreadCount_${currentUser.email}" to 0,
            "unreadCount_$otherUserEmail" to 0
        )

        db.collection("chats")
            .add(chatData)
            .addOnSuccessListener { documentReference ->
                chatId = documentReference.id
                Log.d(TAG, "Chat creado: $chatId")
                showLoading(false)
                loadMessages()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e(TAG, "Error creando chat", e)
                Toast.makeText(this, "Error al crear chat", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadMessages() {
        if (chatId.isNullOrEmpty()) return

        db.collection("chats")
            .document(chatId!!)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "Error cargando mensajes", e)
                    return@addSnapshotListener
                }

                messagesList.clear()

                snapshots?.documents?.forEach { doc ->
                    val messageId = doc.id
                    val senderEmail = doc.getString("senderEmail") ?: ""
                    val messageText = doc.getString("text") ?: ""
                    val messageType = doc.getString("type") ?: "text"
                    val fileUrl = doc.getString("fileUrl") ?: ""
                    val fileName = doc.getString("fileName") ?: ""
                    val timestamp = doc.getTimestamp("timestamp")

                    val message = MessageItem(
                        messageId = messageId,
                        senderEmail = senderEmail,
                        text = messageText,
                        type = messageType,
                        fileUrl = fileUrl,
                        fileName = fileName,
                        timestamp = timestamp?.toDate()
                    )
                    messagesList.add(message)
                }

                adapter.notifyDataSetChanged()
                if (messagesList.isNotEmpty()) {
                    recyclerView.scrollToPosition(messagesList.size - 1)
                }
            }
    }

    private fun sendTextMessage() {
        val messageText = etMessage.text.toString().trim()
        if (messageText.isEmpty()) return

        sendMessage(messageText, "text", null, null)
        etMessage.text.clear()
    }

    private fun sendMessage(text: String, type: String, fileUrl: String?, fileName: String?) {
        if (chatId.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Chat no inicializado", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        val messageData = hashMapOf(
            "senderEmail" to currentUser.email,
            "text" to text,
            "type" to type,
            "fileUrl" to (fileUrl ?: ""),
            "fileName" to (fileName ?: ""),
            "timestamp" to FieldValue.serverTimestamp()
        )

        db.collection("chats")
            .document(chatId!!)
            .collection("messages")
            .add(messageData)
            .addOnSuccessListener {
                updateLastMessage(text)
                Log.d(TAG, "Mensaje enviado")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error enviando mensaje", e)
                Toast.makeText(this, "Error al enviar mensaje", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateLastMessage(text: String) {
        if (chatId.isNullOrEmpty()) return

        val currentUser = auth.currentUser ?: return

        db.collection("chats")
            .document(chatId!!)
            .update(
                mapOf(
                    "lastMessage" to text,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "unreadCount_$otherUserEmail" to FieldValue.increment(1)
                )
            )
    }

    private fun markMessagesAsRead() {
        if (chatId.isNullOrEmpty()) return

        val currentUser = auth.currentUser ?: return

        db.collection("chats")
            .document(chatId!!)
            .update("unreadCount_${currentUser.email}", 0)
    }

    private fun showAttachmentOptions() {
        val options = arrayOf(
            "📷 Foto",
            "📁 Archivo"
        )

        AlertDialog.Builder(this)
            .setTitle("Adjuntar")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickImage()
                    1 -> pickFile()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        pickFileLauncher.launch(intent)
    }

    private fun pickFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        pickFileLauncher.launch(intent)
    }

    private fun uploadFileAndSendMessage(uri: Uri) {
        Log.d(TAG, "=== INICIO SUBIDA DE ARCHIVO ===")
        Log.d(TAG, "📱 URI: $uri")
        Log.d(TAG, "💬 ChatId: $chatId")

        showLoading(true)

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "❌ Usuario no autenticado")
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            showLoading(false)
            return
        }

        Log.d(TAG, "✅ Usuario autenticado: ${currentUser.email}")
        Log.d(TAG, "✅ UID: ${currentUser.uid}")

        if (chatId.isNullOrEmpty()) {
            Log.e(TAG, "❌ ChatId es null o vacío")
            Toast.makeText(this, "Error: Chat no inicializado", Toast.LENGTH_SHORT).show()
            showLoading(false)
            return
        }

        Log.d(TAG, "✅ ChatId: $chatId")

        val fileName = getFileName(uri)
        val fileExtension = fileName.substringAfterLast(".", "")

        Log.d(TAG, "📄 Nombre del archivo: $fileName")
        Log.d(TAG, "📄 Extensión: $fileExtension")

        val fileType = when {
            fileExtension.lowercase() in listOf("jpg", "jpeg", "png", "gif") -> "image"
            fileExtension.lowercase() in listOf("pdf") -> "pdf"
            else -> "file"
        }

        val storageFileName = "${System.currentTimeMillis()}_$fileName"
        val storagePath = "chat_files/$chatId/$storageFileName"

        Log.d(TAG, "📁 Ruta de Storage: $storagePath")
        Log.d(TAG, "📁 Storage Reference: ${storage.reference}")
        Log.d(TAG, "📁 Storage Bucket: ${storage.reference.bucket}")

        // NUEVO: Verificar que el storage esté inicializado
        try {
            val bucketName = storage.reference.bucket
            Log.d(TAG, "✅ Storage bucket verificado: $bucketName")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error verificando storage bucket", e)
            Toast.makeText(this, "Error: Storage no configurado", Toast.LENGTH_LONG).show()
            showLoading(false)
            return
        }

        val fileRef = storage.reference.child(storagePath)
        Log.d(TAG, "📁 FileRef path: ${fileRef.path}")

        Log.d(TAG, "🚀 Iniciando subida...")

        fileRef.putFile(uri)
            .addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                Log.d(TAG, "📊 Progreso: $progress%")
            }
            .addOnSuccessListener { taskSnapshot ->
                Log.d(TAG, "✅ Archivo subido exitosamente")
                Log.d(TAG, "✅ Bytes transferidos: ${taskSnapshot.bytesTransferred}")

                fileRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    Log.d(TAG, "✅ URL obtenida: $downloadUri")
                    showLoading(false)
                    val messageText = if (fileType == "image") "📷 Imagen" else "📁 $fileName"
                    sendMessage(messageText, fileType, downloadUri.toString(), fileName)
                }.addOnFailureListener { e ->
                    showLoading(false)
                    Log.e(TAG, "❌ Error obteniendo URL", e)
                    Log.e(TAG, "❌ Mensaje: ${e.message}")
                    Log.e(TAG, "❌ Clase: ${e.javaClass.name}")

                    if (e is com.google.firebase.storage.StorageException) {
                        Log.e(TAG, "❌ StorageException Error Code: ${e.errorCode}")
                        Log.e(TAG, "❌ HTTP Result Code: ${e.httpResultCode}")
                    }

                    Toast.makeText(this, "Error al obtener URL: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e(TAG, "❌❌❌ ERROR SUBIENDO ARCHIVO ❌❌❌")
                Log.e(TAG, "❌ Tipo: ${e.javaClass.simpleName}")
                Log.e(TAG, "❌ Mensaje: ${e.message}")
                Log.e(TAG, "❌ Clase completa: ${e.javaClass.name}")

                // Información detallada para StorageException
                if (e is com.google.firebase.storage.StorageException) {
                    Log.e(TAG, "❌ StorageException detectada")
                    Log.e(TAG, "❌ Error Code: ${e.errorCode}")
                    Log.e(TAG, "❌ HTTP Result Code: ${e.httpResultCode}")
                    Log.e(TAG, "❌ Is Recoverable: ${e.isRecoverableException}")
                    Log.e(TAG, "❌ Causa raíz: ${e.cause?.message}")

                    // Interpretación del error
                    when (e.errorCode) {
                        com.google.firebase.storage.StorageException.ERROR_NOT_AUTHENTICATED -> {
                            Log.e(TAG, "❌ ERROR: Usuario no autenticado en Firebase")
                        }
                        com.google.firebase.storage.StorageException.ERROR_NOT_AUTHORIZED -> {
                            Log.e(TAG, "❌ ERROR: Sin permisos (revisa Storage Rules)")
                        }
                        com.google.firebase.storage.StorageException.ERROR_BUCKET_NOT_FOUND -> {
                            Log.e(TAG, "❌ ERROR: Bucket de Storage no encontrado")
                        }
                        com.google.firebase.storage.StorageException.ERROR_OBJECT_NOT_FOUND -> {
                            Log.e(TAG, "❌ ERROR: Objeto no encontrado")
                        }
                        com.google.firebase.storage.StorageException.ERROR_QUOTA_EXCEEDED -> {
                            Log.e(TAG, "❌ ERROR: Cuota de Storage excedida")
                        }
                        else -> {
                            Log.e(TAG, "❌ ERROR CODE DESCONOCIDO: ${e.errorCode}")
                        }
                    }
                }

                Log.e(TAG, "❌ Stack trace:", e)

                Toast.makeText(this, "Error subiendo: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun getFileName(uri: Uri): String {
        var name = "archivo"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnSend.isEnabled = !show
        btnAttach.isEnabled = !show
    }

    data class MessageItem(
        val messageId: String,
        val senderEmail: String,
        val text: String,
        val type: String,
        val fileUrl: String,
        val fileName: String,
        val timestamp: Date?
    )

    inner class MessagesAdapter(
        private val items: List<MessageItem>,
        private val currentUserEmail: String
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val VIEW_TYPE_SENT = 1
        private val VIEW_TYPE_RECEIVED = 2

        override fun getItemViewType(position: Int): Int {
            return if (items[position].senderEmail == currentUserEmail) {
                VIEW_TYPE_SENT
            } else {
                VIEW_TYPE_RECEIVED
            }
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == VIEW_TYPE_SENT) {
                val view = android.view.LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_sent, parent, false)
                SentMessageViewHolder(view)
            } else {
                val view = android.view.LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_received, parent, false)
                ReceivedMessageViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val message = items[position]

            when (holder) {
                is SentMessageViewHolder -> holder.bind(message)
                is ReceivedMessageViewHolder -> holder.bind(message)
            }
        }

        override fun getItemCount() = items.size

        inner class SentMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val tvMessage: TextView = view.findViewById(R.id.tvMessageSent)
            private val tvTime: TextView = view.findViewById(R.id.tvTimeSent)
            private val ivImage: ImageView = view.findViewById(R.id.ivImageSent)
            private val layoutFile: LinearLayout = view.findViewById(R.id.layoutFileSent)
            private val tvFileName: TextView = view.findViewById(R.id.tvFileNameSent)

            fun bind(message: MessageItem) {
                tvMessage.visibility = View.GONE
                ivImage.visibility = View.GONE
                layoutFile.visibility = View.GONE

                when (message.type) {
                    "text" -> {
                        tvMessage.visibility = View.VISIBLE
                        tvMessage.text = message.text
                    }
                    "image" -> {
                        ivImage.visibility = View.VISIBLE
                        Glide.with(itemView.context)
                            .load(message.fileUrl)
                            .into(ivImage)

                        ivImage.setOnClickListener {
                            openImageFullscreen(message.fileUrl)
                        }
                    }
                    else -> {
                        layoutFile.visibility = View.VISIBLE
                        tvFileName.text = message.fileName

                        layoutFile.setOnClickListener {
                            openFile(message.fileUrl)
                        }
                    }
                }

                if (message.timestamp != null) {
                    tvTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(message.timestamp)
                }
            }
        }

        inner class ReceivedMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val tvMessage: TextView = view.findViewById(R.id.tvMessageReceived)
            private val tvTime: TextView = view.findViewById(R.id.tvTimeReceived)
            private val ivImage: ImageView = view.findViewById(R.id.ivImageReceived)
            private val layoutFile: LinearLayout = view.findViewById(R.id.layoutFileReceived)
            private val tvFileName: TextView = view.findViewById(R.id.tvFileNameReceived)

            fun bind(message: MessageItem) {
                tvMessage.visibility = View.GONE
                ivImage.visibility = View.GONE
                layoutFile.visibility = View.GONE

                when (message.type) {
                    "text" -> {
                        tvMessage.visibility = View.VISIBLE
                        tvMessage.text = message.text
                    }
                    "image" -> {
                        ivImage.visibility = View.VISIBLE
                        Glide.with(itemView.context)
                            .load(message.fileUrl)
                            .into(ivImage)

                        ivImage.setOnClickListener {
                            openImageFullscreen(message.fileUrl)
                        }
                    }
                    else -> {
                        layoutFile.visibility = View.VISIBLE
                        tvFileName.text = message.fileName

                        layoutFile.setOnClickListener {
                            openFile(message.fileUrl)
                        }
                    }
                }

                if (message.timestamp != null) {
                    tvTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(message.timestamp)
                }
            }
        }

        private fun openImageFullscreen(imageUrl: String) {
            val intent = Intent(this@ChatActivity, ImageViewerActivity::class.java)
            intent.putExtra("IMAGE_URL", imageUrl)
            startActivity(intent)
        }

        private fun openFile(fileUrl: String) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(fileUrl)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "No se puede abrir el archivo", Toast.LENGTH_SHORT).show()
            }
        }
    }
}