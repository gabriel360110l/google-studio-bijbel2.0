package agape.app.data.user

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Everything the account syncs, as one document. */
@Serializable
data class UserSnapshot(
    val bookmarks: List<Bookmark> = emptyList(),
    val highlights: List<Highlight> = emptyList(),
    val notes: List<Note> = emptyList(),
    val chaptersRead: List<ChapterRead> = emptyList(),
    val activity: List<Activity> = emptyList(),
    val plans: List<PlanProgress> = emptyList(),
    val quizResults: List<QuizResult> = emptyList(),
    val favorites: List<FavoritePrayer> = emptyList(),
)

class UserRepository(private val db: UserDatabase) {
    private val dao = db.dao()
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits after every local change, so sync can push. */
    val changes: SharedFlow<Unit> = _changes

    val bookmarks = dao.bookmarks()
    val highlights = dao.highlights()
    val notes = dao.notes()
    val activity = dao.activity()
    val plans = dao.plans()
    val quizResults = dao.quizResults()
    val favorites = dao.favorites()
    val chaptersRead = dao.chaptersRead()
    val smartSchedule: Flow<List<SmartScheduleEntry>> = dao.smartSchedule()

    fun highlightsIn(book: String, chapter: Int) = dao.highlightsIn(book, chapter)

    val stats: Flow<ProgressStats> = combine(
        dao.activity(), dao.chaptersRead(), dao.plans(), dao.quizResults(),
        combine(dao.bookmarks(), dao.highlights(), dao.notes()) { b, h, n -> b.size + h.size + n.size },
    ) { activity, read, plans, quizzes, saved ->
        val days = activity.map { it.epochDay }.toSet()
        val today = LocalDate.now().toEpochDay()
        ProgressStats(
            currentStreak = Streaks.current(days, today),
            longestStreak = Streaks.longest(days),
            chaptersRead = read.size,
            hoursPrayed = activity.count { it.type == ActivityType.PRAYED_HOUR },
            quizzesPassed = quizzes.count { it.passed },
            plansCompleted = plans.count { !it.active && it.completedDays.isNotEmpty() },
            councilPlanCompleted = plans.any { it.planId == "councils" && !it.active && it.completedDays.isNotEmpty() },
            saved = saved,
        )
    }

    private suspend fun log(type: ActivityType, ref: String, title: String) {
        val now = System.currentTimeMillis()
        dao.insertActivity(Activity(type, ref, LocalDate.now().toEpochDay(), title, now))
    }

    private fun changed() { _changes.tryEmit(Unit) }

    suspend fun markChapterRead(book: String, chapter: Int, title: String) = markChapterRead(book, chapter, true, title)

    suspend fun markChapterRead(book: String, chapter: Int, on: Boolean, title: String) {
        if (on) {
            dao.insertChapterRead(ChapterRead(book, chapter, System.currentTimeMillis()))
            log(ActivityType.READ_CHAPTER, "$book.$chapter", title)
        } else {
            dao.deleteChapterRead(book, chapter)
        }
        changed()
    }

    suspend fun markHourPrayed(hourId: String, title: String) {
        log(ActivityType.PRAYED_HOUR, "hour:$hourId", title); changed()
    }

    suspend fun markPrayed(prayerId: String, title: String) {
        log(ActivityType.PRAYED, "prayer:$prayerId", title); changed()
    }

    suspend fun setBookmark(book: String, chapter: Int, verse: Int, on: Boolean, title: String) {
        if (on) {
            dao.upsertBookmark(Bookmark(book, chapter, verse, System.currentTimeMillis()))
            log(ActivityType.BOOKMARK, "$book.$chapter.$verse", title)
        } else dao.deleteBookmark(book, chapter, verse)
        changed()
    }

    suspend fun setHighlight(book: String, chapter: Int, verse: Int, color: Int?, title: String) {
        if (color != null) {
            dao.upsertHighlight(Highlight(book, chapter, verse, color, System.currentTimeMillis()))
            log(ActivityType.HIGHLIGHT, "$book.$chapter.$verse", title)
        } else dao.deleteHighlight(book, chapter, verse)
        changed()
    }

    suspend fun saveNote(book: String, chapter: Int, verse: Int, text: String, title: String) {
        if (text.isBlank()) dao.deleteNote(book, chapter, verse)
        else {
            dao.upsertNote(Note(book, chapter, verse, text.trim(), System.currentTimeMillis()))
            log(ActivityType.NOTE, "$book.$chapter.$verse", title)
        }
        changed()
    }

    suspend fun startPlan(planId: String) {
        dao.upsertPlan(PlanProgress(planId, System.currentTimeMillis(), emptySet(), active = true)); changed()
    }

    suspend fun stopPlan(planId: String) { dao.deletePlan(planId); changed() }

    suspend fun completePlanDay(progress: PlanProgress, day: Int, totalDays: Int, title: String) {
        val done = progress.completedDays + day
        dao.upsertPlan(progress.copy(completedDays = done, active = done.size < totalDays))
        log(ActivityType.PLAN_DAY, "plan:${progress.planId}:$day", title)
        changed()
    }

    suspend fun saveQuizResult(key: String, score: Int, total: Int, passed: Boolean, title: String) {
        dao.upsertQuiz(QuizResult(key, score, total, passed, System.currentTimeMillis()))
        if (passed) log(ActivityType.QUIZ_PASSED, key, title)
        changed()
    }

    suspend fun setFavorite(id: String, on: Boolean) {
        if (on) dao.upsertFavorite(FavoritePrayer(id, System.currentTimeMillis())) else dao.deleteFavorite(id)
        changed()
    }

    suspend fun saveSmartSchedule(entries: List<SmartScheduleEntry>) {
        dao.clearSmartSchedule()
        dao.upsertSmartSchedule(entries)
        changed()
    }

    suspend fun setSmartScheduleEnabled(hourId: String, enabled: Boolean) {
        dao.setSmartScheduleEnabled(hourId, enabled)
        changed()
    }

    suspend fun updateSmartScheduleTime(entry: SmartScheduleEntry) {
        dao.upsertSmartScheduleEntry(entry)
        changed()
    }

    suspend fun allActivityList(): List<Activity> = dao.allActivity()
    suspend fun allFavoritesList(): List<FavoritePrayer> = dao.allFavorites()
    suspend fun allSmartScheduleList(): List<SmartScheduleEntry> = dao.allSmartSchedule()

    suspend fun snapshot(): UserSnapshot = UserSnapshot(
        dao.allBookmarks(), dao.allHighlights(), dao.allNotes(), dao.allChaptersRead(),
        dao.allActivity(), dao.allPlans(), dao.allQuizResults(), dao.allFavorites(),
    )

    /** Merges a snapshot from the server into local data; nothing local is lost. */
    suspend fun merge(remote: UserSnapshot) {
        remote.bookmarks.forEach { dao.upsertBookmark(it) }
        remote.highlights.forEach { dao.upsertHighlight(it) }
        val localNotes = dao.allNotes().associateBy { Triple(it.book, it.chapter, it.verse) }
        remote.notes.forEach { n ->
            val local = localNotes[Triple(n.book, n.chapter, n.verse)]
            if (local == null || local.updatedAt < n.updatedAt) dao.upsertNote(n)
        }
        remote.chaptersRead.forEach { dao.insertChapterRead(it) }
        remote.activity.forEach { dao.insertActivity(it) }
        val localPlans = dao.allPlans().associateBy { it.planId }
        remote.plans.forEach { p ->
            val local = localPlans[p.planId]
            dao.upsertPlan(if (local == null) p else local.copy(completedDays = local.completedDays + p.completedDays, active = local.active && p.active))
        }
        val localQuiz = dao.allQuizResults().associateBy { it.key }
        remote.quizResults.forEach { q -> if (localQuiz[q.key]?.passed != true) dao.upsertQuiz(q) }
        remote.favorites.forEach { dao.upsertFavorite(it) }
    }

    /** Removes all personal data from this device (sign-out). */
    suspend fun clear() = withContext(Dispatchers.IO) { db.clearAllTables() }
}
