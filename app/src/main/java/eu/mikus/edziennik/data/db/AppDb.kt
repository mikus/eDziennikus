package eu.mikus.edziennik.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import eu.mikus.edziennik.config.db.ConfigDao
import eu.mikus.edziennik.config.db.ConfigEntry
import eu.mikus.edziennik.data.db.converter.*
import eu.mikus.edziennik.data.db.dao.*
import eu.mikus.edziennik.data.db.entity.*

/**
 * The schema version [AppDb] declares. The annotation and the guard that decides which file to
 * open both read it, so they cannot drift apart.
 */
const val APP_DB_VERSION = 3

@Database(entities = [
    Grade::class,
    Teacher::class,
    TeacherAbsence::class,
    TeacherAbsenceType::class,
    Subject::class,
    Notice::class,
    Team::class,
    Attendance::class,
    Event::class,
    EventType::class,
    LoginStore::class,
    Profile::class,
    LuckyNumber::class,
    Announcement::class,
    GradeCategory::class,
    FeedbackMessage::class,
    Message::class,
    MessageRecipient::class,
    DebugLog::class,
    EndpointTimer::class,
    LessonRange::class,
    Notification::class,
    Classroom::class,
    NoticeType::class,
    AttendanceType::class,
    Lesson::class,
    ConfigEntry::class,
    LibrusLesson::class,
    TimetableManual::class,
    Note::class,
    Metadata::class
], version = APP_DB_VERSION)
@TypeConverters(
        ConverterTime::class,
        ConverterDate::class,
        ConverterJsonObject::class,
        ConverterListLong::class,
        ConverterListString::class,
        ConverterDateInt::class,
        ConverterEnums::class
)
abstract class AppDb : RoomDatabase() {
    abstract fun gradeDao(): GradeDao
    abstract fun teacherDao(): TeacherDao
    abstract fun teacherAbsenceDao(): TeacherAbsenceDao
    abstract fun teacherAbsenceTypeDao(): TeacherAbsenceTypeDao
    abstract fun subjectDao(): SubjectDao
    abstract fun noticeDao(): NoticeDao
    abstract fun teamDao(): TeamDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun eventDao(): EventDao
    abstract fun eventTypeDao(): EventTypeDao
    abstract fun loginStoreDao(): LoginStoreDao
    abstract fun profileDao(): ProfileDao
    abstract fun luckyNumberDao(): LuckyNumberDao
    abstract fun announcementDao(): AnnouncementDao
    abstract fun gradeCategoryDao(): GradeCategoryDao
    abstract fun feedbackMessageDao(): FeedbackMessageDao
    abstract fun messageDao(): MessageDao
    abstract fun messageRecipientDao(): MessageRecipientDao
    abstract fun debugLogDao(): DebugLogDao
    abstract fun endpointTimerDao(): EndpointTimerDao
    abstract fun lessonRangeDao(): LessonRangeDao
    abstract fun notificationDao(): NotificationDao
    abstract fun classroomDao(): ClassroomDao
    abstract fun noticeTypeDao(): NoticeTypeDao
    abstract fun attendanceTypeDao(): AttendanceTypeDao
    abstract fun timetableDao(): TimetableDao
    abstract fun configDao(): ConfigDao
    abstract fun librusLessonDao(): LibrusLessonDao
    abstract fun timetableManualDao(): TimetableManualDao
    abstract fun noteDao(): NoteDao
    abstract fun metadataDao(): MetadataDao

    companion object {
        const val APP_DB_NAME = "edziennik.db"

        @Volatile private var instance: AppDb? = null
        private val LOCK = Any()

        /**
         * Which file [open] last chose to build on, or null until a database has been built.
         * [DatabaseChoice.Current] is the normal case; `Newer` and `Unreadable` mean the real file
         * was left alone and the app is running on a sidecar.
         *
         * The setter is `internal` so tests can put the app in a parked state without placing a
         * real database file and letting `App.onCreate` boot against the cached singleton.
         */
        @Volatile var choice: DatabaseChoice? = null
            internal set

        operator fun invoke(context: Context) = instance ?: synchronized(LOCK) {
            instance ?: buildDatabase(context).also { instance = it }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE events ADD COLUMN eventDateUnknown INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE attendances ADD COLUMN attendanceDateUnknown INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        internal fun builder(context: Context, name: String) = Room.databaseBuilder(
                context.applicationContext,
                AppDb::class.java,
                name
        ).addMigrations(*MIGRATIONS)
                // A file from a newer build does not reach this under its own name: no migration
                // path is registered for a downgrade, so it would always land here and be dropped,
                // and `open` parks it on a sidecar before Room sees it. What still lands here is a
                // stored version lower than ours with no registered path. Corruption does not: that
                // goes through the open helper's onCorruption, which deletes the file.
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()

        /**
         * Decides which file to open for the database called [baseName] at [version], records the
         * decision on [choice], and builds on the file it names. A file written by a newer build
         * is left where it is and a sidecar is opened beside it; see [chooseDatabase].
         *
         * This is what [buildDatabase] does with the production name, kept as its own function so
         * the tests drive it and not a copy of it: [invoke] caches a singleton and fixes the name.
         */
        internal fun open(context: Context, baseName: String, version: Int): AppDb {
            val chosen = chooseDatabase(context.getDatabasePath(baseName), baseName, version)
            choice = chosen
            return builder(context, chosen.name).build()
        }

        private fun buildDatabase(context: Context) = open(context, APP_DB_NAME, APP_DB_VERSION)
    }
}
