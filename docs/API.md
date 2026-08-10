# Stacks Not Slots developer API

Use `StacksNotSlotsApi.createInventory(...)` or `InventoryFactory.create(...)`. Consumers should depend on types in `com.cappleapple.stacksnotslots.api`; no player or UI integration is assumed.

## Creation and policy

```java
MutableCapacityInventory storage = InventoryFactory.create(
    InventoryOptions.builder(capacitySupplier)
        .insertionRule(stack -> isValidInput(stack))
        .extractionRule(stack -> !isLocked(stack))
        .mutationListener(this::setChanged)
        .build()
);
```

Rules run on normal insertion/extraction calls. Loading saved authoritative contents and applying a trusted server snapshot restore the encoded state so a later rule change does not silently destroy existing items.

## Queries and transactions

`entries()` returns aggregated identities with defensive representative stacks and `long` quantities. `count`, `totalItemCount`, capacity, exact capacity, remaining capacity, over-capacity state, and revision queries are available without exposing backing storage.

Insertion and extraction never mutate caller-owned input stacks. Pass `true` for simulation and `false` to commit. `InventoryTransfer.move` simulates the destination before extracting and returns unexpected commit-time remainders to the source. If consumer callbacks change both inventories during the commit and the source then rejects restoration, the result exposes defensive `unresolvedRemainders()` so ownership is never silently discarded.

## Persistence and synchronization

`serializeNBT`/`deserializeNBT` preserve item IDs, counts, data components, sparse indexed placement, unresolved entries, and revision metadata. `snapshot()` returns defensive copies suitable for a consumer's networking protocol; only apply snapshots received through a trusted server-authoritative path.

Stacks Not Slots deliberately does not prescribe packet IDs or tracking scope because a machine block entity, backpack item, NPC, and player attachment have different ownership and visibility rules.

## Capacity accounting

Use `CapacityUnits.exactUnitCost` and `CapacityUnits.exactCost` for calculations. `CapacityAmount` is an immutable normalized rational value. Whole-unit helpers round conservatively.

Capacity providers are priority ordered and registered through `StacksNotSlotsApi.registerCapacityCostProvider`. Return a non-negative whole-unit per-item override when applicable and `-1` to defer to the next provider or the exact `64 / maxStackSize` fallback.

## NeoForge compatibility

`com.cappleapple.stacksnotslots.api.compat.DynamicItemHandler` exposes the same inventory as a sparse, dynamically growing `IItemHandlerModifiable`; its last index is an append position, not a storage ceiling. `VanillaInventoryMirror` is an opt-in helper for consumers that intentionally maintain a bounded vanilla-list projection.

## Threading and authority

The library performs no client-authoritative mutation. The owning mod must execute authoritative mutations on its server thread and decide who may send requests. Change listeners run synchronously on the mutation thread and should remain fast.
