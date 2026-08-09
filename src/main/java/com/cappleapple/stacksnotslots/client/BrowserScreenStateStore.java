package com.cappleapple.stacksnotslots.client;

import com.cappleapple.stacksnotslots.config.ClientConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Persists one floating-browser state for each concrete container-screen class. */
final class BrowserScreenStateStore {
    private static final String VERSION = "v1";

    record State(
            String screenType,
            int x,
            int y,
            boolean open,
            boolean visible,
            ClientConfig.BrowserDockSide dockSide
    ) {}

    private BrowserScreenStateStore() {}

    static State load(String screenType) {
        List<? extends String> savedStates = ClientConfig.BROWSER_SCREEN_STATES.get();
        for (String encoded : savedStates) {
            Optional<State> decoded = decode(encoded);
            if (decoded.isPresent() && decoded.get().screenType().equals(screenType)) return decoded.get();
        }
        // Preserve the pre-0.6.1 global position once; subsequent unseen screens use the configured anchor.
        int fallbackX = savedStates.isEmpty() ? ClientConfig.BROWSER_HANDLE_X.getAsInt() : -1;
        int fallbackY = savedStates.isEmpty() ? ClientConfig.BROWSER_HANDLE_Y.getAsInt() : -1;
        return new State(screenType,
                fallbackX,
                fallbackY,
                false,
                ClientConfig.BROWSER_HANDLE_VISIBLE.getAsBoolean(),
                ClientConfig.BROWSER_DOCK_SIDE.get());
    }

    static void save(State state) {
        List<String> updated = new ArrayList<>();
        for (String encoded : ClientConfig.BROWSER_SCREEN_STATES.get()) {
            Optional<State> decoded = decode(encoded);
            if (decoded.isEmpty() || !decoded.get().screenType().equals(state.screenType())) updated.add(encoded);
        }
        updated.add(encode(state));
        ClientConfig.BROWSER_SCREEN_STATES.set(List.copyOf(updated));
        ClientConfig.SPEC.save();
    }

    static String encode(State state) {
        return String.join("|", VERSION, state.screenType(), Integer.toString(state.x()), Integer.toString(state.y()),
                Boolean.toString(state.open()), Boolean.toString(state.visible()), state.dockSide().name());
    }

    static Optional<State> decode(String encoded) {
        String[] parts = encoded.split("\\|", -1);
        if (parts.length != 7 || !VERSION.equals(parts[0]) || parts[1].isBlank()) return Optional.empty();
        try {
            if (!(parts[4].equals("true") || parts[4].equals("false"))
                    || !(parts[5].equals("true") || parts[5].equals("false"))) return Optional.empty();
            return Optional.of(new State(parts[1], Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                    Boolean.parseBoolean(parts[4]), Boolean.parseBoolean(parts[5]),
                    ClientConfig.BrowserDockSide.valueOf(parts[6])));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
