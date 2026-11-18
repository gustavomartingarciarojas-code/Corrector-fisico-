package ar.trainingwraps.corrector

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * v1.8 — Lee SOLO la barra de direcciones de Chrome, y solo en el momento en que
 * se aprieta Enter, para saber si la página es claude.ai. No guarda ni manda nada.
 *
 * Con eso el teclado decide: en claude.ai, Enter = Ctrl+Enter (enviar);
 * en cualquier otra página (por ejemplo, el webmail), Enter sigue siendo Enter.
 */
class LectorChrome : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instancia = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No hace falta escuchar nada: la dirección se lee recién cuando se aprieta Enter.
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instancia = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instancia = null
        super.onDestroy()
    }

    /** Texto de la barra de direcciones de Chrome, o null si no se pudo leer. */
    fun direccionChrome(): String? {
        val raices = mutableListOf<AccessibilityNodeInfo>()
        try {
            rootInActiveWindow?.let { raices.add(it) }
        } catch (_: Exception) {}
        try {
            for (w in windows) w.root?.let { raices.add(it) }
        } catch (_: Exception) {}
        for (raiz in raices) {
            if (raiz.packageName?.toString() != CHROME) continue
            try {
                val nodos = raiz.findAccessibilityNodeInfosByViewId("$CHROME:id/url_bar")
                val texto = nodos.firstOrNull()?.text?.toString()
                if (!texto.isNullOrBlank()) return texto
            } catch (_: Exception) {}
        }
        return null
    }

    companion object {
        const val CHROME = "com.android.chrome"

        @Volatile
        var instancia: LectorChrome? = null
    }
}
