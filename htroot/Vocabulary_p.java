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

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.lod.vocabulary.DCTerms;
import net.yacy.cora.lod.vocabulary.Owl;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.lod.vocabulary.Tagging.SOTuple;
import net.yacy.cora.lod.vocabulary.YaCyMetadata;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.LibraryProvider;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.index.Segment;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class Vocabulary_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        Collection<Tagging> vocs = LibraryProvider.autotagging.getVocabularies();

        String vocabularyName = (post == null) ? null : post.get("vocabulary", null);
        Tagging vocabulary = vocabularyName == null ? null : LibraryProvider.autotagging.getVocabulary(vocabularyName);
        if (vocabulary == null) vocabularyName = null;
        int count = 0;
        for (Tagging v: vocs) {
            prop.put("vocabularyset_" + count + "_name", v.getName());
            prop.put("vocabularyset_" + count + "_selected", (vocabularyName != null && vocabularyName.equals(v.getName())) ? 1 : 0);
            count++;
        }
        prop.put("vocabularyset", count);

        if (post != null) {
            try {
                if (vocabulary == null) {
                    // create a vocabulary
                    String discovername = post.get("discovername", "");
                    if (discovername.length() > 0) {
                        String discoverobjectspace = post.get("discoverobjectspace", "");
                        MultiProtocolURI discoveruri = null;
                        if (discoverobjectspace.length() > 0) try {discoveruri = new MultiProtocolURI(discoverobjectspace);} catch (MalformedURLException e) {}
                        if (discoveruri == null) discoverobjectspace = "";
                        Map<String, Tagging.SOTuple> table = new TreeMap<String, Tagging.SOTuple>();
                        File propFile = LibraryProvider.autotagging.getVocabularyFile(discovername);
                        if (discoveruri != null) {
                            String segmentName = sb.getConfig(SwitchboardConstants.SEGMENT_PUBLIC, "default");
                            Segment segment = sb.indexSegments.segment(segmentName);
                            Iterator<DigestURI> ui = segment.urlSelector(discoveruri);
                            while (ui.hasNext()) {
                                DigestURI u = ui.next();
                                String u0 = u.toNormalform(true, false);
                                String t = u0.substring(discoverobjectspace.length());
                                if (t.indexOf('/') >= 0) continue;
                                int p = t.indexOf('.');
                                if (p >= 0) t = t.substring(0, p);
                                while ((p = t.indexOf(':')) >= 0) t = t.substring(p + 1);
                                while ((p = t.indexOf('=')) >= 0) t = t.substring(p + 1);
                                if (p >= 0) t = t.substring(p + 1);
                                if (t.length() == 0) continue;
                                table.put(t, new Tagging.SOTuple("", u0));
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
                    		objectlink = new MultiProtocolURI(objectlink).toNormalform(true, false);
                    	} catch (MalformedURLException e) {}
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
            } catch (IOException e) {
                Log.logException(e);
            }
        }

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
            prop.putHTML("edit_namespace", vocabulary.getNamespace());
            prop.putHTML("edit_predicate", vocabulary.getPredicate());
            prop.putHTML("edit_prefix", Tagging.DEFAULT_PREFIX);
            prop.putHTML("edit_editable_objectspace", vocabulary.getObjectspace() == null ? "" : vocabulary.getObjectspace());
            prop.putHTML("edit_editable_objectspacepredicate", DCTerms.references.getPredicate());
            prop.putHTML("edit_triple1", "<" + yacyurl + "> <" + vocabulary.getPredicate() + "> \"[discovered-tags-commaseparated]\"");
            prop.putHTML("edit_triple2", "<" + yacyurl + "> <" + Owl.SameAs.getPredicate() + "> <[document-url]>");
            prop.putHTML("edit_tripleN", vocabulary.getObjectspace() == null ? "none - missing objectspace" : "<" + yacyurl + "> <" + DCTerms.references.getPredicate() + "> \"[reference-link]#[tag]\" .");
            int c = 0;
            boolean dark = false;
            for (Map.Entry<String, SOTuple> entry: vocabulary.list().entrySet()) {
                prop.put("edit_terms_" + c + "_editable", editable ? 1 : 0);
                prop.put("edit_terms_" + c + "_dark", dark ? 1 : 0); dark = !dark;
                prop.putHTML("edit_terms_" + c + "_term", entry.getKey());
                prop.putHTML("edit_terms_" + c + "_editable_term", entry.getKey());
                prop.putHTML("edit_terms_" + c + "_editable_synonyms", entry.getValue().getSynonymsCSV());
                prop.putHTML("edit_terms_" + c + "_editable_objectlink", entry.getValue().getObjectlink());
                c++;
                if (c > 3000) break;
            }
            prop.put("edit_terms", c);
        }

        // return rewrite properties
        return prop;
    }
}
