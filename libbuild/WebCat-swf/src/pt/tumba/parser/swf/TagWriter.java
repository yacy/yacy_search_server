package pt.tumba.parser.swf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.zip.DeflaterOutputStream;

/**
 *  A writer that implements the SWFTagTypes interface and writes to a SWFTags
 *  interface
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class TagWriter implements SWFTagTypes, SWFConstants {
    /**
     *  Description of the Field
     */
    protected SWFTags tags;

    /**
     *  Description of the Field
     */
    protected OutStream out;
    /**
     *  Description of the Field
     */
    protected ByteArrayOutputStream bytes;
    /**
     *  Description of the Field
     */
    protected int tagType;
    /**
     *  Description of the Field
     */
    protected boolean longTag;
    /**
     *  Description of the Field
     */
    protected int version;


    /**
     *  Constructor for the TagWriter object
     *
     *@param  tags  Description of the Parameter
     */
    public TagWriter(SWFTags tags) {
        this.tags = tags;
    }


    /**
     *  Gets the outStream attribute of the TagWriter object
     *
     *@return    The outStream value
     */
    protected OutStream getOutStream() {
        return out;
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    protected SWFActions factorySWFActions() {
        return new ActionWriter(this, version);
    }


    /**
     *  Description of the Method
     *
     *@param  hasAlpha  Description of the Parameter
     *@param  hasStyle  Description of the Parameter
     *@return           Description of the Return Value
     */
    protected SWFShape factorySWFShape(boolean hasAlpha, boolean hasStyle) {
        return new SWFShapeImpl(this, hasAlpha, hasStyle);
    }


    /**
     *  Start a new tag context
     *
     *@param  tagType  Description of the Parameter
     *@param  longTag  Description of the Parameter
     */
    protected void startTag(int tagType, boolean longTag) {
        this.tagType = tagType;
        this.longTag = longTag;

        bytes = new ByteArrayOutputStream(10000);
        out = new OutStream(bytes);
    }


    /**
     *  Start a new definition tag context
     *
     *@param  tagType          Description of the Parameter
     *@param  id               Description of the Parameter
     *@param  longTag          Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void startTag(int tagType, int id, boolean longTag)
             throws IOException {
        startTag(tagType, longTag);
        out.writeUI16(id);
    }


    /**
     *  Finish the tag context and write the tag
     *
     *@exception  IOException  Description of the Exception
     */
    protected void completeTag() throws IOException {
        out.flush();
        byte[] contents = bytes.toByteArray();

        out = null;
        bytes = null;

        tags.tag(tagType, longTag, contents);
    }


    /**
     *  SWFTags interface
     *
     *@param  tagType          Description of the Parameter
     *@param  longTag          Description of the Parameter
     *@param  contents         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tag(int tagType, boolean longTag, byte[] contents)
             throws IOException {
        tags.tag(tagType, longTag, contents);
    }


    /**
     *  SWFHeader interface. Sets movie length to -1 to force a recalculation
     *  since the length cannot be guaranteed to be the same as the original.
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
        this.version = version;
        tags.header(version, -1, twipsWidth, twipsHeight, frameRate, frameCount);
    }


    /**
     *  SWFTagTypes interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void tagEnd() throws IOException {
        tags.tag(TAG_END, false, null);
    }


    /**
     *  SWFTagTypes interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void tagShowFrame() throws IOException {
        tags.tag(TAG_SHOWFRAME, false, null);
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@param  format           Description of the Parameter
     *@param  frequency        Description of the Parameter
     *@param  bits16           Description of the Parameter
     *@param  stereo           Description of the Parameter
     *@param  sampleCount      Description of the Parameter
     *@param  soundData        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineSound(int id, int format, int frequency,
            boolean bits16, boolean stereo,
            int sampleCount, byte[] soundData)
             throws IOException {
        startTag(TAG_DEFINESOUND, id, true);
        out.writeUBits(4, format);
        out.writeUBits(2, frequency);
        out.writeUBits(1, bits16 ? 1 : 0);
        out.writeUBits(1, stereo ? 1 : 0);
        out.writeUI32(sampleCount);
        out.write(soundData);
        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  buttonId           Description of the Parameter
     *@param  rollOverSoundId    Description of the Parameter
     *@param  rollOverSoundInfo  Description of the Parameter
     *@param  rollOutSoundId     Description of the Parameter
     *@param  rollOutSoundInfo   Description of the Parameter
     *@param  pressSoundId       Description of the Parameter
     *@param  pressSoundInfo     Description of the Parameter
     *@param  releaseSoundId     Description of the Parameter
     *@param  releaseSoundInfo   Description of the Parameter
     *@exception  IOException    Description of the Exception
     */
    public void tagDefineButtonSound(int buttonId,
            int rollOverSoundId, SoundInfo rollOverSoundInfo,
            int rollOutSoundId, SoundInfo rollOutSoundInfo,
            int pressSoundId, SoundInfo pressSoundInfo,
            int releaseSoundId, SoundInfo releaseSoundInfo)
             throws IOException {
        startTag(TAG_DEFINEBUTTONSOUND, buttonId, true);

        out.writeUI16(rollOverSoundId);
        if (rollOverSoundId != 0) {
            rollOverSoundInfo.write(out);
        }

        out.writeUI16(rollOutSoundId);
        if (rollOutSoundId != 0) {
            rollOutSoundInfo.write(out);
        }

        out.writeUI16(pressSoundId);
        if (pressSoundId != 0) {
            pressSoundInfo.write(out);
        }
        out.writeUI16(releaseSoundId);
        if (releaseSoundId != 0) {
            releaseSoundInfo.write(out);
        }

        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  soundId          Description of the Parameter
     *@param  info             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagStartSound(int soundId, SoundInfo info) throws IOException {
        startTag(TAG_STARTSOUND, soundId, false);
        info.write(out);
        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  playbackFrequency   Description of the Parameter
     *@param  playback16bit       Description of the Parameter
     *@param  playbackStereo      Description of the Parameter
     *@param  streamFormat        Description of the Parameter
     *@param  streamFrequency     Description of the Parameter
     *@param  stream16bit         Description of the Parameter
     *@param  streamStereo        Description of the Parameter
     *@param  averageSampleCount  Description of the Parameter
     *@exception  IOException     Description of the Exception
     */
    public void tagSoundStreamHead(
            int playbackFrequency, boolean playback16bit, boolean playbackStereo,
            int streamFormat, int streamFrequency, boolean stream16bit, boolean streamStereo,
            int averageSampleCount) throws IOException {
        writeSoundStreamHead(TAG_SOUNDSTREAMHEAD,
                playbackFrequency, playback16bit, playbackStereo,
                streamFormat, streamFrequency, stream16bit, streamStereo,
                averageSampleCount);
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  playbackFrequency   Description of the Parameter
     *@param  playback16bit       Description of the Parameter
     *@param  playbackStereo      Description of the Parameter
     *@param  streamFormat        Description of the Parameter
     *@param  streamFrequency     Description of the Parameter
     *@param  stream16bit         Description of the Parameter
     *@param  streamStereo        Description of the Parameter
     *@param  averageSampleCount  Description of the Parameter
     *@exception  IOException     Description of the Exception
     */
    public void tagSoundStreamHead2(
            int playbackFrequency, boolean playback16bit, boolean playbackStereo,
            int streamFormat, int streamFrequency, boolean stream16bit, boolean streamStereo,
            int averageSampleCount) throws IOException {
        writeSoundStreamHead(TAG_SOUNDSTREAMHEAD2,
                playbackFrequency, playback16bit, playbackStereo,
                streamFormat, streamFrequency, stream16bit, streamStereo,
                averageSampleCount);
    }


    /**
     *  Description of the Method
     *
     *@param  tag                 Description of the Parameter
     *@param  playbackFrequency   Description of the Parameter
     *@param  playback16bits      Description of the Parameter
     *@param  playbackStereo      Description of the Parameter
     *@param  streamFormat        Description of the Parameter
     *@param  streamFrequency     Description of the Parameter
     *@param  stream16bits        Description of the Parameter
     *@param  streamStereo        Description of the Parameter
     *@param  averageSampleCount  Description of the Parameter
     *@exception  IOException     Description of the Exception
     */
    public void writeSoundStreamHead(int tag,
            int playbackFrequency, boolean playback16bits, boolean playbackStereo,
            int streamFormat, int streamFrequency, boolean stream16bits, boolean streamStereo,
            int averageSampleCount) throws IOException {
        startTag(tag, false);

        out.writeUBits(4, 0);
        out.writeUBits(2, playbackFrequency);
        out.writeUBits(1, playback16bits ? 1 : 0);
        out.writeUBits(1, playbackStereo ? 1 : 0);

        out.writeUBits(4, streamFormat);
        out.writeUBits(2, streamFrequency);
        out.writeUBits(1, stream16bits ? 1 : 0);
        out.writeUBits(1, streamStereo ? 1 : 0);
        out.writeUI16(averageSampleCount);

        if (streamFormat == SWFConstants.SOUND_FORMAT_MP3) {
            out.writeUI16(0);
            //unknown
        }

        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  soundData        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagSoundStreamBlock(byte[] soundData) throws IOException {
        startTag(TAG_SOUNDSTREAMBLOCK, true);
        out.write(soundData);
        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  serialNumber     Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagSerialNumber(String serialNumber) throws IOException {
        startTag(TAG_SERIALNUMBER, false);
        out.writeString(serialNumber);
        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  data             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagGenerator(byte[] data) throws IOException {
        startTag(SWFConstants.TAG_FLASHGENERATOR, false);
        out.write(data);
        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  data             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagGeneratorText(byte[] data) throws IOException {
        startTag(SWFConstants.TAG_GENERATOR_TEXT, false);
        out.write(data);
        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  data             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagGeneratorCommand(byte[] data) throws IOException {
        startTag(SWFConstants.TAG_TEMPLATECOMMAND, false);
        out.write(data);
        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  data             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagGeneratorFont(byte[] data) throws IOException {
        startTag(SWFConstants.TAG_GEN_EXTERNAL_FONT, false);
        out.write(data);
        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  data             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagNameCharacter(byte[] data) throws IOException {
        startTag(SWFConstants.TAG_NAMECHARACTER, false);
        out.write(data);
        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@param  imageData        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineBits(int id, byte[] imageData) throws IOException {
        startTag(SWFConstants.TAG_DEFINEBITS, id, true);
        out.write(imageData);
        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  jpegEncodingData  Description of the Parameter
     *@exception  IOException   Description of the Exception
     */
    public void tagJPEGTables(byte[] jpegEncodingData) throws IOException {
        startTag(SWFConstants.TAG_JPEGTABLES, true);
        out.write(jpegEncodingData);
        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@param  imageData        Description of the Parameter
     *@param  alphaData        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineBitsJPEG3(int id, byte[] imageData, byte[] alphaData) throws IOException {
        startTag(SWFConstants.TAG_DEFINEBITSJPEG3, id, true);
        out.writeUI32(imageData.length);
        out.write(imageData);
        out.write(alphaData);
        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    public SWFActions tagDoAction() throws IOException {
        startTag(TAG_DOACTION, true);
        return factorySWFActions();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@param  outline          Description of the Parameter
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    public SWFShape tagDefineShape(int id, Rect outline) throws IOException {
        startShape(TAG_DEFINESHAPE, id, outline);
        return factorySWFShape(false, true);
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@param  outline          Description of the Parameter
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    public SWFShape tagDefineShape2(int id, Rect outline) throws IOException {
        startShape(TAG_DEFINESHAPE2, id, outline);
        return factorySWFShape(false, true);
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@param  outline          Description of the Parameter
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    public SWFShape tagDefineShape3(int id, Rect outline) throws IOException {
        startShape(TAG_DEFINESHAPE3, id, outline);
        return factorySWFShape(true, true);
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  charId           Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagFreeCharacter(int charId) throws IOException {
        startTag(TAG_FREECHARACTER, false);
        out.writeUI16(charId);
        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  charId           Description of the Parameter
     *@param  depth            Description of the Parameter
     *@param  matrix           Description of the Parameter
     *@param  cxform           Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagPlaceObject(int charId, int depth,
            Matrix matrix, AlphaTransform cxform)
             throws IOException {
        startTag(TAG_PLACEOBJECT, false);
        out.writeUI16(charId);
        out.writeUI16(depth);
        matrix.write(out);
        if (cxform != null) {
            cxform.write(out);
        }
        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  isMove           Description of the Parameter
     *@param  clipDepth        Description of the Parameter
     *@param  depth            Description of the Parameter
     *@param  charId           Description of the Parameter
     *@param  matrix           Description of the Parameter
     *@param  cxform           Description of the Parameter
     *@param  ratio            Description of the Parameter
     *@param  name             Description of the Parameter
     *@param  clipActionFlags  Description of the Parameter
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    public SWFActions tagPlaceObject2(boolean isMove,
            int clipDepth,
            int depth,
            int charId,
            Matrix matrix,
            AlphaTransform cxform,
            int ratio,
            String name,
            int clipActionFlags)
             throws IOException {
        boolean hasClipActions = (clipActionFlags != 0);

        startTag(TAG_PLACEOBJECT2, false);

        out.writeUBits(1, hasClipActions ? 1 : 0);
        out.writeUBits(1, (clipDepth > 0) ? 1 : 0);
        out.writeUBits(1, (name != null) ? 1 : 0);
        out.writeUBits(1, (ratio >= 0) ? 1 : 0);
        out.writeUBits(1, (cxform != null) ? 1 : 0);
        out.writeUBits(1, (matrix != null) ? 1 : 0);
        out.writeUBits(1, (charId > 0) ? 1 : 0);
        out.writeUBits(1, isMove ? 1 : 0);

        out.writeUI16(depth);

        if (charId > 0) {
            out.writeUI16(charId);
        }
        if (matrix != null) {
            matrix.write(out);
        }
        if (cxform != null) {
            cxform.write(out);
        }
        if (ratio >= 0) {
            out.writeUI16(ratio);
        }
        if (clipDepth > 0) {
            out.writeUI16(clipDepth);
        }

        if (name != null) {
            out.writeString(name);
            longTag = true;
        }

        if (hasClipActions) {
            out.writeUI16(0);
            //unknown
            out.writeUI16(clipActionFlags);

            return
                new ActionWriter(this, version) {
                    public void start(int conditions) throws IOException {
                        super.start(conditions);
                        tagWriter.out.writeUI16(conditions);
                    }


                    protected void writeBytes(byte[] bytes) throws IOException {
                        tagWriter.out.writeUI32(bytes.length);
                        super.writeBytes(bytes);
                    }


                    public void done() throws IOException {
                        tagWriter.out.writeUI16(0);
                        super.done();
                    }
                };
        }

        completeTag();
        return null;
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  charId           Description of the Parameter
     *@param  depth            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagRemoveObject(int charId, int depth) throws IOException {
        startTag(TAG_REMOVEOBJECT, false);
        out.writeUI16(charId);
        out.writeUI16(depth);
        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  depth            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagRemoveObject2(int depth) throws IOException {
        startTag(TAG_REMOVEOBJECT2, false);
        out.writeUI16(depth);
        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  color            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagSetBackgroundColor(Color color) throws IOException {
        startTag(TAG_SETBACKGROUNDCOLOR, false);
        color.writeRGB(out);
        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  label            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagFrameLabel(String label) throws IOException {
        startTag(TAG_FRAMELABEL, true);
        out.writeString(label);
        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    public SWFTagTypes tagDefineSprite(int id) throws IOException {
        startTag(TAG_DEFINESPRITE, id, true);

        out.writeUI16(0);
        //framecount - to be filled in later

        TagWriter writer = new TagWriter(new SpriteTags());
        writer.version = version;

        return writer;
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  password         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagProtect(byte[] password) throws IOException {
        tags.tag(TAG_PROTECT, false, password);
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  password         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagEnableDebug(byte[] password) throws IOException {
        tags.tag(TAG_ENABLEDEBUG, false, password);
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@param  numGlyphs        Description of the Parameter
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    public SWFVectors tagDefineFont(int id, int numGlyphs) throws IOException {
        startTag(TAG_DEFINEFONT, id, true);

        return new SWFShapeImpl(this, numGlyphs);
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  fontId           Description of the Parameter
     *@param  fontName         Description of the Parameter
     *@param  flags            Description of the Parameter
     *@param  codes            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineFontInfo(int fontId, String fontName, int flags, int[] codes)
             throws IOException {
        startTag(TAG_DEFINEFONTINFO, true);
        out.writeUI16(fontId);

        byte[] chars = fontName.getBytes();
        out.writeUI8(chars.length);
        out.write(chars);

        out.writeUI8(flags);

        boolean wide = (flags & FONT_WIDECHARS) != 0;

        for (int i = 0; i < codes.length; i++) {
            if (wide) {
                out.writeUI16(codes[i]);
            } else {
                out.writeUI8(codes[i]);
            }
        }

        completeTag();
    }


    /**
     *  SWFTagTypes interface
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
        startTag(TAG_DEFINEFONT2, id, true);

        out.writeUI8(flags);
        out.writeUI8(0);
        //reserved flags

        byte[] nameBytes = name.getBytes();
        out.writeUI8(nameBytes.length);
        out.write(nameBytes);
        out.writeUI16(numGlyphs);

        return new Font2ShapeImpl(this, flags, numGlyphs, ascent, descent, leading,
                codes, advances, bounds,
                kernCodes1, kernCodes2, kernAdjustments);
    }


    /**
     *  SWFTagTypes interface
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
            String initialText, Rect boundary, int flags2,
            AlphaColor textColor, int alignment, int fontId, int fontSize,
            int charLimit, int leftMargin, int rightMargin, int indentation,
            int lineSpacing)
             throws IOException {
        int flags = flags2 | 0x2005;

        startTag(TAG_DEFINETEXTFIELD, fieldId, true);

        boundary.write(out);

        out.writeUI16(flags);
        out.writeUI16(fontId);
        out.writeUI16(fontSize);
        textColor.write(out);

        if ((flags & TEXTFIELD_LIMIT_CHARS) != 0) {
            out.writeUI16(charLimit);
        }

        out.writeUI8(alignment);
        out.writeUI16(leftMargin);
        out.writeUI16(rightMargin);
        out.writeUI16(indentation);
        out.writeUI16(lineSpacing);

        out.writeString(fieldName);

        if ((flags & TEXTFIELD_HAS_TEXT) != 0) {
            out.writeString(initialText);
        }

        completeTag();
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
        startTag(TAG_DEFINETEXT, id, true);
        return defineText(bounds, matrix, false);
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
        startTag(TAG_DEFINETEXT2, id, true);
        return defineText(bounds, matrix, true);
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@param  buttonRecords    Description of the Parameter
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    public SWFActions tagDefineButton(int id, List buttonRecords)
             throws IOException {
        startTag(TAG_DEFINEBUTTON, id, true);

        ButtonRecord.write(out, buttonRecords);
        System.out.println("BUTTON");
        return new ActionWriter(this, version);
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  buttonId         Description of the Parameter
     *@param  transform        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagButtonCXForm(int buttonId, ColorTransform transform)
             throws IOException {
        startTag(TAG_DEFINEBUTTONCXFORM, buttonId, false);
        transform.writeWithoutAlpha(out);
        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@param  trackAsMenu      Description of the Parameter
     *@param  buttonRecord2s   Description of the Parameter
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    public SWFActions tagDefineButton2(int id,
            boolean trackAsMenu,
            List buttonRecord2s)
             throws IOException {
        startTag(TAG_DEFINEBUTTON2, id, true);
        out.writeUI8(trackAsMenu ? 1 : 0);

        return new ButtonActionWriter(this, version, buttonRecord2s);
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  names            Description of the Parameter
     *@param  ids              Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagExport(String[] names, int[] ids) throws IOException {
        startTag(TAG_EXPORT, true);

        int count = ids.length;

        out.writeUI16(count);

        for (int i = 0; i < count; i++) {
            //System.out.println( "Exporting " + ids[i] + " as " + names[i] );
            out.writeUI16(ids[i]);
            out.writeString(names[i]);
        }

        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  movieName        Description of the Parameter
     *@param  names            Description of the Parameter
     *@param  ids              Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagImport(String movieName, String[] names, int[] ids)
             throws IOException {
        startTag(TAG_IMPORT, true);

        int count = ids.length;

        out.writeString(movieName);
        out.writeUI16(count);

        for (int i = 0; i < count; i++) {
            //System.out.println( "Importing " + names[i] + " as " + ids[i] + " from " + movieName );
            out.writeUI16(ids[i]);
            out.writeString(names[i]);
        }

        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@param  filename         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineQuickTimeMovie(int id, String filename) throws IOException {
        startTag(TAG_DEFINEQUICKTIMEMOVIE, id, true);
        out.writeString(filename);
        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@param  data             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineBitsJPEG2(int id, byte[] data) throws IOException {
        startTag(TAG_DEFINEBITSJPEG2, id, true);
        out.write(data);
        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@param  format           Description of the Parameter
     *@param  width            Description of the Parameter
     *@param  height           Description of the Parameter
     *@param  colors           Description of the Parameter
     *@param  imageData        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineBitsLossless(int id, int format, int width, int height,
            Color[] colors, byte[] imageData)
             throws IOException {
        writeBitsLossless(id, format, width, height, colors, imageData, false);
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@param  format           Description of the Parameter
     *@param  width            Description of the Parameter
     *@param  height           Description of the Parameter
     *@param  colors           Description of the Parameter
     *@param  imageData        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineBitsLossless2(int id, int format, int width, int height,
            Color[] colors, byte[] imageData)
             throws IOException {
        writeBitsLossless(id, format, width, height, colors, imageData, true);
    }


    /**
     *  Description of the Method
     *
     *@param  id               Description of the Parameter
     *@param  format           Description of the Parameter
     *@param  width            Description of the Parameter
     *@param  height           Description of the Parameter
     *@param  colors           Description of the Parameter
     *@param  imageData        Description of the Parameter
     *@param  hasAlpha         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void writeBitsLossless(int id, int format, int width, int height,
            Color[] colors, byte[] imageData, boolean hasAlpha)
             throws IOException {
        startTag(hasAlpha ? TAG_DEFINEBITSLOSSLESS2 : TAG_DEFINEBITSLOSSLESS,
                id, true);

        out.writeUI8(format);
        out.writeUI16(width);
        out.writeUI16(height);

        switch (format) {
            case BITMAP_FORMAT_8_BIT:
                out.writeUI8(colors.length - 1);
                break;
            case BITMAP_FORMAT_16_BIT:
                out.writeUI16(colors.length - 1);
                break;
            case BITMAP_FORMAT_32_BIT:
                break;
            default:
                throw new IOException("unknown bitmap format: " + format);
        }

        //--zip up the colors and the bitmap data
        DeflaterOutputStream deflater = new DeflaterOutputStream(bytes);
        OutStream zipOut = new OutStream(deflater);

        if (format == BITMAP_FORMAT_8_BIT || format == BITMAP_FORMAT_16_BIT) {
            for (int i = 0; i < colors.length; i++) {
                if (hasAlpha) {
                    colors[i].writeWithAlpha(zipOut);
                } else {
                    colors[i].writeRGB(zipOut);
                }
            }
        }
        zipOut.write(imageData);
        zipOut.flush();
        deflater.finish();

        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@param  jpegImage        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineBitsJPEG2(int id, InputStream jpegImage) throws IOException {
        startTag(TAG_DEFINEBITSJPEG2, id, true);

        //--Write stream terminator/header
        out.writeUI8(0xff);
        out.writeUI8(0xd9);
        out.writeUI8(0xff);
        out.writeUI8(0xd8);

        int read = 0;
        byte[] bytes = new byte[10000];

        while ((read = jpegImage.read(bytes)) >= 0) {
            out.write(bytes, 0, read);
        }

        jpegImage.close();
        completeTag();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@param  startBounds      Description of the Parameter
     *@param  endBounds        Description of the Parameter
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    public SWFShape tagDefineMorphShape(int id, Rect startBounds, Rect endBounds)
             throws IOException {
        startTag(TAG_DEFINEMORPHSHAPE, id, true);
        startBounds.write(out);
        endBounds.write(out);
        return new MorphShapeImpl(this);
    }


    //-----------------------------------------------------------------------

    /**
     *  Description of the Class
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    protected static class ButtonActionWriter extends ActionWriter {
        //offsets is a vector of int[]{ offsetPtr, offsetValue }
        /**
         *  Description of the Field
         */
        protected List offsets = new Vector();
        /**
         *  Description of the Field
         */
        protected int lastPtr;
        /**
         *  Description of the Field
         */
        protected OutStream tagout;


        /**
         *  Constructor for the ButtonActionWriter object
         *
         *@param  tagWriter        Description of the Parameter
         *@param  flashVersion     Description of the Parameter
         *@param  buttonRecs       Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        public ButtonActionWriter(TagWriter tagWriter,
                int flashVersion,
                List buttonRecs)
                 throws IOException {
            super(tagWriter, flashVersion);

            tagout = tagWriter.getOutStream();

            //--save ptr to first offset location
            lastPtr = (int) tagout.getBytesWritten();

            tagout.writeUI16(0);
            //will be calculated later

            //--write the button records
            ButtonRecord2.write(tagout, buttonRecs);
        }


        /**
         *  Description of the Method
         *
         *@param  conditions       Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        public void start(int conditions) throws IOException {
            super.start(conditions);

            //--save ptr to offset location and offset value
            int ptr = (int) tagout.getBytesWritten();
            int offset = ptr - lastPtr;

            offsets.add(new int[]{lastPtr, offset});

            lastPtr = ptr;

            tagout.writeUI16(0);
            //will be calculated later
            tagout.writeUI16(conditions);
        }


        /**
         *  Description of the Method
         *
         *@exception  IOException  Description of the Exception
         */
        public void done() throws IOException {
            //--save last offset
            offsets.add(new int[]{lastPtr, 0});

            tagout.flush();
            byte[] contents = tagWriter.bytes.toByteArray();

            tagWriter.out = null;
            tagWriter.bytes = null;

            //--fix the offsets
            for (Iterator enumerator = offsets.iterator(); enumerator.hasNext(); ) {
                int[] offInfo = (int[]) enumerator.next();
                int ptr = offInfo[0];
                int off = offInfo[1];

                byte[] offbytes = OutStream.uintTo2Bytes(off);
                contents[ptr] = offbytes[0];
                contents[ptr + 1] = offbytes[1];
            }

            tagWriter.tags.tag(tagWriter.tagType, true, contents);
        }
    }


    /**
     *  Description of the Method
     *
     *@param  bounds           Description of the Parameter
     *@param  matrix           Description of the Parameter
     *@param  hasAlpha         Description of the Parameter
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    protected SWFText defineText(Rect bounds, Matrix matrix, boolean hasAlpha)
             throws IOException {
        bounds.write(out);
        matrix.write(out);

        return new SWFTextImpl(hasAlpha);
    }


    /**
     *  Description of the Class
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    protected class SWFTextImpl implements SWFText {
        /**
         *  Description of the Field
         */
        protected boolean hasAlpha;

        /**
         *  Description of the Field
         */
        protected int maxGlyphIndex = 0;
        /**
         *  Description of the Field
         */
        protected int maxAdvance = 0;

        // Text records are stored as
        // Object[] { int[]{ font, height }, int[]{ x }, int[]{ y }, Color }, or
        // Object[] { int[] glyphs, int[] advances }
        /**
         *  Description of the Field
         */
        protected List recs = new Vector();
        /**
         *  Description of the Field
         */
        protected Object[] currentStyleRecord = null;


        /**
         *  Constructor for the SWFTextImpl object
         *
         *@param  hasAlpha  Description of the Parameter
         */
        public SWFTextImpl(boolean hasAlpha) {
            this.hasAlpha = hasAlpha;
        }


        /**
         *  Gets the currentStyle attribute of the SWFTextImpl object
         *
         *@return    The currentStyle value
         */
        protected Object[] getCurrentStyle() {
            if (currentStyleRecord == null) {
                currentStyleRecord = new Object[4];
                recs.add(currentStyleRecord);
            }

            return currentStyleRecord;
        }


        /**
         *  SWFText interface
         *
         *@param  fontId           Description of the Parameter
         *@param  textHeight       Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        public void font(int fontId, int textHeight) throws IOException {
            getCurrentStyle()[0] = new int[]{fontId, textHeight};
        }


        /**
         *  SWFText interface
         *
         *@param  color            Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        public void color(Color color) throws IOException {
            getCurrentStyle()[3] = color;
        }


        /**
         *  SWFText interface
         *
         *@param  x                The new x value
         *@exception  IOException  Description of the Exception
         */
        public void setX(int x) throws IOException {
            getCurrentStyle()[1] = new int[]{x};
        }


        /**
         *  SWFText interface
         *
         *@param  y                The new y value
         *@exception  IOException  Description of the Exception
         */
        public void setY(int y) throws IOException {
            getCurrentStyle()[2] = new int[]{y};
        }


        /**
         *  SWFText interface
         *
         *@param  glyphIndices     Description of the Parameter
         *@param  glyphAdvances    Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        public void text(int[] glyphIndices, int[] glyphAdvances) throws IOException {
            currentStyleRecord = null;
            recs.add(new Object[]{glyphIndices, glyphAdvances});

            for (int i = 0; i < glyphIndices.length; i++) {
                if (glyphIndices[i] > maxGlyphIndex) {
                    maxGlyphIndex = glyphIndices[i];
                }
                if (glyphAdvances[i] > maxAdvance) {
                    maxAdvance = glyphAdvances[i];
                }
            }
        }


        /**
         *  SWFText interface
         *
         *@exception  IOException  Description of the Exception
         */
        public void done() throws IOException {
            int glyphBits = OutStream.determineUnsignedBitSize(maxGlyphIndex);
            int advanceBits = OutStream.determineSignedBitSize(maxAdvance);

            out.writeUI8(glyphBits);
            out.writeUI8(advanceBits);

            for (Iterator enumerator = recs.iterator(); enumerator.hasNext(); ) {
                Object[] rec = (Object[]) enumerator.next();

                if (rec.length == 4) {
                    //style record

                    boolean hasFont = rec[0] != null;
                    boolean hasX = rec[1] != null;
                    boolean hasY = rec[2] != null;
                    boolean hasColor = rec[3] != null;

                    int flags = 0x80;
                    if (hasFont) {
                        flags |= TEXT_HAS_FONT;
                    }
                    if (hasX) {
                        flags |= TEXT_HAS_XOFFSET;
                    }
                    if (hasY) {
                        flags |= TEXT_HAS_YOFFSET;
                    }
                    if (hasColor) {
                        flags |= TEXT_HAS_COLOR;
                    }

                    out.writeUI8(flags);

                    if (hasFont) {
                        out.writeUI16(((int[]) rec[0])[0]);
                        //fontId
                    }

                    if (hasColor) {
                        Color color = (Color) rec[3];

                        if (hasAlpha) {
                            color.writeWithAlpha(out);
                        } else {
                            color.writeRGB(out);
                        }
                    }

                    if (hasX) {
                        int xOffset = ((int[]) rec[1])[0];
                        out.writeSI16((short) xOffset);
                    }

                    if (hasY) {
                        //x & y are in reverse order from flag bits

                        int yOffset = ((int[]) rec[2])[0];
                        out.writeSI16((short) yOffset);
                    }

                    if (hasFont) {
                        out.writeUI16(((int[]) rec[0])[1]);
                        //textHeight
                    }
                } else {
                    //glyph record

                    int[] glyphs = (int[]) rec[0];
                    int[] advances = (int[]) rec[1];

                    out.writeUI8(glyphs.length);

                    for (int i = 0; i < glyphs.length; i++) {
                        out.writeUBits(glyphBits, glyphs[i]);
                        out.writeSBits(advanceBits, advances[i]);
                    }
                }
            }

            out.writeUI8(0);
            //record terminator

            completeTag();
        }
    }


    /**
     *  Description of the Class
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    protected class SpriteTags implements SWFTags {
        /**
         *  Description of the Field
         */
        protected int frameCount = 0;


        /**
         *  Description of the Method
         *
         *@param  tagType2         Description of the Parameter
         *@param  longTag2         Description of the Parameter
         *@param  contents         Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        public void tag(int tagType2, boolean longTag3,
                byte[] contents2) throws IOException {
            byte[] contents = contents2;
            int length = (contents != null) ? contents.length : 0;
            boolean longTag2 = (length > 62) || longTag3;

            int hdr = (tagType2 << 6) + (longTag2 ? 0x3f : length);

            out.writeUI16(hdr);

            if (longTag2) {
                out.writeUI32(length);
            }

            if (contents != null) {
                out.write(contents);
            }

            if (tagType2 == SWFConstants.TAG_SHOWFRAME) {
                frameCount++;
            }

            if (tagType2 == SWFConstants.TAG_END) {
                out.flush();
                contents = bytes.toByteArray();

                out = null;
                bytes = null;

                byte[] fc = OutStream.uintTo2Bytes(frameCount);
                contents[2] = fc[0];
                contents[3] = fc[1];

                tags.tag(tagType, longTag, contents);
            }
        }


        /**
         *  Description of the Method
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
                int frameRate, int frameCount) throws IOException { }
    }


    /**
     *  Description of the Method
     *
     *@param  tagType          Description of the Parameter
     *@param  id               Description of the Parameter
     *@param  outline          Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void startShape(int tagType, int id, Rect outline) throws IOException {
        startTag(tagType, id, true);
        outline.write(out);
    }


    /**
     *  Implementation of the SWFShape interface
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    protected static class SWFShapeImpl implements SWFShape {
        /**
         *  Description of the Field
         */
        protected boolean hasAlpha;
        /**
         *  Description of the Field
         */
        protected boolean hasStyle;
        /**
         *  Description of the Field
         */
        protected boolean outstandingChanges = true;
        /**
         *  Description of the Field
         */
        protected boolean initialStyles = false;

        /**
         *  Description of the Field
         */
        protected int fill0Index = -1;
        /**
         *  Description of the Field
         */
        protected int fill1Index = -1;
        /**
         *  Description of the Field
         */
        protected int lineIndex = -1;

        /**
         *  Description of the Field
         */
        protected int[] moveXY;
        /**
         *  Description of the Field
         */
        protected List lineStyles = new Vector();
        /**
         *  Description of the Field
         */
        protected List fillStyles = new Vector();

        /**
         *  Description of the Field
         */
        protected int fillBits;
        /**
         *  Description of the Field
         */
        protected int lineBits;

        /**
         *  Description of the Field
         */
        protected int glyphCount = 0;
        /**
         *  Description of the Field
         */
        protected List glyphByteArrays;

        /**
         *  Description of the Field
         */
        protected OutStream out;
        /**
         *  Description of the Field
         */
        protected TagWriter writer;
        /**
         *  Description of the Field
         */
        protected ByteArrayOutputStream bout;


        /**
         *  For shapes (other than glyphs)
         *
         *@param  writer    Description of the Parameter
         *@param  hasAlpha  Description of the Parameter
         *@param  hasStyle  Description of the Parameter
         */
        public SWFShapeImpl(TagWriter writer, boolean hasAlpha, boolean hasStyle) {
            this.hasAlpha = hasAlpha;
            this.hasStyle = hasStyle;
            this.writer = writer;
            out = writer.getOutStream();
        }


        /**
         *  For glyphs
         *
         *@param  writer      Description of the Parameter
         *@param  glyphCount  Description of the Parameter
         */
        public SWFShapeImpl(TagWriter writer, int glyphCount) {
            this(writer, false, false);
            this.glyphCount = glyphCount;
            bout = new ByteArrayOutputStream();
            out = new OutStream(bout);
            glyphByteArrays = new Vector();

            fill1Index = 1;
            lineIndex = 0;
        }


        /**
         *  Description of the Method
         *
         *@exception  IOException  Description of the Exception
         */
        public void done() throws IOException {
            if (!initialStyles) {
                writeInitialStyles();
                initialStyles = true;
            }

            out.writeUBits(6, 0);
            //end record
            out.flushBits();

            if (bout != null && glyphCount > 0) {
                //capturing bytes internally

                byte[] glyphBytes = bout.toByteArray();
                glyphByteArrays.add(glyphBytes);
            }

            if (glyphCount > 1) {
                bout = new ByteArrayOutputStream();
                out = new OutStream(bout);
                glyphCount--;

                fill1Index = 1;
                lineIndex = 0;
                outstandingChanges = true;
                initialStyles = false;
            } else {
                if (bout != null) {
                    finishFont();
                }
                writer.completeTag();
            }
        }


        /**
         *  Description of the Method
         *
         *@exception  IOException  Description of the Exception
         */
        protected void finishFont() throws IOException {
            out = writer.getOutStream();

            int glyphCount = glyphByteArrays.size();

            //--Write first shape offset
            int offset = glyphCount * 2;
            out.writeUI16(offset);

            //--Write subsequent shape offsets
            for (int i = 0; i < glyphCount - 1; i++) {
                offset += ((byte[]) glyphByteArrays.get(i)).length;
                out.writeUI16(offset);
            }

            //--Write shapes
            for (int i = 0; i < glyphCount; i++) {
                out.write((byte[]) glyphByteArrays.get(i));
            }
        }


        /**
         *  Sets the fillStyle0 attribute of the SWFShapeImpl object
         *
         *@param  styleIndex       The new fillStyle0 value
         *@exception  IOException  Description of the Exception
         */
        public void setFillStyle0(int styleIndex) throws IOException {
            fill0Index = styleIndex;
            outstandingChanges = true;
        }


        /**
         *  Sets the fillStyle1 attribute of the SWFShapeImpl object
         *
         *@param  styleIndex       The new fillStyle1 value
         *@exception  IOException  Description of the Exception
         */
        public void setFillStyle1(int styleIndex) throws IOException {
            fill1Index = styleIndex;
            outstandingChanges = true;
        }


        /**
         *  Sets the lineStyle attribute of the SWFShapeImpl object
         *
         *@param  styleIndex       The new lineStyle value
         *@exception  IOException  Description of the Exception
         */
        public void setLineStyle(int styleIndex) throws IOException {
            lineIndex = styleIndex;
            outstandingChanges = true;
        }


        /**
         *  Description of the Method
         *
         *@param  color            Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        public void defineFillStyle(Color color) throws IOException {
            fillStyles.add(new FillStyle(color));
            outstandingChanges = true;
        }


        /**
         *  Description of the Method
         *
         *@param  matrix           Description of the Parameter
         *@param  ratios           Description of the Parameter
         *@param  colors           Description of the Parameter
         *@param  radial           Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        public void defineFillStyle(Matrix matrix, int[] ratios,
                Color[] colors, boolean radial)
                 throws IOException {
            fillStyles.add(new FillStyle(matrix, ratios, colors, radial));
            outstandingChanges = true;
        }


        /**
         *  Description of the Method
         *
         *@param  bitmapId         Description of the Parameter
         *@param  matrix           Description of the Parameter
         *@param  clipped          Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        public void defineFillStyle(int bitmapId, Matrix matrix, boolean clipped)
                 throws IOException {
            fillStyles.add(new FillStyle(bitmapId, matrix, clipped));
            outstandingChanges = true;
        }


        /**
         *  Description of the Method
         *
         *@param  width            Description of the Parameter
         *@param  color            Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        public void defineLineStyle(int width, Color color) throws IOException {
            lineStyles.add(new LineStyle(width, color));
            outstandingChanges = true;
        }


        /**
         *  Description of the Method
         *
         *@param  deltaX           Description of the Parameter
         *@param  deltaY           Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        public void line(int deltaX, int deltaY) throws IOException {
            if (outstandingChanges) {
                flushChangeRecords();
            }

            int numBits = OutStream.determineSignedBitSize(deltaX);
            int dyBits = OutStream.determineSignedBitSize(deltaY);

            if (dyBits > numBits) {
                numBits = dyBits;
            }
            if (numBits < 2) {
                numBits = 2;
            }

            out.writeUBits(2, 3);
            //11b = line record

            out.writeUBits(4, numBits - 2);

            if (deltaX != 0 && deltaY != 0) {
                //general line

                out.writeUBits(1, 1);
                out.writeSBits(numBits, deltaX);
                out.writeSBits(numBits, deltaY);
            } else {
                //horz or vert line

                out.writeUBits(1, 0);

                if (deltaY != 0) {
                    //vert line

                    out.writeUBits(1, 1);
                    out.writeSBits(numBits, deltaY);
                } else {
                    ///horz line

                    out.writeUBits(1, 0);
                    out.writeSBits(numBits, deltaX);
                }
            }
        }


        /**
         *  Description of the Method
         *
         *@param  cx               Description of the Parameter
         *@param  cy               Description of the Parameter
         *@param  dx               Description of the Parameter
         *@param  dy               Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        public void curve(int cx, int cy, int dx, int dy) throws IOException {
            if (outstandingChanges) {
                flushChangeRecords();
            }

            int numBits = OutStream.determineSignedBitSize(cx);
            int dyBits = OutStream.determineSignedBitSize(cy);
            int adxBits = OutStream.determineSignedBitSize(dx);
            int adyBits = OutStream.determineSignedBitSize(dy);

            if (dyBits > numBits) {
                numBits = dyBits;
            }
            if (adxBits > numBits) {
                numBits = adxBits;
            }
            if (adyBits > numBits) {
                numBits = adyBits;
            }

            if (numBits < 2) {
                numBits = 2;
            }

            out.writeUBits(2, 2);
            //10b = curve record

            out.writeUBits(4, numBits - 2);

            out.writeSBits(numBits, cx);
            out.writeSBits(numBits, cy);
            out.writeSBits(numBits, dx);
            out.writeSBits(numBits, dy);
        }


        /**
         *  Description of the Method
         *
         *@param  x                Description of the Parameter
         *@param  y                Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        public void move(int x, int y) throws IOException {
            moveXY = new int[]{x, y};
            outstandingChanges = true;
        }


        /**
         *  Description of the Method
         *
         *@exception  IOException  Description of the Exception
         */
        protected void flushChangeRecords() throws IOException {
            if (!initialStyles) {
                writeInitialStyles();
                initialStyles = true;
            }

            writeChangeRecord();

            outstandingChanges = false;
        }


        /**
         *  Description of the Method
         *
         *@exception  IOException  Description of the Exception
         */
        protected void writeInitialStyles() throws IOException {
            out.flushBits();

            fillBits = OutStream.determineUnsignedBitSize(fillStyles.size());
            lineBits = OutStream.determineUnsignedBitSize(lineStyles.size());

            //--For fonts - the fillstyle bits must be one
            if (!hasStyle) {
                fillBits = 1;
            } else {
                writeStyles(fillStyles);
                writeStyles(lineStyles);
                out.flushBits();
            }

            out.writeUBits(4, fillBits);
            out.writeUBits(4, lineBits);
        }


        /**
         *  Description of the Method
         *
         *@exception  IOException  Description of the Exception
         */
        protected void writeChangeRecord() throws IOException {
            boolean hasNewStyles = hasStyle &&
                    (fillStyles.size() > 0 || lineStyles.size() > 0);

            boolean hasMoveTo = (moveXY != null);
            boolean hasFillStyle0 = fill0Index >= 0;
            boolean hasFillStyle1 = fill1Index >= 0;
            boolean hasLineStyle = lineIndex >= 0;

            if ((!hasStyle) && hasFillStyle0) {
                hasFillStyle1 = false;
            }

            if (hasNewStyles) {
                out.writeUBits(1, 0);
                //non-edge record
                out.writeUBits(1, 1);
                //defines new styles
                out.writeUBits(1, 1);
                out.writeUBits(1, 1);
                out.writeUBits(1, 1);
                out.writeUBits(1, 1);

                //--Clear existing styles
                writeMoveXY(0, 0);
                out.writeUBits(fillBits, 0);
                out.writeUBits(fillBits, 0);
                out.writeUBits(lineBits, 0);

                if (fill0Index == 0) {
                    fill0Index = -1;
                }
                if (fill1Index == 0) {
                    fill1Index = -1;
                }
                if (lineIndex == 0) {
                    lineIndex = -1;
                }

                fillBits = OutStream.determineUnsignedBitSize(fillStyles.size());
                lineBits = OutStream.determineUnsignedBitSize(lineStyles.size());

                writeStyles(fillStyles);
                writeStyles(lineStyles);

                out.writeUBits(4, fillBits);
                out.writeUBits(4, lineBits);

                writeChangeRecord();
                return;
            }

            if (hasFillStyle0 || hasFillStyle1 || hasLineStyle || hasMoveTo) {
                out.writeUBits(1, 0);
                //non-edge record
                out.writeUBits(1, 0);
                out.writeUBits(1, hasLineStyle ? 1 : 0);
                out.writeUBits(1, hasFillStyle1 ? 1 : 0);
                out.writeUBits(1, hasFillStyle0 ? 1 : 0);
                out.writeUBits(1, hasMoveTo ? 1 : 0);

                if (hasMoveTo) {
                    int moveX = moveXY[0];
                    int moveY = moveXY[1];
                    writeMoveXY(moveX, moveY);
                }

                if (hasFillStyle0) {
                    out.writeUBits(fillBits, fill0Index);
                }

                if (hasFillStyle1) {
                    out.writeUBits(fillBits, fill1Index);
                }

                if (hasLineStyle) {
                    out.writeUBits(lineBits, lineIndex);
                }

                moveXY = null;
                fill0Index = -1;
                fill1Index = -1;
                lineIndex = -1;
            }
        }


        /**
         *  Description of the Method
         *
         *@param  moveX            Description of the Parameter
         *@param  moveY            Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        protected void writeMoveXY(int moveX, int moveY) throws IOException {
            int moveBits = OutStream.determineSignedBitSize(moveX);
            int moveYBits = OutStream.determineSignedBitSize(moveY);
            if (moveYBits > moveBits) {
                moveBits = moveYBits;
            }

            out.writeUBits(5, moveBits);
            out.writeSBits(moveBits, moveX);
            out.writeSBits(moveBits, moveY);
        }


        /**
         *  Description of the Method
         *
         *@param  styles           Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        protected void writeStyles(List styles) throws IOException {
            int numStyles = (styles != null) ? styles.size() : 0;

            if (numStyles < 0xff) {
                out.writeUI8(numStyles);
            } else {
                out.writeUI8(0xff);
                out.writeUI16(numStyles);
            }

            if (styles != null) {
                for (Iterator enumumerator = styles.iterator();
                        enumumerator.hasNext(); ) {
                    Style style = (Style) enumumerator.next();
                    style.write(out, hasAlpha);
                }

                styles.clear();
            }
        }
    }


    /**
     *  Description of the Class
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    protected static class MorphShapeImpl extends TagWriter.SWFShapeImpl {
        /**
         *  Description of the Field
         */
        protected int edgeOffsetBase;
        /**
         *  Description of the Field
         */
        protected int edgeOffsetTarget;
        /**
         *  Description of the Field
         */
        protected int shapeCount;
        /**
         *  Description of the Field
         */
        protected int fillBitSize;
        /**
         *  Description of the Field
         */
        protected int lineBitSize;
        /**
         *  Description of the Field
         */
        protected int shapeStart;


        /**
         *  Constructor for the MorphShapeImpl object
         *
         *@param  writer           Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        public MorphShapeImpl(TagWriter writer) throws IOException {
            super(writer, true, false);
            fill0Index = -1;
            fill1Index = -1;
            lineIndex = -1;

            shapeCount = 2;

            this.out = writer.getOutStream();

            edgeOffsetBase = (int) this.out.getBytesWritten();

            this.out.writeUI32(0);
            //edge offset - to be filled in later
        }


        /**
         *  Description of the Method
         *
         *@exception  IOException  Description of the Exception
         */
        public void done() throws IOException {
            if (!initialStyles) {
                writeInitialStyles();
                initialStyles = true;
            }

            this.out.writeUBits(6, 0);
            //end record
            this.out.flushBits();

            if (shapeCount == 2) {
                edgeOffsetTarget = (int) this.out.getBytesWritten();

                fill0Index = -1;
                fill1Index = -1;
                lineIndex = -1;
                moveXY = null;
                outstandingChanges = true;
                initialStyles = false;

                shapeCount--;
                return;
            }

            this.out.flush();
            byte[] bytes = writer.bytes.toByteArray();

            int edgeOffset = edgeOffsetTarget - edgeOffsetBase - 4;

            byte[] offsetBytes = OutStream.uintTo4Bytes(edgeOffset);

            bytes[edgeOffsetBase] = offsetBytes[0];
            bytes[edgeOffsetBase + 1] = offsetBytes[1];
            bytes[edgeOffsetBase + 2] = offsetBytes[2];
            bytes[edgeOffsetBase + 3] = offsetBytes[3];

            writer.out = null;
            writer.bytes = null;

            writer.tags.tag(writer.tagType, writer.longTag, bytes);
        }


        /**
         *  Description of the Method
         *
         *@exception  IOException  Description of the Exception
         */
        protected void writeInitialStyles() throws IOException {
            this.out.flushBits();

            int fillCount = fillStyles.size() / 2;
            int lineCount = lineStyles.size() / 2;

            fillBitSize = OutStream.determineUnsignedBitSize(fillCount);
            lineBitSize = OutStream.determineUnsignedBitSize(lineCount);

            //--Write style definitions
            if (shapeCount == 2) {
                if (fillCount < 255) {
                    this.out.writeUI8(fillCount);
                } else {
                    this.out.writeUI8(255);
                    this.out.writeUI16(fillCount);
                }

                for (Iterator enumumerator = fillStyles.iterator(); enumumerator.hasNext(); ) {
                    FillStyle startStyle = (FillStyle) enumumerator.next();
                    FillStyle endStyle = (FillStyle) enumumerator.next();
                    FillStyle.writeMorphFillStyle(this.out, startStyle, endStyle);
                }

                if (lineCount < 255) {
                    this.out.writeUI8(lineCount);
                } else {
                    this.out.writeUI8(255);
                    this.out.writeUI16(lineCount);
                }

                for (Iterator enumerator = lineStyles.iterator(); enumerator.hasNext(); ) {
                    LineStyle startStyle = (LineStyle) enumerator.next();
                    LineStyle endStyle = (LineStyle) enumerator.next();
                    LineStyle.writeMorphLineStyle(this.out, startStyle, endStyle);
                }
            }

            if (shapeStart == 0) {
                shapeStart = (int) this.out.getBytesWritten();
            }

            this.out.writeUBits(4, fillBitSize);
            this.out.writeUBits(4, lineBitSize);
        }


        /**
         *  Description of the Method
         *
         *@exception  IOException  Description of the Exception
         */
        protected void writeChangeRecord() throws IOException {
            boolean hasMoveTo = (moveXY != null);
            boolean hasFillStyle0 = fill0Index >= 0;
            boolean hasFillStyle1 = fill1Index >= 0;
            boolean hasLineStyle = lineIndex >= 0;

            if (hasFillStyle0 || hasFillStyle1 || hasLineStyle || hasMoveTo) {
                this.out.writeUBits(1, 0);
                //non-edge record
                this.out.writeUBits(1, 0);
                this.out.writeUBits(1, hasLineStyle ? 1 : 0);
                this.out.writeUBits(1, hasFillStyle1 ? 1 : 0);
                this.out.writeUBits(1, hasFillStyle0 ? 1 : 0);
                this.out.writeUBits(1, hasMoveTo ? 1 : 0);

                if (hasMoveTo) {
                    int moveX = moveXY[0];
                    int moveY = moveXY[1];
                    int moveBits = OutStream.determineSignedBitSize(moveX);
                    int moveYBits = OutStream.determineSignedBitSize(moveY);
                    if (moveYBits > moveBits) {
                        moveBits = moveYBits;
                    }

                    this.out.writeUBits(5, moveBits);
                    this.out.writeSBits(moveBits, moveX);
                    this.out.writeSBits(moveBits, moveY);
                }

                if (hasFillStyle0) {
                    this.out.writeUBits(fillBitSize, fill0Index);
                }

                if (hasFillStyle1) {
                    this.out.writeUBits(fillBitSize, fill1Index);
                }

                if (hasLineStyle) {
                    this.out.writeUBits(lineBitSize, lineIndex);
                }

                moveXY = null;
                fill0Index = -1;
                fill1Index = -1;
                lineIndex = -1;
            }
        }
    }


    /**
     *  Description of the Class
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    protected static class Font2ShapeImpl extends TagWriter.SWFShapeImpl {
        /**
         *  Description of the Field
         */
        protected int flags;
        /**
         *  Description of the Field
         */
        protected int ascent;
        /**
         *  Description of the Field
         */
        protected int descent;
        /**
         *  Description of the Field
         */
        protected int leading;
        /**
         *  Description of the Field
         */
        protected int[] codes;
        /**
         *  Description of the Field
         */
        protected int[] advances;
        /**
         *  Description of the Field
         */
        protected Rect[] bounds;
        /**
         *  Description of the Field
         */
        protected int[] kernCodes1;
        /**
         *  Description of the Field
         */
        protected int[] kernCodes2;
        /**
         *  Description of the Field
         */
        protected int[] kernAdjustments;


        /**
         *  Constructor for the Font2ShapeImpl object
         *
         *@param  writer           Description of the Parameter
         *@param  flags            Description of the Parameter
         *@param  glyphCount       Description of the Parameter
         *@param  ascent           Description of the Parameter
         *@param  descent          Description of the Parameter
         *@param  leading          Description of the Parameter
         *@param  codes            Description of the Parameter
         *@param  advances         Description of the Parameter
         *@param  bounds           Description of the Parameter
         *@param  kernCodes1       Description of the Parameter
         *@param  kernCodes2       Description of the Parameter
         *@param  kernAdjustments  Description of the Parameter
         */
        public Font2ShapeImpl(TagWriter writer, int flags, int glyphCount,
                int ascent, int descent, int leading,
                int[] codes, int[] advances, Rect[] bounds,
                int[] kernCodes1, int[] kernCodes2,
                int[] kernAdjustments) {
            super(writer, glyphCount);

            this.flags = flags;
            this.ascent = ascent;
            this.descent = descent;
            this.leading = leading;
            this.codes = codes;
            this.advances = advances;
            this.bounds = bounds;
            this.kernCodes1 = kernCodes1;
            this.kernCodes2 = kernCodes2;
            this.kernAdjustments = kernAdjustments;
        }


        /**
         *  Description of the Method
         *
         *@exception  IOException  Description of the Exception
         */
        protected void finishFont() throws IOException {
            this.out = writer.getOutStream();

            int glyphCount = glyphByteArrays.size();

            boolean is32 = (flags & FONT2_32OFFSETS) != 0;
            int offset = is32 ? ((glyphCount + 1) * 4) : ((glyphCount + 1) * 2);
            for (int i = 0; i <= glyphCount; i++) {
                if (is32) {
                    this.out.writeUI32(offset);
                } else {
                    this.out.writeUI16(offset);
                }

                if (i < glyphCount) {
                    offset += ((byte[]) glyphByteArrays.get(i)).length;
                }
            }

            for (int i = 0; i < glyphCount; i++) {
                this.out.write((byte[]) glyphByteArrays.get(i));
            }

            boolean isWide = (flags & FONT2_WIDECHARS) != 0 || glyphCount > 256;
            for (int i = 0; i < glyphCount; i++) {
                if (isWide) {
                    this.out.writeUI16(codes[i]);
                } else {
                    this.out.writeUI8(codes[i]);
                }
            }

            if ((flags & FONT2_HAS_LAYOUT) != 0) {
                this.out.writeSI16((short) ascent);
                this.out.writeSI16((short) descent);
                this.out.writeSI16((short) leading);

                for (int i = 0; i < glyphCount; i++) {
                    this.out.writeSI16((short) advances[i]);
                }

                for (int i = 0; i < glyphCount; i++) {
                    bounds[i].write(this.out);
                }

                int kerningCount = (kernCodes1 != null) ? kernCodes1.length : 0;
                this.out.writeUI16(kerningCount);

                for (int i = 0; i < kerningCount; i++) {
                    if (isWide) {
                        this.out.writeUI16(kernCodes1[i]);
                        this.out.writeUI16(kernCodes2[i]);
                        this.out.writeSI16((short) kernAdjustments[i]);
                    } else {
                        this.out.writeUI8(kernCodes1[i]);
                        this.out.writeUI8(kernCodes2[i]);
                        this.out.writeSI16((short) kernAdjustments[i]);
                    }
                }
            }
        }
    }
}
