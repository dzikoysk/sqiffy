description = "Sqiffy Symbol Processor | KSP implementation of Sqiffy specification"

dependencies {
    implementation(project(":sqiffy"))
    implementation("com.google.devtools.ksp:symbol-processing-api:1.9.21-1.0.15")

    val kotlinPoet = "1.14.2"
    implementation("com.squareup:kotlinpoet:$kotlinPoet")
    implementation("com.squareup:kotlinpoet-ksp:$kotlinPoet")
}