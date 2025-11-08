package com.primero.alertamascota

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import java.util.*

class LoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LoginActivity"
        private const val REQUEST_CONNECTION_CHECK = 9999
    }

    // ✅ LISTA DE EMAILS ADMINISTRADORES
    private val ADMIN_EMAILS = listOf(
        "alexiagonzales200516@gmail.com"
        // Agrega más emails admin aquí
    )

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var btnGoogleSignIn: Button
    private lateinit var tvForgotPassword: TextView

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            Log.w(TAG, "Google sign in failed", e)
            Toast.makeText(this, "Fallo el login con Google: ${e.statusCode}",
                Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!NetworkUtils.isNetworkAvailable(this)) {
            goToNoConnection()
            return
        }
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // Si ya está logueado, redirigir según tipo de usuario
        if (auth.currentUser != null) {
            checkIfAdminByEmail(auth.currentUser?.email ?: "")
            return
        }

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnRegister = findViewById(R.id.btnRegister)
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        btnLogin.setOnClickListener {
            loginWithEmail()
        }

        btnRegister.setOnClickListener {
            registerWithEmail()
        }

        btnGoogleSignIn.setOnClickListener {
            startGoogleSignIn()
        }

        tvForgotPassword.setOnClickListener {
            resetPassword()
        }
    }

    private fun goToNoConnection() {
        val intent = Intent(this, NoConnectionActivity::class.java)
        startActivityForResult(intent, REQUEST_CONNECTION_CHECK)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CONNECTION_CHECK) {
            if (resultCode == RESULT_OK) {
                // La conexión se restableció, reiniciar la actividad
                recreate()
            } else {
                // Sin conexión, cerrar la app
                finish()
            }
        }
    }
    // ✅ LOGIN CON EMAIL - FUNCIONA PARA ADMIN Y USUARIOS NORMALES
    private fun loginWithEmail() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (!validateInput(email, password)) {
            return
        }

        setButtonsEnabled(false)

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                setButtonsEnabled(true)
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user?.isEmailVerified == true) {
                        // ✅ Redirige a admin o mapa según el email
                        checkIfAdminByEmail(email)
                    } else {
                        Toast.makeText(
                            this,
                            "Por favor verifica tu correo electrónico antes de iniciar sesión",
                            Toast.LENGTH_LONG
                        ).show()
                        auth.signOut()
                    }
                } else {
                    Toast.makeText(
                        this,
                        "Error: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    // ✅ VERIFICA SI ES ADMIN O USUARIO NORMAL
    private fun checkIfAdminByEmail(email: String) {
        val normalizedEmail = email.lowercase(Locale.getDefault())

        if (ADMIN_EMAILS.contains(normalizedEmail)) {
            // 🛡️ Es admin
            Toast.makeText(this, "Bienvenido Administrador 🛡️", Toast.LENGTH_SHORT).show()
            goToAdminDashboard()
        } else {
            // 🗺️ Usuario normal
            Toast.makeText(this, "Login exitoso", Toast.LENGTH_SHORT).show()
            goToMaps()
        }
    }

    private fun registerWithEmail() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (!validateInput(email, password)) {
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres",
                Toast.LENGTH_SHORT).show()
            return
        }

        setButtonsEnabled(false)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                setButtonsEnabled(true)
                if (task.isSuccessful) {
                    sendEmailVerification()
                } else {
                    Toast.makeText(
                        this,
                        "Error al crear cuenta: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun sendEmailVerification() {
        val user = auth.currentUser
        user?.sendEmailVerification()
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(
                        this,
                        "Cuenta creada. Por favor verifica tu correo electrónico.",
                        Toast.LENGTH_LONG
                    ).show()
                    auth.signOut()
                } else {
                    Toast.makeText(
                        this,
                        "Cuenta creada pero no se pudo enviar el correo de verificación",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun resetPassword() {
        val email = etEmail.text.toString().trim()

        if (email.isEmpty()) {
            Toast.makeText(this, "Ingresa tu correo electrónico",
                Toast.LENGTH_SHORT).show()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Ingresa un correo válido",
                Toast.LENGTH_SHORT).show()
            return
        }

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(
                        this,
                        "Correo de recuperación enviado a $email",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        "Error: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            Toast.makeText(this, "Ingresa tu correo electrónico",
                Toast.LENGTH_SHORT).show()
            return false
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Ingresa un correo válido",
                Toast.LENGTH_SHORT).show()
            return false
        }

        if (password.isEmpty()) {
            Toast.makeText(this, "Ingresa tu contraseña",
                Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        btnLogin.isEnabled = enabled
        btnRegister.isEnabled = enabled
        btnGoogleSignIn.isEnabled = enabled
    }

    private fun startGoogleSignIn() {
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    // ✅ LOGIN CON GOOGLE - FUNCIONA PARA ADMIN Y USUARIOS NORMALES
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val email = auth.currentUser?.email ?: ""
                    // ✅ Redirige a admin o mapa según el email
                    checkIfAdminByEmail(email)
                } else {
                    Toast.makeText(this, "Autenticación fallida",
                        Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun goToAdminDashboard() {
        val intent = Intent(this, AdminDashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun goToMaps() {
        val intent = Intent(this, MapsActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}