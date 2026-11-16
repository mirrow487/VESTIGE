package dev.vestige.combat.action;

import net.minecraft.resources.ResourceLocation;

/** Reference to an action inside a moveset; serialized as {@code namespace:moveset#action}. */
public record ActionRef(ResourceLocation moveset, String action) {
    public static ActionRef parse(String text) {
        int hash = text.indexOf('#');
        if (hash < 0) {
            throw new IllegalArgumentException("Action reference needs '#': " + text);
        }
        return new ActionRef(ResourceLocation.parse(text.substring(0, hash)), text.substring(hash + 1));
    }

    @Override
    public String toString() {
        return moveset + "#" + action;
    }
}
