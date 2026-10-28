package com.chris.whisperloom.simple

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import com.chris.whisperloom.R
import com.chris.whisperloom.ui.components.SystemIntents

/**
 * Platzhalter-Start (Etappe 1, Goal Out-of-Scope): nur eine Activity kann den Mikrofon-Dialog zeigen; dazu
 * Tastatur einschalten/waehlen (Systemabspruenge aus der Haupt-App) und ein Testfeld fuer den Emulator-Test.
 * Die Einrichtung mit Anmelde-Code, automatischer Tastaturwahl und Update-Hinweis kommt in Etappe 3 (U5).
 */
class SimpleStartActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.simple_start)
        findViewById<Button>(R.id.simple_start_mic).setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
        }
        findViewById<Button>(R.id.simple_start_enable).setOnClickListener {
            SystemIntents.open(this, SystemIntents.inputMethods())
        }
        findViewById<Button>(R.id.simple_start_pick).setOnClickListener { SystemIntents.showImePicker(this) }
    }

    override fun onResume() {
        super.onResume()
        updateMicButton()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateMicButton()
    }

    private fun updateMicButton() {
        val granted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        findViewById<Button>(R.id.simple_start_mic).apply {
            setText(if (granted) R.string.simple_start_mic_ok else R.string.simple_start_mic)
            isEnabled = !granted
        }
    }

    private companion object {
        const val REQUEST_MIC = 1
    }
}
