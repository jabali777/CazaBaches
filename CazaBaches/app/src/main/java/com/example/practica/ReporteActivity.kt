package com.example.practica

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.textfield.TextInputEditText
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
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
        if (exito) {
            ivFoto.setImageURI(fotoUri)
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
            println("FOTO: existe=${file.exists()} tamaño=${file.length()} path=${file.absolutePath}")
        } ?: println("FOTO: fotoFile es NULL")

        fotoFile?.let { file ->
            builder.addFormDataPart(
                "foto", file.name,
                file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            )
        }

        val request = Request.Builder()
            .url("${RetrofitClient.BASE_URL}api/reportes")
            .addHeader("Authorization", "Bearer $token")
            .post(builder.build())
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                runOnUiThread {
                    btnEnviar.isEnabled = true
                    Toast.makeText(this@ReporteActivity, "Error de red: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    btnEnviar.isEnabled = true
                    if (response.isSuccessful) {
                        Toast.makeText(this@ReporteActivity, "Reporte enviado", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@ReporteActivity, "Error: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}
