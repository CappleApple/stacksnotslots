# Changelog

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
