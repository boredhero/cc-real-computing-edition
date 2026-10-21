// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.computer.core;

import dan200.computercraft.test.shared.WithMinecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

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
    void cannotMoveAnotherClaim() {
        var identities = new ComputerIdentities();
        var uuid = identities.claim(null, "one");
        assertThrows(IllegalStateException.class, () -> identities.move(uuid, "wrong", "two"));
        assertEquals(uuid, identities.claim(uuid, "one"));
    }
}
