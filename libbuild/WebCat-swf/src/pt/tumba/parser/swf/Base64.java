package pt.tumba.parser.swf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Hashtable;

/**
 *  Base64 encoding/decoding utilities
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class Base64 {
    
	private static final Base64 _theInstance = new Base64();

		private Base64() {
		}

		public static Base64 getInstance() {
			return _theInstance;
		}

    
    /**
     *  Description of the Field
     */
    public final static char[] charset =
            {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',
            'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
            'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X',
            'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',
            'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
            'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
            'w', 'x', 'y', 'z', '0', '1', '2', '3',
            '4', '5', '6', '7', '8', '9', '+', '/'
            };

    /**
     *  Description of the Field
     */
    public final static char paddingChar = '=';

    /**
     *  Description of the Field
     */
    protected static Hashtable charLookup = new Hashtable();

    static {
        //initialize the hashtable

        for (int i = 0; i < charset.length; i++) {
            charLookup.put(new Character(charset[i]),
                    new Integer(i));
        }
    }


    /**
     *  Description of the Method
     *
     *@param  in             Description of the Parameter
     *@param  out            Description of the Parameter
     *@exception  Exception  Description of the Exception
     */
    public static void decode(Reader in, OutputStream out)
             throws Exception {
        char[] chars = new char[4];
        int[] sixbit = new int[4];

        //--Process the input stream in 4-character chunks
        while (true) {
            int numread = 0;

            while (numread < 4) {
                int read = in.read();
                if (read < 0) {
                    break;
                }
                //end of input

                char aChar = (char) read;

                if (Character.isWhitespace(aChar)) {
                    continue;
                }
                //skip w/s

                chars[numread++] = aChar;
            }

            if (numread == 0) {
                return;
            }
            //end of input

            if (numread != 4) {
                throw new Exception("Incomplete character quartet at end of Base64 input");
            }

            //--Convert chars to six-bit values
            for (int i = 0; i < 4; i++) {
                Integer value = (Integer) charLookup.get(new Character(chars[i]));

                if (value == null) {
                    if (chars[i] != '=' || i < 2) {
                        throw new Exception("Invalid char ("
                                + chars[i] + ") in Base64 data");
                    }

                    sixbit[i] = -1;
                } else {
                    sixbit[i] = value.intValue();
                }
            }

            //--Write first 6 bits and top 2 bits from second value
            out.write((sixbit[0] << 2) + (sixbit[1] >> 4));
            //System.out.println( (sixbit[0] << 2) + (sixbit[1] >> 4) );

            //--Get bottom four bits of second value
            int val = (sixbit[1] & 0xf) << 4;

            if (sixbit[2] >= 0) {
                //third value is valid

                //--Add top four bits of third value
                val += sixbit[2] >> 2;

                out.write(val);
                //System.out.println( val );

                //--Get bottom two bits of third value
                val = (sixbit[2] & 0x3) << 6;

                if (sixbit[3] >= 0) {
                    //fourth value is valid

                    val += sixbit[3];

                    out.write(val);
                    //System.out.println( val );
                }
            }
        }
    }


    /**
     *  Description of the Method
     *
     *@param  base64         Description of the Parameter
     *@return                Description of the Return Value
     *@exception  Exception  Description of the Exception
     */
    public static byte[] decode(String base64)
             throws Exception {
        //if base64 is invalid

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StringReader in = new StringReader(base64);

        decode(in, out);

        in.close();
        out.flush();
        out.close();

        return out.toByteArray();
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@param  out              Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public static void encode(InputStream in, Writer out)
             throws IOException {
        int column = 0;

        //--Process 3 bytes in each loop - writing 4 base64 chars to the output
        while (true) {
            int byte1 = in.read();
            int byte2 = in.read();
            int byte3 = in.read();

            if (byte1 < 0) {
                return;
            }
            //end-of-data

            //--Wrap output at column 72
            if (column >= 72) {
                column = 0;
                out.write('\n');
            }

            out.write(charset[byte1 >> 2]);
            //write top 6 bits of byte 1

            int index = (byte1 & 0x3) << 4;
            //get bottom two bits of byte 1

            if (byte2 < 0) {
                //no more data

                out.write(charset[index]);
                out.write(paddingChar);
                out.write(paddingChar);
                return;
            }

            index += byte2 >> 4;
            //add the top 4 bits of byte 2
            out.write(charset[index]);

            index = (byte2 & 0xf) << 2;
            //get bottom 4 bits of byte 2

            if (byte3 < 0) {
                //more more data

                out.write(charset[index]);
                out.write(paddingChar);
                return;
            }

            index += byte3 >> 6;
            //add top 2 bits of byte 3
            out.write(charset[index]);

            out.write(charset[byte3 & 0x3f]);
            //write bottom 6 bits of byte 3

            //--Advance column counter
            column += 4;
        }
    }


    /**
     *  Description of the Method
     *
     *@param  data  Description of the Parameter
     *@return       Description of the Return Value
     */
    public static String encode(byte[] data) {
        try {
            return encode(data, 0, data.length);
        } catch (ArrayIndexOutOfBoundsException aiobe) {
            return aiobe.toString();
        }
    }


    /**
     *  Description of the Method
     *
     *@param  data                                Description of the Parameter
     *@param  start                               Description of the Parameter
     *@param  length                              Description of the Parameter
     *@return                                     Description of the Return
     *      Value
     *@exception  ArrayIndexOutOfBoundsException  Description of the Exception
     */
    public static String encode(byte[] data, int start, int length)
             throws ArrayIndexOutOfBoundsException {
        StringWriter out = new StringWriter();
        ByteArrayInputStream in = new ByteArrayInputStream(data, start, length);

        try {
            encode(in, out);
            in.close();
            out.flush();
            out.close();
        } catch (IOException ioe) {
            return ioe.toString();
        }

        return out.toString();
    }


    /**
     *  If args.length > 0 then encode binary on stdin to base64 on stdout, else
     *  decode base64 on stdin to binary on stdout
     *
     *@param  args           The command line arguments
     *@exception  Exception  Description of the Exception
     */
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            OutputStreamWriter out = new OutputStreamWriter(System.out);
            encode(System.in, out);
            out.flush();
            return;
        }

        decode(new InputStreamReader(System.in), System.out);
        System.out.flush();
    }
}
