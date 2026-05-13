package com.bililisten.prototype.core;

import com.bililisten.prototype.model.AudioStreamKind;
import com.bililisten.prototype.model.AudioStreamSource;
import com.bililisten.prototype.model.ContentKey;
import com.bililisten.prototype.model.QueueItem;
import com.bililisten.prototype.model.ResolvedAudio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class BilibiliAudioResolver {
    private final PlayUrlDataSource playUrlDataSource;

    public BilibiliAudioResolver(PlayUrlDataSource playUrlDataSource) {
        this.playUrlDataSource = playUrlDataSource;
    }

    public ResolvedAudio resolve(QueueItem item) {
        return resolve(item.key(), item.title(), item.durationMs());
    }

    public ResolvedAudio resolve(ContentKey key, String title, long durationMs) {
        Map<String, Object> data = playUrlDataSource.fetchPlayUrl(key.bvid(), key.cid());
        return buildResolvedAudio(data, key, title, durationMs);
    }

    public static ResolvedAudio buildResolvedAudio(Map<String, Object> data, ContentKey key, String title, long durationMs) {
        List<AudioStreamSource> candidates = new ArrayList<>();

        Map<String, Object> dash = Jsons.optObject(data, "dash");
        if (dash != null) {
            candidates.addAll(parseRegularAudio(dash));
            candidates.addAll(parseFlacAudio(dash));
            candidates.addAll(parseDolbyAudio(dash));
        }

        if (candidates.isEmpty()) {
            candidates.addAll(parseDurlFallback(data));
        }

        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Playurl response does not contain any playable audio source.");
        }

        AudioStreamSource selected = chooseDefaultCandidate(candidates);
        long resolvedDurationMs = durationMs > 0L ? durationMs : Jsons.optLong(data, "timelength", 0L);
        return new ResolvedAudio(key, title, resolvedDurationMs, selected, candidates);
    }

    private static List<AudioStreamSource> parseRegularAudio(Map<String, Object> dash) {
        List<Object> audioList = Jsons.optList(dash, "audio");
        List<AudioStreamSource> result = new ArrayList<>();
        for (Object value : audioList) {
            result.add(parseDashSource(Jsons.asObject(value, "dash audio"), AudioStreamKind.DASH_AUDIO));
        }
        return result;
    }

    private static List<AudioStreamSource> parseFlacAudio(Map<String, Object> dash) {
        Map<String, Object> flac = Jsons.optObject(dash, "flac");
        if (flac == null) {
            return List.of();
        }

        Map<String, Object> audio = Jsons.optObject(flac, "audio");
        if (audio == null) {
            return List.of();
        }

        return List.of(parseDashSource(audio, AudioStreamKind.DASH_FLAC));
    }

    private static List<AudioStreamSource> parseDolbyAudio(Map<String, Object> dash) {
        Map<String, Object> dolby = Jsons.optObject(dash, "dolby");
        if (dolby == null) {
            return List.of();
        }

        Object rawAudio = dolby.get("audio");
        if (rawAudio == null) {
            return List.of();
        }

        if (rawAudio instanceof Map<?, ?> map) {
            return List.of(parseDashSource(Jsons.asObject(map, "dolby audio"), AudioStreamKind.DASH_DOLBY));
        }

        List<Object> audioList = Jsons.asList(rawAudio, "dolby audio");
        List<AudioStreamSource> result = new ArrayList<>();
        for (Object value : audioList) {
            result.add(parseDashSource(Jsons.asObject(value, "dolby audio"), AudioStreamKind.DASH_DOLBY));
        }
        return result;
    }

    private static List<AudioStreamSource> parseDurlFallback(Map<String, Object> data) {
        List<Object> durl = Jsons.optList(data, "durl");
        List<AudioStreamSource> result = new ArrayList<>();
        for (int index = 0; index < durl.size(); index++) {
            Map<String, Object> entry = Jsons.asObject(durl.get(index), "durl");
            String url = firstNonBlank(Jsons.optString(entry, "url"), Jsons.optString(entry, "backup_url"));
            if (url == null) {
                continue;
            }
            result.add(new AudioStreamSource(
                    index,
                    url,
                    List.of(),
                    0L,
                    null,
                    Jsons.optString(data, "format"),
                    AudioStreamKind.MUXED_FALLBACK
            ));
        }
        return result;
    }

    private static AudioStreamSource parseDashSource(Map<String, Object> source, AudioStreamKind kind) {
        String url = firstNonBlank(Jsons.optString(source, "baseUrl"), Jsons.optString(source, "base_url"));
        if (url == null) {
            throw new IllegalArgumentException("Dash source is missing baseUrl/base_url.");
        }

        return new AudioStreamSource(
                Jsons.optInt(source, "id", 0),
                url,
                parseBackupUrls(source),
                Jsons.optLong(source, "bandwidth", 0L),
                Jsons.optString(source, "codecs"),
                firstNonBlank(Jsons.optString(source, "mimeType"), Jsons.optString(source, "mime_type")),
                kind
        );
    }

    private static List<String> parseBackupUrls(Map<String, Object> source) {
        Object rawBackupUrls = source.get("backupUrl");
        if (rawBackupUrls == null) {
            rawBackupUrls = source.get("backup_url");
        }
        if (rawBackupUrls == null) {
            return List.of();
        }

        List<String> backupUrls = new ArrayList<>();
        for (Object value : Jsons.asList(rawBackupUrls, "backup urls")) {
            if (value instanceof String string && !string.isBlank()) {
                backupUrls.add(string);
            }
        }
        return backupUrls;
    }

    private static AudioStreamSource chooseDefaultCandidate(List<AudioStreamSource> candidates) {
        Comparator<AudioStreamSource> comparator = Comparator
                .comparingInt((AudioStreamSource source) -> preferenceRank(source.kind()))
                .thenComparingLong(AudioStreamSource::bandwidth);
        return candidates.stream()
                .max(comparator)
                .orElseThrow();
    }

    private static int preferenceRank(AudioStreamKind kind) {
        return switch (kind) {
            case DASH_AUDIO -> 3;
            case DASH_FLAC -> 2;
            case DASH_DOLBY -> 1;
            case MUXED_FALLBACK -> 0;
        };
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
