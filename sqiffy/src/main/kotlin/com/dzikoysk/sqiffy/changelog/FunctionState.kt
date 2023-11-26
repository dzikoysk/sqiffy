package com.dzikoysk.sqiffy.changelog

data class FunctionState(
    val name: String,
    val parameters: List<String>,
    val returnType: String,
    val body: String
)