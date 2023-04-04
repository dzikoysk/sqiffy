import com.google.devtools.ksp.gradle.KspTask

plugins {
    id("com.google.devtools.ksp") version "1.8.10-1.0.9"
}

dependencies {
    testImplementation(project(":sqiffy"))
    kspTest(project(":sqiffy-symbol-processor"))
    testImplementation(project(":sqiffy-symbol-processor"))
    testImplementation("com.h2database:h2:2.1.214")
    testImplementation("ch.qos.logback:logback-classic:1.4.6")
    testImplementation("io.zonky.test:embedded-postgres:2.0.3")
    testImplementation("org.postgresql:postgresql:42.6.0")
}

sourceSets.configureEach {
    kotlin.srcDir("$buildDir/generated/ksp/$name/kotlin/")
}

tasks.withType<KspTask> {
    dependsOn("clean")
}