# Machine interaction adapters

Available in CC: Real Computing Edition `1.120.2-rce.0.7.0-SNAPSHOT`.

Mods can expose machine state and named controls to turtles through the public
`TurtleInteraction` interface. A recipe selector, start button or operating-mode
selector can call the same server-side method as the machine's GUI. No fake
client, screen coordinates, packet replay or GUI automation is required.

Standard containers still work through `inspectInventoryAt`, `pushItemsAt` and
`pullItemsAt` without this API. Adapters are for controls beyond inventory slots.
Installing this release does not automatically add adapters for other mods.

## Lua interface

```lua
local ok, adapters = turtle.getInteractionsAt(0, 0, 1)
if not ok then error(adapters) end
for id, adapter in pairs(adapters) do
    print(id, textutils.serialize(adapter.actions))
    print(textutils.serialize(adapter.state))
end

-- Example when the bundled example mod is loaded and a lever is ahead:
local success, result = turtle.interactAt(
    0, 0, 1, "examplemod:lever", "toggle", {}, { sneak = true }
)
if success then print("Powered:", result.powered) else print(result) end
```

- `getInteractionsAt(x, y, z, options?)` returns `true, adapters`, keyed by
  namespaced adapter ID. Each entry has `actions` (a sorted list) and `state`.
  A supported, accessible target with no registered adapter returns `true, {}`.
- `interactAt(x, y, z, adapter, action, arguments?, options?)` returns
  `true, resultTable`, or `false, reason`. Invalid Lua argument types, coordinates,
  adapter ID syntax or oversized payloads raise an argument error.
- Both use the ordinary turtle command queue, including held-item command gating.
  Coordinates are integer right/up/forward offsets; spherical reach is 4.5 blocks.
  `options` supports the same `face` and `sneak` fields as `useAt`.
- At execution, the target must still be loaded, visible and in reach. Protection
  hooks must allow block interaction. The adapter's `supports` and `actions`
  methods are checked again, so an old discovery is never an authorization token.
- Arguments/results are detached snapshots. Values support strings, finite numbers,
  booleans, maps with scalar keys, and Java lists (Lua arrays). No null values,
  live Minecraft objects, callbacks or userdata. Limits per snapshot are 4,096
  values (including table keys), 16 nesting levels and 65,536 string characters.
  Discovery has one shared output limit across all adapters. Lua tables arrive
  as maps, including numeric keys for array-like arguments.

## Registering an adapter

Compile against this fork's matching API artifacts; the interface is not in
upstream CC:Tweaked. Register once during your mod's normal initialization after
CC's API is available (NeoForge common setup or Fabric initialization):

```java
ComputerCraftAPI.registerTurtleInteraction(
    ResourceLocation.fromNamespaceAndPath("yourmod", "machine"),
    new YourMachineInteraction()
);
```

IDs must be unique; duplicates throw rather than silently replace an adapter.
Registration is thread-safe. Discovery order is deterministic by adapter ID.

Implement these methods:

```java
boolean supports(TurtleInteractionContext context);
Map<String, ?> inspect(TurtleInteractionContext context) throws LuaException;
Set<String> actions(TurtleInteractionContext context);
Map<String, ?> execute(TurtleInteractionContext context,
                       String action, Map<?, ?> arguments) throws LuaException;
```

The [compiled lever example](../projects/common/src/examples/java/com/example/examplemod/ExampleTurtleInteraction.java)
shows a complete implementation. Its registration lives in
[ExampleMod](../projects/common/src/examples/java/com/example/examplemod/ExampleMod.java).
It is included only in the example mod, not enabled by the production mod.

`TurtleInteractionContext` provides the server level, target block position,
visible hit face, turtle access, and owner-aware fake player. The player's main
hand contains the selected turtle item and its sneak state matches the request.
Use the **player inventory** for item consumption/returns: it is synchronized
back to the turtle afterwards. Mutating the turtle inventory directly while the
player inventory is loaded can be overwritten. Do not retain the context or
player beyond the callback, or enqueue asynchronous work using them.

All callbacks run on the server thread. Keep them short; expensive calculations
belong outside these callbacks. `supports`, `inspect` and `actions` must be
read-only. Discovery fires the platform right-click hook for protection, but
does not itself call item-use or block-use behavior. Event listeners may have
their own side effects. Transient menus opened by an adapter are closed on exit;
this API does not maintain a GUI session or call arbitrary screen buttons.

The fork checks reach/LOS, spawn protection and loader interaction hooks before
calling an adapter. **Your adapter must also enforce its own machine locks,
ownership and action-specific permissions**, just like its GUI packet handler.
Validate arguments and permissions before changing state. Throw `LuaException`
for a useful rejection message. Runtime exceptions are logged and returned as a
generic failure; inventory and menu cleanup still runs. Actions are not
transactional: an exception or oversized result after mutation does not roll it
back. Do not blindly retry failed actions with side effects.

For a custom GUI, extract its server-side action into a shared method and call
that method from both the packet handler and this adapter. The adapter supplies
a fake player, so machines which intentionally require a real connected player
may continue to reject it. Existing peripherals remain useful for remotely
connected machines; adapters provide local, player-like interaction.
