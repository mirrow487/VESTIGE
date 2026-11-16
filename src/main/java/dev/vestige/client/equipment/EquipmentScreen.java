package dev.vestige.client.equipment;

import com.mojang.math.Axis;
import dev.vestige.combat.player.PlayerAttack;
import dev.vestige.equipment.EquipSlot;
import dev.vestige.equipment.EquipmentData;
import dev.vestige.equipment.EquipmentMenu;
import dev.vestige.equipment.EquipmentModule;
import dev.vestige.equipment.EquipmentNetwork;
import dev.vestige.equipment.EquipmentRules;
import dev.vestige.player.PlayerModule;
import dev.vestige.stats.Attribute;
import dev.vestige.stats.Burden;
import dev.vestige.stats.DamageSpread;
import dev.vestige.stats.DamageType;
import dev.vestige.stats.Rarity;
import dev.vestige.stats.StatSheet;
import dev.vestige.stats.Stats;
import dev.vestige.weapon.WeaponProfiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The equipment screen (GDD §9.5): a live, rotatable paper-doll inside the Readiness Cross (Main,
 * Secondary, active Relic, Wick Flask), armor on the left, accessories on the right, the character's
 * stats below and the inventory at the bottom, all in the slate-tile style of the reference layout.
 */
public class EquipmentScreen extends AbstractContainerScreen<EquipmentMenu> {
    static final int SLATE_A = 0xF0121116, SLATE_B = 0xF01C1A21, FRAME = 0xFF5D5A55, SHADOW = 0xFF050406, RULE = 0xFF7A6F5C;
    static final int PARCH = 0xFFD9CFB8, PARCH_DIM = 0xB3D9CFB8, EMBER = 0xFFE08A3C, MUTED = 0xFF8C8475, BAD = 0xFFD86A5A;
    private static final int DOLL_X = 132, DOLL_Y = 106, CROSS_TOP_Y = 18, CROSS_BOTTOM_Y = 112;
    private float dollYaw = 200f, dollTargetYaw = 200f;
    private boolean dragging;
    private long openedAt;
    /** For the driver. */
    public static volatile EquipmentScreen current;

    public EquipmentScreen(EquipmentMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = EquipmentMenu.WIDTH;
        imageHeight = EquipmentMenu.HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        current = this;
        openedAt = System.currentTimeMillis();
    }

    @Override
    public void removed() {
        super.removed();
        current = null;
    }

    public void rotateDoll(float yaw) {
        dollTargetYaw = yaw;
        dollYaw = yaw;
    }

    private float fade() {
        return Mth.clamp((System.currentTimeMillis() - openedAt) / 180f, 0f, 1f);
    }

    // ------------------------------------------------------------------ drawing helpers

    /** A slate tile: dark vignette, pale stone frame, shadow line, hairline rule (rarity-tinted when filled). */
    static void tile(GuiGraphics g, int x, int y, int w, int h, boolean hovered, int ruleColor, boolean locked) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, SHADOW);
        g.fill(x, y, x + w, y + h, FRAME);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, SLATE_B, SLATE_A);
        g.fill(x + 2, y + 2, x + w - 2, y + 3, 0x18FFFFFF);
        if (hovered) {
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x22F2D27A);
        }
        int rw = Math.min(14, w - 6);
        g.fill(x + (w - rw) / 2, y + h - 4, x + (w + rw) / 2, y + h - 3, ruleColor);
        if (locked) {
            for (int i = 2; i < w - 2; i += 3) {
                g.fill(x + i, y + 2 + (i % 6), x + i + 2, y + 3 + (i % 6), 0xFF4A4740);
                g.fill(x + i, y + h - 6 - (i % 6), x + i + 2, y + h - 5 - (i % 6), 0xFF4A4740);
            }
            g.fill(x + w / 2 - 3, y + h / 2 - 2, x + w / 2 + 3, y + h / 2 + 3, 0xFF6E6A62);
            g.fill(x + w / 2 - 2, y + h / 2 - 5, x + w / 2 + 2, y + h / 2 - 2, 0xFF6E6A62);
            g.fill(x + w / 2 - 1, y + h / 2 - 4, x + w / 2 + 1, y + h / 2 - 2, SLATE_A);
        }
    }

    /** 9x9 ghost silhouettes of each slot's category (GDD §9.5), drawn at ~25% opacity. */
    private static final String[] GHOST_SWORD = {"       ##", "      ###", "     ### ", "    ###  ", " # ###   ", "  ###    ", "  ##     ", " #  #    ", "#        "};
    private static final String[] GHOST_SHIELD = {" ####### ", "#########", "#########", "#########", "#########", " ####### ", "  #####  ", "   ###   ", "    #    "};
    private static final String[] GHOST_HELM = {"  #####  ", " ####### ", "#########", "#########", "##     ##", "## ### ##", "#########", "### # ###", "##     ##"};
    private static final String[] GHOST_CHEST = {"##     ##", "###   ###", "#########", " ####### ", " ####### ", " ####### ", " ####### ", " ####### ", " ####### "};
    private static final String[] GHOST_LEGS = {"#########", "#########", "###   ###", "###   ###", "###   ###", "###   ###", "###   ###", "###   ###", "###   ###"};
    private static final String[] GHOST_BOOTS = {"         ", "###   ###", "###   ###", "###   ###", "###   ###", "####  ####", "#### ####", "         ", "         "};
    private static final String[] GHOST_RING = {"   ###   ", "  #####  ", "   ###   ", "  #   #  ", " #     # ", " #     # ", " #     # ", "  #   #  ", "   ###   "};
    private static final String[] GHOST_TALISMAN = {"    #    ", "   ###   ", "  #####  ", " ### ### ", "###   ###", " ### ### ", "  #####  ", "   ###   ", "    #    "};
    private static final String[] GHOST_RELIC = {"#########", "#       #", "# ##### #", "# #   # #", "# # # # #", "# #   # #", "# ##### #", "#       #", "#########"};
    private static final String[] GHOST_FLASK = {"   ###   ", "    #    ", "   ###   ", "  #####  ", " ####### ", "#########", "#########", "#########", " ####### "};

    static void ghost(GuiGraphics g, String[] shape, int cx, int cy) {
        for (int y = 0; y < shape.length; y++) {
            for (int x = 0; x < shape[y].length(); x++) {
                if (shape[y].charAt(x) == '#') {
                    g.fill(cx - 4 + x, cy - 4 + y, cx - 3 + x, cy - 3 + y, 0x40D9CFB8);
                }
            }
        }
    }

    private static String[] ghostFor(EquipSlot s) {
        return switch (s.kind) {
            case WEAPON -> GHOST_SWORD;
            case SECONDARY -> GHOST_SHIELD;
            case RING -> GHOST_RING;
            case TALISMAN -> GHOST_TALISMAN;
            case RELIC -> GHOST_RELIC;
        };
    }

    static int rarityColor(ItemStack stack) {
        Rarity r = WeaponProfiles.of(stack, true).map(w -> w.rarity())
                .or(() -> EquipmentData.armor(stack, true).map(a -> a.rarity()))
                .or(() -> EquipmentData.accessory(stack, true).map(a -> a.rarity()))
                .orElse(null);
        return r == null ? RULE : r.color;
    }

    private void label(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(font, s, x, y, color, false);
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.renderBackground(g, mouseX, mouseY, partial);
        // Gritty dark overlay over the blurred world (GDD §9.5).
        int a = (int) (0xB8 * fade());
        g.fill(0, 0, width, height, a << 24);
        long seed = 0x9E3779B9L;
        for (int i = 0; i < 900; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int x = (int) ((seed >>> 33) % Math.max(1, width));
            int y = (int) ((seed >>> 13) % Math.max(1, height));
            g.fill(x, y, x + 1, y + 1, ((int) (0x14 * fade()) << 24) | 0xFFFFFF);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int x0 = leftPos, y0 = topPos;
        StatSheet s = Stats.of(minecraft.player);
        // Header: title, character level and the burden bar.
        g.fill(x0 + 9, y0 + 5, x0 + 13, y0 + 11, EMBER);
        g.fill(x0 + 10, y0 + 3, x0 + 12, y0 + 6, 0xFFFFE2A8);
        label(g, "EQUIPMENT", x0 + 17, y0 + 4, PARCH);
        burden(g, s, x0 + imageWidth - 124, y0 + 4);
        g.fill(x0 + 6, y0 + 14, x0 + imageWidth - 6, y0 + 15, 0x805D5A55);
        // Section labels.
        label(g, "ARMOR", x0 + 8, y0 + 17, PARCH_DIM);
        label(g, "RINGS", x0 + 214, y0 + 17, PARCH_DIM);
        label(g, "TALISMAN", x0 + 214, y0 + 51, PARCH_DIM);
        label(g, "RELICS", x0 + 214, y0 + 85, PARCH_DIM);
        // Tiles under every slot.
        for (Slot slot : menu.slots) {
            int sx = x0 + slot.x - 2, sy = y0 + slot.y - 2;
            boolean hovered = mouseX >= sx && mouseX < sx + 20 && mouseY >= sy && mouseY < sy + 20;
            boolean locked = slot instanceof EquipmentMenu.RpgSlot rs && !EquipmentRules.unlocked(minecraft.player, rs.equipSlot);
            boolean big = slot instanceof EquipmentMenu.RpgSlot rs2 && (rs2.equipSlot == EquipSlot.MAIN || rs2.equipSlot == EquipSlot.SECONDARY);
            if (big) {
                tile(g, sx - 3, sy - 3, 26, 26, hovered, slot.hasItem() ? rarityColor(slot.getItem()) : RULE, false);
            } else {
                tile(g, sx, sy, 20, 20, hovered, slot.hasItem() ? rarityColor(slot.getItem()) : RULE, locked);
            }
            if (!slot.hasItem() && !locked) {
                if (slot instanceof EquipmentMenu.RpgSlot rs) {
                    ghost(g, ghostFor(rs.equipSlot), sx + 10, sy + 10);
                } else if (slot.index >= EquipmentMenu.ARMOR_START && slot.index < EquipmentMenu.INV_START) {
                    int i = slot.index - EquipmentMenu.ARMOR_START;
                    ghost(g, new String[][] {GHOST_HELM, GHOST_CHEST, GHOST_LEGS, GHOST_BOOTS}[i], sx + 10, sy + 10);
                }
            }
        }
        // The cross: active relic (top) and Wick Flask (bottom) around the doll.
        int cx = x0 + DOLL_X - 13;
        var inv = minecraft.player.getData(EquipmentModule.RPG_INVENTORY);
        ItemStack relic = inv.activeRelicStack();
        tile(g, cx, y0 + CROSS_TOP_Y, 26, 26, inside(mouseX, mouseY, cx, y0 + CROSS_TOP_Y, 26), relic.isEmpty() ? RULE : rarityColor(relic), false);
        if (relic.isEmpty()) {
            ghost(g, GHOST_RELIC, cx + 13, y0 + CROSS_TOP_Y + 13);
        } else {
            g.renderItem(relic, cx + 5, y0 + CROSS_TOP_Y + 5);
        }
        tile(g, cx, y0 + CROSS_BOTTOM_Y, 26, 26, inside(mouseX, mouseY, cx, y0 + CROSS_BOTTOM_Y, 26), EMBER, false);
        var flask = minecraft.player.getData(PlayerModule.FLASK);
        g.renderItem(new ItemStack(PlayerModule.WICK_FLASK.get()), cx + 5, y0 + CROSS_BOTTOM_Y + 4);
        String charges = Integer.toString(flask.charges());
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.drawString(font, charges, cx + 23 - font.width(charges), y0 + CROSS_BOTTOM_Y + 16, flask.charges() > 0 ? PARCH : BAD, true);
        g.pose().popPose();
        if (flask.kindling() > 0) {
            g.drawString(font, "+" + flask.kindling(), cx + 2, y0 + CROSS_BOTTOM_Y + 2, EMBER, true);
        }
        doll(g, mouseX, mouseY, partial);
        stats(g, s, x0, y0);
    }

    private static boolean inside(int mx, int my, int x, int y, int size) {
        return mx >= x && mx < x + size && my >= y && my < y + size;
    }

    private void burden(GuiGraphics g, StatSheet s, int x, int y) {
        float ratio = s.equipCapacity <= 0 ? 0 : s.equipLoad / s.equipCapacity;
        int w = 60;
        int color = switch (s.burden) {
            case LIGHT -> 0xFF7FBF6A;
            case MEDIUM -> 0xFFE8B84A;
            case HEAVY -> 0xFFE08A3C;
            case OVERBURDENED -> 0xFFD86A5A;
        };
        label(g, "Burden", x, y, PARCH_DIM);
        int bx = x + 40;
        g.fill(bx - 1, y + 1, bx + w + 1, y + 8, SHADOW);
        g.fill(bx, y + 2, bx + w, y + 7, 0xFF26232A);
        g.fill(bx, y + 2, bx + Math.min(w, Math.round(w * ratio)), y + 7, color);
        for (float t : new float[] {0.3f, 0.7f}) {
            int tx = bx + Math.round(w * t * (1f + s.burdenThresholdBonus));
            g.fill(tx, y + 1, tx + 1, y + 8, 0xFF5D5A55);
        }
        String cls = s.burden.name().charAt(0) + s.burden.name().substring(1).toLowerCase(Locale.ROOT);
        g.drawString(font, cls, bx + w + 4, y, color, false);
    }

    private void doll(GuiGraphics g, int mouseX, int mouseY, float partial) {
        dollYaw += (dollTargetYaw - dollYaw) * 0.25f;
        var player = minecraft.player;
        float bodyRot = player.yBodyRot, yRot = player.getYRot(), xRot = player.getXRot(), headO = player.yHeadRotO, head = player.yHeadRot;
        player.yBodyRot = dollYaw;
        player.setYRot(dollYaw);
        player.setXRot(0f);
        player.yHeadRot = dollYaw;
        player.yHeadRotO = dollYaw;
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = Axis.XP.rotationDegrees(-8f);
        pose.mul(camera);
        InventoryScreen.renderEntityInInventory(g, leftPos + DOLL_X, topPos + DOLL_Y, 30f, new Vector3f(0, 0, 0), pose, camera, player);
        player.yBodyRot = bodyRot;
        player.setYRot(yRot);
        player.setXRot(xRot);
        player.yHeadRotO = headO;
        player.yHeadRot = head;
    }

    private void stats(GuiGraphics g, StatSheet s, int x0, int y0) {
        var player = minecraft.player;
        // Right column: level, attributes, vitals, attack.
        int c = x0 + 272, y = y0 + 17;
        g.fill(c - 7, y0 + 20, c - 6, y0 + 138, 0x605D5A55);
        label(g, "Level " + s.level, c, y, PARCH);
        int i = 0;
        for (Attribute a : Attribute.values()) {
            int v = s.attr(a);
            label(g, a.abbreviation, c + (i % 2) * 56, y + 11 + (i / 2) * 9, MUTED);
            String vs = Integer.toString(v);
            label(g, vs, c + (i % 2) * 56 + 40 - font.width(vs), y + 11 + (i / 2) * 9, v > 10 ? EMBER : PARCH);
            i++;
        }
        int vy = y + 42;
        row(g, c, vy, "Health", String.format(Locale.ROOT, "%.0f", player.getMaxHealth()), 0xFFD0564A);
        row(g, c, vy + 9, "Stamina", String.format(Locale.ROOT, "%.0f", s.maxStamina), 0xFF63B36C);
        row(g, c, vy + 18, "Poise", String.format(Locale.ROOT, "%.0f", s.poise), PARCH);
        row(g, c, vy + 27, "Flask", String.format(Locale.ROOT, "%.0f%%", 100 * (dev.vestige.player.FlaskRules.healFraction(
                player.getData(PlayerModule.FLASK).kindling()) + s.flaskHealBonus)), EMBER);
        int ay = vy + 40;
        var inv = player.getData(EquipmentModule.RPG_INVENTORY);
        ItemStack main = dev.vestige.equipment.Stance.main(player, inv);
        var w = WeaponProfiles.of(main, true);
        label(g, "Attack", c, ay, PARCH_DIM);
        if (w.isPresent()) {
            DamageSpread ar = PlayerAttack.spread(player, w.get(), null);
            int r = 0;
            for (DamageType t : DamageType.all()) {
                if (ar.get(t) > 0 && r < 3) {
                    row(g, c + 6, ay + 9 + r * 9, EquipmentTooltips.name(t), String.format(Locale.ROOT, "%.0f", ar.get(t)), t.color);
                    r++;
                }
            }
        } else {
            label(g, "unarmed", c + 6, ay + 9, MUTED);
        }
        // Bottom left: absorption per type.
        int bx = x0 + 8, by = y0 + 145;
        label(g, "Absorption", bx, by, PARCH_DIM);
        int r = 0;
        for (DamageType t : DamageType.all()) {
            if (t == DamageType.STRIKE || t == DamageType.PIERCE) {
                continue; // physical is one number
            }
            String n = t == DamageType.SLASH ? "Phys" : EquipmentTooltips.name(t);
            n = n.length() > 4 ? n.substring(0, 4) : n;
            int cx = bx + (r % 2) * 48, cy = by + 11 + (r / 2) * 9;
            label(g, n, cx, cy, t == DamageType.SLASH ? PARCH : t.color);
            String v = Math.round(s.absorption.get(t) * 100) + "%";
            label(g, v, cx + 44 - font.width(v), cy, PARCH);
            r++;
        }
        label(g, "Status res.", bx, by + 51, PARCH_DIM);
        String sr = Math.round(s.statusResist * 100) + "%";
        label(g, sr, bx + 92 - font.width(sr), by + 51, PARCH);
        label(g, "Defense", bx, by + 60, PARCH_DIM);
        String df = String.format(Locale.ROOT, "%.1f", s.defense.get(DamageType.SLASH));
        label(g, df, bx + 92 - font.width(df), by + 60, PARCH);
        // Bottom right: set bonuses (wrapped).
        int sx = x0 + 280, sy = y0 + 145;
        label(g, "Set bonuses", sx, sy, PARCH_DIM);
        int ly = sy + 11;
        if (s.sets.isEmpty()) {
            label(g, "none", sx, ly, MUTED);
        }
        for (var e : s.sets.entrySet()) {
            var set = EquipmentData.set(e.getKey(), true);
            if (set.isEmpty() || ly > y0 + 212) {
                continue;
            }
            int n = e.getValue();
            label(g, set.get().name() + " " + n + "/4", sx, ly, n >= 2 ? EMBER : PARCH);
            ly += 9;
            String text = n >= 4 ? set.get().fourText() : n >= 2 ? set.get().twoText() : "2 pieces: " + set.get().twoText();
            for (FormattedCharSequence line : font.split(Component.literal(text), 96)) {
                if (ly > y0 + 212) {
                    break;
                }
                g.drawString(font, line, sx, ly, n >= 2 ? PARCH : MUTED, false);
                ly += 9;
            }
        }
    }

    private void row(GuiGraphics g, int x, int y, String name, String value, int color) {
        label(g, name, x, y, PARCH_DIM);
        label(g, value, x + 96 - font.width(value), y, color);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Drawn in renderBg.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
        // Locked slots and the cross tiles explain themselves.
        for (Slot slot : menu.slots) {
            if (slot instanceof EquipmentMenu.RpgSlot rs && !EquipmentRules.unlocked(minecraft.player, rs.equipSlot)
                    && isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                g.renderTooltip(font, List.of(Component.literal("Locked").withColor(BAD & 0xFFFFFF).getVisualOrderText(),
                        Component.literal(rs.equipSlot.unlockHint()).withColor(PARCH & 0xFFFFFF).getVisualOrderText()), mouseX, mouseY);
            }
        }
        int cx = leftPos + DOLL_X - 13;
        if (inside(mouseX, mouseY, cx, topPos + CROSS_BOTTOM_Y, 26)) {
            var flask = minecraft.player.getData(PlayerModule.FLASK);
            List<FormattedCharSequence> lines = List.of(Component.literal("Wick Flask").withColor(EMBER & 0xFFFFFF).getVisualOrderText(),
                    Component.literal(flask.charges() + " of " + flask.max() + " charges · Kindling +" + flask.kindling()).getVisualOrderText(),
                    Component.literal("Refills when you rest at a Waylight").withColor(MUTED & 0xFFFFFF).getVisualOrderText());
            g.renderTooltip(font, lines, mouseX, mouseY);
        } else if (inside(mouseX, mouseY, cx, topPos + CROSS_TOP_Y, 26)) {
            var relic = minecraft.player.getData(EquipmentModule.RPG_INVENTORY).activeRelicStack();
            if (!relic.isEmpty()) {
                g.renderTooltip(font, relic.getHoverName(), mouseX, mouseY);
            } else {
                g.renderTooltip(font, List.of(Component.literal("Active Relic").getVisualOrderText(),
                        Component.literal("Equip relics on the right; click to cycle (Y)").withColor(MUTED & 0xFFFFFF).getVisualOrderText()),
                        mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int cx = leftPos + DOLL_X - 13;
        if (inside((int) mx, (int) my, cx, topPos + CROSS_TOP_Y, 26)) {
            PacketDistributor.sendToServer(new EquipmentNetwork.Act(EquipmentNetwork.Act.CYCLE_RELIC));
            return true;
        }
        if (mx > leftPos + DOLL_X - 26 && mx < leftPos + DOLL_X + 26 && my > topPos + 44 && my < topPos + DOLL_Y + 6) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            dollTargetYaw -= (float) dx * 2.5f;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (EquipmentClient.EQUIPMENT.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    static boolean overburdened(StatSheet s) {
        return s.burden == Burden.OVERBURDENED;
    }
}
