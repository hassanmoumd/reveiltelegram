package fr.exemple.reveiltelegram

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statut: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("reglages", MODE_PRIVATE)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        fun bouton(t: String, action: () -> Unit): Button = Button(this).apply {
            text = t
            setOnClickListener { action() }
            col.addView(this)
        }

        bouton("⏹ ARRÊTER L'ALARME") { AlarmPlayer.stop(this); maj() }.textSize = 22f

        statut = TextView(this).apply {
            textSize = 16f
            setPadding(0, pad, 0, pad)
        }
        col.addView(statut)

        col.addView(CheckBox(this).apply {
            text = "Réveil activé (22h → 8h)"
            isChecked = prefs.getBoolean("actif", true)
            setOnCheckedChangeListener { _, b -> prefs.edit().putBoolean("actif", b).apply() }
        })

        col.addView(CheckBox(this).apply {
            text = "Sonner aussi en journée (pour tester)"
            isChecked = prefs.getBoolean("toujours", false)
            setOnCheckedChangeListener { _, b -> prefs.edit().putBoolean("toujours", b).apply() }
        })

        val filtre = EditText(this).apply {
            hint = "Nom du canal (vide = tous les messages)"
            setText(prefs.getString("filtre", ""))
        }
        col.addView(filtre)

        bouton("Enregistrer le filtre") {
            prefs.edit().putString("filtre", filtre.text.toString().trim()).apply()
            Toast.makeText(this, "Filtre enregistré", Toast.LENGTH_SHORT).show()
        }

        bouton("1. Autoriser l'accès aux notifications") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        bouton("2. Infos de l'app (batterie, paramètres restreints)") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")))
        }

        bouton("🔔 Tester l'alarme") {
            AlarmPlayer.start(this, "Ceci est un test")
            maj()
        }

        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() {
        super.onResume()
        maj()
    }

    private fun maj() {
        val acces = Settings.Secure.getString(contentResolver,
            "enabled_notification_listeners")?.contains(packageName) == true
        statut.text =
            (if (acces) "✅ Accès aux notifications : OK"
             else "❌ Accès aux notifications : NON autorisé") + "\n" +
            (if (AlarmPlayer.isRinging) "🔔 Alarme en cours" else "🔕 Alarme arrêtée")
    }
}
