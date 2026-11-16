package dev.vestige.client.equipment;

import dev.vestige.combat.player.PlayerAttack;
import dev.vestige.equipment.Attunement;
import dev.vestige.equipment.EquipSlot;
import dev.vestige.equipment.EquipmentModule;
import dev.vestige.equipment.EquipmentNetwork;
import dev.vestige.player.PlayerModule;
import dev.vestige.stats.Attribute;
import dev.vestige.stats.AttributeRules;
import dev.vestige.stats.StatSheet;
import dev.vestige.stats.Stats;
import dev.vestige.weapon.WeaponProfiles;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Attunement at a Waylight (GDD §8.6): spend Glim to raise attributes. Points are planned with
 * +/- first; every derived number (health, stamina, load, poise, attack, flask, resistances) is
 * projected live, the total cost is checked against the purse, and Confirm pays it all at once.
 */
public class AttuneScreen extends Screen {
    private static final int W = 364, H = 214;
    private static final String[] DESCRIPTIONS = {
        "Maximum health",
        "Stamina, load, poise",
        "Heavy and Strike weapons",
        "Quick, Slash, Pierce, bows",
        "Resonance, Hush, Void",
        "Cinder, Radiance, healing"};
    private final BlockPos waylight;
    private final Screen parent;
    private final EnumMap<Attribute, Integer> planned = new EnumMap<>(Attribute.class);
    private Button confirm;
    /** For the driver. */
    public static volatile AttuneScreen current;

    public AttuneScreen(BlockPos waylight, Screen parent) {
        super(Component.literal("Attunement"));
        this.waylight = waylight;
        this.parent = parent;
        for (Attribute a : Attribute.values()) {
            planned.put(a, 0);
        }
    }

    private int x0() {
        return (width - W) / 2;
    }

    private int y0() {
        return (height - H) / 2;
    }

    private Map<Attribute, Integer> base() {
        return minecraft.player.getData(EquipmentModule.ATTRIBUTES).asMap();
    }

    private int points() {
        return planned.values().stream().mapToInt(Integer::intValue).sum();
    }

    private long cost() {
        return Attunement.cost(minecraft.player.getData(EquipmentModule.ATTRIBUTES).level(), points());
    }

    private long glim() {
        return minecraft.player.getData(PlayerModule.GLIM).amount();
    }

    private boolean affordable(int extraPoints) {
        return minecraft.player.getAbilities().instabuild
                || Attunement.cost(minecraft.player.getData(EquipmentModule.ATTRIBUTES).level(), points() + extraPoints) <= glim();
    }

    @Override
    protected void init() {
        current = this;
        clearWidgets();
        int x0 = x0(), y0 = y0();
        Attribute[] attrs = Attribute.values();
        for (int i = 0; i < attrs.length; i++) {
            Attribute a = attrs[i];
            int ry = y0 + 40 + i * 22;
            addRenderableWidget(Button.builder(Component.literal("-"), b -> plan(a, -1)).bounds(x0 + 150, ry, 16, 16).build());
            addRenderableWidget(Button.builder(Component.literal("+"), b -> plan(a, 1)).bounds(x0 + 190, ry, 16, 16).build());
        }
        confirm = addRenderableWidget(Button.builder(Component.literal("Attune"), b -> confirm()).bounds(x0 + W - 172, y0 + H - 28, 76, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(x0 + W - 90, y0 + H - 28, 76, 20).build());
        refresh();
    }

    public void plan(Attribute a, int delta) {
        int now = planned.get(a);
        int next = now + delta;
        if (next < 0 || base().get(a) + next > AttributeRules.MAX || (delta > 0 && !affordable(1))) {
            return;
        }
        planned.put(a, next);
        refresh();
    }

    private void refresh() {
        if (confirm != null) {
            confirm.active = points() > 0 && affordable(0);
        }
    }

    public void confirm() {
        List<Integer> pts = new ArrayList<>();
        for (Attribute a : Attribute.values()) {
            pts.add(planned.get(a));
        }
        PacketDistributor.sendToServer(new EquipmentNetwork.Attune(waylight, pts));
        for (Attribute a : Attribute.values()) {
            planned.put(a, 0);
        }
        refresh();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.renderBackground(g, mouseX, mouseY, partial);
        int x0 = x0(), y0 = y0();
        EquipmentScreen.tile(g, x0, y0, W, H, false, EquipmentScreen.RULE, false);
        g.fill(x0 + 8, y0 + 28, x0 + W - 8, y0 + 29, 0x805D5A55);
        g.fill(x0 + 228, y0 + 34, x0 + 229, y0 + H - 34, 0x605D5A55);
        // A small flame glyph.
        g.fill(x0 + 12, y0 + 10, x0 + 18, y0 + 20, EquipmentScreen.EMBER);
        g.fill(x0 + 14, y0 + 7, x0 + 16, y0 + 12, 0xFFFFE2A8);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        refresh();
        super.render(g, mouseX, mouseY, partial);
        int x0 = x0(), y0 = y0();
        var player = minecraft.player;
        Map<Attribute, Integer> base = base();
        Map<Attribute, Integer> next = new HashMap<>(base);
        planned.forEach((a, n) -> next.merge(a, n, Integer::sum));
        int level = AttributeRules.level(base), newLevel = AttributeRules.level(next);
        g.drawString(font, "ATTUNEMENT", x0 + 24, y0 + 11, EquipmentScreen.PARCH, false);
        String lv = newLevel == level ? "Level " + level : "Level " + level + " → " + newLevel;
        g.drawString(font, lv, x0 + W - 12 - font.width(lv), y0 + 11, newLevel == level ? EquipmentScreen.PARCH_DIM : EquipmentScreen.EMBER, false);
        Attribute[] attrs = Attribute.values();
        for (int i = 0; i < attrs.length; i++) {
            Attribute a = attrs[i];
            int ry = y0 + 40 + i * 22;
            String name = a.name().charAt(0) + a.name().substring(1).toLowerCase(Locale.ROOT);
            g.drawString(font, name, x0 + 12, ry, EquipmentScreen.PARCH, false);
            g.drawString(font, DESCRIPTIONS[i], x0 + 12, ry + 9, EquipmentScreen.MUTED, false);
            String v = Integer.toString(next.get(a));
            g.drawCenteredString(font, v, x0 + 178, ry + 4, planned.get(a) > 0 ? EquipmentScreen.EMBER : EquipmentScreen.PARCH);
            if (planned.get(a) > 0) {
                g.drawString(font, "+" + planned.get(a), x0 + 210, ry + 4, EquipmentScreen.EMBER, false);
            }
        }
        // Projection.
        StatSheet s = Stats.of(player);
        int px = x0 + 234, py = y0 + 46;
        g.drawString(font, "Projected", px, y0 + 34, EquipmentScreen.PARCH_DIM, false);
        int row = 0;
        int vit = next.get(Attribute.VITALITY), end = next.get(Attribute.ENDURANCE), cad = next.get(Attribute.CADENCE), lmp = next.get(Attribute.LAMPLIGHT);
        float hpNow = player.getMaxHealth();
        float hp = hpNow + AttributeRules.vitalityHealth(vit) - AttributeRules.vitalityHealth(base.get(Attribute.VITALITY));
        row = line(g, px, py, row, "Health", hpNow, hp, "%.0f");
        float sta = s.maxStamina + AttributeRules.enduranceStamina(end) - AttributeRules.enduranceStamina(base.get(Attribute.ENDURANCE));
        row = line(g, px, py, row, "Stamina", s.maxStamina, sta, "%.0f");
        float cap = s.equipCapacity + AttributeRules.enduranceCapacity(end) - AttributeRules.enduranceCapacity(base.get(Attribute.ENDURANCE));
        row = line(g, px, py, row, "Equip load", s.equipCapacity, cap, "%.1f");
        float poise = s.poise + AttributeRules.endurancePoise(end) - AttributeRules.endurancePoise(base.get(Attribute.ENDURANCE));
        row = line(g, px, py, row, "Poise", s.poise, poise, "%.0f");
        ItemStack main = player.getData(EquipmentModule.RPG_INVENTORY).get(EquipSlot.MAIN);
        if (main.isEmpty()) {
            main = player.getMainHandItem();
        }
        var w = WeaponProfiles.of(main, true);
        if (w.isPresent()) {
            float now = PlayerAttack.spread(player, w.get(), null).total();
            float then = AttributeRules.attackRating(w.get().baseDamage(), w.get().scaling(), w.get().requirements(), next).total();
            row = line(g, px, py, row, "Attack", now, then, "%.0f");
        }
        row = line(g, px, py, row, "Defense", AttributeRules.levelDefense(level), AttributeRules.levelDefense(newLevel), "%.1f");
        row = line(g, px, py, row, "Flask heal", 100 * AttributeRules.lamplightFlaskBonus(base.get(Attribute.LAMPLIGHT)),
                100 * AttributeRules.lamplightFlaskBonus(lmp), "+%.0f%%");
        row = line(g, px, py, row, "Status res.", 100 * AttributeRules.lamplightStatusResist(base.get(Attribute.LAMPLIGHT)),
                100 * AttributeRules.lamplightStatusResist(lmp), "%.0f%%");
        line(g, px, py, row, "Art damage", 100 * AttributeRules.cadenceArtBonus(base.get(Attribute.CADENCE)),
                100 * AttributeRules.cadenceArtBonus(cad), "+%.0f%%");
        // Cost against the purse.
        long cost = cost(), glim = glim();
        boolean ok = affordable(0);
        String c = String.format(Locale.ROOT, "Cost %,d", cost);
        String have = String.format(Locale.ROOT, "Glim %,d", glim);
        g.drawString(font, c, x0 + 12, y0 + H - 28, points() == 0 ? EquipmentScreen.MUTED : ok ? EquipmentScreen.EMBER : EquipmentScreen.BAD, false);
        g.drawString(font, have, x0 + 12, y0 + H - 18, EquipmentScreen.PARCH_DIM, false);
        String nextCost = String.format(Locale.ROOT, "next point %,d", Attunement.cost(newLevel, 1));
        g.drawString(font, nextCost, x0 + 90, y0 + H - 18, EquipmentScreen.MUTED, false);
    }

    private int line(GuiGraphics g, int x, int y, int row, String name, float now, float then, String fmt) {
        int ry = y + row * 14;
        g.drawString(font, name, x, ry, EquipmentScreen.PARCH_DIM, false);
        String a = String.format(Locale.ROOT, fmt, now);
        boolean changed = Math.abs(then - now) > 0.01f;
        String b = changed ? "→ " + String.format(Locale.ROOT, fmt, then) : "";
        int right = x + 124;
        g.drawString(font, b, right - font.width(b), ry, 0xFF7FBF6A, false);
        g.drawString(font, a, right - font.width(b) - (b.isEmpty() ? 0 : 4) - font.width(a), ry, EquipmentScreen.PARCH, false);
        return row + 1;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        current = null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
