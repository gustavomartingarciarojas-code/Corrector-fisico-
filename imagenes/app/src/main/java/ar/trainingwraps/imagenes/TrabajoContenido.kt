package ar.trainingwraps.imagenes

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.MediaStore

/**
 * Respaldo de la vigilancia: Android despierta este trabajo cada vez que
 * cambia la galería, aunque la app esté cerrada.
 */
class TrabajoContenido : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try {
                Ordenador.ordenarRecientes(applicationContext)
            } catch (e: Exception) {
                // nada: el próximo cambio lo vuelve a intentar
            }
            jobFinished(params, false)
            programar(applicationContext)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val ID_TRABAJO = 4242

        fun programar(ctx: Context) {
            val js = ctx.getSystemService(JobScheduler::class.java) ?: return
            val info = JobInfo.Builder(ID_TRABAJO, ComponentName(ctx, TrabajoContenido::class.java))
                .addTriggerContentUri(
                    JobInfo.TriggerContentUri(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                    )
                )
                .setTriggerContentUpdateDelay(2000)
                .setTriggerContentMaxDelay(15000)
                .build()
            try {
                js.schedule(info)
            } catch (e: Exception) {
                // sin respaldo, sigue la vigilancia principal
            }
        }
    }
}
