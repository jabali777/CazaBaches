package com.example.practica

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.button.MaterialButton
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var session: SessionManager
    private lateinit var btnMenu: FloatingActionButton
    private lateinit var btnReportar: FloatingActionButton
    private lateinit var btnBorrar: FloatingActionButton
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var drawerLayout: DrawerLayout

    private lateinit var bottomSheet: View
    private lateinit var imgReporte: ImageView
    private lateinit var txtTitulo: TextView
    private lateinit var txtDescripcion: TextView
    private lateinit var txtFecha: TextView
    private lateinit var btnVerImagen: MaterialButton
    private lateinit var btnCerrar: MaterialButton

    private var reporteActual: Reporte? = null
    private var locationMarker: Marker? = null
    private var currentLatLng: LatLng? = null
    private val handler = Handler(Looper.getMainLooper())
    private var firstLocationUpdate = true
    private val reporteMarkers = mutableListOf<Marker>()
    
    private var reporteSeleccionado: Reporte? = null
    private var markerSeleccionado: Marker? = null

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val REFRESH_INTERVAL_MS = 3000L
    }

    private val locationRunnable = object : Runnable {
        override fun run() {
            updateLocation()
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        if (!session.estaLogueado()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        bottomSheet = findViewById(R.id.bottomSheetReporte)
        imgReporte = findViewById(R.id.imgReporte)
        txtTitulo = findViewById(R.id.txtTitulo)
        txtDescripcion = findViewById(R.id.txtDescripcion)
        txtFecha = findViewById(R.id.txtFecha)
        btnVerImagen = findViewById(R.id.btnVerImagen)
        btnCerrar = findViewById(R.id.btnCerrar)
        
        bottomSheet.visibility = View.GONE
        
        btnCerrar.setOnClickListener { ocultarBottomSheet() }
        btnVerImagen.setOnClickListener {
            reporteActual?.bitmapCache?.let { mostrarImagenCompleta(it) }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.drawer_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        drawerLayout = findViewById(R.id.drawer_layout)
        btnMenu = findViewById(R.id.btn_menu)
        btnMenu.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        
        btnReportar = findViewById(R.id.btn_reportar)
        btnReportar.setOnClickListener { mostrarDialogoReporte() }
        
        btnBorrar = findViewById(R.id.btn_borrar)
        btnBorrar.setOnClickListener {
            val reporte = reporteSeleccionado
            val marker = markerSeleccionado
            if (reporte != null && marker != null) {
                mostrarDialogoBorrar(marker, reporte)
            }
        }

        findViewById<TextView>(R.id.nav_perfil).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, Profile::class.java))
        }
        findViewById<TextView>(R.id.nav_configuraciones).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        findViewById<TextView>(R.id.nav_cerrar_sesion).setOnClickListener {
            session.cerrarSesion()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else if (bottomSheet.visibility == View.VISIBLE) {
                    ocultarBottomSheet()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            startLocationUpdates()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.setOnMarkerClickListener { marker ->
            val reporte = marker.tag as? Reporte ?: return@setOnMarkerClickListener false
            mostrarBottomSheet(marker, reporte)
            
            marker.setAnchor(0.5f, 1f)
            mMap.animateCamera(CameraUpdateFactory.newLatLng(marker.position))

            if (reporte.email == session.obtenerEmail()) {
                reporteSeleccionado = reporte
                markerSeleccionado = marker
                btnBorrar.visibility = View.VISIBLE
            } else {
                reporteSeleccionado = null
                markerSeleccionado = null
                btnBorrar.visibility = View.GONE
            }
            true
        }

        mMap.setOnMapClickListener {
            ocultarBottomSheet()
            btnBorrar.visibility = View.GONE
            reporteSeleccionado = null
            markerSeleccionado = null
        }

        requestLocationPermission()
        fetchReportes()
    }

    private fun mostrarDialogoReporte() {
        val latLng = currentLatLng
        if (latLng == null) {
            Toast.makeText(this, "Esperando ubicación...", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, ReporteActivity::class.java)
        intent.putExtra("latitud", latLng.latitude)
        intent.putExtra("longitud", latLng.longitude)
        startActivity(intent)
    }

    private fun mostrarDialogoBorrar(marker: Marker, reporte: Reporte) {
        AlertDialog.Builder(this)
            .setTitle("Borrar reporte")
            .setMessage("¿Seguro que querés borrar \"${reporte.titulo}\"?")
            .setPositiveButton("Borrar") { _, _ -> borrarReporte(marker, reporte) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun borrarReporte(marker: Marker, reporte: Reporte) {
        val token = session.obtenerToken() ?: return
        val request = Request.Builder()
            .url("${RetrofitClient.BASE_URL}api/reportes/${reporte.id}")
            .addHeader("Authorization", "Bearer $token")
            .delete()
            .build()
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error de red al borrar", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        marker.remove()
                        reporteMarkers.remove(marker)
                        btnBorrar.visibility = View.GONE
                        reporteSeleccionado = null
                        markerSeleccionado = null
                        Toast.makeText(this@MainActivity, "Reporte borrado", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Error ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun precargarImagen(marker: Marker, reporte: Reporte, useAlternative: Boolean = false) {
        val imageUrl = if (useAlternative) reporte.alternativeImageUrl else reporte.fullImageUrl
        if (imageUrl == null) return
        
        Glide.with(this).asBitmap().load(imageUrl).into(object : com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: com.bumptech.glide.request.transition.Transition<in Bitmap>?) {
                reporte.bitmapCache = resource
                if (reporteActual?.id == reporte.id) {
                    imgReporte.alpha = 0f
                    imgReporte.setImageBitmap(resource)
                    imgReporte.animate().alpha(1f).setDuration(250).start()
                }
            }
            override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
            override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                if (!useAlternative) precargarImagen(marker, reporte, true)
            }
        })
    }

    private fun fetchReportes() {
        val token = session.obtenerToken() ?: return
        val request = Request.Builder()
            .url("${RetrofitClient.BASE_URL}api/reportes")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Error cargando reportes", Toast.LENGTH_SHORT).show() }
            }
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) return
                val body = response.body?.string() ?: return
                val jsonArray = JSONArray(body)
                val reportes = mutableListOf<Reporte>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    reportes.add(Reporte(
                        id = obj.getInt("id"),
                        titulo = obj.getString("titulo"),
                        descripcion = obj.optString("descripcion"),
                        latitud = obj.getDouble("latitud"),
                        longitud = obj.getDouble("longitud"),
                        imagenPath = obj.optString("imagen_path"),
                        fecha = obj.getString("fecha"),
                        email = obj.getString("email")
                    ))
                }
                runOnUiThread {
                    reporteMarkers.forEach { it.remove() }
                    reporteMarkers.clear()
                    reportes.forEach { reporte ->
                        val latLng = LatLng(reporte.latitud, reporte.longitud)
                        val marker = mMap.addMarker(MarkerOptions()
                            .position(latLng)
                            .anchor(0.5f, 1f)
                            .icon(createBubbleMarker(reporte.titulo))
                            .title(reporte.titulo))
                        marker?.tag = reporte
                        marker?.let { reporteMarkers.add(it) }
                    }
                }
            }
        })
    }

    private fun createBubbleMarker(titulo: String): BitmapDescriptor {
        val density = resources.displayMetrics.density
        val padding = (18 * density).toInt()
        val radius = 22f * density
        val pointerHeight = 18f * density
        val title = if (titulo.length > 20) titulo.substring(0, 20) + "…" else titulo

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 15f * density
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 17f * density
        }
        val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E85D1A")
        }

        val textWidth = textPaint.measureText(title)
        val bubbleWidth = (padding * 2  + 16 * density + textWidth).toInt()
        val bubbleHeight = (40 * density).toInt()
        val totalHeight = (bubbleHeight + pointerHeight).toInt()

        val bitmap = Bitmap.createBitmap(bubbleWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawRoundRect(RectF(0f, 0f, bubbleWidth.toFloat(), bubbleHeight.toFloat()), radius, radius, bubblePaint)

        val cx = bubbleWidth / 2f
        val path = Path().apply {
            moveTo(cx - 14 * density, bubbleHeight.toFloat())
            lineTo(cx + 14 * density, bubbleHeight.toFloat())
            lineTo(cx, totalHeight.toFloat())
            close()
        }
        canvas.drawPath(path, bubblePaint)

        val font = textPaint.fontMetrics
        val centerY = bubbleHeight / 2f - (font.ascent + font.descent) / 2f
        canvas.drawText(title, padding + 12 * density, centerY, textPaint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        handler.removeCallbacks(locationRunnable)
        handler.post(locationRunnable)
    }

    private fun updateLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val userLatLng = LatLng(it.latitude, it.longitude)
                currentLatLng = userLatLng
                if (locationMarker == null) {
                    locationMarker = mMap.addMarker(MarkerOptions().position(userLatLng).anchor(0.5f, 0.5f).flat(true).icon(createBlueDotIcon()))
                } else {
                    locationMarker!!.position = userLatLng
                }
                if (firstLocationUpdate) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 16f))
                    firstLocationUpdate = false
                }
            }
        }
    }

    private fun createBlueDotIcon(): BitmapDescriptor {
        val size = 65
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(50, 25, 118, 210) }.also { canvas.drawCircle(cx, cy, size / 2f, it) }
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }.also { canvas.drawCircle(cx, cy, size / 3.2f, it) }
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(25, 118, 210) }.also { canvas.drawCircle(cx, cy, size / 5f, it) }
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    override fun onResume() {
        super.onResume()
        if (::mMap.isInitialized) {
            startLocationUpdates()
            fetchReportes()
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(locationRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(locationRunnable)
    }

    private fun mostrarBottomSheet(marker: Marker, reporte: Reporte) {
        reporteActual = reporte
        txtTitulo.text = reporte.titulo
        txtDescripcion.text = if (reporte.descripcion.isNullOrBlank()) "Sin descripción" else reporte.descripcion
        txtFecha.text = reporte.fecha

        if (reporte.bitmapCache != null) {
            imgReporte.setImageBitmap(reporte.bitmapCache)
        } else {
            imgReporte.setImageResource(R.drawable.bg_foto_placeholder)
            if (!reporte.imagenPath.isNullOrBlank()) {
                precargarImagen(marker, reporte)
            }
        }

        if (bottomSheet.visibility != View.VISIBLE) {
            bottomSheet.visibility = View.VISIBLE
            bottomSheet.translationY = 1000f
            bottomSheet.animate().translationY(0f).setDuration(300).start()
        }
    }

    private fun ocultarBottomSheet() {
        if (bottomSheet.visibility == View.VISIBLE) {
            bottomSheet.animate().translationY(bottomSheet.height.toFloat()).setDuration(300).withEndAction {
                bottomSheet.visibility = View.GONE
            }.start()
        }
    }

    private fun mostrarImagenCompleta(bitmap: Bitmap) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val imageView = ImageView(this)
        imageView.setImageBitmap(bitmap)
        imageView.setBackgroundColor(Color.BLACK)
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        dialog.setContentView(imageView)
        imageView.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}