package fr.exemple.reveiltelegram

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var col: LinearLayout
    private var pad = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pad = (16 * resources.displayMetrics.density).toInt()
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        setContentView(ScrollView(this).apply { addView(col) })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        construire()
    }

    private fun titre(t: String) {
        col.addView(TextView(this).apply {
            text = t
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, pad, 0, pad / 2)
        })
    }

    private fun texte(t: String): TextView = TextView(this).apply {
        text = t
        textSize = 14f
        col.addView(this)
    }

    private fun bouton(t: String, action: () -> Unit): Button = Button(this).apply {
        text = t
        isAllCaps = false
        setOnClickListener { action() }
        col.addView(this)
    }

    private fun verif(ok: Boolean, nom: String, corriger: () -> Unit) {
        if (ok) texte("✅ $nom") else bouton("❌ $nom — toucher pour corriger", corriger)
    }

    private fun ouvrir(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")))
        }
    }

    private fun construire() {
        col.removeAllViews()

        if (AlarmService.enCours) {
            bouton("⏹ ARRÊTER L'ALARME") {
                AlarmService.arreter(this)
                col.postDelayed({ construire() }, 500)
            }.textSize = 22f
        }

        col.addView(Switch(this).apply {
            text = "Automatisations activées"
            textSize = 18f
            isChecked = Store.appActive(this@MainActivity)
            setOnCheckedChangeListener { _, b -> Store.setAppActive(this@MainActivity, b) }
        })

        titre("Vérifications")
        val nm = getSystemService(NotificationManager::class.java)
        val pm = getSystemService(PowerManager::class.java)
        val pkgUri = Uri.parse("package:$packageName")

        verif(Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                ?.contains(packageName) == true, "Accès aux notifications") {
            ouvrir(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        verif(nm.areNotificationsEnabled(), "Notifications de l'application") {
            ouvrir(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
        verif(pm.isIgnoringBatteryOptimizations(packageName), "Batterie non restreinte") {
            ouvrir(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri))
        }
        verif(Settings.canDrawOverlays(this), "Affichage par-dessus les autres applis") {
            ouvrir(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri))
        }
        if (Build.VERSION.SDK_INT >= 34) {
            verif(nm.canUseFullScreenIntent(), "Notifications plein écran") {
                ouvrir(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri))
            }
        }
        bouton("Infos de l'appli (paramètres restreints, batterie)") {
            ouvrir(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri))
        }

        titre("Mes automatisations")
        for (r in Store.regles(this)) {
            col.addView(Switch(this).apply {
                text = r.nom
                textSize = 17f
                setTypeface(null, Typeface.BOLD)
                isChecked = r.active
                setOnCheckedChangeListener { _, b ->
                    val liste = Store.regles(this@MainActivity)
                    liste.find { it.id == r.id }?.active = b
                    Store.sauver(this@MainActivity, liste)
                }
            })
            col.addView(TextView(this).apply {
                text = r.resume(this@MainActivity) + "\n✏️ Toucher pour modifier"
                setPadding(0, pad / 4, 0, pad)
                setOnClickListener { modifier(r.id) }
            })
        }
        bouton("➕ Nouvelle automatisation") { modifier(-1L) }

        titre("Tests")
        bouton("🔔 Tester l'alarme maintenant") {
            AlarmService.demarrer(this, Regle(nom = "Test"), "Test", "Ceci est un test", "")
            col.postDelayed({ construire() }, 500)
        }
        bouton("⏳ Tester dans 15 s (verrouillez l'écran)") {
            Toast.makeText(this, "Verrouillez l'écran maintenant…", Toast.LENGTH_LONG).show()
            Handler(Looper.getMainLooper()).postDelayed({
                AlarmService.demarrer(applicationContext,
                    Regle(nom = "Test écran verrouillé", dureeMin = 1),
                    "Test", "Si vous entendez ceci, tout fonctionne", "")
            }, 15_000)
        }

        titre("Journal")
        texte(Store.journal(this).ifEmpty { "Aucun événement pour l'instant." })
            .setTextIsSelectable(true)
        bouton("🔄 Actualiser") { construire() }
        bouton("🗑 Effacer le journal") {
            Store.effacerJournal(this)
            construire()
        }
    }

    private fun modifier(id: Long) {
        startActivity(Intent(this, RuleEditorActivity::class.java).putExtra("id", id))
    }
}
