import io.papermc.paperweight.userdev.ReobfArtifactConfiguration

plugins {
    kotlin("jvm") version "2.5.0-Beta2"
    kotlin("plugin.serialization") version "2.5.0-Beta2"
    id("com.gradleup.shadow") version "9.6.1"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.24"
}

group = "com.fantamomo.mc"
version = "2.3.1-SNAPSHOT"

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
    paperweight.paperDevBundle("26.3.build.+")

    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    implementation("com.fantamomo.mc:kotlin-adventure:1.4-SNAPSHOT")
    implementation("com.fantamomo.mc:brigadier-kt:1.5-SNAPSHOT")
    implementation("com.fantamomo.mc:brigadier-interception:1.1-SNAPSHOT")

    implementation("org.mineskin:java-client:3.2.7")
    implementation("ai.koog:koog-agents-jvm:1.3.0")
    implementation("ai.koog:prompt-executor-openrouter-client:1.3.0")


    testImplementation(kotlin("test"))
}

val targetJavaVersion = 25
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

abstract class GitUnattachedSource : ValueSource<Boolean, ValueSourceParameters.None> {
    override fun obtain(): Boolean {
        val process = ProcessBuilder("git", "status", "--porcelain", "--untracked-files=no")
            .start()

        val output = process.inputStream
            .bufferedReader()
            .readLines()
            .filterNot {
                it.startsWith("?? ") || // skip untracked files
                        it == " M gradlew" || // ignore chmod changes on gradlew caused by GitHub Actions
                        it.isBlank() // ignore empty lines
            }
        return output.isNotEmpty()
    }
}

val gitHash: Provider<String> = providers.of(GitHashSource::class) {}
val gitUnattached: Provider<Boolean> = providers.of(GitUnattachedSource::class) {}

tasks.jar {
    archiveClassifier.set("thin")

    manifest {
        attributes(
            "Jar-Type" to "thin"
        )
    }
}

val liteJar = tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("liteJar") {
    description = "Create the lite jar, with only the bare minimum dependencies"
    archiveClassifier.set("lite")

    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()

    from(sourceSets.main.get().output)
    configurations = listOf(project.configurations.runtimeClasspath.get())

    // These dependencies are intentionally excluded from the shaded (fat) JAR.
    // They are resolved and downloaded dynamically at runtime on the first plugin startup
    // via AmongUsPluginLoader (src/main/java/com/fantamomo/mc/amongus/AmongUsPluginLoader.java), keeping the plugin artifact lightweight.
    exclude("org/jetbrains/kotlinx/**")
    exclude("org/mineskin/**")
    exclude("ai/koog/**")

    // Internal project dependencies (com.fantamomo.mc) are currently published via GitHub Packages
    // and require authentication using a GitHub token.
    // Embedding such credentials inside the JAR would pose a security risk.
    //
    // Since these artifacts are not yet available in a fully public repository,
    // they are bundled directly into the shaded JAR for now.
    dependencies {
        include(dependency("com.fantamomo.mc:.*"))
    }

    excludedFromShadow.forEach { group ->
        dependencies { exclude(dependency("$group:.*")) }
    }

    manifest {
        attributes(
            "Jar-Type" to "lite"
        )
    }
}

val standaloneJar = tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("standaloneJar") {
    description = "Create the standalone jar, with all dependencies"
    archiveClassifier.set("standalone")

    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()

    from(sourceSets.main.get().output)
    configurations = listOf(project.configurations.runtimeClasspath.get())

    manifest {
        attributes(
            "Jar-Type" to "standalone"
        )
    }
}

liteJar {
    dependsOn(tasks.classes)
}

standaloneJar {
    dependsOn(tasks.classes)
}

tasks {

    build {
        dependsOn(jar)
        dependsOn(liteJar)
        dependsOn(standaloneJar)
    }

    processResources {
        val gitHash = providers.of(GitHashSource::class) {}
        val gitUnattached = providers.of(GitUnattachedSource::class) {}
        val pluginVersion = version.toString()

        inputs.property("version", pluginVersion)
        inputs.property("githash", gitHash)
        inputs.property("unattached", gitUnattached)

        filteringCharset = "UTF-8"
        filesMatching("paper-plugin.yml") {
            expand(
                mapOf(
                    "version" to pluginVersion,
                    "githash" to gitHash.get(),
                    "unattached" to gitUnattached.get()
                )
            )
        }
    }
}