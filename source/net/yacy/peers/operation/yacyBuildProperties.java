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
        return props == null ? "0.1" : props.getProperty("Version", "0.1");
    }

    public static String getRepositoryVersionDate() {
        return props == null ? "20220101" : props.getProperty("RepositoryVersionDate", "20220101");
    }

    public static String getRepositoryVersionTime() {
        return props == null ? "0000" : props.getProperty("RepositoryVersionTime", "0000");
    }

    public static String getRepositoryVersionHash() {
        return props == null ? "0" : props.getProperty("RepositoryVersionHash", "0");
    }

    public static String getReleaseStub() {
        return props == null ? "yacy_v0.1_202201010000_000000000" : props.getProperty("ReleaseStub", "yacy_v0.1_202201010000_000000000");
    }

    public static String getDstamp() {
        return props == null ? "20220101" : props.getProperty("dstamp", "20220101");
    }

    public static String getTstamp() {
        return props == null ? "0000" : props.getProperty("tstamp", "0000");
    }

    public static final Pattern versionMatcher = Pattern.compile("\\A(\\d+\\.\\d{1,3})(\\d{0,5})\\z");

    public static final Pattern releaseStubVersionMatcher = Pattern.compile("yacy_v(\\d+.\\d{1,3})_(\\d{12})_([0-9a-f]{9})");
}
