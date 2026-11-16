package dev.vestige.client.equipment;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vestige.VestigeIds;
import dev.vestige.equipment.EquipmentArmor;
import dev.vestige.equipment.EquipmentMenu;
import dev.vestige.equipment.EquipmentNetwork;
import dev.vestige.equipment.VestigeArmorItem;
import dev.vestige.stats.Perks;
import dev.vestige.stats.Stats;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/** Client side of equipment (GDD §9): keys, armor renderers, the equipment screen and movement relics. */
public final class EquipmentClient {
    public static final String CATEGORY = "key.categories.vestige";
    public static final KeyMapping EQUIPMENT = new KeyMapping("key.vestige.equipment", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I, CATEGORY);
    public static final KeyMapping STANCE = new KeyMapping("key.vestige.stance", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V, CATEGORY);
    public static final KeyMapping RELIC_USE = new KeyMapping("key.vestige.relic_use", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping RELIC_CYCLE = new KeyMapping("key.vestige.relic_cycle", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y, CATEGORY);

    private EquipmentClient() {}

    public static void init(IEventBus modBus, IEventBus gameBus) {
        EquipmentArmor.rendererFactory = EquipmentClient::armorProvider;
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.register(EQUIPMENT);
            e.register(STANCE);
            e.register(RELIC_USE);
            e.register(RELIC_CYCLE);
        });
        modBus.addListener((RegisterMenuScreensEvent e) -> e.register(EquipmentMenu.TYPE.get(), EquipmentScreen::new));
        gameBus.addListener(EquipmentClient::onTick);
        gameBus.addListener(EquipmentTooltips::onTooltip);
        gameBus.addListener(EquipmentClient::onRenderArm);
        // The equipment and attunement screens are full-screen: no HUD shows through them.
        gameBus.addListener((net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre e) -> {
            var screen = Minecraft.getInstance().screen;
            if (screen instanceof EquipmentScreen || screen instanceof AttuneScreen) {
                e.setCanceled(true);
            }
        });
    }

    private static GeoRenderProvider armorProvider(String set) {
        return new GeoRenderProvider() {
            private GeoArmorRenderer<VestigeArmorItem> renderer;

            @Override
            public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(T entity, ItemStack stack, EquipmentSlot slot,
                                                                                 HumanoidModel<T> original) {
                if (renderer == null) {
                    renderer = new GeoArmorRenderer<>(new DefaultedItemGeoModel<>(VestigeIds.id("armor/" + set)));
                }
                return renderer;
            }
        };
    }

    /** A set's armor renderer that, for the first-person arm, shows only that arm's bone. */
    static final class ArmRenderer extends GeoArmorRenderer<VestigeArmorItem> {
        net.minecraft.world.entity.HumanoidArm arm;

        ArmRenderer(String set) {
            super(new DefaultedItemGeoModel<>(VestigeIds.id("armor/" + set)));
        }

        @Override
        protected void applyBoneVisibilityBySlot(EquipmentSlot slot) {
            setAllVisible(false);
            if (arm == net.minecraft.world.entity.HumanoidArm.RIGHT) {
                setBoneVisible(rightArm, true);
            } else {
                setBoneVisible(leftArm, true);
            }
        }
    }

    private static final java.util.Map<String, ArmRenderer> ARM_RENDERERS = new java.util.HashMap<>();
    /** For the driver: first-person armor sleeves drawn. */
    public static volatile int sleevesDrawn;

    /**
     * Vanilla draws only skin and sleeve on the first-person arm; VESTIGE chest armor also covers it,
     * so the arm in first person matches the third-person model (GDD §9.1).
     */
    private static void onRenderArm(net.neoforged.neoforge.client.event.RenderArmEvent event) {
        var player = event.getPlayer();
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof VestigeArmorItem armor)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.getEntityRenderDispatcher().getRenderer(player) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr)) {
            return;
        }
        var model = pr.getModel();
        model.attackTime = 0f;
        model.crouching = false;
        model.swimAmount = 0f;
        model.setupAnim(player, 0f, 0f, 0f, 0f, 0f);
        (event.getArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? model.rightArm : model.leftArm).xRot = 0f;
        ArmRenderer r = ARM_RENDERERS.computeIfAbsent(armor.set(), ArmRenderer::new);
        r.arm = event.getArm();
        r.prepForRender(player, chest, EquipmentSlot.CHEST, model, event.getMultiBufferSource(),
                mc.getTimer().getGameTimeDeltaPartialTick(false), 0f, 0f, 0f, 0f);
        r.renderToBuffer(event.getPoseStack(), null, event.getPackedLight(), net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF);
        sleevesDrawn++;
    }

    private static void send(int action) {
        PacketDistributor.sendToServer(new EquipmentNetwork.Act(action));
    }

    private static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.screen != null) {
            return;
        }
        while (EQUIPMENT.consumeClick()) {
            send(EquipmentNetwork.Act.OPEN_EQUIPMENT);
        }
        while (STANCE.consumeClick()) {
            send(EquipmentNetwork.Act.STANCE);
        }
        while (RELIC_USE.consumeClick()) {
            send(EquipmentNetwork.Act.USE_RELIC);
        }
        while (RELIC_CYCLE.consumeClick()) {
            send(EquipmentNetwork.Act.CYCLE_RELIC);
        }
        var sheet = Stats.of(player);
        boolean jump = mc.options.keyJump.isDown();
        Vec3 v = player.getDeltaMovement();
        // Windsail Cape: holding jump while falling turns the fall into a glide.
        if (sheet.has(Perks.WINDSAIL_CAPE) && jump && !player.onGround() && !player.isInWater() && v.y < -0.08 && !player.getAbilities().flying) {
            Vec3 look = Vec3.directionFromRotation(0f, player.getYRot());
            player.setDeltaMovement(v.x * 0.9 + look.x * 0.045, -0.08, v.z * 0.9 + look.z * 0.045);
            if (player.tickCount % 3 == 0) {
                player.level().addParticle(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.6, player.getZ(), 0, 0, 0);
            }
            gliding = true;
        } else {
            gliding = false;
        }
        // Climber's Hooks: jump into a wall to climb it; sneak to cling.
        if (sheet.has(Perks.CLIMBERS_HOOKS) && player.horizontalCollision && !player.onGround()) {
            if (jump) {
                player.setDeltaMovement(v.x, 0.22, v.z);
                climbing = true;
            } else if (player.isShiftKeyDown()) {
                player.setDeltaMovement(v.x, 0, v.z);
                climbing = true;
            }
        } else {
            climbing = false;
        }
    }

    /** For the driver. */
    public static volatile boolean gliding;
    public static volatile boolean climbing;
}
