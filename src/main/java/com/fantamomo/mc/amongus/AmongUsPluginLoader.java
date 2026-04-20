package com.fantamomo.mc.amongus;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
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

    private boolean shouldDownloadDependencies() {
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
        } catch (IOException ignore) {}
        if (manifest == null) {
            return true;
        }
        String value = manifest.getMainAttributes().getValue("Jar-Type");
        return value == null || value.equalsIgnoreCase("lite");
    }

    @Override
    public void classloader(@NonNull PluginClasspathBuilder classpathBuilder) {
        if (!shouldDownloadDependencies()) return;

        classpathBuilder.getContext().getLogger().info("Loading dependencies for Among Us");

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