description = "Sqiffy Symbol Processor | KSP implementation of Sqiffy specification"

dependencies {
    implementation(project(":sqiffy"))
    implementation("com.google.devtools.ksp:symbol-processing-api:1.9.25-1.0.20")

    val kotlinPoet = "1.18.1"
    implementation("com.squareup:kotlinpoet:$kotlinPoet")
    implementation("com.squareup:kotlinpoet-ksp:$kotlinPoet")
}