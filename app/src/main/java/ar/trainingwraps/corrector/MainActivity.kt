package ar.trainingwraps.corrector

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Pantalla de instrucciones y activación del teclado. */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val columna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        columna.addView(TextView(this).apply {
            textSize = 16f
            text = """
                Corrector físico — corrector ortográfico en español para teclado Bluetooth.

                Cómo activarlo:
                1. Tocá "Activar teclado" y encendé "Corrector físico" en la lista.
                2. Tocá "Elegir teclado" y seleccioná "Corrector físico".
                3. Listo: escribí con tu teclado Bluetooth. Corrige al apretar espacio.

                Si una corrección no te gusta, apretá Retroceso una vez y vuelve la palabra original.

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

        setContentView(ScrollView(this).apply { addView(columna) })
    }
}
