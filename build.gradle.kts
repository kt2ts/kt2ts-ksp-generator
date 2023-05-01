plugins {
    kotlin("jvm") version "1.8.0" //apply false
}

//buildscript {
//    dependencies {
//        classpath(kotlin("gradle-plugin", version = "1.8.0"))
//    }
//}

repositories {
    mavenCentral()
    mavenLocal()
}

//tasks {
//    compileKotlin { kotlinOptions.jvmTarget = "1.8" }
//    compileTestKotlin { kotlinOptions.jvmTarget = "1.8" }
//}

//publishing {
//    publications {
//        create<MavenPublication>("maven") {
//            groupId = "ktts"
//            artifactId = "ktts"
//            version = "1.0.0"
//
//            from(components["kotlin"])
//        }
//    }
//}

dependencies {
    implementation("ktts:ktts-annotations:1.0.0")

    implementation("com.google.devtools.ksp:symbol-processing-api:1.7.21-1.0.8")
    implementation("org.json:json:20220320")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")
}
