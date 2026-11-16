package dev.vestige.client;

import dev.vestige.VestigeIds;
import dev.vestige.client.combat.CombatClient;
import dev.vestige.client.devdriver.DevDriver;
import dev.vestige.client.entity.TimelineGeoModel;
import dev.vestige.client.entity.TimelineGeoRenderer;
import dev.vestige.client.fx.GlintParticle;
import dev.vestige.combat.CombatModule;
import dev.vestige.entity.EntityModule;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only entry point. Everything under {@code dev.vestige.client} is loaded only on the
 * physical client; common code must never reference it ({@code ArchitectureRulesTest}).
 */
@Mod(value = VestigeIds.MOD_ID, dist = Dist.CLIENT)
public final class VestigeClient {
    public VestigeClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        IEventBus gameBus = NeoForge.EVENT_BUS;
        CombatClient.init(modBus, gameBus);
        dev.vestige.client.rpg.RpgClient.init(modBus, gameBus);
        modBus.addListener(VestigeClient::renderers);
        modBus.addListener(VestigeClient::particles);
        modBus.addListener(VestigeClient::setup);
        gameBus.addListener(WeaponTooltips::onTooltip);
        DevDriver.initIfEnabled(gameBus);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityModule.HOLLOWED.get(), ctx -> new TimelineGeoRenderer(ctx, TimelineGeoModel.HUMANOID, 0.5f, 1.0f));
        event.registerEntityRenderer(EntityModule.HOLLOWED_BRUTE.get(), ctx -> new TimelineGeoRenderer(ctx, TimelineGeoModel.HUMANOID, 0.75f, 1.1f));
        event.registerEntityRenderer(EntityModule.TOLLING_RAM.get(), ctx -> new TimelineGeoRenderer(ctx, TimelineGeoModel.QUADRUPED, 0.95f, 1.35f));
        event.registerEntityRenderer(CombatModule.COMBAT_ARROW.get(), ctx -> new net.minecraft.client.renderer.entity.ArrowRenderer<>(ctx) {
            @Override
            public net.minecraft.resources.ResourceLocation getTextureLocation(dev.vestige.combat.projectile.CombatArrow arrow) {
                return net.minecraft.client.renderer.entity.TippableArrowRenderer.NORMAL_ARROW_LOCATION;
            }
        });
        event.registerEntityRenderer(CombatModule.INCENSE_CLOUD.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
    }

    private static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        // The bow shows its drawn model while a shot is being drawn or held (local prediction or mirror).
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(dev.vestige.weapon.WeaponModule.HUNTING_BOW.get(),
                dev.vestige.VestigeIds.id("draw"), (stack, level, entity, seed) -> drawProgress(entity)));
    }

    public static float drawProgress(net.minecraft.world.entity.LivingEntity entity) {
        if (entity == null) {
            return 0f;
        }
        var mc = net.minecraft.client.Minecraft.getInstance();
        var a = entity == mc.player ? dev.vestige.client.combat.ClientCombat.machine().current()
                : dev.vestige.client.combat.ClientActions.of(entity);
        if (a == null || a.def.effects().projectile().isEmpty() || a.phase() != dev.vestige.combat.action.ActionPhase.WINDUP) {
            return 0f;
        }
        int windup = a.def.timing().windup();
        return a.holding() || windup <= 0 ? 1f : Math.min(1f, a.elapsed() / (float) windup);
    }

    private static void particles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(CombatModule.GLINT_WHITE.get(), GlintParticle.Provider::new);
        event.registerSpriteSet(CombatModule.GLINT_RED.get(), GlintParticle.Provider::new);
    }
}
