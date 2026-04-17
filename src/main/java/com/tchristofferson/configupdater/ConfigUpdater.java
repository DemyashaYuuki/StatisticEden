package com.tchristofferson.configupdater;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Lightweight embedded replacement for the external Config-Updater dependency.
 * <p>
 * It keeps existing user values and only injects missing keys from the bundled resource.
 * This implementation intentionally avoids depending on unavailable snapshot artifacts,
 * making CI and GitHub builds reproducible.
 */
public final class ConfigUpdater {

    private ConfigUpdater() {
    }

    public static void update(@NotNull Plugin plugin, @NotNull String resourceName, @NotNull File toUpdate) throws IOException {
        update(plugin, resourceName, toUpdate, Collections.emptyList());
    }

    public static void update(@NotNull Plugin plugin,
                              @NotNull String resourceName,
                              @NotNull File toUpdate,
                              @NotNull List<String> ignoredSections) throws IOException {
        if (!toUpdate.exists()) {
            throw new IOException("Target file does not exist: " + toUpdate.getAbsolutePath());
        }

        try (InputStream inputStream = plugin.getResource(resourceName)) {
            if (inputStream == null) {
                throw new IOException("Bundled resource not found: " + resourceName);
            }

            YamlConfiguration bundledConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8)
            );
            YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(toUpdate);

            mergeMissingValues(bundledConfig, currentConfig, ignoredSections);
            currentConfig.save(toUpdate);
        }
    }

    private static void mergeMissingValues(@NotNull YamlConfiguration source,
                                           @NotNull YamlConfiguration target,
                                           @NotNull List<String> ignoredSections) {
        Set<String> keys = source.getKeys(true);
        for (String path : keys) {
            if (isIgnored(path, ignoredSections) || target.contains(path)) {
                continue;
            }

            Object value = source.get(path);
            if (value instanceof ConfigurationSection) {
                continue;
            }
            target.set(path, value);
        }
    }

    private static boolean isIgnored(@NotNull String path, @NotNull List<String> ignoredSections) {
        for (String ignored : ignoredSections) {
            if (ignored == null || ignored.isBlank()) {
                continue;
            }
            if (path.equals(ignored) || path.startsWith(ignored + ".")) {
                return true;
            }
        }
        return false;
    }
}
