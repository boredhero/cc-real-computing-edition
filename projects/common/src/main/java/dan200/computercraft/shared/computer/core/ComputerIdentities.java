// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.computer.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent claims prevent copied blocks from impersonating another placed device, even in unloaded chunks. */
public final class ComputerIdentities extends SavedData {
    private final Map<UUID, String> claims = new HashMap<>();
    private final Map<UUID, UUID> pocketTokens = new HashMap<>();
    private final Map<UUID, UUID> storageSources = new HashMap<>();

    public static ComputerIdentities get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
            new Factory<>(ComputerIdentities::new, ComputerIdentities::load, null), "computercraft_device_identities");
    }

    public static String location(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + ":" + pos.asLong();
    }

    public UUID claim(@Nullable UUID requested, String location) {
        var uuid = requested;
        if (uuid == null || (claims.containsKey(uuid) && !location.equals(claims.get(uuid)))) {
            do {
                uuid = UUID.randomUUID();
            } while (claims.containsKey(uuid));
        }
        if (requested != null && !requested.equals(uuid)) storageSources.put(uuid, requested);
        if (!location.equals(claims.put(uuid, location))) setDirty();
        return uuid;
    }

    public boolean isPocketTokenCurrent(@Nullable UUID uuid, @Nullable UUID token) {
        return !pocketTokens.containsKey(uuid) || pocketTokens.get(uuid).equals(token);
    }

    /**
     * A persistent identity and its current mobile activation token.
     * @param uuid The device identity.
     * @param token The current activation token.
     */
    public record PocketClaim(UUID uuid, UUID token) {
    }

    /**
     * Rotate the token when starting a new pocket runtime. A stale serialized item is a copy, even if no
     * other runtime is alive. For indistinguishable copies, the first activated item retains the identity.
     * Missing tokens are accepted only for identities which have never used tokens (legacy migration).
     * @param requested The saved identity, if any.
     * @param token The item's activation token, if any.
     * @param copied Whether this is a known copy rather than a transfer.
     * @return The claimed identity and replacement token.
     */
    public PocketClaim claimPocket(@Nullable UUID requested, @Nullable UUID token, boolean copied) {
        var stale = requested != null && pocketTokens.containsKey(requested) && !pocketTokens.get(requested).equals(token);
        var uuid = requested != null && (copied || stale) ? copy(requested, "pocket") : claim(requested, "pocket");
        var next = UUID.randomUUID();
        pocketTokens.put(uuid, next);
        setDirty();
        return new PocketClaim(uuid, next);
    }

    /**
     * Allocate an independent copy of a mobile device.
     *
     * @param source The original device UUID.
     * @param location The new claim location.
     * @return The independent copy's UUID.
     */
    public UUID copy(UUID source, String location) {
        var uuid = claim(null, location);
        storageSources.put(uuid, source);
        setDirty();
        return uuid;
    }

    /**
     * Capture the source lineage on the server thread before mounting on the computer thread.
     *
     * @param uuid The device being mounted.
     * @return Source UUIDs, nearest ancestor first.
     */
    public List<UUID> storageSources(UUID uuid) {
        var sources = new ArrayList<UUID>();
        sources.add(uuid);
        while ((uuid = storageSources.get(uuid)) != null && !sources.contains(uuid)) sources.add(uuid);
        sources.removeFirst();
        return List.copyOf(sources);
    }

    public void release(@Nullable UUID uuid, String location) {
        if (uuid != null && claims.remove(uuid, location)) setDirty();
    }

    public void move(UUID uuid, String from, String to) {
        if (!from.equals(claims.get(uuid))) throw new IllegalStateException("Cannot move another computer's identity");
        claims.put(uuid, to);
        setDirty();
    }

    static ComputerIdentities load(CompoundTag tag, HolderLookup.Provider registries) {
        var identities = new ComputerIdentities();
        for (var entry : tag.getList("Claims", Tag.TAG_COMPOUND)) {
            var claim = (CompoundTag) entry;
            if (claim.hasUUID("UUID")) identities.claims.put(claim.getUUID("UUID"), claim.getString("Location"));
        }
        for (var entry : tag.getList("StorageSources", Tag.TAG_COMPOUND)) {
            var source = (CompoundTag) entry;
            if (source.hasUUID("UUID") && source.hasUUID("Source")) {
                identities.storageSources.put(source.getUUID("UUID"), source.getUUID("Source"));
            }
        }
        for (var entry : tag.getList("PocketTokens", Tag.TAG_COMPOUND)) {
            var pocket = (CompoundTag) entry;
            if (pocket.hasUUID("UUID") && pocket.hasUUID("Token")) identities.pocketTokens.put(pocket.getUUID("UUID"), pocket.getUUID("Token"));
        }
        return identities;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        claims.forEach((uuid, location) -> {
            var claim = new CompoundTag();
            claim.putUUID("UUID", uuid);
            claim.putString("Location", location);
            list.add(claim);
        });
        tag.put("Claims", list);
        var sources = new ListTag();
        storageSources.forEach((uuid, source) -> {
            var entry = new CompoundTag();
            entry.putUUID("UUID", uuid);
            entry.putUUID("Source", source);
            sources.add(entry);
        });
        tag.put("StorageSources", sources);
        var pockets = new ListTag();
        pocketTokens.forEach((uuid, token) -> {
            var entry = new CompoundTag();
            entry.putUUID("UUID", uuid);
            entry.putUUID("Token", token);
            pockets.add(entry);
        });
        tag.put("PocketTokens", pockets);
        return tag;
    }
}
