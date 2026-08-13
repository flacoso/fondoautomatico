package com.auralis.fondoautomatico

import android.app.*
import android.app.WallpaperManager
import android.content.*
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.*
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
            val minutes = longArrayOf(1, 5, 10, 15)[getSharedPreferences("config", MODE_PRIVATE).getInt("interval", 0)]
            handler.postDelayed(this, minutes * 60_000L)
        }
    }
    override fun onCreate() {
        super.onCreate(); createNotificationChannel()
        val n = NotificationCompat.Builder(this, "wallpaper").setContentTitle("Fondo Automático").setContentText("Cambiando fondos automáticamente").setSmallIcon(android.R.drawable.ic_menu_gallery).setOngoing(true).build()
        startForeground(10, n)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { running = true; handler.removeCallbacks(runnable); runnable.run(); return START_STICKY }
    private fun changeWallpaper() {
        thread {
            val prefs = getSharedPreferences("config", MODE_PRIVATE); val folder = prefs.getString("folder", null) ?: return@thread; val tree = Uri.parse(folder); val images = mutableListOf<Uri>()
            try {
                contentResolver.query(android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree, android.provider.DocumentsContract.getTreeDocumentId(tree)), arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID, android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
                    val idCol = c.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID); val mimeCol = c.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                    while (c.moveToNext()) { if ((c.getString(mimeCol) ?: "").startsWith("image/")) images.add(android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(idCol))) }
                }
            } catch (_: Exception) {}
            if (images.isEmpty()) return@thread
            val index = if (prefs.getBoolean("random", false)) Random.nextInt(images.size) else (prefs.getInt("index", -1) + 1) % images.size
            prefs.edit().putInt("index", index).apply()
            try { contentResolver.openInputStream(images[index])?.use { input -> BitmapFactory.decodeStream(input)?.let { b -> WallpaperManager.getInstance(this).setBitmap(b, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK); b.recycle() } } } catch (_: Exception) {}
        }
    }
    private fun createNotificationChannel() { if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("wallpaper", "Cambio de fondos", NotificationManager.IMPORTANCE_LOW)) }
    override fun onDestroy() { running = false; handler.removeCallbacks(runnable); super.onDestroy() }
    override fun onBind(intent: Intent?) = null
}
