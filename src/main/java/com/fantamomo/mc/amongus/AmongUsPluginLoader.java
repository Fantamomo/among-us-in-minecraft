package com.fantamomo.mc.amongus;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.jar.Manifest;

/**
 * PluginLoader implementation used by the Among Us plugin to dynamically resolve and load
 * external dependencies at runtime.
 * <p>
 * This class is required to be written in Java because the Kotlin runtime is not yet available
 * on the classpath when the loader itself is initialized.
 * <p>
 * Instead of shading all dependencies into a single fat jar, this loader uses Paper's
 * {@link MavenLibraryResolver} to fetch and attach only the required libraries during plugin
 * startup. This helps keep the final plugin jar lightweight and modular.
 * <p>
 * Dependencies are resolved from Maven Central and additional configured repositories,
 * and then added to the plugin classpath via the {@link PluginClasspathBuilder}.
 *
 * @author Fantamomo
 * @version 2.0
 */
@SuppressWarnings("UnstableApiUsage")
public class AmongUsPluginLoader implements PluginLoader {

    private String getJarType() {
        Manifest manifest = null;
        try {
            URL url = this.getClass().getClassLoader().getResource("META-INF/MANIFEST.MF");
            if (url != null) {
                URLConnection connection = url.openConnection();
                connection.setUseCaches(false);
                try (InputStream inputStream = connection.getInputStream()) {
                    manifest = new Manifest(inputStream);
                }
            }
        } catch (IOException ignore) {
        }
        if (manifest == null) {
            return "unknown";
        }
        String value = manifest.getMainAttributes().getValue("Jar-Type");
        return value == null ? "unknown" : value.toLowerCase();
    }

    @Override
    public void classloader(@NonNull PluginClasspathBuilder classpathBuilder) {
        String jarType = getJarType();
        ComponentLogger logger = classpathBuilder.getContext().getLogger();
        switch (jarType) {
            case "standalone":
                return;
            case "thin":
                logger.warn("Detected thin jar, the plugin will not work");
                logger.warn("Please switch to the lite or standalone jar types");
                logger.warn("The plugin will throw an exception");
                return;
            case "unknown":
                logger.warn("Could not determine jar type, proceeding with dependency resolution");
                break;
            case "lite":
                logger.info("Detected lite jar, proceeding with dependency resolution");
                break;
            default:
                logger.warn("Unknown jar type: {}, proceeding with dependency resolution", jarType);
                break;
        }

        MavenLibraryResolver centralResolver = new MavenLibraryResolver();

        centralResolver.addRepository(
                new RemoteRepository.Builder(
                        "central",
                        "default",
                        MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR
                ).build()
        );

        centralResolver.addDependency(new Dependency(new DefaultArtifact("org.jetbrains.kotlin:kotlin-stdlib:2.3.20-Beta2"), null));
        centralResolver.addDependency(new Dependency(new DefaultArtifact("ai.koog:koog-agents-jvm:0.7.1"), null));
        centralResolver.addDependency(new Dependency(new DefaultArtifact("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0"), null));
        centralResolver.addDependency(new Dependency(new DefaultArtifact("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2"), null));

        MavenLibraryResolver inventiveResolver = new MavenLibraryResolver();

        inventiveResolver.addRepository(
                new RemoteRepository.Builder(
                        "inventive-repo",
                        "default",
                        "https://repo.inventivetalent.org/repository/public/"
                ).build()
        );

        inventiveResolver.addDependency(
                new Dependency(
                        new DefaultArtifact("org.mineskin:java-client:3.2.1-SNAPSHOT"),
                        null
                )
        );

        classpathBuilder.addLibrary(centralResolver);
        classpathBuilder.addLibrary(inventiveResolver);
    }
}