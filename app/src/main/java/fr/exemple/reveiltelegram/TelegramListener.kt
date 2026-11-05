package fr.exemple.reveiltelegram

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

    private fun diag(msg: String) {
        val h = SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date())
        getSharedPreferences("reglages", MODE_PRIVATE).edit()
            .putString("diag", "$h : $msg").apply()
    }

    override fun onListenerConnected() {
        diag("service connecté, en attente de messages…")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg !in PAQUETS) {
            if (pkg.contains("gram", ignoreCase = true)) diag("app inconnue : $pkg")
            return
        }

        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val prefs = getSharedPreferences("reglages", MODE_PRIVATE)
        if (!prefs.getBoolean("actif", true)) {
            diag("ignoré : interrupteur principal désactivé"); return
        }

        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val nuit = h >= HEURE_DEBUT || h < HEURE_FIN
        if (!nuit && !prefs.getBoolean("toujours", false)) {
            diag("ignoré : on est en journée"); return
        }

        if (AlarmPlayer.isRinging) { diag("alarme déjà en cours"); return }
        if (System.currentTimeMillis() - AlarmPlayer.lastStop < 60_000) {
            diag("ignoré : pause d'1 min après un arrêt"); return
        }

        val titre = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val texte = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        val filtre = (prefs.getString("filtre", "") ?: "").trim()
        if (filtre.isNotEmpty() &&
            !titre.contains(filtre, ignoreCase = true) &&
            !texte.contains(filtre, ignoreCase = true)) {
            diag("ignoré par le filtre (titre reçu : « $titre »)"); return
        }

        diag("🔔 ALARME déclenchée par « $titre »")
        AlarmPlayer.start(this, "$titre : $texte")
    }
}
