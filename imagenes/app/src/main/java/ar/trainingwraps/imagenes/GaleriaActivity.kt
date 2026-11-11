package ar.trainingwraps.imagenes

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Bundle
import android.util.LruCache
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * La "carpeta en el escritorio": muestra todas las imágenes de Imagenes,
 * de la más nueva a la más vieja.
 *  - Tocar: abre la imagen.
 *  - Mantener apretado: elegir varias y Compartir.
 *  - Si otra app pide una imagen (adjuntar), tocar una la devuelve a esa app.
 */
class GaleriaActivity : Activity() {

    private lateinit var grilla: GridView
    private lateinit var titulo: TextView
    private lateinit var barraAbajo: LinearLayout
    private lateinit var botonAccion: Button
    private lateinit var adaptador: Adaptador

    private val archivos = ArrayList<File>()
    private val seleccion = LinkedHashSet<String>()
    private var modoElegir = false
    private var permiteVarias = false
    private var lado = 300

    private val ejecutor: ExecutorService = Executors.newFixedThreadPool(3)
    private val cache = object : LruCache<String, Bitmap>(
        ((Runtime.getRuntime().maxMemory() / 1024) / 6).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()

        modoElegir = intent?.action == Intent.ACTION_GET_CONTENT
        permiteVarias = intent?.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false) ?: false
        lado = (150 * resources.displayMetrics.density).toInt()

        val raiz = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val barraArriba = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(32, 16, 16, 16)
        }
        titulo = TextView(this).apply {
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        }
        barraArriba.addView(
            titulo,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        if (!modoElegir) {
            barraArriba.addView(Button(this).apply {
                text = "Ajustes"
                setOnClickListener {
                    startActivity(Intent(this@GaleriaActivity, AjustesActivity::class.java))
                }
            })
        }
        raiz.addView(barraArriba)

        grilla = GridView(this).apply {
            numColumns = GridView.AUTO_FIT
            columnWidth = lado
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            horizontalSpacing = 6
            verticalSpacing = 6
            setPadding(6, 6, 6, 6)
            clipToPadding = false
        }
        adaptador = Adaptador()
        grilla.adapter = adaptador
        grilla.setOnItemClickListener { _, _, pos, _ -> tocar(pos) }
        grilla.setOnItemLongClickListener { _, _, pos, _ ->
            alternar(pos)
            true
        }
        raiz.addView(
            grilla,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        barraAbajo = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 8, 16, 8)
            visibility = View.GONE
        }
        val cancelar = Button(this).apply {
            text = "Cancelar"
            setOnClickListener {
                seleccion.clear()
                refrescar()
            }
        }
        botonAccion = Button(this).apply {
            setTypeface(typeface, Typeface.BOLD)
            setOnClickListener { accionSeleccion() }
        }
        barraAbajo.addView(cancelar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        barraAbajo.addView(botonAccion, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        raiz.addView(barraAbajo)

        // Que nada quede tapado por la barra de arriba ni por la franja de abajo.
        raiz.setOnApplyWindowInsetsListener { v, insets ->
            val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        setContentView(raiz)
    }

    override fun onResume() {
        super.onResume()
        if (!Ordenador.tienePermiso()) {
            titulo.text = "Falta configurar: tocá Ajustes"
            if (!modoElegir && !yaMandoAAjustes) {
                yaMandoAAjustes = true
                startActivity(Intent(this, AjustesActivity::class.java))
            }
            return
        }
        VigiaService.arrancar(this)
        cargar()
    }

    override fun onDestroy() {
        ejecutor.shutdownNow()
        super.onDestroy()
    }

    private fun cargar() {
        val lista = Ordenador.destino.listFiles()
            ?.filter { it.isFile && !it.name.startsWith(".") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
        archivos.clear()
        archivos.addAll(lista)
        val existentes = lista.map { it.absolutePath }.toSet()
        seleccion.retainAll(existentes)
        refrescar()
    }

    private fun refrescar() {
        titulo.text = when {
            seleccion.isNotEmpty() -> "${seleccion.size} seleccionadas"
            modoElegir -> "Elegí una imagen"
            archivos.isEmpty() -> "Imagenes · todavía vacía"
            else -> "Imagenes · ${archivos.size}"
        }
        if (seleccion.isEmpty()) {
            barraAbajo.visibility = View.GONE
        } else {
            barraAbajo.visibility = View.VISIBLE
            botonAccion.text = if (modoElegir) {
                "Adjuntar (${seleccion.size})"
            } else {
                "Compartir (${seleccion.size})"
            }
        }
        adaptador.notifyDataSetChanged()
    }

    private fun tocar(pos: Int) {
        val f = archivos.getOrNull(pos) ?: return
        if (seleccion.isNotEmpty()) {
            alternar(pos)
            return
        }
        if (modoElegir) {
            devolver(listOf(f))
            return
        }
        val ver = Intent(Intent.ACTION_VIEW)
            .setDataAndType(ArchivoProvider.uri(f), ArchivoProvider.mime(f.name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(ver)
        } catch (e: Exception) {
            Toast.makeText(this, "No hay una app para ver imágenes", Toast.LENGTH_SHORT).show()
        }
    }

    private fun alternar(pos: Int) {
        val f = archivos.getOrNull(pos) ?: return
        if (modoElegir && !permiteVarias) {
            devolver(listOf(f))
            return
        }
        if (!seleccion.add(f.absolutePath)) seleccion.remove(f.absolutePath)
        refrescar()
    }

    private fun accionSeleccion() {
        val elegidas = archivos.filter { it.absolutePath in seleccion }
        if (elegidas.isEmpty()) return
        if (modoElegir) devolver(elegidas) else compartir(elegidas)
    }

    private fun clipDe(uris: List<Uri>): ClipData {
        val clip = ClipData.newRawUri("imagenes", uris[0])
        for (i in 1 until uris.size) clip.addItem(ClipData.Item(uris[i]))
        return clip
    }

    /** Devuelve las imágenes elegidas a la app que pidió adjuntar. */
    private fun devolver(lista: List<File>) {
        val uris = lista.map { ArchivoProvider.uri(it) }
        val r = Intent()
        r.data = uris[0]
        r.clipData = clipDe(uris)
        r.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setResult(RESULT_OK, r)
        finish()
    }

    private fun compartir(lista: List<File>) {
        val uris = lista.map { ArchivoProvider.uri(it) }
        val envio = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND)
                .putExtra(Intent.EXTRA_STREAM, uris[0])
                .setType(ArchivoProvider.mime(lista[0].name))
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                .setType("image/*")
        }
        envio.clipData = clipDe(uris)
        envio.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(envio, "Compartir"))
        seleccion.clear()
        refrescar()
    }

    private inner class Adaptador : BaseAdapter() {
        override fun getCount(): Int = archivos.size
        override fun getItem(position: Int): Any = archivos[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val iv = (convertView as? Cuadrado) ?: Cuadrado(this@GaleriaActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.LTGRAY)
            }
            val f = archivos[position]
            val clave = f.absolutePath
            iv.tag = clave
            val enCache = cache.get(clave)
            if (enCache != null) {
                iv.setImageBitmap(enCache)
            } else {
                iv.setImageDrawable(null)
                try {
                    ejecutor.execute {
                        if (iv.tag != clave) return@execute
                        val b = try {
                            ThumbnailUtils.createImageThumbnail(f, Size(lado, lado), null)
                        } catch (e: Exception) {
                            null
                        }
                        if (b != null) {
                            cache.put(clave, b)
                            runOnUiThread { if (iv.tag == clave) iv.setImageBitmap(b) }
                        }
                    }
                } catch (e: Exception) {
                    // la pantalla se está cerrando
                }
            }
            if (clave in seleccion) {
                iv.setColorFilter(Color.argb(120, 0, 110, 255))
            } else {
                iv.clearColorFilter()
            }
            return iv
        }
    }

    /** Miniatura cuadrada. */
    class Cuadrado(ctx: Context) : ImageView(ctx) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, widthMeasureSpec)
        }
    }

    companion object {
        private var yaMandoAAjustes = false
    }
}
