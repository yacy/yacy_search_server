package de.anomic.yacy;
/**
 * Properties set when compiling this release/version
 */
public class yacyBuildProperties {
	private yacyBuildProperties() {
	}

	public static String getSVNVersion() {
		return "@REPL_REVISION_NR@";
	}

	public static String getVersion() {
		return "@VERSION@";
	}

	/**
	 * determines, if this release was compiled and installed
	 * by a package manager
	 */
	public static boolean isPkgManager() {
		return "@REPL_PKGMANAGER@".equals("true");
	}
}
