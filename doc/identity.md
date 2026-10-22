# Persistent identity and denser terminal UIs

Available in `1.120.2-rce.0.7.0-SNAPSHOT` for Minecraft 1.21.1 / NeoForge.

## Device UUIDs

Computer blocks (normal, advanced and command computers), turtles and pocket computers have
persistent UUID identities. They are separate from CC's existing runtime instance
UUID, which changes when a server computer is recreated.

```lua
print(os.getComputerUUID())
print(os.getComputerID()) -- existing numeric ID remains compatible
print(os.getComputerLabel()) -- existing human-readable label

-- For an adjacent or wired computer/turtle peripheral:
print(peripheral.call("left", "getUUID"))
```

The `id` program displays both IDs. The peripheral method also works when the
other computer is powered off. Standalone core embeddings without an assigned
persistent identity still return nil. Pocket identity is stored in the item
component and survives transfer, upgrades and save/reload.

Legacy blocks are assigned a random UUID when first ticked or accessed. Their
numeric ID, label and programs are preserved; storage migrates as described below. The UUID is saved
in block NBT (`ComputerUUID`) and the `computercraft:computer_uuid` item component.
Breaking/picking up the device and placing it elsewhere preserves it. Turtle
movement, including dimension changes through the normal turtle movement path,
transfers the same identity. Upgrade recipes which copy device components retain
it too.

The overworld saved-data file `data/computercraft_device_identities.dat` records
which placed location owns each UUID, including unloaded chunks. A copied item
or block requesting an already-claimed UUID at another location receives a new
UUID when ticked or accessed. Removing a device releases its location claim;
chunk unload and server shutdown do not. If identical items exist while no block
claims their UUID, the first placed/accessed copy claims it and later copies are
assigned fresh UUIDs.

### Pocket copies

Pockets claim an identity when first ticked in a player's inventory or accessed.
A copied item carrying the same UUID or runtime reference as an active pocket gets
a fresh UUID and independent running computer. Moving the original to another
slot, player, item entity or lectern preserves its identity and running state.
A persistent activation token now separates offline copies too. Whenever a new
pocket runtime starts (or a transfer reuses a still-live runtime), the item receives a fresh token and the identity registry
records it. A separately saved item with an older (or missing) token receives a
new UUID and independent storage. The registry survives server restarts; an
ordinary transfer, save/reload or upgrade carrying the latest components retains
its UUID. Legacy pockets without tokens migrate on their first activation.

For byte-identical copies, **the first activated copy keeps the existing UUID**;
subsequent copies split when activated, even if the first is already offline.
There is no way to identify which identical item a person considers the original.
In particular, if a hidden original has not been activated since duplication,
the first copy may use its existing files until the hidden item is activated and
split. Whole-world copies retain identities. Tokens are duplicate detection,
not authentication or a transaction spanning world/player saves: restore the
identity registry and inventories together, and preserve both UUID and token
components in transfer/upgrade recipes.

Mod authors performing deliberate duplication should use
`ComputerCraftAPI.copyPocketComputer(serverLevel, sourceStack)` on the server
thread. It returns an independent item with a fresh UUID immediately, preserves
label/configuration, removes the runtime reference and records the disk source.
Do not use it for transfers or upgrades. Copying files still occurs at first boot,
not at the API call. This explicit path also avoids the first-activation ambiguity.

## UUID storage and migration

The internal filesystem now uses `computercraft/computer/uuid/<uuid>/`. On first
boot, an existing device copies its old `computercraft/computer/<numeric-id>/`
files into this directory. The legacy directory is retained untouched and is
never used again once that UUID directory exists. Numeric IDs and labels remain
available for compatibility, but no longer select the internal disk for devices
with UUIDs. Removable floppy disks retain their existing disk IDs and directories.

When a placed clone or active pocket copy receives a fresh UUID, its first boot
copies the source UUID's files, then all writes and deletes are independent.
If the source has not booted yet, migration follows its recorded clone ancestry,
falling back to the legacy numeric directory. Copies made before this release,
which already have distinct UUIDs but no recorded source, each migrate from their
legacy directory. Copying is ordinary file copying, not a live shared mount.
The snapshot is taken at first boot, not item duplication time; stop the source
computer first if a consistent snapshot of actively changing files is required.

Migration uses a temporary sibling directory and an atomic rename. A failed copy
is never mounted as a completed migration and may be retried; existing UUID
files are never overwritten. Non-regular files (including symlinks) are rejected.
A failed cleanup can leave a `.migrate-*` directory, which is never mounted.
Migration needs enough disk space for an independent copy. Backups and external
server tooling must include/use the new UUID directories. Downgrading does not
copy current UUID files back into the retained legacy directories.

The single-player computer-folder link now opens UUID storage. Its explicit
command accepts either a UUID or a numeric ID; numeric IDs open legacy folders.
Raw modem channels and wired peripheral names retain upstream numeric semantics.

## UUID networking

```lua
rednet.open("back")
print(os.getComputerUUID())
rednet.host("fleet", "harvester") -- same registration for both discovery APIs
local target = rednet.lookupUUID("fleet", "harvester")
if target then rednet.send(target, { command = "harvest" }, "fleet") end
local sender, message, protocol = rednet.receive("fleet")
rednet.send(sender, { ok = true }, protocol)
rednet.broadcastUUID({ status = "ready" }, "fleet")
```

`rednet.send` accepts a numeric ID or a canonical UUID string (case-insensitive).
UUID-targeted traffic returns the sender UUID from `rednet.receive` and
`rednet_message`; numeric traffic still returns numeric IDs. Programs consuming
both types should handle both. `rednet.lookupUUID(protocol, hostname?, timeout?)`
returns UUIDs using the same conventions as `lookup`. `broadcastUUID` broadcasts
with UUID senders; `broadcast` and `lookup` retain numeric behavior.

UUID messaging uses `rednet.CHANNEL_UUID` (65532), opened alongside normal rednet
channels, with full destination UUID filtering. Computers sharing a numeric ID
can exchange UUID messages without being mistaken for each other. Older listeners
do not receive UUID traffic. Repeaters must run this release's `repeat` program;
older repeaters do not understand the UUID channel. Modem range, dimensions and
network connectivity still apply. UUIDs are addresses, not authentication:
messages can still be intercepted or spoofed.

Back up the identity saved-data file along with chunks, inventories and computer
directories. Restoring an entire world restores its identities. A separate copy
of a world deliberately contains the same UUIDs; no cross-server authority is
consulted. Imported devices keep their UUID when unclaimed in the destination,
or receive a new one on collision. Partial restores and offline editors which
move/remove blocks without the normal lifecycle can leave stale claims; such
imports may receive new UUIDs. Do not delete the registry while devices are placed
if duplicate detection across unloaded chunks matters.

## Terminal UI density

| Terminal | Previous | New default |
| --- | --- | --- |
| Computer block | 51 × 19 | 61 × 23 |
| Turtle | 39 × 13 | 47 × 16 |

These are character grids in the computer/turtle UI. Monitor blocks and pocket
terminal sizes are unchanged. Turtle terminal text scales to fit the existing UI
area, preserving inventory slots and buttons. Computer windows scale their
terminal down when needed to fit the available GUI area. Mouse clicks, drags,
releases and scrolling use the same scale as rendering.

Existing explicit server configuration is respected. If a world already has
`[term_sizes.computer]` set to `width = 51` and `height = 19`, change those values
to `61` and `23` in its CC server config when installing the release to use the new
computer default. Explicit per-item terminal-size components are also preserved.
This change does not edit any live server configuration. Turtle size uses the new
47 × 16 grid directly. Programs should query `term.getSize()` instead of assuming
a fixed grid. The font remains the existing CC bitmap font; this is denser text,
not a new graphics API.

## Validation

Tests cover legacy file migration, independent clone writes/deletes, failed-copy
retry, saved source ancestry, pocket transfer/copy/reload, UUID routing with
identical numeric IDs, discovery through repeaters, and legacy listener isolation.
They also cover migration without changing the numeric ID, UUID persistence in NBT
and dropped item components, replacement after pickup, distinct IDs for placed
copies, saved registry collision checks, movement, and Lua/peripheral UUID access.
A terminal widget test verifies that scaled mouse events reach the correct first
and last character cells. Client Java compilation validates both UI paths;
visual inspection in a running client and full Evolution-pack qualification
remain outstanding.
