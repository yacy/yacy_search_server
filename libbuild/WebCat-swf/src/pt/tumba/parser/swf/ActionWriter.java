package pt.tumba.parser.swf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import java.util.Vector;

/**
 *  A writer that implements the SWFActions interface and writes action bytes to
 *  an OutStream
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class ActionWriter implements SWFActions, SWFActionCodes {
    /**
     *  Description of the Field
     */
    protected TagWriter tagWriter;
    /**
     *  Description of the Field
     */
    protected OutStream out;
    /**
     *  Description of the Field
     */
    protected ByteArrayOutputStream bout;
    /**
     *  Description of the Field
     */
    protected int count;
    /**
     *  Description of the Field
     */
    protected int flashVersion;

    /**
     *  Description of the Field
     */
    protected List pushValues;

    /**
     *  Description of the Field
     */
    protected Hashtable labels;
    /**
     *  Description of the Field
     */
    protected List jumps;
    /**
     *  Description of the Field
     */
    protected List skips;

    //--for fixing up functions and WITH blocks..
    /**
     *  Description of the Field
     */
    protected List blocks;
    /**
     *  Description of the Field
     */
    protected Stack blockStack;


    /**
     *  Constructor for the ActionWriter object
     *
     *@param  tagWriter     Description of the Parameter
     *@param  flashVersion  Description of the Parameter
     */
    public ActionWriter(TagWriter tagWriter, int flashVersion) {
        this.flashVersion = flashVersion;
        this.tagWriter = tagWriter;
    }


    /**
     *@param  code             Description of the Parameter
     *@return                  the code count
     *@exception  IOException  Description of the Exception
     */
    protected int writeCode(int code) throws IOException {
        if (pushValues.size() > 0) {
            flushPushValues();
        }
        out.writeUI8(code);
        count++;
        return count;
    }


    /**
     *  SWFActions interface
     *
     *@param  conditions       Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void start(int conditions) throws IOException {
        //ignore conditions

        count = 0;
        bout = new ByteArrayOutputStream();
        out = new OutStream(bout);
        pushValues = new Vector();
        labels = null;
        jumps = null;
        skips = null;
        blocks = null;
        blockStack = null;
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void end() throws IOException {
        writeCode(0);
        out.flush();
        byte[] bytes = bout.toByteArray();

        //--Fix up jumps and skips
        if (labels != null) {
            if (jumps != null) {
                fixupJumps(bytes);
            }
            if (skips != null) {
                fixupSkips(bytes);
            }
        }

        if (blocks != null) {
            fixupBlocks(bytes);
        }

        writeBytes(bytes);
    }


    /**
     *  Pass through a blob of actions
     *
     *@param  blob             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void blob(byte[] blob) throws IOException {
        writeBytes(blob);
    }


    /**
     *  Description of the Method
     *
     *@param  bytes            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void writeBytes(byte[] bytes) throws IOException {
        tagWriter.getOutStream().write(bytes);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void done() throws IOException {
        tagWriter.completeTag();
    }


    /**
     *  Description of the Method
     *
     *@param  bytes  Description of the Parameter
     */
    protected void fixupBlocks(byte[] bytes2) {
		byte[] bytes = bytes2;
        for (Iterator enumerator = blocks.iterator(); enumerator.hasNext(); ) {
            int[] info = (int[]) enumerator.next();

            int codeSize = info[1];
            int offset = info[0];
            byte[] sizeBytes = OutStream.sintTo2Bytes(codeSize);

            bytes[offset] = sizeBytes[0];
            bytes[offset + 1] = sizeBytes[1];
        }
    }


    /**
     *  Description of the Method
     *
     *@param  bytes  Description of the Parameter
     */
    protected void fixupJumps(byte[] bytes2) {
        byte[] bytes = bytes2;
        for (Iterator enumumerator = jumps.iterator(); enumumerator.hasNext(); ) {
            Object[] obja = (Object[]) enumumerator.next();
            String label = (String) obja[0];
            int target = ((Integer) obja[1]).intValue();

            int[] labelInfo = (int[]) labels.get(label);

            if (labelInfo == null) {
                System.out.println("Missing label '" + label + "' in action code");
                continue;
            }

            int absolute = labelInfo[0];
            //offset of the label
            int relative = absolute - (target + 2);
            //relative jump

            byte[] val = OutStream.sintTo2Bytes(relative);
            bytes[target] = val[0];
            bytes[target + 1] = val[1];
        }
    }


    /**
     *  Description of the Method
     *
     *@param  bytes  Description of the Parameter
     */
    protected void fixupSkips(byte[] bytes2) {
		byte[] bytes = bytes2;
        for (Iterator enumerator = skips.iterator(); enumerator.hasNext(); ) {
            Object[] obja = (Object[]) enumerator.next();
            String label = (String) obja[0];

            int[] skipInfo = (int[]) obja[1];
            int skipIndex = skipInfo[0];
            int skipLoc = skipInfo[1];

            int[] labelInfo = (int[]) labels.get(label);

            if (labelInfo == null) {
                System.out.println("Missing label '" + label + "' in action code");
                continue;
            }

            int labelIndex = labelInfo[1];
            //index of the labelled action
            int skip = labelIndex - skipIndex - 1;

            byte val = OutStream.uintToByte(skip);
            bytes[skipLoc] = val;
        }
    }


    /**
     *  SWFActions interface
     *
     *@param  comment          Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void comment(String comment) throws IOException {
        //ignore comments
    }


    /**
     *  SWFActions interface
     *
     *@param  code             Description of the Parameter
     *@param  data             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void unknown(int code, byte[] data) throws IOException {
        writeCode(code);

        int length = (data != null) ? data.length : 0;

        if (code >= 0x80 || length > 0) {
            out.writeUI16(length);
        }

        if (length > 0) {
            out.write(data);
        }
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void initArray() throws IOException {
        writeCode(INIT_ARRAY);
    }


    /**
     *  SWFActions interface
     *
     *@param  label            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void jumpLabel(String label) throws IOException {
        if (pushValues.size() > 0) {
            flushPushValues();
        }

        int offset = (int) out.getBytesWritten();

        if (labels == null) {
            labels = new Hashtable();
        }
        labels.put(label, new int[]{offset, count + 1});
    }


    /**
     *  SWFActions interface
     *
     *@param  frameNumber      Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void gotoFrame(int frameNumber) throws IOException {
        writeCode(GOTO_FRAME);
        out.writeUI16(2);
        out.writeUI16(frameNumber);
    }


    /**
     *  SWFActions interface
     *
     *@param  label            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void gotoFrame(String label) throws IOException {
        writeCode(GOTO_LABEL);
        out.writeUI16(OutStream.getStringLength(label));
        out.writeString(label);
    }


    /**
     *  SWFActions interface
     *
     *@param  url              Description of the Parameter
     *@param  target           Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void getURL(String url, String target) throws IOException {
        writeCode(GET_URL);
        out.writeUI16(OutStream.getStringLength(url) + OutStream.getStringLength(target));
        out.writeString(url);
        out.writeString(target);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void nextFrame() throws IOException {
        writeCode(NEXT_FRAME);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void prevFrame() throws IOException {
        writeCode(PREVIOUS_FRAME);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void play() throws IOException {
        writeCode(PLAY);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void stop() throws IOException {
        writeCode(STOP);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void toggleQuality() throws IOException {
        writeCode(TOGGLE_QUALITY);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void stopSounds() throws IOException {
        writeCode(STOP_SOUNDS);
    }


    /**
     *  SWFActions interface
     *
     *@param  target           The new target value
     *@exception  IOException  Description of the Exception
     */
    public void setTarget(String target) throws IOException {
        writeCode(SET_TARGET);
        out.writeUI16(OutStream.getStringLength(target));
        out.writeString(target);
    }


    /**
     *  Description of the Method
     *
     *@param  label            Description of the Parameter
     *@param  code             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void writeJump(String label, int code) throws IOException {
        writeCode(code);
        out.writeUI16(2);

        int here = (int) out.getBytesWritten();
        out.writeUI16(0);
        //will be fixed up later

        //--save jump info for later fix-up logic
        if (jumps == null) {
            jumps = new Vector();
        }
        jumps.add(new Object[]{label, new Integer(here)});
    }


    /**
     *  SWFActions interface
     *
     *@param  jumpLabel        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void jump(String jumpLabel) throws IOException {
        writeJump(jumpLabel, JUMP);
    }


    /**
     *  SWFActions interface
     *
     *@param  jumpLabel        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void ifJump(String jumpLabel) throws IOException {
        writeJump(jumpLabel, IF);
    }


    /**
     *  SWFActions interface
     *
     *@param  frameNumber      Description of the Parameter
     *@param  jumpLabel        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void waitForFrame(int frameNumber, String jumpLabel) throws IOException {
        writeCode(WAIT_FOR_FRAME);
        out.writeUI16(3);
        out.writeUI16(frameNumber);

        int here = (int) out.getBytesWritten();
        out.writeUI8(0);
        //will be fixed up later

        //--save skip info for later fix-up logic
        if (skips == null) {
            skips = new Vector();
        }
        skips.add(new Object[]{jumpLabel, new int[]{count, here}});
    }


    /**
     *  SWFActions interface
     *
     *@param  jumpLabel        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void waitForFrame(String jumpLabel) throws IOException {
        writeCode(WAIT_FOR_FRAME_2);
        out.writeUI16(1);

        int here = (int) out.getBytesWritten();
        out.writeUI8(0);
        //will be fixed up later

        //--save skip info for later fix-up logic
        if (skips == null) {
            skips = new Vector();
        }
        skips.add(new Object[]{jumpLabel, new int[]{count, here}});
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void pop() throws IOException {
        writeCode(POP);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void add() throws IOException {
        writeCode(ADD);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void substract() throws IOException {
        writeCode(SUBTRACT);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void multiply() throws IOException {
        writeCode(MULTIPLY);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void divide() throws IOException {
        writeCode(DIVIDE);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void equals() throws IOException {
        writeCode(EQUALS);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void lessThan() throws IOException {
        writeCode(LESS);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void and() throws IOException {
        writeCode(AND);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void or() throws IOException {
        writeCode(OR);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void not() throws IOException {
        writeCode(NOT);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void stringEquals() throws IOException {
        writeCode(STRING_EQUALS);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void stringLength() throws IOException {
        writeCode(STRING_LENGTH);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void concat() throws IOException {
        writeCode(STRING_ADD);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void substring() throws IOException {
        writeCode(STRING_EXTRACT);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void stringLessThan() throws IOException {
        writeCode(STRING_LESS);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void stringLengthMB() throws IOException {
        writeCode(MB_STRING_LENGTH);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void substringMB() throws IOException {
        writeCode(MB_STRING_EXTRACT);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void toInteger() throws IOException {
        writeCode(TO_INTEGER);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void charToAscii() throws IOException {
        writeCode(CHAR_TO_ASCII);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void asciiToChar() throws IOException {
        writeCode(ASCII_TO_CHAR);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void charMBToAscii() throws IOException {
        writeCode(MB_CHAR_TO_ASCII);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void asciiToCharMB() throws IOException {
        writeCode(MB_ASCII_TO_CHAR);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void call() throws IOException {
        writeCode(CALL);
        out.writeUI16(0);
        //SWF File Format anomaly
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void getVariable() throws IOException {
        writeCode(GET_VARIABLE);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void setVariable() throws IOException {
        writeCode(SET_VARIABLE);
    }


    /**
     *  SWFActions interface
     *
     *@param  sendVars         Description of the Parameter
     *@param  loadMode         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void getURL(int sendVars, int loadMode) throws IOException {
        writeCode(GET_URL_2);
        out.writeUI16(1);

        int flags = 0;

        //String sendVars_ = null;
        switch (sendVars) {
            case GET_URL_SEND_VARS_GET:
                flags = 1;
                break;
            case GET_URL_SEND_VARS_POST:
                flags = 2;
                break;
            case GET_URL_SEND_VARS_NONE:
            default:
                break;
        }

        //String mode = null;
        switch (loadMode) {
            case GET_URL_MODE_LOAD_MOVIE_INTO_LEVEL:
                break;
            case GET_URL_MODE_LOAD_MOVIE_INTO_SPRITE:
                flags |= 0x40;
                break;
            case GET_URL_MODE_LOAD_VARS_INTO_LEVEL:
                flags |= 0x80;
                break;
            case GET_URL_MODE_LOAD_VARS_INTO_SPRITE:
                flags |= 0xC0;
                break;
            default:
                break;
        }

        out.writeUI8(flags);
    }


    /**
     *  SWFActions interface
     *
     *@param  play             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void gotoFrame(boolean play) throws IOException {
        writeCode(GOTO_FRAME_2);
        out.writeUI16(1);
        out.writeUI8(play ? 1 : 0);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void setTarget() throws IOException {
        writeCode(SET_TARGET_2);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void getProperty() throws IOException {
        writeCode(GET_PROPERTY);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void setProperty() throws IOException {
        writeCode(SET_PROPERTY);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void cloneSprite() throws IOException {
        writeCode(CLONE_SPRITE);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void removeSprite() throws IOException {
        writeCode(REMOVE_SPRITE);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void startDrag() throws IOException {
        writeCode(START_DRAG);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void endDrag() throws IOException {
        writeCode(END_DRAG);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void trace() throws IOException {
        writeCode(TRACE);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void getTime() throws IOException {
        writeCode(GET_TIME);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void randomNumber() throws IOException {
        writeCode(RANDOM_NUMBER);
    }


    /**
     *  SWFActions interface
     *
     *@param  values           Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void lookupTable(String[] values) throws IOException {
        writeCode(LOOKUP_TABLE);

        ByteArrayOutputStream baout = new ByteArrayOutputStream();
        OutStream bout = new OutStream(baout);

        bout.writeUI16(values.length);

        for (int i = 0; i < values.length; i++) {
            bout.writeString(values[i]);
        }

        bout.flush();
        byte[] data = baout.toByteArray();
        out.writeUI16(data.length);
        out.write(data);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void callFunction() throws IOException {
        writeCode(CALL_FUNCTION);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void callMethod() throws IOException {
        writeCode(CALL_METHOD);
    }


    /**
     *  SWFActions interface
     *
     *@param  name             Description of the Parameter
     *@param  paramNames       Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void startFunction(String name, String[] paramNames) throws IOException {
        if (blockStack == null) {
            blockStack = new Stack();
        }

        writeCode(DEFINE_FUNCTION);

        ByteArrayOutputStream baout = new ByteArrayOutputStream();
        OutStream bout = new OutStream(baout);

        bout.writeString(name);
        bout.writeUI16(paramNames.length);

        for (int i = 0; i < paramNames.length; i++) {
            bout.writeString(paramNames[i]);
        }

        bout.writeUI16(0);
        //code size - will be fixed up later

        bout.flush();
        byte[] data = baout.toByteArray();
        out.writeUI16(data.length);
        out.write(data);

        blockStack.push(new int[]{(int) out.getBytesWritten(), 0});
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void endBlock() throws IOException {
        if (blockStack == null || blockStack.isEmpty()) {
            return;
        }
        //nothing to do
        int[] blockInfo = (int[]) blockStack.pop();

        if (blocks == null) {
            blocks = new Vector();
        }

        int offset = blockInfo[0];
        int codeSize = ((int) out.getBytesWritten()) - offset;

        //--store this info for later fix-up
        blockInfo[0] = offset - 2;
        blockInfo[1] = codeSize;
        blocks.add(blockInfo);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void defineLocalValue() throws IOException {
        writeCode(DEFINE_LOCAL_VAL);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void defineLocal() throws IOException {
        writeCode(DEFINE_LOCAL);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void deleteProperty() throws IOException {
        writeCode(DEL_VAR);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void deleteThreadVars() throws IOException {
        writeCode(DEL_THREAD_VARS);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void enumerate() throws IOException {
        writeCode(ENUMERATE);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void typedEquals() throws IOException {
        writeCode(TYPED_EQUALS);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void getMember() throws IOException {
        writeCode(GET_MEMBER);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void initObject() throws IOException {
        writeCode(INIT_OBJECT);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void newMethod() throws IOException {
        writeCode(CALL_NEW_METHOD);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void newObject() throws IOException {
        writeCode(NEW_OBJECT);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void setMember() throws IOException {
        writeCode(SET_MEMBER);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void getTargetPath() throws IOException {
        writeCode(GET_TARGET_PATH);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void startWith() throws IOException {
        writeCode(WITH);
        out.writeUI16(2);
        out.writeUI16(0);
        //codeSize - will be fixed up later

        //--push the block start info
        if (blockStack == null) {
            blockStack = new Stack();
        }
        blockStack.push(new int[]{(int) out.getBytesWritten(), 0});
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void duplicate() throws IOException {
        writeCode(DUPLICATE);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void returnValue() throws IOException {
        writeCode(RETURN);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void swap() throws IOException {
        writeCode(SWAP);
    }


    /**
     *  SWFActions interface
     *
     *@param  registerNumber   Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void storeInRegister(int registerNumber) throws IOException {
        writeCode(REGISTER);
        out.writeUI16(1);
        out.writeUI8(registerNumber);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void convertToNumber() throws IOException {
        writeCode(CONVERT_TO_NUMBER);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void convertToString() throws IOException {
        writeCode(CONVERT_TO_STRING);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void typeOf() throws IOException {
        writeCode(TYPEOF);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void typedAdd() throws IOException {
        writeCode(TYPED_ADD);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void typedLessThan() throws IOException {
        writeCode(TYPED_LESS_THAN);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void modulo() throws IOException {
        writeCode(MODULO);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void bitAnd() throws IOException {
        writeCode(BIT_AND);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void bitOr() throws IOException {
        writeCode(BIT_OR);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void bitXor() throws IOException {
        writeCode(BIT_XOR);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void shiftLeft() throws IOException {
        writeCode(SHIFT_LEFT);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void shiftRight() throws IOException {
        writeCode(SHIFT_RIGHT);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void shiftRightUnsigned() throws IOException {
        writeCode(SHIFT_UNSIGNED);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void decrement() throws IOException {
        writeCode(DECREMENT);
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void increment() throws IOException {
        writeCode(INCREMENT);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    protected void flushPushValues() throws IOException {
        out.writeUI8(PUSH);
        count++;

        ByteArrayOutputStream baout = new ByteArrayOutputStream();
        OutStream bout = new OutStream(baout);

        for (Iterator enumerator = pushValues.iterator(); enumerator.hasNext(); ) {
            Object value = enumerator.next();

            if (value instanceof String) {
                bout.writeUI8(PUSHTYPE_STRING);
                bout.writeString(value.toString());
            } else if (value instanceof Boolean) {
                bout.writeUI8(PUSHTYPE_BOOLEAN);
                bout.writeUI8(((Boolean) value).booleanValue() ? 1 : 0);
            } else if (value instanceof Integer) {
                bout.writeUI8(PUSHTYPE_INTEGER);
                bout.writeSI32(((Integer) value).intValue());
            } else if (value instanceof Short) {
                bout.writeUI8(PUSHTYPE_LOOKUP);
                bout.writeUI8(((Short) value).intValue());
            } else if (value instanceof Byte) {
                bout.writeUI8(SWFActionCodes.PUSHTYPE_REGISTER);
                bout.writeUI8(((Byte) value).intValue());
            } else if (value instanceof Float) {
                bout.writeUI8(PUSHTYPE_FLOAT);
                bout.writeFloat(((Float) value).floatValue());
            } else if (value instanceof Double) {
                bout.writeUI8(PUSHTYPE_DOUBLE);
                bout.writeDouble(((Double) value).doubleValue());
            } else {
                bout.writeUI8(PUSHTYPE_NULL);
            }
        }

        pushValues.clear();

        bout.flush();
        byte[] data = baout.toByteArray();
        out.writeUI16(data.length);
        out.write(data);
    }


    /**
     *  SWFActions interface
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void push(String value) throws IOException {
        pushValues.add(value);
        if (flashVersion < 5) {
            flushPushValues();
        }
    }


    /**
     *  SWFActions interface
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void push(float value) throws IOException {
        pushValues.add(new Float(value));
        if (flashVersion < 5) {
            flushPushValues();
        }
    }


    /**
     *  SWFActions interface
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void push(double value) throws IOException {
        pushValues.add(new Double(value));
        if (flashVersion < 5) {
            flushPushValues();
        }
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void pushNull() throws IOException {
        pushValues.add(new Object());
        if (flashVersion < 5) {
            flushPushValues();
        }
    }


    /**
     *  SWFActions interface
     *
     *@param  registerNumber   Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void pushRegister(int registerNumber) throws IOException {
        pushValues.add(new Byte((byte) registerNumber));
        if (flashVersion < 5) {
            flushPushValues();
        }
    }


    /**
     *  SWFActions interface
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void push(boolean value) throws IOException {
        pushValues.add(Boolean.valueOf(value));
        if (flashVersion < 5) {
            flushPushValues();
        }
    }


    /**
     *  SWFActions interface
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void push(int value) throws IOException {
        pushValues.add(new Integer(value));
        if (flashVersion < 5) {
            flushPushValues();
        }
    }


    /**
     *  SWFActions interface
     *
     *@param  dictionaryIndex  Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void lookup(int dictionaryIndex) throws IOException {
        pushValues.add(new Short((short) dictionaryIndex));
        if (flashVersion < 5) {
            flushPushValues();
        }
    }
}
