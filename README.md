# Stacks Not Slots

Stacks Not Slots is a NeoForge mod for Minecraft 1.21.1 that makes player storage capacity-based instead of slot-count-based. The authoritative inventory is a dynamically growing collection of legal `ItemStack` values. The nine-position hotbar and vanilla inventory indices are access views over that collection; neither grants storage nor limits how many distinct entries can exist.

The default capacity is 1,728 units. A 64-stackable item costs one unit, a 16-stackable item costs four, and a non-stackable item costs 64. Capacity includes hotbar-accessed items and is controlled live by the `stacksnotslots:inventory_capacity` player attribute.

There is no hidden compatibility-slot ceiling below capacity. If a player has capacity `N`, the backend and NeoForge item-handler view can grow to represent `N` distinct quantity-one 64-stackable identities (subject only to Java's practical integer/memory limits). Snapshot chunking and the vanilla 36-index projection are transport/access details, never carrying limits.

## Implementation status

Implemented:

- Dynamic logical inventory with no configured backing-slot maximum
- Sparse, dynamically indexed compatibility slots that retain explicit placement, plus an append slot
- Runtime capacity attribute and normal Minecraft attribute-modifier support
- Integer capacity costs with a public override registry
- Transactional simulated/real partial insertion and extraction
- Automatic consolidation that respects data components
- Persistent NeoForge player attachment, lossless vanilla-inventory migration, and unresolved-entry preservation
- Over-capacity retention when attribute capacity falls
- Chunked initial sync and revisioned delta sync
- Player-owned category definitions, exact item/tag/mod include/exclude rules, ordering, dynamic or fixed icons, sorting, enablement, and pickup limits
- Separate `config/stacksnotslots/default_categories.json` preset file
- In-game searchable category editor and reset-to-defaults operation
- Most-restrictive overlapping world-pickup limits and rate-limited feedback
- Category-only hotbar cycle bindings, remembered selections, cycling keybinds, and HUD feedback
- Vanilla-first inventory UI with a collapsed-by-default, four-direction draggable browser for search, category projection, list/grid views, configurable counts, sorting, and capacity
- Shared topmost browser on all container screens, with input scoped to the browser so modded container controls remain usable
- Dynamic JEI and EMI exclusion areas so their ingredient lists reflow around the moved or expanded browser
- Native quick-move integration for vanilla, Mouse Tweaks-style repeated clicks, and modded container screens
- Bulk dump/extract controls for open menus and looked-at item-handler containers, with an optional world feedback grid
- Server-authoritative inventory/category/hotbar packets with input validation and action rate limiting
- NeoForge entity item-handler capability and public Java API
- Capacity/debug/validation/category commands
- Automated tests, including 1,000 distinct quantity-one 64-stackable entries at capacity 1,000
- Successful dedicated NeoForge server smoke startup

The vanilla 36 item indices remain a compatibility projection. Mods that call `Inventory` methods see this projection, while NeoForge item-handler users see the dynamically growing complete view. A mod that directly reads the public `Inventory.items` field bypasses both adapters and is a known compatibility risk; see [docs/VANILLA_HOOKS.md](docs/VANILLA_HOOKS.md).

## Build and development environment

Requirements:

- Java 21
- Minecraft 1.21.1
- NeoForge 21.1.244

Windows:

```powershell
.\gradlew.bat build
.\gradlew.bat test
.\gradlew.bat runClient
.\gradlew.bat runServer
```

Linux/macOS:

```bash
./gradlew build
./gradlew test
./gradlew runClient
./gradlew runServer
```

The built mod is written to `build/libs/stacksnotslots-0.6.0.jar`. The project uses official Mojang mappings with Parchment parameter names and ModDevGradle's Minecraft-aware JUnit support.

## Player usage

Open the normal inventory to see the familiar vanilla layout. Click the spyglass handle to open the inventory browser. Drag it at least a few pixels to reposition it; its blue state shows that the browser is open. The browser can open left, right, above, or below the handle and renders above the underlying menu. Position, docking, open state, and visibility are remembered independently for each container-screen type. Control-F toggles the handle and closes an open browser.

- Left-click an entry to move a legal stack to the cursor.
- Right-click an entry to move half a legal stack to the cursor.
- Press the normal drop key while hovering an entry to drop one; hold Control to drop a stack.
- Control-left-click a visible player slot to stow that stack behind the vanilla window. Clicking the browser list while carrying a stack does the same.
- With the browser closed, shift-clicking retains normal vanilla main-grid/hotbar/equipment behavior. With it open, shift-clicking a visible player stack stows it; shift-clicking a browser entry moves one backend stack into the first free main-grid slot, or does nothing when that grid is full.
- In another container screen, an open browser redirects container-to-player shift-clicks into backend storage while player-to-container shift-clicks keep the menu's native behavior.
- The sticky-piston browser button extracts an open container; hold Shift to turn it into a normal piston and dump the player inventory. Control-G and Control-H perform bulk dump/extract against the open menu or the container being looked at.
- The draggable category icon above the vanilla grid scrolls categories whether its popup is open or closed. Hold Shift to turn it into a sticky piston and stow the 27-slot main grid without touching the hotbar.
- Use **Manage Tabs** to add/edit/delete/reorder categories, assign a cycle category independently to each of the nine hotbar positions, and choose whether pickups may enter empty hotbar slots.
- Selecting a category or sort mode performs one explicit arrangement of the main 27-slot grid. It displays one stack per distinct matching identity; subsequent placement is fully manual until another category or sort control is clicked.
- Hotbar bindings never restrict placement or rearrange items automatically. The configurable forward/backward cycle keys explicitly swap the selected position with the next owned item in its assigned category.
- Every container screen can show the same draggable browser; take any logical entry to the cursor and place it into the container normally.
- Right-click the search field to clear it. Control-A selects the complete query so Backspace, Delete, or newly typed text can replace it.

An empty search shows the selected category. A non-empty search spans every category and matches display names, full registry IDs, mod namespaces (prefix with `@`), item tags (prefix with `#`), and optionally cached tooltip text. Search results retain the selected sort order. Category rules accept exact items, `#tags`, and `@modid` namespaces. Sort modes cover name, quantity, registry ID, and namespace; the current sort and category selection persist with player data.

The browser never extends beyond the screen edge or across its handle. It reduces visible rows or columns when space is limited, while retaining at least one item row or column. Top and bottom docking use left/right control rails so the search field remains at the top and horizontal space is available to item results.

## Capacity and over-capacity behavior

The effective limit is the floored, non-negative value of the player's `stacksnotslots:inventory_capacity` attribute. Attribute modifiers take effect immediately. For example:

```mcfunction
/attribute @s stacksnotslots:inventory_capacity modifier add stacksnotslots:demo_pack 768 add_value
```

If capacity falls below current usage, no item is deleted or moved. New positive-cost insertion is rejected until enough capacity is restored or items are removed. Dropping, consuming, crafting with, and transferring items out remain valid.

If a committed held-item transformation produces a higher-cost result, the result is preserved and the player enters the normal over-capacity state rather than losing the item or crashing the operation.

Equipped armor and offhand items retain vanilla equipment storage and are not duplicated in the logical collection.

## Categories and pickup rules

Categories are predicates over the unified collection; they never own items or reserve capacity. Matching is:

```text
(allItems OR any include rule) AND no exclude rule
```

Exclusions win. An item may appear in multiple categories. During world pickup, every matching enabled finite limit must allow the accepted amount, so the most restrictive remaining allowance wins. Manual container transfers do not use category limits by default, while global capacity always applies.

Player customizations are persisted per player and are not overwritten when server defaults change. **Reset to Defaults** is explicit.

## Configuration

`config/stacksnotslots-common.toml`:

- `inventory.baseInventoryCapacity` — initial base attribute value, default `1728`
- `inventory.overCapacityBlocksPickup` — enables the dedicated over-capacity pickup short-circuit; disabling it never permits positive growth beyond global capacity
- `inventory.allowPartialPickup` — accept only the legal portion of a ground stack
- `categories.categoryLimitsAffectWorldPickup` — default `true`
- `categories.categoryLimitsAffectManualTransfers` — default `false`

`config/stacksnotslots-client.toml`:

- `capacityDisplayMode` — `CAPACITY`, `STACK_EQUIVALENTS`, or `BOTH`
- `pickupLimitNotification` — `NONE`, `HUD`, `ACTION_BAR`, `SOUND`, or `HUD_AND_SOUND`
- `enableSearchTooltipIndexing`
- `enableHotbarCycleOverlay`
- `browserViewMode` — `GRID` (default) or `LIST`
- `browserGridColumns` / `browserGridRows` — default `4` by `6`
- `browserItemCountMode` — `EXACT`, `COMPACT` (default), `STACKS`, `STACKS_REMAINDER`, or `PERCENTAGE`
- `browserOverallCountMode` — `EXACT`, `COMPACT`, `STACKS` (default), or `PERCENTAGE`
- `manageTabsIcon`, `settingsIcon`, and `browserHandleIcon` — configurable item IDs for the square controls and draggable handle
- `browserHandleX`, `browserHandleY`, `browserHandleVisible`, and `browserDockSide` — defaults for container-screen types without saved state
- `browserScreenStates` — internal per-screen-type placement, docking, open, and visibility state
- `autoChooseBrowserSide` — optional side selection while dragging
- `autoSideDeadZoneX` / `autoSideDeadZoneY` — center-screen dead-zone half sizes for automatic docking
- `showBulkTransferOverlay` / `bulkTransferOverlaySeconds` — in-world bulk-transfer feedback and duration

## Default preset schema

The bundled file is copied once to `config/stacksnotslots/default_categories.json`. Pack authors can redistribute a replacement. A category object supports:

```json
{
  "id": "ores",
  "name": "Ores",
  "icon": "minecraft:raw_iron",
  "order": 20,
  "include": ["#c:ores", "minecraft:ancient_debris", "@examplemod"],
  "exclude": ["#example:ignored_ores"],
  "pickupLimit": -1,
  "sort": "name",
  "enabled": true,
  "allItems": false
}
```

Unqualified category IDs use the `stacksnotslots` namespace. Unqualified item/tag IDs use `minecraft`; `@modid` matches every item registered by that namespace. `pickupLimit: -1` means unlimited. Supported sort strings are `name`, `name_descending`, `quantity_ascending`, `quantity_descending`, `registry_id`, and `mod_namespace`.

## Commands

```text
/stacksnotslots capacity [player]
/stacksnotslots inventory debug
/stacksnotslots inventory validate
/stacksnotslots categories reset
/stacksnotslots categories reload
```

Use vanilla `/attribute` commands for capacity modifiers.

## API

The public entry point is `com.cappleapple.stacksnotslots.api.StacksNotSlotsApi`. It exposes capacity/usage queries, logical enumeration, simulation and mutation transactions, change listeners, category views, the attribute ID, and a priority-ordered capacity-cost provider registry. See [docs/API.md](docs/API.md).

## Compatibility notes

- The complete dynamic inventory is exposed through NeoForge's player entity item-handler capabilities.
- Empty compatibility positions are retained as sparse holes, so explicit vanilla/API slot placement remains stable across inventory changes and persistence.
- Vanilla menus retain 36 projected item indices and real armor/offhand indices. The custom inventory/container panels provide access to entries outside that projection.
- Shift-clicks from external containers use vanilla visible-slot behavior while the browser is closed and the dynamic backend path while it is open. Player-owned main-grid/hotbar shift-clicks retain vanilla destination semantics whenever the browser is closed.
- The public vanilla `Inventory.items` list is maintained as a live first-36 compatibility view for mods that access the field directly; direct replacements and stack mutations are reconciled into logical storage.
- The HUD hotbar and accessor APIs read the same live projection.
- JEI and EMI receive the expanded browser as an exclusion area and can lay out their ingredient panels around it.
- Recipe matching accounts for all logical stacks. Code that directly indexes `Inventory.items` observes the live first-36 view, while capability/API integrations can enumerate the complete dynamic backend.
- Client UI classes are isolated behind the client-only mod entry point; the dedicated server smoke run loads no client package.

## Roadmap

- Broader compatibility fixtures for popular menu/recipe implementations that directly access vanilla fields
- Datapack reload merging for server preset contributions
- Rich tooltip text indexing cache for search
- More customization features

- Eventually, this mod will be split into 3 separate mods. This one will be the backend library that handles the capacity based inventory, a second mod will be a universally available window manager library, and then the front end part of this that combines the two and gives a UI front end to the techical backend

## License

MIT. Minecraft and NeoForge remain subject to their respective licenses.
