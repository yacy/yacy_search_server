package pt.tumba.parser.swf;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

/**
 *  A Movie or Movie Clip frame
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class Frame {
	/**
	 *  Description of the Field
	 */
	protected int frameNumber;
	/**
	 *  Description of the Field
	 */
	protected String label;
	/**
	 *  Description of the Field
	 */
	protected List placements = new Vector();
	/**
	 *  Description of the Field
	 */
	protected boolean stop;
	/**
	 *  Description of the Field
	 */
	protected TimeLine timeline;
	/**
	 *  Description of the Field
	 */
	protected Actions actions;

	/**
	 *  Constructor for the Frame object
	 *
	 *@param  number    Description of the Parameter
	 *@param  timeline  Description of the Parameter
	 */
	protected Frame(int number, TimeLine timeline) {
		frameNumber = number;
		this.timeline = timeline;
	}

	/**
	 *  Get the frame actions
	 *
	 *@return    The actions value
	 */
	public Actions getActions() {
		return actions;
	}

	/**
	 *  Set the frame actions (or null them out)
	 *
	 *@param  actions  The new actions value
	 */
	public void setActions(Actions actions) {
		this.actions = actions;
	}

	/**
	 *  Reset the frame actions (if any) and return the new empty Actions object
	 *
	 *@param  flashVersion  Description of the Parameter
	 *@return               Description of the Return Value
	 */
	public Actions actions(int flashVersion) {
		actions = new Actions(0, flashVersion);
		return actions;
	}

	/**
	 *  Get the frame number
	 *
	 *@return    The frameNumber value
	 */
	public int getFrameNumber() {
		return frameNumber;
	}

	/**
	 *  Get the placements in this frame
	 *
	 *@return    The placements value
	 */
	public Placement[] getPlacements() {
		Placement[] p = new Placement[placements.size()];
		placements.toArray(p);
		return p;
	}

	/**
	 *  Get the frame label
	 *
	 *@return    null if the frame has no label
	 */
	public String getLabel() {
		return label;
	}

	/**
	 *  Set the frame label - set to null to clear any label
	 *
	 *@param  label  The new label value
	 */
	public void setLabel(String label) {
		this.label = label;
	}

	/**
	 *  Set the stop flag - if true then the movie will stop at this frame. This
	 *  can be set on the last frame to prevent the movie looping.
	 */
	public void stop() {
		this.stop = true;
	}

	/**
	 *  Place a symbol at the given coordinates at the next available depth.
	 *
	 *@param  symbol  Description of the Parameter
	 *@param  x       Description of the Parameter
	 *@param  y       Description of the Parameter
	 *@return         Description of the Return Value
	 */
	public Instance placeSymbol(Symbol symbol, int x, int y) {
		return placeSymbol(symbol, new Transform(x, y), null, -1, -1);
	}

	/**
	 *  Place a symbol at the next available depth with the given matrix
	 *  transform and color transform.
	 *
	 *@param  matrix  may be null to place the symbol at (0,0)
	 *@param  cxform  may be null if no color transform is required
	 *@param  symbol  Description of the Parameter
	 *@return         Description of the Return Value
	 */
	public Instance placeSymbol(
		Symbol symbol,
		Transform matrix,
		AlphaTransform cxform) {
		return placeSymbol(symbol, matrix, cxform, -1, -1);
	}

	/**
	 *  Place a symbol at the next available depth with the given properties.
	 *
	 *@param  matrix     may be null to place the symbol at (0,0)
	 *@param  cxform     may be null if no color transform is required
	 *@param  ratio      only for a MorphShape - the morph ratio from 0 to
	 *      65535, should be -1 for a non-MorphShape
	 *@param  clipDepth  the top depth that will be clipped by the symbol,
	 *      should be -1 if this is not a clipping symbol
	 *@param  symbol     Description of the Parameter
	 *@return            Description of the Return Value
	 */
	public Instance placeSymbol(
		Symbol symbol,
		Transform matrix2,
		AlphaTransform cxform,
		int ratio,
		int clipDepth) {
		Transform matrix = matrix2;
		int depth = timeline.getAvailableDepth();
		Instance inst = new Instance(symbol, depth);
		timeline.setAvailableDepth(depth + 1);

		if (matrix == null) {
			matrix = new Transform();
		}

		Placement placement =
			new Placement(
				inst,
				matrix,
				cxform,
				null,
				ratio,
				clipDepth,
				frameNumber,
				false,
				false,
				null);

		placements.add(placement);
		return inst;
	}

	/**
	 *  Replace the symbol at the given depth with the new symbol
	 *
	 *@param  matrix     may be null to place the symbol at (0,0)
	 *@param  cxform     may be null if no color transform is required
	 *@param  ratio      only for a MorphShape - the morph ratio from 0 to
	 *      65535, should be -1 for a non-MorphShape
	 *@param  clipDepth  the top depth that will be clipped by the symbol,
	 *      should be -1 if this is not a clipping symbol
	 *@param  symbol     Description of the Parameter
	 *@param  depth      Description of the Parameter
	 *@return            Description of the Return Value
	 */
	public Instance replaceSymbol(
		Symbol symbol,
		int depth,
		Transform matrix2,
		AlphaTransform cxform,
		int ratio,
		int clipDepth) {
		Transform matrix = matrix2;
		Instance inst = new Instance(symbol, depth);

		if (matrix == null) {
			matrix = new Transform();
		}

		Placement placement =
			new Placement(
				inst,
				matrix,
				cxform,
				null,
				ratio,
				clipDepth,
				frameNumber,
				false,
				true,
				null);

		placements.add(placement);
		return inst;
	}

	/**
	 *  Place a Movie Clip at the next available depth with the given
	 *  properties.
	 *
	 *@param  matrix       may be null to place the symbol at (0,0)
	 *@param  cxform       may be null if no color transform is required
	 *@param  name         the instance name of a MovieClip - should be null if
	 *      this is not a MovieClip
	 *@param  symbol       Description of the Parameter
	 *@param  clipActions  Description of the Parameter
	 *@return              Description of the Return Value
	 */
	public Instance placeMovieClip(
		Symbol symbol,
		Transform matrix2,
		AlphaTransform cxform,
		String name,
		Actions[] clipActions) {
		Transform matrix = matrix2;
		int depth = timeline.getAvailableDepth();
		Instance inst = new Instance(symbol, depth);
		timeline.setAvailableDepth(depth + 1);

		if (matrix == null) {
			matrix = new Transform();
		}

		Placement placement =
			new Placement(
				inst,
				matrix,
				cxform,
				name,
				-1,
				-1,
				frameNumber,
				false,
				false,
				clipActions);

		placements.add(placement);
		return inst;
	}

	/**
	 *  Replace the Symbol at the given depth with the new MovieClip
	 *
	 *@param  matrix       may be null to place the symbol at (0,0)
	 *@param  cxform       may be null if no color transform is required
	 *@param  name         the instance name of a MovieClip - should be null if
	 *      this is not a MovieClip
	 *@param  symbol       Description of the Parameter
	 *@param  depth        Description of the Parameter
	 *@param  clipActions  Description of the Parameter
	 *@return              Description of the Return Value
	 */
	public Instance replaceMovieClip(
		Symbol symbol,
		int depth,
		Transform matrix2,
		AlphaTransform cxform,
		String name,
		Actions[] clipActions) {
		Transform matrix = matrix2;
		Instance inst = new Instance(symbol, depth);

		if (matrix == null) {
			matrix = new Transform();
		}

		Placement placement =
			new Placement(
				inst,
				matrix,
				cxform,
				name,
				-1,
				-1,
				frameNumber,
				false,
				true,
				clipActions);

		placements.add(placement);
		return inst;
	}

	/**
	 *  Remove the symbol instance from the stage
	 *
	 *@param  instance  Description of the Parameter
	 */
	public void remove(Instance instance) {
		placements.add(new Placement(instance, frameNumber));
	}

	/**
	 *  Alter the symbol instance by moving it to the new coordinates. Only one
	 *  alteration may be made to an Instance in any given frame.
	 *
	 *@param  instance  Description of the Parameter
	 *@param  x         Description of the Parameter
	 *@param  y         Description of the Parameter
	 */
	public void alter(Instance instance, int x, int y) {
		alter(instance, new Transform(x, y), null, -1);
	}

	/**
	 *  Alter the symbol instance by applying the given transform and/or color
	 *  transform. Only one alteration may be made to an Instance in any given
	 *  frame.
	 *
	 *@param  matrix    may be null if no positional change is to be made.
	 *@param  cxform    may be null if no color change is required.
	 *@param  instance  Description of the Parameter
	 */
	public void alter(
		Instance instance,
		Transform matrix,
		AlphaTransform cxform) {
		alter(instance, matrix, cxform, -1);
	}

	/**
	 *  Alter the symbol instance by applying the given properties. Only one
	 *  alteration may be made to an Instance in any given frame.
	 *
	 *@param  matrix    may be null if no positional change is to be made.
	 *@param  cxform    may be null if no color change is required.
	 *@param  ratio     only for a MorphShape - the morph ratio from 0 to 65535,
	 *      should be -1 for a non-MorphShape
	 *@param  instance  Description of the Parameter
	 */
	public void alter(
		Instance instance,
		Transform matrix,
		AlphaTransform cxform,
		int ratio) {
		Placement placement =
			new Placement(
				instance,
				matrix,
				cxform,
				null,
				ratio,
				-1,
				frameNumber,
				true,
				false,
				null);

		placements.add(placement);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  movie             Description of the Parameter
	 *@param  timelineWriter    Description of the Parameter
	 *@param  definitionWriter  Description of the Parameter
	 *@exception  IOException   Description of the Exception
	 */
	protected void flushDefinitions(
		Movie movie,
		SWFTagTypes timelineWriter,
		SWFTagTypes definitionWriter)
		throws IOException {
		for (Iterator enumerator = placements.iterator();
			enumerator.hasNext();
			) {
			Placement placement = (Placement) enumerator.next();

			placement.flushDefinitions(movie, timelineWriter, definitionWriter);
		}
	}

	/**
	 *  Write the frame
	 *
	 *@param  movie              Description of the Parameter
	 *@param  movieTagWriter     Description of the Parameter
	 *@param  timelineTagWriter  Description of the Parameter
	 *@exception  IOException    Description of the Exception
	 */
	protected void write(
		Movie movie,
		SWFTagTypes movieTagWriter,
		SWFTagTypes timelineTagWriter)
		throws IOException {
		if (actions != null) {
			SWFActions acts = timelineTagWriter.tagDoAction();
			acts.start(0);
			acts.blob(actions.bytes);
			acts.done();
		}

		if (stop) {
			SWFActions actions = timelineTagWriter.tagDoAction();

			actions.start(0);
			actions.stop();
			actions.end();
			actions.done();
		}

		for (Iterator enumumerator = placements.iterator();
			enumumerator.hasNext();
			) {
			Placement placement = (Placement) enumumerator.next();

			placement.write(movie, movieTagWriter, timelineTagWriter);
		}

		if (label != null) {
			timelineTagWriter.tagFrameLabel(label);
		}
		timelineTagWriter.tagShowFrame();
	}
}
