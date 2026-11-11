package ar.trainingwraps.imagenes

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Entrega las imágenes de la carpeta Imagenes a otras apps (Claude, WhatsApp,
 * Gmail, Galería…) cuando las compartís o las adjuntás. Solo lectura, y solo
 * archivos de esa carpeta.
 */
class ArchivoProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    private fun archivo(uri: Uri): File? {
        val nombre = uri.lastPathSegment ?: return null
        if (nombre.contains('/') || nombre.startsWith(".")) return null
        val f = File(Ordenador.destino, nombre)
        return if (f.isFile) f else null
    }

    override fun getType(uri: Uri): String? = archivo(uri)?.let { mime(it.name) }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val f = archivo(uri) ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val f = archivo(uri) ?: return null
        val columnas = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(columnas)
        val fila = ArrayList<Any?>()
        for (col in columnas) {
            fila.add(
                when (col) {
                    OpenableColumns.DISPLAY_NAME -> f.name
                    OpenableColumns.SIZE -> f.length()
                    else -> null
                }
            )
        }
        cursor.addRow(fila)
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0

    companion object {
        const val AUTORIDAD = "ar.trainingwraps.imagenes.archivos"

        fun uri(f: File): Uri =
            Uri.Builder().scheme("content").authority(AUTORIDAD).appendPath(f.name).build()

        fun mime(nombre: String): String =
            when (nombre.substringAfterLast('.', "").lowercase()) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "heic" -> "image/heic"
                "heif" -> "image/heif"
                "bmp" -> "image/bmp"
                else -> "image/jpeg"
            }
    }
}
