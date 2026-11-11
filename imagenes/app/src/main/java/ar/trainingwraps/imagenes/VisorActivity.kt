package ar.trainingwraps.imagenes

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import kotlin.math.abs

/**
 * Ver una imagen en grande dentro de la app, con los botones siempre a la vista:
 * Volver · Compartir · Borrar · Ampliar (abre en Galería para hacer zoom).
 * Deslizar a izquierda/derecha pasa a la siguiente/anterior.
 */
class VisorActivity : Activity() {

    private lateinit var imagen: ImageView
    private lateinit var nombre: TextView
    private lateinit var detector: GestureDetector

    private var lista: List<File> = emptyList()
    private var indice = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lista = GaleriaActivity.listaVisor
        indice = intent.getIntExtra(EXTRA_INDICE, 0)
        if (lista.isEmpty()) {
            finish()
            return
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

        imagen = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val marco = FrameLayout(this)
        marco.addView(imagen, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        raiz.addView(marco, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val abajo = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
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
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        abajo.addView(Button(this).apply {
            text = "Ampliar"
            setOnClickListener { ampliar() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        raiz.addView(abajo)

        raiz.setOnApplyWindowInsetsListener { v, insets ->
            val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        setContentView(raiz)

        detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val inicio = e1 ?: return false
                val dx = e2.x - inicio.x
                if (abs(dx) > 120 && abs(dx) > abs(e2.y - inicio.y)) {
                    if (dx < 0) mover(1) else mover(-1)
                    return true
                }
                return false
            }
        })
        imagen.setOnTouchListener { _, evento -> detector.onTouchEvent(evento) }

        mostrar()
    }

    private fun actual(): File? = lista.getOrNull(indice)

    private fun mover(paso: Int) {
        val nuevo = indice + paso
        if (nuevo < 0 || nuevo >= lista.size) return
        indice = nuevo
        mostrar()
    }

    private fun mostrar() {
        val f = actual() ?: run {
            finish()
            return
        }
        nombre.text = "${indice + 1} de ${lista.size} · ${f.name}"
        imagen.setImageDrawable(null)
        val ancho = resources.displayMetrics.widthPixels
        val alto = resources.displayMetrics.heightPixels
        val pedido = indice
        Thread {
            val bmp = try {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(f)) { decoder, info, _ ->
                    val escala = maxOf(
                        info.size.width.toFloat() / ancho,
                        info.size.height.toFloat() / alto,
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
        val f = actual() ?: return
        val uri = ArchivoProvider.uri(f)
        val envio = Intent(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .setType(ArchivoProvider.mime(f.name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        envio.clipData = android.content.ClipData.newRawUri("imagen", uri)
        startActivity(Intent.createChooser(envio, "Compartir"))
    }

    private fun ampliar() {
        val f = actual() ?: return
        val ver = Intent(Intent.ACTION_VIEW)
            .setDataAndType(ArchivoProvider.uri(f), ArchivoProvider.mime(f.name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(ver)
        } catch (e: Exception) {
            Toast.makeText(this, "No hay una app para ampliar", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmarBorrar() {
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
