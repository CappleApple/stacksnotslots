package com.cappleapple.stacksnotslots.client;

/** Minimal text-editing state for the topmost browser search field. */
final class BrowserSearchQuery {
    private final int maximumLength;
    private String value = "";
    private boolean allSelected;

    BrowserSearchQuery(int maximumLength) {
        this.maximumLength = maximumLength;
    }

    String value() { return value; }
    boolean allSelected() { return allSelected && !value.isEmpty(); }

    void selectAll() {
        allSelected = !value.isEmpty();
    }

    void clearSelection() {
        allSelected = false;
    }

    boolean clear() {
        if (value.isEmpty()) {
            allSelected = false;
            return false;
        }
        value = "";
        allSelected = false;
        return true;
    }

    boolean backspace() {
        if (allSelected()) return clear();
        if (value.isEmpty()) return false;
        int previous = value.offsetByCodePoints(value.length(), -1);
        value = value.substring(0, previous);
        return true;
    }

    boolean deleteSelection() {
        return allSelected() && clear();
    }

    boolean append(int codePoint) {
        if (!Character.isValidCodePoint(codePoint) || Character.isISOControl(codePoint)) return false;
        String appended = new String(Character.toChars(codePoint));
        String base = allSelected() ? "" : value;
        if (base.length() + appended.length() > maximumLength) return false;
        value = base + appended;
        allSelected = false;
        return true;
    }
}
