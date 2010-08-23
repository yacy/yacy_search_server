//
//$LastChangedDate$
//$LastChangedRevision$
//$LastChangedBy$
//

import java.io.File;
import java.util.ArrayList;

import net.yacy.cora.protocol.RequestHeader;

import de.anomic.http.server.RobotsTxtConfig;
import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.servletProperties;

public class robots {
    
    public static servletProperties respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final servletProperties prop = new servletProperties();
        final RobotsTxtConfig rbc = ((Switchboard)env).robotstxtConfig;
        
        if (rbc.isAllDisallowed()) {
            prop.put(RobotsTxtConfig.ALL, 1);
        } else {
            if (rbc.isBlogDisallowed()) prop.put(RobotsTxtConfig.ALL + "_" + RobotsTxtConfig.BLOG, "1");
            if (rbc.isBookmarksDisallowed()) prop.put(RobotsTxtConfig.ALL + "_" + RobotsTxtConfig.BOOKMARKS, "1");
            if (rbc.isFileshareDisallowed()) prop.put(RobotsTxtConfig.ALL + "_" + RobotsTxtConfig.FILESHARE, "1");
            if (rbc.isHomepageDisallowed()) prop.put(RobotsTxtConfig.ALL + "_" + RobotsTxtConfig.HOMEPAGE, "1");
            if (rbc.isNetworkDisallowed()) prop.put(RobotsTxtConfig.ALL + "_" + RobotsTxtConfig.NETWORK, "1");
            if (rbc.isNewsDisallowed()) prop.put(RobotsTxtConfig.ALL + "_" + RobotsTxtConfig.NEWS, "1");
            if (rbc.isStatusDisallowed()) prop.put(RobotsTxtConfig.ALL + "_" + RobotsTxtConfig.STATUS, "1");
            if (rbc.isSurftipsDisallowed()) prop.put(RobotsTxtConfig.ALL + "_" + RobotsTxtConfig.SURFTIPS, "1");
            if (rbc.isWikiDisallowed()) prop.put(RobotsTxtConfig.ALL + "_" + RobotsTxtConfig.WIKI, "1");
            if (rbc.isProfileDisallowed()) prop.put(RobotsTxtConfig.ALL + "_" + RobotsTxtConfig.PROFILE, "1");
            
            if (rbc.isLockedDisallowed() || rbc.isDirsDisallowed()) {
                final ArrayList<String>[] p = getFiles(env.getConfig(SwitchboardConstants.HTROOT_PATH, SwitchboardConstants.HTROOT_PATH_DEFAULT));
                if (rbc.isLockedDisallowed()) {
                    prop.put(RobotsTxtConfig.ALL + "_" + RobotsTxtConfig.LOCKED, p[0].size());
                    for (int i=0; i<p[0].size(); i++)
                        prop.put(RobotsTxtConfig.ALL + "_" + RobotsTxtConfig.LOCKED + "_" + i + "_page", p[0].get(i));
                }
                if (rbc.isDirsDisallowed()) {
                    prop.put(RobotsTxtConfig.ALL + "_" + RobotsTxtConfig.DIRS, p[1].size());
                    for (int i=0; i<p[1].size(); i++)
                        prop.put(RobotsTxtConfig.ALL + "_" + RobotsTxtConfig.DIRS + "_" + i + "_dir", p[1].get(i));
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
