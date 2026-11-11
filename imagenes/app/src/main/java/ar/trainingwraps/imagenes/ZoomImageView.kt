package ar.trainingwraps.imagenes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import kotlin.math.abs

/**
 * Imagen con zoom propio (sin otras apps):
 *  - Pellizcar o rueda del mouse: acercar / alejar.
 *  - Doble toque: zoom rápido y volver.
 *  - Arrastrar: mover la imagen ampliada.
 *  - Deslizar (sin zoom): imagen siguiente / anterior.
 */
class ZoomImageView(ctx: Context) : ImageView(ctx) {

    private val matriz = Matrix()
    private var escala = 1f
    var alDeslizar: ((Int) -> Unit)? = null

    private val escalador = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoom(detector.scaleFactor, detector.focusX, detector.focusY)
            return true
        }
    })

    private val gestos = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (escala > 1.01f) {
                matriz.postTranslate(-distanceX, -distanceY)
                limitar()
                imageMatrix = matriz
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (escala > 1.01f) ajustar() else zoom(2.5f, e.x, e.y)
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val inicio = e1 ?: return false
            if (escala > 1.01f) return false
            val dx = e2.x - inicio.x
            if (abs(dx) > 120 && abs(dx) > abs(e2.y - inicio.y)) {
                alDeslizar?.invoke(if (dx < 0) 1 else -1)
                return true
            }
            return false
        }
    })

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        post { ajustar() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ajustar()
    }

    /** Imagen entera, centrada, sin zoom. */
    fun ajustar() {
        val d = drawable ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        if (vw <= 0f || vh <= 0f || dw <= 0f || dh <= 0f) return
        val base = minOf(vw / dw, vh / dh)
        escala = 1f
        matriz.reset()
        matriz.postScale(base, base)
        matriz.postTranslate((vw - dw * base) / 2f, (vh - dh * base) / 2f)
        imageMatrix = matriz
    }

    private fun zoom(factor: Float, x: Float, y: Float) {
        val nueva = (escala * factor).coerceIn(1f, 6f)
        if (nueva <= 1.001f) {
            ajustar()
            return
        }
        val f = nueva / escala
        escala = nueva
        matriz.postScale(f, f, x, y)
        limitar()
        imageMatrix = matriz
    }

    /** Que la imagen no se escape de la pantalla. */
    private fun limitar() {
        val d = drawable ?: return
        val r = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        matriz.mapRect(r)
        val vw = width.toFloat()
        val vh = height.toFloat()
        var tx = 0f
        var ty = 0f
        if (r.width() <= vw) {
            tx = (vw - r.width()) / 2f - r.left
        } else {
            if (r.left > 0f) tx = -r.left
            if (r.right < vw) tx = vw - r.right
        }
        if (r.height() <= vh) {
            ty = (vh - r.height()) / 2f - r.top
        } else {
            if (r.top > 0f) ty = -r.top
            if (r.bottom < vh) ty = vh - r.bottom
        }
        matriz.postTranslate(tx, ty)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        escalador.onTouchEvent(event)
        if (!escalador.isInProgress) gestos.onTouchEvent(event)
        return true
    }

    /** Rueda del mouse = zoom donde está el puntero. */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL) {
            val v = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (v != 0f) {
                zoom(if (v > 0) 1.25f else 0.8f, event.x, event.y)
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }
}
