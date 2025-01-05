/**
 *  ClassificationTest
 *  part of YaCy
 *  Copyright 2017 by reger24; https://github.com/reger24
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */
package net.yacy.cora.document.analysis;

import java.io.File;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for Classification class.
 */
public class ClassificationTest {

    /**
     * Initialize with default file ext to mime file
     */
    @BeforeClass
    public static void setUpClass() {
        Classification.init(new File("defaults/httpd.mime"));
    }

    /**
     * Test of ext2mime method, of class Classification.
     */
    @Test
    public void testExt2mime_String() {
        assertEquals("application/x-compress", Classification.ext2mime("Z"));
        assertEquals("application/x-compress", Classification.ext2mime("z"));
        
        assertEquals("image/tiff", Classification.ext2mime("TIFF"));
        assertEquals("image/tiff", Classification.ext2mime("tiff"));
        
        assertEquals("image/tiff", Classification.ext2mime("TIFF", "image/tiff"));
        assertEquals("image/tiff", Classification.ext2mime("tiff", "image/tiff"));
    }
    
	/**
	 * Test of isNNNExtension methods with lower and upper case samples, containing
	 * notably the 'i' character which case conversion is different whith the Turkish
	 * locale. THis test be successful with any default system locale.
	 */
    @Test
    public void testIsExtension() {
        assertTrue(Classification.isApplicationExtension("ISO"));
        assertTrue(Classification.isApplicationExtension("iso"));
        
        assertTrue(Classification.isAudioExtension("AIF"));
        assertTrue(Classification.isAudioExtension("aif"));
        
        assertTrue(Classification.isVideoExtension("AVI"));
        assertTrue(Classification.isVideoExtension("avi"));
        
        assertTrue(Classification.isImageExtension("GIF"));
        assertTrue(Classification.isImageExtension("gif"));
        
        assertTrue(Classification.isControlExtension("SHA1"));
        assertTrue(Classification.isControlExtension("sha1"));
        
        assertTrue(Classification.isMediaExtension("GIF"));
        assertTrue(Classification.isMediaExtension("gif"));
        
        assertTrue(Classification.isAnyKnownExtension("GIF"));
        assertTrue(Classification.isAnyKnownExtension("gif"));
    }
    
    /**
     * Test of isPictureMime method with some sample media types.
     */
    @Test
    public void testIsPictureMime() {
        assertTrue(Classification.isPictureMime("image/jpeg"));
        assertTrue(Classification.isPictureMime("IMAGE/JPEG"));
        
        assertFalse(Classification.isPictureMime("text/html"));
        assertFalse(Classification.isPictureMime("TEXT/HTML"));
    }

}
