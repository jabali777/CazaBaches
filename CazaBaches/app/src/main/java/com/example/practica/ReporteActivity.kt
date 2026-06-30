package com.example.practica

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.google.android.material.textfield.TextInputEditText
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReporteActivity : AppCompatActivity() {

    private lateinit var session: SessionManager
    private lateinit var etTitulo: TextInputEditText
    private lateinit var etDescripcion: TextInputEditText
    private lateinit var tvCoordenadas: TextView
    private lateinit var ivFoto: ImageView
    private lateinit var btnTomarFoto: Button
    private lateinit var btnEnviar: Button

    private var latitud: Double = 0.0
    private var longitud: Double = 0.0
    private lateinit var fotoUri: Uri
    private var fotoFile: File? = null

    private val camaraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { exito ->
        if (exito && fotoFile != null && fotoFile!!.length() > 0) {
            // Cambio a Glide para previsualización más confiable
            Glide.with(this).load(fotoFile).into(ivFoto)
        } else {
            Toast.makeText(this, "No se pudo capturar la foto, intentá de nuevo", Toast.LENGTH_SHORT).show()
            fotoFile?.delete()
            fotoFile = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reporte)

        session    = SessionManager(this)
        etTitulo      = findViewById(R.id.et_titulo)
        etDescripcion = findViewById(R.id.et_descripcion)
        tvCoordenadas = findViewById(R.id.tv_coordenadas)
        ivFoto        = findViewById(R.id.iv_foto)
        btnTomarFoto  = findViewById(R.id.btn_tomar_foto)
        btnEnviar     = findViewById(R.id.btn_enviar)

        latitud  = intent.getDoubleExtra("latitud", 0.0)
        longitud = intent.getDoubleExtra("longitud", 0.0)
        tvCoordenadas.text = "Lat: %.6f  |  Lng: %.6f".format(latitud, longitud)

        btnTomarFoto.setOnClickListener { abrirCamara() }
        btnEnviar.setOnClickListener { enviarReporte() }
    }

    private fun abrirCamara() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 2001)
            return
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        fotoFile = File.createTempFile("BACHE_${timestamp}_", ".jpg", storageDir)
        fotoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", fotoFile!!)
        camaraLauncher.launch(fotoUri)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2001 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            abrirCamara()
        }
    }

    private fun enviarReporte() {
        val titulo      = etTitulo.text.toString().trim()
        val descripcion = etDescripcion.text.toString().trim()

        if (titulo.isEmpty()) {
            etTitulo.error = "El título es requerido"
            return
        }

        if (fotoFile != null && fotoFile!!.length() == 0L) {
            Toast.makeText(this, "La foto está vacía, tomala de nuevo", Toast.LENGTH_SHORT).show()
            fotoFile = null
            return
        }

        btnEnviar.isEnabled = false

        val token = session.obtenerToken() ?: run {
            Toast.makeText(this, "Sesión expirada", Toast.LENGTH_SHORT).show()
            btnEnviar.isEnabled = true
            return
        }

        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("titulo", titulo)
            .addFormDataPart("descripcion", descripcion)
            .addFormDataPart("latitud", latitud.toString())
            .addFormDataPart("longitud", longitud.toString())

        fotoFile?.let { file ->
            Log.d("UPLOAD", "Enviando FOTO: existe=${file.exists()} tamaño=${file.length()} path=${file.absolutePath}")
            builder.addFormDataPart(
                "foto", file.name,
                file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            )
        } ?: Log.d("UPLOAD", "Enviando reporte SIN FOTO")

        val apiUrl = "${RetrofitClient.BASE_URL}api/reportes"
        Log.d("UPLOAD", "URL de destino: $apiUrl")

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("Authorization", "Bearer $token")
            .post(builder.build())
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    btnEnviar.isEnabled = true
                    Log.e("UPLOAD", "Error de red", e)
                    Toast.makeText(this@ReporteActivity, "Error de red: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val resBody = response.body?.string()
                runOnUiThread {
                    btnEnviar.isEnabled = true
                    if (response.isSuccessful) {
                        Log.d("UPLOAD", "Éxito: $resBody")
                        Toast.makeText(this@ReporteActivity, "Reporte enviado", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Log.e("UPLOAD", "Error servidor (${response.code}): $resBody")
                        Toast.makeText(this@ReporteActivity, "Error (${response.code}): $resBody", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }
}
