package com.auralis.fondoautomatico

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var folderText: TextView
    private lateinit var intervalSpinner: Spinner
    private lateinit var modeSpinner: Spinner

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            getSharedPreferences("config", MODE_PRIVATE).edit().putString("folder", uri.toString()).apply()
            folderText.text = "Carpeta seleccionada"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        folderText = findViewById(R.id.folderText)
        intervalSpinner = findViewById(R.id.intervalSpinner)
        modeSpinner = findViewById(R.id.modeSpinner)
        intervalSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("1 minuto", "5 minutos", "10 minutos", "15 minutos"))
        modeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("Orden de las fotos", "Aleatorio"))
        val prefs = getSharedPreferences("config", MODE_PRIVATE)
        folderText.text = if (prefs.getString("folder", null) == null) "Ninguna carpeta seleccionada" else "Carpeta seleccionada"
        findViewById<Button>(R.id.selectFolder).setOnClickListener { folderPicker.launch(null) }
        findViewById<Button>(R.id.startButton).setOnClickListener {
            if (prefs.getString("folder", null) == null) { Toast.makeText(this, "Selecciona una carpeta primero", Toast.LENGTH_LONG).show(); return@setOnClickListener }
            prefs.edit().putInt("interval", intervalSpinner.selectedItemPosition).putBoolean("random", modeSpinner.selectedItemPosition == 1).apply()
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            val i = Intent(this, WallpaperService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            Toast.makeText(this, "Cambio automático iniciado", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.stopButton).setOnClickListener { stopService(Intent(this, WallpaperService::class.java)); Toast.makeText(this, "Cambio automático detenido", Toast.LENGTH_SHORT).show() }
        findViewById<Button>(R.id.batteryButton).setOnClickListener {
            try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:$packageName") }) }
            catch (_: Exception) { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }
}
