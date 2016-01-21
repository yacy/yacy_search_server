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
 * Action Codes and associated constants
 */
public interface SWFActionCodes
{
    public static final int NEXT_FRAME        = 0x04; //F3 ***
    public static final int PREVIOUS_FRAME    = 0x05; //F3 ***
    public static final int PLAY              = 0x06; //F3 ***
    public static final int STOP              = 0x07; //F3 ***
    public static final int TOGGLE_QUALITY    = 0x08; //F3 ***
    public static final int STOP_SOUNDS       = 0x09; //F3 ***
    public static final int ADD               = 0x0a; //F4
    public static final int SUBTRACT          = 0x0b; //F4
    public static final int MULTIPLY          = 0x0c; //F4
    public static final int DIVIDE            = 0x0d; //F4
    public static final int EQUALS            = 0x0e; //F4
    public static final int LESS              = 0x0f; //F4
    public static final int AND               = 0x10; //F4
    public static final int OR                = 0x11; //F4
    public static final int NOT               = 0x12; //F4
    public static final int STRING_EQUALS     = 0x13; //F4
    public static final int STRING_LENGTH     = 0x14; //F4
    public static final int STRING_EXTRACT    = 0x15; //F4
    
    public static final int POP               = 0x17; //F4
    public static final int TO_INTEGER        = 0x18; //F4
    
    public static final int GET_VARIABLE      = 0x1c; //F4
    public static final int SET_VARIABLE      = 0x1d; //F4
    
    public static final int SET_TARGET_2      = 0x20; //F4
    public static final int STRING_ADD        = 0x21; //F4
    public static final int GET_PROPERTY      = 0x22; //F4
    public static final int SET_PROPERTY      = 0x23; //F4
    public static final int CLONE_SPRITE      = 0x24; //F4
    public static final int REMOVE_SPRITE     = 0x25; //F4
    public static final int TRACE             = 0x26; //F4
    public static final int START_DRAG        = 0x27; //F4
    public static final int END_DRAG          = 0x28; //F4
    public static final int STRING_LESS       = 0x29; //F4
    
    public static final int RANDOM_NUMBER     = 0x30; //F4
    public static final int MB_STRING_LENGTH  = 0x31; //F4
    public static final int CHAR_TO_ASCII     = 0x32; //F4
    public static final int ASCII_TO_CHAR     = 0x33; //F4
    public static final int GET_TIME          = 0x34; //F4
    public static final int MB_STRING_EXTRACT = 0x35; //F4
    public static final int MB_CHAR_TO_ASCII  = 0x36; //F4
    public static final int MB_ASCII_TO_CHAR  = 0x37; //F4
    
    public static final int DEL_VAR           = 0x3a; //F5 ---  
    public static final int DEL_THREAD_VARS   = 0x3b; //F5 ---  
    public static final int DEFINE_LOCAL_VAL  = 0x3c; //F5 ---  
    public static final int CALL_FUNCTION     = 0x3d; //F5 ---    
    public static final int RETURN            = 0x3e; //F5 ---      
    public static final int MODULO            = 0x3f; //F5 ---  
    public static final int NEW_OBJECT        = 0x40; //F5 ---
    public static final int DEFINE_LOCAL      = 0x41; //F5 ---
    public static final int INIT_ARRAY        = 0x42; //F5 ---
    public static final int INIT_OBJECT       = 0x43; //F5 ---
    public static final int TYPEOF            = 0x44; //F5 ---        
    public static final int GET_TARGET_PATH   = 0x45; //F5 ---
    public static final int ENUMERATE         = 0x46; //F5 ---
    public static final int TYPED_ADD         = 0x47; //F5 ---        
    public static final int TYPED_LESS_THAN   = 0x48; //F5 ---        
    public static final int TYPED_EQUALS      = 0x49; //F5 ---    
    public static final int CONVERT_TO_NUMBER = 0x4a; //F5 ---        
    public static final int CONVERT_TO_STRING = 0x4b; //F5 ---        
    public static final int DUPLICATE         = 0x4c; //F5 ---    
    public static final int SWAP              = 0x4d; //F5 ---   
    public static final int GET_MEMBER        = 0x4e; //F5 ---    
    public static final int SET_MEMBER        = 0x4f; //F5 ---      
    public static final int INCREMENT         = 0x50; //F5 ---        
    public static final int DECREMENT         = 0x51; //F5 ---        
    public static final int CALL_METHOD       = 0x52; //F5 ---    
    public static final int CALL_NEW_METHOD   = 0x53; //F5 ---    
	public static final int INSTANCE_OF       = 0x54; //MX <<<    
	public static final int ENUMERATE_OBJECT  = 0x55; //MX <<<    
    
    public static final int BIT_AND           = 0x60; //F5 ---        
    public static final int BIT_OR            = 0x61; //F5 ---        
    public static final int BIT_XOR           = 0x62; //F5 ---        
    public static final int SHIFT_LEFT        = 0x63; //F5 ---        
    public static final int SHIFT_RIGHT       = 0x64; //F5 ---        
    public static final int SHIFT_UNSIGNED    = 0x65; //F5 ---        
	public static final int STRICT_EQUALS     = 0x66; //MX <<<    
	public static final int GREATER           = 0x67; //MX <<<    
	public static final int STRING_GREATER    = 0x68; //MX <<<    
    
    public static final int GOTO_FRAME        = 0x81; //F3 ***
    
    public static final int GET_URL           = 0x83; //F3 ***
 
    public static final int REGISTER          = 0x87; //F5 ---    
    public static final int LOOKUP_TABLE      = 0x88; //F5 ---
    
    public static final int WAIT_FOR_FRAME    = 0x8a; //F3 ***
    public static final int SET_TARGET        = 0x8b; //F3 ***
    public static final int GOTO_LABEL        = 0x8c; //F3 ***
    public static final int WAIT_FOR_FRAME_2  = 0x8d; //F4
    
    public static final int WITH              = 0x94; //F5 ---

    public static final int PUSH              = 0x96; //F4
    
    public static final int JUMP              = 0x99; //F4
    public static final int GET_URL_2         = 0x9a; //F4
    public static final int DEFINE_FUNCTION   = 0x9b; //F5 ---
    
    public static final int IF                = 0x9d; //F4
    public static final int CALL              = 0x9e; //F4
    public static final int GOTO_FRAME_2      = 0x9f; //F4
    
    //--Property Constants
    public static final int PROP_X            = 0;
    public static final int PROP_Y            = 1;
    public static final int PROP_XSCALE       = 2;
    public static final int PROP_YSCALE       = 3;
    public static final int PROP_CURRENTFRAME = 4;
    public static final int PROP_TOTALFRAMES  = 5;
    public static final int PROP_ALPHA        = 6;
    public static final int PROP_VISIBLE      = 7;
    public static final int PROP_WIDTH        = 8;
    public static final int PROP_HEIGHT       = 9;
    public static final int PROP_ROTATION     = 10;
    public static final int PROP_TARGET       = 11;
    public static final int PROP_FRAMESLOADED = 12;
    public static final int PROP_NAME         = 13;
    public static final int PROP_DROPTARGET   = 14;
    public static final int PROP_URL          = 15;
    public static final int PROP_HIGHQUALITY  = 16;
    public static final int PROP_FOCUSRECT    = 17;
    public static final int PROP_SOUNDBUFTIME = 18;
    public static final int PROP_QUALITY      = 19; //flash 5 only
    public static final int PROP_XMOUSE       = 20; //flash 5 only
    public static final int PROP_YMOUSE       = 21; //flash 5 only
        
    
    //--TypeOf Strings (from the ActionScript typeof() operator)
    public static final String TYPEOF_NUMBER    = "number";
    public static final String TYPEOF_BOOLEAN   = "boolean";
    public static final String TYPEOF_STRING    = "string";
    public static final String TYPEOF_OBJECT    = "object";
    public static final String TYPEOF_MOVIECLIP = "movieclip";
    public static final String TYPEOF_NULL      = "null";
    public static final String TYPEOF_UNDEFINED = "undefined";
    public static final String TYPEOF_FUNCTION  = "function";
    
    //--Types for Flash 5 push action
    public static final int PUSHTYPE_STRING   = 0;
    public static final int PUSHTYPE_FLOAT    = 1;
    public static final int PUSHTYPE_NULL     = 2;
    public static final int PUSHTYPE_03       = 3; //unknown
    public static final int PUSHTYPE_REGISTER = 4;
    public static final int PUSHTYPE_BOOLEAN  = 5;
    public static final int PUSHTYPE_DOUBLE   = 6;
    public static final int PUSHTYPE_INTEGER  = 7;
    public static final int PUSHTYPE_LOOKUP   = 8;    
}
