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
        List<String> extensions = new ArrayList();
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
            Html2Image.pdf2image(pdffile, jpgfile, 1024, 1024, 300, 75);
            assertTrue(jpgfile.exists());
        }
    }

}
