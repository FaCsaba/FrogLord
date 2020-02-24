package net.highwayfrogs.editor.file.map.poly.polygon;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.highwayfrogs.editor.Constants;
import net.highwayfrogs.editor.file.map.view.TextureMap;
import net.highwayfrogs.editor.file.map.view.TextureMap.TextureTreeNode;
import net.highwayfrogs.editor.file.map.view.VertexColor;
import net.highwayfrogs.editor.file.reader.DataReader;
import net.highwayfrogs.editor.file.standard.psx.ByteUV;
import net.highwayfrogs.editor.file.standard.psx.PSXColorVector;
import net.highwayfrogs.editor.file.vlo.ImageFilterSettings;
import net.highwayfrogs.editor.file.vlo.ImageFilterSettings.ImageState;
import net.highwayfrogs.editor.file.writer.DataWriter;
import net.highwayfrogs.editor.system.TexturedPoly;
import net.highwayfrogs.editor.utils.Utils;

import java.awt.image.BufferedImage;

/**
 * Represents PSX polygons with a texture.
 * Created by Kneesnap on 8/25/2018.
 */
@Getter
@Setter
public abstract class MAPPolyTexture extends MAPPolygon implements TexturedPoly {
    private short flags;
    private ByteUV[] uvs;
    private short textureId;
    private PSXColorVector[] vectors;

    private static final ImageFilterSettings SHOW_SETTINGS = new ImageFilterSettings(ImageState.EXPORT).setTrimEdges(true).setAllowFlip(true);
    private static final int SHOW_SIZE = 150;

    public MAPPolyTexture(MAPPolygonType type, int verticeCount, int colorCount) {
        super(type, verticeCount);
        this.uvs = new ByteUV[verticeCount];
        this.vectors = new PSXColorVector[colorCount];
    }

    @Override
    public void load(DataReader reader) {
        super.load(reader);

        this.flags = reader.readShort();
        reader.skipShort(); // Padding

        loadUV(0, reader);
        reader.skipShort(); // Runtime clut-id.
        loadUV(1, reader);
        this.textureId = reader.readShort();

        if (this.uvs.length == 3) {
            loadUV(2, reader);
            reader.skipShort(); // Padding.
        } else if (this.uvs.length == 4) {
            loadUV(3, reader); // Read out of order.
            loadUV(2, reader);
        } else {
            throw new RuntimeException("Cannot handle " + this.uvs.length + " uvs.");
        }

        for (int i = 0; i < this.vectors.length; i++) {
            PSXColorVector vector = new PSXColorVector();
            vector.load(reader);
            this.vectors[i] = vector;
        }
    }

    @Override
    public void save(DataWriter writer) {
        super.save(writer);

        writer.writeShort(this.flags);
        writer.writeNull(Constants.SHORT_SIZE);
        this.uvs[0].save(writer);
        writer.writeShort((short) 0); // Runtime value.
        this.uvs[1].save(writer);
        writer.writeShort(this.textureId);

        if (this.uvs.length == 3) {
            this.uvs[2].save(writer);
            writer.writeNull(Constants.SHORT_SIZE);
        } else if (this.uvs.length == 4) {
            this.uvs[3].save(writer); // Save out of order.
            this.uvs[2].save(writer);
        } else {
            throw new RuntimeException("Cannot save " + this.uvs.length + " uvs.");
        }

        for (PSXColorVector colorVector : this.vectors)
            colorVector.save(writer);
    }

    /**
     * Creates a texture which has shading applied.
     * @return shadedTexture
     */
    public BufferedImage makeShadedTexture(TextureMap map, BufferedImage applyTo) {
        if (!(this instanceof VertexColor))
            throw new RuntimeException("Cannot generate shaded texture, this is not a VertexColor.");

        BufferedImage normalImage = applyTo != null ? applyTo : getNode(map).getImage();
        BufferedImage colorIdentifier = ((VertexColor) this).makeTexture(normalImage.getWidth(), normalImage.getHeight(), true);
        BufferedImage newImage = new BufferedImage(normalImage.getWidth(), normalImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < newImage.getWidth(); x++) {
            for (int y = 0; y < newImage.getHeight(); y++) {
                int rgb = normalImage.getRGB(x, y);
                int overlay = colorIdentifier.getRGB(x, y);
                int red = (int) (((double) Utils.getRedInt(overlay) / 127D) * (double) Utils.getRedInt(rgb));
                int green = (int) (((double) Utils.getGreenInt(overlay) / 127D) * (double) Utils.getGreenInt(rgb));
                int blue = (int) (((double) Utils.getBlueInt(overlay) / 127D) * (double) Utils.getBlueInt(rgb));
                newImage.setRGB(x, y, ((0xFF << 24) | ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF)));
            }
        }

        return newImage;
    }

    private void loadUV(int id, DataReader reader) {
        this.uvs[id] = new ByteUV();
        this.uvs[id].load(reader);
    }

    @Override
    public int getOrderId() {
        return getTextureId();
    }

    @Override
    public TextureTreeNode getNode(TextureMap map) {
        return map.getEntry(getTextureId());
    }

    /**
     * Test if a flag is present.
     * @param flag The flag in question.
     * @return flagPresent
     */
    public boolean testFlag(PolyTextureFlag flag) {
        return (this.flags & flag.getFlag()) == flag.getFlag();
    }

    /**
     * Swap the UV order, if possible.
     */
    public void performSwap() {
        if (this.uvs.length == 4) {
            ByteUV temp = this.uvs[3];
            this.uvs[3] = this.uvs[2];
            this.uvs[2] = temp;
        }
    }

    /**
     * Set a flag state.
     * @param flag     The flag to set.
     * @param newState The new flag state.
     */
    public void setFlag(PolyTextureFlag flag, boolean newState) {
        boolean currentState = testFlag(flag);
        if (currentState == newState)
            return; // Prevents the ^ operation from breaking the value.

        if (newState) {
            this.flags |= flag.getFlag();
        } else {
            this.flags ^= flag.getFlag();
        }
    }

    @Getter
    @AllArgsConstructor
    public enum PolyTextureFlag {
        SEMI_TRANSPARENT(Constants.BIT_FLAG_0), // setSemiTrans(true)
        ENVIRONMENT_IMAGE(Constants.BIT_FLAG_1), // Show the solid environment bitmap. (For instance, how water appears as a solid body, or sludge in the sewer levels.)
        MAX_ORDER_TABLE(Constants.BIT_FLAG_2); // Puts at the back of the order table. Either the very lowest rendering priority, or the very highest.

        private final int flag;
    }
}
