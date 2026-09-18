# Stacks Not Slots

Stacks Not Slots is a storage library for NeoForge 1.21.1 that treats an inventory as **capacity** instead of a fixed number of slots.

It is intentionally just the backend. There is no player inventory replacement, UI, category system, or dependency on Bundled Not Siloed or Panels Not Screens. A mod can use it for machines, backpacks, storage blocks, vehicles, NPC inventories, or anything else that should not be limited by an arbitrary slot count.

## Capacity model

By default, one full legal stack costs 64 capacity units.

That means:

- A 64-stack item costs 1 per item.
- A 16-stack item costs 4 per item.
- A non-stackable item costs 64.
- Modded stack sizes are handled proportionally as well.

The goal is for storage cost to describe **how much stuff is stored**, not how many different item types happen to occupy slots.

## API

The public API lives under:

```text
com.cappleapple.stacksnotslots.api
```

It supports:

- Fixed or runtime-supplied capacity
- Custom insertion/extraction rules
- Simulated operations before committing changes
- Logical item entries instead of artificial backing slots
- Exact counts for complete item identities
- Serialization with data components intact
- Change callbacks for block/entity owners
- Custom capacity-cost providers
- Inventory-to-inventory transfers
- A dynamically growing NeoForge `IItemHandlerModifiable` compatibility view

A basic inventory looks like this:

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
    inventory.insert(input, false);
}
```

The simulation-first pattern makes it possible to ask what would happen before changing the inventory, which is useful for automation and container transfers.

See [docs/API.md](docs/API.md) for the complete API contract and more examples.

## NeoForge compatibility

Stacks Not Slots can expose its dynamic storage through the normal NeoForge item-handler API.

That adapter grows as needed instead of imposing a second fixed slot limit, which makes it easier to plug capacity storage into mods that already know how to work with `IItemHandler`.

## Related projects

**Bundled Not Siloed** is the player-facing inventory overhaul built on Stacks Not Slots.

**Panels Not Screens** is a separate client UI library used by some of the same projects.

Neither is required to use this library.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.244 or newer compatible 21.1 build
- Java 21

## Building

```bash
./gradlew test build
```

Windows:

```powershell
.\gradlew.bat test build
```

The built library is written to `build/libs/`.

## License

Stacks Not Slots is licensed under [CC BY-NC-SA 4.0 with a Modpack/Server Exception](LICENSE). Modpacks and Minecraft servers, including monetized ones, may use it under the additional permission in the LICENSE.
