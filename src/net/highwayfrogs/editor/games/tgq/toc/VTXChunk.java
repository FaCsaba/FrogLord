package net.highwayfrogs.editor.games.tgq.toc;

import lombok.Getter;
import lombok.Setter;
import net.highwayfrogs.editor.Constants;
import net.highwayfrogs.editor.file.GameObject;
import net.highwayfrogs.editor.file.reader.DataReader;
import net.highwayfrogs.editor.file.writer.DataWriter;
import net.highwayfrogs.editor.games.tgq.TGQChunkedFile;
import net.highwayfrogs.editor.games.tgq.TGQFile;
import net.highwayfrogs.editor.games.tgq.TGQImageFile;
import net.highwayfrogs.editor.system.Tuple2;
import net.highwayfrogs.editor.system.Tuple3;
import net.highwayfrogs.editor.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a 3D model.
 * TODO: It seems there are other polygon modes or something, some of the models don't look right.
 * Created by Kneesnap on 3/23/2020.
 */
@Getter
public class VTXChunk extends TGQFileChunk {
    private String referenceName; // If this is a reference.
    private String fullReferenceName;
    private byte[] toSave;

    // If this is a model.
    private List<TGQMaterial> materials = new ArrayList<>();
    private List<Tuple3<Float, Float, Float>> vertices = new ArrayList<>();
    private List<Tuple3<Float, Float, Float>> normals = new ArrayList<>();
    private List<Tuple2<Float, Float>> uvs = new ArrayList<>();
    private byte[] unknownBytes;
    private List<Integer> unknownIntegers = new ArrayList<>();
    @Setter private transient TGQChunkedFile environmentFile;
    //TODO: Could materials be applied to groups? What about the extra bytes in the material? Maybe it has ranges of faces, or just plain faces?
    //TODO: Color.
    //TODO: Bones, Animations? It seems like these are probably in a different chunk, and on a per-file basis. [Maybe this means the bone definitions are in the animation data.]

    public static final int NAME_SIZE = 32;
    public static final int FULL_NAME_SIZE = 260;
    public static final byte FULL_NAME_PADDING = (byte) 0xCD;

    public VTXChunk(TGQChunkedFile parentFile) {
        super(parentFile, TGQChunkType.VTX);
    }

    @Override
    public void load(DataReader reader) {
        reader.jumpTemp(reader.getIndex());
        this.toSave = reader.readBytes(reader.getRemaining()); //TODO
        reader.jumpReturn();

        if (!isRootChunk()) { // Reference.
            this.referenceName = reader.readTerminatedStringOfLength(NAME_SIZE);
            this.fullReferenceName = reader.readTerminatedStringOfLength(FULL_NAME_SIZE);
            return;
        }

        // Read Header. (TODO: Make sense of)
        int unk1 = reader.readInt(); //TODO: Flags? Always either 342 or 338. Could be flags. Though, what flags, I am not sure.
        int mode = reader.readInt(); // I think?

        int materialCount = reader.readInt();
        if (materialCount > 1)
            System.out.println(getParentFile().getMainArchive().getFiles().size() + " has MORE. " + materialCount);

        int nodeCount = reader.readInt(); // A node is probably a bone. TODO
        int byteCountForStep3 = reader.readInt();
        int materialAddress = reader.readInt(); //TODO: ?? Really?? Doesn't seem so, but the PS2 version seems to think so. Maybe it only has a value at runtime. Unsure.
        int vertexCount = reader.readInt();
        int unk7 = reader.readInt();
        int unk8 = reader.readInt();
        int unk9 = reader.readInt();
        int unk10 = reader.readInt();
        int unk11 = (mode == 6 ? reader.readInt() : -1);
        int unk12 = reader.readInt(); // Might always be zero.

        // 1. Read Materials.
        for (int i = 0; i < materialCount; i++) {
            TGQMaterial newMaterial = new TGQMaterial();
            newMaterial.load(reader);
            materials.add(newMaterial);
        }

        // 2. Unknown integers which resemble faces. TODO: I bet this data has something to do with what it applies to.
        while (reader.hasMore()) {
            int temp = reader.readInt();
            if ((temp & 0xFFFF0000) != 0) {
                reader.setIndex(reader.getIndex() - Constants.INTEGER_SIZE);
                break;
            }

            this.unknownIntegers.add(temp);
        }

        // 3. Read unknown bytes.
        this.unknownBytes = reader.readBytes(byteCountForStep3);
        if (reader.getIndex() % 4 != 0)
            reader.skipBytes(4 - (reader.getIndex() % 4)); // Pad.

        // Read Vertices. TODO: If we're struggling to find materials, read all integers in a file. Target a specific texture. Get its global hash, hash to TEX chunk in TOC, etc, and work to find it.
        //TODO: Is the first integer the count of faces in a mesh in certain cases?
        for (int i = 0; i < vertexCount; i++) {
            if (mode == 5) { // Should be 64.
                this.uvs.add(new Tuple2<>(reader.readFloat(), reader.readFloat()));
                reader.skipInt(); // May be material.
                reader.skipInt();
                this.vertices.add(new Tuple3<>(reader.readFloat(), reader.readFloat(), reader.readFloat()));
                this.normals.add(new Tuple3<>(0F, 0F, 0F)); //TODO: ?
                reader.skipBytes(Constants.INTEGER_SIZE * 5); // Unsure, Seems to be 0x3F800000. TODO: Figure this out. It might be color.
                //this.uvs.add(new Tuple2<>(reader.readFloat(), reader.readFloat())); // TF? We already read uvs above. TODO: ???
                reader.skipBytes(2 * Constants.FLOAT_SIZE); //TODO ^^
                reader.skipInt();
                reader.skipInt(); // May be material.
            } else { // Mode 6.
                this.vertices.add(new Tuple3<>(reader.readFloat(), reader.readFloat(), reader.readFloat()));
                reader.readFloat(); // 1.0 as a float usually.
                this.normals.add(new Tuple3<>(reader.readFloat(), reader.readFloat(), reader.readFloat()));
                reader.skipBytes(Constants.FLOAT_SIZE * 5); // Unsure, Seems to be 0x3F800000. Usually seems to be 1.0
                this.uvs.add(new Tuple2<>(reader.readFloat(), reader.readFloat()));
                reader.skipBytes(12); //TODO: Figure out this stuff. (Might have material id) 2 floats, 1 int.
            }
        }

        if (reader.hasMore())
            System.out.println("More " + getParentFile().getMainArchive().getFiles().size() + ": " + reader.getRemaining());
    }

    @Override
    public void save(DataWriter writer) {
        writer.writeBytes(this.toSave); //TODO: Temporary.
    }

    /**
     * Export this model to a .obj file.
     * @param outputFile The output file.
     */
    public void saveToFile(File outputFile) throws IOException {
        PrintWriter writer = new PrintWriter(outputFile);

        writer.write("# Exported by FrogLord " + Constants.VERSION + Constants.NEWLINE);
        writer.write("mtllib " + Utils.stripExtension(outputFile.getName()) + ".mtl" + Constants.NEWLINE);
        writer.write("usemtl root" + Constants.NEWLINE);
        writer.write(Constants.NEWLINE);

        // Vertices.
        writer.write("# Vertices" + Constants.NEWLINE);
        for (Tuple3<Float, Float, Float> vertex : this.vertices)
            writer.write("v " + vertex.getA() + " " + vertex.getB() + " " + vertex.getC() + Constants.NEWLINE);
        writer.write(Constants.NEWLINE);

        writer.write("# Normals" + Constants.NEWLINE);
        for (Tuple3<Float, Float, Float> normal : this.normals)
            writer.write("vn " + normal.getA() + " " + normal.getB() + " " + normal.getC() + Constants.NEWLINE);
        writer.write(Constants.NEWLINE);

        writer.write("# Texture Coordinates" + Constants.NEWLINE);
        for (Tuple2<Float, Float> uv : this.uvs)
            writer.write("vt " + uv.getA() + " " + uv.getB() + Constants.NEWLINE);
        writer.write(Constants.NEWLINE);

        // Write Faces.
        writer.write("# Faces" + Constants.NEWLINE);
        //for (int i = 1; i < this.vertices.size(); i += 3) //TODO: The faces don't always export right, example Gavin, or the dragon. I bet that block of integers above is face connections, we just have to figure out how to use them. (3761).
        //    writer.write("f " + i + "/" + i + "/" + i + " " + (i + 1) + "/" + (i + 1) + "/" + (i + 1) + " " + (i + 2) + "/" + (i + 2) + "/" + (i + 2) + Constants.NEWLINE);

        for (int i = 1; i < (this.vertices.size() - 2); i += 3) { //TODO: It's ++ for all faces, but borked. Fix this for other models later.
            int x = ((i % 3) != 1) ? i : (i + 2);
            int y = ((i % 3) != 1) ? (i + 1) : (i + 1);
            int z = ((i % 3) != 1) ? (i + 2) : i;
            writer.write("f " + x + "/" + x + "/" + x + " " + y + "/" + y + "/" + y + " " + z + "/" + z + "/" + z + Constants.NEWLINE);
        }

        writer.close();

        List<TEXChunk> chunks = new ArrayList<>();
        if (getEnvironmentFile() != null) {
            for (TGQFileChunk chunk : getEnvironmentFile().getChunks())
                if (chunk instanceof TEXChunk)
                    chunks.add((TEXChunk) chunk);

            PrintWriter mtlWriter = new PrintWriter(new File(outputFile.getParentFile(), outputFile.getName().split("\\.")[0] + ".mtl"));
            TEXChunk goodChunk = null;
            for (int i = 0; i < getMaterials().size(); i++) {
                TGQMaterial material = getMaterials().get(i);
                if (material.getTextureFile().isEmpty())
                    continue;

                File folder = new File(outputFile.getParentFile(), "Textures/");
                if (!folder.exists())
                    folder.mkdirs();

                String stripped = material.getTextureFile().split("\\.")[0].toLowerCase();
                TEXChunk foundChunk = null; //TODO: Gotta get this from the linked map.
                for (TEXChunk chunk : chunks)
                    if (chunk.getName().toLowerCase().contains(stripped))
                        foundChunk = chunk;

                if (foundChunk != null) {
                    if (goodChunk == null)
                        goodChunk = foundChunk;

                    TGQFile file = getParentFile().getMainArchive().getFileByName(foundChunk.getPath());
                    if (file instanceof TGQImageFile) {
                        ((TGQImageFile) file).saveImageToFile(new File(folder, file.getExportName() + ".png"));
                        mtlWriter.write("newmtl " + (goodChunk == foundChunk ? "root" : foundChunk.getName()) + Constants.NEWLINE);
                        mtlWriter.write("Kd 1 1 1" + Constants.NEWLINE);
                        mtlWriter.write("map_Kd " + "Textures/" + file.getExportName() + ".png" + Constants.NEWLINE);
                        mtlWriter.write(Constants.NEWLINE);
                    }
                }
            }
            mtlWriter.close();
        }

        //TODO: Get Textures from TEX section. Apply everything to texture 0 for now.
    }

    @Getter
    public static class TGQMaterial extends GameObject { //TODO: How does it know which material to apply to what face.
        private String materialName;
        private String textureFile;
        private byte[] extraData; // Extra bytes. TODO: Some of this data is color data, presumably meaning whole materials can be recolored. If this is true, it should also impact the exported material.
        //TODO: Tint?
        //TODO: Offset to texture data?
        //TODO: specular color?

        private static final int NAME_SIZE = 32;
        private static final int FILENAME_SIZE = 32;

        @Override
        public void load(DataReader reader) {
            this.materialName = reader.readTerminatedStringOfLength(NAME_SIZE);
            this.textureFile = reader.readTerminatedStringOfLength(FILENAME_SIZE);
            this.extraData = reader.readBytes(80);
        }

        @Override
        public void save(DataWriter writer) {
            writer.writeTerminatedStringOfLength(this.materialName, NAME_SIZE);
            writer.writeTerminatedStringOfLength(this.textureFile, FILENAME_SIZE);
            writer.writeBytes(this.extraData);
        }
    }
}
