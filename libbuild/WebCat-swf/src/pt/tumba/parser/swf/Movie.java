package pt.tumba.parser.swf;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Vector;

/**
 *  A Flash Movie
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class Movie implements TimeLine {
    /**
     *  Description of the Field
     */
    protected int width;
    /**
     *  Description of the Field
     */
    protected int height;
    /**
     *  Description of the Field
     */
    protected int frameRate;
    /**
     *  Description of the Field
     */
    protected Color backColor;
    /**
     *  Description of the Field
     */
    protected int version;
    /**
     *  Description of the Field
     */
    protected boolean isProtected;

    /**
     *  Description of the Field
     */
    protected Map importLibraries;
    /**
     *  Description of the Field
     */
    protected List exportedSymbols;

    /**
     *  Description of the Field
     */
    protected SortedMap frames = new TreeMap();
    /**
     *  Description of the Field
     */
    protected int frameCount = 0;

    //--Table of characters defined so far in the movie - while writing out
    /**
     *  Description of the Field
     */
    protected Map definedSymbols = new HashMap();

    /**
     *  Description of the Field
     */
    protected int depth = 1;
    //the next available depth
    /**
     *  Description of the Field
     */
    protected int maxId = 1;


    //the next available symbol id

    /**
     *  Create a movie with the default values - (550x400), 12 frames/sec, white
     *  backcolor, Flash version 5.
     */
    public Movie() {
        width = 550;
        height = 400;
        frameRate = 12;
        version = 5;
    }


    /**
     *  Create a movie with the given properties
     *
     *@param  width      Description of the Parameter
     *@param  height     Description of the Parameter
     *@param  frameRate  Description of the Parameter
     *@param  version    Description of the Parameter
     *@param  backColor  Description of the Parameter
     */
    public Movie(int width, int height, int frameRate, int version, Color backColor) {
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
        this.version = version;
        this.backColor = backColor;
    }


    /**
     *  Gets the width attribute of the Movie object
     *
     *@return    The width value
     */
    public int getWidth() {
        return width;
    }


    /**
     *  Gets the height attribute of the Movie object
     *
     *@return    The height value
     */
    public int getHeight() {
        return height;
    }


    /**
     *  Gets the frameRate attribute of the Movie object
     *
     *@return    The frameRate value
     */
    public int getFrameRate() {
        return frameRate;
    }


    /**
     *  Gets the version attribute of the Movie object
     *
     *@return    The version value
     */
    public int getVersion() {
        return version;
    }


    /**
     *  Gets the backColor attribute of the Movie object
     *
     *@return    The backColor value
     */
    public Color getBackColor() {
        return backColor;
    }


    /**
     *  Sets the width attribute of the Movie object
     *
     *@param  width  The new width value
     */
    public void setWidth(int width) {
        this.width = width;
    }


    /**
     *  Sets the height attribute of the Movie object
     *
     *@param  height  The new height value
     */
    public void setHeight(int height) {
        this.height = height;
    }


    /**
     *  Sets the frameRate attribute of the Movie object
     *
     *@param  rate  The new frameRate value
     */
    public void setFrameRate(int rate) {
        this.frameRate = rate;
    }


    /**
     *  Sets the version attribute of the Movie object
     *
     *@param  version  The new version value
     */
    public void setVersion(int version) {
        this.version = version;
    }


    /**
     *  Sets the backColor attribute of the Movie object
     *
     *@param  color  The new backColor value
     */
    public void setBackColor(Color color) {
        this.backColor = color;
    }


    /**
     *  Return the protection flag. If true then the movie cannot be imported
     *  into the Flash Author. The existence of tools such as JavaSWF makes this
     *  kind of protection almost worthless.
     *
     *@return    The protected value
     */
    public boolean isProtected() {
        return isProtected;
    }


    /**
     *  Description of the Method
     *
     *@param  isProtected  Description of the Parameter
     */
    public void protect(boolean isProtected) {
        this.isProtected = isProtected;
    }


    /**
     *  Get the current number of frames in the timeline.
     *
     *@return    The frameCount value
     */
    public int getFrameCount() {
        return frameCount;
    }


    /**
     *  Get the Frame object for the given frame number - or create one if none
     *  exists. If the frame number is larger than the current frame count then
     *  the frame count is increased.
     *
     *@param  frameNumber  must be 1 or larger
     *@return              The frame value
     */
    public Frame getFrame(int frameNumber) {
        if (frameNumber < 1) {
            return null;
        }

        Integer num = new Integer(frameNumber);
        Frame frame = (Frame) frames.get(num);

        if (frame == null) {
            frame = new Frame(frameNumber, this);
            frames.put(num, frame);
            if (frameNumber > frameCount) {
                frameCount = frameNumber;
            }
        }

        return frame;
    }


    /**
     *  Append a frame to the end of the timeline
     *
     *@return    Description of the Return Value
     */
    public Frame appendFrame() {
        frameCount++;
        Frame frame = new Frame(frameCount, this);
        frames.put(new Integer(frameCount), frame);
        return frame;
    }


    /**
     *  Get the next available depth in the timeline
     *
     *@return    The availableDepth value
     */
    public int getAvailableDepth() {
        return depth;
    }


    /**
     *  Set the next available depth in the timeline
     *
     *@param  depth  must be >= 1
     */
    public void setAvailableDepth(int depth) {
        if (depth < 1) {
            return;
        }
        this.depth = depth;
    }


    /**
     *  Import symbols from another movie (Flash 5 only)
     *
     *@param  libraryName  Description of the Parameter
     *@param  symbolNames  Description of the Parameter
     *@return              Symbols representing the imports
     */
    public ImportedSymbol[] importSymbols(String libraryName, String[] symbolNames) {
        if (importLibraries == null) {
            importLibraries = new HashMap();
        }

        ArrayList imports = (ArrayList) importLibraries.get(libraryName);
        if (imports == null) {
            imports = new ArrayList();
            importLibraries.put(libraryName, imports);
        }

        ImportedSymbol[] symbols = new ImportedSymbol[symbolNames.length];

        for (int i = 0; i < symbolNames.length; i++) {
            ImportedSymbol imp = new ImportedSymbol(0, symbolNames[i], libraryName);
            symbols[i] = imp;
            imports.add(imp);
        }

        return symbols;
    }


    /**
     *  Clear all the defined library imports
     */
    public void clearImports() {
        if (importLibraries != null) {
            importLibraries.clear();
        }
    }


    /**
     *  Access the imported symbols.
     *
     *@return    an empty array if there are no imports
     */
    public ImportedSymbol[] getImportedSymbols() {
        if (importLibraries == null) {
            return new ImportedSymbol[0];
        }

        Vector imports = new Vector();

        for (Iterator iter = importLibraries.values().iterator(); iter.hasNext(); ) {
            List list = (List) iter.next();

            for (Iterator i2 = list.iterator(); i2.hasNext(); ) {
                imports.add(i2.next());
            }
        }

        ImportedSymbol[] imps = new ImportedSymbol[imports.size()];
        imports.copyInto(imps);

        return imps;
    }


    /**
     *  Export a number of symbols with the given names so that other movies can
     *  import and use them. Flash version 5 only.
     *
     *@param  exportNames  Description of the Parameter
     *@param  symbols      Description of the Parameter
     */
    public void exportSymbols(String[] exportNames, Symbol[] symbols) {
        if (exportedSymbols == null) {
            exportedSymbols = new Vector();
        }

        for (int i = 0; i < exportNames.length && i < symbols.length; i++) {
            exportedSymbols.add(new ExportedSymbol(symbols[i], exportNames[i]));
        }
    }


    /**
     *  Get the symbols exported from the movie
     *
     *@return    an empty array if there are no exports
     */
    public ExportedSymbol[] getExportedSymbols() {
        if (exportedSymbols == null) {
            return new ExportedSymbol[0];
        }
        ExportedSymbol[] exports = new ExportedSymbol[exportedSymbols.size()];
        exportedSymbols.toArray(exports);
        return exports;
    }


    /**
     *  Clear all the symbol exports
     */
    public void clearExports() {
        if (exportedSymbols != null) {
            exportedSymbols.clear();
        }
    }


    /**
     *  Write the movie in SWF format.
     *
     *@param  tagwriter        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void write(SWFTagTypes tagwriter) throws IOException {
        //--Reset state
        definedSymbols.clear();
        maxId = 1;

        tagwriter.header(version,
                -1,
        //force length calculation
                width * SWFConstants.TWIPS,
                height * SWFConstants.TWIPS,
                frameRate,
                -1);
        //force frame calculation

        //default backColor is white
        if (backColor == null) {
            backColor = new Color(255, 255, 255);
        }

        tagwriter.tagSetBackgroundColor(backColor);

        if (isProtected) {
            tagwriter.tagProtect(null);
        }

        //--Process Imports
        if (importLibraries != null && !importLibraries.isEmpty()) {
            for (Iterator keys = importLibraries.keySet().iterator(); keys.hasNext(); ) {
                String libName = (String) keys.next();
                List imports = (List) importLibraries.get(libName);

                String[] names = new String[imports.size()];
                int[] ids = new int[imports.size()];

                int i = 0;
                for (Iterator it = imports.iterator(); it.hasNext(); ) {
                    ImportedSymbol imp = (ImportedSymbol) it.next();

                    names[i] = imp.getName();
                    ids[i] = imp.define(this, tagwriter, tagwriter);

                    i++;
                }

                tagwriter.tagImport(libName, names, ids);
            }
        }

        //--Process Exports
        if (exportedSymbols != null && !exportedSymbols.isEmpty()) {
            String[] names = new String[exportedSymbols.size()];
            int[] ids = new int[exportedSymbols.size()];

            int i = 0;
            for (Iterator it = exportedSymbols.iterator(); it.hasNext(); ) {
                ExportedSymbol exp = (ExportedSymbol) it.next();

                names[i] = exp.getExportName();
                ids[i] = exp.getSymbol().define(this, tagwriter, tagwriter);

                i++;
            }

            tagwriter.tagExport(names, ids);
        }

        int lastFrame = 0;
        for (Iterator iter = frames.values().iterator(); iter.hasNext(); ) {
            Frame frame = (Frame) iter.next();

            int number = frame.getFrameNumber();

            //write any intermediate empty frames
            while (number > lastFrame + 1) {
                tagwriter.tagShowFrame();
                lastFrame++;
            }

            frame.write(this, tagwriter, tagwriter);

            lastFrame = number;
        }

        //end of time line
        tagwriter.tagEnd();
    }


    /**
     *  Write the movie in SWF format to the given file.
     *
     *@param  filename         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void write(String filename) throws IOException {
        SWFWriter swfwriter = new SWFWriter(filename);
        TagWriter tagwriter = new TagWriter(swfwriter);
        write(tagwriter);
    }


    /**
     *  Write the movie in SWF format to the given output stream.
     *
     *@param  out              Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void write(OutputStream out) throws IOException {
        SWFWriter swfwriter = new SWFWriter(out);
        TagWriter tagwriter = new TagWriter(swfwriter);
        write(tagwriter);
    }
}
