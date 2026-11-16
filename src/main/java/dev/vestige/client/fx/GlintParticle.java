package dev.vestige.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** The telegraph glint: a full-bright star that pops in, spins slightly and fades (GDD §7.12). */
public class GlintParticle extends TextureSheetParticle {
    private final float peakSize;

    protected GlintParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        pickSprite(sprites);
        lifetime = 12;
        peakSize = 0.55f;
        quadSize = 0.05f;
        gravity = 0f;
        hasPhysics = false;
        xd = yd = zd = 0;
        oRoll = roll = 0f;
    }

    @Override
    public void tick() {
        super.tick();
        float t = age / (float) lifetime;
        quadSize = peakSize * (t < 0.25f ? t / 0.25f : 1f - (t - 0.25f) / 0.75f * 0.85f);
        alpha = t < 0.6f ? 1f : 1f - (t - 0.6f) / 0.4f;
        oRoll = roll;
        roll += 0.08f;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public float getQuadSize(float partialTick) {
        return Mth.lerp(partialTick, quadSize * 0.95f, quadSize);
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new GlintParticle(level, x, y, z, sprites);
        }
    }
}
