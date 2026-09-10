package com.techbucketdivision.mystcraft.client.screen;

import com.techbucketdivision.mystcraft.api.linking.LinkProperty;
import com.techbucketdivision.mystcraft.client.screen.gui.ToggleButton;
import com.techbucketdivision.mystcraft.linking.InkEffects;
import com.techbucketdivision.mystcraft.menu.LinkModifierMenu;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/** Link Modifier screen (REQUIREMENTS §8.4): flag toggles, seed / name fields, kill + confirm buttons. */
public class LinkModifierScreen extends AbstractMystcraftScreen<LinkModifierMenu> {
    private static final Identifier SINGLE_SLOT = MystIds.id("textures/gui/single_slot.png");

    private @Nullable EditBox seedBox;
    private @Nullable EditBox nameBox;
    private @Nullable ToggleButton armButton;
    private @Nullable ToggleButton confirmButton;
    private boolean armed;
    private boolean syncing;

    public LinkModifierScreen(LinkModifierMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    protected void buildElements() {
        int gx = leftPos, gy = topPos;
        seedBox = addEditBox(new EditBox(font, gx + 80, gy + 15, imageWidth - 80 - 9, 14, Component.translatable("gui.mystcraft.seed")));
        seedBox.setMaxLength(21);
        seedBox.setResponder(text -> {
            if (syncing) return;
            CompoundTag tag = new CompoundTag();
            tag.putString("Seed", text);
            send(LinkModifierMenu.MSG_SET_SEED, tag);
        });
        nameBox = addEditBox(new EditBox(font, gx + 80, gy + 56, imageWidth - 80 - 9, 14, Component.translatable("gui.mystcraft.item_name")));
        nameBox.setMaxLength(LinkModifierMenu.MAX_TITLE);
        nameBox.setResponder(text -> {
            if (syncing) return;
            CompoundTag tag = new CompoundTag();
            tag.putString("Title", text);
            send(LinkModifierMenu.MSG_SET_TITLE, tag);
        });

        int x = 5, y = 10, size = 18;
        for (LinkProperty property : InkEffects.getProperties()) {
            addElement(new ToggleButton(gx + x, gy + y, size, null, () -> menu.getLinkFlag(property), () -> toggleFlag(property))
                    .tooltip(Component.translatable(property.descriptionId()))
                    .color(propertyColor(property)));
            y += size + 2;
            if (y >= 60) {
                y = 10;
                x += size + 2;
            }
        }
        confirmButton = addElement(new ToggleButton(gx + 140, gy + 32, size, "!", menu::isLinkDead, () -> {
            sendOnly(LinkModifierMenu.MSG_RECYCLE_DIM);
            armed = false;
        }).tooltip(Component.translatable("gui.mystcraft.link_modifier.confirm_kill")).color(0xFFFF4040));
        armButton = addElement(new ToggleButton(gx + 120, gy + 32, size, "X", () -> false, () -> armed = true)
                .tooltip(Component.translatable("gui.mystcraft.link_modifier.kill")));
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
        boolean hasSeed = menu.hasItemSeed();
        boolean dead = menu.isLinkDead();
        if (menu.getBook().isEmpty()) armed = false;
        if (seedBox != null) {
            seedBox.setVisible(hasSeed);
            if ((force || !seedBox.isFocused()) && !seedBox.getValue().equals(menu.getItemSeed())) {
                syncing = true;
                seedBox.setValue(menu.getItemSeed());
                syncing = false;
            }
        }
        if (nameBox != null) {
            nameBox.setEditable(!menu.getBook().isEmpty());
            if ((force || !nameBox.isFocused()) && !nameBox.getValue().equals(menu.getBookTitle())) {
                syncing = true;
                nameBox.setValue(menu.getBookTitle());
                syncing = false;
            }
        }
        if (armButton != null) armButton.setVisible(hasSeed && !armed && !dead);
        if (confirmButton != null) confirmButton.setVisible(hasSeed && (armed || dead));
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.blit(RenderPipelines.GUI_TEXTURED, SINGLE_SLOT, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        g.text(font, menu.getLinkDimensionId(), leftPos + 100, topPos + 40, 0xFFFFFFFF, true);
    }
}
