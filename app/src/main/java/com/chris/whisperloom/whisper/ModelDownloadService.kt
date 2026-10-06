package com.chris.whisperloom.whisper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R

/**
 * Laedt genau ein Modell (whisper oder Textmodell, [findOfflineModel]) im Vordergrund-Dienst
 * (Typ dataSync), damit der Download weiterlaeuft, wenn der Nutzer den Screen verlaesst.
 * Fortschritt fuer die UI ueber [ModelDownloads.states], fuer den Nutzer ueber die Notification
 * (Aktion "Abbrechen"). Abbruch durch den Nutzer verwirft
 * die Teildatei (UX-Spec E4); Fehler lassen sie liegen, damit "Erneut" per Range-Resume fortsetzt.
 *
 * Start: [start] (startForegroundService), Abbruch: [cancel] oder die Notification-Aktion.
 * Android 15: dataSync darf max. 6 h pro 24 h laufen -> [onTimeout] stoppt sauber (Teildatei bleibt).
 * Laeuft bereits ein Download, wird ein weiterer START ignoriert (UI sperrt die anderen Knoepfe
 * ueber [ModelDownloads.isAnyRunning]).
 */
class ModelDownloadService : Service() {

    private val main = Handler(Looper.getMainLooper())

    @Volatile private var cancelled = false
    @Volatile private var timedOut = false
    @Volatile private var current: OfflineModel? = null
    @Volatile private var lastPercent = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getStringExtra(EXTRA_MODEL_ID)
                val model = findOfflineModel(id)
                when {
                    model == null -> {
                        // Vertrag von startForegroundService einhalten, dann sofort wieder weg.
                        Log.w(TAG, "Unbekanntes Modell: $id")
                        if (current == null) {
                            startAsForeground(null, 0)
                            stop()
                        }
                    }
                    current != null -> Log.w(TAG, "Download von ${current?.id} laeuft — $id ignoriert")
                    else -> begin(model)
                }
            }
            ACTION_CANCEL -> {
                cancelled = true
                if (current == null) stopSelf()
            }
            else -> if (current == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    /** Android 15+: Zeitlimit fuer dataSync erreicht — sauber beenden, Teildatei bleibt fuer "Erneut". */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Zeitlimit fuer dataSync-Dienste erreicht")
        timedOut = true
        cancelled = true
        if (current == null) stop()
    }

    override fun onDestroy() {
        cancelled = true
        super.onDestroy()
    }

    private fun begin(model: OfflineModel) {
        cancelled = false
        timedOut = false
        lastPercent = -1
        current = model
        val store = ModelStore(this)
        val existing = store.partFile(model).length() // 0 ohne Teildatei
        ModelDownloads.update(model.id, DownloadState.Running(existing, model.bytes, 0))
        startAsForeground(model, percentOf(existing, model.bytes))
        Thread({ run(model, store) }, "loom-model-download").start()
    }

    /** Laeuft auf dem Download-Thread. */
    private fun run(model: OfflineModel, store: ModelStore) {
        try {
            val done = ModelDownloader().download(model, store, isCancelled = { cancelled }) { bytes, total, rate ->
                ModelDownloads.update(model.id, DownloadState.Running(bytes, total, rate))
                val pct = percentOf(bytes, total)
                if (pct != lastPercent) {
                    lastPercent = pct
                    notificationManager().notify(NOTIF_ID, notification(model, pct))
                }
            }
            when {
                done -> {
                    selectTextModel(this, model)
                    ModelDownloads.update(model.id, DownloadState.Done)
                    showResult(getString(R.string.models_notif_done, model.label))
                }
                timedOut -> ModelDownloads.update(model.id, DownloadState.Failed(MSG_TIMEOUT, retryable = true))
                else -> {
                    store.partFile(model).delete() // Abbruch durch den Nutzer verwirft die Teildatei
                    ModelDownloads.update(model.id, DownloadState.Idle)
                }
            }
        } catch (e: DownloadException) {
            Log.w(TAG, "Download ${model.id} fehlgeschlagen: ${e.message}")
            val text = messageFor(this, e)
            ModelDownloads.update(model.id, DownloadState.Failed(text, e.retryable))
            showResult(getString(R.string.models_failed, text))
        } catch (e: Exception) {
            Log.e(TAG, "Download ${model.id}: unerwarteter Fehler", e)
            val text = e.message ?: e.javaClass.simpleName
            ModelDownloads.update(model.id, DownloadState.Failed(text, retryable = false))
            showResult(getString(R.string.models_failed, text))
        } finally {
            current = null
            main.post { stop() }
        }
    }

    private fun stop() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // --- Notifications -------------------------------------------------------

    private fun notificationManager(): NotificationManager = getSystemService(NotificationManager::class.java)

    private fun startAsForeground(model: OfflineModel?, percent: Int) {
        notificationManager().createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.models_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val notif = notification(model, percent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun notification(model: OfflineModel?, percent: Int): Notification {
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_whisperloom)
            .setContentTitle(getString(R.string.models_notif_title))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
        if (model != null) {
            val cancelPi = PendingIntent.getService(
                this, 0,
                Intent(this, ModelDownloadService::class.java).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder
                .setContentText(getString(R.string.models_notif_text, model.label, percent))
                .setProgress(100, percent, false)
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_close),
                        getString(R.string.common_cancel),
                        cancelPi,
                    ).build(),
                )
        }
        return builder.build()
    }

    /** Ergebnis als eigene Notification — die Vordergrund-Notification verschwindet mit dem Dienst. */
    private fun showResult(text: String) {
        val notif = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_whisperloom)
            .setContentTitle(getString(R.string.models_title))
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        notificationManager().notify(NOTIF_RESULT_ID, notif)
    }

    companion object {
        private const val TAG = "ModelDownloadService"
        const val ACTION_START = "com.chris.whisperloom.action.MODEL_DOWNLOAD_START"
        const val ACTION_CANCEL = "com.chris.whisperloom.action.MODEL_DOWNLOAD_CANCEL"
        const val EXTRA_MODEL_ID = "modelId"
        const val MSG_TIMEOUT = "Zeitlimit für Hintergrund-Downloads erreicht — bitte erneut starten"

        private const val CHANNEL_ID = "whisperloom_models"
        private const val NOTIF_ID = 2201
        private const val NOTIF_RESULT_ID = 2202

        /** Download eines Katalog-Modells (whisper oder Text) starten (aus der UI, App im Vordergrund). */
        fun start(context: Context, modelId: String) {
            context.startForegroundService(startIntent(context, modelId))
        }

        /** Laufenden Download abbrechen (Teildatei wird verworfen). */
        fun cancel(context: Context) {
            runCatching { context.startService(cancelIntent(context)) }
        }

        fun startIntent(context: Context, modelId: String): Intent =
            Intent(context, ModelDownloadService::class.java).setAction(ACTION_START).putExtra(EXTRA_MODEL_ID, modelId)

        fun cancelIntent(context: Context): Intent =
            Intent(context, ModelDownloadService::class.java).setAction(ACTION_CANCEL)

        /**
         * Ein fertig geladenes Textmodell wird immer gewaehlt — der Nutzer hat es gezielt geladen
         * (vorher blieb E2B aktiv, wenn E4B aus der Liste fertig wurde, und E4B war erst per Radio
         * waehlbar). Ein whisper-Modell aendert nichts.
         */
        internal fun selectTextModel(context: Context, model: OfflineModel) {
            if (TextModelCatalog.find(model.id) != null) Prefs(context).localLlmModel = model.id
        }

        /** Nutzertext zu einem Download-Fehler (err_* aus der UX-Spec, sonst die Meldung des Downloaders). */
        fun messageFor(context: Context, e: DownloadException): String = when (e.kind) {
            DownloadException.Kind.NETWORK -> context.getString(R.string.err_download_net)
            DownloadException.Kind.CHECKSUM -> context.getString(R.string.err_model_checksum)
            DownloadException.Kind.STORAGE -> context.getString(R.string.err_storage_full)
            DownloadException.Kind.OTHER -> e.message ?: context.getString(R.string.err_download_net)
        }
    }
}
