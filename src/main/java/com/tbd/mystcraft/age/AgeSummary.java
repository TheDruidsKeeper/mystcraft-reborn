package com.tbd.mystcraft.age;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tbd.mystcraft.api.linking.LinkInfo;
import com.tbd.mystcraft.blockentity.BookUtil;
import com.tbd.mystcraft.instability.InstabilityController;
import com.tbd.mystcraft.item.DescriptiveBookItem;
import com.tbd.mystcraft.item.PageItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The summary page of a bound Descriptive Book (plan §2 rule 7): what the book view shows after the last page. Built
 * on the server from the Age data, the Age controller and - when the Age is loaded - its instability controller, and
 * synced to the book screen as a compound.
 *
 * @param score         the quantised instability score of the loaded Age, or -1 while the Age is not loaded
 * @param activeEffects instability providers currently dealt in the Age (ids), empty when not loaded
 * @param discovered    pages the game discovered at the first link
 * @param total         symbol pages in the book
 */
public record AgeSummary(String name, long seed, int baseInstability, int symbolInstability, int score, List<String> activeEffects,
                         int discovered, int total, List<String> authors, boolean dead) {
    public static final Codec<AgeSummary> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(AgeSummary::name),
            Codec.LONG.fieldOf("seed").forGetter(AgeSummary::seed),
            Codec.INT.fieldOf("base").forGetter(AgeSummary::baseInstability),
            Codec.INT.fieldOf("symbol").forGetter(AgeSummary::symbolInstability),
            Codec.INT.fieldOf("score").forGetter(AgeSummary::score),
            Codec.STRING.listOf().fieldOf("effects").forGetter(AgeSummary::activeEffects),
            Codec.INT.fieldOf("discovered").forGetter(AgeSummary::discovered),
            Codec.INT.fieldOf("total").forGetter(AgeSummary::total),
            Codec.STRING.listOf().fieldOf("authors").forGetter(AgeSummary::authors),
            Codec.BOOL.optionalFieldOf("dead", false).forGetter(AgeSummary::dead)
    ).apply(i, AgeSummary::new));

    /** The summary for a bound Descriptive Book, or {@code null} for anything else. */
    public static @Nullable AgeSummary of(MinecraftServer server, ItemStack book) {
        if (!DescriptiveBookItem.isDescriptiveBook(book)) return null;
        LinkInfo info = BookUtil.linkInfo(book);
        if (info == null || !info.isBound()) return null;
        AgeData data = DescriptiveBookItem.getAgeData(server, book);
        if (data == null) return null;
        AgeController controller = AgeControllers.server(server, data.uuid());
        int symbolInstability = controller == null ? 0 : controller.symbolInstability();
        int score = -1;
        List<String> effects = new ArrayList<>();
        ServerLevel level = server.getLevel(data.levelKey());
        if (level != null) {
            InstabilityController instability = InstabilityController.get(level);
            if (instability != null) {
                score = instability.getInstabilityScore();
                for (Map.Entry<String, Integer> e : instability.providerLevels().entrySet()) {
                    if (e.getValue() > 0) effects.add(e.getKey());
                }
            }
        }
        int discovered = 0, total = 0;
        for (ItemStack page : DescriptiveBookItem.getPages(book)) {
            if (!PageItem.isSymbolPage(page)) continue;
            total++;
            if (PageItem.isDiscovered(page)) discovered++;
        }
        return new AgeSummary(data.name(), data.seed(), data.baseInstability(), symbolInstability, score, effects, discovered, total,
                data.authors(), data.dead());
    }

    public Tag toTag() {
        return CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow();
    }

    public static @Nullable AgeSummary fromTag(CompoundTag tag, String key) {
        Tag child = tag.get(key);
        if (child == null) return null;
        return CODEC.parse(NbtOps.INSTANCE, child).result().orElse(null);
    }
}
