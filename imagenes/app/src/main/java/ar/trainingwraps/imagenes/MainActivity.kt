package ar.trainingwraps.imagenes

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** Pantalla de configuración: estado arriba, botones y explicación abajo. */
class MainActivity : Activity() {

    private lateinit var estado: TextView
    private lateinit var resultado: TextView
    private lateinit var botonOrdenar: Button

    @Volatile
    private var ordenando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()

        val columna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 48)
        }

        columna.addView(TextView(this).apply {
            text = "Todo a Imagenes"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
        })

        // Lo importante, siempre arriba: estado y avance.
        estado = TextView(this).apply {
            textSize = 16f
            setPadding(0, 24, 0, 8)
        }
        columna.addView(estado)

        resultado = TextView(this).apply {
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 8, 0, 24)
        }
        columna.addView(resultado)

        columna.addView(Button(this).apply {
            text = "1. Dar permiso"
            setOnClickListener { pedirPermiso() }
        })

        columna.addView(Button(this).apply {
            text = "2. Que no se cierre"
            setOnClickListener { pedirBateria() }
        })

        botonOrdenar = Button(this).apply {
            text = "3. Ordenar todo lo que ya tengo"
            setOnClickListener { confirmarOrdenarTodo() }
        }
        columna.addView(botonOrdenar)

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

        columna.addView(TextView(this).apply {
            textSize = 15f
            setPadding(0, 40, 0, 0)
            text = """
                Junta todas tus imágenes en una sola carpeta:
                Almacenamiento interno > Imagenes (sin subcarpetas).

                Capturas, fotos de la cámara, descargas, .jpg, .png, .webp: todo lo nuevo se mueve solo ahí.

                Se configura una sola vez:
                1. "Dar permiso": activá el acceso a todos los archivos.
                2. "Que no se cierre": aceptá el aviso. Si no aparece ningún aviso, no pasa nada: mientras esté la notificación de la app arriba, sigue vigilando.
                3. "Ordenar todo lo que ya tengo": junta lo que ya existe.
            """.trimIndent()
        })

        val raizVista = ScrollView(this).apply { addView(columna) }
        // Que nada quede tapado por la barra de arriba ni por la franja de abajo.
        raizVista.setOnApplyWindowInsetsListener { v, insets ->
            val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        setContentView(raizVista)

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
            append(if (bateria) "✔ Batería: no la cierra\n" else "• Batería: sin excepción (paso 2, opcional)\n")
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
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                resultado.text = "Esta tablet no muestra ese aviso. No hace falta: la app sigue vigilando."
            }
        }
    }

    private fun confirmarOrdenarTodo() {
        if (ordenando) return
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
        ordenando = true
        botonOrdenar.isEnabled = false
        resultado.text = "Buscando imágenes en toda la tablet…"
        Thread {
            val n = try {
                Ordenador.ordenarTodo(applicationContext) { hechas, total ->
                    runOnUiThread {
                        resultado.text = if (total == 0) {
                            "No encontré imágenes para mover."
                        } else {
                            "Moviendo $hechas de $total…"
                        }
                    }
                }
            } catch (e: Exception) {
                -1
            }
            runOnUiThread {
                ordenando = false
                botonOrdenar.isEnabled = true
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
