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
    mavenLocal()
}

sourceSets.main {
    java.srcDirs("src/main/kotlin")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "io.github.kt2ts"
            artifactId = "kt2ts-ksp-generator"
            version = "0.0.1"

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
