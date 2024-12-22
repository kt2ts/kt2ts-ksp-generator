plugins {
    kotlin("jvm") version "1.8.21" //apply false
    `maven-publish`
}

buildscript {
    dependencies {
        classpath(kotlin("gradle-plugin", version = "1.8.0"))
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://mlorber.net/maven_repo") }
}

sourceSets.main {
    java.srcDirs("src/main/kotlin")
}

java {
    /*
     * Setup JDK and will also set target with Kotlin projects.
     * https://docs.gradle.org/current/userguide/toolchains.html
     */
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "io.github.kt2ts"
            artifactId = "kt2ts-ksp-generator"
            version = "0.0.8"

            from(components["kotlin"])
        }
    }
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation("io.github.kt2ts:kt2ts-annotation:1.0.0")

    implementation("com.google.devtools.ksp:symbol-processing-api:1.8.21-1.0.11")
    implementation("org.json:json:20230227")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
}
