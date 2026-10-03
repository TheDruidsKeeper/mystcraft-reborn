package com.techbucketdivision.mystcraft.client.screen;

import com.techbucketdivision.mystcraft.api.linking.LinkProperty;
import com.techbucketdivision.mystcraft.client.screen.gui.CheckBoxRow;
import com.techbucketdivision.mystcraft.client.screen.gui.ToggleButton;
import com.techbucketdivision.mystcraft.linking.InkEffects;
import com.techbucketdivision.mystcraft.menu.LinkModifierMenu;
import com.techbucketdivision.mystcraft.registry.ModItems;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Link Modifier screen (REQUIREMENTS §8.4). Left: the book slot with the title and seed fields below it. Right: a
 * side panel with a labelled check list of the link effects (each with a tooltip explaining what it does) and the
 * "mark Age dead" action with an explicit confirm step. Everything is disabled until a book is in the slot.
 */
public class LinkModifierScreen extends AbstractMystcraftScreen<LinkModifierMenu> {
    private static final Identifier SINGLE_SLOT = MystIds.id("textures/gui/single_slot.png");
    private static final int BASE_W = 176, BASE_H = 166;
    private static final int PANEL_X = 173, PANEL_W = 118; // side panel extends the base window to the right
    private static final int ROW_H = 12;

    private @Nullable EditBox seedBox;
    private @Nullable EditBox nameBox;
    private @Nullable ToggleButton armButton;
    private @Nullable ToggleButton confirmButton;
    private @Nullable ToggleButton cancelButton;
    private final List<CheckBoxRow> flagRows = new ArrayList<>();
    private boolean armed;
    private boolean syncing;

    public LinkModifierScreen(LinkModifierMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_X + PANEL_W, BASE_H);
    }

    @Override
    protected void buildElements() {
        int gx = leftPos, gy = topPos;
        flagRows.clear();

        // --- left: book slot (80,35 from the texture), title + seed below it
        hintSlot(LinkModifierMenu.SLOT_BOOK, ModItems.LINKING_BOOK.get(), "gui.mystcraft.link_modifier.slot.book");
        nameBox = addEditBox(new EditBox(font, gx + 40, gy + 17, 128, 12, Component.translatable("gui.mystcraft.link_modifier.item_name")));
        nameBox.setMaxLength(LinkModifierMenu.MAX_TITLE);
        nameBox.setHint(Component.translatable("gui.mystcraft.link_modifier.item_name.hint"));
        nameBox.setResponder(text -> {
            if (syncing) return;
            CompoundTag tag = new CompoundTag();
            tag.putString("Title", text);
            send(LinkModifierMenu.MSG_SET_TITLE, tag);
        });
        seedBox = addEditBox(new EditBox(font, gx + 40, gy + 55, 128, 12, Component.translatable("gui.mystcraft.link_modifier.seed")));
        seedBox.setMaxLength(21);
        seedBox.setHint(Component.translatable("gui.mystcraft.link_modifier.seed.hint"));
        seedBox.setResponder(text -> {
            if (syncing) return;
            CompoundTag tag = new CompoundTag();
            tag.putString("Seed", text);
            send(LinkModifierMenu.MSG_SET_SEED, tag);
        });

        // --- right panel: link effects check list
        int px = gx + PANEL_X + 7, py = gy + 18;
        for (LinkProperty property : InkEffects.getProperties()) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable(property.descriptionId()));
            tip.add(Component.translatable(property.descriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
            flagRows.add(addElement(new CheckBoxRow(px, py, PANEL_W - 12, Component.translatable(property.descriptionId()),
                    propertyColor(property), () -> menu.getLinkFlag(property), () -> toggleFlag(property)).tooltip(tip)));
            py += ROW_H;
        }

        // --- right panel: Age controls (only for descriptive books)
        int by = gy + 118;
        armButton = addElement(new ToggleButton(px, by, PANEL_W - 14, 14, null, () -> false, () -> armed = true)
                .label(Component.translatable("gui.mystcraft.link_modifier.kill"))
                .tooltip(List.of(Component.translatable("gui.mystcraft.link_modifier.kill.tooltip"),
                        Component.translatable("gui.mystcraft.link_modifier.kill.tooltip2").withStyle(ChatFormatting.RED))));
        confirmButton = addElement(new ToggleButton(px, by, PANEL_W - 14, 14, null, menu::isLinkDead, () -> {
            sendOnly(LinkModifierMenu.MSG_RECYCLE_DIM);
            armed = false;
        }).label(Component.translatable("gui.mystcraft.link_modifier.confirm_kill")).color(0xFFFF6060)
                .tooltip(Component.translatable("gui.mystcraft.link_modifier.confirm_kill.tooltip")));
        cancelButton = addElement(new ToggleButton(px, by + 16, PANEL_W - 14, 14, null, () -> false, () -> armed = false)
                .label(Component.translatable("gui.cancel")));
        sync(true);
    }

    private static int propertyColor(LinkProperty property) {
        var c = property.color();
        return c == null ? 0xFFFFFFFF : 0xFF000000 | (c.clamp().toRGB() & 0xFFFFFF);
    }

    private void toggleFlag(LinkProperty property) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Flag", property.name());
        tag.putBoolean("Value", !menu.getLinkFlag(property));
        send(LinkModifierMenu.MSG_SET_FLAG, tag);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        sync(false);
    }

    private void sync(boolean force) {
        boolean hasBook = !menu.getBook().isEmpty();
        boolean hasSeed = menu.hasItemSeed();
        boolean dead = menu.isLinkDead();
        if (!hasBook) armed = false;
        for (CheckBoxRow row : flagRows) row.setEnabled(hasBook);
        if (seedBox != null) {
            seedBox.setEditable(hasSeed);
            seedBox.setVisible(true);
            if ((force || !seedBox.isFocused()) && !seedBox.getValue().equals(menu.getItemSeed())) {
                syncing = true;
                seedBox.setValue(menu.getItemSeed());
                syncing = false;
            }
        }
        if (nameBox != null) {
            nameBox.setEditable(hasBook);
            if ((force || !nameBox.isFocused()) && !nameBox.getValue().equals(menu.getBookTitle())) {
                syncing = true;
                nameBox.setValue(menu.getBookTitle());
                syncing = false;
            }
        }
        if (armButton != null) armButton.setVisible(hasSeed && !armed && !dead);
        if (confirmButton != null) confirmButton.setVisible(hasSeed && (armed || dead));
        if (cancelButton != null) cancelButton.setVisible(hasSeed && armed && !dead);
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.blit(RenderPipelines.GUI_TEXTURED, SINGLE_SLOT, leftPos, topPos, 0, 0, BASE_W, BASE_H, 256, 256);
        drawPanel(g, leftPos + PANEL_X, topPos, PANEL_W, BASE_H);
        caption(g, title, leftPos + 8, topPos + 6);
        caption(g, Component.translatable("container.inventory"), leftPos + 8, topPos + BASE_H - 96 + 2);
        caption(g, "gui.mystcraft.link_modifier.item_name", leftPos + 8, topPos + 19);
        caption(g, "gui.mystcraft.link_modifier.book", leftPos + 100, topPos + 39);
        caption(g, "gui.mystcraft.link_modifier.seed", leftPos + 8, topPos + 57);

        int px = leftPos + PANEL_X + 7;
        caption(g, "gui.mystcraft.link_modifier.effects", px, topPos + 6);
        caption(g, "gui.mystcraft.link_modifier.age", px, topPos + 96);
        boolean hasBook = !menu.getBook().isEmpty();
        Component target = hasBook
                ? Component.literal(shorten(menu.getLinkDimensionId(), PANEL_W - 14))
                : Component.translatable("gui.mystcraft.link_modifier.no_book");
        g.text(font, target, px, topPos + 106, hasBook ? 0xFF202020 : 0xFF8A8A8A, false);
        if (menu.isLinkDead()) {
            g.text(font, Component.translatable("gui.mystcraft.link_modifier.dead"), px, topPos + 136, 0xFFAA0000, false);
        } else if (hasBook && !menu.hasItemSeed()) {
            g.text(font, Component.translatable("gui.mystcraft.link_modifier.linking_book_note"), px, topPos + 120, 0xFF8A8A8A, false);
        }
    }

    private String shorten(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String t = text.startsWith("mystcraft:") ? text.substring("mystcraft:".length()) : text;
        while (!t.isEmpty() && font.width(t + "...") > maxWidth) t = t.substring(0, t.length() - 1);
        return t + "...";
    }

    /** A vanilla-styled bevelled panel (matches the container texture colours). */
    static void drawPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
        g.fill(x + 1, y + 1, x + w - 2, y + 3, 0xFFFFFFFF);     // top highlight
        g.fill(x + 1, y + 1, x + 3, y + h - 2, 0xFFFFFFFF);     // left highlight
        g.fill(x + 3, y + h - 3, x + w - 1, y + h - 1, 0xFF555555); // bottom shadow
        g.fill(x + w - 3, y + 3, x + w - 1, y + h - 1, 0xFF555555); // right shadow
    }
}
