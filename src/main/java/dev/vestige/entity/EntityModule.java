package dev.vestige.entity;

import dev.vestige.VestigeIds;
import dev.vestige.core.SyncedDataTable;
import dev.vestige.core.VestigeModule;
import dev.vestige.entity.mob.Hollowed;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import dev.vestige.entity.ai.AttackTokens;

/** VESTIGE enemies (GDD §18) and their shared AI framework. */
public final class EntityModule implements VestigeModule {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, VestigeIds.MOD_ID);

    /** Enemy profiles from {@code data/<ns>/vestige/enemies/*.json}, keyed by entity type id. */
    public static final SyncedDataTable<EnemyProfile> ENEMIES = new SyncedDataTable<>("enemies", "vestige/enemies", EnemyProfile.CODEC);

    public static final DeferredHolder<EntityType<?>, EntityType<Hollowed>> HOLLOWED = ENTITY_TYPES.register("hollowed",
            () -> EntityType.Builder.of(Hollowed::new, MobCategory.MONSTER).sized(0.6f, 1.95f).eyeHeight(1.74f)
                    .clientTrackingRange(8).build(VestigeIds.id("hollowed").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<Hollowed>> HOLLOWED_BRUTE = ENTITY_TYPES.register("hollowed_brute",
            () -> EntityType.Builder.of(Hollowed::new, MobCategory.MONSTER).sized(0.8f, 2.45f).eyeHeight(2.18f)
                    .clientTrackingRange(8).build(VestigeIds.id("hollowed_brute").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<dev.vestige.entity.mob.TollingRam>> TOLLING_RAM =
            ENTITY_TYPES.register("tolling_ram", () -> EntityType.Builder.of(dev.vestige.entity.mob.TollingRam::new, MobCategory.MONSTER)
                    .sized(1.35f, 1.6f).eyeHeight(1.35f).clientTrackingRange(10).build(VestigeIds.id("tolling_ram").toString()));

    @Override
    public String id() {
        return "entity";
    }

    @Override
    public void init(IEventBus modBus, IEventBus gameBus) {
        ENTITY_TYPES.register(modBus);
        ENEMIES.register(modBus, gameBus);
        modBus.addListener(EntityModule::attributes);
        modBus.addListener(EntityModule::spawnPlacements);
        gameBus.addListener((ServerStoppedEvent e) -> AttackTokens.clear());
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(HOLLOWED.get(), Hollowed.attributes(0.23, 0.0).build());
        event.put(HOLLOWED_BRUTE.get(), Hollowed.attributes(0.21, 0.6).build());
        event.put(TOLLING_RAM.get(), dev.vestige.entity.mob.TollingRam.attributes().build());
    }

    private static void spawnPlacements(RegisterSpawnPlacementsEvent event) {
        for (var type : new EntityType[] {HOLLOWED.get(), HOLLOWED_BRUTE.get()}) {
            @SuppressWarnings("unchecked")
            EntityType<Hollowed> t = (EntityType<Hollowed>) type;
            event.register(t, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
        event.register(TOLLING_RAM.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                dev.vestige.entity.mob.TollingRam::checkSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
