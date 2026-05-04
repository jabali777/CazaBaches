package com.example.practica

import android.content.Context

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun guardarToken(token: String) = prefs.edit().putString("jwt", token).apply()
    fun obtenerToken(): String? = prefs.getString("jwt", null)
    fun cerrarSesion() = prefs.edit().clear().apply()
    fun estaLogueado(): Boolean = obtenerToken() != null
}