import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.0"
    `java-library`

    application
    signing
    `maven-publish`
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
}

description = "Sqiffy | Parent module"

allprojects {
    apply(plugin = "java-library")
    apply(plugin = "signing")
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "application")
    apply(plugin = "signing")

    group = "com.dzikoysk.sqiffy"
    version = "1.0.0-alpha.25"

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8

        withJavadocJar()
        withSourcesJar()
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_1_8.toString()
            languageVersion = "1.8"
            freeCompilerArgs = listOf(
                "-Xjvm-default=all", // For generating default methods in interfaces
                "-Xcontext-receivers"
            )
        }
    }

    repositories {
        mavenCentral()
    }

    afterEvaluate {
        description
            ?.takeIf { it.isNotEmpty() }
            ?.split("|")
            ?.let { (projectName, projectDescription) ->
                publishing {
                    publications {
                        create<MavenPublication>("library") {
                            pom {
                                name.set(projectName.trim())
                                description.set(projectDescription.trim())
                                url.set("https://github.com/dzikoysk/sqiffy")

                                licenses {
                                    license {
                                        name.set("The Apache License, Version 2.0")
                                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                                    }
                                }
                                developers {
                                    developer {
                                        id.set("dzikoysk")
                                        name.set("dzikoysk")
                                        email.set("dzikoysk@dzikoysk.net")
                                    }
                                }
                                scm {
                                    connection.set("scm:git:git://github.com/dzikoysk/sqiffy.git")
                                    developerConnection.set("scm:git:ssh://github.com/dzikoysk/sqiffy.git")
                                    url.set("https://github.com/dzikoysk/sqiffy.git")
                                }
                            }

                            from(components.getByName("java"))
                        }
                    }
                }

                if (findProperty("signing.keyId").takeIf { it?.toString()?.trim()?.isNotEmpty() == true } != null) {
                    signing {
                        sign(publishing.publications.getByName("library"))
                    }
                }
            }
    }
}

subprojects {
    dependencies {
        api("com.zaxxer:HikariCP:5.0.1")

        val junit = "5.9.3"
        testImplementation("org.junit.jupiter:junit-jupiter-params:$junit")
        testImplementation("org.junit.jupiter:junit-jupiter-api:$junit")
        testImplementation("org.junit.jupiter:junit-jupiter-engine:$junit")

        testImplementation("org.assertj:assertj-core:3.24.1")
    }

    tasks.test {
        useJUnitPlatform()
    }
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            username.set(getEnvOrProperty("SONATYPE_USER", "sonatypeUser"))
            password.set(getEnvOrProperty("SONATYPE_PASSWORD", "sonatypePassword"))
        }
    }
}

fun getEnvOrProperty(env: String, property: String): String? =
    System.getenv(env) ?: findProperty(property)?.toString()