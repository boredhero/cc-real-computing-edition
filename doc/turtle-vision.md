# Turtle vision and targeted interactions

Available in CC: Real Computing Edition `1.120.2-rce.0.3.0-SNAPSHOT`.
These APIs do not require a scanner peripheral or change the existing turtle commands.

## Coordinates and reach

Offsets are integer block coordinates relative to the turtle at execution time:
`x` is right, `y` is up, and `z` is forward. Thus `(1, 0, 1)` is the forward-right
diagonal. Turning changes the coordinate frame. The turtle does not move or spend
movement fuel when observing or interacting.

Interactions have a 4.5-block spherical reach, measured from the turtle's block
centre. Both the target cell centre and clicked face must be within reach. The
turtle's own cell cannot be targeted. Unloaded or out-of-world targets fail.

## `turtle.observe(radius)`

Returns a snapshot table directly (no leading success boolean):

```lua
local view = turtle.observe(4)
for _, block in ipairs(view.blocks) do
    print(block.x, block.y, block.z, block.name)
    if block.name == "minecraft:wheat" then
        print("Crop age:", block.state.age)
    end
end
```

`radius` must be an integer from 1 to 8. The snapshot contains:

- `revision`: a monotonically increasing observation/invalidation number.
- `radius`: the requested radius.
- `facing`: the turtle's current world-facing direction.
- `blocks`: a list of visible non-air blocks, with relative `x`, `y`, `z` and
  the usual `turtle.inspect()` details (`name`, `state`, `tags`, and registered
  detail providers).

Vision covers all directions, without a lighting requirement. It uses Minecraft
block outline ray tests from the turtle's centre, sampling each block outline's bounding-box centre
and six faces. This is an approximation of visibility, not a rendered camera:
transparent blocks with solid outlines occlude rays, fluids do not occlude rays,
and small exposed corners may be missed. Air, unloaded blocks, and the turtle
itself are omitted. Observation never loads chunks. Entities and block-entity
inventory/NBT changes are not part of this API.

Observations are cached until relevant world state changes. The first call also
subscribes to `turtle_vision` events. Block-state mutations (including growth),
movement, turning, and nearby chunk availability invalidate the cache. No full
world scan runs on idle ticks; the next `observe` refreshes an invalidated view.
At most one event is sent until the program observes again:

```lua
local view = turtle.observe(4)
while true do
    -- Process the current view here, or submit it to a compute worker.
    local _, revision = os.pullEvent("turtle_vision")
    if revision > view.revision then view = turtle.observe(4) end
end
```

The event means the view **may** have changed: hidden block changes may invalidate
it too. Events carry only the revision, not block data. Multiple changes coalesce.
Like other CC events, notifications can be dropped if the event queue is full;
a later explicit `observe` still returns the current view. Call
`turtle.unobserve()` to stop notifications and release the cache. Shutdown clears
the subscription. Snapshots sent to compute workers remain snapshots; re-check
an action's result because the world may have changed meanwhile.

## Targeted actions

All three return `true` on success or `false, reason` on failure. Invalid numeric
arguments raise a Lua error. Actions run through the existing turtle command
queue on the server thread, with their normal animation delay.

- `turtle.digAt(x, y, z)` breaks the specified block with an equipped built-in
  turtle tool. Drops go into the turtle inventory, and tool suitability and
  durability rules still apply. Third-party tool upgrades need explicit support;
  they are not invoked with a fabricated position. Unlike legacy `dig`, this
  command does not first attempt to till/strip the block.
- `turtle.placeAt(x, y, z)` places the selected block item into exactly that cell.
  It chooses a visible support face, using the support's actual outline shape
  (including farmland), or replaces a visible replaceable block. It does not
  right-click the support block's UI. Seeds are block items and work here.
- `turtle.useAt(x, y, z)` right-clicks the specified block with the selected item,
  or an empty hand. This includes ordinary block actions such as levers and item
  use such as hoeing. It does not restrict empty-hand actions to the legacy
  turtle block-use whitelist. Opened menus are closed after the action; this
  does not provide a GUI automation API. Entity interaction, sustained item use,
  air-use items, sneaking, and explicit face selection are future work.

For example, harvest and replant a mature diagonal crop without moving or
breaking a neighbouring plant:

```lua
-- Select a slot containing seeds first.
assert(turtle.digAt(1, 0, 1))
assert(turtle.placeAt(1, 0, 1))
```

The fake player retains the turtle's existing owner identity. Breaks use the
normal player game-mode destruction path; use and placement invoke the platform
right-click hooks, and item placement retains the item/loader placement hooks.
Spawn protection and line of sight are checked. Protection mods can deny these
actions through those hooks; they are not bypassed by teleporting the fake player
nearer the target. Individual protection mods and the complete FTB Evolution
pack still need integration testing.

## Validation

The NeoForge game tests exercise diagonal crop harvesting/replanting, empty-hand
lever use, range rejection, actual mutation-driven cache invalidation,
occlusion, turning, unsubscribe, and cancelled break/use/place events with item
preservation. Run them with `./gradlew :forge:runGametest`. All three new tests
passed on the final source. The broader suite has an intermittent failure in the
existing `inventory_test.double_chest_size` test and an optional treasure-loot
failure; these are recorded rather than disabled. Core and NeoForge unit suites
passed (1,637 passed, 13 skipped).
