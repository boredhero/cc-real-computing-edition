// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.computer.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.UUID;

/** One-time, non-destructive migration and independent copy-on-first-boot storage. */
public final class ComputerStorage {
    private ComputerStorage() {
    }

    /**
     * Publish complete directories only. A failed copy must never expose a partially migrated disk.
     *
     * @param root The world's ComputerCraft storage directory.
     * @param uuid The destination device UUID.
     * @param sources Source UUIDs, nearest ancestor first.
     * @param legacyId The legacy directory to migrate if no UUID source exists.
     * @return The storage path relative to root.
     */
    public static synchronized String prepare(Path root, UUID uuid, List<UUID> sources, int legacyId) {
        var relative = "computer/uuid/" + uuid;
        var directory = root.resolve("computer/uuid");
        var target = root.resolve(relative);
        Path temporary = null;
        try {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                requireDirectory(target);
                return relative;
            }
            Files.createDirectories(directory);
            requireDirectory(directory);
            var source = root.resolve("computer/" + legacyId);
            for (var parent : sources) {
                var candidate = root.resolve("computer/uuid/" + parent);
                if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                    source = candidate;
                    break;
                }
            }
            temporary = Files.createTempDirectory(directory, ".migrate-");
            var destination = temporary;
            if (Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
                requireDirectory(source);
                var origin = source;
                Files.walkFileTree(source, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        Files.createDirectories(destination.resolve(origin.relativize(dir)));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (!attrs.isRegularFile()) throw new IOException("Cannot migrate non-regular file: " + file);
                        Files.copy(file, destination.resolve(origin.relativize(file)));
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            temporary = null;
            return relative;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot initialise computer storage for " + uuid, e);
        } finally {
            if (temporary != null) {
                try {
                    Files.walkFileTree(temporary, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                            if (error != null) throw error;
                            Files.delete(dir);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } catch (IOException ignored) {
                    // Leftover .migrate-* directories are never mounted or used as sources.
                }
            }
        }
    }

    private static void requireDirectory(Path path) throws IOException {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Not a regular directory: " + path);
    }
}
