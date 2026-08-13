package com.auralis.fondoautomatico

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import kotlin.concurrent.thread
import kotlin.random.Random

class WallpaperService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

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
            .setContentText("Cambio automático de fondos activo")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setOngoing(true)
            .build()
        startForeground(10, notification)
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
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri)
                )
                contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    while (cursor.moveToNext()) {
                        val mime = cursor.getString(mimeColumn) ?: ""
                        if (mime.startsWith("image/")) {
                            images += DocumentsContract.buildDocumentUriUsingTree(
                                treeUri,
                                cursor.getString(idColumn)
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                return@thread
            }

            if (images.isEmpty()) return@thread

            val randomMode = prefs.getBoolean("random", false)
            val index = if (randomMode) {
                Random.nextInt(images.size)
            } else {
                (prefs.getInt("index", -1) + 1) % images.size
            }
            prefs.edit().putInt("index", index).apply()

            try {
                contentResolver.openInputStream(images[index])?.use { input ->
                    val bitmap = BitmapFactory.decodeStream(input) ?: return@use
                    val manager = WallpaperManager.getInstance(this)
                    manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                    if (Build.VERSION.SDK_INT >= 24) {
                        manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                    }
                    bitmap.recycle()
                }
            } catch (_: Exception) {
                // Android may reject an individual image; the service continues with the next cycle.
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                "wallpaper",
                "Cambio de fondos",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(runnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
