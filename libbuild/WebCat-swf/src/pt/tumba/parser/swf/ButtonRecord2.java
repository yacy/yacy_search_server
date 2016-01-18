package pt.tumba.parser.swf;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

/**
 *  Description of the Class
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class ButtonRecord2 extends ButtonRecord {
    /**
     *  Description of the Field
     */
    protected AlphaTransform transform;


    /**
     *  Gets the transform attribute of the ButtonRecord2 object
     *
     *@return    The transform value
     */
    public AlphaTransform getTransform() {
        return transform;
    }


    /**
     *  Sets the transform attribute of the ButtonRecord2 object
     *
     *@param  transform  The new transform value
     */
    public void setTransform(AlphaTransform transform) {
        this.transform = transform;
    }


    /**
     *  Read a button record array
     *
     *@param  in               Description of the Parameter
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    public static List read(InStream in) throws IOException {
        Vector records = new Vector();

        int firstByte = 0;
        while ((firstByte = in.readUI8()) != 0) {
            records.addElement(new ButtonRecord2(in, firstByte));
        }

        return records;
    }


    /**
     *  Write a button record array
     *
     *@param  out              Description of the Parameter
     *@param  records          Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public static void write(OutStream out, List records) throws IOException {
        for (Iterator enumerator = records.iterator(); enumerator.hasNext(); ) {
            ButtonRecord2 rec = (ButtonRecord2) enumerator.next();
            rec.write(out);
        }

        out.writeUI8(0);
    }


    /**
     *  Constructor for the ButtonRecord2 object
     *
     *@param  id         Description of the Parameter
     *@param  layer      Description of the Parameter
     *@param  matrix     Description of the Parameter
     *@param  transform  Description of the Parameter
     *@param  flags      Description of the Parameter
     */
    public ButtonRecord2(int id,
            int layer,
            Matrix matrix,
            AlphaTransform transform,
            int flags) {
        super(id, layer, matrix, flags);
        this.transform = transform;
    }


    /**
     *  Constructor for the ButtonRecord2 object
     *
     *@param  in               Description of the Parameter
     *@param  firstByte        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected ButtonRecord2(InStream in, int firstByte) throws IOException {
        super(in, firstByte);
        transform = new AlphaTransform(in);
    }


    /**
     *  Description of the Method
     *
     *@param  out              Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void write(OutStream out) throws IOException {
        super.write(out);
        transform.write(out);
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public String toString() {
        return super.toString() + " " + transform;
    }
}
