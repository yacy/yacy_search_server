package pt.tumba.parser.swf;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *  A SAX2 parser (XMLReader) that implements the SWFTagTypes interface and
 *  produces an XML representation of the Flash movie.
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class SWFSaxParser extends SaxParserBase
         implements SWFTagTypes, SWFShape, SWFActions, SWFText {
    /**
     *  The namespace for the SWF XML vocabulary
     */
    public final static String NAMESPACE = "http://www.anotherbigidea.com/javaswf2";

    /**
     *  Description of the Field
     */
    protected String frameLabel;
    /**
     *  Description of the Field
     */
    protected boolean definingSprite;
    /**
     *  Description of the Field
     */
    protected int actionsType = 0;
    //1=frame,2=clip,3=button

    /**
     *  Description of the Field
     */
    protected int glyphCount = 0;
    /**
     *  Description of the Field
     */
    protected Rect[] glyphRects;
    /**
     *  Description of the Field
     */
    protected int[] glyphAdvances;
    /**
     *  Description of the Field
     */
    protected int[] glyphCodes;

    /**
     *  Description of the Field
     */
    protected Rect morphEndRect;
    /**
     *  Description of the Field
     */
    protected boolean morph;


    /**
     *  Constructor for the SWFSaxParser object
     */
    public SWFSaxParser() {
        super(NAMESPACE);
    }


    /**
     *  Constructor for the SWFSaxParser object
     *
     *@param  contenthandler  Description of the Parameter
     *@param  errorhandler    Description of the Parameter
     */
    public SWFSaxParser(ContentHandler contenthandler, ErrorHandler errorhandler) {
        this();
        this.setContentHandler(contenthandler);
        this.setErrorHandler(errorhandler);
    }


    /**
     *  Reads the input as a SWF file byte stream
     *
     *@param  input             Description of the Parameter
     *@exception  IOException   Description of the Exception
     *@exception  SAXException  Description of the Exception
     */
    public void parse(InputSource input) throws IOException, SAXException {
        InputStream istream = input.getByteStream();
        parse(istream);
    }


    /**
     *  Reads the systemId as a filename
     *
     *@param  systemId          Description of the Parameter
     *@exception  IOException   Description of the Exception
     *@exception  SAXException  Description of the Exception
     */
    public void parse(String systemId) throws IOException, SAXException {
        FileInputStream istream = new FileInputStream(systemId);
        parse(istream);
    }


    /**
     *  Description of the Method
     *
     *@param  istream          Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void parse(InputStream istream) throws IOException {
        TagParser parser = new TagParser(this);
        SWFReader reader = new SWFReader(parser, istream);
        reader.readFile();

        istream.close();
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
        start("tag", new String[]
                {
                "type", Integer.toString(tagType)
                });

        text(Base64.encode(contents));

        end();
    }


    /**
     *  SWFHeader interface.
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
        startDoc();
        start("movie", new String[]
                {
                "version", Integer.toString(version),
                "width", Integer.toString(twipsWidth / SWFConstants.TWIPS),
                "height", Integer.toString(twipsHeight / SWFConstants.TWIPS),
                "rate", Integer.toString(frameRate)
                });
    }


    /**
     *  SWFTagTypes interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void tagEnd() throws IOException {
        end();
        //movie or sprite

        if (!definingSprite) {
            endDoc();
        } else {
            definingSprite = false;
        }
    }


    /**
     *  SWFTagTypes interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void tagShowFrame() throws IOException {
        element("frame", (frameLabel == null) ?
                null :
                new String[]{"label", frameLabel});

        frameLabel = null;
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
        String sndFormat = null;
        switch (format) {
            case SWFConstants.SOUND_FORMAT_RAW:
                sndFormat = "raw";
                break;
            case SWFConstants.SOUND_FORMAT_ADPCM:
                sndFormat = "adpcm";
                break;
            case SWFConstants.SOUND_FORMAT_MP3:
                sndFormat = "mp3";
                break;
            default:
                break;
        }

        String freq = null;
        switch (frequency) {
            case SWFConstants.SOUND_FREQ_5_5KHZ:
                freq = "5.5";
                break;
            case SWFConstants.SOUND_FREQ_11KHZ:
                freq = "11";
                break;
            case SWFConstants.SOUND_FREQ_22KHZ:
                freq = "22";
                break;
            case SWFConstants.SOUND_FREQ_44KHZ:
                freq = "44";
                break;
            default:
                break;
        }

        start("sound", new String[]
                {
                "id", Integer.toString(id),
                "format", sndFormat,
                "rate", freq,
                "bits", (bits16 ? "16" : "8"),
                "stereo", (stereo ? "yes" : "no"),
                "sample-count", Integer.toString(sampleCount)
                });
        text(Base64.encode(soundData));
        end();
    }


    /**
     *  Description of the Method
     *
     *@param  info             Description of the Parameter
     *@param  event            Description of the Parameter
     *@param  id               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void soundInfo(SoundInfo info, String event, int id) throws IOException {
        int loopCount = info.getLoopCount();
        int inpoint = info.getInPoint();
        int outpoint = info.getOutPoint();

        String loop = (loopCount > 1) ? Integer.toString(loopCount) : null;
        String inpnt = (inpoint >= 0) ? Integer.toString(inpoint) : null;
        String outpnt = (outpoint >= 0) ? Integer.toString(outpoint) : null;

        start("sound-info", new String[]
                {
                "event", event,
                "sound-id", Integer.toString(id),
                "single-instance", (info.isNoMultiplePlay() ? "yes" : "no"),
                "stop-playing", (info.isStopPlaying() ? "yes" : "no"),
                "loop-count", loop,
                "fade-in", inpnt,
                "fade-out", outpnt
                });

        SoundInfo.EnvelopePoint[] envelope = info.getEnvelope();
        if (envelope != null && envelope.length > 0) {
            for (int i = 0; i < envelope.length; i++) {
                element("envelope-point", new String[]
                        {
                        "position", Integer.toString(envelope[i].mark44),
                        "level-0", Integer.toString(envelope[i].level0),
                        "level-1", Integer.toString(envelope[i].level1)
                        });
            }
        }

        end();
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
        start("button-sound", new String[]{"id", Integer.toString(buttonId)});

        if (rollOverSoundId != 0) {
            soundInfo(rollOverSoundInfo, "roll-over", rollOverSoundId);
        }

        if (rollOutSoundId != 0) {
            soundInfo(rollOutSoundInfo, "roll-out", rollOutSoundId);
        }

        if (pressSoundId != 0) {
            soundInfo(pressSoundInfo, "press", pressSoundId);
        }

        if (releaseSoundId != 0) {
            soundInfo(releaseSoundInfo, "release", releaseSoundId);
        }
        end();
        //button-sound
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  soundId          Description of the Parameter
     *@param  info             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagStartSound(int soundId, SoundInfo info) throws IOException {
        start("start-sound", null);
        soundInfo(info, null, soundId);
        end();
    }


    /**
     *  Description of the Method
     *
     *@param  type                Description of the Parameter
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
    protected void soundStreamHead(String type,
            int playbackFrequency, boolean playback16bit, boolean playbackStereo,
            int streamFormat, int streamFrequency, boolean stream16bit, boolean streamStereo,
            int averageSampleCount) throws IOException {
        String sndFormat = null;
        switch (streamFormat) {
            case SWFConstants.SOUND_FORMAT_RAW:
                sndFormat = "raw";
                break;
            case SWFConstants.SOUND_FORMAT_ADPCM:
                sndFormat = "adpcm";
                break;
            case SWFConstants.SOUND_FORMAT_MP3:
                sndFormat = "mp3";
                break;
            default:
                break;
        }

        String playfreq = null;
        switch (playbackFrequency) {
            case SWFConstants.SOUND_FREQ_5_5KHZ:
                playfreq = "5.5";
                break;
            case SWFConstants.SOUND_FREQ_11KHZ:
                playfreq = "11";
                break;
            case SWFConstants.SOUND_FREQ_22KHZ:
                playfreq = "22";
                break;
            case SWFConstants.SOUND_FREQ_44KHZ:
                playfreq = "44";
                break;
            default:
                break;
        }

        String streamfreq = null;
        switch (streamFrequency) {
            case SWFConstants.SOUND_FREQ_5_5KHZ:
                streamfreq = "5.5";
                break;
            case SWFConstants.SOUND_FREQ_11KHZ:
                streamfreq = "11";
                break;
            case SWFConstants.SOUND_FREQ_22KHZ:
                streamfreq = "22";
                break;
            case SWFConstants.SOUND_FREQ_44KHZ:
                streamfreq = "44";
                break;
            default:
                break;
        }

        element("sound-stream-header", new String[]
                {
                "type", type,
                "play-rate", playfreq,
                "play-bits", (playback16bit ? "16" : "8"),
                "play-stereo", (playbackStereo ? "yes" : "no"),
                "stream-format", sndFormat,
                "stream-rate", streamfreq,
                "stream-bits", (stream16bit ? "16" : "8"),
                "stream-stereo", (streamStereo ? "yes" : "no"),
                "average-sample-count", Integer.toString(averageSampleCount)
                });
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
        soundStreamHead("1",
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
        soundStreamHead("2",
                playbackFrequency, playback16bit, playbackStereo,
                streamFormat, streamFrequency, stream16bit, streamStereo,
                averageSampleCount);
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  soundData        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagSoundStreamBlock(byte[] soundData) throws IOException {
        start("sound-stream-block", null);
        text(Base64.encode(soundData));
        end();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  serialNumber     Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagSerialNumber(String serialNumber) throws IOException {
        start("serial-number", null);
        text(serialNumber);
        end();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  data             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagGenerator(byte[] data) throws IOException {
        start("generator", null);
        text(Base64.encode(data));
        end();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  data             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagGeneratorText(byte[] data) throws IOException {
        start("generator-text", null);
        text(Base64.encode(data));
        end();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  data             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagGeneratorCommand(byte[] data) throws IOException {
        start("generator-command", null);
        text(Base64.encode(data));
        end();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  data             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagGeneratorFont(byte[] data) throws IOException {
        start("generator-font", null);
        text(Base64.encode(data));
        end();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  data             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagNameCharacter(byte[] data) throws IOException {
        start("generator-name-character", null);
        text(Base64.encode(data));
        end();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@param  imageData        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineBits(int id, byte[] imageData) throws IOException {
        start("jpeg", new String[]
                {
                "id", Integer.toString(id),
                "common-header", "yes"
                });
        start("image", null);
        text(Base64.encode(imageData));
        end();
        end();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  jpegEncodingData  Description of the Parameter
     *@exception  IOException   Description of the Exception
     */
    public void tagJPEGTables(byte[] jpegEncodingData) throws IOException {
        start("jpeg-header", null);
        text(Base64.encode(jpegEncodingData));
        end();
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
        start("jpeg", new String[]
                {
                "id", Integer.toString(id)
                });

        start("image", null);
        text(Base64.encode(imageData));
        end();

        start("alpha", null);
        text(Base64.encode(alphaData));
        end();

        end();
    }


    /**
     *  SWFTagTypes interface
     *
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    public SWFActions tagDoAction() throws IOException {
        actionsType = 1;
        //frame actions
        return this;
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
        start("shape", new String[]
                {
                "id", Integer.toString(id),
                "has-alpha", "no",
                "min-x", Double.toString(((double) outline.getMinX()) / SWFConstants.TWIPS),
                "min-y", Double.toString(((double) outline.getMinY()) / SWFConstants.TWIPS),
                "max-x", Double.toString(((double) outline.getMaxX()) / SWFConstants.TWIPS),
                "max-y", Double.toString(((double) outline.getMaxY()) / SWFConstants.TWIPS)
                });

        return this;
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
        start("shape", new String[]
                {
                "id", Integer.toString(id),
                "has-alpha", "no",
                "min-x", Double.toString(((double) outline.getMinX()) / SWFConstants.TWIPS),
                "min-y", Double.toString(((double) outline.getMinY()) / SWFConstants.TWIPS),
                "max-x", Double.toString(((double) outline.getMaxX()) / SWFConstants.TWIPS),
                "max-y", Double.toString(((double) outline.getMaxY()) / SWFConstants.TWIPS)
                });

        return this;
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
        start("shape", new String[]
                {
                "id", Integer.toString(id),
                "has-alpha", "yes",
                "min-x", Double.toString(((double) outline.getMinX()) / SWFConstants.TWIPS),
                "min-y", Double.toString(((double) outline.getMinY()) / SWFConstants.TWIPS),
                "max-x", Double.toString(((double) outline.getMaxX()) / SWFConstants.TWIPS),
                "max-y", Double.toString(((double) outline.getMaxY()) / SWFConstants.TWIPS)
                });

        return this;
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  charId           Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagFreeCharacter(int charId) throws IOException {
        element("release", new String[]{"id", Integer.toString(charId)});
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
        start("place", new String[]
                {
                "id", Integer.toString(charId),
                "depth", Integer.toString(depth)
                });

        if (matrix != null) {
            writeMatrix(matrix);
        }
        if (cxform != null) {
            writeCXForm(cxform);
        }

        end();
        //place
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
        Vector attrs = new Vector();

        if (charId > 0) {
            attrs.add("id");
            attrs.add(Integer.toString(charId));
        }

        if (isMove) {
            attrs.add("alter");
            attrs.add("yes");
        }

        if (clipDepth > 0) {
            attrs.add("clip-depth");
            attrs.add(Integer.toString(clipDepth));
        }

        if (ratio >= 0) {
            attrs.add("ratio");
            attrs.add(Integer.toString(ratio));
        }

        attrs.add("depth");
        attrs.add(Integer.toString(depth));

        if (name != null && name.length() > 0) {
            attrs.add("name");
            attrs.add(name);
        }

        String[] attributes = new String[attrs.size()];
        attrs.copyInto(attributes);

        start("place", attributes);

        if (matrix != null) {
            writeMatrix(matrix);
        }
        if (cxform != null) {
            writeCXForm(cxform);
        }

        if (clipActionFlags == 0) {
            end();
            //place
            return null;
        }

        actionsType = 2;
        //clip actions
        return this;
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  charId           Description of the Parameter
     *@param  depth            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagRemoveObject(int charId, int depth) throws IOException {
        element("remove", new String[]{"depth", Integer.toString(depth)});
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  depth            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagRemoveObject2(int depth) throws IOException {
        element("remove", new String[]{"depth", Integer.toString(depth)});
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  color            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagSetBackgroundColor(Color color) throws IOException {
        element("background-color", new String[]
                {
                "red", Integer.toString(color.getRed()),
                "green", Integer.toString(color.getGreen()),
                "blue", Integer.toString(color.getBlue())
                });
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  label            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagFrameLabel(String label) throws IOException {
        frameLabel = label;
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    public SWFTagTypes tagDefineSprite(int id) throws IOException {
        start("sprite", new String[]{"id", Integer.toString(id)});
        definingSprite = true;
        return this;
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  password         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagProtect(byte[] password) throws IOException {
        element("protect", (password == null || password.length == 0) ?
                null :
                new String[]
                {
                "password", Base64.encode(password)
                });
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  password         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagEnableDebug(byte[] password) throws IOException {
        element("enable-debug", (password == null || password.length == 0) ?
                null :
                new String[]
                {
                "password", Base64.encode(password)
                });
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
        start("text-font", new String[]{"id", Integer.toString(id)});
        glyphCount = numGlyphs;
        start("glyph", null);
        return this;
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
        StringBuffer buff = new StringBuffer();

        if ((flags & SWFConstants.FONT_UNICODE) != 0) {
            buff.append("unicode");
        }
        if ((flags & SWFConstants.FONT_SHIFTJIS) != 0) {
            buff.append(" shiftjis");
        }
        if ((flags & SWFConstants.FONT_ANSI) != 0) {
            buff.append(" ansi");
        }
        if ((flags & SWFConstants.FONT_ITALIC) != 0) {
            buff.append(" italic");
        }
        if ((flags & SWFConstants.FONT_BOLD) != 0) {
            buff.append(" bold");
        }

        start("font-info", new String[]
                {
                "id", Integer.toString(fontId),
                "name", fontName,
                "flags", buff.toString().trim()
                });

        for (int i = 0; i < codes.length; i++) {
            element("code", new String[]{"code", Integer.toString(codes[i])});
        }

        end();
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
        StringBuffer buff = new StringBuffer();

        if ((flags & SWFConstants.FONT2_UNICODE) != 0) {
            buff.append("unicode");
        }
        if ((flags & SWFConstants.FONT2_SHIFTJIS) != 0) {
            buff.append(" shiftjis");
        }
        if ((flags & SWFConstants.FONT2_ANSI) != 0) {
            buff.append(" ansi");
        }
        if ((flags & SWFConstants.FONT2_ITALIC) != 0) {
            buff.append(" italic");
        }
        if ((flags & SWFConstants.FONT2_BOLD) != 0) {
            buff.append(" bold");
        }

        start("edit-font", new String[]
                {
                "id", Integer.toString(id),
                "flags", buff.toString().trim(),
                "name", name,
                "ascent", Double.toString(((double) ascent) / SWFConstants.TWIPS),
                "descent", Double.toString(((double) descent) / SWFConstants.TWIPS),
                "leading", Double.toString(((double) leading) / SWFConstants.TWIPS)
                });

        if (kernCodes1 != null && kernCodes1.length > 0) {
            start("kerning-info", null);

            for (int i = 0; i < kernCodes1.length; i++) {
                element("kerning", new String[]
                        {
                        "code-1", Integer.toString(kernCodes1[i]),
                        "code-2", Integer.toString(kernCodes2[i]),
                        "offset", Double.toString(((double) kernAdjustments[i]) / SWFConstants.TWIPS)
                        });
            }

            end();
        }

        if (numGlyphs == 0) {
            end();
            return null;
        }

        glyphAdvances = advances;
        glyphRects = bounds;
        glyphCodes = codes;
        glyphCount = numGlyphs;

        Rect bound = bounds[0];
        int advance = 0;
        if (advances != null && advances.length >= 1) {
            advance = advances[0];
        }

        start("glyph", new String[]
                {
                "code", Integer.toString(glyphCodes[0]),
                "advance", Double.toString(((double) advance) / SWFConstants.TWIPS),
                "min-x", Double.toString(((double) bound.getMinX()) / SWFConstants.TWIPS),
                "min-y", Double.toString(((double) bound.getMinY()) / SWFConstants.TWIPS),
                "max-x", Double.toString(((double) bound.getMaxX()) / SWFConstants.TWIPS),
                "max-y", Double.toString(((double) bound.getMaxY()) / SWFConstants.TWIPS)
                });

        return this;
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
        String align = null;
        if (alignment == SWFConstants.TEXTFIELD_ALIGN_CENTER) {
            align = "center";
        } else if (alignment == SWFConstants.TEXTFIELD_ALIGN_JUSTIFY) {
            align = "justify";
        } else if (alignment == SWFConstants.TEXTFIELD_ALIGN_LEFT) {
            align = "left";
        } else if (alignment == SWFConstants.TEXTFIELD_ALIGN_RIGHT) {
            align = "right";
        }

        start("edit-field", new String[]
                {
                "id", Integer.toString(fieldId),
                "font", Integer.toString(fontId),
                "size", Double.toString(((double) fontSize) / SWFConstants.TWIPS),
                "limit", (charLimit > 0) ? Integer.toString(charLimit) : null,
                "left-margin", Double.toString(((double) leftMargin) / SWFConstants.TWIPS),
                "right-margin", Double.toString(((double) rightMargin) / SWFConstants.TWIPS),
                "indentation", Double.toString(((double) indentation) / SWFConstants.TWIPS),
                "linespacing", Double.toString(((double) lineSpacing) / SWFConstants.TWIPS),
                "name", fieldName,
                "text", initialText,
                "align", align,
                "min-x", Double.toString(((double) boundary.getMinX()) / SWFConstants.TWIPS),
                "min-y", Double.toString(((double) boundary.getMinY()) / SWFConstants.TWIPS),
                "max-x", Double.toString(((double) boundary.getMaxX()) / SWFConstants.TWIPS),
                "max-y", Double.toString(((double) boundary.getMaxY()) / SWFConstants.TWIPS),
                "selectable", ((flags & SWFConstants.TEXTFIELD_NO_SELECTION) != 0) ? "no" : "yes",
                "border", ((flags & SWFConstants.TEXTFIELD_DRAW_BORDER) != 0) ? "yes" : "no",
                "html", ((flags & SWFConstants.TEXTFIELD_HTML) != 0) ? "yes" : "no",
                "system-font", ((flags & SWFConstants.TEXTFIELD_FONT_GLYPHS) != 0) ? "no" : "yes",
                "wordwrap", ((flags & SWFConstants.TEXTFIELD_WORD_WRAP) != 0) ? "yes" : "no",
                "multiline", ((flags & SWFConstants.TEXTFIELD_IS_MULTILINE) != 0) ? "yes" : "no",
                "password", ((flags & SWFConstants.TEXTFIELD_IS_PASSWORD) != 0) ? "yes" : "no",
                "read-only", ((flags & SWFConstants.TEXTFIELD_DISABLE_EDIT) != 0) ? "yes" : "no"
                });

        writeColor(textColor);

        end();
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
        return defineText(id, bounds, matrix, false);
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
        return defineText(id, bounds, matrix, true);
    }


    /**
     *  Description of the Method
     *
     *@param  id               Description of the Parameter
     *@param  bounds           Description of the Parameter
     *@param  matrix           Description of the Parameter
     *@param  hasAlpha         Description of the Parameter
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    protected SWFText defineText(int id, Rect bounds, Matrix matrix, boolean hasAlpha)
             throws IOException {
        start("text", new String[]
                {
                "has-alpha", hasAlpha ? "yes" : "no",
                "id", Integer.toString(id),
                "min-x", Double.toString(((double) bounds.getMinX()) / SWFConstants.TWIPS),
                "min-y", Double.toString(((double) bounds.getMinY()) / SWFConstants.TWIPS),
                "max-x", Double.toString(((double) bounds.getMaxX()) / SWFConstants.TWIPS),
                "max-y", Double.toString(((double) bounds.getMaxY()) / SWFConstants.TWIPS)
                });

        writeMatrix(matrix);

        return this;
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
        start("button", new String[]
                {
                "id", Integer.toString(id),
                "menu", "no"
                });

        for (Iterator e = buttonRecords.iterator(); e.hasNext(); ) {
            ButtonRecord rec = (ButtonRecord) e.next();

            StringBuffer buff = new StringBuffer();
            if (rec.isHitTest()) {
                buff.append("hit");
            }
            if (rec.isOver()) {
                buff.append(" over");
            }
            if (rec.isUp()) {
                buff.append(" up");
            }
            if (rec.isDown()) {
                buff.append(" down");
            }

            start("layer", new String[]
                    {
                    "id", Integer.toString(rec.getCharId()),
                    "depth", Integer.toString(rec.getLayer()),
                    "for", buff.toString().trim()
                    });

            Matrix matrix = rec.getMatrix();

            if (matrix != null) {
                writeMatrix(matrix);
            }

            end();
        }

        this.actionsType = 3;
        return this;
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
        start("button-cxform", new String[]{"id", Integer.toString(buttonId)});
        writeCXForm(transform);
        end();
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
        start("button", new String[]
                {
                "id", Integer.toString(id),
                "menu", trackAsMenu ? "yes" : "no"
                });

        for (Iterator e = buttonRecord2s.iterator(); e.hasNext(); ) {
            ButtonRecord2 rec = (ButtonRecord2) e.next();

            StringBuffer buff = new StringBuffer();
            if (rec.isHitTest()) {
                buff.append("hit");
            }
            if (rec.isOver()) {
                buff.append(" over");
            }
            if (rec.isUp()) {
                buff.append(" up");
            }
            if (rec.isDown()) {
                buff.append(" down");
            }

            start("layer", new String[]
                    {
                    "id", Integer.toString(rec.getCharId()),
                    "depth", Integer.toString(rec.getLayer()),
                    "for", buff.toString().trim()
                    });

            Matrix matrix = rec.getMatrix();
            AlphaTransform cxform = rec.getTransform();

            if (matrix != null) {
                writeMatrix(matrix);
            }
            if (cxform != null) {
                writeCXForm(cxform);
            }

            end();
        }

        this.actionsType = 3;
        return this;
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  names            Description of the Parameter
     *@param  ids              Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagExport(String[] names, int[] ids) throws IOException {
        start("export", null);

        for (int i = 0; i < names.length; i++) {
            element("symbol", new String[]
                    {
                    "name", names[i],
                    "id", Integer.toString(ids[i])
                    });
        }

        end();
        //export
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
        start("import", new String[]{"movie", movieName});

        for (int i = 0; i < names.length; i++) {
            element("symbol", new String[]
                    {
                    "name", names[i],
                    "id", Integer.toString(ids[i])
                    });
        }

        end();
        //export
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@param  filename         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineQuickTimeMovie(int id, String filename) throws IOException {
        element("quicktime-movie", new String[]
                {
                "id", Integer.toString(id),
                "name", filename
                });
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@param  data             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineBitsJPEG2(int id, byte[] data) throws IOException {
        start("jpeg", new String[]{"id", Integer.toString(id)});

        start("image", null);
        text(Base64.encode(data));
        end();

        end();
    }


    /**
     *  SWFTagTypes interface
     *
     *@param  id               Description of the Parameter
     *@param  jpegImage        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tagDefineBitsJPEG2(int id, InputStream jpegImage) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        OutStream out = new OutStream(bout);

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

        tagDefineBitsJPEG2(id, bout.toByteArray());
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
        start("morph", new String[]
                {
                "id", Integer.toString(id)
                });

        start("shape", new String[]
                {
                "min-x", Double.toString(((double) startBounds.getMinX()) / SWFConstants.TWIPS),
                "min-y", Double.toString(((double) startBounds.getMinY()) / SWFConstants.TWIPS),
                "max-x", Double.toString(((double) startBounds.getMaxX()) / SWFConstants.TWIPS),
                "max-y", Double.toString(((double) startBounds.getMaxY()) / SWFConstants.TWIPS)
                });

        morphEndRect = endBounds;
        morph = true;
        return this;
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
        defineBitsLossless(id, format, width, height, colors, imageData, false);
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
        defineBitsLossless(id, format, width, height, colors, imageData, true);
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
    public void defineBitsLossless(int id, int format, int width, int height,
            Color[] colors, byte[] imageData,
            boolean hasAlpha)
             throws IOException {
        String bits = null;
        if (format == SWFConstants.BITMAP_FORMAT_8_BIT) {
            bits = "8";
        } else if (format == SWFConstants.BITMAP_FORMAT_16_BIT) {
            bits = "16";
        } else {
            bits = "32";
        }

        start("bitmap", new String[]
                {
                "id", Integer.toString(id),
                "width", Integer.toString(width),
                "height", Integer.toString(height),
                "has-alpha", hasAlpha ? "yes" : "no",
                "bits", bits
                });

        if (colors != null && colors.length > 0) {
            start("colors", null);

            for (int i = 0; i < colors.length; i++) {
                writeColor(colors[i]);
            }

            end();
        }

        start("pixels", null);
        text(Base64.encode(imageData));
        end();
        end();
    }


    /**
     *  SWFVectors interface SWFActions interface SWFText interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void done() throws IOException {
        //--do not terminate an action block here (it's done in end())
        if (actionsType == 1) {
            actionsType = 0;
            return;
        }

        actionsType = 0;
        end();

        if (morph) {
            if (morphEndRect != null) {
                start("shape", new String[]
                        {
                        "min-x", Double.toString(((double) morphEndRect.getMinX()) / SWFConstants.TWIPS),
                        "min-y", Double.toString(((double) morphEndRect.getMinY()) / SWFConstants.TWIPS),
                        "max-x", Double.toString(((double) morphEndRect.getMaxX()) / SWFConstants.TWIPS),
                        "max-y", Double.toString(((double) morphEndRect.getMaxY()) / SWFConstants.TWIPS)
                        });

                morphEndRect = null;
            } else {
                end();
                morph = false;
            }

            return;
        }

        if (glyphCount > 0) {
            if (glyphCount == 1) {
                //end of glyphs

                glyphCount = 0;
                glyphRects = null;
                glyphAdvances = null;
                glyphCodes = null;
                end();
                //font
            } else {
                if (glyphRects == null) {
                    start("glyph", null);
                } else {
                    int idx = glyphRects.length - glyphCount + 1;
                    Rect bound = glyphRects[idx];
                    int advance = 0;
                    if (glyphAdvances != null && glyphAdvances.length >= 1) {
                        advance = glyphAdvances[idx];
                    }

                    start("glyph", new String[]
                            {
                            "code", Integer.toString(glyphCodes[idx]),
                            "advance", Double.toString(((double) advance) / SWFConstants.TWIPS),
                            "min-x", Double.toString(((double) bound.getMinX()) / SWFConstants.TWIPS),
                            "min-y", Double.toString(((double) bound.getMinY()) / SWFConstants.TWIPS),
                            "max-x", Double.toString(((double) bound.getMaxX()) / SWFConstants.TWIPS),
                            "max-y", Double.toString(((double) bound.getMaxY()) / SWFConstants.TWIPS)
                            });
                }
            }

            glyphCount--;
        }
    }


    /**
     *  SWFVectors interface
     *
     *@param  dx               Description of the Parameter
     *@param  dy               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void line(int dx, int dy) throws IOException {
        element("line", new String[]
                {
                "dx", Double.toString(((double) dx) / SWFConstants.TWIPS),
                "dy", Double.toString(((double) dy) / SWFConstants.TWIPS)
                });
    }


    /**
     *  SWFVectors interface
     *
     *@param  cx               Description of the Parameter
     *@param  cy               Description of the Parameter
     *@param  dx               Description of the Parameter
     *@param  dy               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void curve(int cx, int cy, int dx, int dy) throws IOException {
        element("curve", new String[]
                {
                "cx", Double.toString(((double) cx) / SWFConstants.TWIPS),
                "cy", Double.toString(((double) cy) / SWFConstants.TWIPS),
                "dx", Double.toString(((double) dx) / SWFConstants.TWIPS),
                "dy", Double.toString(((double) dy) / SWFConstants.TWIPS)
                });
    }


    /**
     *  SWFVectors interface
     *
     *@param  x                Description of the Parameter
     *@param  y                Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void move(int x, int y) throws IOException {
        element("move", new String[]
                {
                "x", Double.toString(((double) x) / SWFConstants.TWIPS),
                "y", Double.toString(((double) y) / SWFConstants.TWIPS)
                });
    }


    /**
     *  SWFShape interface
     *
     *@param  styleIndex       The new fillStyle0 value
     *@exception  IOException  Description of the Exception
     */
    public void setFillStyle0(int styleIndex) throws IOException {
        if (glyphCount > 0) {
            element("anticlockwise", null);
            return;
        }

        element("set-primary-fill", new String[]
                {
                "index", Integer.toString(styleIndex)
                });
    }


    /**
     *  SWFShape interface
     *
     *@param  styleIndex       The new fillStyle1 value
     *@exception  IOException  Description of the Exception
     */
    public void setFillStyle1(int styleIndex) throws IOException {
        if (glyphCount > 0) {
            return;
        }

        element("set-secondary-fill", new String[]
                {
                "index", Integer.toString(styleIndex)
                });
    }


    /**
     *  SWFShape interface
     *
     *@param  styleIndex       The new lineStyle value
     *@exception  IOException  Description of the Exception
     */
    public void setLineStyle(int styleIndex) throws IOException {
        if (glyphCount > 0) {
            return;
        }

        element("set-line-style", new String[]
                {
                "index", Integer.toString(styleIndex)
                });
    }


    /**
     *  SWFShape interface
     *
     *@param  color            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void defineFillStyle(Color color) throws IOException {
        start("color-fill", null);
        writeColor(color);
        end();
    }


    /**
     *  SWFShape interface
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
        start("gradient-fill", new String[]{"radial", radial ? "yes" : "no"});
        writeMatrix(matrix);

        start("gradient", null);
        for (int i = 0; i < ratios.length; i++) {
            start("step", new String[]{"ratio", Integer.toString(ratios[i])});
            writeColor(colors[i]);
            end();
            //step
        }
        end();
        //gradient
        end();
        //gradient-fill
    }


    /**
     *  SWFShape interface
     *
     *@param  bitmapId         Description of the Parameter
     *@param  matrix           Description of the Parameter
     *@param  clipped          Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void defineFillStyle(int bitmapId, Matrix matrix, boolean clipped)
             throws IOException {
        start("image-fill", new String[]
                {
                "clipped", clipped ? "yes" : "no",
                "image-id", Integer.toString(bitmapId)
                });

        writeMatrix(matrix);

        end();
        //image-fill
    }


    /**
     *  SWFShape interface
     *
     *@param  width            Description of the Parameter
     *@param  color            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void defineLineStyle(int width, Color color) throws IOException {
        start("line-style", new String[]
                {
                "width", Double.toString(((double) width) / SWFConstants.TWIPS)
                });

        writeColor(color);

        end();
        //line-style
    }


    /**
     *  Description of the Method
     *
     *@param  matrix           Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void writeMatrix(Matrix matrix) throws IOException {
        element("matrix", new String[]
                {
                "skew0", Double.toString(matrix.getSkew0()),
                "skew1", Double.toString(matrix.getSkew1()),
                "scale-x", Double.toString(matrix.getScaleX()),
                "scale-y", Double.toString(matrix.getScaleY()),
                "x", Double.toString(matrix.getTranslateX() / SWFConstants.TWIPS),
                "y", Double.toString(matrix.getTranslateY() / SWFConstants.TWIPS)
                });
    }


    /**
     *  Description of the Method
     *
     *@param  color            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void writeColor(Color color) throws IOException {
        if (color instanceof AlphaColor) {
            AlphaColor acolor = (AlphaColor) color;

            element("color", new String[]
                    {
                    "red", Integer.toString(acolor.getRed()),
                    "green", Integer.toString(acolor.getGreen()),
                    "blue", Integer.toString(acolor.getBlue()),
                    "alpha", Integer.toString(acolor.getAlpha())
                    });
        } else {
            element("color", new String[]
                    {
                    "red", Integer.toString(color.getRed()),
                    "green", Integer.toString(color.getGreen()),
                    "blue", Integer.toString(color.getBlue())
                    });
        }
    }


    /**
     *  Description of the Method
     *
     *@param  cxform           Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void writeCXForm(ColorTransform cxform) throws IOException {
        Vector attrs = new Vector();

        int addRed = cxform.getAddRed();
        int addGreen = cxform.getAddGreen();
        int addBlue = cxform.getAddBlue();
        double multRed = cxform.getMultRed();
        double multGreen = cxform.getMultGreen();
        double multBlue = cxform.getMultBlue();

        if (addRed != 0) {
            attrs.add("add-red");
            attrs.add(Integer.toString(addRed));
        }

        if (addGreen != 0) {
            attrs.add("add-green");
            attrs.add(Integer.toString(addGreen));
        }

        if (addBlue != 0) {
            attrs.add("add-blue");
            attrs.add(Integer.toString(addBlue));
        }

        if (multRed != 1.0) {
            attrs.add("mult-red");
            attrs.add(Double.toString(multRed));
        }

        if (multGreen != 1.0) {
            attrs.add("mult-green");
            attrs.add(Double.toString(multGreen));
        }

        if (multBlue != 1.0) {
            attrs.add("mult-blue");
            attrs.add(Double.toString(multBlue));
        }

        if (cxform instanceof AlphaTransform) {
            AlphaTransform axform = (AlphaTransform) cxform;

            int addAlpha = axform.getAddAlpha();
            double multAlpha = axform.getMultAlpha();

            if (addAlpha != 0) {
                attrs.add("add-alpha");
                attrs.add(Integer.toString(addAlpha));
            }

            if (multAlpha != 1.0) {
                attrs.add("mult-alpha");
                attrs.add(Double.toString(multAlpha));
            }
        }

        String[] attributes = new String[attrs.size()];
        attrs.copyInto(attributes);

        element("color-transform", attributes);
    }


    /**
     *  SWFActions interface
     *
     *@param  flags            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void start(int flags2) throws IOException {
        String[] attrs = null;
        int flags = flags2;
        if (actionsType == 2) {
            //clip

            StringBuffer conds = new StringBuffer();

            if ((flags & SWFConstants.CLIP_ACTION_ON_LOAD) > 0) {
                conds.append("load");
            }
            if ((flags & SWFConstants.CLIP_ACTION_ENTER_FRAME) > 0) {
                conds.append(" enter-frame");
            }
            if ((flags & SWFConstants.CLIP_ACTION_UNLOAD) > 0) {
                conds.append(" unload");
            }
            if ((flags & SWFConstants.CLIP_ACTION_MOUSE_MOVE) > 0) {
                conds.append(" mouse-move");
            }
            if ((flags & SWFConstants.CLIP_ACTION_MOUSE_DOWN) > 0) {
                conds.append(" mouse-down");
            }
            if ((flags & SWFConstants.CLIP_ACTION_MOUSE_UP) > 0) {
                conds.append(" mouse-up");
            }
            if ((flags & SWFConstants.CLIP_ACTION_KEY_DOWN) > 0) {
                conds.append(" key-down");
            }
            if ((flags & SWFConstants.CLIP_ACTION_KEY_UP) > 0) {
                conds.append(" key-up");
            }
            if ((flags & SWFConstants.CLIP_ACTION_DATA) > 0) {
                conds.append(" data");
            }

            String conditions = conds.toString().trim();
            attrs = new String[]{"conditions", conditions};
        } else if (actionsType == 3) {
            //button

            StringBuffer conds = new StringBuffer();

            if (flags == 0) {
                flags = SWFConstants.BUTTON2_OVERDOWN2OVERUP;
            }

            if ((flags & SWFConstants.BUTTON2_OVERDOWN2IDLE) > 0) {
                conds.append("menu-drag-out");
            }
            if ((flags & SWFConstants.BUTTON2_IDLE2OVERDOWN) > 0) {
                conds.append(" menu-drag-over");
            }
            if ((flags & SWFConstants.BUTTON2_OUTDOWN2IDLE) > 0) {
                conds.append(" release-outside");
            }
            if ((flags & SWFConstants.BUTTON2_OUTDOWN2OVERDOWN) > 0) {
                conds.append(" drag-over");
            }
            if ((flags & SWFConstants.BUTTON2_OVERDOWN2OUTDOWN) > 0) {
                conds.append(" drag-out");
            }
            if ((flags & SWFConstants.BUTTON2_OVERDOWN2OVERUP) > 0) {
                conds.append(" release");
            }
            if ((flags & SWFConstants.BUTTON2_OVERUP2OVERDOWN) > 0) {
                conds.append(" press");
            }
            if ((flags & SWFConstants.BUTTON2_OVERUP2IDLE) > 0) {
                conds.append(" roll-out");
            }
            if ((flags & SWFConstants.BUTTON2_IDLE2OVERUP) > 0) {
                conds.append(" roll-over");
            }

            String conditions = conds.toString().trim();

            if ((flags & 0xfe00) > 0) {
                //has a key press condition

                int charcode = (flags & 0xfe00) >> 9;

                attrs = new String[]
                        {
                        "conditions", conditions,
                        "char-code", Integer.toString(charcode)
                        };
            } else {
                attrs = new String[]{"conditions", conditions};
            }
        }

        start("actions", attrs);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void end() throws IOException {
        super.end();
    }


    /**
     *  SWFActions interface
     *
     *@param  blob             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void blob(byte[] blob) throws IOException {
        //not used
    }


    /**
     *  SWFActions interface
     *
     *@param  code             Description of the Parameter
     *@param  data             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void unknown(int code, byte[] data) throws IOException {
        start("unknown", new String[]{"code", Integer.toString(code)});
        text(Base64.encode(data));
        end();
    }


    /**
     *  SWFActions interface
     *
     *@param  label            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void jumpLabel(String label) throws IOException {
        element("jump-label", new String[]{"label", label});
    }


    /**
     *  SWFActions interface
     *
     *@param  comment          Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void comment(String comment) throws IOException {
        start("comment", null);
        text(comment);
        end();
    }


    /**
     *  SWFActions interface
     *
     *@param  frameNumber      Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void gotoFrame(int frameNumber) throws IOException {
        element("goto-frame", new String[]{"number",
                Integer.toString(frameNumber)});
    }


    /**
     *  SWFActions interface
     *
     *@param  label            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void gotoFrame(String label) throws IOException {
        element("goto-frame", new String[]{"label", label});
    }


    /**
     *  SWFActions interface
     *
     *@param  url              Description of the Parameter
     *@param  target           Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void getURL(String url, String target) throws IOException {
        element("get-url", new String[]
                {
                "url", url,
                "target", target
                });
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void nextFrame() throws IOException {
        element("next-frame", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void prevFrame() throws IOException {
        element("prev-frame", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void play() throws IOException {
        element("play", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void stop() throws IOException {
        element("stop", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void toggleQuality() throws IOException {
        element("toggle-quality", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void stopSounds() throws IOException {
        element("stop-sounds", null);
    }


    /**
     *  SWFActions interface
     *
     *@param  frameNumber      Description of the Parameter
     *@param  jumpLabel        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void waitForFrame(int frameNumber, String jumpLabel) throws IOException {
        element("wait-for-frame", new String[]
                {
                "number", Integer.toString(frameNumber),
                "jump-label", jumpLabel
                });
    }


    /**
     *  SWFActions interface
     *
     *@param  target           The new target value
     *@exception  IOException  Description of the Exception
     */
    public void setTarget(String target) throws IOException {
        element("set-target", new String[]{"target", target});
    }


    /**
     *  SWFActions interface
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void push(String value) throws IOException {
        element("push", new String[]{"string", value});
    }


    /**
     *  SWFActions interface
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void push(float value) throws IOException {
        element("push", new String[]{"float", Float.toString(value)});
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void pop() throws IOException {
        element("pop", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void add() throws IOException {
        element("add", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void substract() throws IOException {
        element("subtract", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void multiply() throws IOException {
        element("multiply", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void divide() throws IOException {
        element("divide", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void equals() throws IOException {
        element("equals", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void lessThan() throws IOException {
        element("less-than", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void and() throws IOException {
        element("and", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void or() throws IOException {
        element("or", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void not() throws IOException {
        element("not", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void stringEquals() throws IOException {
        element("string-equals", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void stringLength() throws IOException {
        element("string-length", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void concat() throws IOException {
        element("concat", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void substring() throws IOException {
        element("substring", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void stringLessThan() throws IOException {
        element("string-less-than", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void stringLengthMB() throws IOException {
        element("mutlibyte-string-length", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void substringMB() throws IOException {
        element("multibyte-substring", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void toInteger() throws IOException {
        element("to-integer", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void charToAscii() throws IOException {
        element("char-to-ascii", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void asciiToChar() throws IOException {
        element("ascii-to-char", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void charMBToAscii() throws IOException {
        element("mutlibyte-char-to-ascii", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void asciiToCharMB() throws IOException {
        element("ascii-to-multibyte-char", null);
    }


    /**
     *  SWFActions interface
     *
     *@param  jumpLabel        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void jump(String jumpLabel) throws IOException {
        element("jump", new String[]{"jump-label", jumpLabel});
    }


    /**
     *  SWFActions interface
     *
     *@param  jumpLabel        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void ifJump(String jumpLabel) throws IOException {
        element("if", new String[]{"jump-label", jumpLabel});
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void call() throws IOException {
        element("call", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void getVariable() throws IOException {
        element("get-variable", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void setVariable() throws IOException {
        element("set-variable", null);
    }


    /**
     *  SWFActions interface
     *
     *@param  sendVars         Description of the Parameter
     *@param  loadMode         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void getURL(int sendVars, int loadMode) throws IOException {
        String method = "none";
        if (sendVars == GET_URL_SEND_VARS_GET) {
            method = "get";
        } else if (sendVars == GET_URL_SEND_VARS_POST) {
            method = "post";
        }

        String mode = null;
        String target = null;

        if (loadMode == GET_URL_MODE_LOAD_MOVIE_INTO_SPRITE) {
            mode = "yes";
            target = "target-sprite";
        } else if (loadMode == GET_URL_MODE_LOAD_VARS_INTO_LEVEL) {
            mode = "level";
            target = "load-vars-into";
        } else if (loadMode == GET_URL_MODE_LOAD_VARS_INTO_SPRITE) {
            mode = "sprite";
            target = "load-vars-into";
        }

        element("get-url", new String[]
                {
                "send-vars", method,
                target, mode
                });
    }


    /**
     *  SWFActions interface
     *
     *@param  play             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void gotoFrame(boolean play) throws IOException {
        element("goto-frame", new String[]{"play", play ? "yes" : "no"});
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void setTarget() throws IOException {
        element("set-target", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void getProperty() throws IOException {
        element("get-property", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void setProperty() throws IOException {
        element("set-property", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void cloneSprite() throws IOException {
        element("clone-sprite", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void removeSprite() throws IOException {
        element("remove-sprite", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void startDrag() throws IOException {
        element("start-drag", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void endDrag() throws IOException {
        element("end-drag", null);
    }


    /**
     *  SWFActions interface
     *
     *@param  jumpLabel        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void waitForFrame(String jumpLabel) throws IOException {
        element("wait-for-frame", new String[]
                {
                "jump-label", jumpLabel
                });
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void trace() throws IOException {
        element("trace", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void getTime() throws IOException {
        element("get-time", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void randomNumber() throws IOException {
        element("random-number", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void callFunction() throws IOException {
        element("call-function", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void callMethod() throws IOException {
        element("call-method", null);
    }


    /**
     *  SWFActions interface
     *
     *@param  values           Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void lookupTable(String[] values) throws IOException {
        start("lookup-table", null);

        for (int i = 0; i < values.length; i++) {
            start("value", null);
            text(values[i]);
            end();
        }

        end();
    }


    /**
     *  SWFActions interface
     *
     *@param  name             Description of the Parameter
     *@param  paramNames       Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void startFunction(String name, String[] paramNames) throws IOException {
        StringBuffer parms = new StringBuffer();

        for (int i = 0; i < paramNames.length; i++) {
            parms.append(" ");
            parms.append(paramNames[i]);
        }

        start("function", new String[]
                {
                "name", name,
                "params", parms.toString().trim()
                });
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void endBlock() throws IOException {
        end();
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void defineLocalValue() throws IOException {
        element("define-local-value", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void defineLocal() throws IOException {
        element("define-local", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void deleteProperty() throws IOException {
        element("delete-property", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void deleteThreadVars() throws IOException {
        element("delete-thread-vars", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void enumerate() throws IOException {
        element("enumerate", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void typedEquals() throws IOException {
        element("typed-equals", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void getMember() throws IOException {
        element("get-member", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void initArray() throws IOException {
        element("init-array", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void initObject() throws IOException {
        element("init-object", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void newMethod() throws IOException {
        element("new-method", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void newObject() throws IOException {
        element("new-object", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void setMember() throws IOException {
        element("set-member", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void getTargetPath() throws IOException {
        element("get-target-path", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void startWith() throws IOException {
        start("with", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void convertToNumber() throws IOException {
        element("to-number", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void convertToString() throws IOException {
        element("to-string", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void typeOf() throws IOException {
        element("type-of", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void typedAdd() throws IOException {
        element("typed-add", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void typedLessThan() throws IOException {
        element("typed-less-than", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void modulo() throws IOException {
        element("modulo", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void bitAnd() throws IOException {
        element("bit-and", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void bitOr() throws IOException {
        element("bit-or", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void bitXor() throws IOException {
        element("bit-xor", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void shiftLeft() throws IOException {
        element("shift-left", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void shiftRight() throws IOException {
        element("shift-right", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void shiftRightUnsigned() throws IOException {
        element("shift-right-unsigned", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void decrement() throws IOException {
        element("decrement", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void increment() throws IOException {
        element("increment", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void duplicate() throws IOException {
        element("duplicate", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void returnValue() throws IOException {
        element("return", null);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void swap() throws IOException {
        element("swap", null);
    }


    /**
     *  SWFActions interface
     *
     *@param  registerNumber   Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void storeInRegister(int registerNumber) throws IOException {
        element("store", new String[]{"register", Integer.toString(registerNumber)});
    }


    /**
     *  SWFActions interface
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void push(double value) throws IOException {
        element("push", new String[]{"double", Double.toString(value)});
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void pushNull() throws IOException {
        element("push", null);
    }


    /**
     *  SWFActions interface
     *
     *@param  registerNumber   Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void pushRegister(int registerNumber) throws IOException {
        element("push", new String[]{"register", Integer.toString(registerNumber)});
    }


    /**
     *  SWFActions interface
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void push(boolean value) throws IOException {
        element("push", new String[]{"boolean", "" + value});
    }


    /**
     *  SWFActions interface
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void push(int value) throws IOException {
        element("push", new String[]{"int", Integer.toString(value)});
    }


    /**
     *  SWFActions interface
     *
     *@param  dictionaryIndex  Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void lookup(int dictionaryIndex) throws IOException {
        element("push", new String[]{"lookup", Integer.toString(dictionaryIndex)});
    }


    /**
     *  SWFText interface
     *
     *@param  fontId           Description of the Parameter
     *@param  textHeight       Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void font(int fontId, int textHeight) throws IOException {
        element("set-font", new String[]
                {
                "id", Integer.toString(fontId),
                "size", Double.toString(((double) textHeight) / SWFConstants.TWIPS)
                });
    }


    /**
     *  SWFText interface
     *
     *@param  color            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void color(Color color) throws IOException {
        writeColor(color);
    }


    /**
     *  SWFText interface
     *
     *@param  x                The new x value
     *@exception  IOException  Description of the Exception
     */
    public void setX(int x) throws IOException {
        element("set-x", new String[]
                {
                "x", Double.toString(((double) x) / SWFConstants.TWIPS)
                });
    }


    /**
     *  SWFText interface
     *
     *@param  y                The new y value
     *@exception  IOException  Description of the Exception
     */
    public void setY(int y) throws IOException {
        element("set-y", new String[]
                {
                "y", Double.toString(((double) y) / SWFConstants.TWIPS)
                });
    }


    /**
     *  SWFText interface
     *
     *@param  glyphIndices     Description of the Parameter
     *@param  glyphAdvances    Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void text(int[] glyphIndices, int[] glyphAdvances) throws IOException {
        for (int i = 0; i < glyphIndices.length; i++) {
            element("char", new String[]
                    {
                    "glyph-index", Integer.toString(glyphIndices[i]),
                    "advance", Double.toString(((double) glyphAdvances[i]) / SWFConstants.TWIPS)
                    });
        }
    }


    /**
     *  Read a SWF file (args[0]) and write XML out (args[1])
     *
     *@param  args           The command line arguments
     *@exception  Exception  Description of the Exception
     */
    public static void main(String[] args) throws Exception {
        OutputStream out = new FileOutputStream(args[1]);
        XMLWriter writer = new XMLWriter(out);
        SWFSaxParser parser = new SWFSaxParser(writer, writer);

        parser.parse(args[0]);

        out.close();
    }
}
