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
- Player-owned category definitions, exact item/tag include/exclude rules, ordering, icons, sorting, enablement, and pickup limits
- Separate `config/stacksnotslots/default_categories.json` preset file
- In-game searchable category editor and reset-to-defaults operation
- Most-restrictive overlapping world-pickup limits and rate-limited feedback
- Exact-item and category hotbar bindings, remembered selections, cycling keybinds, and HUD feedback
- Vanilla-first inventory UI with a collapsed-by-default, animated browser drawer for search, category projection, sorting, aggregated quantities, and capacity
- JEI and EMI exclusion-area integrations so their ingredient lists avoid the expanded browser drawer
- Unified-inventory side panel on ordinary container screens
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

The built mod is written to `build/libs/stacksnotslots-0.2.0.jar`. The project uses official Mojang mappings with Parchment parameter names and ModDevGradle's Minecraft-aware JUnit support.

## Player usage

Open the normal inventory to see the familiar vanilla layout. Use the slim `>` tab on its right edge to open the inventory-browser drawer. The drawer displays each stack-compatible identity once with its total quantity and slides closed again with `<`.

- Left-click an entry to move a legal stack to the cursor.
- Right-click an entry to move half a legal stack to the cursor.
- Press the normal drop key while hovering an entry to drop one; hold Control to drop a stack.
- Shift-click an entry to bind its exact item to the currently selected hotbar position.
- Use **Manage Tabs** to add/edit/delete/reorder categories. The **B** button binds a category to the selected hotbar position.
- Selecting a category or sort mode projects only that ordered view into the vanilla main-inventory grid; ordinary insertion/removal does not compact explicitly placed slots.
- Use the configurable forward/backward cycle keys to change the active item in a category-bound hotbar position.
- Container screens show a scrollable unified-inventory panel; take any logical entry to the cursor and place it into the container normally.

Search matches display names, full registry IDs, namespaces, item tags (prefix the query with `#`), and optionally cached tooltip text. Sort modes cover name, quantity, registry ID, and namespace; the current sort and category selection persist with player data.

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

## Default preset schema

The bundled file is copied once to `config/stacksnotslots/default_categories.json`. Pack authors can redistribute a replacement. A category object supports:

```json
{
  "id": "ores",
  "name": "Ores",
  "icon": "minecraft:raw_iron",
  "order": 20,
  "include": ["#c:ores", "minecraft:ancient_debris"],
  "exclude": ["#example:ignored_ores"],
  "pickupLimit": -1,
  "sort": "name",
  "enabled": true,
  "allItems": false
}
```

Unqualified category IDs use the `stacksnotslots` namespace. Unqualified item/tag IDs use `minecraft`. `pickupLimit: -1` means unlimited. Supported sort strings are `name`, `name_descending`, `quantity_ascending`, `quantity_descending`, `registry_id`, and `mod_namespace`.

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
- Shift-clicks from containers into player storage use the dynamic logical append path; shifts between vanilla's main/hotbar projections are intentionally a no-op because no physical move exists.
- Direct mutation of live vanilla projected stacks is reconciled each player tick and synchronized by revision.
- The HUD hotbar reads the live projection instead of vanilla's stale public `items` list.
- JEI and EMI receive the expanded drawer as an exclusion area and can lay out their ingredient panels around it.
- Recipe matching accounts for all logical stacks. Some modded recipe-placement or inventory code that directly indexes `Inventory.items` may only observe vanilla's empty compatibility field.
- Client UI classes are isolated behind the client-only mod entry point; the dedicated server smoke run loads no client package.

## Roadmap

- Broader compatibility fixtures for popular menu/recipe implementations that directly access vanilla fields
- Datapack reload merging for server preset contributions
- Rich tooltip text indexing cache for search
- GameTests covering multi-player concurrent container sessions and death/respawn flows
- Optional addon examples for equipment mods that apply capacity attribute modifiers

## License

MIT. Minecraft and NeoForge remain subject to their respective licenses.
