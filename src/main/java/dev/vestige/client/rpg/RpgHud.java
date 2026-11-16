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
 * the Readiness Cross (bottom left): Main and Secondary weapons, the active Relic and the Wick Flask.
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
        // --- Readiness Cross (GDD §9.5, bottom left): Relic top, Secondary left, Main right, Flask bottom.
        var inv = player.getData(dev.vestige.equipment.EquipmentModule.RPG_INVENTORY);
        boolean stance = inv.inStance();
        int cx = 34, cy = h - 38;
        ItemStack mainStack = dev.vestige.equipment.Stance.main(player, inv);
        ItemStack secondStack = dev.vestige.equipment.Stance.secondary(player, inv);
        crossTile(g, cx + 21, cy, mainStack, stance ? EMBER : BRASS, null, stance ? "" : "V");
        crossTile(g, cx - 21, cy, secondStack, stance ? EMBER : BRASS, null, "");
        ItemStack relic = inv.activeRelicStack();
        crossTile(g, cx, cy - 21, relic, BRASS, null, relic.isEmpty() ? "" : "G");
        Flask flask = player.getData(PlayerModule.FLASK);
        crossTile(g, cx, cy + 21, new ItemStack(PlayerModule.WICK_FLASK.get()), flask.charges() > 0 ? BRASS : 0xFF8A2F24,
                Integer.toString(flask.charges()), "H");
        // the centre: a small brass rivet
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, BRASS);
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, stance ? EMBER : 0xFF3A2E1C);
        statuses(g, player);
        rendered = true;
    }

    /** Status buildup meters under the vitals (GDD §7.10): a filling bar while building, a draining bar while active. */
    private static void statuses(GuiGraphics g, net.minecraft.world.entity.player.Player player) {
        Minecraft mc = Minecraft.getInstance();
        var st = dev.vestige.combat.status.StatusService.state(player);
        int x = 8, y = 38;
        for (dev.vestige.stats.StatusType t : dev.vestige.stats.StatusType.values()) {
            float meter = st.meter(t);
            int timer = st.timer(t);
            boolean bitten = t == dev.vestige.stats.StatusType.VOIDBITE && st.voidbitten();
            if (meter <= 0f && timer <= 0 && !bitten) {
                continue;
            }
            boolean active = timer > 0 || bitten;
            float fill = active ? (bitten ? 1f : Mth.clamp(timer / (float) (t == dev.vestige.stats.StatusType.HUSHED
                    ? dev.vestige.combat.status.StatusService.HUSHED_TICKS : dev.vestige.combat.status.StatusService.SCORCH_TICKS), 0f, 1f))
                    : meter / dev.vestige.combat.status.StatusService.THRESHOLD;
            // a small diamond glyph in the status colour
            g.fill(x + 2, y, x + 4, y + 6, t.color);
            g.fill(x + 1, y + 1, x + 5, y + 5, t.color);
            g.fill(x, y + 2, x + 6, y + 4, t.color);
            int bx = x + 9, bw = 44;
            g.fill(bx - 1, y, bx + bw + 1, y + 6, FRAME);
            g.fill(bx, y + 1, bx + Math.round(bw * Mth.clamp(fill, 0f, 1f)), y + 5, active ? t.color : (t.color & 0x00FFFFFF) | 0xA0000000);
            String name = t.name().charAt(0) + t.name().substring(1).toLowerCase(java.util.Locale.ROOT);
            g.drawString(mc.font, active ? name.toUpperCase(java.util.Locale.ROOT) : name, bx + bw + 4, y - 1, active ? t.color : 0xB0E9DFC6, true);
            y += 9;
        }
    }

    /** For the driver. */
    public static volatile boolean rendered;

    private static void crossTile(GuiGraphics g, int cx, int cy, ItemStack stack, int trim, String count, String key) {
        Minecraft mc = Minecraft.getInstance();
        int x = cx - 10, y = cy - 10, size = 20;
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, trim);
        g.fill(x, y, x + size, y + size, stack.isEmpty() ? 0x901C1A21 : BACK);
        if (!stack.isEmpty()) {
            g.renderItem(stack, x + 2, y + 2);
        } else {
            g.fill(x + 6, y + 9, x + 14, y + 11, 0x40E9DFC6);
        }
        if (count != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.drawString(mc.font, count, x + size - mc.font.width(count), y + size - 8, "0".equals(count) ? 0xFF8A2F24 : PARCH, true);
            g.pose().popPose();
        }
        if (!key.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.drawString(mc.font, key, x + 1, y + 1, 0x80E9DFC6, false);
            g.pose().popPose();
        }
    }
}
