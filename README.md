# Stacks Not Slots

Stacks Not Slots is an independent NeoForge 1.21.1 inventory/storage library. It models storage as a dynamically growing collection of legal `ItemStack` values constrained by capacity, not by a fixed number of slots. It has no player overhaul, screens, keybinds, categories, or dependency on Bundled Not Siloed or Panels Not Screens.

Every complete legal stack costs 64 capacity units by default. A 64-stackable item costs one unit each, a 16-stackable item costs four, a non-stackable item costs 64, and unusual or modded maximum stack sizes use exact rational accounting.

## Public API

Public types live under `com.cappleapple.stacksnotslots.api`, including the `api.inventory` and `api.compat` packages. The API supports:

- fixed or live-supplied capacity;
- consumer-defined insertion and extraction rules;
- simulated and committed insertion/extraction;
- aggregate logical entries and total/per-identity counts;
- component-preserving NBT serialization;
- defensive revisioned synchronization snapshots;
- simulation-first inventory-to-inventory transfers;
- change callbacks;
- capacity-cost providers; and
- a dynamically growing NeoForge `IItemHandlerModifiable` adapter.

```java
MutableCapacityInventory inventory = StacksNotSlotsApi.createInventory(
    InventoryOptions.builder(() -> machineCapacity)
        .insertionRule(stack -> machineAccepts(stack))
        .extractionRule(stack -> automationMayExtract(stack))
        .mutationListener(this::setChanged)
        .build()
);

InsertionResult preview = inventory.insert(input, true);
if (preview.acceptedAmount() > 0) {
    InsertionResult committed = inventory.insert(input, false);
}

long apples = inventory.count(new ItemStack(Items.APPLE));
CompoundTag saved = inventory.serializeNBT(registries);
inventory.deserializeNBT(registries, saved);
```

See [docs/API.md](docs/API.md) for the complete usage contract.

## Building

Requirements are Java 21, Minecraft 1.21.1, and NeoForge 21.1.244 or newer.

```powershell
./gradlew test build
```

The built library is written to `build/libs/stacksnotslots-1.0.jar`.

## Related projects

- **Panels Not Screens** is the independent draggable/dockable client panel library.
- **Bundled Not Siloed** is the player-facing inventory overhaul that consumes both libraries.

Neither related project is required to use this backend for machines, backpacks, storage blocks, vehicles, NPCs, or other inventories.

## License

MIT. Minecraft and NeoForge remain subject to their respective licenses.
