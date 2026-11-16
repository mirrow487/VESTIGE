package dev.vestige.client.rpg;

import dev.vestige.waylight.WaylightPayloads;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Waylight menu (GDD §24): Rest, or Travel to another lit Waylight. Slate panel, brass trim,
 * parchment text; refusals (combat, hostiles near) are shown instead of greyed silently.
 */
public class WaylightScreen extends Screen {
    private static final int SLATE = 0xE01A1B20, SLATE_HI = 0xE0262830, BRASS = 0xFF8A6D3B, PARCH = 0xFFE9DFC6, EMBER = 0xFFE08A3C;
    private static final int W = 240, ROW = 22;
    private final WaylightPayloads.Open data;
    private boolean travelling;
    /** For the driver. */
    public static volatile WaylightScreen current;

    public WaylightScreen(WaylightPayloads.Open data) {
        super(Component.literal(data.name()));
        this.data = data;
    }

    public WaylightPayloads.Open data() {
        return data;
    }

    @Override
    protected void init() {
        current = this;
        clearWidgets();
        int x = (width - W) / 2 + 20, y = height / 2 - 30;
        if (!travelling) {
            Button rest = Button.builder(Component.translatable("gui.vestige.waylight.rest"), b -> {
                PacketDistributor.sendToServer(new WaylightPayloads.Act(data.pos(), false, net.minecraft.core.GlobalPos.of(
                        minecraft.level.dimension(), data.pos())));
                onClose();
            }).bounds(x, y, W - 40, 20).build();
            rest.active = data.restRefusal().equals("NONE");
            addRenderableWidget(rest);
            Button travel = Button.builder(Component.translatable("gui.vestige.waylight.travel"), b -> {
                travelling = true;
                init();
            }).bounds(x, y + 26, W - 40, 20).build();
            travel.active = data.travelRefusal().equals("NONE") && !data.destinations().isEmpty();
            addRenderableWidget(travel);
            addRenderableWidget(Button.builder(Component.translatable("gui.vestige.waylight.leave"), b -> onClose())
                    .bounds(x, y + 52, W - 40, 20).build());
        } else {
            List<WaylightPayloads.Destination> list = data.destinations();
            int top = height / 2 - Math.min(list.size(), 6) * ROW / 2;
            for (int i = 0; i < Math.min(list.size(), 6); i++) {
                WaylightPayloads.Destination d = list.get(i);
                String label = d.name() + (d.distance() >= 0 ? "  (" + d.distance() + "m)" : "  [" + shortDim(d.dimension()) + "]");
                addRenderableWidget(Button.builder(Component.literal(label), b -> {
                    PacketDistributor.sendToServer(new WaylightPayloads.Act(data.pos(), true, d.pos()));
                    onClose();
                }).bounds(x, top + i * ROW, W - 40, 20).build());
            }
            addRenderableWidget(Button.builder(Component.translatable("gui.vestige.waylight.back"), b -> {
                travelling = false;
                init();
            }).bounds(x, top + Math.min(list.size(), 6) * ROW + 6, W - 40, 20).build());
        }
    }

    /** Driver hook: press the n-th destination. */
    public void travelTo(int index) {
        WaylightPayloads.Destination d = data.destinations().get(index);
        PacketDistributor.sendToServer(new WaylightPayloads.Act(data.pos(), true, d.pos()));
        onClose();
    }

    public void showTravel() {
        travelling = true;
        init();
    }

    private static String shortDim(String dim) {
        return dim.substring(dim.indexOf(':') + 1).replace('_', ' ');
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.renderBackground(g, mouseX, mouseY, partial);
        int x0 = (width - W) / 2, x1 = x0 + W;
        int y0 = height / 2 - 80, y1 = height / 2 + 80;
        g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, BRASS);
        g.fillGradient(x0, y0, x1, y1, SLATE_HI, SLATE);
        g.fill(x0 + 6, y0 + 30, x1 - 6, y0 + 31, BRASS);
        // a small lantern glyph beside the title
        int lx = x0 + 12, ly = y0 + 9;
        g.fill(lx, ly, lx + 8, ly + 12, BRASS);
        g.fill(lx + 2, ly + 2, lx + 6, ly + 10, EMBER);
        g.fill(lx + 3, ly + 4, lx + 5, ly + 8, 0xFFFFE2A8);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        int y0 = height / 2 - 80;
        g.drawString(font, data.name(), (width - W) / 2 + 26, y0 + 11, PARCH, true);
        String hint = travelling ? Component.translatable("gui.vestige.waylight.choose").getString() : refusalText();
        if (!hint.isEmpty()) {
            g.drawCenteredString(font, hint, width / 2, height / 2 + 64, travelling ? PARCH : 0xFFE07A5F);
        }
    }

    private String refusalText() {
        String r = !data.restRefusal().equals("NONE") ? data.restRefusal() : data.travelRefusal();
        if (r.equals("NONE")) {
            return data.destinations().isEmpty() ? Component.translatable("gui.vestige.waylight.no_destinations").getString() : "";
        }
        return Component.translatable("message.vestige.waylight_refused." + r.toLowerCase(Locale.ROOT)).getString();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        current = null;
    }
}
