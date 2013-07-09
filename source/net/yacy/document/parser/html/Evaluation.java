/**
 *  Evaluation
 *  Copyright 2011 by Michael Peter Christen
 *  First released 28.04.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-03-08 02:51:51 +0100 (Di, 08 Mrz 2011) $
 *  $LastChangedRevision: 7567 $
 *  $LastChangedBy: low012 $
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

package net.yacy.document.parser.html;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.yacy.cora.sorting.ClusteredScoreMap;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.util.MemoryControl;


/*
 * This class provides methods to use a pattern analysis for html files
 * The pattern analysis is generic and can be configured using a field-name/pattern property
 * configuration file.
 * Such a configuration file has names of the structure
 * <subject-name>_<document-element>
 * and values are regular java expressions
 * A html file is scanned for pattern matchings within a specific <document-element>
 * and if such a matching can be found then the <attribute-name> is collected as
 * subject for the scanned document
 * patternProperties files must have special file names where the file name
 * starts with the word "parser." and must end with ".properties"
 * everything between this is a name for a solr multi-value field where
 * the collected subject names are stored to
 */
public class Evaluation {

    private static List<Model> models = new ArrayList<Model>(); // the list of all models that shall be applied

    public static enum Element {
        text,
        title,
        bodyclass,
        divid,
        csspath,
        metagenerator,
        url,
        scriptpath,
        scriptcode,
        framepath,
        iframepath,
        imgpath,
        apath,
        comment;
    }

    private static class Attribute {
        public String subject; // the name of the attribute
        public Pattern pattern; // the pattern that must match for that attribute
        public Attribute(final String subject, final Pattern pattern) {
            this.subject = subject;
            this.pattern = pattern;
        }

        @Override
        public String toString() {
            return this.subject + ":" + this.pattern.toString();
        }
    }

    private static class Model {

        private final String modelName;
        private final Map<Element, List<Attribute>> elementMatcher; // a mapping from element-names to lists of Attributes

        public Model(final File patternProperties) throws IOException {
            if (!patternProperties.exists()) throw new IOException("File does not exist: " + patternProperties);
            final String name = patternProperties.getName();
            if (!name.startsWith("parser.")) throw new IOException("file name must start with 'parser.': " + name);
            if (!name.endsWith(".properties")) throw new IOException("file name must end with '.properties': " + name);
            this.modelName = name.substring(7, name.length() - 11);
            if (this.modelName.length() < 1) throw new IOException("file name too short: " + name);

            // load the file
            final Properties p = new Properties();
            p.load(new FileReader(patternProperties));

            // iterate through the properties and generate method patterns
            this.elementMatcher = new HashMap<Element, List<Attribute>>();
            String subject, elementName;
            Element element;
            Pattern pattern;
            for (final Map.Entry<Object, Object> entry: p.entrySet()) {
                final String k = (String) entry.getKey();
                final String v = (String) entry.getValue();
                final int w = k.indexOf('_');
                if (w < 0) {
                    ConcurrentLog.severe("PatternAnalysis", "wrong configuration in " + name + ": separator '_' missing: " + k);
                    continue;
                }
                subject = k.substring(0, w);
                elementName = k.substring(w + 1);
                try {
                    pattern = Pattern.compile(v);
                } catch (final PatternSyntaxException e) {
                    ConcurrentLog.severe("PatternAnalysis", "bad pattern in " + name + ": '" + k + "=" + v + "' - " + e.getDescription());
                    continue;
                }
                element = Element.valueOf(elementName);
                if (element == null) {
                    ConcurrentLog.severe("PatternAnalysis", "unknown element in " + name + ": " + elementName);
                    continue;
                }
                List<Attribute> attributeList = this.elementMatcher.get(element);
                if (attributeList == null) {
                    attributeList = new ArrayList<Attribute>();
                    this.elementMatcher.put(element, attributeList);
                }
                attributeList.add(new Attribute(subject, pattern));
            }
        }

        public String getName() {
            return this.modelName;
        }

        /**
         * match elementContents for a specific elementName
         * @param element - the name of the element as Element enum type
         * @param content - the content of the element
         * @return a list of subject names that match with the element
         */
        public ClusteredScoreMap<String> match(final Element element, final CharSequence content) {
            final ClusteredScoreMap<String> subjects = new ClusteredScoreMap<String>();
            final List<Attribute> patterns = this.elementMatcher.get(element);
            if (patterns == null) return subjects;
            for (final Attribute attribute: patterns) {
                if (attribute.pattern.matcher(content).matches()) {
                    subjects.inc(attribute.subject);
                }
            }
            return subjects;
        }

        @Override
        public String toString() {
            return this.modelName + ":" + this.elementMatcher.toString();
        }

    }

    private final Map<String, ClusteredScoreMap<String>> modelMap; // a map from model names to attribute scores

    public Evaluation() {
        this.modelMap = new HashMap<String, ClusteredScoreMap<String>>();
    }

    @Override
    public String toString() {
        return this.modelMap.toString();
    }

    /**
     * produce all model names
     * @return a set of model names
     */
    public Set<String> getModelNames() {
        return this.modelMap.keySet();
    }

    /**
     * calculate the scores for a model
     * the scores is a attribute/count map which count how often a specific attribute was found
     * @param modelName
     * @return
     */
    public ClusteredScoreMap<String> getScores(final String modelName) {
        return this.modelMap.get(modelName);
    }

    /**
     * add a model to the evaluation set
     * @param f
     * @throws IOException
     */
    public static void add(final File f) throws IOException {
        final Model pattern = new Model(f);
        models.add(pattern);
    }

    /**
     * match some content within a specific element
     * this will increase statistic counters for models if a model matches
     * @param element - the element where a matching is made
     * @param content - the content of the element which shall be matched
     */
    public void match(final Element element, final CharSequence content) {
        if (models.isEmpty()) return; // fast return if this feature is not used
        ClusteredScoreMap<String> newScores, oldScores;
        for (final Model pattern: models) {
            newScores = pattern.match(element, content);
            oldScores = getScores(pattern.getName());
            if (oldScores == null) {
                oldScores = new ClusteredScoreMap<String>();
                this.modelMap.put(pattern.getName(), oldScores);
            }
            oldScores.inc(newScores);
        }
    }

    public void match(final Element element, final char[] content) {
        if (models.isEmpty()) return; // fast return if this feature is not used
        if (MemoryControl.request(content.length * 2, false)) {
            match(element, new String(content) /*Segment(content, 0, content.length)*/);
        }
    }

    public static void main(String[] args) {
        String t =
            "// [CDATA[\n" +
            "var gaJsH xost = ((\"https:\" == document.location.protocol) ? \"https://ssl.\" : \"http://www.\");\n" +
            "document.write(unescape(\"%3Cscript xsrc='\" + gaJ sHost + \"google-analytics.com/ga.js'\n" +
            "type='text/javascript'%3E%3C/script%3E\"));\"\n" +
            "\"// ]]\"";
        Pattern p = Pattern.compile("(?s).*gaJsHost.*|(?s).*_gat._anonymizeIp.*");
        if (p.matcher(t).matches()) System.out.println("1");
    }

}
