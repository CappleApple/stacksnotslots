package com.cappleapple.stacksnotslots.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BrowserSearchQueryTest {
    @Test
    void selectAllCanDeleteOrReplaceTheWholeQuery() {
        BrowserSearchQuery query = new BrowserSearchQuery(16);
        query.append('s');
        query.append('t');
        query.selectAll();
        assertTrue(query.allSelected());
        assertTrue(query.deleteSelection());
        assertEquals("", query.value());

        query.append('o');
        query.append('l');
        query.append('d');
        query.selectAll();
        query.append('n');
        assertEquals("n", query.value());
        assertFalse(query.allSelected());
    }

    @Test
    void backspaceRemovesOneCodePoint() {
        BrowserSearchQuery query = new BrowserSearchQuery(16);
        query.append('a');
        query.append(0x1F50D);
        query.backspace();
        assertEquals("a", query.value());
    }
}
