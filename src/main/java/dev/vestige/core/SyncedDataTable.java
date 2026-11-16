package dev.vestige.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.vestige.VestigeIds;
import io.netty.buffer.ByteBuf;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * A datapack folder of JSON entries decoded with a {@link Codec}, reloadable with {@code /reload},
 * and mirrored to clients. Server and client keep separate copies (they share a JVM in
 * singleplayer), selected with {@link #side(boolean)}.
 *
 * @param <T> entry type
 */
public final class SyncedDataTable<T> {
    private final String name;
    private final String folder;
    private final Codec<T> codec;
    private final CustomPacketPayload.Type<SyncPayload> type;
    private final StreamCodec<ByteBuf, Map<ResourceLocation, T>> mapCodec;
    private volatile Map<ResourceLocation, T> server = Map.of();
    private volatile Map<ResourceLocation, T> client = Map.of();
    private final java.util.List<Runnable> clientListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<Runnable> serverListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Wire payload carrying the full table. */
    public final class SyncPayload implements CustomPacketPayload {
        final Map<ResourceLocation, T> entries;

        SyncPayload(Map<ResourceLocation, T> entries) {
            this.entries = entries;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return type;
        }
    }

    /**
     * @param name   short unique name, used for the payload id ({@code vestige:sync_<name>})
     * @param folder datapack folder under {@code data/<namespace>/}
     */
    public SyncedDataTable(String name, String folder, Codec<T> codec) {
        this.name = name;
        this.folder = folder;
        this.codec = codec;
        this.type = new CustomPacketPayload.Type<>(VestigeIds.id("sync_" + name));
        this.mapCodec = ByteBufCodecs.fromCodec(Codec.unboundedMap(ResourceLocation.CODEC, codec));
    }

    public void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(this::registerPayload);
        gameBus.addListener(this::addReloadListener);
        gameBus.addListener(this::onDatapackSync);
    }

    /** Entries for the logical side. */
    public Map<ResourceLocation, T> side(boolean clientSide) {
        return clientSide ? client : server;
    }

    public Map<ResourceLocation, T> side(Level level) {
        return side(level.isClientSide());
    }

    public Optional<T> get(boolean clientSide, ResourceLocation id) {
        return Optional.ofNullable(side(clientSide).get(id));
    }

    /** Called after the client copy is replaced (on the client thread). */
    public void onClientUpdate(Runnable listener) {
        clientListeners.add(listener);
    }

    /** Called after the server copy is replaced. */
    public void onServerUpdate(Runnable listener) {
        serverListeners.add(listener);
    }

    private void registerPayload(RegisterPayloadHandlersEvent event) {
        StreamCodec<ByteBuf, SyncPayload> payloadCodec = mapCodec.map(SyncPayload::new, p -> p.entries);
        event.registrar(VestigeIds.MOD_ID).versioned("1").playToClient(type, payloadCodec, (payload, ctx) -> {
            client = Collections.unmodifiableMap(new LinkedHashMap<>(payload.entries));
            clientListeners.forEach(Runnable::run);
        });
    }

    private void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), folder) {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager manager, ProfilerFiller profiler) {
                Map<ResourceLocation, T> decoded = new LinkedHashMap<>();
                jsons.forEach((id, json) -> {
                    DataResult<T> result = codec.parse(JsonOps.INSTANCE, json);
                    result.resultOrPartial(err -> VestigeIds.LOG.error("Invalid {} entry {}: {}", name, id, err))
                            .ifPresent(value -> decoded.put(id, value));
                });
                server = Collections.unmodifiableMap(decoded);
                VestigeIds.LOG.info("Loaded {} {} entries", decoded.size(), name);
                serverListeners.forEach(Runnable::run);
            }
        });
    }

    private void onDatapackSync(OnDatapackSyncEvent event) {
        SyncPayload payload = new SyncPayload(server);
        event.getRelevantPlayers().forEach(player -> Net.send(player, payload));
    }
}
