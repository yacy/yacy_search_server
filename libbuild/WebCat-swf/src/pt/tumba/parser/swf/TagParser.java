package pt.tumba.parser.swf;

import com.anotherbigidea.flash.SWFConstants;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;
import java.util.zip.InflaterInputStream;

/**
 *  Parse Tags and drive a SWFTagTypes interface.
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class TagParser implements SWFTags, SWFConstants {
    /**
     *  Description of the Field
     */
    protected SWFTagTypes tagtypes;


    //protected byte[] contents;  //for debugging

    /**
     *  Constructor for the TagParser object
     *
     *@param  tagtypes  Description of the Parameter
     */
    public TagParser(SWFTagTypes tagtypes) {
        this.tagtypes = tagtypes;
    }


    /**
     *  Interface SWFTags
     *
     *@param  version          Description of the Parameter
     *@param  length           Description of the Parameter
     *@param  twipsWidth       Description of the Parameter
     *@param  twipsHeight      Description of the Parameter
     *@param  frameRate        Description of the Parameter
     *@param  frameCount       Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void header(int version, long length,
            int twipsWidth, int twipsHeight,
            int frameRate, int frameCount) throws IOException {
        tagtypes.header(version, length,
                twipsWidth, twipsHeight,
                frameRate, frameCount);
    }


    /**
     *  Interface SWFTags
     *
     *@param  tagType          Description of the Parameter
     *@param  longTag          Description of the Parameter
     *@param  contents         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tag(int tagType, boolean longTag, byte[] contents)
             throws IOException {
        //this.contents = contents; //for debugging

        int length = (contents != null) ? contents.length : 0;

        InStream in = (length > 0) ? new InStream(contents) : null;

        switch (tagType) {
            case TAG_END:
                tagtypes.tagEnd();
                break;
            case TAG_SHOWFRAME:
                tagtypes.tagShowFrame();
                break;
            case TAG_DEFINESHAPE:
            case TAG_DEFINESHAPE2:
            case TAG_DEFINESHAPE3:
                parseDefineShape(tagType, in);
                break;
            case TAG_DOACTION:
                parseDoAction(in);
                break;
            case TAG_FREECHARACTER:
                tagtypes.tagFreeCharacter(in.readUI16());
                break;
            case TAG_PLACEOBJECT:
                parsePlaceObject(in, length);
                break;
            case TAG_PLACEOBJECT2:
                parsePlaceObject2(in);
                break;
            case TAG_REMOVEOBJECT:
                tagtypes.tagRemoveObject(in.readUI16(), in.readUI16());
                break;
            case TAG_REMOVEOBJECT2:
                tagtypes.tagRemoveObject2(in.readUI16());
                break;
            case TAG_SETBACKGROUNDCOLOR:
                tagtypes.tagSetBackgroundColor(new Color(in));
                break;
            case TAG_FRAMELABEL:
                tagtypes.tagFrameLabel(in.readString());
                break;
            case TAG_DEFINESPRITE:
                parseDefineSprite(in);
                break;
            case TAG_PROTECT:
                tagtypes.tagProtect((length > 0) ? in.read(length) : null);
                break;
            case TAG_ENABLEDEBUG:
                tagtypes.tagEnableDebug((length > 0) ? in.read(length) : null);
                break;
            case TAG_DEFINEFONT:
                parseDefineFont(in);
                break;
            case TAG_DEFINEFONTINFO:
                parseFontInfo(in, length);
                break;
            case TAG_DEFINEFONT2:
                parseDefineFont2(in);
                break;
            case TAG_DEFINETEXTFIELD:
                parseDefineTextField(in);
                break;
            case TAG_DEFINETEXT:
            case TAG_DEFINETEXT2:
                parseDefineText(tagType, in);
                break;
            case TAG_DEFINEBUTTON:
                parseDefineButton(in);
                break;
            case TAG_DEFINEBUTTON2:
                parseDefineButton2(in);
                break;
            case TAG_DEFINEBUTTONCXFORM:
                parseButtonCXForm(in);
                break;
            case TAG_EXPORT:
                parseExport(in);
                break;
            case TAG_IMPORT:
                parseImport(in);
                break;
            case TAG_DEFINEQUICKTIMEMOVIE:
                tagtypes.tagDefineQuickTimeMovie(in.readUI16(), in.readString());
                break;
            case TAG_DEFINEBITSJPEG2:
                parseDefineJPEG2(in, length);
                break;
            case TAG_DEFINEBITSLOSSLESS:
                parseDefineBitsLossless(in, length, false);
                break;
            case TAG_DEFINEBITSLOSSLESS2:
                parseDefineBitsLossless(in, length, true);
                break;
            case TAG_DEFINEMORPHSHAPE:
                parseMorphShape(in);
                break;
            case TAG_NAMECHARACTER:
                tagtypes.tagNameCharacter(contents);
                break;
            case TAG_GENERATOR_TEXT:
                tagtypes.tagGeneratorText(contents);
                break;
            case TAG_TEMPLATECOMMAND:
                tagtypes.tagGeneratorCommand(contents);
                break;
            case TAG_FLASHGENERATOR:
                tagtypes.tagGenerator(contents);
                break;
            case TAG_GEN_EXTERNAL_FONT:
                tagtypes.tagGeneratorFont(contents);
                break;
            case TAG_SERIALNUMBER:
                tagtypes.tagSerialNumber(in.readString());
                break;
            case TAG_DEFINESOUND:
                parseDefineSound(in);
                break;
            case TAG_STARTSOUND:
                parseStartSound(in);
                break;
            case TAG_DEFINEBUTTONSOUND:
                parseDefineButtonSound(in);
                break;
            case TAG_SOUNDSTREAMHEAD:
                parseSoundStreamHead(true, in);
                break;
            case TAG_SOUNDSTREAMHEAD2:
                parseSoundStreamHead(false, in);
                break;
            case TAG_SOUNDSTREAMBLOCK:
                parseSoundStreamBlock(in);
                break;
            case TAG_DEFINEBITS:
                parseDefineBits(in);
                break;
            case TAG_JPEGTABLES:
                parseDefineJPEGTables(in);
                break;
            case TAG_DEFINEBITSJPEG3:
                parseDefineBitsJPEG3(in);
                break;
            default:
                //--Unknown Tag Type
                tagtypes.tag(tagType, longTag, contents);
                break;
        }
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseDefineSound(InStream in) throws IOException {
        int id = in.readUI16();
        int format = (int) in.readUBits(4);
        int frequency = (int) in.readUBits(2);
        boolean bits16 = in.readUBits(1) != 0;
        boolean stereo = in.readUBits(1) != 0;
        int sampleCount = (int) in.readUI32();

        byte[] soundData = in.read();

        tagtypes.tagDefineSound(id, format, frequency, bits16, stereo,
                sampleCount, soundData);
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseStartSound(InStream in) throws IOException {
        int id = in.readUI16();
        SoundInfo info = new SoundInfo(in);

        tagtypes.tagStartSound(id, info);
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseDefineButtonSound(InStream in) throws IOException {
        int id = in.readUI16();

        int rollOverSoundId = in.readUI16();
        SoundInfo rollOverSoundInfo = (rollOverSoundId == 0) ? null : new SoundInfo(in);

        int rollOutSoundId = in.readUI16();
        SoundInfo rollOutSoundInfo = (rollOutSoundId == 0) ? null : new SoundInfo(in);

        int pressSoundId = in.readUI16();
        SoundInfo pressSoundInfo = (pressSoundId == 0) ? null : new SoundInfo(in);

        int releaseSoundId = in.readUI16();
        SoundInfo releaseSoundInfo = (releaseSoundId == 0) ? null : new SoundInfo(in);

        tagtypes.tagDefineButtonSound(id,
                rollOverSoundId, rollOverSoundInfo,
                rollOutSoundId, rollOutSoundInfo,
                pressSoundId, pressSoundInfo,
                releaseSoundId, releaseSoundInfo);

    }


    /**
     *  Description of the Method
     *
     *@param  adpcmOnly        Description of the Parameter
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseSoundStreamHead(boolean adpcmOnly, InStream in) throws IOException {
        //int reserved = (int) in.readUBits(4);
        int playbackFreq = (int) in.readUBits(2);
        boolean playback16bits = (in.readUBits(1) != 0);
        boolean playbackStereo = (in.readUBits(1) != 0);

        int format = (int) in.readUBits(4);
        int streamFreq = (int) in.readUBits(2);
        boolean stream16bits = (in.readUBits(1) != 0);
        boolean streamStereo = (in.readUBits(1) != 0);
        int avgSampleCount = (int) in.readUI16();

        //--MP3 Streams sometimes have an extra word here...
        //if( format == SWFConstants.SOUND_FORMAT_MP3 )
        //{
        //    int unknown = in.readUI16();
        //}
        //--but other SDK's don't know about this so we can't assume it
        //  will always be present in a SWF.

        if (adpcmOnly) {
            tagtypes.tagSoundStreamHead(playbackFreq, playback16bits, playbackStereo,
                    format, streamFreq, stream16bits, streamStereo,
                    avgSampleCount);
        } else {
            tagtypes.tagSoundStreamHead2(playbackFreq, playback16bits, playbackStereo,
                    format, streamFreq, stream16bits, streamStereo,
                    avgSampleCount);
        }
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseSoundStreamBlock(InStream in) throws IOException {
        tagtypes.tagSoundStreamBlock(in.read());
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseDefineBits(InStream in) throws IOException {
        tagtypes.tagDefineBits(in.readUI16(), in.read());
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseDefineJPEGTables(InStream in) throws IOException {
        tagtypes.tagJPEGTables(in.read());
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseDefineBitsJPEG3(InStream in) throws IOException {
        int id = in.readUI16();
        int size = (int) in.readUI32();

        byte[] imageData = in.read(size);
        byte[] alphaData = in.read();

        tagtypes.tagDefineBitsJPEG3(id, imageData, alphaData);
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseMorphShape(InStream in) throws IOException {
        int id = in.readUI16();

        Rect startBounds = new Rect(in);
        Rect endBounds = new Rect(in);

        //int edgeOffset = (int) in.readUI32();

        SWFShape shape = tagtypes.tagDefineMorphShape(id, startBounds, endBounds);

        if (shape == null) {
            return;
        }

        //--Read the Fill Styles
        int fillCount = in.readUI8();
        if (fillCount == 0xff) {
            fillCount = in.readUI16();
        }

        for (int i = 0; i < fillCount; i++) {
            parseMorphFillStyle(in, shape);
        }

        //--Read the Line Styles
        int lineCount = in.readUI8();
        if (lineCount == 0xff) {
            lineCount = in.readUI16();
        }

        for (int i = 0; i < lineCount; i++) {
            parseMorphLineStyle(in, shape);
        }

        //--read the start shape
        parseShape(in, shape, false, true);

        //--read the end shape
        parseShape(in, shape, false, true);
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@param  shape            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseMorphLineStyle(InStream in, SWFShape shape) throws IOException {
        int startWidth = in.readUI16();
        int endWidth = in.readUI16();

        AlphaColor startColor = new AlphaColor(in);
        AlphaColor endColor = new AlphaColor(in);

        shape.defineLineStyle(startWidth, startColor);
        shape.defineLineStyle(endWidth, endColor);
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@param  shape            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseMorphFillStyle(InStream in, SWFShape shape) throws IOException {
        int fillType = in.readUI8();

        if (fillType == FILL_SOLID) {
            AlphaColor startColor = new AlphaColor(in);
            AlphaColor endColor = new AlphaColor(in);

            shape.defineFillStyle(startColor);
            shape.defineFillStyle(endColor);
        } else if (fillType == FILL_LINEAR_GRADIENT
                || fillType == FILL_RADIAL_GRADIENT) {
            Matrix startMatrix = new Matrix(in);
            Matrix endMatrix = new Matrix(in);

            int numRatios = in.readUI8();

            int[] startRatios = new int[numRatios];
            AlphaColor[] startColors = new AlphaColor[numRatios];

            int[] endRatios = new int[numRatios];
            AlphaColor[] endColors = new AlphaColor[numRatios];

            for (int i = 0; i < numRatios; i++) {
                startRatios[i] = in.readUI8();
                startColors[i] = new AlphaColor(in);
                endRatios[i] = in.readUI8();
                endColors[i] = new AlphaColor(in);
            }

            shape.defineFillStyle(startMatrix, startRatios, startColors,
                    fillType == FILL_RADIAL_GRADIENT);

            shape.defineFillStyle(endMatrix, endRatios, endColors,
                    fillType == FILL_RADIAL_GRADIENT);
        } else if (fillType == FILL_TILED_BITMAP
                || fillType == FILL_CLIPPED_BITMAP) {
            int bitmapId = in.readUI16();
            Matrix startMatrix = new Matrix(in);
            Matrix endMatrix = new Matrix(in);

            shape.defineFillStyle(bitmapId, startMatrix,
                    fillType == FILL_CLIPPED_BITMAP);

            shape.defineFillStyle(bitmapId, endMatrix,
                    fillType == FILL_CLIPPED_BITMAP);
        }
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@param  length           Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseDefineJPEG2(InStream in, int length) throws IOException {
        int id = in.readUI16();

        //--read the image data
        byte[] image = in.read(length - 2);

        tagtypes.tagDefineBitsJPEG2(id, image);
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@param  length           Description of the Parameter
     *@param  hasAlpha         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseDefineBitsLossless(InStream in, int length,
            boolean hasAlpha)
             throws IOException {
        int id = in.readUI16();
        int format = in.readUI8();
        int width = in.readUI16();
        int height = in.readUI16();

        int size = 0;

        switch (format) {
            case BITMAP_FORMAT_8_BIT:
                size = in.readUI8() + 1;
                break;
            case BITMAP_FORMAT_16_BIT:
                size = in.readUI16() + 1;
                break;
            case BITMAP_FORMAT_32_BIT:
                size = 0;
                break;
            default:
                throw new IOException("unknown bitmap format: " + format);
        }

        byte[] data = in.read(length - (int) in.getBytesRead());

        //--unzip the data
        ByteArrayInputStream bin = new ByteArrayInputStream(data);
        InflaterInputStream inflater = new InflaterInputStream(bin);
        InStream dataIn = new InStream(inflater);

        Color[] colors = hasAlpha ? new AlphaColor[size] : new Color[size];

        for (int i = 0; i < size; i++) {
            colors[i] = hasAlpha ? new AlphaColor(dataIn) : new Color(dataIn);
        }

        byte[] imageData = dataIn.read();

        if (hasAlpha) {
            tagtypes.tagDefineBitsLossless2(id, format, width, height,
                    (AlphaColor[]) colors, imageData);
        } else {
            tagtypes.tagDefineBitsLossless(id, format, width, height, colors, imageData);
        }
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseExport(InStream in) throws IOException {
        int count = in.readUI16();

        String[] exportNames = new String[count];
        int[] exportIds = new int[count];

        for (int i = 0; i < count; i++) {
            exportIds[i] = in.readUI16();
            exportNames[i] = in.readString();
        }

        tagtypes.tagExport(exportNames, exportIds);
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseImport(InStream in) throws IOException {
        String movieName = in.readString();
        int count = in.readUI16();

        String[] importNames = new String[count];
        int[] importIds = new int[count];

        for (int i = 0; i < count; i++) {
            importIds[i] = in.readUI16();
            importNames[i] = in.readString();
        }

        tagtypes.tagImport(movieName, importNames, importIds);
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseDefineButton2(InStream in) throws IOException {
        int id = in.readUI16();

        boolean trackAsMenu = (in.readUI8() != 0);

        int actionOffset = in.readUI16();
        //skip first offset

        //--Read multiple button records
        List buttonRecords = ButtonRecord2.read(in);

        SWFActions actions = tagtypes.tagDefineButton2(id, trackAsMenu, buttonRecords);

        if (actions == null) {
            return;
        }

        //--Read multiple action records
        while (actionOffset != 0) {
            actionOffset = in.readUI16();

            //--Read the condition flags for this action array
            int actionConditions = in.readUI16();

            actions.start(actionConditions);

            ActionParser parser = new ActionParser(actions);
            parser.parse(in);
        }

        actions.done();
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseButtonCXForm(InStream in) throws IOException {
        int buttonId = in.readUI16();
        ColorTransform transform = new ColorTransform(in);

        tagtypes.tagButtonCXForm(buttonId, transform);
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseDefineButton(InStream in) throws IOException {
        int id = in.readUI16();
        List buttonRecords = ButtonRecord.read(in);

        SWFActions actions = tagtypes.tagDefineButton(id, buttonRecords);

        if (actions == null) {
            return;
        }

        actions.start(0);
        //no conditions
        ActionParser parser = new ActionParser(actions);
        parser.parse(in);
        actions.done();
    }


    /**
     *  Description of the Method
     *
     *@param  type             Description of the Parameter
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseDefineText(int type, InStream in) throws IOException {
        int id = in.readUI16();
        Rect bounds = new Rect(in);
        Matrix matrix = new Matrix(in);

        SWFText text = (type == TAG_DEFINETEXT) ?
                tagtypes.tagDefineText(id, bounds, matrix) :
                tagtypes.tagDefineText2(id, bounds, matrix);

        if (text == null) {
            return;
        }

        int glyphBits = in.readUI8();
        int advanceBits = in.readUI8();

        //--Read multiple text records
        int firstByte;

        while ((firstByte = in.readUI8()) != 0) {
            if ((firstByte & 0x80) == 0) {
                //Glyph Record

                //--Get number of glyph entries
                int glyphCount = firstByte & 0x7f;

                int[] glyphs = new int[glyphCount];
                int[] advances = new int[glyphCount];

                //--Read the glyph entries
                for (int i = 0; i < glyphCount; i++) {
                    glyphs[i] = (int) in.readUBits(glyphBits);
                    advances[i] = in.readSBits(advanceBits);
                }

                text.text(glyphs, advances);
            } else {
                //Style Record

                int flags = firstByte;

                int fontId = 0;

                if ((flags & TEXT_HAS_FONT) != 0) {
                    fontId = in.readUI16();
                }

                if ((flags & TEXT_HAS_COLOR) != 0) {
                    text.color((type == TAG_DEFINETEXT2) ?
                            new AlphaColor(in) :
                            new Color(in));
                }

                if ((flags & TEXT_HAS_XOFFSET) != 0) {
                    text.setX(in.readSI16());
                }

                if ((flags & TEXT_HAS_YOFFSET) != 0) {
                    //x & y are in reverse order from flag bits

                    text.setY(in.readSI16());
                }

                if ((flags & TEXT_HAS_FONT) != 0) {
                    int textHeight = in.readUI16();

                    text.font(fontId, textHeight);
                }
            }
        }

        text.done();
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseDefineTextField(InStream in) throws IOException {
        int id = in.readUI16();

        Rect boundary = new Rect(in);

        int flags = in.readUI16();
        int fontId = in.readUI16();
        int fontSize = in.readUI16();
        AlphaColor textColor = new AlphaColor(in);

        int charLimit = ((flags & TEXTFIELD_LIMIT_CHARS) != 0) ? in.readUI16() : 0;

        int alignment = in.readUI8();
        int leftMargin = in.readUI16();
        int rightMargin = in.readUI16();
        int indentation = in.readUI16();
        int lineSpacing = in.readUI16();

        String fieldName = in.readString();
        String initialText = ((flags & TEXTFIELD_HAS_TEXT) != 0) ? in.readString() : null;

        tagtypes.tagDefineTextField(id, fieldName, initialText, boundary, flags,
                textColor, alignment, fontId, fontSize,
                charLimit, leftMargin, rightMargin,
                indentation, lineSpacing);
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseDefineFont2(InStream in) throws IOException {
        int id = in.readUI16();
        int flags = in.readUI8();
        int reservedFlags = in.readUI8(); // LANGUAGECODE, spec: SWF 5 or earlier: always 0; SWF 6 or later: language code

        int nameLength = in.readUI8();
        String name = new String(in.read(nameLength));

        int glyphCount = in.readUI16();
        Vector glyphs = new Vector();

        int[] offsets = new int[glyphCount + 1];
        boolean is32 = (flags & FONT2_32OFFSETS) != 0;
        for (int i = 0; i <= glyphCount; i++) {
            offsets[i] = is32 ? (int) in.readUI32() : in.readUI16();
        }

        for (int i = 1; i <= glyphCount; i++) {
            int glyphSize = offsets[i] - offsets[i - 1];
            byte[] glyphBytes = in.read(glyphSize);
            glyphs.addElement(glyphBytes);
        }

        boolean isWide = ((flags & FONT2_WIDECHARS) != 0) || (glyphCount > 256);

        int[] codes = new int[glyphCount];
        for (int i = 0; i < glyphCount; i++) {
            codes[i] = isWide ? in.readUI16() : in.readUI8();
        }

        int ascent = 0;
        int descent = 0;
        int leading = 0;
        int[] advances = null;
        Rect[] bounds = null;
        int[] kerningCodes1 = null;
        int[] kerningCodes2 = null;
        int[] kerningAdjustments = null;

        if ((flags & FONT2_HAS_LAYOUT) != 0) {
            ascent = in.readSI16();
            descent = in.readSI16();
            leading = in.readSI16();

            advances = new int[glyphCount];

            for (int i = 0; i < glyphCount; i++) {
                advances[i] = in.readSI16();
            }

            bounds = new Rect[glyphCount];

            for (int i = 0; i < glyphCount; i++) {
                bounds[i] = new Rect(in);
            }

            int kerningCount = in.readUI16();

            kerningCodes1 = new int[kerningCount];
            kerningCodes2 = new int[kerningCount];
            kerningAdjustments = new int[kerningCount];

            for (int i = 0; i < kerningCount; i++) {
                kerningCodes1[i] = isWide ? in.readUI16() : in.readUI8();
                kerningCodes2[i] = isWide ? in.readUI16() : in.readUI8();
                kerningAdjustments[i] = in.readSI16();
            }
        }

        SWFVectors vectors = tagtypes.tagDefineFont2(id, flags, name, glyphCount,
                ascent, descent, leading,
                codes, advances, bounds,
                kerningCodes1,
                kerningCodes2,
                kerningAdjustments);

        if (vectors == null) {
            return;
        }

        if (glyphs.isEmpty()) {
            vectors.done();
        } else {
            for (Enumeration enumerator = glyphs.elements(); enumerator.hasMoreElements(); ) {
                byte[] glyphBytes = (byte[]) enumerator.nextElement();

                InStream glyphIn = new InStream(glyphBytes);

                parseShape(glyphIn, vectors, false, false);
            }
        }
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@param  length           Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseFontInfo(InStream in, int length2) throws IOException {
        int fontId = in.readUI16();
        int length = length2;
        //--Read the font name
        int nameLength = in.readUI8();
        byte[] chars = in.read(nameLength);
        String fontName = new String(chars);

        //--Read the font flags
        int flags = in.readUI8();

        //--Adjust the body length
        length -= 4 + nameLength;

        //--Read the Glyph-to-code table
        boolean wide = (flags & FONT_WIDECHARS) != 0;

        int[] codes = new int[wide ? (length / 2) : length];

        for (int i = 0; i < codes.length; i++) {
            codes[i] = wide ? in.readUI16() : in.readUI8();
        }

        tagtypes.tagDefineFontInfo(fontId, fontName, flags, codes);
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseDefineFont(InStream in) throws IOException {
        int id = in.readUI16();
        int firstOffset = in.readUI16();
        int numGlyphs = firstOffset / 2;

        SWFVectors vectors = tagtypes.tagDefineFont(id, numGlyphs);

        if (vectors == null) {
            return;
        }

        //--Skip the offset table
        for (int i = 1; i < numGlyphs; i++) {
            in.readUI16();
        }

        for (int i = 0; i < numGlyphs; i++) {
            parseShape(in, vectors, false, false);
        }
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseDefineSprite(InStream in) throws IOException {
        int id = in.readUI16();
        //int frameCount = in.readUI16();

        SWFTagTypes sstt = tagtypes.tagDefineSprite(id);

        if (sstt == null) {
            return;
        }

        TagParser parser = new TagParser(sstt);
        SWFReader reader = new SWFReader(parser, in);
        reader.readTags();
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parsePlaceObject2(InStream in) throws IOException {
        boolean hasClipActions = in.readUBits(1) != 0;
        boolean isClipBracket = in.readUBits(1) != 0;
        boolean hasName = in.readUBits(1) != 0;
        boolean hasRatio = in.readUBits(1) != 0;
        boolean hasColorTransform = in.readUBits(1) != 0;
        boolean hasMatrix = in.readUBits(1) != 0;
        boolean hasCharacter = in.readUBits(1) != 0;
        boolean isMove = in.readUBits(1) != 0;

        int depth = in.readUI16();

        int charId = hasCharacter ? in.readUI16() : 0;
        Matrix matrix = hasMatrix ? new Matrix(in) : null;
        AlphaTransform cxform = hasColorTransform ? new AlphaTransform(in) : null;
        int ratio = hasRatio ? in.readUI16() : -1;
        int clipDepth = isClipBracket ? in.readUI16() : 0;
        String name = hasName ? in.readString() : null;

        int clipActionFlags = 0;

        if (hasClipActions) {
            in.readUI16();
            //unknown
            clipActionFlags = in.readUI16();
            //compound flags
        }

        SWFActions actions = tagtypes.tagPlaceObject2(
                isMove, clipDepth, depth, charId,
                matrix, cxform, ratio, name,
                clipActionFlags);

        if (hasClipActions && actions != null) {
            int flags = 0;

            while ((flags = in.readUI16()) != 0) {
                //int length = (int) in.readUI32();

                actions.start(flags);
                ActionParser parser = new ActionParser(actions);

                parser.parse(in);
            }

            actions.done();
        }
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@param  length           Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parsePlaceObject(InStream in, int length) throws IOException {
        tagtypes.tagPlaceObject(
                in.readUI16(),
        //char id
                in.readUI16(),
        //depth
                new Matrix(in),
                (in.getBytesRead() < length) ? new AlphaTransform(in) : null);
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseDoAction(InStream in) throws IOException {
        SWFActions actions = tagtypes.tagDoAction();

        if (actions == null) {
            return;
        }

        actions.start(0);
        //no conditions
        ActionParser parser = new ActionParser(actions);
        parser.parse(in);
        actions.done();
    }


    /**
     *  Description of the Method
     *
     *@param  type             Description of the Parameter
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseDefineShape(int type, InStream in) throws IOException {
        int id = in.readUI16();
        Rect rect = new Rect(in);

        SWFShape shape = null;

        switch (type) {
            case TAG_DEFINESHAPE:
                shape = tagtypes.tagDefineShape(id, rect);
                break;
            case TAG_DEFINESHAPE2:
                shape = tagtypes.tagDefineShape2(id, rect);
                break;
            case TAG_DEFINESHAPE3:
                shape = tagtypes.tagDefineShape3(id, rect);
                break;
            default:
                break;
        }

        if (shape == null) {
            return;
        }

        parseShape(in, shape,
                true
        /*
         *  has style
         */
                ,
                type == TAG_DEFINESHAPE3
        /*
         *  has alpha
         */
                );
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@param  vectors          Description of the Parameter
     *@param  hasStyle         Description of the Parameter
     *@param  hasAlpha         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseShape(InStream in, SWFVectors vectors,
            boolean hasStyle, boolean hasAlpha)
             throws IOException {
        SWFShape shape = (vectors instanceof SWFShape) ?
                (SWFShape) vectors :
                null;

        in.synchBits();

        if (hasStyle) {
            parseStyles(in, shape, hasAlpha);
        }

        in.synchBits();

        int[] numFillBits = new int[]{(int) in.readUBits(4)};
        int[] numLineBits = new int[]{(int) in.readUBits(4)};

        //--Read the shape records
        while (true) {
            int type = (int) in.readUBits(1);

            if (type == 1) {
                // Edge shape-record

                boolean isCurved = in.readUBits(1) == 0L;

                if (isCurved) {
                    //curve

                    int numBits = ((int) in.readUBits(4)) + 2;

                    int cx = in.readSBits(numBits);
                    int cy = in.readSBits(numBits);
                    int dx = in.readSBits(numBits);
                    int dy = in.readSBits(numBits);

                    vectors.curve(cx, cy, dx, dy);
                } else {
                    //line

                    int numBits = ((int) in.readUBits(4)) + 2;

                    boolean generalLine = in.readUBits(1) == 1;

                    int dx = 0;
                    int dy = 0;

                    if (generalLine) {
                        dx = in.readSBits(numBits);
                        dy = in.readSBits(numBits);
                    } else {
                        // a non-general line

                        boolean vertLine = in.readUBits(1) == 1;

                        if (vertLine) {
                            dy = in.readSBits(numBits);
                        } else {
                            //horizontal line

                            dx = in.readSBits(numBits);
                        }
                    }

                    vectors.line(dx, dy);
                }
            } else {
                //End of records or Change Record

                int flags = (int) in.readUBits(5);

                if (flags == 0) {
                    break;
                }
                //end of records

                parseChangeRecord(in, flags, vectors, shape, hasAlpha,
                        numFillBits, numLineBits);
            }
        }

        vectors.done();
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@param  flags            Description of the Parameter
     *@param  vectors          Description of the Parameter
     *@param  shape            Description of the Parameter
     *@param  hasAlpha         Description of the Parameter
     *@param  numFillBits      Description of the Parameter
     *@param  numLineBits      Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseChangeRecord(InStream in, int flags, SWFVectors vectors,
            SWFShape shape, boolean hasAlpha,
            int[] numFillBits2, int[] numLineBits2)
             throws IOException {
        int numFillBits[] = numFillBits2;
        int numLineBits[] = numLineBits2;
        //System.out.println( "In Change --> " + Integer.toBinaryString( flags ));

        boolean hasNewStyles = (flags & 0x10) != 0;
        boolean hasLineStyle = (flags & 0x08) != 0;
        boolean hasFillStyle1 = (flags & 0x04) != 0;
        //note reverse order
        boolean hasFillStyle0 = (flags & 0x02) != 0;
        //note reverse order
        boolean hasMoveTo = (flags & 0x01) != 0;

        if (hasMoveTo) {
            int moveBits = (int) in.readUBits(5);
            int moveX = in.readSBits(moveBits);
            int moveY = in.readSBits(moveBits);

            //System.out.println( "X=" + moveX + ", Y=" + moveY );

            vectors.move(moveX, moveY);
        }

        if (hasFillStyle0) {
            int fillStyle0 = (int) in.readUBits(numFillBits[0]);

            //System.out.println( "fill0=" + fillStyle0 );

            if (shape != null) {
                shape.setFillStyle0(fillStyle0);
            }
        }

        if (hasFillStyle1) {
            int fillStyle1 = (int) in.readUBits(numFillBits[0]);

            //System.out.println( "fill1=" + fillStyle1 );

            if (shape != null) {
                shape.setFillStyle1(fillStyle1);
            }
        }

        if (hasLineStyle) {
            int lineStyle = (int) in.readUBits(numLineBits[0]);

            //System.out.println( "line=" + lineStyle );

            if (shape != null) {
                shape.setLineStyle(lineStyle);
            }
        }

        if (hasNewStyles) {
            parseStyles(in, shape, hasAlpha);

            numFillBits[0] = (int) in.readUBits(4);
            numLineBits[0] = (int) in.readUBits(4);
        }
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@param  shape            Description of the Parameter
     *@param  hasAlpha         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parseStyles(InStream in, SWFShape shape, boolean hasAlpha)
             throws IOException {
        int numFillStyles = in.readUI8();
        if (numFillStyles == 0xff) {
            //larger number format

            numFillStyles = in.readUI16();
        }

        for (int i = 0; i < numFillStyles; i++) {
            parseFillStyle(in, shape, hasAlpha);
        }

        int numLineStyles = in.readUI8();
        if (numLineStyles == 0xff) {
            //larger number format

            numLineStyles = in.readUI16();
        }

        for (int i = 0; i < numLineStyles; i++) {
            parseLineStyle(in, shape, hasAlpha);
        }
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@param  shape            Description of the Parameter
     *@param  hasAlpha         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void parseLineStyle(InStream in, SWFShape shape, boolean hasAlpha)
             throws IOException {
        int width = in.readUI16();
        Color color = hasAlpha ? new AlphaColor(in) : new Color(in);

        if (shape != null) {
            shape.defineLineStyle(width, color);
        }
    }


    /**
     *  Description of the Method
     *
     *@param  in               Description of the Parameter
     *@param  shape            Description of the Parameter
     *@param  hasAlpha         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void parseFillStyle(InStream in, SWFShape shape, boolean hasAlpha)
             throws IOException {
        int fillType = in.readUI8();

        if (fillType == FILL_SOLID) {
            Color color = hasAlpha ? new AlphaColor(in) : new Color(in);

            if (shape != null) {
                shape.defineFillStyle(color);
            }
        } else if (fillType == FILL_LINEAR_GRADIENT
                || fillType == FILL_RADIAL_GRADIENT) {
            Matrix matrix = new Matrix(in);

            int numRatios = in.readUI8();

            int[] ratios = new int[numRatios];
            Color[] colors = new Color[numRatios];

            for (int i = 0; i < numRatios; i++) {
                ratios[i] = in.readUI8();
                colors[i] = hasAlpha ? new AlphaColor(in) : new Color(in);
            }

            if (shape != null) {
                shape.defineFillStyle(matrix, ratios, colors,
                        fillType == FILL_RADIAL_GRADIENT);
            }
        } else if (fillType == FILL_TILED_BITMAP
                || fillType == FILL_CLIPPED_BITMAP) {
            int bitmapId = in.readUI16();
            Matrix matrix = new Matrix(in);

            if (shape != null) {
                shape.defineFillStyle(bitmapId, matrix,
                        fillType == FILL_CLIPPED_BITMAP);
            }
        }
    }


    /**
     *  The main program for the TagParser class
     *
     *@param  args             The command line arguments
     *@exception  IOException  Description of the Exception
     */
    public static void main(String[] args) throws IOException {
        FileInputStream in = new FileInputStream(args[0]);
        FileOutputStream out = new FileOutputStream(args[1]);

        SWFWriter writer = new SWFWriter(out);
        TagWriter tagwtr = new TagWriter(writer);

        TagParser parser = new TagParser(tagwtr);
        SWFReader reader = new SWFReader(parser, in);

        reader.readFile();
        out.flush();
        out.close();
        in.close();
    }
}
