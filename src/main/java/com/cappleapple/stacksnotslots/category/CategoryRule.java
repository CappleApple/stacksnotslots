package com.cappleapple.stacksnotslots.category;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** One durable category predicate. Regex rules search item metadata and represented block IDs. */
public final class CategoryRule {
    private static final int REGEX_FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private final Type type;
    private final @Nullable ResourceLocation target;
    private final @Nullable String expression;
    private final @Nullable Pattern pattern;

    public CategoryRule(Type type, ResourceLocation target) {
        if (type == Type.REGEX) throw new IllegalArgumentException("Use CategoryRule.regex for regex rules");
        this.type = Objects.requireNonNull(type);
        this.target = Objects.requireNonNull(target);
        this.expression = null;
        this.pattern = null;
    }

    private CategoryRule(String expression) throws PatternSyntaxException {
        this.type = Type.REGEX;
        this.target = null;
        this.expression = stripClosingDelimiter(Objects.requireNonNull(expression));
        this.pattern = Pattern.compile(this.expression, REGEX_FLAGS);
    }

    public static CategoryRule regex(String expression) {
        String source = expression.startsWith("/") ? expression.substring(1) : expression;
        return new CategoryRule(source);
    }

    public Type type() { return type; }
    public @Nullable ResourceLocation target() { return target; }
    public @Nullable String expression() { return expression; }
    public boolean finds(String value) { return pattern != null && pattern.matcher(value).find(); }

    public String encoded() {
        return switch (type) {
            case ITEM -> target.toString();
            case TAG -> "#" + target;
            case MOD_ID -> "@" + target.getNamespace();
            case REGEX -> "/" + expression;
        };
    }

    private static String stripClosingDelimiter(String source) {
        int last = source.length() - 1;
        if (last <= 0 || source.charAt(last) != '/') return source;
        int backslashes = 0;
        for (int index = last - 1; index >= 0 && source.charAt(index) == '\\'; index--) backslashes++;
        return backslashes % 2 == 0 ? source.substring(0, last) : source;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CategoryRule rule
                && type == rule.type && Objects.equals(target, rule.target) && Objects.equals(expression, rule.expression);
    }

    @Override public int hashCode() { return Objects.hash(type, target, expression); }
    @Override public String toString() { return encoded(); }

    public enum Type { ITEM, TAG, MOD_ID, REGEX }
}
