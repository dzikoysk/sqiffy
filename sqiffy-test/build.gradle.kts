import com.google.devtools.ksp.gradle.KspTask

plugins {
    id("com.google.devtools.ksp") version "1.9.20-1.0.13"
}

dependencies {
    testImplementation(project(":sqiffy"))
    kspTest(project(":sqiffy-symbol-processor"))
    testImplementation(project(":sqiffy-symbol-processor"))

    testImplementation("ch.qos.logback:logback-classic:1.4.6")

    testImplementation("org.mariadb.jdbc:mariadb-java-client:2.7.3")
    testImplementation("io.zonky.test:embedded-postgres:2.0.3")
    testImplementation("org.postgresql:postgresql:42.6.0")
    testImplementation("com.h2database:h2:2.1.214")
    testImplementation("mysql:mysql-connector-java:8.0.28")
    testImplementation("org.xerial:sqlite-jdbc:3.42.0.0")

    val testcontainers = "1.18.1"
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