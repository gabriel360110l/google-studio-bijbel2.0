package agape.app.data.user

/** Pure progress rules, kept free of Android so they are easy to test. */
object Streaks {
    /** Consecutive active days ending today, or ending yesterday if today has no activity yet. */
    fun current(activeDays: Set<Long>, today: Long): Int {
        val end = when {
            today in activeDays -> today
            (today - 1) in activeDays -> today - 1
            else -> return 0
        }
        var day = end
        while ((day - 1) in activeDays) day--
        return (end - day + 1).toInt()
    }

    fun longest(activeDays: Set<Long>): Int {
        var best = 0
        for (day in activeDays) {
            if ((day - 1) in activeDays) continue
            var len = 1
            while ((day + len) in activeDays) len++
            best = maxOf(best, len)
        }
        return best
    }
}

data class ProgressStats(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val chaptersRead: Int = 0,
    val hoursPrayed: Int = 0,
    val quizzesPassed: Int = 0,
    val plansCompleted: Int = 0,
    val councilPlanCompleted: Boolean = false,
    val saved: Int = 0,
)

enum class Achievement(val target: Int, val measure: (ProgressStats) -> Int) {
    FIRST_CHAPTER(1, { it.chaptersRead }),
    TEN_CHAPTERS(10, { it.chaptersRead }),
    HUNDRED_CHAPTERS(100, { it.chaptersRead }),
    STREAK_7(7, { it.longestStreak }),
    STREAK_30(30, { it.longestStreak }),
    FIRST_HOUR(1, { it.hoursPrayed }),
    FORTY_HOURS(40, { it.hoursPrayed }),
    FIRST_QUIZ(1, { it.quizzesPassed }),
    FIRST_PLAN(1, { it.plansCompleted }),
    COUNCILS(1, { if (it.councilPlanCompleted) 1 else 0 }),
    TEN_SAVED(10, { it.saved });

    fun progress(stats: ProgressStats): Int = measure(stats).coerceAtMost(target)
    fun earned(stats: ProgressStats): Boolean = measure(stats) >= target
}
