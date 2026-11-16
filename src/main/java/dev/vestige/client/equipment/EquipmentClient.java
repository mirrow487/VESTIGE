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
