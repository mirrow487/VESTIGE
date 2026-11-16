package dev.vestige.vitals;

import dev.vestige.VestigeIds;
import dev.vestige.core.VestigeModule;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.function.Supplier;

/** Health scale and stamina (GDD §8). */
public final class VitalsModule implements VestigeModule {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, VestigeIds.MOD_ID);

    public static final Supplier<AttachmentType<Stamina>> STAMINA = ATTACHMENTS.register("stamina",
            () -> AttachmentType.builder(() -> new Stamina())
                    .serialize(Stamina.CODEC)
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, Stamina.STREAM_CODEC)
                    .build());

    /** Set by the combat module so stamina does not regenerate mid-action. */
    private static volatile Predicate<Player> busyCheck = p -> false;

    @Override
    public String id() {
        return "vitals";
    }

    @Override
    public void init(IEventBus modBus, IEventBus gameBus) {
        ATTACHMENTS.register(modBus);
        gameBus.addListener(VitalsModule::onJoin);
        // Scale vanilla damage before other modules (i-frames, guard) look at the amount.
        gameBus.addListener(EventPriority.HIGHEST, VitalsModule::onIncomingDamage);
        gameBus.addListener(VitalsModule::onHeal);
    }

    public static void setBusyCheck(Predicate<Player> check) {
        busyCheck = check;
    }

    /** Server tick for one player; driven by the combat module's player tick. */
    public static void tickPlayer(ServerPlayer player) {
        StaminaService.tick(player, busyCheck.test(player));
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity living) {
            HealthScaling.apply(living, !event.loadedFromDisk());
        }
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!HealthScaling.applies(event.getEntity()) && !(event.getEntity() instanceof HealthScaling.NativeHealth)) {
            return;
        }
        if (!event.getSource().is(HealthScaling.NATIVE_SCALE)) {
            event.setAmount(HealthScaling.scaleAmount(event.getAmount()));
        }
    }

    private static void onHeal(LivingHealEvent event) {
        if (HealthScaling.applies(event.getEntity())) {
            event.setAmount(HealthScaling.scaleAmount(event.getAmount()));
        }
    }
}
