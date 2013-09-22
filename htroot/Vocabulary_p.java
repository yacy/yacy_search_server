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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.lod.vocabulary.DCTerms;
import net.yacy.cora.lod.vocabulary.Owl;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.lod.vocabulary.Tagging.SOTuple;
import net.yacy.cora.lod.vocabulary.YaCyMetadata;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.LibraryProvider;
import net.yacy.kelondro.data.meta.URIMetadataNode;
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
                if (vocabulary == null) {
                    // create a vocabulary
                    if (discovername != null && discovername.length() > 0) {
                        String discoverobjectspace = post.get("discoverobjectspace", "");
                        MultiProtocolURL discoveruri = null;
                        if (discoverobjectspace.length() > 0) try {discoveruri = new MultiProtocolURL(discoverobjectspace);} catch (final MalformedURLException e) {}
                        if (discoveruri == null) discoverobjectspace = "";
                        Map<String, Tagging.SOTuple> table = new TreeMap<String, Tagging.SOTuple>();
                        File propFile = LibraryProvider.autotagging.getVocabularyFile(discovername);
                        boolean discoverNot = post.get("discovermethod", "").equals("none");
                        boolean discoverFromPath = post.get("discovermethod", "").equals("path");
                        boolean discoverFromTitle = post.get("discovermethod", "").equals("title");
                        boolean discoverFromTitleSplitted = post.get("discovermethod", "").equals("titlesplitted");
                        boolean discoverFromAuthor = post.get("discovermethod", "").equals("author");
                        Segment segment = sb.index;
                        String t;
                        if (!discoverNot) {
                            Iterator<DigestURL> ui = segment.urlSelector(discoveruri, 600000L, 100000);
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
                        Tagging newvoc = new Tagging(discovername, propFile, discoverobjectspace, table);
                        LibraryProvider.autotagging.addVocabulary(newvoc);
                        vocabularyName = discovername;
                        vocabulary = newvoc;
                    }
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
            String yacyurl = YaCyMetadata.hashURI("[hash]".getBytes());
            prop.put("edit_editable", editable ? 1 : 0);
            prop.putHTML("edit_editable_file", editable ? vocabulary.getFile().getAbsolutePath() : "");
            prop.putHTML("edit_name", vocabulary.getName());
            prop.putXML("edit_namexml", vocabulary.getName());
            prop.putHTML("edit_namespace", vocabulary.getNamespace());
            prop.put("edit_size", vocabulary.size());
            prop.putHTML("edit_predicate", vocabulary.getPredicate());
            prop.putHTML("edit_prefix", Tagging.DEFAULT_PREFIX);
            prop.putHTML("edit_editable_objectspace", vocabulary.getObjectspace() == null ? "" : vocabulary.getObjectspace());
            prop.putHTML("edit_editable_objectspacepredicate", DCTerms.references.getPredicate());
            prop.putXML("edit_triple1", "<" + yacyurl + "> <" + vocabulary.getPredicate() + "> \"[discovered-tags-commaseparated]\"");
            prop.putXML("edit_triple2", "<" + yacyurl + "> <" + Owl.SameAs.getPredicate() + "> <[document-url]>");
            prop.putXML("edit_tripleN", vocabulary.getObjectspace() == null ? "none - missing objectspace" : "<" + yacyurl + "> <" + DCTerms.references.getPredicate() + "> \"[object-link]#[tag]\" .");
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

        // return rewrite properties
        return prop;
    }
}
