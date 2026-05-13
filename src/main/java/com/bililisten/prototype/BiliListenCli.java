package com.bililisten.prototype;

import com.bililisten.prototype.core.BilibiliLinkParser;
import com.bililisten.prototype.core.BilibiliAudioResolver;
import com.bililisten.prototype.core.BilibiliPlayUrlApiClient;
import com.bililisten.prototype.core.BilibiliViewApiClient;
import com.bililisten.prototype.core.BilibiliViewQueueResolver;
import com.bililisten.prototype.core.FixtureQueueResolver;
import com.bililisten.prototype.core.FixtureWriter;
import com.bililisten.prototype.core.Jsons;
import com.bililisten.prototype.core.ListeningSessionService;
import com.bililisten.prototype.core.ListeningSessionService.AdvanceOutcome;
import com.bililisten.prototype.core.ListeningSessionService.PlannedPlayback;
import com.bililisten.prototype.core.PlaybackPlanner;
import com.bililisten.prototype.core.QueueResolver;
import com.bililisten.prototype.model.AudioStreamSource;
import com.bililisten.prototype.model.CollectionSession;
import com.bililisten.prototype.model.ContentKey;
import com.bililisten.prototype.model.PlaybackProgress;
import com.bililisten.prototype.model.QueueItem;
import com.bililisten.prototype.model.ResolvedAudio;
import com.bililisten.prototype.model.ResolvedQueue;
import com.bililisten.prototype.model.ResumePolicy;
import com.bililisten.prototype.model.StartPlan;
import com.bililisten.prototype.model.VideoReference;
import com.bililisten.prototype.store.CollectionSessionStore;
import com.bililisten.prototype.store.FileBackedCollectionSessionStore;
import com.bililisten.prototype.store.FileBackedProgressStore;
import com.bililisten.prototype.store.InMemoryProgressStore;
import com.bililisten.prototype.store.ProgressStore;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BiliListenCli {
    private static final Path STATE_DIR = Path.of("state");
    private static final Path PROGRESS_FILE = STATE_DIR.resolve("progress.properties");
    private static final Path SESSION_FILE = STATE_DIR.resolve("sessions.properties");

    private BiliListenCli() {
    }

    public static void main(String[] args) {
        if (args.length == 0 || "--help".equals(args[0]) || "help".equals(args[0])) {
            printHelp();
            return;
        }

        switch (args[0]) {
            case "parse" -> runParse(args);
            case "demo" -> runDemo();
            case "plan-fixture" -> runPlanFixture(args);
            case "save-progress-fixture" -> runSaveProgressFixture(args);
            case "plan-live" -> runPlanLive(args);
            case "save-progress-live" -> runSaveProgressLive(args);
            case "export-live-fixture" -> runExportLiveFixture(args);
            case "audio-live" -> runAudioLive(args);
            case "audio-id-live" -> runAudioIdLive(args);
            case "advance-fixture" -> runAdvanceFixture(args);
            case "advance-live" -> runAdvanceLive(args);
            case "debug-view-live" -> runDebugViewLive(args);
            default -> {
                System.err.println("Unknown command: " + args[0]);
                printHelp();
                System.exit(1);
            }
        }
    }

    private static void runParse(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: parse <bv-or-url>");
        }

        BilibiliLinkParser parser = new BilibiliLinkParser();
        VideoReference reference = parser.parse(args[1]);
        System.out.println("bvid=" + reference.bvid());
        System.out.println("page=" + reference.page());
    }

    private static void runDemo() {
        long now = Instant.now().toEpochMilli();

        QueueItem first = new QueueItem(
                new ContentKey("BV1xx411c7mD", 1001L),
                "Part 1",
                180_000L,
                1,
                0,
                "season-demo",
                "Demo Collection",
                null
        );
        QueueItem second = new QueueItem(
                new ContentKey("BV1xx411c7mD", 1002L),
                "Part 2",
                210_000L,
                1,
                1,
                "season-demo",
                "Demo Collection",
                null
        );
        QueueItem third = new QueueItem(
                new ContentKey("BV1xx411c7mD", 1003L),
                "Part 3",
                240_000L,
                1,
                2,
                "season-demo",
                "Demo Collection",
                null
        );

        ResolvedQueue queue = new ResolvedQueue(
                "season-demo",
                "Demo Collection",
                List.of(first, second, third),
                0
        );

        InMemoryProgressStore progressStore = new InMemoryProgressStore();
        progressStore.saveProgress(new PlaybackProgress(first.key(), first.durationMs(), first.durationMs(), true, now));
        progressStore.saveProgress(new PlaybackProgress(second.key(), 48_000L, second.durationMs(), false, now));

        PlaybackPlanner planner = new PlaybackPlanner();
        StartPlan plan = planner.planStart(
                queue,
                Optional.of(new CollectionSession("season-demo", 1, now)),
                progressStore,
                ResumePolicy.PREFER_SAVED_COLLECTION_POSITION
        );

        System.out.println("Resume reason: " + plan.reason());
        System.out.println("Start index: " + plan.startIndex());
        System.out.println("Title: " + plan.item().title());
        System.out.println("Start position: " + plan.startPositionMs() + "ms");
    }

    private static void runPlanFixture(String[] args) {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: plan-fixture <fixture-file> <bv-or-url> [requested|collection]");
        }

        Path fixtureFile = Path.of(args[1]);
        VideoReference reference = new BilibiliLinkParser().parse(args[2]);
        ResumePolicy resumePolicy = args.length >= 4 ? parsePolicy(args[3]) : ResumePolicy.PREFER_SAVED_COLLECTION_POSITION;

        printPlanForResolver(new FixtureQueueResolver(fixtureFile), reference, resumePolicy);
    }

    private static void runSaveProgressFixture(String[] args) {
        if (args.length < 4) {
            throw new IllegalArgumentException("Usage: save-progress-fixture <fixture-file> <bv-or-url> <position-ms> [requested|collection]");
        }

        Path fixtureFile = Path.of(args[1]);
        VideoReference reference = new BilibiliLinkParser().parse(args[2]);
        long positionMs = Long.parseLong(args[3]);
        ResumePolicy resumePolicy = args.length >= 5 ? parsePolicy(args[4]) : ResumePolicy.PREFER_SAVED_COLLECTION_POSITION;

        saveProgressForResolver(new FixtureQueueResolver(fixtureFile), reference, positionMs, resumePolicy);
    }

    private static void runPlanLive(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: plan-live <bv-or-url> [requested|collection]");
        }

        VideoReference reference = new BilibiliLinkParser().parse(args[1]);
        ResumePolicy resumePolicy = args.length >= 3 ? parsePolicy(args[2]) : ResumePolicy.PREFER_SAVED_COLLECTION_POSITION;
        printPlanForResolver(new BilibiliViewQueueResolver(new BilibiliViewApiClient()), reference, resumePolicy);
    }

    private static void runSaveProgressLive(String[] args) {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: save-progress-live <bv-or-url> <position-ms> [requested|collection]");
        }

        VideoReference reference = new BilibiliLinkParser().parse(args[1]);
        long positionMs = Long.parseLong(args[2]);
        ResumePolicy resumePolicy = args.length >= 4 ? parsePolicy(args[3]) : ResumePolicy.PREFER_SAVED_COLLECTION_POSITION;
        saveProgressForResolver(new BilibiliViewQueueResolver(new BilibiliViewApiClient()), reference, positionMs, resumePolicy);
    }

    private static void runExportLiveFixture(String[] args) {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: export-live-fixture <bv-or-url> <fixture-file>");
        }

        VideoReference reference = new BilibiliLinkParser().parse(args[1]);
        Path outputFile = Path.of(args[2]);
        QueueResolver resolver = new BilibiliViewQueueResolver(new BilibiliViewApiClient());
        ResolvedQueue queue = resolver.resolve(reference);
        new FixtureWriter().write(queue, outputFile);

        System.out.println("Exported fixture: " + outputFile);
        System.out.println("Collection: " + (queue.collectionTitle() == null ? "<single video>" : queue.collectionTitle()));
        System.out.println("Items: " + queue.items().size());
        System.out.println("Requested index: " + queue.requestedIndex());
    }

    private static void runAudioLive(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: audio-live <bv-or-url> [requested|collection]");
        }

        VideoReference reference = new BilibiliLinkParser().parse(args[1]);
        ResumePolicy resumePolicy = args.length >= 3 ? parsePolicy(args[2]) : ResumePolicy.PREFER_SAVED_COLLECTION_POSITION;

        QueueResolver queueResolver = new BilibiliViewQueueResolver(new BilibiliViewApiClient());
        PlannedPlayback planResult = resolvePlannedPlayback(queueResolver, reference, resumePolicy);
        ResolvedAudio resolvedAudio = new BilibiliAudioResolver(new BilibiliPlayUrlApiClient()).resolve(planResult.plan().item());

        printPlan(planResult.queue(), planResult.plan());
        printAudio(resolvedAudio);
    }

    private static void runAudioIdLive(String[] args) {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: audio-id-live <bvid> <cid>");
        }

        ContentKey key = new ContentKey(args[1], Long.parseLong(args[2]));
        ResolvedAudio resolvedAudio = new BilibiliAudioResolver(new BilibiliPlayUrlApiClient())
                .resolve(key, key.asStoreKey(), 0L);
        printAudio(resolvedAudio);
    }

    private static void runAdvanceFixture(String[] args) {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: advance-fixture <fixture-file> <bv-or-url> [requested|collection]");
        }

        Path fixtureFile = Path.of(args[1]);
        VideoReference reference = new BilibiliLinkParser().parse(args[2]);
        ResumePolicy resumePolicy = args.length >= 4 ? parsePolicy(args[3]) : ResumePolicy.PREFER_SAVED_COLLECTION_POSITION;
        advanceForResolver(new FixtureQueueResolver(fixtureFile), reference, resumePolicy, false);
    }

    private static void runAdvanceLive(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: advance-live <bv-or-url> [requested|collection]");
        }

        VideoReference reference = new BilibiliLinkParser().parse(args[1]);
        ResumePolicy resumePolicy = args.length >= 3 ? parsePolicy(args[2]) : ResumePolicy.PREFER_SAVED_COLLECTION_POSITION;
        advanceForResolver(new BilibiliViewQueueResolver(new BilibiliViewApiClient()), reference, resumePolicy, true);
    }

    private static void runDebugViewLive(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: debug-view-live <bv-or-url>");
        }

        VideoReference reference = new BilibiliLinkParser().parse(args[1]);
        Map<String, Object> data = new BilibiliViewApiClient().fetchView(reference.bvid());
        printViewDebug(data, reference.bvid());
    }

    private static void printPlanForResolver(QueueResolver resolver, VideoReference reference, ResumePolicy resumePolicy) {
        PlannedPlayback result = resolvePlannedPlayback(resolver, reference, resumePolicy);
        printPlan(result.queue(), result.plan());
    }

    private static void saveProgressForResolver(
            QueueResolver resolver,
            VideoReference reference,
            long positionMs,
            ResumePolicy resumePolicy
    ) {
        long now = Instant.now().toEpochMilli();
        ProgressStore progressStore = new FileBackedProgressStore(PROGRESS_FILE);
        CollectionSessionStore sessionStore = new FileBackedCollectionSessionStore(SESSION_FILE);
        ListeningSessionService sessionService = new ListeningSessionService();
        PlannedPlayback playback = sessionService.plan(resolver, reference, resumePolicy, progressStore, sessionStore);
        PlaybackProgress progress = sessionService.saveCurrentProgress(playback, positionMs, now, progressStore, sessionStore);

        System.out.println("Saved progress for: " + playback.plan().item().title());
        System.out.println("Position: " + progress.positionMs() + "ms");
        System.out.println("Finished: " + progress.finished());
    }

    private static ResumePolicy parsePolicy(String raw) {
        return switch (raw.toLowerCase()) {
            case "requested" -> ResumePolicy.PREFER_REQUESTED_ITEM;
            case "collection" -> ResumePolicy.PREFER_SAVED_COLLECTION_POSITION;
            default -> throw new IllegalArgumentException("Unknown policy: " + raw + ". Use requested or collection.");
        };
    }

    private static void printPlan(ResolvedQueue queue, StartPlan plan) {
        System.out.println("Collection: " + (queue.collectionTitle() == null ? "<single video>" : queue.collectionTitle()));
        System.out.println("Items: " + queue.items().size());
        System.out.println("Requested index: " + queue.requestedIndex());
        System.out.println("Resume reason: " + plan.reason());
        System.out.println("Start index: " + plan.startIndex());
        System.out.println("Title: " + plan.item().title());
        System.out.println("BVID: " + plan.item().key().bvid());
        System.out.println("CID: " + plan.item().key().cid());
        System.out.println("Page: " + plan.item().pageNumber());
        System.out.println("Start position: " + plan.startPositionMs() + "ms");
    }

    private static void printAudio(ResolvedAudio resolvedAudio) {
        AudioStreamSource selected = resolvedAudio.selected();
        System.out.println("Audio title: " + resolvedAudio.title());
        System.out.println("Audio kind: " + selected.kind());
        System.out.println("Audio id: " + selected.id());
        System.out.println("Audio codec: " + valueOrPlaceholder(selected.codec()));
        System.out.println("Audio mime: " + valueOrPlaceholder(selected.mimeType()));
        System.out.println("Audio bandwidth: " + selected.bandwidth());
        System.out.println("Audio url: " + selected.url());
        System.out.println("Backup urls: " + selected.backupUrls().size());
        System.out.println("Candidates: " + resolvedAudio.candidates().size());
    }

    private static String valueOrPlaceholder(String value) {
        return value == null || value.isBlank() ? "<none>" : value;
    }

    private static void advanceForResolver(
            QueueResolver resolver,
            VideoReference reference,
            ResumePolicy resumePolicy,
            boolean resolveAudio
    ) {
        long now = Instant.now().toEpochMilli();
        ProgressStore progressStore = new FileBackedProgressStore(PROGRESS_FILE);
        CollectionSessionStore sessionStore = new FileBackedCollectionSessionStore(SESSION_FILE);
        ListeningSessionService sessionService = new ListeningSessionService();
        PlannedPlayback playback = sessionService.plan(resolver, reference, resumePolicy, progressStore, sessionStore);
        AdvanceOutcome outcome = sessionService.advance(playback, now, progressStore, sessionStore);

        System.out.println("Completed: " + outcome.completedItem().title());
        if (outcome.reachedEnd()) {
            System.out.println("Queue status: end reached");
            return;
        }

        PlannedPlayback nextPlayback = outcome.nextPlayback();
        printPlan(nextPlayback.queue(), nextPlayback.plan());
        if (resolveAudio) {
            ResolvedAudio resolvedAudio = new BilibiliAudioResolver(new BilibiliPlayUrlApiClient()).resolve(nextPlayback.plan().item());
            printAudio(resolvedAudio);
        }
    }

    private static PlannedPlayback resolvePlannedPlayback(QueueResolver resolver, VideoReference reference, ResumePolicy resumePolicy) {
        ProgressStore progressStore = new FileBackedProgressStore(PROGRESS_FILE);
        CollectionSessionStore sessionStore = new FileBackedCollectionSessionStore(SESSION_FILE);
        return new ListeningSessionService().plan(resolver, reference, resumePolicy, progressStore, sessionStore);
    }

    private static void printViewDebug(Map<String, Object> data, String requestedBvid) {
        System.out.println("Top-level title: " + valueOrPlaceholder(Jsons.optString(data, "title")));
        System.out.println("Top-level cover: " + valueOrPlaceholder(Jsons.optString(data, "pic")));
        Map<String, Object> owner = Jsons.optObject(data, "owner");
        System.out.println("Top-level owner.name: " + valueOrPlaceholder(owner == null ? null : Jsons.optString(owner, "name")));
        System.out.println("Top-level owner.face: " + valueOrPlaceholder(owner == null ? null : Jsons.optString(owner, "face")));

        Map<String, Object> season = Jsons.optObject(data, "ugc_season");
        if (season == null) {
            System.out.println("ugc_season: <none>");
            return;
        }

        System.out.println("ugc_season.title: " + valueOrPlaceholder(Jsons.optString(season, "title")));
        System.out.println("ugc_season.id: " + Jsons.optLong(season, "id", -1L));
        boolean foundEpisode = false;
        for (Object sectionValue : Jsons.optList(season, "sections")) {
            Map<String, Object> section = Jsons.asObject(sectionValue, "section");
            for (Object episodeValue : Jsons.optList(section, "episodes")) {
                Map<String, Object> episode = Jsons.asObject(episodeValue, "episode");
                String episodeBvid = Jsons.optString(episode, "bvid");
                if (!requestedBvid.equals(episodeBvid)) {
                    continue;
                }

                foundEpisode = true;
                Map<String, Object> arc = Jsons.optObject(episode, "arc");
                Map<String, Object> episodeOwner = Jsons.optObject(episode, "owner");
                Map<String, Object> episodeAuthor = Jsons.optObject(episode, "author");
                Map<String, Object> arcOwner = arc == null ? null : Jsons.optObject(arc, "owner");
                Map<String, Object> arcAuthor = arc == null ? null : Jsons.optObject(arc, "author");

                System.out.println("Matched episode.bvid: " + valueOrPlaceholder(episodeBvid));
                System.out.println("episode.title: " + valueOrPlaceholder(Jsons.optString(episode, "title")));
                System.out.println("episode.cover: " + valueOrPlaceholder(Jsons.optString(episode, "cover")));
                System.out.println("episode.arc.title: " + valueOrPlaceholder(arc == null ? null : Jsons.optString(arc, "title")));
                System.out.println("episode.arc.pic: " + valueOrPlaceholder(arc == null ? null : Jsons.optString(arc, "pic")));
                System.out.println("episode.owner.name: " + valueOrPlaceholder(nameOf(episodeOwner)));
                System.out.println("episode.owner.face: " + valueOrPlaceholder(faceOf(episodeOwner)));
                System.out.println("episode.author.name: " + valueOrPlaceholder(nameOf(episodeAuthor)));
                System.out.println("episode.author.face: " + valueOrPlaceholder(faceOf(episodeAuthor)));
                System.out.println("episode.arc.owner.name: " + valueOrPlaceholder(nameOf(arcOwner)));
                System.out.println("episode.arc.owner.face: " + valueOrPlaceholder(faceOf(arcOwner)));
                System.out.println("episode.arc.author.name: " + valueOrPlaceholder(nameOf(arcAuthor)));
                System.out.println("episode.arc.author.face: " + valueOrPlaceholder(faceOf(arcAuthor)));
                return;
            }
        }

        if (!foundEpisode) {
            System.out.println("Matched episode: <not found inside ugc_season.sections>");
        }
    }

    private static String nameOf(Map<String, Object> object) {
        if (object == null) {
            return null;
        }
        String name = Jsons.optString(object, "name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        return Jsons.optString(object, "uname");
    }

    private static String faceOf(Map<String, Object> object) {
        if (object == null) {
            return null;
        }
        String face = Jsons.optString(object, "face");
        if (face != null && !face.isBlank()) {
            return face;
        }
        String avatar = Jsons.optString(object, "avatar");
        if (avatar != null && !avatar.isBlank()) {
            return avatar;
        }
        return Jsons.optString(object, "pic");
    }

    private static void printHelp() {
        System.out.println("Commands:");
        System.out.println("  parse <bv-or-url>  Parse a Bilibili BV link or raw BV id");
        System.out.println("  demo               Show queue resume behavior");
        System.out.println("  plan-fixture <fixture-file> <bv-or-url> [requested|collection]");
        System.out.println("                     Resolve a queue from a fixture and print the start plan");
        System.out.println("  save-progress-fixture <fixture-file> <bv-or-url> <position-ms>");
        System.out.println("                     Save progress for the current planned item into the local state store");
        System.out.println("  plan-live <bv-or-url> [requested|collection]");
        System.out.println("                     Resolve a queue from the live Bilibili view API and print the start plan");
        System.out.println("  save-progress-live <bv-or-url> <position-ms>");
        System.out.println("                     Save progress for the current planned live item into the local state store");
        System.out.println("  export-live-fixture <bv-or-url> <fixture-file>");
        System.out.println("                     Fetch a live queue and write it as a local fixture file");
        System.out.println("  audio-live <bv-or-url> [requested|collection]");
        System.out.println("                     Resolve the current item and print its selected audio stream URL");
        System.out.println("  audio-id-live <bvid> <cid>");
        System.out.println("                     Fetch audio stream URLs directly from a bvid + cid pair");
        System.out.println("  advance-fixture <fixture-file> <bv-or-url> [requested|collection]");
        System.out.println("                     Mark the current planned item finished and move to the next fixture item");
        System.out.println("  advance-live <bv-or-url> [requested|collection]");
        System.out.println("                     Mark the current planned live item finished and print the next audio stream");
        System.out.println("  debug-view-live <bv-or-url>");
        System.out.println("                     Print raw cover and author fields from the live Bilibili view API");
    }
}
