package com.example.wordtrainer

import android.app.Application
import com.example.wordtrainer.data.AchievementManager
import com.example.wordtrainer.data.AchievementStore
import com.example.wordtrainer.data.SettingsStore
import com.example.wordtrainer.data.WordRepository
import com.example.wordtrainer.reminders.ReminderScheduler
import com.example.wordtrainer.tts.Speaker

/** Простейший ServiceLocator: держит синглтоны репозитория, настроек и озвучки. */
class WordTrainerApp : Application() {

    val settings: SettingsStore by lazy { SettingsStore(this) }
    val repository: WordRepository by lazy { WordRepository(this) }
    val speaker: Speaker by lazy { Speaker(this) }
    val achievementStore: AchievementStore by lazy { AchievementStore(this) }
    val achievements: AchievementManager by lazy { AchievementManager(this, settings, achievementStore) }

    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.createChannel(this)
    }
}
