---
outline: deep
aside: false
---

# Installation

All you need to configure Sqiffy with Gradle is to add the following dependencies to your `build.gradle.kts` file:

```kotlin
plugins {
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" // for Kotlin 1.9.22
}

dependencies {
    val sqiffy = "1.0.0-alpha.63"
    ksp("com.dzikoysk.sqiffy:sqiffy-symbol-processor:$sqiffy")
    implementation("com.dzikoysk.sqiffy:sqiffy:$sqiffy")
}
```

Once you have added the dependencies, you can start using Sqiffy in your project.

### FAQ

**Q**: After compilation, part of my code is not generated. What should I do? <br>
**A**: You may need to disable incremental KSP processing. To do this, add the following line to your `gradle.properties` file:

```properties
# Disable incremental compilation in KSP
ksp.incremental=false
```