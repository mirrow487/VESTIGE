package dev.vestige.combat.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A named collection of actions plus how player inputs map onto them (GDD §7.4, §10.1).
 * Enemy movesets only use {@link #actions()}; their AI picks actions by name.
 *
 * @param light    light-attack combo chain, in order
 * @param heavy    heavy attack (may be chargeable)
 * @param running  attack started while sprinting
 * @param rolling  attack started right after a dodge
 */
public record MoveSet(Map<String, ActionDef> actions, List<String> light, Optional<String> heavy,
                      Optional<String> running, Optional<String> rolling) {
    public static final Codec<MoveSet> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, ActionDef.CODEC).fieldOf("actions").forGetter(MoveSet::actions),
            Codec.STRING.listOf().optionalFieldOf("light", List.of()).forGetter(MoveSet::light),
            Codec.STRING.optionalFieldOf("heavy").forGetter(MoveSet::heavy),
            Codec.STRING.optionalFieldOf("running").forGetter(MoveSet::running),
            Codec.STRING.optionalFieldOf("rolling").forGetter(MoveSet::rolling)
    ).apply(i, MoveSet::new));

    public Optional<ActionDef> action(String name) {
        return Optional.ofNullable(actions.get(name));
    }
}
