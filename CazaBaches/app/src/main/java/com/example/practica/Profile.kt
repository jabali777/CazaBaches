package com.example.practica

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class Profile : AppCompatActivity() {

    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)

        session = SessionManager(this)

        val tvProfileEmail = findViewById<TextView>(R.id.tv_profile_email)
        val tvDetailEmail = findViewById<TextView>(R.id.tv_detail_email)
        val btnBack = findViewById<Button>(R.id.btn_back)

        val userEmail = session.obtenerEmail()
        tvProfileEmail.text = userEmail
        tvDetailEmail.text = userEmail

        btnBack.setOnClickListener {
            finish()
        }
    }
}