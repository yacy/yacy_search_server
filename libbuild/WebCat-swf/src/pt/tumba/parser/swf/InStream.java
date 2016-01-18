package pt.tumba.parser.swf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Vector;

/**
 *  Input Stream Wrapper
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class InStream {
	/**
	 *  Description of the Field
	 */
	protected InputStream in;
	/**
	 *  Description of the Field
	 */
	protected long bytesRead = 0L;

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
	 *  Constructor for the InStream object
	 *
	 *@param  in  Description of the Parameter
	 */
	public InStream(InputStream in) {
		this.in = in;
		bitBuf = 0;
		bitPos = 0;
	}

	/**
	 *  Constructor for the InStream object
	 *
	 *@param  bytes  Description of the Parameter
	 */
	public InStream(byte[] bytes) {
		this(new ByteArrayInputStream(bytes));
	}

	/**
	 *  Read a string from the input stream
	 *
	 *@return                  Description of the Return Value
	 *@exception  IOException  Description of the Exception
	 */
	public byte[] readStringBytes() throws IOException {
		synchBits();

		Vector chars = new Vector();
		byte[] aChar = new byte[1];
		int num = 0;

		while ((num = in.read(aChar)) == 1) {
			bytesRead++;

			if (aChar[0] == 0) {
				//end of string

				byte[] string = new byte[chars.size()];

				int i = 0;
				for (Enumeration enumerator = chars.elements();
					enumerator.hasMoreElements();
					) {
					string[i++] = ((Byte) enumerator.nextElement()).byteValue();
				}

				return string;
			}

			chars.addElement(new Byte(aChar[0]));
		}

		throw new IOException("Unterminated string - reached end of input before null char");
	}

	/**
	 *  Read a null terminated string using the default character encoding
	 *
	 *@return                  Description of the Return Value
	 *@exception  IOException  Description of the Exception
	 */
	public String readString() throws IOException {
		return new String(readStringBytes());
	}

	/**
	 *  Read all remaining bytes from the stream
	 *
	 *@return                  Description of the Return Value
	 *@exception  IOException  Description of the Exception
	 */
	public byte[] read() throws IOException {
		ByteArrayOutputStream bout = new ByteArrayOutputStream();

		int b = 0;
		while ((b = in.read()) >= 0) {
			bout.write(b);
		}

		return bout.toByteArray();
	}

	/**
	 *  Read bytes from the input stream
	 *
	 *@param  length           Description of the Parameter
	 *@return                  Description of the Return Value
	 *@exception  IOException  Description of the Exception
	 */
	public byte[] read(int length) throws IOException {
		byte[] data = new byte[length];

		if (length > 0) {
			int read = 0;

			while (read < length) {
				int count = in.read(data, read, length - read);
				if (count < 0) {
					bytesRead += read;
					throw new IOException("Unexpected end of input while reading a specified number of bytes");
				}

				read += count;
			}

			bytesRead += read;
		}

		return data;
	}

	/**
	 *  Reset the bit buffer
	 */
	public void synchBits() {
		bitBuf = 0;
		bitPos = 0;
	}

	/**
	 *  Gets the bytesRead attribute of the InStream object
	 *
	 *@return    The bytesRead value
	 */
	public long getBytesRead() {
		return bytesRead;
	}

	/**
	 *  Sets the bytesRead attribute of the InStream object
	 *
	 *@param  read  The new bytesRead value
	 */
	public void setBytesRead(long read) {
		bytesRead = read;
	}

	/**
	 *  Skip a number of bytes from the input stream
	 *
	 *@param  length           Description of the Parameter
	 *@exception  IOException  Description of the Exception
	 */
	public void skipBytes(long length) throws IOException {
		long skipped = 0;

		while (skipped < length) {
			int val = in.read();

			if (val < 0) {
				throw new IOException("Unexpected end of input");
			}

			skipped++;
		}

		bytesRead += length;
	}

	/**
	 *  Read an unsigned value from the given number of bits
	 *
	 *@param  numBits          Description of the Parameter
	 *@return                  Description of the Return Value
	 *@exception  IOException  Description of the Exception
	 */
	public long readUBits(int numBits) throws IOException {
		if (numBits == 0) {
			return 0;
		}

		int bitsLeft = numBits;
		long result = 0;

		if (bitPos == 0) {
			//no value in the buffer - read a byte

			bitBuf = in.read();
			bitPos = 8;

			bytesRead++;
		}

		while (true) {
			int shift = bitsLeft - bitPos;
			if (shift > 0) {
				// Consume the entire buffer
				result |= bitBuf << shift;
				bitsLeft -= bitPos;

				// Get the next byte from the input stream
				bitBuf = in.read();
				bitPos = 8;

				bytesRead++;
			} else {
				// Consume a portion of the buffer
				result |= bitBuf >> -shift;
				bitPos -= bitsLeft;
				bitBuf &= 0xff >> (8 - bitPos);
				// mask off the consumed bits

				return result;
			}
		}
	}

	/**
	 *  Read an unsigned 8 bit value
	 *
	 *@return                  Description of the Return Value
	 *@exception  IOException  Description of the Exception
	 */
	public int readUI8() throws IOException {
		synchBits();

		int ui8 = in.read();
		if (ui8 < 0) {
			throw new IOException("Unexpected end of input");
		}

		bytesRead++;

		return ui8;
	}

	/**
	 *  Read an unsigned 16 bit value
	 *
	 *@return                  Description of the Return Value
	 *@exception  IOException  Description of the Exception
	 */
	public int readUI16() throws IOException {
		synchBits();

		int ui16 = in.read();
		if (ui16 < 0) {
			throw new IOException("Unexpected end of input");
		}

		int val = in.read();
		if (val < 0) {
			throw new IOException("Unexpected end of input");
		}

		ui16 += val << 8;

		bytesRead += 2;

		return ui16;
	}

	/**
	 *  Read a signed 16 bit value
	 *
	 *@return                  Description of the Return Value
	 *@exception  IOException  Description of the Exception
	 */
	public short readSI16() throws IOException {
		synchBits();

		int lowerByte = in.read();
		if (lowerByte < 0) {
			throw new IOException("Unexpected end of input");
		}

		byte[] aByte = new byte[1];
		int count = in.read(aByte);
		if (count < 1) {
			throw new IOException("Unexpected end of input");
		}

		bytesRead += 2;

		return (short) ((aByte[0] * 256) + lowerByte);
	}

	/**
	 *  Read an unsigned 32 bit value
	 *
	 *@return                  Description of the Return Value
	 *@exception  IOException  Description of the Exception
	 */
	public long readUI32() throws IOException {
		synchBits();

		long ui32 = in.read();
		if (ui32 < 0) {
			throw new IOException("Unexpected end of input");
		}

		long val = in.read();
		if (val < 0) {
			throw new IOException("Unexpected end of input");
		}

		ui32 += val << 8;

		val = in.read();
		if (val < 0) {
			throw new IOException("Unexpected end of input");
		}

		ui32 += val << 16;

		val = in.read();
		if (val < 0) {
			throw new IOException("Unexpected end of input");
		}

		ui32 += val << 24;

		bytesRead += 4;

		return ui32;
	}

	/**
	 *  Read a signed value from the given number of bits
	 *
	 *@param  numBits          Description of the Parameter
	 *@return                  Description of the Return Value
	 *@exception  IOException  Description of the Exception
	 */
	public int readSBits(int numBits) throws IOException {
		// Get the number as an unsigned value.
		long uBits = readUBits(numBits);

		// Is the number negative?
		if ((uBits & (1L << (numBits - 1))) != 0) {
			// Yes. Extend the sign.
			uBits |= -1L << numBits;
		}

		return (int) uBits;
	}

	/**
	 *  Read a 32 bit signed number
	 *
	 *@return                  Description of the Return Value
	 *@exception  IOException  Description of the Exception
	 */
	public int readSI32() throws IOException {
		synchBits();

		int b0 = in.read();
		if (b0 < 0) {
			throw new IOException("Unexpected end of input");
		}

		int b1 = in.read();
		if (b1 < 0) {
			throw new IOException("Unexpected end of input");
		}

		int b2 = in.read();
		if (b2 < 0) {
			throw new IOException("Unexpected end of input");
		}

		byte[] aByte = new byte[1];
		int count = in.read(aByte);
		if (count < 1) {
			throw new IOException("Unexpected end of input");
		}

		bytesRead += 4;

		return (int)
			((aByte[0] * 256 * 256 * 256) + (b2 * 256 * 256) + (b1 * 256) + b0);
	}

	/**
	 *  Read a 32 bit floating point number
	 *
	 *@return                  Description of the Return Value
	 *@exception  IOException  Description of the Exception
	 */
	public float readFloat() throws IOException {
		return Float.intBitsToFloat(readSI32());
	}

	/**
	 *  Read a 64 bit floating point number
	 *
	 *@return                  Description of the Return Value
	 *@exception  IOException  Description of the Exception
	 */
	public double readDouble() throws IOException {
		byte[] bytes = read(8);

		byte[] bytes2 = new byte[8];

		bytes2[0] = bytes[3];
		bytes2[1] = bytes[2];
		bytes2[2] = bytes[1];
		bytes2[3] = bytes[0];
		bytes2[4] = bytes[7];
		bytes2[5] = bytes[6];
		bytes2[6] = bytes[5];
		bytes2[7] = bytes[4];

		ByteArrayInputStream bin = new ByteArrayInputStream(bytes2);

		return new DataInputStream(bin).readDouble();
	}

	/**
	 *  Util to convert an unsigned byte to an unsigned int
	 *
	 *@param  b  Description of the Parameter
	 *@return    Description of the Return Value
	 */
	public static int ubyteToInt(byte b2) {
		boolean highbit = b2 < 0;
		byte b = (byte)(b2 & 0x7f);

		int i = (int) b;

		if (highbit) {
			i += 128;
		}

		return i;
	}

	/**
	 *  Util to convert 2 bytes to a signed value
	 *
	 *@param  lo  Description of the Parameter
	 *@param  hi  Description of the Parameter
	 *@return     Description of the Return Value
	 */
	public static int bytesToSigned(byte lo, byte hi) {
		int low = ubyteToInt(lo);
		int high = ubyteToInt(hi);

		int value = (high << 8) + low;

		if (value > 0x7fff) {
			value -= 65536;
		}

		return value;
	}
}
