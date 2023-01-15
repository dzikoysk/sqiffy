# Sqiffy 

**sqiffy** _(or just squiffy 🍹)_ - Compound **SQ**L framework with type-safe DSL API generated at compile-time from scheme d**iff**.
It is dedicated for applications, plugins & libraries responsible for internal database management.

### What it does?

1. User defines versioned table definition using `@Defintion` annotation 
2. Sqiffy's annotation processor (KSP) at compile-time:
   1. Converts table definitions into versioned changelog, similar to [Liquibase](https://github.com/liquibase/liquibase)
   2. Generates up-to-date entity data classes for Kotlin with [KotlinPoet](https://github.com/square/kotlinpoet)
   3. Creates bindings for [Exposed (<ins>DSL</ins>)](https://github.com/JetBrains/Exposed) framework
3. When application starts, you can run set of prepared versioned migrations against current database state

### Supports

* MySQL/MariaDB
* H2 (MySQL Mode)