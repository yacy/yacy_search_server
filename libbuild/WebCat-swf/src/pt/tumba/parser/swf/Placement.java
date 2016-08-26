package pt.tumba.parser.swf;

import java.io.IOException;

/**
 *  A Placement holds the transformation and other values relating to the
 *  "placement" of a Symbol Instance within a particular frame.
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class Placement {
    /**
     *  Description of the Field
     */
    protected boolean isAlteration;
    /**
     *  Description of the Field
     */
    protected boolean isReplacement;
    /**
     *  Description of the Field
     */
    protected int frameNumber;
    /**
     *  Description of the Field
     */
    protected Instance instance;
    /**
     *  Description of the Field
     */
    protected Transform matrix;
    /**
     *  Description of the Field
     */
    protected AlphaTransform cxform;
    /**
     *  Description of the Field
     */
    protected String name;
    /**
     *  Description of the Field
     */
    protected int ratio = -1;
    /**
     *  Description of the Field
     */
    protected int clipDepth = -1;
    /**
     *  Description of the Field
     */
    protected boolean isRemove = false;
    /**
     *  Description of the Field
     */
    protected Actions[] clipActions;


    /**
     *  Return true if the placement is replacing the symbol at a given depth
     *  with a new symbol
     *
     *@return    The replacement value
     */
    public boolean isReplacement() {
        return isReplacement;
    }


    /**
     *  Return true if this placement is an alteration to an Instance that was
     *  placed in a previous frame.
     *
     *@return    The alteration value
     */
    public boolean isAlteration() {
        return isAlteration;
    }


    /**
     *  Get the number of the frame within which this placement takes place
     *
     *@return    The frameNumber value
     */
    public int getFrameNumber() {
        return frameNumber;
    }


    /**
     *  Get the Symbol Instance represented by this Placement
     *
     *@return    The instance value
     */
    public Instance getInstance() {
        return instance;
    }


    /**
     *  The transform may be null
     *
     *@return    The transform value
     */
    public Transform getTransform() {
        return matrix;
    }


    /**
     *  The color transform may be null
     *
     *@return    The colorTransform value
     */
    public AlphaTransform getColorTransform() {
        return cxform;
    }


    /**
     *  The name only relates to MovieClip instances and may be null. The name
     *  is only present on the first Placement of an Instance.
     *
     *@return    The name value
     */
    public String getName() {
        return name;
    }


    /**
     *  The ratio only relates to Morph Shapes and will be -1 otherwise. The
     *  ratio is from zero to 65535 and denotes the degree of the morph from the
     *  initial shape to the final shape.
     *
     *@return    The ratio value
     */
    public int getRatio() {
        return ratio;
    }


    /**
     *  The clip depth defines the range of depths which will be clipped by this
     *  symbol. All symbols placed at depths from depth+1 to clipDepth
     *  (inclusive) will be clipped. If this symbol is not a clipping symbol
     *  then the clip depth will be -1. The clip depth is only present on the
     *  first Placement of an Instance.
     *
     *@return    The clipDepth value
     */
    public int getClipDepth() {
        return clipDepth;
    }


    /**
     *  If true then this Placement denotes the removal of the Instance from the
     *  stage.
     *
     *@return    The remove value
     */
    public boolean isRemove() {
        return isRemove;
    }


    /**
     *  Get the actions for a movie clip
     *
     *@return    The clipActions value
     */
    public Actions[] getClipActions() {
        return clipActions;
    }


    /**
     *  Set the actions for a movie clip
     *
     *@param  clipActions  The new clipActions value
     */
    public void setClipActions(Actions[] clipActions) {
        this.clipActions = clipActions;
    }


    /**
     *  Constructor for the Placement object
     *
     *@param  instance     Description of the Parameter
     *@param  frameNumber  Description of the Parameter
     */
    protected Placement(Instance instance, int frameNumber) {
        this.instance = instance;
        this.frameNumber = frameNumber;
        this.isRemove = true;
    }


    /**
     *  Constructor for the Placement object
     *
     *@param  instance     Description of the Parameter
     *@param  matrix       Description of the Parameter
     *@param  cxform       Description of the Parameter
     *@param  name         Description of the Parameter
     *@param  ratio        Description of the Parameter
     *@param  clipDepth    Description of the Parameter
     *@param  frameNumber  Description of the Parameter
     *@param  alteration   Description of the Parameter
     *@param  replacement  Description of the Parameter
     *@param  clipActions  Description of the Parameter
     */
    protected Placement(Instance instance, Transform matrix, AlphaTransform cxform,
            String name, int ratio, int clipDepth, int frameNumber,
            boolean alteration, boolean replacement, Actions[] clipActions) {
        this.instance = instance;
        this.frameNumber = frameNumber;
        this.matrix = matrix;
        this.cxform = cxform;
        this.name = name;
        this.ratio = ratio;
        this.clipDepth = clipDepth;
        this.isRemove = false;
        this.isAlteration = alteration;
        this.isReplacement = replacement;
        this.clipActions = clipActions;
    }


    /**
     *  Description of the Method
     *
     *@param  movie             Description of the Parameter
     *@param  timelineWriter    Description of the Parameter
     *@param  definitionWriter  Description of the Parameter
     *@exception  IOException   Description of the Exception
     */
    protected void flushDefinitions(Movie movie,
            SWFTagTypes timelineWriter,
            SWFTagTypes definitionWriter)
             throws IOException {
        if ((!isAlteration) && !isRemove) {
            //--Make sure that the symbol is defined
            Symbol symbol = instance.getSymbol();

            symbol.define(movie, timelineWriter, definitionWriter);
        }
    }


    /**
     *  Description of the Method
     *
     *@param  movie              Description of the Parameter
     *@param  movieTagWriter     Description of the Parameter
     *@param  timelineTagWriter  Description of the Parameter
     *@exception  IOException    Description of the Exception
     */
    protected void write(Movie movie,
            SWFTagTypes movieTagWriter,
            SWFTagTypes timelineTagWriter)
             throws IOException {
        int depth = instance.getDepth();
        if (depth < 0) {
            return;
        }

        if (isRemove) {
            //--Remove the instance
            timelineTagWriter.tagRemoveObject2(depth);
            return;
        }

        //--Check whether the Instance has been placed
        if (!isAlteration) {
            //--Make sure that the symbol is defined
            Symbol symbol = instance.getSymbol();
            int id = symbol.define(movie, timelineTagWriter, movieTagWriter);

            int flags = 0;

            if (clipActions != null && clipActions.length > 0) {
                for (int i = 0; i < clipActions.length; i++) {
                    flags |= clipActions[i].getConditions();
                }
            }

            SWFActions acts = timelineTagWriter.tagPlaceObject2(
                    isReplacement, clipDepth, depth, id,
                    matrix, cxform, ratio, name, flags);

            if (clipActions != null && clipActions.length > 0) {
                for (int i = 0; i < clipActions.length; i++) {
                    acts.start(clipActions[i].getConditions());
                    acts.blob(clipActions[i].bytes);
                }

                acts.done();
            }
        } else {
            //--Instance is placed - this is just a move
            timelineTagWriter.tagPlaceObject2(true, clipDepth, depth, -1,
                    matrix, cxform, ratio, null, 0);
        }
    }
}
