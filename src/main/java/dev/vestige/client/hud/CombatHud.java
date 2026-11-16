package dev.vestige.client.hud;

import dev.vestige.VestigeIds;
import dev.vestige.vitals.Stamina;
import dev.vestige.vitals.VitalsModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Health and stamina bars (GDD §24): top-left, widths grow with the maximum, a trailing "damage
 * taken" chunk, and a flash when stamina runs out. Replaces the vanilla heart row.
 */
public final class CombatHud {
    public static final ResourceLocation LAYER = VestigeIds.id("vitals");
    private static final int X = 8, Y = 8;
    private static final int FRAME = 0xFF16151A, BACK = 0xC01C1A21, BRASS = 0xFF8A6D3B;
    private static final int HP = 0xFFA3262A, HP_HI = 0xFFC94B4B, TRAIL = 0xFFD9B24A;
    private static final int STA = 0xFF3F8F4A, STA_HI = 0xFF63B36C, STA_EMPTY = 0xFF8A2F24;

    private static float trailHealth = -1f;
    private static long trailHoldUntil;
    private static float flash;
    /** For tests/driver: true once the layer has drawn. */
    public static volatile boolean rendered;

    private CombatHud() {}

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.PLAYER_HEALTH, LAYER, CombatHud::render);
    }

    /** Hide vanilla hearts; VESTIGE health is a bar. */
    public static void hideVanilla(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH)) {
            event.setCanceled(true);
        }
    }

    public static void flashPerfectDodge() {
        flash = 1f;
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) {
            return;
        }
        rendered = true;
        float max = player.getMaxHealth();
        float hp = Mth.clamp(player.getHealth(), 0f, max);
        long now = player.level().getGameTime();
        if (trailHealth < 0f || hp > trailHealth) {
            trailHealth = hp;
        } else if (hp < trailHealth && now > trailHoldUntil) {
            trailHealth = Math.max(hp, trailHealth - max * 0.012f);
        }
        if (hp < trailHealth && trailHoldUntil < now - 30) {
            trailHoldUntil = now + 12;
        }
        int hpWidth = Mth.clamp(Math.round(max * 1.2f), 60, 260);
        bar(g, X, Y, hpWidth, 6, hp / max, trailHealth / max, HP, HP_HI, TRAIL);

        Stamina st = player.getData(VitalsModule.STAMINA);
        float smax = Math.max(1f, st.max());
        int stWidth = Mth.clamp(Math.round(smax * 1.2f), 60, 260);
        boolean empty = st.current() <= 0.01f;
        int stColor = empty && (now / 4) % 2 == 0 ? STA_EMPTY : STA;
        bar(g, X, Y + 10, stWidth, 4, st.current() / smax, -1f, stColor, STA_HI, 0);

        if (flash > 0f) {
            int a = (int) (flash * 90);
            int w = g.guiWidth(), h = g.guiHeight();
            g.fillGradient(0, 0, w, h / 5, (a << 24) | 0xE8F0FF, 0x00E8F0FF);
            g.fillGradient(0, h - h / 5, w, h, 0x00E8F0FF, (a << 24) | 0xE8F0FF);
            flash = Math.max(0f, flash - delta.getRealtimeDeltaTicks() * 0.12f);
        }
    }

    /** A framed bar: dark frame, brass end caps, fill with a highlight row and optional trail. */
    private static void bar(GuiGraphics g, int x, int y, int w, int h, float fill, float trail, int color, int highlight, int trailColor) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, FRAME);
        g.fill(x, y, x + w, y + h, BACK);
        if (trail > fill) {
            g.fill(x, y, x + Math.round(w * Mth.clamp(trail, 0f, 1f)), y + h, trailColor);
        }
        int fw = Math.round(w * Mth.clamp(fill, 0f, 1f));
        g.fill(x, y, x + fw, y + h, color);
        g.fill(x, y, x + fw, y + 1, highlight);
        g.fill(x - 2, y - 2, x, y + h + 2, BRASS);
        g.fill(x + w, y - 2, x + w + 2, y + h + 2, BRASS);
    }
}
