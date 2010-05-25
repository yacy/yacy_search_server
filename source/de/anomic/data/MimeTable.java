package de.anomic.data;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import net.yacy.cora.document.MultiProtocolURI;

public class MimeTable {

    private static final Properties mimeTable = new Properties();
    
    public static void init(final File mimeFile) {
        if (mimeTable.isEmpty()) {
            // load the mime table
            BufferedInputStream mimeTableInputStream = null;
            try {
                mimeTableInputStream = new BufferedInputStream(new FileInputStream(mimeFile));
                mimeTable.load(mimeTableInputStream);
            } catch (final Exception e) {                
                e.printStackTrace();
            } finally {
                if (mimeTableInputStream != null) try { mimeTableInputStream.close(); } catch (final Exception e1) {}                
            }
        }
    }
    
    public static int size() {
        return mimeTable.size();
    }
    
    public static boolean isEmpty() {
        return mimeTable.isEmpty();
    }
    
    public static String ext2mime(final String ext) {
        return mimeTable.getProperty(ext, "application/" + ext);
    }
    
    public static String ext2mime(final String ext, final String dfltMime) {
        return mimeTable.getProperty(ext, dfltMime);
    }
    
    public static String url2mime(final MultiProtocolURI url, final String dfltMime) {
        return ext2mime(url.getFileExtension(), dfltMime);
    }
    
    public static String url2mime(final MultiProtocolURI url) {
        return ext2mime(url.getFileExtension());
    }
}
