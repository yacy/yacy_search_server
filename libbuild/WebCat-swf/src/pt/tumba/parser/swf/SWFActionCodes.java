package pt.tumba.parser.swf;


/**
 *  Action Codes and associated constants
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public interface SWFActionCodes {
    /**
     *  Description of the Field
     */
    public final static int NEXT_FRAME = 0x04;
    //F3 ***
    /**
     *  Description of the Field
     */
    public final static int PREVIOUS_FRAME = 0x05;
    //F3 ***
    /**
     *  Description of the Field
     */
    public final static int PLAY = 0x06;
    //F3 ***
    /**
     *  Description of the Field
     */
    public final static int STOP = 0x07;
    //F3 ***
    /**
     *  Description of the Field
     */
    public final static int TOGGLE_QUALITY = 0x08;
    //F3 ***
    /**
     *  Description of the Field
     */
    public final static int STOP_SOUNDS = 0x09;
    //F3 ***
    /**
     *  Description of the Field
     */
    public final static int ADD = 0x0a;
    //F4
    /**
     *  Description of the Field
     */
    public final static int SUBTRACT = 0x0b;
    //F4
    /**
     *  Description of the Field
     */
    public final static int MULTIPLY = 0x0c;
    //F4
    /**
     *  Description of the Field
     */
    public final static int DIVIDE = 0x0d;
    //F4
    /**
     *  Description of the Field
     */
    public final static int EQUALS = 0x0e;
    //F4
    /**
     *  Description of the Field
     */
    public final static int LESS = 0x0f;
    //F4
    /**
     *  Description of the Field
     */
    public final static int AND = 0x10;
    //F4
    /**
     *  Description of the Field
     */
    public final static int OR = 0x11;
    //F4
    /**
     *  Description of the Field
     */
    public final static int NOT = 0x12;
    //F4
    /**
     *  Description of the Field
     */
    public final static int STRING_EQUALS = 0x13;
    //F4
    /**
     *  Description of the Field
     */
    public final static int STRING_LENGTH = 0x14;
    //F4
    /**
     *  Description of the Field
     */
    public final static int STRING_EXTRACT = 0x15;
    //F4

    /**
     *  Description of the Field
     */
    public final static int POP = 0x17;
    //F4
    /**
     *  Description of the Field
     */
    public final static int TO_INTEGER = 0x18;
    //F4

    /**
     *  Description of the Field
     */
    public final static int GET_VARIABLE = 0x1c;
    //F4
    /**
     *  Description of the Field
     */
    public final static int SET_VARIABLE = 0x1d;
    //F4

    /**
     *  Description of the Field
     */
    public final static int SET_TARGET_2 = 0x20;
    //F4
    /**
     *  Description of the Field
     */
    public final static int STRING_ADD = 0x21;
    //F4
    /**
     *  Description of the Field
     */
    public final static int GET_PROPERTY = 0x22;
    //F4
    /**
     *  Description of the Field
     */
    public final static int SET_PROPERTY = 0x23;
    //F4
    /**
     *  Description of the Field
     */
    public final static int CLONE_SPRITE = 0x24;
    //F4
    /**
     *  Description of the Field
     */
    public final static int REMOVE_SPRITE = 0x25;
    //F4
    /**
     *  Description of the Field
     */
    public final static int TRACE = 0x26;
    //F4
    /**
     *  Description of the Field
     */
    public final static int START_DRAG = 0x27;
    //F4
    /**
     *  Description of the Field
     */
    public final static int END_DRAG = 0x28;
    //F4
    /**
     *  Description of the Field
     */
    public final static int STRING_LESS = 0x29;
    //F4

    /**
     *  Description of the Field
     */
    public final static int RANDOM_NUMBER = 0x30;
    //F4
    /**
     *  Description of the Field
     */
    public final static int MB_STRING_LENGTH = 0x31;
    //F4
    /**
     *  Description of the Field
     */
    public final static int CHAR_TO_ASCII = 0x32;
    //F4
    /**
     *  Description of the Field
     */
    public final static int ASCII_TO_CHAR = 0x33;
    //F4
    /**
     *  Description of the Field
     */
    public final static int GET_TIME = 0x34;
    //F4
    /**
     *  Description of the Field
     */
    public final static int MB_STRING_EXTRACT = 0x35;
    //F4
    /**
     *  Description of the Field
     */
    public final static int MB_CHAR_TO_ASCII = 0x36;
    //F4
    /**
     *  Description of the Field
     */
    public final static int MB_ASCII_TO_CHAR = 0x37;
    //F4

    /**
     *  Description of the Field
     */
    public final static int DEL_VAR = 0x3a;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int DEL_THREAD_VARS = 0x3b;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int DEFINE_LOCAL_VAL = 0x3c;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int CALL_FUNCTION = 0x3d;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int RETURN = 0x3e;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int MODULO = 0x3f;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int NEW_OBJECT = 0x40;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int DEFINE_LOCAL = 0x41;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int INIT_ARRAY = 0x42;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int INIT_OBJECT = 0x43;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int TYPEOF = 0x44;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int GET_TARGET_PATH = 0x45;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int ENUMERATE = 0x46;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int TYPED_ADD = 0x47;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int TYPED_LESS_THAN = 0x48;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int TYPED_EQUALS = 0x49;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int CONVERT_TO_NUMBER = 0x4a;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int CONVERT_TO_STRING = 0x4b;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int DUPLICATE = 0x4c;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int SWAP = 0x4d;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int GET_MEMBER = 0x4e;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int SET_MEMBER = 0x4f;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int INCREMENT = 0x50;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int DECREMENT = 0x51;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int CALL_METHOD = 0x52;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int CALL_NEW_METHOD = 0x53;
    //F5 ---

    /**
     *  Description of the Field
     */
    public final static int BIT_AND = 0x60;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int BIT_OR = 0x61;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int BIT_XOR = 0x62;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int SHIFT_LEFT = 0x63;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int SHIFT_RIGHT = 0x64;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int SHIFT_UNSIGNED = 0x65;
    //F5 ---

    /**
     *  Description of the Field
     */
    public final static int GOTO_FRAME = 0x81;
    //F3 ***

    /**
     *  Description of the Field
     */
    public final static int GET_URL = 0x83;
    //F3 ***

    /**
     *  Description of the Field
     */
    public final static int REGISTER = 0x87;
    //F5 ---
    /**
     *  Description of the Field
     */
    public final static int LOOKUP_TABLE = 0x88;
    //F5 ---

    /**
     *  Description of the Field
     */
    public final static int WAIT_FOR_FRAME = 0x8a;
    //F3 ***
    /**
     *  Description of the Field
     */
    public final static int SET_TARGET = 0x8b;
    //F3 ***
    /**
     *  Description of the Field
     */
    public final static int GOTO_LABEL = 0x8c;
    //F3 ***
    /**
     *  Description of the Field
     */
    public final static int WAIT_FOR_FRAME_2 = 0x8d;
    //F4

    /**
     *  Description of the Field
     */
    public final static int WITH = 0x94;
    //F5 ---

    /**
     *  Description of the Field
     */
    public final static int PUSH = 0x96;
    //F4

    /**
     *  Description of the Field
     */
    public final static int JUMP = 0x99;
    //F4
    /**
     *  Description of the Field
     */
    public final static int GET_URL_2 = 0x9a;
    //F4
    /**
     *  Description of the Field
     */
    public final static int DEFINE_FUNCTION = 0x9b;
    //F5 ---

    /**
     *  Description of the Field
     */
    public final static int IF = 0x9d;
    //F4
    /**
     *  Description of the Field
     */
    public final static int CALL = 0x9e;
    //F4
    /**
     *  Description of the Field
     */
    public final static int GOTO_FRAME_2 = 0x9f;
    //F4

    //--Property Constants
    /**
     *  Description of the Field
     */
    public final static int PROP_X = 0;
    /**
     *  Description of the Field
     */
    public final static int PROP_Y = 1;
    /**
     *  Description of the Field
     */
    public final static int PROP_XSCALE = 2;
    /**
     *  Description of the Field
     */
    public final static int PROP_YSCALE = 3;
    /**
     *  Description of the Field
     */
    public final static int PROP_CURRENTFRAME = 4;
    /**
     *  Description of the Field
     */
    public final static int PROP_TOTALFRAMES = 5;
    /**
     *  Description of the Field
     */
    public final static int PROP_ALPHA = 6;
    /**
     *  Description of the Field
     */
    public final static int PROP_VISIBLE = 7;
    /**
     *  Description of the Field
     */
    public final static int PROP_WIDTH = 8;
    /**
     *  Description of the Field
     */
    public final static int PROP_HEIGHT = 9;
    /**
     *  Description of the Field
     */
    public final static int PROP_ROTATION = 10;
    /**
     *  Description of the Field
     */
    public final static int PROP_TARGET = 11;
    /**
     *  Description of the Field
     */
    public final static int PROP_FRAMESLOADED = 12;
    /**
     *  Description of the Field
     */
    public final static int PROP_NAME = 13;
    /**
     *  Description of the Field
     */
    public final static int PROP_DROPTARGET = 14;
    /**
     *  Description of the Field
     */
    public final static int PROP_URL = 15;
    /**
     *  Description of the Field
     */
    public final static int PROP_HIGHQUALITY = 16;
    /**
     *  Description of the Field
     */
    public final static int PROP_FOCUSRECT = 17;
    /**
     *  Description of the Field
     */
    public final static int PROP_SOUNDBUFTIME = 18;
    /**
     *  Description of the Field
     */
    public final static int PROP_QUALITY = 19;
    //flash 5 only
    /**
     *  Description of the Field
     */
    public final static int PROP_XMOUSE = 20;
    //flash 5 only
    /**
     *  Description of the Field
     */
    public final static int PROP_YMOUSE = 21;
    //flash 5 only

    //--TypeOf Strings (from the ActionScript typeof() operator)
    /**
     *  Description of the Field
     */
    public final static String TYPEOF_NUMBER = "number";
    /**
     *  Description of the Field
     */
    public final static String TYPEOF_BOOLEAN = "boolean";
    /**
     *  Description of the Field
     */
    public final static String TYPEOF_STRING = "string";
    /**
     *  Description of the Field
     */
    public final static String TYPEOF_OBJECT = "object";
    /**
     *  Description of the Field
     */
    public final static String TYPEOF_MOVIECLIP = "movieclip";
    /**
     *  Description of the Field
     */
    public final static String TYPEOF_NULL = "null";
    /**
     *  Description of the Field
     */
    public final static String TYPEOF_UNDEFINED = "undefined";
    /**
     *  Description of the Field
     */
    public final static String TYPEOF_FUNCTION = "function";

    //--Types for Flash 5 push action
    /**
     *  Description of the Field
     */
    public final static int PUSHTYPE_STRING = 0;
    /**
     *  Description of the Field
     */
    public final static int PUSHTYPE_FLOAT = 1;
    /**
     *  Description of the Field
     */
    public final static int PUSHTYPE_NULL = 2;
    /**
     *  Description of the Field
     */
    public final static int PUSHTYPE_03 = 3;
    //unknown
    /**
     *  Description of the Field
     */
    public final static int PUSHTYPE_REGISTER = 4;
    /**
     *  Description of the Field
     */
    public final static int PUSHTYPE_BOOLEAN = 5;
    /**
     *  Description of the Field
     */
    public final static int PUSHTYPE_DOUBLE = 6;
    /**
     *  Description of the Field
     */
    public final static int PUSHTYPE_INTEGER = 7;
    /**
     *  Description of the Field
     */
    public final static int PUSHTYPE_LOOKUP = 8;
}
