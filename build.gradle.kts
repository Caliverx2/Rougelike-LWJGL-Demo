plugins {
    kotlin("jvm") version "2.1.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")

    val lwjglVersion = "3.3.6"
    implementation("org.lwjgl", "lwjgl", lwjglVersion)
    implementation("org.lwjgl", "lwjgl-glfw", lwjglVersion)
    implementation("org.lwjgl", "lwjgl-opengl", lwjglVersion)

    runtimeOnly("org.lwjgl", "lwjgl", lwjglVersion, classifier = "natives-windows")   // Dla Windows
    runtimeOnly("org.lwjgl", "lwjgl-glfw", lwjglVersion, classifier = "natives-windows")
    runtimeOnly("org.lwjgl", "lwjgl-opengl", lwjglVersion, classifier = "natives-windows")

    runtimeOnly("org.lwjgl", "lwjgl", lwjglVersion, classifier = "natives-linux")     // Dla Linux
    runtimeOnly("org.lwjgl", "lwjgl-glfw", lwjglVersion, classifier = "natives-linux")
    runtimeOnly("org.lwjgl", "lwjgl-opengl", lwjglVersion, classifier = "natives-linux")

    runtimeOnly("org.lwjgl", "lwjgl", lwjglVersion, classifier = "natives-macos")     // Dla macOS
    runtimeOnly("org.lwjgl", "lwjgl-glfw", lwjglVersion, classifier = "natives-macos")
    runtimeOnly("org.lwjgl", "lwjgl-opengl", lwjglVersion, classifier = "natives-macos")

    // Dla Apple Silicon (M1/M2/M3)
    runtimeOnly("org.lwjgl", "lwjgl", lwjglVersion, classifier = "natives-macos-arm64")
    runtimeOnly("org.lwjgl", "lwjgl-glfw", lwjglVersion, classifier = "natives-macos-arm64")
    runtimeOnly("org.lwjgl", "lwjgl-opengl", lwjglVersion, classifier = "natives-macos-arm64")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
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