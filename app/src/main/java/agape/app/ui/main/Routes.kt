package agape.app.ui.main

import kotlinx.serialization.Serializable

// Tabs
@Serializable data object HomeRoute
@Serializable data object BibleRoute
@Serializable data object PrayerRoute
@Serializable data object SearchRoute
@Serializable data object MeRoute
@Serializable data object CalendarRoute
@Serializable data object RosaryRoute
@Serializable data object ChotkiRoute

// Bible
/** Deep link: agape://bible/{book}/{chapter}?verse={verse} */
@Serializable data class ReaderRoute(val book: String, val chapter: Int, val verse: Int = 0)
@Serializable data class ArticleRoute(val id: String)
@Serializable data class CouncilRoute(val id: String)
@Serializable data object CouncilsRoute
@Serializable data object FathersRoute
@Serializable data object LeadersRoute
@Serializable data class FatherRoute(val id: String)

// Plans and quizzes
@Serializable data object PlansRoute
@Serializable data class PlanRoute(val planId: String)
@Serializable data class PlanDayRoute(val planId: String, val day: Int)
@Serializable data class QuizRoute(val key: String, val title: String, val planId: String = "", val day: Int = 0)

// Prayer
/** Deep link: agape://prayer/{hourId} */
@Serializable data class HourRoute(val hourId: String)
@Serializable data class PrayerTextRoute(val prayerId: String)

// Me
@Serializable data object SettingsRoute
@Serializable data object SavedRoute
@Serializable data object ActivityRoute
@Serializable data object AchievementsRoute
@Serializable data object HelpRoute
@Serializable data class LegalRoute(val doc: String)

const val DEEP_LINK_SCHEME = "agape"
