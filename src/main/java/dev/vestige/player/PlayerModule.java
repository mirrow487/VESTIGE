package dev.vestige.player;

import dev.vestige.VestigeIds;
import dev.vestige.combat.machine.RunningAction;
import dev.vestige.combat.player.PlayerCombat;
import dev.vestige.config.ServerConfig;
import dev.vestige.core.VestigeModule;
import dev.vestige.entity.VestigeMob;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The player's RPG state (GDD §8.4, §8.5, §23.1): the Glim purse, the Wick Flask, the Ember-Ghost
 * left on death and the Run Mode's inventory rule.
 */
public final class PlayerModule implements VestigeModule {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, VestigeIds.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, VestigeIds.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VestigeIds.MOD_ID);

    /** Carried Glim: saved, kept across death at 0 (the ghost holds it), synced to its owner. */
    public static final Supplier<AttachmentType<Glim>> GLIM = ATTACHMENTS.register("glim",
            () -> AttachmentType.builder(() -> new Glim()).serialize(Glim.CODEC).copyOnDeath()
                    .sync((holder, to) -> holder == to, Glim.STREAM_CODEC).build());

    /** Wick Flask charges, max and Kindling: saved, kept across death, synced to its owner. */
    public static final Supplier<AttachmentType<Flask>> FLASK = ATTACHMENTS.register("flask",
            () -> AttachmentType.builder(() -> new Flask()).serialize(Flask.CODEC).copyOnDeath()
                    .sync((holder, to) -> holder == to, Flask.STREAM_CODEC).build());

    public static final Supplier<EntityType<EmberGhost>> EMBER_GHOST = ENTITY_TYPES.register("ember_ghost",
            () -> EntityType.Builder.<EmberGhost>of(EmberGhost::new, MobCategory.MISC).sized(0.8f, 1.8f).clientTrackingRange(10)
                    .fireImmune().build(VestigeIds.id("ember_ghost").toString()));

    /** The flask itself (drawn in the hand while drinking; the HUD icon). */
    public static final DeferredItem<Item> WICK_FLASK = ITEMS.registerSimpleItem("wick_flask", new Item.Properties().stacksTo(1));
    public static final DeferredItem<FlaskWickItem> FLASK_WICK = ITEMS.registerItem("flask_wick", FlaskWickItem::new,
            new Item.Properties().stacksTo(16));

    /** Sips already paid for (the charge is taken on the first healing tick). */
    private static final Map<RunningAction, Boolean> SIPS = new WeakHashMap<>();
    /** Last position each player stood safely (an Ember-Ghost never waits in the void or in lava). */
    private static final Map<UUID, Vec3> SAFE = new HashMap<>();
    /** Wayfarer: the inventory between death and respawn. */
    private static final Map<UUID, ListTag> KEPT = new HashMap<>();

    @Override
    public String id() {
        return "player";
    }

    @Override
    public void init(IEventBus modBus, IEventBus gameBus) {
        ATTACHMENTS.register(modBus);
        ENTITY_TYPES.register(modBus);
        ITEMS.register(modBus);
        PlayerCombat.setFlaskCheck(FlaskService::hasCharge);
        gameBus.addListener(PlayerModule::onPlayerTick);
        gameBus.addListener(PlayerModule::onDeath);
        gameBus.addListener(PlayerModule::onDrops);
        gameBus.addListener(PlayerModule::onClone);
        gameBus.addListener(PlayerModule::onRespawn);
    }

    // ------------------------------------------------------------------ the Wick Flask

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        tickFlask(player);
        if (player.onGround() && !player.isInLava() && player.getY() > player.level().getMinBuildHeight()) {
            SAFE.put(player.getUUID(), player.position());
        }
    }

    /** Heals over the drink's active frames; a hit that interrupts the drink stops the healing. */
    public static void tickFlask(ServerPlayer player) {
        RunningAction a = PlayerCombat.state(player).machine().current();
        if (a == null || !a.ref.action().equals("flask_drink") || a.phase() != dev.vestige.combat.action.ActionPhase.ACTIVE) {
            return;
        }
        Boolean paid = SIPS.get(a);
        if (paid == null) {
            paid = FlaskService.consume(player);
            SIPS.put(a, paid);
            if (paid) {
                ServerLevel level = player.serverLevel();
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1f, 0.9f);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.3f, 1.8f);
            }
        }
        if (paid) {
            // Kindling, Lamplight and gear raise the sip; Scorch cuts it (GDD §7.10, §8.4, §8.6).
            float heal = (FlaskRules.healPerTick(player.getMaxHealth(), FlaskService.get(player).kindling())
                    + player.getMaxHealth() * dev.vestige.stats.Stats.of(player).flaskHealBonus / FlaskRules.HEAL_TICKS)
                    * dev.vestige.combat.status.StatusService.healFactor(player);
            // Set directly: health scaling must not multiply a heal that is already in VESTIGE units.
            player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + heal));
            if (player.tickCount % 3 == 0) {
                player.serverLevel().sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.3, 0.4, 0.3, 0.01);
            }
        }
    }

    // ------------------------------------------------------------------ death, Glim and the Ember-Ghost

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide) {
            return;
        }
        if (dead instanceof ServerPlayer player) {
            onPlayerDeath(player);
            return;
        }
        if (dead.getKillCredit() instanceof ServerPlayer killer) {
            long glim = Math.round(glimFor(dead) * dev.vestige.stats.Stats.of(killer).glimGain);
            if (glim > 0) {
                GlimService.add(killer, glim);
            }
        }
    }

    /** Glim a creature is worth to its killer (GDD §23.1): profile value for VESTIGE enemies, by health for vanilla hostiles. */
    public static long glimFor(LivingEntity e) {
        if (e instanceof VestigeMob mob) {
            return mob.profile().map(p -> GlimRules.profile(p.glim(), p.health())).orElse(0L);
        }
        return e instanceof Enemy ? GlimRules.vanillaMob(e.getMaxHealth()) : 0L;
    }

    private static void onPlayerDeath(ServerPlayer player) {
        long glim = GlimService.get(player);
        if (glim > 0 && !player.isSpectator()) {
            spawnGhost(player, glim);
            GlimService.set(player, 0);
        }
        if (ServerConfig.RUN_MODE.get() == ServerConfig.RunMode.WAYFARER && !player.level().getGameRules().getBoolean(
                net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY)) {
            KEPT.put(player.getUUID(), player.getInventory().save(new ListTag()));
        }
    }

    /** Leaves the Glim where the player fell (or where they last stood safely), replacing any older ghost. */
    public static EmberGhost spawnGhost(ServerPlayer player, long glim) {
        ServerLevel level = player.serverLevel();
        Vec3 at = player.position();
        if (player.getY() < level.getMinBuildHeight() + 1 || player.isInLava()) {
            at = SAFE.getOrDefault(player.getUUID(), Vec3.atBottomCenterOf(level.getSharedSpawnPos()));
        }
        EmberGhost ghost = EMBER_GHOST.get().create(level);
        if (ghost == null) {
            return null;
        }
        ghost.moveTo(at.x, at.y, at.z, player.getYRot(), 0f);
        ghost.configure(player, glim);
        UUID old = EmberGhosts.get(level.getServer()).replace(player.getUUID(), ghost.getUUID());
        if (old != null) {
            for (ServerLevel l : level.getServer().getAllLevels()) {
                var e = l.getEntity(old);
                if (e != null) {
                    e.discard();
                }
            }
        }
        level.addFreshEntity(ghost);
        return ghost;
    }

    /** Wayfarer: the inventory stays with the player, so nothing drops. */
    private static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && KEPT.containsKey(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    private static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath() && event.getEntity() instanceof ServerPlayer player) {
            ListTag kept = KEPT.remove(event.getOriginal().getUUID());
            if (kept != null) {
                player.getInventory().load(kept);
            }
        }
    }

    /** Respawning (at the last Waylight rested at) refills the flask, as resting does. */
    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.isEndConquered()) {
            FlaskService.refill(player);
        }
    }
}
