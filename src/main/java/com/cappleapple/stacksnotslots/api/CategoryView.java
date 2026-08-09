package com.cappleapple.stacksnotslots.api;

import com.cappleapple.stacksnotslots.category.CategoryDefinition;
import com.cappleapple.stacksnotslots.category.CategoryRule;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** Immutable public category metadata. Categories are views and never own inventory contents. */
public record CategoryView(
        ResourceLocation id,
        String displayName,
        ResourceLocation icon,
        int order,
        List<CategoryRuleView> includes,
        List<CategoryRuleView> excludes,
        long pickupLimit,
        String sortMode,
        boolean enabled,
        boolean allItems
) {
    public CategoryView {
        includes = List.copyOf(includes);
        excludes = List.copyOf(excludes);
    }

    static CategoryView fromDefinition(CategoryDefinition definition) {
        return new CategoryView(definition.id(), definition.displayName(), definition.icon(), definition.order(),
                definition.includes().stream().map(CategoryView::ruleView).toList(),
                definition.excludes().stream().map(CategoryView::ruleView).toList(),
                definition.pickupLimit(), definition.sortMode().name(), definition.enabled(), definition.allItems());
    }

    private static CategoryRuleView ruleView(CategoryRule rule) {
        CategoryRuleView.Type type = switch (rule.type()) {
            case ITEM -> CategoryRuleView.Type.ITEM;
            case TAG -> CategoryRuleView.Type.TAG;
            case MOD_ID -> CategoryRuleView.Type.MOD_ID;
            case REGEX -> CategoryRuleView.Type.REGEX;
        };
        return new CategoryRuleView(type, rule.target(), rule.expression());
    }
}
