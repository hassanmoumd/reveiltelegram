package fr.exemple.reveiltelegram

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object AlarmPlayer {

    private const val CHANNEL = "alarme_telegram"
    private const val NOTIF_ID = 1
    private const val DUREE_MAX = 5 * 60 * 1000L   // arrêt auto après 5 min

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var appCtx: Context? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { appCtx?.let { stop(it) } }

    @Volatile var isRinging = false
        private set
    @Volatile var lastStop = 0L

    @Synchronized
    fun start(context: Context, texte: String) {
        if (isRinging) return
        isRinging = true
        val ctx = context.applicationContext
        appCtx = ctx

        val pm = ctx.getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "reveil:alarme").apply {
            acquire(DUREE_MAX + 10_000)
        }

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val am = ctx.getSystemService(AudioManager::class.java)
        try {
            am.setStreamVolume(AudioManager.STREAM_ALARM,
                am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        } catch (e: Exception) {
        }

        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(ctx, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            null
        }

        try {
            val v = ctx.getSystemService(VibratorManager::class.java).defaultVibrator
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 1000, 500), 0), attrs)
            vibrator = v
        } catch (e: Exception) {
        }

        afficherNotification(ctx, texte)
        handler.postDelayed(autoStop, DUREE_MAX)
    }

    @Synchronized
    fun stop(context: Context) {
        handler.removeCallbacks(autoStop)
        player?.let {
            try { it.stop() } catch (e: Exception) { }
            it.release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        context.applicationContext.getSystemService(NotificationManager::class.java)
            .cancel(NOTIF_ID)
        if (isRinging) lastStop = System.currentTimeMillis()
        isRinging = false
    }

    private fun afficherNotification(ctx: Context, texte: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val canal = NotificationChannel(CHANNEL, "Alarme Telegram",
            NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(canal)

        val ouvrir = PendingIntent.getActivity(ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val arreter = PendingIntent.getBroadcast(ctx, 1,
            Intent(ctx, StopReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("📈 Nouveau message Telegram !")
            .setContentText(texte)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(ouvrir)
            .setFullScreenIntent(ouvrir, true)
            .addAction(Notification.Action.Builder(null, "⏹ ARRÊTER", arreter).build())
            .build()
        nm.notify(NOTIF_ID, n)
    }
}
