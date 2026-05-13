package com.bililisten.prototype.core;

import com.bililisten.prototype.model.ContentKey;
import com.bililisten.prototype.model.QueueItem;
import com.bililisten.prototype.model.ResolvedQueue;
import com.bililisten.prototype.model.VideoReference;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

public final class FixtureQueueResolver implements QueueResolver {
    private final Path fixtureFile;

    public FixtureQueueResolver(Path fixtureFile) {
        this.fixtureFile = fixtureFile;
    }

    @Override
    public ResolvedQueue resolve(VideoReference reference) {
        Properties properties = loadProperties();
        List<FixtureItem> sortedItems = loadItems(properties).stream()
                .sorted(Comparator.comparingInt(FixtureItem::index))
                .toList();
        int requestedIndex = findRequestedIndex(sortedItems, reference);

        String collectionId = normalizeBlank(properties.getProperty("collection.id"));
        String collectionTitle = normalizeBlank(properties.getProperty("collection.title"));

        if ((collectionId == null) != (collectionTitle == null)) {
            throw new IllegalArgumentException("Fixture must define both collection.id and collection.title, or neither.");
        }

        List<QueueItem> queueItems = new ArrayList<>();
        for (int position = 0; position < sortedItems.size(); position++) {
            FixtureItem item = sortedItems.get(position);
            queueItems.add(new QueueItem(
                        new ContentKey(item.bvid(), item.cid()),
                        item.title(),
                        item.durationMs(),
                        item.page(),
                        position,
                        collectionId,
                        collectionTitle,
                        normalizeBlank(item.coverUrl())
            ));
        }

        return new ResolvedQueue(collectionId, collectionTitle, queueItems, requestedIndex);
    }

    private Properties loadProperties() {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(fixtureFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read fixture: " + fixtureFile, exception);
        }
    }

    private List<FixtureItem> loadItems(Properties properties) {
        Set<Integer> indexes = new TreeSet<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("item.")) {
                continue;
            }

            String[] parts = key.split("\\.");
            if (parts.length >= 3) {
                indexes.add(Integer.parseInt(parts[1]));
            }
        }

        if (indexes.isEmpty()) {
            throw new IllegalArgumentException("Fixture does not contain any item.<index> entries: " + fixtureFile);
        }

        List<FixtureItem> items = new ArrayList<>();
        for (int index : indexes) {
            String prefix = "item." + index + ".";
            String bvid = requireProperty(properties, prefix + "bvid");
            long cid = Long.parseLong(requireProperty(properties, prefix + "cid"));
            String title = requireProperty(properties, prefix + "title");
            long durationMs = Long.parseLong(requireProperty(properties, prefix + "durationMs"));
            int page = Integer.parseInt(properties.getProperty(prefix + "page", "1"));
            String coverUrl = properties.getProperty(prefix + "coverUrl");
            items.add(new FixtureItem(index, bvid, cid, title, durationMs, page, coverUrl));
        }
        return items;
    }

    private int findRequestedIndex(List<FixtureItem> items, VideoReference reference) {
        OptionalInt exactMatch = items.stream()
                .filter(item -> item.bvid().equals(reference.bvid()) && item.page() == reference.page())
                .mapToInt(items::indexOf)
                .findFirst();
        if (exactMatch.isPresent()) {
            return exactMatch.getAsInt();
        }

        List<FixtureItem> bvidMatches = items.stream()
                .filter(item -> item.bvid().equals(reference.bvid()))
                .sorted(Comparator.comparingInt(FixtureItem::index))
                .toList();

        if (bvidMatches.size() == 1) {
            return items.indexOf(bvidMatches.get(0));
        }

        if (!bvidMatches.isEmpty() && reference.page() == 1) {
            return items.indexOf(bvidMatches.get(0));
        }

        throw new IllegalArgumentException(
                "Could not find requested item in fixture " + fixtureFile + " for " + reference.bvid() + " page " + reference.page()
        );
    }

    private String requireProperty(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing fixture property: " + key);
        }
        return value.trim();
    }

    private String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record FixtureItem(int index, String bvid, long cid, String title, long durationMs, int page, String coverUrl) {
    }
}
