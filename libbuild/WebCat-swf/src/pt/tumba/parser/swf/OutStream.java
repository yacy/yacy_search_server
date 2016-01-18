package pt.tumba.parser.swf;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 *  Output Stream Wrapper
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class OutStream {
    /**
     *  Description of the Field
     */
    protected OutputStream out;
    /**
     *  Description of the Field
     */
    protected long bytesWritten = 0L;

    //--Bit buffer..
    /**
     *  Description of the Field
     */
    protected int bitBuf;
    /**
     *  Description of the Field
     */
    protected int bitPos;


    /**
     *  Constructor for the OutStream object
     *
     *@param  out  Description of the Parameter
     */
    public OutStream(OutputStream out) {
        this.out = out;
		bitBuf = 0;
	    bitPos = 0;
    }


    /**
     *  Gets the bytesWritten attribute of the OutStream object
     *
     *@return    The bytesWritten value
     */
    public long getBytesWritten() {
        return bytesWritten;
    }


    /**
     *  Sets the bytesWritten attribute of the OutStream object
     *
     *@param  written  The new bytesWritten value
     */
    public void setBytesWritten(long written) {
        bytesWritten = written;
    }


    /**
     *  Write a signed value to the output stream in the given number of bits.
     *  The value must actually fit in that number of bits or it will be garbled
     *
     *@param  numBits          Description of the Parameter
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void writeSBits(int numBits, int value) throws IOException {
        //--Mask out any sign bit
        long lval = value & 0x7FFFFFFF;

        if (value < 0) {
            //add the sign bit

            lval |= 1L << (numBits - 1);
        }

        //--Write the bits as if unsigned
        writeUBits(numBits, lval);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void flush() throws IOException {
        flushBits();
        out.flush();
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void close() throws IOException {
        out.close();
    }


    /**
     *  Flush the bit buffer to the output stream and reset values
     *
     *@exception  IOException  Description of the Exception
     */
    public void flushBits() throws IOException {
        if (bitPos == 0) {
            return;
        }
        //nothing to flush

        out.write(bitBuf);
        bitBuf = 0;
        bitPos = 0;

        bytesWritten++;
    }


    /**
     *  Write an unsigned value to the output stream in the given number of bits
     *
     *@param  numBits          Description of the Parameter
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void writeUBits(int numBits, long value) throws IOException {
        if (numBits == 0) {
            return;
        }

        if (bitPos == 0) {
            bitPos = 8;
        }
        //bitBuf was empty

        int bitNum = numBits;

        while (bitNum > 0) {
            //write all bits

            while (bitPos > 0 && bitNum > 0) {
                //write into all position of the bit buffer

                if (getBit(bitNum, value)) {
                    bitBuf = setBit(bitPos, bitBuf);
                }

                bitNum--;
                bitPos--;
            }

            if (bitPos == 0) {
                //bit buffer is full - write it

                writeUI8(bitBuf);
                bitBuf = 0;
                if (bitNum > 0) {
                    bitPos = 8;
                }
                //prepare for more bits
            }
        }
    }


    /**
     *  Get the given bit (where lowest bit is numbered 1)
     *
     *@param  bitNum  Description of the Parameter
     *@param  value   Description of the Parameter
     *@return         The bit value
     */
    public static boolean getBit(int bitNum, long value) {
        return (value & (1L << (bitNum - 1))) != 0;
    }


    /**
     *  Set the given bit (where lowest bit is numbered 1)
     *
     *@param  bitNum  The new bit value
     *@param  value   The new bit value
     *@return         Description of the Return Value
     */
    public static int setBit(int bitNum, int value) {
        return value | (1 << (bitNum - 1));
    }


    /**
     *  Write the given bytes to the output stream
     *
     *@param  bytes            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void write(byte[] bytes) throws IOException {
        flushBits();

        if (bytes != null && bytes.length > 0) {
            out.write(bytes);
            bytesWritten += bytes.length;
        }
    }


    /**
     *  Write the given bytes to the output stream
     *
     *@param  bytes            Description of the Parameter
     *@param  start            Description of the Parameter
     *@param  length           Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void write(byte[] bytes, int start, int length) throws IOException {
        flushBits();

        if (bytes != null && length > 0) {
            out.write(bytes, start, length);
            bytesWritten += length;
        }
    }


    /**
     *  Write an 8 bit unsigned value to the out stream
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void writeUI8(int value) throws IOException {
        flushBits();

        out.write(value);

        bytesWritten++;
    }


    /**
     *  Write a 16 bit unsigned value to the out stream
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void writeUI16(int value) throws IOException {
        flushBits();

        out.write(value & 0xff);
        out.write(value >> 8);

        bytesWritten += 2;
    }


    /**
     *  Write a 16 bit signed value to the out stream
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void writeSI16(short value) throws IOException {
        flushBits();

        out.write(value & 0xff);
        out.write(value >> 8);

        bytesWritten += 2;
    }


    /**
     *  Write a 32 bit unsigned value to the out stream
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void writeUI32(long value) throws IOException {
        flushBits();

        out.write((int) (value & 0xff));
        out.write((int) (value >> 8));
        out.write((int) (value >> 16));
        out.write((int) (value >> 24));

        bytesWritten += 4;
    }


    /**
     *  Write a string to the output stream using the default encoding and add
     *  terminating null
     *
     *@param  s                Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void writeString(String s2) throws IOException {
        String s = s2;
        if (s == null) {
            s = "";
        }
        writeString(s.getBytes());
    }


    /**
     *  Write a string to the output stream and add terminating null
     *
     *@param  string           Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void writeString(byte[] string) throws IOException {
        flushBits();

        if (string != null) {
            out.write(string);
        }
        out.write(0);
        //terminate string

        bytesWritten += string.length + 1;
    }


    /**
     *  Calculate the byte length of a string as it would be written to the
     *  output stream
     *
     *@param  string  Description of the Parameter
     *@return         The stringLength value
     */
    public static int getStringLength(byte[] string) {
        if (string == null) {
            return 1;
        }
        return string.length + 1;
        //to include the terminating null
    }


    /**
     *  Calculate the byte length of a string as it would be written to the
     *  output stream using the default character encoding
     *
     *@param  string  Description of the Parameter
     *@return         The stringLength value
     */
    public static int getStringLength(String string) {
        if (string == null) {
            return 1;
        }
        byte[] bytes = string.getBytes();

        return bytes.length + 1;
        //to include the terminating null
    }


    /**
     *  Reset the bit buffer
     */
    protected void initBits() {
        bitBuf = 0;
        bitPos = 0;
    }


    /**
     *  Determine the minimum number of bits required to hold the given signed
     *  value
     *
     *@param  value  Description of the Parameter
     *@return        Description of the Return Value
     */
    public static int determineSignedBitSize(int value) {
        if (value >= 0) {
            return determineUnsignedBitSize(value) + 1;
        }

        //--This is probably a really bad way of doing this...
        int topBit = 31;
        long mask = 0x40000000L;

        while (topBit > 0) {
            if ((value & mask) == 0) {
                break;
            }

            mask >>= 1;
            topBit--;
        }

        if (topBit == 0) {
            return 2;
        }
        //must have been -1

        //HACK: Flash represents -16 as 110000 rather than 10000 etc..
        int val2 = value & ((1 << topBit) - 1);
        if (val2 == 0) {
            topBit++;
        }

        return topBit + 1;
    }


    /**
     *  Determine the minimum number of bits required to hold the given unsigned
     *  value (may be zero)
     *
     *@param  value  Description of the Parameter
     *@return        Description of the Return Value
     */
    public static int determineUnsignedBitSize(long value) {
        //--This is probably a really bad way of doing this...
        int topBit = 32;
        long mask = 0x80000000L;

        while (topBit > 0) {
            if ((value & mask) != 0) {
                return topBit;
            }

            mask >>= 1;
            topBit--;
        }

        return 0;
    }


    /**
     *  Write a float value
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void writeFloat(float value) throws IOException {
        writeSI32(Float.floatToIntBits(value));
    }


    /**
     *  Write a double value
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void writeDouble(double value) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(baos);

        dout.writeDouble(value);

        dout.flush();

        byte[] bytes = baos.toByteArray();
        byte[] bytes2 = new byte[8];

        bytes2[0] = bytes[3];
        bytes2[1] = bytes[2];
        bytes2[2] = bytes[1];
        bytes2[3] = bytes[0];
        bytes2[4] = bytes[7];
        bytes2[5] = bytes[6];
        bytes2[6] = bytes[5];
        bytes2[7] = bytes[4];

        write(bytes2);
    }


    /**
     *  Write a 32 bit signed value
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void writeSI32(int value) throws IOException {
        flushBits();

        out.write(value & 0xff);
        out.write(value >> 8);
        out.write(value >> 16);
        out.write(value >> 24);

        bytesWritten += 4;
    }


    /**
     *  Util to convert a signed int to 2 bytes
     *
     *@param  value  Description of the Parameter
     *@return        Description of the Return Value
     */
    public static byte[] sintTo2Bytes(int value) {
        return new byte[]
                {
                uintToByte(value & 0xff),
                uintToByte(value >> 8)
                };
    }


    /**
     *  Util to convert an unsigned int to 2 bytes
     *
     *@param  value  Description of the Parameter
     *@return        Description of the Return Value
     */
    public static byte[] uintTo2Bytes(int value) {
        return new byte[]
                {
                uintToByte(value & 0xff),
                uintToByte(value >> 8)
                };
    }


    /**
     *  Util to convert an unsigned int to 4 bytes
     *
     *@param  value  Description of the Parameter
     *@return        Description of the Return Value
     */
    public static byte[] uintTo4Bytes(int value) {
        return new byte[]
                {
                uintToByte(value & 0xff),
                uintToByte(value >> 8),
                uintToByte(value >> 16),
                uintToByte(value >> 24)
                };
    }


    /**
     *  Util to convert an unsigned int to an unsigned byte
     *
     *@param  value  Description of the Parameter
     *@return        Description of the Return Value
     */
    public static byte uintToByte(int value2) {
        int value = value2;
        int lowbit = value & 1;
        value >>= 1;

        byte b = (byte) value;
        b <<= 1;
        b |= (byte) lowbit;

        return b;
    }
}
