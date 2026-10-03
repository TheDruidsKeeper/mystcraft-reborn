package com.techbucketdivision.mystcraft.client.render.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.joml.Vector3f;

import java.util.EnumSet;

/**
 * A wedge: a box whose top rises from {@code height1} at {@code x} to {@code height2} at {@code x + width}. Port of
 * the original Mystcraft {@code ModelPrism} (used by the lectern body) including its texture layout, which is laid
 * out like a box of height {@code height2}: bottom at (tx, ty), top at (tx+w, ty), x+ side at (tx+2w, ty),
 * x- side right after it, z- / z+ below the bottom / top. The sloped top gets a true sloped normal so it is lit
 * like a slope rather than a flat lid.
 */
public final class PrismCube extends ModelPart.Cube {

    public PrismCube(int texX, int texY, float x, float y, float z, int width, int height1, int height2, int depth,
                     float texW, float texH) {
        super(texX, texY, x, y, z, width, height2, depth, 0f, 0f, 0f, false, texW, texH, EnumSet.allOf(Direction.class));
        float x2 = x + width;
        float y2a = y + height1;
        float y2b = y + height2;
        float z2 = z + depth;
        ModelPart.Vertex p1 = new ModelPart.Vertex(x, y, z, 0, 0);
        ModelPart.Vertex p2 = new ModelPart.Vertex(x2, y, z, 0, 8);
        ModelPart.Vertex p3 = new ModelPart.Vertex(x2, y2b, z, 8, 8);
        ModelPart.Vertex p4 = new ModelPart.Vertex(x, y2a, z, 8, 0);
        ModelPart.Vertex p5 = new ModelPart.Vertex(x, y, z2, 0, 0);
        ModelPart.Vertex p6 = new ModelPart.Vertex(x2, y, z2, 0, 8);
        ModelPart.Vertex p7 = new ModelPart.Vertex(x2, y2b, z2, 8, 8);
        ModelPart.Vertex p8 = new ModelPart.Vertex(x, y2a, z2, 8, 0);
        Vector3f slope = new Vector3f(-(height2 - height1), width, 0f).normalize();
        int i = 0;
        // x+
        polygons[i++] = new ModelPart.Polygon(new ModelPart.Vertex[] {p7, p6, p2, p3},
                texX + width + width, texY, texX + width + width + height2, texY + depth, texW, texH, false, Direction.EAST);
        // x-
        polygons[i++] = new ModelPart.Polygon(new ModelPart.Vertex[] {p4, p1, p5, p8},
                texX + width + width + height2, texY, texX + width + width + height2 + height1, texY + depth, texW, texH, false, Direction.WEST);
        // bottom
        polygons[i++] = new ModelPart.Polygon(new ModelPart.Vertex[] {p2, p6, p5, p1},
                texX, texY, texX + width, texY + depth, texW, texH, false, Direction.DOWN);
        // top (sloped)
        ModelPart.Polygon top = new ModelPart.Polygon(new ModelPart.Vertex[] {p7, p3, p4, p8},
                texX + width, texY, texX + width + width, texY + depth, texW, texH, false, Direction.UP);
        polygons[i++] = new ModelPart.Polygon(top.vertices(), slope);
        // z-
        polygons[i++] = new ModelPart.Polygon(new ModelPart.Vertex[] {p4, p3, p2, p1},
                texX, texY + depth, texX + width, texY + depth + height2, texW, texH, false, Direction.NORTH);
        // z+
        polygons[i] = new ModelPart.Polygon(new ModelPart.Vertex[] {p5, p6, p7, p8},
                texX + width, texY + depth, texX + width + width, texY + depth + height2, texW, texH, false, Direction.SOUTH);
    }
}
