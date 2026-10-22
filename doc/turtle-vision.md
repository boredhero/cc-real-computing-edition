# Turtle vision and targeted interactions

Available in CC: Real Computing Edition `1.120.2-rce.0.6.0-SNAPSHOT`.
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
- `entities`: visible mobs, players, dropped items and other entities (see below).
- `blocks`: a list of visible non-air blocks, with relative `x`, `y`, `z` and
  the usual `turtle.inspect()` details (`name`, `state`, `tags`, and registered
  detail providers).

Vision covers all directions, without a lighting requirement. It uses Minecraft
block outline ray tests from the turtle's centre, sampling each block outline's bounding-box centre
and six faces. This is an approximation of visibility, not a rendered camera:
transparent blocks with solid outlines occlude rays, fluids do not occlude rays,
and small exposed corners may be missed. Air, unloaded blocks, and the turtle
itself are omitted. Observation never loads chunks. Invisible entities and
spectators are omitted. Block-entity inventory/NBT changes are not tracked.

Block observations are cached until relevant world state changes. Entity details
are refreshed from the local loaded-entity index on every observe call. The first call also
subscribes to `turtle_vision` events. Block-state mutations (including growth),
turtle movement, turning, nearby chunk availability, entity movement/removal, and
tracked entity-data changes (such as health and invisibility) invalidate the view.
Entity-only changes reuse the cached block list; they do not trigger a block rescan. No full
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

- `turtle.digAt(x, y, z, options?)` breaks the specified block with an equipped built-in
  turtle tool. Drops go into the turtle inventory, and tool suitability and
  durability rules still apply. Third-party tool upgrades need explicit support;
  they are not invoked with a fabricated position. Unlike legacy `dig`, this
  command does not first attempt to till/strip the block.
- `turtle.placeAt(x, y, z, options?)` places the selected block item into exactly that cell.
  It chooses a visible support face, using the support's actual outline shape
  (including farmland), or replaces a visible replaceable block. It does not
  right-click the support block's UI. Seeds are block items and work here.
- `turtle.useAt(x, y, z, options?)` right-clicks the specified block with the selected item,
  or an empty hand. This includes ordinary block actions such as levers and item
  use such as hoeing. It does not restrict empty-hand actions to the legacy
  turtle block-use whitelist. Opened menus are closed after the action; this
  does not retain an open GUI. Use the inventory methods below for container slots.

`options` accepts `{ face = "up", sneak = true }`. Faces may be `front`, `back`,
`left`, `right`, `up`, `down` relative to the turtle, or world `north`, `south`,
`east`, `west`. An explicit face must actually be visible: selecting a far face
does not let the turtle reach through a block. For `placeAt`, `face` means the
support face clicked to place into the destination cell. Sneaking affects use
and placement; with a nonempty hand it bypasses ordinary block activation just
as secondary use does for a player. Equipped-tool digging retains its normal
break semantics.

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

## Entity vision and actions

Each `view.entities` entry has `uuid`, `name`, `displayName`, `kind`, relative
`x/y/z`, and registered entity detail fields. Positions are continuous coordinates
of the entity's feet relative to the turtle's centre, unlike integer block offsets.
`kind` is `player`, `living`, `item`, or `entity`. Living entities include health;
dropped items include an `item` table with stack details. Visibility samples the
entity bounding box against block outline rays. Other entities do not occlude it.
There is no lighting requirement and no access to entities in unloaded chunks.

```lua
local view = turtle.observe(4)
for _, entity in ipairs(view.entities) do
    if entity.name == "minecraft:sheep" then
        -- Select wheat to feed, or shears to shear, before interacting.
        local ok, reason = turtle.useEntity(entity.uuid)
    elseif entity.kind == "item" then
        turtle.collectEntity(entity.uuid)
    end
end
```

- `turtle.useEntity(uuid, sneak?)`: normal entity right-click using the selected
  item or empty hand. Supports vanilla feeding, shearing and similar interactions,
  plus modded entity interactions that support fake players. Opened entity GUIs
  are closed; mounts/riding and GUI buttons are not automated.
- `turtle.attackEntity(uuid)`: attack with the first equipped built-in turtle tool.
  Preserves its damage, durability, enchantments and drop handling. PvP/server/team
  rules and attack hooks apply. Third-party tool upgrades need explicit support.
- `turtle.collectEntity(uuid)`: normal player pickup of a dropped item, including
  ownership, pickup delay and loader hooks. Requires room for the whole stack;
  it cannot use extra fake-player inventory slots as hidden storage.

These return `true` or `false, reason`. UUIDs are re-resolved at execution time;
removed, unloaded, invisible, obstructed or out-of-reach targets fail safely.
Entity reach is 4.5 blocks to a visible point on its bounding box. Right-click
cancellation cannot fall through to another interaction attempt.

## Container inventory operations

```lua
local ok, inventory = turtle.inspectInventoryAt(1, 0, 1)
if ok then
    print(inventory.size)
    for slot, item in pairs(inventory.items) do print(slot, item.name, item.count) end
end
-- Push from the selected turtle slot into container slot 1:
local ok, moved = turtle.pushItemsAt(1, 0, 1, 16, 1)
-- Pull up to 8 items, searching all container slots:
local ok, moved = turtle.pullItemsAt(1, 0, 1, 8)
```

`inspectInventoryAt(x,y,z,options?)` returns `true, {size=..., items=...}`.
`pushItemsAt` and `pullItemsAt` accept `(x,y,z,count?,slot?,options?)`, returning
`true, moved` or `false, reason`. Counts are 0–64, default 64. Slot indices are
one-based **menu slots excluding the player's inventory**, not peripheral or
sided capability slot indices. Omitting the slot searches in menu order.
Pulling fills the turtle from its selected slot onward.

Each operation performs the block interaction with the selected item, opens the
real server menu provider, validates it, obeys slot insert/pickup/output callbacks,
and closes it afterward. Vanilla locks and protection hooks apply; this is not
remote access through an inventory capability. Output slots which cannot accept
items require transferring their full output stack, avoiding partial recipe
consumption. Blocks without an accessible menu fail. Machines with custom GUI
buttons, client-only logic or nonstandard menu providers may need adapters.
A wrong target can perform its normal right-click action, so inspect block type
before treating it as a container.

## Holding and charging items

```lua
-- Selected slot: bow; arrows elsewhere in turtle inventory.
assert(turtle.useItem(20, 0, -15)) -- ticks, relative yaw, pitch (negative = up)
while turtle.isUsingItem() do sleep(0.05) end
-- Event-driven programs can also handle turtle_item_use in their event loop.
```

`useItem(ticks, yaw?, pitch?, sneak?)` begins the selected item's normal air-use.
Duration is 1–1200 **server ticks** (20 ticks per second at normal TPS). Yaw is
relative to facing, -180 to 180 degrees; pitch is -90 to 90, positive downward.
Aim is fixed for that action. The origin is just outside the turtle's surface,
so projectiles and item rays do not hit the turtle itself. Vanilla item-specific
reach, cooldowns, ammo, consumption and loader use hooks still apply.

The method returns when use starts. Charging progresses one tick at a time through
Minecraft's normal use-tick and completion hooks. At the deadline it releases the
item (firing a bow, for example); consumables can finish sooner. Completion queues
`turtle_item_use(reason, elapsedTicks)` with `completed`, `released`, `cancelled`
or `failed`. `isUsingItem()` permits polling if an event is lost. A successful
start or release does not guarantee a projectile hit or a mod-specific outcome.

`cancelItemUse()` stops holding without intentionally firing the release action.
Shutdown/unload cancels use. Active use is not resumed after save/reload. Inventory
changes are synchronised after every tick; external inserts remain intact, and
removing or changing the held item interrupts use according to vanilla matching rules. Other turtle commands fail while
holding, so programs should finish or cancel before moving, turning or selecting
another slot. Observation and status reads remain available. This supports
server-side items which implement normal air-use; arbitrary mod GUIs and
client-input-specific abilities are outside this API.

## Validation

Integration tests cover entity visibility/occlusion and invalidation, feeding,
shearing, pickups and full inventories, attack/use cancellation, explicit faces,
sneak-use, locked/protected chest transfers, bow charging and release, cancellation,
and consumable completion. Existing targeted farming, vision and UUID tests remain
in the suite. Full FTB Evolution gameplay and individual claim-mod certification
still require testing in a separate pack instance; no live server is modified.

The final NeoForge run passed all eight new interaction tests and 124 of 125 required
game tests overall. The existing intermittent double-chest test and optional treasure-loot
test failed; all 125 required tests passed in the preceding run. Unit suites passed
1,650 tests (13 skipped). Full-pack testing is pending.

## Mod-defined machine controls

`turtle.getInteractionsAt(x, y, z, options?)` discovers registered adapters and
their state/actions. `turtle.interactAt(x, y, z, adapter, action, arguments?, options?)`
executes a named action through the same reach, visibility and protection checks.
See [the adapter API](turtle-adapters.md) for Lua examples and Java registration.
