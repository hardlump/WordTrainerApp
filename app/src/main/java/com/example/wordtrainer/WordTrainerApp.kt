package com.example.wordtrainer

import android.app.Application
import com.example.wordtrainer.data.AchievementManager
import com.example.wordtrainer.data.AchievementStore
import com.example.wordtrainer.data.SettingsStore
import com.example.wordtrainer.data.WordRepository
import com.example.wordtrainer.data.coach.CoachHistoryStore
import com.example.wordtrainer.data.coach.CoachRepository
import com.example.wordtrainer.data.coach.CoachSettingsStore
import com.example.wordtrainer.reminders.ReminderScheduler
import com.example.wordtrainer.tts.Speaker

/** Простейший ServiceLocator: держит синглтоны репозитория, настроек и озвучки. */
class WordTrainerApp : Application() {

    val settings: SettingsStore by lazy { SettingsStore(this) }
    val repository: WordRepository by lazy { WordRepository(this) }
    val speaker: Speaker by lazy { Speaker(this) }
    val achievementStore: AchievementStore by lazy { AchievementStore(this) }
    val achievements: AchievementManager by lazy { AchievementManager(this, settings, achievementStore) }

    // --- ИИ-коуч (независимый модуль общения) ---
    val coachSettings: CoachSettingsStore by lazy { CoachSettingsStore(this) }
    val coachHistory: CoachHistoryStore by lazy { CoachHistoryStore(this) }
    val coachRepository: CoachRepository by lazy { CoachRepository(coachSettings) }

    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.createChannel(this)
    }
}
