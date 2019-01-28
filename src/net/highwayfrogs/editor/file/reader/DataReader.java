package net.highwayfrogs.editor.file.reader;

import net.highwayfrogs.editor.Constants;
import net.highwayfrogs.editor.Utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Stack;

/**
 * A tool for reading information from a data source.
 * Created by Kneesnap on 8/10/2018.
 */
public class DataReader {
    private DataSource source;
    private Stack<Integer> jumpStack = new Stack<>();

    public DataReader(DataSource source) {
        this.source = source;
    }

    /**
     * Read the next byte.
     * @return byteValue
     */
    public byte readByte() {
        try {
            return source.readByte();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read byte.", ex);
        }
    }

    /**
     * Set the reader index.
     * @param newIndex The index to read data from.
     */
    public void setIndex(int newIndex) {
        try {
            source.setIndex(newIndex);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to set reader index.", ex);
        }
    }

    /**
     * Gets the current reader index.
     * @return readerIndex
     */
    public int getIndex() {
        try {
            return source.getIndex();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to get reader index.", ex);
        }
    }

    /**
     * Find the bytes.
     * @param query      The bytes to search for.
     * @param placeAfter Should the cursor be placed after the bytes?
     */
    public void findBytes(byte[] query, boolean placeAfter) {
        Utils.verify(query.length > 0, "Cannot search for empty query.");

        while (hasMore()) {
            if (readByte() != query[0])
                continue;

            jumpTemp(getIndex());

            boolean pass = true;
            for (int i = 1; i < query.length; i++) {
                if (readByte() != query[i]) {
                    pass = false;
                    break;
                }
            }

            jumpReturn();

            if (pass) {
                this.setIndex(this.getIndex() - 1);
                if (placeAfter)
                    this.readBytes(query.length);
                return;
            }
        }

        throw new RuntimeException("Failed to find the specified bytes.");
    }

    /**
     * Get the amount of readable bytes.
     * @return size
     */
    public int getSize() {
        try {
            return source.getSize();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to get source size.", ex);
        }
    }

    /**
     * Checks if there are more readable bytes.
     * @return moreBytes
     */
    public boolean hasMore() {
        return getSize() > getIndex();
    }

    /**
     * Get the amount of remaining bytes.
     * @return remainingCount
     */
    public int getRemaining() {
        return getSize() - getIndex();
    }

    /**
     * Temporarily jump to an offset. Use jumpReturn to return.
     * Jumps are recorded Last in First Out style.
     * @param newIndex The offset to jump to.
     */
    public void jumpTemp(int newIndex) {
        this.jumpStack.add(getIndex());
        setIndex(newIndex);
    }

    /**
     * Return to the offset before jumpTemp was called.
     */
    public void jumpReturn() {
        setIndex(this.jumpStack.pop());
    }

    /**
     * Reads a single byte as an unsigned int.
     * @return unsignedByte
     */
    public int readUnsignedByte() {
        return Byte.toUnsignedInt(readByte());
    }

    /**
     * Reads a single byte as an unsigned byte, and returns it as a short. Since the java byte is -127 to 128, this allows us to read a byte as the appropriate value.
     * @return unsignedByteShort.
     */
    public short readUnsignedByteAsShort() {
        return Utils.byteToUnsignedShort(readByte());
    }

    /**
     * Reads a single short as an unsigned short, and returns it as a int. Since the java byte is -65534 to 65535, this allows us to read a short as the appropriate value.
     * @return unsignedShortInt.
     */
    public int readUnsignedShortAsInt() {
        return Utils.shortToUnsignedInt(readShort());
    }

    /**
     * Reads a single integer as an unsigned integer, returning it as a long.
     * @return unsignedIntLong
     */
    public long readUnsignedIntAsLong() {
        return Utils.intToUnsignedLong(readInt());
    }

    /**
     * Read the next bytes as an integer.
     * Reads four bytes.
     * @return intValue
     */
    public int readInt() {
        return readInt(Constants.INTEGER_SIZE);
    }

    /**
     * Read the next bytes as a short.
     * Reads two bytes.
     * @return shortValue
     */
    public short readShort() {
        byte[] data = readBytes(Constants.SHORT_SIZE);
        short value = 0;
        for (int i = 0; i < data.length; i++)
            value += ((long) data[i] & 0xFFL) << (Constants.BITS_PER_BYTE * i);
        return value;
    }

    /**
     * Read a variable number of bytes into an integer.
     * @param bytes The number of bytes to read.
     * @return intValue
     */
    public int readInt(int bytes) {
        byte[] data = readBytes(bytes);
        int value = 0;
        for (int i = 0; i < data.length; i++)
            value += ((long) data[i] & 0xFFL) << (Constants.BITS_PER_BYTE * i);
        return value;
    }

    /**
     * Read a string of a pre-specified length.
     * @param length The length of the string.
     * @return readStr
     */
    public String readString(int length) {
        return new String(readBytes(length));
    }

    /**
     * Verify the next few bytes match a string.
     * @param verify The string to verify.
     */
    public void verifyString(String verify) {
        String str = readString(verify.getBytes().length);
        Utils.verify(str.equals(verify), "String verify failure! \"%s\" does not match \"%s\".", str, verify);
    }

    /**
     * Read a string until a given byte is found.
     * @param terminator The byte which terminates the string. Usually 0.
     * @return strValue
     */
    public String readString(byte terminator) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            byte[] temp = new byte[1];
            while ((temp[0] = readByte()) != terminator)
                out.write(temp);
            out.close();
            return out.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to read terminating string.", ex);
        }
    }

    /**
     * Read a string which is terminated by a null byte.
     * @return strValue
     */
    public String readNullTerminatedString() {
        return readString(Constants.NULL_BYTE);
    }

    /**
     * Read bytes from the source, respecting endian.
     * @param amount The amount of bytes to read.
     * @return readBytes
     */
    public byte[] readBytes(int amount) {
        try {
            return source.readBytes(amount);
        } catch (Exception ex) {
            throw new RuntimeException("Error while reading " + amount + " bytes.", ex);
        }
    }

    /**
     * Create a sub-reader.
     * @param startOffset The offset to start reading from.
     * @param length      The length to read. -1 = Get remaining.
     * @return newReader
     */
    public DataReader newReader(int startOffset, int length) {
        jumpTemp(startOffset);
        byte[] bytes = readBytes(length >= 0 ? length : getRemaining());
        jumpReturn();

        return new DataReader(new ArraySource(bytes));
    }
}
