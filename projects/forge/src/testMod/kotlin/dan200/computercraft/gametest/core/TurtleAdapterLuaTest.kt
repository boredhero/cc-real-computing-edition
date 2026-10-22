// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.gametest.core

import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.gametest.api.sequence
import dan200.computercraft.gametest.api.thenOnComputer
import dan200.computercraft.shared.turtle.apis.TurtleAPI
import dan200.computercraft.test.core.computer.getApi
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.junit.jupiter.api.Assertions.*
import java.util.Optional

@GameTestHolder("cctest")
@PrefixGameTestTemplate(false)
class TurtleAdapterLuaTest {
    @GameTest(template = "turtle_adapter_lua")
    fun queued_adapter_calls(helper: GameTestHelper) = helper.sequence {
        thenExecute { helper.setBlock(BlockPos(4, 3, 6), Blocks.STONE) }
        thenOnComputer {
            val api = getApi<TurtleAPI>()
            val discovery = api.getInteractionsAt(0.0, 0.0, 2.0, Optional.empty()).await()!!
            assertEquals(true, discovery[0])
            assertTrue((discovery[1] as Map<*, *>).containsKey("cctest:machine"))
            assertThrows(LuaException::class.java) {
                api.interactAt(0.0, 0.0, 2.0, "missing_namespace", "set", Optional.empty(), Optional.empty())
            }
            assertThrows(LuaException::class.java) {
                api.getInteractionsAt(Double.NaN, 0.0, 2.0, Optional.empty())
            }
            val result = api.interactAt(
                0.0, 0.0, 2.0, "cctest:machine", "set", Optional.of(mapOf("confirm" to true)),
                Optional.of(mapOf("face" to "back", "sneak" to true)),
            ).await()!!
            assertEquals(true, result[0])
            assertEquals(true, (result[1] as Map<*, *>)["sneak"])
        }
        thenExecute { helper.assertBlockPresent(Blocks.GOLD_BLOCK, BlockPos(4, 3, 6)) }
    }
}
