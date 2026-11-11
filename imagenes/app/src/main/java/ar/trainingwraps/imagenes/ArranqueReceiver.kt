package ar.trainingwraps.imagenes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Al prender la tablet (o al actualizar la app), vuelve a arrancar la vigilancia sola. */
class ArranqueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        VigiaService.arrancar(context)
    }
}
