package dev.kirisame.accesscontrol;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BiometricWhitelistTest {
    @Test void emptyMeansNobodyAndDuplicateIdentityCountsOnce() {
        assertTrue(BiometricWhitelist.parse(" \n\n ").isEmpty());
        UUID id = UUID.randomUUID();
        assertEquals(Set.of(id), BiometricWhitelist.parse(id + "\n" + id.toString().toUpperCase(Locale.ROOT)));
    }
    @Test void malformedOrAbbreviatedIdentityIsRejected() {
        for (String value : List.of("PlayerName", "1-1-1-1-1", UUID.randomUUID() + "\ninvalid"))
            assertThrows(IllegalArgumentException.class, () -> BiometricWhitelist.parse(value));
    }
    @Test void capacityBoundaryIsEnforced() {
        String full = IntStream.range(0, 64).mapToObj(i -> new UUID(0, i).toString()).collect(Collectors.joining("\n"));
        assertEquals(64, BiometricWhitelist.parse(full).size());
        assertThrows(IllegalArgumentException.class, () -> BiometricWhitelist.parse(full + "\n" + new UUID(0, 64)));
    }
    @Test void oversizedInputIsRejectedBeforeParsing() {
        assertThrows(IllegalArgumentException.class, () -> BiometricWhitelist.parse(" ".repeat(4097)));
    }
    @Test void teamNamesAreExactAndBounded() {
        assertEquals(Set.of("Staff", "staff"), BiometricWhitelist.parseTeams("Staff\nstaff\nStaff"));
        assertTrue(BiometricWhitelist.parseTeams("").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> BiometricWhitelist.parseTeams("two words"));
        assertThrows(IllegalArgumentException.class, () -> BiometricWhitelist.parseTeams("a".repeat(65)));
        String full = IntStream.range(0, 64).mapToObj(i -> "team_" + i).collect(Collectors.joining("\n"));
        assertEquals(64, BiometricWhitelist.parseTeams(full).size());
        assertThrows(IllegalArgumentException.class, () -> BiometricWhitelist.parseTeams(full + "\nextra"));
    }
}
