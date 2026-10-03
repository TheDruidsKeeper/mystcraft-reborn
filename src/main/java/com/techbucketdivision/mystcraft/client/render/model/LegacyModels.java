package com.techbucketdivision.mystcraft.client.render.model;

import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.sprite.SpriteId;

/**
 * The original Mystcraft block-entity models (Techne exports in {@code ModelBookstand}, {@code ModelLectern},
 * {@code ModelWritingDesk}) ported to {@link LayerDefinition}s. Coordinates are the original model pixels; the
 * renderers replay the original GL transforms, so these must not be "fixed" to look upright on their own.
 * Textures live in the block atlas ({@code textures/entity/*}, listed in {@code atlases/blocks.json}).
 */
public final class LegacyModels {
    private LegacyModels() {}

    public static final ModelLayerLocation BOOKSTAND = new ModelLayerLocation(MystIds.id("bookstand"), "main");
    public static final ModelLayerLocation LECTERN = new ModelLayerLocation(MystIds.id("lectern"), "main");
    public static final ModelLayerLocation WRITING_DESK = new ModelLayerLocation(MystIds.id("writing_desk"), "main");

    public static final SpriteId BOOKSTAND_TEXTURE = Sheets.BLOCK_ENTITIES_MAPPER.apply(MystIds.id("bookstand"));
    public static final SpriteId LECTERN_TEXTURE = Sheets.BLOCK_ENTITIES_MAPPER.apply(MystIds.id("lectern"));
    public static final SpriteId DESK_TEXTURE = Sheets.BLOCK_ENTITIES_MAPPER.apply(MystIds.id("desk"));
    /** Open-book covers for the vanilla {@code BookModel} (legacy agebook / linkbook textures, 64x32). */
    public static final SpriteId AGEBOOK_TEXTURE = Sheets.BLOCK_ENTITIES_MAPPER.apply(MystIds.id("agebook"));
    public static final SpriteId LINKBOOK_TEXTURE = Sheets.BLOCK_ENTITIES_MAPPER.apply(MystIds.id("linkbook"));
    /** Writing desk extras: notebook spines in the backboard shelf and the inkwell on the desk top. */
    public static final SpriteId BOOK_SPINE_TEXTURE = Sheets.BLOCK_ENTITIES_MAPPER.apply(MystIds.id("book_spine"));
    public static final SpriteId INKWELL_CUP_TEXTURE = Sheets.BLOCK_ENTITIES_MAPPER.apply(MystIds.id("inkwell/inkwell_cup"));
    public static final SpriteId INKWELL_INK_TEXTURE = Sheets.BLOCK_ENTITIES_MAPPER.apply(MystIds.id("inkwell/ink"));

    /** Desk parts only drawn when the desk has its backboard ({@code hasTop} in the original). */
    public static final String[] DESK_BACKING = {"deskTopBack", "deskTopLeft", "deskTopRight", "deskTopTop", "angleLeft", "angleRight",
            "cupboardLeft", "cupboardRight"};

    /** Ported from the original ModelBookstand (Techne export); coordinates in model pixels. */
    public static LayerDefinition bookstand() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("leftarm", CubeListBuilder.create().texOffs(4, 8).addBox(-0.5F, -0.5F, -1.5F, 6F, 1F, 3F),
                PartPose.offsetAndRotation(0.25F, 0.0F, -0.25F, 0.5235988F, 0.0F, -0.2617994F));
        root.addOrReplaceChild("post", CubeListBuilder.create().texOffs(0, 8).addBox(-0.5F, 0.0F, -0.5F, 1F, 6F, 1F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, 0.0F, -2.5F, 5F, 3F, 5F),
                PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("rightarm", CubeListBuilder.create().texOffs(4, 8).addBox(-0.5F, -0.5F, -1.5F, 6F, 1F, 3F),
                PartPose.offsetAndRotation(-0.25F, 0.0F, -0.25F, -0.5235988F, 3.141593F, 0.2617994F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** Lectern slope angle (the wedge rises 6 px over 16 px), used to lay the displayed item on the slope. */
    public static final float LECTERN_SLOPE_DEGREES = (float) Math.toDegrees(Math.atan2(6.0, 16.0));
    /** Height of the lectern wedge at its centre (model pixels / 16). */
    public static final float LECTERN_SURFACE_CENTER = 4f / 16f;

    /**
     * Ported from the original ModelLectern: one solid wedge ({@code ModelPrism}: 16 wide, 1 px tall at x=-8 rising
     * to 7 px at x=+8, 16 deep) plus a 1x2x14 ledge along the low edge, rotation point y=0.5 so the wedge sits on
     * the block's bottom face. Built directly (no {@link LayerDefinition}) because the mesh builders only make boxes.
     */
    public static Model.Simple lecternModel() {
        List<ModelPart.Cube> cubes = List.of(
                new PrismCube(0, 0, -8f, 0f, -8f, 16, 1, 7, 16, 64f, 32f),
                new ModelPart.Cube(32, 2, -8f, 0f, -7f, 1f, 2f, 14f, 0f, 0f, 0f, false, 64f, 32f, EnumSet.allOf(Direction.class)));
        ModelPart root = new ModelPart(cubes, Map.of());
        return new Model.Simple(root, RenderTypes::entityCutout);
    }

    /** Ported from the original ModelWritingDesk (Techne export); books omitted (never drawn in the original). */
    public static LayerDefinition writingDesk() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("bottomShelf", CubeListBuilder.create().texOffs(0, 34).addBox(0.0F, 0.0F, 0.0F, 14F, 1F, 15F),
                PartPose.offsetAndRotation(-7.0F, 23.0F, -8.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("middleShelf", CubeListBuilder.create().texOffs(0, 17).addBox(0.0F, 0.0F, 0.0F, 30F, 2F, 15F),
                PartPose.offsetAndRotation(-7.0F, 15.0F, -8.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("deskTop", CubeListBuilder.create().texOffs(0, 0).addBox(0.0F, 0.0F, 0.0F, 32F, 1F, 16F),
                PartPose.offsetAndRotation(-8.0F, 8.0F, -8.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("deskMiddle", CubeListBuilder.create().texOffs(94, 36).addBox(0.0F, 0.0F, 0.0F, 2F, 6F, 15F),
                PartPose.offsetAndRotation(7.0F, 9.0F, -8.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("deskLeft", CubeListBuilder.create().texOffs(90, 1).addBox(0.0F, 0.0F, 0.0F, 1F, 15F, 16F),
                PartPose.offsetAndRotation(-8.0F, 9.0F, -8.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("deskRight", CubeListBuilder.create().texOffs(90, 1).addBox(0.0F, 0.0F, 0.0F, 1F, 15F, 16F),
                PartPose.offsetAndRotation(23.0F, 9.0F, -8.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("deskBack", CubeListBuilder.create().texOffs(128, 0).addBox(0.0F, 0.0F, 0.0F, 30F, 15F, 1F),
                PartPose.offsetAndRotation(-7.0F, 9.0F, 7.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("deskMiddleBottom", CubeListBuilder.create().texOffs(77, 42).addBox(0.0F, 0.0F, 0.0F, 1F, 7F, 15F),
                PartPose.offsetAndRotation(7.0F, 17.0F, -8.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("paper1", CubeListBuilder.create().texOffs(0, 60).addBox(-2.0F, 0.0F, 0.0F, 7F, 0F, 5F),
                PartPose.offsetAndRotation(16.5F, 14.8F, -6.3F, 0.0F, -0.1047198F, 0.0F));
        root.addOrReplaceChild("paper2", CubeListBuilder.create().texOffs(0, 60).addBox(-2.0F, 0.0F, 0.0F, 7F, 0F, 5F),
                PartPose.offsetAndRotation(16.5F, 14.8F, -4.9F, 0.0F, 0.2094395F, 0.0F));
        root.addOrReplaceChild("paper3", CubeListBuilder.create().texOffs(0, 60).addBox(-2.0F, 0.0F, 0.0F, 7F, 0F, 5F),
                PartPose.offsetAndRotation(16.5F, 14.8F, -5.9F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("paperStack1", CubeListBuilder.create().texOffs(0, 60).addBox(-2.0F, 0.0F, 0.0F, 7F, 1F, 5F),
                PartPose.offsetAndRotation(16.5F, 13.0F, -5.0F, 0.0F, -0.0174533F, 0.0F));
        root.addOrReplaceChild("paperStack2", CubeListBuilder.create().texOffs(0, 60).addBox(0.0F, 0.0F, 0.0F, 7F, 1F, 5F),
                PartPose.offsetAndRotation(14.5F, 14.0F, -5.0F, 0.0F, 0.0698132F, 0.0F));
        root.addOrReplaceChild("deskTopBack", CubeListBuilder.create().texOffs(128, 16).addBox(0.0F, 0.0F, 0.0F, 32F, 12F, 1F),
                PartPose.offsetAndRotation(-8.0F, -4.0F, 7.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("deskTopTop", CubeListBuilder.create().texOffs(128, 29).addBox(0.0F, 0.0F, 0.0F, 30F, 1F, 6F),
                PartPose.offsetAndRotation(-7.0F, -4.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("deskTopLeft", CubeListBuilder.create().texOffs(146, 40).addBox(0.0F, 0.0F, 0.0F, 1F, 12F, 6F),
                PartPose.offsetAndRotation(-8.0F, -4.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("deskTopRight", CubeListBuilder.create().texOffs(146, 40).addBox(0.0F, 0.0F, 0.0F, 1F, 12F, 6F),
                PartPose.offsetAndRotation(23.0F, -4.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("angleLeft", CubeListBuilder.create().texOffs(128, 40).addBox(0.0F, 0.0F, 0.0F, 1F, 15F, 8F),
                PartPose.offsetAndRotation(-7.99F, -4.0F, 1.0F, -0.6457718F, 0.0F, 0.0F));
        root.addOrReplaceChild("angleRight", CubeListBuilder.create().texOffs(128, 40).addBox(0.0F, 0.0F, 0.0F, 1F, 15F, 8F),
                PartPose.offsetAndRotation(22.99F, -4.0F, 1.0F, -0.6457718F, 0.0F, 0.0F));
        root.addOrReplaceChild("cupboardRight", CubeListBuilder.create().texOffs(182, 40).addBox(0.0F, 0.0F, 0.0F, 7F, 11F, 4F),
                PartPose.offsetAndRotation(16.0F, -3.0F, 3.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("cupboardLeft", CubeListBuilder.create().texOffs(160, 40).addBox(0.0F, 0.0F, 0.0F, 7F, 11F, 4F),
                PartPose.offsetAndRotation(-7.0F, -3.0F, 3.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 256, 128);
    }

    /** A standing notebook for the desk's backboard shelf: 5 deep (x), 10 tall, 2 wide (z) in 1/16 block units, tinted per kind. */
    public static Model.Simple deskShelfBook() {
        List<ModelPart.Cube> cubes = List.of(
                new ModelPart.Cube(0, 0, 0f, 0f, 0f, 5f, 10f, 2f, 0f, 0f, 0f, false, 16f, 16f, EnumSet.allOf(Direction.class)));
        return new Model.Simple(new ModelPart(cubes, Map.of()), RenderTypes::entityCutout);
    }

    /** The inkwell cup on the desk top: a 4x4x4 cube (legacy inkwell_cup texture). */
    public static Model.Simple inkwellCup() {
        List<ModelPart.Cube> cubes = List.of(
                new ModelPart.Cube(0, 0, -2f, 0f, -2f, 4f, 4f, 4f, 0f, 0f, 0f, false, 16f, 16f, EnumSet.allOf(Direction.class)));
        return new Model.Simple(new ModelPart(cubes, Map.of()), RenderTypes::entityCutout);
    }

    /** The ink inside the cup, {@code height} units tall (1..4), translucent (legacy ink texture). */
    public static Model.Simple inkwellInk(int height) {
        float h = Math.max(1, Math.min(4, height)) - 0.5f;
        List<ModelPart.Cube> cubes = List.of(
                new ModelPart.Cube(0, 0, -1.5f, 0.5f, -1.5f, 3f, h, 3f, 0f, 0f, 0f, false, 16f, 16f, EnumSet.allOf(Direction.class)));
        return new Model.Simple(new ModelPart(cubes, Map.of()), RenderTypes::entityTranslucent);
    }
}
