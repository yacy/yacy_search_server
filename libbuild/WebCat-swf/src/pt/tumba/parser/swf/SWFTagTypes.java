package pt.tumba.parser.swf;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 *  Interface for passing SWF tag types.
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public interface SWFTagTypes extends SWFSpriteTagTypes {

    /**
     *@param  format           one of the SWFConstants.SOUND_FORMAT_* constants
     *@param  frequency        one of the SWFConstants.SOUND_FREQ_* constants
     *@param  soundData        format-dependent sound data
     *@param  id               Description of the Parameter
     *@param  bits16           Description of the Parameter
     *@param  stereo           Description of the Parameter
     *@param  sampleCount      Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineSound(int id, int format, int frequency,
            boolean bits16, boolean stereo,
            int sampleCount, byte[] soundData) throws IOException;


    /**
     *  Define the sound for a button
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
             throws IOException;


    /**
     *@param  id               Description of the Parameter
     *@param  outline          Description of the Parameter
     *@return                  SWFShape to receive shape info - or null to skip
     *      the data
     *@exception  IOException  Description of the Exception
     */
    public SWFShape tagDefineShape(int id, Rect outline) throws IOException;


    /**
     *@param  id               Description of the Parameter
     *@param  outline          Description of the Parameter
     *@return                  SWFShape to receive shape info - or null to skip
     *      the data
     *@exception  IOException  Description of the Exception
     */
    public SWFShape tagDefineShape2(int id, Rect outline) throws IOException;


    /**
     *@param  id               Description of the Parameter
     *@param  outline          Description of the Parameter
     *@return                  SWFShape to receive shape info - or null to skip
     *      the data
     *@exception  IOException  Description of the Exception
     */
    public SWFShape tagDefineShape3(int id, Rect outline) throws IOException;


    /**
     *@param  buttonRecords    contains ButtonRecord objects
     *@param  id               Description of the Parameter
     *@return                  SWFActions object (may be null) to receive button
     *      actions - there is only one action array (with no conditions).
     *@exception  IOException  Description of the Exception
     *@see                     com.anotherbigidea.flash.structs.ButtonRecord
     */
    public SWFActions tagDefineButton(int id, List buttonRecords)
             throws IOException;


    /**
     *  Description of the Method
     *
     *@param  buttonId         Description of the Parameter
     *@param  transform        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagButtonCXForm(int buttonId, ColorTransform transform) throws IOException;


    /**
     *@param  buttonRecord2s   contains ButtonRecord2 objects
     *@param  id               Description of the Parameter
     *@param  trackAsMenu      Description of the Parameter
     *@return                  SWFActions object (may be null) to receive button
     *      actions - there may be multiple action arrays - each one is
     *      conditional, using the BUTTON2_* condition flags defined in
     *      SWFConstants.java
     *@exception  IOException  Description of the Exception
     *@see                     com.anotherbigidea.flash.structs.ButtonRecord2
     */
    public SWFActions tagDefineButton2(int id,
            boolean trackAsMenu,
            List buttonRecord2s)
             throws IOException;


    /**
     *  Description of the Method
     *
     *@param  color            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagSetBackgroundColor(Color color) throws IOException;


    /**
     *  The SWFVectors object returned will be called numGlyphs times to pass
     *  the vector information for each glyph (each glyph is terminated by
     *  calling SWFVectors.done() )
     *
     *@param  id               Description of the Parameter
     *@param  numGlyphs        Description of the Parameter
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    public SWFVectors tagDefineFont(int id, int numGlyphs) throws IOException;


    /**
     *@param  id               Description of the Parameter
     *@param  bounds           Description of the Parameter
     *@param  matrix           Description of the Parameter
     *@return                  SWFText object to receive the text style and
     *      glyph information - this may be null if the info is not required
     *@exception  IOException  Description of the Exception
     */
    public SWFText tagDefineText(int id, Rect bounds, Matrix matrix) throws IOException;


    /**
     *  Allows alpha colors
     *
     *@param  id               Description of the Parameter
     *@param  bounds           Description of the Parameter
     *@param  matrix           Description of the Parameter
     *@return                  SWFText object to receive the text style and
     *      glyph information - this may be null if the info is not required
     *@exception  IOException  Description of the Exception
     */
    public SWFText tagDefineText2(int id, Rect bounds, Matrix matrix) throws IOException;


    /**
     *@param  flags            see FONT_* constants in SWFConstants.java
     *@param  fontId           Description of the Parameter
     *@param  fontName         Description of the Parameter
     *@param  codes            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineFontInfo(int fontId, String fontName,
            int flags, int[] codes) throws IOException;


    /**
     *@param  data             must contain the header data - use the
     *      InputStream version when using an external JPEG
     *@param  id               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineBitsJPEG2(int id, byte[] data) throws IOException;


    /**
     *@param  jpegImage        must be a baseline JPEG (not a progressive JPEG)
     *@param  id               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineBitsJPEG2(int id, InputStream jpegImage) throws IOException;


    /**
     *  JPEG image data only - header/encoding data is in tagJPEGTables tag
     *
     *@param  id               Description of the Parameter
     *@param  imageData        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineBits(int id, byte[] imageData) throws IOException;


    /**
     *  Only one tag per SWF - holds common JPEG encoding data
     *
     *@param  jpegEncodingData  Description of the Parameter
     *@exception  IOException   Description of the Exception
     */
    public void tagJPEGTables(byte[] jpegEncodingData) throws IOException;


    /**
     *  JPEG image and encoding data with alpha channel bitmap
     *
     *@param  alphaData        is zlib compressed
     *@param  id               Description of the Parameter
     *@param  imageData        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineBitsJPEG3(int id, byte[] imageData, byte[] alphaData) throws IOException;


    /**
     *@param  format           one of the SWFConstants.BITMAP_FORMAT_n_BIT
     *      constants
     *@param  id               Description of the Parameter
     *@param  width            Description of the Parameter
     *@param  height           Description of the Parameter
     *@param  colors           Description of the Parameter
     *@param  imageData        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineBitsLossless(int id, int format, int width, int height,
            Color[] colors, byte[] imageData)
             throws IOException;


    /**
     *@param  format           one of the SWFConstants.BITMAP_FORMAT_n_BIT
     *      constants
     *@param  id               Description of the Parameter
     *@param  width            Description of the Parameter
     *@param  height           Description of the Parameter
     *@param  colors           Description of the Parameter
     *@param  imageData        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineBitsLossless2(int id, int format, int width, int height,
            Color[] colors, byte[] imageData)
             throws IOException;


    /**
     *@param  password         may be null
     *@exception  IOException  Description of the Exception
     */
    public void tagProtect(byte[] password) throws IOException;


    /**
     *@param  flags            see TEXTFIELD_* constants in SWFConstants.java
     *@param  fieldId          Description of the Parameter
     *@param  fieldName        Description of the Parameter
     *@param  initialText      Description of the Parameter
     *@param  boundary         Description of the Parameter
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
             throws IOException;


    /**
     *  Description of the Method
     *
     *@param  id               Description of the Parameter
     *@param  filename         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineQuickTimeMovie(int id, String filename) throws IOException;


    /**
     *  Description of the Method
     *
     *@param  id               Description of the Parameter
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    public SWFTagTypes tagDefineSprite(int id) throws IOException;


    /**
     *  Description of the Method
     *
     *@param  id               Description of the Parameter
     *@param  startBounds      Description of the Parameter
     *@param  endBounds        Description of the Parameter
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    public SWFShape tagDefineMorphShape(int id, Rect startBounds, Rect endBounds)
             throws IOException;


    /**
     *  Description of the Method
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
            int[] kernAdjustments) throws IOException;


    /**
     *  Description of the Method
     *
     *@param  names            Description of the Parameter
     *@param  ids              Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagExport(String[] names, int[] ids) throws IOException;


    /**
     *  Description of the Method
     *
     *@param  movieName        Description of the Parameter
     *@param  names            Description of the Parameter
     *@param  ids              Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagImport(String movieName, String[] names, int[] ids) throws IOException;


    /**
     *  Description of the Method
     *
     *@param  password         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagEnableDebug(byte[] password) throws IOException;


    /**
     *  In files produced by Generator...
     *
     *@param  serialNumber     Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagSerialNumber(String serialNumber) throws IOException;


    /**
     *  In Generator templates. Data is not parsed.
     *
     *@param  data             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagGenerator(byte[] data) throws IOException;


    /**
     *  In Generator templates. Data is not parsed.
     *
     *@param  data             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagGeneratorText(byte[] data) throws IOException;


    /**
     *  In Generator templates. Data is not parsed.
     *
     *@param  data             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagGeneratorCommand(byte[] data) throws IOException;


    /**
     *  In Generator templates. Data is not parsed.
     *
     *@param  data             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagNameCharacter(byte[] data) throws IOException;


    /**
     *  In Generator templates. Data is not parsed.
     *
     *@param  data             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagGeneratorFont(byte[] data) throws IOException;
}
