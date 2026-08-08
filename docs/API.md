# Public API

The API package is `com.cappleapple.stacksnotslots.api`. Implementation classes are not part of the compatibility contract.

## Entry point

```java
ICapacityInventory inventory = StacksNotSlotsApi.inventory(player);

long capacity = inventory.capacity();
long used = inventory.usedCapacity();
long remaining = inventory.remainingCapacity();
boolean overCapacity = inventory.isOverCapacity();
List<LogicalInventoryEntry> entries = inventory.entries();
```

Returned entries and stacks are defensive views. Hotbar/category metadata never owns an `ItemStack`.

## Transactions

```java
InsertionResult simulation = inventory.insert(stack, true);
InsertionResult insertion = inventory.insert(stack, false);
ExtractionResult extraction = inventory.extract(prototype, amount, false);
```

Inputs are not mutated. Insertion reports requested/accepted amounts, a defensive remainder, capacity consumed, and a rejection reason. Extraction may return multiple legal stacks when the requested quantity spans backing stacks.

Use simulation before a multi-inventory transaction. If another system changes the inventory between simulation and commit, validate the real result; a simulation is not a lock.

## Change listeners

```java
AutoCloseable registration = inventory.addListener((changed, revision) -> {
    // invalidate derived state
});
registration.close();
```

Listeners run on the mutation thread. Server inventory mutations are expected on the server thread; listeners should remain fast and must not recursively mutate the same inventory.

## Capacity-cost providers

```java
AutoCloseable registration = StacksNotSlotsApi.registerCapacityCostProvider(
    ResourceLocation.fromNamespaceAndPath("addon", "special_weights"),
    100,
    stack -> stack.is(MY_ITEM) ? 12 : -1
);
```

Return a positive per-item cost when applicable and `-1` to defer. Higher priority runs first, followed by resource-ID order. Close the registration to remove it. The fallback is `ceil(64 / maxStackSize)`, clamped to at least one.

## Attribute and categories

`StacksNotSlotsApi.INVENTORY_CAPACITY_ATTRIBUTE` is the stable `stacksnotslots:inventory_capacity` resource ID. Use normal Minecraft attribute modifiers rather than backpack-specific calls into the inventory engine.

`StacksNotSlotsApi.categories(player)` returns public `CategoryView`/`CategoryRuleView` records. Treat these immutable values as query metadata only; internal category implementation types are not part of the API contract.

## NeoForge capability

Query `Capabilities.ItemHandler.ENTITY` on a player for the dynamic compatibility view. Indices are stable sparse compatibility positions: an emptied interior index remains empty rather than shifting every later stack, and the last index is an append slot. Slot-specific insert/replace calls affect the requested index. `getSlots()` grows as needed and must never be cached as a capacity limit.

The handler is a view over the same authoritative collection. Simulation, extraction, and stack limits obey the central inventory rules.
