package fr.exemple.reveiltelegram

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class TelegramListener : NotificationListenerService() {

    override fun onListenerConnected() {
        Store.log(this, "✅ Surveillance des notifications connectée")
    }

    override fun onListenerDisconnected() {
        Store.log(this, "⚠️ Surveillance déconnectée, reconnexion…")
        NotificationListenerService.requestRebind(
            ComponentName(this, TelegramListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg == packageName) return
        val n = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        if (!Store.appActive(this)) return

        val concernees = Store.regles(this).filter { pkg in it.applis }
        if (concernees.isEmpty()) return

        val ex = n.extras
        val titre = listOfNotNull(
            ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        ).distinct().joinToString(" · ")
        val texte = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""

        if (AlarmService.enCours) {
            Store.log(this, "Alarme déjà en cours (${court(titre)})")
            return
        }

        val p = Store.prefs(this)
        val maintenant = System.currentTimeMillis()
        for (r in concernees) {
            val raison = r.raisonRefus(pkg, titre, texte)
            if (raison != null) {
                Store.log(this, "Ignoré par « ${r.nom} » : $raison — ${court(titre)} : ${court(texte)}")
                continue
            }
            val dernier = maxOf(p.getLong("dernier_${r.id}", 0L), p.getLong("dernier_arret", 0L))
            if (maintenant - dernier < r.pauseMin * 60_000L) {
                Store.log(this, "« ${r.nom} » en pause anti-répétition")
                continue
            }
            p.edit().putLong("dernier_${r.id}", maintenant).apply()
            Store.log(this, "🔔 « ${r.nom} » déclenchée — ${court(titre)} : ${court(texte)}")
            AlarmService.demarrer(this, r, titre, texte, pkg)
            return
        }
    }

    private fun court(s: String) = if (s.length > 60) s.take(60) + "…" else s
}
