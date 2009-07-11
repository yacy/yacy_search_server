package de.anomic.yacy;

import java.util.Locale;
import de.anomic.yacy.yacyBuildProperties;

/**
 * Properties set when compiling this release/version
 */
public class yacyBuildProperties {
	private yacyBuildProperties() {
	}

	/**
	 * returns the SVN-Revision Number as a String
	 */
	public static String getSVNRevision() {
		return "@REPL_REVISION_NR@";
	}

	/**
	 * returns the version String (e. g. 0.9)
	 */
	public static String getVersion() {
		return "@REPL_VERSION@";
	}

	/**
	 * returns the long version String (e. g. 0.9106712)
	 */
	public static String getLongVersion() {
		return String.format(Locale.US, "%.3f%05d", Double.valueOf(getVersion()), Integer.valueOf(getSVNRevision()));
	}

	/**
	 * returns the date, when this release was build
	 */
	public static String getBuildDate() {
		return "@REPL_DATE@";
	}

	/**
	 * determines, if this release was compiled and installed
	 * by a package manager
	 */
	public static boolean isPkgManager() {
		return "@REPL_PKGMANAGER@".equals("true");
	}

	/**
	 * returns command to use to restart the YaCy daemon,
	 * when YaCy was installed with a packagemanger
	 */
	public static String getRestartCmd() {
		return "@REPL_RESTARTCMD@";
	}
}
