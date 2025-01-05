/**
 *  ProbabilisticClassifier
 *  Copyright 2015 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  first published 06.08.2015 on http://yacy.net
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


package net.yacy.document;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yacy.cora.bayes.BayesClassifier;
import net.yacy.cora.bayes.Classification;
import net.yacy.cora.util.ConcurrentLog;

public class ProbabilisticClassifier {

    public final static String NONE_CATEGORY_NAME = "NONE";
    public final static Category NONE_CATEGORY = new Category(NONE_CATEGORY_NAME);
    
    public static class Category {
        
        String category_name;
        
        public Category(String category_name) {
            this.category_name = category_name;
        }
        
        public String getName() {
            return this.category_name;
        }
    }
    
    public static class Context {

        private String context_name;
        private BayesClassifier<String, Category> bayes;
        
        public Context(String context_name, Map<String, File> categoryExampleLinesFiles, File negativeExampleLines) throws IOException {
            this.context_name = context_name;
            int requiredSize = 0;
            Charset charset = StandardCharsets.UTF_8;
            Map<String, List<String>> categoryBuffer = new HashMap<>();
            for (Map.Entry<String, File> category: categoryExampleLinesFiles.entrySet()) {
                List<String> list = Files.readAllLines(category.getValue().toPath(), charset);
                categoryBuffer.put(category.getKey(), list);
                requiredSize += list.size();
            }
            List<String> list = Files.readAllLines(negativeExampleLines.toPath(), charset);
            categoryBuffer.put(NONE_CATEGORY_NAME, Files.readAllLines(negativeExampleLines.toPath(), charset));
            requiredSize += list.size();
            
            this.bayes = new BayesClassifier<>();
            this.bayes.setMemoryCapacity(requiredSize);
            
            for (Map.Entry<String, List<String>> category: categoryBuffer.entrySet()) {
                Category c = new Category(category.getKey());
                for (String line: category.getValue()) {
                    List<String> tokens = normalize(line);
                    bayes.learn(c, tokens);
                }
            }
            bayes.learn(NONE_CATEGORY, categoryBuffer.get(NONE_CATEGORY_NAME));
        }

        private List<String> normalize(String phrase) {
            String cleanphrase = phrase.toLowerCase().replaceAll("\\W", " ");
            String[] rawtokens = cleanphrase.split("\\s");
            List<String> tokens = new ArrayList<>();
            for (String token: rawtokens) if (token.length() > 2) tokens.add(token);
            return tokens;
        }
        
        public String getName() {
            return this.context_name;
        }

        public Classification<String, Category> classify(String phrase) {
            List<String> words = normalize(phrase);
            return this.bayes.classify(words);
        }
        
     }
    
    private static Map<String, Context> contexts = new HashMap<>();

    public static Set<String> getContextNames() {
        return contexts.keySet();
    }
    
    public static Context getContext(String contextName) {
        return contexts.get(contextName);
    }
    
    /**
     * create a new classifier set.
     * @param path_to_context_directory directory containing contexts wich are directories containing .txt files. One of them must be named 'negative.txt'
     */
    public static void initialize(File path_to_context_directory) {
        contexts.clear();
        String[] context_candidates = path_to_context_directory.list();
        for (String context_candidate: context_candidates) {
            File ccf = new File(path_to_context_directory, context_candidate);
            if (!ccf.isDirectory()) continue;
            String[] category_candidates = ccf.list();
            
            Map<String, File> categoryExampleLinesFiles = new HashMap<>();
            File negativeExampleLines = null;
            
            for (String category_candidate: category_candidates) {
                if (!category_candidate.endsWith(".txt")) continue;
                File catcf = new File(ccf, category_candidate);
                if (category_candidate.startsWith("negative")) {
                    negativeExampleLines = catcf;
                } else {
                    categoryExampleLinesFiles.put(category_candidate.substring(0, category_candidate.length() - 4), catcf);
                }
            }
            
            if (negativeExampleLines != null && categoryExampleLinesFiles.size() > 0) {
                try {
                    Context context = new Context(context_candidate, categoryExampleLinesFiles, negativeExampleLines);
                    contexts.put(context_candidate, context);
                } catch (IOException e) {
                    ConcurrentLog.logException(e);
                }
            }
        }
    }
    
    /**
     * compute the classification of a given text. The result is a map with most probable categorizations for each context.
     * @param text the text to be classified
     * @return a map where the key is the navigator name (the bayes context) and the value is the most probable attribute name (the bayes category)
     */
    public static Map<String, String> getClassification(String text) {
        Map<String, String> c = new HashMap<>();
        for (Context context: contexts.values()) {
            Classification<String, Category> classification = context.classify(text);
            String contextname = context.getName();
            Category category = classification.getCategory();
            String categoryname = category.getName();
            c.put(contextname, categoryname);
        }
        return c;
    }
    
}
