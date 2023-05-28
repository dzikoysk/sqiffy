description = "Sqiffy Symbol Processor | KSP implementation of Sqiffy specification"

dependencies {
    implementation(project(":sqiffy"))
    implementation("com.google.devtools.ksp:symbol-processing-api:1.8.21-1.0.11")

    val kotlinPoet = "1.12.0"
    implementation("com.squareup:kotlinpoet:$kotlinPoet")
    implementation("com.squareup:kotlinpoet-ksp:$kotlinPoet")
}