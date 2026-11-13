package ar.trainingwraps.corrector

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Pantalla de instrucciones, activación del teclado y diccionario personal. */
class MainActivity : Activity() {

    private lateinit var lista: LinearLayout
    private lateinit var titulo: TextView
    private lateinit var estado: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val columna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val version = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "?" }
        columna.addView(TextView(this).apply {
            textSize = 22f
            text = "Versión $version"
            setPadding(0, 0, 0, 24)
        })

        // Estado del teclado: se ve de un vistazo si está escribiendo Corrector físico o Gboard.
        estado = TextView(this).apply {
            textSize = 20f
            setPadding(32, 32, 32, 32)
        }
        columna.addView(estado)

        columna.addView(TextView(this).apply {
            textSize = 16f
            text = """
                Corrector físico — corrector ortográfico en español para teclado Bluetooth.

                Cómo activarlo:
                1. Tocá "Activar teclado" y encendé "Corrector físico" en la lista.
                2. Tocá "Elegir teclado" y seleccioná "Corrector físico".
                3. Listo: escribí con tu teclado Bluetooth. Corrige al apretar espacio.

                Si una corrección no te gusta, apretá Retroceso una vez: vuelve la palabra original y queda guardada en "Mis palabras", así no la vuelve a corregir.

                Este teclado no dibuja nada en pantalla: está hecho para el teclado físico. Si sacás el teclado Bluetooth, volvé a Gboard repitiendo el paso 2.
            """.trimIndent()
        })

        columna.addView(Button(this).apply {
            text = "1. Activar teclado"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        })

        columna.addView(Button(this).apply {
            text = "2. Elegir teclado"
            setOnClickListener {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        })

        // ---- Mis palabras (diccionario personal) ----
        titulo = TextView(this).apply {
            textSize = 20f
            setPadding(0, 48, 0, 8)
        }
        columna.addView(titulo)

        columna.addView(TextView(this).apply {
            textSize = 14f
            text = "Palabras que el corrector nunca cambia: argentinismos, nombres, marcas, términos del rubro. Una palabra por vez, sin espacios."
        })

        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val campo = EditText(this).apply {
            hint = "Ej.: relevamiento"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE
            isSingleLine = true
        }
        val agregar = Button(this).apply { text = "Agregar" }
        fila.addView(campo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        fila.addView(agregar)
        columna.addView(fila)

        val accionAgregar = {
            val texto = campo.text.toString().trim()
            when {
                texto.isEmpty() -> {}
                texto.contains(' ') -> aviso("Una palabra por vez, sin espacios")
                PalabrasPropias.agregar(this, texto) -> {
                    aviso("\"${texto.lowercase()}\" agregada")
                    campo.setText("")
                    refrescarLista()
                }
                else -> aviso("Ya estaba en la lista")
            }
        }
        agregar.setOnClickListener { accionAgregar() }
        campo.setOnEditorActionListener { _, _, _ -> accionAgregar(); true }

        lista = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        columna.addView(lista)

        setContentView(ScrollView(this).apply { addView(columna) })
        refrescarLista()
    }

    override fun onResume() {
        super.onResume()
        // Puede haber palabras nuevas aprendidas con Retroceso desde el teclado.
        if (::lista.isInitialized) refrescarLista()
        if (::estado.isInitialized) refrescarEstado()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Al cerrar la ventanita "Elegir teclado" la pantalla no se reanuda: se actualiza acá.
        if (hasFocus && ::estado.isInitialized) refrescarEstado()
    }

    private fun refrescarEstado() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val activado = try {
            imm.enabledInputMethodList.any { it.packageName == packageName }
        } catch (_: Exception) { false }
        val actual = try {
            Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()
        } catch (_: Exception) { "" }
        val elegido = actual.startsWith("$packageName/")
        when {
            elegido -> {
                estado.text = "✅ FUNCIONANDO: el teclado que escribe es Corrector físico."
                estado.setBackgroundColor(0xFF1B5E20.toInt())
            }
            activado -> {
                estado.text = "❌ NO ESTÁ ELEGIDO: ahora escribe otro teclado (Gboard) y no corrige.\nTocá \"2. Elegir teclado\"."
                estado.setBackgroundColor(0xFFB71C1C.toInt())
            }
            else -> {
                estado.text = "❌ NO ESTÁ ACTIVADO: no corrige.\nTocá \"1. Activar teclado\" y después \"2. Elegir teclado\"."
                estado.setBackgroundColor(0xFFB71C1C.toInt())
            }
        }
        estado.setTextColor(0xFFFFFFFF.toInt())
    }

    private fun refrescarLista() {
        val palabras = PalabrasPropias.todas(this)
        titulo.text = "Mis palabras (${palabras.size})"
        lista.removeAllViews()
        for (p in palabras) {
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            fila.addView(TextView(this).apply {
                text = p
                textSize = 16f
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            fila.addView(Button(this).apply {
                text = "Quitar"
                setOnClickListener {
                    PalabrasPropias.quitar(this@MainActivity, p)
                    refrescarLista()
                }
            })
            lista.addView(fila)
        }
    }

    private fun aviso(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
