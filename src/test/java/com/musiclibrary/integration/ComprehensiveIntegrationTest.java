package com.musiclibrary.integration;

import com.musiclibrary.controller.LibraryController;
import com.musiclibrary.controller.PlayerController;
import com.musiclibrary.controller.SearchController;
import com.musiclibrary.i18n.AppLocale;
import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Library;
import com.musiclibrary.model.PlaybackState;
import com.musiclibrary.model.Playlist;
import com.musiclibrary.model.RepeatMode;
import com.musiclibrary.model.Track;
import com.musiclibrary.model.TrackMetadataUpdate;
import com.musiclibrary.service.audio.AudioPlayer;
import com.musiclibrary.service.audio.PlayerListener;
import com.musiclibrary.service.cache.ArtworkCache;
import com.musiclibrary.service.importer.DuplicatePolicy;
import com.musiclibrary.service.importer.FileScanner;
import com.musiclibrary.service.importer.ImportProgressListener;
import com.musiclibrary.service.importer.ImportResult;
import com.musiclibrary.service.importer.LibraryImportService;
import com.musiclibrary.service.metadata.MetadataExtractor;
import com.musiclibrary.service.metadata.TagWriter;
import com.musiclibrary.service.search.FastSearchIndex;
import com.musiclibrary.service.storage.JsonLibraryStorage;
import com.musiclibrary.service.storage.MissingTrackResolver;
import com.musiclibrary.theme.AppTheme;
import com.musiclibrary.theme.ThemeManager;
import com.musiclibrary.ui.MainFrame;
import com.musiclibrary.ui.MiniPlayerFrame;
import com.musiclibrary.ui.panel.ControlBarPanel;
import com.musiclibrary.ui.panel.SidebarPanel;
import com.musiclibrary.ui.panel.TrackTablePanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * End-to-end comprehensive integration test verifying full application lifecycle:
 * - Library storage initialization & persistence
 * - Music importing & deduplication (upsert / skip)
 * - Playlist management (create, add, reorder, remove)
 * - Sub-millisecond inverted index search (single, AND, CJK bigram, case-insensitive)
 * - Audio playback transport & play queue (priority play next, shuffle, repeat)
 * - Missing track resolution & automatic relinking
 * - ID3 tag atomic updates & immediate search/cache synchronization
 * - Theme and locale dynamic switching
 * - MiniPlayer window switching & restore
 * - Clean resource disposal
 */
class ComprehensiveIntegrationTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        ThemeManager.getInstance().applyTheme(AppTheme.DARK);
        ArtworkCache.getInstance().clear();
    }

    @AfterEach
    void tearDown() {
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        ArtworkCache.getInstance().clear();
    }

    @Test
    @DisplayName("Full application lifecycle integration flow executes end-to-end without errors")
    void testFullApplicationLifecycle() throws Exception {
        Path storageFile = tempDir.resolve("library.json");
        JsonLibraryStorage storage = new JsonLibraryStorage();

        // -------------------------------------------------------------
        // Step 1: Library initialization & empty persistence
        // -------------------------------------------------------------
        Library library = new Library();
        storage.save(library, storageFile);
        assertThat(Files.exists(storageFile)).isTrue();

        Library loadedLib = storage.load(storageFile);
        assertThat(loadedLib.getTrackCount()).isEqualTo(0);

        // -------------------------------------------------------------
        // Step 2: Music Import & Deduplication
        // -------------------------------------------------------------
        Path musicFolder = tempDir.resolve("Music");
        Files.createDirectories(musicFolder);

        Path songFile1 = Files.write(musicFolder.resolve("01_Rock_Song.mp3"), new byte[1024]);
        Path songFile2 = Files.write(musicFolder.resolve("02_Jazz_Standard.mp3"), new byte[2048]);
        Path songFile3 = Files.write(musicFolder.resolve("03_夏祭り_JPop.mp3"), new byte[4096]);

        MetadataExtractor testExtractor = new MetadataExtractor() {
            @Override
            public Track extract(Path filePath) {
                String fname = filePath.getFileName().toString();
                Track.Builder builder = Track.builder()
                        .filePath(filePath.toAbsolutePath().normalize().toString())
                        .fileSizeBytes(filePath.toFile().length())
                        .durationSeconds(200);

                if (fname.contains("01_Rock")) {
                    return builder.title("Rock Anthem").artist("Queen").album("Greatest Hits").genre("Rock").year(1975).trackNumber(1).build();
                } else if (fname.contains("02_Jazz")) {
                    return builder.title("Autumn Leaves").artist("Miles Davis").album("Kind of Blue").genre("Jazz").year(1959).trackNumber(2).build();
                } else {
                    return builder.title("夏祭り").artist("Whiteberry").album("After School").genre("J-Pop").year(2000).trackNumber(3).build();
                }
            }

            @Override
            public boolean supports(Path filePath) {
                return filePath.toString().endsWith(".mp3");
            }
        };

        LibraryImportService importService = new LibraryImportService(
                library, storage, storageFile, new FileScanner(), testExtractor, 2, DuplicatePolicy.SKIP_SAME_CONTENT
        );

        CountDownLatch importLatch = new CountDownLatch(1);
        var importTask = importService.scanAndImportAsync(musicFolder, new ImportProgressListener() {
            @Override
            public void onProgress(int processedCount, int totalCount, String currentFileName) {}
            @Override
            public void onBatchImported(List<Track> importedBatch) {}
            @Override
            public void onCompleted(int totalImportedCount) {
                importLatch.countDown();
            }
            @Override
            public void onCancelled(int partialImportedCount) {
                importLatch.countDown();
            }
            @Override
            public void onError(Throwable error) {
                importLatch.countDown();
            }
        });

        assertThat(importLatch.await(5, TimeUnit.SECONDS)).isTrue();
        ImportResult result1 = importTask.getResultFuture().get(2, TimeUnit.SECONDS);
        assertThat(result1.addedCount()).isEqualTo(3);
        assertThat(library.getTrackCount()).isEqualTo(3);

        // Deduplication test: re-scan same folder should skip all 3
        CountDownLatch reimportLatch = new CountDownLatch(1);
        var reimportTask = importService.scanAndImportAsync(musicFolder, new ImportProgressListener() {
            @Override
            public void onProgress(int processed, int total, String name) {}
            @Override
            public void onBatchImported(List<Track> batch) {}
            @Override
            public void onCompleted(int total) { reimportLatch.countDown(); }
            @Override
            public void onCancelled(int partial) { reimportLatch.countDown(); }
            @Override
            public void onError(Throwable error) { reimportLatch.countDown(); }
        });
        assertThat(reimportLatch.await(5, TimeUnit.SECONDS)).isTrue();
        ImportResult result2 = reimportTask.getResultFuture().get(2, TimeUnit.SECONDS);
        assertThat(result2.addedCount()).isEqualTo(0);
        assertThat(result2.skippedCount()).isEqualTo(3);
        assertThat(library.getTrackCount()).isEqualTo(3);

        Track track1 = library.getTracks().stream()
                .filter(t -> t.getFilePath().equals(songFile1.toAbsolutePath().normalize().toString()))
                .findFirst().orElseThrow();
        Track track2 = library.getTracks().stream()
                .filter(t -> t.getFilePath().equals(songFile2.toAbsolutePath().normalize().toString()))
                .findFirst().orElseThrow();
        Track track3 = library.getTracks().stream()
                .filter(t -> t.getFilePath().equals(songFile3.toAbsolutePath().normalize().toString()))
                .findFirst().orElseThrow();

        // -------------------------------------------------------------
        // Step 3: Playlist Management via LibraryController
        // -------------------------------------------------------------
        SidebarPanel sidebarPanel = new SidebarPanel();
        TrackTablePanel tablePanel = new TrackTablePanel();
        FastSearchIndex searchIndex = new FastSearchIndex();
        searchIndex.indexTracks(library.getTracks());

        TagWriter mockTagWriter = new TagWriter() {
            @Override
            public void write(Path targetFile, TrackMetadataUpdate update) {}
            @Override
            public boolean supports(Path targetFile) { return true; }
        };

        LibraryController libController = new LibraryController(
                library, sidebarPanel, tablePanel, storage, storageFile, searchIndex, null, mockTagWriter
        );
        libController.showLibrarySection(SidebarPanel.LibrarySection.ALL_SONGS);
        assertThat(tablePanel.getTable().getRowCount()).isEqualTo(3);

        Playlist playlist = libController.createPlaylist("My Top Songs");
        assertThat(playlist).isNotNull();
        assertThat(library.getPlaylists()).hasSize(1);

        // Add track1 and track2 to playlist
        libController.addTracksToPlaylist(playlist.getId(), List.of(track1.getId(), track2.getId()));
        assertThat(playlist.getTrackIds()).containsExactly(track1.getId(), track2.getId());

        // Reorder playlist (swap index 0 and 1)
        libController.reorderTracksInPlaylist(playlist.getId(), 0, 1);
        assertThat(playlist.getTrackIds()).containsExactly(track2.getId(), track1.getId());

        // Remove track2 from playlist
        libController.removeTrackFromPlaylist(playlist.getId(), track2.getId());
        assertThat(playlist.getTrackIds()).containsExactly(track1.getId());

        // -------------------------------------------------------------
        // Step 4: Fast Search Index Lookup (Single, AND, CJK, Case-Insensitive)
        // -------------------------------------------------------------
        libController.showLibrarySection(SidebarPanel.LibrarySection.ALL_SONGS);
        assertThat(tablePanel.getTable().getRowCount()).isEqualTo(3);

        javax.swing.JTextField searchField = new javax.swing.JTextField();
        SearchController searchController = new SearchController(searchIndex, searchField, tablePanel);

        // Single word search
        Set<String> searchRock = searchIndex.search("Rock");
        assertThat(searchRock).contains(track1.getId());

        // Case-insensitive search
        Set<String> searchMiles = searchIndex.search("miles");
        assertThat(searchMiles).contains(track2.getId());

        // AND multi-word search
        Set<String> searchAnd = searchIndex.search("Queen Greatest");
        assertThat(searchAnd).contains(track1.getId());
        assertThat(searchAnd).doesNotContain(track2.getId());

        // CJK Bigram search (Japanese "夏祭り")
        Set<String> searchCjk = searchIndex.search("夏祭り");
        assertThat(searchCjk).contains(track3.getId());

        // CJK single character prefix
        Set<String> searchCjkPrefix = searchIndex.search("夏");
        assertThat(searchCjkPrefix).contains(track3.getId());

        // SearchController execute query via text field
        searchField.setText("Whiteberry");
        searchController.applyFilter();
        assertThat(tablePanel.getTable().getRowCount()).isEqualTo(1);

        searchController.clearSearch();
        searchController.applyFilter();
        assertThat(tablePanel.getTable().getRowCount()).isEqualTo(3);

        // -------------------------------------------------------------
        // Step 5: Audio Player, Transport & Queue Control
        // -------------------------------------------------------------
        ControlBarPanel controlBar = new ControlBarPanel();
        MockAudioPlayer audioPlayer = new MockAudioPlayer();
        PlayerController playerController = new PlayerController(audioPlayer, controlBar);

        // Play track1 with context
        playerController.play(track1, List.of(track1, track2, track3));
        assertThat(audioPlayer.getState()).isEqualTo(PlaybackState.PLAYING);
        assertThat(audioPlayer.getCurrentTrack()).isEqualTo(track1);

        // Pause & Resume
        playerController.togglePlayPause();
        assertThat(audioPlayer.getState()).isEqualTo(PlaybackState.PAUSED);
        playerController.togglePlayPause();
        assertThat(audioPlayer.getState()).isEqualTo(PlaybackState.PLAYING);

        // Seek
        playerController.seekTo(120);
        assertThat(audioPlayer.getCurrentPositionSeconds()).isEqualTo(120);

        // Volume
        playerController.setVolumePercent(85);
        assertThat(audioPlayer.getVolume()).isEqualTo(0.85f);

        // Priority Queue (Play Next): enqueue track3 as next
        playerController.enqueueNext(track3);

        // Skip Next -> Should play priority track3 first
        playerController.next();
        assertThat(audioPlayer.getCurrentTrack()).isEqualTo(track3);

        // Shuffle & Repeat toggles
        playerController.toggleShuffle();
        assertThat(playerController.getQueue().isShuffle()).isTrue();

        playerController.setRepeatMode(RepeatMode.ALL);
        assertThat(playerController.getQueue().getRepeatMode()).isEqualTo(RepeatMode.ALL);
        playerController.setRepeatMode(RepeatMode.ONE);
        assertThat(playerController.getQueue().getRepeatMode()).isEqualTo(RepeatMode.ONE);
        playerController.setRepeatMode(RepeatMode.OFF);
        assertThat(playerController.getQueue().getRepeatMode()).isEqualTo(RepeatMode.OFF);

        // -------------------------------------------------------------
        // Step 6: Missing Track Resolution & Automatic Relinking
        // -------------------------------------------------------------
        // Simulate file move to a reorganized subdirectory: Music/Relocated/01_Rock_Song.mp3
        Path relocatedDir = musicFolder.resolve("Relocated");
        Files.createDirectories(relocatedDir);
        Path movedSong = relocatedDir.resolve("01_Rock_Song.mp3");
        Files.move(songFile1, movedSong);

        assertThat(MissingTrackResolver.isMissing(track1)).isTrue();
        track1.setMissing(true);
        assertThat(track1.isMissing()).isTrue();

        CountDownLatch resolveLatch = new CountDownLatch(1);
        libController.resolveMissingTracksAsync(musicFolder, new MissingTrackResolver.ResolveProgressListener() {
            @Override
            public void onProgress(int current, int total, Path path) {}
            @Override
            public void onRelinked(Track t, Path newPath) {}
            @Override
            public void onComplete(int relinkedCount, int remainingCount) {
                resolveLatch.countDown();
            }
            @Override
            public void onError(Throwable error) {
                resolveLatch.countDown();
            }
        });

        assertThat(resolveLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(track1.isMissing()).isFalse();
        assertThat(track1.getFilePath()).isEqualTo(movedSong.toAbsolutePath().normalize().toString());

        // -------------------------------------------------------------
        // Step 7: ID3 Tag Atomic Update, Search Reindexing & Artwork Cache Eviction
        // -------------------------------------------------------------
        // Warm up artwork cache for track1
        byte[] dummyArtwork = createTestImageBytes(40, 40, Color.RED);
        track1.setArtworkData(dummyArtwork);
        ArtworkCache.getInstance().getImage(track1, 38, 38);
        assertThat(ArtworkCache.getInstance().contains(track1.getId(), 38, 38)).isTrue();

        CountDownLatch tagLatch = new CountDownLatch(1);
        TrackMetadataUpdate tagUpdate = TrackMetadataUpdate.builder()
                .title("Bohemian Rhapsody")
                .artist("Queen Remastered 2026")
                .clearArtwork()
                .build();

        libController.updateTrackMetadata(track1, tagUpdate, tagLatch::countDown, null);
        assertThat(tagLatch.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(track1.getTitle()).isEqualTo("Bohemian Rhapsody");
        assertThat(track1.getArtist()).isEqualTo("Queen Remastered 2026");
        assertThat(track1.getArtworkData()).isNull();

        // Search index updated immediately
        assertThat(searchIndex.search("Bohemian")).contains(track1.getId());
        assertThat(searchIndex.search("Queen Remastered 2026")).contains(track1.getId());

        // ArtworkCache evicted track1
        assertThat(ArtworkCache.getInstance().contains(track1.getId(), 38, 38)).isFalse();

        // -------------------------------------------------------------
        // Step 8: Dynamic Theme & Locale Switching
        // -------------------------------------------------------------
        ThemeManager.getInstance().applyTheme(AppTheme.LIGHT);
        assertThat(ThemeManager.getInstance().getCurrentTheme()).isEqualTo(AppTheme.LIGHT);
        ThemeManager.getInstance().applyTheme(AppTheme.DARK);
        assertThat(ThemeManager.getInstance().getCurrentTheme()).isEqualTo(AppTheme.DARK);

        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        assertThat(I18nManager.getInstance().getCurrentLocale()).isEqualTo(AppLocale.JAPANESE);
        assertThat(I18nManager.getInstance().get("app.name")).isEqualTo("ミュージックライブラリマネージャー");

        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        assertThat(I18nManager.getInstance().getCurrentLocale()).isEqualTo(AppLocale.ENGLISH);
        assertThat(I18nManager.getInstance().get("app.name")).isEqualTo("Music Library Manager");

        // -------------------------------------------------------------
        // Step 9: Clean Resource Disposal
        // -------------------------------------------------------------
        importService.shutdown();
        libController.dispose();
        playerController.dispose();
        audioPlayer.close();

        assertThat(importService.isShutdown()).isTrue();
        assertThat(libController.isDisposed()).isTrue();
        assertThat(playerController.isDisposed()).isTrue();
        assertThat(audioPlayer.isClosed()).isTrue();
    }

    @Test
    @DisplayName("MainFrame and MiniPlayerFrame toggle visibility, preserve state, and dispose safely")
    void testUiFrameLifecycle_MiniPlayerAndMainFrame() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());

        Library lib = new Library();
        Track t = Track.builder().id("ui-t1").filePath("tracks/t1.mp3").title("UI Track").build();
        lib.addTrack(t);

        MainFrame mainFrame = new MainFrame(lib, null);
        try {
            MiniPlayerFrame miniPlayer = mainFrame.getMiniPlayerFrame();
            assertThat(miniPlayer).isNotNull();

            // Initial state: MainFrame visible, MiniPlayer not visible
            mainFrame.setVisible(true);
            assertThat(mainFrame.isVisible()).isTrue();
            assertThat(miniPlayer.isVisible()).isFalse();

            // Switch to MiniPlayer mode
            mainFrame.switchToMiniPlayer();
            assertThat(mainFrame.isVisible()).isFalse();
            assertThat(miniPlayer.isVisible()).isTrue();

            // Restore from MiniPlayer mode
            mainFrame.restoreFromMiniPlayer();
            assertThat(mainFrame.isVisible()).isTrue();
            assertThat(miniPlayer.isVisible()).isFalse();

            // Dynamic locale cascade to both frames
            I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
            assertThat(mainFrame.getTitle()).isEqualTo("ミュージックライブラリマネージャー");
            assertThat(miniPlayer.getTitle()).isEqualTo("ミニプレーヤー");

            I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
            assertThat(mainFrame.getTitle()).isEqualTo("Music Library Manager");
            assertThat(miniPlayer.getTitle()).isEqualTo("Mini Player");
        } finally {
            mainFrame.dispose();
        }
    }

    @Test
    @DisplayName("Multiple playlists CRUD operations and track cascade deletion preserve JSON persistence integrity")
    void testMultiPlaylistAndTrackCrudPersistenceIntegrity() throws Exception {
        Path storageFile = tempDir.resolve("multi_playlist_library.json");
        JsonLibraryStorage storage = new JsonLibraryStorage();

        Library library = new Library();

        Track t1 = Track.builder().id("t1").filePath("tracks/t1.mp3").title("Bohemian Rhapsody").artist("Queen").album("A Night at the Opera").build();
        Track t2 = Track.builder().id("t2").filePath("tracks/t2.mp3").title("Billie Jean").artist("Michael Jackson").album("Thriller").build();
        Track t3 = Track.builder().id("t3").filePath("tracks/t3.mp3").title("Smells Like Teen Spirit").artist("Nirvana").album("Nevermind").build();
        Track t4 = Track.builder().id("t4").filePath("tracks/t4.mp3").title("Hotel California").artist("Eagles").album("Hotel California").build();
        Track t5 = Track.builder().id("t5").filePath("tracks/t5.mp3").title("Take On Me").artist("a-ha").album("Hunting High and Low").build();

        library.addTrack(t1);
        library.addTrack(t2);
        library.addTrack(t3);
        library.addTrack(t4);
        library.addTrack(t5);

        // Create 3 playlists
        Playlist pl1 = library.createPlaylist("Classic Rock");
        pl1.addTrack(t1.getId());
        pl1.addTrack(t4.getId());
        pl1.addTrack(t3.getId());

        Playlist pl2 = library.createPlaylist("80s Hits");
        pl2.addTrack(t2.getId());
        pl2.addTrack(t5.getId());
        pl2.addTrack(t4.getId());

        Playlist pl3 = library.createPlaylist("Workout Mix");
        pl3.addTrack(t2.getId());
        pl3.addTrack(t3.getId());
        pl3.addTrack(t5.getId());

        // 1. Reorder tracks in pl1 (move t1 from index 0 to index 2: [t4, t3, t1])
        pl1.reorder(0, 2);
        assertThat(pl1.getTrackIds()).containsExactly(t4.getId(), t3.getId(), t1.getId());

        // 2. Rename pl3 to "Morning Gym"
        pl3.setName("Morning Gym");
        assertThat(pl3.getName()).isEqualTo("Morning Gym");

        // 3. Remove t5 from pl2 (t5 remains in library and pl3)
        pl2.removeTrack(t5.getId());
        assertThat(pl2.getTrackIds()).containsExactly(t2.getId(), t4.getId());
        assertThat(library.getTrack(t5.getId())).isNotNull();
        assertThat(pl3.getTrackIds()).contains(t5.getId());

        // 4. Cascade delete t3 from library (must automatically be removed from both pl1 and pl3)
        library.removeTrack(t3.getId());
        assertThat(library.getTrack(t3.getId())).isNull();
        assertThat(pl1.getTrackIds()).containsExactly(t4.getId(), t1.getId());
        assertThat(pl3.getTrackIds()).containsExactly(t2.getId(), t5.getId());
        assertThat(pl2.getTrackIds()).containsExactly(t2.getId(), t4.getId());

        // 5. Save library and reload in a new instance
        storage.save(library, storageFile);
        assertThat(Files.exists(storageFile)).isTrue();

        Library reloaded = storage.load(storageFile);
        assertThat(reloaded.getTrackCount()).isEqualTo(4);
        assertThat(reloaded.getTrack(t3.getId())).isNull();
        assertThat(reloaded.getTrack(t1.getId())).isNotNull();

        assertThat(reloaded.getPlaylists()).hasSize(3);

        Playlist reloadedPl1 = reloaded.getPlaylist(pl1.getId());
        assertThat(reloadedPl1).isNotNull();
        assertThat(reloadedPl1.getName()).isEqualTo("Classic Rock");
        assertThat(reloadedPl1.getTrackIds()).containsExactly(t4.getId(), t1.getId());

        Playlist reloadedPl2 = reloaded.getPlaylist(pl2.getId());
        assertThat(reloadedPl2).isNotNull();
        assertThat(reloadedPl2.getName()).isEqualTo("80s Hits");
        assertThat(reloadedPl2.getTrackIds()).containsExactly(t2.getId(), t4.getId());

        Playlist reloadedPl3 = reloaded.getPlaylist(pl3.getId());
        assertThat(reloadedPl3).isNotNull();
        assertThat(reloadedPl3.getName()).isEqualTo("Morning Gym");
        assertThat(reloadedPl3.getTrackIds()).containsExactly(t2.getId(), t5.getId());
    }

    @Test
    @DisplayName("Play queue priority interruption, shuffle, and repeat modes interact with full consistency")
    void testQueueInteraction_PriorityQueue_Shuffle_Repeat() {
        MockAudioPlayer audioPlayer = new MockAudioPlayer();
        ControlBarPanel controlBar = new ControlBarPanel();
        PlayerController playerController = new PlayerController(audioPlayer, controlBar);

        Track t1 = Track.builder().id("q1").filePath("tracks/q1.mp3").title("Queue Song 1").durationSeconds(180).build();
        Track t2 = Track.builder().id("q2").filePath("tracks/q2.mp3").title("Queue Song 2").durationSeconds(200).build();
        Track t3 = Track.builder().id("q3").filePath("tracks/q3.mp3").title("Queue Song 3").durationSeconds(220).build();
        Track t4 = Track.builder().id("q4").filePath("tracks/q4.mp3").title("Queue Song 4").durationSeconds(240).build();

        // 1. Play t1 with context tracks [t1, t2, t3, t4]
        playerController.play(t1, List.of(t1, t2, t3, t4));
        assertThat(audioPlayer.getCurrentTrack()).isEqualTo(t1);
        assertThat(audioPlayer.isPlaying()).isTrue();

        // 2. Enqueue two "Play Next" priority tracks
        Track p1 = Track.builder().id("p1").filePath("tracks/p1.mp3").title("Priority Next 1").durationSeconds(150).build();
        Track p2 = Track.builder().id("p2").filePath("tracks/p2.mp3").title("Priority Next 2").durationSeconds(160).build();
        playerController.enqueueNext(p1);
        playerController.enqueueNext(p2);

        // 3. Enable shuffle mode - priority tracks must still retain highest precedence
        playerController.setShuffle(true);
        assertThat(playerController.isShuffle()).isTrue();

        // Next advance must play p1
        playerController.next();
        assertThat(audioPlayer.getCurrentTrack()).isEqualTo(p1);

        // Next advance must play p2
        playerController.next();
        assertThat(audioPlayer.getCurrentTrack()).isEqualTo(p2);

        // Next advance returns to regular playback queue
        playerController.next();
        Track normalTrack = audioPlayer.getCurrentTrack();
        assertThat(normalTrack).isNotNull();

        // 4. RepeatMode.ONE interaction: setting repeat one repeats the current normal track
        playerController.setRepeatMode(RepeatMode.ONE);
        assertThat(playerController.getRepeatMode()).isEqualTo(RepeatMode.ONE);

        playerController.next();
        assertThat(audioPlayer.getCurrentTrack()).isEqualTo(normalTrack);

        // 5. RepeatMode.ALL interaction: queue cycles endlessly without halting
        playerController.setRepeatMode(RepeatMode.ALL);
        assertThat(playerController.getRepeatMode()).isEqualTo(RepeatMode.ALL);

        for (int i = 0; i < 10; i++) {
            playerController.next();
            assertThat(audioPlayer.getCurrentTrack()).isNotNull();
        }

        // 6. Cleanup
        playerController.dispose();
        audioPlayer.close();
        assertThat(audioPlayer.isClosed()).isTrue();
    }

    @Test
    @DisplayName("Missing track auto-relink followed by ID3 tag update, artwork cache eviction, and persistence")
    void testPowerUserScenario_RelinkThenTagUpdateThenArtworkEvictAndPersist() throws Exception {
        Path origDir = tempDir.resolve("original_music");
        Path movedDir = tempDir.resolve("relocated_music");
        Files.createDirectories(origDir);
        Files.createDirectories(movedDir);

        Path initialFile = origDir.resolve("01_Midnight_Groove.mp3");
        byte[] dummyAudio = new byte[]{0x49, 0x44, 0x33, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        Files.write(initialFile, dummyAudio);

        Track track = Track.builder()
                .id("power-user-track-1")
                .filePath(initialFile.toAbsolutePath().toString())
                .title("Midnight Groove")
                .artist("Jazz Quartet")
                .album("Night Session")
                .durationSeconds(210)
                .fileSizeBytes(dummyAudio.length)
                .build();

        Library library = new Library();
        library.addTrack(track);

        // 1. Move file to relocated directory keeping exact filename
        Path relocatedFile = movedDir.resolve("01_Midnight_Groove.mp3");
        Files.move(initialFile, relocatedFile);

        // Verify missing detection
        MissingTrackResolver resolver = new MissingTrackResolver();
        List<Track> missing = resolver.findMissingTracks(library.getTracks());
        assertThat(missing).containsExactly(track);
        assertThat(track.isMissing()).isTrue();

        // 2. Perform automated relink
        CountDownLatch latch = new CountDownLatch(1);
        resolver.resolveMissingTracksAsync(
                missing,
                movedDir,
                new MissingTrackResolver.ResolveProgressListener() {
                    @Override
                    public void onProgress(int current, int total, Path currentPath) {}

                    @Override
                    public void onRelinked(Track t, Path newPath) {}

                    @Override
                    public void onComplete(int relinkedCount, int remainingCount) {
                        latch.countDown();
                    }
                }
        );

        boolean resolvedInTime = latch.await(5, TimeUnit.SECONDS);
        assertThat(resolvedInTime).isTrue();
        assertThat(track.isMissing()).isFalse();
        assertThat(track.getFilePath()).isEqualTo(relocatedFile.toAbsolutePath().normalize().toString());

        // 3. Cache artwork for track
        byte[] artworkBytes = createTestImageBytes(80, 80, Color.MAGENTA);
        Image cachedImg = ArtworkCache.getInstance().getImage(track.getId(), artworkBytes, 40, 40);
        assertThat(cachedImg).isNotNull();
        assertThat(ArtworkCache.getInstance().contains(track.getId(), 40, 40)).isTrue();

        // 4. Update track metadata using LibraryController
        Path storageFile = tempDir.resolve("power_user_library.json");
        JsonLibraryStorage storage = new JsonLibraryStorage();
        SidebarPanel sidebarPanel = new SidebarPanel();
        TrackTablePanel tablePanel = new TrackTablePanel();
        FastSearchIndex searchIndex = new FastSearchIndex();
        TagWriter mockTagWriter = new TagWriter() {
            @Override
            public void write(Path targetFile, TrackMetadataUpdate update) {}
            @Override
            public boolean supports(Path targetFile) { return true; }
        };

        LibraryController libController = new LibraryController(
                library, sidebarPanel, tablePanel, storage, storageFile, searchIndex, resolver, mockTagWriter
        );

        CountDownLatch tagLatch = new CountDownLatch(1);
        TrackMetadataUpdate update = TrackMetadataUpdate.builder()
                .title("Midnight Groove (2026 Remaster)")
                .artist("The Jazz Quartet")
                .album("Night Session Deluxe")
                .year(2026)
                .genre("Modern Jazz")
                .clearArtwork()
                .build();

        libController.updateTrackMetadata(track, update, tagLatch::countDown, null);
        assertThat(tagLatch.await(5, TimeUnit.SECONDS)).isTrue();

        // Verify track metadata updated in memory
        assertThat(track.getTitle()).isEqualTo("Midnight Groove (2026 Remaster)");
        assertThat(track.getArtist()).isEqualTo("The Jazz Quartet");
        assertThat(track.getAlbum()).isEqualTo("Night Session Deluxe");
        assertThat(track.getYear()).isEqualTo(2026);
        assertThat(track.getGenre()).isEqualTo("Modern Jazz");

        // Verify artwork cache was evicted for this track
        assertThat(ArtworkCache.getInstance().contains(track.getId(), 40, 40)).isFalse();

        // 5. Persist to storage and reload
        storage.save(library, storageFile);

        Library reloaded = storage.load(storageFile);
        Track reloadedTrack = reloaded.getTrack(track.getId());
        assertThat(reloadedTrack).isNotNull();
        assertThat(reloadedTrack.getTitle()).isEqualTo("Midnight Groove (2026 Remaster)");
        assertThat(reloadedTrack.getArtist()).isEqualTo("The Jazz Quartet");
        assertThat(reloadedTrack.getFilePath()).isEqualTo(relocatedFile.toAbsolutePath().normalize().toString());
        assertThat(reloadedTrack.isMissing()).isFalse();

        // 6. Cleanup
        resolver.shutdown();
        libController.dispose();
    }

    // --- Helpers & Doubles ---

    private byte[] createTestImageBytes(int width, int height, Color color) {
        try {
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = img.createGraphics();
            g2.setColor(color);
            g2.fillRect(0, 0, width, height);
            g2.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class MockAudioPlayer implements AudioPlayer {
        private PlaybackState state = PlaybackState.STOPPED;
        private Track currentTrack = null;
        private long positionSeconds = 0;
        private float volume = 0.7f;
        private boolean closed = false;
        private final List<PlayerListener> listeners = new CopyOnWriteArrayList<>();

        @Override
        public void play(Track track) {
            this.currentTrack = track;
            this.state = PlaybackState.PLAYING;
            this.positionSeconds = 0;
            listeners.forEach(l -> l.onStateChanged(state));
            listeners.forEach(l -> l.onTrackStarted(track));
        }

        @Override
        public void pause() {
            this.state = PlaybackState.PAUSED;
            listeners.forEach(l -> l.onStateChanged(state));
        }

        @Override
        public void resume() {
            this.state = PlaybackState.PLAYING;
            listeners.forEach(l -> l.onStateChanged(state));
        }

        @Override
        public void stop() {
            this.state = PlaybackState.STOPPED;
            this.positionSeconds = 0;
            listeners.forEach(l -> l.onStateChanged(state));
        }

        @Override
        public void seekTo(long seconds) {
            this.positionSeconds = seconds;
            listeners.forEach(l -> l.onProgressUpdate(positionSeconds, getTotalDurationSeconds()));
        }

        @Override
        public void setVolume(float volume) {
            this.volume = volume;
        }

        @Override
        public float getVolume() {
            return volume;
        }

        @Override
        public PlaybackState getState() {
            return state;
        }

        @Override
        public Track getCurrentTrack() {
            return currentTrack;
        }

        @Override
        public long getCurrentPositionSeconds() {
            return positionSeconds;
        }

        @Override
        public long getTotalDurationSeconds() {
            return currentTrack != null ? currentTrack.getDurationSeconds() : 0;
        }

        @Override
        public boolean isPlaying() {
            return state == PlaybackState.PLAYING;
        }

        @Override
        public void addPlayerListener(PlayerListener listener) {
            if (listener != null) listeners.add(listener);
        }

        @Override
        public void removePlayerListener(PlayerListener listener) {
            listeners.remove(listener);
        }

        @Override
        public void close() {
            this.closed = true;
            this.state = PlaybackState.STOPPED;
        }

        public boolean isClosed() {
            return closed;
        }
    }
}
