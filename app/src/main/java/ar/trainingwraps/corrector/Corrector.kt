package ar.trainingwraps.corrector

import android.content.Context

/**
 * Motor de corrección ortográfica en español.
 *
 * Carga un diccionario de ~50.000 palabras con su frecuencia de uso
 * (assets/es_words.txt, una palabra y su frecuencia por línea) y corrige:
 *  1. Tildes faltantes: "medico" -> "médico", "aplicacion" -> "aplicación".
 *  2. Errores de tipeo de una letra (falta, sobra, cambiada o invertida):
 *     "vinlo" -> "vinilo", "lmainado" -> "laminado".
 *
 * Reglas conservadoras para no romper nada:
 *  - Las palabras que empiezan con mayúscula solo reciben tildes (no se
 *    "corrigen" marcas ni nombres propios como Alltak o Garcete).
 *  - Solo se corrige hacia palabras de uso frecuente.
 *  - Las palabras del diccionario personal (PalabrasPropias) no se corrigen.
 */
class Corrector(context: Context) {

    private val ctx = context.applicationContext

    private val frecuencia = HashMap<String, Long>(60000)
    private val mejorConTilde = HashMap<String, String>(60000)

    @Volatile
    var listo = false
        private set

    private val letras = "abcdefghijklmnopqrstuvwxyzáéíóúüñ"

    init {
        // El diccionario se carga en segundo plano para no trabar el arranque.
        // es_extra.txt (vocabulario del rubro) se carga al final y pisa al general.
        Thread {
            try {
                for (archivo in listOf("es_words.txt", "es_extra.txt")) {
                    context.assets.open(archivo).bufferedReader(Charsets.UTF_8).forEachLine { linea ->
                        val partes = linea.trim().split(' ')
                        if (partes.size == 2) {
                            val palabra = partes[0]
                            val f = partes[1].toLongOrNull() ?: return@forEachLine
                            if ((frecuencia[palabra] ?: 0L) < f) frecuencia[palabra] = f
                            val base = quitarTildes(palabra)
                            val actual = mejorConTilde[base]
                            if (actual == null || (frecuencia[actual] ?: 0L) < (frecuencia[palabra] ?: 0L)) {
                                mejorConTilde[base] = palabra
                            }
                        }
                    }
                }
                listo = true
            } catch (_: Exception) {
                // Si el diccionario no carga, el teclado sigue funcionando sin corregir.
            }
        }.start()
    }

    private fun quitarTildes(w: String): String = buildString(w.length) {
        for (c in w) append(
            when (c) {
                'á' -> 'a'; 'é' -> 'e'; 'í' -> 'i'; 'ó' -> 'o'; 'ú' -> 'u'; 'ü' -> 'u'
                else -> c
            }
        )
    }

    /** Devuelve la palabra corregida, o null si está bien o no hay candidato confiable. */
    fun corregir(palabra: String): String? {
        if (!listo || palabra.length < 2) return null
        val w = palabra.lowercase()
        if (w.any { it !in letras }) return null

        // Palabras del diccionario personal: no se tocan nunca.
        val propias = PalabrasPropias.todas(ctx)
        if (w in propias) return null

        val fPropia = frecuencia[w] ?: 0L

        // 1) Restitución de tildes: solo si la versión con tilde es mucho más usada.
        val conTilde = mejorConTilde[quitarTildes(w)]
        if (conTilde != null && conTilde != w && (frecuencia[conTilde] ?: 0L) > fPropia * 20 + 50) {
            return conservarMayusculas(palabra, conTilde)
        }

        // Palabra conocida: se deja como está.
        if (fPropia > 0L) return null

        // Nombres propios o marcas (mayúscula inicial) no se tocan.
        if (palabra.first().isUpperCase()) return null
        if (palabra.length < 3) return null

        // 2) Error de tipeo de una letra: se elige el candidato más frecuente.
        var mejor: String? = null
        var mejorF = 200L // umbral: no corregir hacia palabras rarísimas
        // Palabras cortas (4 letras o menos): solo se corrigen letras invertidas
        // ("qeu" -> "que"). Cambiar una letra en una palabra corta da otra
        // palabra real con demasiada facilidad ("caba" -> "casa").
        val candidatos = if (w.length <= 4) invertidas(w) else variantes(w)
        for (cand in candidatos) {
            val f = frecuencia[cand] ?: (if (cand in propias) 5000L else continue)
            if (f > mejorF) {
                mejorF = f
                mejor = cand
            }
        }
        return mejor?.let { conservarMayusculas(palabra, it) }
    }

    private fun conservarMayusculas(original: String, corregida: String): String = when {
        original.length > 1 && original.all { it.isUpperCase() } -> corregida.uppercase()
        original.first().isUpperCase() -> corregida.replaceFirstChar { it.uppercaseChar() }
        else -> corregida
    }

    /** Solo variantes con dos letras seguidas invertidas. */
    private fun invertidas(w: String): Sequence<String> = sequence {
        for (i in 0 until w.length - 1) {
            yield(w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2))
        }
    }

    /** Todas las variantes a distancia de edición 1. */
    private fun variantes(w: String): Sequence<String> = sequence {
        // una letra de menos
        for (i in w.indices) yield(w.removeRange(i, i + 1))
        // dos letras invertidas
        for (i in 0 until w.length - 1) {
            yield(w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2))
        }
        // una letra cambiada o una letra de más
        for (i in 0..w.length) {
            for (c in letras) {
                if (i < w.length) yield(w.substring(0, i) + c + w.substring(i + 1))
                yield(w.substring(0, i) + c + w.substring(i))
            }
        }
    }
}
