package com.readqurantoday.quran

import android.content.Context
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

// Screens open in the app's language at once; on a first run the system takes it up only after the first screen shows
abstract class LanguageActivity : AppCompatActivity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(Settings.inLanguage(base))
    }

    // Every screen draws under the system bars on every Android version, as Android 15 does by default
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }
}
