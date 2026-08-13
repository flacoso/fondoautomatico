package com.auralis.fondoautomatico

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.WallpaperManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import kotlin.concurrent.thread
import kotlin.random.Random
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WallpaperService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var wakeLock: PowerManager.WakeLock? = null

    private val runnable = object : Runnable {
        override fun run() {
            if (!running) return
            changeWallpaper()
            val prefs = getSharedPreferences("config", MODE_PRIVATE)
            val position = prefs.getInt("interval", 0).coerceIn(0, 3)
            val minutes = longArrayOf(1, 5, 10, 15)[position]
            handler.postDelayed(this, minutes * 60_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "wallpaper")
            .setContentTitle("Fondo Automático")
            .setContentText("Cambio automático activo")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setOngoing(true)
            .build()
        startForeground(10, notification)
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FondoAutomatico:Timer")
        wakeLock?.setReferenceCounted(false)
        wakeLock?.acquire()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        running = true
        handler.removeCallbacks(runnable)
        runnable.run()
        return START_STICKY
    }

    private fun changeWallpaper() {
        thread {
            val prefs = getSharedPreferences("config", MODE_PRIVATE)
            val folder = prefs.getString("folder", null) ?: return@thread
            val treeUri = Uri.parse(folder)
            val images = mutableListOf<Uri>()

            try {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri, DocumentsContract.getTreeDocumentId(treeUri)
                )
                contentResolver.query(
                    childrenUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE),
                    null, null, null
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    while (cursor.moveToNext()) {
                        if ((cursor.getString(mimeColumn) ?: "").startsWith("image/")) {
                            images += DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idColumn))
                        }
                    }
                }
            } catch (e: Exception) {
                updateNotification("Error leyendo la carpeta")
                return@thread
            }

            if (images.isEmpty()) {
                updateNotification("No hay imágenes en la carpeta")
                return@thread
            }

            val randomMode = prefs.getBoolean("random", false)
            val index = if (randomMode) Random.nextInt(images.size)
            else (prefs.getInt("index", -1) + 1) % images.size
            prefs.edit().putInt("index", index).apply()

            try {
                val manager = WallpaperManager.getInstance(this)
                contentResolver.openInputStream(images[index])?.use { input ->
                    manager.setStream(input, null, true, WallpaperManager.FLAG_SYSTEM)
                } ?: throw IllegalStateException("No se pudo abrir la imagen")

                contentResolver.openInputStream(images[index])?.use { input ->
                    if (Build.VERSION.SDK_INT >= 24) {
                        manager.setStream(input, null, true, WallpaperManager.FLAG_LOCK)
                    }
                }

                val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                updateNotification("Último cambio: $time  •  Foto ${index + 1}/${images.size}")
            } catch (e: Exception) {
                updateNotification("No se pudo cambiar el fondo")
            }
        }
    }

    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, "wallpaper")
            .setContentTitle("Fondo Automático")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setOngoing(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(10, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("wallpaper", "Cambio de fondos", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(runnable)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
