package de.anomic.yacy;

import java.util.Locale;
import de.anomic.yacy.yacyBuildProperties;

/**
 * Properties set when compiling this release/version
 */
public final class yacyBuildProperties {
	private yacyBuildProperties() {
	}

	/**
	 * returns the SVN-Revision Number as a String
	 */
	public static String getSVNRevision() {
		final String revision = "@REPL_REVISION_NR@";
		if (revision.contains("@") || revision.contains("$")) {
			return "0";
		}
		return revision;
	}

	/**
	 * returns the version String (e. g. 0.9)
	 */
	public static String getVersion() {
		if ("@REPL_VERSION@".contains("@") ) {
			return "0.1";
		}
		return "@REPL_VERSION@";
	}

	/**
	 * returns the long version String (e. g. 0.9106712)
	 */
	public static String getLongVersion() {
		return String.format(Locale.US, "%.3f%05d", Float.valueOf(getVersion()), Integer.valueOf(getSVNRevision()));
	}

	/**
	 * returns the date, when this release was build
	 */
	public static String getBuildDate() {
		if ("@REPL_DATE@".contains("@")) {
			return "19700101";
		}
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
		if ("@REPL_RESTARTCMD@".contains("@")) {
			return "echo 'error'";
		}
		return "@REPL_RESTARTCMD@";
	}
}
