package com.bililisten.prototype.store;

import com.bililisten.prototype.model.CollectionSession;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

public final class FileBackedCollectionSessionStore implements CollectionSessionStore {
    private final Path file;

    public FileBackedCollectionSessionStore(Path file) {
        this.file = file;
    }

    @Override
    public synchronized Optional<CollectionSession> loadSession(String collectionId) {
        Properties properties = loadProperties();
        String prefix = collectionId + ".";
        String currentIndex = properties.getProperty(prefix + "currentIndex");
        if (currentIndex == null) {
            return Optional.empty();
        }

        long updatedAt = Long.parseLong(properties.getProperty(prefix + "updatedAt", "0"));
        return Optional.of(new CollectionSession(collectionId, Integer.parseInt(currentIndex), updatedAt));
    }

    @Override
    public synchronized void saveSession(CollectionSession session) {
        Properties properties = loadProperties();
        String prefix = session.collectionId() + ".";
        properties.setProperty(prefix + "currentIndex", Integer.toString(session.currentIndex()));
        properties.setProperty(prefix + "updatedAt", Long.toString(session.updatedAt()));
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
            throw new IllegalStateException("Failed to read session store from " + file, exception);
        }
    }

    private void storeProperties(Properties properties) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                properties.store(writer, "Bili Listen collection session store");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write session store to " + file, exception);
        }
    }
}

