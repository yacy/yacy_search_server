package pt.tumba.parser.swf;

import java.io.IOException;
import java.io.PrintWriter;

/**
 *  A writer that implements the SWFActions interface and writes actions to a
 *  text format
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class ActionTextWriter implements SWFActions, SWFActionCodes {
    /**
     *  Description of the Field
     */
    protected PrintWriter printer;
    /**
     *  Description of the Field
     */
    protected String indent = "";


    /**
     *  Constructor for the ActionTextWriter object
     *
     *@param  printer  Description of the Parameter
     */
    public ActionTextWriter(PrintWriter printer) {
        this.printer = printer;
    }


    /**
     *  Description of the Method
     *
     *@param  mnemonic  Description of the Parameter
     *@param  args      Description of the Parameter
     */
    protected void print(String mnemonic, String[] args) {
        printer.print(indent + "    ");
        writePaddedString(mnemonic + " ", 15);

        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    printer.print(", ");
                }
                printer.print(args[i]);
            }
        }

        printer.println();
    }


    /**
     *  Description of the Method
     *
     *@param  s       Description of the Parameter
     *@param  length  Description of the Parameter
     */
    protected void writePaddedString(String s, int length) {
        int pad = length - s.length();

        printer.print(s);
        while (pad > 0) {
            printer.print(" ");
            pad--;
        }
    }


    /**
     *  Description of the Method
     *
     *@param  conditions       Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void start(int conditions) throws IOException {
        print("conditions", new String[]{Integer.toBinaryString(conditions)});
        printer.flush();
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void end() throws IOException {
        print("end", null);
        printer.println();
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void done() throws IOException {
        printer.flush();
    }


    /**
     *  Description of the Method
     *
     *@param  blob             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void blob(byte[] blob) throws IOException {
        print("(blob)", null);
        printer.println();
    }


    /**
     *  Description of the Method
     *
     *@param  code             Description of the Parameter
     *@param  data             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void unknown(int code, byte[] data) throws IOException {
        print("unknown code =", new String[]{Integer.toString(code)});
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void initArray() throws IOException {
        print("initArray", null);
    }


    /**
     *  Description of the Method
     *
     *@param  label            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void jumpLabel(String label) throws IOException {
        printer.println(indent + label + ":");
    }


    /**
     *  Description of the Method
     *
     *@param  frameNumber      Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void gotoFrame(int frameNumber) throws IOException {
        print("gotoFrame", new String[]{Integer.toString(frameNumber)});
    }


    /**
     *  Description of the Method
     *
     *@param  label            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void gotoFrame(String label) throws IOException {
        print("gotoFrame", new String[]{"\"" + label + "\""});
    }


    /**
     *  Gets the uRL attribute of the ActionTextWriter object
     *
     *@param  url              Description of the Parameter
     *@param  target           Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void getURL(String url, String target) throws IOException {
        print("getURL", new String[]{"\"" + url + "\"", "\"" + target + "\""});
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void nextFrame() throws IOException {
        print("nextFrame", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void prevFrame() throws IOException {
        print("previousFrame", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void play() throws IOException {
        print("play", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void stop() throws IOException {
        print("stop", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void toggleQuality() throws IOException {
        print("toggleQuality", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void stopSounds() throws IOException {
        print("stopSounds", null);
    }


    /**
     *  Sets the target attribute of the ActionTextWriter object
     *
     *@param  target           The new target value
     *@exception  IOException  Description of the Exception
     */
    public void setTarget(String target) throws IOException {
        print("setTarget", new String[]{"\"" + target + "\""});
    }


    /**
     *  Description of the Method
     *
     *@param  jumpLabel        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void jump(String jumpLabel) throws IOException {
        print("jump", new String[]{"\"" + jumpLabel + "\""});
    }


    /**
     *  Description of the Method
     *
     *@param  jumpLabel        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void ifJump(String jumpLabel) throws IOException {
        print("ifJump", new String[]{"\"" + jumpLabel + "\""});
    }


    /**
     *  Description of the Method
     *
     *@param  frameNumber      Description of the Parameter
     *@param  jumpLabel        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void waitForFrame(int frameNumber, String jumpLabel) throws IOException {
        print("waitForFrame", new String[]{Integer.toString(frameNumber),
                "\"" + jumpLabel + "\""});
    }


    /**
     *  Description of the Method
     *
     *@param  jumpLabel        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void waitForFrame(String jumpLabel) throws IOException {
        print("waitForFrame", new String[]{"\"" + jumpLabel + "\""});
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void pop() throws IOException {
        print("pop", null);
    }


    /**
     *  Description of the Method
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void push(String value) throws IOException {
        print("push", new String[]{"\"" + value + "\""});
    }


    /**
     *  Description of the Method
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void push(float value) throws IOException {
        print("push", new String[]{"float " + value});
    }


    /**
     *  Description of the Method
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void push(double value) throws IOException {
        print("push", new String[]{"double " + value});
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void pushNull() throws IOException {
        print("push", new String[]{"null"});
    }


    /**
     *  Description of the Method
     *
     *@param  registerNumber   Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void pushRegister(int registerNumber) throws IOException {
        print("push", new String[]{"register( " + registerNumber + " )"});
    }


    /**
     *  Description of the Method
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void push(boolean value) throws IOException {
        print("push", new String[]{value ? "true" : "false"});
    }


    /**
     *  Description of the Method
     *
     *@param  value            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void push(int value) throws IOException {
        print("push", new String[]{"" + value});
    }


    /**
     *  Description of the Method
     *
     *@param  dictionaryIndex  Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void lookup(int dictionaryIndex) throws IOException {
        print("push", new String[]{"lookup( " + dictionaryIndex + " )"});
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void add() throws IOException {
        print("add", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void substract() throws IOException {
        print("substract", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void multiply() throws IOException {
        print("multiply", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void divide() throws IOException {
        print("divide", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void equals() throws IOException {
        print("equals", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void lessThan() throws IOException {
        print("lessThan", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void and() throws IOException {
        print("and", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void or() throws IOException {
        print("or", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void not() throws IOException {
        print("not", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void stringEquals() throws IOException {
        print("stringEquals", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void stringLength() throws IOException {
        print("stringLength", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void concat() throws IOException {
        print("concat", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void substring() throws IOException {
        print("substring", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void stringLessThan() throws IOException {
        print("stringLessThan", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void stringLengthMB() throws IOException {
        print("stringLengthMB", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void substringMB() throws IOException {
        print("substringMB", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void toInteger() throws IOException {
        print("toInteger", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void charToAscii() throws IOException {
        print("charToAscii", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void asciiToChar() throws IOException {
        print("asciiToChar", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void charMBToAscii() throws IOException {
        print("charMBToAscii", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void asciiToCharMB() throws IOException {
        print("asciiToCharMB", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void call() throws IOException {
        print("call", null);
    }


    /**
     *  Gets the variable attribute of the ActionTextWriter object
     *
     *@exception  IOException  Description of the Exception
     */
    public void getVariable() throws IOException {
        print("getVariable", null);
    }


    /**
     *  Sets the variable attribute of the ActionTextWriter object
     *
     *@exception  IOException  Description of the Exception
     */
    public void setVariable() throws IOException {
        print("setVariable", null);
    }


    /**
     *  Gets the uRL attribute of the ActionTextWriter object
     *
     *@param  sendVars         Description of the Parameter
     *@param  loadMode         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void getURL(int sendVars, int loadMode) throws IOException {
        String sendVars_ = null;
        switch (sendVars) {
            case GET_URL_SEND_VARS_GET:
                sendVars_ = "send vars via GET";
                break;
            case GET_URL_SEND_VARS_POST:
                sendVars_ = "send vars via POST";
                break;
            case GET_URL_SEND_VARS_NONE:
            default:
                sendVars_ = "no send";
                break;
        }

        String mode = null;
        switch (loadMode) {
            case GET_URL_MODE_LOAD_MOVIE_INTO_LEVEL:
                mode = "load movie into level";
                break;
            case GET_URL_MODE_LOAD_MOVIE_INTO_SPRITE:
                mode = "load movie into sprite";
                break;
            case GET_URL_MODE_LOAD_VARS_INTO_LEVEL:
                mode = "load vars into level";
                break;
            case GET_URL_MODE_LOAD_VARS_INTO_SPRITE:
                mode = "load vars into sprite";
                break;
            default:
                mode = "???";
                break;
        }

        print("getURL", new String[]{sendVars_, mode});
    }


    /**
     *  Description of the Method
     *
     *@param  play             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void gotoFrame(boolean play) throws IOException {
        print("gotoFrame", new String[]{play ? "and play" : "and stop"});
    }


    /**
     *  Sets the target attribute of the ActionTextWriter object
     *
     *@exception  IOException  Description of the Exception
     */
    public void setTarget() throws IOException {
        print("setTarget", null);
    }


    /**
     *  Gets the property attribute of the ActionTextWriter object
     *
     *@exception  IOException  Description of the Exception
     */
    public void getProperty() throws IOException {
        print("getProperty", null);
    }


    /**
     *  Sets the property attribute of the ActionTextWriter object
     *
     *@exception  IOException  Description of the Exception
     */
    public void setProperty() throws IOException {
        print("setProperty", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void cloneSprite() throws IOException {
        print("cloneSprite", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void removeSprite() throws IOException {
        print("removeSprite", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void startDrag() throws IOException {
        print("startDrag", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void endDrag() throws IOException {
        print("endDrag", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void trace() throws IOException {
        print("trace", null);
    }


    /**
     *  Gets the time attribute of the ActionTextWriter object
     *
     *@exception  IOException  Description of the Exception
     */
    public void getTime() throws IOException {
        print("getTime", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void randomNumber() throws IOException {
        print("randomNumber", null);
    }


    /**
     *  Description of the Method
     *
     *@param  values           Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void lookupTable(String[] values) throws IOException {
        print("lookupTable", null);

        for (int i = 0; i < values.length; i++) {
            printer.print(indent + "        ");
            writePaddedString(Integer.toString(i) + ":", 5);
            printer.println("\"" + values[i] + "\"");
        }
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void callFunction() throws IOException {
        print("callFunction", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void callMethod() throws IOException {
        print("callMethod", null);
    }


    /**
     *  Description of the Method
     *
     *@param  name             Description of the Parameter
     *@param  paramNames       Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void startFunction(String name, String[] paramNames) throws IOException {
        String args = name + "(";

        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                if (i > 0) {
                    args += ",";
                }
                args += " " + paramNames[i];
            }

            if (paramNames.length > 0) {
                args += " ";
            }
        }

        args += ")";

        printer.println();
        print("defineFunction", new String[]{args});
        print("{", null);

        indent += "    ";
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void endBlock() throws IOException {
        if (indent.length() <= 4) {
            indent = "";
        } else if (indent.length() >= 4) {
            indent = indent.substring(4);
        }

        print("}", null);
        printer.println();
    }


    /**
     *  Description of the Method
     *
     *@param  comment          Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void comment(String comment) throws IOException {
        printer.println(indent + "    // " + comment);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void defineLocalValue() throws IOException {
        print("defineLocalValue", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void defineLocal() throws IOException {
        print("defineLocal", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void deleteProperty() throws IOException {
        print("deleteProperty", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void deleteThreadVars() throws IOException {
        print("deleteThreadVars", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void enumerate() throws IOException {
        print("enumerate", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void typedEquals() throws IOException {
        print("typedEquals", null);
    }


    /**
     *  Gets the member attribute of the ActionTextWriter object
     *
     *@exception  IOException  Description of the Exception
     */
    public void getMember() throws IOException {
        print("getMember", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void initObject() throws IOException {
        print("initObject", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void newMethod() throws IOException {
        print("newMethod", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void newObject() throws IOException {
        print("newObject", null);
    }


    /**
     *  Sets the member attribute of the ActionTextWriter object
     *
     *@exception  IOException  Description of the Exception
     */
    public void setMember() throws IOException {
        print("setMember", null);
    }


    /**
     *  Gets the targetPath attribute of the ActionTextWriter object
     *
     *@exception  IOException  Description of the Exception
     */
    public void getTargetPath() throws IOException {
        print("getTargetPath", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void startWith() throws IOException {
        printer.println();
        print("with", null);
        print("{", null);

        indent += "    ";
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void duplicate() throws IOException {
        print("duplicate", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void returnValue() throws IOException {
        print("return", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void swap() throws IOException {
        print("swap", null);
    }


    /**
     *  Description of the Method
     *
     *@param  registerNumber   Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void storeInRegister(int registerNumber) throws IOException {
        print("register", new String[]{Integer.toString(registerNumber)});
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void convertToNumber() throws IOException {
        print("convertToNumber", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void convertToString() throws IOException {
        print("convertToString", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void typeOf() throws IOException {
        print("typeOf", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void typedAdd() throws IOException {
        print("typedAdd", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void typedLessThan() throws IOException {
        print("typedLessThan", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void modulo() throws IOException {
        print("modulo", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void bitAnd() throws IOException {
        print("bitAnd", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void bitOr() throws IOException {
        print("bitOr", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void bitXor() throws IOException {
        print("bitXor", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void shiftLeft() throws IOException {
        print("shiftLeft", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void shiftRight() throws IOException {
        print("shiftRight", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void shiftRightUnsigned() throws IOException {
        print("shiftRightUnsigned", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void decrement() throws IOException {
        print("decrement", null);
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void increment() throws IOException {
        print("increment", null);
    }
}

