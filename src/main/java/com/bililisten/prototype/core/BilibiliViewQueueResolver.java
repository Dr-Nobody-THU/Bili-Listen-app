package com.bililisten.prototype.core;

import com.bililisten.prototype.model.ContentKey;
import com.bililisten.prototype.model.QueueItem;
import com.bililisten.prototype.model.ResolvedQueue;
import com.bililisten.prototype.model.VideoReference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public final class BilibiliViewQueueResolver implements QueueResolver {
    private final ViewDataSource viewDataSource;

    public BilibiliViewQueueResolver(ViewDataSource viewDataSource) {
        this.viewDataSource = viewDataSource;
    }

    @Override
    public ResolvedQueue resolve(VideoReference reference) {
        Map<String, Object> data = viewDataSource.fetchView(reference.bvid());
        return buildQueue(data, reference);
    }

    static ResolvedQueue buildQueue(Map<String, Object> data, VideoReference reference) {
        String currentBvid = Jsons.getString(data, "bvid");
        String currentTitle = Jsons.getString(data, "title");
        String defaultCover = Jsons.optString(data, "pic");

        if (Jsons.hasNonNull(data, "ugc_season")) {
            return buildSeasonQueue(Jsons.getObject(data, "ugc_season"), reference, defaultCover);
        }

        List<Object> pages = Jsons.optList(data, "pages");
        if (pages.size() > 1) {
            List<ItemDraft> drafts = new ArrayList<>();
            for (Object pageValue : pages) {
                Map<String, Object> page = Jsons.asObject(pageValue, "page");
                drafts.add(new ItemDraft(
                        currentBvid,
                        Jsons.getLong(page, "cid"),
                        buildTitle(currentTitle, Jsons.optString(page, "part"), Jsons.getInt(page, "page"), pages.size()),
                        Jsons.getLong(page, "duration") * 1000L,
                        Jsons.getInt(page, "page"),
                        defaultCover
                ));
            }

            int requestedIndex = findRequestedIndex(drafts, reference);
            return new ResolvedQueue(
                    "video:" + currentBvid,
                    currentTitle,
                    materializeQueue(drafts, "video:" + currentBvid, currentTitle),
                    requestedIndex
            );
        }

        QueueItem singleItem = new QueueItem(
                new ContentKey(currentBvid, Jsons.getLong(data, "cid")),
                currentTitle,
                Jsons.getLong(data, "duration") * 1000L,
                1,
                0,
                null,
                null,
                defaultCover
        );

        return new ResolvedQueue(null, null, List.of(singleItem), 0);
    }

    private static ResolvedQueue buildSeasonQueue(Map<String, Object> season, VideoReference reference, String defaultCover) {
        String collectionId = "season:" + Jsons.getLong(season, "id");
        String collectionTitle = Jsons.getString(season, "title");
        List<ItemDraft> drafts = new ArrayList<>();

        for (Object sectionValue : Jsons.getList(season, "sections")) {
            Map<String, Object> section = Jsons.asObject(sectionValue, "section");
            List<Object> episodes = Jsons.optList(section, "episodes");
            for (Object episodeValue : episodes) {
                Map<String, Object> episode = Jsons.asObject(episodeValue, "episode");
                Map<String, Object> arc = Jsons.optObject(episode, "arc");
                String episodeBvid = Jsons.getString(episode, "bvid");
                String episodeTitle = arc == null ? Jsons.getString(episode, "title") : Jsons.getString(arc, "title");
                String coverUrl = arc == null ? defaultCover : firstNonBlank(Jsons.optString(arc, "pic"), defaultCover);
                List<Object> pages = Jsons.optList(episode, "pages");

                if (!pages.isEmpty()) {
                    List<Object> sortedPages = pages.stream()
                            .map(value -> Jsons.asObject(value, "episode page"))
                            .sorted(Comparator.comparingInt(page -> Jsons.getInt(page, "page")))
                            .map(page -> (Object) page)
                            .toList();
                    for (Object pageValue : sortedPages) {
                        Map<String, Object> page = Jsons.asObject(pageValue, "episode page");
                        drafts.add(new ItemDraft(
                                episodeBvid,
                                Jsons.getLong(page, "cid"),
                                buildTitle(episodeTitle, Jsons.optString(page, "part"), Jsons.getInt(page, "page"), sortedPages.size()),
                                Jsons.getLong(page, "duration") * 1000L,
                                Jsons.getInt(page, "page"),
                                coverUrl
                        ));
                    }
                    continue;
                }

                Map<String, Object> singlePage = Jsons.optObject(episode, "page");
                long cid = singlePage == null ? Jsons.getLong(episode, "cid") : Jsons.optLong(singlePage, "cid", Jsons.getLong(episode, "cid"));
                int pageNumber = singlePage == null ? 1 : Jsons.optInt(singlePage, "page", 1);
                long durationSeconds = singlePage == null
                        ? durationFromEpisode(episode, arc)
                        : Jsons.optLong(singlePage, "duration", durationFromEpisode(episode, arc));
                String part = singlePage == null ? null : Jsons.optString(singlePage, "part");

                drafts.add(new ItemDraft(
                        episodeBvid,
                        cid,
                        buildTitle(episodeTitle, part, pageNumber, 1),
                        durationSeconds * 1000L,
                        pageNumber,
                        coverUrl
                ));
            }
        }

        if (drafts.isEmpty()) {
            throw new IllegalArgumentException("ugc_season does not contain any playable episodes.");
        }

        int requestedIndex = findRequestedIndex(drafts, reference);
        return new ResolvedQueue(collectionId, collectionTitle, materializeQueue(drafts, collectionId, collectionTitle), requestedIndex);
    }

    private static List<QueueItem> materializeQueue(List<ItemDraft> drafts, String collectionId, String collectionTitle) {
        List<QueueItem> items = new ArrayList<>();
        for (int index = 0; index < drafts.size(); index++) {
            ItemDraft draft = drafts.get(index);
            items.add(new QueueItem(
                    new ContentKey(draft.bvid(), draft.cid()),
                    draft.title(),
                    draft.durationMs(),
                    draft.pageNumber(),
                    index,
                    collectionId,
                    collectionTitle,
                    draft.coverUrl()
            ));
        }
        return items;
    }

    private static int findRequestedIndex(List<ItemDraft> drafts, VideoReference reference) {
        OptionalInt exactMatch = drafts.stream()
                .filter(item -> item.bvid().equals(reference.bvid()) && item.pageNumber() == reference.page())
                .mapToInt(drafts::indexOf)
                .findFirst();
        if (exactMatch.isPresent()) {
            return exactMatch.getAsInt();
        }

        List<ItemDraft> bvidMatches = drafts.stream()
                .filter(item -> item.bvid().equals(reference.bvid()))
                .toList();
        if (bvidMatches.size() == 1) {
            return drafts.indexOf(bvidMatches.get(0));
        }
        if (!bvidMatches.isEmpty() && reference.page() == 1) {
            return drafts.indexOf(bvidMatches.get(0));
        }

        throw new IllegalArgumentException("Requested video is not present in the resolved queue: " + reference.bvid() + " page " + reference.page());
    }

    private static long durationFromEpisode(Map<String, Object> episode, Map<String, Object> arc) {
        if (arc != null && Jsons.hasNonNull(arc, "duration")) {
            return Jsons.getLong(arc, "duration");
        }
        if (Jsons.hasNonNull(episode, "duration")) {
            return Jsons.getLong(episode, "duration");
        }
        return 0L;
    }

    private static String buildTitle(String baseTitle, String part, int pageNumber, int totalPages) {
        String normalizedPart = normalizeBlank(part);
        if (normalizedPart == null) {
            return baseTitle;
        }
        if (totalPages == 1 && normalizedPart.equals(baseTitle)) {
            return baseTitle;
        }
        return baseTitle + " - P" + pageNumber + " " + normalizedPart;
    }

    private static String firstNonBlank(String first, String second) {
        String normalizedFirst = normalizeBlank(first);
        return normalizedFirst != null ? normalizedFirst : normalizeBlank(second);
    }

    private static String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ItemDraft(String bvid, long cid, String title, long durationMs, int pageNumber, String coverUrl) {
    }
}

