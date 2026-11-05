package fr.exemple.reveiltelegram

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.Calendar

class TelegramListener : NotificationListenerService() {

    companion object {
        const val HEURE_DEBUT = 22   // début de la nuit
        const val HEURE_FIN = 8      // fin de la nuit

        private val PAQUETS = setOf(
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "org.telegram.messenger.beta",
            "org.thunderdog.challegram"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in PAQUETS) return

        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val prefs = getSharedPreferences("reglages", MODE_PRIVATE)
        if (!prefs.getBoolean("actif", true)) return

        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val nuit = h >= HEURE_DEBUT || h < HEURE_FIN
        if (!nuit && !prefs.getBoolean("toujours", false)) return

        if (AlarmPlayer.isRinging) return
        if (System.currentTimeMillis() - AlarmPlayer.lastStop < 60_000) return

        val titre = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val texte = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        val filtre = (prefs.getString("filtre", "") ?: "").trim()
        if (filtre.isNotEmpty() &&
            !titre.contains(filtre, ignoreCase = true) &&
            !texte.contains(filtre, ignoreCase = true)) return

        AlarmPlayer.start(this, "$titre : $texte")
    }
}
