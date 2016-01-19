/****************************************************************
 * Copyright (c) 2001, David N. Main, All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the 
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above 
 * copyright notice, this list of conditions and the following 
 * disclaimer. 
 * 
 * 2. Redistributions in binary form must reproduce the above 
 * copyright notice, this list of conditions and the following 
 * disclaimer in the documentation and/or other materials 
 * provided with the distribution.
 * 
 * 3. The name of the author may not be used to endorse or 
 * promote products derived from this software without specific 
 * prior written permission. 
 * 
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY 
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, 
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A 
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE 
 * AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT 
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR 
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, 
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ****************************************************************/
package com.anotherbigidea.flash;

/**
 * Various SWF Constant Values
 */
public interface SWFConstants
{
    public static final int TWIPS = 20;  //number of TWIPS per pixel
        
    public static final int TAG_END                  = 0;
    public static final int TAG_SHOWFRAME            = 1;
    public static final int TAG_DEFINESHAPE          = 2;
    public static final int TAG_FREECHARACTER        = 3;
    public static final int TAG_PLACEOBJECT          = 4;
    public static final int TAG_REMOVEOBJECT         = 5;
    public static final int TAG_DEFINEBITS           = 6;
    public static final int TAG_DEFINEBUTTON         = 7;
    public static final int TAG_JPEGTABLES           = 8;
    public static final int TAG_SETBACKGROUNDCOLOR   = 9;
    public static final int TAG_DEFINEFONT           = 10;
    public static final int TAG_DEFINETEXT           = 11;
    public static final int TAG_DOACTION             = 12;
    public static final int TAG_DEFINEFONTINFO       = 13;
    public static final int TAG_DEFINESOUND          = 14; 
    public static final int TAG_STARTSOUND           = 15;
    //???    public static final int TAG_DEFINEBUTTONSOUND    = 17;
    public static final int TAG_SOUNDSTREAMHEAD      = 18;
    public static final int TAG_SOUNDSTREAMBLOCK     = 19;
    public static final int TAG_DEFINEBITSLOSSLESS   = 20;  
    public static final int TAG_DEFINEBITSJPEG2      = 21;  
    public static final int TAG_DEFINESHAPE2         = 22;
    public static final int TAG_DEFINEBUTTONCXFORM   = 23;
    public static final int TAG_PROTECT              = 24;      //???
    public static final int TAG_PLACEOBJECT2         = 26;      //???
    public static final int TAG_REMOVEOBJECT2        = 28;      //???
    public static final int TAG_DEFINESHAPE3         = 32;  
    public static final int TAG_DEFINETEXT2          = 33;  
    public static final int TAG_DEFINEBUTTON2        = 34;  
    public static final int TAG_DEFINEBITSJPEG3      = 35;  
    public static final int TAG_DEFINEBITSLOSSLESS2  = 36;      public static final int TAG_DEFINETEXTFIELD      = 37;  
    public static final int TAG_DEFINEQUICKTIMEMOVIE = 38;
    public static final int TAG_DEFINESPRITE         = 39;  
    public static final int TAG_NAMECHARACTER        = 40;      public static final int TAG_SERIALNUMBER         = 41;  
    public static final int TAG_GENERATOR_TEXT       = 42;  
    public static final int TAG_FRAMELABEL           = 43;      //???
    public static final int TAG_SOUNDSTREAMHEAD2     = 45;  
    public static final int TAG_DEFINEMORPHSHAPE     = 46;      //???
    public static final int TAG_DEFINEFONT2          = 48;  
    public static final int TAG_TEMPLATECOMMAND      = 49;  
    //???
    public static final int TAG_FLASHGENERATOR       = 51;  
    public static final int TAG_GEN_EXTERNAL_FONT    = 52;  
    //???
    //???
    //???
    public static final int TAG_EXPORT               = 56;  
    public static final int TAG_IMPORT               = 57;      
    public static final int TAG_ENABLEDEBUG          = 58;      
	public static final int TAG_DOINITACTION         = 59;
	//???
	//???
	public static final int TAG_DEFINEFONTINFO2      = 62;
	//???
	public static final int TAG_ENABLEDEBUGGER2      = 64;      
    
    //--Fill Types
    public static final int FILL_SOLID           = 0x00;
    public static final int FILL_LINEAR_GRADIENT = 0x10;
    public static final int FILL_RADIAL_GRADIENT = 0x12;
    public static final int FILL_TILED_BITMAP    = 0x40;
    public static final int FILL_CLIPPED_BITMAP  = 0x41;    
    
    //--Clip Action Conditions
    public static final int CLIP_ACTION_ON_LOAD     = 0x01;
    public static final int CLIP_ACTION_ENTER_FRAME = 0x02;
    public static final int CLIP_ACTION_UNLOAD      = 0x04;
    public static final int CLIP_ACTION_MOUSE_MOVE  = 0x08;
    public static final int CLIP_ACTION_MOUSE_DOWN  = 0x10;
    public static final int CLIP_ACTION_MOUSE_UP    = 0x20;
    public static final int CLIP_ACTION_KEY_DOWN    = 0x40;
    public static final int CLIP_ACTION_KEY_UP      = 0x80;
    public static final int CLIP_ACTION_DATA        = 0x100; 
    
    //--Font Info flags
    public static final int FONT_UNICODE   = 0x20;
    public static final int FONT_SHIFTJIS  = 0x10;
    public static final int FONT_ANSI      = 0x08;
    public static final int FONT_ITALIC    = 0x04;
    public static final int FONT_BOLD      = 0x02;
    public static final int FONT_WIDECHARS = 0x01;    
    
    //--DefineFont2 flags
    public static final int FONT2_HAS_LAYOUT = 0x80;
    public static final int FONT2_SHIFTJIS   = 0x40;
    public static final int FONT2_UNICODE    = 0x20;
    public static final int FONT2_ANSI       = 0x10;
    public static final int FONT2_32OFFSETS  = 0x08;
    public static final int FONT2_WIDECHARS  = 0x04;
    public static final int FONT2_ITALIC     = 0x02;
    public static final int FONT2_BOLD       = 0x01;    
    
    //--Text Field flags
    public static final int TEXTFIELD_HAS_LAYOUT     = 0x2000;  //author always sets this
    public static final int TEXTFIELD_NO_SELECTION   = 0x1000;
    public static final int TEXTFIELD_DRAW_BORDER    = 0x0800;
    public static final int TEXTFIELD_HTML           = 0x0200;
    public static final int TEXTFIELD_FONT_GLYPHS    = 0x0100;
    public static final int TEXTFIELD_HAS_TEXT       = 0x0080;
    public static final int TEXTFIELD_WORD_WRAP      = 0x0040;
    public static final int TEXTFIELD_IS_MULTILINE   = 0x0020;
    public static final int TEXTFIELD_IS_PASSWORD    = 0x0010;
    public static final int TEXTFIELD_DISABLE_EDIT   = 0x0008;
    public static final int TEXTFIELD_HAS_TEXT_COLOR = 0x0004;  //author always sets this
    public static final int TEXTFIELD_LIMIT_CHARS    = 0x0002;
    public static final int TEXTFIELD_HAS_FONT       = 0x0001;  //author always sets this
    
    //--Text Field alignment
    public static final int TEXTFIELD_ALIGN_LEFT    = 0;
    public static final int TEXTFIELD_ALIGN_RIGHT   = 1;
    public static final int TEXTFIELD_ALIGN_CENTER  = 2;
    public static final int TEXTFIELD_ALIGN_JUSTIFY = 3;    
    
    //--Used by TagDefineText(2)..
    public static final int TEXT_HAS_FONT    = 0x08;
    public static final int TEXT_HAS_COLOR   = 0x04;
    public static final int TEXT_HAS_YOFFSET = 0x02;
    public static final int TEXT_HAS_XOFFSET = 0x01;
    
    //--Action Conditions for DefineButton2..
    public static final int BUTTON2_OVERDOWN2IDLE    = 0x100;
    public static final int BUTTON2_IDLE2OVERDOWN    = 0x080;
    public static final int BUTTON2_OUTDOWN2IDLE     = 0x040;
    public static final int BUTTON2_OUTDOWN2OVERDOWN = 0x020;
    public static final int BUTTON2_OVERDOWN2OUTDOWN = 0x010;
    public static final int BUTTON2_OVERDOWN2OVERUP  = 0x008;
    public static final int BUTTON2_OVERUP2OVERDOWN  = 0x004;
    public static final int BUTTON2_OVERUP2IDLE      = 0x002;
    public static final int BUTTON2_IDLE2OVERUP      = 0x001;    
    
    //--Formats for DefineBitsLossless..
    public static final int BITMAP_FORMAT_8_BIT  = 3;    
    public static final int BITMAP_FORMAT_16_BIT = 4;    
    public static final int BITMAP_FORMAT_32_BIT = 5;   
    
    //--Sound Constants..
    public static final int SOUND_FORMAT_RAW              = 0;
    public static final int SOUND_FORMAT_ADPCM            = 1;
    public static final int SOUND_FORMAT_MP3              = 2;
	public static final int SOUND_FORMAT_RAW_LITTLEENDIAN = 3;
	public static final int SOUND_FORMAT_NELLYMOSER       = 6;
	            
    public static final int SOUND_FREQ_5_5KHZ = 0;  //5.5 kHz
    public static final int SOUND_FREQ_11KHZ  = 1;
    public static final int SOUND_FREQ_22KHZ  = 2;
    public static final int SOUND_FREQ_44KHZ  = 3;         
    
    //--Language Codes for DefineFontInfo2       
	public static final int LANGUAGE_CODE_NONE                = 0;         
	public static final int LANGUAGE_CODE_LATIN               = 1;         
	public static final int LANGUAGE_CODE_JAPANESE            = 2;         
	public static final int LANGUAGE_CODE_KOREAN              = 3;         
	public static final int LANGUAGE_CODE_SIMPLIFIED_CHINESE  = 4;         
	public static final int LANGUAGE_CODE_TRADITIONAL_CHINESE = 5;         
	
	//--String Encodings
	public static final String STRING_ENCODING_PRE_MX = "US-ASCII";
	public static final String STRING_ENCODING_MX     = "UTF-8";
	
	//--MX Version number
	public static final int FLASH_MX_VERSION = 6;         
	
}
