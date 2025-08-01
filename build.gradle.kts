plugins {
    kotlin("jvm") version "2.1.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("java")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

javafx {
    version = "21.0.3"
    modules = listOf("javafx.controls", "javafx.graphics", "javafx.swing")
}

group = "org.lewapnoob.FileZero"
version = "1.0-SNAPSHOT"

tasks {
    shadowJar {
        archiveBaseName.set("FileZero")
        archiveClassifier.set("")
        archiveVersion.set("")
    }
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "org.lewapnoob.FileZero.FileZeroKt"
    }
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "org.lewapnoob.FileZero.FileZeroKt",
            "Class-Path" to "org.lewapnoob.FileZero.FileZeroKt",
            "JVM-Options" to "-Xmx2048m -Xms1024m"
        )
    }
}