package ar.trainingwraps.corrector

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Toast

/**
 * Teclado (IME) pensado para teclado físico Bluetooth.
 *
 * No dibuja ningún teclado en pantalla: deja pasar todas las teclas tal cual
 * y, al apretar espacio, Enter o un signo de puntuación, revisa la última
 * palabra escrita y la corrige si hace falta.
 *
 * v1.1 — arreglo del error al editar en el medio del texto:
 * antes, la corrección y la tecla (espacio, coma) viajaban por dos caminos
 * distintos y a veces llegaban desordenadas a la app. Resultado: letras
 * duplicadas ("ppablo"), letras perdidas y espacios dobles. Ahora, cuando hay
 * corrección, el teclado escribe él mismo la palabra corregida y la tecla, en
 * una sola operación y en orden.
 * Además, si el cursor está pegado a otra letra (en el medio de una palabra),
 * no se corrige nada.
 *
 * Deshacer y aprender: si una corrección no gustó, apretar Retroceso UNA vez
 * inmediatamente después restaura la palabra original y la agrega al
 * diccionario personal, para que no la vuelva a tocar.
 */
class CorrectorIME : InputMethodService() {

    private lateinit var corrector: Corrector

    // Última corrección aplicada (para poder deshacerla con Retroceso).
    private var ultimaOriginal: String? = null
    private var ultimaCorregida: String? = null
    private var ultimoDisparador: String = " "

    // Tecla cuyo "soltar" hay que tragarse porque ya la procesamos nosotros.
    private var teclaConsumida: Int? = null

    // En contraseñas, mails y direcciones web no se corrige nada.
    private var campoCorregible = true

    override fun onCreate() {
        super.onCreate()
        corrector = Corrector(this)
        // Aviso visible: confirma que el teclado que quedó elegido es este.
        Toast.makeText(this, "Corrector físico activo", Toast.LENGTH_SHORT).show()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        limpiarUltima()
        teclaConsumida = null
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
        // Deshacer con Retroceso justo después de una corrección (y aprender la palabra).
        if (keyCode == KeyEvent.KEYCODE_DEL && ultimaCorregida != null) {
            if (deshacerYAprender()) {
                teclaConsumida = keyCode
                return true // la tecla Retroceso ya hizo su trabajo
            }
            return super.onKeyDown(keyCode, event)
        }

        if (event.repeatCount == 0 && esDisparador(keyCode, event)) {
            if (corregirYEscribir(keyCode, event)) {
                teclaConsumida = keyCode
                return true // ya escribimos nosotros la corrección y la tecla
            }
        } else if (event.isPrintingKey) {
            // Siguió escribiendo: ya no corresponde deshacer la corrección anterior.
            limpiarUltima()
        }

        // La tecla sigue su curso normal (el sistema escribe el carácter).
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == teclaConsumida) {
            teclaConsumida = null
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun esEnter(keyCode: Int) =
        keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

    private fun esDisparador(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SPACE || esEnter(keyCode)) return true
        val c = event.unicodeChar
        return c != 0 && c.toChar() in ".,;:!?)"
    }

    private fun esLetra(c: Char) = c.isLetter()

    /**
     * Si la palabra anterior al cursor necesita corrección, la reemplaza y
     * escribe también la tecla disparadora, todo en una sola operación.
     * Devuelve false si no había nada que corregir (la tecla sigue normal).
     */
    private fun corregirYEscribir(keyCode: Int, event: KeyEvent): Boolean {
        if (!campoCorregible) return false
        val ic = currentInputConnection ?: return false

        // Hay texto seleccionado: no tocar nada.
        val seleccion = ic.getSelectedText(0)
        if (!seleccion.isNullOrEmpty()) return false

        // Cursor en el medio de una palabra (pegado a otra letra): no corregir.
        val despues = ic.getTextAfterCursor(1, 0)?.toString().orEmpty()
        if (despues.isNotEmpty() && esLetra(despues[0])) return false

        val antes = ic.getTextBeforeCursor(48, 0)?.toString() ?: return false
        if (antes.isEmpty()) return false

        val coincidencia = Regex("([A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+)$").find(antes) ?: return false
        val palabra = coincidencia.groupValues[1]

        // La palabra tiene que empezar en un borde (no después de un número, @, etc.).
        val inicio = antes.length - palabra.length
        if (inicio > 0) {
            val previo = antes[inicio - 1]
            if (previo.isLetterOrDigit() || previo in "@_#/-") return false
        }

        val corregida = corrector.corregir(palabra) ?: run {
            limpiarUltima()
            return false
        }

        val enter = esEnter(keyCode)
        val disparador = if (enter) "\n" else event.unicodeChar.toChar().toString()

        ic.beginBatchEdit()
        ic.deleteSurroundingText(palabra.length, 0)
        if (enter) {
            ic.commitText(corregida, 1)
        } else {
            ic.commitText(corregida + disparador, 1)
        }
        ic.endBatchEdit()

        if (enter) {
            // Enter va por el mismo canal, así llega después de la corrección.
            ic.sendKeyEvent(KeyEvent(event.downTime, event.eventTime, KeyEvent.ACTION_DOWN, keyCode, 0, event.metaState))
            ic.sendKeyEvent(KeyEvent(event.downTime, event.eventTime, KeyEvent.ACTION_UP, keyCode, 0, event.metaState))
        }

        ultimaOriginal = palabra
        ultimaCorregida = corregida
        ultimoDisparador = disparador
        return true
    }

    /** Restaura la palabra original y la agrega al diccionario personal. */
    private fun deshacerYAprender(): Boolean {
        val corregida = ultimaCorregida ?: return false
        val original = ultimaOriginal ?: return false
        val disparador = ultimoDisparador
        limpiarUltima()
        val ic = currentInputConnection ?: return false
        val esperado = corregida + disparador
        val antes = ic.getTextBeforeCursor(esperado.length, 0)?.toString()
        if (antes != esperado) return false

        ic.beginBatchEdit()
        ic.deleteSurroundingText(esperado.length, 0)
        ic.commitText(original + disparador, 1)
        ic.endBatchEdit()

        if (PalabrasPropias.agregar(this, original)) {
            Toast.makeText(this, "\"$original\" agregada a Mis palabras", Toast.LENGTH_SHORT).show()
        }
        return true
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
