package dev.vestige.equipment;

import dev.vestige.VestigeIds;
import dev.vestige.player.FlaskService;
import dev.vestige.player.FlaskRules;
import dev.vestige.player.GlimService;
import dev.vestige.stats.Attribute;
import dev.vestige.stats.Perks;
import dev.vestige.stats.Stats;
import dev.vestige.waylight.WaylightBlock;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Equipment actions sent by the client (all validated on the server). */
public final class EquipmentNetwork {
    private EquipmentNetwork() {}

    /** C→S: a simple action. */
    public record Act(int action) implements CustomPacketPayload {
        public static final int STANCE = 0, CYCLE_RELIC = 1, USE_RELIC = 2, OPEN_EQUIPMENT = 3;
        public static final Type<Act> TYPE = new Type<>(VestigeIds.id("equipment_act"));
        public static final StreamCodec<ByteBuf, Act> CODEC = ByteBufCodecs.VAR_INT.map(Act::new, Act::action);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C→S: raise attributes at the Waylight at {@code pos} (points per attribute ordinal). */
    public record Attune(BlockPos pos, java.util.List<Integer> points) implements CustomPacketPayload {
        public static final Type<Attune> TYPE = new Type<>(VestigeIds.id("attune"));
        public static final StreamCodec<ByteBuf, Attune> CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Attune::pos,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(8)), Attune::points, Attune::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C→S: kindle the Wick Flask at the Waylight at {@code pos}. */
    public record Kindle(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Kindle> TYPE = new Type<>(VestigeIds.id("kindle"));
        public static final StreamCodec<ByteBuf, Kindle> CODEC = BlockPos.STREAM_CODEC.map(Kindle::new, Kindle::pos);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void init(IEventBus modBus) {
        modBus.addListener(EquipmentNetwork::register);
    }

    private static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar(VestigeIds.MOD_ID).versioned("1");
        r.playToServer(Act.TYPE, Act.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player) {
                switch (p.action()) {
                    case Act.STANCE -> Stance.toggle(player);
                    case Act.CYCLE_RELIC -> cycleRelic(player);
                    case Act.USE_RELIC -> useRelic(player);
                    case Act.OPEN_EQUIPMENT -> EquipmentMenus.open(player);
                    default -> {
                    }
                }
            }
        });
        r.playToServer(Attune.TYPE, Attune.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player && atLitWaylight(player, p.pos())) {
                Map<Attribute, Integer> add = new HashMap<>();
                for (int i = 0; i < Math.min(p.points().size(), Attribute.values().length); i++) {
                    if (p.points().get(i) > 0) {
                        add.put(Attribute.values()[i], p.points().get(i));
                    }
                }
                long spent = Attunement.attune(player, add);
                player.displayClientMessage(Component.translatable(spent < 0 ? "message.vestige.attune_refused" : "message.vestige.attuned",
                        player.getData(EquipmentModule.ATTRIBUTES).level(), spent), true);
                if (spent > 0) {
                    player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 0.8f);
                }
            }
        });
        r.playToServer(Kindle.TYPE, Kindle.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player && atLitWaylight(player, p.pos())) {
                player.displayClientMessage(Component.translatable(kindle(player) ? "message.vestige.kindled" : "message.vestige.kindle_refused",
                        FlaskService.get(player).kindling()), true);
            }
        });
    }

    static boolean atLitWaylight(ServerPlayer player, BlockPos pos) {
        var state = player.serverLevel().getBlockState(pos);
        return state.getBlock() instanceof WaylightBlock && state.getValue(WaylightBlock.LIT)
                && player.distanceToSqr(Vec3.atCenterOf(pos)) <= 36;
    }

    /** Kindling cost (GDD §8.4): one Kindling Ember and Glim rising with the level. */
    public static long kindleCost(int level) {
        return 400L * (level + 1);
    }

    /** Raises the flask's Kindling by one (+1..+5): heal 40% → 60%. */
    public static boolean kindle(ServerPlayer player) {
        int level = FlaskService.get(player).kindling();
        if (level >= FlaskRules.MAX_KINDLING) {
            return false;
        }
        int ember = player.getInventory().findSlotMatchingItem(new net.minecraft.world.item.ItemStack(EquipmentItems.KINDLING_EMBER.get()));
        boolean creative = player.getAbilities().instabuild;
        if (!creative && (ember < 0 || GlimService.get(player) < kindleCost(level))) {
            return false;
        }
        if (!creative) {
            GlimService.spend(player, kindleCost(level));
            player.getInventory().getItem(ember).shrink(1);
        }
        var f = FlaskService.get(player);
        FlaskService.set(player, f.charges(), f.max(), level + 1);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1f, 0.7f);
        player.serverLevel().sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1, player.getZ(), 20, 0.3, 0.4, 0.3, 0.03);
        return true;
    }

    private static void cycleRelic(ServerPlayer player) {
        RpgInventory inv = player.getData(EquipmentModule.RPG_INVENTORY);
        EquipSlot[] relics = {EquipSlot.RELIC_1, EquipSlot.RELIC_2, EquipSlot.RELIC_3, EquipSlot.RELIC_4};
        for (int i = 1; i <= 4; i++) {
            int next = Math.floorMod(inv.activeRelic + i, 4);
            if (!inv.get(relics[next]).isEmpty()) {
                inv.activeRelic = next;
                break;
            }
        }
        player.syncData(EquipmentModule.RPG_INVENTORY);
    }

    /** The active relic's verb. Pale Hook: grapple to a block within 24 blocks. */
    private static void useRelic(ServerPlayer player) {
        RpgInventory inv = player.getData(EquipmentModule.RPG_INVENTORY);
        if (!inv.activeRelicStack().is(EquipmentItems.PALE_HOOK.get()) || !Stats.of(player).has(Perks.PALE_HOOK)) {
            return;
        }
        long now = player.level().getGameTime();
        if (now - player.getPersistentData().getLong("vestige_pale_hook") < 30) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(24));
        var hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        player.getPersistentData().putLong("vestige_pale_hook", now);
        Vec3 to = hit.getLocation().subtract(player.position());
        Vec3 pull = to.normalize().scale(Math.min(2.2, 0.6 + to.length() * 0.09)).add(0, 0.35, 0);
        player.setDeltaMovement(pull);
        player.hurtMarked = true;
        player.resetFallDistance();
        var level = player.serverLevel();
        for (double d = 0; d < to.length(); d += 0.6) {
            Vec3 p = eye.add(to.normalize().scale(d));
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1f, 0.7f);
    }
}
