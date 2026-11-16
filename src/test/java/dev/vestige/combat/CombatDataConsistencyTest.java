package dev.vestige.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.vestige.combat.action.ActionDef;
import dev.vestige.combat.action.MoveSet;
import dev.vestige.combat.anim.PoseLibrary;
import dev.vestige.entity.EnemyProfile;
import dev.vestige.weapon.WeaponProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Cross-checks the shipped combat data: every file decodes, every reference resolves, and every
 * enemy attack is readable (GDD §7.12: telegraph present, windup of at least 8 ticks).
 */
class CombatDataConsistencyTest {
    private static final Path DATA = Path.of(System.getProperty("vestige.resourceRoot", "src/main/resources"), "data", "vestige", "vestige");
    private static final Map<String, MoveSet> MOVESETS = new HashMap<>();
    private static final Map<String, WeaponProfile> WEAPONS = new HashMap<>();
    private static final Map<String, EnemyProfile> ENEMIES = new HashMap<>();

    private static <T> void load(String folder, Codec<T> codec, Map<String, T> into) throws IOException {
        try (Stream<Path> files = Files.list(DATA.resolve(folder))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonElement json = JsonParser.parseString(Files.readString(f));
                String id = "vestige:" + f.getFileName().toString().replace(".json", "");
                T value = codec.parse(JsonOps.INSTANCE, json).getOrThrow(err -> new AssertionError(f + ": " + err));
                into.put(id, value);
            }
        }
    }

    @BeforeAll
    static void loadAll() throws IOException {
        load("movesets", MoveSet.CODEC, MOVESETS);
        load("weapons", WeaponProfile.CODEC, WEAPONS);
        load("enemies", EnemyProfile.CODEC, ENEMIES);
        assertFalse(MOVESETS.isEmpty());
        assertFalse(WEAPONS.isEmpty());
        assertFalse(ENEMIES.isEmpty());
    }

    @Test
    void everyActionPoseExists() {
        List<String> missing = new ArrayList<>();
        MOVESETS.forEach((id, set) -> set.actions().forEach((name, def) -> {
            if (!def.pose().isEmpty() && PoseLibrary.get(def.pose()).isEmpty()) {
                missing.add(id + "#" + name + " -> " + def.pose());
            }
        }));
        assertTrue(missing.isEmpty(), "Unknown poses: " + missing);
    }

    @Test
    void movesetInputChainsResolve() {
        List<String> missing = new ArrayList<>();
        MOVESETS.forEach((id, set) -> {
            set.light().forEach(a -> { if (!set.actions().containsKey(a)) missing.add(id + " light " + a); });
            set.heavy().ifPresent(a -> { if (!set.actions().containsKey(a)) missing.add(id + " heavy " + a); });
            set.running().ifPresent(a -> { if (!set.actions().containsKey(a)) missing.add(id + " running " + a); });
            set.rolling().ifPresent(a -> { if (!set.actions().containsKey(a)) missing.add(id + " rolling " + a); });
            set.art().ifPresent(a -> {
                if (!set.actions().containsKey(a)) missing.add(id + " art " + a);
                else if (set.actions().get(a).art().isEmpty()) missing.add(id + " art action without cost " + a);
            });
            set.riposte().ifPresent(a -> {
                if (!set.actions().containsKey(a)) missing.add(id + " riposte " + a);
                else if (!set.actions().get(a).defense().invulnerable()) missing.add(id + " riposte must be invulnerable " + a);
            });
            set.actions().forEach((name, def) -> def.art().flatMap(x -> x.onParry()).ifPresent(c -> {
                if (!set.actions().containsKey(c)) missing.add(id + "#" + name + " on_parry " + c);
            }));
        });
        assertTrue(missing.isEmpty(), String.valueOf(missing));
    }

    @Test
    void playerMovesetHasDodgesAndReactions() {
        MoveSet player = MOVESETS.get("vestige:player");
        for (String a : List.of("roll", "backstep", "flinch", "stagger", "guard_weapon", "guard_shield", "parry_buckler", "guard_break")) {
            assertTrue(player.actions().containsKey(a), "player moveset missing " + a);
        }
        assertTrue(player.actions().get("roll").dodge().isPresent());
    }

    @Test
    void everyWeaponClassHasArtAndRiposte() {
        WEAPONS.values().forEach(w -> {
            MoveSet set = MOVESETS.get(w.moveset().toString());
            assertTrue(set.art().isPresent(), w.moveset() + " has no weapon art");
            assertTrue(set.riposte().isPresent(), w.moveset() + " has no riposte");
        });
    }

    @Test
    void weaponsReferenceExistingMovesets() {
        WEAPONS.forEach((id, w) -> assertTrue(MOVESETS.containsKey(w.moveset().toString()), id + " -> " + w.moveset()));
    }

    @Test
    void enemyAttacksResolveAndAreReadable() {
        List<String> problems = new ArrayList<>();
        ENEMIES.forEach((id, e) -> {
            MoveSet set = MOVESETS.get(e.moveset().toString());
            if (set == null) {
                problems.add(id + ": no moveset " + e.moveset());
                return;
            }
            for (String reaction : List.of("flinch", "stagger", "parried", "riposted")) {
                if (!set.actions().containsKey(reaction)) problems.add(id + ": missing " + reaction);
            }
            for (EnemyProfile.Attack a : e.attacks()) {
                ActionDef def = set.actions().get(a.action());
                if (def == null) {
                    problems.add(id + ": unknown attack " + a.action());
                    continue;
                }
                if (def.telegraph().isEmpty()) problems.add(id + "#" + a.action() + ": no telegraph");
                if (def.timing().windup() < 8) problems.add(id + "#" + a.action() + ": windup " + def.timing().windup() + " < 8");
                if (def.hit().isEmpty()) problems.add(id + "#" + a.action() + ": no hitbox");
                a.followUp().ifPresent(f -> { if (!set.actions().containsKey(f)) problems.add(id + ": unknown follow-up " + f); });
            }
        });
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }
}
