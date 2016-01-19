package pt.tumba.parser.swf;

import java.io.IOException;

/**
 *  A Sound Information structure - defines playback style and envelope
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class SoundInfo {
    /**
     *  A Point in a sound envelope
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    public static class EnvelopePoint {
        /**
         *  Description of the Field
         */
        public int mark44;
        /**
         *  Description of the Field
         */
        public int level0;
        /**
         *  Description of the Field
         */
        public int level1;


        /**
         *  Constructor for the EnvelopePoint object
         *
         *@param  mark44  Description of the Parameter
         *@param  level0  Description of the Parameter
         *@param  level1  Description of the Parameter
         */
        public EnvelopePoint(int mark44, int level0, int level1) {
            this.mark44 = mark44;
            this.level0 = level0;
            this.level1 = level1;
        }
    }


    /**
     *  Description of the Field
     */
    protected boolean noMultiplePlay;
    //only one instance can play at a time
    /**
     *  Description of the Field
     */
    protected boolean stopPlaying;

    /**
     *  Description of the Field
     */
    protected EnvelopePoint[] envelope;
    /**
     *  Description of the Field
     */
    protected int inPoint;
    /**
     *  Description of the Field
     */
    protected int outPoint;
    /**
     *  Description of the Field
     */
    protected int loopCount;


    /**
     *@param  noMultiplePlay  true = only play if not already playing
     *@param  stopSound       true = stop playing the sound
     *@param  envelope        may be null or empty for no envelope
     *@param  inPoint         -1 for no in-point
     *@param  outPoint        -1 for no out-point
     *@param  loopCount       >1 for a loop count
     */
    public SoundInfo(boolean noMultiplePlay, boolean stopSound,
            EnvelopePoint[] envelope,
            int inPoint, int outPoint, int loopCount) {
        this.noMultiplePlay = noMultiplePlay;
        this.stopPlaying = stopSound;
        this.envelope = envelope;
        this.inPoint = inPoint;
        this.outPoint = outPoint;
        this.loopCount = loopCount;
    }


    /**
     *  Gets the noMultiplePlay attribute of the SoundInfo object
     *
     *@return    The noMultiplePlay value
     */
    public boolean isNoMultiplePlay() {
        return this.noMultiplePlay;
    }


    /**
     *  Gets the stopPlaying attribute of the SoundInfo object
     *
     *@return    The stopPlaying value
     */
    public boolean isStopPlaying() {
        return this.stopPlaying;
    }


    /**
     *  Gets the envelope attribute of the SoundInfo object
     *
     *@return    The envelope value
     */
    public EnvelopePoint[] getEnvelope() {
        return this.envelope;
    }


    /**
     *  Gets the inPoint attribute of the SoundInfo object
     *
     *@return    The inPoint value
     */
    public int getInPoint() {
        return this.inPoint;
    }


    /**
     *  Gets the outPoint attribute of the SoundInfo object
     *
     *@return    The outPoint value
     */
    public int getOutPoint() {
        return this.outPoint;
    }


    /**
     *  Gets the loopCount attribute of the SoundInfo object
     *
     *@return    The loopCount value
     */
    public int getLoopCount() {
        return this.loopCount;
    }


    /**
     *  Constructor for the SoundInfo object
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public SoundInfo(InStream in) throws IOException {
        int flags = in.readUI8();

        noMultiplePlay = ((flags & 16) != 0);
        stopPlaying = ((flags & 32) != 0);
        boolean hasEnvelope = ((flags & 8) != 0);
        boolean hasLoops = ((flags & 4) != 0);
        boolean hasOutPoint = ((flags & 2) != 0);
        boolean hasInPoint = ((flags & 1) != 0);

        if (hasInPoint) {
            inPoint = (int) in.readUI32();
        } else {
            inPoint = -1;
        }

        if (hasOutPoint) {
            outPoint = (int) in.readUI32();
        } else {
            outPoint = -1;
        }

        if (hasLoops) {
            loopCount = in.readUI16();
        } else {
            loopCount = 1;
        }

        int envsize = 0;
        if (hasEnvelope) {
            envsize = in.readUI8();
        }

        envelope = new EnvelopePoint[envsize];

        for (int i = 0; i < envsize; i++) {
            envelope[i] = new EnvelopePoint((int) in.readUI32(),
                    in.readUI16(),
                    in.readUI16());
        }
    }


    /**
     *  Description of the Method
     *
     *@param  out              Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void write(OutStream out) throws IOException {
        int flags = 0;
        if (noMultiplePlay) {
            flags += 1;
        }
        if (stopPlaying) {
            flags += 2;
        }

        out.writeUBits(4, flags);

        boolean hasEnvelope = (envelope != null && envelope.length > 0);
        boolean hasLoops = (loopCount > 1);
        boolean hasOutPoint = (outPoint >= 0);
        boolean hasInPoint = (inPoint >= 0);

        flags = 0;
        if (hasEnvelope) {
            flags += 8;
        }
        if (hasLoops) {
            flags += 4;
        }
        if (hasOutPoint) {
            flags += 2;
        }
        if (hasInPoint) {
            flags += 1;
        }

        out.writeUBits(4, flags);

        if (hasInPoint) {
            out.writeUI32(inPoint);
        }
        if (hasOutPoint) {
            out.writeUI32(outPoint);
        }
        if (hasLoops) {
            out.writeUI16(loopCount);
        }

        if (hasEnvelope) {
            out.writeUI8(envelope.length);

            for (int i = 0; i < envelope.length; i++) {
                out.writeUI32(envelope[i].mark44);
                out.writeUI16(envelope[i].level0);
                out.writeUI16(envelope[i].level1);
            }
        }
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public String toString() {
        return "SoundInfo: no-multiplay=" + noMultiplePlay +
                " stop=" + stopPlaying +
                " envelope=" + ((envelope == null) ? "none" : ("" + envelope.length + " points")) +
                " in-point=" + inPoint +
                " out-point=" + outPoint +
                " loop-count=" + loopCount;
    }
}
