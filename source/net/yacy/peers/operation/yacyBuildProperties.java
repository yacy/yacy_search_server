package net.yacy.peers.operation;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;


public final class yacyBuildProperties {
    
    private static Properties props = new Properties();
    
    static {
        try {
            props.load(new FileInputStream("defaults/yacyBuild.properties"));
        } catch (IOException e) {
        	e.printStackTrace();
            props = null;
        }
    }

    public static String getSVNRevision() {
        if (props == null) return "0";
        final String revision = props.getProperty("SVNRevision");
        return revision.contains("@") || revision.contains("$") ? "0" : revision;
    }

    public static String getVersion() {
        if (props == null) return "0.1";
        final String version = props.getProperty("Version");
        return version.contains("@") ? "0.1" : version;
    }

    public static final Pattern versionMatcher = Pattern.compile("\\A(\\d+\\.\\d{1,3})(\\d{0,5})\\z"); 
    
    public static String getLongVersion() {
        return String.format(Locale.US, "%.3f%05d", Float.valueOf(getVersion()), Integer.valueOf(getSVNRevision()));
    }
}
