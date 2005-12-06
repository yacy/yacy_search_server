package de.anomic.yacy;

import java.util.HashMap;

import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverAbstractSwitch;
import de.anomic.server.serverCodings;

public final class yacyVersion {    
    public static final float YACY_SUPPORTS_PORT_FORWARDING = (float) 0.383;
    public static final float YACY_SUPPORTS_GZIP_POST_REQUESTS = (float) 0.40300772;
    public static final float YACY_ACCEPTS_RANKING_TRANSMISSION = (float) 0.414;
    
    public static void migrate(plasmaSwitchboard sb){
    		//set preset accounts/passwords
        String acc;
        if ((acc = sb.getConfig("serverAccount", "")).length() > 0) {
            sb.setConfig("serverAccountBase64MD5", de.anomic.server.serverCodings.encodeMD5Hex(serverCodings.standardCoder.encodeBase64String(acc)));
            sb.setConfig("serverAccount", "");
        }
        if ((acc = sb.getConfig("adminAccount", "")).length() > 0) {
            sb.setConfig("adminAccountBase64MD5", de.anomic.server.serverCodings.encodeMD5Hex(serverCodings.standardCoder.encodeBase64String(acc)));
            sb.setConfig("adminAccount", "");
        }

        // fix unsafe old passwords
        if ((acc = sb.getConfig("proxyAccountBase64", "")).length() > 0) {
            sb.setConfig("proxyAccountBase64MD5", de.anomic.server.serverCodings.encodeMD5Hex(acc));
            sb.setConfig("proxyAccountBase64", "");
        }
        if ((acc = sb.getConfig("serverAccountBase64", "")).length() > 0) {
            sb.setConfig("serverAccountBase64MD5", de.anomic.server.serverCodings.encodeMD5Hex(acc));
            sb.setConfig("serverAccountBase64", "");
        }
        if ((acc = sb.getConfig("adminAccountBase64", "")).length() > 0) {
            sb.setConfig("adminAccountBase64MD5", de.anomic.server.serverCodings.encodeMD5Hex(acc));
            sb.setConfig("adminAccountBase64", "");
        }
        if ((acc = sb.getConfig("uploadAccountBase64", "")).length() > 0) {
            sb.setConfig("uploadAccountBase64MD5", de.anomic.server.serverCodings.encodeMD5Hex(acc));
            sb.setConfig("uploadAccountBase64", "");
        }
        if ((acc = sb.getConfig("downloadAccountBase64", "")).length() > 0) {
            sb.setConfig("downloadAccountBase64MD5", de.anomic.server.serverCodings.encodeMD5Hex(acc));
            sb.setConfig("downloadAccountBase64", "");
        }
    }
    
    public static HashMap migrateSwitchboardConfigSettings(serverAbstractSwitch sb, HashMap removedSettings) {
        if ((removedSettings == null)||(removedSettings.size() == 0)) return null;
        HashMap migratedSettings = new HashMap();        
        
        if (removedSettings.containsKey("parseableMimeTypes")) {
            String value = (String) removedSettings.get("parseableMimeTypes");
            migratedSettings.put("parseableMimeTypes.CRAWLER", value);
            migratedSettings.put("parseableMimeTypes.PROXY", value);
            migratedSettings.put("parseableMimeTypes.URLREDIRECTOR", value);
            migratedSettings.put("parseableMimeTypes.ICAP", value);
        }
        
        return migratedSettings;
        
    }
}
