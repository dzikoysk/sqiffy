description = "Sqiffy Symbol Processor | KSP implementation of Sqiffy specification"

dependencies {
    implementation(project(":sqiffy"))
    implementation("com.google.devtools.ksp:symbol-processing-api:2.2.0-2.0.2")

    val kotlinPoet = "2.2.0"
    implementation("com.squareup:kotlinpoet:$kotlinPoet")
    implementation("com.squareup:kotlinpoet-ksp:$kotlinPoet")
}