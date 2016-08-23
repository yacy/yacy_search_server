
/**
 *  Translator_p
 *  Copyright 2012 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 14.09.2011 at http://yacy.net
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
import java.util.Iterator;
import java.util.Map;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.server.servletProperties;
import net.yacy.utils.translation.CreateTranslationMasters;

public class Translator_p {

    public static servletProperties respond(@SuppressWarnings("unused") final RequestHeader requestHeader, @SuppressWarnings("unused") final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {
        try {
            final servletProperties prop = new servletProperties();
            final Switchboard sb = (Switchboard) env;

            String langcfg = sb.getConfig("locale.language", "default");
            prop.put("targetlang", langcfg);
            if ("default".equals(langcfg) || "browser".equals(langcfg)) {
                prop.put("errmsg", "activate a different language");
                return prop;
            } else {
                prop.put("errmsg", "");
            }

            File lngfile = new File(sb.getAppPath("locale.source", "locales"), langcfg + ".lng");
            CreateTranslationMasters ctm = new CreateTranslationMasters(/*new File ("locales","master.lng.xlf")*/);

            File masterxlf = new File(sb.getAppPath("locale.source", "locales"), "master.lng.xlf");
            if (!masterxlf.exists()) ctm.createMasterTranslationLists(masterxlf);
            Map<String, Map<String, String>> origTrans = ctm.joinMasterTranslationLists(masterxlf, lngfile);
            final File locallngfile = ctm.getScratchFile(lngfile);
            Map<String, Map<String, String>> localTrans = ctm.loadTranslationsLists(locallngfile); // TODO: this will read file twice
            int i = 0;
            if (origTrans.size() > 0) {
                String filename = origTrans.keySet().iterator().next();
                if (post != null && post.containsKey("sourcefile")) {
                    filename = post.get("sourcefile", filename);
                }

                // prepare filename list for drop-down list
                Iterator<String> filenameit = origTrans.keySet().iterator();
                final boolean filteruntranslated = post == null ? false : post.getBoolean("filteruntranslated");
                while (filenameit.hasNext()) {
                    String tmp = filenameit.next();
                    if (filteruntranslated) { // filter filenames with untranslated entries
                        Map<String, String> tmptrans = origTrans.get(tmp);
                        boolean hasuntrans = false;
                        for (String x : tmptrans.values()) {
                            if (x == null || x.isEmpty()) {
                                hasuntrans = true;
                                break;
                            }
                        }
                        if (!hasuntrans) continue;
                    }
                    prop.put("filelist_" + i + "_filename", tmp);
                    prop.put("filelist_" + i + "_selected", tmp.equals(filename));
                    i++;
                }
                prop.put("filelist", i);

                prop.add("sourcefile", filename);
                Map<String, String> origTextList = origTrans.get(filename);

                i = 0;
                boolean editapproved = false; // to switch already approved translation in edit mode
                int textlistid = -1;
                if (post != null) {
                    prop.put("filter.checked", filteruntranslated); // remember filter setting
                    textlistid = post.getInt("approve", -1);
                    if (post.containsKey("editapproved")) {
                        textlistid = post.getInt("editapproved", -1);
                        editapproved = true;
                    }
                }
                boolean changed = false;
                for (String sourcetext : origTextList.keySet()) {
                    String targettxt = origTextList.get(sourcetext);
                    boolean existinlocalTrans = localTrans.containsKey(filename) && localTrans.get(filename).containsKey(sourcetext);
                    
                    if (editapproved) existinlocalTrans |= (i == textlistid); // if edit of approved requested handle as/like existing in local to allow edit
                    
                    if (targettxt == null || targettxt.isEmpty() || existinlocalTrans) {
                        prop.put("textlist_" + i + "_filteruntranslated", true);
                    } else if (filteruntranslated) {
                        continue;
                    }
                    // handle (modified) input text
                    if (i == textlistid && post != null) {
                        if (editapproved) { // switch already translated in edit mode by copying to local translation
                            // not saved here as not yet modified/approved
                            ctm.addTranslation(localTrans, filename, sourcetext, targettxt);
                        } else {
                            String t = post.get("targettxt" + Integer.toString(textlistid));
                            // correct common partial html markup (part of text identification for words also used as html parameter)
                            if (!t.isEmpty()) {
                                if (sourcetext.startsWith(">") && !t.startsWith(">")) t=">"+t;
                                if (sourcetext.endsWith("<") && !t.endsWith("<")) t=t+"<";
                            }
                            targettxt = t;
                            // add changes to original (for display) and local (for save)
                            origTextList.put(sourcetext, targettxt);
                            changed = ctm.addTranslation(localTrans, filename, sourcetext, targettxt);
                        }
                    }
                    prop.putHTML("textlist_" + i + "_sourcetxt", sourcetext);
                    prop.putHTML("textlist_" + i + "_targettxt", targettxt);
                    prop.put("textlist_" + i + "_tokenid", Integer.toString(i));
                    prop.put("textlist_" + i + "_filteruntranslated_tokenid", Integer.toString(i));
                    i++;
                }
                if (post != null && post.containsKey("savetranslationlist")) {
                    changed = true;
                }
                if (changed) {
                    ctm.saveAsLngFile(langcfg, locallngfile, localTrans);
                    // adhoc translate this file
                    // 1. get/calc the path
                    final String htRootPath = env.getConfig(SwitchboardConstants.HTROOT_PATH, SwitchboardConstants.HTROOT_PATH_DEFAULT);
                    final File sourceDir = new File(env.getAppPath(), htRootPath);
                    final File destDir = new File(env.getDataPath("locale.translated_html", "DATA/LOCALE/htroot"), locallngfile.getName().substring(0, locallngfile.getName().length() - 4));// cut .lng
                    // get absolute file by adding relative filename from translationlist
                    final File sourceFile = new File(sourceDir, filename);
                    final File destFile = new File(destDir, filename);
                    ctm.translateFile(sourceFile, destFile, origTextList); // do the translation
                }
            }
            prop.put("textlist", i);
            return prop;
        } catch (IOException ex) {
            ConcurrentLog.logException(ex);
        }
        return null;
    }
}
