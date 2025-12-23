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
val MainClass = "org.lewapnoob.FileZero.MainFileZeroKt"

tasks {
    shadowJar {
        archiveBaseName.set("FileZero")
        archiveClassifier.set("")
        archiveVersion.set("")
    }
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = MainClass
    }
}

tasks.jar {
    archiveBaseName.set("FileZero_POCO_X6_PRO_5G")
    archiveVersion.set("")
    archiveClassifier.set("")

    manifest {
        attributes(
            "Main-Class" to MainClass,
            "JVM-Options" to "-Xmx1024m -Xms512m"
        )
    }

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}