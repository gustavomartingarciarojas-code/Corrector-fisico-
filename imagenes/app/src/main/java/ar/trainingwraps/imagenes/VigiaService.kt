package ar.trainingwraps.imagenes

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.provider.MediaStore

/**
 * Queda siempre activo (con una notificación fija) y, cada vez que aparece
 * una imagen nueva en la tablet, la mueve a la carpeta Imagenes.
 */
class VigiaService : Service() {

    private lateinit var hilo: HandlerThread
    private lateinit var manejador: Handler
    private lateinit var observador: ContentObserver
    private val barrido = Runnable { barrer() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        hilo = HandlerThread("vigia")
        hilo.start()
        manejador = Handler(hilo.looper)
        observador = object : ContentObserver(manejador) {
            override fun onChange(selfChange: Boolean) {
                programar(2500)
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observador
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = notificacion(textoEstado())
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID_NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID_NOTIF, n)
        }
        programar(1000)
        return START_STICKY
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(observador)
        hilo.quitSafely()
        super.onDestroy()
    }

    private fun programar(ms: Long) {
        manejador.removeCallbacks(barrido)
        manejador.postDelayed(barrido, ms)
    }

    private fun barrer() {
        val movidas = try {
            Ordenador.ordenarRecientes(this)
        } catch (e: Exception) {
            0
        }
        if (movidas > 0) {
            getSystemService(NotificationManager::class.java)
                ?.notify(ID_NOTIF, notificacion(textoEstado()))
        }
        // Si quedó algo a medio guardar, vuelve a mirar enseguida; si no, cada un minuto.
        programar(if (Ordenador.hayPendientes) 4000L else 60_000L)
    }

    private fun textoEstado(): String {
        val a = Ordenador.ajustes(this)
        val total = a.getLong(Ordenador.CLAVE_TOTAL, 0)
        val ultima = a.getString(Ordenador.CLAVE_ULTIMA, null)
        return if (ultima == null) {
            "Las imágenes nuevas van a la carpeta Imagenes"
        } else {
            "Movidas: $total · última: $ultima"
        }
    }

    private fun notificacion(texto: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(CANAL, "Vigilancia de imágenes", NotificationManager.IMPORTANCE_LOW)
        )
        val abrir = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CANAL)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle("Todo a Imagenes")
            .setContentText(texto)
            .setContentIntent(abrir)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CANAL = "vigia"
        private const val ID_NOTIF = 1

        /** Arranca la vigilancia (si ya tiene el permiso de archivos). */
        fun arrancar(ctx: Context) {
            if (!Ordenador.tienePermiso()) return
            try {
                ctx.startForegroundService(Intent(ctx, VigiaService::class.java))
            } catch (e: Exception) {
                // Android a veces no deja arrancar desde segundo plano; queda el trabajo de respaldo.
            }
            TrabajoContenido.programar(ctx)
        }
    }
}
