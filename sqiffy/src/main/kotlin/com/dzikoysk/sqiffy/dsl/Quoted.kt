package com.dzikoysk.sqiffy.dsl

fun interface Quoted {

    fun toString(quoteType: QuoteType): String

}

class QuoteType(private val quote: String) {

    companion object {
        val BACKTICK = QuoteType("`")
        val DOUBLE_QUOTE = QuoteType("\"")
        val SINGLE_QUOTE = QuoteType("'")
    }

    fun quote(text: String): String =
        "$quote$text$quote"

}