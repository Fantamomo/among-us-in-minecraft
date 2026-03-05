import io.papermc.paperweight.userdev.ReobfArtifactConfiguration

plugins {
    kotlin("jvm") version "2.3.20-Beta2"
    kotlin("plugin.serialization") version "2.3.20-Beta2"
    id("com.gradleup.shadow") version "8.3.0"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
}

group = "com.fantamomo.mc"
version = "1.1-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://repo.inventivetalent.org/repository/public/") {
        name = "inventive-repo"
    }
    github(repo = "brigadier-interception")
    github(repo = "kotlin-adventure")
    github(repo = "brigadier-kt")
}

fun RepositoryHandler.github(user: String = "Fantamomo", repo: String): MavenArtifactRepository = maven {
    url = uri("https://maven.pkg.github.com/$user/$repo")
    name = "GitHub $user/$repo"
    credentials {
        username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_USERNAME")
        password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
    }
}

dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")

    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    implementation("com.fantamomo.mc:kotlin-adventure:1.4-SNAPSHOT")
    implementation("com.fantamomo.mc:brigadier-kt:1.5-SNAPSHOT")
    implementation("com.fantamomo.mc:brigadier-interception:1.1-SNAPSHOT")

    implementation("org.mineskin:java-client:3.2.1-SNAPSHOT")
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
    compilerOptions {
        freeCompilerArgs = listOf("-opt-in=kotlin.uuid.ExperimentalUuidApi", "-Xcontext-parameters")
    }
}

paperweight.reobfArtifactConfiguration = ReobfArtifactConfiguration.MOJANG_PRODUCTION

val excludedFromShadow = listOf(
    "com.google.guava",
    "io.papermc.paper",
    "org.bukkit",
    "net.kyori",
    "com.mojang",
)

tasks {
    shadowJar {
        mergeServiceFiles()
        excludedFromShadow.forEach { group ->
            dependencies { exclude(dependency("$group:.*")) }
        }
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }
}