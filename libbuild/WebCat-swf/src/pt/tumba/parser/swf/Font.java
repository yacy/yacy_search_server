package pt.tumba.parser.swf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 *  A Font Symbol. The Font references a FontDefinition object from which it
 *  takes the glyph definitions it needs.
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class Font extends Symbol {
    /**
     *  Description of the Class
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    public class NoGlyphException extends Exception {
        /**
         *  Description of the Field
         */
        public int code;


        /**
         *  Constructor for the NoGlyphException object
         *
         *@param  code  Description of the Parameter
         */
        public NoGlyphException(int code) {
            super("The font does not have a glyph definition for code " + code);
            this.code = code;
        }
    }


    /**
     *  A set of contiguous characters in one font.
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    public class Chars {
        /**
         *  Description of the Field
         */
        protected String chars;
        /**
         *  Description of the Field
         */
        protected double size;
        /**
         *  Description of the Field
         */
        protected int[] indices;
        /**
         *  Description of the Field
         */
        protected int[] advances;

        /**
         *  Description of the Field
         */
        protected double totalAdvance;
        //total advance
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
        protected double leftMargin;
        /**
         *  Description of the Field
         */
        protected double rightMargin;


        /**
         *  Description of the Method
         *
         *@return    Description of the Return Value
         */
        public String toString() {
            return chars;
        }


        /**
         *  Gets the font attribute of the Chars object
         *
         *@return    The font value
         */
        public Font getFont() {
            return Font.this;
        }


        /**
         *  Gets the size attribute of the Chars object
         *
         *@return    The size value
         */
        public double getSize() {
            return size;
        }


        /**
         *  Gets the totalAdvance attribute of the Chars object
         *
         *@return    The totalAdvance value
         */
        public double getTotalAdvance() {
            return totalAdvance;
        }


        /**
         *  Gets the ascent attribute of the Chars object
         *
         *@return    The ascent value
         */
        public double getAscent() {
            return ascent;
        }


        /**
         *  Gets the descent attribute of the Chars object
         *
         *@return    The descent value
         */
        public double getDescent() {
            return descent;
        }


        /**
         *  The left margin is the difference between the origin of the first
         *  glyph and the left edge of its geometry
         *
         *@return    The leftMargin value
         */
        public double getLeftMargin() {
            return leftMargin;
        }


        /**
         *  The right margin is the different between the total advance and the
         *  right edge of the geometry of the last glyph
         *
         *@return    The rightMargin value
         */
        public double getRightMargin() {
            return rightMargin;
        }


        /**
         *@param  chars                 the characters to display (displayable
         *      chars only - i.e. no newlines, tabs etc..)
         *@param  size                  point-size - only relevant if font is
         *      not null
         *@exception  NoGlyphException  Description of the Exception
         */
        protected Chars(String chars, double size) throws NoGlyphException {
            this.chars = chars;
            this.size = size;
            init();
        }


        /**
         *  Description of the Method
         *
         *@exception  NoGlyphException  Description of the Exception
         */
        protected final void init() throws NoGlyphException {
            //--Figure out the indices and advances
            char[] codes = chars.toCharArray();
            indices = new int[codes.length];
            advances = new int[codes.length];

            double maxAscent = 0.0;
            double maxDescent = 0.0;

            double scale = size * SWFConstants.TWIPS / 1024.0;

            for (int i = 0; i < codes.length; i++) {
                int code = (int) codes[i];

                int[] index = new int[1];
                FontDefinition.Glyph glyph = getGlyph(code, index);

                indices[i] = index[0];

                if (glyph != null) {
                    Shape shape = glyph.getShape();

                    double[] outline = shape.getBoundingRectangle();
                    double x1 = outline[0] * scale;
                    double y1 = outline[1] * scale;
                    double x2 = outline[2] * scale;
                    double y2 = outline[3] * scale;

                    if (maxAscent < -y1) {
                        maxAscent = -y1;
                    }
                    if (maxDescent < y2) {
                        maxDescent = y2;
                    }

                    double advance = glyph.getAdvance() * scale;
                    if (advance == 0) {
                        advance = x2 - x1;
                    }

                    //Kerning adjustment
                    if (i < codes.length - 1) {
                        advance += (fontDef.getKerningOffset(code, (int) codes[i + 1]) * scale);
                    }

                    totalAdvance += advance;

                    advances[i] = (int) (advance * SWFConstants.TWIPS);

                    if (i == 0) {
                        leftMargin = -y1;
                    }
                    if (i == codes.length - 1) {
                        rightMargin = x2 - advance;
                    }
                }
            }

            ascent = fontDef.getAscent() * scale;
            if (ascent == 0.0) {
                ascent = maxAscent;
            }

            descent = fontDef.getDescent() * scale;
            if (descent == 0.0) {
                descent = maxDescent;
            }
        }
    }


    /**
     *  Description of the Field
     */
    protected Object font1Key = new Object();
    //used in movie defined symbols lookup
    /**
     *  Description of the Field
     */
    protected Object font2Key = new Object();
    //used in movie defined symbols lookup

    /**
     *  Description of the Field
     */
    protected FontDefinition fontDef;
    /**
     *  Description of the Field
     */
    protected Map glyphs = new HashMap();
    //glyphs used by this font
    /**
     *  Description of the Field
     */
    protected Map indices = new HashMap();
    //glyph indices
    /**
     *  Description of the Field
     */
    protected List glyphList = new ArrayList();


    /**
     *  Gets the definition attribute of the Font object
     *
     *@return    The definition value
     */
    public FontDefinition getDefinition() {
        return fontDef;
    }


    /**
     *  Constructor for the Font object
     *
     *@param  fontDef  Description of the Parameter
     */
    public Font(FontDefinition fontDef) {
        this.fontDef = fontDef;
    }


    /**
     *  Gets the glyphList attribute of the Font object
     *
     *@return    The glyphList value
     */
    public List getGlyphList() {
        return glyphList;
    }


    /**
     *  Load the glyphs for the characters in the given string from the
     *  FontDefinition into this font.
     *
     *@param  chars                 Description of the Parameter
     *@exception  NoGlyphException
     */
    public void loadGlyphs(String chars) throws NoGlyphException {
        char[] chs = chars.toCharArray();
        for (int i = 0; i < chs.length; i++) {
            getGlyph(chs[i], null);
        }
    }


    /**
     *  Load all glyphs from the font definition
     */
    public void loadAllGlyphs() {
        List list = fontDef.getGlyphList();

        for (Iterator it = list.iterator(); it.hasNext(); ) {
            FontDefinition.Glyph g = (FontDefinition.Glyph) it.next();

            addGlyph(g);
        }
    }


    /**
     *  Get the Chars instance for the given string at the given font size
     *
     *@param  chars                 Description of the Parameter
     *@param  fontSize              Description of the Parameter
     *@return                       Description of the Return Value
     *@exception  NoGlyphException  Description of the Exception
     */
    public Chars chars(String chars, double fontSize)
             throws NoGlyphException {
        return new Chars(chars, fontSize);
    }


    /**
     *  Gets the glyph attribute of the Font object
     *
     *@param  code                  Description of the Parameter
     *@param  index                 Description of the Parameter
     *@return                       The glyph value
     *@exception  NoGlyphException  Description of the Exception
     */
    protected FontDefinition.Glyph getGlyph(int code, int[] index2)
             throws NoGlyphException {
        int index[] = index2;
        Integer codeI = new Integer(code);
        FontDefinition.Glyph g = (FontDefinition.Glyph) glyphs.get(codeI);

        if (g != null) {
            if (index != null) {
                Integer idx = (Integer) indices.get(codeI);
                index[0] = idx.intValue();
            }

            return g;
        }

        g = fontDef.getGlyph(code);

        if (g == null) {
            throw new NoGlyphException(code);
        }

        int idx = addGlyph(g);
        if (index != null) {
            index[0] = idx;
        }

        return g;
    }


    /**
     *  Add a glyph and return the index
     *
     *@param  glyph  The feature to be added to the Glyph attribute
     *@return        Description of the Return Value
     */
    public int addGlyph(FontDefinition.Glyph glyph) {
        int idx = glyphs.size();

        if (glyph.getCode() > 0) {
            Integer codeI = new Integer(glyph.getCode());
            indices.put(codeI, new Integer(idx));
            glyphs.put(codeI, glyph);
        }

        glyphList.add(glyph);

        return idx;
    }


    /**
     *  Set the code for the glyph at the given index
     *
     *@param  index  The new code value
     *@param  code   The new code value
     */
    public void setCode(int index, int code) {
        if (index >= glyphList.size()) {
            return;
        }

        FontDefinition.Glyph g = (FontDefinition.Glyph) glyphList.get(index);
        g.setCode(code);

        Integer codeI = new Integer(code);
        indices.put(codeI, new Integer(index));
        glyphs.put(codeI, g);
    }


    /**
     *  Description of the Method
     *
     *@param  textFont         Description of the Parameter
     *@param  movie            Description of the Parameter
     *@param  tagwriter        Description of the Parameter
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    protected int define(boolean textFont, Movie movie, SWFTagTypes tagwriter)
             throws IOException {
        Integer integerId = textFont ?
                (Integer) movie.definedSymbols.get(font1Key) :
                (Integer) movie.definedSymbols.get(font2Key);

        if (integerId == null) {
            if (textFont) {
                integerId = new Integer(defineFont1(movie, tagwriter));
                movie.definedSymbols.put(font1Key, integerId);
            } else {
                integerId = new Integer(defineFont2(movie, tagwriter));
                movie.definedSymbols.put(font2Key, integerId);
            }
        }

        id = integerId.intValue();
        return id;
    }


    /**
     *  Description of the Method
     *
     *@param  movie            Description of the Parameter
     *@param  tagwriter        Description of the Parameter
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    protected int defineFont1(Movie movie, SWFTagTypes tagwriter)
             throws IOException {
        int id = getNextId(movie);

        SWFVectors vecs = tagwriter.tagDefineFont(id, glyphList.size());

        for (Iterator it = glyphList.iterator(); it.hasNext(); ) {
            FontDefinition.Glyph g = (FontDefinition.Glyph) it.next();

            Shape s = g.getShape();

            s.writeGlyph(vecs);
        }

        if (fontDef.getName() != null) {
            int flags = 0;

            if (fontDef.isUnicode()) {
                flags |= SWFConstants.FONT_UNICODE;
            }
            if (fontDef.isShiftJIS()) {
                flags |= SWFConstants.FONT_SHIFTJIS;
            }
            if (fontDef.isAnsi()) {
                flags |= SWFConstants.FONT_ANSI;
            }
            if (fontDef.isItalic()) {
                flags |= SWFConstants.FONT_ITALIC;
            }
            if (fontDef.isBold()) {
                flags |= SWFConstants.FONT_BOLD;
            }

            tagwriter.tagDefineFontInfo(id, fontDef.getName(), flags, getCodes());
        }

        return id;
    }


    /**
     *  Description of the Method
     *
     *@param  movie            Description of the Parameter
     *@param  tagwriter        Description of the Parameter
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    protected int defineFont2(Movie movie, SWFTagTypes tagwriter)
             throws IOException {
        int id = getNextId(movie);

        int glyphCount = glyphList.size();
        int[] codes = new int[glyphCount];
        Rect[] bounds = new Rect[glyphCount];
        int[] advances = new int[glyphCount];

        //--Gather glyph info
        int i = 0;
        for (Iterator it = glyphList.iterator(); it.hasNext(); ) {
            FontDefinition.Glyph g = (FontDefinition.Glyph) it.next();

            codes[i] = g.getCode();
            advances[i] = (int) (g.getAdvance() * SWFConstants.TWIPS);

            double[] bound = g.getShape().getBoundingRectangle();

            bounds[i] = new Rect((int) (bound[0] * SWFConstants.TWIPS),
                    (int) (bound[1] * SWFConstants.TWIPS),
                    (int) (bound[2] * SWFConstants.TWIPS),
                    (int) (bound[3] * SWFConstants.TWIPS));

            i++;
        }

        //--Gather kerning info
        List kerns = fontDef.getKerningPairList();
        int kernCount = kerns.size();
        int[] kern1 = new int[kernCount];
        int[] kern2 = new int[kernCount];
        int[] kernOff = new int[kernCount];

        i = 0;
        for (Iterator it = kerns.iterator(); it.hasNext(); ) {
            FontDefinition.KerningPair pair = (FontDefinition.KerningPair) it.next();

            kern1[i] = pair.getCode1();
            kern2[i] = pair.getCode2();
            kernOff[i] = (int) (pair.getAdjustment() * SWFConstants.TWIPS);

            i++;
        }

        int flags = 0;
        if (fontDef.hasMetrics()) {
            flags |= SWFConstants.FONT2_HAS_LAYOUT;
        }
        if (fontDef.isShiftJIS()) {
            flags |= SWFConstants.FONT2_SHIFTJIS;
        }
        if (fontDef.isUnicode()) {
            flags |= SWFConstants.FONT2_UNICODE;
        }
        if (fontDef.isAnsi()) {
            flags |= SWFConstants.FONT2_ANSI;
        }
        if (fontDef.isItalic()) {
            flags |= SWFConstants.FONT2_ITALIC;
        }
        if (fontDef.isBold()) {
            flags |= SWFConstants.FONT2_BOLD;
        }

        SWFVectors vecs = tagwriter.tagDefineFont2(
                id, flags, fontDef.getName(), glyphCount,
                (int) (fontDef.getAscent() * SWFConstants.TWIPS),
                (int) (fontDef.getDescent() * SWFConstants.TWIPS),
                (int) (fontDef.getLeading() * SWFConstants.TWIPS),
                codes, advances, bounds, kern1, kern2, kernOff);

        for (Iterator it = glyphList.iterator(); it.hasNext(); ) {
            FontDefinition.Glyph g = (FontDefinition.Glyph) it.next();

            Shape s = g.getShape();

            s.writeGlyph(vecs);
        }

        return id;
    }


    /**
     *  Get the codes of the current set of glyphs
     *
     *@return    The codes value
     */
    protected int[] getCodes() {
        int[] codes = new int[glyphList.size()];

        for (int i = 0; i < codes.length; i++) {
            FontDefinition.Glyph g = (FontDefinition.Glyph) glyphList.get(i);
            codes[i] = g.getCode();
        }

        return codes;
    }


    /**
     *  Description of the Method
     *
     *@param  movie             Description of the Parameter
     *@param  timelineWriter    Description of the Parameter
     *@param  definitionwriter  Description of the Parameter
     *@return                   Description of the Return Value
     *@exception  IOException   Description of the Exception
     */
    protected int defineSymbol(Movie movie,
            SWFTagTypes timelineWriter,
            SWFTagTypes definitionwriter) throws IOException {
        return id;
    }
}
