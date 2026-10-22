package dev.kirisame.accesscontrol;

/** Pure rules shared by gameplay and regression tests. */
public final class AccessRules {
    public static final int PULSE_TICKS = 20;
    private AccessRules() { }

    public static boolean validLevel(int level) { return level >= 1 && level <= 4; }

    public static boolean grants(int cardLevel, int requiredLevel) {
        return validLevel(cardLevel) && validLevel(requiredLevel) && cardLevel >= requiredLevel;
    }

    public static float smoothSlide(float progress) {
        float t = Math.max(0, Math.min(1, progress));
        return t * t * (3 - 2 * t);
    }
}
