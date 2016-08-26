package pt.tumba.parser.swf;


/**
 *  A Movie or Sprite (Movie Clip) time line (collection of frames)
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public interface TimeLine {
    /**
     *  Get the current number of frames in the timeline.
     *
     *@return    The frameCount value
     */
    public int getFrameCount();


    /**
     *  Get the Frame object for the given frame number - or create one if none
     *  exists. If the frame number is larger than the current frame count then
     *  the frame count is increased.
     *
     *@param  frameNumber  must be 1 or larger
     *@return              The frame value
     */
    public Frame getFrame(int frameNumber);


    /**
     *  Append a frame to the end of the timeline
     *
     *@return    Description of the Return Value
     */
    public Frame appendFrame();


    /**
     *  Get the next available depth in the timeline
     *
     *@return    The availableDepth value
     */
    public int getAvailableDepth();


    /**
     *  Set the next available depth in the timeline
     *
     *@param  depth  must be >= 1
     */
    public void setAvailableDepth(int depth);
}
