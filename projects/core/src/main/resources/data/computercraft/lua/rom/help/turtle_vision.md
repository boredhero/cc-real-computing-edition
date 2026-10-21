Turtle vision and targeted actions

view = turtle.observe(radius) returns visible blocks with name, state, tags and x/y/z offsets. Radius is an integer from 1 to 8.
Coordinates: x right, y up, z forward. Turning changes the frame.

observe subscribes to turtle_vision events carrying a revision number. Call observe again after an event to refresh the cached view. Events coalesce until observed; the event means the view may have changed. turtle.unobserve() stops notifications.

Dig, place or use visible blocks within 4.5 blocks, including diagonals:
  turtle.digAt(x, y, z)
  turtle.placeAt(x, y, z)
  turtle.useAt(x, y, z)

Actions return true or false plus a reason. Digging requires an equipped built-in tool. placeAt uses the selected block or seeds; useAt right-clicks with the selected item or empty hand. Normal protection hooks apply. No chunks are loaded. Vision is block-only, 360 degrees, and uses outline ray tests.
