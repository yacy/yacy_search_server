package pt.tumba.parser.swf;

import java.io.IOException;

/**
 *  Interface for passing Action Codes Lifecycle is - 1. start(..) is called
 *  with any condition flags (e.g. event codes) for the action array 2. action
 *  methods are called 3. end() is called to terminate array 4. 1..3 is repeated
 *  for any subsequent condition blocks 5. done() is called to terminate all
 *  action passing
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public interface SWFActions {
    /**
     *  Start of actions
     *
     *@param  flags            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void start(int flags) throws IOException;


    /**
     *  End of all action blocks
     *
     *@exception  IOException  Description of the Exception
     */
    public void done() throws IOException;


    /**
     *  End of actions
     *
     *@exception  IOException  Description of the Exception
     */
    public void end() throws IOException;


    /**
     *  Pass through a blob of actions
     *
     *@param  blob             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void blob(byte[] blob) throws IOException;


    /**
     *  Unrecognized action code
     *
     *@param  data             may be null
     *@param  code             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void unknown(int code, byte[] data) throws IOException;


    /**
     *  Target label for a jump - this method call immediately precedes the
     *  target action.
     *
     *@param  label            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void jumpLabel(String label) throws IOException;


    /**
     *  Comment Text - useful for debugging purposes
     *
     *@param  comment          Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void comment(String comment) throws IOException;


    //--Flash 3 Actions:
    /**
     *  Description of the Method
     *
     *@param  frameNumber      Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void gotoFrame(int frameNumber) throws IOException;


    /**
     *  Description of the Method
     *
     *@param  label            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void gotoFrame(String label) throws IOException;


    /**
     *  Gets the uRL attribute of the SWFActions object
     *
     *@param  url              Description of the Parameter
     *@param  target           Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void getURL(String url, String target) throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void nextFrame() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void prevFrame() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void play() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void stop() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void toggleQuality() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void stopSounds() throws IOException;


    /**
     *  Description of the Method
     *
     *@param  frameNumber      Description of the Parameter
     *@param  jumpLabel        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void waitForFrame(int frameNumber, String jumpLabel) throws IOException;


    /**
     *  Sets the target attribute of the SWFActions object
     *
     *@param  target           The new target value
     *@exception  IOException  Description of the Exception
     */
    public void setTarget(String target) throws IOException;


    //--Flash 4 Actions:
    /**
     *  Description of the Method
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void push(String value) throws IOException;


    /**
     *  Description of the Method
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void push(float value) throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void pop() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void add() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void substract() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void multiply() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void divide() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void equals() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void lessThan() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void and() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void or() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void not() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void stringEquals() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void stringLength() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void concat() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void substring() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void stringLessThan() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void stringLengthMB() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void substringMB() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void toInteger() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void charToAscii() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void asciiToChar() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void charMBToAscii() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void asciiToCharMB() throws IOException;


    /**
     *  Description of the Method
     *
     *@param  jumpLabel        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void jump(String jumpLabel) throws IOException;


    /**
     *  Description of the Method
     *
     *@param  jumpLabel        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void ifJump(String jumpLabel) throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void call() throws IOException;


    /**
     *  Gets the variable attribute of the SWFActions object
     *
     *@exception  IOException  Description of the Exception
     */
    public void getVariable() throws IOException;


    /**
     *  Sets the variable attribute of the SWFActions object
     *
     *@exception  IOException  Description of the Exception
     */
    public void setVariable() throws IOException;


    //----------------------------------------------------------
    /**
     *  Description of the Field
     */
    public final static int GET_URL_SEND_VARS_NONE = 0;
    //don't send variables
    /**
     *  Description of the Field
     */
    public final static int GET_URL_SEND_VARS_GET = 1;
    //send vars using GET
    /**
     *  Description of the Field
     */
    public final static int GET_URL_SEND_VARS_POST = 2;
    //send vars using POST

    /**
     *  Description of the Field
     */
    public final static int GET_URL_MODE_LOAD_MOVIE_INTO_LEVEL = 0;
    /**
     *  Description of the Field
     */
    public final static int GET_URL_MODE_LOAD_MOVIE_INTO_SPRITE = 1;
    /**
     *  Description of the Field
     */
    public final static int GET_URL_MODE_LOAD_VARS_INTO_LEVEL = 3;
    /**
     *  Description of the Field
     */
    public final static int GET_URL_MODE_LOAD_VARS_INTO_SPRITE = 4;


    /**
     *  Gets the uRL attribute of the SWFActions object
     *
     *@param  sendVars         Description of the Parameter
     *@param  loadMode         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void getURL(int sendVars, int loadMode) throws IOException;


    //----------------------------------------------------------

    /**
     *  Description of the Method
     *
     *@param  play             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void gotoFrame(boolean play) throws IOException;


    /**
     *  Sets the target attribute of the SWFActions object
     *
     *@exception  IOException  Description of the Exception
     */
    public void setTarget() throws IOException;


    /**
     *  Gets the property attribute of the SWFActions object
     *
     *@exception  IOException  Description of the Exception
     */
    public void getProperty() throws IOException;


    /**
     *  Sets the property attribute of the SWFActions object
     *
     *@exception  IOException  Description of the Exception
     */
    public void setProperty() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void cloneSprite() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void removeSprite() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void startDrag() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void endDrag() throws IOException;


    /**
     *  Description of the Method
     *
     *@param  jumpLabel        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void waitForFrame(String jumpLabel) throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void trace() throws IOException;


    /**
     *  Gets the time attribute of the SWFActions object
     *
     *@exception  IOException  Description of the Exception
     */
    public void getTime() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void randomNumber() throws IOException;


    //--Flash 5 Actions
    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void callFunction() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void callMethod() throws IOException;


    /**
     *  Description of the Method
     *
     *@param  values           Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void lookupTable(String[] values) throws IOException;


    //startFunction(..) is terminated by matching endBlock()
    /**
     *  Description of the Method
     *
     *@param  name             Description of the Parameter
     *@param  paramNames       Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void startFunction(String name, String[] paramNames) throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void endBlock() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void defineLocalValue() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void defineLocal() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void deleteProperty() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void deleteThreadVars() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void enumerate() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void typedEquals() throws IOException;


    /**
     *  Gets the member attribute of the SWFActions object
     *
     *@exception  IOException  Description of the Exception
     */
    public void getMember() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void initArray() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void initObject() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void newMethod() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void newObject() throws IOException;


    /**
     *  Sets the member attribute of the SWFActions object
     *
     *@exception  IOException  Description of the Exception
     */
    public void setMember() throws IOException;


    /**
     *  Gets the targetPath attribute of the SWFActions object
     *
     *@exception  IOException  Description of the Exception
     */
    public void getTargetPath() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void startWith() throws IOException;


    //terminated by matching endBlock()

    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void convertToNumber() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void convertToString() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void typeOf() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void typedAdd() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void typedLessThan() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void modulo() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void bitAnd() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void bitOr() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void bitXor() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void shiftLeft() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void shiftRight() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void shiftRightUnsigned() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void decrement() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void increment() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void duplicate() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void returnValue() throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void swap() throws IOException;


    /**
     *  Description of the Method
     *
     *@param  registerNumber   Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void storeInRegister(int registerNumber) throws IOException;


    /**
     *  Description of the Method
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void push(double value) throws IOException;


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void pushNull() throws IOException;


    /**
     *  Description of the Method
     *
     *@param  registerNumber   Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void pushRegister(int registerNumber) throws IOException;


    /**
     *  Description of the Method
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void push(boolean value) throws IOException;


    /**
     *  Description of the Method
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void push(int value) throws IOException;


    /**
     *  Description of the Method
     *
     *@param  dictionaryIndex  Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void lookup(int dictionaryIndex) throws IOException;
}
