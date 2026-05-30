plugins {
    id("com.google.devtools.ksp") version "2.3.0"
    jacoco
}

dependencies {
    testImplementation(project(":sqiffy"))
    kspTest(project(":sqiffy-symbol-processor"))
    testImplementation(project(":sqiffy-symbol-processor"))

    testImplementation("ch.qos.logback:logback-classic:1.5.33")

    testImplementation("org.mariadb.jdbc:mariadb-java-client:3.5.8")
    testImplementation("io.zonky.test:embedded-postgres:2.2.2")
    testImplementation("org.postgresql:postgresql:42.7.11")
    testImplementation("com.h2database:h2:2.4.240")
    testImplementation("com.mysql:mysql-connector-j:9.7.0")
    testImplementation("org.xerial:sqlite-jdbc:3.53.1.0")

    val liquibase = "4.33.0"
    testImplementation("org.liquibase:liquibase-core:$liquibase")

    val testcontainers = "2.0.5"
    testImplementation("org.testcontainers:testcontainers-postgresql:$testcontainers")
    testImplementation("org.testcontainers:testcontainers-mysql:$testcontainers")
    testImplementation("org.testcontainers:testcontainers-mariadb:$testcontainers")
    testImplementation("org.testcontainers:testcontainers:$testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:$testcontainers")
}

sourceSets.configureEach {
    kotlin.srcDir("${layout.buildDirectory.get()}/generated/ksp/$name/kotlin/")
}

tasks.matching { it.name.startsWith("ksp") }.configureEach {
    dependsOn("clean")
}

// Tests live here but exercise the :sqiffy core, so the coverage report is pointed at its
// main sources rather than this module's (test-only) classes. The symbol processor runs at
// KSP compile time (not in the test JVM), so it is verified indirectly - its generated output
// is compiled and exercised by these tests - and is intentionally left out of the runtime report.
private val coveredModules = listOf(project(":sqiffy"))

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(files(coveredModules.map { it.layout.buildDirectory.dir("classes/kotlin/main") }))
    sourceDirectories.setFrom(files(coveredModules.map { it.layout.projectDirectory.dir("src/main/kotlin") }))
    reports {
        html.required.set(true)
        xml.required.set(true)
    }
}