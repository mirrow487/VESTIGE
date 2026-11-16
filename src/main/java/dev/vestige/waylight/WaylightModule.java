package dev.vestige.waylight;

import dev.vestige.VestigeIds;
import dev.vestige.core.VestigeModule;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Waylights (GDD §6.6): checkpoints for resting, respawning and fast travel. */
public final class WaylightModule implements VestigeModule {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VestigeIds.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VestigeIds.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, VestigeIds.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, VestigeIds.MOD_ID);

    public static final DeferredBlock<WaylightBlock> WAYLIGHT = BLOCKS.register("waylight", () -> new WaylightBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(-1f, 3_600_000f).sound(SoundType.LANTERN)
                    .lightLevel(s -> s.getValue(WaylightBlock.LIT) ? 14 : 3).noOcclusion()));
    public static final DeferredItem<BlockItem> WAYLIGHT_ITEM = ITEMS.registerSimpleBlockItem(WAYLIGHT);
    public static final Supplier<BlockEntityType<WaylightBlockEntity>> WAYLIGHT_BE = BLOCK_ENTITIES.register("waylight",
            () -> BlockEntityType.Builder.of(WaylightBlockEntity::new, WAYLIGHT.get()).build(null));

    /** The Waylights each player has lit (saved, kept on death). */
    public static final Supplier<AttachmentType<WaylightDiscovery>> DISCOVERY = ATTACHMENTS.register("waylights",
            () -> AttachmentType.builder(() -> new WaylightDiscovery()).serialize(WaylightDiscovery.CODEC).copyOnDeath().build());

    private static volatile WaylightClientSink sink = WaylightClientSink.NONE;

    public static void installClientSink(WaylightClientSink s) {
        sink = s;
    }

    @Override
    public String id() {
        return "waylight";
    }

    @Override
    public void init(IEventBus modBus, IEventBus gameBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(WaylightModule::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar(VestigeIds.MOD_ID).versioned("1");
        r.playToClient(WaylightPayloads.Open.TYPE, WaylightPayloads.Open.CODEC, (p, ctx) -> sink.open(p));
        r.playToServer(WaylightPayloads.Act.TYPE, WaylightPayloads.Act.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player) {
                WaylightService.act(player, p);
            }
        });
    }
}
