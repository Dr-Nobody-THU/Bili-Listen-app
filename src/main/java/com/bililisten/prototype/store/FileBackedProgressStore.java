package com.bililisten.prototype.store;

import com.bililisten.prototype.model.ContentKey;
import com.bililisten.prototype.model.PlaybackProgress;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

public final class FileBackedProgressStore implements ProgressStore {
    private final Path file;

    public FileBackedProgressStore(Path file) {
        this.file = file;
    }

    @Override
    public synchronized Optional<PlaybackProgress> loadProgress(ContentKey key) {
        Properties properties = loadProperties();
        String prefix = key.asStoreKey() + ".";
        String positionMs = properties.getProperty(prefix + "positionMs");
        if (positionMs == null) {
            return Optional.empty();
        }

        long durationMs = Long.parseLong(properties.getProperty(prefix + "durationMs", "0"));
        boolean finished = Boolean.parseBoolean(properties.getProperty(prefix + "finished", "false"));
        long updatedAt = Long.parseLong(properties.getProperty(prefix + "updatedAt", "0"));
        return Optional.of(new PlaybackProgress(key, Long.parseLong(positionMs), durationMs, finished, updatedAt));
    }

    @Override
    public synchronized void saveProgress(PlaybackProgress progress) {
        Properties properties = loadProperties();
        String prefix = progress.key().asStoreKey() + ".";
        properties.setProperty(prefix + "positionMs", Long.toString(progress.positionMs()));
        properties.setProperty(prefix + "durationMs", Long.toString(progress.durationMs()));
        properties.setProperty(prefix + "finished", Boolean.toString(progress.finished()));
        properties.setProperty(prefix + "updatedAt", Long.toString(progress.updatedAt()));
        storeProperties(properties);
    }

    private Properties loadProperties() {
        Properties properties = new Properties();
        if (!Files.exists(file)) {
            return properties;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read progress store from " + file, exception);
        }
    }

    private void storeProperties(Properties properties) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                properties.store(writer, "Bili Listen progress store");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write progress store to " + file, exception);
        }
    }
}

