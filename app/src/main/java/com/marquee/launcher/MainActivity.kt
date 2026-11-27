package com.marquee.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import com.marquee.launcher.data.Catalogs
import com.marquee.launcher.data.TvChannels
import com.marquee.launcher.ui.MarqueeApp

class MainActivity : ComponentActivity() {
    private val resumeTick = mutableIntStateOf(0)
    private val homeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Catalogs.init(applicationContext)
        // Lets Home read Nuvio's Continue Watching row.
        if (checkSelfPermission(TV_LISTINGS) != PackageManager.PERMISSION_GRANTED) {
            runCatching { requestPermissions(arrayOf(TV_LISTINGS), 1) }
        }
        setContent { MarqueeApp(resumeTick.intValue, homeTick.intValue) }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }

    /** Pressing Home while Marquee is already up resets it to the app row. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeTick.intValue++
        // adb shell am start -n com.marquee.launcher/.MainActivity --ez marquee.dump true
        if (intent.getBooleanExtra("marquee.dump", false)) {
            val app = applicationContext
            Thread { TvChannels.dump(app) }.start()
        }
    }

    private companion object {
        const val TV_LISTINGS = "android.permission.READ_TV_LISTINGS"
    }
}
