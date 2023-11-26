package com.dzikoysk.sqiffy.migrator

import com.dzikoysk.sqiffy.SqiffyDatabase

interface Migrator<RESULT> {

    fun runMigrations(database: SqiffyDatabase): RESULT

}