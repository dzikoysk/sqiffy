plugins {
    id("com.google.devtools.ksp") version "1.8.0-1.0.8"
}

dependencies {
    testImplementation(project(":sqiffy-library"))
    kspTest(project(":sqiffy-symbol-processor"))
    testImplementation(project(":sqiffy-symbol-processor"))
    testImplementation("com.h2database:h2:2.1.214")
    testImplementation("ch.qos.logback:logback-classic:1.4.5")
}

sourceSets.configureEach {
    kotlin.srcDir("$buildDir/generated/ksp/$name/kotlin/")
}