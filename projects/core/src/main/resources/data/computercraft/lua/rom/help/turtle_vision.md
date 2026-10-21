Turtle vision and targeted actions

view = turtle.observe(radius) returns visible blocks and entities, with type names and x/y/z offsets. Entities include UUIDs; blocks include states and tags. Radius is an integer from 1 to 8.
Coordinates: x right, y up, z forward. Turning changes the frame.

observe subscribes to turtle_vision events carrying a revision number. Call observe again after an event to refresh the cached view. Events coalesce until observed; the event means the view may have changed. turtle.unobserve() stops notifications.

Dig, place or use visible blocks within 4.5 blocks, including diagonals:
  turtle.digAt(x, y, z)
  turtle.placeAt(x, y, z)
  turtle.useAt(x, y, z)

Actions return true or false plus a reason. Digging requires an equipped built-in tool. placeAt uses the selected block or seeds; useAt right-clicks with the selected item or empty hand. Normal protection hooks apply. No chunks are loaded. Vision covers all directions using block outline ray tests; invisible entities are excluded.


Entity interactions (UUIDs from observe().entities):

* turtle.attackEntity(uuid): use an equipped tool.
* turtle.useEntity(uuid, sneak): selected item; feed, shear or interact.
* turtle.collectEntity(uuid): normal pickup rules, with room for the entire stack.

Targeted block calls accept an optional {face="up", sneak=true} table.
Faces are front/back/left/right/up/down, or world compass directions.
The chosen face must be visible. placeAt clicks that face on a support block.

Containers use normal menu slots, excluding player inventory:

* turtle.inspectInventoryAt(x,y,z,options) -> true, {size=...,items=...}
* turtle.pushItemsAt(x,y,z,count,slot,options) -> true, moved
* turtle.pullItemsAt(x,y,z,count,slot,options) -> true, moved

Count defaults to 64; omit slot to search all. Locks and slot rules apply.

Held items:

* turtle.useItem(ticks,yaw,pitch,sneak): real ticks, 1..1200.
* turtle.isUsingItem(): poll status.
* turtle.cancelItemUse(): cancel without intentionally releasing/firing.

Event: turtle_item_use(reason,elapsedTicks). Other commands fail during use.
Yaw is relative to facing; pitch is positive down. Shutdown/unload cancels.
Use requires normal server-side item behavior; custom client GUIs need adapters.
