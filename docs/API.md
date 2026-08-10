# Public API

The API package is `com.cappleapple.stacksnotslots.api`. Implementation classes are not part of the compatibility contract.

## Entry point

```java
ICapacityInventory inventory = StacksNotSlotsApi.inventory(player);

long capacity = inventory.capacity();
long used = inventory.usedCapacity();
long remaining = inventory.remainingCapacity();
CapacityAmount exactUsed = inventory.exactUsedCapacity();
CapacityAmount exactRemaining = inventory.exactRemainingCapacity();
boolean overCapacity = inventory.isOverCapacity();
List<LogicalInventoryEntry> entries = inventory.entries();
```

Returned entries and stacks are defensive views. Hotbar/category metadata never owns an `ItemStack`. The legacy `usedCapacity()` value rounds fractional usage upward and `remainingCapacity()` rounds downward; use the exact methods for calculations involving items whose max stack size exceeds 64 or does not divide 64 evenly.

## Transactions

```java
InsertionResult simulation = inventory.insert(stack, true);
InsertionResult insertion = inventory.insert(stack, false);
ExtractionResult extraction = inventory.extract(prototype, amount, false);
```

Inputs are not mutated. Insertion reports requested/accepted amounts, a defensive remainder, a conservative upward-rounded whole-unit capacity-consumed value, and a rejection reason. Extraction may return multiple legal stacks when the requested quantity spans backing stacks. Read `exactUsedCapacity()` before and after a transaction when an exact consumed delta is required.

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

Return a positive whole-unit per-item override when applicable and `-1` to defer. Higher priority runs first, followed by resource-ID order. Close the registration to remove it. The fallback is the exact fraction `64 / maxStackSize`, so every complete legal stack costs exactly 64 units regardless of its stack size.

`StacksNotSlotsApi.capacityCost(stack)` remains a conservative upward-rounded whole-unit compatibility view. Use `StacksNotSlotsApi.exactCapacityCost(stack)` to receive a `CapacityAmount`. `CapacityAmount` is an immutable normalized rational number with exact arithmetic, comparisons, floor/ceiling views, and display conversion methods.

## Attribute and categories

`StacksNotSlotsApi.INVENTORY_CAPACITY_ATTRIBUTE` is the stable `stacksnotslots:inventory_capacity` resource ID. Use normal Minecraft attribute modifiers rather than backpack-specific calls into the inventory engine.

`StacksNotSlotsApi.categories(player)` returns public `CategoryView`/`CategoryRuleView` records. Rule types are `ITEM`, `TAG`, `MOD_ID`, and `REGEX`; the `MOD_ID` target's namespace is the matched mod namespace, while `REGEX` uses `expression()` and has a null `target()`. Tag rules cover item tags and represented block tags. Treat these immutable values as query metadata only; internal category implementation types are not part of the API contract.

## NeoForge capability

Query `Capabilities.ItemHandler.ENTITY` on a player for the dynamic compatibility view. Indices are stable sparse compatibility positions: an emptied interior index remains empty rather than shifting every later stack, and the last index is an append slot. Slot-specific insert/replace calls affect the requested index. `getSlots()` grows as needed and must never be cached as a capacity limit.

The handler is a view over the same authoritative collection. Simulation, extraction, and stack limits obey the central inventory rules.
