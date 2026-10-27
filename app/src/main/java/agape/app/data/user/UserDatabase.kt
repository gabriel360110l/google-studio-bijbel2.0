package agape.app.data.user

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "bookmarks", primaryKeys = ["book", "chapter", "verse"])
data class Bookmark(val book: String, val chapter: Int, val verse: Int, val createdAt: Long)

@Serializable
@Entity(tableName = "highlights", primaryKeys = ["book", "chapter", "verse"])
data class Highlight(val book: String, val chapter: Int, val verse: Int, val color: Int, val createdAt: Long)

@Serializable
@Entity(tableName = "notes", primaryKeys = ["book", "chapter", "verse"])
data class Note(val book: String, val chapter: Int, val verse: Int, val text: String, val updatedAt: Long)

@Serializable
@Entity(tableName = "chapters_read", primaryKeys = ["book", "chapter"])
data class ChapterRead(val book: String, val chapter: Int, val readAt: Long)

enum class ActivityType { READ_CHAPTER, PRAYED_HOUR, PRAYED, QUIZ_PASSED, PLAN_DAY, HIGHLIGHT, BOOKMARK, NOTE }

@Serializable
@Entity(tableName = "activity", primaryKeys = ["type", "ref", "epochDay"])
data class Activity(
    val type: ActivityType,
    /** What the activity was about, e.g. "JHN.3", "hour:vespers", "plan:mark:4". */
    val ref: String,
    val epochDay: Long,
    val title: String,
    val at: Long,
)

@Serializable
@Entity(tableName = "plan_progress", primaryKeys = ["planId"])
data class PlanProgress(
    val planId: String,
    val startedAt: Long,
    /** Completed day numbers (1-based). */
    val completedDays: Set<Int>,
    val active: Boolean,
)

@Serializable
@Entity(tableName = "quiz_results", primaryKeys = ["key"])
data class QuizResult(val key: String, val score: Int, val total: Int, val passed: Boolean, val at: Long)

@Serializable
@Entity(tableName = "favorite_prayers", primaryKeys = ["id"])
data class FavoritePrayer(
    /** "hour:<id>" or "prayer:<id>". */
    val id: String,
    val addedAt: Long,
)

@Serializable
@Entity(tableName = "smart_schedule", primaryKeys = ["hourId"])
data class SmartScheduleEntry(
    val hourId: String,
    /** Suggested local prayer time in `HH:mm` format derived from local sunrise/sunset & Room prayer activity. */
    val suggestedTime: String,
    /** Solar anchor key: DAWN, SUNRISE, MORNING, MIDDAY, AFTERNOON, SUNSET, EVENING, NIGHT. */
    val solarAnchor: String,
    val sunriseTime: String,
    val sunsetTime: String,
    val solarNoonTime: String,
    val locationLabel: String,
    val latitude: Double,
    val longitude: Double,
    val weatherSummary: String,
    val enabled: Boolean,
    val updatedAt: Long,
)

class Converters {
    @androidx.room.TypeConverter
    fun fromDays(days: Set<Int>): String = days.sorted().joinToString(",")

    @androidx.room.TypeConverter
    fun toDays(value: String): Set<Int> = value.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
}

@Dao
interface UserDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC") fun bookmarks(): Flow<List<Bookmark>>
    @Upsert suspend fun upsertBookmark(b: Bookmark)
    @Query("DELETE FROM bookmarks WHERE book = :book AND chapter = :chapter AND verse = :verse") suspend fun deleteBookmark(book: String, chapter: Int, verse: Int)

    @Query("SELECT * FROM highlights ORDER BY createdAt DESC") fun highlights(): Flow<List<Highlight>>
    @Query("SELECT * FROM highlights WHERE book = :book AND chapter = :chapter") fun highlightsIn(book: String, chapter: Int): Flow<List<Highlight>>
    @Upsert suspend fun upsertHighlight(h: Highlight)
    @Query("DELETE FROM highlights WHERE book = :book AND chapter = :chapter AND verse = :verse") suspend fun deleteHighlight(book: String, chapter: Int, verse: Int)

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC") fun notes(): Flow<List<Note>>
    @Upsert suspend fun upsertNote(n: Note)
    @Query("DELETE FROM notes WHERE book = :book AND chapter = :chapter AND verse = :verse") suspend fun deleteNote(book: String, chapter: Int, verse: Int)

    @Query("SELECT * FROM chapters_read") fun chaptersRead(): Flow<List<ChapterRead>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertChapterRead(c: ChapterRead)
    @Query("DELETE FROM chapters_read WHERE book = :book AND chapter = :chapter") suspend fun deleteChapterRead(book: String, chapter: Int)

    @Query("SELECT * FROM activity ORDER BY at DESC") fun activity(): Flow<List<Activity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertActivity(a: Activity)

    @Query("SELECT * FROM plan_progress") fun plans(): Flow<List<PlanProgress>>
    @Upsert suspend fun upsertPlan(p: PlanProgress)
    @Query("DELETE FROM plan_progress WHERE planId = :planId") suspend fun deletePlan(planId: String)

    @Query("SELECT * FROM quiz_results") fun quizResults(): Flow<List<QuizResult>>
    @Upsert suspend fun upsertQuiz(r: QuizResult)

    @Query("SELECT * FROM favorite_prayers ORDER BY addedAt") fun favorites(): Flow<List<FavoritePrayer>>
    @Upsert suspend fun upsertFavorite(f: FavoritePrayer)
    @Query("DELETE FROM favorite_prayers WHERE id = :id") suspend fun deleteFavorite(id: String)

    @Query("SELECT * FROM smart_schedule ORDER BY suggestedTime ASC") fun smartSchedule(): Flow<List<SmartScheduleEntry>>
    @Upsert suspend fun upsertSmartSchedule(entries: List<SmartScheduleEntry>)
    @Upsert suspend fun upsertSmartScheduleEntry(entry: SmartScheduleEntry)
    @Query("UPDATE smart_schedule SET enabled = :enabled WHERE hourId = :hourId") suspend fun setSmartScheduleEnabled(hourId: String, enabled: Boolean)
    @Query("DELETE FROM smart_schedule") suspend fun clearSmartSchedule()

    @Query("SELECT * FROM bookmarks") suspend fun allBookmarks(): List<Bookmark>
    @Query("SELECT * FROM highlights") suspend fun allHighlights(): List<Highlight>
    @Query("SELECT * FROM notes") suspend fun allNotes(): List<Note>
    @Query("SELECT * FROM chapters_read") suspend fun allChaptersRead(): List<ChapterRead>
    @Query("SELECT * FROM activity") suspend fun allActivity(): List<Activity>
    @Query("SELECT * FROM plan_progress") suspend fun allPlans(): List<PlanProgress>
    @Query("SELECT * FROM quiz_results") suspend fun allQuizResults(): List<QuizResult>
    @Query("SELECT * FROM favorite_prayers") suspend fun allFavorites(): List<FavoritePrayer>
    @Query("SELECT * FROM smart_schedule") suspend fun allSmartSchedule(): List<SmartScheduleEntry>
}

@Database(
    entities = [
        Bookmark::class,
        Highlight::class,
        Note::class,
        ChapterRead::class,
        Activity::class,
        PlanProgress::class,
        QuizResult::class,
        FavoritePrayer::class,
        SmartScheduleEntry::class,
    ],
    version = 2,
    exportSchema = true,
)
@androidx.room.TypeConverters(Converters::class)
abstract class UserDatabase : RoomDatabase() {
    abstract fun dao(): UserDao

    companion object {
        fun create(context: Context): UserDatabase =
            Room.databaseBuilder(context, UserDatabase::class.java, "agape_user.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
