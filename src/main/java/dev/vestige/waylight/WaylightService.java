package dev.vestige.waylight;

import dev.vestige.combat.player.PlayerCombat;
import dev.vestige.player.FlaskService;
import dev.vestige.vitals.StaminaService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Lighting, resting at and travelling between Waylights (GDD §6.6). */
public final class WaylightService {
    private WaylightService() {}

    public static GlobalPos at(ServerLevel level, BlockPos pos) {
        return GlobalPos.of(level.dimension(), pos);
    }

    public static WaylightDiscovery discovery(ServerPlayer player) {
        return player.getData(WaylightModule.DISCOVERY);
    }

    /** A right-click: an unlit (or not yet discovered) Waylight is lit; a known one opens the menu. */
    public static void use(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        GlobalPos gp = at(level, pos);
        if (!discovery(player).has(gp) || !level.getBlockState(pos).getValue(WaylightBlock.LIT)) {
            light(player, pos);
        } else {
            openMenu(player, pos);
        }
    }

    /** Lights the lantern (for everyone) and records the discovery (for this player), with a title card. */
    public static void light(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof WaylightBlock)) {
            return;
        }
        if (!state.getValue(WaylightBlock.LIT)) {
            level.setBlock(pos, state.setValue(WaylightBlock.LIT, true), 3);
        }
        String name = name(level, pos);
        WaylightRegistry.get(level.getServer()).put(at(level, pos), name);
        if (discovery(player).add(at(level, pos))) {
            level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1f, 0.8f);
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.7f, 1.4f);
            level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 1.15, pos.getZ() + 0.5, 30, 0.2, 0.3, 0.2, 0.04);
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(name)));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title.vestige.waylight_lit")));
        }
    }

    /** The Waylight's name: its own, or "<Biome> Waylight" chosen the first time it is lit. */
    public static String name(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof WaylightBlockEntity be) {
            if (be.name().isEmpty()) {
                String biome = level.getBiome(pos).unwrapKey().map(k -> {
                    String path = k.location().getPath().replace('_', ' ');
                    return Character.toUpperCase(path.charAt(0)) + path.substring(1);
                }).orElse("Wild");
                be.setName(biome + " Waylight");
            }
            return be.name();
        }
        return "Waylight";
    }

    static double nearestHostile(ServerPlayer player, double within) {
        var box = player.getBoundingBox().inflate(within);
        return player.level().getEntitiesOfClass(Mob.class, box, m -> m instanceof Enemy && m.isAlive()).stream()
                .mapToDouble(m -> m.distanceTo(player)).min().orElse(Double.MAX_VALUE);
    }

    private static double distance(ServerPlayer player, BlockPos pos) {
        return Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(pos)));
    }

    public static WaylightRules.Refusal restRefusal(ServerPlayer player, BlockPos pos) {
        return WaylightRules.rest(PlayerCombat.inCombat(player), nearestHostile(player, WaylightRules.REST_HOSTILE_RADIUS + 1),
                distance(player, pos));
    }

    public static WaylightRules.Refusal travelRefusal(ServerPlayer player, BlockPos pos, GlobalPos target) {
        ServerLevel level = player.serverLevel();
        GlobalPos from = at(level, pos);
        boolean known = discovery(player).has(target) && WaylightRegistry.get(level.getServer()).get(target).isPresent();
        return WaylightRules.travel(PlayerCombat.inCombat(player), nearestHostile(player, WaylightRules.TRAVEL_HOSTILE_RADIUS + 1),
                distance(player, pos), discovery(player).has(from), known, from.equals(target));
    }

    /** Rest: respawn here, refill the flask, recover fully, kneel before the lantern. */
    public static boolean rest(ServerPlayer player, BlockPos pos) {
        if (restRefusal(player, pos) != WaylightRules.Refusal.NONE) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        BlockPos stand = standingSpot(level, pos);
        player.setRespawnPosition(level.dimension(), stand, player.getYRot(), true, false);
        FlaskService.refill(player);
        player.setHealth(player.getMaxHealth());
        StaminaService.set(player, StaminaService.get(player).max());
        player.removeAllEffects();
        PlayerCombat.playCommon(player, "rest");
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 0.9f);
        level.playSound(null, pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1f, 1f);
        level.sendParticles(ParticleTypes.WAX_OFF, player.getX(), player.getY() + 1, player.getZ(), 20, 0.5, 0.6, 0.5, 0.02);
        player.displayClientMessage(Component.translatable("message.vestige.waylight_rested"), true);
        return true;
    }

    /** Travel to another lit Waylight (any dimension); arrives standing beside it. */
    public static boolean travel(ServerPlayer player, BlockPos pos, GlobalPos target) {
        if (travelRefusal(player, pos, target) != WaylightRules.Refusal.NONE) {
            return false;
        }
        ServerLevel dest = player.server.getLevel(target.dimension());
        if (dest == null || !(dest.getBlockState(target.pos()).getBlock() instanceof WaylightBlock)) {
            WaylightRegistry.get(player.server).remove(target);
            return false;
        }
        ServerLevel from = player.serverLevel();
        from.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1, player.getZ(), 40, 0.3, 0.8, 0.3, 0.05);
        BlockPos stand = standingSpot(dest, target.pos());
        player.teleportTo(dest, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, player.getYRot(), 0f);
        dest.playSound(null, stand, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1f, 1.2f);
        dest.sendParticles(ParticleTypes.FLAME, stand.getX() + 0.5, stand.getY() + 1, stand.getZ() + 0.5, 40, 0.3, 0.8, 0.3, 0.05);
        return true;
    }

    /** The free spot in front of the Waylight (two blocks of air over solid ground), or above it. */
    public static BlockPos standingSpot(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        var facing = state.hasProperty(WaylightBlock.FACING) ? state.getValue(WaylightBlock.FACING) : net.minecraft.core.Direction.NORTH;
        for (var dir : List.of(facing, facing.getClockWise(), facing.getCounterClockWise(), facing.getOpposite())) {
            BlockPos p = pos.relative(dir);
            if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && !level.getBlockState(p.below()).isAir()) {
                return p;
            }
        }
        return pos.above(2);
    }

    /** Destinations for this player: every lit Waylight they discovered, nearest first. */
    public static List<WaylightPayloads.Destination> destinations(ServerPlayer player, BlockPos from) {
        GlobalPos here = at(player.serverLevel(), from);
        List<WaylightPayloads.Destination> out = new ArrayList<>();
        for (WaylightRegistry.Entry e : WaylightRegistry.get(player.server).all()) {
            if (!e.pos().equals(here) && discovery(player).has(e.pos())) {
                int dist = e.pos().dimension() == here.dimension() ? (int) Math.sqrt(e.pos().pos().distSqr(from)) : -1;
                out.add(new WaylightPayloads.Destination(e.pos(), e.name(), e.pos().dimension().location().toString(), dist));
            }
        }
        out.sort(Comparator.comparingInt(d -> d.distance() < 0 ? Integer.MAX_VALUE : d.distance()));
        return out;
    }

    public static void openMenu(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        WaylightRules.Refusal travel = WaylightRules.travel(PlayerCombat.inCombat(player),
                nearestHostile(player, WaylightRules.TRAVEL_HOSTILE_RADIUS + 1), distance(player, pos), true, true, false);
        dev.vestige.core.Net.send(player, new WaylightPayloads.Open(pos, name(level, pos), restRefusal(player, pos).name(), travel.name(),
                destinations(player, pos)));
    }

    /** Server handler for the menu. */
    static void act(ServerPlayer player, WaylightPayloads.Act act) {
        if (!(player.serverLevel().getBlockState(act.pos()).getBlock() instanceof WaylightBlock)) {
            return;
        }
        boolean ok = act.travel() ? travel(player, act.pos(), act.target()) : rest(player, act.pos());
        if (!ok) {
            WaylightRules.Refusal why = act.travel() ? travelRefusal(player, act.pos(), act.target()) : restRefusal(player, act.pos());
            player.displayClientMessage(Component.translatable("message.vestige.waylight_refused." + why.name().toLowerCase(java.util.Locale.ROOT)), true);
        }
    }
}
