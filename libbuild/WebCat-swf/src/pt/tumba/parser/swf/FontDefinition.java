package pt.tumba.parser.swf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 *  A Font Definition that can referenced by Font symbols. If read in from an
 *  existing Flash movie the font definition may only contain a subset of the
 *  glyphs in the font. To use a system font set the hasMetrics flag to false.
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class FontDefinition {
    /**
     *  A Glyph within the font.
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    public static class Glyph {
        /**
         *  Description of the Field
         */
        protected int code;
        /**
         *  Description of the Field
         */
        protected double advance;
        /**
         *  Description of the Field
         */
        protected Shape shape;


        /**
         *  Gets the shape attribute of the Glyph object
         *
         *@return    The shape value
         */
        public Shape getShape() {
            return shape;
        }


        /**
         *  Gets the code attribute of the Glyph object
         *
         *@return    The code value
         */
        public int getCode() {
            return code;
        }


        /**
         *  Gets the advance attribute of the Glyph object
         *
         *@return    The advance value
         */
        public double getAdvance() {
            return advance;
        }


        /**
         *  Sets the shape attribute of the Glyph object
         *
         *@param  shape  The new shape value
         */
        public void setShape(Shape shape) {
            this.shape = shape;
        }


        /**
         *  Sets the code attribute of the Glyph object
         *
         *@param  code  The new code value
         */
        public void setCode(int code) {
            this.code = code;
        }


        /**
         *  Sets the advance attribute of the Glyph object
         *
         *@param  advance  The new advance value
         */
        public void setAdvance(double advance) {
            this.advance = advance;
        }


        /**
         *  Constructor for the Glyph object
         *
         *@param  shape    Description of the Parameter
         *@param  advance  Description of the Parameter
         *@param  code     Description of the Parameter
         */
        public Glyph(Shape shape, double advance, int code) {
            this.shape = shape;
            this.advance = advance;
            this.code = code;
        }
    }


    /**
     *  A Kerning Pair is an adjustment to the advance between two particular
     *  glyphs.
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    public static class KerningPair {
        /**
         *  Description of the Field
         */
        protected int code1, code2;
        /**
         *  Description of the Field
         */
        protected double adjustment;


        /**
         *  Gets the code1 attribute of the KerningPair object
         *
         *@return    The code1 value
         */
        public int getCode1() {
            return code1;
        }


        /**
         *  Gets the code2 attribute of the KerningPair object
         *
         *@return    The code2 value
         */
        public int getCode2() {
            return code2;
        }


        /**
         *  Gets the adjustment attribute of the KerningPair object
         *
         *@return    The adjustment value
         */
        public double getAdjustment() {
            return adjustment;
        }


        /**
         *  Sets the code1 attribute of the KerningPair object
         *
         *@param  code  The new code1 value
         */
        public void setCode1(int code) {
            code1 = code;
        }


        /**
         *  Sets the code2 attribute of the KerningPair object
         *
         *@param  code  The new code2 value
         */
        public void setCode2(int code) {
            code2 = code;
        }


        /**
         *  Sets the adjustment attribute of the KerningPair object
         *
         *@param  offset  The new adjustment value
         */
        public void setAdjustment(double offset) {
            adjustment = offset;
        }


        /**
         *  Constructor for the KerningPair object
         *
         *@param  code1       Description of the Parameter
         *@param  code2       Description of the Parameter
         *@param  adjustment  Description of the Parameter
         */
        public KerningPair(int code1, int code2, double adjustment) {
            this.code1 = code1;
            this.code2 = code2;
            this.adjustment = adjustment;
        }
    }


    /**
     *  Description of the Field
     */
    protected String name;
    /**
     *  Description of the Field
     */
    protected double ascent;
    /**
     *  Description of the Field
     */
    protected double descent;
    /**
     *  Description of the Field
     */
    protected double leading;

    /**
     *  Description of the Field
     */
    protected boolean isUnicode;
    /**
     *  Description of the Field
     */
    protected boolean isShiftJIS;
    /**
     *  Description of the Field
     */
    protected boolean isAnsi;
    /**
     *  Description of the Field
     */
    protected boolean isItalic;
    /**
     *  Description of the Field
     */
    protected boolean isBold;
    /**
     *  Description of the Field
     */
    protected boolean hasMetrics;

    /**
     *  Description of the Field
     */
    protected List glyphs = new ArrayList();
    /**
     *  Description of the Field
     */
    protected List kerning = new ArrayList();

    /**
     *  Description of the Field
     */
    protected Map glyphLookup;
    /**
     *  Description of the Field
     */
    protected Map kernLookup;


    /**
     *  Gets the name attribute of the FontDefinition object
     *
     *@return    The name value
     */
    public String getName() {
        return name;
    }


    /**
     *  Gets the ascent attribute of the FontDefinition object
     *
     *@return    The ascent value
     */
    public double getAscent() {
        return ascent;
    }


    /**
     *  Gets the descent attribute of the FontDefinition object
     *
     *@return    The descent value
     */
    public double getDescent() {
        return descent;
    }


    /**
     *  Gets the leading attribute of the FontDefinition object
     *
     *@return    The leading value
     */
    public double getLeading() {
        return leading;
    }


    /**
     *  Gets the unicode attribute of the FontDefinition object
     *
     *@return    The unicode value
     */
    public boolean isUnicode() {
        return isUnicode;
    }


    /**
     *  Gets the shiftJIS attribute of the FontDefinition object
     *
     *@return    The shiftJIS value
     */
    public boolean isShiftJIS() {
        return isShiftJIS;
    }


    /**
     *  Gets the ansi attribute of the FontDefinition object
     *
     *@return    The ansi value
     */
    public boolean isAnsi() {
        return isAnsi;
    }


    /**
     *  Gets the italic attribute of the FontDefinition object
     *
     *@return    The italic value
     */
    public boolean isItalic() {
        return isItalic;
    }


    /**
     *  Gets the bold attribute of the FontDefinition object
     *
     *@return    The bold value
     */
    public boolean isBold() {
        return isBold;
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public boolean hasMetrics() {
        return hasMetrics;
    }


    /**
     *  Get the List of Glyph objects
     *
     *@return    The glyphList value
     */
    public List getGlyphList() {
        return glyphs;
    }


    /**
     *  Get the List of KerningPair objects
     *
     *@return    The kerningPairList value
     */
    public List getKerningPairList() {
        return kerning;
    }


    /**
     *  Sets the name attribute of the FontDefinition object
     *
     *@param  name  The new name value
     */
    public void setName(String name) {
        this.name = name;
    }


    /**
     *  Sets the ascent attribute of the FontDefinition object
     *
     *@param  ascent  The new ascent value
     */
    public void setAscent(double ascent) {
        this.ascent = ascent;
    }


    /**
     *  Sets the descent attribute of the FontDefinition object
     *
     *@param  descent  The new descent value
     */
    public void setDescent(double descent) {
        this.descent = descent;
    }


    /**
     *  Sets the leading attribute of the FontDefinition object
     *
     *@param  leading  The new leading value
     */
    public void setLeading(double leading) {
        this.leading = leading;
    }


    /**
     *  Sets the fontFlags attribute of the FontDefinition object
     *
     *@param  isUnicode   The new fontFlags value
     *@param  isShiftJIS  The new fontFlags value
     *@param  isAnsi      The new fontFlags value
     *@param  isItalic    The new fontFlags value
     *@param  isBold      The new fontFlags value
     *@param  hasMetrics  The new fontFlags value
     */
    public void setFontFlags(boolean isUnicode, boolean isShiftJIS, boolean isAnsi,
            boolean isItalic, boolean isBold, boolean hasMetrics) {
        this.isUnicode = isUnicode;
        this.isShiftJIS = isShiftJIS;
        this.isAnsi = isAnsi;
        this.isItalic = isItalic;
        this.isBold = isBold;
        this.hasMetrics = hasMetrics;
    }


    /**
     *  Constructor for the FontDefinition object
     */
    public FontDefinition() { }


    /**
     *  Constructor for the FontDefinition object
     *
     *@param  name        Description of the Parameter
     *@param  ascent      Description of the Parameter
     *@param  descent     Description of the Parameter
     *@param  leading     Description of the Parameter
     *@param  isUnicode   Description of the Parameter
     *@param  isShiftJIS  Description of the Parameter
     *@param  isAnsi      Description of the Parameter
     *@param  isItalic    Description of the Parameter
     *@param  isBold      Description of the Parameter
     *@param  hasMetrics  Description of the Parameter
     */
    public FontDefinition(String name, double ascent, double descent, double leading,
            boolean isUnicode, boolean isShiftJIS, boolean isAnsi,
            boolean isItalic, boolean isBold, boolean hasMetrics) {
        this.name = name;
        this.ascent = ascent;
        this.descent = descent;
        this.leading = leading;

        this.isUnicode = isUnicode;
        this.isShiftJIS = isShiftJIS;
        this.isAnsi = isAnsi;
        this.isItalic = isItalic;
        this.isBold = isBold;
        this.hasMetrics = hasMetrics;
    }


    /**
     *  Look up a glyph by code
     *
     *@param  code  Description of the Parameter
     *@return       null if the code has no glyph
     */
    public Glyph getGlyph(int code) {
        if (glyphLookup == null) {
            glyphLookup = new HashMap();

            for (Iterator it = glyphs.iterator(); it.hasNext(); ) {
                Glyph g = (Glyph) it.next();

                glyphLookup.put(new Integer(g.code), g);
            }
        }

        Glyph g = (Glyph) glyphLookup.get(new Integer(code));

        return g;
    }


    /**
     *  Get the kerning adjustment required between the two given codes
     *
     *@param  code1  Description of the Parameter
     *@param  code2  Description of the Parameter
     *@return        The kerningOffset value
     */
    public double getKerningOffset(int code1, int code2) {
        if (kernLookup == null) {
            kernLookup = new HashMap();

            for (Iterator it = kerning.iterator(); it.hasNext(); ) {
                KerningPair pair = (KerningPair) it.next();
                Integer i1 = new Integer(pair.code1);
                Integer i2 = new Integer(pair.code2);

                HashMap kerns = (HashMap) kernLookup.get(i1);

                if (kerns == null) {
                    kerns = new HashMap();
                    kernLookup.put(i1, kerns);
                }

                kerns.put(i2, pair);
            }
        }

        Integer i1 = new Integer(code1);
        Integer i2 = new Integer(code2);

        HashMap kerns = (HashMap) kernLookup.get(i1);
        if (kerns == null) {
            return 0.0;
        }

        KerningPair pair = (KerningPair) kerns.get(i2);
        if (pair == null) {
            return 0.0;
        }

        return pair.adjustment;
    }
}
