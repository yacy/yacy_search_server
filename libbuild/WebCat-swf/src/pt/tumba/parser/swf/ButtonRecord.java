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
public class ButtonRecord {
    /**
     *  Description of the Field
     */
    public final static int BUTTON_HITTEST = 0x08;
    /**
     *  Description of the Field
     */
    public final static int BUTTON_DOWN = 0x04;
    /**
     *  Description of the Field
     */
    public final static int BUTTON_OVER = 0x02;
    /**
     *  Description of the Field
     */
    public final static int BUTTON_UP = 0x01;

    /**
     *  Description of the Field
     */
    protected int flags;
    /**
     *  Description of the Field
     */
    protected int id;
    /**
     *  Description of the Field
     */
    protected int layer;
    /**
     *  Description of the Field
     */
    protected Matrix matrix;


    /**
     *  Gets the charId attribute of the ButtonRecord object
     *
     *@return    The charId value
     */
    public int getCharId() {
        return id;
    }


    /**
     *  Gets the layer attribute of the ButtonRecord object
     *
     *@return    The layer value
     */
    public int getLayer() {
        return layer;
    }


    /**
     *  Gets the matrix attribute of the ButtonRecord object
     *
     *@return    The matrix value
     */
    public Matrix getMatrix() {
        return matrix;
    }


    /**
     *  Gets the flags attribute of the ButtonRecord object
     *
     *@return    The flags value
     */
    public int getFlags() {
        return flags;
    }


    /**
     *  Gets the hitTest attribute of the ButtonRecord object
     *
     *@return    The hitTest value
     */
    public boolean isHitTest() {
        return ((flags & BUTTON_HITTEST) != 0);
    }


    /**
     *  Gets the down attribute of the ButtonRecord object
     *
     *@return    The down value
     */
    public boolean isDown() {
        return ((flags & BUTTON_DOWN) != 0);
    }


    /**
     *  Gets the over attribute of the ButtonRecord object
     *
     *@return    The over value
     */
    public boolean isOver() {
        return ((flags & BUTTON_OVER) != 0);
    }


    /**
     *  Gets the up attribute of the ButtonRecord object
     *
     *@return    The up value
     */
    public boolean isUp() {
        return ((flags & BUTTON_UP) != 0);
    }


    /**
     *  Sets the charId attribute of the ButtonRecord object
     *
     *@param  id  The new charId value
     */
    public void setCharId(int id) {
        this.id = id;
    }


    /**
     *  Sets the layer attribute of the ButtonRecord object
     *
     *@param  layer  The new layer value
     */
    public void setLayer(int layer) {
        this.layer = layer;
    }


    /**
     *  Sets the matrix attribute of the ButtonRecord object
     *
     *@param  matrix  The new matrix value
     */
    public void setMatrix(Matrix matrix) {
        this.matrix = matrix;
    }


    /**
     *  Sets the flags attribute of the ButtonRecord object
     *
     *@param  flags  The new flags value
     */
    public void setFlags(int flags) {
        this.flags = flags;
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
            records.addElement(new ButtonRecord(in, firstByte));
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
            ButtonRecord rec = (ButtonRecord) enumerator.next();
            rec.write(out);
        }

        out.writeUI8(0);
    }


    /**
     *  Constructor for the ButtonRecord object
     *
     *@param  id      Description of the Parameter
     *@param  layer   Description of the Parameter
     *@param  matrix  Description of the Parameter
     *@param  flags   Description of the Parameter
     */
    public ButtonRecord(int id, int layer, Matrix matrix, int flags) {
        this.id = id;
        this.layer = layer;
        this.matrix = matrix;
        this.flags = flags;
    }


    /**
     *  Constructor for the ButtonRecord object
     *
     *@param  in               Description of the Parameter
     *@param  firstByte        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected ButtonRecord(InStream in, int firstByte) throws IOException {
        flags = firstByte;
        id = in.readUI16();
        layer = in.readUI16();
        matrix = new Matrix(in);
    }


    /**
     *  Description of the Method
     *
     *@param  out              Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void write(OutStream out) throws IOException {
        out.writeUI8(flags);
        out.writeUI16(id);
        out.writeUI16(layer);
        matrix.write(out);
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public String toString() {
        return "layer=" + layer + " id=" + id +
                " flags=" + Integer.toBinaryString(flags) + " " + matrix;
    }
}
