package pt.tumba.parser.swf;


/**
 *  Various SWF Constant Values
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public interface SWFConstants {
    /**
     *  Description of the Field
     */
    public final static int TWIPS = 20;
    //number of TWIPS per pixel

    /**
     *  Description of the Field
     */
    public final static int TAG_END = 0;
    /**
     *  Description of the Field
     */
    public final static int TAG_SHOWFRAME = 1;
    /**
     *  Description of the Field
     */
    public final static int TAG_DEFINESHAPE = 2;
    /**
     *  Description of the Field
     */
    public final static int TAG_FREECHARACTER = 3;
    /**
     *  Description of the Field
     */
    public final static int TAG_PLACEOBJECT = 4;
    /**
     *  Description of the Field
     */
    public final static int TAG_REMOVEOBJECT = 5;
    /**
     *  Description of the Field
     */
    public final static int TAG_DEFINEBITS = 6;
    /**
     *  Description of the Field
     */
    public final static int TAG_DEFINEBUTTON = 7;
    /**
     *  Description of the Field
     */
    public final static int TAG_JPEGTABLES = 8;
    /**
     *  Description of the Field
     */
    public final static int TAG_SETBACKGROUNDCOLOR = 9;
    /**
     *  Description of the Field
     */
    public final static int TAG_DEFINEFONT = 10;
    /**
     *  Description of the Field
     */
    public final static int TAG_DEFINETEXT = 11;
    /**
     *  Description of the Field
     */
    public final static int TAG_DOACTION = 12;
    /**
     *  Description of the Field
     */
    public final static int TAG_DEFINEFONTINFO = 13;
    /**
     *  Description of the Field
     */
    public final static int TAG_DEFINESOUND = 14;
    /**
     *  Description of the Field
     */
    public final static int TAG_STARTSOUND = 15;
    //???
    /**
     *  Description of the Field
     */
    public final static int TAG_DEFINEBUTTONSOUND = 17;
    /**
     *  Description of the Field
     */
    public final static int TAG_SOUNDSTREAMHEAD = 18;
    /**
     *  Description of the Field
     */
    public final static int TAG_SOUNDSTREAMBLOCK = 19;
    /**
     *  Description of the Field
     */
    public final static int TAG_DEFINEBITSLOSSLESS = 20;
    /**
     *  Description of the Field
     */
    public final static int TAG_DEFINEBITSJPEG2 = 21;
    /**
     *  Description of the Field
     */
    public final static int TAG_DEFINESHAPE2 = 22;
    /**
     *  Description of the Field
     */
    public final static int TAG_DEFINEBUTTONCXFORM = 23;
    /**
     *  Description of the Field
     */
    public final static int TAG_PROTECT = 24;
    //???
    /**
     *  Description of the Field
     */
    public final static int TAG_PLACEOBJECT2 = 26;
    //???
    /**
     *  Description of the Field
     */
    public final static int TAG_REMOVEOBJECT2 = 28;
    //???
    /**
     *  Description of the Field
     */
    public final static int TAG_DEFINESHAPE3 = 32;
    /**
     *  Description of the Field
     */
    public final static int TAG_DEFINETEXT2 = 33;
    /**
     *  Description of the Field
     */
    public final static int TAG_DEFINEBUTTON2 = 34;
    /**
     *  Description of the Field
     */
    public final static int TAG_DEFINEBITSJPEG3 = 35;
    /**
     *  Description of the Field
     */
    public final static int TAG_DEFINEBITSLOSSLESS2 = 36;
    /**
     *  Description of the Field
     */
    public final static int TAG_DEFINETEXTFIELD = 37;
    /**
     *  Description of the Field
     */
    public final static int TAG_DEFINEQUICKTIMEMOVIE = 38;
    /**
     *  Description of the Field
     */
    public final static int TAG_DEFINESPRITE = 39;
    /**
     *  Description of the Field
     */
    public final static int TAG_NAMECHARACTER = 40;
    /**
     *  Description of the Field
     */
    public final static int TAG_SERIALNUMBER = 41;
    /**
     *  Description of the Field
     */
    public final static int TAG_GENERATOR_TEXT = 42;
    /**
     *  Description of the Field
     */
    public final static int TAG_FRAMELABEL = 43;
    //???
    /**
     *  Description of the Field
     */
    public final static int TAG_SOUNDSTREAMHEAD2 = 45;
    /**
     *  Description of the Field
     */
    public final static int TAG_DEFINEMORPHSHAPE = 46;
    //???
    /**
     *  Description of the Field
     */
    public final static int TAG_DEFINEFONT2 = 48;
    /**
     *  Description of the Field
     */
    public final static int TAG_TEMPLATECOMMAND = 49;
    //???
    /**
     *  Description of the Field
     */
    public final static int TAG_FLASHGENERATOR = 51;
    /**
     *  Description of the Field
     */
    public final static int TAG_GEN_EXTERNAL_FONT = 52;
    //???
    //???
    //???
    /**
     *  Description of the Field
     */
    public final static int TAG_EXPORT = 56;
    /**
     *  Description of the Field
     */
    public final static int TAG_IMPORT = 57;
    /**
     *  Description of the Field
     */
    public final static int TAG_ENABLEDEBUG = 58;

    //--Fill Types
    /**
     *  Description of the Field
     */
    public final static int FILL_SOLID = 0x00;
    /**
     *  Description of the Field
     */
    public final static int FILL_LINEAR_GRADIENT = 0x10;
    /**
     *  Description of the Field
     */
    public final static int FILL_RADIAL_GRADIENT = 0x12;
    /**
     *  Description of the Field
     */
    public final static int FILL_TILED_BITMAP = 0x40;
    /**
     *  Description of the Field
     */
    public final static int FILL_CLIPPED_BITMAP = 0x41;

    //--Clip Action Conditions
    /**
     *  Description of the Field
     */
    public final static int CLIP_ACTION_ON_LOAD = 0x01;
    /**
     *  Description of the Field
     */
    public final static int CLIP_ACTION_ENTER_FRAME = 0x02;
    /**
     *  Description of the Field
     */
    public final static int CLIP_ACTION_UNLOAD = 0x04;
    /**
     *  Description of the Field
     */
    public final static int CLIP_ACTION_MOUSE_MOVE = 0x08;
    /**
     *  Description of the Field
     */
    public final static int CLIP_ACTION_MOUSE_DOWN = 0x10;
    /**
     *  Description of the Field
     */
    public final static int CLIP_ACTION_MOUSE_UP = 0x20;
    /**
     *  Description of the Field
     */
    public final static int CLIP_ACTION_KEY_DOWN = 0x40;
    /**
     *  Description of the Field
     */
    public final static int CLIP_ACTION_KEY_UP = 0x80;
    /**
     *  Description of the Field
     */
    public final static int CLIP_ACTION_DATA = 0x100;

    //--Font Info flags
    /**
     *  Description of the Field
     */
    public final static int FONT_UNICODE = 0x20;
    /**
     *  Description of the Field
     */
    public final static int FONT_SHIFTJIS = 0x10;
    /**
     *  Description of the Field
     */
    public final static int FONT_ANSI = 0x08;
    /**
     *  Description of the Field
     */
    public final static int FONT_ITALIC = 0x04;
    /**
     *  Description of the Field
     */
    public final static int FONT_BOLD = 0x02;
    /**
     *  Description of the Field
     */
    public final static int FONT_WIDECHARS = 0x01;

    //--DefineFont2 flags
    /**
     *  Description of the Field
     */
    public final static int FONT2_HAS_LAYOUT = 0x80;
    /**
     *  Description of the Field
     */
    public final static int FONT2_SHIFTJIS = 0x40;
    /**
     *  Description of the Field
     */
    public final static int FONT2_UNICODE = 0x20;
    /**
     *  Description of the Field
     */
    public final static int FONT2_ANSI = 0x10;
    /**
     *  Description of the Field
     */
    public final static int FONT2_32OFFSETS = 0x08;
    /**
     *  Description of the Field
     */
    public final static int FONT2_WIDECHARS = 0x04;
    /**
     *  Description of the Field
     */
    public final static int FONT2_ITALIC = 0x02;
    /**
     *  Description of the Field
     */
    public final static int FONT2_BOLD = 0x01;

    //--Text Field flags
    /**
     *  Description of the Field
     */
    public final static int TEXTFIELD_HAS_LAYOUT = 0x2000;
    //author always sets this
    /**
     *  Description of the Field
     */
    public final static int TEXTFIELD_NO_SELECTION = 0x1000;
    /**
     *  Description of the Field
     */
    public final static int TEXTFIELD_DRAW_BORDER = 0x0800;
    /**
     *  Description of the Field
     */
    public final static int TEXTFIELD_HTML = 0x0200;
    /**
     *  Description of the Field
     */
    public final static int TEXTFIELD_FONT_GLYPHS = 0x0100;
    /**
     *  Description of the Field
     */
    public final static int TEXTFIELD_HAS_TEXT = 0x0080;
    /**
     *  Description of the Field
     */
    public final static int TEXTFIELD_WORD_WRAP = 0x0040;
    /**
     *  Description of the Field
     */
    public final static int TEXTFIELD_IS_MULTILINE = 0x0020;
    /**
     *  Description of the Field
     */
    public final static int TEXTFIELD_IS_PASSWORD = 0x0010;
    /**
     *  Description of the Field
     */
    public final static int TEXTFIELD_DISABLE_EDIT = 0x0008;
    /**
     *  Description of the Field
     */
    public final static int TEXTFIELD_HAS_TEXT_COLOR = 0x0004;
    //author always sets this
    /**
     *  Description of the Field
     */
    public final static int TEXTFIELD_LIMIT_CHARS = 0x0002;
    /**
     *  Description of the Field
     */
    public final static int TEXTFIELD_HAS_FONT = 0x0001;
    //author always sets this

    //--Text Field alignment
    /**
     *  Description of the Field
     */
    public final static int TEXTFIELD_ALIGN_LEFT = 0;
    /**
     *  Description of the Field
     */
    public final static int TEXTFIELD_ALIGN_RIGHT = 1;
    /**
     *  Description of the Field
     */
    public final static int TEXTFIELD_ALIGN_CENTER = 2;
    /**
     *  Description of the Field
     */
    public final static int TEXTFIELD_ALIGN_JUSTIFY = 3;

    //--Used by TagDefineText(2)..
    /**
     *  Description of the Field
     */
    public final static int TEXT_HAS_FONT = 0x08;
    /**
     *  Description of the Field
     */
    public final static int TEXT_HAS_COLOR = 0x04;
    /**
     *  Description of the Field
     */
    public final static int TEXT_HAS_YOFFSET = 0x02;
    /**
     *  Description of the Field
     */
    public final static int TEXT_HAS_XOFFSET = 0x01;

    //--Action Conditions for DefineButton2..
    /**
     *  Description of the Field
     */
    public final static int BUTTON2_OVERDOWN2IDLE = 0x100;
    /**
     *  Description of the Field
     */
    public final static int BUTTON2_IDLE2OVERDOWN = 0x080;
    /**
     *  Description of the Field
     */
    public final static int BUTTON2_OUTDOWN2IDLE = 0x040;
    /**
     *  Description of the Field
     */
    public final static int BUTTON2_OUTDOWN2OVERDOWN = 0x020;
    /**
     *  Description of the Field
     */
    public final static int BUTTON2_OVERDOWN2OUTDOWN = 0x010;
    /**
     *  Description of the Field
     */
    public final static int BUTTON2_OVERDOWN2OVERUP = 0x008;
    /**
     *  Description of the Field
     */
    public final static int BUTTON2_OVERUP2OVERDOWN = 0x004;
    /**
     *  Description of the Field
     */
    public final static int BUTTON2_OVERUP2IDLE = 0x002;
    /**
     *  Description of the Field
     */
    public final static int BUTTON2_IDLE2OVERUP = 0x001;

    //--Formats for DefineBitsLossless..
    /**
     *  Description of the Field
     */
    public final static int BITMAP_FORMAT_8_BIT = 3;
    /**
     *  Description of the Field
     */
    public final static int BITMAP_FORMAT_16_BIT = 4;
    /**
     *  Description of the Field
     */
    public final static int BITMAP_FORMAT_32_BIT = 5;

    //--Sound Constants..
    /**
     *  Description of the Field
     */
    public final static int SOUND_FORMAT_RAW = 0;
    /**
     *  Description of the Field
     */
    public final static int SOUND_FORMAT_ADPCM = 1;
    /**
     *  Description of the Field
     */
    public final static int SOUND_FORMAT_MP3 = 2;

    /**
     *  Description of the Field
     */
    public final static int SOUND_FREQ_5_5KHZ = 0;
    //5.5 kHz
    /**
     *  Description of the Field
     */
    public final static int SOUND_FREQ_11KHZ = 1;
    /**
     *  Description of the Field
     */
    public final static int SOUND_FREQ_22KHZ = 2;
    /**
     *  Description of the Field
     */
    public final static int SOUND_FREQ_44KHZ = 3;

}
