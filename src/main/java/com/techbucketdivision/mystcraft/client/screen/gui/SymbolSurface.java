package com.techbucketdivision.mystcraft.client.screen.gui;

import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.api.symbol.ModifierSlot;
import com.techbucketdivision.mystcraft.api.symbol.SymbolCategory;
import com.techbucketdivision.mystcraft.client.render.SymbolGlyphs;
import com.techbucketdivision.mystcraft.item.PageItem;
import com.techbucketdivision.mystcraft.symbol.SymbolRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The Writing Desk's symbol surface (world-building plan §4): a scrollable grid of the symbols the player may write,
 * grouped under category headers and filtered by a tab (a set of categories) and a search text. Clicking a primary
 * symbol writes a copy into the folder; clicking a modifier attaches it to the selected folder page.
 */
public class SymbolSurface extends GuiElement {
    public static final int PAGE_W = 30;
    public static final int PAGE_H = 40;
    public static final int HEADER_H = 11;
    public static final int SCROLLBAR_W = 20;

    /** Desk tabs: which categories each shows. */
    public enum Tab {
        ALL("all", EnumSet.allOf(SymbolCategory.class)),
        TERRAIN("terrain", EnumSet.of(SymbolCategory.TERRAIN)),
        BIOMES("biomes", EnumSet.of(SymbolCategory.BIOMES, SymbolCategory.BIOME_LAYOUT)),
        SKY("sky", EnumSet.of(SymbolCategory.LIGHTING, SymbolCategory.CELESTIALS, SymbolCategory.SKY_COLORS, SymbolCategory.WORLD_COLORS)),
        WEATHER("weather", EnumSet.of(SymbolCategory.WEATHER)),
        FEATURES("features", EnumSet.of(SymbolCategory.FEATURES, SymbolCategory.STRUCTURES)),
        MATERIALS("materials", EnumSet.of(SymbolCategory.MATERIALS)),
        EFFECTS("effects", EnumSet.of(SymbolCategory.EFFECTS)),
        CREATURES("creatures", EnumSet.of(SymbolCategory.CREATURES)),
        MODIFIERS("modifiers", EnumSet.of(SymbolCategory.MODIFIERS));

        public final String id;
        public final Set<SymbolCategory> categories;

        Tab(String id, Set<SymbolCategory> categories) {
            this.id = id;
            this.categories = categories;
        }

        public Component label() {
            return Component.translatable("gui.mystcraft.writing_desk.tab." + id);
        }

        public Component shortLabel() {
            return Component.translatable("gui.mystcraft.writing_desk.tab." + id + ".short");
        }
    }

    /** Screen-side actions. */
    public interface Handler {
        /** Click on a primary symbol. */
        void write(AgeSymbol symbol);

        /** Click on a modifier symbol. */
        void attach(AgeSymbol modifier);

        /** The folder page a modifier would attach to, or null when none is selected. */
        @Nullable ItemStack selectedPage();

        boolean canWrite();
    }

    /** One tile (a symbol) or one header (a category). */
    private static final class Entry {
        @Nullable AgeSymbol symbol;
        @Nullable SymbolCategory header;
        ItemStack stack = ItemStack.EMPTY;
        String name = "";
        float x, y;
    }

    private final Supplier<Set<Identifier>> known;
    private final Supplier<Boolean> showAll;
    private final Handler handler;

    private Tab tab = Tab.ALL;
    private String search = "";
    private @Nullable List<Entry> entries;
    private Set<Identifier> cachedKnown = Set.of();
    private boolean cachedShowAll;
    private int scroll, maxScroll;
    private boolean draggingScroll;
    private @Nullable Entry hover;
    private final List<Component> tooltip = new ArrayList<>();

    public SymbolSurface(int x, int y, int width, int height, Supplier<Set<Identifier>> known, Supplier<Boolean> showAll, Handler handler) {
        super(x, y, width, height);
        this.known = known;
        this.showAll = showAll;
        this.handler = handler;
    }

    // --- state -----------------------------------------------------------------------------------------------------

    public Tab tab() {
        return tab;
    }

    public void setTab(Tab tab) {
        this.tab = tab;
        scroll = 0;
        invalidate();
    }

    public void setSearch(String text) {
        this.search = text == null ? "" : text;
        invalidate();
    }

    public void invalidate() {
        entries = null;
    }

    /** Number of symbol tiles currently listed (test hook). */
    public int tileCount() {
        List<Entry> list = entries();
        if (list == null) return 0;
        int n = 0;
        for (Entry e : list) if (e.symbol != null) n++;
        return n;
    }

    private int gridWidth() {
        return width - SCROLLBAR_W;
    }

    // --- building ----------------------------------------------------------------------------------------------------

    private List<Entry> entries() {
        Set<Identifier> current = known.get();
        boolean all = showAll.get();
        if (entries == null || !current.equals(cachedKnown) || all != cachedShowAll) {
            cachedKnown = current;
            cachedShowAll = all;
            rebuild();
        }
        return entries;
    }

    private void rebuild() {
        hover = null;
        String filter = search.toLowerCase(Locale.ROOT);
        List<Entry> list = new ArrayList<>();
        int perRow = Math.max(1, gridWidth() / (PAGE_W + 1));
        float y = 0;
        for (SymbolCategory category : SymbolCategory.values()) {
            if (!tab.categories.contains(category)) continue;
            List<AgeSymbol> symbols = new ArrayList<>();
            for (AgeSymbol symbol : SymbolRegistry.inCategory(category)) {
                if (!cachedShowAll && !cachedKnown.contains(symbol.id())) continue;
                String name = symbol.displayName().getString();
                if (!filter.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(filter)) continue;
                symbols.add(symbol);
            }
            if (symbols.isEmpty()) continue;
            symbols.sort(Comparator.comparing(s -> s.displayName().getString().toLowerCase(Locale.ROOT)));
            Entry header = new Entry();
            header.header = category;
            header.y = y;
            list.add(header);
            y += HEADER_H;
            int col = 0;
            for (AgeSymbol symbol : symbols) {
                Entry e = new Entry();
                e.symbol = symbol;
                e.stack = PageItem.createSymbolPage(symbol);
                e.name = symbol.displayName().getString();
                e.x = col * (PAGE_W + 1);
                e.y = y;
                list.add(e);
                if (++col >= perRow) {
                    col = 0;
                    y += PAGE_H + 1;
                }
            }
            if (col != 0) y += PAGE_H + 1;
            y += 2;
        }
        entries = list;
    }

    // --- rendering -------------------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int gridW = gridWidth();
        g.fill(x, y, x + gridW, y + height, 0xAA000000);
        List<Entry> list = entries();
        boolean overGrid = contains(mouseX, mouseY, x, y, gridW, height);
        Entry newHover = null;
        maxScroll = 0;
        Font font = Minecraft.getInstance().font;
        ItemStack selected = handler.selectedPage();
        AgeSymbol selectedSymbol = selected == null ? null : PageItem.getSymbol(selected);
        g.enableScissor(x, y, x + gridW, y + height - 1);
        for (Entry e : list) {
            float ex = x + e.x;
            float ey = y + e.y - scroll;
            int h = e.header != null ? HEADER_H : PAGE_H;
            maxScroll = Math.max(maxScroll, (int) (e.y + h + 6 - height));
            if (ey + h < y || ey > y + height) continue;
            if (e.header != null) {
                g.text(font, e.header.displayName(), (int) ex + 2, (int) ey + 1, 0xFFE8D8A0, true);
                continue;
            }
            SymbolGlyphs.drawPage(g, e.stack, ex, ey, PAGE_W, PAGE_H);
            if (e.symbol != null && e.symbol.category().isModifier()) {
                // modifiers that fit the selected page glow; the rest are dimmed until a page is selected
                boolean fits = selectedSymbol != null && selectedSymbol.takes(e.symbol);
                if (selectedSymbol != null && !fits) g.fill((int) ex, (int) ey, (int) ex + PAGE_W, (int) ey + PAGE_H, 0x70000000);
                else if (fits) g.fill((int) ex, (int) ey, (int) ex + PAGE_W, (int) ey + PAGE_H, 0x30E0B040);
            }
            if (overGrid && contains(mouseX, mouseY, (int) ex, (int) ey, PAGE_W, PAGE_H)) newHover = e;
        }
        g.disableScissor();
        maxScroll = Math.max(0, maxScroll);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        renderScrollbar(g);

        if (newHover != hover) {
            hover = newHover;
            tooltip.clear();
            if (hover != null && hover.symbol != null) {
                tooltip.addAll(Screen.getTooltipFromItem(Minecraft.getInstance(), hover.stack));
                tooltip.addAll(schemaLines(hover.symbol));
            }
        }
    }

    /** "Takes: colour, gradient" for primaries, "Attaches to a page that takes: material (terrain, sea)" for modifiers. */
    public static List<Component> schemaLines(AgeSymbol symbol) {
        List<Component> out = new ArrayList<>();
        out.add(symbol.category().displayName().copy().withStyle(ChatFormatting.GOLD));
        ModifierSlot fills = symbol.fills();
        if (fills != null) {
            String slot = fills.displayName().getString();
            if (fills == ModifierSlot.BLOCK && !symbol.blockCategories().isEmpty()) {
                slot += " (" + String.join(", ", symbol.blockCategories().stream().map(c -> c.name()).sorted().toList()) + ")";
            }
            out.add(Component.translatable("gui.mystcraft.writing_desk.symbol.attaches", slot).withStyle(ChatFormatting.BLUE));
        } else if (!symbol.accepts().isEmpty()) {
            List<String> slots = new ArrayList<>();
            for (ModifierSlot slot : ModifierSlot.values()) {
                if (!symbol.accepts().contains(slot)) continue;
                String name = slot.displayName().getString();
                if (slot == ModifierSlot.BLOCK && !symbol.blockCategories().isEmpty()) {
                    name += " (" + String.join(", ", symbol.blockCategories().stream().map(c -> c.name()).sorted().toList()) + ")";
                }
                slots.add(name);
            }
            out.add(Component.translatable("gui.mystcraft.writing_desk.symbol.takes", String.join(", ", slots)).withStyle(ChatFormatting.BLUE));
        }
        return out;
    }

    private void renderScrollbar(GuiGraphicsExtractor g) {
        int sx = x + gridWidth();
        g.fill(sx, y, sx + SCROLLBAR_W, y + height, 0xFF202020);
        int thumbH = maxScroll <= 0 ? height - 2 : Math.max(10, (int) ((height - 2) * (float) height / (height + maxScroll)));
        int thumbY = maxScroll <= 0 ? y + 1 : y + 1 + (int) ((height - 2 - thumbH) * (scroll / (float) maxScroll));
        g.fill(sx + 2, thumbY, sx + SCROLLBAR_W - 2, thumbY + thumbH, maxScroll <= 0 ? 0xFF505050 : 0xFFA0A0A0);
    }

    // --- input -----------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isEnabled() || !contains(mx, my)) return false;
        if (mx >= x + gridWidth()) {
            draggingScroll = button == 0;
            scrollTo(my);
            return true;
        }
        if (hover == null || hover.symbol == null || button != 0) return hover != null;
        if (hover.symbol.category().isModifier()) handler.attach(hover.symbol);
        else handler.write(hover.symbol);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingScroll = false;
        return false;
    }

    public void mouseDragged(double mx, double my) {
        if (draggingScroll) scrollTo(my);
    }

    private void scrollTo(double my) {
        if (maxScroll <= 0) return;
        float f = (float) ((my - y - 5) / (height - 10));
        scroll = Mth.clamp((int) (f * maxScroll), 0, maxScroll);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollY) {
        if (!isEnabled() || !contains(mx, my)) return false;
        scroll = Mth.clamp(scroll - (int) (scrollY * 12), 0, maxScroll);
        return true;
    }

    @Override
    public @Nullable List<Component> tooltip() {
        if (hover == null || hover.symbol == null || tooltip.isEmpty()) return null;
        List<Component> out = new ArrayList<>(tooltip);
        ChatFormatting grey = ChatFormatting.GRAY;
        if (hover.symbol.category().isModifier()) {
            ItemStack selected = handler.selectedPage();
            AgeSymbol target = selected == null ? null : PageItem.getSymbol(selected);
            if (target == null) out.add(Component.translatable("gui.mystcraft.writing_desk.page.select_first").withStyle(grey));
            else if (!target.takes(hover.symbol)) out.add(Component.translatable("gui.mystcraft.writing_desk.page.not_taken", target.displayName()).withStyle(ChatFormatting.RED));
            else out.add(Component.translatable("gui.mystcraft.writing_desk.page.attach", target.displayName()).withStyle(grey));
        } else if (handler.canWrite()) {
            out.add(Component.translatable("gui.mystcraft.writing_desk.page.write").withStyle(grey));
        } else {
            out.add(Component.translatable("gui.mystcraft.writing_desk.page.cannot_write").withStyle(ChatFormatting.DARK_GRAY));
        }
        return out;
    }
}
