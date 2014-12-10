/**
 *  Vocabulary_p
 *  Copyright 2012 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 07.05.2012 at http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.language.synonyms.SynonymLibrary;
import net.yacy.cora.lod.vocabulary.DCTerms;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.lod.vocabulary.Tagging.SOTuple;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.WorkTables;
import net.yacy.document.LibraryProvider;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.search.Switchboard;
import net.yacy.search.index.Segment;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class Vocabulary_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        Collection<Tagging> vocs = LibraryProvider.autotagging.getVocabularies();

        String vocabularyName = (post == null) ? null : post.get("vocabulary", null);
        String discovername = (post == null) ? null : post.get("discovername", null);
        Tagging vocabulary = vocabularyName == null ? null : LibraryProvider.autotagging.getVocabulary(vocabularyName);
        if (vocabulary == null) vocabularyName = null;
        if (post != null) {
            try {
                // create a vocabulary
                if (vocabulary == null && discovername != null && discovername.length() > 0) {
                    // store this call as api call
                    sb.tables.recordAPICall(post, "Vocabulary_p.html", WorkTables.TABLE_API_TYPE_CRAWLER, "vocabulary creation for " + discovername);
                    // get details of creation
                    String discoverobjectspace = post.get("discoverobjectspace", "");
                    MultiProtocolURL discoveruri = null;
                    if (discoverobjectspace.length() > 0) try {discoveruri = new MultiProtocolURL(discoverobjectspace);} catch (final MalformedURLException e) {}
                    if (discoveruri == null) discoverobjectspace = "";
                    Map<String, Tagging.SOTuple> table = new LinkedHashMap<String, Tagging.SOTuple>();
                    File propFile = LibraryProvider.autotagging.getVocabularyFile(discovername);
                    final boolean isFacet = post.getBoolean("isFacet");
                    final boolean discoverNot = post.get("discovermethod", "").equals("none");
                    final boolean discoverFromPath = post.get("discovermethod", "").equals("path");
                    final boolean discoverFromTitle = post.get("discovermethod", "").equals("title");
                    final boolean discoverFromTitleSplitted = post.get("discovermethod", "").equals("titlesplitted");
                    final boolean discoverFromAuthor = post.get("discovermethod", "").equals("author");
                    final boolean discoverFromCSV = post.get("discovermethod", "").equals("csv");
                    final String discoverFromCSVPath = post.get("discoverpath", "").replaceAll("%20", " ");
                    final String discoverFromCSVCharset = post.get("charset", "UTF-8");
                    final int discovercolumnliteral = post.getInt("discovercolumnliteral", 0);
                    final int discovercolumnsynonyms = post.getInt("discovercolumnsynonyms", -1);
                    final int discovercolumnobjectlink = post.getInt("discovercolumnobjectlink", -1);
                    final File discoverFromCSVFile = discoverFromCSVPath.length() > 0 ? new File(discoverFromCSVPath) : null;
                    final boolean discoverenrichsynonyms = post.get("discoversynonymsmethod", "none").equals("enrichsynonyms");
                    final boolean discoverreadcolumn = post.get("discoversynonymsmethod", "none").equals("readcolumn");
                    Segment segment = sb.index;
                    String t;
                    if (!discoverNot) {
                        if (discoverFromCSV && discoverFromCSVFile != null && discoverFromCSVFile.exists()) {
                            // auto-detect charset, used code from http://jchardet.sourceforge.net/; see also: http://www-archive.mozilla.org/projects/intl/chardet.html
                            FileUtils.checkCharset(discoverFromCSVFile, discoverFromCSVCharset, true);
                            // read file
                            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(discoverFromCSVFile), discoverFromCSVCharset));
                            String line = null;
                            Pattern semicolon = Pattern.compile(";");
                            Map<String, String> synonym2literal = new HashMap<>(); // helper map to check if there are double synonyms
                            while ((line = r.readLine()) != null) {
                                if (line.length() == 0) continue;
                                String[] l = semicolon.split(line);
                                if (l.length == 0) l = new String[]{line};
                                String literal = discovercolumnliteral < 0 || l.length <= discovercolumnliteral ? null : l[discovercolumnliteral].trim();
                                if (literal == null) continue;
                                literal = normalizeLiteral(literal);
                                String objectlink = discovercolumnobjectlink < 0 || l.length <= discovercolumnobjectlink ? null : l[discovercolumnobjectlink].trim();
                                if (literal.length() > 0) {
                                    String synonyms = "";
                                    if (discoverenrichsynonyms) {
                                        Set<String> sy = SynonymLibrary.getSynonyms(literal);
                                        if (sy != null) {
                                            for (String s: sy) synonyms += "," + s;
                                        }
                                    } else if (discoverreadcolumn) {
                                        synonyms = discovercolumnsynonyms < 0 || l.length <= discovercolumnsynonyms ? null : l[discovercolumnsynonyms].trim();
                                        synonyms = normalizeLiteral(synonyms);
                                    } else {
                                        synonyms = Tagging.normalizeTerm(literal);
                                    }
                                    // check double synonyms
                                    if (synonyms.length() > 0) {
                                        String oldliteral = synonym2literal.get(synonyms);
                                        if (oldliteral != null) {
                                            // replace old entry with combined new
                                            table.remove(oldliteral);
                                            String newliteral = oldliteral + "," + literal;
                                            literal = newliteral;
                                        }
                                        synonym2literal.put(synonyms, literal);
                                    }
                                    // store term
                                    table.put(literal, new Tagging.SOTuple(synonyms, objectlink == null ? "" : objectlink));
                                }
                            }
                        } else {
                            Iterator<DigestURL> ui = segment.urlSelector(discoveruri, Long.MAX_VALUE, 100000);
                            while (ui.hasNext()) {
                                DigestURL u = ui.next();
                                String u0 = u.toNormalform(true);
                                t = "";
                                if (discoverFromPath) {
                                    int exp = u0.lastIndexOf('.');
                                    if (exp < 0) continue;
                                    int slp = u0.lastIndexOf('/', exp);
                                    if (slp < 0) continue;
                                    t = u0.substring(slp, exp);
                                    int p;
                                    while ((p = t.indexOf(':')) >= 0) t = t.substring(p + 1);
                                    while ((p = t.indexOf('=')) >= 0) t = t.substring(p + 1);
                                }
                                if (discoverFromTitle || discoverFromTitleSplitted) {
                                    URIMetadataNode m = segment.fulltext().getMetadata(u.hash());
                                    if (m != null) t = m.dc_title();
                                    if (t.endsWith(".jpg") || t.endsWith(".gif")) continue;
                                }
                                if (discoverFromAuthor) {
                                    URIMetadataNode m = segment.fulltext().getMetadata(u.hash());
                                    if (m != null) t = m.dc_creator();
                                }
                                t = t.replaceAll("_", " ").replaceAll("\"", " ").replaceAll("'", " ").replaceAll(",", " ").replaceAll("  ", " ").trim();
                                if (t.isEmpty()) continue;
                                if (discoverFromTitleSplitted) {
                                    String[] ts = t.split(" ");
                                    for (String s: ts) {
                                        if (s.isEmpty()) continue;
                                        if (s.endsWith(".jpg") || s.endsWith(".gif")) continue;
                                        table.put(s, new Tagging.SOTuple(Tagging.normalizeTerm(s), u0));
                                    }
                                } else if (discoverFromAuthor) {
                                    String[] ts = t.split(";"); // author names are often separated by ';'
                                    for (String s: ts) {
                                        if (s.isEmpty()) continue;
                                        int p = s.indexOf(','); // check if there is a reversed method to mention the name
                                        if (p >= 0) s = s.substring(p + 1).trim() + " " + s.substring(0, p).trim();
                                        table.put(s, new Tagging.SOTuple(Tagging.normalizeTerm(s), u0));
                                    }
                                } else {
                                    table.put(t, new Tagging.SOTuple(Tagging.normalizeTerm(t), u0));
                                }
                            }
                        }
                    }
                    Tagging newvoc = new Tagging(discovername, propFile, discoverobjectspace, table);
                    newvoc.setFacet(isFacet);
                    LibraryProvider.autotagging.addVocabulary(newvoc);
                    vocabularyName = discovername;
                    vocabulary = newvoc;
                } else {
                    // check if objectspace was set
                    vocabulary.setObjectspace(post.get("objectspace", vocabulary.getObjectspace() == null ? "" : vocabulary.getObjectspace()));

                    // check if a term was added
                    if (post.get("add_new", "").equals("checked") && post.get("newterm", "").length() > 0) {
                    	String objectlink = post.get("newobjectlink", "");
                    	if (objectlink.length() > 0) try {
                    		objectlink = new MultiProtocolURL(objectlink).toNormalform(true);
                    	} catch (final MalformedURLException e) {}
                        vocabulary.put(post.get("newterm", ""), post.get("newsynonyms", ""), objectlink);
                    }

                    // check if a term was modified
                    for (Map.Entry<String, String> e : post.entrySet()) {
                        if (e.getKey().startsWith("modify_") && e.getValue().equals("checked")) {
                            String term = e.getKey().substring(7);
                            String synonyms = post.get("synonyms_" + term, "");
                            String objectlink = post.get("objectlink_" + term, "");
                            vocabulary.put(term, synonyms, objectlink);
                        }
                    }

                    // check if a term shall be deleted
                    for (Map.Entry<String, String> e : post.entrySet()) {
                        if (e.getKey().startsWith("delete_") && e.getValue().equals("checked")) {
                            String term = e.getKey().substring(7);
                            vocabulary.delete(term);
                        }
                    }

                    // check if the vocabulary shall be cleared
                    if (post.get("clear_table", "").equals("checked") ) {
                        vocabulary.clear();
                    }

                    // check if the vocabulary shall be deleted
                    if (vocabulary != null && post.get("delete_vocabulary", "").equals("checked") ) {
                        LibraryProvider.autotagging.deleteVocabulary(vocabularyName);
                        vocabulary = null;
                        vocabularyName = null;
                    }
                    
                    // check the isFacet property
                    if (vocabulary != null && post.containsKey("isFacet")) {
                        vocabulary.setFacet(post.getBoolean("isFacet"));
                    }
                }
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
        }

        int count = 0;
        for (Tagging v: vocs) {
            prop.put("vocabularyset_" + count + "_name", v.getName());
            prop.put("vocabularyset_" + count + "_selected", ((vocabularyName != null && vocabularyName.equals(v.getName())) || (discovername != null && discovername.equals(v.getName()))) ? 1 : 0);
            count++;
        }
        prop.put("vocabularyset", count);

        prop.put("create", vocabularyName == null ? 1 : 0);

        if (vocabulary == null) {
            prop.put("edit", 0);
        } else {
            prop.put("edit", 1);
            boolean editable = vocabulary.getFile() != null && vocabulary.getFile().exists();
            prop.put("edit_editable", editable ? 1 : 0);
            prop.putHTML("edit_editable_file", editable ? vocabulary.getFile().getAbsolutePath() : "");
            prop.putHTML("edit_name", vocabulary.getName());
            prop.putXML("edit_namexml", vocabulary.getName());
            prop.putHTML("edit_namespace", vocabulary.getNamespace());
            prop.put("edit_isFacet", vocabulary.isFacet() ? 1 : 0);
            prop.put("edit_size", vocabulary.size());
            prop.putHTML("edit_predicate", vocabulary.getPredicate());
            prop.putHTML("edit_prefix", Tagging.DEFAULT_PREFIX);
            prop.putHTML("edit_editable_objectspace", vocabulary.getObjectspace() == null ? "" : vocabulary.getObjectspace());
            prop.putHTML("edit_editable_objectspacepredicate", DCTerms.references.getPredicate());
            int c = 0;
            boolean dark = false;
            int osl = vocabulary.getObjectspace() == null ? 0 : vocabulary.getObjectspace().length();
            Map<String, SOTuple> list = vocabulary.list();
            prop.put("edit_size", list.size());
            for (Map.Entry<String, SOTuple> entry: list.entrySet()) {
                prop.put("edit_terms_" + c + "_editable", editable ? 1 : 0);
                prop.put("edit_terms_" + c + "_dark", dark ? 1 : 0); dark = !dark;
                prop.putXML("edit_terms_" + c + "_label", osl > entry.getValue().getObjectlink().length() ? entry.getKey() : entry.getValue().getObjectlink().substring(osl));
                prop.putHTML("edit_terms_" + c + "_term", entry.getKey());
                prop.putXML("edit_terms_" + c + "_termxml", entry.getKey());
                prop.putHTML("edit_terms_" + c + "_editable_term", entry.getKey());
                String synonymss = entry.getValue().getSynonymsCSV();
                prop.putHTML("edit_terms_" + c + "_editable_synonyms", synonymss);
                if (synonymss.length() > 0) {
                    String[] synonymsa = entry.getValue().getSynonymsList();
                    for (int i = 0; i < synonymsa.length; i++) {
                        prop.put("edit_terms_" + c + "_synonyms_" + i + "_altLabel", synonymsa[i]);
                    }
                    prop.put("edit_terms_" + c + "_synonyms", synonymsa.length);
                } else {
                    prop.put("edit_terms_" + c + "_synonyms", 0);
                }
                prop.putXML("edit_terms_" + c + "_editable_objectlink", entry.getValue().getObjectlink());
                c++;
                if (c > 3000) break;
            }
            prop.put("edit_terms", c);

        }

        // make charset list for import method selector
        int c = 0;
        for (String cs: Charset.availableCharsets().keySet()) {
            prop.putHTML("create_charset_" + c + "_name", cs);
            prop.put("create_charset_" + c + "_selected", cs.equals("windows-1252") ? 1 : 0);
            c++;
        }
        prop.put("create_charset", c);
        
        // return rewrite properties
        return prop;
    }
    
    private static String normalizeLiteral(String literal) {
        if (literal == null) return "";
        if (literal.length() > 0 && (literal.charAt(0) == '"' || literal.charAt(0) == '\'')) literal = literal.substring(1);
        if (literal.length() > 0 && (literal.charAt(literal.length() - 1) == '"' || literal.charAt(literal.length() - 1) == '\'')) literal = literal.substring(0, literal.length() - 1);
        return literal;
    }
}
