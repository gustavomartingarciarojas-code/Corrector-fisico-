package ar.trainingwraps.corrector

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo

/**
 * Teclado (IME) pensado para teclado físico Bluetooth.
 *
 * No dibuja ningún teclado en pantalla: deja pasar todas las teclas tal cual
 * y, al apretar espacio, Enter o un signo de puntuación, revisa la última
 * palabra escrita y la corrige si hace falta.
 *
 * Deshacer: si una corrección no gustó, apretar Retroceso UNA vez
 * inmediatamente después restaura la palabra original.
 */
class CorrectorIME : InputMethodService() {

    private lateinit var corrector: Corrector

    // Última corrección aplicada (para poder deshacerla con Retroceso).
    private var ultimaOriginal: String? = null
    private var ultimaCorregida: String? = null

    // En contraseñas, mails y direcciones web no se corrige nada.
    private var campoCorregible = true

    override fun onCreate() {
        super.onCreate()
        corrector = Corrector(this)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        limpiarUltima()
        val t = attribute?.inputType ?: 0
        val clase = t and InputType.TYPE_MASK_CLASS
        val variante = t and InputType.TYPE_MASK_VARIATION
        campoCorregible = clase == InputType.TYPE_CLASS_TEXT && variante !in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Deshacer con Retroceso justo después de una corrección.
        if (keyCode == KeyEvent.KEYCODE_DEL && ultimaCorregida != null) {
            val ic = currentInputConnection
            val corregida = ultimaCorregida!!
            val original = ultimaOriginal!!
            limpiarUltima()
            if (ic != null) {
                val antes = ic.getTextBeforeCursor(corregida.length + 1, 0)?.toString()
                if (antes == "$corregida ") {
                    ic.beginBatchEdit()
                    ic.deleteSurroundingText(corregida.length + 1, 0)
                    ic.commitText("$original ", 1)
                    ic.endBatchEdit()
                    return true // la tecla Retroceso ya hizo su trabajo
                }
            }
            return super.onKeyDown(keyCode, event)
        }

        if (esDisparador(keyCode, event)) {
            corregirPalabraAnterior()
        } else if (event.isPrintingKey) {
            // Siguió escribiendo: ya no corresponde deshacer la corrección anterior.
            limpiarUltima()
        }

        // La tecla sigue su curso normal (el sistema escribe el carácter).
        return super.onKeyDown(keyCode, event)
    }

    private fun esDisparador(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SPACE ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
        ) return true
        val c = event.unicodeChar
        return c != 0 && c.toChar() in ".,;:!?)"
    }

    private fun corregirPalabraAnterior() {
        if (!campoCorregible) return
        val ic = currentInputConnection ?: return
        val antes = ic.getTextBeforeCursor(48, 0)?.toString() ?: return
        if (antes.isEmpty()) return

        val coincidencia = Regex("([A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+)$").find(antes) ?: return
        val palabra = coincidencia.groupValues[1]

        val corregida = corrector.corregir(palabra)
        if (corregida == null) {
            limpiarUltima()
            return
        }

        ic.beginBatchEdit()
        ic.deleteSurroundingText(palabra.length, 0)
        ic.commitText(corregida, 1)
        ic.endBatchEdit()

        ultimaOriginal = palabra
        ultimaCorregida = corregida
    }

    private fun limpiarUltima() {
        ultimaOriginal = null
        ultimaCorregida = null
    }

    override fun onFinishInput() {
        limpiarUltima()
        super.onFinishInput()
    }
}
