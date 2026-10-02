package com.megacitycraft.light;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** UUID-based settings, saved on each change using an atomic replacement. */
final class PlayerSettings {
    private final Path file;
    private final Set<UUID> enabled = new HashSet<>();

    PlayerSettings(Path file) throws IOException, InvalidConfigurationException {
        this.file = file;
        if (Files.exists(file)) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file.toFile());
            for (String value : yaml.getStringList("enabled-players")) {
                try {
                    enabled.add(UUID.fromString(value));
                } catch (IllegalArgumentException error) {
                    throw new InvalidConfigurationException("Invalid player UUID in " + file, error);
                }
            }
        }
    }

    boolean isEnabled(UUID player) {
        return enabled.contains(player);
    }

    void setEnabled(UUID player, boolean value) throws IOException {
        boolean previous = enabled.contains(player);
        if (previous == value) return;
        if (value) enabled.add(player);
        else enabled.remove(player);
        try {
            save();
        } catch (IOException error) {
            if (previous) enabled.add(player);
            else enabled.remove(player);
            throw error;
        }
    }

    private void save() throws IOException {
        Files.createDirectories(file.getParent());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled-players", enabled.stream().map(UUID::toString).sorted().toList());
        Path temporary = Files.createTempFile(file.getParent(), "players-", ".tmp");
        try {
            Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
