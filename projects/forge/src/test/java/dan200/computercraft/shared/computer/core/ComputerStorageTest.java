// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.computer.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ComputerStorageTest {
    @TempDir
    Path root;

    @Test
    void migratesWithoutRemovingLegacyFilesAndNeverOverwrites() throws IOException {
        var legacy = root.resolve("computer/42");
        Files.createDirectories(legacy.resolve("programs"));
        Files.writeString(legacy.resolve("programs/startup.lua"), "original");
        var uuid = UUID.randomUUID();
        var path = root.resolve(ComputerStorage.prepare(root, uuid, List.of(), 42));
        assertEquals("original", Files.readString(path.resolve("programs/startup.lua")));
        Files.writeString(path.resolve("programs/startup.lua"), "modified");
        assertEquals("original", Files.readString(legacy.resolve("programs/startup.lua")));
        ComputerStorage.prepare(root, uuid, List.of(), 42);
        assertEquals("modified", Files.readString(path.resolve("programs/startup.lua")));
    }

    @Test
    void clonesCopyCurrentUuidStorageAndThenDiverge() throws IOException {
        var uuid = UUID.randomUUID();
        var original = root.resolve(ComputerStorage.prepare(root, uuid, List.of(), 42));
        Files.writeString(original.resolve("data"), "current");
        var copy = root.resolve(ComputerStorage.prepare(root, UUID.randomUUID(), List.of(uuid), 42));
        assertEquals("current", Files.readString(copy.resolve("data")));
        Files.writeString(copy.resolve("data"), "copy");
        assertEquals("current", Files.readString(original.resolve("data")));
        Files.delete(copy.resolve("data"));
        assertTrue(Files.exists(original.resolve("data")));
    }

    @Test
    void followsUnbootedCloneLineage() throws IOException {
        var uuid = UUID.randomUUID();
        var original = root.resolve(ComputerStorage.prepare(root, uuid, List.of(), 42));
        Files.writeString(original.resolve("data"), "ancestor");
        var copy = root.resolve(ComputerStorage.prepare(root, UUID.randomUUID(), List.of(UUID.randomUUID(), uuid), 42));
        assertEquals("ancestor", Files.readString(copy.resolve("data")));
    }

    @Test
    void failedMigrationIsNotPublishedAndCanBeRetried() throws IOException {
        var legacy = root.resolve("computer/42");
        Files.createDirectories(legacy);
        Files.writeString(legacy.resolve("data"), "safe");
        Files.createSymbolicLink(legacy.resolve("link"), legacy.resolve("data"));
        var uuid = UUID.randomUUID();
        assertThrows(UncheckedIOException.class, () -> ComputerStorage.prepare(root, uuid, List.of(), 42));
        assertFalse(Files.exists(root.resolve("computer/uuid/" + uuid)));
        Files.delete(legacy.resolve("link"));
        var migrated = root.resolve(ComputerStorage.prepare(root, uuid, List.of(), 42));
        assertEquals("safe", Files.readString(migrated.resolve("data")));
    }
}
