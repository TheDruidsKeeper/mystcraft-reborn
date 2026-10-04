package com.techbucketdivision.mystcraft.world.feature;

import com.techbucketdivision.mystcraft.api.symbol.logic.Populator;
import com.techbucketdivision.mystcraft.registry.ModStructures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * "Villages" / "Strongholds" / "Mineshafts" / "Nether Fortress" (REQUIREMENTS §4.3.8). Vanilla structures are
 * placed by the chunk generator's structure state, so this populator is a marker: {@code AgeChunkGenerator} builds its
 * {@code ChunkGeneratorStructureState} from the structure sets enabled by the populators registered on the Age
 * (see {@code AgeChunkGenerator#createState}). {@link #populate} itself does nothing. The {@code vault} kind enables
 * the mod's own Facility structure set (docs/impl/FACILITY_PLAN.md) the same way.
 */
public final class VanillaStructurePopulator implements Populator {
    public static final String VILLAGES = "villages";
    public static final String STRONGHOLDS = "strongholds";
    public static final String MINESHAFTS = "mineshafts";
    public static final String NETHER_FORTRESS = "nether_fortress";
    public static final String VAULT = "vault";

    private final String kind;
    private final @Nullable ResourceKey<StructureSet> structureSet;

    public VanillaStructurePopulator(String kind) {
        this.kind = kind.toLowerCase(Locale.ROOT);
        this.structureSet = switch (this.kind) {
            case VILLAGES -> BuiltinStructureSets.VILLAGES;
            case STRONGHOLDS -> BuiltinStructureSets.STRONGHOLDS;
            case MINESHAFTS -> BuiltinStructureSets.MINESHAFTS;
            case NETHER_FORTRESS -> BuiltinStructureSets.NETHER_COMPLEXES;
            case VAULT -> ModStructures.FACILITY_SET;
            default -> null;
        };
    }

    public String kind() {
        return kind;
    }

    /** The vanilla structure set this symbol enables, or {@code null} for an unknown kind. */
    public @Nullable ResourceKey<StructureSet> structureSet() {
        return structureSet;
    }

    @Override
    public boolean populate(WorldGenLevel level, RandomSource random, int chunkX, int chunkZ, boolean flag) {
        return false;
    }
}
