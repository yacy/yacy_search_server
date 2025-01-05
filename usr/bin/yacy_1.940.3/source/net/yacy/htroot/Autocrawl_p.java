package net.yacy.htroot;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class Autocrawl_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        boolean autocrawlEnable = sb.getConfigBool(SwitchboardConstants.AUTOCRAWL, false);
        int autocrawlRatio = Integer.parseInt(sb.getConfig(SwitchboardConstants.AUTOCRAWL_RATIO, "50"));
        int autocrawlRows = Integer.parseInt(sb.getConfig(SwitchboardConstants.AUTOCRAWL_ROWS, "100"));
        int autocrawlDays = Integer.parseInt(sb.getConfig(SwitchboardConstants.AUTOCRAWL_DAYS, "1"));
        String autocrawlQuery = sb.getConfig(SwitchboardConstants.AUTOCRAWL_QUERY, "*:*");
        int autocrawlShallow = Integer.parseInt(sb.getConfig(SwitchboardConstants.AUTOCRAWL_SHALLOW_DEPTH, "1"));
        int autocrawlDeep = Integer.parseInt(sb.getConfig(SwitchboardConstants.AUTOCRAWL_DEEP_DEPTH, "3"));
        boolean autocrawlText = sb.getConfigBool(SwitchboardConstants.AUTOCRAWL_INDEX_TEXT, true);
        boolean autocrawlMedia = sb.getConfigBool(SwitchboardConstants.AUTOCRAWL_INDEX_MEDIA, true);

        if (post != null) {
            autocrawlEnable = post.getBoolean("autocrawlEnable");
            if (post.containsKey("autocrawlRatio")) {
                autocrawlRatio = post.getInt("autocrawlRatio", 50);
            }
            if (post.containsKey("autocrawlRows")) {
                autocrawlRows = post.getInt("autocralwRows", 100);
            }
            if (post.containsKey("autocrawlDays")) {
                autocrawlDays = post.getInt("autocrawlDays", 1);
            }
            if (post.containsKey("autocrawlQuery")) {
                autocrawlQuery = post.get("autocrawlQuery", "*:*");
            }
            if (post.containsKey("autocrawlShallow")){
                autocrawlShallow = post.getInt("autocrawlShallow", 1);
            }
            if (post.containsKey("autocrawlDeep")) {
                autocrawlDeep = post.getInt("autocrawlDeep", 3);
            }
            autocrawlText = post.getBoolean("autocrawlText");
            autocrawlMedia = post.getBoolean("autocrawlMedia");
        }

        if (autocrawlRatio > 500) {
            autocrawlRatio = 500;
        } else if (autocrawlRatio < 1) {
            autocrawlRatio = 1;
        }
        if (autocrawlRows > 500) {
            autocrawlRows = 500;
        } else if (autocrawlRows < 1) {
            autocrawlRows = 1;
        }
        if (autocrawlDays > 60) {
            autocrawlDays = 60;
        } else if (autocrawlDays < 1) {
            autocrawlDays = 1;
        }
        if (autocrawlShallow > 1) {
            autocrawlShallow = 2;
        } else if (autocrawlShallow < 0) {
            autocrawlShallow = 0;
        }
        if (autocrawlDeep > 5) {
            autocrawlDeep = 5;
        } else if (autocrawlDeep < 1) {
            autocrawlDeep = 1;
        }

        if (post != null) {
            sb.setConfig(SwitchboardConstants.AUTOCRAWL, autocrawlEnable);
            sb.setConfig(SwitchboardConstants.AUTOCRAWL_RATIO, autocrawlRatio);
            sb.setConfig(SwitchboardConstants.AUTOCRAWL_ROWS, autocrawlRows);
            sb.setConfig(SwitchboardConstants.AUTOCRAWL_DAYS, autocrawlDays);
            sb.setConfig(SwitchboardConstants.AUTOCRAWL_QUERY, autocrawlQuery);
            sb.setConfig(SwitchboardConstants.AUTOCRAWL_SHALLOW_DEPTH, autocrawlShallow);
            sb.setConfig(SwitchboardConstants.AUTOCRAWL_DEEP_DEPTH, autocrawlDeep);
            sb.setConfig(SwitchboardConstants.AUTOCRAWL_INDEX_TEXT, autocrawlText);
            sb.setConfig(SwitchboardConstants.AUTOCRAWL_INDEX_MEDIA, autocrawlMedia);

            sb.initAutocrawl(autocrawlEnable);

            prop.put("changed", true);
        }

        prop.put("autocrawlEnable", autocrawlEnable);
        prop.put("autocrawlRatio", autocrawlRatio);
        prop.put("autocrawlRows", autocrawlRows);
        prop.put("autocrawlDays", autocrawlDays);
        prop.put("autocrawlQuery", autocrawlQuery);
        prop.put("autocrawlShallow", autocrawlShallow);
        prop.put("autocrawlDeep", autocrawlDeep);
        prop.put("autocrawlText", autocrawlText);
        prop.put("autocrawlMedia", autocrawlMedia);

        return prop;
    }
}
