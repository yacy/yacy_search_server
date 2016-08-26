package pt.tumba.parser.swf;

import java.io.IOException;

/**
 *  Interface for passing SWF tag types that can be used in a movie or a sprite
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public interface SWFSpriteTagTypes extends SWFTags {
    /**
     *  Start/stop playing a sound
     *
     *@param  soundId          Description of the Parameter
     *@param  info             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagStartSound(int soundId, SoundInfo info) throws IOException;


    /**
     *  Only allows ADPCM encoding.
     *
     *@param  streamFormat        must be SWFConstants.SOUND_FORMAT_ADPCM
     *@param  playbackFrequency   one of the SWFConstants.SOUND_FREQ_* constants
     *@param  streamFrequency     one of the SWFConstants.SOUND_FREQ_* constants
     *@param  playback16bit       Description of the Parameter
     *@param  playbackStereo      Description of the Parameter
     *@param  stream16bit         Description of the Parameter
     *@param  streamStereo        Description of the Parameter
     *@param  averageSampleCount  Description of the Parameter
     *@exception  IOException     Description of the Exception
     */
    public void tagSoundStreamHead(
            int playbackFrequency, boolean playback16bit, boolean playbackStereo,
            int streamFormat, int streamFrequency, boolean stream16bit, boolean streamStereo,
            int averageSampleCount) throws IOException;


    /**
     *  Allows any encoding.
     *
     *@param  streamFormat        one of the SWFConstants.SOUND_FORMAT_*
     *      constants
     *@param  playbackFrequency   one of the SWFConstants.SOUND_FREQ_* constants
     *@param  streamFrequency     one of the SWFConstants.SOUND_FREQ_* constants
     *@param  playback16bit       Description of the Parameter
     *@param  playbackStereo      Description of the Parameter
     *@param  stream16bit         Description of the Parameter
     *@param  streamStereo        Description of the Parameter
     *@param  averageSampleCount  Description of the Parameter
     *@exception  IOException     Description of the Exception
     */
    public void tagSoundStreamHead2(
            int playbackFrequency, boolean playback16bit, boolean playbackStereo,
            int streamFormat, int streamFrequency, boolean stream16bit, boolean streamStereo,
            int averageSampleCount) throws IOException;


    /**
     *@param  soundData        format-dependent sound data
     *@exception  IOException  Description of the Exception
     */
    public void tagSoundStreamBlock(byte[] soundData) throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void tagEnd() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void tagShowFrame() throws IOException;


    /**
     *@return                  SWFActions to receive actions - or null to skip
     *      the data
     *@exception  IOException  Description of the Exception
     */
    public SWFActions tagDoAction() throws IOException;


    /**
     *  Description of the Method
     *
     *@param  charId           Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagFreeCharacter(int charId) throws IOException;


    /**
     *@param  cxform           may be null
     *@param  charId           Description of the Parameter
     *@param  depth            Description of the Parameter
     *@param  matrix           Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagPlaceObject(int charId, int depth, Matrix matrix, AlphaTransform cxform) throws IOException;


    /**
     *  Description of the Method
     *
     *@param  charId           Description of the Parameter
     *@param  depth            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagRemoveObject(int charId, int depth) throws IOException;


    /**
     *@param  clipDepth        < 1 if not relevant
     *@param  charId           < 1 if not relevant
     *@param  name             of sprite instance - null if not relevant
     *@param  ratio            < 0 if not relevant
     *@param  matrix           null if not relevant
     *@param  cxform           null if not relevant
     *@param  clipActionFlags  == 0 if there are no clip actions - otherwise
     *      this is the OR of the condition flags on all the clip action blocks
     *@param  isMove           Description of the Parameter
     *@param  depth            Description of the Parameter
     *@return                  null if there are no clip actions or they are
     *      irrelevant
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
            int clipActionFlags) throws IOException;


    /**
     *  Description of the Method
     *
     *@param  depth            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagRemoveObject2(int depth) throws IOException;


    /**
     *  Description of the Method
     *
     *@param  label            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagFrameLabel(String label) throws IOException;
}
