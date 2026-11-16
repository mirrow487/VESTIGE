package dev.vestige.client.combat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Combat keybinds (GDD §7.2). They only act while armed and in-game. */
public final class CombatKeys {
    /** Active only while wielding a combat weapon with no screen open; never conflicts with vanilla binds. */
    public static final IKeyConflictContext COMBAT = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return Minecraft.getInstance().screen == null && ClientCombat.armed();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this;
        }
    };

    public static final String CATEGORY = "key.categories.vestige";
    public static final KeyMapping HEAVY = new KeyMapping("key.vestige.heavy", COMBAT, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping DODGE = new KeyMapping("key.vestige.dodge", COMBAT, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);
    public static final KeyMapping LOCK_ON = new KeyMapping("key.vestige.lock_on", COMBAT, InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, CATEGORY);

    private CombatKeys() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(HEAVY);
        event.register(DODGE);
        event.register(LOCK_ON);
    }
}
