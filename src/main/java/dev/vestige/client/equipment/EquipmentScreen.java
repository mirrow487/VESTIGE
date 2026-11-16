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
    private static final int DOLL_X = 152, DOLL_Y = 104, CROSS_TOP_Y = 16, CROSS_BOTTOM_Y = 112;
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
        int a = (int) (0x8C * fade());
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
        // Header: title and the burden bar.
        g.fill(x0 + 10, y0 - 12, x0 + 13, y0 - 9, EMBER);
        g.fill(x0 + 11, y0 - 13, x0 + 12, y0 - 8, EMBER);
        label(g, "EQUIPMENT", x0 + 17, y0 - 14, PARCH);
        burden(g, s, x0 + imageWidth - 118, y0 - 14);
        // Section labels.
        label(g, "ARMOR", x0 + 8, y0 + 4, PARCH_DIM);
        label(g, "READINESS", x0 + 126, y0 + 4, PARCH_DIM);
        label(g, "RINGS", x0 + 224, y0 + 14, PARCH_DIM);
        label(g, "TALISMANS", x0 + 224, y0 + 52, PARCH_DIM);
        label(g, "RELICS", x0 + 214, y0 + 90, PARCH_DIM);
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
        String charges = flask.charges() + "/" + flask.max();
        g.drawString(font, charges, cx + 25 - font.width(charges), y0 + CROSS_BOTTOM_Y + 17, flask.charges() > 0 ? PARCH : BAD, true);
        if (flask.kindling() > 0) {
            g.drawString(font, "+" + flask.kindling(), cx + 2, y0 + CROSS_BOTTOM_Y + 2, EMBER, true);
        }
        doll(g, mouseX, mouseY, partial);
        stats(g, s, x0, y0 + 142);
    }

    private static boolean inside(int mx, int my, int x, int y, int size) {
        return mx >= x && mx < x + size && my >= y && my < y + size;
    }

    private void burden(GuiGraphics g, StatSheet s, int x, int y) {
        float ratio = s.equipCapacity <= 0 ? 0 : s.equipLoad / s.equipCapacity;
        int w = 64;
        int color = switch (s.burden) {
            case LIGHT -> 0xFF7FBF6A;
            case MEDIUM -> 0xFFE8B84A;
            case HEAVY -> 0xFFE08A3C;
            case OVERBURDENED -> 0xFFD86A5A;
        };
        label(g, "Burden", x, y, PARCH_DIM);
        int bx = x + 36;
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

    private void stats(GuiGraphics g, StatSheet s, int x0, int y) {
        var player = minecraft.player;
        g.fill(x0 + 6, y - 4, x0 + imageWidth - 6, y - 3, 0x605D5A55);
        // Column 1: level, attributes.
        label(g, "Level " + s.level, x0 + 8, y, PARCH);
        int ay = y + 10;
        int i = 0;
        for (Attribute a : Attribute.values()) {
            label(g, a.abbreviation + " " + s.attr(a), x0 + 8 + (i % 2) * 34, ay + (i / 2) * 9, PARCH_DIM);
            i++;
        }
        // Column 2: vitals.
        int c2 = x0 + 82;
        label(g, String.format(Locale.ROOT, "HP %.0f", player.getMaxHealth()), c2, y, 0xFFC0453A);
        label(g, String.format(Locale.ROOT, "STA %.0f", s.maxStamina), c2, y + 9, 0xFF63B36C);
        label(g, String.format(Locale.ROOT, "Poise %.0f", s.poise), c2, y + 18, PARCH_DIM);
        label(g, String.format(Locale.ROOT, "Load %.1f/%.0f", s.equipLoad, s.equipCapacity), c2, y + 27, PARCH_DIM);
        // Column 3: attack per type of the main weapon.
        int c3 = x0 + 150;
        var inv = player.getData(EquipmentModule.RPG_INVENTORY);
        ItemStack main = inv.get(EquipSlot.MAIN).isEmpty() ? player.getMainHandItem() : inv.get(EquipSlot.MAIN);
        label(g, "Attack", c3, y, PARCH_DIM);
        var w = WeaponProfiles.of(main, true);
        if (w.isPresent()) {
            DamageSpread ar = PlayerAttack.spread(player, w.get(), null);
            int row = 0;
            for (DamageType t : DamageType.all()) {
                if (ar.get(t) > 0 && row < 3) {
                    label(g, String.format(Locale.ROOT, "%s %.0f", EquipmentTooltips.name(t), ar.get(t)), c3, y + 9 + row * 9, t.color);
                    row++;
                }
            }
        } else {
            label(g, "-", c3, y + 9, MUTED);
        }
        // Column 4: absorption per type.
        int c4 = x0 + 214;
        label(g, "Absorption", c4, y, PARCH_DIM);
        int row = 0;
        for (DamageType t : DamageType.all()) {
            if (t == DamageType.STRIKE || t == DamageType.PIERCE) {
                continue; // physical shown once, as Slash
            }
            String n = t == DamageType.SLASH ? "Phy" : EquipmentTooltips.name(t).substring(0, 3);
            label(g, String.format(Locale.ROOT, "%s %d%%", n, Math.round(s.absorption.get(t) * 100)), c4 + (row % 2) * 44, y + 9 + (row / 2) * 9, t.color);
            row++;
        }
        // Set bonus line.
        List<String> sets = new ArrayList<>();
        s.sets.forEach((id, n) -> EquipmentData.set(id, true).ifPresent(set -> sets.add(set.name() + " (" + n + "/4)" + (n >= 4 ? ": " + set.fourText()
                : n >= 2 ? ": " + set.twoText() : ""))));
        if (!sets.isEmpty()) {
            label(g, "SET  " + sets.get(0), x0 + 8, y + 40 - 2, EMBER);
        }
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
