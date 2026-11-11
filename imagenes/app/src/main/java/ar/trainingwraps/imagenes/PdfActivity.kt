package ar.trainingwraps.imagenes

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Visor de PDF propio: reemplaza al navegador para abrir PDF.
 * Páginas una debajo de la otra; "Volver" regresa a la app de donde vino.
 */
class PdfActivity : Activity() {

    private lateinit var lista: ListView
    private lateinit var nombre: TextView
    private lateinit var paginas: TextView

    private var renderer: PdfRenderer? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    private var anchoPagina = 1400

    private val hilo: ExecutorService = Executors.newSingleThreadExecutor()
    private val cache = object : LruCache<Int, Bitmap>(
        ((Runtime.getRuntime().maxMemory() / 1024) / 4).toInt()
    ) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val datos = intent?.data
        if (datos == null) {
            finish()
            return
        }
        uri = datos
        anchoPagina = minOf(resources.displayMetrics.widthPixels, 1600)

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(25, 25, 25))
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
            setTextColor(Color.rgb(220, 220, 220))
            setPadding(24, 0, 0, 0)
            maxLines = 1
        }
        arriba.addView(nombre, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        paginas = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(160, 160, 160))
            setPadding(16, 0, 16, 0)
        }
        arriba.addView(paginas)
        arriba.addView(Button(this).apply {
            text = "Compartir"
            setTypeface(typeface, Typeface.BOLD)
            setOnClickListener { compartir() }
        })
        raiz.addView(arriba)

        lista = ListView(this).apply {
            dividerHeight = 16
            divider = null
            setPadding(16, 0, 16, 16)
            clipToPadding = false
        }
        raiz.addView(lista, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        raiz.setOnApplyWindowInsetsListener { v, insets ->
            val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        setContentView(raiz)

        nombre.text = nombreDe(datos)
        paginas.text = "Abriendo…"
        hilo.execute {
            val r = abrir(datos)
            runOnUiThread {
                if (r == null) {
                    Toast.makeText(this, "No pude abrir este PDF", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    renderer = r
                    paginas.text = if (r.pageCount == 1) "1 página" else "${r.pageCount} páginas"
                    lista.adapter = Adaptador()
                }
            }
        }
    }

    override fun onDestroy() {
        hilo.shutdownNow()
        try {
            renderer?.close()
        } catch (e: Exception) {
            // ya cerrado
        }
        try {
            descriptor?.close()
        } catch (e: Exception) {
            // ya cerrado
        }
        super.onDestroy()
    }

    private fun nombreDe(u: Uri): String {
        try {
            contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getString(0)
                    if (!n.isNullOrBlank()) return n
                }
            }
        } catch (e: Exception) {
            // sin nombre
        }
        return u.lastPathSegment ?: "PDF"
    }

    /** Abre el PDF directo; si la app de origen no lo permite, lo copia primero. */
    private fun abrir(u: Uri): PdfRenderer? {
        try {
            val d = contentResolver.openFileDescriptor(u, "r")
            if (d != null) {
                descriptor = d
                return PdfRenderer(d)
            }
        } catch (e: Exception) {
            try {
                descriptor?.close()
            } catch (e2: Exception) {
                // nada
            }
            descriptor = null
        }
        return try {
            val copia = File(cacheDir, "visor.pdf")
            val entrada = contentResolver.openInputStream(u) ?: return null
            entrada.use { i -> copia.outputStream().use { o -> i.copyTo(o) } }
            val d = ParcelFileDescriptor.open(copia, ParcelFileDescriptor.MODE_READ_ONLY)
            descriptor = d
            PdfRenderer(d)
        } catch (e: Exception) {
            null
        }
    }

    private fun compartir() {
        val u = uri ?: return
        val envio = Intent(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_STREAM, u)
            .setType("application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        envio.clipData = ClipData.newRawUri("pdf", u)
        try {
            startActivity(Intent.createChooser(envio, "Compartir"))
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo compartir", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class Adaptador : BaseAdapter() {
        override fun getCount(): Int = renderer?.pageCount ?: 0
        override fun getItem(position: Int): Any = position
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val iv = (convertView as? ImageView) ?: ImageView(this@PdfActivity).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.WHITE)
            }
            iv.tag = position
            val enCache = cache.get(position)
            if (enCache != null) {
                iv.minimumHeight = 0
                iv.setImageBitmap(enCache)
                return iv
            }
            iv.setImageDrawable(null)
            iv.minimumHeight = (lista.width * 1.41f).toInt().coerceAtLeast(400)
            try {
                hilo.execute {
                    if (iv.tag != position) return@execute
                    val r = renderer ?: return@execute
                    val bmp = try {
                        r.openPage(position).use { p ->
                            val alto = (anchoPagina.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
                            val b = Bitmap.createBitmap(anchoPagina, alto, Bitmap.Config.ARGB_8888)
                            b.eraseColor(Color.WHITE)
                            p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            b
                        }
                    } catch (e: Exception) {
                        null
                    }
                    if (bmp != null) {
                        cache.put(position, bmp)
                        runOnUiThread {
                            if (iv.tag == position) {
                                iv.minimumHeight = 0
                                iv.setImageBitmap(bmp)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // la pantalla se está cerrando
            }
            return iv
        }
    }
}
