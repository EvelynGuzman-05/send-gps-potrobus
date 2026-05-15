package mx.itson.sendgpspotrobus

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var tvBienvenida: TextView
    private lateinit var tvUnidad: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnLogout: Button

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted   = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            checkBackgroundLocationPermission()
        } else {
            Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
        }
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) checkNotificationPermission()
        else Toast.makeText(this, "Permiso de ubicación en segundo plano denegado", Toast.LENGTH_SHORT).show()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startGpsService()
        else Toast.makeText(this, "Permiso de notificaciones denegado", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("potrobus_prefs", MODE_PRIVATE)

        // Si no hay sesión, volver al login
        if (prefs.getInt("id_unidad", -1) == -1) {
            goToLogin()
            return
        }

        tvBienvenida = findViewById(R.id.tv_bienvenida)
        tvUnidad     = findViewById(R.id.tv_unidad)
        btnStart     = findViewById(R.id.btn_start_tracking)
        btnStop      = findViewById(R.id.btn_stop_tracking)
        btnLogout    = findViewById(R.id.btn_logout)

        // Mostrar info del chofer
        val nombre = prefs.getString("nombre_chofer", "Chofer")
        val numEco = prefs.getString("numero_economico", "—")
        tvBienvenida.text = "Bienvenido, $nombre"
        tvUnidad.text     = "Unidad asignada: $numEco"

        btnStart.setOnClickListener  { checkAndRequestPermissions() }
        btnStop.setOnClickListener   { stopGpsService() }
        btnLogout.setOnClickListener { confirmarCerrarSesion() }
    }

    // --- Permisos ---

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            checkBackgroundLocationPermission()
        } else {
            requestPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    private fun checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                checkNotificationPermission()
            } else {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        } else {
            checkNotificationPermission()
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
                startGpsService()
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startGpsService()
        }
    }

    // --- Servicio ---

    private fun startGpsService() {
        val intent = Intent(this, GpsService::class.java)
        ContextCompat.startForegroundService(this, intent)
        Toast.makeText(this, "Tracking iniciado", Toast.LENGTH_SHORT).show()
        btnStart.isEnabled = false
        btnStop.isEnabled  = true
    }

    private fun stopGpsService() {
        val intent = Intent(this, GpsService::class.java)
        stopService(intent)
        Toast.makeText(this, "Tracking detenido", Toast.LENGTH_SHORT).show()
        btnStart.isEnabled = true
        btnStop.isEnabled  = false
    }

    // --- Sesión ---

    private fun confirmarCerrarSesion() {
        AlertDialog.Builder(this)
            .setTitle("Cerrar sesión")
            .setMessage("¿Deseas cerrar sesión? Se detendrá el tracking.")
            .setPositiveButton("Cerrar sesión") { _, _ ->
                stopGpsService()
                prefs.edit().clear().apply()
                goToLogin()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}