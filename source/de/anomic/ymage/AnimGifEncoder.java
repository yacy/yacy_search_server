/*
 *  (C)2000 by F. Jalvingh, Mumble Internet Services
 *  For questions and the like: fjalvingh@bigfoot.com
 *
 *  Compression part (C)1996,1998 by Jef Poskanzer <jef@acme.com>. All rights reserved.
 *
 *  This software is placed in the public domain. You are free to use this
 *  software for any means while respecting the above copyright.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 *  ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
 *  FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 *  DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 *  OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 *  HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 *  LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 *  OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 *  SUCH DAMAGE.
 *
 *  Optimizations by Jal:
 *  ---------------------
 *  Initial:    Coded RLE code for building the 8-bit color table.
 *  6dec00:     Changed code to remove extraneous if's and unrolled some calls.
 *              Replaced color hashtable with local specialized variant.
 *  7dec00:     Made specialized direct buffer access versions for BufferedImage
 *              images..
 *
 */

// very slightly adopted by Michael Christen, 12.12.2007
// - removed unused variables
// - replaced old java classes by new one

package de.anomic.ymage;

import java.awt.Canvas;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.IndexColorModel;
import java.awt.image.PixelGrabber;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 *  <p>This class can be used to write an animated GIF file by combining several
 *  images. It is loosely based on the Acme GIF encoder.</p>
 *
 *  <p>The characteristics of the generated Gif 89a image are:
 *  <ul>
 *      <li>Only a single color table is used (no local tables). This table is
 *          created by combining the colors from all other images.</li>
 *  </ul>
 *  </p>
 *
 *  @author F. Jalvingh
 */
public class AnimGifEncoder {
    /** The default interlacing indicator */
    private boolean         m_default_interlace = false;

    /** The default delay time, */
    private final static int             m_default_delay = 100;

    /** Set when looping the set is requested. */
    private boolean         m_loop  = true;

    /** The outputstream to write the image to. */
    private final OutputStream    m_os;

    /** The (current) list of images to embed in the GIF */
    private ArrayList<AnIma> m_ima_ar;

    /** The total width and height of all combined images */
    private int             m_w, m_h;

    /** The canvas is used to proprly track images. */
    private Canvas          m_cv;

    /** The index for the "transparant" color. -1 if no transparant found. */
    private short           m_transparant_ix = -1;

    /** The index (palette table entry #) to use for the NEXT color encountered */
    private short           m_color_ix;

    /** The #of bits to use (2log m_color_ix). */
    private int             m_color_bits;

    /// Temp optimization inhibition.
    public boolean          m_no_opt;

    /**
     *  This constructor creates an empty default codec.
     */
    public AnimGifEncoder(final OutputStream os) {
        m_os    = os;
    }

    /**
     *  Creates a codec and specify interlace (not implemented yet).
     */
    public AnimGifEncoder(final OutputStream os, final boolean interlace) {
        m_os    = os;
        m_default_interlace = interlace;
    }

    /**
     *  <p>For animated GIF's the default is to LOOP all images in the GIF file.
     *  This means that after displaying all images in the file the first image
     *  is redisplayed ad infinitum.</p>
     *  <p>To prevent the images from looping call setLoop(false) before calling
     *  the encode() method.
     *  </p>
     *  <p>The current version does not allow the number of repetitions to be
     *  specified.
     *  </p>
     */
    public void setLoop(final boolean loop) {
        m_loop = loop;
    }

    /**
     *  Releases ALL cached resources.
     */
    public void flush() {
        //-- 1. The basic stuff
        m_ccolor_ar = null;
        m_cindex_ar = null;
        m_cv        = null;
        m_ima_ar    = null;

        //-- 2. The compressor.
        m_curr_pixels   = null;
        htab            = null;
        codetab         = null;
        accum           = null;
    }

    /*------------------------------------------------------------------*/
    /*  CODING: Adding images to combine into the animated GIF...       */
    /*------------------------------------------------------------------*/
    /**
     *  Adds the specified image to the list of images. While adding, the
     *  image is converted to pixels; each color is added to the color table
     *  and the resulting 8-bit pixelset is saved. After this call the image
     *  is released, and only the pixelset remains until the encode call is
     *  made. Calling encode will release the pixelset.
     */
    public void add(final Image ima, final int delaytime, final boolean interlace, final int px, final int py) throws IOException {
        final AnIma   ai      = new AnIma();
        ai.m_delay      = delaytime;
        ai.m_interlace  = interlace;
        ai.m_x          = px;
        ai.m_y          = py;

        //-- Add to the list of images to embed,
        if(m_ima_ar == null)                            // First call?
        {
            m_ccolor_ar = new int[CHSIZE];              // New colors code table
            m_cindex_ar = new short[CHSIZE];
            m_ima_ar    = new ArrayList<AnIma>(10);            // Contains all component images,
            m_cv        = new Canvas();
        }
        m_ima_ar.add(ai);

        //-- Pre-scan the image!!
        if(! m_no_opt)
            preCode(ai, ima);                           // Convert to 8bit and make palette
        else
            precodeImage(ai, ima);
    }
    
    /**
     *  Adds the specified image to the list of images.
     */
    public void add(final Image ima) throws IOException {
        add(ima, m_ima_ar == null ? 0 : m_default_delay, m_default_interlace, 0, 0);
    }

    /**
     *  Adds the specified image to the list of images.
     */
    public void add(final Image ima, final int delay) throws IOException {
        add(ima, delay, m_default_interlace, 0, 0);
    }

    /*------------------------------------------------------------------*/
    /*  CODING: I/O to the file - helpers...                            */
    /*------------------------------------------------------------------*/
    /**
     *  Writes a string as a #of bytes to the output stream.
     */
    private void utStr(final String str) throws IOException {
        final byte[] buf = str.getBytes();
        m_os.write( buf );
    }

    private void utWord(final int val) throws IOException {
        utByte( (byte) ( val & 0xff));
        utByte( (byte) (( val >> 8 ) & 0xff ));
    }

    private void utByte(final byte b) throws IOException {
        m_os.write( b );
    }

    /*------------------------------------------------------------------*/
    /*  CODING: Starting the encode process...                          */
    /*------------------------------------------------------------------*/
    /**
     *  Creates the GIF file from all images added to the encoder.
     */
    public void encode() throws IOException {
        //-- Check validity,
        if(m_ima_ar == null || m_ima_ar.size() == 0)
            throw new IOException("No images added.");

        //-- Init the compressor's tables
        htab    = new int[HSIZE];
        codetab = new int[HSIZE];
        accum   = new byte[256];

        //-- Write the GIF header now,
        genHeader();

        /*
         *  Traverse the data for each image. This determines the actual color
         *  table and the complete output size.
         */
        for (int i = 0; i < m_ima_ar.size(); i++) {
            final AnIma   ai = m_ima_ar.get(i);
            genImage(ai);
            ai.m_rgb    = null;
        }
        genTrailer();
        flush();
    }

    /*--------------------------------------------------------------*/
    /*  CODING: Color table code & specialized color hashtable.     */
    /*--------------------------------------------------------------*/
    /*
     *  This is a hashtable mapping (int, byte). The first int is the actual
     *  color as gotten from the image. The byte is the index color in the
     *  colormap for the entry.
     *  We need to find (byte) by indexing with (int) VERY quicky.
     *  Furthermore we already know that the table will at max hold 256 entries.
     *
     *  Since all colors >= 0 are transparant, we use (int) = 0 as the empty
     *  case.
     *
     *  This hashtable uses the same hash mechanism as the LZH compressor: a
     *  double hash without chaining.
     */
    static private final int            CHSIZE  = 1023;

    /// The color hashtable's COLOR table (int rcolors)
    private int[]               m_ccolor_ar;

    /// The color hashtable's INDEX table (byte index)
    private short[]             m_cindex_ar;

    /**
     *  This retrieves the index for a color code from the color hash. If the
     *  color doesn't exist it is added to the hash table. This uses the double
     *  hash mechanism described above. If this call causes >255 colors to be
     *  stored it throws a too many colors exception.
     *  The function returns the index code for the color.
     */
    private short findColorIndex(final int color) throws IOException {
        //-- 1. Primary hash..
        int     i = (color & 0x7fffffff) % CHSIZE;

        if(m_ccolor_ar[i] == color)             // Bucket found?
            return m_cindex_ar[i];

        //-- 2. No match. If the bucket is not empty do the 2nd hash,
        if(m_ccolor_ar[i] != 0)                 // Bucket is full?
        {
            //-- This was a clash. Locate a new bucket & look for another match!
            final int disp = CHSIZE - i;
            do
            {
                i   -= disp;
                if(i < 0) i += CHSIZE;
                if(m_ccolor_ar[i] == color)             // Found in 2nd hash?
                    return m_cindex_ar[i];              // Then return it.
            } while(m_ccolor_ar[i] != 0);               // Loop till empty bucket.
        }

        //-- 3. Empty bucket found: add this there as a new index.
        if(m_color_ix >= 256)
            throw new IOException("More than 255 colors in this GIF are not allowed.");
        m_ccolor_ar[i] = color;
        m_cindex_ar[i] = m_color_ix;
        return m_color_ix++;
    }
    
    /*--------------------------------------------------------------*/
    /*  CODING: Optimized pixel grabbers...                         */
    /*--------------------------------------------------------------*/
    /**
     *  Checks if the image lies in the current complete image, else it extends
     *  the source image.
     */
    private void checkTotalSize(final AnIma ai) {
        int     t;

        t = ai.m_w + ai.m_x;                    // Get end-X of image,
        if(t > m_w) m_w = t;                    // Adjust complete GIF's size
        t   = ai.m_h + ai.m_y;                  // Get total height
        if(t > m_h) m_h = t;                    // Adjust if higher,
    }
    
    /*--------------------------------------------------------------*/
    /*  CODING: The precoder translates all to 8bit indexed...      */
    /*--------------------------------------------------------------*/
    /**
     *  Traverse this image, and determine it's characteristics. It adds all
     *  used colors to the color table and determines the completed size of
     *  the thing. The image is converted to an 8-bit pixelmap where each pixel
     *  indexes the generated color table.
     *  This function tries to get the fastest access to the pixel data for
     *  several types of BufferedImage. This should enhance the encoding speed
     *  by preventing the loop thru the entire generalized Raster and ColorModel
     *  method....
     *  All precode methods build a color table containing all colors used in
     *  the image, and an 8-bit "image" containing, for each pixel, the index
     *  into that color table. They also set the transparant color to use.
     */
    private void preCode(final AnIma ai, final Image ima) throws IOException {
        //-- Call the appropriate encoder depending on the image type.
        if(ima instanceof BufferedImage)
            precodeBuffered(ai, (BufferedImage) ima);
        else
            precodeImage(ai, ima);
    }

    /**
     *  Tries to decode a buffered image in an optimal way. It checks to see
     *  if it knows the BufferedImage type and calls the appropriate quick
     *  decoder. If the image is not implemented we fall back to the generic
     *  method.
     */
    private void precodeBuffered(final AnIma ai, final BufferedImage bi) throws IOException {
        //-- 1. Handle all shared tasks...
        ai.m_w  = bi.getWidth();
        ai.m_h  = bi.getHeight();
        if(ai.m_h == 0 || ai.m_w == 0) return;
        checkTotalSize(ai);

        //-- 2. Optimize for known types...
        boolean done= false;
        final int bt  = bi.getType();
        switch(bt)
        {
            case BufferedImage.TYPE_BYTE_INDEXED:   done = precodeByteIndexed(ai, bi);  break;
            case BufferedImage.TYPE_INT_BGR:        done = precodeIntPacked(ai, bi); break;
            case BufferedImage.TYPE_INT_ARGB:       done = precodeIntPacked(ai, bi); break;
            case BufferedImage.TYPE_USHORT_555_RGB: done = precodeShortPacked(ai, bi); break;
            case BufferedImage.TYPE_USHORT_565_RGB: done = precodeShortPacked(ai, bi); break;
            case BufferedImage.TYPE_INT_RGB:        done = precodeIntPacked(ai, bi); break;
        }

        if(done) return;

        precodeImage(ai, bi);
    }

    private int getBiOffset(final Raster ras, final PixelInterleavedSampleModel sm, final int x, final int y) {
        return (y-ras.getSampleModelTranslateY()) * sm.getScanlineStride() + x-ras.getSampleModelTranslateX();
    }

    private int getBiOffset(final Raster ras, final SinglePixelPackedSampleModel sm, final int x, final int y) {
        return (y-ras.getSampleModelTranslateY()) * sm.getScanlineStride() + x-ras.getSampleModelTranslateX();
    }

    /*--------------------------------------------------------------*/
    /*  CODING: BufferedImage.TYPE_BYTE_INDEXED..                   */
    /*--------------------------------------------------------------*/
    /**
     *  Encodes TYPE_BYTE_INDEXED images.
     */
    private boolean precodeByteIndexed(final AnIma ai, final BufferedImage bi) throws IOException {
        //-- Get the colormodel, the raster, the databuffer and the samplemodel
        final ColorModel  tcm = bi.getColorModel();
        if(! (tcm instanceof IndexColorModel)) return false;
        final IndexColorModel cm  = (IndexColorModel) tcm;

        final Raster      ras = bi.getRaster();
        final SampleModel tsm = ras.getSampleModel();
        if(! (tsm instanceof PixelInterleavedSampleModel)) return false;
        final PixelInterleavedSampleModel sm  = (PixelInterleavedSampleModel) tsm;

        final DataBuffer  dbt = ras.getDataBuffer();
        if(dbt.getDataType() != DataBuffer.TYPE_BYTE) return false;
        if(dbt.getNumBanks() != 1) return false;
        final DataBufferByte  db  = (DataBufferByte) dbt;

        //-- Prepare the color mapping
        final short[] map = new short[256];                   // Alternate lookup table
        for(int i = 0; i < 256; i++)                    // Set all entries to unused,
            map[i] = -1;

        /*
         *  Prepare the run: get all constants e.a. The mechanism runs thru
         *  all pixels by traversing each X scanline, then moving to the next
         *  one. One fun thing: we only have to COPY all pixels, since we're
         *  already byte-packed.
         */
        final int     endoff  = ai.m_w * ai.m_h;              // Output image size,
        final byte[]  par     = new byte[endoff];             // Byte-indexed output array,
        int     doff    = 0;                            // Destination offset,

        //-- source
        int     soff    = getBiOffset(ras, sm, 0, 0);
        final byte[]  px      = db.getData(0);                // Get the pixelset,
        final int     esoff   = getBiOffset(ras, sm, ai.m_w-1, ai.m_h-1);                 // calc end offset,
        final int     iw      = sm.getScanlineStride();       // Increment width = databuf's width

        while(soff < esoff) {                           // For all scan lines,
        
            final int     xe = soff + ai.m_w;                 // End for this line
            while(soff < xe) {                          // While within this line
                //-- (continue) collect a run,
                final int rs  = soff;                         // Save run start
                final byte    rcolor  = px[soff++];           // First color
                while(soff < xe && px[soff] == rcolor)  // Run till eoln or badclor
                    soff++;

                //-- Run ended. Map the input index to the GIF's index,
                short   ii = map[rcolor + 0x80];
                if (ii == -1){                          // Unknown map?
                    //-- New color. Get it's translated RGB value,
                    final int rix = rcolor & 0xff;       // Translate to unsigned
                    int rgb = cm.getRGB(rix);           // Get RGB value for this input index,
                    if(rgb >= 0) {                      // Transparant color?
                        //-- If there is a transparant color index use it...
                        if (m_transparant_ix < 0) {
                            //-- First transparant color found- save it,
                            if(rgb == 0) rgb = 1;       // Zero color protection - req'd for hashtable implementation
                            m_transparant_ix = findColorIndex(rgb);
                        }
                        ii  = m_transparant_ix;         // Use trans color to fill
                    } else {
                        //-- Not transparant,
                        ii  = findColorIndex(rgb);      // Add RGB value to the index,
                    }
                    map[rcolor + 0x80] = ii;
                }

                //-- Always write this run.
                final int     dep = doff + (soff - rs);   // End output pos
                final byte    idx = (byte) ii;
                while(doff < dep)
                    par[doff++] = idx;              // Fill output.
            }

            //-- Prepare for a new line.
            soff    += iw - ai.m_w;                 // Increment what's left to next line,
        }

        ai.m_rgb    = par;                          // Save created thing
        return true;
    }

    /*--------------------------------------------------------------*/
    /*  CODING: BufferedImage.All int packed stuff..                */
    /*--------------------------------------------------------------*/
    /**
     *  Encodes INT pixel-packed images.
     */
    private boolean precodeIntPacked(final AnIma ai, final BufferedImage bi) throws IOException {
        //-- Get the colormodel, the raster, the databuffer and the samplemodel
        final ColorModel  cm  = bi.getColorModel();
        final Raster      ras = bi.getRaster();
        final SampleModel tsm = ras.getSampleModel();
        if(! (tsm instanceof SinglePixelPackedSampleModel)) return false;
        final SinglePixelPackedSampleModel    sm  = (SinglePixelPackedSampleModel) tsm;

        final DataBuffer  dbt = ras.getDataBuffer();
        if(dbt.getDataType() != DataBuffer.TYPE_INT) return false;
        if(dbt.getNumBanks() != 1) return false;
        final DataBufferInt   db  = (DataBufferInt) dbt;

        /*
         *  Prepare the run: get all constants e.a. The mechanism runs thru
         *  all pixels by traversing each X scanline, then moving to the next
         *  one. One fun thing: we only have to COPY all pixels, since we're
         *  already byte-packed.
         */
        final int     endoff  = ai.m_w * ai.m_h;              // Output image size,
        final byte[]  par     = new byte[endoff];             // Byte-indexed output array,
        int     doff    = 0;                            // Destination offset,
        byte    ii;

        //-- source
        int     soff    = getBiOffset(ras, sm, 0, 0);
        final int[]   px      = db.getData(0);                // Get the pixelset,
        final int     esoff   = getBiOffset(ras, sm, ai.m_w-1, ai.m_h-1);                 // calc end offset,
        final int     iw      = sm.getScanlineStride();       // Increment width = databuf's width

        while(soff < esoff) {                            // For all scan lines,
        
            final int     xe = soff + ai.m_w;                 // End for this line
            while (soff < xe) {                         // While within this line
                //-- (continue) collect a run,
                final int rs  = soff;                         // Save run start
                final int rcolor  = px[soff++];               // First color
                while(soff < xe && px[soff] == rcolor)  // Run till eoln or badclor
                    soff++;

                //-- Run ended. Map the input index to the GIF's index,
                int rgb = cm.getRGB(rcolor);            // Get RGB value for this input index,
                if(rgb >= 0) {                          // Transparant color?
                    //-- If there is a transparant color index use it...
                    if(m_transparant_ix < 0) {
                        //-- First transparant color found- save it,
                        if(rgb == 0) rgb = 1;       // Zero color protection - req'd for hashtable implementation
                        m_transparant_ix = findColorIndex(rgb);
                    }
                    ii  = (byte)m_transparant_ix;           // Use trans color to fill
                } else {
                    //-- Not transparant,
                    ii  = (byte)findColorIndex(rgb);        // Add RGB value to the index,
                }

                //-- Always write this run.
                final int     dep = doff + (soff - rs);   // End output pos
                while(doff < dep)
                    par[doff++] = ii;               // Fill output.
            }

            //-- Prepare for a new line.
            soff    += iw - ai.m_w;                 // Increment what's left to next line,

        }

        ai.m_rgb    = par;                          // Save created thing
        return true;
    }

    /*--------------------------------------------------------------*/
    /*  CODING: BufferedImage- SHORT type stuff..                   */
    /*--------------------------------------------------------------*/
    /**
     *  Encodes SHORT pixel-packed images.
     */
    private boolean precodeShortPacked(final AnIma ai, final BufferedImage bi) throws IOException {
        //-- Get the colormodel, the raster, the databuffer and the samplemodel
        final ColorModel  cm  = bi.getColorModel();
        final Raster      ras = bi.getRaster();
        final SampleModel tsm = ras.getSampleModel();
        if(! (tsm instanceof SinglePixelPackedSampleModel)) return false;
        final SinglePixelPackedSampleModel    sm  = (SinglePixelPackedSampleModel) tsm;

        final DataBuffer  dbt = ras.getDataBuffer();
        if(dbt.getDataType() != DataBuffer.TYPE_SHORT) return false;
        if(dbt.getNumBanks() != 1) return false;
        final DataBufferShort db  = (DataBufferShort) dbt;

        /*
         *  Prepare the run: get all constants e.a. The mechanism runs thru
         *  all pixels by traversing each X scanline, then moving to the next
         *  one. One fun thing: we only have to COPY all pixels, since we're
         *  already byte-packed.
         */
        final int     endoff  = ai.m_w * ai.m_h;              // Output image size,
        final byte[]  par     = new byte[endoff];             // Byte-indexed output array,
        int     doff    = 0;                            // Destination offset,
        byte    ii;

        //-- source
        int     soff    = getBiOffset(ras, sm, 0, 0);
        final short[] px      = db.getData(0);                // Get the pixelset,
        final int     esoff   = getBiOffset(ras, sm, ai.m_w-1, ai.m_h-1);                 // calc end offset,
        final int     iw      = sm.getScanlineStride();       // Increment width = databuf's width

        while(soff < esoff)                             // For all scan lines,
        {
            final int     xe = soff + ai.m_w;                 // End for this line
            while(soff < xe)                            // While within this line
            {
                //-- (continue) collect a run,
                final int rs  = soff;                         // Save run start
                final short   rcolor  = px[soff++];           // First color
                while(soff < xe && px[soff] == rcolor)  // Run till eoln or badclor
                    soff++;

                //-- Run ended. Map the input index to the GIF's index,
                int rgb = cm.getRGB(rcolor);            // Get RGB value for this input index,
                if(rgb >= 0)                        // Transparant color?
                {
                    //-- If there is a transparant color index use it...
                    if(m_transparant_ix < 0)
                    {
                        //-- First transparant color found- save it,
                        if(rgb == 0) rgb = 1;       // Zero color protection - req'd for hashtable implementation
                        m_transparant_ix = findColorIndex(rgb);
                    }
                    ii  = (byte)m_transparant_ix;           // Use trans color to fill
                }
                else
                {
                    //-- Not transparant,
                    ii  = (byte)findColorIndex(rgb);        // Add RGB value to the index,
                }

                //-- Always write this run.
                final int     dep = doff + (soff - rs);   // End output pos
                while(doff < dep)
                    par[doff++] = ii;               // Fill output.
            }

            //-- Prepare for a new line.
            soff    += iw - ai.m_w;                 // Increment what's left to next line,

        }

        ai.m_rgb    = par;                          // Save created thing
        return true;
    }


    /*--------------------------------------------------------------*/
    /*  CODING: The generic Image stuff to translate the GIF        */
    /*--------------------------------------------------------------*/
    /**
     *  Using a generic Image, this uses a PixelGrabber to get an integer
     *  pixel array.
     */
    private void precodeImage(final AnIma ai, final Image ima) throws IOException {
        int[]       px;

        //-- Wait for the image to arrive,
        MediaTracker    mt  = new MediaTracker(m_cv);
        mt.addImage(ima, 0);
        try
        {
            mt.waitForAll();                            // Be use all are loaded,
        }
        catch(final InterruptedException x)
        {
            throw new IOException("Interrupted load of image");
        }
        mt.removeImage(ima, 0);
        mt  = null;

        //-- Get the images' size & adjust the complete GIF's size,
        ai.m_w  = ima.getWidth(m_cv);
        ai.m_h  = ima.getHeight(m_cv);
        if(ai.m_h == 0 || ai.m_w == 0) return;
        checkTotalSize(ai);

        //-- Grab pixels & convert to 8-bit pixelset.
        final PixelGrabber    pg  = new PixelGrabber(ima, 0, 0, ai.m_w, ai.m_h, true);
        try {
            pg.grabPixels();
        } catch(final InterruptedException x) {
            throw new IOException("Interrupted load of image");
        }
        px = (int[]) pg.getPixels();    // Get the pixels,

        translateColorsByArray(ai, px);         // Run the translator
    }


    /**
     *  For each pixel in the source image, the color is put into the palette
     *  for the combined GIF. The index of the color is then used in the 8-bit
     *  pixelset for this image.
     */
    private void translateColorsByArray(final AnIma a, final int[] px) throws IOException {
        int         off;
        byte[]      par;
        final int         endoff  = a.m_w * a.m_h;    // Total #pixels in image
        int         rstart, rcolor;             // Run data.
        byte        newc;

        //-- Collect runs of pixels of the same color; then handle them;
        par = new byte[endoff];                 // Allocate output matrix
        off = 0;                                // Output offset,
        while(off < endoff) {
            //-- Collect the current run of pixels.
            rstart  = off;
            rcolor  = px[off++];                // Get 1st pixel of run,
            while(off < endoff && px[off] == rcolor)    // Fast loop!
                off++;

            //-- Translate the color to an index, and handle transparency,
            if(rcolor >= 0)                     // Is this a TRANSPARANT color?
            {
                //-- If there is a transparant color index use it...
                if(m_transparant_ix < 0)
                {
                    //-- First transparant color found- save it,
                    if(rcolor == 0) rcolor = 1; // Zero color protection - req'd for hashtable implementation
                    m_transparant_ix = findColorIndex(rcolor);
                }
                newc = (byte)m_transparant_ix;   // Set color to fill run with
            }
            else
            {
                //-- Not transparant- is an index known for this color?
                final int     i = (rcolor & 0x7fffffff) % CHSIZE;

                if(m_ccolor_ar[i] == rcolor)                // Bucket found?
                    newc = (byte)m_cindex_ar[i];
                else
                    newc = (byte)findColorIndex(rcolor);    // Get color index,
            }

            //-- Always fill the run with the replaced color,
            while(rstart < off)
                par[rstart++] = newc;

            //-- This run has been done!!
        }

        a.m_rgb = par;                              // Save completed map;
    }

    /**
     *  Generates the color map by using the color table and creating all
     *  rgb tables. These are then written to the output. This gets called when
     *  all images have been added and pre-traversed.
     */
    private void genColorTable() throws IOException {
        // Turn colors into colormap entries.
        final int nelem = 1 << m_color_bits;
        final byte[] reds = new byte[nelem];
        final byte[] grns = new byte[nelem];
        final byte[] blus = new byte[nelem];

        //-- Now enumerate the color table.
        for (int i = CHSIZE; --i >= 0;) {           // Count backwards (faster)
            if(m_ccolor_ar[i] != 0) {               // A color was found?
                reds[ m_cindex_ar[i] ]  = (byte) ( (m_ccolor_ar[i] >> 16) & 0xff);
                grns[ m_cindex_ar[i] ]  = (byte) ( (m_ccolor_ar[i] >> 8) & 0xff);
                blus[ m_cindex_ar[i] ]  = (byte) ( m_ccolor_ar[i] & 0xff );
            }
        }

        //-- Write the map to the stream,
        for (int i = 0; i < nelem; i++) {           // Save all elements,
            utByte(reds[i]);
            utByte(grns[i]);
            utByte(blus[i]);
        }
    }

    /**
     *  Writes the GIF file header, containing all up to the first image data
     *  structure: color table, option fields etc.
     */
    private void genHeader() throws IOException {
        // Figure out how many bits to use.
        if(m_color_ix <= 2)
            m_color_bits = 1;
        else if(m_color_ix <= 4)
            m_color_bits = 2;
        else if(m_color_ix <= 8)
            m_color_bits = 3;
        else if(m_color_ix <= 16)
            m_color_bits = 4;
        else
            m_color_bits = 8;

        //-- Start with the headerm
        utStr("GIF89a" );                           // Gif89a Header: signature & version

        //-- Logical Screen Descriptor Block
        utWord(m_w);                                // Collated width & height of all images
        utWord(m_h);
        final byte    b = (byte)(0xF0 | (m_color_bits-1));// There IS a color map, 8 bits per color source resolution. not sorted,
        utByte(b);                                  // Packet fields,
        utByte((byte)0);                            // Background Color Index assumed 0.
        utByte((byte)0);                            // Pixel aspect ratio 1:1: zero always works...

        //-- Now write the Global Color Map.
        genColorTable();

        if (m_loop && m_ima_ar.size() > 1) {
            //-- Generate a Netscape loop thing,
            utByte((byte) 0x21);
            utByte((byte) 0xff);
            utByte((byte) 0x0b);
            utStr("NETSCAPE2.0");
            utByte((byte) 0x03);
            utByte((byte) 1);
            utWord(0);                              // Repeat indefinitely
            utByte((byte)0);
        }
    }

    /**
     *  Writes the GIF file trailer, terminating the GIF file.
     */
    private void genTrailer() throws IOException {
        // Write the GIF file terminator
        utByte((byte) ';');
    }

    /**
     *  Writes a single image instance.
     */
    private void genImage(final AnIma ai) throws IOException {
        //-- Write out a Graphic Control Extension for transparent colour & repeat, if necessary,
        if(m_transparant_ix != -1 || m_ima_ar.size() > 1) {
            byte transpar;

            utByte( (byte) '!');                    // 0x21 Extension Introducer
            utByte( (byte) 0xf9);                   // Graphic Control Label
            utByte( (byte) 4);                      // Block Size,
            if(m_transparant_ix >= 0) {             // There IS transparancy?
                utByte((byte) 1);                   // TRANS flag SET
                transpar    = (byte) m_transparant_ix;
            } else {
                utByte((byte) 0);                   // TRANS flag CLEAR
                transpar    = 0;
            }
            utWord( ai.m_delay );                   // Delay time,
            utByte(transpar);                       // And save the index,
            utByte( (byte) 0);
        }

        //-- Write the Image Descriptor
        utByte((byte)',');
        utWord(ai.m_x);                             // Image left position,
        utWord(ai.m_y);                             // Image right position
        utWord(ai.m_w);
        utWord(ai.m_h);                             // And it's size,
        utByte((byte) (ai.m_interlace ? 0x40 : 0)); // Packed fields: interlaced Y/N, no local table no sort,

        //-- The table-based image data...
        final int initcodesz = m_color_bits <= 1 ? 2 : m_color_bits;
        utByte((byte) initcodesz);                  // Output initial LZH code size, min. 2 bits,
        genCompressed(ai, initcodesz+1);            // Generate the compressed data,
        utByte((byte) 0);                           // Zero-length packet (end series)
    }

    /*------------------------------------------------------------------*/
    /*  CODING: Stuff to compress!!!                                    */
    /*------------------------------------------------------------------*/
    /*
     *  Most of this compressor code has been reaped from the ACME GifEncoder
     *  package. See there for more details.
     *  This code will be revised for speed in the next release though.
     */
    /** Pixmap from ima currently compressed */
    private byte[]          m_curr_pixels;

    /** Current pixel source index in above map */
    private int             m_px_ix;

    /** End index within above index. */
    private int             m_px_endix;

    private void genCompressed(final AnIma a, final int initcodesz) throws IOException {
        //-- Set all globals to retrieve pixel data quickly. $$TODO: Interlaced
        m_curr_pixels   = a.m_rgb;
        m_px_ix         = 0;
        m_px_endix      = a.m_w * a.m_h;            // Last index,

        //-- Coder variables.
        int             i, c, ent, disp, hsize_reg, hshift, fcode;

        //-- Init: the bit-code writer's variables,
        cur_accum   = 0;
        cur_bits    = 0;
        free_ent    = 0;
        clear_flg   = false;
        maxbits     = BITS;         // user settable max # bits/code
        maxmaxcode  = 1 << BITS; // should NEVER generate this code
        a_count     = 0;
        g_init_bits = initcodesz;                   // Initial #of bits

        // Set up the necessary values
        clear_flg   = false;
        n_bits      = g_init_bits;
        maxcode     = MAXCODE( n_bits );
        ClearCode   = 1 << ( initcodesz - 1 );
        EOFCode     = ClearCode + 1;
        free_ent    = ClearCode + 2;
        char_init();

        hshift = 0;
        for ( fcode = hsize; fcode < 65536; fcode *= 2 )
            ++hshift;
        hshift = 8 - hshift;            // set hash code range bound

        hsize_reg = hsize;
        cl_hash( hsize_reg );   // clear hash table
        output(ClearCode);

        ent = m_curr_pixels[m_px_ix++];             // Get 1st pixel value,
        outer_loop: while(m_px_ix < m_px_endix)     // While not at end
        {
            c   = m_curr_pixels[m_px_ix++];         // Get next pixel value,
            fcode = ( c << maxbits ) + ent;
            i = ( c << hshift ) ^ ent;      // xor hashing

            if(htab[i] == fcode)
            {
                ent = codetab[i];
                continue;
            }
            else if ( htab[i] >= 0 )    // non-empty slot
            {
                disp = hsize_reg - i;   // secondary hash (after G. Knott)
                if ( i == 0 )           // ?? Should be inpossible?? JAL
                    disp = 1;
                do
                {
                    if( (i -= disp) < 0 )
                        i += hsize_reg;

                    if ( htab[i] == fcode )
                    {
                        ent = codetab[i];
                        continue outer_loop;
                    }
                }
                while ( htab[i] >= 0 );
            }
            output(ent);
            ent = c;
            if ( free_ent < maxmaxcode )
            {
                codetab[i] = free_ent++;    // code -> hashtable
                htab[i] = fcode;
            }
            else
                cl_block();
        }
        // Put out the final code.
        output(ent);
        outputEOF();
    }

    static final int EOF = -1;
    
    // GIFCOMPR.C       - GIF Image compression routines
    //
    // Lempel-Ziv compression based on 'compress'.  GIF modifications by
    // David Rowley (mgardi@watdcsu.waterloo.edu)

    // General DEFINEs

    static final int BITS = 12;
    static final int HSIZE = 5003;      // 80% occupancy

    // GIF Image compression - modified 'compress'
    //
    // Based on: compress.c - File compression ala IEEE Computer, June 1984.
    //
    // By Authors:  Spencer W. Thomas      (decvax!harpo!utah-cs!utah-gr!thomas)
    //              Jim McKie              (decvax!mcvax!jim)
    //              Steve Davies           (decvax!vax135!petsd!peora!srd)
    //              Ken Turkowski          (decvax!decwrl!turtlevax!ken)
    //              James A. Woods         (decvax!ihnp4!ames!jaw)
    //              Joe Orost              (decvax!vax135!petsd!joe)

    int n_bits;             // number of bits/code
    int maxbits = BITS;         // user settable max # bits/code
    int maxcode;            // maximum code, given n_bits
    int maxmaxcode = 1 << BITS; // should NEVER generate this code

    final int MAXCODE( final int n_bits ) {
        return ( 1 << n_bits ) - 1;
    }

    int[] htab;
    int[] codetab;

    int hsize = HSIZE;      // for dynamic table sizing

    int free_ent = 0;           // first unused entry

    // block compression parameters -- after all codes are used up,
    // and compression rate changes, start over.
    boolean clear_flg = false;

    // Algorithm:  use open addressing double hashing (no chaining) on the
    // prefix code / next character combination.  We do a variant of Knuth's
    // algorithm D (vol. 3, sec. 6.4) along with G. Knott's relatively-prime
    // secondary probe.  Here, the modular division first probe is gives way
    // to a faster exclusive-or manipulation.  Also do block compression with
    // an adaptive reset, whereby the code table is cleared when the compression
    // ratio decreases, but after the table fills.  The variable-length output
    // codes are re-sized at this point, and a special CLEAR code is generated
    // for the decompressor.  Late addition:  construct the table according to
    // file size for noticeable speed improvement on small files.  Please direct
    // questions about this implementation to ames!jaw.

    int g_init_bits;
    int ClearCode;
    int EOFCode;

    // Output the given code.
    // Inputs:
    //      code:   A n_bits-bit integer.  If == -1, then EOF.  This assumes
    //              that n_bits =< wordsize - 1.
    // Outputs:
    //      Outputs code to the file.
    // Assumptions:
    //      Chars are 8 bits long.
    // Algorithm:
    //      Maintain a BITS character long buffer (so that 8 codes will
    // fit in it exactly).  Use the VAX insv instruction to insert each
    // code in turn.  When the buffer fills up empty it and start over.

    int cur_accum = 0;
    int cur_bits = 0;

    static int masks[] = {
            0x0000, 0x0001, 0x0003, 0x0007, 0x000F,
            0x001F, 0x003F, 0x007F, 0x00FF,
            0x01FF, 0x03FF, 0x07FF, 0x0FFF,
            0x1FFF, 0x3FFF, 0x7FFF, 0xFFFF };

    void output(final int code) throws IOException {
        cur_accum |= ( code << cur_bits );
        cur_bits += n_bits;

        while( cur_bits >= 8 ) {
            //-- Expanded char_out code
            accum[a_count++] = (byte) cur_accum;
            if ( a_count >= 254 )
                flush_char();
            //-- End of char_out expansion

            cur_accum >>= 8;
            cur_bits -= 8;
        }

        // If the next entry is going to be too big for the code size,
        // then increase it, if possible.
        // $$Rewrote if (JAL)
        if(clear_flg) {
            maxcode = MAXCODE(n_bits = g_init_bits);
            clear_flg = false;
        } else if(free_ent > maxcode) {
            ++n_bits;

            if (n_bits == maxbits)
                maxcode = maxmaxcode;
            else
                maxcode = MAXCODE(n_bits);
        }
    }

    /**
     *  Removed from output() above to skip an extra IF in the main loop. Must
     *  be called instead of calling output(EOFCode).
     */
    private void outputEOF() throws IOException {
        output(EOFCode);                            // Actually output the code

        //-- At EOF, write the rest of the buffer.
        while( cur_bits > 0)
        {
            //-- Expanded char_out.
            accum[a_count++] = (byte) cur_accum;
            if ( a_count >= 254 )
                flush_char();
            //-- End of char_out expansion
            cur_accum >>= 8;
            cur_bits -= 8;
        }
        flush_char();
    }

    // Clear out the hash table
    // table clear for block compress
    void cl_block() throws IOException {
        cl_hash( hsize );
        free_ent = ClearCode + 2;
        clear_flg = true;

        output(ClearCode);
    }

    // reset code table
    void cl_hash( final int hsize ) {
        for(int i = hsize; --i >= 0;)
            htab[i] = -1;
    }

    // GIF Specific routines
    
    // Number of characters so far in this 'packet'
    int a_count;

    // Set up the 'byte output' routine
    void char_init() {
        a_count = 0;
    }

    // Define the storage for the packet accumulator
    byte[] accum;

    // Add a character to the end of the current packet, and if it is 254
    // characters, flush the packet to disk.
    void char_out(final byte c) throws IOException {
        accum[a_count++] = c;
        if ( a_count >= 254 )
            flush_char();
    }

    // Flush the packet to disk, and reset the accumulator
    void flush_char() throws IOException {
        if( a_count > 0) {
            m_os.write( a_count );
            m_os.write( accum, 0, a_count );
            a_count = 0;
        }
    }
    
    // test method for ymage classes
    public static void main(final String[] args) {
        System.setProperty("java.awt.headless", "true");
        
        final ymageMatrix m = new ymageMatrix(200, 300, ymageMatrix.MODE_SUB, "FFFFFF");
        ymageMatrix.demoPaint(m);
        final File file = new File("/Users/admin/Desktop/testimage.gif");
        
        OutputStream os;
        try {
            os = new FileOutputStream(file);
            final AnimGifEncoder age = new AnimGifEncoder(os);
            age.add(m.getImage());
            age.add(m.getImage());
            age.encode();
            os.close();
        } catch (final FileNotFoundException e) {
            e.printStackTrace();
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
}

class GifColorEntry {
    /** The actual RGB color for this entry */
    public int      m_color;

    /** The colortable [palette] entry number for this color */
    public int      m_index;

    public GifColorEntry(final int col, final int ix) {
        m_color = col;
        m_index = ix;
    }
};

class AnIma {
    /** This-image's interlace flag */
    public boolean  m_interlace;

    /** This-image's delay factor */
    public int      m_delay;

    /** This-image's source and destination within the completed image */
    public int      m_x, m_y;

    /** This image's width and height */
    public int      m_w, m_h;

    /** This-image's 8-bit pixelset. It indexes the m_color_ar table. */
    public byte[]   m_rgb;
};
