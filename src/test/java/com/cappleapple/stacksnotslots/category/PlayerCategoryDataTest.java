package com.cappleapple.stacksnotslots.category;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlayerCategoryDataTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void exactExclusionWinsAndRemovedReferencesRoundTripSafely() {
        ResourceLocation apple = BuiltInRegistries.ITEM.getKey(Items.APPLE);
        ResourceLocation missing = ResourceLocation.fromNamespaceAndPath("removed_mod", "missing_item");
        CategoryDefinition definition = new CategoryDefinition(
                ResourceLocation.fromNamespaceAndPath("stacksnotslots", "test"), "Test", apple, 4,
                List.of(new CategoryRule(CategoryRule.Type.ITEM, apple), new CategoryRule(CategoryRule.Type.ITEM, missing)),
                List.of(new CategoryRule(CategoryRule.Type.ITEM, apple)), 64, SortMode.REGISTRY_ID, true, false);
        assertFalse(CategoryMatcher.matches(definition, new ItemStack(Items.APPLE)));

        PlayerCategoryData original = new PlayerCategoryData();
        original.replaceAll(List.of(definition), true);
        PlayerCategoryData loaded = new PlayerCategoryData();
        loaded.load(original.save());
        assertTrue(loaded.initializedFromDefaults());
        assertEquals(missing, loaded.categories().getFirst().includes().get(1).target());
        assertFalse(CategoryMatcher.matches(loaded.categories().getFirst(), new ItemStack(Items.DIAMOND)));
    }

    @Test
    void allItemsStillHonorsExclusions() {
        ResourceLocation apple = BuiltInRegistries.ITEM.getKey(Items.APPLE);
        CategoryDefinition allExceptApples = new CategoryDefinition(
                ResourceLocation.fromNamespaceAndPath("stacksnotslots", "all_except"), "All Except", apple, 0,
                List.of(), List.of(new CategoryRule(CategoryRule.Type.ITEM, apple)), -1,
                SortMode.NAME_ASCENDING, true, true);
        assertFalse(CategoryMatcher.matches(allExceptApples, new ItemStack(Items.APPLE)));
        assertTrue(CategoryMatcher.matches(allExceptApples, new ItemStack(Items.DIAMOND)));
    }

    @Test
    void modNamespaceRulesMatchAndRoundTrip() {
        ResourceLocation minecraftNamespace = ResourceLocation.fromNamespaceAndPath("minecraft", "mod");
        CategoryDefinition definition = new CategoryDefinition(
                ResourceLocation.fromNamespaceAndPath("stacksnotslots", "minecraft_items"), "Minecraft", minecraftNamespace, 0,
                List.of(new CategoryRule(CategoryRule.Type.MOD_ID, minecraftNamespace)), List.of(), -1,
                SortMode.MOD_NAMESPACE, true, false);

        assertTrue(CategoryMatcher.matches(definition, new ItemStack(Items.APPLE)));
        PlayerCategoryData loaded = new PlayerCategoryData();
        PlayerCategoryData original = new PlayerCategoryData();
        original.replaceAll(List.of(definition), true);
        loaded.load(original.save());
        assertEquals(CategoryRule.Type.MOD_ID, loaded.categories().getFirst().includes().getFirst().type());
        assertEquals("minecraft", loaded.categories().getFirst().includes().getFirst().target().getNamespace());
    }

    @Test
    void presetSchemaAcceptsAtPrefixedModRules() {
        String json = """
                {"schemaVersion":1,"presets":[{
                  "id":"stacksnotslots:mod_test","name":"Mod Test","icon":"minecraft:apple",
                  "include":["@minecraft"],"exclude":[],"sort":"namespace"
                }]}
                """;
        CategoryDefinition parsed = CategoryPresetManager.parse(json).getFirst();
        assertEquals(CategoryRule.Type.MOD_ID, parsed.includes().getFirst().type());
        assertTrue(CategoryMatcher.matches(parsed, new ItemStack(Items.DIAMOND)));
    }

    @Test
    void regexRulesAreDynamicAndRoundTrip() {
        CategoryRule swordRule = CategoryRule.regex("/sword/");
        CategoryDefinition swords = new CategoryDefinition(
                ResourceLocation.fromNamespaceAndPath("stacksnotslots", "swords"), "Swords",
                BuiltInRegistries.ITEM.getKey(Items.IRON_SWORD), 0,
                List.of(swordRule), List.of(), -1, SortMode.NAME_ASCENDING, true, false);

        assertTrue(CategoryMatcher.matches(swords, new ItemStack(Items.DIAMOND_SWORD)));
        assertFalse(CategoryMatcher.matches(swords, new ItemStack(Items.DIAMOND_PICKAXE)));
        PlayerCategoryData original = new PlayerCategoryData();
        original.replaceAll(List.of(swords), true);
        PlayerCategoryData loaded = new PlayerCategoryData();
        loaded.load(original.save());
        assertEquals(CategoryRule.Type.REGEX, loaded.categories().getFirst().includes().getFirst().type());
        assertEquals("sword", loaded.categories().getFirst().includes().getFirst().expression());
    }

    @Test
    void blockFieldLetsRegexRulesSelectEveryBlockItem() {
        CategoryRule blockRule = CategoryRule.regex("/^block:");
        CategoryDefinition blocks = new CategoryDefinition(
                ResourceLocation.fromNamespaceAndPath("stacksnotslots", "blocks"), "Blocks",
                BuiltInRegistries.ITEM.getKey(Items.STONE), 0,
                List.of(blockRule), List.of(), -1, SortMode.NAME_ASCENDING, true, false);

        assertTrue(CategoryMatcher.matches(blocks, new ItemStack(Items.STONE)));
        assertFalse(CategoryMatcher.matches(blocks, new ItemStack(Items.STICK)));
    }

    @Test
    void presetSchemaAcceptsRegexRules() {
        String json = """
                {"schemaVersion":1,"presets":[{
                  "id":"stacksnotslots:swords","name":"Swords","icon":"minecraft:iron_sword",
                  "include":["/sword"],"exclude":[],"sort":"name"
                }]}
                """;
        CategoryDefinition parsed = CategoryPresetManager.parse(json).getFirst();
        assertEquals(CategoryRule.Type.REGEX, parsed.includes().getFirst().type());
        assertTrue(CategoryMatcher.matches(parsed, new ItemStack(Items.IRON_SWORD)));
    }

    @Test
    void bundledPresetsUseTheValidatedExternalSchema() throws Exception {
        String json;
        try (var input = PlayerCategoryDataTest.class.getResourceAsStream("/default_categories.json")) {
            assertTrue(input != null);
            json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<CategoryDefinition> presets = CategoryPresetManager.parse(json);
        assertEquals(14, presets.size());
        assertTrue(presets.stream().anyMatch(category -> category.id().getPath().equals("miscellaneous")));
        CategoryDefinition blocks = presets.stream()
                .filter(category -> category.id().getPath().equals("blocks")).findFirst().orElseThrow();
        assertEquals(CategoryRule.Type.REGEX, blocks.includes().getFirst().type());
        assertEquals("^block:", blocks.includes().getFirst().expression());
        assertFalse(presets.stream().flatMap(category -> category.includes().stream())
                .anyMatch(rule -> "#c:blocks".equals(rule.encoded())));
    }
}
