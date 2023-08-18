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
    maven {
        url = uri("https://maven.pkg.github.com/kt2ts/kt2ts-annotation")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
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
            version = "0.0.2"

            from(components["kotlin"])
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/kt2ts/kt2ts-ksp-generator")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
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
