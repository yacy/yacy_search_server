// Html2ImageTest.java
// Copyright 2016,2017 by reger; https://github.com/reger24 luccioman; https://github.com/luccioman
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// LICENSE
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.cora.util;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import net.yacy.utils.translation.ExtensionsFileFilter;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.BeforeClass;


public class Html2ImageTest {

    @BeforeClass
    public static void setUpClass() throws Exception {
        // make sure DATA exists
        File f = new File("test/DATA");
        if (!f.exists()) f.mkdir();
    }

    /**
     * Test of pdf2image method, of class Html2Image.
     */
    @Test
    public void testPdf2image() {
        // collect pdf filenames in test directory
        File pd = new File("test/parsertest");
        List<String> extensions = new ArrayList<>();
        extensions.add("pdf");
        FilenameFilter fileFilter = new ExtensionsFileFilter(extensions);
        String[] pdffiles = pd.list(fileFilter);
        assertTrue("no pdf files in test/parsertest directory", pdffiles.length > 0);

        for (String pdffilename : pdffiles) {
            File pdffile = new File(pd, pdffilename);
            File jpgfile = new File("test/DATA", pdffilename + ".jpg");
            if (jpgfile.exists()) {
                jpgfile.delete();
            }
            assertTrue(Html2Image.pdf2image(pdffile, jpgfile, 1024, 1024, 300, 75));
            assertTrue(jpgfile.exists());
            assertTrue(jpgfile.length() > 0);
            System.out.println("Test image file successfully written : " + jpgfile.getAbsolutePath());
            
            final File pngFile = new File("test/DATA", pdffilename + ".png");
            if (pngFile.exists()) {
            	pngFile.delete();
            }
            assertTrue(Html2Image.pdf2image(pdffile, pngFile, 1024, 1024, 300, 75));
            assertTrue(pngFile.exists());
            assertTrue(pngFile.length() > 0);
            System.out.println("Test image file successfully written : " + pngFile.getAbsolutePath());
        }
    }

}
