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
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
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
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var drawerLayout: DrawerLayout

    private var locationMarker: Marker? = null
    private var currentLatLng: LatLng? = null
    private val handler = Handler(Looper.getMainLooper())
    private var firstLocationUpdate = true
    private val reporteMarkers = mutableListOf<Marker>()

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

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
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

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
            override fun getInfoWindow(marker: Marker): View? = null

            @SuppressLint("InflateParams")
            override fun getInfoContents(marker: Marker): View? {
                val reporte = marker.tag as? Reporte ?: return null
                val view = LayoutInflater.from(this@MainActivity)
                    .inflate(R.layout.marker_info_window, null)

                view.findViewById<TextView>(R.id.tv_titulo).text = reporte.titulo

                view.findViewById<TextView>(R.id.tv_descripcion).text =
                    reporte.descripcion?.takeIf { it.isNotEmpty() }
                        ?: "Sin descripción"

                view.findViewById<TextView>(R.id.tv_email).text = reporte.email
                view.findViewById<TextView>(R.id.tv_fecha).text = reporte.fecha

                val imageView = view.findViewById<ImageView>(R.id.iv_foto)

                if (!reporte.imagenPath.isNullOrBlank()) {
                    val imageUrl =
                        RetrofitClient.BASE_URL.trimEnd('/') + "/" +
                                reporte.imagenPath.trimStart('/')

                    Glide.with(this@MainActivity)
                        .load(imageUrl)
                        .placeholder(R.drawable.ic_launcher_background)
                        .error(R.drawable.ic_launcher_background)
                        .into(imageView)
                }

                return view
            }
        })

        requestLocationPermission()
        fetchReportes()
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
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error cargando reportes", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) return
                val body = response.body?.string() ?: return

                val jsonArray = JSONArray(body)
                val reportes = mutableListOf<Reporte>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    reportes.add(
                        Reporte(
                            id          = obj.getInt("id"),
                            titulo      = obj.getString("titulo"),
                            descripcion = obj.optString("descripcion"),
                            latitud     = obj.getDouble("latitud"),
                            longitud    = obj.getDouble("longitud"),
                            imagenPath  = obj.optString("imagen_path"),
                            fecha       = obj.getString("fecha"),
                            email       = obj.getString("email")
                        )
                    )
                }

                runOnUiThread {
                    reporteMarkers.forEach { it.remove() }
                    reporteMarkers.clear()

                    reportes.forEach { reporte ->
                        val latLng = LatLng(reporte.latitud, reporte.longitud)
                        val marker = mMap.addMarker(
                            MarkerOptions()
                                .position(latLng)
                                .anchor(0.5f, 1f)
                                .icon(createBubbleMarker(reporte.titulo, reporte.email))
                                .title(reporte.titulo)
                        )
                        marker?.tag = reporte
                        marker?.let { reporteMarkers.add(it) }
                    }
                }
            }
        })
    }

    private fun createBubbleMarker(titulo: String, email: String): BitmapDescriptor {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
        }
        val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 255, 255, 255)
            textSize = 24f
        }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(232, 93, 26)
            style = Paint.Style.FILL
        }

        val padding = 24
        val tailHeight = 18
        val bubbleHeight = 88

        val titleText = if (titulo.length > 14) titulo.substring(0, 14) + "…" else titulo
        val userText  = "@" + email.split("@")[0].let {
            if (it.length > 11) it.substring(0, 11) + "…" else it
        }

        val bubbleWidth = (maxOf(textPaint.measureText(titleText), subTextPaint.measureText(userText))
                + padding * 2).toInt().coerceAtLeast(160)
        val totalHeight = bubbleHeight + tailHeight

        val bitmap = Bitmap.createBitmap(bubbleWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawRoundRect(RectF(0f, 0f, bubbleWidth.toFloat(), bubbleHeight.toFloat()), 18f, 18f, bgPaint)

        val tail = Path().apply {
            moveTo(bubbleWidth / 2f - 14f, bubbleHeight.toFloat())
            lineTo(bubbleWidth / 2f + 14f, bubbleHeight.toFloat())
            lineTo(bubbleWidth / 2f, totalHeight.toFloat())
            close()
        }
        canvas.drawPath(tail, bgPaint)

        canvas.drawText(titleText, padding.toFloat(), 36f, textPaint)
        canvas.drawText(userText,  padding.toFloat(), 66f, subTextPaint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            startLocationUpdates()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        handler.removeCallbacks(locationRunnable)
        handler.post(locationRunnable)
    }

    private fun createBlueDotIcon(): BitmapDescriptor {
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f

        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(50, 25, 118, 210)
            style = Paint.Style.FILL
        }.also { canvas.drawCircle(cx, cy, size / 2f, it) }

        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }.also { canvas.drawCircle(cx, cy, size / 3.2f, it) }

        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(25, 118, 210)
            style = Paint.Style.FILL
        }.also { canvas.drawCircle(cx, cy, size / 5f, it) }

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun updateLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val userLatLng = LatLng(it.latitude, it.longitude)
                currentLatLng = userLatLng

                if (locationMarker == null) {
                    locationMarker = mMap.addMarker(
                        MarkerOptions()
                            .position(userLatLng)
                            .anchor(0.5f, 0.5f)
                            .flat(true)
                            .icon(createBlueDotIcon())
                    )
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
}
