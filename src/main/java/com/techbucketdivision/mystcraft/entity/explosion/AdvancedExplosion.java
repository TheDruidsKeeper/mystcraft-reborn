package com.techbucketdivision.mystcraft.entity.explosion;

import com.techbucketdivision.mystcraft.network.ExplosionEffectsPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Meteor explosion (REQUIREMENTS §9 ExplosionAdvanced): a 16³ ray cube like vanilla collects blocks; entities take
 * {@code ((f²+f)/2 · 8 · size + 1)} damage with knockback; the attached {@link Effect}s run per collected block.
 * Visuals are sent to clients within 64 blocks as an {@link ExplosionEffectsPayload}.
 */
public final class AdvancedExplosion {
    /** Per-block post-processing step. */
    @FunctionalInterface
    public interface Effect {
        void apply(ServerLevel level, AdvancedExplosion explosion, BlockPos pos, RandomSource random);
    }

    /** Client particles only (no server work). */
    public static final Effect BASIC = (level, explosion, pos, random) -> {};
    /** Removes the block without drops. */
    public static final Effect BREAK_NO_DROP = (level, explosion, pos, random) -> level.removeBlock(pos, false);
    /** Removes the block, dropping its items with 30% probability. */
    public static final Effect BREAK_DROP_ITEMS = (level, explosion, pos, random) -> {
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && random.nextFloat() < 0.3f) {
            Block.dropResources(state, level, pos);
        }
        level.removeBlock(pos, false);
    };
    /** 1/3 chance of fire on an opaque support. */
    public static final Effect FIRE = (level, explosion, pos, random) -> {
        if (level.getBlockState(pos.below()).isSolidRender() && level.getBlockState(pos).isAir() && random.nextInt(3) == 0) {
            level.setBlock(pos, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
        }
    };
    /** 1/20 chance of an ore on air above an opaque block (coal 0.5 / iron 0.3 / gold 0.2). */
    public static final Effect PLACE_ORES = (level, explosion, pos, random) -> {
        if (random.nextInt(20) != 0) return;
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.below()).isSolidRender()) return;
        BlockState ore = randomOre(random);
        if (ore != null) level.setBlock(pos, ore, Block.UPDATE_ALL);
    };

    private static final List<Map.Entry<BlockState, Float>> METEOR_BLOCKS = new ArrayList<>();

    static {
        registerMeteorBlock(Blocks.COAL_ORE.defaultBlockState(), 0.5f);
        registerMeteorBlock(Blocks.IRON_ORE.defaultBlockState(), 0.3f);
        registerMeteorBlock(Blocks.GOLD_ORE.defaultBlockState(), 0.2f);
    }

    /** Add-on hook (replaces the original IMC {@code meteorblock}). */
    public static synchronized void registerMeteorBlock(BlockState state, float weight) {
        METEOR_BLOCKS.add(Map.entry(state, weight));
    }

    private static @Nullable BlockState randomOre(RandomSource random) {
        float total = 0;
        for (var e : METEOR_BLOCKS) total += e.getValue();
        if (total <= 0) return null;
        float roll = random.nextFloat() * total;
        for (var e : METEOR_BLOCKS) {
            roll -= e.getValue();
            if (roll <= 0) return e.getKey();
        }
        return METEOR_BLOCKS.getLast().getKey();
    }

    private static final int RAYS = 16;

    private final ServerLevel level;
    private final @Nullable Entity exploder;
    private final double x, y, z;
    private final float size;
    private final List<Effect> effects = new ArrayList<>();
    private final List<BlockPos> blocks = new ArrayList<>();
    private final Map<Player, Vec3> hitPlayers = new HashMap<>();

    public AdvancedExplosion(ServerLevel level, @Nullable Entity exploder, double x, double y, double z, float size) {
        this.level = level;
        this.exploder = exploder;
        this.x = x;
        this.y = y;
        this.z = z;
        this.size = size;
    }

    public AdvancedExplosion addEffect(Effect effect) {
        effects.add(effect);
        return this;
    }

    public List<BlockPos> affectedBlocks() {
        return blocks;
    }

    public Map<Player, Vec3> hitPlayers() {
        return hitPlayers;
    }

    public float size() {
        return size;
    }

    public Vec3 center() {
        return new Vec3(x, y, z);
    }

    /** Collects blocks and damages entities, then applies effects, sound, particles and the client payload. */
    public void explode() {
        collectBlocks();
        damageEntities();
        // Both the SoundEvent and Holder<SoundEvent> playSound overloads exist, so either type of GENERIC_EXPLODE compiles.
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4.0f,
                (1.0f + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.2f) * 0.7f);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0, 0, 0, 0);
        RandomSource random = level.getRandom();
        for (BlockPos pos : blocks) {
            for (Effect effect : effects) effect.apply(level, this, pos, random);
        }
        PacketDistributor.sendToPlayersNear(level, null, x, y, z, 64.0,
                new ExplosionEffectsPayload(x, y, z, size, List.copyOf(blocks)));
    }

    private void collectBlocks() {
        Set<BlockPos> found = new HashSet<>();
        RandomSource random = level.getRandom();
        for (int i = 0; i < RAYS; i++) {
            for (int j = 0; j < RAYS; j++) {
                for (int k = 0; k < RAYS; k++) {
                    if (i != 0 && i != RAYS - 1 && j != 0 && j != RAYS - 1 && k != 0 && k != RAYS - 1) continue;
                    double dx = i / (RAYS - 1.0) * 2.0 - 1.0;
                    double dy = j / (RAYS - 1.0) * 2.0 - 1.0;
                    double dz = k / (RAYS - 1.0) * 2.0 - 1.0;
                    double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    dx /= len;
                    dy /= len;
                    dz /= len;
                    float power = size * (0.7f + random.nextFloat() * 0.6f);
                    double px = x, py = y, pz = z;
                    for (float step = 0.3f; power > 0; power -= step * 0.75f) {
                        BlockPos pos = BlockPos.containing(px, py, pz);
                        if (!level.isOutsideBuildHeight(pos)) {
                            BlockState state = level.getBlockState(pos);
                            float resistance = state.isAir() ? 0 : state.getBlock().getExplosionResistance();
                            power -= (resistance + 0.3f) * step;
                            if (power > 0) found.add(pos);
                        } else {
                            power -= 0.3f * step;
                        }
                        px += dx * step;
                        py += dy * step;
                        pz += dz * step;
                    }
                }
            }
        }
        blocks.addAll(found);
    }

    private void damageEntities() {
        float reach = size * 2.0f;
        AABB box = new AABB(Mth.floor(x - reach - 1), Mth.floor(y - reach - 1), Mth.floor(z - reach - 1),
                Mth.floor(x + reach + 1), Mth.floor(y + reach + 1), Mth.floor(z + reach + 1));
        Vec3 center = new Vec3(x, y, z);
        DamageSource source = level.damageSources().explosion(exploder, exploder);
        for (Entity entity : level.getEntities(exploder, box)) {
            double dist = Math.sqrt(entity.distanceToSqr(x, y, z)) / reach;
            if (dist >= 1.0) continue;
            double dx = entity.getX() - x;
            double dy = entity.getEyeY() - y;
            double dz = entity.getZ() - z;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len == 0) continue;
            dx /= len;
            dy /= len;
            dz /= len;
            double seen = ServerExplosion.getSeenPercent(center, entity);
            double force = (1.0 - dist) * seen;
            entity.hurtServer(level, source, (float) (int) ((force * force + force) / 2.0 * 8.0 * reach + 1.0));
            Vec3 push = new Vec3(dx * force, dy * force, dz * force);
            entity.setDeltaMovement(entity.getDeltaMovement().add(push));
            if (entity instanceof Player player) hitPlayers.put(player, push);
        }
    }
}
