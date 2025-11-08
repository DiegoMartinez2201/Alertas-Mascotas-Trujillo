package com.primero.alertamascota

import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class ImageViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        val imageUrl = intent.getStringExtra("IMAGE_URL")
        val ivFullscreen = findViewById<ImageView>(R.id.ivFullscreenImage)
        val btnClose = findViewById<ImageButton>(R.id.btnCloseImageViewer)

        if (!imageUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(imageUrl)
                .into(ivFullscreen)
        }

        btnClose.setOnClickListener {
            finish()
        }
    }
}