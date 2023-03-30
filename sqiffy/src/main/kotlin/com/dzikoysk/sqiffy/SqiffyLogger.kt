package com.dzikoysk.sqiffy

import org.slf4j.Logger
import org.slf4j.event.Level

interface SqiffyLogger {
    fun log(level: Level, message: String)
}

class StdoutSqiffyLogger : SqiffyLogger {

    override fun log(level: Level, message: String) {
        println("[$level] $message")
    }

}

class Slf4JSqiffyLogger(private val logger: Logger) : SqiffyLogger {

    override fun log(level: Level, message: String) {
        when (level) {
            Level.TRACE -> logger.trace(message)
            Level.DEBUG -> logger.debug(message)
            Level.INFO -> logger.info(message)
            Level.WARN -> logger.warn(message)
            Level.ERROR -> logger.error(message)
        }
    }

}