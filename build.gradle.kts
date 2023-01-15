import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-library`
    kotlin("jvm") version "1.8.0"
    application
}

group = "com.dzikoysk"
version = "1.0-SNAPSHOT"

allprojects {
    apply(plugin = "java-library")
    apply(plugin = "signing")
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "application")

    repositories {
        maven("https://maven.reposilite.com/maven-central")
        maven("https://maven.reposilite.com/releases")
    }

    java {
        withJavadocJar()
        withSourcesJar()
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "11"
            languageVersion = "1.8"
            freeCompilerArgs = listOf(
                "-Xjvm-default=all", // For generating default methods in interfaces
                "-Xcontext-receivers"
            )
        }
    }
}

subprojects {
    dependencies {
        val exposed = "0.41.1"
        api("org.jetbrains.exposed:exposed-core:$exposed")
        api("org.jetbrains.exposed:exposed-dao:$exposed")
        api("org.jetbrains.exposed:exposed-jdbc:$exposed")
        api("org.jetbrains.exposed:exposed-java-time:$exposed")
        api("net.dzikoysk:exposed-upsert:1.0.3")
        api("com.zaxxer:HikariCP:5.0.1")

        val junit = "5.8.2"
        testImplementation("org.junit.jupiter:junit-jupiter-params:$junit")
        testImplementation("org.junit.jupiter:junit-jupiter-api:$junit")
        testImplementation("org.junit.jupiter:junit-jupiter-engine:$junit")

        testImplementation("org.assertj:assertj-core:3.24.1")
    }

    tasks.test {
        useJUnitPlatform()
    }
}