// BlacklistTest_p.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Alexander Schier
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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
// javac -classpath .:../classes Blacklist_p.java
// if the shell's current path is HTROOT

package net.yacy.htroot;

import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.TreeMap;
import java.util.TreeSet;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.ListManager;
import net.yacy.data.TransactionManager;
import net.yacy.repository.Blacklist;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.repository.BlacklistDiagnostics;
import net.yacy.repository.BlacklistDiagnostics.Match;
import net.yacy.repository.BlacklistFile;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class BlacklistTest_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final serverObjects prop = new serverObjects();
        prop.putHTML("blacklistEngine", Blacklist.getEngineInfo());

        // do all post operations
        if(post != null && post.containsKey("testList")) {
            prop.put("testlist", "1");
            String urlstring = post.get("testurl", "");
            if (!urlstring.startsWith("http://") &&
                    !urlstring.startsWith("https://") &&
                    !urlstring.startsWith("ftp://") &&
                    !urlstring.startsWith("smb://") &&
                    !urlstring.startsWith("file://")) urlstring = "http://" + urlstring;
            DigestURL testurl = null;
            try {
                testurl = new DigestURL(urlstring);
            } catch (final MalformedURLException e) {
            	testurl = null;
            }
            if(testurl != null) {
                final String normalUrl = testurl.toNormalform(false);
                prop.putUrlEncodedHTML("url", normalUrl);
                prop.putUrlEncodedHTML("testlist_url", normalUrl);
                final String retest = "BlacklistTest_p.html?testList=Test&testurl="
                        + URLEncoder.encode(normalUrl, StandardCharsets.UTF_8);
                prop.putUrlEncodedHTML("retest", retest);
                boolean isblocked = false;
                final Map<String, Set<BlacklistType>> matchingRules = new TreeMap<>();
                final Map<BlacklistType, Set<String>> activeFiles = new EnumMap<>(BlacklistType.class);
                final Set<String> cachedOnly = new TreeSet<>();
                for (final BlacklistType type : BlacklistType.values()) {
                    final boolean listed = Switchboard.urlBlacklist.isListed(type, testurl);
                    final Set<String> rules = Switchboard.urlBlacklist.getMatchingRules(type, testurl);
                    final String purpose = purpose(type);
                    activeFiles.put(type, new BlacklistFile(env.getConfig(type + ".BlackLists",
                            env.getConfig("BlackLists.DefaultList", "url.default.black")), type).getFileNamesUnified());
                    if (listed) {
                        prop.put("testlist_listedin" + type.toString(), "1");
                        isblocked = true;
                        if (rules.isEmpty()) {
                            cachedOnly.add(purpose);
                        }
                    }
                    for (final String rule : rules) {
                        matchingRules.computeIfAbsent(rule, key -> EnumSet.noneOf(BlacklistType.class)).add(type);
                    }
                }
                final BlacklistDiagnostics.Report report = BlacklistDiagnostics.findSources(
                        ListManager.listsPath, activeFiles, matchingRules);
                final String token = TransactionManager.getTransactionToken(header);
                prop.put("token", token);
                putMatchingRules(prop, report, normalUrl, token);
                if (post.containsKey("ruleAction")) {
                    try {
                        if (!(env instanceof Switchboard) || !((Switchboard) env).verifyAuthentication(header)) {
                            prop.authenticationRequired();
                            return prop;
                        }
                        requirePost(header.getMethod(), post.get(TransactionManager.TRANSACTION_TOKEN_PARAM, ""), token);
                        TransactionManager.checkPostTransaction(header, post);
                        final Match selected = BlacklistDiagnostics.requireMatch(report, post.get("filename", ""),
                                post.get("entry", ""), post.get("revision", ""));
                        final String action = post.get("ruleAction", "");
                        if ("prepareDelete".equals(action)) {
                            putConfirmation(prop, selected, normalUrl, token, retest);
                        } else if ("confirmDelete".equals(action)) {
                            requireConfirmation(post.get("consent", ""));
                            BlacklistDiagnostics.deleteEntry(ListManager.listsPath, selected);
                            Switchboard.urlBlacklist.clear();
                            ListManager.reloadBlacklists();
                            prop.put(serverObjects.ACTION_LOCATION, retest);
                            return prop;
                        } else {
                            throw new IllegalArgumentException("Unknown blacklist action.");
                        }
                    } catch (final IllegalArgumentException | IOException e) {
                        prop.put("actionerror", 1);
                        prop.putHTML("actionerror_message", e.getMessage());
                    }
                }
                prop.put("testlist_cachedonly", cachedOnly.isEmpty() ? 0 : 1);
                prop.putHTML("testlist_cachedonly_types", String.join(", ", cachedOnly));

                if (!isblocked) {
                    prop.put("testlist_isnotblocked", "1");
                }
            }
            else {
                prop.putHTML("url",urlstring);
                prop.put("testlist", "2");
            }
        } else {
            prop.putHTML("url", "http://");
        }
        return prop;
    }

    static String purpose(final BlacklistType type) {
        return type == BlacklistType.CRAWLER ? "Crawling" : type == BlacklistType.DHT ? "DHT"
                : type.name().charAt(0) + type.toString().substring(1);
    }

    static void putMatchingRules(final serverObjects prop, final BlacklistDiagnostics.Report report) {
        putMatchingRules(prop, report, "", "");
    }

    static void requirePost(final String method, final String provided, final String expected) {
        if (!"POST".equals(method) || expected.isEmpty() || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Invalid action request. Test the URL again before deleting.");
        }
    }

    static void requireConfirmation(final String consent) {
        if (!"delete".equals(consent)) throw new IllegalArgumentException("Confirm the deletion checkbox first.");
    }

    static void putConfirmation(final serverObjects prop, final Match selected, final String url,
            final String token, final String retest) {
        prop.put("confirmation", 1);
        putSelection(prop, "confirmation_", selected, url, token);
        prop.putUrlEncodedHTML("confirmation_retest", retest);
    }

    private static void putSelection(final serverObjects prop, final String prefix, final Match selected,
            final String url, final String token) {
        prop.putUrlEncodedHTML(prefix + "filename", selected.filename);
        prop.putUrlEncodedHTML(prefix + "entry", selected.entry);
        prop.put(prefix + "revision", selected.revision);
        prop.putUrlEncodedHTML(prefix + "url", url);
        prop.put(prefix + "token", token);
    }

    static void putMatchingRules(final serverObjects prop, final BlacklistDiagnostics.Report report,
            final String url, final String token) {
        prop.put("testlist_matchdetails", report.matches.isEmpty() ? 0 : 1);
        prop.put("testlist_matchdetails_count", report.matches.size());
        prop.put("testlist_sourcewarning", report.unavailableFiles.isEmpty() ? 0 : 1);
        int row = 0;
        for (final Match entry : report.matches) {
            final String prefix = "testlist_matchdetails_rows_" + row + "_";
            prop.putUrlEncodedHTML(prefix + "rule", entry.rule);
            prop.putUrlEncodedHTML(prefix + "filename", entry.filename.isEmpty() ? "Source unavailable" : entry.filename);
            final Set<String> purposes = new TreeSet<>();
            for (final BlacklistType type : entry.types) purposes.add(purpose(type));
            prop.putHTML(prefix + "types", String.join(", ", purposes));
            prop.put(prefix + "actions", entry.editable() ? 1 : 0);
            putSelection(prop, prefix + "actions_", entry, url, token);
            row++;
        }
        prop.put("testlist_matchdetails_rows", row);
    }

}
