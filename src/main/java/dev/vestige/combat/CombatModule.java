package dev.vestige.combat;

import dev.vestige.VestigeIds;
import dev.vestige.combat.net.CombatNetwork;
import dev.vestige.combat.player.PlayerCombat;
import dev.vestige.combat.player.PlayerCombatState;
import dev.vestige.core.VestigeModule;
import dev.vestige.vitals.VitalsModule;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Elden Ring-inspired combat runtime (GDD §7): actions, hits, dodges, damage. */
public final class CombatModule implements VestigeModule {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, VestigeIds.MOD_ID);

    /** Transient (unsaved) combat state per player. */
    public static final Supplier<AttachmentType<PlayerCombatState>> PLAYER_STATE =
            ATTACHMENTS.register("player_combat", () -> AttachmentType.builder(() -> new PlayerCombatState()).build());

    /** Resonance (GDD §8.3), saved and synced to its owner. */
    public static final Supplier<AttachmentType<dev.vestige.combat.resonance.Resonance>> RESONANCE = ATTACHMENTS.register("resonance",
            () -> AttachmentType.builder(() -> new dev.vestige.combat.resonance.Resonance())
                    .serialize(dev.vestige.combat.resonance.Resonance.CODEC)
                    .sync((holder, to) -> holder == to, dev.vestige.combat.resonance.Resonance.STREAM_CODEC)
                    .build());

    /** Status buildups and running statuses (GDD §7.10); saved, synced to the owning player. */
    public static final Supplier<AttachmentType<dev.vestige.combat.status.StatusState>> STATUS = ATTACHMENTS.register("status",
            () -> AttachmentType.builder(() -> new dev.vestige.combat.status.StatusState())
                    .serialize(dev.vestige.combat.status.StatusState.CODEC)
                    .sync((holder, to) -> holder == to, dev.vestige.combat.status.StatusState.STREAM_CODEC)
                    .build());

    private static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.PARTICLE_TYPE, VestigeIds.MOD_ID);

    /** Telegraph glints (GDD §7.12): white = parriable, red = roll it. */
    public static final Supplier<net.minecraft.core.particles.SimpleParticleType> GLINT_WHITE =
            PARTICLES.register("glint_white", () -> new net.minecraft.core.particles.SimpleParticleType(true));
    public static final Supplier<net.minecraft.core.particles.SimpleParticleType> GLINT_RED =
            PARTICLES.register("glint_red", () -> new net.minecraft.core.particles.SimpleParticleType(true));

    private static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, VestigeIds.MOD_ID);

    /** Arrow loosed by a VESTIGE bow action (damage through the combat pipeline). */
    public static final Supplier<net.minecraft.world.entity.EntityType<dev.vestige.combat.projectile.CombatArrow>> COMBAT_ARROW =
            ENTITY_TYPES.register("combat_arrow", () -> net.minecraft.world.entity.EntityType.Builder
                    .<dev.vestige.combat.projectile.CombatArrow>of(dev.vestige.combat.projectile.CombatArrow::new,
                            net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.5f, 0.5f).eyeHeight(0.13f).clientTrackingRange(4).updateInterval(20).build("combat_arrow"));

    /** Censer incense cloud (Smolder Wake). */
    public static final Supplier<net.minecraft.world.entity.EntityType<dev.vestige.combat.projectile.IncenseCloud>> INCENSE_CLOUD =
            ENTITY_TYPES.register("incense_cloud", () -> net.minecraft.world.entity.EntityType.Builder
                    .<dev.vestige.combat.projectile.IncenseCloud>of(dev.vestige.combat.projectile.IncenseCloud::new,
                            net.minecraft.world.entity.MobCategory.MISC)
                    .sized(1f, 0.5f).clientTrackingRange(6).updateInterval(10).fireImmune().build("incense_cloud"));

    @Override
    public String id() {
        return "combat";
    }

    @Override
    public void init(IEventBus modBus, IEventBus gameBus) {
        ATTACHMENTS.register(modBus);
        PARTICLES.register(modBus);
        ENTITY_TYPES.register(modBus);
        CombatData.MOVESETS.register(modBus, gameBus);
        modBus.addListener(CombatNetwork::register);
        gameBus.addListener(CombatModule::onPlayerTick);
        gameBus.addListener(CombatModule::onIncomingDamage);
        gameBus.addListener((net.neoforged.neoforge.event.tick.EntityTickEvent.Post e) -> {
            if (!e.getEntity().level().isClientSide && e.getEntity() instanceof net.minecraft.world.entity.LivingEntity living) {
                dev.vestige.combat.status.StatusService.tick(living);
            }
        });
        // Stamina: no regeneration mid-action, half speed while guarding (GDD §8.2).
        VitalsModule.setRegenFactor(p -> {
            var current = PlayerCombat.state(p).machine().current();
            return current == null ? 1.0 : current.guarding() ? 0.5 : 0.0;
        });
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerCombat.serverTick(player);
        }
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && PlayerCombat.dodges(player, event.getSource())) {
            event.setCanceled(true);
        }
    }
}
