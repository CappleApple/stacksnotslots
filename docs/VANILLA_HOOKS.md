# Optional vanilla-list projection

Stacks Not Slots itself installs no player inventory mixins. `VanillaInventoryMirror` is a narrowly scoped helper for a consuming mod that chooses to expose the first 36 indexed backend positions through a vanilla `Inventory.items`-style list.

`publish` places the backend's live first-36 references into the supplied list. `reconcileDirectWrites` imports direct replacements and republishes the canonical references. The bounded list remains a compatibility view and never defines logical capacity.

Bundled Not Siloed owns the actual player inventory hooks and documents their behavior in its own repository.
