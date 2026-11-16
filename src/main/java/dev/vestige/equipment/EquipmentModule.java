package dev.vestige.equipment;

import dev.vestige.VestigeIds;
import dev.vestige.combat.player.PlayerCombat;
import dev.vestige.combat.resonance.ResonanceService;
import dev.vestige.core.VestigeModule;
import dev.vestige.player.GlimService;
import dev.vestige.stats.Perks;
import dev.vestige.stats.StatSheet;
import dev.vestige.stats.Stats;
import dev.vestige.vitals.HealthScaling;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Equipment and character progression (GDD §8.6, §9, §11, §12): attributes, the RPG inventory,
 * armor/accessory/set data, the stat sheet every system reads, combat stance and the equipment
 * effects that belong to no other system.
 */
public final class EquipmentModule implements VestigeModule {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, VestigeIds.MOD_ID);

    public static final Supplier<AttachmentType<CharacterAttributes>> ATTRIBUTES = ATTACHMENTS.register("attributes",
            () -> AttachmentType.builder(() -> new CharacterAttributes()).serialize(CharacterAttributes.CODEC).copyOnDeath()
                    .sync((holder, to) -> holder == to, CharacterAttributes.STREAM_CODEC).build());
    public static final Supplier<AttachmentType<RpgInventory>> RPG_INVENTORY = ATTACHMENTS.register("rpg_inventory",
            () -> AttachmentType.builder(() -> new RpgInventory()).serialize(RpgInventory.CODEC).copyOnDeath()
                    .sync((holder, to) -> holder == to, RpgInventory.STREAM_CODEC).build());

    public static final ResourceLocation VITALITY_ID = VestigeIds.id("vitality");
    public static final ResourceLocation SPURS_ID = VestigeIds.id("bellbronze_spurs");
    public static final ResourceLocation DIVERS_BELL_ID = VestigeIds.id("divers_bell");
    /** Talisman of Second Breath cooldown (10 minutes). */
    public static final int SECOND_BREATH_COOLDOWN = 12_000;
    private static final String TAG_SECOND_BREATH = "vestige_second_breath";
    private static final String TAG_TOLL_HITS = "vestige_toll_hits";

    @Override
    public String id() {
        return "equipment";
    }

    @Override
    public void init(IEventBus modBus, IEventBus gameBus) {
        ATTACHMENTS.register(modBus);
        EquipmentItems.ITEMS.register(modBus);
        EquipmentMenu.MENUS.register(modBus);
        EquipmentItems.init(modBus);
        EquipmentData.ARMOR.register(modBus, gameBus);
        EquipmentData.ACCESSORIES.register(modBus, gameBus);
        EquipmentData.SETS.register(modBus, gameBus);
        EquipmentNetwork.init(modBus);
        Stats.install(EquipmentStats::of);
        PlayerCombat.setLedgerHook(p -> GlimService.set(p, Math.max(0, GlimService.get(p) - Math.max(1, GlimService.get(p) / 100))));
        gameBus.addListener(EquipmentModule::onPlayerTick);
        gameBus.addListener(EventPriority.HIGHEST, EquipmentModule::onDeathFirst);
        gameBus.addListener(EquipmentModule::onKill);
        gameBus.addListener(EquipmentModule::onIncomingDamage);
        gameBus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                Stance.exit(p);
            }
        });
        gameBus.addListener((EntityTravelToDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                Stance.exit(p);
            }
        });
        gameBus.addListener((PlayerContainerEvent.Open e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                Stance.exit(p);
            }
        });
        gameBus.addListener((ItemTossEvent e) -> {
            // The wielded stance weapon cannot be thrown away (it belongs to the Main slot).
            if (e.getPlayer() instanceof ServerPlayer p && Stance.in(p)) {
                RpgInventory inv = p.getData(RPG_INVENTORY);
                if (p.getInventory().getItem(inv.stanceSlot).isEmpty()) {
                    p.getInventory().setItem(inv.stanceSlot, e.getEntity().getItem());
                    e.setCanceled(true);
                }
            }
        });
        gameBus.addListener((ServerStoppingEvent e) -> e.getServer().getPlayerList().getPlayers().forEach(Stance::exit));
    }

    /** Max health from Vitality and gear (added before the health scale, so it is divided by it). */
    public static void applyDerived(ServerPlayer player) {
        StatSheet s = Stats.of(player);
        var max = player.getAttribute(Attributes.MAX_HEALTH);
        if (max != null) {
            double amount = s.healthBonus / HealthScaling.scale();
            var existing = max.getModifier(VITALITY_ID);
            if (existing == null || Math.abs(existing.amount() - amount) > 1e-4) {
                float fraction = player.getHealth() / player.getMaxHealth();
                max.addOrReplacePermanentModifier(new AttributeModifier(VITALITY_ID, amount, AttributeModifier.Operation.ADD_VALUE));
                player.setHealth(Math.min(player.getMaxHealth(), fraction * player.getMaxHealth()));
            }
        }
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            boolean spurs = s.sprintSpeedBonus > 0 && player.isSprinting();
            if (spurs && speed.getModifier(SPURS_ID) == null) {
                speed.addTransientModifier(new AttributeModifier(SPURS_ID, s.sprintSpeedBonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            } else if (!spurs && speed.getModifier(SPURS_ID) != null) {
                speed.removeModifier(SPURS_ID);
            }
        }
        var swim = player.getAttribute(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED);
        if (swim != null) {
            boolean bell = s.has(Perks.DIVERS_BELL);
            if (bell && swim.getModifier(DIVERS_BELL_ID) == null) {
                swim.addTransientModifier(new AttributeModifier(DIVERS_BELL_ID, 0.6, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            } else if (!bell && swim.getModifier(DIVERS_BELL_ID) != null) {
                swim.removeModifier(DIVERS_BELL_ID);
            }
        }
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        applyDerived(player);
        StatSheet s = Stats.of(player);
        if (s.has(Perks.DIVERS_BELL) && player.isInWater()) {
            player.setAirSupply(player.getMaxAirSupply());
        }
        // Talisman of the Grove: out of combat, the body mends three times as fast.
        if (s.has(Perks.GROVE) && !PlayerCombat.inCombat(player) && player.tickCount % 20 == 0
                && player.getHealth() < player.getMaxHealth() && player.getFoodData().getFoodLevel() >= 6) {
            player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 3f));
        }
        if (s.has(Perks.WINDSAIL_CAPE) || s.has(Perks.CLIMBERS_HOOKS) || s.has(Perks.PALE_HOOK)) {
            // Movement relics move the player on the client; the server forgives the falls they prevent.
            if (player.getDeltaMovement().y > -0.5) {
                player.resetFallDistance();
            }
        }
    }

    /** Before anything drops: leave combat stance (so the stash returns), then the Second Breath may cheat death. */
    private static void onDeathFirst(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Stats.of(player);
        if (Stats.of(player).has(Perks.SECOND_BREATH) && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            long now = player.level().getGameTime();
            long last = player.getPersistentData().getLong(TAG_SECOND_BREATH);
            if (last == 0 || now - last >= SECOND_BREATH_COOLDOWN) {
                event.setCanceled(true);
                player.setHealth(player.getMaxHealth() * 0.2f);
                player.getPersistentData().putLong(TAG_SECOND_BREATH, now);
                player.level().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 1.3f);
                return;
            }
        }
        Stance.exit(player);
    }

    /** Tithe Ring: every kill rings a little Resonance into its bearer. */
    private static void onKill(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntity().getKillCredit() instanceof ServerPlayer killer && !(event.getEntity() instanceof Player)
                && Stats.of(killer).has(Perks.TITHE_RING)) {
            ResonanceService.add(killer, 8f);
        }
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getSource().is(DamageTypeTags.IS_FALL)
                && Stats.of(p).has(Perks.WANDERER_SURE_FOOT)) {
            event.setAmount(event.getAmount() * 0.7f);
        }
        // Bellbronze Mail (4): every 4th landed hit rings a small Resonance shockwave around the target.
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && !(event.getEntity() instanceof Player)
                && Stats.of(attacker).has(Perks.BELLBRONZE_TOLL)
                && event.getSource().is(dev.vestige.combat.damage.CombatDamageTypes.WEAPON)) {
            int hits = attacker.getPersistentData().getInt(TAG_TOLL_HITS) + 1;
            attacker.getPersistentData().putInt(TAG_TOLL_HITS, hits % 4);
            if (hits % 4 == 0) {
                toll(attacker, event.getEntity());
            }
        }
    }

    private static void toll(ServerPlayer attacker, net.minecraft.world.entity.LivingEntity center) {
        var level = attacker.serverLevel();
        level.playSound(null, center.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.2f, 1.5f);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT, center.getX(), center.getY() + 0.5, center.getZ(),
                24, 1.2, 0.2, 1.2, 0.1);
        for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, center.getBoundingBox().inflate(2.5),
                e -> e != attacker && e.isAlive() && !(e instanceof Player))) {
            e.hurt(dev.vestige.combat.damage.CombatDamageTypes.status(e, attacker), 12f);
            if (e instanceof dev.vestige.combat.CombatTarget ct) {
                ct.receivePoiseDamage(15f, attacker);
            }
        }
    }
}
