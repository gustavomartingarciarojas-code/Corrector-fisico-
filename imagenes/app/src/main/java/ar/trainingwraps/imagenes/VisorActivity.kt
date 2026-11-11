package ar.trainingwraps.imagenes

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * Visor de imágenes propio, con zoom (pellizcar, doble toque o rueda del mouse).
 *  - Desde la galería de la app: Compartir · Borrar, y deslizar para pasar.
 *  - Desde otras apps (Files, Gmail, WhatsApp…): reemplaza al visor del navegador;
 *    "Volver" regresa a la app de donde vino.
 */
class VisorActivity : Activity() {

    private lateinit var imagen: ZoomImageView
    private lateinit var nombre: TextView

    private var lista: List<File> = emptyList()
    private var indice = 0
    private var externo: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == Intent.ACTION_VIEW && intent?.data != null) {
            externo = intent.data
        } else {
            lista = GaleriaActivity.listaVisor
            indice = intent.getIntExtra(EXTRA_INDICE, 0)
            if (lista.isEmpty()) {
                finish()
                return
            }
        }

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        val arriba = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 8, 16, 8)
        }
        arriba.addView(Button(this).apply {
            text = "‹ Volver"
            setOnClickListener { finish() }
        })
        nombre = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(200, 200, 200))
            setPadding(24, 0, 0, 0)
            maxLines = 1
        }
        arriba.addView(nombre, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        raiz.addView(arriba)

        imagen = ZoomImageView(this)
        imagen.alDeslizar = { paso -> mover(paso) }
        val marco = FrameLayout(this)
        marco.addView(imagen, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        raiz.addView(marco, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val abajo = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 8, 16, 8)
        }
        abajo.addView(Button(this).apply {
            text = "Compartir"
            setTypeface(typeface, Typeface.BOLD)
            setOnClickListener { compartir() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        abajo.addView(Button(this).apply {
            text = "Borrar"
            setTextColor(Color.rgb(255, 110, 110))
            setOnClickListener { confirmarBorrar() }
            visibility = if (externo != null) View.GONE else View.VISIBLE
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        abajo.addView(TextView(this).apply {
            textSize = 12f
            setTextColor(Color.rgb(150, 150, 150))
            setPadding(24, 0, 0, 0)
            text = "Zoom: pellizcá, doble toque o rueda del mouse"
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f))
        raiz.addView(abajo)

        raiz.setOnApplyWindowInsetsListener { v, insets ->
            val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        setContentView(raiz)

        mostrar()
    }

    private fun actual(): File? = lista.getOrNull(indice)

    private fun mover(paso: Int) {
        if (externo != null) return
        val nuevo = indice + paso
        if (nuevo < 0 || nuevo >= lista.size) return
        indice = nuevo
        mostrar()
    }

    private fun nombreDe(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getString(0)
                    if (!n.isNullOrBlank()) return n
                }
            }
        } catch (e: Exception) {
            // sin nombre
        }
        return uri.lastPathSegment ?: "Imagen"
    }

    private fun mostrar() {
        val uriExterno = externo
        val fuente: ImageDecoder.Source
        if (uriExterno != null) {
            nombre.text = nombreDe(uriExterno)
            fuente = ImageDecoder.createSource(contentResolver, uriExterno)
        } else {
            val f = actual() ?: run {
                finish()
                return
            }
            nombre.text = "${indice + 1} de ${lista.size} · ${f.name}"
            fuente = ImageDecoder.createSource(f)
        }
        imagen.setImageDrawable(null)
        // Se decodifica más grande que la pantalla para que el zoom se vea nítido.
        val maximo = 2 * maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        val pedido = indice
        Thread {
            val bmp = try {
                ImageDecoder.decodeBitmap(fuente) { decoder, info, _ ->
                    val escala = maxOf(
                        info.size.width.toFloat() / maximo,
                        info.size.height.toFloat() / maximo,
                        1f
                    )
                    decoder.setTargetSize(
                        (info.size.width / escala).toInt().coerceAtLeast(1),
                        (info.size.height / escala).toInt().coerceAtLeast(1)
                    )
                }
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (pedido != indice) return@runOnUiThread
                if (bmp != null) {
                    imagen.setImageBitmap(bmp)
                } else {
                    Toast.makeText(this, "No pude abrir esta imagen", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun compartir() {
        val uriExterno = externo
        val uri: Uri
        val tipo: String
        if (uriExterno != null) {
            uri = uriExterno
            tipo = contentResolver.getType(uriExterno) ?: "image/*"
        } else {
            val f = actual() ?: return
            uri = ArchivoProvider.uri(f)
            tipo = ArchivoProvider.mime(f.name)
        }
        val envio = Intent(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .setType(tipo)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        envio.clipData = ClipData.newRawUri("imagen", uri)
        try {
            startActivity(Intent.createChooser(envio, "Compartir"))
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo compartir", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmarBorrar() {
        if (externo != null) return
        val f = actual() ?: return
        AlertDialog.Builder(this)
            .setTitle("Borrar")
            .setMessage("¿Borrar esta imagen? Va a la papelera: durante 30 días la podés recuperar desde Ajustes.")
            .setPositiveButton("Sí, borrar") { _, _ ->
                Ordenador.borrar(applicationContext, listOf(f))
                Toast.makeText(this, "Imagen borrada", Toast.LENGTH_SHORT).show()
                val resto = lista.toMutableList()
                resto.removeAt(indice)
                lista = resto
                GaleriaActivity.listaVisor = resto
                if (lista.isEmpty()) {
                    finish()
                } else {
                    if (indice >= lista.size) indice = lista.size - 1
                    mostrar()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    companion object {
        const val EXTRA_INDICE = "indice"
    }
}
