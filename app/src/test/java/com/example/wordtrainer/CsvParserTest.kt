package com.example.wordtrainer

import com.example.wordtrainer.data.seed.CsvParser
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvParserTest {

    @Test
    fun `plain fields split on comma`() {
        assertEquals(listOf("a", "b", "c"), CsvParser.parseLine("a,b,c"))
    }

    @Test
    fun `commas inside quotes are preserved`() {
        assertEquals(
            listOf("cat", "noun", "кот, кошка"),
            CsvParser.parseLine("cat,noun,\"кот, кошка\"")
        )
    }

    @Test
    fun `escaped double quotes are unescaped`() {
        assertEquals(
            listOf("He said \"hi\""),
            CsvParser.parseLine("\"He said \"\"hi\"\"\"")
        )
    }

    @Test
    fun `trailing empty field is kept`() {
        assertEquals(listOf("a", "b", ""), CsvParser.parseLine("a,b,"))
    }
}
