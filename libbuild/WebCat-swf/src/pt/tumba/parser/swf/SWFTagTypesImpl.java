package pt.tumba.parser.swf;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 *  A pass-through implementation of the SWFTagTypes interface - useful as a
 *  base class
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class SWFTagTypesImpl implements SWFTagTypes {
    /**
     *  Description of the Field
     */
    protected SWFTagTypes tags;


    /**
     *@param  tags  may be null
     */
    public SWFTagTypesImpl(SWFTagTypes tags) {
        this.tags = tags;
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
        if (tags != null) {
            tags.tag(tagType, longTag, contents);
        }
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
        if (tags != null) {
            tags.header(version, length, twipsWidth, twipsHeight,
                    frameRate, frameCount);
        }
    }


    /**
     *  SWFTagTypes interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void tagEnd() throws IOException {
        if (tags != null) {
            tags.tagEnd();
        }
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
        if (tags != null) {
            tags.tagDefineSound(id, format, frequency,
                    bits16, stereo, sampleCount, soundData);
        }
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
        if (tags != null) {
            tags.tagDefineButtonSound(buttonId,
                    rollOverSoundId, rollOverSoundInfo,
                    rollOutSoundId, rollOutSoundInfo,
                    pressSoundId, pressSoundInfo,
                    releaseSoundId, releaseSoundInfo);
        }
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  soundId          Description of the Parameter
     *@param  info             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagStartSound(int soundId, SoundInfo info) throws IOException {
        if (tags != null) {
            tags.tagStartSound(soundId, info);
        }
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
        if (tags != null) {
            tags.tagSoundStreamHead(
                    playbackFrequency, playback16bit, playbackStereo,
                    streamFormat, streamFrequency, stream16bit, streamStereo,
                    averageSampleCount);
        }
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
        if (tags != null) {
            tags.tagSoundStreamHead2(
                    playbackFrequency, playback16bit, playbackStereo,
                    streamFormat, streamFrequency, stream16bit, streamStereo,
                    averageSampleCount);
        }
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  soundData        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagSoundStreamBlock(byte[] soundData) throws IOException {
        if (tags != null) {
            tags.tagSoundStreamBlock(soundData);
        }
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  serialNumber     Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagSerialNumber(String serialNumber) throws IOException {
        if (tags != null) {
            tags.tagSerialNumber(serialNumber);
        }
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  data             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagGenerator(byte[] data) throws IOException {
        if (tags != null) {
            tags.tagGenerator(data);
        }
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  data             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagGeneratorText(byte[] data) throws IOException {
        if (tags != null) {
            tags.tagGeneratorText(data);
        }
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  data             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagGeneratorFont(byte[] data) throws IOException {
        if (tags != null) {
            tags.tagGeneratorFont(data);
        }
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  data             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagGeneratorCommand(byte[] data) throws IOException {
        if (tags != null) {
            tags.tagGeneratorCommand(data);
        }
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  data             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagNameCharacter(byte[] data) throws IOException {
        if (tags != null) {
            tags.tagNameCharacter(data);
        }
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@param  imageData        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineBits(int id, byte[] imageData) throws IOException {
        if (tags != null) {
            tags.tagDefineBits(id, imageData);
        }
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  jpegEncodingData  Description of the Parameter
     *@exception  IOException   Description of the Exception
     */
    public void tagJPEGTables(byte[] jpegEncodingData) throws IOException {
        if (tags != null) {
            tags.tagJPEGTables(jpegEncodingData);
        }
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@param  imageData        Description of the Parameter
     *@param  alphaData        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineBitsJPEG3(int id, byte[] imageData, byte[] alphaData)
             throws IOException {
        if (tags != null) {
            tags.tagDefineBitsJPEG3(id, imageData, alphaData);
        }
    }


    /**
     *  SWFTagTypes interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void tagShowFrame() throws IOException {
        if (tags != null) {
            tags.tagShowFrame();
        }
    }


    /**
     *  SWFTagTypes interface
     *
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    public SWFActions tagDoAction() throws IOException {
        if (tags != null) {
            return tags.tagDoAction();
        }
        return null;
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
        if (tags != null) {
            return tags.tagDefineShape(id, outline);
        }
        return null;
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
        if (tags != null) {
            return tags.tagDefineShape2(id, outline);
        }
        return null;
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
        if (tags != null) {
            return tags.tagDefineShape3(id, outline);
        }
        return null;
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  charId           Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagFreeCharacter(int charId) throws IOException {
        if (tags != null) {
            tags.tagFreeCharacter(charId);
        }
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
        if (tags != null) {
            tags.tagPlaceObject(charId, depth, matrix, cxform);
        }
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
        if (tags != null) {
            return tags.tagPlaceObject2(isMove, clipDepth, depth,
                    charId, matrix, cxform, ratio,
                    name, clipActionFlags);
        }
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
        if (tags != null) {
            tags.tagRemoveObject(charId, depth);
        }
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  depth            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagRemoveObject2(int depth) throws IOException {
        if (tags != null) {
            tags.tagRemoveObject2(depth);
        }
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  color            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagSetBackgroundColor(Color color) throws IOException {
        if (tags != null) {
            tags.tagSetBackgroundColor(color);
        }
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  label            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagFrameLabel(String label) throws IOException {
        if (tags != null) {
            tags.tagFrameLabel(label);
        }
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    public SWFTagTypes tagDefineSprite(int id) throws IOException {
        if (tags != null) {
            return tags.tagDefineSprite(id);
        }
        return null;
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  password         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagProtect(byte[] password) throws IOException {
        if (tags != null) {
            tags.tagProtect(password);
        }
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  password         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagEnableDebug(byte[] password) throws IOException {
        if (tags != null) {
            tags.tagEnableDebug(password);
        }
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
        if (tags != null) {
            return tags.tagDefineFont(id, numGlyphs);
        }
        return null;
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
        if (tags != null) {
            tags.tagDefineFontInfo(fontId, fontName, flags, codes);
        }
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
        if (tags != null) {
            return tags.tagDefineFont2(id, flags, name, numGlyphs,
                    ascent, descent, leading, codes, advances,
                    bounds, kernCodes1, kernCodes2, kernAdjustments);
        }
        return null;
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
            String initialText, Rect boundary, int flags,
            AlphaColor textColor, int alignment, int fontId, int fontSize,
            int charLimit, int leftMargin, int rightMargin, int indentation,
            int lineSpacing)
             throws IOException {
        if (tags != null) {
            tags.tagDefineTextField(fieldId, fieldName, initialText,
                    boundary, flags, textColor, alignment, fontId,
                    fontSize, charLimit, leftMargin, rightMargin,
                    indentation, lineSpacing);
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
        if (tags != null) {
            return tags.tagDefineText(id, bounds, matrix);
        }

        return null;
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
        if (tags != null) {
            return tags.tagDefineText2(id, bounds, matrix);
        }
        return null;
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
        if (tags != null) {
            return tags.tagDefineButton(id, buttonRecords);
        }
        return null;
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
        if (tags != null) {
            tags.tagButtonCXForm(buttonId, transform);
        }
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
        if (tags != null) {
            return tags.tagDefineButton2(id, trackAsMenu,
                    buttonRecord2s);
        }
        return null;
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  names            Description of the Parameter
     *@param  ids              Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagExport(String[] names, int[] ids) throws IOException {
        if (tags != null) {
            tags.tagExport(names, ids);
        }
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
        if (tags != null) {
            tags.tagImport(movieName, names, ids);
        }
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@param  filename         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineQuickTimeMovie(int id, String filename) throws IOException {
        if (tags != null) {
            tags.tagDefineQuickTimeMovie(id, filename);
        }
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@param  data             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineBitsJPEG2(int id, byte[] data) throws IOException {
        if (tags != null) {
            tags.tagDefineBitsJPEG2(id, data);
        }
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@param  jpegImage        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineBitsJPEG2(int id, InputStream jpegImage) throws IOException {
        if (tags != null) {
            tags.tagDefineBitsJPEG2(id, jpegImage);
        }
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
        if (tags != null) {
            return tags.tagDefineMorphShape(id, startBounds, endBounds);
        }
        return null;
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
        if (tags != null) {
            tags.tagDefineBitsLossless(id, format, width, height,
                    colors, imageData);
        }
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
        if (tags != null) {
            tags.tagDefineBitsLossless2(id, format, width, height,
                    colors, imageData);
        }
    }
}
