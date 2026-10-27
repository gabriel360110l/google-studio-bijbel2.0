package agape.app

import agape.app.core.model.AppLanguage
import agape.app.core.prefs.SettingsRepository
import agape.app.data.auth.AuthRepository
import agape.app.data.auth.LocalDevAuthRepository
import agape.app.data.auth.SupabaseAuthRepository
import agape.app.data.bible.BibleRepository
import agape.app.data.content.Backgrounds
import agape.app.data.content.ContentRepository
import agape.app.data.daily.DailyRepository
import agape.app.data.saints.SaintsRepository
import agape.app.data.schedule.SmartScheduleService
import agape.app.data.sync.SyncRepository
import agape.app.data.user.UserDatabase
import agape.app.data.user.UserRepository
import agape.app.notifications.Notifications
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/** Hand-wired dependencies; small enough that a DI framework would add more than it saves. */
class AppContainer(private val app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun appContext(): Context = app

    private val supabase = if (BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
            install(Auth)
            install(Postgrest)
        }
    } else null

    val settings = SettingsRepository(app)
    val content = ContentRepository(app)
    val bible = BibleRepository(app)
    val user = UserRepository(UserDatabase.create(app))
    val auth: AuthRepository = supabase?.let { SupabaseAuthRepository(it, appScope) } ?: LocalDevAuthRepository(app)
    val sync = SyncRepository(supabase, user, auth.state, appScope)
    val daily = DailyRepository(content, settings)
    val saints = SaintsRepository(content)
    val backgrounds = Backgrounds(app)
    val smartSchedule = SmartScheduleService(app, content, user, settings)

    /** The language the app is shown in. English unless the user picked Dutch. */
    fun language(): AppLanguage = AppLanguage.fromTag(AppCompatDelegate.getApplicationLocales()[0]?.language)

    fun setLanguage(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }

    /** Resources in the app language, for notifications built outside an activity. */
    fun localized(context: Context): Context {
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language().tag)) }
        return context.createConfigurationContext(config)
    }
}

fun CoroutineScope.launchSafely(block: suspend CoroutineScope.() -> Unit): Job =
    launch(CoroutineExceptionHandler { _, e -> Log.e("Agape", "background task failed", e) }, block = block)

class AgapeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applyDefaultLanguageOnce()
        Notifications.createChannels(this)
        container.sync.start()
        container.appScope.launchSafely { container.saints.warmUp() }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                container.appScope.launchSafely { container.settings.markOpened() }
            }
            override fun onStop(owner: LifecycleOwner) {
                container.appScope.launchSafely { container.sync.push() }
            }
        })
    }

    /** The spec asks for English on first launch regardless of the phone's language; after that the user's choice wins. */
    private fun applyDefaultLanguageOnce() {
        val prefs = getSharedPreferences("agape_boot", MODE_PRIVATE)
        if (!prefs.getBoolean("language_initialised", false)) {
            container.setLanguage(AppLanguage.ENGLISH)
            prefs.edit().putBoolean("language_initialised", true).apply()
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as AgapeApp).container
