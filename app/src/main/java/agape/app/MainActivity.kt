package agape.app

import agape.app.ui.AgapeRoot
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : AppCompatActivity() {
    /** Deep links that arrive while the activity is already running. */
    private val newIntents = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AgapeRoot(container = container, newIntents = newIntents, onIntentHandled = { newIntents.value = null })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        newIntents.value = intent
    }
}
