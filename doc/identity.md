# Persistent identity and denser terminal UIs

Available in `1.120.2-rce.0.4.0-SNAPSHOT` for Minecraft 1.21.1 / NeoForge.

## Device UUIDs

Computer blocks (normal, advanced and command computers) and turtles now have
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
other computer is powered off. Hosts without persistent block identities,
including pocket computers and standalone core embeddings, return nil from
`os.getComputerUUID()` in this release. Pocket UUID support is still future work.

Legacy blocks are assigned a random UUID when first ticked or accessed. Their
numeric ID, label, programs and disk directory are preserved. The UUID is saved
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

**Storage and networking compatibility:** numeric IDs still select the existing
`computercraft/computer/<id>` directory and rednet/modem naming still uses numeric
IDs. As with upstream, copying a device's numeric ID can share its existing disk
storage. Assigning a fresh UUID to a clone does not duplicate that disk or change
its numeric alias. UUID-based storage and addressing are not implemented here.
UUIDs identify devices; they are not authentication credentials.

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

Tests cover migration without changing the numeric ID, UUID persistence in NBT
and dropped item components, replacement after pickup, distinct IDs for placed
copies, saved registry collision checks, movement, and Lua/peripheral UUID access.
A terminal widget test verifies that scaled mouse events reach the correct first
and last character cells. Client Java compilation validates both UI paths;
visual inspection in a running client and full Evolution-pack qualification
remain outstanding.
