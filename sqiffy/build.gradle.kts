description = "Sqiffy Library | Core implementation of Sqiffy specification"

dependencies {
    implementation(kotlin("reflect"))

    val jdbi = "3.37.1"
    api("org.jdbi:jdbi3-core:$jdbi")
    api("org.jdbi:jdbi3-sqlobject:$jdbi")
    api("org.jdbi:jdbi3-postgres:$jdbi")
    api("org.jdbi:jdbi3-kotlin:$jdbi")
    api("org.jdbi:jdbi3-kotlin-sqlobject:$jdbi")
    api("org.jdbi:jdbi3-jackson2:$jdbi")
}