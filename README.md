# Stacks Not Slots

Stacks Not Slots is an independent inventory and storage library for NeoForge 1.21.1. Instead of treating an inventory as a fixed number of slots, it treats storage as a dynamically growing collection of valid `ItemStack`s with an overall capacity limit.

The library is intentionally focused on the storage backend. It does not include a player inventory overhaul, screens, keybinds, item categories, or any dependency on Bundled Not Siloed or Panels Not Screens. It can be used on its own anywhere a mod needs an inventory or storage system.

By default, one complete legal stack always represents 64 capacity units. This keeps storage costs proportional regardless of an item's normal maximum stack size:

* A 64-stack item costs **1 capacity unit per item**.
* A 16-stack item costs **4 capacity units per item**.
* A non-stackable item costs **64 capacity units**.
* Items with unusual or modded stack limits are handled using exact rational accounting, so their capacity cost remains proportional without relying on floating-point approximations.

## Public API

The public API is available under `com.cappleapple.stacksnotslots.api`, including the `api.inventory` and `api.compat` packages.

It supports:

* Fixed capacity or capacity supplied dynamically at runtime.
* Custom insertion and extraction rules defined by the consuming mod.
* Simulated operations, allowing an insertion or extraction to be checked before actually changing the inventory.
* Committed insertion and extraction.
* Logical entries that aggregate equivalent item stacks rather than exposing artificial storage slots.
* Total inventory counts and counts for specific item identities.
* NBT serialization that preserves item components.
* Revisioned synchronization snapshots designed to prevent callers from modifying internal inventory state.
* Simulation-first transfers between inventories.
* Change callbacks for marking blocks, entities, or other owners as modified.
* Custom capacity-cost providers.
* A dynamically growing NeoForge `IItemHandlerModifiable` adapter for compatibility with existing item-handler based systems.

A basic inventory can be created like this:

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

In this example, the inventory's capacity can change dynamically, the machine decides which items may enter and which may be extracted by automation, and `setChanged()` is called whenever the inventory is actually modified.

The insertion is simulated first so the caller can determine how much would be accepted before committing the operation.

See [docs/API.md](docs/API.md) for the complete API and usage contract.

## Building

Stacks Not Slots requires:

* Java 21
* Minecraft 1.21.1
* NeoForge 21.1.244 or newer

Build and run the test suite with:

```powershell
./gradlew test build
```

The resulting library JAR is written to:

```text
build/libs/stacksnotslots-1.0.jar
```

## Related Projects

**Panels Not Screens** is a separate client-side library for draggable and dockable interface panels.

**Bundled Not Siloed** is the player-facing inventory overhaul built using both Stacks Not Slots and Panels Not Screens.

Neither project is required to use Stacks Not Slots. The library can serve as the storage backend for machines, backpacks, storage blocks, vehicles, NPCs, custom containers, or essentially any other system that needs an inventory without being constrained to a fixed slot count.

## License

Stacks Not Slots is licensed under the MIT License.

Minecraft and NeoForge remain subject to their respective licenses.
