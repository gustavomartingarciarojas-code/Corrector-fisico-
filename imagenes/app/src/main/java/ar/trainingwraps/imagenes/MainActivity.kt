package ar.trainingwraps.imagenes

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** Pantalla de configuración: permisos, estado y botón "Ordenar todo". */
class MainActivity : Activity() {

    private lateinit var estado: TextView
    private lateinit var resultado: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val columna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 120, 48, 48)
        }

        columna.addView(TextView(this).apply {
            textSize = 16f
            text = """
                Todo a Imagenes junta todas tus imágenes en una sola carpeta:
                Almacenamiento interno > Imagenes (sin subcarpetas).

                Capturas de pantalla, fotos de la cámara, descargas, .jpg, .png, .webp: todo lo nuevo se mueve solo ahí.

                Se configura una sola vez:
                1. Tocá "Dar permiso" y activá el acceso a todos los archivos.
                2. Tocá "Que no se cierre" y aceptá.
                3. Tocá "Ordenar todo lo que ya tengo" para juntar lo que ya existe.
            """.trimIndent()
        })

        estado = TextView(this).apply {
            textSize = 16f
            setPadding(0, 32, 0, 32)
        }
        columna.addView(estado)

        columna.addView(Button(this).apply {
            text = "1. Dar permiso"
            setOnClickListener { pedirPermiso() }
        })

        columna.addView(Button(this).apply {
            text = "2. Que no se cierre"
            setOnClickListener { pedirBateria() }
        })

        columna.addView(Button(this).apply {
            text = "3. Ordenar todo lo que ya tengo"
            setOnClickListener { confirmarOrdenarTodo() }
        })

        val ajustes = Ordenador.ajustes(this)
        columna.addView(Switch(this).apply {
            text = "Incluir las fotos de WhatsApp y Telegram (si se mueven, en los chats viejos pueden no abrir)"
            textSize = 14f
            setPadding(0, 32, 0, 0)
            isChecked = ajustes.getBoolean(Ordenador.CLAVE_MENSAJERIA, false)
            setOnCheckedChangeListener { _, activo ->
                ajustes.edit().putBoolean(Ordenador.CLAVE_MENSAJERIA, activo).apply()
            }
        })

        resultado = TextView(this).apply {
            textSize = 16f
            setPadding(0, 32, 0, 0)
        }
        columna.addView(resultado)

        setContentView(ScrollView(this).apply { addView(columna) })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        if (Ordenador.tienePermiso()) VigiaService.arrancar(this)
        actualizarEstado()
    }

    private fun actualizarEstado() {
        val permiso = Ordenador.tienePermiso()
        val bateria = getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(packageName) ?: false
        val total = Ordenador.ajustes(this).getLong(Ordenador.CLAVE_TOTAL, 0)
        estado.text = buildString {
            append(if (permiso) "✔ Permiso de archivos: dado\n" else "✘ Permiso de archivos: falta (paso 1)\n")
            append(if (bateria) "✔ Batería: no la cierra\n" else "✘ Batería: la puede cerrar (paso 2)\n")
            append(if (permiso) "✔ Vigilando: lo nuevo va a Imagenes\n" else "✘ Todavía no vigila\n")
            append("Imágenes movidas hasta ahora: $total")
        }
    }

    private fun pedirPermiso() {
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    private fun pedirBateria() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun confirmarOrdenarTodo() {
        if (!Ordenador.tienePermiso()) {
            resultado.text = "Primero hacé el paso 1."
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Ordenar todo")
            .setMessage("Voy a mover todas las imágenes de la tablet a la carpeta Imagenes. Salen de las carpetas donde están ahora. ¿Sigo?")
            .setPositiveButton("Sí, ordenar") { _, _ -> ordenarTodo() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun ordenarTodo() {
        resultado.text = "Ordenando… puede tardar un poco."
        Thread {
            val n = try {
                Ordenador.ordenarTodo(applicationContext)
            } catch (e: Exception) {
                -1
            }
            runOnUiThread {
                resultado.text = if (n < 0) {
                    "Hubo un error al ordenar. Probá de nuevo."
                } else {
                    "Listo: moví $n imágenes a Imagenes."
                }
                actualizarEstado()
            }
        }.start()
    }
}
