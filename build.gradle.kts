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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    implementation("com.fantamomo.mc:kotlin-adventure:1.4-SNAPSHOT")
    implementation("com.fantamomo.mc:brigadier-kt:1.5-SNAPSHOT")
    implementation("com.fantamomo.mc:brigadier-interception:1.1-SNAPSHOT")

    implementation("org.mineskin:java-client:3.2.1-SNAPSHOT")


    testImplementation(kotlin("test"))
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

abstract class GitHashSource : ValueSource<String, ValueSourceParameters.None> {
    override fun obtain(): String =
        ProcessBuilder("git", "rev-parse", "HEAD")
            .start()
            .inputStream
            .bufferedReader()
            .readLine()
            ?.trim()
            ?: "unknown"
}

val gitHash: Provider<String> = providers.of(GitHashSource::class) {}

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
        val gitHash = providers.of(GitHashSource::class) {}
        val pluginVersion = version.toString()

        inputs.property("version", pluginVersion)
        inputs.property("githash", gitHash)
        filteringCharset = "UTF-8"
        filesMatching("paper-plugin.yml") {
            expand(mapOf(
                "version" to pluginVersion,
                "githash" to gitHash.get()
            ))
        }
    }
}