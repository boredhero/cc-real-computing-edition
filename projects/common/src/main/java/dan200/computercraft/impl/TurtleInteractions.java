// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.impl;

import dan200.computercraft.api.turtle.TurtleInteraction;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Process-wide adapter registration, with deterministic discovery order and no silent overrides. */
public final class TurtleInteractions {
    private static final Map<ResourceLocation, TurtleInteraction> adapters = new TreeMap<>();

    private TurtleInteractions() {
    }

    public static synchronized void register(ResourceLocation id, TurtleInteraction adapter) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(adapter, "adapter");
        if (adapters.putIfAbsent(id, adapter) != null) throw new IllegalArgumentException("Duplicate turtle interaction: " + id);
    }

    public static synchronized Map<ResourceLocation, TurtleInteraction> snapshot() {
        return java.util.Collections.unmodifiableMap(new TreeMap<>(adapters));
    }
}
