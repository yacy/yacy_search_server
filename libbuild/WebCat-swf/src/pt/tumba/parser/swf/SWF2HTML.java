package pt.tumba.parser.swf;

import com.adobe.xmp.XMPConst;
import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPMeta;
import com.adobe.xmp.XMPMetaFactory;
import com.adobe.xmp.properties.XMPProperty;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;


/**
 *  Description of the Class
 *
 *@author     bmartins
 *@created    22 de Agosto de 2002
 */
public class SWF2HTML extends SWFTagTypesImpl {

    private int sizeCount = 0;


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public int originalSize() {
        return sizeCount;
    }


    protected Map fontCodes = new HashMap();
    protected String headerstr ="";
    protected PrintWriter output; // body of html output (containing all text)

    //private HTMLParser aux;


    /**
     *  Constructor for the SWF2HTML object
     */
    public SWF2HTML() {
        super(null);
    }


    /**
     *  SWFTagTypes interface Save the Text Font character code info
     *
     *@param  fontId           Description of the Parameter
     *@param  fontName         Description of the Parameter
     *@param  flags            Description of the Parameter
     *@param  codes            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineFontInfo(int fontId, String fontName, int flags, int[] codes)
             throws IOException {
        fontCodes.put(new Integer(fontId), codes);
    }


    /**
     *  SWFTagTypes interface Save the character code info
     *
     *@param  id               Description of the Parameter
     *@param  flags            Description of the Parameter
     *@param  name             Description of the Parameter
     *@param  numGlyphs        Description of the Parameter
     *@param  ascent           Description of the Parameter
     *@param  descent          Description of the Parameter
     *@param  leading          Description of the Parameter
     *@param  codes            Description of the Parameter
     *@param  advances         Description of the Parameter
     *@param  bounds           Description of the Parameter
     *@param  kernCodes1       Description of the Parameter
     *@param  kernCodes2       Description of the Parameter
     *@param  kernAdjustments  Description of the Parameter
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    public SWFVectors tagDefineFont2(int id, int flags, String name, int numGlyphs,
            int ascent, int descent, int leading,
            int[] codes, int[] advances, Rect[] bounds,
            int[] kernCodes1, int[] kernCodes2,
            int[] kernAdjustments) throws IOException {
        fontCodes.put(new Integer(id), (codes != null) ? codes : new int[0]);

        return null;
    }


    /**
     *  SWFTagTypes interface Dump any initial text in the field
     *
     *@param  fieldId          Description of the Parameter
     *@param  fieldName        Description of the Parameter
     *@param  initialText      Description of the Parameter
     *@param  boundary         Description of the Parameter
     *@param  flags            Description of the Parameter
     *@param  textColor        Description of the Parameter
     *@param  alignment        Description of the Parameter
     *@param  fontId           Description of the Parameter
     *@param  fontSize         Description of the Parameter
     *@param  charLimit        Description of the Parameter
     *@param  leftMargin       Description of the Parameter
     *@param  rightMargin      Description of the Parameter
     *@param  indentation      Description of the Parameter
     *@param  lineSpacing      Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineTextField(int fieldId, String fieldName,
            String initialText, Rect boundary, int flags,
            AlphaColor textColor, int alignment, int fontId, int fontSize,
            int charLimit, int leftMargin, int rightMargin, int indentation,
            int lineSpacing)
             throws IOException {
        if (initialText != null) {
            output.println(initialText);
        }
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@param  bounds           Description of the Parameter
     *@param  matrix           Description of the Parameter
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    public SWFText tagDefineText(int id, Rect bounds, Matrix matrix)
             throws IOException {
        return new TextDumper();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@param  bounds           Description of the Parameter
     *@param  matrix           Description of the Parameter
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    public SWFText tagDefineText2(int id, Rect bounds, Matrix matrix) throws IOException {
        return new TextDumper();
    }

    /**
     * Parse and interprete Metadata string (xmp rdf format) and create a
     * html header tags for the html output
     *
     * @param xml Metadata
     * @throws IOException
     */
    @Override
    public void tagMetaData(String xml) throws IOException {

        try {
            XMPMeta xmpmeta = XMPMetaFactory.parseFromString(xml);
            XMPProperty xp = xmpmeta.getProperty(XMPConst.NS_DC, "title");
            if (xp != null) {
                headerstr = "<title>" + xp.getValue() + "</title>";
            }

            xp = xmpmeta.getProperty(XMPConst.NS_DC, "creator");
            if (xp != null) {
                headerstr += "<meta name=\"author\" content=\"" + xp.getValue() + "\">";
            }

            xp = xmpmeta.getProperty(XMPConst.NS_DC, "description");
            if (xp != null) {
                headerstr += "<meta name=\"description\" content=\"" + xp.getValue() + "\">";
            }
            xp = xmpmeta.getProperty(XMPConst.NS_DC, "subject");
            if (xp != null) {
                headerstr += "<meta name=\"keywords\" content=\"" + xp.getValue() + "\">";
            }

            // get a date (modified , created)
            xp = xmpmeta.getProperty(XMPConst.NS_XMP, "ModifyDate");
            if (xp != null) {
                headerstr += "<meta name=\"date\" content=\"" + xp.getValue() + "\">";
            } else {
                xp = xmpmeta.getProperty(XMPConst.NS_XMP, "CreateDate");
                if (xp != null) {
                    headerstr += "<meta name=\"date\" content=\"" + xp.getValue() + "\">";
                } else {
                    xp = xmpmeta.getProperty(XMPConst.NS_DC, "date");
                    if (xp != null) {
                        headerstr += "<meta name=\"date\" content=\"" + xp.getValue() + "\">";
                    }
                }
            }

            xp = xmpmeta.getProperty(XMPConst.NS_XMP, "CreatorTool");
            if (xp != null) {
                headerstr += "<meta name=\"generator\" content=\"" + xp.getValue() + "\">";
            }

            xp = xmpmeta.getProperty(XMPConst.NS_DC, "publisher");
            if (xp != null) {
                headerstr += "<meta name=\"publisher\" content=\"" + xp.getValue() + "\">";
            }

        } catch (XMPException ex) { }
    }

    /**
     *  Description of the Class
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    public class TextDumper implements SWFText {

        protected Integer fontId;
        protected boolean firstY = true;


        /**
         *  Description of the Method
         *
         *@param  fontId      Description of the Parameter
         *@param  textHeight  Description of the Parameter
         */
        public void font(int fontId, int textHeight) {
            this.fontId = new Integer(fontId);
        }


        /**
         *  Sets the y attribute of the TextDumper object
         *
         *@param  y  The new y value
         */
        public void setY(int y) {
            if (firstY) {
                firstY = false;
            } else {
                output.println();
            }
            //Change in Y - dump a new line
        }


        /**
         *  Description of the Method
         *
         *@param  glyphIndices   Description of the Parameter
         *@param  glyphAdvances  Description of the Parameter
         */
        public void text(int[] glyphIndices, int[] glyphAdvances) {
            int[] codes = (int[]) fontCodes.get(fontId);
            if (codes == null) {
                return;
            }
            //--Translate the glyph indices to character codes
            char[] chars = new char[glyphIndices.length];
            for (int i = 0; i < chars.length; i++) {
                int index = glyphIndices[i];
                if (index >= codes.length) {
                    //System Font ?
                    chars[i] = (char) index;
                } else {
                    chars[i] = (char) (codes[index]);
                }
            }

            output.print(chars);
        }


        /**
         *  Description of the Method
         *
         *@param  color  Description of the Parameter
         */
        public void color(Color color) { }


        /**
         *  Sets the x attribute of the TextDumper object
         *
         *@param  x  The new x value
         */
        public void setX(int x) { }


        /**
         *  Description of the Method
         */
        public void done() {
            output.println();
        }
    }


    /**
     *  Description of the Method
     *
     *@param  in             Description of the Parameter
     *@return                Description of the Return Value
     *@exception  Exception  Description of the Exception
     */
    public String convertSWFToHTML(File in) throws Exception {
        return convertSWFToHTML(new FileInputStream(in));
    }


    /**
     *  Description of the Method
     *
     *@param  in             Description of the Parameter
     *@return                Description of the Return Value
     *@exception  Exception  Description of the Exception
     */
    public String convertSWFToHTML(URL in) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) in.openConnection();
        conn.setAllowUserInteraction(false);
        conn.setRequestProperty("User-agent", "www.tumba.pt");
        conn.setInstanceFollowRedirects(true);
        conn.connect();
        return convertSWFToHTML(conn.getInputStream());
    }


    /**
     * Parses swf input and extracts text and wrap it as html
     *
     * @param in SWF inputstream
     * @return html of text in swf
     * @exception Exception Description of the Exception
     */
    public String convertSWFToHTML(InputStream in) throws Exception {
        StringWriter out1 = new StringWriter();
        output = new PrintWriter(out1);
        TagParser parser = new TagParser(this);
        SWFReader reader = new SWFReader(parser, in);
        reader.readFile();
        in.close();
        sizeCount = reader.size;
        // generate html output string
        final String ret = "<html>"
                + (headerstr.isEmpty() ? "<body>" :  "<header>" + headerstr + "</header><body>")
                + out1.toString()
                + "</body></html>";
        return ret;
    }


    /**
     *  Description of the Method
     *
     *@param  b2             Description of the Parameter
     *@return                Description of the Return Value
     *@exception  Exception  Description of the Exception
     */
    public String convertSWFToHTML(byte[] b2) throws Exception {
        return convertSWFToHTML(new ByteArrayInputStream(b2));
    }

}
