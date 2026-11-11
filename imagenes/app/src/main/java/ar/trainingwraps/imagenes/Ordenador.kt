package ar.trainingwraps.imagenes

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaScannerConnection
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Junta todas las imágenes de la memoria interna en una sola carpeta:
 * Almacenamiento interno > Imagenes (sin subcarpetas).
 *
 * Se mueven: .jpg .jpeg .png .webp .gif .heic .heif .bmp
 *
 * NO se tocan (para no romper otras apps):
 *  - Lo que ya está en Imagenes.
 *  - Carpetas ocultas (empiezan con punto) y carpetas con ".nomedia":
 *    son cachés y miniaturas de las apps.
 *  - La carpeta MIUI (fondos y temas del sistema) y las carpetas "com.algo"
 *    que crean las apps para sí mismas.
 *  - Android/ (datos de las apps). Excepción: WhatsApp, WhatsApp Business y
 *    Telegram, solo si se activa la opción en la app.
 */
object Ordenador {

    const val CARPETA = "Imagenes"
    const val CLAVE_MENSAJERIA = "incluir_mensajeria"
    const val CLAVE_ULTIMA = "ultima_movida"
    const val CLAVE_TOTAL = "total_movidas"

    /** Hay archivos que se estaban guardando y quedaron para el próximo barrido. */
    @Volatile
    var hayPendientes = false
        private set

    private val extensiones = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp")

    private val mensajeria = listOf(
        "Android/media/com.whatsapp/",
        "Android/media/com.whatsapp.w4b/",
        "Android/media/org.telegram.messenger/",
    )

    private val raiz: File
        get() = Environment.getExternalStorageDirectory()

    val destino: File
        get() = File(raiz, CARPETA)

    fun ajustes(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    fun tienePermiso(): Boolean = Environment.isExternalStorageManager()

    /** Ruta relativa a la memoria interna, o null si el archivo no está ahí. */
    private fun relativa(ruta: String): String? {
        val base = raiz.absolutePath + "/"
        return if (ruta.startsWith(base)) ruta.substring(base.length) else null
    }

    private fun corresponde(ruta: String, incluirMensajeria: Boolean): Boolean {
        val rel = relativa(ruta) ?: return false
        val partes = rel.split('/')
        val nombre = partes.last()
        val carpetas = partes.dropLast(1)

        if (nombre.startsWith(".")) return false
        if (nombre.substringAfterLast('.', "").lowercase() !in extensiones) return false
        if (carpetas.any { it.startsWith(".") }) return false
        if (carpetas.isNotEmpty() && carpetas[0].equals(CARPETA, ignoreCase = true)) return false

        if (carpetas.isNotEmpty() && carpetas[0] == "Android") {
            return incluirMensajeria &&
                mensajeria.any { rel.startsWith(it) } &&
                carpetas.none { it.contains("Stickers") || it.contains("Profile Photos") }
        }

        if (carpetas.isNotEmpty() && carpetas[0].equals("MIUI", ignoreCase = true)) return false
        if (carpetas.any { it.startsWith("com.") }) return false
        return true
    }

    /**
     * Vigilancia automática: revisa lo que entró en los últimos 2 días,
     * por la galería del sistema y por las carpetas de siempre.
     */
    fun ordenarRecientes(ctx: Context): Int {
        if (!tienePermiso()) return 0
        val incluir = ajustes(ctx).getBoolean(CLAVE_MENSAJERIA, false)
        val desdeMs = System.currentTimeMillis() - 2L * 24 * 3600 * 1000
        val candidatas = LinkedHashMap<String, File>()

        // 1) Lo que la galería del sistema (MediaStore) tiene como nuevo.
        try {
            @Suppress("DEPRECATION")
            val colRuta = MediaStore.Images.Media.DATA
            val uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            ctx.contentResolver.query(
                uri,
                arrayOf(colRuta),
                "${MediaStore.Images.Media.DATE_ADDED} >= ?",
                arrayOf((desdeMs / 1000).toString()),
                null
            )?.use { c ->
                val i = c.getColumnIndexOrThrow(colRuta)
                while (c.moveToNext()) {
                    val ruta = c.getString(i) ?: continue
                    if (corresponde(ruta, incluir)) candidatas[ruta] = File(ruta)
                }
            }
        } catch (e: Exception) {
            // Si la galería falla, sigue el recorrido de carpetas.
        }

        // 2) Recorrido directo de las carpetas de siempre, por si algo no pasó por la galería.
        val base = raiz
        base.listFiles()?.forEach { f ->
            if (f.isFile && f.lastModified() >= desdeMs && corresponde(f.absolutePath, incluir)) {
                candidatas[f.absolutePath] = f
            }
        }
        for (nombre in listOf("DCIM", "Pictures", "Download", "Documents", "Movies")) {
            val dir = File(base, nombre)
            if (!dir.isDirectory) continue
            dir.walkTopDown()
                .maxDepth(3)
                .onEnter { !it.name.startsWith(".") && !File(it, ".nomedia").exists() }
                .filter { it.isFile && it.lastModified() >= desdeMs }
                .forEach { f ->
                    if (corresponde(f.absolutePath, incluir)) candidatas[f.absolutePath] = f
                }
        }

        return moverTodas(ctx, candidatas.values.toList())
    }

    /**
     * Botón "Ordenar todo": recorre toda la memoria interna y junta todo lo que ya existe.
     * [alAvanzar] recibe (movidas hasta ahora, total encontradas) para mostrar el avance.
     */
    fun ordenarTodo(ctx: Context, alAvanzar: ((Int, Int) -> Unit)? = null): Int {
        if (!tienePermiso()) return 0
        val incluir = ajustes(ctx).getBoolean(CLAVE_MENSAJERIA, false)
        val base = raiz
        val encontradas = ArrayList<File>()
        base.walkTopDown()
            .onEnter { dir ->
                if (dir.absolutePath == base.absolutePath) return@onEnter true
                val rel = relativa(dir.absolutePath) ?: return@onEnter false
                when {
                    dir.name.startsWith(".") -> false
                    File(dir, ".nomedia").exists() -> false
                    rel.equals(CARPETA, ignoreCase = true) -> false
                    rel == "Android" -> incluir
                    else -> true
                }
            }
            .filter { it.isFile }
            .forEach { f -> if (corresponde(f.absolutePath, incluir)) encontradas.add(f) }
        alAvanzar?.invoke(0, encontradas.size)
        return moverTodas(ctx, encontradas, alAvanzar)
    }

    @Synchronized
    private fun moverTodas(
        ctx: Context,
        archivos: List<File>,
        alAvanzar: ((Int, Int) -> Unit)? = null,
    ): Int {
        hayPendientes = false
        if (archivos.isEmpty()) return 0
        val carpeta = destino
        if (!carpeta.exists() && !carpeta.mkdirs()) return 0

        val ahora = System.currentTimeMillis()
        val aEscanear = ArrayList<String>()
        var movidas = 0
        var ultima: String? = null

        for (origen in archivos) {
            if (!origen.isFile) continue
            // Si se modificó hace menos de 3 segundos, todavía se está guardando.
            if (ahora - origen.lastModified() < 3000) {
                hayPendientes = true
                continue
            }
            val nuevo = moverUno(origen, carpeta) ?: continue
            aEscanear.add(origen.absolutePath)
            aEscanear.add(nuevo.absolutePath)
            movidas++
            ultima = nuevo.name
            if (alAvanzar != null && movidas % 25 == 0) alAvanzar(movidas, archivos.size)
        }
        alAvanzar?.invoke(movidas, archivos.size)

        if (aEscanear.isNotEmpty()) {
            // Avisa a la galería: borra la entrada vieja y registra la nueva.
            MediaScannerConnection.scanFile(ctx.applicationContext, aEscanear.toTypedArray(), null, null)
        }
        if (movidas > 0) {
            val a = ajustes(ctx)
            a.edit()
                .putString(CLAVE_ULTIMA, ultima)
                .putLong(CLAVE_TOTAL, a.getLong(CLAVE_TOTAL, 0) + movidas)
                .apply()
        }
        return movidas
    }

    /** Mueve un archivo a la carpeta única. Si el nombre ya existe, agrega _1, _2… */
    private fun moverUno(origen: File, carpeta: File): File? {
        val nombre = origen.name
        val base = nombre.substringBeforeLast('.')
        val ext = nombre.substringAfterLast('.', "")
        var nuevo = File(carpeta, nombre)
        var n = 1
        while (nuevo.exists()) {
            nuevo = File(carpeta, "${base}_$n.$ext")
            n++
        }
        if (origen.renameTo(nuevo)) return nuevo

        // Si no se pudo renombrar, copia y borra el original (nunca deja duplicados).
        return try {
            origen.copyTo(nuevo)
            nuevo.setLastModified(origen.lastModified())
            if (origen.delete()) {
                nuevo
            } else {
                nuevo.delete()
                null
            }
        } catch (e: Exception) {
            nuevo.delete()
            null
        }
    }
}
