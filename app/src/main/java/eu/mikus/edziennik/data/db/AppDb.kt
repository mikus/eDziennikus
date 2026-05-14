package eu.mikus.edziennik.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import eu.mikus.edziennik.config.db.ConfigDao
import eu.mikus.edziennik.config.db.ConfigEntry
import eu.mikus.edziennik.data.db.converter.*
import eu.mikus.edziennik.data.db.dao.*
import eu.mikus.edziennik.data.db.entity.*

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
], version = 1)
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
        @Volatile private var instance: AppDb? = null
        private val LOCK = Any()

        operator fun invoke(context: Context) = instance ?: synchronized(LOCK) {
            instance ?: buildDatabase(context).also { instance = it }
        }

        private fun buildDatabase(context: Context) = Room.databaseBuilder(
                context.applicationContext,
                AppDb::class.java,
                "edziennik.db"
        ).fallbackToDestructiveMigration().allowMainThreadQueries().build()
    }
}
