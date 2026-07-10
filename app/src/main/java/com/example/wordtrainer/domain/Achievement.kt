package com.example.wordtrainer.domain

import com.example.wordtrainer.R

/**
 * Достижения. Порог/условие проверяется в AchievementManager, здесь — только
 * идентификатор (имя enum) и строковые ресурсы названия/описания (локализуемые).
 */
enum class Achievement(val titleRes: Int, val descRes: Int) {
    STREAK_3(R.string.ach_streak3_title, R.string.ach_streak3_desc),
    STREAK_7(R.string.ach_streak7_title, R.string.ach_streak7_desc),
    STREAK_30(R.string.ach_streak30_title, R.string.ach_streak30_desc),
    STREAK_100(R.string.ach_streak100_title, R.string.ach_streak100_desc),
    REVIEWS_100(R.string.ach_reviews100_title, R.string.ach_reviews100_desc),
    REVIEWS_1000(R.string.ach_reviews1000_title, R.string.ach_reviews1000_desc),
    REVIEWS_5000(R.string.ach_reviews5000_title, R.string.ach_reviews5000_desc),
    LEARNED_10(R.string.ach_learned10_title, R.string.ach_learned10_desc),
    LEARNED_100(R.string.ach_learned100_title, R.string.ach_learned100_desc),
    LEARNED_500(R.string.ach_learned500_title, R.string.ach_learned500_desc),
    PERFECT_DAY(R.string.ach_perfect_day_title, R.string.ach_perfect_day_desc),
    OVERACHIEVER(R.string.ach_overachiever_title, R.string.ach_overachiever_desc),
    NIGHT_OWL(R.string.ach_night_owl_title, R.string.ach_night_owl_desc),
    EARLY_BIRD(R.string.ach_early_bird_title, R.string.ach_early_bird_desc),
    POLYGLOT(R.string.ach_polyglot_title, R.string.ach_polyglot_desc),
    IMPORTER(R.string.ach_importer_title, R.string.ach_importer_desc),
    DICTIONARY_50(R.string.ach_dict50_title, R.string.ach_dict50_desc);
}
