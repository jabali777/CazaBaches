package com.example.practica

import android.graphics.Bitmap

data class Reporte(
    val id: Int,
    val titulo: String,
    val descripcion: String?,
    val latitud: Double,
    val longitud: Double,
    val imagenPath: String?,
    val fecha: String,
    val email: String,
    var bitmapCache: Bitmap? = null
) {
    val fullImageUrl: String?
        get() {
            if (imagenPath.isNullOrBlank() || imagenPath == "null") return null
            val base = RetrofitClient.BASE_URL.trimEnd('/')
            val path = imagenPath.trimStart('/')
            return when {
                path.startsWith("http") -> path
                path.contains("uploads/") || path.contains("static/") -> "$base/$path"
                else -> "$base/uploads/$path"
            }
        }

    val alternativeImageUrl: String?
        get() {
            if (imagenPath.isNullOrBlank() || imagenPath == "null") return null
            val base = RetrofitClient.BASE_URL.trimEnd('/')
            return "$base/${imagenPath.trimStart('/')}"
        }
}