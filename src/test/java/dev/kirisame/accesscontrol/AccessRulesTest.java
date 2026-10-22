package dev.kirisame.accesscontrol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class AccessRulesTest {
    @ParameterizedTest
    @CsvSource({"1,1,true", "1,2,false", "1,3,false", "1,4,false",
            "2,1,true", "2,2,true", "2,3,false", "2,4,false",
            "3,1,true", "3,2,true", "3,3,true", "3,4,false",
            "4,1,true", "4,2,true", "4,3,true", "4,4,true"})
    void clearanceMatrix(int card, int reader, boolean expected) {
        assertEquals(expected, AccessRules.grants(card, reader));
    }
    @Test void malformedClearanceNeverGrants() {
        for (int invalid : new int[] {Integer.MIN_VALUE, -1, 0, 5, Integer.MAX_VALUE}) {
            for (int level = 1; level <= 4; level++) {
                assertFalse(AccessRules.grants(invalid, level));
                assertFalse(AccessRules.grants(level, invalid));
            }
        }
    }
    @Test void animationIsClampedMonotonicAndEasesAtEndpoints() {
        assertEquals(0, AccessRules.smoothSlide(-1));
        assertEquals(1, AccessRules.smoothSlide(2));
        assertEquals(.5f, AccessRules.smoothSlide(.5f), .00001f);
        float last = 0;
        for (int i = 0; i <= 100; i++) {
            float value = AccessRules.smoothSlide(i / 100f);
            assertTrue(value >= last && value >= 0 && value <= 1);
            last = value;
        }
        assertTrue(AccessRules.smoothSlide(.1f) < .1f);
        assertTrue(AccessRules.smoothSlide(.9f) > .9f);
    }
}
