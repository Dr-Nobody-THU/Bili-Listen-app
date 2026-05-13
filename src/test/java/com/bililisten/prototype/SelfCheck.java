package com.bililisten.prototype;

import com.bililisten.prototype.core.BilibiliLinkParser;
import com.bililisten.prototype.core.BilibiliAudioResolver;
import com.bililisten.prototype.core.BilibiliViewQueueResolver;
import com.bililisten.prototype.core.FixtureQueueResolver;
import com.bililisten.prototype.core.FixtureWriter;
import com.bililisten.prototype.core.Jsons;
import com.bililisten.prototype.core.ListeningSessionService;
import com.bililisten.prototype.core.ListeningSessionService.AdvanceOutcome;
import com.bililisten.prototype.core.ListeningSessionService.PlannedPlayback;
import com.bililisten.prototype.core.PlaybackPlanner;
import com.bililisten.prototype.core.QueueResolver;
import com.bililisten.prototype.core.SimpleJsonParser;
import com.bililisten.prototype.model.AudioStreamKind;
import com.bililisten.prototype.model.CollectionSession;
import com.bililisten.prototype.model.ContentKey;
import com.bililisten.prototype.model.PlaybackProgress;
import com.bililisten.prototype.model.QueueItem;
import com.bililisten.prototype.model.ResolvedAudio;
import com.bililisten.prototype.model.ResolvedQueue;
import com.bililisten.prototype.model.ResumePolicy;
import com.bililisten.prototype.model.StartPlan;
import com.bililisten.prototype.model.VideoReference;
import com.bililisten.prototype.store.FileBackedCollectionSessionStore;
import com.bililisten.prototype.store.FileBackedProgressStore;
import com.bililisten.prototype.store.InMemoryCollectionSessionStore;
import com.bililisten.prototype.store.InMemoryProgressStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class SelfCheck {
    private SelfCheck() {
    }

    public static void main(String[] args) throws Exception {
        parsesRawBvid();
        parsesFullVideoLinkWithPage();
        prefersRequestedItemWhenConfigured();
        resumesSavedCollectionPosition();
        skipsFinishedItemsInsideCollection();
        fixtureResolverMatchesExactPage();
        fixtureResolverFallsBackToSingleBvidMatch();
        liveResolverBuildsSeasonQueue();
        liveResolverBuildsMultiPageQueue();
        fixtureWriterRoundTrip();
        audioResolverSelectsRegularDashTrack();
        audioResolverFallsBackToDurl();
        sessionServiceSavesCurrentPlannedItem();
        sessionServiceAdvancesToNextItem();
        fileBackedStoresRoundTrip();

        System.out.println("All self-checks passed.");
    }

    private static void parsesRawBvid() {
        VideoReference reference = new BilibiliLinkParser().parse("BV1xx411c7mD");
        assertEquals("BV1xx411c7mD", reference.bvid(), "raw BV parse");
        assertEquals(1, reference.page(), "raw BV page default");
    }

    private static void parsesFullVideoLinkWithPage() {
        String input = "https://www.bilibili.com/video/BV1xx411c7mD?p=3&spm_id_from=333.1007.top_right_bar_window_default_collection.content.click";
        VideoReference reference = new BilibiliLinkParser().parse(input);
        assertEquals("BV1xx411c7mD", reference.bvid(), "link BV parse");
        assertEquals(3, reference.page(), "link page parse");
    }

    private static void prefersRequestedItemWhenConfigured() {
        ResolvedQueue queue = demoQueue(0);
        InMemoryProgressStore progressStore = new InMemoryProgressStore();
        progressStore.saveProgress(new PlaybackProgress(queue.items().get(0).key(), 25_000L, 120_000L, false, 1L));

        PlaybackPlanner planner = new PlaybackPlanner();
        StartPlan plan = planner.planStart(
                queue,
                Optional.of(new CollectionSession("season-1", 2, 2L)),
                progressStore,
                ResumePolicy.PREFER_REQUESTED_ITEM
        );

        assertEquals(0, plan.startIndex(), "requested item start index");
        assertEquals(25_000L, plan.startPositionMs(), "requested item resume progress");
        assertEquals("requested item", plan.reason(), "requested item reason");
    }

    private static void resumesSavedCollectionPosition() {
        ResolvedQueue queue = demoQueue(0);
        InMemoryProgressStore progressStore = new InMemoryProgressStore();
        progressStore.saveProgress(new PlaybackProgress(queue.items().get(1).key(), 48_000L, 150_000L, false, 1L));

        PlaybackPlanner planner = new PlaybackPlanner();
        StartPlan plan = planner.planStart(
                queue,
                Optional.of(new CollectionSession("season-1", 1, 2L)),
                progressStore,
                ResumePolicy.PREFER_SAVED_COLLECTION_POSITION
        );

        assertEquals(1, plan.startIndex(), "saved collection start index");
        assertEquals(48_000L, plan.startPositionMs(), "saved collection resume progress");
        assertEquals("saved collection position", plan.reason(), "saved collection reason");
    }

    private static void skipsFinishedItemsInsideCollection() {
        ResolvedQueue queue = demoQueue(0);
        InMemoryProgressStore progressStore = new InMemoryProgressStore();
        progressStore.saveProgress(new PlaybackProgress(queue.items().get(0).key(), 120_000L, 120_000L, true, 1L));
        progressStore.saveProgress(new PlaybackProgress(queue.items().get(1).key(), 150_000L, 150_000L, true, 2L));

        PlaybackPlanner planner = new PlaybackPlanner();
        StartPlan plan = planner.planStart(
                queue,
                Optional.of(new CollectionSession("season-1", 0, 2L)),
                progressStore,
                ResumePolicy.PREFER_SAVED_COLLECTION_POSITION
        );

        assertEquals(2, plan.startIndex(), "next unfinished item index");
        assertEquals(0L, plan.startPositionMs(), "next unfinished item position");
        assertEquals("next unfinished item", plan.reason(), "next unfinished item reason");
    }

    private static void fixtureResolverMatchesExactPage() throws Exception {
        Path fixture = writeFixture("""
                collection.id=season-1
                collection.title=Fixture Season
                item.0.bvid=BV1xx411c7mD
                item.0.cid=1001
                item.0.page=1
                item.0.title=Episode 1
                item.0.durationMs=120000
                item.1.bvid=BV1xx411c7mD
                item.1.cid=1002
                item.1.page=2
                item.1.title=Episode 2
                item.1.durationMs=150000
                """);

        ResolvedQueue queue = new FixtureQueueResolver(fixture).resolve(new VideoReference("BV1xx411c7mD", 2));
        assertEquals(1, queue.requestedIndex(), "fixture exact page requested index");
        assertEquals(2, queue.items().size(), "fixture item count");
    }

    private static void fixtureResolverFallsBackToSingleBvidMatch() throws Exception {
        Path fixture = writeFixture("""
                collection.id=season-2
                collection.title=Fixture Collection
                item.0.bvid=BV1xx411c7mD
                item.0.cid=2001
                item.0.title=Episode 1
                item.0.durationMs=100000
                item.1.bvid=BV2xx411c7mD
                item.1.cid=2002
                item.1.title=Episode 2
                item.1.durationMs=110000
                """);

        ResolvedQueue queue = new FixtureQueueResolver(fixture).resolve(new VideoReference("BV2xx411c7mD", 1));
        assertEquals(1, queue.requestedIndex(), "fixture single bvid match requested index");
    }

    private static void liveResolverBuildsSeasonQueue() {
        String json = """
                {
                  "bvid": "BV1AA411c7mD",
                  "title": "Current Episode",
                  "pic": "https://example.com/default.jpg",
                  "cid": 9001,
                  "duration": 61,
                  "ugc_season": {
                    "id": 7788,
                    "title": "Season Demo",
                    "sections": [
                      {
                        "episodes": [
                          {
                            "bvid": "BV1AA411c7mD",
                            "cid": 9001,
                            "title": "Current Episode",
                            "arc": {
                              "title": "Current Episode",
                              "duration": 61,
                              "pic": "https://example.com/cover-a.jpg"
                            },
                            "page": {
                              "cid": 9001,
                              "page": 1,
                              "part": "Intro",
                              "duration": 61
                            }
                          },
                          {
                            "bvid": "BV1BB411c7mD",
                            "cid": 9002,
                            "title": "Second Episode",
                            "arc": {
                              "title": "Second Episode",
                              "duration": 73,
                              "pic": "https://example.com/cover-b.jpg"
                            },
                            "page": {
                              "cid": 9002,
                              "page": 1,
                              "part": "Main",
                              "duration": 73
                            }
                          }
                        ]
                      }
                    ]
                  }
                }
                """;

        ResolvedQueue queue = new BilibiliViewQueueResolver(
                ignored -> Jsons.asObject(SimpleJsonParser.parse(json), "view data")
        ).resolve(new VideoReference("BV1BB411c7mD", 1));

        assertEquals("season:7788", queue.collectionId(), "season queue collection id");
        assertEquals("Season Demo", queue.collectionTitle(), "season queue collection title");
        assertEquals(2, queue.items().size(), "season queue size");
        assertEquals(1, queue.requestedIndex(), "season queue requested index");
        assertEquals("Second Episode - P1 Main", queue.items().get(1).title(), "season queue second title");
    }

    private static void liveResolverBuildsMultiPageQueue() {
        String json = """
                {
                  "bvid": "BV1CC411c7mD",
                  "title": "Multipart Video",
                  "pic": "https://example.com/default.jpg",
                  "cid": 8001,
                  "duration": 120,
                  "pages": [
                    {
                      "cid": 8001,
                      "page": 1,
                      "part": "Part 1",
                      "duration": 45
                    },
                    {
                      "cid": 8002,
                      "page": 2,
                      "part": "Part 2",
                      "duration": 75
                    }
                  ]
                }
                """;

        ResolvedQueue queue = new BilibiliViewQueueResolver(
                ignored -> Jsons.asObject(SimpleJsonParser.parse(json), "view data")
        ).resolve(new VideoReference("BV1CC411c7mD", 2));

        assertEquals("video:BV1CC411c7mD", queue.collectionId(), "multi-page collection id");
        assertEquals(2, queue.items().size(), "multi-page queue size");
        assertEquals(1, queue.requestedIndex(), "multi-page requested index");
        assertEquals(2, queue.items().get(1).pageNumber(), "multi-page page number");
    }

    private static void fixtureWriterRoundTrip() throws Exception {
        ResolvedQueue original = new BilibiliViewQueueResolver(
                ignored -> Jsons.asObject(SimpleJsonParser.parse("""
                        {
                          "bvid": "BV1DD411c7mD",
                          "title": "Fixture Export",
                          "pic": "https://example.com/default.jpg",
                          "cid": 7001,
                          "duration": 120,
                          "pages": [
                            {
                              "cid": 7001,
                              "page": 1,
                              "part": "Part 1",
                              "duration": 50
                            },
                            {
                              "cid": 7002,
                              "page": 2,
                              "part": "Part 2",
                              "duration": 70
                            }
                          ]
                        }
                        """), "view data")
        ).resolve(new VideoReference("BV1DD411c7mD", 2));

        Path fixture = Files.createTempFile("bili-listen-export", ".properties");
        new FixtureWriter().write(original, fixture);
        ResolvedQueue restored = new FixtureQueueResolver(fixture).resolve(new VideoReference("BV1DD411c7mD", 2));

        assertEquals(original.collectionId(), restored.collectionId(), "fixture round trip collection id");
        assertEquals(original.items().size(), restored.items().size(), "fixture round trip size");
        assertEquals(original.requestedIndex(), restored.requestedIndex(), "fixture round trip requested index");
        assertEquals(original.items().get(1).pageNumber(), restored.items().get(1).pageNumber(), "fixture round trip page number");
    }

    private static void audioResolverSelectsRegularDashTrack() {
        String json = """
                {
                  "timelength": 123000,
                  "dash": {
                    "audio": [
                      {
                        "id": 30216,
                        "baseUrl": "https://example.com/audio-64.m4s",
                        "bandwidth": 64000,
                        "codecs": "mp4a.40.2",
                        "mimeType": "audio/mp4"
                      },
                      {
                        "id": 30280,
                        "baseUrl": "https://example.com/audio-192.m4s",
                        "bandwidth": 192000,
                        "codecs": "mp4a.40.2",
                        "mimeType": "audio/mp4"
                      }
                    ],
                    "flac": {
                      "audio": {
                        "id": 30251,
                        "baseUrl": "https://example.com/audio-flac.m4s",
                        "bandwidth": 999999,
                        "codecs": "fLaC",
                        "mimeType": "audio/flac"
                      }
                    }
                  }
                }
                """;

        ResolvedAudio audio = BilibiliAudioResolver.buildResolvedAudio(
                Jsons.asObject(SimpleJsonParser.parse(json), "playurl"),
                new ContentKey("BV1xx411c7mD", 6001L),
                "Audio Test",
                0L
        );

        assertEquals(AudioStreamKind.DASH_AUDIO, audio.selected().kind(), "audio resolver selected kind");
        assertEquals(30280, audio.selected().id(), "audio resolver selected id");
        assertEquals(123000L, audio.durationMs(), "audio resolver duration");
        assertEquals(3, audio.candidates().size(), "audio resolver candidate count");
    }

    private static void audioResolverFallsBackToDurl() {
        String json = """
                {
                  "format": "mp4",
                  "durl": [
                    {
                      "url": "https://example.com/muxed.mp4"
                    }
                  ]
                }
                """;

        ResolvedAudio audio = BilibiliAudioResolver.buildResolvedAudio(
                Jsons.asObject(SimpleJsonParser.parse(json), "playurl"),
                new ContentKey("BV1xx411c7mD", 6002L),
                "Fallback Test",
                0L
        );

        assertEquals(AudioStreamKind.MUXED_FALLBACK, audio.selected().kind(), "durl fallback kind");
        assertEquals("https://example.com/muxed.mp4", audio.selected().url(), "durl fallback url");
    }

    private static void sessionServiceSavesCurrentPlannedItem() {
        ResolvedQueue queue = demoQueue(0);
        QueueResolver resolver = ignored -> queue;
        InMemoryProgressStore progressStore = new InMemoryProgressStore();
        InMemoryCollectionSessionStore sessionStore = new InMemoryCollectionSessionStore();
        sessionStore.saveSession(new CollectionSession("season-1", 1, 1L));

        ListeningSessionService service = new ListeningSessionService();
        PlannedPlayback playback = service.plan(
                resolver,
                new VideoReference("BV1xx411c7mD", 1),
                ResumePolicy.PREFER_SAVED_COLLECTION_POSITION,
                progressStore,
                sessionStore
        );
        PlaybackProgress progress = service.saveCurrentProgress(playback, 54_000L, 2L, progressStore, sessionStore);

        assertEquals(1, playback.plan().startIndex(), "session service planned index");
        assertEquals(queue.items().get(1).key(), progress.key(), "session service saved key");
        assertEquals(54_000L, progress.positionMs(), "session service saved position");
        assertEquals(1, sessionStore.loadSession("season-1").orElseThrow().currentIndex(), "session service saved session index");
    }

    private static void sessionServiceAdvancesToNextItem() {
        ResolvedQueue queue = demoQueue(0);
        QueueResolver resolver = ignored -> queue;
        InMemoryProgressStore progressStore = new InMemoryProgressStore();
        InMemoryCollectionSessionStore sessionStore = new InMemoryCollectionSessionStore();
        sessionStore.saveSession(new CollectionSession("season-1", 1, 1L));

        ListeningSessionService service = new ListeningSessionService();
        PlannedPlayback playback = service.plan(
                resolver,
                new VideoReference("BV1xx411c7mD", 1),
                ResumePolicy.PREFER_SAVED_COLLECTION_POSITION,
                progressStore,
                sessionStore
        );
        AdvanceOutcome outcome = service.advance(playback, 3L, progressStore, sessionStore);

        assertEquals(false, outcome.reachedEnd(), "session service advance reached end");
        assertEquals(queue.items().get(1).title(), outcome.completedItem().title(), "session service completed item");
        assertEquals(2, outcome.nextPlayback().plan().startIndex(), "session service next index");
        assertEquals(true, progressStore.loadProgress(queue.items().get(1).key()).orElseThrow().finished(), "session service finished progress");
        assertEquals(2, sessionStore.loadSession("season-1").orElseThrow().currentIndex(), "session service next session index");
    }

    private static void fileBackedStoresRoundTrip() throws Exception {
        Path tempDir = Files.createTempDirectory("bili-listen-prototype");
        Path progressFile = tempDir.resolve("progress.properties");
        Path sessionFile = tempDir.resolve("session.properties");

        FileBackedProgressStore progressStore = new FileBackedProgressStore(progressFile);
        FileBackedCollectionSessionStore sessionStore = new FileBackedCollectionSessionStore(sessionFile);

        ContentKey key = new ContentKey("BV1xx411c7mD", 9001L);
        PlaybackProgress progress = new PlaybackProgress(key, 12_345L, 90_000L, false, 7L);
        CollectionSession session = new CollectionSession("season-9", 4, 8L);

        progressStore.saveProgress(progress);
        sessionStore.saveSession(session);

        PlaybackProgress restoredProgress = progressStore.loadProgress(key).orElseThrow();
        CollectionSession restoredSession = sessionStore.loadSession("season-9").orElseThrow();

        assertEquals(progress, restoredProgress, "progress store round trip");
        assertEquals(session, restoredSession, "session store round trip");
    }

    private static ResolvedQueue demoQueue(int requestedIndex) {
        QueueItem first = new QueueItem(
                new ContentKey("BV1xx411c7mD", 1001L),
                "Episode 1",
                120_000L,
                1,
                0,
                "season-1",
                "Demo Season",
                null
        );
        QueueItem second = new QueueItem(
                new ContentKey("BV1xx411c7mD", 1002L),
                "Episode 2",
                150_000L,
                1,
                1,
                "season-1",
                "Demo Season",
                null
        );
        QueueItem third = new QueueItem(
                new ContentKey("BV1xx411c7mD", 1003L),
                "Episode 3",
                180_000L,
                1,
                2,
                "season-1",
                "Demo Season",
                null
        );

        return new ResolvedQueue("season-1", "Demo Season", List.of(first, second, third), requestedIndex);
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " expected [" + expected + "] but got [" + actual + "]");
        }
    }

    private static Path writeFixture(String body) throws Exception {
        Path fixture = Files.createTempFile("bili-listen-fixture", ".properties");
        Files.writeString(fixture, body);
        return fixture;
    }
}
