package net.yacy.peers.operation;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.regex.Pattern;


public final class yacyBuildProperties {

    private static Properties props = new Properties();

    static {
        try {
            props.load(new FileInputStream("defaults/yacyBuild.properties"));
        } catch (final IOException e) {
            e.printStackTrace();
            props = null;
        }
    }

    public static String getVersion() {
        return props.getProperty("Version", "0.1");
    }

    public static String getRepositoryVersionDate() {
        return props.getProperty("RepositoryVersionDate", "20220101");
    }

    public static String getRepositoryVersionTime() {
        return props.getProperty("RepositoryVersionTime", "0000");
    }

    public static String getRepositoryVersionHash() {
        return props.getProperty("RepositoryVersionHash", "0");
    }

    public static String getReleaseStub() {
        return props.getProperty("ReleaseStub", "yacy_v0.1_202201010000_000000000");
    }

    public static String getDstamp() {
        return props.getProperty("dstamp", "20220101");
    }

    public static String getTstamp() {
        return props.getProperty("tstamp", "0000");
    }

    public static final Pattern versionMatcher = Pattern.compile("\\A(\\d+\\.\\d{1,3})(\\d{0,5})\\z");

}
