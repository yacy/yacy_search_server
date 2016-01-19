package pt.tumba.parser.swf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Vector;

/**
 *  A set of actions
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class Actions extends ActionWriter {
    /**
     *  Description of the Field
     */
    protected int conditions;
    /**
     *  Description of the Field
     */
    protected byte[] bytes;


    /**
     *  Constructor for the Actions object
     *
     *@param  conditions    Description of the Parameter
     *@param  flashVersion  Description of the Parameter
     */
    public Actions(int conditions, int flashVersion) {
        super(null, flashVersion);

        this.conditions = conditions;
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
     *  Constructor for the Actions object
     *
     *@param  flashVersion  Description of the Parameter
     */
    public Actions(int flashVersion) {
        this(0, flashVersion);
    }


    /**
     *  Parse the action contents and write them to the SWFActions interface
     *
     *@param  swfactions       Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void write(SWFActions swfactions) throws IOException {
        ActionParser parser = new ActionParser(swfactions);
        swfactions.start(conditions);
        parser.parse(bytes);
        swfactions.done();
    }


    /**
     *  The condition flags depend on context - frame, button or clip actions
     *
     *@return    The conditions value
     */
    public int getConditions() {
        return conditions;
    }


    /**
     *  Sets the conditions attribute of the Actions object
     *
     *@param  conds  The new conditions value
     */
    public void setConditions(int conds) {
        this.conditions = conds;
    }


    /**
     *  SWFActions interface
     *
     *@param  conditions       Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void start(int conditions) throws IOException {
        //do nothing
    }


    /**
     *  Description of the Method
     *
     *@param  bytes            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void writeBytes(byte[] bytes) throws IOException {
        this.bytes = bytes;
    }


    /**
     *  SWFActions interface
     *
     *@exception  IOException  Description of the Exception
     */
    public void done() throws IOException {
        //do nothing
    }
}
