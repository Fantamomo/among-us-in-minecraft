plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    // The Gradle Kotlin compiler runs the embeddable (shaded) compiler, so we must compile against it.
    compileOnly(kotlin("compiler-embeddable"))
}

kotlin {
    jvmToolchain(25)
}
