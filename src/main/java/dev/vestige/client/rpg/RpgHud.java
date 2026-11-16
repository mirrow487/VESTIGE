package dev.vestige.client.rpg;

import dev.vestige.VestigeIds;
import dev.vestige.player.Flask;
import dev.vestige.player.PlayerModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * GDD §24 HUD: the Glim counter (bottom right) with a "+N" that rises and fades on every gain, and
 * the Wick Flask tile (the Readiness Cross's bottom tile, bottom left) with its charge count.
 */
public final class RpgHud {
    public static final ResourceLocation LAYER = VestigeIds.id("rpg");
    private static final int FRAME = 0xFF16151A, BACK = 0xC01C1A21, BRASS = 0xFF8A6D3B, EMBER = 0xFFE08A3C, PARCH = 0xFFE9DFC6;

    private static long shownGlim = -1;
    private static long gain;
    private static float gainTimer;
    /** For the driver: total Glim gains seen. */
    public static volatile long gainsSeen;

    private RpgHud() {}

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, LAYER, RpgHud::render);
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.options.hideGui || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) {
            return;
        }
        int w = g.guiWidth(), h = g.guiHeight();
        // --- Glim purse
        long glim = player.getData(PlayerModule.GLIM).amount();
        if (shownGlim < 0) {
            shownGlim = glim;
        } else if (glim != shownGlim) {
            if (glim > shownGlim) {
                gain = (gainTimer > 0 ? gain : 0) + (glim - shownGlim);
                gainTimer = 50f;
                gainsSeen++;
            }
            shownGlim = glim;
        }
        String text = String.format(java.util.Locale.ROOT, "%,d", glim);
        int tw = mc.font.width(text);
        int bx = w - tw - 30, by = h - 22;
        g.fill(bx - 3, by - 3, w - 6, by + 13, FRAME);
        g.fill(bx - 2, by - 2, w - 7, by + 12, BACK);
        g.fill(bx - 3, by - 3, bx - 1, by + 13, BRASS);
        // a small ember-gem glyph
        g.fill(bx + 2, by + 2, bx + 8, by + 8, EMBER);
        g.fill(bx + 3, by + 3, bx + 5, by + 5, 0xFFFFE2A8);
        g.drawString(mc.font, text, bx + 12, by + 1, PARCH, true);
        if (gainTimer > 0) {
            float t = gainTimer / 50f;
            int alpha = (int) (255 * Mth.clamp(t * 2f, 0f, 1f));
            int rise = (int) ((1f - t) * 10);
            String plus = "+" + String.format(java.util.Locale.ROOT, "%,d", gain);
            g.drawString(mc.font, plus, w - mc.font.width(plus) - 8, by - 12 - rise, (alpha << 24) | 0xF2D27A, true);
            gainTimer -= delta.getRealtimeDeltaTicks();
        }
        // --- Wick Flask tile (Readiness Cross, bottom)
        Flask flask = player.getData(PlayerModule.FLASK);
        int fx = 10, fy = h - 30, size = 22;
        g.fill(fx - 1, fy - 1, fx + size + 1, fy + size + 1, BRASS);
        g.fill(fx, fy, fx + size, fy + size, flask.charges() > 0 ? BACK : 0xC0140F10);
        g.renderItem(new ItemStack(PlayerModule.WICK_FLASK.get()), fx + 3, fy + 3);
        String charges = Integer.toString(flask.charges());
        g.drawString(mc.font, charges, fx + size - mc.font.width(charges) - 1, fy + size - 8,
                flask.charges() > 0 ? PARCH : 0xFF8A2F24, true);
        g.drawString(mc.font, "H", fx + 2, fy - 10, 0x80E9DFC6, false);
    }
}
