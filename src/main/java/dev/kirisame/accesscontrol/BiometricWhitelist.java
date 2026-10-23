package dev.kirisame.accesscontrol;

import java.util.*;

/** Bounded, canonical UUID input; parsing completes before any stored list changes. */
public final class BiometricWhitelist {
    public static final int MAX_PLAYERS = 64;
    public static final int MAX_INPUT = 4096;
    private BiometricWhitelist() { }
    public static Set<UUID> parse(String text) {
        if (text.length() > MAX_INPUT) throw new IllegalArgumentException("Whitelist input too long");
        Set<UUID> result = new LinkedHashSet<>();
        for (String line : text.split("\\R")) {
            String token = line.trim();
            if (token.isEmpty()) continue;
            UUID uuid = UUID.fromString(token);
            if (!uuid.toString().equalsIgnoreCase(token)) throw new IllegalArgumentException("Use a full UUID");
            result.add(uuid);
            if (result.size() > MAX_PLAYERS) throw new IllegalArgumentException("Whitelist is full");
        }
        return result;
    }
}
