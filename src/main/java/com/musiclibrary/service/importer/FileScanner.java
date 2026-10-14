package com.musiclibrary.service.importer;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fast recursive directory and file scanner using Java NIO Files.walkFileTree.
 * Supports extension filtering, hidden file/folder exclusion, cancellation tokens,
 * and robust I/O error resilience.
 */
public class FileScanner {

    /**
     * Default supported audio file extensions (lowercase with leading dot).
     */
    public static final Set<String> DEFAULT_SUPPORTED_EXTENSIONS = Set.of(
            ".mp3", ".wav", ".m4a", ".flac"
    );

    private final Set<String> supportedExtensions;

    /**
     * Constructs a FileScanner with default supported extensions (.mp3, .wav, .m4a, .flac).
     */
    public FileScanner() {
        this(DEFAULT_SUPPORTED_EXTENSIONS);
    }

    /**
     * Constructs a FileScanner with custom supported extensions.
     *
     * @param extensions collection of extensions (e.g., "mp3", ".flac")
     */
    public FileScanner(Collection<String> extensions) {
        this.supportedExtensions = normalizeExtensions(extensions);
    }

    private static Set<String> normalizeExtensions(Collection<String> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return DEFAULT_SUPPORTED_EXTENSIONS;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String ext : extensions) {
            if (ext != null && !ext.isBlank()) {
                String clean = ext.trim().toLowerCase(Locale.ROOT);
                if (!clean.startsWith(".")) {
                    clean = "." + clean;
                }
                normalized.add(clean);
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    /**
     * Returns an unmodifiable set of supported file extensions (lowercase, starting with dot).
     *
     * @return supported extensions set
     */
    public Set<String> getSupportedExtensions() {
        return supportedExtensions;
    }

    /**
     * Checks if the given path has a supported audio extension.
     *
     * @param path file path to check
     * @return true if extension is supported, false otherwise
     */
    public boolean isSupported(Path path) {
        if (path == null) {
            return false;
        }
        String fileName = path.getFileName() != null ? path.getFileName().toString() : "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) {
            return false;
        }
        String ext = fileName.substring(dotIndex).toLowerCase(Locale.ROOT);
        return supportedExtensions.contains(ext);
    }

    /**
     * Scans a single path (file or directory) recursively.
     *
     * @param rootPath directory or file path
     * @return list of found audio file paths
     */
    public List<Path> scan(Path rootPath) {
        return scan(rootPath, null);
    }

    /**
     * Scans a single path (file or directory) recursively with cancellation support.
     *
     * @param rootPath   directory or file path
     * @param cancelFlag optional cancellation flag
     * @return list of found audio file paths
     */
    public List<Path> scan(Path rootPath, AtomicBoolean cancelFlag) {
        if (rootPath == null || !Files.exists(rootPath)) {
            return Collections.emptyList();
        }
        return scanAll(List.of(rootPath), cancelFlag);
    }

    /**
     * Scans multiple paths (files or directories mixed) recursively.
     *
     * @param paths collection of root paths
     * @return list of found audio file paths
     */
    public List<Path> scanAll(Collection<Path> paths) {
        return scanAll(paths, null);
    }

    /**
     * Scans multiple paths (files or directories mixed) recursively with cancellation support.
     *
     * @param paths      collection of root paths
     * @param cancelFlag optional cancellation flag
     * @return deduplicated list of found audio file paths
     */
    public List<Path> scanAll(Collection<Path> paths, AtomicBoolean cancelFlag) {
        if (paths == null || paths.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Path> results = new LinkedHashSet<>();

        for (Path root : paths) {
            if (isCancelled(cancelFlag)) {
                break;
            }
            if (root == null || !Files.exists(root)) {
                continue;
            }

            try {
                if (Files.isRegularFile(root)) {
                    if (!isHidden(root) && isSupported(root)) {
                        results.add(root.toAbsolutePath().normalize());
                    }
                } else if (Files.isDirectory(root)) {
                    if (!isHidden(root)) {
                        walkDirectory(root, results, cancelFlag);
                    }
                }
            } catch (Exception e) {
                // Continue scanning even if inspecting a specific path fails
            }
        }

        return new ArrayList<>(results);
    }

    private void walkDirectory(Path dir, Set<Path> results, AtomicBoolean cancelFlag) {
        try {
            Files.walkFileTree(dir, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path subDir, BasicFileAttributes attrs) {
                    if (isCancelled(cancelFlag)) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (!Objects.equals(subDir, dir) && isHidden(subDir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (isCancelled(cancelFlag)) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (attrs.isRegularFile() && !isHidden(file) && isSupported(file)) {
                        results.add(file.toAbsolutePath().normalize());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // Log or ignore permission errors and continue scanning
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // Keep scanning other directories
        }
    }

    private static boolean isCancelled(AtomicBoolean cancelFlag) {
        return cancelFlag != null && cancelFlag.get();
    }

    /**
     * Checks if a file or directory is hidden (dot-prefixed or OS hidden attribute).
     */
    private static boolean isHidden(Path path) {
        try {
            Path fileName = path.getFileName();
            if (fileName != null && fileName.toString().startsWith(".")) {
                return true;
            }
            return Files.isHidden(path);
        } catch (Exception e) {
            return false;
        }
    }
}
