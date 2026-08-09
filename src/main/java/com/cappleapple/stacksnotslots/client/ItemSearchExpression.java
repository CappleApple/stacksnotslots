package com.cappleapple.stacksnotslots.client;

import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Parsed search syntax shared by the logical-inventory browser and category rule editor. */
public final class ItemSearchExpression {
    public enum Mode { ALL, PLAIN, MOD, TAG, TOOLTIP, REGEX, TOOLTIP_REGEX }

    private static final int REGEX_FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private final Mode mode;
    private final String term;
    private final Pattern pattern;
    private final boolean valid;

    private ItemSearchExpression(Mode mode, String term, Pattern pattern, boolean valid) {
        this.mode = mode;
        this.term = term;
        this.pattern = pattern;
        this.valid = valid;
    }

    public static ItemSearchExpression parse(String raw) {
        String query = raw == null ? "" : raw.trim();
        if (query.isEmpty()) return new ItemSearchExpression(Mode.ALL, "", null, true);
        if (query.startsWith("^/")) return regex(Mode.TOOLTIP_REGEX, query.substring(2));
        if (query.startsWith("^")) return literal(Mode.TOOLTIP, query.substring(1));
        if (query.startsWith("/")) return regex(Mode.REGEX, query.substring(1));
        if (query.startsWith("@")) return literal(Mode.MOD, query.substring(1));
        if (query.startsWith("#")) return literal(Mode.TAG, query.substring(1));
        return literal(Mode.PLAIN, query);
    }

    private static ItemSearchExpression literal(Mode mode, String term) {
        return new ItemSearchExpression(mode, term.toLowerCase(Locale.ROOT), null, true);
    }

    private static ItemSearchExpression regex(Mode mode, String source) {
        String expression = stripClosingDelimiter(source);
        try {
            return new ItemSearchExpression(mode, expression, Pattern.compile(expression, REGEX_FLAGS), true);
        } catch (PatternSyntaxException ignored) {
            return new ItemSearchExpression(mode, expression, null, false);
        }
    }

    private static String stripClosingDelimiter(String expression) {
        int last = expression.length() - 1;
        if (last <= 0 || expression.charAt(last) != '/') return expression;
        int backslashes = 0;
        for (int index = last - 1; index >= 0 && expression.charAt(index) == '\\'; index--) backslashes++;
        return backslashes % 2 == 0 ? expression.substring(0, last) : expression;
    }

    public Mode mode() { return mode; }
    public String term() { return term; }
    public boolean valid() { return valid; }
    public boolean requiresTooltip() { return mode == Mode.TOOLTIP || mode == Mode.TOOLTIP_REGEX; }

    public boolean matches(ItemStack stack, Supplier<String> tooltipText) {
        if (!valid || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String namespace = id.getNamespace().toLowerCase(Locale.ROOT);
        String itemId = id.toString().toLowerCase(Locale.ROOT);
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case ALL -> true;
            case PLAIN -> name.contains(term) || itemId.contains(term) || namespace.contains(term);
            case MOD -> namespace.contains(term);
            case TAG -> stack.getTags().anyMatch(tag -> tag.location().toString().toLowerCase(Locale.ROOT).contains(term));
            case TOOLTIP -> {
                String tooltip = tooltipText.get().toLowerCase(Locale.ROOT);
                yield term.isEmpty() ? !tooltip.isEmpty() : tooltip.contains(term);
            }
            case REGEX -> find(name) || find(itemId) || find(namespace)
                    || stack.getTags().anyMatch(tag -> find(tag.location().toString()));
            case TOOLTIP_REGEX -> find(tooltipText.get());
        };
    }

    private boolean find(String value) {
        return pattern != null && pattern.matcher(value).find();
    }
}
