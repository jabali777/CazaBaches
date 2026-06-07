package com.example.practica

data class Reporte(
    val id: Int,
    val titulo: String,
    val descripcion: String?,
    val latitud: Double,
    val longitud: Double,
    val imagenPath: String?,
    val fecha: String,
    val email: String
)