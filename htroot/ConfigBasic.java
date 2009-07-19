// ConfigBasic_p.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
// Created 28.02.2006
//
// $LastChangedDate: 2005-09-13 00:20:37 +0200 (Di, 13 Sep 2005) $
// $LastChangedRevision: 715 $
// $LastChangedBy: borg-0300 $
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

// You must compile this file with
// javac -classpath .:../classes ConfigBasic_p.java
// if the shell's current path is HTROOT

import java.io.File;
import java.util.regex.Pattern;

import de.anomic.data.translator;
import de.anomic.http.metadata.HeaderFramework;
import de.anomic.http.metadata.RequestHeader;
import de.anomic.http.server.HTTPDemon;
import de.anomic.http.server.HTTPDFileHandler;
import de.anomic.net.UPnP;
import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;
import de.anomic.server.serverCore;
import de.anomic.server.serverDomains;
import de.anomic.server.serverInstantBusyThread;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacySeed;

public class ConfigBasic {
    
    private static final int NEXTSTEP_FINISHED  = 0;
    private static final int NEXTSTEP_PWD       = 1;
    private static final int NEXTSTEP_PEERNAME  = 2;
    private static final int NEXTSTEP_PEERPORT  = 3;
    private static final int NEXTSTEP_RECONNECT = 4;
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        final String langPath = env.getConfigPath("locale.work", "DATA/LOCALE/locales").getAbsolutePath();
        String lang = env.getConfig("locale.language", "default");
        
        final int authentication = sb.adminAuthenticated(header);
        if (authentication < 2) {
            // must authenticate
            prop.put("AUTHENTICATE", "admin log-in"); 
            return prop;
        }
        
        // starting a peer ping
        
        //boolean doPeerPing = false;
        if ((sb.peers.mySeed().isVirgin()) || (sb.peers.mySeed().isJunior())) {
            serverInstantBusyThread.oneTimeJob(sb.yc, "peerPing", null, 0);
            //doPeerPing = true;
        }
        
        // language settings
        if ((post != null) && (!(post.get("language", "default").equals(lang)))) {
            translator.changeLang(env, langPath, post.get("language", "default") + ".lng");
        }
        
        // peer name settings
        final String peerName = (post == null) ? env.getConfig("peerName","") : post.get("peername", "");
        
        // port settings
        long port = env.getConfigLong("port", 8080); //this allows a low port, but it will only get one, if the user edits the config himself.
		if (post != null && Integer.parseInt(post.get("port")) > 1023) {
			port = post.getLong("port", 8080);
		}

        // check if peer name already exists
        final yacySeed oldSeed = sb.peers.lookupByName(peerName);
        if ((oldSeed == null) && (!(env.getConfig("peerName", "").equals(peerName)))) {
            // the name is new
        	final boolean nameOK = Pattern.compile("[A-Za-z0-9\\-_]{3,80}").matcher(peerName).matches();
            if (nameOK) env.setConfig("peerName", peerName);
        }
        
        // UPnP config
        boolean upnp = false;
        if(post != null && post.containsKey("port")) { // hack to allow checkbox
        	if (post.containsKey("enableUpnp")) upnp = true;
        	if (upnp && !sb.getConfigBool(SwitchboardConstants.UPNP_ENABLED, false)) UPnP.addPortMapping();
        	sb.setConfig(SwitchboardConstants.UPNP_ENABLED, upnp);
        	if(!upnp) UPnP.deletePortMapping();
        }
        
        // check port
        boolean reconnect = false;
        if (!(env.getConfigLong("port", port) == port)) {
            // validate port
            final serverCore theServerCore = (serverCore) env.getThread("10_httpd");
            env.setConfig("port", port);
            
            // redirect the browser to the new port
            reconnect = true;
            
            // renew upnp port mapping
            if (upnp) UPnP.addPortMapping();
            
            String host = null;
            if (header.containsKey(HeaderFramework.HOST)) {
                host = header.get(HeaderFramework.HOST);
                final int idx = host.indexOf(":");
                if (idx != -1) host = host.substring(0,idx);
            } else {
                host = serverDomains.myPublicLocalIP().getHostAddress();
            }
            
            prop.put("reconnect", "1");
            prop.put("reconnect_host", host);
            prop.put("nextStep_host", host);
            prop.put("reconnect_port", port);
            prop.put("nextStep_port", port);
            prop.put("reconnect_sslSupport", theServerCore.withSSL() ? "1" : "0");
            prop.put("nextStep_sslSupport", theServerCore.withSSL() ? "1" : "0");
            
            // generate new shortcut (used for Windows)
            //yacyAccessible.setNewPortBat(Integer.parseInt(port));
            //yacyAccessible.setNewPortLink(Integer.parseInt(port));
            
            // force reconnection in 7 seconds
            theServerCore.reconnect(7000);
        } else {
            prop.put("reconnect", "0");
        }

        // set a use case
        String networkName = sb.getConfig(SwitchboardConstants.NETWORK_NAME, "");
        if (post != null && post.containsKey("usecase")) {
            if (post.get("usecase", "").equals("freeworld") && !networkName.equals("freeworld")) {
                // switch to freeworld network
                sb.switchNetwork("defaults/yacy.network.freeworld.unit");
                // switch to p2p mode
                sb.setConfig(SwitchboardConstants.INDEX_DIST_ALLOW, true);
                sb.setConfig(SwitchboardConstants.INDEX_RECEIVE_ALLOW, true);
            }
            if (post.get("usecase", "").equals("portal") && !networkName.equals("webportal")) {
                // switch to webportal network
                sb.switchNetwork("defaults/yacy.network.webportal.unit");
                // switch to robinson mode
                sb.setConfig(SwitchboardConstants.INDEX_DIST_ALLOW, false);
                sb.setConfig(SwitchboardConstants.INDEX_RECEIVE_ALLOW, false);
            }
            if (post.get("usecase", "").equals("intranet") && !networkName.equals("intranet")) {
                // switch to intranet network
                sb.switchNetwork("defaults/yacy.network.intranet.unit");
                // switch to p2p mode: enable ad-hoc networks between intranet users
                sb.setConfig(SwitchboardConstants.INDEX_DIST_ALLOW, false);
                sb.setConfig(SwitchboardConstants.INDEX_RECEIVE_ALLOW, false);
            }
            if (post.get("usecase", "").equals("intranet")) {
                String repositoryPath = post.get("repositoryPath", "/DATA/HTROOT/repository");
                File repository = (repositoryPath.startsWith("/") || repositoryPath.charAt(1) == ':') ? new File(repositoryPath) : new File(sb.getRootPath(), repositoryPath);
                if (repository.exists() && repository.isDirectory()) {
                	sb.setConfig("repositoryPath", repositoryPath);
                }
            }
        }
        
        networkName = sb.getConfig(SwitchboardConstants.NETWORK_NAME, "");
        if (networkName.equals("freeworld")) {
            prop.put("setUseCase", 1);
            prop.put("setUseCase_freeworldChecked", 1);
        } else if (networkName.equals("webportal")) {
            prop.put("setUseCase", 1);
            prop.put("setUseCase_portalChecked", 1);
        } else if (networkName.equals("intranet")) {
            prop.put("setUseCase", 1);
            prop.put("setUseCase_intranetChecked", 1);
        } else {
            prop.put("setUseCase", 0);
        }
        prop.put("setUseCase_port", port);
        prop.put("setUseCase_repositoryPath", sb.getConfig("repositoryPath", "/DATA/HTROOT/repository"));
        
        // check if values are proper
        final boolean properPassword = (sb.getConfig(HTTPDemon.ADMIN_ACCOUNT_B64MD5, "").length() > 0) || sb.getConfigBool("adminAccountForLocalhost", false);
        final boolean properName = (env.getConfig("peerName","").length() >= 3) && (!(yacySeed.isDefaultPeerName(env.getConfig("peerName",""))));
        final boolean properPort = (sb.peers.mySeed().isSenior()) || (sb.peers.mySeed().isPrincipal());
        
        if ((env.getConfig("defaultFiles", "").startsWith("ConfigBasic.html,"))) {
        	env.setConfig("defaultFiles", env.getConfig("defaultFiles", "").substring(17));
        	env.setConfig("browserPopUpPage", "Status.html");
        	HTTPDFileHandler.initDefaultPath();
        }
        
        prop.put("statusName", properName ? "1" : "0");
        prop.put("statusPort", properPort ? "1" : "0");
        if (reconnect) {
            prop.put("nextStep", NEXTSTEP_RECONNECT);
        } else if (!properName) {
            prop.put("nextStep", NEXTSTEP_PEERNAME);
        } else if (!properPassword) {
            prop.put("nextStep", NEXTSTEP_PWD);
        } else if (!properPort) {
            prop.put("nextStep", NEXTSTEP_PEERPORT);
        } else {
            prop.put("nextStep", NEXTSTEP_FINISHED);
        }
                
        final boolean upnp_enabled = env.getConfigBool(SwitchboardConstants.UPNP_ENABLED, false);
        prop.put("upnp", "1");
        prop.put("upnp_enabled", upnp_enabled ? "1" : "0");
        if (upnp_enabled) prop.put("upnp_success", (UPnP.getMappedPort() > 0) ? "2" : "1");
        else prop.put("upnp_success", "0");
        
        // set default values       
        prop.putHTML("defaultName", env.getConfig("peerName", ""));
        prop.putHTML("defaultPort", env.getConfig("port", "8080"));
        lang = env.getConfig("locale.language", "default"); // re-assign lang, may have changed
        if (lang.equals("default")) {
            prop.put("langDeutsch", "0");
            prop.put("langEnglish", "1");
        } else if (lang.equals("de")) {
            prop.put("langDeutsch", "1");
            prop.put("langEnglish", "0");
        } else {
            prop.put("langDeutsch", "0");
            prop.put("langEnglish", "0");
        }
        return prop;
    }
}
