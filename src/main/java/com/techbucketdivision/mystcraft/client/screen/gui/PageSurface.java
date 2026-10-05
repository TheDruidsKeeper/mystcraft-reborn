package com.techbucketdivision.mystcraft.client.screen.gui;

import com.techbucketdivision.mystcraft.api.item.ItemBehaviours;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.client.render.SymbolGlyphs;
import com.techbucketdivision.mystcraft.item.PageItem;
import com.techbucketdivision.mystcraft.symbol.SymbolRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Scrollable grid of 30×40 page tiles showing the pages of an ordered page provider (folder: one tile per slot),
 * filtered by a search text. Port of {@code GuiElementPageSurface} (original spec §8.6).
 */
public class PageSurface extends GuiElement {
    public static final int PAGE_W = 30;
    public static final int PAGE_H = 40;
    public static final int SCROLLBAR_W = 20;

    /** Screen-side actions. */
    public interface Handler {
        /** Place the cursor stack at {@code index}. */
        void place(int index, boolean single);

        /** Pick up a page (left click, or shift + left click when {@link #writesOnClick()}). */
        void pickup(Entry entry);

        /** Copy a symbol page (plain left click when {@link #writesOnClick()}, or right click + release) — writing desk only. */
        default void copy(Entry entry) {}

        /**
         * Whether a plain left click should {@link #copy} instead of {@link #pickup}. The writing desk answers true
         * while it has ink and something to write on, so pages are only pulled out of a notebook on purpose (shift).
         */
        default boolean writesOnClick() {
            return false;
        }

        /** Extra tooltip lines under a hovered page explaining what the mouse buttons do here. */
        default List<Component> actionHints() {
            return List.of();
        }
    }

    public static final class Entry {
        public int slotId;
        public ItemStack stack = ItemStack.EMPTY;
        public int count = 1;
        public float x, y;
        public @Nullable String name;
    }

    private final Supplier<ItemStack> source;
    private final Handler handler;
    private final Supplier<Player> player;

    private ItemStack cached = ItemStack.EMPTY;
    private @Nullable List<Entry> entries;
    private String search = "";

    private int scroll;
    private int maxScroll;
    private boolean draggingScroll;
    private @Nullable Entry hover;
    private boolean rightDown;
    private final List<Component> tooltip = new ArrayList<>();

    public PageSurface(int x, int y, int width, int height, Supplier<ItemStack> source, Supplier<Player> player, Handler handler) {
        super(x, y, width, height);
        this.source = source;
        this.player = player;
        this.handler = handler;
    }

    // --- state -----------------------------------------------------------------------------------------------------

    public void setSearch(String text) {
        this.search = text == null ? "" : text;
        invalidate();
    }

    public void invalidate() {
        cached = ItemStack.EMPTY;
        entries = null;
    }

    private int gridWidth() {
        return width - SCROLLBAR_W;
    }

    // --- collection building ---------------------------------------------------------------------------------------

    private @Nullable List<Entry> entries() {
        ItemStack stack = source.get();
        if (!ItemStack.matches(stack, cached)) {
            cached = stack.copy();
            rebuild();
        }
        return entries;
    }

    private void rebuild() {
        entries = null;
        hover = null;
        if (cached.isEmpty() || !(cached.getItem() instanceof ItemBehaviours.PageProvider provider)) return;
        List<Entry> list = new ArrayList<>();
        int i = 0;
        for (ItemStack page : provider.getPageList(player.get(), cached)) {
            Entry e = new Entry();
            e.stack = page;
            e.slotId = i++;
            e.count = 1;
            e.name = symbolName(page);
            list.add(e);
        }
        float xStep = PAGE_W + 1, yStep = PAGE_H + 1;
        float px = 0, py = 0;
        for (Entry e : list) {
            e.x = px;
            e.y = py;
            px += xStep;
            if (px + xStep > gridWidth()) {
                px = 0;
                py += yStep;
            }
        }
        entries = list;
    }

    private static @Nullable String symbolName(ItemStack page) {
        Identifier id = PageItem.getSymbolId(page);
        if (id == null) return null;
        AgeSymbol symbol = SymbolRegistry.get(id);
        return symbol != null ? symbol.displayName().getString() : id.getPath();
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
        g.enableScissor(x, y, x + gridW, y + height - 1);
        if (list != null) {
            for (Entry e : list) {
                float ex = x + e.x;
                float ey = y + e.y - scroll;
                maxScroll = Math.max(maxScroll, (int) (e.y + PAGE_H + 6 - height));
                if (ey + PAGE_H < y || ey > y + height) continue;
                boolean filteredOut = e.name != null && !search.isEmpty() && !e.name.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));
                ItemStack shown = e.count > 0 && !filteredOut ? e.stack : ItemStack.EMPTY;
                SymbolGlyphs.drawPage(g, shown, ex, ey, PAGE_W, PAGE_H);
                if (e.count > 1) {
                    g.text(Minecraft.getInstance().font, Integer.toString(e.count), (int) ex + 1, (int) (ey + PAGE_H - 9), 0xFFFFFFFF, true);
                }
                if (overGrid && contains(mouseX, mouseY, (int) ex, (int) ey, PAGE_W, PAGE_H)) newHover = e;
            }
        }
        g.disableScissor();
        maxScroll = Math.max(0, maxScroll);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        renderScrollbar(g);

        if (newHover != hover) {
            hover = newHover;
            tooltip.clear();
            if (hover != null && !hover.stack.isEmpty()) {
                tooltip.addAll(Screen.getTooltipFromItem(Minecraft.getInstance(), hover.stack));
            }
        }
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
        List<Entry> list = entries();
        if (list == null) return false;
        ItemStack carried = player.get().containerMenu.getCarried();
        if (!carried.isEmpty()) {
            int index = hover != null ? hover.slotId : list.size();
            handler.place(index, button == 1);
            return true;
        }
        if (hover != null && button == 0) {
            if (hover.count > 0) {
                if (!GuiElement.isShiftHeld() && handler.writesOnClick()) handler.copy(hover);
                else handler.pickup(hover);
            }
            return true;
        }
        if (hover != null && button == 1) {
            rightDown = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingScroll = false;
        if (button == 1 && rightDown && hover != null && hover.count > 0 && contains(mx, my)) {
            handler.copy(hover);
        }
        rightDown = false;
        return false;
    }

    /** Mouse drag on the scrollbar (called by the screen from mouseDragged if available; polled here otherwise). */
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
        if (hover == null || tooltip.isEmpty()) return null;
        List<Component> hints = hover.count > 0 ? handler.actionHints() : List.of();
        if (hints.isEmpty()) return tooltip;
        List<Component> out = new ArrayList<>(tooltip);
        out.addAll(hints);
        return out;
    }
}
