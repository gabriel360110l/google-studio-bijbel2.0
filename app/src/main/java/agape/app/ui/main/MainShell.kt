package agape.app.ui.main

import agape.app.R
import agape.app.core.prefs.TourStop
import agape.app.ui.LocalContainer
import agape.app.ui.LocalSettings
import agape.app.ui.bible.BibleScreen
import agape.app.ui.bible.ReaderScreen
import agape.app.ui.church.ArticleScreen
import agape.app.ui.church.CouncilScreen
import agape.app.ui.church.CouncilsScreen
import agape.app.ui.church.FatherScreen
import agape.app.ui.church.FathersScreen
import agape.app.ui.church.LeadersScreen
import agape.app.ui.components.AmbientBackground
import agape.app.ui.components.AgapeIcons
import agape.app.ui.calendar.CalendarScreen
import agape.app.ui.search.SearchScreen
import agape.app.ui.search.SearchTarget
import androidx.compose.material.icons.outlined.Search
import agape.app.ui.home.HomeScreen
import agape.app.ui.legal.LegalScreen
import agape.app.ui.me.AchievementsScreen
import agape.app.ui.me.ActivityScreen
import agape.app.ui.me.HelpScreen
import agape.app.ui.me.MeScreen
import agape.app.ui.me.SavedScreen
import agape.app.ui.me.SettingsScreen
import agape.app.ui.plans.PlanDayScreen
import agape.app.ui.plans.PlanScreen
import agape.app.ui.plans.PlansScreen
import agape.app.ui.plans.QuizScreen
import agape.app.ui.prayer.HourScreen
import agape.app.ui.prayer.PrayerScreen
import agape.app.ui.prayer.PrayerTextScreen
import agape.app.ui.prayer.ROSARY_ID
import agape.app.ui.prayer.RosaryScreen
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tour.TourOverlay
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

private data class Tab(
    val route: Any,
    val routeClass: KClass<*>,
    val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val stop: TourStop?,
)

// Profile and settings (Me) open from the gear on Home, so the bar keeps the five places of a day.
private val tabs = listOf(
    Tab(HomeRoute, HomeRoute::class, R.string.tab_home, Icons.Outlined.Home, Icons.Filled.Home, TourStop.HOME),
    Tab(BibleRoute, BibleRoute::class, R.string.tab_bible, Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Filled.MenuBook, TourStop.BIBLE),
    Tab(PrayerRoute, PrayerRoute::class, R.string.tab_prayer, AgapeIcons.Candle, AgapeIcons.Candle, TourStop.PRAYER),
    Tab(CalendarRoute, CalendarRoute::class, R.string.calendar_title, Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth, null),
    Tab(SearchRoute, SearchRoute::class, R.string.tab_search, Icons.Outlined.Search, Icons.Outlined.Search, null),
)

@Composable
fun MainShell(newIntents: StateFlow<Intent?>, onIntentHandled: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val currentTab = tabs.firstOrNull { t -> destination?.hierarchy?.any { it.hasRoute(t.routeClass) } == true }
    val settings = LocalSettings.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()

    val pending by newIntents.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        pending?.let { nav.handleDeepLink(it); onIntentHandled() }
    }

    AmbientBackground {
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            AnimatedVisibility(currentTab != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                BottomBar(currentTab) { tab -> nav.navigateToTab(tab.route) }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            AgapeNavHost(nav)
            // Content scrolls beneath the status bar; a soft veil keeps the clock and icons readable. Home has its
            // own dark picture there, which the veil would hide.
            if (currentTab?.route != HomeRoute) Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(AgapeTheme.colors.background.copy(alpha = 0.94f)))
            // Guided tour: the first visit to each tab explains it. The Prayer tab's explanation lives in PrayerScreen.
            val stop = currentTab?.stop
            if (stop != null && stop != TourStop.PRAYER && stop !in settings.tourSeen) {
                TourOverlay(stop = stop, seen = settings.tourSeen, onDismiss = { scope.launch { container.settings.markTourSeen(stop) } })
            }
        }
    }
    }
}

private fun NavHostController.navigateToTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/**
 * A floating, rounded tab bar that hovers over the page, dark and slightly translucent. The current tab sits in a
 * soft pill of its own, with a filled icon.
 */
@Composable
private fun BottomBar(current: Tab?, onSelect: (Tab) -> Unit) {
    val c = AgapeTheme.colors
    val bar = if (c.isDark) c.surfaceRaised else c.ink
    val on = if (c.isDark) c.ink else c.background
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp, top = 4.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(26.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.38f), spotColor = Color.Black.copy(alpha = 0.48f))
                .clip(CircleShape)
                .background(bar.copy(alpha = 0.95f))
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            c.accent.copy(alpha = 0.28f),
                            c.accent.copy(alpha = 0.58f),
                            c.accent.copy(alpha = 0.28f),
                        ),
                    ),
                    shape = CircleShape,
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val selected = tab == current
                val label = stringResource(tab.label)
                val pill by animateColorAsState(if (selected) c.accent.copy(alpha = 0.18f) else Color.Transparent, label = "tab")
                Column(
                    Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(pill)
                        .then(
                            if (selected) Modifier.border(0.8.dp, c.accent.copy(alpha = 0.45f), CircleShape)
                            else Modifier
                        )
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab) })
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        if (selected) tab.selectedIcon else tab.icon, null,
                        tint = if (selected) c.accent else on.copy(alpha = 0.65f),
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        maxLines = 1,
                        color = if (selected) on else on.copy(alpha = 0.65f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AgapeNavHost(nav: NavHostController) {
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(nav, startDestination = HomeRoute) {
        composable<HomeRoute>(deepLinks = listOf(navDeepLink { uriPattern = "$DEEP_LINK_SCHEME://home" })) {
            HomeScreen(
                openReader = { b, c, v -> nav.navigate(ReaderRoute(b, c, v)) },
                openPlans = { nav.navigate(PlansRoute) },
                openPlan = { nav.navigate(PlanRoute(it)) },
                openTab = { route -> nav.navigateToTab(route) },
                openHelp = { nav.navigate(HelpRoute) },
                openProfile = { nav.navigate(MeRoute) },
                openRosary = { nav.navigate(RosaryRoute) },
                openCalendar = { nav.navigateToTab(CalendarRoute) },
                openChotki = { nav.navigate(ChotkiRoute) },
            )
        }
        composable<CalendarRoute> { CalendarScreen(openPassage = { p -> nav.navigate(ReaderRoute(p.book, p.chapter, p.startVerse ?: 0)) }) }
        composable<RosaryRoute> {
            RosaryScreen(onBack = back, openPassage = { p -> nav.navigate(ReaderRoute(p.book, p.chapter, p.startVerse ?: 0)) })
        }
        composable<ChotkiRoute> {
            agape.app.ui.prayer.ChotkiScreen(onBack = back)
        }
        composable<SearchRoute> {
            SearchScreen(open = { t ->
                when (t) {
                    is SearchTarget.Verse -> nav.navigate(ReaderRoute(t.book, t.chapter, t.verse))
                    is SearchTarget.Hour -> nav.navigate(if (t.id == ROSARY_ID) RosaryRoute else HourRoute(t.id))
                    is SearchTarget.Prayer -> nav.navigate(PrayerTextRoute(t.id))
                    is SearchTarget.Council -> nav.navigate(CouncilRoute(t.id))
                    is SearchTarget.Father -> nav.navigate(FatherRoute(t.id))
                    is SearchTarget.Article -> nav.navigate(ArticleRoute(t.id))
                    is SearchTarget.Plan -> nav.navigate(PlanRoute(t.id))
                    SearchTarget.Calendar -> nav.navigateToTab(CalendarRoute)
                }
            })
        }
        composable<BibleRoute> {
            BibleScreen(
                openChapter = { b, c -> nav.navigate(ReaderRoute(b, c)) },
                openArticle = { nav.navigate(ArticleRoute(it)) },
                openCouncils = { nav.navigate(CouncilsRoute) },
                openFathers = { nav.navigate(FathersRoute) },
                openLeaders = { nav.navigate(LeadersRoute) },
                openPlans = { nav.navigate(PlansRoute) },
            )
        }
        composable<PrayerRoute> {
            PrayerScreen(
                openHour = { nav.navigate(if (it == ROSARY_ID) RosaryRoute else HourRoute(it)) },
                openPrayer = { nav.navigate(PrayerTextRoute(it)) },
                openRosary = { nav.navigate(RosaryRoute) },
                openChotki = { nav.navigate(ChotkiRoute) },
            )
        }
        composable<MeRoute> {
            MeScreen(
                onBack = back,
                openSettings = { nav.navigate(SettingsRoute) },
                openSaved = { nav.navigate(SavedRoute) },
                openActivity = { nav.navigate(ActivityRoute) },
                openAchievements = { nav.navigate(AchievementsRoute) },
                openHelp = { nav.navigate(HelpRoute) },
                openLegal = { nav.navigate(LegalRoute(it)) },
            )
        }
        composable<ReaderRoute>(deepLinks = listOf(navDeepLink<ReaderRoute>(basePath = "$DEEP_LINK_SCHEME://bible"))) {
            val r = it.toRoute<ReaderRoute>()
            ReaderScreen(
                book = r.book, chapter = r.chapter, focusVerse = r.verse, onBack = back,
                openChapter = { b, c, v -> nav.navigate(ReaderRoute(b, c, v)) { popUpTo<ReaderRoute> { inclusive = true } } },
                openReference = { b, c, v -> nav.navigate(ReaderRoute(b, c, v)) },
                openQuiz = { key, title -> nav.navigate(QuizRoute(key, title)) },
            )
        }
        composable<ArticleRoute> { ArticleScreen(it.toRoute<ArticleRoute>().id, onBack = back) }
        composable<CouncilsRoute> { CouncilsScreen(onBack = back, openCouncil = { nav.navigate(CouncilRoute(it)) }) }
        composable<CouncilRoute> {
            CouncilScreen(
                it.toRoute<CouncilRoute>().id, onBack = back,
                openPassage = { p -> nav.navigate(ReaderRoute(p.book, p.chapter, p.startVerse ?: 0)) },
                openQuiz = { key, title -> nav.navigate(QuizRoute(key, title)) },
            )
        }
        composable<LeadersRoute> { LeadersScreen(onBack = back) }
        composable<FathersRoute> { FathersScreen(onBack = back, openFather = { nav.navigate(FatherRoute(it)) }) }
        composable<FatherRoute> {
            FatherScreen(it.toRoute<FatherRoute>().id, onBack = back, openVerse = { b, c, v -> nav.navigate(ReaderRoute(b, c, v)) })
        }
        composable<PlansRoute> { PlansScreen(onBack = back, openPlan = { nav.navigate(PlanRoute(it)) }) }
        composable<PlanRoute> {
            PlanScreen(it.toRoute<PlanRoute>().planId, onBack = back, openDay = { id, day -> nav.navigate(PlanDayRoute(id, day)) })
        }
        composable<PlanDayRoute> {
            val r = it.toRoute<PlanDayRoute>()
            PlanDayScreen(
                planId = r.planId, day = r.day, onBack = back,
                openPassage = { p -> nav.navigate(ReaderRoute(p.book, p.chapter, p.startVerse ?: 0)) },
                openQuiz = { key, title -> nav.navigate(QuizRoute(key, title, r.planId, r.day)) },
            )
        }
        composable<QuizRoute> {
            val r = it.toRoute<QuizRoute>()
            QuizScreen(quizKey = r.key, title = r.title, planId = r.planId, day = r.day, onDone = back)
        }
        composable<HourRoute>(deepLinks = listOf(navDeepLink<HourRoute>(basePath = "$DEEP_LINK_SCHEME://prayer"))) {
            HourScreen(it.toRoute<HourRoute>().hourId, onBack = back)
        }
        composable<PrayerTextRoute> { PrayerTextScreen(it.toRoute<PrayerTextRoute>().prayerId, onBack = back) }
        composable<SettingsRoute> { SettingsScreen(onBack = back, openLegal = { nav.navigate(LegalRoute(it)) }) }
        composable<SavedRoute> {
            SavedScreen(
                onBack = back,
                openVerse = { b, c, v -> nav.navigate(ReaderRoute(b, c, v)) },
                openPlan = { nav.navigate(PlanRoute(it)) },
                openHour = { nav.navigate(HourRoute(it)) },
                openPrayer = { nav.navigate(PrayerTextRoute(it)) },
            )
        }
        composable<ActivityRoute> { ActivityScreen(onBack = back) }
        composable<AchievementsRoute> { AchievementsScreen(onBack = back) }
        composable<HelpRoute> { HelpScreen(onBack = back, openLegal = { nav.navigate(LegalRoute(it)) }) }
        composable<LegalRoute> { LegalScreen(it.toRoute<LegalRoute>().doc, onBack = back) }
    }
}
