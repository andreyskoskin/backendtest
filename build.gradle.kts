import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.21"
    application
}

group = "backendtest"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val test by tasks.getting(Test::class) {
    useJUnitPlatform { }
}

application {
    mainClassName = "MainKt"
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    compile("com.sparkjava:spark-kotlin:1.0.0-alpha")

    testImplementation(group = "io.kotlintest", name = "kotlintest-runner-junit5", version = "3.3.2")

    // https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-core
    compile("com.fasterxml.jackson.core:jackson-core:2.9.8")
    // https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-databind
    compile("com.fasterxml.jackson.core:jackson-databind:2.9.8")
    // https://mvnrepository.com/artifact/com.fasterxml.jackson.module/jackson-module-kotlin
    compile("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.8")
    // https://mvnrepository.com/artifact/org.slf4j/slf4j-simple
    compile(group = "org.slf4j", name = "slf4j-simple", version = "1.7.26")


    // https://mvnrepository.com/artifact/org.junit.jupiter/junit-jupiter-api
    testCompile(group = "org.junit.jupiter", name = "junit-jupiter-api", version = "5.4.1")

    // https://mvnrepository.com/artifact/org.slf4j/slf4j-simple
    testCompile(group = "org.slf4j", name = "slf4j-simple", version = "1.7.26")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

val fatJar = task("fatJar", type = Jar::class) {
    baseName = "${project.name}-full"
    manifest {
        attributes["Implementation-Title"] = "Money Transfers"
        attributes["Implementation-Version"] = version
        attributes["Main-Class"] = "MainKt"
    }
    from(configurations.runtime.map { if (it.isDirectory) it else zipTree(it) })
    with(tasks["jar"] as CopySpec)
}

tasks {
    "build" {
        dependsOn(fatJar)
    }
}