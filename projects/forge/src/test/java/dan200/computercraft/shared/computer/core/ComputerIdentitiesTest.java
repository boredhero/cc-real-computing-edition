// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.computer.core;

import dan200.computercraft.test.shared.WithMinecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@WithMinecraft
class ComputerIdentitiesTest {
    @Test
    void claimsSurviveSaveAndPreventOfflineCollisions() {
        var identities = new ComputerIdentities();
        var original = identities.claim(null, "overworld:one");
        var saved = identities.save(new CompoundTag(), RegistryAccess.EMPTY);
        var restored = ComputerIdentities.load(saved, RegistryAccess.EMPTY);
        assertEquals(original, restored.claim(original, "overworld:one"));
        assertNotEquals(original, restored.claim(original, "overworld:two"));
        restored.move(original, "overworld:one", "nether:three");
        restored.release(original, "overworld:one");
        assertNotEquals(original, restored.claim(original, "overworld:four"));
        restored.release(original, "nether:three");
        assertEquals(original, restored.claim(original, "overworld:five"));
    }

    @Test
    void persistsCloneSourcesAfterClaimsAreReleased() {
        var identities = new ComputerIdentities();
        var original = identities.claim(null, "one");
        var copy = identities.claim(original, "two");
        var pocket = identities.copy(copy, "pocket");
        identities.release(original, "one");
        var restored = ComputerIdentities.load(identities.save(new CompoundTag(), RegistryAccess.EMPTY), RegistryAccess.EMPTY);
        assertEquals(List.of(copy, original), restored.storageSources(pocket));
        assertEquals(pocket, restored.claim(pocket, "pocket"));
        assertNotEquals(pocket, restored.claim(pocket, "block"));
    }

    @Test
    void cannotMoveAnotherClaim() {
        var identities = new ComputerIdentities();
        var uuid = identities.claim(null, "one");
        assertThrows(IllegalStateException.class, () -> identities.move(uuid, "wrong", "two"));
        assertEquals(uuid, identities.claim(uuid, "one"));
    }
}
