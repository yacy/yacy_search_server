// ConfigBasic.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
// Created 28.02.2006
//
// $LastChangedDate: 2011-11-25 12:23:52 +0100 (Fr, 25 Nov 2011) $
// $LastChangedRevision: 8101 $
// $LastChangedBy: orbiter $
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

package net.yacy.htroot;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.TransactionManager;
import net.yacy.data.Translator;
import net.yacy.data.WorkTables;
import net.yacy.http.YaCyHttpServer;
import net.yacy.peers.OnePeerPingBusyThread;
import net.yacy.peers.Seed;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.server.http.HTTPDFileHandler;
import net.yacy.utils.translation.TranslatorXliff;
import net.yacy.utils.upnp.UPnP;
import net.yacy.utils.upnp.UPnPMappingType;

public class ConfigBasic {

    private static final int NEXTSTEP_FINISHED  = 0;
    private static final int NEXTSTEP_PWD       = 1;
    private static final int NEXTSTEP_PEERNAME  = 2;
    private static final int NEXTSTEP_PEERPORT  = 3;
    private static final int NEXTSTEP_RECONNECT = 4;

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) throws FileNotFoundException, IOException {

        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        final File langPath = new File(sb.getAppPath("locale.source", "locales").getAbsolutePath());
        String lang = env.getConfig("locale.language", "browser");

        /* For authenticated users only : acquire a transaction token for the next POST form submission */
        try {
            prop.put(TransactionManager.TRANSACTION_TOKEN_PARAM, TransactionManager.getTransactionToken(header));
        } catch (IllegalArgumentException e) {
            // In case that the user is not authenticated, the transaction manager throws an exception.
            // This is not an error and to be considered normal operation in case that the user is actually
            // not authenticated. In such a case we simply do not include the transaction token.
            sb.log.fine("access by unauthorized or unknown user: no transaction token delivered");
        }

        if ((sb.peers.mySeed().isVirgin()) || (sb.peers.mySeed().isJunior())) {
            new OnePeerPingBusyThread(sb.yc).start();
        }

        String peerName = sb.peers.mySeed().getName();
        long port = env.getLocalPort(); //this allows a low port, but it will only get one, if the user edits the config himself.
        boolean ssl = env.getConfigBool("server.https", false);
        boolean upnp = false;
        if (post != null) {

            final int authentication = sb.adminAuthenticated(header);
            if (authentication < 2) {
                // must authenticate
                prop.authenticationRequired();
                return prop;
            }

            /* Settings will be modified : check this is a valid transaction using HTTP POST method */
            TransactionManager.checkPostTransaction(header, post);

            // store this call as api call
            if(post.containsKey("set")) {
                sb.tables.recordAPICall(post, "ConfigBasic.html", WorkTables.TABLE_API_TYPE_CONFIGURATION, "basic settings");
            }

            // language settings
            if(post.containsKey("language")  && !lang.equals(post.get("language", "default"))) {
                if(new TranslatorXliff().changeLang(env, langPath, post.get("language", "default") + ".lng")) {
                    prop.put("changedLanguage", "1");
                }
            }

            // port settings
            if(post.getInt("port", 0) > 1023) {
                port = post.getLong("port", 8090);
                ssl = post.getBoolean("withssl");
            }

            // peer name settings
            peerName = post.get("peername", "");

            // UPnP config
            if(post.containsKey("port")) { // hack to allow checkbox
                upnp = post.containsKey("enableUpnp");
                if (upnp && !sb.getConfigBool(SwitchboardConstants.UPNP_ENABLED, false)) {
                    UPnP.addPortMappings();
                }
                sb.setConfig(SwitchboardConstants.UPNP_ENABLED, upnp);
                if (!upnp) {
                    UPnP.deletePortMappings();
                }
            }
        }

        if (ssl) {
            prop.put("withsslenabled_sslport", env.getHttpServer().getSslPort());
        }

        if (peerName != null && peerName.length() > 0) {
            peerName = peerName.replace(' ', '-');
        }

        // check if peer name already exists
        final Seed oldSeed = sb.peers.lookupByName(peerName);
        if (oldSeed == null &&
            !peerName.equals(sb.peers.mySeed().getName()) &&
            Pattern.compile("[A-Za-z0-9\\-_]{3,80}").matcher(peerName).matches()) {
            sb.peers.setMyName(peerName);
            sb.peers.saveMySeed();
        }

        // check port and ssl connection
        final boolean reconnect;
        if (!(env.getLocalPort() == port) || env.getConfigBool("server.https", false) != ssl) {
            // validate port
            final YaCyHttpServer theServerCore =  env.getHttpServer();
            env.setConfig(SwitchboardConstants.SERVER_PORT, port);
            env.setConfig("server.https", ssl);

            // redirect the browser to the new port
            reconnect = true;

            // renew upnp port mapping
            if (upnp) {
                UPnP.addPortMappings();
            }

            String host = header.getServerName();
            if (host == null) {
                host = Domains.myPublicLocalIP().getHostAddress();
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

            // force reconnection in 2 seconds
            theServerCore.reconnect(2000);
        } else {
            reconnect = false;
            prop.put("reconnect", "0");
        }

        // set a warning in case that the default password was not changed
        String currpw = sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "");
        String dfltpw = SwitchboardConstants.ADMIN_ACCOUNT_B64MD5_DEFAULT;
        prop.put("changedfltpw", currpw.equals(dfltpw) ? "1" : "0");

        // set a use case
        prop.put("setUseCase_switchError", 0);
        prop.put("setUseCase_switchWarning", 0);
        String networkName = sb.getConfig(SwitchboardConstants.NETWORK_NAME, "");
        if (post != null && post.containsKey("usecase")) {

            final int authentication = sb.adminAuthenticated(header);
            if (authentication < 2) {
                // must authenticate
                prop.authenticationRequired();
                return prop;
            }

            /* Settings will be modified : check this is a valid transaction using HTTP POST method */
            TransactionManager.checkPostTransaction(header, post);

            final boolean hasNonEmptyRemoteSolr = sb.index.fulltext().connectedRemoteSolr()
                    && (sb.index.fulltext().collectionSize() > 0 || sb.index.fulltext().webgraphSize() > 0);
            if ("freeworld".equals(post.get("usecase", "")) && !"freeworld".equals(networkName)) {
                if("intranet".equals(networkName) && hasNonEmptyRemoteSolr) {
                    /* One or more non empty remote Solr(s) attached : disallow switching from intranet to another network to prevent disclosure of indexed private documents */
                    prop.put("setUseCase_switchError", 1);
                } else {
                    if(hasNonEmptyRemoteSolr) {
                        /* One or more non empty remote Solr(s) attached : warn the user even if not coming from intranet as indexed documents may be irrelevant for the new mode */
                        prop.put("setUseCase_switchWarning", 2);
                    }
                    // switch to freeworld network
                    sb.setConfig(SwitchboardConstants.CORE_SERVICE_RWI, true);
                    sb.switchNetwork("defaults/yacy.network.freeworld.unit");
                    // switch to p2p mode
                    sb.setConfig(SwitchboardConstants.INDEX_DIST_ALLOW, true);
                    sb.setConfig(SwitchboardConstants.INDEX_RECEIVE_ALLOW, true);
                    sb.setConfig(SwitchboardConstants.INDEX_RECEIVE_ALLOW_SEARCH, true);
                    // set default behavior for search verification
                    sb.setConfig(SwitchboardConstants.SEARCH_VERIFY, "iffresh"); // nocache,iffresh,ifexist,cacheonly,false
                    sb.setConfig(SwitchboardConstants.SEARCH_VERIFY_DELETE, "true");
                }
            }
            if ("portal".equals(post.get("usecase", "")) && !"webportal".equals(networkName)) {
                if("intranet".equals(networkName) && hasNonEmptyRemoteSolr) {
                    /* One or more non empty remote Solr(s) attached : disallow switching from intranet to another network to prevent disclosure of indexed private documents */
                    prop.put("setUseCase_switchError", 1);
                } else {
                    if(hasNonEmptyRemoteSolr) {
                        /* One or more non empty remote Solr(s) attached : warn the user even if not coming from intranet as indexed documents may be irrelevant for the new mode */
                        prop.put("setUseCase_switchWarning", 2);
                    }

                    // switch to webportal network
                    sb.setConfig(SwitchboardConstants.CORE_SERVICE_RWI, false);
                    sb.switchNetwork("defaults/yacy.network.webportal.unit");
                    // switch to robinson mode
                    sb.setConfig(SwitchboardConstants.INDEX_DIST_ALLOW, false);
                    sb.setConfig(SwitchboardConstants.INDEX_RECEIVE_ALLOW, false);
                    sb.setConfig(SwitchboardConstants.INDEX_RECEIVE_ALLOW_SEARCH, false);
                    // set default behavior for search verification
                    sb.setConfig(SwitchboardConstants.SEARCH_VERIFY, "ifexist"); // nocache,iffresh,ifexist,cacheonly,false
                    sb.setConfig(SwitchboardConstants.SEARCH_VERIFY_DELETE, "false");
                }
            }
            if ("intranet".equals(post.get("usecase", "")) && !"intranet".equals(networkName)) {
                if(hasNonEmptyRemoteSolr) {
                    /* One or more non empty remote Solr(s) attached : warn the user as the indexed documents may be public external resources out of scope for intranet/localhost domain. */
                    prop.put("setUseCase_switchWarning", 1);
                }
                // switch to intranet network
                sb.setConfig(SwitchboardConstants.CORE_SERVICE_RWI, false);
                sb.switchNetwork("defaults/yacy.network.intranet.unit");
                // switch to p2p mode: enable ad-hoc networks between intranet users
                sb.setConfig(SwitchboardConstants.INDEX_DIST_ALLOW, false);
                sb.setConfig(SwitchboardConstants.INDEX_RECEIVE_ALLOW, false);
                sb.setConfig(SwitchboardConstants.INDEX_RECEIVE_ALLOW_SEARCH, false);
                // set default behavior for search verification
                sb.setConfig(SwitchboardConstants.SEARCH_VERIFY, "cacheonly"); // nocache,iffresh,ifexist,cacheonly,false
                sb.setConfig(SwitchboardConstants.SEARCH_VERIFY_DELETE, "false");
            }
        }

        networkName = sb.getConfig(SwitchboardConstants.NETWORK_NAME, "");
        if ("freeworld".equals(networkName)) {
            prop.put("setUseCase", 1);
            prop.put("setUseCase_freeworldChecked", 1);
        } else if ("webportal".equals(networkName)) {
            prop.put("setUseCase", 1);
            prop.put("setUseCase_portalChecked", 1);
        } else if ("intranet".equals(networkName)) {
            prop.put("setUseCase", 1);
            prop.put("setUseCase_intranetChecked", 1);
        } else {
            prop.put("setUseCase", 0);
        }
        prop.put("setUseCase_port", port);

        // check if values are proper
        final boolean properPassword = (sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "").length() > 0) || sb.getConfigBool(SwitchboardConstants.ADMIN_ACCOUNT_FOR_LOCALHOST, false);
        final boolean properName = (sb.peers.mySeed().getName().length() >= 3) && (!(Seed.isDefaultPeerName(sb.peers.mySeed().getName())));
        final boolean properPort = (sb.peers.mySeed().isSenior()) || (sb.peers.mySeed().isPrincipal());

        if ((env.getConfig(SwitchboardConstants.BROWSER_DEFAULT, "").startsWith("ConfigBasic.html,"))) {
            env.setConfig(SwitchboardConstants.BROWSER_DEFAULT, env.getConfig(SwitchboardConstants.BROWSER_DEFAULT, "").substring(17));
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
        if (upnp_enabled) {
            prop.put("upnp_success", (UPnP.getMappedPort(UPnPMappingType.HTTP) > 0) ? "2" : "1");
        }
        else {
            prop.put("upnp_success", "0");
        }

        // set default values
        prop.putHTML("defaultName", sb.peers.mySeed().getName());
        prop.put("defaultPort", env.getLocalPort());
        prop.put("withsslenabled", env.getConfigBool("server.https", false) ? 1 : 0);
        lang = env.getConfig("locale.language", "default"); // re-assign lang, may have changed
        prop.put("lang_browser", "0"); // for client browser language dependent
        prop.put("lang_de", "0");
        prop.put("lang_fr", "0");
        prop.put("lang_zh", "0");
        prop.put("lang_ru", "0");
        prop.put("lang_uk", "0");
        prop.put("lang_en", "0");
        prop.put("lang_ja", "0");
        prop.put("lang_el", "0");
        prop.put("lang_it", "0");
        prop.put("lang_es", "0");
        if ("default".equals(lang)) {
            prop.put("lang_en", "1");
        } else {
            prop.put("lang_" + lang, "1");
        }
        // set label class (green background) for active translation
        if (lang.equals("browser")) {
            final List<String> l = Translator.activeTranslations();
            prop.put("active_zh", l.contains("zh") ? "2" : "1");
            prop.put("active_de", l.contains("de") ? "2" : "1");
            prop.put("active_fr", l.contains("fr") ? "2" : "1");
            prop.put("active_hi", l.contains("hi") ? "2" : "1");
            prop.put("active_ja", l.contains("ja") ? "2" : "1");
            prop.put("active_el", l.contains("el") ? "2" : "1");
            prop.put("active_it", l.contains("it") ? "2" : "1");
            prop.put("active_ru", l.contains("ru") ? "2" : "1");
            prop.put("active_uk", l.contains("uk") ? "2" : "1");
            prop.put("active_es", l.contains("es") ? "2" : "1");
            prop.put("active_en", "2");

        } else {
            prop.put("active_de", "0");
            prop.put("active_fr", "0");
            prop.put("active_hi", "0");
            prop.put("active_zh", "0");
            prop.put("active_ru", "0");
            prop.put("active_uk", "0");
            prop.put("active_en", "0");
            prop.put("active_ja", "0");
            prop.put("active_el", "0");
            prop.put("active_it", "0");
            prop.put("active_es", "0");
        }
        return prop;
    }
}
