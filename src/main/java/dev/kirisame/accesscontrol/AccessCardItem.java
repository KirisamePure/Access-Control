package dev.kirisame.accesscontrol;

import net.minecraft.item.Item;

public final class AccessCardItem extends Item {
    private final int level;

    public AccessCardItem(int level, Settings settings) {
        super(settings.maxCount(1));
        if (!AccessRules.validLevel(level)) throw new IllegalArgumentException("Card level must be 1–4");
        this.level = level;
    }

    public int level() { return level; }
}
