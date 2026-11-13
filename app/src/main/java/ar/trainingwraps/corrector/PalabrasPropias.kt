package ar.trainingwraps.corrector

import android.content.Context
import java.io.File

/**
 * Diccionario personal: palabras que el usuario agrega y que el corrector
 * nunca toca (argentinismos, nombres, marcas, términos del rubro).
 *
 * Se guardan en un archivo de texto dentro de la app (una palabra por línea)
 * y se recargan solas cuando el archivo cambia, así lo que se agrega desde
 * la pantalla de la app o con Retroceso vale al instante en el teclado.
 */
object PalabrasPropias {

    private const val ARCHIVO = "mis_palabras.txt"

    private var cache: Set<String> = emptySet()
    private var modificado = -1L

    private fun archivo(context: Context) = File(context.applicationContext.filesDir, ARCHIVO)

    @Synchronized
    fun todas(context: Context): Set<String> {
        val f = archivo(context)
        val m = if (f.exists()) f.lastModified() else 0L
        if (m != modificado) {
            cache = if (f.exists()) {
                f.readLines(Charsets.UTF_8).map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSortedSet()
            } else emptySet()
            modificado = m
        }
        return cache
    }

    fun contiene(context: Context, palabra: String) = palabra.lowercase() in todas(context)

    /** Devuelve true si la palabra era nueva. */
    @Synchronized
    fun agregar(context: Context, palabra: String): Boolean {
        val p = palabra.trim().lowercase()
        if (p.isEmpty() || p.contains(' ')) return false
        val actuales = todas(context)
        if (p in actuales) return false
        guardar(context, actuales + p)
        return true
    }

    @Synchronized
    fun quitar(context: Context, palabra: String) {
        guardar(context, todas(context) - palabra.lowercase())
    }

    private fun guardar(context: Context, palabras: Set<String>) {
        val f = archivo(context)
        f.writeText(palabras.sorted().joinToString("\n"), Charsets.UTF_8)
        modificado = -1L // fuerza la recarga en la próxima consulta
    }
}
