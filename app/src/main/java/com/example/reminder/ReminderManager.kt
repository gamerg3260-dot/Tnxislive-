package com.example.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.local.ReminderDao
import com.example.data.local.ReminderEntity
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class ReminderActionResult {
    data class Scheduled(
        val reminder: ReminderEntity,
        val messageHindi: String,
        val voiceResponseHindi: String
    ) : ReminderActionResult()

    data class Cancelled(
        val reminder: ReminderEntity,
        val messageHindi: String,
        val voiceResponseHindi: String
    ) : ReminderActionResult()

    data class Listed(
        val activeReminders: List<ReminderEntity>,
        val messageHindi: String,
        val voiceResponseHindi: String
    ) : ReminderActionResult()

    data class Error(
        val messageHindi: String,
        val voiceResponseHindi: String
    ) : ReminderActionResult()
}

/**
 * Offline, zero-network Reminder and Alarm manager powered by Android AlarmManager & Room database.
 */
class ReminderManager(
    private val context: Context,
    private val reminderDao: ReminderDao
) {
    private val tag = "ReminderManager"
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val timeDisplayFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val dateDisplayFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

    val allReminders: Flow<List<ReminderEntity>> = reminderDao.getAllReminders()
    val activeReminders: Flow<List<ReminderEntity>> = reminderDao.getActiveReminders()

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Checks if the user command is a reminder/alarm instruction.
     */
    fun isReminderOrAlarmCommand(rawCommand: String): Boolean {
        val q = rawCommand.lowercase().trim()
        val reminderKeywords = listOf(
            "याद दिलाना", "याद दिलाओ", "याद दिला देना", "रिमाइंडर", "रिमाइंडर लगाओ",
            "अलार्म", "अलार्म लगाओ", "अलार्म सेट", "alarm", "reminder", "remind me",
            "याद रखना", "yaad dilana", "yaad dilao", "alarm laga do", "reminder lagao",
            "cancel reminder", "रिमाइंडर कैंसिल", "अलार्म बंद", "alarm cancel",
            "reminders बताओ", "reminders batao", "mere reminders", "अलार्म दिखाओ"
        )
        return reminderKeywords.any { q.contains(it) }
    }

    /**
     * Parse and execute natural language reminder command offline.
     */
    suspend fun handleVoiceCommand(rawCommand: String): ReminderActionResult {
        val query = rawCommand.trim()
        val lower = query.lowercase()

        // 1. Check if user wants to list all reminders ("mere saare reminders batao")
        if (lower.contains("saare reminder") || lower.contains("सारे रिमाइंडर") ||
            lower.contains("list reminder") || lower.contains("reminders batao") ||
            lower.contains("रिमाइंडर बताओ") || lower.contains("रिमाइंडर दिखाओ") ||
            lower.contains("alarm batao") || lower.contains("अलार्म बताओ") ||
            lower.contains("कितने रिमाइंडर") || lower.contains("kitne reminder")
        ) {
            return listActiveReminders()
        }

        // 2. Check if user wants to cancel a reminder ("mera [kaam] wala reminder cancel karo")
        if (lower.contains("cancel") || lower.contains("कैंसिल") || lower.contains("हटाओ") ||
            lower.contains("रद्द") || lower.contains("डिलीट") || lower.contains("delete") ||
            lower.contains("band karo") || lower.contains("बंद करो")
        ) {
            return cancelMatchingReminder(query)
        }

        // 3. User wants to schedule a reminder/alarm
        return parseAndScheduleReminder(query)
    }

    /**
     * Cancels an active reminder matching the task title or latest if generic.
     */
    private suspend fun cancelMatchingReminder(query: String): ReminderActionResult {
        val cleanKeyword = extractCancelKeyword(query)
        val activeList = reminderDao.getActiveRemindersList(System.currentTimeMillis())

        if (activeList.isEmpty()) {
            return ReminderActionResult.Error(
                messageHindi = "वर्तमान में कोई सक्रिय रिमाइंडर या अलार्म नहीं है।",
                voiceResponseHindi = "आपके पास अभी कोई सक्रिय रिमाइंडर या अलार्म नहीं है।"
            )
        }

        val target = if (cleanKeyword.isNotBlank()) {
            activeList.firstOrNull { it.title.contains(cleanKeyword, ignoreCase = true) }
                ?: activeList.firstOrNull()
        } else {
            activeList.firstOrNull()
        }

        return if (target != null) {
            cancelSystemAlarm(target.id)
            reminderDao.cancelReminder(target.id)
            ReminderActionResult.Cancelled(
                reminder = target,
                messageHindi = "'${target.title}' का रिमाइंडर (${target.formattedTime}) कैंसिल कर दिया गया है।",
                voiceResponseHindi = "${target.title} का रिमाइंडर कैंसिल कर दिया गया है।"
            )
        } else {
            ReminderActionResult.Error(
                messageHindi = "इस नाम से कोई मिलता-जुलता रिमाइंडर नहीं मिला।",
                voiceResponseHindi = "मुझे इस नाम का कोई रिमाइंडर नहीं मिला।"
            )
        }
    }

    private fun extractCancelKeyword(query: String): String {
        var text = query.lowercase()
        val removeWords = listOf(
            "cancel", "कैंसिल", "karo", "करो", "mera", "मेरा", "meri", "मेरी",
            "wala", "वाला", "wali", "वाली", "reminder", "रिमाइंडर", "alarm", "अलार्म",
            "hatao", "हटाओ", "radd", "delete", "डिलीट", "band", "बंद", "ka", "का", "ko", "को"
        )
        for (w in removeWords) {
            text = text.replace(Regex("\\b$w\\b", RegexOption.IGNORE_CASE), " ")
        }
        return text.trim()
    }

    /**
     * Lists active reminders via Hindi text and TTS.
     */
    private suspend fun listActiveReminders(): ReminderActionResult {
        val list = reminderDao.getActiveRemindersList(System.currentTimeMillis())
        if (list.isEmpty()) {
            return ReminderActionResult.Listed(
                activeReminders = emptyList(),
                messageHindi = "कोई आगामी रिमाइंडर या अलार्म सेट नहीं है।",
                voiceResponseHindi = "आपके पास अभी कोई आगामी रिमाइंडर या अलार्म सेट नहीं है।"
            )
        }

        val summary = list.take(5).joinToString(", ") { "${it.title} (${it.formattedTime})" }
        val voice = "आपके पास ${list.size} रिमाइंडर हैं: $summary"

        return ReminderActionResult.Listed(
            activeReminders = list,
            messageHindi = "सक्रिय रिमाइंडर्स:\n" + list.joinToString("\n") { "• ${it.title} - ${it.formattedTime}" },
            voiceResponseHindi = voice
        )
    }

    /**
     * Parse natural language time and task description, then persist in Room & AlarmManager.
     */
    private suspend fun parseAndScheduleReminder(rawCommand: String): ReminderActionResult {
        val calendar = Calendar.getInstance()
        val nowMillis = calendar.timeInMillis
        var parsedTimeMillis: Long? = null
        var isAlarm = rawCommand.contains("अलार्म", ignoreCase = true) || rawCommand.contains("alarm", ignoreCase = true)

        val lower = rawCommand.lowercase()

        // Case A: Relative time "X minute baad" / "X ghante baad" / "in X minutes"
        val relativeMinRegex = Regex("(\\d+)\\s*(?:minute|min|मिनट)\\s*(?:baad|बाद|after|later)?", RegexOption.IGNORE_CASE)
        val relativeMinMatch = relativeMinRegex.find(lower)

        val relativeHourRegex = Regex("(\\d+)\\s*(?:ghante|hour|hours|घंटे|घंटा)\\s*(?:baad|बाद|after|later)?", RegexOption.IGNORE_CASE)
        val relativeHourMatch = relativeHourRegex.find(lower)

        val relativeSecRegex = Regex("(\\d+)\\s*(?:second|sec|सेकंड)\\s*(?:baad|बाद)?", RegexOption.IGNORE_CASE)
        val relativeSecMatch = relativeSecRegex.find(lower)

        if (relativeMinMatch != null && (lower.contains("baad") || lower.contains("बाद") || lower.contains("in ") || lower.contains("after"))) {
            val minutes = relativeMinMatch.groupValues[1].toIntOrNull() ?: 1
            calendar.add(Calendar.MINUTE, minutes)
            parsedTimeMillis = calendar.timeInMillis
        } else if (relativeHourMatch != null && (lower.contains("baad") || lower.contains("बाद") || lower.contains("in ") || lower.contains("after"))) {
            val hours = relativeHourMatch.groupValues[1].toIntOrNull() ?: 1
            calendar.add(Calendar.HOUR_OF_DAY, hours)
            parsedTimeMillis = calendar.timeInMillis
        } else if (relativeSecMatch != null && (lower.contains("baad") || lower.contains("बाद"))) {
            val seconds = (relativeSecMatch.groupValues[1].toIntOrNull() ?: 30).coerceAtLeast(15)
            calendar.add(Calendar.SECOND, seconds)
            parsedTimeMillis = calendar.timeInMillis
        }

        // Case B: Absolute time (e.g. "5 baje", "5:30 baje", "sham 7 baje", "subah 6 baje", "7 pm", "8 am")
        if (parsedTimeMillis == null) {
            val timeRegex = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(?:baje|बजे|am|pm|एएम|पीएम)?", RegexOption.IGNORE_CASE)
            val match = timeRegex.find(lower)

            if (match != null) {
                var hour = match.groupValues[1].toIntOrNull() ?: -1
                val minute = match.groupValues[2].toIntOrNull() ?: 0

                if (hour in 0..23) {
                    val isPM = lower.contains("sham") || lower.contains("शाम") ||
                            lower.contains("raat") || lower.contains("रात") ||
                            lower.contains("dopahar") || lower.contains("दोपहर") ||
                            lower.contains("pm") || lower.contains("पीएम")

                    val isAM = lower.contains("subah") || lower.contains("सुबह") ||
                            lower.contains("am") || lower.contains("एएम")

                    if (isPM && hour < 12) {
                        hour += 12
                    } else if (isAM && hour == 12) {
                        hour = 0
                    } else if (!isAM && !isPM && hour in 1..6) {
                        // Ambiguous "5 baje" without morning/evening: if 5 PM is in future today, treat as 5 PM (17:00)
                        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                        if (currentHour in 7..16) {
                            hour += 12
                        }
                    }

                    val targetCal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    // Check if tomorrow explicitly stated or time already passed today
                    val isTomorrow = lower.contains("kal") || lower.contains("कल") || lower.contains("tomorrow")
                    if (isTomorrow || targetCal.timeInMillis <= nowMillis) {
                        targetCal.add(Calendar.DAY_OF_YEAR, 1)
                    }

                    parsedTimeMillis = targetCal.timeInMillis
                }
            }
        }

        // Fallback: Default to 10 minutes if no specific time recognized
        if (parsedTimeMillis == null || parsedTimeMillis <= nowMillis) {
            calendar.timeInMillis = nowMillis
            calendar.add(Calendar.MINUTE, 5)
            parsedTimeMillis = calendar.timeInMillis
        }

        // Extract Task Name / Title
        val taskTitle = extractTaskTitle(rawCommand)
        val formattedTime = dateDisplayFormat.format(Date(parsedTimeMillis))
        val type = if (isAlarm) "ALARM" else "REMINDER"

        val reminderEntity = ReminderEntity(
            title = taskTitle,
            triggerTimeMillis = parsedTimeMillis,
            formattedTime = formattedTime,
            isTriggered = false,
            isCancelled = false,
            createdAt = System.currentTimeMillis(),
            type = type
        )

        val insertedId = reminderDao.insertReminder(reminderEntity)
        val finalReminder = reminderEntity.copy(id = insertedId)

        // Schedule in Android AlarmManager
        val scheduledSuccessfully = scheduleSystemAlarm(finalReminder)

        val labelType = if (isAlarm) "अलार्म" else "रिमाइंडर"
        val voiceMsg = if (scheduledSuccessfully) {
            "$formattedTime पर '$taskTitle' का $labelType सेट कर दिया गया है।"
        } else {
            "$formattedTime पर $labelType सेव किया गया है। सटीक समय के लिए अलार्म अनुमति दें।"
        }

        return ReminderActionResult.Scheduled(
            reminder = finalReminder,
            messageHindi = "✅ $labelType सेट: $taskTitle ($formattedTime)\n[लोकल AlarmManager + Room DB]",
            voiceResponseHindi = voiceMsg
        )
    }

    /**
     * Cleans spoken query into a concise reminder title (e.g. "दवाई लेना", "मीटिंग", "दूध लाना").
     */
    private fun extractTaskTitle(command: String): String {
        var text = command
        val stopPhrases = listOf(
            "mujhe", "मुझे", "yaad dilana", "याद दिलाना", "yaad dilao", "याद दिलाओ",
            "ka reminder", "का रिमाइंडर", "ki yaad", "की याद", "reminder lagao", "रिमाइंडर लगाओ",
            "alarm laga do", "अलार्म लगा दो", "alarm lagao", "अलार्म लगाओ", "set karo", "सेट करो",
            "reminder", "रिमाइंडर", "alarm", "अलार्म", "par", "पर", "ko", "को", "pe", "पे",
            "subah", "सुबह", "sham", "शाम", "raat", "रात", "dopahar", "दोपहर", "kal", "कल",
            "baad", "बाद", "minute", "मिनट", "ghante", "घंटे", "hour", "hours",
            "baje", "बजे", "am", "pm", "today", "aaj", "आज", "please", "जरा", "bhi", "भी",
            "karna hai", "करना है", "lene ka", "लेने का", "dene ka", "देने का"
        )

        // Remove numeric time patterns like "5:30", "5 baje"
        text = text.replace(Regex("\\b\\d{1,2}:\\d{2}\\b"), " ")
        text = text.replace(Regex("\\b\\d{1,2}\\s*(?:baje|बजे|am|pm)\\b", RegexOption.IGNORE_CASE), " ")
        text = text.replace(Regex("\\b\\d+\\s*(?:minute|min|मिनट|ghante|घंटे|sec|सेकंड)\\b", RegexOption.IGNORE_CASE), " ")

        for (phrase in stopPhrases) {
            text = text.replace(Regex("\\b$phrase\\b", RegexOption.IGNORE_CASE), " ")
        }

        val cleaned = text.replace(Regex("\\s+"), " ").trim()
        return if (cleaned.length >= 2) cleaned else "ज़रूरी काम"
    }

    /**
     * Registers an exact alarm with AlarmManager.
     */
    fun scheduleSystemAlarm(reminder: ReminderEntity): Boolean {
        return try {
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_ALARM_TRIGGER
                putExtra(AlarmReceiver.EXTRA_REMINDER_ID, reminder.id)
                putExtra(AlarmReceiver.EXTRA_REMINDER_TITLE, reminder.title)
                putExtra(AlarmReceiver.EXTRA_REMINDER_TYPE, reminder.type)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminder.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminder.triggerTimeMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminder.triggerTimeMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerTimeMillis,
                    pendingIntent
                )
            }
            Log.i(tag, "Scheduled exact alarm for ${reminder.title} at ${reminder.triggerTimeMillis}")
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to schedule alarm: ${e.localizedMessage}")
            false
        }
    }

    /**
     * Cancels an alarm in AlarmManager.
     */
    fun cancelSystemAlarm(reminderId: Long) {
        try {
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_ALARM_TRIGGER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminderId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.i(tag, "Cancelled alarm with ID: $reminderId")
        } catch (e: Exception) {
            Log.e(tag, "Failed to cancel alarm: ${e.localizedMessage}")
        }
    }

    suspend fun deleteReminder(id: Long) {
        cancelSystemAlarm(id)
        reminderDao.deleteReminder(id)
    }
}
