package com.example.wordtrainer.data.seed

/**
 * Минимальный, но корректный парсер CSV (RFC 4180): понимает поля в двойных
 * кавычках с запятыми внутри и экранированные кавычки (`""`). Наивный
 * `String.split(",")` на этих данных разваливался.
 */
object CsvParser {

    /** Разбивает одну строку CSV на поля. */
    fun parseLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"'); i++ // экранированная кавычка ""
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    result.add(sb.toString()); sb.setLength(0)
                }
                else -> sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }
}
