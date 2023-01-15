dependencies {
    implementation(project(":sqiffy-specification"))
    implementation(project(":sqiffy-library"))
    implementation("com.google.devtools.ksp:symbol-processing-api:1.8.0-1.0.8")

    val kotlinPoet = "1.12.0"
    implementation("com.squareup:kotlinpoet:$kotlinPoet")
    implementation("com.squareup:kotlinpoet-ksp:$kotlinPoet")
}