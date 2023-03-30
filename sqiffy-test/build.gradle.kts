import com.google.devtools.ksp.gradle.KspTask

plugins {
    id("com.google.devtools.ksp") version "1.8.0-1.0.8"
}

dependencies {


    testImplementation(project(":sqiffy"))
    kspTest(project(":sqiffy-symbol-processor"))
    testImplementation(project(":sqiffy-symbol-processor"))
    testImplementation("com.h2database:h2:2.1.214")
    testImplementation("ch.qos.logback:logback-classic:1.4.6")
}

sourceSets.configureEach {
    kotlin.srcDir("$buildDir/generated/ksp/$name/kotlin/")
}

tasks.withType<KspTask>() {
    dependsOn("clean")
}

tasks.getByName("sourcesJar").dependsOn("kspKotlin")