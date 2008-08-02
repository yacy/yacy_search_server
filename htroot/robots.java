

import java.io.File;
import java.util.ArrayList;

import de.anomic.http.httpHeader;
import de.anomic.http.httpdRobotsTxtConfig;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaSwitchboardConstants;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.servletProperties;

public class robots {
    
    public static servletProperties respond(final httpHeader header, final serverObjects post, final serverSwitch<?> env) {
        final servletProperties prop = new servletProperties();
        final httpdRobotsTxtConfig rbc = ((plasmaSwitchboard)env).robotstxtConfig;
        
        if (rbc.isAllDisallowed()) {
            prop.put(httpdRobotsTxtConfig.ALL, 1);
        } else {
            if (rbc.isBlogDisallowed()) prop.put(httpdRobotsTxtConfig.ALL + "_" + httpdRobotsTxtConfig.BLOG, "1");
            if (rbc.isBookmarksDisallowed()) prop.put(httpdRobotsTxtConfig.ALL + "_" + httpdRobotsTxtConfig.BOOKMARKS, "1");
            if (rbc.isFileshareDisallowed()) prop.put(httpdRobotsTxtConfig.ALL + "_" + httpdRobotsTxtConfig.FILESHARE, "1");
            if (rbc.isHomepageDisallowed()) prop.put(httpdRobotsTxtConfig.ALL + "_" + httpdRobotsTxtConfig.HOMEPAGE, "1");
            if (rbc.isNetworkDisallowed()) prop.put(httpdRobotsTxtConfig.ALL + "_" + httpdRobotsTxtConfig.NETWORK, "1");
            if (rbc.isNewsDisallowed()) prop.put(httpdRobotsTxtConfig.ALL + "_" + httpdRobotsTxtConfig.NEWS, "1");
            if (rbc.isStatusDisallowed()) prop.put(httpdRobotsTxtConfig.ALL + "_" + httpdRobotsTxtConfig.STATUS, "1");
            if (rbc.isSurftipsDisallowed()) prop.put(httpdRobotsTxtConfig.ALL + "_" + httpdRobotsTxtConfig.SURFTIPS, "1");
            if (rbc.isWikiDisallowed()) prop.put(httpdRobotsTxtConfig.ALL + "_" + httpdRobotsTxtConfig.WIKI, "1");
            if (rbc.isProfileDisallowed()) prop.put(httpdRobotsTxtConfig.ALL + "_" + httpdRobotsTxtConfig.PROFILE, "1");
            
            if (rbc.isLockedDisallowed() || rbc.isDirsDisallowed()) {
                final ArrayList<String>[] p = getFiles(env.getConfig(plasmaSwitchboardConstants.HTROOT_PATH, plasmaSwitchboardConstants.HTROOT_PATH_DEFAULT));
                if (rbc.isLockedDisallowed()) {
                    prop.put(httpdRobotsTxtConfig.ALL + "_" + httpdRobotsTxtConfig.LOCKED, p[0].size());
                    for (int i=0; i<p[0].size(); i++)
                        prop.put(httpdRobotsTxtConfig.ALL + "_" + httpdRobotsTxtConfig.LOCKED + "_" + i + "_page", p[0].get(i));
                }
                if (rbc.isDirsDisallowed()) {
                    prop.put(httpdRobotsTxtConfig.ALL + "_" + httpdRobotsTxtConfig.DIRS, p[1].size());
                    for (int i=0; i<p[1].size(); i++)
                        prop.put(httpdRobotsTxtConfig.ALL + "_" + httpdRobotsTxtConfig.DIRS + "_" + i + "_dir", p[1].get(i));
                }
            }
        }
        
        return prop;
    }
    
    @SuppressWarnings("unchecked")
    private static ArrayList<String>[] getFiles(final String htrootPath) {
        final File htroot = new File(htrootPath);
        if (!htroot.exists()) return null;
        final ArrayList<String> htrootFiles = new ArrayList<String>();
        final ArrayList<String> htrootDirs = new ArrayList<String>();
        final String[] htroots = htroot.list();
        File file;
        for (int i=0, dot; i<htroots.length; i++) {
            if (htroots[i].equals("www")) continue;
            file = new File(htroot, htroots[i]);
            if (file.isDirectory()) {
                htrootDirs.add(htroots[i]);
            } else if (
                    ((dot = htroots[i].lastIndexOf('.')) < 2 ||
                    htroots[i].charAt(dot - 2) == '_' && htroots[i].charAt(dot - 1) == 'p') &&
                    !(htroots[i].endsWith("java") || htroots[i].endsWith("class"))
            ) {
                htrootFiles.add(htroots[i]);
            }
        }
        return new ArrayList[] { htrootFiles, htrootDirs };
    }
}
