package com.example.practica

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
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

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var session: SessionManager
    private lateinit var btnMenu: FloatingActionButton
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var drawerLayout: DrawerLayout

    private var locationMarker: Marker? = null
    private val handler = Handler(Looper.getMainLooper())
    private var firstLocationUpdate = true

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        session = SessionManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        drawerLayout = findViewById(R.id.drawer_layout)

        btnMenu = findViewById(R.id.btn_menu)
        btnMenu.setOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        findViewById<TextView>(R.id.nav_perfil).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        findViewById<TextView>(R.id.nav_configuraciones).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        findViewById<TextView>(R.id.nav_cerrar_sesion).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            session.cerrarSesion()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        requestLocationPermission()
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
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        handler.post(locationRunnable)
    }

    private fun createBlueDotIcon(): BitmapDescriptor {
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f

        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(50, 25, 118, 210)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, size / 2f, haloPaint)

        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, size / 3.2f, whitePaint)

        val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(25, 118, 210)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, size / 5f, bluePaint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun updateLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val userLatLng = LatLng(it.latitude, it.longitude)

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