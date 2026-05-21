package com.example.practica

import android.content.Context

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun guardarSesion(token: String, email: String) {
        prefs.edit().apply {
            putString("jwt", token)
            putString("user_email", email)
            apply()
        }
    }

    fun obtenerToken(): String? = prefs.getString("jwt", null)
    fun obtenerEmail(): String? = prefs.getString("user_email", "usuario@ejemplo.com")

    fun cerrarSesion() = prefs.edit().clear().apply()
    fun estaLogueado(): Boolean = obtenerToken() != null
}