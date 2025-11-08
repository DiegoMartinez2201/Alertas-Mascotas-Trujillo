package com.primero.alertamascota

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.chip.Chip

class NotificationSettingsActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "NotificationPrefs"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_NOTIFY_INJURED = "notify_injured"
        private const val KEY_NOTIFY_LOST = "notify_lost"
        private const val KEY_NOTIFY_ABANDONED = "notify_abandoned"
        private const val KEY_NOTIFY_DANGER = "notify_danger"
        private const val KEY_NOTIFY_ADOPTION = "notify_adoption"
        private const val KEY_NOTIFY_SICK = "notify_sick"
        private const val KEY_NOTIFY_DOG = "notify_dog"
        private const val KEY_NOTIFY_CAT = "notify_cat"
        private const val KEY_NOTIFY_BIRD = "notify_bird"
        private const val KEY_NOTIFY_OTHER = "notify_other"
    }

    private lateinit var prefs: SharedPreferences

    // UI Elements
    private lateinit var btnBack: ImageButton
    private lateinit var switchNotifications: SwitchCompat
    private lateinit var tvNotificationStatus: TextView

    // Estados
    private lateinit var chipInjured: Chip
    private lateinit var chipLost: Chip
    private lateinit var chipAbandoned: Chip
    private lateinit var chipDanger: Chip
    private lateinit var chipAdoption: Chip
    private lateinit var chipSick: Chip

    // Tipos de mascota
    private lateinit var chipDog: Chip
    private lateinit var chipCat: Chip
    private lateinit var chipBird: Chip
    private lateinit var chipOther: Chip

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_settings)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        initViews()
        loadSettings()
        setupListeners()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        switchNotifications = findViewById(R.id.switchNotifications)
        tvNotificationStatus = findViewById(R.id.tvNotificationStatus)

        // Estados
        chipInjured = findViewById(R.id.chipInjured)
        chipLost = findViewById(R.id.chipLost)
        chipAbandoned = findViewById(R.id.chipAbandoned)
        chipDanger = findViewById(R.id.chipDanger)
        chipAdoption = findViewById(R.id.chipAdoption)
        chipSick = findViewById(R.id.chipSick)

        // Tipos
        chipDog = findViewById(R.id.chipDog)
        chipCat = findViewById(R.id.chipCat)
        chipBird = findViewById(R.id.chipBird)
        chipOther = findViewById(R.id.chipOther)
    }

    private fun loadSettings() {
        // Cargar estado general
        val notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, false)
        switchNotifications.isChecked = notificationsEnabled
        updateNotificationStatus(notificationsEnabled)

        // Cargar estados
        chipInjured.isChecked = prefs.getBoolean(KEY_NOTIFY_INJURED, true)
        chipLost.isChecked = prefs.getBoolean(KEY_NOTIFY_LOST, true)
        chipAbandoned.isChecked = prefs.getBoolean(KEY_NOTIFY_ABANDONED, true)
        chipDanger.isChecked = prefs.getBoolean(KEY_NOTIFY_DANGER, true)
        chipAdoption.isChecked = prefs.getBoolean(KEY_NOTIFY_ADOPTION, true)
        chipSick.isChecked = prefs.getBoolean(KEY_NOTIFY_SICK, true)

        // Cargar tipos
        chipDog.isChecked = prefs.getBoolean(KEY_NOTIFY_DOG, true)
        chipCat.isChecked = prefs.getBoolean(KEY_NOTIFY_CAT, true)
        chipBird.isChecked = prefs.getBoolean(KEY_NOTIFY_BIRD, true)
        chipOther.isChecked = prefs.getBoolean(KEY_NOTIFY_OTHER, true)

        enableDisableFilters(notificationsEnabled)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            saveNotificationEnabled(isChecked)
            updateNotificationStatus(isChecked)
            enableDisableFilters(isChecked)
        }

        // Listeners para estados
        chipInjured.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_NOTIFY_INJURED, isChecked).apply()
        }

        chipLost.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_NOTIFY_LOST, isChecked).apply()
        }

        chipAbandoned.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_NOTIFY_ABANDONED, isChecked).apply()
        }

        chipDanger.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_NOTIFY_DANGER, isChecked).apply()
        }

        chipAdoption.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_NOTIFY_ADOPTION, isChecked).apply()
        }

        chipSick.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_NOTIFY_SICK, isChecked).apply()
        }

        // Listeners para tipos
        chipDog.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_NOTIFY_DOG, isChecked).apply()
        }

        chipCat.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_NOTIFY_CAT, isChecked).apply()
        }

        chipBird.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_NOTIFY_BIRD, isChecked).apply()
        }

        chipOther.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_NOTIFY_OTHER, isChecked).apply()
        }
    }

    private fun saveNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    private fun updateNotificationStatus(enabled: Boolean) {
        tvNotificationStatus.text = if (enabled) {
            "✅ Recibirás notificaciones de casos cercanos"
        } else {
            "❌ Las notificaciones están desactivadas"
        }
    }

    private fun enableDisableFilters(enabled: Boolean) {
        // Estados
        chipInjured.isEnabled = enabled
        chipLost.isEnabled = enabled
        chipAbandoned.isEnabled = enabled
        chipDanger.isEnabled = enabled
        chipAdoption.isEnabled = enabled
        chipSick.isEnabled = enabled

        // Tipos
        chipDog.isEnabled = enabled
        chipCat.isEnabled = enabled
        chipBird.isEnabled = enabled
        chipOther.isEnabled = enabled

        // Cambiar opacidad visual
        val alpha = if (enabled) 1.0f else 0.5f
        chipInjured.alpha = alpha
        chipLost.alpha = alpha
        chipAbandoned.alpha = alpha
        chipDanger.alpha = alpha
        chipAdoption.alpha = alpha
        chipSick.alpha = alpha
        chipDog.alpha = alpha
        chipCat.alpha = alpha
        chipBird.alpha = alpha
        chipOther.alpha = alpha
    }
}