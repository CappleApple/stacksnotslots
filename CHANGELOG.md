# Changelog

## 1.0 - 2026-08-10

Stacks Not Slots 1.0 marks the project's transition from an all-in-one player inventory overhaul into a focused library mod for mod developers. The capacity-based inventory backend is now independent, reusable, and ready to serve as a foundation for other mods.

### Important

- Version 1.0 is a library release, not a drop-in replacement for the player-facing 0.6.x releases.
- Installing Stacks Not Slots by itself no longer changes the player's inventory or adds an inventory interface.
- The former player-facing experience is being separated into **Bundled Not Siloed**, which uses this library together with **Panels Not Screens**.

### Added

- Added a public API for creating fixed- or dynamically-sized capacity inventories.
- Added configurable insertion and extraction rules, change listeners, simulated operations, snapshots, transfers, and component-preserving serialization.
- Added reusable NeoForge item-handler and vanilla inventory compatibility adapters.
- Added exact capacity accounting for items with any positive maximum stack size.
- Added developer documentation and examples for integrating the library into machines, backpacks, storage blocks, vehicles, NPCs, player attachments, and other inventory implementations.

### Changed

- Refocused the mod on its core concept: inventories limited by total capacity instead of a fixed number of slots.
- Moved the supported public API under `com.cappleapple.stacksnotslots.api`.
- Made ownership, persistence, networking, synchronization, and user interface behavior the responsibility of the consuming mod so the backend can support many inventory types.
- Updated the project metadata and documentation to present Stacks Not Slots as an independent NeoForge 1.21.1 library.

### Removed

- Removed the built-in player inventory replacement, inventory browser, category system, hotbar bindings, commands, configuration, networking, and player-specific storage behavior.
- Removed the built-in screens, keybinds, mixins, and JEI/EMI interface integration.

### For Players

You only need to install Stacks Not Slots when another mod lists it as a dependency. For the complete player-facing inventory overhaul previously included in this project, use **Bundled Not Siloed** when it becomes available.

## 0.6.5 - 2026-08-10

### Changed

- Capacity accounting now supports every positive item max-stack size with exact proportional costs. A complete legal stack always costs 64 units, so individual 128-stackable items cost 0.5 units and 96-stackable items cost exactly 2/3 of a unit.
- Capacity acceptance, category pickup limits, slot replacement, invariant repair, commands, percentages, and the browser capacity display now use exact rational arithmetic without floating-point drift.
- Added exact capacity values to the public API while retaining conservative rounded whole-unit methods for source compatibility.

## 0.6.4 - 2026-08-09

### Changed

- Increased the default base inventory capacity from 1,728 to 2,304 units so it represents all 36 ordinary player slots, including the nine-slot hotbar.

### Fixed

- Fixed shift-clicking browser entries into virtual storage terminals and menus with custom transfer logic, including Tom's Simple Storage and Sophisticated Backpacks.
- Fixed closed-browser container transfers filling the player inventory from right to left and bottom to top instead of main-grid order.
- Fixed custom storage menus bypassing backend stow when shift-clicking their contents while the inventory browser is open.

## 0.6.3 - 2026-08-09

### Added

- Added `/pattern` regular-expression searches to the inventory browser and category rule editor.
- Added explicit `^text` tooltip searches and `^/pattern` tooltip regular-expression searches.
- Added the project logo to the NeoForge Mods screen and made it the default inventory-browser handle icon.
- Added `SNS-SaveState.json` in the game directory for client-owned settings, per-screen browser placement, tabs, hotbar category bindings, and view preferences.
- Added durable `/regex` category include/exclude rules. Regex rules match names, registry IDs, namespaces, item/block tags, and `block:<id>` for block items.
- Added block-tag support for `BlockItem`s, including vanilla `minecraft:mineable/*` tags.

### Changed

- Normal unprefixed searches no longer inspect tooltip text; tooltip indexing only runs for explicit `^` searches.
- Player customization is now keyed by player UUID on the client and validated/synchronized to the server at login. Actual inventory contents and capacity remain server/world-owned.
- Existing client TOML placement/settings and legacy world-saved tab data migrate automatically on first use.
- Restored the spyglass as the default browser handle while retaining the project logo on the Mods screen and as a configurable handle option.
- Updated the bundled category presets to use valid NeoForge conventional tags and dynamic regex rules. Unedited legacy defaults upgrade automatically; customized presets are preserved.

### Fixed

- Search syntax help now appears only while Shift is held over the search field.
- The browser handle normally shows only `Inventory Browser`; holding Shift adds the drag hint.
- Fixed block-only tags appearing invalid in the category editor and never matching their block items.

## 0.6.2 - 2026-08-09

Changes since 0.6.0:

### Added

- Added independently persisted browser position, docking direction, open state, and visibility for each container-screen type.
- Added a configurable default browser-handle anchor. New interfaces default to the bottom-right beside the player hotbar.
- Added the `Start Inventory Search` keybind, bound to `F` by default. It opens the browser, clears the previous query, and focuses search without entering the shortcut character.
- Added right-click-to-clear and Control-A selection to the browser search field.

### Changed

- Rebuilt the inventory browser as a responsive four-direction layout. It shrinks its visible rows or columns to available space, retains at least one result row or column, and stays between its handle and the screen edge.
- Top and bottom docking now use horizontal results with controls on side rails while keeping search at the top.
- Added a drag threshold so clicking the browser handle no longer nudges its saved position.
- Browser placement is now stored relative to the current container GUI instead of as absolute screen pixels. It follows GUI repositioning, resolution changes, GUI-scale changes, and ultrawide layouts.
- Reduced the overlay's input ownership to its visible bounds so underlying vanilla and modded container interfaces remain interactive.
- Simplified production build metadata and optional compatibility dependency handling.

### Fixed

- Fixed browser panels and controls overlapping each other or extending beyond the available screen area.
- Fixed stale absolute browser coordinates placing the handle far away from the current inventory interface.
- Fixed the search shortcut's key appearing as the first character of a new query.
- Fixed crashes when a modded inventory menu does not expose a safely constructible menu type.
- Fixed browser state leaking between unrelated container-screen types.
