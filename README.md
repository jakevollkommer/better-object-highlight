# Better Object Highlight

Highlight game objects the way [better-npc-highlight](https://github.com/riktenx/better-npc-highlight)
highlights NPCs: maintain your own lists of object IDs or names per highlight style, and hide
objects from rendering entirely with a built-in entity hider.

Works on all object types, game objects (pillars, obstacles, etc.), wall objects,
decorative objects, and ground objects.

## Features

- **Per-style ID/name lists**, separate lists for hull, model outline, clickbox, and tile
  highlights, each with its own colors. Lists accept comma- or newline-separated object IDs
  or names (`*` wildcards supported for names).
- **Preset colors**, append `:n` to any entry to color it with preset `n` (1–5), e.g.
  `1234:1, 1235:2` or `Guardian*:3`. Presets are configured in the "Preset colors" section;
  entries without a suffix use the style's default colors.
- **Entity hider for objects**, objects on the hide list are removed from the scene and never
  rendered. Removing an entry from the list restores the objects (via a quick scene reload).
- **Multiloc aware**, objects whose appearance changes with game state (farming patches,
  doors, etc.) are matched against both their base ID and all impostor IDs/names.

Use a plugin like [Identificator](https://github.com/Skretzo/runelite-plugins) to find object IDs.

## Notes

- Wall, decorative, and ground objects have no individual removal API in RuneLite, so hiding
  one removes everything on its tile. Plain game objects (most standalone scenery) are removed
  individually.

## Credits

- Concept and config UX modeled on [riktenx/better-npc-highlight](https://github.com/riktenx/better-npc-highlight).
- Object tracking and rendering derived from RuneLite's
  [Object Markers](https://github.com/runelite/runelite/tree/master/runelite-client/src/main/java/net/runelite/client/plugins/objectindicators)
  plugin (BSD-2-Clause, © Tomas Slusny, Adam), license headers retained.
