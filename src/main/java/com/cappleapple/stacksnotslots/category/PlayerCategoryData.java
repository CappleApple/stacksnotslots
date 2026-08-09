package com.cappleapple.stacksnotslots.category;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public final class PlayerCategoryData {
    private final ArrayList<CategoryDefinition> categories = new ArrayList<>();
    private boolean initializedFromDefaults;
    private long revision;

    public List<CategoryDefinition> categories() {
        return categories.stream().sorted(Comparator.comparingInt(CategoryDefinition::order)).toList();
    }

    public void replaceAll(List<CategoryDefinition> definitions, boolean defaultsInitialized) {
        categories.clear();
        categories.addAll(definitions);
        initializedFromDefaults = defaultsInitialized;
        revision++;
    }

    public boolean initializedFromDefaults() {
        return initializedFromDefaults;
    }

    public long revision() { return revision; }

    public CategoryDefinition find(ResourceLocation id) {
        return categories.stream().filter(category -> category.id().equals(id)).findFirst().orElse(null);
    }

    public void upsert(CategoryDefinition definition) {
        categories.removeIf(category -> category.id().equals(definition.id()));
        categories.add(definition);
        revision++;
    }

    public void remove(ResourceLocation id) {
        if (categories.removeIf(category -> category.id().equals(id))) revision++;
    }

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        root.putBoolean("InitializedFromDefaults", initializedFromDefaults);
        ListTag list = new ListTag();
        categories.forEach(category -> list.add(saveCategory(category)));
        root.put("Categories", list);
        return root;
    }

    public void load(CompoundTag root) {
        categories.clear();
        initializedFromDefaults = root.getBoolean("InitializedFromDefaults");
        ListTag list = root.getList("Categories", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CategoryDefinition definition = loadCategory(list.getCompound(i));
            if (definition != null) categories.add(definition);
        }
        revision++;
    }

    public static CompoundTag saveCategory(CategoryDefinition category) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", category.id().toString());
        tag.putString("Name", category.displayName());
        tag.putString("Icon", category.icon().toString());
        tag.putInt("Order", category.order());
        tag.putLong("PickupLimit", category.pickupLimit());
        tag.putString("Sort", category.sortMode().name());
        tag.putBoolean("Enabled", category.enabled());
        tag.putBoolean("AllItems", category.allItems());
        tag.put("Includes", saveRules(category.includes()));
        tag.put("Excludes", saveRules(category.excludes()));
        return tag;
    }

    private static ListTag saveRules(List<CategoryRule> rules) {
        ListTag list = new ListTag();
        for (CategoryRule rule : rules) {
            list.add(StringTag.valueOf(rule.encoded()));
        }
        return list;
    }

    public static CategoryDefinition loadCategory(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Id"));
        ResourceLocation icon = ResourceLocation.tryParse(tag.getString("Icon"));
        if (id == null || icon == null) return null;
        SortMode sort;
        try { sort = SortMode.valueOf(tag.getString("Sort")); }
        catch (IllegalArgumentException ignored) { sort = SortMode.NAME_ASCENDING; }
        return new CategoryDefinition(id, tag.getString("Name"), icon, tag.getInt("Order"),
                loadRules(tag.getList("Includes", Tag.TAG_STRING)), loadRules(tag.getList("Excludes", Tag.TAG_STRING)),
                Math.max(-1, tag.getLong("PickupLimit")), sort, tag.getBoolean("Enabled"), tag.getBoolean("AllItems"));
    }

    private static List<CategoryRule> loadRules(ListTag list) {
        ArrayList<CategoryRule> rules = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            String encoded = list.getString(i);
            if (encoded.startsWith("/")) {
                try { rules.add(CategoryRule.regex(encoded)); }
                catch (IllegalArgumentException ignored) {}
                continue;
            }
            CategoryRule.Type type = encoded.startsWith("#") ? CategoryRule.Type.TAG
                    : encoded.startsWith("@") ? CategoryRule.Type.MOD_ID : CategoryRule.Type.ITEM;
            ResourceLocation target = type == CategoryRule.Type.MOD_ID
                    ? ResourceLocation.tryBuild(encoded.substring(1), "mod")
                    : ResourceLocation.tryParse(type == CategoryRule.Type.TAG ? encoded.substring(1) : encoded);
            if (target != null) rules.add(new CategoryRule(type, target));
        }
        return rules;
    }
}
