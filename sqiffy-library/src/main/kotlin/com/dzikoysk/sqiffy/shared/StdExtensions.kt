package com.dzikoysk.sqiffy.shared

fun <T> MutableList<T>.replaceFirst(condition: (T) -> Boolean, value: (T) -> T): Boolean =
    indexOfFirst(condition)
        .takeIf { it != -1}
        ?.also { this[it] = value(this[it]) } != null
        //?: run { add(value(null)) } // for .replaceFirstOrAdd