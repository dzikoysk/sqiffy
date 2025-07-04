import com.google.devtools.ksp.gradle.KspTask

plugins {
    id("com.google.devtools.ksp") version "2.2.0-2.0.2"
}

dependencies {
    testImplementation(project(":sqiffy"))
    kspTest(project(":sqiffy-symbol-processor"))
    testImplementation(project(":sqiffy-symbol-processor"))

    testImplementation("ch.qos.logback:logback-classic:1.5.18")

    testImplementation("org.mariadb.jdbc:mariadb-java-client:3.5.4")
    testImplementation("io.zonky.test:embedded-postgres:2.1.0")
    testImplementation("org.postgresql:postgresql:42.7.7")
    testImplementation("com.h2database:h2:2.3.232")
    testImplementation("mysql:mysql-connector-java:8.0.33")
    testImplementation("org.xerial:sqlite-jdbc:3.50.2.0")

    val liquibase = "4.25.0"
    testImplementation("org.liquibase:liquibase-core:$liquibase")

    val testcontainers = "1.21.3"
    testImplementation("org.testcontainers:postgresql:$testcontainers")
    testImplementation("org.testcontainers:mysql:$testcontainers")
    testImplementation("org.testcontainers:mariadb:$testcontainers")
    testImplementation("org.testcontainers:testcontainers:$testcontainers")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainers")
}

sourceSets.configureEach {
    kotlin.srcDir("${layout.buildDirectory.get()}/generated/ksp/$name/kotlin/")
}

tasks.withType<KspTask> {
    dependsOn("clean")
}