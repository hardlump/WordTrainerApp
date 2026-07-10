package com.example.wordtrainer.domain

/**
 * Уровни/XP считаются «на лету» из пожизненных показателей — никакого хранения.
 * Порог до следующего уровня растёт: 100, 150, 200, … (+50 за уровень).
 */
object LevelSystem {

    data class Level(val level: Int, val xpIntoLevel: Int, val xpForNextLevel: Int)

    /** XP = повторения + 10 за каждое выученное слово. */
    fun xp(totalReviews: Int, learned: Int): Int = totalReviews + learned * 10

    fun levelOf(totalXp: Int): Level {
        var level = 1
        var accumulated = 0
        var need = 100
        while (totalXp >= accumulated + need) {
            accumulated += need
            level++
            need = 100 + (level - 1) * 50
        }
        return Level(level, totalXp - accumulated, need)
    }
}
