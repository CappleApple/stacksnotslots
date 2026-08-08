# Vanilla inventory compatibility hooks

Minecraft 1.21.1 assumes 36 player item indices in menus and several direct code paths. Stacks Not Slots keeps those indices as a bounded *view* while storage and NeoForge capability enumeration remain dynamic.

## `InventoryMixin`

The mixin activates only after the one-time vanilla inventory migration marker is set.

- `getItem`, `getSelected`, `setItem`, and removal methods map vanilla indices 0–35 to logical backing entries or hotbar bindings.
- both `add` overloads call the centralized capacity transaction; pickup, commands, rewards, trading, crafting remainders, and most modded vanilla-style insertion therefore share one enforcement path.
- `getFreeSlot`, stack matching, and remaining-space queries describe only the projection and never define logical carrying capacity.
- `tick` ticks every actual backing stack; changes made through a live held-item reference are reconciled by identity/count hash.
- `fillStackedContents` accounts for the complete logical collection for recipe matching.
- `dropAll` drains and drops the complete logical collection. Armor/offhand continue through vanilla.
- `clearContent` clears logical ownership without affecting category/hotbar metadata.

The mixin deliberately leaves `getContainerSize()` at 41 so vanilla armor indices 36–39 and offhand index 40 do not shift as the collection grows.

## `ItemEntityMixin`

The redirect wraps only the call from `ItemEntity.playerTouch` to `Inventory.add`. It adds world-pickup category policies before the common global-capacity transaction while retaining vanilla pickup statistics, animation, partial-stack remainder, and post-event behavior.

## `SlotMixin`

Vanilla menu transfer logic splits source stacks before calling `Inventory.setItem`. The slot mixin reports a capacity-adjusted maximum for player item slots so the source is split by exactly the amount global capacity can accept. Existing stack capacity is credited when a slot is being replaced. Armor/offhand and non-player containers are untouched.

## Menu shift-click mixins

`AbstractContainerMenuMixin` recognizes destination ranges made entirely of vanilla player-storage slots and routes the source directly through the central insertion transaction. This allows chest, furnace, crafting-result, and modded-menu shift-clicks to append beyond the 36-slot projection whenever capacity remains.

`InventoryMenuMixin` suppresses vanilla's main-inventory-to-hotbar shuffle because both ranges are views of the same collection. Shift-equipping armor/offhand and transfers from crafting/equipment slots remain active.

## Dynamic NeoForge view

`DynamicItemHandler` is independent of vanilla's 36-index projection. It reports every occupied legal backing stack plus an append slot. Inserting through that final slot may grow the view, so the synthetic slot count can represent as many distinct stacks as capacity and JVM memory/indexing allow.

## Remaining direct-field risk

`Inventory.items` is a public final `NonNullList` in vanilla. Changing its shape would break fixed armor/offhand index assumptions, and replacing every external direct field access is not possible without broad bytecode changes. It remains empty after migration. Integrations must use:

1. the public capacity-inventory API,
2. NeoForge's player entity item-handler capability, or
3. vanilla `Inventory` methods for the 36-entry projection.

Mods that directly iterate or mutate `player.getInventory().items` will see only that compatibility field and need an integration patch. This limitation does not cap storage or the item-handler slot view.
