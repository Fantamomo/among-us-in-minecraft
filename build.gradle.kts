plugins {
    kotlin("jvm") version "2.3.20-Beta2"
    id("com.gradleup.shadow") version "8.3.0"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20-Beta2"
}

group = "com.fantamomo.mc"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://repo.inventivetalent.org/repository/public/") {
        name = "inventive-repo"
    }
}

dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    implementation(kotlin("stdlib"))

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    implementation("com.fantamomo.mc:kotlin-adventure:1.4-SNAPSHOT")
    implementation("com.fantamomo.mc:brigadier-kt:1.3-SNAPSHOT")
    implementation("com.fantamomo.mc:brigadier-interception:1.0-SNAPSHOT")

    implementation("org.mineskin:java-client:3.2.1-SNAPSHOT")
    implementation("org.mineskin:java-client-jsoup:3.2.1-SNAPSHOT")
}

val targetJavaVersion = 24
kotlin {
    jvmToolchain(targetJavaVersion)
    compilerOptions {
        freeCompilerArgs = listOf("-opt-in=kotlin.uuid.ExperimentalUuidApi", "-Xcontext-parameters")
    }
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

tasks.build {
    dependsOn("shadowJar")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}