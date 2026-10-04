package com.airwall.radar

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.airwall.radar.ui.AirwallTheme
import com.airwall.radar.ui.RadarScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on while TV display is active
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Use WindowCompat for robust full screen
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            AirwallTheme {
                RadarScreen()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            try {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
